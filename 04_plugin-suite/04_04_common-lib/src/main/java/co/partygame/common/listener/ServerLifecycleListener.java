package co.partygame.common.listener;

import co.partygame.common.McCommonPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

public class ServerLifecycleListener implements Listener {
    private final McCommonPlugin plugin;

    public ServerLifecycleListener(McCommonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getLogger().info(event.getPlayer().getName() + " 加入伺服器");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getLogger().info(event.getPlayer().getName() + " 離開伺服器");
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        plugin.getLogger().warning("插件已停用: " + event.getPlugin().getName());
    }
}
