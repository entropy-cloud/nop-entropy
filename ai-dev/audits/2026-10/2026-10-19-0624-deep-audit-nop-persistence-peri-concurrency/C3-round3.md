# C3 深挖第 3 轮（收敛轮）— nop-persistence-peri（WI 2.5）

## 深挖范围与方法

本轮为收敛轮，触发面 = C3-008（IncrementalLagProbe 键错位）。围绕「lag 探测与链注册名的对应关系」做门禁全链复核，并对 runIncremental/runVerify 全链 grep 同族键位/名错位站点。复核线与结论：

| 复核线 | 触达文件 | 结论 |
|---|---|---|
| ① 探测/注册两侧命名派生的第三层：DebeziumCdcSourceFunction 的 instanceName 层 | `DebeziumCdcSourceFunction.java:431,441-443,461-473,568-570`、`DebeziumSourceSupport.java:77-79` | registry 键 = `instanceName(cfg.getName(), subtaskIndex, totalParallelism)` = `parallelism>1 ? name+"-"+i : name`。migration 链恒单并行度（不调 validateParallelism），该层后缀惰性——C3-008 的两侧对照（探测裸 base vs 注册 base-i）维持成立，无隐藏后缀 |
| ② offset registry 消费面全量清点 | grep `forConnector|clearConnector` 全仓（排除 test）：仅 `DebeziumCdcSourceFunction`（写侧）与 `IncrementalLagProbe:83`（读侧）两个消费点 | 键派生不对称面闭合：写侧经 instanceName 派生，读侧裸配置名。无第三个消费点 |
| ③ MultiSinkTopologyProbe 消费面与副作用 | `MultiSinkTopologyProbe.java`（全文）、`StreamExecutionEnvironment.java:135-137,317` | `getExecutionEnvironment()` 每次 new 实例——probe 的探测图与 startChain 的链图互不污染（阴性）；`fanoutEnabled` 生产调用点**为零**（grep 证实），startFallbackChains 仅 log probe 结果，拓扑恒为 fallback 形态——C3-008 失效面⑤的 fan-out 形态当前为**死代码**，修复只需覆盖 fallback 命名 |
| ④ runIncremental→startChain→filter 全链键位核对 | `IncrementalJobManager.java:134-139,146-188`、`ShardChangeEventRouter.java:40-61`、`TargetShardMapping.java:53-89,235-262`、`ConsistencyVerifier.java:120-129,157-189`、`FullCopyBatchJob.java:73-118`、`ShardedUpsertConsumerProvider.java:73-118` | 发现**同族键位错位新站点**：relocate 场景 router 的 querySpace 硬编码 `""` 与目标物理表的 mapped querySpace 错位（C3-009）；lag 门禁 ledger 观测 querySpace 与 sink 提交 querySpace 错位（C3-010） |
| ⑤ seed 派生链复核 | `CdcOffsetSeedBuilder.java:47-64`、`IncrementalJobManager.java:89-90,153-154`、`MigrationJobConfig.java:142-146` | serverName 缺省=分片 connector 名，registry 按 connector 名分桶、分区内按 serverName 键——链内自洽（C3-008 失效面⑥裁定维持）；`connector-name` 经 JSON parse 强制非空（L143-145），探测侧 null 名仅 Builder 路径可达 |
| ⑥ 其余 C3 面（PubSub/lettuce 异步链/rpc 桥接/tdengine） | 第 1/2 轮已扫站点不重复开箱 | 无新 P0/P1 实证，收敛 |

## 新增发现

