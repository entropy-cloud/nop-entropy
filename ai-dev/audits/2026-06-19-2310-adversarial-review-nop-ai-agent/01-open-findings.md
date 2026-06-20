# 对抗性审查 — nop-ai-agent

## 基本信息

- **审查模块**: `nop-ai-agent`（AI Agent 框架库，路径 `nop-ai/nop-ai-agent`）
- **审查日期**: 2026-06-19
- **审查类型**: 开放式、发现导向对抗性审查（非固定维度清单）
- **执行方式**: 4 个并行子 agent 从不同视角切入（ReAct 循环逻辑 bug / 跨子系统交互 / DSL 与资源生命周期 / 测试-生产漂移），主 agent 对所有 P1 与高置信度 P2 发现逐条对照 live source 验证；随后做了一轮定向深挖（abandonTask CAS 对等性 / restoreSession 清理对等性 / CallAgent 递归）。
- **去重基线**: 紧邻执行的 `2026-06-19-2310-deep-audit-nop-ai-agent/`（21 维度、89 个发现）。本审查**不重复**深度审核已覆盖的发现，仅在新发现与旧发现共享同一根因时做一行引用说明。

### 发现来源视角

本轮主要切入视角：
- **异常路径侦探** + **组合爆炸测试者**：追踪 ReAct 循环中 deny/checkpoint/compact/hook 的实际执行顺序与异常路径。
- **事务边界追踪者**：追踪 DenialLedger + PostDenialGuard、TeamTaskStore + Scheduler + Recovery、Checkpoint + Session + TakeoverLock 三组协作子系统的跨边界一致性。
- **代码生成受害者** + **死代码清道夫**：检查模型加载边界条件与引擎/actor/checkpoint cache 的资源生命周期。
- **未来破坏者**：检查"测试通过但不保护真实路径"的虚假信心。

---

## P1 发现

### [AR-01] `completeTask` / `abandonTask` 缺少 `CLAIMED_BY` CAS — 重派后旧 dispatcher 可完成/放弃它不再拥有的任务（双重执行）

