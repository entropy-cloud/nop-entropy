# C10 测试保护力 — nop-cluster（WI 4.2）

- **审计人**: CRA WI 4.2 C10 测试保护力审计员（最后批次）
- **日期**: 2026-10-20
- **输入**: C1/C2/C3/C4/C8 五文件「## 最终保留项」+「## 维度复核结论」全量 43 行；`concurrency-resource-audit-prompt.md` C10 正文（L329–347）与附录 A（L351–377）
- **方法**: 全量测试盘点（nop-cluster 13 个测试文件逐文件通读 + 模块外 elector 生产子类测试族核查）→ 对 43 条保留发现逐条变异推演（补上正确实现后现有哪个测试会红）→ 反向钉死检查 → 缺保护登记

## 审计范围与口径

### 测试面盘点（基线）

nop-cluster 全模块测试共 **13 个文件**（与 C3 文件口径一致），逐文件通读结论：

| 测试文件 | 覆盖对象 | 并发相关内容 |
|---|---|---|
| `TestAbstractLeaderElector` | elector 生命周期/ promise/listener | **全单线程**；spin+sleep 等待调度；`TestElector.checkElection` 不自续（不调 `scheduleCheck`），轮询回路在测试中只跑一跳 |
| `TestLeaderElectorConfigBinding` | 配置绑定注解文本 | 无（反射钉定 `@cfg:` 前缀，P1-4.4-C1 回归） |
| `TestLoadBalance` | 4 类 LB | 无并发；LeastActive 用例**同用恒 0 adapter**（注释自认）；权重用例全部 weight≥1 |
| `TestWeightedPartitionAssigner` | 分区分配 | 无并发；`testZeroAndNegativeWeight` 钉定 weight≤0→100 语义 |
| `TestPartitionResolver` | 分区解析/TTL | 无并发；钉定"空→null"现行为；Mock 永不抛异常 |
| `TestCachingNamingService` | 缓存透传/原子装载 | async 用 `.get(timeout)`，**全顺序**，无并发交错 |
| `TestAutoRegistration` | 注册生命周期 | spin 等待刷新；stop 后 `sleep(100)` 断言静默（弱守卫） |
| `TestClusterRpc` | RPC 客户端 | **仅同步 call 路径**错误码断言（2 实例连接失败→`ERR_HTTP_CONNECT_FAIL`）；无 async/重试次数/超时断言 |
| `TestNacosNamingServiceCopyFromInstance` | copyFromInstance | 仅 serviceName `@@` 拆分（P1-4.4-C4 回归），静态调用新对象 |
| `TestSentinelFlowControlRunner` | 流控 runner 五路径 + newError 映射 | CompletableFuture 手工完成；**路径 4 钉定同步裸抛契约（反向钉死）** |
| `TestSentinelFlowControlRunnerContext` | context 泄漏 | 唯一的泄漏检测钩子（反射读 contextHolder），服务于此前的 P1-4.4-C3 修复 |
| `TestSpecificServiceInstanceFilter` / `TestRouteFilter` / `TestFilteredDiscoveryClient` / `TestCompositeHealthChecker` | filter/发现过滤/健康检查 | 纯顺序功能语义 |

**模块外间接覆盖**（生产子类族）：`nop-sys/TestDaoLeaderElector`、`nop-stream/TestJdbcLeaderElector(+EpochMonotonicity)`、`TestJobCoordinatorLeaderElection/JdbcHaIntegration` 使用真实子类与 `whenElectionCompleted()`——保护的是 **fencing epoch 单调性与 JobCoordinator 接线**（既有修复），其中 `testMultiThreadedExecutorTakeoverGuardsConcurrentCheckElection` 是模块外唯一多线程形态测试，但其 stop 序列为「`a.stop()` → `exec1.destroy()`」安全序，且 stop 后对被停节点 **零断言**（无 `isLeader()==false` 持续性、无 lease 行不再重写、无尾部 reschedule 终止断言）。`addCleanupTask` 全仓 grep 唯一命中即类定义（含测试，零触达）。

### 口径声明

1. **43 行的构成**：五文件「最终保留项」独立登记合计 39 条（P1×4 / P2×22 / P3×13；其中 C3-010 为并入 C1-007 主登记的行内注记），加 C8 复核「驳回＝同案并入」的 4 行（C8-004→C3-002、C8-008→C3-008、C8-009→C1-006、C8-010→C3-003），合计 **43 行**，并入行标注主登记。派发口径「P2×23/P3×12」与五文件实际合计存在 ±1 归类偏差，本矩阵以五文件「最终保留项」原文为准。
2. **三列判定口径**（C10 正文变异推演）：**会红·有保护**＝缺陷在场时测试红或修复后测试钉定正确行为（本单元为 0）；**假保护**＝存在表面相关测试但对缺陷无分辨力，或反向钉定缺陷行为；**无测试**＝该失效面无任何测试触达，修复与回归双向静默。
3. 本维度不重复缺陷本体判定，只评估保护力；缺保护条目按 C10 正文口径登记（P1 本体缺保护→P2 缺保护条目；P2/P3 本体缺保护→P3），**与缺陷本体分开计数**。
4. 证据基准：live code（worktree 基线 120ff53d0d 同批）。

## 保护力矩阵（43 行）

