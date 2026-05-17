package com.rokoc.blockslide.core

import com.rokoc.blockslide.data.LevelPackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SolverTest {
    @Test
    fun `solves a simple level and returns the move list`() {
        val level = level(
            layout = listOf(
                "#####",
                "#...#",
                "#...#",
                "#...#",
                "#####",
            ),
            blocks = listOf(Block("r1", BlockColor.Red, Position(2, 2))),
            targets = listOf(Target(BlockColor.Red, Position(1, 2))),
        )

        val result = Solver.solve(level, maxStates = 100, maxDepth = 4)

        assertTrue(result is SolverResult.Solved)
        val solved = result as SolverResult.Solved
        assertEquals(listOf(SolverMove("r1", Direction.Up)), solved.moves)
    }

    @Test
    fun `reports unsolved when a required target is unreachable`() {
        val level = level(
            layout = listOf(
                "#####",
                "#...#",
                "#####",
                "#...#",
                "#####",
            ),
            blocks = listOf(Block("r1", BlockColor.Red, Position(1, 1))),
            targets = listOf(Target(BlockColor.Red, Position(3, 3))),
        )

        val result = Solver.solve(level, maxStates = 100, maxDepth = 8)

        assertTrue(result is SolverResult.Unsolved)
    }

    @Test
    fun `can analyze the bundled level pack within a small state budget`() {
        val json = File("src/main/res/raw/levels.json").readText()
        val levels = LevelPackParser.parse(json)
        val analyzed = levels.map { level ->
            level.id to Solver.solve(level = level, maxStates = 2_000, maxDepth = 20)
        }
        val report = analyzed.joinToString(separator = "\n") { (levelId, result) ->
            when (result) {
                is SolverResult.Solved -> "$levelId SOLVED ${result.moves.size} moves ${result.visitedStates} states"
                is SolverResult.Unsolved -> "$levelId UNSOLVED ${result.visitedStates} states"
                is SolverResult.LimitReached -> "$levelId LIMIT ${result.visitedStates} states"
            }
        }
        File("build/solver-report.txt").apply {
            parentFile?.mkdirs()
            writeText(report)
        }

        assertEquals(21, analyzed.size)
        assertTrue(analyzed.any { (_, result) -> result is SolverResult.Solved })
    }

    private fun level(
        id: String = "solver-test",
        title: String = "Solver Test",
        layout: List<String>,
        blocks: List<Block>,
        targets: List<Target>,
    ): Level {
        val cells = layout.map { row ->
            row.map { token ->
                when (token) {
                    '#' -> Cell.Wall
                    '.' -> Cell.Floor
                    'S' -> Cell.Stopper
                    ' ' -> Cell.Void
                    else -> error("Unknown test token $token")
                }
            }
        }
        return Level(
            id = id,
            title = title,
            width = layout.first().length,
            height = layout.size,
            cells = cells,
            blocks = blocks,
            targets = targets,
        )
    }
}
