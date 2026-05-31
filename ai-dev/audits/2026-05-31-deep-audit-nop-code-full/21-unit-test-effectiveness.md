# 审核维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestBuildHierarchyCycleProtection 测试基础设施而非业务逻辑

- **文件**: `nop-code/nop-code-service/.../TestBuildHierarchyCycleProtection.java`
- **严重程度**: P2
- **现状**: 测试 Math.min() 和 HashSet 的基本行为，而非 CodeIndexService.buildTypeHierarchy() 的实际循环检测。
- **建议**: 重写为测试实际的层次结构构建逻辑。
- **复核状态**: 未复核

### [维度21-02] TestDeterministicEntityIds 测试 DigestHelper 而非实际 ID 生成逻辑

- **文件**: `nop-code/nop-code-service/.../TestDeterministicEntityIds.java`
- **严重程度**: P2
- **现状**: 测试 DigestHelper.sha256Hex() 的幂等性，但实际 ID 生成使用不同格式。
- **建议**: 重写为测试 CodeIndexService 中实际的 ID 生成逻辑。
- **复核状态**: 未复核

### 合规项

- 核心算法测试覆盖较好（CallGraph、CommunityDetector、FlowDetector 等）
- 枚举测试虽简单但可接受（防止值碰撞）
- 大量 BizModel 方法只测 happy path（P3，可排期改进）
