# 0008: The bot creates embassy threads, also on its own when a foreign office holder links

Status: accepted

## Context

So far admins had to create every embassy thread in Discord and then assign it on the dashboard (ADR 0007). Most
threads are wanted as soon as somebody from a foreign government is on the server, which is when they link their
account. Only the bot may create threads in Discord, and only the backend knows who holds which office (ADR 0002,
0003). Which country a linked member belongs to costs a WarEra call per member, which the role sync already makes
every hour.

## Decision

- **Admins choose an embassy channel.** The bot creates threads there only. The dashboard refuses a channel in which
  the bot may not create private threads, manage threads and send messages in threads, or that the ambassador or a
  host role may not see, as it does for threads. Without a channel, nothing is created.
- **Every embassy sync asks for missing threads.** For each foreign country without a thread or a request, in which a
  linked member holds one of the chosen offices, the backend asks the bot for a thread "Botschaft <Land>". The
  members' countries come from the role sync's last load, and those of members linked just now are loaded right
  away, so the check costs at most one WarEra call per link. Admins can also ask for a thread with a name of their
  own.
- **A request is a row and an event.** The backend stores the request and publishes an `EmbassyThreadCreationEvent`
  in one transaction. The bot creates a private thread that only moderators can invite to and that archives after a
  week, and reports it back; the backend assigns it and syncs right away. At most one request per country.
- **A minute without an answer is a failure.** A failed request stays with its reason on the dashboard until an
  admin tries again, assigns a thread or dismisses it, so a broken setup does not create threads again every hour. A
  thread the bot reports after the request failed is not taken: the backend answers 410 and the bot deletes it.

## Consequences

- An office holder whose country has no thread yet is in their thread within a sync after linking, or within an hour
  after taking office, as long as the role sync is on: its data is in memory only, so after a start of the backend the
  first hourly run fills it.
- A thread the bot created is an ordinary embassy thread: unassigning it leaves it in Discord (ADR 0007).
- While the embassies are switched off, no sync runs, so nothing is created on its own; admins can still ask for a
  thread.
- An event that comes twice creates no second thread: the bot remembers handled requests and drops events older than a
  minute, and a thread the backend no longer wants is deleted again.
