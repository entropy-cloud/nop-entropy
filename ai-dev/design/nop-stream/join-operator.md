# join-operator——双流等值 join 算子设计（WI13 实现依据）

> Status: active
> 本文覆盖：join 声明面的消费契约、hash join 与 window join 两种形态的算子设计、有序缓冲复用义务、并行度前提验证义务、未决评估项。
> 读者：WI13 的实现者、WI12（有序缓冲构件）的设计者。
> 实现锚点（现状）：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/`（StreamModelDslBuilder.validateJoinDeclarations、AdvancedTransforms.buildJoin 占位）；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/`（StreamJoinModel/StreamJoinSpecModel）；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/model/JoinType.java`。
> 相关裁定：`sql-compiler-contract.md` §3（D13 模块边界）、`multi-input-model.md`（union 后 keyBy 形态）、`ai-dev/backlog/nop-stream-sql-roadmap.md` §3.1（HASH 边与 keyBy 互斥）、A6（并行度假设）。

## 1. 定位

WI8d 交付了 join 声明面（joins 注册表 + joinRef + 八项构造期校验）与显式运行时占位（NOT_IMPLEMENTED）。本档定义 WI13 补齐运行时的设计约束——实现须遵循此处记录的决策与义务，偏离须先修订本档。

## 2. 声明面消费契约（WI8d 已交付，WI13 只消费不重建）

- `<join joinRef>` 元素经 `validateJoinDeclarations` 八项校验（joinRef 存在/joinType/键集非空/键数相等/恰两条上游边/HASH 边禁入/windowStrategyRef 命中且窗口 join 限 INNER-LEFT/timeout 依赖窗口且格式合法）。
- **WI13 到达 buildJoin 时声明已合法**：buildJoin 无需重复校验，直接按 spec 装配运行时。
- joinKey 表达式求值：leftKeyExprs/rightKeyExprs（逗号分隔多键）经 WI9 的 `StreamSqlExprCompiler.compileScalar` 编译为 StreamRecordEvaluator——**join 不新建求值设施**。
- HASH 边入 join 已在 builder fail-fast——join 的键共置完全由 joinKey 决定，声明面无 keyBy。

## 3. 形态一：hash join（无窗口 join）

- 拓扑形态：`left 流 + right 流 → union → keyBy(joinKey) → process(join 算子)`——与 roadmap §3.1 裁定一致（HASH 边与 keyBy 互斥后，共置唯一机制是 union 后 keyBy）。
- 算子形态：keyed process 算子（`KeyedProcessFunction` 家族或等价），双侧缓冲以 **keyed state** 承载（§三 #8 拒绝自管 HashMap；§十 明确窗口状态走 namespace-based keyed state——join 缓冲同规则）。
- 语义：INNER 保留双侧匹配；LEFT/RIGHT/FULL 补齐未匹配侧（补齐发放时机=超时/水位推进，属实现细节但语义须与 JoinType 一致）。
- joinType 的 `isOuter()` 在算子分支判定中消费（WI8d audit M-2：消费断言随本 WI 补齐）。

## 4. 形态二：window join

- 前置：joinSpec 声明 windowStrategyRef（引用 WI10 参数化后的 windowingStrategies，duration 任意）与可选 timeout。
- 语义：双侧记录按 joinKey 共置后，落在同一时间窗内的跨侧匹配对发放；窗口语义复用 WI10 的 WindowAssigner 参数化（tumbling-event-time + duration）；timeout 是窗口关闭后保留迟到匹配的时长（D9 放行后的 allowedLateness 同构语义）。
- 限制：窗口 join 仅 INNER/LEFT（构造期已强制）；FULL 窗口补齐为本档登记的评估项，实施须先修订本档。

## 5. 复用义务（不可另建）

| 设施 | 提供方 | join 义务 |
|---|---|---|
| 每 key 有序缓冲 | WI12（独立可测构件，事件时间排序） | window join 与 hash join 的双侧缓冲复用该构件，不另建排序缓冲 |
| keyed state checkpoint/restore | 引擎既有（ProcessOperator 先例） | join 状态必须经 checkpoint/restore 端到端证据（TestE2EWindowOperatorWithCheckpoint 同级用例） |
| union 通路 | WI6 | 双流接入只经 union，不开新多输入通路 |
| 表达式求值 | WI9 | joinKey 与投影求值 |

## 6. 待验证义务

- **A6 并行度前提**：「union 后 keyBy 的 key 共置依赖 keyBy 自身显式 parallelism」在仓库内无既有证据——WI13 以 TestEquiJoinParallelismInvariant 实测确认或推翻，结论回写 roadmap A6 行。
- 状态 schema：双侧缓冲的 keyed state 描述符（namespace 划分 left/right）在实现时确定，本档只约束「keyed state + 可 checkpoint」。

## 7. 未决评估项

- FULL 窗口 join（双侧未匹配补齐在窗口语义下的发放时机）。
- 缓冲清理策略（水位推进后清理点）——与 WI12 的清空语义对齐，不得引入无界状态。
