package com.unciv.discord

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Serializable
data class BotData(
    val players: MutableList<Long> = mutableListOf(),
    val elos: MutableList<Int> = mutableListOf(),
    val tokens: MutableList<Int> = mutableListOf()
)

object DiscordBot {
    private var started = false
    private val dataFile = File("bot_data.json")
    private var data = BotData()

    fun startBot() {
        if (started) return
        started = true

        
        val token = System.getenv("DISCORD_BOT_TOKEN")
            ?: System.getProperty("DISCORD_BOT_TOKEN")
            ?: File("discord_token.txt").takeIf { it.exists() }?.readText()?.trim()
            ?: return println("❌ No Discord token found!")

        loadData()

        val jda = JDABuilder.createDefault(token)
            .setActivity(Activity.playing("Unciv"))
            .addEventListeners(CommandListener())
            .build()

        // Register slash commands
        jda.updateCommands().addCommands(
            Commands.slash("ping", "Replies with Pong!"),
            Commands.slash("register", "Register yourself as a player"),
            Commands.slash("registerother", "Register another player")
                .addOption(OptionType.USER, "user", "The user to register", true),

            Commands.slash("elo", "Show your or another user's ELO")
                .addOption(OptionType.USER, "user", "The user to check", false),

            Commands.slash("setelo", "Set a player's ELO")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "value", "New ELO value", true),

            Commands.slash("addelo", "Add or remove ELO to a player")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "value", "ELO change (can be negative)", true),

            Commands.slash("duel", "Calculate ELO change for a duel")
                .addOption(OptionType.USER, "winner", "Winner", true)
                .addOption(OptionType.USER, "loser", "Loser", true),

            Commands.slash("leaderboard", "Show top 10 players"),

            Commands.slash("tokens", "Check your or another user's tokens")
                .addOption(OptionType.USER, "user", "The user to check", false),

            Commands.slash("addtokens", "Add tokens to a player")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "amount", "Number of tokens", true),

            Commands.slash("removetokens", "Remove tokens from a player")
                .addOption(OptionType.USER, "user", "Target user", true)
                .addOption(OptionType.INTEGER, "amount", "Number of tokens", true)
        ).queue()

        println("✅ Discord bot started successfully.")
    }

    fun saveData() {
        dataFile.writeText(Json.encodeToString(data))
        println("📂 Bot data file path: ${dataFile.absolutePath}")
    }

    fun loadData() {
        if (dataFile.exists()) {
            data = Json.decodeFromString(dataFile.readText())
        }
    }

    fun registerUser(userId: Long): Boolean {
        if (userId in data.players) return false
        data.players.add(userId)
        data.elos.add(1000)
        data.tokens.add(0)
        saveData()
        return true
    }

    fun getElo(userId: Long): Int? = data.players.indexOf(userId).takeIf { it != -1 }?.let { data.elos[it] }

    fun setElo(userId: Long, newElo: Int): Boolean {
        val index = data.players.indexOf(userId)
        if (index == -1) return false
        data.elos[index] = newElo
        saveData()
        return true
    }

    fun addElo(userId: Long, delta: Int): Boolean {
        val index = data.players.indexOf(userId)
        if (index == -1) return false
        data.elos[index] += delta
        saveData()
        return true
    }

    fun addTokens(userId: Long, amount: Int) {
        val index = data.players.indexOf(userId)
        if (index == -1) return
        data.tokens[index] += amount
        saveData()
    }

    fun removeTokens(userId: Long, amount: Int) {
        val index = data.players.indexOf(userId)
        if (index == -1) return
        data.tokens[index] = max(0, data.tokens[index] - amount)
        saveData()
    }

    fun getTokens(userId: Long): Int? = data.players.indexOf(userId).takeIf { it != -1 }?.let { data.tokens[it] }

    fun leaderboard(): String {
        val sorted = data.players.zip(data.elos).sortedByDescending { it.second }
        return sorted.take(10).mapIndexed { i, (id, elo) -> "${i + 1}. <@$id> — $elo ELO" }
            .joinToString("\n")
    }

    // ELO formula like chess: K = 32
    fun duel(winnerId: Long, loserId: Long): Pair<Int, Int>? {
        val wIndex = data.players.indexOf(winnerId)
        val lIndex = data.players.indexOf(loserId)
        if (wIndex == -1 || lIndex == -1) return null

        val k = 32
        val wElo = data.elos[wIndex]
        val lElo = data.elos[lIndex]

        val expectedW = 1.0 / (1 + 10.0.pow((lElo - wElo) / 400.0))
        val expectedL = 1.0 / (1 + 10.0.pow((wElo - lElo) / 400.0))

        val newWElo = (wElo + k * (1 - expectedW)).toInt()
        val newLElo = (lElo + k * (0 - expectedL)).toInt()

        data.elos[wIndex] = newWElo
        data.elos[lIndex] = newLElo
        saveData()

        return Pair(newWElo - wElo, newLElo - lElo)
    }
}

