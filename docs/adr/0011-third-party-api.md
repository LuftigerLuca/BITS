# 0011: Third parties read through a separate API with scoped, expiring tokens

Status: accepted

## Context

Tools outside BITS, such as statistics bots of the community, want to read data BITS has: who is linked to which
WarEra account, the daily figures, the DSKs and the outcome of votes. The bot's API under `/api` cannot be opened for
them, since its service token stands for full power over all users (see CONTRIBUTING.md, "The bot is trusted"), and
the dashboard's `/web` needs a session of a person. Admins should decide who may read what, and access granted once
should not last forever.

## Decision

A third API under `/external/v1/` only reads. It has its own filter chain, which knows neither the session cookie nor
the service token, and it allows GET only. Clients authenticate with an API token as bearer token, which admins create
under "API-Tokens" in the dashboard:

- A token is 256 random bits with the prefix `bits_`, shown once. Only its SHA-256 hash is stored, unsalted, since the
  token is random. Revoking deletes it, which takes effect with the next request.
- Every token expires, at the latest a year after it was created. Scopes and expiry cannot be changed: more rights or
  more time need a new token, so access is reviewed regularly and the secret is rotated.
- There is one scope per endpoint. Each endpoint names its scope with `@ExternalEndpoint`; an endpoint without one is
  denied, so a new endpoint is never open by mistake, and an existing token never gets a new endpoint.
- Every token may send 60 requests per minute, counted in Redis.
- The endpoints read the database through the `api/` of the modules that own the data, never WarEra, and have their
  own response types, versioned by the path.
- springdoc documents the API at `/external/docs`, open to everybody, generated from the code; the scope of an endpoint
  comes from the same annotation as its check.

## Consequences

- Data that a tool has read lives outside BITS: unlinking with `/unverify` does not delete it there. The note in
  `/verify`, the manual and the README tell members so.
- Every request reads the token from Postgres, and records its last use at most once a minute. Without Postgres, the
  API answers 401; without Redis, it works without the limit.
- nginx forwards `/external/` to the backend as well. The documentation needs no exception in the CSP, since Swagger UI
  loads only scripts of its own origin.
- New data for third parties needs an endpoint with a new scope and, if another module owns the data, a read interface
  in that module's `api/`.
