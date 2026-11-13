# NodeTscBridge 代际栅栏、lint 全局槽卸载契约与 nop-tools 写面/注册表并发契约（CRA 8.38 R3-08 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；设计评审 R1 已完成[1 Blocker + 4 Major + 6 Minor，全部修订随轮落盘——见文末「评审记录」]，Blocker+Major 修订面待独立复审后 plan 2531 Phase 2 方可启动）
> Last Reviewed: 2026-11-03
> Source: plan 2531（`ai-dev/plans/2531-cra-phase8.38-r3-08-tools-parse.md`，CRA Phase 8.38 R3-08 6.2 tools-parse 批）；审计目录 `ai-dev/audits/2026-10/2026-10-22-1500-deep-audit-nop-tools-parse-concurrency/`；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.3 R3-08；既有裁定 `ai-dev/design/nop-lint/07-xscript-engine.md` §4（Route A 全局换装）；软对齐先例 [leiden-timeout-envelope-and-code-graph-self-heal-singleflight-guard-contract.md](leiden-timeout-envelope-and-code-graph-self-heal-singleflight-guard-contract.md)（plan 2530 结构模板）
> Owners: 裁定①（nop-lint-js NodeTscBridge 代际栅栏/统一失败出口域，W-01+W-11+W-12）、裁定②（nop-lint-core lint 全局槽卸载域，W-02）、裁定③（nop-lint-core 缓存写面域，W-03+W-10）、裁定④（nop-lint-core 守卫前置域，W-07）、裁定⑤（nop-refactor-core 落盘契约域，W-08）、裁定⑥（注册表与语言缓存域，W-04+W-05）、裁定⑦（协议守卫域，W-09）、裁定⑧（测试缝总表）

本文是 plan 2531 全部成员修复的唯一设计裁决权威。每条裁定给出：决策、理由、被否决的备选、零触碰边界。行号以 live 基线 `42374373f4`（分支 `concurrency-audit-remediation`）亲读值为锚（与 plan 2531 基线 `8b41f8ff18` 站点零源码漂移——两基线在全部站点路径 git diff 零 diff 且本批逐行亲读核对），后续漂移以 live 为准。文件命名沿 plan 2531 Goals 拟名零调整。

## 裁定① W-01+W-11+W-12：NodeTscBridge 代际栅栏与统一失败出口（P1）

**决策**：

1. **栅栏机制 = 局部引用捕获族的「按代队列分离」形态（预设；捕获点 = spawn 监视器帧内——设计评审 R1 Major-1 再勘）**：`spawn()`（:195-229）在监视器帧内创建本代队列局部变量，并以 spawn 局部变量（当前 `Process` 与本代队列）经 lambda 捕获传入 `Thread` 构造（:208）——**捕获发生在 spawn 监视器帧内而非 reader 线程入口**：reader 入口捕获存在「启动延迟窗」残窗（reader 被 CPU 调度延迟期间主线程可先行完成 destroy+respawn，迟到的入口捕获会误捕新代引用——R1 Major-1 判定的非结构化点）；spawn 帧内捕获使捕获值与代际静态绑定（Thread.start 先行发生于 lambda 体对捕获局部变量的全部读取），`drainPeerOutput`（:231-243）的 reader 侧逻辑改为消费捕获局部变量：该 reader 的全部 `put`（:236 对应位）与 finally 的 EOF `offer`（:241 对应位）物理靶向其捕获的本代队列。`spawn()` 为每一代新建队列并替换共享引用（:59）——旧代 reader 的写目标是被遗弃的旧代队列，新代握手/查询只消费新代队列。「旧代帧入新代队列」结构性不可能（旧 reader 物理持旧队列引用，写入点不经任何共享可变状态），无需二次比对；队列引用即代际令牌（引用分离是「捕获比对」形态的最强化：比对退化为引用靶向本身）。request/pollWithDeadline（:157/:250）经共享引用读取的始终是当前代队列，消费路径零形态变化。
2. **reader NPE 变体同栅栏封死**：reader 不再解引用共享字段 `process`——流读取与 put/offer 全部经 spawn 帧捕获的局部 Process/队列引用（:232-233 读共享字段面消除）；`destroyProcess` 置 null（:280-281）与任何后续 spawn 对 reader 侧不可见亦无影响，finally 的 EOF offer 靶向本代队列不毒化新代。
3. **分支 A/B 双封死论证**：分支 A（stale 数据帧过 validateReady 杀健康新进程 ：219→:224）与分支 B（stale EOF 过握手误判 EXHAUSTED ：213-217）的共同前提是旧代帧/EOF 经共享队列跨代可达；按代队列分离使该前提对两类帧（数据帧与 EOF 哨兵）同时不成立——单一形态封死全部变体（C6 复核「一行级栅栏同时封死全部变体」裁定的兑现形态）。
4. **W-11 握手 EOF 分支补 destroy**：:213-217 置 EXHAUSTED 前补 `destroyProcess`——EOF 语义上「此进程不可用」与 ：224 对照组对齐；对已死进程 `destroyForcibly` 为安全幂等操作（回收句柄），`destroyProcess` 自带 null 检查（:278）实证；**EXHAUSTED 终态语义零变更**（补资源回收，不改「预算外终态」语义——修「误入」不修「终态」）。
5. **W-12 decode BAD_FRAME 映射统一失败出口**：:164 decode 抛错即流完整性已不可信（帧边界失配，后续帧全部不可对齐）→ 回收映射补齐：decode 失败经 handleProcessFailure 回收后原样重抛——与 ：169 correlation 失配对照组对齐（结构性失配立即回收、解析失配同等对待），恢复晚一轮多烧一预算槽的缺口闭合。**BAD_FRAME taxonomy 注记（R1 Minor-②）**：回收映射仅覆盖流完整性面（decode 解析失败/correlation 失配/EOF——帧边界已不可信）；内容型 BAD_FRAME（:94 initProject 无 cacheKey / :108 getTypeAtLocation 无 type / TscProtocol intValue 非数值 ：96-102 族）为健康流上的内容契约违例，对齐既有 error 帧语义（:175-176 注释——健康对端的结构化回答）不回收。
6. **统一失败出口回收映射完备性表（C6-101 失效面清单回收映射声明）**：

| # | 失败路径（live 锚） | 回收出口 | 状态 |
|---|---|---|---|
| 1 | 写失败（:154） | handleProcessFailure（:267-275） | 既有 |
| 2 | 查询中 EOF（:160） | handleProcessFailure | 既有 |
| 3 | 查询超时（:254） | handleProcessFailure | 既有 |
| 4 | ok 帧 correlation 失配（:169） | handleProcessFailure（对照组） | 既有 |
| 5 | error 帧 correlation 失配（:177，裁定⑦） | handleProcessFailure | 新增（消费侧） |
| 6 | decode BAD_FRAME（:164，W-12） | handleProcessFailure | 新增（生产侧） |
| 7 | 握手 EOF（:213-217，W-11） | destroyProcess 补齐（终态语义零变更） | 新增 |
| 8 | validateReady 失败（:224） | destroyProcess + EXHAUSTED | 既有 |
| 9 | spawn IOException（:203） | 直接 EXHAUSTED（无进程可回收） | 既有 |

