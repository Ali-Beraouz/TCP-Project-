package com.chatapp.server;

import com.chatapp.server.config.ServerConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerConfigTest {

    @Test
    void readsHostAndPortFromProperties() {
        Properties props = new Properties();
        props.setProperty("server.host", "127.0.0.1");
        props.setProperty("server.port", "4567");
        ServerConfig config = ServerConfig.fromProperties(props, "test");
        assertEquals("127.0.0.1", config.host());
        assertEquals(4567, config.port());
    }

    @Test
    void missingValuesFallBackToDefaults() {
        ServerConfig config = ServerConfig.fromProperties(new Properties(), "empty");
        assertEquals(ServerConfig.DEFAULT_HOST, config.host());
        assertEquals(ServerConfig.DEFAULT_PORT, config.port());
    }

    @Test
    void invalidPortIsRejected() {
        Properties props = new Properties();
        props.setProperty("server.port", "abc");
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromProperties(props, "test"));
        props.setProperty("server.port", "70000");
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromProperties(props, "test"));
    }

    @Test
    void externalFileIsReadAtRuntime() throws Exception {
        Path file = Files.createTempFile("server", ".properties");
        try {
            Files.writeString(file, "server.host=0.0.0.0\nserver.port=5050\n");
            System.setProperty(ServerConfig.CONFIG_PROPERTY, file.toString());
            ServerConfig config = ServerConfig.load();
            assertEquals(5050, config.port());
            assertEquals(file.toAbsolutePath().normalize().toString(), config.source());
        } finally {
            System.clearProperty(ServerConfig.CONFIG_PROPERTY);
            Files.deleteIfExists(file);
        }
    }
}
