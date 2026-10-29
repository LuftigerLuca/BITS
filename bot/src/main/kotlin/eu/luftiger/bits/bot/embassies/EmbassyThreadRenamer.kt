package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.shared.EmbassyThreadRename
import eu.luftiger.bits.shared.EmbassyThreadRenameEvent
import eu.luftiger.bits.shared.EmbassyThreadRenameReport
import eu.luftiger.bits.shared.FailedThreadRename
import eu.luftiger.bits.shared.ThreadRenameFailure
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/**
 * Renames the embassy threads an admin asked for, one after the other, so a thread that fails holds up none of the
 * others. An archived thread is skipped, since renaming would unarchive it, and so is one that has the name already.
 * Only what failed goes to [report], for the backend's system log; the new names reach the backend with the next
 * report of the threads, which the rename itself sets off.
 */
class EmbassyThreadRenamer(
    private val guild: EmbassyGuild,
    private val report: (EmbassyThreadRenameReport) -> Unit,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun handle(event: EmbassyThreadRenameEvent) {
        val failures = event.threads.mapNotNull { rename(it) }
        log.info("Renamed embassy threads: {} asked for, {} failed", event.threads.size, failures.size)
        if (failures.isNotEmpty()) report(EmbassyThreadRenameReport(failures))
    }

    /** `null` if renamed or skipped. */
    private suspend fun rename(target: EmbassyThreadRename): FailedThreadRename? {
        fun failed(
            name: String?,
            failure: ThreadRenameFailure,
        ): FailedThreadRename {
            log.warn("Embassy thread {} of {} not renamed: {}", target.threadId, target.countryName, failure)
            return FailedThreadRename(target.threadId, name, target.countryName, failure)
        }
        val thread =
            try {
                when (val lookup = guild.thread(target.threadId, target.parentChannelId)) {
                    is ThreadLookup.Found -> lookup.thread
                    ThreadLookup.Missing -> return failed(null, ThreadRenameFailure.MISSING)
                    ThreadLookup.NoPermission -> return failed(null, ThreadRenameFailure.NO_PERMISSION)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Could not look up embassy thread {}", target.threadId, e)
                return failed(null, ThreadRenameFailure.FAILED)
            }
        if (thread.archived || thread.name == target.name) return null
        if (!thread.manageable) return failed(thread.name, ThreadRenameFailure.NO_PERMISSION)
        return try {
            thread.rename(target.name, "BITS: Botschaft von ${target.countryName} nach der Vorlage umbenannt")?.let {
                failed(thread.name, it)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Discord refused to rename embassy thread {}", target.threadId, e)
            failed(thread.name, ThreadRenameFailure.FAILED)
        }
    }
}
