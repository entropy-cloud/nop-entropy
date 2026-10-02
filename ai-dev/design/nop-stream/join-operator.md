# join-operator——双流等值 join 算子设计（WI13 实现依据）

> Status: active
> 本文覆盖：join 声明面的消费契约、hash join 与 window join 两种形态的算子设计、有序缓冲复用义务、并行度前提验证义务、未决评估项。
> 读者：WI13 的实现者、WI12（有序缓冲构件）的设计者。
> 实现锚点（WI13 落地后）：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java`（join 运行时，hash/window 双形态）；`.../core/model/JoinSideRecord.java` 与 `.../core/model/JoinMatch.java`（侧标记记录与配对输出）；`.../core/common/buffer/PerKeyOrderedBuffer.java`（WI12 有序缓冲，自 runtime/windowing 上移 core 以满足双算子复用）；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java` 的 buildJoin（union→keyBy→join 装配）与 `.../flow/builder/functions/JoinSideTagFunction.java`、`JoinSideKeySelector.java`（侧键求值与路由）；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/`（StreamJoinModel/StreamJoinSpecModel）；`.../core/model/JoinType.java`。
> 相关裁定：`sql-compiler-contract.md` §3（D13 模块边界）、`multi-input-model.md`（union 后 keyBy 形态）、`ai-dev/backlog/nop-stream-sql-roadmap.md` §3.1（HASH 边与 keyBy 互斥）、A6（并行度假设）。

## 1. 定位

WI8d 交付了 join 声明面（joins 注册表 + joinRef + 八项构造期校验）与显式运行时占位（NOT_IMPLEMENTED）。本档定义 WI13 补齐运行时的设计约束——实现须遵循此处记录的决策与义务，偏离须先修订本档。

## 2. 声明面消费契约（WI8d 已交付，WI13 只消费不重建）

- `<join joinRef>` 元素经 `validateJoinDeclarations` 八项校验（joinRef 存在/joinType/键集非空/键数相等/恰两条上游边/HASH 边禁入/windowStrategyRef 命中且窗口 join 限 INNER-LEFT/timeout 依赖窗口且格式合法）。
- **WI13 到达 buildJoin 时声明已合法**：buildJoin 无需重复校验，直接按 spec 装配运行时。
- joinKey 表达式求值（WI13 落地裁定）：leftKeyExprs/rightKeyExprs（逗号分隔）按 **XLang 表达式**编译（`compileSimpleExpr` + allowUnregisteredScopeVar，`event` 绑定与 `<keyBy keyExpr>` 同款），在侧标记 map 内每记录求值一次——flow 保持零 SQL 依赖，join 运行时自身零表达式求值。WI9 的 StreamSqlExprCompiler 通路留给 WI17 编译器在生成 joinSpec 文本前使用，两通道不冲突。
- HASH 边入 join 已在 builder fail-fast——join 的键共置完全由 joinKey 决定，声明面无 keyBy。

## 3. 形态一：hash join（无窗口 join）

- 拓扑形态：`left 流 + right 流 → union → keyBy(joinKey) → process(join 算子)`——与 roadmap §3.1 裁定一致（HASH 边与 keyBy 互斥后，共置唯一机制是 union 后 keyBy）。
- 算子形态：keyed process 算子（`KeyedProcessFunction` 家族或等价），双侧缓冲以 **keyed state** 承载（§三 #8 拒绝自管 HashMap；§十 明确窗口状态走 namespace-based keyed state——join 缓冲同规则）。
- 语义（WI13 落地）：配对即时发射（后到元素与对侧已缓冲记录配对，每 key 笛卡尔积，恰一次）；LEFT/RIGHT/FULL 的补齐在 watermark 推进时发放且仅对从未匹配的记录（matched 旗标工作视图与 keyed durable 副本同步翻转）；INNER 零补齐。补齐时机若取元素到达即时发放，乱序下同一记录会补齐+配对双发——watermark 门是乱序安全的最小时机。
- joinType 的 `isOuter()` 在算子补齐分支中消费（WI8d audit M-2 义务已由 TestEquiJoinHashSemantics.isOuterConsumedDrivesCompletionBranch 履行）。
- 状态有界性：hash join 的匹配寿命止于 watermark 边界——水位越过的记录即被修剪（配对后不再参与后续匹配），状态规模与水位内未决记录数成正比，无界增长被结构性排除。代价是已修剪记录不再与后来的新记录配对（晚到数据按引擎既有 fire-and-update 语义丢弃，D9 口径）。

## 4. 形态二：window join

- 前置：joinSpec 声明 windowStrategyRef（引用 WI10 参数化后的 windowingStrategies，duration 任意）与可选 timeout。
- 语义（WI13 落地）：双侧记录按 joinKey 共置后，同一 tumbling 事件时间窗（windowStrategyRef 的 duration 参数化，窗宽必须显式声明）内的跨侧配对即时发放；窗口在 watermark ≥ windowEnd + timeout 时触发收尾——补齐侧（LEFT）未匹配记录发放 null 补齐，双侧窗口条目即行丢弃；窗口已收尾后到达的迟 records 见即弃（无补发、无状态增长）。timeout 解析复用 WI10 的 parseDurationMillis（ms/s/m 后缀）。
- 限制：窗口 join 仅 INNER/LEFT（构造期已强制）；FULL 窗口补齐仍为本档未决评估项，实施须先修订本档。

## 5. 复用义务（不可另建）

| 设施 | 提供方 | join 义务 |
|---|---|---|
| 每 key 有序缓冲 | WI12（独立可测构件，事件时间排序；WI13 起落 `nop-stream-core/common/buffer`） | window join 与 hash join 的双侧缓冲复用该构件，不另建排序缓冲 |
| keyed state checkpoint/restore | 引擎既有（ProcessOperator 先例） | join 状态必须经 checkpoint/restore 端到端证据（TestE2EWindowOperatorWithCheckpoint 同级用例） |
| union 通路 | WI6 | 双流接入只经 union，不开新多输入通路 |
| 表达式求值 | WI9 | joinKey 与投影求值 |

## 6. 待验证义务

- **A6 并行度前提**：「union 后 keyBy 的 key 共置依赖 keyBy 自身显式 parallelism」在仓库内无既有证据——WI13 以 TestEquiJoinParallelismInvariant 实测确认或推翻，结论回写 roadmap A6 行。
- 状态 schema：双侧缓冲的 keyed state 描述符（namespace 划分 left/right）在实现时确定，本档只约束「keyed state + 可 checkpoint」。

## 7. 评估项处置（WI13）

- **缓冲清理策略——已收口（WI13 落地）**：hash join 按 watermark 修剪（trimToWatermark，keyed 通道以 WithKeys 变体逐条同步删除）；window join 按窗口收尾整窗丢弃。两者与 WI12 的清空语义同源（同一构件的 trim 变体），无界状态被结构性排除；匹配寿命止于 watermark/窗口边界的语义代价见 §3。
- FULL 窗口 join（双侧未匹配补齐在窗口语义下的发放时机）——**维持未决**，实施须先修订本档。
