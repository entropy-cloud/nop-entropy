# 280 nop-ai-agent ReAct fan-out 孤儿 future 取消与可中断 join

> **Plan Status**: planned
> **Module**: nop-ai-agent
> **Work Item**: WI-REACT-FANOUT-CANCEL
> Last Reviewed: 2026-06-20
> Source: carry-over AR-15 + 14-02 from `ai-dev/plans/278-nop-ai-agent-engine-resource-lifecycle-recovery-delegation-bounds.md`（§Deferred But Adjudicated AR-15、§Non-Blocking Follow-ups 14-02）
> Related: Plan 278（引擎资源生命周期）、Plan 277（ReAct 主循环消息契约）

## Granularity / Bundle Justification

> 本计划合并 ROADMAP 预分组的两个 carry-over（`<NEXT_ITEM>` AR-15 + 14-02）。预估生产代码改动 **~22 行**（AR-15 ~10 行 + 14-02 ~12 行），低于 ~100 行默认下限。已强制检索 bundle sibling：唯一同区域候选 AR-18（`FingerprintPostDenialGuard` 指纹集无 tenant 维度）属**多租户隔离执行模型**，与 fan-out future 生命周期不同根因、不同代码路径，按 Carry-Over Workflow 显式**不可 bundle**（roadmap 标注 not bundle-eligible）。其余 carry-over（02-02 God Object 拆分、sandbox→tool wiring、05-2/14-04 优化项）范围/风险差异更大，亦不可并入。因此本计划是**最大合格 bundle，无更多 eligible sibling**，按 Granularity Rule 的 below-granularity escape hatch 执行（ROADMAP 将在下一周期尝试更强 bundle）。

## Bundled Items

| Item | Source Plan | Section | Root Cause |
|------|------------|---------|------------|
| AR-15 | `278-nop-ai-agent-engine-resource-lifecycle-recovery-delegation-bounds.md` | §Deferred But Adjudicated | fan-out 构建循环同步抛异常时已启动的 tool future 逃逸 `allOf().join()` 成为孤儿 |
| 14-02 | `278-nop-ai-agent-engine-resource-lifecycle-recovery-delegation-bounds.md` | §Non-Blocking Follow-ups | fan-out `allOf(...).join()` 不可中断，lease-lost/forced-cancel 中断打不断 join |

## Purpose

把 ReAct 工具 fan-out 批处理（`ReActAgentExecutor.java:1949-1988`）的两个 future 生命周期缺口收口为"构建期同步抛异常时已启动的 future 被显式取消、等待期可被线程中断打破"的正确语义，消除孤儿 tool 执行与 lease-lost 后 join 不可打断导致的 abort 失效。

## Current Baseline

