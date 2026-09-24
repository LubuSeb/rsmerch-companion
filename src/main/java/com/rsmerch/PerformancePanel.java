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
    private final JComboBox<String> source=new JComboBox<>(new String[]{"Recorded fills","Recovered history · estimate"});
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

    PerformancePanel(Consumer<Event> save,BiConsumer<Boolean,String> history,Runnable refresh) {
        super(new BorderLayout()); this.save=save; this.history=history; this.refresh=refresh; setBackground(BG);
        JPanel controls=column(BG); controls.setBorder(new EmptyBorder(2,10,6,10));
        controls.add(label("PERFORMANCE",BOLD,MUTED)); controls.add(Box.createVerticalStrut(5));
        for (JComboBox<String> combo:Arrays.asList(source,period,order,chart)) {
            combo.setFont(SMALL); combo.setAlignmentX(0); combo.setMaximumSize(new Dimension(Integer.MAX_VALUE,26));
            combo.setBackground(CARD); combo.setForeground(TEXT);
            combo.addActionListener(e -> { page=0; render(); }); controls.add(combo); controls.add(Box.createVerticalStrut(4));
        }
        source.getAccessibleContext().setAccessibleName("Performance data source"); period.getAccessibleContext().setAccessibleName("Performance period");
        order.getAccessibleContext().setAccessibleName("Leaderboard order"); chart.getAccessibleContext().setAccessibleName("Progress chart type");
        search.setFont(SMALL); search.setMaximumSize(new Dimension(Integer.MAX_VALUE,27)); search.setAlignmentX(0);
        search.setBackground(CARD); search.setForeground(TEXT); search.setCaretColor(MINT);
        search.getAccessibleContext().setAccessibleName("Filter leaderboard by item");
        controls.add(label("Filter items",SMALL,MUTED)); controls.add(search);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); } public void removeUpdate(DocumentEvent e) { changed(); } public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { page=0; render(); }
        });
        add(controls,BorderLayout.NORTH); body.setBorder(new EmptyBorder(0,10,10,10)); scroll.setBorder(null);
        scroll.getViewport().setBackground(BG); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20); scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6,0)); add(scroll,BorderLayout.CENTER);
    }
    void show(DeskView view) { this.view=view; render(); }
    void selectSource(int index) { source.setSelectedIndex(index); }
    private long since() {
        return period.getSelectedIndex()==1 ? view.now-Book.DAY : period.getSelectedIndex()==2 ? view.now-7*Book.DAY : period.getSelectedIndex()==3 ? view.now-30*Book.DAY : 0;
    }
    private void render() {
        if (view==null) { return; }
        int y=scroll.getVerticalScrollBar().getValue(); body.removeAll(); boolean recovered=source.getSelectedIndex()==1;
        Performance.Dataset data=recovered ? view.recoveredPerformance : view.performance;
        Performance.Summary summary=Performance.summarise(data,since(),view.now);
        JPanel totals=card(); totals.add(label(recovered ? "RECOVERED FIFO · APPROX." : "MATCHED PROFIT · EST.",BOLD,MUTED));
        totals.add(label(summary.matched>0 ? signed(summary.profit)+" GP" : "No costed sales yet",summary.matched>0 ? NUM : BOLD,summary.profit<0 ? WARN : MINT));
        totals.add(pair("Costed / sold units",n(summary.matched)+" / "+n(summary.sells)));
        totals.add(pair("Bought / sold",n(summary.buys)+" / "+n(summary.sells)));
        totals.add(pair("Buy spend",n(summary.spent)+" GP")); totals.add(pair("Sale proceeds",n(summary.received)+" GP"));
        totals.add(pair("Tax estimate",n(summary.tax)+" GP"));
        if (summary.unknown>0) { totals.add(wrap(n(summary.unknown)+" sold units have no matched purchase cost; excluded from profit.",WARN)); }
        totals.add(wrap(recovered ? "Separate estimate from rounded archive records, ordered by recorded time. Missing trades and opening stock can change the result." : "FIFO profit on recorded sales with known costs. Offline fills and unrecorded stock use can leave gaps.",MUTED)); add(totals);
        List<Performance.Point> points=chart.getSelectedIndex()==0 ? summary.curve : data.balances;
        if (chart.getSelectedIndex()==1) { points=new ArrayList<>(); for (Performance.Point point:data.balances) { if (point.at>=since() && point.at<=view.now) { points.add(point); } } }
        JPanel progress=card(); progress.add(label(chart.getSelectedIndex()==0 ? "CUMULATIVE MATCHED PROFIT" : "SAVED TRADING VALUE",BOLD,MUTED));
        if (points.size()>1) { progress.add(new Curve(points)); }
        else { progress.add(wrap(chart.getSelectedIndex()==0 ? "The line appears as costed sales build up." : "Save two value snapshots to chart total trading value.",MUTED)); }
        if (chart.getSelectedIndex()==1) { progress.add(wrap("Value changes include deposits, withdrawals and stock corrections; they are not trading profit.",MUTED)); }
        add(progress);
        if (!recovered && chart.getSelectedIndex()==1) { addValue(data); }
        else if (!recovered) {
            JButton value=button("Trading value & stock",() -> chart.setSelectedIndex(1)); body.add(value); body.add(Box.createVerticalStrut(8));
        }
        else if (view.archive.error!=null) { JPanel error=card(); error.add(wrap("History error: "+view.archive.error,WARN)); add(error); }
        List<Performance.Row> rows=new ArrayList<>(summary.rows);
        String query=search.getText().trim().toLowerCase(Locale.ROOT); rows.removeIf(r -> !r.name.toLowerCase(Locale.ROOT).contains(query));
        if (order.getSelectedIndex()==1) { rows.sort(Comparator.comparing((Performance.Row r) -> r.matched==0).thenComparingDouble(r -> r.profit)); }
        if (order.getSelectedIndex()==2) { rows.sort(Comparator.comparingLong((Performance.Row r) -> r.sellNet).reversed()); }
        if (order.getSelectedIndex()==3) { rows.sort(Comparator.comparingLong((Performance.Row r) -> r.sold).reversed()); }
        body.add(label("ITEM LEADERBOARD · "+rows.size(),BOLD,MUTED)); body.add(Box.createVerticalStrut(6));
        int pages=Math.max(1,(rows.size()+19)/20); page=Math.min(page,pages-1);
        for (int i=page*20;i<Math.min(rows.size(),page*20+20);i++) {
            Performance.Row row=rows.get(i); JPanel item=card(); item.add(wrap((i+1)+". "+row.name,TEXT));
            item.add(label(row.matched>0 ? signed(row.profit)+" GP" : "Profit unavailable",NUM.deriveFont(18f),row.profit<0 ? WARN : MINT));
            item.add(pair("Buy / sell units",n(row.bought)+" / "+n(row.sold)));
            item.add(pair("Avg buy",row.bought>0 ? n((double)row.buyCash/row.bought)+" GP" : "—"));
            item.add(pair("Avg sell · gross",row.sold>0 ? n((double)row.sellGross/row.sold)+" GP" : "—"));
            item.add(pair("Matched ROI",row.cost>0 ? String.format(Locale.UK,"%.1f%%",row.profit/row.cost*100) : "—"));
            if (row.unknown>0) { item.add(wrap(n(row.unknown)+" sold units missing cost",WARN)); }
            item.add(button("View buys & sells",() -> details(row,recovered))); add(item);
        }
        if (rows.isEmpty()) {
            JPanel empty=card(); empty.add(wrap("No trades in this source and period.",MUTED));
            empty.add(button(recovered ? "Show recorded fills" : "Show recovered history",() -> source.setSelectedIndex(recovered ? 0 : 1))); add(empty);
        }
        if (pages>1) {
            JPanel pager=new JPanel(new GridLayout(1,2,5,0)); pager.setOpaque(false);
            JButton back=button("Previous",() -> { page--; render(); }); back.setEnabled(page>0);
            JButton next=button("Next",() -> { page++; render(); }); next.setEnabled(page<pages-1);
            pager.add(back); pager.add(next); body.add(pager);
        }
        body.revalidate(); body.repaint(); SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(y));
    }
    private void addValue(Performance.Dataset data) {
        Performance.Value value=Performance.value(data,view.wiki,view.now); JPanel c=card();
        c.add(label("RECORDED STOCK · NOW",BOLD,MUTED));
        c.add(label(n(value.stock)+" GP"+(value.complete() ? "" : " · partial"),NUM.deriveFont(18f),TEXT));
        c.add(wrap("After estimated tax at fresh Wiki instant-sell references. Available demand is unknown.",MUTED));
        c.add(pair("Known stock cost",n(value.knownCost)+" GP"));
        c.add(pair("Unrealised · priced lots",signed(value.unrealised)+" GP"));
        c.add(pair("Coins in GE slots",n(value.geCoins)+" GP"));
        if (value.unpricedUnits>0) { c.add(wrap(n(value.unpricedUnits)+" units lack a fresh price; excluded from value.",WARN)); }
        if (value.unknownCostUnits>0) { c.add(wrap(n(value.unknownCostUnits)+" units lack cost; excluded from unrealised profit.",WARN)); }
        if (!data.balances.isEmpty()) {
            Performance.Point last=data.balances.get(data.balances.size()-1);
            c.add(wrap("Last trading-value snapshot: "+n(last.value)+" GP · "+DeskView.time(last.at),TEXT));
        }
        c.add(button("Refresh value prices",refresh));
        JButton checkpoint=button("Save trading value",() -> balance(value)); checkpoint.setEnabled(value.complete());
        checkpoint.setToolTipText("Reconcile holdings and enter actual coins outside GE. This does not use your scanner budget."); c.add(checkpoint);
        add(c);
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
        content.add(new JLabel(recovered ? "Approximate archive matching; recorded times are not exact fill times. Unknown-cost sales have no profit." : "Observed fills in selected period; matching can use earlier purchases. Tax is estimated."),BorderLayout.NORTH);
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
    private static JPanel pair(String key,String val) { JPanel p=new JPanel(new BorderLayout(3,0)); p.setOpaque(false); p.setAlignmentX(0); p.add(label(key,SMALL,MUTED),BorderLayout.WEST); p.add(label(val,SMALL,TEXT),BorderLayout.EAST); return p; }
    private static JButton button(String text,Runnable action) { JButton b=new JButton(text); b.setFont(BOLD); b.setAlignmentX(0); b.setOpaque(true); b.setBackground(new Color(49,58,63)); b.setForeground(TEXT); b.setBorder(new EmptyBorder(6,7,6,7)); b.addActionListener(e -> action.run()); return b; }
    private static String n(double v) { return DeskView.number(v); }
    private static String signed(double v) { return (v>0 ? "+" : "")+n(v); }
    private static final class Curve extends JComponent {
        private final List<Performance.Point> points;
        Curve(List<Performance.Point> points) { this.points=points; setPreferredSize(new Dimension(175,92)); setMaximumSize(new Dimension(Integer.MAX_VALUE,92)); setAlignmentX(0); setToolTipText("Progress from "+n(points.get(0).value)+" to "+n(points.get(points.size()-1).value)+" GP"); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g=(Graphics2D)g0.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            double min=points.stream().mapToDouble(p -> p.value).min().orElse(0),max=points.stream().mapToDouble(p -> p.value).max().orElse(0);
            long start=points.get(0).at,end=points.get(points.size()-1).at; g.setFont(SMALL.deriveFont(9f)); g.setColor(MUTED);
            g.drawString(n(max)+" GP",1,12); g.drawString(n(min)+" GP",1,69);
            g.drawString(DeskView.time(start).substring(0,6),1,86); String finish=DeskView.time(end).substring(0,6); g.drawString(finish,Math.max(1,getWidth()-38),86);
            g.setColor(MINT); g.setStroke(new BasicStroke(1.6f));
            for (int i=1;i<points.size();i++) {
                Performance.Point a=points.get(i-1),b=points.get(i);
                int x1=2+(int)((a.at-start)/(double)Math.max(1,end-start)*(getWidth()-4)),x2=2+(int)((b.at-start)/(double)Math.max(1,end-start)*(getWidth()-4));
                int y1=58-(int)((a.value-min)/Math.max(1,max-min)*38),y2=58-(int)((b.value-min)/Math.max(1,max-min)*38);
                g.drawLine(x1,y1,x2,y2);
            }
            g.dispose();
        }
    }
}
