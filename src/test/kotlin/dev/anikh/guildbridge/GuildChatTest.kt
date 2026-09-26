package dev.anikh.guildbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText

class GuildChatTest {
    @Test
    fun parsesRankedGuildChat() {
        val line = GuildChat.parse("Guild > [MVP+] Steve: hello guild")
        assertEquals(GuildLine("Steve", "hello guild"), line)
    }

    @Test
    fun parsesGuildRankAfterTheName() {
        val line = GuildChat.parse("Guild > [MVP++] Steve [Officer]: hello there")
        assertEquals(GuildLine("Steve", "hello there"), line)
    }

    @Test
    fun keepsColonsInsideTheMessage() {
        val line = GuildChat.parse("Guild > Alex: meet at 8:30")
        assertEquals(GuildLine("Alex", "meet at 8:30"), line)
    }

    @Test
    fun parsesColoredAndBracketedGuildChat() {
        val colored = GuildChat.parse("§2Guild > §b[MVP+] Steve§f: §7hello")
        assertEquals(GuildLine("Steve", "hello"), colored)
        val bracketed = GuildChat.parse("[Guild] Alex: hi")
        assertEquals(GuildLine("Alex", "hi"), bracketed)
    }

    @Test
    fun ignoresJoinLeaveAndOtherChats() {
        assertNull(GuildChat.parse("Guild > Steve joined."))
        assertNull(GuildChat.parse("Party > [MVP+] Steve: hi"))
        assertNull(GuildChat.parse("Guild > The guild is muted."))
        assertNull(GuildChat.parse("[Discord] Steve: hi"))
    }

    @Test
    fun webhookUsesThePlayerNameAndHead() {
        val payload = GuildChat.webhookPayload(GuildLine("Steve", "hello @everyone"))
        assertTrue(payload.contains("\"username\":\"Steve\""))
        assertTrue(payload.contains("https://minotar.net/helm/Steve/128.png"))
        assertTrue(payload.contains("@ everyone"))
        assertTrue(payload.contains("\"allowed_mentions\":{\"parse\":[]}"))
        assertTrue(!payload.contains("@everyone"))
    }

    @Test
    fun acceptsADiscordWebhookAndDropsQueryText() {
        val url = WebhookUrl.normalize(
            "https://discord.com/api/webhooks/123456789012345678/abcDEF_123-token?wait=true",
        )
        assertEquals(
            "https://discord.com/api/webhooks/123456789012345678/abcDEF_123-token",
            url,
        )
        assertNull(WebhookUrl.normalize("https://example.com/api/webhooks/123456789012345678/abcDEF_123-token"))
        assertNull(WebhookUrl.normalize(""))
    }

    @Test
    fun olderConfigWithoutAWebhookStaysEmpty() {
        val path = java.nio.file.Files.createTempDirectory("guildbridge").resolve("guildbridge.json")
        path.writeText(
            """
            {"enabled":true,"botToken":"abc","channelId":"1","guildId":"2","pollSeconds":3}
            """.trimIndent(),
        )
        val loaded = BridgeConfig.load(path)
        assertEquals("", loaded.webhookUrl)
        assertEquals("abc", loaded.botToken)
    }
}
