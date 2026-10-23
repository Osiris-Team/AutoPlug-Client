package com.osiris.autoplug.client.mc.launch;

import java.io.InterruptedIOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** One cancellation scope for downloads, authentication and the child game process. */
public final class Cancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CopyOnWriteArrayList<Registration> listeners = new CopyOnWriteArrayList<>();

    public void check() throws InterruptedIOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Minecraft operation cancelled");
        }
    }

    public boolean isCancelled() { return cancelled.get(); }

    public AutoCloseable onCancel(Runnable listener) {
        Registration registration = new Registration(java.util.Objects.requireNonNull(listener));
        listeners.add(registration);
        if (cancelled.get()) { registration.fire(); listeners.remove(registration); }
        return () -> { registration.done.set(true); listeners.remove(registration); };
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            for (Registration listener : listeners) listener.fire();
            listeners.clear();
        }
    }

    private final class Registration {
        private final AtomicBoolean done = new AtomicBoolean();
        private final Runnable listener;
        private Registration(Runnable listener) { this.listener = listener; }
        private void fire() {
            if (done.compareAndSet(false, true)) {
                try { listener.run(); }
                catch (RuntimeException ignored) { /* One failed cleanup must not prevent the others. */ }
            }
        }
    }
}
