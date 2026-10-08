# 02 WI0b D5 与 D7 与 ASOF 与字段扩展授权四条边界裁定

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI0b 行、前置裁定表 WI0b 行、门控判据表 D5/D7/ASOF/字段扩展四行、Assumptions A2）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md`（D5 落档对象）；`ai-dev/design/nop-stream/sql-compiler-contract.md`（D7 落档对象）；`ai-dev/design/nop-stream/00-vision.md` §六#1（字段扩展补记落点）；`ai-dev/analysis/streaming-sql-survey/2026-10-07-nop-stream-sql-gap-and-recommendations.md`（B4/B5/B7 建议）
> Related: `ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md`（首裁）；ai-dev/plans/nop-stream-sql-enhancement/ 下 keyed-TopN 实施与 asymmetric-join-timeout 实施承载 plan（未来交付物，编号按起草时最大编号+1）；`ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md`（WI7 已 done——D7 触发条件核对）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」委托 ZCode 代理按 roadmap 建议项与 survey 结论执行；与 2026-10-02 首裁及 2026-10-08 WI0a 同一委托模式）

## Purpose

裁定四条边界并把结论落档到各自 owner doc：

1. **D5**：全局 ORDER BY/LIMIT 是否重裁为 keyed TopN 近似（修订 sql-subset-and-semantics §4 与 §4a#1）。
2. **D7**：DDL 与查询分层是否随 multi-engine WI7 落地重开（修订 sql-compiler-contract D7 节）。
3. **ASOF**：「时间就近匹配」是否单列于非等值/范围 join 排除之外（落 sql-subset-and-semantics）。
4. **字段扩展授权**：joins 既有条目字段级扩展的 §六#1 授权补记与否（vision §六裁决记录追加）。

WI27（非对称窗口 join timeout）与 WI40（keyed TopN）依赖本计划结论；放行项的实施按 roadmap 动态规划另立或进 Follow-up Backlog。

## Current Baseline

- **D5 首裁**（sql-subset-and-semantics §4，2026-10-02）：全局 ORDER BY 与 LIMIT 首版排除，进不支持清单。理由两支：(1) 引擎无 sort/TopN 设施；(2) keyed 缓冲近似引入全量状态与正确性边界问题。现理由 (1) 已部分失效、理由 (2) 在 keyed 形态下可回答：sql roadmap WI12 已**构件级交付** OVER 分析窗口算子与 PerKeyOrderedBuffer 每键有序缓冲 + ROW_NUMBER frame（survey B4 行明示「WI12 的 PerKeyOrderedBuffer+ROW_NUMBER 已提供构件基础」）——注意属构件级事实：`OverWindowOperator`（nop-stream-runtime）目前仅测试实例化，无 DataStream API 方法、无 stream.xdef 声明节点、编译器对 OVER 全线 fail-fast，**尚无生产接线**。keyed 形态下状态有界性成立：每键缓冲受 watermark 推进清理（与 WI12 完成判定的 namespace-based keyed state 通道同构），状态规模 = O(每键窗口内元素数)，非全局全量。
- **编译器现状**：`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/StreamSqlCompiler.java` compileQuery 前置 fail-fast：ORDER BY :248-250 与 LIMIT :251-253 抛 `nop.err.eql.dialect-not-support-feature`（pin 码）；WINDOW/OVER :241-244 抛 `nop.err.stream.invalid-arg`。另两个与 keyed TopN 语法载体相关的已知边界：子查询表源 :264-266 fail-fast；同层 WHERE 能否引用窗口输出（QUALIFY 类语义）未经核实——**keyed TopN 的语法载体归 WI40 plan 实测裁定**，本裁定只锁语义形态（每键取前 N）。
- **D7 首裁**（sql-compiler-contract §1.1，2026-10-02）：`.sql` DDL 文件拒绝，schema 内聚查询声明处（`<sql>` 内嵌 `<schemas>`）。survey B5 建议触发条件「multi-engine WI7 CatalogTable 落地后一并裁」——**该条件已满足**：`ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI7 行已勾选 done（RowKind+tableId 引擎级变更元数据，plan 2336）。
- **A2 约束**：WI0b 的 D7 分支在 WI7 落地前只裁不放行实施——WI7 已落地，本裁定可裁可放行，实施义务仍按 roadmap 不预设（DDL 分层实施在 Follow-up Backlog FU-E3）。
- **ASOF 现状**：join 声明面仅等值键集（leftKeyExprs/rightKeyExprs 数量相等校验）；运行时 `EquiJoinOperator` 等值键控 hash/window merge 双形态；§4a#2 排除非等值/范围 join；vision §四 Non-Goals「双流 Join」排除行（非等值/范围 join）是其边界母条款。survey B7：Feldera LEFT ASOF + MATCH_CONDITION 仅 `>=` 是「限制式 fail-fast 模板」，放行前提是 owner 认定「就近匹配≠范围 join」单列。
- **字段扩展现状**：joins 注册表条目（`StreamJoinSpecModel`，timeout 单值字段）由 WI8d 授权（vision §六#1 授权 (b)「StreamComponents 注册表新增条目」）；字段级扩展（leftTimeout/rightTimeout）属 roadmap 门控判据表认定的「既有条目的结构修改」，须 owner 补记授权——比照 WI21 schemas 补记先例：授权原文之外的实施延伸在 §六裁决记录显式补记。注意 WI21 先例的实质理由「条目为描述性规范化载体，不含可执行语义」**不可平移**——leftTimeout/rightTimeout 是可执行语义参数，本次补记须给出自己的实质理由（条目集与条目类型不变、仅参数化既有 timeout 字段、构建期校验面扩展）。
- **multi-engine WI11 已 done**（`ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` :66）：CachingTableLookup 本地物化+后台刷新已落地；WI39 销号规则（仅当 WI11 显式吸收）与此裁定无关，本计划不触。

