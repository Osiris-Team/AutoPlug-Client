package com.osiris.autoplug.client.ui.client;

import com.osiris.autoplug.client.clientapp.*;
import com.osiris.autoplug.client.launcher.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientSettingsPanel extends DashboardPanel {
    private final NativeClientService service;
    private final JTextField offlineName = new JTextField(24), clientId = new JTextField(36), template = new JTextField(24);
    private final JCheckBox offline = new JCheckBox("Use offline mode for local testing");
    private final JTextField worldPort = new JTextField(8);
    private final JCheckBox upnp = new JCheckBox("Allow UPnP when Play & share is chosen");
    private final JTextField[] java = {new JTextField(36), new JTextField(36), new JTextField(36), new JTextField(36)};
    private final JLabel account = new JLabel();
    private AtomicBoolean cancelled;
    private long authenticationAttempt;
    public void release() { if (cancelled != null) cancelled.set(true); service.cancelAuthentication(authenticationAttempt); }
    public ClientSettingsPanel(NativeClientService service) {
        this.service = service;
        offlineName.setText(service.settings.offlineName); offline.setSelected(service.settings.offlineMode); clientId.setText(service.settings.microsoftClientId); template.setText(service.settings.templateId);
        worldPort.setText(Integer.toString(service.settings.worldPort())); upnp.setSelected(service.settings.upnpAllowed());
        int[] majors = {8, 17, 21, 25}; for (int i = 0; i < majors.length; i++) java[i].setText(service.settings.javaPaths.getOrDefault(majors[i], ""));
        JPanel form = new JPanel(new GridBagLayout()); int row = 0;
        row(form, row++, "Account", account); row(form, row++, "Testing", offline); row(form, row++, "Offline player name", offlineName);
        row(form, row++, "Microsoft public application ID", clientId); row(form, row++, "Default client template ID", template);
        row(form, row++, "World port (0 = automatic)", worldPort); row(form, row++, "Router sharing", upnp);
        for (int i = 0; i < majors.length; i++) row(form, row++, "Java " + majors[i] + " executable (optional)", java[i]);
        JLabel note = new JLabel("<html>Leave Java paths empty for automatic, verified runtime downloads.<br>Microsoft sign-in uses your registered public client application and a licensed account.<br>Access and refresh tokens stay in memory; sign in again after restarting AutoPlug.<br>Offline mode cannot join servers that require online authentication.<br>UPnP is requested only by Play & share; it uses a temporary TCP lease.<br>Port 0 selects a free port; a fixed port must be available. Changes apply to new launches.</html>");
        GridBagConstraints space = new GridBagConstraints(); space.gridx = 0; space.gridy = row; space.gridwidth = 2; space.fill = GridBagConstraints.HORIZONTAL; space.insets = new Insets(12, 0, 0, 0); form.add(note, space);
        JPanel top = new JPanel(new BorderLayout()); top.add(form, BorderLayout.NORTH); add(new JScrollPane(top), BorderLayout.CENTER);
        JButton save = new JButton("Save preferences"), signIn = new JButton("Sign in with Microsoft"), signOut = new JButton("Sign out");
        save.addActionListener(event -> save()); signIn.addActionListener(event -> signIn());
        signOut.addActionListener(event -> { if (cancelled != null) cancelled.set(true); service.signOut(); account.setText(service.accountLabel()); status.setText("Signed out; memory session cleared"); });
        JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.add(buttons(save, signIn, signOut), BorderLayout.NORTH); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH); account.setText(service.accountLabel());
    }
    private static void row(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints constraints = new GridBagConstraints(); constraints.gridx = 0; constraints.gridy = row; constraints.anchor = GridBagConstraints.WEST; constraints.insets = new Insets(5, 0, 5, 18); form.add(new JLabel(label), constraints);
        constraints.gridx = 1; constraints.weightx = 1; constraints.fill = GridBagConstraints.HORIZONTAL; constraints.insets = new Insets(5, 0, 5, 0); form.add(field, constraints);
    }
    private boolean save() {
        try {
            java.util.Map<Integer, String> paths = new java.util.LinkedHashMap<>(); int[] majors = {8, 17, 21, 25}; for (int i = 0; i < majors.length; i++) paths.put(majors[i], java[i].getText().trim());
            service.applyPreferences(offlineName.getText().trim(), offline.isSelected(), clientId.getText().trim(), template.getText().trim(), paths, Integer.parseInt(worldPort.getText().trim()), upnp.isSelected());
            account.setText(service.accountLabel()); status.setText("Preferences saved"); return true;
        } catch (Exception exception) { JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid settings", JOptionPane.ERROR_MESSAGE); return false; }
    }
    private void signIn() {
        if (cancelled != null && !cancelled.get()) { status.setText("A sign-in is already in progress"); return; }
        if (!save()) return;
        AtomicBoolean cancel = new AtomicBoolean(false); cancelled = cancel;
        String authorizingClientId = service.settings.microsoftClientId;
        long attempt = service.beginAuthentication(); authenticationAttempt = attempt;
        work("Starting Microsoft device sign-in…", () -> {
            MicrosoftOAuth oauth = new MicrosoftOAuth(authorizingClientId);
            return new Object[]{oauth, oauth.beginDeviceAuthorization()};
        }, result -> {
            MicrosoftOAuth oauth = (MicrosoftOAuth)result[0]; MicrosoftOAuth.DeviceAuthorization device = (MicrosoftOAuth.DeviceAuthorization)result[1];
            if (cancel.get() || !service.authenticationCurrent(attempt)) { oauth.clearSession(); cancel.set(true); return; }
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Microsoft sign-in", Dialog.ModalityType.MODELESS);
            JPanel content = new JPanel(new BorderLayout(12, 12)); content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            JTextArea instructions = new JTextArea("Open " + device.getVerificationUri() + "\nEnter code: " + device.getUserCode() + "\nExpires: " + device.getExpiresAt() + "\nComplete sign-in yourself in your browser.", 5, 48); instructions.setEditable(false); content.add(instructions, BorderLayout.CENTER);
            JButton cancelButton = new JButton("Cancel sign-in"); cancelButton.addActionListener(event -> { cancel.set(true); dialog.dispose(); }); content.add(cancelButton, BorderLayout.SOUTH);
            dialog.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent event) { cancel.set(true); } }); dialog.setContentPane(content); dialog.pack(); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
            new SwingWorker<LaunchAccount, Void>() {
                protected LaunchAccount doInBackground() throws Exception { return oauth.awaitMinecraftAccount(device, () -> cancel.get() || !service.authenticationCurrent(attempt)); }
                protected void done() {
                    dialog.dispose();
                    try { LaunchAccount loggedIn = get(); if (!cancel.get() && service.completeAuthenticationAndSave(attempt, loggedIn, oauth, authorizingClientId)) { offline.setSelected(false); account.setText(service.accountLabel()); status.setText("Signed in; credentials stay in memory"); } else { oauth.clearSession(); status.setText("Sign-in cancelled"); } }
                    catch (Exception exception) { oauth.clearSession(); if (!service.authenticationCurrent(attempt)) cancel.set(true); status.setText(cancel.get() ? "Sign-in cancelled" : "Sign-in failed"); if (!cancel.get()) JOptionPane.showMessageDialog(ClientSettingsPanel.this, exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage(), "Microsoft sign-in", JOptionPane.ERROR_MESSAGE); }
                    finally { cancel.set(true); }
                }
            }.execute();
        }, error -> { cancel.set(true); service.cancelAuthentication(attempt); });
    }
}
