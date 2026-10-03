package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.Profile;

@FunctionalInterface
public interface ClientLauncher {
    Process launch(Profile profile, String host, int port) throws Exception;
}