## Goals

裁定结论（受托 owner 按 survey 建议与引擎现状作出）：

1. **D5 = 部分重裁：keyed TopN 放行，全局排序维持排除**。语义形态 = 每键分组内取前 N（keyed TopN 近似）；**语法载体归 WI40 plan 实测裁定**（`WHERE rn <= N` 为 survey 参考形态而非锁定语法；已知边界两处须 WI40 处置：子查询表源 :264-266 fail-fast、同层 WHERE 引用窗口输出的 QUALIFY 类语义未核实）；无 GROUP BY 键的全局 ORDER BY / 全局 LIMIT 维持排除（引擎无全局 sort 设施的理由仍然成立）。对首裁理由第二支（keyed 缓冲近似的全量状态与正确性边界）的处置：keyed 形态下状态有界——每键缓冲受 watermark 推进清理（WI12 PerKeyOrderedBuffer 通道），状态规模 O(每键窗口内元素数) 而非全局全量；正确性边界由 WI40 实施时钉语义测试（乱序/迟到/键内并列秩）承担。落 sql-subset-and-semantics §4 修订 + §4a#1 行改写。实施归 WI40（本 roadmap 内）。**边界注记**：本裁定不预授实施所需的模型级结构变更——若 WI40 落地所需声明结构构成注册表结构变更/新条目类型/条目集变更（判据口径同下方第 4 条授权边界），须另走 §六#1 补记；由既有已授权模型类型承载的声明形态（类比 WI26 伪表函数变体不触门）不在补记范围。
2. **D7 = 维持查询内聚**。multi-engine WI7 落地（触发条件满足）但 DDL 分层无首个真实消费者：schema 内聚在 `<sql>` 声明处的窄范围口径继续成立；`.sql` DDL 文件维持拒绝。重开触发条件显式登记 = multi-engine roadmap 的 cdc-design schema evolution defer 撤销且 WI15 启动后其 sink 侧 DDL 应用路径成为首个真实消费者时，或真实用户诉求出现时。落 sql-compiler-contract §1 修订注记。FU-E3 保留承接。
3. **ASOF = 维持排除**。「时间就近匹配」仍归入非等值/范围 join 排除（§4a#2 及其母条款 vision §四 Non-Goals「双流 Join」排除行——裁定记录须引用该 §四排除行），不单列。理由：需新增 join 类型（EquiJoinOperator 等值键控之外的第二形态）与有序状态设施，真实需求未出现（比照 multi-engine WI0 裁定 R6 的「roadmap 立项属缺口登记不构成真实用户需求」判例）；FU-E4 保留承接，重开触发条件 = 真实需求 + owner 认定单列。落 sql-subset-and-semantics 新附节。
4. **字段扩展授权 = 补记通过**。joins 既有条目（`StreamJoinSpecModel`）的字段级扩展（leftTimeout/rightTimeout 声明与构建期校验）属 **2026-10-02 授权原文之外的实施延伸**（授权 (b) 列举的三类注册表条目不含 joins 条目内字段参数化），比照 WI21 schemas 补记先例在 vision §六裁决记录追加显式补记；补记实质理由：条目集与条目类型不变（仍是授权 (b) 已覆盖的 joins 条目）、仅将既有单一 timeout 字段参数化为 leftTimeout/rightTimeout、构建期校验面扩展，不触碰 (a) Transformation 类型。**同时显式声明边界**：本补记不改变 2026-10-02 授权 (a)(b) 的既有范围——新增注册表条目类型仍限授权原文所列，授权范围之外的新条目类型或条目集变更仍属 §六#1 人审门。WI27 据此可实施。

