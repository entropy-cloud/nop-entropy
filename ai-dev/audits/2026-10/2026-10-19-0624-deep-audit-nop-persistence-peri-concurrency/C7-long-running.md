# C7 长时运行稳定性 — nop-persistence-peri（WI 2.5）

- 审计维度：C7（长时运行稳定性：单调增长 / 挂死可检测性 / 重试收敛 / 自愈与 fencing / 时钟与期限 / 热路径外慢性病）
- 审计形式：单批次盲审初审（未读 ai-dev/audits|plans|bugs|lessons|backlog，未查 git 修复历史）
- 基线：`_tmp/cra-baseline-nop-persistence-peri.txt` C7a/C7b/C7c 节（方法论文 F1–F11 + 共享前缀 + 附录 A 13 字段格式）

## 审计范围与口径

单批次覆盖全部 10 个源码根，与基线 C7 节圈定一致并做人工补扫：

| ROOT | 规模（基线口径） | C7 审计面 |
|---|---|---|
| `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java` | 7f/1,832 | 窄（启动期单次升级执行，重入语义见 CL-022；`DataBaseUpgrader` 无进度/无中途断点） |
| `nop-persistence/nop-db-migration/src/main/java`（`_` 前缀跳过） | 72f/3,877 手写 | 窄（`MigrationEngine.migrate` 单次执行 + 历史表重入；失败行不毒化版本，重入语义基本健康，见 CL-022） |
| `nop-persistence/nop-orm-migration/src/main/java` | 20f/3,462 | **主战场**：五状态机 + 增量链长驻线程 + lag 探测 + 状态行存储，全部 20 文件逐一读过 |
| `nop-persistence/nop-cdc/nop-cdc-core/src/main/java` | 1f/11 | **零审计面登记**：仅 `CdcConstants.java`（11 行常量），无任何可审计执行面 |
| `nop-persistence/nop-nosql/nop-nosql-core/src/main/java` | 19f/1,083 | 接口 + `NosqlCache`/`RedisConfig`/`RedisScripts`，已读 |
| `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java` | 18f/2,377 | 连接 provider / pub-sub 消息面 / lock/queue/session/rate-limiter 全读 |
| `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java` | 6f/726 | 近零（无线程/无静态可变缓存/无循环等待；`TdMessageService` 为显式占位 stub） |
| `nop-persistence/nop-orm-geo/src/main/java` | 12f/700 | 近零（方言 type handler / 初始化器，无状态） |
| `nop-persistence/nop-orm-pdm/src/main/java` | 9f/1,654 | 近零（PDM 模型解析，纯函数式解析面） |
| `nop-persistence/nop-orm-rpc/src/main/java` | 1f/913 | 近零（无状态 persist driver） |

口径裁定：

1. **降档口径执行**：本维度核心面即运行期/长程行为，**所有状态残留与长时运行形态发现一律未做"启动期单线程"降档**；dbtool/db-migration 仅按任务书以"重入语义"窄口径审视。
2. **规模包络**：nop-orm-migration 面向大表扩容/机房搬迁（设计明示场景），verify/full 阶段的规模判断按该声明场景评估，未与普通 CRUD 包络混排。
3. 跨根引用（`OrmMigrationWriteGate` 位于 nop-orm、`RoundRobinSupplier` 位于 nop-kernel、`JdbcTwoPhaseCommitSink` 位于 nop-stream-connector-jdbc）：只作为本根内使用方式的证据锚点，不作为独立发现对象。
4. **终止信号**：本维度存在 5 条 P1 发现，**不输出终止信号**（P0 为 0 条）。

### 单调增长源清单（C7 步骤 1 盘点输出）

| # | 增长源 | 位置 | 有界性 | 对应发现 |
|---|---|---|---|---|
| G1 | `stream_epoch_ledger_v2` append-only 提交账本（每 epoch×subtask 一行，无 DELETE/TTL，作业 DONE/ABORT 后残留） | `OrmMigrationOrchestrator.java:87` 硬编码共享表；sink 侧仅 INSERT | 无界（随 checkpoint 间隔×时长×分片数单调增长） | C7-012 |
| G2 | `NosqlCache` 写入键（`put`/`computeIfAbsent` 无 TTL；`expireAfterWrite` 配置全模块未被引用） | `NosqlCache.java:90-106` | 无界（键基数即内存） | C7-005 |
| G3 | `LettuceSessionStore.setAsync` 的 hmset→pexpire 两步窗口残留（无 TTL 会话 hash 永久残留） | `LettuceSessionStore.java:62-75` | 逐故障累积 | C7-011 |
| G4 | `NopStreamOffsetBackingStore` 静态 REGISTRY（按 connector 名累积，仅链首跑 clear 自身） | 经 `IncrementalJobManager.startChain` 间接使用 | 弱有界（#作业×#分片），跨作业不清 | 记录于 C7-001 失效面 |
| G5 | `ConsistencyVerifier.scanHashes` 堆内 pk→hash 双全量 Map（瞬时但 O(表行数)） | `ConsistencyVerifier.java:131-151` | 瞬时无界 | C7-007 |
| G6 | `nop_db_migration_history` / `nop_orm_migration_job` 状态行 | `MigrationHistoryManager.java:96-138`（UPDATE 覆盖式）`MigrationJobStateStore.java:81-118` | 有界（#迁移/#作业），健康 | 无发现 |

### 挂死点清单（C7 步骤 2 盘点输出）

| # | 可能永久阻塞/静默死亡点 | 健康检测可见性 | 对应发现 |
|---|---|---|---|
| H1 | 增量 CDC 链作业线程死亡（daemon 线程，`failure` 引用无人轮询） | **不可见**：状态行停在 INCREMENTAL，status biz 面无链健康字段，lag 探测反将冻结位点判为追平 | C7-001 |
| H2 | FULL 阶段 nop-batch 复制任务长驻同步执行 | 状态行只记 taskName，无进度/心跳回写；卡死不可区分慢 | C7-006/C7-007 |
| H3 | `IncrementalLagProbe.sleep` 静默窗等待 | 有界（quiesceSeconds），但墙钟回拨可任意延长 VERIFY 冻结 | C7-008 |
| H4 | PubSub 消费者回调在 lettuce IO 线程执行，阻塞型消费者冻结全部主题分发 | 无看门狗；可自死锁（同步 Redis 回调同 event loop） | C7-010 |
| H5 | lettuce sync 调用等待 | 默认 `soTimeout=2000ms` 有界；仅当显式配置 `<=0` 时 `buildTimeoutOptions` 返回 null → 无命令超时（已声明配置面，不立发现） | 记录 |

## 发现

