import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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

    // This effect runs once to set the status bar icon colors to dark.
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(Unit) {
            val window = (view.context as Activity).window
            // This tells the system that the status bar has a light background and icons should be dark.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    LaunchedEffect(logger.isLogging) {
        if (logger.isLogging) {
            while (true) {
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
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = PathWithColor(Path().apply { moveTo(offset.x, offset.y) }, Color.Black)
                                logger.log(writing = 1, x = offset.x, y = offset.y)
                            },
                            onDragEnd = {
                                currentPath?.let { paths = paths + it }
                                currentPath = null
                            }
                        ) { change, _ ->
                            change.consume()
                            currentPath?.path?.lineTo(change.position.x, change.position.y)
                            logger.log(writing = 1, x = change.position.x, y = change.position.y)
                            redrawTrigger = !redrawTrigger
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
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
