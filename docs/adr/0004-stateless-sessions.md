# 0004: Dashboard sessions are signed tokens in a cookie

Status: accepted

## Context

The dashboard has few users, mostly admins, who log in with Discord or, as a fallback, with the admin account from
the environment. Whether someone is an admin is decided by their Discord permissions at login. The backend should
restart without logging everybody out, and should not depend on Discord being reachable for every request.

## Decision

A session is a JWT signed with `BITS_JWT_SECRET`, in an HttpOnly, Secure, SameSite cookie. It holds the user and
the role and is not extended: 8 hours for `USER`, 1 hour for `ADMIN`. The role is checked only at login. Logging out
puts the token's ID on a denylist in Redis until it would have expired. The HTTP session is used only during the
OAuth2 redirect.

## Consequences

- No session store: a restart keeps everybody logged in, and requests need neither Discord nor Postgres to
  authenticate.
- Losing admin rights in Discord takes effect at the next login, at most an hour later. To end all sessions at once,
  rotate `BITS_JWT_SECRET`.
- Every request checks the denylist. If Redis cannot be asked, the session is rejected: a dashboard that needs a new
  login is better than one that lets a revoked session in. The admin login also counts attempts in Redis, so the
  dashboard as a whole needs Redis.
