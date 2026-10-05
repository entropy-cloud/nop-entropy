# nop-orm 分片策略配置设计

**日期**：2026-10-05
**范围**：`nop-persistence/nop-dao`（shard SPI、数据源装配）、`nop-persistence/nop-orm`（路由改写、DDL）、`nop-persistence/nop-orm-model`（实体模型声明）
**状态**：active（plan 2320 已实现；实现裁定与落地形态见 §六）
**灵感来源**：Apache ShardingSphere encrypt/sharding 功能面对比中确认的分片配置缺口

---

## 一、设计结论

1. **配置面分两层**：实体模型只声明模型级真相（`useShard` + `shardProp`，即"哪个实体分片、分片键是哪列"——已有属性，不变）；**策略配置**（算法、分片数、命名规则、绑定哪些实体）是部署环境属性，放 yaml 配置体系，不进 orm.xml。dev 与 prod 分片数不同，靠配置分叉而非模型分叉。
2. **策略注册表 + SPI 双轨**：内置算法（MOD/HASH_MOD/RANGE/INTERVAL/INLINE）由配置声明、框架实例化，经策略注册表统一装配到既有 `IShardSelector` 扩展点（`SessionFactoryConfig.shardSelector`，IoC 可注入）；自定义算法直接实现 `IShardSelector`（beans.xml 装配，覆盖内置注册表）。
3. **路由模型维持 `ShardSelection(querySpace, shardName)` 二元组**：表分片（shardName 后缀）、库分片（不同 querySpace → 既有 `nopDataSource_<querySpace>` 多数据源装配）、库+表两级组合，均由该二元组天然表达；一条 SQL 只允许一个 querySpace 的既有约束保留。
4. **缺分片键 fail-fast**：useShard 实体的 DML/查询不含分片条件时，缺省抛错（取代现状"静默落回基表名执行"的陷阱行为）；策略级开关允许显式声明回落到指定默认分片。不做隐式全分片 fan-out。
5. **路由保持在执行期 marker 改写**（`GenSqlTransformer`），不进 EQL 编译期——分片值来自每次执行的参数，执行期改写与查询计划缓存天然兼容（编译产物静态、路由动态）。

## 二、背景与动机

现状是"SPI 骨架 + 单分片路由"级别，具备以下既有事实（均已核实）：

- 实体侧：`entity.xdef` 已有 `useShard` / `shardProp` 一级属性；`GenSqlTransformer.collectShardSelections` 在执行期从 SQL marker 中取分片值，调 `IShardSelector.selectShard` 得 `ShardSelection`，将表名改写为 `{原表名}_{shardName}` 并切换 querySpace。
- 数据源侧：`dao-defaults.beans.xml` 以 `ioc:collect-beans name-prefix="nopDataSource_"` 收集每个 querySpace 一个数据源 bean，库分片的数据面已存在。
- 装配链：`SessionFactoryConfig.shardSelector` → `SessionFactoryImpl` → `JdbcQueryExecutor` 构造 `GenSqlTransformer`，IoC 注入点现成。

动机来自三个缺口：

1. **无内置算法**：`EmptyShardSelector` 是唯一实现，用户要用分片必须自己写 Java 类实现 `IShardSelector` 并装配 bean。
2. **静默陷阱**：useShard 实体的 SQL 若不含分片条件（`hasShardParam()==false`），现状**静默不改写表名、直接在基表名上执行**（`GenSqlTransformer.collectShardSelections` 的 fall-through 路径）——若基表不存在则数据库报错，若存在（如遗留表）则静默查错数据。
3. **死 SPI 面**：`IShardSelector.selectShards`（范围多分片选择）在全仓无任何调用方，是误导性表面——用户实现它不会有任何效果。

## 三、核心设计

### 3.1 配置面分层

| 层 | 放什么 | 放哪 | 变更频率 |
|---|---|---|---|
| 模型层（已有，不变） | `useShard="true"` + `shardProp`（分片键列） | orm.xml 实体声明 | 模型定稿后不变 |
| 策略层（本设计新增） | 算法类型与参数、分片命名、实体绑定、缺分片键策略 | JSON 配置（`nop.orm.shard`） | 按环境（dev/prod/扩容）不同 |
| 扩展层（已有，不变） | 完全自定义路由逻辑 | `IShardSelector` 实现 + beans.xml | 极少 |

