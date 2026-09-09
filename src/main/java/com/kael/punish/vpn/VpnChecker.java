package com.kael.punish.vpn;

import com.kael.punish.config.PluginConfig;
import com.kael.punish.util.IpFilter;
import com.kael.punish.util.Messages;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VpnChecker {

    private static final Pattern PROXY = Pattern.compile("\"proxy\"\\s*:\\s*(true|false)");
    private static final Pattern HOSTING = Pattern.compile("\"hosting\"\\s*:\\s*(true|false)");
    private static final String BYPASS_PERMISSION = "kaerban.vpn.bypass";

    private final PluginConfig config;
    private final Logger logger;
    private final HttpClient client;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public VpnChecker(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void check(Player player, String ip) {
        if (!config.isVpnEnabled() || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        if (!IpFilter.isPublic(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(ip);
        if (entry != null && entry.expiresAt > now) {
            handle(player, ip, entry.result);
            return;
        }
        try {
            VpnResult result = query(ip);
            long ttl = Math.max(1, config.getVpnCacheMinutes()) * 60_000L;
            cache.put(ip, new CacheEntry(now + ttl, result));
            handle(player, ip, result);
        } catch (Exception e) {
            logger.warn("VPN 检测请求失败，玩家 {}：{}", player.getUsername(), e.getMessage());
        }
    }

    private VpnResult query(String ip) throws Exception {
        String encodedIp = ip.contains(":") ? "[" + ip + "]" : ip;
        String url = "http://ip-api.com/json/" + encodedIp + "?fields=status,message,proxy,hosting";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body.contains("\"status\":\"fail\"")) {
            throw new IllegalStateException("ip-api 返回 fail");
        }
        return new VpnResult(booleanValue(PROXY, body), booleanValue(HOSTING, body));
    }

    private void handle(Player player, String ip, VpnResult result) {
        if (!result.proxy && !result.hosting) {
            return;
        }
        logger.warn("检测到 VPN/代理：玩家 {}，IP {}", player.getUsername(), ip);
        if (config.isVpnKickOnDetect() && player.isActive()) {
            player.disconnect(Messages.vpnScreen());
        }
    }

    private boolean booleanValue(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() && "true".equals(matcher.group(1));
    }

    private static final class VpnResult {
        private final boolean proxy;
        private final boolean hosting;

        private VpnResult(boolean proxy, boolean hosting) {
            this.proxy = proxy;
            this.hosting = hosting;
        }
    }

    private static final class CacheEntry {
        private final long expiresAt;
        private final VpnResult result;

        private CacheEntry(long expiresAt, VpnResult result) {
            this.expiresAt = expiresAt;
            this.result = result;
        }
    }
}
