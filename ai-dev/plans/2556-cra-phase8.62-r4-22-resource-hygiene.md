# 2556 CRA Phase 8.62 — R4-22 3.1 资源卫生束 b（10 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.62；批次定义 = batch-proposal.md §二 :137 R4-22 行；成员发现 = `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`
> Related: plan 2555（R4-21，AgentSessionLifecycle 同文件 rebase 面）；设计文档 = `../design/concurrency/ai-resource-hygiene-batch.md`（裁定 A-J）
> **PA**：无（10 项全 Java 层；C3-029 兄弟站点触 nop-ai-service）
> **特殊约束**：① AiCommand 三单同文件（C3-005/006/C7-006）合并 diff；② ThresholdBreaker 两单同状态机（C7-005/007）协同；③ C7-015 与 plan 2555 AgentSessionLifecycle 改动同文件 rebase（哨兵/save-restore/预算钉零回退）；④ C3-008 watch 护栏不在本批
> 成员取证（fresh session @ 2026-11-10）：10/10 原样

## Purpose

关闭 R4-22 的 **10 个 P2 工单**：异步包络族（C3-005 同步抛出、C3-006 缓存读写对称、C3-029 DB 副作用隔离）、资源包络族（C5-002 响应缓存磁盘上界+CL-041、C5-017 DeferredAckMailbox 有界化）、可靠性族（C7-003 Retry-After cap、C7-005 probe 世代令牌、C7-006 重试退避+限流复查、C7-007 失败记忆 TTL）、锁序族（C7-015 restore 锁门）。

## Baseline（live 亲读 @ 2026-11-10，取证 agent 独立核验；10/10 原样）

- `AiCommand.java:291-315,333,345`（load catch LOG.info 丢 throwable/save thenApply 裸调/retryNTimes 零延迟）；`ChatServiceImpl.java:448-460`（checkRateLimit 同步 throw）。
- `DbUsageRecorder.java:72,173-174,184`；`DbModelSwitchedMessageWriter.java:157-158`；`ReActAgentExecutor.java:996,1058,1096,1430,1564`；兄弟 `nop-ai-service` `OrmModelSwitchedMessageWriter.java:64`。
- `DefaultAiChatResponseCache.java`（无 max-* 键；`ai-defaults.beans.xml:21` TTL\|0）；CL-041 promptName 路径穿越同文件。
- `DeferredAckMailbox.java:59-60,106,134,174`（deadLetter 裸 ArrayList 无 purge/inFlight 无 stale 回放）。
- `AbstractLlmDialect.java:207,249,316`（四源解析）；`StandardRetryPolicy.java:163,195,212`（retryAfter 原样透传）。
- `ThresholdBreaker.java:98,103,176,205-211,248-265,272,287-298,336`（probe 逃逸仅刷新时间/回执无世代校验/entries 仅 pristine 清理）。
- `AgentSessionLifecycle.java:840,847,853`（restoreSession 先变更后抢锁；plan 2555 哨兵/save-restore 同文件）。
- 基线命令：`./mvnw -o test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent,nop-ai/nop-ai-service -am`（无 docker）。

## Goals

- 10 工单全部落地且载体先红后绿或变异论证（≥3 链）。
- 2555 回归零回退（TestCancelDuringAssembly/TestTenantSaveRestore/TestOrphanRecoveryBudget/TestSessionLifecycleLockGate 既有 7 用例）。
- owner doc `docs-for-ai/03-modules/nop-ai.md` 落账。

## Non-Goals

- C3-008 callStream 晚订阅竞态（watch-only，护栏已随 MA-01 批执行；转立案条件未触发）。
- MA-11 DBMessageService 治理族（不在成员清单）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-J + fresh-session 取证 10/10 原样 + 四接缝约束落位）

- [x] 设计文档（裁定 A-J + 10 载体表 + 回归接缝）
- [x] 成员取证（fresh session：10/10 原样）
- [x] 独立 draft review（R1 must-fix-first → R2；记录见 Review 记录）

### Phase 2 - 10 工单实现 + 回归测试

Status: completed（10 工单全落盘 @ 2026-11-10；三腿变异红证 `_tmp/red-green-ledger-2556.md`）
Targets: nop-ai/nop-ai-core + nop-ai/nop-ai-agent + nop-ai/nop-ai-service + 测试树（G 接线面 = LlmCallCoordinator/ICircuitBreaker 模块内 SPI）

- Item Types: `Fix`（10）

**实施顺序**

