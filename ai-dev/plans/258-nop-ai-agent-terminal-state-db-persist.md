# 258 nop-task 终态 step-state DB 持久化（succeed/FAILED driver 后追加 saveStepState——cross-process resume 可观测终态）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: dao-task-state-store-terminal-state-persistence (carry-over, L4)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/257-nop-ai-agent-task-step-continuation-skip-reader.md`（§Closure Follow-up line 214「DaoTaskStateStore 生产路径终态持久化：execution-flow saveStepState 在 ACTIVE 时调用，终态 driver 后需额外 save 才能持久化终态 snapshot 到 DB——当前 in-memory reference 语义已足够 reader E2E；DB snapshot 终态持久化为 production deployment follow-up」）。plan 257 已 `completed`；依 Minimum Rules #20 不回写其历史正文，traceability 经本 Source 字段 + plan 257 新增 `## Follow-up handled by` 段建立。
> Related: `252`（succeed-driver retry-sync）/`253`（succeed-driver retry-async + 非 retry）/`254`（FAILED-driver + cancel-check）/`257`（continuation-skip reader + DaoTaskStateStore save/load 真实实现）

## Purpose

把 plan 257 交付的 continuation-skip reader 从「经 in-memory reference 语义通过 E2E」收敛为「经 DB-backed 持久化在生产环境可观测终态」。plan 257 的 succeed/FAILED 终态 driver（plans 252-254）在 choke-point wiring 后只原地 mutate 内存 `TaskStepStateBean`（stepStatus → COMPLETED/FAILED），从不 re-save 终态 snapshot 到 DB——唯一的 production `saveStepState` 调用点（`TaskStepRuntimeImpl`，ACTIVE 创建时）写出的 DB 行停留在 ACTIVE。结果：cross-process / crash-restart resume 时 `DaoTaskStateStore.loadStepState` 从 DB 取回 ACTIVE snapshot（stale）→ reader `isDone()==false` → 不跳过 → step 被重新执行，plan 257 reader 在生产环境空转。本计划在终态 driver 后追加 `saveStepState()` 持久化 COMPLETED/FAILED 终态 snapshot，使 DB-backed resume 可观测终态。

## Current Baseline

基于上一会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **DaoTaskStateStore save/load 已为真实实现（plan 257 Phase 1 交付，非 stub）**：`DaoTaskStateStore.saveStepState`（`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java`，经 `NopTaskStepInstance` entity 持久化 stepStatus/resultValue[JSON]/exception[errCode+errMsg]）+ `loadStepState`（经 QueryBean 查询 (taskInstanceId, stepPath) 重构 `TaskStepStateBean`）。
- **唯一 production saveStepState 调用点在 ACTIVE 时执行（本计划核心缺口）**：`TaskStepRuntimeImpl`（`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskStepRuntimeImpl.java`，约 :84）调 `stateStore.saveStepState(this)`，发生在 step runtime ACTIVE 创建时。此 save 写出的 DB 行 stepStatus=ACTIVE。终态 driver 之后无追加 save。
- **终态 driver 原地 mutate 内存 bean，不 re-save（本计划核心缺口）**：succeed-driver（`TaskStepStateBean.succeed(...)` → stepStatus=COMPLETED，plans 252/253）+ FAILED-driver（`TaskStepExecution.executeWithParentRt` 错误分支 choke-point，plan 254）在 ACTIVE-time save 之后才设置终态 stepStatus，只 mutate 内存对象，无追加 saveStepState。DB 行不随之更新。
- **plan 257 reader 经 in-memory reference 语义通过 E2E（掩盖生产 gap）**：plan 257 Phase 3 的 `ResumeCapableTaskStateStore` 持有同一 `TaskStepStateBean` 对象引用，driver 原地 mutate 后 resume load 取回同一被 mutate 对象 → isDone true → skip。但生产 `DaoTaskStateStore` 经 DB round-trip（非引用），resume load 取回 ACTIVE snapshot → isDone false → 不跳过。此为 plan 257 显式 carry-over 的 production deployment blocker。
- **plan 257 reader 已 wired（不改）**：`TaskStepExecution.executeWithParentRt`（约 :192-222）step body 之前检查 `state.isDone()`；COMPLETED→return `state.result()`，FAILED→rethrow `state.exception()`。本计划不改 reader，只补其 DB-side 终态前置（终态持久化）。

