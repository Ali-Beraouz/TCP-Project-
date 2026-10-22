package com.chatapp.client.model;

/**
 * A chat message as formatted and distributed by the server.
 *
 * @param time   server time, {@code HH:mm}
 * @param sender sender's username
 * @param color  sender's colour assigned by the server ({@code #RRGGBB})
 * @param text   message body (may contain line breaks)
 * @param mine   true when this client sent it
 */
public record ChatMessage(String time, String sender, String color, String text, boolean mine) {
}