- 组 1（nop-ai-core）：A+B+H（AiCommand 三单同 diff）→ C5-002 → F → G+I（ThresholdBreaker）→ C7-006 剩余 = AiCommand 重试循环异常感知改造[限流复查由重试单位=callChat 天然达成，零 ChatServiceImpl 改动]
- 组 2（nop-ai-agent + service）：C3-029 → C5-017 → C7-015

Exit Criteria:

- [x] 10 工单全部实施
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：C3-005 包络、C7-005 世代、C7-015 锁序）
- [x] **2555 回归零回退**：TestCancelDuringAssembly/TestTenantSaveRestore/TestOrphanRecoveryBudget/TestSessionLifecycleLockGate 既有 7 用例全绿
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2556-full-build-excli.log`）
- [x] E3：三模块逐模块归因绿（core 505/agent 3641/service 42；TestBackgroundRegistryJobSink 全量负载下 1F=pre-existing 时序 flake[solo 双跑绿+零 diff 归因]，TestScheduledRecoveryManagerInsulation 同族登记）
- [x] owner doc 落账：`docs-for-ai/03-modules/nop-ai.md`
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow service EXIT=0 + core/agent 命中均为 pre-existing 豁免登记（`_tmp/red-green-ledger-2556.md`）
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
- [x] `./mvnw compile`（三模块）
- [x] `./mvnw test`（三模块逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### C3-008 callStream 晚订阅竞态
- Classification: `watch-only residual`
- Why Not Blocking Closure: 护栏义务已随 C10-001→MA-01 批（plan 2505）执行；转立案条件（契约扩展/多订阅者/MA-01 修复后仍复现）未触发。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-10，must-fix-first）**：B1 裁定 G 无法按文实现（allowCall 返回 boolean 无 token 通道+LlmCallCoordinator 接线点未提）→ API 形态显式化（ICircuitBreaker 模块内 SPI default 方法+兼容回退档+接线断言载体）；M1 裁定 H 限流复查无 seam（IChatService=PA 面）→ 重试层级上移至 callChat（每 attempt 重入天然复查，零 PA 触碰）；M2 策略映射显式化（invalid→TRANSIENT+GlobalTimer 轻量延迟 hop）；M3 A×H 交互（包络化后异常完成不被 thenCompose 重试）→ 新循环异常感知形态；M4 裁定 E 工厂不存在→惰性清扫+opt-in 有界缺省+包络声明；m1 cap 单点化 shouldRetry 入口；m2 probe timeout 经 DefaultAgentEngineConfig；m3 C10-018 义务补登；m4 逐出→文件删除映射；m5/m6 文字。R2 确认后 ready-to-execute。

## Closure

Status Note: 8.62/R4-22 全 10 工单落地（10 Fix），12 载体类 56 钉全绿，三腿变异红证，跨组协同真缺陷（ThresholdBreaker absent-key 首回执丢弃→熔断器永不离开 CLOSED）载体抓获并修复，2555 钉族零回退，owner doc 落账，E1 排除形态 + E3 三模块逐模块绿。closure audit APPROVED（1 cosmetic 非阻塞观察）。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-11）
- Evidence:
  - 10/10 机制 live 取证命中（逐一 file:line：callChat 包络+缓存隔离+异常感知重试/LRU+逐出删文件+CL-041/隔离 guard+CHM 缓存+service 兄弟钉/deadLetter 有界+惰性 stale 扫描+有界缺省工厂接线/shouldRetry 单点 cap 10min/ICircuitBreaker default gen 方法+probeGeneration+90s 可配/TTL 惰性清除/LlmCallCoordinator st.breakerGen 四站点+FALLBACK 重捕获/restore 锁序后置）
  - 独立复跑：core 505 / agent 3641（flake 未复现，零 diff 归因成立）/ service 42 全 BUILD SUCCESS；TestThresholdBreakerEndToEnd 3/3（coordination fix 自洽）；12 载体类全绿
  - Anti-Hollow 三链 PASS（重试分类链/世代链/锁序链）；16 文件 diff 无空体/静默 no-op
  - Deferred 诚实性 PASS（C3-008 watch-only，MA-01 护栏 live 在位）；scan-hollow 豁免与 ledger 精确一致（NoOpProviderFailoverQueue/PlanReplanner 零 diff 核验）
  - cosmetic 观察（非阻塞）：legacy 回退 WARN-once 落在 ThresholdBreaker 而非接口 default（唯一真实实现承载语义）；登记 follow-up
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- ICircuitBreaker 接口 default 方法 legacy 回退 WARN-once 可下沉至接口（当前唯一实现 ThresholdBreaker 已承载，cosmetic）
