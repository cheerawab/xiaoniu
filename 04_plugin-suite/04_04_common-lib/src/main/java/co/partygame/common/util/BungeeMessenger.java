package co.partygame.common.util;

import co.partygame.common.protocol.packets.lobby.MatchRequest;
import co.partygame.common.protocol.packets.lobby.PlayerInfo;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * BungeeCord/Velocity 消息發送器。
 *
 * 封裝 BungeeCord 插件消息通道 ("MPNS") 和 Velocity 的插件消息機制。
 * 提供 MatchRequest 對象的序列化/反序列化方法。
 * 以及 Packets (bungeetablist+) 的 JSON 序列化/反序列化方法。
 *
 * 使用示例：
 * <pre>{@code
 * BungeeMessenger messenger = new BungeeMessenger(plugin);
 * messenger.sendToServer("hub-1", "SomeChannel", data);
 * MatchRequest req = messenger.deserializeMatchRequest(data);
 * }</pre>
 */
public class BungeeMessenger {

    private static final Logger LOGGER = Logger.getLogger(BungeeMessenger.class.getName());

    private final Plugin plugin;
    private String channel = "MPNS";

    /**
     * 創建 BungeeCord/Velocity 消息發送器。
     *
     * @param plugin 插件實例
     */
    public BungeeMessenger(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin must not be null");
        this.plugin = plugin;
    }

    /**
     * 發送數據到指定伺服器。
     * 通過所有在指定伺服器的玩家發送插件消息。
     *
     * @param server   目標伺服器名稱
     * @param channel  消息通道名稱
     * @param data     二進制數據
     */
    public void sendToServer(String server, String channel, byte[] data) {
        Objects.requireNonNull(server, "Server name must not be null");
        Objects.requireNonNull(channel, "Channel name must not be null");
        Objects.requireNonNull(data, "Data must not be null");
        sendMessageToServer(server, channel, data);
    }

    /**
     * 發送數據到伺服器，使用默認通道。
     *
     * @param server 目標伺服器名稱 `
     * @param data   二進制數據
     */
    public void sendToServer(String server, byte[] data) {
        sendToServer(server, this.channel, data);
    }

    /**
     * 設置插件消息通道。
     *
     * @param channel 通道名稱
     */
    public void setChannel(String channel) {
        Objects.requireNonNull(channel, "Channel must not be null");
        this.channel = channel;
    }

    /**
     * 序列化 MatchRequest 對象為二進制數據。
     *
     * @param request MatchRequest 對象
     * @return 序列化後的 byte[]
     * @throws IOException 序列化失敗時拋出
     */
    public byte[] serializeMatchRequest(MatchRequest request) throws IOException {
        Objects.requireNonNull(request, "MatchRequest must not be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
            out.writeObject(request);
            out.flush();
        }
        return baos.toByteArray();
    }

