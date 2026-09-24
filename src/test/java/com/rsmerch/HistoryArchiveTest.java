package com.rsmerch;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HistoryArchiveTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static final String ACCOUNT="111111111111111111111111";
    private static final long NOW=BookTest.NOW;
    private String saved() { return "[{\"b\":true,\"i\":1763,\"q\":150,\"p\":450,\"t\":"+(NOW-1000)+"}]"; }
    private List<HistoryArchive.Row> parse(String json) { return HistoryArchive.parseRuneLite(json,id -> "Red dye",NOW); }
    @Test public void reimportAndReloadAreIdempotentButIdenticalOccurrencesArePreserved() throws Exception {
        HistoryArchive archive=new HistoryArchive(temp.getRoot().toPath(),ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON);
        assertTrue(archive.save(HistoryArchive.RUNELITE,parse(saved()),saved(),NOW));
        assertFalse(archive.save(HistoryArchive.RUNELITE,parse(saved()),saved(),NOW+1000));
        String twice=saved().substring(0,saved().length()-1)+","+saved().substring(1);
        archive.save(HistoryArchive.RUNELITE,parse(twice),twice,NOW+2000);
        assertEquals(2,archive.view(new Book()).saved.size());
        HistoryArchive reloaded=new HistoryArchive(temp.getRoot().toPath(),ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON);
        assertEquals(2,reloaded.view(new Book()).saved.size());
    }
    @Test public void screenSnapshotRepeatsDoNotMultiplyButDifferentSnapshotsRemainReviewable() throws Exception {
        HistoryArchive archive=new HistoryArchive(temp.getRoot().toPath(),ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON);
        HistoryArchive.Row row=HistoryScreen.parse(1763,10,"Sold:","Red dye<br>x 10","8,820 coins<br>(9,000 - 180)<br>= 882 each");
        assertTrue(archive.save(HistoryArchive.SCREEN,Arrays.asList(row),null,NOW));
        assertFalse(archive.save(HistoryArchive.SCREEN,Arrays.asList(row),null,NOW+100));
        archive.save(HistoryArchive.SCREEN,Arrays.asList(row,row),null,NOW+1000);
        HistoryArchive.View v=new HistoryArchive(temp.getRoot().toPath(),ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON).view(new Book());
        assertEquals(2,v.captures.size()); assertEquals(2,v.screen.size()); assertEquals(0,v.screen.get(0).row.recordedAt);
        assertEquals(Long.valueOf(180),v.screen.get(0).row.tax);
    }
    @Test public void wrongAccountOrEditedFinancialFieldsFailClosed() throws Exception {
        Path root=temp.getRoot().toPath(); HistoryArchive archive=new HistoryArchive(root,ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON);
        archive.save(HistoryArchive.RUNELITE,parse(saved()),saved(),NOW);
        try { new HistoryArchive(root,"222222222222222222222222",net.runelite.http.api.RuneLiteAPI.GSON); fail(); } catch (java.io.IOException expected) { }
        Path file; try (java.util.stream.Stream<Path> stream=Files.list(root.resolve("history-v1"))) { file=stream.filter(p -> p.toString().endsWith(".json")).findFirst().get(); }
        JsonObject json=new JsonParser().parse(Files.readString(file)).getAsJsonObject();
        json.getAsJsonArray("rows").get(0).getAsJsonObject().addProperty("price",999);
        Files.writeString(file,json.toString());
        try { new HistoryArchive(root,ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON); fail(); } catch (java.io.IOException expected) { }
    }
    @Test public void archiveDoesNotInventInventoryAndMarksPossibleLiveOverlap() throws Exception {
        Book book=new Book(); book.accept(BookTest.offer("empty",-2000,"EMPTY",0,0));
        book.accept(BookTest.offer("buy",-1500,"BUYING",0,0));
        book.accept(BookTest.offer("fill",-1000,"BOUGHT",100,45000));
        HistoryArchive archive=new HistoryArchive(temp.getRoot().toPath(),ACCOUNT,net.runelite.http.api.RuneLiteAPI.GSON);
        String json=saved().replace("150","100");
        long stock=book.positions.get(1763).quantity(); int fills=book.fills.size();
        archive.save(HistoryArchive.RUNELITE,parse(json),json,NOW);
        HistoryArchive.View v=archive.view(book);
        assertEquals("Possible match in live journal",v.saved.get(0).overlap);
        assertEquals(stock,book.positions.get(1763).quantity()); assertEquals(fills,book.fills.size());
        Path csv=temp.getRoot().toPath().resolve("export.csv"); archive.export(csv,book);
        assertTrue(Files.readString(csv).contains("Possible match in live journal"));
    }
    @Test public void badRecordsAndFutureDatesAreRejected() {
        for (String json:new String[]{saved().replace("150","-1"),saved().replace(Long.toString(NOW-1000),Long.toString(NOW+120000)),"[{\"i\":1}]"}) {
            try { parse(json); fail(); } catch (RuntimeException expected) { }
        }
    }
}
