# 2503 CRA Phase 8.9 — R1-08 Debezium 双轨与消息交付语义批（R16/R17/R18 面，B1-05 三案，6 项）

> Plan Status: active
> Draft Review: R1 agent_68ef0989（0 Blocker、2 Major[Issue1 TestDebeziumErrorBarriers 9→3+4+2 归因勘正/Issue2 roadmap L329 幽灵锚点修正]+5 Minor——修订 agent_1563e10d 全量应用[live 锚点回读核实]，active）
> Design Review: 两裁定文档独立评审 agent_d082ac70（D12 M-1[ConsumeFailure CDC 接收面未裁定]+m-1/m-2；REGISTRY 原样通过）→ 修订 agent_28bc4b02 落盘（subscribe 变体携带失败处理器/convert attempt 固定 0/poison-event 键/守卫挂桶代粒度）→ 增量复核**通过进入实现**[零遗留]
> Last Reviewed: 2026-10-25
> Source: roadmap 工作项 8.9（R1-08，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L151，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-08 行（L69：finding ID 清单 6 项 / PA=无 / 依赖=无前置可并行 + R4-24 设计联动 + R2-16 对齐注记 / exit criteria 增补四条）；判级终裁 = family-clustering `.../family-clustering.md` §三 B1-05 行（L516：三案统一维持 P1）；成员发现详情 = 1.2 审计 `ai-dev/audits/2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency/`（C3-014 = C3-async-chains.md L496、C3-004 = L145）+ 4.1 审计 `ai-dev/audits/2026-10/2026-10-20-2210-deep-audit-nop-message-concurrency/`（C3-002/C8-004/C6-002/C8-002/C6-006 条目；**工单落位权威 = merge-adjudication.md**：MA-02 同 diff 配对、MA-05 三面归一、§五-3 判级配对、§五-5 REGISTRY 契约、§五-7 毒事件 endpoint、§七-1/3/4 跨单元对账）；执行基线 @ `9042b56f69`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.8 已 done，plan 2495–2502 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2496（R1-01 绝缘层批，completed——4.1-C3-009 七屏障 Throwable 闭合 @ `1925eb2dd5`：本批 C8-004/C8-002 的屏障宽度面、wrapper worker 守卫④已由其落地，**同文件异面零回退钉**见 Baseline 边界核对 2）；plan 2498（R1-03 池消费方批，completed——1.2-C4-004 per-engine 专用载体 + stop 有界终结预算 @ `a6a59f2035`：本批 C3-014 残余面为该载体前提上的续作，**不重做载体**，边界见 Baseline 边界核对 1，2498 勘误 4 与 Non-Blocking Follow-ups 协同注记的兑现批次即本批）；plan 2495/2493/2494（批次划分与 Phase 8 展开）；**下游交互**：R4-24（roadmap 8.64，硬依赖本批——REGISTRY 契约一次裁决设计项为 G62 十三项的实现契约，**禁止各修一半**）、R2-16（3.1-MA-11 consumeAwaitTimeout 契约对齐互注——4.1 merge-adjudication §五-6 + batch-proposal R2-16 行 L90 / R4-24 行 L139「MA-11 与 R1-08 consumeAwaitTimeout 契约对齐」）、R2-08（1.2 连接器 MA 束——C3-014 同文件 MA-09~18 续作面本批不承接，边界注记落账）、R4-26/R3-02（R17 族兄弟 4.3-MA-09 / 5.2-I-02——注册/注销配对族批次对齐注记）
> Protected Area 与授权：本批 **PA 触碰面为零（batch-proposal PA 列 = 无）**——无 plan-first/ask-first 义务。零触碰声明（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物；`NopStreamOffsetBackingStore`/`FAILURE_LISTENERS` 注册协议本体（C1-002/C1-001/C5-002/MA-10）不改动（归 R4-24，边界见 Non-Goals）。

## Purpose

关闭 R1-08 六项已确认发现（batch-proposal §二.1 R1-08 行：P1×5——1.2-C3-014[MA-07 ←C4-005/C4-006]、1.2-C3-004、4.1-C3-002+C8-004[MA-02 同 diff 配对]、4.1-C6-002[MA-05 主]、4.1-C8-002；P2×1——4.1-C6-006[R16 双轨族搭车]）：①Debezium 双轨（R16 族）使用面/本体面同批收敛——stop 契约从「已发出」升级为「已终止」（幽灵引擎不可再现）、事件屏障两极同禁（存活极性禁整体炸流 + 投递极性禁吞 offset 推进）、订阅生命周期注册/注销身份一次收敛；②消息交付语义（R18 族 Local 面）——Local 消费失败第一层 onConsumeFailure 回调补齐，三层失败架构（SPI 回调 → 原生 DLQ[Local 无 substrate] → MessageConsumeFailureBridge）Local 面闭合；③枚举器恢复双登记（同表双引擎重复消费）闭合；④B1-05 三案统一 P1 落账确认（4.1 §五-3 同批调整完成）；⑤R4-24 REGISTRY 契约一次裁决设计项落档（R4-24 硬依赖，禁止各修一半）。批末 roadmap 8.9 → done，六项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-08 = 6 项成员（P1×5 + P2×1）；cra-index P1 台账五行（L150 `C3-014（MA-07）`、L152 `C3-004`——1.2 单元；L266 `nop-message-C3-002+C8-004（MA-02 同 diff 配对）`、L267 `nop-message-C6-002（MA-05 主）`、L268 `nop-message-C8-002`——4.1 单元，裁定批次均=R1-08、状态均=open）+ P2 台账 G63 行（L439：成员 4.1-C3-009、4.1-C6-006——C3-009 已随 plan 2496 闭合，C6-006 批次注记「R1-08（C6-006 随 P1 4.1-C6-002 同 map 改造）」）；roadmap 8.9 行 Status=todo。
- 前置：**无硬依赖前置批**（batch-proposal 依赖列「无前置（可并行）」）；两份前置产物在位并构成本批边界前提：plan 2496 Phase 4（`1925eb2dd5`，4.1-C3-009 七屏障）与 plan 2498 Phase 1（`a6a59f2035`，1.2-C4-004 专用载体）。
- 判级终裁已定：family-clustering §三 B1-05（L516）=「4.1 判级三案嵌入方口径（MA-02 配对 C3-002+C8-004 / MA-05 幽灵订阅 / C8-002 Local 断链）**三案统一维持 P1**」——「生产主路径结构性免疫」保留为触发条件限定（非默认拓扑不触发），随工单走不降档；配对/同批调整约束（4.1 merge-adjudication §五-3）维持。本批不再开庭判级，只承载落账确认。
- 设计文档现状：`ai-dev/design/nop-message/backend-semantics-design.md` 在位（D1/D5/D6/D11 契约源），**其范围节不含 nop-message-debezium**（4.1 审计 C8-004 误报排除节证实）——本批毒事件 endpoint 裁定以此为扩围 owner（SSOT，不另起新交付语义文档）；`ai-dev/design/concurrency/` 在位五份设计文档，REGISTRY 生命周期契约为缺口（R4-24 无实现契约可依）——本批补齐。

### 前置批边界核对与残余面裁定（live @ `9042b56f69` 回读；本批最大的勘误风险点，两项核对均有 live 证据）

