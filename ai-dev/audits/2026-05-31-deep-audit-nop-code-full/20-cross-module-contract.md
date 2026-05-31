# 审核维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] nop-code-api 模块为空壳，服务接口定义在 service 模块中

- **严重程度**: P3
- **现状**: ICodeIndexService 定义在 nop-code-service 中而非 nop-code-api。如果将来有模块需要消费 code API，必须依赖整个 nop-code-service。
- **建议**: 当前无外部消费者可暂缓。将来需要时将 ICodeIndexService 和 DTO 迁移到 api 模块。
- **复核状态**: 未复核

### 合规项

- nop-search-api 集成正确（optional=true, @Nullable, 优雅回退到 DB 查询）
