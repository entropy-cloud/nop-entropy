# 01 WI0a D10 与 D11 放行裁定（emit 两档与 ACCUMULATING 声明）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI0a 行、前置裁定表 WI0a 行、门控判据表 D10/D11 两行）；`ai-dev/design/nop-stream/window-failfast-decisions.md`（修订对象）；`ai-dev/analysis/streaming-sql-survey/2026-10-07-kafka-streams-analysis.md` 与 `ai-dev/analysis/streaming-sql-survey/2026-10-07-ksql-analysis.md`（B2/B3 参考形态，转引口径）
> Related: `ai-dev/plans/nop-stream-sql/05-wi0d-window-failfast-decisions.md`（2026-10-02 首裁）；ai-dev/plans/nop-stream-sql-enhancement/03-wi38-accumulation-mode-wiring-fix.md（D10 生效前提，未来交付物）、04-wi37-emit-two-tier.md（D11 实施，未来交付物）——2026-10-08 事实性更正：WI38 先于 WI37 执行，plan 编号按起草序 03/04
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」委托 ZCode 代理按 roadmap 建议项与 survey 结论执行；与 2026-10-02 首裁同一委托模式）

## Purpose

裁定两条窗口 fail-fast 边界并把结论落档 `ai-dev/design/nop-stream/window-failfast-decisions.md`（原位修订 D10/D11 两节，保留首裁日期与修订链）：

- **D11**：trigger 声明是否放行为 emit 两档（Kafka Streams EmitStrategy 形态），并随裁三项——trigger 注册表前置存废、triggerId fail-fast 是否保留、emit 挂点。
- **D10**：ACCUMULATING 是否随 WI38 修复一并开放声明，生效前提与门禁边界。

WI37（emit 两档实施）与 WI38（FU-8 accumulationMode 接线修复）依赖本计划结论。

## Current Baseline

以下锚点为 2026-10-08 在本分支 HEAD `55aa962595` 实测（相对 2026-10-02 首裁时行号已漂移，本节为当前权威口径）：

- **D10 build 期闸门**：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java` 的 `failFastOnUnsupportedWindowStrategy`（:231-249）在 :241-248 拒绝非 DISCARDING 的 `accumulationMode` 声明（`ERR_STREAM_WINDOW_ATTR_UNSUPPORTED`）。该声明值**从未被消费传递**：`buildWindow`（:156-222）全程不读 `strategy.getAccumulationMode()`。
- **D11 双层闸门**：同文件 strategy 级 :233-238（`strategy.getTriggerId() != null` 拒绝）与 window 节点级 :258-263（`failFastOnUnsupportedWindowNodeAttrs`）。triggerId 在引擎主代码零消费点（仅生成模型存取器与 fail-fast 门）。
- **FU-8 结构缺口**（首裁后 WI10 audit 发现，本批 WI38 承接）：xdef 声明 DISCARDING（默认值）的模型构建出的算子恒为 ACCUMULATING——`WindowedStreamImpl`（nop-stream-core）无 accumulationMode 字段（字段全集 :53-84）；`IWindowOperatorFactory` 四代 create* 重载均无该参数（:22-246）；`WindowOperatorFactoryImpl.configureBuilder`（nop-stream-runtime，:350-378）从不设值；`WindowOperatorBuilder` 有 setter（:110-113）但链路无人调用；`WindowOperator` 缺省 ACCUMULATING（:492）。core `WindowingStrategy.java` :52（及 :60 无参构造委托）缺省 DISCARDING，与算子 :492 缺省 ACCUMULATING **两层缺省相反**。
- **WindowOperator 运行时门**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java` :590-596 对 `ACCUMULATING_AND_RETRACTING` 开期 fail-fast（spec-only）；:1388-1400 仅 DISCARDING 在 fire 后清空窗口内容（event-time + allowedLateness>0 时 skipPurgeForLateness）。
- **trigger 机制现状**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/` 已有 Trigger/TriggerResult（FIRE/FIRE_AND_PURGE 等）/EventTimeTrigger（缺省）/ProcessingTimeTrigger/PurgingTrigger/CountTrigger/ContinuousEventTimeTrigger/ContinuousProcessingTimeTrigger/ContinuousIntervalTrigger/DeltaTrigger/ProcessingTimeoutTrigger；`WindowedStream.trigger`（:80）接口点与 factory create* trigger 参数、`WindowOperator` per-trigger accumulator（:355-358）均已存在。**缺的只是「逐元素 FIRE」档位与声明面**。
- **首裁口径**（window-failfast-decisions.md §2/§3，2026-10-02）：D10 保持 fail-fast（理由=scope 收敛，非语义不相容；原文自证「ACCUMULATING 运行时已支持」，并明示「后续若放行 ACCUMULATING 须先修订本裁定」）；D11 保持 fail-fast（原文前置「后续放行须先建 trigger 注册表并修订本裁定」）。
- **xdef**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef` strategy 属性全集含 `triggerId="string"`、`accumulationMode="!enum:...AccumulationMode=DISCARDING"`（:48-56）；window 节点无 accumulationMode 属性（:190-194）。

