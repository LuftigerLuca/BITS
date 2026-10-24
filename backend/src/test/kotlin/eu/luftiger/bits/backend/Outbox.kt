package eu.luftiger.bits.backend

import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.fail

/**
 * Events reach Redis after the commit, on another thread. Waits until the event publication registry is empty, i.e.
 * every event published so far is in the stream (completion-mode delete).
 */
fun JdbcTemplate.awaitOutbox() {
    repeat(100) {
        if (queryForObject("SELECT COUNT(*) FROM event_publication", Int::class.java) == 0) return
        Thread.sleep(50)
    }
    fail("events still waiting in event_publication: ${queryForList("SELECT event_type, listener_id FROM event_publication")}")
}
