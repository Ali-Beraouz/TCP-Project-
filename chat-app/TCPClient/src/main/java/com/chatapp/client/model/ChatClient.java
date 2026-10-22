package com.chatapp.client.model;

import com.chatapp.client.protocol.Packet;
import com.chatapp.client.protocol.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The client model: owns the TCP socket, translates user input into protocol frames and
 * server frames into {@link ClientListener} events. No JavaFX code lives here.
 */
public class ChatClient {

    /** What happened to a line passed to {@link #send(String)}. */
    public enum SendResult { SENT, COMMAND, EMPTY, BLOCKED_READ_ONLY, NOT_CONNECTED }

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final long LEAVE_GRACE_MS = 1_000;

    private final ClientListener listener;
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile Socket socket;
    private volatile BufferedWriter out;
    private volatile boolean connected;
    private volatile boolean leaving;
    private volatile boolean readOnly;
    private volatile String username = "";
    private volatile String color = "#E5E7EB";

    public ChatClient(ClientListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    /**
     * Opens the connection and sends {@code JOIN}. A blank username asks for READ-ONLY mode.
     * Blocks for at most 5 seconds; the server's answer arrives later through
     * {@link ClientListener#onWelcome}.
     */
    public void connect(String host, int port, String requestedUsername) throws IOException {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            socket = s;
            leaving = false;
            connected = true;
            Thread.ofVirtual().name("server-reader").start(() -> readLoop(in));
        } catch (IOException e) {
            s.close();
            throw e;
        }
        String name = requestedUsername == null ? "" : requestedUsername.strip();
        write(Protocol.encode(Protocol.JOIN, Protocol.escape(name)));
    }

    /**
     * Handles one line typed by the user: the commands {@code allUsers}, {@code end} and
     * {@code bye}, or a chat message.
     */
    public SendResult send(String text) {
        String line = text == null ? "" : text.strip();
        if (line.isEmpty()) {
            return SendResult.EMPTY;
        }
        if (!connected) {
            return SendResult.NOT_CONNECTED;
        }
        if (line.equalsIgnoreCase(Protocol.CMD_ALL_USERS)) {
            requestUserList();
            return SendResult.COMMAND;
        }
        if (line.equalsIgnoreCase(Protocol.CMD_END) || line.equalsIgnoreCase(Protocol.CMD_BYE)) {
            leave();
            return SendResult.COMMAND;
        }
        if (readOnly) {
            listener.onError("READ-ONLY MODE: you joined without a username, so you cannot send messages.");
            return SendResult.BLOCKED_READ_ONLY;
        }
        write(Protocol.encode(Protocol.MSG, Protocol.escape(line)));
        return SendResult.SENT;
    }

    /** Asks the server for the active users (same as typing "allUsers"). */
    public void requestUserList() {
        write(Protocol.USERS);
    }

    /**
     * Leaves gracefully: tells the server ({@code BYE}), stops sending, and lets the reader
     * finish when the server closes its side. Falls back to a hard close after one second.
     */
    public void leave() {
        if (!connected || leaving) {
            return;
        }
        leaving = true;
        write(Protocol.BYE);
        Socket s = socket;
        try {
            s.shutdownOutput();
        } catch (IOException ignored) {
            // already closing
        }
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(LEAVE_GRACE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeQuietly(s);
        });
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public String getUsername() {
        return username;
    }

    public String getColor() {
        return color;
    }

    // ---------------------------------------------------------------- internals

    private void readLoop(BufferedReader in) {
        try (in) {
            String line;
            while ((line = in.readLine()) != null) {
                dispatch(Packet.parse(line));
            }
        } catch (IOException e) {
            // socket closed or connection lost
        } finally {
            boolean byChoice = leaving;
            connected = false;
            closeQuietly(socket);
            listener.onDisconnected(byChoice ? "You left the chat." : "Connection to the server was lost.");
        }
    }

    private void dispatch(Packet packet) {
        switch (packet.type()) {
            case Protocol.WELCOME -> {
                String[] f = packet.fields(3);
                username = f[0];
                readOnly = Protocol.MODE_READ_ONLY.equals(f[1]);
                if (!f[2].isBlank()) {
                    color = f[2];
                }
                listener.onWelcome(username, readOnly, color);
            }
            case Protocol.CHAT -> {
                String[] f = packet.fields(4);
                listener.onMessage(new ChatMessage(f[0], f[1], f[2], Protocol.unescape(f[3]),
                        f[1].equalsIgnoreCase(username)));
            }
            case Protocol.USER_LIST -> {
                String[] f = packet.fields(2);
                List<String> users = f[0].isBlank() ? List.of() : Arrays.asList(f[0].split(","));
                int readOnlyCount;
                try {
                    readOnlyCount = Integer.parseInt(f[1].trim());
                } catch (NumberFormatException e) {
                    readOnlyCount = 0;
                }
                listener.onUserList(users, readOnlyCount);
            }
            case Protocol.INFO -> listener.onInfo(Protocol.unescape(packet.payload()));
            case Protocol.ERROR -> listener.onError(Protocol.unescape(packet.payload()));
            default -> {
                // unknown frame type: ignore (forward compatibility)
            }
        }
    }

    private void write(String line) {
        BufferedWriter writer = out;
        if (writer == null) {
            return;
        }
        writeLock.lock();
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            closeQuietly(socket); // the reader thread reports the disconnection
        } finally {
            writeLock.unlock();
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }
}
