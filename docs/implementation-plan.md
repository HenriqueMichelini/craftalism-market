# Market Implementation Plan

## Ownership
This repository owns the plugin-side market client plan:
- command entry and GUI flow
- cache, stale mode, and viewer/session handling
- async orchestration and Bukkit/Paper thread safety
- local inventory checks and player-facing messaging

This repository consumes, and does not own:
- pricing engine behavior
- momentum and regeneration rules
- snapshot, quote, and execute contract authority
- rejection code and version/token semantics

Those consumed contracts must be defined in `craftalism-api`.

## Current State
Completed in this repository:
- root-level Paper plugin packaging
- `/market` command entry
- API-backed snapshot browsing with cached read-only fallback
- category and trade GUI flow
- debounced quote requests with session-bound stale protection
- buy execution using API-issued `quoteToken` and `snapshotVersion`
- plugin-local item delivery after successful buys, with overflow drops
- sell execution using plugin-local inventory validation before execute
- post-success local item removal after sell settlement
- rejection-code mapping using API `code`, not ad-hoc text
- session cleanup on inventory close and player quit

Current sell boundary:
- `craftalism-market` validates player inventory possession locally on the main thread before sell execute
- `craftalism-market` reduces requested sell quantity to the quantity actually held, or stops if the player has none
- `craftalism-api` remains authoritative for quote validity, pricing, and economic settlement
- `craftalism-market` removes sold items locally only after successful API execute
- any post-execute local-removal failure must be treated as a compensation-grade error

## Remaining Work
### Repo-Local Hardening
- broaden plugin-side tests around command entry, GUI/session transitions, stale quote refresh, and cleanup behavior
- improve compensation handling for post-execute local inventory failures
- add targeted post-trade snapshot refresh or live viewer updates where useful
- reconcile docs continuously as behavior evolves

### Cross-Repo Coordination
- keep aligned with `craftalism-api` as snapshot, quote, execute, and rejection semantics harden further
- confirm any future changes to sell ownership or compensation expectations before implementing them locally

## Reverify Checklist
- no main-thread blocking in API quote/execute paths
- stale mode remains read-only when cache is used
- plugin-side pricing math is still not introduced
- buy delivery and sell removal stay plugin-local
- async GUI updates only apply to the current player session state
