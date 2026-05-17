package com.rokoc.blockslide.core

import java.util.ArrayDeque

data class SolverMove(
    val blockId: String,
    val direction: Direction,
)

sealed class SolverResult {
    data class Solved(
        val moves: List<SolverMove>,
        val visitedStates: Int,
    ) : SolverResult()

    data class Unsolved(
        val visitedStates: Int,
    ) : SolverResult()

    data class LimitReached(
        val visitedStates: Int,
    ) : SolverResult()
}

object Solver {
    fun solve(
        level: Level,
        maxStates: Int = 50_000,
        maxDepth: Int = 80,
    ): SolverResult {
        val initial = GameEngine.newGame(level)
        if (initial.isSolved) {
            return SolverResult.Solved(moves = emptyList(), visitedStates = 1)
        }

        val visited = mutableSetOf(initial.signature())
        val queue = ArrayDeque<SearchNode>()
        queue.add(SearchNode(state = initial, moves = emptyList()))

        while (queue.isNotEmpty()) {
            if (visited.size >= maxStates) {
                return SolverResult.LimitReached(visitedStates = visited.size)
            }

            val node = queue.removeFirst()
            if (node.moves.size >= maxDepth) continue

            node.state.activeBlocks().forEach { block ->
                Direction.entries.forEach { direction ->
                    val result = GameEngine.moveBlock(node.state, block.id, direction)
                    if (!result.moved || result.state.levelFailed) return@forEach

                    val signature = result.state.signature()
                    if (!visited.add(signature)) return@forEach

                    val nextMoves = node.moves + SolverMove(blockId = block.id, direction = direction)
                    if (result.state.isSolved) {
                        return SolverResult.Solved(
                            moves = nextMoves,
                            visitedStates = visited.size,
                        )
                    }
                    queue.add(SearchNode(state = result.state.copy(history = emptyList()), moves = nextMoves))
                }
            }
        }

        return SolverResult.Unsolved(visitedStates = visited.size)
    }

    private fun GameState.signature(): String =
        blocks.sortedBy { it.id }.joinToString("|") { block ->
            if (block.lost) {
                "${block.id}:lost"
            } else {
                "${block.id}:${block.position.row},${block.position.col}"
            }
        }

    private data class SearchNode(
        val state: GameState,
        val moves: List<SolverMove>,
    )
}
