package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;

/**
 * 到点重生（死亡骑士）：走到终点被"漏过去"时不会离场——
 * 传回出生点继续走（onDeath 返回 true 取消 despawn）。
 * 注意：被塔击杀时正常死亡（killed=true 不触发重生）。
 */
public class RespawnAbility implements DeathAbility {

    private final TWMob twMob;

    public RespawnAbility(TWMob twMob) {
        this.twMob = twMob;
    }

    @Override
    public boolean onDeath(boolean killed) {
        if (!killed) {
            twMob.getMobNavigation().teleportBack();
            return true; // 取消离场：回城重走
        }
        return false;
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.DEATH;
    }
}
