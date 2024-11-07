package app.fitia

class KnuthMorrisPratt(private val pattern: String) {
    private val pi: List<Int>;

    init {
        pi = computePi(pattern)
    }

    private fun computePi(pattern: String): List<Int> {
        val pi = MutableList(pattern.length) { 0 }
        var index = 0
        var i = 1

        while (i < pattern.length) {
            if (pattern[i] == pattern[index]) {
                pi[i] = ++index
                ++i
                continue
            }
            if (index != 0) {
                index = pi[index - 1]
                --i
            }
            ++i
        }

        return pi
    }

    fun searchOn(text: String): Pair<Boolean, Int> {
        var index = 0
        var i = 0
        while (i < text.length) {
            if (this.pattern[index] == text[i]) {
                ++index
                if (index == pattern.length) {
                    return Pair(true, i - this.pattern.length + 1)
                }
                ++i
                continue
            }
            if (index != 0) {
                index = pi[index - 1]
                --i
            }
            ++i
        }

        return Pair(false, -1)
    }
}