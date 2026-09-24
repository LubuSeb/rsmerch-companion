package com.rsmerch;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders the real Swing panel with synthetic records; never connects to RuneScape. */
public final class PanelPreview {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                long now=BookTest.NOW;
                Book b=new Book();
                b.accept(BookTest.offer("baseline",-2*Book.DAY,"EMPTY",0,0));
                Event stock=BookTest.stock("stock",300,135000L); stock.at=now-2*Book.DAY; b.accept(stock);
                Event buy=BookTest.offer("buy",-Book.DAY/2,"BUYING",0,0); b.accept(buy);
                b.accept(BookTest.offer("bfill",-Book.DAY/3,"CANCELLED_BUY",50,22500));
                b.accept(BookTest.offer("empty",-Book.DAY/4,"EMPTY",0,0));
                b.accept(BookTest.offer("sell",-Book.DAY/5,"SELLING",0,0));
                b.accept(BookTest.offer("sfill",-3_600_000,"SELLING",20,18000));
                Event note=DeskPanel.event("NOTE"); note.at=now; note.note="DEMO: Compare a small 900 GP ask with a second price over two sessions. Keep zero-fill results."; b.accept(note);
                Research r=new Research(); r.status="DEMO ONLY · saved scanner research"; r.generatedAt="Synthetic prices, not current recommendations";
                Research.Candidate c=new Research.Candidate(); c.name="Example opportunity"; c.buy=100; c.sell=120; c.testQuantity=10;
                c.item=2; c.net=18; c.roi=.18; c.limit=11000; c.highVolume=4200; c.lowVolume=3200; c.fresh=true; r.candidates.add(c);
                for (int i=0;i<40;i++) { c.chart.add(new double[]{100+Math.sin(i*.7)*3,120+Math.sin(i*.4)*5}); }
                Research.Candidate second=new Research.Candidate(); second.item=3; second.name="Another sample item"; second.buy=2050; second.sell=2190;
                second.net=97; second.roi=.047; second.limit=125; second.testQuantity=4; second.highVolume=5230; second.lowVolume=4100; second.fresh=true; r.candidates.add(second);
                r.history.add("DEMO archive · 12 observations"); r.history.add("No user export has been loaded.");
                DeskView view=DeskView.build(b,r,now,1_000_000,7,"DEMO DATA · not your holdings",null);
                view.wiki.fetchedAt=now; view.wiki.hourAt=now-3_600_000;
                WikiPrices.Quote quote=new WikiPrices.Quote(); quote.high=890; quote.low=450; quote.highAt=now-60000; quote.lowAt=now-120000;
                quote.hourHigh=875; quote.hourLow=448; quote.hourHighVolume=230; quote.hourLowVolume=150; view.wiki.quotes.put(1763,quote);
                HistoryArchive.Row recovered=HistoryScreen.parse(1763,150,"Bought:","Red dye<br>x 150","67,500 coins<br>= 450 each");
                recovered.recordedAt=now-Book.DAY; recovered.gross=null; recovered.tax=null;
                HistoryArchive.Entry saved=new HistoryArchive.Entry(); saved.row=recovered; saved.source="runelite"; saved.id="demo"; saved.capturedAt=now;
                view.archive.saved.add(saved);
                HistoryArchive.Row screenRow=HistoryScreen.parse(1763,20,"Sold:","Red dye<br>x 20","17,640 coins<br>(18,000 - 360)<br>= 882 each");
                HistoryArchive.Entry screenEntry=new HistoryArchive.Entry(); screenEntry.row=screenRow; screenEntry.source="ge-screen"; screenEntry.id="demo-screen"; screenEntry.capturedAt=now;
                HistoryArchive.Capture capture=new HistoryArchive.Capture(); capture.at=now; capture.id="demo"; capture.entries.add(screenEntry); view.archive.captures.add(capture);
                BufferedImage composite=new BufferedImage(1165,800,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=composite.createGraphics(); g.setColor(new Color(12,15,18)); g.fillRect(0,0,1165,800);
                for (int tab=0;tab<5;tab++) {
                    DeskPanel panel=new DeskPanel((token,event) -> {},() -> {}); panel.show(view,"demo");
                    panel.recordingStatus("Recording · demo fills","Synthetic preview",true);
                    panel.setSize(225,800); panel.selectTab(Math.min(tab,3)); if (tab>=3) { panel.selectHistorySource(tab-2); }
                    layout(panel);
                    Graphics2D cell=(Graphics2D)g.create(tab*235,0,225,800); panel.printAll(cell); cell.dispose();
                }
                g.dispose(); File file=new File(args[0]); file.getParentFile().mkdirs(); ImageIO.write(composite,"png",file);
                System.out.println("Synthetic Swing preview: "+file.getAbsolutePath());
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static void layout(Container c) { c.doLayout(); for (Component child:c.getComponents()) { if (child instanceof Container) { layout((Container)child); } } }
}