7. **诊断不误导与可观测（C1-102 侧写兑现）**：每条回收出口的异常消息携带真实死因（写失败/超时/EOF/BAD_FRAME/id 失配各自成文）——恢复链全程不得对健康重生进程出现 "peer exited"/"not a ready frame" 类死因误报（红① 断言⑤承载）；reader 线程命名随代际可辨（`nop-lint-tsc-bridge-reader-<代序号>`——同名线程排查面本批采定，随栅栏同 diff 最小增量；plan Non-Blocking Follow-up 项以此裁定为终点，不再留优化候选）。
8. **close() 路径零回退论证（:140-141）**：close 于监视器内置 closed → destroyProcess 回收当前代进程 → EXHAUSTED；旧代 reader 残余写目标为被遗弃队列（无人消费、随引用消亡）；closed 标志确保 closed 桥永不静默 respawn（ensureReady :181-183 既有）——栅栏形态对 close 语义零影响。
9. **被否决的备选**：共享字段比对-后-put（gate 检查与 put 非原子——旧 reader 检查通过后被调度延迟、put 落在 spawn clear（:197）之后即毒化；monitor 内 check+put 又与 synchronized request 的全程持锁相斥——在途查询期间帧投递被阻塞数秒量级，不可采）；**reader 线程入口捕获（R1 Major-1 否决）**——捕获时刻在 reader 被 CPU 调度延迟之后，主线程可先行完成 destroy+respawn，迟到捕获误捕新代引用，非结构化封死（捕获点须前移 spawn 监视器帧内）；generation 计数入队 + 消费端过滤（队列元素包装侵入 request/pollWithDeadline 路径、EOF 哨兵需带代标记、消费端过滤循环改变 deadline 余额语义、陈旧项滞留共享队列至下次消费）；spawn clear 前对旧 reader join(短超时)（destroyForcibly 异步生效使 join 超时常态化——每次 spawn 引入延迟且超时后残窗依旧；NPE 变体仍未封死）；握手前二次校验类缓解（治标——stale 帧仍在队，下一查询复现）。

**零触碰边界**：TscBridgeConfig 与 isAvailable/initProject/getTypeAtLocation/isTypeAssignableTo 查询语义零变更；EXHAUSTED 终态语义（javadoc "once the budget is exhausted ... never retries"）与 restart 预算语义零变更；nextId 单调不重置（:65/:148）零变更（错答不被接受的既有排除维持）；FakeTscPeer 既有模式零改动（扩展模式归红① 测试面）。

## 裁定② W-02：lint 全局槽 install 所有权还原 + wrapper 判定链扫描化（P1，路径 X 预设）

**决策**：

1. **路径 = 路径 X 三件套（预设——零 nop-core 触碰，「替代路径=工单行显式登记」以本裁定兑现）**：
   - **链扫描幂等（声明收窄——R1 Major-2(a)）**：install 的已安装判定从单层 instanceof（:72-74）改为从槽顶沿 **仅经 `LintDeadlineExecutor.delegate()`（:60-62）可穿越的透明层** 链扫描——可见链段上任一层为 LintDeadlineExecutor 即已安装。**穿越性边界**：delegate() 是 LDE 自有访问器——真实 attach 形态为槽整体替换（XLangDebuggerInitializer :77 `new DebugExpressionExecutor(debugger)`，无 delegate 访问器），不透明外来 wrapper 之下链不可穿越；故链扫描判定力 = 「可见链段含 LDE → 已安装」，槽顶为不透明外来层时判定为「不可确认」，交由所有权记账与 uninstall 分支处置（下条）。理由：attach 包裹组合形态（外来透明层叠于 LDE 之上）下单层判定误判未安装 → 再包一层 → 叠层且卸载语义破坏；同时维持「不硬连 DefaultExpressionExecutor、与 debugger wrapper 共存组合」的既有 javadoc 契约（:36-39）。
   - **所有权记账与快照还原（uninstall 三分支——R1 Major-2(b)）**：install 计数 +1（首次 0→1 时捕获安装前槽值快照；嵌套调用经计数保持幂等不叠层）；新增对称 uninstall——LintEngine 两调用点（:269/:311）以 finally 与 install 配对（run 终态唯一出口）；最后一次 release（1→0）在 LintDeadlineExecutor 类监视器内按三分支处置：**分支一** 槽顶可确认属本代 wrapper（槽顶 == 本代实例，或经可穿越透明链可见顶为本代）→ 按快照原子交换还原；**分支二** 可见链段含本代 wrapper 但槽顶为外来（不透明）层（attach 包裹交错）→ 不动槽 + 结构化 WARN 显式登记残留（rule 24——非静默）+ 所有权释放；**分支三** 可见链段确认无本代 wrapper（attach 整体替换形态）→ 不动槽 + WARN + 释放所有权（快照按声明丢弃——槽恢复权威回归外来写者自身生命周期，本代还原义务仅覆盖本代安装仍占顶的形态）。
   - **卸载契约**：本代安装占顶形态下 run 终态后 `getGlobalExecutor()` 恢复安装前快照且全链无 LDE 残留（红②② 验收）——CL-401 三后果（全局改道/顺序竞态/强引用永久驻留）的生命周期面确定性闭合；还原操作的原子性由类监视器承载（lint-vs-lint 的 install/uninstall 全部经同一监视器——丢更新面闭合）。
