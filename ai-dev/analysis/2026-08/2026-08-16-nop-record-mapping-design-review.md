# nop-record-mapping 设计与实现评审

> Status: open
> Date: 2026-08-16
> Scope: `nop-kernel/nop-record-mapping`（含 `nop-kernel/nop-xdefs` 的 record xdef、`nop-service-framework/nop-gateway` 的 MappingProcessor 接线、`nop-ai/nop-ai-agent` 的 markdown 接线）
> Conclusion: 模块整体设计清晰、与 gateway / markdown DSL 集成自然，但存在 2 个 P1 级行为缺陷（dict 校验误伤空值、flattenTo 写入对象错误）、若干 P2 级边界 bug 与半成品功能（baseMapping 未生效、manager 双实例、死代码）。

## Context

- 需求：对 `nop-record-mapping`（用户口中的 "nop-record"）做设计与实现评审，列出潜在改进点和可能的 bug。
- 该模块是 Nop 平台声明式字段映射引擎，被 `nop-gateway`（requestMapping/responseMapping bodyMapping）和 `nop-ai-agent`（`agent-plan.md` → 模型 的 markdown DSL loader）两个上层使用。
- 本文为纯代码评审，不修改任何代码；结论可被后续 plan / design 接手。

## Analysis

### 1. 模块概览与架构

```
nop-record-mapping
├── model/     RecordMappingDefinitions / RecordMappingConfig / RecordFieldMappingConfig / RecordPatternFieldConfig
├── impl/      ModelBasedRecordMapping（执行器）、RecordMappingTool（核心工具）、FlattenListProcessor、RecordMappingManagerImpl
├── md/        MappingBasedMarkdownParser / Generator（双向 markdown DSL）、MarkdownDslResourceLoader(Factory)
├── utils/     OrmMdHelper
├── RecordMappingContext / RecordMappingManager / IRecordMapping(Manager)
└── resources/ record-mapping-defaults.beans.xml、record-mapping-gen.xlib、imp/record-mappings.imp.xml
```

分层：xdef（`nop-kernel/nop-xdefs/.../record-mapping.xdef`）定义模型 → 模型类 + `init()` 解析引用 → `ModelBasedRecordMapping` 按 `RecordMappingTool` 的执行顺序驱动 → md parser/generator 复用同一工具链。

执行顺序（核心语义，与 `docs-for-ai/02-core-guides/record-mapping.md` 一致）：
`when → beforeFieldMapping → 取值(computeExpr/from/alias/flattenFrom) → valueExpr → valueMapper/defaultValue → castType(stdDomain/type) → schema 校验 → dict 校验 → mandatory → ignoreWhenEmpty → 写入(属性/varName) → afterFieldMapping`。

亮点：

- 声明式模型 + xdef 强校验 + `x:post-extends` 元编程自动生成反向映射（`GenReverseMappings`），符合 Nop 可逆计算哲学。
- md parser/generator 复用同一套 field 配置，`md:format` / `md:titleField` 通过 ext prop 挂在 field 上，双向兼容思路好。
- patternField（`fromPattern` + 命名捕获 + `to` 模板表达式）覆盖动态字段名场景，测试较全。

### 2. 问题分析

#### P1 级缺陷

##### B1. dict 校验对 null/空值生效，可选 dict 字段空值直接报错

`RecordMappingTool.validateDictValue`（RecordMappingTool.java:431-450）没有 null 守卫：

```
castType(): StringHelper.isEmptyObject(value) → return null   // 空串/空值先被归一化为 null
validateDictValue(): dictBean.getOptionByValue(null)
                     → DictBean.getOptionByValue 内部 valueMap.get("null") → null
                     → 抛 ERR_RECORD_FIELD_VALUE_NOT_IN_DICT
```

