package com.rsmerch;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;

/** Results navigation stays fixed while the ranked cards scroll. */
final class PerformancePanel extends JPanel {
    private static final Color BG=new Color(23,27,31),CARD=new Color(32,38,43),TEXT=new Color(234,238,237),MUTED=new Color(154,169,171),MINT=new Color(130,224,185),WARN=new Color(239,193,117);
    private static final Font SMALL=new Font("Segoe UI",Font.PLAIN,11),BOLD=SMALL.deriveFont(Font.BOLD),NUM=new Font("Consolas",Font.BOLD,21);
    private final JComboBox<String> source=new JComboBox<>(new String[]{"Recorded fills","Recovered history"});
    private final JComboBox<String> period=new JComboBox<>(new String[]{"All available","Last 24h","Last 7 days","Last 30 days"});
    private final JComboBox<String> order=new JComboBox<>(new String[]{"Most profit","Biggest losses","Cash released","Most units sold"});
    private final JComboBox<String> chart=new JComboBox<>(new String[]{"Profit progress","Saved trading value"});
    private final JTextField search=new JTextField();
    private final JPanel body=column(BG);
    private final JScrollPane scroll=new JScrollPane(body);
    private final Consumer<Event> save;
    private final BiConsumer<Boolean,String> history;
    private final Runnable refresh;
    private DeskView view;
    private int page;
    private String account;
    private boolean sourceChosen,settingSource;
    private final JPanel filters=column(BG);
    private final JButton filterButton;


