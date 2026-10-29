# 0003: Postgres holds links and rules, Discord the actual roles

Status: accepted

## Context

Roles live in Discord, where admins and other bots can change them too. Which roles a member should have depends on
WarEra data that changes without notice (citizenship, offices, military units, levels), and gateway events or role
changes can get lost.

## Decision

- **Postgres** is the source for what BITS decides: links between Discord and WarEra accounts, the role rules, and
  the DSKs with their military units.
- **Discord** is the source for the actual state: which roles exist and who has them. The bot reports the roles of
  the guild to the backend; the backend never asks Discord.
- **The backend** computes the target state from links, rules, DSKs and WarEra data and publishes it as `RoleSyncEvent`.
  The bot compares it with the actual roles and changes only what differs.
- **The hourly sync** sends the full target state with fresh WarEra data. It is the reconciliation that repairs
  whatever got lost in between: a failed role change, a missed event, a manual change to a managed role.

## Consequences

- A role with a rule belongs to BITS: anybody who does not meet its rule loses it, even if an admin gave it by hand.
  The same holds for the role of a DSK, which only the members of its military units keep. A role belongs either to a
  rule or to a DSK, so two sources never disagree about it.
- The DSKs live in a module of their own (`militaryunit`) that depends on the role sync. The role sync reads them
  through an interface of its own (`DskRoles`) that the module implements, so the modules do not depend on each other
  in a cycle.
- A failed change costs at most an hour, so the bot can log and skip it instead of retrying.
- Missing WarEra data must not look like change: members whose data cannot be loaded keep their roles, and a full
  sync that would take a role from many of its holders at once is held back (`RemovalLimit`).
- Nicknames or other Discord state are not managed; adding them would follow the same pattern.
