# C8 深挖第 2 轮 — nop-cluster（WI 4.2）

- **审计人**: 独立深挖第 2 轮审计员（合并批 C1/C2/C3/C4/C8）
- **日期**: 2026-10-21
- **状态**: 深挖完成，未复核；与第 1 轮 C8（C8-001…C8-010）编号续接，不重复立案

## 深挖范围与方法

按派发口径，本轮对以下 C8 指定邻接面逐一重审（live code 为准，盲审纪律同第 1 轮）：

1. **Sentinel SphU/Entry 的其他语义面**（SentinelFlowControlRunner 逐行重走）：
   - **slot 链顺序/参数对称性**：`runAsync` 以 `asyncEntry(resource, resourceType, entryType, 1, args)` 创建（batchCount=1，args=bizKey 包装数组或 EMPTY_ARGS），完成路径 `exit(1, args)` 与 finally 路径 `exit(1, args)` 三处 batchCount/args **严格一致**（:63-64/:79/:91）；同步 `run()` 以 `entry(resource, type, entryType, args)`（缺省 batchCount=1）+ `exit(1, args)`（:172）同样一致——**负结果**，无参数错配导致的统计错位面。
   - **ContextUtil.enter 返回值忽略**（:47/:146）：Sentinel 对默认 context 名返回 null 的语义仅影响 caller 统计维度，entry 本身的 Node 统计不受影响——负结果（行为边界，非缺陷）。
   - **exit 跨线程面**：`whenComplete` 在任意完成线程调 `entry.exit`——Entry.exit 为 Sentinel 官方异步模式用法（线程安全），rt 统计跨线程为已知近似——负结果。
   - **cluster 模式面**：适配器仅本地 `SphU` 单机流控，无 Sentinel 集群流控（cluster client/token server）任何接线；多副本部署下"总配额 = N × 单机规则"的语义**未在任何 owner doc/javadoc 声明**（owner doc 零覆盖）——登记 CL-020。
   - **资源名基数**：代码注释自认"资源数目不能太多（超过 1K）"（:61/:156）但平台无任何强制或上限校验，Sentinel Node 统计只增不减——登记 CL-019（F4/C7 面）。
2. **WeightedRandom 权重表更新期间的读一致性（C1-006 相邻崩溃面）**：核验 `getWeights` 逐元素快照 int[] 与 sum/累加遍历使用**同一数组**（单请求内自洽，无撕裂和）；但快照逐元素读共享可变 `weight` 字段（写方为 Nacos 事件线程，C1-001），**部分更新窗口内一次 choose 的快照即可能读到全零组合**——C1-006 的触发前提从"运维将全部实例置 0"弱化为"任意一次权重变更事件的读取窗口 ∩ 排流型变更"，作为扩展 C8-009 并入（崩溃根因与守卫修复同 C1-006/C8-009，不新增编号）。
3. **DynamicLoadBalance 动态权重过载语义**：负结果——类内无任何权重表/过载状态（仅 `beanProvider.getBean(beanPrefix + name)` 每请求重解析委托），全模块 beans 无该类装配（未接线，公共 API 面）；自身无过载语义可言，"动态权重"实为所委托 bean 的属性。与 C1-round2 文件同结论，交叉引用不重复立案。
4. **订阅回调风暴的限流面**：`ServiceInfoListener.updateInstances` 对每次实例事件执行全量 LinkedHashMap 重建 + 排序 + ImmutableList 拷贝（O(n log n)），无事件合并/限流/去重；实例 flapping 风暴下重建风暴与 C3-006 的事件线程阻塞面叠加——登记 CL-021（C7/C8 线索）。

## 新增发现

### [C8-011] AutoRegistration.stop() 注销失败即上抛：持久注册模式下产生服务端永久 ghost instance，流量继续路由至已停止节点（摘除语义失效）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:220-229`；失败源 `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:139-149`
- **证据片段**:
  ```java
  @PreDestroy
  public void stop() {
      if (autoUpdateTimerFuture != null) autoUpdateTimerFuture.cancel(false);
      autoUpdateTimerFuture = null;
      if (instance == null) return;
      namingService.unregisterInstance(instance);   // NacosException → NopException 上抛，无 catch/重试
      instance = null;
  }
  ```
