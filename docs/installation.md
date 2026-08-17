# 安装指南

## 环境要求

- **Minecraft 服务端版本**：Paper / Purpur 1.21 或更高
  > ⚠️ 仅在 **Paper 1.21.4 与 1.21.7** 上测试过——其他版本请自行承担风险。
- **Java**：服务端运行 Java 21 或更高（插件编译目标为 Java 21）

## 安装步骤

1. **获取插件 jar**
   - 从 [Releases 页面](../../releases) 下载，或自行构建（见下方"从源码构建"）。

2. **安装插件**
   把下载的 `.jar` 文件放进服务端的 `plugins/` 目录。
   > ✅ 插件为 fat-jar，**已内置** InvUI GUI 库——**不要**再单独安装任何
   > InvUI / 其他 GUI 库插件，避免类冲突。

3. **重启服务端**
   请**重启**（而非 reload）服务端，让插件正确加载与初始化。

4. **验证安装**
   检查控制台日志，确认 `TowerWarsReborn` 的启动成功信息（含
   "已加载 8 座塔的配置" 与统计追踪状态）。

5. **首次配置竞技场**
   - 在游戏内执行 `/towerwars arena configure arena1` 配置默认竞技场
     （世界名 → 出生点 → 边界 → 路径 → 放塔方块）
   - 点击编辑器中的**"测试竞技场"**按钮验证配置（单人开局，无需启用）
   - 确认无误后点绿羊毛启用竞技场，玩家即可 `/towerwars join arena1` 加入

## 从源码构建

- **JDK 21+**（必须，见下）
- 在仓库根目录执行：

  ```bash
  ./gradlew build
  ```

  产物：`build/libs/TowerWarsReborn-1.0-SNAPSHOT.jar`

> ⚠️ **JDK 版本要求**：Gradle 守护进程必须使用 **≤21 的 JDK**——系统默认
> JDK 26 会让 Gradle 8.14 的 Groovy 编译器报
> `Unsupported class file major version 70`。`gradle.properties` 已配置本机
> JDK 21 路径（`C:/java/zulu21.44.17-ca-jdk21.0.8-win_x64`），其他机器请按
> 注释修改为本机的 JDK 21 路径，或用 `org.gradle.java.home` 指定。

---

## 进阶

如果想在其他版本上使用本插件，可以从源码自行针对目标版本编译。
但请注意：插件使用了 NMS（net.minecraft.server，怪物导航与 InvUI 访问层）
且尚未实现反射兼容，跨版本兼容性因此很低——不过只要 Mojang 不大改核心结构，
移植应该不算太难。

---

## 下一步

- [配置指南](configuration.md)：自定义塔、竞技场、创建加入牌、玩法模式……
- [使用说明](usage.md)：学习基础命令与三种玩法模式
