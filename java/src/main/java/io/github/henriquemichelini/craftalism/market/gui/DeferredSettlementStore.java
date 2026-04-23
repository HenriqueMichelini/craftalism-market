package io.github.henriquemichelini.craftalism.market.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.henriquemichelini.craftalism.market.api.MarketExecuteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;

final class DeferredSettlementStore {
    private static final int MONEY_SCALE = 4;

    private final Path path;
    private final Logger logger;

    DeferredSettlementStore(Path path, Logger logger) {
        this.path = path;
        this.logger = logger;
    }

    Map<UUID, List<MarketGuiService.DeferredSettlement>> load() {
        if (!Files.exists(path)) {
            return Map.of();
        }

        try {
            JsonObject root = JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            JsonArray settlements = root.has("settlements") &&
                root.get("settlements").isJsonArray()
                ? root.getAsJsonArray("settlements")
                : new JsonArray();
            Map<UUID, List<MarketGuiService.DeferredSettlement>> loaded =
                new HashMap<>();
            for (JsonElement element : settlements) {
                JsonObject settlement = element.getAsJsonObject();
                UUID playerId = UUID.fromString(requiredString(settlement, "playerId"));
                loaded.computeIfAbsent(playerId, ignored -> new ArrayList<>())
                    .add(parseSettlement(settlement));
            }
            return copy(loaded);
        } catch (RuntimeException | IOException error) {
            logger.warning(
                "Unable to load deferred market settlements from " +
                    path +
                    ": " +
                    error.getMessage()
            );
            return Map.of();
        }
    }

    void save(Map<UUID, List<MarketGuiService.DeferredSettlement>> settlements) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            settlements.forEach((playerId, playerSettlements) ->
                playerSettlements.forEach(settlement ->
                    array.add(toJson(playerId, settlement))
                )
            );
            root.add("settlements", array);
            writeAtomically(root.toString());
        } catch (IOException error) {
            logger.severe(
                "Unable to persist deferred market settlements to " +
                    path +
                    ": " +
                    error.getMessage()
            );
        }
    }

    private void writeAtomically(String content) throws IOException {
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tempPath, content, StandardCharsets.UTF_8);
        try {
            Files.move(
                tempPath,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(
                tempPath,
                path,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException error) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    private MarketGuiService.DeferredSettlement parseSettlement(
        JsonObject settlement
    ) {
        JsonObject result = settlement.getAsJsonObject("result");
        return new MarketGuiService.DeferredSettlement(
            MarketQuoteSide.valueOf(requiredString(settlement, "side")),
            Material.valueOf(requiredString(settlement, "material")),
            new MarketExecuteResult(
                requiredInt(result, "executedQuantity"),
                normalizeMoney(requiredString(result, "totalPrice")),
                normalizeMoney(requiredString(result, "unitPrice")),
                requiredString(result, "currency"),
                requiredString(result, "snapshotVersion")
            )
        );
    }

    private JsonObject toJson(
        UUID playerId,
        MarketGuiService.DeferredSettlement settlement
    ) {
        JsonObject object = new JsonObject();
        object.addProperty("playerId", playerId.toString());
        object.addProperty("side", settlement.side().name());
        object.addProperty("material", settlement.material().name());

        MarketExecuteResult result = settlement.result();
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("executedQuantity", result.executedQuantity());
        resultJson.addProperty("totalPrice", result.totalPrice());
        resultJson.addProperty("unitPrice", result.unitPrice());
        resultJson.addProperty("currency", result.currency());
        resultJson.addProperty("snapshotVersion", result.snapshotVersion());
        object.add("result", resultJson);
        return object;
    }

    private Map<UUID, List<MarketGuiService.DeferredSettlement>> copy(
        Map<UUID, List<MarketGuiService.DeferredSettlement>> settlements
    ) {
        Map<UUID, List<MarketGuiService.DeferredSettlement>> copied =
            new HashMap<>();
        settlements.forEach((playerId, playerSettlements) ->
            copied.put(playerId, List.copyOf(playerSettlements))
        );
        return Map.copyOf(copied);
    }

    private String requiredString(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException(
                "Missing deferred settlement field '" + field + "'."
            );
        }
        return source.get(field).getAsString();
    }

    private int requiredInt(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException(
                "Missing deferred settlement field '" + field + "'."
            );
        }
        return source.get(field).getAsInt();
    }

    private String normalizeMoney(String value) {
        if (value == null || value.isBlank() || !value.matches("-?\\d+")) {
            return value;
        }

        return new BigDecimal(value)
            .movePointLeft(MONEY_SCALE)
            .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)
            .toPlainString();
    }
}
