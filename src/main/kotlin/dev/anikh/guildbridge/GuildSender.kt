package dev.anikh.guildbridge

import com.mojang.logging.LogUtils
import net.minecraft.client.Minecraft
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object GuildSender {
    private val logger = LogUtils.getLogger()
    private val queue = LinkedBlockingQueue<String>(50)

    fun start() {
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val body = queue.poll(1, TimeUnit.SECONDS) ?: continue
                    if (!BridgeRuntime.enabled) {
                        continue
                    }
                    send(body)
                    Thread.sleep(1500)
                } catch (closed: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (error: Exception) {
                    logger.warn("Guild Bridge could not send guild chat: {}", error.toString())
                    LocalChat.showStatus("Could not send that Discord message to guild chat.")
                }
            }
        }, "guild-bridge-send").apply { isDaemon = true }.start()
    }

    fun enqueue(author: String, text: String) {
        if (!BridgeRuntime.enabled) {
            return
        }
        val safeAuthor = author.replace(Regex("[^A-Za-z0-9_ .-]"), "").trim().ifEmpty { "Discord" }.take(32)
        val body = GuildChat.forGame("$safeAuthor: $text")
        if (body.isEmpty()) {
            return
        }
        if (!queue.offer(body)) {
            logger.warn("Guild Bridge dropped a Discord message because the send queue is full")
        }
    }

    private fun send(body: String) {
        val client = Minecraft.getInstance()
        client.execute {
            val player = client.player
            if (player == null) {
                LocalChat.showStatus("Discord message was not sent. Join Hypixel first.")
                return@execute
            }
            player.connection.sendCommand("gc $body")
        }
    }
}
