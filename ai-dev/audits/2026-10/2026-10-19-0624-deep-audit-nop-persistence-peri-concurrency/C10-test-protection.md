# C10 测试保护力 — nop-persistence-peri（WI 2.5）

- 审计维度：C10（并发正确性的测试保护力，最后批次单轮维度）
- 审计日期：2026-10-18
- 输入：本目录 C1/C3/C6/C7/C8 五维度 `## 最终保留项` + `## 维度复核结论`（共 54 条：P0×1 / P1×22 / P2×27 / P3×4）
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` L329–347（C10 正文）+ 附录 A 格式
- 回码基准：live code（审计工作区 nop-persistence 10 源码根 `src/test/java` 全量盘点 + 全仓 `src/test` 交叉 grep）

## 审计范围与口径

**对象**：54 条最终保留发现的测试保护力——不是"有没有测试"，而是"回归会不会红"。

**判定规则（变异推演口径）**：
- **会红·有保护**：存在确定性触达缺陷语义的测试——缺陷语义（或等价回归）一旦出现/回归，该测试必然或大概率红。
- **假保护**：同类/同方法/同组件的测试存在，但断言的不是缺陷语义，或触发条件（并发时序/多实例/非默认配置/规模）在测试条件下不可达。
- **无测试**：缺陷语义（触发面或修复后才成立的行为）在全仓 `src/test` 无任何触达；每条给出至少一种检索路径。

**重要前提**：54 条发现均为未修复缺陷（新发现 51 / 已知未修复 3）且现有套件为绿。因此对确定性功能型缺陷，"测试触达缺陷语义且断言正确语义"与"套件绿"不可同真——会红判定的实际来源只能是：测试触达同一代码面但因触发条件（配置/时序/规模）不满足而绿。本轮实测 **会红 = 0**，全部 54 条落入假保护（30）或无测试（24）。

**测试底账（10 源码根）**：nop-dbtool-core 3 个测试文件、nop-db-migration 9 个、nop-orm-migration 9 个（含 E2E 基座）、nop-cdc-core 0 个、nop-nosql-core 1 个、nop-nosql-lettuce 4 个（其中 2 个 docker-gated）、nop-orm-tdengine 6 个（1 个整体 @Disabled）、nop-orm-geo 7 个（无涉本批发现）、nop-orm-pdm 1 个、nop-orm-rpc 2 个。相邻模块（本单元外）：nop-orm 的 TestOrmMigrationWriteGate / shard 族测试、nop-kernel 的 TestICacheDefaults 作为证据锚点纳入。

**时间敏感与门控审查（C10 步骤 3/4/5）**：
- `Thread.sleep` 同步测试：TestLettuceNosqlService（≥10 处，:114/:418/:600/:637/:730/:796/:952/:973）、TestIncrementalLagProbe（:55–64/:74–86，sleep(300) + 1s quiesce 竞速）、TestMigrationJobConfig 无。
- 资源泄漏钩子：全单元仅 1 个先例——`testClientResourcesNotLeakedAcrossStartStop`（TestLettuceNosqlService.java:959–977，lettuce 线程计数断言，docker-gated）；orm-migration 侧零泄漏钩子（无池空/句柄数/注册表空断言）。
- 规模包络测试：E2E 全部 4 行源数据；无任何有界边界（chunk 上界、缓存上限、maxParallelism 边界、深分页阈值）参数化测试。
- Docker 门控：TestLettuceNosqlService.java:57–58、TestLettuceClusterService.java:51–52 挂 `@EnabledIfSystemProperty(nop.test.docker.enabled)` + `@Testcontainers(disabledWithoutDocker=true)`——默认构建全跳过。

## 保护力矩阵

判定分布：**会红·有保护 ×0 / 假保护 ×30 / 无测试 ×24**。

| 发现 ID | 所在模块 | 现有测试覆盖判定 | 证据 |
|---|---|---|---|
| C1-001 | nop-orm-rpc | 假保护 | TestRpcEntityPersistDriver.java:124–210（loadAsync/batchLoadAsync 绑定契约测试，mock invoker + completedFuture + 同线程 join）；无任何断言回调执行线程/会话线程约束；检索路径：`grep syncGet|Thread nop-orm-rpc/src/test` → 0 命中 |
| C1-002 | nop-db-migration | 假保护 | TestMigrationEngine.java:202–224（testRegisterExecutorVisibleToDbTypeFilter：后构造注册单线程可见性）、:227–251（注册顺序）；竞态零覆盖；检索路径：`grep registerExecutor|Thread src/test` → 多线程 0 命中 |
| C1-003 | nop-db-migration | 假保护 | TestMigrationHistoryManager.java:52–376（建表/记录/重试全顺序语义）+ TestMigrationEngine.java:106–155（skip/retry/failFast）；多节点并发启动零覆盖；检索路径：`grep ExecutorService|Thread core/TestMigration*.java` → 0 命中 |
| C1-004 | nop-orm-migration | 无测试 | 检索路径：`grep -rln IncrementalJobManager --include=*.java \| grep /test/`（全仓）→ 0 命中；RunningChainJob thread 发布/join null 短路无任何测试触达 |
| C1-005 | nop-orm-migration | 假保护 | e2e 仅 no-incremental 形态（AbstractMigrationE2ETestCase.java:144–159 rescaleConfig 注释自证）；runIncremental 全仓测试 0 引用；offset store 仅单引擎直用（TestIncrementalLagProbe.java:70,:92、TestCdcOffsetSeedBuilder.java:71） |
| C1-006 | nop-orm-migration | 假保护 | e2e 经编排器顺序断言状态行（TestMigrationPipelineE2E.java:63–99、:229–240）；abort×confirmSwitch 交错不可达；检索路径：`grep MigrationJobStateStore \| grep /test/` → 0 直接测试 |
| C1-007 | nop-orm-migration | 假保护 | nop-orm 单线程 setShardSelector 测试（AbstractOrmTestCase.java:59、TestShardBindingValidation.java:65、dbtool TestShardAutoUpgrade.java:101）；e2e :95 断言 "restart-required" 降级——rebuildRouting 热替换路径在 e2e 中根本不执行；可见性/竞态零覆盖 |
| C1-008 | nop-orm-migration | 假保护 | e2e 直接 new 编排器单线程触达惰性访问器（TestMigrationPipelineE2E.java:39–45）；并发首访时序零覆盖；检索路径：`grep incrementalManager\(\)|verifier\(\) \| grep /test/` → 仅经业务路径单线程 |
| C1-009 | nop-orm-migration | 无测试 | e2e abort 仅在 PREPARE（TestMigrationPipelineE2E.java:182–199），incrementalManager 未初始化即 abort；:302 直读路径测试不可达；检索路径：`grep "\.abort(" nop-orm-migration/src/test` → 仅 e2e 1 处（PREPARE 相位） |
| C1-010 | nop-orm-migration | 假保护 | TestOrmMigrationWriteGate.java:50–105（开关/读写放行语义）+ e2e :74–75/:83（门开拒绝→门关通过）；VERIFY/SWITCH 窗口内翻转无保持机制零覆盖 |
| C1-011 | nop-nosql-lettuce | 假保护 | TestLettuceNosqlService.java:557–829 六个 PubSub 测试全顺序（suspend→cancel→resume 串行：:789–797）；并发 subscribe×cancel/suspend×resume 交错零覆盖；且全类 docker-gated（:57–58） |
| C1-012 | nop-nosql-core | 假保护 | TestNosqlCache.java:50–76 仅断言 getAsync 路由到哪个 ops 方法；computeIfAbsent 覆写委托无测试；检索路径：`grep computeIfAbsent nop-nosql*/src/test` → 0 命中（契约单测在 nop-kernel TestICacheDefaults.java:165–185，测的是接口默认实现） |
| C1-013 | nop-nosql-lettuce | 假保护 | TTL 最终存在性断言（TestLettuceNosqlService.java:913–933 getTimeout>0、:945–956 过期后 setField 不复活）；HMSET→PEXPIRE 原子性窗口不可见；docker-gated |
| C1-014 | nop-nosql-lettuce | 无测试 | 检索路径：`grep getCapabilities --include=*.java \| grep /test/`（全仓）→ 命中均为 gateway/xlang/pulsar 异类，lettuce 测试 0 命中 |
| C1-015 | nop-orm-tdengine | 无测试 | TestTdTableMeta.java:45–63 仅 subTableName 命名校验；getter 防御性拷贝/可变性泄漏零断言；检索路径：`grep getTagColumns\|getSubTables src/test` → 0 命中 |
| C3-001 | nop-orm-migration | 无测试 | 检索路径：`grep -rln startFallbackChains \| grep /test/`（全仓）→ 仅 nop-ai-gateway 异类（ChannelConnectorManager.stopAll 不同类）；链启动/回滚零测试 |
| C3-002 | nop-orm-migration | 无测试 | 检索路径：`grep getFailure nop-orm-migration/src/test` → 0 命中（failure AtomicReference 无任何消费断言；与 C3 复核 grep 结论一致） |
| C3-003 | nop-orm-migration | 无测试 | e2e 全链测试不启动增量链（TestMigrationPipelineE2E.java:56–104 无 runIncremental/startChain），completeSwitch 停链语义零覆盖（与 C3 复核"测试面同构缺陷不成立"勘误一致） |
| C3-004 | nop-nosql-lettuce | 假保护 | 同 C1-011 测试面（TestLettuceNosqlService.java:586–611 unsubscribe 顺序语义）；并发 cancel 交错零覆盖；docker-gated |
| C3-005 | nop-nosql-lettuce | 无测试 | 六个 PubSub 测试全部断言"收到/没收到"，无任何断言投递线程、阻塞行为或扇出完整性的测试；检索路径：`grep onMessage.*Thread\|assert.*thread nop-nosql-lettuce/src/test` → 0 命中 |
| C3-006 | nop-nosql-core | 假保护 | 同 C1-012（TestNosqlCache 仅 getAsync 路由断言） |
| C3-007 | nop-nosql-lettuce | 假保护 | 同 C1-013（TTL 最终存在性断言，两步窗口不可见） |
| C3-008 | nop-orm-migration | 假保护 | TestIncrementalLagProbe.java:34,:70,:92 探测与注册用同一字面 connector 名（自对齐）；链注册 base-i 后缀派生零覆盖（IncrementalJobManager 无测试）；config 侧仅校验 connector-name 必填（TestMigrationJobConfig.java:129） |
| C3-009 | nop-orm-migration | 假保护 | relocate 仅 config 解析测试（TestMigrationJobConfig.java:82 testParseRelocateConfig）；router 测试仅 rescale 空 qs 映射（TestIncrementalLagProbe.java:103–119）；relocate 路由执行零覆盖（C3 复核"e2e relocate 零覆盖"复核成立） |
| C3-010 | nop-orm-migration | 假保护 | probe 测试单 namespace（TestIncrementalLagProbe.java:43 insertLedgerRow("mig",…) 与 probe 构造同源）；状态行空间 vs sink 提交空间错位零覆盖 |
| C3-011 | nop-orm-migration | 无测试 | 检索路径：`grep -rln FullCopyBatchJob \| grep /test/`（全仓）→ 0 命中；e2e 经 fullJob().run 单实体单 qs（TestMigrationPipelineE2E.java:107–119），跨表 taskKey 串用不可达 |
| C3-012 | nop-orm-migration | 无测试 | TestCdcOffsetSeedBuilder.java:43–112 仅种子形态/往返；位点↔订阅面↔源表全集三方交叉校验零断言；TestSourcePositionPinner.java:23–54 仅 pin/repin |
| C6-001 | nop-orm-migration | 无测试 | 同 C3-001（startFallbackChains 全仓测试 0 引用） |
| C6-002 | nop-orm-migration | 无测试 | e2e completeSwitch 路径无链在跑（TestMigrationPipelineE2E.java:98–99 仅断言门开/DONE）；"停链"语义零覆盖；检索路径：`grep stopAll nop-orm-migration/src/test` → 0 命中 |
| C6-003 | nop-orm-migration | 无测试 | 检索路径：`grep join\|interrupt nop-orm-migration/src/test` → 0 命中；终止可验证性零测试 |
| C6-004 | nop-nosql-lettuce | 假保护 | TestLettuceNosqlService.java:739–765（cancel 不影响他者：顺序语义）、:768–800（resume 不复活：串行语义）；check/nosql-cdc 前案登记的并发面零覆盖；docker-gated |
| C6-005 | nop-nosql-lettuce | 假保护 | 泄漏钩子仅 start/stop（TestLettuceNosqlService.java:959–977 线程计数）；resize/refreshConfig 收缩路径零覆盖；dockerless 侧仅配置解析（TestLettuceRedisConnectionProvider.java:31–108） |
| C7-001 | nop-orm-migration | 无测试 | 同 C3-002 检索（getFailure 测试 0 引用）；awaitLagZero 测试仅对齐名+单 namespace（TestIncrementalLagProbe.java:41–100） |
| C7-002 | nop-orm-migration | 假保护 | TestMigrationPhase.java:24–80 五状态矩阵全断言，但失败恢复语义（中途失败卡死/受控自环）零覆盖；无 X→X 非法断言（反向钉死排查通过） |
| C7-003 | nop-orm-migration | 假保护 | 状态行经 e2e 顺序断言（同 C1-006）；无单飞/fencing 零覆盖（MigrationJobStateStore 直接测试 0） |
| C7-004 | nop-orm-migration | 假保护 | TestOrmMigrationWriteGate.java:50–105 + e2e 均 JVM 内单实例；集群冻结/跨实例传播不可达；检索路径：无任何多实例测试（全单元测试均单 JVM） |
| C7-005 | nop-nosql-core | 无测试 | TestNosqlCache.java:50–76 无写路径 TTL 断言（仅 get 路由）；检索路径：`grep expireAfterWrite\|putEx nop-nosql-core/src/test` → 0 命中 |
| C7-006 | nop-orm-migration | 假保护 | e2e abort 仅 PREPARE 相位、无链无批作业在跑（TestMigrationPipelineE2E.java:182–199）；FULL 中止不停批复制/换实例不停链不可达 |
| C7-007 | nop-orm-migration | 假保护 | TestConsistencyCanonical.java:29–92 仅 canonical/rowHash 单测；e2e checksum 4 行（TestMigrationPipelineE2E.java:157–179）；全量驻内存/O(N²) 深分页不可达（scanHashes/findPage 测试 0 直接引用） |
| C7-008 | nop-orm-migration | 假保护 | probe 测试覆盖"ledger 有新增→lag≠0"（TestIncrementalLagProbe.java:50–66）但单 namespace 单作业；墙钟回拨/跨 namespace 观测污染零覆盖 |
| C7-009 | nop-nosql-lettuce | 无测试 | 检索路径：`grep doStop\|doStart\|stop().*start nop-nosql-lettuce/src/test` → 仅 provider start/stop（TestLettuceNosqlService.java:79/:90/:970），message service stop→start 生命周期零覆盖 |
| C7-010 | nop-nosql-lettuce | 无测试 | 同 C3-005（投递线程/阻塞行为零断言） |
| C7-011 | nop-nosql-lettuce | 假保护 | 同 C1-013（testSessionStore_TouchRefreshesTTL :409–424 顺序 TTL 语义；两步窗口不可见） |
| C7-012 | nop-orm-migration | 无测试 | e2e 有 ledger 计数 helper（AbstractMigrationE2ETestCase.java:132–142）但仅用于 lag 观测；DONE 后 namespace 残留/保留策略零断言；检索路径：`grep ledgerRowCount nop-orm-migration/src/test` → 仅 lag 测试 |
| C7-013 | nop-nosql-lettuce | 假保护 | TestLettuceRateLimiterValidation.java:20–36 仅构造校验；docker-gated fractional 测试断言 token 计数（TestLettuceNosqlService.java:876–892）；墙钟回拨/跨节点共享零覆盖 |
| C7-014 | nop-nosql-lettuce | 无测试 | 检索路径：`grep reconnect\|RESUBSCRIBE\|NUMSUB nop-nosql-lettuce/src/test` → 0 命中；断线重连场景零测试（docker-gated 测试亦无网络扰动注入） |
| C7-015 | nop-nosql-lettuce | 假保护 | testLockDoubleUnlock（TestLettuceNosqlService.java:686–693）单进程顺序 double-unlock + isHeld 局部语义；跨进程 isHeld 假阳性/无续期/fencing 零覆盖；docker-gated |
| C8-001 | nop-nosql-lettuce | 无测试 | 同 C3-005/C7-010（慢消费者阻塞/队头阻塞/断链行为零断言） |
| C8-002 | nop-orm-migration | 假保护 | 同 C7-007（4 行 e2e + canonical 单测；规模包络边界零参数化） |
| C8-003 | nop-orm-tdengine | 无测试 | 批 SQL 生成有测（TestTdSqlHelper.java:82 testGenBatchInsertSubTableSqlValues）但无任何行数×子表声明数×字节量 chunk 边界测试；检索路径：`grep chunk\|split\|batchSize nop-orm-tdengine/src/test` → 0 命中；集成测试整体 @Disabled（TestTdEnginePersist.java:16） |
| C8-004 | nop-orm-rpc | 无测试 | 批写测试仅 1 action（TestRpcEntityPersistDriver.java:77–106）；批量边界/chunk 化零断言；检索路径：`grep batchExecute.*List.of( src/test` → 全部单元素 |
| C8-005 | nop-nosql-lettuce | 无测试 | 检索路径：`grep forEachItem --include=*.java \| grep /test/`（全仓）→ 0 命中 |
| C8-006 | nop-nosql-core | 无测试 | queue 测试仅功能语义（TestLettuceNosqlService.java:655–664 dequeueBatch；:862 enqueueBatch 空参）；容量/溢出策略旋钮零断言（接口无此 API 可断言） |
| C8-007 | nop-nosql-lettuce | 假保护 | forEachEntry 功能测试存在（TestLettuceNosqlService.java:489–547 收集断言）但小数据集、无 MATCH/总量上限断言；全库遍历放大面不可达；docker-gated |

## 反向钉死检查

逐条排查 54 条发现对应的现有测试是否断言了缺陷语义本身（修复会打红须同步改写）。重点排查了 orm-migration e2e/集成测试与 nosql 测试。**结论：硬命中 0，条件性命中 2**。

**排查后确认不构成反向钉死的高危候选**（修复可直接做，现有测试保持绿）：

1. **e2e completeSwitch 路径（C6-002/C3-003）**：testFullPipelinePrepareFullVerifySwitchDone（TestMigrationPipelineE2E.java:56–104）从 testFullPipeline 断言 completeSwitch→DONE→门重开。C6 复核给出的修复通道含"completeSwitch 增加 runningJobs 空 fail-fast 门禁"——e2e 不启动链，runningJobs 为空，门禁通过，测试不红。已验证。
2. **TestMigrationPhase（C7-002）**：testIllegalTransitionsFail（:50–63）断言 6 条非法迁移，**不含任何 X→X 自环断言**；受控自环修复不触红。testAbortReachableFromEveryActivePhase 的 SWITCH→ABORT 非法断言与停链修复正交。
3. **PubSub resume-after-cancel（C1-011/C3-004/C6-004）**：testPubSub_ResumeAfterCancelDoesNotResurrect（TestLettuceNosqlService.java:768–800）断言的"取消后 resume 不复活"恰是修复方向规定的契约（cancelled 重入守卫）；缺陷在并发交错面，串行断言与锁收敛修复兼容。
4. **nosql 两步写 TTL（C1-013/C3-007/C7-011）**：testHashOps_PutIfAbsentOrMatchEx（:913–933）与 testSessionStore_SetFieldDoesNotResurrectExpiredSession（:945–956）断言 TTL 最终存在与不复活——Lua 原子化修复后两断言均保持成立。
5. **provider 泄漏钩子（C6-005）**：testClientResourcesNotLeakedAcrossStartStop（:959–977）不触发 resize，缩容排空修复不触红。
6. **forEachEntry 功能测试（C8-007）**：:489–547 小数据集收集断言，加 MATCH/上限（可选重载形态）不触红。
7. **e2e restart-required 断言（C1-007）**：:95 断言非 SessionFactoryImpl 宿主降级为 restart-required——volatile 化修复不改变该降级路径。
8. **history 语义测试（C1-003）**：testRecordMigrationFailedWithoutErrorMessageStaysFailed / testRecordMigrationKeepsExistingRowWhenWriteFails 与互斥修复兼容。

**条件性命中 2（弱反向钉死：测试钉住的是缺陷链的前提语义，修复时若选特定方向会被打红，须同步改写）**：

- **RP-1（C1-003）**：TestMigrationHistoryManager.testRecordMigrationReplacesExistingRecord（:253–278）显式断言"重试成功必须**替换**已存在行（主键冲突走 UPDATE 覆盖），而非拒绝"（注释原文 "must be replaced, not rejected with a primary key violation"）。这正是 C1-003 缺陷链中"UPDATE 覆盖对方 success 行"的机制半边。若修复选"主键冲突即拒绝/insert-only"方向，本测试必红；修复须按"跨进程互斥（锁/租约）+ 保留 upsert 语义"方向实施，或同步改写本测试。
- **RP-2（C7-001/C3-002）**：TestIncrementalLagProbe.testLagZeroWhenNothingAdvances（:42–47）与 testLagZeroWithStaticOffsetsAndLedger（:90–100）把"offset 静止 + ledger 静止 → lag=0（判追平）"钉为**正确语义**——这正是 C7-001"死链冻结位点被误判追平"的观测前提。若修复在 probe 层实现（probe 接收链活性句柄、死链返回 false），两条测试须改写；若按复核裁定的修复顺序在编排器层消费 failure（先修 C3-008 键派生后修 C7-001），测试不红。登记为修复设计输入约束。

## 缺保护登记

按附录 A 格式登记。同案发现（C1/C3/C6/C7/C8 复核已裁定的同站点并集）合并为同一保护面条目，共 **39 条（C10-001…C10-039，全部 P2）**，覆盖全部 54 条非会红发现。会红=0，故无遗漏。

### [C10-001] 增量链成功路径停链语义零测试（C6-002 P0 + C3-003 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:279-290`（缺陷面）；测试证据 `src/test/java/io/nop/orm/migration/TestMigrationPipelineE2E.java:56-104`
- **证据片段**:
  ```java
  // e2e 唯一 completeSwitch 路径：无链在跑，停链语义从未被执行
  orchestrator.confirmSwitch(jobId);
  ...
  orchestrator.completeSwitch(jobId);
  assertEquals(MigrationPhase.DONE, orchestrator.getState(jobId).getPhase());
  ```
- **严重程度**: P2（本体 C6-002 为 P0——本单元唯一 P0 无任何测试保护）
- **并发场景**: 编排线程与 M 条链 daemon 线程（IncrementalJobManager.startChain 裸线程）
- **触发时序**: 理论风险，未具体化（测试面）。任何现有测试均不启动链，故"SWITCH 后 abort 不可达 + DONE 不停链"在测试中不可观测
- **现状**: 全仓 `grep stopAll nop-orm-migration/src/test` → 0 命中；e2e 仅走 no-incremental rescale 形态
- **风险**: P0 级泄漏（每次成功迁移泄漏 M 条链）修复后无回归护栏；"completeSwitch 空 runningJobs 门禁"修复可无障碍落地（反向钉死排查通过）
- **建议**: 修复 C6-002 时同 diff 补 e2e/单测：FakeClockSourceFunction 启动 1 条链 → confirmSwitch/completeSwitch 后断言 runningJobs 空 + 链线程退出（有界 join 超时断言）
- **失效面枚举**: C6-002（本条主登记）；C3-003 同站点（同案合并计一次）；C7-006 FULL 中止面另条（C10-016）
- **信心水平**: 确定
- **误报排除**: 非"低频路径粗锁"类误报——本条只登记测试保护力缺失，不重复判级本体
- **复核状态**: 已登记（C10 初审）

### [C10-002] startFallbackChains 部分启动/孤儿链零测试（C6-001 P1 + C3-001 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:121-141`（缺陷面）；检索 `grep -rln startFallbackChains --include=*.java | grep /test/`（全仓）→ 仅 nop-ai-gateway 异类命中
- **证据片段**:
  ```text
  $ grep -rln "startFallbackChains" --include="*.java" . | grep "/test/"
  nop-ai/nop-ai-gateway/src/test/.../TestChannelConnectorManager.java   # 异类：ChannelConnectorManager.stopAll
  （nop-persistence 下 0 命中）
  ```
- **严重程度**: P2
- **并发场景**: 编排线程顺序启动 M 条链；部分失败后已启动链无补偿
- **触发时序**: 理论风险，未具体化（测试面）。链启动路径无任何测试执行
- **现状**: 链启动/回滚/jobName 非单射覆盖（relocate 同名）全部零覆盖
- **风险**: C3 复核确认的 relocate 确定性同名覆盖面（startChain:174 丢 qs）修复后无护栏
- **建议**: 参数化单测：MockListableOffsetStore + fake source function，覆盖 (a) 第 i 条链启动失败回收已启动链、(b) relocate 同名异 qs jobName 冲突 fail-fast
- **失效面枚举**: C6-001+C3-001（同案合并）；关联 C3-011 taskKey 面（C10-008）
- **信心水平**: 确定
- **误报排除**: ai-gateway 命中为不同类不同模块，已人工排除
- **复核状态**: 已登记（C10 初审）

### [C10-003] stopAll 终止不可验证零测试（C6-003 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:201-206,235-243`；检索 `grep -rn "join\|interrupt" nop-orm-migration/src/test` → 0 命中
- **证据片段**: 无测试引用 IncrementalJobManager（全仓 /test/ 0 命中），无可引用片段
- **严重程度**: P2
- **并发场景**: abort 调用线程 vs 链 daemon 线程的协作式停止协议
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: stopAll 无 join/无超时/无中断、join(null) 短路在测试中不可观测
- **风险**: "cancel→有界 join→超时 interrupt"修复（C6 复核裁定与 C6-002 同 diff）无护栏
- **建议**: 修复同 diff：fake 长驻 source，断言 stopAll 在超时内返回且线程终结（thread.isAlive()==false）
- **失效面枚举**: C6-003（独立面）；与 C10-001 同修复 diff
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-004] 链死亡不可见 + fail-fast 契约无测试（C3-002 P1 + C7-001 P1；含 RP-2 弱反向钉死）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:177-181`（failure.set）；测试证据 `src/test/java/io/nop/orm/migration/TestIncrementalLagProbe.java:42-47,90-100`
- **证据片段**:
  ```java
  // 测试把"冻结=追平"钉为正确语义（RP-2）：
  assertTrue(probe.awaitLagZero(null, 1), "no-incremental job: ledger static → lag=0");
  ```
- **严重程度**: P2
- **并发场景**: 链线程死亡 vs 编排线程 awaitLagZero 观测
- **触发时序**: 理论风险，未具体化（测试面）。probe 测试用对齐名+单 namespace，死链场景（run 循环退出后 offset 冻结）无测试
- **现状**: `grep getFailure nop-orm-migration/src/test` → 0 命中；lag=0 测试断言静止即追平（见反向钉死 RP-2）
- **风险**: C3-002/C7-001 修复（消费 failure + fail-fast）若落在 probe 层将打红 RP-2 两条测试；修复设计须按复核裁定顺序（先 C3-008 后 C7-001、编排器层消费）
- **建议**: 修复同 diff：fake 死链（run 直接抛错）→ 断言 runVerify 以链失败类型化拒绝，而非 lag 门禁通过
- **失效面枚举**: C3-002+C7-001（同案合并）；C1-CL-006/C8-CL-023 线索面随工单
- **信心水平**: 确定
- **误报排除**: RP-2 非误报——测试断言的观测语义在"健康静默"场景下正确，仅死链场景不可分；登记为约束而非错误
- **复核状态**: 已登记（C10 初审）

### [C10-005] lag 探测 connector 名错位假保护（C3-008 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:136`（缺陷面）；测试证据 `src/test/java/io/nop/orm/migration/TestIncrementalLagProbe.java:34,70,92`
- **证据片段**:
  ```java
  private static final String CONNECTOR = "lag-probe-cdc";   // 测试探测与注册同一字面名
  NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
  ... probe.awaitLagZero(CONNECTOR, 1) ...
  ```
