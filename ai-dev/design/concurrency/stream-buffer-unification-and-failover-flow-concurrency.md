# 流式缓冲统一与 FailoverStreamFlow 并发修复设计（Stream Buffer Unification and Failover Flow Concurrency）

> Status: active（plan 2509 Phase 1 产出；独立 draft review R1+R2+R3 通过，0 Blocker/0 Major）
> Last Reviewed: 2026-10-28 落笔（行号 = live @ `8ef804c58f` 回读值，该 HEAD 产品代码与 plan 2509 执行基线 `9ca5c3c8be` 零产品漂移；执行前如漂移以 live 为准并登记）
> Source: plan 2509（`ai-dev/plans/2509-cra-phase8.15-r2-06-stream-buffer-batch-a.md`）Phase 1 裁定①–⑧义务；发现事实源 = WI 2.3 审计目录 `ai-dev/audits/2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency/`（C3-async-chains C3-003 :137 / C3-round2 :66-71 / C8-backpressure B1 :37 / merge-adjudication G-D 族 :75 / C10-test-protection P1-2+C10-002）与 WI 3.3 审计目录 `ai-dev/audits/2026-10/2026-10-20-1302-deep-audit-nop-ai-gateway-concurrency/`（merge-adjudication §五裁定 1/3、§七跨单元 #1/#17/#19、§九 C10 映射、MA-05/MA-07、C2-lock-sync C2-001/C2-002、C1-shared-mutable-state C1-001、C3-async-chains C3-001/C3-003、C3-round2 C3-007、C7-round2 C7-005、C8-backpressure C8-001）；批次归组 = batch-proposal §二.2 R2-06
> 实现锚：`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/`（BufferedStreamingPublisher / StreamingProcessor）+ `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/`（FailoverStreamFlow / ChatServiceFailoverAdapter / AiGatewayFailoverInterceptor）+ `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/`（DefaultSelectionStrategy / RuleBasedSelectionStrategy / CandidateHealth / CandidateHealthProvider）

## 一、设计结论

