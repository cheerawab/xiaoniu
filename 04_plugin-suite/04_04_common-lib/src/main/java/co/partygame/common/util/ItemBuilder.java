package co.partygame.common.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 物品創建工具類。
 *
 * 使用建造者模式創建 Minecraft 物品，支援名稱、描述、附魔、旗標等設置。
 * 提供序列化/反序列化方法用於保存物品數據。
 *
 * 使用示例：
 * <pre>{@code
 * ItemStack sword = new ItemBuilder(Material.DIAMOND_SWORD)
 *     .amount(1)
 *     .displayName("&6&l 神聖之劍")
 *     .lore("傷害 +10", "耐久 無限", "&7右鍵點擊使用")
 *     .enchant(Enchantment.DAMAGE_ALL, 5)
 *     .glow()
 *     .build();
 * ItemBuilder.serialize(sword); // Map<String, Object>
 * }</pre>
 */
public class ItemBuilder {

    private static final Logger LOGGER = Logger.getLogger(ItemBuilder.class.getName());

    private final Material material;
    private int amount = 1;
    private short durability = 0;
    private String displayName = null;
    private List<String> lore = null;
    private Map<Enchantment, Integer> enchantments = null;
    private boolean hideEnchantFlag = false;
    private boolean hideAttributeFlag = false;

    /**
     * 創建新的 ItemBuilder。
     *
     * @param material 物品類型
     */
    public ItemBuilder(Material material) {
        this.material = Objects.requireNonNull(material, "Material must not be null");
        this.amount = 1;
    }

    /**
     * 設置物品數量。
     *
     * @param amount 數量
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder amount(int amount) {
        this.amount = Math.max(1, Math.min(64, amount));
        return this;
    }

    /**
     * 設置物品耐久。
     *
     * @param durability 耐久值
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder durability(short durability) {
        this.durability = durability;
        return this;
    }

    /**
     * 設置物品顯示名稱。
     * 名稱中的顏色代碼 (如 &6&l) 將被自動解析。
     *
     * @param name 顯示名稱
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder displayName(String name) {
        this.displayName = ChatUtils.colorize(name);
        return this;
    }

    /**
     * 設置物品描述。
     * 每行將被自動解析顏色代碼。
     *
     * @param lines 描述行
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder lore(String... lines) {
        if (lines != null && lines.length > 0) {
            List<String> colorized = new ArrayList<>();
            for (String line : lines) {
                colorized.add(ChatUtils.colorize(line));
            }
            this.lore = colorized;
        }
        return this;
    }

    /**
     * 添加附魔 (受到最大等級限制)。
     *
     * @param enchantment 附魔類型
     * @param level       附魔等級
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (enchantments == null) {
            enchantments = new HashMap<>();
        }
        enchantments.put(enchantment, level);
        return this;
    }

    /**
     * 添加無限制附魔 (不受等級限制).
     *
     * @param enchantment 附魔類型
     * @param level       附魔等級
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder unsafeEnchant(Enchantment enchantment, int level) {
        return enchant(enchantment, level);
    }

    /**
     * 設置物品為附魔效果 (glow 效果).
     * 同時設置 HIDE_ENCHANTS 旗標使附魔效果可見但隱藏附魔信息。
     *
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder glow() {
        if (enchantments == null) {
            enchantments = new HashMap<>();
        }
        enchantments.put(Enchantment.DURABILITY, 1);
        this.hideEnchantFlag = true;
        return this;
    }

    /**
     * 設置物品 Material 由字符串指定。
     * 如果 Material 不存在, 將使用 "AIR"。
     *
     * @param name 物品類型名稱 (如 "DIAMOND_SWORD", "CHEST", "PLAYER_HEAD")
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder material(String name) {
        try {
            this.material = Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Material not found: " + name + ", using AIR");
            this.material = Material.AIR;
        }
        return this;
    }

    /**
     * 設置物品旗標。
     *
     * @param flags 旗標列表
     * @return 此 ItemBuilder 實例
     */
    public ItemBuilder flags(ItemFlag... flags) {
        for (ItemFlag flag : flags) {
            switch (flag) {
                case HIDE_ENCHANTS:
                    this.hideEnchantFlag = true;
                    break;
                default:
                    break;
            }
        }
        return this;
    }

    /**
     * 構建最終的 ItemStack 對象。
     *
     * @return 構建好的 ItemStack
     */
    public ItemStack build() {
        try {
            ItemStack item = new ItemStack(material, amount, durability);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            if (displayName != null) {
                meta.setDisplayName(displayName);
            }
            if (lore != null) {
                meta.setLore(new ArrayList<>(lore));
            }
            if (enchantments != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            }
            if (hideEnchantFlag) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (hideAttributeFlag) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }

            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to build item: " + material, e);
            return new ItemStack(Material.AIR);
        }
    }

    // ─── Getters ─────────────────────────────────────────────────

    public Material getMaterial() { return material; }
    public int getAmount() { return amount; }
    public short getDurability() { return durability; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public Map<Enchantment, Integer> getEnchantments() { return enchantments; }

    /**
     * 序列化 ItemStack 為 Map<String, Object>。
     * 用於持久化保存。
     *
     * @param item 要序列化的 ItemStack
     * @return Map<String, Object> (null 如果 item 為 null)
     */
    public static Map<String, Object> serialize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("material", item.getType().name());
        data.put("amount", item.getAmount());
        data.put("durability", item.getDurability());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                // Strip color codes when saving
                String sname = net.md_5.bungee.api.ChatColor.stripColor(meta.getDisplayName());
                data.put("displayname", meta.getDisplayName());
            }
            if (meta.hasLore()) {
                List<String> loreLines = new ArrayList<>();
                for (String s : meta.getLore()) {
                    loreLines.add(s);
                }
                data.put("lore", loreLines);
            }
            if (meta.hasEnchants()) {
                Map<String, Integer> enchants = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                    enchants.put(e.getKey().getName(), e.getValue());
                }
                data.put("enchantments", enchants);
            }
        }
        return data;
    }

    /**
     * 從 Map 反序列化為 ItemStack。
     *
     * @param map 序列化數據
     * @return 反序列化後的 ItemStack (null 如果 map 為 null)
     */
    public static ItemStack deserialize(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            Material mat = Material.valueOf(map.get("material").toString());
            int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
            short durability = map.containsKey("durability") ? ((Number) map.get("durability")).shortValue() : (short) 0;
            ItemStack item = new ItemStack(mat, amount, durability);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return item;

            if (map.containsKey("displayname")) {
                Object name = map.get("displayname");
                if (name != null) {
                    meta.setDisplayName(ChatUtils.colorize(name.toString()));
                }
            }
            if (map.containsKey("lore")) {
                Object loreObj = map.get("lore");
                if (loreObj instanceof List) {
                    List<String> loreLines = new ArrayList<>();
                    for (Object s : (List<?>) loreObj) {
                        if (s != null) {
                            loreLines.add(ChatUtils.colorize(s.toString()));
                        }
                    }
                    meta.setLore(loreLines);
                }
            }
            if (map.containsKey("enchantments")) {
                Object enchantsObj = map.get("enchantments");
                if (enchantsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> enchantsMap = (Map<String, Object>) enchantsObj;
                    for (Map.Entry<String, Object> e : enchantsMap.entrySet()) {
                        try {
                            Enchantment enchant = Enchantment.getByName(e.getKey());
                            if (enchant != null) {
                                int level = ((Number) e.getValue()).intValue();
                                meta.addEnchant(enchant, level, true);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize item from map", e);
            return null;
        }
    }
}