即：**只要字段配了 `<schema dict="..."/>`，源值为 null/空串就必报错**，与 `mandatory` 是否设置无关。实测路径可达：`nop-ai-agent` 的 `agentPlan.record-mappings.xml` 中 `status`（dict=`AgentExecStatus`，非 mandatory）在 md 里写 `- 任务状态: `（空值）就会失败。`mandatory` 属性因此被架空——"可选的字典字段"无法表达。修复：`validateDictValue` 开头 `if (value == null) return;`（或 isEmptyObject）。

##### B2. flattenTo 把展平结果写回 source 而不是 target，且 from/name 不对称

`RecordMappingTool.mapCollectionField`（RecordMappingTool.java:357-360）：

```java
if (field.isFlattenTo()) {
    FlattenListProcessor.instance().generateFlattenObj(source, toValue, field.getName(), ...);
}
```

- xdef 注释说"映射得到的列表数据，不直接设置到target对象上，而是按照{from}-{index}-{fieldName}展平"，但实现把展平结果写到 **source** 对象上（`generateFlattenObj(obj=source)`），`map(source, target)` 因此会**修改入参 source**（非幂等、污染输入）。
- 对称性破坏：flattenFrom 解析用 `field.getFromOrName()`（RecordMappingTool.java:157），flattenTo 生成用 `field.getName()`（RecordMappingTool.java:358）。当 `from != name`（很常见，如 `from="items" name="itemList"`）时，round-trip 的键前缀不一致。
- `makeTargetCollection` 的 flattenTo 分支（RecordMappingTool.java:288-290）直接返回 collection，不 set 到 target 属性——若意图是"写入 target 的展平键"，则整个 flattenTo 路径行为与注释矛盾；若意图是"就地变换 source"，需要文档明确并修正参数命名。

#### P2 级缺陷

##### B3. 无包名前缀的 mappingName 抛 StringIndexOutOfBoundsException

`RecordMappingManagerImpl.getMappingPath`（RecordMappingManagerImpl.java:46）：

```java
int pos = mappingName.lastIndexOf('.');
String prefix = mappingName.substring(0, pos).replace('.', '/');
```

`getRecordMapping("Type1_to_Type2")`（无点号）→ `substring(0, -1)` → 原生 `StringIndexOutOfBoundsException`。`Guard.checkArgument(isValidClassName)` 挡不住（单标识符是合法类名）。应改为显式校验并抛 `NopException(ERR_RECORD_MAPPING_NOT_FOUND/参数错误)`。

##### B4. newItemExpr 的 target 参数传入 null

`RecordMappingTool.makeCollectionItem`（RecordMappingTool.java:303）：

```java
Object toItemValue = field.getItemConstructor(itemValue, null, ctx).get();
```

xdef 声明 `newItemExpr` 签名是 `(source,target,ctx)=>any`，但集合条目场景 target 传 `null`；而 `makeMapValue`（RecordMappingTool.java:272）传的是 `toValue`。同一表达式在两处收到不同 target，行为不一致且无法在 newItemExpr 中引用目标容器。

##### B5. `mapping` 分支忽略 ignoreWhenEmpty

`ModelBasedRecordMapping.mapField0`（ModelBasedRecordMapping.java:46-47）：`field.getMapping() != null` 时直接 `mapObjectField`，其中 `getProcessedFromValue` 返回 null 后仍 `makeTargetObject` + `mapObject`，**源为 null 时目标对象字段被写成空对象 `{}`**，`ignoreWhenEmpty` 只在简单值分支检查（ModelBasedRecordMapping.java:72）。`ignoreWhenEmpty="true"` 对嵌套对象字段完全不生效，与 xdef 文档（"当数据为空的时候自动忽略该字段"）矛盾。

##### B6. imp.xml 模板路径与 keyProp 错误（xlsx 导入导出缺陷）

