# Failure modes

What happens when one part of BITS or a service it needs is down, and how it recovers. The behavior is intended; a
deviation from it is a bug.

| Down           | Members in Discord                                                                                                               | Dashboard                                                                                                                                                                             | Role sync                                                                                                                | Recovery                                                                                                                                                                        |
|----------------|----------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **WarEra API** | `/verify`, `/profil` and `/rollen` say WarEra is not reachable; an open code stays valid. If only the party, government or company lookup of `/profil` fails, the card shows the rest and "nicht verfügbar" there. `/ping` shows WarEra as not reachable. | Rules that need a lookup (country, military unit) and the country of the start page cannot be saved (503); the start page shows the citizens as not available; "Mein Account" shows the link but cannot check the roles. Everything else works. | Members whose data cannot be loaded keep their managed roles and nickname; a government that cannot be loaded keeps the office roles, unless another condition of the same rule fails. | The next sync with fresh data, at the latest after an hour.                                                                                                                     |
| **Discord**    | Commands do not arrive. A link made just before does not get its roles yet.                                                      | Discord login fails; the admin account and existing sessions work.                                                                                                                    | Failed role changes are logged and skipped, the event is still acknowledged.                                             | JDA reconnects by itself; the hourly sync applies what was skipped.                                                                                                             |
| **Redis**      | Commands work. Results of verification checks arrive once Redis is back.                                                         | Every request is rejected and nobody can log in, the admin account included (see ADR 0004).                                                                                           | Syncs are computed, their events wait in Postgres.                                                                       | The backend writes waiting events every minute; the bot reconnects with backoff.                                                                                                |
| **Postgres**   | Commands answer that BITS is not reachable.                                                                                      | Pages show an error instead of their data.                                                                                                                                            | Nothing is synced; the backend's health check fails.                                                                     | Works again as soon as Postgres is back; nothing was written meanwhile.                                                                                                         |
| **Backend**    | Commands answer that BITS is not reachable.                                                                                      | The dashboard says it cannot reach BITS and offers to try again.                                                                                                                      | Nothing is synced. The bot sends its role report and member list again with backoff.                                     | Waiting events go out on start; the hourly sync follows at the full hour. The start page shows the member figures as unknown until the bot's next member list, at most an hour. The rule preview needs that list too, and counts linked members as unknown until the next hourly sync. |
| **Bot**        | No commands, no role changes, no direct messages. Members who leave keep their link for now.                                     | Works. Rule changes are saved, their syncs wait in the stream.                                                                                                                        | Events wait in the stream, at most a week.                                                                               | The bot handles what is pending on start; newer syncs win over older ones. Its member list deletes the links of everybody who left meanwhile.                                   |

Failures of single calls are handled where they happen: a WarEra call is retried with backoff before it counts as
down, a role or nickname change that Discord refuses is skipped, and an event the bot cannot handle is retried and then moved to
`bits:events:dead`.

A vote takes no ballots after its end even when a part is down; only its evaluation waits. A vote that has ended stays "Wird
ausgewertet" until the bot has reported who has the role and WarEra has named the parties: the backend asks the bot
again after 30 minutes without an answer, retries WarEra every 5 minutes and records the first failure in the system
log, and publishes the result again until the bot confirms that it posted it. Clicks on a vote while the backend is
down are answered with the usual error; the participation in the message catches up with the next click. A vote's
threshold is checked whenever the bot reports the participation, i.e. after a click; while the bot or the backend is
down, it ends only with the next report or at its end date. If the
vote's channel was deleted, the result is only in the dashboard. Without WarEra, admins cannot list the parties to pick their
colours; result images are still drawn with the colours saved, and the preview uses invented parties.

The embassy sync follows the role sync. Without WarEra, a thread whose government cannot be loaded loses nobody,
its members keep the ambassador role, and the system log records it once per sync; members added by hand and the
members of the host roles are still added. Without the bot, the syncs wait in the stream and the dashboard shows the
members of the last report. A thread that was deleted, or in whose channel the bot may not manage threads, is recorded
in the system log and skipped, and a change Discord refuses is logged and skipped, for a member who may not see the
channel with that reason; the other threads are synced, and
the next sync tries again, at the latest after an hour. Without the backend, the bot keeps reporting its private threads
with backoff, so new threads show up for picking once it is back.

Admins see most of this in the system log of the dashboard: members and governments whose WarEra data is missing,
role and nickname changes Discord refused or the bot could not make, events moved to `bits:events:dead`, and events that wait more than 10 minutes for Redis.
A sync the bot does not report back on within 15 minutes shows as "Keine Rückmeldung vom Bot", which is what a bot or
Redis outage looks like there. Without Postgres or the backend, nothing is recorded; the bot keeps its reports for
about an hour.