| # | 发现 ID（归属注记） | 终判级 | 覆盖判定 | 证据（变异推演：修复后何测试会红 / 回归如何逃逸） |
|---|---|---|---|---|
| 1 | C1-001（原地改写+`_host` 陈旧） | P1 | **无测试** | 唯一近邻 `TestNacosNamingServiceCopyFromInstance` 顺序调用 copyFromInstance 于新对象，只断言 serviceName 拆分；不断言并发撕裂/`_host` 失效；C1-001 修复（重建对象替换元素）后该测试保持绿。回归（恢复原地改写）无任何测试红 |
| 2 | C1-002（cleanupTasks 丢任务，条件化 P2） | P2 | **无测试** | `addCleanupTask` 全仓（含测试）零调用；`getCleanupTasks` 无测试触达；锁对称修复或 CopyOnWriteArrayList 改造均无测试可红 |
| 3 | C1-003（订阅快照回退） | P1 | **无测试** | `getServiceInfo` subscribe→getAllInstances→initInstances 编排零测试（无 Nacos 客户端运行时测试）；init 与事件乱序合并语义无断言 |
| 4 | C1-004（选主 stop 复活） | P1 | **无测试** | `TestAbstractLeaderElector` 全单线程；`testStopNotifiesListenersAndResetsEpoch` 顺序 complete→stop（竞态不可达）；`TestElector.checkElection` 不自续；模块外 JDBC 测试 stop→destroy executor 安全序且 stop 后对 A 零断言。移除 onElectionCompleted 的 synchronized 全套件仍绿 |
| 5 | C1-005（destroy 复活 3 站点） | P2 | **无测试** | NacosNamingService/NacosConfigService start/destroy/懒重建生命周期零测试；destroyed 标志修复与回归双向静默 |
| 6 | C1-006（零权重 nextInt 崩溃；吸收 C8-009 扩展） | P2 | **无测试**（扩展面遭反向钉死，见反向钉死 #3） | `TestLoadBalance` 全部用例 weight≥1，ttlWeight 恒>0，零/负权重分支不可达；`ttlWeight<=0` 回退修复后无测试红 |
| 7 | C1-007（AutoRegistration 停止无序，主登记，吸收 C3-010） | P3 | **假保护（弱）** | `testAutoUpdateRefreshesRegistration` stop 后 sleep(100) 断言 updateCount 不增——方向正确的静默观察，但竞态窗口微秒级 vs 20ms 周期，缺陷在场恒绿（现证）、修复后亦绿，双向无分辨力 |
| 8 | C1-008（CachingNamingService 双路径，条件化 P3） | P3 | **无测试**（对并发面） | `testGetInstancesAsyncCachesResult` 顺序 async→sync；并发 put 交错/旧覆新无断言；收敛 `computeIfAbsentAsync` 修复后 queryCount/assertSame 断言全部保持绿 |
| 9 | C1-009（RoundRobin 共享 seq） | P3 | **无测试** | `testRoundRobin` 单 lb 单服务单线程，只验证"轮询现象"；per-service 发号修复后仍绿；跨服务相位偏斜不可测 |
| 10 | C1-010（契约矛盾：可修改 vs 不可变） | P3 | **无测试** | javadoc 契约无测试锚点；无任何断言"返回列表可修改"；测试桩 `MockDiscoveryClient` 自身返回 `unmodifiableList`（与实现同风格）。修复为契约文本时无测试面 |
| 11 | C2-001（锁不对称，与 C1-002 同 diff） | P2 | **无测试** | 同 C1-002；getCleanupTasks 加 synchronized 的修复无测试可红 |
| 12 | C2-002（bin 锁内远程 I/O） | P2 | **无测试** | `getServiceInfo`/computeIfAbsent 首装路径零测试；首装串行化/延迟传导无时延断言形态 |
| 13 | C2-003（写站点锁覆盖 5 站点） | P2 | **无测试** | `TestAbstractLeaderElector` 单线程，锁覆盖在单线程下不可区分；5 写站点全部加锁后全套件绿 |
| 14 | C2-004（粗锁备查） | P3 | **无测试**（备查级） | 自我豁免登记条目，无正确性后果，不要求测试形态；登记注记即可 |
| 15 | C2-005（complete 持锁派发） | P2 | **无测试** | `testElectionCompletionPublishesEpoch` 顺序完成+get()；无并发等待者，锁内 continuation 执行不可观测；complete 移出锁外的修复无测试红 |
| 16 | C3-001（callAsync 重试异常吞没挂死） | P1 | **无测试** | `TestClusterRpc.testInvoke` 仅同步 call 路径且只断言最终错误码（对重试次数/终结性无分辨力）；callAsync 重试递归零覆盖；修复（try/catch+completeExceptionally）后 testInvoke 保持绿；回归（重挂）表现为测试永不结束也无人断言 |
| 17 | C3-002（广播半个成功，**C8-004 并入**，条件化 P2） | P2 | **无测试** | BroadcastRpcClient 零测试文件（休眠面双缺口：既无接线也无测试）；聚合/取消/get(0) 语义修复无测试可红——**随本体回升（任一部署声明 broadcast proxy 即同时暴露缺陷与缺口）** |
| 18 | C3-003（MasterServerChooser null 违约，**C8-010 并入**） | P2 | **无测试** | chooser 零单测；TestClusterRpc 走缺省 random chooser 不触达 null 分支；null→类型化错误修复无测试红。MasterServerChooser 若回归"无主即 NPE"同样静默 |
| 19 | C3-004（promise 等待者面） | P2 | **无测试** | `testRestartElectionResetsStateAndOpensNewPromise` 覆盖的是无缺陷的顺序路径（onRestartElection 重建 promise）；读取-attach 交错、stop→start 不重建面零测试 |
| 20 | C3-005（订阅泄漏；含 round2 NacosConfigService 扩展） | P2 | **无测试** | 非 NacosException 异常注入零覆盖（catch 分类不备不可测）；NacosConfigService 注册后取值失败泄漏面同零 |
| 21 | C3-006（事件线程内联执行，条件化） | P2 | **无测试** | addCleanupTask 零调用方（含测试）；慢任务阻塞传播不可构造——**随本体回升（接线即同时暴露缺陷与缺口）** |
| 22 | C3-007（Sentinel entry 泄漏） | P2 | **无测试** | `TestSentinelFlowControlRunner` 五路径全部以"future 必然终结"为前提（路径 2 手工 complete）；无 never-completing future 用例、无 entry exit 观测钩子；orTimeout 兜底修复后五路径全绿 |
| 23 | C3-008（同步逃逸，**C8-008 并入**） | P3 | **假保护（反向钉死）** | `testSupplierSyncThrowPropagatesSynchronously` 以 `assertSame(boom, thrown)` 钉定"同步裸抛、不包装"——**缺陷即契约**；实施修复（catch+traceEntry+reject）必然打红该测试（见反向钉死 #1） |
| 24 | C3-009（反向取消缺失） | P3 | **无测试** | 无 future.cancel 语义用例；幽灵请求面零断言 |
| 25 | C3-010（并入 C1-007 主登记） | P3 | **假保护（弱）** | 同 C1-007 行：stop 后静默断言存在但无竞态分辨力；合并修复同 diff 双向静默 |
| 26 | C3-011（配置回调无隔离） | P3 | **无测试** | NacosConfigService 零测试文件；onUpdateConfig 异常隔离/降级面零断言 |
| 27 | C3-012（发射中断孤儿腿） | P3 | **无测试** | 同 C3-002：BroadcastRpcClient 零测试；per-leg 隔离修复无测试红 |
| 28 | C4-001（stop 复活调度链面，双轨 P2） | P2 | **无测试** | `TestElector` 不自续→复活面结构性不可达；模块外测试 stop→destroy 安全序；「停机后无 lease 行重写/无尾部 reschedule」断言缺失（C4 复核已点名此回归面） |
| 29 | C4-002（isDestroyed 不转发，并入 C2-003 面） | P3 | **无测试** | 测试注入 `DefaultScheduledExecutor`（如实转发 `isDestroyed`），包装器形态不可达；守卫行为零断言 |
| 30 | C4-003（裸 globalTimer 轮询链无截止） | P2 | **无测试** | PollingRpcClient/轮询链在模块内零测试（nop-rpc-cluster 测试仅同步错误路径）；timer 占用/deadline 面零断言 |
| 31 | C4-004（自续链断链无监督） | P2 | **无测试** | 断链面（rejection/Error 断链）在测试形态下结构性不存在（TestElector 不自续）；无 future 观察断言；监督修复无测试红 |
| 32 | C4-005（start 两步无回滚） | P3 | **无测试** | `testInvalidVersionMetadataRejected` 只覆盖注册前校验（步骤①之前）；步骤② `scheduleWithFixedDelay` 失败注入零覆盖——**feature 门控（`nop.cluster.registration.enabled`），随本体回升** |
| 33 | C8-001（LeastActive 背压信号恒 0） | P2 | **假保护** | `testLeastActiveWrapsWithoutOverflow` 与 LB 测试族经恒 0 adapter 只覆盖退化均选分支（注释自认"活跃数恒 0（adapter 默认）"）；断言（`items.contains(picked)`）对选择逻辑失真不敏感（恒取首元素亦绿）；真实活跃计数实现后全部绿（见反向钉死/假保护 #2） |
| 34 | C8-002（RPC 重试零退避） | P2 | **无测试** | 无任何时间维度断言；testInvoke 对重试 0 次与 2 次同绿；退避/jitter 修复无测试红 |
| 35 | C8-003（广播扇出无上界，休眠条件化） | P2 | **无测试** | 同 C3-002 零测试面——**随本体回升（声明 broadcast proxy 即实际化）** |
| 36 | C8-005（instanceCache 无界+订阅永不退订，收缩后单面） | P2 | **无测试** | 缓存淘汰/退订钩子/`getServices` 截断告警面零测试；LocalCache 包装修复无测试红 |
| 37 | C8-006（PartitionResolver 无负缓存；含异常路径扩展） | P2 | **无测试**（现状契约被钉但缺陷不设防） | `testEmptyInstancesReturnsNull`/`testMyInstanceNotFoundReturnsNull` 钉定"空→null"现行为（fresh resolver，负缓存修复后仍绿）；Mock 永不抛异常→round2 异常路径扩展不可达 |
| 38 | C8-007（Sentinel 规则单向棘轮） | P2 | **无测试** | SentinelRuleConfig 零测试文件；isEmpty→跳过、解析失败静默保留旧规则均无断言 |
| 39 | C8-011（注销失败 ghost，feature-gated 条件面） | P3 | **无测试** | `testStopUnregistersLastInstance` 仅 happy path；注销失败注入零覆盖——**随本体回升（ephemeral=false 显式配置面）** |
| 40 | C8-004（并入 **C3-002** 主登记） | （P2 随主） | **无测试** | 随主登记行 17：BroadcastRpcClient 零测试；修复验收以 C3-002 状态为准 |
| 41 | C8-008（并入 **C3-008** 主登记，统计面随同 diff） | （P3 随主） | **假保护** | 统计漏计面（traceEntry 三路径不对称）无任何断言，主面反向钉死随行 23；trace+reject 强制要求无测试验证形态 |
| 42 | C8-009（并入 **C1-006** 主登记） | （P2 随主） | **假保护（反向钉死）** | `TestWeightedPartitionAssigner.testZeroAndNegativeWeight` 明文断言"零权重和负权重应该使用默认权重"（0/-50→各得 100），把 C8-round2 扩展点 1 认定的排流语义失效面**钉定为契约**（见反向钉死 #3） |
| 43 | C8-010（并入 **C3-003** 主登记） | （P2 随主） | **无测试** | 随主登记行 18：chooser null 面零测试；修复验收以 C3-003 状态为准 |

