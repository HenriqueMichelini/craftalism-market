# Repo Requirement Pack: craftalism-market

## Repo Role
`craftalism-market` is the Minecraft plugin client for the ecosystem market experience. It consumes canonical market, auth, and shared governance contracts and translates them into player-facing command behavior, GUI interaction, async orchestration, cache usage, and fallback handling.

## Owned Contracts
- No core backend market contracts
- Own plugin-local behavior for:
  - `/market` command UX
  - GUI navigation and rendering behavior
  - plugin-local session/viewer tracking
  - cache usage and stale-mode behavior
  - async orchestration between API calls and Bukkit/Paper main-thread UI work
  - player inventory inspection and local delivery behavior
  - plugin-local documentation coherence

## Consumed Contracts
- `market-snapshots`
  - Consume canonical market category and item snapshot semantics correctly
- `market-quotes`
  - Consume canonical quote semantics, including quantity-sensitive pricing, without recomputing backend logic locally
- `market-execution`
  - Consume canonical buy/sell execution behavior and authoritative results correctly
- `market-rejection-codes`
  - Map stable API rejection codes into player-friendly plugin messages
- `market-versioning`
  - Use `snapshotVersion` and/or `quoteToken` correctly for stale detection and refresh safety
- `error-semantics`
  - Reflect API and transport failures appropriately in plugin UX
- `auth-issuer`
  - Obtain and use credentials/tokens consistently with issuer expectations
- `ci-cd`
  - Meet plugin quality and release-gate expectations
- `testing`
  - Meet plugin-side testing expectations for command, GUI, async, and cache behavior
- `documentation`
  - Keep README and docs aligned with real plugin behavior and consumed contracts
- `security-access-control`
  - Avoid overstating trust, access, or authority that belongs to backend services

## Current Phase Objective
This phase is limited to:
- establishing the market plugin as a clean client of `craftalism-api`
- defining plugin-local responsibilities for `/market` command and GUI behavior
- making stale-mode, cache, and async boundaries explicit
- documenting the plugin/API boundary before implementation expands

This phase is not for implementing the backend market engine inside the plugin.

## Required This Phase
- Verify each consumed contract and classify it as:
  - already compliant
  - partially compliant
  - missing
  - incorrectly implemented
- Implement only confirmed plugin-local behavior and documentation
- Keep market pricing, momentum, stock-regeneration, and quote authority outside the plugin
- Define how the plugin handles:
  - cached category and item snapshots
  - debounced quote flow
  - stale quote rejection
  - read-only degraded mode when API communication is unavailable
  - session/viewer cleanup on GUI close or player quit
- Fix documentation only where it directly describes real plugin-local behavior or consumed contracts
- Fix CI/CD or testing only where:
  - required standards are clearly violated, and
  - the gap materially weakens confidence in this repo

## Not Required This Phase
- Backend pricing engine implementation
- Momentum or regeneration rule ownership
- API-side durable market state ownership
- API-side quote token design ownership
- Broad plugin architecture rewrites unrelated to market behavior
- Admin/operator command expansion unless explicitly scoped later

## Local Requirements
- Keep `/market` GUI-first and player-facing
- Keep API calls asynchronous and never block the main server thread
- Return GUI writes, inventory reads, and player messaging to the main thread
- Keep cache and stale-mode behavior explicit and safe
- Allow read-only browsing when cached data exists but executable trading is unavailable
- Keep viewer/session tracking in-memory and clean up promptly
- Preserve UX clarity when quotes refresh, prices change, or items become blocked

## Governance Requirements
- Comply with shared `ci-cd`, `testing`, `documentation`, and `security-access-control` standards
- Treat `craftalism-api` as authoritative for pricing, stock, momentum, quote, and execution semantics
- Do not redefine backend contracts locally

## Out of Scope
- API-side market pricing formulas
- API-side stock regeneration and momentum persistence
- API-side trade authorization and durable execution state
- Auth-server token issuance internals
- Deployment/runtime ownership outside plugin-local consumption

## Audit Questions
- Does the plugin behave as a market client without re-implementing backend market rules?
- Are cached snapshots, quote flow, and stale handling explicit and safe?
- Are buy/sell operations blocked appropriately when API communication is unavailable?
- Are viewer/session updates safe, incremental, and main-thread correct?
- Do docs accurately distinguish plugin-local behavior from API-owned market logic?

## Success Criteria
- The plugin is a clean market contract consumer
- `/market` behavior is clearly scoped to plugin-local UX and orchestration
- Cached browsing and degraded mode are explicit and safe
- Quote and execution behavior stay authoritative in the API
- Docs are coherent enough to guide implementation without blurring ownership