1. **裁定①（共享有界缓冲组件，收敛双实现分叉）**：终裁 = **物理共享**——泛型有界窗口缓冲组件落 `nop-gateway`（非 nop-commons/nop-api-core，**plan-first PA 不触发**；依赖方向 live 核验：nop-ai-gateway 模块 pom :16-18（`<artifactId>nop-gateway</artifactId>` 位于 :17）已单向依赖 `nop-gateway`，draft review m1 预录成立）。组件形态 = 承载「窗口缓冲（容量 bufferSize 前后同界）+ passed/forwarded 窗口态 + demand 记账 + pendingComplete 终态挂起 + cancel 清理」六合一状态核的泛型核心（元素类型参数化），由两实现各自持有并以自身 monitor 保护；**fetch/重试编排不上移**（2.3 侧重执行回调产 HttpRequest、3.3 侧 selectNextWithProbe+acquire 生命周期，编排形态互异，属各侧职责）。两侧行为差异仅剩上游授信策略（裁定⑤(b) 分侧定夺），缓冲/需求/终态语义同型。迁移次序 = 先落组件 + 2.3 侧接入（BufferedStreamingPublisher 改为组件宿主，`StreamingProcessorBufferingTest` 12 用例全程零回退绿）→ 再 3.3 侧接入（同步落裁定②③④⑥B 组改造，`TestFailoverStreamCancelPin_C3_001` 零回退）。
2. **裁定②（C2-001 acquire/release 配对）**：终裁 = **最小修复单行**——`AttemptSubscriber.onSubscribe` 取消/放弃分支（:510-513）补 `releaseAttempt(state)`（`released` AtomicBoolean CAS 复用，幂等防双释放），一行漏斗覆盖全部 4 进入点（:227/:273/:443/:462）× 全部交错时序；**拒绝结构性监视器互斥形态**（把取消复查×acquire×装配移入监视器会把 `callStream` 发起拖进临界区，与裁定⑥临界区收窄方向冲突）。非流式 `whenComplete` 恒 release 先例（adapter :246-280）为兜底形态对照：流式路径以 onSubscribe 漏斗行取得等价的「有 acquire 必有 release」配对性。
3. **裁定③（C1-001 monitor 完整性）**：终裁 = `handleStreamFailure` 的 `state.buffer.clear()`（:438，全文件唯一锁外 buffer 触点）移入 `synchronized(this)` + `flushBuffer`/`flushAll` 入口补 `state != attempt` attempt-identity 守卫（与 :521 判定同款，纵深防御第二层）；`classifyStreamError`（文件 I/O 重活）维持锁外（:414-417 短暂持锁读 inWindow 的既有形态），不得为满足本裁定把分类 I/O 拖回锁内（与裁定⑥协调）。
4. **裁定④（C3-001 终态挂起）**：终裁 = **pendingComplete 先例对齐**——`handleStreamComplete`（:465-483）`flushAll` 后缓冲非空且 demand 耗尽时置 `pendingComplete = state` 并返回，`request()`（:184-202）冲空缓冲后补发 `onComplete`；语义基准 = `BufferedStreamingPublisher` :141（声明）/:166-175（request 补发）/:328-337（挂起）/:193（cancel 清理半边——取消后挂起终态不再补发）。次级面（窗口内完成 `passed=false` 时 flushBuffer :386 passed 门控排空不能）随同一机制闭合（`flushAll` 不受 passed 门控，pendingComplete 冲刷走 flushAll）。**error 路径豁免维持审计结论**：窗口外失败 `flushAll` 后 onError（:432-433）与 `deliverError` 清缓冲 + onError（:490-493）的「错误优先、缓冲剩余丢弃」语义成文豁免——错误终态无「半个成功」数据完整性违约面。
5. **裁定⑤（C8-001+C3-003 越窗上界 + request 策略）**：(a) 越窗缓冲硬上界 = 与窗口期同界（两侧各复用既有 bufferSize），溢出策略 = **显式 fail-loud**（overload onError 类型化错误 + 积压深度指标），禁无界 `ArrayDeque.add`（两实现同票）；(b) request 策略**分侧唯一定夺**——判定规则 = **上游是否为阻塞 submit 语义**：2.3 侧上游为 SSE fetch + 自驱动映射链（`StreamingProcessor` :144/:162 request(1)），demand 可插门控 → 映射链 request(1) 前置缓冲层 demand 检查（demand==0 不拉取，上游 TCP 反压自然传导）；3.3 侧上游为 delegate `callStream` 的阻塞 submit publisher（`FailoverStreamFlow` :515-516 spike 实证「request(MAX) 避免缓冲积压导致 submit/close 阻塞」）→ **保留 request(MAX) + 越窗上界 fail-loud 兜底**（审计 B 案），不做 demand 联动（联动会使 demand==0 时阻塞 HTTP 客户端接收线程、 stall 整条连接）。(c) 零回退核验：2.3 侧 demand 门控改变的是「网关缓冲层→上游 SSE fetch」的拉取节奏，2505 `waitForDemand` 有界等待位于「服务端 SSE→客户端」传输层，中间隔缓冲层 fail-loud 终态——越窗授信不产生新的无限等待面，也不把传输层推入伪停滞（伪停滞防御由 2505 预算承担，本批不触碰）；2508 `UpstreamAdmission` 准入闸管「在途请求数」（per-URL 许可闸 + equal jitter），本批管「单流缓冲/授信」，维度正交、零共享代码位。
6. **裁定⑥（C2-002/C2-009 R03 锁内重活外移）**：终裁 = 两监视器同型临界区收窄——锁内仅状态机变更（buffer/demand/forwarded/pendingComplete 记账 + `poll()` 摘元素入待投递快照），`subscriber.onX` 出锁后投递。投递四点（+终态两点）逐一处置：`deliver`→onNext（:378）、`flushBuffer`→onNext（:392）、`flushAll`→onNext（:403）、`handleStreamComplete`→onComplete（:481）、`deliverError`→onError（:494）——全部改为「锁内摘取/置位，出锁投递，投递前 attempt-identity 复检」；**信号序保证**：待投递队列出锁段以单一 drain 守卫（投递串行化标志）串行消费，保证同元素不乱序、终态最后；`recordSuccess`（:472，ThresholdBreaker 二级嵌套）出锁（锁内仅置成功标志）。C2-009 侧：`retryCallback.retry`（:304，决策 I/O）与 `startAttempt`（:321，fetch 发起）外移出 `BufferedSubscription` monitor，attempt 放弃判定（:372 同款 identity/cancel 检查）留锁内。
7. **裁定⑦（C3-007/C7-005/MA-05/C3-003 处置 + 跨模块协调注记）**：(a) C3-007 降级信号显式化——按 C10-006 弱钉方向，`StreamingProcessor` onError 内链（:170-183）在缓冲接管形态下降级响应**不再作为普通元素 onNext+onComplete 进入 AttemptSubscriber 通路**，改为显式降级终态（窗口内清缓冲 + 降级 wrapper 经降级终态交付 + 成功终态），直通形态保持既有行为零回归；「失败 attempt recordSuccess 记账反转」按 C3-round2 复核驳回结论**不成立、不实施守卫**（proceedOnStreamComplete 仅由源完成 lambda 驱动，降级路径不可达，live 核验 :186-192 证实——Baseline 不一致登记见 §六）；(b) C7-005——选择门控补 allowCall 冷却复查：**allowCall 门控双面收敛：healthView 第 5 标量为纯读投影（`ThresholdBreaker` 新增只读 `cooldownElapsed` 查询承载冷却感知）+ 权威状态迁移 allowCall 移至两策略「选中后、返回前」唯一调用点（不得入 healthOf——draft review R2 Major-B 同步，与 §3.7(b) 一致）**（其「只做读取投影」不变式同步修订为「读取投影 + 唯一 allowCall 代理」），`CandidateHealth` 增 `allowCall` 字段（第 5 标量）；**HALF_OPEN 取舍 = 单探针语义维持**（复用原语内建 probeTimeout 逃逸与并发互斥，不显式放开恢复期并发——恢复期候选被多请求并发击穿会以噪声主导探活记账）；**RuleBased 半边**：healthView 为 `RuleBasedSelectionStrategy.buildInputs` 代码构造 Map（:201-211），规则模型 schema（`rule.xdef` `<input name="health"/>`）只声明顶层输入名、不声明 Map 内键——**增第 5 标量 `allowCall` 为纯代码侧追加，零 PA 触碰、对既有规则脚本零破坏**（触面核验亲读：测试规则资源 `_vfs/nop/rule/rule-selection/*/v1.rule.xml` 均以 `<input name="health"/>` 整体声明，`NormalizeInputExecutableRule` 校验仅及顶层）；红⑥断言落点 = 新增规则脚本消费 `health[i].allowCall == false` 分支（allowCall=false 候选被门控）。**C3-005 半边（B1 吸收）**：attempted 集合维持请求级生命周期（单请求内不回收、请求结束自然失效），不做 remove/revisit 新簿记——其「冷却期满候选不可再达」后果由 allowCall 冷却复查恢复可达承载，C10-014 验收 = C7-005 全 face + 该半边同 diff；(c) MA-05——三回调体（adapter :246-280 / interceptor :284-321 / 终态 relay `completeRelay` adapter :336 / interceptor :380）统一 try/catch(Throwable) + completeExceptionally/deliverError 兜底（catch NopException 放宽语义按审计修复点），R21 纪律；取消短路记录见 (d)「取消独立分类」归 3.1 仅注记（§3.7）；(d) C3-003——whenComplete err 分支入口取消短路：`cancelToken.isCancelled()` 或 cause 链 `CancellationException` 判定 → `completeExceptionally` 不记账不耗预算（adapter `decideNonStreamFailure` :289-293 + interceptor 孪生 :323-330 同 diff）；跨模块注记（仅注记不修）：「取消」独立 ErrorClassification 归 3.1/nop-ai-core（R2-16 对接）、C7-005 与 LlmCallCoordinator 引擎侧消费模式对齐、C8-004 Retry-After 信号链归其批次。
8. **裁定⑧（测试缝与零回退钉）**：测试缝 = `FailoverTestSupport.CollectingSubscriber`（:396-401 恒 request(MAX)）旁新增 ManualDemand 型可控授信订阅者（既有恒 MAX 用例零破坏）；`ScriptedPublisher` 发射计数/可控 future 桩 + C2-009 重试决策阻塞桩。零回退钉全列见 §七。

