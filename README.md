# craftalism-market

Minecraft Paper plugin client for the Craftalism market experience.

## Project Layout

This repository is a single Gradle project rooted at the repository top level.

## Current State

The repository currently provides the initial snapshot, quote, and trade-execution client flow:

- root-level Gradle build and Paper plugin packaging
- `/market` command entry
- API-backed snapshot browsing with local cached fallback
- category and item browsing GUIs
- trade GUI quantity controls with debounced quote refresh
- buy execution from the latest quote with rejection-code handling
- plugin-local item delivery after successful buys, including overflow drops
- sell execution with plugin-local inventory validation and post-success item removal
- in-memory session tracking and cleanup on inventory close/player quit

## Runtime Configuration

The plugin consumes Craftalism API and auth settings from environment variables first, then from `config.yml`.

| Setting | Purpose |
| --- | --- |
| `MARKET_API_BASE_URL` or `CRAFTALISM_API_URL` | Base URL for `craftalism-api`. `MARKET_API_BASE_URL` has priority when both are set. |
| `AUTH_ISSUER_URI` | OAuth2 issuer base URL used to mint API bearer tokens. |
| `AUTH_TOKEN_PATH` | Token endpoint path. Defaults to `/oauth2/token`. |
| `MINECRAFT_CLIENT_ID` | OAuth2 client ID for the Minecraft server client. |
| `MINECRAFT_CLIENT_SECRET` or `CRAFTALISM_API_KEY` | OAuth2 client secret. `MINECRAFT_CLIENT_SECRET` has priority when both are set. |
| `MINECRAFT_CLIENT_SCOPES` | Client-credentials scope string. Defaults to `api:write` when unset. |

When OAuth2 client credentials are configured, the plugin mints and caches bearer tokens internally and uses them for snapshot, quote, and execute calls. `market-api.auth-token` remains available only as a static-token fallback.

The remaining hardening work is around compensation handling, broader GUI/session coverage, and continued alignment with API contract evolution.