2. **与 design 07 §4 Route A 换装裁定的关系 = 维持只修缺陷**：全局换装方式（经 `registerGlobalExecutor` 公共扩展点安装每节点拦截 wrapper）零变更——路径 X 只修并发（链扫描防叠层）与生命周期（所有权还原防永久驻留）缺陷；换装决策（含 CL-504 passthrough 热路径税 watch 归 C9 不翻案）维持。
3. **Route-B 否决（条件申报①成文）**：`EvalExprProvider` 槽 volatile 化属 nop-core 平台设施变更（Protected Area 框架核心行 plan-first——四件证据链追加申报），且 volatile 只闭合可见性残差、不闭合 install-vs-attach 交错窗（attach 裸写槽不经任何 monitor），对 P1 主害（永久驻留 + 护栏静默失效）零增量——预设不采。
4. **被否决的备选**：作用域传播（wrapper 拦截点从全局槽移至作用域链——构成对 design 07 §4 既有裁定的变更，且须触 EvalRuntime/IEvalScope 平台接口面（nop-core），双重越界：设计变更申报 + Protected Area；如未来深化属「模块级护栏设施化评估」follow-up 候选）；单层 instanceof 维持仅加 uninstall（attach 组合下叠层回归——判定缺陷本体未修）；跨模块监视器统一全局槽写者（XLangDebuggerInitializer 在 nop-dev-tools——依赖方向不可达，且属旁证零 diff 约束面）。
5. **路径 X 残差显式登记（R1 Major-3 强制输出——watch-only）**：install-vs-attach 交错窗**收窄非归零**——attach 裸写槽（XLangDebuggerInitializer :77）不参与 LintDeadlineExecutor 类监视器、槽非 volatile（EvalExprProvider :19/:35-37/:47-48）；交错窗内的丢更新从「永久驻留 + 护栏静默失效」收窄为「attach 后 uninstall 观测外来链顶不动槽」的 WARN 残留小窗；槽可见性残差留 nop-core。确定性归零须 Route-B（条件申报①）或触碰 XLangDebuggerInitializer（违反旁证零 diff 约束）二者之一，均预设不采；**残差登记 watch-only，由红②②（卸载契约，确定性红）承载 W-02 P1 后果面验收**。
6. **反向保护 #2 同 diff 重写方案（条款一，逐用例迁移表——既有覆盖不弱化义务）**：

| 既有用例（live 锚） | 迁移形态 |
|---|---|
| installIsIdempotentAndDelegatesToTheInstallTimeExecutor :73-96（含 ：84-85 "install must own the global executor slot" 反钉） | 幂等断言迁移为链扫描形态（第二次 install 不叠层——:90-92 断言语义维持）；:84-85 槽所有权断言迁移为「run 内 install 持有（链扫描含 LDE）+ uninstall 后槽等于安装前快照且全链无 LDE 残留」（红②② 同体） |
| noDeadlinePassesThroughWithUnchangedSemantics :100-120（透传） | 语义等价迁移——恢复机制从手工 `registerGlobalExecutor(prior)`（:117-119 finally）换为 uninstall 配对；结果值/invocations/无 deadline 断言零变化 |
| expiredDeadlineAbortsExplicitlyAtTheWrapper :125-143 / expiredDeadlineAbortsThroughTheEngineInjectionPath :146-155（超时中止） | 语义等价迁移（同上恢复机制替换）；超时消息/ruleId/budget 断言零变化 |
| evaluationDepthBeyondTheLimitAbortsExplicitly :226-240 / shallowNestingRunsWithinTheDepthLimit :243-252 / depthAccountingIsBalancedAfterAnAbort :255-270（深度护栏） | 语义等价迁移（同上）；MAX_EVAL_DEPTH :47 与 EVAL_DEPTH 平衡面（:49/:92-107）零变更锚维持 |
| injectedDeadlineValuesAreVisibleOnTheExecutionPath :160-180（R1 Minor-⑤ 补全） | 语义等价迁移——现状裸 install 无配对，重写补 uninstall 配对；注入可见性断言（firstDeadline/firstRuleId/firstBudget）零变化 |
| expiryMidLoopProvesPerBackEdgeInterception :204-221（R1 Minor-⑤ 补全） | 语义等价迁移 + uninstall 配对——**rule 23 接线证明（per-back-edge interception，Minimum Rules #23 唯一载体）不得丢失** |
| deadlineKeysAreNotInTheCompileWhitelist :185-190（R1 Minor-⑤ 注记） | 不触槽——原地保留（零迁移） |

7. **红②③ 组合驱动器载体（零 mockito——R1 Major-2(c) 增替换型变体）**：第二写者以测试树内手工外来 `IExpressionExecutor` 载体模拟 attach 语义，两变体——**包裹型**（外来 wrapper 包裹当前链顶后写槽，对齐 uninstall 分支二）与**替换型**（外来执行器整体替换槽、无 delegate 访问器——对齐真实 attach 形态与 uninstall 分支三）；`DebugExpressionExecutor` 位于 nop-xlang-debugger 模块（亲读 pom 核实非 nop-lint-core 依赖），不可用作测试载体。

**零触碰边界**：`EvalExprProvider`（:19/:35-37/:47-48）、`XLangDebuggerInitializer`（:77/:156）、`BetweenOpExecutable`（:76）、`XLang`（:53/:56-57）、`EvalBackendRouter`（:101/:117/:223）旁证零 diff；**`LintEngine` 为本裁定触碰面而非旁证（plan 勘误 7——R1 Blocker 修订）**：仅 :269/:311 两调用点 install/uninstall finally 配对增量，其余行零 diff；透传/超时/深度三语义与 XScriptDeadline 注入通道（VAR_DEADLINE_NANOS/VAR_RULE_ID/VAR_BUDGET_MS）零变更。

## 裁定③ W-03+W-10：RuleResultCache 跨进程互斥与 BaselineFile 原子化（P2 + P3 激活同点）

**决策**：

