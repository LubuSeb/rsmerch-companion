package com.rsmerch;

import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryScreenTest {
    @Test public void exactTaxIsReadWithoutApplyingItTwice() {
        HistoryArchive.Row r=HistoryScreen.parse(1763,10,"Sold:","<col=ffb83f>Red dye</col><br>x\u00a010","<col=ffb83f>8,820\u00a0coins</col><br><col=9f9f9f>(9,000 -\u00a0180)</col><br>= 882 each");
        assertFalse(r.buy); assertEquals("Red dye",r.name); assertEquals(900,r.price);
        assertEquals(Long.valueOf(9000),r.gross); assertEquals(Long.valueOf(180),r.tax); assertEquals(0,r.recordedAt);
    }
    @Test public void buysAndTaxExemptSalesPreserveWholeTotals() {
        HistoryArchive.Row buy=HistoryScreen.parse(1763,1,"Bought:","Red dye","1 coin");
        assertTrue(buy.buy); assertEquals(1,buy.price); assertEquals(Long.valueOf(0),buy.tax);
        HistoryArchive.Row sell=HistoryScreen.parse(1,100,"Sold:","Example","2,001 coins<br>= 20 each");
        assertEquals(20,sell.price); assertEquals(Long.valueOf(2001),sell.gross);
    }
    @Test public void unknownLayoutsAndInconsistentTotalsCannotBecomeTrades() {
        String[] money={"8,820 coins (9,000 - 100)","Price unavailable","8.8K coins"};
        for (String text:money) {
            try { HistoryScreen.parse(1763,10,"Sold:","Red dye",text); fail(); } catch (RuntimeException expected) { }
        }
        try { HistoryScreen.parse(1763,10,"Bought:","Red dye","100 coins (102 - 2)"); fail(); } catch (RuntimeException expected) { }
    }
}
