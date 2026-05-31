# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] _gen 多态 type 字段设计冗余

- **文件**: `nop-stream-cep/.../model/CepPatternSingleModel.java:17-23` + `CepPatternGroupModel.java`
- **证据片段**:
  ```java
  public String getType() {
      return "single";
  }
  @Override
  public void setType(String type) {
  }
  ```
- **严重程度**: P3
- **现状**: xdef 使用 xdef:bean-sub-type-prop="type" 声明多态。手写子类覆盖 getType() 返回常量，setType() 为空实现。_gen 基类的 _type 字段成为死代码。
- **风险**: _type 字段永远不会被读取，增加认知负担。
- **建议**: 当前行为正确，无功能性风险。不需立即修复。
- **信心水平**: 确定
- **误报排除**: 多态设计模式，非 bug。
- **复核状态**: 未复核

## 正面发现

- x:schema 引用正确（/nop/schema/xdef.xdef）
- _gen 继承链完整正确
- SPI 文件引用的类存在
- _gen 注释中的 schema 路径引用正确
- 无 x:extends/x:override 使用（合理）

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 10-01 | P3 | CepPatternSingleModel.java | 多态 type 字段 _type 成为死代码 |
