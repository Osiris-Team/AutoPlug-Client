package com.osiris.autoplug.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Path2D;

/** Neutral dashboard surfaces with a consistent blue accent in light and dark themes. */
final class DashboardTheme {
    static final int ARC = 16;
    private static final Color BLUE = new Color(0x0066FF);

    private DashboardTheme() {}

    static void installDefaults() {
        UIManager.put("Button.arc", ARC);
        UIManager.put("Component.arc", ARC);
        UIManager.put("TextComponent.arc", ARC);
        UIManager.put("ProgressBar.arc", ARC);
        UIManager.put("Component.focusColor", BLUE);
        UIManager.put("Component.focusedBorderColor", BLUE);
        UIManager.put("Button.focusedBorderColor", BLUE);
        UIManager.put("ToggleButton.selectedBackground", BLUE);
        UIManager.put("ToggleButton.selectedForeground", Color.WHITE);
        UIManager.put("ToggleButton.focusedBorderColor", BLUE);
        UIManager.put("TabbedPane.underlineColor", BLUE);
        UIManager.put("ProgressBar.foreground", BLUE);
        UIManager.put("CheckBox.icon.selectedBackground", BLUE);
        UIManager.put("CheckBox.icon.selectedBorderColor", BLUE);
        UIManager.put("CheckBox.icon.checkmarkColor", Color.WHITE);
        UIManager.put("Table.selectionBackground", BLUE);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", new ColorUIResource(dark() ? 0x2B3443 : 0xF3F6FB));
        UIManager.put("TableHeader.foreground", new ColorUIResource(dark() ? 0xE4EAF5 : 0x34435A));
        UIManager.put("Table.gridColor", new ColorUIResource(dark() ? 0x3A4555 : 0xE4EAF3));
    }

    static boolean dark() {
        Color background = UIManager.getColor("Panel.background");
        return background != null && background.getRed() + background.getGreen() + background.getBlue() < 420;
    }

    static Color muted() { return dark() ? new Color(0xBCC8DA) : new Color(0x526176); }
    static Color accent() { return dark() ? new Color(0x8AB8FF) : BLUE; }

