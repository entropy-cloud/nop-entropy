# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] BizModel 核心方法边界条件测试不充分

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeIndexBizModel.java`
- **严重程度**: P2
- **现状**: indexFile 有 MAX_SOURCE_CODE_BYTES（1MB）限制但无测试。triggerFullIndex 的路径校验未测试。detectCommunities 的 null-fallback 未验证。
- **风险**: 边界条件保护无测试保障，回归风险。
- **建议**: 增加 sourceCode 超限、路径遍历、null-fallback 的测试用例。
- **信心水平**: 高
- **误报排除**: 这些是 BizModel 层的关键防御性逻辑，有专门的测试文件（TestBizModelErrorPaths）但未覆盖全部边界。
- **复核状态**: 未复核

### [维度16-02] ICodeIndexService 部分查询方法缺少测试

- **文件**: `nop-code/nop-code-service/src/test/java/`
- **严重程度**: P3
- **现状**: getDepGraph、findDependentFiles、getKnowledgeGaps、getAffectedFlows、diffGraph 等方法无测试。
- **风险**: GraphQL action 注册和参数绑定未经验证。
- **建议**: 优先为 getDepGraph、diffGraph 添加集成测试。
- **信心水平**: 高
- **误报排除**: 这些方法的 BizModel 层代码简单（参数默认值+委托），但缺少集成测试意味着参数绑定未验证。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度16-01] | P2 | TestNopCodeIndexBizModel.java | 核心方法边界条件测试不充分 |
| [维度16-02] | P3 | nop-code-service/src/test/ | 部分查询方法缺少测试 |
