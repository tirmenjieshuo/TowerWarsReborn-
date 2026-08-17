package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;

/**
 * 伤害吸收（僵尸系/幽灵）：替附近队友按比例挡掉伤害。
 * 比例：皮甲僵尸 25%、金甲 60%、钻甲 80%、幽灵 100%（幽灵=全挡）。
 * 死亡逻辑见 TWMob.absorbDamage：吸收者血量不够时替队友而死。
 */
public class DamageAbsorber implements MobAbility {

    private final TWMob twMob;
    private final float part;

    public DamageAbsorber(float part, TWMob twMob) {
        this.twMob = twMob;
        this.part = part;
    }

    /** 本次应吸收的伤害量（伤害 × 比例） */
    public int absorbDamage(int damage) {
        return (int) (damage * part);
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.ABSORBER;
    }
}
