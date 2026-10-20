# C10 测试保护力 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计对象**: `nop-ai/nop-ai-gateway`（CRA WI 3.3 最后批次维度，单轮）
- **输入**: C1/C2/C3/C4/C6/C7/C8 七维度 `## 最终保留项` 合计 48 条（P0×0 / P1×4 / P2×16 / P3×28，与各维度复核判定统计一致：C1×10 + C2×9 + C3×8 + C4×7 + C6×5 + C7×5 + C8×4）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文（L329-347）+ 附录 A（L351-377）；变异推演口径——对每条保留发现回答"按各维度修复方向补上正确实现后，现有哪个测试会红？"

## 审计范围与口径

**测试树基线**：`nop-ai-gateway/src/test/java` 共 31 个 java 文件（failover 11、channel 12、routing 6、login 2、gate 1，含 3 个共享 harness/Support 文件）。真实并发压力测试仅 3 处：`TestConcurrencyRegistry.concurrentAcquireReleaseIsThreadSafe` / `concurrentMismatchedReleaseDoesNotLeakPhantomCount`（8 线程×500 对）、`TestChannelMessageService.backboneConcurrentFirstDispatchDedupsBridgeNoDuplicateFanOut`（64 线程首派发）。

**判定三态的操作定义**（本文件口径，变异推演 = 把"正确实现"视为变异体，观察现有套件反应）：

- **会红·有保护**：存在直接回归测试，其断言与缺陷本体正确语义一致，且缺陷触发场景在测试时序中被确定性执行——缺陷重现/修复回退会使该测试转红。
- **假保护**：存在名义上覆盖缺陷站点的测试，但其断言钉住的恰是缺陷赖以成立的时序/语义——修复本体（按维度修复方向）会使该测试转红，须同步改写；套件呈现覆盖假象而保护力为零。
- **无测试**：缺陷触发场景在全测试树中不可达或不可观测——修复与回退均无任何测试转红。检索纪律：每条给出具体检索路径（文件:line 或 grep 口径）。

**关键测试基建事实**（矩阵判定的共同依据）：

1. **全测试树无有界 demand 订阅者**：`FailoverTestSupport.CollectingSubscriber.onSubscribe` 恒 `subscription.request(Long.MAX_VALUE)`（FailoverTestSupport.java:399-401；`CollectingSubscriberWithSubscribe`:622、`TestChatServiceFailoverAdapterStreaming.cancelOrderInternalSubscriptionBeforeAttemptToken` 匿名订阅者:529 同款）。grep `request(` 全树过滤 `MAX_VALUE` 后仅命中测试辅助方法名，零有界 request 用例 → `FailoverStreamFlow.deliver/flushBuffer` 的 `demand==0` 分支（L380-381）在测试中**不可达**。
2. **取消测试只锚定两端点**：`callerCancelStopsCurrentAttemptOnly`（TestChatServiceFailoverAdapterStreaming.java:425-449）在 `awaitStreamCallCount(2)` 后取消 = attempt 已装配；`callerCancelledBeforeStartNoFetch`（:452-467）= 前置已取消。acquire（FailoverStreamFlow L263）→ `publisher.subscribe`（L288）中间窗口无测试可达，`upSub` 全树 grep 零引用（无观测点）。
3. **生产 `AttemptCancelToken` 零测试触达**：grep `AttemptCancelToken` 全树零命中；流式取消测试用测试自建 `SettableCancelToken`（TestChatServiceFailoverAdapterStreaming.java:627-664）。
4. **渠道栈时序被端点测试钉死**：`close()`（ChannelMessageServiceImpl）全树零调用；`startAll/stopAll` 仅 E2E tearDown/setup 手工调用（TestFeishuConversationE2E.java:129/136、TestChannelProactiveNotifyE2E.java:108/115）；`onMessage` 在测试线程直接内联调用后立即断言（TestFeishuConnector 6+ 处）。
5. **无 beans.xml 生命周期接线断言**：IoC 测试（TestGatewayAutoAssemblyIoC/TestChannelMessageServiceIoC/TestFeishuConnectorIoC/TestIChannelConnectorWiring）断言 bean 存在性与注入，不断言 destroy-method/init-method；`TestInvariantGate2GatewayTimeout` 仅护 `orTimeout` marker 存在性，其 javadoc 自证对 AR-4 cancel 语义"零判别力"（:41-43、:285-287）。

## 保护力矩阵

