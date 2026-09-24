package com.rsmerch;

import java.util.*;

/** Replayable accounting model. A recorded balance is never a claim to see the bank. */
public final class Book {
    public static final long DAY = 86_400_000L;
    public final Map<Integer, Position> positions = new TreeMap<>();
    public final Map<Integer, Event> slots = new TreeMap<>();
    public final List<Fill> fills = new ArrayList<>();
    public final List<String> notices = new ArrayList<>();
    public final List<Event> notes = new ArrayList<>();
    private final Map<Integer, String> offerIds = new HashMap<>();
    public final List<Trial> trials = new ArrayList<>();
    private final Map<Integer, Trial> openTrials = new HashMap<>();
    public long firstObservation;
    public long lastObservation;
    public long observedMillis;
    private long sessionStart;
    private String currentSession;

    public static final class Lot {
        long quantity;
        double unitCost;
        boolean known;
        long at;
        String source;
        Lot(long q, double c, boolean k, long t, String s) { quantity=q; unitCost=c; known=k; at=t; source=s; }
    }
    public static final class Position {
        public int item;
        public String name;
        public final Deque<Lot> lots = new ArrayDeque<>();
        public long unbackedSales;
        public long reconciledAt;
        public double matchedProfit;
        public long matchedSales;
        public long unknownCostSales;
        public long lastBuy;
        public long lastSell;
        public long quantity() { return lots.stream().mapToLong(l -> l.quantity).sum(); }
        public long knownQuantity() { return lots.stream().filter(l -> l.known).mapToLong(l -> l.quantity).sum(); }
        public double knownCost() { return lots.stream().filter(l -> l.known).mapToDouble(l -> l.quantity*l.unitCost).sum(); }
        public long oldest() { return lots.isEmpty() ? 0 : lots.peekFirst().at; }
    }
    public static final class Fill {
        public String id;
        public String offerId;
        public int item;
        public String name;
        public boolean buy;
        public long quantity;
        public long gross;
        public long taxEstimate;
        public long from;
        public long at;
    }
    public static final class Trial {
        public String id;
        public int item;
        public String name;
        public boolean buy;
        public long price;
        public long requested;
        public long started;
        public long ended;
        public long filled;
        public long netCash;
        public boolean startKnown;
        public String result = "OPEN";
    }

    public Position position(int item, String name) {
        Position p = positions.computeIfAbsent(item, key -> { Position n = new Position(); n.item=key; return n; });
        if (name != null && !name.isBlank()) { p.name = name; }
        if (p.name == null) { p.name = "Item " + item; }
        return p;
    }

    public void accept(Event e) {
        validate(e);
        if ("NOTE".equals(e.kind)) { notes.add(e); return; }
        if ("OPENING".equals(e.kind) || "STOCK".equals(e.kind)) {
            Position p = position(e.item, e.name);
            if ("OPENING".equals(e.kind)) {
                p.lots.clear(); p.unbackedSales=0; p.reconciledAt=e.at;
            }
            if (e.quantity > 0) {
                p.lots.add(new Lot(e.quantity, e.cost == null ? 0 : (double)e.cost/e.quantity,
                    e.cost != null, e.at, e.source));
            }
            return;
        }
        if ("GAP".equals(e.kind)) {
            closeTrials(e.at, "OBSERVATION GAP");
            slots.clear(); offerIds.clear(); currentSession=null; sessionStart=0;
            notice("Observation gap: offline fills and stock use need reconciliation.");
            return;
        }
        if (firstObservation == 0) { firstObservation=e.at; }
        if (!Objects.equals(currentSession, e.session)) {
            closeTrials(lastObservation, "OBSERVATION GAP");
            slots.clear(); offerIds.clear(); currentSession=e.session; sessionStart=e.at;
        }
        if (sessionStart != 0 && lastObservation >= sessionStart) {
            observedMillis += Math.max(0, Math.min(60_000, e.at-lastObservation));
        }
        lastObservation = Math.max(lastObservation, e.at);
        if ("HEARTBEAT".equals(e.kind)) { return; }
        Event old = slots.get(e.slot);
        if (e.baseline) {
            finishTrial(e.slot, e.at, "BASELINE");
            slots.put(e.slot, e);
            if (!e.empty()) {
                position(e.item, e.name);
                beginTrial(e, false);
                if (e.quantity > 0) { notice("Existing fills at login were saved as a baseline, not counted again."); }
            }
            return;
        }
        if (e.empty()) {
            finishTrial(e.slot, e.at, "COLLECTED"); slots.put(e.slot, e); offerIds.remove(e.slot); return;
        }
        position(e.item, e.name);
        boolean continuation = e.sameOffer(old) && e.quantity >= old.quantity && e.gross >= old.gross
            && (old.active() || e.sameSnapshot(old));
        boolean fresh = old != null && old.empty();
        if (!continuation) {
            finishTrial(e.slot, e.at, "REPLACED / REPRICED");
            // Seeing an empty slot followed by an offer gives a bounded start, even if it fills immediately.
            beginTrial(e, fresh || e.quantity == 0);
            if (!fresh && e.quantity > 0) {
                notice("An offer changed without a zero-fill start; its existing fills need reconciliation.");
            }
        }
        long q = continuation ? e.quantity-old.quantity : fresh ? e.quantity : 0;
        long cash = continuation ? e.gross-old.gross : fresh ? e.gross : 0;
        if (q > 0 && cash >= 0) {
            Fill f = new Fill();
            f.id=e.id; f.offerId=offerIds.get(e.slot); f.item=e.item; f.name=e.name;
            f.buy=e.buy; f.quantity=q; f.gross=cash;
            f.from=old == null ? e.at : old.at; f.at=e.at;
            f.taxEstimate=e.buy ? 0 : Tax.estimate(e.item, cash, q, e.at);
            fills.add(f); applyFill(f);
            Trial t = openTrials.get(e.slot);
            if (t != null) { t.filled+=q; t.netCash+=cash-f.taxEstimate; }
        } else if (cash != 0 || q < 0) {
            notice("Inconsistent cumulative cash/quantity observed; no synthetic fill was created.");
        }
        slots.put(e.slot, e);
        if (!e.active()) { finishTrial(e.slot, e.at, e.state); }
    }

