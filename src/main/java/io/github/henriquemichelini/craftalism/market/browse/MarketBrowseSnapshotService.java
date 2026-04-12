package io.github.henriquemichelini.craftalism.market.browse;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class MarketBrowseSnapshotService {
    private final MarketBrowseSnapshotProvider liveSnapshotProvider;
    private final Executor asyncExecutor;
    private final AtomicReference<MarketBrowseSnapshot> cachedSnapshot = new AtomicReference<>();

    public MarketBrowseSnapshotService(MarketBrowseSnapshotProvider liveSnapshotProvider, Executor asyncExecutor) {
        this.liveSnapshotProvider = liveSnapshotProvider;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<MarketBrowseSnapshotLoadResult> loadForInitialOpen() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MarketBrowseSnapshot liveSnapshot = liveSnapshotProvider.loadSnapshot().withReadOnly(false);
                cachedSnapshot.set(liveSnapshot);
                return new MarketBrowseSnapshotLoadResult(liveSnapshot, false);
            } catch (RuntimeException error) {
                MarketBrowseSnapshot cached = cachedSnapshot.get();
                if (cached != null) {
                    return new MarketBrowseSnapshotLoadResult(cached.withReadOnly(true), true);
                }

                throw error;
            }
        }, asyncExecutor);
    }

    public Optional<MarketBrowseSnapshot> currentSnapshot() {
        return Optional.ofNullable(cachedSnapshot.get());
    }
}
