package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Tower.ProjectileDespawnListener;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

/**
 * 攻击动画工具：面向目标的转向 + 弹射物发射。
 *
 * 设计意图：
 * 原版在弓箭手/末影人/女巫三座塔里各复制了一份"计算 yaw/pitch → 转身 →
 * 发射"的代码，仅弹射物与粒子不同。本工具把"让塔实体面向目标"这一公共
 * 动作收敛到一处，三座塔的动画只剩"发射什么"这一处差异。
 */
public final class AttackAnimation {

    private AttackAnimation() {
        // 工具类，禁止实例化
    }

    /**
     * 让塔实体面向目标的眼部位置。
     * yaw 由水平方向角 atan2 算出（Bukkit 用角度制），pitch 由垂直分量 asin 算出。
     */
    public static void rotateToFace(Mob creature, TWMob target) {
        Location targetLocation = target.getEyeLocation();
        Location sourceLocation = creature.getEyeLocation();
        Vector dir = targetLocation.toVector().subtract(sourceLocation.toVector()).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.toDegrees(Math.asin(dir.getY()));

        creature.setBodyYaw(yaw);
        creature.setRotation(yaw, pitch);
    }

    /**
     * 向目标发射一支箭（弓箭手专用）：
     * 初速随距离递增（2 + 0.12×距离），并加一个随距离的向上抛物线补偿，
     * 发射后把箭的 UUID 交给 ProjectileDespawnListener，防止箭矢飞出竞技场后残留。
     */
    public static void launchArrow(Mob shooter, TWMob target) {
        Location targetLocation = target.getEyeLocation();
        Location sourceLocation = shooter.getEyeLocation();
        Vector dir = targetLocation.toVector().subtract(sourceLocation.toVector()).normalize();

        double distance = targetLocation.distance(sourceLocation);
        Vector velocity = dir.clone().multiply(2 + 0.12 * distance);
        velocity.setY(velocity.getY() + 0.008 * distance);
        Arrow arrow = shooter.launchProjectile(Arrow.class, velocity);
        arrow.setShooter(shooter);
        ProjectileDespawnListener.getInstance().addEntityUUID(arrow.getUniqueId());
    }
}
