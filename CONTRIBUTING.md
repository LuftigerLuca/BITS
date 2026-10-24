# Contributing to BITS

Setup, architecture and development commands are described in the [README](README.md). This document covers how
changes are made and how they get into `main`.

## Workflow

1. **Branch:** Create a new branch from the current `main` (or from the branch your work builds on).
2. **Implement:** Code, tests and docs, if needed, go into the same branch.
3. **Check:** `./gradlew build` must pass before you open a pull request.
4. **Pull request:** Open it against `main`. It is squash-merged once CI passes and all review threads are
   resolved. Nobody pushes to `main` directly.

## CI

GitHub Actions runs on every pull request and on `main`; all three jobs are required for merging:

- **Gradle:** `./gradlew build` without the frontend, i.e. ktlint and all tests incl. Testcontainers
- **Frontend:** `npm ci`, `npm run lint`, `npm test` and `npm run build`
- **Conventions:** PR title and branch name follow the rules below

The workflow **Images** builds the backend, bot and frontend images and scans them with Trivy; it fails on a
critical vulnerability that has a fix. It also runs weekly, since new vulnerabilities turn up without a change here.
It is not a required check, so a fresh CVE in a base image does not block unrelated pull requests.

The manual workflow **CD** deploys `main` to a GitHub environment such as `staging`; see
[deploy/README.md](deploy/README.md).

Dependabot opens weekly update PRs for Gradle, npm, GitHub Actions and Docker images. Actions are pinned by
commit SHA and images by digest, each with the version next to it; Dependabot updates both. The rules for `main` are
kept in `.github/rulesets/main.json`; change them there and apply them to the repository.

## Branches

Name branches `<type>/<short-name>`, e.g. `feat/link-warera-account`, `fix/ping-timeout`,
`chore/project-skeleton` or `refactor/event-consumer`. The types are the same as for commit messages.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: link discord accounts to warera users

The body explains what changed and why, not how.
```

- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`
- Lowercase subject in the imperative mood, no trailing period
- Each commit is one self-contained change. Do not mix formatting with functional changes.

## Pull requests

- The title follows the Conventional Commits format. It becomes the commit message on `main`, since PRs are
  squash-merged.
- The description says what changed and why, and links the related issue if there is one.
- Keep PRs small. Several focused PRs are better than one that changes everything at once.

## Architecture rules

- Only the **backend** talks to PostgreSQL and the WarEra API. Only the **bot** talks to Discord with the bot
  token and the gateway. The backend calls Discord solely for the OAuth2 login of the dashboard, with the access
  token of the user who logs in, and does not keep that token.
- The dashboard talks to the backend under `/auth/**` (login, logout) and `/web/**` (needs the session cookie,
  `/web/admin/**` the role `ADMIN`). These are separate Spring Security filter chains from the bot's `/api/**`,
  and only they are forwarded by nginx and the Vite dev proxy. DTOs of these endpoints stay in the backend
  module, since the bot does not use them.
- The prefix of a controller's path decides its protection: `/api/` the service token, `/web/` the session,
  `/web/admin/` the role `ADMIN` (admin controllers also carry `@PreAuthorize("hasRole('ADMIN')")`), `/auth/`
  none. Everything else is denied. `EndpointPathTest` fails for other prefixes and for new paths under `/auth/`,
  which have to be added to its list on purpose.
- Requests to `/auth/**` and `/web/**` that change state must come from the dashboard's origin
  (`BITS_PUBLIC_URL`); `SameOriginFilter` rejects others.
- The bot calls the backend only through the REST API under `/api/**`, with the service token as a bearer token.
  The backend reaches the bot only through events in the Redis stream `bits:events`.
- **The bot is trusted.** The backend believes every `discordUserId` and every other claim that comes with the
  service token, so the token stands for full power over all users. It therefore never leaves the compose network:
  nginx does not forward `/api`, and the CD smoke test checks that. A new `/api` endpoint must not do anything the
  bot should not be able to do on behalf of any member; an action only some members may take (e.g. an admin
  action from Discord) has its permission checked by the bot, since only the bot sees who acted. In turn, the bot
  believes every event in `bits:events`, which is why only the backend's Redis user may write to it.
- Publish events for the bot with `EventPublisher`, inside the transaction that makes the change they report.
  They are stored in Spring Modulith's event publication registry with it and written to Redis only after the
  commit, again and again until that works. So an event never reports a change that was rolled back, and an
  outage of Redis neither fails the change nor loses the event. Write to Redis directly only for state that is
  not an event, such as the login lockout.
- Modules react to each other's application events with `@ApplicationModuleListener`, which runs after the
  commit, on its own thread and through the same registry. Use a plain `@EventListener` only for work that must
  happen in the publisher's transaction.
- REST DTOs and events live in `shared` and are serialized with kotlinx.serialization. New events are subtypes
  of `BitsEvent` with a unique `@SerialName`.
