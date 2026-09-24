package com.rsmerch;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class BookTest {
    static final long NOW=Instant.parse("2026-09-15T10:00:00Z").toEpochMilli();
    static Event offer(String id,long offset,String state,long q,long cash) {
        Event e=new Event(); e.id=id; e.kind="OFFER"; e.at=NOW+offset; e.session="a";
        e.slot=0; e.item=1763; e.name="Red dye"; e.buy=state.contains("BUY") || state.equals("BOUGHT");
        e.state=state; e.total=100; e.price=e.buy ? 450 : 900; e.quantity=q; e.gross=cash;
        if (state.equals("EMPTY")) { e.item=0; e.total=0; e.price=0; }
        return e;
    }
    static Event stock(String id,int q,Long totalCost) {
        Event e=new Event(); e.id=id; e.kind="OPENING"; e.item=1763; e.name="Red dye";
        e.at=NOW; e.quantity=q; e.cost=totalCost; e.source="test confirmed opening"; return e;
    }
    @Test public void partialsCompletionAndDuplicateSnapshotsCountOnce() {
        Book b=new Book(); b.accept(offer("0",0,"EMPTY",0,0));
        b.accept(offer("1",1,"BUYING",0,0)); b.accept(offer("2",2,"BUYING",10,4400));
        b.accept(offer("3",3,"BUYING",10,4400)); b.accept(offer("4",4,"BUYING",20,8750));
        b.accept(offer("5",5,"CANCELLED_BUY",20,8750));
        assertEquals(20,b.position(1763,null).quantity());
        assertEquals(8750,b.position(1763,null).knownCost(),0.001);
        assertEquals(2,b.fills.size()); assertEquals(20,b.trials.get(0).filled);
    }
    @Test public void instantFillAfterObservedEmptyCounts() {
        Book b=new Book(); b.accept(offer("0",0,"EMPTY",0,0));
        b.accept(offer("1",1,"BOUGHT",100,44000));
        assertEquals(100,b.fills.get(0).quantity);
        assertEquals("BOUGHT",b.trials.get(0).result);
    }
    @Test public void loginBaselineDoesNotInventPriorExecutions() {
        Book b=new Book(); Event e=offer("0",0,"BUYING",50,22500); e.baseline=true; b.accept(e);
        b.accept(offer("1",1,"BUYING",55,24750));
        assertEquals(5,b.position(1763,null).quantity()); assertEquals(1,b.fills.size());
        assertFalse(b.trials.get(0).startKnown);
    }
    @Test public void reconnectCannotAssumeSameOfferOrCountOfflineFills() {
        Book b=new Book(); b.accept(offer("0",0,"BUYING",0,0)); b.accept(offer("1",1,"BUYING",10,4500));
        Event e=offer("2",1000,"BUYING",50,22500); e.session="b"; e.baseline=true; b.accept(e);
        Event f=offer("3",2000,"BUYING",55,24750); f.session="b"; b.accept(f);
        assertEquals(15,b.position(1763,null).quantity()); assertEquals(2,b.fills.size());
        assertEquals("OBSERVATION GAP",b.trials.get(0).result);
    }
    @Test public void reusedSlotAndPriceEditDoNotReuseOldQuantity() {
        Book b=new Book(); b.accept(offer("0",0,"EMPTY",0,0)); b.accept(offer("1",1,"BOUGHT",100,45000));
        b.accept(offer("2",2,"BUYING",0,0)); b.accept(offer("3",3,"BUYING",10,4500));
        Event edit=offer("4",4,"BUYING",20,9000); edit.price=460; b.accept(edit);
        assertEquals(110,b.position(1763,null).quantity());
        assertTrue(b.notices.stream().anyMatch(n -> n.contains("zero-fill")));
    }
    @Test public void fifoUsesCashDeltasAndUnknownStockDoesNotBecomeFree() {
        Book b=new Book(); b.accept(stock("opening",10,4500L));
        b.accept(offer("0",1,"BUYING",0,0)); b.accept(offer("1",2,"BOUGHT",10,5000));
        b.accept(offer("2",3,"EMPTY",0,0)); b.accept(offer("3",4,"SELLING",0,0));
        b.accept(offer("4",5,"SOLD",15,13500));
        Book.Position p=b.position(1763,null);
        assertEquals(5,p.quantity()); assertEquals(2500,p.knownCost(),0.001);
        assertEquals(6230,p.matchedProfit,0.001); // 15*882 - (10*450 + 5*500)
        b.accept(stock("unknown",3,null)); b.accept(offer("5",6,"EMPTY",0,0));
        b.accept(offer("6",7,"SOLD",5,4500));
        assertEquals(6230,p.matchedProfit,0.001);
        assertEquals(5,p.unknownCostSales); assertEquals(2,p.unbackedSales);
    }
    @Test public void unfilledCancelledTrialsAreRetained() {
        Book b=new Book(); b.accept(offer("0",0,"BUYING",0,0));
        b.accept(offer("1",60_000,"CANCELLED_BUY",0,0));
        assertEquals(1,b.trials.size()); assertEquals(0,b.trials.get(0).filled);
        assertEquals("CANCELLED_BUY",b.trials.get(0).result);
    }
    @Test public void manualOpeningIsAbsoluteAndGatheringIsIncremental() {
        Book b=new Book(); b.accept(stock("0",50,22500L)); b.accept(stock("1",20,null));
        Event gathered=stock("2",28,0L); gathered.kind="STOCK"; b.accept(gathered);
        assertEquals(48,b.position(1763,null).quantity()); assertEquals(28,b.position(1763,null).knownQuantity());
        assertEquals(0,b.position(1763,null).knownCost(),0.001);
    }
    @Test public void taxRoundingCapsExemptionsAndHistoricalRate() {
        assertEquals(0,Tax.unit(1763,49,NOW)); assertEquals(1,Tax.unit(1763,50,NOW));
        assertEquals(18,Tax.unit(1763,900,NOW)); assertEquals(5_000_000,Tax.unit(1763,1_000_000_000,NOW));
        assertEquals(0,Tax.unit(net.runelite.api.gameval.ItemID.HAMMER,900,NOW));
        assertEquals(9,Tax.unit(1763,900,Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()));
        assertEquals(459,Tax.breakEven(1763,450,NOW));
        assertEquals(27,Tax.estimate(1763,1399,2,NOW));
    }
    @Test public void futureFillsDoNotEnterHistoricalVolumeWindow() {
        Book b=new Book(); b.accept(offer("0",0,"EMPTY",0,0)); b.accept(offer("1",1000,"BOUGHT",10,4500));
        assertEquals(0,b.volume(1763,true,NOW-Book.DAY,NOW));
    }
    @Test public void invalidSideRejectedBeforeMutatingBook() {
        Book b=new Book(); Event e=offer("0",0,"SELLING",0,0); e.buy=true;
        assertThrows(IllegalArgumentException.class,() -> b.accept(e)); assertTrue(b.positions.isEmpty());
    }
}
