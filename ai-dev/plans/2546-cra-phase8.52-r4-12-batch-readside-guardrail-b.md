# 2546 CRA Phase 8.52 — R4-12 1.7 读侧/护栏束 b（G23 后半，11 工单）

> Plan Status: draft
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.52（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :223，Status=**todo**；Owner Doc=`docs-for-ai/03-modules/nop-batch.md`；Deps=「batch-proposal.md §二 R4-12（11 项）；硬依赖 8.22——已 done（plan 2516），硬依赖解除」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 R4-12 行（11 finding ID / 模块 nop-batch / 验证命令 `./mvnw test -pl nop-batch -am` / PA=无 / exit criteria 增补：「C8-007 与 C8-004 绑定、**C8-008 为 C8-004 前置**（三面一次接线）」）+ §二.5 G23 组行「22 | R4-11 + R4-12 | 拆分 11/11」；成员发现详情 = `ai-dev/audits/2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency/`（C7-long-running.md C7-005/C7-006/C7-007/C7-008/C7-009；C7-round2.md C7-012；C8-backpressure.md C8-004；C8-round2.md C8-007/C8-008/C8-009/C8-010）
> Related: plan 2545（R4-11，**completed @ 2026-11-09，closure audit R4 APPROVED**）为本批同模块前批——本批**复用其教训**：接口存在 ≠ 行为完成、每条工单必须有可被回归捕获的载体、owner doc 与代码必须一致；plan 2516（R2-13）关闭 C8-001 Guard 与 C8-002 钳位共享位点，本批 C8-009 的「C8-001 Guard 先于参数化」次序前置已满足；C10-005/006/007/008/009/012 为本批验收前置

**Draft Review 记录**

- R1（fresh session 独立评审，2026-11-09）：**REJECT**，6 Blocker + 7 Major + 10 Minor。全部随本版落盘（见下方「Draft Review R1 修订清单」）。
- 修订后待 R2 复审。

### Draft Review R1 修订清单（逐条已闭环）

| R1 项 | 结论与修订 |
|---|---|
| B-1 全批无测试载体 | Phase 2 增「工单 × 载体」表（W-01..W-11 逐条测试类/方法/断言形态），Phase 2/3 Exit Criteria 增红证与变异论证要求 |
| B-2 C7-012「DSL 键」与 Non-Goals 矛盾 | 删除「DSL 键」表述；Deferred 增「KILLED 撤离门仅程序化 API（DSL 面归 xdef 批次）」+ Why Not Blocking |
| B-3 C7-009 捕不到 `SQLException` | 改为按 `NopException` 的 `ErrorCode ∈ {ERR_SQL_TRANSIENT_CONNECTION_FAIL, ERR_SQL_RECOVERABLE_DATA_ACCESS, ERR_SQL_DATA_ACCESS}` 判定；broken 检查**先于** `isClosed()` 早退 |
| B-4 C8-008 类名 `BatchDispatchQueue` 不存在 | 更正为 `PartitionDispatchQueue`；Gauge 生命周期：`context.onAfterComplete` 注销 + tag 用 `task.name/taskId`（capacity 不作 tag） |
| B-5 C7-008 `setup` 无 batchSize | 落点改为 `load(...)` 内 `batchSize<=0` fail-fast，并说明与 `BatchChunkProcessor.adjustBatchSize` 的先后（loader 先于 chunk 钳位） |
| B-6 C8-004 四入口 taskName 不可得 | 改为**单漏斗**：`BatchTask.executeAsync` 在 `setTaskName/setTaskVersion` **之后**才解析/创建 metrics；补 biz `builder.taskName` |
| M-1 C7-006 层级混淆 | 判定改**任务级** `batchTaskModel.getSaveState()==TRUE` 且 reader ∈ {jdbc,orm,generator}；以 sys-event 配置为参数化用例 |
| M-2 C8-010 exp 半边未修 | exp 自建池（`ExportDbTool`/`ImportDbTool` 的 `newExecutor(...,threadCount,10000)`）threadCount 无上界 → 纳入 W-11（乘积上界校验） |
| M-3 C8-008 平台面事实被推翻 | `ThreadPoolConfig.enableMetrics` **已被 `ExecutorHelper.registerMetrics` 消费**（`isEnableMetrics`，非审计 grep 的 `getEnableMetrics`）→ 改写 Deferred 前提为「残余面=换 registry 后重绑缺失 + 无 batch 维度标签」 |
| M-4 C8-009 缺未配置哨兵 | 明确：字段缺省 0 = 未配置哨兵，builder 仅 `>0` 时透传，否则保留 Provider 缺省 30/100；Guard 落 Provider/Queue setter |
| M-5 三面接线顺序写反 | 改为 **C8-008 → C8-004 → C8-007**，Phase 2 显式写「W-09 先于 W-07」 |
| M-6 C7-012 WHERE 谓词改动是空操作 | 删除谓词改动（`taskStatus>RUNNING` 已含 KILLED=60）；只做 `:135` 早退旁路 |
| M-7 C7-007 `newEntity()` 语义未说明 | 补：cursor 实体复用（非每 chunk 新建）、TRANSIENT 不入 cache 不 flush、列名=属性名假设与 `dao.getPkColumnNames()` 来源 |
| m-1..m-10 | `Plan Status: draft`；撤无证据 log 勾选；`toPolicy`→`buildSkipPolicy`；行号订正；基线测试数订正为实测（core 87/总 185）；`-pl nop-batch` 命令**保留**（Maven 4 下确覆盖 16 模块）；Goals 口径改「9 完整 + 2 半修」；C8-010 concurrency 归一注明双保险；C8-007 增 `getProcessItemCount` 同型错源 residual；W-09 载体重命名 `SimpleMeterRegistry.find(...)` |

