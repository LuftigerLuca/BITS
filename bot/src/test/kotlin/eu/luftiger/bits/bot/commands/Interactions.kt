package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.LanguageBackend
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.Language
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/** Languages with what a member chose; nothing unless [stored] says so. */
fun languages(stored: Language? = null) =
    Languages(
        object : LanguageBackend {
            override suspend fun languagePreference(discordUserId: String) = stored

            override suspend fun setLanguagePreference(
                discordUserId: String,
                language: Language?,
            ) = Unit
        },
    )

/** Replies the bot sent through an interaction hook, in order. */
class Replies {
    val messages = CopyOnWriteArrayList<String>()

    /** The part of [messages] sent only to the user. */
    val ephemeralMessages = CopyOnWriteArrayList<String>()

    /** Rows of components sent with [messages]. */
    val components = CopyOnWriteArrayList<MessageTopLevelComponent>()

    /** Embeds the deferred reply was replaced with. */
    val embeds = CopyOnWriteArrayList<MessageEmbed>()

    @Volatile
    var deletedOriginal = false

    val hook: InteractionHook =
        mockk(relaxed = true) {
            every { sendMessage(any<String>()) } answers {
                val text = firstArg<String>()
                messages += text
                val action = mockk<WebhookMessageCreateAction<Message>>(relaxed = true)
                // Relaxed mocks cannot tell the generic type of what the builder methods return.
                every { action.addComponents(*anyVararg<MessageTopLevelComponent>()) } answers {
                    components += args.filterIsInstance<Array<*>>().single().filterIsInstance<MessageTopLevelComponent>()
                    action
                }
                every { action.setEphemeral(any()) } answers {
                    if (firstArg()) ephemeralMessages += text
                    action
                }
                action
            }
            every { editOriginalEmbeds(*anyVararg<MessageEmbed>()) } answers {
                embeds += args.filterIsInstance<Array<*>>().single().filterIsInstance<MessageEmbed>()
                mockk(relaxed = true)
            }
            every { deleteOriginal() } answers {
                deletedOriginal = true
                mockk<RestAction<Void>> { every { submit() } returns CompletableFuture.completedFuture(null) }
            }
        }
}

/** `/<name>` of user 42; the options are given as strings. */
fun slashCommand(
    name: String,
    replies: Replies,
    options: Map<String, String> = emptyMap(),
): SlashCommandInteractionEvent {
    val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
    every { event.name } returns name
    every { event.user.id } returns "42"
    every { event.userLocale } returns DiscordLocale.GERMAN
    every { event.hook } returns replies.hook
    every { event.getOption(any<String>()) } answers {
        options[firstArg()]?.let { value -> mockk { every { asString } returns value } }
    }
    return event
}

/** Click of user 42 on the button [componentId]. */
fun buttonClick(
    componentId: String,
    replies: Replies,
): ButtonInteractionEvent {
    val event = mockk<ButtonInteractionEvent>(relaxed = true)
    every { event.componentId } returns componentId
    every { event.user.id } returns "42"
    every { event.userLocale } returns DiscordLocale.GERMAN
    every { event.member!!.effectiveName } returns "Luca"
    every { event.hook } returns replies.hook
    return event
}

/** Submit of the modal [modalId] by user 42 with [values] by the ID of their input. */
fun modalSubmit(
    modalId: String,
    replies: Replies,
    values: Map<String, String> = emptyMap(),
): ModalInteractionEvent {
    val event = mockk<ModalInteractionEvent>(relaxed = true)
    every { event.modalId } returns modalId
    every { event.user.id } returns "42"
    every { event.userLocale } returns DiscordLocale.GERMAN
    every { event.hook } returns replies.hook
    every { event.getValue(any<String>()) } answers {
        values[firstArg()]?.let { value -> mockk { every { asString } returns value } }
    }
    return event
}
