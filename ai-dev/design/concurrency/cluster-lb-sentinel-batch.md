# WI 4.2 选主外生命周期/路由/Sentinel 束（R4-25，plan 2559）设计裁定

> Status: active
> Last Reviewed: 2026-11-11
> Source: `ai-dev/audits/2026-10/2026-10-21-0240-deep-audit-nop-cluster-concurrency/`（8 原样 + MA-09 部分覆盖 fresh-session 取证 @ 2026-11-11）；owner 裁决前置两项在本节落成（§五-3/§五-4 候选答案裁定，见裁定 C/F）

## 〇、owner 裁决前置（batch-proposal L317 前置门——本节即裁决记录）

- **裁决 R25-1（§五-3 MA-08 零权重语义）**：取**下限语义**——实例 weight≤0 一律按下限 weight=1 参与分配（+WARN 留痕原始值），不取「改写 100」（假保护即本缺陷根因）亦不取「拒绝」（拒绝使单坏节点打垮整个 partition 分配）。反向钉死#3（testZeroAndNegativeWeight）按此契约改写：0/-50 实例按 weight=1 的比例份额分配（非各得 limit=100）。**语义后果显式声明（R1-Major-4）**：weight≤0 不再表示「不接流量」——Nacos 控制台 weight=0 摘流后该实例仍按 weight=1 接随机 LB 流量；摘流须改用 enabled/healthy 等替代手段；WARN 留痕原始值为载体断言项（Exit Criteria）。
- **裁决 R25-3（C8-006 PartitionResolver fail 语义）**：取**typed 上抛形态**——异常路径不缓存（快速失败）、弃 round2 的「负缓存化异常态」与「stale 回退」两候选（stale 回退引入静默陈旧路由面；负缓存化异常会把瞬时故障固化为 5s 假空）。空列表/未命中 → 负缓存短 TTL（裁定 H）。

- **裁决 R25-2（§五-4 C8-001 活跃计数实现形态）**：取 **Dubbo beginCount/endCount 型 per-instance LongAdder**——`ServiceLoadBalanceAdapter` 维护 per-instance 活跃计数注册表，调用前后 begin/end，`LeastActiveLoadBalance` 选 min-active（并列 random）；弃「deprecated 降级声明」（LeastActive 为在册 opt-in 装配形态，假保护即缺陷）。CL-017（roundRobin 误绑 random）同 diff 勘正。

## 一、设计结论（九条裁定 A-I）

1. **裁定 A（MA-03 Nacos 订阅/缓存生命周期）**：`NacosNamingService` ①catch 拓宽至 Exception（订阅失败必 unsubscribe，C3-005 泄漏面）②instanceCache 加容量上界+LRU 淘汰（C8-005，复用 commons cache）③`getServicesOfServer(1,1000)` 分页完备化或显式子集声明。扩展站点 `NacosConfigService` 同型核对。护栏 C10-005（stub 接缝复用 R2-18 getNamingService 覆写形态）。

2. **裁定 B（MA-05 Sentinel 异步包络+统计对称）**：`SentinelFlowControlRunner` ①同步段 try/catch → `FutureHelper.reject`（C3-008）②`orTimeout` 兜底 exit（C3-007 永不完成泄漏面；超时时长可配）③finally 统一 `Tracer.traceEntry` exit（C8-008 三路径统计对称）。**反向钉#1 同 diff 改写**：testSupplierSyncThrowPropagatesSynchronously → 断言 failed-future + traceEntry 计入。护栏 C10-009。

3. **裁定 C（MA-08 零权重=裁决 R25-1 下限语义）**：`WeightedRandomLoadBalance`（:26-28 nextInt 零和崩溃）与 `WeightedPartitionAssigner`（:94-98 ≤0→100）统一改为 weight≤0 → max(1, weight) 下限 + WARN；`NacosNamingService.copyFromInstance:319` 截断改显式 long→int 饱和处理（R1 锚点漂移：现 :319）。**反向钉#3 同 diff 改写**（按 R25-1 契约断言比例份额）。护栏 C10-007。

4. **裁定 D（MA-09 chooser 类型化+同步路径守护）**：`MasterServerChooser`（:40-48 两处 return null）改抛类型化错误（审计建议 (a)——chooser 无实例即语义失败，不返回 null 让下游猜）；`ClusterRpcClient.call` 同步重试循环（:131-132）补 null 守卫（防御性，与 callAsync :80-83 同型）。护栏 C10-001（剩余半边）。

5. **裁定 E（C1-005 destroy/懒初始化复活；R1-m1 勘正）**：`NacosNamingService` 加 destroyed 标志——getNamingService/maintainService 双检含 destroyed 判定（destroy 后懒初始化 typed 拒绝）；`NacosConfigService` 为 **eager start()（:66-79）无双检可加**——落 use-after-stop 守卫 + publishConfig(:181)/getConfigInfo(:162) 裸用字段补 null/destroyed 守卫（audit 失效面③原口径）；remover 裸用段守卫。护栏 C10-005 用例 (1)。

