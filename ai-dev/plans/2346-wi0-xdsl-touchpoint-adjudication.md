# 2346 WI0 五项触点裁定（xdsl-full-coverage roadmap 裁定门）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI0 行，M0 裁定门）
> Related: `ai-dev/design/nop-stream/00-vision.md` §四/§六/§七 #G36；`ai-dev/analysis/2026-10/2026-10-06b-nop-stream-xdsl-vs-oss-stream-examples-portability.md`

## Purpose

完成 xdsl-full-coverage roadmap 的裁定门 WI0：五项 vision/schema 触点逐项裁定 + E1–E12 逐场景目标形态与「接受降级」判定，回写 vision 与对应设计文档，并把 roadmap 12 场景矩阵「接受降级（WI0 裁定）」列逐行回填。本裁定是后续实施 WI（WI9/WI11/WI16–WI20）的唯一授权通道。

## Current Baseline（2026-10-09 live 核对）

- **授权先例与本次委托指令**：vision §六裁决记录 WI0b（2026-10-08）确认——owner 以「执行 roadmap 直到彻底完成」执行指令委托 ZCode 代理按 roadmap 建议方向裁定（sql-enhancement roadmap 先例）。本次 owner 执行指令（2026-10-09）逐字记录如下，vision #WI0-XDSL 条目须照 WI0b 格式逐字引用：
  > 「/goal 执行 nop-stream-xdsl-full-coverage-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」

  本次指令与其先例同构，本 plan 沿用该委托模式，且全部裁定取 roadmap 预设默认方向（零解禁、零 Non-Goal 推翻），把人审面收敛到「确认默认方向」。
- **roadmap 现行文本**：`ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` v3（2026-10-07），12 场景矩阵「接受降级（WI0 裁定）」列 10 行「待裁定」，E4「无需降级」/E5「默认分支」已有判定（与本 plan 回填方向一致）。
- **外部前置已落地（2026-10-09 multi-engine 合并后核实）**：multi-engine roadmap 19/20 done（WI15 CDC 显式延期）——sideOutput 图级路由（WI8b）、窗口事件模型（WI8a）、maxDelay 兜底出数（WI8c）、限速（WI5）、CatalogTable/RowKind（WI7）、重算/FILL_HISTORY（WI10）、维表 lookup 异步增量（WI11）、Avro（WI18）、SQL DISTINCT/HAVING 精确面（WI17a，显式 supersede sql roadmap WI16 不支持清单）均已交付。**另：sql roadmap WI26（2026-10-08）已交付 XDSL raw-DSL 面 `sliding-event-time`/`session-event-time` 参数化 id**（`AdvancedTransforms.java:320-352`：duration+slide→`SlidingEventTimeWindows.of` 含 slide<=size 守卫、duration→`EventTimeSessionWindows.withGap`）——roadmap v3 fail-fast 表「windowFnId 白名单外取值须 bean」已部分失效，行号与表项修正归 WI1 重钉；session 合并接线与 global 参数化仍归 WI7。
- **五项触点的现状**：
  - (a) 通用异步算子：vision §四 Non-Goal「异步算子」在册；2026-10-07 vision §六 #WI0 五引擎对比裁定已维持排除（per-record 异步 lookup 收敛在物化刷新层）。
  - (b) 非等值/区间 join：vision §四 Non-Goal「双流 Join（非等值/范围/广播）」+ D2 收窄（2026-10-02，等值 join 已纳入）在册。
  - (c) `<sideInputs broadcast>`：`stream.xdef:272-278` 声明 `broadcast="!boolean=false"` 属性；builder `failFastOnUnsupportedRegistries`（`StreamModelDslBuilder.java:442-446`）整体拒绝；vision §七 #G36 永久排除 BroadcastState。
  - (d) DISTINCT SQL 口径：精确口径已由 multi-engine WI17a 交付（`nop-stream-sql` 面）；本 roadmap WI9 的声明面聚合目录尚无 distinct 计数。
  - (e) transform 级 SQL：`<sql>` 顶层唯一约束在 builder 构建期拒绝并存形态（roadmap 引 `StreamModelDslBuilder.java:288-302`，行号已漂移——live 位置由 WI1 重新钉定，约束本身仍在）。

## Goals

- 五项触点 (a)–(e) 逐项裁定并回写 `ai-dev/design/nop-stream/00-vision.md`（§六裁决记录新增条目）。
- E1–E12 逐场景「接受降级」判定，回填 roadmap 12 场景矩阵该列。
- (d) 的近似口径文档化落点（user-guide 聚合章节指针）与 (e) 的 Java-only 记录落点（`docs-for-ai/03-modules/nop-stream-sql.md`）在裁定记录中写明，由 WI9/WI16 落地。

