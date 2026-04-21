# Continue Implementing

## Ownership

`craftalism-market` owns plugin-side consumption of Craftalism API/auth configuration, token acquisition, cached browsing behavior, GUI/session orchestration, and player-facing trade interaction.

This repository consumes, and does not own:

- deployment runtime seeding and container wiring, owned by `craftalism-deployment`
- OAuth2 token issuance, owned by `craftalism-authorization-server`
- protected API validation and market route authority, owned by `craftalism-api`

## Current Auth Status

The market plugin now has economy-style auth/config parity:

- reads `MARKET_API_BASE_URL` and falls back to `CRAFTALISM_API_URL`
- reads `AUTH_ISSUER_URI`
- reads `AUTH_TOKEN_PATH`, defaulting to `/oauth2/token`
- reads `MINECRAFT_CLIENT_ID`
- reads `MINECRAFT_CLIENT_SECRET`, falling back to `CRAFTALISM_API_KEY`
- reads optional `MINECRAFT_CLIENT_SCOPES`
- mints OAuth2 client-credentials bearer tokens internally when credentials are configured
- caches tokens until shortly before expiry
- uses the shared bearer-token provider for snapshot, quote, and execute calls
- keeps `market-api.auth-token` as a static-token fallback

## Next Repo-Local Work

Continue with plugin-local hardening only:

1. Broaden tests around reconnect-time settlement, stale quote refresh, and degraded-mode GUI behavior.
2. Harden compensation logging and player/operator messaging for post-settlement local inventory failures.
3. Consider bounded live-viewer refresh fanout only after settlement handling is stable.

Do not move market pricing, quote validity, execution authority, OAuth2 issuance, or deployment wiring into this repository.
