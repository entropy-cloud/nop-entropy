# 维度 02 审计报告：nop-stream 模块职责与文件边界

> 审计日期: 2026-05-27
> 审计对象: nop-stream 全部子模块，407 个 main 源文件（47,881 行）

## 发现

### [02-01] nop-stream-api 完全为空壳，职责未落地
- **文件**: `nop-stream-api/pom.xml`
- **严重程度**: P2
- **现状**: `nop-stream-api` 没有 `src/` 目录，无任何 Java 文件。注释明确说明接口仍在 `nop-stream-core` 中。
- **风险**: 下游模块直接依赖 `nop-stream-core`，暴露核心实现细节。
- **建议**: 要么完成 API 提取，要么删除此空模块并注释说明暂不需要。

### [02-02] 三个空壳模块（checkpoint/flink/flow）含 IDE 杂物
- **文件**: `nop-stream-checkpoint/`, `nop-stream-flink/`, `nop-stream-flow/`
- **严重程度**: P3
- **现状**: 无任何 Java 代码，但含 Eclipse IDE 配置文件（`.classpath`、`.project`、`.settings/`）。
- **风险**: IDE 配置文件污染仓库。
- **建议**: 将 IDE 配置加入 `.gitignore` 并删除。

### [02-03] MemoryKeyedStateBackend（1254 行）职责过度集中
- **文件**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java`
- **严重程度**: P2
- **现状**: 单文件包含 8 种状态类型的完整实现 + JSON 序列化逻辑 + 分片路由逻辑。
- **风险**: 修改任何一种状态类型时需阅读整个文件；测试无法单独针对某个状态实现。
- **建议**: 将 8 个内部类提取为独立顶层类。

### [02-04] NFACompiler.NFAFactoryCompiler（908 行内部类）应独立为顶层类
- **文件**: `nop-stream-cep/.../nfa/compiler/NFACompiler.java`
- **严重程度**: P2
- **现状**: NFACompiler 外壳仅 84 行，真正的编译器是 908 行的内部类。
- **建议**: 将 `NFAFactoryCompiler` 提取为同级类。

### [02-05] NFA（969 行）内含 346 行 EventWrapper 内部类
- **文件**: `nop-stream-cep/.../nfa/NFA.java`
- **严重程度**: P3
- **建议**: 将 `EventWrapper` 提取为 `NFAEventWrapper` 类。

### [02-06] WindowOperator（1088 行）跨 core/runtime 存在概念重叠
- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java` + `nop-stream-core/.../operators/WindowAggregationOperator.java`
- **严重程度**: P2
- **现状**: 两个文件都在做窗口算子但层级不同。core的WindowAggregationOperator自行管理checkpoint快照。
- **建议**: 在 WindowAggregationOperator 类头增加 Javadoc 说明定位差异。

### [02-07] core 模块包含 runtime 级别的执行代码（1054 行）
- **文件**: `TaskExecutor.java`(422行), `Task.java`(314行), `StreamTaskInvokable.java`(318行)
- **严重程度**: P2
- **现状**: core 模块包含线程池管理、任务生命周期状态机、算子链调度等 runtime 关注点。
- **建议**: 长期方案迁移到 runtime；短期方案增加 package-info.java 说明设计意图。

### [02-08] core 的 CheckpointBarrierTracker 混入算子实例化逻辑
- **文件**: `nop-stream-core/.../execution/CheckpointBarrierTracker.java`
- **严重程度**: P3
- **建议**: 随 [02-07] 一起移入 runtime 模块。

### [02-09] GraphModelCheckpointExecutor（804 行）全静态方法，职责过于集中
- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`
- **严重程度**: P2
- **现状**: 25 个 static 方法混合了图构建、checkpoint 协调、barrier 调度、savepoint 管理等关注点。
- **建议**: 拆分为 CheckpointExecutionPlanner、CheckpointExecutionRunner、SavepointManager。

### [02-10] CepOperator 直接实例化 MemoryKeyedStateBackend
- **文件**: `nop-stream-cep/.../operator/CepOperator.java`
- **严重程度**: P3
- **建议**: 通过构造器参数或 IStateBackend 接口注入状态后端。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 02-01 | P2 | nop-stream-api/pom.xml | api 模块空壳，职责未落地 |
| 02-02 | P3 | checkpoint/flink/flow | 空壳含 IDE 杂物 |
| 02-03 | P2 | MemoryKeyedStateBackend.java | 8种状态合一，1254行 |
| 02-04 | P2 | NFACompiler.java | 908行内部类 |
| 02-05 | P3 | NFA.java | 346行 EventWrapper 内部类 |
| 02-06 | P2 | WindowOperator+WindowAggregationOperator | 窗口算子概念重叠 |
| 02-07 | P2 | TaskExecutor/Task/StreamTaskInvokable | core含runtime级代码 |
| 02-08 | P3 | CheckpointBarrierTracker.java | 混入算子依赖 |
| 02-09 | P2 | GraphModelCheckpointExecutor.java | 全静态804行 |
| 02-10 | P3 | CepOperator.java | 硬编码内存状态后端 |
