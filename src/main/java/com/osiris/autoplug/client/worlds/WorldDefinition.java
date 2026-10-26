/* MIT licensed; see the repository LICENSE. */
package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.Profile;
import java.nio.file.Path;
import java.util.Objects;

/** Saved world settings; the server installation and save data have their own directory. */
public final class WorldDefinition {
    public final String id, name, profileId, clientProfileId, gameVersion, loader;
    public final Profile.Type profileType;
    public final Path directoryPath, serverExecutable;
    public final boolean acceptedEula;
    WorldDefinition(String id, String name, String profileId, String clientProfileId, String gameVersion,
                    String loader, Profile.Type profileType, Path directoryPath, Path serverExecutable, boolean acceptedEula) {
        validateId(id); validateId(profileId);
        if (clientProfileId != null) validateId(clientProfileId);
        if (name == null || name.trim().isEmpty() || name.length() > 120 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid world name");
        this.id = id; this.name = name; this.profileId = profileId; this.clientProfileId = clientProfileId;
        Profile validated = new Profile(profileId, Objects.requireNonNull(profileType), gameVersion, loader, directoryPath, false);
        if (profileType == Profile.Type.MODS) throw new IllegalArgumentException("Worlds require a server profile");
        this.gameVersion = validated.gameVersion; this.loader = validated.loader;
        this.profileType = profileType; this.directoryPath = directoryPath.toAbsolutePath().normalize();
        this.serverExecutable = serverExecutable == null ? null : serverExecutable.toAbsolutePath().normalize(); this.acceptedEula = acceptedEula;
    }
    static void validateId(String id) { if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")) throw new IllegalArgumentException("Invalid world/profile id"); }
    @Override public String toString() { return id + " (" + name + ") [" + gameVersion + ", " + loader + ", profile " + profileId + "]"; }
}