## 二、背景与动机

R2-06 批的三面缺陷域（chain25/R02 双实现分叉 + 3.3 同文件四 P1 + R03 锁内重活）：

- **双实现分叉线**：`BufferedStreamingPublisher`（nop-gateway，2.3 侧）与 `FailoverStreamFlow`（nop-ai-gateway，3.3 侧）同源自 plan 2026-08-15-1116 系列，窗口语义注释逐字同构（:239-245 ↔ :348-352），但终态语义已分叉：前者 `handleComplete`（:328-337）以 pendingComplete 挂起终态等 request 冲空缓冲，后者 `handleStreamComplete`（:465-483）demand 耗尽即丢弃尾数据仍报 onComplete（半个成功，C3-001）；越窗缓冲两者均无上界（:264-266 / :379-381，C3-003/C8-001——慢客户端长流每流一份完整响应体堆 + request(MAX) 全额 token 计费面）。
- **FailoverStreamFlow 同文件四 P1 线**（merge-adjudication 裁定 1 判各自独立工单同批复 A）：取消×装配窗口 acquire 永久泄漏（C2-001，幽灵计数 → 账号饱和误判）；失败清理 clear 锁外与 request 冲刷交错（C1-001，跨 attempt 前缀泄漏 / ArrayDeque 撕裂 onNext(null)）；终态冲刷受 demand 约束静默截断（C3-001）。
- **R03 锁内重活线**：`StreamSubscription` 监视器内下游投递四点（C2-002，慢下游把锁持有时长放大为下游处理时长、request 与投递互相卡位）；`BufferedSubscription` 监视器内重试全链（C2-009，决策 I/O + fetch 发起锁内）。

前批边界：plan 2505 已修 4.3 传输层（`waitForDemand` 有界等待 + body 阶段生命周期）与 3.1 聚合层——本批为 2.3/3.3 缓冲编排层，零越界回改；plan 2508 落地网关出站准入闸——与本批 C8-001 共享「上游快于下游」失效模型但维度正交。

## 三、核心设计

### 3.1 裁定①：共享有界窗口缓冲组件（物理共享，落 nop-gateway）

**协议差异亲读核验**（落点定夺依据）：两实现的**下游订阅协议同型**（`Flow.Publisher`/`Flow.Subscription`、单订阅者、monitor 保护的 `ArrayDeque` buffer + demand long、N/T 先到者越窗、pendingComplete 语义候选），**上游协议异型**：2.3 侧上游 = `httpClient.fetchServerEventFlow` SSE 流经映射链（`createMappedPublisher`），demand 由映射链 `subscription.request(1)` 显式驱动（可插门控）；3.3 侧上游 = delegate `callStream` 返回的阻塞 submit publisher，demand 语义仅表现为「submit 阻塞与否」（不可插门控）。**元素类型异型**（`Object` vs `ChatStreamChunk`）。据此刻定：可共享的是**状态核**（缓冲/窗口/demand/终态挂起/取消清理），不可共享的是**上游授信与编排**（裁定⑤(b) 分侧、fetch/重试编排各侧保留）——泛型组件参数化元素类型，授信以策略面分侧注入。

**PA 核验结论**：落点 = `nop-gateway`（`io.nop.gateway.core.executor`）——nop-ai-gateway 模块 pom :16-18 既有单向依赖 `nop-gateway`（`<artifactId>nop-gateway</artifactId>` 位于 :17），反向零依赖；nop-gateway 非 `nop-commons`/`nop-api-core` 平台公共面，**AGENTS.md plan-first PA 不触发**，无批内申报义务（draft review m1 预录「PA 预期不触发主路径」live 证实）。

**组件契约（draft review Minor-1 补行：含 **disabled 直通模式**[2.3 侧 bufferEnabled=false 零缓冲直传，3.3 侧无此档]，泛型核显式承载该档）**（泛型核心，命名归实现）：容量语义 = bufferSize 越窗前后同一上界（窗口期既有 N 满越窗 + T 超时越窗双门维持，越窗后缓冲 add 前容量检查同界——两档语义差异显式声明：窗口期满 = 触发越窗，越窗后满 = fail-loud 溢出）；背压协议 = 下游 demand 是唯一投递介质（request 累加、投递递减、越窗不追补授信），上游授信策略面分侧（3.1 不含 request 决策）；终态挂起 = complete 路径冲刷后缓冲非空即挂起，request 冲空补发，cancel 清理挂起终态（:193 半边纳入）；错误语义 = 窗口内清缓冲 + 回调决策宿主挂钩、窗口外原样转发——宿主形态保留。`onError 不 cancel 上游`的 2.3 侧扩展面（C3-round2 :164-166）并入组件语义：错误终态路径由宿主链补 `subscription.cancel()`（该落点即 plan m3「cancel 落点 :164-166 一处两态」——同一行既是「catch 转 onError 不 cancel」缺陷站点、也是裁定⑤(b) demand 门控的授信插桩点，一处改造两态同闭合）。

