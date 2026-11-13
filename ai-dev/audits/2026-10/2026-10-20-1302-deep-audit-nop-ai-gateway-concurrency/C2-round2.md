# C2 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C2 维度深挖第 2 轮盲审员（盲审纪律同第 1 轮；编号续接 C2-009 起）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L145–168（C2 正文）+ L351–377（附录 A）

## 深挖范围与方法

按派单深挖面逐一执行：

1. **rateLimitLock 与其他锁的获取序组合**：`rateLimitLock`（FeishuConnector L136）唯一访问点 `tryAcquireInbound`（L467-474）复核为叶子锁：临界区内仅 ArrayList removeIf/size/add 纯内存操作；`onMessage` 调用序为 `tryAcquireInbound()`（进出 rateLimitLock）→ DB 查找 → `engine.execute` → `deliverSegmented`（feishuClient.sendMessage → FeishuClient 内部状态，无 FeishuConnector 监视器获取）——rateLimitLock 与 `FeishuConnector.this`、`FeishuClient.this` 无任何嵌套边。第 1 轮锁序图补充一条新边（见下），仍无环。
2. **compute 原子区内调用链**：`ConcurrencyRegistry` 两处 compute（L58/L82）复核——lambda 内仅 AtomicInteger get/incrementAndGet/set、条件 throw、map 条目移除；无 I/O、无递归 map 自身、无外部回调。throw 在 compute 内的语义（条目不变、异常传播）已由 javadoc L87 声明且与 CHM 规范一致。**负验证**。
3. **锁内 DB 面（ChannelSessionStoreImpl saveMapping 在什么锁下被调）**：逐一核对两个 DB 触点的持锁状态——`findByChannel`（onMessage L313，Feishu WS 投递线程）调用时 `rateLimitLock` 已释放（tryAcquireInbound 已返回）、无 `FeishuConnector.this`（onMessage 非 synchronized）；`saveMapping`（onExecutionComplete L407，引擎回调线程）无任何模块锁。`updateLastActive`（L321）同无锁。**结论：本模块 DB 操作全部在锁外执行，负验证留痕**。持锁外部 I/O 的两个已知站点（FeishuConnector.start/stop、ensureBridgeSubscribed）第 1 轮已立案（C2-003/C2-006c），不重复。

**锁序图补充（第 2 轮新增边，经 nop-gateway 源码交叉验证）**：

```text
BufferedSubscription.this（nop-gateway）──(handleError→retryCallback.retry)──> ThresholdBreaker per-key CHM bin（经 recordFailure/allowCall）
BufferedSubscription.this ──(startAttempt→lifecycle.onFetchStarted)──> GatewayStreamingLifecycleListener.this ──> ConcurrencyRegistry CHM bin
BufferedSubscription.this ──(startAttempt 内 fetchServerEventFlow + mapped.subscribe)──> HTTP 连接建立（阻塞点）
GatewayStreamingLifecycleListener.this ──> ConcurrencyRegistry CHM bin（onStreamTerminated 单独路径，无反向边）
```

新增边全部单向；`terminate`/`cancel` 路径刻意在释放 BufferedSubscription 监视器**之后**才调 `lifecycle.onStreamTerminated`（BufferedStreamingPublisher L339-357/L184-203），与 `startAttempt` 在监视器**内**调 `onFetchStarted`（L226-228）构成方向一致的边序——无 (listener → BufferedSubscription) 反向边，无环。**但监视器内新增了两个阻塞点（retry 重链路 + HTTP 发起），立案为 C2-009。**

## 新增发现

