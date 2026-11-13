# C8 背压、流控与过载行为 — nop-cluster（WI 4.2）

## 审计范围与口径

- **对象**：nop-cluster 全部 7 个源码根（74 文件 / 5,577 行）：nop-cluster-core、nop-rpc-cluster、nop-cluster-nacos、nop-cluster-sentinel、nop-hazelcast-core、nop-hazelcast-cache、nop-cluster-admin。roadmap 4.2 点名「限流熔断」，本维度主战场为 nop-cluster-sentinel 适配面与 nop-rpc-cluster 的重试/扇出/负载均衡交互。
- **owner doc 零覆盖声明**：`docs-for-ai/` 对 nop-cluster 无 owner 文档，全部过载/降级语义契约以代码内 javadoc 与 beans 装配为准；「文档声明 vs 实现一致」的比对基准为 javadoc 原文。
- **Phase 2 收官说明**：本批次为 Phase 2 最后一个维度（C8 收官维）。盲审纪律下未读 `ai-dev/audits/**`（本文件除外）、`plans/**`、`bugs/**`、`lessons/**`、`backlog/**`，未 git log。基线仅使用 `_tmp/cra-baseline-nop-cluster.txt` 的 C8a/C8b 节定位站点；数量结论按口径纪律人工复现（基线 C8a 命中的 CompositeHealthChecker/ResourceSpec/HazelcastCache.forEachEntry 经人工核验无本维度发现，C8b 命中的 LB 权重面产出 C8-001/C8-002/C8-009）。
- **与已立案条目的边界声明**（C1/C2/C3/C4 已立案 27 条）：
  - **C3-007（Sentinel Entry.exit 泄漏）**：本维度不审 entry/exit 配对泄漏面。经核验本文件两个路径（`runAsync` finally L87–93 与 `whenComplete` L77–80；`run` finally L169–173）exit 均配对，无新增泄漏条目。
  - **C3-001（callAsync 重试，异步正确性面）**：C8-002 仅立案重试的**流控语义面**（无退避/无抖动/失败风暴放大），不涉及重试链的异步正确性（ctx 传递、递归调度面）。
  - **C4-003（globalTimer 轮询链无截止）**：C8-008 的失效面枚举中显式声明相邻关系——异步 entry 释放依赖任务 future 完成，future 永不完成时限流许可被永久占用；该「无截止」根因已由 C4-003 立案，此处只作边界注记，不重复计数。
  - **C4-004（自续链饱和断链）**：无交集。
- **降档口径**：过载/降级面按行为性缺陷立案，单线程路径不豁免（C8-001 为纯确定性缺陷，无并发时序仍立案）。

### 生产-消费边界清单（C8 步骤 1 要求）

| # | 边界 | 有界性 | 结论 |
|---|---|---|---|
| 1 | 请求入口 → ClusterRpcClient 重试链 | 重试次数有界（默认 2/2/1）但无退避 | C8-002 |
| 2 | BroadcastRpcClient → N 个下游并发调用 | 扇出 = 集群规模，无上界/无分批 | C8-003 |
| 3 | Nacos 通知线程 → ServiceInfoListener.updateInstances + cleanupTasks | 串行执行用户清理任务，无隔离 | C8-005 |
| 4 | 调度调用方 → NamingService 查询（PartitionResolver TTL 缓存） | 正常路径有 TTL；失败路径无负缓存 | C8-006 |
| 5 | Sentinel entry 获取 → 异步释放（whenComplete） | 许可有界但释放依赖 future 完成 | 边界注记（接 C4-003） |
| 6 | 配置中心刷新 → 4 个 Sentinel RuleManager | 4 族独立更新，无原子性 | C8-007 |
| 7 | LB 活跃计数信号（LeastActive 的背压信号源） | 信号源缺失（恒 0） | C8-001 |
| 8 | HazelcastCache → IMap | Hazelcast 自管背压（平台外），核验无缺陷 | 无发现 |
| 9 | getServices() 分页查询（Nacos page=1000） | 有界但静默截断 | C8-005 子注记 |

---

## 发现

