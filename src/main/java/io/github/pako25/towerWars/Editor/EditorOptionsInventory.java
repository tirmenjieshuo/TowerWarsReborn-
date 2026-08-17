package io.github.pako25.towerWars.Editor;

import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.item.Click;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 竞技场编辑器选项菜单（InvUI 版，45 格）：世界名 / 轨道出生点 / 轨道边界 /
 * 轨道路径 / 大厅位置 / 塔放置方块 / 启用状态 / 测试竞技场 八个配置入口。
 *
 * 设计意图：
 * InvUI 迁移后每个配置项是一个可点击物品，左键/右键通过 ClickType 区分
 * （与原 itemClick 的 isLeftClick/isRightClick 语义一致）；点击后重建菜单
 * 展示最新配置进度。
 */
public class EditorOptionsInventory {

    private final TWPlayer twPlayer;
    private final JavaPlugin plugin;
    private final ArenaEditor arenaEditor;
    private Gui gui;
    private Window window;

    public EditorOptionsInventory(JavaPlugin plugin, TWPlayer twPlayer, ArenaEditor arenaEditor) {
        this.twPlayer = twPlayer;
        this.plugin = plugin;
        this.arenaEditor = arenaEditor;
        rebuild();
    }

    private void rebuild() {
        Gui newGui = Gui.empty(9, 5);

        // 世界名：显示当前设置（null = 未设置，红色）
        ItemStack worldNameItem = new ItemStack(Material.OAK_SIGN, 1);
        ItemMeta worldNameMeta = worldNameItem.getItemMeta();
        NamedTextColor worldNameColor = arenaEditor.getWorldName() == null ? NamedTextColor.RED : NamedTextColor.GREEN;
        worldNameMeta.displayName(Messages.Editor.configureWorldName());
        worldNameMeta.lore(List.of(
                Component.text(""),
                Component.text("世界名: " + arenaEditor.getWorldName(), worldNameColor),
                Messages.Editor.loreLeftClickSetNewAtCurrentWorld()
        ));
        worldNameItem.setItemMeta(worldNameMeta);
        newGui.setSlotElement(11, slot(worldNameItem, click -> {
            arenaEditor.setWorldName(twPlayer.getPlayer().getWorld().getName());
            reopen();
        }));

        // 轨道出生点：数量 2~6 为就绪
        ItemStack trackSpawnsItem = new ItemStack(Material.GOLD_BLOCK, 1);
        ItemMeta trackSpawnsMeta = trackSpawnsItem.getItemMeta();
        NamedTextColor trackSpawnsColor = arenaEditor.getTrackSpawns().size() <= arenaEditor.getAvailableColors().size() && arenaEditor.getTrackSpawns().size() > 1 ? NamedTextColor.GREEN : NamedTextColor.RED;
        trackSpawnsMeta.displayName(Messages.Editor.configureTrackSpawns());
        List<Component> trackSpawnsLore = new ArrayList<>(List.of(
                Component.text(""),
                Messages.Editor.configured(arenaEditor.getTrackSpawns().size(), arenaEditor.getAvailableColors().size()).color(trackSpawnsColor),
                Messages.Editor.loreLeftClickGetEditStick(),
                Component.text("右键 -> 删除最后一个", NamedTextColor.GRAY),
                Component.text("")
        ));
        for (Location trackSpawn : arenaEditor.getTrackSpawns()) {
            trackSpawnsLore.add(Component.text(" - [", NamedTextColor.GRAY)
                    .append(Component.text(trackSpawn.getBlockX() + " " + trackSpawn.getBlockY() + " " + trackSpawn.getBlockZ() + "]")));
        }
        trackSpawnsMeta.lore(trackSpawnsLore);
        trackSpawnsItem.setItemMeta(trackSpawnsMeta);
        newGui.setSlotElement(13, slot(trackSpawnsItem, this::handleTrackSpawns));

        // 轨道边界：固定 4 个角，未设置出生点时提示先设出生点
        ItemStack trackBoundsItem = new ItemStack(Material.OAK_FENCE);
        ItemMeta trackBoundsMeta = trackBoundsItem.getItemMeta();
        trackBoundsMeta.displayName(Messages.Editor.configureTrackBounds());
        NamedTextColor trackBoundsColor = arenaEditor.getTrackBoundsLength() == 4 ? NamedTextColor.GREEN : NamedTextColor.RED;
        List<Component> trackBoundsLore = new ArrayList<>(List.of(
                Component.text(""),
                Messages.Editor.configured(arenaEditor.getTrackBoundsLength(), 4).color(trackBoundsColor),
                Messages.Editor.loreLeftClickGetEditFence(),
                Component.text("右键 -> 删除最后一个", NamedTextColor.GRAY),
                Component.text(""),
                Messages.Editor.carefullyReadInstructions(),
                Component.text("")
        ));
        Location firstTrackSpawn;
        try {
            firstTrackSpawn = arenaEditor.getTrackSpawns().getFirst().clone();
        } catch (NoSuchElementException e) {
            firstTrackSpawn = null;
        }

        if (firstTrackSpawn == null) {
            trackBoundsLore.add(Messages.Editor.needTrackSpawn());
        } else {
            for (Vector trackBound : arenaEditor.getTrackBounds()) {
                if (trackBound == null) continue;
                Location trackBoundLocation = firstTrackSpawn.add(trackBound);
                trackBoundsLore.add(Component.text(" - [", NamedTextColor.GRAY)
                        .append(Component.text(trackBoundLocation.getBlockX() + " " + trackBoundLocation.getBlockY() + " " + trackBoundLocation.getBlockZ() + "]")));
            }
        }
        trackBoundsMeta.lore(trackBoundsLore);
        trackBoundsItem.setItemMeta(trackBoundsMeta);
        newGui.setSlotElement(15, slot(trackBoundsItem, click -> handleTrackBounds(click)));

        // 轨道路径：至少一条 ≥3 路径点的路径为就绪
        ItemStack trackPathsItem = new ItemStack(Material.RAIL);
        ItemMeta trackPathsMeta = trackPathsItem.getItemMeta();
        trackPathsMeta.displayName(Messages.Editor.configureTrackPaths());
        boolean pathsConfigurationValid = !arenaEditor.getPaths().isEmpty();
        for (List<Vector> path : arenaEditor.getPaths()) {
            if (path.size() < 3) {
                pathsConfigurationValid = false;
                break;
            }
        }
        List<Component> trackPathsLore = new ArrayList<>(List.of(
                Component.text(""),
                Component.text("已配置: " + (pathsConfigurationValid ? "是" : "否"), pathsConfigurationValid ? NamedTextColor.GREEN : NamedTextColor.RED),
                Messages.Editor.loreLeftClickGetTools(),
                Component.text("右键 -> 新建路径", NamedTextColor.GRAY),
                Component.text(""),
                Messages.Editor.carefullyReadInstructions(),
                Component.text("")
        ));
        int pathCount = 0;
        for (List<Vector> path : arenaEditor.getPaths()) {
            trackPathsLore.add(Component.text("路径 " + pathCount + ": " + path.size() + " 个路径点", NamedTextColor.GRAY));
            pathCount++;
        }
        trackPathsMeta.lore(trackPathsLore);
        trackPathsItem.setItemMeta(trackPathsMeta);
        newGui.setSlotElement(28, slot(trackPathsItem, this::handleTrackPaths));

        // 启用状态：绿/红羊毛切换
        ItemStack trackEnabledItem = new ItemStack(arenaEditor.isArenaEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL);
        ItemMeta trackEnabledMeta = trackEnabledItem.getItemMeta();
        trackEnabledMeta.displayName(Messages.Editor.toggleEnabledStatus());
        trackEnabledMeta.lore(List.of(Component.text("状态: ", NamedTextColor.WHITE)
                .append(arenaEditor.isArenaEnabled() ? Messages.Editor.enabled() : Messages.Editor.disabled())));
        trackEnabledItem.setItemMeta(trackEnabledMeta);
        newGui.setSlotElement(30, slot(trackEnabledItem, click -> {
            arenaEditor.setEnabled(!arenaEditor.isArenaEnabled());
            reopen();
        }));

        // 大厅位置：未设置时玩家排队原地等待
        ItemStack lobbySpawnItem = new ItemStack(Material.GLASS);
        ItemMeta lobbySpawnMeta = lobbySpawnItem.getItemMeta();
        boolean configuredLobbySpawn = arenaEditor.getLobbySpawn() != null;
        lobbySpawnMeta.displayName(Messages.Editor.setPregameLobbySpawn());
        lobbySpawnMeta.lore(List.of(
                Component.text("未设置时玩家不会传送", NamedTextColor.GRAY),
                Messages.Editor.loreLeftClickSetNew(),
                Component.text("位置: " + (configuredLobbySpawn ? arenaEditor.getLobbySpawn().toVector() : "未设置"), configuredLobbySpawn ? NamedTextColor.GREEN : NamedTextColor.RED)
        ));
        lobbySpawnItem.setItemMeta(lobbySpawnMeta);
        newGui.setSlotElement(32, slot(lobbySpawnItem, click -> {
            arenaEditor.setLobbySpawn(twPlayer.getPlayer().getLocation());
            reopen();
        }));

        // 塔放置方块：对话输入材质名
        ItemStack towerBlockItem = new ItemStack(Material.COBBLESTONE);
        ItemMeta towerBlockMeta = towerBlockItem.getItemMeta();
        towerBlockMeta.displayName(Messages.Editor.towerPlaceBlock());
        boolean towerPlaceMaterialSet = arenaEditor.getTowerPlaceMaterial() != null;
        towerBlockMeta.lore(List.of(
                Messages.Editor.loreLeftClickSetNewMaterial(),
                Component.text("当前设置: " + (towerPlaceMaterialSet ? arenaEditor.getTowerPlaceMaterial() : "无"), towerPlaceMaterialSet ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.empty(),
                Component.text("定义玩家可以放塔的方块类型", NamedTextColor.WHITE)
        ));
        towerBlockItem.setItemMeta(towerBlockMeta);
        newGui.setSlotElement(34, slot(towerBlockItem, click -> startMaterialConversation()));

        // 测试竞技场：保存配置并单人开局验证路径/防守
        ItemStack testArenaItem = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta testArenaMeta = testArenaItem.getItemMeta();
        testArenaMeta.displayName(Messages.Editor.testArenaItem());
        testArenaMeta.lore(List.of(
                Messages.Editor.testArenaItemLore(),
                Component.text("无需启用竞技场即可测试", NamedTextColor.GRAY)
        ));
        testArenaItem.setItemMeta(testArenaMeta);
        newGui.setSlotElement(36, slot(testArenaItem, click -> {
            // 测试竞技场：关菜单 → 保存并退出编辑 → 立即单人开局
            if (window != null) window.close();
            ArenaEditor.closeInstanceByPlayer(twPlayer, true);
            GameManager.getInstance().startSoloTest(twPlayer, arenaEditor.getArenaName());
        }));

        this.gui = newGui;
    }

    /** 通用槽位构建：可点击物品 + 处理器 */
    private SlotElement slot(ItemStack item, java.util.function.Consumer<Click> handler) {
        return new SlotElement.ItemSlotElement(new SimpleItem(item, handler));
    }

    // ========== 点击处理（左键 = 设置/发工具，右键 = 删除/新建） ==========

    /** 轨道出生点：左键发配置木棍，右键删最后一个 */
    private void handleTrackSpawns(Click click) {
        boolean isLeftClick = click.getClickType().isLeftClick();
        boolean isRightClick = click.getClickType().isRightClick();
        if (isRightClick) {
            if (arenaEditor.getTrackSpawns().isEmpty()) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.noTrackSpawnsLeft());
                return;
            }
            Location removed = arenaEditor.removeLastTrackSpawn();
            twPlayer.getPlayer().sendMessage(Messages.Editor.removedTrackSpawn(removed.toVector()));
        }
        if (isLeftClick) {
            ItemStack stick = new ItemStack(Material.STICK, 1);
            ItemMeta stickMeta = stick.getItemMeta();
            stickMeta.displayName(Messages.Editor.trackSpawnConfigurator());
            stickMeta.lore(List.of(
                    Messages.Editor.loreLeftClickToSetNewTrackSpawn(),
                    Component.text("右键 -> 删除轨道出生点", NamedTextColor.GRAY)
            ));
            stick.setItemMeta(stickMeta);

            arenaEditor.giveDefaultInventory();
            twPlayer.getPlayer().getInventory().addItem(stick);
        }
        reopen();
    }

    /** 轨道边界：左键传送到出生点并发栅栏工具，右键删最后一个 */
    private void handleTrackBounds(Click click) {
        boolean isLeftClick = click.getClickType().isLeftClick();
        boolean isRightClick = click.getClickType().isRightClick();
        if (isRightClick) {
            if (arenaEditor.getTrackBoundsLength() == 0) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.noTrackBoundsLeft());
                return;
            }
            Vector removed = arenaEditor.removeLastTrackBound();
            twPlayer.getPlayer().sendMessage(Messages.Editor.removedTrackBound(removed));
        }
        if (isLeftClick) {
            try {
                Location trackSpawn = arenaEditor.getTrackSpawns().getFirst();
                twPlayer.getPlayer().teleport(trackSpawn.clone().add(0, 2, 0));
            } catch (NoSuchElementException e) {
                twPlayer.getPlayer().sendMessage(Messages.Editor.needOneTrackSpawnFirst());
                return;
            }
            ItemStack fence = new ItemStack(Material.OAK_FENCE, 1);
            ItemMeta fenceMeta = fence.getItemMeta();
            fenceMeta.displayName(Messages.Editor.trackBoundsConfigurator());
            fenceMeta.lore(List.of(
                    Messages.Editor.loreLeftClickToSetNewTrackBound(),
                    Component.text("右键 -> 删除轨道边界", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("边界是相对你被传送到的", NamedTextColor.GRAY),
                    Component.text("轨道出生点的向量！", NamedTextColor.GRAY)
            ));
            fence.setItemMeta(fenceMeta);

            arenaEditor.giveDefaultInventory();
            twPlayer.getPlayer().getInventory().addItem(fence);
        }
        reopen();
    }

    /** 轨道路径：右键新建路径，左键发路径点工具与路径选择器 */
    private void handleTrackPaths(Click click) {
        boolean isLeftClick = click.getClickType().isLeftClick();
        boolean isRightClick = click.getClickType().isRightClick();
        if (isRightClick) {
            arenaEditor.getPaths().add(new ArrayList<>());
        }
        if (isLeftClick || (isRightClick && twPlayer.getPlayer().getInventory().contains(Material.RAIL))) {
            if (isLeftClick) {
                try {
                    Location trackSpawn = arenaEditor.getTrackSpawns().getFirst();
                    twPlayer.getPlayer().teleport(trackSpawn.clone().add(0, 2, 0));
                } catch (NoSuchElementException e) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.needOneTrackSpawnFirst());
                    return;
                }
            }
            ItemStack rail = new ItemStack(Material.RAIL);
            ItemMeta railMeta = rail.getItemMeta();
            railMeta.displayName(Messages.Editor.waypointConfigurator());
            railMeta.lore(List.of(
                    Messages.Editor.loreLeftClickToSetNewWaypoint(),
                    Component.text("右键 -> 删除路径点", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("用路径选择工具选择", NamedTextColor.GRAY),
                    Messages.Editor.loreWithPathSelectionTool(),
                    Component.empty(),
                    Component.text("路径点是相对你被传送到的", NamedTextColor.GRAY),
                    Component.text("轨道出生点的向量！", NamedTextColor.GRAY)
            ));
            rail.setItemMeta(railMeta);

            ItemStack repeater = new ItemStack(Material.REPEATER);
            ItemMeta repeaterMeta = repeater.getItemMeta();
            repeaterMeta.displayName(Messages.Editor.pathSelectionTool());
            List<Component> repeaterLore = new ArrayList<>(List.of(
                    Messages.Editor.loreLeftClickToChangePath(),
                    Messages.Editor.loreRightClickToRemoveSelectedPath()
            ));
            int pathCount = 0;
            for (List<Vector> path : arenaEditor.getPaths()) {
                NamedTextColor pathColor = pathCount == arenaEditor.getSelectedPathIndex() ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
                repeaterLore.add(Component.text("路径 " + pathCount + ": " + path.size() + " 个路径点", pathColor));
                pathCount++;
            }
            repeaterMeta.lore(repeaterLore);
            repeater.setItemMeta(repeaterMeta);

            arenaEditor.giveDefaultInventory();
            twPlayer.getPlayer().getInventory().addItem(rail);
            twPlayer.getPlayer().getInventory().addItem(repeater);
        }
        reopen();
    }

    /** 材质输入对话：聊天框输入 Minecraft 材质名（Material 枚举） */
    private void startMaterialConversation() {
        if (window != null) window.close();
        StringPrompt prompt = new StringPrompt() {
            @Override
            public @NotNull String getPromptText(@NotNull ConversationContext context) {
                return Messages.Editor.enterMaterialPrompt().toString();
            }

            @Override
            public @Nullable Prompt acceptInput(@NotNull ConversationContext context, @Nullable String input) {
                try {
                    if (input == null) throw new IllegalArgumentException();
                    Material newMaterial = Material.matchMaterial(input, false);
                    if (newMaterial == null || !newMaterial.isBlock()) throw new IllegalArgumentException();
                    arenaEditor.setTowerPlaceMaterial(newMaterial);
                    twPlayer.getPlayer().sendMessage(Messages.Editor.materialSetSuccessfully());
                } catch (IllegalArgumentException e) {
                    twPlayer.getPlayer().sendMessage(Messages.Editor.invalidMaterial());
                    // 附上材质名列表链接，帮助玩家找到合法材质名
                    twPlayer.getPlayer().sendMessage(Component.text("可用材质列表见 ", NamedTextColor.WHITE)
                            .append(Component.text("此处", NamedTextColor.RED)
                                    .clickEvent(ClickEvent.openUrl("https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html"))
                                    .hoverEvent(HoverEvent.showText(Component.text("https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html")))));
                }
                twPlayer.unfreeze();
                return END_OF_CONVERSATION;
            }
        };
        Conversation conversation = new Conversation(plugin, twPlayer.getPlayer(), prompt);
        twPlayer.getPlayer().beginConversation(conversation);
        twPlayer.getPlayer().showTitle(Title.title(Messages.Editor.writeMaterialInChatTitle(), Component.empty(), Title.DEFAULT_TIMES));
        twPlayer.freeze();
    }

    /** 打开编辑器选项菜单 */
    public void open() {
        window = Window.single(builder -> builder
                .setGui(gui)
                .setTitle(new AdventureComponentWrapper(Messages.Editor.editorOptionsMenuTitle()))
                .setViewer(twPlayer.getPlayer()));
        window.open();
    }

    /** 点击后刷新：关旧窗口、重建（展示最新配置进度）、重开 */
    private void reopen() {
        if (window != null && window.isOpen()) {
            window.close();
        }
        rebuild();
        open();
    }
}