## Non-Goals

- 不解除任何 vision §四 Non-Goal、§七 #G36、§六人审决策点（全部裁定维持既有边界）。
- 不实施任何声明面代码（WI9/WI11/WI16–WI20 的实现属后续 WI）。
- 不改写 roadmap 既有的 Baseline 表、规则集与 Work Item 定义文本（仅回填「接受降级」列；Baseline 行号漂移由 WI1 修正）。

## Scope

### In Scope

- vision §六裁决记录新增「#WI0-XDSL」条目（五触点裁定全文 + 委托依据）。
- roadmap 12 场景矩阵「接受降级（WI0 裁定）」列 12 行逐行回填。
- `docs-for-ai/03-modules/nop-stream-user-guide.md`（(d) 近似口径与精确口径的边界指针落点预登记——实际章节内容由 WI9 补）与 `docs-for-ai/03-modules/nop-stream-sql.md`（(e) 维持裁定的记录）的最小裁定注记。

### Out Of Scope

- WI1 的锚点行号重钉（本 plan 裁定文本引用行号时标注「WI1 重钉」）。
- 任何 `stream.xdef`/builder 代码变更。
- Follow-up Backlog 的新增登记（tail 源、CREATE JOB 等已在 roadmap Follow-up 节登记）。
- roadmap 的 Baseline 表、规则集与 Work Item 定义文本不回填现状差异（归 WI1 重钉）；本 plan 只在 roadmap header 留版本注记（Rule 3）与回填「接受降级」列。

## Execution Plan

### Phase 1 - 五项触点裁定文本

Status: completed
Targets: `ai-dev/design/nop-stream/00-vision.md`

- Item Types: `Decision`

- [x] 起草五触点裁定（方向全部取 roadmap 预设默认）：
  - (a) **通用异步算子：维持 §四 Non-Goal，不解除**。E6/E11 的异步 I/O 走同步替代声明形态（WI17 默认分支：process bean 承载外部调用 + 有界容量与超时行为契约 + 显式串行语义标注）。理由：2026-10-07 vision §六 #WI0 已裁定 per-record 异步 lookup 维持排除、异步性收敛在物化刷新层；通用异步算子属 §六决策点 1 人审面，无新增 owner 诉求不改判。
  - (b) **非等值/区间 join：维持 §四排除项，不解除**。E11/E12 的 `BETWEEN` 区间语义走声明式变通形态（WI18 默认分支：`<union>` + 多次两路等值 join + `<process>` 聚合 + 事件时间过滤），变通形态进 owner doc。理由：D2 收窄仅纳入等值 join；区间谓词声明面无 demonstrated user need。
  - (c) **`<sideInputs broadcast>` 属性语义重定义：go**——语义钉死为「有限维表注入方式」标记（构建期或低频更新的有限维表只读快照，声明该 sideInput 复制到所有并行实例），不引入流式广播状态、不引入 BroadcastState 类型，守 §七 #G36。WI11 按此语义落地。
  - (d) **DISTINCT SQL 口径：精确口径归 `<sql>` 面（multi-engine WI17a 已交付，已 supersede sql roadmap WI16 不支持清单）；声明面聚合目录（WI9）交付 distinct 计数的近似口径并文档化**（dedup 集合计数语义 + 内存上界说明），近似口径条目在 user-guide 聚合章节与 SQL 不支持/支持清单互指。
  - (e) **transform 级 SQL：维持 `<sql>` 顶层唯一约束，不放宽**。WI16 交付 Java-only 记录 + owner doc 指针；E8 的 transform 级 SQL 以「Java 编译产物」形态验收（SeaTunnel Sql transform 语义经 Java DataStream API 挂接 `<sql>`/EQL 编译产物）。理由：`<sql>` 顶层生成器是唯一 SQL 编译入口的单一事实源；放宽引入「模型内嵌 SQL 片段」的第二编译路径，与 §三 #1「图模型为核、运行时只执行模型」的收敛路径相悖；登记触发条件（真实多段 SQL 管线声明诉求出现时重启裁定）。
