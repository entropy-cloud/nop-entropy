# 1 周期性 stuck-task 恢复扫描（交付 + 导出）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Source: throwable-sweep plan `2026-08-14-1452-1` Non-Blocking Follow-ups「Stuck-task monitoring query for SCHEDULED/RUNNING delivery/export records after Error propagation (operational concern, not code defect)」
> Related: `ai-dev/plans/nop-datav/2026-08-14-1510-2-report-delivery-retry-nop-retry-integration.md`（deferred；正交：retry 处理 FAILED，本计划处理 stuck 非终态）、`ai-dev/plans/nop-datav/2026-08-14-1452-1-executor-throwable-exception-classification-sweep.md`
> Mission: nop-datav
> Work Item: D5/D3 deferred follow-up — 周期性 stuck-task 恢复

## Purpose

把现有「仅进程重启（`@PostConstruct`）时清理中断记录」的恢复机制扩展为「周期性扫描 + 时间阈值」，使进程存活但 worker 线程因 JVM Error（经 throwable-sweep 后 Error 传播出 `GlobalExecutors.globalWorker()` 线程）死亡导致的 stuck 交付/导出记录，在一个有界时间窗口内被标记为 FAILED，无需等待下一次进程重启。时间阈值确保正在执行的记录不被误杀。

## Current Baseline

- **重启恢复仅 @PostConstruct、无时间阈值、覆盖两类记录**：
  - `NopDatavReportDeliveryRecovery.java:51-58,79-100`：进程启动时把所有 status ∈ {PENDING, RUNNING} 的交付记录标 FAILED（reason="interrupted by process restart"），无时间过滤，幂等。
  - `NopDatavExportTaskRecovery.java:54-61,82-101`：导出任务同构（标 FAILED，无时间过滤）。
  - 两者均在 `app-service.beans.xml:15,39-40` 注册（非 `_service.beans.xml`；后者只含 BizModel bean）。
- **gap：进程存活 + worker 线程死亡 → stuck 直到重启**：throwable-sweep plan `2026-08-14-1452-1` 把 `ReportDeliveryExecutor`/`NopDatavExportTaskBizModel` 的 `catch (Throwable)` 改为 `catch (Exception)`，使 JVM Error 传播出 `GlobalExecutors.globalWorker().submit(...)` 的 worker 线程（`ReportDeliveryExecutor.java:127-137`）。进程本身仍存活（线程池可建新线程），但该 delivery/export 记录永远停在 RUNNING/PENDING，直到下次重启才被 `@PostConstruct` 清理。对长运行服务器，stuck 窗口可达数天。
- **per-request 恢复已被显式移除（Dim14-01 审计裁定）**：`NopDatavExportTaskRecovery.java:26-30` javadoc 明记「恢复仅由容器启动期 PostConstruct 执行一次；请求路径不再调用（audit Dim14-01：per-request 调用会把所有其他用户/同用户的在途任务误标 FAILED）」。故周期扫描**必须**用时间阈值区分「真正 stuck」与「正常在途」，不能照搬重启时的「全部标 FAILED」。
- **两类记录的时间字段不一致（关键）**：
  - `NopDatavReportDelivery` 有 `startTime`（`_gen/_NopDatavReportDelivery.java:57`，propId 9）——执行开始时刻，适合做 stuck 判定基准。
  - `NopDatavExportTask` **无 `startTime`**，只有 `createTime`（`_gen/_NopDatavExportTask.java:73`，propId 13）——导出任务记录创建即排队，`createTime` 是可用的最早时间戳基准。
  - 两者均有 `updateTime`，但 `updateTime` 会在每次状态转换时刷新（`touchDelivery`/`touchTask`），对 stuck 判定不可靠（任何无关写都会重置时钟）。故 stuck 判定按实体分别用 `startTime`（交付）与 `createTime`（导出）。
- **nop-job cron 调度范式已就绪且可测**：`NopDatavReportScheduler`/`NopDatavAlertScheduler` 已示范「`@Inject @Nullable IJobScheduler` + `@PostConstruct` 注册 cron job」模式（`app-service.beans.xml:33-34,54-55`），并暴露 `getScheduler()`（`NopDatavReportScheduler.java:318`）+ `fireScheduledForTest()`（`:323`）供测试经 `IJobScheduler.fireNow` 同步触发。本计划复用此模式。

## Goals

