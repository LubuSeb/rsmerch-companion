package com.rsmerch;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Public market data only. No game account data is sent to this service. */
final class WikiClient {
    static final String BASE="https://prices.runescape.wiki/api/v1/osrs/";
    private final OkHttpClient http;
    private final Map<String,Cached> cache=new HashMap<>();
    private long lastRequest;
    private static final class Cached { long at; JsonElement data; }

    WikiClient(OkHttpClient http) {
        this.http=http.newBuilder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build();
    }

    synchronized JsonElement get(String route,long ttl) throws IOException {
        if (!route.matches("(?:latest|mapping|5m|1h(?:\\?timestamp=[0-9]+)?|timeseries\\?timestep=1h&id=[0-9]+)")) {
            throw new IOException("Unsupported Wiki endpoint");
        }
        long now=System.currentTimeMillis(); Cached existing=cache.get(route);
        if (existing!=null && now-existing.at>=0 && now-existing.at<ttl) { return existing.data; }
        long delay=300-(now-lastRequest);
        if (delay>0) { try { Thread.sleep(delay); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Market request cancelled",ex); } }
        if (Thread.currentThread().isInterrupted()) { throw new IOException("Market request cancelled"); }
        lastRequest=System.currentTimeMillis();
        Request request=new Request.Builder().url(BASE+route)
            .header("User-Agent","RSMerch Companion/0.5.0 (+https://github.com/LubuSeb/rsmerch-companion)").build();
        try (Response response=http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body()==null) { throw new IOException("Wiki price feed returned HTTP "+response.code()+". Try again later."); }
            if (response.body().contentLength()>12_000_000) { throw new IOException("Wiki response exceeded the size limit"); }
            String text=response.body().string(); if (text.length()>12_000_000) { throw new IOException("Wiki response exceeded the size limit"); }
            JsonElement data=new JsonParser().parse(text);
            if (!(data.isJsonArray() && route.equals("mapping")) && !(data.isJsonObject() && data.getAsJsonObject().has("data"))) {
                throw new IOException("Wiki response did not contain market data");
            }
            Cached saved=new Cached(); saved.at=System.currentTimeMillis(); saved.data=data; cache.put(route,saved);
            if (cache.size()>200) { cache.entrySet().removeIf(e -> saved.at-e.getValue().at>2*Book.DAY); }
            return data;
        } catch (JsonParseException | IllegalStateException ex) { throw new IOException("Wiki response could not be read",ex); }
    }
    static long number(JsonObject object,String key) {
        if (object==null || !object.has(key) || object.get(key).isJsonNull()) { return 0; }
        return object.get(key).getAsLong();
    }
    static JsonObject object(JsonObject parent,String key) {
        if (parent==null || !parent.has(key) || !parent.get(key).isJsonObject()) { return new JsonObject(); }
        return parent.getAsJsonObject(key);
    }
}
