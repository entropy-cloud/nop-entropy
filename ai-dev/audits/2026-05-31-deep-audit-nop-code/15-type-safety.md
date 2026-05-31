# Audit Dimension 15: Type Safety — nop-code

## 无 P1+ 发现

检查内容：原始类型、不必要的类型转换、Object 类型参数、泛型精度。
- 所有集合实例化正确使用菱形操作符
- 方法参数始终使用适当的泛型类型
- _gen/ 中 Object 用法仅限框架所需的 orm_propValue/orm_internalSet
- JsonTool.parseNonStrict 是动态 JSON 解析边界的合理使用
- 无 @SuppressWarnings("unchecked")
