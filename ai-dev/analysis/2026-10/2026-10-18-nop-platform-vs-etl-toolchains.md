# Nop 平台 vs ETL 工具链（Kettle / Airflow / dbt）平台级对比

> Status: open
> Date: 2026-10-18
> Scope: Nop 平台数据集成面整体（nop-batch/nop-task/nop-job/nop-stream/nop-metadata/nop-batch-exp）；Kettle/PDI（`~/sources/pentaho-kettle` tarball 快照，master）；Apache Airflow 3.4.0（`~/sources/airflow` 浅克隆）；dbt-core（`~/sources/dbt-core` tarball，**Rust Fusion 重写版**，非传统 Python）。补全路线图：`ai-dev/backlog/nop-etl-platform-completion-roadmap.md`（G1–G10 已收敛为 WI0–WI16）
> Conclusion: Nop 平台在数据质量/血缘/对账（超 dbt）、事务档位+chunk 续传（超 Kettle）、声明式 Delta 定制（三者皆无）上有结构性优势；最大产品化缺口是**图形化 ETL 编排器**（Kettle Spoon 缺位）、**backfill 回填**（Airflow 缺位）。**批任务运行控制并非"无 API"而是"未接线"**——nop-job 控制面（cancelFire/rerunFire/IJobInvoker.cancelAsync）已全套，batch 反向取消钩子现成，只缺 `BatchTaskRunner.cancel` + context 注册表 + 状态关联列（用户 2026-10-18 追问后修订）。`NopMetaPipeline` 与 `DefaultBizEntityImporter` 是两处"名存实亡"占位，dbt 式"SQL 转换编排"产品面在 Nop 平台内**无对应物**。不建议抄 Kettle 行级流或在 nop-batch 自建第二套控制面。

## Context

- 问题：把 Kettle、Airflow、dbt 这三个典型 ETL/数据工程工具与 Nop 平台的数据集成面（batch/task/job/stream/metadata）做平台级对比，找出 Nop 的优势与缺口，给改进方向。
- 为什么单独成文：`ai-dev/analysis/2026-10/2026-10-18-nop-batch-vs-oss-batch-frameworks.md`（批处理引擎对比）已明确把这三个工具排除在主对比外（"UI 驱动/Python 编排，非嵌入式 Java 引擎"）；本文件承接其排除项，从**平台/产品形态**视角对比。
- 方法：三套源码 tarball/浅克隆下载（`~/sources/`）+ 两轮 explore 子 agent 定向取证 + Nop 仓内事实核验（rg 确认 `DefaultBizEntityImporter` 占位抛异常、`NopMetaPipelineBizModel` 仅 `CrudBizModel`、`nop-batch-web` 无触发/kill 动作、`nop-batch-exp` 仅被 `nop-cli-core` 依赖）。
- 边界：调度域（nop-job vs PowerJob/snail-job）已有 `2026-08-04-nop-job-vs-powerjob-vs-snail-job-deep-analysis.md`；流计算（nop-stream vs Flink/SeaTunnel）已有 `2026-10-06a-...`；本文只覆盖**数据集成/ETL 产品形态**域，不重复。

---

## 一、三个对照物的形态定位

