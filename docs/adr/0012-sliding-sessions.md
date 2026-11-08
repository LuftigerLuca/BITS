# 0012: Dashboard sessions slide with use and are checked at Discord every hour

Status: accepted, supersedes [0004](0004-stateless-sessions.md)

## Context

Under ADR 0004, a session was a JWT that was never extended: 1 hour for `ADMIN`, 8 hours for `USER`. The short
admin session was the price for noticing lost admin rights without a session store, but it logged admins out in the
middle of their work and made them log in with Discord on every visit. The roles that decide who manages votes had
the same problem. Rights must still be checked again at least every hour.

## Decision

- A session is an entry in Redis, created at login. It ends after 24 hours without a request, and 7 days after the
  login however active it is. Both are measured with the backend's `Clock`; every request counts as use.
- The cookie holds a JWT signed with `BITS_JWT_SECRET` that names the session and is valid for 1 hour. The cookie
  itself lasts as long as the session can, so the browser keeps sending the expired token.
- An expired token is renewed on the way to `/web/`, before the request is authenticated, so the dashboard does not
  notice. For a Discord user, the backend swaps Discord's refresh token for a new access token and checks again
  whether the user is in the guild, whether they are an admin and which roles they have. A former admin goes on as
  `USER`; who has left the guild or deauthorized the app is logged out, and the login page says the session ended.
- The refresh token is kept in the session entry, encrypted with AES-GCM under a key derived from
  `BITS_JWT_SECRET`. The access token is still dropped after use.
- If Discord cannot be reached, the session goes on with the role of the last check, and the check is tried again a
  minute later. The 24-hour and 7-day limits still apply.
- The admin account from the environment is not checked anywhere. Its session keeps a fingerprint of
  `BITS_ADMIN_PASSWORD_HASH`; a different hash, or a disabled account, ends the session at the next renewal.
- Logging out deletes the entry, which ends every token of the session and its renewal, and still puts the current
  token on the denylist.
- Discord rotates the refresh token on every use. Requests that arrive together with the same expired token are
  therefore renewed one after the other, behind a lock in memory (one backend, ADR 0005); the later ones reuse the
  token the first one got.

## Consequences

- Admins stay logged in while they work, and still lose their rights within an hour of losing them in Discord.
  Roles that were given or taken in Discord also count in the dashboard within an hour.
- Every request reads and writes the session entry in Redis, in addition to checking the denylist. If Redis cannot
  be asked, the session is rejected, as before; losing Redis's data logs everybody out. A restart of the backend
  keeps the sessions.
- A renewal needs Discord, but an outage does not log anybody out; it only delays the check.
- If Discord rotates the refresh token but its answer gets lost, the next renewal fails and the user has to log in
  again. That is rare and is accepted.
- Rotating `BITS_JWT_SECRET` still ends every session: the tokens lose their signature, and the refresh tokens can no
  longer be decrypted.
- Sessions from before this change have no entry. They are not renewed and run out on their own.
