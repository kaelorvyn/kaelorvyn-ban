package com.kael.punish.listener;

import com.kael.punish.storage.BanRecord;
import com.kael.punish.storage.Database;
import com.kael.punish.util.IpFilter;
import com.kael.punish.util.Messages;
import com.kael.punish.vpn.VpnChecker;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LoginListener {

    private final Database database;
    private final VpnChecker vpnChecker;
    private final Logger logger;

    public LoginListener(Database database, VpnChecker vpnChecker, Logger logger) {
        this.database = database;
        this.vpnChecker = vpnChecker;
        this.logger = logger;
    }

    @Subscribe(async = true)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (!database.available()) {
            logger.warn("登录封禁检查已跳过，数据库不可用：玩家 {}", player.getUsername());
            return;
        }
        String uuid = player.getUniqueId().toString();
        String nameLower = player.getUsername().toLowerCase(Locale.ROOT);
        String ip = IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress());

        Optional<BanRecord> linkedBan = database.enforceLinkedBan(
                uuid, player.getUsername(), nameLower, ip, System.currentTimeMillis());
        if (linkedBan.isPresent()) {
            event.setResult(ResultedEvent.ComponentResult.denied(Messages.banScreen(linkedBan.get())));
            logger.info("玩家 {} 触发连坐封禁 {}，已自动封禁当前 IP {}。",
                    player.getUsername(), linkedBan.get().getBanId(), ip);
            return;
        }
        Optional<BanRecord> playerBan = database.findActivePlayerBan(uuid, nameLower);
        if (playerBan.isPresent()) {
            event.setResult(ResultedEvent.ComponentResult.denied(Messages.banScreen(playerBan.get())));
            return;
        }
        Optional<BanRecord> ipBan = database.findActiveIpBan(ip);
        if (ipBan.isPresent() && !database.isIpBanExempt(uuid)) {
            event.setResult(ResultedEvent.ComponentResult.denied(Messages.ipBanScreen(ipBan.get())));
        } else if (ipBan.isPresent()) {
            logger.info("玩家 {} 已豁免 IP 封禁，放行。", player.getUsername());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String ip = IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress());
        String uuid = player.getUniqueId().toString();
        String name = player.getUsername();
        CompletableFuture.runAsync(() -> {
            if (database.available()) {
                database.recordLogin(uuid, name, ip, System.currentTimeMillis());
                Optional<BanRecord> linkedBan = database.enforceLinkedBan(
                        uuid, name, name.toLowerCase(Locale.ROOT), ip, System.currentTimeMillis());
                if (linkedBan.isPresent()) {
                    player.disconnect(Messages.banScreen(linkedBan.get()));
                    logger.info("玩家 {} 关联到生效封禁 {}，已自动补封当前 IP 并踢出。",
                            name, linkedBan.get().getBanId());
                    return;
                }
            }
            vpnChecker.check(player, ip);
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return;
        }
        Player player = event.getPlayer();
        String ip = IpFilter.normalize(player.getRemoteAddress().getAddress().getHostAddress());
        String uuid = player.getUniqueId().toString();
        CompletableFuture.runAsync(() -> {
            if (database.available()) {
                database.touchDisconnect(uuid, ip, System.currentTimeMillis());
            }
        });
    }
}
