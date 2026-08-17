package io.github.pako25.towerWars.Editor;

import io.github.pako25.towerWars.CustomConfig;
import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.Main;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 竞技场编辑器：一个编辑会话对应一名玩家 + 一个竞技场，用"世界内点击"
 * 配置出生点/边界/路径，GUI 选项菜单配置世界名/大厅/材质/启用状态。
 *
 * 设计意图：
 * 编辑会话注册表 EditorList 保证同一竞技场同时只有一位编辑者；
 * 保存时把内存数据序列化为 yml（出生点是绝对坐标，边界/路径点是相对
 * 出生点的向量），加载既有竞技场时反序列化回来。
 * 修复的原版 bug：加载既有竞技场时 enabled 被硬编码为 false（读配置的
 * 代码被注释掉了），每次进编辑器保存都会把竞技场改回禁用——现改为
 * 正常读取配置的 enabled 字段。
 */
public class ArenaEditor {

    private static final List<ArenaEditor> EditorList = new ArrayList<>();
    private final CustomConfig config;
    private final List<NamedTextColor> availableColors;
    private final Vector[] trackBounds = new Vector[4];
    private final ArrayList<ArrayList<Vector>> paths = new ArrayList<>();
    private final List<Location> trackSpawns = new ArrayList<>();
    private final String arenaName;
    private final TWPlayer twPlayer;
    private final JavaPlugin plugin;
    private String worldName;
    private boolean enabled;
    private static ItemStack instructionsBook;
    private int selectedPathIndex = 0;
    private Location lobbySpawn;
    private Material towerPlaceMaterial;

