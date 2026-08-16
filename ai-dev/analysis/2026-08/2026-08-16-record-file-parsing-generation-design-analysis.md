# nop-record record file 解析/生成设计与实现分析

> Status: resolved
> Date: 2026-08-16
> Scope: `nop-format/nop-record` + `nop-format/nop-record-netty` + `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/record/*.xdef`
> Conclusion: 分层架构（模型层/读写抽象/序列化/资源IO/codec扩展）能支撑"任意二进制或文本文件、任意二进制或文本消息"的宣称；但实现中存在 1 个确定的校验逻辑错误（checkMinLen 完全失效）、2 个 stub codec 造成数据丢失（Dyn 系）、1 个 netty 可选路径 bug（lengthCodec 分支）、一批"schema 已声明但引擎未实现"的能力（readWhen/transformIn/transformOut/parseExpr/terminator），以及若干一致性风险，需按 P0/P1 分级修复后才能宣称完整支持。

## Context

- 需要回答的问题：record file 解析/生成宣称可用于任意二进制/文本文件以及任意二进制/文本消息，其设计是否成立、实现完整度如何、存在哪些 bug 和改进点。
- 涉及模块：`nop-format/nop-record`（核心引擎，149 个 Java 文件）、`nop-format/nop-record-netty`（TCP 消息集成）、xdef schema（模型定义）。
- 约束：不修改代码，仅产出评估报告；所有结论附代码路径证据。

## Analysis

### 一、总体架构与设计意图

分层结构（自底向上）：

1. **模型层（xdef 驱动）**
   - `record-file.xdef` → `RecordFileMeta`（header/body/trailer、beforeRead/afterRead、二进制/文本判定、模板）
   - `record-object.xdef` → `RecordObjectMeta`（typeRef/baseType 继承、tags、fields、template、rawVarName、readWhen/writeWhen）
   - `record-field.xdef` → `RecordFieldMeta`（repeat 字段、lengthExpr、case 切换）
   - `record-simple-field.xdef` → `RecordSimpleFieldMeta`（定长/padding/trim/terminator/content/defaultValue/offset/varName/skipWhenRead/Write 等 30+ 属性）
   - `record-definitions.xdef` → `RecordDefinitions`（charset/endian/type 定义）
   - `packet-codec.xdef` → `PacketCodecModel`、`record-template.xdef` → `RecordTemplateModel`
   - 通过 `ResourceComponentManager` 加载，支持 Delta 定制（`docs-for-ai` 的模型优先开发模式在此同样生效）。
2. **读写抽象层**
   - 二进制读：`StreamBinaryDataReader` / `ByteBufferBinaryDataReader` / `BlockCachedBinaryDataReader`（滚动窗口缓存）/ `RandomAccessFileBinaryDataReader` / `SubBinaryDataReader`（长度受限子视图，支持 detach/pushBack）
   - 文本读：`SimpleTextDataReader`（全量字符串）/ `ReaderTextDataReader` / `BlockCachedTextDataReader` / `SubTextDataReader`
   - 写：`StreamBinaryDataWriter` / `AppendableTextDataWriter` / `ByteBufBinaryDataReader`/`Writer`（netty）
   - `subInput(maxLength) + detach/pushBack` 机制是解析任意嵌套定长结构的基础，设计良好。
3. **序列化层**
   - `AbstractModelBasedRecordSerializer/Deserializer` + 文本/二进制四个子类；`AbstractModelBasedBinaryRecordDeserializer` 按 `getObjectLength`（lengthExpr/长度前缀/定长）决定是否建立子视图。
   - 支持 tags（位图）、repeatKind（none/fixed/count/expr/until/eos）、writeWhen、beforeRead/afterRead、writeAsMap/readAsMap、rawVarName 诊断、defaultValue、content、skipWhenRead/Write、padding/trim、case 多态（switch on field）。
   - `StreamingRecordDeserializer`：基于 `StreamingStackFrame` 的 stage 状态机（STAGE_INIT→BEFORE_READ→READ_TAGS→READ_FIELDS→AFTER_READ→COMPLETED），任意深嵌套对象的流式输出（`StreamingItem` 携带 collectionSize/index）。
4. **资源 IO 层**
   - `ModelBasedResourceRecordIO`：按资源大小自动选择"全量读内存"或"滚动窗口缓存"；`AbstractModelBasedRecordInput/Output` 处理 header/body/trailer、repeatCount 读取、聚合分页输出（`RecordAggregateState` + `EvalFunctionAggregator`、pageHeader/pageFooter、groupBy 切页）。
