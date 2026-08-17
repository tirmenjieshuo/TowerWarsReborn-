package io.github.pako25.towerWars.GameManagement;

import io.github.pako25.towerWars.CustomConfig;
import io.github.pako25.towerWars.Main;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.config.ArenaConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 游戏总管理器（单例）：竞技场注册表 + 排队/开局/收尾的调度中枢。
 *
 * 设计意图：
 * 全插件唯一的"当前有哪些竞技场、哪些在开、哪些在排队"状态持有者。
 * 开局由队列满员或倒计时归零触发；游戏结束（gameEnd）或服务端关闭
 * （cancelAllGames）时回收全部游戏与队列。赛道颜色共 6 种，与
 * GameRules.MAX_TRACK_SPAWNS 对应。
 */
public class GameManager {

    private static GameManager gameManager;

    /** 可分配的 6 种赛道颜色 */
    private final List<NamedTextColor> allColors;
    private final Map<String, Game> gameMap = new HashMap<>();
    /** config.yml 的 arenas 列表：只有登记在案的竞技场才会被加载 */
    private final List<String> allArenas;
    private final Map<String, GameQueue> queue = new HashMap<>();
    private final JavaPlugin plugin;

    private GameManager() {
        this.plugin = JavaPlugin.getPlugin(Main.class);
        FileConfiguration config = CustomConfig.getFileConfiguration("config");
        allArenas = config.getStringList("arenas");
        for (String arenaName : allArenas) {
            CustomConfig.setup(arenaName); // 为每个竞技场加载 <arenaName>.yml
        }
        allColors = List.of(NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.GOLD, NamedTextColor.AQUA);
    }

