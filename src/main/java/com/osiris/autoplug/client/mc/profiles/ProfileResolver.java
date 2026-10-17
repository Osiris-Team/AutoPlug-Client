package com.osiris.autoplug.client.mc.profiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Selects client profiles without downloading, cloning or launching anything. */
public final class ProfileResolver {
    private final ProfileRepository repository;

    public ProfileResolver(ProfileRepository repository) {
        if (repository == null) throw new IllegalArgumentException("A profile repository is required");
        this.repository = repository;
    }

    /**
     * READY means an exact metadata match, not verified launch or mod compatibility.
     * Callers must still complete asset validation and the launch preparation pipeline.
     * A server ping supplies a game version, but cannot identify the required mod loader.
     * The caller must therefore provide its explicitly selected loader. A template is
     * only a proposal: cloning, migration and user confirmation remain separate steps.
     */
    public Resolution resolve(String gameVersion, String loader, String defaultTemplateId) throws IOException {
        if (gameVersion == null || gameVersion.trim().isEmpty()
                || loader == null || loader.trim().isEmpty()) {
            throw new IllegalArgumentException("An explicit Minecraft version and loader are required");
        }
        List<Profile> matches = new ArrayList<>();
        for (Profile profile : repository.list()) {
            if (profile.getType() == ProfileType.MODS && !profile.isTemplate()
                    && profile.getGameVersion().equals(gameVersion)
                    && profile.getLoader().equalsIgnoreCase(loader)) {
                matches.add(profile);
            }
        }
        if (!matches.isEmpty()) {
            return new Resolution(matches.size() == 1 ? State.READY : State.CHOOSE_PROFILE,
                    matches, gameVersion);
        }
        if (defaultTemplateId == null) {
            return new Resolution(State.CREATE_PROFILE, Collections.emptyList(), gameVersion);
        }
        Profile template = repository.find(defaultTemplateId)
                .orElseThrow(() -> new IOException("Unknown template: " + defaultTemplateId));
        if (!template.isTemplate() || template.getType() != ProfileType.MODS
                || !template.getLoader().equalsIgnoreCase(loader)) {
            throw new IOException("The default template must be a client-mod template for the selected loader");
        }
        return new Resolution(State.CONFIRM_TEMPLATE_CLONE, Collections.singletonList(template), gameVersion);
    }

    public enum State {
        READY, CHOOSE_PROFILE, CONFIRM_TEMPLATE_CLONE, CREATE_PROFILE
    }

    public final class Resolution {
        private final State state;
        private final List<Profile> profiles;
        private final String targetGameVersion;

        private Resolution(State state, List<Profile> profiles, String targetGameVersion) {
            this.state = state;
            this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
            this.targetGameVersion = targetGameVersion;
        }

        public State getState() { return state; }
        public List<Profile> getProfiles() { return profiles; }
        public String getTargetGameVersion() { return targetGameVersion; }
    }
}
