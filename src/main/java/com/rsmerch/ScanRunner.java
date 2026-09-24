package com.rsmerch;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** User-triggered Java research, isolated from Swing and the trade journal. */
final class ScanRunner implements AutoCloseable {
    interface Task { JsonObject run(long cash,boolean members,Consumer<String> progress) throws Exception; }
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"rsmerch-market-scan"); t.setDaemon(true); return t;
    });
    private final Task task;
    private boolean busy;
    private volatile boolean closed;
    ScanRunner(WikiClient client) { this(new MarketScanner(client)::scan); }
    ScanRunner(Task task) { this.task=task; }
    synchronized boolean start(String report,long cash,Consumer<String> progress,Consumer<String> done) { return start(report,cash,true,progress,done); }
    synchronized boolean start(String report,long cash,boolean members,Consumer<String> progress,Consumer<String> done) {
        if (busy || closed) { return false; } busy=true;
        executor.execute(() -> {
            String failure=null;
            try {
                if (report==null || report.isBlank()) { throw new IOException("Market report location is missing"); }
                JsonObject result=task.run(cash,members,progress);
                if (closed || Thread.currentThread().isInterrupted()) { throw new IOException("Market scan cancelled"); }
                Path path=Paths.get(report).toAbsolutePath(); Files.createDirectories(path.getParent());
                Path temporary=Files.createTempFile(path.getParent(),"market-",".tmp");
                try { Files.writeString(temporary,result.toString()); Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temporary); }
            } catch (Exception ex) { failure=ex.getMessage()==null ? "Market scan failed. Previous results retained." : ex.getMessage(); }
            finally { synchronized (this) { busy=false; } }
            if (!closed) { done.accept(failure); }
        });
        return true;
    }
    @Override public synchronized void close() { closed=true; executor.shutdownNow(); }
}
