package io.github.henriquemichelini.craftalism.market.session;

import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
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
        String buyQuoteToken,
        String sellQuoteToken,
        String buyQuoteSnapshotVersion,
        String sellQuoteSnapshotVersion,
        boolean executingBuy,
        boolean executingSell
) {
    public static MarketSession categoryList(boolean readOnly) {
        return new MarketSession(MarketScreen.CATEGORY_LIST, null, null, readOnly, 1, 0, MarketQuoteStatus.DISABLED, null, null, null, null, null, null, null, false, false);
    }

    public static MarketSession itemList(String categoryId, boolean readOnly) {
        return new MarketSession(MarketScreen.ITEM_LIST, categoryId, null, readOnly, 1, 0, MarketQuoteStatus.DISABLED, null, null, null, null, null, null, null, false, false);
    }

    public static MarketSession tradeView(String categoryId, String itemId, boolean readOnly) {
        return new MarketSession(
                MarketScreen.TRADE_VIEW,
                categoryId,
                itemId,
                readOnly,
                1,
                0,
                readOnly ? MarketQuoteStatus.DISABLED : MarketQuoteStatus.PENDING,
                readOnly ? "Cached preview only" : "Refreshing quote...",
                null,
                null,
                null,
                null,
                null,
                null,
                false
                ,
                false
        );
    }

    public MarketSession withQuantityPending(int quantity) {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion + 1,
                readOnly ? MarketQuoteStatus.DISABLED : MarketQuoteStatus.PENDING,
                readOnly ? "Cached preview only" : "Refreshing quote...",
                null,
                null,
                null,
                null,
                null,
                null,
                false
                ,
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
                false
                ,
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
                false,
                false
        );
    }

    public MarketSession asLiveQuotePending() {
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                false,
                quantity,
                quoteRequestVersion + 1,
                MarketQuoteStatus.PENDING,
                "Refreshing quote...",
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
        return new MarketSession(
                screen,
                selectedCategoryId,
                selectedItemId,
                readOnly,
                quantity,
                quoteRequestVersion,
                MarketQuoteStatus.AVAILABLE,
                "Quotes ready",
                pair.buy().totalPrice() + " " + pair.buy().currency(),
                pair.sell().totalPrice() + " " + pair.sell().currency(),
                pair.buy().quoteToken(),
                pair.sell().quoteToken(),
                pair.buy().snapshotVersion(),
                pair.sell().snapshotVersion(),
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
                "Executing buy...",
                buyQuotedTotal,
                sellQuotedTotal,
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
