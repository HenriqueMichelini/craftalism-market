package io.github.henriquemichelini.craftalism.market.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public final class HttpMarketQuoteClient implements MarketQuoteClient {
    private final MarketApiTransport transport;
    private final URI quoteUri;
    private final Duration requestTimeout;
    private final MarketBearerTokenProvider bearerTokenProvider;

    public HttpMarketQuoteClient(
            MarketApiTransport transport,
            URI quoteUri,
            Duration requestTimeout,
            MarketBearerTokenProvider bearerTokenProvider
    ) {
        this.transport = transport;
        this.quoteUri = quoteUri;
        this.requestTimeout = requestTimeout;
        this.bearerTokenProvider = bearerTokenProvider;
    }

    @Override
    public MarketQuoteResult requestQuote(UUID playerId, String itemId, MarketQuoteSide side, int quantity, String snapshotVersion) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("playerUuid", playerId.toString());
        requestBody.addProperty("itemId", itemId);
        requestBody.addProperty("side", side.name());
        requestBody.addProperty("quantity", quantity);
        if (snapshotVersion != null && !snapshotVersion.isBlank()) {
            requestBody.addProperty("snapshotVersion", snapshotVersion);
        }

        try {
            String responseBody = transport.postJson(
                    quoteUri,
                    requestBody.toString(),
                    requestTimeout,
                    bearerTokenProvider.getBearerToken()
            );
            return parseQuote(side, responseBody);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to request market quote from the API.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while requesting market quote from the API.", exception);
        }
    }

    MarketQuoteResult parseQuote(MarketQuoteSide side, String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        return new MarketQuoteResult(
                side,
                requiredInt(root, "quantity"),
                normalizeMoney(requiredString(root, "totalPrice")),
                normalizeMoney(requiredString(root, "unitPrice")),
                optionalString(root, "currency", "coins"),
                requiredString(root, "quoteToken"),
                requiredString(root, "snapshotVersion")
        );
    }

    private String requiredString(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException("Missing field '" + field + "' in market quote response.");
        }

        return source.get(field).getAsString();
    }

    private int requiredInt(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException("Missing field '" + field + "' in market quote response.");
        }

        return source.get(field).getAsInt();
    }

    private String optionalString(JsonObject source, String field, String fallback) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            return fallback;
        }

        return source.get(field).getAsString();
    }

    private String normalizeMoney(String value) {
        return MoneyValueFormatter.normalize(value);
    }
}
