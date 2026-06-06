# 维度 05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] NopCodeSemanticEdge 源模型缺失 i18n-en:displayName 和 ext:icon

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 901-903
- **证据片段**:
  ```xml
  <!-- NopCodeSemanticEdge — 缺少 i18n-en:displayName 和 ext:icon -->
  <entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" displayName="语义边"
          name="io.nop.code.dao.entity.NopCodeSemanticEdge" registerShortName="true"
          tableName="nop_code_semantic_edge" useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
- **严重程度**: P2
- **现状**: 11个实体中唯一缺失这两个属性。生成产物传播：i18n yaml 英文标签为 null，web i18n 菜单为空字符串，action-auth 英文 displayName 为空。
- **建议**: 补充 i18n-en:displayName="Semantic Edge" 和 ext:icon 属性。
- **信心水平**: 确定（10/11 实体一致具有，仅此缺失）
- **误报排除**: 源模型缺陷，非生成产物问题。
- **复核状态**: 未复核

## 生成管线闭合性确认

从 model/nop-code.orm.xml 经 codegen→dao→meta→service→web 的完整链路闭合正确。11个实体在5个层级全部一致。Maven 构建顺序确保各阶段按正确时序执行。
