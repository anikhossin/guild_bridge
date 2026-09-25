package dev.anikh.guildbridge

import com.mojang.logging.LogUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object GuildSender {
    private val logger = LogUtils.getLogger()
    private val queue = LinkedBlockingQueue<PrivateMessage>(50)

    fun start() {
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val message = queue.poll(1, TimeUnit.SECONDS) ?: continue
                    if (!BridgeRuntime.enabled) {
                        continue
                    }
                    LocalChat.showDiscord(message.author, message.text)
                } catch (closed: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (error: Exception) {
                    logger.warn("Guild Bridge could not show a Discord message: {}", error.toString())
                }
            }
        }, "guild-bridge-send").apply { isDaemon = true }.start()
    }

    fun enqueue(author: String, text: String) {
        if (!BridgeRuntime.enabled || text.isBlank()) {
            return
        }
        if (!queue.offer(PrivateMessage(author, text))) {
            logger.warn("Guild Bridge dropped a Discord message because the queue is full")
        }
    }

    private data class PrivateMessage(val author: String, val text: String)
}
