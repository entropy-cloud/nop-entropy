# 2321 nop-orm 分片执行引擎（fan-out 与归并）实施

> Plan Status: active（2026-10-06 首轮对抗审查 REJECT——1 Blocker（selectAllShards default 语义不可实现/空全集静默）+ 4 Minor 已修订；审查确认 13 条 baseline 行号零误差、缓存安全与 orderBy 映射可行性成立，判定修复后无需整轮复审）
> Last Reviewed: 2026-10-06
> Source: `ai-dev/design/nop-orm-sharding-execution-engine-design.md`（active，本 plan 唯一语义规格——§3.1 编译期结构信息、§3.2 执行模型与事务、§3.3 连接与事务边界、§3.4 归并分级、§3.5 跨分片 JOIN 边界、§3.8 使用契约与错误码、每分片强制 selector 机制）；roadmap `ai-dev/backlog/nop-persistence-middleware-roadmap.md` WI8（执行引擎实施）
> Related: `ai-dev/plans/2320-orm-sharding-strategy.md`（completed，前置契约：策略注册表/ConfigShardSelector/MissingShardPolicy/绑定校验/GenSqlTransformer 路由/DDL N 表展开——本 plan 建在其上）；`ai-dev/design/nop-orm-sharding-strategy-design.md`（§3.4/§3.6 由本 plan 同步修订）；`ai-dev/plans/2318-orm-enc-assisted-query-columns.md`（同 roadmap 前序 plan，Phase 结构与 Exit Criteria 风格参照）

> Draft Review Evidence（2026-10-06）：独立 fresh session 审查——13 条 Baseline 行号级零误差、缓存安全（QueryPlanCacheKey 为编译输入确定函数）与 orderBy→投影序数映射（getProjection identity 匹配先例）两大可行性问题均有现成机制支撑；REJECT 1 Blocker（selectAllShards default"按 pattern 推导"不可实现 + 空全集静默）+ 4 Minor，已全部修订（B1 裁定=default 返回空列表语义 + 空全集显式失败 + ConfigShardSelector 同源断言），设计文档 §3.8 已同步；审查判定修复后无需整轮复审。

## Purpose

落地 WI7 设计的执行形态：`missing-shard-policy: fanout` 的 useShard 实体在缺分片值时，SELECT 查询经执行引擎按分片全集串行执行 N 份物理 SQL，并按归并分级（无序拼接 / ORDER BY 内存排序 / 裸 count 求和 / 浅分页）产出唯一结果集。全程无静默错误：不可归并构造与资格越界一律显式失败，归并行数受 `nop.orm.shard.fanout-max-rows` 逐分片增量兜底，全局开关关闭时运行期退化为 fail。

## Protected Area 声明

- 本计划不触碰 ORM 模型结构（`model/*.orm.xml`）、`nop-core`/`nop-xlang`/`nop-xdef`、`nop-api-core`、`nop-*-api`。
- `ICompiledSql` 为 nop-orm-eql 模块内接口（非跨模块公共 API 模块），新增只读结构信息为模块内增量（2320 对 `IShardSelector` 的 default 方法扩展同构先例）。
- `GenSqlTransformer` 的唯一增量是 FANOUT 兜底显式失败分支（设计 §3.2 纵深防御），路由链零改动。
- 若实施证明必须改 nop-core（如 `SyntaxMarker`），按 AGENTS.md Protected Area 先回本 plan 补裁定与回归测试证据，不得顺手下改。

## Current Baseline

以下事实均经 2026-10-05 live code 逐条核实：