- **严重程度**: P3
- **并发场景**: 停机线程（@PreDestroy）× Nacos 服务端可用性（注销请求期的网络/服务端故障）；无进程内对手线程，属失败路径的降级语义缺陷（C8 口径：下游/依赖不可用时的有界降级缺失）。
- **触发时序**: T1 运维在 Nacos 集群抖动/网络分区窗口执行停机 → T2 stop() 调 `unregisterInstance` → NacosNamingService:146-148 捕获 NacosException 后 `throw newError(e)`（非 NacosException 则裸穿）→ 异常穿出 stop()，@PreDestroy 失败仅被容器记日志 → 服务端实例表残留本节点注册：ephemeral=true 时随 Nacos 客户端 shutdown 停止心跳约 30s 自愈；**ephemeral=false（持久注册，setEphemeral(false) 为受支持配置）时永久残留**——发现列表持续返回已死端点，全集群流量按 C1-001 面继续建连失败/超时，直至人工在 Nacos 控制台删除。
- **现状**: 注销为单次、无重试、无失败兜底；注销失败与成功在调用方（容器）不可区分地结束停机流程。与 C4-005（start 两步无回滚）、C3-010（stop 与刷新竞态）为 AutoRegistration 生命周期族的三个独立失效面，互不重叠。
- **风险**: 滚动重启常态叠加 Nacos 抖动即触发 ephemeral 自愈窗口（30s 级路由失败面）；持久注册部署（长连接/状态服务常用）下演变为需人工介入的永久 ghost——实例摘除这一发布/停机基本语义在最需要它的依赖故障窗口失效（与 C8-006 的"依赖故障期降级方向相反"同构）。
- **建议**: stop() 包 try/catch：失败记 error（带 serviceName/instanceId）后至少置进程级标志供健康端点暴露"注销未确认"；对注销增加有限重试（复用平台 globalTimer 单次延迟重试）或 javadoc 强约束 ephemeral=false 与 stop 失败的组合后果；NacosNamingService.unregisterInstance 对"客户端已 shutdown"类非 NacosException 转类型化错误。
- **失效面枚举**（F3：模块内全部注销/反注册站点逐一核对失败路径）: ① AutoRegistration.stop（本条，无兜底）；② NacosNamingService.ServiceInfoListener.unsubscribe（仅错误路径可达，见 C8-005 失效面(4)，订阅随客户端 shutdown 消亡，无 ghost 面）；③ NacosConfigService ConfigInfo remover（失败静默无告警，但监听器随 shutdown 消亡，边界）；④ EmptyNamingService（no-op，N/A）。同族仅 ① 失守。
- **信心水平**: 确定（失败传播链为代码级事实；持久残留后果以 ephemeral=false 配置为前提）
- **误报排除**: 非"停机窗口低频豁免"——后果是服务端持久状态污染而非进程内瞬态；非 C3-010/C4-005 重复（三个失效面机制独立：竞态/启动回滚/注销兜底）
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（feature-gated 条件面）——注销失败传播链回码成立（AutoRegistration:220-229 无 catch + NacosNamingService:139-149 `throw newError(e)` 上抛，非 NacosException 裸穿）；ephemeral=false 条件面与 ephemeral 30s 自愈面论证与代码一致；现状补充：AutoRegistration 由 `nop.cluster.registration.enabled` feature 门控（休眠非零接线）；与 C3-010（stop 竞态→C1-007 主登记）/C4-005（start 回滚）三面独立成立，维持 P3

## 扩展并入（不新增编号）

### 扩展 C8-009（三项：分区分配面静默改写零权重 + 小数权重截断 + 变更窗口崩溃前提弱化）

- **扩展点 1（语义面，最重要）**: `WeightedPartitionAssigner.getWeight:94-99` 将 `weight <= 0` 静默改写为 `defaultWeight=100`——第 1 轮失效面 (3) 据此判"有守卫"，但该守卫只防崩溃、**同时使 weight=0 的 Nacos 标准排流语义在分区分配面完全失效**：被摘流的实例仍按 100 权重获得完整分区份额（nop-job 等调用方据此继续把分区任务派给"已排空"实例）。权重旋钮的两个消费面（LB 尊重 0、Assigner 改写 0）语义相反，需统一裁决（Assigner 若要尊重 0，须回答"0 权重实例的分区给谁"——可能需要下限语义而非改写，修复属 owner 裁决项）。
- **扩展点 2（输入面）**: `NacosNamingService.copyFromInstance:289` 的 `(int) inst.getWeight()` 截断小数权重——合法的 Nacos 权重 0.5 静默变 0，无告警进入零权重池（LB 面放大 C1-006/C8-009 崩溃与排流范围，Assigner 面被扩展点 1 掩蔽为 100）。
- **扩展点 3（触发前提弱化）**: 权重快照逐元素读共享可变字段（C1-001），一次权重变更事件的部分更新窗口内，一次 choose 的快照即可呈现全零组合——C8-009 触发时序 T1 的"运维显式全量置 0"前提弱化为"任意排流型权重变更事件的读取窗口"，崩溃面概率上修，守卫修复（`ttlWeight<=0` 回退）不变。

