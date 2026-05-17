package com.rokoc.blockslide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rokoc.blockslide.GameUiState
import com.rokoc.blockslide.GameViewModel
import com.rokoc.blockslide.core.Direction
import com.rokoc.blockslide.core.Position

@Composable
fun BlockSlideApp(
    viewModel: GameViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditor by rememberSaveable { mutableStateOf(false) }

    BlockSlideTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                if (showEditor) {
                    LevelEditorScreen(
                        onClose = { showEditor = false },
                    )
                } else {
                    GameScreen(
                        uiState = uiState,
                        onTapCell = viewModel::tapCell,
                        onSwipeFrom = viewModel::swipeFrom,
                        onUndo = viewModel::undo,
                        onReset = viewModel::reset,
                        onPrevious = viewModel::previousLevel,
                        onNext = viewModel::nextLevel,
                        onLevelSelected = viewModel::openLevel,
                        onOpenEditor = { showEditor = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameScreen(
    uiState: GameUiState,
    onTapCell: (Position) -> Unit,
    onSwipeFrom: (Position, Direction) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLevelSelected: (Int) -> Unit,
    onOpenEditor: () -> Unit,
) {
    val gameState = uiState.gameState
    if (uiState.errorMessage != null) {
        Text(
            text = uiState.errorMessage,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    if (gameState == null) {
        Text(
            text = "Cargando niveles",
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            textAlign = TextAlign.Center,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(
            uiState = uiState,
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenEditor = onOpenEditor,
        )

        BoardCanvas(
            gameState = gameState,
            moveAnimation = uiState.lastMove,
            inputEnabled = !uiState.inputLocked,
            onCellTap = onTapCell,
            onSwipeFrom = onSwipeFrom,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        if (gameState.levelFailed) {
            Text(
                text = "Una pieza necesaria se fue del tablero. Reinicia o deshace el movimiento.",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (gameState.isSolved) {
            Text(
                text = "Nivel completo",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        LevelStrip(
            uiState = uiState,
            onLevelSelected = onLevelSelected,
        )

        Controls(
            canUndo = gameState.history.isNotEmpty(),
            enabled = !uiState.inputLocked,
            onUndo = onUndo,
            onReset = onReset,
        )
    }
}

@Composable
private fun Header(
    uiState: GameUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val gameState = uiState.gameState ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = !uiState.inputLocked && uiState.levelIndex > 0,
        ) {
            Text("Prev")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Block Slide",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Nivel ${uiState.levelIndex + 1}/${uiState.levels.size} - ${gameState.level.title} - ${gameState.moves} movs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = onNext,
            enabled = !uiState.inputLocked && uiState.levelIndex < uiState.levels.lastIndex,
        ) {
            Text("Next")
        }
        OutlinedButton(
            onClick = onOpenEditor,
            enabled = !uiState.inputLocked,
        ) {
            Text("Editor")
        }
    }
}

@Composable
private fun LevelStrip(
    uiState: GameUiState,
    onLevelSelected: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(uiState.levels) { index, level ->
            val solved = level.id in uiState.solvedLevelIds
            if (index == uiState.levelIndex) {
                Button(
                    onClick = { onLevelSelected(index) },
                    enabled = !uiState.inputLocked,
                ) {
                    Text("${index + 1}${if (solved) "*" else ""}")
                }
            } else {
                OutlinedButton(
                    onClick = { onLevelSelected(index) },
                    enabled = !uiState.inputLocked,
                ) {
                    Text("${index + 1}${if (solved) "*" else ""}")
                }
            }
        }
    }
}

@Composable
private fun Controls(
    canUndo: Boolean,
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
            enabled = enabled && canUndo,
        ) {
            Text("Deshacer")
        }
        OutlinedButton(
            modifier = Modifier.padding(start = 8.dp),
            onClick = onReset,
            enabled = enabled,
        ) {
            Text("Reiniciar")
        }
    }
}
