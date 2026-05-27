# 维度 01：依赖图与模块边界

**审计日期**: 2026-05-27  
**审计范围**: nop-stream 全部 9 个子模块

## 模块结构总览

| 模块 | 实际代码 | 依赖方向 | 状态 |
|------|---------|---------|------|
| nop-stream-api | ❌ 空壳 (placeholder) | — | 预留 |
| nop-stream-core | ✅ 406 源文件 | nop-commons, nop-core | 正常 |
| nop-stream-checkpoint | ❌ 空壳 (placeholder) | — | 预留 |
| nop-stream-flink | ❌ 空壳 (placeholder) | — | 预留 |
| nop-stream-flow | ❌ 空壳 (placeholder) | — | 预留 |
| nop-stream-cep | ✅ 82 源文件 | nop-stream-core, nop-xlang | 正常 |
| nop-stream-connector | ✅ 6 源文件 | nop-stream-core, nop-batch-core, nop-message-core(opt), nop-message-debezium(opt) | 正常 |
| nop-stream-runtime | ✅ 42 源文件 | nop-stream-core, nop-dao(provided), nop-message-core(test) | 正常 |
| nop-stream-fraud-example | ✅ 9 源文件 | nop-stream-cep | 正常 |

## 依赖方向验证

### 合规依赖方向
```
nop-stream-core ← nop-stream-cep (✅)
nop-stream-core ← nop-stream-connector (✅)
nop-stream-core ← nop-stream-runtime (✅)
nop-stream-cep ← nop-stream-fraud-example (✅)
```

### 未发现反向依赖 ✅
- nop-stream-core 不依赖任何 nop-stream 子模块
- nop-stream-cep 不依赖 nop-stream-runtime
- nop-stream-connector 不依赖 nop-stream-runtime

## 发现

### D01-01: 4 个空壳 placeholder 模块
- **文件**: `nop-stream-api/pom.xml`, `nop-stream-checkpoint/pom.xml`, `nop-stream-flink/pom.xml`, `nop-stream-flow/pom.xml`
- **严重程度**: P2
- **现状**: 这些模块仅包含 `pom.xml`，注释标记为 "planned but not implemented"，无任何 Java 代码。在父 pom 的 `<modules>` 中声明并参与构建。
- **风险**: 增加构建时间，Maven reactor 需要处理这些空模块；对使用者造成困惑。
- **建议**: 如果这些模块近期不会实现，考虑从 `<modules>` 中移除，仅保留目录和 pom.xml 作为占位。或者至少在根 pom.xml 中添加注释说明预期。

### D01-02: nop-stream-api 职责未提取
- **文件**: `nop-stream-api/pom.xml`
- **严重程度**: P2
- **现状**: pom.xml 注释说 "interfaces are in nop-stream-core; this module is reserved for future API extraction"。当前所有公开 API 接口都在 nop-stream-core 中。
- **风险**: 外部消费者依赖 nop-stream-core 会引入不必要的传递依赖（nop-core）。
- **建议**: 按计划将纯 API 接口提取到 nop-stream-api，实现消费者与实现的解耦。

### D01-03: nop-stream-fraud-example 使用 Java 17 而项目要求 Java 21
- **文件**: `nop-stream-fraud-example/pom.xml:16-17`
- **严重程度**: P2
- **现状**: `maven.compiler.source=17, maven.compiler.target=17`，而其他模块使用默认的 Java 21（从父 pom 继承）。
- **风险**: 可能导致编译行为不一致；如果代码使用了 Java 21 特性，example 模块可能编译失败。
- **建议**: 移除 fraud-example 的自定义 compiler 版本，使用父 pom 的 Java 21 设置。

### D01-04: nop-stream-connector 对 nop-batch-core 的依赖
- **文件**: `nop-stream-connector/pom.xml`
- **严重程度**: P2
- **现状**: nop-stream-connector 依赖 nop-batch-core，这引入了 batch 模块的传递依赖。
- **风险**: 流处理模块不应依赖批处理模块，违反关注点分离。
- **建议**: 检查 `BatchLoaderSourceFunction` 是否可以只依赖必要的接口而非整个 batch-core。

### D01-05: nop-stream-runtime 对 nop-dao 的 provided 依赖
- **文件**: `nop-stream-runtime/pom.xml`
- **严重程度**: P3
- **现状**: `nop-dao` 声明为 `provided` scope，用于 `JdbcCheckpointStorage` 和 `JdbcClusterRegistry`。
- **风险**: 在运行时如果 nop-dao 不在 classpath 上，JDBC 存储将不可用，但没有编译时错误提示。
- **建议**: 这是合理的 provided 用法（可选功能），但应在文档中说明部署时需包含 nop-dao。

## 总结

依赖方向整体合规，无反向依赖问题。主要问题是 4 个空壳模块和 API 未提取。
