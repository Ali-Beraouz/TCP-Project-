package com.chatapp.server.model;

import com.chatapp.server.config.ServerConfig;
import com.chatapp.server.model.LogEntry.Level;
import com.chatapp.server.protocol.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The chat server model: accepts TCP connections, runs one virtual thread per client,
 * and relays every message to all active connections.
 *
 * <p>Contains no UI code. Everything observable is reported through a {@link ServerListener},
 * which lets the same class drive the JavaFX window, the console mode and the unit tests.</p>
 */
public class ChatServer {

    public static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ServerConfig config;
    private final ServerListener listener;
    private final ColorPalette palette = new ColorPalette();
    private final AtomicInteger guestCounter = new AtomicInteger();

    /** Joined users, keyed by lower-case name (names are unique, case-insensitively). */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    /** Every open socket, including clients that have not sent JOIN yet. */
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService workers;
    private volatile boolean running;

    public ChatServer(ServerConfig config, ServerListener listener) {
        this.config = Objects.requireNonNull(config);
        this.listener = Objects.requireNonNull(listener);
    }

    // ---------------------------------------------------------------- lifecycle

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(config.host(), config.port()));
        serverSocket = socket;

        // Virtual threads: one cheap thread per connected client (JDK 21+).
        workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("client-", 1).factory());
        running = true;

        log(Level.SYSTEM, "Server Started on " + config.host() + ":" + getPort() + "  (config: " + config.source() + ")");
        listener.onServerStarted(config.host(), getPort());
        Thread.ofVirtual().name("acceptor").start(this::acceptLoop);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        broadcast(Protocol.encode(Protocol.INFO, "Server is shutting down. Goodbye!"));
        closeQuietly(serverSocket);
        connections.forEach(ClientHandler::close);

        workers.shutdown();
        try {
            if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Anyone not cleaned up by their own handler is removed here.
        clients.forEach((key, handler) -> {
            if (clients.remove(key, handler) && handler.user() != null) {
                listener.onUserLeft(handler.user());
            }
        });
        connections.clear();

        log(Level.SYSTEM, "Server Stopped");
        listener.onServerStopped();
    }

    public boolean isRunning() {
        return running;
    }

    /** Actual listening port (useful when the configured port is 0 = "any free port"). */
    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? config.port() : socket.getLocalPort();
    }

    public ServerConfig getConfig() {
        return config;
    }

    /** Joined users ordered by join time. */
    public List<ConnectedUser> getActiveUsers() {
        return clients.values().stream()
                .map(ClientHandler::user)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ConnectedUser::joinedAt).thenComparing(ConnectedUser::name))
                .toList();
    }

    private void acceptLoop() {
        while (running) {
            log(Level.SYSTEM, "Waiting for Client...");
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(this, socket);
                connections.add(handler);
                log(Level.SYSTEM, "Client connected from " + handler.remoteAddress());
                workers.execute(handler);
            } catch (IOException e) {
                if (running) {
                    log(Level.ERROR, "Could not accept a connection: " + e.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------- called by ClientHandler

    /** Registers a client after its JOIN line. An empty username means READ-ONLY mode. */
    ConnectedUser join(ClientHandler handler, String requestedName) {
        String raw = requestedName == null ? "" : requestedName.strip();
        boolean readOnly = raw.isEmpty();
        String name = readOnly ? reserveGuestName(handler) : reserveName(handler, raw);

        ConnectedUser user = new ConnectedUser(name, palette.next(), readOnly,
                handler.remoteAddress(), LocalTime.now().withNano(0));
        // Marks the handler as joined (so it receives broadcasts) and sends WELCOME atomically:
        // no message can be missed or arrive before the WELCOME line.
        handler.welcome(user, Protocol.encode(Protocol.WELCOME, name,
                readOnly ? Protocol.MODE_READ_ONLY : Protocol.MODE_FULL, user.color()));

        listener.onUserJoined(user);
        if (readOnly) {
            log(Level.JOIN, "Welcome " + name + "  (no username: READ-ONLY MODE)");
        } else {
            log(Level.JOIN, "Welcome " + name);
            broadcastExcept(handler, Protocol.encode(Protocol.INFO, name + " joined the chat"));
        }
        return user;
    }

    /** Handles a MSG frame: formats it with sender and time, then sends it to every active connection. */
    void handleMessage(ClientHandler handler, String text) {
        ConnectedUser user = handler.user();
        String message = text == null ? "" : text.strip();
        if (user == null || message.isEmpty()) {
            return;
        }
        // Commands are also accepted as plain messages, so even a raw client (telnet/netcat) can use them.
        if (message.equalsIgnoreCase(Protocol.CMD_ALL_USERS)) {
            sendUserList(handler);
            return;
        }
        if (message.equalsIgnoreCase(Protocol.CMD_END) || message.equalsIgnoreCase(Protocol.CMD_BYE)) {
            handler.requestClose();
            return;
        }
        if (user.readOnly()) {
            handler.send(Protocol.encode(Protocol.ERROR,
                    "READ-ONLY MODE: you joined without a username, so you cannot send messages."));
            log(Level.WARNING, "Blocked a message from " + user.name() + " (read-only)");
            return;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        String time = LocalTime.now().format(TIME);
        broadcast(Protocol.encode(Protocol.CHAT, time, user.name(), user.color(), Protocol.escape(message)));
        log(Level.CHAT, user.name() + ": " + abbreviate(message));
        listener.onMessageRelayed(user, message);
    }

    /** Answers the "allUsers" command to the requesting client only. */
    void sendUserList(ClientHandler handler) {
        List<ConnectedUser> users = getActiveUsers();
        String names = users.stream().filter(u -> !u.readOnly()).map(ConnectedUser::name)
                .collect(Collectors.joining(","));
        long readOnlyCount = users.stream().filter(ConnectedUser::readOnly).count();
        handler.send(Protocol.encode(Protocol.USER_LIST, names, String.valueOf(readOnlyCount)));

        ConnectedUser requester = handler.user();
        log(Level.SYSTEM, (requester == null ? handler.remoteAddress() : requester.name())
                + " requested the list of active users");
    }

    void disconnected(ClientHandler handler, boolean leftByChoice) {
        connections.remove(handler);
        ConnectedUser user = handler.user();
        if (user == null) {
            log(Level.SYSTEM, "Connection from " + handler.remoteAddress() + " closed before joining");
            return;
        }
        if (!clients.remove(key(user.name()), handler)) {
            return; // already cleaned up (e.g. during stop())
        }
        listener.onUserLeft(user);
        log(Level.LEAVE, user.name() + (leftByChoice ? " left the chat" : " disconnected"));
        if (running && !user.readOnly()) {
            broadcast(Protocol.encode(Protocol.INFO, user.name() + " left the chat"));
        }
    }

    void log(Level level, String message) {
        listener.onLog(LogEntry.of(level, message));
    }

    // ---------------------------------------------------------------- helpers

    private void broadcast(String line) {
        for (ClientHandler client : clients.values()) {
            if (client.user() != null) {
                client.send(line);
            }
        }
    }

    private void broadcastExcept(ClientHandler excluded, String line) {
        for (ClientHandler client : clients.values()) {
            if (client != excluded && client.user() != null) {
                client.send(line);
            }
        }
    }

    private String reserveName(ClientHandler handler, String requested) {
        String base = Protocol.sanitizeUsername(requested);
        if (base.isEmpty()) {
            base = "User";
        }
        String candidate = base;
        int suffix = 2;
        // putIfAbsent is atomic, so two clients asking for the same name at once still get different names.
        while (clients.putIfAbsent(key(candidate), handler) != null) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private String reserveGuestName(ClientHandler handler) {
        String candidate;
        do {
            candidate = "Guest-" + guestCounter.incrementAndGet();
        } while (clients.putIfAbsent(key(candidate), handler) != null);
        return candidate;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String abbreviate(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 117) + "...";
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception ignored) {
            // nothing useful to do
        }
    }
}
