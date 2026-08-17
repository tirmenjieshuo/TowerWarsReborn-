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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 女巫塔（支援系）：魔法伤害 + 施加"虚弱"减益（受伤放大）。
 *
 * 设计意图：
 * 虚弱数值原版硬编码在代码里（0.4/0.8），而 towerConfig.yml 的 weakness 键
 * 从未被读取——配置形同虚设。重构后虚弱值从 TowerConfig 读取（修复该 bug），
 * 配置与代码只有一个事实来源。
 * 2 级的目标策略"优先打还没有虚弱的怪"通过覆写 selectTarget 实现，
 * 无需重写攻击流程。
 */
public class WitchTower extends AttackTower {

    public WitchTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        entities.add((Mob) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.WITCH));
        applyStats(TowerType.WITCH);
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        Set<TWMob> inAttackRadius = defaultAttackMobs(mobSet);
        if (inAttackRadius == null) return; // 防御：选目标失败（空集）时直接跳过
        for (TWMob mob : inAttackRadius) {
            boolean success = mob.takeDamage(damage, this, AttackType.MAGIC);
            if (success) damageDealt += damage;
            // 虚弱倍率从配置读取（0.40/0.80），持续 10 秒
            mob.applyWeakness((float) TowerConfig.levelStats(TowerType.WITCH, level).weakness(), 10);
        }
        shots++;
        resetCooldown();
    }

    /** 2 级优先攻击"未被虚弱覆盖"的怪，让虚弱尽快铺开（伤害最大化） */
    @Override
    protected TWMob selectTarget(Set<TWMob> mobSet) {
        if (level != 2) {
            return super.selectTarget(mobSet);
        }
        Set<TWMob> mobsWithoutWeakness = mobSet.stream()
                .filter(mob -> !mob.hasWeakness())
                .collect(Collectors.toSet());
        if (mobsWithoutWeakness.isEmpty()) {
            return super.selectTarget(mobSet);
        }
        return super.selectTarget(mobsWithoutWeakness);
    }

    public void animateAttack(TWMob mob) {
        Mob creature = entities.iterator().next();
        AttackAnimation.rotateToFace(creature, mob);
        ParticleTrail.spawnParticleTrail(creature.getEyeLocation(), mob.getEyeLocation(), 0.8, 5, Particle.ENCHANT);
    }

    public void cleanup() {
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        slownessIndicatorLocation = location.clone().add(0, 4, 0);
    }
}
