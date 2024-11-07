package app.fitia

class Trie<T> {
    private val root = Node<T>()

    fun add(word: String, payload: T) {
        var automata = root

        for (letter in word) {
            if (!automata.children.contains(letter)) {
                automata.children[letter] = Node()
            }
            automata = automata.children[letter]!!
        }

        automata.isEnd = true
        automata.word = word
        automata.payload.add(payload)
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
                node.payload.forEach { payload -> data.add(Pair(node.word, payload)) }
            }

            for (child in node.children) {
                queue.add(child.value)
            }
        }

        return data
    }
}

class Node<T> {
    val children: HashMap<Char, Node<T>> = HashMap()
    var isEnd = false
    var word = ""
    var payload: MutableList<T> = mutableListOf()
}