package co.partygame.core;

import co.partygame.framework.SWMFrameworkPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.logging.Level;

public class PartyGamePlugin extends JavaPlugin {
    private static PartyGamePlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        SWMFrameworkPlugin swmPlugin = (SWMFrameworkPlugin) Bukkit.getPluginManager().getPlugin("SWMFramework");
        if (swmPlugin == null) {
            getLogger().severe("無法找到 SWMFramework 插件！禁用 PartyGameCore...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("PartyGame 框架載入成功 - PartyGameCore 就緒");
        getLogger().info("世界管理已初始化");
        getLogger().info("PartyGameCore v1.0.0 已啟用");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("PartyGameCore v1.0.0 已停用");
    }

    public static PartyGamePlugin getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("此命令僅供玩家使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (cmd.getName().equalsIgnoreCase("partygame")) {
            if (args.length == 0) {
                player.sendMessage("§6=== Party Game Core ===");
                player.sendMessage("§a版本: v1.0.0");
                player.sendMessage("§a使用: /partygame <debug|status|world>");
                return true;
            }
            
            String subCmd = args[0].toLowerCase();
            switch (subCmd) {
                case "debug":
                    player.sendMessage("§adebug mode enabled");
                    break;
                case "status":
                    player.sendMessage("§aPartyGameCore is running");
                    break;
                case "world":
                    if (args.length >= 2) {
                        String worldName = args[1];
                        player.sendMessage("§aWorld: " + worldName);
                    }
                    break;
            }
        }
        
        return true;
    }
}
