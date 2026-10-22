package com.chatapp.server;

import com.chatapp.server.protocol.Packet;
import com.chatapp.server.protocol.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {

    @Test
    void encodeJoinsTypeAndFields() {
        assertEquals("CHAT|12:00|ali|#FFFFFF|hi", Protocol.encode("CHAT", "12:00", "ali", "#FFFFFF", "hi"));
        assertEquals("USERS", Protocol.encode("USERS"));
    }

    @Test
    void escapeRoundTripKeepsLineBreaksBackslashesAndPipes() {
        String original = "line 1\nline 2 | with pipe \\ and backslash\\n literal";
        String escaped = Protocol.escape(original);
        assertFalse(escaped.contains("\n"), "escaped text must fit on one line");
        assertEquals(original, Protocol.unescape(escaped));
    }

    @Test
    void lastFieldKeepsSeparators() {
        Packet packet = Packet.parse("CHAT|09:15|sara|#ABCDEF|a | b | c");
        String[] f = packet.fields(4);
        assertEquals("CHAT", packet.type());
        assertEquals("sara", f[1]);
        assertEquals("a | b | c", f[3]);
    }

    @Test
    void missingFieldsArePaddedWithEmptyStrings() {
        String[] f = Packet.parse("USERLIST|").fields(2);
        assertEquals(2, f.length);
        assertEquals("", f[0]);
        assertEquals("", f[1]);
        assertEquals("", Packet.parse("BYE").payload());
    }

    @Test
    void usernamesAreValidatedAndSanitized() {
        assertTrue(Protocol.isValidUsername("youness_a.1-x"));
        assertFalse(Protocol.isValidUsername("two words"));
        assertFalse(Protocol.isValidUsername("pipe|name"));
        assertFalse(Protocol.isValidUsername(""));
        assertEquals("twowords", Protocol.sanitizeUsername("  two words "));
        assertEquals("abcdefghijklmnopqrst", Protocol.sanitizeUsername("abcdefghijklmnopqrstuvwxyz"));
    }
}
