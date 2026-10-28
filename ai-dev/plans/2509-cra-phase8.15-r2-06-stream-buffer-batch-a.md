# 2509 CRA Phase 8.15 — R2-06 流式缓冲批 A（chain25/R02 双实现分叉，3.3 同文件批 A，11 项）

"> Plan Status: active
> Draft Review: R1 agent_1bf37222 @ 2026-10-28（0→1 Blocker、2 Major、3 Minor；基线 24 处 file:line 抽查全 PASS）——B1 C10-014 的 C3-005 半边归属自违勘误 3 原则[定夺：C3-005 半边吸收进成员 11，随裁定⑦(b) attempted 回收一次裁定，C10-014 验收 = C7-005 全 face + C3-005 半边同 diff]；M1 Related 误引 plan 2506[订正为 2505——MA-02/C6-004 溯源号]；M2 成员 11 RuleBasedSelectionStrategy 半边接线不可唯一实施[定夺：healthView 增第 5 标量 allowCall 纯追加 + 规则模型触面核验结论成文 + 红⑥断言落点 + C3-005 attempted 回收经 allowCall 冷却窗自然失效不引入新簿记]；Minor×3（pom :17 单向依赖预录[PA 预期不触发主路径]、4 进入点 :227/:273/:443/:462 枚举、cancel 落点 :164-166 一处两态澄清）——修订全量应用，转 active
> Last Reviewed: 2026-10-28
> Source: roadmap 工作项 8.15（R2-06，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L157，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-06 行（L80：finding ID 清单 11 项[2.3-C3-003；3.3-C2-001；3.3-C1-001；3.3-C3-001；3.3-C8-001；3.3-MA-05；3.3-C2-002；3.3-C2-009；3.3-C3-003；3.3-C3-007；3.3-C7-005] / 模块与验证命令 nop-service-framework + nop-ai[各自 `./mvnw test -pl <模块> -am`] / Owner Doc=`docs-for-ai/02-core-guides/service-layer.md` + `docs-for-ai/03-modules/nop-ai-gateway.md` / PA=无 / 依赖「无前置（可并行）」/ exit criteria 增补：**pendingComplete 正确先例对齐（同仓）、「共享有界缓冲组件」方向收敛双实现分叉、越窗上界+demand 修正、C2-002/C2-009 R03 锁内重活外移、C3-003 与 3.1 分类器跨模块协调注记；依赖注记：3.3-C7-002 拆出至 R4-32、G36 的 G-D 面（R4-17）批次对齐注记**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 2.3 审计目录 `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`（C3-async-chains.md C3-003 立案 :137 / C3-round2.md 失效面扩展 :66-71 / C8-backpressure.md B1 深化 :37 / merge-adjudication.md G-D 族 :75 / C10-test-protection.md P1-2+C10-002）与 WI 3.3 审计目录 `ai-dev/audits/2026-10/2026-10-20-1302-deep-audit-nop-ai-gateway-concurrency/`（C2-lock-sync.md C2-001/C2-002 + CL-003 / C1-shared-mutable-state.md C1-001 / C3-async-chains.md C3-001/C3-003 / C3-round2.md C3-007 / C7-round2.md C7-005 / C8-backpressure.md C8-001 / merge-adjudication.md MA-05/MA-07、裁定 1/3、§七跨单元 #1/2/14/17/19/21、§九 C10 映射、§8.4 同文件批 A 防碎片化条款），逐条回读路径见 Current Baseline；执行基线 @ `9ca5c3c8be`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.14 已 done，plan 2495–2508 均 completed；本 Baseline 全部行号为该基线 live 回读值，2026-10-28 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2505（R2-02，completed——**同域边界对齐（关键）**：`ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md` 为同一 BufferedStreamingPublisher/FailoverStreamFlow 域的既有设计产物——2505 已修 **4.3 侧**（`AbstractServerEventSubscription.waitForDemand` 有界等待 + 响应体阶段生命周期）与 **3.1-C3-001 族**（聚合层）；本批是 **2.3/3.3 侧**（缓冲编排层），零越界回改 4.3 传输层；2505 设计 §live 已核认 `StreamingProcessor`（:144/:162 request(1) 逐元素拉取派）与 `BufferedStreamingPublisher`（:378 自述）为其下游消费方——本批改动不得破坏该消费形态）；plan 2505（R2-02，completed——`sse-backpressure` 设计的 MA-02 族消费面回写与 C6-004 daemon 化即其 Source 明载范围，draft review M1 订正误引 2506）；plan 2508（R2-05，completed——前批，`service-layer.md` 新增「SSO 刷新异步化」「网关准入上界」两节后的现状为本批 owner doc 增量基线；**C3-003 的 AI 路由上游即网关出站**——2508 落地的 `UpstreamAdmission` 准入闸与 equal jitter 与本批 C8-001 的 demand 授信共享「上游快于下游」失效模型，裁定⑤须对照核验正交性）；**下游/并行协同**：R4-32（墙钟批次，3.3-C7-002 拆出）、R4-17（roadmap 8.57，G36 后半 G-D 面——`service-layer.md` 流式缓冲族相邻成员；R4-16 行 L198 明示软依赖 8.15）；R2-16（3.1 批，C5-001 无界缓冲族批次对齐 + 分类器跨模块协调的 3.1 侧）
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-06 行授权列）。live 回读勘误：**共享有界缓冲组件若裁定物理共享落点 nop-commons/nop-api-core（平台公共面）则触发 AGENTS.md「nop-api-core plan-first」Protected Area**——照 2507/2508 先例，该 PA 裁定在 Phase 1 裁定①内批内闭环（设计文档 + 回归测试面申报），实施前成文；若裁定①选定双实现各自收敛（对齐契约非物理共享）则无 PA 触发。本批不触碰 `model/*.orm.xml`、不做 `_` 前缀生成物改动、不改 nop-network 传输层（2505 已修面零回退）、不改 `ThresholdBreaker` 原语本体（C7-005 只修编排消费面，nop-ai-core 分类器面仅注记协调，见裁定⑦）。

## Purpose

