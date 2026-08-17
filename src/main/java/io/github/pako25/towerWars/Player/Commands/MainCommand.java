package io.github.pako25.towerWars.Player.Commands;

import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /towerwars 主命令（别名 /tw）：子命令分发器。
 * 子命令：join / leave / arena / forcestart / debug / increaseincome / help。
 * Tab 补全按权限过滤候选。
 */
public class MainCommand implements TabExecutor {

    private final JoinGame joinGameHandler = new JoinGame();
    private final LeaveGame leaveGameHandler = new LeaveGame();
    private final ArenaCommand arenaCommandHandler = new ArenaCommand();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.Cmd.onlyPlayers()); // 控制台/命令方块也可以收到组件消息
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Messages.Cmd.usage());
            return true;
        }

        String subcommand = args[0].toLowerCase();
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());

        switch (subcommand) {
            case "join" -> joinGameHandler.onCommand(twPlayer, Arrays.copyOfRange(args, 1, args.length));
            case "leave" -> leaveGameHandler.onCommand(twPlayer, Arrays.copyOfRange(args, 1, args.length));
            case "arena" -> arenaCommandHandler.onCommand(twPlayer, Arrays.copyOfRange(args, 1, args.length));
            case "forcestart" -> handleForceStart(twPlayer, args);
            case "test" -> handleTest(twPlayer, args);
            case "debug" -> handleDebug(twPlayer, args);
            case "increaseincome" -> handleIncreaseIncome(twPlayer, args);
            case "help" -> handleHelp(player);
            default -> player.sendMessage(Messages.Cmd.unknownSubcommand(subcommand).append(Component.text("。试试 /towerwars help")));
        }

        return true;
    }

    /** forcestart：跳过倒计时立即开局（需要 towerwars.forcestart） */
    private void handleForceStart(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.forcestart")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (twPlayer.isInEditor()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.inArenaConfigMode());
            return;
        }
        if (twPlayer.isInGame()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.gameOngoing());
            return;
        }
        if (args.length != 1) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars forcestart"));
            return;
        }
        GameManager.getInstance().forceStart(twPlayer);
    }

    /** test：单人测试模式立即开局（towerwars.test，无需等待其他玩家） */
    private void handleTest(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.test")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (twPlayer.isInEditor()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.inArenaConfigMode());
            return;
        }
        if (twPlayer.isInGame() || twPlayer.isInLobby()) {
            twPlayer.getPlayer().sendMessage(Messages.Lobby.alreadyInGame());
            return;
        }
        if (args.length != 2) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars test <竞技场>"));
            return;
        }
        GameManager.getInstance().startSoloTest(twPlayer, args[1]);
    }

    /** debug：发一根调试木棍（towerwars.debug） */
    private void handleDebug(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.debug")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (args.length != 1) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars debug"));
            return;
        }
        ItemStack debugItem = new ItemStack(Material.STICK, 1);
        ItemMeta debugItemMeta = debugItem.getItemMeta();
        debugItemMeta.displayName(Messages.Cmd.debugStickName());
        debugItemMeta.lore(List.of(Messages.Cmd.debugStickLore()));
        debugItem.setItemMeta(debugItemMeta);
        twPlayer.getPlayer().getInventory().addItem(debugItem);
    }

    /** increaseincome <数量>：调试用加收入（towerwars.debug，需在游戏中） */
    private void handleIncreaseIncome(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.debug")) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.noPermission());
            return;
        }
        if (args.length != 2) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars increaseincome <数量>"));
            return;
        }
        if (!twPlayer.isInGame()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.needToBeInGame());
            return;
        }
        try {
            int income = Integer.parseInt(args[1]);
            twPlayer.increaseIncome(income);
        } catch (NumberFormatException e) {
            // 参数不是数字：静默忽略（原版会抛异常崩命令）
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars increaseincome <数量>"));
        }
    }

    /** help：玩法指引 */
    private void handleHelp(Player player) {
        player.sendMessage(Messages.Cmd.helpLine1());
        player.sendMessage(Messages.Cmd.helpLine2());
        player.sendMessage(Messages.Cmd.helpLine3());
        player.sendMessage(Component.text("如果你无法使用上述命令，请联系管理员授予 towerwars.play 与 towerwars.list 权限。", net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) return List.of();
        TWPlayer twPlayer = TWPlayer.getTWPlayer(player.getUniqueId());

        return switch (args.length > 0 ? args[0] : "") {
            case "join" -> joinGameHandler.onTabComplete(twPlayer, Arrays.copyOfRange(args, 1, args.length));
            case "arena" -> arenaCommandHandler.onTabComplete(twPlayer, Arrays.copyOfRange(args, 1, args.length));
            case "test" -> player.hasPermission("towerwars.test")
                    ? (args.length > 1
                            ? GameManager.getInstance().getAllArenas().stream()
                                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).toList()
                            : GameManager.getInstance().getAllArenas())
                    : List.of();
            default -> {
                List<String> availableSubcommands = new ArrayList<>(List.of("help"));
                if (player.hasPermission("towerwars.play")) availableSubcommands.addAll(List.of("join", "leave"));
                if (player.hasPermission("towerwars.forcestart")) availableSubcommands.add("forcestart");
                if (player.hasPermission("towerwars.test")) availableSubcommands.add("test");
                if (player.hasPermission("towerwars.debug")) availableSubcommands.addAll(List.of("debug", "increaseincome"));
                if (player.hasPermission("towerwars.configure") || player.hasPermission("towerwars.list"))
                    availableSubcommands.add("arena");
                yield availableSubcommands.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
            }
        };
    }
}
