package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.PartyResult
import kotlin.math.roundToInt

/** What is kept of an evaluated vote: the counts per party and the names of the non-voters, no single ballot. */
class VoteResult(
    val question: String,
    /** Parties by size, those in no party last. */
    val parties: List<PartyResult>,
    val nonVoters: List<String>,
) {
    val yes = parties.sumOf { it.yes }
    val no = parties.sumOf { it.no }
    val abstain = parties.sumOf { it.abstain }
    val voted = yes + no + abstain
    val eligible = parties.sumOf { it.eligible }
}

/** The share of [part] in [whole], rounded to a whole percent; 0 if [whole] is 0. */
fun percent(
    part: Int,
    whole: Int,
): Int = if (whole == 0) 0 else (part * 100.0 / whole).roundToInt()
