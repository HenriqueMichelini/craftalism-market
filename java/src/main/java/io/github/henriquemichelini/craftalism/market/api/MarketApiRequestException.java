package io.github.henriquemichelini.craftalism.market.api;

public final class MarketApiRequestException extends IllegalStateException {
    private final int statusCode;
    private final String responseBody;

    public MarketApiRequestException(int statusCode, String responseBody) {
        super("Market API request failed with status " + statusCode + ".");
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