### [C7-001] 增量链作业死亡不可见，lag=0 探测把死链冻结位点误判为"已追平"

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:176-188,230-233`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:210-227`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalLagProbe.java:52-90`
- **证据片段**:
  ```java
  // IncrementalJobManager.java:176-183 —— 失败仅写入 AtomicReference
  Thread thread = new Thread(() -> {
      try {
          env.execute(jobName);
      } catch (Throwable e) {
          running.failure.set(e);
      }
  }, jobName);
  // IncrementalJobManager.java:230-233 —— 注释声称"编排器轮询到 failure 即阶段失败"，但全模块无调用者
  /** 任务是否已观测到失败（fail-fast：编排器轮询到 failure 即阶段失败）。 */
  public Throwable getFailure() {
      return failure.get();
  }
  ```
- **严重程度**: P1
- **并发场景**: 链作业 daemon 线程（`env.execute` 长驻）与调用编排 API 的 HTTP/运维线程；lag 探测读取 `NopStreamOffsetBackingStore` 静态 registry 快照
- **触发时序**: T1（第 N 天）：某分片链线程因源库网络中断/debezium 异常退出，`failure.set(e)` 后再无人读；该链 offset 停止前进，目标表停止写入。T2：运维关写门后调 `runVerify` → `awaitLagZero` 对冻结的 offset 指纹与静止的 ledger 计数做前后比对（`IncrementalLagProbe.java:52-57`：`before.equals(after)`）→ 死链的"完全静止"恰好满足 lag=0 门禁。交叠后果：VERIFY 门禁在目标端缺数的状态下放行；若 `verify-count` 关闭或抽样 checksum 未命中缺失行（`ConsistencyVerifier` 抽样分母 >1 时按 `pkHash % denominator` 抽样），`verifyResult=passed` → confirmSwitch → DONE 成为不可回滚点，目标分片永久缺行。
- **现状**: 链作业失败被写入 `RunningChainJob.failure` 后没有任何消费者：`getFailure()` 在整个模块（含编排器、status biz 面测试外的主代码）零调用（grep 证实仅定义处 1 hit）；`OrmMigrationJobStatusBizModel.getJob` 只透出状态行 9 个字段，不含任何链作业健康信息；`resume()` 对 INCREMENTAL 相位只 `return jobId` 不做链活性检查。
- **风险**: 长迁移（声明场景为天/周级）期间 CDC 链死亡是常态事件而非尾部事件；死亡→假追平→切换的链条把一次可恢复故障转化为不可逆数据缺失。这同时命中 F5（挂死生产者对健康检测不可见）与 F9（异步链异常被静默吞掉导致状态不一致）。
- **建议**: (1) `runVerify` 门禁前置增加链活性检查：遍历 `incrementalManager` 的 `runningJobs`，任一 `getFailure() != null` 或线程 `!isAlive()` 即 `gateFailed`；(2) `OrmMigrationJobStatusBizModel` 透出每链 `thread.isAlive()`/`failure` 摘要；(3) 复用平台设施 `io.nop.commons.concurrent.thread.ThreadHelper`/NamedThreadFactory 统一链线程命名与 UncaughtExceptionHandler，替代裸 `new Thread`。
- **失效面枚举**: (a) 回退形态每分片链作业 ×M（`startFallbackChains`，M 个同构线程）——全部同病；(b) `fanoutEnabled` 的多 sink 形态（`MultiSinkTopologyProbe` opt-in 路径）——同一失败观测缺口；(c) `resume()` INCREMENTAL 分支——不检查链活性；(d) status biz 查询面——不暴露健康；(e) `stopAll`（`IncrementalJobManager.java:201-206`）——仅 cancel 不观测失败。逐一核查，无一有轮询/上报路径。
- **信心水平**: 确定（`getFailure` 零调用者为可 grep 事实；lag 假阳性是 `before.equals(after)` 对冻结快照的直接代码后果）
- **误报排除**: 非原语使用误报：缺陷不在 Thread/AtomicReference 本身，而在"声明了 fail-fast 观测契约（javadoc 原文）却无任何轮询者"的复合语义断裂；`verify-count` 默认开启可部分兜底，但 `verify-count`/`full-checksum` 均为配置项，门禁链路本身未闭合。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同案：本单元 C3-002 同缺陷双维度登记，合并计一次工单——C3 复核已同裁定；另有 C1-CL-006、C8-CL-023 线索分账。`failure.set`/getFailure 全仓零调用方（grep 复现，含 test）/lag=0 对冻结快照假阳性/status biz 仅 9 个状态行字段/resume 对 INCREMENTAL 只 return jobId——逐项回码复核成立。修复顺序先修 C3-008 键派生再加链存活轮询（round2 扩展裁定确认））

### [C7-002] 五状态机无自环相位：INCREMENTAL/VERIFY/SWITCH 中途失败即卡死，长迁移工作量一次性报废

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/MigrationPhase.java:36-43`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:151-170,185-204,210-251,257-274,417-424`
- **证据片段**:
  ```java
  // MigrationPhase.java:36-43 —— 允许边集中无任何自环（X→X）
  private static final Map<MigrationPhase, Set<MigrationPhase>> ALLOWED = Map.of(
          PREPARE, Set.of(FULL, ABORT),
          FULL, Set.of(INCREMENTAL, VERIFY, ABORT),
          INCREMENTAL, Set.of(VERIFY, ABORT),
          VERIFY, Set.of(SWITCH, ABORT),
          SWITCH, Set.of(DONE),
          DONE, Set.of(),
          ABORT, Set.of());
  // OrmMigrationOrchestrator.java:160-166 —— resume 对 INCREMENTAL/VERIFY/SWITCH 不做任何接管
  case INCREMENTAL:
  case VERIFY:
  case SWITCH:
  case DONE:
  case ABORT:
      // 非终态阶段恢复 = 状态行已在，门禁与执行动作由对应编排 API 续推
      return jobId;
  ```
- **严重程度**: P1
- **并发场景**: 单机编排线程 + 运维编排 API 调用；不依赖并发交错，是恢复路径自身的结构性缺口（失败→重入被 transition 检查拒绝）
- **触发时序**: 三条具体链——(1) T1 JVM 在 INCREMENTAL 期崩溃 → T2 重启后 `resume(jobId)` 命中 `case INCREMENTAL: return jobId`（不重建 manager、不重启链）→ 运维唯一推进入口 `runIncremental` 要求 `INCREMENTAL→INCREMENTAL` 迁移，被 `checkTransition` 拒绝（`ERR_MIG_PHASE_TRANSITION_INVALID`）→ 链无法重启，作业永久卡 INCREMENTAL。(2) T1 `runVerify` 校验失配或抛异常，`verifyResult=failed` 落库 → T2 重跑 `runVerify` 要求 `VERIFY→VERIFY`，被拒 → 唯一出路 `abort`（终态），数天全量+增量工作作废；verify 中途抛 Error（如全量 checksum OOM，`catch (Exception e)` 不捕获）时 `verifyResult` 保持 null，`confirmSwitch` 的 `!"passed".equals(...)` 同样永久阻断。(3) T1 `confirmSwitch` 已置 phase=SWITCH 后 `rebuildRouting` 失败（`rebuild-failed` 落库，`OrmMigrationOrchestrator.java:417-424` 注释明言"修复后 confirm 重试"）→ T2 重试 `confirmSwitch` 要求 `SWITCH→SWITCH`，被拒——javadoc 承诺的 fix-forward 重试路径同样走不通（`completeSwitch` 虽可调用但它只查 `phase==SWITCH` 不查 `routeRebuild`，等于绕过收敛门，见 C7-003 失效面）。
- **现状**: 恢复面只覆盖 PREPARE/FULL（`resume` 显式重跑），后三个非终态相位既无自环、也无独立重入 API、`resume` 也不接管。
- **风险**: 长迁移越到后期，一次瞬时故障（网络/校验抖动/OOM）的报废成本越高；被卡死的作业只能 DBA 手工改状态行（未在任何文档声明的带外操作）或 ABORT 重跑全流程。
- **建议**: (1) 为 INCREMENTAL/VERIFY 增加受控自环：`INCREMENTAL→INCREMENTAL`（重启链前先 `stopAll` 清理旧链）、`VERIFY→VERIFY`（重跑校验前重置 `verifyResult`），或提供显式 `retryVerify/restartIncremental` API 内部先 `checkTransition` 到自环；(2) `rebuildRouting` 失败后的重试改为允许 `SWITCH` 相位重入 `confirmSwitch`（先校验写门仍 closed）；(3) `completeSwitch` 增加 `routeRebuild` 收敛门校验，杜绝绕过。
- **失效面枚举**: PREPARE（`resume`→`executePrepare` 可重入，健康）；FULL（`resume`→`executeFull` 可重入 + batch 断点/upsert 幂等，健康）；INCREMENTAL（卡死，见时序 1）；VERIFY（失败后卡死，见时序 2）；SWITCH（`confirmSwitch` 不可重试，见时序 3；`completeSwitch` 可用但缺收敛门校验）；DONE/ABORT（终态无出边，声明设计）。7 相位逐一核查。
- **信心水平**: 确定（迁移合法性由 `ALLOWED` 静态表与两条调用链共同决定，无运行时旁路）
- **误报排除**: 不是"声明的设计边界"：javadoc 在 `rebuildRouting` 处明确承诺"fix-forward（修复后 confirm 重试）"，与 `ALLOWED` 表矛盾，属于实现与自身契约冲突，非有意只进不退。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（修复语义敏感项专项复核成立，见维度复核结论专节：ALLOWED 全表无自环逐行核实、resume 四非终态相位 return jobId、runIncremental/runVerify 重入均被 checkTransition 拒、rebuildRouting javadoc「fix-forward（修复后 confirm 重试）」与 SWITCH→SWITCH 拒绝的矛盾属实、completeSwitch 只查 phase 不查 routeRebuild 的绕过面属实。同案：C1-CL-007（INCREMENTAL 部分启动卡死面）、C1-CL-008（SWITCH fix-forward 不可达面）为本条两个具体面，母条目吸收合并修复）

### [C7-003] 状态行全列覆盖无守卫 + 编排 API 无单飞/fencing：并发交错导致状态行与实态背离、增量链双跑

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/MigrationJobStateStore.java:100-118`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:296-313,185-204,257-274`
- **证据片段**:
  ```java
  // MigrationJobStateStore.java:105-115 —— 全列覆盖，WHERE 仅 job_id，无 version/phase 乐观守卫
  .sql("UPDATE " + TABLE_NAME + " SET phase=").param(state.getPhase().name())
  .sql(",scenario=").param(state.getScenario())
  ...
  .sql(",update_time=").param(new Timestamp(state.getUpdateTimeMillis()))
  .sql(" WHERE job_id=").param(state.getJobId())
  // OrmMigrationOrchestrator.java:302-304 —— abort 直接读字段，非实例内启动的链无从停止
  IncrementalJobManager manager = this.incrementalManager;
  if (manager != null)
      manager.stopAll();
  ```
- **严重程度**: P1
- **并发场景**: 多个 HTTP/运维线程并发调用同一编排器 bean（NopIoC 单例、无并发守卫约定）；以及多节点各自持有编排器实例对同一状态行操作（无 owner/租约/fencing 列）
- **触发时序**: T1（confirmSwitch）：`requireState` 载入 phase=VERIFY 副本 A → 过门禁 → `setPhase(SWITCH)` + `update` 落库 → `flipShardConfig` 翻转 `nop.orm.shard` → `rebuildRouting`。T2（abort）：在 T1 flip 前后载入 phase=VERIFY 副本 B → `checkTransition(VERIFY→ABORT)` 基于过期副本通过 → `setPhase(ABORT)` + 全列 `update` 覆盖 → 且 `OrmMigrationWriteGate.setClosed(false)` 开门。交叠后果：shard 配置已翻新、写门被重新打开，而状态行=ABORT（其语义承诺"旧配置未动无损"）——状态行与实态背离，且开门后写流量按新路由（可能半重建）进入。第二链：T1/T2 并发调 `runIncremental`（双击/超时重试）→ 两者各自惰性 `incrementalManager()` 判空竞态（`OrmMigrationOrchestrator.java:444-454` 无同步）→ 各建 manager → 同名 connector 双链写同一目标表、交错提交 2PC epoch。
- **现状**: 7 个编排入口（start/resume/runFull/runIncremental/runVerify/confirmSwitch/completeSwitch/abort）全部依赖"载入内存副本→内存判定→全列覆盖写回"，无行版本、无 `WHERE phase=?` 条件更新、无分布式锁/租约；`loadAll()`（`MigrationJobStateStore.java:129-135`）只读不清理终态行。
- **风险**: 迁移控制面的判定依据（状态行）可被并发写回伪造，开关门与配置翻转的实态无从对账；多节点双跑时两套增量链同时消费源库 binlog，2PC ledger 同 namespace 交错，幂等守卫按复合 PK 去重虽防重复落库，但两链 offset 相互覆盖导致跳段。
- **建议**: (1) `update` 改条件更新 `WHERE job_id=? AND phase=?`（CAS 化阶段推进），受影响行数 0 即抛 `ERR_MIG_PHASE_TRANSITION_INVALID`；(2) 引入作业级互斥：复用 `io.nop.commons.concurrent.lock.IResourceLockManager`/`LocalFileLock`（单机）+ 状态行加 `owner_instance/lease_until` 列（多机 fencing，参照 commons 既有租约形态）；(3) `abort` 经 `incrementalManager()` getter 并在 stopAll 后校验链线程退出。
- **失效面枚举**: (a) `runVerify` vs `abort` 交错——见时序；(b) `confirmSwitch` vs `abort`——同型（flip 后被 abort 覆盖）；(c) `completeSwitch` vs `abort`——completeSwitch 只查内存相位，abort 后仍可能开门至 DONE；(d) `runIncremental` 双入——双链双跑；(e) `start` 同 jobId 双入——PK 冲突显式失败（健康）；(f) `resume` 与编排 API 并发——同 (a) 型；(g) 多节点同相位双 resume——无 fencing，同 (d)。逐一核查，仅 (e) 有守卫。
- **信心水平**: 确定（交错序列各步均有代码锚点，无假设性环节）
- **误报排除**: 非"低频管理操作粗粒度锁可接受"的豁免面：这里是锁与守卫**完全缺失**而非粒度问题，且后果是控制面状态伪造 + 数据面路由错乱，超出管理操作优雅性范畴。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（update 全列覆盖 WHERE 仅 job_id 无版本/相位谓词、abort 直读实例字段、惰性 getter 无同步均回码核实。同站点并集：C1-005（双启 face）/C1-006（状态行覆盖 face）/C1-009（abort stale-null face），合并计一次工单。WI 1.5 C7-001（WfTaskScanner fencing）与 WI 1.6 fencing/租约族为家族关联不并案（与 C1 复核裁定一致），跨进程租约/条件更新设施选型三案对齐）

### [C7-004] 写停止关翻转仅本 JVM 生效且无持久化回写：completeSwitch 后集群其余实例写冻结不可自愈

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:285,306-308`（调用方，本审计根）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmMigrationWriteGate.java:38-59`（执行面，证据锚点）
- **证据片段**:
  ```java
  // OrmMigrationWriteGate.java:38-40 / 57-59 —— 配置面读写都落在本地 JVM 的 AppConfig
  public static boolean isClosed() {
      return GATE_CLOSED.equalsIgnoreCase(CFG_ORM_MIGRATION_WRITE_GATE.get());
  }
  public static void setClosed(boolean closed) {
      AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_MIGRATION_WRITE_GATE,
              closed ? GATE_CLOSED : GATE_OPEN);
  }
  ```
- **严重程度**: P1
- **并发场景**: 多实例集群（设计自身声明该拓扑：`rebuildRouting` 的收敛门=「全部实例重建完成」、降级路径=滚动重启）；编排器所在 JVM 与其余业务实例
- **触发时序**: T1：运维按 `runVerify` 门禁提示在配置源（各节点共享的配置文件/配置中心下发的本地快照）设置 `nop.orm.migration.write-gate=closed`，全集群写冻结。T2：编排器所在节点 A 走完 SWITCH，`completeSwitch` 调 `setClosed(false)`——仅改写 A 的 JVM 内存配置。交叠后果：节点 B..N 的写门仍为 closed，全部业务 DML 持续抛 `ERR_ORM_MIGRATION_WRITE_GATE_CLOSED`；文档化恢复路径「滚动重启」（`routeRebuild=restart-required`，`OrmMigrationOrchestrator.java:413-416`）重启后从配置源重新加载 `closed`，写门重新关闭——冻结经重启依然不可消除，只能逐节点手工改配置。`abort` 路径（L306-308）同样只解本 JVM 冻结。
- **现状**: 关门（配置源、集群面）与开门（运行时 API、单 JVM 面）通道不对称，无集群传播、无配置源回写、无 TTL/租约自动开启。
- **风险**: 迁移"成功完成"后演变为全集群写故障，且自愈路径（重启）反向加固冻结；长时间无人察觉时业务侧表现为大面积写失败。写门若被当作纯本地开关使用（单机部署）则无此问题——但多实例正是设计声明的目标拓扑。
- **建议**: (1) `completeSwitch/abort` 的开门动作改为回写配置源（`IConfigProvider` 持久化面或专用开门登记表），使各实例经配置刷新收敛；(2) 或写门增加时间盒（`lease_until` 语义，超时自动 fail-open 并告警），杜绝永久冻结；(3) 文档化多实例下关门/开门的操作矩阵（哪些节点、何种顺序）。
- **失效面枚举**: (a) `completeSwitch`（`OrmMigrationOrchestrator.java:285`）——本 finding 主链；(b) `abort`（L307-308）——同型，单 JVM 开门；(c) `runVerify` 门禁检查 `isClosed()`（L215）——读本 JVM 配置，多实例下"门已关"判定对其他节点不成立（lag 门禁部分兜底）；(d) `OrmSessionImpl` 各检查点（flush/flushSave/flushUpdate/flushDelete/flushImmediately）——消费面，随所在 JVM 配置面各自为政。逐一核查，无一处具备集群语义。
- **信心水平**: 很可能（默认 `IConfigProvider` 为本地配置面，无内建跨实例传播；若宿主接入具备双向传播的配置中心且 `updateConfigValue` 实现回写源，则风险降级为重启回滚窗口）
- **误报排除**: 非平台基础设施（nop-kernel/nop-api-core）自身缺陷的重审：缺陷锚定在本模块调用方对单 JVM 原语做集群级假设的使用方式，属"使用方式"审计面。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（`setClosed`→AppConfig 进程内 updateConfigValue、原语 javadoc 自述「生效于本 JVM」、无持久化回写均回码核实；多实例为设计自身声明拓扑（rebuildRouting 收敛门=全部实例重建完成），单机部署不触发的条件面已在原信心水平「很可能」中如实标注，P1 维持。门可见性半边归 nop-core-framework-C1-007（2.2），本条为集群传播/无自愈面，与 C1-010 保持机制面正交不并案）

### [C7-005] NosqlCache 不落地 expireAfterWrite：put/computeIfAbsent 写入永不过期，缓存键无界累积

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java:51-58,63-65,90-106`；`nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/config/RedisConfig.java`（无涉）；对照 `nop-kernel` `CacheConfig.java:30-32`
- **证据片段**:
  ```java
  // NosqlCache.java:51-58 —— 只有读路径落地 expireAfterAccess
  public CompletionStage<Object> getAsync(@Nonnull String key) {
      Duration expireAfterAccess = cacheConfig.getExpireAfterAccess();
      if (expireAfterAccess == null)
          return ops.getAsync(key);
      return ops.getExAsync(key, expireAfterAccess.toMillis());
  }
  // NosqlCache.java:100-102 —— 写路径无任何 TTL
  public void put(String key, Object value) {
      ops.put(key, value);   // → LettuceMessageService.put = SET（无过期）
  }
  ```
