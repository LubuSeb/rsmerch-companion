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
        try (Journal j=new Journal(file)) {
            assertEquals(0,j.recordCount()); assertEquals(0,j.lastSavedAt());
            Event empty=BookTest.offer("0",0,"EMPTY",0,0); assertTrue(j.append(empty)); assertFalse(j.append(empty));
            assertEquals(1,j.recordCount()); assertTrue(j.lastSavedAt()>0);
            j.append(BookTest.offer("1",1,"BUYING",0,0)); j.append(BookTest.offer("2",2,"BOUGHT",10,4499));
            Event conflict=BookTest.offer("2",2,"BOUGHT",11,4949);
            assertThrows(IOException.class,() -> j.append(conflict));
            assertEquals(10,j.book.position(1763,null).quantity());
            j.exportFills(temp.getRoot().toPath().resolve("fills.csv"));
        }
        try (Journal j=new Journal(file)) {
            assertEquals(4499,j.book.position(1763,null).knownCost(),0.001); assertEquals(1,j.book.fills.size());
            assertEquals(3,j.recordCount()); assertTrue(j.lastSavedAt()>0);
        }
        assertTrue(Files.readString(temp.getRoot().toPath().resolve("fills.csv")).contains(",true,10,4499,0,"));
    }
    @Test public void secondWriterIsRejectedWithoutDamagingFirst() throws Exception {
        Path file=temp.getRoot().toPath().resolve("events.jsonl");
        try (Journal j=new Journal(file)) {
            assertThrows(OverlappingFileLockException.class,() -> new Journal(file));
            assertTrue(j.append(BookTest.offer("0",0,"EMPTY",0,0)));
        }
    }
    @Test public void truncatedOrCorruptJournalFailsClosed() throws Exception {
        Path file=temp.getRoot().toPath().resolve("events.jsonl");
        Files.writeString(file,"{\"version\":1");
        assertThrows(IOException.class,() -> new Journal(file));
        assertEquals("{\"version\":1",Files.readString(file));
        Files.writeString(file,"{}\n");
        assertThrows(IOException.class,() -> new Journal(file));
    }
    @Test public void stableAccountKeysAreSeparatedAndContainNoDisplayNames() {
        assertEquals(RsMerchPlugin.accountKey(7),RsMerchPlugin.accountKey(7));
        assertNotEquals(RsMerchPlugin.accountKey(7),RsMerchPlugin.accountKey(8));
        assertTrue(RsMerchPlugin.accountKey(-123).matches("[0-9a-f]{24}"));
    }
}
