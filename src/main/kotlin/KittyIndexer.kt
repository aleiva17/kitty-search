package app.fitia

import java.text.Normalizer
import kotlin.collections.forEach

class KittyIndexer<T>(
    private val database: List<T>,
    private val getIndexableValues: (T) -> List<String>,
    private val responseSize: Int = 16,
    synonyms: List<Pair<String, String>> = emptyList()
) {
    private val prefixTree = Trie<T>()
    private val synonymOf = HashMap<String, String>()


    init {
        this.refreshSynonyms(synonyms)
        this.buildTrie()
    }

    fun refreshSynonyms(newSynonyms: List<Pair<String, String>>) {
        synonymOf.clear()

        for ((a, b) in newSynonyms) {
            val first = this.normalize(a)
            val second = this.normalize(b)

            synonymOf[first] = second
            synonymOf[second] = first
        }
    }

    fun search(term: String): List<T> {
        val matches = ArrayList<RankedMatch<T>>()
        val normalizedTerm = this.normalize(term)

        // PART A: Consider term as is (no typos)
        // 1) As prefix
        val prefixAsIs = this.prefixTree.getDataWithPrefix(normalizedTerm)
        val possibleSynonym = this.synonymOf.get(normalizedTerm)

        val synonymsAsIs = if (possibleSynonym != null) {
            this.prefixTree.getDataWithPrefix(possibleSynonym)
        } else emptyList()

        prefixAsIs.forEach { match ->
            matches.add(Pair(match.second, match.first.length - normalizedTerm.length))
        }
        synonymsAsIs.forEach { match ->
            matches.add(Pair(match.second, match.first.length - normalizedTerm.length))
        }

        // 2) Any word can be a prefix
        val perWordMatches = this.getMatchesPerWord(normalizedTerm)
        val synonymPerWordMatches = if (possibleSynonym != null) {
            this.getMatchesPerWord(possibleSynonym)
        } else emptyList()

        perWordMatches.forEach { (pair, score) ->
            matches.add(Pair(pair.second, score))
        }
        synonymPerWordMatches.forEach { (pair, score) ->
            matches.add(Pair(pair.second, score))
        }

        // PART B: Consider term has typos
        // 3) Applying Damerau-Levenshtein Distance
        val corrections = this.getNearestFoodsFixingTypos(normalizedTerm)

        corrections.forEach { (correctionItem, distanceScore) ->
            matches.add(Pair(correctionItem, distanceScore))
            val indexableValues = this.getIndexableValues(correctionItem)

            for (key in indexableValues) {
                val suggestedSynonym = this.synonymOf[this.normalize(key)]

                if (suggestedSynonym == null) {
                    continue
                }

                val suggestedSynonymNormalized = this.normalize(suggestedSynonym)
                val suggestedSynonymPrefixAsIs =
                    this.prefixTree.getDataWithPrefix(suggestedSynonymNormalized)

                suggestedSynonymPrefixAsIs.forEach { match ->
                    matches.add(Pair(match.second, match.first.length - suggestedSynonymNormalized.length))
                }
            }
        }

        // 4) Merge all previous steps
        // Group recommendations by item and keep minimal score
        val cnt = HashMap<T, Pair<Int, Int>>()

        for ((data, score) in matches) {
            val existingScore = cnt.getOrDefault(data, Pair(0, 0))
            cnt[data] = Pair(existingScore.first + 1, existingScore.second + score)
        }

        val uniques = cnt.toList()

        if (uniques.isEmpty()) {
            return emptyList()
        }

        // Return the top recommendations
        val sortedByMostFrequencyAndHigherScore =
            uniques.sortedWith(compareBy({ -1 * it.second.first }, { it.second.second }))

        return sortedByMostFrequencyAndHigherScore.take(responseSize).map { it.first }
    }

    private fun getMatchesPerWord(text: String): List<RankedMatch<Pair<String, T>>> {
        val words = text.split(" ")
        val matches = ArrayList<RankedMatch<Pair<String, T>>>()

        for (word in words) {
            if (word.length <= 1) {
                continue
            }
            // Prefix matches
            matches.addAll(
                this.prefixTree.getDataWithPrefix(word).map { match ->
                    Pair(Pair(match.first, match.second), match.first.length - word.length)
                }
            )

            // KMP substring matches
            val kmp = KnuthMorrisPratt(word)

            this.database.forEach { data ->
                val indexableValues = this.getIndexableValues(data)

                indexableValues.forEach { key ->
                    val factor = if (word.length <= 7) {
                        1.75
                    } else if (word.length <= 12) {
                        1.50
                    } else {
                        1.25
                    }

                    if (key.length <= factor * word.length) {
                        val matchResponse = kmp.searchOn(this.normalize(key))
                        if (matchResponse.first && matchResponse.second > 0) {
                            matches.add(
                                Pair(
                                    Pair(key, data),
                                    matchResponse.second + key.length - word.length
                                )
                            )
                        }
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

            // Calculate the minimal Damerau-Levenshtein distance for all indexable values
            for (value in indexableValues) {
                val normalizedValue = this.normalize(value)
                val distance = DamerauLevenshteinDistance.calculate(normalizedText, normalizedValue)
                if (distance < minDistance) {
                    minDistance = distance
                }
            }

            nearestMatches.add(Pair(record, minDistance))
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