1. **temp 唯一名（W-03 主责）**：save 的临时文件改 `Files.createTempFile`（目标目录 + cache 文件名前缀 + `.tmp` 后缀）——批次行「atomicWriteText 模式」= EditPlanApplier.atomicWrite :163-164 既有形态的复用（C1-105「本处是同族漏改」裁定兑现——非新发明写原语）；固定名 `.tmp`（:199）互踩面（A move 搬空 B 的 tmp → NoSuchFileException 虚假失败）结构性排除。
2. **moved + finally 清理（W-10 同点）**：照抄 :169-194 范式（moved 标记 + finally deleteIfExists；清理失败 WARN 不掩主失败）——:204-207 catch 无清理的垃圾面闭合；W-03 主责 + W-10 清理 + BaselineFile 原子化同一变更集交付、台账分域注记（批次行「W-03 与 W-10 同点落地」兑现）。
3. **跨进程互斥 = LocalFileLock 圈 load→save 全程 RMW 临界区（预设——C1-105 建议前者）**：锁文件为 cache 文件同目录 sibling；每 cache 文件一把、单锁无嵌套（无死锁环）；获取有界等待（poll interval + timeout），超时/获取失败 fail-closed（设施实际抛 `NopException`[ERR_FILE_ACQUIRE_LOCK_FAIL]——与 cache 面 NopLintException 为 fail-closed 同语义非同异常类型[R1 Minor-⑥ 措勘]，禁静默降级跳过保存，rule 24）；进程死亡由 OS 释放文件锁（无 stale 锁面）。**锁生命周期专条（R1 Major-3）**：锁生命周期全在 RuleResultCache 类内——acquire-at-load（load 入口获锁）；load 自身失败路径 try/catch 释放（获锁后的 load 面异常即释放——消除「load 失败锁滞留 → 同 JVM 后续获锁 OverlappingFileLockException 永久 fail-closed」毒化面）；save 以 finally 释放；load→save 之间的中途异常放弃语义显式声明为 **CLI 进程域**（CLI 每 run 一进程，进程退出 OS 释放文件锁——无跨 run 毒化）；不触 CheckRunner 等 CLI 面（本裁定站点集零新增——勘误 7 增计的 LintEngine 属 W-02 面）。理由：RMW 全件（load :73-111 → put :175-191 → save :197-208）只有全程串行化才保住「A run 的结果不被 B run 的陈旧快照整件覆盖」；temp 唯一名只闭合写冲突、不闭合 lost-update。**同 JVM 语义显式登记**：LocalFileLock 底座为 JVM 级文件锁（tryLock :44-76 亲读——同 JVM 重入获锁走异常路径 → ERR_FILE_ACQUIRE_LOCK_FAIL 快速失败）——同 JVM 并发同 cache 显式 fail-closed 而非静默交叉写；CI 容器/网络盘文件锁可用性 watch 维持（fail-closed 形态下不可用即显式失败，不产生错误结果——plan Risks 登记的裁定③ 论证面闭合）。
4. **BaselineFile 原子化（W-10 失效面，预设）**：writeTo（:187-194，writeString :189 直接落盘）改 temp + ATOMIC_MOVE（对齐同族 atomicWrite 范式）；load fail-closed 语义零变更。撕裂写声明边界备选否决——与 RuleResultCache/EditPlanApplier 同族写面质量标准看齐（F8 清理不对称消除），声明边界会把「同模块三写面两种质量标准」固化为契约。
5. **被否决的备选**：single-writer 契约文档 + save 冲突降级跳过保存（C1-105 备选二——互斥责任转嫁给文档，降级静默面与 rule 24 冲突，lost-update 未闭合）；仅 temp 唯一名不加锁（写冲突闭合但 RMW lost-update 开放）；锁粒度下沉到 save 单方法（load 与 save 之间的内存 artifact 交错面不闭合）；BaselineFile 撕裂写声明边界（第 4 条）。
6. **失败注入测试形态**：红③（裁定⑧）——只读目录注入归因 + 预置 X.tmp 并发 save 踩踏钉定 + move 失败无残留断言。CL-507 陈旧路径驻留 watch 维持（磁盘面治理独立候选，不入本批）。

**零触碰边界**：LocalFileLock 设施本体零 diff（条件申报④预设不触发）；load/put 校验与 fail-closed 面（:74-111/:126-157）、FORMAT_VERSION/runFingerprint 契约零变更；EditPlanApplier.atomicWrite 零触碰（仅范式复用）；BaselineFile 读路径（toYaml/load）零行为变更。

## 裁定④ W-07：EditPlanApplier 守卫前置（P2）

**决策**：

1. **守卫前移形态**：apply 写序重排为 merge → splice（内存，:87）→ 内存守卫（countErrorNodes ×2，:92-94 的两次解析全部前移至任何写盘之前）→ 守卫通过才 atomicWrite(next)（:89）→ **删除 ：96 回滚写**（守卫拒绝路径零落盘，回滚写不再存在）。守卫输入 source/next 均在内存——前置零成本（plan 核实在案）。
2. **FixApplier multipass 语义等价论证**：rolledBack=true 的 EditPlanResult 返回形态不变（finalSource=input、appliedEdits=0、skipped 结构不变——:100-101）——multipass 循环的 `applied -=` 记账零影响；FixApplier :129 multipass 窗口面零触碰（C6 复核第四节裁定兑现）。
3. **生产行为变更声明（owner doc 落点登记）**：守卫拒绝路径的中间窗口破损内容不再落盘——并发读取方不再可见破损内容、kill -9 不再滞留被拒内容（:71-72 restore 承诺由「拒绝后回滚还原」升级为「拒绝时零落盘」，javadoc 同步更新）；owner doc 同步义务 = `nop-refactor.md` Runbook 指针节对 EditPlanApplier javadoc 的指向同步（Phase 3 E5 落账）。
4. **dryRun=true 零变更论证**：dry-run 从不写盘（:88-90 条件维持）——守卫前置只改非 dry-run 路径的写序；dryRunRollbackAlsoReportsTheFlagWithoutWrites 既有用例零回退。
5. **被否决的备选**：守卫后置保留 + 回滚写加固（中间窗口破损内容在盘的本体缺陷未闭合——kill -9 滞留面依旧）；「先写 temp → 守卫 → move」变体（守卫输入全在内存，先落 temp 无信息增益、多一次无谓 IO——守卫前置是唯一零窗零增量形态）。
6. **红⑥ 失败注入形态 + watch 登记**：见裁定⑧（只读目录注入——修复前红修复后绿）；kill -9 中间窗口随守卫前置消除（拒绝路径不再有盘面中间态），与 C6-006 durable 面（atomicWrite 无 fsync）的 watch 同批注记维持 follow-up。

**零触碰边界**：Fixer.merge 单点权威零触碰；spliceAll/stale-range fail-closed 语义（:112-127）零变更；RefactorOperationRunner :61-62 调用点零产品 diff；atomicWrite 本体（:159-195）零触碰（仅调用序重排）。

## 裁定⑤ W-08：(b) fail-loud 声明边界 + 异常通道结构化 landed 载荷（P2，反向保护 #8 相容）

**决策**：采 **(b)（预设裁定）**——

