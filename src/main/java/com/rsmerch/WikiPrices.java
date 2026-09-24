package com.rsmerch;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Last reported trades are references, never executable bids or asks. */
final class WikiPrices {
    long fetchedAt,fiveAt,hourAt;
    String error;
    final Map<Integer,Quote> quotes=new HashMap<>();
    static final class Quote {
        long high,low,highAt,lowAt,fiveHigh,fiveLow,fiveHighVolume,fiveLowVolume,hourHigh,hourLow,hourHighVolume,hourLowVolume;
        boolean fresh(long at,long now) { return at>0 && at<=now+60_000 && now-at<=30*60_000; }
    }
    static WikiPrices fetch(WikiClient client) throws IOException {
        long now=System.currentTimeMillis();
        JsonObject latest=client.get("latest",60_000).getAsJsonObject();
        JsonObject five=client.get("5m",60_000).getAsJsonObject();
        long hour=now/3_600_000*3600-3600;
        JsonObject hourly=client.get("1h?timestamp="+hour,60_000).getAsJsonObject();
        if (WikiClient.number(hourly,"timestamp")!=hour) { throw new IOException("Wiki hourly data returned the wrong interval"); }
        return parse(latest,five,hourly,now);
    }
    static WikiPrices parse(JsonObject latest,JsonObject five,JsonObject hour,long now) {
        WikiPrices result=new WikiPrices(); result.fetchedAt=now;
        result.fiveAt=WikiClient.number(five,"timestamp")*1000; result.hourAt=WikiClient.number(hour,"timestamp")*1000;
        JsonObject f=WikiClient.object(five,"data"),h=WikiClient.object(hour,"data");
        for (Map.Entry<String,JsonElement> e:WikiClient.object(latest,"data").entrySet()) {
            int id=Integer.parseInt(e.getKey()); JsonObject last=e.getValue().getAsJsonObject();
            Quote q=new Quote(); q.high=WikiClient.number(last,"high"); q.low=WikiClient.number(last,"low");
            q.highAt=WikiClient.number(last,"highTime")*1000; q.lowAt=WikiClient.number(last,"lowTime")*1000;
            JsonObject a=WikiClient.object(f,e.getKey()),b=WikiClient.object(h,e.getKey());
            q.fiveHigh=WikiClient.number(a,"avgHighPrice"); q.fiveLow=WikiClient.number(a,"avgLowPrice");
            q.fiveHighVolume=WikiClient.number(a,"highPriceVolume"); q.fiveLowVolume=WikiClient.number(a,"lowPriceVolume");
            q.hourHigh=WikiClient.number(b,"avgHighPrice"); q.hourLow=WikiClient.number(b,"avgLowPrice");
            q.hourHighVolume=WikiClient.number(b,"highPriceVolume"); q.hourLowVolume=WikiClient.number(b,"lowPriceVolume");
            if (q.high<0 || q.low<0 || q.high>Integer.MAX_VALUE || q.low>Integer.MAX_VALUE) { continue; }
            result.quotes.put(id,q);
        }
        return result;
    }
    static WikiPrices load(Path path) {
        try {
            if (!Files.exists(path)) { return new WikiPrices(); }
            if (Files.size(path)>12_000_000) { throw new IOException("Saved market data is too large"); }
            WikiPrices result=new Gson().fromJson(Files.readString(path),WikiPrices.class);
            if (result==null || result.quotes==null || result.fetchedAt<=0) { throw new IOException("Saved market data is invalid"); }
            return result;
        } catch (Exception ex) { WikiPrices result=new WikiPrices(); result.error="Saved Wiki prices could not be read. Refresh prices."; return result; }
    }
    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent()); Path temporary=Files.createTempFile(path.getParent(),"wiki-",".tmp");
        try { Files.writeString(temporary,new Gson().toJson(this)); Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
}