**矩阵统计**：会红·有保护 **0** / 假保护 **6**（C1-007、C3-010、C3-008、C8-008、C8-001、C8-009，其中 2 行为硬反向钉死）/ 无测试 **37**。P1×4（C1-001、C1-003、C1-004、C3-001）**全部无保护**。

## 反向钉死检查

对派发指定四个区域逐一回码，另检出两处区域外硬反向钉死：

### 命中 #1（硬反向钉死）：SentinelFlowControlRunner 同步逃逸契约被测试钉定
- **测试**: `nop-cluster-sentinel/src/test/java/io/nop/cluster/sentinel/TestSentinelFlowControlRunner.java:100-114`（`testSupplierSyncThrowPropagatesSynchronously`）
- **钉定内容**: `assertSame(boom, thrown)` + 消息 `"supplier exception must propagate synchronously and unwrapped"`——把 C3-008（P3 主登记）与 C8-008（统计漏计面）认定的缺陷行为（返回 `CompletionStage` 的方法同步裸抛、无 traceEntry）写成断言契约。
- **后果**: 实施主登记修复（`catch(Exception)` → `Tracer.traceEntry` + `FutureHelper.reject`）**必然打红该测试**；后来者会以"测试绿"为由拒绝语义修复。修复批必须同步改写该用例为"断言返回 rejected future 且 entry 计入异常统计"。

### 命中 #2（结构性假保护，派发点名项证实）：LeastActive 恒 0 adapter 同用
- **测试**: `nop-cluster-core/src/test/java/io/nop/cluster/lb/TestLoadBalance.java:128-139`（`testLeastActiveWrapsWithoutOverflow`）
- **钉定内容**: 测试注释自认"活跃数恒 0（adapter 默认）→ 全体并列最小 → 等权随机分支"——该测试（P1-4.4-C2 回绕回归）与整个 LB 测试族经 `ServiceLoadBalanceAdapter.getActiveCount()=0` 只覆盖 C8-001 缺陷态的退化分支；断言为 `items.contains(picked)`，对选择逻辑失真（如恒取首元素）不敏感。**C8-001 复核行"测试同用恒 0 adapter、保护力缺口成立"独立证实。** 真实活跃计数实现（Dubbo beginCount/endCount 形态）落地后将没有任何测试验证 leastActive 语义本身。

### 命中 #3（硬反向钉死）：WeightedPartitionAssigner 零权重语义被测试钉定
- **测试**: `nop-cluster-core/src/test/java/io/nop/cluster/assigner/TestWeightedPartitionAssigner.java:251-270`（`testZeroAndNegativeWeight`）
- **钉定内容**: 断言 0/-50 权重实例各得 defaultWeight=100 的完整份额（"零权重和负权重应该使用默认权重"）——把 C8-009 round2 扩展点 1（C1-006 主登记吸收）认定的"排流语义在分区分配面失效"钉定为契约。Assigner 语义修复（尊重 weight=0 或下限语义，属 owner 裁决项）必先改此测试。

### 命中 #4（弱守卫，非钉死）：AutoRegistration stop 后静默断言
- **测试**: `nop-cluster-core/src/test/java/io/nop/cluster/naming/TestAutoRegistration.java:143-148`
- **内容**: stop 后 `Thread.sleep(100)` 断言 updateCount 不增——是模块内唯一观察"stop 终止后台活动"的断言，方向正确；但对 C1-007/C3-010 的微秒级 check-then-act 竞态无分辨力（缺陷在场恒绿，现证）。属"偶发绿"形态而非反向钉死。

### 四个指定区域判定汇总

| 区域 | 反向钉死 | 保护力结论 |
|---|---|---|
| AbstractLeaderElector 测试 | 无（无缺陷行为被钉定） | **零并发保护**：全单线程；`TestElector.checkElection` 不自续（轮询回路只跑一跳，复活/断链面结构性不可达）；stop 用例为顺序 complete→stop。模块外子类测试保护 fencing 单调性（既有修复），stop 采用安全序且对被停节点零断言。C1-004/C4-001/C2-003/C3-004/C2-005 全部无测试 |
| NacosNamingService 测试 | 无 | **仅存静态拷贝回归**（serviceName `@@` 拆分，P1-4.4-C4）；生命周期/订阅/缓存/清理任务全裸。C1-001/C1-003/C1-005/C3-005/C8-005 无测试 |
| 负载均衡测试 | 假保护（命中 #2）+ 邻接反向钉死（命中 #3） | WeightedRandom 用例全部 weight≥1（C1-006 零保护）；RoundRobin 单实例单线程（C1-009 零保护）；LeastActive 只覆盖恒 0 退化分支（C8-001 假保护） |
| ClusterRpcClient 重试测试 | 无（同步路径行为本身正确） | `testInvoke` 仅同步 call 路径最终错误码断言；对重试次数无分辨力（0 次与 2 次同绿）；callAsync/重试终结/退避/取消全零覆盖。C3-001（P1）无测试 |

**反向钉死/假保护命中合计：4 处**（2 处硬反向钉死 + 1 处结构性假保护 + 1 处弱守卫）。

## 缺保护登记（C10-001 起）

> 按同案保护面合并为 14 条；含 P1 本体的保护面判 P2（C10 正文口径），其余 P3。条件化/门控条目标注「随本体回升」。

### [C10-001] ClusterRpcClient 调用链（重试终结/无主防护/退避/取消）零测试保护

- **文件**: 缺陷面 `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:60-126`；现测试 `nop-cluster/nop-rpc-cluster/src/test/java/io/nop/rpc/cluster/TestClusterRpc.java:26-38`
- **证据片段**:
  ```java
  // 现有全部覆盖：同步路径 + 仅断言最终错误码（对重试次数/终结性无分辨力）
  serviceA.myMethod(ApiRequest.build("test"));   // 走 ClusterRpcProxyFactoryBean 代理
  // → catch (NopException e) assertEquals(ERR_HTTP_CONNECT_FAIL...)
  // callAsync 的手工 future + 重试递归（C3-001 挂死链）零测试触达
  ```
- **严重程度**: P2（承载 P1 本体 C3-001 的缺保护）
- **保护对象**: P1 C3-001（重试异常吞没→future 永不完成）、P2 C3-003（MasterServerChooser null→NPE，C8-010 并入）、P2 C8-002（零退避风暴）、P3 C3-009（反向取消缺失）
- **变异推演**: 修复 C3-001（重试体 try/catch + completeExceptionally）后现有测试全绿——修复不被钉定；回归（重挂）表现为测试套件若覆盖 async 路径将永不结束，但该路径现在根本没有测试，回归静默逃逸。
- **现状**: live 默认装配路径（`rpc-cluster-defaults.beans.xml` AbstractRpcProxyFactoryBean→ClusterRpcProxyFactoryBean，C3-001 复核证实），全平台 @RpcClient 代理经此链，而测试仅覆盖同步路径的一个错误码。
- **风险**: P1 挂死缺陷可在任何重构后无声回归；上层超时兜底掩盖挂死使回归更难被发现。
- **建议**: (1) Mock IRpcService 首跳同步抛 RuntimeException + retryCount≥1 + 多实例 → 断言返回 future 在有限时间内 completeExceptionally（直接钉死 C3-001 修复）；(2) MasterServerChooser（leaderObserver 返回空）× callAsync/call → 断言 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 类型化错误（钉死 C3-003）；(3) 记录重试时间戳序列断言存在退避间隔（钉死 C8-002）；(4) cancel 返回 future 后断言底层 cancelToken 触发（钉死 C3-009）。
- **失效面枚举**: ClusterRpcClient.callAsync:60-94 / call:96-126 / getRpcClient:137-152；消费面 ClusterRpcServiceInvoker.invokeAsync、两个 ProxyFactoryBean——同一测试族可全盖，无遗漏站点。
- **信心水平**: 确定（测试文件全量通读）
- **误报排除**: testInvoke 虽真实执行了同步重试（2 个 invalid-domain 实例），但其断言粒度（最终错误码）对本案四个失效面均无分辨力，不构成保护。
- **复核状态**: 未复核（C10 新登记）