## Goals

- 修订 window-failfast-decisions.md D10/D11 两节为放行结论，要素齐全（选项、结论、理由与代码证据、负责人、日期、受影响 WI、生效前提）。
- 裁定结论（受托 owner 按 survey B2/B3 建议与引擎现状作出）：
  - **D11 = 放行 emit 两档**。形态锁死 Kafka Streams EmitStrategy 的 ON_WINDOW_UPDATE / ON_WINDOW_CLOSE 两档（`<sql>` 面 emit 属性取值 `update|close`）。随裁三项：
    1. **trigger 注册表前置作废**——两档是封闭枚举而非自由 trigger 体系，无需注册表；D11 原文前置条款随本裁定废止。
    2. **triggerId fail-fast 保留**——不引入自由 trigger 声明面；`triggerId` 属性与既有双层门维持原样（未实现声明仍显式拒绝，不得静默）。
    3. **挂点 = `<sql>` 顶层属性**（编译产物落 windowingStrategy 声明），非窗口/聚合节点属性——`<sql>` 是模型级生成器唯一入口（D8 已裁），声明面随查询整体下发；WI37 实施时在 stream.xdef strategy 增 emit 属性承载。
    对首裁「部分值未经验证」反对的显式处置：首裁反对的自由 trigger 语义是**开放式**的（任意 trigger 逻辑的部分发射未经引擎验证）；本裁定的两档是**封闭枚举**，其发射语义由引擎管线唯一定义并经 WI37 判别性测试钉死（update 档=逐元素 FIRE+ACCUMULATING 不清空、close 档=现行为），验证面由封闭档位的钉码测试承担——这正是首裁把「建注册表」设为前置时想避免的未验证面，封闭枚举形态使其不再成立。
  - **D10 = 放行 ACCUMULATING 声明**，**生效前提 = WI38 done**（FU-8 修复使声明值真实接线后，放行才有可验证语义；在此之前 build 期门保持）。`ACCUMULATING_AND_RETRACTING` 维持 spec-only fail-fast（:590-596 原样）；D9 skipPurgeForLateness 交互（:1388-1400）不因本裁定改变。
  - 语义一致性依据：D1=(a) 终值语义下 emit=update 的逐次发射经下游 last-value-wins 覆盖，无 retract 一致性问题；ACCUMULATING 与 D9 迟到管线兼容（不 purge 使迟到更新可重发，与 D9 的 fire-and-update 语义同向）。
- roadmap 同步：WI0a 状态行 → done（括注记裁定分支与日期）；前置裁定表 WI0a 行同步「已裁定」。

## Non-Goals

- 不实施任何产品代码（emit 两档归 WI37；accumulationMode 接线修复与 ACCUMULATING 门开放归 WI38）。
- 不裁 D5/D7/ASOF/字段扩展（WI0b 承接）；不裁 D12（维持不变）；不触 D1。
- 不改 stream.xdef（声明面属性由 WI37/WI38 各自承载 plan 落地）。

## Scope

### In Scope

- `ai-dev/design/nop-stream/window-failfast-decisions.md`：D10/D11 两节原位修订（保留首裁日期与修订链，新增 2026-10-08 放行记录）。
- roadmap：WI0a 状态行、前置裁定表 WI0a 行。
- 当日 `ai-dev/logs/2026/10-08.md` 更新。

### Out Of Scope

- 任何产品代码、xdef、grammar 变更。
- 其他设计文档（sql-subset-and-semantics / sql-compiler-contract 不动）。

## Execution Plan

### Phase 1 - D10/D11 放行裁定落档

Status: completed
Targets: `ai-dev/design/nop-stream/window-failfast-decisions.md`

- Item Types: `Decision`

- [x] D11 节原位修订：结论改「放行 emit 两档（2026-10-08 修订）」，记录形态锁定（EmitStrategy ON_WINDOW_UPDATE/ON_WINDOW_CLOSE）、三项随裁（注册表前置作废 / triggerId fail-fast 保留 / 挂点 `<sql>` 属性）、对首裁「部分值未经验证」反对的处置（封闭枚举 + WI37 判别性测试义务）、理由（survey kafka-streams/ksql 形态 + trigger 机制现状 + D1=(a) 终值语义兼容）、受影响 WI（WI37 实施义务清单）、保留首裁 2026-10-02 记录（加「首裁时行号，现已漂移」注记，且对原文前置条款「后续放行须先建 trigger 注册表」加「已随 2026-10-08 裁定废止」标记防误读为活条款）
- [x] D10 节原位修订：结论改「放行 ACCUMULATING 声明，生效前提 WI38 done（2026-10-08 修订）」，记录理由（运行时本已支持、FU-8 缺口实测锚点、生效前提逻辑）、边界（A&R 维持 spec-only、skipPurgeForLateness 不变）、受影响 WI（WI38 修复接线 + 放开 build 期 strategy 级 accumulationMode 门——唯一 build 门，window 节点无该属性，A&R 运行时门维持）、保留首裁记录（同样加行号漂移注记）
- [x] 文档头部修订机制：保留原「裁定日期：2026-10-02」不动（覆盖 D9-D12 首裁），新增「修订日期：2026-10-08（仅 D10/D11 两节）」行，D9/D12 节不改

