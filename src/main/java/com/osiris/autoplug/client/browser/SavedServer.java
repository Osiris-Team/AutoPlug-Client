package com.osiris.autoplug.client.browser;

public final class SavedServer {
    public final String name, address;
    public SavedServer(String name, String address) {
        this.address = ServerAddress.parse(address).toString();
        this.name = name == null || name.trim().isEmpty() ? this.address : name.trim();
    }
    @Override public String toString() { return name + " (" + address + ")"; }
}
