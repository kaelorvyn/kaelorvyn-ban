package com.kael.punish;

import com.kael.punish.config.PluginConfig;
import com.kael.punish.storage.BanRecord;
import com.kael.punish.storage.Database;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

public final class DbSmokeTest {

    private static final String TEST_UUID = "00000000-0000-0000-0000-00000000test";
    private static final String TEST_NAME = "SmokeTest";
    private static final String TEST_IP = "8.8.8.8";

    private DbSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Path.of("D:\\MC\\server\\[25565] 代理端\\plugins\\KaelorvynBan");
        PluginConfig config = new PluginConfig(dataDirectory, LoggerFactory.getLogger("DbSmokeTest")).load();
        Database database = new Database(config, LoggerFactory.getLogger("DbSmokeTest"));
        database.ensureTables();
        if (!database.available()) {
            throw new IllegalStateException("数据库不可用");
        }

        cleanup(config);
        long now = System.currentTimeMillis();
        database.recordLogin(TEST_UUID, TEST_NAME, TEST_IP, now);

        Optional<BanRecord> playerBan = database.banPlayer(
                TEST_UUID, TEST_NAME, TEST_NAME.toLowerCase(), BanRecord.Type.BAN,
                "数据库自检原因", "控制台", now, null);
        require(playerBan.isPresent(), "玩家封禁写入失败");
        require(database.findActivePlayerBan(TEST_UUID, TEST_NAME.toLowerCase()).isPresent(), "玩家封禁查询失败");

        Optional<BanRecord> ipBan = database.banIp(
                TEST_UUID, TEST_NAME, TEST_NAME.toLowerCase(), TEST_IP, BanRecord.Type.IP_BAN,
                "数据库自检原因", "控制台", now, null);
        require(ipBan.isPresent(), "IP 封禁写入失败");
        require(database.findActiveIpBan(TEST_IP).isPresent(), "IP 封禁查询失败");
        require(database.latestPublicIp(TEST_NAME.toLowerCase()).orElse("").equals(TEST_IP), "公网 IP 查询失败");

        int unbanned = database.unbanPlayer(TEST_UUID, TEST_NAME.toLowerCase());
        require(unbanned >= 2, "解封记录数异常: " + unbanned);
        require(database.findActivePlayerBan(TEST_UUID, TEST_NAME.toLowerCase()).isEmpty(), "玩家封禁未解除");
        require(database.findActiveIpBan(TEST_IP).isEmpty(), "IP 封禁未解除");

        cleanup(config);
        System.out.println("DbSmokeTest OK");
    }

    private static void cleanup(PluginConfig config) throws SQLException {
        String url = "jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/"
                + config.getDatabaseName()
                + "?useSSL=false&characterEncoding=utf8&connectTimeout=3000&socketTimeout=3000"
                + "&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(
                url, config.getDatabaseUser(), config.getDatabasePassword())) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM player_ips WHERE uuid = ?")) {
                statement.setString(1, TEST_UUID);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM ban_logs WHERE target_uuid = ?")) {
                statement.setString(1, TEST_UUID);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM ip_bans WHERE target_uuid = ?")) {
                statement.setString(1, TEST_UUID);
                statement.executeUpdate();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
