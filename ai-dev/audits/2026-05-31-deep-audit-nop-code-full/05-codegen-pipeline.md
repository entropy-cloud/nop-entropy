# 维度05：生成管线完整性

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度05-01] NopCodeSemanticEdge 的 sourceSymbol/targetSymbol 关系缺少 refPropName

- **文件**: `nop-code/model/nop-code.orm.xml:869-878`
- **证据片段**:
  ```xml
  <to-one name="sourceSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
      <join>
          <on leftProp="sourceSymbolId" rightProp="id"/>
      </join>
  </to-one>
  <to-one name="targetSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
      <join>
          <on leftProp="targetSymbolId" rightProp="id"/>
      </join>
  </to-one>
  ```
- **严重程度**: P3
- **现状**: 两个 to-one 关系没有指定 `refPropName`，意味着 NopCodeSymbol 端没有反向 to-many 关系。
- **风险**: 无法从 NopCodeSymbol 直接导航到语义边。如果业务需要查询"某符号关联的语义边"，需条件查询而非关系导航。
- **建议**: 如需反向导航，添加 refPropName。如不需要，当前设计合理。
- **信心水平**: 95%
- **误报排除**: 有意为之的非双向关系设计，但需业务确认。
- **复核状态**: 未复核

---

### [维度05-02] nop-code-api 模块为空壳（无 Java 源文件）

- **文件**: `nop-code/nop-code-api/pom.xml`
- **证据片段**: 无 src/main/java 目录，仅有 pom.xml
- **严重程度**: P2
- **现状**: 标准的 Nop 模块通常在 api 模块中定义公共契约。api 模块为空不影响运行时功能（BizModel + xbiz 自动映射）。
- **风险**: 其他模块若需公共 API 类型，只能依赖 service/core，导致传递依赖膨胀。
- **建议**: 将公共 DTO 迁移到 api 模块，或注明为占位。
- **信心水平**: 90%
- **误报排除**: BizModel + xbiz 自动映射使得手写 API 接口非必需。
- **复核状态**: 未复核

---

## 生成链路总结

| 步骤 | 状态 |
|------|------|
| model → dao/xbiz | ✅ 11 实体完整一致 |
| dao entity gen | ✅ 一致 |
| meta gen | ✅ 11 xmeta + 6 dict |
| i18n gen | ✅ 2 套（en 为 null 占位，正常） |
| page gen | ✅ 11 CRUD + 3 自定义 |
| service xbiz | ✅ 11 BizModel + IBiz |

**无 P0/P1 发现。** 生成管线正确闭合。
