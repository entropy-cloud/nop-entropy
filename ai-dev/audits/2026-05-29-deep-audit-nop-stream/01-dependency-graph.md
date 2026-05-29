# 维度01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-stream-connector 的 nop-message-core 依赖作用域不匹配

- **文件**: `nop-stream/nop-stream-connector/pom.xml:25-29`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: `nop-message-core` 被声明为 `optional`，但实际仅在测试代码中使用（`TestMessageAdapters.java`）。connector 的 main 代码中无任何 `io.nop.message.core.*` 的 import。`MessageSourceFunction` 和 `MessageSinkFunction` 使用的是 `io.nop.api.core.message.*`（来自 `nop-api-core`，已通过传递可用）。
- **风险**: `optional` 将该依赖暴露到 main 编译期 classpath，可能误导开发者在 main 源码中引入 `nop-message-core` 的类，导致运行时 `NoClassDefFoundError`。
- **建议**: 将 `<optional>true</optional>` 改为 `<scope>test</scope>`，与实际使用位置一致。
- **信心水平**: 确定
- **误报排除**: 不是"多模块 pom.xml 中存在必要的传递依赖声明"（误报校准条款），因为此依赖在 main 代码中完全未使用，且 `optional` 与 `test` scope 在语义上有明确差异。
- **复核状态**: 未复核

---

### [维度01-02] nop-bom 缺失 nop-stream-runtime 和 nop-stream-connector 的版本管理条目

- **文件**: `nop-bom/pom.xml:1130-1135`
- **证据片段**:
  ```xml
  <!-- nop-bom/pom.xml 中已有的 nop-stream 条目（行 1106-1134）： -->
  <artifactId>nop-stream-api</artifactId>        <!-- 空占位 -->
  <artifactId>nop-stream-core</artifactId>       <!-- ✓ 有代码 -->
  <artifactId>nop-stream-flow</artifactId>       <!-- 空占位 -->
  <artifactId>nop-stream-cep</artifactId>        <!-- ✓ 有代码 -->
  <artifactId>nop-stream-checkpoint</artifactId> <!-- 空占位 -->
  <!-- 以下模块完全缺失： -->
  <!-- nop-stream-connector（6 个 main Java 文件） -->
  <!-- nop-stream-runtime（40 个 main Java 文件） -->
  <!-- nop-stream-flink（空占位） -->
  <!-- nop-stream-fraud-example（10 个 main Java 文件） -->
  ```
- **严重程度**: P2
- **现状**: `nop-bom` 的 `dependencyManagement` 只收录了 5 个 nop-stream 子模块（api、core、flow、cep、checkpoint），其中 3 个是空占位模块。真正有代码的 `nop-stream-runtime`（40 个 main Java 文件）和 `nop-stream-connector`（6 个 main Java 文件）未被收录。
- **风险**: 外部消费者通过 `nop-bom` 管理版本时，无法获得 `nop-stream-runtime` 和 `nop-stream-connector` 的版本管控。若外部项目需要使用它们，必须手动指定版本，违背 BOM 统一版本管理约定。
- **建议**: 在 `nop-bom/pom.xml` 的 `dependencyManagement` 中补充 `nop-stream-connector`、`nop-stream-runtime`、`nop-stream-flink`、`nop-stream-fraud-example` 四个条目。同步更新 `tests/pom.xml`。
- **信心水平**: 很可能（当前无外部消费者引用，但结构性缺口确实存在）
- **误报排除**: 不是"多模块 pom.xml 中存在必要的传递依赖声明"，而是 BOM 作为公共契约的完整性问题。BOM 收录了 3 个空占位模块却遗漏了 2 个有实际代码的模块。
- **复核状态**: 未复核

---

### [维度01-03] nop-stream-fraud-example 冗余指定依赖版本

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:22-24`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-cep</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: `nop-stream-fraud-example` 显式使用 `${project.version}` 指定 `nop-stream-cep` 版本，但 `nop-stream-cep` 已在 `nop-bom` 中被 `dependencyManagement` 管理。同模块的其他子模块（cep、connector、runtime）对内部依赖均未指定版本。这是唯一一个显式指定版本的子模块。
- **风险**: 低。违反同一模块组内统一的版本管理约定，增加了维护成本。
- **建议**: 移除 `<version>${project.version}</version>` 行，与同组其他子模块保持一致。
- **信心水平**: 确定
- **误报排除**: 有量化依据的约定不一致——9 个子模块中仅此 1 个显式指定了版本。
- **复核状态**: 未复核

---

## 依赖图

```
nop-stream (parent POM, packaging=pom)
│
├── nop-stream-api ─────────── [EMPTY, no dependencies]
├── nop-stream-core ────────── [→ nop-commons, nop-core]
├── nop-stream-checkpoint ──── [EMPTY, no dependencies]
├── nop-stream-flink ───────── [EMPTY, no dependencies]
├── nop-stream-flow ────────── [EMPTY, no dependencies]
├── nop-stream-cep ─────────── [→ nop-stream-core, nop-xlang]
├── nop-stream-connector ───── [→ nop-stream-core, nop-batch-core, nop-message-core(opt→test?), nop-message-debezium(opt)]
├── nop-stream-runtime ─────── [→ nop-stream-core, nop-dao(provided), nop-message-core(test)]
└── nop-stream-fraud-example ─ [→ nop-stream-cep]
```

**依赖方向合规性**：无循环依赖 ✓ 无反向依赖 ✓ 无跨层耦合 ✓
