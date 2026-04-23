package io.github.henriquemichelini.craftalism.market.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMarketExecuteClientTest {
    @Test
    void postsExecuteRequestAndParsesSuccessResponse() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpMarketExecuteClient client = new HttpMarketExecuteClient(
                new MarketApiTransport() {
                    @Override
                    public String get(URI uri, Duration timeout, String bearerToken) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postJson(URI uri, String body, Duration timeout, String bearerToken) {
                        requestBody.set(body);
                        authHeader.set(bearerToken);
                        return """
                                {
                                  "status": "SUCCESS",
                                  "executedQuantity": 4,
                                  "unitPrice": "4.95",
                                  "totalPrice": "19.80",
                                  "currency": "coins",
                                  "snapshotVersion": "snapshot-v3"
                                }
                                """;
                    }

                    @Override
                    public String postForm(URI uri, String body, Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/execute"),
                Duration.ofSeconds(5),
                () -> "secret-token"
        );

        UUID playerId = UUID.fromString("c5eb4cd5-183e-4148-b936-4a805b155a57");
        MarketExecuteResult result = client.executeTrade(
                playerId,
                "wheat",
                MarketQuoteSide.BUY,
                4,
                "quote-123",
                "snapshot-v2"
        );

        assertEquals(4, result.executedQuantity());
        assertEquals("19.8", result.totalPrice());
        assertEquals("snapshot-v3", result.snapshotVersion());
        assertEquals("secret-token", authHeader.get());
        assertTrue(requestBody.get().contains("\"playerUuid\":\"" + playerId + "\""));
        assertTrue(requestBody.get().contains("\"quoteToken\":\"quote-123\""));
        assertTrue(requestBody.get().contains("\"side\":\"BUY\""));
    }

    @Test
    void preservesIntegerExecutePriceDisplayUnits() {
        HttpMarketExecuteClient client = new HttpMarketExecuteClient(
                new MarketApiTransport() {
                    @Override
                    public String get(URI uri, Duration timeout, String bearerToken) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postJson(URI uri, String body, Duration timeout, String bearerToken) {
                        return """
                                {
                                  "status": "SUCCESS",
                                  "executedQuantity": 4,
                                  "unitPrice": 49500,
                                  "totalPrice": 198000,
                                  "currency": "coins",
                                  "snapshotVersion": "snapshot-v3"
                                }
                                """;
                    }

                    @Override
                    public String postForm(URI uri, String body, Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/execute"),
                Duration.ofSeconds(5),
                () -> "secret-token"
        );

        MarketExecuteResult result = client.executeTrade(
                UUID.fromString("c5eb4cd5-183e-4148-b936-4a805b155a57"),
                "wheat",
                MarketQuoteSide.BUY,
                4,
                "quote-123",
                "snapshot-v2"
        );

        assertEquals("198000", result.totalPrice());
        assertEquals("49500", result.unitPrice());
    }

    @Test
    void parsesStructuredRejectionCodes() {
        HttpMarketExecuteClient client = new HttpMarketExecuteClient(
                new MarketApiTransport() {
                    @Override
                    public String get(URI uri, Duration timeout, String bearerToken) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postJson(URI uri, String body, Duration timeout, String bearerToken) {
                        throw new MarketApiRequestException(409, """
                                {
                                  "status": "REJECTED",
                                  "code": "STALE_QUOTE",
                                  "message": "Quote is stale.",
                                  "snapshotVersion": "snapshot-v4"
                                }
                                """);
                    }

                    @Override
                    public String postForm(URI uri, String body, Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/execute"),
                Duration.ofSeconds(5),
                () -> "secret-token"
        );

        MarketExecuteRejectedException rejection = assertThrows(
                MarketExecuteRejectedException.class,
                () -> client.executeTrade(
                        UUID.fromString("c5eb4cd5-183e-4148-b936-4a805b155a57"),
                        "wheat",
                        MarketQuoteSide.SELL,
                        4,
                        "quote-123",
                        "snapshot-v2"
                )
        );

        assertEquals("STALE_QUOTE", rejection.rejectionCode());
        assertEquals("snapshot-v4", rejection.snapshotVersion());
        assertEquals("Quote is stale.", rejection.getMessage());
    }
}
