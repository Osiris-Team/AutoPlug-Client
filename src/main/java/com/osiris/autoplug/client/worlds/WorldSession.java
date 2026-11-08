package com.osiris.autoplug.client.worlds;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** All resources opened for a world have one idempotent owner. */
public final class WorldSession implements AutoCloseable {
    private final VirtualWorld world;
    private final ManagedServer server;
    private final Process client;
    private final int port;
    private final SharingService sharing;
    private final AutoCloseable worldLease;
    private final boolean authenticatedAccount;
    private boolean sharingEnabled;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    private SharingService.Lease shareLease;
    private ShareResult shareResult = new ShareResult(null, "Local world; sharing is off.");

    WorldSession(VirtualWorld world, ManagedServer server, Process client, int port,
                 SharingService sharing, AutoCloseable worldLease, boolean authenticatedAccount, boolean sharingEnabled, Runnable onClose) {
        this.world = world;
        this.server = server;
        this.client = client;
        this.port = port;
        this.sharing = sharing;
        this.worldLease = worldLease;
        this.authenticatedAccount = authenticatedAccount;
        this.sharingEnabled = sharingEnabled;
        this.onClose = onClose;
    }

    void monitor() {
        client.onExit().thenRun(this::close);
        server.process().onExit().thenRun(this::close);
    }

    public VirtualWorld getWorld() { return world; }
    public Process client() { return client; }
    public ManagedServer server() { return server; }
    public int getPort() { return port; }
    public boolean isClosed() { return closed.get(); }
    public synchronized ShareResult getShareResult() {
        if (shareLease != null && !shareLease.isActive())
            return new ShareResult(null, "Sharing expired or the gateway stopped responding. Share again to retry.");
        return shareResult;
    }

    public synchronized ShareResult share() {
        if (closed.get()) return new ShareResult(null, "This world is no longer running.");
        if (!sharingEnabled) {
            shareResult = new ShareResult(null, "UPnP sharing is disabled in Settings. Enable it and choose Share again, or use a TCP tunnel to 127.0.0.1:" + port + ".");
            return shareResult;
        }
        if (!authenticatedAccount) {
            shareResult = new ShareResult(null, "Offline worlds stay local. Sign in with a licensed Microsoft account and relaunch this world before sharing.");
            return shareResult;
        }
        if (shareLease != null && shareLease.isActive()) return shareResult;
        shareLease = null;
        try {
            shareLease = sharing.open(port);
            shareResult = new ShareResult(shareLease.address(), "Sharing is active until this world closes.");
        } catch (Exception e) {
            shareResult = new ShareResult(null, "Automatic sharing unavailable: " + e.getMessage()
                    + ". The world remains local. Use a TCP tunnel to 127.0.0.1:" + port
                    + " or configure a LAN forwarding endpoint before manual port forwarding.");
        }
        return shareResult;
    }

    synchronized void setSharingEnabled(boolean enabled) {
        sharingEnabled = enabled;
        if (!enabled && shareLease != null) {
            try { shareLease.close(); }
            catch (Exception e) { shareResult = new ShareResult(null, "Sharing disabled; gateway cleanup failed: " + e.getMessage()); shareLease = null; return; }
            shareLease = null;
            shareResult = new ShareResult(null, "UPnP sharing was disabled in Settings. The world remains local.");
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        synchronized (this) {
            if (shareLease != null) {
                try { shareLease.close(); }
                catch (Exception e) { shareResult = new ShareResult(null, "Sharing cleanup failed: " + e.getMessage()); }
                shareLease = null;
            }
        }
        try {
            if (client.isAlive()) {
                client.destroy();
                if (!client.waitFor(2000, TimeUnit.MILLISECONDS)) client.destroyForcibly();
            }
        } catch (InterruptedException e) {
            client.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            try { server.close(); }
            finally {
                try { worldLease.close(); } catch (Exception ignored) { }
                onClose.run();
            }
        }
    }
}
