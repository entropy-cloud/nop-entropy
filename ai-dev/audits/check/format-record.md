# format-record 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-format/{nop-record,nop-record-netty,nop-tablesaw}
- 文件数: 约 175（src/main/java；实际 nop-record 136、nop-record-netty 10、nop-tablesaw 12，其中 nop-record 含 24 个 `_gen/` 生成文件）
- 覆盖范围声明:
  - nop-record-netty（10 文件）与 nop-tablesaw（12 文件）**全部文件逐行阅读**。
  - nop-record 非 `_gen` 文件约 112 个，其中约 45 个核心文件逐行深读：全部 reader/writer（SubBinaryDataReader、StreamBinaryDataReader、BlockCachedBinaryDataReader、ByteBufferBinaryDataReader、RandomAccessFileBinaryDataReader、ByteBufBinaryDataReader/Writer、SimpleTextDataReader、ReaderTextDataReader、SubTextDataReader、BlockCachedTextDataReader 节选、StreamBinaryDataWriter、AppendableTextDataWriter）、全部 codec/impl（定长 FLS、EOL、LV/DynLV、Count/DynCountArray、BitmapTag、BeanBinaryCodec、ModelBasedPacketCodec）、全部 serialization（含 StreamingRecordDeserializer/StackFrame/ReadResult）、resource 包、match/PeekMatchRuleParser、util/RecordMetaHelper、template/RecordTemplateManager、initializer。
  - 其余为接口定义（IFieldXxx、IModelBasedXxx）、模型薄壳类（RecordXxxMeta 继承 `_gen`）与 RecordErrors/RecordConstants 常量类，做扫读 + 定向核对（如 DEFAULT_MAX_COLLECTION_SIZE=1000000、PacketCodecModel.initialBytesToStrip 默认 0）。
  - `_gen/` 生成物、`_` 前缀文件、target/、测试代码未审（按任务约定）。
  - 为验证调用链读取了少量范围外文件（只读）：nop-network/nop-netty 的 PacketCodecHandler、NettyTcpServer.sendToAnyChannel；nop-kernel/nop-commons 的 ByteHelper/StringHelper/MutableString/FixedBitSet；tablesaw-core-0.43.1 字节码（javap 验证 `IntColumn.append(Integer)` 对 null 的行为）。这些文件不计入发现对象。
  - 方法：先结构梳理，再 grep 扫描（空 catch、`new RuntimeException`、`printStackTrace`、`release()/retain()`、`SimpleDateFormat`、`synchronized`），每个命中点 Read 上下文验证。三个模块中未发现空 catch、`printStackTrace`、`SimpleDateFormat`、`synchronized` 命中；`new RuntimeException` 命中 1 处（见 P2-11）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 6 |
| P2 | 9 |
| P3 | 4 |

---

## 发现列表

### [P0] ColumnCollectors 所有 consumer 的 null 判断反了，数据集转 Table 后全部单元格丢失

- **文件**: `nop-format/nop-tablesaw/src/main/java/io/nop/tablesaw/dataset/ColumnCollectors.java:50-170`（10 个 consumer 全部同样问题）
- **维度**: D1 正确性
- **证据**:
```java
private static ObjIntConsumer<IDataRow> consumeString(Column<String> col) {
    return (row, colIndex) -> {
        String value = row.getString(colIndex);
        if (value == null) {
            col.append(value);        // null 走 append(null)
        } else {
            col.appendMissing();      // 非空值走 appendMissing —— 数据被丢弃
        }
    };
}
```
- **现状**: 全部 10 个 consumeXxx 方法（String/Integer/Short/Long/Double/Float/Boolean/LocalDateTime/LocalDate/LocalTime/Instant）均为 `if (value == null) col.append(value); else col.appendMissing();`，null 与非 null 分支完全颠倒。
- **风险**: `DataSetToTableTransformer.apply()`（经 `DataSetHelper.dataSetToTable`、`TablesawHelper.dataSetTransformer` 暴露为模块公共 API）产出的 Table 中**每一个单元格都是 missing**——非空值全部被 `appendMissing()` 丢弃，数据 100% 损坏且无异常抛出，调用方难以察觉。
- **建议**: 两个分支对调：`if (value != null) col.append(value); else col.appendMissing();`
- **误报排除**: 已用 javap 反编译依赖库 tablesaw-core-0.43.1 确认 `IntColumn.append(java.lang.Integer)` 对 null 的处理是 `appendMissing()`（字节码：`ifnonnull → appendMissing`），即两个分支结果同为 missing，不存在"append(null) 恰好写值"的可能；逻辑颠倒与命名语义（appendMissing）自相矛盾，无其他解释。仓库内无其他调用方（公共 API，供下游使用），不影响结论成立。

---

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。全部 10 个 consumer 的 null/非 null 分支对调。测试：`nop-tablesaw` `TestDataSetToTableTransformer#testTransformKeepsValues`（修复前该测试必红：所有单元格变 missing）。

### [P1] netty 解码路径 ByteBuf 引用计数泄漏：subInput retain 后无人 release

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/netty/ByteBufBinaryDataReader.java:377-396`；触发链 `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/ModelBasedPacketCodec.java:162-174` + `nop-format/nop-record/src/main/java/io/nop/record/serialization/AbstractModelBasedRecordDeserializer.java:70-72`
- **维度**: D2 资源管理（ByteBuf 泄漏，重点项）
- **证据**:
```java
// ByteBufBinaryDataReader.subInput
ByteBuf newBuffer = bb.slice();
newBuffer.writerIndex((int) n);
// 子视图独立所有权：父 reader close（release）不影响子视图读取
newBuffer.retain();                       // +1 引用计数

