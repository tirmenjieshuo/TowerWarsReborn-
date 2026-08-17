package io.github.pako25.towerWars.GameManagement;

import io.github.pako25.towerWars.CustomConfig;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.TowerWars;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.TowerMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 加入牌管理器（单例 + 监听器）：玩家在告示牌上写 [TOWERWARS] + 竞技场名
 * 即可创建"可点击加入游戏"的牌子；牌子第 4 行实时显示 已禁用/已满/排队人数。
 *
 * 设计意图：
 * 牌子位置持久化在 managedSigns.yml（与原版一致，docs 中误写的 signs.yml
 * 已按代码修正）；首行 [TOWERWARS] 是解析键，保持 ASCII 不汉化。
 * 左键（有 placesign 权限）移除牌子，右键加入游戏。
 */
public class SignManager implements Listener {

    private static SignManager signManager;
    private final List<Location> managedSigns = new ArrayList<>();

    public static SignManager getInstance() {
        if (signManager == null) signManager = new SignManager();
        return signManager;
    }

    private SignManager() {
        FileConfiguration cfg = CustomConfig.getFileConfiguration("managedSigns");

        try {
            List<Location> signLocations = new ArrayList<>();
            List<?> locationsRaw = cfg.getList("locations");
            if (locationsRaw == null) return;

            for (Object locationRaw : locationsRaw) {
                if (!(locationRaw instanceof List<?> locationData) || locationData.size() != 4) {
                    throw new IllegalArgumentException("managedSigns.yml 中存在非法条目");
                }
                World world = TowerWars.getPlugin().getServer().getWorld((String) locationData.get(0));
                signLocations.add(new Location(world, (int) locationData.get(1), (int) locationData.get(2), (int) locationData.get(3)));
            }
            for (Location location : signLocations) {
                if (location.getBlock().getState() instanceof Sign) {
                    managedSigns.add(location);
                } else {
                    TowerWars.getPlugin().getLogger().warning("检测到牌子已被移除: " + location);
                }
            }
        } catch (Exception e) {
            // 数据损坏时直接关服是最安全的选择——避免刷出成堆异常日志
            TowerWars.getPlugin().getLogger().severe("解析 managedSigns.yml 时发生严重错误，请修复或删除该文件后重启。");
            TowerWars.getPlugin().getServer().shutdown();
        }
        updateSigns();
        saveManagedSigns();
    }

    /** 持久化全部受管牌子的位置 */
    public void saveManagedSigns() {
        List<List<Object>> saveList = new ArrayList<>();
        for (Location location : managedSigns) {
            saveList.add(List.of(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        }
        CustomConfig config = CustomConfig.getCustomConfig("managedSigns");
        config.getCustomFile().set("locations", saveList);
        config.save();
    }

    /** 刷新全部牌子：已消失/竞技场被删的牌子直接清掉 */
    public void updateSigns() {
        ArrayList<Location> markedForRemoval = new ArrayList<>();
        for (Location location : managedSigns) {
            boolean success = updateSign(location);
            if (!success) markedForRemoval.add(location);
        }
        if (!markedForRemoval.isEmpty()) {
            managedSigns.removeAll(markedForRemoval);
            for (Location location : markedForRemoval) {
                if (location.getBlock().getType() != Material.AIR) location.getBlock().setType(Material.AIR);
            }
            saveManagedSigns();
        }
    }

    /**
     * 刷新单个牌子第 4 行状态：
     * 已禁用 → "已禁用"；比赛进行中 → "已满"；否则 → "排队数 / 上限"。
     */
    private boolean updateSign(Location location) {
        if (!(location.getBlock().getState() instanceof Sign sign)) return false;
        if (!sign.isPlaced()) return false;
        String arenaName = PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(2));
        if (!GameManager.getInstance().arenaExists(arenaName)) return false;

        // 规格化第 2 行操作提示：兼容旧牌子（放置时未写入提示的补上，幂等不重复写）
        if (!PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(1))
                .equals(PlainTextComponentSerializer.plainText().serialize(Messages.Sign.signClickToJoin()))) {
            sign.getSide(Side.FRONT).line(1, Messages.Sign.signClickToJoin());
            sign.update(true);
        }

        if (!GameManager.getInstance().isArenaEnabled(arenaName)) {
            sign.getSide(Side.FRONT).line(3, Messages.Sign.signDisabled());
            sign.update(true);
            return true;
        }

        if (!GameManager.getInstance().isArenaFree(arenaName)) {
            sign.getSide(Side.FRONT).line(3, Messages.Sign.signFull());
            sign.update(true);
            return true;
        }

        List<?> spawnsRaw = CustomConfig.getFileConfiguration(arenaName).getList("trackSpawns");
        if (spawnsRaw == null) return false;
        int maxPlayers = spawnsRaw.size();
        int queuedPeople = GameManager.getInstance().getPeopleQueuedForArena(arenaName);
        sign.getSide(Side.FRONT).line(3, Messages.Sign.signQueueCount(queuedPeople, maxPlayers));
        sign.update(true);
        return true;
    }

