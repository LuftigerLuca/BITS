package eu.luftiger.bits.backend.systemstatus.internal

import eu.luftiger.bits.backend.systemstatus.api.BackgroundJob
import eu.luftiger.bits.backend.systemstatus.api.BackgroundWork
import eu.luftiger.bits.backend.systemstatus.api.EventCounter
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The last run of each [BackgroundJob] since the backend started, and the events handed to the bot. */
@Component
class BackgroundWorkTracker(
    private val clock: Clock,
) : BackgroundWork,
    EventCounter {
    private val jobs = ConcurrentHashMap<BackgroundJob, JobState>()
    val events = UsageCounters(clock)

    override fun <T> run(
        job: BackgroundJob,
        trigger: String?,
        work: () -> T,
    ): T {
        val startedAt = clock.instant()
        jobs.compute(job) { _, state -> (state ?: JobState()).copy(runningSince = startedAt, runningTrigger = trigger) }
        var failure: String? = null
        try {
            return WareraCaller.within(WareraCaller.of(job), work)
        } catch (e: Exception) {
            failure = e.message ?: e::class.simpleName
            throw e
        } finally {
            val run = JobRun(trigger, startedAt, Duration.between(startedAt, clock.instant()), failure)
            jobs.compute(job) { _, state ->
                val previous = state ?: JobState()
                previous.copy(
                    runningSince = null,
                    runningTrigger = null,
                    lastRun = run,
                    runs = previous.runs + 1,
                    failures =
                        previous.failures + if (failure != null) 1 else 0,
                )
            }
        }
    }

    override fun published(type: String) = events.add(type)

    fun jobs(): Map<BackgroundJob, JobState> = jobs.toMap()

    data class JobState(
        val runningSince: Instant? = null,
        val runningTrigger: String? = null,
        val lastRun: JobRun? = null,
        val runs: Long = 0,
        val failures: Long = 0,
    )

    /** [failure] is `null` if the run went through. */
    data class JobRun(
        val trigger: String?,
        val startedAt: Instant,
        val duration: Duration,
        val failure: String?,
    )
}
