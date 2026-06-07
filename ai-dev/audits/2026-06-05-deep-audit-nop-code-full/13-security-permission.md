# 维度 13：安全与权限模型 — nop-code 模块

## 第 1 轮（初审）

### [维度13-01] indexFile mutation 接受无大小限制的 sourceCode 参数

- **文件**: `NopCodeIndexBizModel.java:99-107`
- **证据片段**: `@BizMutation @Auth(roles = "admin")` 方法接受任意大小 sourceCode 字符串。
- **严重程度**: P1
- **现状**: 无 sourceCode 大小验证，可导致内存耗尽或超长分析时间。
- **风险**: DoS 风险。admin 用户可发送超大 sourceCode 导致服务不可用。
- **建议**: 添加 sourceCode 大小验证（如最大 1MB）。
- **信心水平**: 很可能
- **误报排除**: @Auth(roles="admin") 提供基本保护但不防 DoS。
- **复核状态**: 未复核

### [维度13-02] exportGraph 的 format 参数未验证

- **文件**: `NopCodeIndexBizModel.java:251-259`
- **严重程度**: P2
- **建议**: 添加格式白名单验证。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度13-03] validatePath 仅检查 ".." 但不检查其他路径注入模式

- **文件**: `CodeIndexService.java:1870-1893`
- **严重程度**: P2
- **现状**: allowedLocalRoot 默认为 null，未配置时不检查路径范围。
- **建议**: 在 BizModel 层验证 allowedLocalRoot 已配置。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度13-04] xmeta 字段级权限 — 几乎所有属性 insertable=true updatable=true

- **文件**: `_NopCodeSymbol.xmeta:23-230` 及其他生成 xmeta
- **严重程度**: P3
- **现状**: 通过标准 CRUD mutation 可修改 indexId、fileId、extData 等关键字段。
- **建议**: 在 delta xmeta 中设置不需要用户修改的字段为 updatable="false"。
- **信心水平**: 很可能
- **复核状态**: 未复核

### 正面发现

- 所有 48 个公开方法有 @Auth 注解，模式一致
- @BizMutation 使用 roles="admin"，查询使用 permissions="code-query"

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 13-01 | P1 | NopCodeIndexBizModel.java:99 | sourceCode 无大小限制 DoS 风险 |
| 13-02 | P2 | NopCodeIndexBizModel.java:251 | format 参数未验证 |
| 13-03 | P2 | CodeIndexService.java:1870 | 路径验证不充分 |
| 13-04 | P3 | 生成 xmeta | 字段权限过于宽松 |
