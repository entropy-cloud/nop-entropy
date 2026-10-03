# 05 WI0d D9-D12 四个 fail-fast 放行裁定

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/nop-stream-sql-roadmap.md（WI0d 行、前置裁定表 D9-D12 行、§3.5、sql-subset-and-semantics.md §1 对 D9-D12 的输入约束）
> Related: ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md、ai-dev/design/nop-stream/sql-subset-and-semantics.md
> Owner: 仓库 owner（2026-10-02 执行指令委托，授权原文见 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md §2）

## Purpose

把四个窗口 fail-fast 放行裁定（D9 allowedLateness、D10 accumulationMode、D11 triggerId、D12 窗口级 parallelism）落档到 ai-dev/design/nop-stream/window-failfast-decisions.md（未来交付物），四项各有放行或保持结论与理由，D12 附 per-transform parallelism 与 2PC 门禁不退化的确认。WI10 依赖本计划结论实施。

## Current Baseline

- D1 已裁 (a)（2026-10-02，sql-subset-and-semantics.md §1），其输入约束三条：retract 路线关闭 → D10 放行前提消失；ACCUMULATING_AND_RETRACTING 维持 spec-only fail-fast（`WindowOperator.java:449`）；D12 的 per-transform parallelism 与 2PC 门禁不因 (a) 改变。
- 当前 fail-fast 行为（2026-10-02 实测 `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java`）：
  - **D9 allowedLateness**：strategy 级（`:204-210`）与 window 节点级（`:228-233`）双层 fail-fast（`ERR_STREAM_WINDOW_ATTR_UNSUPPORTED`）是放行的唯一闸门。**运行时迟到管线已存在（审查实测，B1 修正）**：接口 `WindowedStream` 无该方法，但实现类 `WindowedStreamImpl.java:52,154-160` 已有 `allowedLateness(long)` 并经 `:238-292` 四个算子构造点与 `IWindowOperatorFactory.java:27,46` 接线到 `WindowOperator`（字段 :173）；迟到语义机制齐全（`isWindowLate` :1340-1342、cleanup timer :1358-1372、PaneInfo EARLY/ON_TIME/LATE :1147-1165、lateDataOutputTag :180、numLateRecordsDropped 指标 :192）。`AdvancedTransforms.java:189-194` 的「core WindowedStream cannot express」注释相对实现类**已陈旧**。WI10 真实缺口 = 接口面补方法（或 impl 通路）+ `buildWindow` 消费 xdef 双层声明值，**非从零实现迟到语义**。
  - **D10 accumulationMode**：strategy 级 fail-fast 非 DISCARDING（`:211-218`）；`WindowOperator.java:449` 对 A&R 开期 fail-fast。
  - **D11 triggerId**：strategy 级（`:198-203`）与节点级（`:234-239`）双层 fail-fast；「triggers would require a trigger registry」（`:190-192` 注释）。
  - **D12 窗口级 parallelism**：`<window>` 是虚拟构建点无自身执行顶点，声明 parallelism 直接 fail-fast（`:159-173`，注释指明应声明在持有 window 算子的 `<aggregate>/<reduce>/<process>` 元素上——per-transform parallelism（productization item 29）已由宿主元素承载）。
- xdef 缺省：allowedLateness=0、accumulationMode=DISCARDING、triggerId 未设（stream.xdef `:49-51`）。

## Goals

- 新建 ai-dev/design/nop-stream/window-failfast-decisions.md，四项裁定各含选项、结论、理由（代码证据）、负责人、日期 2026-10-02、受影响 WI。
- 裁定结论（按 D1=(a) 输入约束推导）：
  - **D9 = 放行**：终值语义下迟到数据触发窗口更新重发（fire-and-update），last-value-wins 覆盖，无 retract 一致性问题；运行时管线已存在（见 Baseline），WI10 义务 = 接口面通路 + `buildWindow` 消费双层声明，双层声明（strategy/节点）合并语义在 WI10 plan 定义并测试。**关键实现约束（D9×D10 交互，审查 M1）**：`emitWindowContents` 在 DISCARDING 下每次 fire 后清空窗口内容（`WindowOperator.java:1101-1106`）——放行 lateness 时清空点必须从 fire 时推迟到 window end + lateness（cleanup 时点），否则迟到更新退化为迟到元素的部分聚合，经下游 last-value-wins 恰好破坏 D1=(a) 终值承诺。
  - **D10 = 保持 fail-fast（首版 scope 收敛，非语义不相容；审查 M2 修正）**：分两层——(i) ACCUMULATING_AND_RETRACTING：spec-only，`WindowOperator.java:449` 运行时门禁原样；(ii) ACCUMULATING：**运行时已支持**（:449 错误文案明示 "Use DISCARDING or ACCUMULATING instead"；:1101 仅 DISCARDING purge；算子构造缺省即 ACCUMULATING :360），与终值语义兼容——首版收敛为 DISCARDING-only 的理由是 scope 最小化，放行属后续增量能力而非 retract 必需；D1 输入约束只消灭了 (c) 路线下的放行必要性。真正闸门是 build 期 `:211-218`。
  - **D11 = 保持 fail-fast**：首版不建 trigger 注册表；自定义 trigger 的部分发射语义与 DISCARDING-only 首版不一致，放行推迟至有 retract/触发器语义验证面后另行裁定。
  - **D12 = 保持 fail-fast**：`<window>` 虚拟节点无执行顶点，per-transform parallelism 已由宿主元素承载；放行会造成语义歧义（静默改写上游 keyBy 顶点）；**确认 per-transform parallelism 与 2PC 门禁零退化**。
