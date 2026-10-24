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
- **Frontend:** `npm ci`, `npm run lint` and `npm run build`
- **Conventions:** PR title and branch name follow the rules below

The manual workflow **CD** deploys `main` to a GitHub environment such as `staging`; see
[deploy/README.md](deploy/README.md).

Dependabot opens weekly update PRs for Gradle, npm, GitHub Actions and Docker images. The rules for `main` are
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

- Only the **backend** talks to PostgreSQL and the WarEra API. Only the **bot** talks to Discord.
- The bot calls the backend only through the REST API under `/api/**`, with the service token as a bearer token.
  The backend reaches the bot only through events in the Redis stream `bits:events`.
- REST DTOs and events live in `shared` and are serialized with kotlinx.serialization. New events are subtypes
  of `BitsEvent` with a unique `@SerialName`.
- Change the database schema only through new Flyway migrations in `backend/src/main/resources/db/migration`,
  named `V<n>__<description>.sql`. Never edit an existing migration. Hibernate only validates the schema
  (`ddl-auto: validate`).
- Access WarEra through `warera-client`, not directly from backend code.

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
- **Frontend:** TypeScript, React and shadcn/ui, linted with oxlint (`npm run lint`). shadcn components live in
  `frontend/src/components/ui`.
- `.editorconfig` defines indentation and line endings.
- **Language:** Code, comments, logs, commits and branch names are in English. User-facing text (Discord messages,
  dashboard) is in German.
- Add KDoc to classes and public functions whose purpose is not clear from their name. Comments explain why,
  not what.
- Get the current time from the injected `Clock`, never from `Instant.now()`, so tests can control it.

## Tests

- New logic comes with tests in the style of the existing ones (`kotlin.test`, Ktor `MockEngine`, fakes via
  `@TestConfiguration`).
- Backend tests run against real PostgreSQL and Redis with Testcontainers, so they need Docker.
- Tests never call external services (WarEra, Discord). Replace them with fakes.

## Configuration and secrets

- Add new environment variables with a comment to `.env.example` and, where needed, to `docker-compose.yml`.
- Never commit secrets such as tokens or passwords.
