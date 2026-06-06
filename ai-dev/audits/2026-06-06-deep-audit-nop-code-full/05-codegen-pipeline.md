# 维度 05：生成管线完整性 — nop-code 模块审计报告

## 第 1 轮（初审）

### [维度05-01] NopCodeSemanticEdge 缺失 i18n-en:displayName 导致英文 i18n 生成 null

- **文件**: `nop-code/model/nop-code.orm.xml:898-900`
- **证据片段**:
  ```xml
  <entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" displayName="语义边"
          name="io.nop.code.dao.entity.NopCodeSemanticEdge" registerShortName="true"
          tableName="nop_code_semantic_edge" useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
- **严重程度**: P3
- **现状**: 源模型中 11 个实体，10 个有 `i18n-en:displayName` 属性，NopCodeSemanticEdge 是唯一缺失的。生成管线正确地将空值传播到了 i18n YAML（`null`），说明生成链路本身是闭合的，但上游数据不完整。
- **风险**: 英文 locale 下，NopCodeSemanticEdge 实体的 displayLabel 将为 null/空，影响前端英文界面的展示友好性。
- **建议**: 在源模型中添加 `i18n-en:displayName="Semantic Edge"`，然后重新运行 codegen。
- **信心水平**: 95%
- **误报排除**: 已排除"生成器跳过无属性实体"的假说——生成器确实输出了 null 而非省略条目，证明管线正确运行但源数据缺失。
- **复核状态**: 未复核

## 其余检查项无问题

1. **源模型文件** ✅: model/nop-code.orm.xml 存在，970行，格式正确
2. **codegen 生成脚本** ✅: 正确引用 model/nop-code.orm.xml
3. **dao 生成产物** ✅: 11个 _gen/*.java + 11个 retention entity + 11个 IBiz 接口完整
4. **meta 生成脚本** ✅: gen-meta.xgen 和 gen-i18n.xgen 正确执行
5. **web 生成脚本** ✅: gen-page.xgen 正确生成 11 个 CRUD 页面目录
6. **xbiz 与 BizModel 对应** ✅: 11对 xbiz 文件与 BizModel 类一一对应
7. **生成产物时间戳** ✅: _app.orm.xml 比源模型更新
8. **Maven 插件配置** ✅: 各步骤的 exec-maven-plugin 配置完备
