# 维度 11：XMeta 与 BizModel 对齐

## 第 1 轮（初审）

### [维度11-01] NopCodeSymbol.xmeta 中 kinds prop 类型为 String，BizModel 方法签名为 List<String>

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeSymbol/NopCodeSymbol.xmeta:8-10`
- **证据片段**:
  ```xml
  <prop name="kinds" displayName="符号类型" queryable="true" insertable="false" updatable="false">
      <schema type="java.lang.String" precision="200"/>
  </prop>
  ```
  而 BizModel 方法：
  ```java
  public PageBean<SymbolDTO> findPage_symbols(
          @Name("kinds") @Optional List<String> kinds, ...) {
  ```
- **严重程度**: P3
- **现状**: xmeta 中 `kinds` 定义为 `type="java.lang.String"`，但 BizModel 方法签名声明 `List<String> kinds`。Nop 框架参数绑定可自动转换，功能不受影响，但类型语义不精确。
- **风险**: 类型声明不一致可能误导 API 消费者。
- **建议**: 将 xmeta 中的 types 改为 `type="java.util.List<java.lang.String>"` 或使用数组类型。
- **信心水平**: 确定
- **误报排除**: 功能可工作，但类型语义不精确。
- **复核状态**: 未复核

## 合规检查通过项

1. 8 个简单 BizModel（Call/Dependency/Inheritance/Usage/AnnotationUsage/SemanticEdge/Flow/FlowMembership）与 xmeta 完全对齐
2. NopCodeFile.xmeta 中 sourceCode 权限与 BizModel @Auth 一致
3. NopCodeSymbol @BizLoader 通过 forType 正确注册到 DTO 而非实体
4. dict 的 code 值与 Java 枚举名一致

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 11-01 | P3 | NopCodeSymbol.xmeta:8-10 | kinds prop 类型为 String vs BizModel 的 List<String> |
