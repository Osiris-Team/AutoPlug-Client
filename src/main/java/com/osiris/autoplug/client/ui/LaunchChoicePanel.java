package com.osiris.autoplug.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import com.osiris.autoplug.client.ui.LauncherActions.ProfileInfo;

/** One large target per choice, with an explicit, cancelable remembered-choice countdown. */
final class LaunchChoicePanel extends JPanel implements AutoCloseable {
    private final Timer timer;
    private final LongSupplier clock;
    private final Consumer<ProfileInfo> choose;
    private final ProfileInfo preferred;
    private final JButton wait;
    private final long deadline;
    private boolean waiting, finished;

    LaunchChoicePanel(List<ProfileInfo> profiles, ProfileInfo preferred, boolean autoLaunch,
                      Consumer<ProfileInfo> choose) {
        this(profiles, preferred, autoLaunch, choose, System::nanoTime);
    }
    LaunchChoicePanel(List<ProfileInfo> profiles, ProfileInfo preferred, boolean autoLaunch,
                      Consumer<ProfileInfo> choose, LongSupplier clock) {
        super(new BorderLayout(0, 12)); this.clock = clock; this.choose = choose; this.preferred = preferred;
        setBorder(new EmptyBorder(16, 16, 16, 16));
        deadline = clock.getAsLong() + 2_000_000_000L;
        waiting = autoLaunch && preferred != null && preferred.launchable && !preferred.template;
        JLabel heading = new JLabel(waiting ? "Launching with " + preferred.name : "Choose your client profile");
        heading.putClientProperty("html.disable", Boolean.TRUE); add(heading, BorderLayout.NORTH);
        JPanel choices = new Choices();
        for (ProfileInfo profile : profiles) {
            JButton button = new JButton(); button.putClientProperty("html.disable", Boolean.TRUE); button.setText(profile.toString());
            button.setName("launch-choice-" + profile.id); button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setPreferredSize(new Dimension(470, 54)); button.setEnabled(profile.launchable && !profile.template);
            button.setToolTipText(profile.migrationSummary);
            button.addActionListener(e -> select(profile)); choices.add(button);
        }
        JScrollPane scroll = new JScrollPane(choices); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(500, Math.min(340, Math.max(62, profiles.size() * 62)))); add(scroll);
        wait = new JButton(); wait.setName("abort-auto-launch"); wait.setPreferredSize(new Dimension(500, 52));
        wait.setBackground(new Color(177, 40, 49)); wait.setForeground(Color.WHITE); wait.setOpaque(true);
        wait.addActionListener(e -> { cancelCountdown(); heading.setText("Choose your client profile"); });
        add(wait, BorderLayout.SOUTH);
        timer = new Timer(100, e -> tick());
        tick(); if (waiting) timer.start();
    }
    void tick() {
        if (!waiting || finished) { wait.setVisible(false); return; }
        long remaining = deadline - clock.getAsLong();
        if (remaining <= 0) { select(preferred); return; }
        wait.setText("Wait, let me select something else… (" + Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L) + "s)");
    }
    void cancelCountdown() { waiting = false; timer.stop(); wait.setVisible(false); revalidate(); }
    private void select(ProfileInfo profile) {
        if (finished) return; finished = true; waiting = false; if (timer != null) timer.stop(); choose.accept(profile);
    }
    @Override public void close() { finished = true; waiting = false; timer.stop(); }
    private static final class Choices extends JPanel implements Scrollable {
        Choices() { super(new GridLayout(0, 1, 0, 8)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
