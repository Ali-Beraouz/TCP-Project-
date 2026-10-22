package com.chatapp.server.model;

import com.chatapp.server.model.LogEntry.Level;
import com.chatapp.server.protocol.Packet;
import com.chatapp.server.protocol.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serves one connected client on its own virtual thread.
 *
 * <p>Reading happens only on this handler's thread, but writing can happen from any thread
 * (every broadcast writes to every client), so writes are guarded by a lock. A
 * {@link ReentrantLock} is used instead of {@code synchronized} because, on JDK 21, blocking
 * I/O inside a {@code synchronized} block pins the virtual thread to its carrier thread.</p>
 */
final class ClientHandler implements Runnable {

    /** A client must send JOIN within this time, or it is dropped. */
    private static final int JOIN_TIMEOUT_MS = 60_000;

    private final ChatServer server;
    private final Socket socket;
    private final String remoteAddress;
    private final BufferedWriter out;
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile ConnectedUser user;
    private volatile boolean leftByChoice;
    private volatile boolean closed;

    ClientHandler(ChatServer server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(JOIN_TIMEOUT_MS);
            Packet hello = Packet.parse(in.readLine());
            if (!hello.is(Protocol.JOIN)) {
                send(Protocol.encode(Protocol.ERROR, "Protocol error: the first line must be JOIN|<username>"));
                return;
            }
            socket.setSoTimeout(0);
            server.join(this, Protocol.unescape(hello.payload()));

            String line;
            while ((line = in.readLine()) != null) {
                Packet packet = Packet.parse(line);
                if (packet.is(Protocol.BYE)) {
                    leftByChoice = true;
                    break;
                } else if (packet.is(Protocol.MSG)) {
                    server.handleMessage(this, Protocol.unescape(packet.payload()));
                } else if (packet.is(Protocol.USERS)) {
                    server.sendUserList(this);
                } else if (!packet.type().isEmpty()) {
                    send(Protocol.encode(Protocol.ERROR, "Unknown command: " + packet.type()));
                }
            }
        } catch (SocketTimeoutException e) {
            server.log(Level.WARNING, remoteAddress + " did not send a username in time");
        } catch (IOException e) {
            // connection reset or closed by stop() / requestClose() - handled below
        } finally {
            close();
            server.disconnected(this, leftByChoice);
        }
    }

    /** Sends one protocol line. Safe to call from any thread. */
    void send(String line) {
        if (closed) {
            return;
        }
        writeLock.lock();
        try {
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            close(); // the reader thread will notice and clean up
        } finally {
            writeLock.unlock();
        }
    }

    /** The user typed "end" / "bye" as a message. */
    void requestClose() {
        leftByChoice = true;
        close();
    }

    void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    ConnectedUser user() {
        return user;
    }

    /**
     * Publishes the user and sends the WELCOME line while holding the write lock. A broadcast
     * that sees the user waits for the lock, so it is always delivered after WELCOME.
     */
    void welcome(ConnectedUser joinedUser, String welcomeLine) {
        writeLock.lock();
        try {
            this.user = joinedUser;
            send(welcomeLine);
        } finally {
            writeLock.unlock();
        }
    }

    String remoteAddress() {
        return remoteAddress;
    }
}