1. **ICompiledSql 无任何结构信息（归并信息面缺口）**：`ICompiledSql`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ICompiledSql.java:21-63`）只暴露 name/useTenantModel/querySpace/statementKind/dataSetMeta/readEntityNames/writeEntityName/columnBinders/fieldMetas/sql/buildParams——没有 GROUP BY/DISTINCT/HAVING/聚合投影/FOR UPDATE/UNION 的结构标志，也没有 ORDER BY 信息。执行层今天无法可靠判定"不可归并构造"，也无法为归并定位排序键。具体实现 `compile/CompiledSql.java`（:22-33）为纯字段袋，新增只读结构信息是加字段 + getter 的最小增量。
2. **编译期填充点现成**：`EqlCompiler.compile`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:101`）构造 `CompiledSql`，SELECT 分支（:111-147）已遍历投影构建 fieldMetas——填充是纯静态投影。AST 侧结构完整可得：`_SqlQuerySelect`（`ast/_gen/_SqlQuerySelect.java:19-33`）携带 distinct(:19)/forUpdate(:21)/groupBy(:25)/having(:27)/limit(:29)/orderBy(:33)；集合操作有独立节点 `SqlUnionSelect`（extends SqlSelect，编译入口 `stm` 可直接 instanceof 判定）；聚合投影为 `SqlAggregateFunction` 节点。CompiledSql 经 QueryPlanCache（`SessionFactoryImpl.java:74`，key=编译输入五元组（QueryPlanCacheKey：name+sqlText+disableLogicalDelete+allowUnderscoreName+enableFilter））跨执行共享——结构标志是编译产物静态属性，随文本缓存，缓存安全模型不变。
3. **transform 缺分片值分支现状（FANOUT 骑点）**：`GenSqlTransformer.collectShardSelections`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlTransformer.java:112-167`）：`policy==FAIL` 抛 `ERR_ORM_SHARD_CONDITION_MISSING`（:140-144）；`DEFAULT_SHARD` 分支（:145-152）经 `selectShardWhenMissing` → addShardSelection + setQuerySpace + return——**这是每分片强制 selector 要骑的既有分支**；`NONE` 与无分支命中静默 fall-through 基表执行（:159/:162）。**`FANOUT` 取值今天不存在、也不命中任何分支**：若声明后引擎不接管而泄漏进 transform，将与 NONE 同款静默 fall-through 基表执行（plan 2320 消灭的陷阱借新策略值复活）——因此 `policy==FANOUT` 的兜底显式失败分支是 transform 的唯一增量。`transform(SQL, List<Object>)`（:74）是唯一入口，不存在外部注入 ShardSelection 的通道；dialect 在尾部按 querySpace 解析（:166）。
4. **执行漏斗与只读意图现状**：`JdbcQueryExecutor`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java`）三入口 `executeUpdateSql`(:63-69)/`executeQuerySql`(:82-99)/`executeStatementSql`(:112-122) 都以 `transformEQL`（:153-158，每次执行新建 `GenSqlTransformer`，:155）为第一步；`executeQuerySql` 在 transform **之后**对单条 SELECT SQL 设置 readOnly(true)（:91-93，plan 2319 读写分离只读意图）——引擎产出的 N 份物理 SQL 必须逐份显式重复该规则，不存在"标记自然传播到多份 SQL"的机制。装配锚点：`OrmSessionFactoryBean.java:124` `setDefaultQueryExecutor(new JdbcQueryExecutor(impl))`；消费链 `OrmSessionImpl.java:1421/1428/1436` → `SessionFactoryImpl.getQueryExecutor`（:377-383，per-querySpace map + default 回落）——设计裁定 `IQueryExecutor` 契约不变。
5. **MissingShardPolicy 三值现状**：`nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/MissingShardPolicy.java:25` 枚举 `NONE/FAIL/DEFAULT_SHARD`；parse（:27-39）仅接受 fail/default-shard（空值回落 FAIL），未知取值抛 IllegalArgumentException，文案 "expected: fail | default-shard"——第四取值落地时须同步。`ShardStrategy.parseStrategies`（`shard/ShardStrategy.java:167-181`）消费 parse，且 DEFAULT_SHARD 必须配 default-shard 值（:180-181）。`DaoConfigs.CFG_ORM_SHARD`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoConfigs.java:83`）描述文本枚举 "missing-shard-policy(fail|default-shard,缺省fail)"——第四取值落地时须同步。
6. **SPI 现状**：`IShardSelector`（`shard/IShardSelector.java:32-87`）现有 selectShard/hasStrategyConfig/isEntityBound/getMissingShardPolicy/selectShardWhenMissing/getShardNames(:77，仅分片名列表，DDL N 表展开消费)/getShardTableName；**无 selectAllShards**——fanout 执行需要每分片完整落点（`ShardSelection` = querySpace + shardName 二元组，`shard/ShardSelection.java:22-29`），querySpace 由 query-space-pattern 推导且该知识在策略内部，调用方无法从分片名自行组装（设计 §3.8 m1）。`ConfigShardSelector`（`shard/ConfigShardSelector.java:51-191`）为配置轨原生实现（init 解析 `nop.orm.shard` JSON，未配置时与 EmptyShardSelector 同语义）。
7. **错误码现状**：`OrmErrors.ERR_ORM_SHARD_CONDITION_MISSING`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmErrors.java:329`）文案硬编码 "（missing-shard-policy=fail）" 且 args 仅 entityName/propName——设计 §3.8 注记（评审 m2）核定该文案用于"开关关闭退化"复用场景不贴切，实施须补 arg（policy 实际值/退化原因）避免误导排障；`:337` 为 `ERR_ORM_SHARD_STRATEGY_MISSING`。`DaoErrors.ERR_DAO_SHARD_CONFIG_INVALID`（`nop-dao/.../DaoErrors.java:83`，arg=reason）复用为 fanout 配置解析错误，无新增 DaoErrors 码。全仓无任何 FANOUT 错误码。
8. **nop-core 零改动约束可维持**：`SyntaxMarker`（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SyntaxMarker.java`）shard 字段为 final（:38-39），7 参构造器现成（:45-53）——2320 先例 `ShardMarkerInjector.replaceTableMarkers` 已证明"不改 final 字段、以 7 参构造器新建 marker 替换"路径可用。本计划结构信息载体为 CompiledSql 新字段（nop-orm-eql），不需要触碰 nop-core。
9. **归并取值与分页通道**：`JdbcQueryExecutor.transformRow`（:160-177）按 fieldMetas 逐列 `ISqlExprMeta.buildValue` 组装结果行——归并器排序键经"orderBy 项→fieldMetas 序数映射"从组装行取值走同一通道；分页 range 为 `LongRangeBean`（`jdbc().executeQuery` 第二参），浅分页的每分片 offset+limit 下推即对该参数按分片变换。
10. **测试基建现成**：nop-orm 测试 `io.nop.orm.shard` 包（`nop-persistence/nop-orm/src/test/java/io/nop/orm/shard/`：TestOrmShardRouting/TestShardBindingValidation/TestShardDdlExpansion/TestShardReadWriteCombined）+ nop-dao `io.nop.dao.shard.TestShardStrategy`；`AbstractOrmTestCase`（H2 内存库）；2320 先例覆盖分表形态端到端（单 H2 双物理表 `_0`/`_1` + RecordingSelector 接线断言 + 物理表行数断言）与分库形态 focused（query-space-pattern=ds_{shard}）。
11. **文档面现状**：owner doc `docs-for-ai/02-core-guides/orm-sharding.md` 现有六节（配置契约/路由语义/缺分片键策略/启动期绑定校验/DDL N 表展开/扩展点与边界），**无执行引擎章节**；策略设计 `ai-dev/design/nop-orm-sharding-strategy-design.md` §3.4（缺分片键策略，:91）与 §3.6（路由时机与查询计划缓存，:112）按执行引擎设计 §五须同步修订（missing-shard-policy 分支补 fanout 取值；分野表述扩展为"静态结构进编译期/值路由进执行期"）；执行引擎设计 §3.8 含两处 WI8 注记（m1 同源断言、m2 错误码 arg）与 fanout-max-rows 缺省值待定——须在本 plan 实施后消解为最终契约表述。
12. **roadmap 定位**：`ai-dev/backlog/nop-persistence-middleware-roadmap.md:58` WI8 "执行引擎实施 plan 起草 + 实施：承载 WI7 设计（Deliverable: plan + 代码 + 归并正确性测试矩阵 + E2E；deps: WI7；Item Type: Feature）"；WI7 设计已 active（:57）。
13. **gap**：全仓无任何 fan-out 执行/归并代码（`FANOUT`/`selectAllShards` 在 main 源码零命中）；`nop.orm.shard` 配置体系无 fanout-enabled/fanout-max-rows；MissingShardPolicy 无第四取值——现状下"按分片全集扫描"没有合法路径，缺分片键查询只能 fail-fast 或回落 default 分片。

## Goals

- `MissingShardPolicy` 第四取值 `FANOUT` + SPI `selectAllShards`（与 getShardNames 同源，断言固化）+ 全局配置 `nop.orm.shard.fanout-enabled`（缺省 false）/ `nop.orm.shard.fanout-max-rows`（缺省 10000，本 plan 裁定）。
- CompiledSql 编译期结构信息暴露：六个构造标志（hasGroupBy/hasHaving/hasDistinct/hasSetOp/hasForUpdate/isBareCount）+ orderBy→投影序数映射（排序键未投影无映射项）。
- 执行引擎：fanout 资格收口（仅 SELECT、单 fanout 实体、不与带分片值实体混用）→ 每分片强制 selector 骑既有 default-shard 分支产出 N 份物理 SQL → v1 串行执行 → 归并分级（设计 §3.4）→ 逐分片增量行数兜底。不可归并/越界形态一律 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT` 显式失败。
- 开关关闭运行期退化为 fail（`ERR_ORM_SHARD_CONDITION_MISSING` 补 policy/原因 arg）+ 启动期 WARN；未配置 fanout 的部署行为零变更。
- 端到端（H2 双分片）：无序/count/ORDER BY 真序/浅分页/超限中断/资格收口各形态显式失败（含自定义 selector 未覆盖 selectAllShards 的空全集形态）/开关关闭退化，全矩阵可断言。
- owner doc（orm-sharding.md 执行引擎章节）、策略设计 §3.4/§3.6 同步、执行引擎设计 WI8 注记消解。

