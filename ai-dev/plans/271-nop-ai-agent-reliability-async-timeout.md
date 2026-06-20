# 271 nop-ai-agent 可靠性——call-agent 超时不取消子执行 + markConsumed 失败致消息永久丢失 + LLM/工具调用无超时

> Plan Status: completed
> Module: nop-ai-agent
> Last Reviewed: 2026-06-19
> Source: deep audit 2026-06-19-1355 dimensions 14 (async/transaction), findings 14-01, 14-02, 14-03, 14-04
> Related: 270-nop-ai-agent-security-hardening (13-8 deferred here)

## Purpose

收口 deep audit 发现的 3 个 P1 可靠性问题：call-agent 超时后子 agent 僵尸执行、markConsumed 失败导致消息永久卡在 CLAIMED 状态、ReAct 主循环 LLM/工具调用无超时导致会话永久阻塞。

## Current Baseline

- `CallAgentExecutor.java:364-371` 用 `CompletableFuture.orTimeout` 设置超时，但超时仅取消 Future 本身，不取消子 agent 的实际执行。子 agent 继续消耗 LLM API 配额和数据库资源。
- `DBMessageService.java:360-406` `markConsumed`/`releaseClaim` 吞掉 `SQLException`，消息永久卡在 CLAIMED 状态，永远不会被重投递。破坏 "at-least-once" 交付保证。
- `ReActAgentExecutor.java:1268-1374` LLM 调用和工具 fanout `.join()` 无 wall-clock 超时。单个挂起的 LLM/工具调用永久阻塞 agent session、worker thread 和 takeover lock。
- `DefaultAgentEngine.java:1889-1973` 三个引擎入口点都使用 `ForkJoinPool.commonPool()`（默认 ~3-7 线程），多个并发 agent 快速耗尽 commonPool 导致跨 JVM 功能性饥饿。

## Goals

- call-agent 超时时，子 agent 执行被取消（interrupt 或 poison pill），释放 LLM/DB 资源。
- `markConsumed` 失败时抛出异常（或添加 stale CLAIMED 消息清扫机制），确保消息不永久丢失。
- ReAct 主循环的 LLM 调用和工具 fanout 有可配置的 wall-clock 超时。
- 引擎使用专用线程池（或虚拟线程），不共享 `ForkJoinPool.commonPool()`。

## Non-Goals

- 不改消息队列的持久化层或存储引擎。
- 不引入分布式消息队列（如 Kafka/RabbitMQ）替代当前 DB-based 消息。
- 不重设计 ReAct 循环的整体架构。

## Scope

### In Scope

- **Phase 1**: 消息可靠性（markConsumed 失败处理 + stale CLAIMED 清扫）
- **Phase 2**: 执行超时（call-agent 子执行取消 + ReAct LLM/工具超时 + 线程池隔离）

### Out Of Scope

- 14-06 takeover lock 30min 无自动续期：归入后续 plan（需设计 heartbeat 机制）。
- 14-04 commonPool 耗尽的全面重设计：Phase 2 仅做线程池隔离，不做完整 executor 架构。

## Execution Plan

### Phase 1 - 消息可靠性修复

Status: completed
Targets: `DBMessageService.java`

- Item Types: `Fix`

- [x] `markConsumed` 方法在 `SQLException` 时抛出 `NopAiAgentException`（或记录 error 日志 + 标记消息为 FAILED），不吞异常
- [x] `releaseClaim` 方法同上处理 `SQLException`
- [x] 添加 `sweepStaleClaimedMessages` 方法：扫描 `claimed_at < now - timeout` 的 CLAIMED 消息，将其重置为 PENDING 状态
- [x] 添加定时清扫机制（或在 agent 启动时触发一次）
- [x] 添加测试：模拟 `markConsumed` 失败场景，验证消息不永久丢失
- [x] 添加测试：验证 stale CLAIMED 消息被清扫重置

Exit Criteria:

- [x] `markConsumed` 失败时异常向上传播或消息被标记为 FAILED
- [x] stale CLAIMED 消息在超时后被自动重置为 PENDING
- [x] **接线验证**：`markConsumed` 失败路径被测试覆盖（mock DataSource 抛 SQLException，断言异常传播或消息状态变更）
- [x] **无静默跳过**：`sweepStaleClaimedMessages` 在无 stale 消息时不抛异常、不静默吞错
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 所有 in-scope confirmed live defects (14-02) 已修复确认
- [x] No owner-doc update required: `DBMessageService` 为模块内部实现，无 public contract 变更
- [x] `ai-dev/logs/2026/06-19.md` 已更新

