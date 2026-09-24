package com.rsmerch;

import java.awt.*;
import javax.swing.*;

/** Rows stay readable at the minimum sidebar width, including large GP values. */
final class CompactUi {
    private CompactUi() {}
    static JPanel pair(JLabel key,JLabel value) {
        JPanel p=new JPanel(); p.setOpaque(false); p.setAlignmentX(0);
        int width=key.getPreferredSize().width+value.getPreferredSize().width+8;
        if (width>174) {
            p.setLayout(new GridLayout(2,1,0,2)); p.add(key); p.add(value);
        } else {
            p.setLayout(new BorderLayout(8,0)); p.add(key,BorderLayout.WEST); p.add(value,BorderLayout.EAST);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE,p.getPreferredSize().height));
        return p;
    }
    static JLabel money(double amount,Font font,Color color,boolean signed) {
        String full=(signed && amount>0 ? "+" : "")+DeskView.number(amount)+" GP";
        JLabel label=new JLabel(full); label.setFont(font); label.setForeground(color); label.setAlignmentX(0);
        if (label.getPreferredSize().width>174) {
            double unit=Math.abs(amount)>=1_000_000_000 ? 1_000_000_000 : 1_000_000;
            label.setText((signed && amount>0 ? "+" : "")+String.format(java.util.Locale.UK,"%.2f",amount/unit)+(unit==1_000_000 ? "m" : "b")+" GP");
        }
        label.setToolTipText(full); return label;
    }
}
