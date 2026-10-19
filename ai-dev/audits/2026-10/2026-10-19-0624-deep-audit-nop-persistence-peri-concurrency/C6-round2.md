# C6 深挖第 2 轮 — nop-persistence-peri（WI 2.5）

## 深挖范围与方法

第 1 轮 5 条（C6-001..005）集中在链作业生命周期与 PubSub/连接池配对。本轮按任务书四条未扫面深挖：

| 深挖线 | 触达文件 | 结论 |
|---|---|---|
| ① db-migration changeSet 执行器的资源句柄（每 changeSet 的 Statement 生命周期、失败回滚路径的连接状态） | `MigrationExecutor.java`、`MigrationEngine.java:130-260`、`InsertDataExecutor.java`、`SqlExecutor.java`、`UpdateDataExecutor.java`（全文） | **阴性（Statement 面）**：全部 12 个执行器经平台 `JdbcTemplate.executeUpdate/executeMultiSql` 执行，模块内无任何手工 `Connection/Statement/ResultSet` 获取点；`MigrationContext` 不持有裸连接。**阳性证据面（事务面）**：`executeMigration`（`MigrationEngine.java:242-253`）对 changeset 逐 change 循环执行（`MigrationExecutor.execute:41-47` 同型）**无任何事务界定**——每个 change 经 JdbcTemplate 独立自动提交，单 migration 内第 k 个 change 失败时前 k-1 个 change 的 DDL/DML 已生效且仅记一条 FAILED 行；此为第 1 轮 CL-003"半个成功"的**每-migration 原子性**证据扩展（见下方扩展节），非新发现 |
| ② geo/pdm 流式解析的中间产物 | `PdmModelParser.java`、`PdmModelLoader.java`、`GeometryTypeHandler.java:154-168`、`H2GisInitializer.java` | **阴性**：PDM 解析为 `XNodeParser` DOM 全量载入 + 11 个 per-instance map，中间产物全部线程封闭且失败路径随实例整体丢弃（无静态残留、无文件/临时资源）；geo 的 Blob 流 `toByteArray:158-168` finally 关闭（第 1 轮已核，本轮复核确认）；H2GIS init 经 `runWithConnection` 平台管理 |
| ③ tdengine 写出流关闭序 | `TdEntityPersistDriver.java`（全文）、`TdSqlHelper.java`（全文） | **阴性**：写出全部经 `jdbc().executeUpdate/executeQuery`（平台 JdbcTemplate 管理连接/语句生命周期），驱动内无手工流/连接；`SQL.SqlBuilder` 为纯内存组装，无可关闭资源 |
| ④ Lettuce 适配的 buffer/codec 生命周期 | `PrefixTextCodec.java`（全文）、`LettuceExecutor.java`、`AbstractLettuceOperations.java` | **阴性**：`PrefixTextCodec` 无状态（decode/encode 均为 per-call 堆分配 String/ByteBuffer，无直接内存、无池化借用、无可泄漏句柄）；`LettuceExecutor.evalScript` 的 NOSCRIPT 回退经 `handle`/`thenCompose` 正常传播异常，无悬挂 future；共享连接的借取为 round-robin 直取（无 checkout/return 配对面可破） |

补充复现：dbtool `JdbcMetaDiscovery`/`DataBaseUpgrader`、orm-migration `MigrationJobStateStore.initializeTable`（TWR）、`ShardedUpsertConsumerProvider.upsertGroup:102-117`（PreparedStatement TWR + SQLExceptionTranslator）复核与第 1 轮配对表一致，无回归性新缺口。

## 扩展

### 扩展 nop-persistence-peri-CL-003（migrate"半个成功"→ 每-migration 无事务界定的新证据锚点）

第 1 轮 CL-003 以 `failOnError=false` 为条件描述"失败迁移记账后引擎继续执行后续迁移"。本轮补足其无条件底座证据：即便 fail-fast 全开，**单个 migration 的 changeset 也不具备原子性**——`MigrationEngine.executeMigration:247-252` 逐 change 循环、`MigrationExecutor.execute:42-47` 同型循环、全部执行器直连自动提交，中途失败时已生效 change 无补偿路径（rollback 语义仅覆盖显式 `MigrationEngine.rollback:366-402` 的整版本逆放，CL-004 已立案其簿记缺失）。净效果：FAILED 行对应的实态是"changeset 前缀已应用"，重跑同版本将先撞已建对象（`createTable` 等 fail）再停留在同一中间态。修复方向与 CL-003 建议合并处置（逐 change 进度簿记或 per-migration 事务包裹——后者受 DDL 自动提交方言限制，需按方言评估 MySQL 隐式提交边界）。

## 新增发现

（本轮无新增发现。四条深挖线的结论均为阴性复现或归并为第 1 轮已立案条目的扩展证据，无满足附录 A 立案标准的新失效面。）

## 维度集外线索（overlay）

（本轮无新增 CL；CL-025 已登记于 C3-round2 文件，全维度统一流水不重复。）

## 继续判定

**终止信号**。依据：本轮新增 0 条（无任何严重度分列）；四条任务书深挖线全部收敛——Statement/codec/stream 三个资源面确认为平台管理 + 无状态形态（阴性），唯一阳性（changeset 无事务界定）为第 1 轮 CL-003/CL-004 同族的证据补强而非新根因。C6 维度在两轮后资源获取点配对表已闭合：模块内手工资源获取点仅存于第 1 轮已立案的链作业与连接池两处，无遗留未配对面。

## 复核结论（WI 2.5 C6 维度独立复核）

- 本轮「0 新增发现 + 终止信号」结论**复核成立**：四条深挖线的阴性判定经抽查回码确认（Statement/codec/stream 三面确为平台 JdbcTemplate 管理 + 无状态形态）；changeset 无事务界定的定性（CL-003/CL-004 同族证据补强，非新根因）无误。终止信号有效。
- 第 1 轮 5 条发现复核详情见 `C6-resource-lifecycle.md`「维度复核结论」节：保留 5 / 降级 0 / 驳回 0；最终严重度 P0×1（C6-002 维持，即时通道，与 C3-003 同案合并主登记）/ P1×3 / P2×1；现状标注 新发现 3、已知未修复 2。
- 附带登记（不影响本轮结论，供后续工单参考）：`ai-dev/audits/check/nosql-cdc.md`（2026-08-19）两条 PubSub/连接生命周期旧账状态勘定——[P2] ClientResources 泄漏面已修复（现行 `doStop:128-138` 显式 `clientResources.shutdown()`，代码证据 L133-137）；[P2] doStop 后 `pubSubConnection` 不置空、`subscriptions` 不清空（restart 后拿到已关闭连接）现行代码仍未修复（`doStop:112-117` 仅 close），该面与 C6-004 同类，C6-004 修复工单应一并核对。