关闭 R2-06 批的 11 项已确认发现（P1×4 / P2×7）。核心结果面有三：①**双实现分叉收敛**——`BufferedStreamingPublisher`（nop-gateway，2.3 侧）与 `FailoverStreamFlow`（nop-ai-gateway，3.3 侧）是同一「首段缓冲窗口 + 重订阅」机制的孪生实现，前者已带 `pendingComplete` 终态挂起正确先例、后者缺同一机制且越窗后缓冲无上界；以「共享有界缓冲组件」方向收敛两实现的缓冲/需求/终态语义（裁定①定形态）。②**3.3 同文件批 A**——`FailoverStreamFlow.java` 四条 live P1（C2-001/C1-001/C3-001/C8-001，merge-adjudication 裁定 1 判各自独立工单、同文件同批）一次闭合，消除监视器完整性、取消×装配窗口、终态 demand 语义、越窗上界四面缺陷。③**R03 锁内重活外移**——`StreamSubscription`/`BufferedSubscription` 两监视器内的下游投递与重试全链外移出临界区（C2-002/C2-009）。批末 roadmap 8.15 → done，11 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-06 = 11 项成员（P1×4：2.3-C3-003、3.3-C2-001、3.3-C1-001、3.3-C3-001、3.3-C8-001 中 C2-001/C1-001/C3-001/C8-001 四条；P2×7）；cra-index 台账：P1 四行独立（`nop-service-framework-hotpath-C3-003` L207、`nop-ai-gateway-C2-001（MA-07 主）` L258、`-C1-001` L259、`-C3-001` L260、`-C8-001` L261，均 open）+ P2-G58 家族行 L442（7 成员：MA-05、C2-002、C2-009、C3-003、C3-007、C7-005 入本批 + C7-002 拆出 R4-32）；roadmap 8.15 行 Status=todo。前置：无前置（可并行），R1-00～R1-08 与 R2-01～R2-05（plan 2495–2508）均 completed @ `9ca5c3c8be`——基线门禁已满足。
- **C10 验收前置入批集（勘误 3 定集）**：C10-001（C8-001 面）、C10-002（C3-001+C1-001 面）、C10-003（C2-001 面，C1-008/C1-009 成员随 MA-07 吸收）、C10-004（C2-002+C2-009 面）、C10-005（MA-05 面）、C10-006（C3-007 面）、C10-014（**C7-005 + C3-005 双面**——merge-adjudication 裁定表原样；draft review B1：初稿只取 C7-005 半边自违勘误 3「以裁定表为准」原则。处置 = **C3-005 半边吸收进成员 11**：超限重选 attempted 不可回收的回收语义随裁定⑦(b) allowCall 冷却复查一次裁定[remove 通道/revisit 形态]，C10-014 验收 = C7-005 全 face + C3-005 半边同 diff）**：两实现同源自 plan 2026-08-15-1116 系列（W7 gateway 形态 / W1 LOCAL spike），窗口语义注释逐字同构（`BufferedStreamingPublisher` :239-245 ↔ `FailoverStreamFlow` :348-352），但终态语义已分叉——前者 `handleComplete`（:328-337）以 `pendingComplete`（:141、:166-175）挂起终态等 request 冲空缓冲，后者 `handleStreamComplete`（:465-483）demand 耗尽即吞尾数据仍报 onComplete；越窗缓冲前者 :264-266、后者 :379-381 均无上界。merge-adjudication §七跨单元 #1 判「孪生实现同族、非同站点，修复同批联动（shared 有界缓冲组件方向）」。