### [C8-001] LeastActive 负载均衡的活跃数背压信号恒为 0——过载感知路由整面失效

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/ServiceLoadBalanceAdapter.java:20-23`；关联 `LeastActiveLoadBalance.java:28,70`、`nop-rpc-cluster/src/main/resources/_vfs/nop/rpc/beans/load-balance-defaults.beans.xml:13-18,34`
- **证据片段**:
  ```java
  // ServiceLoadBalanceAdapter.java（全仓唯一 ILoadBalanceAdapter 实现）
  @Override
  public int getActiveCount(ServiceInstance candidate) {
      return 0;
  }
  // LeastActiveLoadBalance.java:28（javadoc 声明的语义）
  //  * 选择当前活动连接数最少的服务器
  // LeastActiveLoadBalance.java:70
  int active = adapter.getActiveCount(item);// 活跃数
  ```
- **严重程度**: P2
- **并发场景**: 全部 RPC 客户端线程并发调用 `LoadBalanceServerChooser.chooseFromCandidates` → `LeastActiveLoadBalance.choose`；活跃数本应作为集群过载信号的唯一生产端不存在。
- **触发时序**: 确定性行为缺陷，无需并发交错。T1：装配 `nopLoadBalance_leastActive`（load-balance-defaults.beans.xml:13-18 注入 `nopServiceLoadBalanceAdapter`，rpc-cluster-defaults.beans.xml:72-74 暴露 `nopServerChooser_leastActive`）。T2：任一请求执行 `choose`，L70 对每个候选读到的 active 恒为 0 → 全体候选并列最小 → 走加权随机/等权随机分支。后果：即使某实例活跃连接数远超其他实例，也不会被降低选中概率；「最不活跃优先」语义完全不存在。
- **现状**: 唯一的 adapter 实现硬编码返回 0，平台内不存在任何活跃计数的生产/维护代码（全仓 grep `getActiveCount|beginCount|activeCount` 仅命中接口定义与本实现）；javadoc 声明的选择语义与实现永久矛盾。
- **风险**: 配置 leastActive 的部署获得的是「候选窗口内加权随机」而非过载规避；运维以为存在的负载过载保护实际为零。且测试 `TestLoadBalance.java:129-130` 同样使用恒 0 adapter，leastActive 的既有测试对真实语义无保护力（C10 保护力缺口）。
- **建议**: 参考 Dubbo `RpcStatus.beginCount/endCount`，在 `HttpRpcService` 调用前后维护 per-instance 活跃计数（可用平台 `io.nop.commons.concurrent.IAtomicLong` 或 `LongAdder` per ServiceInstance），由 `ServiceLoadBalanceAdapter.getActiveCount` 读取；若短期不实现，应将 `nopServerChooser_leastActive` 标记 deprecated 并在 javadoc 声明当前退化为随机。
- **失效面枚举**: `ILoadBalanceAdapter` 实现全仓仅 `ServiceLoadBalanceAdapter` 一个：(1) `getActiveCount`——恒 0，本条立案；(2) `getWeight`——透传 `ServiceInstance.getWeight()`（Nacos metadata 真实回填，NacosNamingService.java:289），正常。消费方两个：`LeastActiveLoadBalance`（本条）、`WeightedRandomLoadBalance`（只用 getWeight，不受影响）。
- **信心水平**: 确定
- **误报排除**: 非「无状态单例被并发调用」类误报；这是 javadoc 声明语义（L28）与实现的直接矛盾，且装配为生产缺省可注入 bean，非死代码。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 面）——getActiveCount 恒 0（ServiceLoadBalanceAdapter:20-23 回码）、javadoc「选择当前活动连接数最少的服务器」（LeastActiveLoadBalance:28）与实现永久矛盾、全仓 getActiveCount/beginCount 零其他命中（活跃计数生产端不存在，独立 grep 证实）、既有测试同用恒 0 adapter（TestLoadBalance:41/61/130，C10 保护力缺口成立）均回码；装配链 nopServerChooser_leastActive→nopLoadBalance_leastActive→恒 0 adapter 证实（load-balance-defaults.beans.xml:23-28 + rpc-cluster-defaults.beans.xml:74-76）。子注记 `i > 0` sameWeight 缺陷回码成立（plan 2395 修窗口回绕时遗留），随本条同 diff 修正。leastActive 为 opt-in chooser（缺省 nopServerChooser_random），P2 按「背压信号面整面失效 + 声明矛盾 + 保护力缺口」校准恰当
- **子注记（同文件 P3 顺带项）**: `LeastActiveLoadBalance.java:83` `if (sameWeight && i > 0 && weight != firstWeight)`——窗口回绕后 `i==0` 是合法候选下标，位于下标 0 的非首候选权重差异永远不会打破 sameWeight 判定，加权 tie-break 少数场景失真（从 Dubbo 继承的同源缺陷；修复 C8-001 时应一并修正为 `leastIndexs[0] != i` 判定）。

### [C8-002] RPC 重试零退避零抖动——故障期重试风暴放大 3 倍且跨实例扩散

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:79-87,104-124`；装配 `rpc-cluster-defaults.beans.xml:91,107,137`
- **证据片段**:
  ```java
  // callAsync 失败分支（L82-86）
  if (instances.size() > 1) {
      instances.remove(instance);
  }
  this.accept(retryTimes + 1);   // 立即递归重试：无 delay、无退避、无抖动
  // 装配缺省：retryCount=2（cluster-client）/1（invoker）
  <property name="retryCount" value="@cfg:nop.rpc.cluster-client-retry-count|2"/>
  ```
- **严重程度**: P2
- **并发场景**: 全部 RPC 调用方线程 × 网络故障期；每次失败后 `accept` 在 RPC 回调线程上立即发起新一轮选择+调用，无任何时间维度上的流量整形。
- **触发时序**: T1：下游某实例连接故障（对 `NopConnectException` 可重试，L133-135）。T2：任一在途请求在 L75 `whenComplete` 收到连接异常 → L85 立即 `accept(retryTimes+1)`，同毫秒级重新 choose 并发起下一次连接。T3：故障持续期间每个上游请求产生 retryCount+1（默认 3）次背靠背连接尝试，且因 L83 `instances.remove` 将重试**扩散**到其他实例——网络抖动被放大为对全实例列表的快速连击；在途请求量 Q 时故障实例及其邻居承受最高 3Q 的连接冲击。
- **现状**: 重试仅由次数上界约束，无指数退避、无 jitter、无 per-instance 熔断联动；与 Sentinel 降级规则的联动依赖 trace 统计生效（若未配置规则则完全无保护，Sentinel 缺省 fail-open）。
- **风险**: 「负载上升必然触发」族的放大器：故障期最需要降载的时刻反而产生最高连接压力，拖慢故障实例恢复；对下游为短连接/高连接建立成本的协议时放大更明显。
- **建议**: 重试间引入退避（复用平台 `IScheduledExecutor`/`GlobalExecutors.globalTimer()` 做 delay 调度，指数 + jitter）；或接入 Sentinel 熔断规则作为缺省配置示例。
- **失效面枚举**: (1) `callAsync` 重试链 L79-87——本条主面；(2) `call` 同步重试循环 L104-124——同样零退避，同族同等立案；(3) `ClusterRpcServiceInvoker`（retryCount 缺省 1，L137）与两个 ProxyFactoryBean（缺省 2，L91/L107）——均为无退避语义的配置入口，修复退避时须同时覆盖。与 C3-001（callAsync 异步正确性面）同站点不同失效面，已声明边界。
- **信心水平**: 确定
- **误报排除**: 重试有次数上界（非无界重试），但 C8 判据是「上游快于下游时有界降级」——此处有界的是单请求尝试数，无界的是**故障期单位时间的连接尝试总量**（随 QPS 线性放大且无时间整形），不属误报校准条款。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 面）——whenComplete 回调线程内 `this.accept(retryTimes + 1)` 立即递归（ClusterRpcClient:79-87）、零 delay/退避/jitter、retry 仅 NopConnectException（:133-135）、装配缺省 `nop.rpc.cluster-client-retry-count|2`（两个 ProxyFactoryBean）与 `nop.rpc.proxy.client-retry-count|1`（nopClusterRpcServiceInvoker，rpc-cluster-defaults.beans.xml:135-139）均回码成立；`instances.remove` 扩散重试至其他实例（:82-84）成立。与 C3-001（同站点异步正确性面）边界维持。与 WI 2.3 C8-002（P1，429×7 放大有确定性 OOM 轨迹）分档合理：本条尝试次数有界、无 OOM 轨迹，P2 恰当

