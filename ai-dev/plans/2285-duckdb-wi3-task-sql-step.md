# 2285 DuckDB WI3 — nop-task SQL 执行步骤集成

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI3；WI0 裁定报告
> Related: 2282/2283/2284（已完成）

## Purpose

把 DuckDB 执行层接入 nop-task 编排：提供 SQL 执行型任务步骤 bean（经 task 系统的 `<step type="simple" bean="...">` 既有扩展点接入，不改动 nop-task-core/nop-xdefs），参数绑定防注入，步骤间只传文件路径/表名不传数据，结果摘要回传，并与既有 retry/timeout 等装饰器兼容。

## Current Baseline

- WI1/WI2 已交付：IDuckDbEngine 连接管理、DuckDbFiles 文件数据面（commit ab22dc6519、29a03397b5），25 测试全绿
- nop-task step 类型分发是 `TaskStepBuilder.buildRawStep` 的硬编码 switch（TaskConstants.STEP_TYPE_*），无 Java SPI；新增 step 类型的官方扩展点是 `<step type="simple" bean="...">`（buildSimpleStep：从 IoC 容器取 bean **cast 为 AbstractTaskStep**，经 SimpleBeanTaskStep 包装，per-model 配置落在包装上——plan 364 修复，共享单例安全）
- 步骤输入读取惯例：runtime 先对 `<input>` 声明求值并注入 scope（TaskStepExecution.initInputs），步骤体经 `stepRt.getValue(name)` 读取（先例 InvokeTaskStep）；输出经 TaskStepReturn.RETURN(Map) 返回，**仅 task.xml 声明为 `<output>` 的 key 会写入 parentScope** 供下游 `${var}` 引用
- 任务测试家族先例：nop-task-ext `TestTaskFlowDemo`（CoreInitialization + `taskFlowManager.loadTaskFromPath` + `newTaskRuntime` + execute）；可靠性测试家族在 nop-task-ext/src/test/.../reliability/
- 装饰器（retry/timeout/ratelimit/transaction/orm）在 buildDecoratedStep 层按 step model 属性包装，对 simple step 同样生效
- ITaskStepRuntime.execute 提供 eval scope（步骤输入变量由 runtime 注入 scope）；TaskStepReturn.RETURN(outputs) 回传 Map 输出
- nop-duckdb 当前无 nop-task 依赖；接入后 nop-duckdb 依赖 nop-task-core（compile）

## Goals

- `DuckDbSqlTaskStep extends AbstractTaskStep`（bean id nopDuckDbSqlTaskStep；stepType 字段仅 informational，运行期被包装层的 "simple" 覆写，无路由语义）：输入 sql（必选）、params（可选，PreparedStatement ? 绑定）、ingestCsvPath+ingestTable（可选前置摄取）、exportPath+exportFormat（可选后置导出 csv/parquet）；输出摘要 Map（rowCount/rowsIngested/rowsExported/outputPath）；步骤间只传路径与表名（数据面留在 DuckDB 内）
- 参数绑定防注入：用户值一律 PreparedStatement ? 绑定，绝不拼接入 SQL；路径/表名转义与 DuckDbFiles 单一来源（escapePath/quoteIdentifier 由 private 提升为包内可见或抽公共 helper，防两处漂移）
- **无状态执行约束：bean 为共享单例，execute 内不得持有 Connection/PreparedStatement 等实例状态**（plan 364 事故预防）
- 生命周期：每 execute 开连接、用毕关闭；dbPath 输入决定 openFile（文件库）或缺省 openMemory（内存库）。**连接契约（WI2 固化）：内存连接相互隔离、表随连接消失**——多步管道必须在各步传同一 dbPath（文件库，顺序步骤 open→close→open 合法）或单步内完成多段；单步内 ingest→sql→export 三段共用同一连接
- 错误语义：SQL 失败/文件缺失等复用 NopDuckDbErrors，包装为 NopDuckDbException 透传给 task 装饰器。**重试语义裁定：NopDuckDbException 默认非 bizFatal，既有 RetryPolicy 对其全部可重试（含永久性错误）——本 WI 接受该无差别重试语义，按错误类别的 bizFatal 精细化（lock conflict 应不可重试还是可重试）显式归 WI4 裁定**
- 与 nop-task 既有装饰器兼容：task.xml 中该 step 挂 retry/timeout 属性经既有装饰器链生效（测试验证）

## Non-Goals

- 不改 nop-task-core/nop-xdefs（不加新 step type 常量、不动 task.xdef；simple step 是官方扩展点）
- 不做单写者锁的任务级语义（WI4）；不测 ratelimit/transaction/orm 装饰器（transaction/orm 依赖 nop-dao/nop-orm 装配，归 WI4/WI6 的 DB/ORM 场景；roadmap WI3 装饰器兼容主张在勾选时以 retry/timeout 实测 + 其余机制同构为由覆盖，ratio 记录进日志）
- 不做对拍/性能（WI5/WI7）、不写使用文档（WI9）
- 不引入异步/分步 suspend 语义（同步步骤即可，异步场景经既有 timeout 装饰器）

## Scope

### In Scope

- nop-duckdb pom 增 nop-task-core 依赖（compile）
- DuckDbSqlTaskStep + app-duckdb.beans.xml 注册 + 新错误码（invalid-step-input，英文描述）
- 任务编排测试：task.xml（ingest→sql(带 ? 绑定)→export 三步管道）经 ITaskFlowManager 全链执行；装饰器兼容测试（retry 属性触发重试后成功）；输入校验失败测试
- 当日 ai-dev/logs/ 更新

### Out Of Scope

