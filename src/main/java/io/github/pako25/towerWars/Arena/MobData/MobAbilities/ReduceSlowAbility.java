package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 减速抵抗（野马）：被施加减速时按系数抵消一部分减速幅度。
 * 野马自带 50% 抗性：外部减速 40% 时实际只吃到 20%。
 */
public class ReduceSlowAbility implements MobAbility {

    private final float amplifier;

    public ReduceSlowAbility(float amplifier) {
        this.amplifier = amplifier;
    }

    /** 抗性系数（0.5 = 抵消一半减速） */
    public float getAmplifier() {
        return amplifier;
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.REDUCEDSLOW;
    }
}
