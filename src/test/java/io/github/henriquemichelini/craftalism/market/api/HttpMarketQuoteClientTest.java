package io.github.henriquemichelini.craftalism.market.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpMarketQuoteClientTest {
    @Test
    void postsQuoteRequestAndParsesQuoteResponse() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpMarketQuoteClient client = new HttpMarketQuoteClient(
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
                                  "quantity": 4,
                                  "totalPrice": "19.80",
                                  "unitPrice": "4.95",
                                  "currency": "coins",
                                  "quoteToken": "quote_123",
                                  "snapshotVersion": "snapshot-v2"
                                }
                                """;
                    }

                    @Override
                    public String postForm(URI uri, String body, Duration timeout, String authorizationHeader) {
                        throw new UnsupportedOperationException();
                    }
                },
                URI.create("http://localhost:8080/market/quote"),
                Duration.ofSeconds(5),
                () -> "secret-token"
        );

        MarketQuoteResult result = client.requestQuote("wheat", MarketQuoteSide.BUY, 4, "snapshot-v1");

        assertEquals(MarketQuoteSide.BUY, result.side());
        assertEquals("19.80", result.totalPrice());
        assertEquals("quote_123", result.quoteToken());
        assertEquals("snapshot-v2", result.snapshotVersion());
        assertEquals("secret-token", authHeader.get());
        assertTrue(requestBody.get().contains("\"itemId\":\"wheat\""));
        assertTrue(requestBody.get().contains("\"side\":\"BUY\""));
        assertTrue(requestBody.get().contains("\"quantity\":4"));
    }
}
