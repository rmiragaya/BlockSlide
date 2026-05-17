package com.rokoc.blockslide.core

data class Block(
    val id: String,
    val color: BlockColor,
    val position: Position,
    val kind: BlockKind = BlockKind.Block,
    val groupId: String? = null,
    val required: Boolean = true,
    val lost: Boolean = false,
)