## Non-Goals

- 跨分片写 fan-out：非 SELECT 命中 fanout 策略归 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`（v2 复用既有事务组，设计 §3.3）。
- 并行执行（ThreadLocal 事务域先决条件未解决，设计 §四已拒）与流式 K 路归并（v2 演进）。
- GROUP BY/HAVING/DISTINCT/非 count 聚合/FOR UPDATE/UNION 的归并支持（显式失败）；同策略多 fanout 实体"共置受限 JOIN"；广播表（维持拒绝，设计 §3.6）。
- sql-lib/原生 SQL 的解析与 fan-out（不解析，SQL 作者显式负责）；nop-batch 搬迁/回填路径（不经执行引擎）。
- 事务上下文跨线程传播、独立组提交/补偿机制（设计 §3.3 裁定复用既有 best-effort 事务组语义，不新建）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql`：CompiledSql 结构信息（ICompiledSql 只读增量 + EqlCompiler 编译期填充）。
- `nop-persistence/nop-dao`：MissingShardPolicy.FANOUT、IShardSelector.selectAllShards、DaoConfigs 两个全局配置、ShardStrategy/ConfigShardSelector 适配（错误码无 DaoErrors 新增，复用 config-invalid）。
- `nop-persistence/nop-orm`：OrmErrors 新增 2 个错误码 + 既有码 arg 补充、GenSqlTransformer FANOUT 兜底分支（唯一 transform 增量）、执行引擎与归并器、执行漏斗接线。
- 测试：nop-orm-eql/nop-dao/nop-orm focused tests + H2 双分片端到端矩阵。
- 文档：`docs-for-ai/02-core-guides/orm-sharding.md`、两份 `ai-dev/design/` 设计文档同步。