分层的判定依据：**分片键属于业务模型**（订单按 tenantId 分片是业务事实，进模型）；**分片数与算法属于部署**（dev 1 片、prod 16 片、扩容到 32 片是运维事实，进配置）。把策略烧进模型会导致 dev/prod 模型分叉，只能靠 delta 弥补，成本高于配置分叉。

配置契约（JSON 配置串，`nop.orm.shard`，实现裁定见 §六.1；语义与 yaml 示例一致）：

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

实体绑定用显式实体名列表（不用通配符 pattern）：分片是高风险配置，隐式匹配到意外实体属于静默错误；宁可列举。`default-strategy` 是显式的全局回落，不是隐式匹配。

### 3.2 路由模型与两级分片

`ShardSelection(querySpace, shardName)` 不变，三种形态：

| 形态 | querySpace 来源 | shardName 来源 | 物理落点 |
|---|---|---|---|
| 单库分表 | 实体声明（缺省 default） | 算法输出 | `{table}_{shardName}`，同一数据源 |
| 分库 | `query-space-pattern` 按分片名映射 | 算法输出 | 表名不变，切到 `nopDataSource_{querySpace}` |
| 分库+分表 | 同上 | 同上 | `{table}_{shardName}` 且切数据源 |

**绑定表（binding tables）用"同策略名"隐式表达**：两个实体绑定同一策略且分片键值相同时，`GenSqlTransformer` 对各自 TABLE marker 的改写必然落到同一 querySpace 与同名后缀，JOIN 天然单分片——不需要 ShardingSphere 式显式 `ShardingTableReferenceRule`，也不引入新配置面。约束（既有，保留）：一条 SQL 内所有 useShard 实体解析出的 querySpace 必须一致，否则 `ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL`。

### 3.3 内置算法集

| 算法 type | 语义 | 关键配置 | 分片键类型 |
|---|---|---|---|
| `mod` | 数值取模 | `shard-count` | 数值 |
| `hash-mod` | 对值做稳定 hash 后取模（跨方言同 hash，不用数据库 hash） | `shard-count` | 任意（字符串/数值） |
| `range` | 显式边界范围 → 分片名映射表 | `ranges: {"0-100w": "0", ...}` | 数值/可比较类型 |
| `interval` | 时间区间 → 分片名（按月/日/自定义步长） | `interval-step`、`datetime-lower/upper`、`format` | 日期/时间 |
| `inline` | 表达式直接计算分片名 | `algorithm-expression`（XScript 表达式） | 任意 |

裁剪说明：SS 的 MOD 与 HASH_MOD 语义照收；VOLUME_RANGE（按容量自动均分）**不做**（需要预先知道数据分布，第一版用 `range` 显式边界替代）；CLASS_BASED 即 SPI 本身；HINT_INLINE 不做（见 §四）。

`selectShards`（范围值 → 多分片）从 SPI 中**移除**：它当前零调用方，且范围路由只有配合跨分片 fan-out 执行才有意义，而 fan-out 不在本设计范围（见 §四）。移除死表面优于保留误导性契约。

### 3.4 缺分片键策略

路由判定语义（伪代码，语义契约）：

```
对 SQL 中每个 useShard 实体的 TABLE marker：
  若 SQL 携带该实体分片键的等值条件（参数或字面量）：
    按绑定策略计算 ShardSelection → 改写表名 + 设定 querySpace
  否则：
    按 missing-shard-policy：
      fail（缺省）→ 抛 ERR_ORM_SHARD_CONDITION_MISSING（携带实体名与分片键列名）
      default-shard → 路由到 default-shard 指定的分片（显式声明低频数据落 default 分片的场景）
```

fail-fast 取代现状静默 fall-through 是**行为变更**：现状依赖"基表存在"才能工作的存量用法不存在（无内置算法意味着无人用过该 fall-through），变更无兼容负担。

### 3.5 DDL 与 Schema 升级

