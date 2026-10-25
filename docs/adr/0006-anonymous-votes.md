# 0006: Votes are evaluated anonymously, the electorate comes from the bot

Status: accepted

## Context

Members vote Yes, No or Abstain on questions such as a declaration of war; the result is broken down by WarEra
party and names who did not vote. Who voted how must stay secret, also from admins and from the database, but
somebody must be able to change their vote until the end. Who may vote depends on a Discord role, and only the bot
knows its members (ADR 0003). Ending a vote needs the bot (who has the role now) and WarEra (who is in which party),
and either can be down at that moment.

## Decision

- **Ballots are kept per member only while the vote runs**, so a member can change theirs and nobody votes twice.
  The evaluation counts them per party and then deletes the ballots and the electorate; what remains are the counts
  per party and the names of those who did not vote. Neither the API nor the dashboard ever shows a single ballot.
- **The bot reports the electorate at the end.** The backend publishes `VoteClosedEvent`, and the bot answers with
  the role's members via `PUT /api/votes/{id}/electorate`. Ballots of members who lost the role or left in the
  meantime do not count. Only members with a linked WarEra account count at all, since the party comes from WarEra.
- **The party is looked up at the end**, from the parties of each voter's country of citizenship at that moment.
- **A vote that cannot be evaluated stays `COUNTING`** and is retried: the backend asks the bot again after 30
  minutes without an answer and retries WarEra every 5 minutes. The result is posted by the bot once it receives
  `VoteEvaluatedEvent`, which is also published again until the bot confirms it.
- **Who may manage votes** comes from the member's Discord roles: the bot sends them along with each command, the
  dashboard takes them from the session, which reads them at login with the scope `guilds.members.read`.

## Consequences

- The secrecy rests on deleting the ballots at the evaluation. Until then, somebody with access to the database
  can see them; a database dump taken during a vote keeps them until it rotates out.
- A party with a single voter, or a group where everybody voted the same, reveals how its members voted. That is a
  property of a result by party and is accepted.
- The dashboard sees role changes only at the next login, so a member who lost a manager role can keep managing
  votes there for up to the session's lifetime (8 hours). Discord commands always use the current roles.
- If the bot posts the result but cannot confirm it, it may post the result twice.
