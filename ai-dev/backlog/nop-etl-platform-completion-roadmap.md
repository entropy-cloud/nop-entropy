# nop ETL 平台补全 Roadmap — 从分析缺口到可运维数据集成面

> Last updated: 2026-10-18（**R1+R2 双轮独立审查收敛**：R1 7 项 Major 全修，R2 验证全 OK、无新 Blocker/Major，2 处 Minor 机械修复；全 WI 未启动）
> 位置：按仓库 roadmap 惯例存放于 ai-dev/backlog/。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护。
> Sources:
> - `ai-dev/analysis/2026-10/2026-10-18-nop-platform-vs-etl-toolchains.md`（**主输入**：Kettle/Airflow/dbt 平台级对比 + G1–G10 缺口）
> - `ai-dev/analysis/2026-10/2026-10-18-nop-batch-vs-oss-batch-frameworks.md`（批处理引擎对比 + P0 接线裁定 + P1/P2 建议）
> - nop-job 控制面探索（2026-10-18，结论并入两份分析的修订版 P0/G2）
> - 现有 owner doc：`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/02-core-guides/batch-dsl.md`、`docs-for-ai/03-modules/nop-job.md`
> - 需求来源：2026-10-18 会话——分析 nop-batch 对比开源批处理；下载 Kettle/Airflow/dbt 对比 nop 平台整体；用户追问"批任务运行控制不能集成 nop-job 吗"后裁定接线方向；随后"写一个 roadmap 补充缺失的功能"

## Purpose

本 roadmap 编排把 Nop 平台的数据集成面从「引擎能力完备但运维/产品面断裂」推进到「可运维、可编排、可治理的平台级 ETL 面」，补齐与 Kettle/Airflow/dbt 对比暴露的缺口。

覆盖五条主线：

1. **运行控制接线 nop-job**（P0）：不在 nop-batch 自建第二套控制面——补 `BatchTaskRunner.cancel`、运行中 context 注册表、状态关联列与双状态 SSOT。
2. **图形化 ETL 编排起步**（P0）：AMIS 表单式 batch 管道编排，不追 Spoon SWT。
3. **数据面补齐**（P1）：`NopMetaPipeline` 执行器、通用导入占位实现、exp Web/API 化、backfill、metrics 自动接线、聚合步骤。
4. **治理增强**（P2）：变更传播选择器、增量策略模板、数据目录文档站、语义层评估。
5. **收口**：端到端验收 + owner doc 修正（含已立案的 batch×job 集成节漂移 P2-5.1-004）。

**范围硬边界**（改动须先改本节）：

- **不在 nop-batch 自建 kill/resume API**：运行控制复用 nop-job 的 `cancelFire`/`rerunFire`/`IJobInvoker.cancelAsync`（用户 2026-10-18 裁定）；只补接线断点。
- **不抄 Kettle 行级 RowSet 流模型**：chunk 批量模型在事务/幂等/续传上更优；行级低延迟场景归 nop-stream。
- **不自建独立调度中心**：cron/分片/coordinator 归 nop-job（调度域专文已裁定）。
- **不重建 dbt 式 manifest/血缘**：nop-metadata 列级血缘已结构性更强；只接线 `NopMetaPipeline`，不重造血缘。
- **不追平 Airflow 109 providers 连接器生态**：连接器按需增长，聚焦业务模型同栈差异化。
- **不做 SWT 桌面客户端**：图形化走 Web/AMIS 路径。
- **ORM 模型变更（`model/*.orm.xml`）走 plan-first Protected Area**：WI2 的 `jobTaskId` 加列须独立 plan + owner doc + 回归测试。
- **`nop-*-api` 与 `IBatchTaskRunner` 等公开 SPI 变更走 plan-first**：跨模块公共 API。
- **不做 Flink/Spark 式数据并行引擎**：问题域正交（既有分析已排除）。
- **语义层只做评估决策，不在本 roadmap 实施**（WI14 输出 go/no-go，实施另立 roadmap）。

不含实现细节。每个 WI 由独立执行计划承载（`ai-dev/plans/00-plan-authoring-and-execution-guide.md`）；roadmap 就绪后可经 mission-driver draft 生成 mission config 驱动（当前未立项 mission）。

## Evidence Ledger（证据底账，2026-10-18 闭合）

