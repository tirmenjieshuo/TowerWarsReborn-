package io.github.pako25.towerWars.Arena.MobData;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.config.MobConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 全怪物状态注册表：每局游戏一份（挂在 Track 上），保存本局内
 * 每种怪的运行时状态（召唤次数/进化进度）与"怪物力量增强"倍率。
 *
 * 设计意图：
 * 原版构造时直接遍历 YAML 字符串键并逐字段读取，这里改为遍历
 * MobConfig（启动期已类型化解析并校验），构造只做"枚举 → MobState"装配。
 */
public class MobStates {

    private final HashMap<MobType, MobState> mobStates = new HashMap<>();
    /** 力量增强倍率：每 30 秒全局乘性上涨一次（powerCreep） */
    private float powerCreepHealthMultiplyer = 1;

    public MobStates() {
        loadDefaultMobStates();
    }

    private void loadDefaultMobStates() {
        for (Map.Entry<MobType, MobConfig.MobDefinition> entry : MobConfig.allMobs()) {
            mobStates.put(entry.getKey(), new MobState(entry.getValue(), entry.getKey(), this));
        }
    }

    public MobState getMobState(MobType mobType) {
        return mobStates.get(mobType);
    }

    public Set<Map.Entry<MobType, MobState>> getEntrySet() {
        return mobStates.entrySet();
    }

    public float getPowerCreepHealthMultiplyer() {
        return powerCreepHealthMultiplyer;
    }

    /** 力量增强：倍率 ×（1 + 增幅），怪物越来越肉 */
    public void multiplyPowerCreepMultiplyer(float multiplyer) {
        powerCreepHealthMultiplyer = powerCreepHealthMultiplyer * (1 + multiplyer);
    }
}
