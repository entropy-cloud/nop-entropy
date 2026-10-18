# C8 背压·流控·过载行为审计报告（第 2 轮深挖·盲审）

## 基本信息

- **审计单元**: nop-service-framework-hotpath（请求热路径五模块 / 8 源码根 / 406 文件 / 42,748 行）
  - `nop-service-framework/nop-graphql/nop-graphql-core`（145 files / 17,097 lines）
  - `nop-service-framework/nop-graphql/nop-graphql-orm`（11 files / 859 lines）
  - `nop-service-framework/nop-graphql/nop-graphql-message`（3 files / 162 lines）
  - `nop-service-framework/nop-graphql/nop-graphql-grpc`（37 files / 3,174 lines）
  - `nop-service-framework/nop-biz`（89 files / 9,985 lines）
  - `nop-service-framework/nop-gateway`（44 files / 4,743 lines）
  - `nop-service-framework/nop-biz-auth-core`（60 files / 5,760 lines）
  - `nop-service-framework/nop-biz-file-core`（14 files）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度**: C8——背压、流控与过载行为
- **判级口径**: 沿第 1 轮（请求热路径高 QPS 门槛最低判定；写不出过载数字的背压类发现一律 P2 标注）
- **现状标注**: 本轮为第 2 轮深挖，新增条目 `复核状态: 未复核`；既有条目复核结论见各节。

## 本轮输入

- 第 1 轮报告 `C8-backpressure.md`（C8-001~008 + CL-012~014 + 15 项缓冲载体清单 + 生产-消费边界清单）。
- 基线与 live code；第 1 轮「×16」数字沿 C4 r3 勘误口径以 ×7（加法）引用。
- 既有登记边界（不重复立案）：C3-003（背压断裂/BufferedStreamingPublisher 缓冲）、C8-003（同族其他缓冲）、CL-012/013/014 已登记。

## 盲审纪律声明

本轮为盲审深挖轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件，**唯一例外**为本维度第 1 轮文件 `C8-backpressure.md`（必要输入）。未读取同目录其他维度文件。一切结论以当前 live code 复现为准；prompt 给出的既有登记号仅作不重复边界提示。

## 本轮目标（①~⑤）

1. ① C8-001 失效面扩展（「同步阻塞发送」族）：basicRemote 同步发送站点全枚举；「5 线程全局 timer 钉死」聚合量化；慢客户端检测与断开机制缺失面。
2. ② C8-002 失效面扩展（重试放大族）：×7 加法口径收口；在途并发无准入上界的量化收口；Retry-After 90s 等待队列形态。
3. ③ C8-007 量化复核：每连接 1000 订阅硬编码 + N 连接聚合算术复核；setter 无调用方复核。
4. ④ 限流面收口：C8-005/C8-008 关系判定；AiRateLimit 实现本体复核（令牌桶算法/持久化）。
5. ⑤ C8-006 零指标面扩展：GraphQL 层 MetricsGraphQLHook 已接线对照复核；auth/file 层指标面。

---

## 第 2 轮复核记录（指令①~⑤ 逐项）

### ① C8-001 失效面扩展复核（同步阻塞发送族）

**basicRemote 站点全枚举**（`grep -rn "getBasicRemote\|getAsyncRemote"` 全仓，排除 target/test = **2 文件命中**）：

| # | 站点 | 代码 | 结论 |
|---|---|---|---|
| A1 | `nop-quarkus/nop-quarkus-web/.../JakartaWebSocketSession.java:25` | `session.getBasicRemote().sendText(message)` | 同步阻塞，无写超时 |
| A2 | `nop-spring/nop-spring-web-starter/.../SpringWebSocketSession.java:25` | 同上 | 同步阻塞，无写超时 |

`getAsyncRemote` 全仓 **0 命中**——平台无任何异步发送路径。`IWebSocketSession.sendMessage` 是 8 源码根内唯一的 WS 发送抽象，全部调用方最终收敛到 A1/A2 的阻塞原语。8 源码根内 `session.sendMessage` 全站点 = **12 处 send + 7 处 close**，全部在 `JsonRpcWebSocketHandler`（167/169/210/255/359/380/384/464/489/326/331/217*）与 `GraphQLSubscriptionManager.pushToClient:217`（*经 grep 确认 217 为该文件唯一 send 站点）。第 1 轮 F3 枚举「无遗漏入口」结论**复核成立**。

