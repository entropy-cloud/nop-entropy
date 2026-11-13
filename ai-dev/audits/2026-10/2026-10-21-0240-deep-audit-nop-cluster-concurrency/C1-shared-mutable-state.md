# C1 共享可变状态与可见性 — nop-cluster（WI 4.2）

- **审计人**: 独立盲审初审员（C1+C2 合并批）
- **日期**: 2026-10-21
- **状态**: 初审完成，未复核

## 审计范围与口径

- **范围**: 7 源码根，74 文件 / 5,577 行，逐文件审读（手写口径，`_` 前缀生成物不在立案面）：
  - `nop-cluster/nop-cluster-core/src/main/java`（54f/3,807）：naming/AutoRegistration、CachingNamingService、elector/AbstractLeaderElector + AbstractPollingLeaderElector、lb/impl 9 类 + chooser 3 + filter 5、PartitionResolver、discovery/ServiceInstance、health、assigner、resources
  - `nop-cluster/nop-rpc-cluster/src/main/java`（9f/646）：ClusterRpcClient、BroadcastRpcClient、ClusterRpcServiceInvoker、ClusterRpcProxyFactoryBean、http/* 3 类、*ProxyFactoryBean
  - `nop-cluster/nop-cluster-nacos/src/main/java`（4f/564）：NacosNamingService、ServiceInfo、NacosConfigService
  - `nop-cluster-sentinel`（2f）、`nop-hazelcast-core`（1f）、`nop-hazelcast-cache`（1f）、`nop-cluster-admin`（3f）
- **owner doc 零覆盖声明**: `docs-for-ai/01-repo-map/module-groups.md` 对本单元零契约覆盖（无 nop-cluster 专属契约条目），本维度契约以源码 javadoc 为准（`ILeaderElector`"每次发生leader选举都会返回一个新的CompletionStage对象"、`IDiscoveryClient`"返回的列表可以被修改"、`IRpcClientInstanceProvider`"管理所有到具体服务器的连接"等均已逐条对照）。
- **适配面薄预校准声明**: sentinel / hazelcast-core / hazelcast-cache / admin 四根自管并发原语为 0（grep 基线 C1a–C1c、C2a 全空证实）。已逐文件确认（SentinelFlowControlRunner entry/context 配对已含 plan 2395 修复注释；HazelcastCache 全委派 IMap；ClusterAdminBizModel 为空实现），**不凑数扩面**；hazelcast-cache computeIfAbsent×3 与 CachingNamingService:37 按委派面单列注记（见 C2 文件边界声明），不立案为自管原语缺陷。
- **平台设施面**: `LocalCache`（Caffeine 包装）、`GlobalExecutors`、`LifeCycleSupport`、`FutureHelper`、`MathHelper.random()`（DefaultThreadLocalRandom，线程安全）只审使用方式不重审实现。
- **降档口径**: 本模块全部为请求触达 + 长驻运行期面（服务发现/选主/负载均衡/RPC 客户端），启动期降档一般不适用；仅 ConfigService/HazelcastProvider 的纯启动装配点按低频口径降档。
- **生产消费面核实**: `CachingNamingService` 全仓库 grep 仅类定义、零装配（潜在 API 面）；`AbstractLeaderElector` 的生产子类在本模块外（`SysDaoLeaderElector`、`JdbcLeaderElector`），其 `checkElection` 实际执行 DB I/O 并调用 `onRestartElection`/`onElectionCompleted`，用于具体化基类契约竞态的触发链（子类文件本身不在本单元立案面）。

### 共享可变状态清单表（字段 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|---------|------|
| 1 | `NacosNamingService.namingService/namingMaintainService`（volatile） | getNamingService 双检块 / destroy() | 全部注册/发现/注销路径 | volatile + synchronized 双检 | 发布安全；**destroy 后复活竞态**（C1-005） |
| 2 | `NacosNamingService.instanceCache`（CHM） | computeIfAbsent(:175) | getInstances | CHM 原子性 | 原子性成立；**锁内远程 I/O**（C2-002）；条目无驱逐/退订路径（寿命=进程） |
| 3 | `ServiceInfo.serviceInstances`（不可变 List 引用） | ServiceInfoListener.updateInstances（持 listener 锁） | getInstances 全部消费方 | synchronized(ServiceInfo) | 引用替换原子；**元素对象被原地改写**（C1-001） |
| 4 | `ServiceInfoListener.instanceMap` | updateInstances / initInstances | 自身 | synchronized(listener) | 封闭正确；**init 快照可回退新事件态**（C1-003） |
| 5 | `ServiceInfo.listener` | getServiceInfo（发布前） | getListener | 无（非 volatile） | CHM put 前写入，安全发布，之后只读——良性 |
| 6 | `ServiceInstance` 全部字段（含 `_host`、`modifyIndex`） | Nacos 事件线程（copyFromInstance 原地改写）+ 业务方 addCleanupTask/setAttribute | 全部 LB/filter/invoker（见 C1-001 失效面） | **无**（仅 addCleanupTask synchronized） | **缺陷**（C1-001、C2-001） |
| 7 | `AbstractLeaderElector.leaderEpoch/electionPromise`（volatile） | onRestartElection/onElectionCompleted（synchronized）；doStop/onStop（**无锁**） | getLeaderEpoch/whenElectionCompleted 全部消费方 | volatile + 部分 synchronized | **锁纪律不一致 → stop 后状态复活**（C1-004、C2-003） |
| 8 | `AbstractLeaderElector.listeners`（COW） | add/remove/clear | 4 个遍历点 | CopyOnWriteArrayList | 遍历安全；与 doStop 的乱序问题归入 C1-004 |
| 9 | `AbstractPollingLeaderElector.scheduledExecutor` | doStart 惰性 init / setter | scheduleCheck（timer 线程） | 无 volatile；doStart 写先于 schedule（executor HB） | 发布安全；stop 后 pending check 不可取消（C1-004 组成部分） |
| 10 | `AutoRegistration.instance/autoUpdateTimerFuture` | start/stop（生命周期线程） | refreshRegistration（globalTimer 线程） | 无 volatile；timer 注册 HB | **stop 与 timer 回调无序**（C1-007） |
| 11 | `PartitionResolver.cachedPartitions/lastResolveTime`（均 volatile） | resolvePartitions 尾部两连写 | resolvePartitions TTL 读 | volatile 逐字段 | 两者非原子成对更新 → TTL 偏差，后果良性（不立案，清单留痕） |
| 12 | `RoundRobinLoadBalance.seq`（AtomicInteger） | choose | 自身 | AtomicInteger | `getAndIncrement`+`nonNegativeMod` 原子，良性 |
| 13 | `ClusterRpcClient.retryCount` / Invoker 同名字段 | setRetryCount（含 ClusterRpcProxyFactoryBean 动态更新路径） | callAsync/call | 无（int 读原子） | 可见性延迟=配置传播延迟，良性 |
| 14 | 各 filter/chooser 的 `enabled/staticRoute/zone/tags` 等配置字段 | IoC 装配期 | 请求热路径 | 无 | 装配期写、运行期只读，良性（未发现运行期热更新路径） |
| 15 | `WeightedRandomLoadBalance`/`LeastActiveLoadBalance` 经 adapter 读 `weight` | Nacos 事件线程（#6） | choose | 无 | 活跃共享读，一致性面随 C1-001；零权重崩溃边（C1-006） |
| 16 | `NacosConfigService.configService`/`timeout`/`group` | start/stop | getConfigInfo/publishConfig + ConfigInfo close lambda | 无 volatile | **stop 后 use-after-shutdown / NPE**（C1-005 失效面 3） |
| 17 | `HazelcastProvider.hazelcast` | init（PostConstruct） | getInstance bean 方法 | 无 | 装配期单点调用，良性 |

## 发现

### [C1-001] Nacos 事件线程原地改写共享 ServiceInstance 对象，请求线程撕裂读 + `_host` 永久缓存陈旧端点

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:249-255`、`nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/ServiceInstance.java:172-178`
- **证据片段**:
  ```java
  // NacosNamingService.ServiceInfoListener.updateInstances
  serviceInstance = old.getRight();
  // 如果instance的内容改变了
  if (!old.getLeft().equals(instance.toString())) {
      serviceInstance.setModifyIndex(serviceInstance.getModifyIndex() + 1);
      copyFromInstance(instance, serviceInstance);   // 原地改写共享对象：addr/port/weight/healthy/...
  }
  ```
  ```java
  // ServiceInstance.getHost —— 惰性缓存，永不失效
  public String getHost() {
      if (_host == null) {
          _host = getAddr() + ':' + getPort();
      }
      return _host;
  }
  ```
- **严重程度**: P1
- **并发场景**: Nacos 客户端 notifier 线程（InstancesChangeEvent 回调 → updateInstances）与 HTTP worker / globalWorker 线程（RPC 发现→过滤→负载均衡→建连）并发；写方仅持 listener 监视器，读方无任何同步。
- **触发时序**: T1 请求线程执行 `SpecificServiceInstanceFilter.filter` → `instance.getHost()`（ServiceInstance.java:174，此时 `_host==null`，读得 `oldAddr:oldPort` 并写入 `_host`）→ T2 notifier 线程进入 updateInstances:251 判定内容变化 → 执行 copyFromInstance 改写 `addr/port/weight/healthy` → 交叠后果：(a) T1 后续读 port/weight 与已读 addr 撕裂（非 volatile 字段无 happens-before）；(b) 该实例的 `_host` 已固化为旧端点，copyFromInstance 不清 `_host`，此后所有请求的 host 匹配（SpecificServiceInstanceFilter:69）与建连基准永久指向下线/迁移前的地址，直至实例对象被替换（仅当 instanceId 变化才重建）。变体时序：T1 `HttpRpcClientInstanceProvider.getRpcClientInstance` 读 `getAddr()`（新值）后 T2 改写 `port` → `getPort()`（旧值）→ 拼出 `newIp:oldPort` 错误 baseUrl。
- **现状**: 发现列表是不可变 List（引用安全），但元素 ServiceInstance 被 Nacos 事件线程跨线程原地改写；全部字段非 volatile、无锁、无防御性拷贝，`modifyIndex` 递增也非原子且对读者不可见。
- **风险**: 实例摘流/迁移/元数据变更窗口内，负载均衡按混合新旧权重选择、流量按撕裂或陈旧的 ip:port 建连；`_host` 缓存使陈旧端点在事件流停止后仍无限期存活——服务发现缓存的首要正确性承诺（端点可达性）被静默打破。
- **建议**: updateInstances 检测到内容变化时重建 ServiceInstance 并以新对象替换 List 成员（copy-on-write 元素语义，而非原地 set）；或最小修复：内容变化时同时置 `_host=null` 并将全部可变字段 volatile 化（写少读多，volatile 开销可接受）。重建对象方案同时消除撕裂读，优先。
- **失效面枚举**（F3：共享可变 ServiceInstance 的全部读者面，逐一核对状态）:
  1. `WeightedRandomLoadBalance:26-35`（getWeight 快照）——受影响（本案）
  2. `LeastActiveLoadBalance:71-91`（两遍读 weight：totalWeight 快照 vs 逐项活读）——受影响，权重漂移仅致回退均选，后果轻
  3. `HealthyServiceInstanceFilter:38`（isHealthy/isEnabled removeIf）——受影响（健康摘流延迟可见）
  4. `SpecificServiceInstanceFilter:69`（getHost，含 `_host` 永久缓存）——受影响，最重（本案主时序）
  5. `ZoneServiceInstanceFilter:41-43`（getMetadata/getClusterName）——受影响（zone 漂移）
  6. `TagServiceInstanceFilter:45`（getTags）——受影响（灰度路由漂移）
  7. `RouteServiceInstanceFilter:56-64`（getMetadata(META_VERSION)）——受影响（版本路由漂移）
  8. `HttpRpcClientInstanceProvider:31-34`（getAddr()+getPort() 两次读拼 baseUrl）——受影响（撕裂端点，本案变体时序）
  9. `MasterServerChooser.chooseFromCandidates:41` / `MasterFirstLoadBalance.chooseMaster:41`（getInstanceId）——instanceId 事件中不改写，安全
  10. `PartitionResolver:101`（sort by getInstanceId）——instanceId 不改写，安全
  11. `ServiceLoadBalanceAdapter:17-23`（getWeight/getActiveCount 恒 0）——受影响同 1/2
  12. `ClusterRpcClient.chooseFromCandidates` 消费链——经由 1-8 间接暴露
- **信心水平**: 机制确定（写方无同步、读方无同步均为代码事实）；`_host` 永久陈旧为确定；撕裂读触发频率很可能
- **误报排除**: 不属"无状态单例被并发调用"（这是有状态共享对象）；不是"ConcurrentHashMap 原语误用"类误报——问题在元素对象的原地可变性，容器原子性不提供元素级 happens-before
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现未修复（live 主路径：Nacos 发现装配即生效；失效面 12 项已逐一回码，两处行号漂移见维度复核结论）

### [C1-002] ServiceInstance 清理任务读取无锁：丢任务/CME → 实例下线清理静默丢失

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/ServiceInstance.java:289-300`（锁面立案于 C2-001，本条立案可见性与资源后果面）
- **证据片段**:
  ```java
  public synchronized void addCleanupTask(Runnable task) {   // 写：持 this 锁
      if (cleanupTasks == null)
          cleanupTasks = new ArrayList<>();
      cleanupTasks.add(task);
  }

  @JsonIgnore
  public List<Runnable> getCleanupTasks() {                  // 读：完全无锁
      if (cleanupTasks == null)
          return Collections.emptyList();
      return new ArrayList<>(cleanupTasks);
  }
  ```
- **严重程度**: P1
- **并发场景**: 业务/请求线程调用 `addCleanupTask`（如注册连接级清理回调）vs Nacos notifier 线程在实例移除事件中调用 `getCleanupTasks()`（NacosNamingService.java:269，且该调用点位于 listener 锁外）。
- **触发时序**: T1 请求线程进入 `addCleanupTask` 执行 `cleanupTasks.add(task)`（ArrayList 扩容中，`elementData` 半迁移）→ T2 notifier 线程执行 `new ArrayList<>(cleanupTasks)`（ArrayList.java:296）→ 交叠后果：拷贝得到含 null 元素的数组（随后 `task.run()` NPE）或漏掉新增任务；`cleanupTasks` 非 volatile，T2 还可能读到 null 而整批跳过。三类后果共同点：**清理任务丢失且无任何告警**。
- **现状**: 写侧 synchronized、读侧裸读+遍历拷贝，锁不对称；唯一消费点在移除事件的清理循环（该循环本意就是"实例消失时回收资源"）。
- **风险**: 清理任务恰是实例下线时的资源回收路径（F8 资源清理不对称族）；丢失即连接/句柄泄漏，且发生在集群拓扑变更的高频窗口，长时运行累积。
- **建议**: `getCleanupTasks` 加 synchronized（读少，代价可忽略），或改用 `CopyOnWriteArrayList` 并去掉字段级锁。
- **失效面枚举**: `cleanupTasks` 字段全仓库名仅 2 个触点：写 API `addCleanupTask`（ServiceInstance:289，模块内无生产调用方——为业务模块预留的公共 API 面）、读点 `NacosNamingService:269`（唯一消费者）。无其他兄弟字段/方法遗漏。
- **信心水平**: 确定（不对称结构为代码事实；具体后果取决于业务方是否使用该 API）
- **误报排除**: 不是"低频路径粗粒度 synchronized"类合理用法——这是同步缺失而非过度同步；也不是测试专用简化模型
- **复核状态**: 未复核
- 复核状态: 降级(P2，携回升条款)｜休眠公共 API 面——addCleanupTask 全仓 grep 仅类定义一处、零生产调用方（复核独立复现）；任一业务模块接线即回升 P1。锁不对称/丢任务机制本身回码成立

### [C1-003] 订阅初始化快照可回退已生效的监听事件态：陈旧实例列表无版本守护地覆盖新态

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:186-198`、`230-232`
- **证据片段**:
  ```java
  namingService.subscribe(serviceName, groupName, listener);   // ① 先订阅（事件开始投递）
  subscribed = true;
  List<Instance> instances = getNamingService().getAllInstances(serviceName); // ② 后查询快照
  listener.initInstances(instances);                            // ③ 无条件覆盖
  ```
  ```java
  synchronized void initInstances(List<Instance> instances) {
      updateInstances(instances);   // 与 onEvent 同一入口，无版本/时序比较，后到者胜
  }
  ```
- **严重程度**: P1
- **并发场景**: 首次发现某服务的请求线程（getServiceInfo，持 instanceCache bin 锁内执行）vs Nacos 客户端 notifier 线程（订阅生效后的 InstancesChangeEvent → updateInstances），两线程对同一 ServiceInfo 竞争写入。
- **触发时序**: T1 请求线程执行 ① 订阅 → 服务端发生实例变更 [B]（新增/摘除）→ T2 notifier 线程先处理变更事件，updateInstances([B]) 写入 ServiceInfo → T1 才执行 ② getAllInstances 返回变更前快照 [A]（查询与推送乱序）→ ③ initInstances([A]) 覆盖 [B] → 交叠后果：instanceCache 中该服务长期呈现陈旧列表 [A]，**直到下一次变更事件才被纠正**；若无后续变更，陈旧态无限期存活。
- **现状**: initInstances 与 onEvent 共用 updateInstances，采用"后到者胜"语义，但快照（②）与事件流（①③）之间没有任何 epoch/version/时序比较，快照旧于事件时发生状态回退。
- **风险**: 扩容场景新实例不可见（流量不疏散）、缩容场景已下线实例仍被调用（连接失败/超时），且故障持续到下一个无关变更事件，排查面大（F1 快照撕裂族变体：快照与事件流无序合并）。
- **建议**: initInstances 前重新拉取一次 getAllInstances 并与事件态做 modifyIndex/内容比较，或延迟 initInstances 至订阅首个事件落地（Nacos 订阅后会推送当前快照，②可整体省去）；最低成本方案：③ 改为"仅当 listener 尚未收到过事件时才写入"。
- **失效面枚举**: 同一写入口 `updateInstances` 的两个调用方：`initInstances`（:230，本案）与 `onEvent`（:214-219，顺序流，无问题）；`getServiceInfo` 中 `subscribe`/`getAllInstances` 顺序仅此一处，无兄弟站点。
- **信心水平**: 很可能（交错窗口依赖 Nacos 客户端查询/推送乱序，机制上成立且不可排除）
- **误报排除**: 非平台设施（Nacos SDK）内部问题——订阅与查询的编排顺序、以及无版本守护的合并语义均为本模块代码所有
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现未修复（live 面）。复核补强论证：乱序窗口不依赖 Nacos SDK 违反"holder 先更新后派发事件"语义——事件 B 只需落在 getAllInstances() 返回之后、initInstances(A) 执行之前，该窗口为本模块代码段自身所有，触发链具体成立

### [C1-004] 选主 stop 路径无锁：leaderEpoch/electionPromise 停止后复活 + 监听器乱序回调

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractLeaderElector.java:119-124`、`173-181`、`213-225`
- **证据片段**:
  ```java
  protected void doStop() {                    // 无锁（与 onElectionCompleted 的 synchronized 不对称）
      electionPromise.cancel(false);
      onStop();                                // 内部：遍历 listeners → leaderEpoch=null → cancel promise
      listeners.clear();
  }
  ...
  protected synchronized void onElectionCompleted(LeaderEpoch epoch) {
      this.leaderEpoch = epoch;                // 可在 doStop 之后把 null 改回非 null
      CompletableFuture<LeaderEpoch> promise = this.electionPromise;
      if(promise.isDone()){                    // 被 stop 取消后新建 promise 并完成
          promise = new CompletableFuture<>();
          this.electionPromise = promise;
      }
      promise.complete(epoch);
  }
  ```
- **严重程度**: P1
- **并发场景**: globalTimer 线程（checkElection 轮询中，生产子类 SysDaoLeaderElector/JdbcLeaderElector 的 checkElection 含 DB I/O，窗口为 DB 延迟量级）vs 生命周期线程（`LifeCycleSupport.stop()` → doStop）。
- **触发时序**: T1 timer 线程在 checkElection 内完成 DB 裁决，即将调用 onElectionCompleted → T2 生命周期线程执行 doStop：cancel promise、onStop()（监听器收到 onStop、leaderEpoch 置 null）、listeners.clear() → T1 进入 synchronized onElectionCompleted：`leaderEpoch = epoch`（停止后复活为"本节点是 leader"）、promise.isDone()==true → 新建 promise 并 complete(epoch)。交叠后果三连：(a) stop 完成后 `getLeaderEpoch()` 返回非 null、`isLeader()` 为 true；(b) `whenElectionCompleted()` 返回已完成的新 promise，等待者收到已停止节点的当选通知；(c) 若 T1 与 onStop 的监听器遍历交错，监听器先收 onStop 再收 becomeLeader/becomeFollower——生命周期事件乱序，监听器状态机（如"我已停止所以不再接管角色"假设）被打破。
- **现状**: 状态转移的三个写站点中两个有 synchronized 守护，doStop/onStop 完全无锁；stop 也不可撤销已在途的 pending `checkElection`（schedule 为 fire-and-forget，见 C2-003/CL-005），复活窗口在每次 stop 期间都存在。
- **风险**: 停机中的节点对外仍呈现 leader 身份（同进程组件读 getLeaderEpoch/isLeader 决策）；监听器乱序导致角色相关资源（如 leader 侧调度器）在停止后重新自启——选主语义违约，非 mere 数据竞争。
- **建议**: doStop 与 onStop 一并置于 synchronized(this)（或独立锁对象），stop 后置 destroyed 标志并在 onElectionCompleted 入口检查 `isStopping()` 直接 return；监听器回调同样纳入停止守护（回调前查 stopping 标志）。
- **失效面枚举**（F3：leaderEpoch/electionPromise 全部写站点）: `onRestartElection`（:165，有锁）、`onElectionCompleted`（:173，有锁，本案对手方）、`onStop`（:221-224，无锁，本案）、`doStop`（:121，无锁，本案）、`newElection`（:161，无锁——模块内无生产调用方，锁豁免但属同族不一致，见 C2-003）。读站点 `getLeaderEpoch`/`whenElectionCompleted`（volatile 读，安全）。生产子类 `SysDaoLeaderElector.checkElection`（模块外，仅作触发链证据）。
- **信心水平**: 确定（交错无需罕见时序：checkElection 的 DB I/O 窗口 × 2s 轮询 × 每次 stop）
- **误报排除**: 不是"stop 后进程即退出所以无害"——本类 allowRestart=true（LifeCycleSupport:107 stop 后回到 CREATED），且 stop 与进程退出之间有窗口；也不是监听器自身实现问题——乱序由基类锁纪律缺失造成
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现未修复（生产子类 SysDaoLeaderElector/JdbcLeaderElector 均为 src/main 生产代码，checkElection DB I/O 窗口证实；allowRestart=true 在 LifeCycleSupport:26 复核证实；三连后果 a/b/c 逐项回码成立）

### [C1-005] destroy 与懒初始化复活竞态：shutdown 后重建 Nacos 客户端并泄漏（3 处同族）

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:65-78`、`101-120`；`NacosConfigService.java:130-169`
- **证据片段**:
  ```java
  // destroy(): 关闭后置 null
  namingService.shutDown();
  ...
  namingService = null;
  // getNamingService(): 看到 null 会重新创建——destroy 之后也如此
  public NamingService getNamingService() {
      if (namingService == null) {
          synchronized (this) {
              if (namingService == null) {
                  namingService = NacosFactory.createNamingService(properties);
  ```
- **严重程度**: P2
- **并发场景**: 停机线程（@PreDestroy destroy()）vs 仍在处理请求的 worker 线程（getInstances → getServiceInfo → getNamingService；register/unregister 路径同理）。
- **触发时序**: T1 worker 线程进入 getNamingService 首检 `namingService == null` 为 false，继续向下 → T2 停机线程 destroy()：shutDown() + `namingService = null` → T1 走到 synchronized 块内二检 `namingService == null` 为 **true** → createNamingService 重建全新客户端 → 交叠后果：destroy 已执行完毕，重建的客户端无人再调用 shutDown（进程随后退出则靠 JVM 回收，长驻进程则真泄漏）；期间 register/getAllInstances 继续在"已宣判死亡"的 bean 上发起网络调用。
- **现状**: 懒初始化双检与 destroy 置 null 构成 check-then-act 缺口：无 destroyed 标志、destroy 不参与 synchronized(this) 互斥。
- **风险**: 关闭后资源复活 + 泄漏（F8 家族）；优雅停机期间发出的发现/注册请求落在复活客户端上，产生不可解释的幽灵连接与日志。
- **建议**: 增加 `volatile boolean destroyed`，getNamingService/getNamingMaintainService 二检后遇 destroyed 抛 NopException（fail-fast）；destroy 置 null 改为仅 shutdown 不置 null（复活路径消失，后续调用落在已 shutdown 实例上由 SDK 快速失败）。
- **失效面枚举**（F3：同构"shutdown+置 null vs 懒初始化"站点全清单）: ① `NacosNamingService.namingService`（本案）；② `NacosNamingService.namingMaintainService`（:80-93 + :112-119，同构，**同案修复**）；③ `NacosConfigService.configService`（stop():128-139 置 null + getConfigInfo 内 close lambda `if (configService != null) configService.removeListener(...)`（:164-166）与 publishConfig（:158）直接裸用——stop 后调用 NPE 或对已 shutdown 实例操作，同案修复）；④ `HazelcastProvider`（:34-47）无懒初始化、destroy 不置 null，**无此缺陷**（对照面）。
- **信心水平**: 确定（结构性竞态；触发限于停机窗口）
- **误报排除**: 停机窗口竞态不是"低频路径粗锁"豁免——它产生资源泄漏这一持续后果；双检锁本身（volatile + synchronized）正确，缺陷在 destroy 侧，非"双检缺 volatile"误报形态
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（三站点结构性竞态回码成立；与 WI 2.2 C1-009 JdbcConfigSource 为同族异模块关联，非重复，见维度复核结论同族清单）

### [C1-006] WeightedRandomLoadBalance 零权重总和未守护：全量摘流配置下每次选择抛 IllegalArgumentException

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/WeightedRandomLoadBalance.java:26-35`
- **证据片段**:
  ```java
  int[] weights = getWeights(items);        // 逐实例读活跃共享 weight（无一致性快照保障）
  int ttlWeight = sum(weights);
  int rnd = MathHelper.random().nextInt(ttlWeight);  // ttlWeight==0 → IllegalArgumentException
  ```
- **严重程度**: P2
- **并发场景**: 请求线程读共享 ServiceInstance.weight（由 Nacos 事件线程维护，见 C1-001）的聚合值；触发既可以是稳态配置（全部实例 weight=0），也可以是事件改写窗口的瞬态。
- **触发时序**: 运维对某服务全部实例执行 weight=0 摘流（Nacos 控制台合法操作，语义为"不接新流量"）→ 下一请求进入 choose → sum(weights)==0 → `nextInt(0)` 抛异常 → 交叠后果：该服务**所有** RPC 调用连续失败（比 weight=0 的预期语义"仅存量请求排空"严重得多）；瞬态变体：T1 在 getWeights 循环中读到混合新旧权重恰好总和为 0 的窗口。
- **现状**: 未守护；同族两处均有守护：`WeightedPartitionAssigner.assignWeighted:58`（`if(ttlWeight==0) ttlWeight=1`）与 `LeastActiveLoadBalance:85`（`!sameWeight && totalWeight > 0` 才走加权分支，否则均选回退）。
- **风险**: 摘流操作（发布/故障演练常规动作）直接放大为服务不可用；权重聚合值是跨线程活跃共享状态，瞬态触发无需全量配置。
- **建议**: 对齐兄弟实现：`ttlWeight<=0` 时回退 `RandomLoadBalance`/首实例选择；同时与 LeastActive 一样把守护语义写为注释。
- **失效面枚举**（F3：LB 家族权重聚合读点全清单）: `WeightedRandomLoadBalance:27-28`（无守护，本案）；`LeastActiveLoadBalance:76-85`（有 totalWeight>0 守护，且终态 `nextInt(leastCount)` leastCount≥1 恒安全）；`WeightedPartitionAssigner:57-59`（有守护）；`RoundRobinLoadBalance/RandomLoadBalance/FirstLoadBalance/MasterFirstLoadBalance/ConsistentHashLoadBalance`（不读权重，N/A）。
- **信心水平**: 确定
- **误报排除**: 非纯配置问题——聚合读点位于请求热路径且消费跨线程可变状态，守护缺失属共享状态复合读取缺陷；兄弟站点已证明本仓库将该边界面视为需守护的契约
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现未修复（nextInt(0/负值) IAE 面与两兄弟守护站点均回码证实；round2 扩展的负权重与小数权重截断 `(int) inst.getWeight()`（copyFromInstance:289）证实并入）

### [C1-007] AutoRegistration 停止与定时刷新无序：cancel(false) 后 update-after-unregister（当前被缺省实现掩蔽）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:210-229`
- **证据片段**:
  ```java
  protected void refreshRegistration() {      // globalTimer 线程
      if (instance != null) {                  // check
          namingService.updateInstance(instance);  // act——与 stop 无互斥
      }
  }
  public void stop() {                         // 生命周期线程
      if (autoUpdateTimerFuture != null)
          autoUpdateTimerFuture.cancel(false); // 不中断在途回调
      ...
      namingService.unregisterInstance(instance);
      instance = null;
  }
  ```
- **严重程度**: P2
- **并发场景**: globalTimer 线程（refreshRegistration 周期回调，回调体含网络调用窗口）vs 容器停机线程（stop()）。
- **触发时序**: T1 timer 线程进入 refreshRegistration 通过 `instance != null` 检查 → T2 停机线程 cancel(false)（不中断 T1）、unregisterInstance、instance=null → T1 继续执行 updateInstance(instance) → 交叠后果：注销之后再触发更新。**后果现状被掩蔽**：`INamingService.updateInstance` 为 default 空实现（INamingService.java:31-32），全仓库无任何覆写（F3 grep 证实），T1 实际执行 no-op；一旦未来任一 INamingService 实现提供真实更新语义（Nacos updateInstance 对不存在实例的语义依版本可能重挂元数据），该时序即产生 ghost instance。
- **现状**: check-then-act（instance 判空与使用之间无原子性）+ cancel(false) 语义错配（既不等待在途回调也不中断）；`instance`/`autoUpdateTimerFuture` 均 non-volatile（timer 注册 HB 覆盖读可见性，判空后失配是问题所在）。
- **风险**: 当前为零后果的潜伏结构缺陷；属 F2"半个成功"（stop 的注销与 timer 的更新无法定序），实现 updateInstance 时即转化为幽灵实例复活。
- **建议**: stop() 改为 `cancel(true)` 或先置 volatile `stopped` 标志、refreshRegistration 入口检查；或 stop 中注销前先 cancel 并 `ThreadHelper` 等待在途回调退出。
- **失效面枚举**: `instance` 字段写点 2（getServiceInstance:185、stop:228）、读点 2（refreshRegistration:211、stop:225）；`autoUpdateTimerFuture` 写点 2/读点 1。同族定时注销对齐面：`AbstractPollingLeaderElector`（无 future 保留，见 CL-005，形态不同单列）；模块内无其他 scheduleWithFixedDelay 站点。
- **信心水平**: 时序确定 / 后果当前被掩蔽（明确标注：潜伏缺陷，非现实故障）
- **误报排除**: 未按现实故障判 P1，如实降档并注明掩蔽原因；不属"配置字段运行期只读"豁免——timer 回调与 stop 是真实的双线程无序面
- **复核状态**: 未复核
- 复核状态: 降级(P3)｜潜伏缺陷当前零后果（updateInstance 生产链终为 no-op 复核证实：default 空实现 + NacosNamingService 未覆写；CachingNamingService 仅透传）+ 与 C3-010 同站点同失效面重复立案，本条为主登记、C3-010 并入去重

### [C1-008] CachingNamingService 同步/异步双路径缓存不连贯：async 旁路 put 可用旧数据回写覆盖新数据

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/CachingNamingService.java:36-54`
- **证据片段**:
  ```java
  public List<ServiceInstance> getInstances(String serviceName) {
      return cache.computeIfAbsent(serviceName, k -> service.getInstances(serviceName)); // 群护 + 原子装载
  }
  public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceName) {
      List<ServiceInstance> instances = cache.get(serviceName);
      if (instances != null)
          return FutureHelper.success(instances);
      return service.getInstancesAsync(serviceName).thenApply(ret -> {
          cache.put(serviceName, ret);       // check-then-act：无原子性，后完成者胜
          return ret;
      });
  }
  ```
- **严重程度**: P2
- **并发场景**: 多个 globalWorker/调用线程在同一 TTL 失效窗口并发走 getInstancesAsync；与走同步 getInstances 的线程交错。
- **触发时序**: T1、T2 同时 cache.get 未命中 → 各自发起远程装载 → T2 先返回较新快照 R2 并 put → T1 后返回较旧快照 R1 并 put 覆盖 → 交叠后果：缓存长期呈现 R1（比"谁先装载谁填充"更差——明确回退），直至 TTL 过期；同时两请求都打了远端（惊群，与同步路径的 computeIfAbsent 群护语义不一致）。
- **现状**: 同一缓存两个访问协议：同步路径原子装载（LocalCache→Caffeine `cache.get(key, mappingFunction)`），异步路径非原子 check-then-act；两条路径返回的又都是底层共享可变元素列表（与 C1-001 叠加）。**仓库内当前零装配**（grep 全仓库仅类定义），属公共 API 面潜伏缺陷。
- **风险**: 装配启用后：TTL 窗口惊群放大 Nacos 压力；旧数据回写在实例摘流窗口延长错误路由时间。
- **建议**: 异步路径收敛到同一原子装载原语（`LocalCache.computeIfAbsentAsync` 已存在于 io.nop.commons.cache.LocalCache:287），删除手写 check-then-act。
- **失效面枚举**: 缓存触点全清单：`getInstances`（:37，computeIfAbsent，安全面）、`getInstancesAsync`（:47-53，本案）、`registerInstance/unregisterInstance/updateInstance`（直通底层、不失效缓存——TTL 内自注册对 peer 不可见，属 TTL 语义声明范围，不立案仅留痕）。
- **信心水平**: 确定（时序可直接构造）
- **误报排除**: 非平台设施缺陷（LocalCache 自身原子性正确，缺陷在使用方式）；标注仓库内零装配以避免与现实故障混排
- **复核状态**: 未复核
- 复核状态: 降级(P3，携回升条款)｜零装配休眠面——CachingNamingService 全仓 grep 仅测试引用、无 beans 装配与生产实例化（复核独立复现）；装配启用即回升 P2。机制与时序回码成立（LocalCache.computeIfAbsentAsync:287 存在，修复锚点有效）

## 维度集外线索（overlay C5/C6/C7/C9）

### [nop-cluster-CL-001]（C9）RouteServiceInstanceFilter 热路径逐实例重复解析语义版本 + INFO 级日志
- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/filter/RouteServiceInstanceFilter.java:56-64`
- 线索：`removeIf` 内对每个实例执行 `Requirement.buildNPM(svcRoute)`（同一请求内同一 route 字符串重复解析 N 次）并 `LOG.info(...)` 每实例一条。路由过滤位于每次 RPC 发现热路径。建议：buildNPM 提升到 filter 调用首解析一次 + 日志降 debug。后续维度归属：性能/长时运行审计。

### [nop-cluster-CL-002]（C9）ConsistentHashLoadBalance 每请求重建全量哈希环
- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/ConsistentHashLoadBalance.java:33-42`
- 线索：每次 choose 新建 TreeMap 并按 items×replicaNumber（默认 160）填充——实例列表在 TTL 缓存内不变，环却每请求重建。建议：按列表引用/epoch 缓存环。归属：性能审计。

### [nop-cluster-CL-003]（C9）HttpRpcClientInstanceProvider 每调用新建 HttpRpcService，与接口 javadoc"管理所有到具体服务器的连接"不符
- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/http/HttpRpcClientInstanceProvider.java:30-35`、`IRpcClientInstanceProvider.java:16`
- 线索：每次 `getRpcClientInstance` 执行字符串拼接 + new HttpRpcService + new DefaultRpcUrlBuilder，无任何实例级缓存/池化；javadoc 声明的"管理连接"契约无实现支撑。当前 HttpRpcService 若为轻包装则仅是分配浪费（ClusterRpcClient 每调用经由此路径）。归属：性能/资源审计，需结合 HttpRpcService 实际权重定性。

### [nop-cluster-CL-004]（C9）NacosNamingService.getServices 硬编码单页 1000 上限静默截断
- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:167`
- 线索：`getServicesOfServer(1, 1000)` 无翻页，服务数 >1000 时静默丢弃尾部。低频管理路径，规模边界未在 javadoc 声明。归属：长时运行/规模包络审计。

## 维度复核结论

- **复核人**: CRA WI 4.2 独立复核员（合并批 C1+C2+C3）
- **复核日期**: 2026-10-20
- **方法**: 27 条逐条回码（live code，worktree 基线 120ff53d0d）+ cra-index 台账（WI 1.1–4.1）查重 + 降档口径核对（请求触达面不适用启动期降档；休眠面按零接线/零调用方条件化降档，均附 grep 证据）

| 发现 | 初判 | 复核判定 | 关键复核证据 |
|------|------|---------|-------------|
| C1-001 | P1 | **保留 P1** | updateInstances:251-254 原地改写、ServiceInstance 全字段非 volatile、getHost:172-178 惰性缓存无失效、toString:167-170 副作用——全部回码成立；失效面 12 项逐一核对成立 |
| C1-002 | P1 | **降级 P2（条件化回升）** | 机制成立；但 addCleanupTask 全仓 grep 仅类定义、零生产调用方→休眠公共 API 面，按零调用方口径条件化降档，接线即回升 P1 |
| C1-003 | P1 | **保留 P1** | subscribe→getAllInstances→initInstances:186-192 编排证实；乱序窗口为本模块代码段自身所有（事件落地于查询返回与 init 执行之间），不依赖 SDK 内部乱序假设，触发链具体 |
| C1-004 | P1 | **保留 P1** | doStop:119-124/onStop:213-225 无锁、onElectionCompleted:173-181 复活分支回码成立；allowRestart=true（LifeCycleSupport:26）证实；生产子类 DB I/O 窗口证实 |
| C1-005 | P2 | **保留 P2** | destroy:101-120 置 null + getNamingService:65-78 懒重建回码成立；三站点失效面成立 |
| C1-006 | P2 | **保留 P2** | nextInt(ttlWeight) 无守护、WeightedPartitionAssigner/LeastActive 两兄弟守护回码成立；round2 扩展（负权重、(int) 截断 :289）证实 |
| C1-007 | P2 | **降级 P3（主登记）** | 与 C3-010 同站点同失效面（refreshRegistration vs stop），合并为单案；后果当前被 default no-op updateInstance 掩蔽（复核证实 NacosNamingService 未覆写），潜伏零后果按 P3 |
| C1-008 | P2 | **降级 P3（条件化回升）** | 机制成立；CachingNamingService 全仓零装配（仅测试引用）→条件化降档，装配启用即回升 P2 |
| C1-009（R2） | P3 | **保留 P3** | seq 共享 + beans 单例装配（load-balance-defaults.beans.xml）回码成立；nonNegativeMod:1176 归一化证实 |
| C1-010（R2） | P3 | **保留 P3** | IDiscoveryClient:35/IServerChooser:36 契约文本与 immutableList:264/Collections.emptyList()/List.of() 三违约站点回码成立 |

**行号漂移注记**（不改变站点与判定）: C1-001 失效面 #4 SpecificServiceInstanceFilter getHost 实为 :28（原文 :69）；#3 HealthyServiceInstanceFilter removeIf 实为 :27（原文 :38）。同站点证据核验无误。

**降档口径核对**: 本维度 8 条主发现中无一适用启动期降档（全部请求触达/长驻运行期面）；C1-002/C1-008 按休眠面条件化降档，C1-007 按潜伏零后果降档——均附 grep/回码证据。

## 最终保留项

| 最终严重度 | 条目 |
|-----------|------|
| P1×3 | C1-001（原地改写+_host 陈旧端点）、C1-003（订阅快照回退）、C1-004（选主 stop 复活） |
| P2×3 | C1-005（destroy 复活，3 站点）、C1-006（零权重未守护）、C1-002（cleanupTasks 丢任务，条件化 P2） |
| P3×4 | C1-007（AutoRegistration 停止无序，主登记，吸收 C3-010）、C1-008（CachingNamingService 双路径，条件化 P3）、C1-009（共享 seq）、C1-010（契约矛盾） |

统计：保留原级 6 / 降级 2 / 并入去重 1（C3-010→C1-007，计入 C3 侧）/ 驳回 0。CL 线索（CL-001~004、CL-018）复核确认成立，其中 CL-003 与 C3 文件 CL-008 为同站点重复登记（HttpRpcClientInstanceProvider 每调用新建），修复批应合并处置。