**双侧落点与迁移次序**：先 2.3 后 3.3（§一.1）——2.3 侧先行使组件获得 pendingComplete/上界/门控的完整既有测试基线（12 用例），3.3 侧迁移与裁定②③④⑥同 diff 落地，避免组件语义两次返工。组件接入后两实现的 `buffer/passed/forwarded/demand/pendingComplete` 字段与窗口判定方法由组件承载，各侧保留的宿主面 = 上游订阅/授信（分侧策略）、失败决策（各侧分类/记账/重选）、生命周期回调（2.3 侧 lifecycle/retryCallback 扩展点）——组件不为编排面提供钩子以外的侵入点，防止宿主绕过组件语义直接操作缓冲。

### 3.2 裁定②：取消×装配窗口 acquire/release 配对（C2-001/MA-07）

**修复形态**：`AttemptSubscriber.onSubscribe`（:508-514）守卫分支改为「`subscription.cancel()` + `releaseAttempt(state)`」——`released` CAS 保证：与 `teardownCurrentAttempt`（:323-334）、`handleStreamFailure`（:418）、`handleStreamComplete`（:470）的释放路径并发时 exactly-once；放弃（非取消）变体下重复释放由 CAS 吸收为 no-op。

**4 进入点 × 交错变体矩阵**（每格终态 = 计数归零）：

| 进入点（调用位点） | 取消先于 :235 检查 | 取消落在检查后/acquire(:263) 前 | 取消落在 acquire 后/装配(:288) 前 | 取消落在装配后/subscribe(:292) 前/后 |
|---|---|---|---|---|
| :227 start() 首 attempt | 入口拦截，无 acquire | 无 acquire，无害 | acquire 已发生→漏斗行释放 | 漏斗行释放 |
| :273 超限重选递归 | 同上 | 同上 | 同上（此前 :269 已 release 超限候选，新候选 acquire 泄漏由漏斗行覆盖） | 漏斗行释放 |
| :443 CACHE_STATE_LOST 原地重发 | 同上 | 同上 | 同上 | 漏斗行释放 |
| :462 切换类重订阅 | 同上 | 同上 | 同上（审计触发路径） | 漏斗行释放 |

结构性监视器互斥形态被拒理由见 §四；非流式 whenComplete 恒 release 先例（adapter :246-280 全终止路径 release）为配对完备性的同仓对照锚。

### 3.3 裁定③：monitor 完整性（C1-001）

`clear()`（:438）入锁后，`AttemptState.buffer` 全部触点（:361 add / :380 add / :389-393 poll / :400-404 poll / :438 clear / :492 clear）均在 `StreamSubscription` monitor 内；`flushBuffer`/`flushAll` 入口 attempt-identity 守卫（`state != attempt` 拒绝冲刷）为纵深防御层——即使未来新增锁外触点或调用序漂移，放弃 attempt 的缓冲也永不交付。交错终态：attempt-1 已缓冲内容零泄漏进下游输出、零 onNext(null)。

### 3.4 裁定④：终态冲刷 demand 修正（C3-001）

`handleStreamComplete` 改为「releaseAttempt + recordSuccess + metrics（锁内记账段）→ flushAll → 缓冲空则 onComplete / 非空则置 pendingComplete 返回」；`request()` 增 pendingComplete 冲刷段（对齐 BufferedStreamingPublisher :166-175 形态：flushAll → 空则清标志 + 补发 onComplete）。与裁定⑥合成后：记账段锁内、补发 onComplete 出锁投递（终态最后序由 drain 守卫保证）。error 豁免边界成文：仅**成功终态**承担「缓冲非空不终止」义务；错误终态（:432-433/:490-493）以错误优先、剩余缓冲丢弃为显式语义（`deliverError` 清缓冲行为保留）。

### 3.5 裁定⑤：越窗上界 + request 策略（C8-001 + C3-003 主体）

**(a) 上界与溢出**：`deliver`/`handleItem` 越窗分支 add 前容量检查（同 bufferSize），满 = 类型化 overload onError（经宿主错误路径断流，R21 显式可观测）+ 积压深度指标；两实现同票;**溢出错误为不可重试终态（draft review Major-1）**：溢出 onError 不得进入 handleStreamFailure 分类/recordFailure/重选/重订阅决策（慢客户端溢出若按可切换失败处理将重新调用 LLM[计费]、污染熔断记账、耗尽预算后才断流，与 fail-loud 相悖且与 2508 准入闸在途管理冲突）——两实现同票：直接走窗口外断流路径 + releaseAttempt，红⑤断言含「溢出零重订阅零 recordFailure」。

**(b) request 策略判定规则（唯一）**：上游为阻塞 submit 语义（demand 联动会阻塞 HTTP 接收线程、stall 连接）→ 保留 request(MAX) + 上界兜底；上游为显式 request 驱动（demand 门控零额外阻塞代价）→ demand 联动。按此：**3.3 侧 = request(MAX) 保留 + fail-loud 兜底**（B 案）；**2.3 侧 = 映射链 request(1) 前置缓冲层 demand 检查**（`StreamingProcessor` :144/:162 两授信点经缓冲层 tryRequest 询问，demand==0 不 request——越窗后上游 SSE 自然停滞，缓冲上界成为第二道防线而非唯一防线）。

**(c) 零回退核验表**：

| 已修面 | 契约 | 组合行为核验结论 |
|---|---|---|
| 2505 `waitForDemand` 有界等待 | 停滞订阅者预算内错误终态 | 位于服务端 SSE→客户端传输层；本批 2.3 侧门控改变缓冲层→上游 fetch 拉取节奏，越窗 fail-loud 产生显式 onError 终态而非新等待面——传输层伪停滞终态排除，零回退 |
| 2505 MA-02 族/C6-004 daemon 化 | jdk/apache SSE 消费 daemon 化 | 本批不触碰 nop-network；`git diff` 零 nop-network 文件为 Phase 3 核对项 |
| 2508 `TestGatewayUpstreamAdmission` | 在途请求数许可闸 + equal jitter | 维度正交（在途请求数 vs 单流缓冲/授信），零共享代码位；该测试全绿为回归门禁 |

