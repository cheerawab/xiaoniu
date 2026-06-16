package co.partygame.matchmaking;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import co.partygame.matchmaking.gui.MatchGUI;

import java.util.Arrays;

public class MatchGUIHandler implements Listener {
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getName() == null) return;
        
        String invName = event.getInventory().getName();
        if (invName.startsWith("§6=== 配對界面 ===")) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null || event.getClick() == ClickType.LEFT) return;
            
            Player player = (Player) event.getWhoClicked();
            String itemName = event.getCurrentItem().getItemMeta().getDisplayName();
            
            switch (itemName) {
                case "§a§l生存遊戲":
                    player.closeInventory();
                    MatchmakingPlugin.getInstance().startMatch(player, "survival", null);
                    break;
                case "§b§l障礙關卡":
                    player.closeInventory();
                    MatchmakingPlugin.getInstance().startMatch(player, "obby", null);
                    break;
                case "§c§l殭屍圍城":
                    player.closeInventory();
                    MatchmakingPlugin.getInstance().startMatch(player, "zombie", null);
                    break;
                case "§d§l查看統計":
                    player.closeInventory();
                    MatchmakingPlugin.getInstance().showQueueStatus(player);
                    break;
                case "§7§l關閉":
                    player.closeInventory();
                    break;
            }
        }
    }
    
    public static void openMatchGUI(Player player) {
        String title = "§6=== 配對界面 ===";
        Inventory inventory = Bukkit.createInventory(null, 27, title);
        
        ItemStack survival = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        ItemMeta survivalMeta = survival.getItemMeta();
        survivalMeta.setDisplayName("§a§l生存遊戲");
        survivalMeta.setLore(Arrays.asList(
            "§7進行生存淘汰賽",
            "§7人數: 4-8 人",
            "§7點擊加入匹配",
            "",
            "§e權限: partygame.game.survival"
        ));
        survival.setItemMeta(survivalMeta);
        
        ItemStack obby = new ItemStack(org.bukkit.Material.PURPUR_BLOCK);
        ItemMeta obbyMeta = obby.getItemMeta();
        obbyMeta.setDisplayName("§b§l障礙關卡");
        obbyMeta.setLore(Arrays.asList(
            "§7通過障礙關卡挑戰",
            "§7人數: 4-8 人",
            "§7點擊加入匹配",
            "",
            "§e權限: partygame.game.obby"
        ));
        obby.setItemMeta(obbyMeta);
        
        ItemStack zombie = new ItemStack(org.bukkit Material.ZOMBIE_HEAD);
        ItemMeta zombieMeta = zombie.getItemMeta();
        zombieMeta.setDisplayName("§c§l殭屍圍城");
        zombieMeta.setLore(Arrays.asList(
            "§7對抗殭屍潮",
            "§7人數: 4-8 人",
            "§7點擊加入匹配",
            "",
            "§e權限: partygame.game.zombie (VIP+)"
        ));
        zombie.setItemMeta(zombieMeta);
        
        inventory.setItem(10, survival);
        inventory.setItem(12, obby);
        inventory.setItem(14, zombie);
        
        player.openInventory(inventory);
    }
}
