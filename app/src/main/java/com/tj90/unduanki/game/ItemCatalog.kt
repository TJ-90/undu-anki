package com.tj90.unduanki.game

data class Decoration(val id: String, val label: String, val price: Int)

/** IDs are persisted and must not change when labels or artwork change. */
object ItemCatalog {
    val items = listOf(
        Decoration("flowers", "Flowers", 10),
        Decoration("lantern", "Lantern", 15),
        Decoration("beach_ball", "Beach ball", 20),
        Decoration("palm_tree", "Palm tree", 25),
        Decoration("hammock", "Hammock", 30),
        Decoration("treehouse", "Treehouse", 50),
    )
    fun find(id: String): Decoration? = items.find { it.id == id }
}
