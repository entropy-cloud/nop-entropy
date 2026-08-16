# 2253 nop-record-mapping 缺陷修复（tests-first）

> Plan Status: active
> Last Reviewed: 2026-08-16
> Source: `ai-dev/analysis/2026-08/2026-08-16-nop-record-mapping-design-review.md`
> Related: `275-nop-ai-agent-record-mapping-scope-fix.md`（completed，scope 问题已收口，与本文不重叠）
> Review Consensus: 独立 fresh-session 对抗性审查（`ses_ff7471533ffemAX2QcmkXVJk9S`）结论 conditional-approve：Current Baseline 全部 17 项与 live repo 逐行核对无误；1 Blocker（B9 用 getFieldByFrom 与 D3 删除冲突 → D3 删除清单剔除 getFieldByFrom）+ 3 Major（D4(c) flatten 方向须互换复制、codegen 需先 rebuild nop-xdefs 并声明 diff 处置、B7 遗漏 titleField 同型缺陷 :83/:189）+ 7 Minor 全部修复后 promoted 为 active。修复落点见各 Phase 对应条目。

## Purpose

把 `nop-record-mapping` 设计评审中确认的 12 个 live defects（B1-B12）与 4 个设计层缺陷（D3/D4/D6/D7）全部修复。**方法论硬约束：每个缺陷先写回归测试并运行确认失败（复现问题），再实施修复并运行确认通过（红→绿），证据记录到 `ai-dev/logs/`。**

## Current Baseline

（以下行号均为 2026-08-16 在 live repo 核对的事实）

