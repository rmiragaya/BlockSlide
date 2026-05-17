package com.rokoc.blockslide.core

data class Position(
    val row: Int,
    val col: Int,
) {
    fun step(direction: Direction): Position =
        Position(row = row + direction.deltaRow, col = col + direction.deltaCol)
}
