package com.chatapp.server;

import com.chatapp.server.protocol.Packet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A raw protocol client used by the integration tests (no JavaFX, no client project). */
final class TestClient implements AutoCloseable {

    private static final String EOF = "\u0000EOF";

    private final Socket socket;
    private final BufferedWriter out;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    TestClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Thread.ofVirtual().start(() -> {
            try (in) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException ignored) {
                // closed
            }
            lines.add(EOF);
        });
    }

    synchronized void send(String line) throws IOException {
        out.write(line);
        out.newLine();
        out.flush();
    }

    /** Waits for the next frame of the given type, skipping others. */
    Packet next(String type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line == null) {
                continue;
            }
            if (line.equals(EOF)) {
                throw new AssertionError("Connection closed while waiting for " + type);
            }
            Packet packet = Packet.parse(line);
            if (packet.is(type)) {
                return packet;
            }
        }
        throw new AssertionError("Timed out waiting for " + type);
    }

    /** True if no frame of this type arrives within the given time. */
    boolean receivesNo(String type, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(20, TimeUnit.MILLISECONDS);
            if (line != null && !line.equals(EOF) && Packet.parse(line).is(type)) {
                return false;
            }
        }
        return true;
    }

    /** True once the server has closed the connection. */
    boolean waitForClose() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (EOF.equals(line)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
