# {2} Export Task State-Machine Correctness

> Plan Status: active
> Last Reviewed: 2026-08-11
> Source: `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md` — Dim14-01 [P1], Dim07-01 [P1], Dim16-01 [P1]
> Related: design `ai-dev/design/nop-datav/permission-sharing-design.md`（§310 状态机 / §318 cancel 轮询 / §322-326 重启恢复）

## Purpose

把「数据导出任务」状态机的三处契约违背收口：cancel 在执行期内被静默改写为 SUCCEEDED、每次请求都误杀全部在途任务、
cancel-during-running 契约零有效测试。修复后导出任务状态机在 design §310/§318/§322-326 下行为正确且有测试护栏。

## Current Baseline

（基于 2026-08-10 live code 核对）

- **Dim14-01（active incorrect）**：`NopDatavExportTaskBizModel.createExportTask` 在每次请求线程、插入新任务前同步调用
  `recovery.recoverInterruptedTasks()`（`NopDatavExportTaskBizModel.java:136`）。`NopDatavExportTaskRecovery.doRecover`
  选**全部**非终态任务（PENDING/RUNNING，无 owner/陈旧度谓词）并写 `status=FAILED`、`errorMsg="interrupted by process restart"`
  （`NopDatavExportTaskRecovery.java:80-99`）。`@PostConstruct init()` 已在容器启动时跑过一次（`:52-59`），故每次请求的调用
  冗余且有害：并发导出下，每个请求都会把所有其他用户/同用户在途任务误标 FAILED。对照 `NopDatavReportDeliveryRecovery` 仅
  `@PostConstruct`、无 per-request 调用，确认导出路径是离群点。
- **Dim07-01（broken contract）**：`executeTask` 中 cancel 标志位**仅在 RUNNING 迁移前检查一次**（`:211`）；执行体
  `exporter.exportDashboard/exportPanel`（`:228/:235`）内**不轮询**任何标志；`PanelDataExporter.exportPanel/exportDashboard`
  不接受 cancel 供应商、写出循环（`writeCsv:160-162`、`toSheet:220-227`、dashboard 面板循环 `:130-141`）内无标志检查。
  随后 SUCCEEDED 写入（`:241`）**无条件**覆盖 cancel 线程已写的 CANCELLED（`cancelExportTask:166-171` 同时置标志 + 写 CANCELLED），
  因 `updateEntityDirectly` 不做版本检查，执行体后写的 SUCCEEDED 生效 → 用户看到短暂 CANCELLED 后翻转为 SUCCEEDED。
- **Dim16-01（zero-protective test）**：`TestNopDatavExportE2E.testCancelRunningTaskTransitionsToCancelled`（`:246-254`）
  仅用 `seedTask` 直写一行 RUNNING（从未 `submitExecution`），故执行体根本不并发运行；它只验证了 cancel 请求线程的直写，
  无法检测执行体覆盖问题。全仓 grep 无任何测试引用 `cancelFlags`/`isCancelFlagged`。删掉整个 cancel 机制也不会让任何测试失败。
- **副作用**：`ERR_DATAV_EXPORT_FAILED`（`NopDatavErrors.java:257`，design §318 指定用于 cancel 轮询抛出）当前无任何引用（见 backlog Dim09-04）。

## Goals

- 取消一个正在 RUNNING 的导出任务，最终状态为 `CANCELLED`（而非被 SUCCEEDED 覆盖），`fileRecordId` 为 null。
- `createExportTask` 不再在请求路径调用 `recoverInterruptedTasks()`；在途任务不受新请求影响（恢复仅 `@PostConstruct` 启动期执行）。
- 新增一条真正端到端的 cancel-during-execution 测试：提交真实任务 → 轮询至 RUNNING → cancel → 断言终态 CANCELLED + `fileRecordId` null；
  该测试在「移除 cancel 轮询」时会失败（即具备保护力）。

## Non-Goals

- 不重写导出状态机（仍保持 PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED 五态）。
- 不改动导出取数管线（数据集查询、行数限额、文件落盘）。
- 不处理分享/导出的 RBAC 与 `passwordHash`（→ Plan {1}）。
- 不处理 P2 项（Dim09-03 空源错误码、Dim09-04 chatbi 死错误码、Dim14-03/04 调度器吞 Throwable），见 backlog。

