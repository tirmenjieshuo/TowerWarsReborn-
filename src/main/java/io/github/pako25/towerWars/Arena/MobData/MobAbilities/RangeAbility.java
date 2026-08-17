package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Tower.Tower;

import java.util.Set;

/**
 * "以自身为中心、按半径找塔"的减益技能基类。
 *
 * 设计意图：
 * 失明（BlindAbility）、减速（SlowAbility）、眩晕（StunAbility）三个技能
 * 原版各复制了一份 getTowersInRange（以怪物位置为中心、按半径扫塔），
 * 这里收敛为共享基类，三个子类只剩"施加哪种减益"这一处差异。
 * 实际距离判定收敛在 Track.getTowersInRange（赛道内所有塔，带过滤谓词）。
 */
public abstract class RangeAbility implements MobAbility {

    protected final int range;
    protected final TWMob twMob;

    protected RangeAbility(int range, TWMob twMob) {
        this.range = range;
        this.twMob = twMob;
    }

    /** 射程内的全部塔（不含过滤，减益技能对任何塔都生效） */
    protected Set<Tower> getTowersInRange() {
        return twMob.getTrack().getTowersInRange(twMob.getLocation(), range, tower -> true);
    }
}
