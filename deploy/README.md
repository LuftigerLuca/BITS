# Deployment

BITS is deployed with the manual workflow [CD](../.github/workflows/cd.yml) to a GitHub environment, e.g.
`staging` with a test bot on a test server, and later `production`. A self-hosted runner on the target host
builds the images, writes the [stack](docker-compose.yml) and a generated `.env` into `DEPLOY_PATH` and starts
it. The host needs no inbound connections and no registry.

Each environment runs its own Discord application. The staging bot is a separate bot user with its own token on
a test server, so testing never touches the real WarEra Discord.

## Set up an environment

1. **Discord application:** In the [Developer Portal](https://discord.com/developers/applications), create an
   application (e.g. "BITS Staging") and copy the token under **Bot → Reset Token**. On the same page, turn on
   **Server Members Intent**; the role sync needs all members, and the bot does not start without it. Invite the
   bot to the test server via **OAuth2 → URL Generator** with the scopes `bot` and `applications.commands` and
   the permission **Manage Roles**. The bot can only give roles below its own highest role. Copy the ID of
   the server (developer mode in Discord, right-click on the server → **Copy Server ID**). For the dashboard
   login, copy **Client ID** and **Client Secret** under **OAuth2** and add
   `<BITS_PUBLIC_URL>/auth/oauth2/callback/discord` under **Redirects**.
2. **Host:** Install Docker with the Compose plugin and a
   [self-hosted runner](https://docs.github.com/en/actions/hosting-your-own-runners) for this repository. Give
   the runner the label of the environment (e.g. `staging`) and let its user run `docker`.
3. **GitHub environment:** Under **Settings → Environments**, create the environment with the same name.
   Restrict **Deployment branches** to `main`. Then add:

   | Name                       | Kind     | Value                                                                                               |
   |----------------------------|----------|-----------------------------------------------------------------------------------------------------|
   | `DEPLOY_PATH`              | variable | Directory for the stack on the host, e.g. `/srv/bits-staging`                                       |
   | `DISCORD_GUILD_ID`         | variable | ID of the server the bot serves                                                                     |
   | `PUBLIC_PORT`              | variable | Port of the dashboard on the host (optional, default `8080`)                                        |
   | `PUBLIC_BIND`              | variable | Interface the dashboard is bound to (optional, default `127.0.0.1`)                                 |
   | `BITS_PUBLIC_URL`          | variable | Origin the dashboard is reached under, e.g. `https://bits.example.org`                              |
   | `DISCORD_CLIENT_ID`        | variable | OAuth2 client ID from step 1                                                                        |
   | `BITS_ADMIN_USERNAME`      | variable | Name of the admin account (optional, disabled without it)                                           |
   | `DISCORD_TOKEN`            | secret   | Bot token from step 1                                                                               |
   | `DISCORD_CLIENT_SECRET`    | secret   | OAuth2 client secret from step 1                                                                    |
   | `BITS_SERVICE_TOKEN`       | secret   | Random secret, e.g. `openssl rand -hex 32`                                                          |
   | `BITS_JWT_SECRET`          | secret   | Random secret for the dashboard sessions, e.g. `openssl rand -hex 32`                               |
   | `BITS_ADMIN_PASSWORD_HASH` | secret   | BCrypt hash of the admin password (optional), e.g. `htpasswd -nbBC 12 "" 'password' \| cut -d: -f2` |
   | `POSTGRES_PASSWORD`        | secret   | Random secret; keep it, the database is created with it on the first run                            |

The dashboard sets its session cookie with `Secure`, so it has to be served over HTTPS (a TLS proxy in front of
`PUBLIC_BIND:PUBLIC_PORT`); only `localhost` works without. Changing `BITS_JWT_SECRET` logs everybody out.

Environments on the same host need different values for `DEPLOY_PATH` and `PUBLIC_PORT`. Containers, volumes and
images are named after the environment (`bits-staging-…`), so they do not get in each other's way.

## Deploy

Under **Actions → CD → Run workflow**, pick the environment and run it from `main`. The workflow:

1. builds the images, tagged with the commit,
2. writes `docker-compose.yml` and `.env` (readable only by the runner's user) into `DEPLOY_PATH`,
3. starts the stack and waits until Postgres, Redis, backend and dashboard are healthy (Flyway migrates on
   backend startup),
4. checks that the backend rejects requests without the service token, that the bot connects to Discord and
   stays up, and that the dashboard answers and forwards `/web` to the backend,
5. removes older images of the environment, keeping the previous set as a fallback.

The bot registers its slash commands on the server at every start, so new commands show up right after a
deployment.

## Operate

```sh
cd /srv/bits-staging          # DEPLOY_PATH
docker compose ps
docker compose logs -f bot backend
```

To end every dashboard session at once, e.g. after a leaked cookie or when someone lost the admin rights, set a
new `BITS_JWT_SECRET` in the environment and deploy again. A single session ends with its logout, ADMIN sessions
on their own after one hour.

To roll back, set the image tags in `.env` to the previous commit (`docker images 'bits-staging-*'`) and run
`docker compose up -d`. A migration that already ran is not undone by that; there are no automatic backups yet.
