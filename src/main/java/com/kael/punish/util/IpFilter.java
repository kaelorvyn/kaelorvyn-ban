package com.kael.punish.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class IpFilter {

    private IpFilter() {
    }

    public static String normalize(String host) {
        if (host == null) {
            return "";
        }
        String ip = host.trim();
        if (ip.startsWith("::ffff:")) {
            return ip.substring("::ffff:".length());
        }
        return ip;
    }

    public static boolean isPublic(String host) {
        String ip = normalize(host);
        if (ip.isEmpty()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return !address.isAnyLocalAddress()
                    && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress()
                    && !address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
