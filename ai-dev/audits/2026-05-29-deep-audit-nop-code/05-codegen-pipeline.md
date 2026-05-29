# 维度 05：生成管线完整性

**审计日期**: 2026-05-29
**审计范围**: nop-code 全部生成链路 model→codegen→dao→meta→service→web

---

## 第 1 轮（初审）

### [维度05-01] nop-code-api 为空壳模块，无 parent 继承且无源码

- **文件**: `nop-code/nop-code-api/pom.xml`
- **行号**: 全文件
- **证据片段**:
  ```xml
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <java.version>11</java.version>
  </properties>
  ```
  目录结构仅有 pom.xml + target/，无 src/。父 pom.xml 在 modules 列表中包含 nop-code-api，但无下游消费者。
- **严重程度**: P2
- **现状**: nop-code-api 作为子模块被 Maven reactor 构建，但无 parent 继承、无 Java 源码、无资源文件。
- **风险**: 空壳模块增加构建时间且无产出；Java 版本声明与项目不一致（11 vs 21）。
- **建议**: 确认该模块是否为预留。若不需要，从父 pom modules 中移除；若需要，补充 parent 继承并统一 Java 版本。
- **信心水平**: 高
- **误报排除**: 属于 Maven 构建配置范畴的维护成本问题。与维度01-01/01-02为同一问题的不同方面。
- **复核状态**: 未复核

### [维度05-02] NopCodeSemanticEdge 的关系缺少 refPropName，无法从 Symbol 导航到语义边

- **文件**: `nop-code/model/nop-code.orm.xml:864-873`
- **证据片段**:
  ```xml
  <to-one name="sourceSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
      <join><on leftProp="sourceSymbolId" rightProp="id"/></join>
  </to-one>
  <to-one name="targetSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
      <join><on leftProp="targetSymbolId" rightProp="id"/></join>
  </to-one>
  ```
  对比 NopCodeInheritance（有 refPropName）：
  ```xml
  <to-one name="subType" refPropName="superTypes" .../>
  ```
- **严重程度**: P3
- **现状**: 生成管线按源模型正确处理了这两个关系，但缺少 refPropName 意味着 NopCodeSymbol 不会生成反向集合属性。
- **风险**: 无法通过 symbol.getSourceSemanticEdges() 进行 ORM 导航查询，必须通过显式查询获取。
- **建议**: 若需要高频导航，补充 refPropName 并重新 codegen。
- **信心水平**: 高
- **误报排除**: 不是管线缺陷（管线行为正确），而是源模型设计一致性观察。
- **复核状态**: 未复核

## 生成管线验证总结

| 检查项 | 状态 |
|--------|------|
| 源模型存在性与格式 | PASS |
| codegen 脚本引用正确性 | PASS |
| dao 生成产物完整性（11个实体） | PASS |
| meta xmeta/dict/i18n 完整性 | PASS |
| service xbiz/beans/BizModel 完整性 | PASS |
| web view.xml/page.yaml/action-auth 完整性 | PASS |
| Maven 插件配置 | PASS |
| 生成链路闭合 | PASS |