// ModelBasedPacketCodec.decodeFromBuf —— reader 从不 close
deserializer.readObject(new ByteBufBinaryDataReader(buf), typeMeta, record, context);

// AbstractModelBasedRecordDeserializer.readObject —— sub reader 也从不 close
if (length > 0) {
    subInput = (Input) baseIn.subInput(length);
```
- **现状**: 上游 `PacketCodecHandler.decode`（nop-netty）用 `in.readSlice(len)` 取帧（不 retain，cumulation 释放一次即回收）。`decodeFromBuf` 内创建的 `ByteBufBinaryDataReader` 及其 `subInput()` 创建的所有子 reader 都没有任何调用方执行 `close()`/`release()`。Netty slice 共享根缓冲区引用计数，因此每次解码一个带 `length>0` 对象（或 `CountArrayBinaryCodec.decode` 的 `input.subInput(length)`，`CountArrayBinaryCodec.java:33`）都会使 cumulation ByteBuf 的 refCnt 净 +1，永不归零。
- **风险**: 每个 TCP 报文的解码都会泄漏一次 cumulation 缓冲区（通常为池化直接内存），长期运行的网关进程直接内存持续增长直至耗尽；由于 refCnt 永不到 0，Netty 正常释放路径失效（leak detector 也可能不报告）。
- **建议**: `decodeFromBuf` 用 try-with-resources/finally 关闭顶层 reader；或 `subInput` 改为不 retain 的视图 + 由唯一所有者释放；至少在反序列化异常路径也要保证释放。
- **误报排除**: 逐层核对了解码链（PacketCodecHandler.decode → decodeFromBuf → readObject → subInput），全链路无 close/release 调用（grep 验证 `ByteBufBinaryDataReader` 的构造点仅 ModelBasedPacketCodec:169/234，两处均未 close）；Netty slice/refCnt 语义按 4.1 标准行为核对。文件内注释"父 reader close 不影响子视图"表明设计意图是子 reader 自行 close，但反序列化器从不关闭它，泄漏成立。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。`AbstractModelBasedRecordDeserializer.readObject` 与流式路径在区域消费完毕后关闭 subInput（`StreamingStackFrame` 新增 subIn 追踪，完成/异常路径均释放）；Count/DynCount codec 的 arrayInput 同样 finally 关闭。注意 `decodeFromBuf` 顶层 reader 不能关闭（PacketCodecHandler 用 readSlice 不 retain，帧由 cumulation 统一释放）。测试：`nop-record` `TestRecordRegionBoundary#testByteBufSubInputRefCntBalanced`（修复前 refCnt 净增）。

### [P1] SubBinaryDataReader.seek/reset 把子区间相对位置当底层绝对位置，静默错位读

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/SubBinaryDataReader.java:39-45, 93-96`
- **维度**: D1 正确性（字段偏移计算/数据损坏）
- **证据**:
```java
@Override
public void seek(long newPos) throws IOException {
    if (newPos > maxLength) { throw new IOException(...); }
    underlying.seek(newPos);        // newPos 是子区间相对位置，却按底层绝对位置 seek
    position = newPos;
}
@Override
public void reset() throws IOException {
    underlying.reset();             // 底层 reset 回底层起点，而非子区间起点
    position = 0;
}
```
- **现状**: `pos()` 返回相对位置，但 `seek()` 将相对位置直接传给 `underlying.seek()`。对比文本侧 `SubTextDataReader.java:118-131` 正确维护 `startOffset`（`seek(pos + startOffset)`、`reset()` 后 `input.seek(startOffset)`），二进制侧缺失 startOffset。该类由 `StreamBinaryDataReader.subInput`、`RandomAccessFileBinaryDataReader.subInput`（`:146-148`，文件型二进制输入的默认路径，见 `ModelBasedResourceRecordIO.createBinaryInput:85-98`）、`BlockCachedBinaryDataReader.subInput`（`:386-388`）创建，底层起点几乎必然非 0。
- **风险**: 标准读取路径会触发 `seek(pos())` 回退：接口默认 `IBinaryDataReader.readBytesTerm(!consumeTerm)`（`IBinaryDataReader.java:433`）、`peekByteString/peekNextByteString`（`:584-597`，switchOnRule 前瞻匹配 `BinaryDataPeekChecker` 使用）。在 RAF/BlockCached 底层：`seek(相对值)` 使底层跳到文件绝对偏移 → **后续所有字段静默解析错误数据**；在 Stream 底层：`seek` 直接抛 UnsupportedOperationException（功能不可用）。`readObject` 对所有 `length>0` 对象构造 subInput，触发路径现实。
- **建议**: 仿照 SubTextDataReader 增加 `startOffset` 字段，构造时记录 `underlying.pos()`，`seek(p)` 映射为 `underlying.seek(startOffset + p)`；`reset()` 回 `startOffset`。
- **误报排除**: 已核对 RandomAccessFileBinaryDataReader.seek（`raf.seek(newPos)` 绝对定位，`:98-101`）与 StreamBinaryDataReader.seek（抛异常，`:108-110`）确认两种失效模式；确认 SubTextDataReader 是正确参照实现（构造器记录 `pos(input)`），排除"相对位置恰好等于绝对位置"的常态假设。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。`SubBinaryDataReader` 增加 `startOffset`，`seek(p)` 映射为 `underlying.seek(startOffset+p)`、`reset()` 回子区间起点。测试：`TestSubBinaryDataReader#testSeekRelativeSubInterval`（RAF 底层）。

### [P1] BlockCachedBinaryDataReader.duplicate() 共享底层 reader 且块位置错标，导致数据损坏

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/BlockCachedBinaryDataReader.java:418-425`（配合 `loadNextBlock:531-554`、`tryLoadMoreData:458-483`）
- **维度**: D1 正确性
- **证据**:
```java
public IBinaryDataReader duplicate() throws IOException {
    BlockCachedBinaryDataReader duplicate = new BlockCachedBinaryDataReader(
            underlyingReader, defaultBlockSize, ...);   // 共享同一个底层 reader
    duplicate.seek(currentPosition);                     // 触发 ensureDataReadTo(currentPosition)
    return duplicate;
}
// loadNextBlock: 新块 startPosition = 本实例的 underlyingPosition 字段
DataBlock block = new DataBlock(ByteBuffer.wrap(buffer, 0, bytesRead), underlyingPosition, bytesRead);
```
- **现状**: duplicate 的新实例 `underlyingPosition=0`、`maxReadPosition=0`，却共享已被原实例推进到 N 的底层 reader。`seek(currentPosition)` 触发 `ensureDataReadTo` → 从底层当前位置（N）读入数据，却把块标为 `startPosition=0`——块内容与位置标签错位。同时原实例继续读底层时，其 `underlyingPosition` 字段已与底层真实位置脱节，后续块标签同样错位。
- **风险**: 调用 `duplicate()` 后，任一副本的任何继续读取都会拿到错位数据（静默数据损坏）；`detach()`（`:391-416`）是正确做法（复制缓存块、detach 底层），`duplicate()` 未遵循同等约束。
- **建议**: `duplicate()` 底层改用 `underlyingReader.duplicate()`/`detach()`，并复制 currentPosition/maxReadPosition 状态（参照 `detach()` 的实现），或直接委托 `detach()`。
- **误报排除**: 已核对 `IBinaryDataReader.duplicate` 语义（独立副本，ByteBufferBinaryDataReader/ByteBufBinaryDataReader 均返回独立 buffer 的 reader）；核对 `tryLoadMoreData` 无任何底层位置校验/重定位逻辑，块标签仅来自实例字段 `underlyingPosition`，错标机制成立。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。`duplicate()` 直接委托 `detach()`（复制缓存块、复制 currentPosition/maxReadPosition 状态），不再与原实例共享底层 reader。

### [P1] BitmapTagBinaryCodec.encodeTags 不向输出写入 bitmap 字节，编解码字节数不对称

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/BitmapTagBinaryCodec.java:59-80`（decode 侧 `:21-57`）
- **维度**: D8 编解码往返一致性
- **证据**:
```java
@Override
public IBitSet encodeTags(IBinaryDataWriter output, Object value,
                          RecordObjectMeta typeMeta, IFieldCodecContext context) throws IOException {
    IBitSet bitSet = new FixedBitSet(128);
    ... // 仅计算 bitSet，从不调用 output.writeXxx(...)
    return bitSet;
}
// decodeTags 则从输入实际消费 8 或 16 字节：
byte[] bytes = input.readBytes(8);
if ((bytes[0] & 0b1000_0000) != 0) { ... nextBytes = input.readBytes(8); }
```
- **现状**: `decodeTags` 每条记录消费 8/16 字节 bitmap；`encodeTags` 接收 `output` 参数但从未写入任何字节，只返回用于字段筛选的 bitSet。`ModelBasedBinaryRecordSerializer.writeTags`（`:51-56`）也只把返回值用于 `isMatchTag`，没有其他代码补写 bitmap。
- **风险**: 使用 `tagsCodec="bitmap128"`（已在 `FieldCodecRegistry.DEFAULT` 注册，`FieldCodecRegistry.java:30`）的模型：编码输出比解码输入每条记录少 8/16 字节，tag 存在位信息完全丢失，decode(encode(x)) 无法往返，对端无法解析。ISO8583 类报文功能不可用。
- **建议**: `encodeTags` 按 bitSet 内容向 `output` 写出 8 或 16 字节（首位置续位），与 decode 的消费量对齐。
- **误报排除**: grep 全模块 `encodeTags/writeTags` 实现与调用点，确认 `ModelBasedTextRecordSerializer.writeTags` 同样只消费返回值，无任何路径写出 bitmap 字节；接口 `IFieldTagBinaryCodec.encodeTags(output, ...)` 的参数签名表明写 output 是其职责，排除"由上层统一写出"的设计。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。`encodeTags` 按 bitSet 写出 8/16 字节 bitmap（bit0 续位，与 decode 消费量对称）。测试：`TestBitmapTagBinaryCodec#testRoundTrip/#testEmptyValueWritesBitmap`。

### [P1] StreamingRecordDeserializer 处理 baseType 时复用共享 frame 并推进至 COMPLETED，派生类型字段全部丢失

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/serialization/StreamingRecordDeserializer.java:82-105`（后果延伸至 `:144-152`）
- **维度**: D1 正确性
- **证据**:
```java
case StreamingStackFrame.STAGE_BEFORE_READ:
    if (recordMeta.getResolvedBaseType() != null) {
        // 递归处理基类 —— 复用同一个 frame
        StreamingReadResult baseResult = processObjectStreaming(
                frame, in, recordMeta.getResolvedBaseType(), context);
        if (baseResult != null) {
            return baseResult.then(() -> {
                frame.moveToNextStage();               // frame 已是 COMPLETED(5)，再+1 变 6
                return processObjectStreaming(frame, paramIn, recordMeta, context);
            });
        }
    }
```
- **现状**: 递归调用用**同一个 frame** 从 STAGE_BEFORE_READ 一路跑到 STAGE_COMPLETED——读取的是 baseType 的字段并在结束时执行派生对象区域的残留对齐（`frame.getSubBaseIn()` 是外层 INIT 设置的派生区域参数）。返回后 continuation 里 `moveToNextStage()` 无法回到未完成状态，`processObjectStreaming(frame, 派生meta)` 的 `while (!frame.isCompleted())` 立即退出，派生类型自身字段（`recordMeta.getFields()`）一个都不读。非流式 `_readObject`（`AbstractModelBasedRecordDeserializer.java:104-114`）是先读 base 再读派生字段，流式路径与契约不一致。
- **风险**: `useStreaming=true` 且 body 类型有 `baseType`（`RecordObjectMeta.isAnyFieldSupportStreaming` 显式包含 base 的流式字段，`:108`）时：记录只剩基类字段、派生字段静默丢失，且区域残留被提前跳过导致后续记录错位——双重数据损坏。
- **建议**: 递归处理 baseType 时为 base 创建独立 frame（或在 frame 中保存/恢复 stage 与 fieldIndex 状态），确保 base 处理完后回到 STAGE_READ_FIELDS 继续读派生字段。
- **误报排除**: 逐行跟踪 frame 状态机（`StreamingStackFrame.isCompleted() = currentStage >= 5`，`moveToNextStage()` 单纯自增，无回退机制）与 `StreamingReadResult.then` 的惰性 continuation 语义（`:81-94`，action 在结果被消费后执行，此时 frame 已完成），确认派生字段循环必被跳过；对照非流式实现确认这是流式特有缺陷。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。STAGE_BEFORE_READ 为基类使用独立 frame（含 suppressEndOfObject 抑制基类的 endOfObject 条目），基类字段读完后回到派生 frame 继续 READ_TAGS/READ_FIELDS；非流式字段经 `copyNonStreamingFields` 并入外层条目。测试：`TestBaseTypeStreaming#testStreamingBaseTypeFieldsNotLost`（修复前派生字段全部丢失、双 endOfObject）。

### [P1] StreamBinaryDataWriter.writeByteBuffer 忽略 arrayOffset，direct buffer 直接崩溃

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/writer/StreamBinaryDataWriter.java:33-36`
- **维度**: D1 正确性
- **证据**:
```java
@Override
public void writeByteBuffer(ByteBuffer buf) throws IOException {
    writtenCount += buf.remaining();
    out.write(buf.array(), buf.position(), buf.remaining());  // 忽略 arrayOffset
}
```
- **现状**: `ByteBuffer.array()` 对 direct buffer 抛 UnsupportedOperationException；对 heap slice/duplicate（`arrayOffset()>0`）时，`buf.position()` 是视图内偏移，未加 `arrayOffset()` 即作为底层数组下标 → 写出错误数据。正确写法应为 `out.write(buf.array(), buf.position() + buf.arrayOffset(), buf.remaining())` 或用 `buf.get(chunk)`。
- **风险**: 该 writer 是二进制文件输出路径的标准实现（`ModelBasedResourceRecordIO.openOutput:117`）。任何经用户 codec/脚本调用 `writeByteBuffer` 传入 direct 或切片 buffer 的场景：前者崩溃，后者**静默写错字节**（编码数据损坏）。
- **建议**: 修正偏移计算并处理 `!buf.hasArray()` 分支（复制写出）。
- **误报排除**: 核对 `IBinaryDataWriter.writeByteBuffer` 为接口公开方法（模块内当前无主代码调用，属公共 API 供扩展 codec 使用），方法本身的错误不依赖特定调用点成立；与 `ByteBufBinaryDataWriter.writeByteBuffer`（`byteBuf.writeBytes(buf)`，正确）对照确认是 Stream 实现的缺陷。

---

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。`writeByteBuffer` 对 heap 视图按 `position()+arrayOffset()` 计算，direct buffer 走临时数组拷贝。测试：`TestStreamBinaryDataWriter` 三个用例（切片/非零 arrayOffset/direct）。

### [P2] ModelBasedPacketCodec 的 initialBytesToStrip 仅在 encode 生效，解码侧从不应用，strip≠0 时帧错位

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/ModelBasedPacketCodec.java:117-119, 163, 221-224`
- **维度**: D8 编解码往返一致性
- **证据**:
```java
// encode: len 从总长中扣除 initialBytesToStrip
int len = endIndex - initialBytesToStrip - lengthAdjustment;
// decode: 只跳过 lengthFieldEndOffset，从不消费 initialBytesToStrip
public Object decodeFromBuf(ByteBuf buf, Class<?> targetType) {
    buf.skipBytes(lengthFieldEndOffset);
```
- **现状**: 解码帧长公式 `raw + lengthAdjustment + lengthFieldEndOffset` 恰好比编码实际写出的总字节数少 `initialBytesToStrip`；上游 `PacketCodecHandler.decode`（nop-netty）也不做 strip。属性在 `_PacketCodecModel` 中默认 0 且无校验约束，模型可配置任意值。
- **风险**: 任何配置 `initialBytesToStrip > 0` 的报文模型：解码端每帧少消费 strip 字节，残留字节被当作下一帧头 → 从第二个包起全部错帧/丢弃，属静默数据损坏；同时该配置项与 Netty LengthFieldBasedFrameDecoder 的同名语义不一致，易误配。
- **建议**: 要么在 decode 侧实现 strip（`determinePacketLength` 帧长公式与 `decodeFromBuf` 同步调整），要么模型加载时校验禁止非 0 值并文档化。
- **误报排除**: grep 全模块 `initialBytesToStrip` 使用点，确认解码链（determinePacketLength/decodeFromBuf/PacketCodecHandler）均未应用；对照 Netty LengthFieldBasedFrameDecoder 语义确认帧长公式推导正确。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试（含比审计更深的问题）。编码公式改为 `len = endIndex - lengthFieldEndOffset - lengthAdjustment`，与解码器 Netty 语义 `frame = raw + adj + H` 对称——审计只指出 strip 不对称，实际上原公式在默认配置（strip=0）下就与解码恒差 H 字节（仅 strip==H 时碰巧一致，netty 集成测试 @Disabled 掩盖）；`initialBytesToStrip` 非 0 时构造即抛 `ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED`（仓库内无任何配置使用非 0 值）。测试：`TestModelBasedPacketCodec#testFrameLengthSymmetry/#testInitialBytesToStripRejected`。

### [P2] readCollection 的 repeatUntil/fixed 分支在元素零消耗且返回 null 时死循环

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/serialization/AbstractModelBasedRecordDeserializer.java:230-261`
- **维度**: D1/D5（挂起、拒绝服务）
- **证据**:
```java
while (!checkUntil(repeatUntil, in, record, context)) {   // until 分支：无 EOF 检查
    Object value = readSwitch(in, field, coll, context);
    if (value != null)
        coll.add(value);
    if (coll.size() >= field.getMaxCollectionSize())       // 仅在 add 后才可能增长
        throw new IllegalStateException(...);
}
// fixed 分支同理：} while (subInput != in && !subInput.isEof());
```
- **现状**: 当元素读取零字节且返回 null（例如 `readSwitch` 中 typeMeta 的 `readWhen` 求值为 false 直接 `return null`，`:303-306`）且循环条件（repeatUntil 恒 false / subInput 恒不 EOF）不变化时，`coll.size()` 永远为 0，上限守卫永不触发，`isEof()` 永不变化 → 无限循环。
- **风险**: 一份配置有问题的模型文件（或攻击者可控的 until 条件/输入组合）即可让解析线程 100% CPU 空转挂死，无内存增长、无异常，难以定位。流式版 `processCollectionStreaming`（`StreamingRecordDeserializer.java:329-362`）因 `incrementCollectionIndex()` 无条件执行而无此问题，行为不一致。
- **建议**: 每轮迭代记录读取前 `in.pos()`，若位置未前进且未新增元素则抛错退出；或 until/fixed 分支也用迭代计数（而非 coll.size）做上限。
- **误报排除**: 核对 `readSwitch` 返回 null 的零消耗路径（typeMeta.readWhen=false、readObject 返回 false）真实存在；核对循环体内无其他终止条件（fixed 分支依赖 subInput.isEof()，零消耗时恒 false）。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。until/fixed 分支每轮记录读取前位置，元素零消耗且未新增（readWhen=false 等）时抛 `ERR_RECORD_COLLECTION_NO_PROGRESS`，不再死循环；集合超限异常改为带错误码的 NopException。

### [P2] Count/DynCountArray 二进制 codec 用线上 count 直接预分配集合，恶意长度字段可致 OOM

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/CountArrayBinaryCodec.java:31-39`；`nop-format/nop-record/src/main/java/io/nop/record/codec/impl/DynCountArrayBinaryCodec.java:44-53`
- **维度**: D5 安全（不可信输入）
- **证据**:
```java
int count = (Integer) countCodec.decode(input, record, length, context, deserializer);
IBinaryDataReader arrayInput = input.subInput(length);
List<Object> ret = new ArrayList<>(count);      // count 来自报文，无上限校验
for (int i = 0; i < count; i++) { ... }
```
- **现状**: `count` 直接来自线上字节（u4 可达 0x7FFFFFFF），`new ArrayList<>(count)` 在任何数据可得性检查之前立即按 count 预分配底层数组 → 单个报文即可触发约 2^31 * 4B ≈ 8GB 申请，直接 OutOfMemoryError。对比 `readCollection`（非 codec 路径）有 `getMaxCollectionSize()`（默认 100 万）守卫，codec 路径完全没有。
- **风险**: 解析不可信二进制输入（netty 报文/上传文件）时单包 DoS。
- **建议**: 在预分配前用 `field.getMaxCollectionSize()` 或 `input.available()/itemLength` 收敛 count（`Math.min(count, 可用字节数/itemLength)`），超限抛带 ErrorCode 的 NopException。
- **误报排除**: 核对 ArrayList 构造语义（立即分配 `Object[count]`）；核对 `readBytes(int n)` 类似模式仅当 n 来自模型配置（可信）而此处 count 来自线上数据，风险等级不同。DynLVFieldBinaryCodec 的 `readBytesStrict`（`:61-75`）在 `length<=0`（未配置 maxLength）时同样可被恶意 len 触发大分配，属同族问题，修复合并处理。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。`CountArrayBinaryCodec.checkCount`：count 超过 `DEFAULT_MAX_COLLECTION_SIZE`(100万) 或为负抛 `ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG`；预分配上限 1024。`DynLVFieldBinaryCodec.readBytesStrict` 在未配置 maxLength 时增加 64MB 防御上限。

### [P2] BinaryReaderHelper.processZlib：截断输入死循环 + 无解压上限 + 裸 RuntimeException

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/BinaryReaderHelper.java:129-144`
- **维度**: D4 错误处理 / D5 安全
- **证据**:
```java
Inflater ifl = new Inflater();
ifl.setInput(data);
ByteArrayOutputStream baos = new ByteArrayOutputStream();
byte buf[] = new byte[ZLIB_BUF_SIZE];
while (!ifl.finished()) {
    try {
        int decBytes = ifl.inflate(buf);   // 输入耗尽时返回 0，finished() 恒 false
        baos.write(buf, 0, decBytes);
    } catch (DataFormatException e) {
        throw new RuntimeException(e);     // 裸 RuntimeException，违背两档错误策略
    }
}
```
- **现状**: (1) zlib 流被截断时 `needsInput()==true`、`inflate()` 恒返回 0、`finished()` 恒 false → 忙等死循环；(2) 解压输出无任何大小上限 → 压缩炸弹可将小输入膨胀到任意大小（OOM）；(3) `RuntimeException(e)` 无错误码、无上下文参数，违背平台"禁止 bare RuntimeException"约定。Kaitai 移植代码，当前模块内无调用方（公共工具方法）。
- **风险**: 一旦被模型/用户代码用于解压不可信数据，存在挂死与 OOM 两种 DoS 形态。
- **建议**: 循环内处理 `inflate()==0 && ifl.needsInput()`（抛 NopException 带 cause）；增加最大输出长度参数；`NopException.adapt(e)` 替换裸异常。
- **误报排除**: 核对 JDK Inflater 语义（输入耗尽且未 finished 时 inflate 返回 0 且不抛异常）；grep 确认模块内无调用方已如实写入现状。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。输入耗尽且未 finished（inflate 返回 0 且 needsInput）抛 `ERR_RECORD_ZLIB_DECODE_FAIL`；解压输出上限 256MB；DataFormatException 以 NopException 包装（保留 cause）；Inflater 在 finally 中 end()。

### [P2] PacketStateMachineHandler.write 丢弃消息时不完成 ChannelPromise，write future 永不结束

- **文件**: `nop-format/nop-record-netty/src/main/java/io/nop/netty/ext/handlers/PacketStateMachineHandler.java:53-71`
- **维度**: D2/D4 资源与契约
- **证据**:
```java
@Override
public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
    ...
    if (process.shouldDrop()) {
        ReferenceCountUtil.release(msg);
        return;                  // promise 既未 setSuccess 也未 setFailure
    }
    ctx.write(process.getMsg(), promise);
}
```
- **现状**: drop 分支释放消息后直接返回，`promise` 永远不会被完成。Netty 契约要求吞掉写操作的 handler 自行完成 promise。
- **风险**: 任何调用方 `writeAndFlush(...).sync()/await()/addListener(...)` 在消息被状态机 drop 时永久挂起或收不到回调；累积未完成的 promise 还会滞留监听器引用。
- **建议**: drop 分支补 `promise.trySuccess()`（或 `promise.setFailure(...)` 表达丢弃语义）。
- **误报排除**: 确认 Netty 4.1 中 handler 正常返回（不抛异常）时框架不会代为完成 promise；channelRead 侧无此问题（inbound 无 promise）。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。drop 分支补 `promise.trySuccess()`，满足 Netty 吞写操作必须自行完成 promise 的契约。

### [P2] TransferServerProxy/ProxyHandler 丢弃转发响应，与类注释宣称的中转回程不符

- **文件**: `nop-format/nop-record-netty/src/main/java/io/nop/netty/ext/handlers/ProxyHandler.java:17-19`（设计来源 `TransferServerProxy.java:21-22, 67-71`）
- **维度**: D8 API/契约一致性
- **证据**:
```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    server.sendToAnyChannel(msg, rpcTimeout);   // 返回 CompletableFuture<Object> 被丢弃
}
```
- **现状**: `NettyTcpServer.sendToAnyChannel(msg, timeout)`（nop-netty，已核对实现）把 msg 作为 RPC 请求发往 serverB 的某连接并返回携带 B 端响应的 future。ProxyHandler 丢弃该 future，也没有任何代码把响应写回 serverA 侧的 `ctx`。类注释明确宣称"当serverB返回消息时，再返回给连接serverA的客户端"，实际未实现回程。
- **风险**: A 侧请求方永远收不到响应（超时）；TransferServerProxy 的中转功能只完成了单向。
- **建议**: `sendToAnyChannel(...).whenComplete((resp, err) -> ctx.writeAndFlush(err != null ? 错误消息 : resp))`。
- **误报排除**: 核对 serverB 侧管线（`TransferServerProxy.initServerBChannel` 有 RpcMessageHandler 管理 pending future），确认响应只通过该 future 传递，不存在其他自动回写路径。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。`ProxyHandler.channelRead` 将 `sendToAnyChannel` 返回的响应 future 写回当前 channel（`ctx.writeAndFlush(resp)`），失败路径记日志，实现类注释宣称的中转回程。

### [P2] 定长字段编码超长静默截断、多字节字符集按字节截半、null 默认值 text/binary 不一致

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/AbstractFixedLengthAsciiCodec.java:59-80, 96-102`
- **维度**: D1/D8 编解码不对称（数据损坏）
- **证据**:
```java
// text 编码：null 默认 "0"
String text = ConvertHelper.toString(value, "0");
text = padString(text, length);      // forceLeftPad/rightPad: 超长时 str.substring(0, len) 静默截断
// binary 编码：null 默认 ""
String text = ConvertHelper.toString(value, "");
byte[] bytes = padBytes(text.getBytes(charset), length);  // ByteHelper.forceXxxPad: copyOf 截断
```
- **现状**: (1) 值超过字段定长时 `StringHelper.forceLeftPad/forceRightPad`（substring）与 `ByteHelper.forceLeftPad/forceRightPad`（copyOf，已核对 nop-commons 实现）都静默截断，不抛错——银行定长报文中金额/账号超长属应当报错的数据错误，这里变成无告警的数据损坏，且 decode(encode(x)) != x；(2) 非 ASCII charset 下按字节截断可能把多字节字符切半，解码出替换字符；(3) null 值：text+FLS codec 写 "0000"，binary 写补位符，无 codec 的 text 路径（`ModelBasedTextRecordSerializer.writeField0:45` 用 `""`）又写补位符——三种默认不一致。
- **风险**: 往返不保真；null 字段在文本模式被编码为 "0" 再解码成 0/""，值漂移；超长数据静默丢失。
- **建议**: 超长抛 `ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE`（RecordMetaHelper 已有该错误码）；null 默认值统一（建议按字段类型：数值 "0"、字符串补位符），多字节 charset 在字符域判断长度。
- **误报排除**: 已读 nop-commons 的 force*Pad 实现确认截断行为；三处默认值来源均已定位（含 `ModelBasedTextRecordSerializer:45`）；"force 前缀=有意截断"不能解释 null 默认值的不一致，作为整体契约风险报告。