## Purpose

关闭 R4-12 批 11 项 P2 工单：读侧断点/游标/翻页语义（C7-005/006/007/008/009）、状态机恢复面（C7-012）、过载可观测性三面接线（C8-004/007/008）、dispatch 参数面（C8-009）、护栏双标（C8-010）。

## Current Baseline

- **判级**：11 项全 P2（merge-adjudication 单元内裁定）；C7-008 含 O(N²/2B) 量化口径。
- **依赖**：硬依赖 8.22（plan 2516）已 done——C8-001 Guard（`BatchTaskBuilder.buildLoader` `loadBatchSize<=0` fail-fast）与 `adjustBatchSize` 钳位 WARN 均在案，**C8-009 参数化前置满足**。
- **PA 判定**：11 成员站点均在 `nop-batch/*`（非 Protected Area）。越界面三处显式不做：C8-008 的平台池 gauge 重绑、C8-009 的 DSL（`nop-xdefs/batch.xdef`）暴露、C7-012 的 DSL 键。三者均属 plan-first Protected Area / schema 面，登记 Deferred。
- **live 基线事实**（2026-11-09 亲读 + draft review R1 校正）：
  - `BatchSkipPolicy.shouldSkip` :42-53 `maxSkipCount > 0` 前置 → 0/负值均=无限；`SkipConsumeHelper` :36-45 只 LOG.info + 计数，无死信；`batch.xdef:48` `maxSkipCount="long"` 无约束。
  - `ModelBasedBatchTaskBuilderFactory`：`:191` 任务级 saveState → `builder.stateStore`；`:431` **loader 级** `loaderModel.getSaveState()`；`:433-442` 仅 file/excel 接收 saveState。`completedIndex` 推进点全仓仅 `ResourceRecordLoaderProvider:302` 与 `EtlTaskStateStore:115`。真实受害配置 `nop-batch-sys/.../non-broadcast-consumer.batch.xml:7` 的 `saveState="true"` 在 **task 元素**上。
  - `OrmQueryBatchLoaderProvider.LoaderState.lastEntity` :42-46 为活实体引用；`load` :131-145 在 `batchLoadProps` 之后写。`dao.findNext` 经 `DaoQueryHelper.appendCursorCondition`（:383-405）按 `orm_propValue(col.getPropId())` 取排序值；`appendPkOrderBy`（:364-375）追加 **column name**。`dao.newEntity()`（`OrmEntityDao:187-189`）→ `OrmTemplateImpl.newEntity` 走 `runInSession`，实体为 TRANSIENT、不入 cache、不 flush。
  - `JdbcPageBatchLoaderProvider.load` :85-98 offset 线性推进；`setup` :74-77 只收 context（无 batchSize）。
  - `JdbcBatchLoaderProvider` :189 任务级 `state.close()`、:255-289 打开连接、`load` :291-307 无 try；结果集读取经 `JdbcDataSet` 逐处 `translate(...)` 成 `NopException`（`JdbcDataSet:100-127`），`DialectSQLExceptionTranslator` 产出 `transient-connection-fail`/`recoverable-data-access`/`data-access` 三类码。
  - `DaoBatchStateStore.startExistingTask` :135-140 KILLED 无条件拒绝；`tryMarkTaskRunning` 谓词 `gt(taskStatus, RUNNING)`（:243-259）**已含 KILLED=60**；`saveTaskState` 有 execCount fencing 条件 UPDATE；`getTaskStatus` `cancel()` 缺省 reason=kill → KILLED。
  - `BatchTaskMetricsImpl` 构造器即按 tag 建全部 meter（:45-53），无法延后重绑；`BatchTask.executeAsync` 中 `beginTask()` 早于 `setTaskName`；`getLoadItemCount`（:157-159）与 `getProcessItemCount`（:162-164）**同型错源**（Timer 次数而非条数）；`RetryConsumeHelper.checkRetry` 只 `incRetryCount()`。
  - `PartitionDispatchQueue` 对外仅 `getCapacity/isFinished/getMaxLockQueueCountPerThread`；`count`/`semaphore` private；queue 为两 Provider `setup` 局部变量。
  - 两 Provider `loadBatchMultiplyFactor=30`/`maxLockQueueCountPerThread=100` 公共 setter 全仓零外部调用方；`batch.xdef:80-85` 不暴露。
  - `DefaultBizEntityExporter` :41-45 钳制只判 `>` 上界；`BizExportTaskBuilder:48` 直传且**未设 builder.taskName**。
  - **`ThreadPoolConfig.enableMetrics` 已被消费**：`ExecutorHelper.registerMetrics`（`isEnableMetrics()`）在 `newThreadPoolExecutor`（含 cached 分支）与 `newScheduledExecutor` 绑定 `ExecutorServiceMetrics`。审计 C8-008「死标志」前提**不成立**。
  - exp 自建池：`ExportDbTool` `DefaultThreadPoolExecutor.newExecutor("export-db", config.getThreadCount(), 10000)` / `ImportDbTool` 同形；`threadCount` 由 `export-db.xdef:11` / `import-db.xdef:7` 的 `!int=0` 直给、**无上界**。

