# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] CepPatternBuilder 未校验 not pattern 不可加 optional 约束

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java` (addQualifier 方法)
- **严重程度**: P2
- **现状**: pattern.xdef 注释声明"'not' 类型的模式不能被 optional 所修饰"，但 CepPatternBuilder.addQualifier() 中处理 optional() 之前未检查当前 part 的 followKind 是否为 notNext 或 notFollowedBy。
- **风险**: xdef 的 optional 属性理论上可以被设置在 followKind 为 not 的 part 上，绕过语义约束。
- **建议**: 在 addQualifier() 中 optional() 处理前检查 followKind，若为 not 则抛出错误。
- **误报排除**: 无。
- **复核状态**: 未复核

## 已验证合规项

- pattern.xdef 定义正确，路径 /nop/schema/stream/pattern.xdef 存在
- 4 个 _gen 生成文件与手写扩展结构一致，继承链正确
- xdef:bean-sub-type-prop="type" 机制与子类 getType() 返回值匹配
- CepPatternBuilder 正确桥接声明式模型和程序化 Pattern API
- FollowKind/AfterMatchSkipStrategyKind 枚举映射正确
- 循环引用检测（previous Set）已实现
- 无其他遗漏的 XDSL 文件
