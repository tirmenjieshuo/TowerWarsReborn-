package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 死亡时触发的技能接口。
 *
 * @return true = 接管这次死亡（取消 despawn，如分裂/重生）；false = 正常死亡
 */
public interface DeathAbility extends MobAbility {
    boolean onDeath(boolean killed);
}