| | Kettle/PDI | Apache Airflow 3.4.0 | dbt-core（Rust Fusion） |
|---|---|---|---|
| **一句话定位** | 传统 ETL 引擎 + 桌面图形编辑器 + 嵌入式服务器 | DAG 任务编排调度平台（Python） | SQL 数据转换编译框架（模型即代码 + 测试 + 血缘） |
| **核心抽象** | Transformation（数据流）+ Job（控制流）+ Hop + Step | DAG + Operator + Task Instance | Model + Macro + Materialization + Source |
| **数据流模型** | 行级生产者-消费者（`BlockingRowSet` 阻塞队列） | 无数据流——Operator 间靠 XCom 传参 | 无行级流——模型间靠 `ref()` 构建图、靠 SQL 表 |
| **断点续传** | ❌ 无（失败整体重跑，`Trans.java`/`BaseStep.java` 无 restart/checkpoint 代码） | ⚠️ task 级重跑（`run_id + task_id` 重试单 task），无 chunk 级 | ⚠️ `state:modified` 选择器 + incremental materialization（表级增量，非行 checkpoint） |
| **内建调度** | ❌ 无（`JobEntrySpecial.schedulerType` 仅元数据字段，engine 无执行逻辑；靠外部 cron/Pentaho BA Server） | ✅ 中央 Scheduler（核心卖点） | ❌ 无（靠外部 cron/Airflow/Dagster 触发） |
| **图形化编辑** | ✅ Spoon（SWT 手绘 `TransGraph` 4860 行，直接模型操作 + hop 拖拽） | ⚠️ 只读 DAG 视图 + 校验（非编辑器） | ⚠️ dbt docs（生成静态血缘图，非编辑器） |
| **规模** | ~129 万行 Java，8 模块，engine 127 个 step 目录 + 70 插件模块 | ~15.7 万行 Python（airflow-core），109 providers | ~66.8 万行 Rust，143 crates（**重写版，非 Python**） |
| **代码/配置形态** | `.ktr`（Trans）/`.kjb`（Job）XML + Spoon 图形产出 | `@task` 装饰器 Python 代码 | `.sql` 模型 + `schema.yml`/`dbt_project.yml` |

**重要事实：dbt-core tarball 是 Rust 重写版（dbt Fusion）**。143 个 crate、1538 个 `.rs` 文件、约 66.8 万行 Rust；无 Python `core/`/`plugins/` 目录；用 `minijinja` 替代 Jinja2。本文所有 dbt 结论基于此版本（`crates/dbt-schemas/...`、`crates/dbt-adapter/...`）。传统 Python dbt 的宏观概念（materialization、ref/source、generic tests、manifest 血缘）在此版全部保留，故结论可外推到 Python 版，但代码级细节（crate 名）不可。

## 二、概念映射矩阵

| ETL 工具概念 | Kettle | Airflow | dbt | Nop 平台对应物 | 对齐度 |
|---|---|---|---|---|---|
| 数据流定义 | Transformation (`.ktr`) | — | — | `*.batch.xml`（chunk 管道） | ⚠️ 粒度不同（chunk vs 行） |
| 控制流/编排 | Job (`.kjb`) | DAG | — | nop-task task flow（graph/loop/mapStep 等 30+ step 类型） | ✅ 强对应 |
| 数据传递单元 | RowSet（阻塞队列，单行/批量） | XCom（变量传递） | SQL 表 | chunk `List<S>` 批量 load | ⚠️ Nop 无行级流 |
| 转换算子/步骤 | Step（127 内置 + 80 插件） | Operator（109 providers） | Model（SQL） | batch processor + step 类型 + customType | ⚠️ 生态窄 |
| 条件分支 | JobHop evaluation XOR | BranchPythonOperator | — | nop-task 条件表达式 | ✅ |
| 子流程调用 | JobEntryTrans/JobEntryJob | triggerDag | — | nop-task 子工作流 step | ✅ |
| 调度 | 外部 cron | 内建 Scheduler | 外部 cron | nop-job（cron + 分布式 coordinator + 集群分片） | ✅ |
| 增量/续跑 | 整体重跑 | task 重试 | incremental materialization | at-least-once + 文件源行号续传 + 状态驱动过滤 | ✅ Nop 更强（chunk 级） |
| 测试 | 无 | unit test/CI | generic tests（unique/not_null/relationships）+ 单元测试 | nop-metadata 质量规则 + 检查点 + 评分 + 告警 | ✅ Nop 显著强 |
| 血缘 | 无 | 无 | manifest parent_map/child_map | nop-metadata `SqlColumnLineageExtractor`（列级 AST 血缘） | ✅ Nop 显著强 |
| 对账 | 无 | 无 | 无 | nop-metadata reconciliation（两表/跨源比对） | ✅ Nop 独有 |
| 数据目录/文档 | 无 | 无 | dbt docs（自动生成站） | ❌ 无对应物 | ❌ 缺口 |
| ETL 图形编排 | ✅ Spoon | ❌ 只读图 | ❌ 只读血缘图 | ❌ 无 | ❌ 最大缺口 |
| 声明式定制 | ❌ XML 直改 | ❌ Python 代码 | ⚠️ Jinja 宏 | ✅ XDSL + Delta（可逆计算） | ✅ Nop 独有 |