5. **扩展点**
   - `FieldCodecRegistry`：按名称注册 codec；`IFieldBinaryCodec` / `IFieldTextCodec` / `IFieldTagBinaryCodec` / `IFieldTagTextCodec`；`DefaultFieldCodecContext` 维护字段路径栈。
   - 内置 codec：`BeanBinaryCodec`（对象）、`LVFieldBinaryCodec`（长度前缀）、`DynLVFieldBinaryCodec`（动态）、`CountArrayBinaryCodec`（count 数组）、`DynCountArrayBinaryCodec`（动态）、`BitmapTagBinaryCodec`（位图）、`EOLFieldCodec`、`FixedLengthStringCodecFactory`、`_gen` 包下 s1/u1/u2le/u4be/f8be 等 18 个基础类型 codec。
6. **netty 集成**
   - `ModelBasedPacketCodec`（仿 Netty LengthFieldBasedFrameDecoder 的帧长确定逻辑）、`PacketProcess`、`PacketStateMachineHandler`、`ModelBasedTcpServer/Client`。
7. **代码生成**：`RecordSerializerCodeGenerator`（未完成，见 P1-6）。

### 二、已核实的功能覆盖（对照"任意文件/任意消息"宣称）

- 文本：定长字段、padding/trim/leftPad、terminator、行式 EOL、record template（静态部分+符号占位符）、case 多态。
- 二进制：定长、LV 长度前缀、count 数组、位图 tags、大小端（le/be 各 9 种基础类型）、String/Number 转换。
- 结构：typeRef 嵌套、baseType 继承、repeat（fixed/count/expr/until/eos）、嵌套重复字段、聚合/分页/分组输出、流式输入（`test-streaming.record-file.xml` 中 rows 字段 `supportStreaming=true`）。
- 测试覆盖：读/写/流式/滚动缓存/detach/packet codec/type match（`TestModelBasedRecordInput/Output/ResourceRecordIO`、`TestStreamingDeserializer`、`TestBlockCachedBinaryDataReader`/`TextDataReader`、`TestDetachReaders`、`TestModelBasedPacketCodec`、`PeekMatchRuleParserTest`、`TestRecordTypeMatch`）。

### 三、确定的问题（bug 级）

#### P0（逻辑错误，特性失效或数据错误）

1. **`RecordMetaHelper.checkMinLen` 完全失效** — `RecordMetaHelper.java:41-49`
   - `int min = field.safeGetMaxLen();` 应为 `safeGetMinLen()`；条件 `len > min` 应为 `len < min`。
   - 后果：值短于 minLength 时静默通过（仅被 padding 补齐），min-length 校验永不触发；且当 maxLen>0 时与 `checkMaxLen`（31-39 行）逻辑重复。
   - 调用方：`padText`/`padBinary`（`RecordMetaHelper.java:134/160`）。
   - 连带问题：两处错误码参数名不匹配 — `RecordErrors.java:63-69` 声明 `ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE` 用 `ARG_MAX_VALUE`（maxValue）、`ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE` 用 `ARG_MIN_VALUE`（minValue），而 `checkMaxLen` 设置 `ARG_MAX_LENGTH`（maxLength）、`checkMinLen` 设置 `ARG_MIN_LENGTH`（minLength），错误消息的参数渲染会缺失。

2. **`DynCountArrayBinaryCodec` 是 stub，decode 产出全 null 列表、encode 丢数据** — `DynCountArrayBinaryCodec.java:26-52`
   - decode 只读 count，`itemCodec.decode(...)` 被注释（35 行），返回 `count` 个 null；encode 只写 count，`serializer.writeObject(...)` 被注释（50 行）。
   - 后果：数组字段的 item 数据在读写两侧全部丢失，且读侧解出的集合全是 null。
   - 无测试覆盖；主仓 grep 无实例化点（仅类本身）。

3. **`DynLVFieldBinaryCodec.encode` 静默丢值** — `DynLVFieldBinaryCodec.java`（encode 中 `valueCodec == null` 分支，值写出语句被注释）
   - decode 侧对 `valueCodec == null` 会原样读取字节，但 encode 侧不写值字节 → 编解码不对称，输出数据静默丢失。

#### P1（功能未接线 / 死代码 / 可选路径 bug）