## Goals

- 11 工单收口：**9 项完整 Fix**（C7-005/006/007/008/009、C8-004/007/009/010）+ **2 项半修**（C7-012 仅程序化 API；C8-008 仅 nop-batch 侧水位出口），半修的另半边各登记 Deferred。
- 每条工单 ≥1 **可被回归捕获**的载体（禁止"接口存在即完成"）。
- C8-008 → C8-004 → C8-007 三层观测面按序一次接线。
- E5 owner doc 落账。

## Non-Goals

- **不改 `nop-kernel/nop-commons`**（ThreadPoolConfig/ExecutorHelper/DefaultThreadPoolExecutor，框架核心 plan-first）→ C8-008 平台面 Deferred。
- **不改 `nop-kernel/nop-xdefs/batch.xdef`**（平台 schema plan-first）→ C8-009 DSL 暴露、C7-012 DSL 键均 Deferred。
- 不做 orm/jdbc loader 的 cursor 断点恢复实现（C7-006 的「补齐」分支）→ 本批做装配期可观测 + 契约显式化。
- 不做 jdbc 页模式 keyset 重写（C7-008 建议①）。
- 不改 orm 模型 / `_` 生成物（C7-012 的 xmeta 收紧 Deferred）。
- 不做 R4-13 起的成员。

## Scope

### In Scope

