package com.kael.punish.command;

import com.kael.punish.storage.BanRecord;
import com.kael.punish.storage.Database;
import com.kael.punish.util.DurationParser;
import com.kael.punish.util.IpFilter;
import com.kael.punish.util.Messages;
import com.kael.punish.util.OfflineUuid;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AdminCommand implements SimpleCommand {

    private final ProxyServer server;
    private final Database database;
    private final Logger logger;

    public AdminCommand(ProxyServer server, Database database, Logger logger) {
        this.server = server;
        this.database = database;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            Messages.send(source, Messages.NO_PERMISSION);
            return;
        }
        String alias = invocation.alias().toLowerCase(Locale.ROOT);
        String[] args = invocation.arguments();
        switch (alias) {
            case "kban":
                ban(source, args);
                break;
            case "ktempban":
                tempban(source, args);
                break;
            case "kipban":
                ipban(source, args, false);
                break;
            case "ktempipban":
                ipban(source, args, true);
                break;
            case "kunban":
                unban(source, args);
                break;
            case "kkick":
                kick(source, args);
                break;
            case "kcheck":
                check(source, args);
                break;
            case "kopunban":
                ipExempt(source, args, true);
                break;
            case "kopreban":
                ipExempt(source, args, false);
                break;
            case "kbanlink":
                banLink(source, args);
                break;
            case "ktempipbanlink":
                tempIpBanLink(source, args);
                break;
            default:
                Messages.send(source, Messages.INVALID_USAGE);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof ConsoleCommandSource;
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    private void ban(CommandSource source, String[] args) {
        if (args.length < 2) {
            sendUsage(source, "/kban <玩家> <原因>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        String reason = join(args, 1);
        UUID uuid = OfflineUuid.fromName(playerName);
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        if (database.highestActiveSeverityInNetwork(uuid.toString(), nameLower, null) >= BanRecord.Type.BAN.severity()) {
            Messages.send(source, Messages.higherBanExists(playerName, "已有生效封禁"));
            return;
        }
        long now = System.currentTimeMillis();
        Optional<BanRecord> ban = database.banPlayer(uuid.toString(), playerName, nameLower,
                BanRecord.Type.BAN, reason, bannedBy(source), now, null);
        if (ban.isEmpty()) {
            Messages.send(source, Messages.PREFIX + "§c写入玩家封禁失败，请查看控制台日志。");
            return;
        }
        Messages.send(source, Messages.banSuccess(playerName, ban.get().getBanId()));
        logger.info("管理员 {} 永久封禁玩家 {}，原因：{}，封禁 ID：{}",
                bannedBy(source), playerName, reason, ban.get().getBanId());
        kickOnlineByName(playerName, Messages.banScreen(ban.get()));
    }

    private void tempban(CommandSource source, String[] args) {
        if (args.length < 3) {
            sendUsage(source, "/ktempban <玩家> <时长> <原因>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        long duration;
        try {
            duration = DurationParser.parse(args[1]);
        } catch (IllegalArgumentException e) {
            Messages.send(source, Messages.INVALID_DURATION);
            return;
        }
        String reason = join(args, 2);
        UUID uuid = OfflineUuid.fromName(playerName);
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        if (database.highestActiveSeverityInNetwork(uuid.toString(), nameLower, null) >= BanRecord.Type.TEMPBAN.severity()) {
            Messages.send(source, Messages.higherBanExists(playerName, "已有生效封禁"));
            return;
        }
        long now = System.currentTimeMillis();
        long endAt = now + duration;
        Optional<BanRecord> ban = database.banPlayer(uuid.toString(), playerName, nameLower,
                BanRecord.Type.TEMPBAN, reason, bannedBy(source), now, endAt);
        if (ban.isEmpty()) {
            Messages.send(source, Messages.PREFIX + "§c写入临时封禁失败，请查看控制台日志。");
            return;
        }
        Messages.send(source, Messages.tempBanSuccess(playerName, args[1], ban.get().getBanId()));
        logger.info("管理员 {} 临时封禁玩家 {}，时长 {}，原因：{}，封禁 ID：{}",
                bannedBy(source), playerName, args[1], reason, ban.get().getBanId());
        kickOnlineByName(playerName, Messages.banScreen(ban.get()));
    }

    private void ipban(CommandSource source, String[] args, boolean temporary) {
        if (temporary ? args.length < 3 : args.length < 2) {
            sendUsage(source, temporary ? "/ktempipban <玩家> <时长> <原因>" : "/kipban <玩家> <原因>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        String reason = temporary ? join(args, 2) : join(args, 1);
        Long endAt = null;
        String durationText = "永久";
        if (temporary) {
            try {
                endAt = System.currentTimeMillis() + DurationParser.parse(args[1]);
                durationText = args[1];
            } catch (IllegalArgumentException e) {
                Messages.send(source, Messages.INVALID_DURATION);
                return;
            }
        }
        UUID uuid = OfflineUuid.fromName(playerName);
        BanRecord.Type ipType = temporary ? BanRecord.Type.TEMP_IP_BAN : BanRecord.Type.IP_BAN;
        BanRecord.Type playerType = temporary ? BanRecord.Type.TEMPBAN : BanRecord.Type.BAN;
        long now = System.currentTimeMillis();
        String currentIp = findOnlinePlayer(playerName)
                .map(player -> IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress()))
                .orElse(null);
        if (database.highestActiveSeverityInNetwork(uuid.toString(), nameLower, currentIp)
                >= BanRecord.Type.TEMP_IP_BAN.severity()) {
            Messages.send(source, Messages.higherBanExists(playerName, "已有 IP/链式封禁"));
            return;
        }
        Database.LinkedBanResult result = database.createLinkedBan(uuid.toString(), playerName, nameLower,
                currentIp, reason, bannedBy(source), now, endAt, playerType, ipType);
        if (result.getAccountBan().isEmpty() && result.getIpBan().isEmpty()) {
            Messages.send(source, Messages.PREFIX + "§c写入封禁失败，请查看控制台日志。");
            return;
        }
        BanRecord displayBan = result.getIpBan().orElseGet(() -> result.getAccountBan().orElseThrow());
        String displayIp = result.getIps().isEmpty() ? "无公网 IP 记录" : result.getIps().get(0);
        if (temporary) {
            Messages.send(source, Messages.tempIpBanSuccess(playerName, displayIp, durationText, displayBan.getBanId()));
        } else {
            Messages.send(source, Messages.ipBanSuccess(playerName, displayIp, displayBan.getBanId()));
        }
        logger.info("管理员 {} {} 玩家 {} 的关联封禁完成，封禁账号 {} 个、公网 IP {} 个，原因：{}，封禁 ID：{}",
                bannedBy(source), temporary ? "临时封禁" : "永久封禁", playerName,
                result.getInsertedAccounts(), result.getInsertedIps(), reason, displayBan.getBanId());
        result.getAccountBan().ifPresent(ban -> kickOnlineByName(playerName, Messages.banScreen(ban)));
        for (String ip : result.getIps()) {
            kickAllByIp(ip, result.getIpBan().map(Messages::ipBanScreen)
                    .orElseGet(() -> Messages.banScreen(displayBan)));
        }
        List<String> linkedAccounts = new ArrayList<>(result.getAccounts());
        linkedAccounts.remove(nameLower);
        if (!linkedAccounts.isEmpty()) {
            Messages.send(source, Messages.associatedAccountsWarning(linkedAccounts));
        }
    }

    private void unban(CommandSource source, String[] args) {
        if (args.length < 1) {
            sendUsage(source, "/kunban <玩家>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        UUID uuid = OfflineUuid.fromName(playerName);
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        LinkedBanData linked = linkedBanData(nameLower);
        int changed = 0;
        for (String account : linked.accounts) {
            String accountUuid = database.findUuidByNameLower(account)
                    .orElseGet(() -> OfflineUuid.fromName(account).toString());
            changed += database.unbanPlayer(accountUuid, account);
            database.removeIpBanExemption(accountUuid);
        }
        if (changed > 0) {
            Messages.send(source, Messages.unbanLinkSuccess(playerName, linked.accounts.size()));
            logger.info("管理员 {} 解除了玩家 {} 及 {} 个关联账号的封禁，并清除豁免。",
                    bannedBy(source), playerName, linked.accounts.size());
        } else {
            Messages.send(source, Messages.notBanned(playerName));
        }
    }

    private void banLink(CommandSource source, String[] args) {
        if (args.length < 2) {
            sendUsage(source, "/kbanlink <玩家> <原因>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        String reason = join(args, 1);
        UUID uuid = OfflineUuid.fromName(playerName);
        long now = System.currentTimeMillis();
        String currentIp = findOnlinePlayer(playerName)
                .map(player -> IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress()))
                .orElse(null);
        if (database.highestActiveSeverityInNetwork(uuid.toString(), nameLower, currentIp)
                >= BanRecord.Type.LINK_BAN.severity()) {
            Messages.send(source, Messages.higherBanExists(playerName, "已有链式封禁"));
            return;
        }
        Database.LinkedBanResult result = database.createLinkedBan(uuid.toString(), playerName, nameLower,
                currentIp, reason, bannedBy(source), now, null,
                BanRecord.Type.LINK_BAN, BanRecord.Type.IP_BAN);
        if (result.getAccountBan().isEmpty() && result.getIpBan().isEmpty()) {
            Messages.send(source, Messages.PREFIX + "§c写入封禁失败，请查看控制台日志。");
            return;
        }
        BanRecord displayBan = result.getAccountBan().orElseGet(() -> result.getIpBan().orElseThrow());
        Messages.send(source, Messages.banLinkSuccess(playerName, result.getInsertedIps()));
        List<String> linkedAccounts = new ArrayList<>(result.getAccounts());
        linkedAccounts.remove(nameLower);
        if (!linkedAccounts.isEmpty()) {
            Messages.send(source, Messages.associatedAccountsWarning(linkedAccounts));
        }
        result.getAccountBan().ifPresent(ban -> kickOnlineByName(playerName, Messages.banScreen(ban)));
        for (String ip : result.getIps()) {
            kickAllByIp(ip, result.getIpBan().map(Messages::ipBanScreen)
                    .orElseGet(() -> Messages.banScreen(displayBan)));
        }
        logger.info("管理员 {} 链式关联封禁玩家 {}，封禁账号 {} 个、公网 IP {} 个，关联账号 {}。",
                bannedBy(source), playerName, result.getInsertedAccounts(), result.getInsertedIps(),
                String.join(", ", linkedAccounts));
    }

    private void tempIpBanLink(CommandSource source, String[] args) {
        if (args.length < 3) {
            sendUsage(source, "/ktempipbanlink <玩家> <时长> <原因>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        long duration;
        try {
            duration = DurationParser.parse(args[1]);
        } catch (IllegalArgumentException e) {
            Messages.send(source, Messages.INVALID_DURATION);
            return;
        }
        String reason = join(args, 2);
        UUID uuid = OfflineUuid.fromName(playerName);
        long now = System.currentTimeMillis();
        long endAt = now + duration;
        String currentIp = findOnlinePlayer(playerName)
                .map(player -> IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress()))
                .orElse(null);
        if (database.highestActiveSeverityInNetwork(uuid.toString(), nameLower, currentIp)
                >= BanRecord.Type.LINK_TEMP_BAN.severity()) {
            Messages.send(source, Messages.higherBanExists(playerName, "已有链式封禁"));
            return;
        }
        Database.LinkedBanResult result = database.createLinkedBan(uuid.toString(), playerName, nameLower,
                currentIp, reason, bannedBy(source), now, endAt,
                BanRecord.Type.LINK_TEMP_BAN, BanRecord.Type.TEMP_IP_BAN);
        if (result.getAccountBan().isEmpty() && result.getIpBan().isEmpty()) {
            Messages.send(source, Messages.PREFIX + "§c写入封禁失败，请查看控制台日志。");
            return;
        }
        BanRecord displayBan = result.getAccountBan().orElseGet(() -> result.getIpBan().orElseThrow());
        Messages.send(source, Messages.tempIpBanLinkSuccess(playerName, args[1], result.getInsertedIps()));
        List<String> linkedAccounts = new ArrayList<>(result.getAccounts());
        linkedAccounts.remove(nameLower);
        if (!linkedAccounts.isEmpty()) {
            Messages.send(source, Messages.associatedAccountsWarning(linkedAccounts));
        }
        result.getAccountBan().ifPresent(ban -> kickOnlineByName(playerName, Messages.banScreen(ban)));
        for (String ip : result.getIps()) {
            kickAllByIp(ip, result.getIpBan().map(Messages::ipBanScreen)
                    .orElseGet(() -> Messages.banScreen(displayBan)));
        }
        logger.info("管理员 {} 临时链式封禁玩家 {}，时长 {}，封禁账号 {} 个、公网 IP {} 个，关联账号 {}。",
                bannedBy(source), playerName, args[1], result.getInsertedAccounts(),
                result.getInsertedIps(), String.join(", ", linkedAccounts));
    }

    private void kick(CommandSource source, String[] args) {
        if (args.length < 2) {
            sendUsage(source, "/kkick <玩家> <原因>");
            return;
        }
        String playerName = args[0];
        String reason = join(args, 1);
        Optional<Player> player = findOnlinePlayer(playerName);
        if (player.isEmpty()) {
            Messages.send(source, Messages.playerNotOnline(playerName));
            return;
        }
        player.get().disconnect(Messages.kickScreen(reason));
        Messages.send(source, Messages.kickSuccess(playerName));
        logger.info("管理员 {} 踢出了玩家 {}，原因：{}", bannedBy(source), playerName, reason);
    }

    private void check(CommandSource source, String[] args) {
        if (args.length < 1) {
            sendUsage(source, "/kcheck <玩家>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        UUID uuid = OfflineUuid.fromName(playerName);
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        List<BanRecord> records = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        database.findActivePlayerBan(uuid.toString(), nameLower).ifPresent(ban -> addUnique(records, seen, ban));
        for (BanRecord ban : database.findActiveIpBansByTarget(uuid.toString(), nameLower)) {
            addUnique(records, seen, ban);
        }
        for (String ip : database.findKnownIps(nameLower)) {
            database.findActiveIpBan(ip).ifPresent(ban -> addUnique(records, seen, ban));
        }
        if (records.isEmpty()) {
            Messages.send(source, Messages.checkEmpty(playerName));
            return;
        }
        Messages.send(source, Messages.checkHeader(playerName));
        for (BanRecord ban : records) {
            Messages.send(source, Messages.checkEntry(ban, Messages.remainingText(ban)));
        }
    }

    private void addUnique(List<BanRecord> records, Set<String> seen, BanRecord ban) {
        if (seen.add(ban.getBanId())) {
            records.add(ban);
        }
    }

    private Optional<Player> findOnlinePlayer(String playerName) {
        for (Player player : server.getAllPlayers()) {
            if (player.getUsername().equalsIgnoreCase(playerName)) {
                return Optional.of(player);
            }
        }
        return server.getPlayer(playerName);
    }

    private void kickOnlineByName(String playerName, Component message) {
        findOnlinePlayer(playerName).ifPresent(player -> player.disconnect(message));
    }

    private void kickAllByIp(String ip, Component message) {
        for (Player player : server.getAllPlayers()) {
            String playerIp = IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress());
            if (playerIp.equals(ip) && !database.isIpBanExempt(player.getUniqueId().toString())) {
                player.disconnect(message);
            }
        }
    }

    private void ipExempt(CommandSource source, String[] args, boolean add) {
        if (args.length < 1) {
            sendUsage(source, add ? "/kopunban <玩家>" : "/kopreban <玩家>");
            return;
        }
        if (!database.available()) {
            Messages.send(source, Messages.DATABASE_UNAVAILABLE);
            return;
        }
        String playerName = args[0];
        UUID uuid = OfflineUuid.fromName(playerName);
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        if (add) {
            if (database.isIpBanExempt(uuid.toString())) {
                Messages.send(source, Messages.ipBanExemptAlready(playerName));
                return;
            }
            boolean found = false;
            boolean permanent = false;
            long latestEnd = Long.MIN_VALUE;
            for (BanRecord ban : database.findActiveIpBansByTarget(uuid.toString(), nameLower)) {
                found = true;
                if (ban.isPermanent()) {
                    permanent = true;
                } else if (ban.getEndAt() > latestEnd) {
                    latestEnd = ban.getEndAt();
                }
            }
            for (String ip : database.findKnownIps(nameLower)) {
                Optional<BanRecord> ban = database.findActiveIpBan(ip);
                if (ban.isPresent()) {
                    found = true;
                    if (ban.get().isPermanent()) {
                        permanent = true;
                    } else if (ban.get().getEndAt() > latestEnd) {
                        latestEnd = ban.get().getEndAt();
                    }
                }
            }
            if (!found) {
                Messages.send(source, Messages.ipBanExemptNoActive(playerName));
                return;
            }
            Long expiresAt = permanent ? null : latestEnd;
            if (database.addIpBanExemption(uuid.toString(), playerName, System.currentTimeMillis(), expiresAt)) {
                Messages.send(source, Messages.ipBanExemptSuccess(playerName));
                logger.info("管理员 {} 豁免了玩家 {} 的 IP 封禁。", bannedBy(source), playerName);
            } else {
                Messages.send(source, Messages.PREFIX + "§c写入 IP 封禁豁免失败，请查看控制台日志。");
            }
        } else {
            if (database.removeIpBanExemption(uuid.toString()) > 0) {
                Messages.send(source, Messages.ipBanExemptRemoved(playerName));
                logger.info("管理员 {} 取消了玩家 {} 的 IP 封禁豁免。", bannedBy(source), playerName);
            } else {
                Messages.send(source, Messages.ipBanExemptNotFound(playerName));
            }
        }
    }

    private void warnAssociatedAccounts(CommandSource source, String playerName, String nameLower) {
        List<String> names = new ArrayList<>();
        for (String ip : knownPublicIps(nameLower)) {
            for (String name : database.findPlayerNamesByIp(ip)) {
                if (!name.equalsIgnoreCase(playerName) && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        if (!names.isEmpty()) {
            Messages.send(source, Messages.associatedAccountsWarning(names));
        }
    }

    private void banAllAssociatedPublicIps(String playerName, String reason, String bannedBy,
                                           long startAt, Long endAt, boolean temporary) {
        String nameLower = playerName.toLowerCase(Locale.ROOT);
        UUID uuid = OfflineUuid.fromName(playerName);
        BanRecord.Type type = temporary ? BanRecord.Type.TEMP_IP_BAN : BanRecord.Type.IP_BAN;
        int count = 0;
        for (String ip : knownPublicIps(nameLower)) {
            Optional<BanRecord> ban = database.banIp(uuid.toString(), playerName, nameLower, ip,
                    type, reason, bannedBy, startAt, endAt);
            if (ban.isPresent()) {
                count++;
            }
        }
        if (count > 0) {
            logger.info("已关联封禁玩家 {} 的 {} 个公网 IP。", playerName, count);
        }
    }

    private List<String> knownPublicIps(String nameLower) {
        List<String> ips = new ArrayList<>();
        for (String ip : database.findKnownIps(nameLower)) {
            if (!IpFilter.isPublic(ip)) {
                continue;
            }
            String normalized = IpFilter.normalize(ip);
            if (!ips.contains(normalized)) {
                ips.add(normalized);
            }
        }
        return ips;
    }

    private LinkedBanData linkedBanData(String nameLower) {
        Set<String> accounts = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        accounts.add(nameLower);
        queue.add(nameLower);
        List<String> ips = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            String account = queue.get(i);
            for (String ip : knownPublicIps(account)) {
                addLinkedIp(ips, accounts, queue, ip);
            }
            for (String ip : database.findIpBanIpsByName(account)) {
                addLinkedIp(ips, accounts, queue, ip);
            }
            if (i == 0) {
                Optional<Player> online = findOnlinePlayer(account);
                if (online.isPresent()) {
                    String currentIp = IpFilter.normalize(online.get().getRemoteAddress().getAddress().getHostAddress());
                    addLinkedIp(ips, accounts, queue, currentIp);
                }
            }
        }
        return new LinkedBanData(ips, accounts);
    }

    private void addLinkedIp(List<String> ips, Set<String> accounts, List<String> queue, String ip) {
        if (!IpFilter.isPublic(ip)) {
            return;
        }
        String normalized = IpFilter.normalize(ip);
        if (!ips.contains(normalized)) {
            ips.add(normalized);
        }
        for (String other : database.findPlayerNamesByIp(normalized)) {
            if (accounts.add(other)) {
                queue.add(other);
            }
        }
    }

    private String bannedBy(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername();
        }
        return "控制台";
    }

    private String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void sendUsage(CommandSource source, String usage) {
        Messages.send(source, Messages.PREFIX + "§c命令用法：§e" + usage);
    }

    private static final class LinkedBanData {
        private final List<String> ips;
        private final Set<String> accounts;

        private LinkedBanData(List<String> ips, Set<String> accounts) {
            this.ips = ips;
            this.accounts = accounts;
        }
    }
}