### Out Of Scope

- xdef / ORM 模型结构（`model/*.orm.xml`）变更；nop-core 变更（约束见 Baseline #8 与 Protected Area 声明）；nop-api-core 变更。
- nop-graphql / nop-biz 层的 fanout 语义透传（执行引擎在 ORM 执行漏斗内收口，上层自然继承）。
- per-request hint 类独立开关（设计 §四被拒）。

## Execution Plan

### Phase 1 - CompiledSql 编译期结构信息暴露

Status: completed
Targets: `nop-persistence/nop-orm-eql`（`ICompiledSql`、`compile/CompiledSql.java`、`compile/EqlCompiler.java`）

- Item Types: `Feature`

> **实施期裁定记录（2026-10-06）**：六标志之外补充第七只读标志 `hasAggregate`——实施证明无 GROUP BY 的 SUM/AVG 等聚合投影在六标志下与普通投影不可区分（`ExprTypeResolver.resolveAggFuncType` 不要求分组），而 Phase 3 资格收口的"非 count 聚合显式失败"必需该判定面；属同性质 nop-orm-eql 模块内只读增量（非跨模块 API），检测不含子查询内部聚合。已同步执行引擎设计 §3.1 构造标志表与本 plan 本节文字。

- [x] 构造标志暴露：SELECT 编译产物携带 hasGroupBy/hasHaving/hasDistinct/hasSetOp/hasForUpdate/isBareCount 六个只读标志（来源为 `_SqlQuerySelect` 字段与投影节点形态，编译期填充；UNION 经 `SqlUnionSelect` instanceof 判定；非 SELECT 语句标志为确定缺省值且不填充映射）+ 实施期补充 hasAggregate（见上方裁定记录）
- [x] orderBy→投影序数映射暴露：每个顶层排序项 → fieldMetas 序数 + 排序方向；**排序键未投影的查询无映射项**（设计 §3.1 边界：映射缺失即"排序键未投影"，消费方据此显式失败，arg=排序键）——实现形态：每排序项一个 `SqlOrderByOrdinal` 条目，未投影项序数=哨兵值 -1（避免"无 orderBy"与"orderBy 全未投影"在消费方不可区分的静默歧义）
- [x] 填充挂在 EqlCompiler SELECT 分支（Baseline #2 填充点），与 fieldMetas 构建同处；缓存安全：标志/映射为编译产物静态属性，随 SQL 文本进 QueryPlanCache（设计 §3.1 理由 b）
- [x] focused 测试：每标志正/反断言（group by/having/distinct/union/for update/裸 count 各构造真实 EQL 编译断言）；orderBy 映射（排序键已投影→序数与方向正确；未投影→无映射项；多排序项；排序键别名引用形态（映射成功断言）与裸表达式重复形态（允许无映射项→显式失败断言，两态都测））；非 SELECT 语句缺省值（`TestCompiledSqlStructFlags` 16 例）
- [x] 缓存交互测试：同一 EQL 文本两次编译（第二次命中 QueryPlanCache）后标志与映射保持正确（对齐 2318 缓存交互测试先例）（nop-orm `TestCompiledSqlStructInfoCache` 2 例，assertSame 证明缓存命中）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述 focused 测试全部通过（Minimum Rules #25：六标志 + 映射 + 缓存交互各有命名用例）
- [x] 既有行为零变更：nop-orm-eql 全量测试回归通过（填充为纯静态投影，不改变任何既有编译产物语义与 SQL 文本）（789 例绿）
- [x] **接线验证**（Minimum Rules #23）：本 Phase 产出为编译产物数据面，运行时消费方（引擎资格判定/归并器）在 Phase 3 接线——本 Phase 以 focused 测试断言编译产物字段值（repo-observable），Phase 3 Exit Criteria 承接运行时连通验证（结构信息被引擎真实消费）
- [x] 无静默跳过：本 Phase 无新增行为分支（No new failure branch；结构判定的显式失败集归 Phase 3 资格判定），新增访问器对非 SELECT/无 orderBy 语句有确定缺省语义（无 null 歧义）
- [x] No owner-doc update required（编译产物模块内数据面，无用户可见契约变化；fanout 用户契约归 Phase 4 orm-sharding.md 章节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - FANOUT 策略取值、SPI 扩展、配置与 transform 兜底失败

