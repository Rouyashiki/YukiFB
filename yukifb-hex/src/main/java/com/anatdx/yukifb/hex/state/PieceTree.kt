package com.anatdx.yukifb.hex.state

import com.anatdx.yukifb.hex.backend.BinaryPiece

internal sealed class PieceTree {
    abstract val length: Long
    abstract val height: Int

    data class Leaf(val piece: BinaryPiece) : PieceTree() {
        override val length = piece.length
        override val height = 1
        init { require(length > 0) }
    }
    data class Branch(val left: PieceTree, val right: PieceTree) : PieceTree() {
        override val length = checkedSum(left.length, right.length)
        override val height = 1 + maxOf(left.height, right.height)
    }
}

internal fun checkedSum(a: Long, b: Long): Long {
    require(a >= 0 && b >= 0 && b <= Long.MAX_VALUE - a) { "Document size overflow" }
    return a + b
}

internal val PieceTree?.size: Long get() = this?.length ?: 0

internal fun pieceTree(piece: BinaryPiece): PieceTree? =
    if (piece.length == 0L) null else PieceTree.Leaf(piece)

private fun balanced(left: PieceTree, right: PieceTree): PieceTree {
    if (left.height > right.height + 1) {
        left as PieceTree.Branch
        return if (left.left.height >= left.right.height) {
            PieceTree.Branch(left.left, PieceTree.Branch(left.right, right))
        } else {
            val middle = left.right as PieceTree.Branch
            PieceTree.Branch(PieceTree.Branch(left.left, middle.left), PieceTree.Branch(middle.right, right))
        }
    }
    if (right.height > left.height + 1) {
        right as PieceTree.Branch
        return if (right.right.height >= right.left.height) {
            PieceTree.Branch(PieceTree.Branch(left, right.left), right.right)
        } else {
            val middle = right.left as PieceTree.Branch
            PieceTree.Branch(PieceTree.Branch(left, middle.left), PieceTree.Branch(middle.right, right.right))
        }
    }
    return PieceTree.Branch(left, right)
}

internal fun join(left: PieceTree?, right: PieceTree?): PieceTree? {
    if (left == null) return right
    if (right == null) return left
    if (left.height > right.height + 1) {
        left as PieceTree.Branch
        return balanced(left.left, checkNotNull(join(left.right, right)))
    }
    if (right.height > left.height + 1) {
        right as PieceTree.Branch
        return balanced(checkNotNull(join(left, right.left)), right.right)
    }
    return PieceTree.Branch(left, right)
}

private fun BinaryPiece.slice(offset: Long, count: Long): BinaryPiece = when (this) {
    is BinaryPiece.Original -> copy(offset = this.offset + offset, length = count)
    is BinaryPiece.Inserted -> copy(offset = this.offset + offset, length = count)
}

internal fun PieceTree?.split(offset: Long): Pair<PieceTree?, PieceTree?> {
    require(offset in 0..size)
    if (this == null) return null to null
    if (offset == 0L) return null to this
    if (offset == length) return this to null
    return when (this) {
        is PieceTree.Leaf -> pieceTree(piece.slice(0, offset)) to pieceTree(piece.slice(offset, length - offset))
        is PieceTree.Branch -> if (offset < left.length) {
            val (a, b) = left.split(offset)
            a to join(b, right)
        } else {
            val (a, b) = right.split(offset - left.length)
            join(left, a) to b
        }
    }
}

internal fun PieceTree?.replace(offset: Long, count: Long, replacement: PieceTree?): PieceTree? {
    require(offset >= 0 && count >= 0 && offset <= size && count <= size - offset)
    checkedSum(size - count, replacement.size)
    val (before, tail) = split(offset)
    val (_, after) = tail.split(count)
    return join(join(before, replacement), after)
}

internal fun PieceTree?.pieces(offset: Long = 0, count: Long = size): Sequence<BinaryPiece> = sequence {
    require(offset >= 0 && count >= 0 && offset <= size && count <= size - offset)
    if (count == 0L) return@sequence
    when (val tree = this@pieces) {
        null -> Unit
        is PieceTree.Leaf -> yield(tree.piece.slice(offset, count))
        is PieceTree.Branch -> {
            val leftCount = minOf(count, (tree.left.length - offset).coerceAtLeast(0))
            if (leftCount > 0) yieldAll(tree.left.pieces(offset, leftCount))
            if (count > leftCount) yieldAll(tree.right.pieces((offset - tree.left.length).coerceAtLeast(0), count - leftCount))
        }
    }
}