### [C3-009] relocate 场景路由面 querySpace 硬编码空串与目标物理表错位：增量链 filter 谓词恒 false 全量丢事件，FULL upsert 写回源侧 default querySpace

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/ShardChangeEventRouter.java:40-42,59-61`（主对象）；`TargetShardMapping.java:71-78,235-246,251-262`（equals + 同根兄弟）；`IncrementalJobManager.java:134-139,172`（filter 消费点）；`FullCopyBatchJob.java:73-83`、`ShardedUpsertConsumerProvider.java:79-101`（FULL 消费点）；`OrmMigrationOrchestrator.java:456-459`（verifier 模板错位兄弟）
- **证据片段**:
  ```java
  // ShardChangeEventRouter.java:41-42 —— relocate 分支硬编码空 querySpace
  public PhysicalTable route(ChangeEvent event) {
      if (mapping.isRelocate())
          return new PhysicalTable("", mapping.getBaseTableName());
  // TargetShardMapping.java:165-166 —— 链目标表带 mapped querySpace（relocate 契约本体）
  sourceTables.add(new PhysicalTable(sourceQuerySpace, entityModel.getTableName()));
  targetTables.add(new PhysicalTable(targetQuerySpace, entityModel.getTableName()));
  // IncrementalJobManager.java:172 —— filter 谓词 = 全字段 equals
  stream.filter(event -> router.belongsTo(event, target)).sink(sink);
  ```
- **严重程度**: P1（声明场景确定性结构失效：relocate 是模块两大 scenario 之一，增量重放 100% 丢事件、FULL 复制写错 querySpace；触发链无任何时序前提）
- **并发场景**: 链作业线程（filter 谓词执行）与编排线程（target 表清单构建）；缺陷不依赖交错——`PhysicalTable.equals`（L77：`querySpace.equals(...) && tableName.equals(...)`）对 `("" ,tbl)` vs `(mappedQs,tbl)` 恒 false
- **触发时序**: T1：relocate 作业 `target-query-space-mapping = {"oldqs":"newdc"}`（relocate 契约要求映射必填且语义即"指向新机房数据源"，`MigrationJobConfig.java:121-133`）→ 目标物理表 = `(newdc, tbl)`。T2：`runIncremental` 启动链，CDC 事件到达 → `belongsTo(event, (newdc,tbl))` = `((newdc,tbl).equals(("",tbl)))` = **false** → 每条事件被 filter 丢弃，offset 照常前进（消费在发生）→ 静默零写入。交叠后果：VERIFY 期 lag=0 门禁在源静默后照常通过（offset 冻结 + C7-008/C3-008 下的 ledger 观测弱化），唯一剩余闸门是 count/checksum——默认 `verify-count=true` 时 ERR_MIG_COUNT_MISMATCH 必然触发（迁移恒失败在 VERIFY，叠加 C7-002 卡死）；`verify.count=false` 且未配 checksum 时 `verifyResult=passed` → confirmSwitch → DONE → 生产流量路由到从未写入的空表。FULL 路径同根：`routeRow`（TargetShardMapping:236-237）同样返回 `("",tbl)`，upsert 分组（ShardedUpsertConsumerProvider:100-101 `querySpace(target.getQuerySpace())` = ""）经**主 jdbcTemplate**（FullCopyBatchJob:76 注入的是源侧 template）的 default querySpace 落回旧库源表（幂等 upsert 同值行，"成功"无报错），mapped 目标库从未收到行。
- **现状**: relocate 分支的 `""` 是为「同库同名表 + 全缺省 querySpace」测试形态写的快捷路径，与 `ensureTargetTablesReady`（Orchestrator:376-383 按 mapped qs 校验）、sink 构造（`:513` `querySpace(target.getQuerySpace())`）、verifier 扫描（ConsistencyVerifier:124/186 按每表自身 qs）三处的 mapped-qs 契约直接矛盾；e2e 测试仅覆盖 rescale + null querySpace 形态（AbstractMigrationE2ETestCase:145 注释自证），relocate 形态零测试保护。
- **风险**: relocate+incremental 管道在真实跨库部署下不可用且失败点后置到 VERIFY（数天 FULL+增量工作量后才发现）；关闭 count 校验即静默切空表。另 verified 失败与 C7-002 相互作用：verify 失败无自环 → 只能 abort 全部作废。
- **建议**: relocate 路由统一改用 mapped querySpace——`ShardChangeEventRouter.route` 与 `TargetShardMapping.routeRow/routeShardValue` 的 relocate 分支改为 `new PhysicalTable(mapQuerySpace(sourceQs), baseTableName)`（`mapQuerySpace` 已存在于 L298-306）或直接返回 mapping.getTargetTables() 的对应条目；同根修复必须三点联动：(a) FULL 消费面改为经 `targetJdbcTemplate`（Orchestrator:456 verifier 同病：`new ConsistencyVerifier(jdbcTemplate, ...)` 应传 targetJdbcTemplate）；(b) 为 relocate 形态补一条 e2e（含非空 mapped qs）；(c) routeRow/route 返回值与 sink 构造取自同一 `getTargetTables()` 条目，消除 `""` 快捷路径。
- **失效面枚举**（F3 同族清扫，relocate querySpace 派生全站点）: ① `ShardChangeEventRouter.route:41-42`（本条主对象，增量 filter 恒 false）；② `TargetShardMapping.routeRow:236-237`（同根兄弟，FULL upsert 分组落 default qs）；③ `TargetShardMapping.routeShardValue:252-253`（同根兄弟，与 ① 等价路径）；④ `OrmMigrationOrchestrator.verifier():456-459`（模板面兄弟：verifier 用主 jdbcTemplate 而 sink/写入用 targetJdbcTemplate，5 参构造形态下 target 扫描打旧库模板——同根接线缺陷，随本条同修）；⑤ `ConsistencyVerifier.sumCounts/findPage`（消费方，自身按每表 qs 正确，无独立缺陷）；⑥ rescale 路径 `routeShardValue:259-261`（sel 来自 newStrategy.shardSelections，与 targets 同源，键位自洽，排除）。
- **信心水平**: 确定（两侧代码行级对照 + equals 语义；relocate 无测试覆盖为 ls src/test 可证事实）
- **误报排除**: 非「已声明边界」——类 javadoc（TargetShardMapping:41-42）明文"目标 querySpace 按 target-query-space-mapping 平移"，实现与之相反；非 C3-008 重复——C3-008 报 offset registry 的 connector **名**维度错位（探测 vs 注册），本条报链内 filter/写面的 **querySpace** 维度错位，站点、修复点、触发场景（relocate vs rescale）均不同；非第 1/2 轮 FullCopyBatchJob 阴性结论翻案——第 2 轮 ① 线只核了异步/并发面，路由键位面本轮首查。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（与 C3-008 非同根论证经复核**成立**：名维度 vs querySpace 维度、站点/场景/修复点互异、互不闭合——保留双条、统一修复方案不并案）。复核：route:41-42 / routeRow:236-237 / routeShardValue:252-253 三处 `""` 硬编码、`PhysicalTable.equals` 全字段等值恒 false、verifier 主模板接线（Orchestrator:456-459）、e2e 仅 rescale+空 qs（AbstractMigrationE2ETestCase.rescaleConfig 注释自证）全部成立；**复核补充**——多源 qs relocate 下 FULL 后果加重：(a,tbl) 与 (b,tbl) 两源行经 routeRow("") 全部合并写主模板 default 表，构成跨源数据合并，不止「同值幂等重写」。

### [C3-010] lag 门禁 ledger 观测的 querySpace 取自编排状态行空间而非 sink 实际提交空间：非缺省 querySpace 部署下观测量报错或退化为对无关表的比对

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:54-55,219-222`（探测构造，`stateQuerySpace` 注入）；`IncrementalLagProbe.java:39-44,92-103`（观测执行）；`OrmMigrationOrchestrator.java:495-521`（sink 侧以 `target.getQuerySpace()` 提交 ledger）；`JdbcTwoPhaseCommitSink.java:673`（`openConnection(querySpace)` 证据锚点）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:221-222 —— ledger 观测空间 = 状态行空间（缺省 ""）
  IncrementalLagProbe probe = new IncrementalLagProbe(targetJdbcTemplate, stateQuerySpace,
          ledgerTableName);
  // IncrementalLagProbe.java:93 —— COUNT 落在 ledgerQuerySpace（= stateQuerySpace）
  String sql = "SELECT COUNT(*) FROM " + ledgerTableName;
  // OrmMigrationOrchestrator.java:513 —— sink 的 ledger 提交空间 = 目标物理表 qs（另侧事实）
  .querySpace(target.getQuerySpace())
  ```
- **严重程度**: P2（条件结构性：缺省 `""=部署形态两侧对齐无恙；命名 querySpace 部署（rescale 新策略分片带 qs / relocate mapped qs）下 m6 第二观测量失效，loud 模式居多、silent 模式需同库存在同名表）
- **并发场景**: runVerify 调用线程（COUNT/MAX 观测）vs 链作业 sink 提交线程（INSERT ledger）；错位是静态空间不一致，不依赖交错
- **触发时序**: T1：作业目标分片带非缺省 querySpace（如 rescale 新策略 shardSelections = `(ds0,tbl_0)..(ds1,tbl_1)`）→ sink 经 `openConnection("ds0")` 把 ledger 守卫行写入 ds0 库的 `stream_epoch_ledger_v2`（`initializeLedgerTable` 每目标 qs 各建一张）。T2：`runVerify` 构造 probe 用 `stateQuerySpace`（4 参构造恒 `""`，Orchestrator:76）→ `COUNT(*)`/`MAX(epoch_id)` 在 **default qs** 执行。交叠后果分两型：(a) default 库无该表 → SQL 异常裸抛出 runVerify（不经 gateFailed、不落 verifyResult，异常形态未在编排器 catch 内）；(b) default 库恰有同名表（同库还承载其他 nop-stream jdbc-2pc 作业——`stream_epoch_ledger_v2` 为全平台共享表名）→ probe 对**无关表**做前后比对，恒静 → ledger 观测量退化为常量，lag=0 门禁第二观测量形同虚设（第一观测量 offset 的错位已由 C3-008 立案）。
- **现状**: `stateQuerySpace` 的语义是「编排状态行落点」（L54 javadoc），被复用作 ledger 观测空间属借用错位；sink 侧每目标 qs 一张物理 ledger 表（表名同、库不同），probe 单一 qs 的观测结构无法覆盖多 qs 目标形态。
- **风险**: 命名 qs 部署（分库分片的标准形态）下 VERIFY 门禁要么反复异常失败（叠加 C7-002 卡死），要么少一个观测量放行；与 C7-008 的 namespace 缺失正交——加 `WHERE namespace=?` 修不了空间错位，两修必须同方案设计。
- **建议**: probe 的 ledger 观测按 `mapping.getTargetTables()` 的**去重目标 querySpace 集**逐空间观测（每 qs 一组 COUNT/MAX，任一变化即 gateFailed），构造入口改为传 `List<String> ledgerQuerySpaces`；`stateQuerySpace` 严格保留给 MigrationJobStateStore；观测 SQL 异常包为 `gateFailed`（带 qs 参数）避免裸异常绕过门禁语义。
- **失效面枚举**（F3 同族清扫，ledger 观测链全站点）: ① `runVerify:221-222` 构造（本条主对象，qs 派生错位）；② `IncrementalLagProbe.countLedger:92-97`/`maxEpoch:99-103`（执行方，随 ① 修闭合；其无 namespace 过滤缺陷已由 C7-008 立案，不重复）；③ `IncrementalLagProbe.snapshot:71-76`（offset 支路，C3-008 已立案）；④ sink 侧 `JdbcTwoPhaseCommitSinkBuilderHolder.build:509-521`（写侧，qs=目标表 qs，正确侧，作为修复基准）；⑤ `ConsistencyVerifier.sumCounts`（同 qs 路由原语但按每表自身 qs，正确，排除）。
- **信心水平**: 确定（两侧 qs 派生代码对照；silent 型后果依赖 default 库存在同名表，属条件分支，已在严重度中计价）
- **误报排除**: 非 C7-008 重复——C7-008 报同一张表内的 namespace 未过滤，本条报观测落在**另一个物理库**的表（qs 维度），修复方案不同且必须协同设计；非「配置未声明」豁免——`IncrementalLagProbe` 构造签名接受任意 qs，编排器传值即契约点，relocate/命名 qs rescale 均为模块声明场景。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（关联 C7-008 namespace 面——正交双面、协同设计，非同案合并）。复核：stateQuerySpace 借用错位（Orchestrator:221-222 vs :513）、loud 型裸异常绕过 gateFailed（awaitLagZero 在 runVerify try/catch 之外，L223 vs L232-245）、silent 型无关表比对，均成立；条件性 P2 计价合理（缺省 ""=单 ds 部署两侧对齐无恙）。

## 扩展

### 扩展 C3-008（键派生修复的语义边界收敛）

- 失效面⑤确认为死代码：`MultiSinkTopologyProbe.fanoutEnabled` 生产调用点为零（grep 全仓），`startFallbackChains:130-132` 仅 log probe 结果——C3-008 修复只需覆盖 fallback 命名与探测侧，fan-out 接线时需回归复核本条。
- 第三层派生惰性后缀：注册侧实际键经 `DebeziumCdcSourceFunction.resolveConnectorName` → `instanceName(base, subtaskIndex, totalParallelism)`（`DebeziumSourceSupport.java:77-79`，parallelism>1 时追加 `-subtaskIndex`）。migration 链恒单并行度，该层当前惰性；统一键派生修复若在探测侧**重新实现** `targets.size()==1 ? base : base+"-"+i` 规则而非经 manager 暴露 `connectorNames()` 单一事实源，未来启用 B 层并行时键位将再次错位。
- null 名分支：JSON parse 路径强制 `connector-name` 非空（MigrationJobConfig:143-145），但 `Builder.incremental(null)` + `build()` 无校验可产出 null 名 → probe 走 `"no-incremental"` 常量支路。统一派生修复应把 null 视为 ERR_MIG_CONFIG_INVALID，不得静默跳过 offset 观测。
- 与 C7-001 的落地顺序约束具体化：修复后 probe 对真实名 `forConnector(base-i)`，在链尚未 `initializeState`（启动间隙/重试 `clearConnector` 清理窗）时 `computeIfAbsent` 建空条目 → 空指纹。「空条目」从此有三种成因（未启动/健康空闲/已死）——存活轮询必须按「空条目 + 线程非存活 = gateFailed」设计，且 probe 读侧应避免 `computeIfAbsent` 建条目（CL-010 增长源），否则键修对而空条目歧义依旧。

### 扩展 C7-001（死链盲视 → 新增空条目歧义面）

C3-008 修复落地后，失效面 (a)-(f) 追加 **(g)**：`forConnector(分片名)` 的空 registry 条目成为新的不可区分状态（见扩展 C3-008 第 4 点）。修复顺序约束不变（先键后存活），并新增一条：存活轮询的判定输入必须包含「registry 条目存在性」而非仅 `thread.isAlive()`，否则启动间隙窗口（线程存活但条目未建）与死亡窗口（条目存在但线程死）仍各有一个盲区。

## 回归面提示

（C3-008「统一键派生」修复会改变行为的站点清单，不立案为缺陷）

| # | 站点 | 行为变化 |
|---|---|---|
| 1 | `IncrementalLagProbe.snapshot:72` 的 `"no-incremental"` 支路 | null 名从「跳过 offset 观测」变为「派生缺省名 `mig-<jobId>` 或显式失败」 |
| 2 | `NopStreamOffsetBackingStore.forConnector:85` computeIfAbsent | 探测读侧从「对误建空条目泄漏」变为「对真实名建条目」——需配套只读访问或 clearConnector 清理（CL-010 增长源闭合点） |
| 3 | `TestIncrementalLagProbe` / `TestMigrationPipelineE2E` | 现有测试锚定 M==1 裸 base 名假设；M>1 聚合指纹与空条目语义需新增测试 |
| 4 | `DebeziumSourceSupport.instanceName` 并行度后缀层 | 修复若绕过该层重派生，为未来 fanout/B 层并行埋二次错位——应经 `IncrementalJobManager.connectorNames()` 单一事实源 |
| 5 | `C7-001` 存活轮询（待实现） | 判定输入须含 registry 条目存在性（扩展 C7-001 面 g），先键后存活顺序不可颠倒 |
| 6 | `MigrationJobConfig.Builder:404-408` | `incremental(connectorName)` 缺 null/空校验——统一派生修复应顺带在 build() 补 ERR_MIG_CONFIG_INVALID |

## 维度集外线索（overlay）

（本轮无。C3-009 失效面④的 verifier 模板错位属同根接线缺陷，已并入 C3-009 失效面枚举随其修复，不单开 CL。）

## 继续判定

**不输出终止信号，需第 4 轮**。依据：本轮新增 1 条 P1（C3-009，relocate 声明场景路由键位结构性失效）+ 1 条 P2（C3-010），按「新增含 P0/P1 → 继续」触发。第 4 轮建议范围（定向、非全维重开）：C3-009 修复落地后的 relocate 全链复核（route/routeRow/verifier 模板三点联动 + 新增 relocate e2e 的断言面），以及 C3-010×C7-008 协同方案（多 qs 逐空间观测 + namespace 过滤）落地后的门禁行为复核。其余 C3 面（PubSub、lettuce 异步链、rpc/tdengine 桥接、probe 内部并发）经三轮扫描无新 P0/P1 实证，**维持收敛**。

## 复核结论（WI 2.5 C3 独立复核）

本文件 2 条条目（C3-009/C3-010）逐条回码复核：**保留 2（P1×1、P2×1，均维持原严重度），驳回 0，待定 0**；现状均为新发现。C3-009 与 C3-008 的非同根论证经代码级对照裁定成立（名维度 vs querySpace 维度，站点/场景/修复点互异，任一修复不闭合另一条），保留双条、修复方案统一设计不并案。扩展节（C3-008 语义边界收敛、C7-001 空条目歧义面）与回归面提示表经复核无事实错误，维持原判。判定表与最终保留清单集中见 C3-async-chains.md「维度复核结论」节。