**边界核对 1——C3-014 vs plan 2498 Phase 1（专用载体已落，残余面裁定）**：live `DebeziumEngineWrapper.java` 证实 2498 产物全部在位——per-engine 专用载体字段 `engineExecutor`（:117，`debezium-engine-<connector>` 命名 daemon 单线程，:184-190 惰性重建）、`stop()` 内载体 `shutdown()` + `awaitTermination(5s)` 有界终结（:270-283，常量 `STOP_TERMINATION_BUDGET_SECONDS` :105）、引擎体退出 finally 自关停载体（:237）；且 :267-269 与 :305-308 注释**明文预留本批承接面**（「The termination-latch contract and the startup guard remain owned by 1.2-C3-014 (R1-08)」「the shutdownNow escalation on a stuck engine belongs to 1.2-C3-014 (R1-08)」）。**裁定：C3-014 残余面 = 五项，2498 已覆盖面不得重做也不得回退**——①`stop()` 对 `engine.close()` 抛出的依赖方 `IllegalStateException`（STARTING_TASKS 窗口，debezium-embedded 3.7 契约）仍**双层吞掉**（wrapper :256-262 `catch (Exception)` 仅 LOG）且 `stopped` 一次性守卫（:250 `getAndSet(true)`）使后续 stop 永久 no-op → 幽灵引擎面**原样在**；②四个 in-scope 停止面（reader close :295-312 / function run finally :297-316 / cancel :331-344 / truncateForDrain :352-367）仍是「stop 已发出 = 引擎已终止」——runner 线程 join 有 typed 超时抛错（:302-307，plan 2343 既有）但 **join 成功 ≠ 引擎终止**（runner 经 latch 退出时 ghost 可存活），引擎级终止性验证缺失面原样在；③worker lambda 无 stopped 守卫（`runEngineWorker` :224-240 不检查 `stopped`，C4-006 面）原样在；④stuck 引擎的载体 `shutdownNow()` 升级未做（2498 注释明文归本批）；⑤终结 latch/终止可观测契约（C4-005 残余——2498 的有界等待提供了预算，终止性信号面归本批）。

**边界核对 2——4.1 成员 vs plan 2496 Phase 4（C3-009 七屏障已闭，同文件异面零回退钉）**：live 回读证实 2496 落地面与本批缺陷面**同文件异面、零重叠、零回退**——`DebeziumMessageSource.dispatchEvent` :163-171 的 catch 已拓至 Throwable 宽度（2496 屏障⑤，Error 逃逸面已闭）但 **Exception 分支仍仅 LOG.error → `handleDebeziumEvent` 正常返回 → engine 按契约推进 offset**（C8-004 的交付语义面 = 屏障宽度之外的另一极，原样在）；`DebeziumEngineWrapper.handleDebeziumEvent` :202-207 **完全无屏障**（2496 只守卫了 worker lambda ④=`runEngineWorker`，notifying 回调体内 convert/accept 直跑——C3-002 存活极性面原样在）；`LocalMessageService.invokeSubscription` :213-240 的 catch 已拓至 Throwable（2496 屏障③）且 `nop.message.consumer-failed` 日志键在位，但**全文零 `onConsumeFailure` 调用**（C8-002 第一层回调面原样在）。**裁定：本批四项 4.1 修复为增量面——不得收窄 2496 已落的 Throwable 宽度、不得改写 Error 分支失败上报路（`notifyEngineFailure`/`dispatchConsumeFailure` 同路语义），回归门禁 = `TestDebeziumErrorBarriers` 3 条 + `TestDebeziumEngineWrapperFailureListener` 4 条 + `TestDebeziumEngineDedicatedCarrier_C4_004` 2 条（2496/2498 产物合计 9）零回归**；C6-002（注册/生命周期）与 C6-006（注销身份）为 2496 未触碰的 map/锁面，无边界纠缠。