> **处置（fix-ai-check 分支，2026-08-21）**: 部分修复，其余暂缓。null 默认值不一致与静默截断属编解码契约设计（超长报错会改变既有银行报文兼容行为，需要按字段类型的破坏性策略决策），暂缓；多字节字符集按字节截半随截断策略一并处理。已在报告中确认风险。

### [P2] 模块内错误处理违例：裸 IllegalStateException/IllegalArgumentException + 数组/LV codec encode 传 null serializer

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/serialization/AbstractModelBasedRecordDeserializer.java:238, 253, 265, 293`；`nop-format/nop-record/src/main/java/io/nop/record/serialization/StreamingRecordDeserializer.java:342-343`；`nop-format/nop-record/src/main/java/io/nop/record/codec/impl/DynLVFieldBinaryCodec.java:81, 84`；`nop-format/nop-record/src/main/java/io/nop/record/codec/impl/CountArrayBinaryCodec.java:50, 52`；`nop-format/nop-record/src/main/java/io/nop/record/codec/impl/DynCountArrayBinaryCodec.java:66, 68`
- **维度**: D4 错误处理 / D7 平台规范
- **证据**:
```java
// AbstractModelBasedRecordDeserializer
throw new IllegalStateException("collection size exceed limit:field=" + field.getName() + ...);
throw new IllegalArgumentException("Repeat count field not found:" + field.getName());
// DynLVFieldBinaryCodec.encode / CountArrayBinaryCodec.encode
lengthCodec.encode(output, len, length, context, null);      // serializer 参数传 null
```
- **现状**: (1) 违背平台错误处理两档策略（nop-record 属框架公共编解码引擎，应使用 `NopException` + `RecordErrors` 错误码 + `.param(...)`；RecordErrors 中已有同类码可复用/扩展）；这些裸异常丢失字段路径、模型位置等上下文。(2) `LVFieldBinaryCodec.encode`（`:50`）正确传入 `serializer`，而 DynLV/Count/DynCountArray 三个 codec 的 encode 把 `serializer` 参数传 `null`——任何自定义 length/item codec 在 encode 中使用该参数（如回调 `IModelBasedBinaryRecordSerializer` 方法）即 NPE。同族接口行为不一致。
- **风险**: 接错模型时报错信息无法定位（裸 JDK 异常）；自定义 codec 组合在 encode 时 NPE。
- **建议**: 替换为带错误码的 NopException；encode 调用统一透传 `serializer`。
- **误报排除**: 逐一打开引用行确认字符串内容与 null 实参；确认 `IFieldBinaryCodec.encode` 的 serializer 参数在 `ModelBasedPacketCodec.writeUnadjustedFrameLength`（传 serializer）与上述三处（传 null）用法矛盾，非接口契约允许的"可空"（接口无 @Nullable 注解且无文档）。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。裸 `IllegalStateException`/`IllegalArgumentException` 替换为 `NopException` + RecordErrors 新增错误码（ERR_RECORD_COLLECTION_SIZE_EXCEED_LIMIT / ERR_RECORD_COLLECTION_NO_PROGRESS / ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED）；DynLV/Count/DynCount 三个 codec 的 encode 统一透传 serializer。

### [P2] AppendableTextDataWriter.append(char[],start,end) 非 StringBuilder 分支双重应用区间，越界或写错字符

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/writer/AppendableTextDataWriter.java:57-65`
- **维度**: D1 正确性
- **证据**:
```java
public ITextDataWriter append(char[] chars, int start, int end) throws IOException {
    if (buf instanceof StringBuilder) {
        ((StringBuilder) buf).append(chars, start, end);       // 正确
    } else {
        buf.append(new MutableString(chars, start, end), start, end);  // 区间应用两次
    }
    length += end - start;
}
```
- **现状**: 已核对 nop-commons `MutableString(char[] buf, int start, int limit)` 是表示 `chars[start..end)` 的 CharSequence（长度 end-start）。随后 `buf.append(view, start, end)` 再取视图的 `[start,end)` 子序列：start>0 时 end 超出视图长度 → IndexOutOfBoundsException；某些恰好不越界的组合则写出错误字符。正确应为 `buf.append(new MutableString(chars, start, end))`。
- **风险**: 模块内当前无调用方（`append(char[])` 全量版本因 start=0 碰巧正确），但这是 `ITextDataWriter` 公开 API，用户 codec/脚本以 `append(chars, start, end)` 写部分数组到 Writer 型目标（如 `ModelBasedResourceRecordIO.openOutput` 包装的 BufferedWriter）时崩溃或产出错数据。
- **建议**: 去掉第二个 `start, end` 实参。
- **误报排除**: 读取 MutableString 构造器与字段语义确认视图长度；举例验证 append(chars,2,5) 必然越界。

