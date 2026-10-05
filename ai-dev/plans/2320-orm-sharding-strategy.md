# 2320 nop-orm 分片策略配置与单分片路由落地

> Plan Status: active（2026-10-05 两轮独立对抗审查：第 1 轮 REJECT——2 Blocker + 5 Major 修复；第 2 轮 fresh session 复审唯一残留 N1 已修，裁定修复后即可 active、无需再开整轮复审；证据见 Draft Review Evidence）
> Last Reviewed: 2026-10-05
> Source: `ai-dev/design/nop-orm-sharding-strategy-design.md`（目标设计，承载本 plan）；roadmap `ai-dev/backlog/nop-persistence-middleware-roadmap.md` WI2/WI6

> Draft Review Evidence（WI2，2026-10-05）：第 1 轮独立审查经 live code 逐点核实 baseline（准确度逐点确认），发现 2 Blocker（启动校验打爆既有 useShard 测试实体；MockShardSelector 删方法必断 nop-auth 编译且 closure 命令测不出）与 5 Major（SPI 缺值契约留白、启动校验放错模块、Phase 2 时序/缓存安全/坏取值语义未写明、模块归属失真、Protected Area 未声明），全部修订；第 2 轮 fresh session 复审确认 7 项 FIXED，唯一残留 N1（Phase 1 旧校验文本未同步删改）与 3 个小项（设计文档 §3.5 同步、行号、措辞）本轮已修正；复审明确「修复后无需再开一整轮独立复审即可转 active」。
> Related: `ai-dev/design/nop-orm-scope-and-non-goals.md` §3.5、`ai-dev/plans/2319-dao-readwrite-splitting.md`（组合 E2E 的对端）

## Purpose

把"SPI 骨架"落地为"配置即用的单分片路由"：内置 5 算法策略注册表、查询路径分片值写入（现状缺失）、缺分片键 fail-fast、DDL N 表展开、死面清理。端到端 = 配置策略后，useShard 实体按分片键自动路由物理表与数据源。

## Current Baseline

以下事实均经 2026-10-05 live code 核实：

1. **路由读侧存在、查询写侧缺失（关键）**：`GenSqlTransformer.collectShardSelections`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlTransformer.java:98-132`）依赖 `SyntaxMarker.hasShardParam()`，但 `shardParamIndex/shardValue` 是 **final 字段**（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SyntaxMarker.java:38-39`），全仓构造点只有 5 参版本（`SQL.java:879 markTable`（方法 864-882）、`:887 addFilterMarker`，shard 参数恒空）——**`hasShardParam()` 恒 false，查询恒走 fall-through（基表名执行）**。写路径不同：`EntityPersisterImpl.getShardSelection`（`persist/EntityPersisterImpl.java:264-281`）从实体属性直取分片值调 `selectShard`，是活的；`GenSqlHelper.transformShard`（`sql/GenSqlHelper.java:657-678`）是写路径既有表名改写器。
2. **Selector 装配链现成**：`orm-defaults.beans.xml:31-32` 缺省 `EmptyShardSelector.INSTANCE`；`SessionFactoryConfig.setShardSelector`（:213-218）→ `JdbcQueryExecutor:149` 构造 transformer。写路径消费点两处（EntityPersisterImpl :264-281、CollectionPersisterImpl :159-170）。
3. **模型侧**：`useShard`/`shardProp` 为 entity.xdef 一级属性（entity.xdef:49）；`OrmEntityModelInitializer:405-417`——useShard 未声明 shardProp 时自动补 `nopShard` VARCHAR(32) 列，声明了但列不存在抛 `ERR_ORM_UNKNOWN_COLUMN`。
4. **既有测试现状**：`TestOrmTemplate.testJoinWithValue`（:226-247）只断言列值回读，无任何物理表名/路由断言；测试跑在 `EmptyShardSelector` 上。唯一自定义实现是 nop-auth 测试的 `MockShardSelector`（按实体名查 Map，与分片值无关）。
5. **DDL 建表三调用点**：`DataBaseSchemaInitializer.init`（`initialize/DataBaseSchemaInitializer.java:56-72`，逐实体循环 `createTable`）、auto-upgrade `OrmDbDiffer.createEntityDiffDdls`（`nop-dbtool-core/.../diff/OrmDbDiffer.java:360-374`，add→createTable / 列 diff；注意 ddl.xlib 加列模板引用 `col.ownerEntityModel.tableName`——仅包装实体模型对加列路径无效，「显式表名入口」选项更干净）、autotest `AutoTestCaseDataBaseInitializer`（:111-121）。**无现成"表名替换"API**——createTable 表名取自 `entityModel.getTableName()`，N 表展开需实体模型包装/克隆或新入口。
6. **配置先例**：Map 型平面配置 `StringHelper.parseStringMap(text,'=',',')`（`DaoHelper.java:33-42`）；XScript 表达式配置先例 `DefaultDataAuthChecker.whenConfig`（编译配置字符串为谓词 + `setLocalValue` 绑定求值）；策略 bean 收集可对齐 `ioc:collect-beans name-prefix` 模式（同 nopDataSource_）。
7. **错误码**：`DaoErrors.java`/`OrmErrors.java` 接口静态字段模式（`ErrorCode.define`）。
8. **死面**：`IShardSelector.selectShards` main 零调用方（仅接口与两个实现自身）。
9. `ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL`（`GenSqlTransformer.java:205`）维持——一条 SQL 单 querySpace。

