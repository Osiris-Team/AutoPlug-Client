package com.osiris.autoplug.client.worlds;

/** Sharing is acquired only on an explicit request and owned by its world session. */
@FunctionalInterface
public interface SharingService {
    Lease open(int loopbackPort) throws Exception;
    interface Lease extends AutoCloseable {
        String address();
        default boolean isActive() { return true; }
        @Override void close() throws Exception;
    }
}
