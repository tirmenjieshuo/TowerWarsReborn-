package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 击杀无奖励标记（死亡骑士/幽灵）：带此标记的怪被击杀时不发放
 * 金币/收入/击杀统计（作为"强怪代价"的平衡手段——它们要么难杀、
 * 要么打死了也不给你钱）。
 */
public class NoKillBonusesAbility implements MobAbility {

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.NOKILLBONUSES;
    }
}