---

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复并附回归测试。非 StringBuilder 分支去掉对 MutableString 视图的第二次区间应用；StringBuilder 分支修正为 `append(chars, start, end - start)`（第三参是长度）。测试：`TestAppendableTextDataWriter`。

### [P3] BlockCachedTextDataReader.readLine 在 EOF 时返回 null，与其他 reader 返回 "" 不一致

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/BlockCachedTextDataReader.java:360`
- **维度**: D8 API/契约一致性
- **证据**:
```java
currentPosition = pos;
return result.length() > 0 ? result.toString() : null;   // EOF 且无内容 → null
```
- **现状**: `SimpleTextDataReader.readLine:90` 与 `ReaderTextDataReader.readLine:231` 在同条件下返回 `""`。`ITextDataReader.readLine` 接口未声明 null 语义，消费方（分隔符文本模型按行读取）对 null/"" 的处理路径不同（如 `field.getContent()` 比较、trim 等）。
- **风险**: 同一模型在大文件（走 BlockCachedTextDataReader）与小文件（走 SimpleTextDataReader）下行为分叉，空行处理不一致。
- **建议**: 统一返回 ""，或在接口 javadoc 固化契约。
- **误报排除**: 三处实现均已打开核对返回语句。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复（方向与审计建议相反）。统一为 **EOF 返回 null**：把 Simple/Reader 两个 reader 改为 null 而非把 BlockCached 改为 ""——现有消费方以 `while ((line=readLine(...)) != null)` 判 EOF（TestBlockCachedTextDataReader2#testLargeFileSimulation 即此约定，首次尝试按审计方向统一为 "" 时该测试死循环），"统一为空串"会在所有 null 判EOF的调用点引入死循环。接口 javadoc 已固化 null 语义。

### [P3] EOLFieldCodec 解码接受 CRLF/LF 但编码恒写 LF，CRLF 输入往返后字节不保真

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/impl/EOLFieldCodec.java:40-64`
- **维度**: D8 编解码往返一致性
- **证据**:
```java
if (t == '\r') {
    t = input.read();          // 吞掉 \r 后的 \n
}
if (t != '\n') throw ...;
return '\n';
// encode 恒写：
output.writeByte((byte) '\n');
```
- **现状**: 解码兼容 `"\r\n"` 与 `"\n"`（并归一为 `'\n'`），编码只输出 `'\n'`。
- **风险**: 读 CRLF 文件再写回，文件行尾被静默改写为 LF；对行尾敏感的对端/审计场景构成字节级漂移（属规范化设计但未文档化，无开关）。
- **建议**: 文档化该规范化行为，或提供按输入保真的行尾选项。
- **误报排除**: 已核对编解码两侧实现，确认无行尾记忆/配置。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复（文档化）。EOLFieldCodec 类 javadoc 明确"解码兼容 CRLF/LF 归一为 LF，编码恒写 LF"的规范化行为。

