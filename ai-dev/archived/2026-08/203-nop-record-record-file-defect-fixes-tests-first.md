# 203 nop-record record file 解析/生成缺陷修复（tests-first）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Source: `ai-dev/analysis/2026-08/2026-08-16-record-file-parsing-generation-design-analysis.md`
> Draft Review: R1 独立对抗性审查（2026-08-16，记录见 `ai-dev/audits/2026-08-16-1447-adversarial-review-nop-record-plan-203.md`）——verdict FAIL：2 Blocker（P0-1/P0-2 Phase 5 区域对齐公式对急切 subInput reader 双重跳过 + 红测试需惰性 reader）+ 6 Major（P1-1 流式路径 readWhen 未接线 / P1-2 集合内 item readWhen 未裁定 / P1-3 header-trailer 级 readWhen 未裁定 / P1-4 DynLV 静默截断跨 Phase 缺口 / P1-5 EOF 红测试依赖 reader 类型 / P1-6 回归测试点名错误）+ 8 Minor，全部落盘修复。R2 fresh-session 复审（同文件附录）：R1 修复全部落实无回退；新发现 1 Major（R2-1 流式路径 STAGE_INIT 区域残留缺口，被 P1-1 修复放大）+ 6 Minor（R2-2~R2-7），按审查指定方向全部落盘修复。0 Blocker / 0 Major，可转 active 执行。

## Purpose

把 analysis 中确认的 nop-record 缺陷全部修复，采用**每个问题先写测试确认（红）→ 再修正（绿）**的 tests-first 方式，收口到所有 in-scope 缺陷都有回归测试覆盖且模块测试全绿。

## Current Baseline

已核实的缺陷清单（file:line 均为本次核实结果）：

| # | 级别 | 缺陷 | 证据位置 |
|---|------|------|---------|
| 1 | P0 | `checkMinLen` 用 `safeGetMaxLen()`+`len > min`，min-length 校验永不触发；`checkMaxLen`/`checkMinLen` 错误码参数名与 `RecordErrors` 声明不一致（声明 `ARG_MAX_VALUE`/`ARG_MIN_VALUE`，调用点传 `ARG_MAX_LENGTH`/`ARG_MIN_LENGTH`） | `util/RecordMetaHelper.java:41-49,31-39`；`RecordErrors.java:63-69` |
| 2 | P0 | `DynCountArrayBinaryCodec` stub：decode 读 count 后填 `count` 个 null（itemCodec 调用被注释），encode 只写 count | `codec/impl/DynCountArrayBinaryCodec.java:35,50` |
| 3 | P0 | `DynLVFieldBinaryCodec.encode` 在 `valueCodec==null` 时只写长度不写值字节；decode 在 `valueCodec==null` 时返回 `len` 而非原始字节 | `codec/impl/DynLVFieldBinaryCodec.java:45-46,61` |
| 4 | P1 | `readWhen` 未接线：xdef 与生成模型均有 `getReadWhen()`，但 deserializer 全程不调用；写路径已调用 `getWriteWhen()`（读写在 when 语义上不对称） | `record-simple-field.xdef:50`、`record-object.xdef:34`；`nop-format/nop-record/src/main/java/io/nop/record/serialization/AbstractModelBasedRecordDeserializer.java`（无调用）；`AbstractModelBasedRecordSerializer.java:48,141` |
| 5 | P1 | `transformIn`/`transformOut`/`parseExpr` 未接线：xdef 声明、生成模型有 getter，引擎从不求值 | `record-simple-field.xdef:53-58` |
| 6 | P1 | `terminator`/`includeTerminator`/`tillEnd` 声明但无引擎实现，且全仓 record 模型无使用（fail-fast 裁定安全） | `record-simple-field.xdef:33-34`；全仓 grep 无模型使用 |
| 7 | P1 | `RecordSerializerCodeGenerator` 死代码：生成不可编译代码（`genLiteral`/`getRecordJavaType` 返回 null、`enterField(String)` 与接口签名不匹配、引用不存在的方法），全仓零引用 | `nop-format/nop-record/src/main/java/io/nop/record/codegen/`（文件已删除，见 Phase 4） |
| 8 | P1 | `ModelBasedPacketCodec.getUnadjustedFrameLength` lengthCodec 分支 `buf.skipBytes(offset)`，`offset` 已含 `readerIndex`，`readerIndex>0`（TCP 粘包残留）时读取位置错位；默认分支用绝对 getter 正确 | `codec/impl/ModelBasedPacketCodec.java:97-98,228-240` |
| 9 | P2 | 二进制定长字段 EOF 静默截断（`readBytes` 返回短数组）与文本 `readFully` 抛 `ERR_RECORD_NO_ENOUGH_DATA` 不一致 | `serialization/ModelBasedBinaryRecordDeserializer.java:107-110` vs `ModelBasedTextRecordDeserializer.java:93`；`codec/impl/AbstractFixedLengthAsciiCodec.java:47` |
| 10 | P2 | `subInput` 区域未消费残留不跳过（惰性消费导致后续错位）；`readObject` 返回 `pos != in.pos()` 在子视图下是绝对/相对位置不可比；`readCollection` fixed 分支用外层 `in.isEof()` 而非子视图判定边界 | `serialization/AbstractModelBasedRecordDeserializer.java:55-84,216-230` |
| 11 | P2 | 模型无 body 时构造 NPE（`fileMeta.getBody().getRepeatKind()`），无明确错误码 | `resource/AbstractModelBasedRecordInput.java:62`；`AbstractModelBasedRecordOutput.java:73` |
| 12 | P2 | `BlockCachedBinaryDataReader.backwardCacheBlocks` 已暴露但从未参与淘汰逻辑 | `nop-format/nop-record/src/main/java/io/nop/record/reader/BlockCachedBinaryDataReader.java`（字段） |
| 13 | P2 | `PeekMatchRuleParser.skipNewlines` 死代码（只判 EOF token，永不前进；因 lexer 已跳过空白而无害） | `nop-format/nop-record/src/main/java/io/nop/record/match/PeekMatchRuleParser.java` |
| 14 | P2 | `RecordDefinitions.getDefaultCharsetObj` 缓存不一致：charset 非 null 分支每次 `Charset.forName`（`forName` 自身有实例缓存，实际影响仅多一次调用） | `nop-format/nop-record/src/main/java/io/nop/record/model/RecordDefinitions.java` |
| 15 | P2? | `ByteBufBinaryDataReader.subInput` 用 `slice()` 共享底层 refCnt，close/release 语义待验证 | `nop-format/nop-record/src/main/java/io/nop/record/netty/ByteBufBinaryDataReader.java`（待 Proof） |

