# C1 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C1 维度深挖第 2 轮盲审员（未读 `ai-dev/audits|plans|bugs|lessons|backlog` 第 1 轮七文件之外的内容；未查 git log）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118（F1–F11 + 共享前缀口径）+ L119–143（C1 正文）+ L351–377（附录 A）；编号续接第 1 轮（C1-009 起）

## 深挖范围与方法

按派单深挖面逐一执行，覆盖声明：

1. **登录态（ChannelLoginScanProcessor 状态机并发转移）**：全文精读 `login/ChannelLoginScanProcessor.java`（198L）与 `login/ChannelLoginApiBizModel.java`（178L）。Processor 为 per-request 实例（`loginByScanAsync` L166 每次新建），五类载体零命中：无实例可变字段（4 个 final 字段 + 1 个启动期 `@InjectValue` long）、无 static 可变状态、无 ThreadLocal、无惰性缓存。bind ticket / MfaChallengeStore / IUserContextCache 的状态机本体在 nop-auth 侧（越界）。`providersByType`（LinkedHashMap）与 C1-007 同站点同判定（装配期写 + 运行期只读），无新失效面。`setChannelBindProviders` 的 clear-then-fill 窗口仅装配期可达，与 C1-007 合并留痕。
2. **CandidateHealth 健康表并发**：`CandidateHealth` 为不可变值快照（3 final 字段）；`CandidateHealthProvider.healthOf` 每次调用重建快照，无共享可变状态。发现 C1-010 立案其 null 兜底构造面。快照内 breaker 状态与并发计数两次读非原子——属 owner doc 已声明的"近似观测"契约族（第 1 轮口径延续），不立案。
3. **ModelClassRouter 路由表**：`attempted`（LinkedHashSet）/`extendedCandidates`/`providerChainExtended` 三可变字段逐一核对消费线程：主动路径 = HTTP worker（onRequest/invoke/callAsync 首选）；被动路径 = 失败决策线程（whenComplete 完成线程 / 上游 onError 线程 / GatewayStreamingRetryCallback.retry 线程）。三条编排链内 router 的使用在信号序上串行（首选择在 fetch 前、重选在终态信号后），网关侧经 `svcCtx.setAttribute(ATTR_ROUTER)`（onRequest L212）交给缓冲层后在 retry 内消费——同一流内无并发写者。javadoc "单线程 per-call 独占"声明复核成立，**负验证，不立案**。
4. **会话映射缓存并发面**：`ChannelSessionStoreImpl`（158L）精读——全部方法经 `ormTemplate.runInSession` 作用域执行，无实例缓存字段；并发首聊 check-then-insert 面第 1 轮已立案（C1-005/C2-005/C3-006/C6-004 四视角），本轮复核 `updateLastActive` 的 load+modify+update 单 OrmSession（L108-118）在并发同 chat 下为 lastActiveAt 时间戳丢更新——良性（单调时间戳），负验证留痕。
5. **dedup/滚动窗口深层竞态**：`tryAcquireInbound`（L464-475）锁内 evict+count+add 原子性复核成立；新增核对的深层竞态——(a) dedup 键提取在准入**之后**（L336 晚于 L301），Stream 重投递每次消耗限流窗口额度（编号扩展见文末）；(b) 同一 message_id 的并发重投（首份在途时重投到达）依赖引擎 duplicate-input 契约仲裁（nop-ai-agent 侧，越界留痕）；(c) `isBotMentioned`/`extractJsonTextField` 纯只读字符串扫描，无共享状态。均负验证或既有条目扩展。

**跨模块语义取证**（证据面，非立案对象）：`ApiRequest.properties` 存储类型实证为懒建裸 `HashMap`（`nop-kernel/nop-api-core/.../ApiRequest.java:31,81-98`，非 volatile 非并发容器）；`BufferedStreamingPublisher`（nop-gateway）的监视器边界与信号路由全文核对；`ai-gateway-defaults.beans.xml` 全文核对装配注入面。

## 新增发现