> 按 `ai-dev/lessons/19-doc-ssot-and-evidence-first.md`：roadmap 细节前先固化载荷事实与既有裁定。每条带 file:line。

1. **nop-job 控制面已全套**：`cancelFire`（级联 task 置 CANCELED + best-effort 通知）、`rerunFire`（RECOVERY fire + `sourceFireId`）、`pauseSchedule`/`resumeSchedule` 均已有——`nop-job-service/.../NopJobFireBizModel.java:106-208`、`NopJobScheduleBizModel.java:61-157`；UI/权限按钮已就位（`nop-job.action-auth.xml:54-60`、`NopJobFire.view.xml:69-80`）。
2. **反向取消钩子现成**：`IJobInvoker.cancelAsync`（`IJobInvoker.java:30`）；`BeanMethodJobInvoker.cancelAsync` 按方法名反射调用（默认 `cancel`，`BeanMethodJobInvoker.java:52,89`）。
3. **batch 已被 job 启动**：`*.job.yaml → LocalJobConfigLoader → BeanMethodJobInvoker.invokeAsync → BatchTaskRunner.executeAsync`（`nop-batch-sys/.../sys-event-batch-consumer.job.yaml:7-12`、`BatchTaskRunner.java:35-50`）。
4. **断点 1**：`BatchTaskRunner` 无 `cancel(Map)` 方法；缺失时 `BeanMethodJobInvoker` 反射失败被 `:92-95` 吞掉并**静默假成功**返回 TRUE。
5. **断点 2**：运行中 `IBatchTaskContext` 无注册表——`executeAsync:38-50` context 即用即弃；仅 ThreadLocal `BatchTaskGlobals.java:9-21`。
6. **断点 3**：`NopBatchTask` 无 `jobTaskId` 关联列（`nop-batch/model/nop-batch.orm.xml:40-110`）；`nop_job_task.taskStatus` 与 `nop_batch_task.taskStatus` 两套无对账。
7. **batch KILLED 语义**：KILLED 永久禁启（`DaoBatchStateStore.java:108-113`）；job `isRecoverable` 含 CANCELED（`JobTaskStateMachine.java:133-140`）——resume/rerun 映射须消解冲突。
8. **软取消缺口**：`IJobExecutionContext.getCancelToken()` 默认 null（`IJobExecutionContext.java:22-24`）；worker 执行中不回读 DB（`JobWorkerScannerImpl.java:309-382`）。
9. **图形化缺口**：`nop-batch-web` 仅 AMIS CRUD 页（`nop-batch/nop-batch-web/src/main/resources/_vfs/nop/batch/pages/NopBatchTask/_gen/_NopBatchTask.view.xml` 仅列展示，无触发/kill 动作）；对照 Kettle Spoon `TransGraph.java`（4860 行 SWT 手绘 + hop 拖拽直接模型绑定）。
10. **`NopMetaPipeline` 名存实亡**：`NopMetaPipelineBizModel extends CrudBizModel` 无额外动作（`nop-metadata-service/.../NopMetaPipelineBizModel.java:12-15`）；无执行器、不被调度。
11. **通用导入占位**：`DefaultBizEntityImporter.importFile` 抛 `UnsupportedOperationException`（`nop-batch-biz/.../DefaultBizEntityImporter.java:20-21`）。
12. **exp 仅 CLI**：`ExportDbTool.java:69`/`ImportDbTool.java:72`；唯一生产依赖是 `nop-runner/nop-cli-core/pom.xml:103`。
13. **backfill 缺口**：nop-task/nop-job 无按区间回填；仅 nop-stream 有测试级 RECALCULATE（对照 Airflow backfill）。
14. **metrics 未接线**：`BatchTaskMetricsImpl` 实现存在（`BatchTaskMetricsImpl.java:21`）但需用户手工 `context.setMetrics`；无自动挂载。
15. **文档漂移已立案**：`docs-for-ai/03-modules/nop-batch.md:191-202,237` 对 batch×job 集成描述与 live 不符（审计立案 P2-5.1-004，`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/5.1-docs-consistency-audit.md:73,222-243`）。
16. **既有裁定**：`NopBatchTaskState` 三件套删除已有审计裁定 G7-07-01（`ai-dev/audits/2026-09/.../07-job-task-batch-retry-tcc.md`）；DB 源行级续传已由用户 2026-10-18 撤回（状态驱动任务重跑天然过滤）。
17. **本地 vs 分布式路径分裂**：`.job.yaml` → `LocalJobScheduler`（内存态、重启丢失）；分布式 → `NopJobSchedule` + coordinator；`beanMethod` executorKind 不在 dict `test/rpc/rpcPoll`（`nop-job.orm.xml:39-43`）。集群级 kill/resume 需落 DB 路径。

