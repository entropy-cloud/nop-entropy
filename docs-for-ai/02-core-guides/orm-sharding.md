# ORM 分片（单分片路由）

Nop 的分片是 **ORM 执行层的分片路由**：实体声明 `useShard` + `shardProp`（模型级事实），策略（算法/分片数/绑定实体/缺分片键策略）经 JSON 配置 `nop.orm.shard` 声明（部署级事实）。写路径与读路径共用同一 `IShardSelector` 扩展点，路由结果为 `ShardSelection(querySpace, shardName)` 二元组——表分片（表名 `_N` 后缀）、库分片（querySpace 切换）、库+表组合由该二元组统一表达。缺分片值查询缺省 fail-fast（或回落 default 分片）；声明 `missing-shard-policy: fanout` 且开启全局开关时按分片全集展开执行（见"执行引擎"章节）。


## 配置契约

配置键 `nop.orm.shard`（JSON 文本，与 `nop.dao.readwrite` 同为"字符串配置事后解析"形态）：

```json
{
  "strategies": {
    "order-mod": {
      "type": "hash-mod",
      "shard-count": 16,
      "table-pattern": "{table}_{shard}",
      "query-space-pattern": "ds_{shard}",
      "missing-shard-policy": "fail",
      "default-shard": "0",
      "entities": ["app.entity.NopOrder"]
    }
  },
  "default-strategy": "order-mod"
}
```

| 键 | 说明 |
|---|---|
| `type` | 内置算法：`mod` \| `hash-mod` \| `range` \| `interval` \| `inline` |
| `shard-count` | `mod`/`hash-mod` 必填，正整数 |
| `ranges` | `range` 必填：`{"1000000": "0", "2000000": "1"}`（上边界闭区间，超出全部边界显式报错） |
| `interval-step` / `datetime-lower` / `datetime-upper` / `format` | `interval` 参数（步长如 `1d`/`2M`/`1y`；分片名为区间起始日期按 `format` 格式化，缺省 `yyyyMMdd`） |
| `algorithm-expression` | `inline` 必填：XScript 表达式，上下文变量 `value`（分片值）、`shardProp`（分片键列名），返回分片名 |
| `shard-names` | 可选显式分片名全集；`inline` 不可静态枚举，DDL 展开前必须显式配置 |
| `table-pattern` | 物理表名规则，缺省 `{table}_{shard}` |
| `query-space-pattern` | 库分片：按分片名映射 querySpace（如 `ds_{shard}`）；缺省不切库 |
| `missing-shard-policy` | `fail`（缺省）\| `default-shard` \| `fanout`（按分片全集展开执行，见"执行引擎"章节；需 `nop.orm.shard.fanout-enabled=true`） |
| `default-shard` | `policy=default-shard` 时的回落分片名（缺省值配置 `default-shard` 隐含声明该策略） |
| `entities` | 本策略绑定的实体名**显式列表**（设计拒绝通配符：分片是高风险配置，隐式匹配属静默错误） |
| `default-strategy`（顶层） | 未显式绑定的 useShard 实体回落到该策略（显式全局回落，非隐式匹配） |

所有配置错误（JSON 非法、未知算法类型、重复绑定实体、unknown default-strategy）启动即抛 `ERR_DAO_SHARD_CONFIG_INVALID`，无静默跳过。

## 路由语义

**写路径**（既有）：`EntityPersisterImpl`/`CollectionPersisterImpl` 从实体属性直取分片值调 `selectShard`。

**读路径**（plan 2320）：EQL/QueryBean 编译时定位 shardProp **等值条件**（参数或字面量形态），将参数序号/字面量作为静态信息写入 TABLE marker；执行期 `GenSqlTransformer` 从当次 paramValues 取值路由，改写表名后缀并切换 querySpace。与查询计划缓存兼容：编译产物静态、路由动态（N 分片 → N 个 PreparedStatement 缓存条目）。

