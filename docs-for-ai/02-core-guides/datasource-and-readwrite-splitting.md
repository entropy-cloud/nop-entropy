# 数据源与读写分离

Nop 的读写分离是 **DataSource 层的路由装饰器**（`ReadWriteRoutingDataSource`），对事务管理器、事务模板和 ORM 完全透明。路由策略单点在 DataSource 装饰器内部，ORM/DAO 执行层只传播"只读意图"，不参与路由决策。

## 一致性契约

| 场景 | 路由 | 说明 |
|---|---|---|
| 活动事务内的任何语句 | 主库 | 事务连接在事务 open 时缓存自主库，一个事务一个库 |
| 无事务 + SELECT（EQL/QueryBean/sql-lib 只读项） | 从库 | 读扩展场景，复制延迟可容忍 |
| 无事务 + DML / 原生 SQL 未标注 | 主库 | fail-safe：语义未知宁走主库 |
| 显式 `ReadWriteHint.runWithPrimary` | 主库 | 写后立即读、报表等强一致场景 |
| 显式 `ReadWriteHint.runWithReplica` | 从库 | 从库不可用时**显式报错**，不静默回退主库 |
| 写后立即读（无事务、无 hint） | 可能读到从库旧数据 | 无隐式 write-then-read 粘滞；强一致读必须用事务或 `runWithPrimary` |

## 判定矩阵（三级判定）

```
getConnection():
  显式 hint 存在        → PRIMARY 走主库；REPLICA 走从库池（不可用则报错）
  否则（UNSET）         → 主库
```

- "活动事务 → 主库"由上游保证：事务连接缓存在事务对象上，不再触发路由判定；且 `JdbcTemplateImpl` 的只读桥接在已打开事务内不建立 REPLICA 作用域。
- "只读语句 → 从库"由只读意图标志驱动（见下文"只读意图的来源"），路由 DataSource 内部只见 REPLICA 作用域。

## 配置契约

配置键 `nop.dao.readwrite`（JSON 文本，与 `nop.dao.config.query-space-to-dialect` 同为"字符串配置事后解析"形态）：

```json
{
  "default": {
    "write-ds": "nopReadWriteDs_default_write",
    "read-ds": ["nopReadWriteDs_default_read1", "nopReadWriteDs_default_read2"],
    "load-balancer": "round-robin",
    "weights": { "nopReadWriteDs_default_read1": 8 },
    "replica-fail-threshold": 3,
    "replica-cooldown-seconds": 30
  }
}
```

| 键 | 必填 | 缺省 | 说明 |
|---|---|---|---|
| `write-ds` | 是 | — | 写库物理 DataSource bean 名 |
| `read-ds` | 是 | — | 从库物理 DataSource bean 名数组（至少一个） |
| `load-balancer` | 否 | `round-robin` | `round-robin` \| `random` \| `weight`；未知值快速失败 |
| `weights` | 否 | 等权(1) | `weight` 算法的从库权重（按 bean 名） |
| `replica-fail-threshold` | 否 | `3` | 连续失败次数阈值，达到后从库进入冷却 |
| `replica-cooldown-seconds` | 否 | `30` | 冷却时长；期满半开探测，成功恢复、失败重新冷却 |

### 物理数据源 bean 命名

物理 DataSource bean 必须以 **`nopReadWriteDs_`** 为前缀（如 `nopReadWriteDs_default_write`）。该前缀刻意区别于 `nopDataSource_`——`dao-defaults.beans.xml` 中 `DefaultTransactionManager` 以 `name-prefix="nopDataSource_"` 收集数据源，若读写分离物理库沿用该前缀会被误收集为幻影 querySpace。

### 装配行为

- 装配初始化器 `nopReadWriteDataSourceInitializer`（`dao-defaults.beans.xml`，`init-method="init"`）在容器启动时读取配置并注册：非 default querySpace 经 `addQuerySpace`，default querySpace 经 `setDefaultFactory` 替换。
- **未配置 `nop.dao.readwrite` 时零行为变更**：不注册任何 querySpace，既有单库装配保持原样。配置存在即启用，无独立开关。

## 只读意图的来源

| 路径 | 机制 |
|---|---|
| EQL / QueryBean 查询 | `JdbcQueryExecutor` 对 `statementKind==SELECT` 的语句自动标记 `SQL.readOnly`（业务代码无需任何标注） |
| sql-lib 原生 `<sql>` 项 | 显式标注 `readOnly="true"`（`sql-lib.xdef` 属性，经生成链进入 `_SqlItemModel`）；未标注走主库 |
| JDBC 裸 SQL | `SQL.begin().sql(...).readOnly(true)` 显式标记 |
| 显式 hint | `ReadWriteHint.runWithPrimary / runWithReplica / runWithReadOnly`（try/finally 作用域原语，线程池环境必须用 runWith 形式） |

`SQL.readOnly` 只表达"只读意图"，经 `SQL.begin(sql)` 重建（分页 handler）与 `SqlBuilder.copy()` 复制时保留。

## fail-safe 边界

- **从库全部不可用**（池空、全冷却或本次获取全部失败）：抛 `nop.err.dao.readwrite.no-replica-available`（`NopException`，携带各从库状态摘要），**不静默回退主库**——否则从库故障会静默演变为全量主库压力。
- **故障状态机**（进程内自治，无外部依赖）：获取连接失败 → 立即按负载均衡顺序换下一台；连续失败达阈值 → 冷却跳过；冷却期满 → 半开放行探测；探测成功恢复、失败重新冷却。
- **不解析 SQL 文本**：只读判定只来自元数据（`statementKind`、sql-lib 声明、显式标志），与 ShardingSphere 的 parse-route 管线不同。
- **裸 `openConnection(querySpace)` 借用路径**：保持主库（无语句语义可判，fail-safe）。
- **ORM 物理路径**（`GenSqlHelper` 按主键 load 等）：v1 不标记只读（fail-safe 主库）。

## 代码锚点

- 路由 DataSource：`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/ReadWriteRoutingDataSource.java`
- hint 原语：`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/ReadWriteHint.java`
- 负载均衡：`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/ReadWriteLoadBalancers.java`
- 装配初始化器：`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/ReadWriteDataSourceInitializer.java`
- 只读桥接：`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java`（`withTxnForSql`）
- EQL 只读标记：`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java`
- sql-lib 属性定义：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef`（`readOnly` 属性）

## 已拒绝的方案

ORM 层路由、运行期 SQL 解析、隐式写后读粘滞、事务内 FIXED/DYNAMIC 读策略、从库故障静默回退主库、注册中心动态发现、独立代理进程、连接池 readonly hack——拒绝理由见设计文档 §四。