## Work Item Status

> 唯一动态状态块。勾选 = 独立 closure audit 通过（完成判定见 Cross-Cutting）；未勾选 = `todo`（无计划），起草 plan 并通过 draft review 后标注 `planned`。WI 编号全文件递增不重排；**deps 是唯一并行屏障**。每个 WI 以独立 plan 承载。

### M0 — 契约裁定与文档基线

- [ ] WI0 双状态 SSOT 裁定 + batch×job 集成节文档修正：裁定 `nop_job_task.taskStatus` 与 `nop_batch_task.taskStatus` 的权威归属与对账策略（对照 job 侧 `JobScheduleCounterReconciler`）；**契约草案强制运行期注册 key = taskPath/taskKey**（jobTaskId 列仅用于状态对账/反查，不作为 cancel 路径 key——`BatchTaskRunner.executeAsync` 当前仅收 taskPath+params、无 jobTaskId 来源）；修正 P2-5.1-004 三文件漂移——`docs-for-ai/03-modules/nop-batch.md` 集成节（`sys-event-batch-consumer.job.yaml` 链路 + 反向取消语义 + 本地/分布式路径差异）、`docs-for-ai/03-modules/nop-sys.md`（事件队列节两 bullet + 锚点表，live :98/:105/:133）、`docs-for-ai/04-reference/source-anchors.md` SYS-001（:97）；在 ai-dev/design/nop-batch（或新建子目录）固化「运行控制接线契约」草案（context 注册表 key=taskPath/taskKey、cancel 方法签名、jobTaskId 语义=对账专用、resume/rerun 映射表）。（Deliverable: SSOT 裁定 + 三文件 owner doc 修正 + 设计契约草案；deps: 无；Item Type: Decision + Proof）

### M1 — 运行控制接线 nop-job（P0）

- [ ] WI1 BatchTaskRunner.cancel + 运行中 context 注册表：给 `BatchTaskRunner` 增加 `cancel(Map params)` 方法（对齐 `BeanMethodJobInvoker` 反射契约，默认方法名 `cancel`）；新增运行中 `IBatchTaskContext` 注册表（**key 按 WI0 契约强制为 taskPath/taskKey**，与 jobTaskId 解耦——WI2 的 jobTaskId 列仅对账）；`executeAsync` 注册、完成/异常注销；cancel 命中后调 `context.cancel(reason)`（复用 `ICancellable.CANCEL_REASON_KILL`），消除 `:92-95` 静默假成功；**长查询场景 loader.load 前检查 `context.isCancelled()`**（异步 fetch 主循环响应性）；覆盖本地 `.job.yaml` 路径与分布式路径的注册/查找一致性。（Deliverable: cancel 链路 + 注册表 + load 前 cancel 检查 + 测试；deps: WI0；Item Type: Feature + Proof）
- [ ] WI2 NopBatchTask.jobTaskId 关联列 + 状态对齐（**ORM plan-first**）：`nop-batch/model/nop-batch.orm.xml` 增加 `jobTaskId`（可空，兼容无 job 触发的直调场景）+ 必要索引；`DaoBatchStateStore` 写路径填列；按 WI0 SSOT 实现 job taskStatus ↔ batch taskStatus 对齐或对账器；独立 ORM plan + 回归测试。（Deliverable: ORM 变更 + 写路径 + 对账/对齐 + 测试；deps: WI0；Item Type: Feature）
- [ ] WI3 resume/rerun 语义映射 + 管理端入口：明确 job `rerunFire` → 新 fire → 新 batch 运行时，stateStore 的重启语义（同 `(taskName,taskKey)` 原地重启 vs 清 completedIndex 重跑 + historyStore 幂等兜底）；消解 batch KILLED 永久禁启与 job `isRecoverable` 的冲突（KILLED 映射为不可 rerun 还是允许 RECOVERY 覆盖）；管理端复用 `nop-job-web` 既有 cancel/rerun 按钮（不建 `nop-batch-web` 第二套入口）；`allowStartIfComplete` 开关接入。（Deliverable: 语义映射 + 冲突消解 + 管理端路径 + 测试；deps: WI1、WI2；Item Type: Decision + Feature）