**集外排除**：`nop-graphql-grpc/GraphQLServerCallHandler.java:202-211` 的 `call.sendMessage` 为 gRPC unary 单发（每 call 一次，HTTP/2 传输层流控自管，grpc-java `ServerCall.sendMessage` 非阻塞入队），非同族缺陷——排除并登记。

**「5 线程全局 timer 钉死」聚合量化（复核+精化）**：

- 池参数：`GlobalExecutors.globalTimer()`（`nop-kernel/nop-commons/.../GlobalExecutors.java:38-49`）默认 `maxPoolSize=5`（`CommonConfigs.java:22-23`，`nop.commons.concurrent.global-timer.maxPoolSize` 缺省 5，可配置但平台缺省即 5）。
- keepAlive 任务形态：每连接 1 个 `scheduleWithFixedDelay(10s)` 任务（`JsonRpcWebSocketHandler.java:82-83`），任务体 = 阻塞 ping send，时长 L。
- 利用率公式：**ρ = (N/10) × L / 5 = N·L/50**。饱和门槛 N_stalled × L = 50 线程·秒：
  - L=10s（严重停顿客户端）→ **5 个同时停顿的客户端即钉死全池**；
  - L=30s → **2 个**；L=60s → **1 个**。
- 第 1 轮「N>5×(10s/L) 时池饱和」与本轮公式一致，复核成立；本轮精化结论：**钉死门槛低至 1~5 个同时停顿的慢客户端（L=10~60s 区间）**，无需大规模连接数。
- 级联面：全池被钉死期间，全部 N 连接的 keepAlive ping 与 `DownloadHelper:39` 临时文件清理（8 根内 globalTimer 仅有的另一用户）全部停摆；以 ping 判活的前端将成批断连。
- 8 根内 globalTimer 用户全枚举：`JsonRpcWebSocketHandler.java:82`（keepAlive）、`DownloadHelper.java:39`（CL-014 已登记）。无第三用户。

**慢客户端检测与断开机制缺失面**：**缺失，全部成立**——发送失败三站点（onNext:466-468、sendKeepAlive:360-362、onComplete:490-492）一律仅 `LOG.warn` 不关会话不取消订阅；keepAlive 单向 ping、`METHOD_PONG` 分支为空 `break`（:149-150）无任何 pong 校验/超时记账；两适配器无 `setMaxIdleTimeout`（grep=0 命中）；无发送队列、无按会话串行化锁。此缺失面立为新发现 **C8-009**（见下）。同时本轮补充一个第 1 轮未列的并发发送面：同一 session 的发送方有三个线程域（keepAlive timer 线程、订阅 publisher 线程、WS 容器 onMessage 线程），Jakarta WebSocket `RemoteEndpoint.Basic` 非线程安全、规范要求应用侧自行保证单写者——本模块无任何锁/队列保证，并发写结果依赖容器实现（部分容器抛 `IllegalStateException`、部分内部串行化），并入 C8-009 F3 枚举。

**C8-001 判级维持 P1**，本轮无修正项；聚合量化精化（1~5 慢客户端即钉死全池）使其触发面比第 1 轮表述更窄（更易触发）。

### ② C8-002 失效面扩展复核（重试放大族）

**×7 加法口径复核（成立）**：

- 主 URL：`InvokeProcessor.invokeUrl`（:135 `int maxRetries = 3` 局部硬编码）→ `invokeUrlWithRetry` 1 次首发 + 最多 3 次重试（429 分支 :152-159 按 Retry-After 退避；5xx 分支 :161-167 按 2^n×1s+jitter 退避）= **最多 4 次主上游调用**。
- fallback：`AiFailoverGatewayInterceptor.tryFallbackUrl`（:111-145）**直接 `httpClient.fetchAsync` 单发，无内层重试**——429/5xx 时仅链到下一 fallback（:128-132），最多 min(3, fallbackUrls.size()) 次。其 `maxRetries` 有 setter（:51-53）可配，但 InvokeProcessor 侧的 3 次重试不可配。
- 合计 **4 + 3 = 7 次**上游请求/网关请求，加法口径成立（非乘法 4×4=16——fallback 绕过 InvokeProcessor 重试链，读码确认）。