    /**
     * 反序列化二進制數據為 MatchRequest 對象。
     *
     * @param data 二進制數組
     * @return MatchRequest 對象
     * @throws IOException            反序列化失敗時拋出
     * @throws ClassNotFoundException 類找不到時拋出
     */
    public MatchRequest deserializeMatchRequest(byte[] data) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(data, "Data must not be null");
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream in = new ObjectInputStream(bais)) {
            Object obj = in.readObject();
            if (obj instanceof MatchRequest) {
                return (MatchRequest) obj;
            }
        }
        throw new IllegalArgumentException("Deserialized object is not a MatchRequest: " + data.getClass().getName());
    }

    /**
     * 將 Packets 插件的 Player 對象轉為 JSON 字符串。
     * 用於 bungeetablist+ 等插件的插件消息格式。
     *
     * @param players 玩家對象
     * @return JSON 字符串，格式為 `[{player1}, {player2}, ...]`
     */
    public String packetsToJson(PlayerInfo... players) {
        if (players == null || players.length == 0) return "[]";
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < players.length; i++) {
            PlayerInfo p = players[i];
            if (i > 0) json.append(", ");
            json.append("{").append("\"player_name\":\"").append(p.getName())
                .append("\",\"uuid\":\"").append(p.getUuid().toString()).append("\"}");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * 將 Packets 插件的 Player 對象轉為 JSON 字符串。
     * 用於 bungeetablist+ 等插件的插件消格式。
     *
     * @param players 玩家列表
     * @return JSON 字符串，格式為 `[{player1}, {player2}, ...]`
     */
    public String packetsToJson(Collection<PlayerInfo> players) {
        if (players == null || players.isEmpty()) return "[]";
        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;
        for (PlayerInfo p : players) {
            if (!first) json.append(", ");
            first = false;
            json.append("{").append("\"player_name\":\"").append(p.getName())
                .append("\",\"uuid\":\"").append(p.getUuid().toString()).append("\"}");
        }
        json.append("]");
        return json.toString();
    }
        json.append("]");
        return json.toString();
    }

    /**
     * 將 JSON 字符串解析為 Packets PlayerInfo 對象。
     * 用於 bungeetablist+ 等插件的插件消息格式。
     *
     * @param json JSON 字符串
     * @return PlayerInfo 實例
     * @throws IOException `解析 JSON 格式錯誤
     */
    public PlayerInfo jsonToPacket(String json) throws IOException {
        Objects.requireNonNull(json, "JSON string must not be null");
        json = json.trim();
        if (json.startsWith("[")) {
            json = json.substring(1, json.length() - 1).trim();
        }
        // Parse simple JSON like: {"player_name":"John","uuid":"1234-5678-90ab-cdef"}
        Map<String, String> map = new HashMap<>();
        json = json.replaceAll("^\\{|\\}$", "");
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].replaceAll("\"", "").trim();
                String value = kv[1].replaceAll("\"", "").trim();
                map.put(key, value);
            }
        }
        UUID uuid = UUID.fromString(map.getOrDefault("uuid", "00000000-0000-0000-0000-000000000000"));
        return new PlayerInfo(uuid, map.getOrDefault("player_name", "Unknown"));
    }

    private void sendMessageToServer(String server, String channel, byte[] data) {
        try {
            java.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
            out.writeUTF(server);
            out.writeUTF(channel);
            out.write(data);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.sendPluginMessage(plugin, "Bungeecord", out.toByteArray());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send plugin message to " + server, e);
        }
    }

    /**
     * 檢測 BungeeCord 是否可用。
     */
    private boolean isBungeeCordAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("BungeeCord") != null;
    }

    /**
     * 檢測 Velocity 是否可用。
     */
    private boolean isVelocityAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Velocity") != null;
    }

    /**
     * 註冊插件消息監聽器。
     * 需要調用此方法以接收來自 BungeeCord/Velocity 的消息。
     *
     * @param plugin 插件實例
     */
    public void registerListener(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin must not be null");
        try {
            org.bukkit.plugin.messaging.PluginMessageReceiver receiver = new org.bukkit.plugin.messaging.PluginMessageReceiver() {
                @Override
                public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] data) {
                    // Handle incoming messages here
                }

                @Override
                public void handleMessage(String channel, byte[] data) {
                    // Handle incoming messages here
                }

                @Override
                public void callHandlers(org.bukkit.plugin.Plugin plugin, String channel, byte[] data) {
                    // Handle incoming messages here
                }
            };
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", receiver);
            LOGGER.info("BungeeCord plugin message listener registered for " + plugin.getDescription().getName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to register BungeeCord listener", e);
        }
    }

    /**
     * 獲取當前配置的通道。
     *
     * @return 通道名稱
     */
    public String getChannel() {
        return channel;
    }

    /**
     * 獲取插件實例。
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * 創建新的 PlayerInfo 對象。
     * 用於構建 Packets JSON。
     *
     * @param name 玩家名稱
     * @param uuid 玩家 UUID
     * @return PlayerInfo 對象
     */
    public static PlayerInfo newPlayer(String name, UUID uuid) {
        return new PlayerInfo(uuid, name);
    }
}
