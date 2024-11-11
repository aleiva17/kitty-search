package app.fitia

import java.text.Normalizer
import kotlin.collections.forEach
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

typealias RankedMatch<T> = Pair<T, Int>

class KittyIndexer<T>(
    private val database: List<T>,
    private val getIndexableValues: (T) -> List<String>,
    synonyms: List<Pair<String, String>> = emptyList()
) {
    private val prefixTree = Trie<T>()
    private val synonymOf = HashMap<String, ArrayList<String>>()


    init {
        this.refreshSynonyms(synonyms)
        this.buildTrie()
    }

    fun refreshSynonyms(newSynonyms: List<Pair<String, String>>) {
        synonymOf.clear()

        for ((a, b) in newSynonyms) {
            val first = this.normalize(a)
            val second = this.normalize(b)
            synonymOf.getOrPut(first) { arrayListOf() }.add(second)
            synonymOf.getOrPut(second) { arrayListOf() }.add(first)
        }
    }

    fun search(term: String, acceptIf: (T) -> Boolean = { data -> true }): List<T> {
        val mustMatches = ArrayList<RankedMatch<T>>()
        val fuzzyMatches = ArrayList<RankedMatch<T>>()

        val normalizedTerm = this.normalize(term)

        // PART A: Consider term as is (no typos)
        // 1) As prefix
        val prefixAsIs = this.prefixTree.getDataWithPrefix(normalizedTerm)
        val synonyms = this.synonymOf.getOrDefault(normalizedTerm, arrayListOf())
        val synonymsAsIs = arrayListOf<Pair<String, T>>()

        synonyms.forEach { it ->
            this.prefixTree.getDataWithPrefix(it).forEach { res -> synonymsAsIs.add(res) }
        }

        prefixAsIs.forEach { (word, payload) ->
            mustMatches.add(Pair(payload, word.length - normalizedTerm.length))
        }
        synonymsAsIs.forEach { (word, payload) ->
            mustMatches.add(Pair(payload, word.length - normalizedTerm.length))
        }

        // 2) Any word can be a prefix
        val perWordMatches = this.getMatchesPerWord(normalizedTerm)
        val synonymPerWordMatches = arrayListOf<RankedMatch<T>>()

        synonyms.forEach { it ->
            this.getMatchesPerWord(it).forEach { res -> synonymPerWordMatches.add(res) }
        }

        perWordMatches.forEach { pair -> mustMatches.add(pair) }
        synonymPerWordMatches.forEach { pair -> mustMatches.add(pair) }

        // PART B: Consider term has typos
        // 3) Applying Damerau-Levenshtein Distance
        val corrections = this.getNearestFoodsFixingTypos(normalizedTerm)

        corrections.forEach { (correctionItem, distanceScore) ->
            fuzzyMatches.add(Pair(correctionItem, distanceScore))
            val indexableValues = this.getIndexableValues(correctionItem)

            for (key in indexableValues) {
                val suggestedSynonyms = this.synonymOf.getOrDefault(this.normalize(key), emptyList())

                suggestedSynonyms.forEach { suggestedSynonym ->
                    val suggestedSynonymNormalized = this.normalize(suggestedSynonym)
                    val suggestedSynonymPrefixAsIs = this.prefixTree.getDataWithPrefix(suggestedSynonymNormalized)

                    suggestedSynonymPrefixAsIs.forEach { match ->
                        fuzzyMatches.add(Pair(match.second, match.first.length - suggestedSynonymNormalized.length))
                    }
                }
            }
        }

        // 4) Merge all previous steps
        val uniqueMustMatches =
            this.compressMatches(mustMatches)
                .sortedWith(compareBy({ -1 * it.second.first }, { it.second.second }))
                .map { it.first }
        val seen = uniqueMustMatches.toHashSet()

        val uniqueFuzzyMatches = this.compressMatches(fuzzyMatches)
            .filter { !seen.contains(it.first) }
            .sortedWith(compareBy({ -1 * it.second.first }, { it.second.second }))
            .map { it.first }

        return (uniqueMustMatches + uniqueFuzzyMatches).filter { it -> acceptIf(it) }
    }

    private fun compressMatches(matches: List<RankedMatch<T>>): List<Pair<T, Pair<Int, Int>>> {
        val frequencyScore = HashMap<T, Pair<Int, Int>>()

        for ((data, score) in matches) {
            val existingScore = frequencyScore.getOrDefault(data, Pair(0, 0))
            frequencyScore[data] = Pair(existingScore.first + 1, existingScore.second + score)
        }

        return frequencyScore.toList()
    }

    private fun getMatchesPerWord(text: String): List<RankedMatch<T>> {
        val words = text.split(" ")
        val matches = ArrayList<RankedMatch<T>>()

        for (word in words) {
            if (word.length <= 1) {
                continue
            }
            val synonyms = this.synonymOf.getOrDefault(word, emptyList())

            // Prefix matches
            matches.addAll(
                this.prefixTree.getDataWithPrefix(word).map { match ->
                    Pair(match.second, match.first.length - word.length)
                }
            )
            // Prefix matches synonym
            synonyms.forEach { synonym ->
                matches.addAll(
                    this.prefixTree.getDataWithPrefix(synonym).map { match ->
                        Pair(match.second, match.first.length - synonym.length)
                    }
                )
            }

            // KMP substring matches
            val kmp = KnuthMorrisPratt(word)

            this.database.forEach { data ->
                val indexableValues = this.getIndexableValues(data)
                var quantityOfMatches = 0
                var minDistance = Int.MAX_VALUE
                var maxDistance = Int.MIN_VALUE

                indexableValues.forEach { key ->
                    val normalizedKey = this.normalize(key)
                    val matchResponse = kmp.searchOn(normalizedKey)

                    if (matchResponse.first && matchResponse.second > 0) {
                        quantityOfMatches++
                        minDistance = min(matchResponse.second, minDistance)
                        maxDistance = max(matchResponse.second, maxDistance)
                    }
                }

                if (quantityOfMatches > 0) {
                    val factor =
                        1.0 + ((indexableValues.size.toDouble() - quantityOfMatches.toDouble()) / indexableValues.size.toDouble())
                    val distance = floor(minDistance + (maxDistance - minDistance).toDouble() / 2).toInt()
                    val score = floor(factor * distance).toInt()
                    if (score > 0) {
                        matches.add(Pair(data, score))
                    }
                }
            }
        }

        return matches
    }

    private fun getNearestFoodsFixingTypos(text: String): List<RankedMatch<T>> {
        if (database.isEmpty()) {
            return emptyList()
        }

        val nearestMatches = ArrayList<RankedMatch<T>>()
        val normalizedText = this.normalize(text)
        val keywordLength = normalizedText.length

        for (record in database) {
            val indexableValues = this.getIndexableValues(record)
            var minDistance = Int.MAX_VALUE
            var maxDistance = Int.MIN_VALUE
            var quantityOfMatches = 0

            // Calculate the minimal Damerau-Levenshtein distance for all indexable values
            for (value in indexableValues) {
                val normalizedValue = this.normalize(value)
                val distance = DamerauLevenshteinDistance.calculate(normalizedText, normalizedValue)

                minDistance = min(distance, minDistance)
                maxDistance = max(distance, maxDistance)

                if (distance <= 0.6 * normalizedText.length) {
                    quantityOfMatches++
                }
            }

            if (quantityOfMatches > 0) {
                val factor =
                    1.0 + ((indexableValues.size.toDouble() - quantityOfMatches.toDouble()) / indexableValues.size.toDouble())
                val distance = floor(minDistance + (maxDistance - minDistance).toDouble() / 2).toInt()
                nearestMatches.add(Pair(record, floor(factor * distance).toInt()))
            }
        }

        nearestMatches.sortBy { it.second }
        if (nearestMatches.isEmpty()) {
            return emptyList()
        }
        val minScore = nearestMatches[0].second

        if ((keywordLength <= 2 && minScore > 1) ||
            (keywordLength <= 6 && minScore > 3) ||
            (keywordLength <= 12 && minScore > 5) ||
            (keywordLength <= 20 && minScore > 8)
        ) {
            return emptyList()
        }

        var thresholdIndex = 0
        while (thresholdIndex < nearestMatches.size && nearestMatches[thresholdIndex].second <= 1.20 * minScore) {
            thresholdIndex++
        }

        return nearestMatches.subList(0, thresholdIndex)
    }

    private fun buildTrie() {
        for (registry in database) {
            val indexableValues = this.getIndexableValues(registry)
            for (key in indexableValues) {
                this.prefixTree.add(this.normalize(key), registry)
            }
        }
    }

    private fun normalize(text: String): String {
        val simplified =
            text.lowercase().replace("\t", "").replace("\n", "").replace("-", "").replace("(", "").replace(")", "")
                .replace(",", " ").replace("'", "").replace(".", " ").replace(";", "")
                .replace("!", "^").replace("^", "").replace("$", "").replace("#", "")
                .replace("+", "").replace("×", "").replace("=", "").replace("_", "")
                .replace("÷", "").replace("@", "").replace("<", "").replace(">", "")
                .replace("\\s{2,}".toRegex(), " ")
        val trimmed = simplified.trim()
        return Normalizer.normalize(trimmed, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "")
    }
}