### 11 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `9ca5c3c8be` 2026-10-28 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.3-C3-003（P1） | 2.3 / nop-service-framework（nop-gateway） | `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java`：`Attempt.buffer` 无界 ArrayDeque :119、越窗后 demand==0 元素入缓冲 :264-266（无上界）、映射链自驱动 request(1) :378（`StreamingProcessor.java` :144/:162 request(1) 两点）；C3-round2 扩展面：onNext 错误分支不 cancel 上游（`StreamingProcessor.createMappedPublisher` 链内， nop-gateway 侧回收缺位） | 映射链自驱动 request(1) 越窗后元素进无界 ArrayDeque，慢客户端长流内存失控；nop-ai-gateway 注入 retryCallback 使缓冲路径成为 AI 流式路由默认形态 | 裁定①共享有界缓冲组件收敛 + 越窗上界 + demand 门控（与裁定⑤同票）；onNext 错误分支补上游 cancel（与 C3-007 宿主链同文件协调） |
| 2 | 3.3-C2-001（P1，MA-07 主登记；C1-008 一行闭合吸收） | 3.3 / nop-ai（nop-ai-gateway） | `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java`：`startAttemptWithOptions` 取消检查 :235 / `registry.acquire` :263 / attempt 装配 :288 三步无原子区；`AttemptSubscriber.onSubscribe` 取消分支 :510-513 仅 `subscription.cancel()` 不回滚计数；对照非流式 `ChatServiceFailoverAdapter` whenComplete 恒 release（:246-274）无此缺陷 | 取消×重订阅交错窗口内新 attempt 的 acquire 无任何 release 路径——per-账号并发计数永久 +1 幽灵，饱和误判，无 gauge 需重启恢复 | 裁定②：acquire/release 配对——预设 onSubscribe 取消分支补 `releaseAttempt(state)`（覆盖全部 4 进入点交错变体（`startAttemptWithOptions` 调用点 ：227/:273/:443/:462——draft review m2 枚举；红④测试矩阵逐点对齐））+ 取消复查×装配互斥性按裁定②成文（`released` AtomicBoolean 守卫复用） |
| 3 | 3.3-C1-001（P1） | 3.3 / nop-ai（nop-ai-gateway） | 同文件 `handleStreamFailure` :413-463——:414-417 短暂持锁读 inWindow 后，**:438 `state.buffer.clear()` 为全文件唯一脱离 monitor 的 buffer 访问**；对峙面 `request()` :184-202（flushBuffer :197-200 只看 passed 不校验 attempt identity） | 窗口内失败清理（clear）与下游 request() 冲刷交错——attempt-1 已缓冲内容泄漏进最终输出或 ArrayDeque 撕裂致 onNext(null) | 裁定③：monitor 完整性——clear 移入 synchronized + flushBuffer/flushAll 入口 `state != attempt` 守卫（attempt-identity） |
| 4 | 3.3-C3-001（P1） | 3.3 / nop-ai（nop-ai-gateway） | 同文件 `handleStreamComplete` :465-483——`flushAll` :399-405 受 demand 约束，缓冲剩余元素被丢弃后仍 `subscriber.onComplete()` :481；**无 pendingComplete 机制（对照正确先例 `BufferedStreamingPublisher` :141/:166-175/:328-337）**；次级面：窗口内完成后 `flushBuffer` :386 passed 门控使 request 无法排空 | 终态冲刷受 demand 约束——缓冲剩余元素静默丢弃后仍发 onComplete，流式响应以成功截断（半个成功）；与自身 :397「不吞数据」注释矛盾 | 裁定④：**pendingComplete 先例对齐**——complete 路径缓冲非空时挂起终态，由 request() 冲空后补发 onComplete；次级面（窗口内完成）一并闭合 |
| 5 | 3.3-C8-001（P1；吸收 CL-001/003/005/012/014 五处） | 3.3 / nop-ai（nop-ai-gateway） | 同文件 `deliver` :371-382（**:380 `state.buffer.add` 无容量检查**）、容量门仅写在越窗前分支 :359、上游恒 `request(Long.MAX_VALUE)` :516 | 越窗后零需求缓冲无上界 + 上游 request(MAX)——下游慢/停滞时整条补全流无界积压（每条慢流一份完整响应体堆，OOM 面；token 全额计费面） | 裁定⑤：越窗上界（与窗口期同界复用 bufferSize）+ request 策略改与下游 demand 联动授信（阻塞 submit 语义不能反向节流时溢出显式 fail-loud 策略），与 2508 准入闸/2505 已修面零回退 |
| 6 | 3.3-MA-05（P2；成员 C3-004 主 + C7-001 吸收） | 3.3 / nop-ai（nop-ai-gateway） | `ChatServiceFailoverAdapter.java` `callAsyncWithOptions` whenComplete :246-274 三回调体（adapter :246 / interceptor `AiGatewayFailoverInterceptor.java` :284-310 / 终态 relay :319-351）无 try/catch(Throwable) 护栏 | 终态回调/信号回调内异常吞入无人消费的从属 stage——非流式 future 永不完成、流式订阅失去终态信号（不可检测挂死） | 裁定⑦处置（R21 纪律）：三回调体统一护栏 + completeExceptionally/deliverError 兜底（catch NopException 放宽语义按审计修复点）；属本批承接项（batch 行 11 成员之一），非 deferred |
| 7 | 3.3-C2-002（P2，R03 族） | 3.3 / nop-ai（nop-ai-gateway） | 同文件 `StreamSubscription` 监视器内下游投递四点：`deliver`→onNext :378、`flushBuffer`→onNext :392、`flushAll`→onNext :403、`handleStreamComplete`→onComplete :481、`deliverError`→onError :494；另 :472 `recordSuccess`（ThresholdBreaker 二级嵌套）锁内；正确先例对照 `subscribe()` :82-83 锁外调用 | 监视器内调用下游 Subscriber 未知代码——慢下游把锁持有时长放大为下游处理时长，request 与投递互相卡位 | 裁定⑥：R03 形态锁内重活外移——临界区只做状态机变更（锁内 poll 摘元素/记账），`subscriber.onX` 出锁后投递 |
| 8 | 3.3-C2-009（P2，R03 族） | 3.3 / nop-service-framework（nop-gateway；登记属 3.3 族） | `BufferedStreamingPublisher.java` `AttemptSubscriber.onError` :392-400 持 monitor 进入 `handleError` :298-326——**监视器内重试全链**：`retryCallback.retry` :304（决策 I/O）+ `startAttempt` :321（fetch 发起）均锁内 | BufferedSubscription 监视器内重试全链——重试决策与重订阅 fetch 在锁内执行，与 demand/request 交互相卡位 | 裁定⑥同票：重试决策与 startAttempt 外移出监视器（attempt 放弃判定留锁内），与 C2-002 同源临界区收窄 |
| 9 | 3.3-C3-003（P2） | 3.3 / nop-ai（nop-ai-gateway + nop-ai-core 分类器读面） | `ChatServiceFailoverAdapter.java` :246-274/:291-293/:324-333（取消→TRANSIENT→recordFailure+预算误耗）；孪生 `AiGatewayFailoverInterceptor.java` `decideNonStreamFailure` :328-330（:284-310）；分类器 `LlmErrorClassifier`（nop-ai-core，InterruptedIOException→TRANSIENT :98-101、CancellationException 兜底 TRANSIENT） | 调用方取消被分类为 TRANSIENT（可切换）——健康账号熔断记账污染 + 重试预算误耗 | 裁定⑦：whenComplete err 分支入口取消短路（isCancelled/cause 链判定 → completeExceptionally，不记账不耗预算）；「取消」提升为独立 ErrorClassification = **与 3.1（nop-ai-core 分类器）跨模块协调注记——仅注记不修分类器本体**（本批分类器零改动） |
| 10 | 3.3-C3-007（P2；C10-006 含修复方案弱钉死注记） | 3.3 / nop-ai（nop-ai-gateway；宿主链 nop-gateway） | `AiGatewayFailoverInterceptor.java` onError 降级分支 :404-427（:426 降级响应作为普通元素返回）、`onStreamComplete` :457-465（:463 失败 attempt 经内链 recordSuccess 记账反转）；宿主 `StreamingProcessor.java` :170-183（proceedOnError→onNext :175 + onComplete :176）；对照 `FailoverStreamFlow` deliverError :485-496 | NON_TRANSIENT 降级路径绕过缓冲层窗口丢弃语义——attempt-1 已缓冲前缀泄漏进最终输出（脏输出）+ 失败 attempt 被记熔断成功（伪装成功/记账反转）；与本地形态语义分裂 | 裁定⑦：降级信号显式化——降级响应不作为 attempt 数据元素进入缓冲通路（按 C10-006 弱钉死方向：内链识别降级终态 + 窗口内清缓冲 + 不 recordSuccess），与 C3-003 宿主链（StreamingProcessor）同文件协调 |
| 11 | 3.3-C7-005（P2） | 3.3 / nop-ai（nop-ai-gateway） | `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/DefaultSelectionStrategy.java` :36-48（唯一门控 `healthOf().isAvailable()` :40，从不调 allowCall）；同包 `CandidateHealth.java` :49-51（isCircuitOpen=state==OPEN 快照）、`CandidateHealthProvider.java` :36；同病 `RuleBasedSelectionStrategy.java` :201-211；原语语义 `nop-ai-core` `ThresholdBreaker` :165-219（OPEN→HALF_OPEN 惰性迁移仅在 allowCall） | 选择面绕过 allowCall——OPEN 冷却期满候选永久出局（容量单调流失不可自愈）+ HALF_OPEN 候选被健康视图视为完全可用（单探针契约在编排消费面失真） | 裁定⑦：选择门控补 allowCall 冷却复查维度（复用原语内建 probeTimeout 逃逸与互斥，不新建判定；allowCall 代理落点 CandidateHealthProvider 一处）+ HALF_OPEN 恢复期取舍成文；与 3.1 引擎侧（LlmCallCoordinator 正确消费）对齐为消费模式注记；C3-005 半边（attempted 不可回收）随裁定⑦(b) attempted 回收吸收（draft review B1） |

