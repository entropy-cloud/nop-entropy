# 302 nop-job Completion Processor：指标误触发修复与 Per-Fire 事务改造

> Plan Status: completed
> Last Reviewed: 2026-07-28
> Source: `ai-dev/analysis/2026-07/2026-07-28-nop-job-completion-processor-architectural-analysis.md`
> Related: `301-nop-job-complete-fire-transaction-session-bugs.md`（已关闭，Plan 301 修复了 dead code + retry loop readonly 污染，本 plan 处理其遗留问题）

## Purpose

Plan 301 修复了 `completeFireAndUpdateSchedule` 的三个正确性 bug，但遗留了两个问题：
1. `completeFireAndUpdateSchedule` 返回 `void`，指标/告警在版本冲突后仍误触发（P0）
2. 批处理级 `@SingleSession` 迫使副效应路径使用 `updateEntityDirectly`，版本冲突抛异常（P1）

本 plan 分两个阶段：Phase 1 用最小改动修复 P0/P1；Phase 2 实施 Per-Fire 事务架构，从根本上消除架构缺陷。

## Current Baseline

- `completeFireAndUpdateSchedule`（`JobFireStoreImpl.java:119-137`）返回 `void`。调用方 `tryCompleteFireAndGetStatus` 在方法返回后无条件触发指标/告警（`JobCompletionProcessorImpl.java:208-217`）。双 Coordinator 部署时确定发生误触发。
- `failFireWithoutSchedule`（`JobFireStoreImpl.java:258-268`）使用 `updateEntityDirectly`（未设置 `orm_disableVersionCheckError`），版本冲突时抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND`，被 `scanBatch` 的 catch 捕获。**有两个调用者**：`JobCompletionProcessorImpl` 和 `JobTimeoutCheckerImpl`。
- `updateRetryRecordId`（`JobFireStoreImpl.java:250-256`）使用 `updateEntityDirectly`，但**当前无生产代码调用此方法**（死代码）。
- `scanBatch`（`JobCompletionProcessorImpl.java:117`）标注 `@SingleSession`。所有 Fire 共享一个 ORM Session。ORM 在成功 flush 后已正确递增内存版本号（`incOptimisticLockVersion`）并清除脏标志（`orm_clearDirty`），跨 Fire Schedule 干扰经分析证实不存在。
- `AbstractBatchScanner.doScan()` 无 Session/Transaction 注解，`scanBatch()` 的 `@SingleSession` 在各子类中单独标注。
- `IJobFireStore.completeFireAndUpdateSchedule(fire, schedule): void` — 接口声明返回 `void`。
- `loadFire(String)` 已存在于 `IJobFireStore` / `JobFireStoreImpl`（`JobFireStoreImpl.java:205-207`），使用 `requireEntityById`（不存在时抛异常，不返回 null）。
- 现有测试全部通过：`./mvnw test -pl nop-job-coordinator -am`（Coordinator 155+ tests、DAO 10+ tests）。

## Goals

- **Phase 1**：`IJobFireStore.completeFireAndUpdateSchedule` 返回 `boolean`，调用方仅在写入成功时触发指标/告警。`failFireWithoutSchedule` 改用 `tryUpdateWithVersionCheck` 避免版本冲突抛异常（接口保持 `void`，内部不再抛异常即可）。
- **Phase 2**：Completion Processor 从批处理级 `@SingleSession` 改为 Per-Fire 独立 `@Transactional + @SingleSession`。`failFireWithoutSchedule` 逻辑在 Completion Processor 路径中内联（方法保留在接口中供 `JobTimeoutCheckerImpl` 使用）。`IJobFireStore` 接口对应简化。
- 两阶段各增加回归测试验证 fix 前后的行为差异。

## Non-Goals

- 不修改 `JobScheduleStoreImpl` 的 `updateScheduleWithRetry` 方法（属于 Plan 路径，不受本 plan 影响）。
- 不修改 `JobTimeoutCheckerImpl` 的超时处理逻辑。其 `completeFireAndUpdateSchedule` 调用不受 Phase 1 影响（同样收到返回 boolean，行为不变）。其 `failFireWithoutSchedule` 调用到 Phase 2 仍通过接口方法进行。
- 不修改 `JobPlannerScannerImpl`、`JobDispatcherScannerImpl`（其他 Scanner 使用 `@SingleSession` + `REQUIRES_NEW` 模式是合理的，不在本 plan 范围内）。
- Phase 2 不改变 `cancelFire` 的行为（取消操作不受 completion 架构影响）。
- 不将 Per-Fire 模式应用于 `JobTimeoutCheckerImpl`（留待后续 plan）。

## Scope

### In Scope

- `IJobFireStore.completeFireAndUpdateSchedule()` — return type `void` → `boolean`。
- `JobFireStoreImpl.completeFireAndUpdateSchedule()` — 对应实现变更。
- `JobFireStoreImpl.failFireWithoutSchedule()` — `updateEntityDirectly` → `tryUpdateWithVersionCheck`（接口保持 `void`，仅内部不再抛异常）。
- `JobCompletionProcessorImpl.scanBatch()` — Phase 2 移除 `@SingleSession`。
- `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` — Phase 1 根据 `completeFireAndUpdateSchedule` 的返回值决定是否触发指标/告警。
- `JobCompletionProcessorImpl.completeSingleFire()` — Phase 2 新增方法，替代 `tryCompleteFireAndGetStatus`。
- `TestJobCompletionProcessor.MockFireStore` — 更新以支持可配置的版本冲突响应。
- 各 Test mock 实现（`TestJobCompletionProcessor`、`TestJobTimeoutChecker`、`TestJobE2E` 等）— 同步 `completeFireAndUpdateSchedule` 的 `void` → `boolean` 签名变更。
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md` — Phase 1 更新接口签名文档；Phase 2 增加模式五（Per-Item 独立事务）。
- `ai-dev/design/nop-job/` — 设计文档更新：
  - `01-architecture-baseline.md`: `§7.4（第 282-288 行附近）` completion 流程描述需反映 Per-Fire 事务模式（Phase 2）。
  - `metrics-design.md`: completion metrics 的触发语义需要明确"仅在 DB 写入确认后触发"（Phase 1 语义变更）。
  - `retry-integration-design.md`: `completeFireAndUpdateSchedule` 的引用（第 108 行附近）需同步签名，并在调用重试桥接之前添加 `completeFireAndUpdateSchedule` 返回 `false` 时的条件分支。

