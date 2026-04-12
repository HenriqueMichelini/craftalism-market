package io.github.henriquemichelini.craftalism.market.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketSessionRegistryTest {
    @Test
    void storesAndRemovesSessions() {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        MarketSession session = new MarketSession(MarketScreen.CATEGORY_LIST, null, null, true);

        registry.put(playerId, session);
        assertEquals(session, registry.get(playerId).orElseThrow());

        registry.remove(playerId);
        assertFalse(registry.get(playerId).isPresent());
    }
}