- 模块：`nop-kernel/nop-record-mapping`（model/impl/md/utils + resources）；模型来源 xdef：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/record/record-mapping.xdef`；被 `nop-gateway`（MappingProcessor bodyMapping）与 `nop-ai-agent`（markdown DSL loader）使用。
- 既有测试 19 个全绿（`TestRecordMappingManager` 9 + `TestFlattenListProcessor` 10）。
- 确认的 live defects：
  - **B1**（P1）：`RecordMappingTool.validateDictValue`（RecordMappingTool.java:431-450）无 null 守卫；`castType` 把空值归一为 null 后，`DictBean.getOptionByValue(null)` 返回 null → 可选 dict 字段空值必抛 `ERR_RECORD_FIELD_VALUE_NOT_IN_DICT`，`mandatory` 被架空。
  - **B2**（P1）：`RecordMappingTool.mapCollectionField`（:357-360）flattenTo 把展平结果写入 **source** 而非 target（`generateFlattenObj(source, ...)`），且前缀用 `field.getName()` 而 flattenFrom 用 `getFromOrName()`，round-trip 不对称；`makeTargetCollection` flattenTo 分支（:288-290）返回 collection 不写 target 属性（保留）。全仓库无 flattenTo 真实使用点。
  - **B3**（P2）：`RecordMappingManagerImpl.getMappingPath`（:46）`mappingName.substring(0, -1)`——无点号 mappingName（如 `getRecordMapping("Type1_to_Type2")`）抛原生 `StringIndexOutOfBoundsException`。
  - **B4**（P2）：`RecordMappingTool.makeCollectionItem`（:303）`getItemConstructor(itemValue, null, ctx)`——`newItemExpr`（xdef 签名 `(source,target,ctx)`）的 target 收到 null；与 `makeMapValue`（:272，传 toValue）不一致。
  - **B5**（P2）：`ModelBasedRecordMapping.mapObjectField`（ModelBasedRecordMapping.java:99-108）在 fromValue 为 null 时仍创建空对象写入 target；`ignoreWhenEmpty` 只在简单值分支检查（:72），对 `mapping` 字段完全无效。
  - **B6**（P2）：`record-mappings.imp.xml`：line 6 `templatePath="template.record-mapping.xlsx"` 与实际文件 `template.record-mappings.xlsx` 不符；line 47 嵌套字段列表 `keyProp="to"` 与 xdef `xdef:key-attr="name"` 不符（field 无 `to` 属性）。
  - **B7**（P2）：`MappingBasedMarkdownParser.mapListItems`（:112-115）/`mapSectionChildren`（:215-218）在 action 内才标记 `processedFields`，`when=false` → 未标记 → `checkComplete`（:153-161）对 mandatory 字段抛 `ERR_RECORD_MD_MISSING_FIELD`，与核心语义（docs：when=false 时 mandatory 不生效）冲突。
  - **B8**（P2）：`MappingBasedMarkdownGenerator.generateListItemFields`（:110-111）`value == null` 直接 return——null 简单字段从 md 中静默消失，mandatory 字段 round-trip 断链（parse 报 missing-field 而非指明是哪个字段空）。
  - **B9**（P3）：md parser 对未知列表项/子章节无容忍选项（`requireFieldByFrom` :111/:213 直接抛 `ERR_RECORD_UNKNOWN_FROM_FIELD`）。
  - **B10**（P3）：`RecordMappingTool.evaluateToExpression`（:557-577）把 `source`/`target`/`sourceFieldName`/`targetFieldName`/pattern 捕获变量写入共享 eval scope 且不清理；gateway 的 ctx 复用 `IGatewayContext.getEvalScope()`。
  - **B11**（P3）：`RecordPatternFieldConfig.getNormalizedDefaultValue()` 硬编码 null（RecordPatternFieldConfig.java:25-27），patternField 无 `defaultValue` 属性支持。
  - **B12**（P3）：`executeForEachField0` 用完整 from 路径标记 processedFields（:109-111），而 `getAllFieldNames`（:586-604）只返回顶层名——bean 源 + 复杂路径 from + patternField 时，pattern 会重复处理整个子对象。
  - **D3**：死代码 `RecordMappingTool.PATH_MATCHER`/`PATTERN_CACHE`（:48-49）、`RecordMappingConfig.getFieldFroms()`、`RecordFieldMappingConfig.objName`。（`getFieldByFrom()` **不删除**——B9 修复将使其成为新使用方。）
  - **D4**：`record-mapping-gen.xlib` GenReverseMappings：双向都显式定义时生成重复 mapping；`A_to_B_to_C` 型名字 `split('_to_')` 长度≠2 返回 null；反向字段丢失 defaultValue/varName/virtual/flatten*/keyProp/itemFilterExpr 等属性。
  - **D6**：`MappingBasedMarkdownGenerator.generateListAsSections`（:175）强转 `(List<Object>) value`，Set 等 Collection 抛 ClassCastException。
  - **D7**：`RecordMappingTool.executeForObject`（:84-87）sourceRoot/targetRoot 绑定在同一个 `== null` 判断内——gateway 预置 sourceRoot 后 targetRoot 永不被设置，`targetRoot`/`rootRecord` 变量为 null。

## Goals

- B1-B12 与 D3/D4/D6/D7 全部修复，每个 defect 有独立回归测试（先红后绿，红→绿证据入 log）。
- 修复不破坏既有 19 个测试与 gateway / ai-agent 集成行为。
- `docs-for-ai/02-core-guides/record-mapping.md` 与 live 行为同步（B2 语义裁定、B9 新属性、B11 新属性、B1 空值行为）。
- 不引入新依赖（B6 验证用资源结构断言，不做 xlsx round-trip 集成测试）。

## Non-Goals

- 不处理 D1（RecordMappingManager 双实例）、D2（baseMapping/fromClass 半成品）、D5（md generator 把 Writer 当 target）——见 Deferred But Adjudicated。
- 不做 xlsx 导入导出全链路集成测试。
- 不重构 md parser/generator 架构，不修改 markdown DSL 格式规范（B9 新增属性为可选开关，默认行为不变）。
- 不修改 `nop-gateway` / `nop-ai-agent` 模块代码（D7 修复点在 `RecordMappingTool` 内）。
- 不改动 `_gen/` 生成文件（B9/B11 通过改 xdef + 重跑 codegen 生成，不手改）。

## Scope

### In Scope

- Phase 1：P1 缺陷（B1、B2，含 B2 语义 Decision）
- Phase 2：P2 缺陷（B3-B8）
- Phase 3：P3 缺陷（B9-B12）+ D7
- Phase 4：设计层清理（D3、D4、D6）
- 文档同步：`docs-for-ai/02-core-guides/record-mapping.md`、`ai-dev/logs/`

### Out Of Scope

- D1/D2/D5（Deferred But Adjudicated 裁定后移出 scope）
- xlsx round-trip 集成测试、md 管线重构、其他模块代码

## Execution Plan

> 执行约定：每个 defect 按 **Proof（写回归测试 → 运行确认 FAIL 并记录失败断言）→ Fix（修复 → 运行确认 PASS）** 两步执行；Proof+Fix 同一 commit（仓库惯例：测试与实现在同一 commit），红→绿证据写入 `ai-dev/logs/` 当日条目。
> 验证命令基线：`./mvnw test -pl nop-kernel/nop-record-mapping -am`（每次变更后运行）；Phase 4 末尾加 `./mvnw clean install -pl nop-kernel/nop-record-mapping -am -T 1C`。

### Phase 1 - P1 缺陷修复（B1、B2）

Status: planned
Targets: `RecordMappingTool.java`, `FlattenListProcessor.java`, `TestRecordMappingManager.java`（或新增 `TestRecordMappingRegression.java`）, `demo.record-mappings.xml` + 新增 `demo.dict.xml`（测试资源，B1 需注册 dict）

- Item Types: `Fix | Proof | Decision`

- [ ] **Proof**（B1）：新增回归测试——新增 dict 测试资源（`demo.dict.xml` 或测试内注册 `DictBean`），dict 字段（非 mandatory）源值为 null 与空串时映射成功；非法非空值仍抛 `ERR_RECORD_FIELD_VALUE_NOT_IN_DICT`。先运行确认当前 FAIL（null/空串场景抛 dict 错误）。
- [ ] **Fix**（B1）：`validateDictValue` 开头增加 `if (StringHelper.isEmptyObject(value)) return;`（RecordMappingTool.java:431）。
- [ ] **Decision**（B2）：裁定 flattenTo 语义——按 xdef 文档字面契约"按照{from}-{index}-{fieldName}展平"：展平结果写入 **target**、前缀取 `getFromOrName()`（与 flattenFrom 对称，round-trip 成立）；否决"保留写入 source 的就地变换语义"（理由：与文档矛盾、round-trip 不对称、污染入参；全仓库无使用点故无兼容负担）。`makeTargetCollection` 的 flattenTo 分支（不写 target 属性）保留不变。
- [ ] **Proof**（B2）：新增回归测试——(a) flattenTo 字段映射后 target 含 `{from}-1-{sub}` 前缀键、source 不被修改；(b) flattenFrom → flattenTo 同字段名 round-trip 一致（含 from≠name 场景）；(c) from≠name 时前缀取 from。先运行确认当前 FAIL（键写在 source、前缀用 name）。
- [ ] **Fix**（B2）：`mapCollectionField` flattenTo 分支改为 `generateFlattenObj(target, toValue, field.getFromOrName(), ...)`（RecordMappingTool.java:357-360）。
- [ ] Phase 1 全量回归：既有 19 测试 + 新增测试全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] B1：null/空串不再抛 dict 错误；非空非法值仍抛；`mandatory` 语义不受影响（mandatory dict 字段空值仍由 `validateMandatoryField` 报错）
- [ ] B2：flattenTo 输出在 target 上、前缀为 from、source 不变；flattenFrom↔flattenTo round-trip 一致（测试名：`testFlattenToWritesToTarget*` 系列）
- [ ] `./mvnw test -pl nop-kernel/nop-record-mapping -am` 全绿（既有 19 + 新增）
- [ ] `docs-for-ai/02-core-guides/record-mapping.md` 更新（flattenTo 语义落文档；dict 空值行为说明）
- [ ] `ai-dev/logs/` 对应日期条目已更新（含红→绿证据）

### Phase 2 - P2 缺陷修复（B3-B8）

Status: planned
Targets: `RecordMappingManagerImpl.java`, `RecordMappingTool.java`, `ModelBasedRecordMapping.java`, `MappingBasedMarkdownParser.java`, `MappingBasedMarkdownGenerator.java`, `record-mappings.imp.xml`, 测试资源与测试类

- Item Types: `Fix | Proof`

- [ ] **Proof**（B3）：测试 `getRecordMapping("Type1_to_Type2")`（无包名前缀）抛 `NopException`（错误码 `ERR_RECORD_MAPPING_NOT_FOUND` 或参数类错误码）而非 `StringIndexOutOfBoundsException`。先运行确认当前 FAIL。
- [ ] **Fix**（B3）：`getMappingPath`（RecordMappingManagerImpl.java:42-48）在 `lastIndexOf('.') < 0` 时抛 `NopException`（带 `ARG_MAPPING_NAME`），不再裸 `substring(0, -1)`。
- [ ] **Proof**（B4）：测试 itemMapping 字段配 `<newItemExpr>target != null</newItemExpr>`，映射后断言每个 collection item 为 `true`（target 非 null）。先运行确认当前 FAIL（item 为 `false`）。
- [ ] **Fix**（B4）：`makeCollectionItem`（RecordMappingTool.java:303）改传 `toValue` 作为 target：`field.getItemConstructor(itemValue, toValue, ctx)`，与 `makeMapValue` 对齐。
- [ ] **Proof**（B5）：测试 `mapping` 嵌套字段 + `ignoreWhenEmpty="true"` + 源值 null → target 不含该字段（无空对象）；`mandatory="true"` + `ignoreWhenEmpty="true"` + null → 仍抛 `ERR_RECORD_FIELD_IS_MANDATORY`（文档语义：ignoreWhenEmpty 不能替代 mandatory）。先运行确认当前 FAIL（生成空对象）。
- [ ] **Fix**（B5）：`mapObjectField`（ModelBasedRecordMapping.java:99-108）在 `getProcessedFromValue` 后增加 `if (field.isIgnoreWhenEmpty() && StringHelper.isEmptyObject(fromValue)) return;`（在 mandatory 校验之后）。
- [ ] **Proof**（B6）：测试读取 `record-mappings.imp.xml` 资源文本，断言 (a) `templatePath` 指向存在的文件 `template.record-mappings.xlsx`；(b) fields 列表 `keyProp="name"`。先运行确认当前 FAIL。
- [ ] **Fix**（B6）：`record-mappings.imp.xml` line 6 改 `templatePath="template.record-mappings.xlsx"`；line 47 `keyProp="to"` 改 `keyProp="name"`。
- [ ] **Proof**（B7）：md parser 测试——mandatory 字段带 `when=false` 且 md 文档含该条目 → 解析成功；文档缺该条目 → 仍抛 `ERR_RECORD_MD_MISSING_FIELD`（行为不变）。先运行确认当前 FAIL（前者误抛 missing-field）。
- [ ] **Fix**（B7）：`processedFields.add(field.getName())` 移到 action 之前，覆盖全部 4 个 add 点（审查 Major M3 扩展）：`mapListItems`（MappingBasedMarkdownParser.java:113）、`mapSectionChildren`（:216）、`mapTitleField`（:83）、`mapListItemAsObject` 的 title 分支（:189）——后两处为 titleField 的 when=false 同型缺陷，一并修复，与核心语义对齐。
- [ ] **Proof**（B8）：md generator 测试——(a) optional 简单字段值为 null → md 输出包含 `- {key}: ` 空值行，parse 后**最终映射值**为 null（审查 Minor 2：`decodeValue` 返回空串、null 由 `castType` 的 isEmptyObject 分支归一，断言应落在映射结果上）；(b) mandatory 简单字段值为 null → md 仍包含该行，parse 报 `ERR_RECORD_FIELD_IS_MANDATORY`（指明字段）而非 missing-field。先运行确认当前 FAIL（行被丢弃）。
- [ ] **Fix**（B8）：`generateListItemFields`（MappingBasedMarkdownGenerator.java:110-111）去掉 `if (value == null) return;`，null 值写 `- {key}: `（`encodeValue(null)` 返回空串）。
- [ ] Phase 2 全量回归。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] B3：无点号 mappingName 得到 `NopException`（测试断言错误类型）
- [ ] B4：collection 条目 newItemExpr 收到非 null target（Map 分支回归测试保持通过）
- [ ] B5：ignoreWhenEmpty 对 mapping 字段生效；mandatory+ignoreWhenEmpty 仍报 mandatory
- [ ] B6：imp.xml 结构断言测试通过（templatePath 指向实际文件、keyProp="name"）
- [ ] B7：when=false + mandatory 不报错；缺字段仍报 missing-field
- [ ] B8：null 简单字段不再从 md 静默消失；mandatory-null round-trip 报明确字段错误
- [ ] `./mvnw test -pl nop-kernel/nop-record-mapping -am` 全绿
- [ ] `docs-for-ai/02-core-guides/record-mapping.md` 更新（如 B7 行为说明与文档一致则 `No owner-doc update required` 并写明）
- [ ] `ai-dev/logs/` 对应日期条目已更新（红→绿证据）

### Phase 3 - P3 缺陷修复（B9-B12）+ D7

Status: planned
Targets: `record-mapping.xdef`（nop-xdefs）, `_gen` 重生成（codegen）, `MappingBasedMarkdownParser.java`, `RecordMappingTool.java`, `RecordPatternFieldConfig.java`, 测试

- Item Types: `Fix | Proof | Decision`

- [ ] **Procedural**（codegen 前置，审查 Major M2）：xdef 属 `nop-xdefs` 模块，codegen 从 classpath 读 xdef —— 先 `./mvnw install -pl nop-kernel/nop-xdefs -DskipTests -T 1C` 使新属性可见；再运行 `nop-kernel/nop-codegen` 的 `CodeGen.main`（对 28 模块幂等重生成）；`git diff` 应仅出现 nop-record-mapping 的 `_gen` 变更，若其他模块出现生成物 diff（基线 xdef 漂移）则回滚该批产物并单独重跑，不得混入无关生成物。
- [ ] **Decision**（B9）：mapping 根新增可选属性 `ignoreUnknownFields="!boolean=false"`——默认 false 保持现有严格契约（未知条目报错），true 时 md parser 跳过未知列表项/子章节。
- [ ] **Fix**（B9）：xdef `<mapping>` 根加 `ignoreUnknownFields` 属性（经 Procedural 项重生成 `_RecordMappingConfig.java`）→ parser 的 `mapListItems`/`mapSectionChildren` 在 `requireFieldByFrom` 前检查 flag：flag=true 时改用非抛错查找 `RecordMappingConfig.getFieldByFrom()`（审查 Blocker 修复——`getFieldByFrom` 成为 B9 新使用方，D3 不再删除，见 Phase 4）跳过未知条目。
- [ ] **Proof**（B9）：测试——默认（flag=false）未知条目仍抛 `ERR_RECORD_UNKNOWN_FROM_FIELD`；flag=true 时未知列表项/子章节被跳过、其余字段正常映射。先运行确认当前 FAIL（flag 不生效）。
- [ ] **Proof**（B10）：测试——pattern 映射后，ctx eval scope 中 `sourceFieldName`/`targetFieldName`/pattern 捕获变量恢复原值（无泄漏）。先运行确认当前 FAIL（变量残留）。
- [ ] **Fix**（B10）：`processPatternFields`/`evaluateToExpression` 对写入的变量（`VAR_SOURCE`/`VAR_TARGET`/`VAR_SOURCE_FIELD_NAME`/`VAR_TARGET_FIELD_NAME`/pattern 捕获变量）做 save/restore（action 执行前保存旧值、执行后恢复）。
- [ ] **Proof**（B11）：测试——patternField 配 `defaultValue`，源字段值 null → 目标得到 defaultValue。先运行确认当前 FAIL（得到 null）。
- [ ] **Fix**（B11）：xdef patternField 增加 `defaultValue="string"`（经 Procedural 项重生成 `_RecordPatternFieldConfig`）→ `RecordPatternFieldConfig.init` 按 type 转换计算 `normalizedDefaultValue`（与 `RecordFieldMappingConfig.init` 同逻辑）→ `createFieldConfigFromPattern`（RecordMappingTool.java:606-639）复制 defaultValue/normalizedDefaultValue。
- [ ] **Proof**（B12）：测试——bean 源（含子对象 `sub`），显式字段 `from="sub.field2"` + patternField `fromPattern="sub*"` → pattern 不再处理 `sub`（不产生重复映射）。先运行确认当前 FAIL（sub 被 pattern 重复映射）。
- [ ] **Fix**（B12）：`executeForEachField0`（RecordMappingTool.java:109-111）标记 processedFields 时同时标记 from 的首段路径名（`.` 之前的部分）。
- [ ] **Proof**（D7）：测试——(a) 直接 `mapping.map(source, target, ctx)`（三参签名，审查 Minor 4）→ 映射后 `ctx.getSourceRoot()==source` 且 `ctx.getTargetRoot()!=null`；(b) 预置 `ctx.setSourceRoot(x)` 后 map → `targetRoot` 仍被设置。先运行确认当前 FAIL（(b) 中 targetRoot 为 null）。
- [ ] **Fix**（D7）：`executeForObject`（RecordMappingTool.java:84-87）改为 sourceRoot / targetRoot **分别**判空设置。
- [ ] Phase 3 全量回归（含 codegen 后模块构建）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] B9：新属性默认行为不变；flag=true 时未知条目跳过（测试双路径覆盖）
- [ ] B10：pattern 处理后无上下文变量残留（测试断言恢复原值）
- [ ] B11：patternField 的 defaultValue 生效（null → default）
- [ ] B12：复杂路径 from + pattern 不产生重复映射
- [ ] D7：直接 map 与 gateway 预置 sourceRoot 两种入口下 root 变量均正确
- [ ] `_gen` 文件由 codegen 生成（git diff 不出现手改痕迹）；`./mvnw test -pl nop-kernel/nop-record-mapping -am` 全绿
- [ ] `docs-for-ai/02-core-guides/record-mapping.md` 更新（B9/B11 新属性、B10/B12 行为说明）
- [ ] `ai-dev/logs/` 对应日期条目已更新（红→绿证据）

### Phase 4 - 设计层清理（D3、D4、D6）

Status: planned
Targets: `RecordMappingTool.java`, `RecordMappingConfig.java`, `RecordFieldMappingConfig.java`, `record-mapping-gen.xlib`, `MappingBasedMarkdownGenerator.java`, 测试资源与测试类

- Item Types: `Fix | Proof | Decision`

- [ ] **Decision**（D3）：删除 `RecordMappingTool.PATH_MATCHER`/`PATTERN_CACHE`（私有字段，无引用）、`RecordFieldMappingConfig.objName`（私有字段，无读写）、`RecordMappingConfig.getFieldFroms()`（public 方法，仓库内零调用，SNAPSHOT 版本，删除无兼容负担）。**`getFieldByFrom()` 不删除**（审查 Blocker 修复——B9 的 ignoreUnknownFields 跳过机制以它为非抛错查找，成为新使用方）。保留 `requireFieldByFrom`（md parser 使用）。
- [ ] **Proof**（D3）：grep 断言 4 个死代码符号（PATH_MATCHER/PATTERN_CACHE/objName/getFieldFroms）零引用 + 模块编译通过。行为级测试不适用死代码删除，按指南标注 `No new test required: dead code removal`（审查 Minor 1），以删除前 grep 命中清单 + 删除后编译/测试全绿作为验证。
- [ ] **Fix**（D3）：删除上述死代码。
- [ ] **Proof**（D4）：新增测试资源 `demo-both-directions.record-mappings.xml`（显式定义 `A_to_B` 与 `B_to_A`，后者带 marker 字段）——加载后 `B_to_A` 是显式版（无重复定义报错/无自动生成版覆盖）；扩展 demo 使反向映射字段携带 defaultValue/varName/keyProp，断言自动生成的反映射保留这些属性；**并增加 flatten 字段反向断言**（审查 Major M1）：原字段 flattenFrom=true 的反映射应为 flattenTo=true（反向互换），flattenTo=true 的反映射应为 flattenFrom=true。先运行确认当前 FAIL（重复定义或属性丢失或 flatten 方向未互换）。
- [ ] **Fix**（D4）：`record-mapping-gen.xlib`——(a) 反向名已显式定义时跳过自动生成；(b) `split('_to_')` 长度≠2 时跳过（不产生 `name="${null}"`）；(c) 反向字段复制属性时 **flattenFrom/flattenTo 反向互换**（原 flattenFrom→反向 flattenTo，原 flattenTo→反向 flattenFrom，审查 Major M1；原因：反向字段的 from/name 已互换，展平方向必须随之翻转），其余补复制 defaultValue/varName/virtual/keyProp/itemFilterExpr/newInstanceExpr/newItemExpr/ignoreWhenEmpty/disableFromPropPath/disableToPropPath（表达式类属性 when/computeExpr/valueExpr/valueMapper/before/after 不复制——反向语义不明，文档注明）。
- [ ] **Proof**（D6）：测试——md generator 对值为 Set 的 itemMapping 字段抛 `NopException`（明确错误）而非 `ClassCastException`。先运行确认当前 FAIL（ClassCastException）。
- [ ] **Fix**（D6）：`generateListAsSections`（MappingBasedMarkdownGenerator.java:175）先 `instanceof List` 检查，非 List 抛 `NopException`（带字段名）。
- [ ] Phase 4 全量验证（含 ai-agent 反向映射场景回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 中 `TestAgentPlanRecordMapping`/`TestAgentPlanMarkdownLoader` 不受影响）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] D3：死代码删除后模块编译通过、既有测试全绿
- [ ] D4：双向显式定义无重复/显式版生效；反向映射保留结构性属性（defaultValue/varName/keyProp 断言）；ai-agent 的 `TestAgentPlanRecordMapping` 通过
- [ ] D6：非 List Collection 值得到 `NopException` 而非 `ClassCastException`
- [ ] `./mvnw clean install -pl nop-kernel/nop-record-mapping -am -T 1C` 全绿；`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [ ] checkstyle / 代码规范检查通过
- [ ] `docs-for-ai/02-core-guides/record-mapping.md` 更新（D4 反向生成属性保真说明）或明确 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] B1-B12 与 D3/D4/D6/D7 全部修复，每个 defect 的回归测试先红后绿（红→绿证据在 `ai-dev/logs/`）
- [ ] 既有 19 个测试 + 全部新增回归测试全绿（`./mvnw test -pl nop-kernel/nop-record-mapping -am`）
- [ ] `nop-ai/nop-ai-agent`（md 接线）与 `nop-gateway`（bodyMapping 接线）相关测试不受影响
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] `docs-for-ai/02-core-guides/record-mapping.md` 已与 live 行为同步（或逐项写明 `No owner-doc update required`）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）修复后的调用链运行时连通（flattenTo 写 target、newItemExpr 传 target、pattern 变量隔离等均有行为级断言），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-kernel/nop-record-mapping -am`
- [ ] `./mvnw test -pl nop-kernel/nop-record-mapping -am`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2253-nop-record-mapping-defect-fix-tests-first.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-record-mapping --severity high` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### D1 RecordMappingManager 双实例

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 两个实例均为默认实现、行为等价（gateway 注入 bean 与 md loader 的静态实例都执行同一逻辑），当前无用户可见缺陷；统一方案涉及 IoC 启动接线决策，不属于本计划缺陷修复结果面。
- Successor Required: `yes`
- Successor Path: 后续架构 plan（nop-record-mapping 模块治理）

