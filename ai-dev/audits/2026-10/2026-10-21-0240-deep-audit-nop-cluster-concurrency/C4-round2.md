# C4 深挖第 2 轮 — nop-cluster（WI 4.2）

- **审计人**: 独立深挖第 2 轮审计员（合并批 C1/C2/C3/C4/C8）
- **日期**: 2026-10-21
- **状态**: 深挖完成，未复核；与第 1 轮 C4（C4-001…C4-004）编号续接，不重复立案

## 深挖范围与方法

按派发口径，本轮对以下 C4 指定邻接面逐一重审（live code 为准，盲审纪律同第 1 轮）：

1. **elector 之外的其他 schedule 站点（穷举复核）**：以基线 C4a/C4b 为底册全仓重走——模块内全部调度站点共 4 处：`AbstractPollingLeaderElector`（:19/:22/:28，executeOn 卸载正确）、`AutoRegistration`（:205-206，executeOn 卸载正确）、`ClusterRpcProxyFactoryBean:43` 与 `ClusterRpcServiceInvoker:68`（裸 globalTimer，C4-003 已立案）。**无第 1 轮清单之外的新站点**；Sentinel/admin/hazelcast 四根零调度站点（逐文件确认）。
2. **LifeCycleSupport 交互的其他面**：
   - `stop()` 对 `doStop` 异常吞咽（LOG.error + ignore，LifeCycleSupport:100-110）——使用面核验：`AbstractLeaderElector.doStop` 的三步（promise.cancel/onStop/listeners.clear）均可安全部分失败（COW clear 幂等、cancel 幂等），**负结果**。
   - `doStart` 失败后的状态机：`AbstractPollingLeaderElector.doStart` 仅一步 schedule，失败即未启动，无半启动残留；对照 `AutoRegistration`（非 LifeCycleSupport 子类，@PostConstruct 直挂）的**两步获取无回滚缺口立案为 C4-005**。
   - stop→start 复用（allowRestart=true）的回路复活面已由 C4-001 完整覆盖（含 LifeCycleSupport:107-108 状态复位证据），本轮复核无新增面。
3. **globalTimer 其余占用点穷举**：4 个注入点已全部立案或核验（见 1）；`PollingRpcClient` 链为模块外消费方，其模块内接线（ClusterRpcClient:143-150）已随 C4-003 立案。**穷举闭合，无遗漏占用点**。

## 新增发现

