package io.github.pako25.towerWars.util;

/**
 * GUI 背包点击动作枚举。
 *
 * 设计意图：
 * 原版识别"玩家点了哪个按钮"的方式不统一：PlaceTowerInventory 用
 * PersistentDataContainer 存塔数据，UpgradeTowerInventory 却拿物品材质与配置
 * shop_material 比对——配置一改升级菜单立即失效，且玩家手持同名物品也会误触。
 * 本枚举配合 TowerWarsKeys.INVENTORY_ACTION 键，让所有 GUI 物品统一携带
 * "点击后要执行什么动作"的元数据，监听器按动作分发，彻底消除材质比对。
 */
public enum InventoryAction {

    /** 显示塔的射程粒子圈（升级菜单第 9 格） */
    SHOW_RANGE,

    /** 显示塔统计（升级菜单第 10 格） */
    STATS,

    /** 升级塔到下一等级（升级菜单第 13 格） */
    UPGRADE,

    /** 出售塔并退款（升级菜单第 16 格） */
    SELL,

    /** 出售塔以腾出位置换新塔（升级菜单第 17 格） */
    REPLACE,

    /** 进阶为专精 1（升级菜单第 21 格） */
    PRESTIGE_1,

    /** 进阶为专精 2（升级菜单第 23 格） */
    PRESTIGE_2,

    /** 在放置塔菜单中选择了某座塔/某个等级（此时物品还携带 TOWER_TYPE 等键） */
    PLACE_TOWER
}
