package com.rokoc.blockslide.data

import com.google.gson.Gson
import com.rokoc.blockslide.core.Block
import com.rokoc.blockslide.core.BlockColor
import com.rokoc.blockslide.core.BlockKind
import com.rokoc.blockslide.core.Cell
import com.rokoc.blockslide.core.Level
import com.rokoc.blockslide.core.Position
import com.rokoc.blockslide.core.Target

object LevelPackParser {
    private val gson = Gson()

    fun parse(json: String): List<Level> {
        val pack = gson.fromJson(json, LevelPackDto::class.java)
        return pack.levels.map { dto -> dto.toLevel() }
    }

    private fun LevelDto.toLevel(): Level {
        require(layout.isNotEmpty()) { "Level $id must include layout rows." }
        val width = layout.first().length
        require(layout.all { it.length == width }) { "Level $id must use rectangular rows." }
        val height = layout.size
        val cells = layout.map { row ->
            row.map { token ->
                when (token) {
                    '#' -> Cell.Wall
                    '.', 'o', 'O' -> Cell.Floor
                    's', 'S' -> Cell.Stopper
                    ' ', '_' -> Cell.Void
                    else -> error("Unknown layout token '$token' in level $id.")
                }
            }
        }
        return Level(
            id = id,
            title = title,
            width = width,
            height = height,
            cells = cells,
            blocks = blocks.map { block ->
                Block(
                    id = block.id,
                    color = block.color.toBlockColor(),
                    position = Position(row = block.row, col = block.col),
                    kind = block.kind.toBlockKind(),
                    groupId = block.groupId?.takeIf { it.isNotBlank() },
                    required = block.required ?: true,
                )
            },
            targets = targets.map { target ->
                Target(
                    color = target.color.toBlockColor(),
                    position = Position(row = target.row, col = target.col),
                )
            },
        )
    }

    private fun String.toBlockColor(): BlockColor =
        BlockColor.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
            ?: error("Unknown block color '$this'.")

    private fun String?.toBlockKind(): BlockKind =
        when {
            this == null -> BlockKind.Block
            equals("block", ignoreCase = true) -> BlockKind.Block
            equals("puck", ignoreCase = true) -> BlockKind.Puck
            equals("ball", ignoreCase = true) -> BlockKind.Puck
            equals("circle", ignoreCase = true) -> BlockKind.Puck
            else -> error("Unknown block kind '$this'.")
        }
}

private data class LevelPackDto(
    val version: Int = 1,
    val levels: List<LevelDto> = emptyList(),
)

private data class LevelDto(
    val id: String,
    val title: String,
    val layout: List<String>,
    val blocks: List<BlockDto>,
    val targets: List<TargetDto>,
)

private data class BlockDto(
    val id: String,
    val color: String,
    val row: Int,
    val col: Int,
    val kind: String? = null,
    val groupId: String? = null,
    val required: Boolean? = null,
)

private data class TargetDto(
    val color: String,
    val row: Int,
    val col: Int,
)
