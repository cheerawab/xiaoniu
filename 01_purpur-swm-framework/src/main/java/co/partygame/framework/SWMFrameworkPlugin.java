package co.partygame.framework;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.logging.Level;

public class SWMFrameworkPlugin extends JavaPlugin implements Listener {
    private static SWMFrameworkPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        String worldFolder = getConfig().getString("swm.world_folder", "./swm_worlds/");
        getLogger().info("SWM 框架已啟用");
        getLogger().info("世界資料夾: " + worldFolder);
        
        File swmDir = new File(worldFolder);
        if (swmDir.exists() && swmDir.isDirectory()) {
            File[] files = swmDir.listFiles((dir, name) -> name.endsWith(".slimeworld"));
            if (files != null) {
                getLogger().info("找到 " + files.length + " 個 .slimeworld 檔案");
                for (File f : files) {
                    getLogger().info("  - " + f.getName());
                }
            }
        } else {
            getLogger().warning("世界資料夾不存在或不是目錄: " + worldFolder);
        }
        
        getLogger().info("SWMFramework v1.0.0 已啟用 - 世界管理就緒");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("SWMFramework v1.0.0 已停用");
    }

    public static SWMFrameworkPlugin getInstance() {
        return instance;
    }
}
