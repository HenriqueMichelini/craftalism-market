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

## Next Plan
### 1. Define the API Contract in `craftalism-api`
Create the missing API-side contract documentation for:
- market snapshots
- quote request/response
- execute request/response
- rejection codes
- `snapshotVersion`
- `quoteToken`

Do not implement plugin trade behavior against guessed backend semantics.

### 2. Scaffold the Plugin Module Locally
In this repository, create the initial plugin-side structure for:
- `/market` command entry
- GUI screen classes
- session/viewer registry
- cache interfaces and models
- async orchestration layer
- config files for categories, icons, lore, and fallback items

### 3. Implement Read-Only Browsing First
Before executable trading:
- open the main market GUI
- navigate categories
- render cached snapshots
- support stale-mode browsing
- add refresh hooks
- wire viewer/session cleanup

### 4. Add Quote Flow
After the API quote contract is stable:
- quantity selection
- debounced quote requests
- pending-quote disabled state
- stale-quote refresh behavior

### 5. Add Execution Flow Last
Only after quote and execute semantics are stable:
- buy/sell execution
- inventory checks
- full-inventory handling on buy
- sell-quantity correction on sell
- live single-slot updates for relevant viewers

### 6. Reverify
Check that:
- no main-thread blocking was introduced
- stale mode remains read-only
- plugin-side pricing math was not introduced
- sessions are cleaned up on GUI close and player quit
- async GUI updates only apply to the current player session state

## Suggested Resume Point
When work resumes, start with:
1. API contract alignment in `craftalism-api`
2. plugin-local scaffolding in this repository
