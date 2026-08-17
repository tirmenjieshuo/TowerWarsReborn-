package io.github.pako25.towerWars.config;

import io.github.pako25.towerWars.CustomConfig;
import io.github.pako25.towerWars.Tower.TowerType;
import io.github.pako25.towerWars.Tower.TowerType.Category;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 塔配置的类型安全访问层（towerConfig.yml）。
 *
 * 设计意图：
 * 原版在 Tower.applyStats、PlaceTowerInventory、UpgradeTowerInventory 等 10+ 处
 * 用 cfg.getString("ARCHER.levels.1.damage") 这种字符串拼接读取，键拼错或配置
 * 缺项时要么静默读 0、要么 NPE 崩服。本类在插件启动时一次性把 towerConfig.yml
 * 解析为强类型对象并做完整性校验（fail-fast），业务代码只面向编译期类型。
 * 读取时"//"分隔的 special 描述在此处就被切为多行列表，消灭隐式契约。
 */
public final class TowerConfig {

    /** 塔定义缓存：枚举 → 解析结果 */
    private static final Map<TowerType, TowerDefinition> TOWERS = new EnumMap<>(TowerType.class);

    private TowerConfig() {
        // 工具类，禁止实例化
    }

    /**
     * 单座塔的完整定义（配置文件中一个大写塔名的全部内容）。
     *
     * @param category   塔类别（normal/support → Category）
     * @param displayName 塔的基础显示名（如 "弓箭手"，不含等级后缀）
     * @param shopMaterial 商店图标材质
     * @param levels     等级 1~N 的数值（support 塔 1~2，normal 塔 1~3）
     * @param prestiges  专精 1~2 的数值（仅 normal 塔有）
     */
    public record TowerDefinition(
            Category category,
            String displayName,
            Material shopMaterial,
            Map<Integer, LevelStats> levels,
            Map<Integer, PrestigeStats> prestiges
    ) {
    }

    /**
     * 普通等级的数值。weakness/slowDuration/slowAmplifier 为可选键，缺省时用默认值，
     * 使旧版配置（无这些键）也能兼容加载。
     */
    public record LevelStats(
            int cost,
            int damage,
            double reload,
            int range,
            int splash,
            double weakness,
            double slowDuration,
            double slowAmplifier,
            List<String> specialLines
    ) {
    }

    /** 专精（威望）数值。stunChance/slowAmplifier 为可选键（MAGMA 专精 2 的眩晕概率、SLIME 专精的减速幅度） */
    public record PrestigeStats(
            String displayName,
            Material shopMaterial,
            int cost,
            int damage,
            double reload,
            int range,
            int splash,
            double stunChance,
            double slowAmplifier,
            List<String> specialLines
    ) {
    }

    /** 由 TowerWars.initialise 在配置初始化之后调用，解析并校验全部塔配置 */
    public static void load(JavaPlugin plugin) {
        TOWERS.clear();
        FileConfiguration cfg = CustomConfig.getFileConfiguration("towerConfig");

        for (TowerType type : TowerType.values()) {
            ConfigurationSection section = cfg.getConfigurationSection(type.name());
            if (section == null) {
                // 启动即失败：缺塔配置意味着游戏数据不完整，静默跳过会让 GUI 少塔且行为漂移
                throw new IllegalStateException("towerConfig.yml 缺少塔配置段: '" + type.name() + "'！请检查配置文件。");
            }
            TOWERS.put(type, parseTower(type, section, plugin));
        }
        plugin.getLogger().info("已加载 " + TOWERS.size() + " 座塔的配置。");
    }

