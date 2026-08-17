package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * AOE 闪避（狼/兔子/野猫）：以固定概率完全闪避一次溅射（AOE）伤害。
 * 概率：狼 33%、兔子 50%、野猫 100%。
 */
public class AOEDodge implements MobAbility {

    private final float chance;

    public AOEDodge(float chance) {
        this.chance = chance;
    }

    /** 掷骰：返回 true 表示本次 AOE 伤害被闪避 */
    public boolean dodge() {
        return Math.random() < chance;
    }

    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.AOEDODGE;
    }
}
