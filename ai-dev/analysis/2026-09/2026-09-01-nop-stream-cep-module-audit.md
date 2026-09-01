# nop-stream-cep 模块审计（roadmap item 9，Phase M 第三项）

> Status: resolved
> Date: 2026-09-01
> Scope: `nop-stream/nop-stream-cep/` 全模块（77 main / 57 test Java 文件，live 核对于 worktree 根）：2026-05-20 duplicate-code audit 与 2026-06-30 code audit 的 cep 相关发现整改收口验证 + 2026-08-04-2300-2 remediation plan cep 侧修复点复核 + 产品化视角新增审计（D-GAP item 9「无额外重点」结论勾销 / NFA-SharedBuffer-模式编译核心路径 / 空壳扫描 / 测试覆盖抽查 / `_gen` 生成纪律）+ 小缺陷就地修复与收口
> Conclusion: 两轮历史审计 cep 相关发现整改收口**成立**（05-20 §2 cep 主体侧：runtime 侧四文件清除引用 item 8 §1.1、CepOperator state 初始化已修复且与 runtime 侧重复归零；06-30 seed+派生 10 项：9 landed/unchanged + 1 partial 路由 Follow-up item 22，无 regressed）；2300-2 cep 侧修复点 live 存在性复核**成立**（releaseNode null 分支 pop :291 + 两个回归测试 live）；D-GAP item 9「无额外重点，按既有审计模式执行」结论显式记录且对照无遗漏；产品化审计采信 8 项缺陷就地修复（4 项行为修复配 focused 测试 + 2 项测试/门禁收口 + 7 处文档/死代码清理）+ 1 项前置会话断点修复（遗留失败测试 + scratch 文件 + invariant 注册表漂移）；hollow scan exit 0；无大缺陷（显式无 Follow-up 追加）；`./mvnw test -pl nop-stream -am -T 1C` 全模块绿（cep 359/0）
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 9；plan `ai-dev/plans/nop-stream-productization/2026-09-01-1457-1-cep-module-audit.md`
> Related: `2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`、`2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7，方法论/报告结构复用；其 §2.2 Flink/Beam 8 格裁定、§1.1 §7 空壳模块结论本报告引用不重复裁定）、`2026-09/2026-09-01-nop-stream-runtime-module-audit.md`（item 8，其 §1.1 已核 05-20 §2 runtime 侧四文件全部删除）、`2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 9 行）、`ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md`

## Context

- roadmap item 9（Phase M 第三个审计项，deps item 6 已 done，sibling items 7/8 已 done）：对 `nop-stream-cep` 按产品标准完成模块审计并收口。D-GAP §3.1 对 item 9 的裁定为「**无额外重点，按既有审计模式执行**」（D-GAP 裁定条目不触及 CEP 子系统；`MalformedPatternException` fail-fast 路径属既有异常层级审计范围），本报告 Phase 2 §2.1 显式勾销。
- 审计方法与 items 7/8 对齐：live 锚点优先于历史 plan 状态（2026-08-06 baseline 证据分级）；所有 live 核对命令于 2026-09-01 在 worktree 根执行，排除 `target`；核心路径优雅性审计由两个独立 explore subagent（NFA/SharedBuffer 与 pattern/编译链）完成初审，执行者逐条源码复核后才采信（其中 1 项经复核被否决，见 §2.2）。
- **执行环境重要事实（前置会话断点）**：本 plan 执行起点，worktree 内存在一笔**未提交**的 cep 模块改动（15 main + 5 test 修改 + 2 个未跟踪 test 文件，480 insertions/200 deletions），为某次更早会话的同族审计修复波（诊断参数补全、releaseNode repeat-visit 语义、flushCache write-through 化、CepOperator 悬挂清理重构、死代码删除、若干新测试），该会话在「新增 E2E 测试失败 + 留下 TestScratchDebug 调试文件 + invariant 注册表行号漂移未同步」处中断且未写入任何 plan/日志。本 plan 以该 worktree 状态为 live 基线执行全部审计（Phase 1/2 的所有 live 读取均含该波改动），断点三件套就地收口（见 Phase 3 CE-0），全量随本报告收口提交。经复核该波改动 350/351 测试绿、与本 plan 采信的修复互不冲突，采纳为基线而非回退。
- 验证基线：修复前 `./mvnw test -pl nop-stream -am -T 1C` 于 cep 模块 351 测试 1 失败（即断点测试，主代码经 TestScratchDebug 逐 watermark 诊断验证正确——失败为测试自身构造缺陷）；修复后全模块绿（见 §3.3）。

## Phase 1 — 历史审计整改收口验证

### 1.1 2026-05-20 duplicate-code audit §2 cep 主体侧核对

> 状态判定同 items 7/8。§2 的 runtime 侧四文件（CepWindowOperator/CepWindowTrigger/CepWindowAssigner + TestCepWindowOperator）清除结论**引用 item 8 报告 §1.1**（已核四文件全部删除），本 plan 仅做抽查确认（`find nop-stream -name "CepWindow*"` 零命中，2026-09-01 live）+ 核对 cep 主体侧。

