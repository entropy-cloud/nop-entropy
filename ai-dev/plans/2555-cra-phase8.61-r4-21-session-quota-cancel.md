# 2555 CRA Phase 8.61 — R4-21 3.1 会话/配额/取消束 a（10 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.61；批次定义 = batch-proposal.md §二 :136 R4-21 行；成员发现 = `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`
> Related: plan 2519（R2-16，硬依赖已解除）、plan 2498（R1-03，MA-16 约束解除）；设计文档 = `../design/concurrency/ai-session-quota-cancel-batch.md`（裁定 A-J）
> **PA**：无（10 项全 Java 层）
> **特殊约束**：① MA-16 阻塞体不得指向 globalTimer（约束收窄后形态）；② C7-004 墙钟轴划出本批（R4-32）；③ C1-010 修复须复核 TestDefaultAgentEngineCancel:229-235 ghost 语义断言；④ SessionLockRenewal/DefaultAgentEngine 同文件多单合并 diff
> 成员取证（fresh session @ 2026-11-10）：原样 9 + MA-12 部分覆盖（C1-004 已修=Proof）+ 已修 1

## Purpose

关闭 R4-21 的 **10 个 P2 工单**：会话存储锁纪律（MA-10）、换装 volatile 清扫（MA-12）、惰性记忆化（MA-13）、复合操作原子化（MA-14）、租户 save-restore（MA-15）、续期调度池（MA-16）、恢复预算（MA-18）、文件读取限额（MA-20）、聊天日志保留期（MA-21）、cancelSession 装配窗 TOCTOU（C1-010）。

## Baseline（live 亲读 @ 2026-11-10，取证 agent 独立核验）

- `FileBackedSessionStore.java:146-174,395-432`（锁外装载+非 heal put 无锁+remove 摘锁复活面）；`DBSessionStore.java:139,165,189`（租户旁路多实例）；`DBCheckpointManager.java:300-304,376` + `FileBackedCheckpointManager.java:472-473,525`（整表替换）。
- MA-12：C1-003 七 static 非 volatile（XmlResponseParser/MarkdownResponseParser/YamlResponseParser/DefaultSystemPromptLoader 等）；C1-013 `DefaultAgentEngine:130,485-487` + `SessionLockRenewal:28,37-39`；C1-022 七组 plain 字段（TeamTaskFlowOrchestrator:169、DBMessageService:140-143,196、CallAgentExecutor:116,126 等）。C1-004 已修（plan 2498，`TestEmbeddingRetrySchedulerLifecycle_C4_004` 在册）。
- MA-13：`PromptModel.java:23,33-35`；`PromptTemplateManager.java:19-22`；`PromptOutputModel.java:20,53-61`（nop-ai-core）。
- MA-14：`InMemoryTeamManager.java:273-289,217`；`InMemoryContributionRegistry.java:112-116`（javadoc :103 失真）；`CalibratedTokenEstimator.java:49,72-83`。
- MA-15：`DefaultAgentEngine.java:523,572,579,613`；`MemberFanOutDispatcher.java:294,312`（plan 279 claim-epoch 语义）。
- MA-16：`SessionLockRenewal.java:71-77,86-`（单线程调度器）；`DbSessionTakeoverLock.java:286+`（tryRenew 无语句超时）。
- MA-18：`DefaultOrphanRecoveryHandler.java:169-190`（fire-and-forget 无预算）；`DefaultSessionTimeoutHandler.java:220` 同构。
- MA-20：`LocalFileOperator.java:102,115,142-143,406,441`（全量读取/自弃上界；**零测试**）。
- MA-21：`DefaultChatLogger.java:42-79`（双文件落盘，无轮转/TTL；MA-01 已改盘写异步化，保留期面零变化）。
- C1-010：`DefaultAgentEngine.java:518-566,837,959-984`（装配窗取消意图静默丢失；else 分支已有 evicted-terminal 适配）。
- 基线命令：`./mvnw -o test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am`（nop-ai 为聚合器；H2 内存库，无 docker）。

## Goals

- 10 工单全部落地且载体先红后绿或变异论证（≥3 链）。
- 既有回归零回退（TestDefaultAgentEngineCancel ghost 断言、TestSessionLockRenewal* 族、TestEmbeddingRetrySchedulerLifecycle_C4_004、TestFanOutFutureLifecycle 等）。
- owner doc `docs-for-ai/03-modules/nop-ai.md` 落账。

## Non-Goals

- C7-004 墙钟敏感轴（→ R4-32）。
- MA-19 接管锁 fencing 加固族（R4-22 范围）。
- MA-11 DBMessageService 治理族（P1，不在本批成员清单）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-J + fresh-session 取证 9 原样/1 部分覆盖 + 四接缝约束落位）

- [x] 设计文档（裁定 A-J + 10 载体表 + 回归接缝）
- [x] 成员取证（fresh session：9 原样 / MA-12 部分覆盖 / C1-004 已修）
- [x] 独立 draft review（R1）

### Phase 2 - 10 工单实现 + 回归测试

Status: completed（10 工单全落盘 @ 2026-11-10；三腿变异红证 `_tmp/red-green-ledger-2555.md`）
Targets: nop-ai/nop-ai-agent + nop-ai/nop-ai-core + 测试树