| # | 发现 ID（终判） | 覆盖判定 | 证据（测试文件:line / 检索路径） |
|---|---|---|---|
| 1 | C1-001（P1 窗口内失败清理与 request 冲刷竞态） | 无测试 | `endToEndInWindowFailureResubscribeAndPassThrough`（TestChatServiceFailoverAdapterStreaming.java:109-149）断言 Major-1 前缀丢弃正确语义（:139-140），但为单订阅顺序时序，未构造"读 inWindow 出监视器→并发 flushBuffer"交错；demand 恒 MAX（基建事实 1）→ demand==0 越窗入缓冲路径不可达；修复（clear 入监视器+attempt-identity 守卫）后全树零转红 |
| 2 | C1-010（P2 装配守卫不对称） | 无测试 | 全部 adapter/interceptor 测试以非空 breaker/registry 构造（TestChatServiceFailoverAdapterStreaming.java:80-82 等）；无"缺依赖构造 → fail-fast"用例；`TestGatewayAutoAssemblyIoC.java:87-88/118-119` 仅断言标准装配存在性；修复加守卫零转红 |
| 3 | C1-005（P3 同 chat 首会话竞态/休眠面） | 无测试 | `TestChannelSessionStore` 全部单线程（L130-191）；`TestFeishuConversationE2E.e2eSessionReuseAcrossTwoMessages`（:225-241）顺序双消息；grep `saveMapping` 全树无并发/重复保存用例；修复 upsert 零转红 |
| 4 | C1-006（P3 capabilities 潜伏/RESERVED 零消费） | 无测试 | grep `.getCapabilities()` 消费方全仓零命中（仅接口/实现声明 + 测试 stub，TestIChannelConnectorWiring.java:115）；无缓存可见性测试；修复 volatile/饿汉零转红 |
| 5 | C1-007（P3 register/setConnectors 可见性守卫） | 无测试 | `TestChannelConnectorManager`（L31-201）全部单线程，register→lookup 单线程断言（:76-85）；无并发注册/查询用例；修复守卫零转红 |
| 6 | C1-002（P3 条件化 started 先于 feishuClient.start） | 无测试〔随本体回升〕 | `TestFeishuConnector.startRegistersInboundHandlerWithFeishuClient`（:283-289）单线程顺序断言；无 start×首消息交叠用例 |
| 7 | C1-003（P3 AttemptCancelToken check-then-act） | 无测试 | 基建事实 3：grep `AttemptCancelToken` 全树零命中；测试用自建 token 替身（TestChatServiceFailoverAdapterStreaming.java:627-664）；修复 add 后二次复核零转红 |
| 8 | C1-004（P3 条件化 volatile/close 同锁） | 无测试〔随本体回升〕 | 基建事实 4：`close()` 全树零调用（grep channel 包 `.close()` 仅 h2Store/factoryBean 命中）；无 dispatch×close 交叠用例 |
| 9 | C1-008（P3 并入 C2-001） | 无测试 | 随 C2-001（矩阵 #11） |
| 10 | C1-009（P3 per-attempt 属性快照/防御性加固） | 无测试 | grep 全树无 attempt 属性可见性断言（`upSub` 零引用）；修复快照/volatile 零转红 |
| 11 | C2-001（P1 取消×重订阅计数泄漏） | 无测试 | 竞态窗口（L263 acquire 后、L288 装配前）测试不可达：`callerCancelStopsCurrentAttemptOnly`（:425-449）取消已装配 attempt（teardown 路径，count 归零断言 :446-447 绿）；`callerCancelledBeforeStartNoFetch`（:452-467）前置消不入窗口；`upSub` 零引用无观测点；修复（onSubscribe 补 releaseAttempt）后全树零转红 |
| 12 | C2-002（P2 StreamSubscription 监视器内投递） | 无测试 | 全部流式测试单订阅者 + 满 demand，无并发 onNext 竞争者可观测锁持有时长/延迟；修复（投递移出监视器）零转红 |
| 13 | C2-009（P2 BufferedSubscription 监视器内 retry 全链） | 无测试 | 网关流式测试（TestAiGatewayFailoverInterceptorStreaming 全部 13 用例）经缓冲层但无锁内冻结时长断言（无计时断言，见 TestFailoverMetricsLocal javadoc :44）；修复零转红 |
| 14 | C2-007（P3 capabilities 零消费） | 无测试 | 同矩阵 #4 |
| 15 | C2-008（P3 AttemptCancelToken 掩蔽面） | 无测试 | 同矩阵 #7 |
| 16 | C2-003（P3 条件化 start/stop 锁内 I/O） | 无测试〔随本体回升〕 | `TestFeishuConnectorIoC` 仅装配验证；无 start/stop 并发交叠用例；修复 AtomicBoolean 零转红 |
| 17 | C2-004（P3 条件化 startAll/stopAll 无互斥） | 无测试〔随本体回升〕 | `TestChannelConnectorManager.stopAllInvokesEachConnectorStopInReverseOrder`（:54-73）单线程 LIFO 断言；无交叠用例；修复 synchronized 零转红 |
| 18 | C2-005（P3 条件化 saveMapping 无互斥） | 无测试〔随本体回升〕 | 同矩阵 #3（C6-004 链） |
| 19 | C2-006（P3 条件化 dispatch/close/subscribe 三面） | 无测试〔随本体回升〕 | `backboneConcurrentFirstDispatchDedupsBridgeNoDuplicateFanOut`（TestChannelMessageService.java:318-352）64 线程触达惰性建池/ensureBridgeSubscribed 站点，但断言（1 订阅 + n 交付，:345/350-351）对 DCL 撕裂/泄漏执行器不可观测；close 面零调用 |
| 20 | C3-001（P1 流式终态冲刷截断） | 无测试 | `completionDuringWindowDeliversBufferedItems`（TestChatServiceFailoverAdapterStreaming.java:327-338）断言"窗口期完成缓冲全量交付"正确语义，但满 demand 下 flushAll 立即排空，缺陷前提（缓冲非空 ∧ demand==0，L465-483 无终态挂起）不可达（基建事实 1）；修复（pendingComplete 挂起）后该测试仍绿 |
| 21 | C3-003（P2 取消误分类记账污染） | 无测试 | `callerCancelledBeforeAttemptTerminatesCleanly`（TestChatServiceFailoverAdapterNonStreaming.java:403-434）仅覆盖入口前置检查；无中途取消→分类→记账用例；metrics 测试（TestFailoverMetricsLocal/Gateway）无取消场景；修复（入口判 isCancelled）零转红 |
| 22 | C3-004（P2 终态回调护栏缺失→future 永不完成） | 无测试 | grep 全树无"whenComplete 回调抛异常"注入 stub；FailoverTestSupport fake 路径不抛；`TestFailoverMetricsGateway` 计数平衡断言（:145）在回调不抛前提下恒绿；修复护栏零转红 |
| 23 | C3-005（P3 attempted 不可回收） | 无测试 | `budgetExhausted*` 用例断言 fail-loud 终态（TestChatServiceFailoverAdapterStreaming.java:274-288），不断言超限后 revisit 语义；修复（remove 通道/revisit）零转红 |
| 24 | C3-006（P3 同 chat 首消息双会话） | 无测试 | 同矩阵 #3（C6-004 链并发面） |
| 25 | C3-007（P2 降级路径绕过窗口丢弃） | 无测试 | `nonTransientStreamingErrorDegradesToTerminalResponse`（TestAiGatewayFailoverInterceptorStreaming.java:351-369）钉降级契约但场景为**空缓冲**（error 前零元素）；`windowOutFailureStreamsErrorWithoutSwitch`（:374-392）为越窗断流非降级；"已缓冲未越窗前缀 + 降级 wrapper 同流交付"组合全树缺位；修复选项 2（缓冲层丢弃未越窗缓冲）零转红（选项 1 见反向钉死 #3） |
| 26 | C3-008（P3 retry 属性/指标先行漂移） | 无测试 | `streamingNoConfigReselectAbortsRetryWithWarn`（:256-290）断言 0 第二 fetch + WARN + registry 归零，不断言 switch/resubscribe 计数归属（metrics 漂移面零断言）；修复零转红 |
| 27 | C3-002（P3 并入 AttemptCancelToken 三登记） | 无测试 | 同矩阵 #7 |
| 28 | C4-001（P2 条件化 孤儿池/destroy-method） | 无测试〔随本体回升〕 | 基建事实 5：无 destroy-method 接线断言测试；grep 全树 beans 断言仅存在性/注入；修复 destroy-method 零转红 |
| 29 | C4-002（P2 条件化 无界队列×非协作挂死池耗尽） | 无测试〔随本体回升〕 | `TestChannelFanOutTimeoutCancel.hangingListenerIsCancelledAndPoolThreadReleased`（:86-131）javadoc 明示以**协作监听器**为前提（:26-28），断言恰为"缺陷不存在时"的行为（中断后池可复用 :123-125）；非协作挂死 + 队列积压无用例；`TestInvariantGate2GatewayTimeout` 自证 marker 计数对 cancel 语义零判别力（:41-43）；修复（有界队列+拒绝策略）零转红（单任务不入队积压） |
| 30 | C4-003（P3 条件化 close 不完整/REE） | 无测试〔随本体回升〕 | 同矩阵 #8（close 零调用）；grep 无 RejectedExecutionException 用例 |
| 31 | C4-004（P2 条件化 连接器生命周期断链） | 无测试〔随本体回升〕 | E2E 手工 startAll/stopAll（TestFeishuConversationE2E.java:129/136）不验证容器关停自动调用；无 destroy-method 断言（同矩阵 #28） |
| 32 | C4-005（P2 条件化 WS 接收线程内联阻塞） | 假保护〔随本体回升〕 | TestFeishuConnector 6+ 处在测试线程调 `connector.onMessage` 后**立即断言**（`messageIdExtractedIntoIdempotencyKey`:125-132、`fullInboundToOutboundRoundTrip`:148-169、`rateLimitGuardSuppressesEngineExecuteWhenExceeded`:352-368 等）——钉死"onMessage 同步内联执行"时序；缺陷本体（ACK 延迟放大）无断言；修复（入口线程交接）会使立即断言偶发失败，须同步改写 |
| 33 | C4-006（P3 条件化 DCL 缺 volatile） | 无测试〔随本体回升〕 | 矩阵 #19 同站点：64 线程测试触达惰性建池但双实例泄漏对交付计数断言不可见；修复 volatile 零转红 |
| 34 | C4-007（P3 注入执行器无契约校验） | 无测试 | `TestChannelFanOutTimeoutCancel` 注入合规池正常路径；无错误类型执行器注入的契约断言；javadoc 修复面零转红 |
| 35 | C6-004（P2 条件化 会话映射半个成功，链主） | 无测试〔随本体回升〕 | `TestChannelSessionStore`（真实 H2 + UK）全单线程；`updateLastActiveOnMissThrowsRatherThanSilentNoop`（:180-184）钉 update 面 miss 抛错，`findByChannel` 的 error≡miss 吞噬面无用例；无重复 saveMapping/UK 冲突/遗弃登记用例；修复 upsert+error 区分零转红 |
| 36 | C6-005（P3 登录会话引导存量） | 无测试 | `TestChannelLoginApi`（10 用例）/`TestChannelLoginAccessCode`（4 用例）功能面；grep 无存量残留/终态指标/回收断言 |
| 37 | C6-001（P3 条件化 成组生命周期编排） | 无测试〔随本体回升〕 | `TestChannelConnectorManager` RecordingConnector.start/stop 不抛（:149-160）；无"start 抛→逆序补偿"“stop 抛→隔离"用例；修复零转红 |
| 38 | C6-002（P3 条件化 停机在途排空） | 无测试〔随本体回升〕 | E2E stopAll 仅在 tearDown 且无在途 future（TestFeishuConversationE2E.java:132-140）；无"stop 时在途执行"断言 |
| 39 | C6-003（P3 条件化 close/派发竞态） | 无测试〔随本体回升〕 | 同矩阵 #8/#30（close 零调用 + REE 零用例） |
| 40 | C7-001（P2 并入 C3-004） | 无测试 | 同矩阵 #22 |
| 41 | C7-002（P2 流式窗口/时长指标墙钟） | 无测试 | 窗口判定/时长 4 处墙钟（FailoverStreamFlow.java:154/360/424/477）；测试时钟单向推进（interEventDelayMs 正向 sleep），无回拨/大步进场景；metrics 测试明示放弃计时值断言（TestFailoverMetricsLocal javadoc :44）；修复 nanoTime 零转红 |
| 42 | C7-003（P3 条件化 入站限流窗墙钟） | 无测试〔随本体回升〕 | `rateLimitGuardSuppressesEngineExecuteWhenExceeded`（TestFeishuConnector.java:352-368）断言计数语义（rateLimitPerMinute=2 直改字段），时钟回拨/未来时间戳无用例；修复单调钟零转红 |
| 43 | C7-004（P2 条件化 并入 C4-002） | 无测试〔随本体回升〕 | 同矩阵 #29 |
| 44 | C7-005（P2 选择面绕过 allowCall） | 无测试 | `TestDefaultSelectionStrategy` 六项语义（L40-110）全用长冷却 breaker（60s）→ allowCall 与 isAvailable 同值，未区分"冷却期满 OPEN 应放行"与"HALF_OPEN 视同完全可用"；探活测试（TestChatServiceFailoverAdapterNonStreaming.probePoolIncludesProviderChainCandidates:315-332）断言 HALF_OPEN 推进属探活面非选择面；修复（门控补 allowCall 复查）零转红 |
| 45 | C8-001（P1 越窗零需求无界缓冲 + request(MAX)） | 无测试 | 基建事实 1：全树订阅者恒 request(MAX) → `state.buffer.add`（demand==0 分支，FailoverStreamFlow L380）与上游 request 降档面零触达；`outOfWindowFailureBreaksStreamWithoutSwitch`（TestChatServiceFailoverAdapterStreaming.java:293-322，setBufferSize(2)）亦在满 demand 下运行；修复（越窗硬上限/高水位门）零转红 |
| 46 | C8-002（P2 条件化 超时锚定/取消后半执行/观测混淆） | 无测试〔随本体回升〕 | `TestChannelFanOutTimeoutCancel` 即时派发（无队列积压 :107-113）→ orTimeout 锚定差异不可观测；无"超时后任务仍完整执行"断言、无 dropped/exec-timeout 日志区分断言；修复（deadline 移至执行开始）零转红 |
| 47 | C8-003（P3 条件化 出站同步无界发送） | 假保护〔随本体回升〕 | `executeResultTriggersFeishuReply`/`executeFailureTriggersErrorReply`/`longTextReplyIsSegmentedByMaxMessageLength`（TestFeishuConnector.java:186-205/308-329）+ `e2eLongTextSegmented`（TestFeishuConversationE2E.java:281-300）在 complete 后**立即断言** sendMessageCount——钉死"完成线程同步串行发送"时序；无超时/限流/隔离断言；修复（dedicated executor + deadline）须同步改写该测试簇 |
| 48 | C8-004（P3 Retry-After 信号链断裂） | 无测试 | grep `retryAfter|Retry-After` 全树零命中；429 用例（TestAiGatewayFailoverInterceptorNonStreaming.nonStreamingRateLimited429SwitchesAccount:230-254）断言切换成功，不断言退避/静默行为；修复零转红 |