    /** 创建编辑会话（同一竞技场已被编辑时拒绝） */
    public static void newInstance(String arenaName, TWPlayer twPlayer) {
        for (ArenaEditor arenaEditor : EditorList) {
            if (arenaEditor.getArenaName().equals(arenaName)) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.someoneElseEditing());
                return;
            }
        }
        EditorList.add(new ArenaEditor(arenaName, twPlayer));
    }

    /** 按玩家关闭编辑会话（save=true 保存配置，false 放弃修改） */
    public static void closeInstanceByPlayer(TWPlayer twPlayer, boolean save) {
        Iterator<ArenaEditor> iterator = EditorList.iterator();
        while (iterator.hasNext()) {
            ArenaEditor arenaEditor = iterator.next();
            if (arenaEditor.isPlayerEditor(twPlayer.getPlayer().getUniqueId())) {
                if (save) {
                    arenaEditor.saveConfig();
                    twPlayer.getPlayer().sendMessage(Messages.Editor.configurationSaved());
                } else {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.changesDiscarded());
                }
                iterator.remove();
            }
        }

        twPlayer.setInEditor(false);
        twPlayer.setActiveArenaEditor(null);
        twPlayer.getPlayer().closeInventory(); // 关闭可能开着的编辑器选项窗口（InvUI）
        twPlayer.getPlayer().getInventory().clear();
        twPlayer.getPlayer().sendMessage(Messages.Editor.quitEditingMode());
    }

    /** 服务端关闭：自动保存所有编辑会话 */
    public static void closeAllEditors() {
        for (ArenaEditor arenaEditor : EditorList) {
            arenaEditor.saveConfig();
        }
        EditorList.clear();
    }

    private ArenaEditor(String arenaName, TWPlayer twPlayer) {
        this.arenaName = arenaName;
        this.twPlayer = twPlayer;
        this.plugin = JavaPlugin.getPlugin(Main.class);
        this.availableColors = GameManager.getInstance().getAllColors();

        List<String> allArenas = GameManager.getInstance().getAllArenas();
        if (allArenas.contains(arenaName)) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.editingExistingArena());
            config = CustomConfig.getCustomConfig(arenaName);
            config.reload();
            loadExistingArena();
        } else {
            twPlayer.getPlayer().sendMessage(Messages.Editor.creatingNewArena(arenaName));
            CustomConfig.setup(arenaName);
            config = CustomConfig.getCustomConfig(arenaName);
            GameManager.getInstance().addArenaToAllArenas(arenaName);
        }

        enterEditingMode();
        showEditorOptions();
    }

    /** 打开编辑器选项菜单 */
    public void showEditorOptions() {
        twPlayer.openArenaEditorInventory(this);
    }

    private void enterEditingMode() {
        twPlayer.setInEditor(true);
        twPlayer.setActiveArenaEditor(this);
        if (!trackSpawns.isEmpty()) {
            twPlayer.getPlayer().teleport(trackSpawns.getFirst().clone().add(0, 1, 0));
        }
        twPlayer.getPlayer().setGameMode(GameMode.CREATIVE);
        giveDefaultInventory();
    }

    /** 从 yml 反序列化既有竞技场的全部配置 */
    private void loadExistingArena() {
        // 启用状态（原版 bug：被硬编码为 false，现正常读取）
        enabled = config.getCustomFile().getBoolean("enabled");

        // 大厅位置
        List<?> locationRaw = config.getCustomFile().getList("lobbyLocation");
        if (locationRaw == null || locationRaw.size() != 3) {
            lobbySpawn = null;
        } else {
            lobbySpawn = new Location(twPlayer.getPlayer().getWorld(), (int) locationRaw.get(0), (int) locationRaw.get(1), (int) locationRaw.get(2));
        }

        // 世界名
        worldName = config.getCustomFile().getString("worldName");

        // 轨道出生点（绝对坐标）
        List<?> spawnsRaw = config.getCustomFile().getList("trackSpawns");
        if (spawnsRaw != null) {
            for (Object spawnObj : spawnsRaw) {
                if (!(spawnObj instanceof List<?> coords) || coords.size() != 3) {
                    plugin.getLogger().warning("非法的 trackSpawn 条目，已移除: " + spawnObj);
                    continue;
                }
                World world = twPlayer.getPlayer().getWorld();
                trackSpawns.add(new Location(world, (int) coords.get(0), (int) coords.get(1), (int) coords.get(2)));
            }

            if (trackSpawns.size() > availableColors.size()) {
                plugin.getLogger().warning("轨道出生点不能超过 " + availableColors.size() + " 个！多余的将被移除！");
                return;
            }
        }

        // 轨道边界（相对出生点的向量，Y 忽略）
        List<?> boundsRaw = config.getCustomFile().getList("trackBounds");
        if (boundsRaw != null) {
            for (int i = 0; i < boundsRaw.size(); i++) {
                if (!(boundsRaw.get(i) instanceof List<?> coords) || coords.size() != 3) {
                    plugin.getLogger().warning("非法的 trackBounds 条目，已移除: " + boundsRaw.get(i));
                    continue;
                }
                trackBounds[i] = new Vector((int) coords.get(0), (int) coords.get(1), (int) coords.get(2));
            }
        }

        // 路径（相对向量的序列）
        List<?> rawPaths = config.getCustomFile().getList("paths");
        if (rawPaths != null) {
            for (Object rawPathObj : rawPaths) {
                if (!(rawPathObj instanceof List<?> rawPathList)) {
                    plugin.getLogger().warning("非法的 path 条目，已跳过: " + rawPathObj);
                    continue;
                }

                ArrayList<Vector> path = new ArrayList<>();
                for (Object coordObj : rawPathList) {
                    if (!(coordObj instanceof List<?> coordList) || coordList.size() != 3) {
                        plugin.getLogger().warning("非法的坐标，已跳过: " + coordObj);
                        continue;
                    }
                    path.add(new Vector((int) coordList.get(0), (int) coordList.get(1), (int) coordList.get(2)));
                }

                if (!path.isEmpty()) {
                    paths.add(path);
                }
            }
        }

        // 塔放置方块
        String towerPlaceMaterialRaw = config.getCustomFile().getString("towerPlaceMaterial");
        if (towerPlaceMaterialRaw != null) {
            towerPlaceMaterial = Material.matchMaterial(towerPlaceMaterialRaw, false);
        }
    }

    /** 序列化内存配置到 yml 并保存 */
    private void saveConfig() {
        FileConfiguration cfg = config.getCustomFile();
        cfg.set("worldName", worldName);

        List<List<Integer>> trackSpawnsFormattedList = new ArrayList<>();
        for (Location trackSpawn : trackSpawns) {
            trackSpawnsFormattedList.add(List.of(trackSpawn.getBlockX(), trackSpawn.getBlockY(), trackSpawn.getBlockZ()));
        }
        cfg.set("trackSpawns", trackSpawnsFormattedList);

        List<List<Integer>> trackBoundsFormattedList = new ArrayList<>();
        for (Vector trackBound : trackBounds) {
            if (trackBound == null) continue;
            trackBoundsFormattedList.add(List.of(trackBound.getBlockX(), trackBound.getBlockY(), trackBound.getBlockZ()));
        }
        cfg.set("trackBounds", trackBoundsFormattedList);

        List<List<List<Integer>>> pathsFormattedList = new ArrayList<>();
        for (ArrayList<Vector> path : paths) {
            List<List<Integer>> pathFormattedList = new ArrayList<>();
            for (Vector waypoint : path) {
                pathFormattedList.add(List.of(waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ()));
            }
            pathsFormattedList.add(pathFormattedList);
        }
        cfg.set("paths", pathsFormattedList);

        if (lobbySpawn == null) {
            cfg.set("lobbyLocation", null);
        } else {
            cfg.set("lobbyLocation", List.of(lobbySpawn.getBlockX(), lobbySpawn.getBlockY(), lobbySpawn.getBlockZ()));
        }

        cfg.set("towerPlaceMaterial", towerPlaceMaterial.name());
        cfg.set("enabled", enabled);

        config.save();
    }

    /** 发放编辑器默认物品：指南针（选项）/ 说明成书 / 时钟（退出） */
    public void giveDefaultInventory() {
        twPlayer.getPlayer().getInventory().clear();

        ItemStack optionsItem = new ItemStack(Material.COMPASS, 1);
        ItemMeta optionsItemMeta = optionsItem.getItemMeta();
        optionsItemMeta.displayName(Messages.Editor.editorOptionsItem());
        optionsItemMeta.lore(List.of(Messages.Editor.editorOptionsItemLore()));
        optionsItem.setItemMeta(optionsItemMeta);

        ItemStack saveItem = new ItemStack(Material.CLOCK);
        ItemMeta saveItemMeta = saveItem.getItemMeta();
        saveItemMeta.displayName(Messages.Editor.exitItem());
        saveItemMeta.lore(List.of(
                Messages.Editor.exitLoreSave(),
                Messages.Editor.exitLoreDiscard()
        ));
        saveItem.setItemMeta(saveItemMeta);

        twPlayer.getPlayer().getInventory().setItem(7, optionsItem);
        twPlayer.getPlayer().getInventory().setItem(6, instructionsBook);
        twPlayer.getPlayer().getInventory().setItem(8, saveItem);
    }

    // ========== 轨道出生点 ==========

    public Location removeLastTrackSpawn() {
        return trackSpawns.removeLast();
    }

    /** 添加出生点（校验世界匹配与重复） */
    public void addNewTrackSpawn(Location location) {
        if (isWorldInvalid(location.getWorld().getName())) return;
        if (trackSpawns.contains(location)) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.trackSpawnAlreadyExists());
            return;
        }
        trackSpawns.add(location);
        twPlayer.getPlayer().sendMessage(Messages.Editor.addedTrackSpawn(location.toVector()));
    }

    /** 按位置移除出生点（返回 null = 未找到） */
    public Location removeTrackSpawn(Location location) {
        Iterator<Location> iterator = trackSpawns.iterator();
        while (iterator.hasNext()) {
            Location trackSpawn = iterator.next();
            if (trackSpawn.equals(location)) {
                iterator.remove();
                return trackSpawn;
            }
        }
        return null;
    }

    // ========== 轨道边界 ==========

    /** 添加边界：记录"点击位置 - 出生点"的相对向量 */
    public void addNewTrackBound(Location location) {
        if (isWorldInvalid(location.getWorld().getName())) return;
        try {
            Location trackSpawn = trackSpawns.getFirst();
            Vector trackBound = location.clone().subtract(trackSpawn).toVector();
            for (int i = 0; i < trackBounds.length; i++) {
                if (trackBounds[i] != null) {
                    if (trackBounds[i].equals(trackBound)) {
                        twPlayer.getPlayer().sendMessage(Messages.Editor.trackBoundAlreadyExists());
                        return;
                    }
                    continue;
                }
                trackBounds[i] = trackBound;
                break;
            }
        } catch (NoSuchElementException e) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.needTrackSpawnFirst());
        }
    }

    /** 移除最后一个边界 */
    public Vector removeLastTrackBound() {
        for (int i = trackBounds.length - 1; i > -1; i--) {
            if (trackBounds[i] != null) {
                Vector removed = trackBounds[i];
                trackBounds[i] = null;
                return removed;
            }
        }
        return null;
    }

    /** 按位置移除边界 */
    public Vector removeTrackBound(Location location) {
        try {
            Location trackSpawn = trackSpawns.getFirst();
            Vector trackBound = location.clone().subtract(trackSpawn).toVector();
            for (int i = 0; i < trackBounds.length; i++) {
                if (trackBounds[i] == null) continue;
                if (trackBounds[i].equals(trackBound)) {
                    trackBounds[i] = null;
                    return trackBound;
                }
            }
        } catch (NoSuchElementException e) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.needTrackSpawnFirst());
        }
        return null;
    }

    // ========== 路径与路径点 ==========

    /** 删除当前选中的路径 */
    public void removeSelectedPath() {
        if (paths.isEmpty()) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.noPathsLeft());
            return;
        }

        try {
            int pathSize = paths.get(selectedPathIndex).size();
            paths.remove(selectedPathIndex);
            twPlayer.getPlayer().sendMessage(Messages.Editor.removedPath(pathSize));
        } catch (IndexOutOfBoundsException e) {
            selectedPathIndex = 0;
        }

        selectedPathIndex--;
        if (selectedPathIndex < 0) selectedPathIndex = 0;
    }

    /** 向当前选中的路径追加路径点（相对出生点的向量） */
    public void addNewWaypoint(Location location) {
        if (isWorldInvalid(location.getWorld().getName())) return;
        if (paths.isEmpty()) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.needPathFirst());
            return;
        }
        try {
            Location trackSpawn = trackSpawns.getFirst();
            Vector waypoint = location.clone().subtract(trackSpawn).toVector();
            if (paths.get(selectedPathIndex).contains(waypoint)) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.waypointAlreadyExists());
                return;
            }
            paths.get(selectedPathIndex).add(waypoint);
            twPlayer.getPlayer().sendMessage(Messages.Editor.addedWaypoint());
        } catch (NoSuchElementException e) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.needTrackSpawnFirst());
        } catch (IndexOutOfBoundsException e) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.waypointAddError());
        }
    }

    /** 从当前选中的路径移除路径点 */
    public void removeWaypoint(Location location) {
        if (paths.isEmpty()) return;
        if (isWorldInvalid(location.getWorld().getName())) return;
        try {
            Location trackSpawn = trackSpawns.getFirst();
            Vector waypoint = location.clone().subtract(trackSpawn).toVector();
            boolean existed = paths.get(selectedPathIndex).remove(waypoint);
            if (existed) {
                twPlayer.getPlayer().sendMessage(Component.text("已删除路径点: " + waypoint, NamedTextColor.YELLOW));
            }
        } catch (NoSuchElementException e) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.needTrackSpawnFirst());
        } catch (IndexOutOfBoundsException e) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.waypointRemoveError());
        }
    }

    /** 世界校验：必须已设置 worldName 且与点击位置同世界 */
    private boolean isWorldInvalid(String worldName) {
        if (this.worldName == null) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.needWorldNameFirst());
            return true;
        }
        if (!this.worldName.equals(worldName)) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.worldMismatch(this.worldName));
            return true;
        }
        return false;
    }

    /** 切换当前编辑的路径（循环轮转） */
    public void changeSelectedPathIndex() {
        if (paths.isEmpty()) {
            selectedPathIndex = 0;
            return;
        }
        if (selectedPathIndex == paths.size() - 1) {
            selectedPathIndex = 0;
        } else {
            selectedPathIndex++;
        }
        twPlayer.getPlayer().sendMessage(Messages.Editor.selectedPath(selectedPathIndex, paths.get(selectedPathIndex).size()));
    }

    /**
     * 启用前的完整性校验：世界存在、出生点 2~6 个、边界 4 个且成矩形、
     * 每条路径 ≥3 点且相邻点只差一个坐标方向。
     */
    private boolean verifyConfigurationValidity() {
        if (worldName == null) return false;
        if (towerPlaceMaterial == null) return false;
        if (plugin.getServer().getWorld(worldName) == null) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.worldDoesNotExist());
            return false;
        }
        if (trackSpawns.size() > availableColors.size() || trackSpawns.size() < 2) return false;
        if (getTrackBoundsLength() != 4) return false;
        if (paths.isEmpty()) return false;
        for (Location location : trackSpawns) {
            if (isWorldInvalid(location.getWorld().getName())) return false;
        }
        // 矩形校验：每个角恰好与另外两个角在一条直线上（只差一维）
        for (Vector v1 : trackBounds) {
            int inlineCounter = 0;
            for (Vector v2 : trackBounds) {
                if (!v1.equals(v2)) {
                    if (areVectorsDifferentInOneDimension(v1, v2)) inlineCounter++;
                }
            }
            if (inlineCounter != 2) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.boundsNotRectangle());
                return false;
            }
        }
        for (List<Vector> path : paths) {
            if (path.size() < 3) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.pathTooShort());
                return false;
            }
            for (int i = 1; i < path.size() - 1; i++) {
                if (!areVectorsDifferentInOneDimension(path.get(i), path.get(i - 1)) || !areVectorsDifferentInOneDimension(path.get(i), path.get(i + 1))) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.pathPointsTooFar());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isPlayerEditor(UUID uuid) {
        return uuid.equals(twPlayer.getPlayer().getUniqueId());
    }

    // ========== 只读访问器 ==========

    public List<NamedTextColor> getAvailableColors() {
        return availableColors;
    }

    public Vector[] getTrackBounds() {
        return trackBounds;
    }

    public ArrayList<ArrayList<Vector>> getPaths() {
        return paths;
    }

    public List<Location> getTrackSpawns() {
        return trackSpawns;
    }

    public int getSelectedPathIndex() {
        return selectedPathIndex;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public Material getTowerPlaceMaterial() {
        return towerPlaceMaterial;
    }

    public void setTowerPlaceMaterial(Material material) {
        towerPlaceMaterial = material;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public boolean isArenaEnabled() {
        return enabled;
    }

    /** 只差一个坐标方向（Y 忽略）——边界与路径的"直线性"判定核心 */
    private boolean areVectorsDifferentInOneDimension(Vector v1, Vector v2) {
        int differentDimensions = 0;
        if (v1.getX() != v2.getX()) differentDimensions++;
        if (v1.getZ() != v2.getZ()) differentDimensions++;
        return differentDimensions == 1;
    }

    /** 切换启用状态：启用前先做完整性校验，不合法则拒绝并提示 */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            enabled = verifyConfigurationValidity();
            if (!enabled) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.configurationInvalid());
            }
        }
        this.enabled = enabled;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getArenaName() {
        return arenaName;
    }

    public int getTrackBoundsLength() {
        int counter = 0;
        for (Vector bound : trackBounds) {
            if (bound != null) counter++;
        }
        return counter;
    }

    /** 生成 7 页中文使用说明成书（编辑器物品栏第 6 格） */
    public static void generateInstructionsBook() {
        if (instructionsBook != null) return;
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        bookMeta.author(Component.text("TowerWars"));
        bookMeta.title(Messages.Editor.instructionsBookTitle());
        bookMeta.addPages(
                Component.text("竞技场世界", NamedTextColor.RED).appendNewline().appendNewline()
                        .append(Component.text("竞技场世界是所有赛道所在的世界。你无法在任何其他世界里设置轨道出生点、边界或路径点。", NamedTextColor.BLACK)),
                Component.text("轨道出生点", NamedTextColor.RED).appendNewline().appendNewline()
                        .append(Component.text("每个轨道出生点对应一条赛道。赛道有自己的边界与路径点。所有赛道的边界、路径配置和朝向都相同，但其他方面可以不同。", NamedTextColor.GRAY, TextDecoration.ITALIC))
                        .appendNewline().append(Component.text("            继续->", NamedTextColor.BLACK)),
                Component.text("轨道出生点是每条赛道的'锚点'。它是唯一以绝对坐标保存的位置，所有边界与路径点都是向量，相对各自的轨道出生点偏移。这样便于整体移动和配置赛道。", NamedTextColor.BLACK),
                Component.text("轨道边界", NamedTextColor.RED).appendNewline().appendNewline()
                        .append(Component.text("轨道边界是赛道的二维'角点'。Y 坐标会被忽略，因此赛道不能上下堆叠。塔只能放置在轨道边界内部。", NamedTextColor.BLACK)),
                Component.text("边界必须构成矩形。只需为一条赛道设置边界，其他赛道会自动同步。路径点同理。", NamedTextColor.BLACK).appendNewline()
                        .append(Component.text("（阅读第 3 页）", NamedTextColor.BLACK)),
                Component.text("路径", NamedTextColor.RED).appendNewline().appendNewline()
                        .append(Component.text("路径由告诉怪物往哪走的路径点组成。怪物只能直线移动。一条赛道可以有多条路径。", NamedTextColor.BLACK)),
                Component.text("路径点", NamedTextColor.RED).appendNewline().appendNewline()
                        .append(Component.text("路径点必须按正确顺序设置（起点到终点）。相邻两个路径点之间最多只能在一个坐标方向上不同。", NamedTextColor.BLACK))
        );
        book.setItemMeta(bookMeta);
        instructionsBook = book;
    }
}
