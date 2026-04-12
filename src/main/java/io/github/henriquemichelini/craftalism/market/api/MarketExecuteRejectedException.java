package io.github.henriquemichelini.craftalism.market.api;

public final class MarketExecuteRejectedException extends IllegalStateException {
    private final String rejectionCode;
    private final String snapshotVersion;

    public MarketExecuteRejectedException(String rejectionCode, String message, String snapshotVersion) {
        super(message);
        this.rejectionCode = rejectionCode;
        this.snapshotVersion = snapshotVersion;
    }

    public String rejectionCode() {
        return rejectionCode;
    }

    public String snapshotVersion() {
        return snapshotVersion;
    }
}
