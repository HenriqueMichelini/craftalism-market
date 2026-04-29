# Next Implementation Steps

## Current Repository State

`craftalism-market` currently implements:
- `/market` command entry
- API-backed snapshot browsing with cached read-only fallback
- category and trade GUI navigation
- debounced quote requests with session-bound stale protection
- buy execution using API-issued `quoteToken` and `snapshotVersion`
- plugin-local item delivery after successful buys, including overflow drops
- sell execution using plugin-local inventory validation before execute
- post-success local item removal after sell settlement
- rejection-code mapping using API `code`
- session cleanup on inventory close and player quit

## Confirmed Next Priority

The next step is still hardening, not another new feature slice.

Targeted post-trade refresh is already implemented in the plugin, including current-player trade-screen refresh after successful buys and sells.

Highest-value next implementation:
- harden post-settlement compensation and reconnect behavior when trade execution succeeds while the player is offline or local inventory application fails

Why this comes next:
- it closes the remaining correctness gap after authoritative trade settlement
- it keeps plugin-local delivery and removal behavior aligned with repo ownership
- it avoids redefining API-side trade authority or quote semantics

## Recommended Implementation Order

### 1. Compensation Hardening
Strengthen the plugin-local settlement path for rare disconnect and partial-failure cases.

Priority areas:
- buy execution succeeds while the player is offline before local delivery runs
- sell execution succeeds while the player is offline before local removal runs
- operator-visible logging when local post-settlement application is deferred or incomplete
- tests for reconnect-time settlement application

### 2. GUI/Session Hardening
Add stronger coverage around current plugin-local behavior.

Priority areas:
- buy click flow
- sell click flow
- stale quote rejection refresh behavior
- expired quote refresh behavior
- session replacement when players change quantity rapidly
- cleanup on inventory close and player quit
- degraded-mode behavior when cached data exists and the API is unavailable

### 3. Viewer Refresh Expansion
Only after the settlement path is stable, consider broader live-viewer fanout.

Priority areas:
- refreshing other open viewers of the same item/category after a successful trade
- keeping fanout bounded to plugin-local UI concerns only

## Explicitly Not Next

Do not prioritize these before the hardening work above:
- new command surfaces
- broad GUI redesign
- moving market authority into the plugin
- guessing new API semantics locally

## Repo Boundary Reminder

This repository owns:
- plugin-local GUI behavior
- async orchestration
- cached fallback behavior
- local inventory inspection/removal/delivery behavior
- player-facing messaging

This repository consumes and must not redefine:
- snapshot semantics
- quote semantics
- execute semantics
- rejection-code semantics
- version/token semantics

## Suggested Resume Point

When implementation resumes, start with:
1. add or refine tests around reconnect-time settlement and stale-quote handling
2. harden any remaining compensation logging and post-settlement failure messaging
3. evaluate whether bounded live-viewer fanout is worth adding after the settlement path is stable
