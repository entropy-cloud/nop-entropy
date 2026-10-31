# 2521 CRA Phase 8.27 — R2-18 4.2 cluster 批（4 项：AbstractLeaderElector doStop 复活锁序（leaderEpoch+监听器序，MA-01 六条族一处闭合）+ ServiceInstance 不可变化（12 读者站点）+ Nacos 订阅快照版本守护 + ClusterRpcClient.callAsync 回调同步异常 completeExceptionally（Sentinel entry 释放，R21 future 族纪律））

> Plan Status: active
> Last Reviewed: 2026-11-01
> Draft Review R1: agent_03adeaa1 @ 2026-11-01 —— 无 Blocker；2 Major（M1 裁定①预设守卫形态自相矛盾[LifeCycleSupport 无 isStopped() 且 allowRestart=true 使 stop 返回回 CREATED 后 isStopping()=false 结构性失明——预设改为独立 volatile stopped 标志+「stop 返回后窗口」三场景显式登记]；M2 C10-003「全条入批」与 Phase 2 组 A 工单枚举不一致[组 A 补列建议(2)(3)(4) 三钉，建议(4) 时延阈值形态裁定改结构性形态不降 watch]）+3 Minor（M3 裁定③注入缝点名子类 override 为预设、M4 Closure 占位补 Audit Session 行、M5 Phase 2 Exit Criteria 补中断回补义务）——协调方机械修复闭环，修订转 active
> 起草说明：本 plan 由独立起草子 agent 于 2026-11-01 在工作仓 `nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation` @ `a9fc5b01a7`）起草；全部站点行号为该基线 live 亲读回读值（勘误节逐条登记漂移核对结果），执行前如再漂移以 live 为准并登记。结构沿 plan 2520 先例（Source 全文引用 / Draft Review 行预留 / PA 判定表 / 对齐纪律显式条款 / 成员逐条表 / 勘误节 / 红绿判据 / C10 半边口径 / 环境三联预登记）。待独立 draft review 通过后修订转 active。
> Source: roadmap 工作项 8.27（R2-18，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L169，Status=todo[本 plan 立项时回写为「todo（plan 2521 drafted，待 draft review @ 2026-11-01）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-18 行（L92 全文：finding ID 清单 4 项[4.2-C1-004；4.2-C1-001；4.2-C1-003；4.2-C3-001] / 模块与验证命令 nop-cluster `./mvnw test -pl nop-cluster -am` / Owner Doc=nop-cluster 无 03-modules owner doc→缺口声明；`docs-for-ai/01-repo-map/module-groups.md` / PA=无 / 依赖「无前置（可并行）；MA-07 R21（吸收成员 C3-008/C8-008 同 diff 在 R4-25 落地）」 / exit criteria 增补：**doStop 复活锁序修复（leaderEpoch+监听器序）；ServiceInstance 不可变化（12 读者站点）；订阅快照版本守护；callAsync 回调同步异常 completeExceptionally+Sentinel entry 释放**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 4.2 审计目录 `ai-dev/audits/2026-10/2026-10-21-0240-deep-audit-nop-cluster-concurrency/`（C1-shared-mutable-state.md[C1-001 :44-88 / C1-003 :120-145 / C1-004 :147-178 + 复核节 :315-318/:326 行号勘误]、C1-round2.md、C2-lock-sync.md[C2-003 :100-122 + 复核 :170/:172]、C2-round2.md、C3-async-chains.md[C3-001 :46-81 + 复核 :396]、C3-round2.md、merge-adjudication.md[§一 :28 扩员表 / §二 MA-01 :32-45 / MA-07 :87-99 / §三 独立 #1 C1-001 修复点 / §四 :146 双轨终裁]、C10-test-protection.md[C10-001 :124-143 / C10-003 :165-186 / C10-004 :188-206 / C10-005 :208-226 + 复核 R1 :446 并入 / C10-006 :228-247 + 反向钉死 #1 :92-95]、summary.md[:39 P1 全清单/:58-61]）；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` :525（WI 4.2 节 13 条表）+ 小结行 :799[**4.2（14，P2×4/P3×10）**——C10-001→MA-07(P1)+MA-09(P2)；003→MA-01(P1 全员)；004→独立#6 C4-004+MA-01（C4-002 注记）；005→独立#3 C1-005/**独立#2 C1-003**/MA-03/独立#4 C2-002/独立#13 C3-011；006→**独立#1 C1-001(P1)**+MA-02(P2)——本批承载 C10-001 的 MA-07 半边、C10-003 全条、C10-004 的 MA-01 半边[C4-004 半边已由 plan 2496 收敛]、C10-005 的 C1-003 半边、C10-006 的 C1-001 半边；半边口径见勘误 6]；P1 台账 = cra-index `ai-dev/audits/cra-index.md` :269–272（live 2026-11-01 回读：R2-18 open 四行 = :269[C1-004（MA-01 主，←C4-001/C2-003/C3-004/C2-005/C4-002 六条族）]/:270[C1-001]/:271[C1-003]/:272[C3-001（MA-07 主，行内 ←C3-008/C8-008 同 diff 注记——落地边界归 R4-25，见勘误 8）]——**4.2 单元 P1×4 全部且仅为本批成员**，nop-cluster 在 P1 台账无其他行[awk 全表核对 4 行]；P2 台账 G65 :449[R4-25 束 9 条]/G66 :450[globalTimer/自续链搭车束 2 条——C4-003/C4-004 已由 plan 2498/2496 收敛]批外）；4.2 计数口径 = cra-index :68；执行基线 @ `a9fc5b01a7`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.26 已 done，plan 2495–2520 均已完成或收口）
> Related: plan 2496（completed——R1-01 绝缘层批已落 `AbstractPollingLeaderElector` C4-004/CL-011/CL-014 监督（提交 `8c1b1505aa`：pendingCheck 保留 :53/:103 + 业务标识告警 :111-118 + runGuardedElectionRound finally 重臂 :131-147），**javadoc :38-39 明文「C4-002/C1-004 stop/restart lifecycle face is owned by R2-18——no cancel-on-stop is added」**——本批 C1-004 承接的正是该预留半边，两批边界以该 javadoc 声明互证）；plan 2498（completed——R1-03 已落 `ClusterRpcClient.getRpcClient` C4-003 整体 deadline（:147-150 注记在案），同文件续作边界 = callAsync 重试终结面，互不重叠）；**下游 R4-25**（G65，roadmap 8.65=todo，软依赖 R2-18）：C3-008/C8-008 吸收成员的「同 diff 改写」义务（MA-05 反向钉死 #1：`TestSentinelFlowControlRunner.testSupplierSyncThrowPropagatesSynchronously` 改写为 rejected future+异常统计新断言）在 R4-25 落地，本批只落 C3-001 主半边——边界见 Non-Goals/对齐纪律/勘误 8；**R21 族纪律**：family-clustering :65 future-永不完成族（chain1）列 4.2-MA-07 为 P1 成员，:596 六例关系行「修复方案对齐 completeExceptionally 纪律，不互相吸收」——本批 C3-001 修复模式受该族纪律约束（勘误 9）；R2-02（R21 纪律先例 completeExceptionally）/R2-12（C8-012 R21 纪律）已收口为族内范式互证。结构模板 = plan 2520（PA 判定表/勘误登记/红绿判据/C10 半边口径/环境三联预登记）与 plan 2519/2518
> Protected Area 授权：批次 PA=**无**（batch-proposal R2-18 行 PA 列显式「无」）。**模块结构核实（live find 亲读）**：nop-cluster 聚合器 6 子模块 = nop-cluster-core/nop-cluster-sentinel/nop-cluster-nacos/nop-cluster-admin/nop-hazelcast（nop-hazelcast-core+cache）/nop-rpc-cluster，**无 nop-cluster-api 叶模块**——「跨模块公共 API（nop-*-api）plan-first」保护类目对本批无载体；nop-api-core（`io.nop.api.core.*`）零触碰（`NopException`/`ICancelToken`/`IRpcService`/`ApiResponse` 均纯消费）。**头部预设：4/4 成员零 `model/*.orm.xml` 触碰、零 `_` 生成物触碰（`src/main/resources/_vfs/**beans*.xml` 为手工维护源非生成物[git log 证实特性提交直接改写]，本批预设亦零触碰——修复全部为纯 Java+测试）、零 nop-api-core 触碰**（PA 判定表逐一核实）。站点分布于 nop-cluster-core（C1-004 elector + C1-001 读者面 ServiceInstance）、nop-cluster-nacos（C1-001/C1-003 写面 NacosNamingService）、nop-rpc-cluster（C3-001 ClusterRpcClient）三模块。

## Purpose

关闭 R2-18 批的 4 项已确认缺陷（P1×4；cra-index open 四行 :269–272 批次列=R2-18，live 2026-11-01 回读全 open；WI 4.2 单元 P1×4 即本批全部成员——P2×13/P3×5 归 R4-25 束[G65]/条化/各本体，C10 缺保护 14 条中 5 条与本批成员对应、随批承载 4 条半边口径[勘误 6]）。核心结果面有四：①**AbstractLeaderElector doStop 复活锁序修复**（C1-004，MA-01 主，←C4-001/C2-003/C3-004/C2-005/C4-002 六条族一处闭合）——无锁 stop 与 synchronized onElectionCompleted 竞交：stop 后 leaderEpoch/electionPromise 复活为「本节点是 leader」+ 监听器乱序回调（allowRestart=true `LifeCycleSupport:26` 停止后回 CREATED + 生产子类 SysDao/JdbcLeaderElector checkElection DB I/O 宽窗口；选主语义违约非 mere 数据竞争）。②**ServiceInstance 不可变化**（C1-001）——Nacos 事件线程经 copyFromInstance 原地改写共享实例对象（全字段非 volatile 无 happens-before）+ `_host` 惰性缓存固化陈旧端点，请求线程撕裂读/陈旧建连（12 读者站点失效面：LB 权重快照/健康摘流/host 匹配/zone/tag/版本路由/baseUrl 拼接）。③**Nacos 订阅快照版本守护**（C1-003）——subscribe 先于 getAllInstances 的初始化编排无版本/时序比较，事件 B 落在查询返回与 initInstances 之间即以陈旧快照 A 无条件覆盖新事件态（复核补强：窗口为本模块代码段自有，不依赖 SDK 乱序；陈旧态存活至下一次无关变更事件）。④**ClusterRpcClient.callAsync 回调同步异常 completeExceptionally**（C3-001，MA-07 主；R21 future 永不完成族）——重试递归体在 whenComplete 回调内同步执行，异常只被丢弃的返回 stage 捕获，手工 future 永不完成调用方永久挂起 + SentinelFlowControlRunner entry 泄漏复合（live 默认装配路径 `rpc-cluster-defaults.beans.xml` :85-97 AbstractRpcProxyFactoryBean→ClusterRpcProxyFactoryBean 证实，全平台 @RpcClient 代理经此链）。批末 roadmap 8.27 → done，cra-index 四行收敛 + C10 成员面注记。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-18 = 4 项成员全 P1（C1-004[MA-01 主，吸收 C4-001 双轨终裁 P2/C2-003 P2/C3-004 P2/C2-005 P2/C4-002 P3 五成员——§一 :28「C3-004、C2-005 扩入 MA-01」+ 复核已登记 C4-001/C2-003/C4-002 三条]；C1-001[独立]；C1-003[独立]；C3-001[MA-07 主]）；cra-index live 2026-11-01 回读 R2-18 批次列 open 四行 = :269–272；roadmap 8.27 行 Status=todo。依赖核对（live 2026-11-01）：**无前置（可并行）**——batch-proposal R2-18 行依赖列明文「无前置（可并行）；MA-07 R21（吸收成员 C3-008/C8-008 同 diff 在 R4-25 落地）」，本批无跨批硬依赖；R21 为族纪律引用非批次号（勘误 9）；C3-008/C8-008 吸收成员同 diff 改写义务在 R4-25 落地（见勘误 8），不构成本批前置。
- **MA-01 吸收成员随主同 diff（不单列工单；batch-proposal §一 schema「族主行内联吸收成员（←X）随族主 ID 承载不单列」+ merge-adjudication §二 MA-01 修复结构）**：C1-004 ←C4-001（stop 复活调度链面，双轨 P1↔P2 并入，§四 :146 终裁）+ C2-003（5 写站点锁纪律 + pending 撤销面）+ C3-004（promise 等待者时序——读取-attach 交错/stop→start 不重建）+ C2-005（promise.complete 持锁派发——取局部引用移出临界区）+ C4-002（isDestroyed 守卫空转，并入 C2-003 面）。**互为前置约束（merge-adjudication MA-01 协同约束）**：C2-005 与 C2-003 互为前置——简单「全站点加 synchronized」会放大 complete 持锁面（C2-round2 明文警告），裁定①须一次定夺锁纪律+派发纪律。**C4-001/CL-005 的 pending 保留半边已由 plan 2496 落地**（pendingCheck :53/:103/:174 + isStopping 守卫 :114/:141），本批承接 stop 撤销（cancel-on-stop）+ 终态守卫剩余半边（2496 javadoc :38-39 预留声明互证）。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` :525 WI 4.2 节 + :799 小结行 + `C10-test-protection.md` 逐条；随本体同 diff 落地[E2]）**：
  - C10-001→C3-001（ClusterRpcClient 调用链零测试保护，P2 承载 P1：现测试仅同步 call 路径最终错误码断言 `TestClusterRpc`，callAsync 重试递归零触达；本批承载**建议 (1) MA-07 半边**——Mock/stub 首跳可重试异常 + 重试体内同步抛 → 断言返回 future 有限时间 completeExceptionally；建议 (2)(3)(4)[无主类型化/退避/反向取消]对应 C3-003→MA-09[R4-25]与 C8-002/C3-009 各本体，非本批）；
  - C10-003→C1-004（选主 promise/stop 生命周期并发面零保护，P2 承载 P1，**MA-01 全员——全条入批**：现有 `TestAbstractLeaderElector` 6 用例全单线程、`TestElector.checkElection` 不自续使复活面结构性不可达；建议 (1) latch 挂起×stop 复活测试/(2) stop→start 循环双回路[对应 C4-001]/(3) promise 等待者 restartElection[对应 C3-004]/(4) 慢 continuation 不阻塞[对应 C2-005]四条建议的保护对象全部在本批主+吸收成员集合内）；
  - C10-004→MA-01 半边（自续调度链治理：**C4-004 半边已由 plan 2496 收敛**——`TestPollingElectorLoopInsulation` 3 钉即 C10-004 建议 (1) 断链监督形态，零回退钉；本批承载 C4-002/C2-003 半边建议 (2)——destroy/stop 后 scheduleCheck 停止投递断言随裁定①同 diff）；
  - C10-005→C1-003 半边（NacosNamingService 生命周期/订阅配对零保护；C10 复核 R1[=Major，:446]裁定 C1-003 并入 C10-005 并升 P2——本批承载**复核并入建议条**「subscribe 新建后首装 getAllInstances 与事件乱序交错断言快照不回退」；其余保护对象 C1-005/C3-005/C8-005/C2-002/C3-011 → R4-25/各本体）；
  - C10-006→C1-001 半边（ServiceInstance 共享可变性零保护，P2 承载 P1；本批承载**建议 (1)(2)**——_host 失效契约测试 + 写侧重建后旧引用读者视图不变；建议 (3)(4) = C1-002/C3-006[MA-02 条化面]随本体不代做）。
- **4.2 批前已收口面（live 形态登记，零触碰）**：P1×4 全 open 即本批，无已 fixed P1 行需排除；P2 面 C4-003（plan 2498 `getRpcClient` deadline :147-150）/C4-004（plan 2496 监督）/C1-009 域外收口等 G66 成员已收敛（cra-index :450）——本批 diff 不触碰 2496/2498 已落代码与钉；**反向钉死 #1**（`TestSentinelFlowControlRunner.testSupplierSyncThrowPropagatesSynchronously` live :100-114，钉定 C3-008 缺陷契约）与**反向钉死 #3**（`TestWeightedPartitionAssigner.testZeroAndNegativeWeight` live :251-270 域）均为 R4-25 改写义务，本批零触碰（防「顺手解除」越批）。
- **平台设施与测试资产盘点（live 实读 @ `a9fc5b01a7` + 基线亲跑）**：
  - **基线测试结果（2026-11-01 亲跑）**：`./mvnw test -pl nop-cluster` **BUILD SUCCESS，EXIT=0，45s**，reactor 9 项目（13-nop-cluster 聚合 + core/sentinel/nacos/admin/hazelcast×2/rpc-cluster）；**80 tests 0F0E**（core 65 / sentinel 8 / nacos 2 / hazelcast-core 1 / rpc-cluster 4；`_tmp/baseline-2521-nop-cluster.log`）——本批零回退钉的绿色基线。Maven 4 wrapper 下 `-pl nop-cluster` 即构建全部子模块（reactor summary 亲读证实），E3 命令 `-am` 额外拉上游依赖链。
  - **先例范式可引用不重开**：R21 族 completeExceptionally 纪律（R2-02[R2-17 族先例]/R2-12 C8-012「返回 future=回调挂接 future」/plan 2496 绝缘层 catch Throwable+completeExceptionally+重臂三件套）；plan 2496 断链监督形态（pendingCheck 保留+whenComplete 告警+退避重臂）与 `TestPollingElectorLoopInsulation` stub-executor 注入缝（RecordingRejectionScheduler :204）；`NopException` + `ErrorCode` + `.param(...)` 两层错误策略（`ClusterErrors.java` :18 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 模块内既有错误码）；CopyOnWriteArrayList 监听器容器（`AbstractLeaderElector.listeners` :43 既有）。
  - **owner doc 现状（2026-11-01 亲读）**：`docs-for-ai/01-repo-map/module-groups.md` 对 nop-cluster 六子模块 **0 提及**（grep 零命中），`docs-for-ai/03-modules/` 无 nop-cluster owner doc——证实 roadmap 8.27 Owner Doc 列「缺口声明」与 cra-index :68「owner doc 零覆盖（module-groups.md 对三模块 0 提及）按 drift 登记收口」；**四成员契约句无 owner 落点**——Phase 3 落点 = 缺口声明 + module-groups.md 通行句 + `docs-for-ai/04-reference/source-anchors.md` 新增 `CLUSTER-CC-001` 锚点行（现有锚点序：AGENT-CC-001 :102 / RAG-CC-001 :103 / ORM-ORCH-001 :104，新行按实际行号顺延插入）；如实现中发现契约面超出通行句容量需建 03-modules owner doc，登记 R-G 治理批去向（Non-Blocking Follow-ups 预登记，不代做）。
  - **测试资产（live 核实，21 个 @Test 文件——审计口径 13/17 的漂移见勘误 7）**：core 13 文件——`TestAbstractLeaderElector`（6 用例，全单线程；`testStopNotifiesListenersAndResetsEpoch` 顺序 complete→get→stop 竞态不可达）、`TestPollingElectorLoopInsulation`（3 用例，plan 2496 新增，stub executor 缝在案）、`TestLeaderElectorConfigBinding`、`TestAutoRegistration`（4）/`TestAutoRegistrationInsulation`（2，plan 2496）、`TestCachingNamingService`（4）、`TestPartitionResolver`（10）、`TestLoadBalance`（5）、`TestWeightedPartitionAssigner`（14，含反向钉死 #3 :251-270）、`TestPartitionAssigner`、`TestSpecificServiceInstanceFilter`（4）/`TestRouteFilter`/`TestFilteredDiscoveryClient`（5）/`TestCompositeHealthChecker`（5）；sentinel——`TestSentinelFlowControlRunner`（6，含反向钉死 #1 :100-114）/`TestSentinelFlowControlRunnerContext`（2）；nacos——`TestNacosNamingServiceCopyFromInstance`（2，仅静态拷贝 serviceName `@@` 拆分）；rpc-cluster——`TestClusterRpc`（1，仅同步 call 路径错误码）/`TestClusterRpcPollOffload_C4_003`（3，plan 2498）+ MockDiscoveryClient/ServiceA 桩；hazelcast-core——`TestHazelcast`（1 smoke）。**无 nacos SDK 运行时 mock 面、无 docker 门控测试**——四成员红钉全部为 plain-JVM 可跑面（stub 注入），无外部依赖门控。
  - **模块外 elector 生产子类族（E3 命令不触达，补充验证腿义务）**：`nop-sys/nop-sys-dao` SysDaoLeaderElector（`TestDaoLeaderElector`/`TestJobCoordinatorWithSysDaoLeaderElector`）与 `nop-stream/nop-stream-runtime` JdbcLeaderElector（`TestJdbcLeaderElector`/`TestJdbcLeaderElectorEpochMonotonicity`/`TestJobCoordinatorLeaderElection`/`TestJobCoordinatorJdbcHaIntegration`/`TestLeaderElector`）——C1-004 改基类锁纪律与终态守卫，Phase 3 须加两模块 elector 族焦点 solo 腿（`-Dtest` 限定）登记（勘误 11）。
- **模块聚合形态**：审计单元标签「nop-cluster」= 聚合器口径，E3 命令 `./mvnw test -pl nop-cluster -am` 触达三站点模块（core/nacos/rpc-cluster）+ sentinel/hazelcast/admin + 上游链，与 batch-proposal 验证命令列一致。

### 对齐纪律显式条款

- **MA-01 × 六条族一处闭合（C1-004 吸收 C4-001/C2-003/C3-004/C2-005/C4-002）**：单 diff elector 并发治理——全部状态写站点统一锁 + stopped 终态守卫（阻断 stop 后复活与完整重选主/DB 抢主）+ pendingCheck 撤销（cancel-on-stop，2496 预留半边）+ promise 重建纳入 stop/start 生命周期 + `promise.complete()` 取局部引用移出临界区 + 监听器回调停止守护（onStop 后不得再 becomeLeader/becomeFollower）——六条族不立六工单、不六处改（merge-adjudication MA-01「同一修复点」明文）；**C2-005×C2-003 互为前置**：锁纪律与派发纪律一次裁定，禁「全站点 synchronized」放大 complete 持锁面（C2-round2 警告）；与 plan 2496 已落监督代码共居一文件，监督面零回退（`TestPollingElectorLoopInsulation` 3 钉保持绿）。
- **C1-001 × 12 读者站点失效面零消费者变更对齐**：修复为写侧重建对象（copy-on-write 元素语义）后，12 读者站点（WeightedRandomLoadBalance :26-28 / LeastActiveLoadBalance :59-91 / HealthyServiceInstanceFilter :27 / SpecificServiceInstanceFilter :28 / ZoneServiceInstanceFilter :40 / TagServiceInstanceFilter :42-45 / RouteServiceInstanceFilter :61 / HttpRpcClientInstanceProvider :33-35 / MasterServerChooser :45 / PartitionResolver :101 / ServiceLoadBalanceAdapter :16-21 / ClusterRpcClient 消费链 :73/:105）**零代码变更**——读者面按不可变契约自然受益，禁「顺手」改读者站点（含 MA-08 触发前提弱化收益仅作注记，其本体 R4-25）；`ServiceInstance` 公共 API 面（setAttribute/addCleanupTask/cleanupTasks[MA-02 条化面]）零触碰。
- **C1-003 × C1-001 同写入口协同**：两缺陷共用 `ServiceInfoListener.updateInstances`（:235-277）单一写入口（initInstances :230-232 与 onEvent :214-220 双调用方）——裁定②（重建对象）与裁定③（版本守护）须在同一次 updateInstances 改造中协同落地，禁止只修一半（回退态与撕裂态共享同一入口）；测试注入缝（stub NamingService 或工厂接缝）一次裁定两案共用。
- **C3-001 × R21 族纪律对齐**：修复模式对齐 future-永不完成族先例——重试体内任何同步异常必须终结返回 future（`completeExceptionally`[预设] 或等价 handle 链），禁依赖「上层超时兜底」；**MA-07 成员 C8-002（零退避零抖动）/C3-009（反向取消联动）不随本批代做**（batch-proposal finding 清单仅 4 项 P1；两成员批次归属按 P2 台账）——重试循环改写时不得顺手引入退避调度（防越批扩面）；`SentinelFlowControlRunner` 文件零触碰（entry 释放经由 future 终结→whenComplete 触发 exit 的机制联动，非本批修改点[该文件 C3-007/C3-008 面 = R4-25 MA-05]）。
- **R4-25 边界条款（吸收成员承接归属）**：C3-001 行内 ←C3-008/C8-008 同 diff 注记（cra-index :272 / family-clustering :65 / summary :61 三登记位）的**落地批 = R4-25**（batch-proposal R2-18 依赖列明文 + R4-25 行 :140「MA-05 反向钉死 #1 同 diff 改写（rejected future+异常统计新断言）」承载）——本批 C3-001 只落主半边（callAsync 终结性）；`TestSentinelFlowControlRunner` 反向钉死 #1 与 `SentinelFlowControlRunner.java` 在本批零 diff；cra-index Phase 4 回填时 :272 行 ←注记按此口径改写为「←C3-008/C8-008 同 diff[落地 R4-25]」注记形态。
- **同变更集绑定（C10 随本体，E2）**：C10-001[MA-07 半边]/C10-003[全条]/C10-004[MA-01 半边]/C10-005[C1-003 半边]/C10-006[C1-001 半边] 与各自本体同 diff 落地，禁止先修本体后补测的拆分交付。
- **硬序条款**：本批无跨批硬依赖（无前置，可并行）；批内纪律 = 每成员先红后绿或变异/回退论证（E2），非批次次序约束；批内软序 = C1-001 与 C1-003 同文件协同（对齐条款第 3 条），建议同组实施。
- **跨单元同号防混**：4.2-C3-001 与 4.3-C3-001（nop-network-integration，plan 2505 已收口）、4.4-C6-007、1.6-C8-012、3.2-MA-01 同属 R21 future 族异站点（:596 六例关系行「不互相吸收」）；4.2-C1-001/C1-003 与 2.5-C1-003（plan 2518 已收口）等同号异站零关联——cra-index 回填时逐行带 4.2 前缀核对。

### 4 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `a9fc5b01a7` 2026-11-01 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 4.2-C1-004（P1，MA-01 主；←C4-001/C2-003/C3-004/C2-005/C4-002 六条族；C10-003 全条 + C10-004 半边） | `AbstractLeaderElector.java`（nop-cluster-core `elector`） | `volatile leaderEpoch` :32 / `volatile electionPromise` :33 / `listeners` COW :43；`doStop` :120-124（`electionPromise.cancel(false)` :121 + `onStop()` :122 + `listeners.clear()` :123，**无锁**）；`onStop` :213-225（监听器遍历 :214-220 + `leaderEpoch = null` :221 + promise cancel :222-224，**无锁**）；`newElection` :161-163（**无锁**）；`onRestartElection` :165-171（有锁）；`onElectionCompleted` :173-181（synchronized :173 + `leaderEpoch = epoch` :174 + isDone 重建 :176-178 + `promise.complete(epoch)` :180）；`AbstractPollingLeaderElector.java`（同包）：plan 2496 监督形态全文件 :42-179（pendingCheck :53/:103、isStopping 守卫 :114/:141、**javadoc :38-39 明文 stop/restart 生命周期面归 R2-18**） | 无锁 stop 与 synchronized onElectionCompleted 竞交——stop 后 onElectionCompleted 把 leaderEpoch 复活为非 null、isDone()==true 触发新建 promise 并 complete（等待者收到已停止节点当选通知）+ 监听器先 onStop 后 becomeLeader/becomeFollower 乱序（allowRestart=true :26 + 停止后回 CREATED :107 + 生产子类 checkElection DB I/O 宽窗口）；5 写站点仅 2 有锁（C2-003 面），pending 调度无 stop 撤销手段（2496 后仍无 cancel-on-stop） | 裁定①（C10-003 全条 + C10-004 MA-01 半边随同 diff；**六条族一处闭合**）：stopped 终态守卫形态（独立 volatile stopped 标志[start/doStart 清除、doStop 置位，R1 Major-1 预设] vs generation/promise-identity 守卫——LifeCycleSupport isStopping 不作唯一守卫：allowRestart=true 使 stop 返回后回 CREATED、isStopping()=false 结构性失明）一次定夺；doStop/onStop/newElection 纳入统一锁 + onElectionCompleted 入口停止检查 + complete 取局部引用移出临界区（C2-005，锁纪律与派发纪律一次裁定——禁全站点 synchronized 放大持锁面）；pendingCheck stop 撤销（cancel-on-stop）与 2496 重臂逻辑的协同（rearm 的 `!isStopping()` 守卫 :114/:141 已在位，stop 撤销不与其互斥）；监听器回调停止守护；回归断言面「停机后无 lease 行重写、无尾部 reschedule」（C4 复核点名）+ C10-003 建议 (1)-(4)；模块外 SysDao/Jdbc 子类零行为回退（fencing 单调性钉族保持绿） |
| 2 | 4.2-C1-001（P1，独立，live 主路径「装配即生效」；C10-006 半边） | `ServiceInstance.java`（nop-cluster-core `discovery`）+ `NacosNamingService.java`（nop-cluster-nacos） | 写面 `NacosNamingService` `updateInstances` :235-277（synchronized(this) :238；内容变化分支 `serviceInstance = old.getRight()` :249 → `!old.getLeft().equals(...)` :251 → `setModifyIndex(+1)` :252 → `copyFromInstance(...)` :253 **原地改写共享对象**；不可变 List 发布 :264-265）；`copyFromInstance` static :280 起（逐 setter 全字段改写）；读者面 `ServiceInstance`：`_host` 字段 :153 / `toString` :167-171（副作用调 getHost）/ `getHost` :172-178（`_host==null` 惰性缓存 :174-175，**copyFromInstance 不清 `_host`**） | Nacos 事件线程原地改写共享 ServiceInstance（全字段非 volatile 无 happens-before）——请求线程撕裂读（addr/port/weight/healthy 交叠）+ `_host` 惰性缓存固化陈旧端点（host 匹配与建连基准永久指向迁移前地址，直至 instanceId 变化重建）；12 读者站点失效面枚举（对齐条款第 2 条全列） | 裁定②（C10-006 建议 (1)(2) 随同 diff）：内容变化时**重建 ServiceInstance 以新对象替换 map 成员**（copy-on-write 元素语义，预设——同时消除撕裂读/`_host` 陈旧/modifyIndex 非原子三面）vs volatile 化最小修复（备选——`_host` 失效+全字段 volatile，审计建议 b）；重建时 modifyIndex 语义（旧值+1 随重建对象携带）与 `Pair.getLeft()` instanceText 键协同；公共 API 面（setAttribute/addCleanupTask）零触碰；12 读者站点零消费者变更声明 |
| 3 | 4.2-C1-003（P1，独立，live 面；C10-005 半边） | `NacosNamingService.java`（nop-cluster-nacos） | 编排 `getServiceInfo` :178-199（`subscribe` :188 → `subscribed = true` :189 → `getAllInstances` :191 → `listener.initInstances(instances)` :192）；`initInstances` :230-232（synchronized，无条件 `updateInstances`）；`onEvent` :214-220（事件入口，同一 updateInstances）；失败回滚 :194-197（NacosException→unsubscribe） | 订阅初始化旧快照无版本守护——事件 B 落在 getAllInstances 返回（:191）与 initInstances（:192）执行之间即以陈旧快照 A 无条件覆盖新事件态（「后到者胜」无 epoch/时序比较；复核补强：窗口为本模块代码段自有，不依赖 SDK 乱序；陈旧态存活至下一次无关变更事件——扩容不可见/缩容幽灵调用） | 裁定③（C10-005 C1-003 半边随同 diff；**与裁定②同写入口协同**）：版本守护形态一次定夺——(a) 预设=「仅当 listener 尚未收到过事件时才写入」（收事件态标志位，最低成本）；(b) 备选=init 前重拉 getAllInstances 与事件态 modifyIndex/内容比较；(c) 延迟 init 至订阅首个事件落地（②可省去——依赖 SDK 首推语义，风险自评）；**测试注入缝一次裁定**[R1 Minor-3 更正：无字段注入缝但 override 缝现成——getNamingService() public 非 final、类非 final；**预设 = 测试子类 override getNamingService() 注入 stub（生产零触碰）**，工厂接缝抽取降备选——两案与裁定②共用]；失败回滚 :194-197 语义零回退 |
| 4 | 4.2-C3-001（P1，MA-07 主；←C3-008/C8-008 同 diff[落地 R4-25——勘误 8]；C10-001 半边） | `ClusterRpcClient.java`（nop-rpc-cluster） | `callAsync` :59-94（手工 future :69；`IntConsumer fn` 重试递归 :70-90；`chooseFromCandidates` :73 / `getRpcClient(...).callAsync(...)` :74 均在 whenComplete 回调内同步执行；`future.completeExceptionally(err)` 仅终态分支 :80；重试分支 `instances.remove(instance)` :82-84 + **`this.accept(retryTimes + 1)` :85 无 try/catch**；**whenComplete 返回 stage 被丢弃 :88**；`fn.accept(0)` :91 首跳异常被 CF 机制捕获——唯独重试跳异常丢失）；`call` 同步路径 :96-126（有 try/catch，安全对照面）；live 默认装配 `rpc-cluster-defaults.beans.xml` :85-97（AbstractRpcProxyFactoryBean parent 链→ClusterRpcProxyFactoryBean，全平台 @RpcClient 代理经此链）；NPE 证据点 `HttpRpcClientInstanceProvider.getRpcClientInstance` :33（`instance.getAddr()` :35 首解引用） | 重试回调内同步异常被吞（whenComplete 返回值丢弃）——chooseFromCandidates/getRpcClientInstance/callAsync 同步段任一 RuntimeException 只被丢弃 stage 捕获，返回 future 永不 complete/completeExceptionally，调用方永久挂起（无超时）/海量超时（有超时）+ SentinelFlowControlRunner entry 泄漏复合（exit 挂在永不完成的 future 上，C3-007 机制联动） | 裁定④（C10-001 MA-07 半边随同 diff；**R21 族纪律对齐**）：重试体 try/catch + `future.completeExceptionally(NopException.adapt(e))` 终结（预设）vs whenCompleteAsync+handle 链改写（备选）——一次定夺；chooseFromCandidates 返回 null → 类型化 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE`（模块内既有错误码 :18）面是否同 diff 纳入为 Phase 1 裁定边界（C3-003 本体[MasterServerChooser 双 null 返回点+IServerChooser 契约]归 R4-25 不触碰）；**Sentinel entry 释放 = future 终结的机制联动声明**（`SentinelFlowControlRunner.java` 零触碰）；退避（C8-002）/反向取消（C3-009）不代做，重试循环改写禁顺手引入 |

### PA 判定表（4 成员逐一，orm/_生成物/跨模块公共 API/nop-api-core 触碰与否；live 2026-11-01 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触跨模块公共 API / nop-api-core？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|
| 1 | C1-004 | nop-cluster-core main java | elector 锁纪律+终态守卫 | 否 | 否 | 否（AbstractLeaderElector 模块内抽象基类，无 nop-cluster-api 叶模块[live find 核实]；子类 SysDao/Jdbc 为模块外消费但不构成 api 模块契约面） | 纯 Java 修复；模块外子类行为零回退以补充验证腿保障（勘误 11） |
| 2 | C1-001 | nop-cluster-core + nop-cluster-nacos main java | 写侧重建对象替换元素 | 否 | 否 | 否（ServiceInstance 为 nop-cluster-core 内实体类，`_host`/setter 均模块内契约；12 读者站点全在 nop-cluster 内） | 纯 Java 修复；公共 API 面（setAttribute/addCleanupTask）零触碰 |
| 3 | C1-003 | nop-cluster-nacos main java | 订阅初始化版本守护 | 否 | 否 | 否（NacosNamingService 模块内实现类；nacos SDK 为外部依赖非平台 api 面） | 纯 Java 修复；测试注入缝为模块内 seam 抽取不扩公共契约 |
| 4 | C3-001 | nop-rpc-cluster main java | 重试体终结性 | 否 | 否 | 否（ClusterRpcClient 实现 `io.nop.api.core.rpc.IRpcService`——**纯消费** nop-api-core 接口零触碰；错误码用模块内 ClusterErrors 既有常量） | 纯 Java 修复；条件申报：若裁定确需 nop-api-core 契约演进（预设不需要）→ 触发 plan-first 申报 |

**结论：头部零 orm 触碰、零 `_` 生成物触碰、零 nop-api-core 触碰声明成立**（4/4 = `src/main/java` 纯代码修复；站点分布于 nop-cluster-core/nop-cluster-nacos/nop-rpc-cluster 三模块，无 ORM 模型与生成物；nop-cluster 无 `-api` 叶模块，「跨模块公共 API（nop-*-api）」保护类目无载体——live find 核实；`_vfs` 下 beans.xml 为手工维护源[git log 亲读]，本批预设零触碰）。批次 PA=无（batch-proposal R2-18 行显式），无条件申报触发形态。

### Baseline 勘误（输入不一致登记）

1. **AbstractPollingLeaderElector 全面改写勘误（审计基线 120ff53d0d → live a9fc5b01a7）**：C1/C2/C10 引用的 `:16-31`（schedule fire-and-forget 丢弃 future）已不存在——plan 2496（提交 `8c1b1505aa`）已落 C4-004/CL-011/CL-014 监督形态（live 全文件 :42-179：pendingCheck 保留 :53/:103、业务标识告警 :111-118、runGuardedElectionRound finally 重臂 :131-147、isStopping 守卫 :114/:141），**javadoc :38-39 明文「the C4-002/C1-004 stop/restart lifecycle face is owned by R2-18 and intentionally not touched here (no cancel-on-stop is added)」**——本批 C1-004 承接该预留半边；C4-001 的「pending 保留」半边与 C10-004 建议 (1) 已由 2496 收敛（`TestPollingElectorLoopInsulation` 3 钉在案为零回退钉），C2-003 的 scheduleCheck 守护空转面被 2496 `executor.isDestroyed()` 检查 :91 部分收敛，本批 C2-003 剩余面 = 5 写站点锁统一 + stop 撤销 + 守卫与终态协同。缺陷机制（复活/乱序）零变化：doStop/onStop 无锁面 live 亲读在位。
2. **ClusterRpcClient 行号微漂移**：审计引 C3-001 证据段 :69-93 / 重试分支 :83-85——live callAsync :59-94（@Override :59；重试分支 else 体 :81-86，`this.accept(retryTimes + 1)` **:85**；whenComplete 返回值丢弃点 :88）；C10-001 引 callAsync:60-94 ✓ / call:96-126 ✓ / getRpcClient:137-152 → live :137-154（plan 2498 C4-003 deadline 注记 :147-150 使尾部 +2 行）；缺陷机制零漂移（重试体无 try/catch + 返回 stage 丢弃两要素亲读在位）。
3. **C1-001 失效面 12 读者站点行号勘误（live 逐一回码）**：SpecificServiceInstanceFilter getHost 使用点 **:28**（复核已勘，初审原文 :69）；HealthyServiceInstanceFilter removeIf **:27**（复核已勘，原文 :38）；ZoneServiceInstanceFilter **:40**（原文 :41-43）；TagServiceInstanceFilter **:42-45**（原文 :45，:42 getTags(request) 局部）；RouteServiceInstanceFilter **:61**（原文 :56-64 范围内）；HttpRpcClientInstanceProvider **:33-35**（原文 :31-34）；MasterServerChooser **:45**（原文 :41，+4 漂移）；LeastActiveLoadBalance **:59-91**（原文 :71-91，:59 totalWeight 声明、:71 getWeight 活读在范围内）；ServiceLoadBalanceAdapter **:16-21**（原文 :17-23，~1 行漂移）；WeightedRandomLoadBalance :26-28 ✓、PartitionResolver :101 ✓ 零漂移。全部为行号漂移，站点与缺陷机制零漂移。
4. **C1-004/C1-003 主站点行号零漂移**：AbstractLeaderElector :32/:33/:43/:120-124/:161-163/:165-171/:173-181/:213-225 与 NacosNamingService :174-176/:186-192（subscribe :188/getAllInstances :191/initInstances :192）/:214-220/:230-232/:235-277（:238/:249-254/:263-265/:268-276）逐项亲读吻合；ServiceInstance :151/:153/:167-171/:172-178/:289-300 零漂移（getHost 签名 :173、写点 :175）。
5. **LifeCycleSupport 行号微漂移**：审计引 allowRestart「:26/:107-108」——live `allowRestart = true` **:26** ✓、stop 终态 `isAllowRestart() ? CREATED : STOPPED` **:107**（:108 为收尾行）、`isStopping()` :114；零实质漂移。
6. **C10 半边口径**：C10-001 本批仅承载 MA-07 半边（建议 (1) 钉 C3-001）——建议 (2)(3)(4)（无主类型化/退避时间戳/反向取消）随 C3-003[MA-09→R4-25]/C8-002/C3-009 各本体；C10-003 **全条入批**（四条建议保护对象 = C1-004+C4-001+C2-003+C3-004+C2-005 全在 MA-01 集合）；C10-004 本批仅承载 MA-01 半边（建议 (2) destroy 后停止投递断言）——C4-004 半边已由 plan 2496 收敛（钉在案零回退）；C10-005 本批仅承载 C10 复核 R1 并入的 C1-003 断言面（升 P2 处置，:446）——其余保护对象（C1-005/C3-005/C8-005/C2-002/C3-011）R4-25/各本体；C10-006 本批仅承载建议 (1)(2)（_host 失效 + 旧引用读者视图）——建议 (3)(4)（cleanupTasks 丢任务/慢任务阻塞）随 MA-02 条化本体。Phase 4 收敛注记按此书写。
7. **测试资产盘点勘误**：审计口径「13 个测试文件」（C10 基线）与复核更正「17 个」→ live **21 个 @Test 文件**——plan 2496 新增 `TestPollingElectorLoopInsulation`/`TestAutoRegistrationInsulation`、plan 2498 新增 `TestClusterRpcPollOffload_C4_003`（审计后落地）；「全模块零真并发测试」结构性结论对 2496 前状态成立，live 已有 3 个并发形态测试（均为 2496/2498 修复钉，非本批成员缺陷面的保护），P1×4 无保护结论经 live 复核维持（TestAbstractLeaderElector 仍全单线程 6 用例/TestClusterRpc 仍仅同步路径）。基线亲跑 80 tests 0F0E EXIT=0（`_tmp/baseline-2521-nop-cluster.log`）。
8. **C3-008/C8-008 吸收成员台账口径勘误（三登记位 vs 裁定文 vs 批次行）**：cra-index :272 行内注记「←C3-008/C8-008 同 diff」/family-clustering R21 行 :65「4.2-MA-07(←C3-008/C8-008 同 diff)」/summary.md :61「C3-001（MA-07 主，成员 C3-008/C8-008）」三处将该对登记为 MA-07 吸收成员；而 merge-adjudication §二裁定 C3-008 扩入 **MA-05**（:181）、C8-008 并入 C3-008（:198，复核登记）——两登记体系并存。**本批边界以 batch-proposal R2-18 依赖列（「吸收成员 C3-008/C8-008 同 diff 在 R4-25 落地」）+ R4-25 行 :140（4.2-MA-05 载「MA-05 反向钉死 #1 同 diff 改写」）为准**：C3-008/C8-008 的修复与反向钉死改写在 R4-25 落地，本批只落 C3-001 主半边；Phase 4 cra-index 回填时 :272 行 ←注记改写为「←C3-008/C8-008 同 diff[落地 R4-25]」消除歧义。成员集合本身不变更（本批 finding 清单 4 项照录）。
9. **依赖列 R21 token 口径**：batch-proposal R2-18 依赖列「MA-07 R21」中 R21 = family-clustering 族行编号（R21 = future-永不完成族 chain1，:65——4.2-MA-07 列该族 P1 成员；:596 六例关系行裁定「修复方案对齐 completeExceptionally 纪律，不互相吸收」；R2-02/R2-12 行内「completeExceptionally 纪律（R21）」同源），**非批次号**——本批 C3-001 修复模式受 R21 族纪律约束（失败路径必须 completeExceptionally），无批次依赖语义。
10. **E3 上游链与环境三联预登记（批前既有，沿 plan 2512–2520 已知面）**：`./mvnw test -pl nop-cluster -am` 按pom 亲读上游闭包 = nop-kernel（nop-commons：LifeCycleSupport/executor 设施）/nop-api-core/nop-core/nop-config/nop-rpc（nop-rpc-core/nop-rpc-http）及传递依赖——已知四面 flake 中**仅 nop-core `TestResourceCacheInvalidationChain` 负载 flake 在闭包内**（2517/2518/2519/2520 执行期留痕先例），debezium docker fork timeout/stream-runtime E2E 收敛/auth MFA TOTP 窗口三面不在 nop-cluster 上游闭包（预登记形态沿 plan 2520 勘误 8）；上游环境抖动按「stash 基线同红 / solo 绿 / 恢复复跑全绿」**三联裁定**处理并留痕，不得静默重跑或跳过。
11. **模块外 elector 子类补充验证腿（E3 通道盲区预登记）**：E3 命令仅覆盖 nop-cluster 聚合器内模块——elector 生产子类测试在模块外（`nop-sys/nop-sys-dao` TestDaoLeaderElector/TestJobCoordinatorWithSysDaoLeaderElector；`nop-stream/nop-stream-runtime` TestJdbcLeaderElector 族/TestJobCoordinator* 族/TestLeaderElector），C1-004 改基类锁纪律/终态守卫属跨模块行为面——Phase 3 须加两模块 elector 族焦点 solo 腿（`-Dtest` 限定，规避 stream-runtime E2E flake 面[2520 r2 先例签名]）并登记；「停机后无 lease 行重写」断言本体属子类模块批次义务，本批以基类测试承载（C10-003 建议 (1) 形态），模块外断言登记 Non-Blocking Follow-ups。
12. **owner doc 缺口登记（E5 通道选择）**：nop-cluster 无 `docs-for-ai/03-modules/` owner doc 且 module-groups.md 零提及（live grep 证实）——batch-proposal §一 E5 二选一通道（补 03-modules 条目 或 module-groups.md 登记 drift）中**本批取「缺口声明 + module-groups.md 通行句 + source-anchors.md CLUSTER-CC-001 锚点」通道**；整批 owner doc 建设登记 R-G 治理批去向（Non-Blocking Follow-ups），不在本批新建 03-modules 文件。
13. **[设计文档起草增补条款（预留）]** 裁定①触碰 `AbstractLeaderElector` 与 plan 2496 已落监督代码共居一文件，设计文档须给出与 `TestPollingElectorLoopInsulation` 3 钉及 `TestAbstractLeaderElector` 6 钉的兼容性核对表（锁纪律改动不得改变 2496 监督行为的可观测形态）；裁定②载体重写须核对 `TestNacosNamingServiceCopyFromInstance` 2 钉（copyFromInstance static 签名与 serviceName `@@` 拆分语义保持）；既有钉零回退。

## Goals

- Phase 1 设计裁定文档一次裁决 4+1 项裁定（裁定①–⑤，含 MA-01 六条族吸收面与三组同变更集/范式对齐绑定）+ 测试缝（独立 draft review 通过），无临场发明空间：①doStop 复活锁序+终态守卫（C1-004 六条族一处闭合，含 C2-005×C2-003 互为前置的锁纪律/派发纪律一次定夺）②ServiceInstance 重建对象不可变化（C1-001）③订阅快照版本守护+测试注入缝（C1-003，与②同写入口协同）④callAsync 重试体终结性（C3-001，R21 族纪律）⑤测试缝与零回退钉清单——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。设计文档拟名 `ai-dev/design/concurrency/cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md`（Phase 1 产出物，前向引用——落盘前 doc-link warning 为预期已登记；命名裁定：选主生命周期（elector 域）+ 服务实例不可变与订阅快照（发现域）+ RPC 重试终结（调用域）三域合一，与现有 24 份 concurrency 设计集命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 4 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-001 MA-07 半边/C10-003 全条/C10-004 MA-01 半边/C10-005 C1-003 半边/C10-006 C1-001 半边，五条成员面）随本体同 diff 落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红④逐成员登记：红① 复活红（并发测试：stub 子类 checkElection 内 latch 挂起模拟 DB I/O + 另线程 stop() + 释放后断言 `getLeaderEpoch()==null` 持续 + `whenElectionCompleted()` 不返回新完成 + 监听器无 becomeLeader——缺陷态 onElectionCompleted 停止后复活/新 promise complete 红→终态守卫+锁统一绿；监听器序断言 onStop 后无角色回调同 diff）；红② 撕裂/陈旧红（契约测试：同一实例对象经历两次内容变更事件后断言旧引用读者视图不变 + `getHost()` 返回新端点——缺陷态原地改写+`_host` 固化红→重建对象绿；并发读者多 trial 撕裂面探针/变异论证）；红③ 快照回退红（注入缝测试：subscribe 后 getAllInstances 返回前注入事件 B、initInstances(A) 断言不覆盖 B 态——缺陷态后到者胜回退红→版本守护绿；注入缝 = 裁定③一次定夺）；红④ 挂死红（stub 首跳可重试异常[NopConnectException] + 重试体内 chooseFromCandidates/getRpcClient 同步抛 RuntimeException + retryCount≥1 + 多实例 → 断言返回 future 有限时间（如 get(5s)）completeExceptionally——缺陷态 future 永不完成 get 超时红→重试体终结绿；同步 call 路径既有行为零回退钉）。
- 联调纪律显式守约（本批八条）：**MA-01 六条族一处闭合**；**C1-001×12 读者站点零消费者变更**；**C1-003×C1-001 同写入口协同**；**C3-001×R21 族纪律对齐（completeExceptionally，禁顺手退避/取消联动）**；**R4-25 边界条款（C3-008/C8-008 同 diff 改写归 R4-25，SentinelFlowControlRunner 零触碰）**；**C10 五条同 diff（E2，半边口径按勘误 6）**；**PA 零触碰（无 api 叶模块/零 orm/零生成物/零 nop-api-core）**；**无跨批硬依赖（可并行）**。
- Owner doc 随批更新：缺口声明 + `docs-for-ai/01-repo-map/module-groups.md`（nop-cluster 六子模块通行句：模块族定位与并发契约指针）+ `docs-for-ai/04-reference/source-anchors.md`（新增 `CLUSTER-CC-001` 锚点行，按实际行号插入）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-25 束成员**（G65 九条：MA-03/MA-05/MA-08/MA-09/C1-005/C2-002/C8-001/C8-006/C8-007，cra-index :449）——特别地 **MA-05（Sentinel runAsync 面：C3-007 主 + C3-008/C8-008 吸收成员）不代做**：`SentinelFlowControlRunner.java` 零触碰、反向钉死 #1（`TestSentinelFlowControlRunner.testSupplierSyncThrowPropagatesSynchronously` :100-114）零触碰（其「rejected future+异常统计」同 diff 改写义务在 R4-25）；MA-08 反向钉死 #3 同理零触碰。本批 C3-001 与 MA-05 的关联仅为机制联动声明（future 终结→entry exit），非代码共改。
- **不做 MA-07 的 P2/P3 成员**（C8-002 零退避零抖动/C3-009 反向取消联动——批次归属按 P2 台账；重试循环改写时禁顺手引入退避调度或 cancelToken 反向挂接）。
- **不做条化/休眠面**：MA-02（cleanupTasks 治理族，零生产调用方条化——`addCleanupTask`/`getCleanupTasks` 零触碰）、MA-04（BroadcastRpcClient 族，零接线条化）、C1-005（destroy 懒重建，R4-25）、C1-008/C1-009/C1-010、watch C2-004。
- 不做平台层改码：nop-api-core/nop-commons/nop-core 平台设施零触碰（只消费惯例——`LifeCycleSupport.isStopping` 消费面属既有公共设施语义）；`IServerChooser`/`ILeaderElector`/`IRpcService` 契约签名零变更；不做整批 owner doc 建设（R-G 治理批去向，勘误 12）。
- 不触碰 `_` 前缀生成物（含 `src/main/resources/_vfs` 下 beans.xml 预设零改动）；G65/条化/watch 批外成员本体；模块外 elector 子类的「停机后无 lease 行重写」断言本体（Non-Blocking Follow-ups 登记去向）；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/`：`elector/`（AbstractLeaderElector——锁纪律/终态守卫/pendingCheck 撤销/监听器守护；AbstractPollingLeaderElector 仅当裁定①需要 stop 撤销挂点时最小触碰，2496 监督面零回退）、`discovery/ServiceInstance.java`（读者面零变更，仅当裁定②选 volatile 备选时触达——预设零 diff）。
- `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java`（updateInstances 重建对象+版本守护、initInstances 编排、测试注入缝）。
- `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java`（callAsync 重试体终结性；`call`/`getRpcClient` 零触碰[预设]）。
- 测试树：core `elector/`（复活/监听器序/promise 等待者新钉）、nacos（快照回退/_host 契约/旧引用视图新钉 + 注入缝桩）、rpc-cluster（callAsync 终结性新钉 + stub chooser/provider）；既有 80 tests 零回退。
- 设计文档：`ai-dev/design/concurrency/cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md`（新建；裁定①–⑤+测试缝+零回退钉+R21/MA-01 对齐条款，最终状态写法；命名调整须登记裁定）。Owner doc：`docs-for-ai/01-repo-map/module-groups.md`（通行句+缺口声明）、`docs-for-ai/04-reference/source-anchors.md`（CLUSTER-CC-001 行）。

### Out Of Scope

- Non-Goals 所列全部项；R4-25（G65）/条化（MA-02/MA-04）/watch 批次工单；nop-api-core/nop-commons/nop-core 平台设施本体；`model/*.orm.xml` 与 `_` 前缀生成物（预设零触碰）；`IServerChooser`/`ILeaderElector`/`IRpcService` 契约演进；模块外 elector 子类代码（SysDao/JdbcLeaderElector 本体）与 stream-runtime/nop-sys 测试树本体。

## Execution Plan

### Phase 1 - elector 生命周期/发现快照/RPC 终结并发设计裁定文档 + 独立评审

Status: planned
Targets: `ai-dev/design/concurrency/cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [ ] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [ ] **裁定① C1-004 doStop 复活锁序（六条族一处闭合）**：stopped 终态守卫形态（**独立 volatile stopped 标志[预设，R1 Major-1]**——LifeCycleSupport 无 isStopped() 且 allowRestart=true 使 stop 返回后 status 回 CREATED、isStopping()=false 结构性失明；守卫消费点显式覆盖「stop 返回后窗口」三场景 = mid-round stop / post-stop rearm[2496 finally 重臂在 CREATED 态同样失明] / post-stop onElectionCompleted；CancellationException 告警豁免 :112 已在位不重开；备选 = generation/promise-identity 守卫）vs 一次定夺；5 写站点锁统一方案（全部 synchronized[this] vs 私有锁对象）与 onElectionCompleted 入口停止检查；C2-005 派发纪律（complete 取局部引用移出临界区）与 C2-003 锁纪律**一次联动定夺**（禁全站点 synchronized 放大持锁面——C2-round2 警告成文）；pendingCheck stop 撤销挂点与 2496 重臂逻辑协同（`!isStopping()` 守卫 :114/:141 语义保持）；监听器回调停止守护（onStop 后 becomeLeader/becomeFollower/onException 禁达）；回归断言面「停机后无 lease 行重写、无尾部 reschedule」+ C10-003 建议 (1)-(4) 测试形态；模块外 SysDao/Jdbc 子类零行为回退声明；与 `TestPollingElectorLoopInsulation` 3 钉/`TestAbstractLeaderElector` 6 钉兼容性核对表（勘误 13）
  - [ ] **裁定② C1-001 重建对象不可变化**：重建 ServiceInstance 替换 map 成员（预设） vs volatile 化最小修复（`_host` 失效+全字段 volatile）——一次定夺；modifyIndex 随重建对象携带语义；`Pair<String,ServiceInstance>` instanceText 键与重建协同；12 读者站点零消费者变更声明 + C10-006 建议 (1)(2) 测试形态；公共 API 面（setAttribute/addCleanupTask）零触碰声明
  - [ ] **裁定③ C1-003 订阅快照版本守护**：守护形态一次定夺——(a) 预设=事件态标志（listener 收过事件后 init 不写入）；(b) 备选=init 前重拉比较；(c) 备选=延迟 init 至首事件（依赖 SDK 首推语义风险自评）；**测试注入缝一次定夺**（stub NamingService 注入 vs getNamingService 工厂接缝抽取——与裁定②共用，缝形态为模块内 seam 不扩公共契约）；与裁定②同写入口（updateInstances）协同落地声明；失败回滚 :194-197 零回退
  - [ ] **裁定④ C3-001 重试体终结性（R21 族纪律）**：try/catch + `completeExceptionally(NopException.adapt(e))`（预设） vs whenCompleteAsync+handle 链——一次定夺；chooseFromCandidates null → `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 类型化面是否同 diff 纳入（C3-003 本体 R4-25 不触碰——边界成文）；Sentinel entry 释放机制联动声明（`SentinelFlowControlRunner.java` 零触碰）；C8-002/C3-009 不代做声明（禁顺手退避/反向取消）；条件申报（nop-api-core 契约演进）触发检查条款[预设不触发]
  - [ ] **裁定⑤ 测试缝与零回退钉清单**：红①–红④ 测试形态逐条（含缺陷态红判据 + 全部 plain-JVM 可跑面声明[无 docker/nacos SDK 门控]）；零回退钉 = nop-cluster 基线 80 tests 全量（`_tmp/baseline-2521-nop-cluster.log`）、其中点名：`TestAbstractLeaderElector` 6 钉、`TestPollingElectorLoopInsulation` 3 钉（2496 监督面）、`TestAutoRegistration`+`TestAutoRegistrationInsulation` 6 钉、`TestCachingNamingService` 4 钉、`TestPartitionResolver` 10 钉、`TestWeightedPartitionAssigner` 14 钉（含反向钉死 #3 :251-270 零触碰）、`TestSentinelFlowControlRunner` 6 钉（含反向钉死 #1 :100-114 零触碰）+`TestSentinelFlowControlRunnerContext` 2 钉、`TestNacosNamingServiceCopyFromInstance` 2 钉、`TestClusterRpc` + `TestClusterRpcPollOffload_C4_003` 4 钉、LB/filters 族；模块外焦点腿 = nop-sys-dao elector 族 + stream-runtime elector 族（`-Dtest` 限定清单成文）；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑤全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [ ] 裁定①含六条族一处闭合与 2496 监督钉兼容核对表 + C2-005×C2-003 联动定夺；裁定②③含同写入口协同与注入缝定夺；裁定④含 R21 纪律对齐与 R4-25 边界成文；裁定⑤含反向钉死两处零触碰声明与模块外焦点腿清单
- [ ] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 4 成员实现 + 回归测试（按子域分组）

Status: planned
Targets: `nop-cluster/nop-cluster-{core,nacos}/src/`、`nop-cluster/nop-rpc-cluster/src/`（各自 main + test 树）

- Item Types: `Fix`（4 项 P1 均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [ ] 组 A elector（nop-cluster-core elector）：C1-004（裁定①，六条族一处闭合 + C10-003 全条四钉[R1 Major-2 逐条枚举：建议(1) 并发复活钉=红①主判据 latch 挂起×stop；建议(2) stop→start 循环×2 双回路钉[checkElection 计数增速不翻倍]；建议(3) 等待者终态完成钉[whenElectionCompleted 挂接后 restartElection，断言等待者在 P2 上完成而非 CancellationException]；建议(4) complete 移出临界区**结构性钉**[慢 continuation latch 阻塞持锁期间另线程有界等待进入 synchronized 方法——缺陷态锁被持有为结构性红，**时延阈值形态按 R1 Major-2 裁定改结构性形态不降 watch**]] + C10-004 MA-01 半边钉[stop 后停止投递/监听器序]；2496 监督钉零回退）
- [ ] 组 B 发现链（nop-cluster-nacos + core discovery 协同）：C1-001（裁定②，C10-006 半边红②两钉[旧引用视图+_host 契约]）+ C1-003（裁定③，C10-005 半边红③快照回退钉——与 C1-001 同 updateInstances 写入口同 diff 协同落地）
- [ ] 组 C 调用链（nop-rpc-cluster）：C3-001（裁定④，C10-001 MA-07 半边红④终结性钉；条件申报触发检查留痕：预设未触发——`ClusterRpcClient` 纯消费 `IRpcService`，git diff 核对 nop-api-core 零触碰）
- [ ] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红④快照落 `_tmp/` 并登记（`_tmp/red-green-2521-summary.md` 总册 + 逐 trial 日志）；**变异探针纪律**：凡以「注释/删除目标行 → 跑钉子 → 恢复」验证回退必红者，恢复动作必须以断言方式确认目标可执行行真实存在（grep/读取校验），防止恢复脚本半写入造成假绿（R5 教训成文义务）；并发钉设 trial 上界防噪声化（红①/红②并发面 trial 上界在裁定⑤成文）
- [ ] **端到端验证**（rule 22）：C1-004 生命周期链 = 复活钉（stop→在途裁决完成→断言终态不复活→监听器序全序断言，从 doStop 入口到监听器回调出口的完整路径）；C3-001 调用链 = 终结性钉（首跳失败→重试体同步抛→future completeExceptionally 全链，Mock/stub 从 callAsync 入口到 future 终结出口）；C1-001/C1-003 为写入口单点修复，由红②/红③钉承载（单点修复无链式入口，免端到端义务显式声明）
- [ ] **接线验证**（rule 23）：裁定①终态守卫在 onElectionCompleted 真实消费断言（stop 后复活路径必被拦截，非仅标志存在）；裁定④ completeExceptionally 真实到达返回 future 断言（stub 异常→future 终结态断言，非仅 catch 存在）；裁定②重建对象真实替换 map 成员断言（旧引用与新引用非同一对象 + 读者视图各自一致）
- [ ] **无静默跳过**（rule 24）：裁定④重试体异常必终结 future（禁 catch 后仅记日志不终结）；裁定①停止后回调路径显式拒绝/短路（禁静默 no-op 监听器吞并）；新增公共分支无空方法体/TODO

Exit Criteria:

- [ ] 4 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（5 条成员面，按勘误 6 半边口径）同 diff 落地
- [ ] 必红证据（E2）：红①–红④均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：基线 80 tests + 既有钉零回退（钉零回改逐条 git diff 为空核对；反向钉死 #1/#3 零触碰核对）；`TestPollingElectorLoopInsulation` 3 钉零回退（2496 监督面）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选（C1-001/C1-003 单点免端到端义务声明在案）
- [ ] `./mvnw test -pl nop-cluster -am` 批行验证命令执行留痕（模块/测试数/退出码/快照路径）；上游环境抖动按三联裁定留痕（勘误 10）；实际触达模块以 git diff 为准逐条登记（预期 core/nacos/rpc-cluster 三模块 main+test，无其他）；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`、不含 nop-api-core/nop-commons/nop-core 平台设施、不含 `SentinelFlowControlRunner.java` 与 `TestSentinelFlowControlRunner`、不含 R4-25/条化/watch 批外成员本体；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向；执行中断时本 Phase 收束前回补本清单勾选，禁跨 Phase 欠账[R1 Minor-5]）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-cluster 聚合全量测试 + 模块外 elector 焦点腿；`docs-for-ai/01-repo-map/module-groups.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（如全聚合测试通道被零 diff 上游已知 flake 阻断，授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，沿 plan 2497–2520 Phase 3 先例声明支路理由与逐面三联裁定留痕）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-cluster -am` BUILD SUCCESS 留痕（模块/测试数/退出码/快照路径；上游抖动三联裁定）；**模块外 elector 焦点腿（勘误 11）**：`nop-sys/nop-sys-dao` elector 族 + `nop-stream/nop-stream-runtime` elector 族 `-Dtest` 限定 solo BUILD SUCCESS 留证；实际触达模块以 git diff 为准逐条登记；零触碰确认（生成物/orm 模型/平台设施/SentinelFlowControlRunner 面/R4-25 批外成员本体/既有钉面）
- [ ] Owner doc 更新：`module-groups.md`——nop-cluster 模块族通行句（六子模块定位一句 + 并发契约指针一句）+ 缺口声明句（无 03-modules owner doc，整批建设归 R-G 治理批）；`source-anchors.md` 新增 `CLUSTER-CC-001` 锚点行（四文件行号锚 + 契约 + 回归钉族 + 设计权威指针，修复后 live 亲读核实；按实际行号插入现有锚点序）；check-doc-links --strict 退出码 0
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-cluster --severity high` 逐条登记（触达面 high=0 或批前既有已裁定——沿 2518/2519/2520 先例口径，注意 shell 管道退出码误读教训[2520 Phase 3 口径更正先例]，以工具真实退出码为准）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 + 模块外焦点腿 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] module-groups.md 通行句 + 缺口声明 + source-anchors.md CLUSTER-CC-001 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记
- [ ] 零触碰确认登记（生成物/orm 模型/平台设施/SentinelFlowControlRunner 面/R4-25 批外成员本体/既有钉面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.27 行、cra-index P1 台账 :269–272 + C10 成员面注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（终态守卫真实拦截/重建对象真实替换/版本守护真实生效/重试体真实终结运行时行为、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-18 四行状态收敛（open→fixed，注记 plan 2521 + closure audit 结论；吸收成员[←C4-001/C2-003/C3-004/C2-005/C4-002 随 :269 行；:272 行 ←C3-008/C8-008 注记按勘误 8 口径改写「落地 R4-25」]）+ C10 成员面收敛注记（5 条按勘误 6 半边口径；C10-004 C4-004 半边 2496 已收敛注记）；roadmap 8.27 行 Status → done；R4-25 行软依赖解除核对（软依赖不阻塞，仅注记）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 复核登记；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 四行收敛 + 吸收成员括注 + C10 半边注记、roadmap 8.27 = done、R4-25 软依赖注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 4 项成员全部修复：C1-004（doStop 复活锁序+终态守卫，六条族一处闭合）、C1-001（ServiceInstance 重建对象不可变化，12 读者站点）、C1-003（订阅快照版本守护）、C3-001（callAsync 重试体终结性，R21 族纪律）
- [ ] batch-proposal R2-18 行 exit criteria 增补逐条达成并留证：doStop 复活锁序修复（leaderEpoch+监听器序）；ServiceInstance 不可变化（12 读者站点）；订阅快照版本守护；callAsync 回调同步异常 completeExceptionally+Sentinel entry 释放
- [ ] 对齐纪律守约：MA-01 六条族一处闭合兑现；C1-003×C1-001 同写入口协同兑现；R21 族纪律对齐兑现（禁顺手退避/反向取消）；12 读者站点零消费者变更兑现；C10 五条同 diff 兑现；R4-25 边界条款兑现（SentinelFlowControlRunner + 反向钉死 #1 零 diff）；PA 条款闭环（零 orm/零生成物/零 nop-api-core 触碰声明兑现——git diff 核对）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红④均有先红后绿或变异/回退论证（E2）；C10 收口门（5 条成员面）同 diff 落地
- [ ] 设计文档裁定①–⑤成文且经独立 draft review；既有钉（基线 80 tests + 2496/2498 钉族 + 反向钉死两处零触碰）零回退实证
- [ ] 全量构建绿：E1（含授权支路声明[如有]）+ 批行验证命令 BUILD SUCCESS + 模块外 elector 焦点腿 BUILD SUCCESS（E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：module-groups.md 通行句+缺口声明 + source-anchors.md CLUSTER-CC-001（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0 或批前既有已裁定（逐条登记，真实退出码）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）终态守卫/重建对象/版本守护/重试体终结运行时真实生效（不只类型系统——守卫真拦截/新对象真替换/快照真不回退/future 真终结，红绿日志证行为改变），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-cluster 聚合）；checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index R2-18 四行收敛 + roadmap 8.27 → done（E6）

## Deferred But Adjudicated

### R4-25 束成员（G65 九条，含 C3-008/C8-008 吸收成员承接与反向钉死 #1/#3 改写义务）

- Classification: `out-of-scope improvement`（batch-proposal §二 R4-25 行 :140 + cra-index :449 批前裁定批外面，非本批降级）；Successor Required: `yes`；Successor Path: `ai-dev/plans/` R4-25 批 plan（roadmap 8.65=todo，软依赖 R2-18——本批落地后以其后的 live 形态为基线）
- Why Not Blocking Closure: 判级对齐零重复——cra-index G65 行批次列=R4-25 且 C10-005/007/008/009/010 成员面验收前置明文；本批已收敛 4.2 单元全部 open P1（4 张）；C3-001 行内 ←注记的落地归属按勘误 8 口径改写，消解双登记体系歧义。

### MA-02/MA-04 条化族、MA-07 P2/P3 成员（C8-002/C3-009）、P3 独立工单与 watch（C2-004）

- Classification: `watch-only residual`（批前裁定：条化零接线/零调用方回升条款/watch 级/族成员批次归属按 P2 台账）；Successor Required: `yes`（触发条款/批次各本体）；Successor Path: 条化激活批/P2 台账批次/各本体
- Why Not Blocking Closure: 批前裁定批外面；本批对 C8-002/C3-009 已登记「禁顺手代做」接缝（对齐纪律第 4 条），对 MA-02 面（addCleanupTask/getCleanupTasks）零触碰声明在案，实施时消费不重建。

## Non-Blocking Follow-ups

- **R-G owner doc 治理批对接注记**：nop-cluster 三/六子模块 owner doc 整批建设（batch-proposal §一 E5 通道二的批级归属）——本批只落 module-groups.md 通行句+缺口声明；如实现中发现契约句超出通行句容量，在本 plan 登记后移交 R-G。
- **模块外 elector 子类停机断言补强**：C4 复核点名的「停机后无 lease 行重写、无尾部 reschedule」断言本体属 SysDao/Jdbc 子类模块（nop-sys-dao/stream-runtime）——本批以基类复活钉承载语义，子类断言登记给两模块对应批次（焦点腿保运行）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent，fresh session）
- Audit Session: （如用子 agent，记录 session ID）
- Evidence: （每条 Exit Criterion 与 Closure Gate 的验证结果；`check-plan-checklist.mjs --strict` 退出码；Anti-Hollow 检查结果；Deferred 项分类检查）

Follow-up:

- （收口时登记 non-blocking follow-up 或明确写 no remaining plan-owned work）

## Risks And Rollback

- **裁定①锁纪律迁移的行为面风险**：doStop/onStop 纳入锁域 + 终态守卫改变「stop 与在途裁决自由竞交」现状——生产子类（SysDao/JdbcLeaderElector 的 DB 抢主/checkElection）在 stop 期间的行为从「可能复活」变为「确定性拒绝」，监控/告警面可能首次暴露停止期裁决拒绝路径（预期改进，但属可观测行为变化）；与 2496 重臂逻辑（`!isStopping()` 守卫）的协同须防「stop 撤销 pending 后重臂误判断链告警」——裁定①成文协同语义；回滚 = 终态守卫行 revert 单点可逆，模块外焦点腿防子类行为漂移。
- **裁定②重建对象的分配面风险**：每次内容变更事件重建全部变化实例对象（写少读多，事件频率=拓扑变更量级）——热点与 GC 压力可忽略但须在设计中声明量级论证；`Pair.getLeft()` instanceText 键不变语义（重建只换 Right 元素）；风险 = 隐式依赖「实例对象同一性」的未知消费方（全仓 grep 核对义务入裁定②）——`ServiceLoadBalanceAdapter`/filter 链按值读无同一性依赖（审计失效面枚举佐证），如发现同一性依赖消费方则升级条件申报。
- **裁定③注入缝的接口面风险**：stub NamingService 或工厂接缝抽取触碰 `getNamingService()` 装配路径——接缝为模块内 protected/包私有形态不扩公共契约（PA 零触碰维持）；风险 = 缝抽取改变懒初始化时序（与 C1-005 destroy 复活面[R4-25]的交错）——裁定③限定缝仅服务测试注入（生产路径零行为变化），或采用子类 override 形态零生产触碰。
- **红④挂死红的可复现性风险**：C3-001 缺陷态为确定性挂死（非竞态红——重试体同步抛路径确定性触发），红证稳定；风险在测试自身超时设计（缺陷态 get(timeout) 必超时红——钉的失败模式是超时而非断言失败，须给足但有限的 timeout 并在钉内注释失败模式语义）。
- **上游链环境噪声风险**：`./mvnw test -pl nop-cluster -am` 触达上游闭包（nop-core/nop-commons/nop-rpc 等），nop-core `TestResourceCacheInvalidationChain` 负载 flake 有 2517–2520 四批先例（勘误 10）——三联裁定执行并留痕；模块外 stream-runtime 焦点腿自带 E2E flake 面（2520 r2 先例签名），`-Dtest` 限定规避。
