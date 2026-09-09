package com.kael.punish.util;

import com.kael.punish.storage.BanRecord;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Messages {

    public static final String PREFIX = "§8[§6KaelorvynBan§8] §7";
    public static final String NO_PERMISSION = PREFIX + "§c该封禁指令仅限代理端控制台使用。";
    public static final String DATABASE_UNAVAILABLE = PREFIX + "§c数据库不可用，请检查 MariaDB 和插件配置。";
    public static final String INVALID_USAGE = PREFIX + "§c命令用法错误。";
    public static final String INVALID_DURATION = PREFIX + "§c封禁时长无效，支持 30m / 12h / 7d / 1y。";
    private static volatile String timezone = "Asia/Shanghai";

    private Messages() {
    }

    public static void setTimezone(String value) {
        if (value != null && !value.isBlank()) {
            timezone = value;
        }
    }

    public static Component component(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    public static void send(CommandSource source, String legacy) {
        source.sendMessage(component(legacy));
    }

    public static Component banScreen(BanRecord ban) {
        boolean temporary = ban.getType() == BanRecord.Type.TEMPBAN
                || ban.getType() == BanRecord.Type.LINK_TEMP_BAN;
        String title = temporary
                ? "§6§l你已被临时封禁！"
                : "§4§l你已被封禁！";
        return component(title + "\n\n"
                + "§7原因：§c" + safe(ban.getReason()) + "\n"
                + "§7封禁 ID：§e" + ban.getBanId() + "\n"
                + "§7解封时间：§e" + expireText(ban) + "\n"
                + "§7剩余时间：§e" + remainingText(ban));
    }

    public static Component ipBanScreen(BanRecord ban) {
        String title = ban.getType() == BanRecord.Type.TEMP_IP_BAN
                ? "§6§l你的 IP 已被临时封禁！"
                : "§4§l你的 IP 已被封禁！";
        return component(title + "\n\n"
                + "§7原因：§c" + safe(ban.getReason()) + "\n"
                + "§7封禁 ID：§e" + ban.getBanId() + "\n"
                + "§7解封时间：§e" + expireText(ban) + "\n"
                + "§7剩余时间：§e" + remainingText(ban));
    }

    public static Component kickScreen(String reason) {
        return component("§e§l你已被踢出！\n\n§7原因：§c" + safe(reason));
    }

    public static Component vpnScreen() {
        return component("§4§l检测到 VPN / 代理！\n\n"
                + "§7本服务器不允许使用 VPN 或代理连接。\n"
                + "§7请关闭 VPN 或代理后重试。");
    }

    public static String banSuccess(String player, String banId) {
        return PREFIX + "§a玩家 §e" + player + " §a已被永久封禁。封禁 ID：§e" + banId;
    }

    public static String tempBanSuccess(String player, String duration, String banId) {
        return PREFIX + "§a玩家 §e" + player + " §a已被封禁 §e" + duration + "§a。封禁 ID：§e" + banId;
    }

    public static String ipBanSuccess(String player, String ip, String banId) {
        return PREFIX + "§a玩家 §e" + player + " §a已被永久封禁，其 IP §e" + ip
                + " §a也已永久封禁。封禁 ID：§e" + banId;
    }

    public static String tempIpBanSuccess(String player, String ip, String duration, String banId) {
        return PREFIX + "§a玩家 §e" + player + " §a已被临时封禁 §e" + duration
                + "§a，其 IP §e" + ip + " §a也已临时封禁。封禁 ID：§e" + banId;
    }

    public static String unbanSuccess(String player) {
        return PREFIX + "§a玩家 §e" + player + " §a的玩家封禁和 IP 封禁已解除。";
    }

    public static String kickSuccess(String player) {
        return PREFIX + "§a玩家 §e" + player + " §a已被踢出。";
    }

    public static String alreadyBanned(String player) {
        return PREFIX + "§c玩家 §e" + player + " §c已有生效封禁。";
    }

    public static String higherBanExists(String player, String currentType) {
        return PREFIX + "§c玩家 §e" + player + " §c已有更高等级封禁（§e"
                + currentType + "§c），不能降级或重复封禁。";
    }

    public static String alreadyIpBanned(String ip) {
        return PREFIX + "§cIP §e" + ip + " §c已有生效封禁。";
    }

    public static String notBanned(String player) {
        return PREFIX + "§c未找到玩家 §e" + player + " §c的生效封禁。";
    }

    public static String playerNotOnline(String player) {
        return PREFIX + "§c玩家 §e" + player + " §c不在线。";
    }

    public static String noPublicIp(String player) {
        return PREFIX + "§c玩家 §e" + player + " §c没有可用的真实公网 IP 记录，无法执行 IP 封禁。";
    }

    public static String ipBanExemptSuccess(String player) {
        return PREFIX + "§a玩家 §e" + player + " §a已单独豁免 IP 封禁。";
    }

    public static String ipBanExemptRemoved(String player) {
        return PREFIX + "§a玩家 §e" + player + " §a的 IP 封禁豁免已取消。";
    }

    public static String ipBanExemptAlready(String player) {
        return PREFIX + "§c玩家 §e" + player + " §c已经在 IP 封禁豁免名单中。";
    }

    public static String ipBanExemptNotFound(String player) {
        return PREFIX + "§c玩家 §e" + player + " §c不在 IP 封禁豁免名单中。";
    }

    public static String ipBanExemptNoActive(String player) {
        return PREFIX + "§c玩家 §e" + player + " §c当前没有生效的 IP 封禁，无法设置关联豁免。";
    }

    public static String unbanLinkSuccess(String player, int count) {
        return PREFIX + "§a已一起解封玩家 §e" + player + " §a的 §e" + count
                + " §a个关联账号，并清除全部相关豁免。";
    }

    public static String associatedAccountsWarning(java.util.List<String> names) {
        return PREFIX + "§7注意：这些 IP 还关联到 §f" + String.join(", ", names)
                + " §7；如果不是小号，请用 §e/kopunban <玩家> §7单独豁免。";
    }

    public static String banLinkSuccess(String player, int count) {
        return PREFIX + "§a玩家 §e" + player + " §a已被永久封禁，并已链式封禁其 §e"
                + count + " §a个关联公网 IP。";
    }

    public static String tempIpBanLinkSuccess(String player, String duration, int count) {
        return PREFIX + "§a玩家 §e" + player + " §a已被临时封禁 §e" + duration
                + "§a，并已链式封禁其 §e" + count + " §a个关联公网 IP。";
    }

    public static String checkHeader(String player) {
        return PREFIX + "§8§m----------§r §e生效封禁：§6" + player + " §8§m----------";
    }

    public static String checkEmpty(String player) {
        return PREFIX + "§7玩家 §e" + player + " §7没有生效封禁。";
    }

    public static String checkEntry(BanRecord ban, String remaining) {
        String ipPart = ban.getIp() == null || ban.getIp().isBlank() ? "" : " §8| §7IP：§f" + ban.getIp();
        return PREFIX + "§8- §c" + ban.getType().chineseName()
                + " §8| §7原因：§f" + safe(ban.getReason())
                + ipPart
                + " §8| §7ID：§e" + ban.getBanId()
                + " §8| §7剩余时间：§e" + remaining
                + " §8| §7封禁人：§f" + ban.getBannedBy();
    }

    public static String expireText(BanRecord ban) {
        if (ban.isPermanent()) {
            return "永久";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of(timezone))
                .format(Instant.ofEpochMilli(ban.getEndAt()));
    }

    public static String remainingText(BanRecord ban) {
        if (ban.isPermanent()) {
            return "永久";
        }
        long now = System.currentTimeMillis();
        long remaining = ban.getEndAt() - now;
        return DurationParser.formatRemaining(remaining);
    }

    public static String safe(String text) {
        return text == null ? "" : text.replace("§", "");
    }
}