> **修复状态: ✅ 已修复（plan 279 / WI-DBSTORE-CAS）**：`completeTask`/`abandonTask`(CLAIMED 分支) CAS 增加 `CLAIM_EPOCH` 绑定（claim 时 `COALESCE(CLAIM_EPOCH,0)+1` 原子自增，调用方沿 claim→dispatch→complete 透传 epoch）；`reclaimTask` **保留** epoch（保持单调，使 reclaim+re-claim 后新 epoch 严格大于旧在途 owner 的 epoch，真正关闭共享 `DEFAULT_DAEMON_SESSION_ID` 双重执行窗口）；abandon CREATED 分支为 epoch-agnostic（`STATUS='CREATED'`，无 owner 可绑定）。raw-JDBC 幂等迁移加列（`CLAIM_EPOCH`）。focused 测试 `TestDbTeamTaskStore#sharedDaemonIdEpochCasClosesDoubleExecutionWindow` + `#abandonClaimedBranchBindsEpochAndCreatedBranchMatchesNullEpoch`。

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/DbTeamTaskStore.java:270-299`（completeTask）；同文件 `:301-332`（abandonTask 同形）；调用方 `team/flow/MemberFanOutDispatcher.java:257`、`team/flow/MemberAgentTaskStep.java:192`、`team/flow/SpawnMemberAgentTaskStep.java:312`
- **证据片段**:
  ```java
  // DbTeamTaskStore.java:276-281  completeTask
  // complete preserves CLAIMED_BY (design 裁定 6) — do not overwrite it.
  String sql = "UPDATE " + AiAgentTeamTaskTable.TABLE_NAME
          + " SET " + AiAgentTeamTaskTable.COL_STATUS + " = ?, "
          + AiAgentTeamTaskTable.COL_UPDATED_AT + " = ? "
          + "WHERE " + AiAgentTeamTaskTable.COL_TASK_ID + " = ? "
          + "AND " + AiAgentTeamTaskTable.COL_STATUS + " = ?";   // 仅校验 STATUS=CLAIMED，无 CLAIMED_BY=?
  ```
  传入的 `completedBy` 参数（`:272` `Objects.requireNonNull(completedBy)`）**从不写入任何列**，也**不进 WHERE**。`AiAgentTeamTaskTable` 根本没有 `COMPLETED_BY` 列。abandonTask（`:308-315`）形态完全一致。
- **严重程度**: P1
- **现状**: `completeTask` 的 CAS 只校验 `STATUS = 'CLAIMED'`，不校验当前 claim 的所有者。重派时间窗内，前一个（已被 reclaim 的）dispatcher 仍能成功完成同一个 task。
- **风险**: 触发链：scheduler claim T1（CLAIMED_BY=daemon）→ 异步派发给 member → `taskTimeoutSeconds` 到期 → `DefaultTeamTaskRecoveryHandler` RECLAIM（CLAIMED→CREATED，清 CLAIMED_BY）→ scheduler 重新 claim 并派发**另一个** member → **原** member 完成长任务 → `completeTask(T1, 原dispatchSessionId)` 因 STATUS 仍为 CLAIMED 而**成功** → 第二个 member 的 `completeTask` CAS 失败抛异常。两个 member 都执行了带副作用的工作，但归属与状态机记录的是"第一个完成"。abandonTask 同理：旧 owner 可放弃已被新 owner claim 的 task。这是**带副作用任务的双重执行**。
- **建议**: `completeTask`/`abandonTask` 的 WHERE 增加 `AND CLAIMED_BY = ?`，把 `completedBy`/`abandonedBy` 绑定进去；或在 `AiAgentTeamTaskTable` 增加 `CLAIM_EPOCH`（每次 claim 自增），CAS 时校验 epoch。`ITeamTaskStore` Javadoc 声称"single completeTask CAS"语义，当前实现不兑现。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-02] `resumeSession` 只 reset DenialLedger 不 reset PostDenialGuard — 人工恢复后 3 轮内被再次 pause（恢复路径形同虚设）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:2498`（仅 `denialLedger.reset(sessionId)`，无 `postDenialGuard.reset`）；协作方 `security/IPostDenialGuard.java:98`（接口声明 `reset` 且 Javadoc 写"human-intervention recovery entry point"）、`security/FingerprintPostDenialGuard.java:39`（`ConcurrentHashMap<String,Set<String>>` keyed by sessionId，`:85` 实现 reset 但无人调）、`engine/ReActAgentExecutor.java:1702`（pre-dispatch consult）、`:2208`（record after deny）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:2494-2498  resumeSession 同步阶段
  // Clear the pause by resetting the ledger (design §6.2 sticky recovery).
  denialLedger.reset(sessionId);
  // ↑ 唯一被调的 reset；postDenialGuard.reset(sessionId) 在整个 resumeSession 中不存在
  ```
  ```java
  // IPostDenialGuard.java:95-100  接口明确把 reset 定义为恢复入口
  /**
   * Clear all recorded denial state for the given session.
   * <p>This is the human-intervention recovery entry point ...
   */
  void reset(String sessionId);
  ```
- **严重程度**: P1
- **现状**: `resumeSession` 是 sticky-pause 的人工恢复入口（glossary、IAgentEngine Javadoc 都如此描述）。它清掉了 ledger 计数使 `isPaused` 返回 false，但 PostDenialGuard 的内存指纹集合**从未被清**。`IPostDenialGuard.reset` 的 Javadoc 自己写明它是"human-intervention recovery entry point"——但引擎从不调用它。
- **风险**: 场景：s1 因 3 次同类 deny 被 pause → 操作员调查后 `resumeSession(s1, approver, "investigated")` → ledger 归零、status=running → ReAct 恢复，LLM 看到上一条是 deny 错误，**重试同一调用**（同 toolName+args+workDir → 同指纹）→ `postDenialGuard.checkBeforeDispatch` 命中未清的指纹集 → 立即 deny → 回灌 ledger → 3 轮内**再次 pause**。对最常见情形（agent 策略没根本变化）`resumeSession` 等于空操作，违反"恢复"契约。
- **建议**: `resumeSession` 在 `denialLedger.reset` 旁补 `postDenialGuard.reset(sessionId)`。补一个集成测试：pause→resume→验证下一次同指纹调用**不被** pre-dispatch 拦截。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-03] `BEFORE/AFTER_TOOL_RESULT_PROCESSED` 的 `ReenterResult` 用 `break` 跳出结果处理 for 循环 — 批量调用中丢弃其余 tool 结果，破坏 LLM tool_call_id 配对契约

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1928-1972`（allOf.join 后的 for 循环；`:1970` break）；同形 bug 在 `:2021-2032`（`:2030` break）
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:1928-1972
  CompletableFuture.allOf(futuresArray).join();          // 所有 tool 已完成、副作用已落
  for (CompletableFuture<ToolCallOutput> f : futuresArray) {   // 逐个处理结果
      ...
      HookResult beforeResult = invokeHooks(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ...);
      if (beforeResult instanceof HookResult.ReenterResult) {
          ...
          ctx.addMessage(ChatToolResponseMessage.fromToolCall(chatToolCall,
                  reenterMsg != null ? reenterMsg : "hook re-enter"));
          break;     // ← 跳出 for：后续 futuresArray 的结果永远不进 ctx、不 checkpoint、不审计
      }
      ctx.addMessage(toolResponse);
      checkpointManager.saveCheckpoint(...);
      ...
  }
  ```
- **严重程度**: P1
- **现状**: 当 LLM 一次返回 N 个 tool_call（并行 fan-out），所有 tool 实际已执行（`allOf().join()` 在 `:1928`）。结果处理 for 循环中，若 hook 在第 k 个 tool 上返回 `ReenterResult`，`break` 直接退出循环：第 k+1…N 个 tool 的**已完成结果**被静默丢弃——不加消息、不写 checkpoint、不审计、不发 TOOL_CALL_COMPLETED 事件。assistant 消息（`:1491`）含 N 个 tool_call id，但 ctx 只补了 1 条 tool response（且第 k 条还是合成的"hook re-enter"占位，不是真实结果）。
- **风险**: OpenAI/Anthropic/Gemini chat API 强制要求每个 `tool_call_id` 都要有匹配的 `role:"tool"` 响应，且响应必须紧跟在对应 assistant 消息后。下一轮 `buildChatRequest` 从 `ctx.getMessages()` 取出这条带孤儿 tool response 的历史 → 调 LLM → **HTTP 400**（"tool_call_id does not match"）→ `chatService.call` 返回 `isSuccess()==false` → 循环 `:1463-1481` 以 failed 终止。即使 provider 宽容，LLM 也无从得知被丢弃 tool 的真实结果，re-enter 的"重试"基于残缺上下文。
- **建议**: re-enter 分支不应 `break` 整个 for；应改为：先把**当前已 join 的所有 tool 真实结果**入 ctx/checkpoint（保证 tool_call_id 配对完整），再触发 re-enter（例如设标志位、退出后统一重入），或仅 re-enter 当前 tool 而继续处理其余。补一个 N=3、在第 2 个 tool 触发 re-enter 的测试，断言 ctx 中 tool response 数 == N 且 tool_call_id 全部匹配。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探 + 组合爆炸测试者

---

### [AR-04] `autoBindTeam` 在 `createActor` 与内层 `try` 之间抛异常 — handle/actor/lock/心跳永久泄漏，sessionId 被永久"砖"

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:2266-2310`（doExecute）；同形结构 `:2576-2607`（resumeSession）、`:2766-2797`（restoreSession）；抛点 `autoBindTeam` → `:3315`、`:3354`、`:3389`、`:3409`（autoBindLead/autoBindMember 失败抛 `NopAiAgentException`）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:2266-2278  doExecute（resume/restore 同形）
  if (actorRuntime.isEnabled()) {
      AgentActor actor = actorRuntime.createActor(sessionId, request.getAgentName());  // ① 注册 actor + 线程
      actor.setSteeringQueue(ctx.getSteeringQueue());
  }
  autoBindTeam(agentModel, sessionId, request.getAgentName());   // ② 可抛 — 在内层 try 之外

  AgentExecutionResult result;
  try {
      result = executor.execute(ctx).toCompletableFuture().join();
  } finally {
      runningExecutions.remove(sessionId, handle);    // ③ 清理只在 finally
      ...
      actorRuntime.getActorBySession(sessionId).ifPresent(a -> actorRuntime.destroyActor(a.getActorId()));
      releaseLockQuietly(sessionId, instanceId);
      cancelLockRenewalQuietly(handle.renewHandle);
  }
  ```
- **严重程度**: P1
- **现状**: 三个入口都在 `supplyAsync` lambda 内按"createActor → autoBindTeam → try{execute}finally{cleanup}"顺序。`autoBindTeam` 位于内层 `try` **之前**。一旦它在 ② 抛（team 未 ACTIVE、member 不在 roster、bind 失败），内层 `finally`（③）**永不进入**；外层 `catch (RuntimeException)`（`:2335`）只捕获 `supplyAsync` **提交**失败（RejectedExecutionException），lambda body 的异常会异常完成 future 但绕过该 catch。
- **风险**: 单次失败的 team-bind 调用造成四重泄漏且无引擎级恢复（除进程重启）：
  1. `runningExecutions.putIfAbsent`（`:2225`）注册的 handle **永不移除** → 该 sessionId 后续所有 execute/resume/restore **永远**抛"session already executing"（永久砖）。
  2. `AgentActor` + 其独占单线程 executor（`InMemoryActorRuntime`）**永不销毁**。
  3. `:2220` 取的 takeover lock **永不释放**（只能等 lease TTL 到期，期间跨实例 double-execution 窗口）。
  4. `:2230` 起的心跳 `ScheduledFuture` **永不取消**，`lockRenewExecutor` 每个周期对死 session 调 `tryRenew` 刷 WARN 直到 lease 过期。
  这是结构性清理范围缺陷（cleanup scope 太窄），不是某个 throw 点的偶发问题。
- **建议**: 把 `autoBindTeam` 移进内层 `try`，或把 createActor+autoBindTeam 一起包进自己的 try/catch，catch 里做 actor/lock/handle/renewal 的对称清理。三个入口（doExecute/resume/restore）都要改。补一个测试：声明 team-member 但 team 未 ACTIVE，调 execute，验证 handle 被移除、actor 被销毁、lock 被释放、sessionId 可再次执行。
- **信心水平**: 确定（结构），很可能（触发条件——需 actorRuntime 非 NoOp 且声明了 team-member）
- **发现来源视角**: 死代码清道夫 + 事务边界追踪者

---

### [AR-05] `CallAgentExecutor` 无递归深度守卫 — 循环/自引用 agent 调用栈溢出，逐层泄漏 session/actor/lock

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java:345-399`（executeSubAgent）；对比 `team/flow/TeamTaskGraphBuilder.java:129`（team blockedBy 有环检测）
- **证据片段**:
  ```java
  // CallAgentExecutor.java:359-364
  AgentMessageRequest execRequest = new AgentMessageRequest(
          targetAgentId, message, childSessionId, buildConstraintMetadata(parentConstraint));
  CompletableFuture<AgentExecutionResult> future;
  try {
      future = engine.execute(execRequest);   // 递归重入引擎，无 depth/visited 检查
  }
  ```
