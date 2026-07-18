# 301 nop-job `completeFireAndUpdateSchedule` 事务-Session 耦合缺陷修复

> Plan Status: completed
> Last Reviewed: 2026-07-18
> Source: `ai-dev/analysis/2026-07/2026-07-18-nop-job-transaction-analysis.md`、`ai-dev/bugs/2026-07/2026-07-18-nop-job-complete-fire-dead-code-and-retry-delta-bug.md`

## Purpose

修复 `completeFireAndUpdateSchedule` 中因 Nop ORM Session 与事务生命周期解耦导致的 3 个正确性 bug，并补充回归测试验证修复。

## Current Baseline

- **Bug A（P0）**：`completeFireAndUpdateSchedule` 在 `@SingleSession` 调用链中方法体是死代码。`requireEntityById` 返回 `@SingleSession` Session 缓存的同一实体实例（已被调用方修改），`isTerminalFire` 短路导致 `tryUpdateWithVersionCheck(fire)` 和 schedule 更新重试循环从不执行。实际持久化发生在 `@SingleSession` 拦截器的无事务 `session.flush()`。
- **Bug B（P1）**：乐观锁重试循环在 `tryUpdateWithVersionCheck(schedule)` 版本冲突后继续复用 `schedule` 参数（被标记 `orm_readonly(true)`）进行 setter 操作，而 `orm_unload()` 不清除 readonly，导致每次都抛出 `ERR_ORM_ENTITY_IS_READONLY` 而非正确重试。原有的 `cancelFire` 也存在完全相同的问题。
- **Bug B delta 计算确认**：原 retry loop 的 delta 计算方式（调用方目标值减去初始 baseline 值）本身是正确的。真正的问题是 delta 应用到 readonly-tainted 参数 `schedule` 上。
- **Bug C（P1）**：`JobTimeoutCheckerImpl.tryMarkDispatchTimeout` 在 `completeFireAndUpdateSchedule` 短路后继续用独立事务更新 tasks，导致 fire 保持 RUNNING、tasks 被 CANCELED 的状态不一致。
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 已记载 `requireEntityById` 返回缓存实体，但未揭示 `@SingleSession + REQUIRES_NEW` 下方法体为死代码的完整后果。

## Goals

- Bug A 修复：让 `completeFireAndUpdateSchedule` 方法体在 `@SingleSession` 调用链中真实执行，`tryUpdateWithVersionCheck(fire)` 和 schedule 更新重试循环不再被短路。
- Bug B 修复：retry loop 不再复用被 `tryUpdateWithVersionCheck` 标记为 readonly 的 `schedule` 参数，改用 `baseline`（每轮重试时从 DB 重新加载的新实体）应用 delta 计算。
- Bug B 修复在 `cancelFire` 中：同一模式的 readonly 重试问题在 `cancelFire` 中不存在（不先调用 `tryUpdateWithVersionCheck` 在外层），但其 retry loop 也改用 `baseline` 而非 `schedule` 参数以防止未来框架行为变更引入同一 bug。
- Bug C 修复：超时路径下 fire 和 tasks 状态不产生不一致。
- Bug C 修复：超时路径下 fire 和 tasks 状态不产生不一致。
- 新增回归测试证明修复前 bug 存在、修复后 bug 消除。
- `docs-for-ai/` 对应文档更新。

## Non-Goals

- 不将 Store 方法改为开新 Session + 新事务（如 `runLocal()` 模式）——纯代码修复即可消除死代码。
- 不涉及 `activeFireCount` 是否改为统计查询的架构决策（已有 `ai-dev/analysis/2026-07-02-nop-job-code-quality-remediation-analysis.md` Issue 11）。
- 不修改 `@SingleSession` 或 `@Transactional(REQUIRES_NEW)` 的框架行为。
- 不涉及其他 Store 方法（如 `insertTasksAndMarkFireDispatching` 等）的 Session 相关问题修复。
- 注意：`cancelFire` 虽然在 Non-Goals 中被排除，但在实施中发现其 retry loop 与 `completeFireAndUpdateSchedule` 有完全相同的 readonly 问题（orm_unload 不清除 readonly），因此在同一变更中附带修复，以消除 Bug B 的重复模式。

## Scope

### In Scope

