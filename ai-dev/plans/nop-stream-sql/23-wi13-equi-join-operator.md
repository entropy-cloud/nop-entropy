# 23 WI13 双流等值 join 算子

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI13 行、A6）、`ai-dev/design/nop-stream/join-operator.md`
> Related: `ai-dev/plans/nop-stream-sql/13-wi6-union-multi-input.md`、`ai-dev/plans/nop-stream-sql/22-wi12-over-window-operator.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

实现 buildJoin 承接 WI8d 的 joinRef 运行时求值：union 后 keyBy 接管 joinKey，process 算子实现 hash join 与 window join；复用 WI12 的 PerKeyOrderedBuffer 而非另建；含 keyed state 经 checkpoint 与 restore 的端到端证据；A6 parallelism 前提由 TestEquiJoinParallelismInvariant 实测确认或推翻并回写 roadmap。

## Current Baseline

- WI8d 交付：joins 注册表 + `<join joinRef>` 声明面（八项构造期校验）+ buildJoin 运行时占位 NOT_IMPLEMENTED 指向本 WI。
- WI6 交付：union 通路（union → keyBy 形态可行）；WI12 交付：PerKeyOrderedBuffer 独立构件（runtime/windowing，复用义务）。
- WI14 先例：ITableLookup 维表 lookup 的 keyed process 路径全链（ProcessOperator 形态，keyed backend 自建 + RuntimeContext 接线 + checkpoint/restore）。
- join-operator.md 设计：双形态——hash join（union 后 keyBy + process + keyed state 缓冲双侧）与 window join（windowStrategyRef + timeout，复用 WI12 有序缓冲）；JoinType.isOuter 消费断言归本 WI。
- A6：union 后 keyBy 的 key 共置依赖 keyBy 自身显式 parallelism——仓库内无证据，须实测确认或推翻。
- KeyedProcessFunction 家族可用（WI14 UserHistoryEnricher 先例）；keyed MapState durable copy 形态与 WI12 一致。

## Goals

- **EquiJoinFunction**（runtime 或 core，KeyedProcessFunction 形态）：双流输入按 source 标记分侧缓冲（left/right keyed MapState），watermark/元素到达时按等值键匹配，按 JoinType（INNER/LEFT/RIGHT/FULL，isOuter 消费）发射匹配对；window join 形态复用 PerKeyOrderedBuffer 做双侧时间排序缓冲 + timeout 清理。
- **buildJoin 真实实现**：替换 NOT_IMPLEMENTED 占位——装配 EquiJoinFunction，joinRef 的 leftKeyExprs/rightKeyExprs 经 WI9 编译器求值（或等值字段直接提取，形态裁定随实现）。
- **测试**（roadmap 明文类名）：
  1. `TestEquiJoinParallelismInvariant`（A6：union 后 keyBy 的 key 共置实测——两源并行度 >1 时 join 结果正确即确认、错误即推翻并回写 roadmap A6 行）
  2. `TestEquiJoinHashSemantics`（INNER/LEFT/RIGHT/FULL 匹配与补齐语义 + isOuter 消费断言）
  3. `TestEquiJoinWindowTimeout`（window join：windowStrategyRef + timeout 语义）
  4. `TestEquiJoinWithCheckpoint`（keyed state checkpoint/restore 端到端——双侧缓冲经 restore 后 join 连续）
- 语义标注：EquiJoinFunction javadoc 标注 D1=(a) 终值语义（匹配对发射、无 retract）。

## Non-Goals

- 不做非等值 join（FU-1）；不做 SQL 语法面（WI17）；不实现 FULL 窗口补齐（构造期已禁，WI13 评估项维持）。

## Scope

### In Scope

- `nop-stream/nop-stream-runtime`：EquiJoinFunction（hash + window 双形态）、buildJoin 真实实现、四个具名测试类
- roadmap WI13 行（Phase 2 翻转）+ A6 回写；join-operator.md 更新（实现锚点）；当日日志

### Out Of Scope

- 非等值 join；SQL 语法面；FULL 窗口补齐。

## Execution Plan

### Phase 1 - EquiJoinCore 与测试（buildJoin 接线待 successor）

Status: completed
Targets: `nop-stream/nop-stream-runtime`、`nop-stream/nop-stream-flow`

- Item Types: `Feature`

- [x] EquiJoinCore 落 nop-stream-core operators/join（四种 JoinType 语义 + expire 窗口超时 + restoreFrom —— flow 与 runtime 共享，解依赖方向）
- [x] buildJoin 接线：**successor 路由**（flow→runtime 依赖方向使直接装配不可行——EquiJoinCore 已移 core 解共享，但 flow 侧 map+union+keyBy+process 拓扑构建需 cross-module SPI 或 EquiJoinCore 生产形态上移 core；NOT_IMPLEMENTED 占位保留，审计记录 successor 路由）
- [x] 四个具名测试类（TestEquiJoinHashSemantics 7 / TestEquiJoinWindowTimeout 3 / TestEquiJoinParallelismInvariant 1 / TestEquiJoinWithCheckpoint 1）
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 四个测试类隔离实跑绿（12 用例；A6 结论：union 单顶点使 key 共置结构化成立——keyBy 显式 parallelism 非共置机制）
- [x] buildJoin 接线 successor 路由落档（本 plan Deferred 节 + join-operator.md）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 绿（1220 零退化）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（不同 task_id）：join 语义判别（四种 JoinType 反事实）、A6 结论、checkpoint 证据、buildJoin 接线；证据落 ai-dev/audits/nop-stream-sql/wi13-closure-audit.md
- [ ] audit 通过后 roadmap WI13 `todo` → `done`（括注单层一对）+ A6 回写；`parseRoadmapMarkdown` 复核 31 + 7
- [ ] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI13 = done + A6 回写 + 解析器 31 + 7 复核通过
- [ ] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Deferred But Adjudicated

### buildJoin flow 侧接线（DSL 拓扑构建）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: flow→runtime 依赖方向（D13 裁定）使 AdvancedTransforms.buildJoin 无法直接引用 runtime 的 EquiJoinCore——EquiJoinCore 已移 core 解共享，但 flow 侧 map+union+keyBy+process 拓扑构建的 inline 实现需 cross-module SPI 或 EquiJoinCore 生产形态上移 core，属架构裁定项非本 WI 范围。四种 JoinType 语义与窗口超时已在 EquiJoinCore 全测。
- Successor Required: `yes`
- Successor Path: buildJoin 接线归 WI17 SQL 编译器交付（WI17 产出 SQL → DSL 拓扑时一并接通 join 流程）或独立 Fix plan

## Closure Gates

- [ ] 四个具名测试类齐备且实跑绿（A6/JoinType 四态/window timeout/checkpoint）
- [ ] buildJoin 真实装配（占位移除），joinRef 声明端到端可 join
- [ ] keyed state checkpoint/restore 证据落地
- [ ] D1=(a) 语义标注（无 retract）
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime` 绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/23-wi13-equi-join-operator.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: <<完成时填写>>
Completed:

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<验证结果>>

Follow-up:

- <<no remaining plan-owned work 或列出>>
