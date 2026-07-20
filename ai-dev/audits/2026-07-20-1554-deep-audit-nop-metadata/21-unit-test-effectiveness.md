# 维度 21：单元测试有效性 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度21-01] Thread.sleep(1100) 是脆弱测试反模式（P2）

- **文件**: `TestNopMetaTableBizModel.java:203`
- **说明**: `testProfileTimeSeriesAppend()` 使用 Thread.sleep(1100) 依赖实时时钟区分前后状态。在 CI 低 CPU/高负载下可能不稳定。
- **严重程度**: P2
- **建议**: 使用注入的 IClock 接口或基于查询排序的区分策略。

### 正面发现

- 所有测试使用 AutoTest 正确配置
- 测试通过 IGraphQLEngine 端到端调用真实 BizModel
- 断言使用具体值比较（反空洞模式）
- 错误路径测试覆盖完善
