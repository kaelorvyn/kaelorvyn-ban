package com.kael.punish.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineUuid {

    private OfflineUuid() {
    }

    public static UUID fromName(String playerName) {
        String source = "OfflinePlayer:" + playerName;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
