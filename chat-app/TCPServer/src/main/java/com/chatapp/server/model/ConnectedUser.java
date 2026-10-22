package com.chatapp.server.model;

import java.time.LocalTime;

/**
 * Immutable snapshot of a client that completed the JOIN handshake.
 *
 * @param name     unique display name (guests get {@code Guest-N})
 * @param color    background color assigned by the server, as {@code #RRGGBB}
 * @param readOnly true when the user connected without a username
 * @param address  remote socket address, e.g. {@code /127.0.0.1:52144}
 * @param joinedAt time the user joined
 */
public record ConnectedUser(String name, String color, boolean readOnly, String address, LocalTime joinedAt) {
}
