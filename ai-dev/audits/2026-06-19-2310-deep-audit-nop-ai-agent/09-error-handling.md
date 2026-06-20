# 维度 09：错误处理与错误码

## 检查范围

`NopAiAgentException.java`、`SandboxException`、`LlmErrorClassifier`/`ErrorClassification`；全模块 throw/catch（~100+ 处）；对照 error-handling.md 两档策略。

**NopAiAgentException 本身合规**：extends NopException，提供 (String)/(String,Throwable)/(ErrorCode)/(ErrorCode,Throwable) 构造器，符合两档策略。模块内 30+ 处 throw 正确用双参构造器保留异常链，消息全英文。全模块无 System.out/err、无 printStackTrace、无空 catch、无中文消息。

## 第 1 轮（初审）发现

### [维度09-01] LlmCompletionJudge 内部组件 catch+转 fallback 返回值且日志丢 stack

- **文件**: `completion/LlmCompletionJudge.java:87-93`
- **证据片段**:
  ```java
  try {
      response = config.getChatService().call(request, null);
  } catch (RuntimeException e) {
      LOG.warn("...chatService.call() threw, using fallback decision. error={}", e.toString());  // e.toString() 插值，未传 throwable
      recordFallbackMetadata(ctx, FALLBACK_VERDICT_LABEL);
      return config.getFallbackDecision();   // 异常伪装成 fallback 返回值
  }
  ```
- **严重程度**: P1
- **现状**: LlmCompletionJudge 是内部组件（非 IToolExecutor/HTTP/GraphQL 边界）。catch 块既用 e.toString() 丢 stack，又把异常转成 fallback 返回值。
- **风险**: 命中 error-handling.md「不要这样写」表反模式（LOG.warn(...,e.toString()) 不传 throwable）；违反 fail-fast 传播契约（doc 明示 IToolExecutor/边界才允许 catch+返回值）；上层无从感知故障。
- **建议**: LOG.warn(...,e)（throwable 作末参数）；评估是否让异常传播到 ReAct 循环（已有 retry+circuit+forced-stop）；若 fallback-on-error 是需求，声明为内嵌降级边界并升 LOG.error。
- **信心水平**: 高
- **误报排除**: LlmCompletionJudge 不实现 IToolExecutor 等 system-boundary 接口。
- **复核状态**: 未复核

### [维度09-02] 3 处 LOG.warn 用 e.toString() 而非 throwable 作末参数（系统性丢 stack）

- **文件**: `skill/LLMCurator.java:214`；`runtime/recovery/ScheduledRecoveryManager.java:392`；`engine/DefaultAgentEngine.java:1848`
- **证据片段**:
  ```java
  // LLMCurator:213-216
  } catch (Exception e) { LOG.warn("LLMCurator: JSON parse failed: {}", e.toString()); return SkillCurationResult.failed(...); }
  // ScheduledRecoveryManager:391-394
  } catch (RuntimeException e) { LOG.warn("...periodic scan failed (will retry next interval): {}", e.toString()); }
  // DefaultAgentEngine:1845-1849 (cancelLockRenewalQuietly)
  } catch (RuntimeException e) { LOG.warn("...failed to cancel lock renewal task: {}", e.toString()); }
  ```
- **严重程度**: P2
- **现状**: 3 处独立 catch 把异常转字符串插值，未把 e 作 logger 末参数。ScheduledRecoveryManager/LLMCurator 在真实错误路径（daemon 周期扫描/LLM 解析）。
- **风险**: 命中 doc「不传 throwable → 丢 stack」反模式；同模式复制 4 处（含09-01）说明习惯性问题易扩散；daemon 故障定位依赖完整 stack。
- **建议**: 统一改 `LOG.warn("<msg>", e)`；可加 checkstyle 禁止 LOG.*(…,e.toString())/(…,e.getMessage())。
- **信心水平**: 高
- **误报排除**: grep 验证 LOG.warn/error/info 中 e.toString() 仅此 3 处（不含09-01）。
- **复核状态**: 未复核

### [维度09-03] DefaultOrphanRecoveryHandler 显式 throw new NullPointerException(msg)

- **文件**: `runtime/recovery/DefaultOrphanRecoveryHandler.java:116-125`
- **证据片段**:
  ```java
  this.mode = java.util.Objects.requireNonNull(mode, "mode must not be null");
  if (mode == RecoveryMode.RESUME && engine == null) {
      throw new NullPointerException("DefaultOrphanRecoveryHandler: engine must not be null for RESUME mode");
  }
  if (mode == RecoveryMode.ABORT && dataSource == null) {
      throw new NullPointerException("...dataSource must not be null for ABORT mode");
  }
  ```
- **严重程度**: P2
- **现状**: fail-fast 校验用显式 `throw new NullPointerException(msg)` 而非 Objects.requireNonNull 或 NopAiAgentException。NPE 是 RuntimeException，不继承 NopException。
- **风险**: 违反 doc「业务异常必须继承 NopException」；同行已用 requireNonNull 处理 mode，风格不一致；javadoc 承诺 NPE 会误导维护者照抄。
- **建议**: 改 Objects.requireNonNull(engine,msg)（与同行一致）或 NopAiAgentException(msg)；同步更新 javadoc。
- **信心水平**: 高
- **误报排除**: NopAiAgentException 已 import 且同文件已使用，替换成本极低。
- **复核状态**: 未复核

### [维度09-04] 公共 API IAgentEngine 的 6 个 default 方法抛 UnsupportedOperationException 而非 NopException

- **文件**: `engine/IAgentEngine.java:38,42,46,76,115-116,186-187`；`engine/DefaultAgentEngine.java:3131`
- **证据片段**:
  ```java
  default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
      throw new UnsupportedOperationException("forkSession requires Phase 2 ISessionStore");
  }
  default CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
      throw new UnsupportedOperationException("restoreSession requires a FileBackedSessionStore-backed engine");
  }
  ```
