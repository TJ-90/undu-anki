package com.tj90.unduanki.game

/** Pure rules: the caller must apply each result in the same durable transaction. */
object RewardService {
    const val COMPLETION_STARS = 10
    const val MAX_COLLECTED_STARS = 20

    fun reward(won: Boolean, practice: Boolean, collectedStars: Int): Int? {
        if (!won || practice || collectedStars < 0) return null
        return COMPLETION_STARS + collectedStars.coerceAtMost(MAX_COLLECTED_STARS)
    }

    fun purchase(balance: Int, owned: Set<String>, itemId: String): Int? {
        val item = ItemCatalog.find(itemId) ?: return null
        if (balance < item.price || itemId in owned) return null
        return balance - item.price
    }
}