### [C10-002] BroadcastRpcClient 全类零测试（聚合/扇出/发射中断/取消四合一缺口）

- **文件**: 缺陷面 `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/BroadcastRpcClient.java:52-90`；测试目录 `nop-cluster/nop-rpc-cluster/src/test/java/io/nop/rpc/cluster/`（仅 3 文件，无 Broadcast 相关）
- **证据片段**:
  ```java
  // 现有测试清单：TestClusterRpc / ServiceA / MockDiscoveryClient —— 无 BroadcastRpcClient 任何触达
  // 缺陷面：waitAll=allOf 首败即全败（C3-002 主登记，C8-004 并入）
  //        + 发射循环无 per-leg 隔离（C3-012）+ 扇出无上界（C8-003）
  ```
- **严重程度**: P3（本体为条件化 P2 休眠面）
- **保护对象**: P2 C3-002（**C8-004 并入**）、P2 C8-003、P3 C3-012
- **变异推演**: 任一修复（失败路径聚合+cancel、per-leg try/catch、并发窗口）后无测试红；回归无测试拦截。休眠面双缺口：类既无生产接线也无测试，是本模块唯一"零触达"的 public RPC 客户端。
- **现状**: BroadcastRpcProxyFactoryBean 全仓零接线（C3-002/C8-003 复核同款 grep），测试同步缺失使该类成为"既不运行也不验证"的死区。
- **风险**: 一旦任一部署声明 broadcast proxy（回升条款触发），四个失效面（半个成功/无上界扇出/孤儿腿/反向取消）同时以零保护状态进入生产。
- **建议**: 单一测试类覆盖四面：(1) waitAll 部分失败→断言 per-server 明细+兄弟腿被 cancel；(2) 第 k 腿同步抛→断言已发射腿被 cancel 且整体失败；(3) N≥3 时断言在途并发≤配置窗口；(4) 与 C10-001 共用 Mock IRpcService 基建。
- **失效面枚举**: BroadcastRpcClient.callServersAsync 全方法 + BroadcastRpcProxyFactoryBean 装配面——本条全盖。
- **信心水平**: 确定
- **误报排除**: 零接线不豁免测试缺失登记——回升条款与测试缺口是同一枚硬币的两面，须同批补齐。
- **复核状态**: 未复核（C10 新登记）｜**随本体回升**

### [C10-003] 选主 promise/stop 生命周期并发面零保护（P1 承载）

- **文件**: 缺陷面 `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractLeaderElector.java:119-225`；现测试 `nop-cluster/nop-cluster-core/src/test/java/io/nop/cluster/elector/TestAbstractLeaderElector.java`（全量 181 行，单线程）
- **证据片段**:
  ```java
  // 现有 stop 用例：顺序调用，竞态不可达
  elector.complete(epoch("node-1", 1));            // T1 完成选举
  elector.whenElectionCompleted()...get(5, SECONDS);
  elector.stop();                                   // T2 停止——与 T1 无交错
  assertNull(elector.getLeaderEpoch(), "stop must reset leader epoch");
  // TestElector.checkElection() 只计数、不调 scheduleCheck()——轮询回路在测试中从不自续
  ```
- **严重程度**: P2（承载 P1 本体 C1-004 的缺保护）
- **保护对象**: P1 C1-004（stop 复活）、P2 C4-001（双轨，复活调度链面）、P2 C2-003（5 写站点锁覆盖）、P2 C3-004（等待者面）、P2 C2-005（complete 持锁派发）
- **变异推演**: 把 onElectionCompleted/doStop/onStop 的 synchronized 全部删除——现有 6 个用例全绿（单线程下锁不可区分）；把 doStop 的 promise.cancel 删除——同样全绿。修复（统一锁 + stopped 标志 + complete 移出锁外）后亦全绿，双向零分辨力。
- **现状**: elector 是本模块唯一有专门测试类的并发核心，但测试只验证顺序功能语义；P1 复活竞态的触发链（checkElection I/O 窗口 × stop）在测试形态下被 TestElector 的"不自续"实现结构性排除。
- **风险**: C1-004 修复（与 C4-001 同 diff）落地时无回归断言可依赖；未来任何锁纪律重构静默破坏复活防护。
- **建议**: (1) 并发复活测试：checkElection 内用 latch 挂起模拟 DB I/O，另一线程 stop()，释放后断言 `getLeaderEpoch()==null` 持续、`whenElectionCompleted()` 不返回新完成、listener 不再收到 becomeLeader（直接钉死 C1-004）；(2) stop→start 循环 ×2 断言无双回路（checkElection 计数增速不翻倍，钉死 C4-001）；(3) whenElectionCompleted() 挂接后立即 restartElection，断言等待者最终在 P2 上完成而非 CancellationException（钉死 C3-004）；(4) 慢 continuation 注册下断言 onRestartElection 不被阻塞超阈值（钉死 C2-005）。
- **失效面枚举**: AbstractLeaderElector 全部状态写点（onRestartElection/onElectionCompleted/onStop/doStop/newElection）+ AbstractPollingLeaderElector.scheduleCheck——测试形态建议(1)(2) 全盖；子类回归断言面（模块外 SysDao/Jdbc elector）按 C4 复核建议补"停机后无 lease 行重写"断言。
- **信心水平**: 确定
- **误报排除**: 现有测试并非无价值（钉定顺序语义与 P1-4.4 修复），只是对并发失效面分辨力为零——两件事不矛盾。
- **复核状态**: 未复核（C10 新登记）

### [C10-004] 自续调度链治理（断链监督/销毁守护）零保护

- **文件**: 缺陷面 `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractPollingLeaderElector.java:16-31`、`nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:204-218`；现测试同 C10-003 + `TestAutoRegistration.java:124-149`
- **证据片段**:
  ```java
  // AbstractPollingLeaderElector:28 —— schedule 返回 future 被丢弃（异常载体无人观察）
  executor.schedule(this::checkElection, getCheckIntervalMs(), TimeUnit.MILLISECONDS);
  // 测试侧：TestElector 从不续排 → 断链面在测试中结构性不存在
  ```
- **严重程度**: P3
- **保护对象**: P2 C4-004（断链无监督）、P3 C4-002（isDestroyed 不转发）
- **变异推演**: 修复（保留 pendingCheck Future + whenComplete 告警 + BindScheduledExecutor 转发 isDestroyed）后无测试红；回归（重新丢弃 future）无测试拦截。TestElector 不自续使"链死亡"在测试中不可构造。
- **现状**: 断链终态（选主静默停止、仅通用执行器日志）没有任何测试能到达；AutoRegistration 侧 testAutoUpdateRefreshesRegistration 只验证刷新发生与 stop 取消，不注入链级失败。
- **风险**: C4-004 修复（建议随 WI 2.1 C3-001 绝缘层同批）后，监督逻辑本身成为无测试的新代码。
- **建议**: (1) 注入"第 N 跳抛 Error/RejectedExecutionException"的 stub executor，断言链按退避重排或至少告警日志带业务标识（钉死 C4-004/CL-014）；(2) 注入自定义非转发包装器，断言 destroy 后 scheduleCheck 停止投递（钉死 C4-002）。
- **失效面枚举**: AbstractPollingLeaderElector 轮询链、AutoRegistration 刷新链、（模块外）PollingRpcClient 链——前两条本条全盖。
- **信心水平**: 确定
- **误报排除**: 非要求真实大规模压测——C10 正文口径允许小规模参数化模拟。
- **复核状态**: 未复核（C10 新登记）

