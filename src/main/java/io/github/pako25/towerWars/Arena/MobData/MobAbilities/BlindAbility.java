package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Tower.Tower;

/**
 * 失明光环（鱿鱼）：每 3 秒让射程内所有塔陷入"失明"——
 * 塔每次攻击都有概率落空（冷却被白白重置），持续 {duration} 秒。
 * 属于 TickAbility（周期性触发）。
 */
public class BlindAbility extends RangeAbility implements TickAbility {

    private final float amplifier;
    private final int duration;

    public BlindAbility(int range, float amplifier, int duration, TWMob twMob) {
        super(range, twMob);
        this.amplifier = amplifier;
        this.duration = duration;
    }

    @Override
    public void onTick() {
        for (Tower tower : getTowersInRange()) {
            tower.applyBlindness(duration, amplifier);
        }
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.TICK;
    }
}