- 一个周期性（可配置间隔，默认 10 min）stuck-task 扫描，覆盖报告交付（`NopDatavReportDelivery`）+ 导出任务（`NopDatavExportTask`）两类记录。
- 仅标记「状态 ∈ {PENDING, RUNNING} 且停留时间超过可配置阈值（默认 60 min，保守值）」的记录为 FAILED（reason 标注 stuck-by-timeout），不触碰阈值内正常在途记录。
- 扫描幂等、表不存在时安全跳过（沿用既有 `existsTable` 守卫）。
- 阈值/间隔/启用开关经 `NopDatavConfigs` 配置。
- 重启 `@PostConstruct` 恢复路径**不变**（仍无阈值，标记全部非终态）。

## Non-Goals

- **报告交付失败重试**：deferred plan `2026-08-14-1510-2`（retry 处理 FAILED 记录，本计划处理 stuck 非终态记录，两者正交；retry 当前 blocked 于平台 RPC 基础设施，见该 plan）。
- **per-request 恢复**：Dim14-01 已显式拒绝（会误杀在途任务）。
- **告警记录 stuck 扫描**：`NopDatavAlertState` 每次 cron tick 即时写回（`AlertEvaluator.evaluate` 同步），无独立长期 RUNNING 执行体记录，不适用。
- **stuck 记录自动重投/重试**：本计划只标记 FAILED（与重启恢复语义一致）；重投依赖 retry 或手动。
- **慢执行竞态的 sticky-FAILED 强化**：见 Risks（接受为良性竞态，不强制 worker 写前校验）。

## Risks And Rollback

- **慢执行竞态（已接受为良性）**：若一条交付/导出合法地执行超过阈值（如超大看板导出），扫描会在阈值到达时标 FAILED，但 worker 线程仍在运行。当 worker 最终完成并写 SUCCEEDED 时，会覆盖扫描的 FAILED。对交付而言这是**正确结果**（报告确实送达了，文件已完整生成）——扫描的 FAILED 是假阳性，被 worker 的 SUCCEEDED 修正。审计轨迹会丢失那次扫描干预记录，但终态正确。缓解：默认阈值取保守高值（60 min）使假阳性极罕见。若未来 retry plan 落地，FAILED 会触发重试 → 可能重复送达，届时需改为 sticky-FAILED（worker 写前校验状态），记为 follow-up。
- **导出 createTime 含排队等待（与交付 startTime 的区别）**：交付有 `startTime`（执行开始时刻），stuck 判定精确。导出任务**无 startTime**，只能用 `createTime`（提交时刻）——`NopDatavExportTaskBizModel` 提交时设 `createTime`，执行经 `GlobalExecutors.globalWorker().submit(...)` 异步延迟。故导出的 `createTime` = 排队等待 + 执行耗时。在并发上限（`max-concurrent-per-user=3`）下若排队超过阈值（默认 60 min），一条刚开始执行的导出可能被误标 FAILED。缓解：保守高默认阈值（60 min）+ 低并发上限使该场景极罕见；若导出量大需为导出单独调高阈值或后续补 `startTime` 列。
- 回滚：删除新 scanner bean 即恢复仅重启恢复的现状（无数据迁移）。

## Scope

### In Scope

- 新增 `NopDatavStuckTaskScanner` bean（`app-service.beans.xml`，`ioc:default="true"`，`@Inject @Nullable IJobScheduler`），`@PostConstruct` 注册一个周期 cron job（间隔可配，默认 10 min）调 `scanStuck()`。
- 在既有 `NopDatavReportDeliveryRecovery` / `NopDatavExportTaskRecovery` 各新增带时间阈值的 `scanStuck(int timeoutMinutes)` 方法（仅标记超阈值非终态记录）；**不改动**既有 `recoverInterrupted*()` / `init()`（重启路径不变）。
- `NopDatavConfigs` 增配置：`nop.datav.stuck-scan.enabled`（默认 true）、`nop.datav.stuck-scan.interval-minutes`（默认 10）、`nop.datav.stuck-scan.timeout-minutes`（默认 60）。
- `scanStuckForTest()` 测试入口（镜像 `fireScheduledForTest` 模式）。
- 设计文档 `schedule-report-design.md` 增补 stuck-task 周期恢复章节。
- 单元测试覆盖阈值边界、幂等、慢执行竞态、周期触发接线。

### Out Of Scope

- 失败重试（deferred plan `2026-08-14-1510-2`）。
- per-request 恢复（Dim14-01 拒绝）。
- 告警 stuck（不适用）。
- sticky-FAILED worker 强化（follow-up，见 Risks）。
- stuck 运营面板（flux）。

## Execution Plan

### Phase 1 — 实现：scanner bean + 阈值扫描方法 + 配置 + cron 注册

