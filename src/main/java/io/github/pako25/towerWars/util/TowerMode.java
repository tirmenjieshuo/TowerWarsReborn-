package io.github.pako25.towerWars.util;

/**
 * 玩法模式枚举。
 *
 * 设计意图：
 * 一局游戏的角色分配由模式决定：
 * - CLASSIC：每人一条赛道，既能放塔又能送怪（原版行为）；
 * - SIEGE：1 名防守者（只能放塔守赛道）+ N 名进攻者（只能送怪），
 *   防守者生命归零 = 进攻方胜，限时守住 = 防守方胜；
 * - CO_OP：全体玩家同队各守各的赛道，对抗系统波次刷怪，
 *   打完所有波次且仍有赛道存活 = 全员胜利。
 * 新玩法只需在枚举里加一项，并在 Game 的模式分支里实现对应规则。
 */
public enum TowerMode {

    /** 经典：每人一条赛道，放塔 + 送怪（原版行为） */
    CLASSIC,
    /** 围攻 1vN：1 守 vs N 攻 */
    SIEGE,
    /** 合作：同队守波次（PvE） */
    CO_OP;

    /** 按名字解析（大小写不敏感），非法返回 null（调用方提示） */
    public static TowerMode fromString(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
