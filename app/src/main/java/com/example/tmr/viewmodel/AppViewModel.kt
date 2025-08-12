package com.example.tmr.viewmodel

import android.content.Context
import android.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScreenType {
    START, PRACTICE, EXPERIMENT, END
}

enum class SessionType {
    PRACTICE, EXPERIMENT
}

enum class WritingState {
    IDLE, WRITING, COOLDOWN
}

data class EndSummary(
    val subjectId: String,
    val trialsCollected: Int,
    val timeSpentMs: Long,
    val filePath: String
)

class CsvLogger(private val subjectId: String, private val sessionType: SessionType) {
    private val header = "timestamp,subject_id,writing,trial,x,y"
    private val lines = StringBuilder().apply { appendLine(header) }

    fun append(timestampMs: Long, writing: Int, trial: Int, x: Float?, y: Float?) {
        val xStr = x?.toString() ?: ""
        val yStr = y?.toString() ?: ""
        lines.appendLine("$timestampMs,$subjectId,$writing,$trial,$xStr,$yStr")
    }

    fun saveToFile(context: Context): String {
        val dir = File(context.getExternalFilesDir(null), "TMR_watch")
        if (!dir.exists()) dir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val prefix = if (sessionType == SessionType.PRACTICE) "practice_" else ""
        val filename = "${prefix}TMR_watch_${subjectId}_${ts}.csv"
        val file = File(dir, filename)
        FileOutputStream(file).use { fos ->
            fos.write(lines.toString().toByteArray())
        }
        return file.absolutePath
    }
}

class AppViewModel : ViewModel() {
    private val COOLDOWN_DURATION_MS: Long = 500L
    val cooldownDurationMs: Long = COOLDOWN_DURATION_MS
    // Navigation/state
    var currentScreen: ScreenType by mutableStateOf(ScreenType.START)
        private set

    var subjectId: String by mutableStateOf("")
        private set

    // Session state
    private var sessionType: SessionType = SessionType.EXPERIMENT
    private var sessionStartMs: Long = 0L
    var elapsedMs: Long by mutableStateOf(0L)
        private set

    // Trial detection
    var writingState: WritingState by mutableStateOf(WritingState.IDLE)
        private set
    var trialNumber: Int by mutableStateOf(0)
        private set
    private var cooldownStartMs: Long = 0L
    var cooldownRemainingMs: Long by mutableStateOf(0L)

    // Pointer/live data for sampling
    var isWritingTrigger: Boolean by mutableStateOf(false)
        private set
    var latestPoint: Offset? = null
        private set

    // Drawing path for current trial; store as android.graphics.Path for efficiency
    val currentPath: Path = Path()
    var pathVersion: Int by mutableStateOf(0)

    private var samplerJob: Job? = null
    private var csvLogger: CsvLogger? = null
    var lastSavedFilePath: String by mutableStateOf("")
    private data class Sample(
        val timestampMs: Long,
        val writing: Int,
        val trial: Int,
        val x: Float?,
        val y: Float?
    )
    private val cooldownBuffer: MutableList<Sample> = mutableListOf()

    fun updateSubjectId(newId: String) { subjectId = newId }

    fun navigateToStart() {
        stopSamplingAndReset()
        currentScreen = ScreenType.START
    }

    fun startPractice() {
        startSession(SessionType.PRACTICE)
        currentScreen = ScreenType.PRACTICE
    }

    fun startExperiment() {
        startSession(SessionType.EXPERIMENT)
        currentScreen = ScreenType.EXPERIMENT
    }

    private fun startSession(type: SessionType) {
        sessionType = type
        sessionStartMs = System.currentTimeMillis()
        elapsedMs = 0L
        trialNumber = 0
        writingState = WritingState.IDLE
        isWritingTrigger = false
        latestPoint = null
        currentPath.rewind()
        csvLogger = CsvLogger(subjectId = subjectId, sessionType = sessionType)
        cooldownBuffer.clear()
        startSampler()
    }