其他已核实事实：

- Dyn 系 codec（#2/#3）在主仓无实例化、无注册点；`_gen` 注册表只注册 18 个基础类型 + `FLS`/`EOL`/`bitmap128`（`FieldCodecRegistry.java:30-32`）。
- netty 测试模型 `test-rpc.packet-codec.xml` 引用未注册的 `VLA`/`FLA` codec，但 `TestModelBasedTcpServer` 被 `@Disabled`，当前不触发（不做修复，记入 Follow-up）。
- 现有测试 12 个测试类（`TestModelBasedRecordInput/Output/ResourceRecordIO`、`TestStreamingDeserializer`、`TestBlockCached*`×4、`TestDetachReaders`、`TestModelBasedPacketCodec`、`PeekMatchRuleParserTest`、`TestRecordTypeMatch`），对上述缺陷均无覆盖。
- `docs-for-ai/` 无 record-file owner doc（仅 `record-mapping.md`/`batch-dsl.md` 相关）；xdef 注释即行为契约文本。

## Goals

- 每个缺陷：先写测试确认缺陷存在（红），再修实现（绿），测试保留为回归测试。
- 行为裁定全部显式化（详见各 Phase），不引入静默 no-op。
- `./mvnw test -pl nop-format/nop-record` 全绿；`nop-record-netty` 模块编译不受影响。

## Non-Goals

- 不实现 `terminator`/`includeTerminator`/`tillEnd` 的真实读取语义（只做 fail-fast 裁定，真实实现留给 successor plan）。
- 不重写 `RecordSerializerCodeGenerator`（删除）。
- 不修复 netty `VLA`/`FLA` 未注册问题（被 `@Disabled` 测试引用，见 Follow-up）。
- 不改 `IBinaryDataReader.readBytes` 的**接口默认**语义（Kaitai 兼容），只在定长字段解析点做严格校验；具体 reader 实现的异常统一除外（如 `ByteBufferBinaryDataReader` 的 `BufferUnderflowException` → `ERR_RECORD_NO_ENOUGH_DATA`，见 Phase 5，R2-7）。
- 不做 record-file 能力面扩展（如新 codec、新 repeatKind）。

## Scope

### In Scope

analysis 确认缺陷 #1–#15（其中 #15 为 Proof-first 裁定项）。

### Out Of Scope

- terminator 真实实现（#6 的 successor）
- RecordSerializerCodeGenerator 重写（#7）
- VLA/FLA 注册、netty 模块代码改动
- `readCollection` 中 `IllegalStateException` 裸异常改错误码（遗留项，记 Follow-up）

## Execution Plan

执行纪律：每个 Phase 先写测试（红）→ 记录红证据 → 修实现 → 记录绿证据 → 更新 xdef/日志 → 勾选。所有 Phase 顺序执行。

### Phase 1 - checkMinLen 修复 + 长度错误码参数统一

Status: completed
Targets: `nop-format/nop-record`（`RecordMetaHelper`、`RecordErrors`、新增 `TestRecordMetaHelper`）

- Item Types: `Fix | Proof`

