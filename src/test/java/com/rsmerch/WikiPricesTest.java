package com.rsmerch;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WikiPricesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private JsonObject json(String text) { return new JsonParser().parse(text).getAsJsonObject(); }
    @Test public void missingSideRemainsUnknownAndCrossedTradesAreNotReordered() {
        WikiPrices data=WikiPrices.parse(json("{\"data\":{\"1\":{\"high\":null,\"highTime\":null,\"low\":50,\"lowTime\":100},\"2\":{\"high\":213,\"highTime\":100,\"low\":226,\"lowTime\":80}}}"),json("{}"),json("{}"),100000);
        assertEquals(0,data.quotes.get(1).high); assertEquals(0,data.quotes.get(1).highAt);
        assertEquals(213,data.quotes.get(2).high); assertEquals(226,data.quotes.get(2).low);
        assertEquals(0,data.quotes.get(2).hourHighVolume);
    }
    @Test public void sourceTimesDetermineFreshnessRatherThanFetchTime() {
        WikiPrices.Quote quote=new WikiPrices.Quote(); long now=BookTest.NOW;
        assertTrue(quote.fresh(now-60000,now)); assertFalse(quote.fresh(now-31*60000,now));
        assertFalse(quote.fresh(now+120000,now)); assertFalse(quote.fresh(0,now));
    }
    @Test public void cacheRoundTripPreservesWindowAndVolumes() throws Exception {
        WikiPrices data=WikiPrices.parse(json("{\"data\":{\"1\":{\"high\":100,\"highTime\":100,\"low\":90,\"lowTime\":95}}}"),json("{\"timestamp\":60,\"data\":{}}"),json("{\"timestamp\":1,\"data\":{\"1\":{\"avgHighPrice\":99,\"highPriceVolume\":17,\"avgLowPrice\":91,\"lowPriceVolume\":22}}}"),100000);
        Path path=temp.getRoot().toPath().resolve("cache.json"); data.save(path,net.runelite.http.api.RuneLiteAPI.GSON); WikiPrices loaded=WikiPrices.load(path,net.runelite.http.api.RuneLiteAPI.GSON);
        assertNull(loaded.error); assertEquals(1000,loaded.hourAt); assertEquals(17,loaded.quotes.get(1).hourHighVolume); assertEquals(22,loaded.quotes.get(1).hourLowVolume);
        Files.writeString(path,"broken"); assertNotNull(WikiPrices.load(path,net.runelite.http.api.RuneLiteAPI.GSON).error);
    }
}