    /** 加入队列：竞技场必须存在且空闲；首个玩家创建队列（并指定玩法模式），其余加入 */
    public void joinQueue(TWPlayer twPlayer, String arenaName, TowerMode mode) {
        if (!(arenaExists(arenaName) && isArenaFree(arenaName))) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.arenaUnavailable());
            return;
        }

        if (isPlayerInQueue(twPlayer)) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.alreadyInQueue());
            return;
        }

        if (!queue.containsKey(arenaName)) {
            // 创建该竞技场的队列（首个玩家即队首，其选择的模式生效）
            queue.put(arenaName, new GameQueue(arenaName, twPlayer, mode));
        } else {
            queue.get(arenaName).addPlayer(twPlayer);
            if (queue.get(arenaName).isFull()) {
                startGame(arenaName);
                queue.remove(arenaName);
            }
        }
        SignManager.getInstance().updateSigns();
    }

    /** 离开队列：游戏中=弃权败北；队列中=移除并可能取消倒计时 */
    public void leaveQueue(TWPlayer twPlayer) {
        if (twPlayer.isInGame()) {
            if (twPlayer.isAttacker()) {
                // 围攻进攻者：无赛道，直接弃权（其余进攻者继续，防守者无人可打时获胜）
                twPlayer.attackerForfeit();
            } else {
                twPlayer.getTrack().setLives(0);
                twPlayer.getTrack().closeTrack();
            }
            twPlayer.getPlayer().sendMessage(Messages.Lobby.forfeitedBattle());
            return;
        }

        Iterator<Map.Entry<String, GameQueue>> iterator = queue.entrySet().iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            Map.Entry<String, GameQueue> entry = iterator.next();
            boolean wasStartable = entry.getValue().isStartable();
            if (entry.getValue().removePlayer(twPlayer)) removed = true;

            // 人数跌破开赛线时取消倒计时
            if (!entry.getValue().isStartable() && wasStartable) entry.getValue().cancelCountdown(true);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        if (!removed) twPlayer.getPlayer().sendMessage(Messages.Lobby.notInQueue());
    }

    /** 开局：构建 Game 并登记；失败时把玩家退回并清理队列 */
    private void startGame(String arenaName) {
        try {
            GameQueue gameQueue = queue.get(arenaName);
            Game game = new Game(gameQueue.getPlayers(), arenaName, gameQueue.getMode());
            gameMap.put(arenaName, game);
            gameQueue.cancelCountdown(false);
            SignManager.getInstance().updateSigns();
        } catch (Exception e) {
            plugin.getLogger().severe("在 " + arenaName + " 中初始化新游戏失败，原因: ");
            plugin.getLogger().severe(e.getMessage());
            queue.get(arenaName).getPlayers().forEach(twPlayer -> twPlayer.getPlayer().sendMessage(Messages.Lobby.serverError()));
            queue.get(arenaName).cancelCountdown(false);
            queue.remove(arenaName);
        }
    }

    /** 强制开局（towerwars.forcestart）：人数足够才生效 */
    public void forceStart(TWPlayer twPlayer) {
        if (!isPlayerInQueue(twPlayer)) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.notInQueue());
            return;
        }

        Iterator<GameQueue> iterator = queue.values().iterator();
        while (iterator.hasNext()) {
            GameQueue q = iterator.next();
            if (q.containsPlayer(twPlayer)) {
                if (q.isStartable()) {
                    startGame(q.getArenaName());
                    iterator.remove();
                } else {
                    twPlayer.getPlayer().sendMessage(Messages.Lobby.notEnoughPlayersToForceStart());
                }
                break;
            }
        }
    }

    /** 倒计时归零触发的开局（区别于满员开局） */
    protected void startGameByCountdown(String arenaName) {
        startGame(arenaName);
        queue.remove(arenaName);
    }

    /**
     * 单人测试模式（towerwars.test）：无需排队、无视 enabled 开关立即开局，
     * 供管理员在配置完竞技场后验证路径/塔位/防守（怪会发往自己的赛道）。
     * 竞技场配置不完整时先 fail-fast 校验并明确报错，避免竞技场被静默占住。
     */
    public void startSoloTest(TWPlayer twPlayer, String arenaName) {
        if (!arenaExists(arenaName)) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.arenaDoesNotExist());
            return;
        }
        if (!isArenaFree(arenaName)) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.arenaUnavailable());
            return;
        }
        // 全量解析配置：缺 towerPlaceMaterial/trackSpawns/trackBounds/paths 会抛异常
        try {
            new ArenaConfig(plugin, arenaName);
        } catch (IllegalStateException e) {
            plugin.getLogger().severe("单人测试开局失败: " + e.getMessage());
            twPlayer.getPlayer().sendMessage(Messages.Lobby.serverError());
            return;
        }
        try {
            Game game = new Game(List.of(twPlayer), arenaName, TowerMode.CLASSIC);
            gameMap.put(arenaName, game);
            SignManager.getInstance().updateSigns();
            twPlayer.getPlayer().sendMessage(Messages.Cmd.testModeStarted(arenaName));
        } catch (Exception e) {
            plugin.getLogger().severe("单人测试开局失败: " + e.getMessage());
            twPlayer.getPlayer().sendMessage(Messages.Lobby.serverError());
        }
    }

    private boolean isPlayerInQueue(TWPlayer twPlayer) {
        for (GameQueue q : queue.values()) {
            if (q.containsPlayer(twPlayer)) return true;
        }
        return false;
    }

    /** 游戏结束：注销并刷新牌子 */
    public void gameEnd(String arenaName) {
        gameMap.remove(arenaName);
        SignManager.getInstance().updateSigns();
    }

    public boolean isArenaFree(String arenaName) {
        return !gameMap.containsKey(arenaName);
    }

    public List<NamedTextColor> getAllColors() {
        return allColors;
    }

    public boolean arenaExists(String arenaName) {
        return allArenas.contains(arenaName);
    }

    public static GameManager getInstance() {
        if (gameManager == null) {
            gameManager = new GameManager();
        }
        return gameManager;
    }

    /**
     * 服务端关闭：取消全部游戏与队列。
     * 用 keySet 快照迭代：cancelGame → cleanup → gameEnd 会从 gameMap 移除自身，
     * 直接遍历 values() 会在中途抛 ConcurrentModificationException（原版遗留 bug）。
     */
    public void cancelAllGames() {
        for (String arenaName : new ArrayList<>(gameMap.keySet())) {
            gameMap.get(arenaName).cancelGame();
        }
        queue.clear();
        gameMap.clear();
        SignManager.getInstance().updateSigns();
    }

    public List<String> getAllArenas() {
        return allArenas;
    }

    /** 注册新竞技场（编辑器创建时调用）并持久化到 config.yml */
    public void addArenaToAllArenas(String arenaName) {
        allArenas.add(arenaName);

        CustomConfig mainConfig = CustomConfig.getCustomConfig("config");
        mainConfig.getCustomFile().set("arenas", allArenas);
        mainConfig.save();
    }

    /** 空闲且启用中的竞技场列表（命令补全与列表展示用） */
    public List<String> getAvailableArenas() {
        List<String> availableArenas = new ArrayList<>();
        for (String arenaName : allArenas) {
            if (!isArenaFree(arenaName)) continue;
            if (isArenaEnabled(arenaName)) availableArenas.add(arenaName);
        }
        return availableArenas;
    }

    public int getPeopleQueuedForArena(String arenaName) {
        if (queue.containsKey(arenaName)) {
            return queue.get(arenaName).getPlayers().size();
        }
        return 0;
    }

    /**
     * 竞技场是否启用（enabled 键）。
     * 注意：这里必须轻量读取——竞技场可能处于"模板/未配置完成"状态
     * （如刚新建时 trackSpawns 为空），ArenaConfig 的全量解析会因缺配置段
     * fail-fast 抛异常，而状态查询应能安全地报告"未启用"。
     */
    public boolean isArenaEnabled(String arenaName) {
        if (!allArenas.contains(arenaName)) return false;
        return CustomConfig.getFileConfiguration(arenaName).getBoolean("enabled", false);
    }
}
