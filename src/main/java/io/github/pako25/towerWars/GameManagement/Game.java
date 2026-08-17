package io.github.pako25.towerWars.GameManagement;

import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Main;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.config.ArenaConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.GameRules;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一场游戏：经典/围攻/合作三种玩法模式，以主循环驱动全场。
 *
 * 设计意图：
 * 主循环每 5 服务器 tick 跑一次 tickGame：计时（每秒减 1）、发被动收入
 * （每 6 秒）、力量增强（每 600 次调用 ≈150 秒）、逐赛道 tick；
 * 模式分支：围攻模式维护限时与进攻者列表，合作模式维护波次刷怪。
 * 只剩一条赛道存活时本局结束；超时（1 小时）判平局。
 *
 * 模式规则：
 * - CLASSIC：每人一条赛道，放塔 + 送怪；
 * - SIEGE：1 名防守者（有赛道）+ N 名进攻者（无赛道只送怪），
 *   防守者生命归零 = 进攻方胜，限时守住 = 防守方胜；
 * - CO_OP：全体玩家同队各守各的赛道，系统按波次刷怪，打完所有波次
 *   且仍有赛道存活 = 全员胜利。
 */
public class Game {

    private final List<Track> trackList = new ArrayList<>();
    /** 围攻模式的进攻者（无赛道） */
    private final List<TWPlayer> attackers = new ArrayList<>();
    private final JavaPlugin plugin;
    private final String arenaName;
    private final TowerMode mode;
    private Material towerPlaceMaterial;

    private BukkitTask gameTicker;

    private final int incomeTimerMax = GameRules.INCOME_INTERVAL_SECONDS;
    private final int maxTime = GameRules.MAX_GAME_TIME_SECONDS;
    private int incomeTimer = GameRules.INCOME_INITIAL_TIMER;
    private int gameTimer = maxTime;
    private int tickCounter = 1;

    // ========== 围攻模式（SIEGE） ==========

    /** 围攻限时（秒），守住即为防守方胜利 */
    private static final int SIEGE_TIME_SECONDS = 600;
    private int siegeTimer = SIEGE_TIME_SECONDS;
    /** 结束时进攻方是否获胜（trackDied 置 true，限时置 false） */
    private boolean attackersWin = false;

    // ========== 合作模式（CO_OP） ==========

    /** 合作模式总波数 */
    private static final int MAX_WAVES = 20;
    /** 波次间隔（秒） */
    private static final int WAVE_INTERVAL_SECONDS = 20;
    private int wave = 0;
    private int waveTimer = 10; // 第一波 10 秒后到达

    public Game(List<TWPlayer> twPlayers, String arenaName, TowerMode mode) {
        this.plugin = JavaPlugin.getPlugin(Main.class);
        this.arenaName = arenaName;
        this.mode = mode;
        startGame(twPlayers);
    }

    /**
     * 初始化赛道并启动主循环。
     * 配置非法时抛 IllegalArgumentException，由调用方（GameManager 的开局入口）
     * 统一记录错误并提示玩家——单人测试模式与多人队列共用这一错误路径。
     */
    public void startGame(List<TWPlayer> twPlayers) {
        if (mode == TowerMode.SIEGE) {
            initialiseSiegeGame(twPlayers);
        } else {
            initialiseTracks(twPlayers);
        }
        gameTicker = (new BukkitRunnable() {
            @Override
            public void run() {
                tickGame();
            }
        }).runTaskTimer(plugin, 0L, GameRules.TICKER_INTERVAL_TICKS);
    }

    /** 围攻开局：第一个玩家 = 防守者（分配赛道），其余 = 进攻者（只送怪） */
    private void initialiseSiegeGame(List<TWPlayer> twPlayers) throws IllegalArgumentException {
        ArenaConfig arenaConfig = new ArenaConfig(plugin, arenaName);
        towerPlaceMaterial = arenaConfig.getTowerPlaceMaterial();

        String worldName = arenaConfig.getWorldName();
        if (worldName == null) throw new IllegalArgumentException("竞技场 " + arenaName + ".yml 缺少 worldName 字段");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("世界 '" + worldName + "' 不存在！");

        List<Location> trackSpawns = arenaConfig.getTrackSpawns();
        if (trackSpawns.isEmpty()) throw new IllegalArgumentException("竞技场 " + arenaName + ".yml 的 trackSpawns 为空");

        // 防守者：第一条赛道
        TWPlayer defender = twPlayers.getFirst();
        Track track = new Track(trackSpawns.getFirst(), plugin, defender, this, arenaName,
                GameManager.getInstance().getAllColors().getFirst());
        trackList.add(track);
        track.gameStart();
        defender.getPlayer().sendMessage(Messages.Game.defenderStarted());

        // 进攻者：无赛道，只送怪
        for (TWPlayer attacker : twPlayers.subList(1, twPlayers.size())) {
            attackers.add(attacker);
            attacker.attackerGameStart(this);
        }
    }

