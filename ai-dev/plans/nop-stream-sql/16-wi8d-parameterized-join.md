# 16 WI8d 步骤3 参数化 join 面

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI8d 行、§3.1 sort-merge 行、Cross-Cuting 4）、`ai-dev/design/nop-stream/sql-landing-decision.md` §5
> Related: `ai-dev/plans/nop-stream-sql/15-wi8c-parameterized-aggregate.md`（同构先例）
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立子 agent 对抗性审查一轮修订——B-1 JoinType enum 新建落 nop-stream-core（io.nop.stream.core.model.JoinType，Scope 增补）；M-1 joinType 改可选声明 + builder 校验（钉码面保留）；M-2 增恰 2 上游边校验与 HASH 边入 join fail-fast 裁定；M-3 self-join 用例改为一 join 元素吃同一上游两条边；M-4 landing-decision §4.1 补 IJoinResolver 作废注记；m1 插入点钉死 validateDag 内；m2 entry xdef 细节补全；m3 测试基建列明；m4 buildJoin 全序说明。

## Purpose

新增 `joins` 注册表与 `<join joinRef>` 参数化 join 声明面：字段含 joinType/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout；**本 WI 只做声明与构造期校验，运行时求值由 WI13 的 buildJoin 承接**（roadmap 明文边界）。

## Current Baseline

- WI8c 全套先例（已完成并 audit）：base xdef 注册表模式、SPI 消费面（IAggregatorFunctionResolver + 模块 app-beans 自动装配 + 双 _module 布局）、恰一裁定、六项 fail-fast 测试形态、builder 全序（entry 查找 → BeanContainer 护栏/tryGetBeanByType → 委托/fail-fast）。
- `<join>` 当前不存在于 stream.xdef（grep 实测无 join 元素）——本 WI 新增元素与注册表。
- 上游约束：WI8d deps 含 WI6（done，union 通路已开）与 WI10（done）——join 的目标形态是 union 后 keyBy + process（roadmap §3.1），本 WI 不建运行时。
- D13 回改裁定已执行（WI8c）：声明面直接落 base stream.xdef；本 WI 沿用。
- HASH 边与 union/keyBy 互斥语义已实测（WI6：HASH 入 union fail-fast；HASH 入 keyBy 冗余 fail-fast）。
- 窗口 join 需要 windowStrategyRef 引用 windowingStrategies 注册表（WI10 已有 duration 参数化）；timeout 为 window join 的超时窗口。
- 错误码惯例：复用 ERR_STREAM_INVALID_ARG/ERR_STREAM_REF_UNKNOWN，零 core 新码。

## Goals

