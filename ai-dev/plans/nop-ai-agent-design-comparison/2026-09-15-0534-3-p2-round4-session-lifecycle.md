---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-SESSION
group: "2026-09-15-0534"
verify: [test]
---

# P2 round-4 session 生命周期与租约契约修复（锁前就地变更 / release-lock 先于 final save / wakeSession 租户上下文）

## Current Baseline

- 来源：deep-audit round 4 登记 P2 Follow-up Backlog 中 3 项未勾选的 session 生命周期缺陷（roadmap `## Follow-up Backlog` 文档顺序前 3 项），全部经 live repo 复核（HEAD `51544255b8`，2026-09-15，`AgentSessionLifecycle.java` 960 行 / `DefaultAgentEngine.java` 1045 行）：
  1. **`AgentSessionLifecycle` resumeSession/wakeSession 在 tryAcquire 之前就地变更共享 live session**：`resumeSession` 在 :245-263 先 `denialLedger.reset`+`postDenialGuard.reset`+`session.setStatus(running)`，:301 才 `tryAcquire`；`wakeSession` 在 :414 `session.setStatus(running)`，:435 才 `tryAcquire`——锁被他人持有时异常传播但内存态已改：cached 会话卡死为 running（后续重试恒报 "not paused/waiting"）+ 暂停证据已被清除而 session 并未真正恢复；`InMemorySessionStore`（`../../../nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/InMemorySessionStore.java` :29）/`FileBackedSessionStore`（`../../../nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java` :132-141）均返回缓存活对象。
  2. **四条执行路径均在最终 `sessionStore.save()` 之前释放 takeover 租约**：`DefaultAgentEngine.doExecute`（releaseLock :906 vs save :925）、`AgentSessionLifecycle.resumeSession`（:367 vs :380）、`wakeSession`（:481 vs :489）、`restoreSession`（:760 vs :771）——该窗口内 JVM 崩溃/save 抛错（磁盘满）→ 持久化状态过期 + 会话已解锁，另一实例可从过期状态 resume 重复执行同一用户 turn（工具副作用重复）；终态结果在 checkpoint 移除后未落盘。
  3. **`wakeSession` 不重建租户上下文**：与 `resumeSession`（:240-242 捕获 `sessionTenantId` 并在 worker 线程恢复）和 `restoreSession`（显式 set(null) 有注释理由）不同，`wakeSession`（:395-499）同步阶段与 worker lambda 均不设 tenant，租户作用域 DB 操作（denial ledger/session store）以 null tenant 运行（跨租户可见）且无任何注释说明。
- 归属模块：nop-ai-agent（全部 3 项，同一生命周期实现类簇）。
- 验证面：运行时行为修复，涉及 `./mvnw test -pl nop-ai/nop-ai-agent -am`；owner doc `nop-ai-agent-reliability.md`（§13 WAIT_FOR/wakeSession 语义）+ `nop-ai-agent-session-and-storage.md`（§11 并发控制/租约语义）需同步。

## Goals

- 锁获取（tryAcquire 成功）之前不再就地变更共享 live session——失败路径不污染内存态，cached 会话不卡死为 running、暂停证据不丢失。
- 四条执行路径（doExecute/resume/wake/restore）的终态 `sessionStore.save()` 全部先于 takeover 租约释放——无"已解锁但状态过期"窗口，崩溃/保存失败不会遗留可重复执行的过期解锁会话。
- `wakeSession` 与 resumeSession 一致的租户上下文语义（同步阶段 + worker lambda），或显式记录与 resume/restore 不同的理由（不能无注释地以 null tenant 运行租户作用域 DB 操作）。
- 每项配套回归测试（正确结果断言，非仅"不报错"）；owner docs 同步；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不改 takeover 锁本身的 CAS/lease 语义（`DbSessionTakeoverLock`）、不改 `restoreSession` 既有 tenant 语义（显式 set(null) 有理由则保留）。
- 不处置本轮 roadmap 其余未勾选项（round-3 P2 docs/tests、round-4 其余 P2/P3 项，另开计划）。
- 不引入新的存储后端或锁实现；不改 `IWaitCoordinator`/wake 触发语义。
- 不运行 mvn 全量构建。

