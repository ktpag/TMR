package com.example.tmr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.tmr.viewmodel.AppViewModel
import com.example.tmr.viewmodel.ScreenType
import com.example.tmr.viewmodel.WritingState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Size
import android.widget.Toast

@Composable
fun AppRoot(viewModel: AppViewModel) {
    Surface(color = MaterialTheme.colorScheme.background) {
        when (viewModel.currentScreen) {
            ScreenType.START -> StartScreen(viewModel)
            ScreenType.PRACTICE -> PracticeScreen(viewModel)
            ScreenType.EXPERIMENT -> ExperimentScreen(viewModel)
            ScreenType.END -> EndScreen(viewModel)
        }
    }
}

@Composable
fun StartScreen(viewModel: AppViewModel) {
    var subjectField by remember { mutableStateOf(TextFieldValue(viewModel.subjectId)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = subjectField,
            onValueChange = { value ->
                subjectField = value
                viewModel.updateSubjectId(value.text)
            },
            label = { Text("Subject ID") }
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.startPractice() }, enabled = viewModel.subjectId.isNotBlank()) {
                Text("Practice")
            }
            Button(onClick = { viewModel.startExperiment() }, enabled = viewModel.subjectId.isNotBlank()) {
                Text("Start Experiment")
            }
        }
    }
}

@Composable
fun PracticeScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        WritingCanvas(viewModel)
        // Overlay when 3 minutes passed (sampler stopped)
        if (viewModel.elapsedMs >= 3 * 60 * 1000L) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Practice finished", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        viewModel.finalizePractice(context)
                        if (viewModel.lastSavedFilePath.isNotBlank()) {
                            Toast.makeText(context, "Saved: ${viewModel.lastSavedFilePath}", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.startExperiment()
                    }) {
                        Text("Start Experiment")
                    }
                }
            }
        }
    }
}

@Composable
fun ExperimentScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        WritingCanvas(viewModel)
        LaunchedEffect(viewModel.elapsedMs, viewModel.trialNumber) {
            if (viewModel.canAutoEndExperiment()) {
                val summary = viewModel.endExperiment(context)
                if (summary.filePath.isNotBlank()) {
                    Toast.makeText(context, "Saved: ${summary.filePath}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun EndScreen(viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Experiment ended.")
        Spacer(Modifier.height(8.dp))
        Text("Subject: ${viewModel.subjectId}")
        Text("Trials: ${viewModel.trialNumber}")
        Text("Time: ${viewModel.elapsedMs / 1000}s")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.navigateToStart() }) { Text("Back to Start") }
    }
}

@Composable
private fun WritingCanvas(viewModel: AppViewModel) {
    var composePath by remember { mutableStateOf<Path?>(null) }
    val version = viewModel.pathVersion
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val area = computeMaxCenterSquare(canvasSize)
                    if (area.contains(down.position)) {
                        viewModel.onPointerDown(down.position)
                    } else {
                        // Touch outside writing area does not start writing
                    }
                    drag(down.id) { change ->
                        val areaDrag = computeMaxCenterSquare(canvasSize)
                        if (areaDrag.contains(change.position)) {
                            viewModel.onPointerMove(change.position)
                        } else if (viewModel.writingState == WritingState.WRITING) {
                            // Leaving area ends writing and starts cooldown
                            viewModel.onPointerUp()
                        }
                    }
                    viewModel.onPointerUp()
                }
            }
    ) {
        Canvas(Modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                canvasSize = Offset(sz.width.toFloat(), sz.height.toFloat())
            }
        ) {
            // composePath is recreated lazily from android Path for drawing
            if (composePath == null || version >= 0) {
                val p = Path()
                p.asAndroidPath().set(viewModel.currentPath)
                composePath = p
            }
            composePath?.let { path ->
                drawPath(path = path, color = Color.Black, style = Stroke(width = 6f))
            }

            // Draw center square boundary (as large as looks nice with margins)
            val area = computeMaxCenterSquare(Offset(size.width, size.height))
            drawRect(
                color = Color.Gray,
                topLeft = Offset(area.left, area.top),
                size = Size(area.width, area.height),
                style = Stroke(width = 3f)
            )
            // Draw cooldown timer bar above the square
            if (viewModel.writingState == WritingState.COOLDOWN) {
                val total = viewModel.cooldownDurationMs.toFloat().coerceAtLeast(1f)
                val remaining = viewModel.cooldownRemainingMs.coerceAtLeast(0L).toFloat()
                val progress = 1f - (remaining / total).coerceIn(0f, 1f)
                val barPadding = 12f
                val barHeight = 10f
                val barWidth = area.width
                val barLeft = area.left
                val barTop = (area.top - barPadding - barHeight).coerceAtLeast(8f)
                // track
                drawRect(
                    color = Color.LightGray.copy(alpha = 0.6f),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight)
                )
                // progress
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth * progress, barHeight)
                )
            }
        }
    }
}

private fun computeCenterSquare(size: Offset): Rect {
    val minDim = minOf(size.x, size.y)
    val side = minDim * 0.7f
    val left = (size.x - side) / 2f
    val top = (size.y - side) / 2f
    return Rect(left, top, left + side, top + side)
}

private fun computeMaxCenterSquare(size: Offset): Rect {
    // Large as possible with a small margin so it looks nice
    val margin = (minOf(size.x, size.y) * 0.05f).coerceAtLeast(16f)
    val usableWidth = (size.x - margin * 2).coerceAtLeast(0f)
    val usableHeight = (size.y - margin * 2).coerceAtLeast(0f)
    val side = minOf(usableWidth, usableHeight)
    val left = (size.x - side) / 2f
    val top = (size.y - side) / 2f
    return Rect(left, top, left + side, top + side)
}


