# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

## 模块概况统计

| 子模块 | 主代码文件数 | 主代码行数 | 测试文件数 | 测试行数 | 超过500行文件数 |
|--------|------------|-----------|-----------|---------|--------------|
| nop-stream-api | 0 | 0 | 0 | 0 | 0 |
| nop-stream-core | 268 | 26,309 | 99 | 18,551 | 4 |
| nop-stream-runtime | 39 | 8,745 | 41 | 11,051 | 6 |
| nop-stream-cep | 76 | 10,032 | 9 | 931 | 4 |
| nop-stream-connector | 6 | 486 | 8 | 841 | 0 |
| nop-stream-flow | 0 | 0 | 0 | 0 | 0 |
| nop-stream-checkpoint | 0 | 0 | 0 | 0 | 0 |
| nop-stream-flink | 0 | 0 | 0 | 0 | 0 |
| nop-stream-fraud-example | 9 | 1,520 | 3 | 604 | 0 |

---

### [维度02-01] nop-stream-runtime 声明了对 nop-stream-cep 的编译期依赖但源码无任何引用

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:27-29`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-cep</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: nop-stream-runtime 的 pom.xml 声明了对 nop-stream-cep 的 compile scope 依赖，但经全量扫描，runtime 模块的 `src/main/java/` 和 `src/test/java/` 中无任何 `import io.nop.stream.cep.*` 导入，也无 resources 目录下引用 CEP 的配置文件。
- **风险**: (1) 不必要的编译期耦合使 runtime 模块多传递 ~10K 行 cep 依赖，增加下游消费者的传递依赖膨胀。(2) CEP 的间接依赖（如 nop-xlang）被带入 runtime 的编译类路径，任何 CEP 内部重构都可能意外触发 runtime 编译失败。(3) 对使用者造成困惑：以为 runtime 包含 CEP 集成逻辑，实际并无。
- **建议**: 将该依赖从 compile scope 改为 `test` scope（如果测试需要），或直接移除。如未来确实需要 CEP 集成代码再按需加回。
- **误报排除**: 已逐一扫描 `src/main/java/` 全部 39 个源文件的 import 语句，以及 `src/test/java/` 全部 41 个测试文件的 import 语句，均无 CEP 引用。这不是"看起来不优雅"，而是编译期依赖图存在实际漂移。
- **复核状态**: 未复核

---

### [维度02-02] MemoryKeyedStateBackend 1179 行混合了状态工厂、快照/反序列化和 7 个内部状态实现类

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:1-1179`
- **证据片段**:
  ```java
  // 行 159-231: 状态工厂方法 (getState, getMapState, getListState, ...)
  @Override
  public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) { ... }
  
  // 行 279-619: 快照/反序列化逻辑 (~340 行)
  @Override
  public StateSnapshot snapshotState() throws Exception { ... }
  private void restoreValueState(...) { ... }
  private void restoreMapState(...) { ... }
  
  // 行 702-1104: 7 个内部状态实现类
  private static class MemoryListState<T> implements ListState<T> { ... }
  private static class MemoryValueState<T> implements ValueState<T> { ... }
  private static class MemoryMapState<UK, UV> implements MapState<UK, UV> { ... }
  private static class MemoryAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT> { ... }
  private static class MemoryInternalAppendingState<K, N, IN, ACC> implements InternalAppendingState<K, N, IN, ACC, ACC> { ... }
  private static class MemoryInternalListState<K, N, T> implements InternalListState<K, N, T> { ... }
  
  // 行 1106-1179: 2 个辅助键类
  static class ShardPrefixedKey implements Serializable { ... }
  protected static class TypedNamespaceAndKey implements Serializable { ... }
  ```
- **严重程度**: P2
- **现状**: 单个 1179 行文件承担了三个明显职责：(1) 状态工厂/管理（~230 行），(2) 快照与反序列化（~340 行，含 5 个 private restore/snapshot 方法），(3) 7 个内部状态实现类（~400 行）。其中快照/反序列化逻辑与状态实现逻辑在逻辑上是独立的。
- **风险**: (1) 修改序列化格式时需要在同一文件中定位分散在 5 个方法中的逻辑，容易遗漏某个 restoreXxx 方法。(2) 修改某个状态实现时需在 1179 行文件中准确定位，增加代码审查难度。(3) 无法独立对快照/反序列化进行单元测试。
- **建议**: 将快照/反序列化逻辑提取为独立的 `MemoryStateSerializer` 类（package-private），将内部状态实现类提取为同包下的独立 package-private 文件（如 `MemoryValueState.java`），保留 MemoryKeyedStateBackend 本身约 300 行的工厂和管理职责。此重构不影响任何外部 API。
- **误报排除**: 这不是单纯的"看起来不优雅"。文件在 1179 行中混合了工厂、序列化和运行时实现三个独立关注点，且序列化逻辑（~340 行）与运行时状态逻辑（~400 行）在修改时互不干扰但被放在同一文件中，构成真实的维护成本。
- **复核状态**: 未复核

---

### [维度02-03] 四个空壳占位模块在 Maven Reactor 中占据构建槽位但无任何代码产出

