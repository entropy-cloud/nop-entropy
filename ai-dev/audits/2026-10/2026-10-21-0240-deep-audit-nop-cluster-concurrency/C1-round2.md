# C1 深挖第 2 轮 — nop-cluster（WI 4.2）

- **审计人**: 独立深挖第 2 轮审计员（合并批 C1/C2/C3/C4/C8）
- **日期**: 2026-10-21
- **状态**: 深挖完成，未复核；与第 1 轮 C1（C1-001…C1-008）编号续接，不重复立案

## 深挖范围与方法

按派发口径，本轮在通读第 1 轮全部既有发现后，对以下 C1 指定邻接面逐一重审（live code 为准，盲审纪律同第 1 轮：仅允许 `ai-dev/skills/concurrency-resource-audit-prompt.md`、第 1 轮五文件与 `_tmp/cra-baseline-nop-cluster.txt`）：

1. **lb/impl 9 类与 chooser/filter 的选择状态**（逐文件全读）：
   - `RoundRobinLoadBalance`：回绕面核验为**负结果**——`seq.getAndIncrement()`（AtomicInteger，非 AtomicLong）在 `Integer.MAX_VALUE` 处回绕为 `MIN_VALUE`，`MathHelper.nonNegativeMod`（MathHelper.java:1176-1182，`r<0 则 r+=mod`）正确归一化，回绕不产生负下标越界；`items.size()==0` 的 `x % 0` 由上游 chooser 空列表守卫（LoadBalanceServerChooser:38-39、ClusterRpcClient:65/99）拦截。**共享序列面立案为 C1-009**。
   - `ConsistentHashLoadBalance`：CL-016 相邻的"环重建竞态"核验为**收敛进 C1-001**——环为每请求从每请求私有候选副本（ServiceServerChooser.filterInstances 恒 `new ArrayList`）重建的纯函数，无跨请求持久环状态可竞争；唯一竞态输入是元素字段（addr/host）被 Nacos 事件线程原地改写，即 C1-001 已立案面。`replicaNumber` volatile、运行期无写者（无 refresh 装配），良性。
   - `DynamicLoadBalance`：核验为**负结果**——类内无任何权重表/动态状态（仅 `beanProvider.getBean(beanPrefix + name)` 每请求重解析），全模块 beans 装配 grep 无该类 bean 定义（未接线，公共 API 面）；`name`/`beanPrefix`/`beanProvider` 为装配期写、运行期只读（无 refresh-config-method 装配），无 C1 立案面。C8 侧"动态权重过载语义"同此负结论（自身无过载语义，全权委托所解析 bean）。
   - `LeastActiveLoadBalance`：窗口算术复核——`i=(startIndex+currentCursor)%n`（:66，P1-4.4-C2 修复在场）无越界；`leastIndexs[leastCount++]`（:80）上界 `max=min(n,10)` 与循环次数严格一致，无溢出；`i>0` sameWeight 判定缺陷已在 C8-001 子注记登记，不重复。
   - `WeightedRandomLoadBalance`：逐元素快照 int[] 自洽（sum 与累加遍历同一数组，无撕裂和）；末位兜底 `items.get(n-1)` 保证 fall-through 不返回 null；崩溃面归 C1-006（本轮扩展见下）。
   - `FirstLoadBalance`/`RandomLoadBalance`/`MasterFirstLoadBalance`/`ServiceLoadBalanceAdapter`/`WeightedPartitionAssigner`：无状态或装配期常量，`WeightedPartitionAssigner.getWeight` 的 `weight<=0→defaultWeight` 语义面归 C8（扩展 C8-009）。
2. **AutoRegistration 注册去重面**：Nacos 服务端按 service+ip:port 幂等覆盖注册，`start()` 重复调用产生的新 ServiceInstance 对象仅替换引用，无客户端去重缺口；start 两步获取（register→schedule）的失败回滚缺口立案于 **C4-005**（C4 文件，线程/生命周期交互归属），本维度不重复计数。
3. **ServiceInstance 其余字段**：`attrs`（CHM 自洽）、`metadata`/`tags`（copyFromInstance 每次以**新建 TreeMap/Set 整体替换引用**，从不原地 mutate——读者持旧引用得旧快照，安全）、`modifyIndex`（long 非原子，已含 C1-001 面）。**扩展 C1-001 两项**：`toString()` 副作用写 `_host`；第 1 轮失效面 #9/#10 "instanceId 事件中不改写"结论修正（copyFromInstance:287 确实改写，但 map key 等值约束使写入为同值写，无害）。`getCleanupTasks`/`cleanupTasks` 已由 C1-002/C2-001 立案。
4. **PartitionResolver（CL-013 相邻可见性面）**：`cachedPartitions`/`lastResolveTime` volatile 双写之外，核验并发读面仅 TTL check-then-return（并发双解析幂等，良性）；`assignedPartitions` 装配期写；`getMyRange` 纯函数。**无新立案面**；解析抛异常路径（getInstances 抛 NacosException 直穿调用方）作为 C8-006 扩展并入 C8 文件。