- **严重程度**: P2（跨模块契约角度见维度20-01，该处补强为 P1）
- **现状**: IAgentEngine 是对外暴露的跨模块公共 API，6 个 default 方法直接 throw UOE（RuntimeException 子类，不继承 NopException）。javadoc 反而承诺"fails fast with NopAiAgentException"。
- **风险**: 违反 doc 模式一「公共 API 必须用 ErrorCode/NopException」；调用方按 NopException 体系处理会漏接 UOE；javadoc 与实现不一致。
- **建议**: 改 throw new NopAiAgentException(msg)（或定义 ErrorCode+.param）；修正 javadoc 口径。
- **信心水平**: 中（受 JDK"interface default+UOE 表未实现"习惯影响；但项目 doc 明确要求公共 API 走 NopException）
- **误报排除**: 这些是 default 方法（外部直接调 IAgentEngine 实例会命中 UOE）；ISessionStore 类似 UOE 被 javadoc 明示 rewrap，但 IAgentEngine 自身 UOE 无 rewrap 路径。
- **复核状态**: 见维度20-01（跨模块契约角度确认成立）

### [维度09-05] ≥9 处 exhaustive-switch default 抛 IllegalStateException，绕过 NopException 体系

- **文件**: `reliability/ThresholdBreaker.java:134,179,213`；`team/flow/SpawnMemberAgentTaskStep.java:296`；`team/flow/MemberFanOutDispatcher.java:369`；`team/flow/MemberExecOutcome.java:232`；`runtime/recovery/DefaultOrphanRecoveryHandler.java:146`；`runtime/recovery/DefaultTeamTaskRecoveryHandler.java:265,269`
- **证据片段**:
  ```java
  // ThresholdBreaker.allowCall switch default
  default: throw new IllegalStateException("Unknown circuit state: " + entry.state);
  // SpawnMemberAgentTaskStep:295-297
  default: throw new IllegalStateException("unhandled spawn result status: " + spawnResult.getStatus());
  ```
- **严重程度**: P3
- **现状**: ≥9 处 switch 默认分支抛 IllegalStateException，注释标注"Unreachable: enum exhaustive"/"Defensive"。不继承 NopException。
- **风险**: 严格按 doc 违规；但这些是"不可达防御代码"，实际触发说明 enum 加新值而 switch 未更新，JDK 社区习惯用法，换 NopAiAgentException 收益边际。真实风险点：ThresholdBreaker 熔断器热路径 enum 扩展遗漏会以非 NopException 上抛。
- **建议**: 统一替换为 NopAiAgentException，或 checkstyle 豁免"exhaustive switch default"。
- **信心水平**: 中
- **误报排除**: 9 处均为 enum exhaustive switch default，注释说明 unreachable，非真实可达业务路径，故 P3。
- **复核状态**: 未复核

### [维度09-06] InMemoryActorRuntime.createActor 中 mailboxLookup 抛异常被静默降级

- **文件**: `runtime/InMemoryActorRuntime.java:267-274`
- **证据片段**:
  ```java
  IMailbox mailbox = null;
  try { mailbox = mailboxLookup.apply(sessionId); }
  catch (Exception e) { LOG.warn("InMemoryActorRuntime: mailboxLookup threw for sessionId={}", sessionId, e); }
  AgentActor actor = new AgentActor(actorId, sessionId, agentName, now, mailbox);  // mailbox 可能 null
  actor.updateStatus(AgentActorStatus.READY);
  ```
- **严重程度**: P3
- **现状**: createActor 公共 API，mailboxLookup 抛任何异常时 catch+LOG.warn，actor 以 mailbox=null 创建并返回 READY；后续 runConsumptionLoop 发现 null mailbox 时 idle 不 poll，actor 沉默不做事。
- **风险**: 调用方拿到看似成功的 AgentActor 引用，无法感知 mailbox 绑定失败；steering 消息将永不被消费，表现为"agent 偶发不响应"——难排障的静默降级。LOG.warn 满足 doc「丢弃异常前留证」最低要求（throwable 作末参数），不构成"吞异常"违规；问题在可观测性。
- **建议**: 若 mailbox 必需，传播 NopAiAgentException；若可选，日志升 error 并标 actor "DEGRADED"。
- **信心水平**: 中（mailbox 是否可选是设计判断）
- **误报排除**: catch 用了 LOG.warn(msg,e) 而非 e.toString()，未触发09-01/09-02 antipattern；本条仅针对静默降级行为。
- **复核状态**: 未复核

## 维度复核结论

09-01~06 均以 live code 为据，事实核验成立。09-04 与维度20-01 同源（公共 API 异常类型契约），跨模块角度补强。两档策略合规项（NopAiAgentException、异常链保留、英文消息、无吞异常、LlmErrorClassifier 分类）已确认良好，未报告。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 09-01 | P1 | completion/LlmCompletionJudge.java | 内部组件 catch+fallback+e.toString() 三连 |
| 09-02 | P2 | skill/LLMCurator.java 等3处 | LOG.warn(...,e.toString()) 系统性丢 stack |
| 09-03 | P2 | runtime/recovery/DefaultOrphanRecoveryHandler.java | 显式 throw new NullPointerException |
| 09-04 | P2 | engine/IAgentEngine.java | 公共 API default 方法抛 UOE（跨模块角度见20-01） |
| 09-05 | P3 | reliability/ThresholdBreaker.java 等9处 | exhaustive-switch default 抛 IllegalStateException |
| 09-06 | P3 | runtime/InMemoryActorRuntime.java | createActor mailboxLookup 失败静默降级 |
