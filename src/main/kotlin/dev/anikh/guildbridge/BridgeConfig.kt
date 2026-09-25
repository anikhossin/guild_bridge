package dev.anikh.guildbridge

import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class BridgeConfig {
    @JvmField var enabled: Boolean = true
    @JvmField var botToken: String = ""
    @JvmField var channelId: String = BridgeSecrets.CHANNEL_ID
    @JvmField var guildId: String = BridgeSecrets.GUILD_ID
    @JvmField var pollSeconds: Int = 3

    fun sanitized(): BridgeConfig {
        enabled = enabled
        botToken = botToken.trim()
        channelId = channelId.trim().ifEmpty { BridgeSecrets.CHANNEL_ID }
        guildId = guildId.trim().ifEmpty { BridgeSecrets.GUILD_ID }
        pollSeconds = pollSeconds.coerceIn(2, 30)
        return this
    }

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