条件定位范围（v1 边界）：语句顶层 WHERE 与显式 JOIN 的 ON 条件中 **AND 连通**的等值条件；两分片列等值（`o.shard = s.shard`）触发同值传播（绑定表语义：同策略两实体落同一 querySpace + 同名后缀）。OR/NOT 分支与子查询内部不下探——无法推出语句级单分片值，交由 missing-shard-policy 裁决。一条 SQL 内所有 useShard 实体解析出的 querySpace 必须一致，否则 `ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL`（既有约束）。

## 缺分片键策略

| 场景 | 行为 |
|---|---|
| SQL 含分片键等值条件（参数/字面量），值非 null | `selectShard` 路由到目标分片 |
| 无分片键条件 / 条件值为 null，`missing-shard-policy=fail`（缺省） | 抛 `ERR_ORM_SHARD_CONDITION_MISSING`（携带实体名与分片键列名），**取代静默落基表执行的陷阱行为** |
| 无分片键条件，`missing-shard-policy=default-shard` | 路由到 `default-shard` 指定分片（显式回落） |
| 无分片键条件，`missing-shard-policy=fanout` | 交由执行引擎按分片全集展开执行并归并（见下节）；全局开关关闭时运行期退化为 fail |
| 实体未绑定策略（未配置分片体系） | 维持既有基表执行语义（零变更） |

## 启动期绑定校验（配置门控）

- **存在全局分片配置时**：useShard 实体未绑定策略（显式或 `default-strategy`）→ 模型加载期 fail-fast `ERR_ORM_SHARD_STRATEGY_MISSING`。
- **无任何分片配置时不校验**：未配置分片体系的部署维持既有 Empty 行为，既有 useShard 实体零变更。

## DDL N 表展开

绑定策略的 useShard 实体建表/升级时按分片名全集展开为 N 张物理表，覆盖三个调用点：启动建表（`DataBaseSchemaInitializer`）、auto-upgrade（`OrmDbDiffer`/`DataBaseUpgrader`，对 N 张物理表逐一列 diff，不加列到不存在的逻辑基表）、autotest 建表（`AutoTestCaseDataBaseInitializer`）。DDL 定向经 `DdlSqlCreator` 的显式物理表名重载（模板 `shardTableName ?? table.tableName` 回退，不传时输出与既有方法逐字节一致）。

## 执行引擎（fan-out 与归并，plan 2321）

`missing-shard-policy: fanout` 的 useShard 实体在缺分片值时，SELECT 查询由执行引擎按分片全集**串行**执行 N 份物理 SQL 并归并出唯一结果集（接管点在 `JdbcQueryExecutor` 查询入口、transform 之前；每分片经强制 selector 骑既有 default-shard 回落分支产出物理 SQL，`GenSqlTransformer` 路由链不变）。

### 全局配置

| 键 | 缺省 | 说明 |
|---|---|---|
| `nop.orm.shard.fanout-enabled` | `false` | 总开关。关闭时 fanout 策略**运行期退化为 fail**（`ERR_ORM_SHARD_CONDITION_MISSING`，携带 policy 与原因 arg），不静默降级单分片；启动期检测到 fanout 策略绑定实体而开关关闭记 WARN。保留"一键关闭"应急刹车属性 |
| `nop.orm.shard.fanout-max-rows` | `10000` | 归并行数上限（逐分片增量计数）：每拉回一个分片立即累计，超限立即中断后续分片、不返回部分结果，抛 `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED`。非正数启动即抛 `ERR_DAO_SHARD_CONFIG_INVALID` |

### 资格边界（越界一律 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`）

- **仅 SELECT**：非 SELECT（UPDATE/DELETE）命中 fanout 策略显式失败——跨分片非原子 DML 不进 OLTP 路径（全表修正走 nop-batch 逐分片作业）。
- **单条 SQL 至多一个 fanout 绑定实体**，且**不得与带分片值的 useShard 实体混用**（跨分片配对静默缺行风险；绑定表同分片值单分片 JOIN 保持原生，引擎零参与）。
- **不可归并构造显式失败**：GROUP BY / HAVING / DISTINCT / 非 count 聚合 / FOR UPDATE / UNION——由 EQL 编译期结构标志判定（`CompiledSql` 的 hasGroupBy/hasHaving/hasDistinct/hasSetOp/hasForUpdate/isBareCount/hasAggregate，非 SQL 文本正则）。
- **ORDER BY 排序键必须已投影**：归并排序从组装行按 orderBy→投影序数映射取值，排序键未投影无映射项即失败（出路：把排序键加入投影）。
- **分片全集非空**：`IShardSelector.selectAllShards(entityName)` 返回空列表（接口 default 语义=「该选择器不提供分片全集」）时显式失败，禁止静默空结果。配置轨 `ConfigShardSelector` 原生提供真实全集（与 `getShardNames` 同源）；**自定义轨 selector 必须覆盖 `selectAllShards`，否则 fanout 不可用**。