## 三、关键差异逐项

### 3.1 数据流模型：行级流 vs chunk 批量

- **Kettle**：每个 Step 独立线程，经 `RowSet` 阻塞队列逐行/小批量传递（`BlockingBatchingRowSet`，BATCHSIZE=2 双缓冲，`BaseStep.java:109-121` 契约"读单行→putRow 单行"）。优势：低内存流式、可视化 hop 直观映射物理数据流。代价：线程数 = step 数，hop 缓冲默认 1 万行，事务/批量优化靠 step 自觉。
- **Nop**：`IBatchLoader.load(batchSize)` 批量取 → `process` flatMap → `consume(Collection)` 批量消，事务档位 `none/chunk/process/consume` 可配。chunk 是显式可靠边界（at-least-once + checkpoint），而非队列副产物。
- **判定**：chunk 模型在事务/幂等/续传上结构性优于行级流；Kettle 行级流唯一不可替代的场景是"单条记录驱动的低延迟流式清洗"，Nop 应交给 nop-stream 而非 nop-batch。**不建议 nop-batch 抄行级 RowSet**。

### 3.2 容错语义：无断点 vs chunk 续传

- **Kettle**：`Trans.java`/`BaseStep.java` 全文无 restart/checkpoint/recover；失败即整体失败（`Trans.java:1320-1328`），需外部触发重跑。
- **Nop**：文件源 `ResourceRecordLoaderProvider` 行号续传 + 状态驱动任务重跑天然过滤 + historyStore 记录级幂等 + `DaoBatchStateStore` 条件 UPDATE 防双启动。chunk 级续传是 Nop 相对 Kettle 的代差优势。
- **Airflow**：task 是最小重跑单元（task 重试/N attempts），无任务内断点；一个 8 小时 task 失败只能整 task 重跑。粒度比 Nop 的 chunk 粗。

### 3.3 编排能力：Airflow DAG vs nop-task

- **Airflow**：DAG/Operator 模型 + Python 代码定义，109 providers 覆盖主流 SaaS/DB/文件系统；成熟 backfill（按日期区间回填）、pause/unpause、clear（清 task 状态重跑）、depends_on_past 等。UI 提供只读 DAG 图 + 校验。
- **nop-task**：30+ step 类型（graph/loop/mapStep/条件/子工作流）、跨重启断点续跑、与 batch 无缝嵌套（`batch.xlib` 编译期固化）、声明式 XDSL + Delta。
- **判定**：Airflow 胜在**生态规模 + backfill + 运维成熟度**；nop-task 胜在**与业务同栈 + 声明式 + 可 Delta 定制**。Nop 最大缺口是 backfill（回填历史区间）——nop-task/nop-job 无此语义，仅 nop-stream 有测试级 RECALCULATE。

### 3.4 调度

- Airflow 中央 Scheduler 是其核心卖点；Kettle 完全无内建调度；dbt 无调度。
- Nop nop-job 已有 cron + 分布式 coordinator + 集群分片（见调度域专文，本文不重复）。
- 结论：调度维度 Nop 无缺口，三者中 Airflow 是唯一可比者且已有专文裁定。

### 3.5 治理：质量/血缘/对账 vs dbt