**等待队列形态（复核+精化）**：等待不是显式队列，而是 **N 条各自挂起的 `thenCompose` 补全链**，钉在 JDK `CompletableFuture.Delayer`（`InvokeProcessor.delayedFuture:198-202` 用 `CompletableFuture.delayedExecutor`——JVM 全局单线程调度器 + 内部无界 `DelayedWorkQueue`）上。每条挂起链强持有：body String（`GatewayHttpFilter:143-146` `getTextAsync` 物化，lambda 闭包捕获至 future 完成）+ JSON.parse DOM 树 + `ApiRequest`/`IGatewayContext` 全套。`Retry-After` 钳制 30s（`MAX_RETRY_DELAY_MS:208`），429 路径 3 次重试 → 单请求最长持有 ≈ **3×30s + fallback 时间 ≈ 90s+**，复核成立。附带后果：全部被 429 的请求若拿到相同 Retry-After 值，将在同一时刻齐发重试（无 jitter 的同步重试波，第 1 轮表述复核成立）。

**在途并发无准入上界——量化收口（参数化）**：

- 准入控制存在性：`grep -rn "Semaphore\|HighWatermark\|RateLimiter\|maxInflight\|maxConcurren\|tryAcquire\|admission" nop-gateway/src/main/java` = **0 命中**；`GatewayConfigs` 全部配置仅 `nop.gateway.model-path` 1 项（`GatewayConfigs.java:19-21`）；nop-gateway 主代码 timeout 配置点 **0** 个。无准入上界结论**复核成立**。
- 内存参数化（Little's law）：在途数 C = λ × H（λ=到达率，H=持有时长，429 洪峰下 H→90s）。每在途保留 ≈ **S×2（String，UTF-16）+ S×(3~5)（DOM 树）≈ S×(5~7)**，S=请求体字节数。示例轨迹：λ=1000/s、S=10KB、H=45s → C=45,000 → 堆 ≈ 45,000 × 10KB × 6 ≈ **2.7GB**，且随 λ 线性增长无上界。
- 流式重试回调面状态更新（第 1 轮 C8-002 F3 ④「依赖回调自律」）：`BufferedStreamingPublisher` 的 `attemptCount` 仅递增无上界检查（:142,224），重订阅循环在 publisher 本体确实无界；但生产接线回调 `GatewayStreamingRetryCallback`（nop-ai-gateway，热路径范围外依赖面）以 `retryBudget` 预算钳制（:62,80-83，超预算返回 null 断流）。**约束存在于回调层、publisher 契约仍无界**——第 1 轮定性维持，补充注明生产回调已有预算这一缓解事实。

**C8-002 判级维持 P1**，无修正项；本轮补充的参数化给出可复核的 OOM 轨迹数字。

### ③ C8-007 量化复核

- **1000 硬编码**：`JsonRpcWebSocketHandler.java:59` `private int maxActiveOperations = 1000;` 确认。
- **setter 无调用方**：`setMaxActiveOperations` 全仓 grep（java/xml/yaml/properties，排除 target）= **0 调用方**（含 beans 与配置文件）；`GraphQLSubscriptionManager.setMaxActiveSubscriptions` 仅有 2 处测试调用（`TestGraphQLSubscriptionManager.java:60,115`）。第 1 轮「上限不可调」结论**复核成立**。
- **每订阅持有链验证**：`activeOperations`（CHM）→ `SubscriptionSubscriber`（:440-503，含 `AtomicReference<Flow.Subscription>`）→ `sendStreamingMessage:349-355` subscribe 的 `RpcSubscriptionPublisher` → 其匿名 `Flow.Subscription`（`onSubscribe` 内捕获外部 `TransformingSubscriber`）→ `RpcSubscriptionPublisher` 外部类引用 `context`/`engine` → `IGraphQLExecutionContext`（`ApiRequest` + data Map + `FieldSelectionBean` 树 + headers）。**每活跃订阅钉住完整执行上下文**成立，10KB/订阅为保守量级（随 selection 复杂度上浮）标注合理。
- **算术复核**：10^6 订阅 × 10KB = 10^10 B ≈ **10GB（十进制）**，成立。本轮补充：线性域远早于 N=1000——N=100 连接 × 1000 订阅 = 10^5 订阅 ≈ **1GB**；keepAlive 任务数 = N（每连接 1 个，非每订阅），N=1000 时 timer 到达率 100 任务/s（对照第 1 轮 N=5000 → 500/s 的表述，两者均为量级示例不矛盾）。