### 六成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `9042b56f69` 回读值，执行前如再漂移以 live 为准并登记；漂移成因见勘误 4）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向（工单落位 = 4.1 merge-adjudication 权威） |
|---|---|---|---|---|---|
| 1 | 4.1-C3-002+C8-004（P1×2，MA-02 同 diff 配对；cra-index L266，B1-05 案一） | 4.1 / nop-message（nop-message-debezium） | `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:202-207`（`handleDebeziumEvent` 无屏障；convert 抛点 = `DebeziumEventConverter.java:33-45` parseNonStrict CCE、`:112-124` parseKey NPE、数值宽度 CCE）；`nop-message/.../debezium/DebeziumMessageSource.java:163-171`（dispatchEvent 吞订阅者异常 → offset 推进）；`:34`/`:193-195`（started 标志不感知引擎死亡）+ 全类零 `registerFailureListener` 接线（对照：nop-stream 主路径 `DebeziumCdcSourceFunction.java:248/:298` 已成对注册） | 畸形事件转换异常逃逸进 `engine.run()` 使 CDC 流终态死亡（isStarted 撒谎、静默断流）+ 订阅者 handler 异常被吞使 engine 视为投递成功并推进 offset（事件对该订阅者永久丢失）——屏障存活/投递两极性 | MA-02 修复点（同 diff）：①`handleDebeziumEvent` 补 per-event 屏障（convert 与 consumer 调用统一纳入，坏事件不炸流）；②毒事件 failure endpoint 按本批 Phase 1 裁定落地（两极同禁：纯吞禁[本行 C8-004 面]与整体炸流禁[C3-002 面]；行为=跳过+计数+上报+ConsumeFailure 派发或中止本轮换 at-least-once，以裁定为准绳）；③`DebeziumMessageSource` 启动时注册 failure listener 并把终态死亡反映到 started 标志或健康探针（isStarted 不再撒谎；**按现行 `registerFailureListener`/`unregisterFailureListener` API 形态使用，不改注册协议**——MA-10 归 R4-24）；④dispatchEvent 失败分支接入同一 endpoint 语义。边界：不回退 2496 屏障⑤ Throwable 宽度与 worker ④ Error 上报路 |
| 2 | 4.1-C6-002（P1，MA-05 主；cra-index L267，B1-05 案二；**工单 = MA-05 三面归一**，勘误 1） | 4.1 / nop-message（nop-message-debezium） | `DebeziumMessageSource.java:72-80`（subscribe 先 `subscriptions.put` 后 `startEngineIfNeeded`）、`:137-153`（start 失败 catch 重抛 NopException 不回滚）、`:176-188`（stop：二次吞 + `engineWrapper=null` + `subscriptions.clear()`）、`:227-237`（Subscription.cancel） | 启动失败路径幽灵订阅残留（异常上抛但注册条目留存、调用方拿不到 ICancellable 永不可注销）——后续任一次 subscribe 成功启动引擎后事件对幽灵双投递；同站点另有锁外交错面（C3-003 交错 A/B：订阅静默丢失 / stop 后引擎复活） | MA-05 三面归一同站点一个 diff（merge-adjudication MA-05 修复点）：①`subscriptions.put` 移至 `startEngineIfNeeded` 成功之后（或 catch 中 remove 后重抛）；②subscribe/cancel 的「增删 + 空判定 + 启停」复合操作统一入同一锁；③stop 后 subscribe typed 拒绝（`ERR_DEBEZIUM_ENGINE_START_FAILED` 族）而非复活引擎——C3-003 交错 A/B 与 C1-006 并入子面随同 diff 闭合（半修禁戒：只修 put-then-start 单面会留下交错面） |
| 3 | 4.1-C6-006（P2；P2-G63 行 cra-index L439，R16 双轨族搭车） | 4.1 / nop-message（nop-message-debezium） | `DebeziumMessageSource.java:32`（`Map<Consumer<ChangeEvent>, Subscription>` action 键）、`:74`（put 覆盖语义——同引用双订阅 S1 成孤儿句柄）、`:227-237`（cancel 按 action remove + `isEmpty()` → `stop()`） | 以 action 身份作注册/注销键：同方法引用双订阅后 cancel 旧句柄删掉现任注册并停掉活跃订阅引擎（WI23/MSG-F-15 同族未修；设计 §3.9 明文禁令「不得回退到按 consumer 身份查找」） | WI23 同法注销身份改造：按 Subscription 自身身份注销（`subscriptions.values().remove(this)`）或 map 改 `Map<Subscription, ·>`——**与 MA-05 同 map/锁改造同 diff 落地**（batch exit criterion；判级独立 P2）；重复 action 订阅时 WARN 声明覆盖/拒绝语义随实现期对齐 |
| 4 | 4.1-C8-002（P1，独立工单；cra-index L268，B1-05 案三） | 4.1 / nop-message（nop-message-core） | `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:213-240`（`invokeSubscription` catch Throwable :232 仅 `LOG.error("nop.message.consumer-failed...")` :238；`handleMessageResult` reply 发送失败 :230 经同 catch 被吞）；全类 grep 零 `onConsumeFailure`（对照 Kafka :631-643 / Pulsar 各 2 处已在） | Local 消费失败静默丢弃且从不派发第一层 onConsumeFailure 回调——违反设计 §3.3「无 DLQ substrate 后端仅第一层回调」契约，at-least-once 退化 at-most-once，三层失败架构（SPI 回调 → 原生 DLQ → `MessageConsumeFailureBridge`）Local 整体断链（Local 为生产路径：SysDaoMessageService 承接 nop_sys_event） | catch 中构造 `ConsumeFailure` 并 guarded dispatch `config.getConsumer().onConsumeFailure`（对齐 `KafkaConsumeTask:631-643` 形态：回调自身抛异常不得杀死发送者/重投线程）；三层架构 Local 面闭合（bridge 可收到事件）；是否延迟重投按设计 §3.9「无 broker 重投循环」立场**显式裁决**并落 owner doc（不得静默不裁决）；**不回退 2496 屏障③ Throwable 宽度与 `nop.message.consumer-failed` 日志键**；R2-16 对齐注记义务见 Phase 4 |
| 5 | 1.2-C3-014（P1，MA-07 ←C4-005/C4-006；cra-index L150）——**残余面 = 边界核对 1 五项** | 1.2 / nop-stream（connector 使用链）+ nop-message（wrapper 载体本体） | wrapper：`DebeziumEngineWrapper.java:245-286`（stop：:256-262 吞 ISE + :250 stopped 一次性守卫）；`DebeziumMessageSource.java:176-188`（stop 二次吞 + engineWrapper=null + subscriptions.clear）；in-scope 四停止面 = `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:295-312`（close）、`DebeziumCdcSourceFunction.java:297-316`（run finally）、`:331-344`（cancel）、`:352-367`（truncateForDrain）；worker lambda `runEngineWorker` :224-240（无 stopped 守卫，C4-006） | STARTING_TASKS 窗口 stop 被依赖方 ISE 按契约拒绝且被载体双层吞掉——取消/关闭路径产生永久幽灵引擎（活 binlog 连接 + 引擎线程组 + offset 持续覆写同名 registry 桶），无任何组件重试关闭；同名重部署时 ghost 覆写 offset 致恢复跳数（数据面后果） | 按边界核对 1 残余五项落：①wrapper.stop 对 STARTING_TASKS 拒绝做**有预算重试**（等待状态离开 STARTING_TASKS 后重试 close 直至 STOPPED）或把 ISE typed 上抛——不得吞（形态随实现期对齐 debezium-embedded 3.7 状态机契约）；②四停止面 `source.stop()` 后增加**引擎终止性验证**（有限预算内轮询，未终止 typed 失败或 ERROR 显式 ghost 风险声明）——「已发出」升级「已终止」；③worker lambda stopped 守卫（C4-006）；④stuck 引擎载体 `shutdownNow()` 升级（2498 预留）；⑤终结 latch/终止可观测契约闭合（C4-005 残余）。**不重做 2498 载体**（engineExecutor / 5s 预算 / 既有 close 链零回退）；REGISTRY 同名互覆残余的契约归宿 = Phase 1 REGISTRY 裁决一致性声明，本体清理归 R4-24 |
| 6 | 1.2-C3-004（P1；cra-index L152） | 1.2 / nop-stream（nop-stream-connector-debezium，配合 core 恢复时序） | `DebeziumTableSplitEnumerator.java:63-67`（start 全表 `i % parallelism` 重算，`computeIfAbsent` 只增不清）、`:130-133`（restoreState 把全部 unassigned 表 `computeIfAbsent(nextSubtaskIndex)` 单桶倾倒——注释声称 one table per registration）；时序前提 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/coordinator/LocalSourceCoordinator.java:160-163`（restoreState 恒先于 start） | 恢复路径把未分配表与新份额双重登记（恢复态 ∪ 新鲜份额 = 并集非差集）——同表被两台引擎重复消费，下游变更事件双份投递（jdbc-2pc 重复行 / file sink 重复行；reader 侧 D-4 守卫只挡「已启动后新表」，跨 reader 无去重） | 审计建议二选一：①restoreState 不预置份额，start 检测恢复态把 unassigned 表并入其 position 份额桶（`i % parallelism` 的桶，每表恰一桶）；②start 在 `restoredState != null` 时跳过全表重算、只按恢复态分配；对齐同族已闭环样板 `FileSplitEnumerator`（恢复集合防重复分配）；相邻面 1.2-C1-001/C2-001（同枚举器线程安全面）不归并不扩批（勘误 6） |

### Baseline 勘误（输入不一致登记）

1. **batch-proposal R1-08 finding 清单「4.1-C6-002」为 MA-05 工单的 P1 代表 ID，修复单元 = MA-05 三面归一**：4.1 merge-adjudication MA-05 工单 = C6-002（P1 主）+ C3-003（P2 站点主登记：锁外 map 写 + 锁内 started 检查，交错 A 订阅静默丢失 / 交错 B stop 后引擎复活）+ C1-006（P2 并入交错 A 子面），修复点明文「同站点一个 diff 批次」；旁证 = batch-proposal R3-02 行依赖列「与 R1-08 **4.1-MA-05** 注册/注销配对修复对齐注记」与 family-clustering R17 族行「P1：**4.1-MA-05**（幽灵订阅；B1-05）」。本批按工单级三面归一承接（成员 2 修复方向已并入），防「只修 put-then-start 单面留下交错面」的半修。
2. **batch-proposal R4-24 行「REGISTRY 契约一次裁决（……覆盖 MA-03/MA-04/C1-002 三单+2.5 消费面）」中 MA-04 系笔误/漂移**：REGISTRY 契约裁决覆盖对象权威口径 = 4.1 merge-adjudication §五-5「一次裁决覆盖 **C5-001+C6-004**（= MA-03 两成员）**+C1-002** 三单 + 软钉#5（setOffsets REPLACE）+ WI 2.5 消费面表态」；4.1-MA-04（Local 重投 executor 族）无任何 REGISTRY 面。本批 Phase 1 设计项按 §五-5 口径执行，cra-index 回填注记时同步勘正引用。
3. **账面编号漂移防混注记**（family-clustering R16 行 L60 明文）：inventory-ledgers 链#21 所记「4.1 MA-09」实为 **MA-05** 工单——本批 Baseline 与 cra-index 回填一律以 MA-05 书写；MA-09（DebeziumEngineWrapper 生命周期族：C6-005/C1-011/C6-007，P3）**不在本批成员清单**，其与 C3-014 的同文件续作边界见勘误 5。
4. **行号漂移登记（成因各注）**：C3-002 `handleDebeziumEvent` 审计载 :185-190 → live :202-207（2496 屏障④注释 + 2498 载体注释致类头下移）；C8-004 `dispatchEvent` 审计载 :155-163 → live :163-171（2496 屏障⑤ Throwable 宽度 + 注释 +8 行）；C6-002 subscribe :72-80 零漂移；C8-002 `invokeSubscription` :213 起零漂移（:232 catch 已由 2496 拓为 Throwable——面宽变化随本条登记，非漂移）；C6-006 cancel 审计载 :219-229 → live :227-237；C3-014 wrapper.stop 审计载 :204-215 → live :245-286（2498 有界终结块插入）；reader close 审计载 :294-312 → live :295-312，且 **:302-307 的 runner-join typed 超时抛错自 plan 2343 即在**（审计 C3-014 证据片段省写未载——不影响其本体主张：join 成功 ≠ 引擎终止，残余面裁定按边界核对 1 ②收窄为引擎级终止性验证）；function run finally 审计载 :308-316 → live :297-316（`unregisterFailureListener` :298 行计入）、cancel :330-344 → :331-344、truncateForDrain :346-367 → :352-367；C3-004 start 重算审计载 :56-70 → live :63-67、restoreState :113-135 → :130-133；LocalSourceCoordinator :160-163 零漂移。本 Baseline 已按 live 回读值书写；执行时再以 live 为准。
5. **C3-014（使用面停止契约）与 4.1-MA-09（wrapper 生命周期族，P3）的同文件续作边界**（4.1 merge-adjudication §七-3「使用面/本体面双轨互补，修复批次对齐，非重复」）：本批承接 C4-005/C4-006 并入面（终结保证 + 启动守卫）+ ISE 处理 + 终止性验证；MA-09 面（stop 限时 join 后 stuck WARN 形态细化、worker 死亡路径幂等 `engine.close()` 回收、start 失败复位 `startRequested`、start-after-stop one-shot 契约声明）**不默认扩批**——若本批 Phase 3 同文件 diff 以最小增量自然覆盖某子面，随批落账并 cra-index 注记；否则 MA-09 维持 P3 最小 diff 归并轨（successor 登记见 Deferred 节）。双向注记落 cra-index 两行，防两批各修一半（对齐 plan 2498 勘误 4 先例）。
6. **1.2-C3-004 相邻面对账**：WI1.1 C1-001/C2-001 指向同一枚举器的线程安全面（裸 LinkedHashMap CME），与恢复分配逻辑缺陷互异——1.2 审计已裁「相邻非重复，cross-ref 不归并」；本批不扩批，对账注记归 Phase 5 回填。

## Goals

- 六项成员全部修复并各带 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项先红后绿或变异/回退论证（E2）——线程归属/终止性/双投递类断言以修复前缺陷基线跑红为标准动作。
- Debezium 双轨收敛：幽灵引擎不可再现（stop 契约「已发出」→「已终止」，ISE 不得吞、四停止面终止性可验证）；事件屏障两极同禁（畸形事件不炸流 + 订阅者异常不静默吞 offset 推进），isStarted 不再撒谎；订阅生命周期注册/注销身份一次收敛（MA-05 三面归一 + C6-006 同 map 同 diff）。
- Local 交付语义闭合：消费失败第一层 `onConsumeFailure` 派发在位，三层失败架构（SPI 回调 → 无 DLQ substrate 声明 → `MessageConsumeFailureBridge`）Local 面连通；重投立场显式裁决落档。
- 恢复语义闭合：`DebeziumTableSplitEnumerator` 恢复后每表恰一桶/恰一引擎（跨 reader 双登记不可再现）。
- B1-05 三案统一 P1 落账确认：cra-index 三行（L266/267/268）终裁注记（family-clustering §三 L516 口径）+ 4.1 §五-3「同批调整」完成确认。
- R4-24 REGISTRY 契约一次裁决设计项落 `ai-dev/design/concurrency/`（三选一定稿 + 软钉#5 + WI 2.5 表态 + C3-014 一致性声明；R4-24 实现契约，禁止各修一半）。
- 使用面/本体面批次对齐落账（batch exit criterion）：R16 族内 1.2 使用面（C3-014）与 4.1 本体面（MA-02/MA-05/C6-006）同批收敛；MA-09 同文件续作边界注记、R17 族（4.3-MA-09/5.2-I-02）与 R18 族（3.1-MA-11）批次对齐注记、R2-16 consumeAwaitTimeout 契约互注落 cra-index。
- Owner doc 随批更新：roadmap 8.9 三份（`docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/03-modules/nop-message.md`、`docs-for-ai/03-modules/nop-stream-cdc-cookbook.md`）核对与更新。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 零触碰声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不承接 R4-24 十三项本体工单**（G62：4.1-MA-01/03/04/06/07/10、C1-002、C1-012、C3-004[4.1 Pulsar sendAsync]、C3-005、C3-006、C8-005、C8-007）——本批只交付 REGISTRY 契约一次裁决**设计项**；REGISTRY 本体清理（C5-001/C6-004/C1-002 实施 + 软钉#5 改写）、MA-01 consumeAwaitTimeout、MA-10 FAILURE_LISTENERS 注册协议（键升维/条件 remove/stop 配对回收）均归 R4-24。本批新增 listener 接线按现行 API 形态使用（`DebeziumCdcSourceFunction:248/:298` finally 注销合规先例），不改注册协议。
- **不回退 plan 2496 Phase 4 产物**（C3-009 七屏障 Throwable 宽度、Error 分支失败上报路、`runEngineWorker` 抽取、`nop.message.consumer-failed` 日志键、`TestDebeziumErrorBarriers` 3 条 + `TestDebeziumEngineWrapperFailureListener` 4 条 + `TestDebeziumEngineDedicatedCarrier_C4_004` 2 条（2496/2498 产物合计 9））与本批同文件异面续作处的绝缘语义。
- **不回退 plan 2498 Phase 1 产物**（per-engine 专用载体 `engineExecutor`、5s 有界终结预算、既有 close 链）——C3-014 残余面为载体前提上的增量修复，非载体重做。
- **不承接 4.1-MA-09 本体**（C6-005/C1-011/C6-007，P3 最小 diff 归并轨——边界见勘误 5；最小增量自然覆盖时随批落账并注记，不主动扩批）。
- **不承接 R17/R18 族兄弟成员**：5.2-I-02（→R3-02）、4.3-MA-09（→R4-26）、2.5-MA-02（→R2 系）、3.1-MA-11（→R2-16）——本批只落批次对齐注记。
- **不做 nop-stream 侧 C3-014 同族扩展面**：1.2-MA-09~18（连接器 MA 束 → R2-08/R4-03）、1.2-C3-006/MA-10 双轨本体（→R4-24/R4-04 系）——本批 stop 契约修复以 Debezium 引擎宿主为限，不做同族清扫扩批。
- 不触碰 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物（零触碰声明见 plan 头部）。
- 不做 Deferred 节所列项（慢订阅者背压形态改造、MA-09 主动承接等）。

## Scope

### In Scope

- `nop-message/nop-message-debezium`（DebeziumEngineWrapper handleDebeziumEvent 屏障 + stop ISE 处理 + 终止性契约；DebeziumMessageSource 订阅生命周期/注销身份/失败可见性）。
- `nop-message/nop-message-core`（LocalMessageService 第一层失败回调 + 三层架构闭合）。
- `nop-stream/nop-stream-connector-debezium`（DebeziumCdcSourceReader/DebeziumCdcSourceFunction 四停止面终止性验证 + DebeziumTableSplitEnumerator 恢复双登记）。
- 设计文档：`ai-dev/design/nop-message/backend-semantics-design.md` 范围扩展（毒事件 endpoint 裁定）+ `ai-dev/design/concurrency/` 新增 REGISTRY 生命周期契约文档（R4-24 实现契约）。
- Owner doc：roadmap 8.9 三份核对与更新；cra-index 台账回填（六成员行 + B1-05 三案注记 + 勘误 1/2/3/5 对齐注记）；roadmap 8.9 → done。

### Out Of Scope

- Non-Goals 所列各批工单；R4-24 实施面、R2-16 实施面、R2-08/R4-03 连接器 MA 束、R17/R18 族兄弟成员；MA-09 主动承接（仅最小增量自然覆盖时随批）。

## Execution Plan

> 设计裁定面先行（Phase 1——毒事件 endpoint 行为与 REGISTRY 契约两项 Decision，分别为 Phase 2 修复与 Phase 3 C3-014/R4-24 的实现契约）→ 4.1 修复分片（Phase 2，nop-message）→ 1.2 修复分片（Phase 3，nop-stream + nop-message 载体续作）→ 全量验证 + owner doc + 对齐注记（Phase 4）→ closure audit + 台账收口（Phase 5）。分片间依赖：Phase 2/3 依赖 Phase 1 两项裁定；Phase 2 与 Phase 3 无相互硬依赖、可按序执行；每分片独立提交（提交纪律：每模块一提交，commit message 声明工单 ID 与 plan 号）。

### Phase 1 - 设计裁定面：毒事件 endpoint 行为 + R4-24 REGISTRY 契约一次裁决

Status: completed（设计裁定两文档已落盘、本 Phase Exit Criteria 全勾；独立 draft review 由协调方派发中——评审通过并处置发现前，Phase 2/3 不得消费裁定条款，本 Phase 不得置 completed）
Targets: `ai-dev/design/nop-message/backend-semantics-design.md`（范围节扩展 + 失败终点节补 CDC 面）、`ai-dev/design/concurrency/registry-lifecycle-contract.md`（新增 REGISTRY 生命周期契约设计文档，内容四要素见下）

- Item Types: `Decision`

- [x] 毒事件 endpoint 行为裁定（4.1 merge-adjudication §五-7「工单内前置设计裁定」）：C3-002（存活极性：屏障须有，禁整体炸流）与 C8-004（投递极性：屏障不得吞后静默推进 offset）两极同禁约束下，Debezium 路径失败行为二选一定稿——(a) 跳过+计数+上报+`ConsumeFailure` 派发（该事件 at-most-once，可见性保底）或 (b) 中止本轮投递换 at-least-once 重放（故障订阅者阻塞引擎线程的取舍须随裁定显式声明）；`DebeziumMessageSource.dispatchEvent` 失败分支与 `handleDebeziumEvent` 屏障共用同一 endpoint 语义。裁定记录扩入 `backend-semantics-design.md`（范围节覆盖 nop-message-debezium——补齐审计证实的范围缺口，SSOT 不另起新交付语义文档）；C10-001 建议测试场景 (2)（毒事件两极）为该裁定的唯一行为锚定，Phase 2 测试消费本结论【2026-10-25 落档：终裁 (a) 案——§一结论 8 + §3.3.3 + §四两条拒绝；范围节补 nop-message-debezium（live 核实落档前零该模块）；(b) 案拒绝理由 = debezium-embedded 回调异常逃逸即引擎终态死亡（与存活极性冲突）+ 屏障内自建重投违反 nop-retry 唯一重试机制约束】
- [x] R4-24 REGISTRY 契约一次裁决（设计项；R4-24 硬依赖，batch-proposal R4-24 行依赖列声明）：`NopStreamOffsetBackingStore` 生命周期契约三选一定稿（stop-clear vs TTL/evictIdle vs 条件移除——对「同名重启续传」期望影响相反）+ 软钉#5（`setOffsets` REPLACE 契约，`TestNopStreamOffsetBackingStore:90-98` 现状钉）一并裁决 + WI 2.5 消费面表态（C3-008/CL-010/WI 2.5 C1-005 互斥分层维持声明）+ **与 C3-014 幽灵引擎修复的一致性声明**（ghost 歼灭后同名 registry 桶互覆残余的语义归宿、与 C3-004 恢复语义的衔接）。设计文档四要素齐备；覆盖对象按勘误 2 权威口径（C5-001+C6-004+C1-002 三单，非「MA-03/MA-04/C1-002」）；R4-24 十三项按本裁决实施，禁止各修一半【2026-10-25 落档：`ai-dev/design/concurrency/registry-lifecycle-contract.md`——终裁 stop 侧配对释放 + 绑定代条件移除（同名重启续传唯一合法路径 = 显式 checkpoint 恢复）；软钉#5 维持 REPLACE 不改写、仅增恢复声明窗守卫；WI 2.5 互斥分层维持；C3-014 双层防御一致性 + C3-004 衔接闭环】
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 毒事件 endpoint 裁定已在 `backend-semantics-design.md` 落档（范围节覆盖 nop-message-debezium、两极同禁语义成文），Phase 2 测试可直接引用其行为条款
- [x] REGISTRY 契约设计文档 `ai-dev/design/concurrency/registry-lifecycle-contract.md` 已落档，四要素齐备（三选一结论 / 软钉#5 裁决 / WI 2.5 表态 / C3-014 一致性声明），覆盖对象为勘误 2 权威口径
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（本 Phase 为 ai-dev 设计文档产出；`docs-for-ai/` 三份 owner doc 的受影响面归 Phase 4 统一核对——去向前向已显式登记）



> **Phase 1 回填注（2026-10-26）**：两裁定文档落盘（提交 ed91055920）+ 评审 agent_d082ac70（D12 M-1/m-1/m-2；REGISTRY 原样通过）+ 修订 agent_28bc4b02 + 增量复核**通过进入实现**（零遗留）。m-2 回填指针义务登记归 Phase 4（R4-24 批末向 registry-lifecycle-contract.md §3.3 回填守卫粒度裁定一行，防第二事实入口）。### Phase 2 - 4.1 修复分片：Debezium 本体面三工单 + Local 失败回调（nop-message）

Status: planned
Targets: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java`（:72-80 subscribe、:163-171 dispatchEvent、:176-188 stop、:227-237 cancel、:32 map 键）、`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java`（:202-207 handleDebeziumEvent）、`nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java`（:213-240 invokeSubscription）、两模块测试目录

