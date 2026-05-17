package com.rokoc.blockslide.data

import com.google.gson.GsonBuilder
import com.rokoc.blockslide.core.BlockColor
import com.rokoc.blockslide.core.BlockKind
import com.rokoc.blockslide.core.Cell
import com.rokoc.blockslide.core.Level

object LevelJsonExporter {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    fun exportLevel(level: Level): String =
        gson.toJson(level.toDto())

    private fun Level.toDto(): ExportLevelDto =
        ExportLevelDto(
            id = id,
            title = title,
            layout = cells.map { row -> row.joinToString(separator = "") { it.layoutToken() } },
            blocks = blocks
                .sortedWith(compareBy({ it.position.row }, { it.position.col }, { it.id }))
                .map { block ->
                    ExportBlockDto(
                        id = block.id,
                        color = block.color.jsonName(),
                        row = block.position.row,
                        col = block.position.col,
                        kind = block.kind.takeIf { it != BlockKind.Block }?.jsonName(),
                        groupId = block.groupId,
                        required = false.takeIf { !block.required },
                    )
                },
            targets = targets
                .sortedWith(compareBy({ it.position.row }, { it.position.col }, { it.color.name }))
                .map { target ->
                    ExportTargetDto(
                        color = target.color.jsonName(),
                        row = target.position.row,
                        col = target.position.col,
                    )
                },
        )

    private fun Cell.layoutToken(): String =
        when (this) {
            Cell.Floor -> "."
            Cell.Stopper -> "S"
            Cell.Wall -> "#"
            Cell.Void -> " "
        }

    private fun BlockColor.jsonName(): String = name.lowercase()

    private fun BlockKind.jsonName(): String =
        when (this) {
            BlockKind.Block -> "block"
            BlockKind.Puck -> "puck"
        }
}

private data class ExportLevelDto(
    val id: String,
    val title: String,
    val layout: List<String>,
    val blocks: List<ExportBlockDto>,
    val targets: List<ExportTargetDto>,
)

private data class ExportBlockDto(
    val id: String,
    val color: String,
    val row: Int,
    val col: Int,
    val kind: String? = null,
    val groupId: String? = null,
    val required: Boolean? = null,
)

private data class ExportTargetDto(
    val color: String,
    val row: Int,
    val col: Int,
)
