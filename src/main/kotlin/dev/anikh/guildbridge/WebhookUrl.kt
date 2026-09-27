package dev.anikh.guildbridge

object WebhookUrl {
    private val discordWebhook = Regex(
        "^https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api/webhooks/[0-9]{17,20}/[A-Za-z0-9_-]{10,}$",
    )

    fun normalize(raw: String): String? {
        var cleaned = raw.trim()
        if (cleaned.length >= 2 &&
            ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
                (cleaned.startsWith("<") && cleaned.endsWith(">")))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        cleaned = cleaned.substringBefore('?').trimEnd('/')
        return cleaned.takeIf { discordWebhook.matches(it) }
    }
}
