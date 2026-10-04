package dev.anikh.guildbridge

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class DropCategory(
    val title: String,
    val emoji: String,
    val color: Int,
) {
    DUNGEON("Dungeon", "⚔", 0x9B59B6),
    KUUDRA("Kuudra chest", "🔥", 0xE67E22),
}

data class DropAlert(
    val category: DropCategory,
    val player: String,
    val item: String,
    val detail: String = "",
    val sourceLine: String = "",
) {
    fun dedupKey(): String = player + "\u0000" + item + "\u0000" + category.name

    fun webhookPayload(): String {
        val avatar = "https://minotar.net/helm/${URLEncoder.encode(player, StandardCharsets.UTF_8)}/128.png"
        val description = buildString {
            append("**")
            append(GuildChat.forDiscord(item))
            append("**")
            append(" — **")
            append(GuildChat.forDiscord(player))
            append("**")
        }
        return buildString {
            append("{\"username\":\"Guild Bridge Drops\",\"avatar_url\":")
            append(GuildChat.jsonString("https://minotar.net/avatar/GuildBridge/128"))
            append(",\"embeds\":[{")
            append("\"title\":")
            append(GuildChat.jsonString("${category.emoji} ${category.title}"))
            append(",\"description\":")
            append(GuildChat.jsonString(description))
            append(",\"color\":")
            append(category.color)
            append(",\"thumbnail\":{\"url\":")
            append(GuildChat.jsonString(avatar))
            append("},\"fields\":[")
            append("{\"name\":\"Player\",\"value\":")
            append(GuildChat.jsonString(player))
            append(",\"inline\":true}")
            append(",{\"name\":\"Item\",\"value\":")
            append(GuildChat.jsonString(GuildChat.forDiscord(item).take(256)))
            append(",\"inline\":true}")
            if (detail.isNotBlank()) {
                append(",{\"name\":\"Details\",\"value\":")
                append(GuildChat.jsonString(GuildChat.forDiscord(detail).take(256)))
                append(",\"inline\":false}")
            }
            append("],\"footer\":{\"text\":\"Hypixel SkyBlock • Guild Bridge\"}")
            append("}],\"allowed_mentions\":{\"parse\":[]}}")
        }
    }

    companion object {
        private val rankPrefix = Regex("^(?:\\[[^\\]]+\\] )+")

        private val obtained = Regex(
            """^(?:\[[^\]]+] )*([A-Za-z0-9_]{3,16}) has obtained (.+?)[!.]?\s*$""",
            RegexOption.IGNORE_CASE,
        )
        private val chestPickup = Regex(
            """^([A-Za-z0-9_]{3,16}) picked up (.+?) from (.+?)[!.]?\s*$""",
            RegexOption.IGNORE_CASE,
        )
        private val youPickedUp = Regex("""^You picked up (.+?)!?\.?\s*$""", RegexOption.IGNORE_CASE)
        private val kuudraShare = Regex(
            """^(?:\[[^\]]+] )*([A-Za-z0-9_]{3,16}) received (.+?) from Kuudra(?:'s)? (.+?)[!.]?\s*$""",
            RegexOption.IGNORE_CASE,
        )

        private val kuudraChestHints = listOf("kuudra", "hoard", "hellstorm", "crimson", "kuurth")

        fun plainChat(raw: String): String =
            raw.replace(Regex("§."), "")
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()

        fun parse(raw: String, localPlayer: String): DropAlert? {
            val plain = plainChat(raw)
            if (plain.isEmpty()) {
                return null
            }

            kuudraShare.matchEntire(plain)?.let { match ->
                val player = match.groupValues[1]
                val item = cleanItem(match.groupValues[2])
                val chest = cleanItem(match.groupValues[3])
                return DropAlert(
                    category = DropCategory.KUUDRA,
                    player = player,
                    item = item,
                    detail = "From $chest",
                    sourceLine = plain,
                )
            }

            chestPickup.matchEntire(plain)?.let { match ->
                val player = match.groupValues[1]
                val item = cleanItem(match.groupValues[2])
                val chest = cleanItem(match.groupValues[3])
                val category = if (isKuudraChest(chest)) DropCategory.KUUDRA else DropCategory.DUNGEON
                return DropAlert(
                    category = category,
                    player = player,
                    item = item,
                    detail = "From $chest",
                    sourceLine = plain,
                )
            }

            obtained.matchEntire(plain)?.let { match ->
                val player = match.groupValues[1]
                val item = cleanItem(match.groupValues[2])
                return DropAlert(
                    category = DropCategory.DUNGEON,
                    player = player,
                    item = item,
                    sourceLine = plain,
                )
            }

            youPickedUp.matchEntire(plain)?.let { match ->
                return DropAlert(
                    category = DropCategory.DUNGEON,
                    player = localPlayer.ifBlank { "You" },
                    item = cleanItem(match.groupValues[1]),
                    sourceLine = plain,
                )
            }

            return null
        }

        private fun cleanItem(raw: String): String =
            raw.trim().trimEnd('!', '.').replace(rankPrefix, "").trim()

        private fun isKuudraChest(chest: String): Boolean {
            val lower = chest.lowercase()
            return kuudraChestHints.any { lower.contains(it) }
        }
    }
}