    /** 经典/合作开局：每个玩家一条赛道（合作模式由系统刷怪，玩家送怪发给自己赛道） */
    public void initialiseTracks(List<TWPlayer> twPlayers) throws IllegalArgumentException {
        ArenaConfig arenaConfig = new ArenaConfig(plugin, arenaName);
        towerPlaceMaterial = arenaConfig.getTowerPlaceMaterial();

        String worldName = arenaConfig.getWorldName();
        if (worldName == null) throw new IllegalArgumentException("竞技场 " + arenaName + ".yml 缺少 worldName 字段");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("世界 '" + worldName + "' 不存在！");

        List<Location> trackSpawns = arenaConfig.getTrackSpawns();
        if (trackSpawns.isEmpty()) throw new IllegalArgumentException("竞技场 " + arenaName + ".yml 的 trackSpawns 为空");

        if (trackSpawns.size() > GameManager.getInstance().getAllColors().size()) {
            throw new IllegalArgumentException("轨道出生点不能超过 " + GameManager.getInstance().getAllColors().size() + " 个！");
        }
        if (twPlayers.size() > trackSpawns.size()) {
            throw new IllegalArgumentException("玩家人数不能超过轨道出生点数量！");
        }

        Iterator<NamedTextColor> colorIterator = GameManager.getInstance().getAllColors().iterator();
        Iterator<Location> locationIterator = trackSpawns.iterator();
        for (TWPlayer twPlayer : twPlayers) {
            Track track = new Track(locationIterator.next(), plugin, twPlayer, this, arenaName, colorIterator.next());
            trackList.add(track);
        }

        trackList.forEach(Track::gameStart);
    }

    /**
     * 主循环：计时 → 收入 → 模式分支（围攻限时/合作波次）→ 力量增强 →
     * 逐赛道 tick → 超时判定。
     * 迭代必须用快照：赛道 tick 中怪到终点会触发 closeTrack → trackDied → 移除元素，
     * 直接 forEach 会在本轮结束后抛 ConcurrentModificationException（原版遗留 bug）。
     */
    private void tickGame() {
        tickCounter++;

        if (tickCounter % GameRules.TICKS_PER_SECOND == 0) { // 每秒
            gameTimer--;
            incomeTimer--;
            tickModePerSecond();
        }
        if (incomeTimer == 0) { // 到点发放被动收入
            incomeTimer = incomeTimerMax;
            trackList.forEach(Track::giveIncome);
            // 进攻者也吃被动收入（经济是送怪的基础）
            attackers.forEach(TWPlayer::recieveIncome);
        }
        if (tickCounter % GameRules.POWER_CREEP_INTERVAL_TICKS == 0) {
            trackList.forEach(Track::powerCreep);
        }

        for (Track track : new ArrayList<>(trackList)) {
            track.tickTrack();
        }

        if (mode == TowerMode.CO_OP) {
            tickCoopWave();
        }
        // 围攻进攻者没有赛道，主循环负责刷新其侧边栏（增量刷新，值不变不重写）
        attackers.forEach(attacker -> attacker.updateSidebar(false));

        if (tickCounter >= maxTime * GameRules.TICKS_PER_SECOND) {
            staleMate();
        }
    }

    /** 每秒的模式逻辑：围攻倒计时、合作波次计时 */
    private void tickModePerSecond() {
        if (mode == TowerMode.SIEGE) {
            siegeTimer--;
            if (siegeTimer <= 0) {
                // 限时结束：防守方获胜
                Track defender = trackList.getFirst();
                defender.setLives(1);
                defender.closeTrack(); // lost=false → 防守者显示胜利
                attackersWin = false;
                cleanup();
            }
        }
        if (mode == TowerMode.CO_OP) {
            waveTimer--;
        }
    }

