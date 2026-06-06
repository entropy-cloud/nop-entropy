# 维度 15：类型安全与泛型使用 — nop-code 模块

## 第 1 轮（初审）

### [维度15-01] SpringEventSynthesizer 不一致的无检查转换

- **文件**: `SpringEventSynthesizer.java:91-103`
- **严重程度**: P3
- **现状**: 第 101 行 cast 到 Map 缺少本地 @SuppressWarnings。
- **复核状态**: 未复核

### [维度15-02] CodeIndexService.getIndexStats 使用未类型化 Map 选择字段

- **文件**: `CodeIndexService.java:530-541`
- **严重程度**: P3（框架 API 标准模式）
- **复核状态**: 未复核

### 正面发现

- 所有 11 个 INopCode*Biz 接口正确扩展 ICrudBiz<EntityType> 并指定泛型参数
- 30+ DTO 类正确使用 @DataBean 和适当字段类型
- 无原始类型集合实例化