- Item Types: `Fix`（9）、`Proof`（1：MA-12 内 C1-004 复绿验收）

**实施顺序**

- 组 1（nop-ai-agent 会话/取消面）：MA-10 → C1-010+MA-15+MA-12(DefaultAgentEngine 部分) → MA-14 → MA-16+MA-12(SessionLockRenewal 部分) → MA-18
- 组 2（nop-ai-core 面 + 残余）：MA-13 → MA-20 → MA-21 → MA-12 剩余（C1-003/C1-022）

Exit Criteria:

- [x] 10 工单全部实施（含 C1-004 Proof 复绿验收）
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：MA-14 配额、C1-010 哨兵、MA-16 队头阻塞）
- [x] **ghost 语义零改动**：TestDefaultAgentEngineCancel:229-235 断言零改动复绿
- [x] **MA-16 约束**：续期 DB 阻塞体不指向 globalTimer（结构钉/审阅确认）
- [x] 既有回归零回退：TestSessionLockRenewal* 族 + TestFanOutFutureLifecycle + TestEmbeddingRetrySchedulerLifecycle_C4_004 + TestSessionStoreIntraExecutionPersistence
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2555-full-build-excli.log`）
- [x] E3：逐模块归因绿（nop-ai-agent 3628 / nop-ai-core 471[4 pre-existing skip] 全 0F0E）
- [x] owner doc 落账：`docs-for-ai/03-modules/nop-ai.md`
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow 两叶子命中均为 pre-existing 零 diff 项（关键词误报/有意 NoOp 声明）——豁免逐条登记于 `_tmp/red-green-ledger-2555.md`
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（10 工单）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（两模块）
- [x] `./mvnw test`（两模块逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### C7-004/MA-19 墙钟轴
- Classification: `watch-only residual`（successor 明确承接）
- Why Not Blocking Closure: 墙钟敏感跨单元批 R4-32 统一单调时钟注入（roadmap 8.72 在册成员），MA-19 本就不在本批成员清单。
- Successor Required: `yes`
- Successor Path: roadmap 8.72（R4-32）

## Review 记录

**Draft review R1（fresh session，2026-11-10，must-fix-first）**：Blocker-1 裁定 J 注册前移形态四处未决（CancelHandle.context final/哨兵生命周期/装配失败清理路径声明失真/哨兵输者租约语义）→ 改最小哨兵形态（不改注册时点，putIfAbsent try/finally 两参条件移除，注册侧输者消费哨兵走既有注册后取消流程，入口→getOrCreate 残余窗 watch-only）；M1 裁定 A 租户复合键覆盖三映射+loadedSessions.remove+锁条目机会性移除；M2 裁定 F 换装=启动期语义+新增 @cfg 键；M3 裁定 H globGrep 内部缺省上界不改接口；M4 裁定 I once-per-date 节流；m1/m3/m4 备注（resumeSession 自带 restore 豁免）。R2 确认：裁定 J 哨兵算法 live-code 六点核查通过（ghost 断言兼容/输者租约闭合/双取消幂等/checkpoint 面闭合），A/F 文本落位核验后 ready-to-execute。

## Closure

Status Note: 8.61/R4-21 全 10 工单落地（9 Fix + 1 Proof[C1-004 复绿]），26 载体钉全绿，三腿变异红证，ghost 断言零改动，2518/2498/2519 回归面零回退，MA-16 globalTimer 禁令守恒（grep 零命中），owner doc 落账，E1 排除形态 + E3 两模块逐模块绿。closure audit APPROVED（1 Minor 非阻塞观察）。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-10）
- Evidence:
  - 10/10 机制 live 取证命中（逐一 file:line：装载入锁+锁条目机会性退休/租户复合键三映射/checkpoint remove 含 loadedSessions/取消哨兵两参条件移除全链/save-restore 三站点/配额双保险/续期池零 globalTimer+queryTimeout 新键/恢复预算退避+升级/BoundedFileReader+globGrep 1000/retention 节流/volatile 全家族）
  - 独立复跑：nop-ai-agent 3628 / nop-ai-core 471 全 BUILD SUCCESS（与计划声明精确一致）；TestDefaultAgentEngineCancel 零 diff、ghost 断言 :229-235 原样
  - Anti-Hollow 三链 PASS（哨兵链/配额链/预算链），diffed 生产文件无空体/静默 no-op
  - Deferred 诚实性 PASS（C7-004/MA-19 → R4-32 successor）；既有测试 setMaxAttempts(1) 规格适配非弱化（fire-once 语义保持）
  - 工具门禁：doc-links EXIT=0（auditor 复跑）；scan-hollow 4 项豁免全部零 diff 核验（pre-existing）
  - Minor 观察（非阻塞）：裁定 B 字面「setter 覆盖前旧池 shutdown」与 live 启动期换装语义存在措辞偏差——实际接线路径无泄漏（builder 先于首次派发），javadoc+载体钉定启动期契约；登记 follow-up
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- setAgentExecutor/setLockRenewalExecutor 运行期换装场景的旧 own 池收尾（当前启动期契约已 javadoc+钉承载，实际路径无泄漏）
