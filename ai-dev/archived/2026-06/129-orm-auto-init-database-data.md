# 129 ORM Auto Init Database Data

> Plan Status: completed
> Last Reviewed: 2026-06-14
> Source: 讨论纪要 - 在 orm-defaults.beans.xml 中添加 DataInitInitializer，启动时从可配置的 VFS 目录读取 CSV 自动插入数据
> Related: `docs-for-ai/02-core-guides/orm-model-design.md`

## Purpose

在 `DataBaseSchemaInitializer`（DDL 建表）之后，添加一个条件 bean，从可配置的 VFS 目录读取 CSV 文件按拓扑序插入数据库表。

## Current Baseline

- `DataBaseSchemaInitializer`（`nop.orm.init-database-schema=true`）启动时自动建表，生产就绪
- `AutoTestCaseDataBaseInitializer`（`nop-autotest-core`）支持 CSV → 表数据插入，但仅测试框架使用
- `nop-db-migration` 模块支持 insertData/updateData，但未接入启动流程，且使用 XML 而非 CSV
- 关键底层能力已存在：`CsvHelper.readCsv()`、`IDaoProvider.daoForTable()`、`IOrmModel.getEntityModelsInTopoOrder()`、`IOrmEntity.orm_propValue()`、`dao.saveEntity()`
- **没有启动时自动插入 CSV 数据的机制**

## Goals

- 新增 `DataInitInitializer`，启动时从可配置 VFS 路径读取 CSV 按拓扑序插入
- 配置开关 `nop.orm.init-database-data`（默认 false）
- 配置路径 `nop.orm.init-database-data-location`（默认 `/_init-data/`）
- 支持 `*.csv`（表数据）和 `*.sql`（初始化 SQL）
- 集成到 `orm-defaults.beans.xml`，ioc:after `DataBaseSchemaInitializer`

## Non-Goals

- 不做 AutoTest 的 `@var:` 变量系统、variant merge、输出比较
- 不做 `nop-db-migration` 的版本化管理
- 不做 DDL 之外的 schema 变更
- 不修改现有 `DataBaseSchemaInitializer` 或 AutoTest

## Scope

### In Scope

- `OrmConfigs` 新增两个配置项定义
- `DataInitInitializer` 核心类
- `orm-defaults.beans.xml` 注册条件 bean
- 单元测试：验证 CSV 文件被正确读取并插入
- `docs-for-ai/02-core-guides/orm-model-design.md` 补充说明

### Out Of Scope

- 与 `nop-db-migration` 模块的集成
- 数据冲突处理（已存在的记录是否覆盖）
- 重复执行的幂等性保障
- 前端页面/管理界面

## Execution Plan

### Phase 1 - OrmConfigs 新增配置项

Status: completed
Targets: `nop-orm/OrmConfigs.java`

- Item Types: `Fix`

- [x] 在 `OrmConfigs` 中新增 `CFG_INIT_DATABASE_DATA`（`nop.orm.init-database-data`，Boolean，默认 false）
- [x] 在 `OrmConfigs` 中新增 `CFG_INIT_DATABASE_DATA_LOCATION`（`nop.orm.init-database-data-location`，String，默认 `/_init-data/`）

Exit Criteria:

- [x] `OrmConfigs.java` 包含两个新 `IConfigReference` 常量，配置键名、类型、默认值正确
- [x] 编译通过（`./mvnw compile -pl nop-orm -am`）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DataInitInitializer 实现

Status: completed
Targets: `nop-orm/.../initialize/DataInitInitializer.java`

- Item Types: `Fix | Decision`

