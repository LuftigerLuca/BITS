package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteState
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoteMessagesTest {
    private val vote =
        VoteDto(7, "Neue *Steuern*?", "r-congress", "c-votes", endsAt = 1_767_268_800_000, state = VoteState.OPEN, messageId = "m1")

    @Test
    fun `durations are read in minutes, hours and days, or as open`() {
        assertEquals(VoteEnd.After(Duration.ofHours(48)), VoteMessages.parseDuration("48h"))
        assertEquals(VoteEnd.After(Duration.ofDays(3)), VoteMessages.parseDuration(" 3D "))
        assertEquals(VoteEnd.After(Duration.ofMinutes(90)), VoteMessages.parseDuration("90 m"))
        assertEquals(VoteEnd.Open, VoteMessages.parseDuration("Offen"))
        assertNull(VoteMessages.parseDuration("0m"))
        assertNull(VoteMessages.parseDuration("91d"))
        assertNull(VoteMessages.parseDuration("morgen"))
    }

    @Test
    fun `the buttons carry vote, choice and role`() {
        val ids =
            VoteMessages
                .components(vote)
                .single()
                .buttons
                .map { it.customId!! }

        assertEquals(
            listOf(
                VoteButton(7, VoteChoice.YES, "r-congress"),
                VoteButton(7, VoteChoice.NO, "r-congress"),
                VoteButton(7, VoteChoice.ABSTAIN, "r-congress"),
            ),
            ids.map { VoteMessages.parseButton(it) },
        )
        assertNull(VoteMessages.parseButton("nickname:on"))
        assertNull(VoteMessages.parseButton("vote:x:YES:r"))
    }

    @Test
    fun `an open vote shows the participation and its end, a closed one has no buttons`() {
        val open = VoteMessages.embed(vote, ParticipationResponse(voted = 3, eligible = 7))

        assertEquals("**Neue \\*Steuern\\*?**", open.description)
        assertEquals("3 von 7 haben abgestimmt", open.fields.single { it.name == "Beteiligung" }.value)
        assertEquals("<t:1767268800:f> (<t:1767268800:R>)", open.fields.single { it.name == "Ende" }.value)
        assertTrue(VoteMessages.components(vote.copy(state = VoteState.COUNTING)).isEmpty())
    }

    @Test
    fun `a vote that ends at a participation says so, also without end date`() {
        val withDate = VoteMessages.embed(vote.copy(autoClosePercent = 60), ParticipationResponse(3, 7))
        val withoutDate = VoteMessages.embed(vote.copy(endsAt = null, autoClosePercent = 60), ParticipationResponse(3, 7))

        assertEquals(
            "<t:1767268800:f> (<t:1767268800:R>)\nEndet automatisch bei 60 % Beteiligung, falls die früher erreicht ist",
            withDate.fields.single { it.name == "Ende" }.value,
        )
        assertEquals(
            "Endet automatisch bei 60 % Beteiligung, sonst wenn sie jemand beendet",
            withoutDate.fields.single { it.name == "Ende" }.value,
        )
    }

    @Test
    fun `a vote ended by its participation says it ended automatically`() {
        val ended =
            VoteMessages.embed(
                vote.copy(state = VoteState.COUNTING, autoClosePercent = 60, endedByParticipation = true),
                ParticipationResponse(5, 7),
            )

        assertEquals("Abstimmung beendet", ended.title)
        assertEquals(
            "automatisch beendet bei 60 % Beteiligung, die Auswertung erscheint in diesem Kanal",
            ended.fields.single { it.name == "Ende" }.value,
        )
    }

    @Test
    fun `a cancelled vote shows no participation`() {
        val cancelled = VoteMessages.embed(vote.copy(state = VoteState.CANCELLED), ParticipationResponse(3, 7))

        assertEquals("Abstimmung abgebrochen", cancelled.title)
        assertTrue(cancelled.fields.none { it.name == "Beteiligung" })
    }
}
