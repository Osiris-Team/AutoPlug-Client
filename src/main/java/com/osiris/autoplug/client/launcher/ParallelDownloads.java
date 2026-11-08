package com.osiris.autoplug.client.launcher;

import okhttp3.Call;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded work queue shared by libraries, native archives, client jars and assets. */
final class ParallelDownloads {
    static final int WORKERS = 8;
    private static final ThreadLocal<Cancellation> CURRENT = new ThreadLocal<>();

    private ParallelDownloads() { }

    static Consumer<String> serialized(Consumer<String> progress) {
        if (progress == null) return value -> { };
        return value -> { synchronized (progress) { progress.accept(value); } };
    }

    static void run(String description, List<? extends Callable<Void>> tasks, Consumer<String> progress) throws IOException {
        Consumer<String> log = serialized(progress);
        log.accept(description + ": 0/" + tasks.size());
        if (tasks.isEmpty()) return;
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Minecraft preparation cancelled.");
        Cancellation cancellation = new Cancellation();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(WORKERS, tasks.size()), task -> {
            Thread thread = new Thread(task, "Minecraft file download");
            thread.setDaemon(true);
            return thread;
        });
        CompletionService<Void> completion = new ExecutorCompletionService<>(executor);
        List<Future<Void>> pending = new ArrayList<>(WORKERS);
        int submitted = 0;
        try {
            // Never enqueue the entire asset index: keep only one task per worker outstanding.
            for (; submitted < Math.min(WORKERS, tasks.size()); submitted++)
                pending.add(submit(completion, cancellation, tasks.get(submitted)));
            for (int done = 1; done <= tasks.size(); done++) {
                Future<Void> finished = completion.take();
                pending.remove(finished);
                finished.get();
                log.accept(description + ": " + done + "/" + tasks.size());
                if (submitted < tasks.size()) pending.add(submit(completion, cancellation, tasks.get(submitted++)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Minecraft preparation cancelled.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            if (e.getCause() instanceof Error) throw (Error) e.getCause();
            throw new IOException(description + " preparation failed.", e.getCause());
        } finally {
            // Interrupting a Future alone does not interrupt OkHttp's blocking socket read.
            cancellation.cancel();
            for (Future<Void> future : pending) future.cancel(true);
            executor.shutdownNow();
            boolean interrupted = Thread.interrupted();
            try { executor.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { interrupted = true; }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }

    private static Future<Void> submit(CompletionService<Void> completion, Cancellation cancellation, Callable<Void> task) {
        return completion.submit(() -> {
            CURRENT.set(cancellation);
            try {
                if (cancellation.cancelled.get()) throw new InterruptedIOException("Download cancelled.");
                return task.call();
            } finally { CURRENT.remove(); }
        });
    }

    static TrackedCall track(Call call) { return new TrackedCall(call, CURRENT.get()); }

    static final class TrackedCall implements AutoCloseable {
        private final Call call;
        private final Cancellation cancellation;
        private TrackedCall(Call call, Cancellation cancellation) {
            this.call = call;
            this.cancellation = cancellation;
            if (cancellation != null) {
                cancellation.calls.add(call);
                if (cancellation.cancelled.get()) call.cancel();
            }
        }
        @Override public void close() { if (cancellation != null) cancellation.calls.remove(call); }
    }

    private static final class Cancellation {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final Set<Call> calls = ConcurrentHashMap.newKeySet();
        void cancel() {
            cancelled.set(true);
            for (Call call : calls) call.cancel();
        }
    }
}