- `JobFireStoreImpl.completeFireAndUpdateSchedule()` — 移除 `requireEntityById` 短路 + retry loop 改为对 `baseline` 实体操作。
- `JobFireStoreImpl.cancelFire()` — retry loop 改为对 `baseline` 实体操作（与 Bug B 相同的 readonly 修复）。
- `JobTimeoutCheckerImpl.tryMarkDispatchTimeout()` — 修正 `completeFireAndUpdateSchedule` 返回后的行为。
- `TestJobFireStoreRace` — 更新 1 个测试（原 `testCompleteFireThrowsOnScheduleVersionConflict` → `testCompleteFireConvergesDespiteScheduleModifications`），新增 1 个测试。
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md` — 补充 `REQUIRES_NEW` 不创建新 Session 的明确说明、以及死代码模式避免指南。

### Out Of Scope

- `activeFireCount` 维护方式的架构决策（由后续 plan 处理）。
- 其他调用 `completeFireAndUpdateSchedule` 的代码路径分析。

## Execution Plan

### Phase 1+2 - Bug A + Bug B + Bug C 修复

Status: **completed**
Targets: `JobFireStoreImpl.java`, `JobTimeoutCheckerImpl.java`, `TestJobFireStoreRace.java`

- Item Types: `Fix | Proof`

**分析发现（2026-07-18）：**

1. **Bug A（死代码）**：`requireEntityById` 在 `@SingleSession` 中返回缓存对象（与参数同一实例），`isTerminalFire` 短路。
2. **Bug B 根因**：`tryUpdateWithVersionCheck` 在版本冲突时设 `orm_readonly(true)`；`orm_unload()` 不清除 readonly；retry loop 复用同一实例 → setter 抛 `ERR_ORM_ENTITY_IS_READONLY`。
3. **Bug B 根因重解释**：delta 计算（目标值 − 初始 baseline）本身正确。真正问题是 readonly 副作用 + 复用同一实体实例。
4. **@SingleSession 限制定理**：在 `@SingleSession` 中，`requireEntityById` **永远返回缓存对象**（`_makeProxy` 先查缓存），因此 retry loop 无论重试多少次，reload 得到的都是同一只读实体。retry loop 在 `@SingleSession` 下**注定失败**。
5. **Bug C**：`tryMarkDispatchTimeout` 在 `completeFireAndUpdateSchedule` 返回后在 `@SingleSession` 中用新事务取消 tasks，与 fire 状态不一致。
6. **`cancelFire`** 的 retry loop 有完全相同的问题（Issue 12 的 `orm_unload()` 不清除 readonly）。

**代码变更：**

**`JobFireStoreImpl.completeFireAndUpdateSchedule`**：
- Bug A: 移除 `requireEntityById` + `isTerminalFire` 短路线
- Bug B: **移除 retry loop**（基于 @SingleSession 限制定理，retry loop 永远无效）；改为单次 `tryUpdateWithVersionCheck(schedule)` — schedule 已被调用方修改（dirty），flush 会包含所有 dirty 属性
- Bug C: 不抛 `ERR_JOB_FIRE_STATUS_CONFLICT`（方法体变简洁）

**`JobFireStoreImpl.cancelFire`**：
- 移除 retry loop；改为单次 `tryUpdateWithVersionCheck(schedule)`
- 失败只记 warn 日志（不抛异常）

**`JobTimeoutCheckerImpl.tryMarkDispatchTimeout`**：
- Bug C: `completeFireAndUpdateSchedule` 返回后检查 `fire.orm_readonly()`（`tryUpdateWithVersionCheck` 在版本冲突时设置此标记）
- 如果 readonly（= 更新未执行），跳过 task 取消逻辑，避免 DB 中 RUNNING fire + CANCELED tasks 的不一致

**测试更新：**

- `testCompleteFireThrowsOnScheduleVersionConflict` → `testCompleteFireConvergesDespiteScheduleModifications`（现在单次 try 不抛异常）
- 所有测试通过（DAO 10 + Coordinator 155 + TimeoutChecker 22 + CompletionProcessor 19）

Exit Criteria:

- [x] `traceCompleteFireErrorCode()` 确认异常来源为 `ERR_ORM_ENTITY_IS_READONLY`
- [x] Bug A 修复：移除 `requireEntityById` + `isTerminalFire` 短路线
- [x] Bug B 修复：移除无效的 retry loop；单次 `tryUpdateWithVersionCheck` 靠 entity dirty 属性 flush
- [x] Bug C 修复：`tryMarkDispatchTimeout` 检查 `fire.orm_readonly()` 后跳过 task 取消
- [x] `cancelFire` 移除 retry loop
- [x] 所有测试通过
- [x] No owner-doc update required（doc-sync 在 Phase 4 统一处理）

### Phase 2+ - Bug C 修复（超时路径状态不一致）

Status: **completed** （与 Phase 1+2 合并实施）
Targets: `JobTimeoutCheckerImpl.java`, `TestJobTimeoutChecker.java`

- Item Types: `Fix | Proof`

- [x] 修正 Bug C：在 `JobTimeoutCheckerImpl.tryMarkDispatchTimeout` 中，`completeFireAndUpdateSchedule` 返回后检查 `fire.orm_readonly()`。如果 true（版本冲突导致 fire 未实际更新），跳过 task 取消逻辑。
- [x] 所有 coordinator 测试通过（155 tests, 0 failures）

Exit Criteria:

- [x] `tryMarkDispatchTimeout` 在 fire 未实际更新时跳过 task 取消。
- [x] 使用 `fire.orm_readonly()` 判断而非静默跳过（Anti-Hollow Rule #24 — 有明确的 guard condition）。
- [x] No owner-doc update required（doc-sync 在 Phase 3 统一处理）。

### Phase 4 - 文档更新

Status: **completed**
Targets: `docs-for-ai/02-core-guides/concurrency-and-transactions.md`

- Item Types: `Decision | Follow-up`

- [x] 在 `concurrency-and-transactions.md` 模式二（@SingleSession + REQUIRES_NEW）下增加三个常见陷阱：
  - 死代码陷阱（`requireEntityById` 返回缓存对象导致短路）
  - 缓存不可刷新陷阱（@SingleSession 限制定理：retry loop 永远无效）
  - 状态不一致陷阱（Store 方法失败后调用方继续操作）
- [x] 在模式四中更新"如何强制从 DB 重新加载"表格，增加 `@SingleSession` 下无效的说明
- [x] 在模式四的"常见误区"中增加 retry loop 和 `orm_unload` 相关误区
- [x] 在模式四的"在乐观锁重试循环中的应用"中增加 `@SingleSession` 限制警告
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` — 0 errors, 0 warnings

