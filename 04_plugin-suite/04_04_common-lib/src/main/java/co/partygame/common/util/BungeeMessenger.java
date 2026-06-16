package co.partygame.common.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Level;

public class BungeeMessenger {
    private final JavaPlugin plugin;

    public BungeeMessenger(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getServer().getPluginManager().isPluginEnabled("BungeeCord")) {
            plugin.getServer().getPluginManager().registerEvent(
                new org.bukkit.event.server.PluginMessageEvent("BungeeCord", "partygame", new byte[0]),
                org.bukkit.event.Listener.class,
                org.bukkit.event.EventPriority.NORMAL,
                (listener, event) -> {
                    if (event.getTag().equals("BungeeCord")) {
                        plugin.getLogger().info("接收 BungeeCord 消息");
                    }
                },
                plugin, false
            );
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord",
            (channel, player, data) -> {
                handleIncomingMessage(channel, player, data);
            }
        );
    }

    public void sendMessage(String targetServer, String... data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            for (String s : data) {
                out.writeUTF(s);
            }
            if (targetServer.equals("*")) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    player.sendPluginMessage(plugin, "BungeeCord", baos.toByteArray());
                }
            } else {
                org.bukkit.Server server = plugin.getServer().getServer(targetServer);
                if (server != null) {
                    server.sendPluginMessage(plugin, "BungeeCord", baos.toByteArray());
                } else {
                    plugin.getLogger().warning("無法找到目標伺服器: " + targetServer);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "發送 BungeeCord 消息失敗", e);
        }
    }

    private void handleIncomingMessage(String channel, org.bukkit.entity.Player player, byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream in = new DataInputStream(bais);
            String subChannel = in.readUTF();
            
            switch (subChannel) {
                case "MatchAccepted":
                    plugin.getLogger().info("收到 MatchAccepted 從 " + player.getName());
                    break;
                case "GameStart":
                    plugin.getLogger().info("收到 GameStart 從 " + player.getName());
                    break;
                case "GameEnd":
                    plugin.getLogger().info("收到 GameEnd 從 " + player.getName());
                    break;
                default:
                    plugin.getLogger().info("收到未知消息: " + subChannel);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "處理 incoming 消息失敗", e);
        }
    }
}