> 续作纪律：本分片四工单允许分会话续作——每工单独立提交（commit message 带对应工单 ID 与 plan 号），每工单完成即时勾选对应 checklist 项与 Exit Criteria，不跨面积压未勾选项；MA-02/MA-05/C6-006 同文件同 diff 面（DebeziumMessageSource map/锁改造）按「同站点一个 diff」纪律合并或分提交并声明关系，不得拆散三面归一。

- Item Types: `Fix`（四工单均为已确认 live defect，不得写成 Feature）、`Decision`（C8-002 重投立场显式裁决）、`Proof`（回归测试）

- [ ] 4.1-C3-002+C8-004（MA-02 配对）：`handleDebeziumEvent` 补 per-event 屏障（convert + consumer 统一）+ 毒事件 failure endpoint 按 Phase 1 裁定落地（两极同禁）+ `DebeziumMessageSource` 启动时注册 failure listener 并把终态死亡反映到 started 标志或健康探针（isStarted 不撒谎）+ dispatchEvent 失败分支接入同一 endpoint 语义；listener 接线按现行 API 形态（不改 MA-10 注册协议）
- [ ] 4.1-C6-002（MA-05 三面归一，勘误 1）：`subscriptions.put` 移至 start 成功后（或 catch remove 后重抛）+ subscribe/cancel「增删+空判定+启停」复合操作统一入同一锁 + stop 后 subscribe typed 拒绝（`ERR_DEBEZIUM_ENGINE_START_FAILED` 族）；C3-003 交错 A/B 与 C1-006 并入子面随同 diff 闭合
- [ ] 4.1-C6-006：注销身份改造按 WI23 同法（`values().remove(this)` 或 `Map<Subscription, ·>`）——与 MA-05 同 map/锁改造同 diff；重复 action 订阅语义（WARN 声明或 typed 拒绝）随实现期对齐 WI23 先例
- [ ] 4.1-C8-002：Local catch 中构造 `ConsumeFailure` 并 guarded dispatch `onConsumeFailure`（对齐 `KafkaConsumeTask:631-643` 形态，回调自身异常不杀死发送者/重投线程）+ 三层架构闭合核验（`MessageConsumeFailureBridge` 可收到 Local 事件）+ 重投与否按设计 §3.9 立场显式裁决（结论落 owner doc，禁止静默不裁决）；不回退 2496 Throwable 宽度与 `nop.message.consumer-failed` 日志键
- [ ] 回归测试（rule 25，测试名带 `_C3_002`/`_C8_004`/`_C6_002`/`_C6_006`/`_C8_002` 标识）：①**TestDebeziumMessageSource 新建**（C10-001 随批义务，四场景：幽灵订阅不残留[启动失败后无条目、二次订阅不双投递]/毒事件两极[按 Phase 1 裁定断言 endpoint 行为]/并发 subscribe×stop 交错 A/B 断言/同引用双订阅注销不误停引擎）；②handleDebeziumEvent 屏障断言（畸形 payload 不炸流 + engine 死亡时 failure listener 必达 + started 反映终态）；③dispatchEvent 失败分支按裁定断言（不静默推进 offset 语义的行为钉）；④Local 失败派发断言（handler 抛异常 → `onConsumeFailure` 收到 ConsumeFailure + bridge 事件可见——C10-006「失败派发」维度现状预期红，随批转绿）+ 回调自身抛异常不杀死发送者线程；⑤`TestDebeziumErrorBarriers` 3 条 + `TestDebeziumEngineWrapperFailureListener` 4 条 + `TestDebeziumEngineDedicatedCarrier_C4_004` 2 条（2496/2498 产物合计 9）零回归（2496 屏障/失败监听零回退钉 + 2498 载体零回退钉）
- [ ] **接线验证**（rule 23）：真实订阅链（`subscribe` → `startEngineIfNeeded` → wrapper start）上断言 listener 注册/死亡通知连通（engine 死亡 → source started 翻转/探针可见）；Local 真实 send 链上断言失败回调派发
- [ ] **无静默跳过**（rule 24）：typed 拒绝路径（stop 后 subscribe）、失败派发路径、重投裁决路径均显式失败或可观测，不吞异常不静默返回
- [ ] **提交纪律**：nop-message 分提交或按实际 diff 面单提交并声明关系，commit message 带对应工单 ID 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 4.1-C3-002+C8-004 / C6-002（含 MA-05 三面归一）/ C6-006 / C8-002 四工单 landed：各带 ≥1 条 focused 回归测试且测试名可辨识工单；C10-001 四场景 TestDebeziumMessageSource 随批交付
- [ ] 必红证据（E2）：至少 C8-002（修复前 onConsumeFailure 零派发断言红——C10-006 维度现状红）、C6-002（修复前幽灵订阅残留断言红）、MA-02（修复前毒事件两极断言红：畸形事件炸流极性 + 吞后静默推进 offset 极性 / 幽灵双投递断言红）与 C6-006（修复前同引用双订阅 cancel 误停活跃引擎断言红）四项先红后绿或变异/回退论证留痕（快照 `_tmp/phase2-*.log` 含 RED 与 GREEN 两段）；MA-02/C6-002/C6-006 同 map/锁改造共享测试夹具，红基线可在共享夹具改造前统一取证，留痕中显式声明共享夹具的红论证方式；其余工单按 E2 逐条可复现口径执行
- [ ] 接线验证证据登记（哪条断言证明运行时生效）
- [ ] 入口到出口断言（真实 wrapper 通道）：在专用载体上经真实 wrapper 驱动 `start → handleDebeziumEvent → 毒事件 endpoint → failure listener 必达` 的入口到出口断言在位（扩 `TestDebeziumEngineDedicatedCarrier_C4_004` 既有半成品形态）——本 Phase 以该真实 wrapper 全链断言为组件+接线级闭环的行为证据
- [ ] `./mvnw test -pl nop-message -am` BUILD SUCCESS
- [ ] Owner doc 裁定：`nop-message.md` 消费失败契约/Debezium 订阅契约受影响面核对（含 C8-002 重投立场声明落点）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 4 统一但须在本 Phase 显式登记去向
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 1.2 修复分片：C3-014 残余面 + C3-004 恢复双登记（nop-stream + nop-message 载体续作）