| 核对项 | 05-20 发现（时点） | live 状态 | 证据锚点 |
|---|---|---|---|
| CepOperator 生产引用 | 「生产引用 fraud-example」 | **landed（生产接线完备）** | 公共 API 全链：`CEP.pattern()` → `PatternStreamBuilder.build:146-160`（`new CepOperator(...)` + `keyedStream.transform("CepOperator", ...)`；非 keyed 走 `:167` GlobalCepOperator 路径，TestCepNonKeyedEntryE2E 覆盖）；fraud-example main 6 文件消费 cep NFA 层（`FraudDetectionDemo.java:20-24` import NFA/NFAState/NFACompiler/AfterMatchSkipStrategy）；cep 模块内 NFA/SharedBuffer/Pattern Javadoc 交叉引用；core `TaskProcessingTimeService.java:36` 注释引用其 fail-fast 意图 |
| CepOperator state 未初始化（05-20 §2.3：两者 initializeState 关键代码被注释、运行时 NPE） | cep 主体侧同病 | **已修复（landed）** | 现 `open()`（CepOperator.java:259-379）完整初始化：可插拔 state backend（`:266-273` `stateBackend.createKeyedStateBackend` + deferred restore `applyPendingRestoreState()`）、`:282-288` computationStates/elementQueueState/partialMatches 三状态初始化、无 backend 时 `:277-280` 显式 WARN + MemoryKeyedStateBackend 回退；processing-time 缺 ProcessingTimeService 分支 `:543-551` 显式 fail-fast（StreamException，非静默 NPE） |
| 与 runtime 侧 ~85% 重复 | CepWindowOperator 四文件克隆 | **随 runtime 侧删除归零** | item 8 §1.1 引用 + 抽查 `find` 零命中（见上）；模块内无其他 CepWindow* 残留 |
| §2.4 建议「未来 CEP+Window 集成应在 CepOperator 之上构建」 | — | 无新集成需求（无消费者），维持裁定 | `rg CepWindowOperator` 全仓零命中 |

**结论**：05-20 §2 cep 主体侧整改收口成立，无回潮。

### 1.2 2026-06-30 code audit cep 相关发现核对表

> 先列全表再逐项核对。seed 清单 = plan Current Baseline 所列；派生规则 = 报告中「涉及文件位于 nop-stream-cep」的其余发现。三态：landed / partial / regressed。

| # | 06-30 发现（章节） | live 三态 | 证据锚点 |
|---|---|---|---|
| 1 | §1.1 模块统计（时点 72 main/46 test，活跃开发） | **landed（活跃开发，非缺陷）** | live 77 main / 57 test（53 个 Test* 类 + 4 个 helper：Event/SubEvent/CepTestUtils/MockRuntimeContext）；323 @Test（06-30 时点 272） |
| 2 | §1.2 TimerService 名称冲突（core.time @Deprecated vs cep.time 活跃；`cep.time.TimerService:34` Javadoc 引用 core 版） | **landed（core 侧修正后引用语义恢复正确）** | core 侧 item 7 S-8b 已修正：`core/time/TimerService.java:23-30` 现为「user-facing timer API handed to ProcessFunction.Context#timerService() … NOT deprecated」；cep.time.TimerService:34 的 `{@link io.nop.stream.core.time.TimerService}` 引用对象从「被误标弃用的幽灵接口」恢复为 live 公共 API，语义正确（CepOperator.TimerServiceImpl 经同一 ProcessingTimeService 链提供 currentProcessingTime） |
| 3 | §1.3 依赖方向（时点 cep → core + nop-xlang ✅） | **landed（改善漂移，不判 regressed）** | live pom 仅 `nop-stream-core` + `guava`（+test junit），nop-xlang 依赖已移除——CEP 独立性改善，按「时点值 → live 变化」记录 |
| 4 | §1.3 违规检查 runtime → cep 幽灵依赖 | **landed**（item 8 核对表 #6 已核，cep 侧无行动项；fraud-example → cep 为合法正向依赖） | item 8 报告 §1.2 #6 锚点 |
| 5 | §2.1 UOE 桩 `GroupPattern.where/or/subtype`（原文判定「合法的禁止调用设计」，低） | **unchanged（维持合法裁定 + 行为被测试钉定）** | `GroupPattern.java:46,51,56` guard 式 UOE（"does not support" 消息族，hollow scan 归 low 信息级）；`TestErrorDiagnosticsEnhancement:103,113` 显式钉定 UOE 行为（testGroupPatternWhere/OrThrowsUnsupportedOperationException）；taxonomy 一致性裁定见 §2.2 W-7 |
| 6 | §2.2 通配符导入（派生：cep 15 个 test 文件） | **partial（main 清零 / test 15 文件残留）** | main：`rg "import .*\.\*;" src/main` 0 命中；test：15 文件。→ **路由 Follow-up item 22**（core 169/runtime 108/cep 15/flow 1 跨模块统一 sweep，item 7 已立项，本 plan 不重复机械修改） |
| 7 | §2.4 `_gen` 生成代码仅在 cep 存在（4 文件，生成纪律不手改） | **unchanged（纪律成立，Phase 2 §2.5 专项核验）** | 4 文件 live，见 §2.5 |
| 8 | §3.1/§3.2 测试覆盖（时点 46 类 272 @Test，NFA 引擎覆盖充分） | **landed（覆盖增长）** | live 53 类 323 @Test；覆盖抽查见 §2.4（含 functions 包缺口按缺口记录处理） |
| 9 | §3.3 E2E：TestCepPublicApiE2E / TestCepOperatorStateRecovery | **landed（live 存在且扩展）** | 两者 live；TestCepPublicApiE2E 现含 5 用例（含本 plan 修复的 branching E2E）；operator 包测试 16 文件 |
| 10 | §2.2 大量 `return null`（100+，集中在 runtime JDBC/LocalFile 类，无 cep 文件被点名；派生规则扫描 cep） | **landed（cep 无 catch-return-null 模式）** | 两个 subagent 全量读 cep main + hollow scan：无 catch-return-null / 空 catch / 吞异常路径（§2.3）；cep main 现存 null 均为契约语义（previousNodeId nullable 等） |

**结论**：无 regressed；唯一 partial（#6 test 通配符）已路由 Follow-up item 22；#5 维持原「合法设计」裁定。跨模块路由记录：无（InputGate/WindowedStreamImpl 等 core 侧发现已由 item 7 核对，不涉及 cep）。

