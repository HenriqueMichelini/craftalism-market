package io.github.henriquemichelini.craftalism.market.browse;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketBrowseSnapshotServiceTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void returnsLiveSnapshotWhenApiFetchSucceeds() throws Exception {
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(
                () -> sampleSnapshot(false),
                DIRECT_EXECUTOR
        );

        MarketBrowseSnapshotLoadResult result = service.loadForInitialOpen().get();

        assertFalse(result.fromCache());
        assertFalse(result.snapshot().readOnly());
        assertFalse(service.currentSnapshot().orElseThrow().readOnly());
    }

    @Test
    void fallsBackToCachedSnapshotInReadOnlyMode() throws Exception {
        SwitchableProvider provider = new SwitchableProvider();
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(provider, DIRECT_EXECUTOR);

        service.loadForInitialOpen().get();
        provider.fail = true;

        MarketBrowseSnapshotLoadResult result = service.loadForInitialOpen().get();

        assertTrue(result.fromCache());
        assertTrue(result.snapshot().readOnly());
        assertFalse(service.currentSnapshot().orElseThrow().readOnly());
    }

    @Test
    void failsWithoutCacheWhenApiFetchFails() {
        MarketBrowseSnapshotService service = new MarketBrowseSnapshotService(
                () -> { throw new IllegalStateException("boom"); },
                DIRECT_EXECUTOR
        );

        ExecutionException error = assertThrows(ExecutionException.class, () -> service.loadForInitialOpen().get());
        assertEquals("boom", error.getCause().getMessage());
    }

    private static MarketBrowseSnapshot sampleSnapshot(boolean readOnly) {
        return new MarketBrowseSnapshot(List.of(
                new MarketCategorySnapshot(
                        "farming",
                        "Farming",
                        Material.WHEAT,
                        List.of(),
                        List.of(new MarketItemSnapshot(
                                "wheat",
                                "Wheat",
                                Material.WHEAT,
                                List.of(),
                                "4.8 coins",
                                "4.1 coins",
                                "2.3%",
                                "Stock: 1820",
                                "Fixture"
                        ))
                )
        ), readOnly);
    }

    private static final class SwitchableProvider implements MarketBrowseSnapshotProvider {
        private boolean fail;

        @Override
        public MarketBrowseSnapshot loadSnapshot() {
            if (fail) {
                throw new IllegalStateException("boom");
            }

            return sampleSnapshot(false);
        }
    }
}
