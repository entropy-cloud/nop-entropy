# 维度 02：模块职责与文件边界

## 检查范围

main 25 个子包共 57078 行；逐包统计文件/行数；对 >500 行类核验方法清单/字段依赖；子包 import 关系；`_gen/` 与 `_` 前缀文件手写痕迹检查。

## 第 1 轮（初审）发现

### [维度02-01] ReActAgentExecutor（3501 行）是 God Object，多个横切关注点深度内联

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`
- **行号**: 字段 175-341（36+ 注入接口）；execute 1064-2157（单方法约 1094 行）；安全检查内联 1700-1870；LLM 调用链 1290-1461
- **证据片段**:
  ```java
  // 36+ 注入接口（节选）
  private final IToolAccessChecker toolAccessChecker;       // 179
  private final IPathAccessChecker pathAccessChecker;       // 180
  private final IApprovalGate approvalGate;                 // 196
  private final IDenialLedger denialLedger;                 // 197
  // execute 主循环内 6 层安全检查连续内联（1700-1870 节选）
  ToolAccessResult accessResult = toolAccessChecker.checkAccess(toolName, ctx);   // 1731
  String pathDenied = checkPathAccess(chatToolCall, ctx, sessionId, agentName);   // 1779
  SecurityConsultationOutcome layer2 = checkLayer2Consultation(...);              // 1803
  String layer3Denied = checkLayer3Approval(...);                                 // 1826
  String conflictDenied = checkWriteConflict(...);                                // 1855
  // deny 处理样板重复 7 次（1722/1746/1770/1786/1811/1834/1863）
  ```
- **严重程度**: P1
- **现状**: 单一类承担 ReAct 主循环、6 层工具分发安全检查、LLM 调用（超时+重试+熔断+fallback）、压缩、检查点、usage、token、budget、guardrail、talent/skill 咨询等 10+ 关注点；Builder 自身约 600 行；deny 样板重复 7 次。
- **风险**: 安全策略调整回归面巨大；单方法 1094 行难以针对性测试；7 处样板一致性维护负担；注释里大量 Plan 桩号说明反复打补丁。
- **建议**: 提取 `ToolDispatchGuard`（6 层安全 + 统一 deny 样板）+ `ResilientLlmCaller`（circuit+timeout+retry+fallback），主循环只判断 decision.isDenied()。
- **信心水平**: 高
- **误报排除**: 不是"文件大"告警——36+ 字段、10+ 关注点、7 处样板重复是结构性职责过载。
- **复核状态**: 未复核

### [维度02-02] DefaultAgentEngine（3435 行）跨职责域，内联 lock/team/message 子系统

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`
- **行号**: 锁管理 1716-1850；团队自动绑定 3296-3415；messenger 777-870；skill curation 723
- **证据片段**:
  ```java
  private void releaseLockQuietly(String sessionId, String ownerId) { sessionTakeoverLock.release(...); }  // 1716
  synchronized ScheduledExecutorService getLockRenewExecutor() { ... }                                     // 1735
  private void handleLeaseLost(CancelHandle handle, ...) { ctx.setLeaseLost(true); ... t.interrupt(); }    // 1821
  private void autoBindTeam(AgentModel agentModel, String sessionId, String agentName) { ... }             // 3315
  private void autoBindLead(TeamModel teamDecl, ...) { teamManager.createTeam(...); ... }                  // 3336
  ```
- **严重程度**: P1
- **现状**: 90+ 方法、8 个构造器重载。除核心职责外内联：takeover lock 心跳续期+lease-lost 子系统（1716-1850，5 私有方法）、团队自动绑定子系统（3296-3415，含 ACTIVE 团队查找+幂等 createTeam）、call-agent 路由（777-870）、skill curation（723）。
- **风险**: team/lock/message 子包已有目录但关键编排逻辑落在 engine 包；lock 5 方法深度耦合内部 CancelHandle/runningExecutions；autoBindLead/Member 含真实业务逻辑，违反 engine 作为"协调壳"定位。
- **建议**: 提取 `SessionTakeoverLockManager` + `TeamAutoBinder`；call-agent 路由独立化。
- **信心水平**: 高
- **误报排除**: 不是"setter 多合理保留"——这里内联了有状态、有自己错误处理路径的完整子系统。
- **复核状态**: 未复核