Status: planned
Targets: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java`（stop :245-286、runEngineWorker :224-240）、`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java`（close :295-312）、`DebeziumCdcSourceFunction.java`（run finally :297-316 / cancel :331-344 / truncateForDrain :352-367）、`DebeziumTableSplitEnumerator.java`（start :63-67 / restoreState :130-133）、两模块测试目录

> 续作纪律：本分片允许分会话续作——提交粒度按「残余面组」（如 C3-014 残余①ISE 不吞组 / ②四停止面终止性验证组 / ③④worker 守卫+shutdownNow 升级组 / ⑤终结 latch/终止可观测契约组 / C3-004 枚举器恢复组）独立提交（commit message 带对应工单 ID 与 plan 号），每组完成即时勾选对应 checklist 项与 Exit Criteria，不跨面积压未勾选项；次序约束：⑤（终结 latch/终止可观测契约）先行落地或与②（四停止面终止性验证）同 diff 落地——②的轮询消费⑤产出的终止性信号，⑤未在位前②不得声明闭环。

- Item Types: `Fix`（两项均为已确认 live defect）、`Proof`（回归测试）

- [ ] C3-014 残余①：`wrapper.stop()` 对 STARTING_TASKS 窗口依赖方 ISE 不再吞——有预算重试（等状态离开后重试 close 直至 STOPPED）或 typed 上抛，形态随实现期对齐 debezium-embedded 3.7 状态机契约；`stopped` 一次性守卫与重试路径协调（重试期间不永久锁死后续 stop）
- [ ] C3-014 残余②：四个 in-scope 停止面（reader close / function run finally / cancel / truncateForDrain）`source.stop()` 后增加引擎终止性验证——有限预算内轮询，未终止即 typed 失败或 ERROR 显式 ghost 风险声明（「已发出」→「已终止」）；runner-join 既有 typed 超时（:302-307）零回退
- [ ] C3-014 残余③④⑤：`runEngineWorker` stopped 守卫（C4-006——stop 已完成后 worker lambda 不再驱动引擎）；stuck 引擎载体 `shutdownNow()` 升级（2498 预留面，触发条件与 5s 预算衔接）；终结 latch/终止可观测契约闭合（C4-005 残余——终止性信号对停止面可查询）
- [ ] **载体零回退钉**：不改动 2498 载体形态（`engineExecutor` 惰性重建 / `STOP_TERMINATION_BUDGET_SECONDS` / finally 自关停）——`TestDebeziumEngineDedicatedCarrier_C4_004` 2 条零回归
- [ ] 1.2-C3-004：恢复双登记闭合（成员 6 修复方向二选一，对齐 `FileSplitEnumerator` 恢复形态）——恢复态与新鲜份额求差集，每表恰一桶
- [ ] 回归测试（rule 25，测试名带 `_C3_014`/`_C3_004` 标识）：①STARTING_TASKS 窗口 stop 场景（注入启动期阻塞 → 窗口内 stop → 断言引擎真实终止、无 ghost：引擎线程有界消亡 + registry 桶不再被覆写——修复前基线跑红）；②终止性验证断言（未终止时 typed 失败或 ghost 声明可见）；③worker stopped 守卫断言（stop 后完成的 worker 不复活引擎）；④shutdownNow 升级断言（stuck 引擎有界强制终结）；⑤枚举器恢复测试（构造「部分 reader 注册后崩溃」快照 → restore → start → 断言每表恰一桶、两 reader 分配无交集——修复前双桶断言红）；⑥既有 connector 测试（`TestDebeziumCdcSource`/`TestDebeziumCdcSharding`/gated 真库套件除外[不触发 gated]）零回归
- [ ] **端到端验证**（rule 22）：以既有真实链路测试为锚（`TestDebeziumCdcCheckpoint` 快照/恢复 round-trip 套件绿）——本批新增面在 enumerator/wrapper 级 focused 测试覆盖入口到出口（stop 链：cancel→msgSource.stop→wrapper.stop→engine 终止→验证可查询；恢复链：snapshot→restore→start→assignSplits 无重复），gated 真库 E2E 不在本批触发范围
- [ ] **接线验证**（rule 23）：真实 stop 链（`DebeziumCdcSourceReader.close` → `embeddedFunction.cancel` → `source.stop` → wrapper）上断言终止性验证生效（ghost 场景可观测）；真实 coordinator 时序（restoreState→start）上断言恢复分配
- [ ] **无静默跳过**（rule 24）：ISE 重试预算耗尽、终止性验证超时、shutdownNow 升级路径均显式失败或 ERROR 可见，不吞异常
- [ ] **提交纪律**：nop-message（wrapper 面）与 nop-stream（停止面 + 枚举器）各一提交（或按实际 diff 面单提交并声明关系），commit message 带 `1.2-C3-014`/`1.2-C3-004` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 1.2-C3-014 残余五项 landed（ISE 不吞 + 四停止面终止性验证 + worker 守卫 + shutdownNow 升级 + 终结契约）且 1.2-C3-004 landed：各带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 必红证据（E2）：至少 C3-014 ghost 场景断言（STARTING_TASKS 窗口 stop 后引擎存活）与 C3-004 双桶断言（恢复后同表双份额）两项先红后绿或变异/回退论证留痕（快照 `_tmp/phase3-*.log`）
- [ ] 接线验证证据登记（真实 stop 链 / restore→start 时序）
- [ ] `./mvnw test -pl nop-stream -am` BUILD SUCCESS；`./mvnw test -pl nop-message -am` BUILD SUCCESS（wrapper 面 diff 通道）
- [ ] Owner doc 裁定：`nop-stream-connectors.md` 停止契约节（2498 落的「引擎线程模型与终结预算」节扩终止性契约）与 `nop-stream-cdc-cookbook.md` 恢复语义节核对——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 4 统一但须在本 Phase 显式登记去向
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 全量验证 + owner doc 三份 + 对齐注记

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/03-modules/nop-message.md`、`docs-for-ai/03-modules/nop-stream-cdc-cookbook.md`（roadmap 8.9 Owner Doc 列三份）；`ai-dev/audits/cra-index.md`（对齐注记组）

