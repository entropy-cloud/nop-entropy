# nop-batch — 批处理引擎

## 功能概览

企业级批处理引擎，支持大数据量场景。核心设计思想：按 Chunk（批次）读取→处理→写入，每个 Chunk 独立事务，支持断点续传与记录级幂等。

- **Chunk 处理模式**：按批次读取→处理→写入，内存友好
- **文件输入/输出**：支持 CSV/Excel 文件作为数据源或输出
- **ORM/JDBC 读写**：内建 `orm-reader`/`orm-writer`、`jdbc-reader`/`jdbc-writer`
- **断点续传**：通过 `completedIndex` 记录进度，中断后可恢复；语义为 at-least-once（失败 chunk 的行会重新投递，业务方需自带幂等）
- **记录级幂等**：`historyStore` 把成功记录写入 `NopBatchRecordResult`（`resultStatus=0`，mandatory 三列 retryCount=0/batchSize=filtered.size()/handleStatus=0 由 saveProcessed 显式赋值——P1-3.4-002），`filterProcessed` 按 `recordKey` 跳过已处理记录；`saveProcessed` 与业务 consume 同事务提交（`consume` scope + history 时 `BatchTaskBuilder` 自动提升为 process 级事务包装，详见 `../02-core-guides/batch-dsl.md` 的 transactionScope 一节）
- **重试/跳过**：`retryPolicy` + `skipPolicy` 内建支持
- **`snapshotBuilder="orm-entity"` 用法（P1-3.4-001，plan 2382）**：映射到 `OrmBatchRecordSnapshotBuilder`（魔法治具值经 `BatchConstants.SNAPSHOT_BUILDER_ORM_ENTITY`；也可 `snapshotBuilder="beanName"` 指向自定义 `IBatchRecordSnapshotBuilder`）。语义：ORM 实体在 processor 链前后做快照/恢复——`restore` 返回**恢复后的完整记录列表**并回写 retry 集；未配置时 retry 消费方拿不到恢复记录（失败 chunk 记录会静默丢弃），使用 ORM 实体 + 重试语义的任务应配置它。
- **并发处理**：`concurrency` 控制 chunk 循环并行度，`executor` 指定承载线程池（Bean 名称）。配置 `executor` 时 chunk 任务提交到该池执行；**未配置时兜底提交到平台共享 cached 线程池执行（并非串行）**——该池有全局上界（配置项 `nop.commons.concurrent.cached-pool.maxPoolSize`，缺省 200）与达界熔断（超界提交报错 `nop.err.commons.concurrent.cached-pool-saturated`）。`concurrency` 与 `dispatcher` 的 `fetchThreadCount` 受装配期护栏约束：超过 `nop.batch.max-concurrency-cap`（缺省 200）在任务装配期报错 `nop.err.batch.concurrency-exceeds-cap`（fail-fast，不进入线程创建路径）。兜底池形态裁定（1.7-C8-003）：维持共享 cached 池兜底 + 上界熔断基线声明，不迁移 globalWorker（chunk 循环为长任务体，不得常驻平台共享 worker）
- **分区处理**：`dispatcher` + `partitionIndexField` 按字段分区并行
- **生命周期 Listener**：12 种回调（onTaskBegin/End, onChunkBegin/End 等）

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopBatchTask | `nop_batch_task` | 批处理任务实例 |
| NopBatchTaskVar | `nop_batch_task_var` | 任务状态变量（EAV） |
| NopBatchRecordResult | `nop_batch_record_result` | 记录级处理结果（幂等） |
| NopBatchFile | `nop_batch_file` | 批处理文件追踪 |

## 关键字段

**NopBatchTask**:
- `taskName` / `taskKey`：任务标识（同 taskName+taskKey 只允许一个活跃实例）
- `taskStatus`：任务状态
- `completedIndex`：已完成位置（断点续传）
- `processItemCount` / `writeItemCount` / `skipItemCount` / `retryItemCount`：统计计数
- `loadRetryCount`：重试加载次数