    /** 合作模式波次驱动：到点刷一波，全部波次打完且仍有赛道存活 = 全员胜利 */
    private void tickCoopWave() {
        if (waveTimer > 0 || trackList.isEmpty()) return;
        waveTimer = WAVE_INTERVAL_SECONDS;
        wave++;
        if (wave > MAX_WAVES) {
            coopEnd(true);
            return;
        }
        spawnCoopWave();
    }

    /** 刷一波怪：随机选 1~2 条存活赛道，数量随波次递增，波次越高怪越强 */
    private void spawnCoopWave() {
        List<Track> aliveTracks = new ArrayList<>();
        for (Track track : trackList) {
            if (track.isAlive()) aliveTracks.add(track);
        }
        if (aliveTracks.isEmpty()) return;

        Collections.shuffle(aliveTracks);
        int targets = Math.min(aliveTracks.size(), wave > 10 ? 2 : 1);
        int countPerTrack = 2 + wave / 3;

        for (int i = 0; i < targets; i++) {
            Track target = aliveTracks.get(i);
            for (int j = 0; j < countPerTrack; j++) {
                // summoner 用目标赛道的玩家：怪数值跟随其召唤状态
                target.summonMob(randomMobForWave(wave), target.getTwPlayer());
            }
        }
        for (Track track : trackList) {
            track.getTwPlayer().getPlayer().sendMessage(Messages.Game.waveStarted(wave, MAX_WAVES));
        }
    }

    /** 波次怪类型：前期基础怪，中后期高级怪，后期概率出现 Boss */
    private MobType randomMobForWave(int wave) {
        MobType[] basic = {MobType.SILVERFISH, MobType.CHICKEN, MobType.SHEEP, MobType.CAVE_SPIDER,
                MobType.ZOMBIE, MobType.WOLF, MobType.BLACK_SPIDER, MobType.LEATHER_ZOMBIE,
                MobType.RABBIT, MobType.PRIEST, MobType.ZOMBIE_PIGMAN, MobType.WILD_CAT, MobType.CREEPER};
        MobType[] advanced = {MobType.ENDERMITE, MobType.PIGGY_BANK, MobType.RAINBOW_SHEEP, MobType.SQUID,
                MobType.GOLD_ZOMBIE, MobType.MAD_COW, MobType.SPIDER_JOCKEY, MobType.DIAMOND_ZOMBIE,
                MobType.WILD_HORSE, MobType.HIGH_PRIEST, MobType.WITHER_SKELETON,
                MobType.RUNNING_IRON_GOLEM, MobType.CHARGED_CREEPER};
        MobType[] boss = {MobType.GHAST, MobType.DEATH_RIDER};

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (wave >= 15 && random.nextDouble() < 0.3) return boss[random.nextInt(boss.length)];
        if (wave >= 8 && random.nextDouble() < 0.5) return advanced[random.nextInt(advanced.length)];
        return basic[random.nextInt(basic.length)];
    }

    /** 合作模式结束：胜利 = 存活赛道判胜后全员收尾；失败 = 全员淘汰（trackDied 已触发） */
    private void coopEnd(boolean victory) {
        for (Track track : new ArrayList<>(trackList)) {
            if (victory) track.setLives(1); // 存活赛道判定为胜利
            track.closeTrack();
        }
        cleanup();
    }

    /**
     * 送怪：经典模式发给其他赛道（单人测试发给自己）；合作模式发给自己赛道
     * （没有敌方）；围攻模式不走这里（进攻者用 attackerSummonMob）。
     */
    public void sendMonstersFrom(UUID trackUUID, MobType mobType, TWPlayer summoner) {
        switch (mode) {
            case CO_OP -> {
                for (Track track : trackList) {
                    if (track.getUUID().equals(trackUUID) && track.isAlive()) {
                        track.summonMob(mobType, summoner);
                    }
                }
            }
            case SIEGE -> {
                // 不应到达（进攻者无赛道，防守者无召唤怪物品）；忽略
            }
            case CLASSIC -> {
                if (trackList.size() == 1) {
                    // solo 测试：没有敌方赛道，怪从自己的出生点出发
                    trackList.getFirst().summonMob(mobType, summoner);
                    return;
                }
                for (Track track : trackList) {
                    if (!track.getUUID().equals(trackUUID) && track.isAlive()) {
                        track.summonMob(mobType, summoner);
                    }
                }
            }
        }
    }