## Phase 1 — resumeSession/wakeSession：tryAcquire 成功前不就地变更 live session

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java`、对应测试类

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 变更时序裁定：(A) 把 `denialLedger.reset`/`postDenialGuard.reset`/`session.setStatus(running)` 整体后移到 tryAcquire 成功（含 putIfAbsent 成功）之后，失败路径零内存变更；或 (B) 失败路径回滚内存态（reset 后重设、status 还原）——记录理由与备选；推荐 (A)（无回滚逻辑、无中间态窗口），需核对事件发布（SESSION_RESUMED/SESSION_WOKE）顺序是否受影响。
- [x] `Fix` 按裁定落地 `resumeSession`（:245-301 段）：锁获取成功后再执行 ledger/guard reset + status 置 running；tryAcquire 失败/putIfAbsent 失败路径不产生任何内存变更（含不发布误导性事件）。
- [x] `Fix` 按裁定落地 `wakeSession`（:395-435 段）：`deliverWake` 与 `session.setStatus(running)` 后移到 tryAcquire 成功之后（或明确 `deliverWake` 无内存副作用则保留原位置但 status 变更后移），失败路径零内存变更。
- [x] `Fix` 回归测试：InMemory/FileBacked session store 下，tryAcquire 被他人持有（或锁表被占）时调用 resumeSession/wakeSession——断言 (a) 抛错（fail-fast 语义不变）、(b) session 内存态保持 paused/waiting（未卡死 running）、(c) denial 证据未被清除（reset 未发生）；锁释放后重试可正常恢复。
- [x] `Proof` 复核：全部分支路径（成功/失败/租约丢失）状态变更点与锁获取点的先后关系逐条核对；事件发布点（SESSION_RESUMED/SESSION_WOKE）不在失败路径。

Exit Criteria:

- [x] tryAcquire 成功前无任何共享 live session 就地变更（成功/失败路径对照测试为证）。
- [x] 失败路径后 cached 会话状态保持 paused/waiting、denial 证据完整（回归测试断言具体状态而非仅"抛错"）。
- [x] **端到端验证**：经 `IAgentEngine.resumeSession/wakeSession` 公开入口 → tryAcquire 失败 → 状态保持 → 锁释放后重试成功的完整链路。
- [x] **接线验证**：内存态变更点确实被 tryAcquire 结果门控（代码审查 + 失败路径测试）。
- [x] **无静默跳过**：失败路径 fail-fast 抛错（错误码语义不变），不吞异常、不静默返回。
- [x] owner doc 更新：`nop-ai-agent-reliability.md` §13（resume/wake 前置条件与状态变更时序）或 `nop-ai-agent-session-and-storage.md` §11 并发控制段落同步（以实际落点为准，明确记录）。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — 四条执行路径：终态 save 先于租约释放

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`、对应测试类

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 释放顺序裁定：(A) 四条路径统一改为「先 `sessionStore.save(session)` 后 `releaseLockQuietly`」（成功路径），异常路径（save 抛错）保留 release（避免永久持锁）+ 显式错误传播（save 失败不能让调用方看到"成功但未持久化"）；或 (B) 先 save 后 release 且 save 失败时 lease 保留至 TTL 过期（记录理由与备选；推荐 (A)，与现路径 2/3 的 `finally` release 结构对齐，错误路径语义不变）。
- [x] `Fix` `DefaultAgentEngine.doExecute`：终态 `sessionStore.save`（:925）移到 releaseLock（:906）之前；异常路径 release 保持。
- [x] `Fix` `AgentSessionLifecycle.resumeSession`（:367 vs :380）、`wakeSession`（:481 vs :489）、`restoreSession`（:760 vs :771）：成功路径先 save 后 release；异常路径（save 抛错）显式抛错且 release（或按裁定）。
- [x] `Fix` 回归测试：注入 save 抛错（磁盘满模拟）——断言 (a) 调用方收到错误（不静默成功）、(b) 锁仍被释放（无永久持锁）、(c) 无"会话已解锁但状态未落盘"残留（save 失败后 session 状态可查证）；成功路径断言 save 先于 release（如测试桩记录调用顺序）。
- [x] `Proof` 复核：四条路径 release 与 save 的代码顺序逐条核对（grep + 读码），`checkpointManager.remove` 与 save 的相对位置复核（终态 checkpoint 清理不应早于持久化）。

Exit Criteria:

