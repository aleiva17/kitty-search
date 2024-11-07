package app.fitia

class Food(val name: String, val category: String) {
    override fun toString(): String {
        return "$name (Category: $category)"
    }
}

fun runSearchTest(term: String, kitty: KittyIndexer<Food>) {
    println("\n============================")
    println("RESULTS FOR: '$term'")
    val response = kitty.search(term)
    response.forEachIndexed { index, food ->
        val order = index + 1
        println("\t$order. $food")
    }
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
        Food("Panqueques de Plátano, Claras y Nueces", "Receta"),

        Food("Lomo de Res con Queso con Papas al Horno y Vainitas", "Receta"),
        Food("Lomo Salteado con Chapiñones y Cebolla", "Receta"),
        Food("Lomo de Res con Choclo y Verduras al Grill", "Receta"),
        Food("Pollo Salteado", "Receta"),
        Food("Aguacate al Horno con Huevo y Atún", "Receta"),
        Food("Oreo Milshake de Fresa", "Galletas y Snacks"),
        Food("Original OREO Test 2", "Galletas y Snacks"),
        Food("Oreo Galletas", "Galletas y Snacks"),

        Food("Fruta", "Frutas"),
        Food("Uvas Verdes", "Frutas"),
        Food("Manzana", "Frutas"),
        Food("Naranja", "Frutas"),
        Food("Papaya", "Frutas"),
        Food("Sandía", "Frutas"),
        Food("Una fruta con un nombre super largo", "Frutas"),
        Food("Melón", "Frutas"),
        Food("Otra fruta pero con un nombre aún más largo que el anterior", "Frutas"),
    )

    val synonyms = arrayListOf(
        Pair("Pink", "Green"),
        Pair("Palta", "Aguacate"),
        Pair("Papaya", "Lechosa"),
        Pair("Sushi", "Maki"),
        Pair("Ajustes", "Configuración"),
    )

    val getIndexableValues = { data: Food -> arrayListOf(data.name, data.category) }
    val kitty = KittyIndexer<Food>(database, getIndexableValues, 100, synonyms)

    runSearchTest("palta", kitty)
    runSearchTest("aguacate", kitty)
    runSearchTest("receta", kitty)
    runSearchTest("pink", kitty)
    runSearchTest("pollo receta", kitty)
    runSearchTest("receta pollo", kitty)
    runSearchTest("huevo y palta galletas con", kitty)
    runSearchTest("uevo y pata galletas con", kitty)

    runSearchTest("panqueques", kitty)
    runSearchTest("panqueues receta", kitty)
    runSearchTest("panqueues platano", kitty)
    runSearchTest("panqueues platano claras y nueces", kitty)
    runSearchTest("panqueues nueces platano y clara", kitty)

    runSearchTest("Panqueques de Plátano, Claras y Nueces - Receta", kitty)
    runSearchTest("Pal", kitty)

    runSearchTest("galletas y snacks", kitty)
    runSearchTest("pollo y lomo", kitty)
    runSearchTest("Lomo de Res", kitty)

    runSearchTest("galletsa sancks", kitty)
    runSearchTest("galletas y snacks", kitty)
    runSearchTest("pollo y lomo", kitty)
    runSearchTest("Lomo de Res", kitty)
    runSearchTest("F", kitty)
    runSearchTest("Fr", kitty)
    runSearchTest("Fru", kitty)
    runSearchTest("Frut", kitty)
    runSearchTest("Fruta", kitty)
    runSearchTest("Frutas", kitty)
    runSearchTest("Frtuas", kitty)
    runSearchTest("FFFFRRRRRRuuuuuutttttttttaas", kitty)

    runSearchTest("Configuración de sistema", kitty)
}
