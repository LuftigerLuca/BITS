# 0007: Embassy threads are synced as a whole, the bot adds the hosts

Status: accepted

## Context

Diplomacy with other countries runs in private threads, one per country. Who belongs in a thread follows from WarEra
(who holds which office in that country), from Discord (who has a host role such as the own government) and from
admins (members added by hand, who need no link). Members must also leave once they lose their office, and the
ambassador role that lets them see the channel must follow. Only the backend may ask WarEra and only the bot may
change Discord (ADR 0002, 0003). WarEra allows 100 calls per minute, which the role sync mostly uses up at the full
hour.

## Decision

- **Every sync covers all threads.** The backend loads the governments of the countries with a thread only, works out
  the office holders among the linked members and the members added by hand, and publishes one `EmbassySyncEvent` with
  the target of every thread. It runs after every link and unlink, after every change on the dashboard page and every
  hour, at ten past so it stays out of the role sync's way. Governments come from the WarEra cache, so a sync right
  after another costs no calls; a handful of countries is cheap enough that a sync per member is not worth it.
- **The bot resolves the host roles.** The event names the host roles, not their members, since only the bot knows who
  holds a role right now.
- **The bot reports back what is in the threads.** Discord has no audit log for thread members, so the bot reports who
  is in each thread and why, and the dashboard shows that report.
- **Order in the bot:** read the thread members, grant the ambassador role, add and remove thread members, then take
  the role away. Discord only lets members into a private thread who can see its channel.
- **Missing data removes nobody.** If a government cannot be loaded, its thread loses nobody and its members keep the
  role. Members who may manage threads in the channel are never removed, so moderators can look in.
- **Admins pick threads from a list** of private threads the bot reports, active and archived, instead of pasting IDs.
  The bot can also create them, see ADR 0008.
- **The dashboard refuses what Discord would refuse.** The bot reports with each thread which roles may see its
  channel, and the backend rejects an ambassador or host role that may not, and a member added by hand who may not
  even with the ambassador role, so the admin learns it right away instead of from the system log. Overwrites for
  single members and roles that are denied explicitly are not considered; what Discord still refuses is reported with
  the reason.

## Consequences

- A member who loses an office stays in the thread until the next sync, at most an hour, or longer while WarEra is
  down. The cache can delay a new office holder by up to 10 minutes.
- Anybody added directly in Discord is removed by the next sync; adding by hand goes through the dashboard.
- When the ambassador role is changed, the members of the old role keep it; BITS only manages the role that is set.
- Unassigning a thread leaves its members as they are; BITS just stops managing it. Its ambassadors lose the role at
  the next sync if they are in no other thread.
- Listing archived private threads needs a request per text channel the bot may manage threads in, on start, every hour
  and after channel changes.
