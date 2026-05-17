package com.rokoc.blockslide.core

data class GameState(
    val level: Level,
    val blocks: List<Block> = level.blocks,
    val selectedBlockId: String? = blocks.firstOrNull { !it.lost }?.id,
    val moves: Int = 0,
    val history: List<List<Block>> = emptyList(),
) {
    val levelFailed: Boolean
        get() = blocks.any { it.required && it.lost }

    val isSolved: Boolean
        get() = !levelFailed &&
            blocks.any { it.required } &&
            blocks.filter { it.required && !it.lost }.all { block ->
                level.targets.any { target ->
                    target.color == block.color && target.position == block.position
                }
            }

    fun blockAt(position: Position): Block? = blocks.firstOrNull { !it.lost && it.position == position }

    fun selectedBlock(): Block? = selectedBlockId?.let { id -> blocks.firstOrNull { !it.lost && it.id == id } }

    fun activeBlocks(): List<Block> = blocks.filterNot { it.lost }
}
