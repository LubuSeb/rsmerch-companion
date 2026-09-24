package com.rsmerch;

import java.nio.file.*;
import okhttp3.OkHttpClient;

/** Explicit network smoke check; it only writes a public-price cache. */
public final class WikiSmoke {
    public static void main(String[] args) throws Exception {
        OkHttpClient http=new OkHttpClient();
        try {
            WikiPrices prices=WikiPrices.fetch(new WikiClient(http)); prices.save(Paths.get(args[0]),net.runelite.http.api.RuneLiteAPI.GSON);
            if (prices.quotes.isEmpty()) { throw new IllegalStateException("No Wiki quotes loaded"); }
            System.out.println("Loaded "+prices.quotes.size()+" Wiki items with timestamped last trades and hourly windows.");
        } finally { http.connectionPool().evictAll(); http.dispatcher().executorService().shutdown(); }
    }
}
