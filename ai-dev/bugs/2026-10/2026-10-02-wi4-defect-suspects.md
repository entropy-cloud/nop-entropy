# 2026-10-02 WI4 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

> 来源：plan 2296（WI4 persistence 核心域补强）执行期发现。按 roadmap 硬边界记录，修复独立立项。

## 1. OrmModelInitializer.initRefs 集合注册时序（P1 嫌疑）

- Problem：`io.nop.orm.model.OrmModelInitializer.initRef` 第一遍循环用 `ref.getCollectionName()`（此时仍为 null）注册 collectionMap，第二遍循环才 `setCollectionName("Entity@prop")` 且不再回填 map。
- 复现：手工 OrmModel + to-many ref（不 setCollectionName）→ `init()` → `getCollectionModel("Entity@prop")` 返回 null（map 以 null 为 key）。
- 影响：静态 OrmModel 的该初始化入口实际不可用；LazyLoadOrmModel 的解析兜底掩盖了动态路径。

## 2. OrmComputePropModel.computeValue 声明参数缺失抛裸异常（P2 嫌疑）

- Problem：args 缺少任一声明参数时 `BeanTool.castBeanToType(null, type)` 抛裸 `IllegalArgumentException("IsNull:src")`，违反 error-handling.md 两级错误处理约定（应为 NopException + 错误码 + param）。
- 复现：单声明参数的 compute prop，`computeValue(entity, emptyMap())`。

## 3. JdbcTransactionFactory.openConnection 不参与事务（设计歧义，P2 嫌疑）

- Problem：`ITransactionManager.openConnection(querySpace)` 返回池中全新连接（autocommit），不挂接该 querySpace 已注册事务——事务内经此连接写入 rollback 无法撤回（dao 测试迭代中实测复现：rollback 后行仍可见）。
- 影响：事务内连接必须走 `IJdbcTransaction.getConnection()`，公开 API 存在易误用面。修复裁定（改语义 vs 文档告警）需设计评审。

## 4. OffsetFetchPaginationHandler.buildPageExpr 缺 OFFSET 0（P3 嫌疑）

- Problem：offset 为空、limit 非空时输出 `FETCH FIRST n ROWS ONLY` 而缺 SQL 标准要求的 `OFFSET 0 ROWS` 前置子句（`getPagedSql` 路径有处理，`buildPageExpr` 路径没有）。
- 影响：部分数据库（如 SQL Server + SQL Standard 方言）对无 OFFSET 的 FETCH FIRST 语法报错。
