# 维度16+17+18+19+20：测试/风格/文档/命名/跨模块

## 维度16：测试覆盖

### [维度16-01] 核心 BizModel 方法缺少测试覆盖

- **文件**: `NopCodeIndexBizModel.java`, `NopCodeSymbolBizModel.java`
- **严重程度**: P2
- **现状**: 依赖图操作（getDeps/getReverseDeps/findCycles/getDepGraph）、高级图分析（getCriticalNodes/getKnowledgeGaps/exportGraph/diffGraph）、索引管理（indexFile/deleteIndex）、变更分析等全线无测试。
- **建议**: 补充核心方法的集成测试。
- **复核状态**: 未复核

---

### [维度16-02] Flow 相关测试断言过于宽松

- **文件**: `TestNopCodeFlowBizModel.java:66-109`
- **严重程度**: P3
- **现状**: 使用 `if (response.isOk())` 模式，不验证数据正确性，空数据也能通过。
- **建议**: 添加具体数据断言。
- **复核状态**: 未复核

---

### [维度16-03] AutoTest 快照完全缺失

- **严重程度**: P3
- **现状**: 无 `*.auto-test.yaml`、无 `expected-output` 目录。
- **建议**: 为核心方法添加 AutoTest 快照。
- **复核状态**: 未复核

---

## 维度17：代码风格

### [维度17-01] System.out.println 用于生产代码

- **文件**: `NopCodeApplication.java:31`
- **严重程度**: P2
- **现状**: `System.out.println("started")`。
- **建议**: 改为 `LOG.info()` 或移除。
- **复核状态**: 未复核

---

### [维度17-02] import 分组系统性违反项目规范

- **文件**: 全模块手写源码
- **严重程度**: P2
- **现状**: 所有文件采用 `io.nop.* → third-party → jakarta.* → java.*`，而规范要求 `java.* → jakarta.* → third-party → io.nop.*`。
- **建议**: 统一修正 import 顺序。
- **复核状态**: 未复核

---

## 维度18：文档一致性

### [维度18-01] 文档声称 nop-code 不在根 pom.xml modules 中，与事实不符

- **文件**: `docs-for-ai/02-core-guides/debugging-and-diagnostics.md:119`
- **严重程度**: P2
- **现状**: 文档说"nop-code 不在根 pom.xml 的 modules 中"，实际已在根 pom.xml 第 562 行。
- **建议**: 更新文档。
- **复核状态**: 未复核

---

### [维度18-02] nop-code 模块缺少专属技术文档

- **严重程度**: P3
- **现状**: 缺少架构图、索引流程说明、API 清单、语言适配器扩展指南。
- **建议**: 补充文档。
- **复核状态**: 未复核

---

## 维度19：命名一致性

### [维度19-01] NopCodeDependency 主键字段名不一致

- **文件**: `nop-code.orm.xml:594-595`
- **严重程度**: P2
- **现状**: 9 个实体用 `id`，唯独 NopCodeDependency 用 `depId`。
- **建议**: 统一为 `id`。
- **复核状态**: 未复核

---

## 维度20：跨模块契约

### [维度20-01] nop-code-api 为孤立模块

- **文件**: `nop-code-api/`
- **严重程度**: P2
- **现状**: 无实现、无消费者、API 风格不匹配、Java 版本不匹配、无父 POM。
- **建议**: 删除或对接实现。
- **复核状态**: 未复核

---

### [维度20-02] NopCodeConfigs 和 NopCodeConstants 为空壳接口

- **文件**: `NopCodeConfigs.java`, `NopCodeConstants.java`
- **严重程度**: P3
- **现状**: 空接口，但 CodeIndexService 中有硬编码常量应抽取。
- **建议**: 将 BATCH_SIZE 等常量抽取到这些接口中。
- **复核状态**: 未复核
