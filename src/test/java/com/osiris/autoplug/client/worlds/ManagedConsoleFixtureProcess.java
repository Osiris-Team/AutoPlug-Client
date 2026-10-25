package com.osiris.autoplug.client.worlds;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;

/** No server, network or real world data: a real pipe target for instance-routing tests. */
public final class ManagedConsoleFixtureProcess {
    public static void main(String[] args) throws Exception {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                Files.write(Paths.get("commands.txt"), Collections.singletonList(line), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (".stop both".equals(line)) return;
            }
        }
    }
}
