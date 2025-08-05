import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.OutputStreamWriter

// Logger class remains the same
class Logger {
    var isLogging by mutableStateOf(false)
        private set

    private var startTime = 0L
    private val logs = mutableListOf<String>()

    fun start() {
        if (isLogging) return
        startTime = System.currentTimeMillis()
        logs.clear()
        isLogging = true
        log(writing = 0)
    }

    fun end() {
        if (!isLogging) return
        log(writing = 0)
        isLogging = false
    }

    fun log(writing: Int, x: Float? = null, y: Float? = null) {
        if (!isLogging) return
        val timestamp = System.currentTimeMillis() - startTime
        val xPos = x?.toInt()?.toString() ?: ""
        val yPos = y?.toInt()?.toString() ?: ""
        logs.add("$timestamp,$writing,$xPos,$yPos")
    }

    fun getCsvData(): String {
        return "timestamp,writing,x,y\n" + logs.joinToString("\n")
    }
}

@Composable
fun DrawView() {
    var paths by remember { mutableStateOf(listOf<PathWithColor>()) }
    var currentPath by remember { mutableStateOf<PathWithColor?>(null) }
    // ★★★ 실시간 그리기를 위한 상태 트리거를 다시 추가합니다.
    var redrawTrigger by remember { mutableStateOf(false) }
    val logger = remember { Logger() }
    val context = LocalContext.current

    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            OutputStreamWriter(outputStream).use { writer ->
                                writer.write(logger.getCsvData())
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(Unit) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    LaunchedEffect(logger.isLogging) {
        if (logger.isLogging) {
            while (isActive) {
                logger.log(writing = 0)
                delay(50)
            }
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { paths = emptyList() }) {
                    Text("Erase All")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { logger.start() }, enabled = !logger.isLogging) {
                    Text("Start")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { logger.end() }, enabled = logger.isLogging) {
                    Text("End")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/csv"
                            putExtra(Intent.EXTRA_TITLE, "drawing_log_${System.currentTimeMillis()}.csv")
                        }
                        fileSaverLauncher.launch(intent)
                    },
                    enabled = !logger.isLogging && logger.getCsvData().lines().size > 1
                ) {
                    Text("CSV")
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val path = Path().apply {
                                moveTo(down.position.x, down.position.y)
                                lineTo(down.position.x, down.position.y)
                            }
                            currentPath = PathWithColor(path, Color.Black)
                            logger.log(writing = 1, x = down.position.x, y = down.position.y)

                            // ★★★ 첫 터치 지점을 그리도록 트리거
                            redrawTrigger = !redrawTrigger

                            drag(down.id) { change ->
                                currentPath?.path?.lineTo(change.position.x, change.position.y)
                                logger.log(writing = 1, x = change.position.x, y = change.position.y)

                                // ★★★ 드래그 중 매 순간 그리도록 트리거
                                redrawTrigger = !redrawTrigger
                            }

                            currentPath?.let { paths = paths + it }
                            currentPath = null
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // ★★★ 트리거를 읽어서 상태 변경을 감지하도록 합니다.
                    val trigger = redrawTrigger

                    paths.forEach { pathWithColor ->
                        drawPath(path = pathWithColor.path, color = pathWithColor.color, style = Stroke(width = 7f))
                    }
                    currentPath?.let { pathWithColor ->
                        drawPath(path = pathWithColor.path, color = pathWithColor.color, style = Stroke(width = 7f))
                    }
                }
            }
        }
    }
}

data class PathWithColor(val path: Path, val color: Color)