### [C10-005] NacosNamingService 生命周期/订阅配对/缓存钉存面零保护

- **文件**: 缺陷面 `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:46-199,235-277`；现测试 `TestNacosNamingServiceCopyFromInstance.java`（仅静态拷贝 2 用例）
- **证据片段**:
  ```java
  // 模块内对 NacosNamingService 的全部测试触达：
  NacosNamingService.copyFromInstance(nacosInstance("my-service"), ret);  // 静态方法、新对象、无网络
  // destroy/懒重建、subscribe/unsubscribe 配对、instanceCache 淘汰 —— 零触达
  ```
- **严重程度**: P3
- **保护对象**: P2 C1-005（destroy 复活 3 站点）、P2 C3-005（订阅泄漏，含 NacosConfigService 扩展）、P2 C8-005（instanceCache 无界+永不退订）、P2 C2-002（bin 锁内远程 I/O）
- **变异推演**: 修复（destroyed 标志、catch(Exception) 回滚退订、LocalCache 包装+淘汰钩子）后现有 2 个用例全绿；回归全部静默。
- **现状**: 唯一测试锚定的是上一个修复批（P1-4.4-C4）的回归点；本批四个 P2 面全部无测试形态。
- **风险**: nacos 发现为 live 装配面（C1-001 复核证实"装配即生效"），修复批无回归护栏。
- **建议**: 用可注入的 Nacos client stub（或抽取 NamingService 工厂接缝）补：(1) destroy 后 getInstances 快速失败不重建（钉死 C1-005）；(2) getAllInstances 抛非 NacosException 后断言 Nacos 侧 unsubscribe 已执行、重复失败不累积 listener（钉死 C3-005）；(3) 超过 CacheConfig 容量的 serviceName 淘汰时断言退订（钉死 C8-005）；(4) 首装并发计数断言远程调用次数/锁外化（钉死 C2-002）。
- **失效面枚举**: NacosNamingService 生命周期站点 + NacosConfigService stop/configService 站点（C1-005 失效面③）——建议(1) 扩展到 ConfigService 同构断言。
- **信心水平**: 确定
- **误报排除**: stub Nacos client 是既有测试基建惯例（MockDiscoveryClient 同型），不构成对 Nacos SDK 的重审。
- **复核状态**: 未复核（C10 新登记）

### [C10-006] ServiceInstance 共享可变性与 cleanupTasks 执行模型零保护（P1 承载）

- **文件**: 缺陷面 `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:235-277`、`nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/ServiceInstance.java:172-178,289-300`；现测试 `TestNacosNamingServiceCopyFromInstance.java`、`TestSpecificServiceInstanceFilter.java`
- **证据片段**:
  ```java
  // 缺陷面：事件线程原地改写共享元素（copyFromInstance）+ _host 惰性缓存永不失效
  serviceInstance.setModifyIndex(serviceInstance.getModifyIndex() + 1);
  copyFromInstance(instance, serviceInstance);
  // 测试面：零并发读者、零 _host 断言、零 cleanupTasks 触达（addCleanupTask 全仓零调用）
  ```
- **严重程度**: P2（承载 P1 本体 C1-001 的缺保护）
- **保护对象**: P1 C1-001（原地改写+撕裂读+`_host` 陈旧）、P2 C1-002（丢任务，C2-001 同 diff）、P2 C3-006（事件线程内联，条件化）
- **变异推演**: 修复（重建对象替换元素 + `_host` 失效）后，copyFromInstance 的 2 个静态用例与 filter 用例全绿——修复不被钉定；回归（恢复原地 set）无测试红。
- **现状**: `TestSpecificServiceInstanceFilter.testFilterByHeaderTargetHost` 断言 getHost 匹配，但全部用例的实例从不经历"addr/port 变更后复用"——`_host` 缓存失效面恰在测试盲区。
- **风险**: P1 撕裂/陈旧端点在 LB/过滤/建连链上的修复无回归护栏；cleanupTasks 家族（休眠面）接线时将同时暴露缺陷与零测试。
- **建议**: (1) 修复后契约测试：同一 ServiceInstance 对象经历两次内容变更事件后断言 getHost() 返回新端点（钉死 `_host` 失效）；(2) 写侧重建后断言旧引用读者视图不变（钉死 copy-on-write 元素语义）；(3) 并发 addCleanupTask × getCleanupTasks 压力小测试断言任务零丢失（钉死 C1-002）；(4) 慢清理任务下断言后续事件处理不受阻塞（钉死 C3-006）——**随本体回升（C3-006 条件化）**。
- **失效面枚举**: C1-001 失效面 12 项读者 + cleanupTasks 写/读/执行三点——建议(1)(2) 盖读者面、(3)(4) 盖清理面。
- **信心水平**: 确定
- **误报排除**: 非要求 jcstress 级并发压测——契约级断言（修复后不变量）即可钉定本面。
- **复核状态**: 未复核（C10 新登记）

### [C10-007] 负载均衡权重/活跃数/公平性语义保护失真（假保护修复面）

- **文件**: 现测试 `nop-cluster/nop-cluster-core/src/test/java/io/nop/cluster/lb/TestLoadBalance.java:38-139`、`nop-cluster/nop-cluster-core/src/test/java/io/nop/cluster/assigner/TestWeightedPartitionAssigner.java:251-270`；缺陷面 `WeightedRandomLoadBalance.java:26-35`、`ServiceLoadBalanceAdapter.java:20-23`、`RoundRobinLoadBalance.java:17-25`
- **证据片段**:
  ```java
  // TestLoadBalance：全部权重 ≥1，零权重分支不可达
  item.setWeight(1); ...
  // TestWeightedPartitionAssigner.testZeroAndNegativeWeight：钉定 weight<=0→100（反向钉死）
  assertEquals(100, ranges.get(0).getLimit());   // 0 权重实例得全额分区
  ```
- **严重程度**: P3
- **保护对象**: P2 C1-006（零权重崩溃，**C8-009 并入**）、P2 C8-001（LeastActive 恒 0）、P3 C1-009（共享 seq）
- **变异推演**: C1-006 修复（`ttlWeight<=0` 回退）后无测试红（现有用例 weight≥1）；C8-001 修复（真实活跃计数）后现有 LB 测试全绿且无新测试验证 leastActive 语义；C1-009 修复（per-service 发号）后 testRoundRobin 仍绿。Assigner 语义修复将打红 testZeroAndNegativeWeight（反向钉死 #3），须同步改写。
- **现状**: LB 是测试数量最多的类（5 用例），却对三个已立案语义缺陷全部零分辨力——测试投入与缺陷分布错位的典型样本。
- **建议**: (1) 全零/含负权重用例断言类型化错误或回退（钉死 C1-006）；(2) 注入可变 getActiveCount 的 stub adapter，断言选中活跃数最小实例（钉死 C8-001 修复语义）；(3) 两个 chooser 交叉调用断言按服务独立轮询（钉死 C1-009）；(4) 改写 testZeroAndNegativeWeight 为 owner 裁决后的语义契约。
- **失效面枚举**: WeightedRandomLoadBalance/LeastActiveLoadBalance/RoundRobinLoadBalance/WeightedPartitionAssigner 四站点——建议(1)-(4) 一一对应。
- **信心水平**: 确定
- **误报排除**: 统计型断言（count>3000）对分布偏斜有粗分辨力，但对"语义恒 0"与"共享 seq 相位偏斜"类确定性缺陷不适用。
- **复核状态**: 未复核（C10 新登记）

### [C10-008] PartitionResolver 故障降级面零保护

- **文件**: 缺陷面 `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java:88-108`；现测试 `TestPartitionResolver.java:66-103`
- **证据片段**:
  ```java
  // 现有用例钉定现行为：空→null（fresh resolver，对负缓存修复无分辨力）
  void testEmptyInstancesReturnsNull() { namingService.setInstances(Collections.emptyList());
      assertNull(resolver.resolvePartitions()); }
  // MockNamingService.getInstances 永不抛异常 → round2 异常路径扩展不可达
  ```
