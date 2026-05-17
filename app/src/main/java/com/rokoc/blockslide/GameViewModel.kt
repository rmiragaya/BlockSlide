package com.rokoc.blockslide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rokoc.blockslide.core.BlockMovement
import com.rokoc.blockslide.core.Direction
import com.rokoc.blockslide.core.GameEngine
import com.rokoc.blockslide.core.GameState
import com.rokoc.blockslide.core.Level
import com.rokoc.blockslide.core.Position
import com.rokoc.blockslide.data.LevelRepository
import com.rokoc.blockslide.data.ProgressRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MOVE_ANIMATION_BASE_MILLIS = 90
private const val MOVE_ANIMATION_PER_CELL_MILLIS = 70
private const val MOVE_ANIMATION_MIN_MILLIS = 130
private const val MOVE_ANIMATION_MAX_MILLIS = 620

data class MoveAnimation(
    val nonce: Long,
    val movements: List<BlockMovement>,
    val durationMillis: Int = movements.animationDurationMillis(),
) {
    fun movementFor(blockId: String): BlockMovement? = movements.firstOrNull { it.blockId == blockId }
}

private fun List<BlockMovement>.animationDurationMillis(): Int {
    val maxDistance = maxOfOrNull { it.gridDistance() } ?: 0
    return (MOVE_ANIMATION_BASE_MILLIS + maxDistance * MOVE_ANIMATION_PER_CELL_MILLIS)
        .coerceIn(MOVE_ANIMATION_MIN_MILLIS, MOVE_ANIMATION_MAX_MILLIS)
}

private fun BlockMovement.gridDistance(): Int =
    abs(to.row - from.row) + abs(to.col - from.col)

data class GameUiState(
    val levels: List<Level> = emptyList(),
    val levelIndex: Int = 0,
    val gameState: GameState? = null,
    val solvedLevelIds: Set<String> = emptySet(),
    val lastMove: MoveAnimation? = null,
    val inputLocked: Boolean = false,
    val errorMessage: String? = null,
)

class GameViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val levelRepository = LevelRepository(application)
    private val progressRepository = ProgressRepository(application)
    private val _uiState = MutableStateFlow(GameUiState())

    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        val levels = runCatching { levelRepository.loadLevels() }
        levels.onSuccess { loaded ->
            val firstState = loaded.firstOrNull()?.let(GameEngine::newGame)
            _uiState.value = GameUiState(levels = loaded, gameState = firstState)
            restoreProgress(loaded)
            observeSolvedLevels()
        }.onFailure { error ->
            _uiState.value = GameUiState(errorMessage = error.message ?: "No se pudieron cargar los niveles.")
        }
    }

    fun tapCell(position: Position) {
        if (_uiState.value.inputLocked) return
        val state = _uiState.value.gameState ?: return
        val tappedBlock = state.blockAt(position)
        if (tappedBlock != null) {
            _uiState.update { it.copy(gameState = GameEngine.selectBlock(state, tappedBlock.id)) }
            return
        }

        val selected = state.selectedBlock() ?: return
        val direction = when {
            position.row == selected.position.row && position.col > selected.position.col -> Direction.Right
            position.row == selected.position.row && position.col < selected.position.col -> Direction.Left
            position.col == selected.position.col && position.row > selected.position.row -> Direction.Down
            position.col == selected.position.col && position.row < selected.position.row -> Direction.Up
            else -> null
        }
        direction?.let(::moveSelected)
    }

    fun swipeFrom(position: Position, direction: Direction) {
        val state = _uiState.value.gameState ?: return
        if (_uiState.value.inputLocked) return
        val block = state.blockAt(position) ?: return
        moveBlock(block.id, direction)
    }

    fun moveSelected(direction: Direction) {
        if (_uiState.value.inputLocked) return
        val state = _uiState.value.gameState ?: return
        val blockId = state.selectedBlockId ?: return
        moveBlock(blockId = blockId, direction = direction)
    }

    private fun moveBlock(
        blockId: String,
        direction: Direction,
    ) {
        val state = _uiState.value.gameState ?: return
        val result = GameEngine.moveBlock(state = state, blockId = blockId, direction = direction)
        val animation = if (result.moved && result.movements.isNotEmpty()) {
            MoveAnimation(
                nonce = System.nanoTime(),
                movements = result.movements,
            )
        } else {
            null
        }
        _uiState.update {
            it.copy(
                gameState = result.state,
                lastMove = animation ?: it.lastMove,
                inputLocked = animation != null,
            )
        }
        animation?.let { moveAnimation ->
            unlockInputAfter(moveAnimation.nonce)
        }
        if (result.moved && result.state.isSolved) {
            viewModelScope.launch {
                progressRepository.markSolved(result.state.level.id)
            }
        }
    }

    fun undo() {
        if (_uiState.value.inputLocked) return
        val state = _uiState.value.gameState ?: return
        _uiState.update { it.copy(gameState = GameEngine.undo(state), lastMove = null, inputLocked = false) }
    }

    fun reset() {
        if (_uiState.value.inputLocked) return
        val state = _uiState.value.gameState ?: return
        _uiState.update { it.copy(gameState = GameEngine.reset(state), lastMove = null, inputLocked = false) }
    }

    fun previousLevel() {
        if (_uiState.value.inputLocked) return
        val current = _uiState.value
        openLevel((current.levelIndex - 1).coerceAtLeast(0))
    }

    fun nextLevel() {
        if (_uiState.value.inputLocked) return
        val current = _uiState.value
        openLevel((current.levelIndex + 1).coerceAtMost((current.levels.size - 1).coerceAtLeast(0)))
    }

    fun openLevel(index: Int) {
        if (_uiState.value.inputLocked) return
        val levels = _uiState.value.levels
        if (levels.isEmpty()) return
        val safeIndex = index.coerceIn(0, levels.lastIndex)
        openLevelInternal(levels = levels, index = safeIndex)
        viewModelScope.launch {
            progressRepository.setCurrentLevel(safeIndex)
        }
    }

    private fun restoreProgress(levels: List<Level>) {
        viewModelScope.launch {
            val restoredIndex = progressRepository.currentLevelIndex.first()
                .coerceIn(0, (levels.size - 1).coerceAtLeast(0))
            openLevelInternal(levels = levels, index = restoredIndex)
        }
    }

    private fun observeSolvedLevels() {
        viewModelScope.launch {
            progressRepository.solvedLevelIds.collect { solvedIds ->
                _uiState.update { it.copy(solvedLevelIds = solvedIds) }
            }
        }
    }

    private fun openLevelInternal(
        levels: List<Level>,
        index: Int,
    ) {
        val level = levels[index]
        _uiState.update {
            it.copy(
                levels = levels,
                levelIndex = index,
                gameState = GameEngine.newGame(level),
                lastMove = null,
                inputLocked = false,
            )
        }
    }

    private fun unlockInputAfter(animationNonce: Long) {
        viewModelScope.launch {
            delay(stateAnimationDuration(animationNonce).toLong())
            _uiState.update { state ->
                if (state.lastMove?.nonce == animationNonce) {
                    state.copy(inputLocked = false)
                } else {
                    state
                }
            }
        }
    }

    private fun stateAnimationDuration(animationNonce: Long): Int =
        _uiState.value.lastMove
            ?.takeIf { it.nonce == animationNonce }
            ?.durationMillis
            ?: MOVE_ANIMATION_MIN_MILLIS
}