- `nop-batch-core`：`BatchSkipPolicy`、`consumer/SkipConsumeHelper`、`loader/PartitionDispatchQueue`、`loader/AsyncFetchPartitionDispatchLoaderProvider`、`loader/PartitionDispatchLoaderProvider`、`BatchDispatchConfig`、`metrics/BatchTaskMetricsImpl`、`consumer/RetryConsumeHelper`、`impl/BatchTask`、`impl/BatchTaskContextImpl`、`IBatchTaskContext`、`BatchConfigs`/`BatchErrors`
- `nop-batch-orm`：`loader/OrmQueryBatchLoaderProvider`
- `nop-batch-jdbc`：`loader/JdbcPageBatchLoaderProvider`、`loader/JdbcBatchLoaderProvider`
- `nop-batch-dsl`：`manager/ModelBasedBatchTaskBuilderFactory`、`model/BatchSkipPolicyModel`
- `nop-batch-dao`：`store/DaoBatchStateStore`
- `nop-batch-biz`：`importexport/DefaultBizEntityExporter`、`importexport/BizExportTaskBuilder`
- `nop-batch-exp`：`ExportDbTool`、`ImportDbTool`（threadCount 上界）
- 测试树 + `docs-for-ai/03-modules/nop-batch.md`

### Out Of Scope

Non-Goals 所列全部。

## Execution Plan

### Phase 1 - 设计裁定

Status: planned
Targets: 本 plan 的裁定表（模块内小批次，无跨模块契约变更，不单建设计文档）

- Item Types: `Decision`

- [ ] 裁定表落 Plan（见下「裁定 A」）——本批无跨模块契约变更（三处越界面均显式不做），按 roadmap「轻量通道」不单建 design 文档
- [ ] `No owner-doc update required`（本 Phase；owner doc 归 Phase 3）
- [ ] `ai-dev/logs/` 已更新

**裁定 A（每工单唯一形态）**

