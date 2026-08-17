package io.github.pako25.towerWars.Arena.MobData.MobAbilities;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.Arena.TWMob;
import io.github.pako25.towerWars.Arena.Track;
import org.bukkit.util.Vector;

import java.util.ArrayList;

/**
 * 分裂（僵尸）：被塔击杀时原地分裂出 3 只迷你僵尸继续前进。
 * 分裂出的迷你僵尸从"死亡位置之后的路径点"继续走（不重复已走过的路）。
 */
public class SplitAbility implements DeathAbility {

    private final TWMob twMob;
    private final Track track;

    public SplitAbility(TWMob twMob) {
        this.twMob = twMob;
        track = twMob.getTrack();
    }

    @Override
    public boolean onDeath(boolean killed) {
        if (killed) {
            int nextWaypointIndex = twMob.getMobNavigation().getNavigation().getPath().getNextNodeIndex();
            // 三只迷你僵尸从同一位置出发（原版同样的调用写 3 遍，这里循环化）
            for (int i = 0; i < 3; i++) {
                track.summonMob(MobType.MINI_ZOMBIE, getNewTrimmedPath(nextWaypointIndex), twMob.getSummonerTWPlayer());
            }
        }
        return false; // 不取消本体死亡
    }

    /** 截取"死亡点之后的路径段"作为分裂体的行进路径（死亡点本身作起点） */
    private ArrayList<Vector> getNewTrimmedPath(int nextWaypointIndex) {
        ArrayList<Vector> path = track.getRandomPath();
        ArrayList<Vector> trimmedPath = new ArrayList<>();

        trimmedPath.add(twMob.getLocation().clone().subtract(track.getTrackSpawn()).toVector()); // 出生点 = 死亡位置
        for (int i = nextWaypointIndex + 1; i < path.size(); i++) {
            trimmedPath.add(path.get(i));
        }
        return trimmedPath;
    }

    @Override
    public boolean isAbilityType(AbilityTypes abilityType) {
        return abilityType == AbilityTypes.DEATH;
    }
}