`record-mappings.imp.xml:6`：`templatePath="template.record-mapping.xlsx"`，但实际文件是 `template.record-mappings.xlsx`（`ls` 确认）——xlsx 导出会找不到模板。
`record-mappings.imp.xml:47`：嵌套字段列表 `keyProp="to"`，但 xdef 中 field 的 key-attr 是 `name`，field 上根本没有 `to` 属性——xlsx 导入时字段列表 key 全部为 null，存在丢行/重复键风险。

##### B7. md parser 中 `when=false` 仍触发 mandatory（与核心语义冲突）

`MappingBasedMarkdownParser.mapListItems`（:112-115）/`mapSectionChildren`（:215-218）：`processedFields.add(field.getName())` 只在 action 内执行，`when` 不通过则不标记 → `checkComplete`（:153-161）对未标记的 mandatory 字段抛 `ERR_RECORD_MD_MISSING_FIELD`。而核心 mapping 语义是"when=false 时 mandatory 不生效"（docs 明确记载）。同一条规则在 xml 映射和 md 解析下行为相反。

##### B8. md round-trip：mandatory 简单字段值为 null 时生成后无法解析回来

`MappingBasedMarkdownGenerator.generateListItemFields`（:109-111）：`value == null` 直接 return，不写任何行 → 生成的 markdown 缺少该字段 → `MappingBasedMarkdownParser.checkComplete` 对 mandatory 字段抛 missing-field。对象树中存在 mandatory 字段且值为 null（例如经 `dslNodeToJson` 构造的 bean）时，md 保存-加载闭环断裂。

#### P3 级缺陷

##### B9. md parser 对未知列表项/子章节无容忍选项

`requireFieldByFrom`（MappingBasedMarkdownParser.java:111、:213）对文档中任何未配置的条目直接抛 `ERR_RECORD_UNKNOWN_FROM_FIELD`。markdown 是用户可编辑文本，含注释性条目时整个解析失败，无 `ignoreUnknown` 类开关。

##### B10. pattern 求值把上下文变量残留到共享 scope

`evaluateToExpression`（RecordMappingTool.java:557-577）将 `source`/`target`/`sourceFieldName`/`targetFieldName`/pattern 捕获变量写入 `ctx`（即 eval scope），字段处理结束后不清理。gateway 的 `RecordMappingContext` 复用 `IGatewayContext.getEvalScope()`（MappingProcessor.java:172），同一请求后续处理可读到残留的 `sourceFieldName` 等变量；多个 patternField 之间也会互相污染（后面 pattern 的 when/表达式能看到前面 pattern 留下的变量）。

##### B11. patternField 不支持 defaultValue

`RecordPatternFieldConfig.getNormalizedDefaultValue()` 硬编码返回 null（RecordPatternFieldConfig.java:25-27），xdef 的 patternField 也没有 `defaultValue` 属性（record-mapping.xdef:69-76）。`applyValueMapper` 对 null 值返回 `getNormalizedDefaultValue()`，pattern 字段的 null 值永远无法落到缺省值。

##### B12. 复杂路径 from 与 pattern 的 processedFields 匹配错位

`executeForEachField0` 用 `field.getFrom()` 标记 processedFields（RecordMappingTool.java:109-111），而 `getAllFieldNames` 只返回顶层字段名（Map keySet 或 bean 顶层 prop）。`from="sub.field2"` 的显式字段不会把顶层名 `sub` 标记为已处理 → 后续 `patternField fromPattern="sub*"` 会重复处理整个 `sub` 对象（二次映射/覆盖）。

#### 设计层面问题

##### D1. RecordMappingManager 双实例，静态注册机制无人使用

`RecordMappingManager.instance()`（静态默认实现）与 IoC bean `nopRecordMappingManager`（record-mapping-defaults.beans.xml）并存；`registerInstance()` 全仓库无调用。gateway 通过 bean setter 注入，`MarkdownDslResourceLoader` 却硬编码走 `RecordMappingManager.instance()`（MarkdownDslResourceLoader.java:32）——一旦有人覆盖 bean 实现，md loader 与 gateway 行为分裂。

