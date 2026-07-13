## 变更 2026-07-13
* **破坏性变更**: nop-job 的 `scheduler.yaml` 每个 job 必须显式启用，job 定义改为按文件扫描 (commits: 即将提交)
  - `LocalJobConfig` 新增 `enabled` 字段，**Java 默认值为 `false`**
  - `LocalJobConfigLoader` 加载时跳过 `enabled != true` 的 job（记 INFO 日志 `nop.job.local-config-loader.job-disabled`），不注册到 scheduler
  - **job 定义改为按文件扫描**：`/nop/job/conf/<jobName>.job.yaml`，每个文件 = 一个 job。`scheduler.yaml` 仅保留全局 `enabled` 开关。`LocalJobConfigLoader.init()` 同时支持旧机制（`scheduler.yaml.jobs` 内联）和新机制（目录扫描），同名 job 第二次出现时记 WARN 跳过
  - 平台内置 job 全部迁移到 `.job.yaml` 形式：`sys-event-batch-consumer`、`wf-due-task-scan`、`wf-remind-task-scan`
  - 每个 job 的 `enabled` 与 `trigger.cronExpr` 统一用 `@cfg:nop.job.<jobName>.<field>|<default>` 绑定到独立配置项，缺省值在 `|` 后给出
  - **迁移指南**: 升级后所有内置 job 默认不运行。应用层必须在 `application.yaml` 显式添加：
    ```yaml
    nop:
      job:
        sys-event-batch-consumer:
          enabled: true   # jobName 对应的配置 key
        wf-due-task-scan:
          enabled: true
    ```
  - 兼容性: 自定义 `scheduler.yaml` 中未写 `enabled` 字段的 job 也将默认不启用；如需保留旧行为（自动启用），显式写 `enabled: true`。旧的 `scheduler.yaml.jobs` 内联写法仍可工作但不推荐，新加 job 一律走 `.job.yaml`

## 变更 2026-03-31
* **数据库方言与迁移能力统一**: `IJdbcTemplate` 新增对象存在性 API，并下沉到 dialect 模板驱动 (commits: c11b5dff1, 279e02f1b, 85a59606a, b7969c80d)
  - 新增接口: `existsTable(querySpace, schemaName, tableName)`, `existsColumn`, `existsIndex`, `existsForeignKey`, `existsSequence`, `existsView`
  - 方言扩展: `dialect.xdef` 与主流 `*.dialect.xml` 增加 `tableExists/columnExists/indexExists/foreignKeyExists/sequenceExists/viewExists` 模板
  - 调用迁移: `nop-db-migration` precondition checker 改为统一调用 `IJdbcTemplate` 新 API，不再在 main 路径手写 `INFORMATION_SCHEMA` exists SQL
  - 兼容性: 旧接口 `existsTable(querySpace, tableName)` 保持可用，内部委托到新重载（`schemaName=null`）

## 变更 2026-03-04
* **数据库变更**: nop-sys模块VERSION字段从INTEGER升级为BIGINT (commit: af4dbaf54)
  - 迁移指南: 执行ALTER TABLE修改字段类型，代码中version从Integer改为Long
  - 兼容性: 数据完全向后兼容，但JDBC返回类型变为Long

## 特性 2026-03-04
* 新增nop.cluster.name配置项，支持物理机房隔离 (commit: 19b1f98b0)
* 新增配置表达式解析器，支持${...}语法引用其他配置 (commit: 5bb9ffbcb)
* 新增路由**通配符语法糖，自动转换为{*path} (commit: d73f6ead7)
* 增强EQL集合操作符，支持AND/OR条件组合 (commit: 2ddbeee37)


# 更新日志

## 特性 2023-11-2
* 增加启用租户机制的全局开关nop.orm.enable-tenant-by-default
* 增加NopSpringTransactionFactory，继承Spring的事务管理

## 变更 2023-08-19
* 重构项目结构，将meta文件保存到独立的meta模块中。已经生成的代码需要按照NopMigrateTask中的做法进行迁移