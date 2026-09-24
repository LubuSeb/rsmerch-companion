package com.rsmerch;

import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.IntFunction;

/** Evidence archive. Historical observations never change live stock or profit. */
public final class HistoryArchive {
    static final String RUNELITE="runelite", SCREEN="ge-screen";
    private final Path directory;
    private final String account;
    private final Gson gson;
    private final Map<String,Snapshot> snapshots=new LinkedHashMap<>();
    public static final class Row {
        public int item;
        public String name;
        public boolean buy;
        public long quantity,price,recordedAt;
        public Long gross,tax;
        public String raw;
        String signature() { return item+":"+buy+":"+quantity+":"+price+":"+recordedAt+":"+gross+":"+tax; }
    }
    public static final class Snapshot {
        public int version=1;
        public String account,source,id;
        public long capturedAt;
        public String raw;
        public List<Row> rows=new ArrayList<>();
    }
    public static final class Entry {
        public Row row;
        public String id,source,overlap;
        public long capturedAt;
    }
    public static final class View {
        public final List<Entry> saved=new ArrayList<>(), screen=new ArrayList<>();
        public final List<Capture> captures=new ArrayList<>();
        public long screenCapturedAt;
        public int screenSnapshots;
        public String error;
    }
    public static final class Capture {
        public String id;
        public long at;
        public final List<Entry> entries=new ArrayList<>();
        @Override public String toString() { return DeskView.time(at)+" · "+entries.size()+" rows"; }
    }
    public HistoryArchive(Path accountDirectory,String account,Gson gson) throws IOException {
        if (!account.matches("[0-9a-f]{24}")) { throw new IOException("Invalid archive account key"); }
        this.directory=accountDirectory.resolve("history-v1"); this.account=account; this.gson=Objects.requireNonNull(gson);
        if (Files.exists(directory)) {
            try (DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*.json")) {
                for (Path file:files) {
                    if (Files.size(file)>10_000_000) { throw new IOException("History snapshot is too large"); }
                    try {
                        Snapshot s=gson.fromJson(Files.readString(file),Snapshot.class); validate(s);
                        if (!file.getFileName().toString().equals(s.source+"-"+s.id+".json")) { throw new IOException("History filename does not match content"); }
                        snapshots.put(s.id,s);
                    } catch (RuntimeException ex) { throw new IOException("Unreadable history snapshot: "+file.getFileName(),ex); }
                }
            }
        }
    }
    public boolean save(String source,List<Row> rows,String raw,long capturedAt) throws IOException {
        Snapshot s=new Snapshot(); s.account=account; s.source=source; s.rows=rows; s.raw=raw; s.capturedAt=capturedAt; s.id=digest(source,rows); validate(s);
        if (snapshots.containsKey(s.id)) { return false; }
        Files.createDirectories(directory);
        Path target=directory.resolve(source+"-"+s.id+".json");
        if (!Files.exists(target)) {
            Path temp=Files.createTempFile(directory,"pending-",".tmp");
            try {
                try (FileChannel out=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                    ByteBuffer bytes=StandardCharsets.UTF_8.encode(gson.toJson(s)+"\n"); while (bytes.hasRemaining()) { out.write(bytes); } out.force(true);
                }
                try { Files.move(temp,target); }
                catch (FileAlreadyExistsException ignored) { /* Another identical import won; read its committed snapshot below. */ }
            } finally { Files.deleteIfExists(temp); }
        }
        // Read the committed snapshot, preserving its first capture time on repeat imports.
        Snapshot committed=gson.fromJson(Files.readString(target),Snapshot.class); validate(committed); snapshots.put(committed.id,committed); return true;
    }
    private void validate(Snapshot s) throws IOException {
        if (s==null || s.version!=1 || !account.equals(s.account) || !(RUNELITE.equals(s.source) || SCREEN.equals(s.source))
            || s.rows==null || s.rows.size()>5000 || s.capturedAt<=0) { throw new IOException("Invalid or mismatched history account"); }
        for (Row r:s.rows) { validateRow(r); }
        if (!digest(s.source,s.rows).equals(s.id)) { throw new IOException("History snapshot checksum mismatch"); }
    }
    static void validateRow(Row r) {
        if (r==null || r.item<=0 || r.quantity<=0 || r.quantity>Integer.MAX_VALUE || r.price<0 || r.price>Integer.MAX_VALUE
            || r.recordedAt<0 || (r.gross!=null && r.gross<0) || (r.tax!=null && (r.gross==null || r.tax<0 || r.tax>r.gross))) {
            throw new IllegalArgumentException("Invalid history row");
        }
    }
    static String digest(String source,List<Row> rows) {
        StringBuilder text=new StringBuilder(source); for (Row r:rows) { text.append('|').append(r.signature()); }
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(); for (byte b:bytes) { hex.append(String.format(Locale.ROOT,"%02x",b)); } return hex.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public static List<Row> parseRuneLite(String json,IntFunction<String> names,long now) {
        JsonArray array=new JsonParser().parse(json).getAsJsonArray(); if (array.size()>5000) { throw new IllegalArgumentException("Too many saved trades"); }
        List<Row> rows=new ArrayList<>();
        for (JsonElement el:array) {
            JsonObject o=el.getAsJsonObject(); Row r=new Row(); r.item=o.get("i").getAsInt(); r.buy=o.get("b").getAsBoolean();
            r.quantity=o.get("q").getAsLong(); r.price=o.get("p").getAsLong(); r.recordedAt=o.get("t").getAsLong();
            if (r.recordedAt<=0 || r.recordedAt>now+60_000) { throw new IllegalArgumentException("Saved trade time is invalid"); }
            r.name=names.apply(r.item); r.raw=o.toString(); validateRow(r); rows.add(r);
        }
        return rows;
    }
    public View view(Book book) {
        View view=new View(); Map<String,Entry> saved=new LinkedHashMap<>();
        List<Snapshot> ordered=new ArrayList<>(snapshots.values()); ordered.sort(Comparator.comparingLong(s -> s.capturedAt));
        for (Snapshot s:ordered) {
            if (SCREEN.equals(s.source)) { continue; }
            Map<String,Integer> occurrences=new HashMap<>();
            for (Row r:s.rows) {
                String signature=r.signature(); int occurrence=occurrences.merge(signature,1,Integer::sum);
                String id=signature+":"+occurrence;
                if (!saved.containsKey(id)) { saved.put(id,entry(r,s,id,overlap(r,book))); }
            }
        }
        view.saved.addAll(saved.values()); view.saved.sort(Comparator.comparingLong((Entry e) -> e.row.recordedAt).reversed());
        Collections.reverse(ordered);
        for (Snapshot s:ordered) {
            if (!SCREEN.equals(s.source)) { continue; }
            Capture capture=new Capture(); capture.id=s.id; capture.at=s.capturedAt;
            for (int i=0;i<s.rows.size();i++) {
                Row r=s.rows.get(i); String relation=overlap(r,book);
                if (relation==null && saved.values().stream().anyMatch(e -> sameAmounts(r,e.row))) { relation="Possible match in RuneLite archive"; }
                capture.entries.add(entry(r,s,s.id+":"+i,relation));
            }
            view.captures.add(capture);
        }
        view.screenSnapshots=view.captures.size();
        if (!view.captures.isEmpty()) {
            view.screenCapturedAt=view.captures.get(0).at; view.screen.addAll(view.captures.get(0).entries);
        }
        return view;
    }
    private static Entry entry(Row row,Snapshot s,String id,String overlap) { Entry e=new Entry(); e.row=row; e.source=s.source; e.id=id; e.capturedAt=s.capturedAt; e.overlap=overlap; return e; }
    private static boolean sameAmounts(Row a,Row b) { return a.item==b.item && a.buy==b.buy && a.quantity==b.quantity && a.price==b.price; }
    static String overlap(Row r,Book book) {
        for (Book.Trial t:book.trials) {
            if (t.item!=r.item || t.buy!=r.buy || t.filled!=r.quantity || (r.recordedAt>0 && (r.recordedAt<t.started-120_000 || r.recordedAt>(t.ended>0 ? t.ended : book.lastObservation)+120_000))) { continue; }
            long quantity=0,gross=0;
            for (Book.Fill f:book.fills) { if (Objects.equals(f.offerId,t.id)) { quantity+=f.quantity; gross+=f.gross; } }
            if (quantity==r.quantity && gross/quantity==r.price) { return "Possible match in live journal"; }
        }
        return null;
    }
    public void export(Path target,Book book) throws IOException {
        View v=view(book); List<Entry> entries=new ArrayList<>(v.saved); for (Capture c:v.captures) { entries.addAll(c.entries); }
        try (BufferedWriter w=Files.newBufferedWriter(target,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            w.write("source,observation_id,item_id,item_name,buy,quantity,average_gross_gp,exact_gross_gp,exact_tax_gp,recorded_at_utc,captured_at_utc,possible_overlap\n");
            for (Entry e:entries) { Row r=e.row;
                w.write(e.source+","+csv(e.id)+","+r.item+","+csv(r.name)+","+r.buy+","+r.quantity+","+r.price+","+(r.gross==null ? "" : r.gross)+","+(r.tax==null ? "" : r.tax)+","+(r.recordedAt==0 ? "" : java.time.Instant.ofEpochMilli(r.recordedAt))+","+java.time.Instant.ofEpochMilli(e.capturedAt)+","+csv(e.overlap)+"\n");
            }
        }
    }
    private static String csv(String text) { return "\""+(text==null ? "" : text.replace("\"","\"\""))+"\""; }
}
