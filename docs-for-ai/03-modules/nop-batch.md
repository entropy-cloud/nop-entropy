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
| `nop-batch-web` | Web 层与 AMIS 页面 |

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

excel-reader（`file-reader`/excel 形态）在读取前将**全 sheet 物化为内存 List**——这是已声明的包络边界而非流式实现：大数据量（XLSX 单 sheet 上限 1,048,576 行）存在确定性内存压力，大数据量场景应走 csv/jdbc-reader 或显式配置 `maxCountExpr` 截断。`maxCountExpr` 已恢复接线（`FileBatchSupport.newExcelReader` → loader）：**行为变更声明——已配置 maxCount 的链路从全量读取变为截断读取**（接线恢复前该配置无效、数据全量消费；恢复后超出 maxCount 的条目不再进入处理）。物化面残留为已登记的 watch-only 观察项。

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