**NopBatchRecordResult**:
- `recordKey`：记录键（幂等标识）
- `resultStatus`：处理结果状态
- `retryCount`：重试次数
- `handleStatus`：处理状态

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-batch-core` | 批处理核心引擎（IBatchTask/IBatchLoader/IBatchProcessor/IBatchConsumer） |
| `nop-batch-dsl` | 批处理 DSL 定义（batch.xdef Schema + batch.xlib 标签库） |
| `nop-batch-orm` | ORM 集成（orm-reader/orm-writer） |
| `nop-batch-jdbc` | JDBC 批处理（jdbc-reader/jdbc-writer） |
| `nop-batch-biz` | BizModel 集成 |
| `nop-batch-dao` | ORM 实体与 DAO |
| `nop-batch-service` | 业务逻辑（nopBatchTaskManager） |
| `nop-batch-web` | Web 层与管理页面 |

## DSL 结构

批处理模型由三部分组成：**Loader**（数据加载）→ **Processor**（数据处理）→ **Consumer**（数据消费）。

```
batch.xml 结构
    │
    ├─ batch 根节点
    │    ├─ taskName / batchSize / concurrency / saveState / executor
    │    ├─ taskKeyExpr（任务实例区分）
    │    ├─ retryPolicy / skipPolicy（重试/跳过策略）
    │    ├─ onTaskBegin/End, onChunkBegin/End（生命周期 Listener）
    │    │
    │    ├─ loader（数据加载）
    │    │    ├─ file-reader（CSV/Excel 文件）
    │    │    ├─ orm-reader（ORM 查询）
    │    │    ├─ jdbc-reader（原生 SQL）
    │    │    ├─ generator（生成器）
    │    │    ├─ provider（自定义加载器，返回 IBatchLoader）
    │    │    ├─ dispatcher（分区加载）
    │    │    └─ afterLoad（加载后回调）
    │    │
    │    ├─ processor（数据处理，可多个，按 order 排序）
    │    │    ├─ source（xpl 脚本，调用 consume(item) 输出）
    │    │    ├─ filter（过滤条件）
    │    │    └─ provider（自定义处理器，返回 IBatchProcessor）
    │    │
    │    └─ consumer（数据消费，可多个，按 name 区分）
    │         ├─ filter（过滤条件，匹配 item）
    │         ├─ file-writer（文件写出）
    │         ├─ orm-writer（ORM 写入）
    │         ├─ jdbc-writer（原生 SQL 写入）
    │         ├─ source（xpl 脚本）
    │         └─ transformer（写入前转换）
```

## processor 中调用 I*Biz

processor 的 `<source>` 段是 xpl 脚本环境，支持通过 `inject()` 获取 IoC 容器中的任何 Bean（包括 `I*Biz` 接口）。

**伪代码示例**：

```
processor name="processOrder":
    source:
        // 通过 inject() 获取 I*Biz 接口
        const biz = inject('IErpXxBiz');
        // 调用业务方法
        const result = biz.doSomething(item);
        // 输出到 consumer
        consume(result);
```

**也可以使用 xpl 标签库调用 ORM**：

```
processor name="queryData":
    source:
        <dao:FindFirst xpl:lib="/nop/orm/xlib/dao.xlib">
            select o from ErpMdMaterial o where o.code = ${item.materialCode}
        </dao:FindFirst>
```

## 动态 SQL 传参机制

### xpl-sql 类型与 `${expr}` 语法

在 xpl 的 sql 输出模式下（`xpl-sql` 类型，等价于 `xpl:outputMode="sql"`），`${expr}` 的行为是**自动参数化**：

- `${expr}` → 自动转为 JDBC `?` 参数，防 SQL 注入
- `${raw(expr)}` → 原样拼接 SQL 文本（跳参数化），用于动态表名/列名
- `${collection}` → 展开为多个 `?` 参数（IN 子句）

> **与 MyBatis 的关键区别**：MyBatis 中 `${}` 是原样替换（有注入风险），xpl sql 模式中 `${}` 默认安全参数化，需要原样拼接时显式使用 `raw()`。两者默认行为相反。

### dao:FindFirst / dao:FindPage / dao:FindAll 的传参

这些标签的 body 是 EQL 语句（类 SQL），参数通过 `${expr}` 从 xpl 作用域传入：

```
// body 中的 EQL 语句，${item.materialCode} 从 xpl 作用域取值并参数化
<dao:FindFirst xpl:lib="/nop/orm/xlib/dao.xlib">
    select o from ErpMdMaterial o where o.code = ${item.materialCode}
</dao:FindFirst>
```

- EQL 语法类似 SQL，支持 `a.b.c` 风格的关联属性（自动转为 JOIN）
- `${expr}` 在 sql 模式下自动参数化为 `?`，安全防注入
- 需要原样拼接（如动态表名）时用 `${raw(expr)}`

### orm-reader 的传参

orm-reader 仅支持 `<query>` 结构化查询模型（P1-3.3-001 修复后；历史 `<eql>` 节点已移除，见下方查询模型说明）：

**方式 1：query 查询模型（推荐简单场景）**

```
orm-reader entityName="ErpMdMaterial":
    query:
        filter:
            eq(name="status", value="1")
            gt(name="stockQty", value="${minStock}")  // ${} 同样参数化
        orderBy:
            field(name="code")
