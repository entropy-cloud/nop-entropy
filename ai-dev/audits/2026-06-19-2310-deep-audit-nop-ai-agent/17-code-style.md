# 维度 17：代码风格与规范

## 检查范围

431 main java 文件（402 手写）；checkstyle 基线；对照 code-style.md/AGENTS.md。

## 总体结论

整体风格良好：**0** System.out/err、**0** printStackTrace、**0** public mutable field、**0** snake_case 标识符、**0** 可观察 API 拼写错误（recieve/seperate/occured 等无命中）、**0** magic number 散落（数值默认值都定义为 DEFAULT_* 常量）；包名 io.nop.ai.agent.* 一致；类名 PascalCase/方法 camelCase 无违反。import 分组虽与 code-style.md 写明顺序相反（io.nop.* → java.*），但与全仓其他模块一致，属项目级通行做法，不报。

## 第 1 轮（初审）发现

### [维度17-01] AI 风格噪音 Javadoc 大面积污染（plan/design 引用横跨 257 文件）

- **文件**: 模块级（257/402 手写文件，64% 命中），代表 `runtime/coordination/IDaemonCoordinator.java`、`reliability/ISustainer.java`、`quota/IResourceGuard.java`
- **证据片段**（IDaemonCoordinator.java:3-37，3 方法签名的 interface 却写了 105 行 Javadoc 前言）:
  ```java
  /**
   * Opt-in cross-process team-level scan-lease coordination ... (plan 242 / L4-cross-process-daemon-coordination).
   * <p><b>Why this interface exists</b>. A multi-instance deployment ...
   * ... claimTask's DB-level CAS (plan 227 / 240) ...
   * ... (design 裁定 2) ...
   * ... (design 裁定 3).
   */
  ```
- **严重程度**: P2
- **现状**: 257/402（64%）手写文件含 `plan 2XX`/`vision §X`/`design §X`/`Minimum Rules #N`/`L4-…`/`L3-…` 引用；全模块 572 处 plan 2XX 引用、482 处 § 标记；16 文件注释行占比 >55%（ISustainer 80%、IDaemonCoordinator 63%）。引用指向 ai-dev/plans/、ai-dev/design/nop-ai-agent/ 等过程文档。
- **风险**: (1)AGENTS.md 明确 ai-dev/ 是过程记录**不是**平台用户规范文档，生产 Javadoc 的 plan 234/design §5.1a 对调用方完全不可解析；(2)计划文档会重排合并，引用编号快速腐烂；(3)单方法 interface 用 100+ 行前言违反 AGENTS.md「不要噪音注释」；(4)重要业务信息被埋在 plan 编号中降低可读性。
- **建议**: 大批量删去 plan/vision/design/Minimum Rules/L4/L3 内部引用，仅保留契约必需语义说明（规则表/CAS 真值表/线程安全约束保留）；设计决策挪到 docs-for-ai/ 或 ai-dev/ 内部，不污染生产源码。
- **信心水平**: 高
- **误报排除**: 引用文件确实存在于 ai-dev/；非 docs-for-ai 平台用户文档；checkstyle 不强制 Javadoc 内容；AGENTS.md 明确反对噪音注释。
- **复核状态**: 未复核

### [维度17-02] ActorRegistry 接口缺 I 前缀，破坏模块内 65/66 接口一致性

- **文件**: `runtime/ActorRegistry.java:27`
- **证据片段**:
  ```java
  public interface ActorRegistry {   // :27
      void register(AgentActor actor);
      ...
      Optional<AgentActor> get(String actorId);
  }
  // 引用：InMemoryActorRegistry:39 implements ActorRegistry；InMemoryActorRuntime:110 持有 ActorRegistry 字段
  ```
- **严重程度**: P3
- **现状**: 模块 66 接口中 65 个遵守 code-style.md「接口 I+PascalCase」（IResourceGuard/IAgentEngine/ITeamManager 等），唯独 ActorRegistry 缺 I 前缀。
- **风险**: 公共 API 命名不一致，IDE 补全/检索难与 AgentActor/InMemoryActorRegistry 区分；重命名破坏已发布 API，越往后成本越高。
- **建议**: 重命名为 IActorRegistry（接入方多可加 @Deprecated 别名过渡）。
- **信心水平**: 高
- **误报排除**: grep 列出全部 66 接口逐个核对，仅此一例；非空标记接口/内嵌类型，是真实顶层公共契约；checkstyle 未启用接口命名规则。
- **复核状态**: 未复核

### [维度17-03] ReActAgentExecutor.java 行宽 >120 集中（18 处，最高 164 字符）

- **文件**: `engine/ReActAgentExecutor.java`
- **证据片段**（:2686）:
  ```java
                          throw new NopAiAgentException(
                                  "ReenterResult is only valid at re-entrant hook points (BEFORE_TOOL_RESULT_PROCESSED, AFTER_TOOL_RESULT_PROCESSED), got: " + point);
  ```
  其他典型：:1619(144) LOG.warn、:1988(132) 字符串拼接、:1744(134) 三元。
- **严重程度**: P3
- **现状**: 全模块 58 处 >120 字符，其中 18 处（31%）集中在 ReActAgentExecutor（最核心 3501 行执行器）。
- **风险**: 单文件 18 处超长行降低 diff review 效率；code-style.md 明确 80-120；多数是字符串拼接/LOG.warn 可优化但不影响功能。
- **建议**: 字符串拼接拆多行或 String.format；LOG.warn 占位符参数另起一行；单独 cleanup 提交（不触发 noisy refactor 红线）。
- **信心水平**: 高
- **误报排除**: 剔除空行统计；checkstyle 未启用 LineLength 故构建不拦截；仅报最集中处，零星不报。
- **复核状态**: 未复核

## 维度复核结论

3 项均属实。模块风格整体良好，主要问题是 17-01 噪音注释（量大，P2）。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 17-01 | P2 | 模块级(257文件) | AI 风格噪音 Javadoc 污染（plan/design 引用） |
| 17-02 | P3 | runtime/ActorRegistry.java | 接口缺 I 前缀 |
| 17-03 | P3 | engine/ReActAgentExecutor.java | 行宽 >120 集中(18处) |