## Goals

- 内置算法 5 个（mod/hash-mod/range/interval/inline-XScript）+ 自定义 SPI（`IShardSelector` 覆盖注册表）双轨；策略经配置声明、绑定实体列表（显式列举，无通配符）。
- 查询路径打通：EQL/QueryBean 查询中 shardProp 等值条件 → 分片值进入 TABLE marker → 表名后缀 + querySpace 改写（与写路径同语义）。
- 缺分片键 fail-fast：`missing-shard-policy: fail`（缺省）抛 `ERR_ORM_SHARD_CONDITION_MISSING`；`default-shard` 显式回落。启动期校验：useShard 实体必须绑定策略（显式或 default-strategy），否则 fail-fast。
- DDL N 表展开覆盖三个建表调用点；auto-upgrade 对 N 表逐一 diff；`selectShards` 死面删除。
- 端到端：配置策略 → 保存（写路径路由）→ 按分片键查询（读路径路由命中同一物理表）→ 无分片键查询按策略失败/回落。

## Non-Goals

- 跨分片 fan-out 执行与结果归并（roadmap WI7/WI8 独立立项）；广播表（设计已拒）；Hint 路由（设计已拒）；通配符实体绑定（设计已拒）。
- 扩容数据搬迁（roadmap WI9）。
- 分库（querySpace 映射）v1 支持 `query-space-pattern`，但组合 E2E 以单库分表为主（分库形态有 focused 测试即可）。
- 不改 `ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL` 约束。

## Scope

### In Scope

- `nop-persistence/nop-dao`：策略 SPI/注册表、内置算法、`DaoErrors` 新错误码、`IShardSelector` 死面移除（selectShards + isSupportShard）、配置。
- `nop-persistence/nop-orm-eql`：查询侧分片值写入的编译链路改动（EqlCompiler/AstToSqlGenerator 所在模块）。
- `nop-persistence/nop-orm`：`GenSqlTransformer` fail-fast 与策略接入、启动期绑定校验、DDL 展开调用点、测试。
- `nop-persistence/nop-dbtool`：auto-upgrade 对 N 表的 diff 适配。
- `nop-autotest/nop-autotest-core`：第三建表调用点的 N 表展开适配。
- `nop-auth/nop-auth-service`（仅测试代码适配）：`MockShardSelector` 随接口死面移除的编译适配。
- 文档：设计文档转 active、owner docs。

### Out Of Scope

- xdef 变更（useShard/shardProp 已存在；策略配置不进模型——设计裁定）。
- nop-auth `MockShardSelector` 的路由**语义**验证迁移（其按实体名查 Map 的语义与策略注册表不冲突；但接口死面移除引发的**编译适配在本 plan scope 内**，见 In Scope）。

## Execution Plan

### Phase 1 - 策略模型、注册表与配置承载

Status: planned
Targets: `nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/`（扩展）、`DaoConfigs`、`DaoErrors`

- Item Types: `Feature` + `Decision`

