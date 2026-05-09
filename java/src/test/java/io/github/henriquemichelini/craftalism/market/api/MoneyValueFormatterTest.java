package io.github.henriquemichelini.craftalism.market.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyValueFormatterTest {
    @Test
    void convertsEconomyRawUnitsToDisplayUnits() {
        assertEquals("0.0014", MoneyValueFormatter.normalize("14"));
        assertEquals("1", MoneyValueFormatter.normalize("10000"));
        assertEquals("100", MoneyValueFormatter.normalize("1000000"));
        assertEquals("10000", MoneyValueFormatter.normalize("100000000"));
    }

    @Test
    void normalizesScaledDisplayDecimals() {
        assertEquals("19.8", MoneyValueFormatter.normalize("198000"));
        assertEquals("19.8", MoneyValueFormatter.normalize("198000.0000"));
        assertEquals("1.2346", MoneyValueFormatter.normalize("12345.6"));
        assertEquals("0.0001", MoneyValueFormatter.normalize("1"));
    }

    @Test
    void formatsMessageAmountsWithFixedTwoDecimalPlaces() {
        assertEquals("19.80", MoneyValueFormatter.formatMessageAmount("19.8"));
        assertEquals("1.23", MoneyValueFormatter.formatMessageAmount("1.234"));
        assertEquals("1.24", MoneyValueFormatter.formatMessageAmount("1.235"));
        assertEquals("0.00", MoneyValueFormatter.formatMessageAmount("0.0014"));
    }

    @Test
    void leavesInvalidMessageAmountsUnchanged() {
        assertEquals("", MoneyValueFormatter.formatMessageAmount(""));
        assertEquals("Unavailable", MoneyValueFormatter.formatMessageAmount("Unavailable"));
    }
}
