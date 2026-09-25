# Guild Bridge

Client-side Fabric mod, written in Kotlin, that connects Hypixel guild chat with your Discord channel.

One player runs the mod. Guild chat is posted to Discord under that player's name and skin head. Discord messages come back only as a private message on that client.

## What it does

**Game to Discord.** A guild line such as `Guild > [MVP+] Steve: hello` is posted through the built-in webhook. Discord shows the name `Steve`, Steve's skin head, and the text `hello`. Join, leave, and party messages are ignored. `@everyone` and `@here` are neutralized so guild chat cannot ping the server.

**Discord to Hypixel.** A message in the guild channel appears only for you, in the same style as a Hypixel whisper: `From Name: message`. It is not sent with `/gc`, so the rest of the guild does not see it. Webhook posts are skipped, so guild chat does not bounce back into the game.

The webhook URL is compiled into [`BridgeSecrets.kt`](src/main/kotlin/dev/anikh/guildbridge/BridgeSecrets.kt). A webhook can only send, so reading Discord needs a bot token. Save it in game with `/guildbridge token`.

## Hypixel

This mod is built to stay inside [Hypixel's allowed modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications):

- It is client-side only. There are no mixins and it does not change packets.
- It only reads chat the client has already received, then sends that text to Discord.
- It does not move, click, or aim.
- Discord messages are shown only to you. The mod does not send them with `/gc` or any other chat command.

Run it on one account. If two guild members enable it at the same time, Discord gets a copy of each line from each client.

## Install

You need [Fabric Loader](https://fabricmc.net/use/installer/) for your exact Minecraft version, [Fabric API](https://modrinth.com/mod/fabric-api) for that same version, and Java 25.

A push to `main` builds a separate jar for each version and publishes them on the [GitHub Release](https://github.com/anikhossin/guild_bridge/releases). Download the jar that matches your game:

| Minecraft | Jar | Fabric API used to build |
| --- | --- | --- |
| 26.1 | `guild-bridge-1.0.0-mc26.1.jar` | 0.145.1+26.1 |
| 26.1.1 | `guild-bridge-1.0.0-mc26.1.1.jar` | 0.145.4+26.1.1 |
| 26.1.2 | `guild-bridge-1.0.0-mc26.1.2.jar` | 0.155.3+26.1.2 |
| 26.2 | `guild-bridge-1.0.0-mc26.2.jar` | 0.161.0+26.2 |

Each jar only loads on the Minecraft version in its name. Kotlin is packed inside the jar, so you do not install Fabric Language Kotlin separately.

Put the jar in `.minecraft/mods`.

## Discord to game

Guild chat posts to Discord with no extra setup. The other direction needs a Discord bot, because a webhook cannot read messages. The channel `1553130909270671401` and guild `1323913838143209622` are already compiled in.

### Create the bot

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and click **New Application**.
2. Open **Bot**, click **Reset Token**, and copy the token. Under **Privileged Gateway Intents**, turn on **Message Content Intent** and save. Send Messages and View Channel are not enough. Without that intent, Discord gives the mod blank messages and nothing is sent to Hypixel. The bot also needs Read Message History.
3. Copy the **Application ID** from **General Information**.
4. Invite the bot into the guild. Replace `APPLICATION_ID` in this link:

```
https://discord.com/oauth2/authorize?client_id=APPLICATION_ID&permissions=66560&scope=bot&guild_id=1323913838143209622&disable_guild_select=true
```

Those permissions are View Channel and Read Message History. After it joins, confirm it can see the guild-bridge channel.

### Save the token in game

Join a world and run:

```
/guildbridge token YOUR_BOT_TOKEN
```

The command stays on your client. It is not sent to Hypixel. The mod writes the token into `config/guildbridge.json` in that instance’s Minecraft folder (for the official launcher, `%appdata%\.minecraft\config\guildbridge.json`) and starts reading the channel.

Check it without printing the token:

```
/guildbridge token
```

That replies either that no token is saved, or that one is already saved. Run `/guildbridge token` with a new token to replace it. `/guildbridge` also reports whether Discord reading is active.

The saved file looks like this. `botToken` is filled in by the command. Leave the ids alone.

```json
{
  "enabled": true,
  "botToken": "saved-by-the-command",
  "channelId": "1553130909270671401",
  "guildId": "1323913838143209622",
  "pollSeconds": 3
}
```

Do not commit that file. If the token leaks, reset it on the Bot page and run `/guildbridge token` again with the new one. The token is also stored in your local Minecraft chat history, so don’t share screenshots of the command.

On join, the mod marks the newest Discord message as already seen, so old history is not dumped into chat. Posts made by the webhook are skipped, so guild chat does not bounce back into the game as a Discord message.

## Commands

These are client commands. They stay on your computer and are not sent to Hypixel.

| Command | Effect |
| --- | --- |
| `/guildbridge` | Shows whether the relay is on, and whether a bot token is saved |
| `/guildbridge on` | Starts relaying |
| `/guildbridge off` | Stops relaying |
| `/guildbridge reload` | Rereads `config/guildbridge.json` |
| `/guildbridge token` | Says whether a bot token is saved, without showing it |
| `/guildbridge token <token>` | Saves the Discord bot token and starts reading the guild channel |

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

The workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) runs the same four builds on push and pull request. Pushes to `main` also publish those jars on a GitHub Release tagged `v` plus the version in `gradle.properties`.

## Webhook

The webhook is part of the source and of every built jar. Anyone who can read the repository or the jar can post in that Discord channel. If this repository is public, regenerate the webhook in Discord channel settings and replace `WEBHOOK_URL` in `BridgeSecrets.kt`.