Status: completed
Targets: `nop-persistence/nop-dao`（`MissingShardPolicy`、`IShardSelector`、`ShardStrategy`、`ConfigShardSelector`、`DaoConfigs`）、`nop-persistence/nop-orm`（`OrmErrors`、`GenSqlTransformer`）

- Item Types: `Feature` + `Decision`

- [x] `MissingShardPolicy.FANOUT` 第四取值：parse 接受 "fanout"，未知取值错误文案同步更新；`ShardStrategy.parseStrategies` 适配（FANOUT 不要求 default-shard 配置）；`DaoConfigs.CFG_ORM_SHARD` 描述文本补 fanout 取值（Baseline #5）
- [x] `IShardSelector.selectAllShards(entityName)` SPI 新增：**接口 default 返回空列表，语义 =「该选择器不提供分片全集」**（接口 default 无法接触 query-space-pattern——那是 ConfigShardSelector/ShardStrategy 内部知识）；`ConfigShardSelector` 覆盖实现真实全集（与 getShardNames 同源，focused 断言同源）；同源断言落点为 ConfigShardSelector；**自定义轨 selector 不覆盖 selectAllShards 则 fanout 不可用（显式失败）**
- [x] 全局配置两项登记（DaoConfigs，对齐 CFG_ORM_SHARD 声明先例）：`nop.orm.shard.fanout-enabled`（缺省 false）/ `nop.orm.shard.fanout-max-rows`（缺省 **10000**——**Decision**：WI8 承载的缺省值裁定，采纳设计建议值；非正数配置启动即抛 `ERR_DAO_SHARD_CONFIG_INVALID`，对齐 2320 配置错误启动即抛先例）
- [x] `ERR_ORM_SHARD_CONDITION_MISSING` 文案/arg 补充（设计 §3.8 m2）：新增携带实际 policy 值（或退化原因）的 arg，消除硬编码 "（missing-shard-policy=fail）" 文案对开关关闭退化场景（Phase 3 消费）的误导；既有 FAIL 抛点的 entityName/propName args 与文案语义保持
- [x] **transform 兜底显式失败分支（唯一 transform 增量，设计 §3.2 纵深防御）**：`collectShardSelections` 为 `policy==FANOUT` 新增分支抛 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`（arg=实体名），语义为"fanout 策略命中但引擎未接管"；NONE/FAIL/DEFAULT_SHARD 三既有分支行为零变更
- [x] 启动期 WARN：检测到 fanout 策略绑定实体而全局开关关闭时记 WARN（保留"一键关闭"应急刹车属性且不产生配置矛盾，设计 §3.8）
- [x] 新增错误码 2 个（OrmErrors）：`ERR_ORM_SHARD_FANOUT_NOT_SUPPORT` / `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED`（语义与 arg 按设计 §3.8 错误码表；文案风格与既有分片错误条目一致）
- [x] 单测：parse("fanout") 命中 FANOUT / 未知取值仍抛 / default-shard 语义不变；selectAllShards 同源断言 + 未绑定实体返回空列表；fanout-max-rows 非正数显式失败；兜底分支（FANOUT policy 的 selector 直驱 transform、不经引擎接管）断言错误码 + arg=实体名（nop-dao `TestShardFanoutPolicy` 9 例 + nop-orm `TestShardFanoutTransformFallback` 1 例）
- [x] 无任何分片配置时既有行为零变更回归（ConfigShardSelector 未配置路径全方法与 EmptyShardSelector 同语义，含 selectAllShards 缺省空列表）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述测试全部通过（Minimum Rules #25：第四取值/SPI/配置/兜底分支/错误码各有命名用例）
- [x] **接线验证**（Minimum Rules #23）：FANOUT 策略经 `ConfigShardSelector.getMissingShardPolicy` 真实流入 `GenSqlTransformer` 兜底分支并响亮失败（selector→transform 运行时调用链断言，而非仅枚举值存在）（`TestShardFanoutTransformFallback`：RecordingFanoutSelector 委托真实 ConfigShardSelector + policyCalls 计数断言）
- [x] 无静默跳过（Minimum Rules #24）：FANOUT 泄漏进 transform 不再可能静默 fall-through（Baseline #3 陷阱被封死，测试钉住）
- [x] 既有 FAIL/DEFAULT_SHARD/NONE 三分支与未配置部署回归零变更（2320 既有 TestShardStrategy/TestOrmShardRouting 等全绿）
- [x] owner-doc 裁定：fanout 配置契约、开关语义与错误码清单归 Phase 4 orm-sharding.md 执行引擎章节统一核对（本 Phase 无独立文档交付）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 执行引擎：资格收口、串行 fan-out 与归并器

Status: completed
Targets: `nop-persistence/nop-orm`（执行引擎、归并器、`JdbcQueryExecutor` 三入口接管、`OrmErrors` 消费）

- Item Types: `Feature`

**执行约束（设计裁定，实现前必读）**：
1. 接管点在执行层且必须在 transform 之前（设计 §3.2）：三入口（executeUpdateSql/executeQuerySql/executeStatementSql）对 transform+fetch+merge 整体接管，判定发生在 transform 之前；`IQueryExecutor` 契约不变（装配形态——原地改 JdbcQueryExecutor 或 default-executor 装饰器——实现时裁定，锚点见 Baseline #4）；非 fanout 查询路径行为零变更。
2. v1 串行：调用线程逐分片执行，不引入并行（设计 §3.2 三个依据；不预埋并行化）。
3. 每分片物理 SQL 逐份显式 `readOnly(true)`（statementKind==SELECT，与 executeQuerySql:91-93 同一规则）；连接经既有事务组/读写分离路径获取，引擎不碰连接池（设计 §3.3/§3.7）。
4. 归并行数上限为逐分片增量计数：每分片拉回即累计，达限立即中断（不再向剩余分片发起物理 SQL）、已拉回数据不返回（无部分结果）、异常向调用方传播（设计 §3.2 评审 M4；全分片拉回后才校验的形态被拒）。
5. 归并分级严格按设计 §3.4：无序拼接 / ORDER BY 内存排序（排序键经 Phase 1 映射从组装行取值，未投影显式失败）/ 裸 count 求和（isBareCount 判定）/ 浅分页（每分片下推 offset+limit，归并截断 `[offset, offset+limit)`，总数=各分片 count 求和）；GROUP BY/HAVING/DISTINCT/非 count 聚合/FOR UPDATE/UNION 显式失败。

- [x] fanout 资格判定（接管点；判定依据=Phase 1 构造标志 + TABLE marker 实体名 + 绑定策略裁决）：仅 SELECT；fanout 绑定实体数 ≤1 且不与带分片值的 useShard 实体混用；越界一律 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`（arg 按设计 §3.8：构造名/排序键/实体名与原因）；绑定表（同策略同分片值）单分片 JOIN 保持原生，引擎零参与（设计 §3.5）（`ShardFanoutEngine.planQuery`）
- [x] 开关门控：`fanout-enabled=false` 时 FANOUT 策略运行期退化为 fail——抛 `ERR_ORM_SHARD_CONDITION_MISSING`（携带 Phase 2 新增 policy/原因 arg），不静默降级单分片（设计 §3.8）
- [x] 每分片强制 selector 装饰：getMissingShardPolicy→DEFAULT_SHARD、selectShardWhenMissing→该分片强制落点（骑 Baseline #3 的 :145-152 既有分支）；分片全集来自 selectAllShards；transform 路由链零改动（设计 §3.2 评审 M5）（`ForcedShardSelector`）
- [x] 串行执行 N 份物理 SQL + 逐份 readOnly + 归并器四语义（约束 3/4/5）+ 可观测性：`nop.orm.shard.fanout` 汇总日志（逻辑 SQL 名、分片数、归并行数、归并耗时、是否达限中断；设计 §3.8）（`ShardFanoutEngine.executeQuery` + `FanoutRowMerger`）
- [x] focused 测试（Minimum Rules #25）（nop-orm `TestShardFanoutEngine` 17 例）：
  - 资格收口逐形态显式失败：非 SELECT（UPDATE/DELETE 命中 fanout 策略）/ fanout 实体数>1 / fanout 与带值实体混用 / GROUP BY / HAVING / DISTINCT / 非 count 聚合 / FOR UPDATE / UNION / 排序键未投影——每形态断言错误码与 arg
  - 归并四语义：无序拼接全量完整；ORDER BY 全量真序（asc/desc，数据跨分片交错构造）；裸 count 求和；浅分页（offset/limit 截断正确 + 总数求和）
  - 行数上限：跨分片累计超限→中断（剩余分片物理 SQL 未发起，计数断言）+ 无部分结果 + `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED`（arg=实际行数与上限）
  - 逐份 readOnly 断言（N 份物理 SQL 各带只读标记）；强制 selector 断言（N 份物理 SQL 表名后缀与 querySpace 逐一正确）
  - 开关关闭退化 fail（含 Phase 2 policy arg 断言）；事务内 fanout 读经既有事务组自动入组（缺省部署，行为可观测断言，设计 §3.3 m3）
