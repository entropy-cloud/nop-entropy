# 数据搬迁管道（nop-orm-migration）

**模块**：`nop-persistence/nop-orm-migration`（编排器；执行面委托 nop-batch / nop-stream）
**语义规格**：数据搬迁管道设计文档（平台设计裁定承载；本页只写使用契约）

## 定位

分片扩容（N→M 片）与同构机房迁移的数据搬迁编排：**五状态机** PREPARE → FULL → INCREMENTAL → VERIFY → SWITCH/DONE/ABORT（ABORT 全程可达，旧配置未动）。

- **PREPARE**：钉定源位点 P0（mysql `SHOW MASTER STATUS` / pg `pg_current_wal_lsn()`，经 `PositionProvider` seam 供测试注入）；密钥一致门禁（搬迁前后加密密钥必须一致——换 key/重加密显式 Non-Goal）；目标物理表就绪校验。
- **FULL**：委托 nop-batch（offset 分页读（`JdbcPageBatchLoaderProvider`，查询成功后推进游标）+ 按新策略分表的 keyed upsert 写；rateLimit/batchSize/concurrency 复用既有旋钮；null 分片键 fail-fast 携带实体/主键/物理表）。
- **INCREMENTAL**：委托 debezium-cdc（`snapshot.mode=never` + **首跑 offset 种子化为 P0**——这是对 `NopStreamOffsetBackingStore` 的契约级新增）→ shard 路由 → `jdbc-2pc` sink（UPSERT_DELETE，任意点重放幂等）。多 sink 拓扑需前置探测通过（`MultiSinkTopologyProbe`），缺省落地回退形态（每目标分片单 sink 链作业）。
- **VERIFY**：门禁 = 写停止 + lag=0（静默期 CDC offset 无前进 + epoch ledger 无新增提交行）；三级校验 count → 抽样 checksum → 逐行 checksum（应用侧物理列值规范化 hash；enc 列同 key 前提下密文原样比对；脱敏展示字段为 compute 派生属性不进入比对面）。
- **SWITCH**：VERIFY 通过 → 显式 confirm（v1 人工收敛门）→ `nop.orm.shard` 配置原子翻转 + 路由重建 → 开启写门 → DONE。配置翻转后须**全部应用实例完成路由重建**才允许恢复写入。

## 写停止关

`nop.orm.migration.write-gate`（`open`|`closed`，缺省 open）。`closed` 时所有 ORM DML 在提交路径显式 fail-fast（`ERR_ORM_MIGRATION_WRITE_GATE_CLOSED`，携带实体与操作），读路径不受影响。它是 lag=0 判定与 VERIFY 门禁资格的前置契约。

## 配置契约

JSON 配置串 `nop.orm.migration`（与 `nop.orm.shard`/`nop.dao.readwrite` 同构）：scenario（relocate/rescale）、entity、source-query-spaces、target-strategy/target-query-space-mapping、write-gate、incremental、verify（sample-ratio/full-checksum）+ incremental（quiesce-seconds——lag=0 判定的静默期，归增量段配置）、throttle、batch-task-name。配置错误启动/发起即显式失败（`ERR_MIG_*` 族）。

## 边界与前置条件

- 源端 CDC 白名单 mysql/postgres；目标端 native upsert 覆盖 mysql/mariadb/postgresql（同方言迁移）。
- 源库 binlog/WAL 保留窗口必须覆盖全量时长。
- 搬迁数据面走物理 JDBC 直读直写（不经 ORM session——运行期路由契约会把写入改回旧片）。
- v1 停写窗口切换，无双写/灰度切流；热重建降级为窗口内滚动重启（多实例须满足收敛门）。
