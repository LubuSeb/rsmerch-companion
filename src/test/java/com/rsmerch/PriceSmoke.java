package com.rsmerch;

import java.nio.file.*;
import okhttp3.OkHttpClient;
import net.runelite.http.api.RuneLiteAPI;

/** Explicit public-data integration check. Contains no account input or personal journal reads. */
public final class PriceSmoke {
    public static void main(String[] args) throws Exception {
        OkHttpClient http=new OkHttpClient();
        try {
            WikiClient client=new WikiClient(http); WikiPrices quotes=WikiPrices.fetch(client);
            PriceHistory history=new PriceHistory();
            for (int id:new int[]{1763,9736,10148}) {
                long now=System.currentTimeMillis(); PriceHistory.Item item=PriceHistory.fetch(client,id,now);
                if (item.hours.isEmpty()) { throw new IllegalStateException("No public history for "+id); }
                history=history.with(id,item); PriceGuidance.Advice a=PriceGuidance.assess(id,item,quotes.quotes.get(id),now);
                System.out.println("Item "+id+": "+item.hours.size()+" complete hours; "+a.status+"; "+a.sellLow+"-"+a.sellHigh+" GP");
            }
            Path directory=Paths.get(args[0]); Files.createDirectories(directory);
            history.save(directory.resolve("price-history-smoke.json"),RuneLiteAPI.GSON);
            quotes.save(directory.resolve("price-quotes-smoke.json"),RuneLiteAPI.GSON);
            if (PriceHistory.load(directory.resolve("price-history-smoke.json"),RuneLiteAPI.GSON).items.size()!=3) { throw new IllegalStateException("History cache did not round-trip"); }
        } finally { http.connectionPool().evictAll(); http.dispatcher().executorService().shutdownNow(); }
    }
}
