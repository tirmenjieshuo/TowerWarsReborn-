package io.github.pako25.towerWars.GameManagement;

import io.github.pako25.towerWars.CustomConfig;
import io.github.pako25.towerWars.Main;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.TowerWars;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.GameRules;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个竞技场的等待队列：收人、倒计时、BossBar 进度、大厅物品发放。
 *
 * 设计意图：
 * 队列满员立即开局；人数 ≥2 启动倒计时（{@link GameRules#START_WAIT_SECONDS}
 * 秒），每整秒向全体播报剩余时间；人数跌破开赛线时取消倒计时。
 * 大厅内玩家手持"离开"时钟与"统计"告示牌。
 * 修复的原版 bug：倒计时开始时硬编码 "60 seconds left" 但实际只有 11 秒，
 * 现统一使用真实的 startWaitTime。
 */
public class GameQueue {

    private final int maxPlayers;
    private final int minPlayers = GameRules.MIN_PLAYERS_TO_START;
    private final int startWaitTime = GameRules.START_WAIT_SECONDS;

    private final String arenaName;
    /** 本队列的玩法模式（首个加入者选择，开局时传给 Game） */
    private final TowerMode mode;
    private final ArrayList<TWPlayer> players = new ArrayList<>();
    private Location lobbyLocation;
    private BukkitTask countdownTimer;
    private int countdown = 0;

    public GameQueue(String arenaName, TWPlayer player, TowerMode mode) {
        this.arenaName = arenaName;
        this.mode = mode;

        FileConfiguration cfg = CustomConfig.getFileConfiguration(arenaName);
        List<?> locationRaw = cfg.getList("lobbyLocation");
        String worldName = cfg.getString("worldName");

        if (locationRaw == null || locationRaw.size() != 3 || worldName == null) {
            lobbyLocation = null; // 未配置大厅：玩家原地等待
        } else {
            try {
                lobbyLocation = new Location(TowerWars.getPlugin().getServer().getWorld(worldName), (int) locationRaw.get(0), (int) locationRaw.get(1), (int) locationRaw.get(2));
            } catch (Exception e) {
                TowerWars.getPlugin().getLogger().severe("解析大厅位置时出错。");
                player.getPlayer().sendMessage(Messages.Editor.errorOccurred());
            }
        }

        List<?> spawnsRaw = cfg.getList("trackSpawns");
        if (spawnsRaw == null) {
            throw new IllegalArgumentException("竞技场 " + arenaName + ".yml 缺少 trackSpawns 配置段");
        }
        maxPlayers = spawnsRaw.size();

        addPlayer(player);
    }

    /** 启动倒计时：每秒减一并向玩家播报（30/10/5~1 秒时广播） */
    private void startCountdown() {
        if (countdownTimer != null && !countdownTimer.isCancelled()) return;

        countdown = startWaitTime;
        countdownTimer = (new BukkitRunnable() {
            @Override
            public void run() {
                if (!isStartable()) {
                    cancel(); // 人数不足：自行停止
                    return;
                }
                countdown--;
                updateBossBars();

                if (countdown == 30 || countdown == 10 || countdown == 5 || countdown == 4 || countdown == 3 || countdown == 2 || countdown == 1) {
                    sendCountDownMessage();
                }

                if (countdown < 1) {
                    cancel();
                    GameManager.getInstance().startGameByCountdown(arenaName);
                }
            }
        }).runTaskTimer(JavaPlugin.getProvidingPlugin(Main.class), 20L, 20L);
        for (TWPlayer twPlayer : players) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.countdownStarted(startWaitTime));
        }
    }

    private void sendCountDownMessage() {
        for (TWPlayer twPlayer : players) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.countdown(countdown));
        }
    }

    /** 移除玩家：清理大厅状态并广播 */
    public boolean removePlayer(TWPlayer twPlayer) {
        if (containsPlayer(twPlayer)) {
            players.remove(twPlayer);
            twPlayer.setInLobby(false);
            twPlayer.clearBossBar();
            twPlayer.getPlayer().getInventory().clear();
            if (twPlayer.getLocationBeforeGame() != null) {
                twPlayer.getPlayer().teleport(twPlayer.getLocationBeforeGame().clone().add(0, 2, 0));
            }
            twPlayer.getPlayer().sendMessage(Messages.Lobby.leftQueue());
            for (TWPlayer p1 : players) {
                if (!p1.equals(twPlayer)) {
                    p1.getPlayer().sendMessage(Messages.Lobby.broadcastLeftQueue(twPlayer.getPlayer().displayName(), players.size(), maxPlayers));
                }
            }
            updateBossBars();
            SignManager.getInstance().updateSigns();
            return true;
        }
        return false;
    }

    public boolean containsPlayer(TWPlayer twPlayer) {
        return players.contains(twPlayer);
    }

    public boolean isFull() {
        return players.size() == maxPlayers;
    }

    /** 加入玩家：记录原位置、传送大厅、发物品、启动/更新倒计时与 BossBar */
    public void addPlayer(TWPlayer twPlayer) {
        if (isFull()) return;
        players.add(twPlayer);
        sendPlayerJoinMessage(twPlayer);

        twPlayer.setLocationBeforeGame(twPlayer.getPlayer().getLocation());
        if (lobbyLocation != null) {
            twPlayer.getPlayer().teleport(lobbyLocation.clone().add(0, 2, 0));
        }
        twPlayer.getPlayer().setGameMode(GameMode.ADVENTURE);
        twPlayer.setInLobby(true);
        giveLobbyItems(twPlayer);

        if (isStartable()) {
            startCountdown();
        }
        setBossBar(twPlayer);
    }

    /** 大厅物品：8 格"离开"时钟、7 格"统计"告示牌 */
    private void giveLobbyItems(TWPlayer twPlayer) {
        ItemStack leaveItem = new ItemStack(Material.CLOCK);
        ItemMeta leaveItemMeta = leaveItem.getItemMeta();
        leaveItemMeta.displayName(Messages.Lobby.lobbyLeaveItemName());
        leaveItemMeta.lore(List.of(Component.text("右键 -> 离开", NamedTextColor.GRAY)));
        leaveItem.setItemMeta(leaveItemMeta);
        twPlayer.getPlayer().getInventory().setItem(8, leaveItem);

        ItemStack statsItem = new ItemStack(Material.DARK_OAK_SIGN);
        ItemMeta statsItemMeta = statsItem.getItemMeta();
        statsItemMeta.displayName(Messages.Lobby.lobbyStatsItemName());
        statsItemMeta.lore(List.of(Component.text("右键 -> 查看统计", NamedTextColor.GRAY)));
        statsItem.setItemMeta(statsItemMeta);
        twPlayer.getPlayer().getInventory().setItem(7, statsItem);
    }

    public String getArenaName() {
        return arenaName;
    }

    public TowerMode getMode() {
        return mode;
    }

    public ArrayList<TWPlayer> getPlayers() {
        return players;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    /** 加入广播：本人 + 队内其他人 */
    private void sendPlayerJoinMessage(TWPlayer twPlayer) {
        twPlayer.getPlayer().sendMessage(Messages.Lobby.joinedQueue(players.size(), maxPlayers));
        for (TWPlayer p1 : players) {
            if (!p1.equals(twPlayer)) {
                p1.getPlayer().sendMessage(Messages.Lobby.broadcastJoinedQueue(twPlayer.getPlayer().displayName(), players.size(), maxPlayers));
            }
        }
    }

    public boolean isStartable() {
        return players.size() >= minPlayers;
    }

    /** 取消倒计时（announce=true 时向玩家广播并刷新进度条） */
    public void cancelCountdown(boolean announce) {
        if (countdownTimer != null && !countdownTimer.isCancelled()) {
            countdownTimer.cancel();
        }
        if (announce) {
            for (TWPlayer twPlayer : players) {
                twPlayer.getPlayer().sendMessage(Messages.Lobby.countdownCanceled());
            }
            updateBossBars();
        }
    }

    /** 设置玩家的排队 BossBar（竞技场名 + 人数 + 剩余倒计时） */
    private void setBossBar(TWPlayer twPlayer) {
        float progress = 1;
        if (countdownTimer != null && !countdownTimer.isCancelled()) progress = (float) countdown / startWaitTime;
        BossBar bossBar = BossBar.bossBar(
                Messages.Lobby.bossBarProgress(arenaName, players.size(), maxPlayers, countdown, progress != 1),
                progress, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        twPlayer.setBossBar(bossBar);
    }

    /** 刷新队内全部玩家的 BossBar */
    private void updateBossBars() {
        float progress = 1;
        if (countdownTimer != null && !countdownTimer.isCancelled()) progress = (float) countdown / startWaitTime;

        for (TWPlayer twPlayer : players) {
            if (twPlayer.getBossBar() == null) {
                setBossBar(twPlayer);
                continue;
            }
            BossBar bossBar = twPlayer.getBossBar();
            bossBar.name(Messages.Lobby.bossBarProgress(arenaName, players.size(), maxPlayers, countdown, progress != 1));
            bossBar.progress(progress);
        }
    }
}
