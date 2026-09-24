package com.rsmerch;

import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class JournalTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void replayMatchesLiveAndDuplicateIdentityIsIdempotent() throws Exception {
        Path file=temp.getRoot().toPath().resolve("events.jsonl");
        try (Journal j=new Journal(file, net.runelite.http.api.RuneLiteAPI.GSON)) {
            assertEquals(0,j.recordCount()); assertEquals(0,j.lastSavedAt());
            Event empty=BookTest.offer("0",0,"EMPTY",0,0); assertTrue(j.append(empty)); assertFalse(j.append(empty));
            assertEquals(1,j.recordCount()); assertTrue(j.lastSavedAt()>0);
            j.append(BookTest.offer("1",1,"BUYING",0,0)); j.append(BookTest.offer("2",2,"BOUGHT",10,4499));
            Event conflict=BookTest.offer("2",2,"BOUGHT",11,4949);
            assertThrows(IOException.class,() -> j.append(conflict));
            assertEquals(10,j.book.position(1763,null).quantity());
            j.exportFills(temp.getRoot().toPath().resolve("fills.csv"));
        }
        try (Journal j=new Journal(file, net.runelite.http.api.RuneLiteAPI.GSON)) {
            assertEquals(4499,j.book.position(1763,null).knownCost(),0.001); assertEquals(1,j.book.fills.size());
            assertEquals(3,j.recordCount()); assertTrue(j.lastSavedAt()>0);
        }
        assertTrue(Files.readString(temp.getRoot().toPath().resolve("fills.csv")).contains(",true,10,4499,0,"));
    }
    @Test public void secondWriterIsRejectedWithoutDamagingFirst() throws Exception {
        Path file=temp.getRoot().toPath().resolve("events.jsonl");
        try (Journal j=new Journal(file, net.runelite.http.api.RuneLiteAPI.GSON)) {
            assertThrows(OverlappingFileLockException.class,() -> new Journal(file, net.runelite.http.api.RuneLiteAPI.GSON));
            assertTrue(j.append(BookTest.offer("0",0,"EMPTY",0,0)));
        }
    }
    @Test public void truncatedOrCorruptJournalFailsClosed() throws Exception {
        Path file=temp.getRoot().toPath().resolve("events.jsonl");
        Files.writeString(file,"{\"version\":1");
        assertThrows(IOException.class,() -> new Journal(file, net.runelite.http.api.RuneLiteAPI.GSON));
        assertEquals("{\"version\":1",Files.readString(file));
        Files.writeString(file,"{}\n");
        assertThrows(IOException.class,() -> new Journal(file, net.runelite.http.api.RuneLiteAPI.GSON));
    }
    @Test public void stableAccountKeysAreSeparatedAndContainNoDisplayNames() {
        assertEquals(RsMerchPlugin.accountKey(7),RsMerchPlugin.accountKey(7));
        assertNotEquals(RsMerchPlugin.accountKey(7),RsMerchPlugin.accountKey(8));
        assertTrue(RsMerchPlugin.accountKey(-123).matches("[0-9a-f]{24}"));
    }
    @Test public void clientGsonReplaysLegacyJournalAndKeepsOneRecordPerLine() throws Exception {
        Path file=temp.getRoot().toPath().resolve("legacy.jsonl");
        Event baseline=BookTest.offer("legacy",0,"EMPTY",0,0);
        Files.writeString(file,new com.google.gson.Gson().toJson(baseline)+"\n");
        try (Journal j=new Journal(file,net.runelite.http.api.RuneLiteAPI.GSON)) {
            assertEquals(1,j.recordCount()); assertFalse(j.append(baseline));
            j.append(BookTest.offer("purchase",1,"BOUGHT",10,4500));
        }
        assertEquals(2,Files.readAllLines(file).size());
        try (Journal j=new Journal(file,net.runelite.http.api.RuneLiteAPI.GSON)) {
            assertEquals(2,j.recordCount()); assertEquals(10,j.book.position(1763,null).quantity());
            assertEquals(4500,j.book.position(1763,null).knownCost(),0.001);
        }
    }
    @Test public void savedValueSnapshotReplaysWithoutBecomingInventoryOrProfit() throws Exception {
        Path file=temp.getRoot().toPath().resolve("value.jsonl");
        Event e=DeskPanel.event("NOTE"); e.source=Book.VALUE_SNAPSHOT_SOURCE; e.cost=123456L; e.gross=50000; e.note="Confirmed trading value";
        try (Journal j=new Journal(file,net.runelite.http.api.RuneLiteAPI.GSON)) { j.append(e); }
        try (Journal j=new Journal(file,net.runelite.http.api.RuneLiteAPI.GSON)) {
            assertEquals(1,j.book.balances.size()); assertEquals(Long.valueOf(123456),j.book.balances.get(0).cost);
            assertTrue(j.book.positions.isEmpty()); assertTrue(j.book.fills.isEmpty());
        }
    }
}