### [C1-009] ApiRequest.properties（裸 HashMap）被用作流式 per-attempt 跨线程状态通道——sinkCandidate 写与 onStreamElement 每 chunk 读之间无任何模块内同步原语

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:473-492`（写方 sinkCandidate）；`:429-447`（读方 onStreamElement）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/AiDialectBackendMessageConverter.java:99-168`（读方转换链）
- **证据片段**:
  ```java
  // 写方（重执行回调线程，经 BufferedStreamingPublisher 监视器进入）：
  void sinkCandidate(ApiRequest<?> request, ModelClassCandidate candidate, int attempt) {
      request.setProperty(PROP_PROVIDER, candidate.getProvider());   // L474
      request.setProperty(PROP_MODEL, candidate.getModel());         // L475
      ...
      request.removeProperty(GatewayStreamingConstants.PROP_BASE_URL); // L482
  // 读方（新 attempt 的 SSE 投递线程，不持任何与本写方共同的锁）：
  metrics.onStreamElementConverted(request.getStringProperty(PROP_PROVIDER),   // L443
          request.getStringProperty(PROP_MODEL), request.getStringProperty(PROP_ACCOUNT_KEY));
  return requireConverter().toFrontendStreamChunk((Map<String, Object>) element, request); // L446
  ```
  `ApiRequest.setProperty` 实现为懒建 `HashMap.put`（ApiRequest.java:81-89），无 volatile、无并发容器。
- **严重程度**: P2（按纪律标注：跨 attempt 可见性链依赖跨模块隐式 happens-before，理论风险未具体化为必然交错）
- **并发场景**: T1 = 第 N 次 attempt 的上游错误投递线程（`BufferedStreamingPublisher.AttemptSubscriber.onError` → `handleError` → `retryCallback.retry` → `sinkCandidate`，该线程持有 BufferedSubscription 监视器）；T2 = 第 N+1 次 attempt 的 HTTP SSE 投递线程（`StreamingProcessor.createMappedPublisher.onNext` L156 → `proceedOnStreamElement` → 属性读取，**先于**进入 BufferedSubscription 监视器执行）。
- **触发时序**: T1 在监视器内执行 `sinkCandidate` 的 5 次 put + 2 次 remove + headers 写（普通 HashMap 写，监视器仅对同锁竞争者建立 happens-before）→ T1 发起新 fetch（fetchServerEventFlow）→ T2 在**新的**投递线程上收到 attempt N+1 首 chunk，在进入任何共同同步点之前于 L443-446 / `resolveBackendStyle`(L115)/`resolveConfig`(L149)/`resolveRequestModel`(L135-136)/`resolveStream`(L166) 读取属性 → 交叠后果：T1 的写对 T2 的可见性在 JMM 下无模块内同步边保证（唯一候选 HB 链 = nop-http 客户端请求提交→响应投递的内部实现），弱序架构（本仓库 aarch64 生产形态）上切号后首段 chunk 可能读到旧 attempt 的 PROP_API_STYLE/PROP_MODEL → 反向转换用错 backend dialect / 指标归因错号。同 attempt 内读写由 RS 信号序串行（写发生在该 attempt onError 后），无竞态。
- **现状**: B-10 契约（AiDialectBackendMessageConverter javadoc L100-101"重执行回调已同步更新 properties"）声称的"同步"实际仅为同 attempt 信号序；跨 attempt 的传递通道是裸 HashMap，模块内无任何声明或防御。
- **风险**: 切号后首段流式元素方言/模型错配（前端解析异常或格式错乱），概率低但长时运行下必现；无复现确定性，事后难归因。
- **建议**: `sinkCandidate` 写入与 `onStreamElement` 读取之间建立显式可见性——最小修复 = retry 路径在 `startAttempt` 前（仍在监视器内）对属性通道做一次 volatile 写栅栏（如 `ApiRequest` 引用经 volatile 字段重发布），或网关缓冲层在AttemptSubscriber.onSubscribe（监视器内）主动读取一次属性建立 HB；根治 = per-attempt 属性快照对象（不可变，经构造器安全发布）替代共享可变 properties 通道。
- **失效面枚举（F3）**: 属性通道全部读点逐一核验——①`onStreamElement` L443-444 + `toFrontendStreamChunk` 转换链 4 读点（**跨线程，本条缺陷面**）；②`GatewayStreamingLifecycleListener.onFetchStarted` L42-43（首次 = onRequest 同线程；重订阅 = retry 同线程——均与写同线程，OK）；③`onError`/`onStreamComplete` 的 `currentCandidate` 读（与写同在错误/完成投递线程，信号序串行，OK）；④非流式 `invokeNonStreamingAttempt` 的 sink（whenComplete 完成线程写 + 该线程内重新 proceedInvoke 读，同线程，OK）；⑤`buildStreamingHttpRequest.applyBaseOverride` 读 PROP_BASE_URL（首次 onRequest 同线程；重试 URL 由 retry 内 buildHttpRequest 构造不经此路径，OK）。写点唯一 = `sinkCandidate` + retry 的 `setProperty(PROP_ATTEMPT)`（L63）。
- **信心水平**: 很可能（缺陷形态为 JMM 可证的可见性缺口；实际表象依赖 nop-http 内部同步实现与 CPU 弱序，未具体化必然复现序列——按纪律标注理论风险）
- **误报排除**: 非"无状态单例并发调用"豁免——per-request 的 ApiRequest 是跨线程共享可变载体；非"HashMap 原语使用"误报——指控的是 B-10 契约的同步缺失而非容器选择本身；与第 1 轮全部条目无同站点重叠（第 1 轮未触及 properties 通道）。
- **复核状态**: 未复核
- 复核状态: 降级(P3)｜新发现；"模块内无显式同步边"事实成立，但 JMM 传递性覆盖该可见性缺口（T1 程序序的属性写先于 HTTP 提交完成；响应投递必然经 nop-http 客户端内部同步移交至投递线程，传递性使写对读可见）——具体失效交错需以 nop-http 客户端内部存在数据竞争为前提，初审未能写出不依赖该前提的序列；降格为隐式契约脆弱性（防御性加固候选），其余 F3 读点核验复核成立