- [x] 在 vision §六裁决记录追加「#WI0-XDSL — xdsl-full-coverage 五项触点裁定（WI0 门，2026-10-09）」条目，含：五项裁定结论、委托依据（逐字引用 Current Baseline 记录的本次 owner 执行指令原文，照 WI0b 先例格式）、影响面（WI9/WI11/WI16/WI17/WI18/WI19/WI20 的分支选择）。
- [x] roadmap header 版本注记（Rule 3）：`Last updated` bump 为 v4，注记「WI0 五项触点裁定落地（vision §六 #WI0-XDSL）+ 12 场景矩阵接受降级列 12/12 回填；fail-fast 表行号漂移与 sliding/session 参数化现状修正归 WI1」。

Exit Criteria:

- [x] vision §六裁决记录含 #WI0-XDSL 条目，五项 (a)–(e) 各有明确结论与理由，委托依据逐字引用本次 owner 执行指令
- [x] 裁定方向与 roadmap 各 WI 预设默认分支一一对应（(a)→WI17 默认、(b)→WI18 默认、(c)→WI11 落地语义、(d)→WI9 近似口径、(e)→WI16 Java-only）
- [x] roadmap header 已 bump v4 且版本注记含 WI0 裁定落地与 WI1 重钉移交说明
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - E1–E12 逐场景「接受降级」回填

Status: completed
Targets: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`

- Item Types: `Decision`

- [x] 12 场景矩阵「接受降级（WI0 裁定）」列逐行回填（12/12）：
  - E1：限速降级为 bean 内自限速（引擎侧 SourceRateLimiter 已由 multi-engine WI5 交付，XDSL 限速声明面属 Follow-up，fixture 不依赖）。
  - E2：无降级（trigger/evictor/参数化 global 由 WI5/WI6/WI7 交付）。
  - E3：无降级（sideOutput 已由 multi-engine WI8b 接线，WI10 补 lateData 与场景验收）。
  - E4：无需降级。
  - E5：默认分支——逻辑落 process bean；水位一等对象处理属 Java 面（声明降级），水位一等对象声明面登记 Follow-up。
  - E6：WI0(a) 维持 Non-Goal——同步替代声明形态（有界容量/超时契约 + 串行语义标注）。
  - E7：子集——retraction 受终值语义约束（断言窗口终值）、时态 join 不做（边界 = D2 收窄的静态维表边界——版本化/时间版本维表不属静态维表——叠加 §四双流 Join 排除）、DISTINCT 走「SQL 精确口径 + 声明面近似口径」双轨；不支持项 typed fail-fast 断言保留。
  - E8：多表目标形态 = WI20 声明面（CatalogTable/RowKind 前置已由 multi-engine WI7 交付）；「单表 + 表标识字段」为声明面不可用时的显式降级形态；transform 级 SQL 走 Java 编译产物形态（WI0(e)）。
  - E9：STREAM_OPTIONS 取子集——WATERMARK(d)≈水位策略、MAX_DELAY≈maxDelay（已接线）、迟到丢弃≈allowedLateness；IGNORE_DISORDER/DELETE_RECALC/ROLLUP BY/NOTIFY/EXPIRED_TIME/PRE_FILTER 等不做（边界 = §四「SQL API」Non-Goal 的方言全覆盖排除，触发条件 = 真实 TDengine 方言诉求出现时重启裁定）；FILL_HISTORY 走 WI19 回补声明面。
  - E10：无降级（sliding 参数化已由 sql roadmap WI26 交付 XDSL 面；session 合并接线与 global 参数化仍归 WI7；复合聚合由 WI9 补）。
  - E11：N 路 hashJoin 走 union + 两路 join 变通（WI18 默认分支）；异步 gRPC 走同步替代（WI17 默认分支）；维表富化走既有 lookup 形态。
  - E12：早出由 WI8 交付；tail 源 **WI0 裁定不做**（登记触发条件 = 日志 tail 类场景真实诉求出现，roadmap Follow-up 已有登记）→ WI19 落地 bounded 重放 + DRAIN 变通；`BETWEEN` 时序 join 走事件时间过滤变通（WI18 默认分支）；`CREATE JOB`/`SINK INTO` 不属 XDSL（roadmap Follow-up 已登记）。
- [x] 逐行核对回填文本与 Phase 1 五项裁定一致（E6↔(a)、E11/E12↔(b)、E11↔(c) 关联、E7↔(d)、E8↔(e)、E12 tail↔(WI0 裁定不做→WI19 落地变通)）。

Exit Criteria:

- [x] roadmap 12 场景矩阵「接受降级（WI0 裁定）」列 12/12 行已回填，无「待裁定」残留
- [x] 回填文本与 vision #WI0-XDSL 裁定逐项一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 裁定落点预登记（(d)/(e) owner doc 注记）

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-sql.md`、`docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Decision`