**配置契约**：本批**零新增配置键**——越窗上界复用既有 `bufferSize`（`GatewayStreamingModel.getBufferSize`/`adapter.getBufferSize`，缺省 10）、窗口期既有 N/T 双门维持（`bufferTimeMs` 缺省 1000）；溢出指标命名归 Phase 2 落地时按平台 Meter 命名惯例登记（积压深度 + 溢出计数，非本设计新配置面）。不引入独立「越窗上界」配置项：越窗上界与窗口期同界是裁定①(a) 的语义决定（同一缓冲同一容量），独立配置键即重开两档语义分叉面。

### 3.6 裁定⑥：R03 锁内重活外移（C2-002/C2-009）

**投递点处置表**：

| 站点（live） | 锁内保留 | 出锁投递 |
|---|---|---|
| deliver→onNext :378 | demand 递减/forwarded 置位/元素入待投递快照 | onNext（identity 复检后） |
| flushBuffer→onNext :392 | poll 循环摘元素 | 批量快照出锁逐个 onNext |
| flushAll→onNext :403 | 同上 | 同上 |
| handleStreamComplete→onComplete :481 | release/记账标志/pendingComplete 置位 | onComplete（drain 串行，终态最后） |
| deliverError→onError :494 | 清缓冲/终止置位 | onError |
| recordSuccess :472 | 成功标志置位 | 锁外 CircuitObservation.recordSuccess |
| request() 补发 onComplete（pendingComplete 冲空段，裁定④） | 出锁投递（draft review Minor-4 补行：先例 :166-175 在锁内 terminate，共享核统一为第 6 投递点，同 drain-loop 纪律） |

信号序保证：出锁投递段经单一 drain 守卫串行化（同一订阅同一时刻仅一线程在投递段）——**drain-loop 形态钉死（draft review Major-2）**：while 循环——持锁取快照 → 出锁投递 → 重入锁重查队列，空则清投递标志退出、非空继续消费（投递期间新入队快照由下一轮消费，无滞留、无通知原语）；「终态最后」由入队序保证（终态快照必位于在途快照之后，顺序 drain 即天然终态最后）；identity 复检失败仅允许丢弃「未 forwarded」的窗口内快照——已 forwarded（窗口外）attempt 的缓冲元素裁定为**丢弃 + WARN 留痕计数**（丢弃=数据丢失由上游 failover 重试承载，不丢弃=前缀泄漏风险），锁内追加序 = 快照序 = 投递序——同元素不乱序、终态最后。C2-009 侧（`BufferedSubscription` monitor）：`retryCallback.retry`（:304）与 `startAttempt`（:321）出锁，attempt 放弃判定（identity + cancelled，:372/:384/:394/:405 同款）留锁内——重试决策期间 demand/request 交互不被卡位。

### 3.7 裁定⑦：降级显式化、allowCall 消费面、回调护栏、取消短路

**(a) C3-007**：降级响应不经数据元素通路——`createMappedPublisher` onError 分支（:170-183）按「缓冲接管与否」分流：直通形态保持 onNext+onComplete（零回归）；缓冲接管形态经缓冲层**降级终态入口**（窗口内：清 attempt 缓冲 + 降级 wrapper 为唯一交付元素 + 成功终态；窗口外：缓冲空/已转发，降级 wrapper 尾元素 + 成功终态）。Baseline 勘误：plan 成员 10 行「失败 attempt 经内链 recordSuccess 记账反转」按 C3-round2 复核**驳回**（`proceedOnStreamComplete` 唯一调用点 :188 位于源完成 lambda 内，降级路径不可达，live :186-192 亲读证实）——recordSuccess 守卫不实施；C10-006 断言面 = 零前缀泄漏 + 降级显式终态（不含记账反转腿）。

**(b) C7-005（含 C3-005 半边）**——**调用时序裁定（draft review Major-3）**：`ThresholdBreaker.allowCall` 为带状态迁移写操作（OPEN→HALF_OPEN + 占 probe 槽），**不得入 `healthOf` 视图构建**（RuleBased buildInputs :201-211 对全部候选无差别调用，会为每个 OPEN 期满候选消耗幻影 probe 槽、无真实派发收口、恢复延迟放大 probeTimeout 量级）；定夺 = **healthView 第 5 标量为纯读投影**（非 OPEN、或 OPEN 且冷却已过即 true——不占槽不迁移，供脚本偏好排序）；**冷却感知读面（draft review R2 Major-A 定夺）**：投影判定「OPEN 且冷却已过」需原语暴露冷却进度，而 `ThresholdBreaker` 现仅 `getState`——定夺 = **为 `ThresholdBreaker` 新增只读查询 `cooldownElapsed(modelKey)`**（内部实现类非 nop-*-api，零 PA；对 plan「原语零改动」约束的偏离为只读追加、写面零变化，显式申报）——投影 = `state != OPEN || cooldownElapsed`，红⑥恢复可达腿保持成立。 **权威状态迁移 allowCall 调用移至两策略「选中后、返回前」单点**：Default 在 selectNext 返回前调用一次（`isAvailable() || allowCall 复查放行` 语义保持）；RuleBased 在脚本返回候选后调用一次，false 则跳过该候选尝试其余、全拒走既有不可用路径；真实探针由选中后唯一调用收口，幻影结构性消除；HALF_OPEN 恢复期取舍 = **单探针维持**（拒绝显式放开并发——多请求并发击穿恢复期候选使 recordSuccess/recordFailure HALF_OPEN 恢复期取舍 = **单探针维持**（拒绝显式放开并发——多请求并发击穿恢复期候选使 recordSuccess/recordFailure 被噪声主导）。RuleBased 半边（M2 定夺）：healthView 代码构造 Map 增第 5 标量 `allowCall`（`buildInputs` :201-211 一处），规则模型触面核验结论 = **零 PA**（`rule.xdef` 只声明顶层输入 `health`，Map 内键非 schema 面；既有规则脚本逐键读取，纯追加零破坏——测试规则资源亲读证实）；红⑥断言落点 = 新增规则脚本 `health[i].allowCall == false → 不选中` 分支。C3-005 半边：attempted 请求级生命周期维持（单请求内不可回收为既有语义、请求结束自然失效，不引入 remove/revisit 簿记），跨请求可达性由 allowCall 冷却复查恢复——C10-014 验收双面同 diff。