## 新增发现

### [C1-009] RoundRobinLoadBalance 单例 bean 的 AtomicInteger seq 被全部 chooser/服务共享：按服务轮询公平性不成立（回绕面核验为安全）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/RoundRobinLoadBalance.java:17-25`；装配 `nop-cluster/nop-rpc-cluster/src/main/resources/_vfs/nop/rpc/beans/load-balance-defaults.beans.xml:24-25`
- **证据片段**:
  ```java
  public class RoundRobinLoadBalance<T, R> implements ILoadBalance<T, R> {
      private final AtomicInteger seq = new AtomicInteger(0);
      @Override
      public T choose(List<T> items, R request) {
          int idx = seq.getAndIncrement();
          idx = MathHelper.nonNegativeMod(idx, items.size());
          return items.get(idx);
      }
  }
  ```
  ```xml
  <bean id="nopLoadBalance_roundRobin" class="io.nop.cluster.lb.impl.RoundRobinLoadBalance"/>
  ```
- **严重程度**: P3
- **并发场景**: 所有引用 `nopLoadBalance_roundRobin` 单例 bean 的 chooser（跨服务、跨请求线程）并发调用同一 `seq`；轮询序号是全局单调序列而非每服务独立序列。
- **触发时序**: T1 服务 S1（2 实例）的请求在全局 seq=0 取号 → idx=0 → 实例 0；T2 服务 S2（3 实例）的请求占用 seq=1,2,3；T3 S1 后续请求恰总落在偶数号（与另一单一服务 chooser 锁步交替时必然如此）→ `0,4,8,…` 对 2 取模恒为 0 → S1 的全部流量确定性地压在实例 0 上。轮询语义退化为"全局发号器 + 各服务取模"，任一服务的分布由**其他服务的调用相位**决定。
- **现状**: CL-017 已立案 roundRobin chooser 误绑 random 的装配缺陷，并显式预留"顺带核查 RoundRobinLoadBalance 自身 AtomicInteger 共享语义"——本条为该预留项的裁定。共享发号器在并发下原子（无竞态、无丢号），缺陷是**语义粒度**：轮询公平性未按 serviceName 隔离。
- **风险**: 多服务共享该 bean 的部署中，个别服务的流量可能长期偏斜到单实例（相位锁步时 100% 偏斜），与"轮询均匀分发"的用户预期相悖；偏斜进一步放大单实例过载（与 C8-001 背压信号缺失叠加）。当前因 CL-017 的误绑，该 bean 经缺省 chooser 不可达，暴露面限于直接注入方。
- **建议**: 若要求按服务公平，需将发号粒度改为 per-serviceName（`ConcurrentHashMap<String, AtomicInteger>`，注意与实例下线的清理配对）；或将共享行为写入 javadoc 声明为已知边界。修复 CL-017 装配时应同步裁决本条。
- **失效面枚举**（F3：lb/impl 全部有状态实现逐一核对）: `RoundRobinLoadBalance.seq`（本条，唯一跨请求可变状态）；`ConsistentHashLoadBalance.replicaNumber`（volatile、装配期写，无状态选择）；`LeastActiveLoadBalance.maxCandidateCount`（装配期写）；其余 6 类无实例状态。同族共享计数器无第二处（grep `AtomicInteger|AtomicLong` 模块内仅此一处）。
- **信心水平**: 确定（偏斜序列可逐号推演）
- **误报排除**: 非"AtomicInteger 原语使用"误报——原子性成立，立案点是共享粒度这一复合语义；亦非 CL-017 重复（那是装配绑错目标 bean，本条是 bean 自身语义，两案修复需协同但不重叠）
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（受 CL-017 误绑掩蔽，暴露面限于直接注入方；共享 seq + beans 单例装配回码成立，nonNegativeMod 归一化证实）