- [x] `Fix` 红测试 `TestRecordMetaHelper`：① 字段带 `<schema minLength="3"/>`，`padText("ab", field)` 断言抛 `ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE`（当前不抛 → 红）；② `padBinary` 同样断言（红）；③ maxLength 场景断言异常参数 `maxValue` 存在、minLength 场景断言参数 `minValue` 存在（当前为 `maxLength`/`minLength` → 红）。测试装置：编程构造 `RecordSimpleFieldMeta`（`setSchema(...)` 后经 `init`）或 `DslModelParser` 解析最小 `record-file.xml`（与 Phase 3/5 共用模型文件惯例）
- [x] `Fix` 修正 `checkMinLen`：`int min = field.safeGetMinLen(); if (min > 0 && len < min)`（`RecordMetaHelper.java:41-49`）——**执行裁定修订**：`safeGetMinLen()` 无显式 minLength 时 fallback 到 `getLength()`，与 pad 补全语义冲突（定长字段写短值应 pad 而非报错，现有 test.record-file.xml 模型即此形态）→ 改为**仅检查显式 `schema.minLength`**；`safeGetMinLen` 因此无使用方（记入日志观察项）
- [x] `Fix` 统一错误码参数：以 `RecordErrors.java:63-69` 声明为准，`checkMaxLen`/`checkMinLen` 调用点改传 `ARG_MAX_VALUE`/`ARG_MIN_VALUE`
- [x] `Proof` 全仓 grep `checkMaxLen|checkMinLen` 调用点，确认无其他使用方残留旧参数（仅 `padText`/`padBinary` 内部 4 处调用，均无外部使用方）

Exit Criteria:

- [x] 红证据：修复前 `./mvnw test -pl nop-format/nop-record -Dtest=TestRecordMetaHelper` 失败（断言错误，非编译错误）——4 个用例全部失败：短值 2 例不抛异常、长值 2 例 `maxValue` 参数为 null
- [x] 绿证据：修复后同一命令通过（Tests run: 4, Failures: 0）；全模块 `./mvnw test -pl nop-format/nop-record` 通过（Tests run: 113, Failures: 0, Errors: 0）
- [x] 新增测试覆盖：`padText`/`padBinary` 短值抛错、错误参数名断言（`TestRecordMetaHelper` 4 用例）
- [x] No owner-doc update required（纯内部校验修复，xdef `schema minLength` 语义未变）
- [x] `ai-dev/logs/2026/08-16.md`（或执行当日）追加条目

### Phase 2 - Dyn 系 codec 修复

Status: completed
Targets: `nop-format/nop-record`（`DynCountArrayBinaryCodec`、`DynLVFieldBinaryCodec`、新增 `TestDynCodecs`）

- Item Types: `Fix | Decision`

- [x] `Fix` 红测试 `DynCountArrayBinaryCodec`：`countCodec=FieldBinaryCodec_u2be.INSTANCE`、`itemCodec=FixedLengthStringCodecFactory` 派生定长字符串 codec，decode 断言返回真实 item 列表（当前全 null → 红）；encode→decode round-trip 断言字节内容（当前 encode 不写 item → 红）
- [x] `Decision` 裁定 `DynCountArrayBinaryCodec` 构造：新增 `DynCountArrayBinaryCodec(IFieldBinaryCodec countCodec, IFieldBinaryCodec itemCodec, int itemLength)`；旧两参构造保留但 decode/encode 调用时抛 `UnsupportedOperationException("not yet implemented: itemCodec required")`（No Silent No-Op）
- [x] `Fix` 实现 decode：countCodec 读 count → 每个 item 用 `subInput(itemLength)` + itemCodec.decode；实现 encode：countCodec.encode(count) + 逐 item itemCodec.encode（与已实现 `CountArrayBinaryCodec` 对齐）
- [x] `Fix` 红测试 `DynLVFieldBinaryCodec`（valueCodec==null 路径）：encode ByteString 值 → decode，断言 round-trip 字节一致（当前 encode 只写长度、decode 返回 `len` → 红）
- [x] `Decision` 裁定 valueCodec==null 语义：decode 读取 `len` 字节返回 `ByteString`；encode 将 value（`ByteString` 直接写；`String` 按 UTF-8 编码）写出 `len` 字节；无法处理类型 → `UnsupportedOperationException`。**decode 读值必须显式长度校验**（循环读取 + `n == len` 校验，不足抛 `ERR_RECORD_NO_ENOUGH_DATA`），不得依赖 `IBinaryDataReader.readBytes` 默认短读语义（P1-4：否则 EOF 静默截断在 Phase 5 的严格化范围之外）——**执行中发现并规避**：`ByteBufferBinaryDataReader.read()` 在 EOF 返回 0（非 -1），`IBinaryDataReader.tryReadFully` 默认实现 `n < 0` 退出条件不匹配 → EOF 死循环（jstack 实证于 testDynLVDecodeRejectsShortData），`readBytesStrict` 自实现 `n <= 0` 退出循环；`tryReadFully` 契约问题记入日志，Phase 5 EOF 严格化须一并处理
- [x] `Fix` 实现上述 encode/decode（`DynLVFieldBinaryCodec.java:45-46,61`）；javadoc 注明 String→UTF-8 硬编码限制（codec 无 charset 字段，与模型 charset 可能不一致，属已知限制）

Exit Criteria:

