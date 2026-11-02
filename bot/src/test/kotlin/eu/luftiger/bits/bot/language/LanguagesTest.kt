package eu.luftiger.bits.bot.language

import eu.luftiger.bits.bot.backend.LanguageBackend
import eu.luftiger.bits.shared.Language
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LanguagesTest {
    private val backend = mockk<LanguageBackend>(relaxed = true) { coEvery { languagePreference(any()) } returns null }
    private var time = 1_000_000L
    private val languages = Languages(backend) { time }

    private fun interaction(locale: DiscordLocale) =
        mockk<Interaction> {
            every { user.id } returns "42"
            every { userLocale } returns locale
        }

    @Test
    fun `the stored choice beats the client`() =
        runTest {
            coEvery { backend.languagePreference("42") } returns Language.ENGLISH

            assertEquals(Language.ENGLISH, languages.of(interaction(DiscordLocale.GERMAN)))
        }

    @Test
    fun `without a choice the client decides, every German variant counts and an unknown client is German`() =
        runTest {
            coEvery { backend.languagePreference("42") } returns null

            assertEquals(Language.GERMAN, languages.of(interaction(DiscordLocale.GERMAN)))
            assertEquals(Language.ENGLISH, languages.of(interaction(DiscordLocale.FRENCH)))
            assertEquals(Language.ENGLISH, languages.of(interaction(DiscordLocale.ENGLISH_UK)))
            assertEquals(Language.GERMAN, languages.of(interaction(DiscordLocale.UNKNOWN)))
        }

    @Test
    fun `direct messages are German without a choice`() =
        runTest {
            assertEquals(Language.GERMAN, languages.ofDirectMessage("42"))
        }

    @Test
    fun `direct messages follow the choice`() =
        runTest {
            coEvery { backend.languagePreference("42") } returns Language.ENGLISH

            assertEquals(Language.ENGLISH, languages.ofDirectMessage("42"))
        }

    @Test
    fun `a failing backend leaves the usual order without a choice`() =
        runTest {
            coEvery { backend.languagePreference("42") } throws IllegalStateException("connection refused")

            assertEquals(Language.ENGLISH, languages.of(interaction(DiscordLocale.ENGLISH_US)))
            assertEquals(Language.GERMAN, languages.ofDirectMessage("42"))
        }

    @Test
    fun `a backend that hangs does not hold up the answer`() =
        runTest {
            coEvery { backend.languagePreference("42") } coAnswers { awaitCancellation() }

            assertEquals(Language.GERMAN, languages.ofDirectMessage("42"))
        }

    @Test
    fun `answers are kept for a while, also that there is no choice, and asked anew afterwards`() =
        runTest {
            coEvery { backend.languagePreference("42") } returns null

            languages.ofDirectMessage("42")
            languages.ofDirectMessage("42")
            coVerify(exactly = 1) { backend.languagePreference("42") }

            time += 600_000
            languages.ofDirectMessage("42")
            coVerify(exactly = 2) { backend.languagePreference("42") }
        }

    @Test
    fun `a failure is not kept`() =
        runTest {
            coEvery { backend.languagePreference("42") } throws IllegalStateException("connection refused")
            languages.ofDirectMessage("42")

            coEvery { backend.languagePreference("42") } returns Language.ENGLISH

            assertEquals(Language.ENGLISH, languages.ofDirectMessage("42"))
        }

    @Test
    fun `a new choice counts at once and is known without asking`() =
        runTest {
            coEvery { backend.languagePreference("42") } returns null
            languages.ofDirectMessage("42")

            languages.set("42", Language.ENGLISH)

            assertEquals(Language.ENGLISH, languages.cachedOf(interaction(DiscordLocale.GERMAN)))
            assertEquals(Language.ENGLISH, languages.ofDirectMessage("42"))
            coVerify(exactly = 1) { backend.languagePreference("42") }
        }

    @Test
    fun `without a cached answer the client decides without asking`() {
        assertEquals(Language.ENGLISH, languages.cachedOf(interaction(DiscordLocale.SPANISH)))
        coVerify(exactly = 0) { backend.languagePreference(any()) }
    }

    @Test
    fun `a choice the backend does not take is not kept`() =
        runTest {
            coEvery { backend.setLanguagePreference("42", Language.ENGLISH) } throws IllegalStateException("connection refused")

            assertFailsWith<IllegalStateException> { languages.set("42", Language.ENGLISH) }

            assertEquals(Language.GERMAN, languages.cachedOf(interaction(DiscordLocale.GERMAN)))
        }
}
