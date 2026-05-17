package com.rokoc.blockslide.core

enum class Direction(val deltaRow: Int, val deltaCol: Int) {
    Up(deltaRow = -1, deltaCol = 0),
    Down(deltaRow = 1, deltaCol = 0),
    Left(deltaRow = 0, deltaCol = -1),
    Right(deltaRow = 0, deltaCol = 1),
}