- [x] 红证据：`./mvnw test -pl nop-format/nop-record -Dtest=TestDynCodecs` 修复前失败（6 用例 5 failure + 1 error：旧构造 decode 返回 null 列表、encode 不写 item、旧构造不 fail-fast、DynLV decode 返回 Integer、DynLV encode 只写长度）
- [x] 绿证据：修复后通过（Tests run: 6, Failures: 0）；全模块测试通过（Tests run: 119, Failures: 0, Errors: 0, Skipped: 1 @Disabled）
- [x] 新增测试覆盖：dynCount 三参构造 round-trip（encode 字节断言 + decode）、旧构造 fail-fast、dynLV valueCodec==null round-trip（ByteString + String UTF-8）、EOF 短读抛 `ERR_RECORD_NO_ENOUGH_DATA`
- [x] No owner-doc update required（Dyn 系 codec 未注册、无使用方，类内 javadoc 注明语义）
- [x] `ai-dev/logs/` 对应日期条目

### Phase 3 - readWhen / transformIn / transformOut / parseExpr 接线

Status: completed
Targets: `nop-format/nop-record`（`AbstractModelBasedRecordDeserializer`、`ModelBasedBinary/TextRecordDeserializer`、`ModelBasedBinary/TextRecordSerializer`、`StreamingRecordDeserializer`、`AbstractModelBasedRecordInput`/`Output`、`RecordErrors`、xdef 注释、新增 `test-when-transform.record-file.xml` + `TestReadWhenTransform`）

- Item Types: `Fix | Proof | Decision`

- [x] `Proof` 全仓 grep 确认无 record 模型使用 readWhen/transformIn/transformOut/parseExpr（已核实仅 `record-file.imp.xml:284` 有 parseExpr 的导入定义，模型文件无使用）——**执行复核**：除新建测试模型外零使用
- [x] `Fix` 红测试字段级 readWhen：模型 body 字段 `a` 带 `readWhen`，两种引用方式各一用例：① 引用同一记录内前置字段（`record.b == "off"`，经 `record` 参数——实际用 `record.d.trim() != 'off'`，d 为 parseExpr 前置字段）；② 引用 header 变量（header 含 `mode` 字段，经 varName 入 scope）。false 时断言字段被跳过：不消费字节、值不设置、后续字段对齐（红证据：stash 实现后 3F+4E）——**执行修订**：readWhen 等属性在 xdef 中是**子元素**（record-simple-field.xdef:50/53/56/58 为 `<readWhen>` 声明），实例 XML 必须用 `<readWhen>expr</readWhen>` 子元素形式而非属性形式（属性形式抛 `xdsl.attr-not-allowed`）
- [x] `Fix` 输入侧 header/trailer 变量管线：`readHeader`/`readTrailer` 解析后 `context.getEvalScope().setLocalValues(headerMeta/trailerMeta)`（写侧 `beginWrite`/`endWrite` 同款）；null-guard（段不存在/readWhen=false → headerMeta=null → 跳过 setLocalValues）
- [x] `Fix` 实现 `readField` 顶部求值 `field.getReadWhen()`（false → `return false`，不消费，先于 offset 跳过，与写侧 `shouldIgnoreWrite` 位置对称）：`AbstractModelBasedRecordDeserializer.readField`（:127）
- [x] `Fix` 红测试嵌套对象级 readWhen：typeRef 字段对象带 readWhen=false → 断言字段值为 null、输入位置不前进（红）——**执行修订**：`readSwitch` 需在 `makeObject`（会 setProp 创建空对象）**之前**预检 typeMeta.getReadWhen()，否则 record 字段残留空 Map（实测 g={}）
- [x] `Fix` 实现 `readObject` 求值 `recordMeta.getReadWhen()`（false → `return false`，不消费，先于 beforeRead，与写侧 `writeObject` 对称）：`AbstractModelBasedRecordDeserializer.readObject`（:55）；`readSwitch`（:268-273）中 `readObject` 返回 false 时返回 null
- [x] `Decision` 裁定 body 顶级类型 readWhen：不支持（无法"跳过而不消费"地推进记录流，会造成死循环或歧义）→ `AbstractModelBasedRecordInput` 构造时若 `resolvedBody.getReadWhen() != null` 抛新错误码 `ERR_RECORD_READWHEN_NOT_SUPPORTED_AT_TOP_LEVEL`（快速失败）——已实现 + 红测试 `test-top-level-when.record-file.xml`
- [x] `Decision` 裁定 header/trailer 级 readWhen：**支持**——`readObject` 返回 false → 该段视为不存在（headerMeta 保持空、trailer 不设置），流继续；`readHeader`/`readTrailer` 处理 readObject 返回值 + setLocalValues；红测试 `test-header-when.record-file.xml`（header readWhen=false → headerMeta null + body 从流首对齐）——**执行注**：header readWhen 表达式仅可引用外部 scope 变量（构造期求值，header 字段尚未入 scope，测试用常量 `false`）
- [x] `Decision` 裁定集合内 item readWhen=false 语义：fixed 分支 `value == null → continue`（不提前终止集合）；count/expr 分支 null 不加入集合；repeatUntil 分支同 count（R2-2）——**执行注**：fixed 分支 item 不消费字节时位置错位/死循环风险依赖 Phase 5 readObject 对齐（对象带 length 场景），Phase 3 红测试用 count 分支（数据恰好 EOF 设计）
- [x] `Fix` 红测试集合内 item readWhen：count 分支 readWhen=false → `items.isEmpty()`（修复前 [null,null] → 红）；readWhen=true → item 正常解析（绿）
- [x] `Fix` 流式路径接线（P1-1）：`StreamingRecordDeserializer.processFieldStreaming` 在 **`FIELD_STAGE_BEFORE_READ` 阶段、offset 之前**求值字段级 readWhen（false → `setFieldStage(FIELD_STAGE_COMPLETED)` 跳过整个字段，不消费、不触发 before/after 回调）——非"方法顶部"（可重入状态机，R2-3）；`processObjectStreaming` STAGE_INIT 求值对象级 readWhen（false → 返回 null）；`StreamingStackFrame` 新增 `setFieldStage`
- [x] `Fix` 红测试流式 readWhen：`supportStreaming` 字段带 readWhen，流式模式（useStreaming=true）断言路径可读（红：stash 后失败）
- [x] `Decision` 裁定 transformOut 与 content/codec 分支交互：`field.getContent()` 非空（content 字段）时 transformOut 不适用（content 原样写出）；transformOut 仅作用于 value 路径（writeField0 内 `getContent() == null` 条件）
- [x] `Fix` 红测试 transformIn：字段带 `transformIn` 表达式（`value + 'X'`），断言读取结果已转换（红 → 绿）
- [x] `Fix` 红测试 transformOut：字段带 `transformOut` 表达式（`value + 'O'`），断言写出内容为表达式结果（红 → 绿；测试用可注入 StringBuilder 的 `AppendableTextDataWriter`——该类无内容 getter）
- [x] `Fix` 红测试 parseExpr：字段带 `parseExpr`（`input.readFully(5)`），断言绕过默认 decode 读取原始 5 字符（红 → 绿）
- [x] `Fix` 实现：`readField0`（binary :87 / text :84）最前应用 `parseExpr`（`call3(null, in, record, context, scope)` 直接返回）；decode 后应用 `transformIn`（`call3(null, record, value, context, scope)`）；`writeField0`（binary :31 / text :29）取值后应用 `transformOut`（content 字段除外）