## Scope

### In Scope

- `NopDatavExportTaskBizModel.java:136`：删除 per-request `recovery.recoverInterruptedTasks()` 调用。
- `NopDatavExportTaskBizModel.executeTask`：将 cancel 标志以 `BooleanSupplier` 形式传入 `PanelDataExporter`；
  在 SUCCEEDED 写入前（`:241` 之前）再次检查标志，命中则走 `markCancelled` 而非 SUCCEEDED。
- `PanelDataExporter.exportPanel/exportDashboard`：接受可选 `BooleanSupplier cancelChecker`，在每个面板取数前与写出循环内检查，
  命中抛 `NopException(ERR_DATAV_EXPORT_FAILED).param(ARG_REASON, "cancelled by user")`（让执行体 catch 走 FAILED/CANCELLED 路径，
  或直接由 BizModel 转为 CANCELLED——由 Phase 2 Decision 裁定具体短路方式）。
- `TestNopDatavExportE2E`：新增真实 cancel-during-execution E2E 测试；保留既有 cancel 测试。

### Out Of Scope

- 把 cancel 标志持久化（当前为内存 `ConcurrentMap`，进程重启由 `@PostConstruct` 恢复清理，符合 design §322-326，不改）。
- 报告交付 SMTP/会话问题（→ Plan {3}）。
- 引入导出任务的版本号乐观锁（audit 未要求；`updateEntityDirectly` 不版本检查是现状，本计划用「轮询 + SUCCEEDED 前复检」闭合，不改 DAO 行为）。

## Execution Plan