**矩阵统计**：会红·有保护 **0** ／ 假保护 **2**（#32 C4-005、#47 C8-003，均为时序钉死型）／ 无测试 **46**。
子注：#1/#11/#20/#25/#45 五条 P1/P2 live 面存在"语义面部分覆盖"（Major-1 丢弃、取消后计数归零、窗口期完成全量交付、降级契约等**正确语义已被断言**），但缺陷触发时序在测试中不可达——按操作定义计入无测试，登记中标注部分覆盖面。

## 反向钉死检查

对任务点名的三个重点面逐一核查"测试断言的正是缺陷语义（修复会打红）"：

**命中（修复须同步改写测试）：**

1. **fan-out 测试 → C4-005/C8-003（强钉死）**：TestFeishuConnector / TestFeishuConversationE2E 的"complete 后立即断言 sendMessageCount"与"onMessage 后立即断言 executeCount"钉死了 WS 接收线程内联执行与完成线程同步串行发送——恰是 C4-005（入口线程交接）与 C8-003（dedicated executor + deadline）修复方向所要改变的时序。受影响用例 ≥8（TestFeishuConnector:125-132/148-169/186-205/249-268/308-349/352-368；TestFeishuConversationE2E:203-222/281-300）。改写方向：统一 `awaitUntil` 有界等待（该 harness 已存在于 TestChannelMessageService.java:62-76）。
2. **fan-out 测试 → C8-002（无钉死，确认安全）**：`TestChannelFanOutTimeoutCancel` 的协作监听器中断断言在"deadline 移至执行开始"修复下不受影响（即时派发，两锚点重合）；池复用探针（:123-125）不受有界队列影响。修复无需改写。
3. **FailoverStreamFlow 测试 → C3-007（弱钉死，方案依赖）**：`nonTransientStreamingErrorDegradesToTerminalResponse`（TestAiGatewayFailoverInterceptorStreaming.java:351-369）钉住"NON_TRANSIENT 窗口内降级 = onNext+onComplete 正常终止"契约（B-17）。C3-007 修复选项 2（缓冲层 handleComplete 前丢弃未越窗缓冲）保持该契约（空缓冲不变）零转红；选项 1（拦截器感知缓冲层接管态改 rethrow）会使该测试转红——修复方案评审时须带测试改写评估，避免为消一缺陷打红另一契约。
4. **FailoverStreamFlow 测试 → C8-001/C3-001/C2-001（无钉死，确认安全）**：满 demand harness 下，越窗硬上限/上游降档（C8-001）、pendingComplete 终态挂起（C3-001）、onSubscribe 补 releaseAttempt（C2-001）均不改变现有断言结果（逐用例推演：`endToEndInWindowFailureResubscribeAndPassThrough` 的 fake 上游只响应首次 request 且无视 n 值一次性发射，降档授信不改变其行为；`concurrencyCountHooksAtCallStreamInvocation` 的"subscribe 返回时计数 +1"在结构性修复（装配纳入监视器）下仍成立；`callerCancelStopsCurrentAttemptOnly` 断言的 teardown 释放路径与补的 acquire 回滚路径互斥不冲突）。
5. **ConcurrencyRegistry 测试（确认安全 + 反向佐证）**：`TestConcurrencyRegistry` 两个并发用例（:93-126/:154-200）护的是**已修复的原语**（幽灵 +1 补偿竞态、下溢 fail-fast、map 收缩），与本轮 48 条发现零交集——C2-001 缺陷在 FailoverStreamFlow 编排层，registry 原语测试全绿恰好证明"原语正确、编排泄漏"，构成 C2-001 无保护的强佐证。修复 C2-001 不会打红任何 registry 测试。

**结论**：反向钉死命中 2 强 + 1 弱（C4-005、C8-003 强；C3-007 选项 1 弱）。全部 48 条中无一条被现有测试以"断言缺陷语义"方式正向钉死——缺陷语义未被任何测试编码，这是比反向钉死更彻底的保护力空洞。

## 缺保护登记

按同案保护面合并（对照各维度复核主登记网），48 条保留发现收敛为 18 条缺保护登记。缺保护本身按 P2 口径立项（与缺陷本体分开计数）；本体为渠道栈休眠条件化条目的，登记维持 P2/P3 并标注「随本体回升」。全部条目为复核确认（无测试）状态。

### [C10-001] 流式 demand==0 缓冲与上游授信面零测试覆盖（C8-001 本体 P1）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/FailoverTestSupport.java:399-401`（harness 缺陷面）
- **证据片段**:
  ```java
  // CollectingSubscriber.onSubscribe —— 全模块所有流式测试的唯一消费者形态
  public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
  }
  ```
- **严重程度**: P2（本体 C8-001 P1）
- **并发场景**: 慢 SSE 消费者（demand==0）× 上游 request(MAX) 长流——生产缺陷场景的测试不可达面
- **触发时序**: 测试中任何订阅者建立即 request(MAX) → `deliver`/`flushBuffer` 恒走 demand>0 分支 → `state.buffer.add`（FailoverStreamFlow.java:380，demand==0 分支）全树零执行 → 越窗无界缓冲与上游降档语义（修复目标）均无断言。修复后无测试转红 = 回归无保护。
- **现状**: 检索路径——grep `request(` 全测试树过滤 MAX_VALUE 零有界用例；`setBufferSize(2)` 用例（outOfWindow）亦在满 demand 下运行。
- **风险**: C8-001（P1 live 面）修复后任何重构回退（如误删容量门）无测试报警；且 demand 语义本身（request 授信时点、demand-- 配对）从无直接单测。
- **建议**: 新增有界 demand 订阅者 harness（request(n) 分批授信）+ 三用例：越窗后 demand==0 缓冲有界（对齐 bufferSize 上限断言）、缓冲满溢出策略（deliverError/指标）、demand 补充后排空。载体：TestChatServiceFailoverAdapterStreaming 新增 demand 分节。
- **失效面枚举**: C8-001（主）；同站点 CL-001/CL-005/CL-012/CL-014 线索不独立计数；跨单元孪生 WI 2.3 C3-003（nop-gateway BufferedStreamingPublisher）同缺保护形态，修复批次对齐时该单元测试缺失同步登记。
- **信心水平**: 确定
- **误报排除**: 非要求真实大规模压测——按 C10 正文步骤 5 以小规模参数化模拟（有界 demand 即可触达缺陷分支）。
- **复核状态**: 已保留（C10 单轮终判）

### [C10-002] 流式终态冲刷与窗口清理竞态面零测试覆盖（C3-001 + C1-001，双 P1 同文件同批）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestChatServiceFailoverAdapterStreaming.java:327-338`（completionDuringWindowDeliversBufferedItems）
- **证据片段**:
  ```java
  // 短流（3 元素 < N=10，未越窗）正常完成 → 缓冲元素必须全量交付
  fake.queueStream(StreamScenario.success(...));
  sub.awaitAndAssertSuccess("x1", "x2", "x3");   // 满 demand 下 flushAll 立即排空，
                                                  // 缺陷前提（缓冲非空 ∧ demand==0）不可达
  ```