    private static TowerDefinition parseTower(TowerType type, ConfigurationSection section, JavaPlugin plugin) {
        String typeRaw = section.getString("type", "normal");
        Category category;
        try {
            category = Category.valueOf(typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 原版 type.equals("support") 对非法值会 NPE，这里启动即报错并指明修复方向
            throw new IllegalStateException("塔 '" + type.name() + "' 的 type 字段非法: '" + typeRaw + "'（应为 normal 或 support）！");
        }

        String displayName = requireString(section, "name", type.name());
        Material shopMaterial = parseMaterial(requireString(section, "shop_material", type.name()));

        Map<Integer, LevelStats> levels = new java.util.HashMap<>();
        ConfigurationSection levelsSection = section.getConfigurationSection("levels");
        if (levelsSection == null) {
            throw new IllegalStateException("塔 '" + type.name() + "' 缺少 levels 配置段！");
        }
        for (String levelKey : levelsSection.getKeys(false)) {
            ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
            if (levelSection == null) continue;
            try {
                int level = Integer.parseInt(levelKey);
                levels.put(level, new LevelStats(
                        levelSection.getInt("cost"),
                        levelSection.getInt("damage"),
                        levelSection.getDouble("reload"),
                        levelSection.getInt("range"),
                        levelSection.getInt("splash"),
                        levelSection.getDouble("weakness", 0.0),
                        levelSection.getDouble("slow_duration", 4.0),
                        levelSection.getDouble("slow_amplifier", 0.0),
                        splitSpecial(levelSection.getString("special"))
                ));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("塔 '" + type.name() + "' 等级 '" + levelKey + "' 不是合法数字，已跳过。");
            }
        }

        Map<Integer, PrestigeStats> prestiges = new java.util.HashMap<>();
        ConfigurationSection prestigesSection = section.getConfigurationSection("prestiges");
        if (prestigesSection != null) {
            for (String prestigeKey : prestigesSection.getKeys(false)) {
                ConfigurationSection prestigeSection = prestigesSection.getConfigurationSection(prestigeKey);
                if (prestigeSection == null) continue;
                try {
                    int prestige = Integer.parseInt(prestigeKey);
                    prestiges.put(prestige, new PrestigeStats(
                            requireString(prestigeSection, "name", type.name() + " 专精" + prestige),
                            parseMaterial(requireString(prestigeSection, "shop_material", type.name())),
                            prestigeSection.getInt("cost"),
                            prestigeSection.getInt("damage"),
                            prestigeSection.getDouble("reload"),
                            prestigeSection.getInt("range"),
                            prestigeSection.getInt("splash"),
                            prestigeSection.getDouble("stun", 0.0),
                            prestigeSection.getDouble("slow_amplifier", 0.0),
                            splitSpecial(prestigeSection.getString("special"))
                    ));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("塔 '" + type.name() + "' 专精 '" + prestigeKey + "' 不是合法数字，已跳过。");
                }
            }
        }

        // 完整性校验：GUI 与升级逻辑假设 normal 塔有 1~3 级 + 专精 1~2、support 塔有 1~2 级。
        // 缺项时原版会在 GUI 直接 NPE，这里把问题提前到启动期并给出明确提示。
        int expectedLevels = category == Category.NORMAL ? 3 : 2;
        for (int lv = 1; lv <= expectedLevels; lv++) {
            if (!levels.containsKey(lv)) {
                throw new IllegalStateException("塔 '" + type.name() + "' 缺少等级 " + lv + " 的配置！");
            }
        }
        if (category == Category.NORMAL) {
            for (int p = 1; p <= 2; p++) {
                if (!prestiges.containsKey(p)) {
                    throw new IllegalStateException("普通塔 '" + type.name() + "' 缺少专精 " + p + " 的配置！");
                }
            }
        }

        return new TowerDefinition(category, displayName, shopMaterial, levels, prestiges);
    }

    /** 配置里以 "//" 分隔的多行描述 → 逐行列表（原版在 GUI 层做 split，这里提前规范化） */
    private static List<String> splitSpecial(String special) {
        List<String> lines = new ArrayList<>();
        if (special == null || special.isBlank()) return lines;
        for (String line : special.split("//")) {
            lines.add(line.trim());
        }
        return lines;
    }

    private static String requireString(ConfigurationSection section, String key, String towerName) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("塔 '" + towerName + "' 缺少必填字段 '" + key + "'！请检查 towerConfig.yml。");
        }
        return value;
    }

    private static Material parseMaterial(String raw) {
        Material material = Material.matchMaterial(raw, false);
        if (material == null) {
            throw new IllegalStateException("towerConfig.yml 中存在非法材质名: '" + raw + "'！请检查配置。");
        }
        return material;
    }

    // ========== 业务 API（原字符串式读取的替代入口） ==========

    /** 获取某塔某等级的数值（等级不存在时抛异常，杜绝静默 0） */
    public static LevelStats levelStats(TowerType type, int level) {
        LevelStats stats = TOWERS.get(type).levels().get(level);
        if (stats == null) {
            throw new IllegalStateException("塔 '" + type.name() + "' 没有等级 " + level + " 的配置！");
        }
        return stats;
    }

    /** 获取某塔某专精的数值（专精不存在时抛异常） */
    public static PrestigeStats prestigeStats(TowerType type, int prestige) {
        PrestigeStats stats = TOWERS.get(type).prestiges().get(prestige);
        if (stats == null) {
            throw new IllegalStateException("塔 '" + type.name() + "' 没有专精 " + prestige + " 的配置！");
        }
        return stats;
    }

    /** 购买/升级价格：level 为专精等级（4）时取专精价，否则取普通等级价 */
    public static int buyCost(TowerType type, int level, int prestige) {
        return prestige == 0 ? levelStats(type, level).cost() : prestigeStats(type, prestige).cost();
    }

    /** 最大等级：support 塔 2 级，normal 塔 3 级（原版的 type.equals("support") 判定） */
    public static int maxLevel(TowerType type) {
        return type.isSupport() ? 2 : 3;
    }

    /** 是否支援塔（不攻击） */
    public static boolean isSupport(TowerType type) {
        return type.isSupport();
    }

    /** 商店图标材质 */
    public static Material shopMaterial(TowerType type) {
        return TOWERS.get(type).shopMaterial();
    }

    /** 塔的基础显示名（不含等级后缀） */
    public static String displayName(TowerType type) {
        return TOWERS.get(type).displayName();
    }

    /** 全部已解析的塔定义（GUI 遍历用），带枚举键便于写入 PDC */
    public static Iterable<Map.Entry<TowerType, TowerDefinition>> allTowers() {
        return TOWERS.entrySet();
    }

    /** 取单座塔的完整定义 */
    public static TowerDefinition definition(TowerType type) {
        return TOWERS.get(type);
    }
}