- [ ] 策略接口与注册表：接口必须同时承载 (a) 分片值→`ShardSelection` 解析，(b) **缺分片值解析契约**（按 missing-shard-policy 输出 fail 或 default 分片——消费方为 nop-orm 的 `GenSqlTransformer`，若 Phase 1 无法定义完整契约则显式声明注册表旁路方法及其消费方）；该 SPI 属 nop-dao 模块内扩展点，不属 `nop-*-api` 公共 API；**Decision**：配置承载形态裁定——IoC collect-beans 策略 bean（对齐 `IOrmDaoListener` by-type 收集先例）vs JSON 配置串解析；裁定记录到设计文档（两者均符合「部署属性不进模型」裁定）
- [ ] 内置算法 5 个：mod / hash-mod（稳定 hash，跨方言一致）/ range（显式边界映射）/ interval（时间步长）/ inline（XScript 表达式，对齐 `DefaultDataAuthChecker` 求值先例）；每个算法独立单测（边界值、负数、非整数分片键、null 分片键→显式错误）
- [ ] 实体绑定配置模型（显式实体名列表 + 可选 default-strategy 字段）；启动期绑定校验**不属本 Phase**——已移入 Phase 2 在 nop-orm 层实施，校验错误码随 Phase 2 登记 `OrmErrors`
- [ ] 死面移除：`IShardSelector.selectShards` 与 `isSupportShard`（两者 main 均零调用方）从接口与 `EmptyShardSelector` 收缩；**同步删除 nop-auth-service 测试 `MockShardSelector` 的对应 @Override 实现（编译适配，In Scope）**；全仓编译回归证明无断裂
- [ ] 单测：注册表解析、5 算法、绑定校验、缺省 Empty 行为不变（未配置策略的既有用例全绿）

Exit Criteria:

- [ ] 上述测试全部通过（Minimum Rules #25）；**无任何分片配置时既有行为零变更**（回归，含 base.orm.xml 的 useShard 测试实体全绿）
- [ ] 无静默跳过：所有配置错误启动即抛（Minimum Rules #24）
- [ ] Decision 结论已回写设计文档
- [ ] owner-doc 更新延至 Phase 4 统一执行（本 Phase 无独立文档交付）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 查询侧分片值写入与缺分片键策略

Status: planned
Targets: `nop-persistence/nop-orm-eql`（编译链路）、`nop-persistence/nop-orm`（`GenSqlTransformer` 接线 + 启动校验）

- Item Types: `Feature`

**执行约束（审查核定，实现前必读）**：
1. **时序**：输出器按 select→from→where 单遍输出（`AstToEqlGenerator.printSelect`；`markTable` 调用在子类 `AstToSqlGenerator:757`），FROM 期执行时 WHERE 参数 marker 尚未产生——「构造期传入」不可行；注入点应为**生成完成后基于已解析 AST 定位分片等值条件，用现有 7 参构造器新建 `SyntaxMarker` 替换 builder marker 列表中的 TABLE marker**（final 字段不改），或 CompiledSql 侧信道（entityName→参数序号）——二选一，实现时裁定。
2. **缓存安全**：`CompiledSql` 经 QueryPlanCache 跨执行共享，marker 只能承载静态信息（参数序号/字面量）；执行期值从 `paramValues` 取——`collectShardSelections` 需接收 paramValues（签名变更），现有 `sql.getMarker(shardParamIndex)` 取 Marker 对象的取值语义是坏的，须重定义（列入改动面，非「不变」）。
3. **Protected Area**：优先完全不改 nop-core（`SyntaxMarker` 现有 7 参构造器已足够）；若实施证明必须改 nop-core，按 AGENTS.md Protected Area 先补本 plan 修订与回归测试证据，不得顺手下改。

- [ ] 查询分片值提取：useShard 实体的 EQL/QueryBean 查询中，shardProp 列的**等值条件**（参数或字面量）→ 按上述约束写入 TABLE marker
- [ ] `GenSqlTransformer`：分片值存在 → `selectShard` 路由（表名后缀 + querySpace，取值语义按约束 2 重定义）；缺失时按绑定策略的 `missing-shard-policy`——fail（缺省）抛 `ERR_ORM_SHARD_CONDITION_MISSING`（OrmErrors，携带实体名与分片键列名）/ default-shard 路由到指定分片
- [ ] **启动期绑定校验**（从 Phase 1 移入，校验点在 nop-orm 层——nop-dao 不依赖 nop-orm-model）：**全局策略配置存在时**，useShard 实体未绑定策略且无 default-strategy → 启动 fail-fast（`ERR_ORM_SHARD_STRATEGY_MISSING`）；**无任何分片配置时不校验**（显式裁定：配置驱动的严格性，未配置分片体系的部署维持既有 Empty 行为，保证既有 useShard 测试实体零变更）
- [ ] 写路径回归：`EntityPersisterImpl`/`CollectionPersisterImpl` 既有直取行为不变；`GenSqlHelper.transformShard` 不变
- [ ] 单测：等值条件（参数形态/字面量形态）路由正确；范围条件/无条件按策略失败或回落；join 中同策略两表同分片值落同 querySpace（绑定表语义）+ 跨 querySpace 仍报既有错误；绑定校验两态（有配置/无配置）

