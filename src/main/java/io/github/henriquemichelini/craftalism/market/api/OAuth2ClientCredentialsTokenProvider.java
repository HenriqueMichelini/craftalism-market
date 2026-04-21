package io.github.henriquemichelini.craftalism.market.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Logger;

public final class OAuth2ClientCredentialsTokenProvider implements MarketBearerTokenProvider {
    private final MarketApiTransport transport;
    private final URI tokenUri;
    private final Duration requestTimeout;
    private final String clientId;
    private final String clientSecret;
    private final String scopes;
    private final Logger logger;

    private String cachedToken;
    private Instant tokenExpiry = Instant.MIN;

    public OAuth2ClientCredentialsTokenProvider(
            MarketApiTransport transport,
            URI tokenUri,
            Duration requestTimeout,
            String clientId,
            String clientSecret,
            String scopes,
            Logger logger
    ) {
        this.transport = transport;
        this.tokenUri = tokenUri;
        this.requestTimeout = requestTimeout;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
        this.logger = logger;
    }

    @Override
    public synchronized String getBearerToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(30))) {
            return cachedToken;
        }

        TokenResponse tokenResponse = requestToken(false);
        if (shouldRetryWithClientSecretPost(tokenResponse.statusCode())) {
            tokenResponse = requestToken(true);
        }

        if (tokenResponse.statusCode() != 200) {
            throw new IllegalStateException(
                    "Failed to fetch market API bearer token: " +
                            tokenResponse.statusCode() +
                            " - " +
                            tokenResponse.body()
            );
        }

        JsonObject root = JsonParser.parseString(tokenResponse.body()).getAsJsonObject();
        if (!root.has("access_token") || root.get("access_token").isJsonNull()) {
            throw new IllegalStateException("Token response did not contain access_token.");
        }

        cachedToken = root.get("access_token").getAsString();
        long expiresIn = root.has("expires_in") && !root.get("expires_in").isJsonNull()
                ? root.get("expires_in").getAsLong()
                : 300L;
        tokenExpiry = Instant.now().plusSeconds(Math.max(1L, expiresIn));
        return cachedToken;
    }

    private TokenResponse requestToken(boolean includeClientCredentialsInBody) {
        String authorizationHeader = includeClientCredentialsInBody
                ? ""
                : "Basic " + Base64.getEncoder().encodeToString(
                        (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
                );

        try {
            String responseBody = transport.postForm(
                    tokenUri,
                    requestBody(includeClientCredentialsInBody),
                    requestTimeout,
                    authorizationHeader
            );
            return new TokenResponse(200, responseBody);
        } catch (MarketApiRequestException exception) {
            return new TokenResponse(exception.statusCode(), exception.responseBody());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to fetch market API bearer token.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching market API bearer token.", exception);
        }
    }

    private boolean shouldRetryWithClientSecretPost(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403;
    }

    private String requestBody(boolean includeClientCredentialsInBody) {
        StringBuilder body = new StringBuilder("grant_type=client_credentials");
        if (includeClientCredentialsInBody) {
            body.append("&client_id=")
                    .append(URLEncoder.encode(clientId, StandardCharsets.UTF_8))
                    .append("&client_secret=")
                    .append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
        }
        if (scopes != null && !scopes.isBlank()) {
            body.append("&scope=").append(URLEncoder.encode(scopes, StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    record TokenResponse(int statusCode, String body) {
    }
}
