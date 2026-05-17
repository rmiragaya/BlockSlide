package com.rokoc.blockslide.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rokoc.blockslide.MoveAnimation
import com.rokoc.blockslide.core.Block
import com.rokoc.blockslide.core.BlockColor
import com.rokoc.blockslide.core.BlockKind
import com.rokoc.blockslide.core.Cell
import com.rokoc.blockslide.core.Direction
import com.rokoc.blockslide.core.GameEngine
import com.rokoc.blockslide.core.GameState
import com.rokoc.blockslide.core.Level
import com.rokoc.blockslide.core.Position
import com.rokoc.blockslide.core.Solver
import com.rokoc.blockslide.core.SolverResult
import com.rokoc.blockslide.core.Target
import com.rokoc.blockslide.core.canHoldPieces
import com.rokoc.blockslide.data.LevelJsonExporter
import kotlinx.coroutines.delay

private const val EDITOR_SOLVER_MAX_STATES = 8_000
private const val EDITOR_SOLVER_MAX_DEPTH = 50

@Composable
fun LevelEditorScreen(
    onClose: () -> Unit,
) {
    var draft by remember { mutableStateOf(LevelDraft.blank(width = 8, height = 8)) }
    var tool by remember { mutableStateOf(EditorTool.Block) }
    var color by remember { mutableStateOf(BlockColor.Red) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(EditorMode.Edit) }
    var playState by remember { mutableStateOf<GameState?>(null) }
    var playAnimation by remember { mutableStateOf<MoveAnimation?>(null) }
    var playLocked by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Listo para editar.") }
    var exportedJson by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val draftLevel = remember(draft) { draft.toLevel() }

    fun clearGeneratedOutput() {
        exportedJson = null
        statusText = "Editando ${draft.width}x${draft.height}."
    }

    fun startPlaytest() {
        val level = draft.toLevel()
        playState = GameEngine.newGame(level)
        playAnimation = null
        playLocked = false
        mode = EditorMode.Playtest
        statusText = "Probando ${level.title}."
    }

    fun runSolver() {
        val result = Solver.solve(
            level = draft.toLevel(),
            maxStates = EDITOR_SOLVER_MAX_STATES,
            maxDepth = EDITOR_SOLVER_MAX_DEPTH,
        )
        statusText = result.editorLabel()
    }

    fun exportJson() {
        val json = LevelJsonExporter.exportLevel(draft.toLevel())
        exportedJson = json
        copyToClipboard(context = context, text = json)
        statusText = "JSON exportado y copiado al portapapeles."
    }

    fun applyPlayMove(blockId: String, direction: Direction) {
        val state = playState ?: return
        if (playLocked) return
        val result = GameEngine.moveBlock(state = state, blockId = blockId, direction = direction)
        val animation = if (result.moved && result.movements.isNotEmpty()) {
            MoveAnimation(nonce = System.nanoTime(), movements = result.movements)
        } else {
            null
        }
        playState = result.state
        playAnimation = animation ?: playAnimation
        playLocked = animation != null
        statusText = when {
            result.state.isSolved -> "Playtest: nivel completo en ${result.state.moves} movimientos."
            result.state.levelFailed -> "Playtest: una pieza necesaria se fue del tablero."
            result.moved -> "Playtest: ${result.state.moves} movimientos."
            else -> "Playtest: ese movimiento no cambia nada."
        }
    }

    LaunchedEffect(playAnimation?.nonce) {
        val animation = playAnimation ?: return@LaunchedEffect
        delay(animation.durationMillis.toLong())
        if (playAnimation?.nonce == animation.nonce) {
            playLocked = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EditorHeader(
            mode = mode,
            statusText = statusText,
            onClose = onClose,
            onEdit = {
                mode = EditorMode.Edit
                playLocked = false
                playAnimation = null
                statusText = "Editando ${draft.width}x${draft.height}."
            },
            onPlaytest = ::startPlaytest,
        )

        DraftMetadataControls(
            draft = draft,
            onDraftChanged = { next ->
                draft = next
                mode = EditorMode.Edit
                clearGeneratedOutput()
            },
        )

        if (mode == EditorMode.Edit) {
            BoardCanvas(
                gameState = GameState(draftLevel),
                moveAnimation = null,
                inputEnabled = true,
                onCellTap = { position ->
                    draft = draft.paint(
                        position = position,
                        tool = tool,
                        color = color,
                        groupId = groupId,
                    )
                    clearGeneratedOutput()
                },
                onSwipeFrom = { _, _ -> },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            val state = playState ?: GameEngine.newGame(draftLevel)
            BoardCanvas(
                gameState = state,
                moveAnimation = playAnimation,
                inputEnabled = !playLocked,
                onCellTap = { position ->
                    state.blockAt(position)?.let { block ->
                        playState = GameEngine.selectBlock(state, block.id)
                    }
                },
                onSwipeFrom = { position, direction ->
                    state.blockAt(position)?.let { block ->
                        applyPlayMove(block.id, direction)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        if (mode == EditorMode.Edit) {
            EditorToolPanel(
                selectedTool = tool,
                selectedColor = color,
                selectedGroupId = groupId,
                warnings = draft.warnings(),
                onToolSelected = { tool = it },
                onColorSelected = { color = it },
                onGroupSelected = { groupId = it },
            )
        } else {
            PlaytestControls(
                state = playState,
                enabled = !playLocked,
                onUndo = { playState = playState?.let(GameEngine::undo) },
                onReset = { playState = GameEngine.newGame(draft.toLevel()) },
            )
        }

        EditorActions(
            exportedJson = exportedJson,
            onValidate = ::runSolver,
            onPlaytest = ::startPlaytest,
            onExport = ::exportJson,
            onCopy = {
                exportedJson?.let { json ->
                    copyToClipboard(context = context, text = json)
                    statusText = "JSON copiado al portapapeles."
                }
            },
        )
    }
}

@Composable
private fun EditorHeader(
    mode: EditorMode,
    statusText: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onPlaytest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onClose) {
            Text("Volver")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Editor interno",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (mode == EditorMode.Edit) {
            Button(onClick = onPlaytest) {
                Text("Probar")
            }
        } else {
            Button(onClick = onEdit) {
                Text("Editar")
            }
        }
    }
}

@Composable
private fun DraftMetadataControls(
    draft: LevelDraft,
    onDraftChanged: (LevelDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onDraftChanged(draft.copy(title = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Titulo") },
            )
            OutlinedTextField(
                value = draft.id,
                onValueChange = { onDraftChanged(draft.copy(id = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Id") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stepper(
                label = "Ancho",
                value = draft.width,
                onMinus = { onDraftChanged(draft.resize(width = draft.width - 1, height = draft.height)) },
                onPlus = { onDraftChanged(draft.resize(width = draft.width + 1, height = draft.height)) },
            )
            Stepper(
                label = "Alto",
                value = draft.height,
                onMinus = { onDraftChanged(draft.resize(width = draft.width, height = draft.height - 1)) },
                onPlus = { onDraftChanged(draft.resize(width = draft.width, height = draft.height + 1)) },
            )
            OutlinedButton(
                modifier = Modifier.height(42.dp),
                onClick = {
                    onDraftChanged(
                        LevelDraft.blank(width = draft.width, height = draft.height)
                            .copy(id = draft.id, title = draft.title),
                    )
                },
            ) {
                Text("Limpiar")
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            modifier = Modifier.size(width = 42.dp, height = 42.dp),
            onClick = onMinus,
        ) {
            Text("-")
        }
        OutlinedButton(
            modifier = Modifier.size(width = 42.dp, height = 42.dp),
            onClick = onPlus,
        ) {
            Text("+")
        }
    }
}

@Composable
private fun EditorToolPanel(
    selectedTool: EditorTool,
    selectedColor: BlockColor,
    selectedGroupId: String?,
    warnings: List<String>,
    onToolSelected: (EditorTool) -> Unit,
    onColorSelected: (BlockColor) -> Unit,
    onGroupSelected: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EditorTool.entries) { tool ->
                SelectableButton(
                    text = tool.label,
                    selected = selectedTool == tool,
                    onClick = { onToolSelected(tool) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BlockColor.entries) { color ->
                ColorSwatch(
                    color = color,
                    selected = selectedColor == color,
                    onClick = { onColorSelected(color) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SelectableButton(
                    text = "Sin grupo",
                    selected = selectedGroupId == null,
                    onClick = { onGroupSelected(null) },
                )
            }
            items(listOf("a", "b", "c", "d")) { id ->
                SelectableButton(
                    text = "Grupo ${id.uppercase()}",
                    selected = selectedGroupId == id,
                    onClick = { onGroupSelected(id) },
                )
            }
        }
        if (warnings.isNotEmpty()) {
            Text(
                text = warnings.joinToString(separator = "  |  "),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaytestControls(
    state: GameState?,
    enabled: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onUndo,
            enabled = enabled && state?.history?.isNotEmpty() == true,
        ) {
            Text("Deshacer")
        }
        OutlinedButton(
            modifier = Modifier.padding(start = 8.dp),
            onClick = onReset,
            enabled = enabled,
        ) {
            Text("Reiniciar prueba")
        }
    }
}

@Composable
private fun EditorActions(
    exportedJson: String?,
    onValidate: () -> Unit,
    onPlaytest: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onValidate,
            ) {
                Text("Validar")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onPlaytest,
            ) {
                Text("Probar")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onExport,
            ) {
                Text("Exportar")
            }
        }
        exportedJson?.let { json ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "JSON generado",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onCopy) {
                    Text("Copiar")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 170.dp)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outline)
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SelectableButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text)
        }
    }
}

@Composable
private fun ColorSwatch(
    color: BlockColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            .background(color.editorColor())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = color.editorLabel(),
            color = color.editorLabelColor(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun SolverResult.editorLabel(): String =
    when (this) {
        is SolverResult.Solved -> "Solver: resuelto en ${moves.size} movimientos, $visitedStates estados."
        is SolverResult.Unsolved -> "Solver: no encontro solucion en $visitedStates estados."
        is SolverResult.LimitReached -> "Solver: llego al limite de $visitedStates estados."
    }

private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("BlockSlide level", text))
}

private enum class EditorMode {
    Edit,
    Playtest,
}

private enum class EditorTool(
    val label: String,
) {
    Floor("Piso"),
    Wall("Pared"),
    Void("Vacio"),
    Stopper("Freno"),
    Block("Bloque"),
    Puck("Bola"),
    Target("Objetivo"),
    Erase("Borrar"),
}

private data class LevelDraft(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val cells: List<List<Cell>>,
    val blocks: List<Block>,
    val targets: List<Target>,
) {
    fun resize(
        width: Int,
        height: Int,
    ): LevelDraft {
        val nextWidth = width.coerceIn(3, 24)
        val nextHeight = height.coerceIn(3, 24)
        val nextCells = List(nextHeight) { row ->
            List(nextWidth) { col ->
                cells.getOrNull(row)?.getOrNull(col) ?: defaultCell(row = row, col = col, width = nextWidth, height = nextHeight)
            }
        }
        return copy(
            width = nextWidth,
            height = nextHeight,
            cells = nextCells,
            blocks = blocks.filter { block -> block.position.isInside(nextWidth, nextHeight) && nextCells.cellAt(block.position).canHoldPieces },
            targets = targets.filter { target -> target.position.isInside(nextWidth, nextHeight) && nextCells.cellAt(target.position).canHoldPieces },
        )
    }

    fun paint(
        position: Position,
        tool: EditorTool,
        color: BlockColor,
        groupId: String?,
    ): LevelDraft {
        if (!position.isInside(width, height)) return this
        return when (tool) {
            EditorTool.Floor -> withCell(position = position, cell = Cell.Floor)
            EditorTool.Wall -> withCell(position = position, cell = Cell.Wall).withoutPiecesAt(position)
            EditorTool.Void -> withCell(position = position, cell = Cell.Void).withoutPiecesAt(position)
            EditorTool.Stopper -> withCell(position = position, cell = Cell.Stopper)
            EditorTool.Block -> withBlock(position = position, color = color, kind = BlockKind.Block, groupId = groupId)
            EditorTool.Puck -> withBlock(position = position, color = color, kind = BlockKind.Puck, groupId = groupId)
            EditorTool.Target -> withTarget(position = position, color = color)
            EditorTool.Erase -> withoutPiecesAt(position).withCell(position = position, cell = Cell.Floor)
        }
    }

    fun toLevel(): Level =
        Level(
            id = id.normalizedId(),
            title = title.ifBlank { "Untitled" },
            width = width,
            height = height,
            cells = cells,
            blocks = blocks.sortedWith(compareBy({ it.position.row }, { it.position.col }, { it.id })),
            targets = targets.sortedWith(compareBy({ it.position.row }, { it.position.col }, { it.color.name })),
        )

    fun warnings(): List<String> =
        buildList {
            if (blocks.none { it.required }) add("Falta al menos un bloque requerido.")
            if (targets.isEmpty()) add("Falta al menos un objetivo.")
            val targetColors = targets.map { it.color }.toSet()
            val blockColors = blocks.filter { it.required }.map { it.color }.toSet()
            val missingTargets = blockColors - targetColors
            val missingBlocks = targetColors - blockColors
            if (missingTargets.isNotEmpty()) add("Sin objetivo para: ${missingTargets.joinToString { it.name }}.")
            if (missingBlocks.isNotEmpty()) add("Sin bloque requerido para: ${missingBlocks.joinToString { it.name }}.")
        }

    private fun withCell(
        position: Position,
        cell: Cell,
    ): LevelDraft =
        copy(cells = cells.replaceCell(position = position, cell = cell))

    private fun withBlock(
        position: Position,
        color: BlockColor,
        kind: BlockKind,
        groupId: String?,
    ): LevelDraft {
        val playableDraft = if (cells.cellAt(position).canHoldPieces) this else withCell(position = position, cell = Cell.Floor)
        return playableDraft.copy(
            blocks = playableDraft.blocks.filterNot { it.position == position } + Block(
                id = playableDraft.nextBlockId(color),
                color = color,
                position = position,
                kind = kind,
                groupId = groupId,
            ),
        )
    }

    private fun withTarget(
        position: Position,
        color: BlockColor,
    ): LevelDraft {
        val playableDraft = if (cells.cellAt(position).canHoldPieces) this else withCell(position = position, cell = Cell.Floor)
        return playableDraft.copy(
            targets = playableDraft.targets.filterNot { it.position == position } + Target(
                color = color,
                position = position,
            ),
        )
    }

    private fun withoutPiecesAt(position: Position): LevelDraft =
        copy(
            blocks = blocks.filterNot { it.position == position },
            targets = targets.filterNot { it.position == position },
        )

    private fun nextBlockId(color: BlockColor): String {
        val prefix = color.idPrefix()
        var index = 1
        while (blocks.any { it.id == "$prefix$index" }) {
            index += 1
        }
        return "$prefix$index"
    }

    companion object {
        fun blank(
            width: Int,
            height: Int,
        ): LevelDraft {
            val safeWidth = width.coerceIn(3, 24)
            val safeHeight = height.coerceIn(3, 24)
            return LevelDraft(
                id = "custom-level",
                title = "Custom Level",
                width = safeWidth,
                height = safeHeight,
                cells = List(safeHeight) { row ->
                    List(safeWidth) { col ->
                        defaultCell(row = row, col = col, width = safeWidth, height = safeHeight)
                    }
                },
                blocks = emptyList(),
                targets = emptyList(),
            )
        }
    }
}

private fun Position.isInside(
    width: Int,
    height: Int,
): Boolean =
    row in 0 until height && col in 0 until width

private fun List<List<Cell>>.cellAt(position: Position): Cell =
    this[position.row][position.col]

private fun List<List<Cell>>.replaceCell(
    position: Position,
    cell: Cell,
): List<List<Cell>> =
    mapIndexed { row, cells ->
        if (row != position.row) {
            cells
        } else {
            cells.mapIndexed { col, current -> if (col == position.col) cell else current }
        }
    }

private fun defaultCell(
    row: Int,
    col: Int,
    width: Int,
    height: Int,
): Cell =
    if (row == 0 || col == 0 || row == height - 1 || col == width - 1) {
        Cell.Wall
    } else {
        Cell.Floor
    }

private fun String.normalizedId(): String {
    val normalized = trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "-")
        .trim('-')
    return normalized.ifBlank { "custom-level" }
}

private fun BlockColor.idPrefix(): String =
    when (this) {
        BlockColor.Red -> "r"
        BlockColor.Blue -> "b"
        BlockColor.Green -> "g"
        BlockColor.Yellow -> "y"
        BlockColor.Pink -> "p"
        BlockColor.Cyan -> "c"
        BlockColor.Orange -> "o"
        BlockColor.Purple -> "v"
        BlockColor.White -> "w"
        BlockColor.Gray -> "gy"
    }

private fun BlockColor.editorLabel(): String =
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

private fun BlockColor.editorColor(): Color =
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

private fun BlockColor.editorLabelColor(): Color =
    when (this) {
        BlockColor.Yellow, BlockColor.White, BlockColor.Gray -> Color(0xFF26302C)
        else -> Color.White
    }
