# C8 深挖第 2 轮 — nop-persistence-peri（WI 2.5）

## 深挖范围与方法

第 1 轮 6 条（C8-001..006）集中在 PubSub 投递、校验器/驱动批上界与队列旋钮。本轮按任务书四条未扫面深挖：

| 深挖线 | 触达文件 | 结论 |
|---|---|---|
| ① nosql 扫描/迭代器面的拉全量（cursor 无界） | `LettuceMessageService.java:132-148,227-261`、`LettuceListOperations.forEachItemAsync:125-133`、`LettuceSetOperations/LettuceHashOperations` 遍历族 | **发现 SCAN 全库迭代面**（C8-007）：第 1 轮把 `forEachEntry` 记为"SCAN 分页 100 正面先例"，本轮复核发现其分页只限单页大小，**总迭代范围 = 整个 Redis keyspace**（无 MATCH 前缀、无总键数上限、无时限）——分页正确性与总量无界是两个独立面 |
| ② db-migration 大 schema 变更批的内存包络 | `SqlExecutor.java`、`InsertDataExecutor.java`、`UpdateDataExecutor.java`、`MigrationEngine.executeMigration:242-253` | **阴性**：每个 change 构造单条语句（insertData/updateData 每 change 一行），`splitStatements` 的整段 SQL 体与批量 change 集合受迁移模型体积约束（配置期有界），无运行期批累积；executeMultiSql 的语句切分在平台 JdbcTemplate（不重审） |
| ③ orm-rpc 批量上限语义 | `RpcEntityPersistDriver.java`（第 1 轮 C8-004 已覆盖批载/批写全入口） | **已覆盖确认**：ids/data 三处收集点（`:368-411` 批载、`:316-323` topoAsc 写、`:328-335` 删除）均无 chunk，无第 1 轮之外的新收集点；`loadAsync` 单实体 N+1 面已由 CL-024 立案 |
| ④ tdengine tag/字段展开放大 | `TdEntityPersistDriver.batchExecuteAsync:128-160`、`TdSqlHelper.genBatchInsertSubTableSql:64-71`、`genInsertSubTableSql:73-94`、`appendValues:122-135` | **扩展 C8-003**（非新条目）：行数 chunk 之外还存在 tag 声明维度的放大——每个不同子表（nbr 基数）独立重复 `USING <super> (tagcols) TAGS(字面量)` 声明，SQL 文本 = Σ子表(tag声明 + 行×dataCols)；同时发现同子表内 tag 值分歧被静默取首行（见扩展节） |

## 新增发现

