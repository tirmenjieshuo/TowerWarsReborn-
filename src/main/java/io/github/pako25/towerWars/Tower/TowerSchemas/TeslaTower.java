package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Tower.AttackTower;
import io.github.pako25.towerWars.Tower.TowerType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 特斯拉塔：锁定"离终点最远"的怪持续电击，同一目标的伤害逐次叠加。
 *
 * 设计意图：
 * 塔身是 4 只苦力怕（主攻 + 隐形动画替身 + 两侧装饰），通电视觉靠
 * setPowered 切换实现。玩法核心是 targetLock（锁定的目标）：只要目标
 * 还在射程内，伤害倍率持续叠加（普通/光束最高 200%，闪电专精 300% 且
 * 目标换人也不清零）。
 * 原版把持续动画放在 Tower.isOnCooldown 里用 instanceof 特判驱动，
 * 现迁入 {@link #onTick()} 钩子（每 tick 无条件调用，时机更干净）；
 * "射程内无怪"的倍率清零迁入 {@link #onNothingInRange()} 钩子。
 */
public class TeslaTower extends AttackTower {

    private Creeper attackingCreeper;
    private final int beamPrestige = 1;
    private final int lightningPrestige = 2;
    /** 锁定的目标：不换人直到它离开射程或死亡 */
    private TWMob targetLock;
    private float damageMultiplier = 0;
    private Creeper animationCreeper;
    private int lastRandomAnimationOffsetX = 0;
    private int lastRandomAnimationOffsetZ = 0;
    private ArmorStand armorStand; // 专精底座

    public TeslaTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        attackingCreeper = (Creeper) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.CREEPER);
        entities.add(attackingCreeper);
        // 动画替身：平时隐形，电击时现身并传送到目标身边
        animationCreeper = (Creeper) location.getWorld().spawnEntity(location, EntityType.CREEPER);
        animationCreeper.setAI(false);
        animationCreeper.setInvisible(true);
        entities.add(animationCreeper);

        Creeper creeper4 = null;
        if (level > 1 || prestige != 0) {
            // 两侧装饰苦力怕
            Creeper creeper2 = (Creeper) location.getWorld().spawnEntity(location.clone().add(0, 1, 0.5), EntityType.CREEPER);
            Creeper creeper3 = (Creeper) location.getWorld().spawnEntity(location.clone().add(1, 1, 0.5), EntityType.CREEPER);
            entities.add(creeper2);
            entities.add(creeper3);
            if (level == 3 || prestige != 0) {
                creeper4 = (Creeper) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.CREEPER);
                attackingCreeper.addPassenger(creeper4);
                entities.add(creeper4);
            }
        }

        if (prestige != 0) {
            armorStand = PrestigeBase.spawn(location, prestige, attackingCreeper);
            if (creeper4 != null) {
                attackingCreeper.addPassenger(creeper4);
            }
        }
        applyStats(TowerType.TESLA);
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        if (targetLock == null || !mobSet.contains(targetLock)) {
            // 重新锁定"离终点最远"的怪
            double maxPathLeft = 0;
            TWMob target = null;
            for (TWMob mob : mobSet) {
                double pathLeft = mob.getMobNavigation().getPathLeft();
                if (pathLeft > maxPathLeft) {
                    maxPathLeft = pathLeft;
                    target = mob;
                }
            }
            targetLock = target;
            // 只有闪电专精在换目标时保留倍率，其他形态换目标即清零
            if (prestige != lightningPrestige) {
                damageMultiplier = 0;
            }
        }

        boolean success = targetLock.takeDamage((int) (damage * (1 + damageMultiplier)), this, AttackType.NORMAL);
        if (success) damageDealt += (int) (damage * (1 + damageMultiplier));
        shots++;

        // 每次命中叠加倍率，上限因形态而异（普通 200%、光束无上限、闪电 300%）
        if (prestige == 0) {
            if (damageMultiplier < 2) {
                damageMultiplier = damageMultiplier + 0.5F;
            }
        }
        if (prestige == beamPrestige) {
            targetLock.applySlowness(0.3F, (int) Math.ceil(reload));
            damageMultiplier = damageMultiplier + 0.5F;
        }
        if (prestige == lightningPrestige) {
            if (damageMultiplier < 3) {
                damageMultiplier = damageMultiplier + 0.5F;
            }
        }

        resetCooldown();
    }

    /**
     * 每 tick 钩子：驱动"通电动画"——锁定目标时全体通电、动画替身现身电击；
     * 目标失效时全体复位。
     */
    @Override
    public void onTick() {
        if (targetLock == null || !targetLock.isAlive()) {
            // 无目标：全部复位（关电、隐藏替身）
            for (Entity entity : entities) {
                if (entity instanceof Creeper creeper) {
                    creeper.setPowered(false);
                }
            }
            attackingCreeper.setInvisible(false);
            animationCreeper.setInvisible(true);
            animationCreeper.setPowered(false);
            return;
        }
        // 有目标：全体通电，主攻藏起来，替身现身到目标头顶附近
        for (Entity entity : entities) {
            if (entity instanceof Creeper creeper) {
                creeper.setPowered(true);
            }
        }
        attackingCreeper.setInvisible(true);
        attackingCreeper.setPowered(false);
        animationCreeper.setInvisible(false);
        animationCreeper.setPowered(true);

        // 替身位置：目标附近随机一格偏移（闪电落点感），且与上一 tick 的偏移不同
        Location targetLocation = targetLock.getEyeLocation().clone();
        int offsetX = ThreadLocalRandom.current().nextInt(-1, 2);
        int offsetZ = ThreadLocalRandom.current().nextInt(-1, 2);
        while (offsetX == lastRandomAnimationOffsetX) {
            offsetX = ThreadLocalRandom.current().nextInt(-1, 2);
        }
        while (offsetZ == lastRandomAnimationOffsetZ) {
            offsetZ = ThreadLocalRandom.current().nextInt(-1, 2);
        }
        lastRandomAnimationOffsetX = offsetX;
        lastRandomAnimationOffsetZ = offsetZ;
        targetLocation.add(offsetX, -0.5, offsetZ);
        animationCreeper.teleport(targetLocation);
    }

    /** 射程内无怪：清零倍率并解除锁定（目标跑了，蓄力作废） */
    @Override
    public void onNothingInRange() {
        damageMultiplier = 0;
        targetLock = null;
    }

    public void cleanup() {
        animationCreeper.remove();
        if (armorStand != null) {
            armorStand.remove();
        }
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        int height = 3;
        if (level == 3) height = 5;
        if (level == 4) height = 5;
        slownessIndicatorLocation = location.clone().add(0, height, 0);
    }
}
