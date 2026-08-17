package io.github.pako25.towerWars.Tower;

import io.github.pako25.towerWars.Arena.Track;
import org.bukkit.Location;

/**
 * 支援塔抽象基类：不攻击、只提供增益的塔（村民）。
 *
 * 设计意图：
 * 原版村民塔空实现 attackMobs/animateAttack，且 Track.tickTrack 每次循环都
 * instanceof 判断它再单独调 buffTowers()。本类把"支援塔"固化为类型：
 * 构造时置 attacker=false，主循环靠 isAttacker() 跳过攻击流程；
 * 村民的增益逻辑通过 {@link Tower#onTick()} 钩子每 tick 调用（见 VillagerTower）。
 */
public abstract class SupportTower extends Tower {

    public SupportTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }
}
