package io.github.henriquemichelini.craftalism.market.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyValueFormatterTest {
    @Test
    void preservesMarketPriceDisplayUnits() {
        assertEquals("6", MoneyValueFormatter.normalize("6"));
        assertEquals("1000000", MoneyValueFormatter.normalize("1000000"));
    }

    @Test
    void normalizesDisplayDecimalsWithoutApplyingBalanceScaling() {
        assertEquals("19.8", MoneyValueFormatter.normalize("19.8"));
        assertEquals("19.8", MoneyValueFormatter.normalize("19.8000"));
        assertEquals("1.2346", MoneyValueFormatter.normalize("1.23456"));
        assertEquals("0.0001", MoneyValueFormatter.normalize("0.00006"));
    }
}
