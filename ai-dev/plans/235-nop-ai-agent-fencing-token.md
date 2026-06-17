# 235 nop-ai-agent Fencing Token — foundational monotonic-counter concurrent-write protection primitive

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-fencing-token

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/234-nop-ai-agent-resource-guard-quota.md`（Non-Goals line 52 / Non-Blocking Follow-ups line 219：`Fencing Token（vision §5.1 monotonic counter 并发写入防护）：独立 carry-over（roadmap L4-fencing-token，P2），属 Phase 5 并行项。Classification: successor plan required`；plan 234 已添加 `## Follow-up handled by 235` 链接）+ `ai-dev/plans/232-nop-ai-agent-multi-tenant-isolation.md` + `ai-dev/plans/230-nop-ai-agent-team-db-persistence.md`（同上 carry-over）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §5.1（lines 252-267：Fencing Token 协议）+ §6.2（line 311：Compaction/snapshot 附带 fencing token）+ §10 Phase 4（line 459：Fencing Token = successor）+ §10 line 462（引擎层依赖 `IFencingTokenService` 接口）；`ai-dev/design/nop-ai-agent/glossary.md` line 65（`FencingToken` = actorId + monotonicCounter + issuedAt）
> Related: `234`（ResourceGuard 配额强制 — 本计划 Non-Goal 中明确 Fencing Token 为 successor）、`228`（Team ACL — 同 NoOp shipped 默认 + functional impl 模式）、`218`（IActorRuntime — 同为 primitive 先于 consumer 交付模式）、`221`（DbSessionTakeoverLock — DB CAS 模式可供 DB-backed fencing successor 参考）

## Purpose

