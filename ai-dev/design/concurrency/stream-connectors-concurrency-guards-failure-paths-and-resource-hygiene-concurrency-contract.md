# stream-connectors 并发守卫、失败路径与资源卫生并发契约（CRA 8.44 R4-04 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立评审记录见「评审记录」节）
> Last Reviewed: 2026-11-07
> Source: plan 2537（[2537-cra-phase8.44-r4-04-connectors-concurrency-leak.md](../../plans/2537-cra-phase8.44-r4-04-connectors-concurrency-leak.md)，CRA Phase 8.44 R4-04 1.2 连接器并发正确性+失败路径泄漏束 G07+G08，11 工单全 P2）；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/`（[merge-adjudication.md](../../audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/merge-adjudication.md) §2.2 独立案 :72-82 + §2.4 :105 + §4.4 :226/:229 + §五 修序建议 8 + C3-async-chains.md C3-005/006/010/011/012 + C6-resource-lifecycle.md C6-005/006/007/011 + C7-long-running.md C7-003/010 + C10-test-protection.md §2 P2 侧记）；批次定义 = batch-proposal §二.4 R4-04（:119）+ §一 E1–E6；既有裁定权威 = [stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md)（2511 裁定①–⑧）+ [stream-connectors-metrics-identity-avro-lifecycle-cepbounds-and-2pc-guards-concurrency-contract.md](stream-connectors-metrics-identity-avro-lifecycle-cepbounds-and-2pc-guards-concurrency-contract.md)（2536 裁定 A–E）——两份均**仅引用不重建**；范式先例 = R03 [stream-buffer-unification-and-failover-flow-concurrency.md](stream-buffer-unification-and-failover-flow-concurrency.md) §3.6；fd 计数腿形态参照 = plan 2522 `TestFileDownloadSinkClose`（nop-http-client-jdk，形态复用零文件复制）
> Owners: 裁定 A（取消响应域：W-01/W-03——wakeup 通道 + runThread interrupt + face② 声明 + E5 取消时延声明面）；裁定 B（debezium 信号与重入域：W-02/W-04——in-scope 守卫与 M1 unregister 所有权判定 + CAS typed 与 C10-015 same-diff 改写）；裁定 C（停机链与资源清理域：W-05/W-06/W-07/W-09——双桥 close clear-first+interrupt、openDB 失败清理 helper、restore 两段关闭、walk TWR 与 fd 腿）；裁定 D（磁盘残留与 TTL 生命周期域：W-08/W-10/W-11——孤儿清扫挂点与双白名单、restore 残留清扫、TTL sweep 移植）；裁定 E（测试缝总表：红①–红⑪ 驱动器 + compile-red 存证载体 + 零回退钉全列 + C10 映射）。**裁定编号 = Phase 1 起草指令分组**（本分组将 plan Goals 预设 A–F 的 W-06/W-07/W-09 并入裁定 C、W-08/W-10/W-11 并为裁定 D、红钉总表定为裁定 E——12 项内容一一全覆盖，映射见「工单→裁定映射与分组登记」节；分组调整随本登记生效）。文件命名裁定：`stream-connectors-concurrency-guards-failure-paths-and-resource-hygiene-concurrency-contract.md`（取代 plan Goals/Scope/Phase 1 Targets 拟名 `...cancel-reentrancy-stop-chain-rocksdb-native-cleanup-and-failure-path-residue-...`——「concurrency-guards」覆盖 W-01/W-02/W-03/W-04/W-05 守卫与取消面、「failure-paths」覆盖 W-04/W-06/W-07 失败路径语义、「resource-hygiene」覆盖 W-08/W-09/W-10/W-11 资源卫生面；命名调整已在 plan 2537 三处登记）。
> 行号锚 = live 基线 `51fe88f737`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值——plan 起草基线 `22ed724af5` → 本文基线 `51fe88f737` 唯一 commit 为 docs(plan) 提交（plan 2537 起草+draft review R1），`git diff 22ed724af5..51fe88f737 -- nop-stream` **零 diff** 亲证（11 工单站点锚逐行抽验命中，见 K 节）；后续漂移以 live 为准并登记于 plan 勘误。与 plan 文本不一致的 live 核证事实集中登记于「plan 输入勘正（K 节）」，裁定以本文为准。

本文是 plan 2537 全部 11 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序论证）、被否决的备选、零触碰边界。三范式/溢出语义/保留参数结论引用不复制——SSOT 在既有设计文档。全部裁定零产品侧新配置文件载体：错误码全部复用 `NopStreamErrors` 在位码（`ERR_STREAM_STATE_ERROR`/`ERR_STREAM_CONFIG_ERROR`/`ERR_STREAM_INVALID_ARG`/`ERR_STREAM_JOB_EXECUTE_FAILED`/`ERR_STREAM_CHECKPOINT_ERROR`——2536 K6 亲证在位），零新错误码、零 `_` 生成物、零 orm、零平台设施本体。

## 既有裁定引用核对（引用不重建——软依赖 8.17 已解除 + 紧邻前批 8.43 已收口 + 对齐纪律「既有裁定引用条款」授权）

2511 裁定①–⑧（[stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md) §3.1–§3.9）与 2536 裁定 A–E（[stream-connectors-metrics-identity-avro-lifecycle-cepbounds-and-2pc-guards-concurrency-contract.md](stream-connectors-metrics-identity-avro-lifecycle-cepbounds-and-2pc-guards-concurrency-contract.md)）为本批可引用既有裁定，引用边界逐条确认如下；本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。

| 既有裁定 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| 2511-① gauge 锁内取值（readCurrent） | 零直接站点（recorder 本批零 diff）；W-06 同文件邻接 | **红线 1**：`readCurrent` 与 gauge 闭包取参点零改写；backend :319-320 register 接线零触碰（W-06 openDB 失败清理面在 :301-304 catch 内，不触接线语义） |
| 2511-④ 2PC 保留策略（face `epochledger`，R01 SSOT 成员） | W-08 同文件（FileTwoPhaseCommitSink） | **红线 2**：retention 窗口常量（:90-97）/`computeRetentionEpochWindow`/`pruneExpiredEntries`（:565-587）/commit 顺带裁剪链（:477）零触碰——W-08 清扫为 temp 文件面，manifest **只读载入**做白名单，零 manifest 写路径、零保留窗口参数触达；`TestFileTwoPhaseCommitSinkManifestPrune_C3_002` 零改写 |
| 2511-⑤ 增量缺省 true+规模阈值守卫 | W-06/W-10/W-11 同文件基线（RocksDBKeyedStateBackend） | **红线 3**：`incrementalCheckpointEnabled` 缺省 true（:125）与 `snapshot-materialize-max-entries` 守卫（snapshotState :930-941）零重开零触碰；W-11 只在 setCurrentKey 加触发点、W-10 只在 restoreIncremental 入口加清扫——两面均不触快照分派与阈值语义 |
| 2511-⑥ last-emitted 位点协议 | W-05 同文件前提（DebeziumCdcSourceReader） | **红线 4**：`LastEmittedOffsetTracker` 全链（function :128 / reader :77 起/:347 seedFromOffsetMap）零触碰——W-05 只触 close 路径（:353-377）；`TestDebeziumLastEmittedOffsets_C8_001` 零改写 |
| 2511-⑦ kafka 事务裕度 | W-01 同模块零交集 | 仅裁⑦落地面零触碰（`TestKafkaTwoPhaseCommitSinkLifecycle`/`TestKafkaTwoPhaseCommitSinkTransactionMargin` 零改写）；W-01 与裁⑦零共享站点 |
| 2511-⑧ 测试缝与零回退钉 | 全批测试面 | **红线 5**：2511 八钉+改写类新钉（压窗/反转/挂死三件/保留双钉/驻留/last-emitted/裕度）钉内断言全批 diff 零触碰；`TestBatchLoaderSourceFunction` 零触碰（C10-016 归 R-G——W-03 新钉禁入该文件）；挂死注入/压窗/latch 形态为裁定 E 新钉的形态基准 |
| 2536-A.1/A.2 gauge 身份与告警 | 零站点（recorder 零 diff） | recorder 文件本批零 diff 维持；backend :319-320/:1175/:1184-1186 接线区零触碰（W-06 邻接面） |
| 2536-B 双桥 maxRecordBytes byte-bound 守卫 | W-05 同文件同方法族 | **红线 6**：`MessageSourceReader.collect` 守卫段（:306-322）与 `DebeziumCdcSourceReader.collect` 守卫段（:447-462）零触碰——W-05 只触 close 路径（MSR :228-245 / DCR :353-377）与 interrupt 兜底；`TestMessageSourceReaderByteBoundR403`/`TestDebeziumCdcReaderByteBoundR403` 零改写 |
| 2536-C.4 manifest 锁 tryLock 预算 | W-08 同文件 | **红线 7**：`withManifestLock`（:623-694）/`manifestLockTimeoutMs`/四旋钮（`maxEpochLines` 检查 :381-389、`maxPendingEpochs` 检查 :417-425）零触碰——W-08 清扫挂 beginTransaction、不进锁协议、manifest 只读；`TestFileSinkEnvelopeLockR403` 零改写 |
| 2536-D.2 seedIndexFromHandles | W-10 同方法区 | **红线 8**：`restoreIncremental` 的 seed 调用序（:1146-1151）零触碰——W-10 清扫插入点在 restoreRangeInto 调用**之前**（:1145 区），不移动/不改写 seed 调用与其注释；`TestRocksDBSnapshotHashPolicyR403`/`TestRocksDBStateMigrationStreamingR403` 零改写 |
| 2536-E 红绿驱动器纪律 | 裁定 E 全盘沿用 | orTimeout 禁令/闩带 ≤30s 且收尾必放闸/`_tmp/` 总册+逐 trial 日志/同号防混（`*R404` 后缀）/结构钉+变异探针诚实留痕禁伪绿/E-2536-7+Major-D1 教训（原始日志存证+compile-red 存证载体明文——见裁定 E） |
| 2503 停止契约（C3-014 产物） | W-02/W-04/W-05 同文件零回退 | **红线 9**：`verifyEmbeddedEngineTerminated`（:355-382）/cancel 终止性链（:394-410）/`TestDebeziumCdcStopVerification_C3_014` 零改写——W-05 Debezium 面 restructure 后 verify 调用保持在 join 成功路径（K2 落款）；R5-CON-03 注释块（:259-263）零弱化、`TestDebeziumCdcSourceErrorPropagation` 语义零弱化 |
| AR-03 unnamed fail-fast（:543-549） | W-02 守卫口径 | 引用其对同名桶 fail-fast 口径（`ERR_STREAM_CONFIG_ERROR`+配置指引 param）——W-02 守卫为**新 face（同名非 unnamed）**，零改写既有 unnamed 分支本体 |
| R03 范式（§3.6 锁内重活外移） | W-05 clear 移出 throw 影子 | 同构投影引用不复制：超时抛错的**影子范围**收敛——clear（无副作用动作）先于/独立于 typed 抛错执行，锁内/关键区内只留状态机变更的范式精神，零范式文本复制 |
| R07 fd 计数形态（plan 2522 `TestFileDownloadSinkClose`） | 红⑨ fd 腿 | 形态参照（`UnixOperatingSystemMXBean.getOpenFileDescriptorCount()` delta + `@EnabledIf` 可用性门 + 预热 + N 次迭代增量断言）——复用形态不复制文件，nop-http 通道与本批零交集 |
| 2535/2510 纪律族 | 全批 | 2535-B.2「诚实来源」论证纪律（W-03 driver 响应差异声明的声明-边界区分）、2510-⑪ 九测试缝惯例（包内可见注入缝/手工桩/latch/探针 subclass）——引用即约束 |

**既有裁定零重建声明**：本批无任何新 face token、零 R01 SSOT 成员行、零 SSOT diff（W-08 清扫参数为零旋钮形态——见裁定 D）；2511/2536 裁定结论如需修订走其各自设计文档修订，不回写本批。

## plan 输入勘正（live 亲读核证 @ `51fe88f737`，裁定以本节为准）

| # | plan 2537 表述 | live 事实（file:line @ `51fe88f737`） | 对裁定的影响 |
|---|---|---|---|
| K1 | W-09「TWR 写法正例 = SnapshotStrategy :189 先例」（1.2 审计基线行号） | 2536 new-only 插入使行号漂移——live 六处 TWR 目录流 = SnapshotStrategy **:162/:243/:276** + Restore **:136/:191/:277**（逐一亲证 `try (Stream<Path>…)`/`try (var …)` 形态不变） | 裁定 E.2 写法对齐锚更新为 :162（SnapshotStrategy 首处）；机制与形态零漂移 |
| K2 | plan W-05 行 DCR close 序「:360 join/:361 isAlive/:362-364 throw 先于/:375 clear」 | 亲证成立且**补全**：:362-364 throw 与 :374-375 clear 之间还有 2503 产物 `verifyEmbeddedEngineTerminated("reader-close", true)`（:367-373） | W-05 restructure 的硬约束具体化：verify 调用保持在 **join 成功路径**、语义零改写（join 超时路径先 typed 抛出，不再到达 verify——超时即 runner 未终止，verify 必然 ghost 失败，提前抛出不损失任何信号）——红线 9 的落款依据 |
| K3 | plan W-11「时钟缝（可注入门控 vs 反射推进）归裁定」——无既有先例指引 | **in-repo 先例在位**：core `TestMemoryStateTtlSweep` :116 以 `setTtlSweepIntervalMillis(1)`（「smallest positive interval (1ms of nanoTime)」）驱动 sweep 门控，零产品侧 nanotime 缝、零反射；Memory 后端门控本体即裸 `System.nanoTime()`（:237） | 裁定 D.4 时钟缝唯一分支 = 微间隔驱动（1ms）——与 Memory 形态移植（含测试缝惯例）同构，反射推进否决 |
| K4 | plan W-11「setTtlSweepIntervalMillis 接线」载体未核证 | RocksDB 后端**已有** `ttlClock` 字段（:208）与 `setTtlTimeProvider`（:802-804）——过期判定钟缝在位；缺失的只有 sweep 门控三件（interval 旋钮/`lastTtlSweepNanos`/`anyTtlBound`）与 setCurrentKey 挂钩 | W-11 移植半径收敛为「门控三件+挂钩+setter」；过期钟缝零新增（FakeClock 测试惯例直接复用——`TestRocksDBStateTtl` :48-68 亲证） |
| K5 | plan W-08「恢复入口（首次 commit 前/恢复链）」挂点两说未定夺 | 基类 `TwoPhaseCommitSinkFunction` 恢复链亲证：`setPendingCommits`（:90）→ `restoreFromEpoch`（:274，非 durable 恢复条目 abort :312）→ **`beginTransaction`（:321，恢复链最后一步）**；fresh run 亦经 beginTransaction（:83） | 裁定 D.1 挂点唯一分支 = `beginTransaction()` override：白名单（恢复 pending）已就位、abort 清理已完成、fresh/恢复两形态统一覆盖、距首个 saveState 有整段无竞争窗 |
| K6 | plan W-07 联动注记「openReadOnlyTyped 失败路径 handles 半成品无人关——C6-005 同族兄弟」（audit C6-006 失效面③） | 亲证：`openReadOnlyTyped`（:209 调用点）open 中途抛错时 handles 已部分填充且无人关——该 face 不在批次 11 finding 内 | 裁定 C.2 两段式 finally restructure **结构性闭合**该兄弟面（handles 关闭在 finally 中先于 src、且 src 非 null 才关）——同 diff 结构性收益，非顺带（同工单站点方法内），登记于裁定 C.2 |
| K7 | plan W-01「probe 面 :376-386」 | 亲证 `checkConnection` 方法 :375-385（`@Override` :375、签名 :376——±1 签名行偏移）；probe 持**独立** try-with-resources consumer（:377-384） | 裁定 A.1 probe 零改写裁定维持；probe 阻塞上界 = max.block.ms（声明边界，E5 承载）；C7-011 P3 相邻零触碰 |
| K8 | plan 勘误 4/m2-5 基线签名 | 亲证口径：执行 **4481 tests / 1F[F1 solo 三联绿在案] / 0E / 20S**——core 2008 1S / cep 394 / flow 315 / connector-jdbc 72（71+1 第二次 surefire 执行块）/ debezium 82 6S / batch 62 / kafka 26 / rocksdb 153 / connector 116 / runtime 1253 1F 13S | 裁定 E 零回退签名基准 = 4481（+新钉增量）；F1 按 E3 判据处置（并发复现→solo 三联绿归因维持，禁静默重跑） |
| K9 | plan W-05「byte-bound 守卫段 MSR :307-321 / DCR :457-463」 | 亲证：MSR collect 守卫注释+分支 = **:306-322**（if :313/throw :314-321）、DCR = **:447-462**（if :451/throw :452-461）；put :325/:464、interrupt 分支 :326-330/:465-469 全部命中 | 零触碰锚±1 注释收录偏移，锚本体不变（红线 6） |
| K10 | plan 红⑧「注入合法在途 temp 反例腿[m2-8]」构造基础未核证 | 亲证：temp 名 = `.epoch-{id}{subtaskSuffix}.tmp`（:955-957），final = `epoch-{id}{suffix}.txt`（:947-949），manifest 键 = `{id}`/`{id}.sK`（:594-610），manifest temp = `manifest.properties{suffix}.tmp`（:853-854）——**subtask 后缀（`.sK`/空）为并行隔离维** | 红⑧ 反例腿唯一构造 = 他 subtask 后缀 temp 注入（后缀过滤结构性不命中）+ 恢复 pending 白名单 temp 注入（路径白名单不命中）双 leg |
| K11 | plan C10-015 面「`TestDebeziumResourceManagement` :64 断言」 | 亲证：缺陷载体 = **两个 catch-all + 成功标志折叠**——runner1 catch-all（:47-49）/runner2 catch-all（:58-60）把「静默 return」与「typed 抛错」折叠为同一 `secondRunReturned=true` 观察 + :64 断言文本钉住「should return immediately due to reentrancy guard」 | 裁定 B.2 改写半径 = :41-64 区（标志语义反转为 Throwable 判别），非单行——与 C10 侧记「catch-all 折叠」口径一致 |
| K12 | plan W-10「pruneOldCheckpoints 仅匹配 cp-{digits}」 | 亲证：`pruneOldCheckpoints`（SnapshotStrategy :241-253）仅 `name.startsWith("cp-")`+数字解析（:247-251）——`*-restore-range-*` 无保留策略扫描成立 | 裁定 D.3 跨 id 清扫为唯一新增保留面，prune 本体零触碰 |

## 工单→裁定映射与分组登记

| 工单（finding ID） | 裁定 | 承载节 | plan Goals 预设分组对照 |
|---|---|---|---|
| W-01（1.2-C3-005） | A.1 | kafka wakeup 通道 | plan-A 同点位 |
| W-03（1.2-C3-010） | A.2 | batch runThread interrupt + face② 声明 | plan-A 同域 |
| W-02（1.2-C3-006，载体面→R4-24） | B.1 | in-scope 守卫 + M1 所有权判定 + 红② 存活反例腿 | plan-B 同点位 |
| W-04（1.2-C3-011，C10-015 同 diff） | B.2 | CAS typed + 断言反转 | plan-B 同点位 |
| W-05（1.2-C3-012，C6-002/003 残面唯一锚点） | C.1 | 双桥 close clear-first + interrupt 兜底 | plan-C 同点位 |
| W-06（1.2-C6-005） | C.3 | openDB 失败清理 helper | plan-D 并入本组 |
| W-07（1.2-C6-006） | C.2 | restore 两段关闭（先 handles 后 src） | plan-D 并入本组 |
| W-09（1.2-C6-011，fd 复用 R07 先例） | C.4 | walk TWR 双点 + fd 腿形态 | plan-E 并入本组 |
| W-08（1.2-C6-007，双 face 绑定） | D.1 | 孤儿清扫挂点 + 双白名单 + 反例腿 | plan-E 同点位（W-09 移出） |
| W-10（1.2-C7-003，与 W-07 同文件联动） | D.3 | restore 残留清扫 + deleteRecursively 兜底扩展 | plan-D 同点位 |
| W-11（1.2-C7-010，双面绑定） | D.4 | TTL sweep 移植 + 时钟缝 + :422 兑现 | plan-D 同点位 |
| 红①–红⑪+零回退钉全列+C10 映射 | E | 测试缝总表 | plan-F 同位 |

分组调整声明：plan Goals 预设「裁定 A–F」六组（A=取消响应/B=debezium 信号与重入/C=停机链[W-05]/D=rocksdb native[W-06/W-07/W-10/W-11]/E=磁盘残留[W-08/W-09]/F=钉总表）；本文按 Phase 1 起草指令并为五组——W-06/W-07/W-09（失败路径资源清理同族）并入裁定 C、W-08/W-10/W-11（磁盘残留+TTL 生命周期同族）并为裁定 D、红钉总表定为裁定 E。12 项裁定内容一一全覆盖，plan Phase 1 Exit Criteria 各内容项的承载节在上表可定位；分组调整随本登记生效（plan 侧回填随命名调整登记一并完成）。

## 裁定 A 取消响应域（W-01/W-03）

### A.1 W-01：KafkaSourceFunction.cancel 的 consumer.wakeup 通道（1.2-C3-005）

**决策**：**「volatile consumer 引用 + cancel 单发 wakeup + run 侧 armed 自保护 + WakeupException 双分支判别」唯一分支**：

1. **字段化形态 = `private volatile KafkaConsumer<String, byte[]> consumer;`**（非 AtomicReference）。run() 在 consumer 创建后（:246 try-with-resources 资源表达式改两步：`KafkaConsumer<String, byte[]> c = createConsumer(); this.consumer = c; try (c) {…}`——资源仍 TWR 关闭，字段仅发布可见性）赋值，finally 末尾置 null（consumer 关闭后清除引用——置 null 与 close 的交错无害：对已关闭 consumer 调 wakeup 为无害 no-op，wakeup 标志无人消费）。
2. **cancel() 形态**：`running = false; KafkaConsumer<String, byte[]> c = this.consumer; if (c != null) c.wakeup();`——单发、无重试、无后台线程。
3. **run 侧 armed 自保护（早取消窗闭合）**：run() 赋值字段后、`partitionsFor`（:247）前置 `if (!running) { consumer.wakeup(); }`——cancel 先于 consumer 存在而触发的竞窗（wakeup 无对象可打）转化为首个阻塞调用的立即 `WakeupException`，不再阻塞至 max.block.ms。
4. **WakeupException 双分支判别（吞异常语义唯一定夺）**：run() catch `WakeupException`——`running == false`（cancel 发起）→ **优雅退出**（return，任务正常终态——取消语义 = 停止而非失败）；`running == true`（非 cancel 的意外 wakeup——本类外零调用方，实践中不可达）→ 包装 `StreamException(ERR_STREAM_STATE_ERROR)` typed 上抛，**零静默吞掉**（rule 24）。
5. **consumer 创建缝**：新增 `protected KafkaConsumer<String, byte[]> createConsumer()` 默认直调既有私有 `newConsumer()`（:224，auto-commit 守卫等全部逻辑零移动）——红① 探针 subclass 的注入缝（同族形态先例 = `DebeziumCdcSourceFunction.createMessageSource` protected seam，:236-239 亲证；既有 `*ForTest` 包内钩子惯例 :398-418 同族）。
6. **probe 面（checkConnection :375-385）零改写**：probe 为提交前编排步骤、自身即编排动作、`ConnectivityCheckable` 无 cancel 原语——无「被取消的编排」存在；其阻塞上界 = max.block.ms（有界、TWR 干净）立为**声明边界**（E5 承载），零产品 diff；C7-011 P3 文档面相邻零触碰（红线：不顺手修）。

**取消时延声明（E5 载体文案）**：稳态取消时延 ≤ poll 间隔 200ms（既有；限速切片窗 MAX_SLEEP_SLICE_MS=100 与 seek/assign 窗同为 max.block.ms 系 Kafka 阻塞点——wakeup 机制同样覆盖[MN-5 完备句]）；启动期元数据拉取（`partitionsFor`）阻塞中取消时延 ≤ wakeup 传播（即时）——修复前上界 max.block.ms（缺省 60s、用户可放大）消除；`checkConnection` 提交前探测阻塞上界 = max.block.ms（声明边界，无取消语义面）。

**理由（触发时序论证）**：缺陷时序——T1 任务线程 run() 进入 `partitionsFor`（KafkaConsumer 唯一阻塞原语，`max.block.ms` 缺省 60s 可放大）→ T2 用户取消 → cancel 仅置 `running=false`（:328）→ T3 KafkaConsumer 对线程中断不响应（2511 期 R3 复核证实——SubtaskTask interrupt 不解除 poll/partitionsFor）→ 取消挂起至 max.block.ms，停机/恢复编排被拖住，恢复窗口内旧任务不退场拉长 fencing 窗。post-fix 时序——T2' cancel 置位 + 对在途 consumer 单发 `wakeup()` → T3' 阻塞中的 `partitionsFor`/`poll` 立即抛 `WakeupException` → run 双分支判别优雅退出，取消时延从「≤ max.block.ms」收敛为「即时」。并发面：wakeup 是 KafkaConsumer 设计内的跨线程单发原语（线程安全、幂等无害）；volatile 引用满足「单写者（run 线程）+ 读侧最新值可见」——cancel 读到旧引用最坏情形 = 对已关闭 consumer wakeup（无害 no-op），读到 null = 不打（armed 自保护已闭合早取消窗），**两方向竞窗均无害**，无需 CAS。

**被否决的备选**：
- **AtomicReference 消费引用**：本形态无 read-modify-write 需求（wakeup-on-stale-reference 天然无害，见上）——AtomicReference 零语义收益纯样板；不采。
- **interrupt 任务线程替代 wakeup**：KafkaConsumer 不响应线程中断（2511 R3 亲证）——无效原语；不采。
- **probe 面同改（wakeup 机制复用）**：probe 无 cancel 原语可服务（`ConnectivityCheckable` 签名即契约）——改则须发明取消通道=超工单；有界声明（max.block.ms）+ 零 diff 为诚实形态；不采。
- **max.block.ms 配置钳制/缺省收紧**：改用户可见 Kafka 配置语义，超工单半径；不采。
- **scheduled wakeup/看门狗线程**：线程生命周期与 cancel 竞态复杂度无对价（单发 wakeup 已闭合）；不采。

**零触碰边界**：`newConsumer()` 本体（:224-241，auto-commit typed 守卫等）零移动零改写；poll 循环/限速/drain 语义零改写；`checkConnection`（:375-385）零 diff；C7-011 相邻 P3 零触碰；`TestKafkaSourceFunctionSharding`/`TestKafkaTwoPhaseCommitSinkLifecycle`/`TestKafkaTwoPhaseCommitSinkTransactionMargin` 零改写（红① 为新测试类追加）。

### A.2 W-03：BatchLoaderSourceFunction.cancel 的 runThread interrupt（1.2-C3-010）

**决策**：**「volatile runThread 记录 + cancel 单发 interrupt + 身份守卫清空」唯一分支**：

1. **字段**：`private volatile Thread runThread;`。run() 在 lifecycle 复位（`this.running = true` :103）后记录 `this.runThread = Thread.currentThread();`；finally（:144-148 扩展）内**身份守卫清空**：`if (this.runThread == Thread.currentThread()) { this.runThread = null; }`——防止旧 run 的 finally 抹掉同实例 region-restart 新 run 的注册（该实例无重入守卫——C3-011 失效面③已登记的独立面，本批零顺手加守卫）。
2. **cancel() 形态**：`running = false; Thread t = this.runThread; if (t != null) t.interrupt();`——单发、无重试。
3. **中断递送语义（driver 响应差异声明，E5 载体）**：interrupt 递送 ≠ 解除——loader 为 JDBC 形态时多数驱动经 `Statement.cancel`/socket 中断响应 interrupt；不响应 interrupt 的 loader 实现下取消时延上界 = 在途 `loader.load` 时长（**声明边界**）。run 循环批间/记录间 `running` 检查（:115/:132）维持为细粒度响应点（现状良好面零改写）。
4. **中断标志残留（watch-only 声明）**：cancel 与 run 收尾竞窗内的中断递送可能在任务线程上遗留中断标志——任务线程生命周期归 core 框架（SubtaskTask 停止路径本就递送 interrupt），残留标志与框架停机语义一致，不clear（他线程代清他线程中断标志为竞态源）；残余登记 watch-only。

**face②（BatchLoaderSourceReader.pollNext 内联 load :170）owner doc 取消时延上界声明（E5 载体文案）**：FLIP-27 reader 的 `loader.load` 在 mailbox 任务线程内联执行——取消邮件与 load 阻塞点同线程，无解除原语（`IBatchTaskContext` 无取消钩子亲证，nop-batch-core :25 区）；**声明：reader 面取消时延上界 = 单次 `loader.load` 时长**（legacy 函数面 interrupt 通道不覆盖 reader 面——两载体不同线程模型，如实分立声明）。

**理由（触发时序论证）**：缺陷时序——T1 任务线程在 `loader.load(batchSize, chunkContext)`（:116，唯一阻塞点）在途执行长查询 → T2 用户取消 → cancel 仅置 `running=false`（:153）→ T3 load 不响应 → 取消生效推迟至 load 自然返回（查询时长无上界）。post-fix——T2' cancel 置位 + 对 run 线程单发 interrupt → T3' 响应 interrupt 的 loader 在预算内抛出/返回 → run 退出；不响应的 loader 由声明边界承载（递送通道已建立是本工单的本体面——「设计缺口而非漏调用」的审计定性即通道缺失）。并发面：volatile Thread 引用满足跨线程可见；identity 守卫清空消除旧 finally 误清新注册；interrupt 与 loader close（finally）的交错安全（close 期间再收 interrupt 无资源语义影响）。

**被否决的备选**：
- **`IBatchTaskContext` 取消钩子**（audit 建议②）：nop-batch-core 跨模块改动——**条件申报③明示禁选点**（plan PA 表行 3；如裁定采须四件链申报）；预设零触发，不采。
- **可取消 loader 包装器/Future 化**：改 `IBatchLoaderProvider`/`IBatchLoader` 平台接口契约（owner doc WI2 明示「不改平台接口」）；不采。
- **reader 面（face②）同装 interrupt**：mailbox 同线程模型无独立 run 线程可记录——interrupt 自线程=无效；不采（声明承载）。
- **Thread.stop**：deprecated/破坏性，禁路线。

**零触碰边界**：`TestBatchLoaderSourceFunction` 零触碰（C10-016 归 R-G——W-03 新钉落新测试类 `*R404`）；skip-shortfall typed 守卫（:117-130）与 seek/负 offset 拒绝语义零改写；`BatchLoaderSourceReader` 本批仅触 close 面（C.5 face③），pollNext/nextRecord 零改写。

## 裁定 B debezium 信号与重入域（W-02/W-04）

### B.1 W-02：失败监听同名覆盖 in-scope 守卫 + M1 unregister 所有权判定（1.2-C3-006；载体面零 diff 红线）

**决策**：**「静态 live-name 认领注册表 typed fail-fast + 认领先于 try/finally 结构位 + 两参条件移除」唯一分支**：

1. **认领注册表**：`DebeziumCdcSourceFunction` 新增 `private static final ConcurrentHashMap<String, Object> LIVE_CONNECTOR_CLAIMS = new ConcurrentHashMap<>();`（in-scope 模块内静态面——零载体触碰；认领值为 per-run 认领令牌对象）。
2. **守卫点 = resolveConnectorName 之后、try 块之前**（结构位即所有权判定；**[MN-1] `resolveConnectorName()` 调用（现 try 首语句 :257）随守卫一并移出 try 块——否则守卫落入 try 内、被拒实例 finally 存在、M1 破功**；**[MJ-1 pre-try 抛出复位规则] 凡 CAS 成功后的任何 pre-try 抛出（守卫拒绝/搬移后的 resolve AR-03）抛出前先复位自身 runEntered=false——新增站点不触 :99/:340 零触碰面；不采 claim 前移（claim put 成功后 CAS 抛出无 finally 会泄漏 claim 致同名永久误拒）；红② 增补「被拒实例可重试」并列不变式**）：run() 内 `connectorName = resolveConnectorName();`（移出 try 后）后立即 `Object claim = new Object(); if (LIVE_CONNECTOR_CLAIMS.putIfAbsent(connectorName, claim) != null) { throw new StreamException(ERR_STREAM_CONFIG_ERROR).param(ARG_DETAIL, "duplicate live connector name '<name>': another pipeline is running with the same DebeziumConfig.name in this JVM — failure listeners would cross-route (use a unique name or per-instance sharding)"); }`——**typed 拒绝发生在进入 try 之前**，被拒实例不存在任何会执行 `unregisterFailureListener` 的 finally（AR-03 口径对齐：同 `ERR_STREAM_CONFIG_ERROR`+配置指引 param 形态，:543-549 先例——AR-03 本体零改写，本守卫为新 face）。
3. **finally 配对移除（M1 硬约束的兑现形态）**：正常路径 finally（:317 区）**首行** `LIVE_CONNECTOR_CLAIMS.remove(connectorName, claim)`——**两参条件移除**（仅当映射仍为本 run 认领令牌才移除，从不按裸名移除）；随后既有 `DebeziumEngineWrapper.unregisterFailureListener(connectorName)`（:318，零改动）才执行。载体零 diff 红线落款：engine 侧注册/注销调用形态零改动，守卫使「同名覆盖 put」对经本类注册的调用方结构性不可达。
4. **M1 所有权判定论证**：被拒实例的 typed 抛出先于 try/finally 存在——其 finally **不存在**，按名移除第一实例 listener 的路径结构性消失（红② 存活反例腿钉该不变式，见裁定 E）；正常实例的按名移除恒自持（认领令牌在整个 run 生命周期被持有——任何同名并发 run 已被守卫拒绝，finally 执行时刻 name↔本实例映射唯一）；两参条件移除消除「A 在 finally 期间 B 已重启同名」（不可能并发但防御性配对）残余面。守卫自身失败路径**不再复活 W-02 同型缺陷**（M1 判定闭环）。
5. **R4-24 接缝注记（落款）**：本守卫阻断的是**经 `DebeziumCdcSourceFunction.run` 注册路径**的同名覆盖触发链；R4-24 承接的载体面（`DebeziumEngineWrapper.FAILURE_LISTENERS` put 语义/`NopStreamOffsetBackingStore` 同名桶）为纵深加固——两批接缝兼容：in-scope 守卫拒绝在调用链更早处 typed 失败，R4-24 的载体 typed 拒绝/令牌化对残余直接调用方生效，两者叠加无冲突、无语义互斥；接缝处零共享 diff。

**理由（触发时序论证）**：缺陷时序（audit T1-T4）——T1 管线 A run() 注册 listener-A（键=裸 base 名，`totalParallelism<=1` 时 `instanceName` 返回裸名，:645-647 + DebeziumSourceSupport :77-78 亲证）→ T2 同名管线 B 注册 → 载体 put 直接覆盖 listener-A → T3 A 引擎终态失败 → notify 命中 listener-B → B 假失败重启、A 假活死流（R5-CON-03 要消灭形态的镜像）→ T4 A finally 按名移除 B 的注册 → B 失去失败可见性。post-fix——T2' B 认领失败 typed 拒绝（B 不入注册、不覆盖、其 finally 不存在）→ T3' A 失败命中 listener-A（路由正确）→ T4' A finally 条件移除自持认领 + 自持 listener。B 层并行模式（`totalParallelism>1`，实例名带 `-<subtaskIndex>` 后缀）天然互异——守卫在该模式零触发（audit 失效面③「干净」面维持）。

**被否决的备选**：
- **注册键实例级限定**（本类给注册键追加实例唯一后缀）：engine 侧 `notifyEngineFailure` 以引擎 config name 路由——调用方改键即断路由（通知键=引擎名，非自由变量）；且把「同名双管线」这一部署配置错误静默合法化，违 fail-fast 口径；键改造本体归 R4-24 裁决域。不采。
- **声明兜底（doc-only）**：缺陷本体是跨管线信号错投（数据面正确性），零代码收口违反 Fix 判定（批次行为修复批）；不采。
- **载体内加同名守卫**：nop-message-debezium 批外模块——**载体零 diff 红线**（条件申报禁静默触发）；不采。
- **synchronized 注册方法**：跨 run 长临界区无对价（认领 putIfAbsent 已原子）；不采。

**零触碰边界**：R5-CON-03 注释块（:259-263）零弱化——守卫为该注释所声明语义（终态失败必须 fail THIS task）的注册面加固；`TestDebeziumCdcSourceErrorPropagation` 零改写；`DebeziumEngineWrapper`/`NopStreamOffsetBackingStore` 零 diff（批外红线）；`TestDebeziumCdcStopVerification_C3_014` 零改写；B 层 sharding/instanceName 本体零改写。

### B.2 W-04：runEntered CAS 失败 typed 拒绝 + C10-015 same-diff 断言反转（1.2-C3-011）

**决策**：**「CAS 失败分支 typed 拒绝（一行语义修复）+ `TestDebeziumResourceManagement` 折叠断言反转为终态形态判别」唯一分支**：

1. **产品面**：`if (!runEntered.compareAndSet(false, true)) { return; }`（:243-245）改为 `throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL, "run() re-entered while a previous run is still active — refusing silent EOS (typed rejection, recovery path owns the retry)")`。抛出点在 try 块之前——`runEntered` 保持 true（所有权归在途 run，语义与现行静默 return 一致）、被拒线程不进入 finally（不触任何清理面，与 B.1 M1 判定同构）。
2. **C10-015 同 diff 改写（K11 折叠半径）**：`TestDebeziumResourceManagement.testDebeziumRunReentrancyGuard` :41-64 区——runner2 的 catch-all（:58-60）改为记录 `Throwable`；:64 断言 `assertTrue(secondRunReturned.get(), "…should return immediately…")` 反转为终态形态判别：`assertTrue(thrown instanceof StreamException, "second run() must fail typed, never silently return as fake EOS")` + 消息含 re-entered 判别（或真实进入形态的显式判别——本并发双 run 场景下 runEntered 被在途 run 持有，「真实进入」分支结构性不可达，断言锚定 typed 抛出）。
3. **旧断言消号理由（登记）**：现行 catch-all + 成功标志形态把「静默 return（缺陷载体——假 EOS）」与「typed 抛错」折叠为同一观察值，且断言文本显式钉住「should return immediately」为预期行为——即 C10-015 认定的「钉住缺陷载体」假保护（沿 2511 C10-013 改写先例形态；消号依据 = merge-adjudication §4.4 :226 权威口径「并发 run 第二线程终态形态（typed 异常或真实进入，不允许假 EOS）」+ 批次行 exit criteria 增补「C10-015 随 1.2-C3-011」同 diff 绑定——禁拆分）。
4. **触发面收窄裁决维持（零重开）**：运行时同实例双进入已被 SupervisionLoop fail-loud zombie + SubtaskTask interrupt 结构性阻断（2503 期 R3 裁决）——typed 拒绝的服务对象 = 程序化直构路径（owner doc 声明的合法用法）+ 防御纵深；行为变更面（直构消费方从「静默假完成」变 typed 失败）随 E5 声明。

**理由（触发时序论证）**：缺陷时序——T1 旧 run 在 finally `source.stop()` 中阻塞、`runEntered` 仍 true → T2 新任务线程 run() CAS 失败 → 静默 return → 作业标记 FINISHED（假 EOS）而非 FAILED，CDC 流已死零信号——与本模块已消灭的 P1-9/R5-CON-02 静默死流家族同型（守卫把检测到的异常转译为最恶性静默完成）。post-fix——T2' CAS 失败 typed 抛出 → 作业 FAILED → 恢复语义接管。并发面：CAS 本身原子（守卫检测面无竞态），缺陷纯在响应语义——一行修复闭环。

**被否决的备选**：
- **重试/等待进入**（CAS 失败后自旋等旧 run 退出再进入）：重入是异常场景，自动重试掩盖根因且引入无界等待面；恢复路径是唯一合法重试通道。不采。
- **返回 boolean/状态枚举改签名**：run() 签名为框架契约（`SourceFunction.run`），改签名=跨层破坏；不采。
- **仅改测试不改产品**：缺陷本体是响应语义（假 EOS），测试反转无产品修复则 C10-015 改写钉钉住的是新行为而产品未变——本末倒置；不采。

**零触碰边界**：`runEntered` 字段声明（:99）/finally reset（:340）零改写；`TestDebeziumCdcSourceFunction.testRunEnteredAtomicPreventsDoubleEntry`（:308-349，断言仅终止性）零改写并列运行；2503 停止契约产物面零回退（红线 9）。

## 裁定 C 停机链与资源清理域（W-05/W-06/W-07/W-09）

### C.1 W-05：双桥 reader close() clear-first + interrupt 兜底（1.2-C3-012；C6-002/003 驳回残面唯一存续锚点）

**决策**：**「超时检测 → 单发 interrupt → try{clear}/finally{typed 抛出} → Debezium 面 verify 保持 join 成功路径」双桥同形唯一分支**：

1. **统一 close 序（`MessageSourceReader.close` :228-245 与 `DebeziumCdcSourceReader.close` :353-377 同改对称）**：
   - `running = false; embeddedFunction.cancel();`（现状零改写）
   - `Thread thread = runnerThread; boolean joinTimedOut = false; if (thread != null) { thread.join(CLOSE_JOIN_TIMEOUT_MILLIS); joinTimedOut = thread.isAlive(); }`
   - **超时分支单发 interrupt（先于 clear）[MJ-2 机制三段诚实化]**：`if (joinTimedOut) { thread.interrupt(); }`——**机制归属改写**：interrupt 打在 runnerThread，而 `queue.put` 的阻塞者是总线发送方线程（MSR）/Debezium 引擎分发线程（DCR）——runner 的 interrupt 到达不了它们；生产分发线程解除的真实通道 = **`clear()` 释放在途 put**（本序保留）；interrupt = runner 侧 best-effort（打断其 stop/join 内可中断等待；残余已 watch-only 登记维持）。原「put 对中断立即抛出」归因错置订正——修复结果不变（clear 通道保留），归因与可观测面按此重锚。
   - **clear 先于/独立于 typed 抛错（R03 同构投影）**：`try { if (queue != null) { queue.clear(); } } finally { if (joinTimedOut) { throw (StreamException) new StreamException(ERR_STREAM_JOB_EXECUTE_FAILED).param(ARG_DETAIL, "<runner> did not terminate within " + CLOSE_JOIN_TIMEOUT_MILLIS + "ms of close()"); } }`——clear 无条件执行（异常路径零跳过），typed 抛错在 finally 中保持既定消息/码/形态零改写。
   - **Debezium 桥专属（K2 落款）**：`embeddedFunction.verifyEmbeddedEngineTerminated("reader-close", true)`（:367-373）保持在 **join 成功路径**、调用形态与预算零改写——join 超时路径在 clear/finally 处 typed 抛出不再到达 verify（超时即 runner 未终止，verify 必然 ghost 失败，提前抛出不损失任何信号）；`TestDebeziumCdcStopVerification_C3_014` 的 reader-close typed 断言走 join-成功-引擎未终止路径，零改写。
2. **interrupt 兜底语义**：单发、不循环、不 join 二次——collect 的 InterruptedException 分支（MSR :326-330 / DCR :465-469，已恢复中断标志+typed 重抛）从「链路不可达」恢复可达，**分支本体零改写**（现状登记 (l)）。残余面（runner 卡在引擎 stop 内部深处、interrupt 不解除其 native 等待）= 2503 ghost 风险声明域（stop 契约承接），watch-only。
3. **clear 无副作用论证（数据语义）**：close 后队列随即废弃（reader 终态、pollNext 经 `running=false` 恒空）；队列驻留记录在 last-emitted 位点边界之内（`onEmitted` 于 pollNext 出队后记账——queue 驻留记录的位点 **>**（非 ≤）已发射边界[MN-2 方向订正——未出队者未记账，这正是丢弃无损的原因]），丢弃 = 恢复重放补齐，**宁可重复不丢失**（2511-⑥ 已声明 at-least-once 语义）；message 桥后端承载 at-least-once 同构安全。clear 不触 2536 byte-bound 守卫段（红线 6）、不触位点链（红线 4）。
4. **预算语义**：`CLOSE_JOIN_TIMEOUT_MILLIS = 5000L` 维持静态常量（双桥 :55/:61）——可配化候选归 Non-Blocking Follow-up（零触碰）；5s 在钉预算 ≤30s 闩带内。

**理由（触发时序论证）**：缺陷时序——T1 分发线程（Local 总线=发送方业务线程 / Debezium=引擎分发线程）阻塞在满队列 `queue.put` → T2 close() → runner 卡在 finally `source.stop()`（>5s）→ join 超时 → typed 异常**先抛**（:235-237/:362-364）→ `queue.clear()`（:241/:375）被跳过 → T1 的 put 永久阻塞，无中断会到达（interrupt 分支不可达）——发送方业务线程被单条流任务关闭路径无限期拖死（故障跨出流模块，F5 不可见挂死）。post-fix——T2' close 检测超时 → interrupt 单发（put 立即 InterruptedException → collect typed 重抛 → runner 经 failure 通道退出）→ clear 兜底（即便 runner 未走 interrupt 路径，在途 put 由 clear 解除）→ typed 抛出保持（超时信号零弱化）；发送方业务线程在预算内解除。**为何 interrupt 在 clear 之前**：中断使 put 即刻抛出（不等槽位），clear 随后兜底队列遗留——两动作独立生效，次序取「runner 最快出口」优先。

**被否决的备选**：
- **join 超时静默吞掉不抛**：超时=关闭异常必须 typed 可见（现状消息/码零弱化——只解除阻塞不改判级）；不采。
- **interrupt 循环/多次递送**：单发已闭合（put 即抛）；循环引入忙等面；不采。
- **clear 放 interrupt 之前且不 interrupt**（仅 clear）：当前在途 put 解除但持续泵送的发送方立即再阻塞于下一 put（C3-012 复核精化明确登记的面）——缺 interrupt 兜底则缺陷半修；不采。
- **queue 改 offer(timeout)+running 检查**（audit C3-009 建议②的 in-scope 半边）：改背压形态=行为面扩大（现状「阻塞 put=背压 parity」为声明语义）+与 2536 byte-bound 守卫段同点冲突风险；本工单面=close 路径，泵送面零触碰。不采。
- **face③ 保持 LOG.warn 吞异常**：见 C.5——族内 typed 纪律对齐为工单明文面。

**零触碰边界**：2536 byte-bound 守卫段（红线 6）+2511 位点链（红线 4）+2503 verify 形态与钉（红线 9）；collect 本体（含 InterruptedException 分支文本）零改写；`TestMessageSourceFunctionLifecycle`/`TestMessageSourceFunctionRestart`/`TestMessageSourceFunctionThreadSafety` 零改写（红⑤ 为新测试类追加）。

### C.2 W-07：restoreRangeInto 两段关闭——先关全部 handles 后关 src（1.2-C6-006）

**决策**：**「放弃对 src 的 TWR 自动顺序，finally 手工两段（handles 全部 → src）」唯一分支**（`RocksDBIncrementalRestore.restoreRangeInto` :208-230 区）：

1. **形态**：`List<ColumnFamilyHandle> handles = new ArrayList<>(); RocksDB src = null; try { src = openReadOnlyTyped(…); …copy loop…（逻辑零改写）; } finally { for (ColumnFamilyHandle h : handles) { if (h != null) { try { h.close(); } catch (RuntimeException e) { /* 收集，见下 */ } } } if (src != null) { try { src.close(); } catch (RuntimeException e) { … } } }`——外层 `try (ColumnFamilyOptions cfOpts; DBOptions dbOptions)`（:180-181）**零改写**（options 在内层块退出后关闭，顺序仍正确：handles→src→options）。
2. **关闭失败收集**：单个 close 的 RuntimeException 收集并在主异常（若 body 抛出）上 `addSuppressed`，无主异常时保持首错上抛——**顺序契约优先于单点失败可见性**（先关完全部 handles 才关 src，任何单点 close 失败不中断两段序）。
3. **同 diff 结构性闭合面（K6 登记非顺带）**：`openReadOnlyTyped` 中途抛错时已部分填充的 handles 由同一 finally 关闭、src 为 null 不关——audit C6-006 失效面③「open 失败路径 handles 半成品无人关」（C6-005 同族兄弟）在同一方法内被本 restructure 结构性闭合；登记为同 diff 收益，零额外触面。
4. **红⑦ 承载**：结构钉（源序断言 handles-close 先于 src-close）+变异探针（还原原序→钉红→复修复→绿留痕）——native UAF 非确定性，**禁行为伪红**（裁定 E）。

**理由（时序论证）**：缺陷时序——T1 try-with-resources 退出 → T2 自动 `src.close()`（DBImpl 析构，旗下 CF 句柄原生对象随之失效）→ T3 finally 才对 handles 逐个 `h.close()`（:226-230）→ 对已随 DB 释放的句柄 dispose = use-after-free（rocksdbjni 官方契约「先关全部 ColumnFamilyHandle 再关 DB」）——多数构建表现为无害 no-op、部分平台/版本 SIGSEGV/堆损坏，恢复流程以 typed 体系外的崩溃终结。同仓双标准成立：`RocksDBKeyedStateBackend.close`（:1175-1244 区）正确序（cfHandles→defaultCF→db→preset→options）为同模块正例（零触碰对照）。post-fix——finally 两段序与正例同构，copy 逻辑（`backend.copyColumnFamilyRange` :220）零触碰——仅关闭顺序变化，功能行为零变化（`TestRocksDBIncrementalRestoreFailFast` round-trip 功能绿为验收并列钉）。

**被否决的备选**：
- **TWR 仅包 handles 集合**（audit 建议的括号形态）：List 无单一 AutoCloseable 载体（需引入包装类=新抽象零对价）；手工两段更直白且与 close() 正例同构；不采。
- **src 关闭后重新校验 handles**（防御 dispose 双重）：rocksdbjni close 幂等性足够（isOwningHandle 语义）；防御层零对价；不采。
- **顺带修 openDB 同族形态**（backend openDB catch 无清理）：那是 W-06 本体工单（C.3）——同文件不同工单面，diff 分组登记禁交叉。

**零触碰边界**：外层 options TWR（:180-181）/listColumnFamilies 降级分支（:182-203）/copy 循环本体/`cfNameOf`（:237-249）零改写；Backend.close 正例（:1175 区）零触碰；2536 `TestRocksDBStateMigrationStreamingR403` 零改写。

### C.3 W-06：openDB 失败路径 native 句柄逆序清理 helper（1.2-C6-005）

**决策**：**「包内可见失败清理 helper（handles → WBM → cache → bloom → cfOptions → dbOptions，收集-suppressed 后重抛）」唯一分支**（`RocksDBKeyedStateBackend.openDB` :298-304 区）：

1. **helper 签名（白盒钉载体）**：`void closeResourcesAfterFailedOpen(List<ColumnFamilyHandle> handles)`——**包内可见（package-private）**（2510-⑪ 包内注入缝惯例；红⑥ 白盒钉直调形态，见裁定 E）。helper 体：handles 非 null 项逐个 close（list 序）→ `writeBufferManager` → `blockCache` → `bloomFilter` → `cfOptions` → `dbOptions`（**与既有 close() 正例同序**——:1218-1244 的 preset 三件序「WBM→cache（其 charging 目标）→bloom」+options 收尾；各对象 close 后字段置 null，与 close() 的置 null 纪律一致）；每个 close 的 RuntimeException 收集入局部列表（不中断两段序）。
2. **catch 改形**：`catch (RocksDBException e) { closeResourcesAfterFailedOpen(handles); throw new StreamException(ERR_STREAM_STATE_ERROR, e).param(ARG_DETAIL, "Failed to open RocksDB at " + dbPath); }`——主异常**形态零改写**（同一码/同一消息/同一 param——红⑥ 行为 leg 的校准锚）；清理中收集的失败经 helper 返回后 `addSuppressed` 附加（**附加不替换**——plan 风险段口径）。
3. **同方法双标准消解**：listing 的 Options TWR（:280-281）与 mirror 注释（:278-279）零改写——open 失败路径补齐 mirror 纪律的缺半边。
4. **红⑥ 承载**：helper 白盒钉（真 native 对象 cleanup 后 `isOwningHandle()==false`——dbOptions/cfOptions/preset 对象+handles 半成品 ≥3 断言）+ dir-is-a-file typed 行为 leg（dbPath 指向普通文件 → `RocksDB.open` 失败 → typed 断言）+ 结构腿（compile-red 诚实留痕——裁定 E 存证载体明文）。

**理由（触发时序论证）**：缺陷时序——T1 构造分配 dbOptions（:256-259）/cfOptions（:261-262）/memory-control 预设（:270-272 调用 → applyMemoryControlPreset :335-355 分配 cache/WBM/bloom）→ T2 `RocksDB.open`（:300）因 `LOCK:` 抢锁失败/目录损坏抛 RocksDBException → catch 直接 typed 上抛（:302-304）→ 调用方从未拿到实例引用、close() 永不可达 → 全部 native 句柄（含 handles 半成品 :298）只能等 rocksdbjni finalizer——native 内存不受堆 GC 压力驱动，回收时机不可控（audit 口径）；重启重试风暴下泄漏线性叠加（WriteBufferManager/Cache native 内存+后台线程驻留）。post-fix——T2' catch 先逆序清理全部已分配句柄（失败收集不中断序）再按原形态上抛——失败重启场景零泄漏叠加。并发面：构造路径单线程，helper 零并发面；字段置 null 与 close() 幂等纪律一致（重复调用安全）。

**被否决的备选**：
- **openDB 全体 TWR 化**（把 options 分配包进 try-with-resources）：dbOptions/cfOptions 是实例字段（open 后存活全生命周期，由 close() 关闭）——TWR 化须改字段为局部+传递，面扩大且与 close() 所有权模型冲突；catch 逆序清理是最小 diff 形态。不采。
- **Cleaner/finalizer 兜底**：GC 依赖不可控（缺陷本体即 finalizer 不可靠）；禁路线。
- **helper 私有 + `…ForTest` 委托**：两跳零对价；包内可见直调与 2510-⑪ 惯例同构。不采（私有形态）。
- **handles 逆序（list 倒序）**：handles 由 `RocksDB.open` 按描述符序填充、失败时部分项彼此无依赖——list 序与逆序等价安全；与 close() 正例的 cfHandles 遍历序（:1187-1196 values 序）一致取 list 序。不采（逆序形态）。

**零触碰边界**：成功路径零 diff（:306-320 赋值/recorder register 接线 :319-320 红线 1）；2511 缺省与阈值守卫（红线 3）/2536 W-01 接线零改写；applyMemoryControlPreset 本体零改写（helper 只消费其分配结果）；`TestRocksDBMemoryControl`/`TestRocksDBKeyedStateBackendCrossThreadOps`（8.41 受害侧钉）零改写。

### C.4 W-09：Files.walk 双点 TWR 化 + fd 计数回归（1.2-C6-011）

**决策**：**「双点 TWR（对齐 SnapshotStrategy :162 live 写法）+ R07 形态 fd-delta 腿（环境受限降结构腿+变异探针）」唯一分支**：

1. **双点改形**：`FileSplitEnumerator.discoverSplits`（:147）与 `FileSource.sample`（:407）——`List<Path> files; try (Stream<Path> stream = Files.walk(dir)) { files = stream.filter(Files::isRegularFile).collect(Collectors.toList()); }`（sample 的既有外层 try 保持异常处理职能，TWR 包裹 Stream 本体；对齐锚 = SnapshotStrategy :162 `try (Stream<Path> entries = Files.list(nativeDir))` live 形态——K1 勘正）。
2. **fd 计数腿（红⑨ 主形态，R07 先例复用声明）**：`UnixOperatingSystemMXBean.getOpenFileDescriptorCount()` delta 有界断言 + `@EnabledIf(openFileDescriptorCountAvailable)` 可用性门 + 预热（消除连接池/常驻 fd 基线漂移）+ N 次异常路径迭代后 fd 净增有界——**形态复用不复制文件**（plan 2522 `TestFileDownloadSinkClose` 形态参照，nop-http 通道与本批零交集——cra-index :393「fd 计数回归复用 R07 先例」条款兑现）。
3. **环境受限降级预案（裁定明文）**：perm 注入在 CI 非 root 形态成立（`Files.walk` 遍历不可读子目录抛 UncheckedIOException——缺陷触发面）；如执行环境使 fd 腿或 perm 注入不可用（root/平台无 UnixOperatingSystemMXBean），降级 = 结构腿（TWR 结构断言，见裁定 E 红⑨）+ 变异探针（移除 TWR → 结构钉红 → 复修复 → 绿）——降级**须留痕**（环境证据+探针日志），禁伪绿。

**理由（触发时序论证）**：缺陷时序——T1 `Files.walk(dir)` 打开源目录 DirectoryStream（native fd）→ T2 遍历中途命中不可读子目录/IO 错误 → `FileTreeWalker.next()` 抛 UncheckedIOException → collect 异常终止 → Stream 注册的 `onClose(walker::close)` 永不执行（调用方未 TWR/显式 close）→ `UnixDirectoryStream` 无 finalizer/Cleaner（JDK 25 亲证）→ fd 泄漏至进程退出。happy path 反证：遍历正常穷尽时 walker 自关——缺陷面收敛异常路径（P2 非 P1 判级依据）。重启风暴下线性累积直至 `ulimit -n`，故障表现远离根因。post-fix——TWR 保证异常路径 onClose 执行，fd 预算内归还。同仓双标准消解：SnapshotStrategy/Restore 六处全 TWR（K1 六锚亲证）——本批把最后两处裸用对齐。

**被否决的备选**：
- **仅修 discoverSplits（作业主路径）**：sample 可被 ops 反复调用（失败循环面）——双点绑定是工单明文（同变更集绑定条款）；不采。
- **Files.walkFileTree 改写**：遍历模型重写（行为面扩大）零对价；TWR 是最小 diff。不采。
- **collect 前显式 close 变体**（try 内手动 close）：TWR 即该形态的惯用表达；不采。

**零触碰边界**：`C2-007`（FileSourceReader 监视器 I/O，P3）同文件族邻接零触碰；sample 的 maxLineBytes 语义（:415-421）与 H-1 副作用预算零改写；`TestFileSourceSample`/`TestFileSourceReaderRecovery`/`TestFileSourceCheckpointRestore` 零改写（红⑨ 为新测试类追加）。

### C.5 W-05 face③：BatchLoaderSourceReader.close typed 对齐（随 C.1 同工单同 diff）

**决策**：**「loader close 异常 typed 上抛（`StreamException(ERR_STREAM_STATE_ERROR, e)` + detail「Failed to close the batch loader during reader close; loader resources may leak」），loader 置 null 保持」唯一分支**（:221-232 区）：

- `SourceReader.close() throws Exception`（接口 :103 亲证）签名已携带失败——catch 包装上抛为签名内合法形态；`loader = null`（:231）在 close 尝试后保持执行（try/catch 形态：close 异常包装抛出前置 null 或 finally 置 null，取「null 先于抛出」——对象终态一致）。
- **行为变更面（E5 声明）**：close 期 loader close 失败从「WARN 吞掉（reader 假正常关闭，loader 资源静默泄漏）」变 typed 失败——reader close 为终态清理点，typed 上抛使泄漏可见并进恢复路径（与 `BatchConsumerSinkFunction.close` flush 失败 typed 上抛「data may be lost」族纪律同构——族参照零 diff）。
- **被否决备选**：保持 WARN（缺陷本体——族内 typed 纪律不一致）；吞掉+suppressed 到下一异常（close 后常无下一异常——抑制=变相吞）；不采。

**零触碰边界**：`TestBatchConsumerSinkFunctionCloseLogging`（族参照，sink 侧零 diff）；`TestBatchLoaderSource` 零改写（红⑤ face③ typed leg 为追加）。

## 裁定 D 磁盘残留与 TTL 生命周期域（W-08/W-10/W-11）

### D.1 W-08：File 2PC 孤儿 temp 清扫——挂 beginTransaction + manifest∪pending 双白名单 + 自后缀过滤（1.2-C6-007；双 face 绑定）

**决策**：**「beginTransaction 挂点（恢复链末位、每 run 恰一次）+ 自 subtask 后缀精确过滤 + manifest∪pending 双白名单 + manifest temp face② 同扫」唯一分支**：

1. **挂点 = `beginTransaction()` override（:775-778 现为 no-op）**——K5 亲证的挂点依据：基类恢复链 `setPendingCommits`（:90）→ `restoreFromEpoch`（:274，非 durable 恢复条目 abort :312 已删其 temp）→ **`beginTransaction`（:321，恢复链最后一步）**；fresh run 同经 beginTransaction（:83）。挂该点 = (a) 恢复白名单——**[MJ-3 前提改述] begin 时刻还原 pending 已被 commit/abort 消费**（restoreFromEpoch 两循环 `pending.remove` 后才 beginTransaction）；**pending 白名单唯一实保护 = commit 失败保留条目的 temp（:304-307 subsuming commit 待用）+ 防御性配对；manifest 白名单为该挂点主白名单**；(b) abort 清理已完成（幸存 temp 判定无歧义）；(c) fresh-run 与恢复两形态统一覆盖（「首次 commit 前」）；(d) 距首个 saveState 有整段无竞争窗（自 subtask 后缀内单线程）。**零锁协议触碰**：清扫不进 `withManifestLock`（红线 7——2536 W-06 锁预算零交互）；manifest **只读载入**做白名单（`loadManifest()` 既有读路径），零 manifest 写、零保留窗口参数触达（红线 2——R6 CON-01 provenance 判别同址：`recoveredEntryKeys` 在同一恢复链已就位）。
2. **自后缀精确过滤（合法在途反例腿的结构基础）**：仅扫描本 subtask 后缀的文件名——subtask 0 = `^\.(epoch-(\d+))\.tmp$`（无后缀），subtask K>0 = `^\.epoch-(\d+)\.sK\.tmp$`（精确后缀）。他 subtask 的在途 temp（`.epoch-9.s1.tmp` 对 subtask 0 的扫描）**结构性不命中**——并行 2PC（per-subtask 隔离，AR-14）互不误删；`.manifest.lock`/`manifest.properties`/`epoch-*.txt` finals/他后缀全部不在扫描集。
3. **manifest∪pending 双白名单**：temp 被删当且仅当（匹配自后缀形态）∧（epochId ∉ manifest 已记录键集〔同一 epochId+同后缀的 manifest 键存在 = 保守跳过——防异态 FS 下 temp 与 final 并存的极端形态〕）∧（路径 ∉ `getPendingCommits()` 全部 `FilePendingCommit.getTempPath()`〔**MJ-3**：该集合在 begin 时刻仅含 commit 失败保留条目——subsuming commit 待用 temp + 防御性配对〕）。不采 mtime 窗（跨 JVM/共享存储时钟不可靠 + 竞窗弱判定）。
4. **删除失败可见（不复用 deleteIfExistsQuiet）**：清扫用自有删除（`Files.deleteIfExists` + IOException → **WARN** 含文件名，非 fatal——清扫失败不得阻断恢复链启动）；**不复用** `deleteIfExistsQuiet`（:975-981 空 catch——C6-014 P3 面，abort 路径零触碰不顺带修）——新代码零继承已登记 P3 气味。
5. **face②（manifest temp ATOMIC_MOVE 失败残留，同族微缩版同修）**：同一清扫 pass 扩展自后缀的 `manifest.properties{suffix}.tmp` 残留删除（:853-854 亲证命名）——恢复链起点无本 subtask 在途 commit，凡自后缀 manifest temp 均为 ATOMIC_MOVE 失败/崩溃残留；无白名单需要（manifest temp 从不进入 manifest 内容）。
6. **红⑧ 承载**：孤儿注入（自后缀 `.epoch-7.tmp` 非白名单）→ pre-fix 残留 = 当前即红；post-fix 清扫绿 + **合法在途反例腿**（他后缀 temp 注入 + 恢复 pending temp 注入[MJ-3 重定义二选一：(i) commit 失败保留边构造[commit 抛错+保留 pending+幸存 temp→断言零删——真实链可测]或 (ii) manifest 覆盖 temp 注入——原「restore pending temp」经真实恢复链构造不出幸存用例]——断言零误删，防「清扫存在但白名单误杀」退化，K10 双 leg）。

**理由（触发时序论证）**：缺陷时序——T1 saveState(N+1) 写 `.epoch-(N+1).tmp` + 仅登记内存 `pendingCommits`（:426-427）→ T2 checkpoint 未 durable 即失败/崩溃 → 登记随进程消亡 → T3 恢复 run 的 `restoreFromEpoch` 只 abort「恢复快照继承」条目（:312）→ N+1 temp 无人删除 → 输出目录按失败 checkpoint 数线性累积（每 temp=一个完整 epoch 批量），全文件 grep 零清扫路径（亲证维持）。post-fix——T3' 新 run beginTransaction 扫描：幸存自后缀 temp 减去白名单（恢复 pending 的 temp + manifest 已记录 epoch）= 纯孤儿集 → 删除（WARN 可见）——磁盘包络从「失败次数×全量 epoch」收敛为「在途上限（maxPendingEpochs 面）×单 epoch」。并发面：自后缀内 beginTransaction 与首个 saveState 之间无并发写者；跨 subtask 由后缀过滤隔离；跨 JVM 同目录并行 subtask 同理——清扫正确性不依赖任何锁。

**被否决的备选**：
- **挂 commit 链（withManifestLock 内清扫）**：每 commit 热路径扫描成本 + 与 2536 锁预算（红线 7）交互面 + 挂点晚于恢复（恢复 run 若永不到 commit 则清扫永不执行——恰是失败密集场景）；不采。
- **挂 setPendingCommits**：恢复链中段——非 durable 恢复条目的 abort（:312）尚未执行，其 temp 会被白名单外误判（它们将在 abort 中被删，清扫先行判定冗余但无害）+ fresh-run 无恢复链不触达；begin 为链末位统一点。不采。
- **beginTransaction mtime 窗**：共享存储跨 JVM 时钟偏差 + 「本次 run 启动时间」获取点歧义；白名单判定为精确判据。不采。
- **启动清扫器/后台线程**：生命周期宿主缺失（sink 无调度链——2511-④ 载体二面同款判据）；不采。
- **复用 deleteIfExistsQuiet**：C6-014 P3 气味继承；不采（自有 WARN 形态）。

**零触碰边界**：红线 2 全集（保留窗口/prune/幂等守卫——`TestFileTwoPhaseCommitSink` 的 abort 删 tmp/kill-recover 无残留/AR-14 互斥钉零改写为红⑧ 并列锚）+红线 7 全集（四旋钮/锁预算/`TestFileSinkEnvelopeLockR403`）；C2-006（saveState 锁内 I/O）/MA-19（静态锁表）/C6-014（quiet 空 catch）三个 P3 相邻面零触碰；`TestFileTwoPhaseCommitSinkManifestPrune_C3_002` 零改写。

### D.2 W-08 挂点与 2511/2536 产物面交互声明（裁定 D.1 附款）

- beginTransaction 现 no-op（:776-778 注释「No per-epoch transaction resource」）——清扫注入不改变其事务语义（file 载体无事务资源；注释随清扫补一句「orphans sweep here」级说明，注释外的既有语义零改写）。
- 清扫执行的 manifest 只读载入失败（manifest 不存在/损坏）→ 白名单取空集 + WARN——**清扫降级为跳过**（白名单不可信时不删任何文件——fail-safe 方向=保守不删，孤儿残留为下一轮恢复重试对象），typed 上抛不在本面（恢复链启动优先）。

### D.3 W-10：restore 残留清扫 + deleteRecursively 兜底扩展（1.2-C7-003；与 W-07 同文件联动同组单 diff）

**决策**：**「restoreIncremental 入口陈旧目录清扫（id 严格小于当前恢复 id）+ restoreRangeInto 的 reconstruct 三行移入 try 边界（finally deleteRecursively 覆盖 mid-reconstruct 失败）」唯一分支**：

1. **入口清扫**：`RocksDBKeyedStateBackend.restoreIncremental`（:1137-1153）在 baseDir 解析（:1144）后、`restoreRangeInto` 调用（:1146）前插入：列举 baseDir 子项匹配 `rocksdb-restore-range-<digits>` **目录**，解析 id，`id < result.getCheckpointId()` → 递归删除（WARN 可见、失败非 fatal——与 D.1 同「保守不删」fail-safe 方向）。**id 比较窗 = 严格小于**：同 id 残留由既有 :173 前置清理命中；大于当前 id 的目录在跨部署回滚（restore id 回退）场景可存在且暂不清扫（id 前移后自愈收敛——watch-only 残留[MN-3 前提诚实化]）（restore 恒取最新 durable 点）——永不删除（保守窗）。pruneOldCheckpoints（仅匹配 `cp-{digits}`，:241-253 亲证）零触碰——`*-restore-range-*` 为独立保留面，不并入 prune。
2. **reconstruct 移入清理边界**：restoreRangeInto :172-175 三行+`Path nonSstDir`（:174）共四条语句[MN-4 补记]（`reconstructed` 赋值 + 同 id 前置清理 + `reconstructRocksdbDir`）移入 try 块（`reconstructed` 变量 try 外声明 null 初值，finally `deleteRecursively`（:232）null 守卫后执行）——**finally deleteRecursively 兜底从「成功路径之前的异常」扩展为「reconstruct 中途失败」**（plan「deleteRecursively 兜底扩展」口径）。与 W-07 两段关闭同方法同组——**同组单 diff 登记禁拆**（对齐纪律同文件跨批面条款；两面的 diff 块在 restoreRangeInto 同一 restructure 内，分节验收）。
3. **seed 面零触碰落款（红线 8）**：清扫插入点在 `restoreRangeInto` 调用之前——`:1146-1151 seedIndexFromHandles 调用序零移动零改写`；backend 侧 diff 块 = :1145 区插入清扫一段，seed 与 `Files.createDirectories(baseDir)`（:1145）相对序不变（清扫在 createDirectories 之后——目录已存在才能列举）。
4. **kill -9 残留面（声明边界）**：恢复期 kill 的残留目录由下一次恢复的入口清扫命中（id 严格小于）——本裁定清扫为恢复入口惰性触发，无独立后台清扫（同 D.1 备选否决逻辑）；同 id 重复恢复由 :173 前置清理覆盖。

**理由（触发时序论证）**：缺陷时序——T1 task 崩溃于恢复中途 → T2 新实例 `restoreIncremental` → `reconstructRocksdbDir` 将共享 SST 全量拷入 `{checkpointBaseDir|dbPath}-restore/rocksdb-restore-range-{id}`（目录尺寸 ≈ 全量状态）→ T3 中途失败（segment 缺失/校验拒绝/IO）→ 异常上抛、try/finally 尚未进入（:175 先于 :180 边界）→ 部分重建目录残留 → T4 下次恢复若 durable 点已前移（新 id），旧 `rocksdb-restore-range-{旧id}` 永不再被 :173 同名清理命中且无任何保留策略扫描（prune 仅 cp-{digits}）→ 反复故障循环节点磁盘线性耗尽。post-fix——T3' mid-reconstruct 失败由 finally 兜底删除（同 run 窗口）；T4' 跨 id 残留在下次恢复入口被 id 窗清扫命中。并发面：恢复在 task 重建路径单线程执行；同 id 并发恢复（同 baseDir 双 subtask 异速）被「严格小于」窗排除——零误删并发面。

**被否决的备选**：
- **id ≤ 当前（含同 id）**：同 id 由 :173 前置清理管辖——入口清扫越权同 id 会在「同 id 重复恢复」场景与 :173 双重删除（幂等无害但职责边界模糊）；严格小于为最小职责面。不采。
- **清扫挂 backend open**（audit 建议①的 open 点）：open 早于 segmentStore 配置与恢复 id 可得点——无 id 窗可判；restoreIncremental 是唯一恢复入口（:1050/:1054/:1068 亲证三分支汇入）。不采。
- **后台清扫线程/独立保留策略扫描器**：宿主缺失同 D.1；不采。

**零触碰边界**：红线 8（seed 调用序/注释）；红线 3（增量缺省/阈值）；pruneOldCheckpoints 本体与 K=2 滚动保留；`TestRocksDBIncrementalRestoreFailFast`（W-07/W-10 零回退锚+新钉基座）/`TestRocksDBKeyGroupRangeRestore` 零改写（红⑩ 为追加）。

### D.4 W-11：TTL 清理 setCurrentKey 周期 sweep 移植（1.2-C7-010；双面绑定——hook + 声明兑现）

**决策**：**「Memory 后端形态逐字移植（nanotime 门控 + 缺省 60s + ≤0 关闭）+ 微间隔测试缝 + 既有手动 sweep 面同步配测」唯一分支**（`RocksDBKeyedStateBackend`）：

1. **门控三件移植**（Memory `maybeSweepExpiredEntries` core :233-245 fact-reference 同构，零触碰源）：字段 `DEFAULT_TTL_SWEEP_INTERVAL_MILLIS = 60_000L`、`private long ttlSweepIntervalMillis = DEFAULT_TTL_SWEEP_INTERVAL_MILLIS`、`private transient long lastTtlSweepNanos`、`private transient boolean anyTtlBound`（`applyTtl` 成功绑定处置 true——:832-842 区插一行[MN-7 锚勘正——成功 bindTtl 之后]）；私有 `maybeSweepExpiredEntries()`：`if (!anyTtlBound || ttlSweepIntervalMillis <= 0) return;` → `System.nanoTime()` 门控（`lastTtlSweepNanos != 0 && now - last < interval×1_000_000L` 跳过）→ `lastTtlSweepNanos = now; cleanupExpiredEntries();`。**过期判定钟零新增**：既有 `ttlClock`/`setTtlTimeProvider`（:208/:802）继续承载（K4——门控用 nanotime 与过期判定用 ttlClock 的双钟分工与 Memory 后端逐字一致）。
2. **挂钩**：`setCurrentKey`（:484-486）在 `this.currentKey = key;` 后调用 `maybeSweepExpiredEntries();`——热路径成本 = 一布尔一长比较（Memory 同构论证；TTL-free 作业零额外成本）。`cleanupExpiredEntries` 本体（:889-916）与 snapshotState 挂点（:919-927 两处调用）**零改写**——只加触发点不删既有触发点（checkpoint 前置 sweep 语义维持）。
3. **旋钮接线**：`public void setTtlSweepIntervalMillis(long)`（≤0 = 关闭自动 sweep，语义与 Memory setter :279-284 逐字同构）+ getter。零新配置文件载体（setter 注入形态——Memory 同款，rocksdb varRef 面零触碰）。
4. **时钟缝（裁定唯一分支，K3 先例锚定）**：**微间隔驱动**——测试以 `setTtlSweepIntervalMillis(1)` 驱动门控（TestMemoryStateTtlSweep :116「smallest positive interval」in-repo 先例），过期推进用既有 `setTtlTimeProvider(FakeClock)`（`TestRocksDBStateTtl` :48-68 既有惯例）；**否决反射推进**（`lastTtlSweepNanos` 私有瞬态字段反射=脆弱+违反缝仅测试可见原则）与**新增产品侧 nanotime 缝**（Memory 后端本体即裸 nanoTime——移植含测试缝惯例一起同构，缝形态不超越源）。
5. **`TestRocksDBStateTtl` 手动 sweep 面同步配测（C10 侧记义务兑现）**：既有手动调 `cleanupExpiredEntries()` 用例（sweep 语义面）**零改写并列**；追加自动触发用例（追加零改写形态）：TTL state + FakeClock 推进过期 + 不调 snapshotState（checkpoint 关闭面）+ `setCurrentKey`（微间隔门控开）→ 断言过期 entry 自动回收 + sidecar 收敛；`≤0` 关闭 leg（门控关、手动 sweep 仍生效——手动面语义不弱化）。
6. **设计文档 :422 声明兑现（E5 载体）**：`state-management-design.md` :422 声明（「TTL 清理挂 `setCurrentKey` 周期 sweep（默认 60s，`setTtlSweepIntervalMillis(≤0)` 关闭…）」未限定后端）在 RocksDB 后端移植后**全局成立**——Phase 3 在该行落**兑现注记**（声明本体零改写，注记 RocksDB 后端已兑现 + 键形/缺省值），owner doc 能力矩阵/RocksDB 节同步（裁定 E C10 注记表承载）。

**理由（触发时序论证）**：缺陷时序——T1 RocksDB 后端 + TTL state（dedup/会话/幂等窗口典型形态：key 写入后不再回访）+ checkpoint 间隔小时级或 checkpointEnabled=false（公开旋钮）→ T2 key 过期且流上再无访问 → T3 无 barrier/长间隔 → `snapshotState()`（唯一自动触发点 :923/:926）不执行 → 过期 entry 永久驻留列族 + `TtlContext` sidecar 只增不删——访问惰性逐出只覆盖回访 key，TTL 主用途（永不回访）零回收；设计文档声明形态仅 Memory 后端存在（doc-code 分歧本体在代码侧）。post-fix——T3' setCurrentKey 门控 sweep（60s 缺省）按周期回收不回访 key——存储行与 sidecar 双面收敛，:422 声明在生产大状态后端兑现。并发面：单任务线程 mailbox 模型（与 Memory 同款「Single-threaded mailbox model — no synchronization」论证），门控字段零同步原语。

**被否决的备选**：
- **doc-only 兜底**（声明改「仅 Memory 后端」+运维手动入口）：批次为修复批且声明分歧本体在代码侧（plan 预设代码修复）——声明退让把生产包络缺口（TTL 配置被静默击穿）留作用户责任；不采。
- **后台 sweep 线程**：`TtlCleanupStrategy` javadoc 亲证声明「caller-driven、无自动后台清理线程」——线程形态违既有契约 + 生命周期宿主复杂度；不采。
- **挂 get/delete 访问路径替代 setCurrentKey**：回访 key 已由访问惰性逐出覆盖（audit 失效面③「存在，仅覆盖回访 key」）——访问挂点对永不回访 key 零增益；setCurrentKey 为 key 切换自然周期点（设计文档声明点位+Memory 先例）。不采。
- **sweep 深度 gauge/观测**：R-G 观测轨候选（Non-Blocking Follow-up 登记），零触碰。

**零触碰边界**：`MemoryKeyedStateBackend` 本体零 diff（core fact-reference——移植是 rocksdb 侧新增非 core 改动）；红线 3（增量缺省/阈值）；`cleanupExpiredEntries` 本体与 snapshotState 挂点；`TestRocksDBStateTtl` 既有用例零改写（追加形态）；8.41 受害侧钉零触碰。

## 裁定 E 测试缝总表（红①–红⑪ + compile-red 存证载体 + 零回退钉全列 + C10 映射）

### 红绿判据纪律（先红后绿，E2——本批 1 条 C10 验收前置[C10-015 随 W-04]+E2 泛在 11 钉）

全批测试零 mockito 新增使用（站点五模块 pom 仅 debezium 声明 mockito-core 未用——2536 F1 订正维持零使用）——观测一律 JDK 并发原语 + latch + 手工桩 + 探针 subclass + 包内可见注入缝（2510-⑪ 惯例）。**orTimeout 假绿陷阱禁令**（2536 全盘沿用）：future 完成性断言只允许同步 `get(timeout)` 捕获 TimeoutException 判红，禁 `orTimeout`/`completeOnTimeout`。**闩带上界 ≤30s 且收尾必放闸**（TestAwait deadline 60s 范式适配）。红绿快照落 `_tmp/` 总册 + 逐 trial 原始日志（2536 Major-D1 灭失教训——存证纪律）。测试类注释显式归属 **CRA 8.44 R4-04（`*R404` 后缀）**（同号防混：8.44-W-xx ≠ 8.43-W-xx ≠ 8.42/8.41-W-xx ≠ 8.17 finding 直呼；finding ID 同号异站防混同 plan 对齐纪律第八条）。红⑥⑦⑨ 的结构/变异腿诚实留痕禁伪绿（2536 红③④⑨⑪⑫ 先例形态）。

**compile-red 存证载体明文（M1 硬输入落款——红⑥ 结构腿专用，2536 Major-D1 补救形态）**：红⑥ 的白盒钉测试引用 `closeResourcesAfterFailedOpen`（pre-fix 不存在）→ 测试类 pre-fix **不编译**。诚实留痕三件套（禁伪绿禁灭失）：① **编译日志快照**——pre-fix 执行 `./mvnw test-compile -pl nop-stream-rocksdb`（或模块测试编译通道），BUILD FAILURE 日志原文快照落 `_tmp/red6-compile-red-<ts>.log`[MN-6a：三件套产物在计划总册/日检视日志留副本——`_tmp/` 易失性对冲]；② **structure-grep**——`grep -n "closeResourcesAfterFailedOpen" <backend 源文件>` pre-fix 零命中留痕（`_tmp/red6-structure-grep-<ts>.txt`）；③ **stash 留痕**——修复 diff 以 git stash/commit 序留痕（pre-fix 树 → compile-red 证据 → 修复落盘 → 钉绿），证据链时间序在 `_tmp/` 总册登记。同纪律适用红⑨ 结构腿（source-text 断言形态——测试可编译、断言读源码文本：TWR 结构断言 pre-fix 红，证据 = 断言输出留痕，无需 compile-red 三件套）。

### 产品侧测试缝（S1–S11）

| 缝 | 形态 | 覆盖钉 |
|---|---|---|
| S1 | `createConsumer()` protected 缝 + 探针 KafkaConsumer subclass（覆写 `wakeup()` 计数/`partitionsFor` 返假分区/`poll` 阻塞 latch——零 broker） | 红① |
| S2 | 新测试类双 function 并发 run（`createMessageSource` protected seam + NoEngine 桩——`TestDebeziumCdcSourceFunction` :359-364/:429 既有形态）+ `LIVE_CONNECTOR_CLAIMS` 行为断言 + `DebeziumEngineWrapper.notifyEngineFailure` 显式触发（载体 API 只读使用[MN-6b 观测面锚定：pendingError 为 private 无 accessor——观测锚定 completionLatch 计数/run 终态]） | 红② |
| S3 | 新测试类（禁入 `TestBatchLoaderSourceFunction`——C10-016）：stub `IBatchLoaderProvider`（load 阻塞 latch + 记录 interrupt 位）+ runThread interrupt 递送断言 | 红③ |
| S4 | `TestDebeziumResourceManagement` :41-64 区 same-diff 改写（Throwable 判别）+ `TestDebeziumCdcSourceFunction` 并列 typed 断言（追加） | 红④ |
| S5 | 手工桩 embeddedFunction（泵满 256 队列后卡 stub stop）+ queue 预填 + close 超时窗（双桥各自测试类；face③ = loader close 抛错桩 typed 断言） | 红⑤ |
| S6 | `closeResourcesAfterFailedOpen` 包内直调（真 native 对象 isOwningHandle 白盒）+ dir-is-a-file typed leg + compile-red 三件套 | 红⑥ |
| S7 | 源序结构钉（restoreRangeInto 源文本 handles-close 先于 src-close 断言）+ 变异探针（还原原序→红→复修→绿）+ `TestRocksDBIncrementalRestoreFailFast` round-trip 并列 | 红⑦ |
| S8 | 临时输出目录孤儿注入（自后缀/他后缀/白名单三分集）+ beginTransaction 触发 + 残留断言 | 红⑧ |
| S9 | fd-delta 腿（`UnixOperatingSystemMXBean` delta + `@EnabledIf` 门 + 预热 + N 次异常迭代）/受限降级=结构腿（TWR 源文本断言）+变异探针 | 红⑨ |
| S10 | 陈旧 `rocksdb-restore-range-{旧id}` 注入 + stub segment store 中途失败 → 清扫断言 + finally 兜底断言 | 红⑩ |
| S11 | `setTtlSweepIntervalMillis(1)` 微间隔 + `setTtlTimeProvider(FakeClock)` + checkpoint 关闭 + setCurrentKey 自动 sweep 断言 + ≤0 关闭 leg（`TestRocksDBStateTtl` 追加零改写） | 红⑪ |

### 红①–红⑪ 驱动器总表

| 钉 | 工单 | 驱动器形态 | pre-fix 红判据 | 伪绿防线 | 闩带 |
|---|---|---|---|---|---|
| 红① | W-01 | S1：探针 consumer 挂 run() 消费期（poll 阻塞 latch）→ cancel() → 断言 wakeup 计数 ≥1 + run 线程预算内终态；armed 腿（cancel 先于 run → partitionsFor 即抛→优雅退出断言）；零 broker 依赖 | pre-fix cancel 零 wakeup 调用（计数=0）且 run 阻塞至 latch 释放 =红（**当前即红，确定性**） | wakeup 计数（非日志断言单依赖）+ 优雅退出双 leg；`WakeupException` while-running typed 腿防「catch 全吞」退化；变异=移除 cancel 内 wakeup → 红① 必红 | ≤10s |
| 红② | W-02 | S2：同 config.name 双 function 并发 run → (a) 第二实例 typed 拒绝（`ERR_STREAM_CONFIG_ERROR` 判别）；(b) **[M1 存活反例腿]** 第一实例 listener 存活——显式 `notifyEngineFailure(name, err)` → 断言第一实例 pendingError 可见/latch 计数（错投即第一实例零信号 = red）；(c) 第一实例正常完成 | pre-fix 第二实例静默覆盖（notify 错投第二实例=路由错断言红/第一实例假活）=红（**当前即红，确定性**） | (a)(b)(c) 三 leg 齐备防「守卫存在但存活未证」；R5-CON-03 注释块/`TestDebeziumCdcSourceErrorPropagation` 零改写并列；两参条件移除防「拒后误删」变异腿 | ≤15s |
| 红③ | W-03 | S3：stub loader.load 阻塞 latch → cancel() → 断言 load 内观测中断位（递送实测）预算内成立 + run 预算内退出 | pre-fix cancel 后 load 持续阻塞至 latch 手动释放、中断位零置位 =红（**当前即红，确定性**） | 递送位实测（`Thread.interrupted` 于 stub 内采样）非仅无异常；driver 不响应形态由 owner doc 声明边界承载（非钉面）；`TestBatchLoaderSourceFunction` 零触碰并列 | ≤10s |
| 红④ | W-04（=C10-015 同 diff） | S4：并发第二 run() → 断言 Throwable 为 typed `StreamException`（re-entered 判别）——不允许静默 return；`:64` 反转断言 + 消号理由登记 | pre-fix `secondRunReturned=true` 且零异常（假 EOS）=红（**当前即红，确定性**——反转断言直击） | typed 实例+消息判别（防 catch-all 复发）；`TestDebeziumCdcSourceFunction.testRunEnteredAtomicPreventsDoubleEntry` 零改写并列（终止性面）；runEntered 所有权 leg（抛出后字段仍 true——在途 run 所有权不变式） | ≤10s |
| 红⑤ | W-05 | S5：queue 预填满 + runner 卡 stub source.stop() → close() → (a) close typed 抛出断言（超时信号零弱化）；(b) 分发线程预算内解除（put 返回/interrupt 递送/线程终态）；(c) clear 执行断言（队列空）；Debezium 桥同形 leg + face③ loader-close typed leg 随组 | pre-fix close 先抛且 clear 跳过、分发线程永久阻塞（预算后仍 alive/put 未返回）=红（**当前即红，确定性**——CLOSE_JOIN_TIMEOUT 5s 在闩带内） | (a)(b)(c) 三 leg 齐备防「只 clear 不 interrupt」与「只 interrupt 不 clear」半修；2536 byte-bound 钉/2503 停止契约钉/2511 last-emitted 钉零改写并列 | ≤30s（join 5s+解除窗） |
| 红⑥ | W-06 | S6：(a) helper 白盒——真 dbOptions/cfOptions/Cache/WBM/Bloom + handles 半成品 → helper → 逐对象 `isOwningHandle()==false`（≥3 断言）；(b) dir-is-a-file typed leg（消息/码/param 校准断言）；(c) 结构腿 = **compile-red 三件套**（M1 明文——编译日志快照+structure-grep+stash 留痕） | pre-fix helper 不存在 =**编译红腿**（诚实留痕——禁伪绿）；(b) 形态 pre-fix 行为 leg 可红可绿按失败形态（dir-is-a-file 本就 typed——校准腿声明，非红依据） | (a) 白盒计数+isOwningHandle 三断言（防「helper 存在但漏关某对象」）；(c) 三件套防存证灭失（Major-D1 教训）；红⑥ 行为校准 = (b)（诚实登记其非红判据） | ≤5s（白盒同步） |
| 红⑦ | W-07 | S7：(a) 结构钉——restoreRangeInto 源序断言（handles close 先于 src close）；(b) **变异探针**（还原 TWR 原序 → 钉红 → 复修复 → 绿——回退论证留痕，恢复后 assert 目标行存在[R5 探针纪律]）；(c) `TestRocksDBIncrementalRestoreFailFast` round-trip 功能绿并列 | pre-fix 结构钉红（TWR 序=先 src 后 handles）=红（**结构红，确定性**）；(b) 为回退必红证明（**禁行为伪红**——native UAF 非确定性不构钉） | (a) 源文本序断言（非反射启发式）；(b) 探针留痕链（还原-红-复修-绿四相日志）；(c) 功能零回退并列防「重排破坏 restore」 | ≤30s（round-trip） |
| 红⑧ | W-08 | S8：临时输出目录注入三分集（自后缀孤儿 `.epoch-7.tmp` / 他后缀在途 `.epoch-9.s1.tmp` / 白名单恢复 pending temp）→ beginTransaction → 断言孤儿删、两反例零删；face② manifest temp leg 随组 | pre-fix 自后缀孤儿残留 =红（**当前即红，确定性**）；反例腿为 post-fix 防退化 leg（pre-fix 两反例同样残留=缺陷面） | 三分集齐备防「清扫存在但白名单误杀/后缀越界」退化（M1 红线落钉）；`TestFileTwoPhaseCommitSink` abort 删 tmp/kill-recover 无残留钉零改写并列 | ≤10s |
| 红⑨ | W-09 | S9 主形态：perm 注入不可读子目录 + N 次迭代 → fd-delta 有界断言（`@EnabledIf` 门）；**降级预案**（裁定 C.4——环境受限留痕）：结构腿（双点 TWR 源文本断言，pre-fix 红=结构红）+变异探针 | 主形态 pre-fix fd 净增 ≈N =红（**当前即红，CI 非 root 形态**）；降级形态 pre-fix 结构断言红（**结构红，确定性**） | fd 腿+结构腿双轨（单轨禁用须留痕降级理由）；happy-path 回归（sample/discoverSplits 正常遍历零变化）并列防「TWR 破坏穷尽自关语义」；SnapshotStrategy 写法对齐结构核对 | ≤15s |
| 红⑩ | W-10 | S10：baseDir 注入陈旧 `rocksdb-restore-range-{旧id}`（含垃圾文件）+ stub segment store 中途失败 → (a) 入口清扫断言（旧 id 目录删除、当前 id 零触碰）；(b) mid-reconstruct 失败 finally 兜底断言（reconstructed 目录零残留）；(c) seed 调用序零触碰结构核对 | pre-fix 旧 id 残留/中途失败残留 =红（**当前即红，确定性**） | (a)(b) 双 leg 防「入口清了但 finally 仍漏」半修；(c) 防 seed 序扰动（红线 8 的钉化）；`TestRocksDBIncrementalRestoreFailFast` 零改写并列 | ≤30s |
| 红⑪ | W-11 | S11：TTL state + FakeClock 推进过期 + checkpoint 关闭（不调 snapshotState）+ setCurrentKey（微间隔门）→ 断言过期 entry 自动回收 + sidecar 收敛；`≤0` 关闭 leg（自动零触发+手动 sweep 生效）；既有手动 sweep 用例零改写并列 | pre-fix setCurrentKey 后过期 entry 零回收（唯一触发点 snapshotState 未运行）=红（**当前即红，确定性**） | 自动触发行为断言（非字段反射）；`≤0` leg 防「hook 常开不可关」退化；手动 sweep 用例并列防「hook 破坏既有 sweep 语义」；Memory 后端本体零触碰核对 | ≤10s |

### 零回退钉全列

- **基线签名（plan 勘误 4/m2-5，2026-11-07 亲跑 @ `22ed724af5`；`51fe88f737` 零 nop-stream diff 继续有效）**：`./mvnw test -pl nop-stream` 分解通道 = **4481 tests / 1F[F1 环境归因 solo 三联绿在案] / 0E / 20S**（core 2008 1S / cep 394 / flow 315 / connector-jdbc 72 / debezium 82 6S / batch 62 / kafka 26 / rocksdb 153 / connector 116 / runtime 1253 1F 13S）。零回退实证 = 各模块 tests/F/E/S 签名比对（与 2536 收口签名逐字一致——本基线即「2536 收口后现状签名」）+ 新钉增量；F1 按 E3 判据处置。
- **2511 全部钉（红线 5）**：压窗钉（testC1_002_*）、TestCepOperatorSubtaskCopy 反转钉、挂死三件（TestAvroWireFormat）、保留双钉（TestJdbcEpochLedgerRetention_C3_002/TestFileTwoPhaseCommitSinkManifestPrune_C3_002）、驻留钉（TestRocksDBSnapshotRestore 键数参数化+opt-out leg）、last-emitted 钉（TestDebeziumLastEmittedOffsets_C8_001）、裕度钉（TestKafkaTwoPhaseCommitSinkLifecycle）、TestDebeziumCdcSource/TestDebeziumCdcCheckpoint 改写外用例——钉内断言全批 diff 零触碰。
- **2536 全部钉**：`*R403` 测试类 10 个（live grep 亲证全列 = TestCepOperatorMatchEnvelopeR403 / TestDebeziumCdcReaderByteBoundR403 / TestJdbcSinkEnvelopeR403 / TestFileSinkEnvelopeLockR403 / TestMessageSourceReaderByteBoundR403 / TestCheckpointSnapshotBudgetValidationR403 / TestAvroSchemaRegistryClientLifecycleR403 / TestAvroWireFormatNegativeCacheR403 / TestRocksDBStateMigrationStreamingR403 / TestRocksDBSnapshotHashPolicyR403）+ 3 既有适配 + E-2536-1~7 执行期登记产物——零触碰零回退。
- **8.41 受害侧钉**：`TestRocksDBKeyedStateBackendCrossThreadOps` 零触碰零重开。
- **既有锚点名（本批零回退锚）**：TestKafkaSourceFunctionSharding / TestBatchLoaderSource / TestBatchLoaderParallelismGate / TestBatchConsumerSinkFunctionCloseLogging / TestMessageSourceFunctionLifecycle / TestMessageSourceFunctionRestart / TestMessageSourceFunctionThreadSafety / TestFileTwoPhaseCommitSink / TestFileTwoPhaseCommitSinkManifestPrune_C3_002 / TestFileSourceSample / TestFileSourceReaderRecovery / TestFileSourceCheckpointRestore / TestConnectorResourceManagement / TestDebeziumCdcSourceFunction / TestDebeziumResourceManagement（改写半径外用例）/ TestDebeziumCdcStopVerification_C3_014 / TestDebeziumCdcSourceErrorPropagation / TestRocksDBIncrementalRestoreFailFast / TestRocksDBStateTtl / TestRocksDBSnapshotRestore / TestRocksDBMemoryControl / TestRocksDBKeyGroupRangeRestore / TestRocksDBRestoreGuards / TestDebeziumReal* 3 项+CrossDbSyncPipeline/CredentialIntegration（docker assumeTrue 门控——E3 噪声源）——钉内断言零改写（红钉为追加用例/新类形态，既有用例只增不改）。
- **已知环境面集（E3 噪声源预案——plan 勘误 4/5 沿用）**：面① nop-core TestResourceCacheInvalidationChain 负载 flake（solo 三联先例）；面② runtime F1 TestStreamConnectorRegistryE2E（**本轮 baseline 现形+solo 三联绿留痕在案**——禁静默重跑）；面③ runtime docker 双 E2E 慢跳过/fork 超时；面④ debezium 6S 环境门控；面⑤ format-avro/sql/fraud-example 反应器跳过（Phase 3 补齐）；面⑥ E1 在线模式 nop-ai-translate 面（预计不现形）。

### C10 映射（对齐纪律第一条）

| C10 条目 | 承载面 | 本批关联工单 | 零改写/兑现核对面 |
|---|---|---|---|
| C10-015（**验收前置，同 diff 绑定**——批次行增补+cra-index :392） | 折叠断言改写（TestDebeziumResourceManagement :41-64 区） | W-04（红④） | 改写=同 diff 必改项（禁拆分）；消号理由登记（裁定 B.2）；旧断言外的该测试其余用例零触碰 |
| C7-003 同步配测侧记（「修复排批时应同步配测试」） | 跨 id 残留清扫自动触发钉 | W-10（红⑩） | 侧记义务由红⑩ (a)(b) 双 leg 兑现——C10-test-protection :75 明文 |
| C7-010 同步配测侧记（「测试手动调 sweep 钉住零自动触发」假保护形态） | 自动 sweep 触发钉+既有手动面零改写 | W-11（红⑪） | 侧记义务由 S11 三 leg 兑现（自动触发/≤0 关闭/手动面并列）——同上明文 |
| 既有 C10 面零改写清单 | 2511 改写钉六条/8.41 受害侧钉/2536 `*R403` 全列/C10-016（`TestBatchLoaderSourceFunction` 零触碰） | 全批 | 红线 5/6/7/8/9 全集——closure audit git diff 逐钉核对 |

其余红①②③⑤⑥⑦⑧⑨ 为无 C10 号工单（merge-adjudication §4.4 :229 注——其余 P2 无 C10 登记）——E2 泛在钉承载，形态见驱动器总表。

## 条件申报登记（禁静默扩权——预设全部不触发，逐面亲读依据）

①**`*-api` 契约叶 → 预设不触发**：11 工单 import 面亲读——`StreamException`/`NopStreamErrors` 族为使用面引用零 diff；`ConnectivityCheckable`/`SourceReader`/`IBatchLoaderProvider`/`IBatchTaskContext` 均为既有接口使用面（零签名改动——A.2 身份守卫/B.1 认领注册表全为模块内新增私有/包内面）。②**orm/DB 承载 → 预设不触发**：W-08 清扫为文件系统面（plan 预设禁选维持）；五模块 `model/*.orm.xml` 零触碰。③**平台设施本体 → 预设不触发**：KafkaConsumer/JDK 并发原语/Files.walk/ReentrantLock 均为使用面；W-03 的 `IBatchTaskContext` 钩子形态**禁选**（nop-batch-core 跨模块——A.2 备选否决，申报点关闭）；W-02 载体面（nop-message-debezium）**禁选**（R4-24 承接——B.1 备选否决，申报点关闭）；core `MemoryKeyedStateBackend` 零 diff（D.4 移植为 rocksdb 侧新增）。④**`_` 生成物/pom 结构 → 预设不触发**：零新错误码（全部复用在位码）；新测试落既有测试树（`*R404` 新类）；零新配置键文件（W-11 setter 注入形态、其余工单零旋钮）；站点文件零 `_` 生成物、rocksdb/cep main 树无本批涉 `_gen` 面。**任一条件申报触发 → 按 AGENTS.md 四件证据链走完方可实施；未申报前实施=禁。**

## 零触碰边界总表（8.17 产物面 / 8.43 产物面 / R4-24 与批外模块 / P3×9 与 deferred / `*R403` 钉）

- **8.17/2511 产物面（同文件跨批——禁顺带逐面）**：RocksDBKeyedStateBackend 增量缺省（:125）+阈值守卫（:930-941）+recorder 接线（:319-320/:1175/:1184-1186）——本批触 openDB catch（C.3）/restoreIncremental 入口清扫（D.3）/setCurrentKey 挂钩（D.4）三面，面边界 diff 分组登记禁顺带；FileTwoPhaseCommitSink 保留窗口（:90-97）+prune 链（:565-587/:477）——本批触 beginTransaction 清扫（D.1），manifest 零写路径；DebeziumCdcSourceReader/Function last-emitted 链（红线 4）——W-05/B 组只触 close/注册/守卫面；2511 保留 cleaner（Backend/File）零触碰。
- **8.43/2536 产物面（同文件交界——头号新增边界逐面）**：双桥 byte-bound 守卫段（红线 6，K9 锚）；`seedIndexFromHandles` 调用序（红线 8，K5 区）；manifest 四旋钮（:381-389/:417-425）+锁预算（withManifestLock :623-694，红线 7）；RocksDBIncrementalSnapshotStrategy new-only 哈希面（:162 区邻接零触碰——W-09 仅引其 TWR 写法形态）；CEP/Jdbc/Avro/format 面（本批零站点）；全部 `*R403` 钉+新配置键全集（红线全集）零触碰零回退零顺带。
- **R4-24 与批外模块（零 diff 红线）**：`DebeziumEngineWrapper`/`NopStreamOffsetBackingStore`（载体注册表/同名桶——B.1 in-scope 守卫为唯一触碰面，测试只读使用 `notifyEngineFailure` 载体 API 零 diff）；nop-batch-core（W-03 禁选形态）；nop-stream-core 产品源码预设零 diff（`TwoPhaseCommitSinkFunction` 恢复链 :234-277 区为 W-08 机制证据零触碰、`MemoryKeyedStateBackend` :222-245 为 W-11 形态 fact-reference 零触碰）；runtime/flow/sql/fraud-example/kafka 产品源码/cep/format-avro/connector-jdbc（本批零站点）。
- **P3×9 相邻面（防混登记——Deferred 承载）**：C2-006（saveState 锁内 I/O）/MA-19（静态锁表）/C6-014（deleteIfExistsQuiet 空 catch——D.1 显式不复用）×W-08 同文件零触碰；C2-007（FileSourceReader 监视器 I/O）×W-09 同文件族零触碰；C7-011（checkConnection 声明）×W-01 probe 面相邻零触碰；C7-008/C5-003/C6-013/MA-20 零交集。执行期意外触及禁顺带——四件证据链申报或回退。
- **G06/G10/C10-016/8.45+**：MA-12 条化激活（零成员交集）/C8-008 深度遥测（typed param 承载最小可见性）/C10-016（`TestBatchLoaderSourceFunction` 零触碰——W-03 新钉禁入）/R4-05 起 1.3 单元（零站点交集）——全部维持 plan Non-Goals。
- **既有裁定文档**：2511/2536 设计文档、batch-proposal、merge-adjudication、C10 台账、cra-index 台账行——零回写（收敛注记归 Phase 4）；本设计文档为唯一新增文档面（命名调整登记=plan 2537 三处行内订正，不改其批次行原文）。

## 端到端与接线验证义务（rule 22/23——随 Phase 2 落证）

- **端到端（rule 22——plan Phase 2 两链）**：≥1 条恢复链覆盖 W-07/W-08/W-10 组合面（增量恢复入口 → 陈旧目录清扫 → 中途失败清理 → 孤儿 temp 判定——从 restoreIncremental 入口到恢复终局）随红⑦⑧⑩承载；≥1 条停机链覆盖 W-01/W-03/W-05 组合语义（cancel 触发 → wakeup/interrupt/clear-first 全通道——从 cancel() 入口到线程终局；debezium 面 gated docker 测试不强制入 CI，本地留证）随红①③⑤承载。
- **接线验证（rule 23——十项）**：wakeup 真生效（红① 计数）；interrupt 真生效（红③ 递送位实测）；守卫真生效（红② (a) typed 实测）；存活反例真生效（红② (b)——M1 判定的接线证明）；typed 真生效（红④ 终态形态实测）；clear+interrupt 真生效（红⑤ (b)(c)）；清理真生效（红⑥ (a) isOwningHandle 实测）；顺序真生效（红⑦ (a)(b)）；清扫真生效（红⑧ 三分集/红⑩ (a)(b)）；sweep 真生效（红⑪ 自动触发实测）；TWR 真生效（红⑨ fd 腿/结构腿）。

## 批外新发现登记（亲读发现即登记，禁瞒）

- **BF-1（P3 量级——C6-005 同族兄弟的独立站点面，audit 已登记、本批由 C.2 结构性闭合后在册残面零扩）**：`RocksDBIncrementalRestore` 中 `openReadOnlyTyped` 的其他调用点（如有）与 restore 路径外的 open 失败形态——C.2 的 finally restructure 已覆盖 restoreRangeInto 调用点；本登记确认无新增未登记泄漏站点（六处 TWR 目录流 + openDB 唯一泄漏面均已对齐——W-09/K1 六锚亲证）。零本批处理（无新工单面）。
- **BF-2（信息性——probe 探测红线声明缺口与 C7-011 关系澄清，非缺陷）**：`KafkaSourceFunction.checkConnection` 的 max.block.ms 阻塞上界在 owner doc 无行——C7-011 P3 红线表缺口同族；本批以 E5 取消时延声明的 probe 段承载声明（零产品 diff），C7-011 本体归 successor 零顺手修。
- **BF-3（信息性——`runEntered` 无重入守卫的同族面确认，audit C3-011 失效面②③④已登记）**：`MessageSourceFunction`/`BatchLoaderSourceFunction`/`KafkaSourceFunction` 无 run() 重入守卫——同异常形态下双 run 的退化面为 audit 已登记独立面，**不在批次 11 finding 内**（merge-adjudication §2.2 :75 独立案仅 C3-011）；本批零顺手加守卫（禁顺带——W-04 的 typed 形态为其 successor 参照形态，登记不立项）。
- 核证范围 = 11 工单站点文件全量亲读（K1-K12 锚清单）+ core 两 fact-reference 文件（TwoPhaseCommitSinkFunction/MemoryKeyedStateBackend）+ 测试树全量锚点名核对 + R07 形态文件（零交集确认）+ TestMemoryStateTtlSweep 缝先例；除 BF-1/BF-2/BF-3 外无批外缺陷。

## 评审记录

- **设计评审 R1**（2026-11-07，agent_28bc9798 fresh session 只读 @ `51fe88f737`，live 抽验 ~75 处锚点）：**PASS-with-revisions——0 Blocker + 3 Major + 7 Minor，全部随轮落盘，无需 R2 放行 Phase 2**。**MJ-1**[Major] 守卫 pre-try 抛出位使被拒实例自身 runEntered 永久滞留 true（CAS 后 try 前，finally 结构性不存在；region restart 复用实例撞 W-04 CAS 误导拒绝）——补 pre-try 抛出复位规则[不采 claim 前移：claim 泄漏面]+红② 可重试不变式；**MJ-2**[Major] C.1 interrupt 归因错置（interrupt 打在 runnerThread，put 阻塞者在总线发送方/引擎分发线程——clear() 才是生产解除通道；三分段改写+红⑤(b)/rule-23/K9 重锚）；**MJ-3**[Major] D.1 双白名单前提半虚高（begin 时刻还原 pending 已被消费——pending 白名单唯一实保护=commit 失败保留 temp；红⑧ leg(c) 重定义二选一：commit 失败保留边构造或 manifest 覆盖注入）。MN-1 resolve 随迁出 try（否则 M1 破功）/MN-2 at-least-once 方向翻转（> 非 ≤）/MN-3 单调性前提诚实化/MN-4 nonSstDir 随迁/MN-5 时延完备句/MN-6a 存证副本对冲 `_tmp/` 易失+MN-6b 观测面锚定 latch/终态/MN-7 插行锚勘正。三风险专项：红② 载体 API 成立（public static+生产同路径零分叉）/红⑥ 三件套纪律足够（MN-6a 副本对冲）/W-08·W-11·W-01 声明边界诚实（W-08 经 MJ-3 改述、W-11 :422 兑现核证、W-01 MN-5 补句）。M1 兑现核证：认领先于 try 结构位成立+MN-1/MJ-1 为前置修订。

- **设计评审 R1**：（预留——独立评审子 agent fresh session 只读完成后回填：结论/修订清单/live 抽验规模/想象性分析三风险点结论）