- **严重程度**: P1
- **并发场景**: 所有以 `ICache<String,Object>` + `CacheConfig` 契约使用 `NosqlCache` 的调用方（多请求线程并发写）；失效面为 Redis 服务端的键空间
- **触发时序**: T1：应用以 `CacheConfig.maxSize(N).expireAfterWrite(10min)` 构建 `NosqlCache`（`getConfig()` 透出该配置，暗示已生效）。T2：业务线程持续 `computeIfAbsent(key)` / `put(key,...)` ——`LettuceMessageService.computeIfAbsent`（`LettuceMessageService.java:62-70`）与 `put`（L86-88）均为无 TTL 的 `SET`。交叠后果：`expireAfterWrite` 全模块零引用（grep 证实），写入键永不过期也不淘汰；`maxSize` 同样无执行点。键基数随业务维度（每用户/每会话/每实体键）单调增长，Redis 内存与扫描成本持续上升，且陈旧缓存被永久命中（正确性劣化）。
- **现状**: 该类唯一的过期执行点是读路径的 `getEx`（`expireAfterAccess`），配置面承诺的 `expireAfterWrite` 语义静默缺失；`clearForTenant`（L145-147）为空方法，租户清理请求静默无效；`stats()` 恒返回空。
- **风险**: 对照 F4（无界账本只增不删）：这是把"内存缓存"实现到 Redis 上时丢失淘汰语义的典型无界增长源；长期运行实例的 Redis 内存单调上升，且无指标（stats 恒空）可观测。
- **建议**: (1) 写路径落地 TTL：`put/putAll/computeIfAbsent*/putIfAbsent*` 在 `expireAfterWrite != null` 时改用 `psetex/set(px)`；(2) 未配置任何过期时至少在 `put` 上打 warn-once 或抛配置错误（fail-fast，不做静默降级——与模块 D1 哲学一致）；(3) `clearForTenant` 要么实现（按前缀 scan 删除）要么在接口层声明不支持并显式失败。
- **失效面枚举**: 同族写入口逐一核查：`put`(L100)、`putAll`(L105)、`putIfAbsent`(L117 附近，经 `ops.putIfAbsent`=SETNX 无 TTL)、`computeIfAbsent`(L90)、`computeIfAbsentAsync`(L63)、`putAsync`(L166 附近)、`putAllAsync`、`putIfAbsentAsync`、`computeIfAbsentAsync`——全部无 TTL，全部同病；`removeIfMatch/remove/removeAll/clear` 为删除面（健康）；读路径 `getAsync/getExAsync` 为唯一 TTL 触点。
- **信心水平**: 确定（`expireAfterWrite` 在 nop-nosql 全模块零引用为 grep 事实）
- **误报排除**: 非"有界声明存在的缓存"误报类：恰因 `CacheConfig` 携带 maxSize/expire 声明而实现未执行，属于守卫缺失而非已声明边界。
- **复核状态**: 未复核
- 复核状态: 保留，降档 P2(P2)｜新发现（复核新证：NosqlCache 全仓生产零实例化——`new NosqlCache` 仅测试命中，唯一 javadoc 级引用方 RedisMfaChallengeStore 刻意绕行该类走 putExAsync 自带 TTL；按本单元 C1-012/C7-015「零生产调用方条件化降档」同款口径 P1→P2。expireAfterWrite 全模块零引用、写路径九入口无 TTL、clearForTenant 空方法、stats 恒空均回码核实。邻面分账：C1-012（computeIfAbsent 原子性）、C1-CL-014（GETEX 读副作用）；check/nosql-cdc.md 旧 P1 NPE 面已修（现 L54-57 null fallback），与本条写路径面无涉）