- **严重程度**: P2（本体 C3-001 P1 + C1-001 P1，为本轮最高优先缺保护）
- **并发场景**: 终态（onComplete/onError）与下游 demand 排空、handleStreamFailure 监视器内外清理的交错
- **触发时序**: 现有测试的完成/失败信号均在满 demand 下到达 → C3-001 的"缓冲非空时 onComplete 直发截断缓冲"（handleStreamComplete L465-483 无挂起）与 C1-001 的"读 inWindow 出监视器后 flushBuffer 无 attempt-identity 守卫交错"两个时序都构造不出来；Major-1 语义断言（:139-140）存在但时序永不复现缺陷。
- **现状**: 语义面部分覆盖（Major-1/全量交付断言在位），触发面零覆盖。
- **风险**: 双 P1 修复（pendingComplete 挂起 + clear 入监视器/attempt-identity 守卫）落地后，回归保护为零；同文件后续重构（如 demand 记账方式变更）可能静默重引。
- **建议**: 新增三用例：①窗口期完成 + demand==0 → 终态挂起、demand 补充后缓冲补发再 onComplete（对齐 BufferedStreamingPublisher pendingComplete L328-337 对照实现）；②失败清理与 flushBuffer 交错（gate 控制两线程时序）；③越窗 demand==0 后旧 attempt flush 不污染新 attempt（attempt-identity）。
- **失效面枚举**: C3-001（主）、C1-001；CL-001 归 C10-001；本地形态（adapter）与网关形态（interceptor/BufferedStreamingPublisher 终态面）分别登记，本条覆盖 adapter 侧，interceptor 侧终态语义随 WI 2.3 已有覆盖核对。
- **信心水平**: 确定
- **误报排除**: 已确认同仓库存在正确对照实现（BufferedStreamingPublisher pendingComplete），语义可测，非"不可测试"豁免。
- **复核状态**: 已保留

### [C10-003] 取消×装配窗口计数泄漏 + per-attempt 状态发布面零测试覆盖（C2-001 + C1-008 + C1-009）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestChatServiceFailoverAdapterStreaming.java:425-467`（取消两端点用例）
- **证据片段**:
  ```java
  awaitStreamCallCount(2);            // 取消点 = attempt 已装配（teardown 路径）
  assertNull(sub.error, ...);
  callerToken.cancel("test-cancel");  // 或 :452 前置已取消（入口检查路径）
  // 两端点之间：acquire(FailoverStreamFlow L263) → subscribe(L288) 窗口不可达
  ```
- **严重程度**: P2（本体 C2-001 P1，live 流式主链）
- **并发场景**: 调用方取消线程 × attempt 装配时序（acquire 后、onSubscribe 前）
- **触发时序**: 测试取消仅落在"装配完成后"或"入口检查前"，中间窗口需要 cancel 与 startAttempt 并发且 cancel 恰落在 L263-L288 之间；`upSub` 全树零引用 → 窗口内状态无观测点，时序注入设施（可暂停的 executor/gate 化 subscribe）不存在。
- **现状**: 邻近语义有保护（teardown 释放、前置拒绝均被断言且绿）；竞态窗口零保护。
- **风险**: 修复（onSubscribe 取消分支补 releaseAttempt）后回归无保护；该缺陷是 P1 汇总 79 行台账内条目，裸奔回归代价高。
- **建议**: gate 化 delegate（callStream 挂起至测试放行）+ 后台线程先发起流式调用、等 acquire 落账后 cancel、再放行装配 → 断言终态 count==0；同一 harness 顺带覆盖 C1-009 属性快照面（装配后读属性的一致性断言）。
- **失效面枚举**: C2-001（主）、C1-008（并入）、C1-009（同文件发布面）；跨单元 MA-01（nop-ai-core callStream 取消传播）同族缺保护形态在彼单元登记，不在此重复。
- **信心水平**: 确定
- **误报排除**: registry 原语测试（TestConcurrencyRegistry）全绿证明缺陷不在原语——不可用"已有并发测试"反驳本登记。
- **复核状态**: 已保留

### [C10-004] 流式两监视器锁内副作用面零测试覆盖（C2-002 + C2-009）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestAiGatewayFailoverInterceptorStreaming.java`（全部 13 用例经 BufferedSubscription 但无锁面断言）
- **证据片段**:
  ```java
  // 全部用例形如：handle → join → subscribe → await → 断言载荷/计数。
  // 无任何用例断言：投递期间监视器持有时长 / retry 链（VFS 分类器+探活+HTTP 发起）在锁内。
  ```
- **严重程度**: P2（本体 C2-002/C2-009 均 P2 live 面）
- **并发场景**: 流式逐元素投递/重试决策与并发 demand 竞争者的锁竞争
- **触发时序**: 现有测试单订阅者顺序执行，锁内 I/O 的冻结时长（VFS loadConfig、探活、buildHttpRequest）不可观测——即便锁内挂死，测试仍绿。
- **现状**: 功能结果面全覆盖（切换/重订阅/降级/预算），并发代价面零覆盖。
- **风险**: 修复（临界区收窄）后回归无保护；后续向 retry 链添加更重 I/O（如新指标/新配置源）无护栏报警。
- **建议**: 锁面用双订阅者 + 慢 listener 结构断言"投递不阻塞他方 onSubscribe/onNext"（时序上界用 latch 而非 sleep）；retry 锁面用计数断言锁内完成的 VFS 调用次数为 0（注入 counting classifier/config source）。
- **失效面枚举**: C2-002（StreamSubscription 监视器）、C2-009（BufferedSubscription 监视器）——同批两监视器一次 harness 覆盖。
- **信心水平**: 很可能（锁面可测性依赖注入点存在性，建议阶段先核实 classifier 可注入）
- **误报排除**: 非"锁使用原语"误报——登记对象是锁内副作用这一已回码证实的缺陷面（C2 复核逐项核实 VFS/HTTP 调用链）。
- **复核状态**: 已保留

### [C10-005] 终态回调异常护栏面零测试覆盖（C3-004 + C7-001 同工单）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/FailoverTestSupport.java:182-309`（FakeHttpClient 全路径不抛）
- **证据片段**:
  ```java
  // fake 全部终态路径：completeExceptionally / onComplete / onNext 均正常返回，
  // 无一注入 "whenComplete 回调体抛异常" 场景 → future 永不完成链在测试中不可达。
  ```
- **严重程度**: P2（本体 C3-004 P2 + C7-001 P2，live 面，F9 静默吞异常族）
- **并发场景**: 完成线程回调（whenComplete）抛异常 → 从属 stage 无人消费 → 调用方永久悬挂
- **触发时序**: 需要回调体内异常注入（如 metrics/breaker 抛出）；现有 fake/metrics stub 全部无异常路径。
- **现状**: 无测试。grep 全树无对应注入点。
- **风险**: 护栏修复（try/catch(Throwable) + completeExceptionally 兜底）后回归无保护；F9 族在本模块的唯一直面缺陷裸奔。
- **建议**: 注入抛异常的 IFailoverMetrics/breaker stub，断言 future 在超时窗内以 completeExceptionally 终结且计数归零（对齐 1.7 C6-001 / WI 3.2 MA-01 族的护栏测试形态）。
- **失效面枚举**: C3-004 三回调体（adapter whenComplete / interceptor whenComplete / onError 内联）+ C7-001 release 下溢触发器视角；修复同 diff，测试同批。
- **信心水平**: 确定
- **误报排除**: 非"测试自己 catch 掉竞争"误报——是真实的注入缺失，C7 复核 OBS-02 注释实证在案。
- **复核状态**: 已保留

### [C10-006] 降级路径×窗口缓冲组合场景缺位（C3-007）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestAiGatewayFailoverInterceptorStreaming.java:351-369`
- **证据片段**:
  ```java
  fake.queueStream(StreamScenario.failing(streamError(400, nonTransientBody()))); // error 前零元素
  ...
  assertEquals(1, collector.degraded.size(), "客户端收到降级终止响应元素"); // 空缓冲下降级契约
  ```
- **严重程度**: P2（本体 C3-007 P2 live 面：脏输出 + 伪装成功）
- **并发场景**: 窗口内已缓冲前缀 + 源 onError → 拦截器降级分支 onNext+onComplete → 缓冲层 handleComplete 全量冲刷
- **触发时序**: 缺陷场景 = success(a1) + error(400)（缓冲层 bufferSize 默认 10、未越窗）→ 期望正确语义为前缀丢弃 + 降级 wrapper 或断流；现有测试只有空缓冲降级（:351）与越窗断流（:374）两端点，中间组合缺位。
- **现状**: 降级契约被钉死但缺陷组合场景缺位（弱反向钉死：修复选项 1 会打红本用例，见反向钉死 #3）。
- **风险**: 用户可见脏输出缺陷（Major-1 契约绕过）修复后无回归保护。
- **建议**: 新增"缓冲未越窗 + 降级"用例，断言前缀不出现于输出且终态语义与修复裁定一致；随修复方案（选项 1/2）同步定稿断言。
- **失效面枚举**: C3-007（主）；本地形态对应面为 deliverError 清缓冲语义（已有 outOfWindow 断流用例覆盖窗口外面），无同族遗漏。
- **信心水平**: 确定
- **误报排除**: 非要求覆盖所有排列——该组合是 C3 复核确认的确定性可达路径（非竞态），单用例即可钉死。
- **复核状态**: 已保留

