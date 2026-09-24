package com.rsmerch;

import org.junit.Test;
import static org.junit.Assert.*;

public class PerformanceTest {
    private static final long NOW=BookTest.NOW;
    private Book sale(Long cost,long units,long gross) {
        Book b=new Book(); b.accept(BookTest.stock("opening",10,cost));
        b.accept(BookTest.offer("empty",-100,"EMPTY",0,0));
        b.accept(BookTest.offer("sold",0,"SOLD",units,gross)); return b;
    }
    @Test public void partialCostMatchingDoesNotTurnUnknownUnitsIntoProfit() {
        Book b=sale(1000L,15,3000); Performance.Summary summary=Performance.summarise(Performance.live(b),0,NOW);
        assertEquals(960,summary.profit,.001); assertEquals(10,summary.matched); assertEquals(5,summary.unknown);
        assertEquals(1000,summary.cost,.001); assertEquals(2940,summary.received);
        assertEquals(b.positions.get(1763).matchedProfit,summary.profit,.001);
    }
    @Test public void dateFilterKeepsEarlierPurchaseBasisAndRecordsLosses() {
        Book b=new Book(); b.accept(BookTest.offer("empty",-2*Book.DAY,"EMPTY",0,0));
        b.accept(BookTest.offer("buy",-Book.DAY,"BOUGHT",10,1000));
        b.accept(BookTest.offer("empty2",-100,"EMPTY",0,0)); b.accept(BookTest.offer("sell",0,"SOLD",10,900));
        Performance.Summary summary=Performance.summarise(Performance.live(b),NOW-1000,NOW);
        assertEquals(0,summary.buys); assertEquals(10,summary.sells); assertEquals(-110,summary.profit,.001);
        assertEquals(-110,summary.curve.get(summary.curve.size()-1).value,.001);
    }
    @Test public void unknownCostsAreExcludedButGatheredZeroCostIsKnown() {
        assertEquals(0,Performance.summarise(Performance.live(sale(null,10,2000)),0,NOW).matched);
        Performance.Summary gathered=Performance.summarise(Performance.live(sale(0L,10,2000)),0,NOW);
        assertEquals(10,gathered.matched); assertEquals(1960,gathered.profit,.001);
    }
    private HistoryArchive.Entry row(String id,boolean buy,long at,long quantity,long price) {
        HistoryArchive.Entry e=new HistoryArchive.Entry(); e.id=id; e.source=HistoryArchive.RUNELITE;
        e.row=new HistoryArchive.Row(); e.row.item=1763; e.row.name="Red dye"; e.row.buy=buy; e.row.recordedAt=at;
        e.row.quantity=quantity; e.row.price=price; return e;
    }
    @Test public void recoveredUsesOnlyDeduplicatedArchiveAndLeavesLiveBookAlone() {
        HistoryArchive.View archive=new HistoryArchive.View(); archive.saved.add(row("buy",true,NOW-1000,10,100));
        archive.saved.add(row("sell",false,NOW,15,200)); archive.screen.add(row("screen",false,NOW,15,200));
        Performance.Dataset recovered=Performance.recovered(archive); Performance.Summary summary=Performance.summarise(recovered,0,NOW);
        assertTrue(recovered.recovered); assertEquals(2,recovered.executions.size()); assertEquals(960,summary.profit,.001);
        assertEquals(5,summary.unknown); assertTrue(recovered.holdings.isEmpty());
        assertTrue(Performance.live(new Book()).executions.isEmpty());
    }
    @Test public void identicalArchiveTimesDoNotAssumeTheBuyHappenedBeforeTheSale() {
        HistoryArchive.View archive=new HistoryArchive.View(); archive.saved.add(row("buy",true,NOW,10,100)); archive.saved.add(row("sell",false,NOW,10,200));
        Performance.Summary summary=Performance.summarise(Performance.recovered(archive),0,NOW);
        assertEquals(0,summary.matched); assertEquals(10,summary.unknown);
    }
    @Test public void staleQuotesAreExcludedAndValueSnapshotsDoNotChangeProfit() {
        Book b=new Book(); b.accept(BookTest.stock("stock",10,1000L));
        WikiPrices prices=new WikiPrices(); WikiPrices.Quote quote=new WikiPrices.Quote(); quote.low=200; quote.lowAt=NOW-31*60_000; prices.quotes.put(1763,quote);
        Performance.Value value=Performance.value(Performance.live(b),prices,NOW);
        assertEquals(10,value.unpricedUnits); assertEquals(0,value.stock,.001);
        quote.lowAt=NOW; value=Performance.value(Performance.live(b),prices,NOW);
        assertEquals(1960,value.stock,.001); assertEquals(960,value.unrealised,.001);
        Event balance=DeskPanel.event("NOTE"); balance.at=NOW; balance.gross=500; balance.cost=2460L; balance.source=Book.VALUE_SNAPSHOT_SOURCE; b.accept(balance);
        assertEquals(1,b.balances.size()); assertEquals(10,b.position(1763,null).quantity());
        assertEquals(0,Performance.summarise(Performance.live(b),0,NOW).profit,.001);
    }
    @Test public void uncollectedCoinsIncludeBidRemainderAndSaleProceeds() {
        Book b=new Book(); b.accept(BookTest.offer("empty",-100,"EMPTY",0,0));
        Event bid=BookTest.offer("buy",0,"BUYING",10,1000); bid.price=150; bid.total=20; b.accept(bid);
        assertEquals(2000,Performance.live(b).geCoins,.001);
    }
}