### M2 — 图形化 ETL 编排起步（P0）

- [ ] WI4 AMIS 表单式 batch 管道编排：在 nop-batch-web（或复用 nop-task 工作流设计器）提供「选 step 类型→配参数→连线」的表单式编排，产出标准 `*.batch.xml`；支持 loader/processor/consumer 参数编辑、事务档位选择、并发配置；只读预览与一键执行（**经 WI1 取消链路可控**——执行入口复用 BatchTaskRunner，注册表已就绪故 kill 可达）；定位「非开发者可配一条 batch 管道」，不做 Spoon 式自由画布。（Deliverable: 编排表单 + DSL 产出 + 执行接线（可取消）+ 测试；deps: WI0、WI1；Item Type: Feature）

### M3 — 数据面补齐（P1）

- [ ] WI5 NopMetaPipeline 执行器：给 `NopMetaPipeline` 实体接线——`schedule` 字段（或等价 cron）触发 nop-job；执行委托 nop-task（内含 batch step）或直接 BatchTaskRunner；先让「名存实亡」实体活过来，实现 dbt 式「模型编排执行」的最小可用形态。**pipeline↔nop-task 边界裁定作为本 WI 的 Decision 交付物**（pipeline 是轻量数据流定义内嵌 batch 引用，还是完整 task flow 的别名；不并入 WI0——WI0 只覆盖运行控制四件）。（Deliverable: 边界裁定 + 执行器 + 调度接线 + 测试；deps: WI0；Item Type: Decision + Feature）
- [ ] WI6 DefaultBizEntityImporter 实现：实现通用实体导入（ORM 实体 + 批量 load + 事务档位）；与 `nop-batch-exp` CLI 收敛到同一 SPI；覆盖「配置权限表但不生效」式的占位陷阱（调用即抛 UnsupportedOperationException 现状）。（Deliverable: 导入实现 + 测试；deps: 无；Item Type: Feature）
- [ ] WI7 nop-batch-exp Web/API 化：把 ExportDbTool/ImportDbTool 包一层 BizModel/HTTP，让导入导出可在管理端触发；复用 WI6 SPI；与 WI4 编排入口协同（导出/导入可作为管道步骤或独立操作）。（Deliverable: Web/API 入口 + 测试；deps: WI6；Item Type: Feature）
- [ ] WI8 backfill 回填语义：nop-task 增加「按参数区间重复实例化」的 operator（或 nop-job 增加 backfill API）；对齐 Airflow backfill 心智（历史区间补跑、跳过已成功、失败续跑）；与 nop-job 分片/并发上限交互明确。（Deliverable: backfill 语义 + 测试；deps: WI0；Item Type: Feature）
- [ ] WI9 metrics 自动接线：`BatchTaskRunner`/DSL builder 检测 IoC 中的 `MeterRegistry`，默认挂 `BatchTaskMetricsImpl`（实现已存在）；标签 job.name/task.name 就绪；消除「实现有、需手工 setMetrics」现状。（Deliverable: 自动挂载 + 测试；deps: 无；Item Type: Feature）
- [ ] WI10 reduce/aggregate 步骤：DSL 增加 `aggregator`（任务结束时对全部 chunk 输出或 context 变量做汇总，可委托 xpl）；补上 snail-job/PowerJob MapReduce 的「汇」半边；保持「数据面轻量、跨节点 shuffle 不做」边界。（Deliverable: aggregator DSL + 测试；deps: 无；Item Type: Feature）

### M4 — 治理增强（P2）