### [C10-007] 取消/失败记账与上游信号消费面零测试覆盖（C3-003 + C3-008 + C8-004）

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/TestFailoverMetricsLocal.java`（指标契约测试，无取消场景）+ 全树 grep `retryAfter|Retry-After` 零命中
- **证据片段**:
  ```java
  // callerCancelledBeforeAttemptTerminatesCleanly 仅覆盖入口前置检查（pre-cancelled）：
  // 无"调用中取消 → decideNonStreamFailure 分类记账"用例；
  // streamingNoConfigReselectAbortsRetryWithWarn 断言 registry 归零但不断言 switch 计数归属。
  ```
- **严重程度**: P2（本体 C3-003 P2 live + C3-008/C8-004 P3）
- **并发场景**: 在途取消 × 错误分类记账；retry 中止 × 指标先行记账；上游 429 Retry-After × 重选决策
- **触发时序**: 中途取消（gate 挂起 HTTP 后 cancel 非流式调用）→ 现有套件无此场景 → InterruptedIOException/未知异常兜底 TRANSIENT 的误记账路径不可达；指标漂移与 Retry-After 面同理零断言。
- **现状**: 无测试（pre-cancel 端点 + 429 切换语义有覆盖，均不入缺陷面）。
- **风险**: 记账污染（熔断被取消请求推进、指标归属漂移）修复后无保护；上游信号消费缺口无行为锚点。
- **建议**: ①gate 化非流式调用 + 中途取消，断言不记 breaker failure/FAILURE 指标、终态 CancellationException；②no-config 重选用例补 switch/resubscribe 计数归属断言；③RATE_LIMITED + Retry-After 头 → 断言重选跳过该 provider。
- **失效面枚举**: C3-003（adapter 面②interceptor 孪生同 diff）、C3-008（sinkCandidate/metrics/buildHttpRequest 顺序）、C8-004（retryAfterMs 全链）——一次记账-信号 harness 覆盖三面。
- **信心水平**: 确定
- **误报排除**: 计时类断言按 TestFailoverMetricsLocal 既有 flaky-free 口径（只断言计数归属，不断言耗时值）。
- **复核状态**: 已保留

### [C10-008] ChannelMessageServiceImpl close/生命周期/并发站点零测试覆盖（C1-004 + C2-006 + C4-001 + C4-003 + C4-006 + C6-003 单工单族）〔随本体回升〕

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/TestChannelMessageService.java`（close 零调用）+ TestChannelMessageServiceIoC.java（无 destroy-method 断言）
- **证据片段**:
  ```java
  // grep channel 包 `.close()`：仅 h2Store.close() / factoryBean.destroy() 命中；
  // ChannelMessageServiceImpl.close() 全测试树零调用 → close 同锁/复活池/REE 三面不可达。
  ```
- **严重程度**: P2（本体含 C4-001 P2 条件化 + 4×P3 条件化；随本体回升——投产接线即回升 P2/P1）
- **并发场景**: dispatch/subscribe/close 三方交叠；ownedFanOutExecutor 惰性建池 DCL；容器关停 destroy 回调
- **触发时序**: 现有 64 线程用例（:318-352）触达惰性建池站点但断言（1 订阅 + n 交付）对双实例泄漏/REE 不可观测；close 从未被调用，交叠与关停语义零覆盖；destroy-method 接线无结构性断言。
- **现状**: 无测试（触达而不可观测计入无测试，见矩阵 #19/#33 注）。
- **风险**: 单工单修复（volatile + close 同锁 + destroy-method + 退订 + REE 防御）落地后全面无回归保护；接线回归（误删 destroy-method）无报警。
- **建议**: ①beans 结构断言测试（解析 ai-gateway-defaults.beans.xml 断言 nopChannelMessageService 带 destroy-method="close"）；②close×dispatch 交叠用例（close 后 dispatch 断言 REE 防御行为 + 无复活池：probe executor 无新线程）；③并发惰性建池断言池线程命名唯一集（可观测双实例）。
- **失效面枚举**: C1-004（volatile/close 同锁）、C2-006（三面竞态）、C4-001（destroy-method）、C4-003（复活池+REE）、C4-006（DCL）、C6-003（closed 标志）——同工单同 diff，登记合一；CL-002 随案。
- **信心水平**: 确定
- **误报排除**: 渠道栈休眠面按 WI 3.2 先例条件化——本登记不升本体判级，仅登记保护缺口并标注「随本体回升」。
- **复核状态**: 已保留

### [C10-009] fan-out 队列治理与超时语义面零测试覆盖（C4-002 + C7-004 + C8-002 同站点三失效面）〔随本体回升〕