### [C7-006] abort 中止面不完整：FULL 期不停批量复制、换实例 abort 不停增量链（半个中止）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:296-313,341-354`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/FullCopyBatchJob.java:98-111`
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:301-305 —— 只停增量链，无 FULL 批任务句柄
  if (config.isIncrementalEnabled()) {
      IncrementalJobManager manager = this.incrementalManager;
      if (manager != null)
          manager.stopAll();
  }
  // FullCopyBatchJob.java:103-104 —— 批任务同步执行到底，无取消入口
  task.execute(context);
  ```
- **严重程度**: P2
- **并发场景**: 运维线程调 `abort` 与 batch 工作线程池（`throttle-concurrency`）中的复制任务、以及（换实例场景）上一实例遗留的链作业 daemon 线程
- **触发时序**: 链 A（FULL 期 abort）：T1 `runFull` 启动百万行级复制 → T2 `abort(jobId)` 落 ABORT 状态行返回 → 复制任务继续跑完（小时级），目标表持续被写入，"旧配置未动无损"期间目标端并非静止。链 B（换实例 abort）：T1 上一编排器实例 `runIncremental` 已置 `this.incrementalManager` 并启动链线程 → 宿主重建编排器 bean（配置刷新/重装配）→ T2 新实例 `abort`：`this.incrementalManager == null` → `stopAll` 不执行 → 旧链继续消费 CDC 并写目标。交叠后果：状态行=ABORT（终态）而数据面仍在运动；若随后以新 jobId 重开作业，新旧两套写入交叠。
- **现状**: abort 的清理面只覆盖"同实例 + 已建 manager + 增量链"一个象限；batch 任务按设计"天然出现在 nop-batch 任务管理面"可单独取消，但编排器未联动（也无 taskName→取消的引用留存，仅状态行字符串）。
- **风险**: 中止语义的"半个成功"（F2 族）：ABORT 承诺的静止态不成立，基于它做的重开作业/对账都会被残留写入污染；资源上残留任务占满复制带宽。
- **建议**: (1) `executeFull` 持有 `IBatchTask`/取消句柄并注册到状态行可追溯的 registry，`abort` 联动 cancel；(2) `abort` 改经 `incrementalManager()` 并额外清理同 jobKey 的全局链注册（或链注册表按 jobId 静态索引，跨实例可见）；(3) ABORT 完成语义增加"数据面静止确认"步骤（复用 `IncrementalLagProbe` 的 ledger 静止观测）。
- **失效面枚举**: PREPARE/FULL/INCREMENTAL/VERIFY 四个可 abort 相位逐一核查：FULL——批任务不停（本条）；INCREMENTAL——同实例 manager 在场可停（健康）、换实例不可停（本条）；PREPARE/VERIFY——无长驻数据面（健康）；SWITCH/DONE 不可 abort（`ALLOWED` 表）。批量复制与增量链两类执行体各一处缺口。
- **信心水平**: 确定
- **误报排除**: 非"管理面可人工兜底即豁免"：abort API 自身以状态行承诺中止语义，语义与行为不一致属缺陷而非运维便利问题。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（abort 只停增量链且经实例字段直读、FullCopyBatchJob task.execute 同步到底且编排器不留 IBatchTask 句柄均回码核实；批任务可经 nop-batch 管理面人工取消为部分缓解，P2 恰当。关联：C6-002/C3-003 为成功路径不停链（已并 P0 工单），本条为 abort 路径同根不同触发面，修复设计统一收敛不并案；换实例 face 与 C1-009（同实例 stale-null）互补）

### [C7-007] VERIFY 全量 checksum 堆内驻留 O(表行数) + offset 分页二次方 IO，长迁移终局阶段无进度可观测

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/ConsistencyVerifier.java:131-189`
- **证据片段**:
  ```java
  // ConsistencyVerifier.java:132-133 —— 源、目标各建一张全量 pk→hash 常驻 Map
  Map<String, String> sourceHashes = scanHashes(mapping.getSourceTables(), denominator, report);
  Map<String, String> targetHashes = scanHashes(mapping.getTargetTables(), denominator, report);
  TreeSet<String> allKeys = new TreeSet<>();          // L135 —— 再复制一份全量键集
  // ConsistencyVerifier.java:183-188 —— ORDER BY + offset 跳页，每页全表重排
  String sql = "SELECT * FROM " + escape(table) + " ORDER BY " + order;
  return jdbcTemplate.findPage(..., offset, CHECKSUM_BATCH);
  ```
- **严重程度**: P2
- **并发场景**: VERIFY 阶段单线程校验（无并发缺陷），风险在长时运行的资源收敛性与可观测性；与 C7-002 叠加（verify 期 OOM → 状态卡死）
- **触发时序**: T1：`verifyFullChecksum=true` 对千万/亿级行表（该工具声明场景）执行校验 → scanHashes 为源+目标各物化一张 `pk→64字符hash` HashMap，再加 `allKeys` TreeSet，堆需求 ≈ 2×行数×(pk+hash+开销)，百 GB 级表直接 OOM。T2（未 OOM 路径）：offset 分页每页 `ORDER BY` 全表排序 + 前缀跳过，N 行表总 IO O(N²/batch)，verify 阶段演变为天级黑盒——状态行停在 VERIFY，无进度字段、无已扫描行数上报。交叠后果：校验失败（OOM 为 Error 不入 `verifyResult`）后作业卡死（C7-002 时序 2）。
- **现状**: 抽样模式（denominator>1）在存入前过滤，内存可控；全量模式无流式归并、无外部排序、无分块比对；`MAX_MISMATCH_DETAILS=10` 只限明细条数不限内存驻留。
- **风险**: 长迁移最末环节成为最脆弱环节：校验器自身把可校验规模上限压到堆尺寸以下，与"大表搬迁"产品定位相悖；期间卡死不可观测。
- **建议**: (1) 改双游标主键序归并流式比对（两侧各按 pk 序分页游标推进，O(1) 堆内状态），或复用 nop-batch 分块框架分块比对；(2) 分页改 keyset（`WHERE (pk > ?) ORDER BY pk LIMIT ?`）消除二次方跳页；(3) 校验进度落状态行/日志（每 N 页 `LOG.info` 已扫描行数 + 估算完成度）。
- **失效面枚举**: `verify()` 三个层级逐一核查：count 校验（`sumCounts`，聚合下推 DB，健康）；抽样 checksum（内存 O(抽样数)，可控）；全量 checksum（本条，O(行数)）。`scanHashes` 内部两表循环（源/目标、rescale 多物理表聚合）全部同病。
- **信心水平**: 确定（代码结构事实；OOM 概率取决于行数×行宽，按声明场景为必然而非尾部）
- **误报排除**: 非"超出声明包络"类：大表正是该模块声明场景；亦非理论竞态，而是资源消耗的结构性上界问题。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同案：本单元 C8-002 同站点同证据同修复（verifyChecksum/scanHashes/findPage 双侧全量 Map + TreeSet + offset 深分页逐行核实），双维度重复登记合并计一次工单；抽样前置过滤有界、MAX_MISMATCH_DETAILS 只限明细均核实。CL-025（FullCopyBatchJob 深分页）为同模式族关联，修复统一 keyset/归并方案）