### 1.3 2026-08-04-2300-1/2/3 remediation plans cep 侧复核

> 复核基准：item 8 报告 §1.3 同款方法——抽查三 plan 的 cep 侧修复点 live 存在性。

| Plan | cep 侧修复点 | 复核结论 | 抽查证据 |
|---|---|---|---|
| 2300-2 checkpoint-state-backend-cep-correctness | ① `SharedBufferAccessor.releaseNode` null 分支补 `versionsToExamine.pop()`（恢复栈 lockstep 不变量）；② 回归测试 `testReleaseNodePopsVersionOnNullEntry`；③ E2E `testFollowedByAnyBranchingWithSkipPastLastEvent` | **成立（landed，三点全 live）** | ① `SharedBufferAccessor.java:286-293`：null 分支 `versionsToExamine.pop()` 在 `continue` 前，注释完整陈述 lockstep 不变量；② `TestSharedBufferExtended.java:311`；③ `TestNFAExtended.java:641`。**同族路径复查**（Phase 2 §2.2 A-1）：releaseNode 其余全分支 push/pop 配对、put/lockNode/releaseEvent 配对平衡、无第二个 lockstep 违例 |
| 2300-1 coordinator-runtime-concurrency-recovery-hardening | Targets 逐条核对：JobCoordinator/InputGate/TaskManager/SupervisionLoop/NopStreamErrors 全在 runtime/core | **不适用（无 cep 侧修复点）** | plan Targets（:58/:80/:100/:117）逐条 rg 核对；cep 仅出现在 Non-Goals（"Plan {2}" 引用） |
| 2300-3 contract-drift-config-test-integrity | Targets：core state SPI + runtime `_module` + core/runtime 测试 | **不适用（无 cep 侧修复点）** | plan Targets（:64/:84/:101）逐条核对；cep 仅出现在 Non-Goals |

**结论**：「三 remediation plans 已收口」对 cep 模块**成立**（2300-2 cep 侧三点全 live；2300-1/3 无 cep 侧修复点）。

## Phase 2 — 产品化视角新增审计

### 2.1 D-GAP item 9 审计重点勾销清单

| # | D-GAP §3.1 item 9 条目内容 | 消化结论 | 证据 |
|---|---|---|---|
| 全部 | 「**无额外重点，按既有审计模式执行**（D-GAP 裁定条目不触及 CEP 子系统；`MalformedPatternException` fail-fast 路径属既有异常层级审计范围）」 | **结论显式记录 + 对照勾销无遗漏**。D-GAP §3.1 表中 item 9 行仅此一条、无派生重点（对照 items 7/8 的多重点行）；其注记的两项语义（异常层级 + `MalformedPatternException`）按「既有审计模式」并入 §2.2 B 维度执行：全 cep main 的 `MalformedPatternException` 21 处 throw 站点全部携带 `ERR_CEP_MALFORMED_PATTERN + ARG_PATTERN_DETAIL`（模式上下文，无裸字符串构造器残留）；异常层级一致性核验见 §2.2 B（采信 2 项 taxonomy 缺陷修复 CE-5/CE-8，其余裁定 convention-compliant） | D-GAP 报告 §3.1:130；本报告 §2.2 |

**勾销对照**：D-GAP item 9 行为单条结论行，无遗漏条目（items 7/8 的「三项重点/两项重点」结构在 item 9 不适用）。

### 2.2 核心逻辑优雅性/可靠性审计（双 explore subagent + 执行者逐条复核）

> 范围：NFA 状态机（nfa/ + nfa/aftermatch/ 跳过策略家族）、SharedBuffer/SharedBufferAccessor（引用计数、锁与并发、2300-2 同族路径）、模式编译链（pattern/ → nfa/compiler → model/builder 含 `_gen`）、operator/CepOperator 计时器语义、functions/adaptors、configuration。subagent：NFA/SharedBuffer（session `ses_fa3363831ffeV13LhnUQ5gF7JK`）与 pattern/编译链（session `ses_fa3360e1cffeelAyWZT3rg0Wq6`）；其发现经执行者 2026-09-01 live 源码复核后采信（1 项否决，见末尾）。发现编号 CE-（修复）/ W-（watch-only residual）。

**A. 验证干净的核心维度（明确核验无发现）**：

| 维度 | 结论 | 证据 |
|---|---|---|
| 2300-2 同族 lockstep 不变量 | `releaseNode` 全分支（null 分支 :291 / 正常 :295 / 边推入 :306-307 / 终态 :313-318）push/pop 配对；`extractPatterns` 单复合栈无平行栈问题 | SharedBufferAccessor.java 全读 |
| 引用计数 lock/release 配对 | `put`（新节点 lockEvent :108）、`addComputationState`（NFA.java:777 lock）、`computeNextStates` :749-753（null 守卫 release）、stop 分支 :395-397、timeout :324-325、skip 策略 prune（AfterMatchSkipStrategy:115-117）——全路径平衡；pending 状态 timeout 经 `processMatchesAccordingToSkipStrategy`（NFA.java:446-477）延迟释放，无泄漏 | 逐路径核验 |
| Guava cache 驱逐 × refcount | cache 严格 write-through（每次 mutation 即时 upsert 到 MapState），SIZE 驱逐仅丢弃缓存映射，`getEntry/getEvent` miss 后从权威 state 重载——**驱逐不可能丢失活动 refcount** | SharedBuffer.java:265-355；TestSharedBufferCache.testWriteThroughCacheAndStateConsistency |
| accessor open/close 语义 | close() = flushCache() clear-only，幂等；跨 key clear-on-success 为 load-bearing 且已文档化（SharedBuffer.java:357-368） | 同上 |
| start-state 排除不变量（CepOperator.resetNfaStateIfFullyTimedOut Javadoc 三断言） | 全部成立：`isStateTimedOut` 排除 start（NFA.java:353）、`doProcess` 必重加 start（:736-747）、skip 策略不剪 start（startEventID == null，AfterMatchSkipStrategy:113-114） | 执行者复核 |
| 跳过策略家族克隆漂移 | 无行为漂移：SkipToFirst/SkipToLast throwExceptionOnMiss 逐字节同构（差异仅构造类型）；SkipPastLast/SkipToNext getPruningId 差异为有意的 max/min 语义 | 文件比对 |
| 事件时间乱序 / timer 语义 | 乱序上游缓冲（CepOperator :563-575，late 事件 side-output/计数丢弃）；`advanceTime` 超时时间戳计算 null-safe（:282 containsKey 守卫 + :316-320）；空模式空 NFA no-op；`windowTime <= 0` 一致早退 | NFA.java:265-346 + CepOperator.java:784 |
| 吞异常 / 裸 RuntimeException | cep main 13 处 catch 全部 wrap-and-rethrow（coded StreamException/MalformedPatternException）；无空 catch / catch-return-null / 裸 RuntimeException | 双 subagent 全读 + hollow scan |

