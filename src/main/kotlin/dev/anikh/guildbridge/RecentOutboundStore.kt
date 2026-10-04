package dev.anikh.guildbridge

/**
 * Drops duplicate guild lines before they hit the Discord webhook queue.
 * Hypixel (and Fabric) can deliver the same chat line twice with different formatting.
 */
object RecentOutboundStore {
    private const val WINDOW_MS = 12_000L
    private const val MAX_ENTRIES = 32

    private data class Entry(val atMs: Long, val key: String)

    private val recent = ArrayDeque<Entry>()

    /** Canonical key for a parsed guild line (case-insensitive author, normalized body). */
    fun guildKey(line: GuildLine): String {
        val user = line.username.trim().lowercase()
        val body = GuildChat.forDiscord(line.message)
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
        return "$user\u0000$body"
    }

    /**
     * @return true if this line should be sent; false if it is a duplicate within [WINDOW_MS].
     */
    fun trySendGuild(line: GuildLine, rawPlain: String? = null): Boolean {
        val key = guildKey(line)
        val plainKey = rawPlain?.let { plainGuildFingerprint(it) }
        synchronized(this) {
            prune()
            if (recent.any { it.key == key || (plainKey != null && it.key == plainKey) }) {
                return false
            }
            remember(key)
            if (plainKey != null && plainKey != key) {
                remember(plainKey)
            }
            return true
        }
    }

    private fun plainGuildFingerprint(plain: String): String {
        val normalized = GuildChat.plain(plain).lowercase()
        return "plain\u0000$normalized"
    }

    private fun remember(key: String) {
        if (recent.size >= MAX_ENTRIES) {
            recent.removeFirst()
        }
        recent.addLast(Entry(System.currentTimeMillis(), key))
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        while (recent.isNotEmpty() && recent.first().atMs < cutoff) {
            recent.removeFirst()
        }
    }
}
