package com.tj90.unduanki.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tj90.unduanki.island.IslandRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IslandPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "island-test-${UUID.randomUUID()}.db"
    private lateinit var db: GameDatabase
    private lateinit var island: IslandRepository

    @Before fun setup() { open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    private fun open() {
        db = GameDatabase(context, databaseName = name)
        island = IslandRepository(db)
    }

    @Test fun ticketSettlementPurchaseAndPlacementSurviveReopenWithoutDuplication() {
        val progress = ProgressRepository(db)
        val batch = progress.startBatch("synthetic", 3, 42, "easy")
        for (card in 1..3) {
            progress.recordPending("attempt-$card", batch, card.toLong(), 0, card.toLong(), 0, 0, "again", 50)
            progress.confirm("attempt-$card")
        }
        val ticket = db.readableDatabase.rawQuery("SELECT id FROM tickets WHERE status='earned'", null).use {
            assertTrue(it.moveToFirst()); it.getString(0)
        }
        assertFalse(island.settle(ticket, 13, won = false, practice = false))
        assertFalse(island.settle(ticket, 13, won = true, practice = true))
        assertEquals(0, island.snapshot().stars)
        assertTrue(island.settle(ticket, 13, won = true, practice = false))
        assertFalse(island.settle(ticket, 13, won = true, practice = false))
        assertEquals(23, island.snapshot().stars)
        assertTrue(island.buy("flowers"))
        assertFalse(island.buy("flowers"))
        assertFalse(island.buy("treehouse"))
        assertTrue(island.place("flowers", 1))
        db.close()
        open()
        val restored = island.snapshot()
        assertEquals(13, restored.stars)
        assertTrue(restored.rescued)
        assertEquals(setOf("flowers"), restored.owned)
        assertEquals(mapOf(1 to "flowers"), restored.placements)
        assertTrue(island.place("flowers", 2))
        assertTrue(island.unplace(2))
        assertEquals(setOf("flowers"), island.snapshot().owned)
        assertTrue(island.snapshot().placements.isEmpty())
        assertFalse(island.settle(ticket, 13, won = true, practice = false))
        assertEquals(13, island.snapshot().stars)
    }
}