### Phase 2 - 执行超时与线程池隔离

Status: completed
Targets: `CallAgentExecutor.java`, `ReActAgentExecutor.java`, `DefaultAgentEngine.java`

- Item Types: `Fix`

- [x] `CallAgentExecutor` 超时处理中检测 `TimeoutException`，调用 `engine.cancelSession(childSessionId)` 取消子 agent 执行
- [x] `ReActAgentExecutor` 的 LLM 调用 `CompletableFuture` 添加 `.orTimeout(timeoutMs, TimeUnit)` 配置
- [x] `ReActAgentExecutor` 的工具 fanout `CompletableFuture` 添加 per-tool `.orTimeout(toolTimeoutMs, TimeUnit)`
- [x] `DefaultAgentEngine` 创建专用 `Executors.newVirtualThreadPerTaskExecutor()` 或固定大小线程池，替代 `ForkJoinPool.commonPool()`（当前 `supplyAsync` 无 executor 参数，默认走 commonPool）
- [x] 超时配置通过 `DefaultAgentEngine` 的 `@InjectValue` 字段注入（`callAgentTimeoutMs`、`llmTimeoutMs`、`toolTimeoutMs`），提供合理默认值
- [x] 添加测试：验证 call-agent 超时后子 agent 被取消
- [x] 添加测试：验证 ReAct LLM 超时后 agent 不永久阻塞
- [x] 添加测试：验证专用线程池被使用（非 commonPool）

Exit Criteria:

- [x] call-agent 超时后子 agent 执行被取消，资源被释放
- [x] ReAct LLM/工具调用有可配置超时，挂起时 agent 不永久阻塞
- [x] 引擎不使用 `ForkJoinPool.commonPool()`
- [x] **接线验证**：`CallAgentExecutor` 超时时调用 `engine.cancelSession()`，验证方式：测试中 mock engine，断言 `cancelSession` 被调用
- [x] **无静默跳过**：`cancelSession` 在 timeout 路径上被调用（非 silent return），超时配置字段有默认值（非 null/0）
- [x] **端到端验证**：从 `doExecute` 入口到超时取消的完整路径已验证
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 所有 in-scope confirmed live defects (14-01, 14-03, 14-04) 已修复确认
- [x] owner-doc 已更新（裁定：`service-layer.md` 非该配置 owner，实际更新 `docs-for-ai/03-modules/nop-ai.md`「Agent 引擎可靠性配置」段 + `docs-for-ai/04-reference/source-anchors.md` `AIREL-001` 锚点）
- [x] `ai-dev/logs/2026/06-19.md` 已更新

## Closure Gates