Exit Criteria:

- [x] 两节裁定要素齐全且与 live 代码锚点一致（本 plan Current Baseline 行号为核对基准）
- [x] D11 三项随裁逐一显式记录（注册表前置作废 / triggerId 保留 / 挂点 `<sql>`）；D10 生效前提与 A&R/spec-only 边界显式记录
- [x] No new test required: 纯文档变更，无产品代码
- [x] No owner-doc update beyond 本档（本档即 owner doc；`docs-for-ai/03-modules/nop-stream-sql.md` 用户口径由 WI37/WI38 实施时同步）
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 前置裁定表 WI0a 行同步「已裁定 owner（委托链）2026-10-08」+ 结论短句 + 落档位置
- [x] 独立子 agent closure audit（与起草审查不同 task）：核验两节裁定与 roadmap 行、survey 建议一致性、代码锚点抽查（audit 前置检查先行；「翻转后状态行复核」在下一项翻转完成回填证据）；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi0a-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI0a 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)` 字符），翻转后回看状态行完成 audit 复核子项；重跑解析器核对工作项 18 里程碑 4，命令：`node -e "import('file:///Users/abc/app/nop-entropy-wt/nop-entropy-stream-roadmap/tools/mission-driver/src/roadmap-check.mjs').then(m=>{const r=m.parseRoadmapMarkdown(require('fs').readFileSync('ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md','utf8'));const nonM=r.phases.filter(p=>!p.isMilestone);console.log('items',nonM.reduce((n,p)=>n+(p.items?p.items.length:0),0),'milestones',r.phases.length-nonM.length)})"`（在该仓根目录执行；返回形态以实际模块导出为准调整——实测 phases 即逐条目数组，items=非 isMilestone 条目数；实测输出 items 18 milestones 4 wi0a done）
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0

Exit Criteria:

- [x] roadmap WI0a 行 + 前置裁定表行同步，解析器实测 items 18 milestones 4
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql-enhancement/wi0a-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/2026/10-08.md 收口记录与 plan 状态一致

## Closure Gates

> 纯文档计划：无产品代码变更，`./mvnw compile/test`、scan-hollow 按 guide 豁免条款删除。

- [x] D10/D11 两条裁定全部落档且要素齐全（选项/结论/理由/负责人/日期/受影响 WI/生效前提）
- [x] D11 三项随裁与 D10 边界（A&R spec-only、skipPurgeForLateness 不变）显式落档
- [x] 无超授权内容（未触 D1/D12/其他设计文档/产品代码）
- [x] 独立子 agent closure-audit 已完成并记录证据（与起草审查不同 task）
- [x] Anti-Hollow Check（文档版）：裁定理由引用的代码锚点与 live 代码一致（audit 抽查）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/01-wi0a-d10-d11-emit-decisions.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: D10/D11 放行裁定原位落档 window-failfast-decisions.md §2/§3（保留首裁记录与修订链），roadmap WI0a 行与前置裁定表同步，解析器 18+4 复核。纯文档计划，无产品代码变更；D10 生效前提（WI38 done）与 D11 实施（WI37）由后续 plan 承接。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 R1 agent_37fb7796 / R2 agent_f2b54afc 均不同 task）：agent_3c4271e1-33c4-4662-bc74-afc93591ba25
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi0a-closure-audit.md
- Evidence:
  - Exit Criteria 逐条 PASS：design doc D10/D11 两节要素齐全（§3 :33-44、§2 :21-31）、头部修订链（:4/:5/:7）、D9/D12 零改动（git diff 仅 3 hunk）、roadmap 两行同步（:70/:79）、解析器 items 18 milestones 4 wi0a done、check-doc-links --strict 0
  - Closure Gates 逐条 PASS：锚点抽查全部精确命中（AdvancedTransforms :241-248/:233-238/:258-263、WindowOperator :492/:590-596/:1388-1400、WindowingStrategy :52/:60、stream.xdef :48-56）；Anti-Hollow 文档版通过（FU-8 零消费论据复核；无悬空断言；deferred 分类诚实）
  - audit 2 项 Minor（时序、closure 残留）均为翻转时动作，已随本翻转处置，详见 audit 文件
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/01-wi0a-d10-d11-emit-decisions.md --strict` 退出码 0（翻转后复跑确认）

Follow-up:

- no remaining plan-owned work；D11 实施归 WI37、D10 接线修复与开门归 WI38（均在本 roadmap 内）
