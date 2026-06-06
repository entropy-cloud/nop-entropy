# 维度 11：XMeta 与 BizModel 对齐

## 第 1 轮（初审）

未发现问题。

### 检查范围

手写 xmeta 文件（NopCodeSymbol.xmeta、NopCodeFile.xmeta、NopCodeIndex.xmeta、NopCodeSemanticEdge.xmeta）与对应 BizModel 类的方法对齐。

### BizModel 与 xmeta 对应

全部 11 个 BizModel 的 @BizModel 值均与对应 xmeta/xbiz 文件名匹配。

### xmeta 字段覆盖

- NopCodeSymbolBizModel 的查询方法使用 query/kinds/packageName → NopCodeSymbol.xmeta 中有对应 prop 定义
- NopCodeFile.xmeta 中 sourceCode 设置 published="false" queryable="false" — 正确

### @BizLoader 检查

所有 @BizLoader 方法均为计算属性或关联实体返回，不需 xmeta prop — 正确。

### 字段权限

- 虚拟查询字段设置 insertable="false" updatable="false" — 正确
- 大字段 sourceCode 设置 published="false" — 正确
- 主键字段设置 updatable="false" internal="true" — 正确