- [x] Phase 1 Exit Criteria 全部勾选（14-02 消息可靠性）
- [x] Phase 2 Exit Criteria 全部勾选（14-01 超时取消 + 14-03 LLM/工具超时 + 14-04 线程池隔离）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 所有 in-scope confirmed live defects (14-01, 14-02, 14-03, 14-04) 已修复确认
- [x] 无 in-scope 项被静默降级为 deferred
- [x] 独立 closure audit 完成（Reviewer: 独立子 agent task ses_11f794b4dffe8497AqiA74KVvM，CLOSURE_APPROVED）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/271-nop-ai-agent-reliability-async-timeout.md --strict` 退出码 0

## Closure

Status Note: 三项 in-scope P1 可靠性缺陷（14-01 call-agent 超时不取消子 agent / 14-03 ReAct LLM 与工具调用无超时 / 14-04 引擎共享 commonPool）已在 Phase 2 修复并经端到端测试与独立 closure audit 验证落地；Phase 1（14-02 消息可靠性）此前已完成。两项 deferred（14-06 heartbeat、14-04 完整 executor 重设计）均为已裁定的 optimization candidate，不阻断 baseline。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task id ses_11f794b4dffe8497AqiA74KVvM，explore / very thorough，read-only）
- Audit Session: ses_11f794b4dffe8497AqiA74KVvM
- Evidence:
  - Exit Criterion「call-agent 超时取消子 agent」PASS — `CallAgentExecutor.java:345-399`（childSessionId 前置解析 + `.orTimeout` + `.exceptionally` 检测 TimeoutException 调用 `engine.cancelSession(childSessionId,reason,true)`）；handler 对称路径 PASS — `DefaultAgentEngine.java:786-850`
  - Exit Criterion「ReAct LLM/工具超时」PASS — `ReActAgentExecutor.java:2263-2288` `callChatWithTimeout`（supplyAsync + `f.get(llmTimeoutMs)` + 超时 `f.cancel(true)` 抛 CompletionException，retry loop line 1365 实际调用非死代码）；工具 fanout PASS — `ReActAgentExecutor.java:1908-1930` per-tool `.orTimeout` + `.exceptionally` 转错误结果使 `allOf().join()` 不抛
  - Exit Criterion「引擎不使用 commonPool」PASS — `DefaultAgentEngine.java:1701-1721` `getAgentExecutor()`（`nop-ai-agent-exec` 守护线程池）；三入口点 `doExecute:2133` / `resumeSession:2411` / `restoreSession:2587` 均 `supplyAsync(…, getAgentExecutor())`，无 no-arg supplyAsync；`resolveExecutor` wiring `:2894-2896` `.llmTimeoutMs/.toolTimeoutMs/.timeoutExecutor(getAgentExecutor())`
  - Exit Criterion「超时配置默认值」PASS — `DefaultAgentEngine.java:432/440/447`（callAgentTimeoutMs=60000 / llmTimeoutMs=120000 / toolTimeoutMs=300000，均非 0，有 getter/setter）
  - Closure Gate「测试全绿」PASS — `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（2741 tests，0 failures / 0 errors），含新增 `TestPlan271AsyncTimeoutReliability`（5 tests，断言真实非 always-pass：cancelSession 计数、failed 状态、线程名 `nop-ai-agent-exec-*`、非 commonPool、默认值正数）
  - Anti-Hollow 检查 PASS — 端到端调用链 `execute→supplyAsync(专用 executor)→ReAct→callChatWithTimeout→超时→failed` 连通无断点；`callChatWithTimeout` 在 retry loop 实际被调用；三目标文件 grep TODO/FIXME 零命中；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 findings）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/271-nop-ai-agent-reliability-async-timeout.md --strict` 退出码 0
  - Deferred 项分类检查 PASS — 14-06（heartbeat）与 14-04 完整 executor 重设计均 `optimization candidate` + non-blocking 理由；14-04 线程池隔离（in-scope）确已落地，仅完整重设计 deferred，无 in-scope live defect 被降级

Follow-up:

- 14-06 takeover lock heartbeat 续期机制（successor plan required）
- 13-8 Docker `--cpus` 语义修正（来自 plan 270，successor plan required）
- 14-04 完整 executor 架构重设计（按需，当前隔离方案满足 baseline，no successor required）

## Deferred But Adjudicated

- **14-06** `DbSessionTakeoverLock` lease 30min 无自动续期
  - Classification: optimization candidate
  - Why Not Blocking Closure: 长时间运行 agent（>30min）触发 double-execution 是边界场景，当前已有 30min TTL 兜底
  - Successor Required: yes
  - Successor Path: 需设计 heartbeat 续期机制，归入独立后续 plan

- **14-04** commonPool 耗尽的完整 executor 架构重设计
  - Classification: optimization candidate
  - Why Not Blocking Closure: Phase 2 已做线程池隔离（专用 executor），完整重设计为架构优化
  - Successor Required: no（当前隔离方案已满足 baseline）
  - Successor Path: 后续按需重设计

- **13-8** Docker `--cpus 30` 语义错误（cpuSeconds → CPU core count）
  - Classification: optimization candidate
  - Why Not Blocking Closure: 来自 security plan 270 deferred，资源限制语义修正不阻断 reliability baseline
  - Successor Required: yes
  - Successor Path: 顺带修复或独立后续 plan

## Follow-up handled by 273-nop-ai-agent-takeover-lock-heartbeat-renewal.md

- **14-06** `DbSessionTakeoverLock` 租约 30min 无自动续期 → 已由 `ai-dev/plans/273-nop-ai-agent-takeover-lock-heartbeat-renewal.md` 接管（心跳续期 + lease-lost 中止）。本 traceability 标记仅作前向链接，不回写本 completed 计划的其余内容。