| 治理能力 | dbt（Rust Fusion） | Nop nop-metadata |
|---|---|---|
| 列级血缘 | manifest `parent_map`/`child_map`（表级图）+ `columns` 节点字段 | `SqlColumnLineageExtractor`（SQL AST 列级血缘，更强） |
| 数据质量 | generic tests（unique/not_null/relationships）+ schema.yml | 38 实体：质量规则 + 检查点 + 评分 + 告警（更全） |
| 新鲜度 | `FreshnessStatus` Pass/Warn/Error | 有对应规则类型 |
| 对账 | ❌ 无 | ✅ reconciliation（两表/跨源） |
| 增量选择 | `state:modified` 选择器 + incremental 策略（append/merge/delete_insert/insert_overwrite/microbatch） | 无对应选择器语义（增量靠 batch processor 手写 SQL） |
| 语义层 | MetricFlow（指标→SQL 编译） | ❌ 无对应物 |

- **判定**：nop-metadata 在血缘/质量/对账上**结构性强于 dbt**（列级 vs 表级、带评分告警、带对账）。dbt 值得借鉴的是：① `state:modified` 选择器（按变更传播选模型）；② 增量策略枚举（append/merge/delete_insert/insert_overwrite/microbatch 是行业收敛的模式）；③ 语义层。
- **最大问题**：`NopMetaPipeline` 实体与 `NopMetaPipelineBizModel`（`nop-metadata-service/.../NopMetaPipelineBizModel.java:12-15`，仅 `CrudBizModel` 无额外动作）**没有任何执行器**——pipeline 定义存库但不被调度/执行，名存实亡。dbt 的"模型编排执行"在 Nop 平台内**无对应产品面**。

### 3.6 操作面：图形化编排与导入导出

- **Kettle Spoon**：SWT 手绘图形编辑器（`TransGraph.java` 4860 行，`paintControl` 直接 GC 绘制 step/hop，拖拽创建连线直接调 `transMeta.addTransHop()`），模型与图形双向绑定。这是 ETL 产品化的黄金标准。
- **Nop 现状**：
  - `nop-batch-web`：仅 AMIS 生成 CRUD 页（`_gen/_NopBatchTask.view.xml` 只有 startTime/endTime/restartTime/resultStatus 列），无触发/kill/重跑动作。
  - `nop-batch-exp`：CLI 工具（`ExportDbTool.java:69`、`ImportDbTool.java:72`），仅被 `nop-cli-core` 依赖（`nop-runner/nop-cli-core/pom.xml:103`），无 Web/API 化。
  - `nop-batch-biz`：`DefaultBizEntityImporter` 占位实现，`importFile` 直接抛 `UnsupportedOperationException`（`DefaultBizEntityImporter.java:20-21`），通用实体导入**未实现**。
- **判定**：操作面（图形编排 + 运行大屏 + 导入导出 Web 化）是 Nop 平台相对 Kettle 的**系统性产品化缺口**，不是单点补丁能解决的。

### 3.7 生态宽度

- Airflow 109 providers、Kettle 70 插件模块/80+ step、dbt 十余 adapter（Snowflake/BigQuery/Databricks/Redshift/Spark/DuckDB/Postgres/ClickHouse/Trino 等，`crates/dbt-adapter-core/src/lib.rs:6-94`）。
- Nop nop-stream 5 连接器、nop-batch 依赖 Nop ORM/Dao，连接器生态显著窄。
- **判定**：生态宽度是开源工具十年积累的结果，Nop 不必硬追；应聚焦"与业务模型同栈"的差异化（批处理直接消费 ORM 实体、processor 调 I*Biz），而非做通用连接器市场。

## 四、Nop 平台的结构性优势（护城河）

