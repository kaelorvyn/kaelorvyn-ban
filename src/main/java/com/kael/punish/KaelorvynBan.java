package com.kael.punish;

import com.kael.punish.command.AdminCommand;
import com.kael.punish.config.PluginConfig;
import com.kael.punish.listener.LoginListener;
import com.kael.punish.storage.Database;
import com.kael.punish.util.Messages;
import com.kael.punish.vpn.VpnChecker;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "kaelorvynban",
        name = "KaelorvynBan",
        version = "1.0.0",
        description = "Velocity 中文封禁插件",
        authors = {"Kael"}
)
public final class KaelorvynBan {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Database database;

    @Inject
    public KaelorvynBan(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        PluginConfig config = new PluginConfig(dataDirectory, logger).load();
        Messages.setTimezone(config.getTimezone());
        database = new Database(config, logger);
        database.ensureTables();

        VpnChecker vpnChecker = new VpnChecker(config, logger);
        server.getEventManager().register(this, new LoginListener(database, vpnChecker, logger));

        CommandMeta meta = server.getCommandManager().metaBuilder("kban")
                .aliases("ktempban", "kipban", "ktempipban", "kunban", "kkick", "kcheck",
                        "kopunban", "kopreban", "kbanlink", "ktempipbanlink")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, new AdminCommand(server, database, logger));

        logger.info("KaelorvynBan 已启动，数据库可用：{}", database.available());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
        logger.info("KaelorvynBan 已关闭。");
    }
}
