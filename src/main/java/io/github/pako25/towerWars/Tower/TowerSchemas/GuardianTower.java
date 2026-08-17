package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Tower.AttackTower;
import io.github.pako25.towerWars.Tower.TowerType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 守卫者塔：直线激光穿透攻击——射程内"与目标连成一线"的所有怪都受伤害。
 *
 * 设计意图：
 * 塔身是叠罗汉的守卫者（按等级叠 1~3 只），专精形态脚下有金/钻底座。
 * 激光判定（isInsideLaser）用点到直线距离判断怪是否在光束内；
 * 专精 1（心灵控制）1/3 概率逼退最靠近的怪倒退 3 秒；
 * 专精 2（疲劳）禁用最靠近怪的特殊技能 5 秒。
 * 每 tick 开局的"清激光"动作原在 Track 里被 instanceof 特判调用，
 * 现移入 {@link #onTick()} 钩子，时机与原版一致（冷却检查之前无条件执行）。
 */
public class GuardianTower extends AttackTower {

    private final int mindControlPrestige = 1;
    private final int fatiguePrestige = 2;
    private Guardian attackingGuardian;
    private ArmorStand tempTarget;
    private ArmorStand armorStand; // 专精底座

    public GuardianTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        Guardian guardian = (Guardian) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.GUARDIAN);
        entities.add(guardian);
        attackingGuardian = guardian;
        guardian.setAI(false);
        // 叠罗汉：2 级叠 2 只，3 级叠 3 只
        if (level > 1 || prestige != 0) {
            Guardian guardian2 = (Guardian) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.GUARDIAN);
            guardian.addPassenger(guardian2);
            entities.add(guardian2);
            guardian2.setAI(false);
            if (level > 2 || prestige != 0) {
                Guardian guardian3 = (Guardian) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.GUARDIAN);
                guardian2.addPassenger(guardian3);
                entities.add(guardian3);
                guardian3.setAI(false);
            }
        }
        if (prestige != 0) {
            armorStand = PrestigeBase.spawn(location, prestige, guardian);
        }
        applyStats(TowerType.GUARDIAN);
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        TWMob targetMob = getClosestToExit(mobSet);

        Location target = targetMob.getEyeLocation();
        Location source = attackingGuardian.getEyeLocation();
        // 激光打的是"全赛道"的怪，不只射程内：光束可以扫到路径转角后更远的怪
        Set<TWMob> allMobs = track.snapshotActiveMobs();

        // 结算激光路径上所有怪，并记下最远的那只为动画端点
        double longestSquaredDistance = 0;
        TWMob animationTarget = null;
        for (TWMob twMob : allMobs) {
            if (isInsideLaser(source, target, twMob.getEyeLocation(), 2)) {
                boolean success = twMob.takeDamage(damage, this, AttackType.LASER);
                if (success) damageDealt += damage;
                double distanceSquared = twMob.getEyeLocation().distanceSquared(attackingGuardian.getEyeLocation());
                if (distanceSquared > longestSquaredDistance) {
                    longestSquaredDistance = distanceSquared;
                    animationTarget = twMob;
                }
            }
        }
        shots++;

        // 没打到任何怪时不播激光动画（原版 assert 会在此时崩服，已改为判空跳过）
        if (animationTarget != null) {
            animateAttack(animationTarget, longestSquaredDistance);
        }

        if (prestige == fatiguePrestige) {
            mobSet.iterator().next().disableSpecialAbility(5);
        }
        if (prestige == mindControlPrestige) {
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                mobSet.iterator().next().getMobNavigation().walkBackwards(3);
            }
        }
        resetCooldown();
    }

    /** 激光动画：在目标位置生成一个隐形盔甲架当"靶子"，让守卫者对它射出真激光 */
    private void animateAttack(TWMob targetMob, double distanceSquared) {
        Vector source = attackingGuardian.getEyeLocation().toVector();
        Vector direction = targetMob.getEyeLocation().toVector();

        direction.subtract(source);
        double directionLenSquared = direction.lengthSquared();

        double s = Math.sqrt(distanceSquared / directionLenSquared);

        Vector scaledVector = direction.clone().multiply(s);
        Vector goal = source.clone().add(scaledVector);

        tempTarget = (ArmorStand) location.getWorld().spawnEntity(goal.toLocation(location.getWorld()), EntityType.ARMOR_STAND);
        tempTarget.setGravity(false);
        tempTarget.setVisible(false);
        tempTarget.setInvulnerable(true);

        attackingGuardian.setLastDamage(0);
        attackingGuardian.setTarget(tempTarget);
        attackingGuardian.setLaser(true);
    }

    /**
     * 每 tick 钩子：清空上一 tick 的激光（Target 指向的盔甲架）。
     * 原版 resetTargeting 在 Track.tickTrack 的 instanceof 分支调用，时机完全一致。
     */
    @Override
    public void onTick() {
        if (tempTarget == null) return;
        attackingGuardian.setLaser(false);
        tempTarget.remove();
        tempTarget = null;
    }

    public void cleanup() {
        if (tempTarget != null) {
            tempTarget.remove();
        }
        if (armorStand != null) {
            armorStand.remove();
        }
    }

    /**
     * 点到直线距离判定：怪的眼部位置是否落在"光源→目标"的光束直径内。
     * 用向量叉积求点到直线距离（标准几何算法），D<0 表示怪在光源后方，不在光束内。
     */
    private boolean isInsideLaser(Location source, Location target, Location testLocation, float diameter) {
        double vx = target.x() - source.x();
        double vy = target.y() - source.y();
        double vz = target.z() - source.z();

        double wx = testLocation.x() - source.x();
        double wy = testLocation.y() - source.y();
        double wz = testLocation.z() - source.z();

        // 点积：怪在光源前方（D>=0）才算命中
        double D = vx * wx + vy * wy + vz * wz;
        if (D < 0) return false;

        // 叉积：||v×w||² / ||v||² = 点到直线距离²
        double cx = vy * wz - vz * wy;
        double cy = vz * wx - vx * wz;
        double cz = vx * wy - vy * wx;
        double crossNormSq = cx * cx + cy * cy + cz * cz;
        double vNormSq = vx * vx + vy * vy + vz * vz;
        double distanceSquared = crossNormSq / vNormSq;
        return distanceSquared < diameter * diameter;
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        int height = level + 1;
        slownessIndicatorLocation = location.clone().add(0, height, 0);
    }
}
