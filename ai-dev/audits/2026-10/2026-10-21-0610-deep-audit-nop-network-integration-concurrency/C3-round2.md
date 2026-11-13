# C3 深挖第 2 轮 — nop-network-integration（WI 4.3）

## 深挖范围与方法

按派发任务对第 1 轮 32 条（C3-001…032，两批次收官）之外的邻接面定向深挖，全部基于 live code 逐文件实读：

1. **Apache 异步链（reactor 回调面）**：`ApacheHttpClient`（fetchAsync/doDownloadAsync/upload 双模式的 promise 完成路径、416 重试链的 cancel 耦合）、`DownloadResponseConsumer`/`AbstractStreamResponseConsumer`（HC5 consumeResponse→streamStart→completed 回调链）、`LineAsyncDataConsumer`（completed()/resultCallback 派发）、`ServerEventPublisher`/`ServerEventResponseConsumer`（completed/failed/cancelled 三回调与订阅终态的关系）。
2. **rpc Invoker 链 future 组合**：`HttpRpcService`（thenApply 链）、`MessageRpcClient`（prepareSend + sendAsync.whenComplete + cancelToken 组合）、`MessageRpcSubscriber` 面、`RpcMessageSubscriber.handleMethod`（同步/异步双分支完成路径）、`MultiRpcService`、`CancellableRpcClient`/`PollingRpcClient` 组合器、`RpcInvocationHandler`（proxy_destroy/proceedAsync）、`AopRpcServiceInvocation`。
3. **DelegateHttpClient 委派链异常传播**：`DelegateHttpClient` 全部五个入口的 onXxxBegin 分派矩阵 × `AddAccessTokenHttpClientEnhancer` 覆盖面。
4. **oauth enhancer 异步边界**：`AddAccessTokenHttpClientEnhancer`（EnhancedClient 内部类、onFetchBegin 同步段、锁内 fetch 面——第 1 轮已立案部分不重复）。
5. **SSE 分支的 completed 语义**：`AbstractServerEventSubscription`（request/onComplete/processLine/dispatchEvent/waitForDemand/cleanup 全方法）、`jdk ServerEventPublisher`（startRequest/whenComplete/processResponse/parseEvents）、`apache ServerEventPublisher`（FutureCallback 三回调 + onStreamComplete）。
6. **基线外隐藏站点**：`HttpFetchTask`、`HttpServerHelper._runWithFilters`、`CoreInitializationGuardFilter`、`VertxHelper.runTask/toCompletableFuture`、`FlowControlRpcServiceInterceptor`/`DefaultFlowControlRunner`、三个 StreamingEventAggregator、全 scope `appendOnCancel` 站点穷举（grep 复现）。

