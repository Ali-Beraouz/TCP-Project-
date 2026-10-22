package com.chatapp.server.model;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds the machine's LAN IPv4 addresses so the server can show where clients should connect. */
public final class NetworkInfo {

    private NetworkInfo() {
    }

    public static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                nif.inetAddresses()
                        .filter(Inet4Address.class::isInstance)
                        .filter(a -> !a.isLinkLocalAddress())
                        .map(a -> a.getHostAddress())
                        .forEach(result::add);
            }
        } catch (SocketException e) {
            // no network information available - not fatal
        }
        return result;
    }
}