```

> **查询模型说明（P1-3.3-001，plan 2381）**：`orm-reader` 仅支持 `<query>` 结构化查询模型
> （filter/orderBy）。历史版本的 `<eql>` 节点因引擎从未接线（声明后被静默丢弃→全表扫描）已从
> DSL 移除；`dao:FindFirst` 等标签的 body 直接是 EQL 文本，与此处无关。orm-reader 未声明
> `<query>` 时会全表加载（运行时打 `nop.batch.orm-reader-no-query` WARN）。

**关键点**：
- `inject('beanName')` 可获取任何 IoC Bean，包括 `I*Biz` 接口
- `dao:SaveEntity` / `dao:FindFirst` 等标签来自 `/nop/orm/xlib/dao.xlib`
- processor 中可调用多次 `consume(item)` 输出多个结果
- 多个 processor 按 `order` 属性升序执行（P1-3.3-002 接线，plan 2381；此前声明序即执行序、order 无效）
- consumer 中通过 `filter` 匹配不同的输出

## 常用 loader 类型

| loader 类型 | 用途 | 关键配置 |
|-------------|------|----------|
| `file-reader` | CSV/Excel 文件读取 | `filePath`、`fileModelPath`、`headers` |
| `orm-reader` | ORM 查询读取 | `entityName` + `<query>`（结构化查询模型；`<eql>` 已移除，P1-3.3-001） |
| `jdbc-reader` | 原生 SQL 读取 | `sqlName` + `<sql>`（原生 SQL）或 `<query>`（结构化查询模型） |
| `provider` | 自定义加载器 | 返回 `IBatchLoader` 或 `List` |

## 常用 consumer 类型

| consumer 类型 | 用途 | 关键配置 |
|---------------|------|----------|
| `file-writer` | 文件写出 | `filePath`、`fileModelPath` |
| `orm-writer` | ORM 写入 | `entityName`、`keyFields`、`allowInsert/Update` |
| `jdbc-writer` | 原生 SQL 写入 | `tableName`、`keyFields` |
| `source` | xpl 脚本消费 | 自定义逻辑 |

## 与 nop-job 的集成

nop-batch 的 `<batch:Execute>` 标签可以在 nop-job 的 task 中使用：

```
task steps:
    custom name="runBatch" customType="batch:Execute":
        batch:task taskName="xxx" batchSize="100" saveState="true":
            // batch DSL 配置