**盲审纪律**：未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`（第 1 轮三文件除外），未查 git log。与第 1 轮同根的形态一律以「扩展 C3-{nnn}」并入；第 1 轮已立案站点（C3-001…032）不重复。

**本 轮核心结论**：深挖任务书点名的"SSE 分支 completed 语义"确认为真实缺陷族——订阅终态（onComplete/onError）之后**无任何终止隔离**：底层请求不取消、消费不停止、迟到事件可越过终态再次派发（详见 C3-033）。Apache/JDK 两实现 + 共享基类三点同源。

## 新增发现

### [C3-033] SSE 订阅终态后无终止隔离：[DONE] 不取消底层请求、迟到事件越过 onComplete 继续派发、onError 无终态守卫——Flow 规范违约 + 承载线程持续空转

- **文件**: 基类 `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/AbstractServerEventSubscription.java:63-70`（onComplete 无隔离）、`:82-115`（processLine：`[DONE]` → dispatchEvent+onComplete 后解析态继续可用）、`:117-127`（dispatchEvent 仅检查 isCancelled）、`:59-61`（onError 无 completed 守卫）；apache `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ServerEventPublisher.java:70-73`（completed 回调 → 第二次 onComplete）、`:117-124`（onStreamComplete）；jdk `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java:74-89`（whenComplete 异常分支 → onError）、`:138-145`（parseEvents 循环）
- **证据片段**:
  ```java
  protected void onComplete() {
      if (completed) return;
      completed = true;                 // 仅防重入，不隔离后续事件源
      dispatchEvent();
      subscriber.onComplete();          // 终态发出，但底层请求仍在跑
  }
  // processLine：data:[DONE] → dispatchEvent(); onComplete(); —— 此后 id/event/data 继续累积
  private void dispatchEvent() {
      if (data != null) {
          if (!isCancelled) {           // 唯一闸门是 isCancelled，completed 不检查
              waitForDemand();          // 终态后无人再 request/cancel → 永久 wait
              subscriber.onNext(...);   // 可在 onComplete 之后再次调用（Flow 违约）
          }
  ```
- **严重程度**: P2
- **并发场景**: SSE 承载线程（apache IO reactor dispatch 线程 / jdk client executor 或 globalWorker 线程）在终态信号发出后继续驱动消费；取消线程经 cleanup 与之交错。单线程内即可成立（无需多线程交错），属完成语义缺陷。
- **触发时序**: (a) **迟到事件越过终态**：T1 流式响应推送 `data: [DONE]` → onComplete()（completed 置位、subscriber.onComplete）→ T1 同一消费循环继续 readLine/消费后续 chunk → 服务器（兼容网关、或规范不禁止的持续推流端）再推任何 `data:` 行 → processLine 累积 → 空行触发 dispatchEvent → `completed` 不在检查之列 → `subscriber.onNext` 在 onComplete 之后再次调用——Reactive Streams 2.7/2.13 违约，下游聚合器/收集器的终态后状态被二次写入。(b) **终态后挂死承载线程**：订阅者收到 onComplete 后按契约不再 request 也不 cancel（引用已弃）→ 下一条迟到数据的 `waitForDemand()` 进入 `wait()`，无超时、无人 notifyAll → apache reactor 线程（共享）或 jdk executor 线程**永久 park**——这是第 1 轮 C6-004"阻塞反压"缺陷在终态后的最强触发形态：终态前订阅者尚有 request/cancel 的合同义务，终态后义务终止而实现仍等待。(c) **cancel 后 onError**（jdk 专属）：T2 取消 → cleanup → `future.cancel(true)` → `CompletableFuture` 以 CancellationException 异常完成 → L74 whenComplete 的 `ex != null` 分支 → `onError(wrapException(CancellationException))` → **每次 jdk SSE 取消都确定性向订阅者派发一个 onError**（cancel 之后的非法信号），下游按真实失败记日志/告警/触发重试。
- **现状**: `completed` 只用于 onComplete 幂等，不进入 dispatchEvent/onError 的闸门条件；`[DONE]` 分支不 cancel 底层请求（apache 未调 `future.cancel`，jdk 不关 reader）；jdk whenComplete 把取消异常与真实失败混同。
- **风险**: AI 网关流式链路（本模块 SSE 的主要消费场景）中，网关类服务端在 `[DONE]` 后补发 usage/错误事件的实现差异会把缺陷变为常态路径；终态后 park 的 reactor/executor 线程累积直接复现 C6-004 的线程耗尽后果且更难归因；取消路径的假错误污染重试与监控语义。
- **建议**: (1) `onComplete()` 内先 `cleanup(null)`（复用既有幂等清理：isCancelled 置位 + 底层 future cancel + notifyAll）再派发终态，使"终态 = 数据源终止"；(2) `dispatchEvent()` 与 `onError()` 增加 `if (completed) return;` 守卫（迟到的 processLine 数据直接丢弃）；(3) jdk whenComplete 对 `CancellationException` 分支直接 return（取消不是错误）。
- **失效面枚举**（F3 同族清扫——终态信号路径全扫）: ① `[DONE]` → onComplete（base，中招）；② apache `onStreamComplete → onComplete` + `FutureCallback.completed → onComplete` 双路径（幂等不重复派发 → 该面 OK；但同样不隔离后续 → 中招同面）；③ jdk parseEvents EOF → onComplete（同 ①）；④ `onError`（base 无守卫 → 中招：终态后 failed/exceptionHandler 仍可派发 onError）；⑤ cleanup/cancel 路径：apache `cancelled()` 不向订阅者发信号（合规 → OK），jdk `whenComplete` CancellationException → onError（中招）；⑥ OkHttp 无 SSE 实现（`fetchServerEventFlow` 抛 UnsupportedOperationException，不适用）。
- **信心水平**: 确定（三条路径均为静态可达代码顺序；(b) 的 park 无任何超时/唤醒兜底，成立不需要稀有服务端行为——只需终态后一条迟到数据）
- **误报排除**: 与第 1 轮 C6-004（demand 等待无超时的反压实现）同承载点但**缺陷本体不同**——C6-004 报的是等待机制无超时，本条报的是终态隔离缺失使"等待者义务已终止"的窗口成为常态；与 C3-003（jdk readLine 取消失效）不同——本条的 (a)(b) 在**不取消**的主成功路径即成立。非"原语使用误报"：Flow 规范的终态唯一性是显式契约。
- **复核状态**: 未复核
- 复核状态: 已保留(P2)｜新发现——三路径回码全部证实：onComplete 仅置位不隔离（AbstractServerEventSubscription L63-70）、processLine `[DONE]` 后解析态继续可用且 dispatchEvent 唯一闸门是 isCancelled（L82-127）、waitForDemand 终态后永久 park 无超时/唤醒兜底（L143-153）、jdk whenComplete 将 CancellationException 确定性路由为 onError（ServerEventPublisher L74-78）；apache 双完成回调幂等面与 jdk EOF 面核对一致；与 C6-004 同承载点缺陷本体区分成立

## 扩展第 1 轮发现（并入，不另立案）

- **扩展 C3-008（失效面枚举修正）**: 第 1 轮判定"onFetchBegin（fetchAsync/downloadAsync/uploadAsync 三入口经此，均有 token）"有误。`DelegateHttpClient` 的分派矩阵是：`fetchAsync → onFetchBegin`、`downloadAsync → onDownloadBegin`、`uploadAsync → onUploadBegin`、`fetchServerEventFlow → onRequestBegin`（DelegateHttpClient.java:34-56），而 `EnhancedClient` **仅覆盖 onFetchBegin**（AddAccessTokenHttpClientEnhancer.java:54-69）——即 **downloadAsync 与 uploadAsync 与 SSE 一样完全绕过 token 注入与 propagateHeaders**，中招入口由 1 个（SSE）实为 3 个（download/upload/SSE），仅 fetchAsync 有 token。凭据配置了 oauth provider 的部署，文件下载/上传一律 401。修复须同点收敛：onRequestBegin 统一注入或四个 onXxxBegin 全覆盖。
- **扩展 C3-012（新增中招站点 2 处）**: 全 scope `appendOnCancel` 站点穷举（grep 复现，main 源码 10 处）在第 1 轮清单外补充：`MessageRpcClient.callAsync`（nop-rpc-core/.../message/MessageRpcClient.java:104-108，`cancelToken.appendOnCancel(reason -> channelState.onFailure(requestId, ...))` 完成后不移除，lambda 钉住 request/id/通道状态）；`CancellableRpcClient.callAsync`（.../composite/CancellableRpcClient.java:54-72，appendOnCancel 不移除——第 1 轮 C6-012 失效面 ② 以"监听器随 token 生命周期回收"判 OK 的前提（短命 token）与 C3-012 的立案前提（长命 token）矛盾，long-lived token 下同族泄漏）。Apache 4 站点 + jdk SSE/Apache ServerEventPublisher 经 `bindCancelToken`（polling L82-83 亦手动 remove）共 6 处 OK 不变。修复同 C3-012：统一 `FutureHelper.bindCancelToken`。
- **扩展 C3-011（新增抛出站点 1 处）**: `MqttServerMessageService.sendAsync`（nop-vertx-mqtt-server/.../bus/MqttServerMessageService.java:75-77）在 `mqttServer == null` 时同步 `throw NopException`——"返回 CompletionStage"契约的又一同步抛出站点（防御性守卫，注入缺失配置才触发，低危）；与 C3-021 的 `MqttConnection.sendAsync` 同文件消费链。

## 维度集外线索（overlay）

（本轮 C3 面无新增 dims-external 线索；CL 流水号占用见 C6 文件 CL-034。）

## 继续判定

**终止信号。** 本轮新增 1 条：P2×1（C3-033），无 P0/P1；另并入扩展 3 项（均为第 1 轮已立案缺陷的失效面修正/站点补充，不新增计数）。收敛证据：(1) 严重度剖面自第 1 轮 P1×8/P2×16/P3×8 单调下移至单条 P2；(2) 第 1 轮交付的异步入口清单（HTTP 12 入口 + RPC 8 实现 + 拦截器/定时任务）本轮逐项重走完毕，唯一新缺陷族（终态隔离）位于该清单已覆盖入口的完成语义层，且修复点收敛于共享基类一处；(3) 深挖任务书五个方向中，Apache reactor 回调面（资源面已由 C6 卷覆盖、异步语义面即 C3-033）、rpc future 组合（新增仅 2 个监听器泄漏站点 + 1 个低危同步抛）、Delegate 委派链（产出 C3-008 失效面修正）、oauth 异步边界（第 1 轮 C3-008/C1-002/C6-008 已覆盖主体）、SSE completed 语义（C3-033）均已闭合。判定：C3 维度收官，扩展项并入对应条目修复基线，无需第 3 轮。

---

## 维度复核结论（C3 复核员附注，WI 4.3）

- 复核员：C3 维度独立复核员（CRA WI 4.3）；完整判定表与最终保留项见同目录 `C3-async-chains.md`「维度复核结论」节。
- **C3-033：保留(P2)**。三路径逐行回码证实：`onComplete` 仅置位不隔离数据源（AbstractServerEventSubscription L63-70）；`[DONE]` 分支后解析态继续可用且 `dispatchEvent` 唯一闸门是 `isCancelled`、`completed` 不在检查之列（L82-127）；终态后迟到事件的 `waitForDemand()` 进入无超时 `wait()`、无人 notifyAll（L143-153）；jdk `ServerEventPublisher` L74-78 将取消的 CancellationException 确定性路由为 `onError`。与 C6-004（同承载点的反压实现）缺陷本体区分经核对成立。修复建议三点（终态即 cleanup + dispatchEvent/onError 补 completed 守卫 + CancellationException 分支 return）核验可落地，单点收敛于共享基类。
- **round2 三项扩展复核结论**：扩展 C3-008 失效面修正成立（DelegateHttpClient 分派矩阵 L34-56 与 EnhancedClient 仅覆盖 onFetchBegin L54-69 实读证实，中招入口 1→3）；扩展 C3-012 新增 2 站点成立（MessageRpcClient L104-108、CancellableRpcClient L54-72 appendOnCancel 不移除；全 scope 穷举 10 站点与初审清单并集一致，PollingRpcClient L82-84 手动 remove OK）；扩展 C3-011 新增 1 站点成立（MqttServerMessageService.sendAsync L75-77 同步抛）。
- **收官判定维持**：round2「C3 维度收官、无需第 3 轮」结论经独立复核维持——本轮全维度复核新增结构性问题为 0（3 条驳回均为跨维度同站点重复登记，非 C3 面内遗漏），C3 面内 30 条保留发现无一条因复核被证伪。
- 移交 7.1 事项（C3 侧）：① C3-016↔C6-021、C3-017↔C1-009 两处判级分歧（C3 侧意见已附）；② 两组休眠面激活回升条款统一适用（feishu 链 C3-015+C6-024；vertx-mqtt 面 C3-018 合并工单）；③ C3-001/C3-005 两族跨单元同族登记单列。
