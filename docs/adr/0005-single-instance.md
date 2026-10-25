# 0005: One guild, one backend, one bot

Status: accepted

## Context

BITS serves the German WarEra Discord, a single guild with a few thousand members. Its load is small: a few
commands per minute and an hourly sync limited by the WarEra API (100 requests per minute and IP).

## Decision

Each environment runs exactly one backend and one bot for exactly one guild (`DISCORD_GUILD_ID`). Nothing
coordinates several instances.

## Consequences

Running more than one instance breaks things, not just wastes them:

- **Two backends** would both run the hourly sync and the cleanup jobs, and each would throttle itself to the WarEra
  limit, so together they would exceed it. The queue of role syncs (`RoleSyncTrigger`) and the member list of the guild
  (`GuildMemberService`) live in memory and do not span processes.
- **Two bots** would read the stream under the same consumer name, so each event goes to whichever asks first,
  each takes the other's pending events for its own, and both report roles.
- **A second guild** needs its own environment; links, rules and roles are not separated by guild.

Scaling out would need leader election (or ShedLock) for the jobs, a shared rate limiter and consumer names per
instance. Until the load calls for it, a restart is the plan for failures: Docker restarts a crashed container, and
the outbox and the stream make sure nothing is lost meanwhile (0002).
