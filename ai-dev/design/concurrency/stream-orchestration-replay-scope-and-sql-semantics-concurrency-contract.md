# stream 编排 replay 语义、作用域冻结与 SQL 静态生命周期并发契约（CRA 8.45 R4-05 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立评审记录见「评审记录」节）
> Last Reviewed: 2026-11-07
> Source: plan 2538（[2538-cra-phase8.45-r4-05-replay-stream-semantics.md](../../plans/2538-cra-phase8.45-r4-05-replay-stream-semantics.md)，CRA Phase 8.45 R4-05 1.3 Replay/流语义束 G11，10 工单全 P2；头部 Draft Review R1 修订 F-1~F-5+S-1~S-3 = 裁定输入硬约束——F-3 W-02 偏离登记（裁定 C.1 落款）/F-4 红⑦ 钉形限定（裁定 E 红⑦ 行落款）/S-1 迟到失败+并发 attempt 入口复位抹除窗[MJ-1 扩写：attempt-1 emitter 失败已记录→attempt-2 经分布式 deployTask 进入（K9 不等待旧线程）→入口复位抹为 null→attempt-1 wrapper 读到 null 正常 EOS 收尾——「失败必须 FAILED 而非伪装 EOS」在竞窗内重开洞；登记 watch-only+兜底论证[REPLAYABLE 重放语义 at-least-once 保持+LOG.error 保留+窗口为分布式路径毫秒级]；被否决备选登记「去入口复位→迟到失败误杀新 run」取舍——两者择一代价已权衡，机制分支不变]边界（裁定 C.1 落款）/S-2 waiting-loser 上界（裁定 B.2 零触发落款）/S-3 家族基线落款（裁定 A.1 落款））；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-1440-deep-audit-nop-stream-orchestration-thin-concurrency/`（[merge-adjudication.md](../../audits/2026-10/2026-10-14-1440-deep-audit-nop-stream-orchestration-thin-concurrency/merge-adjudication.md) §2.1 族表 :43-46[MA-02~05] + §2.2 :53-61[六独立案] + §4.5 :150-153[C10-005/006/008] + §五 :185-187 批次建议 A/B/C）；批次定义 = batch-proposal §二.4 R4-05 行 + §一 E1–E6；既有裁定权威 = [stream-orchestration-thin-concurrency.md](stream-orchestration-thin-concurrency.md)（2512 裁定①–⑤）——**仅引用不重建**；格式与纪律先例 = [stream-connectors-concurrency-guards-failure-paths-and-resource-hygiene-concurrency-contract.md](stream-connectors-concurrency-guards-failure-paths-and-resource-hygiene-concurrency-contract.md)（2537 裁定 A–E/K 节/驱动器总表形态）
> Owners: 裁定 A（快照冻结与 eval 根作用域域：W-05 fresh-root + S-3 家族基线落款 + W-01 两落点冻结）；裁定 B（模型构建契约与目录源守卫域：W-07 build 零消耗[F-4 钉形] + W-06 typed 拒绝[S-2 零触发落款]）；裁定 C（replay 失败语义域：W-02 零父类缝[F-3 偏离登记+MJ-1 类比+S-1 边界]+C10-005 同 diff 设计+三工单联调矩阵）；裁定 D（注册表/缓存/订阅生命周期域：W-03/W-04/W-08/W-09/W-10 逐项）；裁定 E（测试缝总表：C10-008 搭车定夺[四环授权链] + 红①–红⑩ 驱动器 + 零回退钉全列 + C10 映射）。**裁定编号 = Phase 1 起草指令分组**（plan Goals 预设 A–G 的承载节在本档映射表逐项可定位；分组调整随「工单→裁定映射与分组登记」节生效）。
> 文件命名裁定：`stream-orchestration-replay-scope-and-sql-semantics-concurrency-contract.md`（取代 plan Goals/Scope/Phase 1 Targets 拟名「stream-orchestration-replay-semantics-scope-freeze-and-registry-lifecycle-concurrency-contract」形态——「replay-scope」覆盖 W-02/W-03/W-10 replay 域与 W-05 根作用域域、「sql-semantics」覆盖 W-04/W-08/W-09 SQL 静态生命周期面；命名调整已在 plan 2538 三处登记）
> 行号锚 = live 基线 `ef4094a81e`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值——plan 起草基线 `12182bcffd` → 本文基线 `ef4094a81e` 唯一 commit 为 docs(plan) 提交（plan 2538 起草+draft review R1），`git diff 12182bcffd..ef4094a81e -- nop-stream` **零 diff** 亲证（10 工单站点锚逐行抽验命中，见 K 节）；后续漂移以 live 为准并登记于 plan 勘误。与 plan 文本不一致的 live 核证事实集中登记于「plan 输入勘正（K 节）」，裁定以本文为准。

本文是 plan 2538 全部 10 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序论证）、被否决的备选、零触碰边界。三范式/既有裁定结论引用不复制——SSOT 在既有设计文档。全部裁定零产品侧新配置载体：错误码全部复用 `NopStreamErrors` 在位码（`ERR_STREAM_STATE_ERROR`/`ERR_STREAM_NULL_ARG`/`ERR_STREAM_INVALID_ARG`——flow/sql/fraud-example 三模块既有使用先例亲证）+ 既有 `IllegalStateException` 通道（`DirectoryFileSourceFunction.discoverFiles` 先例、resolver :117-119 先例），零新错误码、零 `_` 生成物、零 orm、零平台设施本体。

## 既有裁定引用核对（引用不重建——软依赖 8.18 已解除 + 同单元前批 2512 已收口 + 对齐纪律「既有裁定引用条款」授权）

2512 裁定①–⑤（[stream-orchestration-thin-concurrency.md](stream-orchestration-thin-concurrency.md) §3.1–§3.5）为本批可引用既有裁定；2511/2503/2534–2537 产物面为引用边界。本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。**三条红线落款**：resolver 三产物零改写红线、DirectoryFileSourceFunction 复位块零触碰红线、C10-006 反向钉零回退红线（见下表红线 1/2/3）。

| 既有裁定/产物 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| 2512-① resolver CHM 化+换绑 WARN+call null fail-fast | W-09 同文件基线（StreamScalarFunctionResolver :73/:56-62/:117-119） | **红线 1（resolver 三产物零改写）**：`functions` CHM（:73）、setDefault 换绑 WARN（:56-62）、call null fail-fast（:117-119）零改写零移动；W-09 的 close 解绑恰为 2512 Non-Goals 明示留给 R4-05 的边界（非重开面）；`TestStreamScalarFunctionResolver` 12 用例（含 closeIsIdempotent :121 + C10-001 三钉 :139/:204/:261）零改写 |
| 2512-② merge-max 增量恢复 | W-06 同文件基线（DirectoryFileSourceFunction :266-291/:310-321） | **红线 2（复位块+增量恢复零触碰）**：run 入口复位块 :117-123（注释+`this.cancelled=false`）零触碰——「复位先于循环首读」判据（:123 先于循环首读）在 W-06 修复重排时序时必须保持；`putAllCursors` merge-max 与 `initializeState` 增量合并零触碰；`TestDirectoryFileSourceFunction` 既有断言（C10-002 钉族）零改写 |
| 2512-④ cancelled 复位契约 | W-06 同文件联调承接（2512 就绪注记预声明 C1-003 修复与裁定④联调共验） | 复位语义=「CAS 成功后、循环首读前复位」——W-06 裁定 B.2 的 typed 拒绝抛出点在 CAS 落败分支（抛出者非 owner、永不进入复位块），与复位语义零交互；C10-004 cancel-后-重跑钉族零触碰 |
| 2512-⑤ 零回退钉清单惯例 | 裁定 E 零回退钉全列 | 五钉（C10-001×3/C10-002/C10-003[core 归属注记]/C10-004/C10-007 三探针）+ 三文件落地 diff 零改写零回退；C10-007 已随 2512 搭车消号（G91 1.3 半边）——本批零触碰 |
| 2511 C8-001 fixture 契约 | W-02/W-03/W-10 同文件零触碰锚（ReplayableCdcSourceFunction :97-137 snapshotState 并回覆写 + :294-298 metadata 契约注释 + CdcEventFixtures :287-305） | fixture 本体与 `toChangeEvent` 契约注释零改写；W-02/W-03/W-10 的 diff 分组登记禁顺带禁交叉（同文件跨批面条款） |
| 2503 生产契约（DebeziumMessageSource.subscribe :120-157） | W-10 修复对齐基准 | Set 登记+reject-if-stopped typed（NopException+detail 指引，:148-152 先例）= W-10 typed 风格的形态基准（风格对齐、载体不同——ReplayEngine 为一次性重放引擎，非事件分发源）；nop-message-debezium 批外零 diff 红线 |
| 2537 W-04（B.2 CAS typed 拒绝） | W-06 同族先例（`DebeziumCdcSourceFunction.run` 守卫 typed 形态 :262-272 区） | 形态对齐引用不复制：同一缺陷家族（守卫把检测异常翻译为静默假 EOS→typed 拒绝、所有权保持 owner、被拒线程不入 finally）；`DebeziumCdcSourceFunction` 为 2537 站点文件——**零触碰红线**（W-02 父类缝禁选申报点的依据） |
| 2537 MJ-1（pre-try 抛出复位规则） | W-02 的类比落款（裁定 C.1 优先级/suppressed 规则） | 类比引用：2537 MJ-1 = 「CAS 成功后的 pre-try 抛出先复位自身守卫」；本批类比面 = 子类 override run 的 stale 复位纪律（super.run 前清 pendingReplayError——镜像父类 :279 pendingError 入口复位与 2512-④ 复位纪律同族） |
| 2534–2537 各批裁定与钉 | 全批零触碰边界 | `*R403`（10 类）/*R404 全部批钉+新配置键全集零触碰零重开；1.2-C10-006 rocksdb 受害侧钉（8.41 受害侧钉）零触碰零重开；2534 core C3-009 产物（ProcessOperator/SerializationRoundTripCopy/FunctionUtils）零站点交集零触碰 |
| R03 范式（锁内重活外移） | W-08 两段式 | 同构投影引用不复制：`get` 前置 + 锁外 compute + `putIfAbsent` 回填的范式精神（2537 档「R03 范式」行同口径），零范式文本复制 |
| 2537 红绿判据纪律（裁定 E 全盘沿用） | 裁定 E | orTimeout 禁令/闩带 ≤30s 且收尾必放闸/`_tmp/` 总册+逐 trial 日志存证（Major-D1 灭失教训）/同号防混 `*R405` 后缀/结构钉+变异探针诚实留痕禁伪绿/恢复-探针-复修四相链断言纪律（R5 探针纪律） |

**既有裁定零重建声明**：本批零新 face token、零 SSOT diff；2512 裁定结论如需修订走其设计文档修订，不回写本批。2512 §3.1 边界注记原文（「本裁定只落并发容器化 + WARN，解绑语义归 R4-05 不代做」）= W-09 的立案授权锚。

## plan 输入勘正（live 亲读核证 @ `ef4094a81e`，裁定以本节为准）

| # | plan 2538 表述 | live 事实（file:line @ `ef4094a81e`） | 对裁定的影响 |
|---|---|---|---|
| K1 | plan 红②③⑤⑥⑧⑨⑩ 行内均插入同一段「[F-4 钉形限定] 红色观察量=build 后 model 残态断言…」文本 | 该段为 Draft Review R1 修订脚本误插——F-4 语义专属红⑦（W-07 残态断言钉形；HEAD 提交信息与 R1 修订清单口径均为「F-4 红⑦ 钉形限定」）；红②③⑤⑥⑧⑨⑩ 的红色观察量以本档裁定 E 驱动器总表为准 | F-4 硬约束在红⑦ 行兑现（残态断言腿主用+差异腿条件采用）；其余钉行不受 F-4 约束；plan 文本勘误不回写（登记于本行，归 plan 维护者随 Draft Review 回填处置） |
| K2 | MA-03 族表修复口径「修复走父类 pendingError 通道、仅成功路径 cancel」（merge-adjudication :44） | 零父类缝形态下：失败路径**保留** finally cancel（:227-231 零改写）——cancel 是 super.run wait 循环的唯一解锁机制；族表字面与零父类 diff 形态的字面偏离经 F-3 偏离登记承载（裁定 C.1） | 裁定 C.1 显式偏离登记节；族表收口条件（C10-005 失败注入 FAILED 断言 + L130 cancel 不抛锚保留）全部兑现 |
| K3 | S-3「当前形态 1 scope+1 map，增量 ≈+1 scope/+1 map」 | 逐路径实测口径：newCallScope() 现形态 = 1 EvalScopeImpl + 1 HashMap（newChildScope 内 new HashMap）；fresh-root 形态 = 1+1（计数不变）；newCallScope(bindings 非空) 现形态 = 1 scope + 0 map（EvalScopeImpl.newChildScope(Map) **adopt** childVars 为 variables——零拷贝亲证）；fresh-root 形态 = 2 scope + 1 map（fresh root + adopt child）→ 增量 +1 scope +1 map 仅在 WI11 bindings 三路径；双跳站点（keyBy/join/tap）= 2 scope + 1 map + 1 singletonMap 计数不变（与 C9-001 登记画像逐字一致） | 裁定 A.1 S-3 家族基线落款的精确分配画像 |
| K4 | W-01 落点 2「绑定隔离/overlay」的机制前提未核证 | `EvalScopeImpl.newChildScope(Map childVars)` 将 childVars **直接 adopt** 为 scope variables（无拷贝）——冻结引用直通即隔离（adopt 后每记录 scope 持同一冻结 map，写路径 UOE） | 裁定 A.2 落点 2 唯一分支 = 直接绑定冻结引用（零每记录拷贝） |
| K5 | xpl 体 event 绑定是否写入 newCallScope 产出的 scope（冻结后是否会 UOE 误伤正常路径） | `ExecutableFunction.invoke`（:146-171）将 args 绑入新建 `EvalFrame`（frame.setArg）——event 经帧参数机制传递，**不写入**传入 scope 的 variables | 冻结 bindings 不影响 event 传参路径；bindings 级写穿透=唯一 UOE 面（缺陷面本身） |
| K6 | C10-005 注入缝可行性「NopStreamOffsetBackingStore.set 公开」 | `NopStreamOffsetBackingStore.set(Map, Callback)` :165 **public** 亲证；REPLAY_INDEX_KEY :57-58 包级可见、测试同包 | 红② 注入形态成立（零产品可见性改动） |
| K7 | plan W-07「renameIds 对 XNode 树改写」计入调用方模型消耗面 | renameIds/stripAndRenameFragment 作用于 `ISqlStreamCompiler.compileTransform` 返回的 fragment XNode——`StreamSqlCompilerProvider.compileTransform`（:19-22）每调用直调 `StreamSqlCompiler.compileTransform`，**零缓存亲证**（grep cache/computeIfAbsent 零命中）——fragment 为每次编译新鲜产物，renameIds 不触调用方模型 | W-07 的调用方模型消耗面收窄为：expandSqlModel 六 setter+setSql(null)（:580-586）+ expandSqlTransformVertex 的 setTransforms/setEdges（含 `e.setFrom(exit)` 既有 edge 对象就地改写）/addSchema/addAggregator/addJoinSpec/setWindowingStrategies（:741/:773 区）；深拷贝形态下全部落在副本上 |
| K8 | 深拷贝机制可行性未定夺 | 平台原生 round-trip[MN-1 补：S7 增廉价恒等直钉腿「fixture 模型 serialize→reparse→serialize 结构恒等断言」+B.1 诚实边界声明[xdef 不可表达态的模型不受保真保障——审计复核零现成调用方]] 通道在位：`DslModelHelper.dslModelToXNode(xdefPath, model)`（model→XNode，xdef 路径经 `getXdefPath` 从 IXDslModel 解析）+ `DslModelParser().parseFromNode(node)`（XNode→model——expandSqlModel :571-577 既有同构使用亲证） | 裁定 B.1 拷贝机制唯一分支 = xdef round-trip；保真校准腿 = 既有 E2E 钉族零改写全绿 |
| K9 | plan W-06 触发路径「deployTask 槽位替换不等待旧线程退出」 | 复核修正维持（audit C1-003 复核状态行）；cancel 早退路径的缺陷窗 = [cancel 观察点→finally 复位] 微窗（run :124-136 内 return→finally 无中间语句） | 裁定 B.2 构造证明的关键输入（见 B.2 被否决备选「守卫窗窄化」的否决论证） |
| K10 | plan 基线签名（勘误 5） | 亲证维持：聚合通道已知面③⑤留痕 + sql solo 176 0F0E 0S EXIT=0 + fraud-example solo 126 0F0E 16S EXIT=0 + flow 315 0F0E（聚合通道内） | 裁定 E 零回退签名基准 = 勘误 5 签名 + 新钉增量 |
| K11 | W-03 组合基准「io.nop.commons.lang.impl.Cancellable」 | 亲证 :40-51 cancel synchronized check-and-set + :63-73 appendOnCancel（已取消态注册立即执行）/ :97-105 removeOnCancel / :107-124 tryRunCallbacks（快照+清空+逐回调 try/catch LOG.error）——实现 `io.nop.api.core.util.ICancellable`（ReplaySubscription 现即 ICancellable 实现者 :175） | 裁定 D.1 组合转发形态的契约基准；ReplaySubscription 五方法（isCancelled/getCancelReason/cancel/appendOnCancel/removeOnCancel）全部有对位转发目标 |
| K12 | 生产 typed 拒绝风格锚「:148-152」 | 亲证 `DebeziumMessageSource.subscribe` :148-152 reject-if-stopped（stopped 守卫 + NopException + detail「subscribe is rejected; create a new source」+ 指引 param）——且 Set 登记+引擎启动同在 monitor 内原子化 | 裁定 D.5 typed 门形态的声明基准（风格对齐：typed+指引 detail；载体差异如实声明——重放引擎一次性语义 vs 分发源多订阅语义） |

## 工单→裁定映射与分组登记

| 工单（finding ID） | 裁定 | 承载节 | plan Goals 预设分组对照 | 起草指令 A–E 对照 |
|---|---|---|---|---|
| W-05（1.3-C1-002） | A.1 | fresh-root 唯一合规范形态 + S-3 家族基线落款 + 漏斗零触碰声明 | plan-A 同域（W-05 半边） | 指令-A 同点位 |
| W-01（1.3-MA-02） | A.2 | 两落点缺一不闭合（materialize 冻结 + 冻结引用直通） | plan-A 同域（W-01 半边） | 指令-D 行所列 W-01 **并入本组**——分组调整声明见下 |
| W-07（1.3-C1-005） | B.1 | build 零消耗（拷贝重建唯一分支）+ F-4 钉形落款 | plan-B 同点位 | 指令-B 同点位 |
| W-06（1.3-C1-003；C10-006 反向钉接） | B.2 | typed 拒绝唯一分支 + S-2 零触发落款 + 2512 复位交互论证 + 缝形态 | plan-F 并入本组 | 指令-B 同组（S-2 片段在指令-D 行，按归属订正至本节落款） |
| W-02（1.3-MA-03；C10-005 同 diff） | C.1/C.2 | 零父类缝 + F-3 偏离登记 + MJ-1 类比 + S-1 边界 + 注入设计 | plan-E 的 W-02 半边 | 指令-C 同点位 |
| W-02/W-03/W-10 联调矩阵 | C.3 | 三工单组合行为矩阵 | plan-E 联调义务 | 指令-C 同组 |
| W-03（1.3-MA-04） | D.1 | 组合平台 Cancellable + stop 对称 | plan-E 的 W-03 半边 | 指令-D 同点位 |
| W-04（1.3-MA-05） | D.2 | 双载体声明闭合 | plan-C 同点位 | 指令-D 同点位 |
| W-08（1.3-C3-005） | D.3 | 两段式锁外 compute | plan-C 同点位 | 指令-D 同点位 |
| W-09（1.3-C6-003） | D.4 | close 解绑 + 传播语义 + E5 §3a 文案 | plan-D 同点位 | 指令-D 同点位 |
| W-10（1.3-C6-005） | D.5 | subscribed 门 typed | plan-E 的 W-10 半边 | 指令-D 同点位 |
| C10-008 搭车 + 红①–红⑩ + 零回退钉全列 + C10 映射 | E | 测试缝总表 | plan-G 同位 | 指令-E 同位 |

分组调整声明：plan Goals 预设「裁定 A–G」七组；本文按 Phase 1 起草指令并为五组（A–E）。**唯一实质点位调整 = W-01 从指令-D 行归入裁定 A**：依据 =（i）plan 同变更集绑定条款明文「W-01 与 W-05 同域绑定（newCallScope 漏斗域——同组实施）」；（ii）plan 工单表 W-01 行明文「两落点缺一不闭合（裁定 A）」——落点 2（newCallScope(bindings) 绑定面）与 W-05 的 fresh-root 改动同在 XplFunctionSupport.newCallScope 方法族，拆组将割裂「缺一不闭合」的单一收口单元；（iii）指令-D 行对 W-01 的承载要求（「按 plan 工单表逐项唯一定夺」）由 A.2 逐项兑现，无一缺席。其余为 plan A–G → 指令 A–E 的域并组（plan-B+F→指令-B、plan-E 三分→指令-C/D、plan-C+D+E 余量→指令-D、plan-G→指令-E），全部内容项一一覆盖（上表可定位）。分组调整随本登记生效；plan 侧回填随命名调整登记一并完成。**F-3/F-4/S-1/S-2/S-3 五项硬约束的承载节**：F-3→C.1 偏离登记款；F-4→E 红⑦ 行；S-1→C.1 边界声明款；S-2→B.2 零触发落款（预设形态不落 waiting-loser——若后续形态演进为等待式，须补等待上界与 interrupt 响应性声明）；S-3→A.1 家族基线落款。

## 裁定 A 快照冻结与 eval 根作用域域（W-05/W-01）

### A.1 W-05：newCallScope 每次新建根作用域（1.3-C1-002）

**决策**：**「newCallScope()/newCallScope(bindings) 每调用经 `EvalExprProvider.newEvalScope()` 新建独立根作用域，ROOT_SCOPE 静态共享字段删除」唯一分支**：

1. `newCallScope()`（:37-39）改为 `return EvalExprProvider.newEvalScope();`——每调用一个独立空根（`ROOT_SCOPE.newChildScope()` 的「共享根+子分配」被「独立根分配」等计数替换：现形态 1 EvalScopeImpl+1 HashMap → 新形态 1+1，K3 亲证口径）。
2. `newCallScope(bindings)`（:47-52）非空路径改为 `return EvalExprProvider.newEvalScope().newChildScope(bindings);`——fresh root + adopt child（K4：`newChildScope(Map)` 将 bindings 直接 adopt 为 variables，零拷贝）；null/empty 路径与 1 同形。每记录 bindings 作用域在父子链上无任何共享祖先（parent 链到独立根为止）——跨记录/跨子任务经根作用域的任何写共享点结构性消失。
3. `ROOT_SCOPE` 静态字段（:35）与其 javadoc（:30-34「A root scope retained across invocations」）删除；类 javadoc（:20-23）与 `newCallScope(bindings)` javadoc 同 diff 改写为 fresh-root 纪律表述（bindings 须为构建期冻结只读快照——与 A.2 冻结契约互引一句）。
4. **漏斗结构零触碰声明**：全部 11 处调用方[MN-4 计数单位：调用方类口径——文件 10 个/调用点 12 个（JoinSideTagFunction :53/:58 两点），路径口径见 K3]（per-record 9 wrapper：XplMap :48/XplFilter :48/XplFlatMap :51/XplSource :60/XplSink :37/XplReduce :37/EvalActionKeySelector :40/JoinSideTagFunction :53/:58/SideOutputTapOperator :76；构建期 2：AdvancedTransforms :1268/:1314）**零 diff**——单一漏斗点=单一修复点（audit 失效面枚举：全仓 grep `ROOT_SCOPE` 仅本类内部引用，零绕行者）；nop-core `EvalScopeImpl`/`EvalExprProvider` 为只读机制锚零 diff（write-guard 方案=nop-core plan-first **禁选**，条件申报③落款）。
5. XplFunctionSupport 的 diff 分组登记：本工单（newCallScope 方法族+javadoc）+ A.2 的消费面 javadoc 句（零逻辑 diff）——同文件两工单面**同组单 diff、分节登记禁顺带**。

**S-3 结论性家族基线落款（分配画像对齐已 tolerated 的 C9-001 登记画像）**：C9-001（keyBy/join/tap 每记录双跳 scope 链，P3 登记级）的 tolerated 画像 = 每记录 2 EvalScopeImpl + 1 空 HashMap + 1 singletonMap = 4 次分配（C2 复核 :245 画像/:278 口径[MN-3 订正——「不作为缺陷只作优化候选」短语实位于 :278] 逐字口径：「不作为缺陷只作优化候选」）。fresh-root 形态的逐路径画像（K3 实测口径）：无 bindings 八路径 = 1 scope+1 map（计数不变）；WI11 bindings 三路径 = 2 scope+1 map = 3 次分配（增量 +1 scope +1 map——上包络即 plan S-3 预设的「≈+1」）；双跳三站点 = 2 scope+1 map+1 singleton = 4 次分配（**计数不变**——首跳由共享根之子替换为独立根）。全部站点画像 ≤ C9-001 已 tolerated 画像上界；无新增 P2 级分配面。基准缺口 watch-only：`nop-benchmark-stream` 无 scope 链基准（C2 复核 :327/:436 在案——补基准归 R-G 测试基建轨，本批零触碰）；分配形态若在执行期观测到可回退 GC 证据 → 回裁定（plan 风险段口径），预设不触发。

**理由（触发时序论证）**：缺陷本体 = 不变量侵蚀型（audit C1-002 口径）：ROOT_SCOPE 构造后事实只读（9 调用方自律走 newChildScope、`newChildScope` 对父只读递归——audit 对平台源码逐行验证），但 `EvalScopeImpl.variables` 为普通 HashMap、`beanProvider`/`classModelLoader` 非 volatile——任何单点误用（未来包装器把根作用域直传 `body.call1`、或未来 XLang 引入父作用域赋值语义）即刻成为跨线程写共享点，损坏表现为跨记录/跨子任务绑定串值、无 fail-fast、极难归因。post-fix：根作用域生命周期 = 单次 newCallScope 调用——「误用根作用域」的暴露面从 JVM 级静态收窄为调用栈内局部对象；即便未来某调用方误将 newCallScope 产物跨记录持有，其污染半径也从「全 JVM 全算子」收敛为「该调用方自身」。并发面：`EvalExprProvider.newEvalScope()` 为无共享工厂调用（并发调用产出相互独立对象）；ROOT_SCOPE 删除后 XplFunctionSupport 无任何静态可变态残留。父链可见性：fresh root 为 `parentScope=null`，bindings child `inheritParentVars=true` 指向独立空根——读路径解析语义与现形态逐字等价（现形态父=共享空根，读结果同为空）。

**被否决的备选**：
- **write-guard 方案 b**（保留共享根 + nop-core `EvalScopeImpl` 根作用域写守卫）：触 nop-core 框架核心引擎 Protected Area（plan-first——条件申报③**禁选**落款）；且 merge-adjudication §五 :186 批次建议 B 行明示「建议优先选『每次新建根作用域』零依赖方案」；ROOT_SCOPE 语义本是空根，共享实例无对价。不采。
- **保留共享根 + 调用方纪律声明（doc-only）**：缺陷本体=安全性无机制保障（不变量侵蚀型），声明不改变单点误用的全局爆炸半径——Fix 判定不成立。不采。
- **ThreadLocal 根作用域**：引入新全局可变态（线程挂载生命周期/清理义务）大于其收益；fresh root 零全局态。不采。
- **绕过 EvalExprProvider 直构 EvalScopeImpl**：破坏平台工厂抽象（实现替换/测试替身通道）；flow 现行仅依赖 `IEvalScope`+`EvalExprProvider` 面。不采。

**零触碰边界**：nop-core `EvalScopeImpl`/`EvalExprProvider` 零 diff；11 处漏斗调用方零 diff；`TestAdvancedTransforms`/`TestSideOutputTapOperator` 等调用方面既有测试零改写（红⑤ 为新测试类追加）。

### A.2 W-01：sideInput 快照两落点冻结（1.3-MA-02）

**决策**：**「materializeSideInputs 结构化递归拷贝+只读化（落点 1）+ 冻结引用直通 newCallScope(bindings) 零拷贝（落点 2）」两落点缺一不闭合唯一分支**：

1. **落点 1（materializeSideInputs :1526-1549）**：`supplier.get()` 返回的 snapshot（:1534）经结构化冻结后入 `snapshotsByTarget`（:1543-1544 put 点为冻结落点）。冻结形态 = **递归拷贝+只读包装**：顶层 `new LinkedHashMap<>(snapshot)` 防御拷贝（脱离应用侧活引用——失效面③）后 `Collections.unmodifiableMap` 包装；值为 `Map` → 递归同形处理（拷贝+包装）；值为 `List` → `new ArrayList` 拷贝 + `Collections.unmodifiableList`；其余值原样共享。嵌套 Map/List 写路径（含 xpl 体 `sideInputs['dict'].put(k,v)`——red① 主探针面）运行期 typed `UnsupportedOperationException`（失效面①闭合）；顶层直写（`sideInputs.put`）同 UOE（失效面②的 bindings 级半边）。
2. **落点 2（消费面）**：冻结后的 bindings map 经既有 `newCallScope(bindings)` 直通（A.1 fresh-root + adopt，K4/K5 亲证机制前提）——**零每记录拷贝**（scope 内拷贝形态否决：+1 map/record 叠加 fresh-root 增量超 S-3 家族包络，且冻结已使共享 adopt 只读安全）；`XplFunctionSupport.newCallScope(bindings)` javadoc 增一句「bindings must be a build-time frozen read-only snapshot（see materializeSideInputs）」——javadoc-only 句归 A.1 同组单 diff。event 传参经 EvalFrame 帧机制（K5）不受冻结影响——正常路径零行为变化。
3. **三失效面闭合映射**：①写内层 snapshot map→跨记录污染 → 递归只读化 UOE（red① 主探针）；②bindings 直通 scope variables 写穿透 → 冻结 adopt：bindings 级写=UOE typed、body 局部赋值走帧/子作用域语义不变（K4/K5）；③应用侧线程写同一活引用→跨线程撕裂 → 顶层防御拷贝脱离应用引用（应用 post-build 改写自身 map 对快照零影响——red① detach 腿）。**结构化值域边界声明（E5 承载）**：快照值契约 = 数据型结构（Map/List/标量）；含自定义可变 POJO 的深层 setter 写面超出只读化表达力（结构化拷贝后 POJO 实例仍共享）——声明为不受支持用法，零 typed 强制（诚实边界，不虚高）。
4. 三 wrapper（XplMap/XplFilter/XplFlatMapFunction）**本体逻辑零 diff**，javadoc 同步一句（bindings 冻结语义）——触碰上限 5 文件（F-1 口径）的登记维持。
5. `StreamModelDslBuilder` diff 分组登记：materializeSideInputs 面（本工单）与 B.1 的 build 面同文件两工单**禁顺带**（面边界：:1526-1549 vs build/expand 族）；materializeSideInputs 的 null fail-fast（:1535-1542）零改写。

**理由（触发时序论证）**：缺陷时序（merge-adjudication :43 三失效面）——T1 构建期 `supplier.get()` 返回应用持有的活 Map，裸存 `snapshotsByTarget`（:1543-1544）→ T2 装配 `singletonMap("sideInputs", …)`（:1546-1548）直通每记录 xpl 作用域（K4 adopt）→ T3a xpl 体 `sideInputs['dict'].put(k,v)`：写入快照嵌套 map，**后续所有记录读到被污染的字典**（跨记录语义违约）；T3b body 未声明赋值穿透 bindings 级：裸存形态下写入共享 map（跨记录污染）/部分路径 UOE 诊断错位；T3c P≥2 序列化拷贝后各 subtask 各持裸引用，应用侧/子任务侧任一写入即撕裂（F1 形态）。owner doc WI11（user-guide :175「构建期 get() 恰一次并冻结只读快照」+:311 锚点行）声明的冻结契约零运行时强制——本裁定把声明变为机制。post-fix：T1' 快照在物化点即冻结（拷贝脱离+只读包装）→ T3a' 嵌套写 UOE typed（fail loud，诊断指向 xpl 写点）；T3c' 应用侧引用与快照脱钩。构建期一次性成本（快照为静态小字典——WI11 用法口径），非热路径。

**被否决的备选**：
- **浅冻结 only**（仅顶层 unmodifiableMap，不递归）：失效面①不闭合——red① 主探针恰为嵌套 `sideInputs['dict'].put`（plan 风险段「W-01 浅冻结的嵌套写面风险」自认该面须裁定结论）；不采。
- **全量序列化深拷贝**（2512-③ round-trip 同族手段）：对非 Serializable 快照值引入构建期 typed 失败面（行为变更面扩大）；结构化递归拷贝已闭合 Map/List 全部结构面，序列化拷贝超工单半径。不采。
- **scope 内拷贝**（`newCallScope(bindings)` 每记录 `new HashMap<>(bindings)`）：每记录 +1 map 叠加 A.1 增量（WI11 路径画像 2 scope+2 map 超 S-3 包络方向），且冻结已闭合写面——拷贝零对价。不采。
- **doc-only**（owner doc 声明「勿写 sideInputs」）：MA-02 为已确认 live 缺陷（merge-adjudication §2.1 :43），声明不闭合三失效面——Fix 判定不成立。不采。

**零触碰边界**：`XplFunctionSupport` 逻辑面零 diff（javadoc 句归 A.1 组）；materializeSideInputs 的 null fail-fast 与 validate 面（:1017 调用点）零改写；三 wrapper 本体零 diff；`TestWi11SideInputDeclaration` 零改写（红① 为新测试类追加）。

## 裁定 B 模型构建契约与目录源守卫域（W-07/W-06）

### B.1 W-07：build() 零消耗——入口拷贝重建（1.3-C1-005）

**决策**：**「build() 首行对输入模型做一次防御性深拷贝（xdef round-trip 唯一机制），expandSqlModel/expandSqlTransforms 全部作用于副本——输入模型零改写、of() 幂等可重入」唯一分支（F-4 钉形定夺：残态断言腿主用）**：

1. **拷贝机制唯一分支 = xdef round-trip**：`XNode node = DslModelHelper.dslModelToXNode(null, model);`（xdef 路径经 `getXdefPath` 从模型 IXDslSchema 解析——K8 亲证通道在位）→ `StreamModel copy = (StreamModel) new DslModelParser().parseFromNode(node);`（expandSqlModel :571-577 对编译产物既有同构使用——零新机制引入）。拷贝产物替换 builder 的工作模型（`model` 字段 :153 由 final 改非 final + build() 首行赋值副本；expand 族代码零改写——它们继续读 this.model，读到的已是副本）。
2. **消耗面清零映射（K7 收窄口径）**：expandSqlModel 六 setter+setSql(null)（:580-586）、expandSqlTransformVertex 的 setTransforms/setEdges（含 `e.setFrom(exit)` 对既有 edge 对象的就地改写）/addSchema/addAggregator/addJoinSpec/setWindowingStrategies（:741/:773 区）——全部落在副本上；renameIds/stripAndRenameFragment 作用于编译器新鲜 fragment（K7 零缓存亲证），本就不触调用方模型。输入模型的 `getSql()`/transforms/edges/schemas/aggregators/joins/windowingStrategies post-build 全部保持原值与原对象引用（F-4 残态断言的观察面）。
3. **同 builder 双 build 语义**：每次 build() 从 pristine 输入重新拷贝 → 二次 build 完整重走 expand（expandSqlModel 不早退——副本 sql != null）→ 两次产出等价 env（red⑦ 等价校准腿）；`materializeSideInputs` 每次 build 重走（supplier.get() 每次 build 恰一次——WI11「构建期 get() 恰一次」契约按 build 粒度自洽，BF-3 核证登记）。of() 幂等可重入：同一模型可 `of(model).build()` 任意次、可多 builder 共持同一模型。
4. **javadoc 契约声明**：build() javadoc 增「build() does not mutate the parsed model: all expansions run on a defensive deep copy」（E5 随批登记行为面）。
5. `StreamModelDslBuilder` diff 分组登记：本工单（model 字段+build 首行+copy helper+javadoc）与 A.2 的 materializeSideInputs 面**禁顺带**。

**理由（触发时序论证）**：缺陷时序——T1 调用方 parse 得 StreamModel（唯一持有者语义假设）→ T2 `of(model).build()`：expandSqlModel 六 setter 整体替换编译产物+`setSql(null)`（:580-586）、expandSqlTransformVertex 就地改写 edge 对象（`e.setFrom(exit)`）→ T3 调用方再次 build（或平台模型缓存/复用惯例下共持同一已解析模型）→ 基于被消耗残态（sql==null 早退/transforms 已替换/edges 已重定向）产出**静默错误**的 env——理论风险在缓存惯例下转现实（审计口径 P2）。单元内现行调用方全 fresh parse（审计复核证据——零现成复用消费方），故缺陷当前不可达但契约缺失。post-fix：build 为纯函数式消费（输入零改写）——复用消费方（现有与未来）行为确定。构建期一次性拷贝成本（非热路径——审计口径）；保真前提 = round-trip 保真，校准腿=既有 E2E 钉族零改写全绿（TestStreamModelDslBuilderE2E/TestSqlModelExpansion/TestSqlTransformExpansion——它们钉住单次 build 的产出语义，拷贝不得改变产出）。

**F-4 钉形落款**：红⑦ 的红色观察量 = **build 后 model 残态断言**（getSql()==null / transforms 整体替换 / edges 对象改写——「build() 不得消耗输入模型」契约）；二次 build 产出差异腿**不采用**——裁定 B 证得深拷贝形态下二次 build 等价（差异不存在），该腿转为 post-fix 等价校准腿（两次 build 产出断言等价）。F-4 的「差异腿仅在裁定 B 证得非等价时采用」条件分支就此关闭（证得等价）。

**被否决的备选**：
- **alreadyBuilt fail-fast**（builder 级 built 标志+二次 build typed 失败）：与 F-4 残态断言主腿不相容——fail-fast 形态下**单次 build 仍消耗输入模型**（六 setter+setSql(null) 照跑），残态断言 post-fix 不绿，须改钉形=违 F-4 硬约束；且 builder 实例级标志不防「跨 builder of(model) 复用」face（两个 builder 先后 build 同一模型仍连环消耗）；零现成复用消费方使 fail-fast 的「最小 diff」收益无对价对象（其生态候选归 plan Non-Blocking Follow-up）。不采。
- **of() 构造期拷贝**：同 builder 二次 build 仍消耗其私有副本（二次 expandSqlModel 早退面原样保留）；build()-entry 拷贝同时闭合同 builder 双 build 与跨 builder 复用两面。不采。
- **doc-only**（javadoc 声明消耗语义）：契约缺失本体在行为（C1-005 判定），且 F-4 已钉死「不得消耗」方向。不采。

**零触碰边界**：materializeSideInputs 面（A.2）零顺带；expandSqlModel/expandSqlTransformVertex 逻辑零改写（只换其读到的模型引用）；renameIds/fragment 面（K7）零 diff；`TestSqlModelExpansion`/`TestSqlTransformExpansion`/`TestStreamModelDslBuilderE2E`/`TestStreamModelDslBuilderFailFast` 零改写（红⑦ 为新测试类追加+既有 E2E 即保真校准锚）。

### B.2 W-06：runEntered CAS 落败 typed 拒绝（1.3-C1-003；C10-006 反向钉接）

**决策**：**「CAS 落败分支静默 return 改 typed 拒绝（`StreamException(ERR_STREAM_STATE_ERROR)` + detail 指引，一行语义修复——2537 W-04 同族先例形态）；守卫持有窗与复位结构零重排」唯一分支**：

1. **产品面（:114-116）**：`if (!runEntered.compareAndSet(false, true)) { return; }` 改为 `throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL, "run() re-entered while a previous run is still active — refusing silent zero-event EOS (typed rejection, recovery path owns the retry)")`。抛出点在 try 块之前：被拒线程不进入 finally（不触任何清理面）、`runEntered` 保持 true（所有权归在途 run——2537 B.2 同构论证）；抛出者非 owner，**无需任何复位动作**（2537 MJ-1 的「CAS 成功后 pre-try 抛出先复位」规则在此不适用——CAS 落败者未持有守卫）。
2. **2512 裁定④复位块交互论证（红线 2）**：复位块 :117-123 零触碰；「复位先于循环首读」（:123 先于循环首读）保持——typed 拒绝不重排 CAS 与复位块的任何时序（改动仅在落败分支）；「teardown cancel 必达」链（StreamSourceOperator.close→cancel）零触碰。
3. **C10-006 反向钉零回退红线落款**：`runEnteredGuardPreventsDoubleEmission`（:277-306）零改写——typed 拒绝形态下落败者不读取（断言 emptiness 与「并行首入落败者不得继续读取」约束全部维持、钉保持绿）；**契约变更明示（plan 对齐纪律第一条逃生条款）**：落败者从「静默 return」变「typed 失败」——C10-006 的字面预设「保持落败者静默」按其原文逃生条款（「如修复语义确需变更该契约须同步修订测试并明示」）处置：钉无需修订（钉断言=落败者零发射，typed 形态下成立），语义变更在此明示并由 E5 守卫语义句承载。C7-001 式 bounce 归因：拒绝 detail 含 re-entered 判别。
4. **S-2 waiting-loser 零触发落款**：本形态无任何等待（落败者即时 typed）——S-2 的「等待上界与 interrupt 响应性声明」义务**零触发**；若后续形态演进为 waiting-loser（如编排侧纵深候选落地），须先补该声明（前瞻落款，本批不立项）。
5. **红⑥ 缝形态裁定 = 子类 emitRemaining barrier（audit 双形态之二）**：`emitRemaining`（:158 private）可见性放宽为 protected（零逻辑改动）；测试子类覆写之，super 调用后按闩停靠——构造「attempt-1 读取完成、守卫未释放」的重叠窗，attempt-2 run() 断言 typed（green）/pre-fix 零事件正常完成（red）。「读循环退出后、finally 复位前」产品内钩子形态否决（见备选）；×100 概率 trip-wire 替代形态否决（检出力弱且非确定性——既有 `:130`/线程状态门先例均为确定性缝，本批维持确定性缝标准）。
6. **E5 守卫语义句（Phase 3 承载）**：「并行首入落败者从静默零事件完成改为 typed 失败（ERR_STREAM_STATE_ERROR）——守卫不再把检测到的并发异常翻译为假 EOS；恢复路径拥有重试权。」

**理由（触发时序论证）**：缺陷时序（复核修正版，K9）——T1 attempt-1 在读循环中观察 `cancelled=true`（:126-128 区）从循环返回，**尚未执行** finally 复位（:135）→ T2 编排层 deployTask 槽位替换不等待旧线程退出，attempt-2 立即 `run()` → CAS 落败（:114）→ **静默 return** → attempt-2 以零事件正常完成收尾 → 有界源恢复尝试提前 EOF、剩余输入静默丢失（假 EOS 截断；LOCAL 路径已被 2512 复核证伪——`restartRegion` Phase-2 waitForTerminal 阻断，残余暴露面=分布式 deployTask）。post-fix——T2' attempt-2 typed 失败 → 任务 FAILED（非假 EOS）→ 恢复语义接管（恢复路径拥有重试权——attempt-3 在 attempt-1 退出后重入成功）；attempt-1 的 finally 复位照常执行（红线 2 零交互）。并发面：CAS 原子（检测面无竞态），缺陷纯在响应语义——一行修复闭环；同族先例 = 父类 `DebeziumCdcSourceFunction` 守卫在 2537 W-04 已作同形 typed 修复（「the guard must never translate a DETECTED anomaly into a silent fake EOS」注释在位亲证）——子类与父类守卫语义家族一致。

**两契约同时满足的构造证明（plan 裁定 F 要求成文）**：契约一「重叠窗不假 EOS」——本形态下**任意**守卫持有窗内的 attempt-2 都 typed 失败（窗无关性：不依赖窗宽假设，含 [cancel 观察点→finally] 微窗与 linger[MN-2 行为变更成本登记：S2 场景 finishLingerMs 缺省 1000ms——pre-fix 该窗 bounce=正确零事件完成，post-fix 同窗 typed 失败→FAILED→恢复重试消耗恢复预算] 窗）；契约二「并行首入落败者不读取」——typed 抛出先于任何读取（:114 即抛，discoverFiles 未执行）——反向钉 emptiness 断言恒真。两契约由同一抛出点同时满足，无窗宽/时序前提。

**被否决的备选**：
- **自旋等待后继续读取**（audit 建议一的 wait-then-read 读法）：C10-006 反向约束的红条件原文即「落败者自旋等待后**继续读取**」（audit C10-006 (c) 亲证）——直接违全矩阵唯一反向钉；且触发 S-2（等待上界+interrupt 响应性义务）。不采。
- **守卫窗窄化**（读循环末提前释放/linger 外移/「锁+完成标志」区分在跑与已跑完）：构造证明不成立——缺陷本体窗 = [cancel 观察点→finally 复位]（K9），cancel 早退路径在窄化形态下该窗**不变化**（cancelled 早退与 finally 之间仍无中间语句可提前释放）；被窄化的只有非 cancel 完成路径的 linger 窗，而该窗的 bounce 不产生数据损失（游标已到尾——zero-event 完成=正确结果），非缺陷面。不采。
- **attempt/generation 身份守卫经编排层传递**：代际信号源在 nop-stream-runtime（deployTask）——跨单元改动（编排侧纵深候选已登记 Deferred，本批零触碰 `SupervisionLoop`/`TaskManager`）；源类侧 typed 拒绝已独立闭合违约路径（2537 B.2「恢复路径拥有重试权」同 doctrine）。不采。
- **维持静默 return**：缺陷本体（异常翻译为假 EOS）原样存活；且与父类同守卫的 2537 已修语义家族不一致。不采。
- **×100 概率 trip-wire 替代**：检出力弱（缺陷窗为微秒级调度窗，×100 命中期望≈0）且非确定性——违本批确定性缝标准（决策 5）。不采。

**零触碰边界**：2512 复位块（:117-123）与「复位先于循环首读」判据零触碰（红线 2）；`initializeState`/`putAllCursors` merge-max 面（2512-②）零触碰；`cancel()`（:237-240）零改写；`TestDirectoryFileSourceFunction` 全用例零改写（含反向钉 :277-306——红线 3；红⑥ 为新测试类 `*R405` 追加）；emitRemaining 可见性放宽（private→protected）为本工单缝面唯一结构改动，零逻辑 diff。

## 裁定 C replay 失败语义域（W-02 + C10-005 + 三工单联调矩阵）

### C.1 W-02：零父类缝失败重抛（1.3-MA-03；C10-005 验收前置同 diff）

**决策**：**「子类 override run(SourceContext) 包装 super.run + 子类自有 volatile pendingReplayError + super.run 返回后 typed 重抛；失败路径保留 finally cancel」唯一分支（父类 failSource 缝=条件申报④禁选落款）**：

1. **字段**：`ReplayableCdcSourceFunction` 新增 `private transient volatile Throwable pendingReplayError;`（子类自有字段——父类 pendingError :132 为 private 且 run 入口 :279 复位，不可复用；transient 对齐部署序列化面，volatile 承载 emitter→任务线程 happens-before）。
2. **记录点**：`ReplayEngine.runReplay` 的 catch-all（:225-226）在 LOG.error 之外增一行 `ReplayableCdcSourceFunction.this.pendingReplayError = e;`——LOG.error 保留（观测面零弱化）；InterruptedException 分支（:223-224）与正常完成路径**零改写**（不记录——cancel 不是失败）；finally cancel（:227-231）**零改写**（见偏离登记款）。
3. **override run 形态**：
   - 入口 stale 复位：`pendingReplayError = null;`（**MJ-1 类比落款**：2537 MJ-1 = 「CAS 成功后的 pre-try 抛出先复位自身守卫」——本形态的类比纪律 = 「super.run 前清上一 run 迟到失败位」，镜像父类 run 入口 `pendingError=null`（:279）与 2512-④ cancelled 复位的「跨 run 复用实例须入口复位生命周期态」家族纪律；region-restart 复用实例时，上一 run 的迟到失败位不得误杀新 run）。
   - `super.run(ctx)` 以 try/catch 包装：**优先级与 suppressed 规则（MJ-1 类比落款之优先级半边）**——super.run 自身抛出时**父异常为主异常原样上抛[MN-5 精确化：镜像 throwPendingError（:439-446 亲证）双分支——Exception 直抛/Throwable 包 StreamException]**（其 finally 清理与生命周期语义归父类所有），`pendingReplayError != null && != 父异常` 时 `父异常.addSuppressed(pendingReplayError)`（失败位永不静默丢弃；不反转优先级——重放失败先发生但父异常承载框架终态语义，suppressed 保全双信号）。
   - super.run 正常返回后：`Throwable e = pendingReplayError; if (e != null) { if (e instanceof Exception) throw (Exception) e; throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL, "CDC replay engine failed: " + e); }`——重抛形态**逐字镜像父类 throwPendingError（:439-446）的 Exception 直抛/Throwable 包装双分支**（家族一致）。
4. **F-3 偏离登记（族表字面 vs 零父类 diff 等价——Draft Review R1 硬约束）**：merge-adjudication MA-03 族表修复口径字面 = 「修复走父类 pendingError 通道、仅成功路径 cancel」。零父类缝形态的两处字面偏离，逐一登记：①「父类 pendingError 通道」→ 子类自有 volatile 字段+返回后重抛——**等价性论证**：族表的规范意图 = 失败必须产生任务 FAILED 终态而非伪装 EOS；零父类缝形态下失败信号经「emitter volatile 写 → cancel 解锁 wait 循环 → super.run 返回 → 子类 volatile 读 → typed 重抛」链产生同一 FAILED 终态，且不触碰 2537 站点文件与批外模块（PA 红线）；父类通道的字面使用需开父类缝（禁选申报点）或反射写 private 字段（破坏封装+:279 复位竞窗）——等价形态是唯一合规范解。②「仅成功路径 cancel」→ 失败路径**保留** finally cancel（:227-231 零改写）——**必要性论证**：cancel 是 super.run wait 循环（:351-357）的唯一解锁机制（bounded-run 语义依赖它）；失败路径若不 cancel，emitter 失败后任务线程永久滞留 wait 循环（假挂死，劣于原缺陷）；族表该字面的意图（失败不被 cancel 掩饰为正常停止）由重抛链兑现——cancel 保留但不再产生「正常完成」假象（post-fix 同一 cancel 后跟 FAILED 重抛）。族表收口条件全部兑现：C10-005 失败注入 FAILED 断言（C.2）+ `:130` cancel 不抛锚零改写 + C7-001 失败位面（失效面③）由任务 FAILED 语义承载（subscription 级 cancelReason 声明为机制归因——E5；不发明 ICancellable 常量集之外的新 reason 词表）。
5. **S-1 迟到失败边界声明（Draft Review R1 硬约束）**：子类检查是 super.run 返回时刻的快照——**run() 已返回后到达的重放失败仅 LOG 不传播**（emitter catch 的 LOG.error 为唯一观测）：teardown-cancel × emitter 失败交叠的固有残窗（外部 cancel 先解锁 wait 循环 → 子类读到 null → 正常返回 → emitter 随后失败）。声明为 watch-only 残窗：失败已被记录（LOG）且任务已在终止流程中，强传播需 cancel-后-join 语义（编排面，超工单半径）；C10-008 的 emitter 有界终止半边覆盖「终会退出」面（裁定 E），不覆盖「退出后传播」面——**禁造空壳断言**（2512 勘误 4 纪律）。
6. **三线程协调窗口论证（红② 构造基础）**：T0 测试主线程 run 前经 `REPLAY_INDEX_KEY`（:57-58 包级缝）+ `NopStreamOffsetBackingStore.set`（public :165）注入非数字 replay index → T1 emitter 线程 `readReplayIndex` `Long.parseLong` NFE（:244）→ catch 记录 pendingReplayError（volatile 写，:225-226+记录点）→ finally `ReplayableCdcSourceFunction.this.cancel()`（:227-231）→ 父类 cancel：`running=false`（volatile 写）+latch countDown → T2 任务线程 wait 循环（:351-357）读 running=false 退出 → pendingError==null → run 正常返回 → 子类 volatile 读 pendingReplayError（happens-before：T1 程序序[记录写→cancel 内 running 写] × T2 读序[running 读→子类读]）→ typed 重抛 → 任务 FAILED。**红② 确定性依据**：注入先于 run 存在 → T1 必在首事件前失败（:209 为 runReplay 首语句）→ 落在 S-1 残窗之外 → 零时序依赖。
7. **diff 分组登记**：ReplayableCdcSourceFunction 三工单面（W-02 失败通道面 ：207-232+override run+字段 vs W-03 订阅注册表面 ：175-205+stop :255-258 vs W-10 subscribe 入口面 ：167-173）**禁顺带禁交叉**；snapshotState 并回面（:97-137）与 metadata 契约（:294-298）零触碰。

**被否决的备选**：
- **父类 protected failSource 缝**：触 `DebeziumCdcSourceFunction`（2537 已修站点文件+批外模块 connector-debezium）——条件申报④**禁选**落款（plan Non-Goals/PA 表行 2 双重明文）。不采。
- **族表字面「仅成功路径 cancel」**：失败路径不 cancel → super.run wait 循环无解锁机制 → 任务线程假挂死（劣于原缺陷）；F-3 偏离登记款承载等价形态。不采。
- **反射写父类 pendingError**：private 字段破坏封装；父 run 入口 :279 复位构成写-复位竞窗；零父类缝纪律自缚。不采。
- **失败位挂 ReplaySubscription（而非子类字段）**：订阅与失败通道生命周期错位（subscription 是 parent finally 取消的对象，失败位须活过 super.run 返回时刻）；子类字段为最小正确载体。不采。

**零触碰边界**：`DebeziumCdcSourceFunction` 全文件零 diff（红线——2537 站点文件）；`readReplayIndex`/`writeReplayIndex` 本体与 `REPLAY_INDEX_KEY` 可见性零改动；`:130 assertNull("interrupted run must not throw")` 零改写（merge-adjudication :150 收口要求）；`TestReplayableCdcSourceFunction` 既有 5 用例零改写（红② 为新测试类追加）。

### C.2 C10-005 同 diff 注入用例设计（验收前置——批次行增补明文，禁拆分）

- **注入形态**：测试（同包）run 前经 `ReplayableCdcSourceFunction.REPLAY_INDEX_KEY` 写入非数字 bytes（`"not-a-number"`）到 `NopStreamOffsetBackingStore`（`set` public :165 直调——零产品可见性改动）→ runner 线程 `run(ctx)`。
- **断言面**：`run()` 以 Throwable 收尾（FAILED 语义——typed 实测）；**不呈现「部分事件+正常返回」**（本构造下零事件+抛出双断言）；`:130` cancel 不抛锚零改写并列（兼容锚）。
- **消号理由登记**：无（本例为新建非改写——`TestReplayableCdcSourceFunction` 既有用例无一钉失败路径，C10-005 复核「双向无检出力」口径的闭合=新增失败腿）。
- 测试类归属 CRA 8.45 R4-05（`*R405` 后缀+类注释）。

### C.3 三工单组合行为联调矩阵（组 C 收口门——单工单钉红不足证组合语义）

| 组合面 | 断言（载体：红②红③红⑩+C10-008 emitter pin） |
|---|---|
| 失败与 cancel 并发 | 失败注入 + 立即外部 cancel → 任务终态=FAILED（重抛优先于 cancel 语义——失败位先记录）+ emitter 有界退出（C10-008 pin 并列） |
| replay 失败后 subscription 状态 | 失败→finally cancel（reason=机制归因 kill）→ delegate cancelled 恒真；此后二次 subscribe → typed 拒绝（W-10 门为 engine 级一次性——失败后仍生效） |
| double-subscribe 拒绝后首订阅存活 | 第二 subscribe typed → 第一 emitter 未受扰跑完 + writeReplayIndex 单写者（互踩面结构性消失） |
| stop() 与外部 cancel 双路径 | stop() 先触发回调（D.1 对称化）→ 后续 parent finally `subscription.cancel()` 幂等——回调恰一次（delegate check-and-set） |
| 正常完成路径 | runReplay 正常完 → finally cancel → 任务正常 EOS（FINISHED）——`:130` cancel 不抛锚并列（cancel 语义=停止而非失败） |

## 裁定 D 注册表/缓存/订阅生命周期域（W-03/W-04/W-08/W-09/W-10）

### D.1 W-03：ReplaySubscription 组合平台 Cancellable + stop 对称（1.3-MA-04）

**决策**：**「`private final Cancellable delegate = new Cancellable();` 五方法转发 + engine cancelled 位保持 + stop() 对称驱动 delegate」唯一分支**：

1. **组合转发**（`ReplaySubscription` :175-205 内部）：`isCancelled()→delegate.isCancelled()`；`getCancelReason()→delegate.getCancelReason()`；`cancel(reason)→{ ReplayEngine.this.cancelled = true; delegate.cancel(reason); }`（engine 循环观察位保持——emitter 退出机制零变化）；`appendOnCancel(t)→delegate.appendOnCancel(t)`；`removeOnCancel(t)→delegate.removeOnCancel(t)`。平台 Cancellable 免费承载三重偏离的全部修复：cancel check-and-set 幂等（:40-51）、回调列表 synchronized（:63-73/:97-105）、tryRunCallbacks 快照+逐回调 try/catch 隔离（:107-124）。**平台类使用非平台类修改**（nop-commons 零 diff——PA 表行 3 口径）。
2. **stop() 对称（:255-258）**：`synchronized stop()` 保持 monitor 纪律，体改为 `{ cancelled = true; ReplaySubscription sub = this.subscriptionRef; if (sub != null) sub.cancel(ICancellable.CANCEL_REASON_STOP); }`——`subscriptionRef` 为 W-10 门引入的 engine 级单订阅引用（D.5）——注册/触发不对称面（MA-04 失效面④）闭合：stop 与外部 cancel 两路径同驱 delegate，回调恰一次。**行为变更面如实登记**：当前生产接线 `appendOnCancel` 零调用方（audit grep 证据）——stop 对称化的行为变化当前不可达，契约面定型为主（audit 预设登记口径维持）。
3. **getCancelReason 语义变化声明（E5）**：现形态恒返 `CANCEL_REASON_STOP`（cancelled 时）；post-fix 返回 delegate 实际 reason（parent finally 路径=`kill`、stop()=stop）。C7-001 失效面③的归因缺口由任务 FAILED 语义承载（C.1 偏离登记款）；cancelReason 定位=机制归因（声明面，非失败位）。
4. **C10-008 emitter 有界终止半边随本裁定定型落地**（四环授权链见裁定 E）：delegate 化后全部取消路径（parent finally/stop/直接 cancel）同驱 engine cancelled → emitter 循环（:211 检查）退出 → 有界终止 pin 的机制面定型。

**理由（触发时序论证）**：缺陷时序（MA-04 三重偏离+C3-004 吸收面）——T1/T2 双线程并发 `cancel(reason)`：裸实现 :188-194 全量重跑 `onCancelTasks`（非幂等——回调双跑×2）；回调列表裸 ArrayList（:176）跨线程 add×iterate 无同步（CME/漏执行）；回调一抛异常即中断循环（无逐回调隔离——第二回调不执行）；T3 `stop()`（:255-258）只置 engine cancelled 绕过注册表（注册者永不被 stop 触发）。post-fix：并发 cancel 仅首调执行回调（check-and-set）；列表操作同步；单回调异常 LOG 后其余照跑（:117-121 隔离）；stop 与 cancel 同驱。锁序面核证（BF-1）：engine monitor（stop synchronized）→ delegate monitor 单向，无反向路径——零死锁面。

**被否决的备选**：
- **最小三改**（手工幂等门/CopyOnWriteArrayList/逐回调 try-catch）：重新实现平台设施（`Cancellable` 同语义已在位）——维护双源无对价；audit 预设建议即组合形态。不采。
- **stop 维持绕过注册表**：MA-04 失效面④（工单明文「stop 绕过注册表」）。不采。
- **发明失败 reason 常量挂 C7-001**：ICancellable 词表五常量在位，新增词表超契约面；失败归因已由 FAILED 语义承载。不采。

**零触碰边界**：nop-commons `Cancellable` 零 diff；engine 循环/`readReplayIndex`/`writeReplayIndex`/snapshotState 并回面零触碰；`TestReplayableCdcSourceFunction` 既有用例零改写（红③ 为新测试类追加）。

### D.2 W-04：双载体静态缓存包络声明闭合（1.3-MA-05）

**决策**：**「双载体包络以声明闭合（merge-adjudication :46/:83 张力消解口径——『声明拒绝条件或上界』双口径同满足）：GETTER_CACHE 有界性 javadoc 声明 + SqlRowAggregateOps.CACHE 拒绝条件精确化」唯一分支（结构钉诚实留痕——红④ 结构红形态，2537 红⑥⑪ 先例）**：

1. **GETTER_CACHE（:36-37）**：类 javadoc 增有界性声明（对齐 SqlRowAggregateOps.CACHE 同款式）：「条目宇宙 = SQL 编译期解析到 getter 的 record 类集（每类一条，仅进不出）；生命周期 = JVM/类加载器域；拒绝条件 = 热重载 classloader 场景的陈旧类条目滞留不在支持范围（动态 redeploy 场景声明不支持）」。
2. **SqlRowAggregateOps.CACHE（:27-28，:21-23 javadoc 精确化）**：「per compiled pipeline 一条」声明的成立前提成文：「键 = 编译产物内嵌的 spec JSON 文本（每条编译管线常量）；拒绝条件 = 程序化构造无限互异 spec JSON 文本的调用形态不在支持范围（键宇宙随互异 JSON 无上界）」。
3. **验证双面**（一工单双载体——批次行明文）：双载体声明结构钉各自独立断言（红④ 双 leg）；校准腿 = 同文件 W-08 两段式 round-trip 功能绿（D.3）。
4. **W-04/W-08 同文件联动归裁定（同组单 diff 登记）**：两工单在 `RecordColumnAccess` 的改动面 = javadoc（本工单）+ getterFor 结构（D.3）——**同组单 diff、分节登记禁拆**（plan 同变更集绑定条款「如两改同块则同组单 diff 登记」采同组单 diff 形态）。

**理由（触发时序论证）**：MA-05 双载体——GETTER_CACHE 键=Class（:84 computeIfAbsent 唯一 put 侧，全文件无 remove/evict/上限）：键宇宙静态有限（编译期解析面）时天然有界；动态类族/redeploy 场景陈旧条目经 static CHM 强引用钉住 classloader（泄漏面）。SqlRowAggregateOps.CACHE 键=spec JSON 文本：编译管线常量前提下有界（javadoc :21-23 已声明包络），程序化互异 JSON 构造下无界。裁定取声明闭合而非机制上界：键宇宙在**声明用法域内**均有界——缺陷=「成立前提未成文」（C3 维度判包络声明缺口口径）而非有界用法下的行为错误；机制逐出（弱引用/上限+随机逐出）在声明用法域内零收益且引入逐出正确性风险。**诚实登记**：本工单为声明面修复（零行为 diff）——红④ 依 2537 红⑥⑪ 先例以结构红诚实留痕（禁伪绿禁行为假红），非行为钉。

**被否决的备选**：
- **弱引用键设施挂接**：JDK 无 weak-key CHM；平台弱引用缓存设施挂接=设施面改动（plan Non-Blocking Follow-up 明示归 R-G/后续优化轮——本批零触碰）。不采。
- **条目上限+逐出**：随机/LRU 逐出在声明用法域内零收益，且逐出策略引入热条目误逐风险——劣于诚实声明。不采。
- **doc-only 归 R-G**：MA-05 为批次行 in-scope 已确认缺口（merge-adjudication :46/:83 裁定收口=声明），deferred=违 Non-Degradable 纪律。不采。

**零触碰边界**：`GETTER_CACHE`/`CACHE` 字段与 `function()` 逻辑零 diff（本工单纯声明面）；`TestStreamSqlAggregations`/`TestWi9AggregateCatalog` 零改写。

### D.3 W-08：getterFor 两段式锁外 compute（1.3-C3-005）

**决策**：**「先 get → miss 锁外 collectGetters → putIfAbsent 回填（重复计算幂等允许）」唯一分支**（R03 锁内重活外移范式同构投影——2537 档 R03 行同口径引用不复制）：

1. **形态**：`getterFor`（:82-86）改两段——`Map<String, Method> getters = GETTER_CACHE.get(clazz); if (getters == null) { getters = collectGetters(clazz); Map<String, Method> existing = GETTER_CACHE.putIfAbsent(clazz, getters); if (existing != null) getters = existing; } return getters.get(column);`——`computeIfAbsent` 方法引用回调（:84）消除：反射内省（collectGetters :91 `getDeclaredMethods` 的参数/返回类型懒加载触发面）在 **CHM bin 锁外**执行，类加载锁倒置（JDK-8161372 同族）触发条件结构性消失。
2. **幂等论证**：并发 miss 双方各自锁外 collect（重复内省，结果确定性等价——同一类的 public 无参 getter 集唯一）、putIfAbsent 单胜者、败者采纳胜者条目——重复计算成本=首触窗口一次额外内省（类集合有界——MA-05 口径互证），热路径零变化（命中路径单次 get）。
3. **与 D.2 同组单 diff**（决策 D.2.4）：getterFor 结构面（本工单）+ 类 javadoc（D.2）同 commit 分节登记。
4. **红⑧ 承载**：结构钉（源文本断言：getterFor 无 computeIfAbsent 回调形态）+ **变异探针**（还原 computeIfAbsent → 结构钉红 → 复修复 → 绿——四相链留痕；恢复后 grep 断言目标可执行行真实存在——R5 探针纪律）+ `TestStreamSqlExprCompiler` round-trip 功能绿并列。锁倒置死锁非确定性——**禁行为伪红**（2537 红⑦ 同口径）。

**理由（触发时序论证）**：缺陷时序——T1 线程 `getterFor` miss → `computeIfAbsent` 持 CHM bin 锁执行 `collectGetters` → :91 `getDeclaredMethods` 触发参数/返回类型懒加载 → 申请类加载锁；T2 并发线程持类加载锁（如另一路径在做类初始化/反射解析）申请同 bin 的 CHM 锁 → 锁序倒置 → 死锁挂起（理论——审计口径 P2「理论」）。post-fix：collectGetters 无锁执行 → 类加载锁申请不持 CHM bin 锁 → 倒置环不成立。并发面：putIfAbsent 原子；命中路径（热路径）由 computeIfAbsent 的一次 hash+maybe-callback 变纯 get——零劣化。

**被否决的备选**：
- **保留 computeIfAbsent + collectGetters 去反射化**（缓存 MethodHandles 等）：缺陷本体=锁内副作用位置（任何锁内懒加载触发面），非反射形式本身；外移才是根因闭合。不采。
- **synchronized 全方法**：逐记录热路径锁争用（CHM 无锁读为现行优点——C2 抽查热路径清单口径）。不采。
- **per-class 双检锁**：比两段式多同步原语零收益。不采。

**零触碰边界**：`collectGetters` 本体（:88-111）零改写；`GETTER_CACHE` 字段零 diff（D.2 声明面除外）；`TestStreamSqlExprCompiler`/`TestStreamSqlAggregations` 零改写（红⑧ 为新测试类追加）。

### D.4 W-09：close 解绑 + 替换不传播（1.3-C6-003）

**决策**：**「close() 内身份守卫解绑 `if (defaultResolver == this) setDefault(null);` + setDefault 替换不传播 close（声明化）」唯一分支**：

1. **close 形态（:101-103 空壳替换）**：`public void close() { if (defaultResolver == this) { setDefault(null); } }`——身份守卫：仅当**本实例**是注册 default 时解绑（非 default 实例的 close 是合法生命周期事件——bean 容器传播 close 不误伤他人注册）；解绑经 `setDefault(null)` 复用既有 WARN 通道（:56-62——previous!=null && previous!=resolver(null) 触发换绑 WARN，零新日志语句，解绑留痕天然在位）。
2. **与 2512 null fail-fast 通道衔接**：close 后迟到 `call()`（:115-124）→ `defaultResolver==null` → :117-119 `IllegalStateException` typed 失败——2512 产物零改写（红线 1）而语义闭环（use-after-close 守卫由既有通道天然承接）。
3. **替换不传播（声明化，`WindowedStreamImpl.setDefaultFactory` 先例口径——测试自由 reset 形态亲证）**：`setDefault(A→B)` 不关闭 A——旧实例所有权归其注册方（app-beans bean），框架代关破坏所有权模型；A 的 close 缺失观测 = 既有换绑 WARN 已留痕（新旧标识）——owner doc 运维节观测指引（plan Follow-up 口径），不阻塞 closure。E5 §3a 生命周期句成文（Phase 3 承载）：「provider close 时若自身为注册 default 则解绑（身份守卫，WARN 留痕）；解绑后 UDF 调用按既有 null fail-fast typed 失败；setDefault 替换不关闭旧实例（所有权归注册方）」。
4. **幂等/重注册语义**：close 幂等（二次 close：default 已 null 或已易主——守卫 false，no-op；`closeIsIdempotent` :121 既有断言零改写并列）；close 后 `registerAsDefault()` 可再注册（setDefault 语义原样）——生命周期对称性由 javadoc 一句声明。
5. **static 强引用 GC 面**：解绑即清除 `defaultResolver` 对实例的静态强引用（单容器 close 即停机形态下容器级 GC 阻碍面随解绑消解——audit C6-003 缺陷面③的诚实覆盖：长驻进程多 provider 轮换场景收益，单容器形态不可达面如实登记）。

**理由（触发时序论证）**：缺陷时序——T1 provider bean 注册为 default（:106-108）→ T2 bean 容器传播 close（owner doc §3a 契约：「AutoCloseable 由 bean 容器传播 close」）→ close 空壳（:101-103）→ defaultResolver 静态强引用仍指向 closed 实例 → 后续编译/查表 `call()` 继续解析旧实例执行（use-after-close 无守卫）+ 静态引用阻碍容器级 GC（audit 缺陷三面：close 空壳/无解绑对偶/替换不关旧值）。post-fix：T2' close 身份守卫解绑（WARN 留痕）→ T3' 迟到 call 走 :117-119 typed 失败（快速失败、诊断指向「no default registered」）→ 静态引用释放。单容器生命周期内 close 即停机——行为变更面（close 后编译失败 vs 静默用旧）为不可达面如实登记（plan 风险段口径）。

**被否决的备选**：
- **setDefault 替换传播 close（关旧实例）**：旧实例所有权归注册方——框架代关破坏所有权模型；`WindowedStreamImpl.setDefaultFactory` 先例=替换不传播（测试 reset 自由形态亲证）。不采。
- **新增 unregisterAsDefault/removeDefault 公开对偶 API**：close 即解绑（AutoCloseable 传播通道既有——§3a 契约句在位）；对偶 API=表面扩张零消费方。不采。
- **close 对非 default 实例 typed 失败**：非 default 实例的 close 是合法生命周期事件（bean 容器传播）——typed 失败误伤。不采。
- **替换传播+观测 WARN（plan Follow-up 变体）**：同所有权否决；观测候选仅在「不传播」形态下有意义（已由换绑 WARN 天然承载）。不采。

**零触碰边界**：红线 1 全集（CHM :73/WARN :56-62/null fail-fast :117-119 零改写零移动）；`TestStreamScalarFunctionResolver` 12 用例零改写（红⑨ 为新测试类追加）；owner doc §3a 现文案本体零改写（生命周期句为 Phase 3 增补——E5 同 diff 或同 commit 序列）。

### D.5 W-10：subscribe 单订阅门 typed fail-fast（1.3-C6-005）

**决策**：**「engine 级 `AtomicBoolean subscribed` 门 + `subscriptionRef` 单订阅引用 + 二次 subscribe typed 拒绝（对齐生产 reject-if-stopped typed 风格）」唯一分支**：

1. **门形态（:167-173）**：`private final AtomicBoolean subscribed = new AtomicBoolean(false);` + `private ReplaySubscription subscriptionRef;`（engine 级单订阅引用——D.1 stop 对称的驱动目标）。subscribe：`if (!subscribed.compareAndSet(false, true)) { throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL, "subscribe() already called on this ReplayEngine; a second subscription would concurrently replay events and interleave writeReplayIndex (REPLAYABLE exactly-once violation) — create a new engine"); }` 随后既有 emitter 启动与 `new ReplaySubscription()` 原样、`this.subscriptionRef = sub` 登记。typed 风格对齐生产 :148-152 先例（typed+detail 指引「create a new …」句式——K12）；载体差异如实声明：生产 `DebeziumMessageSource.subscribe` 为事件分发多订阅语义（Set 登记），ReplayEngine.subscribe 为一次性重放启动语义——单订阅门与一次性语义同构（audit C10-008 预期形态：「若 C6-005 修复选定 typed 拒绝语义，补 double-subscribe 断言用例钉契约」）。
2. **语义闭环**：二次订阅的并发重放+`writeReplayIndex` 互踩（后写者回退先写者 offset——:37-40 REPLAYABLE 声明违约）结构性消失；失败/取消后二次 subscribe 仍 typed（门为 engine 级一次性——C.3 矩阵行 2 断言面）。
3. **红⑩ 承载**：双 subscribe → pre-fix 双 emitter 并存（线程名 `fraud-s1-replay-cdc` 枚举计数 ≥2 = red 确定性）；post-fix 第二次 typed + 首订阅存活（第一 emitter 完成不受扰）。
4. **行为变更面（E5）**：测试双胞胎「按生产语义多次订阅」的可用性变化——拒绝形态下 double-subscribe 用例断言 typed（C10-008 半边用例形态绑定本裁定；plan 风险段口径登记）。

**理由（触发时序论证）**：缺陷时序——T1 `subscribe(actionA)` 起 emitter-1 → T2 `subscribe(actionB)` 起 emitter-2（无门，:169-171）→ 两 emitter 并发重放同一 fixture（并发 `action.accept`）+ 并发 `writeReplayIndex`（:215/:247-253 offset store 互踩——后写回退先写）→ 恢复点位错乱（REPLAYABLE exactly-once 违约）。当前唯一调用方（parent `source.subscribe(event -> …)` 每 run 恰一次，:315-331 区）使缺陷契约面不可达——契约缺口而非现行触发（audit 口径）。post-fix：T2' typed 拒绝（detail 指引新建 engine）→ 单写者不变量成立。并发面：CAS 门原子；`subscriptionRef` 单写（subscribe 门保证恰一次写）+ stop 读——volatile 语义足够（引用发布后只读）。

**被否决的备选**：
- **登记语义**（多订阅共存+隔离）：与一次性重放语义冲突（多 emitter=多重放=互踩本体）；生产 Set 登记语义为分发源载体——跨载体错配。不采。
- **复用生产 Set 登记+隔离 offset 写**：隔离写需 offset 分片/合并协议=重放语义重设计（超工单半径且违 REPLAYABLE 单点位声明）。不采。
- **doc-only**：C6-005 为批次行 in-scope 缺口（C10-008 double-subscribe 用例预期 typed 语义在案）。不采。

**零触碰边界**：nop-message-debezium 全模块零 diff（生产契约对照红线）；parent subscribe 调用链零触碰；`TestReplayableCdcSourceFunction` 既有用例零改写（红⑩ 为新测试类追加）。

## 裁定 E 测试缝总表（C10-008 搭车定夺 + 红①–红⑩ 驱动器 + 零回退钉全列 + C10 映射）

### C10-008 两半边搭车定夺（四环授权链落款——对齐纪律第六条预设的兑现）

**裁定：两半边随批搭车**。四环授权链：①**2512 预授权引用**——2512 §3.5 C10 集合落名原文「C10-008 不搭车（关联本体 C3-007/MA-04/C6-005 **均在 R4-05**，强落会造无本体红的空壳断言，维持归 R-G）」——其不搭车前提（本体不在 2512）在本批成立反面：双本体现已在批，2512 Deferred 节预授权形态「或随 R4-05 对应族修复落地」兑现；②**双本体在批**——关联本体 MA-04（W-03，裁定 D.1）与 C6-005（W-10，裁定 D.5）均为本批次行 finding；③**钉有真实本体**——搭车两半边各自钉住本批新行为（emitter 有界终止钉=D.1 delegate 化后的取消路径基本面；double-subscribe typed 契约钉=D.5 新门）——非空壳断言（2512 勘误 4 纪律：禁造无本体红）；④**C3-007 interrupt 半边维持 R-G**——本体（cancel 无 interrupt 响应点）为 P3 不在批次行，强落 interrupt 响应性断言=无本体红；cra-index :476 G91 行 1.3 半边消号注归 Phase 4（沿 2512 C10-007 消号先例）。两半边落点：emitter 有界终止 pin 随组 C（W-03 载体）、double-subscribe typed pin 随红⑩（W-10 载体）。

### 红绿判据纪律（先红后绿，E2——本批 2 条 C10 验收前置[C10-005 随 W-02 / C10-006 反向钉零回退]+E2 泛在 10 钉）

全批测试零 mockito 新增使用（三站点模块 pom 无 mockito 声明亲读）——观测一律 JDK 并发原语 + latch + 手工桩 + 探针 subclass + 包内可见注入缝（2510-⑪ 惯例）。**orTimeout 假绿陷阱禁令**（2536/2537 全盘沿用）：future 完成性断言只允许同步 `get(timeout)` 捕获 TimeoutException 判红。**闩带上界 ≤30s 且收尾必放闸**。红绿快照落 `_tmp/` 总册 + 逐 trial 原始日志（2536 Major-D1 灭失教训——存证纪律+副本对冲）。测试类注释显式归属 **CRA 8.45 R4-05（`*R405` 后缀）**（同号防混：8.45-W-xx ≠ 8.41/8.42/8.43/8.44-W-xx；finding ID 防混权威锚 1.3-C3-009≠1.1-C3-009）。红④⑧ 结构/变异腿诚实留痕禁伪绿（F-4 在红⑦ 行落款：残态断言腿主用——K1 勘正口径）。

### 产品侧测试缝（S1–S10）

| 缝 | 形态 | 覆盖钉 |
|---|---|---|
| S1 | 含 sideInput 的 map 模型 + xpl 体写探针（`sideInputs['dict'].put(k,v)`）+ 双记录污染观测 + 应用侧引用 detach 腿（build 后应用改写自身 map → 快照零变化） | 红① |
| S2 | REPLAY_INDEX_KEY 注入非数字 index（同包缝+`NopStreamOffsetBackingStore.set` public）→ runner run() → Throwable/零事件双断言；`:130` 兼容锚并列（C.2） | 红②（=C10-005 同 diff） |
| S3 | ReplaySubscription 双回调注册（其一异常探针）+ 双 cancel → 执行计数/隔离断言 + stop() 先触发回调→后续 cancel 幂等腿 | 红③ |
| S4 | 源文本结构断言（GETTER_CACHE 有界性声明/SqlRowAggregateOps 拒绝条件字样——双载体双 leg） | 红④ |
| S5 | pre-fix 反射 ROOT_SCOPE 污染探针（trial 形态）+ post-fix 反射缺席结构断言 + fresh-root 跨 scope 不可见行为腿 + 漏斗完整性 grep | 红⑤ |
| S6 | emitRemaining protected + 测试子类 barrier（末文件 super 后停靠）+ attempt-2 run 终态形态断言 + 反向钉 :277-306 零改写并列 | 红⑥ |
| S7 | 残态断言腿（getSql()/transforms/edges 引用态——F-4 主腿）+ 二次 build 等价校准腿 + 既有 E2E 钉族零改写并列（保真锚） | 红⑦ |
| S8 | getterFor 源文本结构钉 + 变异四相链（还原→红→复修→绿；恢复后行存在性 grep 断言）+ round-trip 功能绿并列 | 红⑧ |
| S9 | close() → 静态 call() typed 断言（IllegalStateException 判别）+ closeIsIdempotent 零改写并列 + 替换不传播/再注册生命线腿 | 红⑨ |
| S10 | 双 subscribe → 线程名枚举计数（pre-fix ≥2=red）→ post-fix typed+首订阅存活腿+C10-008 double-subscribe 半边 | 红⑩（含 C10-008 半边） |

### 红①–红⑩ 驱动器总表

| 钉 | 工单 | 驱动器形态 | pre-fix 红判据 | 伪绿防线 | 闩带 |
|---|---|---|---|---|---|
| 红① | W-01 | S1：构建含 sideInput map 模型 → xpl 体 `sideInputs['dict'].put` → 第二记录读值断言；detach 腿（应用改写自身 map）；UOE typed 判别 | pre-fix 第二记录读到污染值（put 成功）=红（**当前即红，确定性——单线程可构造**） | UOE 实测（非日志断言）+ detach 腿防「冻结存在但顶层引用仍共享」退化 + 递归面（嵌套二级 map 写同 UOE 腿）；`TestWi11SideInputDeclaration` 零改写并列 | ≤10s |
| 红② | W-02（=C10-005 同 diff） | S2：注入非数字 replay index → run() → (a) Throwable 实测（FAILED 语义）；(b) 零事件断言（不呈现部分事件+正常返回）；(c) `:130` 锚并列 | pre-fix run 干净返回（假 EOS）=红（**当前即红，确定性**——注入先于 run，落在 S-1 残窗外） | Throwable 类型/消息判别（防 catch-all 复发）+ (b) 防「抛了但已污染输出」半修；S-1 迟到失败残窗=声明边界非钉面（禁造空壳断言） | ≤15s |
| 红③ | W-03 | S3：双回调（其一抛探针）+ 双 cancel → (a) 每回调恰一次（执行计数）；(b) 隔离（首回调抛后次回调仍执行）；(c) stop→cancel 幂等腿（回调总数不增） | pre-fix 回调双跑（计数 ×2）且异常中断循环（第二回调零执行）=红（**当前即红，确定性**） | 计数+隔离双 leg 防「幂等门存在但无隔离」半修；stop 对称腿防「delegate 存在但 stop 仍绕过」退化（plan 风险段点名退化面）；engine cancelled 位联动断言（emitter 退出） | ≤10s |
| 红④ | W-04 | S4：双载体源文本结构断言（声明存在性+拒绝条件字样）——结构红诚实留痕（2537 红⑥⑪ 先例）；校准腿=D.3 round-trip 功能绿（声明非行为腿——诚实登记非红判据） | pre-fix 声明缺席=**结构红**（断言零命中，确定性） | 源文本断言（非反射启发式——javadoc 无运行时可见性）+ 双载体独立 leg 防单边声明；本钉诚实登记为结构腿（无行为红——禁伪绿即禁谎称行为红） | ≤5s |
| 红⑤ | W-05 | S5：pre-fix 反射 ROOT_SCOPE 写→newCallScope 读污染探针（trial 形态留痕）；post-fix (a) 反射缺席结构断言（无静态共享 eval 根）；(b) 双 newCallScope 互不可见行为腿 | pre-fix 跨调用污染可见=红（**当前即红，确定性**）；post-fix 结构+行为双绿 | 反射缺席（防「字段改名但共享仍在」）+ 互不可见行为腿（防「根独立但子链仍共享」）+ 漏斗 grep（11 调用方零绕行——`ROOT_SCOPE` 引用仅本类）防「新根存在但调用方绕过漏斗」退化（plan 风险段点名） | ≤5s |
| 红⑥ | W-06（C10-006 反向钉绑定） | S6：子类 barrier 停靠 attempt-1（读取完成、守卫未释放）→ attempt-2 run() → (a) typed StreamException（ERR_STREAM_STATE_ERROR+re-entered 判别）；反向钉 :277-306 零改写并列 | pre-fix attempt-2 零事件正常完成（假 EOS）=红（**当前即红，确定性**——线程状态门+barrier 缝） | typed 实测+detail 判别（防「typed 但消息失归因」）；反向钉零回退（git diff 为空核对）防契约翻转；2512 复位块零触碰结构核对（:117-123 diff 为空） | ≤10s |
| 红⑦ | W-07（**F-4 钉形限定**：残态断言腿主用） | S7：(a) 残态断言——build() 后 model.getSql()!=null、transforms/edges 对象引用不变、edges from 值未被改写；(b) 二次 build 等价校准腿（两 env 等价断言）；既有 E2E 钉族零改写并列（保真锚） | pre-fix getSql()==null+transforms 整体替换+edge 就地改写=红（**当前即红，确定性——单次 build 即红**）；二次 build 差异腿不采用（裁定 B 证得深拷贝形态下二次 build 等价——F-4 条件分支关闭） | 多成员残态断言（sql+transforms+edges 三面——防「拷贝了 sql 漏了 transforms」半修）+ 引用同一性断言（防「值相等但对象已换」假绿）+ E2E 保真锚防「拷贝失真改变单次 build 产出」 | ≤30s（E2E round-trip） |
| 红⑧ | W-08 | S8：(a) getterFor 源文本结构钉（无 computeIfAbsent 方法引用回调形态）；(b) 变异四相链（还原→钉红→复修复→绿；恢复后可执行行存在性 grep 断言——R5 探针纪律）；(c) round-trip 功能绿并列 | pre-fix 结构钉红（computeIfAbsent 在位）=红（**结构红，确定性**）；(b) 为回退必红证明（**禁行为伪红**——锁倒置非确定性不构钉） | (a) 源文本断言（非反射启发式）；(b) 四相链日志留痕（还原-红-复修-绿时间序）；(c) 防重排破坏查表功能 | ≤10s |
| 红⑨ | W-09 | S9：register+registerAsDefault → close() → 静态 `call()` → (a) IllegalStateException typed（2512 :117-119 通道判别）；(b) closeIsIdempotent :121 零改写并列；(c) 替换不传播腿（setDefault(A→B) 后 A 未被 close——声明断言）+ 再注册生命线腿（close→registerAsDefault→call 恢复） | pre-fix close 后 call 仍解析旧实例执行（返回值可观测）=红（**当前即红，确定性**） | typed 判别（防「解绑了但 fail-fast 面被改」）+ (b) 防幂等回归 + (c) 防传播误伤（所有权红线钉化）；红线 1 三产物零改写结构核对 | ≤5s |
| 红⑩ | W-10（含 C10-008 double-subscribe 半边） | S10：同 engine 双 subscribe → (a) pre-fix 线程名枚举 `fraud-s1-replay-cdc` 计数 ≥2=红；post-fix 第二次 typed+计数=1；(b) 首订阅存活腿（第一 emitter 完成不受扰+replay index 单写者终值） | pre-fix 双 emitter 并存=红（**当前即红，确定性**——枚举计数） | 计数+typed+存活三 leg 防「门存在但误杀首订阅」与「typed 但门可绕」退化；C10-008 半边=本钉 (a)(b)（消号注归 Phase 4） | ≤15s |

**C10-008 emitter 有界终止半边（随 W-03 组落地）**：`emitterThreadTerminatesAfterBoundedRun`——runToCompletion 后按线程名查找 `fraud-s1-replay-cdc`，deadline（5s，> emitDelayMs+finishLingerMs 上界——C10-008 校准口径）内断言线程终止。纯观测 pin（零产品依赖）；**不**含 interrupt 响应性断言（C3-007 半边维持 R-G——四环授权链第④环）。

### 零回退钉全列

- **基线签名（plan 勘误 5，2026-11-07 亲跑 @ `12182bcffd`；`ef4094a81e` 零 nop-stream diff 继续有效——K10）**：聚合通道已知面③⑤留痕（runtime 1253 0F0E 13S/double E2E 慢跳过+format-avro/sql/fraud-example 反应器跳过）；批站点 solo 通道 = **sql 176 tests 0F 0E 0S EXIT=0 + fraud-example 126 tests 0F 0E 16S EXIT=0** + flow 315 0F0E（聚合通道内）。零回退实证 = 三站点模块签名维持（flow 315/sql 176/fraud-example 126+新钉增量）+ 已执行模块签名比对 + 已知面处置留痕（F1/docker/反应器跳过 solo 补齐按 E3 判据——禁静默重跑）。
- **2512 全部钉（红线 1/2/3）**：C10-001×3（`TestStreamScalarFunctionResolver` :139/:204/:261）+ C10-002/C10-004（`TestDirectoryFileSourceFunction`）+ C10-003（core 归属注记）+ C10-007 三探针 + `TestStreamScalarFunctionResolver` 12 用例（含 closeIsIdempotent :121）+ `TestDirectoryFileSourceFunction` 既有全部断言（反向钉 :277-306 含）+ `TestUdfE2E` + 三文件 2512 落地 diff（resolver CHM/WARN/null fail-fast；DirectoryFileSourceFunction 复位块 :117-123+merge-max；ReplayableCdcSourceFunction snapshotState 并回 :97-137）——钉内断言与落地 diff 全批 diff 零触碰。
- **2534–2537 各批钉**：`*R403` 10 类 + `*R404` 全部批钉 + 2534/2535 各批钉全集 + 8.41 受害侧钉（`TestRocksDBKeyedStateBackendCrossThreadOps`）+ 2537 全部新配置键——零触碰零重开（本批站点三模块与前四批零文件交集 git 亲证——plan 勘误 2；钉名全集以其各自收口档零回退钉节为 SSOT，本批不复制不重开）。
- **既有锚点名（本批零回退锚）**：TestWi11SideInputDeclaration / TestStreamModelDslBuilderE2E / TestStreamModelDslBuilderFailFast / TestSqlModelExpansion / TestSqlTransformExpansion / TestSideOutputTapOperator / TestAdvancedTransforms / TestStreamFlowAuditFixes / TestStreamErrorCodeContract（错误码面零新增码核对）/ TestStreamSqlExprCompiler / TestWi9AggregateCatalog / TestStreamSqlAggregations / TestStreamAggregatorFunctionResolver / TestReplayableCdcSourceFunction 全用例（含 `:130`）/ TestDirectoryFileSourceFunction 全用例（含反向钉）/ TestS1CdcPipelineE2E / TestS1CdcRecoveryE2E / TestS2FileAggregationE2E / TestS2RecoveryAndRescaleE2E / TestUdfE2E——钉内断言零改写（红钉为追加用例/新类形态，既有用例只增不改）；gated 多 JVM 套件（TestParallel2PcMultiJvmE2E/TestS2RestoreRescaleMultiJvmE2E——16S 门控面）零触碰。
- **已知环境面集（E3 噪声源预案——plan 勘误 5 沿用）**：面① nop-core TestResourceCacheInvalidationChain 负载 flake（solo 三联先例）；面② runtime F1 TestStreamConnectorRegistryE2E 负载 flake（本轮绿）；面③ runtime docker 双 E2E 慢跳过/fork teardown 超时（基线亲证——结果面完好按等价验收）；面④ debezium 6S 环境门控；面⑤ format-avro/sql/fraud-example 反应器跳过（基线亲证——solo 补齐处置路径）；面⑥ E1 在线模式 nop-ai-translate 面（预计不现形）。

### C10 映射（对齐纪律第一条）

| C10 条目 | 承载面 | 本批关联工单 | 零改写/兑现核对面 |
|---|---|---|---|
| C10-005（**验收前置，同 diff 绑定**——批次行增补+cra-index :396） | 失败注入 FAILED 断言（S2） | W-02（红②） | 同 diff 必落（禁拆分）；`:130` cancel 不抛锚零改写（merge-adjudication :150 收口要求）；既有 5 用例零触碰 |
| C10-006（**全矩阵唯一反向约束**——批次行增补） | 反向钉零回退（`runEnteredGuardPreventsDoubleEmission` :277-306） | W-06（红⑥） | 钉零改写（git diff 为空核对）；落败者不得读取语义维持（typed 形态下钉绿）；契约字面变更（静默→typed）已明示（裁定 B.2 决策 3 逃生条款处置） |
| C10-008（G91 :476——搭车裁定） | emitter 有界终止 pin（随 W-03）+ double-subscribe typed pin（红⑩ (a)(b)） | W-03/W-10 | 四环授权链落款（本节首）；C3-007 interrupt 半边维持 R-G（禁造无本体红）；cra-index 消号注归 Phase 4 |
| C10-007 | —（已随 2512 搭车消号） | — | 三探针零触碰（2512 产物） |
| 既有 C10 面零改写清单 | 2512 五钉/2534–2537 全部批钉/8.41 受害侧钉 | 全批 | 红线 1/2/3 全集——closure audit git diff 逐钉核对 |

其余红①③④⑤⑦⑧⑨ 为无 C10 号工单（merge-adjudication §4.5 仅登记 C10-005/006/008 关联面）——E2 泛在钉承载，形态见驱动器总表。

## 条件申报登记（禁静默扩权——预设全部不触发，两处禁选申报点落款）

①**`*-api` 契约叶 → 预设不触发**：10 工单 import 面亲读——`StreamException`/`NopStreamErrors`/`ICancellable`/`IEvalScope`/`EvalExprProvider`/`IScalarFunctionResolver` 均为使用面引用零 diff（ReplaySubscription 实现既有 ICancellable 签名零改动；resolver 实现既有 SPI 零签名改动）。②**orm/DB 承载 → 预设禁选不触发**：三模块无 `model/*.orm.xml` 实体面；全部修复为进程内 Java 面。③**C1-002 write-guard（nop-core plan-first）→ 禁选落款**：裁定 A.1 明示否决 write-guard 方案（`EvalScopeImpl`/`EvalExprProvider` 零 diff——只读机制锚）；merge-adjudication :186 零依赖方案建议为裁定依据。④**MA-03 父类缝（connector-debezium 跨模块+2537 站点文件）→ 禁选落款**：裁定 C.1 明示否决（`DebeziumCdcSourceFunction` 零 diff 红线）；子类 override run 形态零父类 diff。⑤**`_` 生成物/pom 结构 → 预设不触发**：零新错误码（复用 ERR_STREAM_STATE_ERROR/ERR_STREAM_NULL_ARG/ERR_STREAM_INVALID_ARG 在位码 + IllegalStateException 既有通道）；新测试落既有测试树（`*R405` 新类）；站点文件零 `_` 生成物、flow/sql/fraud-example main 树无本批涉 `_gen` 面；emitRemaining 可见性放宽（private→protected）为同文件结构面非 API 面。**任一条件申报触发 → 按 AGENTS.md 四件证据链走完方可实施；未申报前实施=禁。**

## 零触碰边界总表（8.18 产物面 / 2534–2537 产物面 / 同文件跨批面 / 批外模块 / P3×6 与 G91 残余 / `_gen`）

- **8.18/plan 2512 产物面（同文件跨批——禁顺带逐面）**：resolver 三产物（红线 1）+ 复位块 :117-123 与 merge-max（红线 2）+ snapshotState 并回 :97-137 与 metadata 契约 :294-298（2511 承接面）——本批触 resolver close 面（D.4）/DirectoryFileSourceFunction 落败分支（B.2）/ReplayableCdcSourceFunction 失败通道面（C.1）三面，面边界 diff 分组登记禁顺带；五钉+12 用例+既有断言零改写。
- **8.17/plan 2511 产物面**：CdcEventFixtures（:287-305）与 metadata 契约注释零改写；W-02/W-03/W-10 同文件三工单 diff 分组禁顺带禁交叉。
- **8.41–8.44/plans 2534–2537 产物面（零文件交集亲证）**：全部批钉（`*R403`/*R404/2534/2535）+ 新配置键全集 + 1.2-C10-006 受害侧钉——零触碰零重开零顺带；**`DebeziumCdcSourceFunction` = 2537 站点文件零触碰红线**（W-02 父类缝禁选依据；MA-03 事实源只读引用）。
- **批外模块（零 diff 红线）**：nop-commons `Cancellable`（MA-04 契约基准——组合使用零 diff）；nop-core `EvalScopeImpl`/`EvalExprProvider`（C1-002 机制锚——零 diff，write-guard 禁选）；nop-message-debezium `DebeziumMessageSource`/`NopStreamOffsetBackingStore`（C6-005 生产契约对照+注入缝载体——测试只读使用 `set` public API 零 diff）；nop-stream-core/nop-stream-runtime 产品源码预设零 diff（`SupervisionLoop`/`TaskManager`/`StreamSourceOperator` 只读引用）。
- **P3×6 相邻面（防混登记——Deferred 承载）**：C3-007（ReplayEngine 无 interrupt 响应点——W-02/W-03/W-10 同文件零触碰；协作式取消声明口径维持）/C1-006（UserHistoryEnricher——2512-③ 主导不单独立案）/C1-007（InMemoryBeanFunctionResolver——flow 零触碰）/C9-001（scope 链分配画像——A.1 家族基线引用面，本体零触碰）/C9-002（begin() 重复查找——sql 零触碰）/MA-06（SQL 子表达式重复求值——sql 零触碰）。执行期意外触及禁顺带——四件证据链申报或回退。
- **G91 残余半边与 8.46+**：C10-008 C3-007 interrupt 半边（维持 R-G——裁定 E 四环第④环）/1.2-C10-016（跨单元零交集）/R4-06 起 1.4 单元（零站点交集）——全部维持 plan Non-Goals。
- **`_gen` 与 orm**：站点文件零 `_` 生成物、三模块无 orm 实体面（条件申报②⑤预设零触发）；`StreamModel` 的生成基类 `_StreamModel` 零触碰（B.1 拷贝经模型公共面，不触生成层）。
- **既有裁定文档**：2512/2536/2537 设计文档、batch-proposal、merge-adjudication、C10 台账、cra-index 台账行——零回写（收敛注记归 Phase 4）；本设计文档为唯一新增文档面（命名与分组调整登记=plan 2538 行内订正，不改其批次行原文）。

## 端到端与接线验证义务（rule 22/23/24——随 Phase 2 落证）

- **端到端（rule 22——plan Phase 2 三链）**：≥1 条 S1 链路覆盖 W-02/W-03/W-10 组合面（run() 入口 → 注入失败 → emitter 失败 → cancel → subscription 生命周期 → FAILED 终局/恢复重放——红② 即链路载体）；≥1 条恢复链覆盖 W-06 与 2512-④ 组合行为（cancel → 同实例重跑 → 增量恢复 → 守卫 typed 语义——`TestS2RecoveryAndRescaleE2E` 邻接+红⑥ 缝承载）；≥1 条 SQL 声明面链路（sideInput 声明 → 构建期冻结 → xpl 逐记录消费——覆盖 W-01，红① 即链路载体）。
- **接线验证（rule 23——十项）**：冻结真生效（红① UOE 实测）；FAILED 真生效（红② 重抛实测）；幂等/隔离真生效（红③ 回调计数实测）；包络声明真生效（红④ 结构断言+D.3 校准腿实测）；新根隔离真生效（红⑤ 误用探针实测）；守卫粘性真生效（红⑥ 缝实测+反向钉零回退实测）；build 契约真生效（红⑦ 残态+二次等价实测）；锁外 compute 真生效（红⑧ 结构+变异实测）；解绑真生效（红⑨ close 后 call 实测）；订阅门真生效（红⑩ 双订阅拒绝+首订阅存活实测）。
- **无静默跳过（rule 24——十一显式）**：冻结显式（typed UOE——非静默污染维持）；失败显式（typed 重抛——非 catch-all+伪装 EOS 维持）；幂等显式（check-and-set——非全量重跑维持）；隔离显式（逐回调 try——非中断循环维持）；新根显式（fresh root——非共享可变维持）；守卫显式（typed 拒绝——非静默零事件 EOS 维持）；契约显式（深拷贝——非静默改写维持）；锁外显式（两段式——非锁内回调维持）；解绑显式（setDefault(null)——非空壳 close 维持）；订阅门显式（typed 拒绝——非并发重放维持）；新增公共分支零空方法体/TODO（grep 核对）。

## 批外新发现登记（亲读发现即登记，禁瞒）

- **BF-1（信息性——锁序核证结论，非缺陷）**：`ReplayEngine.stop()` 为 `synchronized`（:255-256）而 subscribe/ReplaySubscription.cancel 无 engine monitor——D.1 组合后锁序 = engine monitor（stop）→ delegate monitor（cancel 内部），外部 cancel 路径（parent finally `sub.cancel()`）只取 delegate monitor 不取 engine monitor——锁序单向、零反向路径，无死锁面。登记为核证结论。
- **BF-2（信息性——plan 文本勘误，非产品缺陷）**：plan 2538 红②③⑤⑥⑧⑨⑩ 行内 F-4 段落误插（K1 详证）——F-4 语义专属红⑦。裁定以本档驱动器总表为准；plan 文本处置归 Draft Review 回填/后续维护者，本批不回写 plan 红行文本（对齐纪律「不回写历史裁定文档」同源纪律——本 plan 为活跃计划，其红行订正随 Phase 1 收口由 plan 侧登记，不在本文档半径内）。
- **BF-3（信息性——WI11「get() 恰一次」契约粒度核证，非缺陷）**：`materializeSideInputs` 在每次 build() 的模型遍历中执行（:1017 调用点在 build 走链内）——B.1 深拷贝形态下二次 build 重走物化，supplier.get() 按 build 粒度「每次恰一次」——与 owner doc WI11「构建期 get() 恰一次并冻结只读快照」契约自洽（「构建期」=单次 build 过程）；多 build 场景的多次 get 为新显式语义（E5 随批一句登记），非缺陷。
- **BF-4（信息性——`ReplaySubscription.getCancelReason` 语义变化面确认，非新缺陷）**：D.1 转发后 getCancelReason 从恒返 STOP 变为返回 delegate 实际 reason（kill/stop）——行为变更面（非缺陷回归）：已在 D.1 决策 3 以 E5 声明承载，C7-001 归因面由 C.1 任务 FAILED 语义承载。登记防 closure audit 误判为未申报行为变化。
- 核证范围 = 10 工单站点文件全量亲读（K1–K12 锚清单）+ 平台机制文件四处（EvalScopeImpl/EvalExprProvider 工厂/ExecutableFunction.invoke/Commons Cancellable）+ 生产契约对照两处（DebeziumMessageSource/ICancellable）+ 父类守卫与 pendingError 通道（DebeziumCdcSourceFunction run/throwPendingError/cancel 区）+ 测试锚点三处（:130/反向钉/closeIsIdempotent）；除 BF-1~BF-4 外无批外缺陷。

## 评审记录

- **设计评审 R1**（2026-11-07，agent_ef449f36 fresh session 只读 @ `ef4094a81e`，live 抽验 ~80 处锚点）：**PASS-with-revisions——0 Blocker + 1 Major + 5 Minor，全部随轮落盘，无需 R2 放行 Phase 2**。**MJ-1**[Major] W-02 入口复位存在未登记的「并发 attempt 抹除窗」（attempt-2 分布式 deployTask 进入不等待旧线程[K9]——入口复位抹除在途 run 已记录失败→wrapper 正常 EOS 收尾=伪装 EOS 在竞窗重开洞；S-1 原文仅覆盖迟到失败机制）——S-1 扩写覆盖+C.3 矩阵补面+被否决备选登记「去入口复位」取舍[兜底论证：REPLAYABLE at-least-once+LOG.error 保留+毫秒级窗]。MN-1 round-trip 恒等直钉腿+边界声明/MN-2 linger 窗行为变更成本登记/MN-3 C9-001 引用行号订正（:245 画像/:278 口径）/MN-4 计数单位注明（11 调用方=10 文件 12 调用点）/MN-5 catch 分支镜像 throwPendingError 双分支精确化。三风险专项：W-02 suppressed 优先级 PASS（双异常窗压力测试+反转备选无运行时证据需求+家族一致性论证）/W-06 契约字面变更 PASS（C10-006 反向钉 emptiness 恒真亲读核证+deployTask 闭环成立+gated 16S 无静默 bounce 依赖亲证+MN-2 成本登记）/W-07 保真 PASS with MN-1（三层输出级 oracle 充分性+直钉腿补强）。核证面：2512 裁定①–⑤+「解绑归 R4-05 不代做」原文逐字命中/C9-001 画像 :245 亲证/K1–K12 全部 firsthand/红线九锚全命中/rule 14 合规/check-doc-links EXIT=0。

- **设计评审 R1**：（预留——独立评审子 agent fresh session 只读完成后回填：结论/修订清单/live 抽验规模/想象性分析三风险点结论）