1. **(b) 三件**：run javadoc :45「lands atomically」修正为 fail-loud 逐文件落地语义；类注释 ：30「already-landed files in the error (never a faked success)」维持为落盘语义单一事实源；崩溃恢复语义显式声明「undo via VCS」（无 journal/marker——进程崩溃后文件集混合状态以 VCS 为恢复权威，声明入 javadoc 与 owner doc）。禁两处矛盾并存（:45 与 ：30/:63-71 收敛为单一事实源）。
2. **载荷载体裁定（对 plan 行文「RefactorResult 载荷」的载体修正——live 核证）**：部分落地数据只存在于失败通道（:63-71/:103-110 中途失败即抛 NopRefactorException——RefactorResult 是成功终态载荷，结构上不可能承载「失败时的已落地清单」）；机器可读 landed 载荷的载体 = **NopRefactorException 上的结构化字段（landed 文件列表 + notAttempted 计数）**，异常消息文本（:65-69）维持人类可读形态；RefactorResult 既有载荷（applied/edits/diff/verification 四桶）零字段变更。owner doc 同步义务（R1 Major-1 强制输入）：`nop-refactor.md` :21「RefactorResult（applied=true 原子落盘）」与 ：23「跨文件原子落盘」随 (b) 改写为 fail-loud/部分落地声明（Phase 3 E5 同 diff 落账 + Closure Gates owner doc 条目核对）；异常载荷扩展如落地，owner doc 载荷节同步（条款二）。
3. **反向保护 #8 相容（条款二——相容登记）**：TestNopRefactorBizModel :176-206 midApplyFailure 的行为断言（"already landed" 消息枚举 :197-200、已落地文件保新内容 ：201、失败文件保原值 ：202）与 (b) fail-loud 语义完全一致——**零改写**；红⑦ 载荷断言为增量（不触碰既有断言）。
4. **预设理由强制输出**：(b) 与 ：27-32 既有自声明契约一致——本工单收敛的是「承诺词与实现的矛盾」（契约漂移）而非行为缺陷，实现零行为变更；(a) 批级原子须同 diff 改写 #8 + 多文件事务语义（journal/单点提交）+ 崩溃恢复语义重设计——范围放大风险（plan Risks 在案）；(b) 下调用方「失败即有部分副作用」以显式声明 + 结构化载荷达成等价可判定性。
5. **被否决的备选**：(a) intent journal（多文件事务 + journal 重放设计，超工单包络；#8 反钉同 diff 改写义务全套）；(a) 统一收集单点提交（全文件先 temp 后统一 move——与 EditPlanApplier 逐文件 atomicWrite 的既有正确形态冲突面大）；(b) 仅改 javadoc 不加载荷（消费方对 landed 的机器可读判定缺失——条款二「如落地」采落地）；RefactorResult 本体加 landed 字段（第 2 条——成功载荷无部分落地概念）。
6. **入口 ×5 全覆盖声明**：run 为唯一执行模板（:47），rewrite/rename × preview/apply 与 CLI 全部经同一 catch 形态（:63-71/:103-110）——异常载荷扩展一处生效；dryRun=true 零影响（:64/:104 条件维持）。

**零触碰边界**：RefactorResult/EditedFile/FileEdit/NonApply 载荷 record 零字段变更；RefactorVerifier/RefactorOperation 装配面零触碰；nop-cli-core 消费方零 diff 面（异常载荷为增量字段——消费方通道条件义务不触发）。

## 裁定⑥ W-04+W-05：注册表 CHM/putIfAbsent 与语言缓存 computeIfAbsent（P2）

**决策**：

1. **W-04：HashMap（:30）→ ConcurrentHashMap + register putIfAbsent 语义（预设——C1-104 建议原文）**：register 保持同实例幂等——先 get 比较（existing == language 直接返回；existing 非 null 且不同实例即抛 ：72-74 既有冲突消息形态）；无竞争者时 putIfAbsent 写入；**败者复查返回值——非 null 且 != language 即 fail-closed 抛冲突（R1 Minor-3 语义补全，消息形态对齐既有文案）**。fails-closed 契约文 ：55-59 零变更（修复兑现契约非改契约）；registeredIds（:101-105）改 CHM keySet 快照——弱一致迭代零 CME；resolve（:84-96）零变更。
2. **W-04 被否决的备选**：synchronized + 冻结两段式（发现期结束后冻结注册——与 javadoc :20「tests and embedders may also register manually」嵌入契约冲突，冷路径粗粒度锁无收益）；Collections.synchronizedMap（register 的 check-then-act 复合操作仍非原子——缺陷本体维持）。
3. **W-05：cache.computeIfAbsent(name, this::loadLanguage) 一行修复（预设——C1-001 建议原文）**：**loader 链不回环论证登记**——loadLanguage（:64-82）→ customProviders DCL（:101-119，synchronized(this)）→ Language.fromClasspath，均不触本 cache——无 CHM bin-lock 递归死锁；锁序单向（bin lock → this 锁，后者快速路径 ：102-105 初始化完成后无锁）；**第三方 customProvider 禁递归约束（R1 Minor-⑥）**：不回环论证对第三方 provider 的契约要求 = 其 getLanguage 实现禁递归回本 provider 的 cache（:22-25 类注释防御性跳过同型的调用面延伸——违例属装配错误，不在本批修复面，红⑤ 断言不依赖该约束）。**持 bin lock 跑 loader 的持锁时长论证**：blob 解码毫秒级（数百 KB），并发同键其余线程阻塞毫秒级有界——换得单实例身份契约（TSParser :127-129 fail-fast 链消除）与双份解码表驻留消除；不同键经 CHM bin 分条互不阻塞；null 语义等价（loader 返回 null → 不落缓存、返回 null——与 ：51-54 现状一致）。Language 实例身份契约（`validSymbolCache` computeIfAbsent :732 既有正确形态为同模块对照组）随之兑现。
4. **W-05 被否决的备选**：手工 DCL 锁化重写（比 computeIfAbsent 多一套监视器与复查、无收益）；启动期 preload 全部内置语法（7 语法全量解码常驻——与 lazy 原则冲突，启动成本前移）。
5. **红④/红⑤ 驱动器取值论证**：红④ ×M=1000 轮（每轮全新 registry + barrier 齐发双线程同 id 异实例——断言每轮恰一冲突检出）；**概率形态脚注（R1 Minor-⑥）**：齐发轮的冲突检出面为概率形态（:70-76 同锁外窗口窄），**确定性红面 = CME 脚**；概率脚在修复前未捕获到红时按 E2 转变异/回退论证。CME 红面 = registeredIds 迭代与新 id 持续注册交叠（HashMap 扩容期迭代——确定性红面）；红⑤ N=8 latch 齐发、每轮全新 provider 实例（cache 冷态，以实例隔离取代语法名参数化——内置名仅 7 个，实例隔离使预热干扰结构性不存在）；TestTreeSitterNodeCache.concurrentFirstWalksCooperate（:100）为并发设施先例——位于 nop-lint-core 测试树（io.nop.lint.core.node，亲读核实），红⑤ 为跨模块形态参照非同套件先例。

**零触碰边界**：库内调用方 7+1 站点（NopLintBizModel/NopRefactorBizModel/NopLintCli/CheckMojo/LSP create/RuleTestRunner/NopRefactorCli）零触碰（全安全发布形态）；TestLanguageRegistry :74-84 顺序冲突/:87-95 同实例幂等保留不弱化；app-treesitter.beans.xml 装配资源零触碰；BUILTIN_BLOBS 与 customProviders 遮蔽语义（:89-99/:111 putIfAbsent）零变更。

## 裁定⑦ W-09：error 帧 correlation 校验 + BAD_FRAME 去同步回收 + protocolVersion（P2）

**决策**：

