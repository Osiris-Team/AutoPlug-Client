package com.osiris.autoplug.client.profiles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.osiris.autoplug.client.launcher.*;
import com.osiris.autoplug.client.ui.LauncherActions.SettingsInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** No network or real credentials: production service state transitions with a latched fake provider. */
class LauncherAuthenticationRaceTest {
    @TempDir Path temporary;
    static class Attempt {
        final CountDownLatch beginEntered = new CountDownLatch(1), beginRelease = new CountDownLatch(1);
        final CountDownLatch polling = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        volatile Throwable error;
        Attempt(boolean delayBegin) { if (!delayBegin) beginRelease.countDown(); }
    }
    static class FakeAuth extends MicrosoftAccountService {
        final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
        @Override public DeviceLogin beginLogin(String clientId) {
            Attempt attempt = attempts.get(Thread.currentThread().getName());
            attempt.beginEntered.countDown(); awaitUninterruptibly(attempt.beginRelease);
            try {
                Constructor<DeviceLogin> constructor = DeviceLogin.class.getDeclaredConstructor(String.class, JsonObject.class);
                constructor.setAccessible(true);
                return constructor.newInstance("fixture-app-id", JsonParser.parseString("{\"user_code\":\"FAKE-CODE\",\"device_code\":\"fixture-only\",\"verification_uri\":\"https://example.invalid/fixture\",\"expires_in\":120,\"interval\":1}").getAsJsonObject());
            } catch (Exception e) { throw new AssertionError(e); }
        }
        @Override public MinecraftAccount completeLogin(DeviceLogin login) throws InterruptedException {
            String name = Thread.currentThread().getName(); Attempt attempt = attempts.get(name);
            attempt.polling.countDown(); awaitUninterruptibly(attempt.release);
            // Deliberately emulate an in-flight request which finishes despite cancellation.
            return new MinecraftAccount(name, name.equals("LoginA") ? "00000000000000000000000000000001" : "00000000000000000000000000000002",
                    "fixture-access", "fixture-refresh", "fixture-app-id", "", false, Instant.MAX);
        }
    }
    static void awaitUninterruptibly(CountDownLatch latch) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (latch.getCount() > 0) try {
            if (System.nanoTime() > deadline) throw new AssertionError("Fixture release timed out");
            latch.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { }
    }
    static void await(CountDownLatch latch) throws Exception { assertTrue(latch.await(5, TimeUnit.SECONDS), "Fixture milestone timed out"); }
    LauncherServices service(FakeAuth fake) throws Exception {
        LauncherServices service = new LauncherServices(temporary.resolve("app"), temporary.resolve("minecraft"), ignored -> {}, fake);
        SettingsInfo settings = service.settings(); settings.rememberAccount = true; settings.microsoftClientId = "fixture-app-id"; service.saveSettings(settings);
        return service;
    }
    Attempt start(LauncherServices service, FakeAuth fake, String name, boolean delayBegin, Consumer<String> instructions) {
        Attempt attempt = new Attempt(delayBegin); fake.attempts.put(name, attempt);
        new Thread(() -> {
            try { service.signInMicrosoft(instructions); } catch (Throwable e) { attempt.error = e; }
            finally { attempt.done.countDown(); }
        }, name).start();
        return attempt;
    }
    void selected(LauncherServices service, String label, String uuid, int remembered) throws Exception {
        assertEquals(label, service.settings().account);
        JsonObject persisted = JsonParser.parseString(new String(Files.readAllBytes(temporary.resolve("app/settings.json")), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(uuid, persisted.get("selectedAccount").getAsString(), "Disk choice must also stay current");
        assertEquals(remembered, new AccountStore(temporary.resolve("app/accounts.json")).list().size(), "Obsolete tokens must not be persisted");
    }
    @Test void offlineChoiceSurvivesLateLogin() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", false, ignored -> {});
            try { await(a.polling); service.useOfflineAccount("LocalPlayer"); a.release.countDown(); await(a.done);
                selected(service, "LocalPlayer (offline)", "", 0); assertInstanceOf(CancellationException.class, a.error);
            } finally { a.beginRelease.countDown(); a.release.countDown(); await(a.done); }
        }
    }
    @Test void latestLoginSurvivesReverseCompletion() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", false, ignored -> {}), b = null;
            try { await(a.polling); b = start(service, fake, "LoginB", false, ignored -> {}); await(b.polling);
                b.release.countDown(); await(b.done); assertNull(b.error); a.release.countDown(); await(a.done);
                selected(service, "LoginB (Microsoft)", "00000000000000000000000000000002", 1);
                assertInstanceOf(CancellationException.class, a.error);
            } finally { a.release.countDown(); if (b != null) b.release.countDown(); await(a.done); if (b != null) await(b.done); }
        }
    }
    @Test void dialogCancellationSurvivesLateLogin() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", false, ignored -> {});
            try { await(a.polling); service.cancelMicrosoftSignIn(); a.release.countDown(); await(a.done); selected(service, "Player (offline)", "", 0); }
            finally { a.release.countDown(); await(a.done); }
        }
    }
    @Test void serviceCloseSurvivesLateLogin() throws Exception {
        FakeAuth fake = new FakeAuth(); LauncherServices service = service(fake);
        Attempt a = start(service, fake, "LoginA", false, ignored -> {});
        try { await(a.polling); service.close(); a.release.countDown(); await(a.done); selected(service, "Player (offline)", "", 0); }
        finally { a.release.countDown(); await(a.done); service.close(); }
    }
    @Test void lateDeviceInstructionsAreSuppressedAfterOfflineChoice() throws Exception {
        FakeAuth fake = new FakeAuth(); AtomicInteger instructions = new AtomicInteger(); try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", true, ignored -> instructions.incrementAndGet());
            try { await(a.beginEntered); service.useOfflineAccount("LocalPlayer"); a.beginRelease.countDown(); a.release.countDown(); await(a.done);
                assertEquals(0, instructions.get()); selected(service, "LocalPlayer (offline)", "", 0);
            } finally { a.beginRelease.countDown(); a.release.countDown(); await(a.done); }
        }
    }
    @Test void queuedLoginCannotStartAfterOfflineClick() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            long attempt = service.beginAccountChange(); SettingsInfo form = service.settings();
            service.useOfflineAccount("LocalPlayer");
            Attempt queued = new Attempt(false); queued.release.countDown(); fake.attempts.put(Thread.currentThread().getName(), queued);
            assertThrows(CancellationException.class, () -> service.signInMicrosoft(attempt, form, ignored -> {}));
            selected(service, "LocalPlayer (offline)", "", 0);
        }
    }
    @Test void queuedOfflineChoiceCannotCancelNewerLogin() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            long offlineAttempt = service.beginAccountChange();
            Attempt b = start(service, fake, "LoginB", false, ignored -> {});
            try { await(b.polling);
                assertThrows(CancellationException.class, () -> service.useOfflineAccount(offlineAttempt, "LocalPlayer"));
                b.release.countDown(); await(b.done); assertNull(b.error);
                selected(service, "LoginB (Microsoft)", "00000000000000000000000000000002", 1);
            } finally { b.release.countDown(); await(b.done); }
        }
    }
    @Test void cancellationInterruptsPollingWorker() throws Exception {
        FakeAuth fake = new FakeAuth() {
            @Override public MinecraftAccount completeLogin(DeviceLogin login) throws InterruptedException {
                Attempt attempt = attempts.get(Thread.currentThread().getName());
                attempt.polling.countDown(); attempt.release.await(); return super.completeLogin(login);
            }
        };
        try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", false, ignored -> {});
            try { await(a.polling); service.cancelMicrosoftSignIn();
                assertTrue(a.done.await(2, TimeUnit.SECONDS), "Cancellation must interrupt polling without waiting for a response");
                assertInstanceOf(CancellationException.class, a.error); selected(service, "Player (offline)", "", 0);
            } finally { a.release.countDown(); await(a.done); }
        }
    }
    @Test void changedAuthPreferencesRejectLateLogin() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", false, ignored -> {});
            try { await(a.polling); SettingsInfo settings = service.settings(); settings.microsoftClientId = "other-fixture-app";
                settings.rememberAccount = false; service.saveSettings(settings); a.release.countDown(); await(a.done);
                selected(service, "Player (offline)", "", 0); assertInstanceOf(CancellationException.class, a.error);
            } finally { a.release.countDown(); await(a.done); }
        }
    }
    @Test void currentLoginStillCommitsAndPersists() throws Exception {
        FakeAuth fake = new FakeAuth(); try (LauncherServices service = service(fake)) {
            Attempt a = start(service, fake, "LoginA", false, ignored -> {});
            try { await(a.polling); a.release.countDown(); await(a.done); assertNull(a.error);
                selected(service, "LoginA (Microsoft)", "00000000000000000000000000000001", 1);
            } finally { a.release.countDown(); await(a.done); }
        }
    }
}