## Non-Goals

- 不实施任何产品代码（keyed TopN 归 WI40；非对称 timeout 归 WI27）。
- 不触 D1/D10/D11/D12/D14（已裁）；不裁 CEP SQL（Tier C 维持）。
- 不改 grammar/xdef（WI26/WI27/WI40 各自承载 plan 落地）。
- 不改 multi-engine roadmap 文本（WI7/WI11/WI15 状态为既有事实引用）。

## Scope

### In Scope

- `ai-dev/design/nop-stream/sql-subset-and-semantics.md`：D5 节修订（keyed TopN 放行+全局维持排除）、§4a#1 行改写、ASOF 维持排除附节（或并入 D5 修订节）。
- `ai-dev/design/nop-stream/sql-compiler-contract.md`：D7 节修订注记（维持查询内聚 + 重开触发条件）。
- `ai-dev/design/nop-stream/00-vision.md`：§六裁决记录追加字段扩展授权补记条目。
- roadmap：WI0b 状态行、前置裁定表 WI0b 行。
- 当日 `ai-dev/logs/2026/10-08.md` 更新。

### Out Of Scope

- 任何产品代码、xdef、grammar 变更。
- `docs-for-ai/03-modules/nop-stream-sql.md` 用户口径（WI40/WI27 实施时同步）。

## Execution Plan

### Phase 1 - 四条裁定落档

Status: completed
Targets: `ai-dev/design/nop-stream/sql-subset-and-semantics.md`、`ai-dev/design/nop-stream/sql-compiler-contract.md`、`ai-dev/design/nop-stream/00-vision.md`

- Item Types: `Decision`

- [x] sql-subset-and-semantics：§4 节标题同步改写（「D5 全局 ORDER BY 与 LIMIT = 首版排除」→ 反映 keyed TopN 放行新口径），节内原位修订——结论改「keyed TopN 放行、全局维持排除（2026-10-08 修订）」，以增行方式保留首裁记录（选项/结论行不动原文，新增修订行），逐一处置首裁两支理由（全局设施失效部分/状态有界性回答）；§4a#1 行同步改写（keyed TopN 移出不支持、全局排序保留、fail-fast 形态与指认更新；行文必须带「放行≠已实施」标记——写明裁定放行、实施归 WI40，全局 ORDER BY/LIMIT 编译期 fail-fast（StreamSqlCompiler :248-253）维持现状）；新增 ASOF 附节（置于 §4d 之后 §7 之前，编号顺延现有附节序列为 §4e）——维持排除裁定 + 理由 + **引用 vision §四排除行** + FU-E4 承接 + 重开触发条件
- [x] sql-compiler-contract：§1 D7 节追加修订注记（§1.1a，2026-10-08）——维持查询内聚；WI7 落地触发条件已满足但无首个真实消费者；重开触发条件 = cdc-design defer 撤销且 multi-engine WI15 启动后其 sink 侧 DDL 应用路径成为首个真实消费者，或真实用户诉求；FU-E3 承接
- [x] 00-vision §六裁决记录：追加条目——joins 既有条目（StreamJoinSpecModel）字段级扩展（leftTimeout/rightTimeout）授权补记（2026-10-08），比照 WI21 schemas 补记先例的**格式**（执行注记式、含边界声明）但**实质理由须自给**（条目集与条目类型不变、仅参数化既有 timeout 字段、构建期校验扩展——WI21 的「描述性载体无可执行语义」理由不适用于本补记，禁止照抄）；显式边界：不改变 2026-10-02 授权 (a)(b) 既有范围，范围之外的新条目类型仍属 §六#1 人审门；授权依据 = 本 roadmap WI0b（2026-10-08 执行指令委托链）
- [x] 三份文件（四条裁定）落档均含：选项、结论、理由（代码/事实证据）、负责人（委托链）、日期 2026-10-08、受影响 WI（触发条件类裁定另含重开条件）