- `DdlSqlCreator` / auto-upgrade 策略感知：useShard 实体建表/升级时按绑定策略的分片名全集展开为 N 张物理表（`TEST_ORM_SHARD_TABLE_0..15`）；`OrmDbDiffer` 对 N 张后缀表逐一 diff。**启动期绑定校验按配置门控（实现修订）**：仅当存在全局分片配置（`nop.orm.shard` 非空）时，useShard 实体未绑定策略且无 default-strategy 才 fail-fast（`ERR_ORM_SHARD_STRATEGY_MISSING`，校验点在 nop-orm 层模型加载期）；**无任何分片配置时不校验**——未配置分片体系的部署维持既有 Empty 行为，保证既有 useShard 测试实体零变更。本条的"无条件 fail-fast"表述已按配置门控语义修订。
- 扩容（16 → 32 片）是数据搬迁问题，不是 DDL 问题：auto-upgrade 只建新表，存量数据迁移走 nop-batch 批处理作业（已有 chunk 读写/断点续传基建），本设计不含自动搬迁。

### 3.6 路由时机与查询计划缓存

路由保持执行期：`GenSqlTransformer` 在每次执行 transform 时以当次参数值计算分片，改写表名后按最终 SQL 文本复用 PreparedStatement 缓存（N 个分片 → N 个缓存条目，天然正确）。分片路由**不进** EQL 编译期——与可检索加密的辅助列改写（编译期，因列名是静态元数据）构成对照：列改写是静态决策进编译期，值路由是动态决策进执行期。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 策略配置进 orm.xml（实体 `ext:shard-strategy` 扩展属性或一级属性） | 把部署属性烧进模型：dev/prod 分片数不同将迫使模型或 delta 分叉；orm.xml 是"模型即元数据"的稳定面，分片数不是模型 |
| 独立 sharding.xml xdef 模型文件 | 策略配置是纯部署参数（键值对），不承载元模型语义；为其建 xdef + 解析器 + 校验链的成本无对应收益；yaml 配置与 `nop.dao.config.query-space-to-dialect`（querySpace→dialect 映射）等既有环境级配置同构 |
| 仅靠 `IShardSelector` SPI（现状） | 每张分片表写一个 Java 类 + beans.xml 装配，最普通的取模分片也要编码，采用成本不可接受 |
| 跨分片 fan-out 执行 + 结果归并 | 是执行引擎级能力（多 SQL 并行 + 排序/分组/聚合归并），与"一条 SQL 一个 querySpace"的连接语义模型正面冲突，须连同连接管理、事务边界一起设计，独立立项；本设计只收口"策略配置与单分片路由" |
| Hint 路由（ThreadLocal 强制指定分片） | 与 nop 显式数据源切换（`DynamicDataSource.runWith`）能力重叠；隐藏 ThreadLocal 状态与"模型即元数据"的可追溯哲学冲突 |
| 广播表（一写多库同步广播） | 一条 SQL 写多个 querySpace，超出单 querySpace 事务与连接模型；广播语义可由业务层双写 + nop-batch 补偿覆盖 |
| 实体绑定用通配符 pattern | 分片是高风险配置，隐式匹配到意外实体的静默错误代价大于列举成本 |
| `selectShards` 范围路由保留 | 零调用方的死表面，且语义上只服务于 fan-out（已被拒）；删除并以 fail-fast 语义补位 |

## 五、与已有设计的关系

- 本设计不触碰 `nop-orm-scope-and-non-goals.md` 的任何裁定（分片不在其 non-goal 清单内）；读写分离仍归属 DataSource 层（该文档 §3.5），本设计同样不动。
- fan-out/归并执行引擎、数据搬迁管道属分片域的后续设计，本设计的策略注册表与 `ShardSelection` 模型是它们的前置契约。
- 可检索加密（编译期列改写）与本设计（执行期值路由）是 nop 消化 ShardingSphere 两大机制时"编译期/执行期"分野的两个对照样本，实现风格互为参照。
- 实施时的挂点：`SessionFactoryConfig.shardSelector`（装配）、`GenSqlTransformer.collectShardSelections/transformTable`（路由与改写）、`DdlSqlCreator`/`OrmDbDiffer`（N 表展开）、`dao-defaults.beans.xml` 多数据源装配（既有）。

## 六、实现裁定记录（plan 2320 落地结论）

### 6.1 配置承载形态：JSON 配置串（非 IoC collect-beans）

