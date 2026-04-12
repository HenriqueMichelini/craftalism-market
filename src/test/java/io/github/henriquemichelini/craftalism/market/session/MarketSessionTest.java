package io.github.henriquemichelini.craftalism.market.session;

import io.github.henriquemichelini.craftalism.market.api.MarketQuotePair;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteResult;
import io.github.henriquemichelini.craftalism.market.api.MarketQuoteSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSessionTest {
    @Test
    void transitionsTradeSessionFromPendingToAvailable() {
        MarketSession session = MarketSession.tradeView("farming", "wheat", false).withQuantityPending(4);
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
}
