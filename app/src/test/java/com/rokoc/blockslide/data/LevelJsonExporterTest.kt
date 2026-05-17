package com.rokoc.blockslide.data

import com.rokoc.blockslide.core.Block
import com.rokoc.blockslide.core.BlockColor
import com.rokoc.blockslide.core.BlockKind
import com.rokoc.blockslide.core.Cell
import com.rokoc.blockslide.core.Level
import com.rokoc.blockslide.core.Position
import com.rokoc.blockslide.core.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelJsonExporterTest {
    @Test
    fun `exports a level that the parser can read back`() {
        val level = Level(
            id = "editor-test",
            title = "Editor Test",
            width = 4,
            height = 3,
            cells = listOf(
                listOf(Cell.Wall, Cell.Wall, Cell.Wall, Cell.Wall),
                listOf(Cell.Wall, Cell.Floor, Cell.Stopper, Cell.Void),
                listOf(Cell.Wall, Cell.Wall, Cell.Wall, Cell.Wall),
            ),
            blocks = listOf(
                Block(
                    id = "r1",
                    color = BlockColor.Red,
                    position = Position(row = 1, col = 1),
                    groupId = "a",
                ),
                Block(
                    id = "w1",
                    color = BlockColor.White,
                    position = Position(row = 1, col = 2),
                    kind = BlockKind.Puck,
                    required = false,
                ),
            ),
            targets = listOf(Target(color = BlockColor.Red, position = Position(row = 1, col = 2))),
        )

        val exported = LevelJsonExporter.exportLevel(level)
        val parsed = LevelPackParser.parse("""{"version":1,"levels":[$exported]}""").single()

        assertEquals(level.id, parsed.id)
        assertEquals(level.title, parsed.title)
        assertEquals(level.cells, parsed.cells)
        assertEquals(level.blocks, parsed.blocks)
        assertEquals(level.targets, parsed.targets)
    }
}
