# C3 深挖第 2 轮 — nop-cluster（WI 4.2）

- **审计人**: 独立深挖第 2 轮审计员（合并批 C1/C2/C3/C4/C8）
- **日期**: 2026-10-21
- **状态**: 深挖完成，未复核；与第 1 轮 C3（C3-001…C3-011）编号续接，不重复立案

## 深挖范围与方法

按派发口径，本轮对以下 C3 指定邻接面逐一重审（live code 为准，盲审纪律同第 1 轮）：

1. **BroadcastRpcClient 扇出取消传播（C3-002 已立案半个成功——取消面独立评估）**：逐分支核对后裁定**取消面不独立成立新案**——waitAll 失败路径 `cancellable` 不触发已由 C3-002 触发时序与 C8-004 失效面显式覆盖（两处互引）；waitAnySuccess 成功路径取消收尾在场（:80-84）；`cancelToken.appendOnCancel(cancellable::cancel)` 注册先于腿发射（:66-68 先于 :70-75），且平台 `Cancellable`（nop-commons lang/impl/Cancellable.java）已具备 CAS 幂等 cancel + appendOnCancel 立即补执行（已取消态注册立即回调）语义，取消时序面无缺口。**但发射循环的中途同步失败面未被既有条目覆盖**——立案为 C3-012。
2. **ClusterRpcServiceInvoker 链**：`invokeAsync` 前置校验（isAllowedService → reject）与 `getRpcService` 每调用新建（CL-012 已立案分配面）；首跳同步异常经 thenCompose 机制捕获为失败 future（复核确认 C3-008 失效面(4) 结论）；HttpRpcService 源码核验（nop-rpc-http，模块外对照）`callAsync` 仅透传 cancelToken、**无 ContextProvider/currentContext 捕获**——C3 §4 上下文传播检查项在本链为**负结果**：ClusterRpcClient:62 捕获的 `ctx` 仅用于 `isCallExpired()`（显式捕获、无 ThreadLocal 跨线程依赖），无串号/null 面。
3. **AbstractPollingLeaderElector 轮询回路异步面**：复核确认两生产子类均"尾部重调度"纪律（执行完才排下一跳）→ **同实例上两轮 checkElection 不可能重叠**（周期即最小间隔），轮询回路无并发执行面；异步面缺口均已被 C4-001（stop 复活/双回路）、C4-004（断链无监督）、CL-011（重调度契约无守护）覆盖，本轮无新立案。
4. **AutoRegistration 回调异常**：`refreshRegistration` catch(Exception) + warn 后链由 scheduleWithFixedDelay 续排（业务异常不断链，链级失败归 C4-004）；stop 路径竞态归 C3-010/C1-007；start 版本校验抛错发生在任何资源获取之前（无清理缺口）。**负结果**。F8 层的 start 两步获取无回滚缺口立案于 C4-005（C4 文件），本维度不重复。

## 新增发现

