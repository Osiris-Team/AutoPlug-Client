package com.osiris.autoplug.client.mc.profiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProfileManagementTest {
    @TempDir Path temporary;

    @Test void templateRenameAndDeleteOperateOnlyOnTheSelectedProfile() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary);
        Profile profile = repository.create("one", ProfileType.MODS, "1.21", "fabric", false);
        Profile other = repository.create("two", ProfileType.PLUGINS, "1.20", "paper", false);
        Path config = profile.getConfigDirectory().resolve("options.txt");
        Files.write(config, "independent".getBytes(StandardCharsets.UTF_8));
        assertTrue(repository.updateTemplate("one", true).isTemplate());
        Profile renamed = repository.rename("one", "template");
        assertFalse(repository.find("one").isPresent());
        assertTrue(renamed.isTemplate());
        assertEquals("independent", new String(Files.readAllBytes(renamed.getConfigDirectory().resolve("options.txt")), StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> repository.rename("template", "two"));
        repository.delete("template");
        assertFalse(repository.find("template").isPresent());
        assertTrue(Files.isDirectory(other.getDirectory()));
    }

    @Test void useLeaseBlocksEveryMutatingOperationAndClosesIdempotently() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary);
        repository.create("one", ProfileType.MODS, "1.21", "fabric", false);
        ProfileRepository.UseLease lease = repository.acquireUse("one");
        assertTrue(repository.isInUse("one"));
        assertThrows(IOException.class, () -> repository.acquireUse("one"));
        assertThrows(IOException.class, () -> repository.updateTemplate("one", true));
        assertThrows(IOException.class, () -> repository.recordMigratedVersion("one", "1.20.1"));
        assertThrows(IOException.class, () -> repository.cloneProfile("one", "copy"));
        assertThrows(IOException.class, () -> repository.rename("one", "renamed"));
        assertThrows(IOException.class, () -> repository.delete("one"));
        lease.close(); lease.close();
        assertFalse(repository.isInUse("one"));
        try (ProfileRepository.UseLease next = repository.acquireUse("one")) { assertTrue(repository.isInUse("one")); }
        repository.delete("one");
        assertTrue(repository.list().isEmpty());
    }

    @Test void deleteDoesNotFollowLinksInsideAProfile() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.resolve("data"));
        Profile p = repository.create("one", ProfileType.MODS, "1.21", "fabric", false);
        Path outside = temporary.resolve("keep.txt");
        Files.write(outside, new byte[]{7});
        Files.createSymbolicLink(p.getConfigDirectory().resolve("external.txt"), outside);
        repository.delete("one");
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(outside));
    }
}