**裁定**：策略配置以 JSON 配置串 `nop.orm.shard`（`DaoConfigs.CFG_ORM_SHARD`）承载，由 `ConfigShardSelector.init()` 解析构建注册表；IoC collect-beans 不作策略配置承载，仅保留为自定义 `IShardSelector` bean 的装配通道（双轨制不变：同名覆盖 `nopShardSelector` bean 即可替换注册表，`orm-defaults.beans.xml` 缺省 bean 为 `ConfigShardSelector`）。
**理由**：与 `nop.dao.readwrite`（plan 2319）的 JSON 配置先例同构；策略是部署环境属性，走配置分叉（delta config）比 beans.xml 装配更贴合 dev/prod 分叉场景；collect-beans 适合 SPI 扩展而非键值配置。

### 6.2 查询侧分片值注入：TABLE marker 替换 + 参数序号（marker 路线）

**裁定**：采用 marker 替换路线（非 CompiledSql 侧信道）。`EqlCompiler` 在 SQL 生成完成后调用 `ShardMarkerInjector.inject(...)`：基于已解析 AST 定位 shardProp 等值条件（仅 AND 连通的 WHERE/ON 条件；OR/NOT/子查询不下探，交由运行期 missing-shard-policy 裁决），用 `SyntaxMarker` 现有 7 参构造器**新建 marker 替换** builder marker 列表中的 TABLE marker（final 字段不改，nop-core 零改动）。
**缓存安全语义（重定义）**：marker 只携静态信息——`shardParamIndex` 为执行期 `paramValues`（`CompiledSql.buildParams` 产物）的下标（`AstToSqlGenerator.visitSqlParameterMarker` 记录该参数节点输出的 `?` 在全部 ValueMarker 中的序数，与 `changeMarkerValues` 的 1:1 填值顺序对齐），`shardValue` 仅承载编译期字面量；执行期值由 `GenSqlTransformer.collectShardSelections(sql, paramValues)`（签名变更）从 paramValues 取。旧实现 `sql.getMarker(shardParamIndex)` 按 marker 列表下标取 Marker 对象的语义已废除。

### 6.3 DDL N 表展开入口：显式物理表名（非实体模型包装）

**裁定**：`DdlSqlCreator` 新增带 `physicalTableName` 参数的重载（createTable/addColumn/dropColumn/modifyColumn/addUniqueKey/dropUniqueKey/addIndex/dropIndex），`ddl.xlib` 模板的表名表达式改为 `shardTableName ?? table.tableName`（含 `col.ownerEntityModel.tableName` 路径）——不传时输出与既有方法**逐字节一致**（公共语义不变）。三个建表调用点（`DataBaseSchemaInitializer` / `OrmDbDiffer`+`DataBaseUpgrader` / `AutoTestCaseDataBaseInitializer`）经 `ShardDdlHelper.shardTableNames` 做分片名全集展开。
**理由**：实体模型包装两条路均被实施证伪——JDK 动态代理无法通过 XPL 的具体类属性反射（uniqueKeys/compositePk 不在接口上）；`cloneInstance` 视图的 `init()` 会向共享的 frozen 关系/列实例回写 owner（not-allow-change）。显式表名入口是计划预先认定的更干净选项。
auto-upgrade 适配：发现的分片物理表（T_0/T_1）按逻辑表名归一（diff 按 tableName 匹配实体），每逻辑实体取先发现的一张物理表作代表，diff 产生的列 DDL 由 shard-aware 的 `OrmDbDiffer` 对 N 张物理表逐一展开。

### 6.4 SPI 面终态

`IShardSelector` = `selectShard`（值路由）+ default 方法组：`hasStrategyConfig`/`isEntityBound`（启动绑定校验门控，消费方 `ShardBindingValidator`@nop-orm）、`getMissingShardPolicy`/`selectShardWhenMissing`（缺分片值契约，消费方 `GenSqlTransformer`）、`getShardNames`/`getShardTableName`（DDL 展开，消费方 `ShardDdlHelper`）。错误码分层：配置解析类错误归 `DaoErrors`（`ERR_DAO_SHARD_CONFIG_INVALID`/`ERR_DAO_SHARD_ALGORITHM_FAIL`），路由与绑定校验错误归 `OrmErrors`（`ERR_ORM_SHARD_CONDITION_MISSING`/`ERR_ORM_SHARD_STRATEGY_MISSING`）。
