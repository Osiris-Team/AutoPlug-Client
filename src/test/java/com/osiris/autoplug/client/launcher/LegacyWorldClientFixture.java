package com.osiris.autoplug.client.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A forked client contract fixture: no Minecraft install, credentials or save writes. */
public final class LegacyWorldClientFixture implements Executor {
    private static LegacyWorldClientFixture instance;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    public Object screen = new Object();
    private Object overlay = new Object();
    private int opened;
    private int checked;
    private boolean legacyScheduler;
    private int scheduled;

    public static LegacyWorldClientFixture getInstance() { return instance; }
    public Object getOverlay() { return overlay; }
    public void execute(Runnable work) { queue.add(work); }
    public Object schedule(Runnable work) { scheduled++; queue.add(work); return null; }
    public void loadLevel(String world) throws Exception { record(world, "direct"); }
    public Flows createWorldOpenFlows() { return new Flows(this); }
    public Source getLevelSource() { return new Source(); }
    public void selectLevel(String folder, String name, Settings settings) throws Exception {
        if (!"Original display name".equals(name) || settings != null) throw new AssertionError("Save renamed or new settings supplied");
        record(folder, legacyScheduler ? "original-scheduler" : "original-name");
    }
    private void record(String world, String route) throws Exception {
        if (!Thread.currentThread().getName().equals("main")) throw new AssertionError("Wrong thread");
        if (!(screen instanceof TitleScreen) || (!legacyScheduler && (overlay != null || checked < 2))) throw new AssertionError("Startup screen bypassed");
        if (legacyScheduler && scheduled < 2) throw new AssertionError("Native scheduler was bypassed");
        if (++opened != 1) throw new AssertionError("Duplicate world entry");
        Files.write(Paths.get("entered.txt"), (world + "|" + route + "|main").getBytes(StandardCharsets.UTF_8));
    }
    public static class TitleScreen { }
    public static class Settings { }
    public static class Flows {
        private final LegacyWorldClientFixture client;
        public Flows(LegacyWorldClientFixture client) { this.client = client; }
        public void loadLevel(String world) throws Exception { client.record(world, "flow"); }
        public void loadLevel(TitleScreen screen, String world) throws Exception {
            if (screen != client.screen) throw new AssertionError("Native return screen lost");
            client.record(world, "screen-flow");
        }
    }
    public static class Source extends BaseSource {
        public java.util.List<Summary> getLevelList() { return Collections.singletonList(new Summary()); }
    }
    public static class BaseSource {
        public boolean levelExists(String folder) { return "World folder ü".equals(folder); }
    }
    public static class Summary {
        public String getLevelId() { return "World folder ü"; }
        public String getLevelName() { return "Original display name"; }
    }
    public static void main(String[] args) throws Exception {
        instance = new LegacyWorldClientFixture();
        instance.legacyScheduler = args.length > 0 && args[0].equals("original-scheduler");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (instance.opened == 0 && System.nanoTime() < deadline) {
            Runnable task = instance.queue.poll(1, TimeUnit.SECONDS);
            if (task == null) continue;
            task.run();
            instance.checked++;
            if (instance.checked == 1) instance.screen = new TitleScreen();
            if (instance.checked >= 2) instance.overlay = null;
        }
        if (instance.opened != 1) throw new AssertionError("No world entry");
        Runnable unexpected = instance.queue.poll(500, TimeUnit.MILLISECONDS);
        if (unexpected != null) { unexpected.run(); if (instance.opened != 1) throw new AssertionError("Repeated entry"); }
    }
}
