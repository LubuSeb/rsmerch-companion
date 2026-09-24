package com.rsmerch;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Cached public hourly evidence. Immutable after being handed to the UI. */
final class PriceHistory {
    static final class Hour {
        long at,high,low,highVolume,lowVolume;
    }
    static final class Item {
        long fetchedAt; String error;
        final List<Hour> hours=new ArrayList<>();
    }
    final Map<Integer,Item> items=new HashMap<>();
    static Item fetch(WikiClient client,int item,long now) throws IOException {
        if (item<=0) { throw new IOException("Choose an item first"); }
        return parse(client.get("timeseries?timestep=1h&id="+item,15*60_000).getAsJsonObject(),now);
    }
    static Item parse(JsonObject root,long now) throws IOException {
        if (!root.has("data") || !root.get("data").isJsonArray()) { throw new IOException("Hourly prices are unavailable"); }
        Item result=new Item(); result.fetchedAt=now; TreeMap<Long,Hour> unique=new TreeMap<>();
        for (JsonElement element:root.getAsJsonArray("data")) {
            JsonObject obj=element.getAsJsonObject(); Hour h=new Hour(); h.at=WikiClient.number(obj,"timestamp")*1000;
            if (h.at<=0 || h.at>now/3_600_000*3_600_000-3_600_000 || h.at<now-16*Book.DAY) { continue; }
            h.high=WikiClient.number(obj,"avgHighPrice"); h.low=WikiClient.number(obj,"avgLowPrice");
            h.highVolume=WikiClient.number(obj,"highPriceVolume"); h.lowVolume=WikiClient.number(obj,"lowPriceVolume");
            if (h.high<0 || h.low<0 || h.high>Integer.MAX_VALUE || h.low>Integer.MAX_VALUE || h.highVolume<0 || h.lowVolume<0) { continue; }
            unique.put(h.at,h);
        }
        result.hours.addAll(unique.values()); return result;
    }
    PriceHistory with(int id,Item item) {
        PriceHistory copy=new PriceHistory(); copy.items.putAll(items); copy.items.put(id,item);
        while (copy.items.size()>64) {
            int oldest=copy.items.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().fetchedAt)).get().getKey();
            copy.items.remove(oldest);
        }
        return copy;
    }
    static PriceHistory load(Path path,Gson gson) {
        try {
            if (!Files.exists(path) || Files.size(path)>8_000_000) { return new PriceHistory(); }
            PriceHistory value=gson.fromJson(Files.readString(path),PriceHistory.class);
            if (value==null || value.items==null || value.items.size()>64) { return new PriceHistory(); }
            for (Item item:value.items.values()) { if (item==null || item.hours==null || item.hours.size()>400) { return new PriceHistory(); } }
            return value;
        } catch (Exception ex) { return new PriceHistory(); }
    }
    void save(Path path,Gson gson) throws IOException {
        Files.createDirectories(path.getParent()); Path temp=Files.createTempFile(path.getParent(),"prices-",".tmp");
        try { Files.writeString(temp,gson.toJson(this)); Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
}