- **严重程度**: P1
- **现状**: `executeSubAgent` 校验 agentId 字符集（`:151`）但**无深度计数、无 visited-set、无 per-session 调用栈**。`call-agent` → `engine.execute` → 加载目标 agent model → ReAct → 再 `call-agent` 链上没有任何环节阻断深度增长。对比之下 `TeamTaskGraphBuilder.java:129` 对 team `blockedBy` 做了显式环检测——同样的严谨没用到 call-agent 委派图上。
- **风险**: 若 agent A 的 prompt 诱导 LLM 调 `call-agent` 且 `agentId="self"`（或 A→B→A），每层递归新建 session、CancelHandle、actor（若启用）、session 目录（FileBackedSessionStore）。唯一兜底是每跳 `callAgentTimeoutMs`（默认 60s）和每 session 的 `maxIterations`——两者都不限**深度**。配合的 LLM 或对抗性 prompt 注入可把 worker 线程栈拉到 `StackOverflowError`，并在每一层留下孤儿 session/actor/lock-lease。`orTimeout`（`:372`）只取消 Future，不撤销已注册的引擎侧资源。
- **建议**: 在 `AgentToolExecuteContext` 增加 per-顶层-session 的 delegation-depth 计数（沿 sub-agent 调用链传递，参考 ParentPermissionConstraint 的传递方式），`executeSubAgent` 入口检查 `depth >= MAX_DELEGATION_DEPTH`（如 8）即拒绝并返回 errorResult。补测试：self-referencing agent + A↔B 互引，断言得到结构化错误而非栈溢出。
- **信心水平**: 确定（无守卫事实），很可能（可触发性——需 prompt 诱导或恶意 input）
- **发现来源视角**: 未来破坏者 + 模型攻击者

---

## P2 发现