1. **error 帧 correlation 校验（消费侧，预设）**：request 对 error 帧补 id 校验（对齐 ：168-172 ok 帧范式）——帧 id 与在途 id 失配（含 helper 合成 id=-1 帧，tsc-bridge-server.mjs :327-331/:359-361）→ handleProcessFailure("correlation id mismatch on error frame") 回收后抛 BAD_FRAME 形态异常（消息携带对端 error code——诊断不误导）。合成 id=-1 帧处置声明：id=-1 与任何失配同规则（失配即回收，无特例）——**回收理由 = 合成帧无归属可判（无在途请求可关联）+ fail-closed 优先，代价 = 烧一预算槽（R1 Minor-③ 措勘）**；有界 restart 预算吸收，回收后错误显式抛出（rule 24 非静默吞）。**errorOf 在途 id 误归因消解论证**：校验前置使 errorOf（TscProtocol :119-126）只被等 id 帧调用——消息中 id 与真实请求恒一致，误归因面消解于调用侧，不重复改 errorOf 签名。
2. **BAD_FRAME = 去同步信号回收（与 W-12 生产侧映射的分工声明——CS-R2-001 时序 B 去重注记兑现）**：生产侧回收映射（裁定① W-12——decode 抛错→handleProcessFailure）与消费侧校验（本裁定——error 帧 id 失配→handleProcessFailure）互补防双计：同一失序事件只走单一出口（不可解码帧走生产侧 decode 面；可解码但 id 失配帧走消费侧）——流去同步被静默接受（时序 A）与陈旧 error 帧过握手（变体）两形态闭合。
3. **protocolVersion 字段（预设采）**：ready 帧增 protocolVersion 字段——Java validateReady（TscProtocol :71-82）要求字段存在且等于当前协议版本常量，helper mjs（:369-373）双侧同步；错配 → TscBridgeUnavailableException 显式命名版本失配。兼容面：resolveHelperPath per-cwd 解析使同仓 checkout 双侧同发布——错配仅在跨 checkout 混用出现，正是该守卫的收益面（协议漂移从「运行期不可辨失效」变为显式可定位）；旧 helper + 新 Java = 显式握手失败（有界、可诊断——不采静默容忍）。工具仓变更非产品非 PA 面，留痕登记（plan PA 表 row 2）。
4. **被否决的备选**：error 帧失配不回收仅抛对端原错误（K 的错误码抛给 K+1 调用方的误归因本体维持——流已去同步，后续帧全部错位）；protocolVersion 不采（红⑧ 版本用例无载体，协议漂移失效面维持）；多版本协商（协议 v1 单消费方——协商复杂度无收益）。
5. **FakeTscPeer 扩模式定夺**：`error-stale-id`（对在途请求回 error 帧但携带错配 id）+ `half-frame-then-die`（写半行非 JSON 后退出）——红⑧ 载体；既有 parkForever/marker 机制（:70-73/:153-159）零改动。

**零触碰边界**：TscProtocol 既有 decode/isOk/text/intValue/resultOf 语义零变更（validateReady 增版本校验为增量）；helper mjs 既有 op 分派与 error 帧形态零变更（仅 ready 帧增字段）；`_vfs` 规则 fixture 零触碰（条件申报③）。

## 条件申报登记（禁静默扩权——预设全部不触发）

① **W-02 Route-B**（`EvalExprProvider` 补 volatile——nop-core 平台设施）：预设不采（裁定②第 3 条）；如后续采须四件证据链（plan 追加申报 + 本文档专节修订 + draft review 记录追加 + 回归测试含 nop-kernel 通道）。
② **`*-api` 叶**：四成员模块无 `*-api` 子模块（单元 R1 实测）——结构性不触发；RefactorResult/NopRefactorException 载荷扩展为 nop-refactor-core impl 面。
③ **`_vfs` 测试 fixture**：FakeTscPeer 扩模式为 java 测试类——预设不触发。
④ **nop-commons LocalFileLock 设施本体**：使用面引用（裁定③第 3 条）——预设不触发；如需改设施本体须四件链追加申报。

## 批域边界

- **与 8.37/R3-07 边界**：其修复面 nop-graph/nop-code 与本批 nop-tools 零相交；本批栅栏域（进程代际）与其幽灵线程治理域（线程生命周期）机制不同域——2530 文档仅作结构模板引用不重开。
- **与 8.39/R3-09、8.71/R4-31 边界**：nop-code 面零交集零触碰；本批不动 nop-code。
- **与 R-G 边界**：nop-lint/nop-jq owner doc 缺口声明维持；反向保护 #9（ThreadLocalRetentionTest :79）/#11（SubtreeArenaTest :30-58）零触碰、条款登记归 R-G；lint 面（W-01/W-02/W-03/W-07/W-09）契约暂承载于本文档 + `source-anchors.md` 锚点行（Phase 3）+ `module-groups.md` 行核对。
- **与 design 07 §4 / design 11 §4 关系**：Route A 全局换装决策维持（裁定②）；program cache tsconfig-hash 失效语义（design 11 §4）零触碰（tsconfigChangeInvalidatesTheProgramCache 既有用例为零回退锚）。
- **跨单元家族注记**：NodeTscBridge 族与 4.3 MA-05/MA-06（世代守卫）、3.2 MA-02、5.6 W-04 的关联为 cra-index 台账注记级——不并单不回写他批台账。
- **历史裁定文件不回写**（batch-proposal/merge-adjudication/C10/单元维度报告——勘误先例）；单元审计产物（plan 2489 产物目录）为本批事实源。

## 裁定⑧ 测试缝总表（红①–红⑧ 驱动器 + 零回退钉清单 + C10 映射）

### 红证形态分级（R1 Major-2 口径）

- **确定性当前即红**：红②②（W-02 P1 后果面主钉——卸载契约）、红④（CME 脚——race 脚为概率形态，见裁定⑥ 第 5 条脚注）、红⑤、红⑥（修复前红修复后绿）、红⑧、红③ 失败注入面。
- **构造性概率红**（红证 = 有界迭代 loop-红捕获——任一轮即红，失败即转变异/回退论证——E2 原文许可；修复后同 trials 零逃逸为绿面）：红②①（交错丢更新）、红③① 踩踏面、**红①（R1 Major-4 再勘降级——原「单次高概率红为主证 + 循环重放降绿证钉」分级撤销）**。
- **结构钉编译期红**：红⑦（载荷访问器 pre-fix 不存在——2530 红①(b) 结构钉先例）；红②② 的 uninstall API、红⑧ 的 protocolVersion 常量同属编译期构造红形态。
- 变异锚定面 0（红钉均为缺保护面新建；hang-once-then-ok 改写属假保护修复非变异）；反向钉定 2 处同 diff 改写义务（#2——裁定②第 6 条迁移表；#8——裁定⑤第 3 条相容零改写登记）；反向钉定 2 处不入批确认（#9/#11 归 R-G）。

### 红①–红⑧ 驱动器表

