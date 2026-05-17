package com.rokoc.blockslide.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.rokoc.blockslide.MoveAnimation
import com.rokoc.blockslide.core.Block
import com.rokoc.blockslide.core.BlockColor
import com.rokoc.blockslide.core.BlockKind
import com.rokoc.blockslide.core.Cell
import com.rokoc.blockslide.core.Direction
import com.rokoc.blockslide.core.GameState
import com.rokoc.blockslide.core.Position
import com.rokoc.blockslide.core.Level
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
fun BoardCanvas(
    gameState: GameState,
    moveAnimation: MoveAnimation?,
    inputEnabled: Boolean,
    onCellTap: (Position) -> Unit,
    onSwipeFrom: (Position, Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var boardScale by rememberSaveable(gameState.level.id) { mutableFloatStateOf(1f) }
    var pan by remember(gameState.level.id) { mutableStateOf(Offset.Zero) }
    val layout = remember(containerSize, gameState.level) {
        BoardLayout.from(containerSize = containerSize, columns = gameState.level.width, rows = gameState.level.height)
    }
    val latestPan by rememberUpdatedState(pan)
    val latestScale by rememberUpdatedState(boardScale)
    val latestLayout by rememberUpdatedState(layout)
    val latestInputEnabled by rememberUpdatedState(inputEnabled)

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (boardScale * zoomChange).coerceIn(0.85f, 3f)
        boardScale = nextScale
        pan = if (nextScale <= 1f) Offset.Zero else pan + panChange
    }
    val animationProgress = remember(moveAnimation?.nonce) {
        Animatable(if (moveAnimation == null) 1f else 0f)
    }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    LaunchedEffect(moveAnimation?.nonce) {
        if (moveAnimation == null) {
            animationProgress.snapTo(1f)
        } else {
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = moveAnimation.durationMillis, easing = LinearEasing),
            )
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFFE8EFE7))
            .onSizeChanged { containerSize = it }
            .transformable(transformState)
            .pointerInput(gameState.level.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var current = start
                    var maxPointers = 1
                    do {
                        val event = awaitPointerEvent()
                        maxPointers = maxOf(maxPointers, event.changes.count { it.pressed })
                        event.changes.firstOrNull { it.id == down.id }?.let { change ->
                            current = change.position
                        }
                    } while (event.changes.any { it.pressed })

                    if (latestInputEnabled && maxPointers == 1) {
                        val delta = current - start
                        val distance = delta.getDistance()
                        if (distance > 44f && latestScale <= 1.1f) {
                            latestLayout.positionAt(
                                screenOffset = start,
                                pan = latestPan,
                                scale = latestScale,
                            )?.let { startCell ->
                                onSwipeFrom(startCell, delta.toDirection())
                            }
                        } else if (distance < 18f) {
                            latestLayout.positionAt(
                                screenOffset = start,
                                pan = latestPan,
                                scale = latestScale,
                            )?.let(onCellTap)
                        }
                    }
                }
            },
    ) {
        BoardCanvasContent(
            gameState = gameState,
            moveAnimation = moveAnimation,
            progress = animationProgress.value,
            layout = layout,
            pan = pan,
            boardScale = boardScale,
            labelPaint = labelPaint,
        )
    }
}

@Composable
fun BoardThumbnail(
    level: Level,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(containerSize, level) {
        BoardLayout.from(containerSize = containerSize, columns = level.width, rows = level.height)
    }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFFE8EFE7))
            .onSizeChanged { containerSize = it },
    ) {
        BoardCanvasContent(
            gameState = GameState(level),
            moveAnimation = null,
            progress = 1f,
            layout = layout,
            pan = Offset.Zero,
            boardScale = 1f,
            labelPaint = labelPaint,
        )
    }
}

