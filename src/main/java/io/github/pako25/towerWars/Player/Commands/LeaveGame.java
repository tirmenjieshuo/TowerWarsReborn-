package io.github.pako25.towerWars.Player.Commands;

import io.github.pako25.towerWars.GameManagement.GameManager;
import io.github.pako25.towerWars.Player.TWPlayer;
import io.github.pako25.towerWars.message.Messages;

import java.util.List;

/**
 * /towerwars leave：离开队列（或放弃进行中的游戏）。
 */
public class LeaveGame implements SubcommandHandler {

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
        if (!twPlayer.isInGame() && !twPlayer.isInLobby()) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.notInGame());
            return;
        }

        if (args.length != 0) {
            twPlayer.getPlayer().sendMessage(Messages.Cmd.invalidArguments("/towerwars leave"));
            return;
        }
        GameManager.getInstance().leaveQueue(twPlayer);
    }

    @Override
    public List<String> onTabComplete(TWPlayer twPlayer, String[] args) {
        return List.of();
    }
}
