package io.github.pako25.towerWars.Tower;

import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Tower.TowerSchemas.ArcherTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.EndermanTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.GuardianTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.MagmaTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.SlimeTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.TeslaTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.VillagerTower;
import io.github.pako25.towerWars.Tower.TowerSchemas.WitchTower;
import org.bukkit.Location;

/**
 * 塔类型枚举。
 *
 * 设计意图：
 * 原版通过 towerConfig.yml 里的字符串 "normal"/"support" 判定塔的种类（3 处
 * 散落的 type.equals("support")），字符串拼写漂移会让 GUI 直接 NPE。
 * 本枚举用 Category 把"塔的种类"固化为编译期类型，配置加载时校验并映射。
 */
public enum TowerType {

    /** 弓箭手：远程单体射击，专精后可狙击高血量怪 */
    ARCHER(Category.NORMAL),
    /** 史莱姆：溅射伤害 + 减速 */
    SLIME(Category.NORMAL),
    /** 岩浆怪：溅射伤害，专精后点燃/眩晕 */
    MAGMA(Category.NORMAL),
    /** 女巫：魔法伤害 + 虚弱（支援系却带攻击能力） */
    WITCH(Category.SUPPORT),
    /** 末影人：把怪传送回出生点（支援系） */
    ENDERMAN(Category.SUPPORT),
    /** 村民：给附近塔上减益免疫/攻速加成（纯支援） */
    VILLAGER(Category.SUPPORT),
    /** 守卫者：直线激光穿透攻击 */
    GUARDIAN(Category.NORMAL),
    /** 特斯拉：锁定最远怪并叠加伤害 */
    TESLA(Category.NORMAL);

    /** 塔的类别：决定最大等级（普通 3 级 + 专精，支援 2 级）与是否参与攻击 */
    public enum Category {
        NORMAL,
        SUPPORT
    }

    private final Category category;

    TowerType(Category category) {
        this.category = category;
    }

    /** 该塔所属类别（原版的 type 字符串字段） */
    public Category getCategory() {
        return category;
    }

    /** 是否为支援塔（不攻击、无攻击动画） */
    public boolean isSupport() {
        return category == Category.SUPPORT;
    }

    /**
     * 塔工厂：按类型实例化对应塔实现。
     * 设计意图：原版该 switch 散落在 Tower.summonTower 静态方法里，移到枚举上
     * 让"类型 → 实现类"的映射与类型定义同处一地，新增塔只需改这一处。
     */
    public Tower create(Location location, int level, int prestige, Track track) {
        return switch (this) {
            case ARCHER -> new ArcherTower(location, level, prestige, track);
            case SLIME -> new SlimeTower(location, level, prestige, track);
            case MAGMA -> new MagmaTower(location, level, prestige, track);
            case WITCH -> new WitchTower(location, level, prestige, track);
            case ENDERMAN -> new EndermanTower(location, level, prestige, track);
            case VILLAGER -> new VillagerTower(location, level, prestige, track);
            case GUARDIAN -> new GuardianTower(location, level, prestige, track);
            case TESLA -> new TeslaTower(location, level, prestige, track);
        };
    }
}
