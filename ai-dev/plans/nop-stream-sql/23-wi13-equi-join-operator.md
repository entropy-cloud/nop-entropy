# 23 WI13 双流等值 join 算子

> Plan Status: completed
> Last Reviewed: 2026-10-03
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

- **EquiJoinOperator**（双流等值 join 运行时）：双流输入按 source 标记分侧缓冲（left/right keyed MapState durable copy + PerKeyOrderedBuffer 工作视图），配对即时发射、outer 补齐在 watermark，按 JoinType（INNER/LEFT/RIGHT/FULL，isOuter 消费）发放；window join 形态复用 PerKeyOrderedBuffer 做双侧时间排序缓冲 + windowEnd+timeout 收尾。
- **buildJoin 真实实现**：替换 NOT_IMPLEMENTED 占位——装配 join 算子，joinRef 的 leftKeyExprs/rightKeyExprs 编译为侧键求值。
- **测试**（roadmap 明文类名）：
  1. `TestEquiJoinParallelismInvariant`（A6：union 后 keyBy 的 key 共置实测——确认或推翻并回写 roadmap A6 行）
  2. `TestEquiJoinHashSemantics`（INNER/LEFT/RIGHT/FULL 匹配与补齐语义 + isOuter 消费断言）
  3. `TestEquiJoinWindowTimeout`（window join：windowStrategyRef + timeout 语义）
  4. `TestEquiJoinWithCheckpoint`（keyed state checkpoint/restore 端到端——双侧缓冲经 restore 后 join 连续）
- 语义标注：join 算子 javadoc 标注 D1=(a) 终值语义（匹配对发射、无 retract）。

## Non-Goals

- 不做非等值 join（FU-1）；不做 SQL 语法面（WI17）；不实现 FULL 窗口补齐（构造期已禁，评估项维持）。

## Scope

### In Scope

- `nop-stream/nop-stream-core`：join 算子与模型类（flow 须直接装配，落 core 解依赖方向）
- `nop-stream/nop-stream-flow`：buildJoin 真实实现（侧标记 map + union + keyBy + keyed.transform）
- `nop-stream/nop-stream-runtime`：三个算子级测试类
- roadmap WI13 行（Phase 2 翻转）+ A6 回写；join-operator.md 更新（实现锚点）；当日日志

### Out Of Scope

- 非等值 join；SQL 语法面；FULL 窗口补齐。

## Execution Plan

### Phase 1 - join 算子与 buildJoin

Status: completed
Targets: `nop-stream/nop-stream-core`、`nop-stream/nop-stream-flow`、`nop-stream/nop-stream-runtime`

- Item Types: `Feature`