**C8-007 判级维持 P2**，量化复核通过，无修正项。

### ④ 限流面收口复核（C8-005 / C8-008 关系 + AiRateLimit 本体）

**关系判定：两个独立设施的不同缺陷面**（非同一设施的两个面）：

| | C8-005 | C8-008 |
|---|---|---|
| 设施 | 网关自研 `TokenBucket`（`AiRateLimitGatewayInterceptor` 内部类 :146-179） | `DefaultFlowControlRunner`（nop-rpc-core :36-58） |
| 层 | 网关 HTTP 拦截层（per-XFF-key） | RPC/GraphQL 执行层（经 `GraphQLFlowControlInvoker`/`FlowControlFetcher` 双站点） |
| 时基 | `CoreMetrics.currentTimeMillis()` 墙钟惰性回填（缺陷） | Guava `RateLimiter`（`DefaultRateLimiter` 包装，内部单调钟，**无时钟缺陷**） |
| keying | 按 XFF key 分桶（正确） | `entry.resource/origin/bizKey` 全忽略，单全局桶（缺陷） |
| 缺省启用 | 需路由显式启用拦截器 | 非缺省 runner（缺省为 `SentinelFlowControlRunner`，keying 正确） |

- **AiRateLimit 本体复核**：令牌桶算法标准（惰性回填 + `synchronized` 单桶 + `tokens>=1` 判定，:161-178）；桶缓存有界性复核通过（`LocalCache` maximumSize=maxTrackedKeys=100k + expireAfterAccess=ceil(capacity/refillRate)×refillIntervalMs，TTL 语义「静置满 refill 周期后驱逐重建=满桶」无损）；**无持久化**（内存态，重启即全桶回满 = 重启绕过窗口）；**无集群协调**（per-node 独立桶，集群有效限额 = 节点数 × capacity，部署多副本时限流语义按节点数放大）。
- **交叉发现（限流设施不复用）**：网关自研 TokenBucket **未复用平台已有的 `DefaultRateLimiter`**（`nop-commons/.../ratelimit/DefaultRateLimiter.java`：Guava RateLimiter 单调钟 + `acquireSuccessCount/acquireFailCount` 计数器）。若复用，C8-005 的时钟缺陷与 C8-006 的网关拒绝计数缺失可同时消除——两设施互不复用是两个缺陷面的共同工程根因，修复建议据此对齐：TokenBucket 内核替换为 per-key `DefaultRateLimiter`。

**C8-005 / C8-008 判级均维持 P2**，live code 复核通过，无修正项。

### ⑤ C8-006 零指标面扩展复核

- **GraphQL 层对照复核（已接线，确认）**：`MetricsGraphQLHook`（nop-graphql-core :41-59,72-114）= 6 个 Micrometer Timer（graphql.execute/invoke/data-fetch × SUCCESS/FAILURE）+ `RpcServerStat` runningCount 在途计数 + `addExecuteTime`/成败计数；装配点 `nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:18,23`（`graphQLHook` → `nopMetricsGraphQLHook`）；另有 `DevStatBizModel.rpcServerStats`（nop-biz :44-47）暴露查询面。第 1 轮「零指标面精确局限于 nop-gateway」**复核成立**（`grep -rn "MeterRegistry\|micrometer" nop-gateway/src/main/java` = 0，复现）。
- **auth 层（合规）**：`LocalUserContextCache` 3 缓存 + `LocalLoginAttemptStore`（:36）均 `useMetrics()`；`LocalCache.useMetrics` → `CaffeineCacheMetrics.bindTo(GlobalMeterRegistry)`（`LocalCache.java:76`）——auth 有界载体（登录缓存/失败计数）已可观测。**无缺陷**。
- **file 层（零指标，立案 C8-010）**：nop-biz-file-core 全 17 文件 `MeterRegistry|micrometer` = **0 命中**；`DownloadHelper`（nop-biz）同 0。上传在途数/字节速率、下载生成时长、临时文件存量全部不可观测——C8-006 零指标家族的 file 子面，立 **C8-010（P3）**。
- graphql-message 模块（3 文件）亦零指标，但其为消息入站适配器（执行面经 engine executionInvoker，flowControlRunner 挂接点覆盖），过载可见性相关性低，登记不立案。

---

## 第 2 轮（深挖）新增发现