### [C1-010] ChatServiceFailoverAdapter.routerFor 缺 D8 fail-fast 守卫——breaker/registry 未注入时经 CandidateHealthProvider 兜底构造静默退化为 per-call 局部状态（并发上限与熔断全模块失守）；同族守卫两站点不对称

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:371-374`（无守卫站点）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:541-553`（有守卫对照站点）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/CandidateHealthProvider.java:26-29`（兜底构造使能者）
- **证据片段**:
  ```java
  // ChatServiceFailoverAdapter.routerFor —— 无任何 null 检查：
  private ModelClassRouter routerFor(ChatRequest request) {
      ChatOptions options = request != null ? request.getOptions() : null;
      return ModelClassRouter.forRequest(request, options, strategy, breaker, registry);  // L373
  }
  // CandidateHealthProvider 构造器 —— null 静默兜底为"每 router 一套"新实例：
  this.breaker = breaker != null ? breaker : new ThresholdBreaker();       // L27
  this.registry = registry != null ? registry : new ConcurrencyRegistry(); // L28
  // 对照·AiGatewayFailoverInterceptor.routerFor（D8 守卫在场）：
  if (breaker == null || registry == null) { throw new NopAiCoreException(...); }  // L542-547
  ```
- **严重程度**: P2
- **并发场景**: 全部请求线程 ×（漏配装配或手工构造）。标准 beans 装配（`nopChatServiceFailoverAdapter` 以非 optional ref 注入 breaker/registry）不触发；触发面 = 部署侧自建适配器 bean 漏配 ref、手工 `new ChatServiceFailoverAdapter()` 后直接投产、或未来装配重构遗漏。
- **触发时序**: T1…Tn 各请求线程并发调用 `callAsync/callStream` → `routerFor` 传入 `breaker=null, registry=null` → 每个 router 经 `CandidateHealthProvider` 兜底**各自 new** 一套 ThresholdBreaker + ConcurrencyRegistry → 交叠后果：①每个请求的健康视图恒为 CLOSED/count=0，`isConcurrencySaturated` 永假——**per-账号并发上限对上游完全失守**（限流语义静默消失，上游被无界放行）；②熔断失败计数每请求归零，ThresholdBreaker 永不 OPEN；③`FailoverStreamFlow.releaseAttempt`/`whenComplete` 的 release 打在各自私有 registry 上——配对仍成立（同 registry acquire/release），无下溢异常，**失效全程零错误零日志**。
- **现状**: D8 契约明文（beans.xml 注释与拦截器 guard 消息"缺省构造会静默退化为 per-call 局部状态"）证明该失效模式是模块已知风险，但守卫只落在网关形态一个入口，本地形态入口裸奔——同族守卫不对称（F10 守卫缺失型）。
- **风险**: 装配漂移（重构/手写 beans/测试构造外溢）使 nopChatServiceFailoverAdapter 以"看似工作"的状态运行：无限流、无熔断、无跨请求健康记忆——上游 LLM 配额被打穿时网关不切换不熔断，全部流量持续打向首个候选。静默失效使故障只能在 provider 账单侧发现。
- **建议**: `ChatServiceFailoverAdapter.routerFor` 复制拦截器同款 D8 fail-fast（`breaker == null || registry == null` → `ERR_AI_AGENT_INVALID_ARG`）；同时评估 `CandidateHealthProvider` 的 null 兜底构造改为 fail-fast（该兜底是缺陷使能者，"null = 默认实例"的便利语义与 D8 契约直接冲突）。
- **失效面枚举（F3）**: `ModelClassRouter` 构造入口全量清单——①`ChatServiceFailoverAdapter.routerFor` L371-374（**守卫缺失，本条**）；②`AiGatewayFailoverInterceptor.routerFor` L541-553（守卫在场，OK）；③`CandidateHealthProvider` 构造器 L26-29（null 兜底使能者，建议一并收口）；④`ModelClassRouter` 构造器内 `new CandidateHealthProvider(breaker, registry)` L74（传递面，无独立判定）。测试构造形态（单测直接 new）依赖兜底构造——收紧时需同步修测试装配。
- **信心水平**: 确定（代码事实：守卫不对称 + 兜底构造路径完整可证；触发依赖装配形态）
- **误报排除**: 非"低频启动路径粗同步合理"——立案面是请求路径的守卫缺失而非启动同步；非"已声明边界"——D8 契约明文要求注入单例，本条恰是该契约的执行缺口；未按第 1 轮 C1-007 同款 P3 降档的原因：后果是整模块并发防护静默失效而非结构性潜伏（该站点无生产触发面现值——标准装配已注入——但 C1-007 的后果为结构破坏理论面，本条后果为防护全失，取 P2）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现；三代码事实回码证实（adapter routerFor L371-374 无守卫／interceptor L541-553 守卫在场／CandidateHealthProvider L26-29 null 兜底）；beans.xml 标准装配以非 optional ref 注入证实不受影响；触发面 = 装配漂移/手工构造，静默全局失效 + 一行守卫，维持 P2（与 C1-007 判级差异理由成立）

## 维度集外线索（overlay）

—— 本维度第 2 轮无新增集外线索（CL 流水由 C7/C8 文件续接 CL-022/CL-023）。

深挖面负验证留痕（不立案）：①登录态 Processor per-request 实例化，五类载体零命中；②CandidateHealth 不可变快照 + 近似观测已声明；③ModelClassRouter 三链串行复核成立；④updateLastActive 时间戳丢更新良性；⑤同 message_id 并发重投仲裁在引擎侧（越界）；⑥`FeishuConnector.capabilities`/`rateLimitPerMinute` 已在第 1 轮裁定，无新面。

## 继续判定

新增 2 条（P2×2，无 P0/P1）。判定：**终止信号**——①两条新发现均为低触发面（理论可见性链 / 装配漏配潜伏面），主站点（FailoverStreamFlow 启动窗、FeishuConnector 启动窗）在第 1 轮已穷尽且本轮负验证未再产出新面；②新增条目数 < 3 且无 P0/P1；③五类载体清单在第 2 轮全覆盖后收敛（登录态/路由表/健康表/会话缓存/dedup 五个定向面均以负验证或既有条目扩展闭合）。建议 C1 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C1 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=2，P3=0；CL 追加=0。*

## 复核结论（WI 3.3 独立复核）

- **C1-009**：降级 P2→P3——JMM 传递性论证（属性写先于 HTTP 提交、读因果后于提交经客户端内部同步移交），失效交错需以 nop-http 内部数据竞争为前提；模块内显式同步边缺失的契约脆弱性成立，修复建议保留为防御性加固候选。详见 C1-shared-mutable-state.md「维度复核结论」。
- **C1-010**：保留 P2——三代码事实（守卫不对称 + null 兜底使能者）与 beans.xml 标准装配排除均回码证实；判级差异论证（vs C1-007）经复核成立。
- 深挖面负验证抽查：ModelClassRouter 三链串行复核、登录态 Processor per-request 五类载体零命中、CandidateHealth 不可变快照——抽验成立。
- 第 2 轮终止信号判定维持；C1 维度两轮共 10 条复核后终判 P1×1 / P2×1 / P3×8，零驳回。
