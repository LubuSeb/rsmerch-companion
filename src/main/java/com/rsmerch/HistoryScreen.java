package com.rsmerch;

import java.util.*;
import java.util.regex.*;
import net.runelite.api.widgets.Widget;

/** Reads only widgets already supplied by the game; never opens or changes an offer. */
final class HistoryScreen {
    private static final Pattern COINS=Pattern.compile("^([0-9][0-9,]*) coins?(?:\\s|$)");
    private static final Pattern TAX=Pattern.compile("\\(([0-9][0-9,]*)\\s*-\\s*([0-9][0-9,]*)\\)");
    static List<HistoryArchive.Row> read(Widget list) {
        Widget[] children=list.getDynamicChildren();
        if (children==null || children.length==0) { return Collections.emptyList(); }
        if (children.length==1 && clean(children[0].getText()).contains("no recorded Grand Exchange trades")) { return Collections.emptyList(); }
        if (children.length%6!=0 || children.length>600) { throw new IllegalArgumentException("GE history layout changed; capture paused"); }
        List<HistoryArchive.Row> rows=new ArrayList<>();
        for (int i=0;i<children.length;i+=6) {
            for (int j=0;j<6;j++) { if (children[i+j]==null) { throw new IllegalArgumentException("GE history is still loading"); } }
            Widget item=children[i+4];
            rows.add(parse(item.getItemId(),item.getItemQuantity(),children[i+2].getText(),children[i+3].getText(),children[i+5].getText()));
        }
        return rows;
    }
    static HistoryArchive.Row parse(int item,long quantity,String direction,String name,String money) {
        HistoryArchive.Row r=new HistoryArchive.Row(); r.item=item; r.quantity=quantity;
        String side=clean(direction); if (!"Bought:".equals(side) && !"Sold:".equals(side)) { throw new IllegalArgumentException("Unrecognised GE history side"); }
        r.buy="Bought:".equals(side); r.name=clean(name.split("(?i)<br>",2)[0]);
        String amount=clean(money); Matcher coins=COINS.matcher(amount);
        if (!coins.find()) { throw new IllegalArgumentException("Unrecognised GE history amount"); }
        long net=number(coins.group(1)); Matcher tax=TAX.matcher(amount);
        if (tax.find()) {
            if (r.buy) { throw new IllegalArgumentException("Tax on buy history row"); }
            r.gross=number(tax.group(1)); r.tax=number(tax.group(2));
            if (r.gross-r.tax!=net) { throw new IllegalArgumentException("GE history totals disagree"); }
        } else { r.gross=net; r.tax=0L; }
        if (quantity<=0) { throw new IllegalArgumentException("Invalid GE history quantity"); }
        r.price=r.gross/quantity; r.raw=direction+" | "+name+" | "+money;
        HistoryArchive.validateRow(r); return r;
    }
    static String clean(String s) { return s==null ? "" : s.replaceAll("(?i)<br>"," ").replaceAll("<[^>]*>","").replace('\u00a0',' ').replaceAll("\\s+"," ").trim(); }
    private static long number(String s) { return Long.parseLong(s.replace(",","")); }
}