- **严重程度**: P3
- **保护对象**: P2 C8-006（无负缓存/fail-closed；含异常路径扩展）
- **变异推演**: 修复（短 TTL 负缓存或 stale 回退）后现有 9 个用例全绿（每用例 fresh resolver）；回归无拦截；异常路径零覆盖。
- **风险**: 依赖故障期双重放大（查询风暴+N 倍全量处理）的修复无护栏；nop-job 调用方按 javadoc 契约依赖缓存目的声明。
- **建议**: (1) 空结果后短窗口内二次调用断言 getInstances 仅被调一次（钉死负缓存）；(2) Mock 注入 getInstances 抛异常，断言异常路径回退 stale 或负缓存（钉死 round2 扩展）；(3) fail-closed 语义经 owner 裁决后以用例钉定。
- **失效面枚举**: resolvePartitions 四条 return-null 路径 + 异常路径——建议(1)(2) 全盖。
- **信心水平**: 确定
- **误报排除**: 现有 null 契约用例是必要的行为锚点，保留即可，不因本登记删除。
- **复核状态**: 未复核（C10 新登记）

### [C10-009] SentinelFlowControlRunner entry 生命周期与统计对称面（含反向钉死解除）

- **文件**: 缺陷面 `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:60-99`；现测试 `TestSentinelFlowControlRunner.java:100-114`（反向钉死站点）、`TestSentinelFlowControlRunnerContext.java`（context 泄漏钩子先例）
- **证据片段**:
  ```java
  // 现有五路径全部以"future 必然终结"为前提；无 never-completing future 用例
  CompletionStage<String> ret = runner.runAsync(entry("res-async"), () -> stage);
  stage.complete("done");   // 手工终结——永不终结分支不可达
  ```
- **严重程度**: P3
- **保护对象**: P2 C3-007（entry 泄漏）、P3 C3-008（同步逃逸，**C8-008 并入**，含 trace 统计面）
- **变异推演**: C3-007 修复（orTimeout/超时装饰）后五路径全绿；C3-008 修复将打红 testSupplierSyncThrowPropagatesSynchronously（反向钉死 #1）——修复批必须同步解除钉死，并以新断言钉定修复。
- **现状**: context 泄漏已有反射检测钩子（P1-4.4-C3 修复回归），但 entry exit 配对与 Tracer 统计无对应钩子——同类检测形态已有先例可复制。
- **建议**: (1) never-completing future + 超时后断言 entry 已 exit（可用 Sentinel Node 统计/反射 curEntry，复用 context 钩子形态，钉死 C3-007）；(2) 改写路径 4 为"返回 rejected future + traceEntry 计入异常统计"（解除反向钉死并钉死 C3-008/C8-008）。
- **失效面枚举**: runAsync 三完成路径 + run() 对照——建议(1)(2) 全盖；ClusterNode 统计断言形态需一次 spike 验证 Sentinel 公开面。
- **信心水平**: 确定（钉死事实）/ 很可能（统计断言可行性）
- **误报排除**: 非对 Sentinel 库本身重审——断言对象是本模块适配层的配对与对称语义。
- **复核状态**: 未复核（C10 新登记）

### [C10-010] SentinelRuleConfig 热更新语义零测试

- **文件**: 缺陷面 `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/config/SentinelRuleConfig.java:81-119`；测试目录无对应文件
- **证据片段**:
  ```java
  // 全模块唯一限流规则入口，零测试文件：
  if (!StringHelper.isEmpty(flowRules)) { ... FlowRuleManager.loadRules(rules); }
  catch (Exception e) { LOG.error("nop.cluster.sentinel.load-flow-rule-fail", e); }
  ```
- **严重程度**: P3
- **保护对象**: P2 C8-007（单向棘轮+跨族半成功）
- **变异推演**: 修复（isEmpty→显式清空 + 失败聚合上报）后无测试红（无测试可红）；回归无拦截。
- **现状**: sentinel-defaults.beans.xml 缺省启用该 bean（enableIfMissing），是限流熔断唯一规则入口。
- **建议**: (1) 空串配置→断言 loadRules(emptyList) 被调用（钉死清空语义）；(2) 单族 JSON 笔误→断言聚合失败信号/指标且其他族不受影响（钉死半成功可见性）。
- **失效面枚举**: 四族规则（flow/degrade/sys/auth）同构循环——参数化 4 族即全盖。
- **信心水平**: 确定
- **误报排除**: FlowRuleManager 为 Sentinel 库面不重审；断言对象是本模块 refreshConfig 的编排语义。
- **复核状态**: 未复核（C10 新登记）

### [C10-011] AutoRegistration 生命周期失败路径族零保护（happy path 已有）

- **文件**: 缺陷面 `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:189-229`；现测试 `TestAutoRegistration.java`（4 用例全部成功路径或注册前校验）
- **证据片段**:
  ```java
  // 现有失败覆盖仅达步骤①之前：
  assertThrows(NopException.class, registration::start);  // 版本校验失败，registerCount==0
  // 步骤②失败回滚（C4-005）、注销失败兜底（C8-011）、stop 竞态（C1-007 主登记）——零注入
  ```
- **严重程度**: P3
- **保护对象**: P3 C1-007（主登记，C3-010 并入）、P3 C4-005（start 两步无回滚）、P3 C8-011（注销 ghost）
- **变异推演**: 修复（回滚 unregister、catch+重试/标志、stopped 标志）后现有 4 用例全绿；回归无拦截。testAutoUpdateRefreshesRegistration 的 sleep(100) 静默断言对竞态无分辨力（假保护，见矩阵行 7/25）。
- **现状**: 该类有本模块最好的生命周期测试骨架（RecordingNamingService 计数桩），扩展成本低。
- **建议**: (1) stub scheduleWithFixedDelay 抛 RejectedExecutionException→断言 unregister 已回滚（钉死 C4-005）；(2) stub unregisterInstance 抛异常→断言失败可见（标志/日志）且 stop 完成（钉死 C8-011）；(3) 并发 stop × 在途 refresh 断言 unregister 后无 update（钉死 C1-007，替代弱 sleep 断言）——**feature 门控面，随本体回升**。
- **失效面枚举**: AutoRegistration start/refresh/stop 三方法全部失败分支——建议(1)-(3) 全盖。
- **信心水平**: 确定
- **误报排除**: 现有 happy path 用例不因本登记弱化；三条建议均为增量。
- **复核状态**: 未复核（C10 新登记）

### [C10-012] CachingNamingService 并发装载语义零保护（休眠面）

- **文件**: 缺陷面 `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/CachingNamingService.java:36-54`；现测试 `TestCachingNamingService.java:107-126`
- **证据片段**:
  ```java
  // 现有 async 用例：顺序 async→sync→async，无并发交错
  List<ServiceInstance> asyncResult = caching.getInstancesAsync("svc-b")...get(5, SECONDS);
  caching.getInstances("svc-b");
  assertEquals(1, base.queryCount.get(), "sync read after async cache fill must not re-query");
  ```
- **严重程度**: P3
- **保护对象**: P3 C1-008（双路径不连贯，条件化回升条款）
- **变异推演**: 收敛 `LocalCache.computeIfAbsentAsync` 修复后现有 4 用例全绿；回归（重新引入 check-then-act）无测试红。
- **现状**: 类全仓零装配（仅测试引用），测试已验证顺序语义——与 BroadcastRpcClient 不同，本面有测试基建，缺的只是并发用例。
- **建议**: N 线程并发 getInstancesAsync × 交错 getInstances，断言底层装载次数≤装载并发度且缓存终态为最新快照（钉死原子装载语义）——**随本体回升（装配启用即同时需要本测试）**。
- **失效面枚举**: getInstances/getInstancesAsync 双路径——单测试方法全盖。
- **信心水平**: 确定
- **误报排除**: 休眠面登记依据与 C1-008 降档口径一致，不重复论证。
- **复核状态**: 未复核（C10 新登记）｜**随本体回升**

### [C10-013] PollingRpcClient 轮询链（timer 占用/无截止/取消）零测试