- **JoinType enum（新建，落 nop-stream-core）**：`io.nop.stream.core.model.JoinType`（INNER/LEFT/RIGHT/FULL，与 StreamRequirement 同包；全仓无既有 JoinType——xdef enum 须全限定类名）。
- **xdef 声明面（base stream.xdef）**：顶层 `<joins xdef:key-attr="joinId" xdef:body-type="list">` 注册表（entry `xdef:name="StreamJoinSpecModel"` `xdef:unique-attr="joinId"`：`joinId="!string"`、`joinType="enum:io.nop.stream.core.model.JoinType"`（**可选声明**，builder 校验缺失）、`leftKeyExprs="string"`、`rightKeyExprs="string"`、`windowStrategyRef="string"`、`timeout="string"`（duration 格式，复用 AdvancedTransforms.parseDurationMillis 语义，同包可直接调用）+ `<description>`）；`<transforms>` 内 `<join joinRef="!string" xdef:name="StreamJoinModel" xdef:ref="StreamTransformModel"/>`（joinRef 必填对齐 window@strategyRef 先例）。多 key 用逗号分隔表达式（声明面 string，解析时拆分）。`<joins>` 与 `<join>` 同名复用按父作用域解析（`<param>` 先例）。
- **模型重生成**：flow `_gen` 重生成（_StreamJoinModel/_StreamJoinSpecModel + StreamModel.joins/getJoin + StreamTransformModel 子类 StreamJoinModel）+ StreamJoinModel/StreamJoinSpecModel wrapper 手写保留文件。
- **构造期校验（本 WI 的全部运行时义务）**：`validateJoinDeclarations`（**插入点钉死：validateDag 内、validateEdgeDeclarations 之后**，签名携带 byId/upstreams——与先例同构、audit 可机械核对）。八项：未知 joinRef fail-fast（ERR_STREAM_REF_UNKNOWN）；joinType 缺失 fail-fast（ERR_STREAM_INVALID_ARG——xdef 层可选声明、builder 层强制）；leftKeyExprs/rightKeyExprs 缺失或 key 数量不等 fail-fast（等值 join 双方键数必须相等）；**join 元素上游边数恰为 2**（left/right——与 entry 双键集对应，roadmap §3.1 union 后 keyBy 形态的声明面契约）fail-fast；HASH 边入 join fail-fast（validateEdgeDeclarations 增 join 目标分支，对齐 union 先例——per-input keyBy 与 joinKey 不可双重键控）；windowStrategyRef 声明时必须命中 windowingStrategies（未命中 fail-fast）且窗口 join 的 joinType 为 INNER/LEFT（FULL 窗口补齐属 WI13 后评估）；timeout 声明时必须带 windowStrategyRef（无窗口 join 不消费 timeout，fail-fast）；timeout 格式非法 fail-fast（parseDurationMillis 语义）。
- **运行时占位边界**：`AdvancedTransforms.buildJoin` 消费 joinRef 并构建期校验后——运行时执行 fail-fast `ERR_STREAM_NOT_IMPLEMENTED`（ARG_DETAIL 明示「join runtime is delivered by WI13 buildJoin」），本 WI 不实现 hash/window join 算子（roadmap 边界：运行时求值归 WI13）。**这不违反 No-Silent-No-Op 规则**——显式 fail-fast 而非静默跳过，且为 roadmap 预定的分阶段交付。
- **SPI 侧不做**：join 无 WI8c 式 resolver（运行时归 WI13，无构建期函数解析需求）；joinRef 的取值求值通路（keyExpr 编译）由 WI13 消费 WI9 编译器承接。
- **测试**：`TestParameterizedJoinModel`（自备 parse helper——TestAdvancedTransforms 的为 private；无需 bean 容器：纯声明面测试在 buildTransforms 前即抛错）。覆盖：合法声明 joinSpec 字段往返断言；八项 fail-fast 逐项码串钉住；**self-join 形态=同一 source 两条 edge 指向同一 `<join>` 元素**（恰 2 上游校验的正例 + 流与自己 join 的 roadmap 语义），build 走到占位 fail-fast（接线证明 #23）；buildJoin 运行时占位 NOT_IMPLEMENTED 断言。

## Non-Goals

- 不实现 join 运行时（hash join/window join 算子归 WI13）；不做非等值 join（FU-1）；不接 WI12 有序缓冲；不做 FULL 窗口 join 语义（WI13 评估）；不做 join 的 Java API。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：stream.xdef 增量（joins 注册表 + join 元素）
- `nop-stream/nop-stream-core`：JoinType enum 新建（io.nop.stream.core.model）
- `nop-stream/nop-stream-flow`：模型重生成、StreamJoinModel/StreamJoinSpecModel wrapper、validateJoinDeclarations、buildJoin 占位
- 测试：TestParameterizedJoinModel + `_vfs/nop/stream/test/` 测试资源（无需 beans 文件——无 SPI 无 bean 引用）
- roadmap WI8d 行（Phase 3 翻转）；landing-decision §4.1 补 IJoinResolver 作废注记 + §5 注记；当日日志

### Out Of Scope

- join 运行时（WI13）；A6 parallelism 前提验证（WI13）；keyExpr 求值器装配（WI13）。

## Execution Plan

### Phase 1 - 声明面与模型

Status: completed
Targets: `nop-kernel/nop-xdefs`、`nop-stream/nop-stream-flow`

- Item Types: `Feature`

- [x] JoinType enum（nop-stream-core）+ stream.xdef：joins 注册表 + join 元素（joinType 可选 enum/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout；entry unique-attr 防 joinId 重复）
- [x] core install + xdefs install + flow 重生成模型 + wrapper
- [x] landing-decision §4.1 补 IJoinResolver 作废注记
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] `./mvnw install -pl nop-stream/nop-stream-core,nop-stream/nop-stream-flow -DskipTests` 绿（typed 模型携带 join 面）
- [x] StreamJoinModel 为 StreamTransformModel 子类（拓扑可引用）
- [x] landing-decision §4.1/§5 注记齐备（IJoinResolver 作废口径一致）

### Phase 2 - 构造期校验与占位

Status: completed
Targets: `nop-stream/nop-stream-flow`

- Item Types: `Feature`