- [x] 最小端到端（H2 双分片，分表形态）：两分片各预置数据（既有写路由）→ 无分片键 EQL 查询 → 归并结果返回（含 ORDER BY 真序至少一条）（`testFanoutUnorderedConcat`/`testFanoutOrderByTrueOrder` 等）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述 focused 测试与最小端到端全部通过（Minimum Rules #25：资格收口 10 形态 + 归并 4 语义 + 上限中断 + readOnly/selector 断言各有命名用例）
- [x] **端到端验证**（Minimum Rules #22）：从 session 查询入口（OrmSessionImpl→IQueryExecutor）到归并结果输出的完整路径在 H2 双分片上跑通（完整 e2e 矩阵归 Phase 4）
- [x] **接线验证**（Minimum Rules #23）：引擎接管在执行漏斗真实生效——fanout 查询经引擎产出 N 份物理 SQL（表名后缀可观察）而非单条基表 SQL；非 fanout 查询走既有路径零变更（对照断言）；Phase 1 结构信息被资格判定与归并器真实消费（与 Phase 1 接线验证承诺闭环）
- [x] **无静默跳过**（Minimum Rules #24）：资格不满足/排序键未投影/超限/开关关闭全部显式失败；中断不返回截断集
- [x] owner-doc 裁定：执行引擎行为契约归 Phase 4 orm-sharding.md 执行引擎章节统一核对（本 Phase 无独立文档交付）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端矩阵、owner docs 与设计文档同步收口

