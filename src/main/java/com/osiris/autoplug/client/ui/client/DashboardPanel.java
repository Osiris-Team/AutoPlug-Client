package com.osiris.autoplug.client.ui.client;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Network, migration and preparation work never runs on Swing's event dispatch thread. */
abstract class DashboardPanel extends JPanel {
    protected final JLabel status = new JLabel("Ready");
    DashboardPanel() { super(new BorderLayout(12, 12)); setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16)); }
    protected <T> void work(String label, Callable<T> action, Consumer<T> done) {
        work(label, action, done, ignored -> {});
    }
    protected <T> void work(String label, Callable<T> action, Consumer<T> done, Consumer<Throwable> failed) {
        status.setText(label);
        new SwingWorker<T, Void>() {
            protected T doInBackground() throws Exception { return action.call(); }
            protected void done() {
                try { T result = get(); status.setText("Ready"); done.accept(result); }
                catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    failed.accept(cause);
                    status.setText("Action failed");
                    JOptionPane.showMessageDialog(DashboardPanel.this, cause.getMessage(), "AutoPlug", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    protected boolean confirm(String message) {
        if (SwingUtilities.isEventDispatchThread()) return JOptionPane.showConfirmDialog(this, message, "Review changes", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
        final boolean[] answer = {false};
        try { SwingUtilities.invokeAndWait(() -> answer[0] = confirm(message)); }
        catch (Exception exception) { throw new IllegalStateException("Could not request confirmation", exception); }
        return answer[0];
    }
    protected static JPanel buttons(JButton... buttons) { JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); for (JButton button : buttons) panel.add(button); return panel; }
}