### D2 baseMapping / fromClass 半成品

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 功能从未参与执行、仓库内无配置使用，属"未完成特性"而非 live defect；实现继承语义或移除需要 owner 决策。
- Successor Required: `yes`
- Successor Path: 后续架构 plan（nop-record-mapping 模块治理）

### D5 md generator 以 Writer 作为 target 参数

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仓库内 md 生成场景（ai-agent reverse mapping）的 when/valueExpr/computeExpr 均未引用 `target` 变量，无实际触发路径；修正涉及表达式契约变更，单独裁决。
- Successor Required: `no`

### xlsx 导入导出全链路集成测试

- Classification: `optimization candidate`
- Why Not Blocking Closure: B6 已用资源结构断言锁定模板路径与 keyProp 两个缺陷；完整 xlsx round-trip 测试需引入 `nop-excel` test 依赖与测试模板构造，成本高于当前收益。
- Successor Required: `no`

### D8 gateway bodyMapping 端到端集成测试缺口

- Classification: `watch-only residual`
- Why Not Blocking Closure: 评审 D8 指出的 gateway bodyMapping 端到端测试缺口，其覆盖的 md round-trip 与 flatten 行为已分别由 B7/B8/B2 的单元级 Proof 测试接管；gateway 接线本身（MappingProcessor:50/:76/:119）不在本计划变更面内，不因本计划修复而改变。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 执行期间发现的任何新 defect 需回到本计划 scope 判定，不得直接写入 follow-up。

## Closure

Status Note: 已通过独立对抗性审查（`ses_ff7471533ffemAX2QcmkXVJk9S`，conditional-approve，1 Blocker + 3 Major + 7 Minor 已全部修复落盘），已 promoted 为 active，尚未开始执行
Completed: 未完成

Closure Audit Evidence:

- Reviewer / Agent: 待执行后由独立子 agent（fresh session）执行并记录

Follow-up:

- 待执行

## Optional Sections

### Risks And Rollback

- B2 语义裁定改变 flattenTo 行为：全仓库无使用点，风险低；若未来发现外部使用方依赖旧行为，以 xdef 文档为准回滚并记录。
- B9/B11 修改 `record-mapping.xdef`：影响面为模型生成；codegen 幂等，diff 可审查；`_gen` 文件不手改。
- D4 修改 xlib：影响 ai-agent 的反映射生成；Phase 4 有 `TestAgentPlanRecordMapping` 回归兜底。