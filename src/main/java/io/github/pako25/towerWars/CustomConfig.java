package io.github.pako25.towerWars;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 底层 YAML 配置加载工具。
 *
 * 设计意图：
 * 这是全插件唯一的"文件 → FileConfiguration"入口：按配置名（不带 .yml 后缀）
 * 从插件数据目录加载，首次加载时把 jar 内的同名资源复制出来，并始终以 jar 内
 * 资源作为默认值合并（copyDefaults）——这样用户删掉某个键后会自动补回默认值。
 *
 * 注意：本类只是"读文件"的底层工具，业务数据的类型安全读取请使用
 * config 包的 TowerConfig / MobConfig / ArenaConfig。
 */
public class CustomConfig {

    /** 已加载配置的注册表：配置名 → 实例（每个配置全插件只有一份） */
    private static final Map<String, CustomConfig> customConfigMap = new HashMap<>();

    private final File file;
    private FileConfiguration customFile;
    private final JavaPlugin plugin;
    private final String configFilePath;

    private CustomConfig(String configFileName) {
        this.plugin = TowerWars.getPlugin();
        this.configFilePath = configFileName + ".yml";
        file = new File(plugin.getDataFolder(), configFilePath);

        // 数据目录里没有该文件时：jar 里有资源就导出，否则新建空文件（如 managedSigns）
        if (!file.exists()) {
            InputStream resourceStream = plugin.getResource(configFilePath);
            if (resourceStream != null) {
                plugin.saveResource(configFilePath, false);
            } else {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("无法创建空配置文件: " + e.getMessage());
                }
            }
        }

        customFile = YamlConfiguration.loadConfiguration(file);

        // 从 jar 资源加载默认值，保证文件里缺失的键自动补默认值
        applyDefaultsFromResource();

        save();
    }

    /** 从 jar 内资源合并默认值到已加载配置（配置里已有的键优先，缺失键补默认） */
    private void applyDefaultsFromResource() {
        try (InputStream defaultStream = plugin.getResource(configFilePath)) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                customFile.setDefaults(defaultConfig);
                customFile.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("无法为 " + configFilePath + " 加载默认值: " + e.getMessage());
        }
    }

    /**
     * 获取已加载配置的 FileConfiguration。
     * 未 setup 的配置名会直接抛异常——调用方必须确保在 TowerWars.initialiseConfigs
     * 中 setup 过，否则是编程错误，与其返回 null 让后续 NPE，不如现在就点明。
     */
    public static FileConfiguration getFileConfiguration(String configFileName) {
        CustomConfig customConfig = customConfigMap.get(configFileName);
        if (customConfig == null) {
            throw new IllegalStateException("配置 '" + configFileName + "' 尚未加载！请先在 TowerWars.initialiseConfigs 中 setup。");
        }
        return customConfig.getCustomFile();
    }

    public static CustomConfig getCustomConfig(String configFileName) {
        return customConfigMap.get(configFileName);
    }

    /** 注册并加载一个配置；重复 setup 视为编程错误，仅重载并打警告 */
    public static void setup(String configFileName) {
        if (customConfigMap.containsKey(configFileName)) {
            pluginWarn("配置 '" + configFileName + "' 重复 setup！仅执行重载。");
            customConfigMap.get(configFileName).reload();
        } else {
            CustomConfig customConfig = new CustomConfig(configFileName);
            customConfigMap.put(configFileName, customConfig);
        }
    }

    private static void pluginWarn(String message) {
        TowerWars.getPlugin().getLogger().warning(message);
    }

    public FileConfiguration getCustomFile() {
        return customFile;
    }

    /** 保存到磁盘后立即重载，使内存中的 FileConfiguration 与文件一致 */
    public void save() {
        try {
            customFile.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        reload();
    }

    public void reload() {
        customFile = YamlConfiguration.loadConfiguration(file);
        applyDefaultsFromResource();
    }
}