1. **质量/血缘/对账一体**（超 dbt）：列级血缘 + 质量规则/检查点/评分/告警 + 对账，38 实体覆盖数据治理全链路；dbt 只有测试+表级血缘。
2. **chunk 续传 + 事务档位 + ORM 乐观锁协同**（超 Kettle）：at-least-once 显式化、文件源行号续传、4 档事务——只有"自带 ORM 的平台框架"能做出的组合优化。
3. **声明式 XDSL + Delta 定制**（三者皆无）：Kettle 的 `.ktr` 是工具私有 XML，Airflow 是 Python 代码，dbt 是 Jinja+SQL；只有 Nop 的 ETL 定义可被 Delta 覆盖、与业务模型同生命周期演进。
4. **batch/task/job 同栈编排**：`batch.xlib` 编译期把 batch DSL 固化进 nop-task AST，批处理是工作流的原子步骤，无需 Airflow 式跨系统集成。
5. **业务模型同栈**：batch processor 直接调 `I*Biz`/ORM 实体，Kettle/Airflow/dbt 都在业务模型之外再建一层"数据工具自己的世界"。

## 五、Nop 平台的缺口（按严重度）

| # | 缺口 | 对照 | 严重度 |
|---|---|---|---|
| G1 | **图形化 ETL 编排器**：无 Spoon 式拖拽 hop 连线编辑，ETL 定义只能手写 XDSL | Kettle Spoon | 🔴 产品化最大缺口 |
| G2 | **批任务运行控制未接线 nop-job**：控制面（cancelFire/rerunFire/pauseSchedule/IJobInvoker.cancelAsync）nop-job 已全套具备，batch 侧缺 `cancel(Map)` 方法与运行中 context 注册表，两套状态无关联键 → 控制走不通（详见 §6 P0-1） | Airflow clear/pause、Spring Batch JobOperator | 🔴 |
| G3 | **backfill 回填语义**：nop-task/nop-job 无按区间回填；仅 nop-stream 测试级 RECALCULATE | Airflow backfill | 🟠 |
| G4 | **`NopMetaPipeline` 无执行器**：pipeline 实体 CRUD 可用但不被调度执行，名存实亡 | dbt run 编排执行 | 🟠 |
| G5 | **`nop-batch-biz` 通用导入占位**：`DefaultBizEntityImporter.importFile` 抛 UnsupportedOperationException | Kettle TableInput/Output | 🟠 |
| G6 | **`nop-batch-exp` 仅 CLI**：无 Web/API，操作面缺失 | Airflow REST API | 🟡 |
| G7 | **无数据目录/文档站**：无 dbt docs 式自动生成血缘文档 | dbt docs | 🟡 |
| G8 | **无语义层**：无 MetricFlow 式指标→SQL 编译 | dbt semantic layer | 🟡 |
| G9 | **无 `state:modified` 选择器**：无"按变更传播选受影响模型"语义 | dbt state selector | 🟡 |
| G10 | **连接器生态窄**：nop-stream 5 连接器 vs Airflow 109 providers | Airflow/Kettle 生态 | 🟡（不必硬追） |

## 六、改进建议（按优先级）

### P0 — 运维闭环 + 操作面入口（对齐批处理分析 P0，范围扩大到平台）

1. **批任务运行控制接线 nop-job**（G2）：不在 nop-batch 另建控制面——nop-job 已有 `cancelFire`/`rerunFire`/`pauseSchedule`/`IJobInvoker.cancelAsync` 全套，batch 已被 job 经 `job.yaml → BeanMethodJobInvoker → BatchTaskRunner.executeAsync` 启动，**反向取消钩子现成**（`BeanMethodJobInvoker.cancelAsync` 按方法名反射调 `cancel`）。要补的只是三处：① `BatchTaskRunner` 增加 `cancel(Map)` 方法；② 运行中 `IBatchTaskContext` 注册表（按 taskPath/taskKey 或 jobTaskId 定位，现状仅 ThreadLocal `BatchTaskGlobals`）；③ `NopBatchTask` 增加 `jobTaskId` 关联列（ORM 变更，plan-first）+ 双状态 SSOT 裁定（job taskStatus vs batch taskStatus 谁权威）。详见批处理分析 §八 P0-1 修订版。**不建议**在 nop-batch 内自建第二套 kill/resume API。
2. **图形化 ETL 编排器起步**（G1）：不必一步做到 Spoon 的 SWT 手绘。务实路径：AMIS 表单式 step 编排（选 step 类型→配参数→连线），或复用 nop-task 已有的工作流设计器扩展 batch step 类型。目标是"非开发者可配一条 batch 管道"，而非完整 Spoon 克隆。

