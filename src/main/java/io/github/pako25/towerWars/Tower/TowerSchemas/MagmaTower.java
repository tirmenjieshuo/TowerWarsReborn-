package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Tower.AttackTower;
import io.github.pako25.towerWars.Tower.ParticleTrail;
import io.github.pako25.towerWars.Tower.TowerType;
import io.github.pako25.towerWars.config.TowerConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 岩浆怪塔：溅射伤害，专精后附带灼烧/眩晕。
 *
 * 设计意图：
 * 塔身由叠罗汉的岩浆怪构成（造型逻辑与史莱姆塔共用 StackedEntities）。
 * 技能数值读取 towerConfig：
 * - 专精 1（炼狱塔）点燃怪 5 秒（每秒 5% 掉血）；
 * - 专精 2（火山）按配置的 stun 概率（0.05 = 5%）施放真眩晕。
 * 原版把"低概率眩晕"实现成了 25% 概率减速（配置 stun 键从未被读、special
 * 文案却说眩晕），与描述不符；重构后按配置实现真眩晕（applyStun 已加入
 * TWMob），行为与文案一致。减速效果保留但仅限专精 1 的灼烧路径之外的默认逻辑。
 */
public class MagmaTower extends AttackTower {

    private final int infernalTowerPrestige = 1;
    private final int volcanoPrestige = 2;

    public MagmaTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        Mob magma;
        if (prestige == 0) {
            // 普通形态：底层 size 2，2 级叠 1 只小岩浆怪，3 级叠 3 只
            List<Integer> passengers = switch (level) {
                case 2 -> List.of(1);
                case 3 -> List.of(1, 1, 1);
                default -> List.of();
            };
            magma = StackedEntities.spawnStack(location, EntityType.MAGMA_CUBE, 2, passengers);
        } else if (prestige == infernalTowerPrestige) {
            // 炼狱塔：4 只 size 2 的岩浆怪叠罗汉
            magma = StackedEntities.spawnStack(location, EntityType.MAGMA_CUBE, 2, List.of(2, 2, 2));
        } else {
            // 火山：底层直接升级为 size 3
            magma = StackedEntities.spawnStack(location, EntityType.MAGMA_CUBE, 3, List.of());
        }
        entities.add(magma);
        applyStats(TowerType.MAGMA);
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        Set<TWMob> inAttackRadius = defaultAttackMobs(mobSet);
        for (TWMob mob : inAttackRadius) {
            boolean success = mob.takeDamage(damage, this, AttackType.AOE);
            if (success) damageDealt += damage;
            if (prestige == infernalTowerPrestige) {
                mob.applyBurn(5);
            }
            if (prestige == volcanoPrestige) {
                // 眩晕概率从配置读取（0.05 = 5%）
                double stunChance = TowerConfig.prestigeStats(TowerType.MAGMA, volcanoPrestige).stunChance();
                if (ThreadLocalRandom.current().nextDouble() < stunChance) {
                    mob.applyStun(2);
                }
            }
        }
        shots++;
        resetCooldown();
    }

    public void animateAttack(TWMob mob) {
        Location source = location.clone();
        Location goal = mob.getEyeLocation();
        ParticleTrail.spawnParticleTrail(source, goal, 1, 5, Particle.SMOKE);
    }

    public void cleanup() {
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        int height = 3;
        if (level == 3) height = 4;
        if (prestige == infernalTowerPrestige) height = 5;
        slownessIndicatorLocation = location.clone().add(0, height, 0);
    }
}
