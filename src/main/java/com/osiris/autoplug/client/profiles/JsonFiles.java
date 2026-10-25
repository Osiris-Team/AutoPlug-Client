package com.osiris.autoplug.client.profiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Atomic metadata replacement prevents a crash from truncating the previous configuration. */
public class JsonFiles {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public <T> T read(Path path, Class<T> type) throws IOException {
        if (Files.size(path) > 8 * 1024 * 1024) throw new IOException("Metadata exceeds 8 MiB: " + path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T result = gson.fromJson(reader, type);
            if (result == null) throw new IOException("Empty metadata: " + path);
            return result;
        } catch (com.google.gson.JsonParseException e) { throw new IOException("Invalid metadata: " + path, e); }
    }
    public void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), ".metadata-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { gson.toJson(value, writer); }
            try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
