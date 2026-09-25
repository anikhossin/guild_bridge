package dev.anikh.guildbridge

import com.google.gson.JsonParser
import com.mojang.logging.LogUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

object DiscordInbox {
    private val logger = LogUtils.getLogger()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private val inWorld = AtomicBoolean(false)
    private val primed = AtomicBoolean(false)
    private val channelChecked = AtomicBoolean(false)
    private var lastId: String? = null
    private var missingTokenLogged = false
    private var channelProblem = ""
    private var contentWarned = false

    fun start() {
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    tick()
                } catch (closed: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (error: Exception) {
                    logger.warn("Guild Bridge Discord inbox failed: {}", error.toString())
                    Thread.sleep(3_000)
                }
            }
        }, "guild-bridge-inbox").apply { isDaemon = true }.start()
    }

    fun onEnteredWorld() {
        if (inWorld.compareAndSet(false, true)) {
            primed.set(false)
            lastId = null
        }
    }

    fun recheck() {
        primed.set(false)
        channelChecked.set(false)
        lastId = null
    }

    fun onLeftWorld() {
        inWorld.set(false)
        primed.set(false)
        channelChecked.set(false)
        lastId = null
    }

    private fun tick() {
        if (!BridgeRuntime.enabled || !inWorld.get()) {
            Thread.sleep(500)
            return
        }
        val config = BridgeRuntime.config
        val token = config.botToken
        if (token.isEmpty()) {
            if (!missingTokenLogged) {
                missingTokenLogged = true
                logger.info("Guild Bridge Discord-to-game is waiting for a bot token in config/guildbridge.json")
            }
            Thread.sleep(2_000)
            return
        }
        if (!channelChecked.get()) {
            if (!channelBelongsToGuild(token, config.channelId, config.guildId)) {
                Thread.sleep(5_000)
                return
            }
            channelChecked.set(true)
        }
        if (!primed.get()) {
            val newest = fetch(token, config.channelId, after = null)
            lastId = newest.maxByOrNull { it.id.toULongOrNull() ?: 0uL }?.id
            warnIfContentHidden(newest)
            primed.set(true)
        } else {
            val fresh = fetch(token, config.channelId, after = lastId)
                .sortedBy { it.id.toULongOrNull() ?: 0uL }
            warnIfContentHidden(fresh)
            for (message in fresh) {
                lastId = message.id
                if (message.webhookId != null || message.text.isEmpty()) {
                    continue
                }
                GuildSender.enqueue(message.author, message.text)
            }
        }
        Thread.sleep(config.pollSeconds * 1000L)
    }

    private fun channelBelongsToGuild(token: String, channelId: String, guildId: String): Boolean {
        val request = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/channels/$channelId"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bot $token")
            .header("User-Agent", "GuildBridge (https://github.com/anikhossin/guild_bridge, 1.0)")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            reportChannelProblem("could not read channel $channelId (HTTP ${response.statusCode()})")
            return false
        }
        val root = JsonParser.parseString(response.body())
        val actualGuild = if (root.isJsonObject) {
            root.asJsonObject.get("guild_id")?.takeUnless { it.isJsonNull }?.asString
        } else {
            null
        }
        if (actualGuild != guildId) {
            reportChannelProblem("channel $channelId is not in guild $guildId")
            return false
        }
        channelProblem = ""
        return true
    }

    private fun warnIfContentHidden(messages: List<Incoming>) {
        val hidden = messages.any { it.webhookId == null && it.text.isEmpty() }
        if (!hidden || contentWarned) {
            return
        }
        contentWarned = true
        LocalChat.showStatus("Discord is hiding message text. In the Developer Portal, open your bot, turn on Message Content Intent, save, then send a new Discord message.")
    }

    private fun reportChannelProblem(problem: String) {
        if (problem == channelProblem) {
            return
        }
        channelProblem = problem
        logger.warn("Guild Bridge {}", problem)
    }

    private fun fetch(token: String, channelId: String, after: String?): List<Incoming> {
        val query = if (after == null) "limit=1" else "limit=50&after=$after"
        val request = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/channels/$channelId/messages?$query"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bot $token")
            .header("User-Agent", "GuildBridge (https://github.com/anikhossin/guild_bridge, 1.0)")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 429) {
            Thread.sleep(2_000)
            return emptyList()
        }
        if (response.statusCode() !in 200..299) {
            logger.warn("Guild Bridge Discord read returned HTTP {}", response.statusCode())
            return emptyList()
        }
        val root = JsonParser.parseString(response.body())
        if (!root.isJsonArray) {
            return emptyList()
        }
        return root.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) {
                return@mapNotNull null
            }
            val obj = element.asJsonObject
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val content = obj.get("content")?.asString.orEmpty()
            val webhookId = obj.get("webhook_id")?.takeUnless { it.isJsonNull }?.asString
            val authorObj = obj.getAsJsonObject("author")
            val author = authorObj?.get("global_name")?.takeUnless { it.isJsonNull }?.asString
                ?: authorObj?.get("username")?.asString
                ?: "Discord"
            val attachmentNames = obj.getAsJsonArray("attachments")
                ?.mapNotNull { attachment ->
                    attachment.asJsonObject?.get("filename")?.asString
                }
                .orEmpty()
            val text = buildString {
                append(content.trim())
                if (attachmentNames.isNotEmpty()) {
                    if (isNotEmpty()) {
                        append(' ')
                    }
                    append(attachmentNames.joinToString(prefix = "(", postfix = ")"))
                }
            }
            Incoming(id, author, text, webhookId)
        }
    }

    private data class Incoming(
        val id: String,
        val author: String,
        val text: String,
        val webhookId: String?,
    )
}
