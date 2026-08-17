package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Tower.AttackTower;
import io.github.pako25.towerWars.Tower.TowerType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 弓箭手塔：远程单体射击，射程长、攻速快。
 *
 * 设计意图：
 * 塔身是一具骷髅，随等级更换皮甲/铁甲（颜色区分专精形态）；
 * 攻击逻辑走 AttackTower 的标准流程，两个专精各自改变"打谁"与"怎么打"：
 * - 专精 1（狙击手）：优先锁定生命高于 50% 的目标（覆写 selectTarget 即可，无需重写攻击流程）；
 * - 专精 2（机枪手）：射速极快（配置 reload 更小），无特殊目标偏好。
 */
public class ArcherTower extends AttackTower {

    private final int sniperPrestige = 1;
    private final int machineGunPrestige = 2;

    public ArcherTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        entities.add((Mob) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.SKELETON));
        entities.forEach(creature -> {
            if (level > 1) {
                EntityEquipment equipment = creature.getEquipment();
                // 按等级/专精决定护甲材质与染色（Lv2 皮甲默认色、Lv3 铁甲、专精 1 黑皮甲、专精 2 黄皮甲）
                ItemStack chestplate = null, leggings = null, boots = null;
                if (level == 2) {
                    chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
                    leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                    boots = new ItemStack(Material.LEATHER_BOOTS);
                }
                if (level == 3) {
                    chestplate = new ItemStack(Material.IRON_CHESTPLATE);
                    leggings = new ItemStack(Material.IRON_LEGGINGS);
                    boots = new ItemStack(Material.IRON_BOOTS);
                }
                if (prestige == sniperPrestige && level == 4) {
                    // 狙击手形态：三件皮革甲统一染黑
                    chestplate = dyeLeather(new ItemStack(Material.LEATHER_CHESTPLATE), Color.BLACK);
                    leggings = dyeLeather(new ItemStack(Material.LEATHER_LEGGINGS), Color.BLACK);
                    boots = dyeLeather(new ItemStack(Material.LEATHER_BOOTS), Color.BLACK);
                }
                if (prestige == machineGunPrestige && level == 4) {
                    // 机枪手形态：三件皮革甲统一染黄
                    chestplate = dyeLeather(new ItemStack(Material.LEATHER_CHESTPLATE), Color.YELLOW);
                    leggings = dyeLeather(new ItemStack(Material.LEATHER_LEGGINGS), Color.YELLOW);
                    boots = dyeLeather(new ItemStack(Material.LEATHER_BOOTS), Color.YELLOW);
                }
                equipment.setChestplate(chestplate);
                equipment.setLeggings(leggings);
                equipment.setBoots(boots);
            }
        });
        applyStats(TowerType.ARCHER);
    }

    /** 皮革护甲染色工具（原版三件套染色代码重复三次，这里收敛为一次） */
    private ItemStack dyeLeather(ItemStack item, Color color) {
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        // 狙击手优先打"生命高于一半"的目标；没有这样的目标时退回普通选择
        Set<TWMob> inAttackRadius = defaultAttackMobs(mobSet);
        for (TWMob mob : inAttackRadius) {
            boolean success = mob.takeDamage(damage, this, AttackType.NORMAL);
            if (success) damageDealt += damage;
            shots++;
        }
        resetCooldown();
    }

    /** 狙击手的目标选择：优先高血量怪（伤害溢出最小化，属于"斩杀前排"策略） */
    @Override
    protected TWMob selectTarget(Set<TWMob> mobSet) {
        if (prestige != sniperPrestige) {
            return super.selectTarget(mobSet);
        }
        Set<TWMob> mobsAboveHalfHP = mobSet.stream()
                .filter(mob -> mob.getHealth() > mob.getMaxHealth() / 2.0)
                .collect(Collectors.toSet());
        if (mobsAboveHalfHP.isEmpty()) {
            return super.selectTarget(mobSet);
        }
        return super.selectTarget(mobsAboveHalfHP);
    }

    public void animateAttack(TWMob mob) {
        Mob creature = entities.iterator().next();
        AttackAnimation.rotateToFace(creature, mob);
        AttackAnimation.launchArrow(creature, mob);
    }

    public void cleanup() {
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        slownessIndicatorLocation = location.clone().add(0, 3, 0);
    }
}
