package com.rsmerch;

/** Versioned local journal record. Monetary totals are gross, before estimated tax. */
public final class Event {
    public int version = 1;
    public String id;
    public String kind;
    public long at;
    public String session;
    public int slot;
    public int item;
    public String name;
    public boolean buy;
    public String state;
    public long price;
    public long total;
    public long quantity;
    public long gross;
    public boolean baseline;
    public Long cost;
    public String source;
    public String note;

    public boolean empty() { return item == 0 || "EMPTY".equals(state); }
    public boolean active() { return "BUYING".equals(state) || "SELLING".equals(state); }

    public boolean sameOffer(Event other) {
        return other != null && item == other.item && buy == other.buy
            && price == other.price && total == other.total && !other.empty();
    }

    public boolean sameSnapshot(Event other) {
        return sameOffer(other) && quantity == other.quantity && gross == other.gross
            && state.equals(other.state);
    }
}
