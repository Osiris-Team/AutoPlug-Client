package com.osiris.autoplug.client.browser;

import java.util.Locale;

/** A Minecraft address, never a URL, command line, or inferred mod-loader hint. */
public final class ServerAddress {
    public final String host;
    public final int port;
    private ServerAddress(String host, int port) { this.host = host; this.port = port; }
    public static ServerAddress parse(String input) {
        if (input == null) throw new IllegalArgumentException("Enter a server address");
        String value = input.trim(), host = value;
        int port = 25565;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 2) throw new IllegalArgumentException("Invalid IPv6 address");
            host = value.substring(1, end);
            String rest = value.substring(end + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) throw new IllegalArgumentException("Invalid address suffix");
                port = parsePort(rest.substring(1));
            }
            if (!host.matches("[0-9a-fA-F:.%A-Za-z0-9_-]+") || !host.contains(":")) throw new IllegalArgumentException("Invalid IPv6 address");
        } else if (value.indexOf(':') != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Use [IPv6]:port for IPv6 addresses");
        } else if (value.contains(":")) {
            int separator = value.indexOf(':');
            host = value.substring(0, separator); port = parsePort(value.substring(separator + 1));
        }
        if (host.isEmpty() || host.length() > 255 || host.startsWith("-") || !host.matches("[\\p{L}\\p{N}._:%-]+")) throw new IllegalArgumentException("Invalid server host");
        return new ServerAddress(host.toLowerCase(Locale.ROOT), port);
    }
    private static int parsePort(String text) {
        try { int value = Integer.parseInt(text); if (value >= 1 && value <= 65535) return value; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Port must be between 1 and 65535");
    }
    @Override public String toString() { return (host.contains(":") ? "[" + host + "]" : host) + (port == 25565 ? "" : ":" + port); }
    @Override public boolean equals(Object other) { return other instanceof ServerAddress && host.equals(((ServerAddress)other).host) && port == ((ServerAddress)other).port; }
    @Override public int hashCode() { return 31 * host.hashCode() + port; }
}
