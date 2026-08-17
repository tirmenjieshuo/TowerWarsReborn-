package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.TWMob;

/**
 * 受击瞬移（蠹虫）：被塔命中时沿路径方向瞬间前进 {distance} 格
 * （skipBlocks 会重建导航，跳过中间方块直接落位）。
 */
public class TPOnHit implements OnHitAbility {

    private final TWMob twMob;
    private final int distance;

    public TPOnHit(int distance, TWMob twMob) {
        this.twMob = twMob;
        this.distance = distance;
    }

    @Override
    public void onHit() {
        twMob.getMobNavigation().skipBlocks(distance);
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.HIT;
    }
}