- **文件**: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/TestChannelFanOutTimeoutCancel.java:86-131`
- **证据片段**:
  ```java
  // javadoc（:26-28）：a mode-1 fan-out listener that hangs ... a cooperative listener
  // releases the pool thread —— 测试前提恰为缺陷面（非协作挂死）的对偶；
  // 即时派发（无队列积压）→ orTimeout 锚定提交时刻 vs 执行开始的差异不可观测。
  ```
- **严重程度**: P2（本体 C4-002/C7-004/C8-002 均 P2 条件化，回升 P1）
- **并发场景**: 队列积压 >dispatchTimeoutMs；非协作 listener 无视中断占用池线程；超时取消后任务半执行
- **触发时序**: 需要"提交后排队 30s+ 才执行"或"listener 不响应 interrupt"前置；现有测试即时派发 + 协作监听器，两前置均不成立。
- **现状**: 协作中断路径有保护（AR-4 回归面），缺陷面无保护；`TestInvariantGate2GatewayTimeout` 自证对 cancel 语义零判别力（:41-43），marker gate 不构成保护。
- **风险**: 队列治理修复（有界队列+QueueOverflowPolicy+熔断摘除）与超时语义修复（deadline 移至执行开始、dropped/timeout 分离计数）落地后零回归保护。
- **建议**: ①非协作 listener（sleep 无限不捕中断）+ 池耗尽断言后续派发被显式拒绝（有界队列策略）；②注入带排队延迟的 executor 断言 deadline 自执行开始起算；③超时后任务执行状态断言（跳过 vs 完整执行）+ 日志码分离断言。
- **失效面枚举**: C4-002（队列/池耗尽）、C7-004（内存/驻留增量面）、C8-002（超时锚定/半执行/观测面）——同站点三失效面一次 harness 覆盖；orTimeout 站点随 WI 2.3 CL-022 移交面对齐。
- **信心水平**: 确定
- **误报排除**: 协作面已覆盖不构成误报——登记对象明确为非协作/积压面。
- **复核状态**: 已保留

### [C10-010] 连接器生命周期编排与守卫面零测试覆盖（C4-004 + C6-001 + C6-002 + C2-004 + C2-003 + C1-002 + C1-007）〔随本体回升〕

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 `channel/TestChannelConnectorManager.java:31-201` + E2E teardown（TestFeishuConversationE2E.java:132-140）
- **证据片段**:
  ```java
  // RecordingConnector.start/stop 均不抛异常（:149-160）；
  // stopAll 仅在 tearDown 且无在途 future；
  // startAll/stopAll/feishuClient.start 全部单线程顺序调用。
  ```
- **严重程度**: P2（本体含 C4-004 P2 条件化，回升 P1；余 6 条 P3 条件化/保留）
- **并发场景**: startAll 部分失败补偿、stopAll 逐连接器隔离、启停互斥、started 与 feishuClient.start 排序、register 可见性、停机在途排空
- **触发时序**: 全部需要"抛异常的连接器/并发启停/在途执行"前置；现有 RecordingConnector 不抛、测试单线程、E2E 无在途——全部前置不成立。
- **现状**: 正常路径（LIFO 停止序、start 计数、上下文传递）覆盖扎实；全部异常/并发/排序面零覆盖。
- **风险**: destroy-method 挂接与编排修复（逆序补偿、隔离 stop、AtomicBoolean 单次启停）落地后零保护；连接器投产接线时（回升面）该区域将完全裸奔。
- **建议**: ①FailingConnector（start 抛）断言逆序补偿 stop + clear；②stop 抛断言后续连接器仍被 stop；③并发 startAll×stopAll 压力用例；④destroy-method 结构断言（并入 C10-008 ①）。
- **失效面枚举**: C4-004（断链）、C6-001（成组编排）、C6-002（在途排空）、C2-004（互斥）、C2-003（锁内 I/O/单次启停）、C1-002（started 排序）、C1-007（register 守卫）——七条同容器/同编排面合并登记。
- **信心水平**: 确定
- **误报排除**: 正常路径测试覆盖不构成保护——缺陷全部位于异常/并发分支（各维度复核逐条回码证实）。
- **复核状态**: 已保留

### [C10-011] 通道线程交接与出站发送治理面——缺陷无测试且时序被钉死（C4-005 + C8-003 + C4-007）〔随本体回升；反向钉死命中〕

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 `channel/feishu/TestFeishuConnector.java:125-368`（6+ 处内联立即断言）
- **证据片段**:
  ```java
  connector.onMessage(dmMessage("oc_mid", "ou_s", "你好", "om_123"));
  assertNotNull(engine.lastRequest, "engine.execute was called");   // 立即断言 = 钉死内联执行
  ...
  engine.lastFuture.complete(resultWithAssistant(longText));
  assertTrue(feishuClient.sendMessageCount > 1, ...);               // 立即断言 = 钉死同步串行发送
  ```
- **严重程度**: P2（本体 C4-005 P2 条件化回升 P1；C8-003 P3 条件化回升 P2；C4-007 P3）
- **并发场景**: WS 接收线程内联 DB/引擎调用（ACK 延迟）；完成线程同步串行分段发送（无超时/无限流）；注入执行器契约
- **触发时序**: 唯一既"缺陷无断言"又"修复会打红现有测试"的登记——修复（onMessage 入口线程交接 / dedicated executor + per-send deadline）将使 `messageIdExtractedIntoIdempotencyKey`、`fullInboundToOutboundRoundTrip`、`executeResultTriggersFeishuReply`、`executeFailureTriggersErrorReply`、`longTextReplyIsSegmentedByMaxMessageLength`、`rateLimitGuardSuppressesEngineExecuteWhenExceeded`、`e2eLongTextSegmented` 等 ≥8 用例的立即断言偶发失败。
- **现状**: 假保护（时序钉死型）——套件覆盖站点但保护力为零且阻碍修复。
- **风险**: 修复若被"测试红了"阻挡而采取弱化方案（保留内联 + 仅加超时参数），C4-005 的 ACK 延迟主缺陷不闭合。
- **建议**: 修复同批将上述用例的立即断言统一改为 `awaitUntil` 有界等待（复用 TestChannelMessageService.java:62-76 harness）；新增超时/限流行为断言（慢 sendMessage stub + deadline 触发断言）；C4-007 契约面以 javadoc + 注入类型检查断言收口。
- **失效面枚举**: C4-005（onMessage 内联）、C8-003（deliverSegmented/sendToUser 出站）、C4-007（setFanOutExecutor 契约）；受钉死影响用例清单见反向钉死 #1。
- **信心水平**: 确定
- **误报排除**: 立即断言在当前内联实现下确定绿（非 flaky 误报）；钉死判定基于修复方向的确定性时序改变。
- **复核状态**: 已保留

### [C10-012] 会话映射半个成功链零并发/错误面测试覆盖（C6-004 链主 + C1-005 + C2-005 + C3-006）〔随本体回升〕

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 `channel/TestChannelSessionStore.java:130-191`（真实 H2 + UK，全单线程）
- **证据片段**:
  ```java
  store.saveMapping("feishu", "oc_1", "sess-abc-123", "agent-1");   // 单次保存
  assertEquals(1, dao.findAllByQuery(q).size(), ...);               // 无重复保存/UK 冲突用例
  // updateLastActiveOnMissThrowsRatherThanSilentNoop 钉 update 面 miss 抛错；
  // findByChannel 的 error≡miss 吞噬面零用例。
  ```
- **严重程度**: P2（本体 C6-004 P2 条件化链主，回升 P1）
- **并发场景**: 同 chat 并发首聊双 miss → 双插入；engine error 后遗弃无登记；findByChannel 把 DB error 当 miss
- **触发时序**: 需要并发 connector.onMessage 同 chat 双消息 + 真实 store；现有 E2E（TestFeishuConversationE2E.e2eSessionReuseAcrossTwoMessages:225-241）顺序双消息，store 测试单线程。
- **现状**: 持久化功能面（miss/save/refresh/UK 存在性）覆盖扎实；并发/错误/遗弃面零覆盖。
- **风险**: upsert + error/miss 区分 + fail-loud 指标修复落地后零回归保护；投产接线（回升 P1）时半个成功语义直接裸奔。
- **建议**: ①双线程同 chat 并发 saveMapping（真实 H2）断言单行 + 换绑语义；②findByChannel 注入 DB error 断言与 miss 可区分（异常类型/结果标记）；③engine error 后断言遗弃登记指标 >0。
- **失效面枚举**: C6-004（链主五子项）、C1-005（同链可见性面）、C2-005（无互斥 WARN 面）、C3-006（双会话并发面）——链级四视角一次 harness 覆盖；CL-015 随案关闭不另登记。
- **信心水平**: 确定
- **误报排除**: UK 存在已被 C6 复核实证（nop-ai.orm.xml L1462-1466），库端兜底不消解应用层语义缺陷——不可用"有 UK"反驳登记必要性。
- **复核状态**: 已保留

### [C10-013] 时钟选择面（墙钟 vs 单调钟）零测试覆盖（C7-002 + C7-003）

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 failover 全部流式用例（时钟单向推进）；`TestFailoverMetricsLocal.java:44`（javadoc 明示放弃计时值断言）
- **证据片段**:
  ```java
  // 生产：窗口判定/时长 4 处 CoreMetrics.currentTimeMillis()（FailoverStreamFlow:154/360/424/477）
  //       + 入站限流窗（FeishuConnector:465-472）；adapter/interceptor 5 处 nanoTime（对照组在码）。
  // 测试：无任何时钟回拨/大步进注入点；计时断言被合理放弃（防 flaky）→ 时钟选择零保护。
  ```
- **严重程度**: P2（本体 C7-002 P2 live + C7-003 P3 条件化）
- **并发场景**: NTP 校准/环境时钟步进 × 窗口判定与时长指标；未来时间戳 × 限流窗
- **触发时序**: 需要时钟注入设施（可编程 Clock）；现有测试时钟恒正向小幅推进，回拨场景构造不出来。
- **现状**: 无测试（窗口/限流的计数语义有覆盖，时钟面零覆盖）。
- **风险**: 统一 nanoTime 修复后回归无保护；时钟抽象缺失使未来同类站点（新增窗口/时长逻辑）无既定模式可循。
- **建议**: 修复同批引入包内可注入时钟（测试默认可编程），补两用例：窗口期时钟回拨 → 越窗判定仍按单调时序；限流窗未来时间戳 → 不拒绝合法消息（C7-003 防御方向）。
- **失效面枚举**: C7-002（FailoverStreamFlow 4 处）、C7-003（FeishuConnector 入站窗）；F3 兄弟面 grep 证实无其他墙钟窗口站点（C7 复核已清点）。
- **信心水平**: 很可能（时钟注入可行性取决于 CoreMetrics 是否可替换，建议阶段核实；不可替换则以 nanoTime 直改 + 行为等价断言收口）
- **误报排除**: 不要求计时值断言（flaky 口径维持）——登记对象是时钟**选择**的可测性，非耗时精度。
- **复核状态**: 已保留

### [C10-014] 选择面 allowCall 消费与 attempted 回收语义零测试覆盖（C7-005 + C3-005）

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 `routing/TestDefaultSelectionStrategy.java:40-110`（全用例长冷却 breaker）
- **证据片段**:
  ```java
  private final ThresholdBreaker breaker = new ThresholdBreaker(3, 60_000L); // 冷却 60s：
  // allowCall(OPEN)=false 与 isAvailable(OPEN)=false 同值 →
  // "冷却期满 OPEN 应被选择面放行（allowCall=true）"与"HALF_OPEN 单探针契约"零区分。
  ```
- **严重程度**: P2（本体 C7-005 P2 live + C3-005 P3）
- **并发场景**: 冷却期满候选在选择面被快照判死（容量单调流失）；HALF_OPEN 并发放行契约；预算耗尽后 attempted 永久排除
- **触发时序**: 需要"冷却 0 的 OPEN 候选进入选择路径"（现测试全用 60s 冷却隔离探活语义）或"预算耗尽后重选 revisit"；`budgetExhausted*` 只断言 fail-loud 终态。
- **现状**: 无测试（OPEN 跳过/声明序/饱和跳过等快照语义覆盖扎实，恰为缺陷对偶面）。
- **风险**: 门控补 allowCall 复查修复后零回归保护；容量流失缺陷（live 面）持续不可见。
- **建议**: ①冷却 0 OPEN 候选选择路径用例：断言 allowCall 消费（HALF_OPEN 迁移可观测）+ 单探针并发语义；②预算耗尽后探活/revisit 用例（C3-005 修复方向二选一落地后钉死）。
- **失效面枚举**: C7-005（DefaultSelectionStrategy L40/RuleBasedSelectionStrategy L203 兄弟面同批）、C3-005（三重选路径）。
- **信心水平**: 确定
- **误报排除**: 探活路径测试（probePool 系列）不构成覆盖——缺陷站点在选择消费面，C7 复核已确认 allowCall 消费点唯一性事实。
- **复核状态**: 已保留

### [C10-015] 装配守卫 fail-fast 面零测试覆盖（C1-010）

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 `TestGatewayAutoAssemblyIoC.java:87-119`（标准装配存在性）+ 各 adapter/interceptor 测试 setUp（非空依赖构造）
- **证据片段**:
  ```java
  assertNotNull(container.getBean("nopChatServiceFailoverAdapter"), ...); // 断言标准装配
  // 无任何用例构造 "breaker/registry 为 null 的 adapter" 断言 fail-fast ——
  // D8 契约（守卫消息明文声明的失效模式）在测试侧零编码。
  ```
- **严重程度**: P2（本体 C1-010 P2：装配漂移 → 防护静默全局失效）
- **并发场景**: 不适用（装配期缺陷面）；触发面 = 装配漂移/手工构造/未来重构
- **触发时序**: 需要缺依赖构造路径；现有套件全部标准/完整构造，漂移面不可达。
- **现状**: 无测试（标准装配回归面有覆盖——修复加守卫不破坏它们，反向钉死安全）。
- **风险**: 一行守卫修复（adapter routerFor 复制 D8 fail-fast）后回归无保护；CandidateHealthProvider null 兜底收口（评估项）同缺锚点。
- **建议**: 新增构造守卫用例（null breaker/registry/strategy → NopException 且错误码可辨）；若收口 null 兜底，补 CandidateHealthProvider null 依赖用例。
- **失效面枚举**: C1-010 四站点（adapter L371-374 / interceptor L541-553 / CandidateHealthProvider L26-29 / ModelClassRouter 构造器 L74 传递面）——守卫测试一处 + 参数化四构造路径。
- **信心水平**: 确定
- **误报排除**: TestGatewayAutoAssemblyIoC 断言标准装配不受守卫影响，恰证明该测试不是守卫面覆盖——无重叠误报。
- **复核状态**: 已保留

### [C10-016] capabilities 潜伏面零测试覆盖（C1-006 + C2-007 合并）〔SPI 接入时回升〕

- **文件**: 全测试树 grep `.getCapabilities()` 零消费用例（仅 TestIChannelConnectorWiring.java:115 stub 声明）
- **证据片段**:
  ```java
  // 消费方 grep：全仓零生产消费（DBMessageService.getCapabilities 为异接口同名词，C1 复核证实）
  // → 缓存可见性缺陷当前无任何触发面，亦无任何测试锚点；SPI 接入即回升。
  ```
- **严重程度**: P3（本体 C1-006/C2-007 均 P3 零消费面；SPI 接入时回升）
- **并发场景**: 装配期写 × 未来 SPI 消费方读的可见性
- **触发时序**: 当前不可达（零消费）；SPI 消费方接入后首读即可见性问题。
- **现状**: 无测试；修复（volatile + 饿汉初始化）零转红。
- **风险**: 低（休眠面）；SPI 接入批次若不同步补测试，缺陷随消费面激活。
- **建议**: 与 SPI 接入批次绑定：接入同批补饿汉初始化断言（构造后立即读 capabilities 非空/一致）。
- **失效面枚举**: C1-006 = C2-007 同站点合并（已按 C1/C2 复核裁定）；IChannelConnector 实现族仅 FeishuConnector L158 一处，无同族遗漏。
- **信心水平**: 确定
- **误报排除**: 零消费面按休眠口径登记，不升本体判级。
- **复核状态**: 已保留

### [C10-017] AttemptCancelToken 原语面零直接测试覆盖（C1-003 + C2-008 + C3-002 三登记工单）

- **文件**: 全测试树 grep `AttemptCancelToken` 零命中；流式取消测试用自建 SettableCancelToken（TestChatServiceFailoverAdapterStreaming.java:627-664）
- **证据片段**:
  ```java
  // 生产原语：appendOnCancel 的 check-then-act（FailoverStreamFlow L108-118，add 后二次复核缺失）
  // 测试替身：SettableCancelToken —— 自己实现了正确语义（cancel 后 append 立即执行），
  // 恰好绕开生产原语的缺陷面。
  ```
- **严重程度**: P3（本体三登记合并终判 P3；live 外部面经 MA-01 族承载）
- **并发场景**: cancel 线程 × appendOnCancel 调用线程在 [isCancelled 检查, add] 窗口交叠
- **触发时序**: 需要对生产原语直接双线程压测；现有测试全部经替身或端点时序，窗口不可达。
- **现状**: 无测试（且测试替身实现了正确语义，构成对缺陷的系统性遮蔽——替身越正确，原语越无保护）。
- **风险**: 修复（add 后二次复核补跑）后零回归保护；原语被 nop-ai-core/nop-ai-agent 同构实现引用时（MA-01 批次明确"不得假设原语无竞态"），回归面扩散。
- **建议**: 原语直测：N 线程并发 cancel×appendOnCancel，断言"注册时已取消 → 任务立即执行"与"注册后取消 → 任务被调用"无遗漏（双计数器收敛断言）。
- **失效面枚举**: C1-003/C2-008/C3-002 三登记同站（FailoverStreamFlow.AttemptCancelToken L92-118）；nop-ai-core/nop-ai-agent 同构 ICancelToken 实现为跨单元 F3 面，彼单元登记。
- **信心水平**: 确定
- **误报排除**: SettableCancelToken 替身语义正确不构成生产面覆盖——登记对象是生产原语本体。
- **复核状态**: 已保留

### [C10-018] 登录会话存量/回收责任面零测试覆盖（C6-005）

- **文件**: `nop-ai/nop-entropy-concurrency-audit` 测试树 `login/TestChannelLoginApi.java`（10 用例）/ `login/TestChannelLoginAccessCode.java`（4 用例）
- **证据片段**:
  ```java
  // 全部为功能契约面（loginByScan 轮询/绑定/accessCode 往返/重放拒绝）；
  // grep 无 存量残留/终态指标/回收责任 断言。
  ```
- **严重程度**: P3（本体 C6-005 P3 live 面，TTL 兜底有界）
- **并发场景**: 登录编排中断 → 已引导会话残留（仅终态指标缺失面，非无界增长）
- **触发时序**: 需要"编排中途异常"注入；现有用例全部正常路径 + 显式重放负例。
- **现状**: 无测试（功能面覆盖完整）。
- **风险**: 低（auth 侧 TTL 兜底有界）；修复（终态指标 + 频控）后无行为锚点。
- **建议**: 中断注入用例（callback 抛异常/超时未完成）断言终态指标与可观测登记；频控断言随修复同批。
- **失效面枚举**: C6-005（loginByScan 编排单站点）；与 WI 3.1 MA-03 邻接面对齐不重复。
- **信心水平**: 确定
- **误报排除**: live 面零界增长（有界 TTL）不构成误报——登记对象是可观测性缺失面。
- **复核状态**: 已保留

**登记统计**：18 条（覆盖 48 条保留发现全部）。严重度：P2×15（C10-001…C10-015）、P3×3（C10-016…C10-018）。标注「随本体回升」：C10-008/009/010/011/012（渠道栈休眠工单族）+ C10-016（SPI 接入回升）；其中 C10-011 为唯一"假保护+反向钉死"型登记。

## 系统性观察

1. **demand 维度是模块最大单点保护力空洞**：全测试树 6 个订阅者 harness 恒 `request(Long.MAX_VALUE)`，demand==0 分支（`state.buffer.add`、flush 排水、终态挂起）全树零执行。直接后果：4 条 P1 中 3 条（C8-001/C3-001/C1-001 的 demand 交错面）+ C2-002 的触发前提在测试中结构性不可达。同仓库 nop-gateway 的 `BufferedStreamingPublisher` 有 `pendingComplete` 正确实现且被测试覆盖（WI 2.3 已裁定现实路径），nop-ai-gateway 的 FailoverStreamFlow 是"双实现分叉中未被测试钉住的那一半"。建议把"有界 demand 订阅者 harness"作为修复批次的第一交付物（C10-001）。

2. **竞态窗口类缺陷缺通用时序注入设施**：现有取消/失败测试全部锚定端点（前置已取消 / 装配完成后取消），acquire→装配、读 inWindow→clear 等中间窗口既不可达也无观测点（`upSub` 零引用）。模块缺一个 gate 化 delegate/可暂停 executor 的共享设施（FailoverTestSupport 已有 `StreamScenario.gate` 雏形但只用于事件发射时序，未用于编排层时序）。C2-001/C1-001/C1-003 三条竞态的回归保护全部依赖该设施的建立。

3. **渠道栈 17 条休眠面发现处于"端点时序钉死"状态，是修复工程的隐性阻力**：E2E/IoC 测试手工调用 startAll/stopAll、onMessage 内联后立即断言、close 零调用——套件恰好钉死缺陷赖以成立的时序（C10-011 为唯一假保护命中，≥8 用例需随修复改写）。同时 beans.xml 生命周期接线（destroy-method）无任何结构性断言测试，而现存的 `TestInvariantGate2GatewayTimeout` marker gate 自证对 cancel/治理语义"零判别力"（其 javadoc :41-43）——结构性接线回归（误删 destroy-method）当前无任何测试可拦。建议修复批次统一引入 `awaitUntil` 有界等待改造 + beans 结构断言测试。

4. **真实并发测试与缺陷清单零交集**：模块仅有的 3 处多线程测试，2 处护已修复的 registry 原语（幽灵 +1 回归）、1 处护 backbone dedup 语义——48 条保留发现无一条被真实并发压力触达可观测断言；registry 测试全绿反而构成 C2-001"原语正确、编排泄漏"的强佐证。后续新增并发测试应优先指向编排层（FailoverStreamFlow/ChannelMessageServiceImpl）而非再增加原语压测。

5. **时钟抽象缺失使 C7 族无测试抓手**：墙钟（FailoverStreamFlow 4 处 + FeishuConnector 限流窗）与 nanoTime（adapter/interceptor 5 处）口径分裂在码可证，但测试无可编程时钟注入点，回拨/步进场景构造不出来；metrics 测试为防 flaky 明示放弃计时值断言（合理），进一步留下时钟选择零保护。建议统一 nanoTime 修复同批落时钟注入点，作为后续所有窗口/时长站点的既定模式。

## 终止判定

**C10 为单轮维度，显式声明：本轮即终轮，无 round2。** 判定依据：(1) 本维度为纯评估型维度（对象是既有测试套件对已保留缺陷清单的保护力），无待深挖的代码新面；(2) 48 条保留发现的保护力判定已全部产出且每条附检索路径，零遗漏；(3) 缺保护登记已按同案保护面收敛至 18 条工单粒度，与各维度复核主登记网一一对应；(4) 反向钉死检查已对任务点名的三个重点面（FailoverStreamFlow demand/窗口/终态语义、fan-out、ConcurrencyRegistry）逐一闭合。后续动作归修复立项：C10-001…C10-005（live 面 P2）建议随 P1/P2 修复 roadmap 同批立项，其余随对应工单批次携带。

---
*审计员：C10 测试保护力（WI 3.3 最后批次，单轮）。方法：ai-dev/skills/concurrency-resource-audit-prompt.md L26-118/L329-347/L351-377。基线：31 个测试文件全文/定点读取 + 生产源锚点回码 + 全树 grep 检索留痕（检索路径见矩阵各行）。*

## C10 复核结论（独立复核，2026-10-20）

复核员独立回测：七维度 `## 最终保留项` 全文对账 + 测试源码抽查（20/48 行）+ 登记抽查（11/18 条）+ 关键事实回码。本节仅追加，原审计内容未改动。

