package io.github.henriquemichelini.craftalism.market.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public final class HttpMarketExecuteClient implements MarketExecuteClient {
    private final MarketApiTransport transport;
    private final URI executeUri;
    private final Duration requestTimeout;
    private final String bearerToken;

    public HttpMarketExecuteClient(MarketApiTransport transport, URI executeUri, Duration requestTimeout, String bearerToken) {
        this.transport = transport;
        this.executeUri = executeUri;
        this.requestTimeout = requestTimeout;
        this.bearerToken = bearerToken;
    }

    @Override
    public MarketExecuteResult executeTrade(
            String itemId,
            MarketQuoteSide side,
            int quantity,
            String quoteToken,
            String snapshotVersion
    ) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("itemId", itemId);
        requestBody.addProperty("side", side.name());
        requestBody.addProperty("quantity", quantity);
        requestBody.addProperty("quoteToken", quoteToken);
        requestBody.addProperty("snapshotVersion", snapshotVersion);

        try {
            String responseBody = transport.postJson(executeUri, requestBody.toString(), requestTimeout, bearerToken);
            return parseSuccess(responseBody);
        } catch (MarketApiRequestException exception) {
            throw parseRejection(exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute market trade with the API.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing market trade with the API.", exception);
        }
    }

    MarketExecuteResult parseSuccess(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        return new MarketExecuteResult(
                requiredInt(root, "executedQuantity"),
                requiredString(root, "totalPrice"),
                requiredString(root, "unitPrice"),
                optionalString(root, "currency", "coins"),
                optionalString(root, "snapshotVersion", "")
        );
    }

    MarketExecuteRejectedException parseRejection(MarketApiRequestException exception) {
        JsonObject root = JsonParser.parseString(exception.responseBody()).getAsJsonObject();
        return new MarketExecuteRejectedException(
                requiredString(root, "code"),
                optionalString(root, "message", "Market execution rejected."),
                optionalString(root, "snapshotVersion", "")
        );
    }

    private String requiredString(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException("Missing field '" + field + "' in market execute response.");
        }

        return source.get(field).getAsString();
    }

    private int requiredInt(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalStateException("Missing field '" + field + "' in market execute response.");
        }

        return source.get(field).getAsInt();
    }

    private String optionalString(JsonObject source, String field, String fallback) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            return fallback;
        }

        return source.get(field).getAsString();
    }
}
