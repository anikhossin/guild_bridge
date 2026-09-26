package dev.anikh.guildbridge

import com.google.gson.GsonBuilder
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

    fun sanitized(): BridgeConfig {
        enabled = enabled
        webhookUrl = present(webhookUrl)
        botToken = present(botToken)
        channelId = present(channelId).ifEmpty { BridgeSecrets.CHANNEL_ID }
        guildId = present(guildId).ifEmpty { BridgeSecrets.GUILD_ID }
        pollSeconds = pollSeconds.coerceIn(2, 30)
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
            return try {
                gson.fromJson(path.readText(), BridgeConfig::class.java)?.sanitized() ?: BridgeConfig().sanitized()
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
