package dev.anikh.guildbridge

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class DropCategory(
    val title: String,
    val emoji: String,
    val color: Int,
) {
    SLAYER("Slayer RNG", "☠", 0xE74C3C),
    DUNGEON("Dungeon", "⚔", 0x9B59B6),
    KUUDRA("Kuudra", "🔥", 0xE67E22),
    DIANA("Diana", "🌙", 0xF1C40F),
    PET("Pet Drop", "🐾", 0xE91E63),
    FISHING("Fishing", "🎣", 0x3498DB),
    RARE("Rare Drop", "✦", 0x2ECC71),
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

        private val rareDrop = Regex("""^RARE DROP! (.+?)(?: \((\+[^)]+)\))?[!.]?\s*$""", RegexOption.IGNORE_CASE)
        private val petDrop = Regex("""^PET DROP! (.+?)(?: \(.+\))?[!.]?\s*$""", RegexOption.IGNORE_CASE)
        private val extraStats = Regex("""^EXTRA STATS DROP! (.+?)[!.]?\s*$""", RegexOption.IGNORE_CASE)
        private val secretBonus = Regex("""^SECRET BONUS! (.+?)[!.]?\s*$""", RegexOption.IGNORE_CASE)
        private val fishingCatch = Regex(
            """^(?:GOOD|GREAT|OUTSTANDING) CATCH! You caught (.+?)!?\.?\s*$""",
            RegexOption.IGNORE_CASE,
        )
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

        private val dianaHints = listOf(
            "nucleus",
            "burrow",
            "griffin",
            "chimera",
            "shard",
            "minos",
            "anubis",
            "antique",
            "treasure",
            "shen",
            "daedalus",
            "mythos",
            "diana",
        )
        private val kuudraHints = listOf("kuudra", "hoard", "hellstorm", "crimson", "kuurth", "attribute shard")

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

            petDrop.matchEntire(plain)?.let { match ->
                return DropAlert(
                    category = DropCategory.PET,
                    player = localPlayer.ifBlank { "You" },
                    item = cleanItem(match.groupValues[1]),
                    sourceLine = plain,
                )
            }

            fishingCatch.matchEntire(plain)?.let { match ->
                return DropAlert(
                    category = DropCategory.FISHING,
                    player = localPlayer.ifBlank { "You" },
                    item = cleanItem(match.groupValues[1]),
                    sourceLine = plain,
                )
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

            chestPickup.matchEntire(plain)?.let { match ->
                val player = match.groupValues[1]
                val item = cleanItem(match.groupValues[2])
                val chest = cleanItem(match.groupValues[3])
                return DropAlert(
                    category = DropCategory.DUNGEON,
                    player = player,
                    item = item,
                    detail = "From $chest",
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

            secretBonus.matchEntire(plain)?.let { match ->
                return DropAlert(
                    category = DropCategory.DUNGEON,
                    player = localPlayer.ifBlank { "You" },
                    item = cleanItem(match.groupValues[1]),
                    detail = "Secret bonus",
                    sourceLine = plain,
                )
            }

            extraStats.matchEntire(plain)?.let { match ->
                return DropAlert(
                    category = DropCategory.DUNGEON,
                    player = localPlayer.ifBlank { "You" },
                    item = cleanItem(match.groupValues[1]),
                    detail = "Extra stats",
                    sourceLine = plain,
                )
            }

            rareDrop.matchEntire(plain)?.let { match ->
                val item = cleanItem(match.groupValues[1])
                val detail = match.groupValues.getOrNull(2)?.trim().orEmpty()
                val category = categorizeRare(item, plain)
                return DropAlert(
                    category = category,
                    player = localPlayer.ifBlank { "You" },
                    item = item,
                    detail = detail,
                    sourceLine = plain,
                )
            }

            return null
        }

        private fun cleanItem(raw: String): String =
            raw.trim().trimEnd('!', '.').replace(rankPrefix, "").trim()

        private fun categorizeRare(item: String, plain: String): DropCategory {
            val blob = "${item.lowercase()} ${plain.lowercase()}"
            if (dianaHints.any { blob.contains(it) }) {
                return DropCategory.DIANA
            }
            if (kuudraHints.any { blob.contains(it) }) {
                return DropCategory.KUUDRA
            }
            return DropCategory.RARE
        }
    }
}