6. **裁定 F（C2-002 锁外化；R1-Major-3 修订）**：`instanceCache.computeIfAbsent(serviceName, this::getServiceInfo)` 映射函数内两次远程调用——改「先查→未命中锁外装载→putIfAbsent 回写」三段式（bin 锁内零远程 I/O）；**putIfAbsent 败者与装载异常路径均 unsubscribe 自建 listener**（败者泄漏新变体预防；既有 subscribed-flag 逻辑随迁，catch 拓宽至 Exception 按裁定 A①）；C10-005 用例 (4) 断言并发首装后**存活订阅数 = 1** 且远程调用单次。R2-18 钉 TestNacosInstanceRebuildAndSnapshotGuard:119 反射读字段 `instanceCache`——**字段名保留**。护栏 C10-005。

7. **裁定 G（C8-001 活跃计数=裁决 R25-2 LongAdder 形态；R1-Major-2 三要素显式化）**：`ServiceLoadBalanceAdapter` per-instance LongAdder 注册表 + begin/end 包装。**(a) 挂接点**：`ClusterRpcClient.call` 同步段 try/finally begin/end；`callAsync` 在 `getRpcClient(...)` 返回 stage 上 `whenComplete` end（四路径全覆盖——error/timeout/cancel 均终结计数，防永久活跃泄漏）。**(b) 键 = instanceId 字符串**（ServiceInstance 在 C1-001 后事件更新时重建对象——按对象身份计数即刻孤儿化）。**(c) 装配**：adapter 注入 ClusterRpcProxyFactoryBean（新 bean 引用）。`LeastActiveLoadBalance` 改用真实活跃计数（min-active，并列 random 既有形态 :90-109 保持；`i > 0` sameWeight 子注记勘正）；`ServiceLoadBalanceAdapter:21-22 return 0` 恒 0 面移除；CL-017 beans 勘正（roundRobin 误绑 random 勘正）。**接线验证（rule 23）**：载体断言真实调用路径 active count 1→0 回落。护栏 C10-007。

8. **裁定 H（C8-006 PartitionResolver 负缓存，fail 语义=裁决 R25-3；R1-m3 键名落点）**：空列表/未命中 → 负缓存短 TTL——**可配键 `nop.cluster.partition-negative-ttl-ms`（缺省 5000，setter/@InjectValue 注入；正缓存 CACHE_TTL_MS 硬编码常量保持）**——命中期内直接返回空；异常路径 → typed 上抛（不缓存异常，快速失败，R25-3）；正缓存写仅在成功尾部（保持）；静态路径（assignedPartitions 空集/enableCluster off）不缓存。护栏 C10-008。

9. **裁定 I（C8-007 Sentinel 规则热更新对称化；R1-m4 可观测口径声明）**：`SentinelRuleConfig` 四族 isEmpty→skip 改「空规则集 = 显式清空该族」（热更新对称，去单向棘轮）+ 解析失败 WARN 计数保留旧集——**WARN 计数即本裁定的可观测承载**（audit「聚合上报（统一错误码+指标）」在无指标面的 sentinel leaf 以结构化 WARN+计数器达成等效可观测；若后续批引入指标面则升级）。**sentinel 测试零覆盖→本批建测试类**（载体断言 WARN 计数）。mass-clear 风险属对称热更新语义的既定取舍。护栏 C10-010。

## 二、载体表

| 工单 | 载体 | 模块叶 |
|---|---|---|
| MA-03+C1-005+C2-002 | `TestNacosLifecycleConcurrency`（destroy 拒复活/订阅失败退订/并发首装**单次远程+存活订阅数=1**/缓存淘汰） | nacos |
| MA-05 | `TestSentinelFlowControlRunner` 改写（反向钉#1：failed-future+traceEntry）+ orTimeout 兜底 + 三路径统计对称 | sentinel |
| MA-08 | `TestWeightedPartitionAssigner` 改写（反向钉#3：下限语义比例份额）+ `WeightedRandomLoadBalance` 零和守护钉 | core+nacos |
| MA-09 | `TestMasterServerChooser`（chooser typed 错误）+ 同步路径守护钉 | core+rpc-cluster |
| C8-001 | `TestLoadBalance` 扩展（可变活跃计数 stub：min-active 选择+并列 random+CL-017 勘正）+ **接线验证载体**（`TestClusterRpcRetryTermination` 扩展：真实调用路径 active count 1→0 回落——rule 23） | core+rpc-cluster |
| C8-006 | `TestPartitionResolver` 扩展（空/未命中负缓存 TTL + 异常 typed 上抛） | core |
| C8-007 | `TestSentinelRuleRefresh`（新：空集清空对称 + 解析失败保留旧集 + WARN 计数） | sentinel |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-cluster -am`（纯 in-JVM stub，无 docker）。
- owner doc 缺口声明沿用（module-groups.md:21 + CLUSTER-CC-001 锚点扩写；不新建 03-modules/nop-cluster.md——先例 plan 2496/2500；CL-020 Sentinel per-node 声明随裁定 I 收口）。
- 硬接缝：① NacosNamingService 四成员同文件（MA-03/C1-005/C2-002/MA-08 截断）单通道串行；**R2-18 钉反射读字段 `instanceCache` 名保留**（裁定 F）；② 反向钉#1/#3 同 diff 改写（裁定 B/C 契约）；③ R2-18 零回退钉（TestNacosInstanceRebuildAndSnapshotGuard/TestClusterRpcRetryTermination nullChosenInstanceFailsTyped 等）。
