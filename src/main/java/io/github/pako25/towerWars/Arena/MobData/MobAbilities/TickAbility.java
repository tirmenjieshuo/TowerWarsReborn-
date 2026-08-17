package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 周期性触发的技能接口（每 3 秒触发一次，如治疗/减速/失明光环）。
 */
public interface TickAbility extends MobAbility {
    void onTick();
}