Exit Criteria:

- [ ] 读路由/写路由/绑定表/失败矩阵各有 focused 断言（Minimum Rules #25）
- [ ] **接线验证**（Minimum Rules #23）：测试断言分片值经 marker 真实进入 `collectShardSelections`（物理表名 `_N` 后缀可观察）
- [ ] 无静默跳过（Minimum Rules #24）
- [ ] owner-doc 更新延至 Phase 4 统一执行（本 Phase 无独立文档交付）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - DDL N 表展开

Status: planned
Targets: `nop-persistence/nop-orm/initialize/DataBaseSchemaInitializer.java`、`nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/diff/OrmDbDiffer.java`、`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/execute/AutoTestCaseDataBaseInitializer.java`

- Item Types: `Feature`

- [ ] N 表展开机制：绑定策略的 useShard 实体按分片名全集展开（表名后缀），覆盖三个建表/升级调用点（无既有表名替换 API，采用实体模型包装或显式表名入口——实现时裁定并保持 `DdlSqlCreator` 公共语义不变；入口见 `nop-persistence/nop-orm/initialize/DataBaseSchemaInitializer.java`）
- [ ] auto-upgrade：N 表逐一列 diff（`OrmDbDiffer` 适配），不加列到不存在的基表
- [ ] 单测：N 表建表 SQL 数量与表名断言；auto-upgrade 对已展开表加列；未绑策略实体建表行为不变

Exit Criteria:

- [ ] 三个调用点各有 focused 测试（Minimum Rules #25）
- [ ] 既有非分片建表/升级全量回归通过
- [ ] owner-doc 裁定：DDL N 表展开语义归 Phase 4 的分片 owner doc 章节统一核对（本 Phase 无独立文档交付）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端与文档收口

Status: planned
Targets: 测试、owner docs、设计文档状态

- Item Types: `Proof`

- [ ] 端到端（H2 + 策略配置）：保存（写路由到 `_N` 表）→ 按分片键 EQL 查询命中同表 → 无分片键查询按策略失败/default 回落 → querySpace 分片形态 focused 测试
- [ ] 组合验证：分库分表 × 读写分离叠加（querySpace 指向路由 DS）——**若执行到此时 plan 2319 尚未落地，本项移入 Deferred But Adjudicated**（classification: out-of-scope improvement；successor: 2319 落地后补组合 E2E），不得留空勾
- [ ] 设计文档转 active（含 Phase 1 Decision 结论，并**按 Phase 2 配置门控语义改写设计文档 §3.5 的无条件 fail-fast 表述**）；owner doc：`docs-for-ai/02-core-guides/` 分片章节（声明、配置、fail-fast 契约、与 fan-out 边界）+ `docs-for-ai/INDEX.md` 路由
- [ ] 全量回归：`./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dbtool/nop-dbtool-core,nop-autotest/nop-autotest-core -am`

Exit Criteria:

- [ ] **端到端验证**（Minimum Rules #22）：配置 → 写路由 → 读路由 → fail-fast 全链路断言
- [ ] owner docs 更新完成；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 配置即用端到端可用：策略声明 → 写路由 → 按分片键读路由 → 缺分片键 fail-fast/default 回落
- [ ] 查询侧分片值写入接线已验证（不只读侧改造，`hasShardParam` 不再恒 false）
- [ ] 5 内置算法 + 绑定校验 + N 表 DDL 各有 focused 测试；未配置时既有行为零变更
- [ ] `selectShards`/`isSupportShard` 死面已移除且全仓编译/测试无断裂（含 nop-auth MockShardSelector 适配）
- [ ] owner docs 与设计文档（转 active）一致
- [ ] 独立子 agent closure audit 已完成并记录证据（Anti-Hollow：路由在读写两条链路真实生效）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2320-orm-sharding-strategy.md --strict` 退出码 0
- [ ] `./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dbtool/nop-dbtool-core,nop-autotest/nop-autotest-core -am` 通过
- [ ] 全仓编译门禁：`./mvnw clean install -DskipTests -T 1C` 通过（覆盖 nop-auth-service 等 -pl 集合外的模块，证明死面移除无断编译）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（执行中如出现延期项按 guide 补 Classification 与 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- 分库形态（query-space-pattern）的完整 E2E 矩阵（v1 focused 测试即可）。
- interval 算法的时区语义细化（v1 按本地日期）。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