### [nop-service-framework-hotpath-C8-009] WS 会话无死活检测与失败断开机制：发送异常仅告警不断连、keepAlive 单向无 pong 校验、无 idle timeout——僵尸会话的 keepAlive 定时任务与订阅占用无界累积

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:466-468,357-363,149-150,112-117`；适配器依赖面 `JakartaWebSocketSession.java:24-26`、`SpringWebSocketSession.java:24-26`
- **证据片段**:
  ```java
  // :466-468 onNext —— 发送失败仅 WARN，不 cancel 订阅、不 close 会话，继续 request(1)
  } catch (Exception e) {
      LOG.warn("nop.websocket.send-next-fail", e);
  }
  subscription.get().request(1);
  // :357-363 sendKeepAlive —— 失败仅 WARN，会话永不断开，10s 后再次尝试
  private void sendKeepAlive() {
      try { session.sendMessage(createNotification("ping")); }
      catch (IOException e) { LOG.warn("nop.websocket.send-keep-alive-fail", e); }
  }
  // :149-150 PONG 分支为空——keepAlive 单向 ping，无 liveness 记账
  case METHOD_PONG: break;
  ```
- **严重程度**: P2
- **并发场景**: 客户端半开（NAT 表项超时/移动网络切换/进程僵死未发 FIN）或 TCP 零窗口停顿——服务端 send 要么成功写入 OS 缓冲（连接实际已死，容器不可知），要么抛 IOException/阻塞。三处失败处理全部只打 WARN；keepAlive 每 10s 对同一死会话重发。模块内不存在任何把「发送失败」转化为「断开 + 清理」的路径；唯一清理入口 `onClose:120-129` 依赖容器先判定死连接（半开场景下容器同样不可知）。
- **触发时序**: T0=客户端半开/停顿 → T1=keepAlive 或订阅推送 send 失败（或阻塞，C8-001 面）→ 仅 WARN，会话保留 → 此后每 10s keepAlive 重发（快速失败形态=每僵尸 6 条 WARN/min，日志洪峰；阻塞形态=钉住 timer 线程）→ 会话、其 `activeOperations` 订阅条目、keepAlive 定时任务、订阅上游 Flow 链与执行上下文全部永久滞留 → 僵尸累积消耗：①每连接 1000 订阅配额被僵尸占用后新订阅被 4409/TOO_MANY 拒绝；②keepAlive 任务在 5 线程全局 timer 上的常驻负载单调上升（与 C8-001/C8-007 聚合面叠加）；③`onThrowable:112-117` 只取消 keepAliveSender 不清理 activeOperations（依赖容器保证 onError 后必有 onClose，容器相关，不能作为兜底）。
- **过载数字**: 僵尸滞留时长 = ∞（模块内无回收路径；半开 TCP 下容器 idle 判定也不可达——两适配器无 setMaxIdleTimeout，grep=0）。僵尸增长速率 = 半开连接发生率 r；日志产出 = 每僵尸 8,640 条 WARN/天；timer 面负载 = 僵尸数/10 任务·s⁻¹ 落 5 线程池（僵尸 ≥ 50 个且每次 send 阻塞 ≥10s 时池饱和，见 C8-001 公式 ρ=N·L/50）。
- **现状**: 清理逻辑存在且正确（`onClose` 全清 + `onError:479` 双参 remove），但从不被发送失败触发；keepAlive 设计为「发 ping」而非「测活」（PONG 空分支）；无任何 idle/send 超时配置面。同会话三个线程域（timer/publisher/容器 onMessage）可并发调用 `basicRemote.sendText`，模块无串行化保证，Jakarta 规范将单写者责任归于应用侧——并发写结果依赖容器（抛 IllegalStateException 或内部串行），故障形态不可控。
- **风险**: 半开连接常态发生（移动端/云环境），僵尸面单调增长且与 C8-001（阻塞钉 timer）、C8-007（订阅占用）叠加放大；发送失败日志洪峰自身构成过载噪声，掩盖真实故障信号（与 C8-006 零指标叠加，僵尸数不可观测）。
- **建议**: ①发送失败（任意站点连续 N 次）即 `session.close` + `cancelOperation`——复用既有 `onClose` 清理路径；②keepAlive 增加超时未收到 pong 的死亡判定（METHOD_PONG 分支已存在，补 lastPongAt 记账 + 阈值断开）；③适配器补 `session.setMaxIdleTimeout`（容器通用能力，一行配置级）；④每会话串行发送器（带界队列 + 溢出断开，兼修 C8-001 与并发写面）；⑤onThrowable 补 activeOperations 清理。
- **失效面枚举**（F3 同族清扫）: ①`SubscriptionSubscriber.onNext:466-468` catch（失败不 cancel 不 close）——立案本体；②`sendKeepAlive:360-362` catch（同态，且在共享 timer 上重试）——立案本体；③`SubscriptionSubscriber.onComplete:490-492` catch（同态，低危——条目已在 :494 移除，仅 complete 消息丢失）——同族登记；④`METHOD_PONG` 空分支（:149-150，liveness 校验缺失）——立案本体；⑤`onThrowable:112-117`（取消 keepAlive 但不清理 activeOperations）——同族登记；⑥无发送串行化（timer/publisher/onMessage 三线程域并发 `basicRemote`，规范单写者责任无着落）——立案本体（与①②同修）；⑦两适配器无 idle timeout/写超时——依赖面（适配器维度清扫）；⑧`onClose:120-129`/`onError:479` 清理逻辑——配对正确（正面对照，缺的是触发入口而非清理逻辑）。gRPC 站点已排除（unary 单发，传输层自管流控）。
- **信心水平**: 确定（失败路径行为与缺失面均可从 live code 直接复现；僵尸增长率 r 依赖部署环境，已参数化）
- **误报排除**: 不是「有清理逻辑」误报——onClose/onError 的清理确实存在，本条立的是**触发入口缺失**（发送失败与超时均不触发）；不是把容器职责错报为模块职责——模块自建了 keepAlive 与订阅生命周期管理（说明已承认该职责），却无任何死活闭环；不是测试路径——WS 推送为订阅热路径。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2**。三站点仅 WARN 不关会话逐行亲验成立（`onNext:466-468`、`sendKeepAlive:360-362`、`onComplete:490-492`——且 onNext 失败后仍 `:469 request(1)` 继续拉动）；`METHOD_PONG` 空分支（`:149-150`）单向 ping 无 pong 记账成立；`setMaxIdleTimeout` 全仓 grep = 0 命中复现；三线程域（timer/publisher/容器 onMessage）并发写 `basicRemote` 无串行化成立（handler 全文无锁）；`onThrowable:112-117` 仅取消 keepAliveSender 不清理 activeOperations 成立；清理逻辑本身正确（`onClose:120-129` 全清 + `onError:479` 双参 remove）但无发送失败触发入口——「触发入口缺失」定性准确。**量化继承 C8-001 修正**：池实为 4 线程（非 5），「僵尸 ≥ 50 且 send 阻塞 ≥10s 池饱和」应按 ρ=N·L/40 重算（持续阻塞形态 4 僵尸即钉死），阈值更低，方向不变。P2 维持。现状：未修复。

### [nop-service-framework-hotpath-C8-010] 文件上传/下载链路零指标：在途上传数、字节速率、下载生成时长、临时文件存量全部不可观测（C8-006 零指标家族 file 子面）

- **文件**: `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/`（全 17 文件，代表 `NopFileStoreBizModel.java`、`AbstractGraphQLFileService.java`）；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/download/DownloadHelper.java:25-52`
- **证据片段**:
  ```bash
  $ grep -rn "MeterRegistry\|micrometer" nop-service-framework/nop-biz-file-core/src/main/java
  （0 命中）
  $ grep -rn "MeterRegistry\|micrometer\|Metric" nop-service-framework/nop-biz/src/main/java/io/nop/biz/download/
  （0 命中）
  ```
