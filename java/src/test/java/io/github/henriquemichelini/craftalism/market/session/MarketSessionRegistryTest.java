package io.github.henriquemichelini.craftalism.market.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSessionRegistryTest {
    @Test
    void storesAndRemovesSessions() {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        MarketSession session = MarketSession.categoryList(true);

        registry.put(playerId, session);
        assertEquals(session, registry.get(playerId).orElseThrow());

        registry.remove(playerId);
        assertFalse(registry.get(playerId).isPresent());
    }

    @Test
    void replaceReturnsPreviousSession() {
        MarketSessionRegistry registry = new MarketSessionRegistry();
        UUID playerId = UUID.randomUUID();
        MarketSession previous = MarketSession.tradeView("farming", "wheat", false);
        MarketSession replacement = MarketSession.categoryList(false);

        assertFalse(registry.replace(playerId, previous).isPresent());

        var replaced = registry.replace(playerId, replacement);

        assertTrue(replaced.isPresent());
        assertEquals(previous, replaced.orElseThrow());
        assertEquals(replacement, registry.get(playerId).orElseThrow());
    }
}