    /** 围攻进攻者送怪：发往防守者赛道（所有进攻者的怪都打防守者） */
    public void attackerSummonMob(MobType mobType, TWPlayer attacker) {
        if (mode != TowerMode.SIEGE || trackList.isEmpty()) return;
        Track defenderTrack = trackList.getFirst();
        if (defenderTrack.isAlive()) {
            defenderTrack.summonMob(mobType, attacker);
        }
    }

    /** 围攻进攻者弃权（/towerwars leave 或掉线）：从列表移除，进攻人数为 0 时防守方直接获胜 */
    public void attackerForfeit(TWPlayer attacker) {
        attackers.remove(attacker);
        if (attackers.isEmpty() && !trackList.isEmpty()) {
            Track defender = trackList.getFirst();
            defender.setLives(1);
            defender.closeTrack(); // 防守者"胜利"
            attackersWin = false;
            cleanup();
        }
    }

    /**
     * 漏怪送命：把 1 心加给"送出这只怪"的玩家（他偷到了命）。
     * 仅经典模式生效；单人测试/围攻/合作（仅一条赛道或同队）没有偷命对象：
     * 漏怪仅扣防守者 1 心。
     */
    public void giveLiveToOthers(UUID trackUUID, NamedTextColor fromColor, String causeName) {
        if (mode != TowerMode.CLASSIC || trackList.size() <= 1) return;
        for (Track track : trackList) {
            if (track.getUUID().equals(trackUUID) && track.isAlive()) {
                track.gainLive(fromColor, causeName);
            }
        }
        trackList.forEach(Track::updateSidebar);
    }

    /** 一条赛道被淘汰：广播、移除；只剩一条时结束本局（单人测试无人可剩时直接收尾） */
    public void trackDied(Track deadTrack) {
        NamedTextColor deadColor = deadTrack.getColor();

        Iterator<Track> iterator = trackList.iterator();
        while (iterator.hasNext()) {
            Track track = iterator.next();
            if (track.equals(deadTrack)) {
                iterator.remove();
            } else {
                track.getTwPlayer().getPlayer().sendMessage(Messages.Game.trackEliminated(Messages.colorName(deadColor)));
            }
        }
        if (trackList.isEmpty()) {
            attackersWin = true; // 围攻：防守者死亡 = 进攻方胜利
            cleanup();
            return;
        }
        if (trackList.size() == 1 && tickCounter < maxTime * GameRules.TICKS_PER_SECOND) {
            gameEnd();
        }
    }

    /** 唯一幸存者获胜：收掉它的赛道并清理 */
    private void gameEnd() {
        trackList.getFirst().closeTrack();
        cleanup();
    }

    /** 超时平局：全部赛道判负（快照迭代，closeTrack 会经 trackDied 移除元素） */
    private void staleMate() {
        trackList.forEach(track -> track.setLives(0));
        for (Track track : new ArrayList<>(trackList)) {
            track.closeTrack();
        }
        cleanup();
    }

    /** 服务端关闭/主动取消游戏 */
    public void cancelGame() {
        staleMate();
    }

    private void cleanup() {
        if (gameTicker != null) {
            gameTicker.cancel();
        }
        // 围攻模式：进攻者没有赛道，trackDied 不会覆盖他们，需单独收尾
        if (mode == TowerMode.SIEGE) {
            for (TWPlayer attacker : new ArrayList<>(attackers)) {
                attacker.gameEnd(!attackersWin);
            }
            attackers.clear();
        }
        trackList.clear();
        GameManager.getInstance().gameEnd(arenaName);
    }

    // ========== 只读访问器 ==========

    public TowerMode getMode() {
        return mode;
    }

    public int getIncomeTimer() {
        return incomeTimer;
    }

    public int getGameTimer() {
        return gameTimer;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public List<Track> getTrackList() {
        return trackList;
    }

    public Material getTowerPlaceMaterial() {
        return towerPlaceMaterial;
    }

    /** 围攻剩余秒数（进攻者侧边栏用） */
    public int getSiegeTimer() {
        return siegeTimer;
    }

    /** 防守者当前生命（围攻进攻者侧边栏用；无赛道时为 0） */
    public int getDefenderLives() {
        if (trackList.isEmpty()) return 0;
        return trackList.getFirst().getLives();
    }

    /** 合作模式当前波次 */
    public int getWave() {
        return wave;
    }

    /** 合作模式总波数 */
    public int getMaxWaves() {
        return MAX_WAVES;
    }
}