### [C3-012] BroadcastRpcClient 腿发射循环中途同步异常：已发射腿成为不可取消、结果无处投递的孤儿请求（发射路径半个成功）

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/BroadcastRpcClient.java:70-75`（发射循环）、`:86`（thenCompose 异常边界）
- **证据片段**:
  ```java
  List<CompletionStage<ApiResponse<?>>> futures = new ArrayList<>(servers.size());
  for (ServiceInstance server : servers) {
      CompletionStage<ApiResponse<?>> future = clientProvider.getRpcClientInstance(server)
              .callAsync(serviceMethod, request, cancellable);   // 第 k 腿同步抛出时
      futures.add(future);
  }   // ← 异常穿出循环 → 穿出 callServersAsync → 由外层 thenCompose 捕获为整体失败
  ```
- **严重程度**: P3（理论风险成分：同步抛出点依赖 provider/底层实现，未在仓内构造出必现链——按共享前缀第 4 条标注"理论风险，未具体化"并降档）
- **并发场景**: 调用方线程在 thenCompose lambda 内同步执行发射循环；前 k-1 条腿已发射到各自实例（异步在途），第 k 腿 `getRpcClientInstance` 或 `callAsync` 同步抛出（provider 实现、请求前置校验等）。
- **触发时序**: T1 调用方发起 5 实例广播 → 循环发射至第 3 腿时 `callAsync` 同步抛 RuntimeException → 异常穿出循环，外层 thenCompose 将整体 future 异常完成 → T2 调用方收到失败并按广播失败语义处理（重试/告警）→ 交叠后果：前 2 条腿**照常执行完成**，其响应无任何消费点（futures 局部列表随异常丢弃）、`cancellable.cancel()` 在该路径**从不执行**（仅 waitAnySuccess 成功分支有取消收尾）——已发射腿成为对服务端的幽灵写操作，与 C3-009 的幽灵请求同族但发生在发射路径。
- **现状**: 发射循环无 per-leg 异常隔离（无 try/catch、无已发射腿的失败回滚），"部分发射"状态对调用方完全不可见；waitAll/waitAnySuccess 两条聚合路径的失败语义（C3-002/C8-004）均以"全部腿已发射"为前提，发射中断路径未被任何条目覆盖。
- **风险**: 非幂等广播操作在发射中断时产生部分执行且不可对账；因同步抛出点在仓内模块均为轻量构造（HttpRpcClientInstanceProvider 仅字符串拼接 + new），现实触发依赖第三方 IRpcClientInstanceProvider/IRpcService 实现抛错，故降档 P3。
- **建议**: 发射循环包 per-leg try/catch：捕获后先 `cancellable.cancel()` 中止已发射腿，再 `FutureHelper.reject(NopException.adapt(e))` 返回（与 :61-63 空列表分支的失败形态对齐）；或收集已发射 futures 后统一走 waitAll/waitAnySuccess 聚合路径（把发射失败也转化为一个失败 future 加入聚合）。
- **失效面枚举**（F3：模块内"循环发射 + 无 per-leg 隔离"站点全清单）: ① BroadcastRpcClient.callServersAsync:70-75（本条）；② ClusterRpcClient.callAsync 的重试为**串行**逐跳（无并发发射面，异常面归 C3-001）；③ PollingRpcClient 单腿轮询（nop-rpc-core，模块外，链内 catch 覆盖——C4-004 失效面(3) 已核）；④ FutureHelper.waitAnySuccess 内部循环（基础设施，回调仅 complete，不可抛）。同族仅 ① 失守。
- **信心水平**: 机制确定 / 触发条件依赖模块外实现（理论风险，未具体化——如实标注）
- **误报排除**: 非 C3-002（聚合路径的半个成功）重复——本条是**发射路径**的中断面，既有条目均以"发射完成后"为前提；非误报校准豁免项（生产 RPC 主路径，非测试代码）
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜理论风险标注恰当（发射循环 :74-77 无 per-leg 隔离回码成立；同步抛出点依赖模块外实现，符合"未具体化降 P2 以下"口径——本条初判已 P3，维持）

## 扩展并入（不新增编号）

### 扩展 C3-005（同根兄弟站点：NacosConfigService 配置监听器注册后取值失败无清理——第 1 轮失效面(3) 结论修正）

- **站点**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosConfigService.java:148-168`
- **证据**: `getConfigAndSignListener(dataId, group, timeout, nacosListener)`（:162）为 Nacos 客户端"先注册监听器、后取值"的复合操作；取值抛 `NacosException` → :167-168 仅 `throw newError(e)` → 已注册的 `nacosListener` **无任何清理路径**（remover 闭包交付在 `ConfigInfo` 中，异常路径构造不出 ConfigInfo）。
- **对原条目的修正**: 第 1 轮 C3-005 失效面 (3) 曾裁定 NacosConfigService"stop() 后 remover 静默 no-op——无泄漏，登记为边界"，该结论仅覆盖 remover 执行时点面；**注册后取值失败的泄漏面**在同站点独立存在且未登记。与 C3-005（naming subscribe 泄漏）同根：`catch (NacosException)` 分类不备 + 注册/注销配对在异常分类不备时破缺（F8）。
- **后果**: 周期性配置拉取失败（Nacos 抖动窗口）下每次失败净增一个残留监听器，配置推送恢复后残留监听器继续接收并执行 `listener.onUpdateConfig`（重复回调/重复热更新）；修复应与 C3-005 同案（异常分类放宽为 Exception + subscribe/register 后失败路径统一回滚）。

## 维度集外线索（overlay）

（本轮指定深挖面经评估均收敛进既有条目或负结果，无新增 CL；C3 §4 上下文传播检查项经 HttpRpcService 源码核验为负结果——不设 CL。统一流水编号由 C1/C8 文件占用）

## 继续判定

本轮新增 1 条（C3-012 P3，标注理论风险）+ 1 条扩展（C3-005 失效面修正），无 P0/P1。四个指定深挖方向全部得出立案或可复核负结果：Broadcast 取消面收敛进 C3-002/C8-004（互引不重复计数）；ClusterRpcServiceInvoker 链与上下文传播为负结果；轮询回路异步面由尾部重调度纪律证明无重叠面（既有 C4-001/004/CL-011 覆盖其余缺口）；AutoRegistration 回调异常被既有 try/catch + 链续排覆盖。**判定：终止信号**——全 P3 且深挖面收敛，无需第 3 轮。

## 复核结论（WI 4.2 独立复核员，2026-10-20）

- C3-012 **保留原级 P3**（状态行见条目）；"理论风险，未具体化"的降档标注符合共享前缀第 4 条口径。
- 扩展 C3-005（NacosConfigService 配置监听器注册后取值失败无清理）复核通过：模块侧"注册后无回滚路径"回码成立（getConfigInfo:162-168 异常路径构造不出 ConfigInfo），与 C3-005 同案修复采纳。
- 四个指定深挖方向的负结果抽核通过：Broadcast 取消面收敛（C3-002/C8-004 互引）、上下文传播负结果（ClusterRpcClient:62 ctx 仅用于 isCallExpired）、轮询回路尾部重调度无重叠、AutoRegistration 回调异常被既有 catch 覆盖。
- 终止信号裁定认可：全 P3 且深挖面收敛，无需第 3 轮。