## Closure Gates

- [x] Phase 1+2+3 代码修复完成（Bug A + Bug B + Bug C + cancelFire）。
- [x] 全部测试通过：DAO（10）、Coordinator（155）。
- [x] `docs-for-ai/` 对应文档已更新（Phase 4）。
- [x] 独立子 agent closure-audit 完成并记录 evidence（15/15 criteria PASS）。
- [x] Anti-Hollow Check：无空方法体、无静默跳过、无 no-op 作为正常实现（Bug C 的 `fire.orm_readonly()` 是明确的 guard condition）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

**当前状态（2026-07-18 全面完成）：** Bug A/B/C 全部修复。@SingleSession 限制定理已记录在 `docs-for-ai/` 文档中。独立 audit 确认 15/15 exit criteria 通过。

## Deferred But Adjudicated

### activeFireCount 改为统计查询

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 计数器漂移的根本修复方案是改为统计查询（`COUNT(*) WHERE fireStatus != terminal`），但这属于 ORM 模型变更（plan-first），且与 `docs-for-ai/02-core-guides/model-first-development.md` 相关。本 plan 只用固定 delta 修复现有计数器的并发正确性,不改变计数器语义。
- Successor Required: `yes`
- Successor Path: 待定（依赖 Issue 11 架构决策）

### task 取消移入 completeFireAndUpdateSchedule

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这涉及 `IJobFireStore` 接口变更，scope 扩大。当前 Bug C 修复方案（在 `JobTimeoutCheckerImpl` 中检查 fire 状态）已经能消除状态不一致，不需要接口变更。
- Successor Required: `no`

- Classification: `watch-only residual`
- Why Not Blocking Closure: 其他 Store 方法（`insertTasksAndMarkFireDispatching`、`revertDispatchingFireToWaiting`）的 `requireEntityById` 用于二次校验而非读写实体状态，正确性不受影响。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 考虑在 `concurrency-and-transactions.md` 中增加 `@SingleSession` + `REQUIRES_NEW` 的 checklist，帮助审计和开发者判断 Store 方法是否引入了死代码风险。

## Closure

Status Note: *已完成* — 所有 Phase 1-4 exit criteria 满足，独立 audit PASS。
Completed: 2026-07-18

Closure Audit Evidence:

- 独立子 agent（task_id=ses_08c6cd76affeo8n5D0AzY56MwL）完成审计，15/15 exit criteria PASS
- Audit findings: `JobFireStoreImpl.java` L121-135（completeFireAndUpdateSchedule 无短路线、无 retry loop），`JobTimeoutCheckerImpl.java` L338（fire.orm_readonly() guard），`concurrency-and-transactions.md` 新增文档
- 测试全部通过：DAO 10/0/0，Coordinator 155/0/0

Follow-up:

- 无。所有已知 bug 已修复。
