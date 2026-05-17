package com.rokoc.blockslide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rokoc.blockslide.R
import com.rokoc.blockslide.core.Level
import com.rokoc.blockslide.core.Solver
import com.rokoc.blockslide.core.SolverResult
import com.rokoc.blockslide.data.LevelPackParser

@Preview(
    name = "Level Gallery",
    showBackground = true,
    widthDp = 1200,
    heightDp = 1600,
)
@Composable
fun LevelGalleryPreview() {
    val context = LocalContext.current
    val levels = remember {
        val json = context.resources.openRawResource(R.raw.levels).bufferedReader().use { it.readText() }
        LevelPackParser.parse(json)
    }

    BlockSlideTheme {
        LevelGallery(levels = levels)
    }
}

@Composable
private fun LevelGallery(
    levels: List<Level>,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 190.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(levels) { index, level ->
            val solverResult = remember(level.id) {
                Solver.solve(level = level, maxStates = 2_000, maxDepth = 20)
            }
            LevelPreviewCard(
                index = index,
                level = level,
                solverResult = solverResult,
            )
        }
    }
}

@Composable
private fun LevelPreviewCard(
    index: Int,
    level: Level,
    solverResult: SolverResult,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = solverResult.previewColor())
            .background(Color.White)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${index + 1}. ${level.title}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${level.width}x${level.height} - ${solverResult.previewLabel()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            BoardThumbnail(
                level = level,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun SolverResult.previewColor(): Color =
    when (this) {
        is SolverResult.Solved -> Color(0xFF2F9E44)
        is SolverResult.Unsolved -> Color(0xFFD9480F)
        is SolverResult.LimitReached -> Color(0xFFE6A700)
    }

private fun SolverResult.previewLabel(): String =
    when (this) {
        is SolverResult.Solved -> "SOLVED ${moves.size} moves"
        is SolverResult.Unsolved -> "UNSOLVED"
        is SolverResult.LimitReached -> "LIMIT"
    }