Status: completed
Targets: `app-service.beans.xml`、新增 `NopDatavStuckTaskScanner.java`、`NopDatavReportDeliveryRecovery.java`、`NopDatavExportTaskRecovery.java`、`NopDatavConfigs`

- Item Types: `Fix`（关闭重启-only gap）、`Decision`（默认阈值/间隔值、cron job 标识）

- [x] `NopDatavConfigs` 增 3 个配置项（enabled/interval-minutes/timeout-minutes，含 `@Description`，沿用 `varRef(s_loc,...)` 模式）
- [x] 在 `NopDatavReportDeliveryRecovery` 增 `scanStuck(int timeoutMinutes)`：查 status ∈ {PENDING,RUNNING} 且 `startTime < now − timeoutMinutes` 的记录标 FAILED（reason="stuck beyond timeout threshold (Xm)"）；表不存在跳过；既有 `init()`/`recoverInterruptedDeliveries()` **不动**
- [x] 在 `NopDatavExportTaskRecovery` 增 `scanStuck(int timeoutMinutes)`：同上但基准字段用 `createTime`（导出任务无 startTime）；既有 `init()`/`recoverInterruptedTasks()` **不动**
- [x] 新增 `NopDatavStuckTaskScanner` bean：`@Inject @Nullable IJobScheduler`；`@PostConstruct` 当 scheduler≠null 且 enabled 时注册周期定时 job（间隔 = interval-minutes；触发机制用 `TriggerSpec.setRepeatInterval` 固定间隔，或由 interval-minutes 合成 cron 表达式，按既有 `TriggerSpec` 能力裁定——二者均受支持）调 `scanStuck()`；`scanStuck()` 读 timeout-minutes 配置并委托两类 recovery 的 `scanStuck(timeoutMinutes)`；scheduler==null 时 INFO 日志跳过（镜像既有 scheduler 模式）；暴露 `getScheduler()` + `scanStuckForTest()` 供测试
- [x] `app-service.beans.xml` 注册 `nopDatavStuckTaskScanner`（`ioc:default="true"`，注入两个 recovery bean）

Exit Criteria:

> 本 Phase 为实现落地；测试在 Phase 2。Exit Criteria 以代码结构可观测为准。