| 钉 | 成员（C10 面） | 红证形态 | 驱动器与断言 | 缺陷态红判据 |
|---|---|---|---|---|
| 红① | W-01（M-P1-1；nop-lint-js 测试树） | **构造性概率红——有界迭代 loop-红捕获（任一轮即红）+ 修复后同 trials 零逃逸[R1 Major-4 再勘]** | FakeTscPeer 扩两模式改写为可实现的 **hang-past-deadline 族**（`delayed-eof` 机制删——SIGKILL 下 peer 不可能「越过 destroy 时窗后退出」，EOF 由旧 reader 于 pipe 关闭后产生而非 peer 延迟退出）：① `hang-respond-past-deadline`（首代 peer 对查询先 flush 完整响应帧再 park——**近零 deadline** 使超时确定先于消费触发，滞留响应帧构成分支 A 毒源）；② `hang-silent-past-deadline`（park 无响应——分支 B 面由 pipe 关闭后旧 reader 的 EOF offer 与 respawn 竞速构成）；两模式共享 **marker 切换代际行为**（回收后的新代 peer 读 marker 后正常应答——respawn 链可断言）；断言：任一轮出现「健康 respawn 被杀 + 预算作废」或「握手误判 EXHAUSTED」即红；修复后同 trials 全部收敛（initProject 成功、restartsLeft 消耗 ≤1）；断言④ respawn/close 后无存活进程（peer 启动时经 marker 文件上报 pid，测试以 ProcessHandle 断言——零产品 seam）；断言⑤ 恢复链异常消息死因一致 | pre-fix 有界 trials 内任一轮毒化即红（loop-红捕获）；失败即转变异/回退论证（E2 原文许可） |
| 红②① | W-02 交错面（M-P1-2） | 构造性概率红——loop-红捕获 | CyclicBarrier 对齐 T1 install/还原交换与 T2 手工外来 wrapper 写槽 ×N 轮（零 mockito） | 任一轮「T1 写后 LDE 从全链消失且非经还原出口」即红；失败即转变异/回退论证 |
| 红②② | W-02 卸载契约（M-P1-2） | **确定性当前即红——P1 后果面主钉** | install→透传求值→uninstall→断言槽 equals 安装前快照 + 链扫描全链无 LDE 残留 | pre-fix 无 uninstall API——编译期红 + 无还原语义红（双形态） |
| 红②③ | W-02 attach/detach 组合（M-P1-2） | 编译期红随红②② 载体 | install→外来写者写槽→uninstall→断言外来执行器保留、残留经 WARN 显式登记（watch-only 残差的可观测面）；**两变体[R1 Major-2(c)]：包裹型（外来 wrapper 包裹链顶——uninstall 分支二）/替换型（外来执行器整体替换槽、无 delegate 访问器——对齐真实 attach，uninstall 分支三）** | pre-fix 编译期红（无 uninstall）；post-fix 组合语义绿（替换型下快照按声明丢弃——分支三语义断言） |
| 红③① | W-03 踩踏面（M-P2-5） | 构造性概率红——loop-红捕获 | 预置 X.tmp + barrier 对齐双线程独立 cache 实例同文件 save ×N 轮 | 任一轮 NoSuchFileException 或 `.tmp` 残留即红 |
| 红③② | W-03+W-10 失败注入面（M-P2-5） | 确定性当前即红 | 目录只读注入 save → 断言异常归因 + 无 `.tmp` 残留；move 失败注入同型断言（W-10 激活断言）；**注入序列预置锁文件（R1 Minor-⑥）——锁获先于 temp/move：只读目录会同时阻断锁文件创建使失败归因漂移到锁获段，注入前预置 `.lock` sibling 使失败面落在 temp/move 段**；BaselineFile 撕裂写 load fail-closed 契约锚（pre/post 双绿——零变更锚） | pre-fix catch 无清理（:204-207）——残留断言红（确定性） |
| 红④ | W-04（M-P2-4） | 确定性当前即红 | 双线程 barrier 齐发同 id 异实例 ×1000 轮断言每轮恰一冲突检出 + registeredIds 迭代与新 id 注册交叠无 CME | 任一轮 0 冲突检出（胜者通吃）或 CME 即红；既有 ：74-84 保留不弱化 |
| 红⑤ | W-05（M-P2-2） | 确定性当前即红 | 全新 provider 实例（cache 冷态）+ N=8 latch 齐发并发首触同一内置语法名，assertSame 收敛单实例 | 任一轮观测 ≥2 实例即红（blob 解码毫秒级窗口） |
| 红⑥ | W-07（M-P2-6） | 确定性当前即红——**修复前红修复后绿** | POSIX 只读目录 + 守卫必拒编辑 → 断言返回 rolledBack=true 且零文件落盘（零 temp 创建）；否决占锁注入变体（平台相关语义分叉） | pre-fix 写盘先行（:89）——createTempFile 于只读目录抛 IOException 先于守卫——红；post-fix 守卫内存先决、零目录触碰——绿 |
| 红⑦ | W-08（M-P2-7） | 结构钉编译期红 + 行为增量 | 以 TestNopRefactorBizModel :176-206 为基：NopRefactorException 载荷 landed/notAttempted 访问器断言（增量，#8 既有断言零改写） | pre-fix 访问器不存在——编译期红；既有行为断言 pre/post 双绿（零回退锚兼保护面） |
| 红⑧ | W-09（M-P2-1） | 确定性当前即红 | FakeTscPeer `error-stale-id` → 断言回收（预算消耗）+ BAD_FRAME 形态失败；`half-frame-then-die` → 断言 decode 回收后下一查询 respawn 成功（maxRestarts=1 形态恰耗 1 槽——W-12 激活断言）；protocolVersion 错配拒绝用例（validateReady，结构钉） | pre-fix：stale error 无回收（预算不变）+ BAD_FRAME 不回收（下一查询烧死进程再烧槽）——红；版本常量 pre-fix 不存在——编译期红 |

**hang-once-then-ok 假保护处置（M-P1-1 义务——预设改写）**：既有用例（NodeTscBridgeTest :109-129）同 diff 重写为 hang-past-deadline 族两模式（hang-respond-past-deadline/hang-silent-past-deadline——红① 行），原时序敏感断言（elapsedMs < 2500，:122）迁移为结构化回收断言；改写前后断言语义对照 Phase 2 留痕。否决「确定性用例并存登记」（时序敏感假保护留存 = 持续 flake 面与假绿风险——C10 #13/#3/#18 定性在案）。

### 零回退钉清单

