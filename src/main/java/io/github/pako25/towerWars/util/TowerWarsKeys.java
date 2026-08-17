package io.github.pako25.towerWars.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * 插件持久化数据（PersistentDataContainer）键常量。
 *
 * 设计意图：
 * 原版代码在每次点击 GUI 时现场 new NamespacedKey（PlaceTowerInventory、
 * SummonMobInventory、InventoryClickListener 共 6 处），既浪费又容易拼错键名。
 * 本类在插件启动时统一初始化一次，全库复用同一批键实例。
 * 键名保持英文是为了与旧存档兼容（PDC 数据跨版本读取），改动会导致旧物品数据丢失。
 */
public final class TowerWarsKeys {

    private TowerWarsKeys() {
        // 工具类，禁止实例化
    }

    /** 塔类型键：GUI 物品上记录的 TowerType 名称（字符串） */
    public static NamespacedKey TOWER_TYPE;

    /** 塔等级键：GUI 物品上记录的等级（整数） */
    public static NamespacedKey TOWER_LEVEL;

    /** 塔专精（威望）键：GUI 物品上记录的专精序号（整数，0 = 无专精） */
    public static NamespacedKey TOWER_PRESTIGE;

    /** 怪物类型键：召唤菜单物品上记录的 MobType 名称（字符串） */
    public static NamespacedKey MOB_TYPE;

    /**
     * 背包动作键：GUI 物品上记录的点击动作（InventoryAction 名称）。
     * 统一"点击识别"机制——原版 UpgradeTowerInventory 用物品材质比对识别点击，
     * PlaceTowerInventory 用 PDC，两套机制混用且升级菜单在材质被配置改动后即失效。
     */
    public static NamespacedKey INVENTORY_ACTION;

    /** 必须由插件启动流程调用一次（TowerWars.initialise 内），之后所有常量方可使用 */
    public static void init(Plugin plugin) {
        TOWER_TYPE = new NamespacedKey(plugin, "towerType");
        TOWER_LEVEL = new NamespacedKey(plugin, "towerLevel");
        TOWER_PRESTIGE = new NamespacedKey(plugin, "towerPrestige");
        MOB_TYPE = new NamespacedKey(plugin, "mobType");
        INVENTORY_ACTION = new NamespacedKey(plugin, "inventoryAction");
    }
}
