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
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object BridgeRuntime {
    @Volatile var enabled: Boolean = true
    @Volatile var config: BridgeConfig = BridgeConfig()
    lateinit var configPath: java.nio.file.Path
}

object GuildBridgeClient : ClientModInitializer {
    private val logger = LogUtils.getLogger()
    private var seenWorld = false
    private var lastRelayKey = ""
    private var lastRelayAt = 0L
    private var lastDropKey = ""
    private var lastDropAt = 0L

    override fun onInitializeClient() {
        BridgeRuntime.configPath = FabricLoader.getInstance().configDir.resolve("guildbridge.json")
        BridgeRuntime.config = BridgeConfig.load(BridgeRuntime.configPath)
        BridgeRuntime.enabled = BridgeRuntime.config.enabled

        // Hypixel guild chat is a server/game message. Only listen on GAME; CHAT also fires for
        // some routed messages and would post the same line to Discord twice.
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) {
                return@register
            }
            val text = message.string
            relayGuildLine(text)
            relayDropLine(text)
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
                        ClientCommands.literal("relay")
                            .executes { context ->
                                val mode = if (BridgeRuntime.config.relayOwnGuildMessages) {
                                    "own (each player posts only their lines; use when multiple members run the mod)"
                                } else {
                                    "all (posts every guild line you see; use when only one member runs the mod)"
                                }
                                context.source.sendFeedback(
                                    Component.literal("Guild relay is $mode. Use /guildbridge relay all or own."),
                                )
                                1
                            }
                            .then(ClientCommands.literal("all").executes { setGuildRelay(it.source, false) })
                            .then(ClientCommands.literal("own").executes { setGuildRelay(it.source, true) }),
                    )
                    .then(
                        ClientCommands.literal("drops")
                            .executes { context ->
                                val state = if (BridgeRuntime.config.dropAlerts) "on" else "off"
                                context.source.sendFeedback(
                                    Component.literal("Drop alerts are $state. Use /guildbridge drops on or off."),
                                )
                                1
                            }
                            .then(ClientCommands.literal("on").executes { setDropAlerts(it.source, true) })
                            .then(ClientCommands.literal("off").executes { setDropAlerts(it.source, false) }),
                    )
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
                        ClientCommands.literal("webhook")
                            .executes { context ->
                                val message = if (WebhookUrl.normalize(BridgeRuntime.config.webhookUrl) == null) {
                                    "No webhook yet. Run /guildbridge webhook <url>."
                                } else {
                                    "A webhook is already saved. Run /guildbridge webhook <url> to replace it."
                                }
                                context.source.sendFeedback(Component.literal(message))
                                1
                            }
                            .then(
                                ClientCommands.argument("url", StringArgumentType.greedyString())
                                    .executes { context ->
                                        saveWebhook(context.source, StringArgumentType.getString(context, "url"))
                                    },
                            ),
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
        DropPoster.start()
        DiscordInbox.start()
        GuildSender.start()
        logger.info("Guild Bridge ready for guild chat relay")
    }

    private fun relayGuildLine(text: String) {
        if (!BridgeRuntime.enabled) {
            return
        }
        val line = GuildChat.parse(text) ?: return
        val localPlayer = Minecraft.getInstance().user.name
        if (!GuildChat.shouldRelayToDiscord(line, localPlayer, BridgeRuntime.config.relayOwnGuildMessages)) {
            return
        }
        val key = line.username + "\u0000" + line.message
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (key == lastRelayKey && now - lastRelayAt < 3_000L) {
                return
            }
            lastRelayKey = key
            lastRelayAt = now
        }
        WebhookPoster.enqueue(line)
    }

    private fun relayDropLine(text: String) {
        if (!BridgeRuntime.enabled || !BridgeRuntime.config.dropAlerts) {
            return
        }
        val localPlayer = Minecraft.getInstance().user.name
        val alert = DropAlert.parse(text, localPlayer) ?: return
        val key = alert.dedupKey()
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (key == lastDropKey && now - lastDropAt < 3_000L) {
                return
            }
            lastDropKey = key
            lastDropAt = now
        }
        DropPoster.enqueue(alert)
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
        source.sendFeedback(Component.literal("Bot token saved. Discord messages will show as private messages."))
        return 1
    }

    private fun saveWebhook(source: FabricClientCommandSource, raw: String): Int {
        val webhook = WebhookUrl.normalize(raw)
        if (webhook == null) {
            source.sendError(Component.literal("That is not a Discord webhook URL."))
            return 0
        }
        BridgeRuntime.config.webhookUrl = webhook
        BridgeConfig.save(BridgeRuntime.configPath, BridgeRuntime.config)
        WebhookPoster.noteConfigured()
        DropPoster.noteConfigured()
        source.sendFeedback(Component.literal("Webhook saved. Guild chat will post to that Discord channel."))
        return 1
    }

    private fun setGuildRelay(source: FabricClientCommandSource, ownOnly: Boolean): Int {
        BridgeRuntime.config.relayOwnGuildMessages = ownOnly
        BridgeConfig.save(BridgeRuntime.configPath, BridgeRuntime.config)
        val message = if (ownOnly) {
            "Guild relay set to own messages only (recommended when multiple guild members use the mod)."
        } else {
            "Guild relay set to all guild chat you see (use only on one bridge client)."
        }
        source.sendFeedback(Component.literal(message))
        return 1
    }

    private fun setDropAlerts(source: FabricClientCommandSource, value: Boolean): Int {
        BridgeRuntime.config.dropAlerts = value
        BridgeConfig.save(BridgeRuntime.configPath, BridgeRuntime.config)
        source.sendFeedback(Component.literal(if (value) "Drop alerts on." else "Drop alerts off."))
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
        val outbound = if (WebhookUrl.normalize(config.webhookUrl) == null) {
            "Game to Discord is waiting for /guildbridge webhook <url>"
        } else {
            "Game to Discord uses the saved webhook"
        }
        val inbox = if (config.botToken.isEmpty()) {
            "Discord to Hypixel is waiting for /guildbridge token <token>"
        } else {
            "Discord to Hypixel shows private messages from channel ${config.channelId}"
        }
        val relay = if (BridgeRuntime.enabled) "on" else "off"
        val drops = if (config.dropAlerts) "on" else "off"
        val guildRelay = if (config.relayOwnGuildMessages) "own" else "all"
        return Component.literal("Guild Bridge is $relay. Guild relay is $guildRelay. Drop alerts are $drops. $outbound. $inbox")
    }
}
