package dev.anikh.guildbridge

object WebhookUrl {
    private val discordWebhook = Regex(
        "^https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api/webhooks/[0-9]{17,20}/[A-Za-z0-9_-]{10,}$",
    )

    fun normalize(raw: String): String? {
        val cleaned = raw.trim().substringBefore('?').trimEnd('/')
        return cleaned.takeIf { discordWebhook.matches(it) }
    }
}
