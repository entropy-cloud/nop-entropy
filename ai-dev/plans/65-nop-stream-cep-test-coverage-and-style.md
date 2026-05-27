# 65 nop-stream CEP Test Coverage & Code Style Fixes

> Plan Status: planned
> Last Reviewed: 2026-05-27
> Source: `ai-dev/audits/2026-05-27-deep-audit-nop-stream/` (维度 16, 17, 01, 21)
> Related: Plan 57 (Code Cleanup, draft), Plan 64 (Error Codes, planned)
> Prerequisite: Plan 64 完成后执行，避免 import 排序在相同文件上的冲突

## Purpose

补全 CEP 模块测试覆盖、修复 import 排序违规、对齐 Java 版本。这些都是 2026-05-27 深度审计中的 P1-P2 问题。

## Current Baseline

- nop-stream-cep: 76 个主代码 .java 文件，22 个测试 .java 文件（覆盖率约 29%）
- CepOperator（593 行）仅由 `TestCepOperatorStateRecovery` 覆盖（状态恢复场景）；缺少基本事件匹配、超时检测和 keyed 状态隔离测试
- PatternStream/PatternStreamBuilder 无测试
- ~18 个文件 import 顺序不合规（`io.nop.*` 出现在 `org.slf4j` 之前，审计报告 D17-01 列出的具体文件）
- `nop-stream-fraud-example` 使用 Java 17（应为 Java 21）
- 构建/测试全部通过（300 tests, 0 failures）

## Goals

1. 为 CepOperator 添加补充测试覆盖基本事件匹配、keyed 状态隔离
2. 为 PatternStreamBuilder 添加基础构建测试
3. 为 CepOperator 超时检测添加测试
4. 批量修复 import 排序违规文件（D17-01 列出的约 18+ 个文件）
5. 对齐 fraud-example 的 Java 版本为 21

## Non-Goals

- 不修改任何生产代码的业务逻辑（仅修改 import 排序）
- 不为 NFA/SharedBuffer 等已有测试的类添加更多测试
- 不处理空壳模块（api/checkpoint/flink/flow，保留占位）
- 不拆分超大文件

## Scope

### In Scope

- `nop-stream-cep/test/`: 新增 CepOperator 测试、PatternStreamBuilder 测试
- `nop-stream-*`: import 排序违规文件（D17-01 列出的具体文件）
- `nop-stream-fraud-example/pom.xml`: Java 版本对齐

### Out Of Scope

- 生产代码修改（除 import 排序外）
- 空壳模块清理
- assertNotNull 替换（P3 级别）

## Execution Plan

### Phase 1 - CEP 测试补全

Status: planned
Targets: `nop-stream-cep/src/test/`, `CepOperator.java`, `PatternStreamBuilder.java`

- Item Types: `Proof`

- [ ] 创建 `TestCepOperatorBasic.java` — 测试 CepOperator 的基本事件匹配：单 pattern 匹配、多事件序列、keyed 状态隔离
- [ ] 创建 `TestPatternStreamBuilder.java` — 测试 PatternStreamBuilder 的 pattern 构建和 CEP 作业提交
- [ ] 创建 `TestCepOperatorTimeout.java` — 测试 CepOperator 的超时检测和超时输出

Exit Criteria:

- [ ] CepOperator 至少有 1 个新增测试文件覆盖基本匹配逻辑（与已有 TestCepOperatorStateRecovery 互补）
- [ ] PatternStreamBuilder 有基础构建测试
- [ ] `./mvnw test -pl nop-stream/nop-stream-cep -am -T 1C` 通过
- [ ] 新增测试数量 ≥ 3 个文件
- [ ] **端到端验证**: CepOperator 测试从 pattern 定义到结果输出完整路径
- [ ] **无静默跳过**: 新测试不使用空 catch 块
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Import 排序修复 + Java 版本对齐

Status: planned
Targets: D17-01 列出的约 18+ 个文件, `nop-stream-fraud-example/pom.xml`

- Item Types: `Fix`

- [ ] 修复 `nop-stream-core` 中 import 排序违规文件
- [ ] 修复 `nop-stream-runtime` 中 import 排序违规文件
- [ ] 修复 `nop-stream-cep` 中 import 排序违规文件
- [ ] 修复 `nop-stream-connector` 中 import 排序违规文件
- [ ] 修复 `nop-stream-fraud-example` 中 import 排序违规文件
- [ ] 移除 `nop-stream-fraud-example/pom.xml` 中的 Java 17 编译设置，使用父 pom 的 Java 21

Exit Criteria:

- [ ] D17-01 审计报告中列出的文件 import 顺序合规（验证方法：`io.nop.*` import 不出现在 `org.*` 之前）
- [ ] `nop-stream-fraud-example` 使用 Java 21 编译
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] CepOperator 有新增补充测试（D16-01）
- [ ] PatternStreamBuilder 有基础测试
- [ ] D17-01 列出的文件 import 排序合规
- [ ] fraud-example Java 版本为 21（D01-03）
- [ ] `./mvnw clean install -pl nop-stream -am -T 1C` 通过
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

### assertNotNull 过度使用 (D21-P5)

- Classification: `optimization candidate`
- Why Not Blocking Closure: 现有测试已验证核心逻辑，assertNotNull 使用虽多但多数在合理场景（分布式测试）
- Successor Required: no

### 空壳模块清理 (D01-01)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 按设计文档保留占位，无功能影响
- Successor Required: no

## Non-Blocking Follow-ups

- StreamSourceOperator 独立测试（D16-02）
- ChainingOutput 测试（D16-02）
- StreamGraphGenerator assertNotNull 优化（D21-P5）

## Closure

Status Note: (待 closure audit 后填写)

Closure Audit Evidence: (待 closure audit 后填写)