## Goals

- **终态 step-state 持久化到 DB（COMPLETED/FAILED）**：succeed/FAILED 终态 driver 设置终态 stepStatus 后，追加 `saveStepState()` 使 DB snapshot 反映终态（非 ACTIVE）。
- **DB-backed resume 可观测终态**：cross-process / crash-restart resume（新 runtime、从 DB 重新 load，非 in-memory 引用）经 `loadStepState` 取回终态 snapshot → reader `isDone()==true` → 跳过 step body / 重抛 exception。
- **端到端 DB-backed 验证**：E2E 从 execute（step 完成 → 终态 driver → 终态持久化到 DB）→ fresh DB load（snapshot，非引用）→ reader 命中 isDone → skip/rethrow 完整路径连通。
- **零回归**：plans 252-257 状态机 write-side + reader + nop-task-core/ext + nop-ai-agent 全绿。

## Non-Goals

- **EXPIRED/KILLED 终态 status 的 driver**：out-of-scope improvement（plans 254-257 carry-over，4×）。本计划持久化 succeed/FAILED driver 已设置的终态，不新增 EXPIRED(50)/KILLED(70) driver。Classification: out-of-scope improvement。
- **跨重启 exception 持久化 transient 优化**：optimization candidate（plans 247/252/254 carry-over）。本计划持久化 FAILED 终态含 exception 字段（plan 257 已实现 exception 持久化 errCode+errMsg），不优化序列化细节。Classification: optimization candidate。
- **完整 task-level resume 编排**：successor plan required（plan 257 carry-over，task 级 mainStep 状态语义 / 跨 step flow 位置恢复，独立结果面）。本计划仅交付 leaf step 级终态 DB 持久化。Classification: successor plan required。
- **step-state 全量字段持久化 / 完整历史 entity 模型**：optimization candidate（plan 257 carry-over）。本计划持久化 plan 257 已定义的 reader 依赖字段（stepStatus/resultValue/exception），不扩展字段集。Classification: optimization candidate。
- **fail() 行为变更**：rejected（plans 247/252-254 design decision，`fail()` 仅保存 exception 不设终态 status）。Classification: rejected。

## Scope

### In Scope

- **终态 saveStepState wiring**（Phase 1）：在 succeed/FAILED 终态 driver 设置终态 stepStatus 后追加 `saveStepState()`，使 DB snapshot 反映 COMPLETED/FAILED 终态。
- **DB-backed resume E2E**（Phase 2）：execute → 终态持久化 → fresh DB load（snapshot）→ reader 命中 → skip/rethrow 端到端验证 + 零回归。

### Out Of Scope

- 见 Non-Goals（EXPIRED/KILLED driver / exception transient 优化 / task-level resume 编排 / 全量字段持久化 / fail() 变更 均为显式 rejected / out-of-scope / successor / optimization）。

### 设计裁定（Pre-Adjudicated）

1. **终态 save 须在 driver 设置终态之后、执行返回之前**。saveStepState 调用须在 succeed/FAILED driver 已将 stepStatus 设为 COMPLETED/FAILED 之后，且在 step 执行返回之前。具体 wiring 点（单一 choke-point 复用 / 各 driver 点分别 save / `executeWithParentRt` 终态统一出口）属 execution 裁定（Minimum Rules #10）。前提：覆盖 COMPLETED（succeed-driver：retry-sync / retry-async / 非 retry step）+ FAILED（FAILED-driver 错误分支）两条终态路径。
2. **终态 save 须为幂等 upsert（ACTIVE 行已存在）**。ACTIVE-time save（`TaskStepRuntimeImpl` 约 :84）已写入 DB 行；终态 save 须 upsert 同一行（按 taskInstanceId + stepPath 主键），非 insert 新行，避免重复行。是否复用同一 `saveStepState`（upsert 语义）或新增专用方法属 execution 裁定。
3. **终态 save 须条件于 isDone()==true**。仅当 state 已转入终态（COMPLETED/FAILED）才执行终态 save，避免持久化非终态中间状态（ACTIVE/SUSPENDED 等）。
4. **E2E 须用 snapshot store（非 in-memory 引用）以暴露 pre-fix gap**。plan 257 的 `ResumeCapableTaskStateStore` 持有对象引用，driver mutate 后 load 直接可见——会掩盖本计划要修的 gap（DB round-trip 丢失 mutate）。Phase 2 E2E 须使用经序列化/深拷贝 round-trip 的 store（或真实 `DaoTaskStateStore` + 测试 DB），使 load 取回的是 save 时刻的 snapshot（非 driver mutate 后的引用）。此为本计划 Anti-Hollow 的硬前置：pre-fix 此 E2E 应失败（load 返回 ACTIVE）、post-fix 应通过（load 返回终态）。
5. **reader 不改**：plan 257 reader 已正确实现（isDone 检查 + COMPLETED skip + FAILED rethrow）。本计划只补其 DB-side 终态前置（终态 saveStepState），不修改 reader 本身。