##### D2. baseMapping / fromClass 是半成品功能

`RecordMappingDefinitions.init()` 解析并设置 `resolvedBaseMapping`，但执行路径（ModelBasedRecordMapping / RecordMappingTool）从不使用它——"继承映射"没有任何效果（字段不合并）。`fromClass` 属性同样从未被读取。要么实现继承合并语义，要么从 xdef 移除以免误导。

##### D3. 死代码

- `RecordMappingTool.PATH_MATCHER` / `PATTERN_CACHE`（:48-49）声明后未使用（pattern 编译在 `RecordPatternFieldConfig.init` 内完成）。
- `RecordMappingConfig.getFieldFroms()` / `getFieldByFrom()` 无调用方。
- `RecordFieldMappingConfig.objName` 字段无读写。
- `MarkdownDslResourceLoader.serializeDslNodeToText` 对不含 `_to_` 的 mappingName：`StringHelper.reverseMappingName` 返回 null → `getRecordMappingConfig(null)` → Guard 抛原始 IllegalArgumentException，错误信息不可读（同 B3 一类问题）。

##### D4. GenReverseMappings xlib 元编程缺陷

- 当 `A_to_B` 与 `B_to_A` 都显式定义时：`mappingMap[reverseName]` 命中 → 显式节点进 `reverseMappingMap` → 第二段 `<definitions>` 原样 `<c:out>` 输出 → 同一 name 的 mapping 被生成两份（一份自动反转、一份原样拷贝），可能触发重复定义。
- `A_to_B_to_C` 这种含两个 `_to_` 的名字：`split('_to_')` 长度 3 → `reverseMappingName` 返回 null → 生成 `<mapping name="${null}">`。
- 反向生成仅复制 name/from/type/mandatory/optional/displayName/md:format/mapping/itemMapping/schema，**丢失 defaultValue、varName、virtual、flattenFrom/To、keyProp、itemFilterExpr、newInstanceExpr 等属性**——复杂双向映射无法靠自动反转保真。

##### D5. md generator 把 Writer 当 target 传入表达式

`getObjProp(mapping, field, obj, out, ctx)`（MappingBasedMarkdownGenerator.java:306-309）→ `getProcessedFromValue` 的 target 参数是 **Writer**；`when`/`computeExpr`/`valueExpr`/before/afterFieldMapping 中引用 `target` 拿到的不是数据对象。与 parser（target=数据对象）语义不对称，且无文档说明。

##### D6. `generateListAsSections` 强转 List

`(List<Object>) value`（MappingBasedMarkdownGenerator.java:175）：源值为 Set/其他 Collection 时直接 ClassCastException，无类型守卫。

##### D7. 根变量设置不一致

`executeForObject` 仅当 `ctx.getSourceRoot()==null` 时设置 root（RecordMappingTool.java:84-87），而 gateway 只 `setSourceRoot` 不 `setTargetRoot`（MappingProcessor.java:76）；直接调用 `IRecordMapping.map(source, ctx)`（如 README 示例）时 `sourceRoot`/`targetRoot`/`rootRecord` 变量为 null，表达式里 `sourceRoot` 不可用。行为随调用入口漂移。

##### D8. 测试覆盖缺口

- 无 md parser↔generator 双向 round-trip 测试（含 table/code 格式、titleField）。
- 无 flattenFrom/flattenTo 端到端 mapping 测试（仅 FlattenListProcessor 单元测试，且未覆盖 B2 的 source/target 问题）。
- 无 gateway bodyMapping 集成测试。
- 无错误路径测试（dict 校验、mandatory、未知字段、B3 等）。
- `TestRecordMappingManager` 主要覆盖 pattern 字段，核心 mapField0 分支（mapping 嵌套/ignoreWhenEmpty/alias/varName/flatten）覆盖有限。

### 3. 改进建议

按优先级排序：

