package io.github.henriquemichelini.craftalism.market.api;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public interface MarketApiTransport {
    String get(URI uri, Duration timeout, String bearerToken) throws IOException, InterruptedException;

    String postJson(URI uri, String body, Duration timeout, String bearerToken) throws IOException, InterruptedException;

    String postForm(URI uri, String body, Duration timeout, String authorizationHeader) throws IOException, InterruptedException;
}