Exit Criteria:

- [x] 红证据：`./mvnw test -pl nop-format/nop-record -Dtest=TestReadWhenTransform` 修复前失败（stash 实现后 Tests run: 9, Failures: 3, Errors: 4——readWhen/transform/parseExpr/集合/流式全部失效）
- [x] 绿证据：修复后通过（Tests run: 9, Failures: 0）；全模块测试通过（Tests run: 128, Failures: 0, Errors: 0, Skipped: 1 @Disabled）；原 `TestModelBasedRecordInput/Output` 等无回归
- [x] 新增测试覆盖：字段级/嵌套对象级 readWhen 跳过、集合内 item readWhen 跳过（count 分支）、header 级 readWhen（不存在语义）、body 级 readWhen 快速失败、transformIn/transformOut/parseExpr 生效、流式 readWhen 路径
- [x] 流式模式 readWhen 生效验证（Rule #23 接线验证）：`processFieldStreaming`/`processObjectStreaming` 运行时求值已接线（testStreamingReadWhen 覆盖）
- [x] xdef 注释同步：`record-simple-field.xdef` 各属性注释与落地行为一致（readWhen/transformIn/transformOut/parseExpr 语义已与注释一致，仅核对）；无 docs-for-ai 变更（无 record-file owner doc）
- [x] `ai-dev/logs/` 对应日期条目

### Phase 4 - 死代码删除 + packet lengthCodec 修复 + 未实现属性 fail-fast

Status: completed
Targets: `nop-format/nop-record`（`RecordSerializerCodeGenerator` 删除、`ModelBasedPacketCodec`、`RecordErrors`、`RecordFileMeta`/`RecordObjectMeta` 校验、xdef 注释、`TestModelBasedPacketCodec` 扩展、新增 `TestFailFastAttributes`）

- Item Types: `Fix | Proof`

