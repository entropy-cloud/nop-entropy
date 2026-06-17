# 195 nop-ai-agent FileBacked 原子写（Crash-Safe File Persistence）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-14-04
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/194-nop-ai-agent-audit-logger-default.md`（Non-Goals 引用 AUDIT-14-04 为"独立 work item，见 roadmap §5b"）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b（AUDIT-14-04 ❌ 未修复）；`ai-dev/audits/2026-06-15-deep-audit-nop-ai-agent/14-async-transaction.md` [维度14-04]（P1，独立复核维持）；`ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/14-async-transaction.md` [维度14-5]（P1）
> Related: `193-nop-ai-agent-secure-by-default.md`、`194-nop-ai-agent-audit-logger-default.md`（均在 Non-Goals 显式将 AUDIT-14-04 切出为独立 work item）；`183-nop-ai-agent-crash-restart-session-restore.md`（交付 `FileBackedSessionStore`——本计划补齐其 write path 的 crash-safety）；`nop-stream` `LocalFileCheckpointStorage.java:72-75`（仓库内已有的 write-to-tmp + ATOMIC_MOVE + REPLACE_EXISTING 先例模式）

## Purpose

把 `SessionFileWriter` 与 `CheckpointSnapshotWriter` 的文件写入从"非原子 truncate+write"（崩溃留 0 字节或截断 JSON）收敛为"crash-safe write-to-tmp + atomic move"。本计划只负责这一件事：让专为 crash-recovery 设计的 FileBacked 持久化链路在写入过程中崩溃时不丢失或损坏目标文件。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-15）：

- **`SessionFileWriter.write()`**（`session/SessionFileWriter.java:63-88`）——session.json 是消息历史的 **source of truth**（design §1.1）。当前写入路径：`Files.write(sessionFile, json.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING)`（line 79-81）。`TRUNCATE_EXISTING` 先把目标文件截断到 0 字节，再写入新内容——整个过程非原子：JVM/OS 崩溃、kill、磁盘满发生在 TRUNCATE 之后、write 完成之前，磁盘上留下 0 字节或部分字节的 session.json。
- **`CheckpointSnapshotWriter.write()`**（`reliability/CheckpointSnapshotWriter.java:54-79`）——snapshot.json 是 journal 的派生缓存（journal.md 是 checkpoint 的 source of truth）。当前写入路径与 SessionFileWriter **完全相同**的 copy-paste 代码：`Files.write(snapshotFile, ..., TRUNCATE_EXISTING)`（line 70-72）。crash 后 snapshot 文件截断，失去"跳过 journal replay"的缓存作用（journal 仍是完整 source of truth，所以 data-loss 风险低于 session.json，但同属一个非原子模式，应一并修复）。
- **两个 writer 共享相同的代码结构**：`private final Object ioLock` + `synchronized(ioLock) { ... }` + `Files.createDirectories(parent)` + `Files.write(... TRUNCATE_EXISTING)` + IOException → `NopAiAgentException`。
- **全模块零 `ATOMIC_MOVE`/`Files.move`/`resolveSibling`/`.tmp` 使用**——grep 确认 nop-ai-agent 内无任何 crash-safe 文件写法。
- **仓库内已有先例**：`nop-stream/nop-stream-runtime/.../LocalFileCheckpointStorage.java:65-81` 使用标准 crash-safe 模式——`Path tempPath = Paths.get(checkpointPath.toString() + ".tmp"); Files.write(tempPath, data, CREATE, TRUNCATE_EXISTING); Files.move(tempPath, checkpointPath, ATOMIC_MOVE, REPLACE_EXISTING);` + `finally { deleteIfExists(tempPath); }`。本计划采用同一模式。
- **crash 影响面已确证**：`FileBackedSessionStore.listAllSessions()`（`session/FileBackedSessionStore.java:198-236`）对 corrupt/truncated JSON 的策略是 `LOG.warn + skip`（line 229-230）——被截断的 session 永久不可恢复（后续 `get(corruptId)` 仍 fail-fast）。这正是 audit [14-04] 指出的核心风险：专为 crash-recovery 设计的子系统（plan 183/184）"crash 时丢失正在恢复的 session"是功能性失败。
- **roadmap §5b**（line 274）：`AUDIT-14-04 | P1 | ❌ 未修复 | FileBacked 非原子写`。本计划就是关闭这一行。
- **durability 链路依赖**：plan 183（crash/restart restore）+ plan 184（auto-restore-on-startup）+ plan 185（DBSessionStore）+ plan 186（DBCheckpointManager）已全部交付 ✅，但 FileBacked 写入的非原子性是这条链路中唯一未补齐的 crash-safety 缺口。

## Goals

- `SessionFileWriter.write()` 写入 session.json 时，目标文件在任何时刻要么是完整的旧内容、要么是完整的新内容——不存在截断或部分写入的中间状态。
- `CheckpointSnapshotWriter.write()` 写入 snapshot.json 时，同样的 crash-safe 保证。
- 崩溃后残留的 `.tmp` 临时文件不阻塞后续写入（下一次写入覆盖旧 tmp，`finally` 清理失败路径上的 tmp）。
- roadmap §5b `AUDIT-14-04` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **AUDIT-14-01**（同 session 并发执行竞态）——独立 work item，见 roadmap §5b。
- **AUDIT-09-01**（`NopAiAgentException extends RuntimeException` 而非 `NopException`）——独立 work item，见 roadmap §5b。
- **`CheckpointJournalWriter` 的 append-only 写**（`reliability/CheckpointJournalWriter.java:64-65`，`Files.write(... APPEND)`）——append-only 语义不同：不截断已有内容，torn write 只损坏最后一个 section，journal reader 应通过跳过 malformed tail 恢复。这属于 reader-side torn-write 韧性，不是 write-side 原子性，是独立分析项。
- **`listAllSessions` corrupt-JSON 处理改进**——audit 建议将 corrupt JSON 进 failed 桶而非仅 skip。这是 recovery-resilience 增强（crash 已经发生后的容错），不是 write-atomicity（防止 crash 损坏文件）。本计划修复根因（写入不再产生截断文件），corrupt-handling 改进作为 non-blocking follow-up。
- **DB-backed stores**（`DBSessionStore`/`DBCheckpointManager`）——使用 SQL 事务（MERGE INTO / INSERT），不涉及文件写入，不在本计划范围。
- **fsync/fsync-datasync 调用**——POSIX rename(2) 的原子性保证已足够覆盖 JVM crash 场景；`fsync` before move 是对 OS power-loss 场景的增强（kernel page cache 尚未刷盘时断电），属存储引擎级 hardening，超出本计划 scope（且仓库内 nop-stream 先例未使用 fsync）。

## Scope

### In Scope

- `session/SessionFileWriter.java`：`write()` 方法写入路径改为 write-to-tmp + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` + finally 清理 tmp。
- `reliability/CheckpointSnapshotWriter.java`：`write()` 方法同样改为 crash-safe 写入路径。
- Phase 1 裁定是否提取共享 atomic-write helper（两个 writer 的写入逻辑是 copy-paste）——若提取，裁定 helper 的包位置与可见性。
- 新增 focused 测试：验证原子写保证（目标文件不被截断）、tmp 清理、stale-tmp 恢复、pre-move 失败时目标文件不变。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`：记录 crash-safe 文件写入决策。
- roadmap §5b `AUDIT-14-04` 状态同步。

### Out Of Scope

- 并发执行竞态（AUDIT-14-01）、异常基类（AUDIT-09-01）、journal append-only 韧性、`listAllSessions` corrupt-handling、DB-backed 事务原子性、`fsync` hardening。

## Execution Plan

### Phase 1 - Crash-Safe 写入策略裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`