**B. 采信缺陷（Phase 3 就地修复）**：

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| CE-1 | **high（静默语义禁用）** | 内层 windowTime（`oneOrMore(windowTime)`/`times(n,windowTime)`/`times(from,to,windowTime)`/`timesOrMore(n,windowTime)`）不校验非正值：`Duration.ZERO`/负值经 `Quantifier.Times`（:200-209 仅校验 from/to）直通 NFA per-state windowTimes，`NFA.isStateTimedOut:353` 的 `windowTime > 0L` 守卫使其**静默禁用** per-iteration 超时——恰为 `within()` 已加固防住的同类失败模式（Pattern.java:295-297 注释原文点名）；模型路径 `CepPatternBuilder:176-184` 的 `partModel.getWindowTime()` 同样直通受影响 | Pattern.java:420-426,462-469,493-504,530-537；Quantifier.java:200-209；NFA.java:353 | Phase 3 修复（4 站点 + `checkInnerWindowTime` helper，mirror within() fail-fast） |
| CE-2 | medium（fail-slow） | `Pattern.begin(null)`/`followedBy(null)` 等被接受（ctor `if (name != null && ...)` 仅检查 `:` 分隔符），null name 流入 NFAStateNameHandler 产出损坏的内部状态名 `"null:0"`，匹配归因不可追溯——应 API 边界 fail-fast（Flink begin(null) 同样前置失败） | Pattern.java ctor（':' 检查处） | Phase 3 修复（ctor null 守卫覆盖全部创建点） |
| CE-3 | medium（latent 崩溃） | `NFA.doProcess` stop-state discard 循环 `releaseNode(state.getPreviousBufferEntry(), ...)` **无 null 守卫**（对照同族 :749-753 有守卫）：重加的 start state（previousBufferEntry == null）与 stop state 同批时 `releaseNode(null,…)` → Guava `getIfPresent(null)` NPE。经可达性分析：公共 Pattern API 的形状校验（Pattern.java:330-363 拒绝 optional 后 not*、Quantifier:122-125 拒绝 optional NOT）当前排除该形状，但 NFA 为 public 类、手工状态图不经编译器校验——防御性守卫消除该类缺陷（与 2300-2 同族） | NFA.java:407-411 vs :749-753 | Phase 3 修复（1 行守卫 + 注释；触发路径被公共 API 排除故无独立行为测试，回归由全集保障——沿 items 7/8 R-4/R-9 容器置换修复先例） |
| CE-4 | low（计数器损坏） | `SharedBuffer.registerEvent` 溢出守卫在碰撞循环**内**（:198-201）：eventsCount 已为 `Integer.MAX_VALUE` 且槽位空闲时循环体不执行，`:205` `eventsCount.put(timestamp, id + 1)` 静默溢出为 `Integer.MIN_VALUE`，损坏该时间戳下一枚 EventId；姊妹路径 DeweyNumber.increase 已用 long 数学加固——同族不一致 | SharedBuffer.java:196-205 | Phase 3 修复（循环后补守卫） |
| CE-5 | medium（异常层级 + 消息缺陷） | `SharedBufferAccessor.lockEvent` 用 `Guard.checkState(eventWrapper != null, "… id %s", eventId)`：抛**裸 IllegalStateException**（脱离 StreamException/NopCepErrors 体系，同文件其余站点均 typed）且 `Guard.checkState` 不插值 `%s`——发布消息含字面 `%s` | SharedBufferAccessor.java:329 | Phase 3 修复（typed StreamException + 插值 eventId） |
| CE-6 | low（log-and-throw 双重报告） | `SharedBuffer.hasEventInBuffer` catch 块 `LOG.error(...)` 后立即 throw typed StreamException（cause + eventId 上下文齐全）——同一失败双重报告，违反单一可观测通道 | SharedBuffer.java:216-224 | Phase 3 修复（去 LOG.error，注释说明） |
| CE-7 | low（零引用死代码，05-20 方法论） | `SharedBuffer` 6 个统计 getter（getEventCounters/getEventsBufferHitCount/getEventsBufferMissCount/getEntryCacheHitCount/getEntryCacheMissCount/getEntryCacheEvictionCount）全仓（main+test，排除自身）**零引用**（`logCacheStatistics:247-257` 直读 stats 不经 getter；被引用的 getEventsBufferCacheSize/getEventsBufferEvictionCount/getEventsBufferSize 保留） | SharedBuffer.java:376-433 | Phase 3 删除（编译 + 全集回归验证） |
| CE-8 | low（文档漂移 6 处，bundle） | a) Accessor 类 Javadoc 声称 "Operations are persisted only after closing the Accessor"——与 write-through 事实矛盾（flushCache 方法 Javadoc 已改、类级未同步）；b) `advanceTime` Javadoc 畸形实体 `&lt;&eq;`；c) Apache license header 复制损坏（"NOVICE file"/"Vhe ASF"）；d) `extractPatterns` 起点静默 vs 中途 fail-fast 的不对称策略未文档化（起点缺席为 empty-match 合法语义）；e) `PatternStreamBuilder.clean()` identity 但 Javadoc 仍描述 ClosureCleaner；`build()` 硬编码 `inputSerializer = null` 无注释说明；f) `NFA.process` Javadoc 声称 event 可 null（"or null if only pruning shall be done"）——无调用方传 null，pruning 由 advanceTime 驱动 | SharedBufferAccessor.java:43-45,58,3-5,135；PatternStreamBuilder.java:87-94,139；NFA.java:226 | Phase 3 修复（文档级；No new test required per guide Rule #25） |