### [P3] TableFlowFunctions.facetNumeric 桶边界排除最大值且浮点步进漂移

- **文件**: `nop-format/nop-tablesaw/src/main/java/io/nop/tablesaw/dataflow/TableFlowFunctions.java:42-58`
- **维度**: D1 正确性（轻微）
- **证据**:
```java
for (double start = min; start < max; start += step) {
    double end = Math.min(start + step, max);
    ...
    if (v >= rangeStart && v < rangeEnd) count++;   // v == max 永远不满足 v < rangeEnd(=max)
```
- **现状**: 最后一桶 `rangeEnd = max`，条件 `v < rangeEnd` 使等于最大值的样本不落入任何桶（计数丢失）；`start += step` 的浮点累放在长区间上造成桶宽漂移。另 `split()`（`:224`）中 `int colIndex = copy.columnIndex(column);` 为无用赋值，疑似漏写删除原列。
- **风险**: 数据剖析报表的桶计数轻微失真；维护性噪音。
- **建议**: 最后一桶用 `v <= rangeEnd`；步进用整数索引乘法；清理无用变量。
- **误报排除**: 边界条件以 v==max 单值核对；无用赋值经行内确认未在方法内使用。

> **处置（fix-ai-check 分支，2026-08-21）**: 已修复。桶边界改用整数索引乘法（消除浮点累进漂移），最后一个桶闭区间包含最大值。