### 1. 矩阵完整性对账（48 行 ↔ 七维度最终保留项）

- 逐条对账 C1×10 + C2×9 + C3×8 + C4×7 + C6×5 + C7×5 + C8×4 = 48：矩阵行与各维度保留清单**一一对应，无遗漏、无多记**（C1-008→#9、C7-001→#40、C7-004→#43、C3-002→#27 的"并入"行均以本体行覆盖，不丢条目）。
- 判级统计核对：P1×4（C1-001/C2-001/C3-001/C8-001）、P2×16、P3×28 与各维度终判列逐条一致；矩阵三态计数（会红 0／假保护 2＝#32/#47／无测试 46）与行标注一致；〔随本体回升〕标注 20 行与各维度「条件化」终判 20 条一致。

### 2. 抽查验证（20 行回测，假保护边界 7 项 + 无测试 13 项）

**判定全部正确，0 误判。** 关键回码结果：

- **假保护 2 项均证实**：#32（C4-005）`messageIdExtractedIntoIdempotencyKey`(:125-132)、`fullInboundToOutboundRoundTrip`(:148-169) 等确为 onMessage 后立即断言；#47（C8-003）`longTextReplyIsSegmentedByMaxMessageLength`(:308-329)、`executeResultTriggersFeishuReply`(:186-193) 确为 complete 后立即断言 sendMessageCount——修复改时序必打红，判定成立。
- **边界行（部分语义覆盖计入无测试）5 项均证实**：#1 Major-1 断言在 :139-140 精确无误；#11 取消两端点（:425-449/:452-467）+ `upSub` 全树零引用（复核 grep 证实）；#20/#45 满 demand 下运行（`outOfWindowFailureBreaksStreamWithoutSwitch` :293-322 确有 `setBufferSize(2)`(:300) 且注释自证单订阅顺序时序）；#25 空缓冲降级（`StreamScenario.failing` error 前零元素）证实。
- **无测试抽查 13 项**（#2/#3/#5/#17/#19/#23/#26/#29/#35/#36/#42/#44/#48）检索路径全部复核命中：`AttemptCancelToken`/`retryAfter|Retry-After`/`destroy-method` 全树 grep 均零命中；`TestChannelSessionStore` 全单线程（仅 ：168 sleep）；`TestDefaultSelectionStrategy:31` 确为 `ThresholdBreaker(3, 60_000L)` 全用例长冷却；Gate2 javadoc（:41-43）自证 marker 对 AR-4 cancel 语义"NOT detectable / zero-discriminative"。
- **根因性事实核实**：`FailoverTestSupport.java:399-401 request(Long.MAX_VALUE)` 逐字证实；全树 4 个订阅者 harness（W7:116 / 匿名：529 / CollectingSubscriber:400 / CollectingSubscriberWithSubscribe:622）恒无界授信，零有界 request；`FailoverStreamFlow` demand==0 分支 L380、`handleStreamComplete` L465、acquire L263 行号精确；墙钟 4 处 ：154/360/424/477 行号精确；`getCapabilities()` 生产消费方零命中；ORM UK 实证于 `nop-ai/model/nop-ai.orm.xml:1462-1465`；对照实现 `BufferedStreamingPublisher.handleComplete` pendingComplete（:328-337）实证在码。