- [x] `NopDatavConfigs` 含 3 个新配置项（`rg` 命中 enabled/interval-minutes/timeout-minutes）
- [x] 两类 recovery 各有 `scanStuck(int)` 方法（`rg "scanStuck" nop-datav-service` 命中），且既有 `recoverInterrupted*`/`init` 签名未变（重启路径不回归）
- [x] `NopDatavStuckTaskScanner` 存在，`@Inject @Nullable IJobScheduler` + `@PostConstruct` cron 注册 + `scanStuck()` 委托；`app-service.beans.xml` 含 `nopDatavStuckTaskScanner` bean 定义
- [x] **不变式（F4）**：重启 `@PostConstruct` 路径仍无阈值（`recoverInterrupted*` 未被 `scanStuck` 替换或合并）；两方法不统一
- [x] **无静默跳过（Minimum Rules #24）**：表不存在时显式跳过（沿用既有 `existsTable` 守卫）；`scanStuck` 标记记录写明确 reason（非空覆盖）；scheduler==null 时 INFO（非空吞）
- [x] `./mvnw compile -pl nop-datav/nop-datav-service -am` BUILD SUCCESS
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/` 更新放在 Phase 2（设计文档章节）；此处明确标注 deferred to Phase 2
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 测试 + 设计文档定稿

Status: completed
Targets: `nop-datav/nop-datav-service` 测试、`schedule-report-design.md`

- Item Types: `Proof`（阈值/幂等/竞态/接线覆盖）、`Follow-up`（文档定稿）

- [x] 新增测试覆盖：①交付记录 startTime 超阈值 → 标 FAILED + reason；②交付记录未超阈值 → 不动；③导出记录 createTime 超阈值 → 标 FAILED；④导出记录未超阈值 → 不动；⑤幂等（终态记录重复扫描不动）；⑥扫描异常不崩（WARN）；⑦表不存在安全跳过；⑧**周期触发接线（无条件，Anti-Hollow）**：经 `scheduler.fireNow(scannerJobName)` **走 `beanMethod` job invoker** 触发（**非** `scanStuckForTest()` 直调——直调绕过 invoker 无法证明 cron 注册真正派发到 `scanStuck()`），断言 `scanStuck()` 内部计数器/标志递增（mock verify）；`scanStuckForTest()` 仅作 ①–⑦/⑨ 受控调用的辅助入口；⑨**慢执行竞态**：超阈值标 FAILED 后 worker 仍可写 SUCCEEDED（验证终态正确、不崩）
- [x] `schedule-report-design.md` 增补 stuck-task 周期恢复章节（最终结论：阈值/间隔默认值、交付用 startTime 导出用 createTime 的理由、与重启恢复的关系、与 Dim14-01 per-request 拒绝的关系、与 deferred retry plan 的正交性、慢执行竞态裁定）

Exit Criteria:

- [x] **新功能测试规则（Minimum Rules #25）**：Exit Criteria 显式列出 ①–⑨ 每条测试用例名与预期结果，而非仅「原有测试通过」
  - ① `testDeliveryBeyondTimeoutMarkedFailedWithReason`：RUNNING + startTime=now−61min + scanStuck(60) → FAILED + errorMsg 含 "stuck beyond timeout threshold (60m)" + endTime 写入
  - ② `testDeliveryWithinTimeoutUntouched`：RUNNING 30min + PENDING 刚创建 + scanStuck(60) → 0 标记、状态不变、errorMsg 为 null
  - ③ `testExportTaskBeyondTimeoutMarkedFailed`：RUNNING + createTime=now−61min + scanStuck(60) → FAILED + reason 含前缀与 "(60m)"
  - ④ `testExportTaskWithinTimeoutUntouched`：RUNNING 30min + PENDING 刚提交 + scanStuck(60) → 0 标记、状态不变
  - ⑤ `testRescanIdempotentAndTerminalRecordsUntouched`：首扫标记后重扫返回 0 且不覆盖 errorMsg；既有 FAILED/SUCCEEDED（120min 前）即使超阈值也不动（errorMsg "manual stop" 保留）
  - ⑥ `testScannerSwallowsRecoveryException`：两类 recovery 均抛 RuntimeException → `scanStuckForTest()` 不抛、返回 0/0 结果 Map、scanCount 仍 +1
  - ⑦ `testScanStuckSkipsWhenTableNotExists`：IJdbcTemplate 代理 existsTable=false → scanStuck 返回 0、超阈值记录保持 RUNNING（跳过而非全量）
  - ⑧ `testPeriodicWiringFireNowInvokesScanStuck`：getJobNames 含 JOB_NAME（@PostConstruct 注册）→ `fireNow(JOB_NAME)` 走 beanMethod invoker → scanCount 递增 + 超阈值交付与导出记录均 FAILED（端到端）
  - ⑨ `testSlowExecutionRaceWorkerSuccessOverwritesScanFailed`：扫描 FAILED 后 worker 风格终态写 SUCCEEDED（交付+导出）→ 终态 SUCCEEDED、worker 字段胜出、不崩
- [x] **接线验证（Minimum Rules #23，无条件）**：测试 ⑧ 断言周期 cron 注册存在且 `scanStuck()` 被运行时调用（非仅类型存在）
- [x] **端到端验证（Minimum Rules #22）**：测试 ①+⑧ 覆盖「cron 触发 → 扫描 → 超阈值记录标 FAILED」完整路径
- [x] 全部新测试 PASS（`./mvnw test -pl nop-datav/nop-datav-service -am`），既有测试无回归
- [x] 设计文档无 "Proposed"/"待定" 残留（最终结论状态）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/design/` 后必跑）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 周期性 stuck 扫描覆盖交付（startTime）+ 导出（createTime）两类记录（端到端/接线已验证）
- [x] 时间阈值生效：超阈值标 FAILED、阈值内不动（边界测试 ①–④ 通过）
- [x] 扫描幂等、不误杀在途记录（Dim14-01 不回归；重启路径无阈值不变式保持）
- [x] 慢执行竞态已裁定（良性，终态正确；或 sticky-FAILED follow-up 记录）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 受影响 owner doc（`schedule-report-design.md`）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：周期 cron 运行时确实调用 `scanStuck()`（非仅 bean 存在）；无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav/nop-datav-service -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### stuck 记录自动重投（stuck → re-queue 而非仅 FAILED）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划与重启恢复语义一致（仅标 FAILED，不重投）。重投依赖 retry 能力（deferred plan `2026-08-14-1510-2`，当前 blocked 于平台 RPC 基础设施）或手动操作，属独立决策。
- Successor Required: `no`

### sticky-FAILED worker 写前状态校验

