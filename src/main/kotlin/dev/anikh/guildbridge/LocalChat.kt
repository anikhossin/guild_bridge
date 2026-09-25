package dev.anikh.guildbridge

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object LocalChat {
    fun showDiscord(author: String, text: String) {
        val safeAuthor = author.replace('\n', ' ').take(80).ifEmpty { "Discord" }
        val safeText = GuildChat.forGame(text)
        if (safeText.isEmpty()) {
            return
        }
        val line = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_PURPLE))
            .append(Component.literal("Guild Bridge").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_PURPLE))
            .append(Component.literal(safeAuthor).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" -> ").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(safeText).withStyle(ChatFormatting.YELLOW))
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