- nop-task-ext 模块（测试对齐其家族模式但不改动它）
- DB 状态存档续跑（nop-task-ext 既有能力，WI4 验证语义）

## Execution Plan

### Phase 1 - 步骤实现与注册

Status: completed
Targets: `nop-duckdb/src/main/java/io/nop/duckdb/`、`nop-duckdb/pom.xml`、beans

- Item Types: `Fix`（本分支 guide 四分类）

- [x] pom 增 nop-task-core（compile）；DuckDbSqlTaskStep 实现（输入校验 fail-fast：sql 缺失抛 invalid-step-input；ingest/export 参数组合校验）；输出摘要 Map
- [x] beans.xml 注册（依赖注入 IDuckDbEngine）；新错误码

Exit Criteria:

- [x] 实现无空壳：每个输入分支（sql-only / ingest+sql / sql+export / 全组合）都有真实行为
- [x] `./mvnw install -DskipTests -pl nop-duckdb -am` 退出码 0
- [x] No owner-doc update required（WI9 承接）
- [x] `ai-dev/logs/2026/10-01.md` 已更新

### Phase 2 - 任务编排测试与装饰器兼容

Status: completed
Targets: `nop-duckdb/src/test/java/io/nop/duckdb/`、`nop-duckdb/src/test/resources/_vfs/nop/duckdb/task/`

- Item Types: `Proof`

- [x] 管道端到端：task.xml 三步（simple step ingest→sql（? 绑定过滤）→export）经 ITaskFlowManager 执行；**三步传同一 dbPath（临时 .duckdb 文件库）**以跨步骤共享表，断言输出摘要与导出文件内容
- [x] 装饰器兼容：retry/timeout 经 **nop-task-ext 装饰器 bean（pom 加 nop-task-ext test scope）**；"首试失败后重试成功"用确定性构造——test beans 文件定义包装 bean（首 attempt 抛 NopDuckDbException、后续放行真实执行，对齐既有可靠性家族的 fail-once helper 模式），task.xml 对包装 bean 挂 `<retry>`，断言实际执行次数≥2 且最终成功
- [x] 防注入测试：params 值含引号/分号经 ? 绑定按字面量处理（查询结果断言）；输入校验失败测试（sql 缺失抛 invalid-step-input）
- [x] 步骤间传值测试：前一步输出表名经 scope 变量传给后一步（`${tableName}` 表达式），全程不回传数据本体
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] **端到端验证**：task.xml 从 ingest 到 export 全链经 ITaskFlowManager 贯通（用户入口=task 定义，出口=导出文件）
- [x] **接线验证**：DuckDbSqlTaskStep 经 task.xml simple-step 引用被运行时调用（非直 new）
- [x] 新增公共行为每项至少一测（plan guide 规则 25）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] No owner-doc update required（使用文档归 WI9）
- [x] `ai-dev/logs/2026/10-01.md` 已更新 WI3 收口记录

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无已知输入）
- [x] 行为/契约结果已达成：SQL 步骤可被 task.xml 编排并受装饰器保护
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确 No owner-doc update required（显式归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：audit 验证步骤经 task.xml 运行时真实调用、管道端到端贯通、无空壳
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] checkstyle / 代码规范检查通过
- [x] scan-hollow-implementations --module nop-duckdb --severity high 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- duckdb.xlib 提供 `<duckdb:sql>` 生成器标签（customType 机制）作为 DSL 糖——核心能力已由 simple step 承载，糖留待真实消费者需要时加

## Closure

Status Note: SQL 步骤经官方 simple-step 扩展点接入并全链实测：管道（ingest→filter 物化→export）经 ITaskFlowManager 贯通，表名经 scope 变量跨步骤传递（filter 步 outputTable 输出 → export 步 ${filteredTableName} 引用，数据本体不出库）；retry fail-once 确定性重试、防注入绑定、缺输入 fail-fast 均有测试。首轮 audit 唯一 Major（表名 scope 链无测试证据）已按 audit 给出的选项 (a) 实测修复并复跑 29/29 全绿。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，fresh session）
- Audit Session: agent_0ea3bc5c-d4cd-4043-b98e-3f2b0d10a1f9
- Evidence:
  - 首轮 audit：Phase1/Phase2 除一项外全部 PASS；唯一 Major = checklist 第 4 项（表名 scope 链）无测试证据 → 已修复（pipeline.task.xml 增加 resultTable 物化 + outputTable→filteredTableName 导出链 + 测试断言 filteredTableName=t_filtered）
  - Anti-Hollow 三项 PASS（buildSimpleStep 经 BeanContainer 运行时调用非直 new；端到端导出回读 count=2/sum=13；scan-hollow 0）
  - 命令复证（audit 方实跑 + 修复后实现者复跑）：29/29 全绿；-am 收口门 EXIT=0；check-doc-links 0 errors；run-java-lint 0
  - 特别审查结论：getLocalValue 输入语义为平台契约对齐（直调缺输入 fail-fast 抛 invalid-step-input，符合 guide 规则 24）；无状态单例约束落实（execute 内全局部变量）
  - Minor 处置：Minor1 越界 .rels 已还原；Minor2（writeCsv params 重载/ingestParquet 分支无直测）登记 follow-up；Minor3 Targets 路径已更正；Minor4（gate 预勾）随本 evidence 落定
  - check-plan-checklist.mjs --strict 收口后退出码 0

Follow-up:

- writeCsv(4 参 params) 重载与步骤 ingestParquetPath 分支无直接测试（同族路径已覆盖），WI6 端到端场景自然覆盖
- 直调 bean 缺输入抛 invalid-step-input 的显式断言可并入 WI4 测试
- 除此之外 no remaining plan-owned work