- fan-out 批处理位于 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1949-1988`：先在 `for (ChatToolCall : allowedCalls)`（`:1951-1984`）中逐个调 `toolManager.callTool(...)`（`:1966`）并组装 `CompletableFuture<ToolCallOutput>`（带可选 `orTimeout`，`:1969-1982`），加入 `futures` 列表；随后 `CompletableFuture.allOf(futuresArray).join()`（`:1986-1988`）等待全部完成，再在 `:1990+` 逐个 `f.join()` 取结果处理。
- **AR-15（confirmed P3，事实）**：构建循环（`:1951-1984`）无 try-catch。若 `toolManager.callTool(...)` 在循环中段同步抛（如第 2 个 tool 的同步校验失败），异常直接冒泡跳过 `allOf().join()`（`:1988`），**已加入 `futures` 的前序 tool future 成为孤儿**——它们继续在后台执行 tool 副作用，既不被取消也不被等待/收集结果，造成资源泄漏与不可观测的孤儿 tool 执行。
- **14-02（confirmed P3，事实）**：`CompletableFuture.allOf(futuresArray).join()`（`:1988`）使用 `.join()`。`CompletableFuture.join()` **不响应线程中断**。lease-lost（`DefaultAgentEngine.java:1846` `t.interrupt()`）或 forced-cancel（`:2010` `t.interrupt()`）触发时，中断信号打不断该 join，ReAct-loop 线程一直 park 直到**所有** tool future 结算（或各自 `orTimeout` 触发），使 abort 语义失效。
- **既有可参考模式（已落地的正确先例）**：同文件 `callChatWithTimeout`（`:2363-2388`）已显式采用 `f.get(llmTimeoutMs, TimeUnit.MILLISECONDS)` 而非 `.join()`，其 Javadoc（`:2356-2361`）明确记载理由："`.join()` is not interruptible and would block until the LLM call completes... defeating the purpose of a forced cancel"，并在 `InterruptedException` 分支（`:2371-2374`）恢复中断标志后抛出。这是本仓已确立的可中断等待范式。
- lease-lost/forced-cancel 的中断注入点：`AgentExecutionContext.leaseLost`（`AgentExecutionContext.java:44/225-229`）由 `DefaultAgentEngine` 在 `tryRenew` 返回 false 时置位并 `t.interrupt()`（`:1837-1846`）；forced-cancel 在 `:2010` `t.interrupt()`。
- Plan 278 已 completed（`Completed: 2026-06-20`），AR-15/AR-18 作为 watch-only residual 显式裁定移出，14-02 列为 Non-Blocking Follow-up。本计划接管 AR-15 + 14-02。

## Goals

- AR-15：fan-out 构建循环同步抛异常时，**已启动的** tool future 被显式 `.cancel(true)` 取消（不作为孤儿继续执行），异常照常向上冒泡。
- 14-02：fan-out 等待期响应线程中断——lease-lost/forced-cancel 的 `t.interrupt()` 能**立即**打破 fan-out 等待，不再 park 到所有 tool 结算。

## Non-Goals

- 不处理 AR-18（`FingerprintPostDenialGuard` 指纹集无 tenant 维度）——属多租户隔离执行模型，按 roadmap 归属多租户专题计划（含 13-01/13-03/14-04）。
- 不重构 fan-out 批处理的整体结构（不改变"先全部启动再 allOf 等待"的并发模型，不引入每-tool 独立超时替代 batch 等待）。
- 不改动 `toolManager.callTool` 契约、不改动 `orTimeout` 单 tool 超时语义（`:1969-1982` 保持不变）。
- 不处理 team-flow `MemberFanOutDispatcher`（不同子系统，独立 fan-out 机制）。
- 不拆分 `DefaultAgentEngine` God Object（深度审核 02-02，独立计划）。

## Scope

### In Scope

- AR-15：`ReActAgentExecutor.java` fan-out 构建循环（`:1951-1984`）同步抛异常的孤儿 future 取消。
- 14-02：`ReActAgentExecutor.java` fan-out `allOf(...).join()`（`:1986-1988`）替换为可中断等待，并在中断时取消已启动 future。
- 针对两项各新增 focused 测试。

### Out Of Scope

- AR-18 多租户指纹维度（归属多租户专题）。
- fan-out 并发模型重构、`callTool`/`orTimeout` 契约变更。
- `MemberFanOutDispatcher`、God Object 拆分、sandbox→tool wiring。

## Execution Plan

> Phase 依赖：Phase 1（AR-15）与 Phase 2（14-02）修改同一代码块（`:1949-1988`），**顺序执行**：Phase 1 先把构建循环包进 try-catch 并落地取消语义，Phase 2 在 Phase 1 产出的结构上把 `join()` 替换为可中断 `get()`。两者根因不同、测试不同，故拆为两个 Phase。

### Phase 1 - fan-out 构建循环同步抛孤儿取消（AR-15）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（fan-out 构建循环 `:1951-1984`，`futures` 列表 `:1950`）

- Item Types: `Fix`

- [ ] 把 fan-out 构建循环（逐个 `callTool` 并组装 future、加入 `futures`）置于 try 块中；在 catch 中遍历**已加入 `futures`** 的 future 并对每个调 `CompletableFuture.cancel(true)`，取消后**照常重抛**捕获的异常（不得吞异常）。
- [ ] 取消操作必须覆盖"循环进行到第 N 个时同步抛"的一般情形：仅取消已实际启动的 future，不触碰尚未构建的项（自然由循环中断保证）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **新增 focused 测试**：构造 `allowedCalls` 含 ≥2 个 tool call，其中第 2 个 `toolManager.callTool` **同步抛异常**；断言：(a) 第 1 个已启动的 tool future 被取消（`CompletableFuture.isCancelled()` 为 true，或其 tool 副作用被中断/未完整执行——通过可观测的计数器/标志位验证，不只是类型判断）；(b) 异常照常向上传播（执行方收到预期异常，非静默吞掉）；(c) 无孤儿 tool 执行残留（第 1 个 tool 的后台执行未在取消后继续完成副作用）。
- [ ] **无静默跳过**：catch 中不吞异常（至少重抛；如记录则 `LOG.warn(...,e)` 传 throwable），取消后异常显式传播。
- [ ] repo-observable：`ReActAgentExecutor.java` fan-out 构建循环外有 try-catch，catch 内对 `futures` 中每个 future 调 `cancel(true)` 并重抛（grep `cancel(true)` 在该代码块内可观测）。
- [ ] 若改 live baseline / public contract：`ai-dev/design/`（fan-out future 生命周期契约）已更新或明确写 `No owner-doc update required`（本项为内部健壮性修复，不改公共契约）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - fan-out 等待可中断化（14-02）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（`allOf(futuresArray).join()` `:1986-1988`）；参照既有范式 `callChatWithTimeout`（`:2363-2388`，尤其 Javadoc `:2356-2361` 与 InterruptedException 处理 `:2371-2374`）

- Item Types: `Fix`

- [ ] 把 `CompletableFuture.allOf(futuresArray).join()` 替换为**可中断等待**（对齐 `callChatWithTimeout` 已确立的 `get()` 范式；此场景为 batch 等待无单一总超时——每个 tool 已有自己的 `orTimeout`，故采用无超时但可中断的 `get()` 形态）。
- [ ] 在 `InterruptedException` 分支：恢复中断标志（`Thread.currentThread().interrupt()`），对 `futuresArray` 中每个 future 调 `cancel(true)`（中止在途 tool 执行），抛出与现有 ReAct 中断路径一致的异常（对齐 `callChatWithTimeout` `:2373-2374` 的 `NopAiAgentException` 语义——"interrupted (forced cancel or thread interrupt)"），使 lease-lost/forced-cancel 能立即打破等待。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **新增 focused 测试**：构造 fan-out 批处理中有 ≥2 个**慢 tool**（各自 sleep 足够久，远长于测试断言窗口），在 ReAct-loop 等待 `allOf` 期间对执行线程发 `interrupt()`（模拟 lease-lost `t.interrupt()`）；断言：(a) 等待**在远小于 tool sleep 时长**的窗口内返回/抛出（证明 join 被中断打破，而非 park 到 tool 结算）；(b) 中断标志被恢复；(c) 已启动的慢 tool future 被取消（其后台执行被中止，副作用计数器不再增长）。
- [ ] **接线验证**：中断后已启动 future 确实被 `cancel(true)`（测试断言 `isCancelled()` 或 tool 副作用停止增长），不残留孤儿。
- [ ] **无静默跳过**：`InterruptedException` 不被吞——恢复中断标志后抛出明确异常，不返回 null/空/继续后续 `f.join()`。
- [ ] repo-observable：`ReActAgentExecutor.java` fan-out 等待处不再使用不可中断的 `.join()`，改用可中断 `get()` 形态并有 `InterruptedException` 处理 + `cancel(true)` + 恢复中断标志（grep 该代码块可观测）。
- [ ] 若改 live baseline / public contract：`ai-dev/design/`（fan-out future 生命周期/中断契约）已更新或明确写 `No owner-doc update required`（本项使行为与既有 `callChatWithTimeout` 中断语义对齐，不改新增公共契约）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] AR-15：fan-out 构建循环同步抛异常时已启动 future 被取消、异常照常传播、无孤儿 tool 执行（focused 测试验证）。
- [ ] 14-02：fan-out 等待可被线程中断打破、中断标志恢复、已启动 future 被取消（focused 测试验证）。
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope confirmed live defect（AR-15/14-02 均落地，AR-18 明确 out-of-scope 归属多租户专题）。
- [ ] 受影响 owner docs（fan-out future 生命周期/中断契约）已同步或显式 `No owner-doc update required`。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：取消/中断逻辑确有真实方法体并在运行时被触发（测试验证 `isCancelled()`/副作用停止/等待被打破），无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（零回归）。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

> 本计划 in-scope 无 deferred 项。AR-18 维持其原归属，不进入本计划：

### AR-18（FingerprintPostDenialGuard 指纹集无 tenant 维度）—— out-of-scope，沿用原 successor

- Classification: `watch-only residual`（维持 Plan 278 裁定）
- Why Not Blocking Closure: 与 AR-15/14-02 不同执行模型（多租户隔离 vs fan-out future 生命周期），不同代码路径，不可 bundle。
- Successor Required: `yes`
- Successor Path: 多租户隔离一致性专题计划（含 13-01、13-03、14-04）——不在本计划。

## Non-Blocking Follow-ups

- AR-18 多租户指纹维度（归属多租户专题，见上）。
- 02-02 DefaultAgentEngine God Object 渐进拆分（独立计划）。
- sandbox→shell-exec/code-exec tool executor wiring（独立大计划，见 Plan 276）。
- 若 Phase 2 引入 batch 级总超时需求（当前每 tool 已有 `orTimeout`，无需），可后续评估。

## Closure

> 待执行 + 独立 closure audit 后填写。

Status Note: <<待填写>>
Completed: <<待填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<待填写：独立子 agent（fresh session）>>
- Audit Session: <<待填写>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + live code path / test name）
  - 每条 Closure Gate 的验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果；`scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
  - Deferred 项分类检查：确认 AR-18 未被降级、维持 out-of-scope

Follow-up:

- <<待填写：只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
