package com.rsmerch;

import java.util.*;

/** Detached trading results. Recovered history is never merged into the live book. */
final class Performance {
    static final class Execution {
        int item; String name,id; boolean buy;
        long at,quantity,gross,tax,matched,unknown;
        double cost,profit;
    }
    static final class Holding {
        int item; String name; long quantity,known,reconciledAt; double cost;
    }
    static final class Point {
        final long at; final double value;
        Point(long at,double value) { this.at=at; this.value=value; }
    }
    static final class Dataset {
        final List<Execution> executions=new ArrayList<>();
        final List<Holding> holdings=new ArrayList<>();
        final List<Point> balances=new ArrayList<>();
        boolean recovered; long firstAt; double geCoins;
    }
    static final class Row {
        int item; String name; long bought,sold,buyCash,sellGross,sellNet,tax,matched,unknown;
        double profit,cost;
        final List<Execution> executions=new ArrayList<>();
    }
    static final class Summary {
        final List<Row> rows=new ArrayList<>();
        final List<Point> curve=new ArrayList<>();
        long buys,sells,spent,received,tax,matched,unknown;
        double profit,cost;
    }
    static final class Value {
        double stock,knownCost,unrealised,geCoins;
        long pricedUnits,unpricedUnits,unknownCostUnits,unreconciledItems;
        boolean complete() { return unpricedUnits==0; }
    }
    static Dataset live(Book book) {
        Dataset data=new Dataset(); data.firstAt=book.firstObservation;
        for (Book.Fill f:book.fills) {
            Execution e=new Execution(); e.item=f.item; e.name=f.name; e.id=f.id; e.buy=f.buy;
            e.at=f.at; e.quantity=f.quantity; e.gross=f.gross; e.tax=f.taxEstimate;
            e.matched=f.matchedQuantity; e.unknown=f.unknownQuantity; e.cost=f.matchedCost; e.profit=f.profit;
            data.executions.add(e);
        }
        for (Book.Position p:book.positions.values()) {
            if (p.quantity()==0) { continue; }
            Holding h=new Holding(); h.item=p.item; h.name=p.name; h.quantity=p.quantity();
            h.known=p.knownQuantity(); h.cost=p.knownCost(); h.reconciledAt=p.reconciledAt; data.holdings.add(h);
        }
        for (Event e:book.balances) { data.balances.add(new Point(e.at,e.cost)); }
        for (Event e:book.slots.values()) {
            if (e.empty()) { continue; }
            // Coins still in an uncollected offer, including reserved bids and buy refunds.
            data.geCoins+=e.buy ? Math.max(0,(double)e.total*e.price-e.gross)
                : e.gross-(e.quantity>0 ? Tax.estimate(e.item,e.gross,e.quantity,e.at) : 0);
        }
        return data;
    }
    static Dataset recovered(HistoryArchive.View archive) {
        Dataset data=new Dataset(); data.recovered=true;
        List<HistoryArchive.Entry> entries=new ArrayList<>(archive.saved);
        // Identical observation times have no proven execution ordering. Do not match their buys to sells.
        entries.sort(Comparator.comparingLong((HistoryArchive.Entry e) -> e.row.recordedAt).thenComparing(e -> e.row.buy));
        Map<Integer,Deque<double[]>> lots=new HashMap<>();
        for (HistoryArchive.Entry entry:entries) {
            HistoryArchive.Row r=entry.row;
            if (!HistoryArchive.RUNELITE.equals(entry.source) || r.recordedAt<=0) { continue; }
            Execution e=new Execution(); e.item=r.item; e.name=r.name; e.id=entry.id; e.buy=r.buy;
            e.at=r.recordedAt; e.quantity=r.quantity;
            e.gross=r.gross!=null ? r.gross : Math.multiplyExact(r.quantity,r.price);
            e.tax=e.buy ? 0 : r.tax!=null ? r.tax : Tax.estimate(r.item,e.gross,r.quantity,e.at);
            Deque<double[]> queue=lots.computeIfAbsent(e.item,id -> new ArrayDeque<>());
            if (e.buy) { queue.add(new double[]{e.quantity,(double)e.gross/e.quantity}); }
            else {
                long remaining=e.quantity; double net=(double)(e.gross-e.tax)/e.quantity;
                while (remaining>0 && !queue.isEmpty()) {
                    double[] lot=queue.peek(); long units=Math.min(remaining,(long)lot[0]);
                    e.matched+=units; e.cost+=units*lot[1]; e.profit+=units*(net-lot[1]);
                    lot[0]-=units; remaining-=units; if (lot[0]==0) { queue.remove(); }
                }
                e.unknown=remaining;
            }
            data.executions.add(e); if (data.firstAt==0) { data.firstAt=e.at; }
        }
        return data;
    }
    static Summary summarise(Dataset data,long since,long until) {
        Summary result=new Summary(); Map<Integer,Row> rows=new HashMap<>();
        TreeMap<Long,Double> daily=new TreeMap<>();
        // Cost matching is done before filtering: a sale can use a buy from an earlier period.
        for (Execution e:data.executions) {
            if (e.at<since || e.at>until) { continue; }
            Row row=rows.computeIfAbsent(e.item,id -> { Row r=new Row(); r.item=id; r.name=e.name; return r; });
            row.executions.add(e);
            if (e.buy) { row.bought+=e.quantity; row.buyCash+=e.gross; }
            else {
                row.sold+=e.quantity; row.sellGross+=e.gross; row.sellNet+=e.gross-e.tax; row.tax+=e.tax;
                row.matched+=e.matched; row.unknown+=e.unknown; row.cost+=e.cost; row.profit+=e.profit;
                daily.merge(e.at/Book.DAY*Book.DAY,e.profit,Double::sum);
            }
        }
        for (Row r:rows.values()) {
            result.rows.add(r); result.buys+=r.bought; result.sells+=r.sold; result.spent+=r.buyCash;
            result.received+=r.sellNet; result.tax+=r.tax; result.matched+=r.matched; result.unknown+=r.unknown;
            result.cost+=r.cost; result.profit+=r.profit;
        }
        result.rows.sort(Comparator.comparing((Row r) -> r.matched==0).thenComparing(Comparator.comparingDouble((Row r) -> r.profit).reversed()).thenComparing(r -> r.name));
        double total=0;
        for (Map.Entry<Long,Double> e:daily.entrySet()) {
            if (result.curve.isEmpty()) { result.curve.add(new Point(e.getKey()-1,0)); }
            total+=e.getValue(); result.curve.add(new Point(e.getKey(),total));
        }
        return result;
    }
    static Value value(Dataset data,WikiPrices prices,long now) {
        Value value=new Value(); value.geCoins=data.geCoins;
        for (Holding h:data.holdings) {
            value.knownCost+=h.cost; value.unknownCostUnits+=h.quantity-h.known;
            if (h.reconciledAt==0) { value.unreconciledItems++; }
            WikiPrices.Quote q=prices.quotes.get(h.item);
            if (q==null || q.low<=0 || !q.fresh(q.lowAt,now)) { value.unpricedUnits+=h.quantity; continue; }
            double net=q.low-Tax.unit(h.item,q.low,now);
            value.pricedUnits+=h.quantity; value.stock+=h.quantity*net; value.unrealised+=h.known*net-h.cost;
        }
        return value;
    }
}