| 工单 | 唯一形态 |
|---|---|
| C7-005 | `BatchSkipPolicy.setMaxSkipCount` 负值 fail-fast（新增 `ERR_BATCH_SKIP_COUNT_INVALID`），**0 = 显式无限**（javadoc + owner doc）；`BatchSkipPolicyModel.buildSkipPolicy` 在 `maxSkipCount<=0` 时打装配期 WARN `nop.warn.batch.skip-policy-unbounded`；`BatchTask.onTaskComplete` 终态对 `skipItemCount>0` 打汇总 WARN `nop.warn.batch.task-skipped-items`（使跳过可审计）。死信账本判 out-of-scope improvement（改 `saveProcessed` 语义会破坏 historyStore 幂等面），Deferred |
| C7-006 | **任务级**判定：`ModelBasedBatchTaskBuilderFactory.newTaskBuilder`（可见 task 级与 loader 级两轴）对 `batchTaskModel.getSaveState()==TRUE && reader ∈ {jdbc,orm,generator}` 打 WARN `nop.warn.batch.save-state-unsupported-reader`（声明面钉，不阻断装配）；owner doc「断点续传」节显式化适用范围。cursor 恢复实现 Deferred |
| C7-007 | `OrmQueryBatchLoaderProvider` 锚点改**排序键值快照**：`LoaderState` 增 `cursor`（复用同一 detached 实体，每次仅覆盖值，避免每 chunk `newEntity`）；快照列 = `query.getOrderBy()` 列名 + PK 列名（`dao.getPkColumnNames()`；Nop 约定列名=属性名，plan 显式声明该假设）；`load` 时不再保留活实体引用。detached 实体为 TRANSIENT（不入 cache、不 flush、不触发 DB 装载）。NULL 排序值仍由平台 keyset 契约抛 `ERR_ORM_CURSOR_SORT_VALUE_NULL`。owner doc 增「orderBy 列运行期不可变 + NULL 排序列确定性失败」 |
| C7-008 | `JdbcPageBatchLoaderProvider.load(...)` 内 `batchSize<=0` 类型化 fail-fast（loader 先于 `BatchChunkProcessor.adjustBatchSize` 钳位，故必须在 load 侧拦）；首次 `load` 打一次性 WARN `nop.warn.batch.jdbc-page-offset-paging`（offset 语义 + 静态快照假设）；owner doc 增适用面 + O(N²/2B)。keyset 重写 Deferred |
| C7-009 | `JdbcBatchLoaderProvider.LoaderState` 增 `broken` + `brokenError`；`load` 外层 try 包住 `RecordInputImpls.defaultReadBatch`，捕获 `NopException` 且 `ErrorCode ∈ {ERR_SQL_TRANSIENT_CONNECTION_FAIL, ERR_SQL_RECOVERABLE_DATA_ACCESS, ERR_SQL_DATA_ACCESS}` → `close()` + `broken=true` 存原始异常后**重新抛出**；后续 `load` 若 `broken` 则抛同一异常（不返回空列表）。**判定次序：broken 检查先于 `isClosed()` 早退**（EOF 面仍返回空列表）。owner doc 增「streaming loader 任务时长 ≤ 连接最大空闲/存活时长」 |
| C7-012 | `IBatchTaskContext` 增 `allowRestartIfKilled`（`Boolean`，**仅程序化 API**，无 DSL 键）；`BatchTask` 构造链透传；`DaoBatchStateStore.startExistingTask` 的 `:135` KILLED 早退在 `allowRestartIfKilled==TRUE` 时**旁路**（其余闸门 startLimit/COMPLETED/stale 语义不动；fencing 由 execCount 递增天然承接，**不改 WHERE 谓词**——`taskStatus>RUNNING` 已含 KILLED=60）。owner doc 状态机表增「KILLED 撤离门（程序化）」。xmeta 收紧 + DSL 面 Deferred |
| C8-004 | **单漏斗**：metrics 解析/创建移入 `BatchTask.executeAsync` 的 `setTaskName/setTaskVersion` **之后**（`context.getMetrics()==null` 时自建 `BatchTaskMetricsImpl(GlobalMeterRegistry.instance(), null, null, taskName)`），一处覆盖 DSL/exp/biz 三链；`BizExportTaskBuilder` 补 `builder.taskName(bizObjName)`。删除「jobName 取 taskName / 延后重绑」表述（构造器即绑 tag，不可延后） |
| C8-007 | `RetryConsumeHelper.checkRetry` 在 `incRetryCount()` 同点补 `metrics.retry(1)`（null 安全短路，与 `SkipConsumeHelper` 同型）；`BatchTaskMetricsImpl.getLoadItemCount()` 改 `loadCounter.count()`。**与 C8-004 同一 plan 项绑定**。`getProcessItemCount()` 同型错源登记 residual（C10-010 面） |
| C8-008 | `PartitionDispatchQueue` 增只读水位出口 `getCount()`/`getAvailablePermits()`/`getPartitionCount()`（`lock` 内快照）；两 Provider `setup` 增加 Gauge 注册：tag = `task.name`(+`taskId`)，capacity 只作 description/值域说明不作 tag；**`context.onAfterComplete(err -> registry.remove(gauge))` 注销**（防 meter-id 复用陈旧读数 + 逐任务累积）。平台池重绑面 Deferred（事实已改写，见 Deferred） |
| C8-009 | `BatchDispatchConfig` 增 `loadBatchMultiplyFactor`/`maxLockQueueCountPerThread`（缺省 0 = **未配置哨兵**）；`BatchTaskBuilder.buildLoader` 仅 `>0` 时 setter 透传，否则保留 Provider 缺省 30/100；Guard（`>=1`，`ERR_BATCH_DISPATCH_FACTOR_INVALID`）落 Provider setter 与 `PartitionDispatchQueue.setMaxLockQueueCountPerThread`。DSL 暴露 Deferred |
| C8-010 | `DefaultBizEntityExporter` 钳制补**下界**（`batchSize<=0 → maxBatchSize`；`concurrency<=0 → 1`，与 `BatchTask.normalizeConcurrency` 为**双保险**）；**exp 半边**：`ExportDbTool`/`ImportDbTool` 对 `config.getThreadCount()` 增上界（读 `nop.batch.max-concurrency-cap`）与 `threadCount × concurrencyPerTable` 乘积校验，超界 fail-fast。owner doc 增两套 executor 形态对照表 + 30× 内存公式 |

Exit Criteria:

- [ ] 裁定表在案且每项唯一形态
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 11 工单实现 + 回归测试

Status: planned
Targets: nop-batch-core / -orm / -jdbc / -dsl / -dao / -biz / -exp + 测试树

- Item Types: `Fix`（11）、`Proof`

