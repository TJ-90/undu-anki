package com.tj90.unduanki.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardRulesTest {
    @Test fun onlySuccessfulRewardedRunsEarnStars() {
        assertEquals(23, RewardService.reward(true, false, 13))
        assertEquals(30, RewardService.reward(true, false, 20))
        assertNull(RewardService.reward(false, false, 13))
        assertNull(RewardService.reward(true, true, 13))
        assertEquals(30, RewardService.reward(true, false, 21))
        assertNull(RewardService.reward(true, false, -1))
    }

    @Test fun purchasesRequireFundsAndUnownedCatalogItem() {
        assertEquals(5, RewardService.purchase(15, emptySet(), "flowers"))
        assertNull(RewardService.purchase(9, emptySet(), "flowers"))
        assertNull(RewardService.purchase(20, setOf("flowers"), "flowers"))
        assertNull(RewardService.purchase(20, emptySet(), "unknown"))
        assertEquals(6, ItemCatalog.items.map { it.id }.distinct().size)
    }
}
