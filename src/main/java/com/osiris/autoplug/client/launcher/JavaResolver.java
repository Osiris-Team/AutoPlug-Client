package com.osiris.autoplug.client.launcher;
import java.nio.file.Path;
@FunctionalInterface
public interface JavaResolver { Path resolve(int majorVersion) throws Exception; }