- [x] `nop-stream-sql.md`：在 SQL 纳入面章节补一行裁定注记——「transform 级 SQL 不放宽 `<sql>` 顶层唯一约束（WI0(e)，2026-10-09）；Java DataStream API 挂接 EQL/`<sql>` 编译产物为唯一合法组合形态，裁定见 vision §六 #WI0-XDSL」。
- [x] `nop-stream-user-guide.md`：在「触发语义映射表」章末既有 `<sql>` 指针注记后追加一行裁定注记（不发明锚点路径）——「DISTINCT 精确口径在 `<sql>` 面（见 nop-stream-sql.md）；XDSL 声明面聚合目录的 distinct 计数为近似口径（dedup 集合），两口径边界由 WI9 落地后补全（WI0(d) 裁定，见 vision §六 #WI0-XDSL）」。

Exit Criteria:

- [x] 两个 owner doc 各含一条裁定注记，且链接/引用路径真实存在
- [x] 注记不与既有文本冲突（`nop-stream-sql.md` 的 DISTINCT 支持现状与 WI17a 交付一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯文档计划（无代码变更），构建验证条目按模板规则省略。

- [x] 五项触点 (a)–(e) 全部裁定完毕且方向明确（无「待裁定」）
- [x] E1–E12「接受降级」列 12/12 回填完毕
- [x] 裁定记录已回写 vision §六裁决记录，委托依据（与 WI0b 先例同构的执行指令）已记录
- [x] (d)/(e) 的 owner doc 裁定注记已落
- [x] 不存在被静默降级的触点——每个「不做」均有 vision 边界引用与触发条件
- [x] 受影响 owner docs 已同步（vision + 2 个 module doc）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2346-wi0-xdsl-touchpoint-adjudication.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI0 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

（无——WI0 全部触点本轮裁定，无 deferred 项）

## Non-Blocking Follow-ups

- 通用异步算子 / 区间谓词声明面的解除分支：触发条件 = owner 明确解除 vision §四对应 Non-Goal（登记于 vision #WI0-XDSL 裁定条目内，不另立文档）。

## Closure

Status Note: 五项触点裁定全部取 roadmap 预设默认方向（零解禁），vision §六 #WI0-XDSL 已回写（含 owner 执行指令逐字引用），12 场景矩阵「接受降级」列 12/12 回填，(d)/(e) owner doc 注记落点经核实与既有文本无冲突。独立 closure audit APPROVE（0 Blocker / 0 Major / 2 Minor 均为收口流程预待办，已随本收口补齐）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_84386a58-4c82-4db4-aa56-32882c0d06ed（fresh session，未参与实现）
- Audit Session: agent_84386a58-4c82-4db4-aa56-32882c0d06ed
- Evidence:
  - Phase 1 全部 Exit Criterion PASS：vision `00-vision.md:92-102` #WI0-XDSL 五项结论+理由+逐字委托依据+七 WI 影响面；roadmap v4 header；裁定方向与 WI9/WI11/WI16/WI17/WI18/WI19/WI20 预设默认分支一一对应
  - Phase 2 全部 Exit Criterion PASS：roadmap 矩阵 12/12 回填无「待裁定」残留；回填文本与 vision 逐项一致（E6↔(a)、E11/E12↔(b)、E7↔(d)、E8↔(e)、E12 tail→WI19 变通）
  - Phase 3 全部 Exit Criterion PASS：`nop-stream-sql.md` §1 顶层唯一约束注记、`nop-stream-user-guide.md` DISTINCT 双口径注记，均与既有文本无冲突
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（audit 实测）；`node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（audit 实测）
  - Anti-Hollow：纯文档计划——audit 逐条核对了裁定文本与 live 基线事实（`stream.xdef:274` broadcast 属性、`StreamModelDslBuilder.java:436-446` sideInputs 拒绝、`AdvancedTransforms.java:323-343` sliding/session 参数化、`DistinctAggregateWrapper` 存在），无空壳声明
  - Deferred 项分类检查：无 deferred 项；每个「不做」均有 vision 边界引用与重启触发条件
  - audit 发现 2 Minor（roadmap checkbox 与 Closure Evidence 的流程性提前勾选）——均已随本收口动作补齐

Follow-up:

- 通用异步算子 / 区间谓词声明面的解除分支：触发条件 = owner 明确解除 vision §四对应 Non-Goal（已登记于 vision #WI0-XDSL 条目内），no remaining plan-owned work。