- [x] `validateJoinDeclarations`（validateDag 内、validateEdgeDeclarations 后）：八项校验逐项——joinRef 存在/joinType/键数相等/上游恰 2（**执行期修正：按声明边计数，upstreams 去重集会把 self-join 同源两边折叠成 1**）/HASH 边禁入（validateEdgeDeclarations 分支）/windowStrategyRef 命中/timeout 依赖窗口/timeout 格式
- [x] `AdvancedTransforms.buildJoin`：instanceof 分派分支——运行时 NOT_IMPLEMENTED 占位（ARG_DETAIL 指向 WI13；对照 buildSideOutput 先例；前置校验保证到达时声明已合法）
- [x] 新增 `TestParameterizedJoinModel`（13 用例）：合法声明 joinSpec 字段往返 + 八项 fail-fast 码串钉住 + self-join 形态（同一 source 两条 edge 指向同一 `<join>`，注册 source bean 后全链走到占位）+ 纯声明面无需 bean 容器 sanity
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 八项校验各有具名测试且码串钉住（`nop.err.stream.invalid-arg` / `nop.err.stream.ref-unknown`）
- [x] **无静默跳过**：buildJoin 运行时显式 NOT_IMPLEMENTED（非空方法体/静默返回，规则 #24 的正确形态）
- [x] self-join 形态（一 join 元素吃同一上游两条边）拓扑 build 到占位点（恰 2 上游校验正例 + 接线证明）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow` 绿（153，flow 既有用例零退化）；core 1663 绿
- [x] ai-dev/logs/ 当日条目已更新

### Phase 3 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：声明面、八项校验、占位边界（明确 NOT_IMPLEMENTED 非 hollow）、模型重生成纪律；证据落 ai-dev/audits/nop-stream-sql/wi8d-closure-audit.md
- [x] audit 通过后 roadmap WI8d `todo` → `done`（括注单层无嵌套）；`parseRoadmapMarkdown` 复核 31 + 7
- [x] scan-hollow flow 高危零发现（buildJoin 的 NOT_IMPLEMENTED 如实归类为显式失败）；invariants sync OK
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI8d = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow 高危零发现

## Closure Gates

- [x] joins 注册表 + join 元素经 base xdef 落地且 flow typed 模型携带（重生成产出——audit 实读确认 codegen 特征）
- [x] 八项构造期校验各有具名测试且码串钉住
- [x] buildJoin 运行时占位为显式 NOT_IMPLEMENTED（WI13 边界清晰，非静默跳过——scan-hollow 0）
- [x] self-join 形态拓扑用例存在且判别性为真（roadmap 完成判定明文；audit 断言核对）
- [x] windowStrategyRef 与 windowingStrategies（WI10 参数化）联动校验生效
- [x] `./mvnw test -pl nop-stream/nop-stream-flow` 绿（153）；core 1663 绿
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，判定 PASS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/16-wi8d-parameterized-join.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### join 运行时（hash join / window join 算子）

- Classification: `moved to explicit successor ownership`（roadmap 既定边界，非本 plan 延期）
- Why Not Blocking Closure: roadmap WI8d 行明文「本 WI 只做声明与构造期校验，运行时求值由 WI13 的 buildJoin 承接」——分阶段交付为 owner 既定裁定
- Successor Required: `yes`
- Successor Path: ai-dev/backlog/nop-stream-sql-roadmap.md WI13

## Non-Blocking Follow-ups

- FULL 窗口 join 语义（左/右未匹配补齐）归 WI13 设计时评估。

## Closure

Status Note: 参数化 join 声明面落地——joins 注册表与 joinRef 经 base xdef 落地（D13 回改路径沿用），八项构造期校验各有具名钉码测试，self-join 形态用例判别性为真（按声明边计数修正 upstreams 去重折叠），buildJoin 运行时占位为显式 NOT_IMPLEMENTED（WI13 边界），IJoinResolver 作废注记落档。独立 closure audit 判定 PASS（4 项 Minor 非阻塞）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi8d-closure-audit.md（判定 PASS）
- Evidence:
  - xdef/`_gen` codegen 特征实读；八项校验逐项代码核对
  - 实跑 flow 153 / core 1663 / 隔离 TestParameterizedJoinModel 13/13；self-join 判别性断言核对（not-implemented + WI13 指向）
  - 门禁：doc-links 0、scan-hollow 0、invariants sync OK、roadmap 解析 31+7
  - 文档三处一致（landing-decision §4.1/§5 + roadmap CC4）
  - M-1 措辞残留已修（本轮）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/16-wi8d-parameterized-join.md --strict` 退出码 0

Follow-up:

- JoinType.isOuter 消费断言归 WI13（audit M-2）；FULL 窗口 join 语义评估归 WI13（见 Deferred）