    PerformancePanel(Consumer<Event> save,BiConsumer<Boolean,String> history,Runnable refresh) {
        super(new BorderLayout()); this.save=save; this.history=history; this.refresh=refresh; setBackground(BG);
        JPanel controls=column(BG); controls.setBorder(new EmptyBorder(2,10,8,10));
        for (JComboBox<String> combo:Arrays.asList(source,period,order,chart)) {
            combo.setFont(SMALL); combo.setAlignmentX(0); combo.setMaximumSize(new Dimension(Integer.MAX_VALUE,27));
            combo.setBackground(CARD); combo.setForeground(TEXT);
            combo.addActionListener(e -> { if (combo==source && !settingSource) { sourceChosen=true; } page=0; render(); });
        }
        source.getAccessibleContext().setAccessibleName("Performance data source"); period.getAccessibleContext().setAccessibleName("Performance period");
        order.getAccessibleContext().setAccessibleName("Leaderboard order"); chart.getAccessibleContext().setAccessibleName("Progress chart type");
        chart.setModel(new DefaultComboBoxModel<>(new String[]{"Profit", "Trading value"}));
        JPanel modes=new JPanel(new GridLayout(1,2,5,0)); modes.setOpaque(false); modes.setAlignmentX(0);
        modes.add(chart); modes.add(period); controls.add(modes);
        filterButton=button("Filters",() -> { filters.setVisible(!filters.isVisible()); filterButtonText(); revalidate(); });
        controls.add(Box.createVerticalStrut(5)); controls.add(filterButton);
        filters.add(label("Source",SMALL,MUTED)); filters.add(source); filters.add(Box.createVerticalStrut(5));
        filters.add(label("Sort",SMALL,MUTED)); filters.add(order); filters.add(Box.createVerticalStrut(5));
        search.setFont(SMALL); search.setMaximumSize(new Dimension(Integer.MAX_VALUE,27)); search.setAlignmentX(0);
        search.setBackground(CARD); search.setForeground(TEXT); search.setCaretColor(MINT);
        search.getAccessibleContext().setAccessibleName("Filter leaderboard by item");
        filters.add(label("Find item",SMALL,MUTED)); filters.add(search); filters.setVisible(false); controls.add(filters);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); } public void removeUpdate(DocumentEvent e) { changed(); } public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { page=0; render(); }
        });
        add(controls,BorderLayout.NORTH); body.setBorder(new EmptyBorder(0,10,10,10)); scroll.setBorder(null);
        scroll.getViewport().setBackground(BG); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20); scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6,0)); add(scroll,BorderLayout.CENTER);
    }
    private void filterButtonText() { filterButton.setText(filters.isVisible() ? "Hide filters" : "Filters"); }
    void show(DeskView view,String account) {
        if (!Objects.equals(this.account,account)) { this.account=account; sourceChosen=false; page=0; }
        this.view=view;
        if (!sourceChosen) {
            int preferred=view.recoveredPerformance.executions.isEmpty() ? 0 : 1;
            if (source.getSelectedIndex()!=preferred) { settingSource=true; source.setSelectedIndex(preferred); settingSource=false; }
        }
        render();
    }
    void selectSource(int index) { sourceChosen=true; source.setSelectedIndex(index); }
    int selectedSource() { return source.getSelectedIndex(); }
    void selectValue() { chart.setSelectedIndex(1); }
    private long since() {
        return period.getSelectedIndex()==1 ? view.now-Book.DAY : period.getSelectedIndex()==2 ? view.now-7*Book.DAY : period.getSelectedIndex()==3 ? view.now-30*Book.DAY : 0;
    }
    private void render() {
        if (view==null) { return; }
        int y=scroll.getVerticalScrollBar().getValue(); body.removeAll(); boolean recovered=source.getSelectedIndex()==1;
        filterButton.setVisible(chart.getSelectedIndex()==0);
        if (chart.getSelectedIndex()==1) { filters.setVisible(false); filterButtonText(); addValue(view.performance); finish(y); return; }
        Performance.Dataset data=recovered ? view.recoveredPerformance : view.performance;
        Performance.Summary summary=Performance.summarise(data,since(),view.now);
        JPanel totals=card(); totals.add(label(recovered ? "HISTORICAL PROFIT · EST." : "RECORDED PROFIT · EST.",BOLD,MUTED));
        totals.add(summary.matched>0 ? CompactUi.money(summary.profit,NUM,summary.profit<0 ? WARN : MINT,true) : label("No costed sales yet",BOLD,MUTED));
        if (summary.unknown>0) { totals.add(wrap(n(summary.unknown)+" sold without cost · excluded",WARN)); }
        if (summary.curve.size()>1) { totals.add(new Curve(summary.curve)); }
        totals.add(button("Breakdown",() -> breakdown(summary,recovered))); add(totals);
        if (recovered && view.archive.error!=null) { JPanel error=card(); error.add(wrap("History error: "+view.archive.error,WARN)); add(error); }
        List<Performance.Row> rows=new ArrayList<>(summary.rows);
        String query=search.getText().trim().toLowerCase(Locale.ROOT); rows.removeIf(r -> !r.name.toLowerCase(Locale.ROOT).contains(query));
        if (order.getSelectedIndex()==1) { rows.sort(Comparator.comparing((Performance.Row r) -> r.matched==0).thenComparingDouble(r -> r.profit)); }
        if (order.getSelectedIndex()==2) { rows.sort(Comparator.comparingLong((Performance.Row r) -> r.sellNet).reversed()); }
        if (order.getSelectedIndex()==3) { rows.sort(Comparator.comparingLong((Performance.Row r) -> r.sold).reversed()); }
        body.add(label("ITEMS · "+rows.size(),BOLD,MUTED)); body.add(Box.createVerticalStrut(6));
        int pages=Math.max(1,(rows.size()+19)/20); page=Math.min(page,pages-1);
        for (int i=page*20;i<Math.min(rows.size(),page*20+20);i++) {
            Performance.Row row=rows.get(i); JPanel item=card();
            item.add(wrap((i+1)+". "+row.name,TEXT));
            item.add(row.matched>0 ? CompactUi.money(row.profit,NUM.deriveFont(18f),row.profit<0 ? WARN : MINT,true) : label(row.sold>0 ? "Cost missing" : "No sales yet",BOLD,MUTED));
            JPanel meta=new JPanel(new BorderLayout(5,0)); meta.setOpaque(false); meta.setAlignmentX(0);
            meta.add(label(n(row.sold)+" sold",SMALL,MUTED),BorderLayout.CENTER);
            JButton more=button("Details",() -> details(row,recovered)); meta.add(more,BorderLayout.EAST);
            meta.setMaximumSize(new Dimension(Integer.MAX_VALUE,meta.getPreferredSize().height)); item.add(meta);
            if (row.unknown>0 && row.matched>0) { item.add(wrap("Partial · "+n(row.unknown)+" sold without cost",WARN)); }
            add(item);
        }
        if (rows.isEmpty()) {
            JPanel empty=card(); empty.add(wrap("No trades for these filters.",MUTED));
            empty.add(button(recovered ? "Show recorded fills" : "Show recovered history",() -> source.setSelectedIndex(recovered ? 0 : 1))); add(empty);
        }
        if (pages>1) {
            JPanel pager=new JPanel(new GridLayout(1,2,5,0)); pager.setOpaque(false);
            JButton back=button("Previous",() -> { page--; render(); }); back.setEnabled(page>0);
            JButton next=button("Next",() -> { page++; render(); }); next.setEnabled(page<pages-1);
            pager.add(back); pager.add(next); body.add(pager);
        }
        finish(y);
    }
    private void finish(int y) { body.revalidate(); body.repaint(); SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(y)); }
    private void breakdown(Performance.Summary s,boolean recovered) {
        JPanel c=card(); c.add(pair("Bought / sold",n(s.buys)+" / "+n(s.sells)));
        c.add(pair("Buy spend",n(s.spent)+" GP")); c.add(pair("Sale proceeds",n(s.received)+" GP"));
        c.add(pair("Tax estimate",n(s.tax)+" GP")); c.add(pair("Costed sales",n(s.matched)+" units"));
        c.add(wrap(recovered ? "Separate FIFO estimate from rounded archive records, ordered by recorded time. Missing trades and opening stock can change the result." : "FIFO profit on recorded sales with known costs. Offline fills and unrecorded stock use can leave gaps.",MUTED));
        if (s.unknown>0) { c.add(wrap(n(s.unknown)+" sold units have no matched purchase cost and are excluded from profit.",WARN)); }
        JOptionPane.showMessageDialog(this,c,"Profit breakdown",JOptionPane.PLAIN_MESSAGE);
    }
    private void addValue(Performance.Dataset data) {
        Performance.Value value=Performance.value(data,view.wiki,view.now); JPanel c=card();
        c.add(label("RECORDED STOCK · EST.",BOLD,MUTED)); c.add(CompactUi.money(value.stock,NUM,TEXT,false));
        if (!value.complete()) { c.add(label("Partial value · prices missing",BOLD,WARN)); }
        c.add(pair("Unrealised profit",signed(value.unrealised)+" GP"));
        c.add(pair("Coins in GE",n(value.geCoins)+" GP"));
        if (value.unknownCostUnits>0) { c.add(wrap(n(value.unknownCostUnits)+" units without cost",WARN)); }
        c.add(button("Details",() -> {
            JPanel d=card(); d.add(pair("Known stock cost",n(value.knownCost)+" GP"));
            d.add(wrap("Recorded holdings at fresh Wiki instant-sell prices, after estimated tax. Demand is unknown. Unpriced units: "+n(value.unpricedUnits)+". Unknown costs are excluded from unrealised profit.",MUTED));
            JOptionPane.showMessageDialog(this,d,"Stock valuation",JOptionPane.PLAIN_MESSAGE);
        })); add(c);
        JPanel snapshots=card(); snapshots.add(label("SAVED TOTAL VALUE",BOLD,MUTED));
        List<Performance.Point> points=new ArrayList<>();
        for (Performance.Point point:data.balances) { if (point.at>=since() && point.at<=view.now) { points.add(point); } }
        if (!points.isEmpty()) {
            Performance.Point last=points.get(points.size()-1); snapshots.add(CompactUi.money(last.value,NUM,TEXT,false));
            snapshots.add(label(DeskView.time(last.at),SMALL,MUTED));
            if (points.size()>1) { snapshots.add(new Curve(points)); }
        } else { snapshots.add(wrap("Save a snapshot to track total value.",MUTED)); }
        snapshots.setToolTipText("Includes confirmed cash and recorded holdings. Deposits, withdrawals and stock corrections affect value; they are not trading profit.");
        snapshots.add(button("Refresh prices",refresh));
        JButton checkpoint=button("Save snapshot",() -> balance(value)); checkpoint.setEnabled(value.complete());
        checkpoint.setToolTipText("Confirm current holdings and actual coins outside GE. Your scanner budget is not account cash."); snapshots.add(checkpoint); add(snapshots);
    }
    private void balance(Performance.Value value) {
        JTextField cash=new JTextField(); JCheckBox confirmed=new JCheckBox("My recorded stock matches all current holdings");
        Object[] content={"Coins in bank + inventory, excluding coins inside GE:",cash,
            "Adds recorded stock value "+n(value.stock)+" and GE coins "+n(value.geCoins)+" GP.",
            "Reconcile bank, inventory, sold items and offline fills first.",confirmed,
            "This is a market estimate, not a guaranteed liquidation value."};
        if (JOptionPane.showConfirmDialog(this,content,"Save trading-value snapshot",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) { return; }
        try {
            long coins=Long.parseLong(cash.getText().replace(",","").trim());
            if (!confirmed.isSelected() || coins<0 || coins>Integer.MAX_VALUE) { throw new IllegalArgumentException(); }
            Event event=DeskPanel.event("NOTE"); event.gross=coins; event.cost=Math.round(coins+value.stock+value.geCoins);
            event.source=Book.VALUE_SNAPSHOT_SOURCE;
            event.note="Stock estimate "+n(value.stock)+"; coins in GE "+n(value.geCoins)+"; prices fetched "+DeskView.time(view.wiki.fetchedAt);
            Book.validate(event); save.accept(event);
        } catch (IllegalArgumentException ex) { JOptionPane.showMessageDialog(this,"Confirm reconciled stock and enter whole coins from 0 to 2,147,483,647."); }
    }
    private void details(Performance.Row row,boolean recovered) {
        String[] columns={"Recorded at","Side","Units","Avg gross GP","Gross GP","Net GP","Costed units","Profit GP"};
        DefaultTableModel model=new DefaultTableModel(columns,0) { @Override public boolean isCellEditable(int r,int c) { return false; } };
        List<Performance.Execution> events=new ArrayList<>(row.executions); events.sort(Comparator.comparingLong((Performance.Execution e) -> e.at).reversed());
        for (Performance.Execution e:events) { model.addRow(new Object[]{DeskView.time(e.at),e.buy ? "Buy" : "Sell",e.quantity,n((double)e.gross/e.quantity),e.gross,e.gross-e.tax,e.buy ? "—" : e.matched,e.matched>0 ? signed(e.profit) : "—"}); }
        JTable table=new JTable(model); table.setAutoCreateRowSorter(true); table.setRowHeight(25);
        table.setFont(SMALL); table.getAccessibleContext().setAccessibleName(row.name+" buys and sells");
        JScrollPane pane=new JScrollPane(table); pane.setPreferredSize(new Dimension(850,350));
        JPanel content=new JPanel(new BorderLayout(8,8)); content.add(pane,BorderLayout.CENTER);
        JPanel heading=new JPanel(new GridLayout(0,1,0,5));
        heading.add(new JLabel("Bought "+n(row.bought)+" @ "+(row.bought>0 ? n((double)row.buyCash/row.bought) : "—")+" GP   ·   Sold "+n(row.sold)+" @ "+(row.sold>0 ? n((double)row.sellGross/row.sold) : "—")+" GP   ·   ROI "+(row.cost>0 ? String.format(Locale.UK,"%.1f%%",row.profit/row.cost*100) : "—")));
        heading.add(new JLabel(recovered ? "Approximate archive matching; recorded times are not exact fill times. Unknown-cost sales have no profit." : "Observed fills in selected period; matching can use earlier purchases. Tax is estimated.")); content.add(heading,BorderLayout.NORTH);
        content.add(button("Open this item's history",() -> history.accept(recovered,row.name)),BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this,content,row.name+" · "+(recovered ? "Recovered history" : "Recorded fills"),JOptionPane.PLAIN_MESSAGE);
    }
    private void add(JPanel card) { card.setMaximumSize(new Dimension(Integer.MAX_VALUE,card.getPreferredSize().height)); body.add(card); body.add(Box.createVerticalStrut(8)); }
    private static JPanel column(Color color) { JPanel p=new Stack(); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); p.setBackground(color); p.setAlignmentX(0); return p; }
    private static final class Stack extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r,int o,int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r,int o,int d) { return Math.max(20,r.height-30); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private static JPanel card() { JPanel p=column(CARD); p.setBorder(new EmptyBorder(9,8,9,8)); return p; }
    private static JLabel label(String text,Font font,Color color) { JLabel l=new JLabel(text); l.putClientProperty("html.disable",true); l.setFont(font); l.setForeground(color); l.setAlignmentX(0); return l; }
    private static JLabel wrap(String text,Color color) { JLabel l=new JLabel("<html><div style='width:130px'>"+DeskView.esc(text)+"</div></html>"); l.setFont(SMALL); l.setForeground(color); l.setAlignmentX(0); return l; }
    private static JPanel pair(String key,String val) { return CompactUi.pair(label(key,SMALL,MUTED),label(val,SMALL,TEXT)); }
    private static JButton button(String text,Runnable action) { JButton b=new JButton(text); b.setFont(BOLD); b.setAlignmentX(0); b.setOpaque(true); b.setBackground(new Color(49,58,63)); b.setForeground(TEXT); b.setBorder(new EmptyBorder(6,7,6,7)); b.addActionListener(e -> action.run()); return b; }
    private static String n(double v) { return DeskView.number(v); }
    private static String signed(double v) { return (v>0 ? "+" : "")+n(v); }
    private static final class Curve extends JComponent {
        private final List<Performance.Point> points;
        Curve(List<Performance.Point> points) { this.points=points; setPreferredSize(new Dimension(175,74)); setMaximumSize(new Dimension(Integer.MAX_VALUE,74)); setAlignmentX(0); setToolTipText("Progress from "+n(points.get(0).value)+" to "+n(points.get(points.size()-1).value)+" GP"); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            double min=points.stream().mapToDouble(p -> p.value).min().orElse(0),max=points.stream().mapToDouble(p -> p.value).max().orElse(0);
            long start=points.get(0).at,end=points.get(points.size()-1).at; g.setFont(SMALL.deriveFont(9f)); g.setColor(MUTED);
            g.drawString(DeskView.time(start).substring(0,6),1,68); String finish=DeskView.time(end).substring(0,6); g.drawString(finish,Math.max(1,getWidth()-38),68);
            g.setColor(MINT); g.setStroke(new BasicStroke(1.6f));
            for (int i=1;i<points.size();i++) {
                Performance.Point a=points.get(i-1),b=points.get(i);
                int x1=2+(int)((a.at-start)/(double)Math.max(1,end-start)*(getWidth()-4)),x2=2+(int)((b.at-start)/(double)Math.max(1,end-start)*(getWidth()-4));
                int y1=50-(int)((a.value-min)/Math.max(1,max-min)*38),y2=50-(int)((b.value-min)/Math.max(1,max-min)*38);
                g.drawLine(x1,y1,x2,y2);
            }
            g.dispose();
        }
    }
}