**执行顺序约束（batch-proposal §二 R4-12 exit criteria + draft review R1 M-5）**：观测三面必须 **W-09(C8-008) 先于 W-07(C8-004)**，再 W-08(C8-007)；其余工单无相互依赖。

- [ ] W-01 C7-005：skipPolicy 负值 fail-fast + 无限语义显式声明 + 终态跳过汇总 WARN
- [ ] W-02 C7-006：任务级 saveState 非 file/excel 装配期 WARN + 契约显式化
- [ ] W-03 C7-007：orm keyset 排序键值快照锚点（脱离活实体）
- [ ] W-04 C7-008：jdbc 页模式 `load` 侧 batchSize Guard + offset 语义声明 WARN
- [ ] W-05 C7-009：jdbc 流式连接失效 broken 态（按 ErrorCode 判定，抛错不静默 EOF）
- [ ] W-06 C7-012：KILLED 撤离门 `allowRestartIfKilled`（程序化）+ fencing 承接
- [ ] **W-09 C8-008（先）**：dispatch 队列水位出口 + Gauge（含注销）
- [ ] **W-07 C8-004**：metrics 单漏斗（`executeAsync` 内 setTaskName 之后）
- [ ] **W-08 C8-007**：metrics.retry 数据源 + getLoadItemCount 错源修正
- [ ] W-10 C8-009：两旋钮 Java API 入口（0=未配置哨兵）+ Guard
- [ ] W-11 C8-010：biz 钳制下界 + exp threadCount 上界/乘积校验 + 双标对照表
- [ ] 回归测试：既有 nop-batch 全量 + 下表新增钉
- [ ] **无静默跳过**（rule 24）：WARN/抛错全部显式；C7-009 broken 态不得退化为空列表

**工单 × 载体表（draft review R1 B-1）**

| 工单 | 载体 | 类型 |
|---|---|---|
| C7-005 | `TestCraR412BatchPins#pin01`（负值 fail-fast 错误码）/ `pin01b`（0=无限不抛，仅 WARN）/ `pin01c`（终态汇总 WARN 触发路径可达） | 行为钉 |
| C7-006 | `pin02`（任务级 saveState + orm/jdbc/generator → WARN 判定函数返回 true；file/excel → false） | 结构/契约钉 |
| C7-007 | `TestOrmQueryBatchLoaderCursorSnapshot#pin03a`（排序列被就地变更后游标不漂移）/ `pin03b`（快照实体为 TRANSIENT、不 flush） | 行为钉（fake `IDaoProvider`/`IEntityDao`） |
| C7-008 | `pin04`（`load(batchSize<=0)` fail-fast）/ `pin04b`（首次 load WARN 一次性，第二次不重复） | 行为钉 |
| C7-009 | `TestJdbcBatchLoaderBrokenState#pin05a`（注入 `transient-connection-fail` → load 抛错而非空列表）/ `pin05b`（broken 后兄弟线程再次 load 抛同一异常）/ `pin05c`（EOF 关闭仍返回空列表，不被误判 broken） | 行为钉（Proxy/桩 dialect） |
| C7-012 | `TestDaoBatchStateStore#pin06a`（KILLED + allowRestart=true → 可启动）/ `pin06b`（allowRestart=false/null → 仍响亮拒绝）/ `pin06c`（启动后 execCount 递增，旧实例写被 fencing 丢弃） | 行为钉（既有基建） |
| C8-008 | `TestPartitionDispatchQueueWatermark#pin07a`（`getCount/getAvailablePermits/getPartitionCount` 三出口读数正确）/ `pin07b`（Gauge 注册后在 `SimpleMeterRegistry.find(...)` 可读）/ `pin07c`（任务完成注销后不再可查） | 行为钉（`SimpleMeterRegistry`） |
| C8-004 | `pin08a`（`executeAsync` 后 `SimpleMeterRegistry.find("nop.batch.task")` 的 `task.name` tag = 真实任务名，非 default）/ `pin08b`（`context.getMetrics()!=null` 时尊重外部注入，不覆盖） | 行为钉（端到端） |
| C8-007 | `pin09a`（重试发生后 `metrics.getRetryItemCount()` > 0，与 `context.getRetryItemCount()` 一致）/ `pin09b`（`getLoadItemCount()` = 条数而非 chunk 次数） | 行为钉 |
| C8-009 | `pin10a`（`BatchDispatchConfig` 未设 → Provider 保留 30/100）/ `pin10b`（设 0/负 → setter Guard fail-fast）/ `pin10c`（设正值 → 透传给 Provider 并生效） | 行为钉 |
| C8-010 | `pin11a`（biz `batchSize=0/负 → maxBatchSize`）/ `pin11b`（biz `concurrency<=0 → 1`）/ `pin11c`（exp `threadCount` 超 cap fail-fast）/ `pin11d`（乘积超界 fail-fast） | 行为钉 |

