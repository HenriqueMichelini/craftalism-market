package io.github.henriquemichelini.craftalism.market.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class OAuth2ClientCredentialsTokenProviderTest {

    @Test
    void requestsClientCredentialsTokenWithBasicAuthAndCachesIt() {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        OAuth2ClientCredentialsTokenProvider provider =
            new OAuth2ClientCredentialsTokenProvider(
                new MarketApiTransport() {
                    @Override
                    public String get(
                        URI uri,
                        Duration timeout,
                        String bearerToken
                    ) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postJson(
                        URI uri,
                        String body,
                        Duration timeout,
                        String bearerToken
                    ) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postForm(
                        URI uri,
                        String body,
                        Duration timeout,
                        String header
                    ) {
                        tokenRequests.incrementAndGet();
                        requestBody.set(body);
                        authorizationHeader.set(header);
                        return """
                            {
                              "access_token": "market-token",
                              "expires_in": 300
                            }
                            """;
                    }
                },
                URI.create("http://craftalism-auth:9000/oauth2/token"),
                Duration.ofSeconds(5),
                "minecraft-server",
                "client-secret",
                "api:write",
                Logger.getLogger(
                    OAuth2ClientCredentialsTokenProviderTest.class.getName()
                )
            );

        assertEquals("market-token", provider.getBearerToken());
        assertEquals("market-token", provider.getBearerToken());
        assertEquals(1, tokenRequests.get());
        assertEquals(
            "Basic " +
            Base64.getEncoder()
                .encodeToString("minecraft-server:client-secret".getBytes()),
            authorizationHeader.get()
        );
        assertTrue(
            requestBody.get().contains("grant_type=client_credentials")
        );
        assertTrue(requestBody.get().contains("scope=api%3Awrite"));
    }

    @Test
    void retriesWithClientCredentialsInBodyWhenBasicAuthIsRejected() {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicReference<String> retryBody = new AtomicReference<>();
        OAuth2ClientCredentialsTokenProvider provider =
            new OAuth2ClientCredentialsTokenProvider(
                new MarketApiTransport() {
                    @Override
                    public String get(
                        URI uri,
                        Duration timeout,
                        String bearerToken
                    ) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postJson(
                        URI uri,
                        String body,
                        Duration timeout,
                        String bearerToken
                    ) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String postForm(
                        URI uri,
                        String body,
                        Duration timeout,
                        String authorizationHeader
                    ) {
                        if (tokenRequests.incrementAndGet() == 1) {
                            throw new MarketApiRequestException(401, "Unauthorized");
                        }

                        retryBody.set(body);
                        assertEquals("", authorizationHeader);
                        return """
                            {
                              "access_token": "retry-token",
                              "expires_in": 300
                            }
                            """;
                    }
                },
                URI.create("http://craftalism-auth:9000/oauth2/token"),
                Duration.ofSeconds(5),
                "minecraft-server",
                "client-secret",
                "",
                Logger.getLogger(
                    OAuth2ClientCredentialsTokenProviderTest.class.getName()
                )
            );

        assertEquals("retry-token", provider.getBearerToken());
        assertEquals(2, tokenRequests.get());
        assertTrue(retryBody.get().contains("client_id=minecraft-server"));
        assertTrue(retryBody.get().contains("client_secret=client-secret"));
    }
}
