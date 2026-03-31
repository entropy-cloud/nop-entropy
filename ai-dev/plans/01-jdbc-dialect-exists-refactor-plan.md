# IJdbcTemplate Exists 能力下沉 Dialect 的重构执行计划

## 1. 背景与目标

当前 `IJdbcTemplate` 仅提供 `existsTable`，其他对象（列、索引、外键、序列、视图）存在性判断在业务模块中分散实现，容易出现：

- 方言适配重复
- SQL 兼容性维护成本高
- 不同模块行为不一致

本次重构目标：

1. 在 `IJdbcTemplate` 增加与 `existsTable` 对齐的存在性接口。
2. 将各类对象存在性 SQL 统一由 `dialect.xdef` + `*.dialect.xml` 配置驱动。
3. `JdbcTemplateImpl` 负责统一执行与回退策略，业务模块不再拼接方言 SQL。
4. 保持兼容性，避免对现有调用方造成破坏性升级。

---

## 2. 范围

### 2.1 In Scope

- `nop-kernel/nop-xdefs`：扩展 `dialect.xdef`
- `nop-persistence/nop-dao`：
  - `IJdbcTemplate`
  - `JdbcTemplateImpl`
  - `IDialect` / `DialectImpl`（必要时）
  - 各 `*.dialect.xml` 的存在性 SQL 模板配置
- `nop-persistence/nop-db-migration`：迁移 precondition 存在性判断到新 API
- 相关单测与集成测试

### 2.2 Out of Scope

- 与存在性判断无关的 ORM/DDL 逻辑重构
- 非 JDBC 数据源通道改造
- 大规模接口命名统一（仅处理本次新增能力）

---

## 3. 总体设计

### 3.1 API 设计（建议）

在 `IJdbcTemplate` 增加以下方法（保留现有 `existsTable(querySpace, tableName)`）：

- `boolean existsTable(String querySpace, String schemaName, String tableName)`
- `boolean existsColumn(String querySpace, String schemaName, String tableName, String columnName)`
- `boolean existsIndex(String querySpace, String schemaName, String tableName, String indexName)`
- `boolean existsForeignKey(String querySpace, String schemaName, String tableName, String foreignKeyName)`
- `boolean existsSequence(String querySpace, String schemaName, String sequenceName)`
- `boolean existsView(String querySpace, String schemaName, String viewName)`

兼容策略：

- 保留旧方法，不移除。
- 旧方法内部委托到新重载（schemaName 传 `null`）。

### 3.2 方言配置设计

在 `dialect.xdef` 新增存在性模板配置节点（建议名：`existsSqls`），例如：

- `tableExists`
- `columnExists`
- `indexExists`
- `foreignKeyExists`
- `sequenceExists`
- `viewExists`

模板变量建议：

- `schemaName`
- `tableName`
- `columnName`
- `indexName`
- `constraintName`
- `sequenceName`
- `viewName`

说明：

- 并非所有 dialect 都必须提供所有模板；未提供时走回退策略。
- 模板推荐输出可用于 `exists(SQL)` 的查询（例如 `select 1 ...`）。

### 3.3 执行策略

`JdbcTemplateImpl` 增加统一内部方法：

- 先从 `dialect` 获取对应 exists 模板并渲染 SQL。
- 使用 `ISqlExecutor.exists(SQL)` 执行判断。
- 模板缺失或执行受限时采用回退：
  - `tableExists` 优先保留现有稳定实现。
  - 其他对象优先 JDBC `DatabaseMetaData`，必要时再方言特判。

---

## 4. 分阶段执行

### Phase 0 - 预备与基线

1. 建立基线测试（db-migration + dao 相关）。
2. 统计现有散落存在性 SQL 调用点。
3. 明确各数据库（H2/MySQL/PostgreSQL/MSSQL/Oracle）的支持矩阵。

输出：

- 调用点清单
- 支持矩阵草稿

### Phase 1 - 扩展 Schema 与生成模型

1. 修改 `nop-kernel/nop-xdefs/.../dialect.xdef`：新增 `existsSqls` 结构。
2. 在 `nop-xdefs` 执行安装，发布新 schema。
3. 在 `nop-dao` 执行安装，触发 `DialectModel` 相关 `_gen` 代码更新。

关键命令：

- `cd nop-kernel/nop-xdefs && ../../mvnw install`
- `cd nop-persistence/nop-dao && ../../mvnw install`

验收：

- 生成模型含新字段（`DialectModel` 及 `_gen` 类可见新增属性）。

### Phase 2 - DAO API 与实现

1. 扩展 `IJdbcTemplate` 接口。
2. 在 `JdbcTemplateImpl` 实现新增方法与统一执行器。
3. 保持旧接口兼容委托。
4. 处理 schema/case/转义（使用 dialect 现有 normalize/escape 能力）。

验收：

- `nop-dao` 编译通过。
- 新增单测覆盖成功路径 + 回退路径。

### Phase 3 - Dialect 配置落地

1. 先为 `default/h2/mysql/postgresql/mssql/oracle` 补齐核心 exists 模板。
2. 其余 dialect 按继承链补充或显式留空（触发回退）。
3. 验证模板中的占位符与运行时渲染一致。

验收：

- 主流数据库模板可用。
- 不支持数据库走回退不抛异常。

### Phase 4 - 调用方迁移与回归

1. 将 `nop-db-migration` precondition checker 切换到新 `IJdbcTemplate` 方法。
2. 清理重复 INFORMATION_SCHEMA SQL。
3. 运行回归测试并补足缺失场景。