### [C2-009] GatewayStreamingRetryCallback.retry 全重决策链（VFS 配置解析×3 / 全请求体 JSON 转换 / 凭证链解析 / HTTP 发起）在 nop-gateway 缓冲层监视器内同步执行——下游 demand 管理与取消被重试决策时长串行冻结，且无任何期限上界

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/GatewayStreamingRetryCallback.java:53-142`（重链路本体）；进入点 `nop-service-framework/nop-gateway/.../BufferedStreamingPublisher.java:392-400, 298-326`（AttemptSubscriber.onError 持监视器调 handleError → retry，证据引用）
- **证据片段**:
  ```java
  // BufferedStreamingPublisher.AttemptSubscriber.onError（持有 BufferedSubscription 监视器）:
  public void onError(Throwable throwable) {
      synchronized (BufferedSubscription.this) {          // L393
          ...
          handleError(state, throwable);                   // L398 → L304 retryCallback.retry(...) 同锁内
      }
  }
  // retry() 监视器内依次执行：
  ChatServiceFailoverAdapter.classifyStreamError(error, failedCandidate); // L57 → loadConfig → VFS loadComponentModel
  CircuitObservation.recordFailure(...);                                  // L78 → breaker per-key compute
  FailoverProbeSupport.selectNextWithProbe(...);                          // L87 → 探活遍历 → resolveProviderChainCandidates（VFS）
  interceptor.sinkCandidate(request, next, attempt + 1);                  // L95 → loadConfig ×2 + resolveApiKey 凭证链
  return buildHttpRequest(request, next);                                 // L101 → toBackendRequest 全量 JSON 转换 + 凭证链
  ```
  且 `handleError` 的重订阅分支在同锁内继续 `startAttempt(newRequest)`（L321）→ `httpClient.fetchServerEventFlow` + `mapped.subscribe` → 内链 `onSubscribe` 的 `subscription.request(1)`（StreamingProcessor L144）**同步发起 HTTP 连接建立**。
- **严重程度**: P2
- **并发场景**: T1 = 第 N 次 attempt 的上游错误投递线程（持 BufferedSubscription 监视器执行重试决策全链）；T2 = 下游 SSE 写出线程（`request(n)` 补充 demand / `cancel()` 客户端断连，两方法需同一监视器，L151-203）。与第 1 轮 C2-002（FailoverStreamFlow 监视器内投递）不同站点、不同失效面：本条是**监视器内同步执行重量级外部决策与 I/O**，非信号投递自身。
- **触发时序**: T1 流式 attempt-1 窗口内失败 → 进入 onError 持监视器 → `retry()` 依次执行：分类（`LlmConfigHelper.loadConfig` → `ResourceComponentManager.loadComponentModel`，冷路径为 VFS 文件解析 I/O）、熔断记账、被动重选（探活遍历再触发 `resolveProviderChainCandidates` VFS 加载）、`sinkCandidate`（`loadConfig` 在 L484 与 L518 各一次 + `resolveApiKey` 凭证链解析）、`buildHttpRequest`（又一次 `loadConfig` + `toBackendRequest` 完整 JSON parse/build）→ 重订阅分支同锁内发起 HTTP 连接 → T2 此刻调用 `request(n)`（SSE 写出侧补 demand）或 `cancel()`（用户断连）→ 阻塞于监视器直到 T1 全链完成。交叠后果：流式输出的 demand 管理被冻结时长 = 重试决策 + 连接建立（首次冷加载可达数十 ms 级，凭证链走远端 store 时更长的可能性不可排除——模块内无上界）；provider 抖动（连续窗口内失败）时每次重试都重复该冻结。
- **现状**: 第 1 轮 C2-002 修复建议（临界区只做状态机变更）在网关缓冲层路径无对应落实；`retry()` 契约（返回 HttpRequest）与缓冲层监视器边界（nop-gateway 所有）之间无任何"决策移出监视器"的约定；分类/重选/构造的每个环节各自有第 1 轮已立案的异常面（C3-004 家族），但**持锁时长**面未登记。
- **风险**: 慢 VFS/凭证链/连接建立把缓冲层锁持有时间放大为秒级；期间下游断连取消（cancel）被推迟——取消延迟期间上游 HTTP 流继续消耗；provider 抖动场景下冻结逐 attempt 叠加，用户可感知输出停顿。F5：整链无任何期限设施（复用第 1 轮缺席裁定口径，本条只报锁内重操作面）。
- **建议**: 与 C2-002 同修法——缓冲层 `handleError` 在监视器内仅做窗口判定与缓冲清理，`retryCallback.retry` 调用移出监视器（用 `attempt` 引用交换 + onSubscribe 的 `state != attempt` 守卫已天然防错序，BufferedStreamingPublisher L372/L384/L394 现成）；模块侧配合把 `retry()` 的配置解析/请求体构造结果做 per-request 缓存（同一请求内 provider/config/apiKey 不变，三次 loadConfig 可合并为一次）。
- **失效面枚举（F3）**: 监视器内重量级调用点全量清单（本模块代码在缓冲层锁内的执行面）——①`retry()` 决策链（**本条主面**）；②`startAttempt` 内 `fetchServerEventFlow` + `mapped.subscribe` → HTTP 发起（**同锁内 I/O，本条一并立案**）；③`lifecycle.onFetchStarted` → `registry.acquire`（compute 内轻操作，豁免）；④`handleComplete` → `recordSuccess`（breaker compute 轻操作）+ `flushAll` → 下游 `onNext`（下游回调在锁内——与 C2-002 同族形态但站点为缓冲层，登记为同族待修面，不单独计数）；⑤`terminate` → `onStreamTerminated`（刻意锁外，OK）；⑥`cancel` → `upSub.cancel` + `onStreamTerminated`（L197-202 锁外，OK）。
- **信心水平**: 确定（监视器边界与锁内执行面均为代码事实；"冷路径 I/O 放大"依赖 VFS 缓存状态与凭证实现，冻结时长量级为估算）
- **误报排除**: 非"低频管理路径粗锁合理"——重试链在 provider 抖动时逐 attempt 执行于流式主路径；非 C2-002 重复（不同监视器、不同调用面：投递回调 vs 重试决策）；非平台原语内部实现（执行体是本模块回调代码）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（cra-index 零同站点）；监视器边界逐段回码证实（onError L391-400 持 BufferedSubscription 监视器 → handleError L298-326 → retry 全链 + startAttempt 同锁发起 HTTP）；网关形态生产真实（WI 2.3 C3-003 已裁定缓冲层为 AI 路由现实路径）；冻结时长为估算已如实标注；与 C2-002 同批修复、与 WI 2.3 C3-003 同文件协调

## 维度集外线索（overlay）

—— 本维度第 2 轮无新增集外线索。

深挖面负验证留痕（不立案）：①rateLimitLock 叶子性复核成立（无嵌套边）；②ConcurrencyRegistry compute 原子区轻操作复核成立（第 1 轮 C2 数量结论维持）；③saveMapping/findByChannel/updateLastActive 全部锁外执行（"锁内 DB 面"排除）；④新增锁序边（BufferedSubscription → listener/breaker/registry/HTTP）无环。

## 继续判定

新增 1 条（P2×1，无 P0/P1）。判定：**终止信号**——①第 2 轮唯一新发现的失效面（缓冲层监视器内重链路）与第 1 轮 C2-002 属同族不同站点，修复方案同源（临界区收窄），无独立子代缺陷迹象；②三个定向深挖面（rateLimitLock 组合/compute 原子区/锁内 DB）均以负验证闭合；③锁序图扩充后仍无环，死锁面收敛。建议 C2 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C2 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=1，P3=0；CL 追加=0。*

## 复核结论（WI 3.3 独立复核）

- **C2-009**：保留 P2——监视器边界与锁内执行面（retry 决策全链 + HTTP 发起）逐段回码证实；网关形态生产真实；与 C2-002 同族不同监视器、修复同源（临界区收窄），两监视器建议同批修复；与 WI 2.3 C3-003（同文件越窗无界缓冲）为不同失效面，修复批次协调、零重复计数。
- 深挖面负验证抽查：rateLimitLock 叶子性、ConcurrencyRegistry compute 原子区轻操作、saveMapping/findByChannel/updateLastActive 全锁外——抽验成立。
- 第 2 轮终止信号判定维持；C2 维度两轮共 9 条复核后终判 P1×1 / P2×2 / P3×6，零驳回。