class CommandListener : ListenerAdapter() {
    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {

            // ✅ Test command
            "ping" -> event.reply("🏓 Pong!").queue()

            // ✅ Register yourself
            "register" -> {
                val userId = event.user.idLong
                if (DiscordBot.registerUser(userId))
                    event.reply("✅ You are now registered, ${event.user.asMention}!").queue()
                else
                    event.reply("⚠️ You are already registered!").queue()
            }

            // ✅ Register another user
            "registerother" -> {
                val user = event.getOption("user")!!.asUser
                if (DiscordBot.registerUser(user.idLong))
                    event.reply("✅ ${user.asMention} has been registered!").queue()
                else
                    event.reply("⚠️ ${user.asMention} is already registered!").queue()
            }

            // ✅ Show ELO
            "elo" -> {
                val user = event.getOption("user")?.asUser ?: event.user
                val elo = DiscordBot.getElo(user.idLong)
                if (elo != null)
                    event.reply("⭐ ${user.asMention} has **$elo ELO**").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered. Use `/register` first!").queue()
            }

            // ✅ Set a player's ELO
            "setelo" -> {
                val user = event.getOption("user")!!.asUser
                val value = event.getOption("value")!!.asInt
                if (DiscordBot.setElo(user.idLong, value))
                    event.reply("✅ Set ${user.asMention}'s ELO to **$value**").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered.").queue()
            }

            // ✅ Add/remove ELO
            "addelo" -> {
                val user = event.getOption("user")!!.asUser
                val delta = event.getOption("value")!!.asInt
                if (DiscordBot.addElo(user.idLong, delta))
                    event.reply("✅ Added **$delta** elo to ${user.asMention} ").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered.").queue()
            }

            // ✅ ELO duel calculator
            "duel" -> {
                val winner = event.getOption("winner")!!.asUser
                val loser = event.getOption("loser")!!.asUser
                val result = DiscordBot.duel(winner.idLong, loser.idLong)
                if (result != null) {
                    val (wChange, lChange) = result
                    event.reply(
                        "⚔️ Duel result:\n" +
                            "🏆 ${winner.asMention}: **+$wChange ELO**\n" +
                            "💀 ${loser.asMention}: **$lChange ELO**"
                    ).queue()
                } else {
                    event.reply("❌ Both players must be registered before dueling!").queue()
                }
            }

            // ✅ Leaderboard
            "leaderboard" -> {
                val board = DiscordBot.leaderboard()
                if (board.isNotEmpty())
                    event.reply("🏅 **Top 10 Players**\n$board").queue()
                else
                    event.reply("❌ No players registered yet!").queue()
            }

            // ✅ Tokens check
            "tokens" -> {
                val user = event.getOption("user")?.asUser ?: event.user
                val tokens = DiscordBot.getTokens(user.idLong)
                if (tokens != null)
                    event.reply("🎟️ ${user.asMention} has **$tokens tokens**.").queue()
                else
                    event.reply("❌ ${user.asMention} is not registered. Use `/register` first!").queue()
            }

            // ✅ Add tokens
            "addtokens" -> {
                val user = event.getOption("user")!!.asUser
                val amount = event.getOption("amount")!!.asInt
                DiscordBot.addTokens(user.idLong, amount)
                event.reply("✅ Added **$amount tokens** to ${user.asMention}.").queue()
            }

            // ✅ Remove tokens
            "removetokens" -> {
                val user = event.getOption("user")!!.asUser
                val amount = event.getOption("amount")!!.asInt
                DiscordBot.removeTokens(user.idLong, amount)
                event.reply("✅ Removed **$amount tokens** from ${user.asMention}.").queue()
            }

            else -> event.reply("❌ Unknown command!").queue()
        }
    }
}

