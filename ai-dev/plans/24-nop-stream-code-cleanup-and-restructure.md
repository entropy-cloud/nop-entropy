# 24 nop-stream 代码清理与包结构重组

> Plan Status: completed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`
> Related: `03-nop-stream-improvement-plan.md`（旧改进计划，部分内容已过时）
> Review: Momus OK（无阻塞项），Explore 7/7 事实验证通过

## Purpose

基于 2026-05-20 完成的全面代码审计（12 个 agent + 直接工具验证），清理 nop-stream 中的死代码、重复代码和包结构混乱，将模块从 42 个零散包重组为 6 个清晰的领域包，消除 `operator`/`operators` 分裂。

## Current Baseline

- nop-stream 共 10 个子模块（4 空壳），344 个 Java 文件
- **零外部消费者**：仓库中无任何其他模块依赖 nop-stream
- nop-stream-core：185 文件 / 17,706 行，42 个包，21 文件 / 2,510 行零引用废弃代码（14%）
- nop-stream-runtime：18 文件 / ~5,000 行，10 文件 / 2,854 行零引用废弃代码（57%）
- `operator`（单数，3 文件，18 imports）和 `operators`（复数，20 文件，45 imports）两个包并存，含两个互不继承的 `StreamOperator` 接口
- `CepOperator`（631 行）和 `CepWindowOperator`（466 行）~68% 代码重复
- `SimpleInternalTimerService`（319 行，零引用）和 `WindowOperatorTimerService`（175 行，被 WindowOperator 使用）功能重叠
- 图执行路径（StreamGraph/JobGraph/TaskExecutor，~1,836 行）从未被生产路径使用
- 7 个 Accumulator、6 个 Trigger/Evictor、3 个 Function 接口共 ~1,588 行零引用
- 审计报告已完成并通过 3 轮对抗性审查（Oracle + 2 Explore agent），包重组方案已定稿
- Plan 22（connector 实现）已完成，Plan 03（旧改进计划）部分过时

## Goals

- 删除所有零引用废弃代码（~2,114 行主代码 + ~600 行测试 = ~2,714 行）
- 统一 `operator`/`operators` 为单一 `operator` 包
- 将 nop-stream-core 从 42 个包重组为 6 个一级领域包
- 所有变更后 `mvn test` 通过
- 更新相关设计文档反映新结构

## Non-Goals

- 不重构运行时逻辑或修复 state 未初始化等 P0 bug（属于 Plan 03 范畴）
- 不拆分新模块（nop-stream-api 等空壳保留不动）
- 不删除未使用的 Trigger/Evictor/Accumulator/Function（标注 @Internal 保留，属于 API 预留）
- 不删除图执行路径代码（标注 @Internal 保留，属于设计原型）
- 不删除 checkpoint 子系统代码（标注为未对接保留）
- 不做 `windowing/` → `window/` 等纯包重命名（收益低，风险非零，留作可选后续）

## Scope

### In Scope

- nop-stream-runtime 死代码删除
- nop-stream-core `operator`/`operators` 包合并
- nop-stream-core 包结构整理（合并 `common/state/` + `state/`，`sink/` 并入 `operators/`）
- 所有受影响文件的 import 更新
- `@Internal` 标注：未使用的 Trigger/Evictor/Accumulator/Function、图执行路径、checkpoint 子系统
- 设计文档更新（architecture.md 包结构描述、core-design.md 算子层次）
- 审计报告状态更新为 resolved

### Out Of Scope

- 修复 CepOperator/CepWindowOperator 的 state 未初始化 bug
- 实现 nop-stream-api 模块内容
- 新增功能或算子
- 性能优化
- TestCepWindowOperator 以外的测试质量改进

## Execution Plan

### Phase 1 - 删除零引用死代码

Status: completed
Targets: `nop-stream-runtime`, `nop-stream-core`

- Item Types: `Fix`

- [x] 删除 `CepWindowOperator.java`（466 行）+ `CepWindowAssigner.java`（88 行）+ `CepWindowTrigger.java`（133 行）= 687 行
- [x] 删除 `TestCepWindowOperator.java`（597 行测试）
- [x] 删除 `EvictingWindowOperator.java`（511 行）
- [x] 删除 `SimpleInternalTimerService.java`（319 行）
- [x] 删除 `TimestampsAndWatermarksOperator.java`（215 行）
- [x] 确认无其他文件 import 被删除的类（除已删除的测试文件外）
- [x] `mvn test -pl nop-stream/nop-stream-runtime -am` 通过

Exit Criteria:

- [x] 上述 5 个主文件 + 1 个测试文件已从 git 删除
- [x] `mvn test -pl nop-stream/nop-stream-runtime -am` 通过（剩余测试全部绿）
- [x] `mvn test -pl nop-stream/nop-stream-cep -am` 通过（CEP 不受影响）
- [x] No owner-doc update required（删除的都是死代码，不影响公共 API）

### Phase 2 - 统一 operator/operators 包

Status: completed
Targets: `nop-stream-core`

- Item Types: `Fix`, `Decision`

- [x] 将 `operator/StreamOperatorFactory.java` 移入 `operators/` 包（改 package 声明）
- [x] 将 `operator/SimpleStreamOperatorFactory.java` 移入 `operators/` 包（改 package 声明）
- [x] 重写 `SimpleStreamOperatorFactory`：`getRawOperator()` 返回类型从 `Object` 改为 `operators.StreamOperator`；`createStreamOperator()` 返回 `operators.StreamOperator`
- [x] 删除 `operator/StreamOperator.java`（64 行，Nop Platform 简化版）及其内嵌 `ChainingStrategy` 枚举
- [x] 更新所有 import `io.nop.stream.core.operator` → `io.nop.stream.core.operators` 的文件
- [x] 验证 `ChainingStrategy` 引用：统一使用 `operators.ChainingStrategy`（独立类版，4 值）。特别注意 TestJobGraph.java 使用全限定路径 `io.nop.stream.core.operator.StreamOperator.ChainingStrategy.ALWAYS`，需改为 `io.nop.stream.core.operators.ChainingStrategy.ALWAYS`。确认无编译错误
- [x] 删除空的 `operator/` 目录
- [x] `mvn test -pl nop-stream -am` 通过

Exit Criteria:

- [x] `io.nop.stream.core.operator` 包不再存在（目录已删除）
- [x] 所有算子相关类在 `io.nop.stream.core.operators` 包下
- [x] `ChainingStrategy` 只有独立类版本（无内嵌枚举版）
- [x] `SimpleStreamOperatorFactory` 返回类型安全（非 Object）
- [x] `mvn test -pl nop-stream -am` 通过
- [x] `ai-dev/design/nop-stream/core-design.md` §2（算子模型）已更新反映统一后的包名
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 包整理与 @Internal 标注

Status: completed
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Fix`, `Follow-up`

