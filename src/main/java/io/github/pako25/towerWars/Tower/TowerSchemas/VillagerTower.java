package io.github.pako25.towerWars.Tower.TowerSchemas;

import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Tower.SupportTower;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.Tower.TowerType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.HashSet;
import java.util.Set;

/**
 * 村民塔（纯支援）：给射程内的塔施加"减益免疫"（2 级附赠 10% 攻速）。
 *
 * 设计意图：
 * 原版把 buffTowers 放在 Track.tickTrack 的 instanceof 分支里调用，并自带一份
 * getTowersInRange 复制品。重构后：继承 SupportTower（不参与攻击），
 * 增益逻辑搬进 {@link #onTick()} 钩子由主循环统一驱动；
 * 射程查询改用 Track 的统一方法（原版四处副本收敛为一处）。
 * 增量判定：只有赛道上的塔数量变化时才重扫范围（避免每 tick 重复结算）。
 */
public class VillagerTower extends SupportTower {

    private final Set<Tower> buffedTowers = new HashSet<>();
    private int lastTotalTowersOnTrack = 1;

    public VillagerTower(Location location, int level, int prestige, Track track) {
        super(location, level, prestige, track);
    }

    public void spawn() {
        Villager villager = (Villager) location.getWorld().spawnEntity(location.clone().add(0.5, 1, 0.5), EntityType.VILLAGER);
        entities.add(villager);
        villager.setAI(false);
        applyStats(TowerType.VILLAGER);
    }

    /**
     * 每 tick 驱动：塔数量变化时重新扫描射程内的塔并施加增益。
     * 只给"新出现的塔"上 buff（buffedTowers 去重），不重复叠加。
     */
    @Override
    public void onTick() {
        if (lastTotalTowersOnTrack == track.getTowers().size()) return;
        lastTotalTowersOnTrack = track.getTowers().size();
        Set<Tower> towersInRange = track.getTowersInRange(location, range, tower -> tower != this && !(tower instanceof VillagerTower));
        for (Tower tower : towersInRange) {
            if (buffedTowers.add(tower)) { // 只在"首次覆盖到这座塔"时上 buff
                tower.applyDebuffProtection(this);
                if (level == 2) {
                    tower.applyReloadBoost(0.1F);
                }
            }
        }
    }

    /** 被保护塔出售时回调：从自己的保护名单移除（释放引用防泄漏） */
    public void removeFromProtection(Tower tower) {
        buffedTowers.remove(tower);
    }

    public void cleanup() {
        for (Tower tower : buffedTowers) {
            tower.removeVillagerBoosts(this);
        }
        buffedTowers.clear();
    }

    @Override
    protected void setSlownessIndicatorHeight() {
        slownessIndicatorLocation = location.clone().add(0, 3, 0);
    }
}