1. **P1 修复**：`validateDictValue` 跳过 null；flattenTo 明确语义——若应写入 target，则 `generateFlattenObj(target, ...)` 并统一 from/name 前缀；若设计为就地变换，改参数名并写文档。
2. **参数与错误路径规范化**：`getMappingPath` 无点号/非法名 → `NopException`；`makeCollectionItem` 传真实 target；`serializeDslNodeToText` 对无 `_to_` 名提前报错。
3. **语义对齐**：`mapping` 分支支持 `ignoreWhenEmpty`（源 null 时跳过创建空对象）；md parser 的 `when`+mandatory 与核心一致；generator 对 null mandatory 字段写出空值行（或 parse 侧容忍）。
4. **md 可编辑性**：为 parser 增加 `ignoreUnknownFields` 开关（xdef 属性），匹配生成器 `encodeKey` 的转义规则。
5. **上下文隔离**：pattern 求值后清理注入变量（save/restore scope），或使用独立 child scope。
6. **patternField 增强**：xdef 增加 `defaultValue`；`getNormalizedDefaultValue` 实现与 field 一致。
7. **模型修正**：xdef 增加 `fromClass`/`baseMapping` 语义实现或移除；删除全部死代码（PATH_MATCHER/PATTERN_CACHE/getFieldFroms/getFieldByFrom/objName）。
8. **xlib 修正**：GenReverseMappings 处理显式反向定义时的去重、`_to_` 嵌套名、补复制属性（defaultValue/varName/virtual/flatten/keyProp/itemFilterExpr）。
9. **单例统一**：启动时将 IoC bean 注册进 `RecordMappingManager` 静态实例，或 md loader 改为注入式。
10. **补测试**：上述 6 个 P1/P2 修复各配回归测试 + md round-trip 测试。

## Conclusion

- `nop-record-mapping` 的声明式建模与执行器分层是成功的：单一执行顺序贯穿 xml 映射、md 解析/生成、gateway bodyMapping，pattern/flatten/varName 等特性设计意图清晰。
- 主要风险集中在**校验语义的边界**（B1/B7/B8：dict 空值、when+mandatory、round-trip）与**两处行为与注释/直觉不符**（B2 flattenTo 写 source、B5 mapping 分支空对象写入）。
- 后续工作建议：P1/P2 修复拆为 `ai-dev/plans/` 计划（逐个配回归测试），设计层面 D1/D2/D4 需先确认意图（行为契约）再动手。

## Open Questions

- [ ] flattenTo 的预期语义到底是"写入 target 的展平键"还是"就地变换 source"？现无任何真实使用点（全仓库 grep 无 flattenTo 配置），需与上游（原 nop-entropy 仓库）确认。
- [ ] `baseMapping` 是历史遗留未完成，还是后续有 Delta 方案承接？决定实现 vs 移除。
- [ ] md parser 的严格模式（未知条目报错）是产品需求还是实现简化？决定加开关还是保持。

## References

- `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/impl/RecordMappingTool.java`
- `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/impl/ModelBasedRecordMapping.java`
- `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/impl/RecordMappingManagerImpl.java`
- `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/impl/FlattenListProcessor.java`
- `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MappingBasedMarkdownParser.java` / `MappingBasedMarkdownGenerator.java` / `MarkdownDslResourceLoader.java`
- `nop-kernel/nop-record-mapping/src/main/resources/_vfs/nop/record/imp/record-mappings.imp.xml`
- `nop-kernel/nop-record-mapping/src/main/resources/_vfs/nop/record/xlib/record-mapping-gen.xlib`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/record/record-mapping.xdef`
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/MappingProcessor.java`
- `nop-ai/nop-ai-agent/src/test/resources/_vfs/nop/record/mapping/agentPlan.record-mappings.xml`
- `docs-for-ai/02-core-guides/record-mapping.md`、`docs-for-ai/04-reference/source-anchors.md`（MAP-002/003/004）