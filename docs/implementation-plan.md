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

### 1.1 Current API Follow-Up Status
`craftalism-api` has completed the MVP contract implementation and identified the next hardening steps:
- replace bootstrap/seed catalog logic with a real catalog source
- decide whether in-memory quote storage is acceptable beyond MVP
- add integration coverage for quote/execute success and rejection paths
- clarify sell-boundary ownership for inventory possession validation
- coordinate exact contract adoption in `craftalism-market`

Implication for this repository:
- the plugin can begin consumer adoption of snapshot, quote, and execute contracts
- sell execution must still stop at the ownership boundary until inventory validation responsibilities are confirmed

### 2. Resolved Decisions For The First Slice
The first implementation slice is limited to repo-local scaffolding and read-only browsing.

Resolved decisions:
- if no cached snapshot data exists and the API is unavailable, do not open any GUI; send a clear unavailable message and stop
- if cached data exists and the API is unavailable, allow category, item, and trade browsing in informational mode, but disable buy/sell and clearly mark values as cached/outdated
- use a small local data-provider interface backed by plugin resources/config instead of a real API client for the first slice
- store sample browsing data in plugin config/resources, not hardcoded Java constants
- always open the main category GUI first, even when only one category exists
- defer live viewer push updates for the first slice; refresh happens only on reopen
- reopening `/market` is the only refresh path in the first slice; no clickable refresh control yet

Constraints preserved by these decisions:
- stale mode stays strictly read-only
- no API transport or payload semantics are guessed locally
- GUI, cache, and session behavior can be implemented and tested without cross-repo contract drift

### 3. Scaffold the Plugin Module Locally
In this repository, create the initial plugin-side structure for:
- `/market` command entry
- GUI screen classes
- session/viewer registry
- cache interfaces and models
- async orchestration layer
- config files for categories, icons, lore, and fallback items

### 4. Implement Read-Only Browsing First
Before executable trading:
- open the main market GUI
- navigate categories
- render cached snapshots
- support stale-mode browsing
- add refresh hooks
- wire viewer/session cleanup

For the first slice specifically:
- back the browsing flow with resource/config-backed fixture data
- treat that local data as the only snapshot source until `craftalism-api` contracts are finalized
- allow the trade GUI to open as an informational screen only
- keep buy/sell disabled throughout this slice
- use reopening `/market` as the only refresh behavior

### 5. Add Quote Flow
After the API quote contract is stable:
- quantity selection
- debounced quote requests
- pending-quote disabled state
- stale-quote refresh behavior

Implementation notes for this repository:
- replace the fixture-backed browse provider with a real API-backed snapshot client behind the existing local provider interface
- keep cached snapshot browsing as degraded-mode fallback when API calls fail
- store the latest `quoteToken` and `snapshotVersion` in the active player trade session
- ignore async quote responses that no longer match the current item, quantity, or open screen
- never compute authoritative totals locally from snapshot data

### 6. Add Execution Flow Last
Only after quote and execute semantics are stable:
- buy/sell execution
- inventory checks
- full-inventory handling on buy
- sell-quantity correction on sell
- live single-slot updates for relevant viewers

Execution boundary notes:
- execute only with API-issued `quoteToken`
- map rejection `code`, not free-form `message`
- buy flow may proceed once quote and execute semantics are stable
- sell flow must not assume API ownership of inventory possession validation if that responsibility remains plugin-local
- before enabling sells, explicitly confirm whether:
  - the plugin validates and removes items locally before execute, or
  - another repository/service owns that possession check

### 6.1 Consumer Adoption Sequence In `craftalism-market`
Recommended local order:
1. introduce a real market client abstraction and snapshot-fetch implementation
2. add cache-backed degraded browsing around snapshot fetch failures
3. keep the existing read-only browsing UX as the fallback path
4. implement quantity selection and debounced quote flow in the trade GUI
5. persist current `quoteToken` and `snapshotVersion` in the player session
6. add rejection-code mapping and stale/expired quote handling
7. implement buy execution
8. implement sell execution only after inventory-boundary ownership is confirmed

### 6.2 Cross-Repo Workflow Between `craftalism-market` And `craftalism-api`
1. `craftalism-api` publishes and stabilizes snapshot, quote, execute, rejection, and actor-resolution semantics
2. `craftalism-market` consumes snapshot browsing first and keeps degraded mode explicit and read-only
3. `craftalism-api` hardens quote and execute behavior with integration coverage
4. `craftalism-market` adopts quote flow exactly as documented, storing and replaying `quoteToken` and `snapshotVersion`
5. `craftalism-market` adopts execute flow exactly as documented, mapping rejection code instead of message
6. both repositories explicitly confirm sell-boundary ownership before enabling sell execution
7. both repositories reverify that no plugin-local pricing or backend contract drift was introduced

### 7. Reverify
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