### [C1-010] 发现/选择 API 契约声明"返回的列表可修改"，实现返回不可变列表或共享空单例：第三方按契约修改即 UnsupportedOperationException（含空列表分支返回全局单例）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/IDiscoveryClient.java:35`（契约原文）、`nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:174-176,264-265`、`nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/ServiceServerChooser.java:52-54`、`nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/IServerChooser.java:35-37`（契约原文）
- **证据片段**:
  ```java
  // IDiscoveryClient.java:35 —— @return A List of ServiceInstance. 返回的列表可以被修改
  List<ServiceInstance> getInstances(String serviceName);
  // NacosNamingService.java:265 —— 实际返回 Guava 不可变拷贝
  serviceInfo.setServiceInstances(CollectionHelper.immutableList(...));
  // IServerChooser.java:36 —— 每次都返回一个新的List，返回的结果列表允许被修改
  // ServiceServerChooser.java:53-54 —— 空分支返回共享不可变单例
  if (instances.isEmpty())
      return Collections.emptyList();
  ```
- **严重程度**: P3
- **并发场景**: 潜在触发面为任何按 javadoc 契约对返回列表执行写操作的消费方线程（模块外 SPI 消费者）；仓内全部消费方恰好只读或先拷贝，故当前无活跃 UOE。
- **触发时序**: T1 第三方 `INamingService` 消费者按 IDiscoveryClient javadoc 调 `getInstances(...).removeIf(...)` 做本地摘除 → NacosNamingService 返回的 ImmutableList 抛 `UnsupportedOperationException`；变体：T1 对 `IServerChooser.getServers` 空结果分支的返回值执行 add → `Collections.emptyList()` 共享单例同样抛 UOE（且该单例是全局共享对象，若某实现返回可变包装则会污染所有调用方——当前为纯不可变，仅契约违约）。
- **现状**: 两处接口契约（IDiscoveryClient:35、IServerChooser:36-37）明文允许修改，三个实现站点违反：NacosNamingService（immutableList）、ServiceServerChooser 空分支（Collections.emptyList()）、EmptyNamingService（List.of()，同族）。不可变返回本身与 C1-001 的"重建元素替换"修复方向一致（是安全特性），缺陷在**契约文本与实现相矛盾**，且矛盾是静默的（javadoc 层）。
- **风险**: SPI 消费者按文档编写防御性修改逻辑时在生产环境抛出未类型化 UOE；或实现者将来为"修契约"改回可变内部列表，反而重新引入 C1-001/C8-005 的共享可变状态面——契约歧义本身是双向风险源。
- **建议**: 以实现现状为准修契约：IDiscoveryClient/IServerChooser javadoc 改为"返回只读列表，调用方不得修改；需修改请自行拷贝"（一处文档修改覆盖两接口三实现）；同时确认 CachingNamingService 缓存语义（缓存同一不可变列表）与新契约一致。不建议改为每次拷贝（发现热路径分配放大）。
- **失效面枚举**（F3："契约允许可变 vs 返回不可变/共享"站点全清单）: ① IDiscoveryClient.getInstances ↔ NacosNamingService:175（immutableList，违约）；② IServerChooser.getServers ↔ ServiceServerChooser:53-54（Collections.emptyList()，违约）；③ 同接口 ↔ MasterServerChooser:30/35（ArrayList 拷贝，合规）；④ ServiceServerChooser 非空分支 :64/:66（new ArrayList，合规）；⑤ EmptyNamingService（List.of()，违约同 ①，同案修复）；⑥ CachingNamingService（缓存 ① 的返回值，随 ① 联动）。
- **信心水平**: 确定（契约文本与返回类型为代码级事实）
- **误报排除**: 不属"有界声明存在的缓存"等豁免——这是接口契约与其实现的双向矛盾；非 C1-001 重复（C1-001 立案元素对象被写线程原地改写，本条立案读侧契约文本违约，修复方向一致但失效面不同）
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（两接口契约原文与三违约站点回码成立，含 EmptyNamingService List.of() 同族）

## 扩展并入（不新增编号）

### 扩展 C1-001（两项证据增强 + 一项失效面修正）