- [x] EquiJoinOperator（hash 双侧 keyed MapState 缓冲 + 匹配发射 + isOuter 补齐；window 形态复用 PerKeyOrderedBuffer + timeout）。执行期裁定三条：① 形态取 WI12 已过六轮 audit 的原生 AbstractStreamOperator + processWatermark 形态（ProcessFunction 无 watermark 回调，补齐发射须 watermark 驱动），落点 nop-stream-core/operators/join（flow 不可见 runtime，且 buildJoin 须直接构造）；② 复用义务落法——PerKeyOrderedBuffer 自 runtime/windowing git-mv 上移 core/common/buffer（纯数据结构零依赖迁移，git mv 保留历史），窗口与 join 两算子共享；③ 语义裁定（join-operator.md §7 评估项收口）：hash join 匹配寿命止于 watermark 边界（配对即时发射、补齐在 watermark、越界即修剪，有界状态）；window join 在 windowEnd+timeout 触发（D9 宽限），迟 records 见即弃。matched 旗标工作视图与 keyed durable 副本同步翻转，restore 后不重复补发。
- [x] buildJoin 真实实现（NOT_IMPLEMENTED 占位移除；左右侧 JoinSideTagFunction 先求值侧键（XLang compileSimpleExpr + allowUnregisteredScopeVar，event 绑定与 keyBy keyExpr 同款，flow 零 SQL 依赖）→ union → keyBy(equiKey) → keyed.transform(EquiJoinOperator)，声明边序定左右，自连接按声明边计数不去重）
- [x] 四个具名测试类（TestEquiJoinHashSemantics 8 + TestEquiJoinWindowTimeout 5 + TestEquiJoinWithCheckpoint 2 落 runtime/operators/join；TestEquiJoinParallelismInvariant 3 落 flow/builder——DSL E2E 需 flow 测试基建，一 JVM 一 execute 限制下非 E2E 断言不与 execute 同类）。执行期发现并修复两处：① MemoryStateSerDe.restoreState 重建 state 对象导致 open() 期获取的 MapState 句柄变孤儿——restore 后须重新获取句柄（EquiJoinOperator 与 WI12 OverWindowOperator 同修，WI12 该缺陷由本 WI 更强的 checkpoint 断言暴露）；② 引擎 keyed MapState 读取按 CURRENT KEY 作用域——watermark 跨 key 清理循环须按 buffer key 重设 currentKey（scopeToEquiKey，键对象取自工作视图记录与写入时一致），checkpoint 断言亦须 setCurrentKey 后读取；据此本算子不设全量 rebuildViewFromKeyedState（按 key 分片语义下该方法在 putAll 之后调用会叠加重复条目——WI12 R-2 陷阱变体），视图恢复统一走 operator-state 传输通道
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 四个测试类隔离实跑绿（18 用例 8+5+2+3——含 audit MAJ-1 跨 key durable 清理回归两条，修复前红后绿；A6 结论落档：**推翻**——共置由 union→keyBy 单一 partition 顶点结构保证，两侧无需等 parallelism，keyBy 声明 parallelism 只定共享分区顶点的规模；KeyGroupAssignment.assignToKeyGroup 是 (key, maxParallelism) 纯函数，路由从不感知来源侧）
- [x] buildJoin 从 NOT_IMPLEMENTED 占位转为真实装配（test-join-pipeline.stream.xml 端到端：双 source → join → sink 收 J|k1:a|k1:y 与 J|k2:b|k2:x 恰两条）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 与 `./mvnw test -pl nop-stream/nop-stream-flow` 绿（零退化；core 全量亦绿——PerKeyOrderedBuffer 迁移波及）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit 第一轮（不同 task_id，fresh session）：**FAIL——1 Major 代码面缺陷**（MAJ-1：watermark 修剪/收尾循环遍历全部 buffer key 但 keyed MapState 按 currentKey 定 scope，非当前 equiKey 的 durable 条目永久残留，探针实证；测试断言同受 scope 盲区假绿）+ 3 Minor（MIN-1 字段注释陈旧；MIN-2 plan 磁盘版本为并发会话的「EquiJoinCore + buildJoin successor 路由」叙事，与 live 矛盾——其「flow 不可行」前提被本实现证伪；MIN-3 A6 回写措辞须记录无 parallelism>1 执行证据的边界）。报告落 ai-dev/audits/nop-stream-sql/wi13-closure-audit.md。
- [x] MAJ-1 修复：completeAndTrimHash/fireReachedWindows 循环内 scopeToEquiKey 按 buffer key 重设 currentKey（键对象取自工作视图首条记录，与写入时 setCurrentKey 作用域严格一致）；新增跨 key durable 清理回归两条（hash + window，逐 key setCurrentKey 断言，修复前红实证、修复后绿）；MIN-1 注释修正；MIN-2 本 plan 重写为实际交付形态（接管注记保留：并发会话起草 plan 与 EquiJoinCore 草稿后停滞，接管会话按 §十/复用义务重设计为 EquiJoinOperator 并完成 buildJoin 接线——successor 路由叙事作废，无 Deferred 项）；MIN-3 纳入下条 A6 回写措辞。
- [x] 第二轮独立子 agent closure audit（fresh session，与第一轮非同一 task_id）复核 MAJ-1 关闭与全量回归：**PASS**（§7）——MAJ-1 三重核验关闭（机制读码 + 探针复跑全 scope CLEAN + 修复前红/修复后绿精确复现）；MIN-1/2/3 全部确认收敛；全量 runtime 1224 / flow 159 / core 1665、WI12 隔离 12/12、门禁三项全 0；唯一必改项 plan 计数笔误 17→18 已随本轮修正。
- [x] audit 通过后 roadmap WI13 `todo` → `done`（括注单层一对）+ A6 回写（含 MIN-3 边界：结构性与构建级证据成立、parallelism>1 的 join 执行受本地 runner 限制未覆盖）；`parseRoadmapMarkdown` 复核 31 + 7。
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 第二轮独立 audit 证据落档两处
- [x] roadmap WI13 = done + A6 回写 + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] 四个具名测试类齐备且实跑绿（A6/JoinType 四态/window timeout/checkpoint + 跨 key durable 清理回归，18 用例）
- [x] buildJoin 真实装配（占位移除），joinRef 声明端到端可 join
- [x] keyed state checkpoint/restore 证据落地
- [x] D1=(a) 语义标注（无 retract）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id；第一轮 FAIL 的 MAJ-1 已修复并经第二轮复核）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/23-wi13-equi-join-operator.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: buildJoin 真实装配与 EquiJoinOperator（hash/window 双形态、keyed MapState durable 权威 + 工作视图传输、跨 key 清理按 equiKey 重设 currentKey）全量落地；A6 推翻结论落档；WI12 波及（PerKeyOrderedBuffer 上移 core、restore 句柄孤儿同修）经第二轮 audit 确认无退化。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent 两轮（fresh session，与实现者及相互均非同一 session）
- Evidence: 第一轮 FAIL（MAJ-1 跨 key durable 泄漏探针实证 + 3 Minor）落 ai-dev/audits/nop-stream-sql/wi13-closure-audit.md §1-§6；修复后第二轮 PASS 落同档 §7——MAJ-1 机制读码 + 探针复跑全 scope CLEAN + 修复前红（d345e4e850 检出复现 2/2 红）修复后绿（2/2）；实跑 runtime 1224 / flow 159 / core 1665 全 0F/0E、WI12 隔离 12/12；门禁 doc-links 0 / sync OK / hollow 全 0；check-plan-checklist --strict 0（收口 commit 随本轮落盘）
- 接管注记：本 plan 由并发会话起草（EquiJoinCore 方案）后停滞，接管会话按 §十/复用义务重设计为 EquiJoinOperator 并完成 buildJoin 接线；并发会话 02:10 的 plan 回写（successor 路由叙事）经一轮 audit MIN-2 指认后由本重写取代

Follow-up:

- no remaining plan-owned work