- [ ] WI11 state:modified 变更传播选择器：基于实体定义/依赖图的变更传播，选择受影响的 pipeline/task 重跑（借鉴 dbt `state:modified` 选择器）；变更源范围（ORM 模型/batch DSL/pipeline 定义）与传播粒度在 plan 中裁定。（Deliverable: 选择器 + 测试；deps: WI5；Item Type: Feature）
- [ ] WI12 增量策略模板：把 dbt 收敛的 append/merge/delete_insert/insert_overwrite/microbatch 模式沉淀为 batch processor 的策略模板（SQL 片段库/宏），降低手写增量 SQL 成本；不改 chunk 引擎语义。（Deliverable: 策略模板库 + 测试；deps: 无；Item Type: Feature）
- [ ] WI13 数据目录/文档站：从 nop-metadata 血缘 + 质量规则自动生成静态文档站（对齐 dbt docs 心智）；含表/列血缘图、质量规则摘要、pipeline 依赖视图。（Deliverable: 文档站生成 + 样例输出；deps: WI5；Item Type: Feature）
- [ ] WI14 语义层评估（决策 WI，不实施）：评估是否值得做 MetricFlow 式指标→SQL 编译，或先用 nop-metadata 语义实体承载、执行走 nop-task；输出 go/no-go + 若 go 的最小范围建议（另立 roadmap）。（Deliverable: 评估结论 + 建议；deps: 无；Item Type: Decision）
- [ ] WI15 NopBatchTaskState 三件套清理：删除孤儿死代码（手写 entity + `_gen` + BizModel）+ `_templates` 残留；已有审计裁定 G7-07-01，防下次 codegen 清理编译断裂；同步清理 `BatchDslConstants.FILE_TYPE_BATCH_TASK` 双命名问题。（Deliverable: 清理 + 编译验证；deps: 无；Item Type: Cleanup）

### M5 — 端到端验收与文档收口

- [ ] WI16 全链路验收与文档收口：job 触发 batch → 运行中 kill（WI1/WI3）→ resume/rerun → 管理端可见（WI3）→ 图形化配置一条管道（WI4）→ pipeline 执行（WI5）→ 导入导出（WI6/WI7）→ backfill（WI8）→ metrics（WI9）端到端验收；更新 owner doc——`docs-for-ai/03-modules/nop-batch.md` 能力矩阵与集成节、**`docs-for-ai/02-core-guides/batch-dsl.md` 两处补强：① provider loader 线程安全契约警示框（对齐 `IBatchLoaderProvider.java:26` javadoc）；② 集群 partitionRange 无任务重派/宕机=整任务失败重启语义写明**、`docs-for-ai/03-modules/nop-job.md` 若涉及；`docs-for-ai/INDEX.md` 路由、`docs-for-ai/01-repo-map/module-groups.md` 模块登记、`docs-for-ai/04-reference/source-anchors.md` 若锚点变化；当日 ai-dev/logs 状态一致。（Deliverable: 端到端验收报告 + 文档/路由收口（含 batch-dsl.md 两处补强）；deps: WI0–WI15 完成或显式裁定；Item Type: Proof）

- ★ **Milestone: 契约与文档基线就绪**（WI0 完成后勾选）
- ★ **Milestone: 运行控制经 nop-job 可用**（WI1–WI3 完成后勾选）
- ★ **Milestone: 图形化编排最小可用**（WI4 完成后勾选）
- ★ **Milestone: 数据面补齐完成**（WI5–WI10 完成后勾选）
- ★ **Milestone: 治理增强完成**（WI11–WI15 完成后勾选）
- ★ **Milestone: 端到端验收通过**（WI0–WI16 全部完成后勾选）

## Current Baseline（2026-10-18 核对）

**已存在（可用）：**

- **批处理引擎**：`nop-batch-core`（BatchTask/BatchTaskBuilder、chunk 4 档事务、at-least-once + 文件源行号续传、historyStore 记录幂等、partition 微队列）；DSL `*.batch.xml` + `batch.xlib` 编译期固化进 nop-task。
- **编排**：nop-task 30+ step 类型（graph/loop/mapStep/条件/子工作流）；batch 可作为工作流原子步骤。
- **调度**：nop-job cron + 分布式 coordinator + 集群分片；`cancelFire`/`rerunFire`/`pauseSchedule`/`IJobInvoker.cancelAsync` 全套控制面；UI/权限按钮就位。
- **job→batch 启动链**：`job.yaml → BeanMethodJobInvoker → BatchTaskRunner.executeAsync` 已通（本地 `.job.yaml` 路径）。
- **治理**：nop-metadata 38 实体（列级 SQL AST 血缘、质量规则/检查点/评分/告警、对账）——超 dbt 的表级血缘 + generic tests。
- **观测**：`BatchTaskMetricsImpl`（Micrometer）实现存在，待自动接线。
- **导入导出**：`nop-batch-exp` CLI（ExportDbTool/ImportDbTool）。

**主要缺口（本 roadmap 的落点）：**

