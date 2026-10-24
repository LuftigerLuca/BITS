# 0003: Postgres holds links and rules, Discord the actual roles

Status: accepted

## Context

Roles live in Discord, where admins and other bots can change them too. Which roles a member should have depends on
WarEra data that changes without notice (citizenship, offices, military units, levels), and gateway events or role
changes can get lost.

## Decision

- **Postgres** is the source for what BITS decides: links between Discord and WarEra accounts, and the role rules.
- **Discord** is the source for the actual state: which roles exist and who has them. The bot reports the roles of
  the guild to the backend; the backend never asks Discord.
- **The backend** computes the target state from links, rules and WarEra data and publishes it as `RoleSyncEvent`.
  The bot compares it with the actual roles and changes only what differs.
- **The hourly sync** sends the full target state with fresh WarEra data. It is the reconciliation that repairs
  whatever got lost in between: a failed role change, a missed event, a manual change to a managed role.

## Consequences

- A role with at least one rule belongs to BITS: anybody who meets none of its rules loses it, even if an admin gave
  it by hand.
- A failed change costs at most an hour, so the bot can log and skip it instead of retrying.
- Missing WarEra data must not look like change: members whose data cannot be loaded keep their roles, and a full
  sync that would take a role from many of its holders at once is held back (`RemovalLimit`).
- Nicknames or other Discord state are not managed; adding them would follow the same pattern.