- **严重程度**: P2
- **并发场景**: 链注册（base-i 后缀）vs 探测（裸配置名）
- **触发时序**: 理论风险，未具体化（测试面）。测试自对齐命名，M>1 后缀错位不可达
- **现状**: probe 单测 4 条全部对齐名；IncrementalJobManager 键派生零测试
- **风险**: C7-001 修复顺序依赖本条先修（键派生同源化），无护栏
- **建议**: 单测：以 base 名 + 并行度 2 构造链注册 → 断言 probe 用派生名能命中 registry 条目
- **失效面枚举**: C3-008（独立）；同模式族 C3-009/010/011 各自条目
- **信心水平**: 确定
- **误报排除**: config 侧 testIncrementalWithoutConnectorNameFails 仅校验必填，不构成保护
- **复核状态**: 已登记（C10 初审）

### [C10-006] relocate 路由 qs 硬编码假保护（C3-009 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/ShardChangeEventRouter.java`（缺陷面 route:41/routeRow:236/routeShardValue:252）；测试证据 `TestIncrementalLagProbe.java:103-119`（仅 rescale）、`TestMigrationJobConfig.java:82`（仅解析）
- **证据片段**:
  ```java
  // e2e 基座自证：querySpace 恒空 → 硬编码 "" 缺陷在测试中恒不可见
  /** rescale 1→2 作业配置（无 query-space-pattern：querySpace 为 null → 全部落缺省 querySpace） */
  protected MigrationJobConfig rescaleConfig() { ... .sourceQuerySpaces(List.of("")) ... }
  ```
  （AbstractMigrationE2ETestCase.java:144-159）
