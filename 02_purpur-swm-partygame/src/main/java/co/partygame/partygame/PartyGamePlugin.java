package co.partygame.partygame;

import co.partygame.framework.SWMFrameworkPlugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.util.logging.Level;

public class PartyGamePlugin extends JavaPlugin {
    private static PartyGamePlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        SWMFrameworkPlugin swmPlugin = (SWMFrameworkPlugin) Bukkit.getPluginManager().getPlugin("SWMFramework");
        if (swmPlugin == null) {
            getLogger().severe("無法找到 SWMFramework 插件！禁用 PartyGame...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("PartyGame 框架已載入 SWM 框架");
        getLogger().info("SWMPartyGame v1.0.0 已啟用");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("SWMPartyGame v1.0.0 已停用");
    }

    public static PartyGamePlugin getInstance() {
        return instance;
    }
}
