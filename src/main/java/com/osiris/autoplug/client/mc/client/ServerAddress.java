package com.osiris.autoplug.client.mc.client;

import java.net.IDN;

/** Parses addresses without DNS resolution or a network connection. */
public final class ServerAddress {
    private final String host;
    private final int port;

    public ServerAddress(String address) {
        if (address == null || address.isEmpty() || address.length() > 512 || !address.equals(address.trim())
                || address.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                || address.contains("/") || address.contains("\\") || address.contains("@")) {
            throw new IllegalArgumentException("Use a Minecraft host, host:port or [IPv6]:port");
        }
        String name = address;
        String portText = null;
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 2) throw new IllegalArgumentException("Invalid bracketed IPv6 address");
            name = address.substring(1, end);
            String suffix = address.substring(end + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":")) throw new IllegalArgumentException("Invalid IPv6 port");
                portText = suffix.substring(1);
            }
            if (!name.contains(":")) throw new IllegalArgumentException("Brackets require an IPv6 address");
        } else {
            long colons = address.chars().filter(c -> c == ':').count();
            if (colons == 1) {
                int separator = address.indexOf(':');
                name = address.substring(0, separator);
                portText = address.substring(separator + 1);
            }
        }
        if (name.contains(":")) {
            if (!validIpv6(name)) throw new IllegalArgumentException("Invalid IPv6 literal");
            name = name.toLowerCase(java.util.Locale.ROOT);
        } else {
            name = IDN.toASCII(name);
            if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
            if (name.isEmpty() || name.length() > 253) throw new IllegalArgumentException("Invalid Minecraft host");
            for (String label : name.split("\\.", -1)) {
                if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9_-]{0,61}[A-Za-z0-9])?")) throw new IllegalArgumentException("Invalid Minecraft host");
            }
            if (name.matches("[0-9.]+") && !validIpv4(name)) throw new IllegalArgumentException("Invalid IPv4 literal");
            name = name.toLowerCase(java.util.Locale.ROOT);
        }
        int parsed = 25565;
        if (portText != null) {
            if (!portText.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Invalid Minecraft port");
            parsed = Integer.parseInt(portText);
            if (parsed < 1 || parsed > 65535) throw new IllegalArgumentException("Minecraft port must be between 1 and 65535");
        }
        this.host = name;
        this.port = parsed;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    @Override public String toString() { return (host.contains(":") ? "[" + host + "]" : host) + ":" + port; }

    private static boolean validIpv4(String literal) {
        String[] parts = literal.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255) return false;
        return true;
    }

    private static boolean validIpv6(String literal) {
        if (!literal.matches("[0-9a-fA-F:.]+") || literal.contains(":::")) return false;
        if (literal.startsWith(":") && !literal.startsWith("::") || literal.endsWith(":") && !literal.endsWith("::")) return false;
        int compression = literal.indexOf("::");
        if (compression >= 0 && compression != literal.lastIndexOf("::")) return false;
        if (compression < 0 && (literal.startsWith(":") || literal.endsWith(":"))) return false;
        String[] groups = literal.split(":", -1);
        int count = 0;
        for (int i = 0; i < groups.length; i++) {
            String group = groups[i];
            if (group.isEmpty()) continue;
            if (group.contains(".")) {
                if (i != groups.length - 1 || !validIpv4(group)) return false;
                count += 2;
            } else {
                if (!group.matches("[0-9a-fA-F]{1,4}")) return false;
                count++;
            }
        }
        return compression >= 0 ? count < 8 : count == 8;
    }
}
