# 2546 CRA Phase 8.52 — R4-12 1.7 读侧/护栏束 b（G23 后半，11 工单）

> Plan Status: active
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.52（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :223，Status=**todo**；Owner Doc=`docs-for-ai/03-modules/nop-batch.md`；Deps=「batch-proposal.md §二 R4-12（11 项）；硬依赖 8.22——已 done（plan 2516），硬依赖解除」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 R4-12 行（11 finding ID / 模块 nop-batch / 验证命令 `./mvnw test -pl nop-batch -am` / PA=无 / exit criteria 增补：「C8-007 与 C8-004 绑定、**C8-008 为 C8-004 前置**（三面一次接线）」）+ §二.5 G23 组行「22 | R4-11 + R4-12 | 拆分 11/11」；成员发现详情 = `ai-dev/audits/2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency/`（C7-long-running.md C7-005/C7-006/C7-007/C7-008/C7-009；C7-round2.md C7-012；C8-backpressure.md C8-004；C8-round2.md C8-007/C8-008/C8-009/C8-010）
> Related: plan 2545（R4-11，**completed @ 2026-11-09，closure audit R4 APPROVED**）为本批同模块前批——本批**复用其教训**：接口存在 ≠ 行为完成、每条工单必须有可被回归捕获的载体、owner doc 与代码必须一致；plan 2516（R2-13）关闭 C8-001 Guard 与 C8-002 钳位共享位点，本批 C8-009 的「C8-001 Guard 先于参数化」次序前置已满足；C10-005/006/007/008/009/012 为本批验收前置

**Draft Review 记录**

- R1（fresh session 独立评审，2026-11-09）：**REJECT**，6 Blocker + 7 Major + 10 Minor。全部随本版落盘（见下方「Draft Review R1 修订清单」）。
- R2（fresh session 独立评审，2026-11-09）：**REJECT**，4 Blocker（B1 新增 / B2 R1-B-6 未真闭环 / B3 新增 / B4 新增）+ 7 Major + 6 Minor。全部随本版落盘（见下方「Draft Review R2 修订清单」）。
- R3（fresh session，聚焦 8 判定点）：**REJECT**，1 Blocker（pin08a meter 选错）+ 5 Major + 5 Minor。全部随本版落盘（见下方「Draft Review R3 修订清单」）。
- R4（fresh session，收敛轮）：**PASS-with-revisions**，0 Blocker + 2 Major（pin05d 与裁定 A 矛盾、ImportDbTool 断言缺载体）+ 3 Minor。已全部订正（pin05d 改为「SQLState 轨命中 + 反向断言兜底码不得置 broken」、补 `pin08d`、Exit Criteria 口径改 8+3、pin03b 升级计数钉、调用点计数订正）。
- **Draft review 结论：通过，进入实施。**

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

### Draft Review R2 修订清单（逐条已闭环）

