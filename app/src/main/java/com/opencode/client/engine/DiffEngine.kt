package com.opencode.client.engine

import com.opencode.client.domain.DiffFileInfo
import com.opencode.client.domain.DiffHunk
import com.opencode.client.domain.DiffLine
import com.opencode.client.domain.LineKind
import com.opencode.client.domain.RenderedFileDiff
import kotlin.math.abs
import kotlin.math.min

/**
 * Computes mobile-friendly unified diffs.
 *
 * OpenCode delivers full before/after file contents per changed file; we derive the unified view
 * locally with a common prefix/suffix trim plus a bounded LCS on the remaining middle section,
 * falling back to a whole-file hunk when the middle is too large to diff cheaply on a phone.
 */
object DiffEngine {

    private const val MAX_LCS_SIDE = 1200
    private const val CONTEXT = 3

    fun render(file: DiffFileInfo): RenderedFileDiff =
        RenderedFileDiff(file, computeUnified(file.before, file.after))

    fun computeUnified(before: String, after: String): List<DiffHunk> {
        if (before == after) return emptyList()
        val a = before.lines()
        val b = after.lines()

        // Trim common prefix/suffix - the cheap 90% of real-world diffs.
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size - 1
        var endB = b.size - 1
        while (endA >= start && endB >= start && a[endA] == b[endB]) {
            endA--; endB--
        }

        val midA = a.subList(start, endA + 1)
        val midB = b.subList(start, endB + 1)

        val ops: List<Op> = if (midA.isEmpty() && midB.isEmpty()) {
            emptyList()
        } else {
            diffMiddle(midA, midB)
        }

        if (ops.none { it !is Op.Equal }) {
            return emptyList()
        }

        return buildHunks(ops, startOffsetOld = start, startOffsetNew = start)
    }

    private sealed interface Op {
        data class Equal(val text: String) : Op
        data class Del(val text: String) : Op
        data class Add(val text: String) : Op
    }

    private fun diffMiddle(a: List<String>, b: List<String>): List<Op> {
        if (a.size > MAX_LCS_SIDE || b.size > MAX_LCS_SIDE ||
            a.size.toLong() * b.size > MAX_LCS_SIDE * MAX_LCS_SIDE
        ) {
            // Too large: emit a single replace-style pair of hunks without LCS guarantees.
            return buildList {
                a.forEach { add(Op.Del(it)) }
                b.forEach { add(Op.Add(it)) }
            }
        }
        // Myers-lite via DP table of Ints; fine within bounds above (~1.4M cells).
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    min(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val ops = ArrayList<Op>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    ops.add(Op.Equal(a[i])); i++; j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    ops.add(Op.Del(a[i])); i++
                }
                else -> {
                    ops.add(Op.Add(b[j])); j++
                }
            }
        }
        while (i < n) {
            ops.add(Op.Del(a[i])); i++
        }
        while (j < m) {
            ops.add(Op.Add(b[j])); j++
        }
        return ops
    }

    private fun buildHunks(ops: List<Op>, startOffsetOld: Int, startOffsetNew: Int): List<DiffHunk> {
        data class Flat(val kind: LineKind, val text: String, val oldNo: Int?, val newNo: Int?)

        var o = startOffsetOld + 1
        var n = startOffsetNew + 1
        val flats = ArrayList<Flat>(ops.size)
        for (op in ops) {
            when (op) {
                is Op.Equal -> {
                    flats.add(Flat(LineKind.CONTEXT, op.text, o, n)); o++; n++
                }
                is Op.Del -> {
                    flats.add(Flat(LineKind.DEL, op.text, o, null)); o++
                }
                is Op.Add -> {
                    flats.add(Flat(LineKind.ADD, op.text, null, n)); n++
                }
            }
        }

        val changeIndices = flats.indices.filter { flats[it].kind != LineKind.CONTEXT }
        if (changeIndices.isEmpty()) return emptyList()

        val hunks = ArrayList<DiffHunk>()
        var g = 0
        while (g < changeIndices.size) {
            var end = g
            // Merge changes separated by no more than 2*CONTEXT context lines into one hunk.
            while (end + 1 < changeIndices.size &&
                changeIndices[end + 1] - changeIndices[end] - 1 <= 2 * CONTEXT
            ) {
                end++
            }
            val from = maxOf(0, changeIndices[g] - CONTEXT)
            val to = minOf(flats.size - 1, changeIndices[end] + CONTEXT)
            val slice = flats.subList(from, to + 1)
            val oldStart = slice.firstNotNullOfOrNull { it.oldNo }
                ?: slice.firstNotNullOfOrNull { it.newNo }
                ?: 1
            val newStart = slice.firstNotNullOfOrNull { it.newNo }
                ?: slice.firstNotNullOfOrNull { it.oldNo }
                ?: 1
            hunks.add(
                DiffHunk(
                    oldStart,
                    newStart,
                    slice.map { DiffLine(it.kind, it.text, it.oldNo, it.newNo) }
                )
            )
            g = end + 1
        }
        return hunks
    }

    /** Compact stats string like "+128 −14". */
    fun statsLabel(additions: Int, deletions: Int): String = "+$additions −$deletions"

    fun magnitudeColorWeight(additions: Int, deletions: Int): Int = min(abs(additions - deletions) / 10, 5)
}