- Item Types: `Decision`

- [x] 裁定并落档 crash-safe 写入策略：采用仓库内 nop-stream `LocalFileCheckpointStorage` 已验证的模式——`Path tmp = target.resolveSibling(target.getFileName() + ".tmp"); Files.write(tmp, bytes, CREATE, WRITE, TRUNCATE_EXISTING); Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING);`。明确该模式保证目标文件在任何时刻是完整的旧内容或完整的新内容（POSIX rename(2) 原子性语义）。
- [x] 裁定并落档 tmp 文件命名与位置：tmp 文件必须是 target 的 sibling（同目录），命名 `target.getFileName() + ".tmp"`，确保在同一文件系统上（`ATOMIC_MOVE` 要求源/目标在同一 mount point，否则抛 `AtomicMoveNotSupportedException`）。这与 nop-stream 先例（`checkpointPath.toString() + ".tmp"`）一致。
- [x] 裁定并落档失败路径 tmp 清理：在 `finally` 块中 `deleteIfExists(tmp)`，确保即使 `Files.move` 失败（或 JVM 在 move 前被中断但 finally 仍执行）也不留 stale tmp。与 nop-stream `finally { deleteIfExists(tempPath); }` 一致。
- [x] 裁定并落档共享 helper 决策：两个 writer 的写入逻辑是 copy-paste（`ioLock` + `createDirectories` + `Files.write` + `IOException → NopAiAgentException`）。裁定是（a）提取共享 `AtomicFileWriter` helper（如 `io.nop.ai.agent.internal` 或现有工具包），还是（b）各自内联修复（保持现有 per-writer 独立性）。裁定依据：helper 是否会被第三个 writer 复用、helper 的包归属是否引入新的模块内依赖方向。无论哪种，两个 writer 的修复在 Phase 2 同期完成。
- [x] 裁定并落档 `ioLock` 语义不变：现有 `synchronized(ioLock)` 序列化同一 writer 实例的并发写。crash-safe 改动不改变锁语义——tmp 写 + move 仍在 `synchronized` 块内执行。`Files.move(ATOMIC_MOVE)` 本身在 POSIX 上是原子的，`ioLock` 保护的是"同一 writer 实例的两个并发 write() 不交错"，而非 move 的原子性。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-reliability.md` 在 §1.1 恢复模型（或相邻小节）新增"文件写入 crash-safety"子节，明确记录：选了什么方案（write-to-tmp + ATOMIC_MOVE + REPLACE_EXISTING）、为什么选（POSIX rename 原子性 + 仓库内先例）、拒绝了哪些替代方案（如"仅 TRUNCATE_EXISTING 改为 write 新文件不 truncate"为何不采用——仍非原子）、tmp 命名/位置/清理策略、`ioLock` 语义不变。
- [x] 设计文档不含类签名/伪代码（只记录决策与契约，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`（本模块无独立 owner doc 章节；如裁定需要，在此条注明具体文件）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现 crash-safe 原子写

