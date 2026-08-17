package io.github.pako25.towerWars.Player.Commands;

import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;
import io.github.pako25.towerWars.util.TowerMode;

import java.util.List;

/**
 * /towerwars join &lt;竞技场&gt;：按权限 → 状态 → 竞技场可用性逐层校验后加入队列。
 */
public class JoinGame implements SubcommandHandler {

    @Override
    public void onCommand(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.play")) {
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

        if (args.length < 1 || args.length > 2) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars join <竞技场> [模式]"));
            return;
        }
        String arena = args[0];

        // 可选模式参数：classic / siege / coop，缺省经典
        TowerMode mode = TowerMode.CLASSIC;
        if (args.length == 2) {
            mode = TowerMode.fromString(args[1]);
            if (mode == null) {
                twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidMode(args[1]));
                return;
            }
        }

        GameManager gameManager = GameManager.getInstance();

        if (!gameManager.arenaExists(arena)) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.arenaDoesNotExist());
            return;
        }

        if (!gameManager.isArenaEnabled(arena)) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.arenaDisabled());
            return;
        }

        if (!gameManager.isArenaFree(arena)) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.arenaOccupied());
            return;
        }

        gameManager.joinQueue(twPlayer, arena, mode);
    }

    @Override
    public List<String> onTabComplete(TWPlayer twPlayer, String[] args) {
        if (!twPlayer.getPlayer().hasPermission("towerwars.play")) return List.of();
        if (twPlayer.isInEditor() || twPlayer.isInGame() || twPlayer.isInLobby()) return List.of();
        if (args.length == 1) return GameManager.getInstance().getAvailableArenas();
        if (args.length == 2) {
            return List.of("classic", "siege", "coop").stream()
                    .filter(m -> m.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
