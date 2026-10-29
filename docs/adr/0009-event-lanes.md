# 0009: The bot reads role syncs in a lane of their own

Status: accepted

## Context

The bot handled the events of the stream (0002) one after the other. A role sync changes every role with a call of
its own, and Discord throttles role changes per guild. When status roles were first set on a guild of 1500 members,
one sync had to give a role to nearly every member and took far longer than the two minutes `/verify` waits for the
result of a check. The `VerificationCheckedEvent` waited behind it, and members were told the check took unusually
long, although the backend had finished it in seconds. Every large sync does the same, e.g. after the rule of a role
with many holders changes.

## Decision

- **Two consumer groups on the same stream.** `bot` handles every event but role syncs, `bot-role-sync` handles
  only role syncs (`EventLane`). Each group reads the whole stream and acknowledges the events of the other lane
  right away, deciding by the `type` field without decoding the rest. Entries without a readable type belong to
  `bot`, so an unreadable entry reaches the dead letter stream once.
- **Each lane keeps the rules of 0002.** Retries, the dead letter stream and at-least-once delivery work per group
  as before, and role syncs stay in order among themselves, so newer ones still win over older ones.
- **A new lane starts where `bot` is.** Each consumer creates the groups of all lanes before it reads, `bot` first. A
  missing lane group is created before the oldest pending event of `bot`, or else after the last one it read, so an
  update does not lose the role syncs the old bot had not handled yet. A few it had handled may come again, which
  idempotent handlers allow.

Handling role syncs in parallel or acknowledging them before they are done was ruled out: the first breaks the
order among syncs, the second loses a sync when the bot stops halfway.

## Consequences

- Events of different lanes may be handled in any order, and at the same time. No handler depends on that order:
  the backend already starts the role sync and the embassy sync independently.
- A long role sync still delays the next role sync, e.g. the roles of a member who just linked.
- A new lane needs a new group name and nothing in the backend, which only writes to the stream. The bot's Redis
  user may already create groups and read their state.
- Rolling back to a bot without lanes leaves the role syncs that `bot-role-sync` took over acknowledged in `bot`;
  the next hourly sync makes up for them. The group `bot-role-sync` can then be deleted by hand.