- [x] `Proof` grep 确认 `RecordSerializerCodeGenerator` 零引用（主仓 + 测试，仅自身文件）
- [x] `Fix` 删除 `nop-format/nop-record/src/main/java/io/nop/record/codegen/` 下的 `RecordSerializerCodeGenerator.java`（No new test required: 死代码删除，删除后 grep 零引用为证明；文件已删除，codegen 目录已空）
- [x] `Fix` 红测试 packet lengthCodec 分支：`PacketCodecModel`（lengthFieldOffset=1、lengthFieldLength=2、lengthFieldCodec="u2be"）+ ByteBuf 写入残留+帧头+长度字段（绝对 index 2-3 = 12）+ 帧内容、`readerIndex=1`，断言 `determinePacketLength` 返回 15（修复前 4 → 红，stash 验证 1F）
- [x] `Fix` 修复 `getUnadjustedFrameLength` lengthCodec 分支（`ModelBasedPacketCodec.java:228-240`）：记录 `readerIndex` → **`setReaderIndex(offset)`**（绝对定位，offset 由 determinePacketLength 已含 readerIndex）→ lengthCodec.decode → finally 恢复 `readerIndex`（与默认分支绝对 getter 语义一致；原 `skipBytes` 是相对跳转 → readerIndex>0 时双重偏移）
- [x] `Fix` 红测试未实现属性 fail-fast：模型字段设置 `terminator=","`，`DslModelParser` 解析即抛 `ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED`（stash 验证 1F → 红）
- [x] `Fix` 实现：`RecordErrors` 新增 `ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED`（参数：`attributeName`、`fieldName`）；模型 init 校验定在 `RecordFileMeta.init`（遍历 header/body/trailer 类型下所有字段，嵌套 typeRef 递归 + seen 防循环），发现 terminator/includeTerminator/tillEnd 被设置即抛
- [x] `Fix` xdef 注释：`record-simple-field.xdef` 标注 terminator/tillEnd "尚未实现，模型设置即报错"

Exit Criteria:

- [x] 红证据：packet lengthCodec 测试（expected 15, was 4→stash 后 1F）与 fail-fast 测试（stash 后 1F）修复前失败
- [x] 绿证据：修复后通过；全模块测试通过（Tests run: 130, Failures: 0, Errors: 0, Skipped: 1）；`TestModelBasedPacketCodec` 既有用例无回归
- [x] 删除后 `RecordSerializerCodeGenerator` 全仓零引用（grep 证据：无任何引用）
- [x] xdef 注释已同步；No owner-doc update required（docs-for-ai 无 record-file owner doc）
- [x] `ai-dev/logs/` 对应日期条目

### Phase 5 - 子视图边界 / 固定集合边界 / body NPE / 二进制 EOF 严格化

Status: completed
Targets: `nop-format/nop-record`（`AbstractModelBasedRecordDeserializer`、`ModelBasedBinaryRecordDeserializer`、`AbstractModelBasedRecordInput`/`Output`、`AbstractFixedLengthAsciiCodec`、`ByteBufferBinaryDataReader`、`IBinaryDataReader`、`RecordErrors`、新增 `TestRecordRegionBoundary`）

- Item Types: `Fix`

- [x] `Fix` 红测试区域残留错位：binary 模型，body 对象 `length=20` 但字段只消费 15，断言第 1 条记录后第 2 条记录对齐解析。**输入用惰性 subInput reader（`StreamBinaryDataReader`）**（stash 验证 4F+2E → 红）——**执行修订**：模型增加 `supportStreaming="true"` 字段 c 以同时覆盖 R2-1 流式路径；数据每条记录 20 字节（含 5 字节残留）
- [x] `Fix` 实现 `readObject` 对齐（`AbstractModelBasedRecordDeserializer.java:55-84`）：subInput 创建前（beforeRead 之后）捕获父 reader 引用 `baseIn` + 位置 `subStart`；`_readObject` 后 `remaining = (subStart + length) - baseIn.pos()`，`remaining > 0` 时 `readOffset(baseIn, remaining, context)`——按父位置差计算（急切 reader 下父已前进 length，remaining 自动为 0）；返回值裁定：`length > 0` → 恒 `true`（区域按定义已消费）；`length <= 0` → 保留 `pos != in.pos()` 语义
- [x] `Fix` 红测试 fixed 集合边界：repeatKind=fixed、区域长度 15、item 字段 5 字节 → 断言 item 数=3（读满区域）且后续字段 c 正常解析不越界（修复前用外层 `in.isEof()` → 越界读 c 数据当 item → 4 items + c 数据不足 → 红）
- [x] `Fix` 实现 `readCollection` fixed 分支（`AbstractModelBasedRecordDeserializer.java:216-230`）：循环判定改 `!subInput.isEof()`；循环后跳过区域剩余字节（`remaining = (subStart + length) - baseIn.pos()`，同 readObject 对齐公式）；`length <= 0` 时保持原 `in.isEof()` 行为
- [x] `Fix` 红测试 body 缺失：只有 header/trailer 无 body 的模型，Input 侧构造即抛 `ERR_RECORD_BODY_NOT_DEFINED`；Output 侧 `beginWrite` 抛（构造不抛）
- [x] `Fix` 实现：`RecordErrors` 新增 `ERR_RECORD_BODY_NOT_DEFINED`；`AbstractModelBasedRecordInput` 构造、`AbstractModelBasedRecordOutput.beginWrite` 判空抛错（带 typeName 参数）
- [x] `Fix` 红测试二进制 EOF 截断：binary 模型字段 length=10、实际数据 5 字节 → 断言抛 `ERR_RECORD_NO_ENOUGH_DATA`；**`StreamBinaryDataReader`（惰性，静默短读）**与 **`ByteBufferBinaryDataReader`（BufferUnderflowException）**双路径
- [x] `Fix` 实现严格校验：`ModelBasedBinaryRecordDeserializer.decodeString`（:107-110）与 `AbstractFixedLengthAsciiCodec.decode` 二进制路径（:47）读取后 `bytes.length < length` → 抛 `ERR_RECORD_NO_ENOUGH_DATA`（带 pos/length）；`ByteBufferBinaryDataReader.readBytes` 的 `BufferUnderflowException` 统一为同一错误码；**`IBinaryDataReader.tryReadFully` 默认实现 `n < 0` → `n <= 0`（Phase 2 发现的 ByteBuffer reader EOF 死循环契约修复）+ `len <= 0` 先返回 0（避免 readFully(0) 误判）**
- [x] `Fix` 流式对象区域残留对齐（R2-1）：`StreamingRecordDeserializer` STAGE_INIT 记录 subBaseIn/subStartPos/subLength 到 frame（`StreamingStackFrame` 新增字段），帧完成处 `remaining = (subStart + subLength) - subBaseIn.pos()` → `readOffset` 跳过；红/绿测试 `testStreamingRegionLeftoverAligned`（流式 item 粒度：每条记录 3 个 item——字段数据/字段结束/记录结束）

