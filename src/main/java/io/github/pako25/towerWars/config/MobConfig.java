package io.github.pako25.towerWars.config;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.CustomConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 怪物配置的类型安全访问层（mobConfig.yml）。
 *
 * 设计意图：
 * 原版 MobStates.loadDefaultMobStates 直接遍历 YAML 键并用字符串拼接读取，
 * Material.valueOf/EntityType.valueOf 遇非法值即抛异常且无任何上下文提示。
 * 本类在启动时一次性解析全部 28 种怪的配置为强类型 MobDefinition，
 * 配置键与 MobType 枚举一一对应（键名非法启动即报错）。
 */
public final class MobConfig {

    /** 怪物定义缓存：枚举 → 解析结果 */
    private static final Map<MobType, MobDefinition> MOBS = new EnumMap<>(MobType.class);

    private MobConfig() {
        // 工具类，禁止实例化
    }

    /**
     * 单种怪物的完整定义。
     *
     * @param displayName   商店显示名（如 "银蠹虫"）
     * @param category      类别（basic 基础形态 / advanced 进化形态）
     * @param cost          基础召唤价格（进化后按幂次上涨）
     * @param health        基础生命
     * @param speed         移动速度
     * @param income        送怪给对方带来的收入
     * @param entityType    实体类型
     * @param shopMaterial  商店图标材质
     * @param evolution     进化形态（basic 怪才有；null 表示无进化）
     * @param summonable    是否可直接召唤（进化产物 MINI_ZOMBIE 为 false）
     */
    public record MobDefinition(
            String displayName,
            String category,
            int cost,
            int health,
            double speed,
            int income,
            EntityType entityType,
            Material shopMaterial,
            MobType evolution,
            boolean summonable
    ) {
        /** 是否为进化形态（advanced 类别） */
        public boolean isAdvanced() {
            return "advanced".equals(category);
        }
    }

    /** 由 TowerWars.initialise 在配置初始化之后调用 */
    public static void load() {
        MOBS.clear();
        FileConfiguration cfg = CustomConfig.getFileConfiguration("mobConfig");

        for (String key : cfg.getKeys(false)) {
            MobType mobType;
            try {
                mobType = MobType.valueOf(key);
            } catch (IllegalArgumentException e) {
                // 配置里出现枚举之外的键：静默跳过会让某种怪消失，明确报错更安全
                throw new IllegalStateException("mobConfig.yml 中出现未知怪物键: '" + key + "'！请检查配置。");
            }

            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) continue;

            String evolutionRaw = section.getString("evolution");
            MobType evolution = null;
            if (evolutionRaw != null && !evolutionRaw.isBlank()) {
                try {
                    evolution = MobType.valueOf(evolutionRaw);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("mobConfig.yml 中 '" + key + "' 的 evolution 字段非法: '" + evolutionRaw + "'！");
                }
            }

            Material shopMaterial = Material.matchMaterial(section.getString("shop_material"), false);
            if (shopMaterial == null) {
                throw new IllegalStateException("mobConfig.yml 中 '" + key + "' 的 shop_material 非法！");
            }

            EntityType entityType = EntityType.valueOf(section.getString("entity_type"));
            MOBS.put(mobType, new MobDefinition(
                    section.getString("name"),
                    section.getString("category"),
                    section.getInt("cost"),
                    section.getInt("health"),
                    section.getDouble("speed"),
                    section.getInt("income"),
                    entityType,
                    shopMaterial,
                    evolution,
                    !section.getBoolean("unsummonable")
            ));
        }
    }

    /** 取单种怪的配置（配置缺该怪时抛异常，杜绝静默空值） */
    public static MobDefinition definition(MobType mobType) {
        MobDefinition def = MOBS.get(mobType);
        if (def == null) {
            throw new IllegalStateException("mobConfig.yml 缺少怪物 '" + mobType.name() + "' 的配置！");
        }
        return def;
    }

    /** 全部已解析的怪物定义 */
    public static Iterable<Map.Entry<MobType, MobDefinition>> allMobs() {
        return MOBS.entrySet();
    }
}