- **严重程度**: P3
- **并发场景**: 并发上传/下载过载（在途数 × 文件大小）发生时的可观测路径——仅 LOG；对照 auth 层（`LocalUserContextCache` 3 缓存 + `LocalLoginAttemptStore` 均 `useMetrics()`，经 `LocalCache.java:76` 绑定 GlobalMeterRegistry）与 GraphQL 层（6 Timer + runningCount）均有接线，file 层为唯一无接线的文件面。
- **触发时序**: 大量并发上传/下载（如备份脚本失控）→ 上传在途数/累计字节、zip 生成时长（CL-014 的同步写盘时长）、`/tmp` 临时文件存量（CL-014 的重启遗留）→ 无任何 metric 可查 → 磁盘/带宽过载不可量化、不可告警。
- **过载数字**: 指标覆盖面 = 0/4（上传在途、字节速率、下载时长、临时存量）；对照 auth 层缓存指标已接、GraphQL 层 6 Timer 已接。
- **现状**: 上传路径钳制合规（maxFileSize 默认 16MB + 后端 `LimitedInputStream` 二次钳制，第 1 轮 Stub 复核已确认）——本条非钳制缺失，纯可观测性缺失；分片上传为纯 stub（无实现即无热路径，不立案）。
- **风险**: 文件面过载（并发大文件上传/下载、临时目录膨胀）不可见；容量规划无数据。判 P3 因文件操作非最高频热路径、钳制已存在、过载形态需较大并发才显著（降级于网关 C8-006 的 P2）。
- **建议**: 复用 `GlobalMeterRegistry` 为 `NopFileStoreBizModel.upload/download` 补 3 组指标：`file.upload.inflight`（Gauge）/`file.upload.bytes`（Counter）/`file.download.generate-time`（Timer）；`DownloadHelper` 临时文件存量 gauge（与 CL-014 的清理任务修复联动）。
- **失效面枚举**（F3 同族清扫）: ①`NopFileStoreBizModel` 上传/下载入口——立案本体；②`DownloadHelper` zip 生成（CL-014 已登记资源面，本条补其指标面）——同族；③`ChunkFileUploadHandler`——纯 stub，无热路径不适用；④auth 层缓存——已接 metrics（正面对照）；⑤graphql-message——零指标但为消息入站适配、低相关，登记不立案。file 层已穷举。
- **信心水平**: 确定（grep 口径可复现）
- **误报排除**: 不是「有日志即观测」误报——维度定义第 4 条要求积压/拒绝指标；不是把钳制误报为缺失——钳制合规面已在第 1 轮确认，本条仅立指标面；判级未按 P2 报因 file 非主热路径且有钳制兜底。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P3**。grep 口径复现：`MeterRegistry\|micrometer` 于 nop-biz-file-core main = 0 命中（独立重跑确认）；钳制兜底亲验：`NopFileStoreBizModel.setMaxFileSize` 注入 `@cfg:nop.file.upload.max-size|16777216`（默认 16MB）+ `checkMaxSize` 前置校验 + `fileStore.saveFile(record, maxFileSize)` 后端二次钳制——「非钳制缺失、纯可观测性缺失」定性准确；auth 层正面对照（`LocalCache.registerMetrics` → CaffeineCacheMetrics.bindTo(GlobalMeterRegistry)）亲验在场。P3 判级合理。现状：未修复。