## Execution Plan

### Phase 1 - 终态 saveStepState wiring + 单元测试

Status: completed
Targets: 终态 driver 出口（succeed-driver `TaskStepStateBean.succeed(...)` 调用路径 + FAILED-driver `TaskStepExecution.executeWithParentRt` 错误分支）、`TaskStepRuntimeImpl`（ACTIVE-time save 调用点约 :84）、`DaoTaskStateStore.saveStepState`、`ITaskStateStore` 接口

- Item Types: `Fix`（终态 driver 后 DB snapshot 停留 ACTIVE——confirmed production gap，plan 257 reader 在生产环境空转）

- [x] 核对终态 driver 的所有执行出口：succeed-driver（COMPLETED：retry-sync / retry-async / 非 retry step，plans 252/253）+ FAILED-driver（`executeWithParentRt` 错误分支，plan 254）设置终态 stepStatus 的全部代码路径
- [x] 在终态 driver 设置终态 stepStatus 后追加 `saveStepState()`（条件于 isDone()==true，幂等 upsert ACTIVE 行；设计裁定 1/2/3）。具体 wiring 点属 execution 裁定
- [x] 新增单元测试：succeed-driver 后 DB snapshot stepStatus==COMPLETED（非 ACTIVE）——经 snapshot/序列化 store round-trip 可复核
- [x] 新增单元测试：FAILED-driver 后 DB snapshot stepStatus==FAILED（非 ACTIVE）+ exception 持久化
- [x] 确认 ACTIVE-time save（`TaskStepRuntimeImpl` 约 :84）仍正常（fresh step 首次 ACTIVE 行写入不受影响）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] succeed-driver 后 DB snapshot 反映 COMPLETED 终态（经 snapshot store round-trip 断言 stepStatus==COMPLETED，非 ACTIVE）
- [x] FAILED-driver 后 DB snapshot 反映 FAILED 终态 + exception（非 ACTIVE）
- [x] **接线验证**（#23）：终态 saveStepState 在 runtime 被 driver 出口调用（读码/测试可复核：driver 后 stateStore.saveStepState 被调用）
- [x] **无静默跳过**（#24）：终态 save 为真实实现（非空方法体 / 非条件恒 false 跳过 / 非 TODO）
- [x] **幂等性**（设计裁定 2）：终态 save upsert 同一行（按 taskInstanceId + stepPath），无重复行
- [x] 新增功能各有 focused 测试覆盖（#25）：COMPLETED 终态持久化 + FAILED 终态持久化 各有断言
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao -am` 既有（plan 257 load + plans 252-256 状态机）+ 新增测试全绿
- [x] owner-doc 裁定：`No owner-doc update required`（内部 DB 持久化 wiring，step public 执行契约不变）——Phase 2 复核；或若 public resume 契约变化则更新 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DB-backed resume E2E + 零回归收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/`（新增 DB-backed resume E2E，使用 snapshot/序列化 store 或真实 DaoTaskStateStore）、`.task.xml` fixture（`db-terminal-state-resume`）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 新增 DB-backed resume E2E（设计裁定 4，snapshot store 非 in-memory 引用）：execute（step 完成 → 终态 driver → 终态持久化到 DB snapshot）→ fresh load（从 snapshot 反序列化，非引用）→ reader 命中 isDone → step body 不重调用 → 返回缓存 result
- [x] 新增 DB-backed resume E2E（FAILED 变体）：execute（终态 FAILED → persist）→ fresh DB load → reader 命中 → 重抛 exception
- [x] 验证 pre-fix gap 真实存在：snapshot store resume 在无终态 save 时 load 返回 ACTIVE（isDone false → step 重执行）——可经临时移除终态 save 或独立断言证明此行为在本计划前不成立
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] DB-backed resume E2E 全绿（COMPLETED）：execute→终态 driver→终态 DB 持久化→fresh snapshot load→reader 命中→step body 不重调用→返回缓存 result——**此行为在本计划前不成立**（DB 停留 ACTIVE）
- [x] DB-backed resume E2E 全绿（FAILED）：fresh DB load 返回 FAILED 终态 → reader 重抛 exception（非静默跳过，#24）
- [x] **端到端验证**（#22）：从 task execute 入口 → step 完成 → 终态 driver → 终态 saveStepState 持久化到 DB → fresh load（snapshot）→ reader 检查 isDone → 跳过/重抛 完整路径连通
- [x] **接线验证**（#23）：终态 saveStepState 在 E2E runtime 路径被调用（snapshot store save 计数可观测：ACTIVE 1 次 + 终态 1 次）
- [x] **Anti-Hollow Check**（#22/#23/#24）：E2E 经 snapshot/序列化 round-trip（非 in-memory 引用），证明终态 mutate 经 DB 持久化存活（非引用直接可见）；无空方法体/静默跳过
- [x] 新增功能各有 focused 测试覆盖（#25）：DB-backed COMPLETED resume + FAILED resume 各有 E2E 断言
- [x] 零回归：plans 252-257 状态机 + reader + decorator/bizFatal/reliability + nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required` 或更新 `docs-for-ai/` 若 resume public 契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 终态 driver 后 DB snapshot 反映 COMPLETED/FAILED 终态（非 ACTIVE），经 snapshot round-trip 可复核
- [x] 终态 saveStepState 为 runtime-live 接线（driver 出口真实调用，非空壳）
- [x] DB-backed resume（fresh load 非 in-memory 引用）可观测终态 → reader 命中 isDone → skip/rethrow
- [x] E2E 经 snapshot/序列化 round-trip 闭合 plan 257 carry-over 的 production deployment gap（#22）
- [x] 必要 focused verification 已完成（终态持久化单元 + DB-backed resume E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（EXPIRED/KILLED driver / exception transient / task-level resume / 全量字段持久化 / fail() 变更 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）终态 saveStepState 在 runtime 被 driver 出口调用，（b）DB-backed resume 经 snapshot round-trip 可观测终态（非 in-memory 引用），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / successor plan required / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- EXPIRED/KILLED 终态 status 的 driver（out-of-scope improvement，4× carry-over）。本计划 reader 检查 isDone 含 EXPIRED/KILLED 终态集，但无 driver 设置这两个 status。
- 完整 task-level resume 编排（successor plan required，task 级 mainStep 状态语义 / 跨 step flow 位置恢复）。

## Closure

Status Note: 终态 saveStepState wiring 闭合 plan 257 production deployment gap。succeed/FAILED 终态 driver 设置终态 stepStatus 后追加 `saveTerminalStateIfDone(stepRt)`（条件 isDone==true，幂等 upsert），使 DB snapshot 反映 COMPLETED/FAILED 终态（非停留 ACTIVE）。DB-backed resume（snapshot round-trip，非 in-memory 引用）经 plan 257 reader 命中 isDone → 跳过 step body / 重抛 exception。fresh step 行为零变更（ACTIVE-time save 不变）。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（glm-5.2，read-only explore agent，独立 task_id `ses_123e67426ffe0xxMi652hqbw1l`）
- Audit Session: `ses_123e67426ffe0xxMi652hqbw1l`（fresh session，不复用实现阶段 task session）
- Evidence:
  - Phase 1 Exit Criteria — 全 PASS：
    - succeed-driver 后 snapshot COMPLETED：`TestTerminalStatePersistence.succeedDriver_snapshotReflectsCompleted_notActive`（snapshot stepStatus==COMPLETED，非 ACTIVE）+ `getSaveCount==2`
    - FAILED-driver 后 snapshot FAILED + exception：`TestTerminalStatePersistence.failedDriver_snapshotReflectsFailed_notActive_withException`
    - 接线验证（#23）：`TaskStepExecution.java:309/277/336` `saveTerminalStateIfDone(stepRt)` 在 3 个终态 driver 出口调用；save count = ACTIVE 1 + 终态 1 = 2
    - 无静默跳过（#24）：`saveTerminalStateIfDone`（`:362-367`）真实调 `stepRt.saveState()`（非空方法体/非 TODO）；FAILED reader（`:210-221`）真实重抛 exception
    - 幂等性（设计裁定 2）：`DaoTaskStateStore.saveStepState:149-164` findStepEntity → update/save upsert 同一行（taskInstanceId+stepPath）
    - ACTIVE-time save 不变：`TaskStepExecution.java:237` `stepRt.saveState()` 仍在；`TestTerminalStatePersistence.activeTimeSave_firstSnapshotIsActive_freshStepWriteUnchanged`（第 0 次 snapshot==ACTIVE）
  - Phase 2 Exit Criteria — 全 PASS：
    - DB-backed resume E2E COMPLETED：`TestDbBackedTerminalStateResume.resume_completedStep_snapshotLoadSkipsBody_returnsCachedResult_plan258`（resume counter==0，step body 不重调用）
    - DB-backed resume E2E FAILED：`TestDbBackedTerminalStateResume.resume_failedStep_snapshotLoadRethrowsException_plan258`（resume 重抛 exception，counter==0）
    - 端到端验证（#22）：execute→终态 driver→终态 saveStepState→fresh snapshot load→reader isDone→skip/rethrow 完整路径连通
    - Anti-Hollow（#22/#23/#24）：`SnapshotResumeTaskStateStore.copySnapshot` 深拷贝（非 in-memory 引用），证明终态 mutate 经持久化存活；pre-fix gap 证明（`preFixGap_activeSnapshotBeforeTerminal...`：snapshot[0]=ACTIVE isDone false，snapshot[1]=COMPLETED isDone true）
  - Closure Gates — 全 PASS（逐条经 audit item A-F 复核）：
    - 终态 driver 后 DB snapshot 反映终态：PASS（`TaskStepExecution.java` 3 处 driver + snapshot 测试断言）
    - 终态 saveStepState runtime-live 接线：PASS（`saveTerminalStateIfDone` 真实调用，audit trace 从 step.execute 经 success/error 分支均到达 save）
    - DB-backed resume snapshot 可观测终态：PASS（`TestDbBackedTerminalStateResume` 3 tests）
    - 闭合 plan 257 production gap：PASS（snapshot round-trip vs plan 257 引用语义对照）
    - 无静默降级 in-scope defect：PASS（EXPIRED/KILLED/exception transient/task-level resume/全量字段/fail() 均为显式 Non-Goals，无 in-scope 项降级）
    - owner-doc：`No owner-doc update required`（内部 DB 持久化 wiring，step public 执行契约不变）
    - Anti-Hollow Check：PASS（audit trace 3 个终态 driver 出口均接 save；snapshot 深拷贝 round-trip；无空方法体/静默跳过/no-op）
  - 构建验证：
    - `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am` → BUILD SUCCESS
    - `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（nop-task-core 68 / nop-task-ext 52 / nop-ai-agent 2714，全 0 failures / 0 errors）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/258-nop-ai-agent-terminal-state-db-persist.md --strict` 退出码为 0
  - Deferred 项分类检查：本计划无 deferred 项；Non-Goals 均为显式 rejected/out-of-scope/successor/optimization，无 in-scope live defect 被降级

Follow-up:

- EXPIRED/KILLED 终态 status 的 driver（out-of-scope improvement，4× carry-over）：reader 检查 isDone 含 EXPIRED/KILLED 终态集，但无 driver 设置这两个 status。
- 完整 task-level resume 编排（successor plan required，task 级 mainStep 状态语义 / 跨 step flow 位置恢复）。
- 无剩余 plan-owned work（本 plan in-scope 项全部 landed）。
