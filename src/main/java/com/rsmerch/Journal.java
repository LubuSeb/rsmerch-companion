package com.rsmerch;

import com.google.gson.Gson;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Single-writer append-only journal. Persist first, then update the in-memory book. */
public final class Journal implements AutoCloseable {
    public final Book book = new Book();
    public final Path path;
    private final Gson gson;
    private final Map<String,String> seen = new HashMap<>();
    private final FileChannel channel;
    private final FileLock lock;
    private long lastSavedAt;

    public Journal(Path path, Gson gson) throws IOException {
        this.path=path; this.gson=Objects.requireNonNull(gson);
        Files.createDirectories(path.toAbsolutePath().getParent());
        channel=FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock acquired=null;
        try {
            acquired=channel.tryLock();
            if (acquired==null) { throw new IOException("This account journal is already open in another client."); }
            if (channel.size()>100_000_000) { throw new IOException("Journal exceeds 100 MB; archive it before continuing."); }
            if (channel.size()>0) {
                ByteBuffer last=ByteBuffer.allocate(1); channel.read(last,channel.size()-1);
                if (last.array()[0]!='\n') { throw new IOException("Incomplete final journal line. Preserve a copy and repair it before recording."); }
            }
            // Read through the locked handle: Windows disallows a second handle reading this range.
            // The reader intentionally does not own/close the channel; Journal.close() owns it.
            channel.position(0);
            BufferedReader reader=new BufferedReader(Channels.newReader(channel,StandardCharsets.UTF_8.newDecoder(),8192));
            {
                String line; int number=0;
                while ((line=reader.readLine())!=null) {
                    number++;
                    try { replay(gson.fromJson(line,Event.class)); }
                    catch (RuntimeException ex) { throw new IOException("Invalid journal line " + number + "; recording stopped to preserve evidence.",ex); }
                }
            }
            channel.position(channel.size());
            lock=acquired;
            if (!seen.isEmpty()) { lastSavedAt=Files.getLastModifiedTime(path).toMillis(); }
        } catch (IOException | RuntimeException ex) {
            if (acquired!=null) { acquired.release(); }
            channel.close(); throw ex;
        }
    }
    private void replay(Event e) {
        Book.validate(e);
        String json=gson.toJson(e), previous=seen.get(e.id);
        if (previous!=null) {
            if (!previous.equals(json)) { throw new IllegalArgumentException("Conflicting event id"); }
            return;
        }
        book.accept(e); seen.put(e.id,json);
    }
    public boolean append(Event e) throws IOException {
        Book.validate(e);
        if (channel.size()>100_000_000) { throw new IOException("Journal exceeds 100 MB; archive before continuing."); }
        String json=gson.toJson(e), previous=seen.get(e.id);
        if (previous!=null) {
            if (!previous.equals(json)) { throw new IOException("Conflicting event id"); }
            return false;
        }
        ByteBuffer bytes=StandardCharsets.UTF_8.encode(json+"\n");
        while (bytes.hasRemaining()) { channel.write(bytes); }
        channel.force(false);
        replay(e);
        lastSavedAt=System.currentTimeMillis();
        return true;
    }
    public int recordCount() { return seen.size(); }
    public long lastSavedAt() { return lastSavedAt; }
    public void exportFills(Path target) throws IOException {
        try (BufferedWriter w=Files.newBufferedWriter(target,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            w.write("event_id,item_id,buy,quantity,gross_gp,estimated_tax_gp,observed_from_utc,observed_at_utc\n");
            for (Book.Fill f:book.fills) {
                w.write(f.id+","+f.item+","+f.buy+","+f.quantity+","+f.gross+","+f.taxEstimate+","
                    +java.time.Instant.ofEpochMilli(f.from)+","+java.time.Instant.ofEpochMilli(f.at)+"\n");
            }
        }
    }
    @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
