package io.github.pako25.towerWars.Player;

import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.GameManagement.Game;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.List;

/**
 * 每玩家计分板侧边栏：金币/收入/下次收入/倒计时 + 各赛道生命（自己的标"（你）"）。
 * 模式适配：围攻进攻者显示"围攻剩余时间 + 防守者生命"；合作模式追加波次行。
 *
 * 设计意图：
 * 侧边栏是"值变化才更新"的增量式刷新（updateSidebar 只重写变化的行，
 * 避免每 tick 全量重建 Scoreboard 的明显开销）；赛道生命行由 game 通知
 * （tracksChanged）时统一刷新。Adventure 组件经 LegacyComponentSerializer
 * 转 § 色码字符串写入计分板（Bukkit 计分板只认 legacy 格式）。
 */
public class Sidebar {

    private final Game game;
    private final TWPlayer twPlayer;
    private Scoreboard scoreboard;
    private int lastCoin = 75;
    private int lastIncome = 0;
    private int lastNextIncome = 5;
    private int lastTimer = 0;
    private int lastDefenderLives = 0;
    private int lastWave = 0;

    /** 各赛道出生点 → 计分板行号与上一次生命（增量刷新用） */
    private final HashMap<Location, TrackLivesAndScoreboardIndexHolder> lastTrackLivesAndIndexes = new HashMap<>();

    public Sidebar(Game game, TWPlayer twPlayer) {
        this.game = game;
        this.twPlayer = twPlayer;
        showSidebar();
    }

