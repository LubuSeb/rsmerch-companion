package com.rsmerch;

import java.util.*;

/** Explainable price experiments, not an order book or a fill forecast. */
final class PriceGuidance {
    static final class Advice {
        long buyLow,buyHigh,sellLow,sellHigh,fastExit,highUnits,lowUnits;
        int highHours,lowHours,days;
        long latestHour;
        boolean actionable,buyActionable,falling;
        String status="Analyse this item to load its history",reason;
        final List<String> warnings=new ArrayList<>();
    }
    static Advice assess(int item,PriceHistory.Item history,WikiPrices.Quote quote,long now) {
        Advice a=new Advice();
        if (quote!=null && quote.low>0 && quote.fresh(quote.lowAt,now)) { a.fastExit=quote.low; }
        if (history==null) { return a; }
        if (history.error!=null) { a.warnings.add("Last analysis failed: "+history.error); }
        List<Long> highs=new ArrayList<>(),lows=new ArrayList<>(),recentHigh=new ArrayList<>(),recentLow=new ArrayList<>();
        Set<Long> dates=new HashSet<>();
        for (PriceHistory.Hour h:history.hours) {
            if (h.at<now-7*Book.DAY || h.at+3_600_000>now || h.at<=0) { continue; }
            if (h.high>0 && h.highVolume>0) {
                highs.add(h.high); a.highUnits+=h.highVolume; dates.add(h.at/Book.DAY); a.latestHour=Math.max(a.latestHour,h.at);
                if (h.at>=now-Book.DAY) { recentHigh.add(h.high); }
            }
            if (h.low>0 && h.lowVolume>0) { lows.add(h.low); a.lowUnits+=h.lowVolume; if (h.at>=now-Book.DAY) { recentLow.add(h.low); } }
        }
        a.highHours=highs.size(); a.lowHours=lows.size(); a.days=dates.size();
        if (highs.size()<12 || dates.size()<3 || a.latestHour<now-Book.DAY) {
            a.status="Not enough recent evidence"; a.reason="Need buyer trades in 12 completed hours across 3 days, including the last 24h."; return a;
        }
        a.sellLow=quantile(highs,.5); a.sellHigh=quantile(highs,.75);
        a.reason="Patient asks: median to upper quartile of active hourly buy prices over 7 days. Each hour has equal weight.";
        if (recentHigh.size()>=6 && quantile(recentHigh,.5)<a.sellLow*.85) {
            a.falling=true; a.sellLow=quantile(recentHigh,.5); a.sellHigh=quantile(recentHigh,.75);
            a.reason="Recent buying prices fell over 15%; patient range uses the last 24h median to upper quartile.";
            a.warnings.add("Recent decline. Older peaks may no longer be attainable.");
        } else if (recentHigh.size()<6) { a.warnings.add("Few active buying hours in the last day; recovery is unproven."); }
        List<Long> bidSample=recentLow.size()>=6 ? recentLow : lows;
        if (bidSample.size()>=12 || recentLow.size()>=6) { a.buyLow=quantile(bidSample,.25); a.buyHigh=quantile(bidSample,.5); }
        boolean cachedFresh=history.fetchedAt>0 && history.fetchedAt<=now+60_000 && now-history.fetchedAt<=6*3_600_000 && history.error==null;
        a.actionable=cachedFresh && quote!=null && quote.high>0 && quote.fresh(quote.highAt,now);
        a.buyActionable=a.actionable && a.buyLow>0 && quote.low>0 && quote.fresh(quote.lowAt,now);
        a.status=a.actionable ? "Patient price experiment" : "Historical range · refresh before using";
        if (a.buyHigh>0 && a.sellLow-Tax.unit(item,a.sellLow,now)<=a.buyHigh) { a.warnings.add("No positive spread at the conservative ends after tax."); a.buyActionable=false; }
        if (quantile(highs,.9)>quantile(highs,.5)*1.4) { a.warnings.add("Wide swings; upper prices occur unevenly."); }
        a.warnings.add("Public volume can include your own fills. No queue depth or fill-time prediction.");
        return a;
    }
    static long quantile(List<Long> values,double fraction) {
        List<Long> sorted=new ArrayList<>(values); Collections.sort(sorted);
        return sorted.get((int)Math.floor((sorted.size()-1)*fraction));
    }
}
