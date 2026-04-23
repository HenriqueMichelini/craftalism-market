package io.github.henriquemichelini.craftalism.market.session;

import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSessionTest {
    @Test
    void transitionsTradeSessionFromPendingToAvailable() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", false).withQuantity(4);
        MarketQuotePair quotes = new MarketQuotePair(
                new MarketQuoteResult(MarketQuoteSide.BUY, 4, "19.8", "4.95", "coins", "buy-token", "snapshot-buy"),
                new MarketQuoteResult(MarketQuoteSide.SELL, 4, "16.4", "4.10", "coins", "sell-token", "snapshot-sell")
        );

        MarketSession updated = session.withQuotePair(quotes);

        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("19.8 coins", updated.buyQuotedTotal());
        assertEquals("sell-token", updated.sellQuoteToken());
        assertEquals(false, updated.executingBuy());
        assertEquals(false, updated.executingSell());
        assertTrue(updated.matchesTradeRequest("farming", "wheat", 4, updated.quoteRequestVersion()));
    }

    @Test
    void startsLiveTradeSessionsReadyWithoutRequestingQuote() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", false);

        assertEquals(MarketQuoteStatus.AVAILABLE, session.quoteStatus());
        assertEquals("Ready to trade", session.quoteStatusMessage());
    }

    @Test
    void startsReadOnlyTradeSessionsWithQuotesDisabled() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", true);

        assertEquals(MarketQuoteStatus.DISABLED, session.quoteStatus());
        assertEquals("Cached preview only", session.quoteStatusMessage());
    }

    @Test
    void quantityChangeClearsExistingQuoteStateAndMarksRefreshPending() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", false)
                .withQuotePair(new MarketQuotePair(
                        new MarketQuoteResult(MarketQuoteSide.BUY, 1, "4.8", "4.8", "coins", "buy-token", "snapshot-buy"),
                        new MarketQuoteResult(MarketQuoteSide.SELL, 1, "4.1", "4.1", "coins", "sell-token", "snapshot-sell")
                ));

        MarketSession updated = session.withQuantity(6);

        assertEquals(6, updated.quantity());
        assertEquals(session.quoteRequestVersion() + 1, updated.quoteRequestVersion());
        assertEquals(MarketQuoteStatus.PENDING, updated.quoteStatus());
        assertEquals("Refreshing quote...", updated.quoteStatusMessage());
        assertEquals(null, updated.buyQuotedTotal());
        assertEquals(null, updated.sellQuoteToken());
        assertFalse(updated.executingBuy());
        assertFalse(updated.executingSell());
    }

    @Test
    void readOnlyPreviewPreservesSelectionButDisablesTrading() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", false).withQuantity(3);

        MarketSession updated = session.asReadOnlyPreview();

        assertTrue(updated.readOnly());
        assertEquals(3, updated.quantity());
        assertEquals(MarketQuoteStatus.DISABLED, updated.quoteStatus());
        assertEquals("Cached preview only", updated.quoteStatusMessage());
    }

    @Test
    void liveModeLeavesReadOnlyModeWithoutRequestingQuote() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", true);

        MarketSession updated = session.asLiveTradingAvailable();

        assertFalse(updated.readOnly());
        assertEquals(session.quoteRequestVersion(), updated.quoteRequestVersion());
        assertEquals(MarketQuoteStatus.AVAILABLE, updated.quoteStatus());
        assertEquals("Ready to trade", updated.quoteStatusMessage());
    }

    @Test
    void sellExecutionPendingUsesSellMessageAndFlag() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", false)
                .withQuotePair(new MarketQuotePair(
                        new MarketQuoteResult(MarketQuoteSide.BUY, 2, "9.6", "4.8", "coins", "buy-token", "snapshot-buy"),
                        new MarketQuoteResult(MarketQuoteSide.SELL, 2, "8.2", "4.1", "coins", "sell-token", "snapshot-sell")
                ));

        MarketSession updated = session.withExecutionPending(MarketQuoteSide.SELL);

        assertEquals(MarketQuoteStatus.PENDING, updated.quoteStatus());
        assertEquals("Executing sell...", updated.quoteStatusMessage());
        assertFalse(updated.executingBuy());
        assertTrue(updated.executingSell());
    }

    @Test
    void latestQuantityChangeInvalidatesEarlierTradeRequestVersion() {
        MarketSession initial = MarketSession.tradeView("farming", "wheat", false);
        MarketSession afterFirstChange = initial.withQuantity(3);
        MarketSession afterSecondChange = afterFirstChange.withQuantity(7);

        assertFalse(afterSecondChange.matchesTradeRequest(
                "farming",
                "wheat",
                3,
                afterFirstChange.quoteRequestVersion()
        ));
        assertTrue(afterSecondChange.matchesTradeRequest(
                "farming",
                "wheat",
                7,
                afterSecondChange.quoteRequestVersion()
        ));
    }
}