### P1 — 数据面产品补齐

3. **`NopMetaPipeline` 执行器**（G4）：给 pipeline 实体接线——`schedule` 字段触发 nop-job，执行委托 nop-task（内含 batch step）。先让"名存实亡"的实体活过来，再谈 dbt 式模型编排。
4. **`DefaultBizEntityImporter` 实现**（G5）：通用实体导入（ORM 实体 + 批量 load），补齐 `nop-batch-biz` 占位；与 `nop-batch-exp` CLI 收敛到同一 SPI。
5. **backfill 语义**（G3）：nop-task 增加"按参数区间重复实例化"的 operator（或 nop-job 增加 backfill API）；对齐 Airflow backfill 的"历史区间补跑"心智。
6. **`nop-batch-exp` Web/API 化**（G6）：把 CLI 工具包一层 BizModel/HTTP，让导入导出可在管理端触发。

### P2 — 治理增强（借鉴 dbt）

7. **`state:modified` 选择器**（G9）：基于实体定义/依赖图的变更传播，选择受影响的 pipeline/task 重跑。
8. **增量策略枚举**（借鉴）：把 dbt 收敛的 append/merge/delete_insert/insert_overwrite/microbatch 模式沉淀为 batch processor 的策略模板（SQL 片段库），降低手写增量 SQL 成本。
9. **数据目录/文档站**（G7）：从 nop-metadata 血缘+质量规则自动生成静态文档站（对齐 dbt docs 心智）。
10. **语义层评估**（G8）：评估是否值得做指标→SQL 编译（MetricFlow 式），或先用 nop-metadata 的语义实体承载、执行走 nop-task。

### 不建议方向

- ❌ **抄 Kettle 行级 RowSet 流模型**：chunk 模型在事务/幂等/续传上更优；行级流的低延迟场景交给 nop-stream。
- ❌ **自建独立调度中心**：nop-job 已有 cron+coordinator+分片，重复建设违背 DSL 森林分工（调度域专文裁定）。
- ❌ **重建 dbt 式 manifest/血缘**：nop-metadata 列级血缘已结构性更强，重复建设无增量价值；应接线 `NopMetaPipeline` 而非重造。
- ❌ **追平 Airflow 109 providers 生态**：连接器生态是十年积累，Nop 应聚焦"业务模型同栈"差异化，连接器按需增长。
- ❌ **做 Spoon 式 SWT 桌面客户端**：与 Nop 平台 AMIS/Web 技术栈背道而驰；图形化应走 Web 路径。

---

## Open Questions

- [ ] 图形化编排器路径：AMIS 表单式 vs 复用 nop-task 工作流设计器扩展 batch step vs 独立新模块？技术选型未定。
- [ ] `NopMetaPipeline` 与 nop-task 的关系：pipeline 实体是"轻量数据流定义"（内嵌 batch 引用）还是"完整 task flow 的别名"？边界未划。
- [ ] backfill 放 nop-task 还是 nop-job？区间实例化与集群分片的交互？
- [ ] `state:modified` 的变更源：ORM 模型变更？batch DSL 变更？pipeline 定义变更？传播粒度？
- [ ] 语义层是否值得投入？还是先用 nop-metadata 语义实体 + 手写 SQL 指标？

## References

### 仓内