- **运行控制**：job 反向取消链路三断点（cancel 方法、context 注册表、jobTaskId 关联）；双状态无 SSOT；本地/分布式路径分裂。
- **操作面**：`nop-batch-web` 仅只读 CRUD；无图形化编排；exp 无 Web/API。
- **数据面**：`NopMetaPipeline` 无执行器；通用导入占位抛异常；无 backfill；metrics 需手工；无 aggregator。
- **治理**：无变更传播选择器；无增量策略模板；无数据目录文档站；语义层未评估。
- **遗留物**：`NopBatchTaskState` 三件套死代码；owner doc 集成节漂移（P2-5.1-004）。

## Framework / Platform Reuse

| 能力 | 提供方 | 约束 |
| --- | --- | --- |
| 运行控制 | nop-job（cancelFire/rerunFire/IJobInvoker.cancelAsync） | 复用；不自建第二套 |
| 调度/分片 | nop-job coordinator/worker | 复用；不建独立调度中心 |
| 批处理引擎 | nop-batch-core（chunk/事务档位/续传） | 不改行级流；不内建编排 |
| 任务流编排 | nop-task（30+ step） | batch:Execute 编译期固化；不重复建 Job/Step |
| 状态存储 | IBatchStateStore / DaoBatchStateStore | ORM 变更走 plan-first |
| 治理 | nop-metadata（血缘/质量/对账） | 复用；不重建 manifest |
| 观测 | BatchTaskMetricsImpl + Micrometer | 自动接线；不换 registry |
| Web/表单 | nop-amis / nop-web（AMIS schema） | 图形化走 Web；不建 SWT |
| 管理入口 | nop-job-web（cancel/rerun 按钮） | 复用；不建 nop-batch-web 第二套 |
| 权限 | nop-auth | 复用；不自建权限模型 |
| IoC/服务 | @BizModel/beans.xml | 遵守 Nop IoC 约定（无注解扫描） |
| API | GraphQL/crud | 走标准 BizModel |
| CLI | nop-runner/nop-cli-core + nop-batch-exp | Web 化复用同一 SPI |
| 测试基座 | nop-autotest + Playwright + testing.md | 端到端 kill/resume 验收 |

## Cross-Cutting Rules

1. **每个 WI 独立 plan**：走 `ai-dev/plans/00-plan-authoring-and-execution-guide.md`；WI 内不混多个可独立验收的交付物。
2. **ORM/SPI/生成管线变更 plan-first**：WI2（jobTaskId 列，ORM）、WI1/WI3（`IBatchTaskRunner`/`BatchTaskRunner` 公开签名，跨模块 SPI）、WI15（`_gen`/`_templates` 清理，生成管线——plan 证据须含「codegen 再生不复活被删文件 + 全量编译验证」）须独立 plan 证据（owner doc + 回归测试）。
3. **测试要求**：kill 后状态断言、cancel 链路（含反射失败不假成功）、context 注册表并发安全、resume/rerun 语义、metrics 自动挂载、backfill 区间跳过——各 WI plan 中明确判别性测试。
4. **文档 Reconcile-on-touch**：WI0 修正 P2-5.1-004；各 WI 交付后同步对应 owner doc；WI16 终收口 INDEX/module-groups/source-anchors。
5. **完成判定**：WI 勾选须独立 closure audit（不同 task_id 子 agent）对照 plan exit criteria 与 live code；禁止自审。
6. **不追新依赖**：不引 GPL/AGPL 运行时；连接器/语义层评估阶段只对版不引入。
7. **临时文件**：一律 `<project-root>/_tmp/`，不用系统 `/tmp/`。

## Deferred / Out of Scope

- 语义层实施（WI14 仅评估；若 go 另立 roadmap）。
- 连接器生态扩张（G10，按需增长，不设本 roadmap WI）。
- Spoon 式自由画布图形编辑器（M2 只做 AMIS 表单式起步）。
- **job 侧 `IJobExecutionContext` cancelToken 注入**（`IJobExecutionContext.java:22-24` 默认 null）：batch 路径的取消已由 WI1 的 context.cancel 覆盖（`BatchTask.java:234-254` chunk 循环已轮询 isCancelled）；token 注入属 job 执行器通用增强，另立任务，不阻塞本 roadmap。
- 跨节点 MR shuffle / MapReduce 汇聚（保持数据面轻量边界）。
- Kettle 行级流模型、Airflow 式 Python Operator SDK、dbt 式 Jinja 模板语言。
