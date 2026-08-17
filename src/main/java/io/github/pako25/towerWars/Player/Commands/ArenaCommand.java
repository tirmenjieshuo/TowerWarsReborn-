package io.github.pako25.towerWars.Player.Commands;

import io.github.pako25.towerWars.Editor.ArenaEditor;
import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * /towerwars arena 子命令组：list（列表）/ configure（进入编辑器）/
 * saveconfiguration / discardconfiguration（保存或放弃编辑）。
 */
public class ArenaCommand implements SubcommandHandler {

    @Override
    public void onCommand(TWPlayer twPlayer, String[] args) {
        if (args.length == 0) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.usage());
            return;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "list" -> handleList(twPlayer, args);
            case "configure" -> handleConfigure(twPlayer, args);
            case "saveconfiguration" -> handleSaveDiscard(twPlayer, args, true);
            case "discardconfiguration" -> handleSaveDiscard(twPlayer, args, false);
            default -> twPlayer.getPlayer().sendMessage(Messages.Cmd.unknownSubcommand(subcommand));
        }
    }

    /** arena list：竞技场 + 启用/禁用 + 空闲/进行中 */
    private void handleList(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.list")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (args.length != 1) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars arena list"));
            return;
        }
        List<String> allArenas = GameManager.getInstance().getAllArenas();
        List<Component> statuses = new ArrayList<>();
        for (String arenaName : allArenas) {
            // 轻量读取状态即可：竞技场可能处于"未配置完成"的模板状态，
            // ArenaConfig 的全量解析会因缺配置段抛异常（见 GameManager.isArenaEnabled 注释）
            boolean enabled = GameManager.getInstance().isArenaEnabled(arenaName);
            boolean available = GameManager.getInstance().isArenaFree(arenaName);
            NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
            statuses.add(Component.text(arenaName, NamedTextColor.WHITE)
                    .append(Component.text("（", NamedTextColor.GRAY))
                    .append(enabled ? Messages.Cmd.arenaStatusEnabled() : Messages.Cmd.arenaStatusDisabled())
                    .append(Component.text("）（", NamedTextColor.GRAY))
                    .append(available ? Messages.Cmd.arenaStatusFree() : Messages.Cmd.arenaStatusOccupied())
                    .append(Component.text("）", NamedTextColor.GRAY)));
            if (!allArenas.getLast().equals(arenaName)) statuses.add(Component.newline());
        }
        twPlayer.getPlayer().sendMessage(Messages.Cmd.loadedArenas().appendNewline().append(statuses));
    }

    /** arena configure <name>：进入（或新建）竞技场编辑器 */
    private void handleConfigure(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.configure")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (twPlayer.isInGame() || twPlayer.isInLobby()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.cantOpenEditorInGame());
            return;
        }
        if (twPlayer.isInEditor()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.alreadyInEditingMode());
            return;
        }
        if (args.length != 2) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars arena configure <竞技场>"));
            return;
        }
        ArenaEditor.newInstance(args[1], twPlayer);
    }

    /** saveconfiguration / discardconfiguration：结束编辑会话 */
    private void handleSaveDiscard(TWPlayer twPlayer, String[] args, boolean save) {
        if (twPlayer.getPlayer().isConversing()) {
            twPlayer.getPlayer().sendMessage(Messages.Editor.answerFirst());
            return;
        }
        if (!twPlayer.getPlayer().hasPermission("towerwars.configure")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (args.length != 1) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars arena " + (save ? "saveconfiguration" : "discardconfiguration")));
            return;
        }
        if (!twPlayer.isInEditor()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.notInEditingMode());
            return;
        }
        ArenaEditor.closeInstanceByPlayer(twPlayer, save);
    }

    @Override
    public List<String> onTabComplete(TWPlayer twPlayer, String[] args) {
        if (args.length == 1) {
            List<String> availableSubcommands = new ArrayList<>();
            if (twPlayer.getPlayer().hasPermission("towerwars.configure")) {
                availableSubcommands.addAll(List.of("configure", "saveconfiguration", "discardconfiguration"));
            }
            if (twPlayer.getPlayer().hasPermission("towerwars.list")) {
                availableSubcommands.add("list");
            }
            return availableSubcommands.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length != 0 && args[0].equals("configure") && twPlayer.getPlayer().hasPermission("towerwars.configure")) {
            return GameManager.getInstance().getAllArenas().stream()
                    .filter(arenaName -> GameManager.getInstance().isArenaFree(arenaName) && arenaName.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
