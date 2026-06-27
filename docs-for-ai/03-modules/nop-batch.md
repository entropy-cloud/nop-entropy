# nop-batch — 批处理引擎

## 功能概览

企业级批处理引擎，支持大数据量场景。核心设计思想：按 Chunk（批次）读取→处理→写入，每个 Chunk 独立事务，支持断点续传与记录级幂等。

- **Chunk 处理模式**：按批次读取→处理→写入，内存友好
- **文件输入/输出**：支持 CSV/Excel 文件作为数据源或输出
- **ORM/JDBC 读写**：内建 `orm-reader`/`orm-writer`、`jdbc-reader`/`jdbc-writer`
- **断点续传**：通过 `completedIndex` 记录进度，中断后可恢复
- **记录级幂等**：`NopBatchRecordResult` 表追踪每条记录状态，`recordKey` 唯一标识
- **重试/跳过**：`retryPolicy` + `skipPolicy` 内建支持
- **并发处理**：`concurrency` + `executor` 线程池
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

orm-reader 支持两种查询方式：

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

**方式 2：eql 原始 EQL（复杂查询）**

```
orm-reader entityName="ErpMdMaterial":
    eql: select o from ErpMdMaterial o where o.status = ${status} and o.stockQty > ${minStock}
```

> **关键区别**：`dao:FindFirst` 等标签的 body 直接是 EQL 文本；`orm-reader` 的 `<eql>` 也是 EQL 文本，但 `<query>` 是结构化的查询模型（filter/orderBy）。

**关键点**：
- `inject('beanName')` 可获取任何 IoC Bean，包括 `I*Biz` 接口
- `dao:SaveEntity` / `dao:FindFirst` 等标签来自 `/nop/orm/xlib/dao.xlib`
- processor 中可调用多次 `consume(item)` 输出多个结果
- consumer 中通过 `filter` 匹配不同的输出

## 常用 loader 类型

| loader 类型 | 用途 | 关键配置 |
|-------------|------|----------|
| `file-reader` | CSV/Excel 文件读取 | `filePath`、`fileModelPath`、`headers` |
| `orm-reader` | ORM 查询读取 | `entityName` + `<query>`（结构化查询模型）或 `<eql>`（原始 EQL） |
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
| `concurrency` | 并发线程数（需配合 executor） |
| `executor` | 线程池 Bean 名称 |
| `saveState` | 是否保存状态（断点续传） |
| `asyncProcessor` | 是否异步处理 processor |
| `retryPolicy` | 重试策略（maxRetryCount/retryDelay/exponentialDelay） |
| `skipPolicy` | 跳过策略（maxSkipCount/exceptionFilter） |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-batch/model/nop-batch.orm.xml` |
| DSL Schema | `nop-kernel/nop-xdefs/.../nop/schema/task/batch.xdef` |
| 运行时标签库 | `nop-batch/nop-batch-dsl/.../nop/batch/xlib/batch.xlib` |
| 批处理任务文档 | `docs/dev-guide/batch/batch-task.md` |
| 设计理念 | `docs/theory/why-springbatch-is-bad.md` |

## 典型使用场景

- 大批量数据导入（CSV/Excel → 数据库）
- 定期数据同步
- 批量报表生成
- 批量消息发送
- 过账兜底扫描（processor 调用 I*Biz）
- 资产折旧批量计提
- 银行流水导入

## 相关文档

- `../reusable-modules-overview.md`
- `docs/dev-guide/batch/batch-task.md`（DSL 详细指南）
- `docs/theory/why-springbatch-is-bad.md`（设计理念）
