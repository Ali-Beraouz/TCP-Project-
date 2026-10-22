package com.chatapp.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Network settings of the server, loaded at runtime from {@code server.properties}.
 *
 * <p>Lookup order (first match wins), so settings can change without recompiling:</p>
 * <ol>
 *   <li>the file given with {@code -Dserver.config=/path/to/file.properties}</li>
 *   <li>{@code server.properties} in the current working directory (next to the JAR)</li>
 *   <li>the default {@code server.properties} packaged inside the JAR</li>
 * </ol>
 *
 * @param host   bind address ({@code 0.0.0.0} = all network interfaces)
 * @param port   TCP port to listen on
 * @param source human-readable origin of the settings (for the log)
 */
public record ServerConfig(String host, int port, String source) {

    public static final String FILE_NAME = "server.properties";
    public static final String CONFIG_PROPERTY = "server.config";
    public static final String KEY_HOST = "server.host";
    public static final String KEY_PORT = "server.port";
    public static final String DEFAULT_HOST = "0.0.0.0";
    public static final int DEFAULT_PORT = 3000;

    public ServerConfig {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got " + port);
        }
        host = (host == null || host.isBlank()) ? DEFAULT_HOST : host.trim();
    }

    public static ServerConfig load() {
        String explicit = System.getProperty(CONFIG_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return fromFile(Path.of(explicit));
        }
        Path local = Path.of(FILE_NAME);
        if (Files.isRegularFile(local)) {
            return fromFile(local);
        }
        try (InputStream in = ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                return fromProperties(props, "built-in " + FILE_NAME);
            }
        } catch (IOException e) {
            // fall through to defaults
        }
        return new ServerConfig(DEFAULT_HOST, DEFAULT_PORT, "defaults");
    }

    public static ServerConfig fromFile(Path file) {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read configuration file " + file.toAbsolutePath(), e);
        }
        return fromProperties(props, file.toAbsolutePath().normalize().toString());
    }

    public static ServerConfig fromProperties(Properties props, String source) {
        String host = props.getProperty(KEY_HOST, DEFAULT_HOST);
        String portText = props.getProperty(KEY_PORT, String.valueOf(DEFAULT_PORT)).trim();
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + KEY_PORT + " in " + source + ": '" + portText + "'");
        }
        return new ServerConfig(host, port, source);
    }
}
