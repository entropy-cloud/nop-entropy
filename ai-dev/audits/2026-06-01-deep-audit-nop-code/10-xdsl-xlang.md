# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### [维度10-01] 5 个 dict.yaml 的 valueType 与数据库实际存储类型不一致

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/dict/code/symbol_kind.dict.yaml:4-5`（以及 access_modifier、reference_kind、relation_type、index_status 共 5 个文件）
- **证据片段**:
  ```yaml
  valueType: int
  options:
    - label: 类
      value: 10
  ```
  但实际存储使用枚举名称字符串：
  ```java
  symEntity.setKind(sym.getKind() != null ? sym.getKind().name() : null);  // "CLASS"
  ```
- **严重程度**: P3
- **现状**: 所有 dict.yaml 声明 `valueType: int` 并使用整数 value，但数据库实际存储枚举名称字符串（如 "CLASS"、"PUBLIC"）。
- **风险**: 当前功能不受影响（dict.yaml 的 value 字段不被使用），但语义不一致。若将来有代码依赖 dict.yaml 的 value 进行类型转换将出错。
- **建议**: 将 dict.yaml 的 valueType 改为 "string"，value 改为枚举名称。
- **信心水平**: 确定
- **误报排除**: 已验证存储代码使用 `.name()` 而非 `.ordinal()`。
- **复核状态**: 未复核

### [维度10-02] call-hierarchy/type-hierarchy view.xml 的 gql:selection 过长

- **文件**: `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/pages/call-hierarchy/call-hierarchy.view.xml:24`
- **证据片段**: 单行超过 600 字符的 gql:selection 属性。
- **严重程度**: P3
- **现状**: GraphQL selection 字符串硬编码在 XML 属性中，可读性和可维护性差。
- **建议**: 考虑将 selection 提取为可复用的命名片段。
- **信心水平**: 确定
- **误报排除**: 功能正常，但维护时难以定位和修改。
- **复核状态**: 未复核

## 合规检查通过项

1. x:schema 引用全部正确（xbiz/xmeta/view.xml/beans.xml/orm.xml）
2. x:extends 使用正确（手写文件扩展生成文件）
3. x:override 语义正确（replace/merge 使用合理）
4. beans.xml bean 类路径与实际 Java 类一致
5. page.yaml 格式正确
6. 所有 view.xml 中的 API URL 与 BizModel 方法匹配

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 10-01 | P3 | dict/code/*.dict.yaml (5个) | valueType: int 与实际存储的字符串枚举名不一致 |
| 10-02 | P3 | call-hierarchy/type-hierarchy.view.xml | gql:selection 过长（>600字符），可维护性差 |
