# 0001: Backend, bot and frontend run as three processes

Status: accepted

## Context

BITS has to hold a Discord gateway connection, store links and rules, talk to the WarEra API and serve a dashboard.
A single process could do all of it. But the bot token lets its holder act as the bot on the whole guild, and the
dashboard is the one part reachable from the internet.

## Decision

Three processes, each in its own container: the **backend** (Spring Boot) owns Postgres, Redis writes and the WarEra
API; the **bot** (JDA, no Spring) owns the Discord token and the gateway; the **frontend** (nginx with the built
dashboard) serves the dashboard and forwards `/auth` and `/web` to the backend. Only the backend reads and writes the
database. The backend calls Discord only for the OAuth2 login, on behalf of the user.

## Consequences

- A hole in the dashboard or backend does not hand out the bot token, and the frontend container reaches neither
  Postgres nor Redis (see the networks in `deploy/docker-compose.yml`).
- The bot restarts quickly and on its own, without taking the dashboard or a running sync down with it.
- Every feature that spans bot and data needs a REST call and often an event (see 0002), and DTOs must stay
  compatible across versions, since backend and bot are not updated in the same instant.
- Three images to build, scan and deploy instead of one.