Status: completed
Targets: 测试（nop-orm/nop-dao/nop-orm-eql）、`docs-for-ai/02-core-guides/orm-sharding.md`、`ai-dev/design/nop-orm-sharding-strategy-design.md`、`ai-dev/design/nop-orm-sharding-execution-engine-design.md`

- Item Types: `Proof`

- [x] 端到端矩阵（H2 双分片）：无序拼接 / 裸 count 求和 / ORDER BY 真序 / 浅分页（offset+limit）/ 超限中断（无部分结果）/ 资格收口各形态显式失败（含自定义 selector 未覆盖 selectAllShards 的空全集形态） / 开关关闭退化 fail / 开关开启前后对照——全部断言（`TestShardFanoutEngine` 17 例 + `TestShardFanoutTransformFallback`/`TestShardFanoutPolicy`）
- [x] 既有分片行为全量回归：2320 全部 shard 测试（TestShardStrategy/TestOrmShardRouting/TestShardBindingValidation/TestShardDdlExpansion/TestShardReadWriteCombined）+ 未配置 fanout 部署零变更
- [x] owner doc：`orm-sharding.md` 新增执行引擎章节（策略级 fanout 声明、全局开关与行数上限配置、资格边界与显式失败清单、归并语义分级、"跨分片读无统一快照"与"多主组部署分片入组契约"声明（设计 §3.3）、ORM 一级缓存分布式 ID 前置约束（设计 §3.7））；INDEX.md 路由核对
- [x] 设计文档同步：策略设计 §3.4（missing-shard-policy 分支补 fanout 取值）与 §3.6（分野表述扩展为"静态结构进编译期/值路由进执行期"）；执行引擎设计两处 WI8 注记（m1 同源断言、m2 错误码 arg）与 fanout-max-rows 缺省值消解为最终契约表述（Minimum Rules #14：终稿化，不写演进叙事）
- [x] 全量回归：`./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-orm-eql,nop-persistence/nop-orm -am`（BUILD SUCCESS）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：上列矩阵全绿，覆盖从策略配置→查询→归并→失败矩阵的完整用户路径
- [x] owner docs 更新完成且与 live 行为一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 设计文档三处同步完成（策略 §3.4/§3.6 + 执行引擎设计注记消解）
- [x] 全量回归通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭前逐条勾选；流程见 guide 的 When Closing The Plan 与 Closure Audit Rule。

