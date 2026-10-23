package com.osiris.autoplug.client.mc.sharing;

import java.io.IOException;

/** Mapping is requested only after the user explicitly enables sharing. */
public interface PortSharingService {
    Lease share(int localPort) throws IOException;

    interface Lease extends AutoCloseable {
        String getJoinAddress();
        default boolean isExpired() { return false; }
        @Override void close() throws IOException;
    }
}
