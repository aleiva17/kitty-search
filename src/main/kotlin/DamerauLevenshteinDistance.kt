package app.fitia

import kotlin.math.min


class DamerauLevenshteinDistance {
    companion object {
        fun calculate(from: String, to: String): Int {
            val rows = from.length;
            val cols = to.length;

            val table = Array(rows + 1) {
                Array(cols + 1) { 0 }
            }

            for (i in 0..rows) {
                table[i][0] = i
            }

            for (i in 0..cols) {
                table[0][i] = i
            }

            for (i in 1..rows) {
                for (j in 1..cols) {
                    val cost = if (from[i - 1] == to[j - 1]) 0 else 1
                    table[i][j] = min(table[i - 1][j] + 1, min(table[i][j - 1] + 1, table[i - 1][j - 1] + cost))
                    if (i > 1 && j > 1 && from[i - 1] == to[j - 2] && from[i - 2] == to[j - 1]) {
                        table[i][j] = min(table[i][j], table[i - 2][j - 2] + 1)
                    }
                }
            }

            return table[rows][cols];
        }
    }
}