    private fun startSampler() {
        samplerJob?.cancel()
        samplerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                elapsedMs = now - sessionStartMs
                val logger = csvLogger
                val writingVal = if (isWritingTrigger) 1 else 0
                val trialForLog = when (writingState) {
                    WritingState.IDLE -> 0
                    WritingState.WRITING -> trialNumber
                    WritingState.COOLDOWN -> trialNumber
                }
                val sample = Sample(
                    timestampMs = elapsedMs,
                    writing = writingVal,
                    trial = trialForLog,
                    x = latestPoint?.x,
                    y = latestPoint?.y
                )
                if (writingState == WritingState.COOLDOWN) {
                    cooldownBuffer.add(sample)
                } else {
                    logger?.append(
                        timestampMs = sample.timestampMs,
                        writing = sample.writing,
                        trial = sample.trial,
                        x = sample.x,
                        y = sample.y
                    )
                }
                delay(10) // ~100 Hz
                // Handle cooldown -> end of trial when exceeded 1s
                if (writingState == WritingState.COOLDOWN) {
                    val elapsedCd = now - cooldownStartMs
                    cooldownRemainingMs = (COOLDOWN_DURATION_MS - elapsedCd).coerceAtLeast(0L)
                    if (elapsedCd >= COOLDOWN_DURATION_MS) {
                        // End of trial
                        flushCooldownBuffer(zeroTrial = true)
                        currentPath.rewind()
                        pathVersion += 1
                        writingState = WritingState.IDLE
                        cooldownRemainingMs = 0L
                    }
                }
                // End conditions
                if (currentScreen == ScreenType.PRACTICE) {
                    if (elapsedMs >= 3 * 60 * 1000L) {
                        // practice ends automatically; stop sampler but keep on PRACTICE screen until UI overlays
                        stopSamplerOnly()
                    }
                } else if (currentScreen == ScreenType.EXPERIMENT) {
                    if (elapsedMs >= 5 * 60 * 1000L && trialNumber >= 100) {
                        // End experiment
                        // Will be finalized by caller via endExperiment(context)
                    }
                }
            }
        }
    }

    private fun stopSamplerOnly() {
        samplerJob?.cancel()
        samplerJob = null
    }

    private fun stopSamplingAndReset() {
        stopSamplerOnly()
        writingState = WritingState.IDLE
        isWritingTrigger = false
        latestPoint = null
        currentPath.rewind()
        elapsedMs = 0L
        trialNumber = 0
    }

    fun onPointerDown(point: Offset) {
        isWritingTrigger = true
        latestPoint = point
        when (writingState) {
            WritingState.IDLE -> {
                trialNumber += 1
                writingState = WritingState.WRITING
                currentPath.moveTo(point.x, point.y)
                pathVersion += 1
            }
            WritingState.WRITING -> {
                currentPath.lineTo(point.x, point.y)
                pathVersion += 1
            }
            WritingState.COOLDOWN -> {
                // resume same trial
                flushCooldownBuffer(zeroTrial = false)
                writingState = WritingState.WRITING
                // Do not visually connect segments across cooldown
                currentPath.moveTo(point.x, point.y)
                pathVersion += 1
            }
        }
    }

    fun onPointerMove(point: Offset) {
        isWritingTrigger = true
        latestPoint = point
        if (writingState == WritingState.WRITING) {
            currentPath.lineTo(point.x, point.y)
            pathVersion += 1
        }
    }

    fun onPointerUp() {
        isWritingTrigger = false
        cooldownStartMs = System.currentTimeMillis()
        if (writingState == WritingState.WRITING) {
            writingState = WritingState.COOLDOWN
            cooldownBuffer.clear()
        }
    }

    fun finalizePractice(context: Context): String {
        stopSamplerOnly()
        val path = csvLogger?.saveToFile(context) ?: ""
        lastSavedFilePath = path
        return path
    }

    fun canAutoEndExperiment(): Boolean {
        return (elapsedMs >= 5 * 60 * 1000L && trialNumber >= 100)
    }

    fun endExperiment(context: Context): EndSummary {
        stopSamplerOnly()
        val filePath = csvLogger?.saveToFile(context) ?: ""
        lastSavedFilePath = filePath
        val summary = EndSummary(
            subjectId = subjectId,
            trialsCollected = trialNumber,
            timeSpentMs = elapsedMs,
            filePath = filePath
        )
        currentScreen = ScreenType.END
        return summary
    }

    private fun flushCooldownBuffer(zeroTrial: Boolean) {
        val logger = csvLogger ?: return
        cooldownBuffer.forEach { s ->
            logger.append(
                timestampMs = s.timestampMs,
                writing = s.writing,
                trial = if (zeroTrial) 0 else s.trial,
                x = s.x,
                y = s.y
            )
        }
        cooldownBuffer.clear()
    }
}


