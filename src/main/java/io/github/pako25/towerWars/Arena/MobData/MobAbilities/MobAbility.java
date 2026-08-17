package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

/**
 * 怪物技能标记接口：所有技能（无论以何种时机触发）都实现本接口，
 * 并用 {@link #isAbilityType(AbilityTypes)} 声明自己属于哪一类，
 * 供 TWMob 按类型线性查找（技能被禁用期间一律查不到）。
 */
public interface MobAbility {

    /** 声明本技能所属的类型（一个技能只属于一种类型） */
    boolean isAbilityType(AbilityTypes abilityType);
}
