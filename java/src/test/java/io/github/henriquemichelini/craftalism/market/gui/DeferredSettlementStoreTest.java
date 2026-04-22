package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeferredSettlementStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void saveWritesLoadableSettlementWithoutLeavingTempFile() {
        Path path = tempDir.resolve("deferred-settlements.json");
        DeferredSettlementStore store = new DeferredSettlementStore(
            path,
            Logger.getAnonymousLogger()
        );
        UUID playerId = UUID.randomUUID();
        MarketGuiService.DeferredSettlement settlement =
            new MarketGuiService.DeferredSettlement(
                MarketQuoteSide.SELL,
                Material.WHEAT,
                new MarketExecuteResult(
                    4,
                    "16.4",
                    "4.1",
                    "coins",
                    "snapshot-v7"
                )
            );

        store.save(Map.of(playerId, List.of(settlement)));

        assertEquals(Map.of(playerId, List.of(settlement)), store.load());
        assertFalse(Files.exists(tempDir.resolve("deferred-settlements.json.tmp")));
    }
}
