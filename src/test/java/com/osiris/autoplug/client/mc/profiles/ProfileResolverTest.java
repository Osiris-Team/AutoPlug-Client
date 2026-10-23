package com.osiris.autoplug.client.mc.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProfileResolverTest {
    @TempDir Path temporaryDirectory;

    @Test void selectsOneExactClientProfileWithoutConsideringServerProfiles() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("server", ProfileType.MODS_SERVER, "1.20.4", "fabric", false);
        repository.create("plugins", ProfileType.PLUGINS, "1.20.4", "fabric", false);
        repository.create("client", ProfileType.MODS, "1.20.4", "fabric", false);
        ProfileResolver.Resolution result = new ProfileResolver(repository).resolve("1.20.4", "FABRIC", null);
        assertEquals(ProfileResolver.State.READY, result.getState());
        assertEquals("client", result.getProfiles().get(0).getId());
    }

    @Test void doesNotGuessALoaderFromOnlyTheVersion() throws Exception {
        ProfileResolver resolver = new ProfileResolver(new ProfileRepository(temporaryDirectory));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("1.20.4", null, null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("1.20.4", " ", null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("", "fabric", null));
    }

    @Test void doesNotMatchAnotherLoaderOrVersion() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("forge", ProfileType.MODS, "1.20.4", "forge", false);
        repository.create("older", ProfileType.MODS, "1.20.2", "fabric", false);
        assertEquals(ProfileResolver.State.CREATE_PROFILE,
                new ProfileResolver(repository).resolve("1.20.4", "fabric", null).getState());
    }

    @Test void asksForAChoiceInsteadOfPickingArbitraryModCollections() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("zeta", ProfileType.MODS, "1.20.4", "fabric", false);
        repository.create("alpha", ProfileType.MODS, "1.20.4", "fabric", false);
        ProfileResolver.Resolution result = new ProfileResolver(repository).resolve("1.20.4", "fabric", null);
        assertEquals(ProfileResolver.State.CHOOSE_PROFILE, result.getState());
        assertEquals(2, result.getProfiles().size());
        assertEquals("alpha", result.getProfiles().get(0).getId());
        assertThrows(UnsupportedOperationException.class, () -> result.getProfiles().clear());
    }

    @Test void neverLaunchesATemplateEvenWhenItsVersionMatches() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("base", ProfileType.MODS, "1.20.4", "fabric", true);
        assertEquals(ProfileResolver.State.CONFIRM_TEMPLATE_CLONE,
                new ProfileResolver(repository).resolve("1.20.4", "fabric", "base").getState());
    }

    @Test void proposingAMigrationDoesNotCloneOrChangeSourceMetadata() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile template = repository.create("base", ProfileType.MODS, "1.20.2", "fabric", true);
        byte[] before = Files.readAllBytes(template.getDirectory().resolve("profile.properties"));
        ProfileResolver.Resolution result = new ProfileResolver(repository).resolve("1.20.4", "fabric", "base");
        assertEquals(ProfileResolver.State.CONFIRM_TEMPLATE_CLONE, result.getState());
        assertEquals("1.20.4", result.getTargetGameVersion());
        assertEquals("1.20.2", result.getProfiles().get(0).getGameVersion());
        assertArrayEquals(before, Files.readAllBytes(template.getDirectory().resolve("profile.properties")));
        assertEquals(1, repository.list().size());
    }

    @Test void rejectsAMissingOrIncompatibleDefaultTemplate() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("ordinary", ProfileType.MODS, "1.20.2", "fabric", false);
        repository.create("wrongloader", ProfileType.MODS, "1.20.2", "forge", true);
        repository.create("server", ProfileType.MODS_SERVER, "1.20.2", "fabric", true);
        ProfileResolver resolver = new ProfileResolver(repository);
        assertThrows(IOException.class, () -> resolver.resolve("1.20.4", "fabric", "missing"));
        assertThrows(IOException.class, () -> resolver.resolve("1.20.4", "fabric", "ordinary"));
        assertThrows(IOException.class, () -> resolver.resolve("1.20.4", "fabric", "wrongloader"));
        assertThrows(IOException.class, () -> resolver.resolve("1.20.4", "fabric", "server"));
    }

    @Test void anExactProfileDoesNotNeedTheFallbackTemplate() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        repository.create("client", ProfileType.MODS, "1.20.4", "fabric", false);
        assertEquals(ProfileResolver.State.READY,
                new ProfileResolver(repository).resolve("1.20.4", "fabric", "unused-missing").getState());
    }
}
