package com.rsmerch;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Read-only local research. A saved quote is never represented as a live order book. */
public final class Research {
    public String status="Find your next trade. Run a market scan to start.";
    public String generatedAt="";
    public boolean fresh;
    public String problem;
    public final List<Candidate> candidates=new ArrayList<>();
    public final List<String> history=new ArrayList<>();
    public static final class Candidate {
        public int item;
        public String name;
        public long buy, sell, testQuantity, highVolume, lowVolume;
        public long ownBuys, ownSales, net, limit;
        public double roi;
        public boolean fresh, held;
        public final List<double[]> chart=new ArrayList<>();
    }

    public static Research read(String scanPath, String historyPath, Book book, long now, long cash) {
        Research r=new Research();
        if (scanPath!=null && !scanPath.isBlank()) {
            try {
                JsonObject root=readObject(Paths.get(scanPath));
                r.generatedAt=root.get("generated_at").getAsString();
                long generated=timestamp(r.generatedAt);
                long volumeEnd=root.has("source_times") ? timestamp(root.getAsJsonObject("source_times")
                    .get("hourly_block").getAsString())+3_600_000L : generated/3_600_000L*3_600_000L;
                r.fresh=now>=generated && now-generated<=30*60_000L;
                r.status=r.fresh ? "Fresh research · validate with a small test" : "Prices have aged. Scan again before testing.";
                for (JsonElement element:root.getAsJsonArray("opportunities")) {
                    JsonObject o=element.getAsJsonObject();
                    Candidate c=new Candidate();
                    c.item=o.get("id").getAsInt(); c.name=o.get("name").getAsString();
                    c.buy=o.get("buy").getAsLong(); c.sell=o.get("sell").getAsLong();
                    if (c.buy<=0 || c.sell<=0 || c.buy>Integer.MAX_VALUE || c.sell>Integer.MAX_VALUE) { continue; }
                    long lowTime=timestamp(o.get("low_time").getAsString());
                    long highTime=timestamp(o.get("high_time").getAsString());
                    c.fresh=r.fresh && lowTime<=now && highTime<=now
                        && now-lowTime<=30*60_000L && now-highTime<=30*60_000L;
                    c.highVolume=o.get("reported_high_volume_24h").getAsLong();
                    c.lowVolume=o.get("reported_low_volume_24h").getAsLong();
                    Book.Position p=book.positions.get(c.item);
                    c.held=(p!=null && (p.quantity()>0 || p.unbackedSales>0)) || book.committed(c.item)>0;
                    c.net=c.sell-c.buy-Tax.unit(c.item,c.sell,now);
                    c.roi=(double)c.net/c.buy;
                    c.limit=o.get("buy_limit_4h").getAsLong();
                    if (o.has("chart")) {
                        for (JsonElement point:o.getAsJsonArray("chart")) {
                            JsonObject pair=point.getAsJsonObject();
                            if (pair.has("high") && !pair.get("high").isJsonNull() && pair.has("low") && !pair.get("low").isJsonNull()) {
                                c.chart.add(new double[]{pair.get("low").getAsDouble(),pair.get("high").getAsDouble()});
                            }
                        }
                    }
                    double exposure=(p==null ? 0 : p.knownCost())+book.committed(c.item);
                    long room=Math.max(0,(long)(cash*0.25-exposure));
                    boolean unknownStock=p!=null && (p.quantity()!=p.knownQuantity() || p.unbackedSales>0);
                    c.testQuantity=c.fresh && !unknownStock ? Math.max(0,Math.min(
                        Math.min(o.get("test_quantity").getAsLong(),o.get("quantity").getAsLong()),room/c.buy)) : 0;
                    // A total item limit is a ceiling, not knowledge of this account's remaining allowance.
                    c.testQuantity=Math.min(c.testQuantity,Math.max(0,o.get("buy_limit_4h").getAsLong()));
                    c.ownBuys=book.volume(c.item,true,volumeEnd-Book.DAY,volumeEnd-1);
                    c.ownSales=book.volume(c.item,false,volumeEnd-Book.DAY,volumeEnd-1);
                    r.candidates.add(c);
                    if (r.candidates.size()>=50) { break; }
                }
            } catch (Exception ex) { r.problem=ex.toString(); r.status="No usable scan yet. Scan the market to find opportunities."; r.fresh=false; r.candidates.clear(); }
        }
        if (historyPath!=null && !historyPath.isBlank()) {
            try {
                JsonObject h=readObject(Paths.get(historyPath));
                if (!"rsmerch-history-v1".equals(h.get("schema").getAsString())) { throw new IOException("Unknown history schema"); }
                r.history.add("Imported archive · " + h.getAsJsonArray("records").size()+" observations");
                r.history.add("Separate from live fills and stock. Reconcile opening quantities before relying on balances.");
                for (JsonElement item:h.getAsJsonArray("items")) {
                    JsonObject i=item.getAsJsonObject();
                    r.history.add(i.get("name").getAsString()+": bought "+i.get("bought").getAsLong()
                        +", sold "+i.get("sold").getAsLong()+" in export");
                    if (r.history.size()>=10) { break; }
                }
                for (JsonElement warning:h.getAsJsonArray("warnings")) { r.history.add(warning.getAsString()); }
            } catch (Exception ex) { r.history.add("Import unreadable. Choose the normalized import_history.py output."); }
        }
        return r;
    }

    static long timestamp(String value) { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); }

    private static JsonObject readObject(Path path) throws IOException {
        if (Files.size(path)>30_000_000) { throw new IOException("Research file exceeds 30 MB"); }
        try (Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }
}
