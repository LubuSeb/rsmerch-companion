package com.rsmerch;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarketScannerTest {
    private final long now=BookTest.NOW;
    private JsonObject json(String text) { return new JsonParser().parse(text).getAsJsonObject(); }
    private JsonObject item() { return json("{\"id\":1763,\"name\":\"Synthetic dye\",\"limit\":150}"); }
    private JsonObject quote() { JsonObject q=json("{\"low\":100,\"high\":120}"); q.addProperty("lowTime",now/1000-60); q.addProperty("highTime",now/1000-60); return q; }
    private List<JsonObject> rows(int count) {
        List<JsonObject> rows=new ArrayList<>(); for (int i=0;i<count;i++) {
            JsonObject row=json("{\"avgLowPrice\":100,\"avgHighPrice\":120,\"highPriceVolume\":1000,\"lowPriceVolume\":1000}");
            row.addProperty("timestamp",now/1000-3600*(i+1)); rows.add(row);
        } return rows;
    }
    @Test public void stableHistoryProducesConservativeTaxedTargetsAndSmallTest() {
        JsonObject c=MarketScanner.candidate(item(),quote(),new JsonObject(),new JsonObject(),rows(24),1_000_000,now);
        assertNotNull(c); assertEquals(101,c.get("buy").getAsLong()); assertEquals(119,c.get("sell").getAsLong());
        assertTrue(c.get("quantity").getAsLong()<=150); assertTrue(c.get("test_quantity").getAsLong()<=10);
    }
    @Test public void staleAndCrossedQuotesAreRejected() {
        JsonObject q=quote(); q.addProperty("lowTime",now/1000-1801);
        assertNull(MarketScanner.candidate(item(),q,new JsonObject(),new JsonObject(),rows(24),1_000_000,now));
        q=quote(); q.addProperty("low",125);
        assertNull(MarketScanner.candidate(item(),q,new JsonObject(),new JsonObject(),rows(24),1_000_000,now));
    }
    @Test public void missingItemHoursDoNotInflateFlowCapacity() {
        JsonObject c=MarketScanner.candidate(item(),quote(),new JsonObject(),new JsonObject(),rows(12),1_000_000,now);
        assertNotNull(c); assertEquals(30,c.get("quantity").getAsLong());
        assertNull(MarketScanner.candidate(item(),quote(),new JsonObject(),new JsonObject(),rows(11),1_000_000,now));
    }
    @Test public void unsupportedLatestSpikeIsRejected() {
        JsonObject q=quote(); q.addProperty("low",180); q.addProperty("high",210);
        assertNull(MarketScanner.candidate(item(),q,new JsonObject(),new JsonObject(),rows(24),1_000_000,now));
    }
}