    /** Small original line icons inherit the control foreground in either theme. */
    static Icon icon(String action) {
        return new Icon() {
            public int getIconWidth() { return 18; }
            public int getIconHeight() { return 18; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.translate(x, y); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(component.isEnabled() ? component.getForeground() : muted());
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    String key = action.toLowerCase(java.util.Locale.ROOT);
                    if (key.equals("players")) {
                        g.drawOval(3, 2, 5, 5); g.drawOval(10, 4, 4, 4); g.drawArc(1, 9, 10, 11, 0, 180); g.drawArc(9, 10, 8, 9, 0, 150);
                    } else if (key.equals("version")) {
                        g.drawRoundRect(2, 3, 14, 12, 2, 2); g.drawLine(2, 7, 16, 7); g.fillOval(4, 4, 1, 1); g.drawLine(5, 10, 8, 10); g.drawLine(5, 12, 12, 12);
                    } else if (key.equals("address")) {
                        g.drawRoundRect(2, 5, 9, 7, 4, 4); g.drawRoundRect(7, 8, 9, 7, 4, 4);
                    } else if (key.equals("sort")) {
                        g.drawLine(5, 3, 5, 15); g.drawLine(2, 6, 5, 3); g.drawLine(8, 6, 5, 3); g.drawLine(12, 3, 12, 15); g.drawLine(9, 12, 12, 15); g.drawLine(15, 12, 12, 15);
                    } else if (key.equals("online")) {
                        g.drawOval(2, 2, 14, 14); g.drawPolyline(new int[]{5, 8, 13}, new int[]{9, 12, 6}, 3);
                    } else if (key.equals("offline-server")) {
                        g.drawOval(2, 2, 14, 14); g.drawLine(5, 9, 13, 9);
                    } else if (key.contains("play") || key.contains("launch") || key.contains("join") || key.equals("start")) {
                        Path2D p = new Path2D.Float(); p.moveTo(5, 3); p.lineTo(15, 9); p.lineTo(5, 15); p.closePath(); g.fill(p);
                    } else if (key.contains("folder") || key.contains("configuration")) {
                        Path2D p = new Path2D.Float(); p.moveTo(2, 5); p.lineTo(7, 5); p.lineTo(9, 7); p.lineTo(16, 7); p.lineTo(15, 15); p.lineTo(2, 15); p.closePath(); g.draw(p);
                    } else if (key.contains("delete") || key.contains("remove")) {
                        g.drawLine(3, 5, 15, 5); g.drawLine(7, 2, 11, 2); g.drawRoundRect(5, 5, 8, 11, 2, 2); g.drawLine(8, 8, 8, 13); g.drawLine(10, 8, 10, 13);
                    } else if (key.contains("copy") || key.contains("clone")) {
                        g.drawRoundRect(6, 6, 9, 10, 2, 2); g.drawLine(3, 12, 3, 2); g.drawLine(3, 2, 11, 2);
                    } else if (key.contains("edit")) {
                        g.drawLine(4, 13, 13, 4); g.drawLine(7, 15, 15, 7); g.drawLine(13, 4, 15, 7); g.drawLine(4, 13, 3, 16); g.drawLine(3, 16, 7, 15);
                    } else if (key.contains("share")) {
                        g.drawLine(5, 9, 13, 4); g.drawLine(5, 9, 13, 14); g.drawOval(2, 7, 4, 4); g.drawOval(12, 1, 4, 4); g.drawOval(12, 12, 4, 4);
                    } else if (key.contains("refresh") || key.contains("reload") || key.contains("update") || key.contains("restart")) {
                        g.drawArc(3, 3, 12, 12, 40, 285); g.drawLine(15, 2, 15, 7); g.drawLine(11, 7, 15, 7);
                    } else if (key.contains("ping") || key.contains("activity") || key.contains("check")) {
                        g.drawPolyline(new int[]{1, 5, 7, 10, 12, 17}, new int[]{10, 10, 4, 15, 9, 9}, 6);
                    } else if (key.contains("import") || key.contains("add jar")) {
                        g.drawLine(9, 2, 9, 11); g.drawLine(5, 7, 9, 11); g.drawLine(13, 7, 9, 11); g.drawPolyline(new int[]{3, 3, 15, 15}, new int[]{12, 16, 16, 12}, 4);
                    } else if (key.contains("eula")) {
                        g.drawRoundRect(3, 2, 12, 14, 2, 2); g.drawLine(6, 6, 12, 6); g.drawLine(6, 9, 12, 9); g.drawLine(6, 12, 10, 12);
                    } else if (key.contains("settings") || key.contains("advanced")) {
                        for (int row = 4; row <= 14; row += 5) { g.drawLine(2, row, 16, row); g.fillOval(row == 9 ? 10 : 4, row - 2, 4, 4); }
                    } else if (key.contains("world")) {
                        g.drawOval(2, 2, 14, 14); g.drawOval(6, 2, 6, 14); g.drawLine(2, 9, 16, 9);
                    } else if (key.contains("server")) {
                        g.drawRoundRect(2, 2, 14, 6, 2, 2); g.drawRoundRect(2, 10, 14, 6, 2, 2); g.fillOval(4, 4, 2, 2); g.fillOval(4, 12, 2, 2);
                    } else if (key.contains("favorite") || key.contains("template")) {
                        Path2D p = new Path2D.Float(); for (int n = 0; n < 10; n++) { double angle = -Math.PI / 2 + n * Math.PI / 5, r = n % 2 == 0 ? 7 : 3.2; double px = 9 + Math.cos(angle) * r, py = 9 + Math.sin(angle) * r; if (n == 0) p.moveTo(px, py); else p.lineTo(px, py); } p.closePath(); g.draw(p);
                    } else if (key.contains("profile") || key.contains("sign in") || key.contains("offline")) {
                        g.drawOval(6, 2, 6, 6); g.drawArc(3, 10, 12, 11, 0, 180);
                    } else if (key.contains("save") || key.contains("back")) {
                        g.drawRoundRect(3, 2, 12, 14, 2, 2); g.drawRect(6, 3, 6, 4); g.drawRect(6, 11, 6, 5);
                    } else if (key.equals("stop")) g.fillRoundRect(4, 4, 10, 10, 2, 2);
                    else { g.drawLine(3, 9, 15, 9); g.drawLine(9, 3, 9, 15); }
                } finally { g.dispose(); }
            }
        };
    }

    static JButton primary(JButton button) {
        button.setBackground(BLUE); button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        return button;
    }

    static void navigation(JToggleButton button) {
        button.putClientProperty("AutoPlug.navigation", Boolean.TRUE);
        button.addItemListener(event -> refreshNavigation(button));
        refreshNavigation(button);
    }

    private static void refreshNavigation(JToggleButton button) {
        button.setBackground(button.isSelected() ? BLUE : (dark() ? new Color(0x273142) : new Color(0xF6F8FC)));
        button.setForeground(button.isSelected() ? Color.WHITE : (dark() ? new Color(0xE4EAF5) : new Color(0x34435A)));
    }

    static JLabel badge(String text) {
        JLabel label = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(dark() ? new Color(36, 75, 128, 150) : new Color(0xEAF2FF));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                } finally { g.dispose(); }
                super.paintComponent(graphics);
            }
        };
        label.setBorder(new EmptyBorder(2, 8, 2, 8)); tint(label, true);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f)); return label;
    }

    static void tint(JComponent component, boolean accent) {
        component.putClientProperty("AutoPlug.accent", accent);
        component.setForeground(accent ? accent() : muted());
    }

    static void refreshColors(Component component) {
        if (component instanceof JComponent) {
            Object accent = ((JComponent) component).getClientProperty("AutoPlug.accent");
            if (accent instanceof Boolean) component.setForeground((Boolean) accent ? accent() : muted());
            if (component instanceof JToggleButton && Boolean.TRUE.equals(((JComponent) component).getClientProperty("AutoPlug.navigation")))
                refreshNavigation((JToggleButton) component);
        }
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) refreshColors(child);
    }

    static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }

    /** Wrapped text may invalidate ancestors during measurement; avoid BoxLayout's mutable size caches. */
    static GridBagLayout verticalStack() { return new VerticalStackLayout(); }

    private static final class VerticalStackLayout extends GridBagLayout {
        VerticalStackLayout() {
            defaultConstraints.gridx = 0;
            defaultConstraints.gridy = GridBagConstraints.RELATIVE;
            defaultConstraints.weightx = 1;
            defaultConstraints.fill = GridBagConstraints.HORIZONTAL;
            defaultConstraints.anchor = GridBagConstraints.NORTHWEST;
        }
    }

    static JPanel surface(LayoutManager layout, int padding) {
        JPanel panel = new Surface(layout);
        panel.setBorder(new EmptyBorder(padding, padding, padding, padding));
        return panel;
    }

    static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setOpaque(false); scroll.getViewport().setOpaque(false); scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    static void canvas(Graphics graphics, int width, int height) {
        if (width <= 0 || height <= 0) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(dark() ? new Color(0x181E29) : new Color(0xF5F7FB));
            g.fillRect(0, 0, width, height);
        } finally { g.dispose(); }
    }

    static JLabel worldThumbnail() {
        JLabel label = new JLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
                    g.setColor(dark() ? new Color(0x263750) : new Color(0xEEF4FF));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    if (getIcon() == null) {
                        // A neutral world symbol while no saved thumbnail is available.
                        g.setColor(accent()); g.setStroke(new BasicStroke(1.7f));
                        int x = getWidth() / 2 - 21, y = getHeight() / 2 - 21;
                        g.drawOval(x, y, 42, 42); g.drawOval(x + 11, y, 20, 42);
                        g.drawLine(x, y + 21, x + 42, y + 21);
                        g.drawArc(x + 3, y + 6, 36, 12, 180, 180);
                        g.drawArc(x + 3, y + 25, 36, 12, 0, 180);
                    }
                    super.paintComponent(g);
                } finally { g.dispose(); }
            }
        };
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(96, 96)); return label;
    }

    /** An original fallback symbol, not a fetched server logo. */
    static Icon serverIcon() {
        return new Icon() {
            public int getIconWidth() { return 52; }
            public int getIconHeight() { return 52; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.translate(x, y); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(dark() ? new Color(0x263E61) : new Color(0xEAF2FF)); g.fillRoundRect(0, 0, 52, 52, ARC, ARC);
                    g.setColor(accent()); g.setStroke(new BasicStroke(1.8f));
                    g.drawRoundRect(12, 12, 28, 12, 4, 4); g.drawRoundRect(12, 29, 28, 12, 4, 4);
                    g.fillOval(17, 17, 3, 3); g.fillOval(17, 34, 3, 3); g.drawLine(25, 18, 34, 18); g.drawLine(25, 35, 34, 35);
                } finally { g.dispose(); }
            }
        };
    }

    private static final class Surface extends JPanel {
        Surface(LayoutManager layout) { super(layout); setOpaque(false); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color top = dark() ? new Color(39, 49, 65, 242) : new Color(255, 255, 255, 245);
                Color bottom = dark() ? new Color(32, 41, 56, 230) : new Color(250, 252, 255, 225);
                g.setPaint(new GradientPaint(0, 0, top, 0, Math.max(1, getHeight()), bottom));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                boolean selected = Boolean.TRUE.equals(getClientProperty("AutoPlug.selected"));
                g.setColor(selected ? accent() : dark() ? new Color(149, 171, 204, 55) : new Color(209, 219, 234, 190));
                g.setStroke(new BasicStroke(selected ? 2f : 1f));
                g.draw(new RoundRectangle2D.Float(1f, 1f, getWidth() - 2f, getHeight() - 2f, ARC, ARC));
            } finally { g.dispose(); }
            super.paintComponent(graphics);
        }
    }
}