验收：

- `nop-db-migration` 相关测试全绿。
- 行为与既有语义保持一致（含 failOnError / precondition 逻辑）。

---

## 5. 测试计划

### 5.1 单元测试

- 模板渲染与参数边界（null schema、大小写、转义字符）。
- 新增 API 的 true/false 判定。
- 模板缺失时回退路径。

### 5.2 集成测试

- H2 本地快速回归。
- Testcontainers 抽样：MySQL、PostgreSQL（优先）；MSSQL、Oracle（条件允许）。

### 5.3 回归测试

- db-migration precondition：
  - tableExists
  - columnExists
  - indexExists
  - foreignKeyExists
- 历史行为兼容验证：`existsTable(querySpace, tableName)`。

---

## 6. 风险与应对

1. 接口扩展导致外部实现类编译失败。
- 应对：新增方法提供 default 实现，逐步迁移。

2. 各数据库系统视图权限/命名差异导致误判。
- 应对：优先模板，失败时回退 metadata；增加方言特例测试。

3. 生成链路顺序错误导致模型不同步。
- 应对：严格执行“先 `nop-xdefs`，后 `nop-dao`”安装顺序。

4. schemaName 为 null 时行为不一致。
- 应对：统一约定 null 语义（当前连接默认 schema/catalog），并在文档和测试中固定。

---

## 7. 里程碑与提交建议

### M1

- 完成 xdef 与模型生成改造（Phase 1）
- 提交建议：`feat(dialect): add existsSqls schema for object existence checks`

### M2

- 完成 IJdbcTemplate + JdbcTemplateImpl 新 API（Phase 2）
- 提交建议：`feat(dao): add dialect-driven object existence APIs in jdbc template`

### M3

- 完成主流 dialect 模板补齐（Phase 3）
- 提交建议：`feat(dialect): provide exists sql templates for major databases`

### M4

- 完成 db-migration 迁移与回归（Phase 4）
- 提交建议：`refactor(db-migration): use jdbc exists APIs backed by dialect templates`

---

## 8. 执行顺序（必须遵循）

1. 修改 `dialect.xdef`
2. `mvn install` in `nop-kernel/nop-xdefs`
3. `mvn install` in `nop-persistence/nop-dao`
4. 改 `IJdbcTemplate` / `JdbcTemplateImpl`
5. 改 `*.dialect.xml`
6. 迁移 `nop-db-migration` 调用点
7. 全量回归与提交

---

## 9. 完成定义（DoD）

- 新增 exists API 已在 `IJdbcTemplate` 可用并有测试。
- 至少 5 类对象存在性判断可通过 dialect 配置驱动。
- `nop-db-migration` 不再手写方言 INFORMATION_SCHEMA 逻辑。
- 主流数据库抽样测试通过。
- 文档与变更说明已补齐。

---

## 10. 执行进展（2026-03-31）

### 10.1 当前状态总览

- [x] Phase 1 完成：`dialect.xdef` 已扩展 exists 模板字段并完成生成链路安装。
- [x] Phase 2 完成：`IJdbcTemplate` 与 `JdbcTemplateImpl` 新增 exists API 及统一模板/回退实现。
- [x] Phase 3 基本完成：`default/h2/postgresql/mssql/oracle` 已补充核心 exists 模板；`mysql` 通过继承 `default` 获取通用模板。
- [x] Phase 4 完成：`nop-db-migration` precondition checker 已迁移到新 API，主流程不再手写 INFORMATION_SCHEMA exists SQL。

### 10.2 已执行验证

1. 触发并修复生成链路依赖顺序问题
  - 首次编译报错：`DialectSqls` 缺少 `getTableExists/getColumnExists/...`。
  - 处理方式：严格按顺序执行
    - `cd nop-kernel/nop-xdefs && ../../mvnw install`
    - `cd nop-persistence/nop-dao && ../../mvnw install`
  - 结果：`nop-dao` 可继续构建。

2. 模块级测试与回归
  - `./mvnw -pl nop-persistence/nop-dao,nop-persistence/nop-db-migration -DskipTests=false test`
  - `cd nop-persistence/nop-db-migration && ../../mvnw test`
  - 关键命令返回码：`0`。

3. 迁移结果核对
  - `nop-db-migration/src/main/java` 中不再包含 `INFORMATION_SCHEMA` 字符串。
  - precondition 检查器已统一调用：`existsTable/existsColumn/existsIndex/existsForeignKey`。

### 10.3 与 DoD 对照

- DoD-1（API 可用 + 测试）：已满足。
- DoD-2（>=5 类对象支持）：已满足（table/column/index/foreignKey/sequence/view）。
- DoD-3（db-migration 不再手写 exists SQL）：已满足（main 代码路径）。
- DoD-4（主流数据库抽样）：当前已完成 H2/模块回归；Testcontainers（MySQL/PostgreSQL/MSSQL/Oracle）待补。
- DoD-5（文档与变更说明）：本计划文档已更新执行进展，可配合 CHANGELOG 追加发布说明。

### 10.4 剩余建议动作

1. 补充跨数据库抽样测试（优先 MySQL、PostgreSQL）。
2. 按里程碑拆分提交（M1~M4），降低 review 与回滚成本。
3. 在 `CHANGELOG.md` 增加兼容性说明：旧 `existsTable(querySpace, tableName)` 保持可用并委托新重载。
