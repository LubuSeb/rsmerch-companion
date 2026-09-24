package com.rsmerch;

import com.google.inject.Provides;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;

@PluginDescriptor(name="RSMerch Companion", description="Private trade journal, patient price experiments and local scanner research", tags={"grand exchange","flipping","journal"})
public final class RsMerchPlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientToolbar toolbar;
    @Inject private ItemManager items;
    @Inject private RsMerchConfig config;
    @Inject private ConfigManager configManager;
    @Inject private okhttp3.OkHttpClient http;
    @Inject private com.google.gson.Gson gson;
    private ScanRunner scanner;
    private WikiClient wikiClient;
    private ExecutorService marketWorker;
    private volatile WikiPrices wiki=new WikiPrices();
    private final java.util.concurrent.atomic.AtomicBoolean refreshingWiki=new java.util.concurrent.atomic.AtomicBoolean();
    private ScheduledExecutorService worker;
    private Journal journal;
    private HistoryArchive archive;
    private String archiveError;
    private volatile long nextHistoryImport;
    private volatile String lastImportedHistory,lastSavedScreen;
    private String previousScreen;
    private String journalKey;
    private DeskPanel panel;
    private NavigationButton navigation;
    private volatile String error;
    private volatile String activeKey;
    private String session;
    private volatile String displayName;
    private int stableTicks;
    private long lastHeartbeat;
    private final Map<Integer,Event> previous=new HashMap<>();

    @Provides RsMerchConfig provideConfig(ConfigManager manager) { return manager.getConfig(RsMerchConfig.class); }

    @Override protected void startUp() throws Exception {
        error=null; activeKey=null; stableTicks=0; session=null; previous.clear();
        archive=null; archiveError=null; lastImportedHistory=null; previousScreen=null; lastSavedScreen=null; nextHistoryImport=0;
        worker=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"rsmerch-journal"); t.setDaemon(true); return t; });
        wikiClient=new WikiClient(http); scanner=new ScanRunner(wikiClient);
        marketWorker=Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"rsmerch-wiki-prices"); t.setDaemon(true); return t; });
        marketWorker.execute(() -> { wiki=WikiPrices.load(wikiPath(),gson); });
        Runnable create=() -> {
            panel=new DeskPanel(this::manual,this::export,this::scan,this::refreshWiki);
            BufferedImage icon=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=icon.createGraphics(); g.setColor(new Color(121,213,177));
            g.fillRect(1,10,3,5); g.fillRect(6,6,3,9); g.fillRect(11,1,3,14); g.dispose();
            navigation=NavigationButton.builder().tooltip("RSMerch Companion").priority(7).icon(icon).panel(panel).build();
            toolbar.addNavigation(navigation);
        };
        if (SwingUtilities.isEventDispatchThread()) { create.run(); } else { SwingUtilities.invokeAndWait(create); }
        worker.scheduleWithFixedDelay(this::render,0,10,TimeUnit.SECONDS);
    }

    @Override protected void shutDown() {
        if (scanner!=null) { scanner.close(); }
        if (marketWorker!=null) { marketWorker.shutdownNow(); }
        if (navigation!=null) { toolbar.removeNavigation(navigation); }
        activeKey=null;
        if (worker!=null) {
            Future<?> close=worker.submit(() -> {
                if (journal!=null) {
                    try { journal.append(DeskPanel.event("GAP")); journal.close(); }
                    catch (Exception ignored) { /* Earlier records remain on disk; next load detects a partial tail. */ }
                    journal=null;
                }
            });
            worker.shutdown();
            try { close.get(3,TimeUnit.SECONDS); }
            catch (Exception ex) { error="Journal shutdown did not complete: "+ex.getMessage(); }
        }
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        // LOADING also occurs during ordinary region changes and teleports in the same session.
        if (event.getGameState()!=GameState.LOGGED_IN && event.getGameState()!=GameState.LOADING) { disconnect(); }
    }
    private void disconnect() {
        if (activeKey!=null && worker!=null && !worker.isShutdown()) {
            Event gap=DeskPanel.event("GAP");
            worker.execute(() -> append(gap));
        }
        activeKey=null; session=null; previous.clear(); stableTicks=0;
        previousScreen=null; lastSavedScreen=null; lastImportedHistory=null; nextHistoryImport=0;
    }

    @Subscribe public void onGameTick(GameTick tick) {
        if (client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null) { return; }
        String name=client.getLocalPlayer().getName();
        long hash=client.getAccountHash();
        if (name==null || name.isBlank() || hash==0 || hash==-1) { return; }
        if (++stableTicks<2) { return; }
        String key=accountKey(hash);
        if (!key.equals(activeKey)) {
            if (activeKey!=null) { disconnect(); }
            activeKey=key; session=UUID.randomUUID().toString(); displayName=name;
            previous.clear();
            worker.execute(() -> open(key));
            GrandExchangeOffer[] offers=client.getGrandExchangeOffers();
            if (offers!=null) { for (int i=0;i<Math.min(8,offers.length);i++) { capture(i,offers[i],true); } }
        }
        if (RuneScapeProfileType.getCurrent(client)==RuneScapeProfileType.STANDARD) { captureHistory(key,hash); }
        if (System.currentTimeMillis()-lastHeartbeat>=30_000) {
            lastHeartbeat=System.currentTimeMillis();
            Event e=DeskPanel.event("HEARTBEAT"); e.session=session;
            worker.execute(() -> append(e));
        }
    }

    @Subscribe public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged change) {
        if (activeKey!=null && (client.getGameState()==GameState.LOGGED_IN || client.getGameState()==GameState.LOADING)) {
            capture(change.getSlot(),change.getOffer(),!previous.containsKey(change.getSlot()));
        }
    }
    private void capture(int slot,GrandExchangeOffer offer,boolean baseline) {
        if (offer==null || slot<0 || slot>7) { return; }
        Event e=DeskPanel.event("OFFER"); e.session=session; e.slot=slot; e.baseline=baseline;
        e.item=offer.getItemId(); e.state=offer.getState().name();
        e.buy=e.state.equals("BUYING") || e.state.equals("BOUGHT") || e.state.equals("CANCELLED_BUY");
        if (e.empty()) { e.item=0; e.state="EMPTY"; }
        else {
            e.name=items.getItemComposition(e.item).getName();
            e.price=offer.getPrice(); e.total=offer.getTotalQuantity(); e.quantity=offer.getQuantitySold(); e.gross=offer.getSpent();
        }
        Event old=previous.get(slot);
        if (!baseline && old!=null && ((e.empty() && old.empty()) || e.sameSnapshot(old))) { return; }
        previous.put(slot,e);
        worker.execute(() -> append(e));
    }

    private void open(String key) {
        try {
            if (journal!=null) { journal.close(); journal=null; }
            journalKey=key;
            journal=new Journal(RuneLite.RUNELITE_DIR.toPath().resolve("rsmerch").resolve(key).resolve("events-v1.jsonl"),gson);
            error=null;
        } catch (Exception ex) { error=ex.getMessage(); }
        archive=null; archiveError=null;
        try { archive=new HistoryArchive(RuneLite.RUNELITE_DIR.toPath().resolve("rsmerch").resolve(key),key,gson); }
        catch (Exception ex) { archiveError=ex.getMessage(); }
        render();
    }
    // Runs on the client thread; copied records are persisted on the journal worker.
    private void captureHistory(String key,long hash) {
        long now=System.currentTimeMillis();
        if (now>=nextHistoryImport) {
            nextHistoryImport=now+30_000;
            String profile=configManager.getRSProfileKey();
            if (profile!=null && Long.toString(hash).equals(configManager.getConfiguration("rsprofile",profile,"accountHash"))
                && "STANDARD".equals(configManager.getConfiguration("rsprofile",profile,"type"))) {
                String raw=configManager.getConfiguration("grandexchange",profile,"tradeHistory");
                if (raw!=null && !raw.equals(lastImportedHistory)) {
                    try {
                        java.util.List<HistoryArchive.Row> rows=HistoryArchive.parseRuneLite(raw,id -> items.getItemComposition(id).getName(),now);
                        lastImportedHistory=raw;
                        saveHistory(key,HistoryArchive.RUNELITE,rows,raw,now);
                    } catch (Exception ex) { historyFailure(key,"Saved history: "+ex.getMessage()); }
                }
            }
        }
        Widget list=client.getWidget(InterfaceID.GeHistory.LIST);
        if (list==null || list.isHidden()) { previousScreen=null; return; }
        try {
            java.util.List<HistoryArchive.Row> rows=HistoryScreen.read(list);
            // Require two equal ticks to avoid importing an interface during construction.
            String fingerprint=HistoryArchive.digest(HistoryArchive.SCREEN,rows);
            if (fingerprint.equals(previousScreen) && !fingerprint.equals(lastSavedScreen)) {
                lastSavedScreen=fingerprint; saveHistory(key,HistoryArchive.SCREEN,rows,null,now);
            }
            previousScreen=fingerprint;
        } catch (Exception ex) { previousScreen=null; historyFailure(key,"GE history: "+ex.getMessage()); }
    }
    private void saveHistory(String key,String source,java.util.List<HistoryArchive.Row> rows,String raw,long now) {
        worker.execute(() -> {
            if (!key.equals(activeKey) || !key.equals(journalKey) || archive==null || journal==null || error!=null) { return; }
            try { boolean changed=archive.save(source,rows,raw,now); archiveError=null; if (changed) { render(); } }
            catch (Exception ex) { archiveError=ex.getMessage(); lastImportedHistory=null; lastSavedScreen=null; render(); }
        });
    }
    private void historyFailure(String key,String failure) {
        worker.execute(() -> { if (key.equals(activeKey) && !failure.equals(archiveError)) { archiveError=failure; render(); } });
    }
    private void append(Event e) {
        if (journal==null || error!=null) { return; }
        try { journal.append(e); }
        catch (Exception ex) { error=ex.getMessage(); }
    }
    private void manual(String token,Event e) {
        worker.execute(() -> {
            if (token==null || !token.equals(activeKey) || !token.equals(journalKey) || error!=null) {
                message("Account changed or recording is unavailable. No manual record was added."); return;
            }
            append(e); render();
            if (error!=null) { message("Could not save: "+error); }
        });
    }
    private void export() {
        worker.execute(() -> {
            if (journal==null) { message("Log in first to open this account's journal."); return; }
            try {
                Path file=journal.path.getParent().resolve("fills-"+System.currentTimeMillis()+".csv");
                journal.exportFills(file);
                if (archive!=null) { archive.export(journal.path.getParent().resolve("history-"+System.currentTimeMillis()+".csv"),journal.book); }
                message("Exported live fills and available history CSVs to:\n"+file.getParent());
            } catch (Exception ex) { message("Export failed: "+ex.getMessage()); }
        });
    }
    private void message(String text) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel,text)); }
    private void scan(long cash) {
        if (!config.wikiEnabled()) { message("Enable Wiki market requests in RSMerch settings to scan public prices."); return; }
        configManager.setConfiguration("rsmerch","planningCash",cash);
        panel.scanState(true,"Starting market scan…");
        boolean started=scanner.start(config.scannerFile(),cash,config.membersItems(),
            text -> SwingUtilities.invokeLater(() -> panel.scanState(true,text)),
            failure -> {
                if (worker!=null && !worker.isShutdown()) {
                    worker.execute(() -> {
                        render();
                        SwingUtilities.invokeLater(() -> panel.scanState(false,failure));
                    });
                }
            });
        if (!started) { panel.scanState(true,"A scan is already running…"); }
    }
    private Path wikiPath() { return RuneLite.RUNELITE_DIR.toPath().resolve("rsmerch").resolve("wiki-prices-v1.json"); }
    private void refreshWiki() {
        if (!config.wikiEnabled()) { message("Enable Wiki market requests in RSMerch settings to fetch public prices."); return; }
        if (!refreshingWiki.compareAndSet(false,true)) { return; }
        panel.wikiState(true);
        marketWorker.execute(() -> {
            try { WikiPrices next=WikiPrices.fetch(wikiClient); next.save(wikiPath(),gson); wiki=next; }
            catch (Exception ex) {
                WikiPrices failed=new WikiPrices(); failed.quotes.putAll(wiki.quotes); failed.fetchedAt=wiki.fetchedAt;
                failed.fiveAt=wiki.fiveAt; failed.hourAt=wiki.hourAt; failed.error=ex.getMessage(); wiki=failed;
            } finally {
                refreshingWiki.set(false);
                if (worker!=null && !worker.isShutdown()) { worker.execute(this::render); SwingUtilities.invokeLater(() -> panel.wikiState(false)); }
            }
        });
    }
    private void render() {
        try {
            String token=activeKey;
            boolean switching=token!=null && !token.equals(journalKey);
            Book b=journal==null || switching ? new Book() : journal.book;
            long now=System.currentTimeMillis();
            Research research=Research.read(config.scannerFile(),config.historyFile(),b,now,config.planningCash());
            String title=switching ? "Opening account journal…" : token==null ? "Offline · showing saved observations" : displayName;
            DeskView view=DeskView.build(b,research,now,config.planningCash(),config.maxStockDays(),title,error);
            view.wiki=wiki;
            if (!switching && archive!=null) { view.archive=archive.view(b); }
            view.archive.error=archiveError;
            boolean healthy=token!=null && !switching && journal!=null && error==null;
            String status=error!=null ? "Recording stopped · check Desk" : healthy ? "Recording · "+b.fills.size()+" fills" : token==null ? "Offline · saved trades retained" : "Opening account journal…";
            String detail=journal==null ? "Log in to begin a local account journal." : "Last disk save: "+DeskView.time(journal.lastSavedAt())+" · "+journal.recordCount()+" saved records · "+journal.path;
            SwingUtilities.invokeLater(() -> { panel.show(view,token); panel.recordingStatus(status,detail,healthy); });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                panel.show(DeskView.build(new Book(),new Research(),System.currentTimeMillis(),0,7,"Panel unavailable",ex.getMessage()),null);
                panel.recordingStatus("Panel unavailable · check Desk",ex.getMessage(),false);
            });
        }
    }
    static String accountKey(long hash) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(("rsmerch:"+Long.toUnsignedString(hash)).getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder(); for (int i=0;i<12;i++) { s.append(String.format(Locale.ROOT,"%02x",bytes[i])); }
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
