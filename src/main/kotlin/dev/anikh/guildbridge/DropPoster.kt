package dev.anikh.guildbridge

import com.mojang.logging.LogUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object DropPoster {
    private val logger = LogUtils.getLogger()
    private val queue = LinkedBlockingQueue<DropAlert>(50)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
    private var lastKey = ""
    private var lastAt = 0L
    private var missingWarned = false

    fun start() {
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val alert = queue.poll(1, TimeUnit.SECONDS) ?: continue
                    if (!BridgeRuntime.enabled || !BridgeRuntime.config.dropAlerts) {
                        continue
                    }
                    post(alert, retries = 3)
                    Thread.sleep(1100)
                } catch (closed: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (error: Exception) {
                    logger.warn("Guild Bridge drop post failed: {}", error.toString())
                }
            }
        }, "guild-bridge-drops").apply { isDaemon = true }.start()
    }

    fun noteConfigured() {
        missingWarned = false
    }

    fun enqueue(alert: DropAlert) {
        if (!BridgeRuntime.enabled || !BridgeRuntime.config.dropAlerts) {
            return
        }
        if (WebhookUrl.normalize(BridgeRuntime.config.webhookUrl) == null) {
            if (!missingWarned) {
                missingWarned = true
                LocalChat.showStatus("Drop alerts need a webhook. Run /guildbridge webhook <url>.")
            }
            return
        }
        val key = alert.dedupKey()
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (key == lastKey && now - lastAt < 3_000L) {
                return
            }
            lastKey = key
            lastAt = now
        }
        if (!queue.offer(alert)) {
            logger.warn("Guild Bridge dropped a loot alert because the queue is full")
        }
    }

    private fun post(alert: DropAlert, retries: Int) {
        val webhook = WebhookUrl.normalize(BridgeRuntime.config.webhookUrl) ?: return
        val request = HttpRequest.newBuilder(URI.create(webhook))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("User-Agent", "GuildBridge (https://github.com/anikhossin/guild_bridge, 1.0)")
            .POST(HttpRequest.BodyPublishers.ofString(alert.webhookPayload()))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            204, 200 -> Unit
            429 -> {
                val retry = Regex(""""retry_after"\s*:\s*([0-9.]+)""")
                    .find(response.body())
                    ?.groupValues
                    ?.get(1)
                    ?.toDoubleOrNull()
                    ?: 2.0
                Thread.sleep((retry * 1000).toLong().coerceIn(500, 10_000))
                if (retries > 0) {
                    post(alert, retries - 1)
                }
            }
            else -> {
                logger.warn("Guild Bridge drop webhook returned HTTP {}", response.statusCode())
            }
        }
    }
}