### [AR-06] `reentryCounters` 全 execute 生命周期累加且从不重置 — re-enter hook 在累计 3 次后永久静默失效

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1081`（init，per-execute 局部）、`:1961-1966`、`:2024-2029`（只 put 递增，全文件无 reset）
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:1081
  Map<AgentLifecyclePoint, Integer> reentryCounters = new HashMap<>();   // per-execute，非 per-iteration
  // :1961-1966
  int count = reentryCounters.getOrDefault(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, 0);
  if (count >= DEFAULT_MAX_REENTRIES) {                       // = 3
      LOG.warn("Re-entry limit ({}) reached ... forcing PassResult", DEFAULT_MAX_REENTRIES);
  } else {
      reentryCounters.put(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, count + 1);
      ...
  }
  ```
- **严重程度**: P2
- **现状**: `reentryCounters` 作用域是一次 `execute()` 调用（注释 `:1091` 自承"loop-local"）。但它**从不在 iteration 之间、tool batch 之间重置**——没有 `remove`、没有清零。每次成功的 ReenterResult **永久**消耗该生命周期点的 3 个配额之一。
- **风险**: 长会话（如 20 个 tool batch）里一个合法地"每 5 批请求一次 re-enter"的 hook，前 3 次正常工作，剩下 17 次被静默降级为 PassResult，只有一行 WARN。hook 作者拿不到异常，只观察到行为漂移。注释把 `DEFAULT_MAX_REENTRIES=3` 描述为"per lifecycle point per execution"——但实际语义是"per lifecycle point 整个会话仅 3 次"，与意图（应为 per-iteration 或 per-tool-batch 限流）不符。
- **建议**: 配额窗口应明确为 per-iteration（每轮 ReAct 循环开始时重置该 map）或 per-tool-batch；或在 Javadoc 写清"3 次/会话"是有意为之。补测试：连续 5 个 tool batch 都触发 re-enter，断言第 4、5 次行为符合文档承诺。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-07] `handleGoalStuck` 把终态 `escalated` 配 `SESSION_PAUSED` 事件 — 事件流消费者把不可恢复的中断误判为可恢复

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:2497-2509`（goal stuck）；对比 `:2479-2487`（handleSessionPaused 正确配对 paused↔SESSION_PAUSED）
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:2497-2509
  private void handleGoalStuck(AgentExecutionContext ctx, String sessionId, String agentName) {
      ctx.setStatus(AgentExecStatus.escalated);          // 终态、不可恢复
      ...
      publishEvent(AgentEventType.SESSION_PAUSED, sessionId, agentName, payload);  // 复用可恢复事件
      LOG.warn("Session aborted by goal tracker (stuck/looping): ...");            // log 自己叫 abort
  }
  ```
- **严重程度**: P2
- **现状**: `handleSessionPaused` 把 `paused`（可恢复治理态）配 `SESSION_PAUSED`，语义自洽。但 `handleGoalStuck` 设的是**不同的终态** `escalated`，却复用同一事件类型。后循环 gate（`:2129-2132`）证实 `escalated` 与 `paused` 是两个不同的生命周期态（都跳过 POST_CALL 但含义不同）。注释 `:2491` 自称"abort"，与事件名"PAUSED"矛盾。
- **风险**: 按 `SESSION_PAUSED` 路由的事件消费者（监控面板、运维 runbook、自动 resume 编排）会把 goal-truck 的不可恢复中断当成可 resume 处理，触发徒劳的 `resumeSession`（然后失败，因为 status 不是 paused）。审计/告警分流错位。
- **建议**: 新增 `AgentEventType.SESSION_ESCALATED`（或 `GOAL_STUCK`），`handleGoalStuck` 改用它。审计 `AgentEventType.SESSION_PAUSED` 的所有消费者，确认它们不会因 escalated 而误动作。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-08] `getLatestCheckpoint` 跨接管用 `ORDER BY SEQ` — SEQ 每 execute 重置，返回的是"旧 owner 的最高 seq"而非"最近一次执行的 checkpoint"