    /** 初次构建侧边栏（比赛开始时调用），按模式/角色布局 */
    public void showSidebar() {
        Player player = twPlayer.getPlayer();

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective(
                String.valueOf(player.getUniqueId()),
                Criteria.DUMMY,
                Messages.Gui.sidebarTitle()
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        if (twPlayer.isAttacker()) {
            // 围攻进攻者：倒计时（14）+ 防守者生命（13）+ 空（12）+ 金币/收入/下次收入
            objective.getScore(timerBuilder(lastTimer)).setScore(14);
            objective.getScore(defenderLivesBuilder(game.getDefenderLives())).setScore(13);
            objective.getScore("").setScore(12);
            objective.getScore(goldBuilder(lastCoin)).setScore(5);
            objective.getScore(incomeBuilder(lastIncome)).setScore(4);
            objective.getScore(nextIncomeBuilder(lastNextIncome)).setScore(3);
            player.setScoreboard(scoreboard);
            return;
        }

        // 行号布局：14 倒计时 / 7~12 各赛道 / 6 空（合作模式为波次行）/ 5 金币 / 4 收入 / 3 下次收入
        objective.getScore(timerBuilder(lastTimer)).setScore(14);
        objective.getScore(goldBuilder(lastCoin)).setScore(5);
        objective.getScore(incomeBuilder(lastIncome)).setScore(4);
        objective.getScore(nextIncomeBuilder(lastNextIncome)).setScore(3);

        if (game.getMode() == TowerMode.CO_OP) {
            objective.getScore(waveBuilder(game.getWave(), game.getMaxWaves())).setScore(6);
        } else {
            objective.getScore("").setScore(6);
        }

        List<Track> trackList = game.getTrackList();
        for (int i = 0; i < trackList.size(); i++) {
            Track track = trackList.get(i);
            int index = 7 + i;
            objective.getScore(trackBuilder(track, track.getLives())).setScore(index);
            lastTrackLivesAndIndexes.put(track.getTrackSpawn(), new TrackLivesAndScoreboardIndexHolder(track.getLives(), index));
        }
        objective.getScore(" ").setScore(8 + trackList.size());

        player.setScoreboard(scoreboard);
    }

    /** 增量刷新：tracksChanged 时只刷赛道生命行；否则只刷变化的经济/计时行 */
    public void updateSidebar(int coin, int income, int nextIncome, int timer, boolean tracksChanged) {
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;

        if (twPlayer.isAttacker()) {
            // 围攻进攻者：防守者生命变化走 tracksChanged 分支，其余走通用分支
            if (tracksChanged) {
                int defenderLives = game.getDefenderLives();
                if (defenderLives != lastDefenderLives) {
                    scoreboard.resetScores(defenderLivesBuilder(lastDefenderLives));
                    objective.getScore(defenderLivesBuilder(defenderLives)).setScore(13);
                    lastDefenderLives = defenderLives;
                }
                return;
            }
        } else {
            if (tracksChanged) {
                List<Track> trackList = game.getTrackList();
                for (Track track : trackList) {
                    TrackLivesAndScoreboardIndexHolder record = lastTrackLivesAndIndexes.get(track.getTrackSpawn());
                    if (record == null) continue;
                    if (track.getLives() != record.lives) {
                        scoreboard.resetScores(trackBuilder(track, record.lives));
                        objective.getScore(trackBuilder(track, track.getLives())).setScore(record.index);
                        record.lives = track.getLives();
                    }
                }
                return;
            }
            // 合作模式：波次变化刷新波次行
            if (game.getMode() == TowerMode.CO_OP && game.getWave() != lastWave) {
                scoreboard.resetScores(waveBuilder(lastWave, game.getMaxWaves()));
                objective.getScore(waveBuilder(game.getWave(), game.getMaxWaves())).setScore(6);
                lastWave = game.getWave();
            }
        }

        if (coin != lastCoin) {
            scoreboard.resetScores(goldBuilder(lastCoin));
            objective.getScore(goldBuilder(coin)).setScore(5);
            lastCoin = coin;
        }
        if (income != lastIncome) {
            scoreboard.resetScores(incomeBuilder(lastIncome));
            objective.getScore(incomeBuilder(income)).setScore(4);
            lastIncome = income;
        }
        if (nextIncome != lastNextIncome) {
            scoreboard.resetScores(nextIncomeBuilder(lastNextIncome));
            objective.getScore(nextIncomeBuilder(nextIncome)).setScore(3);
            lastNextIncome = nextIncome;
        }
        if (timer != lastTimer) {
            scoreboard.resetScores(timerBuilder(lastTimer));
            objective.getScore(timerBuilder(timer)).setScore(14);
            lastTimer = timer;
        }
    }

    /** 比赛结束：清空侧边栏槽位 */
    public void delete() {
        scoreboard.getScoresFor(twPlayer.getPlayer());
        scoreboard.clearSlot(DisplaySlot.SIDEBAR);
        this.scoreboard = null;
    }

    // —— 行构建：Adventure 组件 → legacy § 串 ——

    private String goldBuilder(int gold) {
        return serialize(Messages.Gui.sidebarGold().append(Component.text(gold, NamedTextColor.YELLOW)));
    }

    private String incomeBuilder(int income) {
        return serialize(Messages.Gui.sidebarIncome().append(Component.text(income, NamedTextColor.YELLOW)));
    }

    private String nextIncomeBuilder(int nextIncome) {
        return serialize(Messages.Gui.sidebarNextIncome().append(Component.text(nextIncome, NamedTextColor.YELLOW)));
    }

    private String timerBuilder(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return serialize(Messages.Gui.sidebarTimer()
                .append(Component.text(minutes + ":" + (seconds >= 10 ? seconds : "0" + seconds), NamedTextColor.GREEN)));
    }

    /** 围攻防守者生命行（进攻者视角） */
    private String defenderLivesBuilder(int lives) {
        return serialize(Component.text("防守者生命: ", NamedTextColor.YELLOW)
                .append(Component.text(lives + " ❤", NamedTextColor.RED)));
    }

    /** 合作波次行 */
    private String waveBuilder(int wave, int maxWaves) {
        return serialize(Component.text("波次: ", NamedTextColor.YELLOW)
                .append(Component.text(wave + "/" + maxWaves, NamedTextColor.GREEN)));
    }

    /** 赛道行：颜色中文名 + 生命，自己的赛道加"（你）"标记 */
    private String trackBuilder(Track track, int lives) {
        Component component = Component.text(Messages.colorName(track.getColor()), track.getColor())
                .append(Component.text(": " + lives, NamedTextColor.WHITE));
        if (twPlayer.getTrack() != null && track.getUUID().equals(twPlayer.getTrack().getUUID())) {
            component = component.append(Component.text("（你）", NamedTextColor.GRAY));
        }
        return serialize(component);
    }

    private String serialize(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}

/** 侧边栏赛道行的缓存记录：上一帧生命 + 固定行号 */
class TrackLivesAndScoreboardIndexHolder {
    public int lives;
    public int index;

    public TrackLivesAndScoreboardIndexHolder(int lives, int index) {
        this.lives = lives;
        this.index = index;
    }
}
