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

The next step is hardening, not another new feature slice.

Highest-value next implementation:
- add targeted post-trade refresh behavior so successful trades refresh the affected item/category snapshot instead of waiting for the next normal cycle

Why this comes next:
- it improves correctness after buy/sell success
- it improves player-facing UX immediately after trade settlement
- it is already consistent with the market module design
- it does not require redefining backend ownership

## Recommended Implementation Order

### 1. Post-Trade Refresh
Implement immediate repo-local refresh behavior after successful buy/sell execution.

Target behavior:
- after a successful trade, refresh the affected item/category snapshot
- update local cached snapshot state
- re-render the current player trade screen with the refreshed snapshot
- preserve selected quantity where possible
- if the item becomes blocked or unavailable, disable actions immediately

Preferred scope:
- start with targeted refresh for the current player only
- defer broader live-viewer fanout until the refresh path is stable

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

### 3. Compensation Hardening
Improve handling of rare partial-failure paths.

Priority areas:
- API sell succeeds but local item removal removes fewer items than expected
- clearer operator logging for compensation-grade failures
- clearer player-facing messaging for local post-settlement failures

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
1. add targeted post-trade snapshot refresh for the current player
2. add tests around that refresh path and stale-quote handling
3. harden compensation logging and messages for post-settlement local failures
