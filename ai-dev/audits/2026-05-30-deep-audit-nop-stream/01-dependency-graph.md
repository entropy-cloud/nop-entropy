# 维度 01：依赖图与模块边界

## 依赖图

```
nop-stream (parent pom)
├── nop-stream-api           [EMPTY] (placeholder)
├── nop-stream-core           → nop-commons, nop-core
├── nop-stream-checkpoint     [EMPTY] (placeholder)
├── nop-stream-flink          [EMPTY] (placeholder)
├── nop-stream-flow           [EMPTY] (placeholder)
├── nop-stream-cep            → nop-stream-core, nop-xlang (optional, unused)
├── nop-stream-connector      → nop-stream-core, nop-batch-core (compile),
│                              nop-message-core (optional), nop-message-debezium (optional)
├── nop-stream-runtime        → nop-stream-core, nop-dao (provided),
│                              nop-message-core (test)
└── nop-stream-fraud-example  → nop-stream-cep

No circular dependencies detected.
```

### [维度01-01] nop-stream-fraud-example 使用 `${project.version}` 而其他内部模块依赖父 POM dependencyManagement

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:19-24`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-cep</artifactId>
      <version>${project.version}</version>  <!-- ← 其他模块不写 version -->
  </dependency>
  ```
- **严重程度**: P3
- **现状**: `nop-stream-fraud-example` 的内部依赖声明了显式 `${project.version}`，而其他所有子模块均依赖父 POM 的 `dependencyManagement` 统一管控版本。
- **风险**: 不影响构建正确性，但风格不一致。
- **建议**: 移除 `<version>${project.version}</version>`，与其他子模块保持一致。
- **信心水平**: 确定
- **误报排除**: 同一 reactor 内其他 4 个有内部依赖的模块全部不写 version，唯独这一个写了。
- **复核状态**: 未复核

### [维度01-02] nop-xlang 作为 optional 依赖在 nop-stream-cep 中声明但从未被 import

- **文件**: `nop-stream/nop-stream-cep/pom.xml:20-24`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-xlang</artifactId>
      <optional>true</optional>
  </dependency>
  ```
  全量搜索 `nop-stream-cep` 中 `import io.nop.xlang` 或 `import io.nop.xdef`：**0 matches**。
- **严重程度**: P2
- **现状**: `nop-xlang` 被声明为 optional 依赖，但在 CEP 模块的所有源码中没有任何 import 或运行时使用痕迹。
- **风险**: 误导开发者认为 CEP 模块有 xlang 集成能力；无谓扩大编译 classpath。
- **建议**: 若确无使用，移除该依赖；若为将来预留，加注释说明意图。
- **信心水平**: 很可能
- **误报排除**: "optional 依赖完全未使用"和"optional 依赖有实际使用但可选"性质不同。前者构成维护噪声。
- **复核状态**: 未复核

### [维度01-03] nop-batch-core 在 nop-stream-connector 中为 compile 作用域但未标记 optional

- **文件**: `nop-stream/nop-stream-connector/pom.xml:20-23`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-batch-core</artifactId>
      <!-- 无 <optional>true</optional> -->
  </dependency>
  ```
  对比同文件中 `nop-message-core` 和 `nop-message-debezium` 均标记了 `<optional>true</optional>`。
- **严重程度**: P2
- **现状**: `nop-batch-core` 是 compile-scope 强依赖。如果下游用户只使用 DebeziumCdcSourceFunction 而不需要 batch 功能，仍然必须引入 `nop-batch-core`。
- **风险**: 会作为传递依赖传播给所有依赖 `nop-stream-connector` 的模块，即使它们不使用 batch 相关功能。违反了 connector 模块对 `nop-message-*` 做的可选隔离模式。
- **建议**: 将 `nop-batch-core` 也标记为 `<optional>true</optional>`，与 `nop-message-core`/`nop-message-debezium` 保持一致。
- **信心水平**: 很可能
- **误报排除**: 同一个 connector 模块内，同性质的集成依赖采用了不同的 optional 策略，构成了真实的依赖传递不对称。
- **复核状态**: 未复核

### [维度01-04] 三个空 placeholder 模块持续占据 reactor 构建槽位

- **文件**: `nop-stream/nop-stream-checkpoint/pom.xml:12-13`, `nop-stream/nop-stream-flink/pom.xml:12-13`, `nop-stream/nop-stream-flow/pom.xml:12-13`
- **证据片段**:
  ```xml
  <artifactId>nop-stream-checkpoint</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P3
- **现状**: 三个空模块在 reactor 中注册但无任何源码。
- **风险**: 轻微增加构建时间。如果长期不实现，会积累技术债务印象。
- **建议**: 补充与 api 模块同等详细的注释说明规划意图，或考虑移出 reactor 直到实现。
- **信心水平**: 确定
- **误报排除**: 模块名暗示了明确的架构规划，问题是注释不够明确。
- **复核状态**: 未复核

### [维度01-05] nop-stream-fraud-example 实际依赖图与审计基线不一致

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:18-31`
- **证据片段**: POM 仅依赖 `nop-stream-cep`，代码中无 `import io.nop.stream.runtime.*`。
- **严重程度**: P3
- **现状**: 提供的基线声明 fraud-example 依赖 runtime，但实际不依赖。
- **风险**: 如果基线用于文档或依赖审计，会导致下游分析不准确。
- **建议**: 更新基线文档。
- **信心水平**: 确定
- **误报排除**: 基线与实际代码不一致属于审计范围内应报告的事项。
- **复核状态**: 未复核
