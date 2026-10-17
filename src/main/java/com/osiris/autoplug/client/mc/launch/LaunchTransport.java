package com.osiris.autoplug.client.mc.launch;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/** Injectable network boundary. Fixture tests implement this without network access. */
public interface LaunchTransport {
    JsonObject json(String method, URI uri, Map<String, String> headers, byte[] body,
                    Cancellation cancellation) throws IOException;
    void download(URI uri, Path destination, Cancellation cancellation) throws IOException;
}