- **严重程度**: P2
- **并发场景**: 无（确定性功能缺陷——非空 qs relocate 下增量 filter 恒 false）
- **触发时序**: 测试面确定不可达：全部测试 qs=""，equals("")==true 掩盖硬编码
- **现状**: relocate 执行路径零覆盖，仅 config 解析测试
- **风险**: 确定性数据丢失缺陷（P1 本体）修复后无护栏；多源 qs FULL 合并写面（复核新证）同裸奔
- **建议**: 修复同 diff：非空 qs relocate 配置 → 断言 route/verifier/sink 三处均用 mapped qs（1 个 e2e + 1 个 router 单测）
- **失效面枚举**: C3-009（独立）；与 C3-008/010/011 同模式统一修复
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-007] lag 门禁 ledger 观测 qs 错位假保护（C3-010 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalLagProbe.java:52-69`（缺陷面）；测试证据 `TestIncrementalLagProbe.java:43-66`
- **证据片段**: insertLedgerRow("mig",…) 与 probe 构造（jdbcTemplate, "", "stream_epoch_ledger_v2"）同 namespace 同源——两侧 qs 派生错位在测试中恒对齐
- **严重程度**: P2
- **并发场景**: 观测线程 ledger 查询 vs sink 提交空间
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 单 namespace 对齐测试；loud/silent 两型后果零覆盖
- **风险**: 与 C7-008 namespace 过滤协同修复（复核裁定）无护栏
- **建议**: 修复同 diff：异 namespace ledger 行注入 → 断言观测互不污染
- **失效面枚举**: C3-010；正交面 C7-008（C10-018）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-008] FullCopyBatchJob taskKey 丢 qs 零测试（C3-011 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/FullCopyBatchJob.java:98-111`；检索 `grep -rln FullCopyBatchJob --include=*.java | grep /test/`（全仓）→ 0 命中
- **证据片段**: 无测试引用；e2e 仅经 fullJob().run("j2") 单实体单 qs（TestMigrationPipelineE2E.java:107-119）
- **严重程度**: P2
- **并发场景**: 平台 (taskName,taskKey) 唯一键/COMPLETED 闸门下的跨表身份串用
- **触发时序**: 理论风险，未具体化（测试面）。多表/多 qs 批作业零测试
- **现状**: 任务身份键派生零覆盖
- **风险**: 同根修复（与 C3-001 统一）无护栏
- **建议**: 单测：同实体两 qs 批作业 → 断言 taskKey 互异且 COMPLETED 闸门不互串
- **失效面枚举**: C3-011；扩展 C3-001 同根
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-009] P0 位点/订阅面/源表全集三方对齐零测试（C3-012 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/CdcOffsetSeedBuilder.java`（缺陷面）；测试证据 `TestCdcOffsetSeedBuilder.java:43-112`、`TestSourcePositionPinner.java:23-54`
- **证据片段**: 种子形态/往返/LSN 换算全覆盖，但无任何"种子 server 名 ∉ 源表全集对应订阅流 → fail-fast"的对齐校验断言
- **严重程度**: P2
- **并发场景**: 无（配置一致性面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 三方交叉校验零断言
- **风险**: 对齐校验补齐后无护栏
- **建议**: 单测：注入 mismatch（位点 server≠订阅 server）→ 断言 start 类型化拒绝
- **失效面枚举**: C3-012（独立）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-010] RunningChainJob 线程发布时序零测试（C1-004 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:174-188,235-243`；检索路径：`grep -rln IncrementalJobManager --include=*.java | grep /test/`（全仓）→ 0 命中
- **证据片段**: 无测试引用该类，无可引用片段
- **严重程度**: P2
- **并发场景**: start 线程 vs stop 调用线程对 thread 字段的读写
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 类级零测试
- **风险**: 句柄发布顺序化（volatile/handle-before-start）修复无护栏
- **建议**: 随 C10-003 同 diff 覆盖（stop 与 start 竞争线程）
- **失效面枚举**: C1-004（独立）；与 C1-009 abort 直读面同族
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-011] 状态行/编排并发守卫假保护（C1-005 P1 + C1-006 P1 + C1-009 P1 + C7-003 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/MigrationJobStateStore.java:100-118`、`OrmMigrationOrchestrator.java:185-204,296-313,444-454`；测试证据 `TestMigrationPipelineE2E.java:63-99,182-199,229-240`
- **证据片段**:
  ```java
  // e2e 状态行断言全部单线程顺序推进；abort 仅 PREPARE 相位（无链无 manager）
  orchestrator.abort("j6", "manual abort");
  assertEquals(MigrationPhase.ABORT, orchestrator.getState("j6").getPhase());
  ```
- **严重程度**: P2
- **并发场景**: abort×confirmSwitch 双 checkTransition 交错；runIncremental 双入双建 manager；多节点无 fencing
- **触发时序**: 理论风险，未具体化（测试面）。e2e 顺序推进使全列覆盖丢更新/双入双建不可观测
- **现状**: `grep MigrationJobStateStore | grep /test/` → 0 直接测试；e2e 仅顺序语义（子面注记：C1-009 的 :302 直读路径在 e2e 中不可达——abort 仅在 PREPARE 相位、incrementalManager 未初始化，该子面按无测试口径并入本条登记）
- **风险**: C1-005/006/009/C7-003 同站点工单（复核裁定合并）修复无护栏；fencing/版本谓词属结构性变更更需回归面
- **建议**: 修复同 diff：双线程 abort×confirmSwitch 压力单测（CountDownLatch 对齐交错点）+ 状态行版本谓词断言；多节点面用两 store 实例模拟
- **失效面枚举**: C1-005/C1-006/C1-009/C7-003（同站点并集，C7 复核裁定合并计一次）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-012] rebuildRouting 热替换可见性假保护（C1-007 P1）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:70,194-200`；测试证据 `nop-persistence/nop-orm/src/test/java/io/nop/orm/AbstractOrmTestCase.java:59`、`shard/TestShardBindingValidation.java:65`、`nop-dbtool/.../TestShardAutoUpgrade.java:101`、e2e `TestMigrationPipelineE2E.java:95`
- **证据片段**: 全部 setShardSelector 测试为单线程"set 后立即查"形态——非 volatile 字段在单线程下行为与 volatile 无异；e2e 走 "restart-required" 降级分支（非 SessionFactoryImpl 宿主），rebuildRouting 实际不执行
- **严重程度**: P2
- **并发场景**: 编排线程 rebuildRouting vs 请求线程读 selector
- **触发时序**: 理论风险，未具体化（测试面）。测试中写读同线程，happens-before 缺陷不可观测
- **现状**: 热替换路径在 e2e 中被降级分支短路；单线程测试给"立即生效"注释以假绿
- **风险**: volatile 化/安全发布修复无护栏；且该面与平台侧 nop-core-framework-C1-007 联动，回归面须列全
- **建议**: 修复同 diff：双线程 setShardSelector×持续查询压力测试（或 jc-stress 形态单测）断言最终可见
- **失效面枚举**: C1-007（本单元）；同族 nop-persistence-rw-C1-003、平台 nop-core-framework-C1-007（跨单元联动回归面）
- **信心水平**: 确定
- **误报排除**: 单线程 set-then-get 测试不是本领域误报校准对象，登记为假保护证据
- **复核状态**: 已登记（C10 初审）