```

在 `scheduler.yaml` 中配置 nop-job task，task 内部调用 nop-batch。

## 关键配置项

| 配置项 | 说明 |
|--------|------|
| `taskName` | 任务名称 |
| `taskKeyExpr` | 任务实例区分表达式（同 taskName+taskKey 只允许一个活跃实例） |
| `batchSize` | 每次处理的数据量（Chunk 大小） |
| `concurrency` | chunk 循环并行线程数（配置 `executor` 时提交该池；未配置兜底平台共享 cached 池[上界 `nop.commons.concurrent.cached-pool.maxPoolSize` 缺省 200 + 达界熔断]；超过 `nop.batch.max-concurrency-cap` 缺省 200 装配期 fail-fast） |
| `executor` | 线程池 Bean 名称 |
| `saveState` | 是否保存状态（断点续传） |
| `asyncProcessor` | 是否异步处理 processor |
| `retryPolicy` | 重试策略（maxRetryCount/retryDelay/exponentialDelay） |
| `skipPolicy` | 跳过策略（maxSkipCount/exceptionFilter） |
| `transactionScope` | 事务范围 none/chunk/process/consume（builder 默认 consume；配置 historyStore 时 consume 自动提升为 process 包装） |
| `historyStore` | 记录级处理历史（去重 + 与 consume 同事务写入） |
| `nop.batch.list-loader.warn-size` | provider 返回 List 时的常驻规模 WARN 阈值（缺省 1000000，`<=0` 关闭告警） |
| `nop.batch.max-output-items-per-chunk` | 单 chunk 输出集上界（`useBatchRequestGenerator` 展开面护栏；缺省 `0` = 不限制，保持既有行为；设正值对全部 `BatchProcessorConsumer` 生效，接线点=`BatchTaskBuilder.buildChunkProcessor` 与 `OrmBatchConsumerProvider.setup` 两处） |

## 任务异常终态契约（executeChunkLoop，plan 2496）

`BatchTask` 的 chunk 执行循环守卫起点为任务 lambda 首行（`propagateContext`/`provideTaskContext` 上下文传播段纳入守卫），捕获宽度为 **Throwable**（含 Error）。任意任务体 Throwable 的行为契约：

- 返回 future **异常终态**（根因保留，不再悬挂——调用方 `allOf` 接线可感知失败）；
- `afterComplete` 关闭链（reader/writer/JDBC 连接关闭）在异常路径**保证执行**，资源不泄漏；
- 任务行**不停留在 RUNNING**：primaryError 记录后走既有失败终态语义（fail-fast cancel 并发分支、finally 语义保持不变）。

回归锚点：`nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/impl/TestBatchTaskErrorTerminalization.java`（注入点分别为 processor 与 propagateContext）。

## RUNNING 死区与 fencing 契约（1.7-C7-001，plan 2516 裁定①）

`nop_batch_task` 的单活跃守卫（同 taskName+taskKey 只允许一个活跃实例）带 **stale 租约接管**能力：终态保存失败或进程崩溃留下的 RUNNING 死区行在租约过期后可被新实例接管，不再永久拒启。裁定权威 = 仓库 ai-dev/design/concurrency/wf-task-scanner-fencing-and-task-wf-concurrency.md §3.6 裁定⑥（R12 族 fencing 语义权威：条件 UPDATE 抢占 + rowsAffected==1 单飞判据 + 租约——本批为引用方、引用不重建；按 docs-for-ai 边界规则不直接链接）+ batch-running-deadzone-fencing-and-parameter-guards.md（R2-13 批裁定①）。三个变更点：

- **启动闸门 stale 感知化**（`DaoBatchStateStore.startExistingTask`）：`taskStatus <= RUNNING` 且租约未到期（`restartTime` 非空且大于 `now - nop.batch.task.stale-lease-ms`）的活行维持 `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE` 响亮失败（事中兜底，错误码语义零回退）；`restartTime` 为 NULL 或已过租约期的 stale 行放行进入条件 UPDATE，由 rowsAffected 单飞裁决（竞态下另一实例先行接管时 rowsAffected==0，响亮失败兜底）。KILLED 行仍先行拒绝、不被 fencing 覆写。
- **`tryMarkTaskRunning` WHERE 扩展**：条件 UPDATE 谓词为「`taskStatus > RUNNING` 或（`taskStatus <= RUNNING` 且（`restartTime IS NULL` 或 `restartTime <= staleCutoff`））」——NULL 判 stale（存量死区行 restartTime 恒 NULL，否则缺陷主体不可接管）；接管成功后行内 taskStatus/restartTime/workerId/execCount 原子刷新。
- **租约写点**：insert-path 于 `newTask` 写入初始租约（insert 即持租约，NULL 仅存在于存量死区行）；续租由既有 per-chunk checkpoint 承接（`saveTaskState` store 侧单点写 `restartTime=now`），接管窗 = checkpoint 间隔 + 租约余量。

**僵尸写者 fencing**：`saveTaskState` 为条件 UPDATE（`WHERE sid=? AND exec_count=:myExecCount`，execCount 随每次进入 RUNNING 递增、为天然 fencing token）——rowsAffected==0 即本实例已被接管/重启，本次状态写被丢弃并记 ERROR（`nop.err.batch.task-state-write-lost-fencing`，携 taskId/execCount），不抛出打断清理链、不覆写新实例已刷新的状态列。该实现为 2514 裁定⑥「限定列集或写前 reload」菜单的等价形态（fencing by execCount），偏离登记已回填 2514 设计文档修订记录，不在模块内发明第二套语义。

| 配置键 | 缺省 | 语义 |
|--------|------|------|
| `nop.batch.task.stale-lease-ms` | 600000 | RUNNING 行 stale 接管租约（毫秒）；`<=0` 关闭接管（运维逃生门，等价修复前现状，显式声明非静默降级） |

**观测面影响（AutoTest 快照契约）**：`saveTaskState` 条件 UPDATE 直发 SQL 绕过 ORM 乐观锁与 AutoTestOrmHook——任务行 VERSION 停留在插入态（不随状态写递增），clock/var 列的 `@var:NopBatchTask@*` 快照变量不再经 saveTaskState 注册；AutoTest 快照断言契约为 `END_TIME`/`RESTART_TIME` 用 `@notNull:` 非空断言、`VERSION` 钉插入态值。

## 任务完成清理段契约（onTaskComplete，plan 2516 裁定⑦）

`BatchTask.onTaskComplete` 清理段三处失败路径显式化（不吞不替换）：

- **S4/S6 终态写失败**：err≠null 分支的 `saveTaskState(true, err)` 抛出新异常 X 时，catch + ERROR 留痕（`nop.err.batch.task-terminal-save-fail`，携 taskName/taskId）后放弃本次状态写；X 不替换原始 err（setup 失败路径进入时对外仍以原始 e 结算），`context.completeExceptionally(err)` 必达。终态写失败后的行残留（停留 RUNNING）由上述 stale 接管承接（租约到期即可重入）。
- **S5 metrics 兜底**：`metrics.endTask` 包独立 try（失败 ERROR 留痕，`nop.err.batch.task-metrics-end-fail`），保证 `FutureHelper.complete` 必达——返回 future 不成为孤儿、调用方不挂死；finally 内既有顺序不变（plan 2496/2381 已修面零回退）。

## REE 双层封闭契约（executor 拒绝，plan 2516 裁定⑥）

`executor.execute` 同步拒绝（RejectedExecutionException，注入有界池饱和/停机竞态）被双层封闭，不裸逃逸绕过 onTaskComplete：

- **提交点防御**：`executeChunkLoop` 内 `executor.execute` 包 try/REE，捕获后 `future.completeExceptionally`（该提交 lambda 未执行、无泄漏面）；
- **futures 提交循环包络**：`executeAsync` 提交循环任一提交抛出时，已提交 futures 逐个 `completeExceptionally`（幽灵链显式结算，allOf 立即触发；已运行加载线程由既有 fail-fast cancel 短路）后直达 `onTaskComplete` 既有终态化+关闭链。

## dispatch 容量与 batchSize 护栏契约（plan 2516 裁定②③）

- **dispatch 容量**（`BatchTaskBuilder.buildLoader` 装配期 fail-fast）：`loadBatchSize <= 0` 抛 `ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID`（param 携 loadBatchSize 与 capacity 公式 `loadBatchSize × loadBatchMultiplyFactor` 上下文）——Semaphore(0) 永久挂死/静默空任务在装配期即被拒绝，orm-reader 路径期望终态为 fail-fast 而非挂死。
- **Export/Import 守卫对称**（1.7-C8-002）：`ExportDbTool` 与 `ImportDbTool` 同持 `batchSize > 0` 守卫——缺省 0 不再进入 `adjustBatchSize` 钳位路径（守卫落缺省后使用 builder 缺省 batchSize=100），消除「每行一 chunk」写放大。`adjustBatchSize` 的 `<=0→1` 钳位保留并补每任务一次 WARN（`nop.warn.batch.batch-size-clamped-to-one`），静默兜底可观测。xdef `batchSize` 缺省值维持现状（缺省改 1000 的备选未启用）。

## excel-reader 规模边界与 maxCount 契约（plan 2516 裁定④）

excel-reader（`file-reader`/excel 形态）在读取前将整张 sheet 物化为内存 List（非流式实现），物化行数受 `ExcelIOConfig.maxCountPerSheet` 约束：batch 侧 `FileBatchSupport.newExcelIO` 不设置该值，取缺省 **200,000 数据行**（表头行不计入）；sheet 数据行超出时在解析期抛 `ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT`，任务失败而非 OOM 或静默截断（契约见 `nop-report.md`「Excel 导入行数上界」）。**行为变更声明**：此前超过 20 万行的 excel 会被全量物化读取，现在显式失败；更大数据量须走 csv/jdbc-reader（batch DSL 当前未暴露 maxCountPerSheet 配置位）。`maxCountExpr` 已恢复接线（`FileBatchSupport.newExcelReader` → loader），它截断的是进入处理的条目数，作用在物化之后——不能用来绕开上述物化上界：**行为变更声明——已配置 maxCount 的链路从全量读取变为截断读取**（接线恢复前该配置无效、数据全量消费；恢复后超出 maxCount 的条目不再进入处理）。

## jdbc-reader fetchSize 语义契约（plan 2516 裁定⑤）

`jdbc-reader` 的 DSL `fetchSize` 配置在 provider 层独立兜底生效（`JdbcBatchLoaderProvider`：显式配置即 `ps.setFetchSize` 必达；streaming 分支优先不变；sql 层 fetchSize 归 JdbcHelper 既有职责）。修复前「query/sql 文本路径 fetchSize 永不生效」的坏门已闭合；`streaming=false` 且 fetchSize 未配置的路径打一次性 WARN（`nop.err.batch.jdbc-fetch-size-unset`）——驱动全量缓冲结果集的内存风险可观测，大数据量查询应显式配置 fetchSize。

## sys-event 触发链守卫覆盖契约（plan 2516 裁定⑧）

sys-event 普通事件触发链（`non-broadcast-consumer.batch.xml`）已配置 `saveState="true"` + 固定 taskKeyExpr（`'non-broadcast'`，全部署单活跃键）+ `allowStartIfComplete="true"`（cron 重复触发的已完成任务允许重入，并发重叠由启动闸门行级条件 UPDATE 裁决）——(taskName, taskKey) 唯一键与启动闸门（含 stale 接管）结构性覆盖该触发链。契约句：`saveState` 未配置（非 true）时 `ModelBasedBatchTaskBuilderFactory` 不接 stateStore、启动守卫结构性旁路——需要单活跃守卫的任务必须显式配置 `saveState` 与确定性 `taskKeyExpr`（无 taskKeyExpr 时 UUID 键使守卫永不冲突，为无状态任务既有语义）。跨单元前提：重叠防护同时依赖 nop-job 调度语义与 nop-sys claim 原子性，batch 侧守卫为批内兜底。

## 账本保留策略（1.7-C7-003，plan 2502）

`nop_batch_task` / `nop_batch_task_var` / `nop_batch_record_result`（及级联面 `nop_batch_file`）由 nop-batch-dao 的 `DaoBatchLedgerCleaner` 周期清理（容器装配单例 bean → `LifeCycleSupport` 生命周期链 → `timer.executeOn(worker)` 绝缘调度，taskName=`nop-batch-ledger-cleanup`）。**与 nop-retry 1.6-C7-001 账本表清理同设计同参（批次对齐声明：batch-proposal §四「1.7 C7-003 与 1.6 C7-001 账本保留族批次对齐」；参数 SSOT = 仓库 ai-dev/design/concurrency/ledger-retention-policy.md §3.2/§3.4，平台内部文档——按 docs-for-ai 边界规则不直接链接；保留策略语义见 [nop-retry.md](nop-retry.md)「账本保留策略」节）**。模块配置键：

| 配置键 | 缺省 | 语义 |
|--------|------|------|
| `nop.batch.ledger.retention-days` | 30 | 保留期（天）；`0`=显式禁用周期清理；负值启动期 fail-fast |
| `nop.batch.ledger.cleanup-interval` | 1h | 清理触发周期；下限 60s，低于下限启动期 fail-fast |
| `nop.batch.ledger.delete-batch-size` | 1000 | 单轮单表删除上限（有界拾取+IN 批删）；非正数启动期 fail-fast |

清理谓词与保留语义：

- **终态任务行**：锚列 `endTime`、谓词 `taskStatus` 为终态（COMPLETED/FAILED/CANCELLED/KILLED），超期整行删除；其 `nop_batch_task_var` / `nop_batch_file` / `nop_batch_record_result` 行按 `batchTaskId` 级联删除。
- **record_result 独立 TTL 通道**：锚列 `createTime`，即使任务行长期非终态，成功记录账本行超期后仍按 TTL 批删（UUID taskKey 任务行写完即死数据的增长面由此收敛）。
- **不清理**：非终态（CREATED/RUNNING/SUSPENDED）任务行及其级联行。
- **幂等语义窗口即保留窗**：`filterProcessed` 的记录级去重依赖 record_result 行存在；超期清除后，同 taskKey 任务的已处理记录可能被重新处理——需要更长幂等窗口的部署应调大 `nop.batch.ledger.retention-days`。
- 清理失败显式可见：失败轮 ERROR（`nop.batch.ledger.cleanup-fail`）+ 失败计数，周期链由绝缘层保障不断链。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-batch/model/nop-batch.orm.xml` |
| DSL Schema | `nop-kernel/nop-xdefs/.../nop/schema/task/batch.xdef` |
| 运行时标签库 | `nop-batch/nop-batch-dsl/.../nop/batch/xlib/batch.xlib` |
| 批处理任务执行体（异常终态守卫） | `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java` |
| 状态存储（stale 接管/租约 fencing，1.7-C7-001） | `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java`（闸门守卫+staleCutoff :133-161、单飞判据 `tryMarkTaskRunning`/`isLiveLease` :227-256、checkpoint 续租与 execCount 条件 UPDATE :314-356） |
| dispatch 容量护栏（1.7-C8-001） | `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java`（`buildLoader` fail-fast :345-352）+ `BatchErrors.java`（`ERR_BATCH_DISPATCH_LOAD_BATCH_SIZE_INVALID` :66） |
| batchSize 钳位 WARN（1.7-C8-002） | `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/processor/BatchChunkProcessor.java`（:101）+ `nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java`（守卫 :279-282） |
| excel-reader maxCount 接线（1.7-C5-001） | `nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/manager/FileBatchSupport.java`（`newExcelReader` :150-155） |
| jdbc-reader fetchSize 兜底（1.7-C5-002） | `nop-batch/nop-batch-jdbc/src/main/java/io/nop/batch/jdbc/loader/JdbcBatchLoaderProvider.java`（:248-258） |
| sys-event 触发链守卫接线（1.7-C7-011） | `nop-batch/nop-batch-sys/src/main/resources/_vfs/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml`（saveState/taskKeyExpr/allowStartIfComplete） |
| 账本保留期清理（1.7-C7-003） | `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/cleanup/DaoBatchLedgerCleaner.java` |
| 批处理任务文档 | `docs/dev-guide/batch/batch-task.md` |
| 设计理念 | `docs/theory/why-springbatch-is-bad.md` |