**(c) MA-05**：三回调体统一护栏——adapter `whenComplete` 体（:246-280）、interceptor `whenComplete` 体（:284-321）、两处 `completeRelay`（adapter :336-343 / interceptor :380-387）：try/catch(Throwable) → catch NopException 按既有语义传播、其余 completeExceptionally/deliverError 兜底（future 必达终态，流式订阅必达终态信号）。

**(d) C3-003**：`decideNonStreamFailure` 入口取消短路（adapter + interceptor 孪生同 diff）：`cancelToken.isCancelled()` 或 cause 链含 `CancellationException` → `completeExceptionally`，不 metrics.onRequestFailure、不 recordFailure、不耗预算。注记（仅注记不修）：「取消」独立 ErrorClassification 归 3.1/nop-ai-core 侧（R2-16）；C7-005 与 LlmCallCoordinator 消费模式对齐注记；C8-004 Retry-After 归其批次。

### 3.8 裁定⑧：测试缝与零回退钉

见 §七测试纪律契约。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 裁定①共享组件落 nop-commons/nop-api-core | 平台公共面 plan-first PA 触发面；nop-gateway 落点已被 pom :17 单向依赖覆盖，零 PA 且更近双实现消费现场 |
| 裁定①双侧同型契约（不物理共享） | 孪生实现已因「同型不同步」产出 C3-001 半个成功分叉；第三次分叉（如越窗上界只修一侧）风险随批次增加单调上升；物理共享使两侧行为同型可断言 |
| 裁定①把 fetch/重试编排一并上移组件 | 两侧编排形态互异（HttpRequest 重执行 vs selectNextWithProbe+acquire 生命周期），强行统一=重写而非收敛，超出批界 |
| 裁定②结构性监视器互斥（取消复查×acquire×装配入 monitor） | callStream 发起（网络调用发起）入临界区与裁定⑥收窄方向冲突；漏斗行 + released CAS 已取得配对完备性（§3.2 矩阵全格归零）。覆盖面前提（draft review Minor-2 注记）=delegate publisher 遵守 Reactive Streams onSubscribe 必达契约（仓内 delegate 为自家 SubmissionPublisher 形态，风险低） |
| 裁定③把 classifyStreamError 拖回锁内满足 monitor 完整性 | 文件 I/O 重活锁内化放大临界区（与 R03 相反方向）；短暂持锁读 inWindow + 锁内 clear 已闭合竞态窗 |
| 裁定④终态不受 demand 约束直接冲空（放弃 pendingComplete） | 违反 Flow 规范（超 demand 发信号）；pendingComplete 先例已是同仓正确基准，自造第三种终态语义即再次分叉 |
| 裁定⑤(b) 3.3 侧 demand 联动授信 | 上游阻塞 submit 语义下 demand==0 会阻塞 HTTP 客户端接收线程、stall 整条连接（spike 实证）；保留 request(MAX) + fail-loud 上界为审计 B 案 |
| 裁定⑤(a) 水位门（HighWatermarkSemaphore）作缺省 | fail-loud 已满足网关语义（显式可观测、无静默丢弃）；水位门引入隐藏排队段，积压可见性反而下降——保留为 fail-loud 不满足语义时的备选 |
| 裁定⑥引入独立单线程执行器串行化信号 | 每订阅一线程成本 × 流并发数；drain 守卫串行化以零新增线程取得同一信号序保证 |
| 裁定⑦(b) 显式放开 HALF_OPEN 恢复期并发 | 多请求并发击穿恢复期候选，探活记账被噪声主导，熔断恢复判据失真（原语单探针契约在消费面对齐而非绕开） |
| 裁定⑦(b) 规则模型 schema 扩 allowCall 字段 | healthView 为代码构造 Map，`rule.xdef` 输入声明只及顶层——schema 扩展无承载对象，纯代码追加即达契约 |
| C3-007 在拦截器侧重试回调注入时一律 rethrow（审计建议一） | 缓冲形态下 NON_TRANSIENT 降级能力整体丢失（降级变断流报错），行为回退；降级终态入口保留降级语义且闭合脏输出 |

### 3.9 Baseline 偏差与勘误登记（亲读 @ `8ef804c58f`，执行前如再漂移以 live 为准）

| # | 项 | Baseline 载值 | live 亲读值 | 处置 |
|---|---|---|---|---|
| 1 | 成员 10「recordSuccess 记账反转」后果 | plan 成员行与红⑥保留该腿 | C3-round2 复核已驳回（`proceedOnStreamComplete` 唯一调用点 `StreamingProcessor` :188 在源完成 lambda 内，降级路径不可达；:186-192 亲读证实） | 修复面收窄为降级显式化（零泄漏 + 降级终态）；recordSuccess 守卫不实施；C10-006 断言无记账反转腿（§3.7(a)/§六） |
| 2 | MA-05 interceptor 终态 relay 行号 | plan/审计载 :319-351 | `completeRelay` 实际位于 interceptor :380-387（adapter 侧 :336-343）；whenComplete 回调体 live :284-321（审计载 :284-310 尾部漂移 +11） | 映射表按 live 值登记；审计值为落笔时快照，执行前复核 |
| 3 | `ThresholdBreaker.allowCall` 行号 | 审计值 :165-219 | live :166-218（compute 内三态 switch，零改动面） | 零改动面维持，无实质漂移 |
| 4 | C7-005 `CandidateHealth` isCircuitOpen | :49-51 | :49-51 ✓；`CandidateHealthProvider.healthOf` :32-39（breaker.getState :36）✓ | 零漂移 |
| 5 | 2.3 侧越窗 add 行号 | 勘误 2 已载 :264-266（add :265） | :264-266 ✓ | 零漂移 |
| 6 | `FailoverTestSupport` 恒 request(MAX) | :399-401 | `CollectingSubscriber.onSubscribe` request(Long.MAX_VALUE) 位于 :400（:396-401 区间）✓ | 零漂移 |