### [C10-013] 编排器惰性组件 check-then-act 假保护（C1-008 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:120-124,432-460`；测试证据 `TestMigrationPipelineE2E.java:39-45`（直接 new，单线程贯穿全部访问器）
- **证据片段**: e2e 8 个用例均构造自有 orchestrator 实例顺序调用；惰性访问器并发首访（双 API 同时进入）不可达
- **严重程度**: P2
- **并发场景**: 两个编排 API 入口并发首访惰性组件
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 触发面窄（复核维持 P2 的理由），测试保护亦为零
- **风险**: 与 CL-035 同族修复（volatile/同步化）无护栏
- **建议**: 随 C10-011 的并发单测覆盖（双入口并发）
- **失效面枚举**: C1-008；同族 nop-kernel-concurrency-CL-035（watch）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-014] 写门瞬时检查/集群作用域假保护（C1-010 P2 + C7-004 P1）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmMigrationWriteGate.java:38-65`；测试证据 `nop-persistence/nop-orm/src/test/java/io/nop/orm/TestOrmMigrationWriteGate.java:50-105`、`TestMigrationPipelineE2E.java:74-75,83,182-193`
- **证据片段**: 门测试完备覆盖单 JVM 开/关/读写放行/重开恢复（5 个用例）；但 (a) VERIFY/SWITCH 长窗口内翻转无保持机制、(b) 集群其余实例冻结不可自愈——两者在单 JVM 测试中结构性不可观测
- **严重程度**: P2
- **并发场景**: 编排线程翻转门 vs 业务线程 DML 提交（窗口面）；多实例（集群面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 单 JVM 语义测试充分；窗口/集群面零覆盖
- **风险**: C7-004 修复（配置源回写/租约 fail-open）属跨模块结构性变更，无护栏；门可见性半边归平台 C1-007 工单（联动回归面）
- **建议**: 修复同 diff：多实例形态测试（两 AppConfig 实例或注入第二 provider）断言开门传播；窗口面用翻转线程×提交线程交错
- **失效面枚举**: C1-010/C7-004（本单元双面）；平台 nop-core-framework-C1-007（分案）
- **信心水平**: 确定
- **误报排除**: 单实例边界是原语已声明边界（C7 复核确认）——本条登记的是使用面（编排器集群语义）无测试，非重审原语
- **复核状态**: 已登记（C10 初审）

### [C10-015] 状态机失败恢复语义假保护（C7-002 P1）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/MigrationPhase.java:36-43`；测试证据 `TestMigrationPhase.java:24-80`
- **证据片段**: 五状态矩阵逐边断言（合法 6 边 + 非法 6 边 + 终态）；但"中途失败→卡死→fix-forward 不可达"（javadoc-实现矛盾）无测试；无 X→X 自环断言（反向钉死排查通过，受控自环修复不触红）
- **严重程度**: P2
- **并发场景**: 无（状态机恢复语义面）
- **触发时序**: 理论风险，未具体化（测试面）：INCREMENTAL 期崩溃→重启、VERIFY failed→重跑两条时序均无测试
- **现状**: 矩阵测试给状态机以"充分覆盖"假象，恢复面裸奔
- **风险**: C7-002 修复（受控自环 + completeSwitch 收敛门校验）无护栏
- **建议**: 修复同 diff：resume 于 INCREMENTAL/VERIFY/SWITCH 各相位的受控自环断言 + completeSwitch 带 routeRebuild 失败的收敛门拒绝断言
- **失效面枚举**: C7-002（吸收 C1-CL-007/008 线索面）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-016] abort 中止面不完整假保护（C7-006 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:296-313,341-354`；测试证据 `TestMigrationPipelineE2E.java:182-199`
- **证据片段**: e2e 唯一 abort 用例在 PREPARE 相位（无链、无批作业在跑）；FULL 中止不停批复制、换实例 abort 不停链两面不可达
- **严重程度**: P2
- **并发场景**: abort 线程 vs 批作业/链线程
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: abort 测试仅覆盖相位翻转+门恢复+旧配置不动
- **风险**: 与 C6-002 停链统一修复（复核裁定）无护栏
- **建议**: 修复同 diff：FULL 相位 abort → 断言批作业终止；INCREMENTAL 相位 abort → 断言链停止
- **失效面枚举**: C7-006；与 C6-002/C3-003 同根不同触发路径（互补不并案）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-017] ConsistencyVerifier 规模包络假保护（C7-007 P2 + C8-002 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/ConsistencyVerifier.java:104-189`；测试证据 `TestConsistencyCanonical.java:29-92`、`TestMigrationPipelineE2E.java:136-179`
- **证据片段**: canonical/rowHash 单测 6 条 + e2e checksum 4 行——全量驻内存/O(N²) 深分页在 4 行规模下结构性不可达；`grep scanHashes|findPage | grep /test/` → 0 直接引用
- **严重程度**: P2
- **并发场景**: 无（规模面）
- **触发时序**: 理论风险，未具体化（测试面）：声明包络内大表 checksum 无参数化边界测试
- **现状**: 正确性有保护（失配检出被 e2e 断言），规模语义零保护
- **风险**: 归并流式+keyset 分页修复（C7/C8 复核裁定合并工单）无护栏
- **建议**: 修复同 diff：小规模参数化模拟分页阈值（如 pageSize=2、行数 5）断言流式路径与分页次数上界
- **失效面枚举**: C7-007+C8-002（同案合并）；CL-025 同模式族
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-018] lag 静默窗墙钟 + namespace 过滤假保护（C7-008 P2）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalLagProbe.java:92-103`；测试证据 `TestIncrementalLagProbe.java:50-66`
- **证据片段**: 时序敏感测试先例：sleep(300) 后台提交 + 1s quiesce——断言方向（有提交→lag≠0）正确，但墙钟回拨、静默窗边界、跨 namespace 观测污染零覆盖
- **严重程度**: P2
- **并发场景**: probe 线程 vs 提交线程（sleep 竞速）
- **触发时序**: 测试自身时序脆弱（C10 步骤 3 登记）：慢机器上 300ms 提交延迟可能落入 1s 窗外→偶发绿
- **现状**: nanoTime 相对期限修复与 namespace 过滤（与 C3-010 协同）无护栏
- **风险**: 修复无护栏 + 既有测试平台性脆弱
- **建议**: 修复同 diff：注入 Clock 抽象使静默窗可测；namespace 过滤断言并入 C10-007 建议
- **失效面枚举**: C7-008；正交 C3-010
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-019] epoch ledger 无保留策略零测试（C7-012 P3）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:87,509-521`；测试证据 `AbstractMigrationE2ETestCase.java:132-142`（ledgerRowCount/insertLedgerRow 仅服务 lag 观测）
- **证据片段**: e2e 建了 ledger 表并手动插行，但 DONE 后 namespace 残留/无清理路径零断言
- **严重程度**: P2（本体 P3，随本体合并处置时可降 P3；append-only 增长主责随 WI 1.2 MA-05 P1 工单）
- **并发场景**: 无（F4 无界账本面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 保留策略零测试
- **风险**: 编排器 DONE 后 namespace 清理（随 MA-05 同批）无护栏
- **建议**: 修复同 diff：DONE 后断言本作业 namespace ledger 行被清理或归档标记
- **失效面枚举**: C7-012（迁移场景伴生面）；sink 面主登记 WI 1.2 MA-05（跨单元同案）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-020] MigrationEngine 注册表并发假保护（C1-002 P2）

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationEngine.java:68-70,109-121`；测试证据 `TestMigrationEngine.java:202-224,227-251`
- **证据片段**:
  ```java
  // testRegisterExecutorVisibleToDbTypeFilter：后构造注册能力被单线程测试钉住（该用法恰是缺陷前提）
  migrationEngine.registerExecutor("probeType", (change, context, dialect) -> executed.add("probe"));
  // 反射取出 dbTypeFilterExecutor 断言可见 —— 无任何并发注册断言
  ```
- **严重程度**: P2
- **并发场景**: 后构造 registerExecutor/registerMigration vs 迁移执行线程读取
- **触发时序**: 理论风险，未具体化（测试面）。测试注册全在 migrate 前/后单线程
- **现状**: 注册能力有测试，注册竞态零测试；CL-415 同族 watch 无联动回归面
- **风险**: 同步化/CHM 修复不触红现有测试（兼容），但回归后无护栏
- **建议**: 修复同 diff：双线程注册×迁移并发单测，或至少并发注册冒烟（ExecutorService 8 线程注册互异类型）
- **失效面枚举**: C1-002；同族 nop-persistence-rw-CL-415（watch）、nop-stream-orchestration-thin-C1-001
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-021] 迁移历史表 check-then-act 假保护（C1-003 P1；含 RP-1 弱反向钉死）

- **文件**: `nop-persistence/nop-db-migration/src/main/java/io/nop/db/migration/core/MigrationHistoryManager.java:72-139`；测试证据 `TestMigrationHistoryManager.java:52-376`、`TestMigrationEngine.java:106-155,176-195`
- **证据片段**:
  ```java
  // testRecordMigrationReplacesExistingRecord:263-270 —— 钉住"冲突必须覆盖"（RP-1）
  // A retried migration succeeds: the failed row must be replaced, not
  // rejected with a primary key violation
  assertDoesNotThrow(() -> historyManager.recordMigration(success));
  ```
- **严重程度**: P2
- **并发场景**: 多节点同时启动 → hasVersion/ensureTable TOCTOU + recordMigration 相互覆盖
- **触发时序**: 理论风险，未具体化（测试面）。全部历史表测试单线程单连接（H2）
- **现状**: 顺序语义重度测试（16 个用例）；并发/多节点零覆盖
- **风险**: RP-1 约束：修复不得走"冲突即拒绝"方向（会打红 :253 测试）；须按跨进程互斥（CL 台账锁设施）方向
- **建议**: 修复同 diff：两 MigrationEngine 实例共享 H2 表并发 migrate → 断言互斥后无 FAILED 脏行、无重复 DDL
- **失效面枚举**: C1-003；同族 1.5 C7-001/1.6 C3-003（跨进程锁设施共用选型）
- **信心水平**: 确定
- **误报排除**: RP-1 登记为修复方向约束，非阻断
- **复核状态**: 已登记（C10 初审）

### [C10-022] PubSub 订阅管理竞态假保护（C1-011 P1 + C3-004 P1 + C6-004 P1）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:72-94,189-226`；测试证据 `TestLettuceNosqlService.java:557-829`
- **证据片段**:
  ```java
  // 全部顺序：suspend → cancel → resume 串行执行，交错不可达（:789-791）
  sub1.suspend(); sub1.cancel(); sub1.resume();
  assertTrue(sub1.isCancelled(), "subscription must stay cancelled after resume()");
  ```
