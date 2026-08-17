package io.github.pako25.towerWars.Player.Commands;

import io.github.pako25.towerWars.Player.TWPlayer;

import java.util.List;

/**
 * 子命令处理器接口：/towerwars 的每个子命令一个实现类，
 * 提供命令执行与 Tab 补全两个钩子。
 */
public interface SubcommandHandler {

    /** 执行子命令（参数已去除子命令本身） */
    void onCommand(TWPlayer twPlayer, String[] args);

    /** Tab 补全候选 */
    List<String> onTabComplete(TWPlayer twPlayer, String[] args);
}
