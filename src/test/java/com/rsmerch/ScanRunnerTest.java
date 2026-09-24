package com.rsmerch;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.JsonObject;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScanRunnerTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void missingInstallationReturnsUsefulErrorWithoutStartingProcess() throws Exception {
        CountDownLatch complete=new CountDownLatch(1); AtomicReference<String> error=new AtomicReference<>();
        try (ScanRunner runner=new ScanRunner()) {
            assertTrue(runner.start("",1_000_000,s -> {},s -> { error.set(s); complete.countDown(); }));
            assertTrue(complete.await(3,TimeUnit.SECONDS));
            assertTrue(error.get().contains("location is missing"));
        }
    }
    @Test public void shutdownRejectsFurtherScans() {
        ScanRunner runner=new ScanRunner(); runner.close();
        assertFalse(runner.start("anything",1_000_000,s -> {},s -> {}));
    }
    @Test public void failedScanRetainsPreviousReportAndRejectsOverlappingScan() throws Exception {
        Path report=temp.newFile("report.json").toPath(); Files.writeString(report,"previous report");
        CountDownLatch running=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicReference<String> failure=new AtomicReference<>();
        try (ScanRunner runner=new ScanRunner((cash,members,progress) -> { running.countDown(); release.await(); throw new java.io.IOException("Feed unavailable"); })) {
            assertTrue(runner.start(report.toString(),1_000_000,s -> {},s -> { failure.set(s); done.countDown(); }));
            assertTrue(running.await(3,TimeUnit.SECONDS)); assertFalse(runner.start(report.toString(),1_000_000,s -> {},s -> {}));
            release.countDown(); assertTrue(done.await(3,TimeUnit.SECONDS)); assertEquals("Feed unavailable",failure.get());
            assertEquals("previous report",Files.readString(report));
        }
    }
    @Test public void completedNativeScanWritesTheRequestedReport() throws Exception {
        Path report=temp.getRoot().toPath().resolve("report.json"); CountDownLatch done=new CountDownLatch(1); AtomicReference<String> failure=new AtomicReference<>();
        try (ScanRunner runner=new ScanRunner((cash,members,progress) -> { JsonObject data=new JsonObject(); data.addProperty("budget",cash); return data; })) {
            runner.start(report.toString(),12345,s -> {},s -> { failure.set(s); done.countDown(); });
            assertTrue(done.await(3,TimeUnit.SECONDS)); assertNull(failure.get()); assertTrue(Files.readString(report).contains("12345"));
        }
    }
}
