package com.osiris.autoplug.client.worlds;

/** A world owns its saves independently of the profiles used to run it. */
public final class VirtualWorld {
    public String id;
    public String name;
    public String serverProfileId;
    public String clientProfileId;
    public String thumbnail;
    public long createdAt;
    public boolean eulaAccepted;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getServerProfileId() { return serverProfileId; }
    public String getClientProfileId() { return clientProfileId; }
    public String getThumbnail() { return thumbnail; }
    public boolean isEulaAccepted() { return eulaAccepted; }
    @Override public String toString() { return name; }
}