---

## 维度集外线索（本轮无新增）

本轮探查过但排除立案的候选（CL-026 起未占用）：

- **InvokeProcessor 中间失败响应的连接释放**：`IHttpResponse` 契约为全量缓冲（`byte[]/String`，无 `close()` 方法），连接生命周期归 HTTP 客户端适配器所有——网关代码无显式释放责任，非缺陷线索。
- **JDK `CompletableFuture.Delayer` 全局单线程调度器**（InvokeProcessor.delayedFuture）：JDK 标准形态，任务非阻塞（仅 complete future），单线程足够——非缺陷线索；其无界 pending 面已计入 C8-002 的在途无上界。
- **GraphQLMessageSubscriptionRegistrar 消息入站执行**：执行面经 engine executionInvoker（flowControlRunner 挂接点覆盖），无独立无界执行面。
- **MFA/SMS store 墙钟过期判定**（`CoreMetrics.currentTimeMillis`，LocalMfaChallengeStore/LocalSmsCodeStore）：时钟回拨影响验证码有效期（正确性形态），非 C8 背压形态，登记待 C2/C5 族处理。

## 既有条目处置汇总（第 2 轮）

| 编号 | 第 1 轮判级 | 本轮复核结论 | 判级修正 |
|---|---|---|---|
| C8-001 | P1 | 复核成立；聚合量化精化：ρ=N·L/50，**1~5 个慢客户端（L=10~60s）即钉死 5 线程全池**；gRPC 站点排除；并发发送面（三线程域无串行化）并入 C8-009 | 维持 P1 |
| C8-002 | P1 | 复核成立；×7 加法确认（fallback 单发直连）；等待队列=JDK Delayer 挂起链；内存参数化 C=λ·H、每在途 ≈ S×(5~7)；流式重试预算在生产回调层已确认存在（publisher 契约仍无界） | 维持 P1 |
| C8-003 | P2 | 本轮未重审（第 1 轮证据与 live code 无变更迹象） | 维持 |
| C8-004 | P2 | 本轮未重审 | 维持 |
| C8-005 | P2 | 复核成立；与 C8-008 判定为独立设施；本体复核：算法标准、缓存有界、无持久化（重启回满）、per-node 无集群协调 | 维持 P2 |
| C8-006 | P2 | 复核成立（gateway grep 0 复现）；家族扩展：auth 合规、file 层立案 C8-010 | 维持 P2 |
| C8-007 | P2 | 量化复核通过：1000 硬编码/setter 0 调用方/持有链/10GB 算术全部成立；补充 N=100 即 ≈1GB 的线性域 | 维持 P2 |
| C8-008 | P2 | 复核成立（entry 全忽略、tryAcquire(1,0) 非阻塞、缺省 Sentinel keying 正确） | 维持 P2 |