@Composable
private fun BoardCanvasContent(
    gameState: GameState,
    moveAnimation: MoveAnimation?,
    progress: Float,
    layout: BoardLayout,
    pan: Offset,
    boardScale: Float,
    labelPaint: Paint,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color(0xFFE8EFE7))
        if (layout.cellSize <= 0f) return@Canvas
        drawRoundRect(
            color = Color(0xFFCBD9C2),
            topLeft = layout.playfieldOrigin,
            size = Size(layout.playfieldSize, layout.playfieldSize),
            cornerRadius = CornerRadius(layout.cellSize * 0.14f),
        )

        withTransform(
            transformBlock = {
                translate(left = layout.origin.x + pan.x, top = layout.origin.y + pan.y)
                scale(scaleX = boardScale, scaleY = boardScale, pivot = Offset.Zero)
            },
        ) {
            val boardSize = Size(
                width = layout.cellSize * gameState.level.width,
                height = layout.cellSize * gameState.level.height,
            )
            drawRoundRect(
                color = Color(0xFFD7E4D1),
                topLeft = Offset.Zero,
                size = boardSize,
                cornerRadius = CornerRadius(layout.cellSize * 0.12f),
            )

            drawCells(gameState = gameState, cellSize = layout.cellSize)
            drawTargets(gameState = gameState, cellSize = layout.cellSize)
            drawBlocks(
                gameState = gameState,
                moveAnimation = moveAnimation,
                progress = progress,
                cellSize = layout.cellSize,
                labelPaint = labelPaint,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCells(
    gameState: GameState,
    cellSize: Float,
) {
    for (row in 0 until gameState.level.height) {
        for (col in 0 until gameState.level.width) {
            val cell = gameState.level.cellAt(Position(row = row, col = col))
            if (cell == Cell.Void) continue
            val topLeft = Offset(x = col * cellSize, y = row * cellSize)
            val color = when (cell) {
                Cell.Floor -> Color(0xFFDDE9D7)
                Cell.Stopper -> Color(0xFFE9EFE9)
                Cell.Wall -> Color(0xFF66864E)
                Cell.Void -> Color.Transparent
            }
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(cellSize * 0.04f),
            )
            drawRoundRect(
                color = if (cell == Cell.Wall) Color(0xFF45613D) else Color(0x55FFFFFF),
                topLeft = topLeft,
                size = Size(cellSize, cellSize),
                style = Stroke(width = cellSize * 0.025f),
            )
            if (cell == Cell.Stopper) {
                drawLine(
                    color = Color(0xFFB9C3BF),
                    start = topLeft + Offset(cellSize * 0.2f, cellSize * 0.8f),
                    end = topLeft + Offset(cellSize * 0.8f, cellSize * 0.2f),
                    strokeWidth = cellSize * 0.06f,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTargets(
    gameState: GameState,
    cellSize: Float,
) {
    gameState.level.targets.forEach { target ->
        val topLeft = Offset(
            x = target.position.col * cellSize + cellSize * 0.22f,
            y = target.position.row * cellSize + cellSize * 0.22f,
        )
        val size = Size(cellSize * 0.56f, cellSize * 0.56f)
        drawRoundRect(
            color = target.color.uiColor().copy(alpha = 0.22f),
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(cellSize * 0.06f),
        )
        drawRoundRect(
            color = target.color.uiColor(),
            topLeft = topLeft,
            size = size,
            cornerRadius = CornerRadius(cellSize * 0.06f),
            style = Stroke(width = cellSize * 0.07f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlocks(
    gameState: GameState,
    moveAnimation: MoveAnimation?,
    progress: Float,
    cellSize: Float,
    labelPaint: Paint,
) {
    val blocks = gameState.blocks
        .filter { block -> !block.lost || (moveAnimation?.movementFor(block.id) != null && progress < 1f) }
        .sortedBy { it.id == gameState.selectedBlockId }
    blocks.forEach { block ->
        val position = block.drawPosition(moveAnimation = moveAnimation, progress = progress)
        val topLeft = Offset(
            x = position.col * cellSize + cellSize * 0.12f,
            y = position.row * cellSize + cellSize * 0.12f,
        )
        val size = Size(cellSize * 0.76f, cellSize * 0.76f)
        if (block.kind == BlockKind.Puck) {
            val center = topLeft + Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color(0x33000000),
                radius = size.width * 0.48f,
                center = center + Offset(cellSize * 0.04f, cellSize * 0.06f),
            )
            drawCircle(
                color = Color.White,
                radius = size.width * 0.48f,
                center = center,
            )
            drawCircle(
                color = block.color.uiColor(),
                radius = size.width * 0.35f,
                center = center,
            )
            drawCircle(
                color = if (block.id == gameState.selectedBlockId) Color(0xFF1B1F1D) else Color.White,
                radius = size.width * 0.48f,
                center = center,
                style = Stroke(width = if (block.id == gameState.selectedBlockId) cellSize * 0.07f else cellSize * 0.035f),
            )
        } else {
            drawRoundRect(
                color = Color(0x33000000),
                topLeft = topLeft + Offset(cellSize * 0.04f, cellSize * 0.06f),
                size = size,
                cornerRadius = CornerRadius(cellSize * 0.08f),
            )
            drawRoundRect(
                color = block.color.uiColor(),
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(cellSize * 0.08f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.65f),
                topLeft = topLeft + Offset(cellSize * 0.08f, cellSize * 0.08f),
                size = Size(cellSize * 0.22f, cellSize * 0.22f),
                cornerRadius = CornerRadius(cellSize * 0.03f),
            )
            drawRoundRect(
                color = if (block.id == gameState.selectedBlockId) Color(0xFF1B1F1D) else Color.White,
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(cellSize * 0.08f),
                style = Stroke(width = if (block.id == gameState.selectedBlockId) cellSize * 0.07f else cellSize * 0.035f),
            )
        }
        if (block.groupId != null) {
            drawCircle(
                color = Color(0xFF1B1F1D),
                radius = cellSize * 0.07f,
                center = topLeft + Offset(size.width * 0.86f, size.height * 0.14f),
            )
        }

        labelPaint.textSize = cellSize * 0.34f
        labelPaint.color = block.color.labelColor().toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            block.color.label(),
            topLeft.x + size.width / 2f,
            topLeft.y + size.height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f,
            labelPaint,
        )
    }
}

private fun Block.drawPosition(
    moveAnimation: MoveAnimation?,
    progress: Float,
): PositionFraction {
    val movement = moveAnimation?.movementFor(id) ?: return PositionFraction(
        row = position.row.toFloat(),
        col = position.col.toFloat(),
    )
    if (movement.from == movement.to) {
        return PositionFraction(row = position.row.toFloat(), col = position.col.toFloat())
    }
    val row = movement.from.row + (movement.to.row - movement.from.row) * progress
    val col = movement.from.col + (movement.to.col - movement.from.col) * progress
    return PositionFraction(row = row, col = col)
}

private fun Offset.toDirection(): Direction =
    if (abs(x) >= abs(y)) {
        if (x > 0f) Direction.Right else Direction.Left
    } else {
        if (y > 0f) Direction.Down else Direction.Up
    }

private fun BlockColor.uiColor(): Color =
    when (this) {
        BlockColor.Red -> Color(0xFFE53E3E)
        BlockColor.Blue -> Color(0xFF2B6CB0)
        BlockColor.Green -> Color(0xFF2F9E44)
        BlockColor.Yellow -> Color(0xFFE3B505)
        BlockColor.Pink -> Color(0xFFD53F8C)
        BlockColor.Cyan -> Color(0xFF00A3A3)
        BlockColor.Orange -> Color(0xFFE67E22)
        BlockColor.Purple -> Color(0xFF805AD5)
        BlockColor.White -> Color(0xFFE7ECEA)
        BlockColor.Gray -> Color(0xFF98A3A0)
    }

private fun BlockColor.label(): String =
    when (this) {
        BlockColor.Red -> "R"
        BlockColor.Blue -> "B"
        BlockColor.Green -> "G"
        BlockColor.Yellow -> "Y"
        BlockColor.Pink -> "P"
        BlockColor.Cyan -> "C"
        BlockColor.Orange -> "O"
        BlockColor.Purple -> "V"
        BlockColor.White -> "W"
        BlockColor.Gray -> "A"
    }

private fun BlockColor.labelColor(): Color =
    when (this) {
        BlockColor.Yellow, BlockColor.White, BlockColor.Gray -> Color(0xFF26302C)
        else -> Color.White
    }

private data class PositionFraction(
    val row: Float,
    val col: Float,
)

private data class BoardLayout(
    val origin: Offset,
    val cellSize: Float,
    val playfieldOrigin: Offset,
    val playfieldSize: Float,
    val columns: Int,
    val rows: Int,
) {
    fun positionAt(
        screenOffset: Offset,
        pan: Offset,
        scale: Float,
    ): Position? {
        if (cellSize <= 0f) return null
        val local = (screenOffset - origin - pan) / scale
        val row = floor(local.y / cellSize).toInt()
        val col = floor(local.x / cellSize).toInt()
        return if (row in 0 until rows && col in 0 until columns) Position(row = row, col = col) else null
    }

    companion object {
        fun from(
            containerSize: IntSize,
            columns: Int,
            rows: Int,
        ): BoardLayout {
            if (containerSize.width <= 0 || containerSize.height <= 0 || columns <= 0 || rows <= 0) {
                return BoardLayout(
                    origin = Offset.Zero,
                    cellSize = 0f,
                    playfieldOrigin = Offset.Zero,
                    playfieldSize = 0f,
                    columns = columns,
                    rows = rows,
                )
            }
            val playfieldSize = min(containerSize.width, containerSize.height) * 0.98f
            val cellSize = playfieldSize / max(columns, rows).toFloat()
            val boardWidth = cellSize * columns
            val boardHeight = cellSize * rows
            val playfieldOrigin = Offset(
                x = (containerSize.width - playfieldSize) / 2f,
                y = (containerSize.height - playfieldSize) / 2f,
            )
            return BoardLayout(
                origin = playfieldOrigin + Offset(
                    x = (playfieldSize - boardWidth) / 2f,
                    y = (playfieldSize - boardHeight) / 2f,
                ),
                cellSize = cellSize,
                playfieldOrigin = playfieldOrigin,
                playfieldSize = playfieldSize,
                columns = columns,
                rows = rows,
            )
        }
    }
}