### 扩展 C8-006（失效面清单修正：解析抛异常路径同样无负缓存）

- **站点**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java:95-98`
- **对原条目的修正**: 第 1 轮 C8-006 枚举的 return null 路径（空列表/未命中）之外，`namingService.getInstances(svcName)` **抛出**（NacosException→NopException 或底层 RuntimeException）时异常直接穿出 `resolvePartitions`：既不写正缓存也不写负缓存，也无 stale-cache 回退——javadoc:35 声明的"避免高频扫描时反复查询 NamingService"在依赖故障态同样失效，且相比空结果路径（返回 null 降级全表）更为激烈：每次调用直接异常打断调用方扫描。原条目修复建议（短 TTL 负缓存 / fail-closed）应同时覆盖该路径（异常路径回退 `cachedPartitions` 或负缓存化异常态）。

## 维度集外线索（overlay）

（编号续接 C1 文件 CL-018 之后，全维度统一流水）

### [nop-cluster-CL-019]（C7/F4）Sentinel 资源名基数无平台强制上界，Node 统计只增不减
- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:61-64,156-158`
- 线索：两处代码注释自认契约"资源名数目不能太多（超过 1K），超出几千请作为参数传入"，但 `entry.getResource()` 由上游 FlowControlEntry 任意构造，适配器无长度/基数校验；Sentinel 内部按资源名累积 Node 统计且不淘汰，长时运行中资源名基数失控即内存单调增长 + slot 链统计退化。建议：适配器入口对 resource 做告警阈值计数，并把契约写入 javadoc。**信心：很可能（Sentinel 内部不淘汰为公开行为，未逐版本核实）**。

### [nop-cluster-CL-020]（C8 语义声明缺口）Sentinel 适配仅本地单机流控，集群模式未接线且 per-node 语义未声明
- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:63,158`（仅 SphU 本地入口，全模块无 cluster client/token server 相关引用）
- 线索：多副本部署下限流阈值语义为 per-node（总吞吐 ≈ N × 单机阈值，熔断统计亦按节点独立），该语义未写入任何 javadoc/owner doc（owner doc 零覆盖）。运维按"服务级总配额"配置规则时会获得 N 倍实际放行。属声明缺口而非代码缺陷；建议至少在适配器 javadoc 声明当前为单机模式及集群模式的配置出口。**信心：确定（接线缺失为 grep 级事实）**。

### [nop-cluster-CL-021]（C7/C8）实例事件风暴无合并/限流：每事件全量 O(n log n) 重建
- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:235-266`
- 线索：`updateInstances` 对每次 InstancesChangeEvent 执行全量 LinkedHashMap 重建 + 排序 + ImmutableList 拷贝，无时间窗合并（coalescing）、无速率上限；实例 flapping（注册/心跳抖动风暴）下与 C3-006 的共享通知线程阻塞面叠加，放大全平台发现视图陈旧窗口。建议：按 serviceName 做尾延迟合并（合并窗口内只保留最新 hosts 列表重放一次）。**信心：确定（机制）；规模后果取决于 Nacos 推送频率**。

## 继续判定

本轮新增 1 条（C8-011 P3）+ 2 条扩展（C8-009 三项、C8-006 一项，均并入既有条目不新增计数），无 P0/P1。四个指定深挖方向全部闭合：Sentinel SphU/Entry 语义面经逐参数核对为负结果（产出 2 条声明/基数类 CL 线索）；权重表读一致性与 DynamicLoadBalance 过载语义收敛进 C1-001/C1-006/C8-009 与负结果；订阅回调风暴限流面登记 CL-021。第 1 轮 10 条的边界经本轮复核无同根扩散。**判定：终止信号**——全 P3 且深挖面收敛、既有条目边界稳定，无需第 3 轮。

## 复核结论（WI 4.2 独立复核员，2026-10-20）

- C8-011 **保留原级 P3**（状态行见条目）：注销失败传播链与 ephemeral 双分支后果回码成立；`nop.cluster.registration.enabled` feature 门控标注为现状补充（休眠非零接线，维持 P3）。
- 两项扩展并入复核通过：扩展 C8-009 三项（Assigner 改写零权重致排流语义在分区面失效、`(int)` 截断、触发前提弱化）逐项回码成立，已确认被 C1-006 复核吸收——C8-009 本体在主文件维度复核中裁定**并入 C1-006 主登记**（P2），扩展随母条目处置；扩展 C8-006（异常路径无负缓存无 stale 回退）回码成立，随 C8-006（保留 P2）处置。
- CL-019/020/021 复核确认成立（基数契约自认无强制、per-node 语义未声明、事件无合并——均为 grep/回码级事实）。
- 终止信号裁定认可：全 P3 且深挖面收敛，无需第 3 轮。
