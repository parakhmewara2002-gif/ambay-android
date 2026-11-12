package com.ambay.billing.core

/**
 * Exact port of js/code128.js — Code 128 B/C encoder returning module bar runs.
 */
object Code128Encoder {
    private val PATTERNS = (
        "212222 222122 222221 121223 121322 131222 122213 122312 132212 221213 " +
        "221312 231212 112232 122132 122231 113222 123122 123221 223211 221132 " +
        "221231 213212 223112 312131 311222 321122 321221 312212 322112 322211 " +
        "212123 212321 232121 111323 131123 131321 112313 132113 132311 211313 " +
        "231113 231311 112133 112331 132131 113123 113321 133121 313121 211331 " +
        "231131 213113 213311 213131 311123 311321 331121 312113 312311 332111 " +
        "314111 221411 431111 111224 111422 121124 121421 141122 141221 112214 " +
        "112412 122114 122411 142112 142211 241211 221114 413111 241112 134111 " +
        "111242 121142 121241 114212 124112 124211 411212 421112 421211 212141 " +
        "214121 412121 111143 111341 131141 114113 114311 411113 411311 113141 " +
        "114131 311141 411131 211412 211214 211232 2331112"
    ).split(" ")

    data class Encoded128(
        val mode: String,
        val runs: List<Pair<Int, Int>>, // (startModule, widthModules)
        val totalModules: Int
    )

    fun encode(s: String): Encoded128 {
        require(s.isNotEmpty()) { "empty code" }
        for (ch in s) {
            require(ch.code in 32..126) { "bad char: $ch" }
        }
        val vals = mutableListOf<Int>()
        val mode: String
        if (s.all { it.isDigit() } && s.length % 2 == 0) {
            mode = "C"
            vals += 105
            var i = 0
            while (i < s.length) {
                vals += s.substring(i, i + 2).toInt()
                i += 2
            }
        } else {
            mode = "B"
            vals += 104
            for (ch in s) vals += (ch.code - 32)
        }
        var chk = vals[0]
        for (i in 1 until vals.size) chk += i * vals[i]
        vals += (chk % 103)
        vals += 106

        val runs = mutableListOf<Pair<Int, Int>>()
        var pos = 0
        for (v in vals) {
            val pat = PATTERNS[v]
            for (k in pat.indices) {
                val w = pat[k].digitToInt()
                if (k % 2 == 0) runs += pos to w
                pos += w
            }
        }
        return Encoded128(mode, runs, pos)
    }
}
