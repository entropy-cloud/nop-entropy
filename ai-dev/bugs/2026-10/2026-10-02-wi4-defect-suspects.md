# 2026-10-02 WI4 测试暴露的产品缺陷嫌疑清单（已修复/裁定，plan 2306 收口）

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


## Fix（2026-10-03 plan 2306 回填）

- **1 initRefs 集合注册时序：`fixed`**。第一遍循环（initRef）在 collectionName 仍为 null 时注册 collectionMap，第二遍设置名字但不回填。修复：第二遍 setCollectionName 后立即 `collectionMap.put`，删除 initRef 的过早注册。回归：TestOrmModelInitializerRefValidation.testToManyCollectionRegisteredUnderBuiltCollectionName（roundtrip + 断言无 null key）。nop-orm-model 32 测试全绿。
- **2 computeValue 裸 IAE：`fixed`**。声明参数缺失时先抛 `NopException(ERR_ORM_COMPUTE_PROP_ARG_MISSING)`（新错误码 `nop.err.orm.compute-prop-arg-missing`，已在 OrmModelErrors 注册声明），不再让 castBeanToType(null) 抛裸 IAE。回归：TestOrmComputePropModel.testComputeValueMissingDeclaredArgThrowsTypedError（错误码+entityName/propName/argName 三参数断言）。
- **3 JdbcTransactionFactory.openConnection 不参与事务：`adjudicated-not-a-defect`（文档告警路线）**。设计裁定：改为挂接活跃事务会把连接关闭职责转移给事务，破坏既有消费方（TransactionTemplateImpl/ITransactionTemplate）生命周期契约，风险大于收益。处置：ITransactionManager.openConnection 与 JdbcTransactionFactory.openConnection 双侧 javadoc 显式告警"独立连接不参与事务，事务内必须经 IJdbcTransaction.getConnection()"。docs-for-ai 事务文档无需变更（openConnection 公开语义未变，仅补契约说明）。
- **4 buildPageExpr 缺 OFFSET 0：`fixed`**。offsetExpr 为 null 且 limitExpr 非空时补 `OFFSET 0 ROWS` 前置子句（SQL 标准要求）。回归：新增 TestOffsetFetchPaginationHandler（limit-only 必含 OFFSET 0 ROWS；offset+limit 用 NEXT 形态；两者皆空不追加）。

## Affected Files

- nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmModelInitializer.java
- nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmModelErrors.java（新错误码）
- nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmComputePropModel.java
- nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/pagination/OffsetFetchPaginationHandler.java
- nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/ITransactionManager.java + nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransactionFactory.java（javadoc 裁定）
- 对应 src/test 下 TestOrmModelInitializerRefValidation / TestOrmComputePropModel / TestOffsetFetchPaginationHandler