- Item Types: `Proof`、`Decision`（对齐注记）

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2496~2502 同支路先例）
- [ ] 批行验证命令（E3）逐条登记：`./mvnw test -pl nop-stream -am`、`./mvnw test -pl nop-message -am`——每条登记模块/测试数/退出码/快照路径（引用 Phase 2/3 终态快照或终态补跑）
- [ ] 零触碰确认（E4）：本批产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记）；2496 屏障面与 2498 载体面零回退实证（对应测试套件全绿 + 设施文件 diff 核对）
- [ ] Owner doc 更新（E5）：三份逐份核对与更新（内容源 = 各 Phase 修复面：Debezium 订阅/交付语义契约[message]、停止契约终止性[connectors，扩 2498 既有节]、恢复分配语义[cookbook 恢复节]），或逐份显式登记 `No owner-doc update required` + 理由；触达模块均有 03-modules 条目（无缺口预期，以实际 diff 为准核对）
- [ ] **R2-16 对齐互注**（4.1 merge-adjudication §五-6 + batch-proposal R2-16 行 L90 / R4-24 行 L139）：cra-index 相关成员行追加「consumeAwaitTimeout 契约对齐」注记——C8-002 落地的 Local 失败派发契约与 4.1-MA-01（R4-24）/3.1-MA-11（R2-16）的 consumeAwaitTimeout 契约对齐义务互指（R2-16 实施时按 4.1 §五-6 对齐）；batch-proposal R2-16 行（L90）既有「MA-11 与 R1-08 consumeAwaitTimeout 契约对齐」只读核对登记（不改 batch-proposal，历史裁定档案）
- [ ] **m-2 回填指针（设计评审移交）**：向 `ai-dev/design/concurrency/registry-lifecycle-contract.md` §3.3 回填一行「守卫粒度=桶/绑定代级，裁定见 backend-semantics-design.md §五关系行」（防第二事实入口）
- [ ] **批次对齐注记组**：①MA-09（勘误 5）与 1.2-C3-014 的同文件续作边界注记落 cra-index 两行（含「最小增量自然覆盖」发生与否的结论）；②R17 族对齐注记（4.3-MA-09 → R4-26、5.2-I-02 → R3-02：注册/注销配对修复与 MA-05 落地形态对齐义务）；③勘误 2 引用勘正注记（REGISTRY 裁决覆盖对象 = C5-001/C6-004/C1-002）；④勘误 3 防混注记（链#21「MA-09」实为 MA-05）在 cra-index R1-08 相关行落账
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（受影响模块全集与 Closure Gates 一致：nop-message、nop-stream）退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] 三份 Owner Doc 核对与更新结论逐份登记（含 `No owner-doc update required` 声明者）；check-doc-links --strict 退出码 0
- [ ] R2-16 互注 + 批次对齐注记组四项全部落 cra-index（可核验）
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - closure audit + 台账收口