- Classification: `watch-only residual`
- Why Not Blocking Closure: 慢执行竞态为良性（worker SUCCEEDED 修正假阳性 FAILED，终态正确）。sticky-FAILED 仅在 retry plan 落地后（FAILED 会触发重试导致重复送达）才成为必要，届时作为该 plan 的前置项处理。
- Successor Required: `no`（条件触发，取决于 retry plan 是否落地）

## Non-Blocking Follow-ups

- stuck-task 运营面板（展示 stuck 历史与原因，flux）
- 阈值自适应（按历史执行耗时 P95 动态调整，optimization candidate）
- 扫描连续失败计数指标 / 连续 N 次失败后升级为 ERROR 日志（防 WARN 刷屏掩盖持续故障）

## Closure

Status Note: 两类 recovery 各新增带阈值 `scanStuck(int)`（交付 startTime / 导出 createTime 基准），新增 `NopDatavStuckTaskScanner` 周期 job（默认 10 min 间隔 / 60 min 阈值，可配置）委托扫描；重启 `@PostConstruct` 全量恢复路径保持无阈值不变式（F4）；9 个新测试覆盖 ①–⑨（含 fireNow 经 beanMethod invoker 的运行时接线验证与慢执行竞态终态正确性）；`schedule-report-design.md` §25 定稿。stuck 窗口从「直到进程重启」收敛到有界 ≈ timeout+interval。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（task id: `ses_fffaf7e42ffe4bPSlIij2QvOz2`，fresh session，read-only）
- Evidence:
  - Phase 1 Exit Criteria：全 PASS（auditor 逐条核对 live code）——`NopDatavConfigs.java:80-89` 3 配置项；`NopDatavReportDeliveryRecovery.java:98-130`（status∈{PENDING,RUNNING}+lt(startTime)，reason 格式）；`NopDatavExportTaskRecovery.java:102-132`（createTime 基准）；`NopDatavStuckTaskScanner.java`（@Nullable IJobScheduler/@PostConstruct setRepeatInterval 注册/beanMethod invoker/委托/吞错 WARN/scanCount）；`app-service.beans.xml:48-49`（ioc:default=true）。F4 不变式经 git diff 确认（restart 路径 byte-identical）。
  - Phase 2 Exit Criteria：全 PASS——`TestNopDatavStuckTaskScan.java` 9 tests 与 plan 列名一一对应；⑧ 接线测试 (a) job 注册断言 (b) `fireNow(JOB_NAME)` 走 invoker（非直调）(c) scanCount 递增断言 (d) 交付+导出端到端 FAILED；`schedule-report-design.md` §25（:499-530）含全部要求要素，无 Proposed/待定残留。
  - Closure Gates：全 PASS——`./mvnw compile -pl nop-datav/nop-datav-service -am` SUCCESS；`./mvnw test -pl nop-datav/nop-datav-service -T 1C` **433 tests, 0 failures**（424 既有 + 9 新增；`-am` 全链路上游 nop-auth-service 2 个预存失败为平台级、历史多次记录，与本 plan 无关）；`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（auditor 独立复跑确认）；`check-doc-links.mjs --strict` 退出码 0（auditor 独立复跑确认）；代码规范：imports 分组、无未用 import、checkstyle 通过（compile+test 全绿）。
  - Anti-Hollow 检查：PASS——auditor 验证 scanStuck 实体方法有真实 body（QueryBean 查询+标记循环+LOG），existsTable 守卫为显式 INFO 跳过（镜像既有模式），运行时接线经 fireNow→BeanMethodJobInvoker→scanStuck() 计数器递增证明；scan-hollow 退出码 0。
  - Deferred 项分类检查：PASS——auto re-queue（依赖 deferred retry plan）与 sticky-FAILED（watch-only，竞态良性经测试 ⑨ 验证）均为已裁定 non-blocking；Non-Blocking Follow-ups 仅含运营面板/自适应阈值/失败计数指标（optimization/governance），无 live defect 降级。
- 注：实现者顺手修复 2 个预存 doc-link 错误（`nop-credential-mfa-roadmap.md` future-deliverable 路径措辞）与 1 个 hollow-scan 误报（`NotificationSender.java:467` 注释 "temp resource" 措辞触发 P6b 正则；comment-only、零行为变化），均为恢复工具退出码 0 的必要修复。

Follow-up:

- stuck-task 运营面板（flux）、阈值自适应（P95）、扫描连续失败计数/升级 ERROR 日志（见 Non-Blocking Follow-ups，均 non-blocking）
- sticky-FAILED worker 写前校验：条件触发（仅 retry plan `2026-08-14-1510-2` 落地后必要）