- **文件**: `nop-stream/nop-stream-api/pom.xml:13`、`nop-stream/nop-stream-flow/pom.xml:13`、`nop-stream/nop-stream-checkpoint/pom.xml:13`、`nop-stream/nop-stream-flink/pom.xml:13`
- **证据片段**:
  ```xml
  <!-- 所有四个模块内容相同 -->
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P3
- **现状**: nop-stream-api、nop-stream-flow、nop-stream-checkpoint、nop-stream-flink 四个子模块各自只包含一个 pom.xml 文件，注释为 "placeholder, planned but not implemented"，无任何 Java 源码、测试码、资源文件。父 pom.xml 的 `<modules>` 列表中它们占据 4/9 的槽位。
- **风险**: (1) 每次 `./mvnw clean install` 增加 4 个空模块的 reactor 调度开销。(2) 对初次接触项目者造成困惑。(3) 实际 checkpoint 功能已在 runtime 模块中实现，与 checkpoint 空壳模块名称重叠，可能导致误解。
- **建议**: 如近期无实现计划，可从父 pom.xml 的 `<modules>` 中注释掉或移除这四个模块，保留 pom.xml 文件本身作为意图标记。
- **误报排除**: checkpoint 模块为空壳但 runtime 模块已有完整的 checkpoint 实现（`runtime/checkpoint/` 下含 CheckpointCoordinator、CheckpointPlanBuilder、PendingCheckpoint 等 6 个文件），这不是"看起来不优雅"而是命名重叠带来的结构性混淆。
- **复核状态**: 未复核

---

### [维度02-04] nop-stream-fraud-example 编译级别设为 Java 17，与父 POM 的 Java 11 不一致

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:15-16`
- **证据片段**:
  ```xml
  <properties>
      <maven.compiler.source>17</maven.compiler.source>
      <maven.compiler.target>17</maven.compiler.target>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  ```
  父 POM 默认 Java 11，nop-stream-runtime 设为 Java 21。
- **严重程度**: P3
- **现状**: 三个编译级别并存：(1) 父 POM 默认 Java 11，(2) runtime 显式设为 Java 21，(3) fraud-example 显式设为 Java 17。
- **风险**: 在 JDK 11 环境下构建 fraud-example 和 runtime 会编译失败，需 JDK 21+。不同模块的 bytecode 版本不同，如果模块间存在交叉引用，可能出现 `UnsupportedClassVersionError`。
- **建议**: 统一项目的 Java 编译级别。建议在 nop-stream 父 pom 中统一设定为 Java 21。
- **误报排除**: 这不是风格问题。三个不同 Java 版本在同一 reactor 构建中并存，构成可量化的构建兼容性风险。
- **复核状态**: 未复核

---

### [维度02-05] GraphModelCheckpointExecutor 全静态方法模式（777 行）使依赖不可注入、行为不可替换

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:49-777`
- **证据片段**:
  ```java
  @Internal
  public class GraphModelCheckpointExecutor {
      // 所有方法均为 static
      public static StreamExecutionResult executeWithCheckpoint(
              JobGraph jobGraph, String jobName, CheckpointConfig checkpointConfig) throws Exception { ... }
      
      private static ICheckpointStorage createStorage(CheckpointConfig config) { ... }
      private static void restoreFromCheckpoint(...) { ... }
      // ...共 20+ 个 static 方法
  }
  ```
- **严重程度**: P2
- **现状**: GraphModelCheckpointExecutor 是一个 777 行的全静态工具类，承担了 checkpoint 执行的完整编排逻辑，包含 20+ 个 static 方法。关键依赖（`ICheckpointStorage`、`CheckpointCoordinator`）在方法内部硬编码创建，无法通过构造函数或 setter 注入替代实现。
- **风险**: (1) 单元测试无法 mock `ICheckpointStorage` 或 `CheckpointCoordinator`，只能依赖真实文件系统或数据库。(2) 无法在不修改源码的情况下替换存储实现。(3) `createStorage()` 方法在遇到 `storageType="jdbc"` 时直接抛出 IllegalStateException，使 JDBC 存储策略完全不可用。
- **建议**: 将 `GraphModelCheckpointExecutor` 改为实例类，通过构造函数接收 `ICheckpointStorage` 工厂和 `CheckpointCoordinator` 工厂。static 入口方法保留为便利 facade，内部转发到实例方法。
- **误报排除**: 这不是"看起来不优雅"。全静态模式直接阻止了依赖注入和 mock 测试，在 777 行的编排逻辑中，`createStorage()` 的硬编码 `new LocalFileCheckpointStorage(...)` 意味着所有测试都必须面对真实的文件 I/O，这是可量化的测试维护成本。
- **复核状态**: 未复核
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 02-01 | **驳回（与01-01完全重复）** | 同一事实（runtime声明cep依赖但未使用），已由01-01记录为P3。合并至01-01。 |
| 02-02 | **保留 P2** | 文件确实1179行，三关注点混合属实。建议的拆分方向合理。 |
| 02-03 | **保留 P3** | 4个空壳模块确认属实。注意：03-05为同一发现的重复，合并至此。 |
| 02-04 | **驳回（与01-03完全重复）** | 同一事实（Java版本不一致），已由01-03记录为P3。合并至01-03。 |
| 02-05 | **降级至 P3** | 核心事实正确但P2过高。该类已标记@Internal，且JdbcCheckpointStorage可独立使用不通过createStorage()。P3更合适。 |
