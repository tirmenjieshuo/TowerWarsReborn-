package io.github.pako25.towerWars.Arena;

import io.github.pako25.towerWars.Arena.MobData.MobBuilder;
import io.github.pako25.towerWars.Arena.MobData.MobStates;
import io.github.pako25.towerWars.GameManagement.Game;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.Tower.AttackTower;
import io.github.pako25.towerWars.Tower.Tower;
import io.github.pako25.towerWars.Tower.TowerType;
import io.github.pako25.towerWars.config.ArenaConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.GameRules;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * 赛道：一名玩家在一场游戏里的全部领地与玩法容器。
 *
 * 设计意图：
 * 赛道承载四件事——塔（placedTowers）、怪（activeMobs + mobQueue）、
 * 地图数据（出生点/边界/路径）与玩家经济挂钩的生命数。核心是每 tick 的
 * {@link #tickTrack()}：进怪 → 怪自身 tick → 每座塔走钩子 → 结算击杀飘字。
 * 原版在 tickTrack 里对村民/守卫者/特斯拉做 instanceof 特判，重构后统一
 * 收敛为 Tower 的 onTick/onNothingInRange 两个钩子（行为等价性论证见 tickTrack）。
 */
public class Track {

    private final Location trackSpawn;
    private final Map<Location, Tower> placedTowers = new HashMap<>();
    private final ArrayList<ArrayList<Vector>> paths = new ArrayList<>();
    private final Set<TWMob> activeMobs = new HashSet<>();
    private final MobQueue mobQueue = new MobQueue();
    private final List<ArmorStand> mobKillDisplays = new ArrayList<>();
    private final Plugin plugin;
    private final TWPlayer twPlayer;
    private final Game game;
    private final UUID uuid = UUID.randomUUID();
    private final Vector[] trackBounds = new Vector[4];
    private final String arenaName;
    private final NamedTextColor color;
    private int lives = GameRules.STARTING_LIVES;
    private boolean alive = true;

    public Track(Location trackSpawn, Plugin plugin, TWPlayer twPlayer, Game game, String arenaName, NamedTextColor color) {
        this.trackSpawn = trackSpawn;
        this.plugin = plugin;
        this.twPlayer = twPlayer;
        this.arenaName = arenaName;
        this.game = game;
        this.color = color;
        loadBounds();
        loadPaths();
    }

    /** 比赛开始：把玩家送入游戏状态 */
    public void gameStart() {
        twPlayer.gameStart(this, game);
    }

    /** 从竞技场配置读取轨道边界（4 个角，相对出生点的向量，Y 忽略） */
    private void loadBounds() {
        ArenaConfig arenaConfig = new ArenaConfig(plugin, arenaName);
        Vector[] bounds = arenaConfig.getTrackBounds();
        System.arraycopy(bounds, 0, trackBounds, 0, bounds.length);
    }

    /** 从竞技场配置读取全部怪物路径（相对出生点的向量序列） */
    private void loadPaths() {
        ArenaConfig arenaConfig = new ArenaConfig(plugin, arenaName);
        for (List<Vector> path : arenaConfig.getPaths()) {
            paths.add(new ArrayList<>(path));
        }
    }

    /** 召唤怪（随机路径）：被送怪的对方赛道调用 */
    public void summonMob(MobType mobType, TWPlayer summoner) {
        int index = ThreadLocalRandom.current().nextInt(paths.size());
        if (mobType == MobType.GHAST) {
            // 幽灵体积大，只能走中间的路径（原版假设路径数为奇数，这是竞技场地图的约定）
            index = (paths.size() / 2) + 1;
        }
        ArrayList<Vector> path = paths.get(index);

        TWMob mob = MobBuilder.buildMob(this, path, mobType, summoner);
        mobQueue.add(mob);
    }

    /** 召唤怪（指定路径）：分裂等技能让怪在指定位置重新出场时使用 */
    public void summonMob(MobType mobType, ArrayList<Vector> customPath, TWPlayer summoner) {
        TWMob mob = MobBuilder.buildMob(this, customPath, mobType, summoner);
        mobQueue.add(mob);
    }

    /** 逐 0.25 秒驱动场上所有怪（到达终点的怪从集合移除） */
    private void tickMobs() {
        synchronized (activeMobs) {
            Iterator<TWMob> iterator = activeMobs.iterator();
            boolean fullSecond = game.getTickCounter() % GameRules.TICKS_PER_SECOND == 0;

            while (iterator.hasNext()) {
                TWMob mob = iterator.next();
                mob.tick(fullSecond);
                if (!mob.isAlive()) iterator.remove();
            }
        }
    }

    /**
     * 漏怪扣命：生命归零则淘汰本条赛道。
     * 死亡骑士双倍扣命（Boss 特性的硬编码）；仅经典多人时普通怪"送命"给对方
     * 赛道（对方 +1 命，本方案例性 -1）；单人测试/围攻/合作无偷命对象，仅扣 1 心。
     */
    void loseLive(TWMob cause) {
        lives--;
        if (cause.getMobType() == MobType.DEATH_RIDER) {
            lives -= GameRules.DEATH_RIDER_LIVES_LOST - 1; // 共扣 2 命
        } else if (game.getMode() == TowerMode.CLASSIC && game.getTrackList().size() > 1) {
            game.giveLiveToOthers(cause.getSummonerTWPlayer().getTrack().getUUID(), color, cause.getMobType().name());

            NamedTextColor toColor = cause.getSummonerTWPlayer().getTrack().getColor();
            twPlayer.getPlayer().sendMessage(Messages.Game.gaveLive(Messages.colorName(toColor)));
        }
        if (lives < 1) {
            closeTrack();
        }
    }

    /** 判断一个绝对坐标是否落在本赛道的塔放置边界内（塔只能放这里） */
    public boolean isLocationInsideTrackBounds(Location location) {
        int x1 = trackSpawn.clone().add(trackBounds[0]).getBlockX();
        int x2 = trackSpawn.clone().add(trackBounds[1]).getBlockX();
        int x3 = trackSpawn.clone().add(trackBounds[2]).getBlockX();
        int x4 = trackSpawn.clone().add(trackBounds[3]).getBlockX();
        int z1 = trackSpawn.clone().add(trackBounds[0]).getBlockZ();
        int z2 = trackSpawn.clone().add(trackBounds[1]).getBlockZ();
        int z3 = trackSpawn.clone().add(trackBounds[2]).getBlockZ();
        int z4 = trackSpawn.clone().add(trackBounds[3]).getBlockZ();
        int checkX = location.getBlockX();
        int checkZ = location.getBlockZ();
        int minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        int maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        int minZ = Math.min(Math.min(z1, z2), Math.min(z3, z4));
        int maxZ = Math.max(Math.max(z1, z2), Math.max(z3, z4));

        return checkX >= minX && checkX <= maxX && checkZ >= minZ && checkZ <= maxZ;
    }

    /** 在指定位置放置一座塔（比赛结束后拒绝放置） */
    public void placeTower(TowerType towerType, Location location, int level, int prestige) {
        if (!alive) {
            twPlayer.getPlayer().sendMessage(Messages.Game.youAreDead());
            return;
        }
        Tower tower = towerType.create(location, level, prestige, this);
        placedTowers.put(location, tower);
    }

    /**
     * 赛道主循环（每 5 服务器 tick 一次）：
     * 进怪 → 怪自身 tick → 塔主循环 → 库存恢复 → 击杀飘字清理。
     *
     * 塔主循环的行为等价性说明（原版 instanceof 特判 → 钩子化改造）：
     * - 村民 buff 原在冷却判断前执行且 continue 跳过攻击 → 迁入 onTick() 且由
     *   isAttacker() 承担"跳过攻击"，时机一致；
     * - 守卫者 resetTargeting 原无条件执行（含冷却/无怪）→ onTick() 每 tick 调用，一致；
     * - 特斯拉 continuouslyAttack 原在 isOnCooldown 内、无怪时短路不执行 →
     *   onTick() 每 tick 调用频次更高，但其动画在 targetLock 为空时只做视觉复位，
     *   锁定的目标在离开射程/死亡两条路径上都会被清空，因此无行为差异；
     * - 特斯拉 nothingInRange 原触发条件（非冷却 ∧ 有怪 ∧ 射程空）与
     *   onNothingInRange() 调用点完全一致。
     */
    public void tickTrack() {
        if (!alive) return;
        synchronized (activeMobs) {
            activeMobs.addAll(mobQueue.tick());
        }
        tickMobs();
        boolean noMobs = activeMobs.isEmpty();
        for (Tower tower : placedTowers.values()) {
            tower.onTick();
            // 用"能力"而非具体类型判断：只有攻击塔（AttackTower）走攻击流程，
            // 支援塔（SupportTower）的每 tick 逻辑全部在 onTick() 里完成
            if (!(tower instanceof AttackTower attackTower)) continue;
            if (attackTower.isOnCooldown() || noMobs) continue;
            Set<TWMob> mobSet = getMobsInRange(tower.getLocation(), tower.getRange());
            if (mobSet.isEmpty()) {
                tower.onNothingInRange();
                continue;
            }
            attackTower.attackMobs(mobSet);
        }
        // 每秒恢复一次召唤库存并刷新侧边栏
        if (game.getTickCounter() % GameRules.TICKS_PER_SECOND == 0) {
            twPlayer.increaseStock();
            twPlayer.updateSidebar(false);
        }

        // 清理超过 20 tick 的击杀飘字
        Iterator<ArmorStand> iterator = mobKillDisplays.iterator();
        while (iterator.hasNext()) {
            ArmorStand killDisplay = iterator.next();
            if (killDisplay.getTicksLived() > GameRules.KILL_DISPLAY_TICKS) {
                killDisplay.remove();
                iterator.remove();
            }
        }
    }

    /** 射程内的怪集合（距离平方与射程平方比较，避免开方开销） */
    public Set<TWMob> getMobsInRange(Location location, int range) {
        Set<TWMob> mobSet = new HashSet<>();
        for (TWMob mob : activeMobs) {
            if (mob.getLocation().distanceSquared(location) <= range * range) {
                mobSet.add(mob);
            }
        }
        return mobSet;
    }

    /**
     * 射程内的塔集合（原版在村民塔与三个减益技能里复制了 4 份，收敛到此一处）。
     * 调用方用谓词过滤自己（如村民不 buff 自己、不 buff 其他村民）。
     */
    public Set<Tower> getTowersInRange(Location location, int range, Predicate<Tower> filter) {
        Set<Tower> towerSet = new HashSet<>();
        for (Tower tower : placedTowers.values()) {
            if (filter.test(tower) && tower.getLocation().distanceSquared(location) <= range * range) {
                towerSet.add(tower);
            }
        }
        return towerSet;
    }

    /** 活跃怪的线程安全快照（锁内拷贝，供守卫者激光等锁外遍历方使用，防并发修改） */
    public Set<TWMob> snapshotActiveMobs() {
        synchronized (activeMobs) {
            return new HashSet<>(activeMobs);
        }
    }

    public boolean isBlockOccupiedByTower(Location targetBlock) {
        return placedTowers.containsKey(targetBlock);
    }

    /** 出售塔：半价退款并移出登记 */
    public void cleanupSoldTower(Tower tower) {
        twPlayer.increaseCoin((int) (tower.getCost() * GameRules.SELL_REFUND_RATIO));
        placedTowers.remove(tower.getLocation());
    }

    /** 关闭赛道（被淘汰或比赛结束）：收走玩家、清怪、卖掉所有塔 */
    public void closeTrack() {
        boolean lost = lives < 1;
        twPlayer.gameEnd(lost);
        if (lost) game.trackDied(this);
        alive = false;

        synchronized (activeMobs) {
            activeMobs.forEach(TWMob::gameEnd);
            activeMobs.clear();
        }
        mobKillDisplays.forEach(ArmorStand::remove);
        mobKillDisplays.clear();

        // 快照后逐个出售，避免迭代中修改集合
        List<Tower> snapshot = new ArrayList<>(placedTowers.values());
        for (Tower tower : snapshot) {
            tower.sell();
        }
        placedTowers.clear();
    }

    /** 怪物力量增强：血量乘性上涨（游戏后期怪越来越肉） */
    public void powerCreep() {
        twPlayer.getMobStates().multiplyPowerCreepMultiplyer(GameRules.POWER_CREEP_MULTIPLIER);
        twPlayer.getPlayer().sendMessage(Messages.Game.monstersGettingStronger());
    }

    // ========== 只读访问器 ==========

    public Plugin getPlugin() {
        return plugin;
    }

    public UUID getUUID() {
        return uuid;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Map<Location, Tower> getTowers() {
        return placedTowers;
    }

    public Set<TWMob> getActiveMobs() {
        return activeMobs;
    }

    public List<ArmorStand> getMobKillDisplays() {
        return mobKillDisplays;
    }

    public int getLives() {
        return lives;
    }

    public Location getTrackSpawn() {
        return trackSpawn;
    }

    public void giveIncome() {
        twPlayer.recieveIncome();
    }

    public TWPlayer getTwPlayer() {
        return twPlayer;
    }

    /** 召唤状态：统一由赛道主人持有（围攻进攻者也有自己的一份，见 TWPlayer.getMobStates） */
    public MobStates getMobStates() {
        return twPlayer.getMobStates();
    }

    public Game getGame() {
        return game;
    }

    public boolean isAlive() {
        return alive;
    }

    /** 收到其他赛道漏怪送来的命（偷命提示） */
    public void gainLive(NamedTextColor fromColor, String causeName) {
        twPlayer.getPlayer().sendMessage(Messages.Game.stoleLive(Messages.colorName(fromColor), causeName));
        lives++;
    }

    public ArrayList<Vector> getRandomPath() {
        return paths.get(ThreadLocalRandom.current().nextInt(paths.size()));
    }

    public boolean hasSpaceLeft() {
        return placedTowers.size() < GameRules.MAX_TOWERS_PER_TRACK;
    }

    public void updateSidebar() {
        twPlayer.updateSidebar(true);
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public int getMaxTowers() {
        return GameRules.MAX_TOWERS_PER_TRACK;
    }
}