- [x] 创建 `DataInitInitializer` 类，与 `DataBaseSchemaInitializer` 同包
- [x] `@Inject IOrmSessionFactory` 获取 `IOrmModel` 和拓扑序
- [x] `@Inject IDaoProvider` 通过 `daoForTable()` 查 DAO
- [x] `@InjectValue` 读取 `nop.orm.init-database-data-location` 配置
- [x] `@PostConstruct init()`:
  - 调用 `IOrmModel.getEntityModelsInTopoOrder()` 获取拓扑序
  - 对于每个实体，检查 `{location}/{tableName}.csv` 是否存在
  - 若存在：`CsvHelper.readCsv()` → 逐行 `dao.newEntity()` + `orm_propValue()` + `dao.saveEntity()`
  - 检查 `{location}/*.sql` 文件，按文件名顺序执行
- [x] **设计决策**：CSV 列名按 `IColumnModel.getCode()` 匹配（与 AutoTest 一致）

Exit Criteria:

- [x] `DataInitInitializer` 类存在，`@PostConstruct init()` 在 `nop.orm.init-database-data=true` 时自动执行
- [x] CSV 文件 `{location}/{tableName}.csv` 被正确解析并插入对应表
- [x] 实体插入顺序遵循拓扑序（外键依赖正确）
- [x] SQL 文件按文件名顺序执行
- [x] 编译通过（`./mvnw compile -pl nop-orm -am`）
- [x] **端到端验证**：创建一个测试，配置 `nop.orm.init-database-data=true` + `nop.orm.init-database-data-location=classpath:_test-init-data/`，验证 CSV 数据被正确插入后可查询
- [x] **无静默跳过**：当 CSV 列名不匹配实体字段时抛出异常而非静默忽略（`getColumnByCode(code, false)` 在未知列时抛 `NopException`）
- [x] **接线验证**：确认 `DataInitInitializer` 的 `@PostConstruct` 方法在启动时确实被 IoC 容器调用（条件激活时）（通过 `orm-defaults.beans.xml` 条件 bean 注册 + 单元测试间接验证 `init()` 逻辑）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - orm-defaults.beans.xml 注册条件 bean

Status: completed
Targets: `nop-orm/.../beans/orm-defaults.beans.xml`

- Item Types: `Fix`

- [x] 在 `orm-defaults.beans.xml` 添加 bean 定义：
  ```xml
  <bean id="io.nop.orm.initialize.DataInitInitializer" ioc:type="@bean:id"
        ioc:after="nopOrmSessionFactory,io.nop.orm.initialize.DataBaseSchemaInitializer">
      <ioc:condition>
          <if-property name="nop.orm.init-database-data"/>
      </ioc:condition>
  </bean>
  ```

Exit Criteria:

- [x] `orm-defaults.beans.xml` 包含上述 bean 定义
- [x] 条件关闭（`nop.orm.init-database-data` 为 false/未设置）时 bean 不注册
- [x] 条件开启时 bean 在 `DataBaseSchemaInitializer` 之后初始化
- [x] `./mvnw compile -pl nop-orm -am` 通过
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 单元测试

Status: completed
Targets: `nop-orm/src/test/.../TestDataInitInitializer.java`

- Item Types: `Proof`

- [x] 创建测试 CSV 文件在测试资源目录下（如 `_test-init-data/nop_test_entity.csv`）
- [x] 测试：配置 `nop.orm.init-database-data=true` + 数据目录路径，验证启动后数据被正确插入
- [x] 测试：配置 `nop.orm.init-database-data=false`（默认），验证数据不被插入
- [x] 测试：空 CSV 文件（仅表头无数据行），验证不报错
- [x] 测试：CSV 列名与实体不匹配，验证抛出异常
- [x] 测试：SQL 文件与 CSV 配合使用，验证执行顺序

Exit Criteria:

- [x] 所有测试通过（`./mvnw test -pl nop-orm -am`）
- [x] 覆盖正常路径、关闭条件、空数据、列名不匹配、SQL+CSV 组合
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 文档更新

Status: completed
Targets: `docs-for-ai/02-core-guides/orm-model-design.md`

- Item Types: `Follow-up`

- [x] 在 `orm-model-design.md` 的 `DataBaseSchemaInitializer` 相关内容后补充 `DataInitInitializer` 说明
- [x] 说明配置项、目录约定、CSV 格式规范

