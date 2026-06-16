package co.partygame.common.protocol.packets.lobby;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 玩家信息 - 在配對相關報文中攜帶的簡化玩家資料。
 * 不包含 Bukkit Player 引用，確保可序列化。
 */
public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID uuid;
    private final String name;

    public PlayerInfo(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return true;
        PlayerInfo info = (PlayerInfo) o;
        return Objects.equals(uuid, info.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "PlayerInfo{uuid=" + uuid + ", name='" + name + "'}";
    }
}
