package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 受击时触发的技能接口（如受击瞬移）。
 */
public interface OnHitAbility extends MobAbility {
    void onHit();
}
