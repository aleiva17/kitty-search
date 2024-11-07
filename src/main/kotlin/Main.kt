package app.fitia

class Food(val name: String, val category: String) {
    override fun toString(): String {
        return "[$name - $category]"
    }
}

fun runSearchTest(term: String, indexer: Indexer<Food>) {
    println("\n============================")
    println("RESULTS FOR: '$term'")
    val response = indexer.search(term)
    response.forEach { food -> println("\t$food") }
}

fun main() {
    val database = arrayListOf(
        Food("Aguacate", "Fruta"),
        Food("Palta", "Fruta"),
        Food("Pan con palta", "Receta"),
        Food("Pan con aguacate", "Receta"),
        Food("Maki Palta", "Sushi"),
        Food("Atún con palta y huevo", "Receta"),
        Food("Galletas con huevo y palta", "Receta"),
        Food("Galletas con huevo y aguacate", "Receta"),
        Food("Fideos con Carne", "Receta"),
        Food("Wildcard", "Any"),
        Food("Green", "Any"),
        Food("Ajustes de sistema", "Feature"),
        Food("Pollo", "Proteínas"),
        Food("Arroz con pollo", "Receta"),
        Food("Pan con pollo", "Receta"),
        Food("Panqueques de Plátano, Claras y Nueces", "Receta")
    )

    val synonyms = arrayListOf(
        Pair("Pink", "Green"),
        Pair("Palta", "Aguacate"),
        Pair("Sushi", "Maki"),
        Pair("Ajustes", "Configuración"),
    )

    val getIndexableValues = { data: Food -> Pair(arrayListOf(data.name), arrayListOf(data.category)) }
    val indexer = Indexer<Food>(database, getIndexableValues, 100, synonyms)

    runSearchTest("palta", indexer)
    runSearchTest("aguacate", indexer)
    runSearchTest("receta", indexer)
    runSearchTest("pink", indexer)
    runSearchTest("pollo receta", indexer)
    runSearchTest("receta pollo", indexer)
    runSearchTest("huevo y palta galletas con", indexer)
    runSearchTest("uevo y pata galletas con", indexer)

    runSearchTest("panqueques", indexer)
    runSearchTest("panqueues receta", indexer)
    runSearchTest("panqueues platano", indexer)
    runSearchTest("panqueues platano claras y nueces", indexer)
    runSearchTest("panqueues nueces platano y clara", indexer)

    runSearchTest("Panqueques de Plátano, Claras y Nueces - Receta", indexer)
    runSearchTest("Pal", indexer);
}
