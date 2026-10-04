package com.osiris.autoplug.client.clientapp;

import com.osiris.autoplug.client.worlds.*;
import com.osiris.autoplug.client.worlds.network.UpnpPortMapper;
import java.io.IOException;
import java.util.*;

/** A single shared world store/lifecycle owner for the service's GUI and console. */
public final class WorldClientContext {
    private static final Map<NativeClientService, WorldClientContext> instances = new IdentityHashMap<>();
    public final WorldStore worlds;
    public final VirtualWorldService lifecycle;
    private WorldClientContext(NativeClientService service) throws IOException {
        worlds = new WorldStore(service.profiles.getRoot(), service.profiles);
        lifecycle = new VirtualWorldService(worlds, service, new UpnpPortMapper());
    }
    public static synchronized WorldClientContext of(NativeClientService service) throws IOException {
        WorldClientContext context = instances.get(service);
        if (context == null) { context = new WorldClientContext(service); instances.put(service, context); }
        return context;
    }
}