### [C7-008] lag 静默窗口依赖墙钟 + 共享 epoch ledger 无 namespace 过滤：时钟回拨延长冻结、跨作业提交互相污染判定

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalLagProbe.java:52-69,92-103`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:87,219-226`
- **证据片段**:
  ```java
  // IncrementalLagProbe.java:59-68 —— deadline 用墙钟计算
  private void sleep(long quiesceSeconds) {
      long deadline = CoreMetrics.currentTimeMillis() + quiesceSeconds * 1000L;
      while (CoreMetrics.currentTimeMillis() < deadline) { ... }
  }
  // IncrementalLagProbe.java:92-95 —— ledger 计数无 namespace/作业过滤（全表 COUNT）
  private long countLedger() {
      String sql = "SELECT COUNT(*) FROM " + ledgerTableName;
  ```
- **严重程度**: P2
- **并发场景**: runVerify 调用线程 vs NTP 校时（墙钟跳变）；同库其他迁移作业/流作业的 2PC sink 提交线程（共享 `stream_epoch_ledger_v2`，`OrmMigrationOrchestrator.java:87` 对所有作业硬编码同名表）
- **触发时序**: 链 A（时钟）：T1 进入 `awaitLagZero` 计算 deadline=now+quiesce → T2 NTP 步进回拨 Δ → 循环条件 `currentTimeMillis() < deadline` 持续成立，VERIFY 门禁冻结期被拉长 Δ（写门关闭期业务停写随之延长）；前跳则静默窗口缩水，弱化"两观测点静止"的判定强度。链 B（跨作业）：T1 作业 A 关写门进入 lag 观测 → T2 作业 B（另一实体迁移）或无关流作业向同一 ledger 表提交新 epoch 行 → A 的 `Snapshot.ledgerRows` 前后不等 → `awaitLagZero=false`，A 的门禁因**与己无关的写入**反复失败，只能反复重试（而重试路径见 C7-002 的卡相位缺陷）。
- **现状**: 窗口等待无单调时钟（平台无 monotonic 计量时惯例应使用 `System.nanoTime()`）；ledger 观测粒度为全表而非本作业 namespace（sink 侧 v2 ledger 本有 namespace 列用于多作业隔离提交守卫，探测侧未使用）。
- **风险**: 多作业并行的长期运维态下，VERIFY 门禁产生系统性假阴性；时钟异常（大步进校时）直接放大停写窗口。
- **建议**: (1) `sleep` 改 `System.nanoTime()` 相对期限；(2) ledger 观测按本作业 sink namespace 过滤（`WHERE namespace=?`，与 `JdbcTwoPhaseCommitSink` 的 ledgerNamespace 对齐）或以 `MAX(update_time/epoch)` 本作业水位替代全表 COUNT。
- **失效面枚举**: 观测量逐一核查：offset 指纹（按 connectorName 定位，天然作业隔离，健康）；`countLedger`（全表，本条）；`maxEpoch`（全表 MAX，同病）；`sleep`（墙钟，本条）。
- **信心水平**: 确定（代码事实；触发依赖时钟事件/并行作业，属条件必现）
- **误报排除**: 墙钟使用不是"低频路径合理粗粒度"豁免对象——它直接处于停写门禁的关键路径；跨作业干扰非"声明边界"（v2 ledger 的 namespace 列即为多作业共存而设，探测面未对齐属实现缺口）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（sleep 墙钟 deadline、countLedger/maxEpoch 全表无 namespace 过滤、v2 ledger `sink_namespace` 列存在而探测侧未用均回码核实。复核修正：失效面枚举中「offset 指纹（按 connectorName 定位，天然作业隔离，健康）」被 C3-008（P1，M>1 键错位恒空）推翻——该面不健康，但不影响本条墙钟与 namespace 两个独立面成立。ledger qs 错位面归 C3-010（正交，协同设计））