**C. watch-only residual（逐条附 Why Not Blocking Closure，Allowed Deferred Classification）**：

| ID | 分类 | 发现 | Why Not Blocking Closure |
|---|---|---|---|
| W-1 | optimization candidate | Rich* 函数对（RichPatternSelectFunction/RichPatternFlatSelectFunction）+ `BooleanConditions.falseFunction()` + `NopCepConstants.VAR_EVENT/VAR_CTX` 全仓零引用（含测试） | Flink 兼容公共 API surface，删除属公共契约变更（超出本 plan「不改公共契约」小缺陷准则），零运行时成本；移除需独立 deprecation 决策 |
| W-2 | optimization candidate | functions/adaptors 四适配器两对克隆（Select vs FlatSelect 61 行仅 delegate 与一处调用差异；Timeout 对同理 open/close 管线逐行同构） | 结构性镜像无行为漂移；去重需泛型绞刑架 + 回归面，属重构优化非缺陷 |
| W-3 | optimization candidate | 跳过策略克隆家族（throwExceptionOnMiss/toString 对、getPruningId 对）+ SkipToFirstStrategy 复制父类 serialVersionUID | 语义同步无漂移（差异为有意的 max/min/first/last）；serialVersionUID 属 Serializable 契约不宜改动 |
| W-4 | optimization candidate | SharedBuffer 三对 cache-aside 样板（upsert/remove/get × Event/Entry，~50 行）；NFA isStart/Stop/Final 三段式（三个 distinct 错误码的 Flink 形态）；CepOperator onEventTime/onProcessingTime STEP1-5 排水循环近同构（while 条件不同） | 稳定结构去重收益有限；C-8 记录为未来重构输入 |
| W-5 | out-of-scope improvement | CepOperator processing-time timer 生命周期不对称：`registerProcessingTimeTimer:311-326` 无去重注册而 `deleteProcessingTimeTimer:328-330`/`forEachProcessingTimeTimer:351-353` 为空方法体（匿名 InternalTimerService 适配层） | 两接口方法全仓（main+test）零调用方——非活路径静默跳过而是死接口合规；实现 delete = 新功能（future 跟踪 + 取消）超出审计修复范围；fires 幂等排水无正确性影响 |
| W-6 | watch-only residual | GroupPattern where/or/subtype 裸 UOE 无错误码（sibling `checkIfNoGroupPattern` 用 MalformedPatternException；模型路径 `<part type="group" subType>` 可触达） | 行为被 TestErrorDiagnosticsEnhancement:103/113 钉定 + 06-30 已裁定「合法的禁止调用设计」+ Flink 同款 guard；改异常类型 = 测试钉定的契约变更 |
| W-7 | watch-only residual | times 边界校验经 `Guard.checkArgument` 抛 IAE 而非 MalformedPatternException（Pattern:465,494,531；Quantifier:201-205） | 平台标准前置条件惯用法（Guard.checkArgument 全平台使用），英文消息 + fail-fast，两级错误策略合规；迁移属 taxonomy 风格收敛，被 TestPatternValidation:129-137,326-338 钉定 |
| W-8 | watch-only residual | `DeweyNumber.addStage` 理论溢出（length==MAX_VALUE 时 NegativeArraySize；increase 已加固）；NFA 负时间戳不对称（`getStartTimestamp() >= 0` 使负 ts 起始匹配不注册窗口 timer）；`NFAState.compareDeweyNumber` 经 toString().split 的 O(digits) 比较器性能 | Flink 同源语义/退化输入类：epoch-millis 输入远离边界；改动偏离 Flink 基线行为 |
| W-9 | watch-only residual | `Lockable` heal-and-throw（负 refcount 先 set(0) 再抛，若未来 catch-and-continue 会掩盖 over-release）+ equals/hashCode 读可变 refCounter（当前不作 map key） | 当前全部调用方 rethrow（fail-fast），无掩盖发生；未来危害记录在案 |
| W-10 | watch-only residual | `SharedBufferCacheConfig.getCacheStatisticsInterval` 零引用（CepOperator 直读 NopCepConfigs）；字段经 ctor 活跃 | @DataBean 配置 bean 公共 surface，非内部 accessor；保守不动 |
| W-11 | watch-only residual | `EvalFunctionCondition:27-29` 传 null IEvalScope 进 `action.call2`——scope 依赖的模型函数会深层 NPE | 记录为使用约束（模型条件必须 scope 无关）；修复需 eval 基建接线，超出审计范围 |
| W-12 | watch-only residual | `CepPatternBuilder.buildFollow` switch 无 default（未知 FollowKind 静默返回原 pattern）；`getAfterMatchSkipStrategy` 冗余 noSkip 预初始化 | FollowKind 为封闭枚举且 case 全覆盖，缺 default 当前不可达；防御性 default 属加固候选 |
| W-13 | watch-only residual | NFACompiler windowTimes 键空间（trailing NOT_FOLLOW 检查用 raw name :189-191 vs map 填充 internal name :444/450）；trailing `notNext` 编译通过（不可观测约束）；`times(0,0)` 诊断消息报 from=1（用户传 0）；`within(null)` 静默 no-op 未文档化；SubtypeCondition 无 null 守卫；EventComparator 接口字段 serialVersionUID；4 适配器仅 1 个 @Internal | 全部为当前正确行为下的语义/诊断毛边，无已证实缺陷；记录为加固候选 |
| W-14 | watch-only residual | `CEP.pattern(input, pattern, comparator)` 3 参重载无生产调用方（测试消费）；`Pattern` 可变 builder 无「编译后勿改」线程契约文档 | Flink parity 公共 API + 单线程构建/一次性编译的实际使用模式 |