### [C4-005] AutoRegistration.start() 两步获取无失败回滚：注册成功后定时链建立失败即产生无人注销、无刷新的残留注册（F8 资源生命周期不对称）

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/AutoRegistration.java:189-208`
- **证据片段**:
  ```java
  @PostConstruct
  public void start() {
      ... // 版本校验（抛错发生在任何资源获取前，无残留）
      namingService.registerInstance(getServiceInstance());        // 步骤①：注册（外部副作用）
      if (autoUpdate && autoUpdateInterval != null && ...) {
          autoUpdateTimerFuture = GlobalExecutors.globalTimer()
                  .executeOn(GlobalExecutors.globalWorker())
                  .scheduleWithFixedDelay(this::refreshRegistration, ...);  // 步骤②：可抛 RejectedExecutionException
      }
  }   // ① 成功 + ② 失败 → start() 抛出 → 无任何 unregisterInstance 回滚
  ```
- **严重程度**: P3
- **并发场景**: 启动线程（@PostConstruct）× globalWorker 池/全局 timer 状态（步骤② 的 RejectedExecutionException 触发条件：worker 队列满 10000 且 30 线程忙、或执行器已 shutdown——如容器装配期间与他方 shutdown 动作交叠的极端窗口）。
- **触发时序**: T1 启动线程执行步骤① `registerInstance` 成功（实例已进入 Nacos 服务端实例表）→ T2 同线程执行步骤② `scheduleWithFixedDelay` 抛 RejectedExecutionException → start() 异常上抛，IoC 装配失败路径无 catch/rollback → 交叠后果：实例保持注册态但**无刷新定时链**——weight/ enabled / metadata 永不随配置演进（refreshRegistration 不再运行），且 stop() 仅在 @PreDestroy 被调用时才注销；若装配失败导致进程退出，ephemeral=true 时靠 Nacos 心跳超时自愈（约 30s 级），**ephemeral=false（持久注册，setEphemeral(false) 为受支持配置）时残留直到人工清理**。
- **现状**: start() 是模块内唯一"多步外部资源获取 + 零回滚"的装配方法（对照：NacosConfigService.start 单步获取、stop 对称；HazelcastProvider init/destroy 对称；两个 ProxyFactoryBean.init 无外部资源）。触发条件极端（启动期 worker 饱和/执行器 shutdown），按共享前缀第 4 条标注理论风险成分。
- **风险**: 持久注册模式下产生需人工干预的 ghost instance（发现列表持续返回已半残实例，流量继续路由）；ephemeral 模式下为自愈的有界窗口。与 C4-001（stop 后复活）、C3-010（stop 竞态）共同构成 AutoRegistration 生命周期治理缺口族，但失效机制独立（启动失败路径，前两条为停止路径）。
- **建议**: 步骤②失败时 catch 后回滚步骤①（`namingService.unregisterInstance(instance); instance=null;` 再 rethrow）；或调换顺序（先建定时链——refreshRegistration 对 `instance==null` 已有守卫——再注册，使失败残留仅为一条空转定时链，stop 有 cancel 句柄可回收）。
- **失效面枚举**（F3："多步外部获取 + 失败回滚"装配方法全清单）: ① AutoRegistration.start（本条，唯一两步无回滚）；② NacosConfigService.start:87-97（单步 createConfigService，失败即无资源，对称）；③ AbstractPollingLeaderElector.doStart:17-23（单步 schedule，失败即未启动，对称）；④ ClusterRpcProxyFactoryBean.init/ClusterRpcServiceInvoker.init（无外部资源获取）；⑤ HazelcastProvider.init（单步，destroy 对称）。同族仅 ① 失守。
- **信心水平**: 结构确定 / 触发为理论风险（极端窗口，未具体化到可复现实测——如实标注）
- **误报排除**: 非"低频路径粗粒度同步合理"类豁免——这是失败路径的清理对称性缺口（F8 定向检查项），修复成本一行级；非 C3-010 重复（那是 stop 与在途刷新的竞态，本条是 start 内部两步的失败回滚缺失）
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现未修复（feature-gated 条件面）——两步获取无回滚回码成立（AutoRegistration:202 registerInstance → :205-206 scheduleWithFixedDelay，②失败路径无 unregister 回滚）；触发为启动期 worker 饱和/执行器 shutdown 极端窗口，「理论风险成分」标注符合共享前缀第 4 条口径；AutoRegistration 由 `nop.cluster.registration.enabled` feature 门控（rpc-cluster-defaults.beans.xml:8-9）——休眠非零接线，维持 P3 不再降档；与 C3-010（stop 竞态，C1-007 主登记）/C8-011（注销无兜底）三面独立成立

## 维度集外线索（overlay）

（本轮三个指定深挖方向中两个为穷举负结果，无新增 CL；统一流水编号由 C1/C8 文件占用）

## 继续判定

本轮新增 1 条（C4-005 P3，标注理论风险成分），无 P0/P1。三个指定深挖方向全部闭合：elector 外 schedule 站点穷举复核与基线一致（4 处，全部已立案或核验为正确卸载）；LifeCycleSupport 交互面除 C4-005 外均为可复核负结果；globalTimer 占用点穷举闭合。第 1 轮 C4-001/002/003/004 覆盖的站点边界经本轮复核无扩散。**判定：终止信号**——全 P3 且各深挖方向均已穷尽收敛，无需第 3 轮。

## 复核结论（WI 4.2 独立复核员，2026-10-20）

- C4-005 **保留原级 P3**（状态行见条目）：机制回码成立、启动期极端窗口的「理论风险成分」标注符合口径；`nop.cluster.registration.enabled` feature 门控标注为现状补充（休眠非零接线，不适用零调用方条件化降档——bean 定义真实存在且 feature 开关即激活）。
- 本轮三个穷举方向的负结果抽核通过：schedule 站点 4 处清单与本轮复核独立 grep 一致（elector/AutoRegistration 正确 executeOn 卸载、两个 rpc 站点裸 timer 已由 C4-003 立案）；LifeCycleSupport stop 吞咽面的「doStop 三步均可安全部分失败」论证与 AbstractLeaderElector:120-124 回码一致。
- 终止信号裁定认可：全 P3 且深挖面收敛，无需第 3 轮。
- 边界修正记录：本轮「继续判定」所述「globalTimer 占用点穷举闭合」经复核维持；但 CL-024（WI 2.1 移交 rpc-cluster 池累计面）的闭环结论在本轮复核中独立再证实（rpc-cluster 零自建池形态，每请求包装对象非池化累计）。
