package dev.wolsey.windowtimer.data.routing

/** Chooses the order that costs the least total travel time. */
object RouteOptimizer {
    private const val EXACT_LIMIT = 12
    private const val INF = Double.MAX_VALUE / 4

    /**
     * [d] is a square matrix of travel times where `d[i][j]` is from i to j. Index 0 is the
     * start; 1..n are the stops. Returns the stop indices in visiting order, ending back at
     * the start when [returnToStart] is true.
     */
    fun order(d: Array<DoubleArray>, returnToStart: Boolean): List<Int> {
        val n = d.size - 1
        return when {
            n <= 0 -> emptyList()
            n == 1 -> listOf(1)
            n <= EXACT_LIMIT -> exact(d, n, returnToStart)
            else -> approximate(d, n, returnToStart)
        }
    }

    /** Total travel time for visiting the stops in [order]. */
    fun cost(d: Array<DoubleArray>, order: List<Int>, returnToStart: Boolean): Double {
        var at = 0
        var total = 0.0
        for (stop in order) {
            total += d[at][stop]
            at = stop
        }
        if (returnToStart) total += d[at][0]
        return total
    }

    /** Held-Karp: tries every subset of stops, remembering the cheapest way to finish each. */
    private fun exact(d: Array<DoubleArray>, n: Int, back: Boolean): List<Int> {
        val full = 1 shl n
        val best = Array(full) { DoubleArray(n) { INF } }
        val from = Array(full) { IntArray(n) { -1 } }

        for (j in 0 until n) best[1 shl j][j] = d[0][j + 1]

        for (mask in 1 until full) {
            for (j in 0 until n) {
                if (mask and (1 shl j) == 0 || best[mask][j] >= INF) continue
                for (k in 0 until n) {
                    if (mask and (1 shl k) != 0) continue
                    val next = mask or (1 shl k)
                    val c = best[mask][j] + d[j + 1][k + 1]
                    if (c < best[next][k]) {
                        best[next][k] = c
                        from[next][k] = j
                    }
                }
            }
        }

        var last = 0
        var lastCost = INF
        for (j in 0 until n) {
            val c = best[full - 1][j] + if (back) d[j + 1][0] else 0.0
            if (c < lastCost) {
                lastCost = c
                last = j
            }
        }

        val result = ArrayList<Int>(n)
        var mask = full - 1
        var j = last
        while (j != -1) {
            result.add(j + 1)
            val previous = from[mask][j]
            mask = mask xor (1 shl j)
            j = previous
        }
        result.reverse()
        return result
    }

    /** Nearest-neighbour, then keeps reversing stretches of the route while that helps. */
    private fun approximate(d: Array<DoubleArray>, n: Int, back: Boolean): List<Int> {
        val left = (1..n).toMutableList()
        val order = mutableListOf<Int>()
        var at = 0
        while (left.isNotEmpty()) {
            val next = left.minBy { d[at][it] }
            left.remove(next)
            order.add(next)
            at = next
        }

        var best = cost(d, order, back)
        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until n - 1) {
                for (j in i + 1 until n) {
                    val candidate = order.toMutableList()
                    candidate.subList(i, j + 1).reverse()
                    val c = cost(d, candidate, back)
                    if (c < best - 1e-9) {
                        order.clear()
                        order.addAll(candidate)
                        best = c
                        improved = true
                    }
                }
            }
        }
        return order
    }
}