Status: planned
Targets: 本 plan、roadmap 8.9 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）；重点抽查「stop 契约终止性运行时真实生效」（ghost 场景断言）、「毒事件 endpoint 两极行为与 Phase 1 裁定一致」、「2496/2498 产物零回退」
- [ ] cra-index 台账回填：P1 五行（L150 `C3-014（MA-07）`、L152 `C3-004`、L266 `nop-message-C3-002+C8-004（MA-02 同 diff 配对）`、L267 `nop-message-C6-002（MA-05 主）`、L268 `nop-message-C8-002`）+ P2-G63 行 C6-006 成分状态收敛（open → 已修/本批，携修复提交引用）；**B1-05 三案统一 P1 落账注记**（三行终裁引用 family-clustering §三 L516 + 4.1 §五-3 同批调整完成确认 + 「结构性免疫」触发条件限定口径）随行落账
- [ ] roadmap 8.9 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2503-cra-phase8.9-r1-08-debezium-delivery.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 六成员行状态全部收敛 + B1-05 三案注记 + 对齐注记组可核验、roadmap 8.9 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 6 项成员全部修复收敛：4.1-C3-002+C8-004（屏障两极同禁 + isStarted 诚实）、4.1-C6-002（MA-05 三面归一）、4.1-C6-006（注销身份改造）、4.1-C8-002（第一层回调 + 三层架构闭合 + 重投立场显式裁决）、1.2-C3-014（残余五项：ISE 不吞/终止性验证/worker 守卫/shutdownNow 升级/终结契约）、1.2-C3-004（恢复每表恰一桶）
- [ ] B1-05 三案统一 P1 落账确认完成（cra-index 三行注记 + §五-3 同批调整确认）；使用面/本体面批次对齐落账（R16 族同批收敛 + MA-09/R17/R18 对齐注记 + R2-16 consumeAwaitTimeout 互注）
- [ ] R4-24 REGISTRY 契约一次裁决设计项落档（四要素齐备，覆盖对象为勘误 2 权威口径）；毒事件 endpoint 裁定落 `backend-semantics-design.md`
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项有先红后绿或变异/回退论证（E2：ghost 场景 + 双桶断言 + 失败派发红 + 幽灵订阅红等）
- [ ] C10-001 四场景 `TestDebeziumMessageSource` 随批交付；C10-006「失败派发」维度转绿
- [ ] 2496 屏障产物与 2498 载体产物零回退实证：`TestDebeziumErrorBarriers` 3 条 + `TestDebeziumEngineWrapperFailureListener` 4 条 + `TestDebeziumEngineDedicatedCarrier_C4_004` 2 条（2496/2498 产物合计 9）、`TestPeriodicTaskInsulation` 全绿 + 设施文件 diff 核对
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令（`-pl nop-stream -am` / `-pl nop-message -am`）BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：roadmap 8.9 三份逐份核对与更新（或 `No owner-doc update required` 声明）；缺口声明核对登记（E5）
- [ ] 零触碰确认（E4）：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/相邻面，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）停止契约终止性与失败回调派发在运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2503-cra-phase8.9-r1-08-debezium-delivery.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 6 成员行收敛 + roadmap 8.9 → done（E6）

