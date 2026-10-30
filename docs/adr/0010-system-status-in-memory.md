# 0010: The system status is kept in memory, the bot reports a heartbeat

Status: accepted

## Context

Admins saw whether Postgres, Redis, WarEra and the bot worked only through the problems the system log records, and
nobody saw how much of WarEra's limit BITS used, or for what. A page "System-Status" was to show both: the state of
each part and counts such as the WarEra calls by procedure and by what they were made for, the requests between bot
and backend and the background jobs, since the start and over the last 24 hours.

## Decision

- **Counts live in the backend's memory.** Totals since the start and one bucket per minute for 24 hours, kept by
  the module `systemstatus`. There is one backend (0005), so nothing has to be shared, and no table or migration is
  needed. Prometheus and Grafana were ruled out as another service to run for a single page.
- **WarEra is not probed.** Every call counts against the limit that verification and role sync share, so its state
  follows from the real calls: an answer means reachable, no answer or a server error does not. `warera-client`
  reports every request, retry, throttled call and cache lookup to a `WareraCallListener`.
- **What a call is made for comes from where it starts.** A filter sets it for requests by their path, and modules
  run their background work through `BackgroundWork`, which also records each run for the page.
- **The bot reports a heartbeat via REST** (`POST /api/bot-heartbeat`, every 30 seconds), as everything the bot tells
  the backend (0002). Its counts run since the bot started and the backend takes the difference, so a heartbeat lost
  while the backend is down loses nothing. A round trip through the event stream was ruled out: a long role sync
  holds up the lane (0009) and would make the bot look down.
- **Postgres and Redis are checked in the background**, every 15 seconds, so the page never waits for a database
  that is down.

## Consequences

- After a restart of the backend all counts start from zero; the page says since when it counts.
- The page cannot show an outage of Redis or of the backend: without them the dashboard does not open (0004).
- Requests of the bot that reach no endpoint, e.g. without the token, are counted too, since the filter runs before
  Spring Security.
- A new background job that calls WarEra should run through `BackgroundWork`, or its calls count as "Sonstiges".
