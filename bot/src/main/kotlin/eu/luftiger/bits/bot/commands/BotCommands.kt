package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.votes.VoteCommand
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

/** Registered at every start, which replaces whatever the guild had before, such as commands that were renamed. */
val BOT_COMMANDS: List<SlashCommandData> =
    listOf(
        PingCommand.data,
        VerifyCommand.data,
        UnverifyCommand.data,
        NicknameCommand.data,
        ProfileCommand.data,
        RolesCommand.data,
        LanguageCommand.data,
        VoteCommand.data,
    )
