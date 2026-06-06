# 维度 20：跨模块契约一致性

## 审计日期
2026-06-06

## 第 1 轮（初审）

### [维度20-01] nop-code-api 是空壳模块（与维度01-01重复）

- **严重程度**: P1（已在维度01报告）
- **现状**: 标准 Nop 模式期望 api 模块包含服务接口和 DTO。当前所有接口在 service 模块中。
- **建议**: 将 ICodeIndexService 和所有 DTO 移到 nop-code-api。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度20-02] nop-search-api 集成正确

- **现状**: ISearchEngine 使用 @Nullable 正确可选注入，null 检查遍布代码。零发现。

### [维度20-03] 配置项全部硬编码，未外部化

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConfigs.java`
- **严重程度**: P2
- **现状**: allowedLocalRoot、MAX_QUERY_RESULTS、BATCH_SIZE 等全部硬编码常量。NopCodeConfigs 为空接口。
- **风险**: 无法不修改代码调整性能参数或安全设置。
- **建议**: 通过 @InjectValue 外部化关键配置项到 NopCodeConfigs。
- **信心水平**: 确定
- **复核状态**: 未复核