Exit Criteria:

- [x] 红证据：`./mvnw test -pl nop-format/nop-record -Dtest=TestRecordRegionBoundary` 修复前失败（stash 后 Tests run: 7, Failures: 4, Errors: 2）
- [x] 绿证据：修复后通过（Tests run: 7, Failures: 0）；全模块测试通过（Tests run: 137, Failures: 0, Errors: 0, Skipped: 1 @Disabled）；**执行中修复测试数据**：`test.txt` 的 TRAILER 字段声明 length=20 但数据仅 7 字节（残缺数据依赖静默短读）→ 补全为 20 字节 + 修正记录布局（31 字节，无尾部换行，避免第 3 条记录解析）——`TestRecordTypeMatch` 语义不变（P1-6：trailer 路径保持覆盖）
- [x] 新增测试覆盖：区域残留对齐（惰性/急切双路径）、fixed 集合边界、body 缺失错误码（Input/Output 双路径）、二进制 EOF 抛错（惰性/ByteBuffer 双路径）、流式对象 length>0 区域残留对齐（R2-1）
- [x] xdef 注释核对（对象 length 语义未变）；No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目

### Phase 6 - 小修复与待验证项

Status: completed
Targets: `nop-format/nop-record`（`BlockCachedBinaryDataReader`、`PeekMatchRuleParser`、`RecordDefinitions`、`ByteBufBinaryDataReader`、扩展 `TestBlockCachedBinaryDataReader2`、扩展 `TestModelBasedPacketCodec`）

- Item Types: `Fix | Proof | Decision`

- [x] `Decision` 裁定 `backwardCacheBlocks`：读淘汰逻辑后裁定**实现向后窗口**——淘汰只移除最老的向后块（`endPosition <= currentPosition`），直到向后块数 ≤ `backwardCacheBlocks`（总缓存同时受 `maxCacheBlocks` 约束，向后无剩余可淘汰时容忍略超）；窗口保持连续（无空洞）；红测试：默认构造（bwd=2）读 850 字节后 seek 回退 3 块应抛 `ERR_RECORD_POS_NOT_IN_CACHE`（修复前窗口 7 块可回退 → 红；修复后向后窗口 2 块 → 绿）——**执行中发现**：构造 clamp（`maxCacheBlocks <= backwardCacheBlocks ? max-1 : bwd`）使 (2,2)→1；淘汰循环原受 maxCacheBlocks 限制使 bwd 无效（向后块 = max-1），改为 bwd 主导淘汰
- [x] `Fix` 按裁定执行；红测试（向后窗口）：读 850 → `seek(150)` 抛（红）；`seek(750)` 窗口内可读（绿）
- [x] `Proof` grep `skipNewlines` 引用 → `Fix` 删除死方法（`PeekMatchRuleParser.java`；No new test required: 死代码删除，`PeekMatchRuleParserTest` 全绿为回归证明）
- [x] `Fix` `RecordDefinitions.getDefaultCharsetObj` 两分支统一缓存（No new test required: `Charset.forName` 自身有实例缓存，行为无差异，仅消除重复调用）
- [x] `Proof` 红测试 `ByteBufBinaryDataReader.subInput` refCnt：subInput 读 + 父 reader close 后再读——**复现**（父 close 后子读抛 `IllegalReferenceCountException`，红：Fix 前 1E）→ `Fix` subInput 创建时 `slice.retain()`（子视图独立所有权，父 close 不影响子；子 close 时 release 对称）→ 绿（父 close 后子仍可读 + 数据正确）

Exit Criteria:

- [x] 红证据（适用项）与绿证据：`./mvnw test -pl nop-format/nop-record` 全绿（Tests run: 139, Failures: 0, Errors: 0, Skipped: 1 @Disabled）；backwardCacheBlocks 红测试 stash 验证 1F、refCnt 红测试 stash 验证 1E
- [x] 每项裁定结论记录到 `ai-dev/logs/`（backwardCacheBlocks 向后窗口裁定、refCnt 复现+retain 修复）
- [x] No owner-doc update required（内部实现级修复）
- [x] `ai-dev/logs/` 对应日期条目

