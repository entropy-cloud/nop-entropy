# 维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] README references non-existent RuntimeTopology pipeline layer

- **文件**: `nop-stream/README.md:5`
- **严重程度**: P3
- **现状**: README 描述五层管线包含 RuntimeTopology，但代码中不存在此类。RuntimeTopology 是设计文档中的概念层，尚未实现。实际运行时使用 GraphExecutionPlan（LOCAL模式）或 IStreamExecutionDispatcher（DISTRIBUTED模式）。
- **建议**: 更新 README 反映实际管线，或将 RuntimeTopology 标记为"规划中"。
- **误报排除**: 文档引用不存在的代码是真实的文档-代码不一致。
- **复核状态**: 未复核

### 已验证准确

- error-handling.md 中 StreamException 层次结构描述与代码一致
- README 模块状态表准确（4个规划中模块确无源码）
- README 设计文档引用路径正确
- README 构建命令有效