- **严重程度**: P2
- **并发场景**: subscribe（synchronized）×cancel/suspend/resume（无同步）并发管理操作
- **触发时序**: 理论风险，未具体化（测试面）。6 个 PubSub 测试全部串行 subscribe→send→cancel
- **现状**: 串行语义覆盖良好（含 check2 审计回归测试）；并发交错零覆盖；全类 docker-gated（:57-58），默认构建这些测试也不跑
- **风险**: C6-004 修复（cancel 入同一把锁 + cancelled 重入守卫 + 锁内空判复查）无并发护栏；testPubSub_ResumeAfterCancelDoesNotResurrect 与修复兼容（反向钉死排查通过）
- **建议**: 修复同 diff：并发压力测试（N 线程随机 subscribe/cancel/suspend/resume × 持续 publish）断言无孤儿订阅、无复活双投递；非 docker 环境可用 mock RedisPubSubListener 层单测
- **失效面枚举**: C1-011+C3-004+C6-004（同案合并）+CL-021；check/nosql-cdc.md 2026-08-19 [P2] 前案同工单
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-023] PubSub 投递 IO 线程内联零测试（C3-005 P2 + C8-001 P1 + C7-010 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:145-161`；测试证据 `TestLettuceNosqlService.java:557-829`（六测试全部只断言"收到/没收到"）
- **证据片段**: 无任何断言投递线程名、慢消费者阻塞传播或扇出完整性的测试；检索路径：`grep -i "thread" nop-nosql-lettuce/src/test/*.java` → 仅 :963-980 线程泄漏计数（provider 面）
- **严重程度**: P2
- **并发场景**: lettuce IO 线程同步回调 vs 慢消费者
- **触发时序**: 理论风险，未具体化（测试面）。C8 复核以字节码独立验证机制（notifyListeners 直迭代），测试面完全缺位
- **现状**: 四重登记（含 C1-CL-011 线索）的保护力为零；onMessage 返回值丢弃（ack 语义）亦无断言
- **风险**: 有界 executor 分发修复（7.1 裁定后实施）无护栏；投递模型改造是行为变更，最需要回归面
- **建议**: 修复同 diff：(a) 投递线程断言（consumer 记录 Thread.currentThread()，断言非 IO 线程）；(b) 慢消费者隔离测试（A 阻塞 2s，B 仍按时收到）；(c) CompletionStage 返回值被 await 的断言
- **失效面枚举**: C3-005+C8-001+C7-010（同案，严重度留 7.1）+C1-CL-011
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-024] PubSub stop→start 消息面死亡零测试（C7-009 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:107-117`；检索路径：`grep "stop()\|doStop" nop-nosql-lettuce/src/test/*.java` → 仅 provider start/stop（TestLettuceNosqlService.java:79,90,970-971），message service 生命周期零覆盖
- **证据片段**: 无测试引用 LettuceMessageService/PubSubService 的 stop→start 循环
- **严重程度**: P2
- **并发场景**: 生命周期管理线程 vs 存活订阅表
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: doStop 状态复位面（复核裁定主登记）零测试
- **风险**: doStop 状态复位 + isOpen 校验修复无护栏
- **建议**: 修复同 diff：docker-gated 集成测试 stop→start→publish→断言原订阅仍收；非 docker 层对复位逻辑做纯单测
- **失效面枚举**: C7-009（主登记，吸收 C1-CL-013/C3-CL-016）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-025] PubSub 断线重连丢订阅零测试（C7-014 P1）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:140-146`、`LettucePubSubService.java:96-105`；检索路径：`grep "reconnect\|RESUBSCRIBE\|NUMSUB" nop-nosql-lettuce/src/test` → 0 命中
- **证据片段**: 无任何网络扰动注入测试（docker-gated 测试也不含断链场景）
- **严重程度**: P2
- **并发场景**: lettuce 重连线程 vs 应用订阅表
- **触发时序**: 理论风险，未具体化（测试面）。长时运行必然触发面（复核 P1 维持依据）在测试中完全不可见
- **现状**: F5 完整形态（三层不可见）无任何检测断言
- **风险**: 重连监听重发 SUBSCRIBE + NUMSUB 对账修复无护栏；round3 五项回归面登记（C7 复核）是现成设计输入
- **建议**: 修复同 diff：docker-gated 测试用 redis CLI CLIENT KILL 断链 → 断言重订阅后消息恢复；非 docker 层对重连钩子接线做单测
- **失效面枚举**: C7-014（PubSub 族失效面③，独立）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-026] 连接池缩容不排空假保护（C6-005 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:92-100`；测试证据 `TestLettuceNosqlService.java:959-977`、`TestLettuceRedisConnectionProvider.java:31-108`
- **证据片段**: 唯一资源泄漏钩子（线程计数断言）只覆盖 provider start/stop 全生命周期；resize 收缩路径（RoundRobinSupplier 换表后热关连接）零覆盖
- **严重程度**: P2
- **并发场景**: refreshConfig 线程 resize vs 取用线程借还连接
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: dockerless 侧仅配置解析 6 测试；docker-gated 侧仅 start/stop 泄漏
- **风险**: 设施面主责随 WI 2.1 C1-001 P1 工单，本单元使用面联动验证无测试载体
- **建议**: 修复同 diff：docker-gated resize 测试（并发取用×连续 resize）断言无命令失败；非 docker 层对 resize 原子换表单测
- **失效面枚举**: C6-005；设施面 WI 2.1 nop-kernel-concurrency-C1-001；CL-020 线索分账
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-027] NosqlCache computeIfAbsent 违约假保护（C1-012 P2 + C3-006 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java:90-92`（覆写委托）；测试证据 `TestNosqlCache.java:50-76`
- **证据片段**:
  ```java
  // 现有 2 个测试只断言 getAsync 路由到 getAsync vs getExAsync：
  assertEquals("getAsync", method.get(), "without expireAfterAccess the plain getAsync path must be used");
  // computeIfAbsent 覆写（GET-then-SET 非原子）零断言；契约单测在 nop-kernel TestICacheDefaults.java:165-185（默认实现）
  ```
- **严重程度**: P2
- **并发场景**: 多线程 computeIfAbsent 同 key 双加载
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: NosqlCache 覆写了被 nop-kernel 测试钉住单一胜者语义的接口方法，覆写实现自身零测试——平台契约测试制造了"缓存族已受保护"的假象
- **风险**: 原子化修复（复用 ICache 契约或 Lua）无护栏
- **建议**: 修复同 diff：recording proxy 断言 computeIfAbsent 走原子原语 + 双线程单胜者断言（形态可抄 TestICacheDefaults）
- **失效面枚举**: C1-012+C3-006（同案合并）；4 站点 + 6 原子兄弟对照见 C3 复核
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-028] hash/session 两步写 TTL 假保护（C1-013 P2 + C3-007 P2 + C7-011 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceHashOperations.java:194-237`、`LettuceSessionStore.java:62-75`；测试证据 `TestLettuceNosqlService.java:409-424,913-933,945-956`
- **证据片段**: TTL 最终存在性断言（getTimeout>0）与过期不复活断言与 D03-02 前案回归测试同构；HMSET→PEXPIRE 崩溃窗口（half-success）在黑盒功能测试中结构性不可观测
- **严重程度**: P2
- **并发场景**: 写与 PEXPIRE 两命令间崩溃/异常
- **触发时序**: 理论风险，未具体化（测试面）：需要注入两命令间失败，现无注入点测试
- **现状**: 同案前案 2026-05-20 D03-02 当时"建议补文档或 Lua，未修"——修复拖延部分归因于无失败注入测试使缺陷不可见
- **风险**: Lua 原子化修复无护栏
- **建议**: 修复同 diff：注入点单测（mock async API 第二命令失败）断言回滚/无 TTL 键不存在；Lua 化后断言单命令路径
- **失效面枚举**: C1-013+C3-007+C7-011（同案合并）；4+1 站点清单见 C1/C3 复核
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-029] NosqlCache 写路径无 TTL 零测试（C7-005 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java:51-65`；检索路径：`grep "expireAfterWrite\|putEx\|putAsync" nop-nosql-core/src/test` → 0 命中（TestNosqlCache 仅 get 路由 2 测试）
- **证据片段**: 无写路径测试
- **严重程度**: P2
- **并发场景**: 无（无界键累积面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 零生产实例化（复核降档依据）+ 零测试双重缺位
- **风险**: 写路径 TTL 实现或显式拒绝（复核建议）无护栏
- **建议**: 修复同 diff：带 expireAfterWrite 配置实例化 → 断言写后 getTimeout>0
- **失效面枚举**: C7-005；邻面 C1-012/CL-014 分账
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-030] CAPABILITIES EnumSet 逃逸零测试（C1-014 P3）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:41-52`；检索路径：`grep getCapabilities --include=*.java | grep /test/`（全仓）→ 命中均为 gateway/xlang/pulsar 异类，lettuce 0 命中
- **证据片段**: 无测试引用
- **严重程度**: P2（本体 P3，随本体合并处置时可降 P3；全仓消费方只读）
- **并发场景**: 理论篡改者（现实不存在）
- **触发时序**: 理论风险，未具体化
- **现状**: 防御性拷贝修复无护栏亦无现实风险面
- **风险**: 低；若修复（EnumSet.copyOf）属无行为变化的加固
- **建议**: 修复同 diff 附 getter 不可变性单测（返回集修改抛 UnsupportedOperationException 或改动不影响内部）
- **失效面枚举**: C1-014；与 C1-015 同族合并修复
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-031] 限流令牌桶墙钟假保护（C7-013 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRateLimiter.java`（Lua 令牌桶）；测试证据 `TestLettuceRateLimiterValidation.java:20-36`（构造校验）、`TestLettuceNosqlService.java:876-892`（fractional token 计数，docker-gated）
- **证据片段**: 全部限流测试断言 token 数值/配置校验；客户端墙钟回拨冻结、跨节点共享桶语义零覆盖
- **严重程度**: P2
- **并发场景**: 多节点共享 Redis 桶 + 时钟回拨
- **触发时序**: 理论风险，未具体化（测试面）：需时钟注入
- **现状**: 无 Clock 抽象可注入
- **风险**: 服务端 TIME/固定窗口修复无护栏；与 2.3 C8-005 批次对齐时无共用回归面
- **建议**: 修复同 diff：Lua 脚本改服务端 TIME 后，断言客户端时钟无关性（伪造本地回拨不改变获取结果）
- **失效面枚举**: C7-013；同族 2.3 C8-005、2.1 CL-034（watch）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-032] LettuceLock 无续期/fencing 假保护（C7-015 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceLock.java`；测试证据 `TestLettuceNosqlService.java:686-693`（testLockDoubleUnlock：单进程顺序 double-unlock + isHeld 局部语义；TestLettuceClusterService.java:233 跨 slot 加锁仅路由面）
- **证据片段**: isHeld 跨进程假阳性（本地 state 判定）、锁过期后误释放他人、无续期——全部零断言
- **严重程度**: P2
- **并发场景**: 持锁 A 超时后持锁 B 与 A 的 unlock/isHeld 交错
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 锁组件仅 1 个顺序测试；零生产调用方（复核降 P2 依据）使缺口当前无现实触发
- **风险**: fencing/续期修复无护栏
- **建议**: 修复同 diff：docker-gated 双客户端竞争测试（A 获锁→TTL 过→B 获锁→A unlock → 断言 B 锁不被误删、isHeld(A)==false）
- **失效面枚举**: C7-015；fencing/租约族 1.5 C7-001/1.6 C3-003（设施选型对齐）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-033] forEachItemAsync 全量物化零测试（C8-005 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceListOperations.java:124-137`；检索路径：`grep forEachItem --include=*.java | grep /test/`（全仓）→ 0 命中
- **证据片段**: 无测试引用
- **严重程度**: P2
- **并发场景**: 无（LRANGE 0 -1 全量物化面）；零生产调用方（复核条件性标注）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: API 面完全无测试
- **风险**: 分页化修复（100/页循环）或 javadoc 边界声明无护栏
- **建议**: 修复同 diff：大数据集（可 mock LRANGE 分页返回）断言分页拉取且不一次物化
- **失效面枚举**: C8-005；CL-015 线索分账同案
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-034] INosqlQueue 无容量旋钮零测试（C8-006 P3）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/INosqlQueue.java:14-30`；测试证据 `TestLettuceNosqlService.java:655-664,862`（仅 dequeue/enqueue 功能）
- **证据片段**: 接口无容量/溢出 API，测试亦无从断言（缺陷即接口设计缺口）
- **严重程度**: P2（本体 P3，随本体合并处置时可降 P3）
- **并发场景**: 队列满策略三态（QueueOverflowPolicy）缺失
- **触发时序**: 不适用（API 缺口）
- **现状**: 旋钮加入前的保护力为结构性零
- **风险**: maxLen+QueueOverflowPolicy 修复后需新测试形态
- **建议**: 修复同 diff：容量 2 队列第 3 次 enqueue 按 fail/block/drop 三态断言
- **失效面枚举**: C8-006（独立）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-035] forEachEntry SCAN 无界假保护（C8-007 P2）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:132,227`；测试证据 `TestLettuceNosqlService.java:489-547`（5 个功能测试：收集/空库/async/回调异常）
- **证据片段**: 功能语义覆盖良好但全部小数据集；无 MATCH 过滤、无总量/时限上限断言
- **严重程度**: P2
- **并发场景**: 无（全库遍历放大面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 遍历正确性有保护，无界性零保护
- **风险**: 匹配约束+上限修复（可选重载形态）不触红现有测试（反向钉死排查通过），但也无护栏
- **建议**: 修复同 diff：超上限数据集 → 断言类型化失败或截断信号；MATCH 参数断言
- **失效面枚举**: C8-007；C7-005 关联（修断键增长源）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-036] RPC 驱动跨线程会话变更假保护（C1-001 P2）

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:261-286,368-411`；测试证据 `TestRpcEntityPersistDriver.java:124-210`、`TestRpcEntityPersistDriverNullFuture.java`
- **证据片段**: mock invoker 返回 completedFuture + 调用线程 join——thenAccept 回调全在测试线程执行，与生产（RPC 传输线程回调）线程拓扑相反；无任何断言回调线程或会话线程约束的测试
- **严重程度**: P2
- **并发场景**: RPC 传输线程 vs ORM 会话归属线程
- **触发时序**: 理论风险，未具体化（测试面）：mock 使缺陷在测试中结构性不可复现
- **现状**: 数据绑定契约（顺序无关绑定/missing 标记/参数形态）覆盖良好；线程语义零覆盖
- **风险**: 修复（会话亲和/syncGet 收敛/守卫）无护栏；同族内联契约（Td/Jdbc 驱动）无对照回归面
- **建议**: 修复同 diff：延迟完成的 future（调用线程外 complete）→ 断言绑定动作发生在正确线程或经显式交接
- **失效面枚举**: C1-001 的 7 个 syncGet 出口（:652..:910）；同族 Td(:84,:178)/Jdbc(:127,:190,:236) 对照（C1 复核）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-037] rpc 批写无 chunk 零测试（C8-004 P2）

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:310-337`；测试证据 `TestRpcEntityPersistDriver.java:77-106`（全部单 action 批）
- **证据片段**: `driver.batchExecuteAsync(true, null, List.of(saveAction), null, null, session)`——批量边界（N 大于阈值）零断言；verify(invoker) 恰好一次的断言反而会在 chunk 化后需要更新（登记为修复时的测试同步点，非反向钉死：单元素输入下 chunk 化仍一次调用）
- **严重程度**: P2
- **并发场景**: 无（批量规模面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: 驱动层无独立守卫（复核修正后：载面有会话层 1000 分块，写面无界维持）
- **风险**: 批写 chunk 化修复无护栏
- **建议**: 修复同 diff：N=阈值+1 输入断言拆为两次 RPC 且聚合语义不变
- **失效面枚举**: C8-004；与 C8-003 同批工单（驱动 chunk 同批一张）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-038] tdengine 批写无 chunk 零测试（C8-003 P2）

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/driver/TdEntityPersistDriver.java:128-160`；测试证据 `TestTdSqlHelper.java:82`（SQL 生成）、`TestTdEntityPersistDriver.java:99-135`（单 action 语义）；集成测试整体 @Disabled（TestTdEnginePersist.java:16）
- **证据片段**: 批 SQL 文本生成有测、tag 取首行语义（TdSqlHelper:66 TAGS(list.get(0))）无分歧行断言；行数×子表声明数×字节量三维度 chunk 零断言
- **严重程度**: P2
- **并发场景**: 无（批量规模 + tag 声明放大面）
- **触发时序**: 理论风险，未具体化（测试面）
- **现状**: round2 扩展面（同子表 tag 分歧静默取首行）零断言
- **风险**: 双维度 chunk 修复（复核裁定设计输入）无护栏
- **建议**: 修复同 diff：多子表声明 + tag 分歧数据集 → 断言切分边界与 TAGS 一致性（纯 SQL 生成层可测，无需 TDengine 实例）
- **失效面枚举**: C8-003；家族 WI 2.4 C5-004、WI 1.7 C8-001/002（不并案）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

### [C10-039] TdTableMeta 可变 List 泄漏零测试（C1-015 P3）

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/model/TdTableMeta.java:34-36,88-94`；测试证据 `TestTdTableMeta.java:45-63`（仅 subTableName 命名校验）
- **证据片段**: getter 返回内部 List 的防御性拷贝语义零断言；消费方 TdSqlHelper 只读（复核 P3 恰当依据）
- **严重程度**: P2（本体 P3，随本体合并处置时可降 P3）
- **并发场景**: 理论（构造后篡改）；现实消费方只读
- **触发时序**: 理论风险，未具体化
- **现状**: 与 C1-014 同族合并修复的另一半
- **风险**: 低
- **建议**: 修复同 diff：getter 返回不可变视图 + 单测断言修改抛异常或隔离
- **失效面枚举**: C1-015；同族 C1-014（合并）
- **信心水平**: 确定
- **误报排除**: 无
- **复核状态**: 已登记（C10 初审）

## 系统性观察

**S1（最重要）：增量链子系统是"零测试区"，且 E2E 只覆盖设计空间中最安全的角落。** 全仓 grep 证实 `IncrementalJobManager`、`MigrationJobStateStore`、`startFallbackChains`、`stopAll`、`runIncremental`、`FullCopyBatchJob`、`getFailure` 在全部 `src/test` 中 0 引用；唯一的 E2E（TestMigrationPipelineE2E）走 no-incremental rescale 形态（空 querySpace、不启动链、4 行数据、单 JVM）。结果是：本单元唯一 P0（C6-002 成功路径不停链）与全部 7 条链生命周期/lag 门禁 P1 的保护力为零——23 条"无测试"发现中 16 条集中于此。该子系统的编排协议（启动/停止/相位推进/状态行）是本单元缺陷密度最高的代码面，却没有任何一条直接单测。

**S2：nosql-lettuce 全部行为测试 docker-gated，默认构建下保护力归零。** TestLettuceNosqlService（984 行，PubSub/session/cache/lock/queue/ratelimit 的全部行为测试）与 TestLettuceClusterService 挂 `@EnabledIfSystemProperty(nop.test.docker.enabled)`——常规 `./mvnw test` 全部跳过。这意味着 PubSub 族 6 条发现（C1-011/C3-004/C6-004/C3-005/C7-009/C7-014）即使算上"相邻功能测试"，默认 CI 保护力也是 0。全单元唯一的资源泄漏钩子先例（:959–977 线程计数）也在这个被跳过的类里。建议：为 PubSub 生命周期与投递模型补一层无 Docker 的 fake-connection 单测（现有 check2 回归测试注释表明团队已有此意识，但并发面未跟进）。

**S3：54 条发现零并发测试；仅有的多线程测试把缺陷前提钉为正确语义。** 全单元没有任何测试以交错/压力方式断言并发缺陷语义（无 CountDownLatch 对齐、无多线程断言）；仅有的两个多线程测试（TestIncrementalLagProbe 的 committer/advancer）用 `Thread.sleep(300)` + 1s quiesce 竞速同步——平台性脆弱（慢机偶发绿）。更关键的是 testLagZeroWhenNothingAdvances/testLagZeroWithStaticOffsetsAndLedger 把"offset 静止+ledger 静止→判追平"钉为期望行为（RP-2），db-migration 的 testRecordMigrationReplacesExistingRecord 把"主键冲突必须覆盖"钉为期望行为（RP-1）——两处弱反向钉死已在修复方向上形成约束（C7-001 修复须按编排器层消费 failure；C1-003 修复须走互斥而非拒绝方向）。

**次级观察**：(a) 规模包络边界测试全单元为零——verifier（4 行 e2e）、批驱动（单 action）、forEachEntry（小数据集）都无任何边界参数化，C7-007/C8-002/C8-003/C8-004 四条规模缺陷的修复均需自建测试形态；(b) db-migration/nosql 的顺序功能测试质量很高（16+16 个用例、多处历史审计回归注释），说明"补回归测试"的工程习惯存在——缺的是并发/失败注入/规模三个维度的同标准覆盖；(c) nop-cdc-core、nop-orm-pdm、dbtool 无涉本批发现，非空洞。

## 终止判定

C10 为**单轮维度**（CRA 附录 B 指定最后批次、无深挖轮次），显式声明：本轮即终轮，不设 C10-round2。判定统计：54/54 条保护力不足（会红 0 / 假保护 30 / 无测试 24），39 条缺保护登记（C10-001…039，P2），弱反向钉死 2 处（RP-1 → C1-003 修复方向约束；RP-2 → C7-001/C3-002 修复层位约束）。缺保护条目不新增独立工单——按 CRA 口径**并入对应缺陷本体的修复工单**（"修复同 diff 补回归测试"），P0/P1 本体工单的验收标准应引用本文件对应条目的"建议"字段作为测试形态要求。

## C10 复核结论

- 复核人：C10 维度独立复核员（CRA WI 2.5 C10 独立复核，独立任务通道，非初审 agent）
- 复核日期：2026-10-18
- 方法：54 行矩阵 ↔ 五维度 `## 最终保留项` 全量对账 + 12 行矩阵回码抽查（假保护 6 / 无测试 6；docker 门控 3 处核实）+ RP-1/RP-2 断言原文核对 + 39 条登记机械格式核查 + 5 条登记检索纪律复现。

### 一、对账结果（矩阵完整性）

- **54/54 对账闭合，0 差异**。矩阵行 ↔ 五维度最终保留清单逐条对应：C1×15（P1×7/P2×6/P3×2）、C3×12（P1×6/P2×6）、C6×5（P0×1/P1×3/P2×1）、C7×15（P1×5/P2×9/P3×1）、C8×7（P1×1/P2×5/P3×1），无遗漏、无多记；严重度合计 P0×1/P1×22/P2×27/P3×4 与文件头声明一致。
- **P3 本体标注一致性 ✓**：4 条 P3 本体（C1-014/C1-015/C7-012/C8-006）对应的 C10-030/C10-039/C10-019/C10-034 全部带「随本体合并处置时可降 P3」标注，无一遗漏。
- **54→39 登记覆盖闭合 ✓**：按各条目标题+失效面枚举声明口径，54 个缺陷 ID 全部有唯一主归属登记（29 单案 + 6 双案合并 + 3 三案合并 + 1 四案合并 = 39 条）；失效面枚举中的其余命中均为「正交面 X（C10-0yy）」式指针或跨单元前缀同号（如 `nop-persistence-rw-C1-003`），不构成重复计数。
- **判定分布自洽 ✓**：假保护 30 / 无测试 24 逐行清点与声明一致；「会红 = 0」的推理前提（54 条均未修复 + 现有套件绿 ⇒ 确定性断言正确语义的测试不可能存在）经反证校验成立，且与 RP-1/RP-2 两处"绿但钉住缺陷前提语义"的实测状态自洽。

### 二、抽查结果（回码验证，12 行矩阵 + 2 处反向钉死 + 3 处门控）

**全部 12 行抽查判定维持，0 误判**：

| 类别 | 抽查行 | 验证结论 |
|---|---|---|
| 假保护 | C1-002 | TestMigrationEngine.java:202-251 注册测试确为单线程（全文件无 Thread/ExecutorService/latch），注册竞态零覆盖判定正确 |
| 假保护 | C1-011 | 6 个 PubSub 测试全部串行（:789-791 suspend→cancel→resume 顺序原文核对一致），断言引用文本逐字吻合；类级 docker-gated |
| 假保护 | C1-012 | TestNosqlCache 恰 2 个测试、仅断言 getAsync/getExAsync 路由（断言文本逐字吻合），computeIfAbsent 覆写零测试 |
| 假保护 | C1-007 | e2e :95 确断言 "restart-required" 降级（rebuildRouting 不执行）；三处 setShardSelector 测试（AbstractOrmTestCase:59 / TestShardBindingValidation:65 / TestShardAutoUpgrade:101）均为单线程 set-then-get |
| 假保护 | C7-002 | TestMigrationPhase.java:24-80 合法 6 边+非法 6 边+终态与矩阵描述一致；确无 X→X 自环断言（反向钉死排查结论复核通过） |
| 假保护 | C8-007 | forEachEntry 5 个功能测试在 :489-547，无 MATCH/上限断言；"大数据集"用例实为 50 条（小数据集判定成立） |
| 无测试 | C1-004 | `grep -rln IncrementalJobManager --include=*.java \| grep /test/`（全仓）复现 0 命中 |
| 无测试 | C3-002 | `grep getFailure nop-orm-migration/src/test` 复现 0 命中 |
| 无测试 | C3-003/C1-005 关联面 | `runIncremental`、`stopAll` 在 nop-orm-migration src/test 复现 0 命中 |
| 无测试 | C7-012 | `ledgerRowCount` 仅 AbstractMigrationE2ETestCase:132 helper（服务 lag 观测），无保留策略断言 |
| 无测试 | C8-003 | TestTdSqlHelper 批 SQL 生成测试存在（testGenBatchInsertSubTableSqlValues）、TestTdEnginePersist.java:16 整类 `@Disabled`、`chunk\|split\|batchSize` 测试 0 命中，全部复现 |
| 无测试 | C8-004 | rpc 批写测试全部单 action（`List.of(saveAction)` / `List.of(deleteAction)`），批量边界零断言 |

**Docker 门控判定核实 ✓**（gating 机制真实存在，非推测）：TestLettuceNosqlService.java:57-58 与 TestLettuceClusterService.java:51-52 均挂 `@EnabledIfSystemProperty(nop.test.docker.enabled)` + `@Testcontainers(disabledWithoutDocker = true)`；TestTdEnginePersist.java:16 `@Disabled`。S2 的 984 行行数、10 处 `Thread.sleep`、唯一泄漏钩子 testClientResourcesNotLeakedAcrossStartStop（:959 起）均逐项核实。

**反向钉死方向判断复核 ✓**：
- **RP-1 维持**：testRecordMigrationReplacesExistingRecord（:253-278）注释与断言原文核对一致（"must be replaced, not rejected with a primary key violation" + assertDoesNotThrow）；「修复不得走主键冲突即拒绝方向」的约束推导正确。
- **RP-2 维持**：testLagZeroWhenNothingAdvances（:42-47）/testLagZeroWithStaticOffsetsAndLedger（:90-100）确将「静止=追平」断言为 true；「probe 层修复须改写两测试、编排器层消费 failure 则不触红」的层位约束推导正确。

### 三、修正项（证据性勘误，均不改变判定与判级）

1. **前言统计修正**：「新发现 51 / 已知未修复 3」应为 **新发现 48 / 已知未修复 6**（已知未修复 6 条 = C1-013、C3-007、C6-004、C6-005、C7-011、C7-012，见五维度现状标注汇总）。「54 条均为未修复」前提不变，会红=0 推理不受影响。
2. **S1 叙事数字修正**：「23 条'无测试'发现中 16 条集中于此」与矩阵自相矛盾——矩阵无测试合计 **24** 条，其中 nop-orm-migration 范围 **12** 条（C1-004/009、C3-001/002/003/011/012、C6-001/002/003、C7-001/012）。「增量链零测试区」结论本身成立且被抽查加强。
3. **C10-002 证据块转写勘误**：`grep -rln startFallbackChains … | grep /test/` 实测**全仓 0 命中**（含 nop-ai-gateway）；证据块所列 ai-gateway TestChannelConnectorManager 命中实为 `stopAll` 的匹配（不同符号）。无测试结论因此**加强**而非削弱。
4. **C10-003 / 矩阵 C6-003 行 grep 计数勘误**：`grep join\|interrupt nop-orm-migration/src/test` 实为 **2 处命中**（TestIncrementalLagProbe:59/:78 的 `Thread.currentThread().interrupt()` 样板，位于测试自身 sleep catch 内）。「无终止验证测试」判定不变。
5. **C10-035 测试清单描述勘误**：forEachEntry 第 5 个测试为 testForEachEntry_LargeBatch（50 条数据），非「回调异常」；「小数据集、无 MATCH/总量上限断言」判定不变。

### 四、判定统计与最终结论

- **矩阵行**：维持 54 / 修正 0 / 驳回 0（12 行抽查全部维持，其余 42 行经格式、证据锚点与对账一致性核查）。
- **缺保护登记**：**保留 39 / 驳回 0**。39 条全部 ≥10 行、附录 A 12 字段齐备（文件/证据片段/严重程度/并发场景/触发时序/现状/风险/建议/失效面枚举/信心水平/误报排除/复核状态）、编号连续 C10-001…039；检索纪律抽查 5 条（C10-002/003/025/030/005 关联面），4 条 grep 声明完全复现，1 条证据转写不精确但结论成立（修正项 3/4）。
- **弱反向钉死**：RP-1、RP-2 均维持，方向约束描述与测试断言原文一致。
- **最终保留缺保护登记数：39 条（C10-001…C10-039，全部 P2）**，其中 4 条（C10-019/030/034/039）保留「随本体合并处置可降 P3」标注。
- **复核终止信号**：C10 审计结论整体成立——54/54 保护力不足、增量链子系统零测试区与 nosql docker-gated 保护力归零两大系统性风险（S1/S2）经独立回码验证属实。修正项 1–5 为叙事/转写层面勘误，随本节存档，原矩阵与登记正文按「复核不改正文」纪律保持原样，以本节为准。