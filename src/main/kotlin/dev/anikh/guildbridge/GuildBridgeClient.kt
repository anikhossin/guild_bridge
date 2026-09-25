package dev.anikh.guildbridge

import com.mojang.logging.LogUtils
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component

object BridgeRuntime {
    @Volatile var enabled: Boolean = true
    @Volatile var config: BridgeConfig = BridgeConfig()
    lateinit var configPath: java.nio.file.Path
}

object GuildBridgeClient : ClientModInitializer {
    private val logger = LogUtils.getLogger()
    private var seenWorld = false

    override fun onInitializeClient() {
        BridgeRuntime.configPath = FabricLoader.getInstance().configDir.resolve("guildbridge.json")
        BridgeRuntime.config = BridgeConfig.load(BridgeRuntime.configPath)
        BridgeRuntime.enabled = BridgeRuntime.config.enabled

        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) {
                return@register
            }
            relayGuildLine(message.string)
        }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            relayGuildLine(message.string)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val inWorld = client.player != null && client.level != null
            if (inWorld && !seenWorld) {
                DiscordInbox.onEnteredWorld()
            } else if (!inWorld && seenWorld) {
                DiscordInbox.onLeftWorld()
            }
            seenWorld = inWorld
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("guildbridge")
                    .executes { context ->
                        context.source.sendFeedback(statusText())
                        1
                    }
                    .then(ClientCommands.literal("on").executes { setEnabled(it.source, true) })
                    .then(ClientCommands.literal("off").executes { setEnabled(it.source, false) })
                    .then(
                        ClientCommands.literal("reload").executes { context ->
                            BridgeRuntime.config = BridgeConfig.load(BridgeRuntime.configPath)
                            BridgeRuntime.enabled = BridgeRuntime.config.enabled
                            DiscordInbox.recheck()
                            context.source.sendFeedback(Component.literal("Guild Bridge config reloaded."))
                            1
                        },
                    )
                    .then(
                        ClientCommands.literal("token")
                            .executes { context ->
                                val message = if (BridgeRuntime.config.botToken.isEmpty()) {
                                    "No bot token yet. Run /guildbridge token <token>."
                                } else {
                                    "A bot token is already saved. Run /guildbridge token <token> to replace it."
                                }
                                context.source.sendFeedback(Component.literal(message))
                                1
                            }
                            .then(
                                ClientCommands.argument("token", StringArgumentType.word())
                                    .executes { context ->
                                        saveToken(context.source, StringArgumentType.getString(context, "token"))
                                    },
                            ),
                    ),
            )
        }

        WebhookPoster.start()
        DiscordInbox.start()
        GuildSender.start()
        logger.info("Guild Bridge ready for guild chat relay")
    }

    private fun relayGuildLine(text: String) {
        if (!BridgeRuntime.enabled) {
            return
        }
        val line = GuildChat.parse(text) ?: return
        WebhookPoster.enqueue(line)
    }

    private fun saveToken(source: FabricClientCommandSource, token: String): Int {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            source.sendError(Component.literal("That token is empty."))
            return 0
        }
        BridgeRuntime.config.botToken = trimmed
        BridgeConfig.save(BridgeRuntime.configPath, BridgeRuntime.config)
        DiscordInbox.recheck()
        source.sendFeedback(Component.literal("Bot token saved. Discord messages will be sent to Hypixel guild chat."))
        return 1
    }

    private fun setEnabled(source: FabricClientCommandSource, value: Boolean): Int {
        BridgeRuntime.enabled = value
        BridgeRuntime.config.enabled = value
        BridgeConfig.save(BridgeRuntime.configPath, BridgeRuntime.config)
        source.sendFeedback(Component.literal(if (value) "Guild Bridge on." else "Guild Bridge off."))
        return 1
    }

    private fun statusText(): Component {
        val config = BridgeRuntime.config
        val inbox = if (config.botToken.isEmpty()) {
            "Discord to Hypixel is waiting for /guildbridge token <token>"
        } else {
            "Discord to Hypixel sends /gc from channel ${config.channelId}"
        }
        val relay = if (BridgeRuntime.enabled) "on" else "off"
        return Component.literal("Guild Bridge is $relay. Game to Discord uses the built-in webhook. $inbox")
    }
}