> **修复状态: ✅ 已修复（plan 279 / WI-DBSTORE-CAS）**：`loadLatestCheckpointFromDb` 的 `ORDER BY SEQ DESC` 改为 `ORDER BY CHECKPOINT_TIMESTAMP DESC, WATERMARK DESC`（`WATERMARK` 作确定性 tie-break，该表无 ID 列）；cache 路径的 `getLatestCheckpoint` 同样改为选最大 `CHECKPOINT_TIMESTAMP`（与 DB-direct 路径一致），而 bySession 列表仍按 SEQ 升序（`loadSessionRowsFromDb` 未改）以保持 `getCheckpoints` 升序契约与 `CompactionAwareTruncation` 语义隔离。focused 测试 `TestDBCheckpointManager#getLatestCheckpointAcrossTakeoverReturnsMostRecentExecution` + `#getCheckpointsStillAscendingSeqAndDecoupledFromLatestSelection`。

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java:285`（`ORDER BY SEQ DESC FETCH FIRST 1 ROWS ONLY`）、`:352`；调用方 `engine/DefaultAgentEngine.java:2679-2691`（restoreSession 一致性检查 + SESSION_RESTORED 事件）；对比 `FileBackedCheckpointManager.java:171`（用插入序，语义不同）
- **证据片段**:
  ```java
  // DBCheckpointManager.java:285
  // "latest" 用 SEQ 倒序取首条
  ps = conn.prepareStatement("... ORDER BY SEQ DESC FETCH FIRST 1 ROWS ONLY");
  ```
  ```java
  // ReActAgentExecutor.java:1105
  int[] checkpointSeq = {0};   // per-execute 局部，每次 execute 从 0 重置
  ```
- **严重程度**: P2
- **现状**: `checkpointSeq` 是 per-execution 局部，每次 `execute()` 从 0 起算（`:1105`）。实例 A 跑到 seq=5，lease 过期；实例 B 接管，从 seq=0 跑到 seq=2 后崩溃；实例 C 调 `restoreSession`，`getLatestCheckpoint` 用 `ORDER BY SEQ DESC` 取到 **A 的 seq=5**（不是 B 的 seq=2）。
- **风险**: C 发的 `SESSION_RESTORED` 事件带错误 watermark（`s1:llm:T_A:5`，来自两次接管前的中止执行）；一致性检查（`:2685`）拿 A 的 checkpoint messageCount 对比 B 持久化的 messageCount，可能刷**误导性**"persisted history incomplete"WARN（历史其实完整，是 checkpoint 引用错了）。`FileBackedCheckpointManager` 用插入序没有此 bug——两个 backend 的"latest"语义不一致。
- **建议**: `ORDER BY CHECKPOINT_TIMESTAMP DESC`（或 `ID DESC`）替代 `ORDER BY SEQ DESC`，或引入全局 seq/epoch。补测试：两轮 execute（模拟接管），断言第二轮后 `getLatestCheckpoint` 返回第二轮的 checkpoint。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-09] `DefaultAgentEngine` 无 `close()`/`shutdown()` 也不实现 `AutoCloseable` — 懒创建的线程池永不终止

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:399`（`lockRenewExecutor` 字段）、`:451`（`agentExecutor` 字段）、`:1735-1744`（懒创建 lockRenewExecutor）、`:1875-1895`（懒创建 agentExecutor）；接口 `IAgentEngine.java` 不 extends AutoCloseable/Closeable
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:1735-1744
  synchronized ScheduledExecutorService getLockRenewExecutor() {
      if (lockRenewExecutor == null) {
          lockRenewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
              Thread t = new thread(r, "nop-ai-agent-lock-renew");
              t.setDaemon(true);
              return t;
          });
      }
      return lockRenewExecutor;
  }
  ```
  对 `DefaultAgentEngine.java` 全文件 grep `public void (close|stop|shutdown|destroy|dispose)\(`、`AutoCloseable`、`Closeable` → **0 命中**。
- **严重程度**: P2
- **现状**: 引擎懒创建两个线程池，但没有任何生命周期终止入口。`setAgentExecutor`/`setLockRenewExecutor`（`:1752`、`:1903`）的"caller owns lifecycle"注释只覆盖**外部注入**路径——懒创建出来的池**无主**。对比 `InMemoryActorRuntime.destroyAll()`（`:326`）给自己的 executor 收尾。
- **风险**: 任何在长生命周期 JVM 内反复构造/销毁引擎的部署（热重载、per-tenant 引擎工厂、集成测试、app-context 关闭），每个跑过至少一次 session 的 `DefaultAgentEngine` 都泄漏一个 cached pool + 一个单线程 scheduled executor。线程是 daemon，不挡 JVM 退出，但在进程存活期内 `nop-ai-agent-lock-renew` 调度器（session 结束后无活可干）和 cached pool 空闲线程随引擎重建累积。
- **建议**: `IAgentEngine extends AutoCloseable`，`DefaultAgentEngine.close()` 关闭自创建的池（不关外部注入的）。补测试：构造引擎→执行一次→close→验证池 `isShutdown()`。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-10] `FileBackedCheckpointManager` 五个内存 cache 无逐出、无 `clear()`、无 `AutoCloseable` — 按"每工具执行"线性增长到 OOM

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java:98-102`；同形 `session/FileBackedSessionStore.java:85`
- **证据片段**:
  ```java
  // FileBackedCheckpointManager.java:98-102
  private final ConcurrentHashMap<String, List<Checkpoint>> bySession = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Checkpoint> byWatermark = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CheckpointSnapshot> snapshotCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicInteger> saveCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> loadedSessions = new ConcurrentHashMap<>();
  ```
- **严重程度**: P2
- **现状**: 每个 `saveCheckpoint` 都往这五个 map 写条目，但**无任何移除**：`ICheckpointManager` 不声明 `remove(sessionId)`、无 LRU/TTL、类不实现 `AutoCloseable` 也无 `clear()`。
- **风险**: 多租户长跑部署里每个会话生成新 sessionId（`resolveSessionId` 用 UUID，是常见情形），`byWatermark` 的增长与**全进程累计工具执行次数**成正比——不是磁盘 journal（可单独轮转），而是进程内索引。busy box 会因这个本地索引 OOM，而非磁盘问题。`FileBackedSessionStore` 同形但每 session 仅一条，`byWatermark` 是更锋利的边。
- **建议**: 加 `ICheckpointManager.remove(sessionId)`，session 终态时引擎调它；或给这些 map 加 size-bounded LRU（如 Caffeine）。补一个长跑模拟测试。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

### [AR-11] Guardrail-block 注入无对应 tool_call 的合成 tool 响应 — 下一轮 LLM 调用触发 400

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1199-1207`（input block）、`:1580-1588`（output block）
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:1199-1207
  GuardrailResult inputGuardrailResult = checkInputGuardrail(ctx);
  if (inputGuardrailResult.isBlock()) {
      ctx.addMessage(ChatToolResponseMessage.error(
              "guardrail-block-input", "guardrail",     // 合成 id，无对应 tool_call
              "Input blocked by content guardrail: " + ...));
      ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
      continue;    // 下一轮带着这条孤儿 tool response 调 LLM
  }
  ```
- **严重程度**: P2
- **现状**: OpenAI/Anthropic-compatible chat API 要求每条 `role:"tool"` 响应携带一个存在于紧邻 assistant 消息里的 `tool_call_id`。这里合成的响应 id 是 `"guardrail-block-input"`/`"-output"`，从不出现在任何 assistant tool_call 中。
- **风险**: 触发链：guardrail block → 合成孤儿 tool response 入 ctx → continue → 下一轮 `:1276` buildChatRequest 把含孤儿响应的消息列表发给 LLM → provider 返回 HTTP 400（tool_call_id mismatch）→ `chatService.call` 返回 `isSuccess()==false` → 循环以 failed 终止。Guardrail 本意只是"别处理这条"，结果把整个 session 杀了。output-block 路径同形同病。
- **建议**: guardrail block 时**不要**注入 tool 响应；应注入一条 assistant 文本消息或终止循环（设 status=blocked），而非构造破坏 tool_call 配对的合成消息。补测试：用真实 chat provider stub（校验 tool_call_id 配对）跑 guardrail block 路径。
- **信心水平**: 很可能（取决于 IChatService provider 严格度，但主流 provider 都强制此不变量）
- **发现来源视角**: 异常路径侦探

---

### [AR-12] 误导性测试：`hookFailureAtPrePointIsLoggedNotSwallowed` 测的是 POST 点不是 PRE 点

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/hook/TestHookInReActLoop.java:597-623`；生产实现 `engine/ReActAgentExecutor.java:2694-2702`
- **证据片段**: 测试在 `:600` 注册于 `POST_REASONING`（POST 点），方法名却叫"hookFailureAt**Pre**Point"。生产 `invokeHooks` 对 PRE_*/BEFORE_* 抛 `throw e`、对 POST_*/AFTER_*/ON_ERROR 吞并 `LOG.warn(... continuing ...)`——测试用的是吞并分支。
- **严重程度**: P2
- **现状**: 测试名承诺验证"PRE 点 hook 失败被 log 不吞"，实际验证的是"POST 点 hook 失败被吞且继续"。
- **风险**: 读者得出"PRE 点 hook 失败非致命"的结论——**与真相相反**。未来若改 `invokeHooks` 的 catch 破坏了 PRE 点 re-throw，这个错名测试不会报警。
- **建议**: 测试改注册到真正的 PRE 点（`PRE_REASONING`）并断言 status=failed（异常传播）；或重命名测试以反映实际验证的 POST 行为。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（测试-生产漂移）

---

### [AR-13] 误导性测试：两个 re-enter 测试只断言"hook 被调用过"，未验证 re-enter 真被兑现

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/hook/TestHookInReActLoop.java:370-405`（before）、`:408-442`（after）；讽刺对照 `:444-477`（reentryCounterForcesPassAfterMaxReentries 用强断言 `> DEFAULT_MAX_REENTRIES`）
- **证据片段**:
  ```java
  // TestHookInReActLoop.java:374-405
  registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx -> {
      int n = reenterCount.incrementAndGet();      // ← 在返回 ReenterResult 之前就自增
      if (n <= 1) return new HookResult.ReenterResult("inject-retry");
      return HookResult.PassResult.instance();
  });
  ...
  assertEquals(AgentExecStatus.completed, result.getStatus());
  assertTrue(reenterCount.get() >= 1, "Re-enter hook should have been triggered");  // ← hook 被调用即满足，与 re-enter 是否生效无关
  ```
- **严重程度**: P2
- **现状**: `reenterCount` 在回调**入口**自增（`:376`），早于返回 ReenterResult。`>= 1` 只证明 hook 被调用过一次——**无论生产代码是否兑现 ReenterResult，断言都通过**：兑现 → 回调 1 次 → reenterCount=1；静默忽略当 Pass 处理 → 回调 1 次 → reenterCount=1。同值同过。
- **风险**: 一旦回归（如 AR-03 修复时误删 `:1970` 的 break，或把 ReenterResult 当 PassResult），这两个测试不会失败。讽刺的是同文件的 `reentryCounterForcesPassAfterMaxReentries`（`:444`）用了强断言，反证这两个是空心的。
- **建议**: 改断言：检查 ctx 中确实加入了合成 re-enter 消息（生产 `:1968-1969`），或对比 reentryCounter 测试的写法。直接复用 AR-03 建议的"N=3、第 2 个 re-enter"测试。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（测试-生产漂移）

---

### [AR-14] 误导性测试：`testMaxIterationsReached` 把"被截断"断言为 `completed` — 下游无法区分"完成"与"打住"

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestReActAgentExecutor.java:210-234`；生产 `engine/ReActAgentExecutor.java:2121-2123`
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:2121-2123
  if (ctx.getStatus() == AgentExecStatus.running) {
      ctx.setStatus(AgentExecStatus.completed);   // 达上限时把 running 强写为 completed
  }
  ```
- **严重程度**: P2
- **现状**: `StubChatService` 总是返回新的 tool-call 需求，达上限时 agent 显然**没完成任务**（LLM 还在要工具）。但生产代码把 running 静默改写为 completed，测试把这个可疑行为固化成绿色断言。
- **风险**: 下游（sustainer `:2093-2117`、计费、UI 状态徽章）无法区分"真完成"与"被截断"——没有 `max_iterations_reached`/`truncated`/`incomplete` 状态。调用方看 `status==completed` 会误信成功。测试把这个语义混淆固化为契约，使修正变成"破坏测试"。
- **建议**: 新增 `AgentExecStatus.truncated`（或 `incomplete`），达上限时用它；测试断言相应更新。审计所有按 completed 路由的下游。
- **信心水平**: 很可能（行为确定，"是否 bug"取决于产品语义——但 completed 混淆截断几乎肯定是反模式）
- **发现来源视角**: 未来破坏者（测试-生产漂移）

---

## P3 发现

### [AR-15] fan-out 构建循环中 `toolManager.callTool` 同步抛 — 已启动的 tool future 成为无主孤儿（无审计、无 checkpoint）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1891-1924`
- **证据片段**:
  ```java
  for (ChatToolCall chatToolCall : allowedCalls) {
      ...
      CompletableFuture<ToolCallOutput> toolFuture = toolManager.callTool(...)   // ← 可能同步抛
              .thenApply(...);
      if (toolTimeoutMs > 0) {
          toolFuture = toolFuture.orTimeout(...).exceptionally(...);   // ← 只兜异步失败，且在 callTool 返回后才挂
      }
      futures.add(toolFuture);
  }
  CompletableFuture.allOf(futuresArray).join();   // ← 同步抛时永远到不了
  ```
- **严重程度**: P3
- **现状**: `.exceptionally(...)` 只转换返回 future 的**异步**失败。若 `toolManager.callTool(...)` 自身在构建第 k 个 future 时同步抛（NPE、校验异常、tool 插件加载错），异常立即传出 for 循环；前 k−1 个已加入 `futures` 的 future 已并发执行、可能正在落副作用。没人对它们 `.join()`、不取消、无 `.orTimeout`（orTimeout 在 callTool 返回后才挂，孤儿没有）。
- **风险**: 外层 catch（`:2142`）设 status=failed 终止 session，而孤儿 tool 在后台继续写文件/发 HTTP。结果：无审计记录、无 checkpoint、无错误归因的副作用。
- **建议**: 构建循环包 try/catch，catch 里对所有已加入 `futures` 的 future 调 `.cancel(true)`；或要求 `toolManager.callTool` 契约上不得同步抛（文档化）。
- **信心水平**: 很可能（取决于 callTool 是否契约禁止同步抛——本文件无文档说明）
- **发现来源视角**: 异常路径侦探

---

### [AR-16] Markdown plan loader 绕过 `mandatory="true"` 的 title 校验（无 H1 时）

- **文件**: `nop-ai/nop-ai-agent/src/test/resources/_vfs/nop/record/mapping/agentPlan.record-mappings.xml:54-56`（mapping）；`nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MappingBasedMarkdownParser.java:73-86, 148-161`
- **证据片段**: mapping 声明 `<field name="title" from="计划标题" mandatory="true">` 且 `md:titleField="title"`。但 titleField 路径在其 lambda 内**无条件**执行 `processedFields.add(titleField.getName())`（即便 `section.getTitle()==null`），随后 `checkComplete`（`:154`）只在 mandatory 字段**不在** processedFields 时报错——title 永远在，mandatory 校验被绕过。
- **严重程度**: P3
- **现状**: 无 `# H1` 的 `.agent-plan.md`（如直接以 `- 计划状态:` 开头）能成功加载并产出 `title==null` 的 AgentPlan。`agent-plan.xdef:15` title 是可选（`string` 非 `!string`），xdef 也放行。唯一测试 `TestAgentPlanMarkdownLoader` 总是提供 H1，缺口未被守护。
- **风险**: 假定 `plan.getTitle()` 非空的下游代码（审计日志、plan 文件名派生）会远离加载点 NPE。
- **建议**: titleField 处理时若值为 null 则不入 processedFields（让 mandatory 生效）；或在 lambda 内对 null 抛错。
- **信心水平**: 很可能
- **发现来源视角**: 代码生成受害者

---

### [AR-17] xdef `<instructions>!string`（强制）与 record-mapping `<instructions>`（非强制）契约漂移

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef:92`（`<instructions>!string</instructions>` 每 task 内）vs `nop-ai/nop-ai-agent/src/test/resources/_vfs/nop/record/mapping/agentPlan.record-mappings.xml:27`（`<field name="instructions" from="指令">`，非 mandatory 且不映射 body）
- **严重程度**: P3
- **现状**: xdef 要求每个 task 有 instructions，record-mapping 不强制且不映射该 body。两份 schema 对"合法 task"定义不一致。
- **风险**: markdown plan 的 task 漏 `- 指令:` → 过 record-mapping → 要么晚到 xdef 校验才以泛化错误失败（不带 markdown 行号），要么产出 null instructions 让 plan-mode executor 无法行动。失败模式随 loader 版本不确定。现有测试从不漏 指令，漂移未被验证。
- **建议**: 两份 schema 对齐 mandatory；record-mapping 补 instructions 的 body 映射。
- **信心水平**: 有趣的猜测
- **发现来源视角**: 代码生成受害者

---

### [AR-18] `FingerprintPostDenialGuard` 指纹集无 tenant 维度 — 跨租户 sessionId 复用时安全状态串台

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/FingerprintPostDenialGuard.java:39`（`ConcurrentHashMap<String,Set<String>>` keyed by sessionId only）、`:63`（checkBeforeDispatch 仅按 sessionId 查）；对比 `DBDenialLedger.java:236-242`（`countDenials` 注入 `TenantSql.whereTenant`，按 sessionId+tenantId）
- **严重程度**: P3（与 AR-02 同根因区域，但触发条件更依赖部署）
- **现状**: Guard 的内存指纹集只按 sessionId 索引；DBLedger 按 sessionId+tenantId 索引。两个协作子系统用**不同的身份模型**。
- **风险**: sessionId 跨租户复用时（配置错、sessionId 碰撞、跨租户 fork），T2 会继承 T1 的被拒指纹，在 T2 从未实际拒绝的动作上误拒，并把误拒回灌 T2 的 ledger 造成审计偏移。注：与深度审核 13-01（DBMessageService poller tenant 泄漏）属同一"多租户隔离在非主路径系统性遗漏"模式的新实例，但触发面不同（guard 是进程内状态，poller 是 DB 轮询）。
- **建议**: guard 的 map key 改为 sessionId+tenantId 复合键（或 guard 持有 tenant resolver）。
- **信心水平**: 很可能（代码缺 tenant 维度属实；可触发性取决于 sessionId 是否跨租户复用）
- **发现来源视角**: 模型攻击者 + 事务边界追踪者

---

## 总评

本轮对抗性审查的核心信号是：**`nop-ai-agent` 的"四层接口架构 + secure-by-default + CAS 互斥 + 跨实例持久化"基础设施在设计文档层非常完整，但在"协作子系统之间的边界条件"和"异常路径下的资源对称性"上存在系统性缺口**。最值得关注的三个方向：

1. **状态机 CAS 不带身份校验（AR-01）是最高危的发现**。`completeTask`/`abandonTask` 的 SQL 只验 STATUS 不验 owner，配合"基于时间的 reclaim（无 liveness 探针）"，开了双重执行带副作用任务的窗口。这不是单点 bug——它暴露了一个模式：`DbTeamTaskStore` 的三个 transition 方法（claim/complete/abandon/reclaim）里只有 claim 是真正的 owner-CAS，其余都退化成"状态存在性校验"。`ITeamTaskStore` Javadoc 反复声称"single CAS"，但代码不兑现。建议把 CLAIMED_BY（或 claim epoch）校验贯穿所有 transition。

2. **三个恢复/治理入口（resume / restore / cancel）的清理对称性普遍不完整（AR-02、AR-04、AR-08）**。`resumeSession` 清 ledger 不清 guard；三个 execute 入口的 `autoBindTeam` 在内层 try 外抛时四重泄漏；`getLatestCheckpoint` 的 SEQ 语义跨接管失效。这些共同指向一个工程实践缺口：**没有把"清理代码"和"注册代码"放在对称的词法作用域里**（try-finally 包不住注册序列）。AR-04 尤其严重——一次失败的 team-bind 永久砖掉 sessionId，只能重启进程恢复。

3. **测试套件庞大但存在"虚假信心"洼地（AR-12/13/14）**。多数 `*InReActLoop` 测试是真端到端，但 hook 相关的几个测试要么名实不符（pre 测了 post）、要么断言过弱（只验"调过"不验"兑现"）。讽刺的是同文件内有强断言的对照测试（`reentryCounterForcesPassAfterMaxReentries`），说明作者知道怎么写对——这几个是漏网。`testMaxIterationsReached` 把"截断"固化为 `completed` 尤其危险，因为它把一个语义缺陷变成了"测试承诺"。

与之相比，**深度审核已覆盖的维度（依赖图健康、IoC 规范、错误处理两档策略、God Object 体积）在本轮未发现新问题**，印证了维度审核在"广度"上的有效性——但对"协作子系统边界条件"和"异常路径资源对称性"这两类横向问题，开放式对抗性审查是必要补充。

---

## 本次审查的盲区自评

- **未实跑测试**：所有发现基于静态代码路径分析 + live source 验证，未通过 `./mvnw test` 或并发压测复现。AR-01/AR-03/AR-11 的实际触发性依赖运行时（provider 严格度、调度时序），建议用集成测试落地复现。
- **未审计 `_gen/` 内部**：按 AGENTS.md 规则不审计生成产物内部，仅追溯模型/模板。AR-16/AR-17 涉及 record-mapping 与 xdef 的 schema 漂移，但未深入 `MappingBasedMarkdownParser` 全部分支。
- **nop-ai-service/web/app 调用方未核验**：AR-07（事件类型）、AR-14（status 语义）的影响面基于本模块分析，未逐一核验下游消费者实际是否按 SESSION_PAUSED 路由或按 completed 计费。
- **roadmap 完成度未对照**：多处发现（AR-02 reset 缺失、AR-04 清理缺口）在设计文档里有"应做"的描述但代码未做，本轮未系统对照 `nop-ai-agent-roadmap.md`（154KB）判断是"未完成项"还是"回归"。
- **性能维度未审**：AR-10（cache 无界）基于增长趋势推断 OOM，未实测内存占用曲线；token 估算精度、DB 查询计划未审。
- **可能遗漏的协作三元组**：本轮追了 4 组（Denial/Guard、Scheduler/Recovery/Store、Checkpoint/Session/Lock、Mailbox/Actor/Store），但 Mailbox+Actor+SessionStore 那组只浅追了，未发现明确 bug 但也未穷举边界。
- **Round 2 未充分展开**：abandonTask CAS 对等性（已并入 AR-01）、restoreSession 清理对等性（已确认 AR-04 三入口同形）做完后，未再就"reclaimTask 是否同样缺 CAS"等线索继续深挖。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 5    | 状态机 CAS 缺身份校验致双重执行(AR-01)；恢复路径清理不完整(AR-02,AR-04)；批量 tool 结果处理 break 丢数据+破坏 LLM 契约(AR-03)；递归无深度守卫(AR-05) |
| P2      | 9    | re-enter 配额不重置(AR-06)；事件/状态错配(AR-07)；checkpoint latest 语义跨接管失效(AR-08)；引擎无生命周期(AR-09)；cache 无界(AR-10)；guardrail 注入孤儿 tool 消息(AR-11)；3 个误导性测试(AR-12/13/14) |
| P3      | 4    | fan-out 同步抛孤儿(AR-15)；markdown title mandatory 绕过(AR-16)；xdef-record-mapping 漂移(AR-17)；guard 指纹集无 tenant 维度(AR-18) |

---

## 与深度审核（2026-06-19-2310-deep-audit）的关系

本审查的 18 个发现**均不与**深度审核的 89 个发现机械重复。与深度审核的关联：

- **AR-18** 与深度审核 **13-01**（DBMessageService poller tenant 泄漏）属同一"多租户隔离在非主路径系统性遗漏"模式的新实例（不同触发面：进程内 guard map vs DB 轮询），已在本审查中标注关联。
- **AR-04**（autoBindTeam 清理缺口）与深度审核 **14-01**（finally 移除 handle 竞态）都涉及 `runningExecutions` 清理，但 14-01 是"成功路径上两个执行互踩"，AR-04 是"异常路径上根本不进 finally"——不同根因，互补。
- 深度审核 **14-02**（lease-lost 中断打不断 fan-out join）的根因是 `CompletableFuture.join()` 不可中断；本轮探索曾发现 cancel() 路径同病，但因其与 14-02 同根且 14-02 已确认，**主动不重复报告**（按 skill"不要机械复述"）。
- 深度审核 **02-01/02**（God Object 体积）指出 `ReActAgentExecutor`/`DefaultAgentEngine` 过大；本审查的 AR-03/AR-06/AR-07/AR-11/AR-15 正是藏在这种体积里的**具体逻辑 bug**——印证了 God Object 不只是可维护性问题，更是 bug 温床。