## Deferred But Adjudicated

### 4.1-MA-09 wrapper 生命周期族（C6-005/C1-011/C6-007，P3）

- Classification: `out-of-scope improvement`（P3 最小 diff 归并轨）
- Why Not Blocking Closure: 非 R1-08 成员清单（batch-proposal L69 权威）；C3-014 残余面裁定（勘误 5）已划界——本批只修停止契约终结保证面；MA-09 的 quiesce join 细化/死亡回收/one-shot 契约/start 失败复位为独立机制面。若 Phase 3 同文件 diff 以最小增量自然覆盖某子面，随批落账并 cra-index 注记（届时本项收窄为剩余子面）。
- Successor Required: `yes`
- Successor Path: P3 最小 diff 归并轨（4.1 merge-adjudication §六「可与同文件 P2 工单同 PR 顺带」条款）；cra-index 回填时登记实际归属

### 慢订阅者阻塞 Debezium 引擎线程（CDC 背压形态）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 4.1 审计 C8-004 现状节明示「慢订阅者阻塞 engine 线程是 CDC 的正确背压形态（保留）」；本批毒事件 endpoint 裁定若取「中止本轮」支路则该阻塞成为声明语义，取「跳过+上报」支路则失败面已闭——两支路下均无未裁定残留。
- Successor Required: `no`（随 R2-08 连接器批既有工单面观察）

## Non-Blocking Follow-ups

- **R4-24/R2-16 对账**：Phase 5 台账回填时核对 REGISTRY 实施面（R4-24 十三项）与 consumeAwaitTimeout 契约（R2-16 MA-11 + R4-24 MA-01）各归其批，防漏防重（沿 plan 2498 先例）。
- **1.2-C1-001/C2-001 相邻面对账**：同枚举器线程安全面（R2-08/R2-09 归属）对账注记随 Phase 5 回填落 cra-index（勘误 6）。
- **C10-002 保护面顺带覆盖注记**：本批 Phase 2/3 新增测试部分覆盖 C10-002（wrapper 启停/通知协议零测试）场景——回填时登记实际覆盖面，其保护本体（MA-10/MA-09）归 R4-24/P3 轨。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - <<每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）>>
  - <<每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）>>
  - <<`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0>>
  - <<Anti-Hollow 检查结果；`scan-hollow-implementations.mjs` 退出码为 0>>
  - <<Deferred 项分类检查：确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
