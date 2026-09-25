package dev.anikh.guildbridge

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GuildLine(
    val username: String,
    val message: String,
)

object GuildChat {
    private val nameToken = Regex("^[A-Za-z0-9_]{1,16}$")
    private val userMention = Regex("<@!?\\d+>")
    private val roleMention = Regex("<@&\\d+>")
    private val channelMention = Regex("<#\\d+>")

    fun parse(line: String): GuildLine? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("Guild > ")) {
            return null
        }
        val body = trimmed.removePrefix("Guild > ")
        val separator = body.indexOf(": ")
        if (separator <= 0) {
            return null
        }
        val username = body.substring(0, separator)
            .split(' ')
            .lastOrNull { nameToken.matches(it) }
            ?: return null
        val message = body.substring(separator + 2).trim()
        if (message.isEmpty()) {
            return null
        }
        return GuildLine(username, message)
    }

    fun forDiscord(text: String): String {
        val cleaned = text
            .replace(userMention, "@user")
            .replace(roleMention, "@role")
            .replace(channelMention, "#channel")
            .replace("@everyone", "@ everyone")
            .replace("@here", "@ here")
            .replace("\u0000", "")
            .trim()
        return if (cleaned.length <= 2000) cleaned else cleaned.take(1999) + "…"
    }

    fun forGame(text: String): String {
        val cleaned = text
            .replace(userMention, "@user")
            .replace(roleMention, "@role")
            .replace(channelMention, "#channel")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length <= 240) cleaned else cleaned.take(239) + "…"
    }

    fun webhookPayload(line: GuildLine): String {
        val avatar = "https://minotar.net/helm/${URLEncoder.encode(line.username, StandardCharsets.UTF_8)}/128.png"
        return buildString {
            append("{\"username\":")
            append(jsonString(line.username.take(80)))
            append(",\"avatar_url\":")
            append(jsonString(avatar))
            append(",\"content\":")
            append(jsonString(forDiscord(line.message)))
            append(",\"allowed_mentions\":{\"parse\":[]}}")
        }
    }

    internal fun jsonString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
