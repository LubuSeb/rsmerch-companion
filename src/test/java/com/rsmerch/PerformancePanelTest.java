package com.rsmerch;

import java.awt.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerformancePanelTest {
    private DeskView view(boolean history) {
        Book book=new Book(); book.accept(BookTest.stock("opening",10,1000L));
        DeskView v=DeskView.build(book,new Research(),BookTest.NOW,1_000_000,7,"Demo",null);
        if (history) {
            HistoryArchive.Entry e=new HistoryArchive.Entry(); e.id="old-buy"; e.source=HistoryArchive.RUNELITE;
            e.row=new HistoryArchive.Row(); e.row.item=1763; e.row.name="Red dye"; e.row.buy=true;
            e.row.recordedAt=BookTest.NOW-Book.DAY; e.row.quantity=300; e.row.price=450;
            v.archive.saved.add(e); v.recoveredPerformance=Performance.recovered(v.archive);
        }
        WikiPrices.Quote q=new WikiPrices.Quote(); q.low=200; q.lowAt=BookTest.NOW; v.wiki.quotes.put(1763,q);
        return v;
    }
    private PerformancePanel panel() { return new PerformancePanel(e -> {},(recovered,name) -> {},() -> {}); }
    @Test public void opensAvailableHistoricalTradesByDefault() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PerformancePanel p=panel(); p.show(view(true),"one"); assertEquals(1,p.selectedSource());
        });
    }
    @Test public void picksUpArchiveLoadedAfterAccountConnects() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PerformancePanel p=panel(); p.show(view(false),"one"); assertEquals(0,p.selectedSource());
            p.show(view(true),"one"); assertEquals(1,p.selectedSource());
        });
    }
    @Test public void preservesExplicitSourceUntilAccountChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PerformancePanel p=panel(); p.show(view(true),"one"); p.selectSource(0);
            p.show(view(true),"one"); assertEquals(0,p.selectedSource());
            p.show(view(true),"two"); assertEquals(1,p.selectedSource());
            p.show(view(false),"three"); assertEquals(0,p.selectedSource());
        });
    }
    @Test public void valueUsesCurrentStockEvenWhenHistoricalLeaderboardIsSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PerformancePanel p=panel(); p.show(view(true),"one"); p.selectValue();
            assertTrue(hasLabel(p,"1,960 GP")); assertEquals(1,p.selectedSource());
        });
    }
    private boolean hasLabel(Container c,String text) {
        for (Component child:c.getComponents()) {
            if (child instanceof JLabel && ((JLabel)child).getText().equals(text)) { return true; }
            if (child instanceof Container && hasLabel((Container)child,text)) { return true; }
        }
        return false;
    }
}