**否决项（subagent 发现经执行者复核不采信为缺陷）**：

| 发现 | 否决理由 |
|---|---|
| 「NopCepConfigs.COMMON_HINT 为 stale Flink-era 文档（'state.backend' rocksdb 描述不适用）」 | **事实错误**：nop-stream 自有 `nop-stream-rocksdb` 模块（RocksDBStateBackend live，roadmap Framework reuse 表），hint 描述的「cache 溢出落 rocksdb state」语义对本平台**准确**，非缺陷 |
| 「SharedBuffer valueSerializer 参数未用属死管线」 | 参数为公共构造签名（Flink parity）保留；根因已由 CE-8e 注释固化（CepOperator:222 同款注释镜像），非行为缺陷 |

### 2.3 空壳/静默跳过扫描（hollow scan）

- 扫描命令与退出码：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-cep --severity high` → **0 critical / 0 high，exit 0**（item 7 修正后的消息语义分级版本）。
- 人工补审多行 UOE（行级正则盲区，沿 items 7/8 基线）：`RichIterativeCondition.getIterationRuntimeContext`（:61-64，guard 消息 "Not support to get…"）为合法显式拒绝；`GroupPattern.where/or/subtype`（单行，06-30 已裁定）。另人工核验空方法体模式：唯一命中为 CepOperator 匿名 InternalTimerService 的 `deleteProcessingTimeTimer`/`forEachProcessingTimeTimer`（W-5，零调用方死接口合规，非正常实现路径的静默跳过）。
- **结论：无 high/critical 真实发现，无误报需修正工具，无需处置动作**。

### 2.4 测试覆盖抽查

**主要包代表性测试（每包 ≥2 命中确认）：**

| 包 | 代表性测试 | 测试文件数 |
|---|---|---|
| nfa | TestNFA、TestNFAExtended（含 2300-2 E2E :641）、TestDeweyNumber、TestGreedy、TestNFAWindowTimeout、TestNFAWindowTimesAccessor、TestNotPattern、TestWatermarkStateRobustness + aftermatch/TestAfterMatchSkipStrategies | 10 + aftermatch 子包 |
| nfa/sharedbuffer | TestSharedBuffer、TestSharedBufferExtended（含 2300-2 回归 :311）、TestSharedBufferCache、TestSharedBufferCacheConsistency、TestSharedBufferFlushCache、TestLockable、TestLockableOverRelease、TestCepReleaseSymmetryInvariant | 8 |
| nfa/compiler | TestNFACompiler、TestNFACompilerExtended、TestNFAStateNameHandler | 3 |
| pattern | TestPatternValidation、TestErrorDiagnosticsEnhancement、TestMalformedPatternException + conditions/ 子包 | 4 + conditions |
| operator | TestCepOperatorBasic、TestCepOperatorStateRecovery（06-30 §3.3 点名）、TestCepOperatorStateBackendWiring、TestCepCheckpointRestoreE2E、TestCepOperatorMultiKeyWatermark、TestCepSkipStrategyE2E 等 | 16 |
| functions | **无专属测试目录——按缺口记录处理（plan 预告的既知缺口，非抽查失败）**：间接覆盖存在（PatternStream.select/flatSelect 经 TestPatternStreamBuilder 与 adaptors 消费路径；RichIterativeCondition.open 生命周期经 TestCepOperatorConditionLifecycle） | 0 |

**行数 top-5 文件直接覆盖**（`wc -l` 排序）：

| 文件（行数） | 直接测试 |
|---|---|
| NFACompiler（1109） | TestNFACompiler / TestNFACompilerExtended / TestNFAStateNameHandler |
| CepOperator（1016） | operator 包 16 测试文件（Basic/Timeout/StateRecovery/CheckpointRestore/MultiKeyWatermark/DanglingCleanup 等） |
| NFA（958） | TestNFA / TestNFAExtended / TestGreedy / TestNFAWindowTimeout / TestNotPattern 等 10 文件 |
| Pattern（721） | TestPattern（root）+ TestPatternValidation（34 @Test）+ TestErrorDiagnosticsEnhancement |
| SharedBuffer（454） | sharedbuffer 包 8 测试文件 |

**结论**：主要包与 top-5 文件覆盖充分；唯二缺口为 functions 包无专属测试（间接覆盖存在，缺口记录）与 cep 15 个 test 文件通配符导入（路由 item 22）。

### 2.5 `_gen` 生成纪律核验

- 4 个 `_gen` 文件 live：`model/_gen/{_CepPatternModel,_CepPatternGroupModel,_CepPatternPartModel,_CepPatternSingleModel}.java`。
- 派生关系存在：每个文件头注释披露 `generate from /nop/schema/stream/pattern.xdef`；模型源 live 存在于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef`（+ stream.xdef/resource-spec.xdef 同目录）。
- 无手改痕迹（等效证据）：git 历史（`git log --oneline --all -- ".../model/_gen/*.java"`，10 commits）全部为 codegen 主题提交（「重新生成代码」「根据xdsl生成代码时不记录行号…」及引入提交），无人工语义编辑；文件体含标准 CPD-OFF/PMD 抑制标记与生成器惯用结构。
- 模块内无 `.xmeta`（`find` 零命中，与 plan Current Baseline 注记一致）。
- **结论：生成纪律成立，无需处置。**

