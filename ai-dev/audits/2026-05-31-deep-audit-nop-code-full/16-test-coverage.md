# 审核维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] NopCodeErrors 中 5 个已定义错误码从未被使用

- **文件**: `nop-code/nop-code-service/.../NopCodeErrors.java`
- **严重程度**: P2（非 P0，虽未使用但不构成运行时错误）
- **现状**: ERR_INDEX_DIRECTORY_FAILED、ERR_INCREMENTAL_NOT_SUPPORTED、ERR_INDEX_NOT_FOUND、ERR_SYMBOL_NOT_FOUND、ERR_CODE_INDEX_ID_REQUIRED 共 5 个已定义但零引用。
- **风险**: 索引/符号未找到等场景可能缺少错误处理。
- **建议**: 在 CodeIndexService 中使用这些错误码，或删除未使用定义。
- **复核状态**: 未复核

### [维度16-02] CodeIndexService 核心服务缺少针对核心方法的直接单元测试

- **文件**: `nop-code/nop-code-service/.../CodeIndexService.java`（1552行）
- **严重程度**: P2
- **现状**: TestCodeIndexService 测试的是 ProjectAnalyzer 层面，未测试 CodeIndexService 自身核心方法。
- **建议**: 为 indexDirectory 的 DB 持久化、缓存管理、增量索引等添加直接测试。
- **复核状态**: 未复核

### 合规项

- NopAutoTest 使用正确（所有集成测试 extend JunitAutoTestCase + @NopTestConfig(localDb=true)）
- 核心算法测试覆盖较好（CallGraph、CommunityDetector、FlowDetector、DeadCodeDetector 等）
- 66 个测试文件，分布合理
