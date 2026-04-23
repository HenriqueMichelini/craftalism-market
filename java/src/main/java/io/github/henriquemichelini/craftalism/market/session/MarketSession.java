package io.github.henriquemichelini.craftalism.market.session;

import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;

public record MarketSession(
        MarketScreen screen,
        String selectedCategoryId,
        String selectedItemId,
        boolean readOnly,
        int quantity,
        int quoteRequestVersion,
        MarketQuoteStatus quoteStatus,
        String quoteStatusMessage,
        String buyQuotedTotal,
        String sellQuotedTotal,
        String buyQuoteMessage,
        String sellQuoteMessage,
        String buyQuoteToken,
        String sellQuoteToken,
        String buyQuoteSnapshotVersion,
        String sellQuoteSnapshotVersion,
        boolean executingBuy,
        boolean executingSell
) {
    public static MarketSession categoryList(boolean readOnly) {
        return new MarketSession(MarketScreen.CATEGORY_LIST, null, null, readOnly, 1, 0, MarketQuoteStatus.DISABLED, null, null, null, null, null, null, null, null, null, false, false);
    }

    public static MarketSession itemList(String categoryId, boolean readOnly) {
        return new MarketSession(MarketScreen.ITEM_LIST, categoryId, null, readOnly, 1, 0, MarketQuoteStatus.DISABLED, null, null, null, null, null, null, null, null, null, false, false);
    }

    public static MarketSession tradeView(String categoryId, String itemId, boolean readOnly) {
        return new MarketSession(
                MarketScreen.TRADE_VIEW,
                categoryId,
                itemId,
                readOnly,
                1,
                0,
                readOnly ? MarketQuoteStatus.DISABLED : MarketQuoteStatus.AVAILABLE,
                readOnly ? "Cached preview only" : "Ready to trade",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }

    public MarketSession withQuantity(int quantity) {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                quoteStatus,
                quoteStatusMessage,
                buyQuotedTotal,
                sellQuotedTotal,
                buyQuoteMessage,
                sellQuoteMessage,
                buyQuoteToken,
                sellQuoteToken,
                buyQuoteSnapshotVersion,
                sellQuoteSnapshotVersion,
                executingBuy,
                executingSell
        );
    }

    public MarketSession withQuoteRefreshPending() {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion + 1,
                readOnly ? MarketQuoteStatus.DISABLED : MarketQuoteStatus.PENDING,
                readOnly ? "Cached preview only" : "Refreshing quote...",
                buyQuotedTotal,
                sellQuotedTotal,
                buyQuoteMessage,
                sellQuoteMessage,
                buyQuoteToken,
                sellQuoteToken,
                buyQuoteSnapshotVersion,
                sellQuoteSnapshotVersion,
                false,
                false
        );
    }

    public MarketSession withClearedQuoteState() {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                readOnly ? MarketQuoteStatus.DISABLED : MarketQuoteStatus.AVAILABLE,
                readOnly ? "Cached preview only" : "Ready to trade",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }

    public MarketSession withQuoteUnavailable(String message) {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                readOnly ? MarketQuoteStatus.DISABLED : MarketQuoteStatus.UNAVAILABLE,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }

    public MarketSession asReadOnlyPreview() {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                true,
                quantity,
                quoteRequestVersion,
                MarketQuoteStatus.DISABLED,
                "Cached preview only",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }

    public MarketSession asLiveTradingAvailable() {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                false,
                quantity,
                quoteRequestVersion,
                MarketQuoteStatus.AVAILABLE,
                "Ready to trade",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }

    public MarketSession withQuotePair(MarketQuotePair pair) {
        return withQuoteResults(pair.buy(), "Quote ready", pair.sell(), "Quote ready", "Quotes ready");
    }

    public MarketSession withQuoteResults(
            MarketQuoteResult buyQuote,
            String buyMessage,
            MarketQuoteResult sellQuote,
            String sellMessage,
            String message
    ) {
        boolean hasAnyQuote = buyQuote != null || sellQuote != null;
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                readOnly ? MarketQuoteStatus.DISABLED : (hasAnyQuote ? MarketQuoteStatus.AVAILABLE : MarketQuoteStatus.UNAVAILABLE),
                message,
                buyQuote == null ? null : buyQuote.totalPrice() + " " + buyQuote.currency(),
                sellQuote == null ? null : sellQuote.totalPrice() + " " + sellQuote.currency(),
                buyMessage,
                sellMessage,
                buyQuote == null ? null : buyQuote.quoteToken(),
                sellQuote == null ? null : sellQuote.quoteToken(),
                buyQuote == null ? null : buyQuote.snapshotVersion(),
                sellQuote == null ? null : sellQuote.snapshotVersion(),
                false,
                false
        );
    }

    public MarketSession withExecutionPending(MarketQuoteSide side) {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                MarketQuoteStatus.PENDING,
                side == MarketQuoteSide.BUY ? "Executing buy..." : "Executing sell...",
                buyQuotedTotal,
                sellQuotedTotal,
                buyQuoteMessage,
                sellQuoteMessage,
                buyQuoteToken,
                sellQuoteToken,
                buyQuoteSnapshotVersion,
                sellQuoteSnapshotVersion,
                side == MarketQuoteSide.BUY,
                side == MarketQuoteSide.SELL
        );
    }

    public MarketSession withQuoteMessage(MarketQuoteStatus status, String message) {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                status,
                message,
                buyQuotedTotal,
                sellQuotedTotal,
                buyQuoteMessage,
                sellQuoteMessage,
                buyQuoteToken,
                sellQuoteToken,
                buyQuoteSnapshotVersion,
                sellQuoteSnapshotVersion,
                false,
                false
        );
    }

    public boolean matchesTradeRequest(String categoryId, String itemId, int quantity, int requestVersion) {
        return screen == MarketScreen.TRADE_VIEW
                && selectedCategoryId != null
                && selectedCategoryId.equals(categoryId)
                && selectedItemId != null
                && selectedItemId.equals(itemId)
                && this.quantity == quantity
                && quoteRequestVersion == requestVersion;
    }
}
