# 维度 05：生成管线完整性 — nop-code 模块

## 第 1 轮（初审）

### 结论：生成管线完整且正确闭合

审计范围: model → codegen → dao → meta → service → web → app → api 全链路。

11 个实体（NopCodeIndex, NopCodeFile, NopCodeSymbol, NopCodeUsage, NopCodeCall, NopCodeInheritance, NopCodeAnnotationUsage, NopCodeDependency, NopCodeFlow, NopCodeFlowMembership, NopCodeSemanticEdge）在每个环节均有对应产物。

检查项全部通过：
- 源模型 (model/*.orm.xml): 11 实体、12 domain、6 dict，格式正确
- codegen 脚本: 两阶段生成，路径正确
- dao 产物: _app.orm.xml, 11 Entity, 11 IBiz 接口，全覆盖
- meta 产物: 11 xmeta, i18n (zh-CN/en), 11 JSON 模板
- service 产物: 11 xbiz, 11 BizModel, _service.beans.xml (22 bean)
- web 产物: 11 view.xml × 5 件套 + 4 自定义页面 + auth
- Maven 插件配置: exec-maven-plugin 在 5 个子模块正确配置
- api 模块: 空壳，符合平台模式

**零发现。深挖结束。**
