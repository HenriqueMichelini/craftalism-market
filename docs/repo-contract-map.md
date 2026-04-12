# Repo Contract Map: craftalism-market

## Repository Role
`craftalism-market` is the Minecraft plugin client for the market experience. It consumes canonical market and auth behavior and presents it as player-facing command and GUI interaction. It is responsible for plugin-side orchestration, cache/session correctness, incremental GUI updates, and coherent documentation.

## Owned Contracts
- No core backend market contracts
- Owns plugin-local behavior for:
  - `/market` command UX
  - GUI navigation and rendering
  - stale-mode browsing behavior
  - cache usage and refresh orchestration
  - session/viewer registry correctness
  - inventory inspection and local item-delivery handling
  - async response application to current GUI state

## Consumed Contracts
- `market-snapshots`
  - Must consume canonical category and item snapshot data correctly
- `market-quotes`
  - Must consume quantity-aware quotes without re-implementing pricing math locally
- `market-execution`
  - Must submit buy/sell intent and consume authoritative execution results correctly
- `market-rejection-codes`
  - Must map stable API rejection codes to player-friendly messages
- `market-versioning`
  - Must use `snapshotVersion` and `quoteToken` correctly for stale detection and revalidation
- `error-semantics`
  - Must map API, timeout, and unavailable-state failures appropriately
- `auth-issuer`
  - Must obtain and use tokens/configuration consistently with issuer expectations
- `ci-cd`
  - Must comply with plugin quality and release gates
- `testing`
  - Must meet plugin-side testing expectations
- `documentation`
  - Must keep repo docs accurate, coherent, and non-conflicting

## Local-Only Responsibilities
- Opening the main market GUI from `/market`
- Category browsing and back-navigation flow
- Trade-screen quantity selection and button behavior
- Debounced quote request orchestration
- Disabling buy/sell while quote state is pending or unavailable
- Tracking open viewers and pushing single-slot GUI refreshes where relevant
- Read-only degraded mode when API communication is unavailable but cached data exists
- Messaging around stale quotes, blocked items, and local inventory constraints

## Out of Scope
- Market pricing formulas
- Momentum and regeneration engine logic
- Durable market state ownership
- API-side rejection-code design
- API-side quote token generation/persistence
- Auth-server security-chain internals
- Deployment orchestration ownership

## Compliance Questions
- Does the plugin consume market snapshots, quotes, and execution results without redefining backend ownership?
- Is stale-mode clearly read-only and safe?
- Are debounced quote requests and stale quote responses handled correctly?
- Are live viewer updates limited to plugin-local UI concerns?
- Do docs keep the plugin/API boundary explicit?

## Success Signal
This repo is compliant when it behaves as a clean market client: responsive, cache-aware, GUI-first, and explicit about the fact that authoritative pricing, quote, and execution semantics belong to `craftalism-api`, not to the plugin.
