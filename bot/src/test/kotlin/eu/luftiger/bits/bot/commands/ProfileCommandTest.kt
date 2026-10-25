package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.ProfileDetail
import eu.luftiger.bits.shared.ProfileParty
import eu.luftiger.bits.shared.ProfileResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileCommandTest {
    private val found =
        ProfileResponse.Found(
            wareraUserId = "w1",
            wareraUsername = "_Luftiger_",
            level = 30,
            militaryRank = 12,
            countryName = "Germany",
            militaryUnitName = null,
            profileUrl = "https://app.warera.io/user/w1",
            party = ProfileParty("_Deutsche Partei_", leader = false),
            offices = listOf("Präsident", "Kongressmitglied"),
            companyCount = 3,
            managedRoleIds = listOf("r-high", "r-mid", "r-low"),
        )

    @Test
    fun `embed shows the profile with a link to WarEra`() {
        val embed =
            assertIs<ProfileCommand.Answer.Profile>(
                ProfileCommand.answer(found, own = true, mention = "<@42>", roleIds = setOf("r-low", "other", "r-high")),
            ).embed

        assertEquals("\\_Luftiger\\_", embed.title)
        assertEquals("https://app.warera.io/user/w1", embed.url)
        assertEquals("WarEra-Profil von <@42>", embed.description)
        assertEquals(
            listOf(
                "Level" to "30",
                "Militärrang" to "12",
                "Staatsbürgerschaft" to "Germany",
                "Military Unit" to "keine",
                "Partei" to "\\_Deutsche Partei\\_",
                "Regierungsamt" to "Präsident, Kongressmitglied",
                "Firmen" to "3",
                "Rollen" to "<@&r-high> <@&r-low>",
            ),
            embed.fields.map { it.name to it.value },
        )
    }

    @Test
    fun `embed fills in what is missing`() {
        val nothing = found.copy(party = null, offices = emptyList(), companyCount = 0)
        val leader = found.copy(party = ProfileParty("Partei", leader = true))

        assertEquals(
            listOf("Partei" to "parteilos", "Regierungsamt" to "keins", "Firmen" to "0", "Rollen" to "keine"),
            fields(nothing, roleIds = setOf("other")).drop(4),
        )
        assertEquals("Partei" to "Partei (Vorsitz)", fields(leader, roleIds = emptySet())[4])
    }

    @Test
    fun `embed leaves out the roles of someone who is not on the server`() {
        assertEquals(listOf("Partei", "Regierungsamt", "Firmen"), fields(found, roleIds = null).drop(4).map { it.first })
    }

    @Test
    fun `embed marks details WarEra could not deliver`() {
        val partial = found.copy(unavailable = setOf(ProfileDetail.PARTY, ProfileDetail.OFFICES, ProfileDetail.COMPANIES))

        assertEquals(
            listOf("Partei" to "nicht verfügbar", "Regierungsamt" to "nicht verfügbar", "Firmen" to "nicht verfügbar"),
            fields(partial, roleIds = null).drop(4),
        )
    }

    @Test
    fun `missing link points the user to verify, but not for someone else`() {
        val own = ProfileCommand.answer(ProfileResponse.NotLinked, own = true, mention = "<@42>", roleIds = null)
        val other = ProfileCommand.answer(ProfileResponse.NotLinked, own = false, mention = "<@7>", roleIds = null)

        assertEquals(
            ProfileCommand.Answer.Hint(
                "Dein Discord-Account ist mit keinem WarEra-Account verknüpft. Mit `/verify` verknüpfst du ihn.",
            ),
            own,
        )
        assertEquals(ProfileCommand.Answer.Hint("<@7> hat keinen WarEra-Account verknüpft."), other)
    }

    @Test
    fun `command shows the profile of the given member in public`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile("7") } returns found
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(
                profileCommand(replies, target = member("7"), targetRoleIds = listOf("r-mid")),
            )
            runCurrent()

            val embed = replies.embeds.single()
            assertEquals("WarEra-Profil von <@7>", embed.description)
            assertEquals("<@&r-mid>", embed.fields.single { it.name == "Rollen" }.value)
            assertFalse(replies.deletedOriginal)
            assertEquals(emptyList(), replies.messages)
        }

    @Test
    fun `command without a member asks for the own profile and answers hints privately`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile("42") } returns ProfileResponse.NotLinked
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies))
            runCurrent()

            assertTrue(replies.deletedOriginal)
            assertEquals(1, replies.ephemeralMessages.size)
            assertEquals(replies.messages, replies.ephemeralMessages)
            assertEquals(emptyList(), replies.embeds)
        }

    @Test
    fun `command says when WarEra cannot be reached`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile(any()) } throws WareraUnavailableException(RuntimeException("503"))
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies))
            runCurrent()

            assertEquals(listOf(ProfileCommand.WARERA_UNAVAILABLE_MESSAGE), replies.ephemeralMessages)
        }

    @Test
    fun `command says BITS is not reachable when the backend fails`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile(any()) } throws IllegalStateException("connection refused")
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies))
            runCurrent()

            assertEquals(listOf(BITS_UNAVAILABLE_MESSAGE), replies.ephemeralMessages)
        }

    @Test
    fun `command shows the own roles without a member`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile("42") } returns found
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies, ownRoleIds = listOf("r-low")))
            runCurrent()

            assertEquals(
                "<@&r-low>",
                replies.embeds
                    .single()
                    .fields
                    .single { it.name == "Rollen" }
                    .value,
            )
        }

    @Test
    fun `command leaves out the roles of a member who left the server`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.profile("7") } returns found
            val replies = Replies()

            ProfileCommand(backend, backgroundScope).onSlashCommandInteraction(profileCommand(replies, target = member("7")))
            runCurrent()

            assertEquals(
                null,
                replies.embeds
                    .single()
                    .fields
                    .firstOrNull { it.name == "Rollen" },
            )
        }

    private fun fields(
        profile: ProfileResponse.Found,
        roleIds: Set<String>?,
    ): List<Pair<String?, String?>> =
        assertIs<ProfileCommand.Answer.Profile>(ProfileCommand.answer(profile, own = true, mention = "<@42>", roleIds = roleIds))
            .embed.fields
            .map { it.name to it.value }

    private fun withRoles(roleIds: List<String>): Member =
        mockk {
            every { roles } returns roleIds.map { id -> mockk<Role> { every { this@mockk.id } returns id } }
        }

    private fun member(id: String): User =
        mockk {
            every { this@mockk.id } returns id
            every { asMention } returns "<@$id>"
        }

    /**
     * `/profil` of user 42 with the roles [ownRoleIds], for [target] if given, who has the roles [targetRoleIds] or is
     * not on the server if they are `null`.
     */
    private fun profileCommand(
        replies: Replies,
        target: User? = null,
        targetRoleIds: List<String>? = null,
        ownRoleIds: List<String> = emptyList(),
    ): SlashCommandInteractionEvent {
        val event = slashCommand(ProfileCommand.NAME, replies)
        val self = member("42")
        every { event.user } returns self
        val ownMember = withRoles(ownRoleIds)
        every { event.member } returns ownMember
        every { event.getOption("nutzer") } returns
            target?.let { user ->
                mockk {
                    every { asUser } returns user
                    every { asMember } returns targetRoleIds?.let(::withRoles)
                }
            }
        return event
    }
}