- `ai-dev/analysis/2026-10/2026-10-18-nop-batch-vs-oss-batch-frameworks.md` — 批处理引擎对比（本文排除项的承接者）
- `ai-dev/analysis/2026-08/2026-08-04-nop-job-vs-powerjob-vs-snail-job-deep-analysis.md` — 调度域对比
- `ai-dev/analysis/2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md` — 流计算对比
- `docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/02-core-guides/batch-dsl.md` — batch 模块权威文档
- `docs/theory/why-springbatch-is-bad.md` — NopBatch 设计哲学基线
- 源码核验：`nop-batch/nop-batch-biz/.../DefaultBizEntityImporter.java:20-21`（占位抛异常）；`nop-metadata/nop-metadata-service/.../NopMetaPipelineBizModel.java:12-15`（仅 CrudBizModel）；`nop-batch/nop-batch-web/.../_gen/_NopBatchTask.view.xml`（仅 CRUD 列）；`nop-runner/nop-cli-core/pom.xml:103`（exp 依赖）；`nop-batch/nop-batch-exp/.../{ExportDbTool,ImportDbTool}.java`
- nop-job 控制面与 batch 接线（2026-10-18 追问后探索）：`nop-job-service/.../NopJobFireBizModel.java:106-208`（cancelFire/rerunFire）；`NopJobScheduleBizModel.java:61-157`（pause/resume）；`nop-job-api/.../IJobInvoker.java:30`；`nop-job-local/.../BeanMethodJobInvoker.java:50-110`（cancelAsync 反射）；`nop-batch-sys/.../sys-event-batch-consumer.job.yaml:7-12`；`nop-batch-core/.../BatchTaskRunner.java:35-50`（无 cancel、context 不外传）；`nop-job-core/.../JobTaskStateMachine.java:133-155`

### 外部参考（本地源码快照）

- Kettle/PDI：`~/sources/pentaho-kettle`（tarball，master）
  - `engine/.../trans/{TransMeta,Trans}.java`、`trans/step/{StepMeta,StepInterface,BaseStep}.java`
  - `core/.../row/{RowMetaInterface,RowMeta}.java`、`BlockingRowSet.java`、`BlockingBatchingRowSet.java`
  - `engine/.../job/{JobMeta,Job}.java`、`job/entry/{JobEntryInterface,JobEntryBase,JobEntryCopy}.java`、`JobHopMeta.java`
  - `engine/.../job/entries/special/JobEntrySpecial.java:48-57`（schedulerType 仅元数据）
  - `ui/.../spoon/{Spoon,TransGraph,AbstractGraph}.java`、`ui/.../spoon/job/GraphJob.java`
  - `engine/.../www/{Carte,WebServer,CarteServlet}.java`（Carte = Jetty + 40+ REST servlet）
- Airflow 3.4.0：`~/sources/airflow`（浅克隆，3.4.0）— DAG/Operator/executor/backfill/109 providers；airflow-core ~15.7 万行 Python
- dbt-core（Rust Fusion）：`~/sources/dbt-core`（tarball，main）
  - `crates/dbt-schemas/src/schemas/{common.rs,manifest/manifest.rs,manifest/v12.rs,data_tests.rs,ref_and_source.rs}`
  - `crates/dbt-state/src/{materialization.rs,selector.rs}`（incremental 策略、state:modified 选择器）
  - `crates/dbt-adapter/src/{adapter/adapter_factory.rs,engine/adapter_engine.rs,snapshots.rs,seed.rs}`
  - `crates/dbt-adapter-core/src/lib.rs:6-94`（adapter 列表）
  - `crates/dbt-compilation/src/core.rs`（Load/Resolve/Build Manifest）
  - `crates/dbt-scheduler/src/schedule.rs`（拓扑排序调度）
  - `crates/dbt-metricflow/src/lib.rs`、`crates/dbt-schemas/src/schemas/semantic_layer/semantic_manifest.rs`（语义层）
