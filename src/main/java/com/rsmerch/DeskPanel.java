package com.rsmerch;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import net.runelite.client.ui.PluginPanel;

/** Compact native trading desk, designed for RuneLite's 225px sidebar. */
public final class DeskPanel extends PluginPanel {
    private static final Color BG=new Color(23,27,31), CARD=new Color(32,38,43), LINE=new Color(49,58,63);
    private static final Color TEXT=new Color(234,238,237), MUTED=new Color(154,169,171), MINT=new Color(130,224,185), GOLD=new Color(239,193,117);
    private static final Font BODY=new Font("Segoe UI",Font.PLAIN,12), SMALL=BODY.deriveFont(11f), BOLD=BODY.deriveFont(Font.BOLD);
    private static final Font NUM=new Font("Consolas",Font.BOLD,18);
    private final BiConsumer<String,Event> save;
    private final LongConsumer scan;
    private final JLabel recording=label("Waiting for account",SMALL,MUTED);
    private final JPanel deck=new JPanel(new CardLayout());
    private final JPanel[] lists=new JPanel[4];
    private final JScrollPane[] scrolls=new JScrollPane[4];
    private final JButton[] tabs=new JButton[5];
    private final PerformancePanel performance;
    private final IntConsumer analyse;
    private final Map<Integer,String> analysing=new HashMap<>();
    private final JButton scanButton=button("Scan market",true);
    private final JButton budget=button("1m GP",false);
    private final JButton wikiButton=button("Refresh Wiki prices",false);
    private final JLabel wikiStatus=label("Public trade prices",SMALL,MUTED);
    private final JLabel scanStatus=label("Find your next trade",SMALL,MUTED);
    private final JProgressBar scanProgress=new JProgressBar();
    private final JTextField search=new JTextField();
    private final JCheckBox newOnly=new JCheckBox("New items only",true);
    private final JComboBox<String> sort=new JComboBox<>(new String[]{"Best fit","ROI","Demand"});
    private final JComboBox<String> tradeSource=new JComboBox<>(new String[]{"Live fills","RuneLite archive","GE history captures"});
    private final JComboBox<HistoryArchive.Capture> captureSource=new JComboBox<>();
    private final JTextField tradeSearch=new JTextField();
    private int tradePage;
    private String captureKey="",tradesKey;
    private boolean updatingCaptureChoices,tradeScrollReset;
    private DeskView view;
    private String accountToken;
    private long cash=1_000_000;
    private int selected;
    private boolean scanning;
    private boolean cashEdited;
    private String scanMessage;
    private String findsKey;