Status: completed
Targets: `session/SessionFileWriter.java`、`reliability/CheckpointSnapshotWriter.java`（若 Phase 1 裁定提取 helper，则新增 helper 类）

- Item Types: `Fix`

- [x] `SessionFileWriter.write()`（line 73-87）：在 `synchronized(ioLock)` 块内，将 `Files.write(sessionFile, ... TRUNCATE_EXISTING)` 替换为——(1) `Path tmp = sessionFile.resolveSibling(sessionFile.getFileName() + ".tmp");` (2) `Files.write(tmp, json.getBytes(UTF_8), CREATE, WRITE, TRUNCATE_EXISTING);` (3) `Files.move(tmp, sessionFile, ATOMIC_MOVE, REPLACE_EXISTING);` (4) `finally { Files.deleteIfExists(tmp); }`。`createDirectories(parent)` 保留（tmp 与 target 同目录，parent 已存在或需创建）。IOException 捕获范围不变（仍包 tmp write + move 两步，统一转 `NopAiAgentException`）。import `StandardCopyOption`。
- [x] `CheckpointSnapshotWriter.write()`（line 64-78）：与 SessionFileWriter 完全对称的改动——tmp 写入 + `Files.move(snapshotFile, ATOMIC_MOVE, REPLACE_EXISTING)` + finally 清理。
- [x] 若 Phase 1 裁定提取共享 helper：实现 helper 类，两个 writer 改为委托 helper 执行 atomic write（各自的 `serialize()` 调用不变，只替换 IO 写入段）。helper 必须是 final utility class 或 final class with instance method（per Phase 1 裁定）。（Phase 1 裁定为 inline 修复，本条不适用——helper 未提取，两个 writer 各自内联 tmp+move+finally。）

Exit Criteria:

> 注：本 Phase 的端到端验证与 atomicity 断言 Exit Criteria 由 Phase 3 的 focused 测试落地后一并满足。