## 典型使用场景

- 大批量数据导入（CSV/Excel → 数据库）
- 定期数据同步
- 批量报表生成
- 批量消息发送
- 过账兜底扫描（processor 调用 I*Biz）
- sys-event 普通事件触发器（`nop-batch-sys` 反向依赖 `nop-sys-dao`，通过 batch DSL `.batch.xml` + `IBatchTaskManager` + `findNext` keyset pagination 周期扫描与触发执行；`SysEventBatchTrigger` 绕过 `BatchTaskRunner` 直接使用 `IBatchTaskManager` API 加载 DSL 任务）
- 资产折旧批量计提
- 银行流水导入

## 相关文档

- `../reusable-modules-overview.md`
- `docs/dev-guide/batch/batch-task.md`（DSL 详细指南）
- `docs/theory/why-springbatch-is-bad.md`（设计理念）


## 读侧语义与过载可观测契约（plan 2546 R4-12）

- **断点续传的适用范围（1.7-C7-006）**：`completedIndex` 的推进点全仓仅在 **file/excel reader**（`ResourceRecordLoaderProvider`，且需 `saveState=true`）与 exp 自有 `EtlTaskStateStore`。**jdbc/orm/generator reader 的 `saveState` 只持久化任务级计数与终态，不推进 `completedIndex`——重启会全量重跑**（正确性由 at-least-once + `historyStore` 幂等承接）。任务级 `saveState=true` 配非 file/excel reader 时，装配期打 `nop.warn.batch.save-state-unsupported-reader`。
- **orm-reader keyset 契约（1.7-C7-007）**：游标锚点是**排序键值快照**（orderBy 列 + 主键，取自 `dao.getPkColumnNames()` 的 prop 名），不是上一页的活实体引用——因此本任务就地修改 orderBy 列**不会**导致游标漂移。反向约束：orderBy 列在任务运行期应保持不可变（若变更，快照仍按变更前值继续，语义正确但可能重复读变更行，由 historyStore 去重）；排序列为 NULL 时平台 keyset 契约抛 `ERR_ORM_CURSOR_SORT_VALUE_NULL`（任务确定性失败，需通过过滤或列约束规避）。
- **jdbc-reader 两形态（1.7-C7-008/C7-009）**：`<jdbc-reader>` 页模式以 **offset 翻页**——每页扫描代价随进度线性增长（全任务 O(N²/2B)），且并发删除源表行会导致区间前移**跳行**；仅适用于静态快照表（首次 load 打 `nop.warn.batch.jdbc-page-offset-paging`）。流式模式（`streaming=true`）整任务持有**单条物理连接**，故任务时长必须 ≤ 数据库最大空闲/存活时长；连接级失效（SQLState `08` 前缀、`SQLRecoverableException`/`SQL*ConnectionException`，或方言 `transient-connection-fail`/`recoverable-data-access`）会把 loader 置为 broken 并**重抛**，后续 load 不再返回空列表（避免把故障静默成 EOF）。
- **跳过语义（1.7-C7-005）**：`skipPolicy.maxSkipCount` 的 **0 = 显式不设上限**（负值装配期 fail-fast）；被跳过的记录**不进** `completedItems`、也**不进** `historyStore`，故任务终态打 `nop.warn.batch.task-skipped-items` 汇总规模。**跳过条目没有死信账本**（属已知边界，见 backlog successor）。
- **KILLED 撤离门（1.7-C7-012）**：`cancel()` 缺省 reason 为 `kill` → 任务行进入 KILLED 且此后无法重启动。程序化 API 可设 `allowRestartIfKilled=true`（`BatchTaskBuilder.allowRestartIfKilled` / `IBatchTaskContext.setAllowRestartIfKilled`）旁路该拒绝，重启由 `execCount` fencing 承接（旧实例的状态写被丢弃并 ERROR 留痕）。**DSL 键面尚未暴露**（需 nop-xdefs 变更）。
- **过载可观测（1.7-C8-004/C8-007/C8-008）**：
  - **任务级 metrics**：`BatchTask.executeAsync` 在 `setTaskName` 之后自动装配 `BatchTaskMetricsImpl`（`nop.batch.metrics.enabled` 缺省 true；外部已 `context.setMetrics(...)` 时不覆盖）。meter 名 `nop.batch.task`/`nop.batch.chunk`/`nop.batch.load`/`nop.batch.item.*` 等 14 个（10 Timer + 4 Counter），tags = `job.name`/`task.name`(+`status`)。基数上界 = 不同 taskName 数 × 14。
  - **重试与装载计数**：`RetryConsumeHelper` 每次重试调 `metrics.retry(1)`（此前恒 0）；`getLoadItemCount()` 返回**条目数**（此前返回 Timer 采样次数=chunk 次数，错几个数量级）。注意 `getProcessItemCount()` 仍是同型错源（返回 process Timer 次数），已登记 residual。
  - **dispatch 水位**：`PartitionDispatchQueue` 提供 `getCount()`/`getAvailablePermits()`/`getPartitionCount()`；异步 fetch 派发注册 Gauge `nop.batch.dispatch.backlog`（积压条数）与 `nop.batch.dispatch.available-permits`，tags = `task.name` + `exec.id`（`exec.id` 取 taskId → taskKey → `anon-<identityHashCode>`，保证唯一且不因 taskId 为 null 而失败），任务结束精确注销。capacity 只作 description。