4. **`readWhen` 在读取路径完全未生效**
   - schema 声明：`record-simple-field.xdef:50`（`<readWhen>xpl-fn:(input,record,ctx)=>boolean</readWhen>`）、`record-object.xdef` 亦有；生成模型 `_RecordSimpleFieldMeta.getReadWhen()`（618 行）、`_RecordObjectMeta.getReadWhen()`（510 行）存在。
   - 但 `AbstractModelBasedRecordDeserializer`（及 Streaming/文本/二进制子类）全程未调用；写路径在 `AbstractModelBasedRecordSerializer.java:48/141` 调用了 `getWriteWhen()`。
   - 后果：读取时条件跳过字段/对象的能力缺失，读写语义不对称；schema 承诺的能力未实现。

5. **其他未接线的 schema 属性**：`transformIn`/`transformOut`/`parseExpr`（`record-simple-field.xdef:53-58`）只存在于生成模型 getter，引擎与内置 codec（`IFieldConfig` 仅暴露 format/scale/padding 等）均未使用；`terminator`/`includeTerminator`/`tillEnd`（xdef:33-34）在引擎中同样无实现。

6. **`RecordSerializerCodeGenerator` 生成不可编译代码且无引用** — `codegen/RecordSerializerCodeGenerator.java`
   - `genLiteral` 返回 null、`getRecordJavaType` 返回 null（生成的类型声明为 `null`）、`context.enterField(String)` 与 `IFieldCodecContext.enterField(RecordFieldMeta)`（`IFieldCodecContext.java:26`）签名不匹配、引用不存在的 writeString/writeInt/getCharset 方法、writeWhen 生成处为 TODO。
   - 全仓 grep 无任何调用点 → 死代码，建议删除或完整重写。

7. **`ModelBasedPacketCodec.getUnadjustedFrameLength` 的 lengthCodec 分支错位** — `ModelBasedPacketCodec.java:228-240`
   - 调用处（97 行）传入 `offset = readerIndex + lengthFieldOffset`，lengthCodec 分支却执行 `buf.skipBytes(offset)`（231 行），即从当前 readerIndex 再跳过 offset 个字节 → 当 `readerIndex > 0`（Netty 累积缓冲残留字节，TCP 粘包场景必然发生）时读取位置错位。
   - 默认分支用绝对 getter（`getUnsignedByte(offset)` 等，245-258 行）是正确的 → 仅影响配置了 `lengthCodec` 的扩展路径。

#### P2（一致性 / 健壮性风险）

8. **二进制读取长度语义不一致**：`IBinaryDataReader.readBytes` 默认实现 EOF 时静默返回短数组；`ByteBufferBinaryDataReader.readBytes` 抛 `BufferUnderflowException`；`ITextDataReader.readFully` 抛 `ERR_RECORD_NO_ENOUGH_DATA`。定长二进制字段在数据不完整时会被静默截断而非报错。

9. **长度受限子视图的"未消费残留"与消费判定失效**
   - `readObject`（`AbstractModelBasedRecordDeserializer.java:55-84`）在 `length > 0` 时创建 `subInput(length)`，`SubBinaryDataReader` 惰性消费底层字节；若对象字段总消耗 < 声明 length，剩余字节留在底层流中，后续字段/记录将错位解析；若字段越界，二进制子视图静默截断（文本抛异常，两者不一致）。
   - 第 83 行 `return pos != in.pos()`：创建子视图后 `in.pos()` 是子视图局部位置（0 起始），与第 56 行记录的绝对 `pos` 不可比，该"是否消费过"判定仅在 pos==0 且零消费时返回 false；流中间位置零消费的对象仍返回 true，EOF/错位判定不可靠。

10. **body 缺失时 NPE**：`AbstractModelBasedRecordInput.java:62`、`AbstractModelBasedRecordOutput.java:73` 构造时直接 `fileMeta.getBody().getRepeatKind()`，只有 header/trailer 无 body 的模型直接 NPE，且无明确错误码（`ModelBasedResourceRecordIO` 本身不触碰 body，故仅这两处）。

11. **`BlockCachedBinaryDataReader.backwardCacheBlocks` 未参与淘汰逻辑**（字段已设置但 eviction 只按 `maxCacheBlocks` 移除最旧块），文档宣称的"向后缓存窗口"未实现；`duplicate()` 丢弃已有缓存并重新 seek，底层为不可 seek 的流式 reader 时抛 UnsupportedOperationException。

12. **`PeekMatchRuleParser.skipNewlines` 是死代码**（只判断 currentToken 是否为 EOF，永不前进；实际无害，因为 lexer 已跳过空白）。

