# ORM 分片（单分片路由）

Nop 的分片是 **ORM 执行层的单分片路由**：实体声明 `useShard` + `shardProp`（模型级事实），策略（算法/分片数/绑定实体/缺分片键策略）经 JSON 配置 `nop.orm.shard` 声明（部署级事实）。写路径与读路径共用同一 `IShardSelector` 扩展点，路由结果为 `ShardSelection(querySpace, shardName)` 二元组——表分片（表名 `_N` 后缀）、库分片（querySpace 切换）、库+表组合由该二元组统一表达。


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
| `missing-shard-policy` | `fail`（缺省）\| `default-shard` |
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
| 实体未绑定策略（未配置分片体系） | 维持既有基表执行语义（零变更） |

## 启动期绑定校验（配置门控）

- **存在全局分片配置时**：useShard 实体未绑定策略（显式或 `default-strategy`）→ 模型加载期 fail-fast `ERR_ORM_SHARD_STRATEGY_MISSING`。
- **无任何分片配置时不校验**：未配置分片体系的部署维持既有 Empty 行为，既有 useShard 实体零变更。

## DDL N 表展开

绑定策略的 useShard 实体建表/升级时按分片名全集展开为 N 张物理表，覆盖三个调用点：启动建表（`DataBaseSchemaInitializer`）、auto-upgrade（`OrmDbDiffer`/`DataBaseUpgrader`，对 N 张物理表逐一列 diff，不加列到不存在的逻辑基表）、autotest 建表（`AutoTestCaseDataBaseInitializer`）。DDL 定向经 `DdlSqlCreator` 的显式物理表名重载（模板 `shardTableName ?? table.tableName` 回退，不传时输出与既有方法逐字节一致）。

## 扩展点与边界

- **自定义路由**：实现 `IShardSelector` 并以 `nopShardSelector` bean 名覆盖缺省 `ConfigShardSelector`（IoC by-type 自动装配到 session factory）。接口同时承载缺分片值契约（`getMissingShardPolicy`/`selectShardWhenMissing`）与 DDL 展开契约（`getShardNames`），default 方法缺省值保持 Empty 行为。
- **不做**：跨分片 fan-out 执行与结果归并（roadmap WI7/WI8）、广播表、Hint 路由、通配符实体绑定、扩容数据搬迁。
- **分库 × 读写分离组合**：`query-space-pattern` 指向的 querySpace 可注册为 `ReadWriteRoutingDataSource`（见 `datasource-and-readwrite-splitting.md`）——分片先切 querySpace，读写分离再在该 querySpace 内切主从，两层正交。
