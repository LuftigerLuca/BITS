# Failure modes

What happens when one part of BITS or a service it needs is down, and how it recovers. The behavior is intended; a
deviation from it is a bug.

| Down           | Members in Discord                                                                                                               | Dashboard                                                                                                                                                                             | Role sync                                                                                                                | Recovery                                                                                                                                                                        |
|----------------|----------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **WarEra API** | `/verify`, `/profil` and `/rollen` say WarEra is not reachable; an open code stays valid. If only the party, government or company lookup of `/profil` fails, the card shows the rest and "nicht verfügbar" there. `/ping` shows WarEra as not reachable. | Rules that need a lookup (country, military unit) and the country of the start page cannot be saved (503), and military units cannot be searched or assigned to a DSK; the start page shows the citizens as not available; "Mein Account" and "Mitglieder" show the link but cannot check the roles. Everything else works. | Members whose data cannot be loaded keep their managed roles and nickname; a government, the parties of a country, a military unit or the companies of a member that cannot be loaded keep the roles that depend on them, unless another block of the rule is met or another condition of the same block fails. | The next sync with fresh data, at the latest after an hour.                                                                                                                     |
| **Discord**    | Commands do not arrive. A link made just before does not get its roles yet.                                                      | Discord login fails; the admin account and existing sessions work.                                                                                                                    | Failed role changes are logged and skipped, the event is still acknowledged.                                             | JDA reconnects by itself; the hourly sync applies what was skipped.                                                                                                             |
| **Redis**      | Commands work. Results of verification checks arrive once Redis is back.                                                         | Every request is rejected and nobody can log in, the admin account included (see ADR 0004).                                                                                           | Syncs are computed, their events wait in Postgres.                                                                       | The backend writes waiting events every minute; the bot reconnects with backoff.                                                                                                |
| **Postgres**   | Commands answer that BITS is not reachable.                                                                                      | Pages show an error instead of their data.                                                                                                                                            | Nothing is synced; the backend's health check fails.                                                                     | Works again as soon as Postgres is back; nothing was written meanwhile.                                                                                                         |
| **Backend**    | Commands answer that BITS is not reachable.                                                                                      | The dashboard says it cannot reach BITS and offers to try again.                                                                                                                      | Nothing is synced. The bot sends its role report and member list again with backoff.                                     | Waiting events go out on start; the hourly sync follows at the full hour. The start page shows the member figures as unknown until the bot's next member list, at most an hour. The rule preview needs that list too, and counts linked members as unknown until the next hourly sync. Until then, "Mitglieder" finds only linked members and knows no Discord names or roles. |
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

A field WarEra stops sending or renames looks like a real change, not like an outage. A sync of all members that would
take a role from at least 10 members and more than a quarter of its holders is held back by the bot, unless an admin
just changed the role's rule or switched the limit off under "Rollen". While it is off, such a change takes the role
from everybody.

The roles of DSKs need nothing from WarEra but the member: a member who cannot be loaded keeps the role of their DSK
as it is. Assigned military units are not looked up again, so their names in the dashboard and in `/rollen` are those
from the time they were assigned.

The status roles need nothing from WarEra: whoever has a link is verified, so they work while WarEra is down. A
member who joins while the backend is down does not get the role for unverified members until the next hourly sync,
since the bot reports the join only once and the backend starts the sync of the member; the same holds while Postgres
or Redis is down, or while the bot misses the join. While the bot is down, the sync waits in the stream like any
other. While the verification is off, BITS leaves the status roles as they are.

The embassy sync follows the role sync. Without WarEra, a thread whose government cannot be loaded loses nobody,
its members keep the ambassador role, and the system log records it once per sync; members added by hand and the
members of the host roles are still added. Without the bot, the syncs wait in the stream and the dashboard shows the
members of the last report. A thread that was deleted, or in whose channel the bot may not manage threads, is recorded
in the system log and skipped, and a change Discord refuses is logged and skipped, for a member who may not see the
channel with that reason; the other threads are synced, and
the next sync tries again, at the latest after an hour. Without the backend, the bot keeps reporting its private threads
with backoff, so new threads show up for picking once it is back. An embassy thread the bot does not create within a
minute, e.g. because the bot or Redis is down, fails with that reason on the dashboard and in the system log, and the
country is free again; the bot drops the request when it gets it later, and deletes a thread it created too late.
Without WarEra, no thread is created on its own; the next sync tries again. Without the role sync, only members who
link get their country a thread, since their country is otherwise known only from the role sync's hourly load.
Renaming embassy threads waits in the stream while the bot is down. A thread that is gone, or in whose channel the bot
may not manage threads, or whose rename Discord refuses, keeps its name and is recorded in the system log; the others
are renamed. While the backend is down, the bot sends those failures again with backoff for about an hour.

Which features an admin switched off under "Verwaltung" the bot asks the backend every 30 seconds. Until its first
answer after a start everything is on; while the backend is down, the bot keeps the last answer, and the commands fail
at the backend anyway. Account changes while the role sync or the embassies are off are not synced on their own;
switching them on again syncs everybody.

A large role sync, e.g. the first one after status roles are set on a large guild, can take a while, since Discord
throttles role changes. Meanwhile further role syncs wait behind it, but the other events, like the result of a
verification check, do not (ADR 0009).

The bot's status and activity reach it as an event; while the bot is down, the event waits in the stream, and the bot
also asks the backend for them on start, with backoff until it answers. Until then it shows as online without activity.

Admins see most of this in the system log of the dashboard: members and governments whose WarEra data is missing,
role and nickname changes Discord refused or the bot could not make, events moved to `bits:events:dead`, and events that wait more than 10 minutes for Redis.
A sync the bot does not report back on within 15 minutes shows as "Keine Rückmeldung vom Bot", which is what a bot or
Redis outage looks like there. Without Postgres or the backend, nothing is recorded; the bot keeps its reports for
about an hour.

The page "System-Status" shows the state itself. Postgres and Redis are checked every 15 seconds on a thread of
their own, so the page loads at once while Postgres is down and shows the last sizes it read. WarEra is not asked for
the page: its state follows from the last real call, so while BITS makes none, the page only shows when the last
contact was. The bot sends a heartbeat every 30 seconds and counts as down after 90 seconds without one; a heartbeat
the backend misses loses nothing, since its counts run since the bot started. All counts live in the memory of the
backend and start from zero after a restart (ADR 0010). While Redis or the backend is down, the dashboard does not
open at all and cannot show the page; it says that BITS cannot be reached.
