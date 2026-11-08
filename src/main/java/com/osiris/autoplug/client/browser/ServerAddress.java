package com.osiris.autoplug.client.browser;

import java.net.IDN;
import java.util.Locale;

/** Parses Minecraft addresses without resolving DNS or contacting a server. */
public final class ServerAddress {
    public final String host;
    public final int port;
    private ServerAddress(String host, int port) { this.host = host; this.port = port; }

    public static ServerAddress parse(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Enter a server address.");
        String text = value.trim(), host = text;
        int port = 25565;
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end < 2) throw new IllegalArgumentException("Invalid IPv6 address.");
            host = text.substring(1, end);
            if (end + 1 < text.length()) {
                if (text.charAt(end + 1) != ':') throw new IllegalArgumentException("Invalid server address.");
                port = parsePort(text.substring(end + 2));
            }
            if (!host.contains(":")) throw new IllegalArgumentException("Brackets are only used for IPv6 addresses.");
        } else if (text.indexOf(':') == text.lastIndexOf(':') && text.contains(":")) {
            host = text.substring(0, text.indexOf(':'));
            port = parsePort(text.substring(text.indexOf(':') + 1));
        }
        if (host.isEmpty() || host.length() > 253 || host.chars().anyMatch(c -> Character.isWhitespace(c) || c == '/' || c == '\\' || c == '?' || c == '#' || c == '@'))
            throw new IllegalArgumentException("Use a hostname or IP address, optionally followed by :port.");
        if (host.contains(":")) {
            if (!host.matches("[0-9a-fA-F:.%a-zA-Z_-]+")) throw new IllegalArgumentException("Invalid IPv6 address.");
        } else host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
        return new ServerAddress(host.toLowerCase(Locale.ROOT), port);
    }
    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("The port must be between 1 and 65535."); }
    }
    @Override public String toString() { return (host.contains(":") ? "[" + host + "]" : host) + (port == 25565 ? "" : ":" + port); }
}