### [C8-007] forEachEntry/forEachEntryAsync 的 SCAN 分页无键过滤、无总量上限、无时限：单次调用即迭代整个 Redis keyspace 并全量 MGET 取值

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:132-148`（sync 版）、`:227-261`（async 版 + `scanAndProcessAsync` 递归链）
- **证据片段**:
  ```java
  // LettuceMessageService.java:132-147 —— ScanArgs 仅 limit(100)，无 MATCH、无 COUNT 预算、无游标总数界
  public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {
      ScanCursor cursor = ScanCursor.INITIAL;
      ScanArgs args = new ScanArgs().limit(100);
      do {
          KeyScanCursor<String> result = sync().scan(cursor, args);   // 迭代整个 keyspace
          List<String> keys = result.getKeys();
          if (keys != null && !keys.isEmpty()) {
              List<KeyValue<String, Object>> values = sync().mget(keys.toArray(new String[0]));  // 每页全值取回
              ...
          }
          cursor = result;
      } while (!cursor.isFinished());
  }
  ```
- **严重程度**: P2（规模推演可具体化：DB 键基数达到 10^6 量级时单次调用为分钟级阻塞 + GB 级累计分配；接口为 `INosqlService` 公共遍历契约，任何宿主接入即暴露）
- **并发场景**: 调用方线程（sync 版在整个循环期间占用调用线程，每命令经 round-robin 借共享连接，`soTimeout` 仅约束单命令不约束循环总时长）与 Redis 服务端（SCAN 无 MATCH 时每页需全 keyspace 位图游标推进，服务端 CPU 随键基数线性付出）；async 版在完成线程上链式递归（`scanAndProcessAsync:236-260` thenCompose 链），期间该键空间的写入并发发生（SCAN 语义下可达性不保证，非缺陷，但消费方不可假设一致性快照）
- **触发时序**: 非竞态，规模推演：T1 = 宿主以 `INosqlService.forEachEntry` 做"清理过期业务键/统计"类运维动作（该 API 的典型用途）；T2 = Redis 实例已承载 10^6–10^8 键（C7-005 已证明 NosqlCache 写入键无 TTL 无界累积——两者复合时键基数由调用方自身历史决定）→ SCAN 循环推进全库（每页 100 键 × 10^4–10^6 页），每页 MGET 物化 100 个 value（经 PrefixTextCodec 反序列化为对象）→ 调用线程被钉住分钟~小时级，累计堆分配 O(全库值体积)（逐页可回收但峰值随消费回调滞留放大）；单页 mget 大 value 时还可触碰 Redis 单响应体上限。全程无进度、无取消入口、无中断响应
- **现状**: 第 1 轮 C8-005 将本方法记为"SCAN 分页 100 正面先例，不报"——本轮复核认定该裁定只覆盖了"单页物化有界"，遗漏"迭代总量无界"：`ScanArgs` 未携带任何 `match(prefix)`/`count` 约束，`forEachEntry` 语义上即"遍历本实例全部键"，与 `INosqlService` 的键空间前缀约定（sessionStore 的 prefix、限流的 `{key}:` 等命名空间惯例）完全脱节；`LettuceHelper.toMap`/消费回调链无背压点
- **风险**: 运维/清理类调用在大实例上触发分钟级线程钉住与服务端 CPU 放大；与 C7-005（键无界累积）复合形成"无界写入 + 全库遍历"的放大闭环；async 版完成线程长占与 C3-005/C8-001 的 event loop 饥饿面叠加
- **建议**: (1) `forEachEntry*` 增加匹配约束选项（`ScanArgs.match(pattern)` + 总键数/时长上限，超限 fail-fast 或回调式分片），最小修在 `INosqlService` javadoc 声明"遍历范围为整个 keyspace、仅限小实例/运维窗口使用"；(2) 修 C7-005 的写路径 TTL 缺失以截断键基数增长源；(3) 大遍历需求引导宿主改用按业务前缀的 SCAN（平台 `ICache` 契约层提供带前缀的遍历入口）
- **失效面枚举**（F3 同族清扫）: 遍历/扫描族全清单——① `forEachEntry:132-148`（本条主对象，sync 全库钉线程）；② `forEachEntryAsync:227-231` + `scanAndProcessAsync:233-261`（本条主对象，async 全库链式迭代）；③ `LettuceListOperations.forEachItemAsync:125-133`（单 key LRANGE 0 -1，C8-005 已立案，不重复）；④ `LettuceHashOperations.forEachEntry*:170-181`（单 key HGETALL，C8-005 失效面 ③ 已降观察）；⑤ `LettuceSessionStore.getAsync:34-43`（单会话键 HGETALL + 拷贝，键级有界，排除）；⑥ `getSize/dbsize`（O(1) 计数，排除）；⑦ dbtool `JdbcMetaDiscovery.discover(null,null,"%")` 全库 schema 物化（元数据有界，第 1 轮已排除）
- **信心水平**: 确定（无 MATCH/上限的 SCAN 语义与循环结构为代码级事实；具体阻塞时长随键基数与值宽，属规模必然）
- **误报排除**: 非第 1 轮 C8-005 的重复——该条对象是"单命令全量物化"（LRANGE 0 -1），本条对象是"迭代语义的总范围无界"（SCAN 全库 + 每页 MGET 全值），修复点不同（分页 vs 匹配约束/上限）；非"SCAN 天然增量无阻塞"的误报豁免——SCAN 服务端虽不阻塞，客户端侧的全库循环钉线程与全值 MGET 物化是真实过载面，且"先例未复用"论据（C8-005）反证模块内对遍历范围从未做契约声明
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（复核：forEachEntry（:132-148）/forEachEntryAsync（:227-231）+ scanAndProcessAsync（:233-261）的 ScanArgs 仅 `limit(100)`、无 MATCH、循环无总量/时限上限、每页全值 MGET 逐行核实成立；与 C8-005 的「非同案」论证（单命令全量物化 vs 迭代总范围无界，修复点互异）经对照成立——第 1 轮「SCAN 分页 100 正面先例」裁定确属只覆盖单页物化面，本轮补面正当非重复立案。全仓零生产调用方普查成立（仅测试 fake），按本单元零调用方缺陷落点 P2 先例（C1-012/C7-015）维持 P2；触发时序引用的复合前提 C7-005 已被 C7 复核降档 P2（NosqlCache 零生产实例化），复合场景双重条件化，不影响本条本体判级）

## 扩展

### 扩展 C8-003（TdEntityPersistDriver 批写无 chunk 上界 → tag 声明维度放大 + tag 分歧静默吸收）

第 1 轮 C8-003 以"行数×全列字面量内联、无 chunk"立案。本轮补足两个独立放大维度，供修复设计参考：

1. **tag 声明放大**：`splitSubTables`（`TdEntityPersistDriver.java:162-170`）按 nbr 值分组后，`genBatchInsertSubTableSql`（`TdSqlHelper.java:64-71`）对每个子表独立生成 `INSERT INTO <sub> USING <super> (tagcols) TAGS(字面量) VALUES...` 声明（`genInsertSubTableSql:73-94`）——SQL 文本 = Σ子表(tag声明长度 + 行数×dataCols 字面量)。因此 C8-003 建议的"按行数 chunk（如 500 行/段）"**不足以封顶**：500 行命中 500 个不同 nbr（物联网设备上报的常态分布）时，单条语句仍含 500 份 TAGS 声明；chunk 上界必须同时按"子表声明数 × 估算字节数"双维度切分。
2. **tag 值分歧静默吸收**：同子表组内各实体除 nbr 外的其他 tag 列若取值不同，组内 TAGS 仅取 `list.get(0)` 首行（`genInsertSubTableSql:73-90`）——TDengine 语义下子表已存在时 TAGS 被忽略、自动建表时仅首行 tag 生效，其余行的 tag 值分歧被无告警吸收。这与 ORM 语义（每实体的 tag 列值即其属性）存在静默偏差，属 C8-003 修复时需一并声明或 fail-fast 的边界（写入侧 warn-once 首检 tag 分歧，与模块 D1 哲学一致）。

## 维度集外线索（overlay）

（本轮无新增 CL；CL-025 已登记于 C3-round2 文件。）

## 继续判定

**终止信号**。依据：本轮新增 1 条 P2（C8-007）+ 1 个扩展节（C8-003 扩展证据），无 P0/P1；四条深挖线中 db-migration 批内存、orm-rpc 批语义两条为确定性阴性/已覆盖确认，tdengine 线产出为已立案条目的修复设计级证据扩展（不新增立案面）。C8 的生产-消费边界清单经两轮后收敛：无界写入源（C7-005）、无界遍历（C8-007）、无界批载荷（C8-003/004）、无界队列（C8-006）已覆盖全部"上游快于下游"形态，剩余为修复验证而非新深挖对象。

## 复核结论（WI 2.5 C8 独立复核，2026-10-18）

本文件 1 条条目（C8-007）+ 1 个扩展节（扩展 C8-003）逐项回码复核：**保留 1（P2 维持）、扩展成立、驳回 0、待定 0**；现状新发现。要点：

1. **C8-007 保留 P2**：ScanArgs 仅 limit(100)、无 MATCH、循环无总量/时限上限、每页全值 MGET 逐行核实成立；与 C8-005「非同案」论证经对照成立（单命令全量物化 vs 迭代总范围，修复点互异），本轮补面正当非重复立案。零生产调用方普查成立，按本单元零调用方缺陷落点 P2 先例维持 P2；复合前提 C7-005 已被 C7 复核降档 P2（NosqlCache 零生产实例化），复合场景双重条件化，不影响本体判级。
2. **扩展 C8-003 成立**：tag 声明放大（每子表独立 `USING…TAGS` 声明，TdSqlHelper:64-71/73-94）与 tag 分歧静默取首行（:66 以 `list.get(0)` 生成 TAGS）逐行核实；「按行数 chunk 不足以封顶、须按子表声明数×估算字节双维度切分」论证成立，随 C8-003 工单作为修复设计输入。另复核修正（详见 C8-backpressure.md「C8-003/004 复核修正」节）：C8-003/004 批载面受会话层 `EntityPersisterImpl._batchLoad`（nop-orm:202-215）默认 1000/chunk 分块约束，原「会话层无上限」表述仅对写路径成立；两文件判定均维持 P2。
3. **终止信号复核维持**：深挖线 ②（db-migration 批内存阴性：每 change 单语句、无运行期批累积）抽验成立；线 ③（orm-rpc 批语义已覆盖确认）成立。七条发现 + 扩展已覆盖无界写入（C7-005）、无界遍历（C8-007）、无界批载荷（C8-003/004）、无界队列（C8-006）、无界投递通道（C8-001）全部「上游快于下游」形态，两轮收敛结论成立。

判定表、C8-001 严重度统一裁定意见与最终保留清单集中见 C8-backpressure.md「维度复核结论」节。
