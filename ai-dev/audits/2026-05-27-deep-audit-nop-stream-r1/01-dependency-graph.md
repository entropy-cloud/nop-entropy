# 维度 01 审计报告：nop-stream 依赖图与模块边界

> 审计日期: 2026-05-27
> 审计对象: nop-stream 全部子模块

## 依赖图总览（实际，基于 pom.xml）

```
                    ┌─────────────────┐
                    │  nop-stream-api │  (空壳，无 src)
                    └─────────────────┘

                    ┌─────────────────┐
         ┌─────────│ nop-stream-core │──────────┐
         │         └─────────────────┘          │
         │  deps: nop-commons, nop-core         │
         │                                       │
    ┌────┴──────────┐  ┌──────────────────┐  ┌──┴──────────────┐
    │ nop-stream-cep│  │nop-stream-connector│  │nop-stream-runtime│
    └───────────────┘  └──────────────────┘  └─────────────────┘
    + nop-xlang       + nop-batch-core       + nop-dao (provided)
                      + nop-message-core (optional)
                      + nop-message-debezium (optional)

    ┌──────────────────────┐
    │ nop-stream-fraud-    │
    │     example          │──→ nop-stream-cep
    └──────────────────────┘

    ┌─────────────────────┐
    │ nop-stream-checkpoint│  (空壳)
    │ nop-stream-flink     │  (空壳)
    │ nop-stream-flow      │  (空壳)
    └─────────────────────┘
```

**无循环依赖。** 所有内部模块箭头单向指向 core，无反向依赖。

---

## 发现

### [维度01-01] nop-stream-api 空壳导致 API 契约模糊

- **文件**: `nop-stream/nop-stream-api/pom.xml:12-14`
- **行号**: 12-14
- **证据代码**:
  ```xml
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  <!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
  ```
- **严重程度**: P2
- **现状**: 该模块在父 pom 的 `<modules>` 列表中排第一位（`nop-stream/pom.xml:17`），但没有 `src/` 目录、没有任何 Java 文件、没有任何依赖。注释承认"接口目前仍在 core 中"。
- **风险**: 外部消费者不清楚应该依赖 `nop-stream-api` 还是 `nop-stream-core`。当前任何依赖 `nop-stream-core` 的模块实际上都绑定了实现细节而非纯接口。如果未来真的将接口抽出到 api，所有下游模块的依赖声明都需要同步修改。
- **建议**: 短期在父 pom 的注释中明确标注 "WIP — consumers should depend on nop-stream-core for now"；中期考虑完成接口抽取，或在不需要时移除该模块以减少混淆。

---

### [维度01-02] nop-stream-runtime 缺少 spec 规定的 cep/connector 依赖

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:21-24`
- **行号**: 21-24
- **证据代码**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-stream-core</artifactId>
  </dependency>
  ```
  仅声明了 core，无 cep 和 connector。
- **严重程度**: P2
- **现状**: spec 规定 "nop-stream-runtime 依赖 core + cep + connector（可依赖所有实现子模块）"，但 pom.xml 实际只依赖 core。经代码扫描验证，runtime 的 main 和 test 源码确实不导入任何 `io.nop.stream.cep.*` 或 `io.nop.stream.connector.*` 的类。runtime 当前只实现了 checkpoint 协调、集群注册、任务调度、传输层等功能，尚未集成 CEP 和 connector 的运行时编排。
- **风险**: spec 与代码不同步。当 runtime 未来需要编排 CEP pattern 或 connector source/sink 时，会突然引入新依赖，可能导致模块分层规则的突然调整。
- **建议**: 如果 spec 是预期设计，在 pom.xml 中添加注释说明 cep/connector 待后续集成；如果 spec 已过时，更新分层规则文档使其与实际代码一致。

---

### [维度01-03] nop-stream-fraud-example 缺少 spec 规定的 runtime 依赖

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:18-32`
- **行号**: 18-32
- **证据代码**:
  ```xml
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-stream-cep</artifactId>
          <version>${project.version}</version>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: spec 规定 "nop-stream-fraud-example 依赖 runtime + cep"，但实际仅依赖 cep。代码扫描确认无任何 `io.nop.stream.runtime.*` 的导入。