## Phase 3 — 缺陷处置与收口

### 3.1 小缺陷就地修复（行为修复配 focused 测试；全部限 cep 模块内、不改跨模块公共契约）

| ID | 修复 | focused 测试（验证的新行为） |
|---|---|---|
| CE-0a | **前置会话断点测试修复**：`TestCepPublicApiE2E.testBranchingFollowedByAnyReleasesAllSharedBufferEntries` 在任何 watermark 之前断言 matches（事件时间模式元素缓冲至 watermark 推进——构造性必然失败）且远水位 100000 恰在 within(100s) 边界内（partials@ts1 需 >100001 才超时）使终态断言不可达。修复：断言前补 `processWatermark(10)` 驱动排水；远水位改 10_000_000 确保全部 partial 超时；断言强化为**恰好 2 个匹配**（两条 followedByAny 分支 (a,b@2,c)/(a,b@3,c)）。主代码经 TestScratchDebug 逐 watermark 诊断确认正确（a,b,b,c → 2 matches，nodes=0/events=0 全释放） | 该测试自身即为验证 releaseNode repeat-visit 语义 + SharedBuffer 全释放的 E2E（修复后 5/5 绿，含分支计数与零残留双断言） |
| CE-0b | **TestScratchDebug.java 删除**（前置会话自声明 "TEMPORARY debug test - delete before commit"，2 用例为调试打印壳） | 零引用删除，编译即验证 |
| CE-0c | **invariant 注册表行号重钉**：前置会话 CepOperator 重构（resetNfaStateIfFullyTimedOut 抽取）使 emission 点 568→571、960→937 漂移，`check-nop-stream-invariants` V4/V5 4 项违规；按注册表 `updated` 字段记载的 re-pin 流程同步（emission 语义不变，tagForm 行注同步 :141→:153/:770→:930） | `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（重跑验证） |
| CE-1 | Pattern 内层 windowTime 非正值 fail-fast：新增 `checkInnerWindowTime` helper（mirror within() 的加固措辞），接入 `oneOrMore/times(int,Duration)/times(int,int,Duration)/timesOrMore` 4 站点；null（无内层窗口）与正值不受影响；模型路径 `CepPatternBuilder` 经同一 Pattern 方法自动获得防护 | **新增** `TestPatternAuditFixes`（8 用例：5 个非正值变体断言 MalformedPatternException + ARG_PATTERN_DETAIL 含方法名/模式名、null 与正值 7 组合放行、begin/followedBy null name 双站点）。**fix-revert 验证**：临时禁用守卫后 5/8 用例 FAIL，证实非空壳 |
| CE-2 | Pattern ctor null name fail-fast（MalformedPatternException + "name must not be null"，覆盖 begin/next/followedBy 等全部创建站点） | TestPatternAuditFixes.testBeginNullNameFailsFast / testFollowedByNullNameFailsFast（**新增**） |
| CE-3 | NFA.doProcess discard 循环补 null 守卫（mirror :749-753 同族写法 + 注释说明可达性分析） | 防御性守卫：触发形状被 Pattern API 校验排除、无法经公共 API 构造行为测试场景（No dedicated trigger test feasible——已在代码注释与本表说明）；回归由 cep 全集 359 测试（含 notFollowedBy/notNext/skip 策略家族全路径）保障 |
| CE-4 | `registerEvent` 碰撞循环后补溢出守卫（counter 已在 MAX_VALUE 且槽位空闲时 fail-fast，消除静默溢出到 MIN_VALUE） | **新增** TestSharedBufferAuditFixes.testEventIdCounterOverflowFailsFast（MemoryKeyedStateBackend 同名状态预置 MAX_VALUE → registerEvent 断言 StreamException + overflow 上下文） |
| CE-5 | `lockEvent` 裸 ISE（Guard.checkState + 字面 %s）→ typed StreamException（ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED + 插值 eventId） | **新增** TestSharedBufferAuditFixes.testLockEventMissingEventFailsFastTyped（双 buffer 实例 + 背后删状态构造 cache-miss×state-miss，断言 StreamException 类型 + detail 含 "non-existent event" 与 eventId） |
| CE-6 | `hasEventInBuffer` 去 log-and-throw 双重报告（typed 异常携带 cause+上下文，注释说明单一可观测通道） | 纯日志级清理（行为=异常路径不变），既有 TestSharedBuffer 家族回归（No new test required per Rule #25） |
| CE-7 | 删除 6 个零引用统计 getter（保留被 logCacheStatistics/测试消费的 3 个） | 零引用删除，编译 + 全集回归验证（No new test required） |
| CE-8 | 文档漂移 6 处修正（a-f，见 §2.2 B 表）：Accessor 类 Javadoc write-through 事实、`&lt;=`、license header NOTICE/The、extractPatterns 不对称策略注释、clean()/inputSerializer 注释、NFA.process null 契约 | 纯文档级（无行为变化），编译验证（No new test required per Rule #25） |

### 3.2 大缺陷处置裁定：**无大缺陷**（显式记录）

全部采信发现均满足小缺陷判定准则（修复限于 cep 模块内、不改公共契约、无需新测试基建）；W-1..W-14 watch-only residual 逐条附 Why Not Blocking（§2.2 C 表，均属 optimization candidate / out-of-scope improvement / watch-only residual 三类合法延期分类）。**roadmap 无 Follow-up 工作项追加**（既有 item 22 承接 cep test 通配符 sweep，编号无顺延）。

### 3.3 回归验证（2026-09-01 执行记录）

- 修复前基线：`./mvnw test -pl nop-stream -am -T 1C` → cep 351 测试 1 失败（断点测试 CE-0a，主代码经诊断验证正确）；其余全模块绿。
- 修复后：`./mvnw test -pl nop-stream -am -T 1C` → **BUILD SUCCESS，10 模块全绿**（core 178+868 runtime / rocksdb / connector×4 / **cep 359/0**（+10 新用例 −2 scratch 调试用例）/ flow / fraud-example；runtime 868 中 9 skipped 为 gated 多 JVM）；EXIT=0。
- `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS（编译含 checkstyle 阶段）。
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-cep --severity high` → exit 0（修复后复跑）。
- `node ai-dev/tools/check-nop-stream-invariants.mjs` → exit 0（CE-0c 重钉后）。
- `node ai-dev/tools/check-doc-links.mjs --strict` → exit 0（本报告新增后复跑，见 closure 记录）。
- **端到端验证**（plan Phase 3 要求：触及 CEP 匹配/状态语义的修复——CE-3 NFA release 守卫 + CE-0a E2E）：`CEP.pattern()` 公共 API → 匹配输出完整路径由 TestCepPublicApiE2E 5 用例承载（含修复后的 branching E2E：CEP 公共 API 入口 → followedByAny 分支 → 2 匹配输出 → 远水位后 SharedBuffer 零残留双断言）；TestCepProductionExecutionE2E / TestCepSkipStrategyE2E / TestCepNonKeyedEntryE2E 家族随全集绿。SharedBuffer 相关修复（CE-4/CE-5）不破坏既有 CEP 测试全集（359/0）。
- Owner-doc 裁定：`No owner-doc update required`——全部修复为 cep 模块内部实现收敛与 fail-fast 加固（CE-1/CE-2 为**新增**前置校验，不改任何合法输入的行为契约）；`ai-dev/design/nop-stream/cep-design.md` 描述的 NFA/SharedBuffer/模式编译契约无事实漂移（write-through 语义本就是 live 行为，CE-8a 仅修正代码内 stale Javadoc）。

## Conclusion

- **历史审计收口**：05-20 §2 cep 主体侧三项全收口（CepOperator 生产接线完备、state 初始化已修复、与已删 runtime 侧重复归零——runtime 侧引用 item 8 §1.1 + 抽查）；06-30 cep 相关 10 项核对（9 landed/unchanged + 1 partial 路由 item 22，无 regressed）；2300-2 cep 侧修复点三点全 live 复核成立（+ 同族路径复查无第二违例）；2300-1/3 无 cep 侧修复点确认。
- **产品化审计**：D-GAP item 9「无额外重点」结论显式勾销无遗漏；双 subagent 核心路径审计 + 执行者复核采信 8 项缺陷（1 high 静默语义禁用 + 4 medium + 3 low）全部就地修复（4 项行为修复配 10 个新 focused 用例 + fix-revert 验证；1 项防御守卫按 items 7/8 先例以全集回归保障）；14 组 watch-only residual 逐条附 non-blocking 理由；2 项 subagent 发现经复核否决（含 1 项事实错误）；hollow scan exit 0 + 多行 UOE/空方法体人工补审干净；测试覆盖抽查充分（唯 functions 包缺口按缺口记录）；`_gen` 生成纪律核验成立。
- **执行环境收口**：前置会话断点三件套（遗留失败测试、scratch 调试文件、invariant 注册表漂移）全部就地修复，未提交改动波采纳为基线并随本报告统一收口。
- **修复与收口**：无大缺陷、无 Follow-up 追加（显式记录）；全模块回归绿 + 三工具门禁 exit 0 + doc-links exit 0。
- **被否决的方案**：回退前置会话未提交改动（否决：该波改动 350/351 绿且与本 plan 修复互不冲突，回退将丢失已验证的修复并重引入其覆盖的问题）；将 Rich*/falseFunction 死 API 就地删除（否决：公共契约变更超出小缺陷准则，W-1 记录）；将 GroupPattern UOE 改 MalformedPatternException（否决：测试钉定契约变更 + Flink 同款 guard，W-6 记录）；实现 CepOperator processing-time timer delete（否决：新功能超出审计修复范围且无调用方，W-5 记录）。
- **后续工作**：item 10/11 审计直接引用本报告 §1（历史收口基线）；Follow-up item 22 承接 cep 15 个 test 文件通配符 sweep；W-1..W-14 为未来重构/加固输入（无 plan-owned 遗留工作）。

## References

- `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`（历史审计原文）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7：方法论/报告结构/§2.2 Flink-Beam 裁定/§1.1 §7 空壳结论/工具消息语义分级）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md`（item 8：§1.1 runtime 侧四文件删除结论、§1.3 2300-2 runtime 侧复核）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 9 行）
- `ai-dev/plans/nop-stream-production/2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md`（cep 侧修复点定义）
- `ai-dev/plans/nop-stream-productization/2026-09-01-1457-1-cep-module-audit.md`（执行 plan）
- 双 explore subagent 审计 session：NFA/SharedBuffer（`ses_fa3363831ffeV13LhnUQ5gF7JK`）、pattern/编译链（`ses_fa3360e1cffeelAyWZT3rg0Wq6`）
