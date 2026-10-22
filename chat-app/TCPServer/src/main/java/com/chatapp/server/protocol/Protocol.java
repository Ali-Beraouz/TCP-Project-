package com.chatapp.server.protocol;

import java.util.regex.Pattern;

/**
 * Line-based text protocol shared by the server and the client.
 *
 * <p>Every frame is a single UTF-8 line: {@code TYPE|field1|field2|...}.
 * Only the last field of a frame may contain free text; it is split with a limit,
 * so a {@code |} inside a chat message is safe. Line breaks inside a message are
 * escaped as {@code \n} so a message always fits on one line.</p>
 *
 * <pre>
 * Client -> Server                      Server -> Client
 * JOIN|&lt;username or empty&gt;            WELCOME|&lt;name&gt;|FULL or READONLY|&lt;#color&gt;
 * MSG|&lt;text&gt;                          CHAT|&lt;HH:mm&gt;|&lt;sender&gt;|&lt;#color&gt;|&lt;text&gt;
 * USERS                                 USERLIST|&lt;name1,name2,...&gt;|&lt;readOnlyCount&gt;
 * BYE                                   INFO|&lt;text&gt;
 *                                       ERROR|&lt;text&gt;
 * </pre>
 */
public final class Protocol {

    public static final char SEPARATOR = '|';

    // Client -> Server
    public static final String JOIN = "JOIN";
    public static final String MSG = "MSG";
    public static final String USERS = "USERS";
    public static final String BYE = "BYE";

    // Server -> Client
    public static final String WELCOME = "WELCOME";
    public static final String CHAT = "CHAT";
    public static final String USER_LIST = "USERLIST";
    public static final String INFO = "INFO";
    public static final String ERROR = "ERROR";

    public static final String MODE_FULL = "FULL";
    public static final String MODE_READ_ONLY = "READONLY";

    /** Commands a user can type in the message box. */
    public static final String CMD_ALL_USERS = "allUsers";
    public static final String CMD_END = "end";
    public static final String CMD_BYE = "bye";

    public static final int MAX_USERNAME_LENGTH = 20;
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1," + MAX_USERNAME_LENGTH + "}");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^A-Za-z0-9_.-]");

    private Protocol() {
    }

    /** Builds a frame: {@code encode("CHAT", "12:00", "ali", "#fff", "hi")} -> {@code CHAT|12:00|ali|#fff|hi}. */
    public static String encode(String type, String... fields) {
        StringBuilder sb = new StringBuilder(type);
        for (String field : fields) {
            sb.append(SEPARATOR).append(field == null ? "" : field);
        }
        return sb.toString();
    }

    /** Escapes a free-text field so it fits on one protocol line. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
    }

    /** Reverses {@link #escape(String)}. */
    public static String unescape(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                sb.append(next == 'n' ? '\n' : next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isValidUsername(String name) {
        return name != null && VALID_USERNAME.matcher(name).matches();
    }

    /** Removes characters that would break the protocol (|, commas, spaces...) and trims to max length. */
    public static String sanitizeUsername(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = INVALID_CHARS.matcher(name.trim()).replaceAll("");
        return cleaned.length() > MAX_USERNAME_LENGTH ? cleaned.substring(0, MAX_USERNAME_LENGTH) : cleaned;
    }
}
