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

/**
 * 史莱姆塔：溅射伤害 + 减速（史莱姆黏液本来就滑）。
 *
 * 设计意图：
 * 塔身与岩浆怪塔共用 StackedEntities（两塔造型代码原版逐行相同）。
 * 减速数值（时长/幅度）原版硬编码在代码里，与配置的 special 文案是两套
 * 信息源；重构后从 towerConfig 的 slow_duration / slow_amplifier 读取，
 * 缺键时回退默认值（4 秒），旧配置零迁移成本。
 */
public class SlimeTower extends AttackTower {

    private final int stickyTowerPrestige = 1;
    private final int bigSlimePrestige = 2;

    public SlimeTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        Mob slime;
        if (prestige == 0) {
            // 普通形态：底层 size 2，2 级叠 1 只小史莱姆，3 级叠 3 只
            List<Integer> passengers = switch (level) {
                case 2 -> List.of(1);
                case 3 -> List.of(1, 1, 1);
                default -> List.of();
            };
            slime = StackedEntities.spawnStack(location, EntityType.SLIME, 2, passengers);
        } else if (prestige == stickyTowerPrestige) {
            // 粘性塔：4 只 size 2 的史莱姆叠罗汉
            slime = StackedEntities.spawnStack(location, EntityType.SLIME, 2, List.of(2, 2, 2));
        } else {
            // 巨型史莱姆：底层升级为 size 3
            slime = StackedEntities.spawnStack(location, EntityType.SLIME, 3, List.of());
        }
        entities.add(slime);
        applyStats(TowerType.SLIME);
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        // 减速数值从配置读取（默认 4 秒、幅度按形态等级分档，与原有硬编码一致）
        TowerConfig.LevelStats stats = TowerConfig.levelStats(TowerType.SLIME, level);
        int duration = (int) stats.slowDuration();
        float amplifier = (float) stats.slowAmplifier();
        if (prestige == stickyTowerPrestige) {
            amplifier = (float) TowerConfig.prestigeStats(TowerType.SLIME, stickyTowerPrestige).slowAmplifier();
        } else if (prestige == bigSlimePrestige) {
            amplifier = (float) TowerConfig.prestigeStats(TowerType.SLIME, bigSlimePrestige).slowAmplifier();
        }

        Set<TWMob> inAttackRadius = defaultAttackMobs(mobSet);
        for (TWMob mob : inAttackRadius) {
            boolean success = mob.takeDamage(damage, this, AttackType.AOE);
            if (success) damageDealt += damage;
            mob.applySlowness(amplifier, duration);
        }
        shots++;
        resetCooldown();
    }

    public void animateAttack(TWMob mob) {
        Location source = location.clone();
        Location goal = mob.getEyeLocation();
        ParticleTrail.spawnParticleTrail(source, goal, 1, 5, Particle.WHITE_SMOKE);
    }

    public void cleanup() {
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        int height = 3;
        if (level == 3) height = 4;
        if (prestige == stickyTowerPrestige) height = 5;
        slownessIndicatorLocation = location.clone().add(0, height, 0);
    }
}
