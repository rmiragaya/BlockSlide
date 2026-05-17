package com.rokoc.blockslide.core

data class BlockMovement(
    val blockId: String,
    val from: Position,
    val to: Position,
)

data class MoveResult(
    val state: GameState,
    val moved: Boolean,
    val blockId: String? = null,
    val from: Position? = null,
    val to: Position? = null,
    val movements: List<BlockMovement> = emptyList(),
    val lostBlockIds: Set<String> = emptySet(),
    val pushedBlockId: String? = null,
)