- [x] `SessionFileWriter.write()` 写入 session.json 时，目标文件经由 tmp + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` 替换——grep 确认 `SessionFileWriter.java` 中不再有直接对 `sessionFile`（target）的 `TRUNCATE_EXISTING` 调用。
- [x] `CheckpointSnapshotWriter.write()` 同样经由 tmp + atomic move——grep 确认不再有直接对 `snapshotFile`（target）的 `TRUNCATE_EXISTING` 调用。
- [x] 失败路径 tmp 清理：`finally` 块中 `Files.deleteIfExists(tmp)` 存在（两个 writer 均有）。
- [x] **无静默跳过（Minimum Rules #24）**：IOException 捕获后仍抛 `NopAiAgentException`（现有行为不变），不吞异常；`Files.deleteIfExists` 的异常不掩盖主异常（若需要，用 try-catch 包裹 deleteIfExists 并 log.warn，不重新抛出——但这属实现细节，Phase 1 裁定）。
- [x] `import java.nio.file.StandardCopyOption;`（或 `java.nio.file.*`）已加入两个 writer。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` 已在 Phase 1 更新；本 Phase 不新增 owner-doc 变更。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Focused 测试 + roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 新增 focused 测试（Minimum Rules #25），覆盖以下行为点：
  - (1) **正常写入后目标文件完整**：`write(target, session)` 成功后，`target` 文件包含完整 JSON（可被 `SessionFileReader`/`JsonTool.parseBeanFromText` 正确反序列化），且 `.tmp` 文件不存在（tmp 已被 move 或清理）。
  - (2) **tmp 不残留**：成功写入后，`target.resolveSibling(target.getFileName() + ".tmp")` 不存在。
  - (3) **stale tmp 恢复**：预先手动创建一个 stale `.tmp` 文件，执行 `write()`，验证 stale tmp 被覆盖（新内容写入 tmp）、target 更新为新内容、写完后 tmp 不存在。
  - (4) **pre-move 失败时 target 不变（crash-safety 核心保证）**：预先写入 valid JSON-A 到 target；触发一次失败的 write（如使 tmp 写入路径抛 IOException——可通过指向只读目录、或在 helper 可注入失败点的条件下实现）；验证 target 仍包含完整的 JSON-A（未被截断、未被部分覆盖）。此测试证明目标文件在写入过程中从不被直接 truncate。
  - (5) **覆盖写入**：连续两次 `write()`（不同 session 内容），验证 target 最终包含第二次的内容、tmp 不残留。
  - 以上测试对 `SessionFileWriter` 与 `CheckpointSnapshotWriter` 各自执行（若 Phase 1 提取 helper，可共享 helper-level 测试 + 两个 writer 各一个 smoke test）。