### 归并语义分级

| 构造 | v1 语义 |
|---|---|
| 无 ORDER BY 的 SELECT | 各分片结果顺序拼接（concat） |
| ORDER BY（排序键已投影） | 全量拉回 + 内存排序产出真序；NULLS FIRST/LAST 三态显式生效，未声明时 null 视为最大值（asc 在后 / desc 在前） |
| 无分组裸 count（count(\*)/count(expr)，无 GROUP BY/HAVING） | 各分片 count 求和，单行结果 |
| 分页（range） | 浅分页：每分片下推 offset+limit，归并排序后截断 `[offset, offset+limit)`；总数=各分片 count 求和（独立的裸 count 查询承载） |
| GROUP BY/HAVING/DISTINCT/非 count 聚合/FOR UPDATE/UNION | 显式失败（见资格边界） |

### 事务与一致性契约

- **跨分片读无统一快照**：N 份物理 SQL 使用 N 个独立连接（各自经既有事务路径），一致性等级="每分片内一致，分片间无全局快照"。需要跨分片强一致的业务必须走单分片键路由或应用层手段。
- **事务内自动入组**：缺省部署（无 `txn-group-map` 映射）下各分片连接经 `TransactionTemplateImpl` 自动成为缺省主组的子事务；**显式多主组部署**（配置了多个主组）时，fanout 策略的全部分片 querySpace 必须映射到同一主组，否则各分片各随其主组提交（由 best-effort 语义覆盖，引擎不做收敛）。
- **读写分离正交叠加**：N 份物理 SQL 逐份显式携带只读意图标记（`readOnly(true)`），无事务的 fan-out 读落到各分片从库。
- **ORM 一级缓存前置约束**：fan-out 结果按既有行组装进 session，L1 身份键 `(entityName, id)` 依赖 id 跨分片唯一——**分布式 ID（snowflake/uuid/编码规则）是分片体系的前置约束**；多分片自增主键会在 L1 缓存产生身份冲突，属分片接入方的模型责任。
- **可观测性**：汇总日志 `nop.orm.shard.fanout`（逻辑 SQL 名、分片数、归并行数、归并耗时、是否达限中断）；每分片物理 SQL 执行明细由既有 `nop.jdbc.run` 日志覆盖。

## 扩展点与边界

- **自定义路由**：实现 `IShardSelector` 并以 `nopShardSelector` bean 名覆盖缺省 `ConfigShardSelector`（IoC by-type 自动装配到 session factory）。接口同时承载缺分片值契约（`getMissingShardPolicy`/`selectShardWhenMissing`）、DDL 展开契约（`getShardNames`）与 fanout 分片全集契约（`selectAllShards`，不覆盖则 fanout 不可用），default 方法缺省值保持 Empty 行为。
- **不做**：GROUP BY/HAVING/DISTINCT/非 count 聚合/FOR UPDATE/UNION 的跨分片归并（显式失败，见执行引擎章节）、跨分片写 fan-out（v2 复用既有事务组）、并行 fan-out 与流式 K 路归并（v2 演进）、广播表、Hint 路由、通配符实体绑定、扩容数据搬迁（nop-batch 逐分片作业）、sql-lib/原生 SQL 的 fan-out（不解析，SQL 作者显式负责）。
- **分库 × 读写分离组合**：`query-space-pattern` 指向的 querySpace 可注册为 `ReadWriteRoutingDataSource`（见 `datasource-and-readwrite-splitting.md`）——分片先切 querySpace，读写分离再在该 querySpace 内切主从，两层正交。