Exit Criteria:

- [x] 四条裁定全部落档且要素齐全；D5 修订与 §4a#1 行、ASOF 附节三处口径一致
- [x] D7 重开触发条件与 ASOF 重开触发条件均显式登记（不悬空）；ASOF 附节引用 vision §四排除行
- [x] vision 补记条目格式与 WI21 先例一致（执行注记式、含边界声明）、实质理由自给（非照抄）
- [x] No new test required: 纯文档变更，无产品代码
- [x] No owner-doc update beyond 三份落档文件本身
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 前置裁定表 WI0b 行同步「已裁定 owner（委托链）2026-10-08」+ 四条结论短句 + 落档位置
- [x] 独立子 agent closure audit（与起草审查不同 task）：核验四处落档与 roadmap 行、survey 建议一致性、既有裁定（§4a 表其他行/D1/§4b）零越权改动、代码锚点抽查；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi0b-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI0b 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)` 字符，四条裁定分支逐一记入括注）；重跑解析器核对 items 18 milestones 4（`phases` 逐条目数组形态，items=非 isMilestone 条目；实测输出 items 18 milestones 4 wi0b done）
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0

Exit Criteria:

- [x] roadmap WI0b 行 + 前置裁定表行同步，解析器实测 items 18 milestones 4
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql-enhancement/wi0b-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/2026/10-08.md 收口记录与 plan 状态一致

## Closure Gates

> 纯文档计划：无产品代码变更，`./mvnw compile/test`、scan-hollow 按 guide 豁免条款删除。

- [x] 四条裁定全部落档且要素齐全（选项/结论/理由/负责人/日期/受影响 WI/触发条件）
- [x] 三份文件（四条裁定）落档位置与 roadmap 前置裁定表约定一致（D5+ASOF→sql-subset、D7→sql-compiler-contract、字段扩展→vision §六）
- [x] 无超授权内容（未触 §4a 其他行、§4b、D1/D10/D11/D12/D14、multi-engine roadmap、产品代码）
- [x] 独立子 agent closure-audit 已完成并记录证据（与起草审查不同 task）
- [x] Anti-Hollow Check（文档版）：裁定理由引用的事实（WI7 done、§4a#1 现状、vision §六#1 授权原文）与 live 文档一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/02-wi0b-boundary-decisions.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 四条边界裁定按落档拆分落齐——D5 keyed TopN 放行（§4+§4a#1，放行≠已实施）、D7 维持查询内聚（§1.1a）、ASOF 维持排除（§4e）、字段扩展授权补记通过（vision §六）；roadmap WI0b 行与前置裁定表同步，解析器 18+4 复核。纯文档计划，无产品代码变更；keyed TopN 实施归 WI40、非对称 timeout 实施归 WI27（均在本 roadmap 内）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 R1 agent_1a126ce9 / R2 agent_a5d94b08 均不同 task）：agent_d6981d33-8174-45d1-96ec-ec75395d8544
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi0b-closure-audit.md
- Evidence:
  - Exit Criteria 逐条 PASS：三份文件四处落档要素齐全（sql-subset §4:45-54/§4a#1:84/§4e:145-154、contract §1.1a:38-43、vision :89）、越权检查（四文件 git diff 逐行核验零越权）、roadmap 两行（:70 前置裁定表/:80 状态行）、解析器 items 18 milestones 4 wi0b done、check-doc-links --strict 0
  - Closure Gates 逐条 PASS：代码锚点（StreamSqlCompiler :248-253 pin 码、StreamJoinSpecModel 实存、OverWindowOperator 构件级现状）与 live 一致；Anti-Hollow 文档版通过（无悬空断言；deferred 分类诚实）
  - audit 2 项 Minor（首裁记录保留方式措辞偏差、既有 doc-link warning）均已接受并记录于 audit 文件
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/02-wi0b-boundary-decisions.md --strict` 退出码 0（翻转后复跑确认）

Follow-up:

- no remaining plan-owned work；keyed TopN 实施归 WI40、非对称 timeout 实施归 WI27、D7/ASOF 放行实施由 FU-E3/FU-E4 在 Follow-up Backlog 承接
