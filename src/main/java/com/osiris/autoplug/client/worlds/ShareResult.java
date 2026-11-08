package com.osiris.autoplug.client.worlds;

public final class ShareResult {
    public final String address;
    public final String message;
    public ShareResult(String address, String message) { this.address = address; this.message = message; }
    public boolean isShared() { return address != null; }
    public String getAddress() { return address; }
    public String getMessage() { return message; }
}
