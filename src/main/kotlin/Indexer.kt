package app.fitia

import java.text.Normalizer

typealias RankedMatch<T> = Pair<T, Int>

class Indexer<T>(
    private val database: List<T>,
    private val getIndexableValues: (T) -> Pair<List<String>, List<String>>,
    private val responseSize: Int,
    synonyms: List<Pair<String, String>> = emptyList()
) {
    private val primaryPrefixTree = Trie<T>()
    private val secondaryPrefixTree = Trie<T>()
    private val synonymToOriginalWord = HashMap<String, String>()


    init {
        this.refreshSynonyms(synonyms);
        this.buildTries()
    }

    fun refreshSynonyms(newSynonyms: List<Pair<String, String>>) {
        synonymToOriginalWord.clear()

        for ((synonym, originalWord) in newSynonyms) {
            val first = this.normalize(synonym)
            val second = this.normalize(originalWord)

            synonymToOriginalWord[first] = second
            synonymToOriginalWord[second] = first
        }
    }

    fun search(term: String): List<T> {
        val matches = ArrayList<RankedMatch<T>>()
        val normalizedTerm = this.normalize(term)

        // PART A: Consider term as is (no typos)
        // 1) As prefix
        val primaryPrefixAsIs = primaryPrefixTree.getDataWithPrefix(normalizedTerm)
        val secondaryPrefixAsIs = secondaryPrefixTree.getDataWithPrefix(normalizedTerm)

        val possibleSynonym = synonymToOriginalWord.get(normalizedTerm)
        val primarySynonymAsIs = if (possibleSynonym != null) {
            primaryPrefixTree.getDataWithPrefix(possibleSynonym)
        } else mutableListOf()

        val secondarySynonymAsIs = if (possibleSynonym != null) {
            secondaryPrefixTree.getDataWithPrefix(possibleSynonym)
        } else mutableListOf()


        primaryPrefixAsIs.forEach { match ->
            matches.add(Pair(match.second, match.first.length))
        }
        secondaryPrefixAsIs.forEach { match ->
            matches.add(Pair(match.second, match.first.length))
        }
        primarySynonymAsIs.forEach { match ->
            matches.add(Pair(match.second, match.first.length))
        }
        secondarySynonymAsIs.forEach { match ->
            matches.add(Pair(match.second, match.first.length))
        }

        // 2) Any word can be a prefix
        val (perWordPrimary, perWordSecondary) = this.getMatchesPerWord(normalizedTerm)
        val (perWordSynonymPrimary, perWordSynonymSecondary) = if (possibleSynonym != null) {
            this.getMatchesPerWord(possibleSynonym)
        } else Pair(mutableListOf(), mutableListOf());

        // Per-word primary matches
        perWordPrimary.forEach { (pair, score) ->
            matches.add(Pair(pair.second, score))
        }

        perWordSynonymPrimary.forEach { (pair, score) ->
            matches.add(Pair(pair.second, score))
        }

        // Per-word secondary matches
        perWordSecondary.forEach { (pair, score) ->
            matches.add(Pair(pair.second, score))
        }

        perWordSynonymSecondary.forEach { (pair, score) ->
            matches.add(Pair(pair.second, score))
        }

        // PART B: Consider term has typos
        // 3) Applying Damerau-Levenshtein Distance
        val corrections = this.getNearestFoodsFixingTypos(normalizedTerm)

        corrections.forEach { (correctionItem, distanceScore) ->
            matches.add(Pair(correctionItem, distanceScore))
        }

        // 4) Merge all previous steps
        // Group recommendations by item and keep minimal score
        val cnt = HashMap<T, Pair<Int, Int>>()

        for ((data, score) in matches) {
            val existingScore = cnt.getOrDefault(data, Pair(0, 0))
            cnt[data] = Pair(existingScore.first + 1, existingScore.second + score)
        }

        val uniques = cnt.toList();

        if (uniques.isEmpty()) {
            return emptyList()
        }

        for (pair in uniques) {
            val dt = pair.first
            val py = pair.second.second
            println("$dt -> $py")
        }
        // Return the top recommendations
        val sortedByMostFrequencyAndHigherScore =
            uniques.sortedWith(compareBy({ -1 * it.second.first }, { -1 * it.second.second }))

        val highestScore = sortedByMostFrequencyAndHigherScore.first().second.second;
        val threshold = if (highestScore <= 30) {
            5
        } else if (highestScore <= 100) {
            30
        } else if (highestScore <= 200) {
            90
        } else {
            110
        }

        return sortedByMostFrequencyAndHigherScore.filter { it.second.second >= threshold }
            .take(responseSize)
            .map { it.first }
    }

    private fun getMatchesPerWord(text: String): Pair<List<RankedMatch<Pair<String, T>>>, List<RankedMatch<Pair<String, T>>>> {
        val words = text.split(" ")
        val primaryMatches = ArrayList<RankedMatch<Pair<String, T>>>()
        val secondaryMatches = ArrayList<RankedMatch<Pair<String, T>>>()

        for (word in words) {
            // Prefix matches
            primaryMatches.addAll(
                this.primaryPrefixTree.getDataWithPrefix(word).map { match ->
                    Pair(Pair(match.first, match.second), match.first.length - word.length)
                }
            )
            secondaryMatches.addAll(
                this.secondaryPrefixTree.getDataWithPrefix(word).map { match ->
                    Pair(Pair(match.first, match.second), match.first.length - word.length)
                }
            )

            // KMP substring matches
            val kmp = KnuthMorrisPratt(word)

            this.database.forEach { data ->
                val (primaryValues, secondaryValues) = this.getIndexableValues(data)

                primaryValues.forEach { primary ->
                    val matchResponse = kmp.searchOn(this.normalize(primary))
                    if (matchResponse.first && matchResponse.second > 0) {
                        primaryMatches.add(
                            Pair(
                                Pair(primary, data),
                                matchResponse.second + primary.length - word.length
                            )
                        )
                    }
                }

                secondaryValues.forEach { secondary ->
                    val matchResponse = kmp.searchOn(this.normalize(secondary))
                    if (matchResponse.first && matchResponse.second > 0) {
                        secondaryMatches.add(
                            Pair(
                                Pair(secondary, data),
                                matchResponse.second + secondary.length - word.length
                            )
                        )
                    }
                }
            }
        }

        return Pair(primaryMatches, secondaryMatches)
    }

    private fun getNearestFoodsFixingTypos(text: String): List<RankedMatch<T>> {
        if (database.isEmpty()) {
            return emptyList()
        }

        val nearestMatches = ArrayList<RankedMatch<T>>()
        val normalizedText = this.normalize(text)
        val keywordLength = normalizedText.length

        for (record in database) {
            val (primaryValues, secondaryValues) = getIndexableValues(record)
            var minDistance = Int.MAX_VALUE

            // Calculate the minimal Damerau-Levenshtein distance for all indexable values
            for (value in primaryValues + secondaryValues) {
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

        // Penalization for long or short words with mistakes
        if ((keywordLength >= 10 && minScore * (keywordLength / 10 + 1) > keywordLength) ||
            (keywordLength > 3 && keywordLength < 10 && (keywordLength shr 1) + 1 <= minScore)
        ) {
            return emptyList()
        }

        var thresholdIndex = 0
        while (thresholdIndex < nearestMatches.size && nearestMatches[thresholdIndex].second == minScore) {
            thresholdIndex++
        }

        return nearestMatches.subList(0, thresholdIndex)
    }

    private fun buildTries() {
        for (registry in database) {
            val (primary, secondary) = this.getIndexableValues(registry)

            for (primaryIndex in primary) {
                primaryPrefixTree.add(this.normalize(primaryIndex), registry)
            }

            for (secondaryIndex in secondary) {
                secondaryPrefixTree.add(this.normalize(secondaryIndex), registry)
            }
        }
    }

    private fun normalize(text: String): String {
        val simplified =
            text.lowercase().replace("\t", "").replace("\n", "").replace("-", "").replace("(", "").replace(")", "")
                .replace(",", " ").replace(".", " ").replace(";", "").replace("!", "^").replace("^", "")
                .replace("$", "").replace("#", "").replace("+", "").replace("×", "").replace("=", "").replace("_", "")
                .replace("÷", "").replace("@", "").replace("<", "").replace(">", "")
                .replace("\\s{2,}".toRegex(), " ")
        val trimmed = simplified.trim()
        return Normalizer.normalize(trimmed, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "")
    }
}