## 五、与已有设计的关系

- **plan 2505（`sse-backpressure-and-body-phase-lifecycle.md`）——同域零回退边界**：2505 已修 4.3 传输层（`waitForDemand` 有界等待、body 阶段生命周期、daemon 承载）与 3.1 聚合层；本批为同一 BufferedStreamingPublisher/FailoverStreamFlow 域的缓冲编排层修复，零越界回改 4.3；2505 §五明载 `StreamingProcessor`（:144/:162 request(1) 逐元素拉取派）与 `BufferedStreamingPublisher`（:378 自述）为其消费形态——裁定⑤(b) 门控改造不改变该消费契约的「逐元素拉取」形态，仅在其前插 demand 询问；组合行为核验结论见 §3.5(c)。
- **plan 2508（`hotpath-sso-refresh-async-and-gateway-retry-admission.md`）——准入闸正交**：`UpstreamAdmission` 许可闸管在途请求数（per-URL），本批管单流缓冲/授信；两者共享「上游快于下游」失效模型但无共享代码位；`TestGatewayUpstreamAdmission`/`InvokeProcessorRetryTest` 全绿为本批回归门禁。
- **plan 2506 骨架**：本文档结构与「成员→裁定映射 + 红绿形态表」测试纪律沿用 `plugin-lifecycle-and-config-source-hardening.md` 先例。
- **R4-32（C7-002 墙钟）/ R4-17（G-D 面）/ R2-16（C5-001+分类器）三向零承接**：本批触碰窗口时间判定零语义改动（仍墙钟，勘误钉防顺手修）；G-D 相邻成员以本批组件化后形态为基线；分类器本体零改动，取消短路在本批编排层先行消除记账污染 live 面。

## 六、成员 → 裁定映射

| # | 成员（判级） | 面 | 裁定 | 修复位（live : 行） | 回归测试（预定名） |
|---|---|---|---|---|---|
| 1 | 2.3-C3-003（P1） | 自驱动 request(1) 越窗无界缓冲 + onError 不 cancel 上游 | ①⑤ | BufferedStreamingPublisher（组件宿主改造）；StreamingProcessor :144/:162/:164-166 | TestStreamingWindowBufferGate_C3_003 |
| 2 | 3.3-C2-001（P1，MA-07 主；C1-008 吸收） | 取消×装配窗口 acquire 泄漏 | ② | FailoverStreamFlow :508-514（漏斗行）；:227/:273/:443/:462 进入点矩阵 | TestFailoverAttemptReleaseOnCancel_C2_001 |
| 3 | 3.3-C1-001（P1） | clear 锁外 × request 冲刷交错 | ③ | FailoverStreamFlow :438 入锁 + :384/:399 入口守卫 | TestFailoverAttemptIdentityGuard_C1_001 |
| 4 | 3.3-C3-001（P1） | 终态冲刷受 demand 约束截断 | ④ | FailoverStreamFlow :465-483/:184-202（pendingComplete） | TestFailoverPendingComplete_C3_001 |
| 5 | 3.3-C8-001（P1；CL-001/003/005/012/014 吸收） | 越窗无界 + request(MAX) | ⑤ | FailoverStreamFlow :371-382（上界）/:516（策略成文保留） | TestFailoverOverflowBound_C8_001 |
| 6 | 3.3-MA-05（P2；C3-004 主 + C7-001 吸收） | 三回调体无护栏 | ⑦(c) | ChatServiceFailoverAdapter :246-280/:336；AiGatewayFailoverInterceptor :284-321/:380 | TestFailoverCallbackGuard_MA_05 |
| 7 | 3.3-C2-002（P2，R03） | 监视器内下游投递 | ⑥ | FailoverStreamFlow :353-405/:465-496 出锁投递 | TestFailoverDeliveryOutsideLock_C2_002 |
| 8 | 3.3-C2-009（P2，R03） | 监视器内重试全链 | ⑥ | BufferedStreamingPublisher :298-326 外移 | TestBufferedRetryOutsideLock_C2_009 |
| 9 | 3.3-C3-003（P2） | 取消误分类记账污染 | ⑦(d) | ChatServiceFailoverAdapter :289-293 + interceptor :323-330 短路 | TestFailoverCancelShortCircuit_C3_003 |
| 10 | 3.3-C3-007（P2） | 降级绕过窗口丢弃语义 | ⑦(a) | StreamingProcessor :170-183 + 缓冲层降级终态入口 | TestDegradedTerminalSignal_C3_007 |
| 11 | 3.3-C7-005（P2；**C3-005 半边吸收**，B1） | 选择面绕过 allowCall + attempted 回收语义 | ⑦(b) | CandidateHealthProvider/CandidateHealth/DefaultSelectionStrategy/RuleBasedSelectionStrategy :201-211 | TestSelectionAllowCallRecheck_C7_005 |

