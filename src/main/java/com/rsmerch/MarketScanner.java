package com.rsmerch;

import com.google.gson.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** User-triggered screening of public reported transactions. No game actions. */
final class MarketScanner {
    private final WikiClient client;
    MarketScanner(WikiClient client) { this.client=client; }
    JsonObject scan(long budget,boolean members,Consumer<String> progress) throws IOException {
        if (budget<=0 || budget>Integer.MAX_VALUE) { throw new IOException("Enter a valid free-cash budget"); }
        long now=System.currentTimeMillis(),hour=now/3_600_000*3600-3600;
        progress.accept("Fetching Wiki prices and item mappings…");
        JsonArray mapping=client.get("mapping",Book.DAY).getAsJsonArray();
        JsonObject latest=client.get("latest",60_000).getAsJsonObject(),five=client.get("5m",60_000).getAsJsonObject();
        JsonObject quotes=WikiClient.object(latest,"data"),recent=WikiClient.object(five,"data"); long newest=0;
        for (Map.Entry<String,JsonElement> quote:quotes.entrySet()) { newest=Math.max(newest,Math.max(n(quote.getValue().getAsJsonObject(),"highTime"),n(quote.getValue().getAsJsonObject(),"lowTime"))); }
        if (now/1000-newest>900 || newest>now/1000+60 || now/1000-n(five,"timestamp")>1200 || n(five,"timestamp")>now/1000+60) { throw new IOException("Wiki feed is stale. Previous results retained."); }
        Map<Integer,List<JsonObject>> histories=new HashMap<>(); JsonObject currentHour=null;
        for (int offset=0;offset<24;offset++) {
            interrupted(now); progress.accept("Checking reported volume · "+(offset+1)+" / 24 hours"); long timestamp=hour-offset*3600;
            JsonObject block=client.get("1h?timestamp="+timestamp,Book.DAY).getAsJsonObject();
            if (n(block,"timestamp")!=timestamp) { throw new IOException("Wiki returned a mismatched hourly interval"); }
            if (offset==0) { currentHour=WikiClient.object(block,"data"); }
            for (Map.Entry<String,JsonElement> e:WikiClient.object(block,"data").entrySet()) {
                JsonObject row=e.getValue().getAsJsonObject().deepCopy(); row.addProperty("timestamp",timestamp);
                histories.computeIfAbsent(Integer.parseInt(e.getKey()),id -> new ArrayList<>()).add(row);
            }
        }
        List<JsonObject> candidates=new ArrayList<>();
        for (JsonElement element:mapping) {
            JsonObject item=element.getAsJsonObject(); int id=(int)n(item,"id"); String key=Integer.toString(id);
            if (!members && item.has("members") && item.get("members").getAsBoolean()) { continue; }
            JsonObject result=candidate(item,WikiClient.object(quotes,key),WikiClient.object(recent,key),WikiClient.object(currentHour,key),histories.getOrDefault(id,Collections.emptyList()),budget,now);
            if (result!=null) { candidates.add(result); }
        }
        candidates.sort(Comparator.comparingDouble((JsonObject r) -> r.get("score").getAsDouble()).reversed());
        JsonArray accepted=new JsonArray(); int checked=0;
        for (JsonObject candidate:candidates.subList(0,Math.min(70,candidates.size()))) {
            interrupted(now); progress.accept("Checking multi-day history · "+(++checked)+" / "+Math.min(70,candidates.size())); int id=(int)n(candidate,"id");
            List<JsonObject> rows=new ArrayList<>(); JsonObject series=client.get("timeseries?timestep=1h&id="+id,30*60_000).getAsJsonObject(); Set<Long> timestamps=new HashSet<>();
            for (JsonElement el:series.getAsJsonArray("data")) {
                JsonObject row=el.getAsJsonObject(); long timestamp=n(row,"timestamp");
                if (timestamp>0 && timestamp<=hour && timestamps.add(timestamp)) { rows.add(row); }
            }
            Stats stats=stats(rows,id,now);
            if (stats.active<72 || stats.latest<hour-3600 || stats.positive<0.65 || stats.dispersion>0.20) { continue; }
            JsonArray chart=new JsonArray(); rows.sort(Comparator.comparingLong(r -> n(r,"timestamp"))); int step=Math.max(1,rows.size()/64);
            for (int i=0;i<rows.size();i+=step) {
                JsonObject row=rows.get(i); if (!valid(row)) { continue; } JsonObject point=new JsonObject();
                point.addProperty("low",n(row,"avgLowPrice")); point.addProperty("high",n(row,"avgHighPrice")); chart.add(point);
            }
            candidate.add("chart",chart); accepted.add(candidate);
        }
        JsonObject report=new JsonObject(); report.addProperty("schema","rsmerch-market-v1"); report.addProperty("generated_at",Instant.ofEpochMilli(now).toString());
        JsonObject times=new JsonObject(); times.addProperty("hourly_block",Instant.ofEpochSecond(hour).toString()); report.add("source_times",times);
        report.addProperty("mapped_items",mapping.size()); report.addProperty("history_candidates",checked); report.add("opportunities",accepted); return report;
    }
    static JsonObject candidate(JsonObject item,JsonObject quote,JsonObject five,JsonObject hour,List<JsonObject> rows,long budget,long now) {
        int id=(int)n(item,"id"); long low=n(quote,"low"),high=n(quote,"high"),limit=n(item,"limit");
        long lowAge=now/1000-n(quote,"lowTime"),highAge=now/1000-n(quote,"highTime");
        if (id<=0 || limit<=0 || low<=0 || high<=0 || low>Integer.MAX_VALUE || high>Integer.MAX_VALUE || Math.min(lowAge,highAge)<-60 || Math.max(lowAge,highAge)>1800) { return null; }
        for (JsonObject window:Arrays.asList(five,hour)) { if (valid(window)) { low=Math.max(low,n(window,"avgLowPrice")); high=Math.min(high,n(window,"avgHighPrice")); } }
        long tick=Math.max(1,low/1000),buy=low+tick,sell=high-tick,cap=budget/4;
        if (buy<=0 || buy>cap || sell<=0) { return null; }
        long net=sell-buy-Tax.unit(id,sell,now); double roi=(double)net/buy;
        if (roi<0.0075 || roi>0.30 || net<=0) { return null; }
        Stats stats=stats(rows,id,now); long highVolume=0,lowVolume=0;
        for (JsonObject row:rows) { highVolume+=n(row,"highPriceVolume"); lowVolume+=n(row,"lowPriceVolume"); }
        if (stats.active<12 || Math.min(highVolume,lowVolume)<200 || stats.positive<0.65 || stats.trend<-.08 || stats.dispersion>.18 || Math.abs((buy+sell)/2.0/stats.median-1)>.20) { return null; }
        // Missing item-hours have zero reported volume; rates always span 24 hours.
        double highRate=highVolume/24.0,lowRate=lowVolume/24.0;
        long flow=(long)(.01*12/(1/highRate+1/lowRate)),quantity=Math.min(limit,Math.min(cap/buy,flow)); if (quantity<1) { return null; }
        double hours=quantity/(lowRate*.01)+quantity/(highRate*.01);
        JsonObject c=new JsonObject(); c.addProperty("id",id); c.addProperty("name",item.get("name").getAsString());
        c.addProperty("buy",buy); c.addProperty("sell",sell); c.addProperty("quantity",quantity); c.addProperty("test_quantity",Math.min(quantity,Math.max(1,Math.min(10,10000/buy))));
        c.addProperty("low_time",Instant.ofEpochSecond(n(quote,"lowTime")).toString()); c.addProperty("high_time",Instant.ofEpochSecond(n(quote,"highTime")).toString());
        c.addProperty("buy_limit_4h",limit); c.addProperty("reported_high_volume_24h",highVolume); c.addProperty("reported_low_volume_24h",lowVolume);
        c.addProperty("score",quantity*net*stats.positive/(1+stats.dispersion*12)/Math.sqrt(Math.max(hours,2))); return c;
    }
    static final class Stats { int active; long latest; double median,positive,dispersion,trend; }
    static Stats stats(List<JsonObject> rows,int id,long now) {
        Stats stats=new Stats(); List<Double> mids=new ArrayList<>(),recent=new ArrayList<>(),previous=new ArrayList<>(); int positive=0;
        for (JsonObject row:rows) { stats.latest=Math.max(stats.latest,n(row,"timestamp")); }
        for (JsonObject row:rows) {
            if (!valid(row)) { continue; } long low=n(row,"avgLowPrice"),high=n(row,"avgHighPrice"); double mid=(low+high)/2.0;
            mids.add(mid); if (high-low-Tax.unit(id,high,now)>0) { positive++; }
            long age=stats.latest-n(row,"timestamp"); if (age<6*3600) { recent.add(mid); } else if (age<24*3600) { previous.add(mid); }
        }
        stats.active=mids.size(); if (mids.isEmpty()) { return stats; }
        stats.median=median(mids); stats.positive=(double)positive/mids.size(); List<Double> differences=new ArrayList<>();
        for (double mid:mids) { differences.add(Math.abs(mid-stats.median)); } stats.dispersion=median(differences)/stats.median;
        stats.trend=recent.isEmpty() || previous.isEmpty() ? 0 : median(recent)/median(previous)-1; return stats;
    }
    private static double median(List<Double> values) { List<Double> sorted=new ArrayList<>(values); Collections.sort(sorted); int size=sorted.size(); return size%2==0 ? (sorted.get(size/2-1)+sorted.get(size/2))/2 : sorted.get(size/2); }
    static boolean valid(JsonObject row) { return n(row,"avgHighPrice")>0 && n(row,"avgLowPrice")>0 && n(row,"highPriceVolume")>=3 && n(row,"lowPriceVolume")>=3; }
    private static long n(JsonObject row,String key) { return WikiClient.number(row,key); }
    private static void interrupted(long start) throws IOException {
        if (Thread.currentThread().isInterrupted()) { throw new IOException("Market scan cancelled"); }
        if (System.currentTimeMillis()-start>5*60_000) { throw new IOException("Market scan timed out. Previous results retained."); }
    }
}
