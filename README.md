# Guild Bridge

Client-side Fabric mod, written in Kotlin, that connects Hypixel guild chat with your Discord channel.

One player runs the mod. Their client reads guild chat the game already shows and posts each line to Discord as that player, with their skin head. Discord messages come back into that same client's local chat. The mod never sends chat or commands to Hypixel.

## What it does

**Game to Discord.** A guild line such as `Guild > [MVP+] Steve: hello` is posted through the built-in webhook. Discord shows the name `Steve`, Steve's skin head, and the text `hello`. Join, leave, and party messages are ignored. `@everyone` and `@here` are neutralized so guild chat cannot ping the server.

**Discord to game.** Messages from people in the channel appear in your Minecraft chat:

`[Discord] Name: message [reply]`

`[reply]` puts `/gc message` in the chat box. You press Enter yourself. Nothing is sent until you do that.

The webhook URL is compiled into [`BridgeSecrets.kt`](src/main/kotlin/dev/anikh/guildbridge/BridgeSecrets.kt). A webhook can only send, so reading Discord needs a bot token in the config file described below.

## Hypixel

This mod is built to stay inside [Hypixel's allowed modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications):

- It is client-side only. There are no mixins and it does not change packets.
- It only reads chat the client has already received, then sends that text to Discord.
- It does not move, click, aim, or type for you.
- It does not run `/gc` or any other command. Hypixel does not allow a mod to send chat or commands automatically.

Run it on one account. If two guild members enable it at the same time, Discord gets a copy of each line from each client.

## Install

You need [Fabric Loader](https://fabricmc.net/use/installer/) for your exact Minecraft version, [Fabric API](https://modrinth.com/mod/fabric-api) for that same version, and Java 25.

GitHub Actions builds a separate jar for each version. Download the artifact that matches your game:

| Minecraft | Artifact | Fabric API used to build |
| --- | --- | --- |
| 26.1 | `guild-bridge-mc26.1` | 0.145.1+26.1 |
| 26.1.1 | `guild-bridge-mc26.1.1` | 0.145.4+26.1.1 |
| 26.1.2 | `guild-bridge-mc26.1.2` | 0.155.3+26.1.2 |
| 26.2 | `guild-bridge-mc26.2` | 0.161.0+26.2 |

Each jar only loads on the Minecraft version in its name. Kotlin is packed inside the jar, so you do not install Fabric Language Kotlin separately.

Put the jar in `.minecraft/mods`.

## Discord to game

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and create an application.
2. Open Bot, reset the token, and copy it. Enable Message Content Intent.
3. Invite the bot to your server with the Read Message History and View Channel permissions, into the same channel as the webhook.
4. Launch Minecraft once so the mod can create `config/guildbridge.json`.
5. Set `botToken` to that token. `channelId` and `guildId` are already filled in. Save the file and run `/guildbridge reload`.

`config/guildbridge.json`:

```json
{
  "enabled": true,
  "botToken": "",
  "channelId": "1553130909270671401",
  "guildId": "1323913838143209622",
  "pollSeconds": 3
}
```

The bot token stays in that local file. Do not commit it.

On join, the mod marks the newest Discord message as already seen, so old history is not dumped into chat. Posts made by the webhook are skipped, so guild chat does not bounce back into the game as a Discord message.

## Commands

These are client commands. They stay on your computer and are not sent to Hypixel.

| Command | Effect |
| --- | --- |
| `/guildbridge` | Shows whether the relay is on, and whether Discord reading is configured |
| `/guildbridge on` | Starts relaying |
| `/guildbridge off` | Stops relaying |
| `/guildbridge reload` | Rereads `config/guildbridge.json` |

## Build

Java 25 is required, including for the Gradle JVM. [The Fabric 26.1 note](https://fabricmc.net/2026/03/14/261.html) covers that requirement.

Build the default 26.1 jar:

```powershell
./gradlew build
```

Build every version into `build/libs`:

```powershell
./scripts/build-all.ps1
```

The workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) runs the same four builds on push and pull request, and uploads one artifact per Minecraft version.

## Webhook

The webhook is part of the source and of every built jar. Anyone who can read the repository or the jar can post in that Discord channel. If this repository is public, regenerate the webhook in Discord channel settings and replace `WEBHOOK_URL` in `BridgeSecrets.kt`.
