package io.github.henriquemichelini.craftalism.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketPluginMetadataTest {
    @Test
    void exposesExpectedPluginIdentity() {
        assertEquals("Craftalism Market", MarketPluginMetadata.PLUGIN_NAME);
        assertEquals(
                "io.github.henriquemichelini.craftalism.market.MarketPlugin",
                MarketPluginMetadata.MAIN_CLASS
        );
    }
}
