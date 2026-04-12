package io.github.henriquemichelini.craftalism.market.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketSessionRegistry {
    private final Map<UUID, MarketSession> sessions = new ConcurrentHashMap<>();

    public void put(UUID playerId, MarketSession session) {
        sessions.put(playerId, session);
    }

    public Optional<MarketSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }
}