- **扩展点 1（`toString()` 副作用写 `_host`）**: `ServiceInstance.toString():167-170` 调用 `getHost()`——任何将实例对象写入日志/监控的路径（如 `AutoRegistration.refreshRegistration:215` 的 warn 参数、`LOG.debug` 的实参求值）都会触发 `_host` 惰性缓存写入。原条目触发时序的 T1 不必是 `SpecificServiceInstanceFilter`，**任意日志点**都可在 copyFromInstance 改写 addr/port 之前固化陈旧端点。建议修复（重建对象替换）天然消除该面。
- **扩展点 2（第 1 轮失效面 #9/#10 结论修正）**: 第 1 轮 C1-001 失效面称"`MasterServerChooser:41` / `PartitionResolver:101`（getInstanceId）——instanceId 事件中不改写，安全"。核对 `NacosNamingService.copyFromInstance:287`：`ret.setInstanceId(inst.getInstanceId())` **确实改写 instanceId**；但因 updateInstances 以 instanceId 为 map key，仅当新旧 id 相等才会走 copyFromInstance 分支，故实际写入为**同值写**（非 volatile 写同值，对读者无可见性后果）。修正结论：仍安全，但理由是"key 等值约束"而非"不改写"；若未来 updateInstances 键策略变化（如改用 ip:port 为键），该字段立即升为活跃撕裂面——登记为修复时的回归注意点。

### 扩展 C1-006（负权重与小数权重截断同根崩溃面）

- `WeightedRandomLoadBalance:28` 的 `nextInt(ttlWeight)` 对 **ttlWeight<0**（存在负权重实例）同样抛 IllegalArgumentException，与 ttlWeight==0 同根同修（守卫条件应写 `<=0`）；产生异常权重的现实输入有两条：① Nacos API 层可提交负 weight（控制台校验可绕过）；② `NacosNamingService.copyFromInstance:289` 的 `(int) inst.getWeight()` **截断小数权重**——合法的 Nacos 权重 0.5 被静默置 0，无告警地进入零权重池，放大 C1-006 触发概率。修复建议维持原条目（`ttlWeight<=0` 回退）并增补：`copyFromInstance` 对非整数权重按 `Math.round` 或告警处理。

## 维度集外线索（overlay）

（编号续接第 1 轮 CL-017 之后，全维度统一流水）

### [nop-cluster-CL-018]（C1/C6 watch）LeaderEpoch.getExpireAt 泄漏可变 java.sql.Timestamp
- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/LeaderEpoch.java:26,38-40`
- 线索：`LeaderEpoch` 字段声明 final（不可变引用语义），但 `expireAt` 为可变 `Timestamp`，getter 直接返回内部引用；该对象经 volatile `leaderEpoch` 与 `electionPromise.complete()` 跨线程广泛共享，任何消费方 `setTime()` 即全局可见污染。仓内 grep 无 `getExpireAt` 消费方（租约判断在模块外子类），登记 watch：模块外子类接入时建议改返回 `new Timestamp(getTime())` 拷贝或将字段改 `long`。**信心：确定（机制）**。

## 继续判定

本轮新增 2 条（C1-009 P3、C1-010 P3），无 P0/P1；两项扩展并入既有条目不新增计数。指定深挖面（lb/impl 9 类、chooser/filter 选择状态、AutoRegistration 去重、ServiceInstance 全字段、PartitionResolver 相邻面）均已得出立案或可复核的负结果，邻接发现全部收敛进第 1 轮条目或 C4/C8 文件（C4-005、扩展 C8-006）。**判定：终止信号**——全 P3 且有收敛证据，无需第 3 轮。

## 复核结论（WI 4.2 独立复核员，2026-10-20）

- C1-009/C1-010 均**保留原级 P3**（状态行见各条目）。
- 两项扩展并入复核通过：扩展 C1-001（toString 副作用 :167-170、copyFromInstance:287 instanceId 同值写论证）与扩展 C1-006（负权重面、(int) 截断 :289）均回码证实，随母条目处置。
- CL-018（LeaderEpoch.getExpireAt 泄漏可变 Timestamp）复核确认成立，维持 watch（仓内无 getExpireAt 消费方）。
- 本轮负结果（RoundRobin 回绕安全、ConsistentHash 无持久环状态、DynamicLoadBalance 未接线、LeastActive 窗口算术）抽核通过：nonNegativeMod（MathHelper:1176）与 beans 装配面独立复现一致。
- 终止信号裁定认可：全 P3 且深挖面收敛，无需第 3 轮。
