# CLAUDE.md

BITS is a Discord bot for the German WarEra Discord, with a backend and a web dashboard. The
[README](README.md) covers setup and commands, [CONTRIBUTING.md](CONTRIBUTING.md) covers workflow and
conventions. Both apply to Claude as well.

## Modules

- `shared`: DTOs and events (`BitsEvent`), shared JSON configuration (`BitsJson`)
- `warera-client`: tRPC client for WarEra (`WareraClient`, `RestWareraClient`)
- `backend`: Spring Boot 4, Spring Modulith, JPA, Flyway, Redis. One module per feature under
  `eu.luftiger.bits.backend.<feature>` with `api/` (exposed interfaces and DTOs, `@NamedInterface("api")`),
  `domain/` (entities, exceptions) and `internal/` (controller, service, repository); cross-cutting config in
  `shared/`. Details in CONTRIBUTING.md, "Backend architecture".
- `bot`: JDA 6, Ktor client for the backend, Lettuce for the Redis stream. No Spring; everything is wired up in
  `Main.kt`.
- `frontend`: Vite, React 19, Tailwind 4, shadcn/ui

The verification (`VerifyCommand` → `VerificationController`/`VerificationService` → `AccountLinked` and
`VerificationCheckedEvent`) is the reference for new features that span all parts, the role sync for the dashboard
and background jobs. CONTRIBUTING.md, "Reference flows", lists the patterns they show. `/ping` is only a smoke test.

## Key rules

- Only the backend talks to the database and the WarEra API. Only the bot talks to Discord with the bot token
  and the gateway; the backend calls Discord solely for the dashboard's OAuth2 login, on behalf of the user who
  logs in. The bot calls the backend via REST (`BackendClient`), the backend reaches the bot via events in Redis
  (`EventPublisher`). No shortcuts.
- The bot's API under `/api/**` is protected by the service token only; the dashboard uses `/auth/**` and
  `/web/**` with a session cookie. nginx forwards only the latter.
- Backend modules use each other only through `api/`. Never import another module's `domain/` or `internal/`;
  `ModulithStructureTest` fails on it.
- Change the database schema only with a new Flyway migration. Never touch existing migrations.
- Get the current time from the injected `Clock`.
- Code and comments in English, user-facing text in German.

## Checks

```sh
./gradlew build            # all modules incl. ktlint and tests (backend tests need Docker)
./gradlew :bot:test        # a single module
./gradlew ktlintFormat     # fix ktlint violations
cd frontend && npm run lint && npm test && npm run build
```

Only report work as done when the checks of the affected modules pass. If something could not run (e.g. no
Docker), say so explicitly.

## Git

- Do not commit or push without explicit approval.
- Commits and PR titles follow Conventional Commits, branches are named `<type>/<name>`.
- No AI attribution in commits or PRs (no `Co-Authored-By`, no "Generated with Claude Code").