### Out Of Scope

- `updateRetryRecordId` 的死代码清理（已从 Phase 1 移除。当前无生产调用者，修改行为有零收益风险。保留当前实现不变，待后续 standalone 清理 plan 处理）。
- `activeFireCount` 改为统计查询的架构决策（由后续 plan 处理）。
- 其他 Store 方法（`cancelFire`、`insertTasksAndMarkFireDispatching` 等）的 Session 相关问题。
- `JobTimeoutCheckerImpl` 迁移到 Per-Fire 模式（留作后续 plan，见 Non-Blocking Follow-ups）。

## Execution Plan

### Phase 1 — 最小修复：completeFireAndUpdateSchedule 返回 boolean + failFireWithoutSchedule 改用 tryUpdateWithVersionCheck

Status: completed (2026-07-28)
Targets: `IJobFireStore.java`, `JobFireStoreImpl.java`, `JobCompletionProcessorImpl.java`, `TestJobFireStoreRace.java`, `TestJobCompletionProcessor.java`, `TestJobTimeoutChecker.java`, `TestJobE2E.java`, `docs-for-ai/02-core-guides/concurrency-and-transactions.md`, `ai-dev/design/nop-job/metrics-design.md`, `ai-dev/design/nop-job/retry-integration-design.md`

- Item Types: `Fix | Proof | Decision`