- **风险**: 低。示例功能完整可编译可测试。spec 文档描述的是"理想"依赖。
- **建议**: 更新 spec 规则，将 fraud-example 的依赖改为 "依赖 cep（传递获得 core）"，与实际代码一致。

---

### [维度01-04] nop-stream-fraud-example 对兄弟模块使用显式版本号

- **文件**: `nop-stream/nop-stream-fraud-example/pom.xml:24`
- **行号**: 24
- **证据代码**:
  ```xml
  <version>${project.version}</version>
  ```
- **严重程度**: P3
- **现状**: 该模块对 `nop-stream-cep` 显式指定了 `${project.version}`。而 cep、connector、runtime 等其他子模块依赖兄弟模块时都不指定 version。
- **风险**: 风格不一致。
- **建议**: 移除 `<version>${project.version}</version>` 行，与其他子模块保持一致。

---

### [维度01-05] nop-stream-runtime 声明了冗余的 maven.compiler 属性

- **文件**: `nop-stream/nop-stream-runtime/pom.xml:14-18`
- **行号**: 14-18
- **证据代码**:
  ```xml
  <properties>
      <maven.compiler.source>21</maven.compiler.source>
      <maven.compiler.target>21</maven.compiler.target>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  ```
- **严重程度**: P3
- **现状**: 根 pom 已统一设置了 Java 21 编译级别。nop-stream-runtime 是 nop-stream 下唯一声明这些属性的子模块。
- **风险**: 如果根 pom 将来调整编译级别，此处会覆盖全局设置造成不一致。
- **建议**: 删除这 3 个 property，使用根 pom 的统一配置。

---

### [维度01-06] nop-stream-connector 声明了 nop-message-core 为 optional 但无代码实际使用

- **文件**: `nop-stream/nop-stream-connector/pom.xml:25-29`
- **行号**: 25-29
- **证据代码**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
      <optional>true</optional>
  </dependency>
  ```
- **严重程度**: P3
- **现状**: 全量扫描 connector 的 main 源码，未发现任何 `import io.nop.message.core.*` 的引用。
- **风险**: 增加了不必要的依赖声明，可能误导维护者。
- **建议**: 移除 `nop-message-core` 的 optional 依赖声明。

---

## 合规检查矩阵

| 规则 | 预期 | 实际 | 合规 |
|------|------|------|------|
| 1. api 只定义纯接口 | 空壳预留 | 空壳无源码 | ⚠️ 未实现 |
| 2. core 依赖 api+框架 | ✅ | ✅ 无越界依赖 | ✅ |
| 3. cep 依赖 core+api | ✅ | ✅ 无越界依赖 | ✅ |
| 4. connector 依赖 core+api+nop-batch-core | ✅ | ✅ 正确 | ✅ |
| 5. runtime 依赖 core+cep+connector | ❌ 仅 core | ⚠️ spec漂移 |
| 6. checkpoint/flink/flow 为空壳 | ✅ | ✅ | ✅ |
| 7. fraud-example 依赖 runtime+cep | ❌ 仅 cep | ⚠️ spec漂移 |
| 8. 无循环依赖 | ✅ | ✅ | ✅ |

## 总结

nop-stream 模块边界整体健康，**无循环依赖、无越界 import**。主要问题集中在 **spec 与实现之间的漂移**以及 **api 模块空壳导致的契约模糊**。均为 P2/P3 问题。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 01-01 | P2 | nop-stream-api/pom.xml | api 空壳导致 API 契约模糊 |
| 01-02 | P2 | nop-stream-runtime/pom.xml | runtime 缺 spec 规定的 cep/connector 依赖 |
| 01-03 | P3 | nop-stream-fraud-example/pom.xml | fraud-example 缺 spec 规定的 runtime 依赖 |
| 01-04 | P3 | nop-stream-fraud-example/pom.xml | 对兄弟模块使用显式版本号 |
| 01-05 | P3 | nop-stream-runtime/pom.xml | 冗余 maven.compiler 属性 |
| 01-06 | P3 | nop-stream-connector/pom.xml | 未使用的 nop-message-core optional 依赖 |