### [C7-009] LettucePubSubService stop→start 重启后消息面永久死亡：closed 连接与订阅表未清理

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:107-117,96-105`
- **证据片段**:
  ```java
  // LettucePubSubService.java:111-117 —— 关连接但不置空字段、不清订阅表
  @Override
  protected void doStop() {
      StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
      if (conn != null) {
          conn.close();
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 生命周期管理线程（`LifeCycleSupport.stop/start`，`allowRestart` 默认 true，stop 后状态回 CREATED 可再次 start）与后续调用 `subscribe/sendAsync` 的业务线程
- **触发时序**: T1：宿主对 bean 执行 stop（配置刷新/优雅停机半程/生命周期管理面）→ pub/sub 连接关闭，但 `pubSubConnection` 仍指向 closed 连接、`subscriptions` 仍保留全部 `SubscriptionEntry`。T2：再次 `start()`（支持的合法路径）→ 业务调 `subscribe` → `getOrCreateConnection()` 双检锁命中非空字段直接返回 closed 连接 → `conn.sync().subscribe(topic)` 抛连接关闭异常；已存订阅的投递通道同样死亡。交叠后果：重启后消息服务呈"bean ACTIVE 但全部订阅死"的静默失效，且异常只在新增订阅时才暴露。
- **现状**: `doStop` 无状态复位；对比同模块 `LettuceRedisConnectionProvider.doStop`（shutdown 后 `clientResources = null`）具备清理对称性，本类缺失。
- **风险**: 依赖 pub/sub 的推送/失效通知在重启后静默断流（F2/F8 清理不对称族）；故障呈间歇性（仅重启后），长时运行中难以归因。
- **建议**: `doStop` 中置空 `pubSubConnection` 并 `subscriptions.clear()`（或逐 entry `removeListener`），使 restart 后走重建路径；同时在 `getOrCreateConnection` 复用前校验 `conn.isOpen()`。
- **失效面枚举**: 同族生命周期方法核查：`LettuceRedisConnectionProvider.doStart/doStop`（对称，健康）；`LettucePubSubService.doStart`（空实现，无对偶清理，本条）；`LettuceMessageService.getMessageService` 惰性创建的 `pubSubService.start()`（首次启动路径健康，restart 路径即本条）。
- **信心水平**: 确定
- **误报排除**: 非"stop 后进程即退出"的场景豁免：`LifeCycleSupport` 显式支持 restart（stop→CREATED→start），该路径是平台声明能力。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（doStop 关连接不置空字段不清订阅表、对照 provider doStop 的清理对称缺口、LifeCycleSupport allowRestart 默认 true 均回码核实。PubSub 生命周期族裁定见维度复核结论专节：本条为 stop→start 重启面，与 C1-011/C3-004/C6-004 订阅竞态面、C7-014 运行期重连面触发路径/修复点互异，非同案；同位点 C1-CL-013/C3-CL-016 线索分账，本条为维度发现主登记）

### [C7-010] PubSub 消费者回调在 lettuce IO 线程内联执行：阻塞型消费者冻结全部主题分发并可自死锁

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:145-161`
- **证据片段**:
  ```java
  // LettucePubSubService.java:152-159 —— listener 在 Redis 命令到达的 netty 线程内同步执行
  for (IMessageConsumer consumer : listeners.keySet()) {
      try {
          consumer.onMessage(channel, message, context);
      } catch (Exception e) {
          consumer.onConsumeFailure(new io.nop.api.core.message.ConsumeFailure(
                  channel, channel, message, 0, e));
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: lettuce event loop 线程（netty IO）执行用户回调；同一 `ClientResources` 的 IO 线程组同时服务连接池中的普通连接
- **触发时序**: T1：IO 线程分发 topic-A 消息进入 `consumer.onMessage`，消费者实现内部做同步 Redis 调用（`sync()`/`FutureHelper.syncGet`）或慢处理。T2：该同步调用落在连接池某连接上，其响应需由同一 event loop 线程处理（`DefaultClientResources` IO 线程组按连接分配，存在同线程概率）→ IO 线程等待自己才能完成的响应 → 自死锁；未死锁的慢路径下，同一 pub/sub 连接承载的全部主题（含其他业务订阅）分发全部停摆，且无看门狗/stall 检测可见（F5）。
- **现状**: 回调无执行器隔离、无超时包裹；`onConsumeFailure` 同样在 IO 线程执行；单连接多主题共享放大阻塞半径（L147 注释自证"one shared connection carries all subscriptions"）。
- **风险**: 长时运行中一个劣化消费者逐步拖垮全部 pub/sub 消费，且表现为"无错误、无产出"的静默停滞；死锁线程占死 IO 线程影响同组其他连接。
- **建议**: 分发改经独立消费线程池（复用 `io.nop.commons.concurrent.executor.IThreadPoolExecutor` + `QueueOverflowPolicy` 声明过载策略），或至少在文档与 `MessageSubscribeOptions` 校验层 warn-once 声明"回调内禁止阻塞"。
- **失效面枚举**: 全部订阅入口共享同一分发路径：`subscribe` 新建 entry（本条）、既有 entry 多消费者循环（本条）、`resume` 恢复的 listener（本条）——同族全病；`sendAsync`（发布面，异步，健康）。
- **信心水平**: 确定（内联执行是代码事实；自死锁分支依赖 event loop 分配概率，标注为条件分支）
- **误报排除**: 非"用户回调行为不当"的责任转移：平台消息面把用户代码调度到共享 IO 线程是框架侧调度决策，对照 F5（健康检测不可见）属审计范围。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同案：本单元 C3-005(P2)/C8-001(P1)/C1-CL-011 同站点投递模型面多维度登记，合并计一次工单；严重度分歧 P1/P2 留 7.1 裁定——C3 复核已同裁定。IO 线程内联回调、onConsumeFailure 同线程、单连接多主题放大半径（L147 注释自证）均回码核实；自死锁分支维持条件分支标注）

### [C7-011] LettuceSessionStore.setAsync 的 hmset→pexpire 两步非原子：故障窗口产生永不过期的会话残留

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceSessionStore.java:62-75,81-93`
- **证据片段**:
  ```java
  // LettuceSessionStore.java:64-70 —— 两步写，中间断链即遗留无 TTL hash
  return async().hmset(key, data)
          .thenCompose(v -> {
              if (ttlMs > 0) {
                  return async().pexpire(key, ttlMs);
              }
              return CompletableFuture.completedFuture(true);
          })
  // L81-86 对照：setFieldAsync 反而用了 Lua exists-guard 防止"复活无 TTL 键"——同族不对称
  ```
- **严重程度**: P2
- **并发场景**: 会话写入线程 vs Redis 连接断连/进程退出窗口；类内两条写路径（setAsync 与 setFieldAsync）行为不一致
- **触发时序**: T1：`setAsync(sid, data, ttl)` 的 `hmset` 成功落库 → T2：连接断开/进程被 kill，`pexpire` 未执行 → 该会话键以无 TTL hash 形式永久残留。长时运行中每次此类窗口累积一个永生键；无 TTL 会话还被 `getFieldAsync` 的 `hget` 正常读到，形成"活会话"假象（对照 F4 与 F11 的持久态断裂混合形态）。
- **现状**: 同类已为 `setFieldAsync` 引入 Lua 脚本（`SESSION_SET_FIELD`）防"复活无 TTL 键"，说明作者已认知该危害，但 `setAsync` 主路径未做同等原子化（F3 同族兄弟漏修的实例内版本）。
- **风险**: 会话键空间单调增长（每次故障 +N），Redis 内存缓慢上升且无自愈；残留会话可能被安全面当作有效会话。
- **建议**: `setAsync` 合一为 Lua（HMSET+PEXPIRE 同脚本原子执行），复用既有 `RedisScripts` 装载模式；或 hmset 前先 `SET` 占位带 TTL、失败路径补偿 DEL。
- **失效面枚举**: 会话写入口逐一核查：`setAsync`（两步非原子，本条）；`setFieldAsync`（Lua 守卫，健康）；`touchAsync`（单命令 pexpire，健康）；`removeAsync`（单命令 del，健康）。四入口仅一处缺口，但为主写入路径。
- **信心水平**: 确定
- **误报排除**: 非"网络故障属下游不可控"豁免：同模块已示范原子化方案（setFieldAsync），缺口是选择而非能力。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜已知未修复（同案：本单元 C1-013/C3-007 同缺陷第三维度登记 + 历史前案 2026-05-20-nosql **D03-02**（HIGH，当时建议补文档或 Lua，未采纳）——四处合并计一次工单。hmset→pexpire 两步、setFieldAsync 的 Lua exists-guard 动机注释（「would resurrect it as a TTL-less hash」）均回码核实，F3 实例内同族漏修定性成立；check/nosql-cdc.md 的 set 合并语义 P2 与本条相邻分账）

### [C7-012] 共享 epoch ledger 无任何保留策略：append-only 单调增长且 DONE 后残留，探测成本随之上升

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:87,509-521`；对照 `nop-stream/nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java`（仅 INSERT 守卫语义，grep 无 DELETE/清理路径）
- **证据片段**:
  ```java
  // OrmMigrationOrchestrator.java:87 —— 所有作业硬编码共享同一 ledger 表
  this.ledgerTableName = "stream_epoch_ledger_v2";
  // OrmMigrationOrchestrator.java:519 —— 每次构建 sink 即自治建表/续写，无保留语义
  sink.initializeLedgerTable();
  ```
- **严重程度**: P3
- **并发场景**: 增量链 checkpoint 提交线程持续追加；`IncrementalLagProbe` 在 VERIFY 期对该表做全表 COUNT/MAX（见 C7-008）
- **触发时序**: T1：INCREMENTAL 期链作业每 checkpoint（缺省 30s，`OrmMigrationOrchestrator.java:72`）每分片提交一行 ledger 守卫行 → 天/周级迁移累计 10^4–10^5 行/分片。T2：作业 DONE/ABORT 后无人清理（模块与 sink 均无 DELETE/TTL/归档路径），后续作业的 lag 探测 COUNT 扫描成本与表尺寸同步上升。交叠后果：与 G1 清单一致的无界增长，长期多迁移的宿主库累积冗余守卫行。
- **现状**: ledger 的复合 PK 幂等守卫只需要近期 epoch 去重语义；代码中不存在按 epoch 水位/作业终态的修剪逻辑。
- **风险**: 目标分片库内的慢性膨胀；与 C7-008 叠加使 VERIFY 门禁越来越慢。量级温和（相对数据表），故 P3。
- **建议**: 作业 DONE 后按 namespace+epoch 水位归档/删除（编排器 `completeSwitch` 收尾步骤）；或 sink 侧提供 `retentionEpochs` 旋钮滚动清理。
- **失效面枚举**: ledger 写入方核查：FULL 阶段 batch upsert（不写 ledger，无关）；INCREMENTAL 每分片 sink（本条 ×M 分片）；fan-out opt-in 形态（同构，同病）；`initializeLedgerTable` 建表路径（幂等，健康）。清理路径：无（零覆盖）。
- **信心水平**: 确定（append-only 为两侧 grep 事实）
- **误报排除**: 非 F4 豁免类"有界账本"：无清理/TTL/归档任一路径；也未以"平台基础设施不重审"豁免——发现锚定在本模块对其的选用与收尾缺失。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜已知未修复（sink 面跨单元同案：WI 1.2 C3-002/**MA-05**「stream_epoch_ledger_v2 只增不删」P1 open——同一 sink 同一 ledger 表，P1/P3 差异系场景包络（stream 长驻作业 vs 迁移作业有界次数），修复主责随 MA-05 台账工单；编排器 DONE 后按 namespace 收尾清理为本轮模块侧新伴生面。append-only 无 DELETE 为双侧 grep 事实复核成立；checkpointIntervalMs=30_000（:72）核实）

## 维度集外线索（overlay C2/C4/C5/C9）

（编号续接 nop-persistence-peri-CL-020；CL-001…019 已占用）

### [CL-020] 连接池配置热更新缩容立即关闭在用共享连接（C2/C4）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:92-100`；`nop-kernel .../commons/concurrent/RoundRobinSupplier.java:43-79`
- `refreshConfig` 直接 `connectionSupplier.resize(n)`；RoundRobinSupplier 缩容分支把裁撤连接在 `finally` 中立即 `safeClose`，而这些连接是 round-robin 直接派发给全部调用方的**共享**连接（非 checkout/return 池）。T1 业务线程 `get()` 拿到 index≥新 size 的连接执行命令 → T2 配置刷新 shrink → 连接被关闭 → 在途命令以连接异常失败。缓解项：lettuce autoReconnect 会重连，错误为瞬态；但契约上"配置热更新不应打断在途命令"。建议缩容走延迟关闭（grace 引用计数）或 resize 仅标记、由获取路径惰性淘汰。同类调用面：本类 `refreshConfig` 为唯一 resize 触点（grep 证实）。

### [CL-021] PubSub subscribe/cancel 无互斥：取消与重订阅交叠产生永不投递的"活订阅"（C2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:72-94,189-200`
- `subscribe` 以 service 为 monitor 同步，而 `PubSubSubscription.cancel/suspend` 无锁。T1 cancel：`entry.removeListener` → `isEmpty()` 为真 → 尚未 unsubscribe；T2 subscribe（同 topic）：`subscriptions.get` 命中未删 entry → `addListener` 成功返回订阅句柄；T1 继续 `unsubscribe` + `subscriptions.remove` → T2 持有看似正常的 `IMessageSubscription`，实际 Redis 侧已退订且 entry 已从 map 摘除——消息静默丢失、cancel 后再无任何错误暴露。`resume` 与并发 cancel 同型。建议 subscribe/cancel/suspend/resume 统一收拢到 service monitor 或 entry 级锁内做"检查-动作"原子化。

### [CL-022] 启动期 schema 升级无多节点互斥：同时启动的多实例并发 diff-and-apply（C2/C9）

- **文件**: `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/initialize/DataBaseUpgradeInitializer.java:33-37`；`nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java/io/nop/dbtool/core/DataBaseUpgrader.java:55-82`；`nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:123-191`
- `auto-upgrade-database=true` 时每个实例在 `@PostConstruct` 直接 `upgrade()`；`MigrationEngine.migrate` 同样无任何锁（本地 history manager、无 DB 顾问锁/锁表）。T1 节点 A 与 T2 节点 B 同时滚动发布启动 → 双方各自读旧 schema、生成同一段 DDL 并发执行 → 一方 CREATE/ALTER 报"已存在"启动失败进入 crash-loop；`InsertDataExecutor` 类数据变更则双份插入污染种子数据。db-migration 的历史表保证"执行后不重跑"（`getExecutedVersions` 过滤 `success=TRUE`，失败行不毒化、`recordMigration` UPDATE 覆盖式防非原子 DELETE+INSERT——该重入语义健康），但**执行中**无互斥。建议 migrate/upgrade 外层加 DB 顾问锁（`GET_LOCK`/`pg_advisory_lock`）或锁表租约，复用 `io.nop.commons.concurrent.lock` 语义落到 DB 面。

—— 本维度无其他集外线索。

## 维度复核结论（WI 2.5 C7 独立复核，2026-10-18）

> 复核人：C7 维度独立复核员（轮外复核，未参与初审与三轮深挖）。逐条回到 live code 验证证据 file:line 真实性与触发时序可具体化性；现状对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.4 台账逐条比对）、同单元 C1/C3/C6/C8 批次（跨维度同案查重）与历史审计（`2026-05-20-deep-audit-nop-nosql-post-plan23`、`check/nosql-cdc.md`）。本节不修改原发现条目；round2/3 条目（C7-013/014/015）状态行写在各 round 文件，判定表集中于此。

### 逐条判定表（C7-001…015）

| ID | 标题（缩写） | 原判 | 复核判定 | 现状标注 | 同案指针 |
|---|---|---|---|---|---|
| C7-001 | 链作业死亡不可见，lag=0 误判追平 | P1 | 保留 P1 | 新发现 | C3-002（同案合并）、C1-CL-006、C8-CL-023 |
| C7-002 | 五状态机无自环，中途失败卡死 | P1 | 保留 P1 | 新发现 | C1-CL-007/CL-008（两个具体面，母条目吸收） |
| C7-003 | 状态行无守卫覆盖 + 编排无单飞/fencing | P1 | 保留 P1 | 新发现 | C1-005/006/009（同站点并集）；1.5 C7-001、1.6 fencing 族（家族关联） |
| C7-004 | 写门翻转仅本 JVM，集群冻结不可自愈 | P1 | 保留 P1 | 新发现 | 门可见性半边归 2.2 C1-007；C1-010 正交 |
| C7-005 | NosqlCache 写路径无 TTL 无淘汰 | P1 | **降档 P2** | 新发现 | 邻面 C1-012 / C1-CL-014 分账不并案 |
| C7-006 | abort 中止面不完整（半个中止） | P2 | 保留 P2 | 新发现 | 关联 C6-002/C3-003（同根不同触发路径） |
| C7-007 | VERIFY 全量 checksum OOM + O(N²) 深分页 | P2 | 保留 P2 | 新发现 | C8-002（同案合并）、CL-025（同模式族） |
| C7-008 | lag 静默窗墙钟 + ledger 观测无 namespace 过滤 | P2 | 保留 P2 | 新发现 | C3-010（正交协同）；C3-008 推翻其「offset 指纹健康」子断言 |
| C7-009 | PubSub stop→start 后消息面永久死亡 | P2 | 保留 P2 | 新发现 | C1-CL-013/C3-CL-016（同位点线索分账，本条主登记） |
| C7-010 | 消费回调 IO 线程内联执行 | P2 | 保留 P2 | 新发现 | C3-005(P2)/C8-001(P1)/C1-CL-011（同案，严重度留 7.1） |
| C7-011 | setAsync hmset→pexpire 两步无 TTL 残留 | P2 | 保留 P2 | 已知未修复 | C1-013/C3-007/2026-05-20 D03-02（同案合并） |
| C7-012 | 共享 epoch ledger 无保留策略 | P3 | 保留 P3 | 已知未修复 | WI 1.2 C3-002/MA-05（跨单元同案，sink 面主登记 P1） |
| C7-013 | 限流令牌桶以客户端墙钟跨节点共享 | P2 | 保留 P2 | 新发现 | WI 2.3 C8-005（同族不同设施）；2.1 CL-034（watch 关联） |
| C7-014 | PubSub 断线重连丢订阅无重订阅无检测 | P1 | 保留 P1 | 新发现 | PubSub 族独立面（见族裁定节） |
| C7-015 | LettuceLock 无续期/fencing，isHeld 本地假阳性 | P2 | 保留 P2 | 新发现 | WI 1.5 C7-001 / 1.6 C3-003（fencing/租约族关联） |

### C7-002 修复语义敏感项专项复核（逐环回码）

1. **ALLOWED 全表核实**（`MigrationPhase.java:36-43`）：七相位出边逐一核对——PREPARE→{FULL,ABORT}、FULL→{INCREMENTAL,VERIFY,ABORT}、INCREMENTAL→{VERIFY,ABORT}、VERIFY→{SWITCH,ABORT}、SWITCH→{DONE}、DONE/ABORT 无出边。**无任何自环（X→X），与原文引用逐字一致**；另核实 SWITCH→ABORT 非法（confirmSwitch 之后 abort 不可达，与 C6 复核对 C6-002 的补强结论一致）。
2. **重入被拒逐路径核实**：`resume`（`OrmMigrationOrchestrator.java:160-166`）对 INCREMENTAL/VERIFY/SWITCH 仅 `return jobId`；`runIncremental`（:191）/`runVerify`（:212）均以当前相位 `checkTransition` 目标相位，重入即 `ERR_MIG_PHASE_TRANSITION_INVALID`。JVM 在 INCREMENTAL 期崩溃→重启后链无法重启（时序 1 成立）；`verifyResult=failed` 落库后重跑 `runVerify` 被拒（时序 2 成立；`awaitLagZero` 失败发生在落 VERIFY 之前，该子路径可重试——原发现未混淆，无需修正）。
3. **javadoc 承诺与实现矛盾属实（本条核心）**：`rebuildRouting` 失败分支（:417-424）注释明言「fix-forward（修复后 confirm 重试）或回翻」，但 `confirmSwitch` 先置 phase=SWITCH 落库（:267-268）再 flip/rebuild——失败行即 SWITCH+rebuild-failed，重试 `confirmSwitch` 在 :266 命中 SWITCH→SWITCH 拒绝，**承诺的重试路径在状态机上不可达**。且 `completeSwitch`（:279-290）只查 `phase==SWITCH` 不查 `routeRebuild`，带失败路由开门的绕过面真实存在。矛盾定性为「实现与自身契约冲突」成立，非已声明边界。
4. **同案吸收**：C1-CL-007（INCREMENTAL 部分启动卡死）、C1-CL-008（SWITCH fix-forward 不可达）为本条时序 1/时序 3 的两个具体面（线索分账，不入发现数）；C3-001 复核修正中「同 jobId 顺序重试被相位机阻断」的同一事实互证。修复为单一受控自环/显式 retry API 工单，吸收上述两面。

### 同单元 PubSub 生命周期族裁定（本单元内同站点多面，逐一裁定）

`LettucePubSubService` 单文件上六个登记（C1-011、C3-004、C6-004、C3-005、C7-009、C7-014 + 本维度 CL-021）按「根因 + 失效层」裁定为**四个独立失效面，非同案**（触发路径与修复点互异），但共享同一连接拓扑，修复设计须统一收敛（round3 回归面提示已登记联动项）：

| 失效面 | 登记（判级） | 触发路径 | 修复点 |
|---|---|---|---|
| ① 订阅管理竞态 | C1-011(P1)/C3-004(P1)/C6-004(P1 已知未修复)+CL-021 | 并发 subscribe×cancel/suspend/resume | 锁收敛 + cancelled 重入守卫（C6-004 主登记，含 check/nosql-cdc.md 2026-08-19 前案） |
| ② stop→start 重启残留 | **C7-009(P2)** + C1-CL-013/C3-CL-016 | 生命周期 stop→start | doStop 状态复位 + isOpen 校验 |
| ③ 运行期断线重连丢订阅 | **C7-014(P1)** | 网络闪断/failover（无需人为动作） | 重连监听重发 SUBSCRIBE + NUMSUB 对账 |
| ④ 投递执行模型 | C3-005(P2)/C8-001(P1)/**C7-010(P2)** + C1-CL-011 | 慢消费者/阻塞回调 | 有界 executor 分发（严重度分歧留 7.1） |

裁定理由：①②③④的触发条件互斥（并发管理操作 / 显式生命周期操作 / 无人为动作网络事件 / 持续负载），任一修复不消除其余（C7-014 修复甚至引入与①的竞次面——round3 回归面 #1/#2 已登记），按「根因 + 失效层」口径各自成立；②③两面在初审误报排除中已自证区分，复核独立确认 `check/nosql-cdc.md` 无重连条目（③无历史前案）、doStop 面此前仅以线索形态存在（②的维度发现主登记为 C7-009）。

### 跨单元/跨维度查重结论

- **cra-index P1 台账逐条比对：零跨单元同案重复立项**（C7-012 的 sink 面除外，见下）。WI 1.5 C7-001（WfTaskScanner fencing）、WI 1.6 fencing/租约族（C3-003 等）、WI 1.7 C7-001（任务行死区）与 C7-003/C7-015 为**家族关联不并案**（不同模块不同代码；与 C1 复核对 C1-003/005/006 的裁定一致），Phase 8 修复批次对齐跨进程租约/fencing 设施选型。
- **C7-012 ↔ WI 1.2 C3-002（MA-05，P1 open）**：**跨单元同案**——同一 `JdbcTwoPhaseCommitSink`、同一 `stream_epoch_ledger_v2` 表。MA-05 主登记（P1，stream 长驻作业场景），C7-012 保留 P3 作为迁移场景伴生面（作业有界故增长温和），不重复立项；编排器 DONE 后 namespace 清理随 MA-05 修复同批。
- **C7-013 ↔ WI 2.3 C8-005（网关自研 TokenBucket 墙钟惰性回填，P2 复核保留）**：同族不同设施（Redis 共享桶 vs 进程内桶），不并案；两案修复均收敛到「服务端/单调时钟」方向，批次对齐。2.1 CL-034（TimeOut 墙钟 API 预警，watch）关联登记。
- **本单元内跨维度同案 6 组**（C7 视角）：C7-001=C3-002（+C1-CL-006/C8-CL-023 线索）、C7-003⊃C1-005/006/009、C7-007=C8-002、C7-010=C3-005=C8-001、C7-011=C1-013=C3-007、C7-009≈C1-CL-013/C3-CL-016（线索分账）。合并裁定时各计一次工单，C7 侧不撤条，以判定表指针供 7.1 汇总。
- **本维度集外线索裁定**：CL-020（refreshConfig 缩容热关共享连接）= C6-005 同位点（C6 已保留 P2、设施主责 WI 2.1 C1-001，线索分账）；CL-021（subscribe/cancel 无互斥）= 上表失效面①，归 C6-004/C1-011/C3-004 工单；CL-022（启动期 schema 升级无多节点互斥）= C1-003 同位点（C1 已保留 P1，C2/C9 视角线索分账）。三条均不独立立案。

### 高风险条目（P1×5）子项复核说明

1. **C7-001**：证据链全部回码成立——`failure.set`（IncrementalJobManager.java:180）、`getFailure` 全仓（main+test）零调用方（grep 复现，C3-002 复核同结论）、javadoc fail-fast 契约（:230-233）无编排代码兑现、status biz 仅 9 个状态行字段、`awaitLagZero` 的 `before.equals(after)` 对死链冻结快照恒真。子断言「verify-count 默认开启可部分兜底」表述成立（两者均为配置项，门禁链路未闭合）；round2 扩展（C3-008 键派生错位使 lag 门禁对健康链也恒真）复核确认，**修复顺序约束（先 C3-008 后 C7-001）维持**。
2. **C7-002**：见专项复核节，javadoc-实现矛盾与 completeSwitch 绕过面均属实；唯一修正级细节为「awaitLagZero 门禁失败发生在落 VERIFY 之前可重试」（原发现未混淆此子路径），不影响判级。
3. **C7-003**：两条触发链逐环回码成立——abort×confirmSwitch 交错在 `update` 全列覆盖（MigrationJobStateStore.java:100-118，WHERE 仅 job_id）+ 双 checkTransition 均基于各自内存副本的结构下可重放（C1-006 复核已独立重演同交错）；runIncremental 双入经惰性 `incrementalManager()`（:444-454 无同步）双建 manager 成立。多节点形态（无 owner/租约/fencing 列）为 DDL 结构事实。
4. **C7-004**：`setClosed` → `AppConfig.getConfigProvider().updateConfigValue` 为进程内裸写（无持久化回写；2.2 C1-007 已证该通道可见性面另案）；`OrmMigrationWriteGate` javadoc 自述「原子生效于本 JVM」——单 JVM 作用域是原语已声明边界，缺陷锚定编排器把单 JVM 原语用于集群级开门（使用方式面，误报排除成立）。多实例为设计自身声明拓扑（rebuildRouting 收敛门=全部实例重建完成），P1 维持；单机部署不触发的条件面已在原信心水平「很可能」中如实标注。
5. **C7-014**：模块侧三处无恢复钩子（getOrCreateConnection/doStart/doStop + createPubSubConnection）回码成立；lettuce `autoReconnect(true)`（provider :247/:261）仅恢复传输、订阅重发属应用层责任——与依赖版本 lettuce-core 7.5.2 一致，采信初审字节码核对（notifyChannelActive 仅复位 subscribeWritten）；`check/nosql-cdc.md` 与 cra-index 无重连面登记，新发现成立。三层不可见（传输重连成功/客户端订阅表原样/publish 永远成功）构成 F5 完整形态，长时运行必然触发面，P1 维持；修复回归面以 round3 五项登记为准。

### 现状标注汇总与同案命中清单

- **判定统计**：保留 15（维持原严重度 14、降级 1：C7-005 P1→P2）/ 驳回 0 / 待定 0。
- **最终严重度分布**：P0×0；P1×5（C7-001/002/003/004/014）；P2×9（C7-005/006/007/008/009/010/011/013/015）；P3×1（C7-012）。
- **现状标注分布**：新发现×13；已知未修复×2（C7-011 ← 2026-05-20 D03-02；C7-012 ← WI 1.2 MA-05 跨单元）；已修复×0。
- **同案命中清单**：本单元内 5 组（C7-001=C3-002、C7-007=C8-002、C7-010=C3-005/C8-001、C7-011=C1-013/C3-007、C7-003⊃C1-005/006/009）；线索吸收 2（C7-002←C1-CL-007/008、C7-009←C1-CL-013/C3-CL-016）；跨单元同案 1（C7-012 sink 面=WI 1.2 MA-05）；同族关联不并案 4（C7-003/C7-015↔1.5 C7-001+1.6 租约族、C7-013↔2.3 C8-005、C7-005↔C1-012/CL-014 邻面、CL-020/022↔C6-005/C1-003）。

## 最终保留项

| ID | 标题 | 最终严重度 | 现状 | 同案/修复通道指针 |
|---|---|---|---|---|
| C7-001 | 链作业死亡不可见，lag=0 把死链冻结位点误判为已追平 | P1 | 新发现 | 与 C3-002 合并计一次工单；先修 C3-008 键派生 |
| C7-002 | 五状态机无自环：INCREMENTAL/VERIFY/SWITCH 中途失败即卡死（javadoc fix-forward 承诺不可达） | P1 | 新发现 | 吸收 C1-CL-007/008 两面；受控自环 + completeSwitch 收敛门校验 |
| C7-003 | 状态行全列覆盖无守卫 + 编排 API 无单飞/fencing | P1 | 新发现 | 并入 C1-005/006/009 同站点工单；fencing 设施与 1.5/1.6 族对齐 |
| C7-004 | 写门开门仅本 JVM 生效：completeSwitch 后集群其余实例写冻结不可自愈 | P1 | 新发现 | 配置源回写/租约 fail-open；与 2.2 C1-007 门可见性半边分案 |
| C7-014 | PubSub 断线重连后订阅全部丢失且无重订阅无检测 | P1 | 新发现 | 重连监听重订阅 + NUMSUB 对账；round3 回归面 5 项为设计输入 |
| C7-005 | NosqlCache 写路径不落地 expireAfterWrite，键无界累积 | P2 | 新发现 | 零生产实例化降档；写路径 TTL + clearForTenant 实现或显式拒绝 |
| C7-006 | abort 中止面不完整：FULL 不停批复制、换实例不停链 | P2 | 新发现 | 与 C6-002/C3-003 停链修复统一设计 |
| C7-007 | VERIFY 全量 checksum 堆内 O(行数) + offset 二次方 IO | P2 | 新发现 | 与 C8-002 合并计一次工单；归并流式 + keyset 分页 |
| C7-008 | lag 静默窗墙钟 + 共享 ledger 观测无 namespace 过滤 | P2 | 新发现 | nanoTime 相对期限 + 按本作业 namespace 过滤（与 C3-010 协同） |
| C7-009 | PubSub stop→start 重启后消息面永久死亡 | P2 | 新发现 | doStop 状态复位 + isOpen 校验（主登记，吸收 CL-013/C3-CL-016 线索面） |
| C7-010 | PubSub 消费回调 IO 线程内联：阻塞型消费者冻结全部分发并可自死锁 | P2 | 新发现 | 与 C3-005/C8-001 合并；严重度分歧留 7.1 |
| C7-011 | setAsync hmset→pexpire 两步非原子：无 TTL 会话残留 | P2 | 已知未修复 | 与 C1-013/C3-007 合并；Lua 原子化（D03-02 同案收口） |
| C7-013 | 限流令牌桶以客户端墙钟跨节点共享：回拨冻结/超前饥饿 | P2 | 新发现 | 服务端 TIME 或固定窗口；与 2.3 C8-005 批次对齐 |
| C7-015 | LettuceLock 无续期/fencing，isHeld 本地假阳性 | P2 | 新发现 | 零生产调用方维持 P2；isHeld 语义修正 + 可选续期 + fencing token |
| C7-012 | 共享 epoch ledger 无保留策略：append-only 且 DONE 后残留 | P3 | 已知未修复 | sink 面主责随 WI 1.2 MA-05（P1）工单；编排器 namespace 收尾随同批 |
