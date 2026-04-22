package io.github.henriquemichelini.craftalism.market.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class MarketSessionRegistry {
    private final Map<UUID, MarketSession> sessions = new ConcurrentHashMap<>();

    public void put(UUID playerId, MarketSession session) {
        sessions.put(playerId, session);
    }

    public Optional<MarketSession> replace(UUID playerId, MarketSession session) {
        return Optional.ofNullable(sessions.put(playerId, session));
    }

    public Optional<MarketSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public Optional<MarketSession> update(UUID playerId, UnaryOperator<MarketSession> updater) {
        return Optional.ofNullable(sessions.computeIfPresent(playerId, (ignored, session) -> updater.apply(session)));
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }
}
