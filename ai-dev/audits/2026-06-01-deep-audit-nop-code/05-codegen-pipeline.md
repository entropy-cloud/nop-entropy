# 维度 05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] code/call_type 字典未在源 ORM 模型 <dicts> 节中定义

- **文件**: `nop-code/model/nop-code.orm.xml:543-544`（引用处），`22-85`（dicts 定义区域）
- **证据片段**:
  ```xml
  <!-- 第 544 行 -->
  <column code="CALL_TYPE" displayName="调用类型" name="callType"
          precision="20" propId="8" stdDataType="string" stdSqlType="VARCHAR" ext:dict="code/call_type"/>
  
  <!-- dicts 区域只有 6 个 dict，缺少 code/call_type -->
  <dicts>
      <dict name="code/symbol_kind" .../>
      <dict name="code/access_modifier" .../>
      <dict name="code/reference_kind" .../>
      <dict name="code/index_status" .../>
      <dict name="code/language" .../>
      <dict name="code/relation_type" .../>
      <!-- 缺少: code/call_type -->
  </dicts>
  ```
- **严重程度**: P2
- **现状**: `NopCodeCall.CALL_TYPE` 列引用 `code/call_type` 字典，但该字典仅在 `call_type.dict.yaml` 中定义，未在 ORM 源模型 `<dicts>` 中定义。`_NopCodeDaoConstants.java` 中缺少 CALL_TYPE_* 常量。
- **风险**: ORM 模型与 meta 字典间契约依赖外部文件一致性。与其他 6 个字典的内联定义模式不一致。
- **建议**: 在 ORM `<dicts>` 中添加 `code/call_type` 字典定义。
- **信心水平**: 85%
- **误报排除**: 其他 6 个字典都遵循 ORM 内联定义模式，call_type 是唯一例外。
- **复核状态**: 未复核

## 管线完整性验证结果

| 步骤 | 状态 |
|------|------|
| 源模型 (11实体) | 通过 |
| gen-orm.xgen 引用 | 通过 |
| dao 生成产物 (11 实体) | 通过 |
| IBiz 接口 (11个) | 通过 |
| meta 生成产物 (11 xmeta + 11 delta + 7 dict + i18n) | 通过 |
| service 生成产物 (11 xbiz + 11 delta + beans) | 通过 |
| web 生成产物 (11 view + 11 delta + action-auth) | 通过 |
| lang-* beans | 通过 |
| POM 配置 | 通过 |

**生成管线完整闭合，无 P0/P1 问题。**
