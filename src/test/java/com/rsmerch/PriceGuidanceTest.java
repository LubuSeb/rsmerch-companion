package com.rsmerch;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PriceGuidanceTest {
    private static final long NOW=BookTest.NOW/3_600_000*3_600_000;
    private PriceHistory.Item evidence() {
        PriceHistory.Item history=new PriceHistory.Item(); history.fetchedAt=NOW;
        for (int i=1;i<=120;i++) { PriceHistory.Hour h=new PriceHistory.Hour(); h.at=NOW-i*3_600_000; h.high=200+i%4*10; h.low=100; h.highVolume=10; h.lowVolume=20; history.hours.add(h); }
        return history;
    }
    private WikiPrices.Quote quote() { WikiPrices.Quote q=new WikiPrices.Quote(); q.high=220; q.low=100; q.highAt=NOW; q.lowAt=NOW; return q; }
    @Test public void repeatedHistoryProvidesRangeButSingleSpikeDoesNotSetTarget() {
        PriceHistory.Item history=evidence(); history.hours.get(2).high=20000;
        PriceGuidance.Advice a=PriceGuidance.assess(1763,history,quote(),NOW);
        assertTrue(a.actionable); assertEquals(120,a.highHours); assertEquals(210,a.sellLow); assertEquals(220,a.sellHigh);
        assertTrue(a.buyActionable); assertEquals(100,a.buyLow); assertEquals(100,a.fastExit);
    }
    @Test public void sparseOrZeroVolumeDataHasNoPatientTarget() {
        PriceHistory.Item history=evidence(); history.hours.forEach(h -> h.highVolume=0);
        assertEquals(0,PriceGuidance.assess(1763,history,quote(),NOW).sellHigh);
        history=evidence(); history.hours.subList(10,history.hours.size()).clear();
        assertEquals(0,PriceGuidance.assess(1763,history,quote(),NOW).sellHigh);
    }
    @Test public void staleTradeOrCacheDisablesCopyableGuidance() {
        WikiPrices.Quote quote=quote(); quote.highAt=NOW-31*60_000;
        assertFalse(PriceGuidance.assess(1763,evidence(),quote,NOW).actionable);
        PriceHistory.Item history=evidence(); history.fetchedAt=NOW-7*3_600_000;
        assertFalse(PriceGuidance.assess(1763,history,quote(),NOW).actionable);
    }
    @Test public void recentDeclineUsesRecentEvidenceInsteadOfOldPeaks() {
        PriceHistory.Item history=evidence(); history.hours.stream().filter(h -> h.at>=NOW-Book.DAY).forEach(h -> h.high=140);
        PriceGuidance.Advice a=PriceGuidance.assess(1763,history,quote(),NOW);
        assertTrue(a.falling); assertEquals(140,a.sellLow); assertEquals(140,a.sellHigh);
    }
    @Test public void crossedAfterTaxRangeDoesNotInviteNewBuy() {
        PriceHistory.Item history=evidence(); history.hours.forEach(h -> h.low=300);
        assertFalse(PriceGuidance.assess(1763,history,quote(),NOW).buyActionable);
    }
    @Test public void incompleteAndDuplicateHoursAreHandledBeforeAnalysis() throws Exception {
        long hour=(NOW-3_600_000)/1000;
        JsonObject root=new JsonParser().parse("{\"data\":[{\"timestamp\":"+hour+",\"avgHighPrice\":100,\"highPriceVolume\":1},{\"timestamp\":"+hour+",\"avgHighPrice\":150,\"highPriceVolume\":2},{\"timestamp\":"+(NOW/1000)+",\"avgHighPrice\":900,\"highPriceVolume\":99}]}").getAsJsonObject();
        PriceHistory.Item history=PriceHistory.parse(root,NOW); assertEquals(1,history.hours.size()); assertEquals(150,history.hours.get(0).high);
    }
}
