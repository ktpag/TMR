import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun DrawView() {
    var paths by remember { mutableStateOf(listOf<PathWithColor>()) }
    var currentPath by remember { mutableStateOf<PathWithColor?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                while (true) {
                    // 1. 다운 이벤트 대기
                    val down = awaitPointerEventScope { awaitPointerEvent(PointerEventPass.Main) }
                    val firstDown = down.changes.firstOrNull { it.pressed }
                    if (firstDown == null) continue

                    // 2. path 시작
                    currentPath = PathWithColor(Path().apply {
                        moveTo(firstDown.position.x, firstDown.position.y)
                    }, Color.Black)

                    awaitPointerEventScope {
                        while (true) {
                            // move or up 이벤트 실시간 추적
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pointer = event.changes.firstOrNull { it.pressed }
                            if (pointer != null) {
                                currentPath?.path?.lineTo(pointer.position.x, pointer.position.y)
                            } else {
                                // up 이벤트 시 path를 리스트에 저장
                                currentPath?.let {
                                    paths = paths + it
                                }
                                currentPath = null
                                break
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            paths.forEach { pathWithColor ->
                drawPath(
                    path = pathWithColor.path,
                    color = pathWithColor.color,
                    style = Stroke(width = 7f)
                )
            }
            currentPath?.let { pathWithColor ->
                drawPath(
                    path = pathWithColor.path,
                    color = pathWithColor.color,
                    style = Stroke(width = 7f)
                )
            }
        }
    }
}

data class PathWithColor(val path: Path, val color: Color)
