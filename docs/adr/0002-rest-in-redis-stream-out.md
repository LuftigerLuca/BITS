# 0002: The bot calls the backend via REST, events go via Redis

Status: accepted

## Context

The bot needs answers from the backend (start a verification, report roles), and the backend needs to tell the bot
about things it did not ask for (a link, a role sync). The bot must not read the database (0001). Events must not be
lost when the bot is down or restarts, and an event must not reach the bot for a change that was rolled back.

## Decision

- **Bot → backend:** REST under `/api/**`, authenticated with a service token, reachable only inside the compose
  network.
- **Backend → bot:** a Redis stream (`bits:events`) that the bot reads with the consumer group `bot` and
  acknowledges with `XACK`. Events are stored with the transaction that causes them (Spring Modulith's event
  publication registry in Postgres) and written to the stream after the commit, again every minute until that
  works. The stream is trimmed to a week. Events the bot cannot handle after a few deliveries move to
  `bits:events:dead`.

A queue in Postgres that the bot polls was ruled out by 0001; a message broker would be one more service to run for
a handful of events per minute.

## Consequences

- Delivery is at least once: every handler in the bot must be idempotent, and newer role syncs must win over older
  ones (`AppliedSyncs`).
- Redis being down delays events but loses none; the bot catches up from the stream after a restart.
- A bot that stays down for more than a week misses events. For roles that does not matter, since the hourly sync
  sends the full target state again (0003).
- New event types reach the bot only once a bot that knows them is deployed; an older bot moves them to the dead
  letter stream.
