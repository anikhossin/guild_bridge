package dev.anikh.guildbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DropAlertTest {
    @Test
    fun ignoresSlayerRareDrop() {
        assertNull(
            DropAlert.parse(
                "§6§lRARE DROP! §r§9Scylla Blade §r§b(+§r§b208% §r§b✯ Magic Find§r§b)",
                "Steve",
            ),
        )
    }

    @Test
    fun parsesDungeonPartyDrop() {
        val alert = DropAlert.parse("§b[MVP+] §r§fSteve §r§ehas obtained §r§6Necron's Handle§r§e!", "Local")
        assertNotNull(alert)
        assertEquals(DropCategory.DUNGEON, alert!!.category)
        assertEquals("Steve", alert.player)
        assertEquals("Necron's Handle", alert.item)
    }

    @Test
    fun ignoresPetDrop() {
        assertNull(DropAlert.parse("§6§lPET DROP! §r§5Baby Yeti", "Steve"))
    }

    @Test
    fun ignoresDianaNucleusRareDrop() {
        assertNull(DropAlert.parse("§6§lRARE DROP! §r§5Overgrown Nucleus", "Steve"))
    }

    @Test
    fun parsesKuudraChestShare() {
        val alert = DropAlert.parse("§fAlex received §6Attribute Shard from Kuudra's Hoard!", "Steve")
        assertNotNull(alert)
        assertEquals(DropCategory.KUUDRA, alert!!.category)
        assertEquals("Alex", alert.player)
    }

    @Test
    fun parsesDungeonChestPickup() {
        val alert = DropAlert.parse("Steve picked up Necron's Handle from Gold Chest!", "Steve")
        assertNotNull(alert)
        assertEquals(DropCategory.DUNGEON, alert!!.category)
        assertEquals("From Gold Chest", alert.detail)
    }

    @Test
    fun embedPayloadUsesEmbeds() {
        val alert = DropAlert(
            category = DropCategory.DUNGEON,
            player = "Steve",
            item = "Handle",
            detail = "Party drop",
        )
        val payload = alert.webhookPayload()
        assertTrue(payload.contains("\"embeds\""))
        assertTrue(payload.contains("Dungeon"))
        assertTrue(payload.contains("Steve"))
    }

    @Test
    fun ignoresNormalChat() {
        assertNull(DropAlert.parse("§aWelcome to Hypixel!", "Steve"))
    }
}