### [C8-003] BroadcastRpcClient 并发扇出无上界——单调用放大为全集群并发请求

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/BroadcastRpcClient.java:70-75`；缺省装配 `BroadcastRpcProxyFactoryBean.java:20`（waitAll=true）
- **证据片段**:
  ```java
  List<CompletionStage<ApiResponse<?>>> futures = new ArrayList<>(servers.size());
  for (ServiceInstance server : servers) {
      CompletionStage<ApiResponse<?>> future = clientProvider.getRpcClientInstance(server)
              .callAsync(serviceMethod, request, cancellable);
      futures.add(future);   // 一次调用 → N 个并发下游请求，无并发上界/无分批
  }
  ```
- **严重程度**: P2
- **并发场景**: 单调用方线程在循环内同步向 servers.size() 个实例并发发射；N 个广播调用方 × N 实例 = N² 量级瞬时下游并发。
- **触发时序**: T1：上游对广播服务发起一次 `callAsync`。T2：L71-75 一次性向全部实例（经 HealthyServiceInstanceFilter 过滤后的完整列表）并发发射请求，无 chunking、无并发上限、无信号量门控。T3：广播服务常用于缓存失效/配置下发类操作，集群扩容到数十实例 + 上游批量触发（如管理端刷新全量缓存）时，瞬时并发 = 调用方数 × 实例数，下游每实例同时收到全量冲击——上游 1 个请求在下游放大为 N 个，且无任何过载可观测指标。
- **现状**: 扇出规模完全由服务发现结果决定，模块内无任何背压机制（平台 `ISemaphore`/`HighWatermarkSemaphore` 未使用）。
- **风险**: 大集群 + 高频广播场景下游过载；与 C8-002 的缺省 retryCount=2 叠加时，下游一抖动扇出请求再放大 3 倍。
- **建议**: 增加可配置的并发窗口（复用平台 `ISemaphore` 或分批 `CycleSynchronizer`），缺省给保守上界；扇出深度与拒绝数纳入统计指标。
- **失效面枚举**: 广播入口全仓仅 `BroadcastRpcClient.callServersAsync` 一处（`BroadcastRpcProxyFactoryBean` 是唯一装配入口）；`waitAll` 与 `waitAnySuccess` 两分支共用同一无上界扇出循环，均立案于本条；无其他同族兄弟遗漏。
- **信心水平**: 确定
- **误报排除**: 广播语义本身要求触达全部实例，但「必须触达全部」不等于「必须无界并发同时触达」——分批/限并发不改变广播语义，故不属已声明边界。
- **复核状态**: 未复核
- 复核状态: 保留(P2，休眠条件化携回升条款)｜新发现未修复（休眠面）——扇出循环无并发上界/无分批/无 ISemaphore 门控（BroadcastRpcClient:70-75 回码）、waitAll/waitAnySuccess 两分支共用同一循环（:77-85）成立。现状修正：载体 BroadcastRpcProxyFactoryBean 全仓非测试**零接线**（本轮复核独立复现 C3-002 复核同款 grep——rpc-cluster-defaults.beans.xml:110-115 仅 abstract 模板 bean，`new BroadcastRpcClient` 全仓仅 FactoryBean:39 一处；原条目「缺省装配 waitAll=true」的加重表述不成立，实为「模板缺省 waitAll=true、待部署显式声明」）——按 WI 3.3 渠道栈条件化口径休眠降档注记，任一部署声明 broadcast proxy 即实际化回升 P2；机制本身维持立案不驳回

### [C8-004] Broadcast waitAll 半成功语义：首败即全败、其余结果丢弃、失败后孤儿调用不取消

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/BroadcastRpcClient.java:77-90`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:397-413`
- **证据片段**:
  ```java
  if (waitAll) {
      return FutureHelper.waitAll(futures).thenApply(r -> buildResponse(futures));
      // waitAll → CompletableFuture.allOf：任一失败立即整体失败
  } else {
      return FutureHelper.waitAnySuccess(futures).thenApply(r -> {
          cancellable.cancel();   // 仅此分支有取消收尾
          return (ApiResponse<?>) r;
      });
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程与 N 个下游 RPC 回调线程；`allOf` 在任一 future 失败时立即异常完成，其余 N-1 个调用继续在各自回调线程执行至完成。
- **触发时序**: T1：waitAll=true（缺省）广播 10 实例。T2：实例 3 首先失败 → `allOf` 异常完成 → 调用方收到整体失败。T3：其余 9 个实例的服务端**照常执行完成**（waitAll 分支无 `cancellable.cancel()`），其成功/失败结果全部被丢弃，调用方不可见。T4：调用方按失败语义重试 → 全部 10 实例再次执行——非幂等广播操作被重复执行且第一次执行的 N-1 个结果无任何恢复/对账路径（F2「半个成功」）。
- **现状**: waitAll 分支的失败聚合语义未定义：只透出 `allOf` 首个异常，其余失败静默吞没（F9 相邻）；成功时 `buildResponse` 也仅返回 `futures.get(0)`（首个实例响应），广播的部分失败完全不可观测。
- **风险**: 「半个成功」无恢复语义——对非幂等广播操作（如下发状态变更）产生跨实例重复执行；故障排查时丢失 N-1 个实例的真实结果。
- **建议**: waitAll 分支失败时也执行 `cancellable.cancel()`（语义：整体已判败，停止无意义在途工作——与 waitAnySuccess 分支对齐）；聚合各实例结果为结构化响应（成功集/失败集），或至少在文档声明「任一失败即整体失败且不取消其余」的语义。
- **失效面枚举**: (1) `waitAll=true` 分支（缺省，BroadcastRpcProxyFactoryBean.java:20）——本条主面；(2) `waitAnySuccess=false` 分支——有取消收尾、首败不取消属合理（等待可能的成功），不立案；(3) `buildResponse` L88-90 单响应丢弃面——并入本条语义缺陷，不单独计数。
- **信心水平**: 确定
- **误报排除**: 非误报校准条款情形；「半个成功无恢复语义」是 P1/P2 判据明文列项（F2 定向检查项），且该分支为缺省装配路径。
- **复核状态**: 未复核
- 复核状态: 驳回（同案并入 C3-002 主登记）｜同站点同缺陷三重合——waitAll=allOf 首败即全败、失败路径 cancellable 从不触发、buildResponse 仅取 get(0) 与 C3-002 证据**完全重合**（同站点 BroadcastRpcClient:65-90、同修复 diff：聚合 per-server 结果+失败路径 cancel）；C3-002 已主登记并复核（降级 P2 休眠条件化携回升条款），C3-round2 复核已裁「C3-002/C8-004 互引不重复计数」。本条无超出 C3-002 的增量证据，关闭并指向主登记；修复验收以 C3-002 状态为准

### [C8-005] NacosNamingService 实例缓存无界增长、订阅永不退订、清理任务阻塞共享通知线程

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:46,174-176,186-198,268-276`
- **证据片段**:
  ```java
  private Map<String, ServiceInfo> instanceCache = new ConcurrentHashMap<>();   // L46 无界、无淘汰

  public List<ServiceInstance> getInstances(String serviceName) {
      return instanceCache.computeIfAbsent(serviceName, this::getServiceInfo)
              .getServiceInstances();   // L174-176 每个 serviceName 永久钉住一个订阅
  }
  ...
  for (Runnable task : serviceInstance.getCleanupTasks()) {   // L269-271 在 Nacos 通知线程内串行执行
      try { task.run(); } ...
  }
  ```
- **严重程度**: P2
- **并发场景**: Nacos 客户端通知线程（事件分发）+ 全部业务调用线程；每个不同 serviceName 触发一次 subscribe + 永久缓存。
- **触发时序**: T1：任一调用线程以新 serviceName 调 `getInstances` → `computeIfAbsent` 创建 ServiceInfo + `namingService.subscribe`（L188）。T2：该条目及其 Nacos 订阅监听器永久存留——instanceCache 无淘汰、无 TTL、无 idle 退订（唯一 unsubscribe 路径 L194-197 仅覆盖初始化失败）。T3：实例变更事件到达 → `updateInstances` 在**通知线程内**串行执行被移除实例的全部 cleanupTasks（L268-276）→ 单个慢清理任务阻塞 Nacos 客户端全部命名事件分发 → 实例上下线向所有服务的传播延迟 → 长时运行下路由陈旧窗口扩大（订阅风暴下的雪崩放大）。
- **现状**: F4 无界账本：缓存键空间 = 调用方使用过的全部 serviceName；服务名有界（常规微服务部署）时增长有界，但经 `ClusterRpcServiceInvoker`（allowedServiceNames 配 `*`）+ 网关转发用户可控服务名时键空间无界，且无任何上限声明。附带：`getServices()` L167 `getServicesOfServer(1, 1000)` 单页取 1000，服务数超限静默截断。
- **风险**: 长时运行无界增长（条件：无界服务名键空间）；慢清理任务造成全平台服务发现事件传播的队头阻塞；两者叠加即「订阅/发现风暴」失效面。
- **建议**: instanceCache 加容量上限与空闲淘汰（复用 `LocalCache`/`CacheConfig`，淘汰钩子内 unsubscribe）；cleanupTasks 移交平台执行器异步执行；getServices 截断时至少 LOG.warn。
- **失效面枚举**: (1) `instanceCache` L46——本条主面；(2) `CachingNamingService` 的 `LocalCache`（容量 1000 + TTL，CachingNamingService.java:15-18）——有界声明存在，不立案；(3) `getServices()` L165-171 截断面——子注记并入；(4) `unsubscribe` L222-228——仅错误路径可达，正常路径无调用方，属本条泄漏面组成。
- **信心水平**: 确定（无界与阻塞面）；很可能（键空间无界的实际触发依赖网关代理部署形态）
- **误报排除**: 不是「有界声明存在的缓存」误报项——instanceCache 是裸 ConcurrentHashMap，无 CacheConfig、无任何上限声明。
- **复核状态**: 未复核
- 复核状态: 保留(P2，失效面收缩)｜新发现未修复（live 面，收缩后单面）——三面复核裁定：**(c) cleanupTasks 内联阻塞通知线程 = C3-006 同案**（P2 已复核保留，条件化：addCleanupTask 全仓零调用方），移交主登记；**订阅泄漏的异常路径半边 = C3-005 同案**（P2 已复核保留），移交主登记；**本条保留独立面 = instanceCache 裸 ConcurrentHashMap 无淘汰无 TTL（:46 回码）+ 正常路径订阅永不退订（unsubscribe 仅 getServiceInfo 失败回滚可达，:194-197）→ 键空间条件性无界增长**。getServices(1,1000) 静默截断子注记保留（:167 回码）。无界性为条件面（服务名有界部署增长有界；allowedServiceNames 配 `*` + 网关转发用户可控名时无界—— beans 缺省值为空串非 `*`，需显式配置），P2 维持；修复为单 diff（LocalCache/CacheConfig 包装 + 淘汰钩子 unsubscribe），与 C3-005/C3-006 同批不冲突

### [C8-006] PartitionResolver 失败/空结果无负缓存——依赖故障期退化为逐调用远端查询并解除分区过滤

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java:32,35,88-98,105-108`
- **证据片段**:
  ```java
  if (cachedPartitions != null && (now - lastResolveTime) < CACHE_TTL_MS) {
      return cachedPartitions;                    // L89-91 仅成功结果进缓存
  }
  List<ServiceInstance> servers = namingService.getInstances(svcName);
  if (servers == null || servers.isEmpty()) {
      return null;                                // L96-98 空结果：不缓存，直接退回 null
  }
  ...
  if (myRange.isEmpty()) { return null; }        // L105-108 实例未命中：同样不缓存
  ```
- **严重程度**: P2
- **并发场景**: 业务调度线程（javadoc L35「高频扫描」场景，如 nop-job 每 tick 调用）× Nacos 依赖故障/分区重分配窗口；无并发守卫问题（volatile 对，属 C1 面，此处不立案）。
- **触发时序**: T1：Nacos 抖动或本实例尚未出现在实例列表（扩容窗口/hostId 不一致）→ `getInstances` 返回空 → L97 返回 null，**未写缓存**。T2：下一个调用立即再次穿透到 NamingService——javadoc L35 声明的缓存目的（「避免高频扫描时反复查询 NamingService」）在恰恰最需要保护的依赖故障态完全失效，查询频率退化为调用方全频。T3：null 按契约（javadoc L32「调用方据此不追加分区过滤条件，扫描全表」）使调用方从分区处理切换为全量处理——集群 N 实例各自全量执行，工作负载瞬时放大 N 倍且任务跨实例重复执行。
- **现状**: 缓存只覆盖「成功且非空」路径；故障/空结果两个高频路径既无负缓存（TTL 内记住空结果）也无降级声明，降级方向与背压原则相反（依赖变弱时反而加重对其压力与自身负载）。
- **风险**: 依赖故障期的双重放大：对 NamingService 的查询风暴 + 集群级全量处理放大；长时运行中 Nacos 滚动重启等常态化抖动即可反复触发。
- **建议**: 空结果/未命中路径引入短 TTL 负缓存（可短于 10s 正缓存）；或提供 fail-closed 配置位（解析失败时沿用上次 cachedPartitions 直至 TTL 超时，超时后显式报错而非静默 null）。
- **失效面枚举**: `resolvePartitions` 的全部 return null 路径逐一核查：(1) L80-82 assignedPartitions 显式配置为空集——静态配置语义，合理；(2) L84-86 enableCluster 关闭/namingService 为 null——静态配置语义，合理；(3) L96-98 空实例列表——无负缓存，立案；(4) L105-108 myRange 未命中——无负缓存，立案。子类（nop-job JobPartitionResolver 等按 javadoc L37 继承本实现）同面受影响。
- **信心水平**: 确定（行为事实）；很可能（N 倍放大的实际规模取决于调用方扫描频率）
- **误报排除**: 非「已声明的边界」——javadoc 声明了 null→全表语义，但未声明「故障瞬态也走该降级」；负缓存缺失与 javadoc 自身的缓存目的陈述（L35）直接矛盾。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 面）——正/负缓存缺失三路径逐点回码成立：仅成功非空路径写缓存（resolvePartitions 尾部 `cachedPartitions=result; lastResolveTime=now`）、空列表 return null 不缓存、myRange 未命中 LOG.warn 后 return null 不缓存；round2 扩展（异常路径：`namingService.getInstances(svcName)` 无 try/catch，异常携无缓存无 stale 回退直接穿出）回码成立；javadoc「避免高频扫描时反复查询 NamingService」与故障态行为自相矛盾（:35）、null→全表扫描契约（javadoc 明文）成立。与 C4 文件 CL-013（volatile 对非原子，P3 不立案）边界维持。fail-safe 降级方向缺陷（依赖越弱越放大）P2 恰当；失效面(3)(4) 两条 return-null 路径 + 异常路径统一随单 diff 修复

### [C8-007] SentinelRuleConfig 热更新单向棘轮：规则无法卸载 + 单族解析失败静默保留旧规则（跨族半成功）

- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/config/SentinelRuleConfig.java:81-119`；装配 `sentinel-defaults.beans.xml:7-12`（`ioc:refresh-config-method="refreshConfig"`，缺省启用）
- **证据片段**:
  ```java
  public void refreshConfig() {
      if (!StringHelper.isEmpty(flowRules)) {       // L81 空串/清空配置 → 直接跳过
          ...
          FlowRuleManager.loadRules(rules);          // loadRules 是「全量替换」语义
      } catch (Exception e) {
          LOG.error("nop.cluster.sentinel.load-flow-rule-fail", e);   // L87 解析失败仅记日志
      }
  ... degradeRules / sysRules / authRules 四族独立同构（L91-119）
  ```
- **严重程度**: P2
- **并发场景**: 配置中心刷新线程调用 `refreshConfig`（IConfigRefreshable 热更新路径）× 业务请求线程并发执行 `SphU.entry` 限流判定；规则热更新过程中判定面读旧规则集。
- **触发时序**: T1（无法关闭限流）：运维清空 `nop.cluster.sentinel.flow-rules` 期望关闭限流 → 刷新触发 `refreshConfig` → L81 isEmpty 判真跳过 → `FlowRuleManager` 内旧规则集原样保留 → 限流继续生效，唯一出路是重启进程。T2（半成功）：一次刷新中 flowRules JSON 有笔误、degradeRules 正常 → flow 族解析失败仅 LOG.error、**旧 flow 规则继续生效**，degrade 族成功更新 → 四族规则跨代混跑，无任何聚合失败信号（各族独立 try/catch，F2）。
- **现状**: `loadRules` 本身是全量替换（正确选择），但 isEmpty 守卫把「空配置」解释为「跳过」而非「清空」，热更新形成单向棘轮：只能换规则、不能卸规则；解析失败静默降级为旧规则集，热更新调用方无失败反馈。
- **风险**: 故障应急场景（限流规则误配置需要紧急关闭）无法在线操作；规则漂移不可见——运维以为已生效的新规则可能因一次解析失败从未生效。
- **建议**: isEmpty 时显式 `loadRules(Collections.emptyList())` 声明清空语义；单族解析失败时聚合上报（抛出或记录统一错误码 + 刷新结果指标），至少保证「热更新是否生效」可观测。
- **失效面枚举**: 同函数四族逐一核查：(1) flowRules L81-89——isEmpty 棘轮 + 静默失败，立案；(2) degradeRules L91-99——同面；(3) sysRules L101-109——同面；(4) authRules L111-119——同面。四族共享同一缺陷形态，F3 同族清扫无遗漏（全仓无其他 `RuleManager.loadRules` 调用点）。
- **信心水平**: 确定
- **误报排除**: 「低频路径粗粒度同步合理」等误报条款不适用——这是热更新路径的语义缺陷，且该 bean 缺省启用（enableIfMissing=true），是限流熔断的唯一规则入口。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（live 面）——refreshConfig 全文逐族核对成立：四族同构 `isEmpty→skip`（空配置=跳过非清空，单向棘轮）+ `catch(Exception)→LOG.error`（单族解析失败静默保留旧规则，跨族半成功）；`loadRules` 全量替换语义选择正确（原条目表述准确）；sentinel-defaults.beans.xml `ioc:refresh-config-method` 热更新路径装配证实。触发场景（应急关闭限流不可在线操作、规则漂移不可见）为运维可遇确定性语义缺陷，P2 恰当

### [C8-008] SentinelFlowControlRunner.runAsync 同步异常路径：降级统计漏计 + CompletionStage 契约破坏

- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:67-99,164-168`
- **证据片段**:
  ```java
  // runAsync（异步版）：task.get() 同步抛出时
  CompletionStage<T> ret = task.get();       // L67 此处抛出 → 无 catch(非 BlockException)
  proceedCalled = true;
  ...                                        // 对比 whenComplete 分支（L72-76）有 Tracer.traceEntry
  } finally {
      if (!proceedCalled) { if (serviceEntry != null) serviceEntry.exit(1, args); }  // 只 exit，不 trace
      ContextUtil.exit();
  }
  // 对比同步版 run()（L164-168）：有 catch(Exception) → traceEntry + NopException.adapt
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程执行 `task.get()`（Supplier 同步段，如请求构建/序列化前置失败同步抛出）时的单线程路径；降级（熔断）规则统计依赖该异常被 `Tracer.traceEntry` 记入 entry 的异常计数。
- **触发时序**: T1：调用方传入的 Supplier 在 L67 同步抛出 RuntimeException（例：请求序列化前置错误、客户端构造失败）。T2：无通用 catch，`proceedCalled=false` → finally 中 entry 正常 exit（无泄漏，C3-007 面无恙），但异常**直接从 runAsync 同步抛出**——返回类型为 `CompletionStage` 的方法以同步抛出代替失败 stage，顶层调用（拦截器入口直调）收到裸异常而非可组合的失败 future。T3：该失败从未 `Tracer.traceEntry` → Sentinel 降级规则的异常统计漏计 → 该失败模式下熔断器少计/不跳闸，过载持续。
- **现状**: 同一方法内三个完成路径的统计行为不对称：whenComplete 失败路径有 trace（L72-76）、同步 `run()` 异常路径有 trace（L164-168）、runAsync 的 Supplier 同步抛出路径无 trace；异常包装行为也不对称（run() adapt 为 NopException，runAsync 同步路径裸抛）。
- **风险**: 特定失败模式（同步段抛出）下熔断保护静默失效；调用方按异步契约编写 then/exceptionally 链时收不到该异常。
- **建议**: runAsync 为 `task.get()` 增加通用 catch：`Tracer.traceEntry` + 返回 `FutureHelper.reject(...)`（对齐 `run()` 的 trace + adapt 行为）。
- **失效面枚举**: (1) runAsync·whenComplete 失败路径 L72-76——有 trace，正常；(2) runAsync·BlockException 路径 L83-86——转类型化错误 future，正常；(3) runAsync·Supplier 同步抛出 L67——无 trace + 裸抛，本条立案；(4) `run()` 异常路径 L164-168——有 trace + adapt，正常；(5) **边界注记（接 C4-003，不重复计数）**：若 task 返回永不完成的 future，`whenComplete` 永不执行 → entry 永久占用限流许可——该「无截止」根因归 C4-003（PollingRpcClient 轮询链），本维度仅登记许可占用这一下游后果。
- **信心水平**: 确定（代码不对称事实）；条件性（同步抛出路径的实际触发频率取决于上游 Supplier 实现）
- **误报排除**: 非原语使用问题；是同一适配类内异常处理不对称导致的统计漏计，属「降级路径」语义缺陷（C8 步骤 2 明确覆盖限流许可与降级统计）。
- **复核状态**: 未复核
- 复核状态: 驳回（同案并入 C3-008 主登记 P3，统计漏计面随同 diff）｜同站点同根——`task.get()` 同步抛出无 catch(BlockException 之外) 直接逃逸，与 C3-008 证据完全重合（SentinelFlowControlRunner:60-99，finally exit 配对完整）；C3-008 已主登记 P3 并复核保留（「量级低（supplier 同步抛错罕见），故 P3」）。本条增量面复核**成立并采纳**：三完成路径统计不对称回码证实（whenComplete 失败路径有 Tracer.traceEntry :72-76、同步 run() 有 catch(Exception)+trace+adapt :164-168、runAsync 同步逃逸路径无 catch 无 trace）→ 熔断统计漏计为真实后果，但触发频率与主登记同源（同一次同步抛出），不构成独立 P2——作为 C3-008 修复 diff 的强制要求注记（catch 后 `Tracer.traceEntry` + `FutureHelper.reject` 对齐 run() 形态），严重度随主登记 P3

### [C8-009] WeightedRandomLoadBalance 全零权重未设防：nextInt(0) 原始崩溃，排空语义不可用

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/WeightedRandomLoadBalance.java:26-28`
- **证据片段**:
  ```java
  int[] weights = getWeights(items);
  int ttlWeight = sum(weights);                 // 全零 → 0
  int rnd = MathHelper.random().nextInt(ttlWeight);   // nextInt(0) → IllegalArgumentException
  ```
- **严重程度**: P3
- **并发场景**: 任一请求线程执行 choose；权重是本模块唯一的流控旋钮（Nacos 权重 0 = 标准排空/摘流语义，经 `NacosNamingService.copyFromInstance` L289 原样回填）。
- **触发时序**: T1：运维按 Nacos 惯例将某服务全部实例权重置 0（整服务摘流，实例数 ≥2）。T2：下一请求进入 `WeightedRandomLoadBalance.choose` → `nextInt(0)` 抛裸 `IllegalArgumentException` → 整个选择路径崩溃。T3：调用方收到的是原始运行时异常而非 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 类型化错误——本应「有界降级为无实例可用」的排空操作变成请求面异常风暴。
- **现状**: 权重合计为零未设防；候选数为 1 时因 `LoadBalanceServerChooser.chooseFromCandidates` L41-42 短路不经过 LB，故仅多实例全零权重可触发。
- **风险**: 运维按平台惯例（Nacos 权重语义）执行摘流即触发；错误面为裸 IllegalArgumentException，不可归因。
- **建议**: ttlWeight≤0 时抛 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE` 类型化错误（或回退等权随机并 LOG.warn），对齐同族守卫。
- **失效面枚举**: 三个权重合计消费方逐一核查：(1) `WeightedRandomLoadBalance.choose` L26-28——无守卫，本条立案；(2) `LeastActiveLoadBalance` L95 `totalWeight > 0`——有守卫（回退等权随机），但零权重实例仍会被选中（无权重 0 摘除语义，随 C8-001 一并修复）；(3) `WeightedPartitionAssigner` L57-59 `ttlWeight == 0 → 1`——有守卫。F3 清扫：3 处中 2 处已设防，1 处漏。
- **信心水平**: 确定
- **误报排除**: 权重 0 不是畸形输入而是 Nacos 生态的标准运维语义（排空），不属「超出声明包络的输入」。
- **复核状态**: 未复核
- 复核状态: 驳回（同案并入 C1-006 主登记 P2）｜同站点同缺陷——`nextInt(ttlWeight)` 全零无守卫（WeightedRandomLoadBalance:26-28）与 C1-006 证据完全重合；C1-006 已主登记 P2 并复核保留，且 round2 三项扩展已被 C1-006 复核吸收（其复核状态行明文：「round2 扩展的负权重与小数权重截断 (int) inst.getWeight()（copyFromInstance:289）证实并入」；扩展点 1 WeightedPartitionAssigner `weight<=0→defaultWeight` 排流语义改写与本轮独立回码一致：assigner getWeight 守卫仅防崩溃不保语义）。本条原判 P3 低于主登记 P2，无增量证据，关闭并指向 C1-006；修复验收（ttlWeight≤0 回退 + Assigner 语义裁决 + 截断告警）以 C1-006 状态为准

### [C8-010] MasterServerChooser 无主降级路径产生裸 NPE——失败语义未类型化、不可归因

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/MasterServerChooser.java:39-48`；下游 `nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcClient.java:73-74`、`http/HttpRpcClientInstanceProvider.java:33-37`
- **证据片段**:
  ```java
  // MasterServerChooser.chooseFromCandidates
  String leaderId = leaderObserver.getLeaderId();
  if (StringHelper.isEmpty(leaderId)) return null;      // 无主窗口 → null
  ...
  return null;                                          // leader 不在实例列表 → null
  // ClusterRpcClient.callAsync
  ServiceInstance instance = serverChooser.chooseFromCandidates(instances, request);
  getRpcClient(instance, request).callAsync(...)        // instance=null → NPE
  ```
- **严重程度**: P3
- **并发场景**: 选主切换/选举空窗期（leaderObserver 暂无 leader）内全部请求线程；`ClusterRpcClient` 对 chooser 返回 null 无任何防御。
- **触发时序**: T1：leader 选举空窗（重启/脑裂恢复期），`getLeaderId()` 返回空。T2：请求进入 `chooseFromCandidates` → 返回 null → `getRpcClient(null, ...)` → `HttpRpcClientInstanceProvider.getRpcClientInstance` L35 `instance.getAddr()` 抛 NPE。T3：异步路径 NPE 使 stage 异常完成、同步路径直接抛出——调用方收到裸 NPE 而非 `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE`；且 NPE 不满足 `isAllowRetry`（仅 NopConnectException），空窗内全部主路由请求确定性失败，错误不可归因。
- **现状**: chooser 契约未声明「可否返回 null」，唯一返回 null 的实现与其唯一消费方组合出崩溃路径；空窗降级语义（应类型化报错并等待重试）缺失。
- **风险**: 每次选主切换窗口内全量请求失败且以 NPE 形态污染日志/指标；排障成本高。
- **建议**: `ClusterRpcClient.callAsync/call` 对 `chooseFromCandidates` 返回 null 显式抛/complete `ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE`；或 `MasterServerChooser` 无主时抛类型化异常。
- **失效面枚举**: `chooseFromCandidates` 三个实现逐一核查：(1) `LoadBalanceServerChooser` L37-45——空列表返回 null，但 `ClusterRpcClient` 在 L65-66/L99-101 已先行拦截空列表，实际不可达 null，无碍；(2) `MasterServerChooser`——可达 null，本条立案；(3) 测试桩实现——不属生产面。与 C3-001（callAsync 重试异步面）同站点不同失效面，边界已声明。
- **信心水平**: 确定
- **误报排除**: 非「检查-然后-放入」并发误报模板；是降级路径返回值契约缺失的行为缺陷，属 C8「过载/降档行为性缺陷」口径。
- **复核状态**: 未复核
- 复核状态: 驳回（同案并入 C3-003 主登记 P2）｜同站点同缺陷——MasterServerChooser 双 null 返回点（:40/:48）→ ClusterRpcClient:73-74 getRpcClient(null)→HttpRpcClientInstanceProvider:35 `instance.getAddr()` NPE，与 C3-003 证据完全重合且 C3-003 失效面枚举更全（含 IServerChooser.chooseServerAsync 消费面、契约 javadoc :29 原文、MasterFirstLoadBalance 对照）；C3-003 已主登记 P2 并复核保留（live 面：经缺省 chooser 装配可达，且为 C3-001 挂起链现实触发源）。本条原判 P3 低于主登记 P2，无增量证据，关闭并指向 C3-003

---

## 维度集外线索（overlay C5/C6/C7/C9）

### [nop-cluster-CL-016] [C9] ConsistentHashLoadBalance 每次选择重建整环——热路径分配放大 n×160

- **维度**: C9 并发高性能（热路径分配放大）
- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/ConsistentHashLoadBalance.java:32-49`
- **线索**: `choose` 每次调用对全部候选执行 `hashCandidate(item, replicaNumber=160, map)`，新建 `TreeMap` 填入 n×160 个条目后仅做一次 `tailMap` 查询。量化口径：100 实例 → 每次 RPC 选择分配并排序 16,000 个 TreeMap 条目，调用频率 = 每次一致性哈希路由请求；环结构对同一实例列表是纯函数，完全可按实例列表指纹缓存。建议按 C9 口径评估 JMH 基准（`hashCandidate` 单次成本 × n × QPS）后立项。
- **状态**: 未复核（C9 维度立案候选）

### [nop-cluster-CL-017] [C9] nopServerChooser_roundRobin 误绑 RandomLoadBalance——轮询公平调度语义静默丢失

- **维度**: C9（负载分布公平性）/ 装配缺陷
- **文件**: `nop-cluster/nop-rpc-cluster/src/main/resources/_vfs/nop/rpc/beans/rpc-cluster-defaults.beans.xml:68-70`
- **线索**:
  ```xml
  <bean id="nopServerChooser_roundRobin" parent="nopServerChooser_base">
      <property name="loadBalance" ref="nopLoadBalance_random"/>   <!-- 应为 nopLoadBalance_roundRobin -->
  </bean>
  ```
  选择 roundRobin 的部署实际获得随机负载均衡，轮询的均匀分发语义（`RoundRobinLoadBalance` 存在且已注册为 `nopLoadBalance_roundRobin`，load-balance-defaults.beans.xml:24-25）从未生效。用户可见行为缺陷，修复为低风险装配改动；顺带核查 `RoundRobinLoadBalance` 自身的 AtomicInteger 共享语义（多个 chooser 复用同一 bean 时轮询序列共享）是否符合预期。
- **状态**: 未复核（C9/配置缺陷立案候选）

---

## 维度复核结论（WI 4.2 独立复核员，2026-10-20）

- **方法**：逐条回码（BroadcastRpcClient、ClusterRpcClient、NacosNamingService、PartitionResolver、SentinelRuleConfig、SentinelFlowControlRunner、LB 四类 + beans 装配、FutureHelper.waitAll）+ 同单元 C1/C2/C3 已立案条目逐条比对（C1-004/006/007、C2-003、C3-001/002/003/005/006/007/008/010）+ 台账查重（cra-index WI 1.1–4.1）+ 降档口径核对。

### 逐条判定表

| 发现 | 初判 | 复核判定 | 关键复核证据 |
|------|------|---------|-------------|
| C8-001 | P2 | **保留 P2** | getActiveCount 恒 0 + javadoc 矛盾 + 全仓零活跃计数生产代码 + 测试同用恒 0 adapter（TestLoadBalance:41/61/130）回码成立；装配链证实 |
| C8-002 | P2 | **保留 P2** | 立即递归重试/零退避/扩散 remove/缺省 retryCount 2/2/1 回码成立；与 WI 2.3 C8-002（P1，OOM 轨迹）分档合理 |
| C8-003 | P2 | **保留 P2（休眠条件化，携回升条款）** | 扇出无上界回码成立；但载体全仓零接线（仅 abstract 模板 bean，独立 grep 复现）——原条目「缺省装配」表述修正，按 WI 3.3 渠道栈口径条件化注记，声明 broadcast proxy 即回升 |
| C8-004 | P2 | **驳回（并入 C3-002）** | 与 C3-002 三重合（allOf 首败即全败/不 cancel/buildResponse get(0)，同站点同 diff）；主登记已复核 P2 休眠条件化，C3-round2 已裁互引不重复计数 |
| C8-005 | P2 | **保留 P2（失效面收缩）** | 面(c) cleanupTasks=C3-006（P2 条件化已复核）、异常路径订阅泄漏=C3-005（P2 已复核）两面移交；保留独立面：instanceCache 裸 CHM 无淘汰（:46）+ 正常路径永不退订（:194-197）条件性无界；getServices 截断子注记保留 |
| C8-006 | P2 | **保留 P2** | 三条 return-null/不缓存路径 + round2 异常路径扩展逐点回码成立；javadoc 自相矛盾成立 |
| C8-007 | P2 | **保留 P2** | 四族 isEmpty 棘轮 + 解析失败仅 LOG.error 全文核对成立；热更新棘轮 + 跨族半成功成立 |
| C8-008 | P2 | **驳回（并入 C3-008，统计面随同 diff）** | 同步逃逸与 C3-008 完全重合（主登记 P3 已复核）；增量统计漏计面回码成立（whenComplete :72-76 有 trace、run() :164-168 有 trace+adapt、同步逃逸无 trace）但与主登记同源同频，作 C3-008 修复强制要求注记，不独立计 P2 |
| C8-009 | P3 | **驳回（并入 C1-006）** | 与 C1-006（P2 复核保留）同站点同缺陷；round2 三项扩展已被 C1-006 复核吸收；原判 P3 低于主登记 P2 |
| C8-010 | P3 | **驳回（并入 C3-003）** | 与 C3-003（P2 复核保留）同站点同缺陷，主登记失效面更全；原判 P3 低于主登记 P2 |
| C8-011（R2） | P3 | **保留 P3** | stop 注销无兜底回码成立（AutoRegistration:220-229 + NacosNamingService:139-149 newError 上抛）；ephemeral=false 条件面；feature 门控（nop.cluster.registration.enabled）现状标注补充；与 C3-010/C4-005 三面独立成立 |

### 降档口径核对

- 本维度全部为**长驻运行期/请求期行为面**，启动期降档零适用（与原文件口径一致）。
- **休眠面条件化（须 grep 证据，已附）**：C8-003——载体 BroadcastRpcProxyFactoryBean 全仓非测试零接线（仅 abstract 模板；`new BroadcastRpcClient` 唯一站点在 FactoryBean 内），与 C3-002 复核同款证据，携回升条款。
- **条件性无界（不降档但标注条件）**：C8-005 无界增长依赖键空间无界（`allowedServiceNames` 缺省空串非 `*`，需显式配置 + 网关转发形态），P2 为条件校准非降档；C8-011 持久 ghost 依赖 ephemeral=false 显式配置。
- 四条驳回均为**同案去重**（本单元 C1/C3 主登记在先且复核已确认），非误报驳回——缺陷本体全部成立，仅计数归属修正；C8-009/C8-010 原判 P3 低于对应主登记 P2，C8-004/C8-008 增量不足独立立案。

### 同案查重（台账 + 本单元）

- **WI 2.3 C8-005 令牌桶族**：无同案——nop-cluster 无自建令牌桶/限流器实现（Sentinel 适配为纯委托），流控面缺陷均为适配层语义面。
- **WI 3.4 C3-005 错误以成功交付族**（WI 3.1 C7-002/WI 3.3 C3-001 家族）：无同案——本维度各失败路径均显式上抛/异常完成（C8-004 整体失败可见、C8-010 NPE 可见），无「错误折叠为成功」形态；C8-008 为「同步异常逃逸异步签名」，方向相反，不属该族。
- **WI 2.3 C8-002 Gateway 重试放大族**：C8-002 同族相邻（重试无时间整形），分档独立成立（无 OOM 轨迹），单列。
- 本单元内四案双登记（C8-004→C3-002、C8-008→C3-008、C8-009→C1-006、C8-010→C3-003）+ C8-005 两面移交（C3-005/C3-006）为本次复核主要计数修正；原文件「边界声明」节未申报这五处交集，属初审边界申报遗漏（复核补正，不影响 C1/C3 侧任何判定）。

### 高风险条目子项复核说明

1. **C8-005（三面拆分）**：(a) 缓存无界+永不退订——本条保留（live：nacos 发现装配下每次 getInstances 首调即订阅钉存）；(b) cleanupTasks 慢任务阻塞通知线程——与 C3-006 同站点同失效面（:268-276 内联 task.run 回码一致），且 C3-006 复核已证 addCleanupTask 全仓零调用方（当前休眠）；(c) 异常路径订阅泄漏——C3-005 主登记（含 round2 NacosConfigService 兄弟站点扩展）。三面修复互不冲突，验收合并批处置。
2. **C8-008（三完成路径统计不对称）**：逐路径回码——(1) whenComplete 失败有 trace（:72-76）；(2) BlockException→reject（:83-86）；(3) run() catch(Exception)+trace+adapt（:164-168）；(4) runAsync Supplier 同步抛——无 catch 无 trace 裸逃逸（finally 仅 exit）。不对称事实成立；因触发与 C3-008 主登记同源（同一次 task.get() 抛出），并入主登记 P3，trace+reject 作为同 diff 强制要求。
3. **C8-009（round2 三扩展）**：扩展点 1（Assigner `weight<=0→100` 使排流语义在分区分配面失效）独立回码成立（WeightedPartitionAssigner.getWeight 尾部守卫）——该面对 nop-job 分区派发有实际后果，已随 C1-006 主登记（其复核状态行已吸收）；扩展点 2（`(int)` 截断 0.5→0）回码成立（copyFromInstance:289）；扩展点 3（触发前提弱化至任意排流型变更窗口）与 C1-001 共享 weight 字段写面一致，采纳。

## 最终保留项

| 最终严重度 | 条目 |
|-----------|------|
| P2×6 | C8-005（实例缓存无界+订阅永不退订，收缩后单面）、C8-007（Sentinel 规则单向棘轮）、C8-006（PartitionResolver 无负缓存）、C8-002（RPC 重试零退避）、C8-001（LeastActive 背压信号恒 0）、C8-003（广播扇出无上界，休眠条件化携回升条款） |
| P3×1 | C8-011（注销失败无兜底 ghost，feature-gated 条件面） |

统计：保留 7（P2×6 + P3×1，其中 C8-005 失效面收缩、C8-003 休眠条件化）/ 驳回 4（全部为同案并入：C8-004→C3-002 P2、C8-008→C3-008 P3、C8-009→C1-006 P2、C8-010→C3-003 P2）/ 降级 0。**本维度不新增 P1/P0**；「半个成功」判据明文列项与 C8-004 维持 P2 之间的张力随并入消解（C3-002 复核已按休眠面裁 P2 携回升条款——broadcast proxy 声明即回升 P1 由 C3-002 条款承载）。CL-016/017/019/020/021 线索复核确认成立；CL-017（roundRobin 误绑 random）经本轮独立回码证实（rpc-cluster-defaults.beans.xml:68-70 确绑 nopLoadBalance_random），建议随 C8-001 修复批一并处置装配勘误。
