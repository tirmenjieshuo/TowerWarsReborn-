package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 技能类型枚举：TWMob 按此类型查找技能并分发到对应触发时机——
 * TICK 每 3 秒触发、HIT 受击触发、DEATH 死亡触发、ABSORBER 伤害吸收结算、
 * AOEDODGE 闪避判定、REDUCEDSLOW 减速抗性、NOKILLBONUSES 击杀奖励标记。
 */
public enum AbilityTypes {
    AOEDODGE, TICK, HIT, DEATH, ABSORBER, REDUCEDSLOW, NOKILLBONUSES
}
