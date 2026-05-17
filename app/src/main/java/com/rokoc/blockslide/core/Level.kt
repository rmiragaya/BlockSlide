package com.rokoc.blockslide.core

data class Level(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val cells: List<List<Cell>>,
    val blocks: List<Block>,
    val targets: List<Target>,
) {
    init {
        require(width > 0) { "Level width must be positive." }
        require(height > 0) { "Level height must be positive." }
        require(cells.size == height) { "Level cell rows must match height." }
        require(cells.all { it.size == width }) { "Every cell row must match width." }
        require(blocks.map { it.id }.distinct().size == blocks.size) { "Block ids must be unique." }
        require(blocks.all { cellAt(it.position).canHoldPieces }) { "Blocks must start on playable cells." }
        require(targets.all { cellAt(it.position).canHoldPieces }) { "Targets must sit on playable cells." }
    }

    fun cellAt(position: Position): Cell {
        if (position.row !in 0 until height || position.col !in 0 until width) {
            return Cell.Void
        }
        return cells[position.row][position.col]
    }
}

val Cell.canHoldPieces: Boolean
    get() = this == Cell.Floor || this == Cell.Stopper
