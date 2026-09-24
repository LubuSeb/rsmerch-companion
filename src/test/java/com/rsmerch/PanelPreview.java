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
                Research r=new Research(); r.status="DEMO ONLY · saved scanner research"; r.generatedAt=java.time.Instant.ofEpochMilli(now).toString(); r.fresh=true;
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
                HistoryArchive.Row historicSale=HistoryScreen.parse(1763,100,"Sold:","Red dye<br>x 100","88,200 coins<br>(90,000 - 1,800)<br>= 882 each");
                historicSale.recordedAt=now-3_600_000;
                HistoryArchive.Entry historicEntry=new HistoryArchive.Entry(); historicEntry.row=historicSale; historicEntry.source="runelite"; historicEntry.id="demo-sale"; historicEntry.capturedAt=now;
                view.archive.saved.add(historicEntry);
                sample(view,9736,"Goat horn dust",130,175,2700,2000,now);
                sample(view,10148,"Black salamander",305,650,500,300,now);
                sample(view,4782,"Mithril brutal",190,350,800,500,now);
                view.recoveredPerformance=Performance.recovered(view.archive);
                PriceHistory.Item evidence=new PriceHistory.Item(); evidence.fetchedAt=now;
                for (int i=1;i<=120;i++) { PriceHistory.Hour h=new PriceHistory.Hour(); h.at=now-i*3_600_000; h.high=800+i%4*30; h.low=440+i%5; h.highVolume=20; h.lowVolume=30; evidence.hours.add(h); }
                view.priceHistory=view.priceHistory.with(1763,evidence);
                HistoryArchive.Row screenRow=HistoryScreen.parse(1763,20,"Sold:","Red dye<br>x 20","17,640 coins<br>(18,000 - 360)<br>= 882 each");
                HistoryArchive.Entry screenEntry=new HistoryArchive.Entry(); screenEntry.row=screenRow; screenEntry.source="ge-screen"; screenEntry.id="demo-screen"; screenEntry.capturedAt=now;
                HistoryArchive.Capture capture=new HistoryArchive.Capture(); capture.at=now; capture.id="demo"; capture.entries.add(screenEntry); view.archive.captures.add(capture);
                BufferedImage composite=new BufferedImage(1400,1050,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=composite.createGraphics(); g.setColor(new Color(12,15,18)); g.fillRect(0,0,1400,1050);
                for (int tab=0;tab<6;tab++) {
                    DeskPanel panel=new DeskPanel((token,event) -> {},() -> {}); panel.show(view,"demo");
                    panel.recordingStatus("Recording","Synthetic preview",true);
                    panel.setSize(225,1050); panel.selectTab(new int[]{4,4,1,0,2,3}[tab]); if (tab==1) { panel.selectPerformanceValue(); } if (tab==5) { panel.selectHistorySource(1); }
                    layout(panel);
                    Graphics2D cell=(Graphics2D)g.create(tab*235,0,225,1050); panel.printAll(cell); cell.dispose();
                }
                g.dispose(); File file=new File(args[0]); file.getParentFile().mkdirs(); ImageIO.write(composite,"png",file);
                System.out.println("Synthetic Swing preview: "+file.getAbsolutePath());
                // Deliberately extreme synthetic amounts exercise sidebar wrapping.
                view.offers.get(0).name="Very long example item name for narrow sidebar";
                view.offers.get(0).price=2_147_483_647; view.offers.get(0).total=2_147_483_647; view.offers.get(0).filled=1_234_567_890;
                quote.high=2_147_483_647; quote.low=1_234_567_890;
                c.buy=1_000_000_000; c.sell=2_000_000_000; c.net=960_000_000; c.name="Another deliberately long example item name";
                view.trades.get(0).quantity=2_147_483_647; view.trades.get(0).gross=2_000_000_000_000L;
                BufferedImage stress=new BufferedImage(705,800,BufferedImage.TYPE_INT_RGB); Graphics2D sg=stress.createGraphics();
                sg.setColor(new Color(12,15,18)); sg.fillRect(0,0,705,800);
                for (int i=0;i<3;i++) {
                    DeskPanel panel=new DeskPanel((token,event) -> {},() -> {}); panel.show(view,"demo");
                    panel.setSize(225,800); panel.selectTab(new int[]{1,2,3}[i]); if (i==2) { panel.selectHistorySource(0); }
                    layout(panel); Graphics2D cell=(Graphics2D)sg.create(i*235,0,225,800); panel.printAll(cell); cell.dispose();
                }
                sg.dispose(); ImageIO.write(stress,"png",new File(file.getParentFile(),"preview-stress.png"));
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private static void sample(DeskView v,int id,String name,long buy,long sell,long bought,long sold,long now) {
        for (int i=0;i<2;i++) {
            HistoryArchive.Entry entry=new HistoryArchive.Entry(); entry.id="demo-"+id+"-"+i; entry.source=HistoryArchive.RUNELITE;
            entry.row=new HistoryArchive.Row(); entry.row.item=id; entry.row.name=name; entry.row.buy=i==0;
            entry.row.recordedAt=now-(i==0 ? 4*Book.DAY : 2*Book.DAY); entry.row.quantity=i==0 ? bought : sold; entry.row.price=i==0 ? buy : sell;
            v.archive.saved.add(entry);
        }
        DeskView.Stock stock=new DeskView.Stock(); stock.item=id; stock.name=name; stock.quantity=bought-sold; stock.known=stock.quantity; stock.cost=stock.quantity*buy;
        stock.breakEven=Tax.breakEven(id,buy,now); v.stocks.add(stock);
        DeskView.Offer offer=new DeskView.Offer(); offer.item=id; offer.name=name; offer.price=sell; offer.filled=sold; offer.total=bought; offer.slot=v.offers.size(); offer.state="SELLING"; v.offers.add(offer);
        WikiPrices.Quote q=new WikiPrices.Quote(); q.high=sell-5; q.low=buy+2; q.highAt=now-60_000; q.lowAt=now-90_000; v.wiki.quotes.put(id,q);
    }
    private static void layout(Container c) { c.doLayout(); for (Component child:c.getComponents()) { if (child instanceof Container) { layout((Container)child); } } }
}