### [维度02-03] compact 包反向依赖 engine.ReActAgentExecutor 具体类（仅读一常量）

- **文件**: `compact/PipelineCompactor.java:5,158`；`engine/ReActAgentExecutor.java:173`
- **证据片段**:
  ```java
  import io.nop.ai.agent.engine.ReActAgentExecutor;            // PipelineCompactor:5
  return ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS;        // :158
  // engine/ReActAgentExecutor.java:173
  public static final int DEFAULT_MAX_CONTEXT_TOKENS = 128000;
  ```
- **严重程度**: P2
- **现状**: PipelineCompactor 反向 import ReActAgentExecutor 仅为读回退常量 128000；该常量与执行器无逻辑关联。
- **风险**: compact→engine 具体类依赖，engine 又依赖 compact，形成双向依赖种子；阻碍 ReActAgentExecutor 拆分。
- **建议**: 提取常量到独立位置（CompactConfig 或 AgentDefaults）。
- **信心水平**: 高
- **误报排除**: 运行时真实调用该常量作回退值，依赖真实。
- **复核状态**: 未复核

### [维度02-04] ITokenEstimator/CalibratedTokenEstimator 放 engine 包致 compact 反向依赖

- **文件**: `engine/ITokenEstimator.java`、`engine/CalibratedTokenEstimator.java`
- **行号**: 被 compact 包 6 文件 import（CompactionContext:4、Layer2TurnPruningStrategy:3、Layer3FullSummaryStrategy:3、MicroCompressionCompactor:3、NoOpContextCompactor:3-4、PipelineCompactor:4）
- **证据片段**:
  ```java
  import io.nop.ai.agent.engine.ITokenEstimator;      // compact/CompactionContext.java:4
  import io.nop.ai.agent.engine.CalibratedTokenEstimator;  // compact/NoOpContextCompactor.java:3
  ```
- **严重程度**: P2
- **现状**: token 估算的核心消费者是 compact 包 6 文件，但接口与实现声明在 engine 包。
- **风险**: compact↔engine 双向依赖（engine.ReActAgentExecutor 调 contextCompactor.compact）；NoOpContextCompactor 直接 new CalibratedTokenEstimator 固化耦合。
- **建议**: 迁移到独立子包（token/ 或 compact 内）。
- **信心水平**: 高
- **误报排除**: ITokenEstimator 在 compact 作为公共能力消费，非 engine 内部协议。
- **复核状态**: 未复核

### [维度02-05] NopAiAgentException 放 engine 包，13 子包 71 文件被迫 import engine

- **文件**: `engine/NopAiAgentException.java`
- **证据片段**:
  ```java
  package io.nop.ai.agent.engine;                                    // :1
  public class NopAiAgentException extends NopException {            // :6
  ```
- **严重程度**: P2
- **现状**: 模块级异常类声明在 engine 子包，导致 compact(1)/completion(4)/contribution(1)/memory(8)/message(5)/reliability(9)/router(1)/runtime(7)/security(9)/session(7)/skill(4)/team(14)/usage(1) 共 13 子包 71 文件 import engine。
- **风险**: engine 成事实"根包"但包名不表达；security/team 平级关注点不应依赖 engine；未来 jpms 模块化会被钉死。
- **建议**: 迁移到 `io.nop.ai.agent` 根包或 `io.nop.ai.agent.exception`。
- **信心水平**: 高
- **误报排除**: 13 子包 71 文件是模块级使用规模。
- **复核状态**: 未复核

### [维度02-06] ThreadLocalTenantResolver 多租户基础设施工具放 security 包