- **分解通道基线（勘误 3 亲跑在案）**：2614 tests / 0 Failures / 26 Errors / 3 Skipped——零回退实证 = `-fn` 分解通道收口 + 计数与签名比对（`-fn` 下 EXIT=0 恒真——门禁实质 = 计数与签名比对收口，R1 Minor-2 口径）。成员模块绿基线：nop-lint-js 61 / nop-lint-core 816 / nop-refactor-core 37 / nop-treesitter 616 有效绿（629 − 11E − 2S）。
- **3S 既有签名**：nop-treesitter JniVsPureSpeedTest 2 + nop-rg-core GlobMatcherTest 1（数量与归属比对维持）。
- **26E 既有红面签名隔离声明**：单一签名 `nop.err.api.context-already-closed` 共 26 处、3 模块 6 测试类（nop-treesitter TestShellAnalysisGraphQL 9 + TestParseTreeSitterGraphQL 2；nop-lint-graphql TestNopLintGraphQL 4；nop-refactor-graphql TestRenameGraphQL 4 + TestNopRefactorGraphQL 4 + TestRefactorP0ClosedLoop 3）——**26 处均非本批面**（勘误 5 三源定性：GraphQL RPC 入口 harness 域，与本批 9 站点零机制交集），零触碰义务 + 逐类逐数签名比对；新钉不得掩盖或放大该面。
- **点名既有钉族零回退**：TestLintDeadlineExecutor 透传/超时/深度三组（:100-120/:124-155/:225-270——重写时语义等价迁移义务，裁定②第 6 条）+ TestLanguageRegistry（:74-84 顺序冲突 fail-closed / :87-95 同实例幂等）+ TestNopLintCliCache + TestRuleResultCacheCorruptEntry（:31-38/:41-81 fail-closed）+ TestEditPlanApplier（:106-120 rollback 终态断言）+ TestCliBaseline/TestBaselineEngine + NodeTscBridgeTest 五组（error 帧 ：80-92 / BAD_FRAME :95-106 / hang-once-then-ok 改写面 ：109-129 / crash-once :134-148 / 预算耗尽 ：151-169——握手 EOF 用例 ：68-75 的 EXHAUSTED 终态断言在 W-11 补 destroy 后语义维持不变）+ TscProtocolTest 8 用例（:22-90——无 id-mismatch-on-error/版本用例，即红⑧ 新增面；**protocolVersion 落地破坏 2 处绿锚的豁免登记[R1 Minor-④]：readyFrameWithTypescriptIsAccepted :63-65 与 FakeTscPeer.ready :50-52 随红⑧ 同 diff 语义等价更新[补版本字段]——其余 6 用例零触碰**）+ TestNopRefactorBizModel 11 tests（#8 :200-202 在 (b) 裁定下零改写）+ TestTreeSitterProvider（:36-108 顺序功能面）+ TestTreeSitterNodeCache + P3 面零触碰锚（ThreadLocalRetentionTest/SubtreeArenaTest/TscTypeResolverTest/TestJavaTypeResolver/XNodePatternMatcherTest）。

### C10 缺保护 8 缺陷面同 diff 映射 + P3 激活断言登记

红①↔M-P1-1 / 红②↔M-P1-2 / 红③↔M-P2-5 / 红④↔M-P2-4 / 红⑤↔M-P2-2 / 红⑥↔M-P2-6 / 红⑦↔M-P2-7 / 红⑧↔M-P2-1——每条与本体同 diff 禁拆分交付（E2）。P3 激活断言（无独立钉编号——2530 C5-R2-007 先例）：W-10 由红③② 残留断言承载 / W-11 由红① 断言④ 承载 / W-12 由红⑧ half-frame 恰 1 槽断言承载。测试命名 `Test*_<工单>` 惯例（W_01..W_12，P3 断言随组测试类登记归属）；红绿快照落 `_tmp/red-green-2531-summary.md` + 逐 trial 日志（沿 2525–2530 形态）。

## 评审记录

- 设计评审 R1：独立子 agent agent_e69b6058（fresh session，2026-11-03）——**Blocker=1，Major=4，Minor=6；结论「修订后须复审修订面」**，全部修订已随轮落盘。**Blocker（plan 勘误 7）**：plan 四处「LintEngine 旁证零 diff」与裁定② finally 配对强制输入矛盾——LintEngine 移入触碰面清单（工单表 row 2/In-Scope/PA 表/E3 面/Phase 2 勾验项），本裁定零触碰边界句同步改写。**Major-1（裁定①）**：栅栏捕获点前移 spawn 监视器帧内（lambda 捕获 spawn 局部变量——Thread.start 先行发生于捕获值才是结构化封死；reader 入口捕获存在调度延迟窗误捕新代引用残窗），「结构性不可能」论证同步修正。**Major-2（裁定②）**：链扫描收窄为仅经 delegate()（:60-62）可穿越的透明层（真实 attach 为不透明整体替换、其下不可穿越）；uninstall 补第三分支（可见链段确认无本代 wrapper → 不动槽 + WARN + 释放所有权，快照仅当槽顶可确认属本代时交换）；红②③ 增替换型外来写者变体。**Major-3（裁定③）**：锁生命周期专条（类内 acquire-at-load + load 失败 try/catch 释放 + save finally 释放 + 中途放弃显式声明 CLI 进程域）——消除 OverlappingFileLockException 永久 fail-closed 毒化面；不触 CheckRunner。**Major-4（裁定⑧）**：红① 降级构造性概率红（有界迭代 loop-红捕获任一轮即红 + 修复后同 trials 零逃逸，与红②①/红③① 同型）；两模式机制改写 hang-past-deadline 族 + marker 切换代际 + 近零 deadline（delayed-eof 越过 SIGKILL 时窗机制不成立——删）；执行期提示与 plan Goals/Exit Criteria 分级措辞同步再勘（plan 勘误 8）。**Minor 六项**：①按代队列分离对 plan 三方案菜单的偏差显式登记勘误（菜单选项 1[局部引用捕获比对]的强化形态——非临场发明）；②BAD_FRAME taxonomy 注记（回收映射仅覆盖流完整性面，内容型 :94/:108 族不回收）；③id=-1 回收理由改写（合成帧无归属可判 + fail-closed 优先，代价 = 烧一预算槽）；④protocolVersion 破坏 TscProtocolTest.readyFrameWithTypescriptIsAccepted/FakeTscPeer.ready 2 绿锚——零回退钉清单登记红⑧ 同 diff 语义等价更新豁免；⑤迁移表补全至 10 用例（补 ：160-180/:204-221——后者为 rule 23 接线证明不得丢失；:185-190 不触槽原地保留）；⑥红④ 概率脚注 + W-05 第三方 customProvider 禁递归约束 + 红③② 预置锁文件 + LocalFileLock 失败通道 NopException 非同类型措勘。**待办：Blocker+Major 修订面独立复审通过后，plan 2531 Phase 2 方可启动。**
- 设计评审 R2（复审修订面，2026-11-03）：独立评审 agent_e69b6058 同会话——R1 全部 11 项（1 Blocker + 4 Major + 6 Minor）逐条确认闭合，未引入机制级新问题；残余措辞级 3 处（plan Goals C10 bullet 分级枚举补红①归属、plan 内联旧 delayed-eof 句加撤销标记、本文件卸载契约句加「本代安装占顶」限定）已随本轮落盘。**结论：可进入 Phase 2。**
