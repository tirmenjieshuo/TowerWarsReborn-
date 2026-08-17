package io.github.pako25.towerWars.Tower.TowerSchemas;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;

import java.util.List;

/**
 * 叠罗汉实体生成工具：史莱姆/岩浆怪塔的造型都是"底层大史莱姆 + 乘客小史莱姆"。
 *
 * 设计意图：
 * 岩浆怪塔与史莱姆塔的生成代码原本逐行相同（只差实体类型与每层尺寸），
 * 这里收敛为一个参数化方法：指定底层尺寸与各乘客层尺寸即可生成整座"塔身"。
 * 利用了 Bukkit 中 MagmaCube 继承 Slime 的特性，setSize 对两者通用。
 */
public final class StackedEntities {

    private StackedEntities() {
        // 工具类，禁止实例化
    }

    /**
     * 生成一摞史莱姆/岩浆怪：底层在最下面，乘客层按顺序骑在前一层背上。
     *
     * @param location       塔基座位置（内部 +0.5,+1,+0.5 对齐方块中心）
     * @param type           实体类型（SLIME 或 MAGMA_CUBE）
     * @param baseSize       底层尺寸
     * @param passengerSizes 各乘客层尺寸（空列表 = 只有底层）
     * @return 底层实体（塔的主实体）
     */
    public static Mob spawnStack(Location location, EntityType type, int baseSize, List<Integer> passengerSizes) {
        Mob base = (Mob) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), type);
        ((Slime) base).setSize(baseSize);
        Mob carrier = base;
        for (int size : passengerSizes) {
            Mob passenger = (Mob) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), type);
            ((Slime) passenger).setSize(size);
            carrier.addPassenger(passenger);
            carrier = passenger;
        }
        return base;
    }
}