13. **`RecordDefinitions.getDefaultCharsetObj` 缓存不一致**：charset 为 null 时缓存 UTF_8，非 null 时每次 `Charset.forName` 重新解析（性能小问题，逻辑正确）。

14. **`ByteBufBinaryDataReader.subInput` 用 `slice()` 共享底层 refCnt**，close/release 在多读者场景的引用计数语义需谨慎（风险待进一步验证）。

### 四、改进建议

1. 修复 `checkMinLen`（`safeGetMinLen()` + `len < min`），统一 `checkMaxLen`/`checkMinLen` 的错误码参数名，补回归测试。
2. 实现 `readWhen` 语义：在 deserializer 读取字段/对象前求值；或与上游对齐后在 xdef 中标注"未实现"。
3. 补全或删除 `DynCountArrayBinaryCodec`/`DynLVFieldBinaryCodec`；若保留需修正 encode 丢数据问题并加测试。
4. 统一二进制 EOF 语义：`readBytes` 提供严格模式或抛出可识别异常，杜绝静默截断。
5. 子视图消费完毕后补齐跳过剩余字节（对象完成后对齐到区域边界），修复 `pos != in.pos()` 判定。
6. 修复 `ModelBasedPacketCodec` lengthCodec 分支（用 `getInt(offset)` 或临时 `readerIndex` 而非 `skipBytes`）。
7. body 缺失时抛出带明确错误码的异常（如 `ERR_RECORD_BODY_NOT_DEFINED`）。
8. `RecordSerializerCodeGenerator` 重写或删除。
9. 未接线属性（transformIn/transformOut/parseExpr/terminator）在 xdef 标注未实现，或实现之。
10. 为上述缺陷补测试：checkMinLen、readWhen、Dyn 系 codec、packet lengthCodec 分支、子视图边界、无 body 模型。

## Conclusion

- 结论：**设计成立，实现不完整**。分层架构 + 可插拔 codec + subInput/detach + 流式状态机足以支撑"任意二进制/文本文件与消息"的宣称；但确定存在 1 个校验逻辑错误（P0）、2 个 stub codec 数据丢失（P0）、1 个 netty 可选路径 bug（P1）、一批 schema 已声明但引擎未实现的能力（readWhen 等，P1），以及若干一致性风险（P2）。
- 被否决方案：无（本次为纯评估，未做方案对比）。
- 后续工作：建议拆 `ai-dev/plans/`：① 修复 checkMinLen + 错误码参数 + 测试；② readWhen/transformIn 等未接线属性实现或标注；③ Dyn 系 codec 补全/删除；④ packet codec lengthCodec 修复；⑤ 子视图边界与 EOF 语义统一。

## Open Questions

- [ ] `readWhen`/`transformIn` 等是否在文档或示例（含 delta 模块）中被实际使用过，确认是"未实现"而非"故意忽略"
- [ ] Dyn 系 codec 是否在第三方/delta 模块中被实例化（主仓 grep 未发现调用点）
- [ ] `ByteBufBinaryDataReader.subInput` 的 refCnt 共享是否在真实 netty 多包场景可复现泄漏
- [ ] `BlockCachedBinaryDataReader` 滚动窗口在真实大文件场景的读放大比例

## References

- 核心引擎：`nop-format/nop-record/src/main/java/io/nop/record/`（model/、serialization/、reader/、writer/、codec/、codec/impl/、codec/_gen/、resource/、util/、match/、template/、codegen/、netty/）
- 模型定义：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/record/{record-file,record-object,record-field,record-simple-field,record-definitions,packet-codec,record-template}.xdef`
- netty 集成：`nop-format/nop-record-netty/src/main/java/io/nop/netty/ext/`（PacketProcess、PacketStateMachineHandler、ModelBasedTcpServer/Client）
- 测试：`nop-format/nop-record/src/test/java/io/nop/record/`（TestModelBasedRecordInput/Output/ResourceRecordIO、TestStreamingDeserializer、TestBlockCached*、TestDetachReaders、TestModelBasedPacketCodec、PeekMatchRuleParserTest、TestRecordTypeMatch）
- 测试资源：`nop-format/nop-record/src/test/resources/_vfs/test/record/{test.record-file.xml,test-streaming.record-file.xml,test.packet-codec.xml,demo.record-file.xml}` 及 `test.txt`/`group-result.txt`/`data.json`
- 分析规范：`ai-dev/analysis/00-analysis-writing-guide.md`