- **dispatch 流控旋钮（1.7-C8-009）**：`BatchDispatchConfig.loadBatchMultiplyFactor`（队列容量倍数，缺省 30）与 `maxLockQueueCountPerThread`（单次 take 最大认领分区数，缺省 100）现可通过程序化 API 配置：**0 = 未配置（保留缺省）**，负值 fail-fast，正值透传；两个 Provider 的 setter 另有 `>=1` 守卫。**DSL（xdef）尚未暴露**。
- **两套 executor 形态与护栏（1.7-C8-010）**：`biz` 导出链为**有界 + 双向钳制**（`concurrency`/`batchSize` 超上界截断、`<=0` 归一到 1/maxBatchSize）；`core`/`exp` 链兜底平台共享 cached 池（上界 `nop.commons.concurrent.cached-pool.maxPoolSize` 缺省 200 + 达界熔断），exp 自建池另加 `threadCount × concurrencyPerTable <= nop.batch.max-concurrency-cap`（缺省 200）装配期校验（`threadCount<=1` 走同步池，短路）。dispatch 队列内存面积 = `loadBatchSize × loadBatchMultiplyFactor`（缺省 30×）。

## 生成器并发与生命周期契约（plan 2545 R4-11）

- **嵌套批任务上下文（1.7-C1-001）**：`BatchTaskGlobals` 的 ThreadLocal 在 `BatchTask` 的两处 provide 站点改为**保存/恢复**语义（`saveTaskContext()` / `restoreTaskContext(prev)`）。原裸 `provide + remove` 在「processor 内同步执行子批任务」的嵌套组合下会确定性清空外层上下文（xpl 侧 `onChunkBegin` 等助手随后 NPE 或静默失聪）。**语义**：批任务可嵌套执行，内层结束后外层上下文自动恢复；外层本无上下文时恢复为无上下文。
- **生成器游标并发（1.7-C1-002 / 1.7-C1-006）**：`BatchGenState` 的 `hasNext/getGenCount/getSubIndex/next` 与 `SequentialBatchRequestGenerator` 的 `nextRequest/onResponse` 全部**实例级 synchronized**。前者是共享游标（`sequential` + `useBatchRequestGenerator` + `concurrency>1` 时多 chunk 线程在 loader 锁外共享同一组 subCases），后者在 `asyncProcessor=true` 下 `nextRequest`（chunk 线程）与 `onResponse`（异步回调线程）并发驱动同一实例的 `subIndex/subContext`。锁粒度 = 载体本身，不跨 chunk。
- **swap-on-fire 注册守卫（1.7-C1-003，batch 侧）**：`onTaskBegin` 在 `fireTaskBegin` 之后注册将**显式抛错**（`nop.err.execution-task-begin-already-fired`），不再静默接受后永不触发。平台 `ExecutionContextImpl.onBeforeComplete/onAfterComplete` 有同型缺口，但属 Protected Area，登记为后续 plan-first 批次（见 plan 2545 Deferred）。
- **单 chunk 输出集上界（1.7-C5-003）**：`BatchProcessorConsumer` 的输出累积受配置键 `nop.batch.max-output-items-per-chunk` 约束（缺省 `0` = 不限制以保既有行为；正值 = 超限 fail-fast `ERR_BATCH_TOO_MANY_PROCESSING_ITEMS`）。接线点两处：`BatchTaskBuilder.buildChunkProcessor`（主装配链）与 `OrmBatchConsumerProvider.setup`（自建 `BatchProcessorConsumer` 的旁路，独立接线）。`useBatchRequestGenerator` 路径下单个 chunk item 会循环展开 `totalCount` 条输出——`batchSize` 只约束输入条数，不约束展开数，本上界是该路径的内存护栏。
- **provider 返回 List 的常驻包络（1.7-C5-004）**：`ListBatchLoader` 持有该 List 直至任务结束（常驻时长 = 任务全程，内存 = 整表）。规模达 `nop.batch.list-loader.warn-size` 即 WARN（只告警不拒绝——拒绝会破坏既有合法大表导出）。大表场景应改用分页 loader。
- **exp 工具资源窗与优雅停机（1.7-C6-003/C6-004/C6-007）**：`ExportDbTool`/`ImportDbTool` 的 executor 创建、`beforeExport`/`beforeImport`、`syncConfigWithDb` 全部纳入 try——任一抛出都能到达 finally 的释放段（Import 侧的 HikariDataSource 不再泄漏）。`finally` 在 `destroy()` 后做**有界等待**（stats 活跃/队列双零即视为已停，上限 30s，超时 WARN），使「`execute()` 返回即已停」成立，避免返回后兄弟表任务仍并发续写状态文件。
- **chunk 压实 loader + 异步 fetch 派发组合不受支持（1.7-C6-008）**：判定轴是 **loader 的 chunk 压实能力**（`IBatchLoaderProvider.hasChunkCompaction()`），而非 task 级 `stateStore`。`saveState` 有两条独立轴：任务级（`stateStore`，只存计数/终态）与 loader 级（`<loader saveState="true">` → `ResourceRecordLoaderProvider.saveState`，在 chunk 级 afterComplete 压实 `processingItems` 并推进 `completedIndex`）。只要 loader 声明了 chunk 压实且 `dispatcher.fetchThreadCount>0`，装配期即抛 `nop.err.batch.save-state-with-dispatcher-unsupported`。根因：分区派发下 load 与 consume 在时间/线程上解耦，压实回调只能附着到 fetch 线程自建的一次性 chunk context，而 `getChunkItems()` 仅由消费线程的 `BatchChunkProcessor.loadItems` 写入——fetch ctx 恒为 null，压实必然空转，`completedIndex` 永不推进，任务 100% 以 `ERR_BATCH_PROCESSING_ITEMS_NOT_EMPTY` 失败。**修复形态**：不实现「假压实」（对空 ctx 调 `complete()` 只会掩盖语义缺口；若为 fetch ctx 填入 chunkItems 又会在数据未消费时推进 `completedIndex`，把 at-least-once 降级为 at-most-once），改为装配期响亮失败。需要断点续传时请去掉 dispatcher 改用 `concurrency`；同步派发（`fetchThreadCount<=0`）下 fetcher 闭包捕获消费线程 ctx，压实正常。**不承担 chunk 压实的 loader（jdbc/orm/列表等）** 在 task 级 `saveState` + 异步派发下仍合法，不被本守卫拒绝。
- **重试语义边界（1.7-C7-004）**：`maxRetryCount` 负值在 `RetryPolicy` 中语义为「无限重试」（与多数框架 `-1=禁用` 惯例相反），属配置陷阱——现于 `BatchRetryPolicyModel` 装配期显式失败。同时移除 `AbstractRetryBatchConsumer` 入口处的 `checkRetry(e, 0)` 预检查（它只 sleep 并消耗一次计数却不消费，使实到尝试次数比声明多 2 次且与加载链计数不一致）；重试次数统一由 `retryConsume` 循环承载。