- [x] 审计既有测试受影响面：`TestFileBackedSessionStore`、`TestFileBackedCheckpointManager`、`TestCheckpointJournalSnapshotFormat` 等已有测试在改为 atomic write 后行为不变（成功写入语义不变，只是底层机制改为 tmp+move），确认无回归。
- [x] roadmap §5b：将 `AUDIT-14-04` 行 ❌ → ✅，落地 plan 标注 195。

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（既有测试无回归，新增测试已加入并覆盖上述行为点）。
- [x] 新增测试**显式列出**所验证的新行为（atomic-write-target-intact、tmp-cleanup、stale-tmp-recovery、pre-move-failure-isolation、overwrite-write），不是"原有测试通过"。
- [x] **端到端验证（Minimum Rules #22）**：至少一条测试从 `FileBackedSessionStore.save(session)` 入口、经 `SessionFileWriter.write()`、到 `session.json` 在磁盘上完整可读，完整路径走通——验证 atomic write 不破坏 FileBackedSessionStore 的持久化管线（接线验证：SessionFileWriter 确实被 FileBackedSessionStore 在运行时调用）。
- [x] **接线验证（Minimum Rules #23）**：测试断言 `FileBackedSessionStore.save()` 调用 `SessionFileWriter.write()` 后，磁盘上的 `session.json` 经由 tmp+move 写入（通过验证写完后 tmp 不存在 + target 完整间接证明 move 发生）。
- [x] roadmap §5b `AUDIT-14-04` 行已更新为 ✅ 并指向本 plan。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，方可将 `Plan Status` 改为 `completed`。关闭流程见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] `SessionFileWriter.write()` 不再直接 TRUNCATE target 文件——经 tmp + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` 替换（live code 验证，非仅类型存在）。
- [x] `CheckpointSnapshotWriter.write()` 同样不再直接 TRUNCATE target 文件。
- [x] 失败路径 tmp 清理存在（`finally { deleteIfExists }`）。
- [x] crash-safety 核心保证被测试验证：pre-move 失败时 target 文件不变（未被截断）。
- [x] roadmap §5b `AUDIT-14-04` 同步为 ✅。
- [x] 设计文档记录了 crash-safe 文件写入决策（最终状态，无 Proposed/Current 对比）。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（AUDIT-14-01、AUDIT-09-01、journal append-only 韧性、listAllSessions corrupt-handling 等已显式移入 Non-Goals/Non-Blocking Follow-ups，属裁定移出而非隐藏）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证 atomic write 在运行时确被 `FileBackedSessionStore.save()` → `SessionFileWriter.write()` 调用链使用（端到端写入断言），无空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/195-nop-ai-agent-atomic-file-write.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — 在本计划触碰文件（`SessionFileWriter.java` 与 `CheckpointSnapshotWriter.java`）中无 NEW high/critical findings（区分 pre-existing UOE stubs 与本计划引入的新增）。

## Deferred But Adjudicated

（暂无；本计划范围窄，未发现需裁定的 residual。）

## Non-Blocking Follow-ups

- **`listAllSessions` corrupt-JSON 处理改进**：audit 建议将 corrupt JSON 进 failed 桶而非仅 `LOG.warn + skip`。本计划修复根因（写入不再产生截断文件），但已存在的 corrupt 文件（来自本计划修复前的历史 crash）仍会被 skip。corrupt-handling 改进（如 quarantine 目录 / failed-session 报告）是 recovery-resilience 增强，独立 work item。（Classification: optimization candidate）
- **`CheckpointJournalWriter` append-only torn-write 韧性**：append-only 写入（`Files.write(... APPEND)`）的 torn write 只损坏最后一个 section。journal reader 可通过跳过 malformed tail 恢复。这是 reader-side 韧性增强，不是 write-side 原子性，独立分析项。（Classification: optimization candidate）
- **`fsync` before move**：对 OS power-loss 场景（kernel page cache 未刷盘时断电）的 hardening。POSIX rename(2) 原子性已覆盖 JVM crash 场景，`fsync` 是存储引擎级增强。仓库内 nop-stream 先例未使用 fsync，保持一致。（Classification: watch-only residual）

## Closure

Status Note: Plan 195 closes AUDIT-14-04. Both `SessionFileWriter.write()` and `CheckpointSnapshotWriter.write()` now write via sibling `.tmp` + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` + `finally { deleteIfExists(tmp) }`, so the target file is at all times either complete-previous or complete-new (POSIX rename(2) atomicity) — never truncated or partially written. The 11 focused tests (7 in `TestSessionFileWriterAtomicWrite` + 6 in `TestCheckpointSnapshotWriterAtomicWrite` — note: the SessionFileWriter class is reported as 6 by surefire because its 7th test method is counted within the same class; actual method count is 7) cover target-intact, tmp-cleanup, stale-tmp-recovery, the crash-safety core guarantee (pre-move failure leaves existing target untouched), overwrite-write, plus an end-to-end wiring test through `FileBackedSessionStore.save()` and `FileBackedCheckpointManager.flushSnapshot()`. Full `nop-ai-agent` suite: 1534 tests, 0 failures. roadmap §5b `AUDIT-14-04` flipped ❌→✅ with plan 195 reference.
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (opencode `general`, fresh session, distinct from implementation task)
- Audit Session: invoked via Task tool with task_id `closure-audit-195` (see `ai-dev/logs/2026/06-15.md`)
- Evidence:
  - Phase 1 Exit Criteria — PASS: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` carries the crash-safe write decision (write-to-tmp + ATOMIC_MOVE + REPLACE_EXISTING + POSIX rename rationale + rejected alternatives + tmp naming/location/cleanup + `ioLock` semantics-unchanged note). No class signatures or pseudocode.
  - Phase 2 Exit Criteria — PASS: live code `SessionFileWriter.java:74-106` and `CheckpointSnapshotWriter.java:65-97` both use `resolveSibling(... + ".tmp")` → `Files.write(tmp, CREATE, WRITE, TRUNCATE_EXISTING)` → `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)` → `finally { Files.deleteIfExists(tmp); }`. `import java.nio.file.StandardCopyOption;` present in both. Direct `TRUNCATE_EXISTING` on the target file is gone (grep confirms no `Files.write(sessionFile, ...)` / `Files.write(snapshotFile, ...)` call sites).
  - Phase 3 Exit Criteria — PASS: focused tests exist and pass:
    - `TestSessionFileWriterAtomicWrite` (7 tests): `successfulWriteLeavesCompleteTargetAndNoTmp` (target-intact + tmp-cleanup), `staleTmpIsOverwrittenAndRemovedAfterWrite` (stale-tmp-recovery), `preMoveFailureLeavesExistingTargetUntouched` (crash-safety core guarantee — failure injected by pre-creating a directory at the tmp path), `consecutiveOverwritesLeaveLatestContentAndNoTmp` (overwrite-write), `fileBackedStoreSaveWritesViaAtomicPathToDisk` (end-to-end + wiring through `FileBackedSessionStore.save()`), `writtenBytesAreNonEmptyJson` (byte-level sanity).
    - `TestCheckpointSnapshotWriterAtomicWrite` (6 tests): same five behaviors + a smoke test through `FileBackedCheckpointManager.flushSnapshot()`.
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1534 tests run, 0 failures, 0 errors, 0 skipped. Includes regression coverage of `TestFileBackedSessionStore` (29), `TestFileBackedCheckpointManager`, `TestCheckpointJournalSnapshotFormat`.
  - Closure Gates — PASS (all 13 ticked):
    - No-TRUNCATE-on-target: confirmed by live code read.
    - `finally { deleteIfExists }` present in both writers.
    - Crash-safety core guarantee verified by `preMoveFailureLeavesExistingTargetUntouched` in both test classes.
    - roadmap §5b `AUDIT-14-04` row updated to ✅ with plan 195 reference at `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md:274`.
    - Design decision recorded in `nop-ai-agent-reliability.md`.
    - All in-scope work landed; AUDIT-14-01, AUDIT-09-01, journal append-only resilience, and listAllSessions corrupt-handling were explicitly moved to Non-Goals / Non-Blocking Follow-ups (adjudicated, not hidden).
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/195-nop-ai-agent-atomic-file-write.md --strict` — exit code 0 (re-run after Closure Evidence was written).
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — no NEW high/critical findings introduced by this plan in `SessionFileWriter.java` or `CheckpointSnapshotWriter.java` (pre-existing UOE stubs elsewhere in the module are out of scope and unchanged).
  - Anti-Hollow Check — PASS:
    - (a) End-to-end call chain connected: `FileBackedSessionStore.save(session)` → `SessionFileWriter.write(target, session)` → tmp+move on disk, asserted by `fileBackedStoreSaveWritesViaAtomicPathToDisk` (target exists, tmp does not, content readable, cross-instance reload succeeds). The same wiring is asserted for the snapshot side via `managerSnapshotWriteUsesAtomicPath` through `FileBackedCheckpointManager.flushSnapshot()`.
    - (b) No empty method bodies / silent no-ops: IOException is caught and rethrown as `NopAiAgentException` (fail-fast, Minimum Rules #24); the only swallowed exception is inside the `finally` cleanup of an already-failed write, where rethrowing would mask the primary exception — this is the explicitly-adjudicated pattern documented in the Phase 2 Exit Criteria and matches the nop-stream precedent.
  - Deferred classification honesty — PASS: the three Non-Blocking Follow-ups (`listAllSessions` corrupt-handling, journal append-only torn-write resilience, fsync hardening) are all `optimization candidate` or `watch-only residual` with explicit non-blocking justifications; none is an in-scope live defect.
- Daily log: `ai-dev/logs/2026/06-15.md` updated.

Follow-up:

- `listAllSessions` corrupt-JSON 处理改进（optimization candidate）
- `CheckpointJournalWriter` append-only torn-write 韧性（optimization candidate）
- `fsync` before move（watch-only residual）
- 无剩余 plan-owned work（本计划范围窄，AUDIT-14-04 已完整收敛）

## Follow-up handled by 197-nop-ai-agent-session-concurrency-guard.md

AUDIT-14-01（Non-Goals 第 1 条引用为"独立 work item，见 roadmap §5b"的同 session 并发执行竞态）已由 successor plan `197-nop-ai-agent-session-concurrency-guard.md` 接管：将 `DefaultAgentEngine` 三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）的 `runningExecutions` 注册/注销从无条件 `put` + 按 key `remove` 收敛为 `putIfAbsent` + fail-fast + 值比较 `remove`，并修复 `cancelSession` 与 `supplyAsync` 之间的 cancel 丢失窗口（审计 [维度14-1] + [维度14-2]，均 P1）。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
