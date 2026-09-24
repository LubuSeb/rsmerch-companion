package com.rsmerch;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Detached display data: the mutable journal never crosses onto Swing's thread. */
public final class DeskView {
    public String account,error;
    public long cash,committed,now,fillCount;
    public double profit,stockCost;
    public Research research;
    public HistoryArchive.View archive=new HistoryArchive.View();
    public WikiPrices wiki=new WikiPrices();
    public final List<Stock> stocks=new ArrayList<>();
    public final List<Offer> offers=new ArrayList<>();
    public final List<Trade> trades=new ArrayList<>();
    public final List<String> notes=new ArrayList<>();
    public final Map<Integer,String> catalog=new LinkedHashMap<>();
    public static final class Stock {
        public int item;
        public String name,warning;
        public long quantity,known,bought,sold,breakEven;
        public double cost,profit;
    }
    public static final class Offer {
        public int item,slot;
        public String name,state;
        public boolean buy;
        public long price,filled,total;
    }
    public static final class Trade {
        public String name;
        public boolean buy;
        public long quantity,gross,tax,at;
    }
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
    public static String esc(String s) { return s==null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    public static String number(double n) { return String.format(Locale.UK,"%,.0f",n); }
    public static String time(long at) { return at<=0 ? "not observed" : TIME.format(Instant.ofEpochMilli(at)); }
    public static String age(long at,long now) {
        if (at<=0) { return "unknown"; }
        long minutes=Math.max(0,now-at)/60_000;
        if (minutes<1) { return "just now"; }
        if (minutes<60) { return minutes+"m ago"; }
        if (minutes<48*60) { return minutes/60+"h ago"; }
        return minutes/(24*60)+"d ago";
    }
    public static DeskView build(Book b,Research r,long now,long cash,int stockDays,String account,String error) {
        DeskView v=new DeskView(); v.research=r; v.account=account; v.error=error; v.cash=cash;
        v.now=now; v.committed=b.committed(0); v.fillCount=b.fills.size();
        for (Book.Position p:b.positions.values()) {
            v.profit+=p.matchedProfit; v.stockCost+=p.knownCost(); v.catalog.put(p.item,p.name);
            if (p.quantity()==0 && p.unbackedSales==0) { continue; }
            Stock s=new Stock(); s.item=p.item; s.name=p.name; s.quantity=p.quantity(); s.known=p.knownQuantity();
            s.cost=p.knownCost(); s.profit=p.matchedProfit;
            s.bought=b.volume(p.item,true,now-Book.DAY,now); s.sold=b.volume(p.item,false,now-Book.DAY,now);
            if (s.known==s.quantity && s.quantity>0) { s.breakEven=Tax.breakEven(s.item,s.cost/s.quantity,now); }
            if (s.known!=s.quantity || p.unbackedSales>0) { s.warning="Cost missing · set your opening stock"; }
            else if (p.reconciledAt==0) { s.warning="Recorded stock · opening balance unset"; }
            else if (b.firstObservation>0 && now-b.firstObservation>=Book.DAY && s.sold==0) { s.warning="No recorded sales in 24h"; }
            else if (b.firstObservation>0 && now-b.firstObservation>=Book.DAY && s.sold>0 && (double)s.quantity/s.sold>stockDays) { s.warning="Over "+stockDays+" days at recorded sales pace"; }
            v.stocks.add(s);
        }
        for (Event e:b.slots.values()) {
            if (e.empty()) { continue; }
            Offer o=new Offer(); o.item=e.item; o.slot=e.slot; o.name=e.name; o.state=e.state;
            o.buy=e.buy; o.price=e.price; o.filled=e.quantity; o.total=e.total; v.offers.add(o); v.catalog.put(e.item,e.name);
        }
        v.offers.sort(Comparator.comparingInt(o -> o.slot));
        for (int i=b.fills.size()-1;i>=Math.max(0,b.fills.size()-60);i--) {
            Book.Fill f=b.fills.get(i); Trade t=new Trade(); t.name=f.name; t.buy=f.buy; t.quantity=f.quantity;
            t.gross=f.gross; t.tax=f.taxEstimate; t.at=f.at; v.trades.add(t);
        }
        for (int i=b.notes.size()-1;i>=Math.max(0,b.notes.size()-10);i--) { v.notes.add(b.notes.get(i).note); }
        for (Research.Candidate c:r.candidates) { v.catalog.put(c.item,c.name); }
        return v;
    }
}