| R2 项 | 结论与修订 |
|---|---|
| **B1** C7-009 白名单漏 `ERR_SQL_UNCATEGORIZED_FAILURE`（PG 连接失效永不计 broken） | 判定改为**双轨**：① `NopException.getCause()` 中取 `SQLException`，按 SQLState 前缀 `08` 或 `instanceof SQLRecoverableException/SQLTransientConnectionException/SQLNonTransientConnectionException` 判连接级失效；② 同时保留 `DaoErrors` 方言映射码。载体新增 **PG 形态** `pin05d`（`SQLException("connection closed","08006")`）。FQN 全部写全（`io.nop.dao.DaoErrors`）。**（本条后被 R3 M-1 收窄：删除兜底码 `ERR_SQL_UNCATEGORIZED_FAILURE`，正文裁定 A 以 R3 为准）** |
| **B2** C8-004 只搬「创建」未搬 `beginTask` → task Timer 恒 0 | 明确三件事：(a) 创建点在 `setTaskName/setTaskVersion` **之后**并立即 `context.setMetrics(created)`；(b) `beginTask()` 随之移到创建之后（外部已注入 metrics 分支保持原语义：注入者在 `executeAsync` 前设好，创建逻辑跳过，`beginTask` 照旧调用）；(c) `pin08a` **断言 `timer.count() > 0`**（不是"能 find 到"），`pin08b` 用计数 stub `IBatchTaskMetrics` 断言 `beginTask/endTask` 落在注入实例上 |
| **B3** C7-012 透传链缺 `BatchTaskContextImpl` 与 `BatchTaskBuilder` | 五节点写全：`IBatchTaskContext`（新方法给 **default `null`**，不破坏外部实现）+ `BatchTaskContextImpl` 字段+getter/setter + `BatchTask` 字段/构造/**新增重载**（保留既有 11 参构造器签名不变，2 处既有测试零改）+ `executeAsync` 缺省注入（对齐 `allowStartIfComplete` 模式）+ `BatchTaskBuilder` 字段/setter/构造调用 + `DaoBatchStateStore` 旁路 |
| **B4** C8-008 Gauge tag 含 `taskId` → 无 stateStore 时 null NPE；同名并发互删 | **每执行唯一键**：`exec.id` tag = `taskId`（非空）→ `taskKey`（非空）→ `"anon-" + System.identityHashCode(queue)`；与 `task.name` 组成唯一 meter id。注销按注册时持有的 `Meter` 引用 `registry.remove(meter)`（因 id 唯一，不会误删他人）。`pin07b` 覆盖 **taskId==null** 形态 |
| **M1** C7-005 死信账本被整体降级，违反 rule 15/16 | 明确**范围裁定**（非"改不动所以不做"）：本批把跳过做成**可观测**（WARN + 计数 + 终态汇总），死信账本为独立能力（新表/新契约）→ `out-of-scope improvement` + **触发条款**（生产出现跳过数据丢失导致业务对账失败 → 升 P1 立即立项）+ successor |
| **M2** C8-004 metrics 默认全任务生效、无开关、无基数裁定 | 新增配置键 `nop.batch.metrics.enabled`（缺省 `true`，保持本批交付可观测）；owner doc 记「metric 基数上界 = 不同 taskName 数 × **14** meter（10 Timer + 4 Counter）」；与 C8-008 registry 生命周期裁定合并叙述 |
| **M3** C7-007 「detached」表述不实；列名/属性名误导 | 改为「`newEntity()` 在 `newLoaderState` **创建一次并复用**；实体为 **TRANSIENT**（`orm_attach` 指向 setup 时 session，可能已关闭；置脏为无害 side-effect；**不入 cache、不 flush**）」；「列名」改「**属性名（prop name）**」，来源 `dao.getPkColumnNames()`（返回 prop 名）。`pin03b` 改可观测断言：`orm_state()==TRANSIENT` + `IDaoProvider` 零 INSERT/UPDATE 调用计数 |
| **M4** C7-006 pin02 不可实现 | 抽出**纯判定** `isSaveStateUnsupportedReader(Boolean taskSaveState, BatchLoaderModel loader)`（静态、无 bean 依赖），装配处调用后打 WARN；pin02 直测该函数。裁定 `bean`/`provider`/`adapter` 形态同样 WARN（无法判定压实能力，保守告警） |
| **M5** C8-010 exp 半边缺落点与表级 override | 新增**纯静态** `validateExecutorConfig(ExportDbConfig/ImportDbConfig)`（无 DB），在 `execute()` 资源段之前调用；`product = max(各表 getConcurrency, 缺省 concurrencyPerTable, 1) × max(threadCount,1)`；`threadCount<=1`（Sync）短路；cap 复用 `CFG_BATCH_MAX_CONCURRENCY_CAP`；`concurrencyPerTable == null` 归 1 |
| **M6** C8-009 「0=哨兵」与 pin10b 矛盾 + 负值静默丢弃 | 二分裁定落地：`BatchDispatchConfig.setX(int)` 内 **`<0` → 类型化 fail-fast（`ERR_BATCH_DISPATCH_FACTOR_INVALID`）；`==0` → 哨兵；`>0` → 值**；builder 仅 `>0` 透传。pin10b 改为「config 设负值 → fail-fast」「Provider/Queue setter 设 0 → fail-fast」两条，不再自相矛盾 |
| **M7** 端到端 Exit Criteria 不可定位 | 裁定 A 写入：meter 名 `nop.batch.task`（Timer）与 `nop.batch.dispatch.backlog`（Gauge，value=积压条数，tags=`task.name`+`exec.id`）；新增测试资产 `_vfs/test/batch/cra412-dispatch-metrics.batch.xml`（dispatcher + `provider` loader）、以及「首败后成功的 consumer」制造一次 retry |
| m-1..m-6 | 行号订正（6 处）；基线数求和 `186`（core 87/dao 23/gen 2/orm 9/web 1/jdbc 5/dsl 25/exp 25/biz 3/sys 6）；Phase 2 增 `No owner-doc update required`（归 Phase 3）；`getProcessItemCount` follow-up 补 Why Not Blocking；C7-008 一次性 WARN 状态放 `LoaderState`；pin03b/pin11c/d 类型列诚实标「结构钉」 |

### Draft Review R3 修订清单（逐条已闭环）

| R3 项 | 结论与修订 |
|---|---|
| **B-1** `pin08a`/端到端断言选错 meter（`nop.batch.task` 有 SUCCESS/FAILURE 两个 Timer，`Search.timer()`=findAny 可能取到 count=0 的 failure Timer） | 断言一律写全 tag：`registry.find("nop.batch.task").tags("task.name", <unique>, "status", "SUCCESS").timer().count() > 0`；且每次用**唯一 taskName**（`GlobalMeterRegistry` 是进程级单例，防跨用例残留）。Phase 2 端到端 Exit Criteria 同改 |
| **M-1** C7-009 轨道②白名单过宽（收 `ERR_SQL_UNCATEGORIZED_FAILURE` 会让"磁盘满/业务 SQL 错误"永久 poison loader，丧失 retry） | 轨道② 收窄为 `{ERR_SQL_TRANSIENT_CONNECTION_FAIL, ERR_SQL_RECOVERABLE_DATA_ACCESS}`；`ERR_SQL_DATA_ACCESS` **仅当** `cause instanceof SQLNonTransientConnectionException` 时计入；**删除** `ERR_SQL_UNCATEGORIZED_FAILURE`（PG 连接失效由轨道① SQLState `08` 承接）。`pin05d` 改断言「SQLState 轨命中」 |
| **M-2** C8-004 伪代码漏首行 `IBatchTaskMetrics metrics = context.getMetrics();` | 伪代码补首行；注入分支下 `beginTask()` 才成立 |
| **M-3** `pin02` 无法落在 core（`BatchLoaderModel` 在 dsl，依赖单向 core←dsl） | 载体表明确 `pin02` 落 **`nop-batch-dsl`** 侧 `TestCraR412DslPins`；`isSaveStateUnsupportedReader(Boolean, BatchLoaderModel)` 放 dsl 模块 |
| **M-4** `ImportDbTool` 未设 `builder.taskName` → import 表任务全落 `task.name=default` | W-07(C8-004) 增 `ImportDbTool.runTask` 补 `builder.taskName(tableConfig.getName())`；载体表加断言 |
| **M-5** C7-005 口径矛盾（Goals 计为完整 Fix，Deferred 又推迟死信账本半边） | Goals 改「**8 完整 + 3 半修**」（C7-005/C7-012/C8-008 为半修）；Closure Gates 同步为三处半修并列出 C7-005 死信账本半边 |
| 5 Minor | SQLState 判定显式 `!= null`；meter 基数 **14**（10 Timer + 4 Counter）；构造器既有调用点 **3 处/3 文件**（`BatchTaskBuilder:333` + 测试 2 处/2 文件）；`pin07c` 按 `Search.gauge()` 语义写；`pin03b` 改「`newEntity()` 调用 1 次 + 零 save/flush」计数钉 |

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

- 11 工单收口：**8 项完整 Fix**（C7-006/007/008/009、C8-004/007/009/010）+ **3 项半修**（C7-005：跳过可观测已修、死信账本 deferred；C7-012：仅程序化 API；C8-008：仅 nop-batch 侧水位出口），三处半修的另半边各登记 Deferred。
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

Status: completed
Targets: 本 plan 的裁定表（模块内小批次，无跨模块契约变更，不单建设计文档）

- Item Types: `Decision`

- [x] 裁定表落 Plan（见下「裁定 A」）——本批无跨模块契约变更（三处越界面均显式不做），按 roadmap「轻量通道」不单建 design 文档
- [x] `No owner-doc update required`（本 Phase；owner doc 归 Phase 3）
- [x] `ai-dev/logs/` 已更新

**裁定 A（每工单唯一形态）**

| 工单 | 唯一形态 |
|---|---|
| C7-005 | `BatchSkipPolicy.setMaxSkipCount` 负值 fail-fast（新增 `ERR_BATCH_SKIP_COUNT_INVALID`），**0 = 显式无限**（javadoc + owner doc）；`BatchSkipPolicyModel.buildSkipPolicy` 在 `maxSkipCount<=0` 时打装配期 WARN `nop.warn.batch.skip-policy-unbounded`；`BatchTask.onTaskComplete` 终态对 `skipItemCount>0` 打汇总 WARN `nop.warn.batch.task-skipped-items`（使跳过可审计）。死信账本判 out-of-scope improvement（改 `saveProcessed` 语义会破坏 historyStore 幂等面），Deferred |
| C7-006 | **任务级**判定：抽出**纯静态** `isSaveStateUnsupportedReader(Boolean taskSaveState, BatchLoaderModel loader)`（无 bean 依赖，可直测），`ModelBasedBatchTaskBuilderFactory.newTaskBuilder` 调用后在 `saveState==TRUE` 且 reader ∈ {jdbc,orm,generator}（含 `bean`/`provider`/`adapter` 形态——无法判定压实能力时保守告警）打 WARN `nop.warn.batch.save-state-unsupported-reader`（声明面钉，不阻断装配）；owner doc「断点续传」节显式化适用范围。cursor 恢复实现 Deferred |
| C7-007 | `OrmQueryBatchLoaderProvider` 锚点改**排序键值快照**：`LoaderState` 增 `cursor`（`dao.newEntity()` 在 `newLoaderState` **创建一次并复用**，每次仅覆盖值）；快照键 = `query.getOrderBy()` 列名 + PK 列名（来源 `dao.getPkColumnNames()`，**返回 prop 名**，与 `appendCursorCmp` 的 `field.getName()` 同域；Nop 约定 prop 名=列名，plan 显式声明该假设）；`load` 时不再保留活实体引用。该实体为 **TRANSIENT**（`orm_attach` 指向 setup 时 session、可能已关闭；置脏为无害 side-effect；**不入 cache、不 flush**）。NULL 排序值仍由平台 keyset 契约抛 `ERR_ORM_CURSOR_SORT_VALUE_NULL`。owner doc 增「orderBy 列运行期不可变 + NULL 排序列确定性失败」 |
| C7-008 | `JdbcPageBatchLoaderProvider.load(...)` 内 `batchSize<=0` 类型化 fail-fast（loader 先于 `BatchChunkProcessor.adjustBatchSize` 钳位，故必须在 load 侧拦）；`LoaderState` 级一次性布尔打 WARN `nop.warn.batch.jdbc-page-offset-paging`（offset 语义 + 静态快照假设；状态放 `LoaderState` 而非 Provider 字段，避免多任务串扰）；owner doc 增适用面 + O(N²/2B)。keyset 重写 Deferred |
| C7-009 | `JdbcBatchLoaderProvider.LoaderState` 增 `broken` + `brokenError`；`load` 外层 try 包住 `RecordInputImpls.defaultReadBatch`，**双轨判定**连接级失效：① `NopException.getCause()` 链中取 `SQLException`，SQLState 前缀 `08` 或 `instanceof SQLRecoverableException/SQLTransientConnectionException/SQLNonTransientConnectionException`；② 或 `ErrorCode ∈ {io.nop.dao.DaoErrors.ERR_SQL_TRANSIENT_CONNECTION_FAIL, ERR_SQL_RECOVERABLE_DATA_ACCESS}`，或 `ErrorCode==ERR_SQL_DATA_ACCESS` **且** `cause instanceof SQLNonTransientConnectionException`。**不收 `ERR_SQL_UNCATEGORIZED_FAILURE`**——它是所有未映射 SQL 错误的兜底，收它会把「磁盘满/业务 SQL 错误」永久 poison 掉重试机会；PG 连接失效的 `08006` 由轨道①的 SQLState 承接。SQLState 判定须显式 `sqlState != null && sqlState.startsWith("08")`。（`DialectSQLExceptionTranslator.newError` 确以 `new JdbcException(errorCode, cause)` 保留原始 `SQLException` 于 cause 链。）→ `close()` + `broken=true` 存原始异常后**重新抛出**；后续 `load` 若 `broken` 则抛同一异常（不返回空列表）。**判定次序：broken 检查先于 `isClosed()` 早退**（EOF 面仍返回空列表）。owner doc 增「streaming loader 任务时长 ≤ 连接最大空闲/存活时长」 |
| C7-012 | **五节点全链**：① `IBatchTaskContext` 增 `default Boolean getAllowRestartIfKilled(){return null;}` + `default void setAllowRestartIfKilled(Boolean v){}`（default 不破坏外部实现）；② `BatchTaskContextImpl` 字段 + 覆写 getter/setter；③ `BatchTask` 字段 + **新增重载构造器**（既有 11 参签名不变，2 处既有测试零改）+ `executeAsync` 缺省注入（对齐 `allowStartIfComplete` 模式）；④ `BatchTaskBuilder` 字段 + `@PropertySetter` + 构造调用；⑤ `DaoBatchStateStore.startExistingTask` 的 KILLED 早退在 `TRUE` 时**旁路**（其余闸门 startLimit/COMPLETED/stale 语义不动；fencing 由 `incExecCount()` + 条件 UPDATE 承接，**不改 WHERE 谓词**——`taskStatus>RUNNING` 已含 KILLED=60）。**仅程序化 API，无 DSL 键**。owner doc 状态机表增「KILLED 撤离门（程序化）」。xmeta 收紧 + DSL 面 Deferred |
| C8-004 | **单漏斗（含 beginTask）**：`BatchTask.executeAsync` 把 `metrics` 解析/创建**与 `beginTask()` 一并**移到 `setTaskName/setTaskVersion` **之后**：`IBatchTaskMetrics metrics = context.getMetrics(); if (metrics == null && CFG_BATCH_METRICS_ENABLED.get()) { metrics = new BatchTaskMetricsImpl(GlobalMeterRegistry.instance(), null, null, context.getTaskName()); context.setMetrics(metrics); } meter = metrics == null ? null : metrics.beginTask();`——一处覆盖 DSL/exp/biz 三链；外部已注入 metrics 时跳过创建但仍 `beginTask()`。`onTaskComplete` 读 `context.getMetrics()` 故拿到同一实例。新增配置键 `nop.batch.metrics.enabled`（缺省 true）。`BizExportTaskBuilder` 补 `builder.taskName(bizObjName)`；**`ImportDbTool.runTask` 补 `builder.taskName(tableConfig.getName())`**（否则 import 表任务全落 `task.name=default`） |
| C8-007 | `RetryConsumeHelper.checkRetry` 在 `incRetryCount()` 同点补 `metrics.retry(1)`（null 安全短路，与 `SkipConsumeHelper` 同型）；`BatchTaskMetricsImpl.getLoadItemCount()` 改 `loadCounter.count()`。**与 C8-004 同一 plan 项绑定**。`getProcessItemCount()` 同型错源登记 residual（C10-010 面） |
| C8-008 | `PartitionDispatchQueue` 增只读水位出口 `getCount()`/`getAvailablePermits()`/`getPartitionCount()`（`lock` 内快照）；`AsyncFetchPartitionDispatchLoaderProvider.setup` 注册 Gauge `nop.batch.dispatch.backlog`（value=积压条数）与 `nop.batch.dispatch.available-permits`。**meter id 唯一化**：tags = `task.name` + `exec.id`，其中 `exec.id` = `taskId`（非空）→ 否则 `taskKey`（非空）→ 否则 `"anon-"+System.identityHashCode(queue)`（**避免 Tag.of null NPE 与同名并发互删**）。注册时持有 `Meter` 引用，`context.onAfterComplete(err -> registry.remove(meter))` 精确注销。capacity 只作 description。平台池重绑面 Deferred |
| C8-009 | `BatchDispatchConfig` 增 `loadBatchMultiplyFactor`/`maxLockQueueCountPerThread`（int 缺省 0 = 未配置哨兵）。**语义二分**：`setX(int)` 内 `<0` → 类型化 fail-fast（`ERR_BATCH_DISPATCH_FACTOR_INVALID`）；`==0` → 哨兵；`>0` → 赋值。`BatchTaskBuilder.buildLoader` 仅 `>0` 时透传给 Provider（需把 `new Provider(...).setup()` 拆为两步）；Provider/`PartitionDispatchQueue` setter 另有 `>=1` Guard。DSL 暴露 Deferred |
| C8-010 | `DefaultBizEntityExporter` 钳制补**下界**（`batchSize<=0 → maxBatchSize`；`concurrency<=0 → 1`，与 `BatchTask.normalizeConcurrency` 为**双保险**）；**exp 半边**：新增**纯静态** `validateExecutorConfig(...)`（无 DB）在两个 `execute()` 资源段之前调用——`product = max(各表 getConcurrency, concurrencyPerTable 非空则用之, 1) × max(threadCount,1)`，`threadCount<=1`（Sync）短路，超 `CFG_BATCH_MAX_CONCURRENCY_CAP` fail-fast（`ERR_BATCH_CONCURRENCY_EXCEEDS_CAP`）。owner doc 增两套 executor 对照表 + 30× 公式 |

Exit Criteria:

- [x] 裁定表在案且每项唯一形态
- [x] `ai-dev/logs/` 已更新

### Phase 2 - 11 工单实现 + 回归测试

Status: completed
Targets: nop-batch-core / -orm / -jdbc / -dsl / -dao / -biz / -exp + 测试树

- Item Types: `Fix`（11）、`Proof`

**执行顺序约束（batch-proposal §二 R4-12 exit criteria + draft review R1 M-5）**：观测三面必须 **W-09(C8-008) 先于 W-07(C8-004)**，再 W-08(C8-007)；其余工单无相互依赖。

- [x] W-01 C7-005：skipPolicy 负值 fail-fast + 无限语义显式声明 + 终态跳过汇总 WARN
- [x] W-02 C7-006：任务级 saveState 非 file/excel 装配期 WARN + 契约显式化
- [x] W-03 C7-007：orm keyset 排序键值快照锚点（脱离活实体）
- [x] W-04 C7-008：jdbc 页模式 `load` 侧 batchSize Guard + offset 语义声明 WARN
- [x] W-05 C7-009：jdbc 流式连接失效 broken 态（按 ErrorCode 判定，抛错不静默 EOF）
- [x] W-06 C7-012：KILLED 撤离门 `allowRestartIfKilled`（程序化）+ fencing 承接
- [x] **W-09 C8-008（先）**：dispatch 队列水位出口 + Gauge（含注销）
- [x] **W-07 C8-004**：metrics 单漏斗（`executeAsync` 内 setTaskName 之后）
- [x] **W-08 C8-007**：metrics.retry 数据源 + getLoadItemCount 错源修正
- [x] W-10 C8-009：两旋钮 Java API 入口（0=未配置哨兵）+ Guard
- [x] W-11 C8-010：biz 钳制下界 + exp threadCount 上界/乘积校验 + 双标对照表
- [x] 回归测试：既有 nop-batch 全量 + 下表新增钉
- [x] **无静默跳过**（rule 24）：WARN/抛错全部显式；C7-009 broken 态不得退化为空列表

**工单 × 载体表（draft review R1 B-1）**

| 工单 | 载体 | 类型 |
|---|---|---|
| C7-005 | `TestCraR412BatchPins#pin01`（负值 fail-fast 错误码）/ `pin01b`（0=无限不抛，仅 WARN）/ `pin01c`（终态汇总 WARN 触发路径可达） | 行为钉 |
| C7-006 | **`nop-batch-dsl` 侧 `TestCraR412DslPins#pin02`**（直测 `isSaveStateUnsupportedReader(Boolean, BatchLoaderModel)`：orm/jdbc/generator/bean/provider/adapter → true；file/excel → false）——`BatchLoaderModel` 在 dsl，core 测试树不可 import | 结构钉 |
| C7-007 | `TestOrmQueryBatchLoaderCursorSnapshot#pin03a`（排序列被就地变更后游标不漂移）/ `pin03b`（**`newEntity()` 调用恰 1 次**（复用不重建）+ **零 save/flush 调用**——计数钉，比 `orm_state()` 自证式断言更强） | pin03a 行为钉；pin03b **结构计数钉**（诚实登记） |
| C7-008 | `pin04`（`load(batchSize<=0)` fail-fast）/ `pin04b`（首次 load WARN 一次性，第二次不重复） | 行为钉 |
| C7-009 | `TestJdbcBatchLoaderBrokenState#pin05a`（注入 `transient-connection-fail` → load 抛错而非空列表）/ `pin05b`（broken 后兄弟线程再次 load 抛同一异常）/ `pin05c`（EOF 关闭仍返回空列表，不被误判 broken）/ **`pin05d`（PG 形态：`SQLException("connection closed","08006")` → 经**轨道① SQLState `08`** 命中 broken；**并反向断言**未映射的业务 SQL 错误（如 `22012 division by zero` → `uncategorized-failure`）**不得**置 broken）** | 行为钉（Proxy/桩 dialect） |
| C7-012 | `TestDaoBatchStateStore#pin06a`（KILLED + allowRestart=true → 可启动）/ `pin06b`（allowRestart=false/null → 仍响亮拒绝）/ `pin06c`（启动后 execCount 递增，旧实例写被 fencing 丢弃）/ **`pin06d`（`BatchTaskBuilder` 设值经重载构造器到 context 的透传链）** | 行为钉（既有基建） |
| C8-008 | `TestPartitionDispatchQueueWatermark#pin07a`（三出口读数正确）/ `pin07b`（Gauge 可读，**含 taskId==null 形态不 NPE**）/ `pin07c`（注销后不再可查）/ **`pin07d`（同名并发两任务 meter id 互不相同，互不覆盖）** | 行为钉（`SimpleMeterRegistry`） |
| C8-004 | **`pin08d`（`TestCraR412ImportTaskNameWiring` 两腿：① 源码契约 `builder.taskName(tableConfig.getName())` 存在；② `builder.taskName` 确实透传到 context.getTaskName()——完整 DB 端到端不可离线的限制已如实标注）**；`pin08a`（**唯一 taskName** + `registry.find("nop.batch.task").tags("task.name", <unique>, "status", "SUCCESS").timer().count() > 0`——必须带 `status=SUCCESS`，否则 `Search.timer()`=findAny 会命中 count=0 的 failure Timer）/ `pin08b`（外部注入计数 stub 时 `beginTask/endTask` 落在注入实例上）/ `pin08c`（`nop.batch.metrics.enabled=false` 时不注册） | 行为钉（端到端；唯一 taskName 防单例残留） |
| C8-007 | `pin09a`（重试发生后 `metrics.getRetryItemCount()` > 0，与 `context.getRetryItemCount()` 一致）/ `pin09b`（`getLoadItemCount()` = 条数而非 chunk 次数） | 行为钉 |
| C8-009 | `pin10a`（未设 → Provider 保留 30/100）/ `pin10b`（config 设负值 → fail-fast；Provider/Queue setter 设 0 → fail-fast）/ `pin10c`（设正值 → 透传并生效） | 行为钉 |
| C8-010 | `pin11a`（biz `batchSize=0/负 → maxBatchSize`）/ `pin11b`（biz `concurrency<=0 → 1`）/ `pin11c`（`validateExecutorConfig`：threadCount 超 cap fail-fast）/ `pin11d`（乘积超界 fail-fast） | pin11a/b 行为钉；pin11c/d **结构钉**（纯静态判定，DB 无关） |

Exit Criteria:

- [x] 11 工单全部 landed（**8 完整 + 3 半修**：C7-005 死信账本 / C7-012 DSL 键 / C8-008 平台池面，各有 Deferred 登记），口径与 Goals / Closure Gates 一致
- [x] 载体表 11/11 落地，每条形为钉均真实断言目标行为（非仅"不抛异常"）
- [x] 关键项先红后绿或变异论证（至少抽 3 条：C7-007 快照、C7-009 broken、C8-007 retry 计数）
- [x] `./mvnw test -pl nop-batch` BUILD SUCCESS
- [x] 零触碰核对：`nop-kernel`（nop-commons + nop-xdefs）零改动；orm 模型/`_` 生成物零改动
- [x] **端到端验证**：新增资产 `_vfs/test/batch/cra412-dispatch-metrics.batch.xml`（dispatcher + provider loader）跑一次真实 DSL 任务 → `TestCraR412DispatchMetricsE2E` 断言 `nop.batch.chunk` Timer（带 `status` tag）count>0 且 `ctx.getMetrics()!=null`（C8-004 单漏斗生效）。Gauge 的注册/读数/注销由 core 钉 `pin07b/pin07c/pin07d` 直接驱动**生产** `registerWatermarkGauges` 验证。
  - **勘误 E-2546-1（诚实登记）**：三层「同一次执行全部可见」中的 **C8-007 重试记账未能在 dispatch 路径端到端复现**——dispatch 下 processor 与 retry 链的挂接使首败不进入 `retryConsume`（实测 `retryCount=0` 且无 `retry-consume-fail` 日志）。C8-007 的回归保护由 core 单元钉 `pin09a`（直接驱动 `RetryConsumeHelper.checkRetry`）承载，属**单元级而非端到端级**覆盖。该差距已在此登记，不冒充端到端。
- [x] **接线验证（分层如实）**：C8-004（Timer）经 `TestCraR412DispatchMetricsE2E` 端到端验证；C8-008 的 Gauge 注册/读数/**生产注销**经 `TestCraR412DispatchGaugeLifecycle`（驱动 `setup()`+`context.complete()` 生产路径）与 `pin07b/07c/07d` 验证；C8-007 的 retry 记账经 `pin09a` 单元级验证（**勘误 E-2546-1：dispatch 路径端到端不可达**）。三层不宣称"同一次执行全部可见"
- [x] **无静默跳过**（rule 24）：C8-009 负值必须 fail-fast 而非被 `>0` 过滤静默忽略；C7-009 broken 不得退化为空列表
- [x] `No owner-doc update required`（本 Phase；owner doc 归 Phase 3）
- [x] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: completed
Targets: nop-batch 全量 + owner doc

- Item Types: `Proof`

- [x] E3 分解通道：`./mvnw test -pl nop-batch` BUILD SUCCESS + 逐模块签名记录（实测基线：core 103/dao 27/gen 2/orm 11/jdbc 15/dsl 30/exp 32/biz 7/sys 6 = **233**）
- [x] owner doc 落账：`nop-batch.md`（断点续传适用范围 / orderBy 不可变契约 / jdbc 页模式与 streaming 时长约束 / 状态机 KILLED 撤离门 / metrics 与 dispatch 水位观测项 / 两套 executor 对照表 + 30× 公式）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--module nop-batch --severity high` EXIT=0
- [x] `ai-dev/logs/` 已更新

Exit Criteria:

- [x] E3 验收且逐条登记
- [x] owner doc 落账核对
- [x] 双门禁 EXIT=0 登记
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

- [ ] 11 工单全部收口（8 完整 Fix + 3 半修：C7-005 死信账本 / C7-012 DSL 键 / C8-008 平台池面）
- [ ] 每工单 ≥1 可被回归捕获的载体
- [ ] `./mvnw test -pl nop-batch` BUILD SUCCESS
- [ ] owner doc 落账完成
- [ ] scan-hollow nop-batch `--severity high` EXIT=0
- [ ] 不存在被静默降级的 in-scope live defect（三处半修均有范围裁定 + 触发条款 + successor 指针）
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

### C7-005 跳过条目死信账本（半修半边）

- Classification: `out-of-scope improvement`
- **范围裁定（非"改不动所以不做"）**：本批职责边界 = 把「跳过」从**完全不可见**变为**可观测**（负值 fail-fast + 无限语义显式声明 + 终态汇总 WARN + 计数）。死信账本是**独立能力**（新表 / 新契约），不属于"让跳过可见"这一修复目标；`saveProcessed` 的"仅成功写入"契约是 historyStore 幂等面的基础，改它会破坏既有语义，故必须新建独立账本而非改既有接口。
- Why Not Blocking Closure: 已确认 live defect（跳过无持久化记录）的**可观测性半边**已由本批闭合（运维可从终态 WARN + skipItemCount 得知规模）；剩余半边（逐条持久化死信）是新增能力，不影响 supported baseline 的数据正确性（at-least-once 仍成立）。
- **触发条款（升 P1）**：生产出现「跳过数据丢失导致业务对账失败」或「需要按记录追溯被跳过的行」→ 升 P1 立即立项死信账本。
- Successor Required: `yes`
- Successor Path: 独立批次（batch 死信/跳过账本能力）。

## Non-Blocking Follow-ups

- `PartitionDispatchQueue.removePartition` 生产调用方为零（死代码）——本轮不清理，登记待后续死代码治理。
- `BatchTaskMetricsImpl.getProcessItemCount()` 与已修的 `getLoadItemCount()` 为**同型错源**（返回 Timer 次数而非条目数）。**Why Not Blocking Closure**：正确修复需扩 `IBatchTaskMetrics.endProcess(meter, count, success)` 的签名（公共接口 API 变更，影响全部实现者），属独立契约变更；本批只修 load 侧，process 侧留 residual（C10-010 面），并在 owner doc 显式声明该指标语义偏差，避免"部分失真观测面"被误认为已全修。
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