### 平台设施与测试资产盘点（live 实读 @ `9ca5c3c8be`）

- **正确先例（同仓，修复对齐基准）**：`BufferedStreamingPublisher.pendingComplete` 终态挂起机制（:141 声明 / :166-175 request 补发 / :328-337 挂起 / :193 cancel 清理）——3.3-C3-001 修复以它为语义基准（裁定④）；`subscribe()` 锁外调用先例（FailoverStreamFlow :82-83）——C2-002 外移方向依据；非流式 whenComplete 恒 release 先例（ChatServiceFailoverAdapter :246-274）——C2-001 兜底形态依据。
- **既有测试资产（回归门禁与测试基建）**：nop-ai-gateway `FailoverTestSupport`（**:399-401 恒 request(MAX)——C10-001 缺口本体，demand==0 分支全树零执行**）、`TestFailoverStreamCancelPin_C3_001`（取消钉，2505 族产物）、`TestChatServiceFailoverAdapterStreaming`/`TestAiGatewayFailoverInterceptorStreaming`（含端到端链路用例）、`TestChatServiceFailoverAdapterNonStreaming`；nop-ai-core `TestStreamFullChainEndToEnd_C3_001`（3.1-C3-001 族端到端，2505 产物=零回退钉）；nop-gateway `StreamingProcessorBufferingTest`（12 用例：`bufferedTailElementsDeliveredBeforeComplete` :771-797 ManualDemandSubscriber 真实门控、`inWindowFailureCallbackDeclinesForwardsError` :397-422、`abortAfterResubscribeCancelsNewSubscription` :624-671）、`GatewayFixTest`（**N1 虚假覆盖用例 `streamingProcessor_requestsOneByOne` 未实例化被测类——C10 侧已知虚假保护，本批重写为真实实例化钉**）、`InvokeProcessorRetryTest`/`TestGatewayUpstreamAdmission`/`ControlledUpstreamHttpClient`（2508 产物=零回退钉）。
- **2505 已修面（零回退边界）**：`AbstractServerEventSubscription.waitForDemand` 有界等待（`ERR_HTTP_SSE_DEMAND_TIMEOUT`，预算缺省 120s）——本批 request 策略改动（裁定⑤）改变 2.3 侧映射链对传输层的 demand 模式时，必须在裁定⑤中核认与该有界等待契约的组合行为（越窗授信节流不会把传输层推入伪停滞终态）。

### Baseline 勘误（输入不一致登记）

1. **模块触达勘误（batch 行模块列=nop-service-framework；nop-ai 基本完整，模块内部归属需展开）**：11 成员中 2.3-C3-003 与 3.3-C2-009 本体位于 `nop-service-framework/nop-gateway`；其余 9 项位于 `nop-ai/nop-ai-gateway`；3.3-C3-003 的分类器读面在 `nop-ai/nop-ai-core`（`LlmErrorClassifier`，本批零改动仅注记协调——PA 不触发）；3.3-C7-005 原语语义证据在 nop-ai-core `ThresholdBreaker`（零改动）。门禁按 batch 行两聚合执行即可，无第三聚合门禁增补（对照 2508 勘误 1 的跨聚合增补先例，本批不需要）。
2. **行号核对（审计→live，零实质漂移）**：C2-001 :234-298/:235/:263/:288/:510-513 ✓；C1-001 :413-463/:438/:184-202/:197-200 ✓；C3-001 :465-483/:399-405/:481/:386/:397 ✓；C8-001 :371-382/:380/:359/:516 ✓；C2-002 :353-405/:378/:392/:403/:481/:494/:472/:82-83 ✓；C2-009 :392-400/:298-326/:304/:321 ✓；2.3-C3-003 :119/:264-266/:378（审计载 :119,265——live 越窗 add 行 :265，±0，区间写法 :264-266）；pendingComplete 先例 :141/:166-175/:328-337 ✓；StreamingProcessor :144/:162/:170-183 ✓；MA-05 adapter :246-274 / interceptor :284-310/:319-351 ✓；C3-003 :246-274/:291-293/:324-333/:328-330 ✓（`LlmErrorClassifier` :98-101 为审计值，live 回读分类器文件属 nop-ai-core 本批零改动面，执行时如引用以 live 为准）；C3-007 :404-427/:426/:457-465/:463 ✓；C7-005 :36-48/:40/:49-51/:36/:201-211 ✓（`ThresholdBreaker` :165-219 为审计值，零改动面）。
3. **C10 归属标注勘误（batch-proposal L442 括注 vs 裁定表错位）**：batch 行括注「C10-004（MA-05 面）」与 merge-adjudication §九映射表（C10-004=C2-002+C2-009 面、C10-005=MA-05 面）错位——**以裁定表为准**，入批 C10 集 = C10-001/002/003/004/005/006/014 七条（见批次基线第 2 条），批量行括注不再单引。
4. **P2-G58 家族拆分登记**：cra-index L442 为 7 成员单行，本批收敛其中 6 成员 + 2.3-C3-003（L207）+ 3.3 四 P1（L258-261）；Phase 4 回填时 L442 行须**成员拆注**（6/7 收敛，C7-002 维持 open 归 R4-32），不得整行标 fixed。