> **维度复核补充勘误（2026-10-18，沿 C4 复核勘误 8）**：上文 §②「等待队列形态」将挂起链描述为钉在「JDK `CompletableFuture.Delayer`——JVM 全局单线程调度器 + 内部无界 `DelayedWorkQueue`」上，该表述系 JDK≤23 语义。项目 `pom.xml release=25` 下 `CompletableFuture.delayedExecutor` 实际走 `ForkJoinPool.asyncCommonPool()` 的 `DelayScheduler`（单定时线程仅做到点 relay，`future.complete` 与 `thenCompose` 重试发起在 common pool 多线程工作线程执行）。「本文末排除项」中「Delayer 全局单线程调度器……单线程足够」同理按 JDK 25 语义更正（该排除结论本身不变）。**C8-002 的 P1 支柱（在途无上界内存累积 + ×7 放大）不依赖该线程语义，判级维持 P1**——与 C4-003 降 P2 事由（「单线程占用」支柱被证伪）不同柱，详见 `C8-backpressure.md` 文末维度复核结论。

## 终止信号判定

- **新增判级分布**: P0×0、P1×0、P2×1（C8-009）、P3×1（C8-010）。无新增 P0/P1 → 按深挖规则不强制第 3 轮。
- **终止信号论证**（五项深挖指令全部闭合）:
  1. 「同步阻塞发送」族：basicRemote 全仓仅 2 适配器文件、8 根内 19 个 send/close 站点全枚举、gRPC unary 排除、globalTimer 用户全枚举（2 处）——**族已闭合，无未知站点**。
  2. 「重试放大」族：×7 加法收口、fallback 单发细节、等待队列形态、准入闸 grep 0 命中、GatewayConfigs 单配置——**族已闭合**。
  3. C8-007 量化复核全部通过、无算术修正——**复核面闭合**。
  4. 限流面：两设施关系判定完成（独立设施、缺陷正交、共同根因=不复用平台 DefaultRateLimiter）、平台限流设施全枚举（网关自研桶 / DefaultFlowControlRunner / Sentinel runner / DefaultRateLimiter）——**设施面闭合，无第三设施**。
  5. 零指标族：GraphQL（合规）/gateway（已立案）/auth（合规）/file（立案 C8-010）/graphql-message（低相关登记）——**8 根覆盖完毕**。
- C8 维度四类载体（生产-消费边界、限流实现、并发无上界面、过载可观测性）在 8 源码根内的枚举均已闭合，新增发现均为 P2/P3 且无未探索热点。**判定：本维度可终止，第 3 轮非必要**；若后续修复阶段需要，C8-009 的适配器侧（idle timeout/写超时）需独立适配器维度任务承接。

## 总计（第 2 轮）

- **新增立案**: 2 条（P2×1：C8-009；P3×1：C8-010）
- **维度集外线索**: 本轮新增 0 条（CL-026 起未占用；4 项候选探查后排除，已记录排除理由）
- **既有条目复核**: 6/8 重审（C8-001/002/005/006/007/008 全部维持原判级、无修正项）；C8-003/004 本轮未重审
- **累计（两轮）**: 立案 10 条（P1×2、P2×7、P3×1）+ CL 3 条（CL-012~014）
- **复核状态**: 本轮新增 2 条未复核（待阶段二维度复核）