## Closure Gates

> 关闭条件：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 缺陷 #1–#15 全部修复或裁定（#15 按 Proof 结论落为 fix：subInput slice().retain()）
- [x] 每个缺陷有对应回归测试（或明确 `No new test required: <reason>`：#7/#13 删除 Proof、#14 行为等价）
- [x] `./mvnw test -pl nop-format/nop-record` 全绿（139 tests, 0 failures, 1 @Disabled 既有）
- [x] `./mvnw compile -pl nop-format/nop-record-netty -am` 通过（BUILD SUCCESS，含 nop-record-mapping 依赖链）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors 0 warnings）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/203-*.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-record --severity high` 退出码 0（0 Critical/0 High/0 findings）
- [x] 无 in-scope 缺陷被静默降级到 deferred / follow-up（Deferred 项分类审计 PASS）
- [x] 受影响的 xdef 注释已同步（terminator/tillEnd 标注"尚未实现，模型设置即报错"；docs-for-ai 无 record-file owner doc，明确 No owner-doc update required）
- [x] 独立子 agent（fresh session）closure-audit 完成，证据写入本 plan `Closure` 段（ses_ff56c39f6ffe60UggRUgDEmq6L，verdict APPROVE）
- [x] Anti-Hollow Check：closure audit 验证无空方法体/静默跳过/no-op 作为正常实现（readWhen 5 处求值点、transform 链、Dyn codec 均有运行时测试路径；scan-hollow 0 findings）

## Deferred But Adjudicated

### terminator / includeTerminator / tillEnd 真实读取语义

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 全仓无使用方；本 plan 已裁定为配置即快速失败（Phase 4），不存在静默 no-op；真实语义属新功能
- Successor Required: `yes`
- Successor Path: 后续 plan（实现字段 terminator 扫描读取，可基于 `IBinaryDataReader.readBytesTerm` 原语）

### VLA / FLA codec 未注册

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 仅被 `@Disabled` 的 `TestModelBasedTcpServer` 引用，当前不可达；修复属于 netty 模块能力补全
- Successor Required: `yes`
- Successor Path: 后续 plan（注册/实现 VLA/FLA codec 并启用测试）

### 嵌套子区域 + 惰性 reader 的 position 失真

- Classification: `watch-only residual`（R2-6）
- Why Not Blocking Closure: `SubBinaryDataReader.subInput`（:125-127）直接 `underlying.subInput(...)`，绕过外层 position 计数器——嵌套 length 对象 + 惰性 reader 下对齐公式的 `baseIn.pos()` 失真。预存缺陷；全仓无嵌套子区域使用方，Plan 红/绿测试全部为扁平模型（无嵌套），无测试可见性；急切 reader（ByteBuffer/ByteBuf 子视图）路径正确
- Successor Required: `no`（若未来出现嵌套子区域使用方，须先修此 position 跟踪再启用对齐公式）

### readCollection 中裸 `IllegalStateException`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 非本次 analysis 缺陷清单项；行为正确（会抛错），仅异常类型不符合模块错误处理规范
- Successor Required: `no`

## Non-Blocking Follow-ups

- `BeanBinaryCodec` 等已实现但未注册的 codec 是否纳入 `FieldCodecRegistry` 注册表（治理项）
- `AbstractModelBasedRecordInput.readRepeatCount` 在 expr 且无任何 count 来源时静默按 0 处理（优化项，改为加载期校验）

## Closure

Status Note: analysis 确认的 15 项缺陷全部修复或裁定；6 个 Phase 红→绿完成；全模块 139 tests 全绿；独立 closure-audit APPROVE。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit agent（fresh session）
- Audit Session: ses_ff56c39f6ffe60UggRUgDEmq6L
- Evidence:
  - 11 条 Closure Gate 全部 PASS（缺陷映射逐条 live 实证 + 回归测试名）
  - 每条 Exit Criterion 验证：Phase 1-6 共 78 项全勾选，红→绿证据（stash 验证）记录在 plan 各 Phase
  - `check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 0 errors；`scan-hollow-implementations.mjs --severity high` 0 findings
  - Anti-Hollow：readWhen 5 处求值点（readField/readObject/readSwitch/processFieldStreaming FIELD_STAGE_BEFORE_READ/processObjectStreaming STAGE_INIT）调用链运行时连通；transformIn/transformOut/parseExpr 在 readField0/writeField0 落地并有值断言测试；Dyn codec 旧构造 fail-fast
  - Deferred 项分类检查：terminator 真实语义/VLA-FLA 注册 = out-of-scope improvement；嵌套子区域 position 失真 = watch-only residual（Successor=no 注明未来使用方须先修）；无 in-scope live defect 被降级
  - 文本一致性：Plan Status/6 Phase Status/Exit Criteria/Closure Gates/日志五处一致

Follow-up:

- no remaining plan-owned work（非阻塞 follow-up 见 `## Non-Blocking Follow-ups` 段：BeanBinaryCodec 注册治理、readRepeatCount 静默 0 加载期校验）