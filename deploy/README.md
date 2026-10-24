# Deployment

BITS is deployed with the manual workflow [CD](../.github/workflows/cd.yml) to a GitHub environment, e.g.
`staging` with a test bot on a test server, and later `production`. A self-hosted runner on the target host
builds the images, writes the [stack](docker-compose.yml) and a generated `.env` into `DEPLOY_PATH` and starts
it. The host needs no inbound connections and no registry.

Each environment runs its own Discord application. The staging bot is a separate bot user with its own token on
a test server, so testing never touches the real WarEra Discord.

## Set up an environment

1. **Discord application:** In the [Developer Portal](https://discord.com/developers/applications), create an
   application (e.g. "BITS Staging") and copy the token under **Bot → Reset Token**. Invite the bot to the
   test server via **OAuth2 → URL Generator** with the scopes `bot` and `applications.commands`. Copy the ID of
   the server (developer mode in Discord, right-click on the server → **Copy Server ID**).
2. **Host:** Install Docker with the Compose plugin and a
   [self-hosted runner](https://docs.github.com/en/actions/hosting-your-own-runners) for this repository. Give
   the runner the label of the environment (e.g. `staging`) and let its user run `docker`.
3. **GitHub environment:** Under **Settings → Environments**, create the environment with the same name.
   Restrict **Deployment branches** to `main`. Then add:

   | Name                 | Kind     | Value                                                                    |
   |----------------------|----------|--------------------------------------------------------------------------|
   | `DEPLOY_PATH`        | variable | Directory for the stack on the host, e.g. `/srv/bits-staging`            |
   | `DISCORD_GUILD_ID`   | variable | ID of the server the bot serves                                          |
   | `PUBLIC_PORT`        | variable | Port of the dashboard on the host (optional, default `8080`)             |
   | `PUBLIC_BIND`        | variable | Interface the dashboard is bound to (optional, default `127.0.0.1`)      |
   | `DISCORD_TOKEN`      | secret   | Bot token from step 1                                                    |
   | `BITS_SERVICE_TOKEN` | secret   | Random secret, e.g. `openssl rand -hex 32`                               |
   | `POSTGRES_PASSWORD`  | secret   | Random secret; keep it, the database is created with it on the first run |

Environments on the same host need different values for `DEPLOY_PATH` and `PUBLIC_PORT`. Containers, volumes and
images are named after the environment (`bits-staging-…`), so they do not get in each other's way.

## Deploy

Under **Actions → CD → Run workflow**, pick the environment and run it from `main`. The workflow:

1. builds the images, tagged with the commit,
2. writes `docker-compose.yml` and `.env` (readable only by the runner's user) into `DEPLOY_PATH`,
3. starts the stack and waits until Postgres, Redis, backend and dashboard are healthy (Flyway migrates on
   backend startup),
4. checks that the backend rejects requests without the service token, that the bot connects to Discord and
   stays up, and that the dashboard answers,
5. removes older images of the environment, keeping the previous set as a fallback.

The bot registers its slash commands on the server at every start, so new commands show up right after a
deployment.

## Operate

```sh
cd /srv/bits-staging          # DEPLOY_PATH
docker compose ps
docker compose logs -f bot backend
```

To roll back, set the image tags in `.env` to the previous commit (`docker images 'bits-staging-*'`) and run
`docker compose up -d`. A migration that already ran is not undone by that; there are no automatic backups yet.
