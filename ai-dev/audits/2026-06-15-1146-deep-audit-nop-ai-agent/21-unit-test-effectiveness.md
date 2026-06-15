# 维度 21：单元测试有效性（nop-ai-agent）

> 本维度的完整发现已与维度 16 合并审计，记录在 `16-test-coverage.md` 中（标记为 [维度21-1]~[维度21-7]）。本文件仅作目录结构占位与索引。

## 审计依据

- `ai-dev/skills/unit-test-antipatterns.md`（P-1~P-8 反模式清单）
- `docs-for-ai/02-core-guides/testing.md`

## 反模式命中摘要

| 反模式 | 命中文件数 | 详见 |
|--------|----------|------|
| P-1 纯 getter/setter 往返/枚举计数 | ~10 | 16-test-coverage.md [21-1][21-3][16-5] |
| P-2 测试元数据/类层级而非行为 | ~10 | 16-test-coverage.md [21-2][21-4][21-7] |
| P-3 只测 happy path | 1 | 16-test-coverage.md [16-1]（已被兄弟文件缓解） |
| P-4 测试与实现同义反复 | 4 | 16-test-coverage.md [21-5] |
| P-5 过度 assertNotNull | 1 | 16-test-coverage.md [21-6] |
| P-6 方法名不表达真实断言 | 2 | 16-test-coverage.md [21-6][21-7] |
| P-7 测试间隐式依赖 | 0 | — |
| P-8 无效的负面测试 | 0 | — |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 21-1 | P3 | security/TestChannelKind.java 等 5 个 | 枚举类测试纯计数+valueOf（P-1/P-2） |
| 21-2 | P3 | skill/TestISkillProvider.java 等 7 个 | implementsXxx 元数据测试（P-2） |
| 21-3 | P3 | security/TestAuditEvent.java 等 7 个 | 值对象纯 getter/setter 往返（P-1） |
| 21-4 | P3 | completion/TestCompletionDecision.java 等 3 个 | concreteTypesExtendXxx 元数据（P-2/P-5） |
| 21-5 | P3 | compact/TestMicroCompressionCompactor.java 等 4 个 | 常量集合/阈值镜像断言（P-4） |
| 21-6 | P3 | router/TestRoutingResult.java:57-66 | toStringContainsFields 纯 assertNotNull（P-5/P-6） |
| 21-7 | P3 | skill/TestISkillProvider.java:42-44 | 私有 assertTrue shim（P-2/P-6） |