- roadmap 更新：WI0d 状态行 → done；前置裁定表 D9-D12 四行同步「已裁定 owner 2026-10-02」。

## Non-Goals

- 不实施任何代码（放行实施归 WI10）。
- 不改 roadmap 除 WI0d 状态行与 D9-D12 四行外内容。
- 不裁 D1-D8/D13-D15（均已裁）。

## Scope

### In Scope

- 新建 ai-dev/design/nop-stream/window-failfast-decisions.md。
- roadmap：WI0d 状态行、前置裁定表 D9-D12 四行。
- 当日 ai-dev/logs/2026/10-02.md 更新。

### Out Of Scope

- 任何产品代码、xdef、grammar 变更。
- 其他设计文档。

## Execution Plan

### Phase 1 - 四条裁定落档

Status: completed
Targets: ai-dev/design/nop-stream/window-failfast-decisions.md（未来交付物）

- Item Types: `Decision`

- [x] 新建文档：D9 放行（理由引 D1=(a) 语义 + 双层 fail-fast 位置 + 运行时管线已存在事实 + D9×D10 清空点推迟约束，注明 WI10 义务与陈旧注释更正）；D10 保持（两层理由：A&R spec-only 门禁原样；ACCUMULATING 运行时已支持、保持属 scope 收敛）；D11 保持（引 trigger 注册表缺失与部分发射语义）；D12 保持（引虚拟节点机制 :159-173 与 item 29 承载方式 + TestPerTransformParallelismWiring 锚点）+ per-transform parallelism 与 2PC 门禁不退化确认段
- [x] 写作约定（承接 WI0b 教训）：落档文档内文件引用一律全仓路径或带行号冒号形式；裸的 java/g4 裸文件名反引号会触发 BROKEN_LINK error；未来交付物纯文本
- [x] 四项各含：选项（放行/保持）、结论、理由与代码证据（AdvancedTransforms 实测行号）、负责人、日期 2026-10-02、受影响 WI（WI10 各项实施义务；audit M2 后 D10/D11 已补行）

Exit Criteria:

- [x] 四项裁定要素齐全；D10/D12 与 D1=(a) 输入约束三条一致；D9/D11 与 D1=(a) 终值语义及 roadmap D9/D11 行耦合注一致（audit 实测）
- [x] D9 落档含 D9×D10 清空点推迟约束；D12 确认段显式写明 per-transform parallelism（宿主元素承载，锚点 TestPerTransformParallelismWiring）与 2PC 门禁不退化
- [x] No new test required: 纯文档变更，无产品代码
- [x] ai-dev/logs/2026/10-02.md 已更新

### Phase 2 - roadmap 同步与收口

Status: planned
Targets: `ai-dev/backlog/nop-stream-sql-roadmap.md`

- Item Types: `Proof`

- [x] 前置裁定表 D9-D12 四行同步「**已裁定 owner 2026-10-02**」+ 结论短句 + 落档位置
- [x] 独立子 agent closure audit（不同 task_id）：核验四条裁定与 roadmap 行、D1 输入约束一致性 + 授权一致性；裁定 PASS（20+ 处行号抽查全部精确）；证据落 ai-dev/audits/nop-stream-sql/wi0d-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI0d 状态行 `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符）；解析器翻转后实测 items 31 milestones 7
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0

Exit Criteria:

- [x] roadmap 四行 + WI0d 状态行同步，解析器实测 items 31 milestones 7
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql/wi0d-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/2026/10-02.md 收口记录三处一致

## Closure Gates

> 纯文档计划：无产品代码变更，`./mvnw compile/test`、hollow-scan 按 guide 豁免条款删除。

- [x] 四条裁定全部落档且要素齐全（D10/D12 对齐输入约束三条，D9/D11 对齐 D1 语义与耦合注）
- [x] D9×D10 清空点约束与 D12 不退化确认段落档
- [x] 无超授权内容
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，20+ 行号抽查全中）
- [x] Anti-Hollow Check（文档版）：四条裁定与 live 代码证据一致（audit 抽查 AdvancedTransforms 四处 fail-fast 位置精确）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/05-wi0d-window-failfast-decisions.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: D9-D12 四条裁定落档 window-failfast-decisions.md（D9 放行含清空点推迟约束、D10/D11/D12 保持且 D12 确认 item 29 与 2PC 门禁零退化），roadmap 四行与 WI0d 状态行同步，解析器 31+7 复核。纯文档计划，无产品代码变更。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi0d-closure-audit.md
- Evidence:
  - 四条裁定实质内容、roadmap 四行、D1 输入约束一致性全部 PASS
  - 代码证据 20+ 处行号抽查全部精确命中
  - audit 必修项 M1（勾选缺失）/M2（D10/D11 受影响 WI 行）已在翻转前修补
  - check-doc-links --strict 0 errors；解析器翻转后 items 31 milestones 7
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/05-wi0d-window-failfast-decisions.md --strict` 退出码 0

Follow-up:

- 无 plan-owned 剩余工作；D9 放行的实施义务归 WI10