    public DeskPanel(BiConsumer<String,Event> save,Runnable export) { this(save,export,n -> {}); }
    public DeskPanel(BiConsumer<String,Event> save,Runnable export,LongConsumer scan) { this(save,export,scan,() -> {}); }
    public DeskPanel(BiConsumer<String,Event> save,Runnable export,LongConsumer scan,Runnable refreshWiki) { this(save,export,scan,refreshWiki,id -> {}); }
    public DeskPanel(BiConsumer<String,Event> save,Runnable export,LongConsumer scan,Runnable refreshWiki,IntConsumer analyse) {
        super(false); this.save=save; this.scan=scan; this.analyse=analyse;
        performance=new PerformancePanel(event -> { if (loggedIn()) { save.accept(accountToken,event); } },
            (recovered,name) -> { tradeSource.setSelectedIndex(recovered ? 1 : 0); tradeSearch.setText(name); selectTab(3); },refreshWiki);
        setLayout(new BorderLayout()); setBackground(BG); setBorder(new EmptyBorder(0,0,0,0));
        setPreferredSize(new Dimension(225,690));
        JPanel top=column(BG); top.setBorder(new EmptyBorder(12,10,0,10));
        JPanel brand=row(); brand.add(label("RSMERCH",BOLD.deriveFont(16f),TEXT),BorderLayout.WEST);
        top.add(brand); top.add(gap(5)); top.add(recording); top.add(gap(12));
        JPanel nav=new JPanel(new GridLayout(2,3,2,2)); nav.setOpaque(false); nav.setAlignmentX(0);
        String[] titles={"Stock","Offers","Finds","History","Results"};
        for (int i:new int[]{4,0,1,2,3}) {
            final int index=i; tabs[i]=button(titles[i],false); tabs[i].setBorder(new EmptyBorder(7,0,7,0));
            tabs[i].addActionListener(e -> selectTab(index)); nav.add(tabs[i]);
        }
        top.add(nav); top.add(gap(8)); add(top,BorderLayout.NORTH);
        deck.setOpaque(false);
        for (int i=0;i<4;i++) {
            lists[i]=column(BG); lists[i].setBorder(new EmptyBorder(2,10,10,10));
            scrolls[i]=new JScrollPane(lists[i]); scrolls[i].setBorder(null); scrolls[i].getViewport().setBackground(BG);
            scrolls[i].setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrolls[i].getVerticalScrollBar().setUnitIncrement(20);
            scrolls[i].getVerticalScrollBar().setPreferredSize(new Dimension(6,0));
            JPanel page=new JPanel(new BorderLayout()); page.setOpaque(false); page.add(scrolls[i],BorderLayout.CENTER);
            if (i==0) { page.add(actions(buttonAction("Add stock",() -> stock(false,null)),buttonAction("Collected",() -> stock(true,null))),BorderLayout.SOUTH); }
            if (i==1) {
                JPanel controls=column(BG); controls.setBorder(new EmptyBorder(2,10,7,10));
                wikiButton.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
                wikiButton.setToolTipText("Fetches public Wiki price data. Your account, offers and history are not uploaded.");
                wikiButton.addActionListener(e -> refreshWiki.run()); controls.add(wikiButton); controls.add(gap(5)); controls.add(wikiStatus);
                page.add(controls,BorderLayout.NORTH);
            }
            if (i==2) { page.add(findControls(),BorderLayout.NORTH); }
            if (i==3) { page.add(tradeControls(),BorderLayout.NORTH); page.add(actions(buttonAction("Add note",this::note),buttonAction("Export CSV",export)),BorderLayout.SOUTH); }
            deck.add(page,Integer.toString(i));
        }
        deck.add(performance,"4"); add(deck,BorderLayout.CENTER); selectTab(4);
        show(DeskView.build(new Book(),new Research(),System.currentTimeMillis(),cash,7,"Waiting for account",null),null);
        tradeSource.setSelectedIndex(1);
    }
    private JPanel tradeControls() {
        JPanel controls=column(BG); controls.setBorder(new EmptyBorder(2,10,7,10));
        tradeSource.setFont(BODY); tradeSource.setAlignmentX(0); tradeSource.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        tradeSource.addActionListener(e -> { tradePage=0; tradeScrollReset=true; captureSource.setVisible(tradeSource.getSelectedIndex()==2 && captureSource.getItemCount()>0); renderTrades(); });
        controls.add(tradeSource); controls.add(gap(5));
        captureSource.setFont(SMALL); captureSource.setAlignmentX(0); captureSource.setMaximumSize(new Dimension(Integer.MAX_VALUE,26)); captureSource.setVisible(false);
        captureSource.addActionListener(e -> { if (!updatingCaptureChoices) { tradePage=0; tradeScrollReset=true; renderTrades(); } }); controls.add(captureSource); controls.add(gap(5));
        tradeSearch.setAlignmentX(0); tradeSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE,29)); tradeSearch.setFont(BODY); tradeSearch.setBackground(CARD); tradeSearch.setForeground(TEXT); tradeSearch.setCaretColor(MINT);
        tradeSearch.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(5,6,5,6)));
        tradeSearch.putClientProperty("JTextField.placeholderText","Search history by item…"); tradeSearch.getAccessibleContext().setAccessibleName("Search trade history");
        tradeSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void update() { tradePage=0; tradeScrollReset=true; renderTrades(); }
            public void insertUpdate(DocumentEvent e) { update(); } public void removeUpdate(DocumentEvent e) { update(); } public void changedUpdate(DocumentEvent e) { update(); }
        }); controls.add(label("Search history",SMALL,MUTED)); controls.add(gap(3)); controls.add(tradeSearch); return controls;
    }
    void selectHistorySource(int index) { tradeSource.setSelectedIndex(index); }
    void selectPerformanceSource(int index) { performance.selectSource(index); }
    private JPanel findControls() {
        JPanel controls=column(BG); controls.setBorder(new EmptyBorder(2,10,7,10));
        JPanel buttons=new JPanel(new GridLayout(1,2,5,0)); buttons.setOpaque(false); buttons.setAlignmentX(0);
        budget.setToolTipText("Free GP available for new tests. Click to change.");
        budget.addActionListener(e -> {
            String value=JOptionPane.showInputDialog(this,"Free cash for new tests (GP)",Long.toString(cash));
            if (value==null) { return; }
            try { long n=Long.parseLong(value.replace(",","").trim()); if (n<1 || n>Integer.MAX_VALUE) { throw new NumberFormatException(); } cash=n; cashEdited=true; budget.setText(compact(cash)+" GP"); }
            catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this,"Enter a whole GP amount greater than zero."); }
        });
        scanButton.addActionListener(e -> scan.accept(cash)); buttons.add(budget); buttons.add(scanButton); controls.add(buttons);
        controls.add(gap(6)); controls.add(scanStatus);
        scanProgress.setAlignmentX(0); scanProgress.setIndeterminate(true); scanProgress.setPreferredSize(new Dimension(100,3)); scanProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE,3));
        scanProgress.setForeground(MINT); scanProgress.setBackground(LINE); scanProgress.setBorder(null); scanProgress.setVisible(false);
        controls.add(gap(4)); controls.add(scanProgress); controls.add(gap(7));
        search.setAlignmentX(0); search.setMaximumSize(new Dimension(Integer.MAX_VALUE,29)); search.setFont(BODY); search.setBackground(CARD); search.setForeground(TEXT); search.setCaretColor(MINT);
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(5,6,5,6)));
        search.setToolTipText("Filter opportunities by item name"); search.putClientProperty("JTextField.placeholderText","Search items…");
        search.getAccessibleContext().setAccessibleName("Search opportunities"); controls.add(label("Search items",SMALL,MUTED)); controls.add(gap(3)); controls.add(search);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { renderFinds(); } public void removeUpdate(DocumentEvent e) { renderFinds(); }
            public void changedUpdate(DocumentEvent e) { renderFinds(); }
        });
        controls.add(gap(5)); JPanel filters=row();
        newOnly.setOpaque(false); newOnly.setFont(SMALL); newOnly.setForeground(MUTED); newOnly.setBorder(null);
        newOnly.addActionListener(e -> renderFinds()); filters.add(newOnly,BorderLayout.WEST);
        sort.setFont(SMALL); sort.setPreferredSize(new Dimension(82,24)); sort.addActionListener(e -> renderFinds()); filters.add(sort,BorderLayout.EAST);
        controls.add(filters); return controls;
    }
    public void selectTab(int index) {
        selected=index; ((CardLayout)deck.getLayout()).show(deck,Integer.toString(index));
        for (int i=0;i<tabs.length;i++) { tabs[i].setBackground(i==index ? LINE : BG); tabs[i].setForeground(i==index ? MINT : MUTED); }
    }
    public void recordingStatus(String text,String detail,boolean healthy) {
        recording.setText("●  "+text); recording.setToolTipText(detail); recording.setForeground(healthy ? MINT : GOLD);
    }
    public void wikiState(boolean busy) { wikiButton.setEnabled(!busy); wikiButton.setText(busy ? "Fetching Wiki prices…" : "Refresh Wiki prices"); }
    public void scanState(boolean busy,String text) {
        scanning=busy; scanMessage=text; scanButton.setEnabled(!busy); budget.setEnabled(!busy);
        scanButton.setText(busy ? "Scanning…" : "Scan market"); scanProgress.setVisible(busy); updateScanStatus();
    }
    private void updateScanStatus() {
        String value=scanMessage;
        if (value==null && view!=null) {
            if (view.research.generatedAt.isEmpty()) { value="Ready to find your next trade"; }
            else {
                try { value=(view.research.fresh ? "Updated " : "Stale · ")+DeskView.age(Research.timestamp(view.research.generatedAt),view.now); }
                catch (RuntimeException ex) { value="Run a fresh market scan"; }
            }
        }
        scanStatus.setText(value); scanStatus.setToolTipText(value);
        scanStatus.setForeground(scanning ? MINT : view!=null && view.research.fresh && scanMessage==null ? MUTED : GOLD);
    }
    public void show(DeskView value,String token) {
        view=value; accountToken=token;
        if (!cashEdited) { cash=value.cash; budget.setText(compact(cash)+" GP"); }
        renderDesk(); renderOffers(); renderFinds(); renderTrades(); performance.show(value); updateScanStatus();
        wikiStatus.setText(value.wiki.fetchedAt>0 ? "Fetched "+DeskView.age(value.wiki.fetchedAt,value.now) : "Click to load public prices");
    }
    private void renderDesk() {
        JPanel p=lists[0]; int y=reset(0);
        JPanel summary=card(); summary.add(label("MATCHED PROFIT · EST.",SMALL,MUTED));
        summary.add(label((view.profit>0 ? "+" : "")+DeskView.number(view.profit)+" GP",NUM.deriveFont(22f),view.profit>=0 ? MINT : GOLD));
        summary.add(gap(6)); summary.add(pair("Stock cost",DeskView.number(view.stockCost)+" GP"));
        summary.add(pair("Open buys",DeskView.number(view.committed)+" GP"));
        summary.setToolTipText("Profit uses known purchase costs and estimated GE tax. Stock includes recorded lots only; reconcile existing holdings."); addCard(p,summary);
        if (view.error!=null) { addCard(p,message("Recording stopped",view.error,GOLD)); }
        JButton history=buttonAction("History · "+view.archive.saved.size()+" recovered",() -> { tradeSource.setSelectedIndex(1); selectTab(3); });
        history.setMaximumSize(new Dimension(Integer.MAX_VALUE,32)); p.add(history); p.add(gap(8));
        p.add(section("YOUR STOCK",view.stocks.size()+" items"));
        if (view.stocks.isEmpty()) { addCard(p,message("Start from here","New fills appear automatically. Add stock you already own to track its cost and profit.",MUTED)); }
        for (DeskView.Stock s:view.stocks) {
            JPanel c=card(); c.add(title(s.name)); c.add(gap(6));
            c.add(pair("Recorded quantity",DeskView.number(s.quantity)));
            c.add(pair("Avg. known cost",s.known>0 ? DeskView.number(s.cost/s.known)+" GP" : "Unknown"));
            c.add(pair("Break-even ask",s.breakEven>0 ? DeskView.number(s.breakEven)+" GP" : "Needs cost"));
            c.add(gap(6)); c.add(label("24h: +"+compact(s.bought)+" bought / "+compact(s.sold)+" sold",SMALL,MUTED));
            if (s.warning!=null) { c.add(wrap(s.warning,SMALL,GOLD)); }
            addGuidance(c,s.item);
            c.add(gap(7)); JButton edit=buttonAction("Edit stock",() -> stock(false,s)); c.add(edit); addCard(p,c);
        }
        p.add(wrap("Recorded fills only. Reconcile bank stock, transfers and offline activity.",SMALL,MUTED)); finish(0,y);
    }
    private void renderOffers() {
        JPanel p=lists[1]; int y=reset(1); p.add(section("GE OFFERS",view.offers.size()+" / 8 slots"));
        if (view.wiki.error!=null) { addCard(p,message("Wiki refresh failed",view.wiki.error,GOLD)); }
        if (view.offers.isEmpty()) { addCard(p,message("No offers observed","Log in to show your current GE slots. New fills are saved as they happen.",MUTED)); }
        for (DeskView.Offer o:view.offers) {
            JPanel c=card(); c.add(section((o.buy ? "BUY" : "SELL")+" · SLOT "+(o.slot+1),o.state.equals("BUYING") || o.state.equals("SELLING") ? "Open" : "Collect"));
            c.add(title(o.name)); c.add(gap(5)); c.add(label("Your "+(o.buy ? "bid " : "ask ")+DeskView.number(o.price)+" GP",NUM,o.buy ? GOLD : MINT));
            c.add(gap(6)); JProgressBar bar=new JProgressBar(0,1000);
            bar.setValue(o.total>0 ? (int)Math.min(1000,1000.0*o.filled/o.total) : 0); bar.setForeground(o.buy ? GOLD : MINT);
            bar.setBackground(LINE); bar.setBorder(null); bar.setPreferredSize(new Dimension(100,4)); bar.setMaximumSize(new Dimension(Integer.MAX_VALUE,4)); bar.setAlignmentX(0); c.add(bar);
            c.add(gap(5)); c.add(pair(DeskView.number(o.filled)+" / "+DeskView.number(o.total),"filled"));
            addWiki(c,o); addGuidance(c,o.item); addCard(p,c);
        }
        p.add(wrap("Buy limits and queue position are not visible. Offers are snapshots from your connected client.",SMALL,MUTED)); finish(1,y);
    }
    private void addWiki(JPanel card,DeskView.Offer offer) {
        WikiPrices.Quote quote=view.wiki.quotes.get(offer.item); card.add(gap(9));
        if (quote==null) { card.add(wrap("Wiki prices not loaded for this item. Use Refresh Wiki prices above.",SMALL,MUTED)); return; }
        card.add(section("WIKI · LAST TRADES",""));
        card.add(pair("Instant buy",quote.high>0 ? DeskView.number(quote.high)+" GP" : "No report"));
        card.add(label(DeskView.age(quote.highAt,view.now)+(quote.fresh(quote.highAt,view.now) ? "" : " · stale / unknown"),SMALL,quote.fresh(quote.highAt,view.now) ? MUTED : GOLD));
        card.add(gap(4)); card.add(pair("Instant sell",quote.low>0 ? DeskView.number(quote.low)+" GP" : "No report"));
        card.add(label(DeskView.age(quote.lowAt,view.now)+(quote.fresh(quote.lowAt,view.now) ? "" : " · stale / unknown"),SMALL,quote.fresh(quote.lowAt,view.now) ? MUTED : GOLD));
        card.add(gap(7));
        long reference=offer.buy ? quote.low : quote.high;
        if (reference>0) { long difference=offer.price-reference; card.add(wrap("Your "+(offer.buy ? "bid" : "ask")+" is "+(difference==0 ? "at" : DeskView.number(Math.abs(difference))+" GP "+(difference>0 ? "above" : "below"))+" the last "+(offer.buy ? "instant sell." : "instant buy."),SMALL,MUTED)); }
        if (quote.high>0 && quote.low>quote.high) { card.add(wrap("Latest trades cross: they occurred at different times.",SMALL,GOLD)); }
        if (view.wiki.hourAt>0) {
            card.add(gap(7)); card.add(label("1h window: "+DeskView.time(view.wiki.hourAt),SMALL,MUTED));
            card.add(pair("Buy / sell avg.",(quote.hourHigh>0 ? DeskView.number(quote.hourHigh) : "—")+" / "+(quote.hourLow>0 ? DeskView.number(quote.hourLow) : "—")));
            card.add(pair("Buy / sell units",DeskView.number(quote.hourHighVolume)+" / "+DeskView.number(quote.hourLowVolume)));
            if (view.now-view.wiki.hourAt>2*3_600_000) { card.add(label("Hourly window is stale",SMALL,GOLD)); }
        }
        card.add(gap(6)); card.add(wrap("Reported trades, not available offers. Your own fills can appear here.",SMALL,MUTED));
        card.add(gap(6)); card.add(buttonAction("Wiki chart",() -> openChart(offer.item)));
    }
    public void analysisState(int item,String status) {
        if (status==null) { analysing.remove(item); } else { analysing.put(item,status); }
        if (view!=null) { renderOffers(); renderDesk(); }
    }
    private void addGuidance(JPanel card,int item) {
        PriceHistory.Item evidence=view.priceHistory.items.get(item);
        PriceGuidance.Advice advice=PriceGuidance.assess(item,evidence,view.wiki.quotes.get(item),view.now);
        card.add(gap(9)); card.add(section("PRICE GUIDANCE","7 DAYS"));
        card.add(wrap(advice.status,BOLD,advice.actionable ? MINT : MUTED));
        if (advice.sellLow>0) {
            card.add(pair("Patient ask range",DeskView.number(advice.sellLow)+"–"+DeskView.number(advice.sellHigh)));
            card.add(pair("Net / unit after tax",DeskView.number(advice.sellLow-Tax.unit(item,advice.sellLow,view.now))+"–"+DeskView.number(advice.sellHigh-Tax.unit(item,advice.sellHigh,view.now))));
            if (advice.buyLow>0) { card.add(pair("Passive bid range",DeskView.number(advice.buyLow)+"–"+DeskView.number(advice.buyHigh))); }
            card.add(pair("Faster-exit reference",advice.fastExit>0 ? DeskView.number(advice.fastExit)+" GP" : "No fresh trade"));
            card.add(wrap(advice.highHours+" active buying hours across "+advice.days+" days · "+DeskView.number(advice.highUnits)+" reported units",SMALL,MUTED));
            Performance.Holding held=view.performance.holdings.stream().filter(h -> h.item==item).findFirst().orElse(null);
            if (held!=null && held.known>0) {
                double basis=held.cost/held.known;
                double low=advice.sellLow-Tax.unit(item,advice.sellLow,view.now)-basis;
                double high=advice.sellHigh-Tax.unit(item,advice.sellHigh,view.now)-basis;
                card.add(wrap("Estimated gain on known-cost units: "+DeskView.number(low)+" to "+DeskView.number(high)+" GP each.",SMALL,low<0 ? GOLD : MUTED));
                if (high<0) { card.add(wrap("This range is below your known break-even; waiting for recovery is a separate risk.",SMALL,GOLD)); }
            }
            List<Performance.Execution> own=new ArrayList<>();
            for (Performance.Execution e:view.performance.executions) { if (e.item==item && !e.buy && e.at>=view.now-7*Book.DAY) { own.add(e); } }
            long units=own.stream().mapToLong(e -> e.quantity).sum(),gross=own.stream().mapToLong(e -> e.gross).sum();
            if (units>0) { card.add(wrap("Your recorded sells: "+DeskView.number(units)+" units at "+DeskView.number((double)gross/units)+" GP average gross, across "+own.size()+" observed fills.",SMALL,MUTED)); }
            JPanel copies=new JPanel(new GridLayout(1,2,4,0)); copies.setOpaque(false); copies.setAlignmentX(0);
            copies.add(price("ASK LOW",advice.sellLow,MINT,advice.actionable)); copies.add(price("ASK HIGH",advice.sellHigh,MINT,advice.actionable)); card.add(copies);
        }
        if (advice.reason!=null) { card.add(wrap(advice.reason,SMALL,MUTED)); }
        for (String warning:advice.warnings) { card.add(gap(3)); card.add(wrap(warning,SMALL,GOLD)); }
        if (evidence!=null) { card.add(label("Analysed "+DeskView.age(evidence.fetchedAt,view.now),SMALL,MUTED)); }
        JButton fetch=buttonAction(analysing.containsKey(item) ? analysing.get(item) : evidence==null ? "Analyse price" : "Refresh analysis",() -> analyse.accept(item));
        fetch.setEnabled(!analysing.containsKey(item)); card.add(gap(5)); card.add(fetch);
    }
    private void renderFinds() {
        if (view==null) { return; }
        String query=search.getText().trim().toLowerCase(Locale.ROOT);
        StringBuilder signature=new StringBuilder(view.research.generatedAt).append(view.research.fresh).append(query).append(newOnly.isSelected()).append(sort.getSelectedIndex());
        for (Research.Candidate c:view.research.candidates) { signature.append('|').append(c.item).append(':').append(c.testQuantity).append(':').append(c.held).append(':').append(c.fresh); }
        if (signature.toString().equals(findsKey)) { return; }
        findsKey=signature.toString();
        JPanel p=lists[2]; int y=reset(2);
        List<Research.Candidate> candidates=new ArrayList<>();
        for (Research.Candidate c:view.research.candidates) {
            if ((!newOnly.isSelected() || !c.held) && c.name.toLowerCase(Locale.ROOT).contains(query)) { candidates.add(c); }
        }
        if (sort.getSelectedIndex()==1) { candidates.sort(Comparator.comparingDouble((Research.Candidate c) -> c.roi).reversed()); }
        if (sort.getSelectedIndex()==2) { candidates.sort(Comparator.comparingLong((Research.Candidate c) -> Math.min(c.highVolume,c.lowVolume)).reversed()); }
        p.add(section("OPPORTUNITIES",candidates.size()+" matches"));
        if (candidates.isEmpty()) { addCard(p,message("No matches yet",view.research.candidates.isEmpty() ? "Scan the market for fresh spreads backed by price history and volume." : "Try another search or turn off New items only.",MUTED)); }
        for (Research.Candidate c:candidates) {
            JPanel card=card(); card.add(title(c.name)); card.add(gap(4));
            card.add(label("+"+DeskView.number(c.net)+" GP / item  ·  "+String.format(Locale.UK,"%.1f%%",c.roi*100),BOLD,MINT));
            card.add(gap(8)); JPanel prices=new JPanel(new GridLayout(1,2,5,0)); prices.setOpaque(false); prices.setAlignmentX(0);
            prices.add(price("BUY",c.buy,GOLD,c.fresh)); prices.add(price("SELL",c.sell,MINT,c.fresh)); card.add(prices);
            if (c.chart.size()>1) { card.add(gap(5)); card.add(new Sparkline(c.chart)); }
            card.add(gap(6));
            card.add(pair("24h buy / sell vol.",compact(c.highVolume)+" / "+compact(c.lowVolume)));
            card.add(pair("Buy limit / 4h",DeskView.number(c.limit)));
            if (c.fresh && c.testQuantity>0) { card.add(pair("Small test",c.testQuantity+" × "+DeskView.number(c.buy))); }
            else { card.add(wrap(c.fresh ? "Test paused · check cash or stock cost" : "Stale prices · scan before testing",SMALL,GOLD)); }
            JPanel footer=row(); footer.setBorder(new EmptyBorder(7,0,0,0));
            JLabel own=label(c.held ? "Already trading" : "After estimated tax",SMALL,MUTED); footer.add(own,BorderLayout.WEST);
            JButton chart=buttonAction("Chart",() -> openChart(c.item)); chart.setBorder(new EmptyBorder(3,5,3,5)); footer.add(chart,BorderLayout.EAST); card.add(footer);
            addCard(p,card);
        }
        p.add(wrap("Tap a price to copy. Quotes are completed trades, not live offers. Test sizes are individual ceilings; fills aren't guaranteed.",SMALL,MUTED)); finish(2,y);
    }
    private void renderTrades() {
        if (view==null) { return; }
        String key=accountToken+":"+view.archive.captures.size()+":"+(view.archive.captures.isEmpty() ? "" : view.archive.captures.get(0).id);
        if (!key.equals(captureKey)) {
            updatingCaptureChoices=true; String selectedId=captureSource.getSelectedItem()==null ? "" : ((HistoryArchive.Capture)captureSource.getSelectedItem()).id;
            captureSource.removeAllItems(); for (HistoryArchive.Capture capture:view.archive.captures) { captureSource.addItem(capture); if (capture.id.equals(selectedId)) { captureSource.setSelectedItem(capture); } }
            captureSource.setVisible(tradeSource.getSelectedIndex()==2 && captureSource.getItemCount()>0); updatingCaptureChoices=false; captureKey=key;
        }
        int source=tradeSource.getSelectedIndex(); String query=tradeSearch.getText().trim().toLowerCase(Locale.ROOT);
        HistoryArchive.Capture choice=(HistoryArchive.Capture)captureSource.getSelectedItem();
        HistoryArchive.Capture capture=choice==null ? null : view.archive.captures.stream().filter(c -> c.id.equals(choice.id)).findFirst().orElse(null);
        String signature=accountToken+":"+source+":"+query+":"+tradePage+":"+view.fillCount+":"+view.archive.saved.size()+":"+view.archive.error+":"+view.notes.hashCode()+":"+(capture==null ? "" : capture.id);
        if (signature.equals(tradesKey)) { return; } tradesKey=signature;
        JPanel p=lists[3]; int y=reset(3); if (tradeScrollReset) { y=0; tradeScrollReset=false; }
        if (view.archive.error!=null) { addCard(p,message("History needs attention",view.archive.error,GOLD)); }
        if (source==0) {
            p.add(section("RECENT FILLS",view.fillCount+" recorded"));
            if (view.trades.isEmpty()) { addCard(p,message("Ready for the first fill","New fills appear here. Select RuneLite archive above for recovered history.",MUTED)); }
            for (DeskView.Trade t:view.trades) {
                if (!t.name.toLowerCase(Locale.ROOT).contains(query)) { continue; }
                JPanel c=card(); c.add(section(t.buy ? "BOUGHT" : "SOLD",DeskView.time(t.at))); c.add(title(t.name));
                c.add(gap(5)); c.add(label(DeskView.number(t.quantity)+" × "+String.format(Locale.UK,"%,.2f",(double)t.gross/t.quantity)+" GP",BOLD,t.buy ? GOLD : MINT));
                c.add(pair(t.buy ? "Paid" : "Net received ≈",DeskView.number(t.gross-t.tax)+" GP")); addCard(p,c);
            }
            if (!view.notes.isEmpty() && query.isEmpty()) { p.add(section("NOTEBOOK","")); for (String n:view.notes) { addCard(p,message("Note",n,MUTED)); } }
        } else {
            List<HistoryArchive.Entry> entries=new ArrayList<>();
            for (HistoryArchive.Entry e:source==1 ? view.archive.saved : capture==null ? Collections.<HistoryArchive.Entry>emptyList() : capture.entries) {
                if ((e.row.name==null ? "Item "+e.row.item : e.row.name).toLowerCase(Locale.ROOT).contains(query)) { entries.add(e); }
            }
            p.add(section(source==1 ? "SAVED TRADES" : "CAPTURED ROWS",entries.size()+" matches"));
            p.add(wrap(source==1 ? "Recovered RuneLite records. Rounded prices; kept outside live stock and profit." : "A snapshot of the GE history list. Trade dates are unknown; captures can overlap.",SMALL,MUTED)); p.add(gap(8));
            if (entries.isEmpty()) { addCard(p,message("No records to show",source==1 ? "Log into the matching account to load its saved archive, or try another search." : "Open the in-game GE History screen. Its rows save automatically after loading.",MUTED)); }
            int pages=Math.max(1,(entries.size()+29)/30); tradePage=Math.min(tradePage,pages-1);
            for (int i=tradePage*30;i<Math.min(entries.size(),(tradePage+1)*30);i++) {
                HistoryArchive.Entry e=entries.get(i); HistoryArchive.Row r=e.row; JPanel c=card();
                c.add(section(r.buy ? "BOUGHT" : "SOLD",r.recordedAt>0 ? DeskView.time(r.recordedAt) : "Date unknown"));
                c.add(title(r.name==null ? "Item "+r.item : r.name)); c.add(gap(5));
                c.add(label(DeskView.number(r.quantity)+" × "+DeskView.number(r.price)+" GP",BOLD,r.buy ? GOLD : MINT));
                if (r.gross==null) { c.add(label("Average price · rounded down",SMALL,MUTED)); }
                else { c.add(pair(r.buy ? "Paid" : "Net received",DeskView.number(r.gross-r.tax)+" GP")); if (r.tax>0) { c.add(pair("Tax shown",DeskView.number(r.tax)+" GP")); } }
                if (e.overlap!=null) { c.add(wrap(e.overlap,SMALL,GOLD)); }
                addCard(p,c);
            }
            if (pages>1) {
                JPanel pager=row(); JButton back=buttonAction("Previous",() -> { tradePage--; tradeScrollReset=true; renderTrades(); scrolls[3].getVerticalScrollBar().setValue(0); }); back.setEnabled(tradePage>0);
                JButton next=buttonAction("Next",() -> { tradePage++; tradeScrollReset=true; renderTrades(); scrolls[3].getVerticalScrollBar().setValue(0); }); next.setEnabled(tradePage<pages-1);
                pager.add(back,BorderLayout.WEST); pager.add(label((tradePage+1)+" / "+pages,SMALL,MUTED),BorderLayout.CENTER); pager.add(next,BorderLayout.EAST); p.add(pager);
            }
        }
        finish(3,y);
    }
    private JPanel price(String name,long value,Color color,boolean enabled) {
        JPanel p=column(CARD); p.add(label(name,SMALL,MUTED)); JButton b=button(DeskView.number(value),false);
        b.setFont(NUM); b.setForeground(color); b.setBorder(new EmptyBorder(5,2,5,2)); b.setEnabled(enabled);
        b.setToolTipText("Copy "+name.toLowerCase(Locale.ROOT)+" price"); b.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(Long.toString(value)),null);
            b.setText("Copied"); javax.swing.Timer timer=new javax.swing.Timer(900,ignored -> b.setText(DeskView.number(value))); timer.setRepeats(false); timer.start();
        }); p.add(b); return p;
    }
    private int reset(int i) { int y=scrolls[i].getVerticalScrollBar().getValue(); lists[i].removeAll(); return y; }
    private void finish(int i,int y) {
        lists[i].add(Box.createVerticalGlue()); lists[i].revalidate(); lists[i].repaint();
        SwingUtilities.invokeLater(() -> scrolls[i].getVerticalScrollBar().setValue(y));
    }
    private static JPanel column(Color color) { JPanel p=new Stack(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); p.setBackground(color); p.setAlignmentX(0); return p; }
    private static JComponent gap(int height) { JComponent gap=(JComponent)Box.createVerticalStrut(height); gap.setAlignmentX(0); return gap; }
    private static final class Stack extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction) { return Math.max(20,r.height-30); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private static JPanel row() { JPanel p=new JPanel(new BorderLayout(4,0)); p.setOpaque(false); p.setAlignmentX(0); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,34)); return p; }
    private static JPanel card() { JPanel p=column(CARD); p.setBorder(new EmptyBorder(10,9,10,9)); return p; }
    private static void addCard(JPanel parent,JPanel c) { c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c.getPreferredSize().height)); parent.add(c); parent.add(gap(8)); }
    private static JLabel label(String value,Font font,Color color) {
        JLabel l=new JLabel(value); l.putClientProperty("html.disable",Boolean.TRUE); l.setFont(font); l.setForeground(color); l.setAlignmentX(0); return l;
    }
    private static JLabel title(String text) { return wrap(text,BOLD,TEXT); }
    private static JLabel wrap(String text,Font font,Color color) {
        JLabel l=new JLabel("<html><div style='width:130px'>"+DeskView.esc(text)+"</div></html>"); l.setFont(font); l.setForeground(color); l.setAlignmentX(0); return l;
    }
    private static JPanel pair(String key,String value) { JPanel p=row(); p.add(label(key,SMALL,MUTED),BorderLayout.WEST); p.add(label(value,SMALL,TEXT),BorderLayout.EAST); return p; }
    private static JPanel section(String name,String detail) { JPanel p=row(); p.setBorder(new EmptyBorder(3,0,7,0)); p.add(label(name,SMALL.deriveFont(10f),MUTED),BorderLayout.WEST); p.add(label(detail,SMALL.deriveFont(10f),MUTED),BorderLayout.EAST); return p; }
    private static JPanel message(String title,String text,Color color) { JPanel p=card(); p.add(title(title)); p.add(gap(5)); p.add(wrap(text,SMALL,color)); return p; }
    private static JButton button(String text,boolean primary) {
        JButton b=new JButton(text); b.setFont(BOLD.deriveFont(11f)); b.setFocusPainted(false); b.setBorder(new EmptyBorder(7,7,7,7));
        b.setBackground(primary ? MINT : LINE); b.setForeground(primary ? BG : TEXT); b.setOpaque(true); b.setAlignmentX(0);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    private static JButton buttonAction(String text,Runnable action) { JButton b=button(text,false); b.addActionListener(e -> action.run()); return b; }
    private static JPanel actions(JButton a,JButton b) { JPanel p=new JPanel(new GridLayout(1,2,5,0)); p.setBackground(BG); p.setBorder(new EmptyBorder(8,10,10,10)); p.add(a); p.add(b); return p; }
    private static String compact(long n) { if (n>=1_000_000) { return String.format(Locale.UK,"%.1fm",n/1_000_000.0); } if (n>=10_000) { return String.format(Locale.UK,"%.0fk",n/1000.0); } return DeskView.number(n); }
    private void openChart(int item) { net.runelite.client.util.LinkBrowser.browse("https://prices.runescape.wiki/osrs/item/"+item); }
    private boolean loggedIn() { if (accountToken!=null) { return true; } JOptionPane.showMessageDialog(this,"Log in before adding account records."); return false; }
    private void stock(boolean gathered,DeskView.Stock existing) {
        if (!loggedIn()) { return; } String token=accountToken;
        List<Map.Entry<Integer,String>> entries=new ArrayList<>(view.catalog.entrySet()); entries.sort(Map.Entry.comparingByValue());
        JComboBox<String> item=new JComboBox<>(); for (Map.Entry<Integer,String> entry:entries) { item.addItem(entry.getValue()); }
        if (existing!=null) { item.setSelectedItem(existing.name); }
        JTextField quantity=new JTextField(existing==null || gathered ? "" : Long.toString(existing.quantity));
        JTextField unitCost=new JTextField(existing!=null && existing.quantity>0 && existing.known==existing.quantity ? DeskView.number(existing.cost/existing.quantity).replace(",","") : "");
        Object[] fields={"Item (your offers, favourites and scan results)",item,gathered ? "Additional units collected" : "Total holdings, including bank + GE",quantity,
            gathered ? "Collected stock has zero GP purchase cost." : "Average GP paid per unit (blank = unknown)",unitCost}; unitCost.setEnabled(!gathered);
        if (JOptionPane.showConfirmDialog(this,fields,gathered ? "Add collected stock" : "Set current stock",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) { return; }
        try {
            Map.Entry<Integer,String> choice=entries.get(item.getSelectedIndex()); Event e=event(gathered ? "STOCK" : "OPENING");
            e.item=choice.getKey(); e.name=choice.getValue(); e.quantity=Long.parseLong(quantity.getText().replace(",","").trim());
            e.cost=gathered ? 0L : unitCost.getText().isBlank() ? null : Math.multiplyExact(Long.parseLong(unitCost.getText().replace(",","").trim()),e.quantity);
            e.source=gathered ? "manual gathered stock" : "manual holdings reconciliation"; Book.validate(e); save.accept(token,e);
        } catch (RuntimeException ex) { JOptionPane.showMessageDialog(this,"Enter non-negative whole quantities and costs."); }
    }
    private void note() {
        if (!loggedIn()) { return; } String token=accountToken;
        String text=JOptionPane.showInputDialog(this,"Price, quantity, reason and when to review","Trading note",JOptionPane.PLAIN_MESSAGE);
        if (text==null || text.isBlank()) { return; } Event e=event("NOTE"); e.note=text.substring(0,Math.min(4000,text.length())); save.accept(token,e);
    }
    static Event event(String kind) { Event e=new Event(); e.kind=kind; e.id=UUID.randomUUID().toString(); e.at=System.currentTimeMillis(); return e; }
    private static final class Sparkline extends JComponent {
        private final List<double[]> values;
        Sparkline(List<double[]> values) { this.values=values; setPreferredSize(new Dimension(150,34)); setMaximumSize(new Dimension(Integer.MAX_VALUE,34)); setAlignmentX(0); setToolTipText("Historical hourly average prices · gold: sell-to-GE · mint: buy-from-GE"); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            double min=Double.POSITIVE_INFINITY,max=0; for (double[] v:values) { min=Math.min(min,Math.min(v[0],v[1])); max=Math.max(max,Math.max(v[0],v[1])); }
            double span=Math.max(1,max-min); g.setStroke(new BasicStroke(1.4f));
            for (int side=0;side<2;side++) { g.setColor(side==0 ? GOLD : MINT); for (int i=1;i<values.size();i++) {
                int x0=(i-1)*(getWidth()-2)/(values.size()-1),x1=i*(getWidth()-2)/(values.size()-1);
                int y0=getHeight()-3-(int)((values.get(i-1)[side]-min)/span*(getHeight()-6)),y1=getHeight()-3-(int)((values.get(i)[side]-min)/span*(getHeight()-6)); g.drawLine(x0,y0,x1,y1);
            } } g.dispose();
        }
    }
}
