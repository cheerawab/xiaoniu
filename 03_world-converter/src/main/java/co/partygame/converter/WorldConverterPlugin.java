package co.partygame.converter;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class WorldConverterPlugin extends JavaPlugin {
    private static WorldConverterPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getLogger().info("WorldConverter v1.0.0 已啟用 - 世界轉換工具就緒");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("WorldConverter v1.0.0 已停用");
    }

    public static WorldConverterPlugin getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("此命令僅供管理員使用");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (cmd.getName().equalsIgnoreCase("worldconverter")) {
            if (args.length == 0) {
                player.sendMessage("§6=== World Converter ===");
                player.sendMessage("§a使用: /worldconverter <convert|status|info>");
                player.sendMessage("§a  convert <world> <output> - 轉換世界");
                player.sendMessage("§a  status - 查看轉換狀態");
                return true;
            }
            
            String subCmd = args[0].toLowerCase();
            switch (subCmd) {
                case "convert":
                    if (args.length < 3) {
                        player.sendMessage("§c使用方式: /worldconverter convert <world> <output>");
                        return true;
                    }
                    String worldName = args[1];
                    String outputDir = args[2];
                    player.sendMessage("§a開始轉換世界: " + worldName);
                    player.sendMessage("§a輸出目錄: " + outputDir);
                    // 完整實作需要讀取 world.dat 和 region 文件
                    getLogger().info("轉換請求: " + worldName + " -> " + outputDir);
                    break;
                case "status":
                    player.sendMessage("§aWorld Converter 就緒");
                    break;
            }
        }
        
        return true;
    }
}