## Goals

- Phase 1 设计裁定文档一次裁决八条裁定（独立 draft review 通过），无临场发明空间：①共享有界缓冲组件形态（收敛双实现分叉 + pendingComplete 先例对齐）；②C2-001 取消×重订阅 acquire/release 配对；③C1-001 monitor 完整性（clear 入锁 + attempt-identity 守卫）；④C3-001 终态冲刷 demand 修正（终态挂起）；⑤C8-001+C3-003 越窗上界 + request 授信策略（2505/2508 已修面零回退）；⑥C2-002/C2-009 R03 锁内重活外移；⑦C3-007/C7-005/MA-05/C3-003 处置 + 跨模块协调注记；⑧测试缝与零回退钉清单。
- 11 项成员全部修复并各带回归测试（rule 25）；C10 七条验收前置随本体同 diff 落地。
- 关键工单先红后绿或变异/回退论证留痕（E2）：四条 P1（越窗无界红/终态截断红/泄漏×撕裂红/幽灵计数红）+ C2-002/009 锁卡位红。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-ai-gateway.md`（缓冲窗口/终态/取消契约节扩展 + 源码锚点）+ `docs-for-ai/02-core-guides/service-layer.md`（流式缓冲组件契约，与 2508 两节并列，按裁定①落点定归属）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 2505 已修面的任何回改**：nop-network 传输层（`waitForDemand` 有界等待、body 阶段生命周期、daemon 承载）、3.1 聚合层（C3-001 族）——裁定⑤只核认组合行为，零变更。
- **不做 R4-32 成员**：3.3-C7-002（流式缓冲窗口 T 判定与 attempt 时长指标墙钟面）拆出至 R4-32 墙钟批次——本批触碰窗口时间判定时零语义改动（仍墙钟，勘误钉防顺手修）。
- **不做 R4-17（8.57）G-D 面成员**：`service-layer.md` 流式缓冲族相邻项（C3-004 重试钳制[非 MA-05 成员面]、C8-003 OpenAiDeltaAccumulator 聚合缓冲、C9-005 逐元素往返）归 R4-17 批次对齐——本批仅裁定⑧中作边界注记。
- **不做 R2-16（3.1 批）成员**：C5-001（3.1 无界缓冲族）批次对齐零承接；nop-ai-core `LlmErrorClassifier` 分类器本体零改动（「取消」独立分类归 3.1 侧协调，本批仅短路注记）。
- 不触碰 `ThresholdBreaker` 原语本体、`ConcurrencyRegistry` 原语层（原语已修，本批只修编排消费面）、`model/*.orm.xml`、`_` 前缀生成物；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-service-framework/nop-gateway`：`BufferedStreamingPublisher`（组件化收敛 + C2-009 锁外移 + 越窗上界）、`StreamingProcessor`（C3-003 链内错误分支上游 cancel + C3-007 宿主降级信号协调）+ 测试。
- `nop-ai/nop-ai-gateway`：`FailoverStreamFlow`（C2-001/C1-001/C3-001/C8-001/C2-002）、`ChatServiceFailoverAdapter`（MA-05 护栏 + C3-003 取消短路）、`AiGatewayFailoverInterceptor`（MA-05 孪生 + C3-007 降级显式化）、`routing/`（C7-005 allowCall 消费面）+ 测试。
- 设计文档：`ai-dev/design/concurrency/stream-buffer-unification-and-failover-flow-concurrency.md`（裁定①–⑧，最终状态写法）；Owner doc：`nop-ai-gateway.md`、`service-layer.md`、`source-anchors.md`。

### Out Of Scope

- Non-Goals 所列全部项；R4-32/R4-17/R2-16 及后续批次工单；C7-002/C5-001/C8-003/C3-004（G-D 相邻）等相邻缺陷本体；nop-ai-core 分类器与 ThresholdBreaker 本体。

## Execution Plan

### Phase 1 - 流式缓冲统一与 FailoverStreamFlow 并发修复设计裁定文档 + 独立评审

Status: planned
Targets: `ai-dev/design/concurrency/stream-buffer-unification-and-failover-flow-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [ ] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下八项裁定，每条唯一预设默认分支：
  - [ ] **裁定① 共享有界缓冲组件形态（收敛双实现分叉）+ pendingComplete 先例对齐**：(a) 组件落点——预设默认分支 = **物理共享组件**（窗口缓冲 + demand 账本 + pendingComplete 终态挂起语义一处的可复用组件），落点候选按依赖方向 live 核验定夺（nop-gateway 与 nop-ai-gateway 无相互依赖时，候选 nop-commons/nop-api-core——**该落点触发 plan-first PA，随本裁定批内闭环申报**；核验证明不可行则退化为「双侧对齐契约」：同一设计文档钉死容量语义/背压协议/终态语义，两实现各自收敛为同型）。(b) 容量语义——越窗前后同一上界（复用既有 bufferSize 配置，LOCK-001 两档语义不适用处显式声明）；(c) 背压协议——下游 demand 是唯一投递介质，上游授信策略归裁定⑤；onError 不 cancel 上游的 2.3 侧扩展面（C3-round2）并入组件语义。(d) pendingComplete 语义以 `BufferedStreamingPublisher` :141/:166-175/:328-337 为基准成文（cancel 清理半边 :193 一并纳入）。
  - [ ] **裁定② C2-001 acquire/release 配对**：预设默认分支 = `AttemptSubscriber.onSubscribe` 取消分支（:510-513）补 `releaseAttempt(state)` 一行覆盖全部 4 进入点交错变体 + `startAttemptWithOptions` 的取消复查×acquire×装配序列与 `teardownCurrentAttempt` 的互斥形态成文（结构性修复：装配/拆除同经监视器 vs 最小修复单行——两者取舍一次定夺，`released` AtomicBoolean 守卫复用）；非流式 whenComplete 恒 release 先例对照声明。
  - [ ] **裁定③ C1-001 monitor 完整性**：预设默认分支 = `:438 clear()` 移入 `synchronized(this)` + `flushBuffer`/`flushAll` 入口 `state != attempt` 守卫（attempt-identity，与 :521 判定同款）；`classifyStreamError` 等 I/O 重活在锁外的形态与裁定⑥协调（不得为满足③把重活拖回锁内）。
  - [ ] **裁定④ C3-001 终态冲刷 demand 修正**：预设默认分支 = **pendingComplete 先例对齐**——complete 路径缓冲非空且 demand 耗尽时挂起终态，request() 冲空后补发 onComplete；次级面（窗口内完成后 `flushBuffer` passed 门控 :386 无法排空）一并闭合；error 路径豁免面（:432-433/:490-493「错误优先」）维持审计豁免结论成文。
  - [ ] **裁定⑤ C8-001+C3-003 越窗上界 + request 策略**：(a) 越窗缓冲硬上界 = 与窗口期同界（bufferSize），溢出策略预设默认分支 = 显式 fail-loud（overload onError + 积压指标），禁无界 `ArrayDeque.add`（两实现同票）；备选「水位门（HighWatermarkSemaphore）」须论证 fail-loud 不满足网关语义后方可采。(b) request 策略——预设默认分支 = 上游授信与下游 demand 联动（`request(demand)` 替代恒 `request(MAX)` :516）；**LOCAL 形态上游若为阻塞 submit 语义不能反向节流时**，保留 request(MAX) + 溢出上界兜底的组合并成文（3.3-C3-001 审计修复方向 B 案的取舍在此定夺）；(c) 与 2505 已修面零回退核验——2.3 侧映射链 demand 模式变化 × `waitForDemand` 有界等待的组合行为核认（伪停滞终态排除）；与 2508 准入闸正交性声明（许可闸管在途请求数，本批管单流缓冲/授信）。
  - [ ] **裁定⑥ C2-002/C2-009 R03 锁内重活外移**：预设默认分支 = 临界区收窄——锁内仅状态机变更（buffer/demand/forwarded 记账 + poll 摘元素），`subscriber.onX` 出锁后投递（投递前 attempt-identity 复检），两监视器同型改造；C2-009 侧重试决策（`retryCallback.retry` :304）与 `startAttempt`（:321）外移出 monitor、attempt 放弃判定留锁内；出锁投递的信号序保证（同元素不乱序/终态最后）成文；`recordSuccess`（:472）二级嵌套外移面一并定夺。
  - [ ] **裁定⑦ C3-007/C7-005/MA-05/C3-003 处置 + 跨模块协调注记**：(a) C3-007——降级信号显式化（内链识别降级终态：窗口内清缓冲不泄漏 + 失败 attempt 不 recordSuccess + 降级响应不经数据元素通路），按 C10-006 弱钉死方向成文，`StreamingProcessor` 宿主链（:170-183）协调形态定夺；(b) C7-005（含 C3-005 半边）——选择门控补 allowCall 冷却复查（allowCall 代理落点 `CandidateHealthProvider` 一处）+ HALF_OPEN 恢复期取舍（单探针 vs 显式放开并发）预设默认分支定夺；**RuleBasedSelectionStrategy 半边接线形态（draft review M2 定夺）**：rule 脚本驱动侧健康视图经 `INPUT_HEALTH` 四标量注入（:86/:201-211）且 `NormalizeInputExecutableRule` 对未声明输入抛 `ERR_RULE_UNKNOWN_INPUT_VAR`——默认分支 = **healthView 增标量 `allowCall` 字段**（第五标量，纯追加对既有脚本零破坏）+ **规则模型源文件触面核验结论成文**（healthView 为代码构造 Map 非模型文件字段时零 PA 触碰；若核验发现规则模型 schema 需扩字段则按批内闭环先例申报 plan-first）+ 该半边红⑥断言落点（脚本内 allowCall=false 分支被门控）；**C3-005 attempted 回收形态**：重选预算耗尽路径的 attempted 标记经 allowCall=false 冷却窗自然失效（与 CandidateHealthProvider 冷却复查同源），remove 通道/revisit 不引入新簿记结构；(c) MA-05——三回调体 try/catch(Throwable) + completeExceptionally/deliverError 兜底（catch NopException 放宽），R21 纪律；(d) C3-003——whenComplete err 分支取消短路预设默认分支（`cancelToken.isCancelled()` 或 cause 链 CancellationException 判定 → completeExceptionally 不记账）；**跨模块协调注记（仅注记不修）**：「取消」独立 ErrorClassification 归 3.1/nop-ai-core 侧协调（R2-16 对接注记）、C7-005 与 3.1 LlmCallCoordinator 消费模式对齐注记、C8-004（Retry-After 上游信号）同批评估注记归其批次。
  - [ ] **裁定⑧ 测试缝与零回退钉清单**：测试缝 = `FailoverTestSupport` demand 受限订阅者改造（:399-401 恒 request(MAX) 是 C10-001 缺口本体——新增 ManualDemand 型可控授信缝，不得破坏既有恒 MAX 用例）+ `ScriptedPublisher` 发射计数/可控 future 桩 + C2-009 重试决策阻塞桩（锁外移验证）；零回退钉 = `StreamingProcessorBufferingTest` 12 用例全绿（重点 :771-797/:397-422/:624-671）、`TestFailoverStreamCancelPin_C3_001`、`TestStreamFullChainEndToEnd_C3_001`（nop-ai-core，2505 产物）、`GatewayFixTest` N1 用例重写（虚假覆盖→真实实例化钉）、`InvokeProcessorRetryTest`/`TestGatewayUpstreamAdmission`（2508 产物）、C7-002 墙钟语义勘误钉（本批零改动声明）；边界声明 = R4-32（C7-002）/R4-17（G-D 面）/R2-16（C5-001+分类器）三向零承接核对。
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑧全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [ ] 裁定①含组件落点 PA 核验结论（触发 plan-first 时申报与回归面清单成文）与双实现收敛形态；裁定⑤含 2505/2508 零回退核验结论；裁定⑦含三向跨模块注记；裁定⑧含零回退钉全列
- [ ] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 11 成员实现 + 回归测试（gateway 组 + ai-gateway 组）

Status: planned
Targets: `nop-service-framework/nop-gateway/src/`（executor、streaming 链）与 `nop-ai/nop-ai-gateway/src/`（failover、routing）——各自 main + test 树

- Item Types: `Fix`（11 项均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 七条验收前置）

- [ ] 组 A（nop-service-framework）：2.3-C3-003 实现（组件化收敛 + 越窗上界 + demand 门控 + 上游 cancel 扩展面）、3.3-C2-009 实现（重试全链外移）、3.3-C3-007 宿主链协调（`StreamingProcessor` 降级信号形态按裁定⑦）
- [ ] 组 B（nop-ai/nop-ai-gateway）：C2-001（acquire/release 配对）、C1-001（monitor 完整性）、C3-001（pendingComplete 对齐）、C8-001（越窗上界 + request 策略）、C2-002（投递外移）、MA-05（三回调体护栏）、C3-003（取消短路 + 协调注记落码面）、C3-007（拦截器降级显式化）、C7-005（allowCall 消费面）——全部按裁定①–⑦实施
- [ ] 回归测试（rule 25 显式覆盖 + C10 七条同 diff，先红后绿或变异/回退论证留痕）：
  - [ ] C8-001/C10-001 红①（越窗无界）：demand 受限订阅者（request(1) 后停滞）+ 长流上游——修复前 `AttemptState.buffer` 深度随元素数线性增长（越窗后无上界红）→ 修复后深度 ≤ bufferSize 上界 + 溢出路径类型化错误 + 上游授信不再恒 MAX（授信记账断言）
  - [ ] C3-001/C10-002 红②（终态截断）：demand 耗尽时上游 onComplete——修复前缓冲剩余元素被丢弃且收到 onComplete（尾部 chunk[finish_reason/usage] 丢失红）→ 修复后 request() 冲空缓冲后才收到 onComplete 且元素 exactly-once 全量交付；窗口内完成次级面（passed 门控）同钉
  - [ ] C1-001/C10-002 红③（泄漏×撕裂）：attempt-1 窗口内失败与下游 request(5) 交错（classifyStreamError 阻塞桩拉宽窗口）——修复前 attempt-1 已缓冲 chunk 泄漏进下游输出或 onNext(null) 红 → 修复后零泄漏零 null（attempt-identity 守卫断言：放弃 attempt 的缓冲永不交付）
  - [ ] C2-001/C10-003 红④（幽灵计数）：取消落在 :235 检查后/:263 acquire 后交错——修复前 registry 计数永久 +1（饱和误判红）→ 修复后全部 4 进入点交错下 acquire/release 配对（计数终值归零断言）
  - [ ] C2-002/C2-009/C10-004 红⑤（锁卡位）：慢下游 onNext 阻塞桩——修复前 `request()` 被投递锁卡位（计时断言红）/重试决策持锁（monitor 持有断言红）→ 修复后 request 不被下游 onNext 阻塞、重试决策在锁外（monitor 持有时长/线程断言）
  - [ ] P2 组红⑥：MA-05（回调体抛非 NopException——修复前 future 永不完成红 → 修复后 completeExceptionally）；C3-003（取消后 stage 异常完成——修复前 recordFailure+预算误耗红 → 修复后零记账零耗预算 + future 异常完成）；C3-007/C10-006（降级路径——修复前 attempt-1 前缀泄漏 + 失败 attempt recordSuccess 红 → 修复后零泄漏零记账反转）；C7-005/C10-014（OPEN 冷却期满候选——修复前永久出局红 → 修复后 allowCall 复查恢复可达；HALF_OPEN 恢复期行为按裁定⑦断言）
- [ ] **端到端验证**（rule 22）：≥1 条 3.3 流式 failover 全链测试（入口 `callStream`/拦截器流式路径 → attempt 失败 → 重订阅 → 越窗 → demand 受限消费 → 终态交付 → 出口点=订阅者收到的元素序列与终态信号 exactly-once 且零泄漏，含一次取消路径 teardown 配对）；≥1 条 2.3 缓冲链路测试（`StreamingProcessor` 映射链 → BufferedStreamingPublisher 组件 → ManualDemandSubscriber 出口点=尾部元素交付 + onComplete 时序，复用/重写 `bufferedTailElementsDeliveredBeforeComplete` 形态）
- [ ] **接线验证**（rule 23）：裁定①若物理共享组件——两实现真实调用组件断言（mock verify/计数器，非 import 自证）；C7-005 allowCall 代理真实被选择路径调用断言；C3-003 取消短路真实在 whenComplete 链上生效断言
- [ ] **无静默跳过**（rule 24）：溢出策略 fail-loud 而非静默丢弃/排队；MA-05 护栏显式 completeExceptionally 而非空 catch；降级信号显式终态而非伪装 onComplete；新增公共分支无空方法体/TODO
- [ ] **提交纪律**：设计文档先行单独提交；按模块分组提交——**组 A**（nop-gateway：C3-003+C2-009+C3-007 宿主）、**组 B**（nop-ai-gateway：其余成员），C10 测试与对应本体同一 commit；两模块无耦合时组 A/组 B 可独立提交序列
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 11 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 七条同 diff 落地
- [ ] 必红证据（E2）：红①–红⑥全部先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：`StreamingProcessorBufferingTest` 12 用例、`TestFailoverStreamCancelPin_C3_001`、`TestStreamFullChainEndToEnd_C3_001`、`InvokeProcessorRetryTest`、`TestGatewayUpstreamAdmission` 全绿（钉零回改项逐条 git diff 为空核对）；2505 已修面（nop-network）零 diff
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-service-framework -am` BUILD SUCCESS；`./mvnw test -pl nop-ai -am` BUILD SUCCESS；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试（nop-service-framework / nop-ai）；`docs-for-ai/03-modules/nop-ai-gateway.md`；`docs-for-ai/02-core-guides/service-layer.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2508 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-service-framework -am` + `./mvnw test -pl nop-ai -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`、`_` 前缀生成物、nop-network（2505 已修面）、nop-ai-core 分类器与 `ThresholdBreaker` 本体（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`nop-ai-gateway.md`——「缓冲窗口（gateway.xdef `streaming`）」节（:152）扩展为含越窗上界/终态挂起/取消配对/降级信号契约（内容源 = Phase 1 设计文档，派生摘要标注「以设计文档为准」）+ 形态 B（:77）本地流式契约同步 + 源码锚点节（:289）更新；`service-layer.md`——流式缓冲组件契约归属按裁定①落点落节（物理共享平台组件时与 2508 两节并列新建；双侧对齐时最小注记）；`source-anchors.md` 新增流式缓冲锚点（按实际）；缺口声明：按实际 diff 核对触达模块 03-modules 覆盖面（nop-gateway 无独立 03-modules 条目——沿 2508 缺口声明先例登记）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-service-framework --severity high`、`--module nop-ai --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-ai-gateway.md 契约节扩展 + service-layer.md 归属落定 + source-anchors.md 锚点 + 缺口声明登记；check-doc-links --strict 退出码 0；scan-hollow 两聚合模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/生成物/nop-network/nop-ai-core 分类器与原语本体）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.15 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（组件/配对/守卫/外移运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：L207（2.3-C3-003）、L258（C2-001）、L259（C1-001）、L260（C3-001）、L261（C8-001）状态收敛（open → fixed，注记 plan 2509）；L442（P2-G58）**成员拆注**（6/7 收敛，C7-002 维持 open 归 R4-32）；C10 七条收敛注记；roadmap 8.15 行 Status → done；R4-16 软依赖 8.15 的就绪注记（沿 2508 对 R4-16 先例）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 5 独立行收敛 + L442 拆注 + C10 七条注记、roadmap 8.15 = done、R4-16 对接注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 11 项成员全部修复：2.3-C3-003（组件收敛+上界+门控+cancel）、3.3 四 P1（配对/完整性/终态挂起/越窗上界+授信）、P2 六项（MA-05 护栏、C2-002/C2-009 外移、C3-003 短路、C3-007 降级显式化、C7-005 allowCall）
- [ ] batch-proposal R2-06 行 exit criteria 增补五条逐条达成并留证：①**pendingComplete 正确先例对齐**（终态挂起语义与 `BufferedStreamingPublisher` 基准逐条对照成文 + 回归钉）；②**「共享有界缓冲组件」方向收敛双实现分叉**（组件落点/容量语义/背压协议按裁定①落地，两实现缓冲语义同型断言）；③**越窗上界+demand 修正**（缓冲深度 ≤ bufferSize 断言 + 授信策略落地）；④**C2-002/C2-009 R03 锁内重活外移**（monitor 持有时长/锁外投递断言）；⑤**C3-003 与 3.1 分类器跨模块协调注记**（注记成文 + 分类器本体零 diff 核对）
- [ ] 依赖注记守约：3.3-C7-002 拆出 R4-32（墙钟语义零改动勘误钉在案）、G36 的 G-D 面归 R4-17 批次对齐注记成文、R2-16（C5-001/分类器）零承接核对
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；四 P1 + 锁卡位均有先红后绿或变异/回退论证（E2）；C10 七条同 diff 落地
- [ ] 设计文档裁定①–⑧成文且经独立 draft review；既有钉（StreamingProcessorBufferingTest 12 用例/CancelPin/E2E 链路/2508 门禁组）零回退实证
- [ ] 2505 已修面零回退：nop-network 零 diff + 裁定⑤组合行为核验结论在案
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-service-framework -am`、`./mvnw test -pl nop-ai -am` BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：nop-ai-gateway.md（契约节扩展+锚点）、service-layer.md（归属落定）、source-anchors.md（锚点）；缺口声明登记（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）共享组件/配对守卫/终态挂起/锁外投递/降级信号运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（受影响聚合模块）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 5 独立行 + L442 拆注收敛 + roadmap 8.15 → done（E6）

## Deferred But Adjudicated

### 3.3-C7-002（流式缓冲窗口 T 判定与 attempt 时长指标墙钟面）

- Classification: `out-of-scope improvement`（batch-proposal R2-06 行依赖注记显式拆出，非本批降级）；Successor Required: `yes`；Successor Path: R4-32 批 plan（尚未立项；roadmap 8.60+ 墙钟批次族）
- Why Not Blocking Closure: batch 行明示「3.3-C7-002 拆出至 R4-32」——时钟选择面独立成批（与 R4-13/R4-09/R4-20 的 C7 拆出同型）；本批窗口时间判定零语义改动（勘误钉防顺手修）。

### G36 的 G-D 面（C3-004 重试钳制非 MA-05 面、C8-003 OpenAiDeltaAccumulator、C9-005 逐元素往返）

- Classification: `out-of-scope improvement`（batch-proposal R2-06 行「G36 的 G-D 面（R4-17）批次对齐注记」显式安排）；Successor Required: `yes`；Successor Path: R4-17 批 plan（roadmap 8.57）
- Why Not Blocking Closure: G-D 族相邻成员归属 R4-17（cra-index G-D 族行批次安排）；本批已收敛 G-D 的 P1 主体（C3-003）与同文件批 A 全部成员，剩余为族内相邻面零重复。

### 3.1-C5-001（3.1 无界缓冲族）与「取消」独立 ErrorClassification 分类器扩展

- Classification: `out-of-scope improvement`（merge-adjudication §七跨单元 #2 批次对齐 + #19 跨模块协调，非本批降级）；Successor Required: `yes`；Successor Path: R2-16 批 plan（3.1 批）+ 分类器协调落点
- Why Not Blocking Closure: 判级对齐零重复——C5-001 归 3.1 批修复；分类器本体（nop-ai-core）本批零改动，本批 C3-003 短路已消除记账污染 live 面，独立分类为契约增强归 3.1 侧协调（注记在裁定⑦成文）。

## Non-Blocking Follow-ups

- **R4-16/R4-17 对接注记**：本批落地后向 R4-16 作「流式缓冲批 A 就绪」完成注记（R4-16 行 L198 软依赖 8.15 的落点）；R4-17 G-D 面实施时以本批组件化后的 live 形态为基线。
- **C8-004（上游 Retry-After 信号链）同批评估注记**：merge-adjudication 独立 #27 与 C7-005 同批评估「平台信号消费面」——本批 C7-005 修复后随其批次复核该注记是否仍成立。
- no remaining plan-owned work（占位，closure 时按实际复核重写）

## Closure

Status Note: （closure 时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + 证据来源；check-plan-checklist 退出码；Anti-Hollow 结果；Deferred 分类检查）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
