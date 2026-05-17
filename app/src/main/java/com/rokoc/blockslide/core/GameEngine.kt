package com.rokoc.blockslide.core

object GameEngine {
    fun newGame(level: Level): GameState = GameState(level = level)

    fun selectBlock(state: GameState, blockId: String): GameState {
        val exists = state.blocks.any { !it.lost && it.id == blockId }
        return if (exists) state.copy(selectedBlockId = blockId) else state
    }

    fun selectAt(state: GameState, position: Position): GameState {
        val block = state.blockAt(position) ?: return state
        return selectBlock(state, block.id)
    }

    fun moveSelected(state: GameState, direction: Direction): MoveResult {
        val blockId = state.selectedBlockId ?: return MoveResult(state = state, moved = false)
        return moveBlock(state = state, blockId = blockId, direction = direction)
    }

    fun moveBlock(
        state: GameState,
        blockId: String,
        direction: Direction,
    ): MoveResult {
        val block = state.blocks.firstOrNull { !it.lost && it.id == blockId }
            ?: return MoveResult(state = state, moved = false)
        val movingIds = block.groupId?.let { groupId ->
            state.activeBlocks().filter { it.groupId == groupId }.map { it.id }.toSet()
        } ?: setOf(block.id)
        if (movingIds.size > 1) {
            return moveConnectedBlocks(
                state = state,
                selectedBlock = block,
                movingIds = movingIds,
                direction = direction,
            )
        }
        val originalBlocks = state.blocks
        val originalSelectedPosition = block.position
        var currentBlocks = state.blocks
        var moved = false
        var lostBlockIds = emptySet<String>()
        var pushedBlockId: String? = null
        var animationBlockId = block.id
        var animationFrom = originalSelectedPosition
        var animationTo = originalSelectedPosition

        while (true) {
            val movingBlocks = currentBlocks.filter { !it.lost && it.id in movingIds }
            if (movingBlocks.isEmpty()) break

            val nextPositions = movingBlocks.associate { it.id to it.position.step(direction) }
            val occupied = currentBlocks
                .filter { !it.lost && it.id !in movingIds }
                .associateBy { it.position }
            val collided = nextPositions.values.mapNotNull { occupied[it] }.firstOrNull()
            if (collided != null) {
                if (movingIds.size == 1 && collided.kind == BlockKind.Puck) {
                    val pushed = slidePuck(
                        level = state.level,
                        blocks = currentBlocks,
                        puck = collided,
                        direction = direction,
                    )
                    if (pushed.changed) {
                        currentBlocks = pushed.blocks
                        moved = true
                        pushedBlockId = collided.id
                        lostBlockIds = lostBlockIds + pushed.lostBlockIds
                        if (animationTo == originalSelectedPosition) {
                            animationBlockId = collided.id
                            animationFrom = collided.position
                            animationTo = pushed.finalPosition
                        }
                    }
                }
                break
            }

            val nextCells = nextPositions.mapValues { (_, position) -> state.level.cellAt(position) }
            if (nextCells.values.any { it == Cell.Wall }) break

            currentBlocks = currentBlocks.map { current ->
                val next = nextPositions[current.id] ?: return@map current
                val nextCell = nextCells.getValue(current.id)
                when (nextCell) {
                    Cell.Void -> current.copy(position = next, lost = true)
                    Cell.Floor, Cell.Stopper -> current.copy(position = next)
                    Cell.Wall -> current
                }
            }
            moved = true
            lostBlockIds = lostBlockIds + nextCells.filterValues { it == Cell.Void }.keys
            currentBlocks.firstOrNull { it.id == block.id }?.let { animationTo = it.position }

            if (nextCells.values.any { it == Cell.Void || it == Cell.Stopper }) break
        }

        if (!moved) {
            return MoveResult(
                state = state.copy(selectedBlockId = blockId),
                moved = false,
                blockId = blockId,
                from = originalSelectedPosition,
                to = originalSelectedPosition,
            )
        }

        val selectedId = blockId.takeIf { id -> currentBlocks.any { !it.lost && it.id == id } }
            ?: currentBlocks.firstOrNull { !it.lost }?.id
        val nextState = state.copy(
            blocks = currentBlocks,
            selectedBlockId = selectedId,
            moves = state.moves + 1,
            history = state.history + listOf(originalBlocks),
        )
        return MoveResult(
            state = nextState,
            moved = true,
            blockId = animationBlockId,
            from = animationFrom,
            to = animationTo,
            movements = listOf(BlockMovement(animationBlockId, animationFrom, animationTo)),
            lostBlockIds = lostBlockIds,
            pushedBlockId = pushedBlockId,
        )
    }

    fun undo(state: GameState): GameState {
        val previousBlocks = state.history.lastOrNull() ?: return state
        val selectedId = state.selectedBlockId?.takeIf { id -> previousBlocks.any { it.id == id } }
            ?: previousBlocks.firstOrNull()?.id
        return state.copy(
            blocks = previousBlocks,
            selectedBlockId = selectedId,
            moves = (state.moves - 1).coerceAtLeast(0),
            history = state.history.dropLast(1),
        )
    }

    fun reset(state: GameState): GameState = GameState(level = state.level)