- **文件**: `security/ThreadLocalTenantResolver.java:1,31,34`；被 engine/team 多包使用
- **证据片段**:
  ```java
  package io.nop.ai.agent.security;                                  // :1
  public final class ThreadLocalTenantResolver implements ITenantResolver {  // :31
  // 被 engine/DefaultAgentEngine、team/scheduler/TeamTaskSchedulerDaemon、team/flow/* 使用
  ```
- **严重程度**: P2
- **现状**: 跨 supplyAsync 线程传播 tenantId 的 ThreadLocal 工具，与安全无关，却与 TenantSql/DBDenialLedger 同包；engine/team/scheduler/flow 都 import security 仅为多租户支持。
- **风险**: 多租户能力与安全能力语义独立却混在一个包；DBMessageService/DBSessionStore 等 SQL 工具依赖 security 包仅为多租户。
- **建议**: 迁移多租户类型（ITenantResolver/ThreadLocalTenantResolver/NullTenantResolver/TenantSql）到独立子包。
- **信心水平**: 高
- **误报排除**: 类注释自承"propagate tenantId across supplyAsync"基础设施，与安全无关。
- **复核状态**: 未复核

### [维度02-07] schema 解析逻辑分散在 engine.ToolSchemaConverter 与 repair.ToolSchemaParser

- **文件**: `engine/ToolSchemaConverter.java:11-15`；`repair/ToolSchemaParser.java:8-25`
- **证据片段**:
  ```java
  // ToolSchemaParser 类注释强调与 ToolSchemaConverter 的区别
  * This parser does NOT use ToolSchemaConverter, which expects JSON-schema ...
  ```
- **严重程度**: P3
- **现状**: 同一关注点（工具 schema 解析）两个并行实现散落 engine 与 repair；ToolSchemaConverter 主要消费者是 repair 包。
- **风险**: 新人难判断用哪个；位置不合理。
- **建议**: 统一到 tool/ 子包（或 tool/schema/）。低优先级。
- **信心水平**: 中
- **误报排除**: 两者处理不同 schema 表示（JSON-schema vs XDEF 属性），非简单重复；问题是位置边界不清。
- **复核状态**: 未复核

## 超大文件职责评估

| 文件 | 行数 | 职责 | 是否过载 |
|------|------|------|---------|
| ReActAgentExecutor | 3501 | ReAct 推理执行器 | **是（见02-01）** |
| DefaultAgentEngine | 3435 | 引擎装配+会话执行/取消/恢复 | **是（见02-02）** |
| TeamTaskSchedulerDaemon | 1097 | 团队任务守护扫描 | 否（单一职责高复杂度） |
| DbTeamManager | 830 | 团队 DB 持久化 | 否（单模块库无 dao 子模块的设计约束） |
| TeamTaskFlowOrchestrator | 792 | DAG 编排 | 否 |
| DBMessageService | 662 | 消息服务 DB 实现 | 否 |

`_gen/` 所有文件含 CPD-OFF/CPD-ON + generate-from 标记，**无手写痕迹**。

## 维度复核结论

[维度02-01/02] 独立复核确认两个 God Object 属实（已由维度14复核 agent 再次验证 ReActAgentExecutor 的 execute/字段/内联逻辑真实存在）。[维度02-03~07] 包边界问题属机械迁移类，复核未发现反证，保留。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 02-01 | P1 | engine/ReActAgentExecutor.java | God Object：36+字段/1094行execute/10+关注点/7处样板 |
| 02-02 | P1 | engine/DefaultAgentEngine.java | 跨职责域内联 lock/team/message 子系统 |
| 02-03 | P2 | compact/PipelineCompactor.java | 反向依赖 engine 仅读一常量 |
| 02-04 | P2 | engine/ITokenEstimator.java | 放错包致 compact↔engine 双向依赖 |
| 02-05 | P2 | engine/NopAiAgentException.java | 模块级异常放 engine，13子包71文件被迫import |
| 02-06 | P2 | security/ThreadLocalTenantResolver.java | 多租户基础设施放 security 包 |
| 02-07 | P3 | engine/ToolSchemaConverter.java | schema 解析逻辑分散 |