把 nop-ai-agent 的并发写入防护从"零 fencing token——无 `IFencingTokenService` 契约、无单调计数器原语、无 stale-token 拒绝机制、无 CAS 原子递增"扩展为"经中央 `IFencingTokenService` 单调计数器决策网关 + NoOp shipped 默认（恒 valid = 零回归）+ functional in-memory CAS 实现，提供 issue（原子递增单调计数器）+ validate（严格递增校验，stale-token 拒绝）原语"。本计划交付 vision §5.1 Fencing Token 协议的 foundational 原语切片，是后续 scope_claim 协调信道 / Compaction 快照写入 / 分支亲和调度注册 / 恢复后重新获取 token 等消费方的最高杠杆基础原语。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src`，2026-06-17）：

- **零 `FencingToken` / `IFencingTokenService` / 单调计数器实现**：grep `fencing|IFencingToken|FencingToken|monotonic.*counter` 在 `nop-ai/nop-ai-agent/src` 返回 0 命中。vision §10 Phase 4（line 459）标注 Fencing Token = successor；§10 line 462 引擎层依赖 `IFencingTokenService` 接口（尚未存在）。
- **vision §5.1 Fencing Token 协议已定义但未实现**（actor-runtime-vision.md lines 252-267）：`FencingToken`（actorId: String + monotonicCounter: long + issuedAt: long epoch ms）。使用规则：(1) Actor 每次 scope_claim 附带当前 fencing token；(2) ResourceGuard 校验 token.counter 必须严格递增；(3) 收到过期 token（counter <= 已记录值）→ 拒绝操作，广播 conflict_alert；(4) Actor 恢复后重新获取 fencing token（counter 重置为 DB 中最大值 + 1）。实现依赖 DB 原子 CAS（`UPDATE ... SET counter = ? WHERE counter = ?`）。
- **vision §6.2（line 311）**：Compaction 和快照操作附带 fencing token，防止并发写入导致状态不一致（compaction 本身为 successor）。
- **glossary.md（line 65）**：`FencingToken` = 并发写入防护令牌：actorId + monotonicCounter + issuedAt，首次定义于 actor-runtime-vision.md §5.1。
- **既有 write-conflict 机制（不同关注点）**：`io.nop.ai.agent.conflict` 包含 `IConflictStrategy` / `ConflictResult` / `FailFastStrategy`（shipped 默认）/ `WriteIntent` / `IWriteIntentRegistry` / `InMemoryWriteIntentRegistry`——这是**文件级 write-intent 冲突检测**（dispatch 路径检测同文件跨 session 写冲突），非单调计数器并发写入防护原语。两者关注点不同：conflict = 文件级意图冲突解决，fencing = 通用的"递增计数器 + stale 拒绝"原语。
- **既有 session 接管锁（CAS 模式可参考但不同抽象）**：`io.nop.ai.agent.runtime.lock` 包含 `ISessionTakeoverLock` / `NoOpSessionTakeoverLock`（shipped 默认）/ `DbSessionTakeoverLock`（独立 `ai_agent_session_lock` 表 + lease/TTL CAS acquire/release）——这是 session 级 lease 锁，使用 DB CAS，但非单调计数器原语。`DbSessionTakeoverLock` 的 DB CAS 模式可供 DB-backed fencing successor 参考。
- **NoOp shipped 默认 + opt-in functional impl 模式已确立**（plan 218/228/234）：每个扩展点有 NoOp 实现（恒 allow/valid/disabled）+ functional impl + 构造期/setter 注入。本计划遵循同一模式：`NoOpFencingTokenService` shipped 默认（恒 valid）+ `DefaultFencingTokenService` functional impl。
- **异常类**：`io.nop.ai.agent.engine.NopAiAgentException`（模块级异常，英文消息）——本计划 primitive 层 validate 返回 decision（非 throw），enforcement-point 反应（throw/broadcast）为 consumer 责任（successor）。
- **roadmap §4 Layer 4 已 100% ✅**；Fencing Token 在 roadmap 中未列为独立 ✅ 项（属 Phase 4/5 successor 池）。

## Goals

- **`FencingToken` 不可变数据对象**：actorId（String）+ monotonicCounter（long）+ issuedAt（long epoch ms），per vision §5.1 + glossary line 65。不可变 + 工厂构造。
- **`IFencingTokenService` 中央单调计数器决策网关契约**（vision §10 line 462 引用此接口名）：issue（原子递增单调计数器，返回新 token）+ validate（校验 token.counter 严格递增 vs 已记录高水位，返回 decision）。
- **`FencingTokenDecision` 不可变结果对象**：valid/stale 状态 + actorId + presentedCounter + recordedCounter + reason（nullable）。valid = counter > recorded（更新 recorded 高水位）；stale = counter <= recorded（拒绝，不更新）。
- **`NoOpFencingTokenService` shipped 默认（零回归）**：validate 恒返回 valid（不强制任何 fencing 约束）——与 `NoOpResourceGuard`/`NoOpTeamAclChecker`/`NoOpSessionTakeoverLock` 模式一致。未 wire functional service 时全部行为不变。
- **`DefaultFencingTokenService` functional in-memory CAS 实现**：per-actor 单调计数器高水位（线程安全）。issue = 原子递增（无重复计数器）；validate = 比较 presented counter vs recorded 高水位（strictly greater → valid + 更新高水位；<= → stale）。线程安全。
- **NoOp shipped 默认零回归**：未 wire functional service 时全部行为不变。既有全量测试零回归。
- **原语生命周期验证**（Anti-Hollow #22）：issue → validate（valid）→ 再 issue → validate 旧 token（stale 拒绝）完整生命周期。并发 issue 原子性验证（无重复计数器）。
- vision §5.1 Fencing Token 协议标注 foundational 原语已落地；vision §10 Phase 4 Fencing Token successor 标注更新。

## Non-Goals

- **scope_claim 协调信道集成**（vision §5.1 规则 1："Actor 每次 scope_claim 时附带当前 fencing token"）：scope_claim/conflict_alert 协调协议本身是独立 successor（plan 234 Non-Goal: ResourceGuard 协调信道 scope_claim/conflict_alert）。本计划只交付原语，不交付协调信道消费方。Classification: successor plan required。
- **conflict_alert 广播**（vision §5.1 规则 3："收到过期 token → 拒绝操作，广播 conflict_alert"）：广播机制依赖协调信道。本计划 validate stale 时返回 decision（含 reason），不广播。Classification: successor（依赖协调信道）。
- **Compaction / 快照写入集成**（vision §6.2 line 311："Compaction 和快照操作附带 fencing token"）：compaction 本身为 successor。本计划只交付原语，不集成 compaction。Classification: successor（依赖 compaction 特性）。
- **DB-backed 跨进程 CAS**（vision §5.1 line 267："实现依赖 DB 原子 CAS"）：本计划 foundational 切片交付 in-memory CAS（单 JVM 内 Virtual Thread 并发防护）。DB-backed 跨进程 CAS（多 JVM 实例共享单调计数器）是 successor。Classification: successor plan required（依赖存储裁定 + ORM model plan-first）。vision §2.3 拒绝多进程模型，单 JVM 是基线。
- **恢复后重新获取 token**（vision §5.1 规则 4："Actor 恢复后重新获取 fencing token，counter 重置为 DB 中最大值 + 1"）：依赖 DB-backed impl。in-memory 场景下进程重启计数器自然重置（内存状态丢失）。Classification: successor（依赖 DB-backed impl）。
- **分支亲和调度注册集成**（`nop-ai-agent-branch-affinity-scheduling.md` line 437/682/757：注册时携带递增 token，旧 session token 自动失效）：分支亲和调度本身未实现（design doc only）。Classification: successor。
- **engine 顶层 `setFencingTokenService` 接线**：本计划无 wired consumer（全部消费方为 successor），不新增 engine 顶层字段。engine 顶层 setter 预留给首个 consumer（scope_claim / Compaction）successor。Classification: successor。
- **ResourceGuard 校验 fencing token**（vision §5.1 规则 2："ResourceGuard 校验 token.counter 必须严格递增"）：ResourceGuard (`IResourceGuard`, plan 234) 当前是 count-based 并发配额网关，fencing token 校验集成是 ResourceGuard 协调信道 successor 的一部分。本计划 validate 由 `IFencingTokenService` 自身完成（primitive 自治），不集成到 ResourceGuard。Classification: successor。

## Scope

### In Scope

- `io.nop.ai.agent.fencing` 包（新包）：
  - `FencingToken` 不可变数据对象（actorId + monotonicCounter + issuedAt）
  - `IFencingTokenService` 契约（issue + validate）
  - `FencingTokenDecision` 不可变结果对象（valid/stale + actorId + presentedCounter + recordedCounter + reason）
  - `NoOpFencingTokenService` shipped 默认（恒 valid）
  - `DefaultFencingTokenService` functional 实现（in-memory CAS）
- 测试文件（contract + concurrency + NoOp 零回归 focused 测试）
- 文档更新（vision §5.1 + §10 Phase 4、roadmap）

### Out Of Scope

- scope_claim 协调信道 / conflict_alert 广播（Non-Goal: 协调协议 successor）
- Compaction / 快照写入集成（Non-Goal: compaction successor）
- DB-backed 跨进程 CAS（Non-Goal: 存储 successor）
- 恢复后重新获取 token（Non-Goal: 依赖 DB-backed impl）
- 分支亲和调度集成（Non-Goal: 未实现 successor）
- engine 顶层接线（Non-Goal: 无 wired consumer）
- ResourceGuard fencing 校验集成（Non-Goal: 协调信道 successor）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **独立 `io.nop.ai.agent.fencing` 包**。与 `quota`（配额）、`conflict`（文件 write-intent 冲突）、`security`（权限）平行。fencing token 是通用的"单调计数器 + stale 拒绝"原语，非文件级冲突、非配额、非权限。理由：(1) 关注点分离——fencing 是更低层原语，服务于多个上层消费方；(2) 与模块既有 one-package-per-concern 约定一致。

2. **NoOp shipped 默认（恒 valid）= 零回归**。`NoOpFencingTokenService` singleton shipped 默认，validate 恒返回 valid（intentional disabled-mode 语义，Javadoc 明确）。未 wire functional service 时全部行为不变。与 `NoOpResourceGuard`（恒 allow）/ `NoOpTeamAclChecker`（恒 allow）/ `NoOpSessionTakeoverLock`（恒 acquire）模式一致。理由：opt-in 扩展点模式，零回归保证。

3. **in-memory CAS foundational（vision §2.3 单 JVM 基线）**。`DefaultFencingTokenService` 使用 per-actor 单调计数器高水位（线程安全并发结构）。issue = 原子递增（无重复计数器）；validate = 比较 + 条件更新高水位。DB-backed 跨进程 CAS（vision §5.1 line 267 `UPDATE ... SET counter = ? WHERE counter = ?`）是 successor。理由：(1) vision §2.3 拒绝多进程模型，单 JVM + Virtual Thread 是基线——in-memory CAS 防护同 JVM 内并发 Actor 写冲突；(2) 与 plan 234 in-memory 配额 + DB successor 模式一致；(3) DB-backed impl 涉及 ORM model 变更（Protected Area plan-first），不应混入 foundational 原语切片。

4. **`FencingToken` 不可变 = actorId + monotonicCounter + issuedAt**（per vision §5.1 + glossary line 65）。工厂构造，final 字段。理由：vision 定义的契约。

5. **`IFencingTokenService` 接口名**（vision §10 line 462 引用）。理由：vision 已引用此接口名作为引擎层依赖的接口之一。

6. **validate 返回 `FencingTokenDecision`（非 throw）**。primitive 提供 query 语义：validate 返回 valid/stale decision（含 actorId/presentedCounter/recordedCounter/reason），enforcement-point 反应（throw `NopAiAgentException` / 广播 conflict_alert / 拒绝操作）是 consumer 责任。理由：(1) 与 `IResourceGuard.checkConcurrent → QuotaDecision`（plan 234）+ `ITeamAclChecker.checkAccess → TeamAclDecision`（plan 228）模式一致——决策与反应分离；(2) 不同 consumer 有不同反应策略（scope_claim = 拒绝+广播，compaction = 拒绝写入），primitive 不应预设。

7. **validate 语义 = strictly greater + 高水位更新**。validate(token)：token.counter > recordedMax(actorId) → valid（原子更新 recordedMax = token.counter）；token.counter <= recordedMax → stale（不更新）。理由：(1) vision §5.1 规则 2-3："counter 必须严格递增"+"counter <= 已记录值 → 拒绝"；(2) 高水位更新确保后续更旧 token 仍被拒绝（单调不回退）。

8. **无 engine 顶层接线**。本计划无 wired consumer（全部消费方为 successor），不新增 `DefaultAgentEngine.fencingTokenService` 字段或 setter。engine 顶层接线预留给首个 consumer successor（scope_claim / Compaction）。理由：(1) Minimum Rules #23 接线验证要求新组件被已有组件运行时调用——本计划无既有 consumer 可接线，强制接线会引入空壳；(2) Anti-Hollow #22 端到端验证 = 原语完整生命周期（issue → validate → stale 拒绝），非"接入某 consumer"。此模式与 plan 218（`IActorRuntime` primitive 先于全部 consumer 交付）一致。

9. **无 DB schema 变更**。in-memory CAS 不涉及 DB。无新表、无新列、无 ORM XML 变更。理由：(1) foundational 原语聚焦；(2) DB schema 变更属 Protected Area plan-first。

### Phase 1 - IFencingTokenService 契约 + FencingToken/FencingTokenDecision + NoOp/Default 实现 + focused 测试

Status: completed
Targets: `io.nop.ai.agent.fencing` 包（新文件：`FencingToken` / `IFencingTokenService` / `FencingTokenDecision` / `NoOpFencingTokenService` / `DefaultFencingTokenService`）、测试文件（`io.nop.ai.agent.fencing` 对应测试包）

- Item Types: `Decision`、`Proof`

- [x] 定义 `FencingToken` 不可变数据对象：actorId（String）+ monotonicCounter（long）+ issuedAt（long epoch ms）。final 字段 + 静态工厂。Javadoc refer vision §5.1（lines 252-267）+ glossary line 65
- [x] 定义 `IFencingTokenService` 契约：issue（`issue(String actorId) → FencingToken`，原子递增单调计数器返回新 token，首次 issue counter 从 1 开始）+ validate（`validate(FencingToken token) → FencingTokenDecision`，校验 strictly greater + 高水位更新）。Javadoc 明确：NoOp 恒 valid；issue 语义 = 每次 issue 返回严格递增 counter；refer vision §5.1 + §10 line 462
- [x] 定义 `FencingTokenDecision` 不可变结果对象：`valid: boolean` + `actorId: String` + `presentedCounter: long` + `recordedCounter: long` + `reason: String`（nullable）+ 静态工厂 `valid(actorId, presentedCounter, recordedCounter)` / `stale(actorId, presentedCounter, recordedCounter)`。stale 工厂 reason 非空（"fencing token stale: presented X <= recorded Y"）
- [x] 实现 `NoOpFencingTokenService`（singleton shipped 默认）。validate 恒返回 valid decision（零回归语义）。issue 返回非 null placeholder token（actorId + counter 0 + now——intentional disabled-mode，Javadoc 明确非空壳）。与 `NoOpResourceGuard` 恒 allow 一致
- [x] 实现 `DefaultFencingTokenService`（functional in-memory CAS）。per-actor 单调计数器高水位（线程安全并发结构）。issue = 原子递增返回新 counter（首次 = 1，后续 2,3,...）。validate = 读高水位，presented > recorded → valid + 原子条件更新高水位（only-if-greater，避免 lost update）；presented <= recorded → stale（不更新）。线程安全
- [x] 编写 focused contract 测试：issue 连续调用返回严格递增 counter（n 次 issue → counter = 1,2,...,n）；validate fresh token（counter == last issued）→ valid；validate stale token（counter < recorded）→ stale + reason 非空
- [x] 编写 focused 高水位更新测试：validate(token counter=3) valid（recordedMax 1→3）→ validate(token counter=2) stale（2 <= 3）→ validate(token counter=1) stale（1 <= 3），验证高水位单调不回退
- [x] 编写 focused 并发原子性测试：多线程并发 issue 同一 actorId（如 100 线程各 issue 1 次）→ 收集全部 counter → 断言无重复（唯一 counter 数 == 线程数）+ 范围连续 [1, n]，验证 CAS 原子性
- [x] 编写 focused NoOp 零回归测试：NoOp validate 恒 valid（任意 token）+ issue 返回非 null placeholder

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `io.nop.ai.agent.fencing` 包下 5 个文件存在且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `FencingToken` 不可变（final 字段 + 工厂构造，actorId + monotonicCounter + issuedAt 与 vision §5.1 lines 252-267 / glossary line 65 一致）
- [x] `DefaultFencingTokenService.issue` 连续调用返回严格递增 counter（focused test 断言 n 次 issue → counter = 1..n，首次 = 1）
- [x] `DefaultFencingTokenService.validate` fresh token（counter == last issued）→ valid；stale token（counter < recorded）→ stale + reason 非空（focused test）
- [x] **高水位更新**（vision §5.1 规则 2-3）：validate valid 时更新 recordedMax，后续更旧 token 仍 stale（focused test：validate #3 valid → #2 stale → #1 stale）
- [x] **并发原子性**（vision §5.1 CAS 语义）：多线程并发 issue 同一 actorId 无重复 counter（focused test，如 100 线程 × 1 issue → 100 唯一 counter）
- [x] `NoOpFencingTokenService` validate 恒 valid + issue 非 null（零回归 focused test）
- [x] **无静默跳过**（Minimum Rules #24）：NoOp 显式返回 valid decision（零回归语义，非 placeholder bug / 非吞掉）；Default validate stale 时显式返回 stale decision + reason（非 null/非静默）
- [x] **端到端验证**（Minimum Rules #22，原语生命周期）：issue → validate（valid）→ issue → validate 旧 token（stale 拒绝）完整生命周期验证（focused test 覆盖完整路径，不止组件级类型存在）
- [x] No owner-doc update required（vision §5.1 / roadmap 更新在 Phase 2）

### Phase 2 - 文档更新 + daily log

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`（§5.1 + §10 Phase 4 + §10 line 462）、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §5.1（lines 252-267 区域）：Fencing Token 协议标注 foundational 原语已落地（`FencingToken` 不可变数据对象 + `IFencingTokenService` 契约 issue/validate + `FencingTokenDecision` valid/stale + `NoOpFencingTokenService` shipped 默认恒 valid + `DefaultFencingTokenService` in-memory CAS）；标注 scope_claim 集成 / conflict_alert 广播 / Compaction 集成 / DB-backed CAS / 恢复后重新获取 token 仍为 successor
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §10 Phase 4（line 459）：Fencing Token 从 successor 标注为 🟡 部分落地（foundational 原语已交付，in-memory CAS；DB-backed + 恢复重获取为 successor）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §10 line 462：`IFencingTokenService` 接口已存在（引擎层依赖的接口之一已交付；engine 顶层接线预留给 consumer successor）
- [x] 更新 `nop-ai-agent-roadmap.md`：新增 L4-fencing-token ✅ 行（foundational 原语切片：FencingToken + IFencingTokenService + NoOp/Default + in-memory CAS；scope_claim/Compaction/DB-backed 集成为 successor）
- [x] `ai-dev/logs/` 对应日期条目更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-actor-runtime-vision.md` §5.1 已更新（Fencing Token foundational 原语已落地标注 + successor 标注）
- [x] `nop-ai-agent-actor-runtime-vision.md` §10 Phase 4 已更新（Fencing Token 🟡 部分落地）
- [x] `nop-ai-agent-actor-runtime-vision.md` §10 line 462 已更新（`IFencingTokenService` 接口已存在）
- [x] `nop-ai-agent-roadmap.md` 已更新（L4-fencing-token ✅ 行）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `FencingToken` + `IFencingTokenService` + `FencingTokenDecision` + `NoOpFencingTokenService`（shipped 默认）+ `DefaultFencingTokenService` 存在且编译通过
- [x] issue 单调递增 + validate strictly-greater/高水位更新 + stale 拒绝 + 并发 CAS 原子性 经 focused test 验证
- [x] NoOp shipped 默认零回归（`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（scope_claim 集成 / conflict_alert 广播 / Compaction 集成 / DB-backed CAS / 恢复后重获取 / 分支亲和集成 / engine 接线 / ResourceGuard 校验集成 均为显式 Non-Goals successor）
- [x] 受影响 owner docs 已同步（vision §5.1 + §10 Phase 4 + roadmap）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`DefaultFencingTokenService` issue/validate 是真实 functional 实现（in-memory CAS，非 stub/no-op），（b）原语完整生命周期（issue → validate valid → stale 拒绝）经 focused test 验证，（c）NoOp 默认显式 valid（零回归语义，非吞掉），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/235-nop-ai-agent-fencing-token.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0

