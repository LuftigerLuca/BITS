package eu.luftiger.bits.backend.overview.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Hourly, so a day still gets its figures if the backend was down or the bot had not reported them at the first run. */
@Component
class OverviewSnapshotJob(
    private val historyService: OverviewHistoryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${bits.overview.snapshot-cron}")
    fun record() {
        if (historyService.recordSnapshot()) log.info("Recorded the key figures of the day")
    }
}
