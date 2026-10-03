package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.profiles.Profile;
import java.nio.file.Path;

@FunctionalInterface
public interface ServerInstaller {
    ServerLaunch prepare(Profile profile, Path worldDirectory) throws Exception;
}