Exit Criteria:

- [ ] 11 工单全部 landed（9 完整 + 2 半修，半修半边各有 Deferred 登记）
- [ ] 载体表 11/11 落地，每条形为钉均真实断言目标行为（非仅"不抛异常"）
- [ ] 关键项先红后绿或变异论证（至少抽 3 条：C7-007 快照、C7-009 broken、C8-007 retry 计数）
- [ ] `./mvnw test -pl nop-batch` BUILD SUCCESS
- [ ] 零触碰核对：`nop-kernel`（nop-commons + nop-xdefs）零改动；orm 模型/`_` 生成物零改动
- [ ] **端到端验证**：一次真实任务执行（DSL 装配）能读到 `nop.batch.task` Timer 与 `nop.batch.dispatch.backlog` Gauge，且 `task.name` tag 为真实任务名
- [ ] **接线验证**：C8-008→C8-004→C8-007 三层在同一次执行中同时可见
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: planned
Targets: nop-batch 全量 + owner doc

- Item Types: `Proof`

- [ ] E3 分解通道：`./mvnw test -pl nop-batch` BUILD SUCCESS + 逐模块签名记录（预填实测基线：core 87/dao 23/gen 2/orm 9/web 1/jdbc 5/dsl 25/exp 25/biz 3/sys 6）
- [ ] owner doc 落账：`nop-batch.md`（断点续传适用范围 / orderBy 不可变契约 / jdbc 页模式与 streaming 时长约束 / 状态机 KILLED 撤离门 / metrics 与 dispatch 水位观测项 / 两套 executor 对照表 + 30× 公式）
- [ ] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--module nop-batch --severity high` EXIT=0
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [ ] E3 验收且逐条登记
- [ ] owner doc 落账核对
- [ ] 双门禁 EXIT=0 登记
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.52 行、cra-index P2-G23 束行

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）：逐条核对 Exit Criteria / Closure Gates / Anti-Hollow / 三处越界面 Deferred 边界
- [ ] cra-index 台账回填：P2-G23 束行（R4-12 半边收敛注，与 R4-11 半边合并为整行 closed）+ roadmap 8.52 → done
- [ ] 双门禁 + Closure Evidence 写入本 plan

Exit Criteria:

- [ ] closure audit 证据写入 Closure 段
- [ ] cra-index + roadmap 在案
- [ ] 双门禁 EXIT=0

## Closure Gates

- [ ] 11 工单全部收口（9 完整 Fix + 2 半修，半修细则见 Deferred）
- [ ] 每工单 ≥1 可被回归捕获的载体
- [ ] `./mvnw test -pl nop-batch` BUILD SUCCESS
- [ ] owner doc 落账完成
- [ ] scan-hollow nop-batch `--severity high` EXIT=0
- [ ] 不存在被静默降级的 in-scope live defect（三处半修为 PA/schema 未授权 successor，非降级）
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**
- [ ] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [ ] cra-index P2-G23 收敛注 + roadmap 8.52 → done

## Deferred But Adjudicated

### C7-012 DSL 键面（`allowRestartIfKilled` 无法在 `.batch.xml` 配置）

- Classification: `out-of-scope improvement`（nop-xdefs plan-first）
- Why Not Blocking Closure: 引擎侧吸收态（KILLED 永久不可重启）已解——程序化 API 可设置；CRUD 残留由 execCount fencing 兜底（僵尸写者状态写被丢弃并 ERROR 留痕）。DSL 暴露需 xdef 变更 + codegen 再生。
- Successor Required: `yes`
- Successor Path: R-G 治理批（schema 变更登记）或 batch DSL 增强批次。

### C7-012 xmeta `taskStatus` 写保护收紧

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 收紧 `updatable="false"` 属 meta/xmeta 契约变更，影响既有运维 CRUD 恢复入口与 UI；本批已提供专用撤离门，缺陷面（杀任务即变砖）闭合。
- Successor Required: `yes`
- Successor Path: R-G 治理批（meta 契约收敛）。

### C8-008 平台池面（重绑缺失 + 无 batch 维度标签）

- Classification: `out-of-scope improvement`（nop-kernel/nop-commons plan-first）
- **前提已按 live 代码订正**（draft review R1 M-3）：平台池**已有** `ExecutorServiceMetrics` 绑定（`ExecutorHelper.registerMetrics`，开关 `ThreadPoolConfig.isEnableMetrics`），审计「死标志」结论不成立。残余面仅两项：替换 `MeterRegistry` 后既有绑定不重绑、以及池 gauge 无 batch 任务维度标签。
- Why Not Blocking Closure: 残余面是标签粒度与 registry 生命周期问题，不影响 batch 侧积压可观测（本批 W-09 已交付任务级水位）；属平台设施跨模块改进。
- Successor Required: `yes`
- Successor Path: R4-13/R4-14（nop-kernel plan-first）或 R-G。

### C8-009 DSL 暴露面

- Classification: `out-of-scope improvement`（nop-xdefs plan-first）
- Why Not Blocking Closure: 本批以 `BatchDispatchConfig` 提供程序化入口并加 Guard，消除「能配但没入口」的误解；DSL 暴露需 schema 变更 + codegen 再生。
- Successor Required: `yes`
- Successor Path: R-G 治理批或 batch DSL 增强批次。

### C7-006 cursor 断点恢复实现（orm/jdbc/gen loader 的 completedIndex 推进）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需新增 loader 状态序列化契约（orm 锚点/PK 快照、jdbc offset/range 持久化），属能力新增；本批已把「saveState 对非 file/excel reader 无续传能力」做成装配期可观测 WARN + owner doc 契约声明，消除了「空承诺」误导。
- Successor Required: `yes`
- Successor Path: 独立批次（batch 断点续传能力增强）。

### C7-008 jdbc 页模式 keyset 重写

- Classification: `optimization candidate`
- Why Not Blocking Closure: 需 SQL 层可参数化游标契约（对任意用户 SQL 无法自动改写）；页模式当前语义（静态快照表）已由 owner doc 显式声明 + 首次 load WARN；退化面只影响大表长任务。
- Successor Required: `yes`
- Successor Path: 独立批次（jdbc-reader keyset 形态）。

### C7-005 跳过条目死信账本

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需新增「跳过即记录」账本语义（`IBatchRecordHistoryStore.saveProcessed` 现契约「仅成功写入」，改语义会破坏 filterProcessed 幂等面）；本批已使跳过规模在任务终态显式汇总并在 owner doc 声明为已知边界。
- Successor Required: `yes`
- Successor Path: 独立批次（batch 死信/跳过账本能力）。

## Non-Blocking Follow-ups

- `PartitionDispatchQueue.removePartition` 生产调用方为零（死代码）——本轮不清理，登记待后续死代码治理。
- `BatchTaskMetricsImpl.getProcessItemCount()` 与已修的 `getLoadItemCount()` 为**同型错源**（返回 Timer 次数而非条目数）；本批只修 load 侧，process 侧留 residual（C10-010 面），避免"部分失真观测面"被误认为已全修。
- 两个派发 provider（`PartitionDispatchLoaderProvider` / `AsyncFetchPartitionDispatchLoaderProvider`）不转发 `hasChunkCompaction()`（2545 R4 m-2）；owner doc 应声明「仅由 `BatchTaskBuilder` 构造，不得手工作为 task loader 注入」。

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent fresh session）
- Audit Session: （session ID）
- Evidence:
  - （每条 Exit Criterion / Closure Gate 的 PASS/FAIL）
  - `check-plan-checklist --strict` 退出码
  - Anti-Hollow 结果 + scan-hollow 退出码
  - Deferred 分类检查

Follow-up:

- （仅 non-blocking）
