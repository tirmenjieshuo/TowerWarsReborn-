package io.github.pako25.towerWars.Tower.TowerSchemas;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * 专精底座特效工具：专精形态的塔脚下会出现一个隐形盔甲架，
 * 头顶顶一块金块（专精 1）/钻石块（专精 2），塔身实体骑在底座上。
 *
 * 设计意图：
 * 守卫者塔与特斯拉塔各复制了一份完全相同的底座代码（生成隐形盔甲架 +
 * 按专精选金/钻块 + 让塔身骑上去），这里收敛为一处，两塔各留一行调用。
 */
public final class PrestigeBase {

    private PrestigeBase() {
        // 工具类，禁止实例化
    }

    /** 生成专精底座并让塔身实体骑上去，返回底座供 cleanup 时移除 */
    public static ArmorStand spawn(Location location, int prestige, Mob rider) {
        ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location.clone().add(0.5, -0.3, 0.5), EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setInvulnerable(true);
        // 专精 1 = 金块，专精 2 = 钻石块（区分两形态的视觉语言）
        ItemStack block = prestige == 1 ? new ItemStack(Material.GOLD_BLOCK) : new ItemStack(Material.DIAMOND_BLOCK);
        EntityEquipment entityEquipment = armorStand.getEquipment();
        entityEquipment.setHelmet(block);
        armorStand.addPassenger(rider);
        return armorStand;
    }
}