### Phase 1 - 移除 per-request 恢复调用（Dim14-01）

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:135-136`

- Item Types: `Fix`

- [ ] 删除 `createExportTask` 中 `recovery.recoverInterruptedTasks();`（`:136`）及其上方的注释（`:135`）。`@PostConstruct init()` 仍是唯一恢复入口（保留 `NopDatavExportTaskRecovery` 不变）。**Scope 边界**：仅删 `:136` 请求路径调用；**保留** `NopDatavExportTaskBizModel.java:443-445` 的 `public void recoverInterruptedTasks()` 委托方法（注释「供测试/管理直接触发重启清理」，是测试/管理入口，非请求路径）；`recovery` 字段仍被 `:444` 引用，不留未用注入。
- [ ] 新增/扩展测试：并发场景——一个任务处于 RUNNING/PENDING 时，另一用户（或同用户）发起 `createExportTask`，断言前者状态**未被误改为 FAILED**（仍为 PENDING/RUNNING 或其真实终态）。

Exit Criteria:

- [ ] `createExportTask` 请求路径不再调用 `recoverInterruptedTasks()`（live code 与 grep 确认 `:136` 已删；`:443-445` 测试/管理委托保留）。
- [ ] 在途任务在新导出请求后状态不被误杀（focused test 断言）。
- [ ] `@PostConstruct init()` 恢复路径未被移除（容器启动仍清理中断任务）。
- [ ] **owner-doc 漂移裁定**：`permission-sharing-design.md` §322-326 描述恢复为启动期；§323 设计原文为「BizModel 实现 `IInitializer`」，live 实现为独立 `NopDatavExportTaskRecovery` bean + `@PostConstruct`（行为等价：均启动期执行一次）。本 plan **不修 §323**（out-of-scope 预存漂移，behaviorally equivalent），仅在 closure 记录该裁定，避免审计误判。
- [ ] **无静默跳过**：删除的是错误调用，非以空方法体/吞异常替代。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - cancel 执行体内轮询（Dim07-01）

Status: planned
Targets: `NopDatavExportTaskBizModel.java:204-261`（executeTask）；`PanelDataExporter.java:82-145`（exportPanel/exportDashboard）

- Item Types: `Fix | Decision`

- [ ] **D2（Decision）— cancel 短路语义（对齐 design §312）**：design §312 明确「cancel 请求线程把 DB 状态 running→cancelled 后，**执行体检测到标志位后仅写 errorMsg("cancelled by user") 不再改状态**」。故执行体侧**不调用 `markCancelled`（它会写 status）**，而是：(1) `PanelDataExporter` 命中 cancel 抛 `NopException(ERR_DATAV_EXPORT_FAILED).param(ARG_REASON,"cancelled by user")`；(2) `executeTask` catch（`:247`）**在 `setStatus(FAILED)`（`:249`）之前**先查 `cancelFlags.get(taskId)`：命中 → **不写 FAILED**（status 已由 cancel 线程写为 CANCELLED），仅可补写 `errorMsg`；未命中 → 走原 FAILED 路径；(3) SUCCEEDED 写入（`:241`）**之前**复检 `cancelFlags`：命中 → **跳过 SUCCEEDED 写入**（保留 cancel 线程写的 CANCELLED），仅可补写 errorMsg 后 return。此方案与 design §312 字面一致，Closure Gate 的文档一致性可成立（无需改 design）。注意：当前 `:254` 的 `cancelFlags` 判定位于 `setStatus(FAILED)` 之后、仅用于抑制日志，**不能直接复用为分流点**——分流必须在设 FAILED 之前。
- [ ] `PanelDataExporter.exportPanel/exportDashboard` 增加可选 `BooleanSupplier cancelChecker` 入参（重载或追加参数，保持调用方兼容）；在 `exportDashboard` 每个面板取数前（`:130` 循环内）、`exportPanel` 取数后写出前、以及写出循环（`writeCsv`/`toSheet` 行循环）内检查；命中即抛 `NopException(ERR_DATAV_EXPORT_FAILED).param(ARG_REASON,"cancelled by user")`。（注：`queryPanelData` 一次性物化所有行，写出循环为 CPU-bound µs 级，故 exporter 内轮询的主要保护点在「面板间」与「取数后写出前」；pre-SUCCEEDED 复检是确定性兜底点。）
- [ ] `executeTask` 把 `() -> Boolean.TRUE.equals(cancelFlags.get(taskId))` 作为 `cancelChecker` 传入 exporter；并在 SUCCEEDED 写入（`:241`）**之前**再查一次 `cancelFlags`，命中则按 D2 跳过 SUCCEEDED 写入。
- [ ] 确保 `ERR_DATAV_EXPORT_FAILED`（`NopDatavErrors.java:257`）被真正引用（顺带退役 backlog Dim09-04 的导出侧死错误码）。

Exit Criteria:

- [ ] RUNNING 中 cancel 后，任务终态为 `CANCELLED`（由 cancel 线程写入，执行体不再覆盖）、`fileRecordId == null`，SUCCEEDED/FAILED 不会被写回（focused test 反向断言）。
- [ ] 执行体侧**不调用 markCancelled 写 status**；cancel 命中时执行体仅跳过 SUCCEEDED/不写 FAILED（对齐 design §312）。
- [ ] `PanelDataExporter` 在面板循环/写出循环内确实检查 `cancelChecker`（live code 确认，非仅入参存在）。
- [ ] catch 分流在 `setStatus(FAILED)` 之前判定（live code 顺序确认，非复用 `:254` 日志判定）。
- [ ] **接线验证**：`executeTask` 传入的 `cancelChecker` 与 `cancelFlags` 运行时联动（E2E 测试在 Phase 3 验证；此处代码追踪确认）。
- [ ] **无静默跳过**：cancel 命中是显式抛 `NopException` 或显式跳过 SUCCEEDED，非 `continue`/空体/吞异常。
- [ ] owner-doc：`permission-sharing-design.md` §310/§312/§318（running→cancelled、执行体检测标志位后不改状态仅写 errorMsg、执行体内主动轮询）与 live 一致（本方案对齐 §312 字面，无需改 design）。
- [ ] `./mvnw test -pl nop-datav -am` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - cancel-during-execution E2E 测试（Dim16-01）

Status: planned
Targets: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/entity/TestNopDatavExportE2E.java`

- Item Types: `Proof`

