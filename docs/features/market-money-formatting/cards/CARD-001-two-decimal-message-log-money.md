# CARD-001: Format Market Message And Log Money Totals

## Status

completed

## Objective

Display settlement money totals with two decimal places in player messages and operator logs while preserving existing GUI and market display precision.

## Context

The plugin currently normalizes API money values to display units with up to four decimal places. That precision should remain available for market displays and GUI values, but settlement messages and settlement logs should show fixed two-decimal totals such as `19.80 coins`.

## Required Reading

- `../contract.md`
- `../../../../AGENTS.md`

## Expected Behavior

Buy, sell, overflow, compensation, and deferred-settlement messages/logs render monetary totals with exactly two decimal places. Market displays and GUI quote/display values continue using the existing formatter behavior.

## Acceptance Criteria

- [x] Buy and sell settlement player messages replace `{total}` with a two-decimal money amount plus currency.
- [x] Sell compensation player messages and logs replace `{total}` with a two-decimal money amount plus currency.
- [x] Deferred settlement applied/pending logs use two-decimal money amounts.
- [x] Existing market display and GUI precision behavior remains unchanged.
- [x] Tests cover two-decimal message/log formatting and unchanged display normalization.

## Expected Files to Change

```text
java/src/main/java/io/github/henriquemichelini/craftalism/market/api/MoneyValueFormatter.java
java/src/main/java/io/github/henriquemichelini/craftalism/market/gui/MarketSettlementService.java
java/src/test/java/io/github/henriquemichelini/craftalism/market/api/MoneyValueFormatterTest.java
java/src/test/java/io/github/henriquemichelini/craftalism/market/gui/MarketGuiServiceTest.java
```

## Constraints

- Do not change API parsing precision or payload semantics.
- Do not change market display or GUI quote/display formatting.
- Do not introduce unrelated GUI, command, or API behavior changes.

## Validation Commands

```bash
rtk ./gradlew --no-daemon test --tests io.github.henriquemichelini.craftalism.market.api.MoneyValueFormatterTest --tests io.github.henriquemichelini.craftalism.market.gui.MarketGuiServiceTest
```

Fallback if targeted Gradle filtering is unavailable:

```bash
rtk ./gradlew --no-daemon test
```

## Out of Scope

- Changing backend price precision or API contracts.
- Changing market display or GUI quote precision.
- Changing config message text.
- Broader settlement, inventory, or session refactors.

## Completion Notes

- Added a separate message/log money formatter that renders normalized money amounts with exactly two decimal places.
- Applied the formatter only to settlement player messages and operator settlement logs.
- Preserved existing market display and GUI formatter behavior.
- Validation passed: `rtk ./gradlew --no-daemon test --tests io.github.henriquemichelini.craftalism.market.api.MoneyValueFormatterTest --tests io.github.henriquemichelini.craftalism.market.gui.MarketGuiServiceTest`.