- **文件**: 缺陷面 `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:143-150`（接线）；现测试无（nop-rpc-cluster 测试仅同步错误路径）
- **证据片段**:
  ```java
  // 缺陷面（C4-003）：裸 globalTimer 注入 + PollingRpcClient 无整体 deadline
  service = new PollingRpcClient(service, pollingMethod, timer, interval, ...);
  // 测试面：polling 语义（PROP_POLLING_METHOD）在任何测试中零激活
  ```
- **严重程度**: P3
- **保护对象**: P2 C4-003（裸 timer 轮询链无截止）
- **变异推演**: 修复（executeOn 卸载 + 可选整体超时）后无测试红；回归无拦截。
- **现状**: opt-in 面（request 属性门控）且 PollingRpcClient 载体在 nop-rpc-core（模块外）——模块内可测的是接线决策（卸载与否）与超时装饰存在性。
- **建议**: (1) 断言传入 PollingRpcClient 的 executor 为 executeOn 包装形态（类Retriable：注入 stub timer 检查任务执行线程名，钉死卸载）；(2) 被放弃的 polling 请求在配置超时后断言链终止（钉死 deadline）。
- **失效面枚举**: ClusterRpcProxyFactoryBean:43 / ClusterRpcServiceInvoker:68 两注入点——参数化 2 站点全盖。
- **信心水平**: 确定（缺口）/ 很可能（线程名断言形态跨池稳定性）
- **误报排除**: 不要求真实 timer 占用压测——接线形态与终止性即可钉定。
- **复核状态**: 未复核（C10 新登记）

### [C10-014] API 契约文本与备查级条目（低优先登记）

- **文件**: 契约面 `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/IDiscoveryClient.java:35`、`chooser/IServerChooser.java:35-37`；备查面 `NacosNamingService.java:65-93`（C2-004）
- **证据片段**:
  ```java
  // 契约原文（C1-010）："返回的列表可以被修改" —— 实现三站点返回不可变/共享单例
  // 测试桩 MockDiscoveryClient 自身返回 Collections.unmodifiableList（与实现同风格，无契约断言）
  ```
- **严重程度**: P3（备查级）
- **保护对象**: P3 C1-010（契约矛盾）、P3 C2-004（粗锁备查）
- **变异推演**: C1-010 修复为 javadoc 文本——无测试形态可言；C2-004 为自我豁免备查条目，无正确性后果。
- **现状**: 契约类缺陷的"测试形态"只能是契约测试（ArchUnit/文档锚定）或 SPI 消费者兼容性测试，当前均不存在。
- **风险**: 低——若未来实现者"修契约"改回可变列表，将重新打开 C1-001/C8-005 共享可变面且无测试拦截（C1-010 条目自证的双向风险）。
- **建议**: 契约修订落地时补一条只读性契约测试（对 NacosNamingService.getInstances 返回值断言 UOE 或文档锚定测试），防止双向漂移；C2-004 不要求测试形态。
- **失效面枚举**: IDiscoveryClient/IServerChooser 两接口 + EmptyNamingService 同族——契约测试一处断言即盖 ①②⑤ 三违约站点。
- **信心水平**: 确定
- **误报排除**: 备查级登记不占用修复批资源，仅防止契约双向漂移。
- **复核状态**: 未复核（C10 新登记）

## 系统性观察

1. **全模块零真并发测试，43 条发现 0 条具备回归保护力**。13 个测试文件全部单线程；无竞争用例、无资源泄漏钩子（唯一钩子是 Sentinel context 反射检测，服务于此前的 P1-4.4-C3 修复）。变异推演结论：即使 43 条全部按建议修复，现有套件**一条都不会红**——修复既不被钉定也不被验证；反之任何回归静默逃逸。P1×4（撕裂读、快照回退、选主复活、RPC 挂死）全部裸奔，且三个是 live 主路径。仓库已有良好先例形态（P1-4.4-C2 回绕回归、P1-4.2-004 五路径表、TestNacosNamingServiceCopyFromInstance 的"修复即锚定"注释），缺的不是测试文化而是把这批发现转成会红用例的动作。
2. **两处测试反向钉死缺陷行为——测试在保护 bug 而非语义**。`testSupplierSyncThrowPropagatesSynchronously`（C3-008）与 `testZeroAndNegativeWeight`（C8-009/C1-006 assigner 面）把本轮认定的缺陷写成了 `assertSame`/`assertEquals` 契约；修复必先改测试，且存在"以测试绿为由拒绝语义修复"的长期风险。加上 LeastActive 恒 0 adapter 的结构性假保护（LB 唯一背压维度零分辨力）与 TestAutoRegistration 的 sleep 型弱守卫，模块内 6 条假保护中 4 条可归因于同一模式：**测试锚定"现状行为"而非"正确契约"**。修复批必须把"解除反向钉死"列为同 diff 强制项。
3. **调度链/生命周期面在测试形态下结构性不可达**。`TestElector.checkElection` 不自续（不调 scheduleCheck）使 C4-001 复活、C4-004 断链两大 P2 面在单测中根本无法发生；模块外真实子类测试（TestJdbcLeaderElector 族）全部采用 stop→destroy executor 安全序、stop 后对被停节点零断言——C4 复核点名的"停机后断言无 lease 行重写"回归面缺失属实。建议 C1-004/C4-001 修复 diff 的验收门槛直接写入该断言（含 C10-003 建议用例 1/2）。

## 终止判定

- C10 为本单元收官维度（最后批次），按 C10 正文完成六步：测试盘点、变异推演（43×会红判定）、时序敏感测试审查（spin+sleep 形态 3 处，均有界、无平台性脆弱；偶发绿形态 1 处已登记假保护）、资源泄漏钩子核查（仅 context 钩子，缺 entry/订阅/缓存钩子）、大规模边界核查（instanceCache 无界、扇出无上界、getServices 1000 截断均无边界测试，并入 C10-005/002）、保护力矩阵输出。
- **矩阵统计**：会红·有保护 0 / 假保护 6 / 无测试 37。**反向钉死命中 4 处**（2 硬 + 1 结构假保护 + 1 弱守卫）。**缺保护登记 14 条**（C10-001～014：P2×3 / P3×11），与缺陷本体分开计数，条件化条目均标注「随本体回升」。
- **最重要 3 个系统性观察**：① 43 条发现零回归保护、P1×4 全裸奔（修复与回归双向静默）；② 两处硬反向钉死——测试钉定缺陷契约，修复批须同 diff 解除；③ 调度链复活/断链面因测试桩不自续而结构性不可测，需以 latch 挂起 + 停机后断言形态补齐。
- **无新增 P0/P1 缺陷本体**（C10 产出仅为缺保护条目，最高 P2×3，均对应 P1 本体承载面）。修复批门禁建议：每修一案必附"会红"回归测试（对齐 P1-4.4 先例形态），优先补 C10-001/003/006 三个 P1 承载面与两处反向钉死解除。
- **审计终止**：无待深挖面；后续动作归属修复批与 owner 裁决（Assigner 零权重语义、C8-001 活跃计数实现形态）。

## C10 复核结论（WI 4.2 独立复核员，2026-10-20）

- **复核方法**：五文件「最终保留项」逐行对账 + 测试源码全量回查（nop-cluster 全部 19 个测试目录 java 文件，含 2 个盘点遗漏文件）+ 生产代码锚点核对（LB/ClusterRpcClient/SentinelFlowControlRunner/NacosNamingService.updateInstances/ServiceInstance/AutoRegistration/AbstractPollingLeaderElector/beans 装配）+ 模块外 elector 测试族核查（TestJdbcLeaderElector:240-297 逐行）。

### 一、矩阵对账：43 行闭合成立（0 差异）

