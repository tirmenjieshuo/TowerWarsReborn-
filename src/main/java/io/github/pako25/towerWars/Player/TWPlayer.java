package io.github.pako25.towerWars.Player;

import io.github.pako25.towerWars.Arena.MobData.MobStates;
import io.github.pako25.towerWars.Arena.MobType;
import io.github.pako25.towerWars.Arena.Track;
import io.github.pako25.towerWars.Editor.ArenaEditor;
import io.github.pako25.towerWars.Editor.EditorOptionsInventory;
import io.github.pako25.towerWars.GameManagement.Game;
import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.GameManagement.PlayerStats;
import io.github.pako25.towerWars.Player.Inventories.PlaceTowerInventory;
import io.github.pako25.towerWars.Player.Inventories.SummonMobInventory;
import io.github.pako25.towerWars.Player.Inventories.UpgradeTowerInventory;
import io.github.pako25.towerWars.Tower.TowerType;
import io.github.pako25.towerWars.config.TowerConfig;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.GameRules;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class TWPlayer {
    private static final Map<UUID, TWPlayer> TWPlayerMap = new HashMap<>();

    private PlaceTowerInventory placeTowerInventory;
    private SummonMobInventory summonMobInventory;
    private ArenaEditor activeArenaEditor;
    private final Player player;
    private final JavaPlugin plugin;
    private Track track;
    private Game game;
    private Location placeTowerClickLocation;
    private Sidebar sidebar;
    private boolean inGame = false;
    private boolean inEditor = false;
    private boolean inLobby = false;
    private Location locationBeforeGame;
    private BossBar bossBar;

    private int coin = 0;
    private int income = 0;
    private int stock = 30;
    /** 本人独立的召唤状态（收入进化/召唤加成），围攻进攻者无赛道也用它 */
    private MobStates mobStates;

    private TWPlayer(Player player, JavaPlugin plugin) {
        this.player = player;
        this.plugin = plugin;
    }

    public static TWPlayer newTWPlayer(Player player, JavaPlugin plugin) {
        TWPlayer TWPlayer = new TWPlayer(player, plugin);
        TWPlayerMap.put(player.getUniqueId(), TWPlayer);
        PlayerStats.getStats(player.getUniqueId());
        return TWPlayer;
    }

    public static Collection<TWPlayer> getTWPlayerCollection() {
        return TWPlayerMap.values();
    }

    public static void removePlayer(UUID uuid) {
        TWPlayerMap.remove(uuid);
    }

    public static TWPlayer getTWPlayer(UUID uuid) {
        return TWPlayerMap.get(uuid);
    }

    public Player getPlayer() {
        return player;
    }

    public void openSummonMobInventory() {
        if (!inGame) return;
        if (summonMobInventory == null) {
            summonMobInventory = new SummonMobInventory(plugin, this);
        }
        summonMobInventory.open();
    }

    public void clickOnBlock(Location targetBlock) {
        if (!inGame) return;
        if (isAttacker()) {
            player.sendMessage(Messages.Game.attackerCannotPlaceTower());
            return;
        }
        if (track.isBlockOccupiedByTower(targetBlock)) {
            openTowerMenu(targetBlock);
        } else {
            openPlaceTowerInventory(targetBlock);
        }
    }

    public void openPlaceTowerInventory(Location targetBlock) {
        if (!inGame) return;
        if (isAttacker()) {
            player.sendMessage(Messages.Game.attackerCannotPlaceTower());
            return;
        }
        boolean onTrack = track.isLocationInsideTrackBounds(targetBlock);
        if (!onTrack) {
            player.sendMessage(Messages.Gui.outsideTrackBounds());
            return;
        }

        placeTowerClickLocation = targetBlock;
        if (placeTowerInventory == null) {
            placeTowerInventory = new PlaceTowerInventory(plugin, this);
        }
        placeTowerInventory.reopen();
    }

    public void openTowerMenu(Location location) {
        if (!inGame) return;
        if (isAttacker()) {
            player.sendMessage(Messages.Game.attackerCannotPlaceTower());
            return;
        }
        new UpgradeTowerInventory(plugin, this, track.getTowers().get(location), location).open();
    }

    public Track getTrack() {
        return track;
    }

    public boolean summonMob(MobType mobType, int cost, int income) {
        if (cost > coin) {
            return false;
        }
        stock--;
        coin = coin - cost;
        if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).increaseGold_spent(cost);
        if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).increaseMobs_sent();
        increaseIncome(income);
        updateSidebar(false);
        mobStates.getMobState(mobType).incrementSummon();
        if (isAttacker()) {
            // 围攻进攻者：怪发往防守者赛道
            game.attackerSummonMob(mobType, this);
        } else {
            game.sendMonstersFrom(track.getUUID(), mobType, this);
        }
        return true;
    }

    public boolean placeTower(TowerType towerType, int level, int prestige, Component towerName) {
        int cost = TowerConfig.buyCost(towerType, level, prestige);
        if (!track.hasSpaceLeft()) {
            player.sendMessage(Messages.Tower.maxTowersReached());
            return false;
        }
        boolean success = buyForCoin(cost);
        if (success) {
            if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).increaseTowers_placed();
            track.placeTower(towerType, placeTowerClickLocation, level, prestige);
            player.sendMessage(Messages.Tower.placedTower(towerName, cost, track.getTowers().size(), track.getMaxTowers()));
        }
        return success;
    }

    /** 有赛道的玩家开局（经典/合作/围攻防守者）：传送赛道、发放放塔+召唤+升级物品 */
    public void gameStart(Track track, Game game) {
        this.track = track;
        this.game = game;
        this.mobStates = new MobStates();
        this.sidebar = new Sidebar(game, this);
        inGame = true;
        inLobby = false;

        coin = GameRules.STARTING_COINS;
        income = GameRules.STARTING_INCOME;

        clearBossBar();
        player.sendMessage(Messages.Game.started());
        player.teleport(track.getTrackSpawn().clone().add(0, 2, 0));
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        player.setInvulnerable(true);
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setHealth(20);

        // 三件游戏物品：放置塔 / 召唤怪 / 升级塔（右键交互，监听器按显示名识别）
        // lore 写明各自用法，玩家拿到物品即知如何操作
        player.getInventory().addItem(gameItem(Material.ARMOR_STAND, Messages.Gui.itemPlaceTower(),
                List.of(Messages.Gui.itemPlaceTowerLore(), Messages.Gui.itemPlaceTowerLore2())));
        // 围攻模式防守者不能送怪（怪是进攻方的事情）
        if (game.getMode() != TowerMode.SIEGE) {
            player.getInventory().addItem(gameItem(Material.NETHER_STAR, Messages.Gui.itemSummonMob(),
                    List.of(Messages.Gui.itemSummonMobLore())));
        }
        player.getInventory().addItem(gameItem(Material.EXPERIENCE_BOTTLE, Messages.Gui.itemUpgradeTower(),
                List.of(Messages.Gui.itemUpgradeTowerLore())));
    }

    /** 围攻进攻者开局：无赛道，只发召唤怪物品，提示玩法 */
    public void attackerGameStart(Game game) {
        this.game = game;
        this.mobStates = new MobStates();
        this.sidebar = new Sidebar(game, this);
        inGame = true;
        inLobby = false;

        coin = GameRules.STARTING_COINS;
        income = GameRules.STARTING_INCOME;

        clearBossBar();
        player.sendMessage(Messages.Game.attackerStarted());
        player.getInventory().clear();
        player.setAllowFlight(false);
        player.setInvulnerable(true);
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setHealth(20);

        player.getInventory().addItem(gameItem(Material.NETHER_STAR, Messages.Gui.itemSummonMob(),
                List.of(Messages.Gui.itemSummonMobLore())));
    }

    /** 构建带中文显示名与用法说明（lore）的游戏物品 */
    private ItemStack gameItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void gameEnd(boolean lost) {
        inGame = false;
        track = null;
        game = null;
        try {
            sidebar.delete();
        } catch (Exception e) {
            plugin.getLogger().warning("侧边栏删除失败: " + e.getMessage());
        }
        sidebar = null;
        player.getInventory().clear();
        player.closeInventory();
        player.setAllowFlight(false);
        player.setInvulnerable(false);

        if (locationBeforeGame != null) {
            player.teleport(locationBeforeGame.clone().add(0, 2, 0));
        }

        if (lost) {
            if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).increaseGames_lost();
            player.showTitle(Title.title(Messages.Game.youLostTitle(), Component.text(""), Title.DEFAULT_TIMES));
        } else {
            if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).increaseGames_won();
            player.showTitle(Title.title(Messages.Game.youWonTitle(), Component.text(""), Title.DEFAULT_TIMES));
        }

        if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).saveToDatabase();
    }

    /** 围攻进攻者：没有赛道，随时可放弃（视为进攻方弃权） */
    public void attackerForfeit() {
        if (game != null) {
            game.attackerForfeit(this);
        }
        gameEnd(true);
    }

    public void openArenaEditorInventory(ArenaEditor arenaEditor) {
        new EditorOptionsInventory(plugin, this, arenaEditor).open();
    }

    public void leaveServer() {
        clearBossBar();
        if (inGame) {
            if (isAttacker()) {
                // 围攻进攻者：无赛道，直接视为弃权
                attackerForfeit();
            } else {
                GameManager.getInstance().leaveQueue(this);
            }
            inGame = false;
            track = null;
            game = null;
            sidebar = null;
        }
        if (inEditor) {
            ArenaEditor.closeInstanceByPlayer(this, true);
        }
        if (inLobby) {
            GameManager.getInstance().leaveQueue(this);
        }
        PlayerStats.closePlayerInstance(player.getUniqueId());
    }

    public void updateSidebar(boolean tracksChanged) {
        if (sidebar == null) return;
        // 围攻进攻者倒计时用围攻限时，其余用总倒计时
        int timer = isAttacker() ? game.getSiegeTimer() : game.getGameTimer();
        sidebar.updateSidebar(coin, income, game.getIncomeTimer(), timer, tracksChanged);
    }

    public void clearBossBar() {
        if (bossBar != null) {
            player.hideBossBar(bossBar);
            bossBar = null;
        }
    }

    public void setBossBar(BossBar bossBar) {
        if (this.bossBar != null) clearBossBar();
        this.bossBar = bossBar;
        player.showBossBar(bossBar);
    }

    public void recieveIncome() {
        player.sendMessage(Messages.Game.passiveIncome(income));
        coin += income;
    }

    public void increaseCoin(int amount) {
        coin += amount;
    }

    public void increaseIncome(int amount) {
        income += amount;
    }

    public boolean buyForCoin(int amount) {
        if (amount <= coin) {
            coin = coin - amount;
            if (PlayerStats.trackingEnabled) PlayerStats.getStats(player.getUniqueId()).increaseGold_spent(amount);
            return true;
        }
        player.sendMessage(Messages.Tower.notEnoughGold());
        return false;
    }

    public void increaseStock() {
        // 库存随时间恢复：随时间增长（最多 +5/秒），上限 30
        int amount = (game.getTickCounter() / GameRules.STOCK_RECOVERY_DIVISOR) + 1;
        if (stock < GameRules.MAX_STOCK) stock += Math.min(amount, GameRules.STOCK_RECOVERY_CAP);
        if (stock > GameRules.MAX_STOCK) stock = GameRules.MAX_STOCK;
        if (summonMobInventory != null) {
            summonMobInventory.refreshIfOpen();
        }
    }

    public void freeze() {
        player.setInvulnerable(true);
        List<PotionEffect> effects = List.of(
                new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 255, false, false, false),
                new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 1, false, false, false),
                new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 255, false, false, false)
        );
        player.addPotionEffects(effects);
        player.setGameMode(GameMode.ADVENTURE);
    }

    public void unfreeze() {
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        if (player.getPreviousGameMode() != null)
            player.setGameMode(player.getPreviousGameMode());
    }

    public int getIncome() {
        return income;
    }

    public int getStock() {
        return stock;
    }

    public boolean isInGame() {
        return inGame;
    }

    public int getCoin() {
        return coin;
    }

    public boolean isInEditor() {
        return inEditor;
    }

    public void setInEditor(boolean inEditor) {
        this.inEditor = inEditor;
    }

    public ArenaEditor getActiveArenaEditor() {
        return activeArenaEditor;
    }

    public void setActiveArenaEditor(ArenaEditor arenaEditor) {
        activeArenaEditor = arenaEditor;
    }

    /** 全服 TWPlayer 注册表（调试工具与跨赛道遍历用） */
    public static Map<UUID, TWPlayer> getTWPlayerMap() {
        return TWPlayerMap;
    }

    public Location getLocationBeforeGame() {
        return locationBeforeGame;
    }

    public void setLocationBeforeGame(Location locationBeforeGame) {
        this.locationBeforeGame = locationBeforeGame;
    }

    public void setInLobby(boolean inLobby) {
        this.inLobby = inLobby;
    }

    public boolean isInLobby() {
        return inLobby;
    }

    public Game getGame() {
        return game;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    /** 本人独立的召唤状态（收入进化/召唤加成） */
    public MobStates getMobStates() {
        return mobStates;
    }

    /** 围攻模式的进攻者：在游戏中但没有赛道（只能送怪） */
    public boolean isAttacker() {
        return inGame && track == null && game != null && game.getMode() == TowerMode.SIEGE;
    }
}