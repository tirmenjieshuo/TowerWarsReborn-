package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Tower.Tower;

/**
 * 减速光环（黑蜘蛛/蜘蛛骑士）：每 3 秒让射程内所有塔减速——
 * 减速期间塔的攻击冷却走得更慢（amplifier 越大越明显），持续 {duration} 秒。
 * 属于 TickAbility（周期性触发）。
 */
public class SlowAbility extends RangeAbility implements TickAbility {

    private final float amplifier;
    private final int duration;

    public SlowAbility(TWMob twMob, int range, float amplifier, int duration) {
        super(range, twMob);
        this.amplifier = amplifier;
        this.duration = duration;
    }

    @Override
    public void onTick() {
        for (Tower tower : getTowersInRange()) {
            tower.applySlowness(duration, amplifier);
        }
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.TICK;
    }
}