### 3. 反向钉死核对（2 强 + 1 弱，方向与断言原文一致）

- **强钉死 ×2（C4-005/C8-003）**：受影响用例清单逐一定位证实（TestFeishuConnector 8 处 + TestFeishuConversationE2E 2 处 ≥ 引用所列），`awaitUntil` 改写载体确存于 TestChannelMessageService.java:62-76。
- **安全确认 ×3（C8-002/C8-001+C3-001+C2-001/registry）**：`TestChannelFanOutTimeoutCancel` 即时派发 + 协作监听器（javadoc :20-28 自证）下 deadline 锚点重合、池复用探针（:123-125）不受队列治理影响；`concurrencyCountHooksAtCallStreamInvocation`(:573) 与 `callerCancelStopsCurrentAttemptOnly` teardown 断言同复核推演成立；TestConcurrencyRegistry 两并发用例（:93-126/:154-200）护已修复原语、与 48 条零交集，"原语正确、编排泄漏"佐证成立。
- **弱钉死（C3-007 选项 1）**：`nonTransientStreamingErrorDegradesToTerminalResponse`(:351-369) 钉住空缓冲降级契约证实；选项 1 改 rethrow 会置 `collector.error` 非空 → 转红，方案依赖提示成立。

### 4. 缺保护登记复核（11/18 条深查）

- 格式：18 条编号连续（C10-001…C10-018），每条 12 字段齐备（高于附录 A 最低 10 项），证据片段/检索路径逐条可回溯。
- 覆盖闭合：18 条登记对 48 条保留发现**恰好唯一覆盖**（1+2+3+2+2+1+3+6+3+7+3+4+2+2+1+2+3+1=48），主归属唯一，无重复计数、无遗漏。
- 口径：「随本体回升」标注 C10-008/009/010/011/012 + C10-016（SPI 变体）与渠道栈休眠/零消费休眠口径一致；严重度 P2×15 + P3×3 与登记统计行一致；C10-011 唯一"假保护+反向钉死"型登记与矩阵 #32/#47 一致。
- 深查 11 条（C10-001/002/003/005/006/007/011/012/014/017/018）检索纪律全部证实（含 `saveMapping` 无并发用例、FakeHttpClient :182 起无回调异常注入、login 10+4 用例计数）。

### 5. 修正项（叙述性/计数性 5 处，均不影响任何三态判定与登记结论）

| # | 位置 | 问题 | 修正 |
|---|---|---|---|
| 1 | 基建事实 4（L23） | "startAll/stopAll 仅 E2E tearDown/setup 手工调用"不完整：单测 TestChannelConnectorManager 亦直接调用（:42/:61/:66） | 改为"无容器生命周期自动调用断言；现存调用均为测试手工调用（单测 + E2E setup/tearDown）"。矩阵 #17/#31 与 C10-010 证据行本身正确，不受影响 |
| 2 | 审计范围基线（L10） | 分桶 "failover 11、channel 12、routing 6、login 2、gate 1" 合计 32 ≠ 31（实际 failover 9、channel+feishu 11、routing 5、根包 3、login 2、gate 1） | 总数 31 正确，分桶多计 1 个文件；按实际分桶修注 |
| 3 | 系统性观察 1（L465） | "6 个订阅者 harness" 实际为 4 个 | 改为 4 |
| 4 | 系统性观察 3（L469） | "渠道栈 17 条休眠面"与矩阵实际标注〔随本体回升〕20 行不符 | 改为 20（或注明口径） |
| 5 | C10-013 证据片段（L349） | "adapter/interceptor 5 处 nanoTime" 实际 6 个调用点（adapter :230/:261/:289 + interceptor :269/:299/:326） | 改为 6；口径分裂结论不受影响 |

### 6. 判定统计

| 项 | 结果 |
|---|---|
| 矩阵 48 行三态判定 | 维持 48 ／ 修正 0 |
| 反向钉死 2 强 + 1 弱 | 全部维持（方向与测试断言原文一致） |
| 缺保护登记 18 条 | 保留 18 ／ 驳回 0 |
| **最终保留缺保护登记数** | **18 条（P2×15 + P3×3）**，与原登记统计一致 |
| 复核结论 | **C10 审计通过复核**。修正项 5 处均为叙述性勘误（上表已给出修正值，生效于本节），保护力矩阵与缺保护登记可直接作为修复立项输入（C10-001…C10-005 随 P1/P2 修复 roadmap 同批） |