- [x] 将 `core/state/` 的 4 个工具类（Keyed, PriorityComparable, PriorityComparator, KeyExtractorFunction）移入 `core/common/state/` 包，删除空的 `core/state/` 目录
- [x] 将 `core/sink/PrintSink.java` 移入 `core/operators/` 包，删除空的 `core/sink/` 目录
- [x] 为以下零引用但保留的类添加 `@Internal` 注解 + Javadoc 说明：
  - 图执行路径：TaskExecutor, Task, JobGraphGenerator, StreamGraphGenerator（标注"设计原型，当前执行路径未使用"）
  - Checkpoint 子系统：CheckpointCoordinator, LocalFileCheckpointStorage, JdbcCheckpointStorage, BarrierAligner（标注"设计原型，未接入执行路径"）
  - 未使用 Trigger：ContinuousEventTimeTrigger, ContinuousProcessingTimeTrigger, ProcessingTimeoutTrigger, DeltaTrigger
  - 未使用 Evictor：TimeEvictor, DeltaEvictor
  - 未使用 Accumulator：AverageAccumulator, DoubleMinimum, DoubleMaximum, IntMinimum, IntMaximum, LongMaximum, ListAccumulator
  - 未使用 Function：TwoPhaseCommitSinkFunction, CoMapFunction, CheckpointedSourceFunction
- [x] 更新所有受 move 影响的 import（state 工具类 ~9 处，PrintSink ~0-2 处）
- [x] `mvn test -pl nop-stream -am` 通过

Exit Criteria:

- [x] `core/state/` 目录已删除（4 个工具类已移入 `common/state/`）
- [x] `core/sink/` 目录已删除（PrintSink 已移入 `operators/`）
- [x] 所有标注 `@Internal` 的类在 Javadoc 中说明保留原因
- [x] `mvn test -pl nop-stream -am` 通过
- [x] `ai-dev/design/nop-stream/architecture.md` 包结构描述已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 文档收口与审计关闭

Status: completed
Targets: `ai-dev/analysis/`, `ai-dev/design/`

- Item Types: `Follow-up`

- [x] 更新 `architecture.md` §2（模块划分）反映清理后的文件数和行数
- [x] 更新 `core-design.md` §2（算子模型）反映统一后的 `operators` 包
- [x] 更新 `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md` 状态为 `resolved`
- [x] 更新 `ai-dev/logs/` 对应日期条目

Exit Criteria:

- [x] 所有设计文档反映清理后的实际状态
- [x] 审计报告状态为 `resolved`
- [x] `ai-dev/logs/` 已更新

### Phase 5 - Closure Audit（独立审计）

Status: completed
Targets: 全部 Phase 1-4 的产出物

- Item Types: `Proof`

> 本 Phase 必须由独立子 agent 执行，不能由执行 Phase 1-4 的同一个 session 顺手完成。
> 这是 Plan Authoring Guide Rule 8/12 的强制要求。

