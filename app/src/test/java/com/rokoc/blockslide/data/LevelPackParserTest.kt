package com.rokoc.blockslide.data

import com.rokoc.blockslide.core.BlockKind
import com.rokoc.blockslide.core.Cell
import com.rokoc.blockslide.core.Position
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LevelPackParserTest {
    @Test
    fun `parses bundled level pack`() {
        val json = File("src/main/res/raw/levels.json").readText()
        val levels = LevelPackParser.parse(json)

        assertEquals(21, levels.size)
    }

    @Test
    fun `parses v2 mechanics while keeping compact JSON readable`() {
        val levels = LevelPackParser.parse(
            """
            {
              "version": 1,
              "levels": [
                {
                  "id": "mechanic",
                  "title": "Mechanic",
                  "layout": [
                    "#####",
                    "#.S #",
                    "#####"
                  ],
                  "blocks": [
                    { "id": "a", "color": "white", "row": 1, "col": 1, "kind": "puck", "groupId": "g", "required": false }
                  ],
                  "targets": [
                    { "color": "white", "row": 1, "col": 2 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val level = levels.single()
        val block = level.blocks.single()

        assertEquals(Cell.Stopper, level.cellAt(Position(1, 2)))
        assertEquals(Cell.Void, level.cellAt(Position(1, 3)))
        assertEquals(BlockKind.Puck, block.kind)
        assertEquals("g", block.groupId)
        assertEquals(false, block.required)
    }
}
