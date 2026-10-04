package dev.anikh.guildbridge

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BridgeConfig {
    @JvmField var enabled: Boolean = true
    @JvmField var webhookUrl: String = ""
    @JvmField var botToken: String = ""
    @JvmField var channelId: String = BridgeSecrets.CHANNEL_ID
    @JvmField var guildId: String = BridgeSecrets.GUILD_ID
    @JvmField var pollSeconds: Int = 3
    @JvmField var dropAlerts: Boolean = true
    @JvmField var relayOwnGuildMessages: Boolean = true

    fun sanitized(): BridgeConfig {
        enabled = enabled
        webhookUrl = present(webhookUrl)
        botToken = present(botToken)
        channelId = present(channelId).ifEmpty { BridgeSecrets.CHANNEL_ID }
        guildId = present(guildId).ifEmpty { BridgeSecrets.GUILD_ID }
        pollSeconds = pollSeconds.coerceIn(2, 30)
        dropAlerts = dropAlerts
        relayOwnGuildMessages = relayOwnGuildMessages
        return this
    }

    private fun present(value: String?): String = value?.trim().orEmpty()

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun load(path: Path): BridgeConfig {
            if (!path.exists()) {
                val created = BridgeConfig().sanitized()
                save(path, created)
                return created
            }
            val text = path.readText()
            return try {
                val config = gson.fromJson(text, BridgeConfig::class.java) ?: BridgeConfig()
                var migrated = false
                val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                if (root != null) {
                    if (!root.has("relayOwnGuildMessages")) {
                        config.relayOwnGuildMessages = true
                        migrated = true
                    }
                    if (!root.has("dropAlerts")) {
                        config.dropAlerts = true
                        migrated = true
                    }
                }
                val sanitized = config.sanitized()
                if (migrated) {
                    save(path, sanitized)
                }
                sanitized
            } catch (_: Exception) {
                BridgeConfig().sanitized()
            }
        }

        fun save(path: Path, config: BridgeConfig) {
            path.parent?.createDirectories()
            path.writeText(gson.toJson(config.sanitized()))
        }
    }
}
