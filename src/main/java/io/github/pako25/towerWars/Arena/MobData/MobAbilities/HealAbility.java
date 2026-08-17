package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;

import java.util.Set;

/**
 * 治疗光环（牧师/大祭司）：每 3 秒治疗射程内的其他怪（最大生命 × factor）。
 * 自带治疗技能的怪不接受外部治疗（防自我治疗循环），判定在 TWMob.heal 里。
 */
public class HealAbility implements TickAbility {

    private final float factor;
    private final int range;
    private final TWMob twMob;

    public HealAbility(float factor, int range, TWMob twMob) {
        this.factor = factor;
        this.twMob = twMob;
        this.range = range;
    }

    @Override
    public void onTick() {
        Set<TWMob> mobsInRange = twMob.getTrack().getMobsInRange(twMob.getLocation(), range);
        mobsInRange.forEach(mob -> mob.heal(factor));
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.TICK;
    }
}
