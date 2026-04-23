package io.github.henriquemichelini.craftalism.market.gui;

import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import io.github.henriquemichelini.craftalism.market.inventory.MarketInventoryAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class MarketSettlementService {

    private final Plugin plugin;
    private final MarketInventoryAccess inventoryService;
    private final MarketGuiRenderer renderer;
    private final Map<UUID, List<MarketGuiService.DeferredSettlement>> deferredSettlements =
        new ConcurrentHashMap<>();
    private final DeferredSettlementStore deferredSettlementStore;

    MarketSettlementService(
        Plugin plugin,
        MarketInventoryAccess inventoryService,
        MarketGuiRenderer renderer
    ) {
        this.plugin = plugin;
        this.inventoryService = inventoryService;
        this.renderer = renderer;
        this.deferredSettlementStore = createDeferredSettlementStore(plugin);
        deferredSettlements.putAll(loadDeferredSettlements());
    }

    void queueDeferredSettlement(
        UUID playerId,
        MarketGuiService.DeferredSettlement settlement
    ) {
        deferredSettlements.compute(playerId, (ignored, current) -> {
            List<MarketGuiService.DeferredSettlement> updated =
                current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.add(settlement);
            return List.copyOf(updated);
        });
        persistDeferredSettlements();

        if (plugin != null) {
            plugin
                .getLogger()
                .warning(
                    "Deferred market " +
                        settlement.side().name().toLowerCase() +
                        " settlement for player " +
                        playerId +
                        " until they reconnect."
                );
        }
    }

    int deferredSettlementCount(UUID playerId) {
        return deferredSettlements.getOrDefault(playerId, List.of()).size();
    }

    void applyDeferredSettlements(Player player) {
        List<MarketGuiService.DeferredSettlement> settlements =
            deferredSettlements.remove(player.getUniqueId());
        if (settlements == null || settlements.isEmpty()) {
            return;
        }

        List<MarketGuiService.DeferredSettlement> remaining =
            new ArrayList<>();
        for (MarketGuiService.DeferredSettlement settlement : settlements) {
            if (settlement.side() == MarketQuoteSide.BUY) {
                if (
                    !applyBuySettlement(
                        player,
                        settlement.material(),
                        settlement.result()
                    )
                ) {
                    remaining.add(settlement);
                    logDeferredSettlementStillPending(
                        player.getUniqueId(),
                        settlement
                    );
                } else {
                    logDeferredSettlementApplied(
                        player.getUniqueId(),
                        settlement
                    );
                }
                continue;
            }

            if (
                inventoryService.count(player, settlement.material()) <
                settlement.result().executedQuantity()
            ) {
                reportSellRemovalFailure(
                    player,
                    settlement.material(),
                    settlement.result(),
                    0
                );
                remaining.add(settlement);
                logDeferredSettlementStillPending(
                    player.getUniqueId(),
                    settlement
                );
                continue;
            }

            if (
                !applySellSettlement(
                    player,
                    settlement.material(),
                    settlement.result()
                )
            ) {
                remaining.add(settlement);
                logDeferredSettlementStillPending(
                    player.getUniqueId(),
                    settlement
                );
            } else {
                logDeferredSettlementApplied(player.getUniqueId(), settlement);
            }
        }

        if (!remaining.isEmpty()) {
            deferredSettlements.put(
                player.getUniqueId(),
                List.copyOf(remaining)
            );
        }
        persistDeferredSettlements();
    }

    boolean applyBuySettlement(
        Player player,
        Material material,
        MarketExecuteResult result
    ) {
        int delivered = inventoryService.addOrDrop(
            player,
            material,
            result.executedQuantity()
        );
        if (delivered < result.executedQuantity()) {
            renderer.sendMessage(
                player,
                renderer
                    .message("messages.buy-overflow")
                    .replace(
                        "{quantity}",
                        Integer.toString(result.executedQuantity())
                    )
                    .replace(
                        "{total}",
                        result.totalPrice() + " " + result.currency()
                    )
                    .replace(
                        "{dropped}",
                        Integer.toString(result.executedQuantity() - delivered)
                    )
            );
            return true;
        }

        renderer.sendMessage(
            player,
            renderer
                .message("messages.buy-success")
                .replace(
                    "{quantity}",
                    Integer.toString(result.executedQuantity())
                )
                .replace(
                    "{total}",
                    result.totalPrice() + " " + result.currency()
                )
        );
        return true;
    }

    boolean applySellSettlement(
        Player player,
        Material material,
        MarketExecuteResult result
    ) {
        int removed = inventoryService.remove(
            player,
            material,
            result.executedQuantity()
        );
        if (removed != result.executedQuantity()) {
            reportSellRemovalFailure(player, material, result, removed);
            return false;
        }

        renderer.sendMessage(
            player,
            renderer
                .message("messages.sell-success")
                .replace(
                    "{quantity}",
                    Integer.toString(result.executedQuantity())
                )
                .replace(
                    "{total}",
                    result.totalPrice() + " " + result.currency()
                )
        );
        return true;
    }

    String sellRemovalFailureLogMessage(
        UUID playerId,
        Material material,
        MarketExecuteResult result,
        int removedQuantity
    ) {
        return renderer
            .message("messages.sell-removal-failed-log")
            .replace("{playerId}", playerId.toString())
            .replace("{item}", material.name())
            .replace("{executed}", Integer.toString(result.executedQuantity()))
            .replace("{removed}", Integer.toString(removedQuantity))
            .replace(
                "{missing}",
                Integer.toString(
                    Math.max(0, result.executedQuantity() - removedQuantity)
                )
            )
            .replace("{total}", result.totalPrice() + " " + result.currency())
            .replace("{snapshotVersion}", result.snapshotVersion());
    }

    String sellRemovalFailurePlayerMessage(
        Material material,
        MarketExecuteResult result,
        int removedQuantity
    ) {
        return renderer
            .message("messages.sell-removal-failed")
            .replace("{item}", material.name())
            .replace("{executed}", Integer.toString(result.executedQuantity()))
            .replace("{removed}", Integer.toString(removedQuantity))
            .replace(
                "{missing}",
                Integer.toString(
                    Math.max(0, result.executedQuantity() - removedQuantity)
                )
            )
            .replace("{total}", result.totalPrice() + " " + result.currency());
    }

    private void reportSellRemovalFailure(
        Player player,
        Material material,
        MarketExecuteResult result,
        int removed
    ) {
        if (plugin != null) {
            plugin
                .getLogger()
                .severe(
                    sellRemovalFailureLogMessage(
                        player.getUniqueId(),
                        material,
                        result,
                        removed
                    )
                );
        }
        renderer.sendMessage(
            player,
            sellRemovalFailurePlayerMessage(material, result, removed)
        );
    }

    private void logDeferredSettlementApplied(
        UUID playerId,
        MarketGuiService.DeferredSettlement settlement
    ) {
        if (plugin == null) {
            return;
        }

        plugin
            .getLogger()
            .info(
                "Applied deferred market " +
                    settlement.side().name().toLowerCase() +
                    " settlement for player " +
                    playerId +
                    ": item=" +
                    settlement.material().name() +
                    ", executed=" +
                    settlement.result().executedQuantity() +
                    ", settled=" +
                    settlement.result().totalPrice() +
                    " " +
                    settlement.result().currency() +
                    ", snapshotVersion=" +
                    settlement.result().snapshotVersion()
            );
    }

    private void logDeferredSettlementStillPending(
        UUID playerId,
        MarketGuiService.DeferredSettlement settlement
    ) {
        if (plugin == null) {
            return;
        }

        plugin
            .getLogger()
            .severe(
                "Deferred market " +
                    settlement.side().name().toLowerCase() +
                    " settlement remains queued for player " +
                    playerId +
                    ": item=" +
                    settlement.material().name() +
                    ", executed=" +
                    settlement.result().executedQuantity() +
                    ", settled=" +
                    settlement.result().totalPrice() +
                    " " +
                    settlement.result().currency() +
                    ", snapshotVersion=" +
                    settlement.result().snapshotVersion()
            );
    }

    private DeferredSettlementStore createDeferredSettlementStore(
        Plugin plugin
    ) {
        if (plugin == null || plugin.getDataFolder() == null) {
            return null;
        }

        Logger logger =
            plugin.getLogger() != null
                ? plugin.getLogger()
                : Logger.getLogger(MarketSettlementService.class.getName());
        return new DeferredSettlementStore(
            plugin
                .getDataFolder()
                .toPath()
                .resolve("deferred-settlements.json"),
            logger
        );
    }

    private Map<UUID, List<MarketGuiService.DeferredSettlement>> loadDeferredSettlements() {
        if (deferredSettlementStore == null) {
            return Map.of();
        }

        return new HashMap<>(deferredSettlementStore.load());
    }

    private void persistDeferredSettlements() {
        if (deferredSettlementStore == null) {
            return;
        }

        deferredSettlementStore.save(deferredSettlements);
    }
}