    /** 写牌子事件：创建加入牌（首行 [TOWERWARS]，第二行竞技场名） */
    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        if (managedSigns.contains(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Messages.Sign.signProtected());
            return;
        }

        if (e.line(0) == null || e.line(1) == null) return;
        if (!PlainTextComponentSerializer.plainText().serialize(e.line(0)).equalsIgnoreCase("[TOWERWARS]")) return;

        if (!e.getPlayer().hasPermission("towerwars.placesign")) return;

        String arenaName = PlainTextComponentSerializer.plainText().serialize(e.line(1));
        if (!GameManager.getInstance().arenaExists(arenaName)) {
            e.getPlayer().sendMessage(Messages.Sign.arenaDoesNotExist(arenaName));
            return;
        }

        // 规格化：首行统一黑色 [TOWERWARS]，第 2 行写"右键点击加入"提示，
        // 竞技场名放到第 3 行（第 4 行留给状态刷新）
        e.line(0, Component.text("[TOWERWARS]", NamedTextColor.BLACK));
        e.line(1, Messages.Sign.signClickToJoin());
        e.line(2, Component.text(arenaName, NamedTextColor.WHITE));
        managedSigns.add(e.getBlock().getLocation());
        saveManagedSigns();
        Bukkit.getScheduler().runTaskLater(TowerWars.getPlugin(), () -> updateSign(e.getBlock().getLocation()), 1L);
    }

    /** 点击牌子：左键（有权限）移除，右键加入游戏 */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Block clickedBlock = e.getClickedBlock();
        if (!managedSigns.contains(clickedBlock.getLocation())) return;
        if (e.getAction().isLeftClick()) {
            if (e.getPlayer().hasPermission("towerwars.placesign")) {
                managedSigns.remove(clickedBlock.getLocation());
                saveManagedSigns();
            } else {
                e.setCancelled(true); // 无权限玩家不能拆牌
            }
            return;
        }
        if (!e.getAction().isRightClick()) return;
        if (!(clickedBlock.getState() instanceof Sign sign)) {
            managedSigns.remove(clickedBlock.getLocation());
            return;
        }

        e.setCancelled(true);

        if (!e.getPlayer().hasPermission("towerwars.usesign")) {
            e.getPlayer().sendMessage(Messages.Sign.noPermissionUseSign());
            return;
        }

        TWPlayer twPlayer = TWPlayer.getTWPlayer(e.getPlayer().getUniqueId());
        String arenaName = PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(2));
        if (!GameManager.getInstance().isArenaFree(arenaName)) {
            e.getPlayer().sendMessage(Messages.Sign.arenaFull());
            return;
        }
        if (GameManager.getInstance().isArenaEnabled(arenaName)) {
            // 加入牌默认经典模式（模式通过 /towerwars join <竞技场> <模式> 选择）
            GameManager.getInstance().joinQueue(twPlayer, arenaName, TowerMode.CLASSIC);
        } else {
            e.getPlayer().sendMessage(Messages.Sign.arenaDisabled());
        }
    }
}