- [x] 四条路径成功分支均为 save → release 顺序（代码审查 + 顺序断言测试为证）。
- [x] save 失败路径显式报错 + 锁仍释放（无永久持锁、无静默成功）——回归测试断言错误传播。
- [x] **端到端验证**：经 doExecute/resume/wake/restore 四入口 → save → release → 结果返回完整链路（测试桩记录顺序）。
- [x] **接线验证**：save 与 release 在同一事务/方法路径上被顺序调用（非孤立单测）。
- [x] **无静默跳过**：save 失败不吞异常（错误码传播），无空 catch。
- [x] owner doc 更新：`nop-ai-agent-reliability.md` §13 或 `nop-ai-agent-session-and-storage.md` §11 同步 save/release 顺序契约（以实际落点为准）。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — wakeSession 租户上下文重建

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java`、对应测试类

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 租户语义裁定：(A) `wakeSession` 与 `resumeSession` 对齐——捕获 `session.getTenantId()` 并在同步阶段 + worker lambda 恢复（`ThreadLocalTenantResolver.set/clear` 对称）；或 (B) 显式记录与 resume/restore 不同的理由（如 wake 的 DB 操作面确为全局性）并加注释（记录理由与备选；推荐 (A)——denial ledger/session store 均按租户作用域设计，null tenant 是跨租户可见性缺陷而非设计意图）。
- [x] `Fix` 按裁定落地：`wakeSession`（:395-499）同步阶段捕获 tenant、worker lambda 内恢复 + finally 清除（对称于 resumeSession :240-242 形态）；若裁定 (B) 则仅加注释说明理由（不改变行为）。
- [x] `Fix` 回归测试：多租户场景（session 带 tenantId）下 wakeSession 的租户作用域 DB 操作（如 denial ledger）以该 tenant 执行（ThreadLocal 断言或存储桩断言），非 null tenant。
- [x] `Proof` 复核：`wakeSession` 全路径（成功/失败/finally）的 tenant set/clear 对称性；与 resumeSession/restoreSession 的 tenant 处理对比表。

Exit Criteria:

- [x] `wakeSession` 租户上下文与裁定一致（(A) 恢复 tenant 或 (B) 显式注释理由），无"无注释的 null tenant 运行"状态。
- [x] 多租户回归测试断言 tenant 作用域操作以正确 tenant 执行。
- [x] **端到端验证**：带 tenantId 的 waiting session → wakeSession → 租户作用域操作 → 清理的完整路径（ThreadLocal 断言）。
- [x] **接线验证**：worker lambda 内 tenant 恢复点确实在租户作用域 DB 操作前执行（代码审查 + 测试）。
- [x] **无静默跳过**：无静默 null tenant 路径残留（若 (A)，全路径 set/clear 对称）。
- [x] owner doc 更新：`nop-ai-agent-reliability.md` §13（wakeSession 语义）或 `nop-ai-agent-session-and-storage.md` 同步（以实际落点为准）。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

- [x] 3 项 in-scope confirmed live defects（锁前就地变更 / save 后 release / wakeSession 无租户上下文）已修复
- [x] 全部 phase Exit Criteria 勾选完成，无静默降级到 deferred 的 in-scope 缺陷
- [x] 受影响 owner docs（`nop-ai-agent-reliability.md` §13 / `nop-ai-agent-session-and-storage.md` §11）已同步
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] 独立子 agent closure audit 已完成并记录证据（Anti-Hollow：运行时调用链连通、无空壳/静默跳过） successor: mission-driver CLOSURE_AUDIT trigger: 本 plan 执行完成后由独立 subagent 对照 Exit Criteria/Closure Gates 复核 live repo 并写入 ## Closure

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0534-3-p2-round4-session-lifecycle-1-89c16aad to opencode-pid-52866
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0534-3-p2-round4-session-lifecycle-1-89c16aad

## Verification

- pass test 20260915-0813 exit=0

## Closure

- dispatch audit #audit-20260915-0813-2026-09-15-0534-3-p2-round4-session-lifecycle-1-1d68e61e to opencode-pid-85385 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-0813-2026-09-15-0534-3-p2-round4-session-lifecycle-1-1d68e61e：独立 closure audit 通过——3 Phase 全部落地并经 live repo + 实跑复核：Phase 1 `resumeSession`/`wakeSession` 的 reset/guard/status/事件整体后移至 tryAcquire+putIfAbsent 成功之后（失败路径零内存变更，cached 会话保持 paused/waiting、denial 证据不清除、不发误导性事件），`TestSessionLifecycleLockGate` 7/7（锁门控 × InMemory/FileBacked + 事件缺席/在位 + 重试成功）；Phase 2 四路径（doExecute/resume/wake/restore）成功分支统一「save → checkpoint remove → release」，save 失败显式传播且租约仍释放（`TestSessionLifecycleSaveBeforeRelease` 6/6 含顺序断言 + 失败传播）；Phase 3 `wakeSession` 租户上下文与 resumeSession 对齐（同步阶段 + worker lambda 恢复 session tenant，对称清理），租户断言 2 例在位；本 visit 实跑 `./mvnw test -pl nop-ai/nop-ai-agent -o` 全绿（3403 tests 0 failures 0 errors）`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors，9 warnings 全为其他历史计划存量，本计划文件零告警）；`node tools/mission-driver/src/plan-check.mjs --strict` 43/43 全勾选 exit=0；owner docs（reliability.md §13.1 / session-and-storage.md §11.3）+ roadmap 3 项 + `ai-dev/logs/2026/09-15.md` 已同步；无 in-scope defect 被降级、无空壳/静默跳过残留