- [x] **接口变更**: `IJobFireStore.completeFireAndUpdateSchedule()`: return type `void` → `boolean`。同步更新 `JobFireStoreImpl` 实现以及所有 mock（`TestJobCompletionProcessor`、`TestJobTimeoutChecker`、`TestJobE2E`）。
- [x] **实现变更**: `JobFireStoreImpl.completeFireAndUpdateSchedule()`: `tryUpdateWithVersionCheck(fire)` 返回 false 时 return false；schedule 更新失败时 return true（fire 已更新完成，schedule 更新是 best-effort）。已知权衡：schedule 更新失败时返回 true 意味着 Fire 已写入但 schedule 计数器可能短暂不一致（如 `activeFireCount` 未递减），下一次扫描周期会修正。
- [x] **实现变更**: `JobFireStoreImpl.failFireWithoutSchedule()`: `updateEntityDirectly` → `tryUpdateWithVersionCheck`。接口保持 `void`，仅内部不再抛异常。版本冲突时 `tryUpdateWithVersionCheck` 返回 false 静默跳过。
- [x] **正确性证明**: 验证 `failFireWithoutSchedule` 的两个调用者（`JobCompletionProcessorImpl` + `JobTimeoutCheckerImpl`）在方法返回后均不读取 fire 实体状态。结论：`JobTimeoutCheckerImpl` 在调用后仅读取 `fire.getJobFireId()`（标量 PK，总是可用）。`JobCompletionProcessorImpl` 在调用后立即 `return`。两者均安全。
- [x] **正确性证明**: 验证 `fetchRunningFires()` 在无外层 Session 时行为正确。结论：`findAllByQuery` 自动创建临时 Session，返回的实体完全加载（所有标量列），标量 getter 在 Session 关闭后安全，无 lazy 加载依赖。
- [x] **实现变更**: `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()`: 调用 `completeFireAndUpdateSchedule` 后检查返回值。返回 false 时跳过指标/告警，直接 return null。
- [x] **回归测试** → `TestJobCompletionProcessor.java`: 新增 `testCompleteFireVersionConflict_skipsMetrics`。更新 `MockFireStore.completeFireAndUpdateSchedule` 支持可配置版本冲突（`AtomicBoolean simulateConflict`）。验证返回 false 时 `tryCompleteFireAndGetStatus` 不触发指标/告警。
- [x] **回归测试** → `TestJobFireStoreRace.java`: 新增 `testFailFireWithoutSchedule_noThrowOnVersionConflict`，验证 `failFireWithoutSchedule` 在版本冲突时不抛异常。使用真实 DAO 层模拟版本冲突。
- [x] **文档更新**: `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 模式二中 `completeFireAndUpdateSchedule` 签名从 `void` 改为 `boolean`。
- [x] **设计文档更新**: `ai-dev/design/nop-job/metrics-design.md` — completion metrics 触发语义增加"仅在 DB 写入确认后触发"。
- [x] **设计文档更新**: `ai-dev/design/nop-job/retry-integration-design.md` — `completeFireAndUpdateSchedule` 签名同步，并在重试桥接调用序列中添加 `completeFireAndUpdateSchedule` 返回 false 时的条件分支。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IJobFireStore.completeFireAndUpdateSchedule` 返回 `boolean`，所有实现和 mock 已同步。
- [x] `failFireWithoutSchedule` 使用 `tryUpdateWithVersionCheck`，版本冲突时不抛异常。
- [x] `tryCompleteFireAndGetStatus` 在 `completeFireAndUpdateSchedule` 返回 false 时跳过指标/告警。
- [x] 新增回归测试验证版本冲突时指标跳过（`TestJobCompletionProcessor`）。
- [x] 新增回归测试验证 `failFireWithoutSchedule` 版本冲突时不抛异常（`TestJobFireStoreRace`）。
- [x] **接线验证**（Minimum Rules #23）：`tryCompleteFireAndGetStatus` 确实检查 `completeFireAndUpdateSchedule` 的返回值而非忽略。验证通过。
- [x] **端到端验证**（Minimum Rules #22）：从 `fetchRunningFires` → `tryCompleteFireAndGetStatus` → 指标跳过的完整路径。验证通过。
- [x] **无静默跳过**（Minimum Rules #24）：Phase 1 新代码中无空方法体、无 `continue` 绕过、无静默吞异常。
- [x] 所有既有测试通过：`./mvnw test -pl nop-job-coordinator -am`（Coordinator 20+4+22 tests、DAO 11 tests）。
- [x] `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 中 `completeFireAndUpdateSchedule` 接口签名已更新为 `boolean`。
- [x] `ai-dev/design/nop-job/metrics-design.md` 中 completion metrics 触发语义已更新为"仅在 DB 写入确认后触发"。
- [x] `ai-dev/design/nop-job/retry-integration-design.md` 中 `completeFireAndUpdateSchedule` 签名和条件分支已同步。
- [x] 正确性证明已完成：`failFireWithoutSchedule` 的两个调用者在调用后均不读取 fire 引用。
- [x] 正确性证明已完成：`fetchRunningFires` 在无外层 Session 时行为正确。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 — Per-Fire 独立事务改造

Status: completed (2026-07-28)
Targets: `JobCompletionProcessorImpl.java`, `JobFireStoreImpl.java`, `IJobFireStore.java`, `TestJobCompletionProcessor.java`, `TestJobTimeoutChecker.java`, `TestJobE2E.java`, `TestJobConcurrency.java`, `TestJobCoordinatorScanner.java`, `docs-for-ai/02-core-guides/concurrency-and-transactions.md`, `ai-dev/design/nop-job/01-architecture-baseline.md`, `ai-dev/design/nop-job/metrics-design.md`

- Item Types: `Fix | Proof | Decision | Follow-up`

- [x] **架构变更**: `JobCompletionProcessorImpl.scanBatch()`: 移除 `@SingleSession`。循环内调用 `completeSingleFire(fire.getJobFireId())`。
- [x] **新增 API**: `IJobFireStore.getFireById(fireId): NopJobFire` — 返回 null 而非抛异常。`JobFireStoreImpl` + 4 个 mock 实现同步。
- [x] **新方法**: `JobCompletionProcessorImpl.completeSingleFire(String fireId)`: `protected` + `@Transactional(REQUIRES_NEW)` + `@SingleSession`。在新 Session 中加载 fire/tasks/schedule → 计算 → 修改脏字段 → `@Transactional` 提交时自动 flush。`failFireWithoutSchedule` 逻辑内联（schedule == null 时直接 setter）。不再调用 `completeFireAndUpdateSchedule`。Metrics/alarms 在方法内部触发。
- [x] **接口决策**: `completeFireAndUpdateSchedule` **保留**（`JobTimeoutCheckerImpl` 仍使用）。Completion Processor 不再调用此方法。`failFireWithoutSchedule` **保留**（`JobTimeoutCheckerImpl` 使用）。
- [x] **测试改造**: 真 DAO 测试（`TestJobConcurrency`、`TestJobCoordinatorScanner`）改为 `@Inject IJobCompletionProcessor` 获取 AOP 代理（Nop 编译时 AOP 自调用可拦截）。Mock 测试（`TestJobCompletionProcessor`、`TestJobE2E`）保持 `new` 创建，验证 entity 字段。
- [x] **Mock 重构**: `TestJobE2E.SimpleFireStore` 改为单 `fires` Map 模拟 DB 表，`fetchRunningFires` 按 status 过滤。`TestJobCompletionProcessor.MockFireStore` 增加 `firesById` Map。
- [x] **文档更新**: `docs-for-ai/02-core-guides/concurrency-and-transactions.md`: 增加模式五（Per-Item 独立事务）。
- [x] **设计文档更新**: `ai-dev/design/nop-job/01-architecture-baseline.md`: `§7.4` completion 流程描述反映 Per-Fire 事务模式。
- [x] **设计文档更新**: `ai-dev/design/nop-job/metrics-design.md`: 确认无额外变更。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `scanBatch` 无 `@SingleSession`，每个 Fire 在独立 `@Transactional(REQUIRES_NEW)` + `@SingleSession` 中处理。
- [x] `completeSingleFire` 返回 `Integer`（fire status 或 null）。null 表示 fire 不存在或未完成。调用方仅在非 null 时递增 completedCount。
- [x] `completeSingleFire` 的 AOP 注解生效已验证：`protected` 方法，IoC 注入后 AOP 代理拦截（156 tests 通过证明）。
- [x] `completeSingleFire` 使用 `getFireById`（`getEntityById`）加载 Fire，不存在时优雅返回 null。
- [x] `failFireWithoutSchedule` 保留在 `IJobFireStore` 中（供 `JobTimeoutCheckerImpl` 使用），Completion Processor 路径已内联其逻辑。
- [x] `completeFireAndUpdateSchedule` 保留决策已完成：保留（`JobTimeoutCheckerImpl` 仍使用）。
- [x] **接线验证**：`scanBatch` → `completeSingleFire` 调用链在运行时连通（TestJobConcurrency 6 tests + TestJobCoordinatorScanner 23 tests 通过）。
- [x] **端到端验证**：从 `fetchRunningFires` → `completeSingleFire` → 指标触发的完整路径已验证。
- [x] **无静默跳过**：Phase 2 新代码中无空方法体、无 `continue` 绕过、无静默吞异常。
- [x] 所有既有测试通过：`./mvnw clean test -pl nop-job/nop-job-dao,nop-job/nop-job-coordinator -am` → 156 tests, 0 failures。
- [x] `docs-for-ai/02-core-guides/concurrency-and-transactions.md` 已更新，增加模式五。
- [x] `ai-dev/design/nop-job/01-architecture-baseline.md` 中 `§7.4` completion 流程描述已反映 Per-Fire 事务模式。
- [x] `ai-dev/design/nop-job/metrics-design.md` 确认与 Phase 2 行为一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Phase 1 全部 exit criteria 已满足。
- [x] Phase 2 全部 exit criteria 已满足。
- [x] `IJobFireStore` 接口变更确认无受影响的外部模块（仅 `nop-job-coordinator` 和 `nop-job-dao` 内部使用）。
- [x] 受影响的 owner docs 已同步到 live baseline：
      - `docs-for-ai/02-core-guides/concurrency-and-transactions.md`（Phase 1 签名更新 + Phase 2 模式五新增）
      - `ai-dev/design/nop-job/01-architecture-baseline.md`（Phase 2 §7.4 核心流程描述）
      - `ai-dev/design/nop-job/metrics-design.md`（Phase 1 metrics 触发语义）
      - `ai-dev/design/nop-job/retry-integration-design.md`（Phase 1 签名引用 + 条件分支）
- [x] 独立子 agent closure-audit 已完成（session: ses_05755b712ffeh4apAA7MdqexDU，2026-07-28）。
- [x] **Anti-Hollow Check**：closure audit 验证（a）`scanBatch` → `completeSingleFire` 调用链运行时连通，（b）`completeSingleFire` 有 83 行真实方法体，无空方法体/静默跳过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs --strict` — exit 0（0 errors）
- [x] `./mvnw compile -pl nop-job-coordinator -am` — BUILD SUCCESS
- [x] `./mvnw test -pl nop-job-coordinator -am` — 156 tests, 0 failures
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` — No errors found

## Deferred But Adjudicated

### activeFireCount 改为统计查询

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 的 Per-Fire 事务改造通过独立 Session 消除了跨 Fire 干扰（即使旧架构下也不存在确定性问题——`incOptimisticLockVersion` 已保证内存版本正确）。`activeFireCount` 作为计数器的精度问题（特别是 schedule 更新失败时的短暂不一致）需要在更广泛的 Schedule 生命周期范围内评估，不属于 Completion Processor 一个模块的工作。
- Successor Required: `yes`
- Successor Path: 待定（与 Plan 301 遗留的同一 Issue 合并处理）

### `updateRetryRecordId` 死代码清理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前无生产调用者，修改其行为有零收益的回归风险。保持当前 `updateEntityDirectly` 实现不变。待后续死代码清理 plan 处理。
- Successor Required: `yes`
- Successor Path: 待定

### `cancelFire` 的架构对齐

- Classification: `watch-only residual`
- Why Not Blocking Closure: `cancelFire` 在 `JobFireStoreImpl` 中属于独立调用路径（通常由 REST API 或人工触发），不受 Completion Processor 的 Session 管理影响。如果未来发现 `cancelFire` 也有类似的 Session 问题，可单独处理。
- Successor Required: `no`

## Non-Blocking Follow-ups

- Phase 2 完成后评估是否将 Per-Fire 事务模式应用于 `JobTimeoutCheckerImpl`（预计为 plan 303）。
- Phase 1 完成后，评估 `completeFireAndUpdateSchedule` 是否还需要保留为独立方法。如果其逻辑仅在 `completeSingleFire` 中被调用，可考虑内联。
- 上线后监控 completion 相关的 alarm 频率，确认误触发消失。

## Closure

Status Note: Plan 302 完成。Phase 1（completeFireAndUpdateSchedule 返回 boolean + failFireWithoutSchedule 改用 tryUpdateWithVersionCheck）和 Phase 2（Per-Fire 独立事务改造：scanBatch 移除 @SingleSession，新增 completeSingleFire with @Transactional+@SingleSession）均已实施并验证。
Completed: 2026-07-28

Closure Audit Evidence:

独立子 agent closure audit（session: ses_05755b712ffeh4apAA7MdqexDU）验证结果：
- Check 1-12（代码实现）: ALL PASS — 接口签名、实现变更、方法可见性、AOP 注解、调用链、测试改造全部与 live code 一致
- Check 13（Phase 1 exit criteria）: ALL [x]
- Check 14（Phase 2 exit criteria）: ALL [x]
- Anti-Hollow: completeSingleFire 有 83 行真实方法体，scanBatch→completeSingleFire 调用链运行时连通
- 验证命令: check-doc-links exit 0, mvnw compile BUILD SUCCESS, mvnw test 156 tests 0 failures

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
