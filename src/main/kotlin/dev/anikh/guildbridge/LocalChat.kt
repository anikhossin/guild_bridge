package dev.anikh.guildbridge

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style

object LocalChat {
    fun showDiscord(author: String, text: String) {
        val safeAuthor = author.replace('\n', ' ').take(80).ifEmpty { "Discord" }
        val safeText = GuildChat.forGame(text)
        if (safeText.isEmpty()) {
            return
        }
        // SuggestCommand only fills the chat box. The player presses Enter.
        // Hypixel does not allow a mod to send chat or commands on its own.
        val line = Component.empty()
            .append(Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(safeAuthor).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(safeText).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" "))
            .append(
                Component.literal("[reply]").withStyle(
                    Style.EMPTY
                        .withColor(ChatFormatting.DARK_GRAY)
                        .withClickEvent(ClickEvent.SuggestCommand("/gc $safeText"))
                        .withHoverEvent(
                            HoverEvent.ShowText(
                                Component.literal("Fills guild chat. Press Enter yourself to send."),
                            ),
                        ),
                ),
            )
        show(line)
    }

    fun showStatus(text: String) {
        show(
            Component.literal("[Guild Bridge] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)),
        )
    }

    private fun show(line: Component) {
        val client = Minecraft.getInstance()
        client.execute {
            client.player?.sendSystemMessage(line)
        }
    }
}
