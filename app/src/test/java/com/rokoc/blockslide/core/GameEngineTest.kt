package com.rokoc.blockslide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun `slides in every cardinal direction until wall`() {
        val level = level(
            blocks = listOf(Block("r1", BlockColor.Red, Position(2, 2))),
            targets = listOf(Target(BlockColor.Red, Position(1, 2))),
        )

        assertEquals(Position(1, 2), GameEngine.moveSelected(GameState(level), Direction.Up).state.block("r1").position)
        assertEquals(Position(3, 2), GameEngine.moveSelected(GameState(level), Direction.Down).state.block("r1").position)
        assertEquals(Position(2, 1), GameEngine.moveSelected(GameState(level), Direction.Left).state.block("r1").position)
        assertEquals(Position(2, 3), GameEngine.moveSelected(GameState(level), Direction.Right).state.block("r1").position)
    }

    @Test
    fun `stops before another block`() {
        val level = level(
            width = 7,
            layout = listOf(
                "#######",
                "#.....#",
                "#.....#",
                "#.....#",
                "#######",
            ),
            blocks = listOf(
                Block("r1", BlockColor.Red, Position(2, 1)),
                Block("b1", BlockColor.Blue, Position(2, 4)),
            ),
            targets = listOf(
                Target(BlockColor.Red, Position(2, 3)),
                Target(BlockColor.Blue, Position(2, 4)),
            ),
        )

        val result = GameEngine.moveBlock(GameState(level), "r1", Direction.Right)

        assertTrue(result.moved)
        assertEquals(Position(2, 3), result.state.block("r1").position)
    }

    @Test
    fun `void cells make required blocks lost and fail the level`() {
        val level = level(
            layout = listOf(
                "#####",
                " ...#",
                " ...#",
                " ...#",
                "#####",
            ),
            blocks = listOf(Block("r1", BlockColor.Red, Position(2, 2))),
            targets = listOf(Target(BlockColor.Red, Position(2, 3))),
        )

        val result = GameEngine.moveSelected(GameState(level), Direction.Left)

        assertTrue(result.moved)
        assertTrue(result.state.block("r1").lost)
        assertTrue(result.state.levelFailed)
        assertFalse(result.state.isSolved)
    }

    @Test
    fun `stopper cells halt a move but allow the next move through`() {
        val level = level(
            width = 6,
            layout = listOf(
                "######",
                "#....#",
                "#.S..#",
                "#....#",
                "######",
            ),
            blocks = listOf(Block("r1", BlockColor.Red, Position(2, 1))),
            targets = listOf(Target(BlockColor.Red, Position(2, 4))),
        )
        val stopped = GameEngine.moveSelected(GameState(level), Direction.Right).state

        assertEquals(Position(2, 2), stopped.block("r1").position)

        val continued = GameEngine.moveSelected(stopped, Direction.Right).state

        assertEquals(Position(2, 4), continued.block("r1").position)
        assertTrue(continued.isSolved)
    }

    @Test
    fun `connected blocks slide independently in the same direction`() {
        val level = level(
            width = 8,
            height = 8,
            layout = listOf(
                "########",
                "#......#",
                "#......#",
                "#......#",
                "#......#",
                "#......#",
                "#......#",
                "########",
            ),
            blocks = listOf(
                Block("r1", BlockColor.Red, Position(2, 2), groupId = "triad"),
                Block("b1", BlockColor.Blue, Position(2, 3), groupId = "triad"),
                Block("g1", BlockColor.Green, Position(3, 2), groupId = "triad"),
            ),
            targets = listOf(
                Target(BlockColor.Red, Position(2, 5)),
                Target(BlockColor.Blue, Position(2, 6)),
                Target(BlockColor.Green, Position(3, 6)),
            ),
        )

        val result = GameEngine.moveSelected(GameState(level), Direction.Right)

        assertTrue(result.moved)
        assertEquals(Position(2, 5), result.state.block("r1").position)
        assertEquals(Position(2, 6), result.state.block("b1").position)
        assertEquals(Position(3, 6), result.state.block("g1").position)
        assertTrue(result.state.isSolved)
    }

    @Test
    fun `pucks are pushed by blocks and the moving block stops`() {
        val level = level(
            width = 6,
            layout = listOf(
                "######",
                "#....#",
                "#....#",
                "#....#",
                "######",
            ),
            blocks = listOf(
                Block("r1", BlockColor.Red, Position(2, 1)),
                Block("p1", BlockColor.Blue, Position(2, 2), kind = BlockKind.Puck),
            ),
            targets = listOf(
                Target(BlockColor.Red, Position(2, 1)),
                Target(BlockColor.Blue, Position(2, 4)),
            ),
        )

        val result = GameEngine.moveSelected(GameState(level), Direction.Right)

        assertTrue(result.moved)
        assertEquals("p1", result.pushedBlockId)
        assertEquals(Position(2, 1), result.state.block("r1").position)
        assertEquals(Position(2, 4), result.state.block("p1").position)
        assertTrue(result.state.isSolved)
    }

    @Test
    fun `reports solved only when blocks match same color targets`() {
        val level = level(
            blocks = listOf(
                Block("r1", BlockColor.Red, Position(2, 2)),
                Block("b1", BlockColor.Blue, Position(3, 1)),
            ),
            targets = listOf(
                Target(BlockColor.Red, Position(1, 2)),
                Target(BlockColor.Blue, Position(3, 3)),
            ),
        )
        var state = GameState(level)

        state = GameEngine.moveBlock(state, "r1", Direction.Up).state
        assertFalse(state.isSolved)

        state = GameEngine.moveBlock(state, "b1", Direction.Right).state
        assertTrue(state.isSolved)
    }

    @Test
    fun `undo restores exact previous block positions and move count`() {
        val level = level(
            blocks = listOf(Block("r1", BlockColor.Red, Position(2, 2))),
            targets = listOf(Target(BlockColor.Red, Position(1, 2))),
        )
        val initial = GameState(level)
        val moved = GameEngine.moveSelected(initial, Direction.Up).state

        val undone = GameEngine.undo(moved)

        assertEquals(initial.blocks, undone.blocks)
        assertEquals(0, undone.moves)
        assertTrue(undone.history.isEmpty())
    }

    private fun GameState.block(id: String): Block =
        blocks.first { it.id == id }

    private fun level(
        width: Int = 5,
        height: Int = 5,
        layout: List<String> = listOf(
            "#####",
            "#...#",
            "#...#",
            "#...#",
            "#####",
        ),
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
            id = "test",
            title = "Test",
            width = width,
            height = height,
            cells = cells,
            blocks = blocks,
            targets = targets,
        )
    }
}