    private void beginTrial(Event e, boolean known) {
        String id=e.id; offerIds.put(e.slot, id);
        if (!e.active() && e.quantity == 0) { return; }
        Trial t=new Trial(); t.id=id; t.item=e.item; t.name=e.name; t.buy=e.buy;
        t.price=e.price; t.requested=e.total; t.started=e.at; t.startKnown=known;
        trials.add(t); openTrials.put(e.slot,t);
    }
    private void finishTrial(int slot, long at, String result) {
        Trial t=openTrials.remove(slot);
        if (t != null) { t.ended=at; t.result=result; }
    }
    private void closeTrials(long at, String result) {
        for (int slot : new ArrayList<>(openTrials.keySet())) { finishTrial(slot,at,result); }
    }
    private void notice(String message) { if (!notices.contains(message)) { notices.add(message); } }

    private void applyFill(Fill f) {
        Position p=position(f.item,f.name);
        if (f.buy) {
            p.lots.add(new Lot(f.quantity, (double)f.gross/f.quantity, true, f.at, "observed buy"));
            p.lastBuy=f.at; return;
        }
        p.lastSell=f.at;
        long left=f.quantity;
        double netUnit=(double)(f.gross-f.taxEstimate)/f.quantity;
        while (left>0 && !p.lots.isEmpty()) {
            Lot l=p.lots.peekFirst(); long q=Math.min(left,l.quantity);
            if (l.known) { p.matchedProfit+=q*(netUnit-l.unitCost); p.matchedSales+=q; }
            else { p.unknownCostSales+=q; }
            l.quantity-=q; left-=q;
            if (l.quantity == 0) { p.lots.removeFirst(); }
        }
        if (left>0) { p.unbackedSales+=left; p.unknownCostSales+=left; }
    }

    public long volume(int item, boolean buy, long since, long until) {
        return fills.stream().filter(f -> f.item==item && f.buy==buy && f.at>=since && f.at<=until)
            .mapToLong(f -> f.quantity).sum();
    }
    public long committed(int item) {
        return slots.values().stream().filter(e -> e.active() && e.buy && (item==0 || e.item==item))
            .mapToLong(e -> (e.total-e.quantity)*e.price).sum();
    }

    public static void validate(Event e) {
        if (e == null || e.version != 1 || e.id == null || e.id.isBlank() || e.at <= 0) {
            throw new IllegalArgumentException("Invalid journal identity/version/time");
        }
        if (!Set.of("OFFER","HEARTBEAT","GAP","OPENING","STOCK","NOTE").contains(e.kind == null ? "" : e.kind)) {
            throw new IllegalArgumentException("Unknown journal event kind");
        }
        if (e.quantity<0 || e.gross<0 || e.price<0 || e.total<0 || e.quantity>Integer.MAX_VALUE
            || e.total>Integer.MAX_VALUE || e.price>Integer.MAX_VALUE || e.gross>Integer.MAX_VALUE
            || (e.cost!=null && e.cost<0)) { throw new IllegalArgumentException("Invalid quantity or GP"); }
        if ("OFFER".equals(e.kind) && (e.slot<0 || e.slot>7 || e.quantity>e.total
            || e.session==null || !Set.of("EMPTY","BUYING","SELLING","BOUGHT","SOLD","CANCELLED_BUY","CANCELLED_SELL").contains(e.state == null ? "" : e.state)
            || (!e.empty() && e.item<=0))) { throw new IllegalArgumentException("Invalid GE snapshot"); }
        if ("OFFER".equals(e.kind) && !e.empty()
            && (e.price<1 || e.total<1 || e.gross<e.quantity
            || e.buy!=Set.of("BUYING","BOUGHT","CANCELLED_BUY").contains(e.state))) {
            throw new IllegalArgumentException("Invalid offer side/price/cash");
        }
        if (("OPENING".equals(e.kind) || "STOCK".equals(e.kind)) && (e.item<=0 || e.source==null)) {
            throw new IllegalArgumentException("Stock needs an item and provenance");
        }
    }
}
