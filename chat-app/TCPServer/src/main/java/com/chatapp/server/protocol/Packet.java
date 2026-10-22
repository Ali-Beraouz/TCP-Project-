package com.chatapp.server.protocol;

import java.util.Arrays;

/**
 * A decoded protocol line: its type and the raw payload after the first separator.
 *
 * @param type    frame type, e.g. {@code MSG}
 * @param payload everything after the first {@code |} (may be empty)
 */
public record Packet(String type, String payload) {

    public static Packet parse(String line) {
        if (line == null) {
            return new Packet("", "");
        }
        int sep = line.indexOf(Protocol.SEPARATOR);
        return sep < 0
                ? new Packet(line.trim(), "")
                : new Packet(line.substring(0, sep).trim(), line.substring(sep + 1));
    }

    /**
     * Splits the payload into exactly {@code count} fields. The last field keeps any extra
     * separators (so free text is preserved); missing fields come back as empty strings.
     */
    public String[] fields(int count) {
        String[] parts = payload.split("\\|", count);
        if (parts.length == count) {
            return parts;
        }
        String[] padded = Arrays.copyOf(parts, count);
        Arrays.fill(padded, parts.length, count, "");
        return padded;
    }

    public boolean is(String expectedType) {
        return type.equalsIgnoreCase(expectedType);
    }
}
