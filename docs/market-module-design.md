# Market Module Design

## Ownership
This repository owns the plugin-local market client behavior:
- `/market` command entry
- GUI navigation and rendering
- plugin-local cache/session/viewer behavior
- async orchestration and main-thread UI application
- player-facing stale and error messaging
- local inventory inspection and delivery behavior

This repository consumes, and does not own:
- authoritative market snapshots
- pricing logic
- momentum and regeneration logic
- quote validity rules
- buy/sell execution authority
- rejection code definitions

Those behaviors belong to `craftalism-api`.

## Primary Entry Point
- Player executes `/market`
- Plugin opens the Market Main GUI
- The module is command-first; API and event handling support this flow

## Screen Model
### Main Category GUI
- Shows market categories as configured icon items
- Uses cached category snapshots for display
- Supports hover-driven lore and future category metadata
- Click opens the category item GUI

### Category Items GUI
- Shows all items for the selected category
- Includes a back button at the bottom-center slot
- Hover shows item display fields from the latest available snapshot:
  - display name
  - current buy estimate
  - current sell estimate
  - recent variation percent
  - stock-related display information when available
- Click opens the trade GUI for that item

### Trade GUI
- Combined buy/sell screen for v1
- Uses fixed increment/decrement controls in code
- Preserves selected quantity while the screen remains open
- Shows:
  - item name
  - selected quantity
  - quoted or estimated buy total
  - quoted or estimated sell total
  - unit pricing summary
  - stock state
  - quote/update status line
- Buy and sell buttons are visibly disabled when:
  - item is blocked
  - stale mode is active
  - quote is pending
  - quote refresh failed

## Config-Driven vs Code-Driven Behavior
### Configurable in v1
- categories
- icon/material mapping
- lore/messages
- slot layout for category and item-browse screens
- default fallback items

### Not Configurable in v1
- increment/decrement amounts
- trade GUI slot layout

## Snapshot Read Model
The plugin should be able to render an item from a snapshot containing:
- item id
- display name
- category
- icon/material key or item mapping key
- current buy unit estimate
- current sell unit estimate
- current stock
- baseline stock
- momentum direction and magnitude
- recent variation percent
- last updated timestamp
- flags such as operating or blocked

The plugin must not infer backend pricing rules from these fields.

## Session and Viewer State
The plugin keeps an in-memory viewer registry keyed by player UUID.

Each market session stores:
- current screen type
- selected category
- selected item id
- selected quantity
- last snapshot version or timestamp used to render
- stale-mode flag
- GUI instance reference

On GUI close or player quit, the session is removed immediately.

## Cache and Refresh Strategy
### Cache Role
- Cache category and item snapshots for browsing and fallback display
- Do not treat cache as authoritative for trading

### Refresh Triggers
- scheduled background refresh for market snapshots
- manual refresh action from GUI
- forced refresh when entering a category if snapshot age exceeds a threshold
- targeted refresh after successful trade for the traded item and possibly its category
- no full refresh on every hover or click

### Live Viewer Updates
When a trade succeeds:
- plugin receives an updated authoritative item snapshot in the trade response, or fetches that item immediately
- plugin updates local cache
- plugin pushes a single-slot GUI refresh to viewers currently seeing that item or category
- scheduled refresh remains fallback for missed updates

## Degraded Mode
If the plugin cannot communicate with the API:
- browsing screens may open only if cached data exists
- GUI must clearly warn that values are cached and may be outdated
- trading actions are blocked fully
- trade screens may remain informational, but buy/sell are not executable
- if no cache exists, the plugin should show a friendly unavailable message instead of opening a broken GUI

## Quote Flow
### Rules
- GUI snapshots are informative
- API quotes and execution results are authoritative
- the plugin must not compute multi-band or momentum-sensitive totals locally

### Debounced Quote Behavior
- request a quote only when selected quantity changes
- debounce requests around 250ms to 500ms
- cancel or supersede older pending quote work for the same session
- ignore quote responses that do not match the latest session state
- never block the main thread waiting for a quote

### While Quote Is Pending
- keep selected quantity visible
- show previous totals as stale or replace them with `updating...`
- disable buy/sell buttons
- show a small status line such as `Refreshing quote...`
- keep the rest of the GUI interactive

### Quote Validity
- quote response should include `snapshotVersion` and `quoteToken`
- execute request should send them back
- API may reject with `STALE_QUOTE`
- on stale rejection, the plugin keeps the trade GUI open, refreshes displayed values, preserves quantity, and requires explicit re-confirmation

## Trade Execution Behavior
### Selling
- player chooses quantity locally first
- plugin inspects current player inventory on the main thread before execute
- if player has fewer items than selected, plugin reduces the sell quantity to the amount actually held
- plugin refreshes displayed totals/messages accordingly before execution

### Buying
- plugin relies on API quote/execute for authoritative market totals
- after a successful buy, if player inventory lacks space, remaining items may be dropped naturally according to plugin-local delivery rules, with a clear player message

### When Item Updates Mid-Screen
If a live update arrives while a trade screen is open:
- refresh the displayed snapshot in place
- preserve selected quantity
- mark that pricing was refreshed
- disable actions immediately if the item becomes blocked
- if a click is rejected because the snapshot changed, cancel only the pending click attempt, keep the GUI open, refresh values, and ask the player to confirm again

## Rejection and Message Mapping
The plugin should consume stable API rejection codes and map them to configurable player-friendly messages.

Expected v1 examples:
- `STALE_QUOTE`
- `ITEM_BLOCKED`
- `INSUFFICIENT_STOCK`
- `INSUFFICIENT_FUNDS`
- `MARKET_CLOSED`
- `INVALID_QUANTITY`
- `RATE_LIMITED`
- `API_UNAVAILABLE`

The plugin should not parse ad-hoc API text.

## Thread Model
- API calls, snapshot refreshes, quote requests, and trade execution run asynchronously
- GUI reads/writes, inventory inspection, and player messaging run on the main server thread
- every async response is checked against the player’s current session before mutating the GUI
- command handlers and click handlers must never block waiting for remote work

## Suggested API Docs Needed Outside This Repo
Implementation should not proceed past plugin-local scaffolding until `craftalism-api` defines:
- snapshot payload contract
- quote payload contract
- execute payload contract
- rejection-code contract
- `snapshotVersion` semantics
- `quoteToken` semantics
- blocked/operating flag semantics