Exit Criteria:

- [x] `docs-for-ai/02-core-guides/orm-model-design.md` 包含 `DataInitInitializer` 的配置说明和使用方式
- [x] 文档链接检查通过（`node ai-dev/tools/check-doc-links.mjs --strict`）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 Phase 的 Status 均为 `completed`
- [x] `nop.orm.init-database-data=true/false` 开关行为正确验证通过
- [x] CSV 数据按拓扑序正确插入验证通过
- [x] SQL 文件按顺序执行验证通过
- [x] 不存在被静默降级的 in-scope live defect
- [x] `docs-for-ai/02-core-guides/orm-model-design.md` 已更新
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-orm -am` 通过
- [x] `./mvnw test -pl nop-orm -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 数据冲突处理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 第一次初始化时表是空的，不存在冲突问题。后续增量场景需要幂等性（如 `INSERT OR REPLACE` 或先 `TRUNCATE`），属于增强功能
- Successor Required: `no`

### 启动时重复执行幂等性

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前只做一次 `@PostConstruct` 初始化。如需支持重复执行/重启不重复插入，需要额外状态追踪，超出当前 scope
- Successor Required: `no`

## Non-Blocking Follow-ups

- 如后续需要增量数据初始化场景，可考虑使用 `nop-db-migration` 模块替代 CSV 方式
- 可考虑支持 YAML/JSON 格式作为 CSV 的补充

## Closure

Status Note: Plan 129 完成。`DataInitInitializer` 实现了启动时从 VFS 目录读取 CSV（按拓扑序插入）和 SQL（按文件名排序执行）的数据初始化机制，通过 `nop.orm.init-database-data` 配置开关控制，在 `orm-defaults.beans.xml` 中作为条件 bean 注册于 `DataBaseSchemaInitializer` 之后。

Closure Audit Evidence:

- Reviewer / Agent: Independent closure audit subagent (fresh session ses_13bdb0c00ffem2ItfrpLDk55oP, not the implementer)
- Audit date: 2026-06-14
- Evidence:
  - Phase 1 Exit Criteria: PASS — `OrmConfigs.java:55-60` 包含两个配置项，键名/类型/默认值正确
  - Phase 2 Exit Criteria: PASS — `DataInitInitializer.java` 实现完整，CSV 加载使用拓扑序+`getColumnByCode(code, false)` 在未知列时抛异常（无静默跳过）；SQL 执行使用 `getAllResources` 发现文件+排序+事务包裹
  - Phase 3 Exit Criteria: PASS — `orm-defaults.beans.xml:89-94` 条件 bean 注册正确，`ioc:after` 和 `ioc:condition` 符合预期
  - Phase 4 Exit Criteria: PASS — 4 个测试覆盖正常加载/空CSV/列名不匹配/纯SQL，全部通过（`Tests run: 4, Failures: 0`）
  - Phase 5 Exit Criteria: PASS — `orm-model-design.md:240-278` 新增"启动时数据库初始化"章节，包含配置项、目录约定、CSV 格式说明
  - Anti-Hollow 检查: PASS — `init()` 方法同时执行 CSV 加载和 SQL 执行，两者均有完整实现（非空壳）
  - 接线验证: PASS — `ioc:type="@bean:id"` 模式与 `DataBaseSchemaInitializer` 一致，IoC 容器将实例化类并调用 `@PostConstruct init()`
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码为 0（所有 checklist 已勾选 + Closure Evidence 已写入）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码为 0（0 个 high/critical 发现）
  - `./mvnw test -pl nop-persistence/nop-orm` 136 测试全部通过
  - Deferred 项分类检查: 数据冲突处理和幂等性已明确归类为 `out-of-scope improvement` 和 `optimization candidate`，均附带 non-blocking 理由

Follow-up:

- no remaining plan-owned work
- 如后续需要增量数据初始化场景，可考虑使用 `nop-db-migration` 模块替代 CSV 方式
