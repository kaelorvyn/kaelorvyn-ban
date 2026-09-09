package com.kael.punish.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PluginConfig {

    private final Path dataDirectory;
    private final Logger logger;
    private String databaseHost = "localhost";
    private int databasePort = 3306;
    private String databaseName = "kaerban";
    private String databaseUser = "kaerban";
    private String databasePassword = "";
    private boolean vpnEnabled = false;
    private boolean vpnKickOnDetect = true;
    private int vpnCacheMinutes = 60;
    private String timezone = "Asia/Shanghai";

    public PluginConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public PluginConfig load() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.properties");
            if (!Files.exists(file)) {
                Files.writeString(file, defaultConfig(), StandardCharsets.UTF_8);
                logger.info("已创建默认配置：{}", file);
            }
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(file);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            databaseHost = value(properties, "database.host", databaseHost);
            databasePort = Integer.parseInt(value(properties, "database.port", String.valueOf(databasePort)));
            databaseName = value(properties, "database.name", databaseName);
            databaseUser = value(properties, "database.user", databaseUser);
            databasePassword = value(properties, "database.password", databasePassword);
            vpnEnabled = Boolean.parseBoolean(value(properties, "vpn.enabled", String.valueOf(vpnEnabled)));
            vpnKickOnDetect = Boolean.parseBoolean(value(properties, "vpn.kick-on-detect", String.valueOf(vpnKickOnDetect)));
            vpnCacheMinutes = Integer.parseInt(value(properties, "vpn.cache-minutes", String.valueOf(vpnCacheMinutes)));
            timezone = value(properties, "timezone", timezone);
        } catch (IOException | NumberFormatException e) {
            logger.warn("读取配置失败，使用默认配置：{}", e.getMessage());
        }
        return this;
    }

    private String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String defaultConfig() {
        return "database.host=localhost\n"
                + "database.port=3306\n"
                + "database.name=kaerban\n"
                + "database.user=kaerban\n"
                + "database.password=\n"
                + "vpn.enabled=false\n"
                + "vpn.kick-on-detect=true\n"
                + "vpn.cache-minutes=60\n"
                + "timezone=Asia/Shanghai\n";
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseUser() {
        return databaseUser;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public boolean isVpnEnabled() {
        return vpnEnabled;
    }

    public boolean isVpnKickOnDetect() {
        return vpnKickOnDetect;
    }

    public int getVpnCacheMinutes() {
        return vpnCacheMinutes;
    }

    public String getTimezone() {
        return timezone;
    }
}