- Change the database schema only through new Flyway migrations in `backend/src/main/resources/db/migration`,
  named `V<n>__<description>.sql`. Never edit an existing migration. Hibernate only validates the schema
  (`ddl-auto: validate`).
- Access WarEra through `warera-client`, not directly from backend code.

### Compatibility of DTOs and events

Backend and bot are deployed together, but for a moment they run different versions of `shared`: the backend
starts before the bot, and a rollback may hit only one of them. Every change to a DTO or event must therefore
work with the previous version on the other side:

- Add new fields only with a default value. Unknown fields are ignored (`BitsJson`), so older readers are fine.
- Do not rename or remove a field in one step. Stop using it first, remove it in a later release.
- Publish a new event type only once the bot that handles it is deployed. The bot leaves an event it cannot
  read pending and retries it; after a few deliveries it moves it to the dead letter stream
  `bits:events:dead`, where it can be inspected or added back to `bits:events` by hand.

## Backend architecture

The backend is a modular monolith built with [Spring Modulith](https://spring.io/projects/spring-modulith).
Every package directly under `eu.luftiger.bits.backend` is a module, usually one per feature (e.g. `ping`,
`event`). Each module is split into the same layers:

```
backend/src/main/kotlin/eu/luftiger/bits/backend/
├── <feature>/
│   ├── api/        # what other modules may use: interfaces (<Feature>Api, extension points), dto/
│   ├── domain/     # JPA entities, enums and value types, <Feature>Exception
│   └── internal/   # controllers, services, repositories, jobs, implementations of other modules' api
└── shared/         # cross-cutting configuration (properties, clock, web, WarEra client bean)
```

- **`api/`** is the only part of a module that other modules may use. Annotate every type in it with
  `@NamedInterface("api")`. A module that others need to call exposes an interface (e.g. `EventPublisher`)
  that a class in its `internal/` implements.
- **`domain/`** holds the module's data model. It is private to the module and never returned by a controller.
- **`internal/`** holds everything else. Controllers map between REST DTOs and the domain; services hold the
  logic and transactions; repositories are Spring Data interfaces.
- REST DTOs that the bot uses live in the Gradle module `shared` (package `eu.luftiger.bits.shared`), not in
  `api/dto`. `api/dto` is only for types that backend modules exchange with each other. Do not confuse the Gradle
  module `shared` with the backend package `eu.luftiger.bits.backend.shared`.
- Modules depend on each other only through `api/`, and dependencies must not form cycles. If module A has to
  ask module B something that B must not know about, B defines an extension point in its `api/` and A implements
  it in its `internal/`.
- `ModulithStructureTest` checks these rules on every build.

Modules without anything to expose have no `api/`, and modules without their own data have no `domain/`.

## Code style

- **Kotlin:** ktlint (`ktlint_official`), no wildcard imports. `./gradlew ktlintFormat` formats the code,
  `./gradlew build` checks it.
- **Frontend:** TypeScript, React and shadcn/ui, linted with oxlint (`npm run lint`). Under `frontend/src`:
  `pages/` holds only what a route renders, `features/<feature>/` the components, queries and texts of one feature
  (e.g. `features/role-sync/`), `components/` what several features share, and `components/ui` the shadcn
  components. Data is loaded with TanStack Query: one hook per request in the feature's `queries.ts`, and a
  mutation invalidates what it changes instead of reloading by hand. While data loads or after it failed, show
  `LoadingState` or `ErrorState` from `components/QueryState.tsx`, never the empty state.
- `.editorconfig` defines indentation and line endings.
- **Language:** Code, comments, logs, commits and branch names are in English. User-facing text (Discord messages,
  dashboard) is in German.
- Add KDoc to classes and public functions whose purpose is not clear from their name. Comments explain why,
  not what.
- Get the current time from the injected `Clock`, never from `Instant.now()`, so tests can control it.
- **Logging:** Logs may name single IDs that are needed to follow up on a problem: a Discord user, role or
  guild ID, a stream entry ID, an event type, the subject of a dashboard session, and the client IP of a login.
  They never contain payloads, member lists, the pairing of a Discord account with a WarEra account, verification
  codes, tokens, passwords or the username of a failed login. Log an event by its type,
  stream ID and `occurredAt`.

## Tests

- New logic comes with tests in the style of the existing ones (`kotlin.test`, Ktor `MockEngine`, fakes via
  `@TestConfiguration`).
- Backend tests run against real PostgreSQL and Redis with Testcontainers, and so do the bot's tests of the
  event consumer, so they need Docker.
- Tests never call external services (WarEra, Discord). Replace them with fakes.
- Frontend tests use Vitest and Testing Library in jsdom (`npm test`), next to the code they test. They stub `fetch`
  with `src/test/fetch.ts` instead of talking to a backend, and render whole pages with `src/test/renderApp.tsx`.

## Configuration and secrets

- Add new environment variables with a comment to `.env.example` and, where needed, to `docker-compose.yml`.
- Never commit secrets such as tokens or passwords.
