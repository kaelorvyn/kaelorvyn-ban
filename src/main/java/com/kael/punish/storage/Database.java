package com.kael.punish.storage;

import com.kael.punish.config.PluginConfig;
import com.kael.punish.util.IpFilter;
import com.kael.punish.util.OfflineUuid;
import org.slf4j.Logger;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class Database {

    private static final String BAN_COLUMNS = "id, ban_id, type, target_uuid, target_name, "
            + "target_name_lower, reason, banned_by, start_at, end_at, active";
    private static final String IP_BAN_COLUMNS = "id, ban_id, type, target_uuid, target_name, "
            + "target_name_lower, ip, reason, banned_by, start_at, end_at, active";

    private final PluginConfig config;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    private volatile boolean available;

    public Database(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.warn("无法加载 MySQL 驱动：{}", e.getMessage());
        }
    }

    public boolean available() {
        if (available) {
            return true;
        }
        try (Connection connection = open()) {
            available = true;
            return true;
        } catch (SQLException e) {
            available = false;
            return false;
        }
    }

    public void ensureTables() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS ban_logs ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "ban_id VARCHAR(40) NOT NULL UNIQUE,"
                        + "type VARCHAR(20) NOT NULL,"
                        + "target_uuid CHAR(36) NOT NULL,"
                        + "target_name VARCHAR(16) NOT NULL,"
                        + "target_name_lower VARCHAR(16) NOT NULL,"
                        + "reason TEXT NOT NULL,"
                        + "banned_by VARCHAR(64) NOT NULL,"
                        + "start_at BIGINT NOT NULL,"
                        + "end_at BIGINT NULL,"
                        + "active TINYINT(1) NOT NULL DEFAULT 1,"
                        + "INDEX idx_ban_uuid_active (target_uuid, active),"
                        + "INDEX idx_ban_name_active (target_name_lower, active)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS ip_bans ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "ban_id VARCHAR(40) NOT NULL UNIQUE,"
                        + "type VARCHAR(20) NOT NULL,"
                        + "target_uuid CHAR(36) NOT NULL,"
                        + "target_name VARCHAR(16) NOT NULL,"
                        + "target_name_lower VARCHAR(16) NOT NULL,"
                        + "ip VARCHAR(45) NOT NULL,"
                        + "reason TEXT NOT NULL,"
                        + "banned_by VARCHAR(64) NOT NULL,"
                        + "start_at BIGINT NOT NULL,"
                        + "end_at BIGINT NULL,"
                        + "active TINYINT(1) NOT NULL DEFAULT 1,"
                        + "INDEX idx_ipban_uuid_active (target_uuid, active),"
                        + "INDEX idx_ipban_name_active (target_name_lower, active),"
                        + "INDEX idx_ipban_ip_active (ip, active)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS player_ips ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + "uuid CHAR(36) NOT NULL,"
                        + "player_name VARCHAR(16) NOT NULL,"
                        + "player_name_lower VARCHAR(16) NOT NULL,"
                        + "ip VARCHAR(45) NOT NULL,"
                        + "first_seen BIGINT NOT NULL,"
                        + "last_seen BIGINT NOT NULL,"
                        + "UNIQUE KEY uk_player_ip (uuid, ip),"
                        + "INDEX idx_ip_last_seen (ip, last_seen),"
                        + "INDEX idx_name_last_seen (player_name_lower, last_seen)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS ip_ban_exemptions ("
                        + "uuid CHAR(36) PRIMARY KEY,"
                        + "player_name VARCHAR(16) NOT NULL,"
                        + "player_name_lower VARCHAR(16) NOT NULL,"
                        + "created_at BIGINT NOT NULL,"
                        + "expires_at BIGINT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "ALTER TABLE ip_ban_exemptions ADD COLUMN IF NOT EXISTS expires_at BIGINT NULL"
        };
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            available = true;
            logger.info("kaerban 数据表检查完成。");
        } catch (SQLException e) {
            available = false;
            logger.warn("数据库初始化失败：{}", e.getMessage());
        }
    }

    public Optional<BanRecord> findActivePlayerBan(String uuid, String nameLower) {
        if (isIpBanExempt(uuid)) {
            return Optional.empty();
        }
        String sql = "SELECT " + BAN_COLUMNS + " FROM ban_logs "
                + "WHERE active = 1 AND (target_uuid = ? OR target_name_lower = ?) "
                + "AND (end_at IS NULL OR end_at > ?) ORDER BY start_at ASC LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, nameLower);
            statement.setLong(3, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapBan(result));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询玩家封禁", e);
        }
        return Optional.empty();
    }

    public Optional<BanRecord> findActiveIpBan(String ip) {
        String sql = "SELECT " + IP_BAN_COLUMNS + " FROM ip_bans "
                + "WHERE active = 1 AND ip = ? AND (end_at IS NULL OR end_at > ?) "
                + "ORDER BY start_at ASC LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ip);
            statement.setLong(2, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapIpBan(result));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询 IP 封禁", e);
        }
        return Optional.empty();
    }

    public List<BanRecord> findActiveIpBansByTarget(String uuid, String nameLower) {
        List<BanRecord> records = new ArrayList<>();
        String sql = "SELECT " + IP_BAN_COLUMNS + " FROM ip_bans "
                + "WHERE active = 1 AND (target_uuid = ? OR target_name_lower = ?) "
                + "AND (end_at IS NULL OR end_at > ?) ORDER BY start_at ASC";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, nameLower);
            statement.setLong(3, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(mapIpBan(result));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询目标 IP 封禁", e);
        }
        return records;
    }

    public List<String> findKnownIps(String nameLower) {
        List<String> ips = new ArrayList<>();
        String sql = "SELECT ip FROM player_ips WHERE player_name_lower = ? "
                + "ORDER BY last_seen DESC, id DESC";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nameLower);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String ip = result.getString("ip");
                    if (!ips.contains(ip)) {
                        ips.add(ip);
                    }
                }
            }
        } catch (SQLException e) {
            logQueryError("查询玩家 IP 历史", e);
        }
        return ips;
    }

    public Optional<String> latestPublicIp(String nameLower) {
        for (String ip : findKnownIps(nameLower)) {
            if (IpFilter.isPublic(ip)) {
                return Optional.of(IpFilter.normalize(ip));
            }
        }
        return Optional.empty();
    }

    public boolean isIpBanExempt(String uuid) {
        String sql = "SELECT expires_at FROM ip_ban_exemptions WHERE uuid = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                Long expiresAt = nullableLong(result, "expires_at");
                if (expiresAt == null || expiresAt > System.currentTimeMillis()) {
                    return true;
                }
                removeIpBanExemption(uuid);
                return false;
            }
        } catch (SQLException e) {
            logQueryError("查询 IP 封禁豁免", e);
            return false;
        }
    }

    public boolean addIpBanExemption(String uuid, String playerName, long now, Long expiresAt) {
        String sql = "INSERT INTO ip_ban_exemptions "
                + "(uuid, player_name, player_name_lower, created_at, expires_at) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), "
                + "player_name_lower = VALUES(player_name_lower), created_at = VALUES(created_at), "
                + "expires_at = VALUES(expires_at)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, playerName);
            statement.setString(3, playerName.toLowerCase(Locale.ROOT));
            statement.setLong(4, now);
            if (expiresAt == null) {
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(5, expiresAt);
            }
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logQueryError("写入 IP 封禁豁免", e);
            return false;
        }
    }

    public int removeIpBanExemption(String uuid) {
        String sql = "DELETE FROM ip_ban_exemptions WHERE uuid = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("删除 IP 封禁豁免", e);
            return 0;
        }
    }

    public List<String> findPlayerNamesByIp(String ip) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT player_name_lower FROM ("
                + "SELECT player_name_lower, last_seen AS ts FROM player_ips WHERE ip = ? "
                + "UNION ALL "
                + "SELECT target_name_lower, start_at AS ts FROM ip_bans WHERE ip = ? "
                + ") t GROUP BY player_name_lower ORDER BY MAX(ts) DESC";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ip);
            statement.setString(2, ip);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    names.add(result.getString("player_name_lower"));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询 IP 关联账号", e);
        }
        return names;
    }

    public List<String> findIpBanIpsByName(String nameLower) {
        List<String> ips = new ArrayList<>();
        String sql = "SELECT DISTINCT ip FROM ip_bans WHERE target_name_lower = ? ORDER BY start_at DESC";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nameLower);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ips.add(result.getString("ip"));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询封禁记录 IP", e);
        }
        return ips;
    }

    public Optional<String> findUuidByNameLower(String nameLower) {
        String sql = "SELECT uuid FROM player_ips WHERE player_name_lower = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nameLower);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询玩家 UUID", e);
        }
        String sql2 = "SELECT target_uuid FROM ip_bans WHERE target_name_lower = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql2)) {
            statement.setString(1, nameLower);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getString("target_uuid"));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询封禁记录 UUID", e);
        }
        return Optional.empty();
    }

    public void recordLogin(String uuid, String playerName, String ip, long now) {
        String sql = "INSERT INTO player_ips "
                + "(uuid, player_name, player_name_lower, ip, first_seen, last_seen) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), "
                + "player_name_lower = VALUES(player_name_lower), last_seen = VALUES(last_seen)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, playerName);
            statement.setString(3, playerName.toLowerCase(Locale.ROOT));
            statement.setString(4, ip);
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("记录登录 IP", e);
        }
    }

    public void touchDisconnect(String uuid, String ip, long now) {
        String sql = "UPDATE player_ips SET last_seen = ? WHERE uuid = ? AND ip = ?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setString(2, uuid);
            statement.setString(3, ip);
            statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("更新最后在线时间", e);
        }
    }

    public Optional<BanRecord> banPlayer(String targetUuid, String targetName, String targetNameLower,
                                         BanRecord.Type type, String reason, String bannedBy,
                                         long startAt, Long endAt) {
        if (isIpBanExempt(targetUuid)) {
            return Optional.empty();
        }
        if (findActivePlayerBan(targetUuid, targetNameLower).isPresent()) {
            return Optional.empty();
        }
        String banId = generateBanId();
        String sql = "INSERT INTO ban_logs "
                + "(ban_id, type, target_uuid, target_name, target_name_lower, reason, banned_by, "
                + "start_at, end_at, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillBanStatement(statement, banId, type, targetUuid, targetName, targetNameLower, null,
                    reason, bannedBy, startAt, endAt);
            statement.executeUpdate();
            return Optional.of(new BanRecord(0, banId, type, targetUuid, targetName, targetNameLower,
                    null, reason, bannedBy, startAt, endAt, true));
        } catch (SQLException e) {
            logQueryError("写入玩家封禁", e);
            return Optional.empty();
        }
    }

    public Optional<BanRecord> banIp(String targetUuid, String targetName, String targetNameLower,
                                     String ip, BanRecord.Type type, String reason, String bannedBy,
                                     long startAt, Long endAt) {
        if (findActiveIpBan(ip).isPresent()) {
            return Optional.empty();
        }
        String banId = generateBanId();
        String sql = "INSERT INTO ip_bans "
                + "(ban_id, type, target_uuid, target_name, target_name_lower, ip, reason, banned_by, "
                + "start_at, end_at, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillBanStatement(statement, banId, type, targetUuid, targetName, targetNameLower, ip,
                    reason, bannedBy, startAt, endAt);
            statement.executeUpdate();
            return Optional.of(new BanRecord(0, banId, type, targetUuid, targetName, targetNameLower,
                    ip, reason, bannedBy, startAt, endAt, true));
        } catch (SQLException e) {
            logQueryError("写入 IP 封禁", e);
            return Optional.empty();
        }
    }

    public int highestActiveSeverityInNetwork(String seedUuid, String seedNameLower, String currentIp) {
        LinkedNetwork network = collectNetwork(seedNameLower, currentIp);
        int max = 0;
        for (String account : network.accounts) {
            String accountUuid = findUuidByNameLower(account)
                    .orElseGet(() -> OfflineUuid.fromName(account).toString());
            Optional<BanRecord> ban = findActivePlayerBan(accountUuid, account);
            if (ban.isPresent()) {
                max = Math.max(max, ban.get().getType().severity());
            }
        }
        for (String ip : network.ips) {
            Optional<BanRecord> ban = findActiveIpBan(ip);
            if (ban.isPresent()) {
                max = Math.max(max, ban.get().getType().severity());
            }
        }
        return max;
    }

    public void deactivateLowerSeverityInNetwork(String seedUuid, String seedNameLower,
                                                 String currentIp, int newSeverity) {
        LinkedNetwork network = collectNetwork(seedNameLower, currentIp);
        deactivateLowerBans(network, newSeverity);
    }

    public LinkedBanResult createLinkedBan(String seedUuid, String seedName, String seedNameLower,
                                           String currentIp, String reason, String bannedBy,
                                           long startAt, Long endAt,
                                           BanRecord.Type accountType, BanRecord.Type ipType) {
        return createLinkedBan(seedUuid, seedName, seedNameLower, currentIp, reason, bannedBy,
                startAt, endAt, accountType, ipType, true);
    }

    public LinkedBanResult createLinkedBan(String seedUuid, String seedName, String seedNameLower,
                                           String currentIp, String reason, String bannedBy,
                                           long startAt, Long endAt,
                                           BanRecord.Type accountType, BanRecord.Type ipType,
                                           boolean deactivateLower) {
        LinkedNetwork network = collectNetwork(seedNameLower, currentIp);
        if (deactivateLower) {
            int commandSeverity = Math.max(accountType.severity(), ipType.severity());
            deactivateLowerBans(network, commandSeverity);
        }
        Optional<BanRecord> accountBan = Optional.empty();
        int insertedAccounts = 0;
        for (String account : network.accounts) {
            String displayName = findKnownName(account).orElse(account);
            String accountUuid = findUuidByNameLower(account)
                    .orElseGet(() -> OfflineUuid.fromName(displayName).toString());
            if (isIpBanExempt(accountUuid)) {
                continue;
            }
            Optional<BanRecord> existing = findActivePlayerBan(accountUuid, account);
            if (existing.isPresent()) {
                if (accountBan.isEmpty()) {
                    accountBan = existing;
                }
                continue;
            }
            Optional<BanRecord> created = banPlayer(accountUuid, displayName, account, accountType,
                    reason, bannedBy, startAt, endAt);
            if (created.isPresent()) {
                insertedAccounts++;
                if (accountBan.isEmpty()) {
                    accountBan = created;
                }
            }
        }

        Optional<BanRecord> ipBan = Optional.empty();
        int insertedIps = 0;
        for (String ip : network.ips) {
            Optional<BanRecord> existing = findActiveIpBan(ip);
            if (existing.isPresent()) {
                if (ipBan.isEmpty()) {
                    ipBan = existing;
                }
                continue;
            }
            Optional<BanRecord> created = banIp(seedUuid, seedName, seedNameLower, ip, ipType,
                    reason, bannedBy, startAt, endAt);
            if (created.isPresent()) {
                insertedIps++;
                if (ipBan.isEmpty()) {
                    ipBan = created;
                }
            }
        }
        return new LinkedBanResult(accountBan, ipBan, insertedAccounts, insertedIps,
                network.accounts, network.ips);
    }

    private void deactivateLowerBans(LinkedNetwork network, int newSeverity) {
        if (newSeverity < 2 || network.accounts.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(network.accounts.size(), "?"));
        List<String> accountParams = new ArrayList<>(network.accounts);
        String accountTypes = "('BAN','TEMPBAN')";
        String accountSql = "UPDATE ban_logs SET active = 0 WHERE active = 1 "
                + "AND target_name_lower IN (" + placeholders + ") "
                + "AND type IN " + accountTypes;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(accountSql)) {
            for (int i = 0; i < accountParams.size(); i++) {
                statement.setString(i + 1, accountParams.get(i));
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("降级清理账号封禁", e);
        }

        if (newSeverity >= 3 && !network.ips.isEmpty()) {
            String ipPlaceholders = String.join(",", Collections.nCopies(network.ips.size(), "?"));
            String ipSql = "UPDATE ip_bans SET active = 0 WHERE active = 1 "
                    + "AND (target_name_lower IN (" + placeholders + ") OR ip IN (" + ipPlaceholders + ")) "
                    + "AND type IN ('IP_BAN','TEMP_IP_BAN')";
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(ipSql)) {
                int index = 1;
                for (String account : network.accounts) {
                    statement.setString(index++, account);
                }
                for (String ip : network.ips) {
                    statement.setString(index++, ip);
                }
                statement.executeUpdate();
            } catch (SQLException e) {
                logQueryError("降级清理 IP 封禁", e);
            }
        }
    }

    public Optional<BanRecord> enforceLinkedBan(String seedUuid, String seedName, String seedNameLower,
                                                String currentIp, long now) {
        if (isIpBanExempt(seedUuid)) {
            return Optional.empty();
        }
        LinkedNetwork network = collectNetwork(seedNameLower, currentIp);
        Optional<BanRecord> activeBan = Optional.empty();
        for (String account : network.accounts) {
            String accountUuid = findUuidByNameLower(account)
                    .orElseGet(() -> OfflineUuid.fromName(account).toString());
            Optional<BanRecord> existing = findActivePlayerBan(accountUuid, account);
            if (existing.isPresent()) {
                activeBan = existing;
                break;
            }
        }
        if (activeBan.isEmpty()) {
            return Optional.empty();
        }
        BanRecord source = activeBan.get();
        BanRecord.Type accountType;
        switch (source.getType()) {
            case TEMPBAN:
                accountType = BanRecord.Type.TEMPBAN;
                break;
            case LINK_TEMP_BAN:
                accountType = BanRecord.Type.LINK_TEMP_BAN;
                break;
            case LINK_BAN:
                accountType = BanRecord.Type.LINK_BAN;
                break;
            default:
                accountType = BanRecord.Type.BAN;
                break;
        }
        BanRecord.Type ipType = source.isPermanent()
                ? BanRecord.Type.IP_BAN : BanRecord.Type.TEMP_IP_BAN;
        createLinkedBan(seedUuid, seedName, seedNameLower, currentIp,
                source.getReason(), source.getBannedBy(), source.getStartAt(), source.getEndAt(),
                accountType, ipType, false);
        return activeBan;
    }

    public Optional<String> findKnownName(String nameLower) {
        String sql = "SELECT player_name FROM player_ips WHERE player_name_lower = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nameLower);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getString("player_name"));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询玩家显示名", e);
        }
        String sql2 = "SELECT target_name FROM ip_bans WHERE target_name_lower = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql2)) {
            statement.setString(1, nameLower);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getString("target_name"));
                }
            }
        } catch (SQLException e) {
            logQueryError("查询封禁记录显示名", e);
        }
        return Optional.empty();
    }

    public int unbanPlayer(String targetUuid, String targetNameLower) {
        int changed = 0;
        changed += update("UPDATE ban_logs SET active = 0 WHERE active = 1 "
                + "AND (target_uuid = ? OR target_name_lower = ?)", targetUuid, targetNameLower);
        changed += update("UPDATE ip_bans SET active = 0 WHERE active = 1 "
                + "AND (target_uuid = ? OR target_name_lower = ?)", targetUuid, targetNameLower);
        return changed;
    }

    public void close() {
        logger.info("KaelorvynBan 数据库连接已释放。");
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/"
                + config.getDatabaseName()
                + "?useSSL=false&characterEncoding=utf8&connectTimeout=3000&socketTimeout=3000"
                + "&allowPublicKeyRetrieval=true";
        Connection connection = DriverManager.getConnection(url, config.getDatabaseUser(), config.getDatabasePassword());
        available = true;
        return connection;
    }

    private int update(String sql, String uuid, String nameLower) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, nameLower);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logQueryError("解除封禁", e);
            return 0;
        }
    }

    private void fillBanStatement(PreparedStatement statement, String banId, BanRecord.Type type,
                                  String targetUuid, String targetName, String targetNameLower,
                                  String ip, String reason, String bannedBy, long startAt, Long endAt)
            throws SQLException {
        int index = 1;
        statement.setString(index++, banId);
        statement.setString(index++, type.name());
        statement.setString(index++, targetUuid);
        statement.setString(index++, targetName);
        statement.setString(index++, targetNameLower);
        if (ip != null) {
            statement.setString(index++, ip);
        }
        statement.setString(index++, reason);
        statement.setString(index++, bannedBy);
        statement.setLong(index++, startAt);
        if (endAt == null) {
            statement.setNull(index++, Types.BIGINT);
        } else {
            statement.setLong(index++, endAt);
        }
    }

    private String generateBanId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder builder = new StringBuilder("KB-");
            for (int i = 0; i < 8; i++) {
                builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String candidate = builder.toString();
            if (!banIdExists(candidate)) {
                return candidate;
            }
        }
        return "KB-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT);
    }

    private boolean banIdExists(String banId) {
        String sql = "SELECT 1 FROM ban_logs WHERE ban_id = ? "
                + "UNION ALL SELECT 1 FROM ip_bans WHERE ban_id = ? LIMIT 1";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, banId);
            statement.setString(2, banId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            logQueryError("检查封禁 ID", e);
            return true;
        }
    }

    private BanRecord mapBan(ResultSet result) throws SQLException {
        return new BanRecord(
                result.getLong("id"),
                result.getString("ban_id"),
                BanRecord.Type.valueOf(result.getString("type")),
                result.getString("target_uuid"),
                result.getString("target_name"),
                result.getString("target_name_lower"),
                null,
                result.getString("reason"),
                result.getString("banned_by"),
                result.getLong("start_at"),
                nullableLong(result, "end_at"),
                result.getBoolean("active")
        );
    }

    private BanRecord mapIpBan(ResultSet result) throws SQLException {
        return new BanRecord(
                result.getLong("id"),
                result.getString("ban_id"),
                BanRecord.Type.valueOf(result.getString("type")),
                result.getString("target_uuid"),
                result.getString("target_name"),
                result.getString("target_name_lower"),
                result.getString("ip"),
                result.getString("reason"),
                result.getString("banned_by"),
                result.getLong("start_at"),
                nullableLong(result, "end_at"),
                result.getBoolean("active")
        );
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private void logQueryError(String action, SQLException e) {
        available = false;
        logger.warn("{}失败：{}", action, e.getMessage());
    }

    private LinkedNetwork collectNetwork(String seedNameLower, String currentIp) {
        Set<String> accounts = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        List<String> ips = new ArrayList<>();
        accounts.add(seedNameLower);
        queue.add(seedNameLower);
        addLinkedIp(ips, accounts, queue, currentIp);
        for (int i = 0; i < queue.size(); i++) {
            String account = queue.get(i);
            for (String ip : findKnownIps(account)) {
                addLinkedIp(ips, accounts, queue, ip);
            }
            for (String ip : findIpBanIpsByName(account)) {
                addLinkedIp(ips, accounts, queue, ip);
            }
        }
        return new LinkedNetwork(accounts, ips);
    }

    private void addLinkedIp(List<String> ips, Set<String> accounts, List<String> queue, String ip) {
        if (ip == null || !IpFilter.isPublic(ip)) {
            return;
        }
        String normalized = IpFilter.normalize(ip);
        if (!ips.contains(normalized)) {
            ips.add(normalized);
        }
        for (String other : findPlayerNamesByIp(normalized)) {
            if (accounts.add(other)) {
                queue.add(other);
            }
        }
    }

    public static final class LinkedBanResult {

        private final Optional<BanRecord> accountBan;
        private final Optional<BanRecord> ipBan;
        private final int insertedAccounts;
        private final int insertedIps;
        private final List<String> accounts;
        private final List<String> ips;

        private LinkedBanResult(Optional<BanRecord> accountBan, Optional<BanRecord> ipBan,
                                int insertedAccounts, int insertedIps,
                                List<String> accounts, List<String> ips) {
            this.accountBan = accountBan;
            this.ipBan = ipBan;
            this.insertedAccounts = insertedAccounts;
            this.insertedIps = insertedIps;
            this.accounts = new ArrayList<>(accounts);
            this.ips = new ArrayList<>(ips);
        }

        public Optional<BanRecord> getAccountBan() {
            return accountBan;
        }

        public Optional<BanRecord> getIpBan() {
            return ipBan;
        }

        public int getInsertedAccounts() {
            return insertedAccounts;
        }

        public int getInsertedIps() {
            return insertedIps;
        }

        public List<String> getAccounts() {
            return accounts;
        }

        public List<String> getIps() {
            return ips;
        }
    }

    private static final class LinkedNetwork {

        private final List<String> accounts;
        private final List<String> ips;

        private LinkedNetwork(Set<String> accounts, List<String> ips) {
            this.accounts = new ArrayList<>(accounts);
            this.ips = new ArrayList<>(ips);
        }
    }
}