    private fun moveConnectedBlocks(
        state: GameState,
        selectedBlock: Block,
        movingIds: Set<String>,
        direction: Direction,
    ): MoveResult {
        val originalBlocks = state.blocks
        var currentBlocks = state.blocks
        var moved = false
        var lostBlockIds = emptySet<String>()
        var animationTo = selectedBlock.position

        val movingBlocks = state.activeBlocks()
            .filter { it.id in movingIds }
            .sortedWith(direction.leadingBlockComparator())

        movingBlocks.forEach { block ->
            val current = currentBlocks.firstOrNull { !it.lost && it.id == block.id } ?: return@forEach
            val slide = slideConnectedBlock(
                level = state.level,
                blocks = currentBlocks,
                block = current,
                direction = direction,
            )
            if (slide.changed) {
                currentBlocks = slide.blocks
                moved = true
                lostBlockIds = lostBlockIds + slide.lostBlockIds
                if (current.id == selectedBlock.id) {
                    animationTo = slide.finalPosition
                }
            }
        }

        if (!moved) {
            return MoveResult(
                state = state.copy(selectedBlockId = selectedBlock.id),
                moved = false,
                blockId = selectedBlock.id,
                from = selectedBlock.position,
                to = selectedBlock.position,
            )
        }

        val selectedId = selectedBlock.id.takeIf { id -> currentBlocks.any { !it.lost && it.id == id } }
            ?: currentBlocks.firstOrNull { !it.lost }?.id
        val movements = movingIds.mapNotNull { movingId ->
            val original = originalBlocks.firstOrNull { it.id == movingId } ?: return@mapNotNull null
            val current = currentBlocks.firstOrNull { it.id == movingId } ?: return@mapNotNull null
            if (original.position != current.position || original.lost != current.lost) {
                BlockMovement(blockId = movingId, from = original.position, to = current.position)
            } else {
                null
            }
        }
        val nextState = state.copy(
            blocks = currentBlocks,
            selectedBlockId = selectedId,
            moves = state.moves + 1,
            history = state.history + listOf(originalBlocks),
        )
        return MoveResult(
            state = nextState,
            moved = true,
            blockId = selectedBlock.id,
            from = selectedBlock.position,
            to = animationTo,
            movements = movements,
            lostBlockIds = lostBlockIds,
        )
    }

    private fun Direction.leadingBlockComparator(): Comparator<Block> =
        when (this) {
            Direction.Right -> compareByDescending<Block> { it.position.col }.thenBy { it.position.row }
            Direction.Left -> compareBy<Block> { it.position.col }.thenBy { it.position.row }
            Direction.Down -> compareByDescending<Block> { it.position.row }.thenBy { it.position.col }
            Direction.Up -> compareBy<Block> { it.position.row }.thenBy { it.position.col }
        }

    private fun slideConnectedBlock(
        level: Level,
        blocks: List<Block>,
        block: Block,
        direction: Direction,
    ): ConnectedSlide {
        var position = block.position
        var changed = false
        var lost = false

        while (true) {
            val next = position.step(direction)
            val nextCell = level.cellAt(next)
            val occupied = blocks.any { !it.lost && it.id != block.id && it.position == next }
            if (nextCell == Cell.Wall || occupied) break

            position = next
            changed = true
            if (nextCell == Cell.Void) {
                lost = true
                break
            }
            if (nextCell == Cell.Stopper) break
        }

        if (!changed) {
            return ConnectedSlide(blocks = blocks, changed = false, finalPosition = block.position)
        }

        val nextBlocks = blocks.map { current ->
            if (current.id == block.id) current.copy(position = position, lost = lost) else current
        }
        return ConnectedSlide(
            blocks = nextBlocks,
            changed = true,
            finalPosition = position,
            lostBlockIds = if (lost) setOf(block.id) else emptySet(),
        )
    }

    private fun slidePuck(
        level: Level,
        blocks: List<Block>,
        puck: Block,
        direction: Direction,
    ): PuckSlide {
        var position = puck.position
        var changed = false
        var lost = false

        while (true) {
            val next = position.step(direction)
            val nextCell = level.cellAt(next)
            val occupied = blocks.any { !it.lost && it.id != puck.id && it.position == next }
            if (nextCell == Cell.Wall || occupied) break

            position = next
            changed = true
            if (nextCell == Cell.Void) {
                lost = true
                break
            }
            if (nextCell == Cell.Stopper) break
        }

        if (!changed) {
            return PuckSlide(blocks = blocks, changed = false, finalPosition = puck.position)
        }

        val nextBlocks = blocks.map { block ->
            if (block.id == puck.id) block.copy(position = position, lost = lost) else block
        }
        return PuckSlide(
            blocks = nextBlocks,
            changed = true,
            finalPosition = position,
            lostBlockIds = if (lost) setOf(puck.id) else emptySet(),
        )
    }

    private data class PuckSlide(
        val blocks: List<Block>,
        val changed: Boolean,
        val finalPosition: Position,
        val lostBlockIds: Set<String> = emptySet(),
    )

    private data class ConnectedSlide(
        val blocks: List<Block>,
        val changed: Boolean,
        val finalPosition: Position,
        val lostBlockIds: Set<String> = emptySet(),
    )
}
