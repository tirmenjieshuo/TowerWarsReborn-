package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Player.Listeners.EndermanTeleportListener;
import io.github.pako25.towerWars.Tower.AttackTower;
import io.github.pako25.towerWars.Tower.ParticleTrail;
import io.github.pako25.towerWars.Tower.TowerType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.util.Set;

/**
 * 末影人塔（支援系）：把目标怪物瞬间传送回它的出生点，制造"回头路"。
 *
 * 设计意图：
 * 塔身是一只末影人，注册到 EndermanTeleportListener 黑名单——普通末影人
 * 会自然随机传送，而塔的传送是玩法核心，必须禁止它自己乱传。
 * 攻击 = 传送怪物回出生点（相当于给它强制回城），2 级附带 10% 当前生命伤害。
 * 冷却极长（60/45 秒），因为"回城"效果本身非常强力。
 */
public class EndermanTower extends AttackTower {

    public EndermanTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        Mob enderman = (Mob) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.ENDERMAN);
        entities.add(enderman);
        EndermanTeleportListener.getListener().addEntityUUID(enderman.getUniqueId());
        applyStats(TowerType.ENDERMAN);
    }

    @Override
    public void attackMobs(Set<TWMob> mobSet) {
        TWMob target = getClosestToExit(mobSet);
        if (target.isWalkingBackwards()) {
            // 该怪已在"倒退"状态（守卫者专精 1 的逼退效果），本 tick 放过它，
            // 下一 tick 自然会重新选目标——否则传送+倒退会互相打架
            return;
        }
        target.getMobNavigation().teleportBack();
        if (level == 2) {
            // 10% 当前生命伤害：必须在 takeDamage 之前缓存伤害值，
            // 否则怪被击杀后 getHealth() 读到 0，统计会算出负数（原版 bug，已修复）
            int damage = (int) (target.getHealth() * 0.1);
            boolean success = target.takeDamage(damage, this, AttackType.MAGIC);
            if (success) damageDealt += damage;
        }
        shots++;
        animateAttack(target);
        resetCooldown();
    }

    public void animateAttack(TWMob mob) {
        Mob creature = entities.iterator().next();
        AttackAnimation.rotateToFace(creature, mob);
        ParticleTrail.spawnParticleTrail(creature.getEyeLocation(), mob.getEyeLocation(), 0.8, 5, Particle.PORTAL);
    }

    public void cleanup() {
        EndermanTeleportListener.getListener().removeEntityUUID(entities.iterator().next().getUniqueId());
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        slownessIndicatorLocation = location.clone().add(0, 4, 0);
    }
}
