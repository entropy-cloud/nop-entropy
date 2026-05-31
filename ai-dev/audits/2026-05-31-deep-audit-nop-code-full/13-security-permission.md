# 审核维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] NopCodeSymbolBizModel 返回源代码的方法无 @Auth 注解

- **文件**: `nop-code/nop-code-service/.../NopCodeSymbolBizModel.java:49-229`
- **证据片段**: showSymbol、sourceCode(@BizLoader)、searchCode 等 13 个 @BizQuery 方法均无 @Auth
- **严重程度**: P2
- **现状**: 这些方法可通过 GraphQL 直接访问，无需任何角色或权限。其中 showSymbol/sourceCode/searchCode 可返回源代码。
- **风险**: 拥有任何 query 权限的用户都可以通过这些 API 获取源代码。
- **建议**: 对 showSymbol、sourceCode、searchCode 添加 `@Auth(permissions = "code-source-read")`。
- **信心水平**: 90%
- **误报排除**: exportGraph 方法有 code-source-read 权限，但功能相似的 showSymbol 没有。
- **复核状态**: 未复核

### [维度13-02] NopCodeFileBizModel 的 sourceCode BizLoader 无 @Auth 注解

- **文件**: `nop-code/nop-code-service/.../NopCodeFileBizModel.java:70-73`
- **证据片段**: sourceCode BizLoader 直接返回文件源代码，无权限检查
- **严重程度**: P2
- **现状**: 虽然源代码 xmeta 已设置 published=false，但通过自定义 BizLoader 仍可获取。
- **建议**: 添加 `@Auth(permissions = "code-source-read")`。
- **信心水平**: 85%
- **复核状态**: 未复核

### 合规项

- action-auth.xml 权限配置合理覆盖所有 11 个实体
- xmeta 中无 SQL 注入风险（全部参数化查询）
- NopCodeFile sourceCode 字段 xmeta 权限控制合理（published=false）
- @BizMutation 方法全部有 @Auth(roles="admin")
