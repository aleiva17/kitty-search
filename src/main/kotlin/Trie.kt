package app.fitia

class Trie<T> {
    private val root = Node<T>();

    fun add(word: String, payload: T?) {
        var automata = root

        for (letter in word) {
            if (!automata.children.contains(letter)) {
                automata.children[letter] = Node()
            }
            automata = automata.children[letter]!!
        }

        automata.isEnd = true
        automata.word = word
        automata.payload = payload
    }

    fun hasPrefix(word: String): Boolean {
        var automata = root

        for (letter in word) {
            if (!automata.children.contains(letter)) {
                return false
            }
            automata = automata.children[letter]!!
        }

        return true
    }

    fun hasWord(word: String): Boolean {
        var automata = root

        for (letter in word) {
            if (!automata.children.contains(letter)) {
                return false
            }
            automata = automata.children[letter]!!
        }

        return automata.isEnd
    }

    fun getDataWithPrefix(prefix: String): MutableList<Pair<String, T>> {
        val data = mutableListOf<Pair<String, T>>()
        var automata = root

        for (letter in prefix) {
            if (!automata.children.contains(letter)) {
                return data
            }
            automata = automata.children[letter]!!
        }

        val queue = ArrayDeque<Node<T>>()
        queue.add(automata)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isEnd) {
                data.add(Pair(node.word, node.payload!!))
            }

            for (child in node.children) {
                queue.add(child.value)
            }
        }

        return data;
    }
}

class Node<T> {
    val children: HashMap<Char, Node<T>> = HashMap()
    var isEnd = false
    var word = ""
    var payload: T? = null
}