- [x] fanout 端到端可用：`missing-shard-policy: fanout` + `fanout-enabled: true` → 缺分片值 SELECT 按分片全集归并（无序/count/ORDER BY 真序/浅分页四语义正确）
- [x] 归并正确性测试矩阵完成（设计 §3.4 分级逐行有测试）；资格收口各形态与超限/开关关闭全部显式失败，无静默错误结果、无静默跳过（含 transform 兜底分支）
- [x] 未配置 fanout / 非分片实体既有行为零变更（2320 全部既有 shard 测试回归绿）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）从查询入口到 N 份物理 SQL 到归并输出的运行时调用链连通（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现（运行时链路由 `TestShardFanoutEngine` 物理表名后缀/querySpace/readOnly/分片发起数断言覆盖；hollow 扫描三模块 high 0 发现）
- [x] GenSqlTransformer 路由链零改动（唯一增量=FANOUT 兜底分支）与 nop-core 零改动经提交 diff 核对
- [x] owner docs（orm-sharding.md、策略设计 §3.4/§3.6、执行引擎设计注记消解）与 live baseline 一致
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（hasAggregate 为实施期补充交付而非延期，见 Phase 1 裁定记录）
- [ ] 独立子 agent closure audit 已完成并记录证据（**留主会话执行**：实现完成后由独立 fresh session auditor 回看 live repo，不信任实现记录；证据写入下方 Closure 段）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2321-orm-sharding-execution-engine.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm --severity high` 退出码 0（另跑 nop-dao 与 nop-orm-eql，均 0 发现）
- [x] `./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-orm-eql,nop-persistence/nop-orm -am` 通过
- [x] 全仓编译门禁：`./mvnw clean install -DskipTests -T 1C` 通过（2320 先例：证明模块内接口增量无跨模块断编译）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 error；7 个既有 warnings 与本任务无关）

## Deferred But Adjudicated

（草稿期无；执行中如出现延期项按 guide 补 Classification 与 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- 并行 fan-out 与流式 K 路归并（v2 演进，前置=事务上下文跨线程传播；设计 §四已裁定 v1 不预埋）
- 同策略多 fanout 实体"共置受限 JOIN"（需编译期谓词覆盖性校验，评审 B2 裁定 v1 无业务拉动）

## Closure

Status Note: （关闭时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent: （独立 fresh session closure auditor，与实现会话无关；审计基线=live repo 回看，不信任实现记录）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live code path 或 test name；checklist 工具与 hollow scan 退出码；Anti-Hollow 链追踪；Deferred 分类检查）

Follow-up:

- （关闭时填写，或明确写 no remaining plan-owned work）
