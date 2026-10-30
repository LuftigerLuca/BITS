package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.BackendTimeoutException
import eu.luftiger.bits.bot.backend.TooManyRequestsException
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.ProfileDetail
import eu.luftiger.bits.shared.ProfileResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory

/**
 * `/profil [nutzer]` shows the WarEra profile of the user's own linked account, or of the given member's, in the
 * channel. Hints, such as a missing link or an unreachable WarEra, only the user sees.
 *
 * WarEra may be slow, so the reply is deferred, and in public, since that is the usual outcome. A hint deletes the
 * public "thinking" reply and follows up privately instead.
 */
class ProfileCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        val option = event.getOption(OPTION_USER)
        val target = option?.asUser ?: event.user
        val own = target.id == event.user.id
        // `null` if the member is not (or no longer) on the server.
        val roleIds = (if (option == null) event.member else option.asMember)?.roles?.mapTo(HashSet()) { it.id }
        event.deferReply().queue()
        scope.launch {
            val answer =
                try {
                    answer(backend.profile(target.id, event.user.id), own, target.asMention, roleIds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Answer.Hint(failureMessage(e))
                }
            when (answer) {
                is Answer.Profile -> event.hook.editOriginalEmbeds(answer.embed).queue()
                is Answer.Hint -> hint(event.hook, answer.text)
            }
        }
    }

    private suspend fun hint(
        hook: InteractionHook,
        text: String,
    ) {
        // Before the follow-up, which would otherwise replace the deferred reply and stay public.
        try {
            hook.deleteOriginal().submit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not delete the deferred reply of /{}", NAME, e)
        }
        hook.sendMessage(text).setEphemeral(true).queue()
    }

    private fun failureMessage(e: Exception): String =
        when (e) {
            is WareraUnavailableException -> WARERA_UNAVAILABLE_MESSAGE
            is TooManyRequestsException -> tooManyRequestsMessage(e)
            is BackendTimeoutException -> "Das dauert gerade länger als erwartet. Versuche es gleich noch einmal."
            else -> {
                log.error("Profile call to backend failed", e)
                BITS_UNAVAILABLE_MESSAGE
            }
        }

    internal sealed interface Answer {
        data class Profile(
            val embed: MessageEmbed,
        ) : Answer

        data class Hint(
            val text: String,
        ) : Answer
    }

    companion object {
        const val NAME = "profil"
        private const val OPTION_USER = "nutzer"

        internal const val WARERA_UNAVAILABLE_MESSAGE = "WarEra ist gerade nicht erreichbar. Versuche es gleich noch einmal."

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Zeigt das WarEra-Profil eines verknüpften Accounts")
                .addOption(OptionType.USER, OPTION_USER, "Wessen Profil; ohne Angabe dein eigenes", false)

        /**
         * [own] if the user asked for their own profile; [mention] is the member whose profile was asked for, [roleIds]
         * their Discord roles, `null` if they are not on the server.
         */
        internal fun answer(
            response: ProfileResponse,
            own: Boolean,
            mention: String,
            roleIds: Set<String>?,
        ): Answer =
            when (response) {
                is ProfileResponse.Found -> Answer.Profile(embed(response, mention, roleIds))
                ProfileResponse.NotLinked ->
                    Answer.Hint(
                        if (own) {
                            "Dein Discord-Account ist mit keinem WarEra-Account verknüpft. Mit `/${VerifyCommand.NAME}` verknüpfst du ihn."
                        } else {
                            "$mention hat keinen WarEra-Account verknüpft."
                        },
                    )
                ProfileResponse.WareraUserNotFound ->
                    Answer.Hint(
                        if (own) {
                            "Deinen verknüpften WarEra-Account gibt es nicht mehr. Mit `/${VerifyCommand.NAME}` verknüpfst du einen neuen."
                        } else {
                            "Den WarEra-Account, mit dem $mention verknüpft ist, gibt es nicht mehr."
                        },
                    )
            }

        private fun embed(
            profile: ProfileResponse.Found,
            mention: String,
            roleIds: Set<String>?,
        ): MessageEmbed {
            fun detail(
                detail: ProfileDetail,
                value: () -> String,
            ) = if (detail in profile.unavailable) "nicht verfügbar" else value()
            val party =
                detail(ProfileDetail.PARTY) {
                    profile.party?.let { MarkdownSanitizer.escape(it.name) + if (it.leader) " (Vorsitz)" else "" } ?: "parteilos"
                }
            val offices = detail(ProfileDetail.OFFICES) { profile.offices.joinToString(", ").ifEmpty { "keins" } }
            val companies = detail(ProfileDetail.COMPANIES) { profile.companyCount.toString() }
            return EmbedBuilder()
                .setTitle(MarkdownSanitizer.escape(profile.wareraUsername), profile.profileUrl)
                // Mentions in embeds do not notify anyone, neither of users nor of roles.
                .setDescription("WarEra-Profil von $mention")
                .addField("Level", profile.level.toString(), true)
                .addField("Militärrang", profile.militaryRank.toString(), true)
                .addField("Staatsbürgerschaft", profile.countryName?.let(MarkdownSanitizer::escape) ?: "unbekannt", true)
                .addField("Military Unit", profile.militaryUnitName?.let(MarkdownSanitizer::escape) ?: "keine", true)
                .addField("Partei", party, true)
                .addField("Regierungsamt", offices, true)
                .addField("Firmen", companies, true)
                .apply {
                    if (roleIds != null) {
                        val roles = profile.managedRoleIds.filter { it in roleIds }
                        addField("Rollen", roles.joinToString(" ") { "<@&$it>" }.ifEmpty { "keine" }, false)
                    }
                }.build()
        }
    }
}