C10 七条验收归属：C10-001→成员 5；C10-002→成员 3+4；C10-003→成员 2；C10-004→成员 7+8；C10-005→成员 6；C10-006→成员 10（断言面按 §3.7(a) 勘误收窄：零泄漏 + 降级显式终态，无记账反转腿）；C10-014→成员 11（C7-005 全 face + C3-005 半边同 diff）。

## 七、测试纪律契约

夹具纪律沿用 2506 先例 §七：daemon 夹具线程 + latch 对齐 + 有界观察窗 + @Timeout；禁仅类型/修饰符存在断言；每件必含修复后可观测行为断言；先红后绿或变异/回退论证留痕（快照落 `_tmp/`）。

**测试缝**：`FailoverTestSupport.CollectingSubscriber`（:396-401 恒 request(MAX)，C10-001 缺口本体）**保留不动**（既有用例零破坏），旁新增 `ManualDemandSubscriber`（可控授信：request(n) 手动驱动、停滞可控）+ `ScriptedPublisher`（发射计数/可控 future/可控失败注入）+ 重试决策阻塞桩（C2-009 锁外移计时验证载体）；`GatewayFixTest` 的 N1 虚假覆盖用例 `streamingProcessor_requestsOneByOne` 重写为真实实例化钉。

**红绿形态表**（11 行）：

| # | 修复前红形态（可观测） | 修复后绿形态（可观测） |
|---|---|---|
| 1（C3-003） | demand 受限订阅者 + 长流上游：缓冲深度随元素数线性增长；onNext 错误分支后上游 SSE 连接滞留至 EOF | 深度 ≤ bufferSize；映射链授信记账断言（demand==0 不 request(1)）；错误终态后上游 cancel 被调用 |
| 2（C2-001） | 取消落在 :235 后交错（latch 对齐）：registry 计数永久 +1，饱和误判红 | 4 进入点全矩阵交错下计数终值归零；releaseAttempt exactly-once（并发释放计数 == 1） |
| 3（C1-001） | classifyStreamError 阻塞桩拉宽窗口 × 下游 request(5) 交错：attempt-1 前缀泄漏进输出或 onNext(null) | 零泄漏零 null；放弃 attempt 的缓冲永不交付（identity 守卫断言） |
| 4（C3-001） | demand 耗尽 + 上游 onComplete：尾部 chunk（finish_reason/usage）丢失仍收 onComplete | request 冲空缓冲后 onComplete 才到达，元素 exactly-once 全量；窗口内完成次级面同钉 |
| 5（C8-001） | 越窗后 demand==0：buffer 深度无界增长红 | 深度 ≤ bufferSize；溢出类型化错误 + 积压指标；request 策略按侧断言（2.3 授信记账 / 3.3 恒 MAX + 上界兜底）；溢出零重订阅零 recordFailure（draft review R2 补腿） |
| 6（MA-05） | 回调体注入抛非 NopException：future 永不完成红 | completeExceptionally 必达终态；NopException 语义传播保持 |
| 7（C2-002） | 慢下游 onNext 阻塞桩：request() 被投递锁卡位（计时断言红） | request 不被下游 onNext 阻塞；投递在锁外（monitor 持有线程断言）；信号序不变式断言（不乱序/终态最后） |
| 8（C2-009） | 重试决策阻塞桩：monitor 持有跨决策时长（持有断言红） | 重试决策与 startAttempt 在锁外（线程/时长断言）；attempt 放弃判定仍原子 |
| 9（C3-003） | 取消后 stage 异常完成：recordFailure + 预算误耗红 | 零记账零耗预算 + future 异常完成（取消短路真实在 whenComplete 链上生效——接线断言） |
| 10（C3-007） | NON_TRANSIENT 窗口内失败：attempt-1 前缀 + 降级 wrapper 一并交付红 | 零前缀泄漏；降级 wrapper 经降级终态唯一交付；直通形态行为零变化（对照腿） |
| 11（C7-005+C3-005） | OPEN 冷却期满候选被永久跳过（其余候选健康时）；规则脚本无 allowCall 输入可用 | allowCall 复查恢复可达；HALF_OPEN 探针在飞时门控拒绝（单探针）；规则脚本 `health[i].allowCall==false` 分支被门控；attempted 候选下一请求经冷却复查可达（C3-005 半边腿） |

**端到端两链路**（rule 22）：①3.3 流式 failover 全链——`callStream`/拦截器入口 → attempt 失败 → 重订阅 → 越窗 → ManualDemand 受限消费 → 终态交付 → 出口点元素序列 exactly-once 零泄漏，含一次取消路径 teardown 配对；②2.3 缓冲链路——`StreamingProcessor` 映射链（demand 门控形态）→ 组件 → ManualDemandSubscriber 出口点 = 尾部元素交付 + onComplete 时序（`bufferedTailElementsDeliveredBeforeComplete` :771-797 形态复用/重写）。**接线验证**（rule 23）：两实现真实调用共享组件断言（计数器/实例断言，非 import 自证）；allowCall 代理真实被选择路径调用；取消短路真实在 whenComplete 链生效。**无静默跳过**（rule 24）：溢出 fail-loud 非静默丢弃/排队；MA-05 护栏显式 completeExceptionally 非空 catch；降级显式终态非伪装 onComplete；新增公共分支无空方法体/TODO。

**零回退钉全列**：`StreamingProcessorBufferingTest` 12 用例（重点 :771-797/:397-422/:624-671）、`TestFailoverStreamCancelPin_C3_001`、`TestStreamFullChainEndToEnd_C3_001`（nop-ai-core，2505 产物）、`InvokeProcessorRetryTest` + `TestGatewayUpstreamAdmission`（2508 产物）、`TestChatServiceFailoverAdapterStreaming`/`TestAiGatewayFailoverInterceptorStreaming`/`TestChatServiceFailoverAdapterNonStreaming`、C7-002 墙钟语义勘误钉（本批零改动声明）。三向零承接核对（R4-32/R4-17/R2-16）随 Phase 3 `git diff` 登记。