- [x] 启动独立子 agent（`subagent_type=explore`），传入本计划路径和全部已变更文件列表
- [x] 子 agent 逐条核对 Closure Gates 中每一项：
  - [x] `mvn test -pl nop-stream -am` 通过（全模块测试绿）
  - [x] 被删除的文件/目录不再存在于 git 中
  - [x] `io.nop.stream.core.operator`（单数）包已消除
  - [x] 所有 `@Internal` 标注已添加
  - [x] 设计文档已同步
  - [x] `ai-dev/logs/` 收口记录已写入
- [x] 子 agent 将审计证据写入本计划的 Closure section
- [x] 审计通过后，将 Plan Status 改为 `completed`

Exit Criteria:

- [x] 独立子 agent 审计完成，所有 Closure Gates `[x]`
- [x] 审计证据已写入 `Closure Audit Evidence` 段
- [x] Plan Status 更新为 `completed`

## Closure Gates

- [x] `mvn test -pl nop-stream -am` 通过（全模块测试绿）
- [x] 被删除的文件/目录不再存在于 git 中
- [x] `io.nop.stream.core.operator`（单数）包已消除
- [x] 所有 `@Internal` 标注已添加
- [x] 设计文档已同步
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

| 项目 | 状态 | Why Not Blocking Closure |
|------|------|--------------------------|
| `windowing/` → `window/` 重命名 | deferred | 纯名称变更，不影响功能，收益低。watch-only residual |
| `common/functions/` → `function/` 重命名 | deferred | 同上。当前名虽带 common 前缀但不影响使用 |
| `common/typeinfo/` + `common/typeutils/` → `types/` | deferred | 同上。Nop 惯例允许 common/ 子包 |
| CepOperator state 未初始化 bug | deferred | 属于 Plan 03 范畴，非本计划目标 |
| nop-stream-api 模块拆分 | deferred | 无外部消费者时拆分无收益。optimization candidate |

## Risks

| 风险 | 影响 | 缓解 |
|------|------|------|
| Phase 2 合并包时遗漏 import 更新 | 编译失败 | `mvn compile` 验证 |
| SimpleStreamOperatorFactory 返回类型变更破坏运行时类型转换 | 运行时 ClassCastException | 需确认所有 getRawOperator() 调用点的实际类型 |
| 删除 PrintSink 后如有外部配置引用 | 编译或运行时错误 | 全仓库 grep PrintSink 确认无外部引用 |
| `@Internal` 注解未在 classpath 中 | 编译失败 | 确认 `@io.nop.api.core.annotations.core.Internal` 已在依赖中 |

## Verification Evidence

> 以下数据由独立 Explore agent 验证，作为计划准确性基线。

| 验证项 | 结果 |
|--------|------|
| `@Internal` 注解可用性 | ✅ `io.nop.api.core.annotations.core.Internal` 已在项目中使用（34 处），nop-stream-core 的依赖链包含 nop-api-core |
| `StreamOperatorFactory` import 数 | ✅ 9 个文件（全部在 nop-stream-core 内） |
| `SimpleStreamOperatorFactory` import 数 | ✅ 1 个文件（DataStreamImpl.java） |
| XML/Spring/IoC 引用 operator 包 | ✅ 无。`io.nop.stream.core.operator` 不出现在任何非 Java 配置文件中 |
| PrintSink 外部引用 | ✅ 仅在 nop-stream-core 内部引用，无外部消费者 |
| nop-stream-runtime 测试文件数 | ✅ 9 个测试文件。删除 TestCepWindowOperator 后剩 8 个 |
| `getRawOperator()` 调用者 | ✅ 仅 2 处调用（SimpleStreamOperatorFactory 自身 + 1 个调用点），类型变更安全 |

## Closure

> Plan Authoring Guide Rule 8/12：closure audit 必须由独立子 agent 执行。

Status Note: 审计通过

Closure Audit Evidence:

独立 Explore agent (bg_2cdd3823) 于 2026-05-20 完成审计，所有 6 个 Closure Gates 通过：

| Gate | 结果 | 证据 |
|------|------|------|
| `mvn test -pl nop-stream -am` | ✅ PASS | BUILD SUCCESS，全模块测试绿 |
| 被删除的文件/目录不存在 | ✅ PASS | 7 个文件 + 3 个目录（operator/、state/、sink/）均已删除 |
| `io.nop.stream.core.operator` 消除 | ✅ PASS | grep 零匹配（`io.nop.stream.cep.operator` 为不同模块，可接受） |
| @Internal 标注已添加 | ✅ PASS | 24 个文件均有 `@Internal` + Javadoc 说明 |
| 设计文档已同步 | ✅ PASS | core-design.md §2.1 反映统一后的 operators 包；architecture.md §2.1 反映清理后状态 |
| ai-dev/logs/ 已更新 | ✅ PASS | `2026/05-20.md` 包含 Plan 24 完整记录 |

Follow-up Actions: 无
