package com.chatapp.server.model;

import java.time.LocalTime;

/**
 * One line of the server activity log.
 *
 * @param time    when the event happened
 * @param level   category, used by views for colouring
 * @param message human-readable text
 */
public record LogEntry(LocalTime time, Level level, String message) {

    public enum Level { SYSTEM, JOIN, LEAVE, CHAT, WARNING, ERROR }

    public static LogEntry of(Level level, String message) {
        return new LogEntry(LocalTime.now().withNano(0), level, message);
    }
}
