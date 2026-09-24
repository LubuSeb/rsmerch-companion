package com.rsmerch;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ResearchTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private Path report(long generated,long quote) throws Exception {
        JsonObject r=new JsonObject(); r.addProperty("generated_at",Instant.ofEpochMilli(generated).toString());
        JsonObject c=new JsonObject(); c.addProperty("id",1763); c.addProperty("name","Red dye");
        c.addProperty("buy",450); c.addProperty("sell",900); c.addProperty("test_quantity",10); c.addProperty("quantity",150);
        c.addProperty("low_time",Instant.ofEpochMilli(quote).toString()); c.addProperty("high_time",Instant.ofEpochMilli(quote).toString());
        c.addProperty("reported_high_volume_24h",1000); c.addProperty("reported_low_volume_24h",500); c.addProperty("buy_limit_4h",150);
        JsonArray a=new JsonArray(); a.add(c); r.add("opportunities",a);
        Path f=temp.getRoot().toPath().resolve("latest.json"); Files.writeString(f,new Gson().toJson(r)); return f;
    }
    @Test public void oldReportBlocksSizingAndIndividualQuotesCanAlsoBeStale() throws Exception {
        long now=BookTest.NOW;
        Path p=report(now-Book.DAY,now-Book.DAY);
        Research r=Research.read(p.toString(),"",new Book(),now,1_000_000);
        assertFalse(r.fresh); assertEquals(0,r.candidates.get(0).testQuantity);
        p=report(now,now-3_600_000);
        r=Research.read(p.toString(),"",new Book(),now,1_000_000);
        assertTrue(r.fresh); assertFalse(r.candidates.get(0).fresh); assertEquals(0,r.candidates.get(0).testQuantity);
    }
    @Test public void exposureAndUnknownCostReducePositionRoom() throws Exception {
        Path p=report(BookTest.NOW,BookTest.NOW); Book b=new Book();
        assertEquals(10,Research.read(p.toString(),"",b,BookTest.NOW,1_000_000).candidates.get(0).testQuantity);
        b.accept(BookTest.stock("0",500,249_000L));
        assertEquals(2,Research.read(p.toString(),"",b,BookTest.NOW,1_000_000).candidates.get(0).testQuantity);
        b.accept(BookTest.stock("1",500,null));
        assertEquals(0,Research.read(p.toString(),"",b,BookTest.NOW,1_000_000).candidates.get(0).testQuantity);
    }
    @Test public void malformedReportAndFutureTimestampDoNotOfferTradableSize() throws Exception {
        Path p=report(BookTest.NOW+60_000,BookTest.NOW); Research r=Research.read(p.toString(),"",new Book(),BookTest.NOW,1_000_000);
        assertFalse(r.fresh); assertEquals(0,r.candidates.get(0).testQuantity);
        Files.writeString(p,"{}"); r=Research.read(p.toString(),"",new Book(),BookTest.NOW,1_000_000);
        assertTrue(r.candidates.isEmpty());
    }
    @Test public void viewDetachesJournalDataAndDoesNotProjectFromShortHistory() {
        Book b=new Book(); b.accept(BookTest.offer("0",0,"EMPTY",0,0));
        Event opening=BookTest.stock("1",10,4500L); opening.name="<test & name>"; b.accept(opening);
        DeskView v=DeskView.build(b,new Research(),BookTest.NOW+1000,1_000_000,7,"Demo",null);
        assertEquals("<test & name>",v.stocks.get(0).name);
        assertNull(v.stocks.get(0).warning);
        b.accept(BookTest.stock("2",20,9000L));
        assertEquals(10,v.stocks.get(0).quantity);
    }
    @Test public void ownVolumeUsesTheSameCompletedHoursAsPublicVolume() throws Exception {
        Book b=new Book(); b.accept(BookTest.offer("0",-120_000,"EMPTY",0,0));
        b.accept(BookTest.offer("1",-60_000,"BUYING",2,900));
        b.accept(BookTest.offer("2",600_000,"BUYING",5,2250));
        long now=BookTest.NOW+1_500_000;
        Path p=report(now,now);
        Research r=Research.read(p.toString(),"",b,now,1_000_000);
        assertEquals(2,r.candidates.get(0).ownBuys); // Report's current partial hour is excluded.
    }
    @Test public void pythonOffsetTimestampsAndChartLoadOnJava11() throws Exception {
        Path p=report(BookTest.NOW,BookTest.NOW);
        JsonObject root=new JsonParser().parse(Files.readString(p)).getAsJsonObject();
        String timestamp=Instant.ofEpochMilli(BookTest.NOW).toString().replace("Z","+00:00");
        root.addProperty("generated_at",timestamp);
        JsonObject source=new JsonObject(); source.addProperty("hourly_block",timestamp); root.add("source_times",source);
        JsonObject item=root.getAsJsonArray("opportunities").get(0).getAsJsonObject();
        item.addProperty("low_time",timestamp); item.addProperty("high_time",timestamp);
        JsonArray chart=new JsonArray(); JsonObject point=new JsonObject(); point.addProperty("high",900); point.addProperty("low",450); chart.add(point); item.add("chart",chart);
        Files.writeString(p,new Gson().toJson(root));
        Research r=Research.read(p.toString(),"",new Book(),BookTest.NOW,1_000_000);
        assertNull(r.problem); assertTrue(r.fresh); assertEquals(10,r.candidates.get(0).testQuantity);
        assertEquals(432,r.candidates.get(0).net); assertEquals(1,r.candidates.get(0).chart.size());
    }
}