- [ ] **D4（Decision）— 确定性 RUNNING 窗口机制**：现有测试数据（`TestNopDatavExportE2E.setupSalesData` 的 `TEST_DATAV_SALES` 仅 3 行）+ `queryPanelData` 一次性物化 + `pollUntilTerminal` 100ms 轮询，无法可靠观察 cancel-during-running 的微秒级窗口。故测试采用**确定性 test seam**（二选一，执行时裁定并落实）：
  - **(方案 A，推荐)** 在 `NopDatavExportTaskBizModel`/`PanelDataExporter` 引入测试可见的 `CountDownLatch` seam：exporter 进入「面板间 cancel 检查处」时 `latch.countDown()`（通知测试「执行体已进入可取消区」），并在该处 `latch.await(timeout)` 阻塞直到测试释放；测试 `await` 该 latch 至触发后，调 `cancelExportTask`，再释放阻塞，使执行体下一次轮询命中 cancel → 抛 `ERR_DATAV_EXPORT_FAILED` → 跳过 SUCCEEDED。该 seam 仅测试可见（package-private setter / `@Inject` optional），生产路径无 latch 不阻塞。
  - **(方案 B)** 构造足够大的数据集（如插入 N 万行）使 RUNNING 窗口可被收紧后的 ≤10ms 轮询稳定捕获；仅当方案 A 因接线成本过高被裁定否决时采用。
  无论选哪个，`pollUntilTerminal` 轮询间隔在该测试中收紧至 ≤10ms（或改用 latch 同步，避免轮询）。
- [ ] 新增测试：`createExportTask` 提交一个真实任务（`submitExecution` 真实跑执行体，非 seedTask 直写），按 D4 机制确定性地进入 RUNNING 并在该窗口调用 `cancelExportTask`，再轮询至终态，断言终态为 `CANCELLED`（非 SUCCEEDED）且 `fileRecordId == null`、`errorMsg` 含 "cancelled"。
- [ ] **mutate-fail 精确声明**：本测试的「保护力」基线 = **同时移除 Phase 2 的两个 checkpoint**（(1) exporter 内 `cancelChecker` 轮询 + (2) `executeTask` pre-SUCCEEDED 复检）整体回归时，本测试必须失败（终态翻转为 SUCCEEDED 或 fileRecordId 非空）。仅移除其一不应被 closure audit 视为「Dim16-01 已闭合」。
- [ ] 保留既有 `testCancelRunningTaskTransitionsToCancelled`（直写 RUNNING 的请求线程路径仍需覆盖），并在其注释中显式标注「仅覆盖 cancel 请求线程直写路径，与新增 E2E（覆盖执行体覆盖问题）互补」。

Exit Criteria:

- [ ] 新增 E2E 测试在当前实现（两 checkpoint 均在）下通过，且在「同时移除两个 checkpoint」时失败（具备保护力，闭合 Dim16-01）。
- [ ] 测试覆盖 `submitExecution` → RUNNING → cancel → 终态 CANCELLED 完整链路，非 seedTask 直写；RUNNING 窗口经 D4 机制确定性可观察（非靠 timing 偶然命中）。
- [ ] **端到端验证**：从 `createExportTask` 入口到任务终态输出的完整路径已验证（见 Minimum Rules #22）。
- [ ] 轮询/latch 不引入 flaky（收紧至 ≤10ms 或用同步原语）。
- [ ] `./mvnw test -pl nop-datav -am` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] Dim14-01：`createExportTask` 请求路径不再误杀在途任务（恢复仅 `@PostConstruct`）。
- [ ] Dim07-01：cancel-during-RUNNING 终态为 CANCELLED，SUCCEEDED/FAILED 不覆盖；`PanelDataExporter` 真实轮询；执行体对齐 design §312（检测标志位后不改 status）。
- [ ] Dim16-01：新增 E2E 测试具备保护力（同时移除两个 checkpoint 即失败；D4 机制确定性可观察）。
- [ ] `ERR_DATAV_EXPORT_FAILED` 已被引用（顺带退役 backlog Dim09-04 导出侧）。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect。
- [ ] owner docs（`permission-sharing-design.md` §310/§312/§318/§322-326）与 live baseline 一致（§323 机制漂移裁定为 out-of-scope 预存漂移）。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证 cancelChecker 在运行时被 exporter 与 executeTask 真实调用（端到端测试 D4 机制 + 代码追踪），且两个 checkpoint（exporter 轮询 + pre-SUCCEEDED 复检）均有测试覆盖（mutate-fail 各自可验证）。
- [ ] `./mvnw compile -pl nop-datav -am`
- [ ] `./mvnw test -pl nop-datav -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若未来需要 cancel 持久化（集群/多实例），可把 `cancelFlags` 迁移到共享存储；当前单机内存 + 启动恢复符合 design，watch-only。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- no remaining plan-owned work（关闭时确认）