### [P3] RecordAggregateState.checkPageChanged 的 pageSize 边界疑似 off-by-one（意图存疑）

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/resource/RecordAggregateState.java:114-117`
- **维度**: D1 正确性（低置信）
- **证据**:
```java
if (pageSize > 0 && indexInPage >= this.pageSize - 1) {
    return true;
}
```
- **现状**: `checkPageChanged` 在写入下一条记录**之前**触发翻页。按 `AbstractModelBasedRecordOutput.beforeWriteRecord` 的调用顺序（check → onWriteRecord 计数自增 → isPageBegin 写页头）推演：pageSize=N 时每页实际容纳 N-1 条记录；pageSize=1 时第一条记录即触发 newPage 且 pageIndex 从 1 跳到 2。
- **风险**: 若 pageSize 语义是"每页记录数"，则页脚聚合/页数统计全部错位。但也存在"pageSize 含页头/页脚行"的报文设计意图，故降级为 P3 待确认。
- **建议**: 对照模型文档/测试确认 pageSize 语义；若为记录数应改为 `indexInPage >= pageSize`（或写后检查）。
- **误报排除**: 推演过程完整（含 isPageBegin 时序）；因设计意图不确定而明确降级，宁缺毋滥原则下保留为低置信提示。

---

## 检查方法与整体印象

- 三个模块整体架构清晰（模型驱动 + codec 注册表 + reader/writer 抽象），错误码体系（RecordErrors）在主路径使用较规范，未发现空 catch、异常吞噬、SimpleDateFormat、synchronized 误用。
- 主要风险集中在：(1) netty ByteBuf 所有权约定（subInput retain 无对应 release）；(2) 子区间 reader 的位置语义（SubBinaryDataReader 与文本侧实现不一致）；(3) 流式反序列化状态机对 baseType 的处理；(4) 编解码字节数对称性（bitmap tag、initialBytesToStrip）；(5) tablesaw 数据搬运的 null 判断颠倒。

> **处置（fix-ai-check 分支，2026-08-21）**: 复查后维持现状。审计自评"意图存疑"：pageSize 可能按"含页头/页脚行"的报文设计，无测试/文档钉住语义；无证据表明当前行为错误，盲目改为 `>= pageSize` 有破坏既有分页报文的风险。待模型文档确认后再动。
