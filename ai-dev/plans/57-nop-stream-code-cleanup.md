# Plan 57: nop-stream Code Cleanup & Style Fixes

> Plan Status: **draft**
> Created: 2026-05-26
> Parent Goal: nop-stream 模块完善

## Purpose

Deep audit round 1 发现的 P2/P3 级代码质量问题：import 排序违规、FQN 引用、未使用依赖、Java 版本不一致。这些不影响正确性但降低代码可维护性。

## Goals

1. **Import 排序修正**：修复 18 个源文件中 `io.nop.*` 排在 `org.slf4j.*` 等第三方库之前的违规，统一为 `java.* → jakarta.* → third-party → io.nop.*`
2. **FQN 清理**：修复 `GraphModelCheckpointExecutor.java` 中 32 处全限定类名引用，改为正常 import
3. **移除未使用依赖**：移除 CEP 模块中未使用的 `nop-xlang` 依赖
4. **Java 版本对齐**：将 `nop-stream-fraud-example` 的 `maven.compiler.source/target` 从 17 对齐到 21

## Non-Goals

- 不修改任何业务逻辑或行为
- 不增加新测试
- 不处理 oversized files（MemoryKeyedStateBackend 1199行等属于优化范畴，非 defect）
- 不处理 tab 字符和长行（P3 级别，deferred）

## Current Baseline

- 18 个文件 import 排序违规（io.nop.* 在 org.slf4j.* 之前）
- `GraphModelCheckpointExecutor.java` 使用 32 处 FQN
- `nop-stream-cep/pom.xml` 声明 `nop-xlang` 依赖但零引用
- `nop-stream-fraud-example` 使用 Java 17 编译，其余模块均为 Java 21

## Exit Criteria

- [ ] 18 个文件的 import 排序符合 `java.* → jakarta.* → third-party → io.nop.*` 规范
- [ ] `GraphModelCheckpointExecutor.java` 中 0 处 FQN（除必须的冲突解决外）
- [ ] `nop-stream-cep/pom.xml` 不再包含 `nop-xlang` 依赖
- [ ] `nop-stream-fraud-example` 使用 Java 21 编译
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全量通过

## Execution

### Slice 1: Import 排序 + FQN 清理

- [ ] 1.1 修复 18 个文件的 import 排序
- [ ] 1.2 修复 GraphModelCheckpointExecutor.java 的 FQN 引用
- [ ] 1.3 运行 `./mvnw test -pl nop-stream -am -T 1C`

### Slice 2: 依赖清理 + Java 版本对齐

- [ ] 2.1 从 `nop-stream-cep/pom.xml` 移除 `nop-xlang` 依赖
- [ ] 2.2 将 `nop-stream-fraud-example` 的 `maven.compiler.source/target` 改为 21
- [ ] 2.3 运行 `./mvnw test -pl nop-stream -am -T 1C`

## Closure Gates

- [ ] 所有 Exit Criteria 逐条通过
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全量通过
- [ ] daily log updated
