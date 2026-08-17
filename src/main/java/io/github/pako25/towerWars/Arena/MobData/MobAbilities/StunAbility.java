package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Tower.Tower;

/**
 * 死亡眩晕（苦力怕/闪电苦力怕）：被击杀时让射程内所有塔眩晕 {duration} 秒。
 * 属于 DeathAbility（死亡触发）——苦力怕死了也要拉塔陪葬。
 */
public class StunAbility extends RangeAbility implements DeathAbility {

    private final int duration;

    public StunAbility(int range, int duration, TWMob twMob) {
        super(range, twMob);
        this.duration = duration;
    }

    @Override
    public boolean onDeath(boolean killed) {
        if (killed) {
            for (Tower tower : getTowersInRange()) {
                tower.applyStun(duration);
            }
        }
        return false; // 不取消死亡
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.DEATH;
    }
}
