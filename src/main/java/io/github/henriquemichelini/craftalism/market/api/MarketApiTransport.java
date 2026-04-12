package io.github.henriquemichelini.craftalism.market.api;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public interface MarketApiTransport {
    String get(URI uri, Duration timeout) throws IOException, InterruptedException;
}