## Deferred But Adjudicated

（暂无；scope_claim 集成 / conflict_alert 广播 / Compaction 集成 / DB-backed CAS / 恢复后重获取 / 分支亲和集成 / engine 接线 / ResourceGuard 校验集成 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **scope_claim 协调信道集成**（vision §5.1 规则 1）：Actor scope_claim 附带 fencing token。Classification: successor plan required（依赖协调信道协议裁定 + ResourceGuard 协调信道 successor）。
- **conflict_alert 广播**（vision §5.1 规则 3）：stale token 触发 conflict_alert 广播。Classification: successor（依赖协调信道）。
- **Compaction / 快照写入集成**（vision §6.2 line 311）：Compaction 和快照操作附带 fencing token。Classification: successor（依赖 compaction 特性）。
- **DB-backed 跨进程 CAS**（vision §5.1 line 267）：`UPDATE ... SET counter = ? WHERE counter = ?` DB 原子 CAS。Classification: successor plan required（依赖存储裁定 + ORM model plan-first）。
- **恢复后重新获取 token**（vision §5.1 规则 4）：counter 重置为 DB max + 1。Classification: successor（依赖 DB-backed impl）。
- **分支亲和调度注册集成**（branch-affinity-scheduling.md）：注册时携带递增 token。Classification: successor（分支亲和未实现）。
- **engine 顶层 `setFencingTokenService` 接线**：Classification: successor（无 wired consumer，预留给首个 consumer）。
- **ResourceGuard fencing 校验集成**（vision §5.1 规则 2）：Classification: successor（依赖协调信道）。

