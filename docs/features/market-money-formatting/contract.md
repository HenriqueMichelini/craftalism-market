# Market Money Formatting Contract

## Ownership

`craftalism-market` owns only plugin-side presentation of money values in player messages, operator logs, and GUI displays. Authoritative price precision, raw economy units, quote semantics, execution semantics, and persistence contracts are consumed from the market API.

## Behavior

- Market display and GUI values may preserve the existing display precision used by market snapshots, quotes, and trade screens.
- Player-facing settlement messages and operator settlement logs should present money totals with two decimal places.
- Formatting changes must not alter API payloads, stored settlement values, quote values, execution values, or backend precision.

