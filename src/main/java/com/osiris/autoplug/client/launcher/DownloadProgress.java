package com.osiris.autoplug.client.launcher;

import com.osiris.jlib.logger.AL;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Download telemetry shared by launcher workers and the existing updater tasks. */
public final class DownloadProgress {
    private static final CopyOnWriteArrayList<Consumer<Event>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    private DownloadProgress() { }

    /** Events can arrive on parallel download workers. Subscribers must marshal UI work to the EDT. */
    public static AutoCloseable subscribe(Consumer<Event> listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static Transfer begin(String description, String url) {
        Transfer transfer = new Transfer(description, url);
        log(transfer.message + " from " + transfer.sourceUrl);
        transfer.emit(0, -1, false);
        return transfer;
    }

    /** Forward URLs printed by official loader subprocesses, which perform their own downloads. */
    public static String installerOutput(String line) {
        String safe = sanitizeUrls(line);
        Matcher urls = URL.matcher(line);
        String lastSource = null;
        while (urls.find()) {
            lastSource = sourceUrl(urls.group());
            log("Loader source: " + lastSource);
            publish(safe, lastSource, -1, -1, false, false, 0);
        }
        if (lastSource == null) publish(safe, null, -1, -1, false, false, 0);
        return safe;
    }

    public static String sanitizeUrls(String text) {
        Matcher urls = URL.matcher(text == null ? "" : text);
        StringBuffer safe = new StringBuffer();
        while (urls.find()) urls.appendReplacement(safe, Matcher.quoteReplacement(sourceUrl(urls.group())));
        urls.appendTail(safe);
        return safe.toString();
    }

    /** Retain useful source paths/query filters without exposing signed links or embedded credentials. */
    public static String sourceUrl(String url) {
        try {
            URI uri = new URI(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawAuthority() == null) return "[invalid source URL]";
            String authority = uri.getRawAuthority();
            int credentials = authority.lastIndexOf('@');
            if (credentials >= 0) authority = "[redacted]@" + authority.substring(credentials + 1);
            StringBuilder safe = new StringBuilder(uri.getScheme()).append("://").append(authority);
            if (uri.getRawPath() != null) safe.append(uri.getRawPath());
            String query = uri.getRawQuery();
            if (query != null) {
                safe.append('?');
                String[] parameters = query.split("&", -1);
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) safe.append('&');
                    String parameter = parameters[i];
                    int equals = parameter.indexOf('=');
                    String name = equals < 0 ? parameter : parameter.substring(0, equals);
                    String key = URLDecoder.decode(name, "UTF-8").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    boolean sensitive = key.contains("token") || key.contains("password") || key.contains("secret")
                            || key.contains("credential") || key.contains("signature") || key.contains("apikey")
                            || key.equals("key") || key.equals("auth") || key.equals("authorization")
                            || key.equals("code") || key.equals("sig") || key.equals("ticket") || key.equals("policy")
                            || key.equals("jwt") || key.equals("session") || key.equals("sessionid") || key.equals("cookie")
                            || key.equals("keypairid");
                    safe.append(sensitive ? name + "=[redacted]" : parameter);
                }
            }
            // Fragments are not sent to the server and can carry OAuth tokens.
            if (uri.getRawFragment() != null) safe.append("#[redacted]");
            return safe.toString();
        } catch (Exception ignored) { return "[invalid source URL]"; }
    }

    private static synchronized void publish(String message, String sourceUrl, long downloadedBytes, long totalBytes,
                                             boolean complete, boolean finished, long transferId) {
        // Snapshot the count at dispatch, not earlier on another worker, so queued events cannot restore stale counts.
        Event event = new Event(message, sourceUrl, downloadedBytes, totalBytes, complete, finished, transferId);
        for (Consumer<Event> listener : LISTENERS) {
            try { listener.accept(event); }
            catch (RuntimeException ignored) { /* A closed view must not fail a download. */ }
        }
    }

    private static void log(String message) {
        // Downloads are also used before Main starts the application logger (and by standalone tools).
        synchronized (AL.class) {
            if (AL.isStarted && AL.STRIPPED_OUT != null) AL.info(message);
            else System.out.println(message);
        }
    }

    public static final class Event {
        public final String message, sourceUrl;
        public final long downloadedBytes, totalBytes;
        /** Stable per-transfer ID; zero denotes installer-owned work with no byte counters. */
        public final long transferId;
        public final int activeTransfers;
        public final boolean complete, finished;
        private Event(String message, String sourceUrl, long downloadedBytes, long totalBytes, boolean complete, boolean finished, long transferId) {
            this.message = message; this.sourceUrl = sourceUrl; this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes; this.complete = complete; this.finished = finished;
            this.transferId = transferId; this.activeTransfers = ACTIVE.get();
        }
    }

    public static final class Transfer implements AutoCloseable {
        private final String message, sourceUrl;
        private final long id = NEXT_ID.incrementAndGet();
        private final AtomicBoolean finished = new AtomicBoolean();
        private long lastUpdate;
        private Transfer(String message, String url) {
            this.message = sanitizeUrls(message); this.sourceUrl = sourceUrl(url);
            ACTIVE.incrementAndGet();
        }
        public void update(long downloaded, long total) {
            long now = System.nanoTime();
            if (now - lastUpdate >= 150_000_000L) { lastUpdate = now; emit(downloaded, total, false); }
        }
        public void complete(long downloaded, long total) {
            if (finished.compareAndSet(false, true)) { ACTIVE.decrementAndGet(); emit(downloaded, total, true); }
        }
        @Override public void close() {
            if (finished.compareAndSet(false, true)) { ACTIVE.decrementAndGet(); emit(-1, -1, false); }
        }
        public void redirect(String url) {
            String redirected = sourceUrl(url);
            if (!redirected.equals(sourceUrl)) {
                log("Download redirected to " + redirected);
                publish("Download redirected", redirected, 0, -1, false, false, id);
            }
        }
        private void emit(long downloaded, long total, boolean complete) {
            publish(message, sourceUrl, downloaded, total, complete, finished.get(), id);
        }
    }
}
