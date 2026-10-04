package com.nakshatra.heritage.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The heritage constellation, a port of frontend/js/map.js: every topic with a
 * location becomes a star at its real latitude/longitude (equirectangular
 * projection, longitude scaled by cos(mid-latitude)), stars that share a town
 * are nudged apart, and the stars are joined by a minimum spanning tree.
 * No political boundary is drawn.
 */
class Constellation(val stars: List<Star>, val links: List<Pair<Int, Int>>) {
    class Star(val topic: Topic, val x: Float, val y: Float)

    companion object {
        const val LON0 = 67.2
        const val LON1 = 98.2
        const val LAT0 = 6.2
        const val LAT1 = 36.4
        val K = cos(22 * PI / 180)
        val WIDTH = ((LON1 - LON0) * K).toFloat()
        val HEIGHT = (LAT1 - LAT0).toFloat()
        private const val MIN_GAP = 0.62

        fun project(lat: Double, lon: Double): Pair<Double, Double> = (lon - LON0) * K to LAT1 - lat

        fun of(topics: List<Topic>): Constellation {
            val located = topics.filter { it.location != null }
            val xs = DoubleArray(located.size)
            val ys = DoubleArray(located.size)
            located.forEachIndexed { i, t ->
                val (x0, y0) = project(t.location!!.lat, t.location.lon)
                var x = x0
                var y = y0
                var tries = 0
                while (tries < 24 && (0 until i).any { hypot(x - xs[it], y - ys[it]) < MIN_GAP }) {
                    val a = tries * 2.4
                    val r = MIN_GAP * (0.75 + tries * 0.12)
                    x = x0 + r * cos(a)
                    y = y0 + r * sin(a)
                    tries++
                }
                xs[i] = x
                ys[i] = y
            }
            val stars = located.mapIndexed { i, t -> Star(t, xs[i].toFloat(), ys[i].toFloat()) }
            return Constellation(stars, spanningTree(xs, ys))
        }

        /** Prim's algorithm; returns index pairs. */
        fun spanningTree(xs: DoubleArray, ys: DoubleArray): List<Pair<Int, Int>> {
            val n = xs.size
            if (n < 2) return emptyList()
            val inTree = BooleanArray(n)
            val best = DoubleArray(n) { Double.POSITIVE_INFINITY }
            val from = IntArray(n) { -1 }
            val edges = ArrayList<Pair<Int, Int>>(n - 1)
            best[0] = 0.0
            repeat(n) {
                var u = -1
                for (i in 0 until n) if (!inTree[i] && (u == -1 || best[i] < best[u])) u = i
                inTree[u] = true
                if (from[u] >= 0) edges += from[u] to u
                for (v in 0 until n) {
                    if (inTree[v]) continue
                    val d = hypot(xs[u] - xs[v], ys[u] - ys[v])
                    if (d < best[v]) {
                        best[v] = d
                        from[v] = u
                    }
                }
            }
            return edges
        }
    }
}
