package io.github.pako25.towerWars.Tower;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import org.bukkit.Location;

import java.util.Collections;
import java.util.Set;

/**
 * 攻击塔抽象基类：拥有攻击能力的塔（弓箭手/史莱姆/岩浆怪/女巫/末影人/守卫者/特斯拉）。
 *
 * 设计意图：
 * 原版把 attackMobs/animateAttack 声明在 Tower 基类上，纯支援塔（村民）被迫
 * 空实现，抽象方法对它们毫无意义。本类把"攻击"这个概念从 Tower 中拆出，
 * Tower 基类只管塔的通用生命周期（升级/出售/减益），攻击逻辑收敛到本类；
 * 目标选择（selectTarget）做成可覆写钩子，让"狙击高血量"（弓箭手专精 1）、
 * "优先打无虚弱目标"（女巫 2 级）这类差异化目标策略不用重写整个攻击流程。
 */
public abstract class AttackTower extends Tower {

    public AttackTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    /** 攻击射程内所有怪物（具体攻击方式由各塔实现） */
    public abstract void attackMobs(Set<TWMob> mobSet);

    /**
     * 播放一次攻击动画（转向/发射/粒子）。
     * 默认空实现：部分塔不走"单目标动画"流程（守卫者用双参激光动画、
     * 特斯拉的动画在 onTick 里持续驱动），它们无需实现本方法。
     */
    public void animateAttack(TWMob mob) {
        // 空默认实现
    }

    /**
     * 目标选择钩子：从射程内怪物中挑出本塔要打的头号目标。
     * 默认选"离终点最近"的怪（威胁最大者优先处理）；
     * 需要差异化目标策略的塔（弓箭手狙击、女巫虚弱优先）覆写此方法。
     */
    protected TWMob selectTarget(Set<TWMob> mobSet) {
        return getClosestToExit(mobSet);
    }

    /**
     * 标准攻击流程模板方法：
     * 选目标 → 按溅射半径扩展命中集合 → 播攻击动画 → 返回实际受伤的怪集合。
     * 各塔的 attackMobs 复用本方法得到"打谁"，再自行结算伤害与特效。
     */
    public Set<TWMob> defaultAttackMobs(Set<TWMob> mobSet) {
        TWMob target = selectTarget(mobSet);
        // 溅射>0 时攻击目标周围 splash 半径内全体；否则只打单体目标
        Set<TWMob> inAttackRadius;
        if (splash > 0) {
            inAttackRadius = track.getMobsInRange(target.getLocation(), splash);
        } else {
            inAttackRadius = Collections.singleton(target);
        }
        animateAttack(target);
        return inAttackRadius;
    }

    /** 选出射程内离终点（路径尽头）最近的怪；pathLeft==0 的怪（已到终点）不参与选择 */
    protected TWMob getClosestToExit(Set<TWMob> mobSet) {
        double minPathLeft = 0;
        boolean first = true;
        TWMob target = mobSet.iterator().next();
        for (TWMob mob : mobSet) {
            double pathLeft = mob.getMobNavigation().getPathLeft();
            if ((pathLeft < minPathLeft || first) && pathLeft != 0) {
                minPathLeft = pathLeft;
                target = mob;
                first = false;
            }
        }
        return target;
    }
}
