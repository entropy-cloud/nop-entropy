> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata
> Dimension: 04 — ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-001] NopMetaSemanticType 上 IX_NOP_META_SEM_TYPE_NAME 索引与 UK 重复

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:546-553`
- **证据片段**:
  ```xml
  <unique-key name="UK_NOP_META_SEM_TYPE_NAME" columns="typeName"/>
  <index name="IX_NOP_META_SEM_TYPE_NAME" unique="false">
      <column name="typeName"/>
  </index>
  ```
- **严重程度**: P2
- **现状**: 唯一键 `UK_NOP_META_SEM_TYPE_NAME` 在 `typeName` 列上自动创建了一个唯一索引。额外声明的非唯一索引 `IX_NOP_META_SEM_TYPE_NAME` 在同一列上是完全冗余的。
- **风险**: 徒增不必要的写入性能开销。
- **建议**: 删除冗余的 `IX_NOP_META_SEM_TYPE_NAME`。
- **信心水平**: 确定
- **误报排除**: 这不是"代码风格问题"。冗余索引有明确的性能开销，属于可量化的工程问题。
- **复核状态**: 未复核

### [维度04-002] NopMetaDataSource 缺少 status 列的非唯一索引

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:370-418`
- **证据片段**: NopMetaDataSource 的 `status` 列是常用过滤条件（查询启用的数据源），但实体上只有两个唯一键（`querySpace`, `name`），没有为 `status` 建立索引。
- **严重程度**: P2
- **现状**: 高频过滤条件列 `status` 缺少索引。
- **风险**: 可能影响按 status 查询数据源的性能。
- **建议**: 添加 `IX_NOP_META_DATA_SOURCE_STATUS` 索引覆盖 `status` 列。
- **信心水平**: 很可能
- **误报排除**: 数据库设计规范要求高频查询列建索引。这不是过度索引，是必要的性能优化。
- **复核状态**: 未复核

### [维度04-003] NopMetaDataProduct 的注释与实体名不匹配

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:3415-3419`
- **证据片段**: 注释写的是 `报表定义（预留）`，但实体名为 `NopMetaDataProduct`（数据产品）。注释明显是从其他实体复制过来的占位符文本。
- **严重程度**: P2
- **现状**: 误导性注释可能导致误读模型意图。
- **建议**: 将注释更新为 `数据产品（Data Product）定义`。
- **信心水平**: 确定
- **误报排除**: 这是一个真实的可追踪文本错误，不是纯粹的格式偏好。
- **复核状态**: 未复核

### [维度04-004] NopMetaQualityCheckpoint.extConfig 缺少 stdDomain="json" 显式声明

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2126-2127`
- **证据片段**:
  ```xml
  <column code="EXT_CONFIG" displayName="扩展配置" domain="json-4000" name="extConfig"
          propId="9" stdDataType="string" stdSqlType="VARCHAR"/>
  ```
  而其他同类字段（如 `NopMetaModule.extConfig`）同时声明了 `domain="json-4000"` 和 `stdDomain="json"`。
- **严重程度**: P3
- **现状**: `json-4000` 域定义已包含 `stdDomain="json"`（行 214），应能正确继承，但列级显式声明不一致。
- **风险**: 造成维护困惑。
- **建议**: 保持代码一致性，补上 `stdDomain="json"` 或统一移除所有列级冗余 `stdDomain` 声明。
- **信心水平**: 很可能
- **误报排除**: 一致性问题，非功能缺陷，但对维护有真实影响。
- **复核状态**: 未复核

### [维度04-005] 三个字典在 ORM 模型中未被 ext:dict 引用

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:104-113,120-125,109-113`
- **证据片段**: `meta/checkpoint-action-type`、`meta/reconciliation-status`、`meta/quality-trend-direction` 三个字典在 `<dicts>` 中已定义，但没有任何 `<column>` 的 `ext:dict` 属性引用它们。
- **严重程度**: P3
- **现状**: 模型层未使用的字典定义，增加维护负担。可能在 Java 代码中通过 DictProvider 引用。
- **建议**: 如果仅在代码中使用可保留；如果不再使用考虑移除。
- **信心水平**: 很可能
- **误报排除**: 非功能缺陷，但来自 Nop 标准模式期望 each dict should be referenced。
- **复核状态**: 未复核

### [维度04-006] 多实体间 dict 值大小写风格不一致

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:<dicts>` 节（行 9-207）
- **证据片段**:
  | 字典 | value 风格 | 示例 |
  |------|-----------|------|
  | `meta/module-status` | UPPERCASE | `DRAFTING`, `RELEASED` |
  | `meta/table-type` | lowercase | `entity`, `sql` |
  | `meta/agg-func` | lowercase/camelCase | `sum`, `countDistinct` |
  | `meta/tag-label-source` | PascalCase + kebab-case | `Classification`, `lineage-propagation` |
- **严重程度**: P3
- **现状**: 字典 value 使用了不一致的大小写风格。在代码比较（`equals("active")` vs `equals("ACTIVE")`）时易引发 bug。
- **风险**: 运行时可能因大小写不匹配引发 bug。
- **建议**: 统一字典 value 的大小写风格，优先使用 `UPPER_SNAKE_CASE` 或 `lower_case`。
- **信心水平**: 确定
- **误报排除**: 大小写不一致在 equals 比较中有真实运行时风险，不是纯粹的风格偏好。
- **复核状态**: 未复核

### [维度04-007] NopMetaModule 自引用级联行为缺少文档注释

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:250-253,308-313,357-363`
- **严重程度**: P3
- **现状**: `NopMetaModule.baseModuleId` 自引用关系的级联行为是经过"排除性"考量的（未设置 cascadeDelete），但不明显。
- **建议**: 在模型注释中记录该自引用级联行为的设计意图。
- **信心水平**: 很可能
- **误报排除**: 这是文档完整性改进，非功能缺陷。
- **复核状态**: 未复核