## Closure

Status Note: Foundational Fencing Token primitive slice delivered — `FencingToken` + `IFencingTokenService`（issue/validate）+ `FencingTokenDecision` + `NoOpFencingTokenService` shipped 默认（恒 valid 零回归）+ `DefaultFencingTokenService` functional in-memory CAS。All Phase 1 + Phase 2 Exit Criteria ticked `[x]`，两个 Phase Status 均 `completed`。8 项 consumer-side 集成（scope_claim/conflict_alert/Compaction/DB-backed CAS/recovery-token/branch-affinity/engine-wiring/ResourceGuard-fencing）均为显式 Non-Goal successor，各自依赖未实现的下游特性，非隐藏 defect。无 wired consumer 故无 engine 顶层接线（setter 预留首个 consumer，与 plan 218 primitive-先于-consumer 模式一致）。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 explore 子 agent（fresh session，task id `ses_12a826ae1ffe0rZgJqQ3hJvKpA`，不同于实现 session）
- Audit Session: `ses_12a826ae1ffe0rZgJqQ3hJvKpA`
- Evidence:
  - **A.1 FencingToken**（PASS）: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/fencing/FencingToken.java:27` `public final class`，3 个 `private final` 字段（actorId/monotonicCounter/issuedAt，:29-31）+ 私有构造器（:33）+ 静态工厂 `of`（:47）+ null guard（:34）。
  - **A.2 FencingTokenDecision**（PASS）: `FencingTokenDecision.java:41` final + 5 final 字段 + 私有构造器；`valid` 工厂（:70）reason=null；`stale` 工厂（:84）auto-generate reason `"fencing token stale: presented X <= recorded Y"`。
  - **A.3 IFencingTokenService**（PASS）: `IFencingTokenService.java:65` interface，`issue(String)→FencingToken`（:76）+ `validate(FencingToken)→FencingTokenDecision`（:91）。
  - **A.4 NoOpFencingTokenService**（PASS）: `NoOpFencingTokenService.java:33` singleton；`validate` 恒返回 explicit valid decision（:62，非 null/非静默）；`issue` 返回非 null placeholder counter=0（:48-53）。
  - **A.5 DefaultFencingTokenService**（PASS, real CAS）: `DefaultFencingTokenService.java:59-62` `issue` 经 `AtomicLong.incrementAndGet`（首次=1）；`:66-87` `validate` 真实 `while(true)` CAS loop + `compareAndSet` only-if-greater + race retry，非 stub 非 `getAndUpdate(max)`。
  - **B Anti-Hollow**（PASS）: NoOp validate 显式 valid（:62）+ Default stale 显式 stale+reason（:84）；5 个 main 文件 grep `continue;|return null;|throw new RuntimeException` 0 命中，无空方法体/静默跳过/吞异常。
  - **C Test coverage**（PASS, 22 tests 全 6 类）: `TestFencingTokenService.java` — issue 连续递增首次=1（`issueReturnsStrictlyIncreasingContiguousCounters`/`issueFirstCallReturnsCounterOne`）/ fresh valid + stale+reason（`validateFreshTokenIsValid`/`validateStaleTokenIsRejectedWithReason`）/ 高水位单调不回退 #3→#2→#1（`highWaterMonotonicallyAdvancesAndNeverRegresses`）/ 并发 issue 100 线程唯一连续（`concurrentIssueProducesUniqueContiguousCounters`）/ NoOp 零回归（`noOpValidateAlwaysValidForAnyToken`/`noOpIssueReturnsNonNullPlaceholderToken`）/ 原语生命周期 issue→valid→issue→stale（`primitiveLifecycleIssueValidateValidIssueValidateStale`）。
  - **D Docs**（PASS）: vision §5.1 landed note（line 269）+ §10 Phase 4 Fencing Token 🟡 部分落地（line 461）+ §10 Phase 5 移出 successor（line 462）+ §10 line 464 `IFencingTokenService` 接口已存在（line 464）；roadmap §4 Layer 4 新增 `L4-fencing-token` ✅ 行（`nop-ai-agent-roadmap.md:258`）；daily log `ai-dev/logs/2026/06-17.md` 顶部条目。
  - **E Tests**（PASS）: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（2482 tests，0 failures，Phase 1 前 2460，+22 focused）。测试引用的 main 类/方法经 audit 核对全部存在。
  - **`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/235-nop-ai-agent-fencing-token.md --strict`** 退出码为 0（见下"工具复核"）。
  - **Anti-Hollow 检查结果**: 端到端原语生命周期（issue→validate valid→issue→validate 旧 token stale）经 `primitiveLifecycleIssueValidateValidIssueValidateStale` focused test 验证；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0（0 critical/high/medium/low 发现）。
  - **Deferred 项分类检查**: 全部 8 项 successor（scope_claim 协调信道 / conflict_alert 广播 / Compaction 集成 / DB-backed CAS / 恢复后重获取 token / 分支亲和集成 / engine 顶层接线 / ResourceGuard fencing 校验）为显式 Non-Goal，各自依赖未实现下游特性（协调信道 / compaction / 存储 ORM model plan-first / 分支亲和），非 in-scope live defect 降级——CONFIRMED。

Follow-up:

- 无 plan-owned 剩余工作。8 项 successor 见 `## Non-Blocking Follow-ups` + `## Non-Goals`（均 Classification: successor plan required，依赖各自下游特性）。
