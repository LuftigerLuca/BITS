# Architecture decisions

Short records of the decisions that shape BITS, each with its context and consequences. The rules that follow from
them are in [CLAUDE.md](../../CLAUDE.md) and [CONTRIBUTING.md](../../CONTRIBUTING.md); these files say why.

| No.                                      | Decision                                                         |
|------------------------------------------|------------------------------------------------------------------|
| [0001](0001-three-processes.md)          | Backend, bot and frontend run as three processes                 |
| [0002](0002-rest-in-redis-stream-out.md) | The bot calls the backend via REST, events go via Redis          |
| [0003](0003-data-ownership.md)           | Postgres holds links and rules, Discord the actual roles         |
| [0004](0004-stateless-sessions.md)       | Dashboard sessions are signed tokens in a cookie                 |
| [0005](0005-single-instance.md)          | One guild, one backend, one bot                                  |
| [0006](0006-anonymous-votes.md)          | Votes are evaluated anonymously, the bot names the voters        |
| [0007](0007-embassy-threads.md)          | Embassy threads are synced as a whole, the bot adds hosts        |
| [0008](0008-embassy-thread-creation.md)  | The bot creates embassy threads, also on its own                 |
| [0009](0009-event-lanes.md)              | The bot reads role syncs in a lane of their own                  |
| [0010](0010-system-status-in-memory.md)  | The system status is kept in memory, the bot reports a heartbeat |

How BITS behaves when one of its parts or services is down is described in [failure-modes.md](../failure-modes.md).

A new decision gets the next number and the same sections. A decision that no longer holds is not deleted; its
status says what replaced it.
