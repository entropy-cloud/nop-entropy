# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

**检查范围**：11 个 BizModel（NopCodeIndexBizModel 21个@BizQuery + 6个@BizMutation, NopCodeSymbolBizModel 14个@BizQuery, NopCodeFileBizModel 3个@BizQuery + 4个@BizLoader, 8个纯 CrudBizModel）。

## 零发现

1. 8 个纯 CrudBizModel 仅提供标准 CRUD，契约一致。
2. 所有 @BizQuery 方法均为活跃 API，无死 API。
3. sourceCode 敏感字段在 xmeta 层和 BizLoader 层双重保护，契约收敛一致。

## 最终保留项

无。