1. 五文件「最终保留项」独立登记实数 **P1×4 / P2×22 / P3×13 = 39**（C1×10、C2×5、C3×12、C4×5、C8 保留×7），加 C8「驳回＝同案并入」4 行（C8-004→C3-002、C8-008→C3-008、C8-009→C1-006、C8-010→C3-003，与 C8 文件统计行逐字一致）＝ **43 行，矩阵逐行闭合**；43 行的 ID、终判级、并入归属注记与五文件原文全部一致，含 C3-010→C1-007 主登记注记（C1/C3 文件双侧一致）。
2. ±1 计数偏差对账属实：派发口径 P2×23/P3×12 与五文件实际 P2×22/P3×13 相差恰 1 条归类，本矩阵以五文件原文为准的处理正确（4+22+13=39、39+4=43 两条算术链均验算通过）。
3. 矩阵统计行验算：会红 0 / 假保护 6（行 7/23/25/33/41/42）/ 无测试 37，合计 43 ✓；P1×4 全部「无测试」✓。

### 二、抽查验证：13 行回码 + 4 处反向钉死核对，0 误判

抽查行（矩阵行号）：1、2、4、6、7、9、15、16、19、23、33、37、42——判定全部维持：

- 行 1/2（C1-001/C1-002）：`TestNacosNamingServiceCopyFromInstance` 确仅静态新对象 + serviceName 断言（2 用例）；`addCleanupTask` 全仓 grep 唯一命中 `ServiceInstance.java:289` 定义，`getCleanupTasks` 生产唯一调用 `NacosNamingService.java:269`、测试零触达 ✓
- 行 4/15/19（C1-004/C2-005/C3-004）：`TestAbstractLeaderElector` 全单线程 6 用例（实数 6，与 C10-003 一致）；`TestElector.checkElection` 只计数不自续（:52-55）✓；`testStopNotifiesListenersAndResetsEpoch` 顺序 complete→get→stop（:148-160）✓
- 行 6/9/33（C1-006/C1-009/C8-001）：LB 用例全部 weight≥1（`getItems` 恒 setWeight(1)）；`WeightedRandomLoadBalance:31` `nextInt(ttlWeight)` 零和崩溃面确认；`RoundRobinLoadBalance:18` 单一共享 `AtomicInteger seq` 确认；`testLeastActiveWrapsWithoutOverflow` 经恒 0 adapter（`ServiceLoadBalanceAdapter:21-23` 返回常量 0）+ `items.contains(picked)` 弱断言 ✓
- 行 7/25（C1-007/C3-010）：`TestAutoRegistration:143-148` stop 后 `sleep(100)` 弱守卫逐行属实 ✓
- 行 16（C3-001）：`TestClusterRpc:26-38` 仅同步路径 + 最终错误码断言；`ClusterRpcClient.callAsync:60-94` 重试递归 `this.accept(retryTimes+1)` 在 whenComplete 内无 try/catch（异常吞没→future 永不完成的机制确认）✓
- 行 23/42（C3-008/C8-009）+ 命中 #1/#3：`TestSentinelFlowControlRunner:100-114` `assertSame(boom, thrown)` + 消息原文、`TestWeightedPartitionAssigner:250-270` 0/-50→各得 100 断言逐字属实 ✓
- 行 37（C8-006）：`TestPartitionResolver` 每用例 `@BeforeEach` fresh resolver、`assertNull` 钉定空→null、Mock `setInstances` 纯 setter 永不抛 ✓
- **「全模块零真并发测试」核实成立**：全部测试目录 grep `new Thread|ExecutorService|supplyAsync|CountDownLatch|CyclicBarrier` 零命中；`Thread.sleep` 全模块恰 3 处（TestAbstractLeaderElector:105、TestAutoRegistration:137/146，与终止判定「spin+sleep 形态 3 处」一致）；Sentinel 测试的 future 均为手工 complete，无并发交错。
- 反向钉死 #2/#4、模块外族：`ServiceLoadBalanceAdapter.getActiveCount` 恒 0、`TestAutoRegistration` 弱守卫、`TestJdbcLeaderElector:240-297` stop→destroy 安全序 + stop 后对被停节点零断言（仅断言 B 接管与 fencing 单调性）、`addCleanupTask` 全仓零调用——全部属实。4 处命中（2 硬 + 1 结构假保护 + 1 弱守卫）**方向与断言原文全部一致，维持**。

### 三、修正项（4 处：1 Major + 2 Minor + 1 Trivial）

| # | 级别 | 修正项 | 复核证据与裁定 |
|---|------|--------|---------------|
| R1 | **Major（覆盖缺口，须修）** | **C1-003（P1 订阅快照回退）与 C3-011（P3 配置回调无隔离）在矩阵判「无测试」，但未被任何 C10-001~014「保护对象」收录**——14 条登记实盖 41/43 行，缺 2 行 | 全文 grep：C1-003 仅出现于矩阵行 3/统计行/区域汇总（L44/86/114），C3-011 仅矩阵行 26（L67），均不在登记节。C1-003 为 P1 本体，按本维度口径其缺保护应为 **P2 条目**。裁定：**并入 C10-005**（同测试族——NacosNamingService/NacosConfigService stub 基建同源，C10-005 失效面枚举已含 NacosConfigService 站点）：C10-005 保护对象补录 C1-003 后由 P3 **升 P2**，建议节补一条「subscribe 新建后首装 getAllInstances 与事件乱序交错断言快照不回退」；C3-011 同条收录（onUpdateConfig 异常隔离断言，P3 不改变条目定级）。若修复批不便合并，改单列 C10-015（P2）等效 |
| R2 | Minor | 测试面基线计数「13 个文件」不准 | 实测 nop-cluster 下含 `@Test` 的测试类 **17 个**（core 12 + nacos 1 + sentinel 2 + rpc-cluster 1 + hazelcast-core 1）；本文件盘点表自身已列 15 个（11 单列 + 1 行含 4 文件），与「13」自相矛盾。遗漏 2 文件：`TestPartitionAssigner`（1 用例，正权重分配 happy path，:21-37）、`TestHazelcast`（1 用例，getMap put 冒烟）——均非并发测试，**不影响任何矩阵行判定**，「零真并发测试」结论对全部 17 个文件成立。计数「13 f」系沿用 C3 文件头部口径（C3 L4「测试 13 f」），属上游口径错误接力。裁定：矩阵行与统计不动；基线数字以 17 为准 |
| R3 | Minor | C10-008「现有 9 个用例」实为 **10** 个 `@Test`（:40/50/58/65/71/77/86/98/105/112） | 计数 -1；「每用例 fresh resolver」与「负缓存修复后全绿」推演不受影响，判定维持 |
| R4 | Trivial | C10-004 证据片段标「AbstractPollingLeaderElector:28 —— schedule 返回 future 被丢弃」，被丢弃 future 的 schedule 实际在 **:30**（:28 为 `IScheduledExecutor executor = ...` 局部变量行） | 片段代码内容真实，仅行号偏移 2；登记条目有效性不受影响 |

### 四、判定统计

- **矩阵行**：43 行全部**维持**原覆盖判定（0 修正、0 驳回）；修正项 R2/R3 仅为基线计数勘误，不触达任何行的三态判定。
- **反向钉死**：4 处全部维持（命中 #1/#3 硬钉死断言原文逐字核实；#2 结构假保护机制核实；#4 弱守卫定性恰当）。
- **登记条目**：C10-001~014 **14 条全部保留**（0 驳回）；编号连续、格式合规（附录 A 字段以「保护对象」替代「并发场景/触发时序」属本维度合理变体，硬性字段「失效面枚举」14/14 在场；「随本体回升」标注在 C10-002/012 条目级与 C10-006/011/013 建议级分布与本体条件化状态一致）。
- **最终保留缺保护登记数：14 条**（合并口径；执行 R1 并入后为 **P2×4 / P3×10**，总条数不变；等效保护面 43/43 行闭合）。终态统计行的「P2×3 / P3×11」按 R1 执行后应读作 P2×4 / P3×10。

### 五、复核终判

C10 审计的 43 行矩阵、反向钉死 4 处、变异推演方法与三态判定口径**复核通过**；唯一实质缺陷是登记节对 C1-003/C3-011 的覆盖遗漏（R1），按上文裁定并入 C10-005 后闭合。修复批门禁建议（优先补 C10-001/003/006 三个 P1 承载面 + 两处硬反向钉死同 diff 解除）采纳；R1 执行后 C1-003 的 P1 承载面一并纳入 C10-005 修复批验收。
