# 对抗性审查：plan 203（nop-record record file 缺陷修复，tests-first）

- Date: 2026-08-16 14:47
- Reviewer: 独立审查 agent（read-only，零文件改动）
- Target: `ai-dev/plans/203-nop-record-record-file-defect-fixes-tests-first.md`（draft）
- Baseline: `ai-dev/analysis/2026-08/2026-08-16-record-file-parsing-generation-design-analysis.md`
- 方法: 想象性执行（逐 Phase 推演红→绿）+ live repo 逐行核对（所有 file:line 引用全部核实）
- Verdict: **FAIL（不能直接执行）**——2 Blocker + 6 Major + 8 Minor，全部落盘修复后需 fresh-session R2 复审

## Summary

Plan 的结构、格式、绝大部分引用与行为裁定质量高（缺陷描述与修复方向经代码验证成立）。不可执行的原因集中在两处：

1. **Phase 5 区域对齐修复公式对"急切 subInput"reader 错误**（P0-1/P0-2）：`remaining = length - subIn.pos()` 在急切 reader（ByteBuffer/ByteBuf，父 position 已立即前进）下双重跳过，且红测试用急切 reader 不会红（缺陷只存在于惰性 reader）。
2. **readWhen 接线与裁定不完整**（P1-1/P1-2/P1-3）：流式路径完全绕过 readField/readObject（静默 no-op）、集合内 item readWhen=false 会提前终止集合、header/trailer 级 readWhen 会静默错位——这三处正是 guide Rule #23 与 No Silent No-Op 要防的形态。

## Findings

### P0（Blocker）

#### P0-1: Phase 5 readObject 区域对齐公式对急切 reader 双重跳过 + 红测试无法红

- 位置: Phase 5「实现 readObject 对齐」条目（plan L170）
- 问题: plan 公式 `remaining = length - (int) subIn.pos()` 假设 subInput 惰性消费。live 核实：`ByteBufferBinaryDataReader.subInput`（:421-426）与 `ByteBufBinaryDataReader.subInput`（:384-387）均为**急切**（父 position 立即 +n）；`StreamBinaryDataReader`/`RandomAccessFileBinaryDataReader`/`BlockCachedBinaryDataReader` 经 `SubBinaryDataReader` 为**惰性**。急切 reader 下父已在 `start+length`，再 skip(n-consumed) → 越界跳过。
- 后果: ① 红测试用 ByteBuffer（现有测试惯例）→ 缺陷今天不显现，测试直接绿，tests-first 纪律失效；② 按 plan 公式实现后急切路径被引入新错位回归；③ 若执行者恰好用惰性 reader 写测试，绿测试通过但急切路径（netty ByteBuf、内存资源）被静默破坏——比原缺陷更隐蔽。
- 修复方向: 公式改 `remaining = (start + length) - baseIn.pos()`（start 为 subInput 创建前、beforeRead 之后捕获的父位置；急切 reader 下自动为 0）；红测试显式指定惰性 reader（`StreamBinaryDataReader`）。

#### P0-2: Phase 5 fixed 集合分支"循环后跳过剩余字节"同源错误

- 位置: Phase 5「实现 readCollection fixed 分支」条目（plan L172）
- 问题: `while (!subInput.isEof())` 改子视图判定对两类 reader 均正确（SubBinaryDataReader.isEof=pos>=maxLength；ByteBuffer 子视图=limit 耗尽），但"循环后跳过区域剩余字节"在急切 reader 下同样是双重跳过。
- 后果: 同 P0-1。
- 修复方向: 与 P0-1 共用同一公式（start 为 subInput 创建前捕获）。

### P1（Major）

#### P1-1: Phase 3 readWhen 接线未覆盖流式路径（静默 no-op）

- 位置: Phase 3 Targets/实现条目（plan L116,L122,L124）
- 问题: live 核实 `StreamingRecordDeserializer.processFieldStreaming`（:214 起）**不调用 deserializer.readField**；`processObjectStreaming.STAGE_INIT`（:52-71）**不调用 readObject**、不求值 recordMeta.getReadWhen()。plan 的测试全用非流式模型，流式路径 readWhen 仍不生效。
- 后果: 违反 plan 自身 Goals（不引入静默 no-op）与 guide Rule #23；正是历史 MA5.2 类缺陷的形态。
- 修复方向: `processFieldStreaming` 顶部求值字段级 readWhen（false → return 不消费）；`processObjectStreaming` STAGE_INIT 求值对象级 readWhen（false → 返回 null）；与 readField0/readObject 共用求值 helper；Exit Criteria 补流式验证用例。

#### P1-2: 集合内 item readWhen=false 语义未裁定（fixed 分支提前终止集合）

- 位置: Phase 3 readObject/readSwitch 裁定（plan L124）
- 问题: plan 裁定 readSwitch 在 readObject 返回 false 时返回 null，但 `readCollection` fixed 分支（:222-225）`if (value == null) break;` → 一个 readWhen=false 的 item 提前终止整个集合，后续 items 丢失；count/expr 分支（:236-239）`coll.add(value)` → 集合出现 null 项。
- 修复方向: 裁定 fixed 分支遇 null → `continue`；count/expr 分支 null 不加入集合；补两个集合内 readWhen 红测试用例。

#### P1-3: header/trailer 级 readWhen 未裁定（静默错位）

- 位置: Phase 3 body 顶级 fail-fast 裁定（plan L125）
- 问题: `AbstractModelBasedRecordInput.readHeader`（:86-89）/readTrailer 调用 readObject 但**忽略返回值**。body 级 fail-fast 只覆盖 body；header 类型带 readWhen=false → readObject 返回 false 不消费 → body 从 header 位置开始解析 → 静默错位。
- 修复方向: 裁定 header/trailer 级 readWhen 支持（readObject false → 视为该段不存在，headerMeta 保持空、流继续）；readHeader/readTrailer 处理返回值；补红测试。

#### P1-4: DynLV valueCodec==null decode 静默截断（跨 Phase 缺口）

- 位置: Phase 2「实现上述 encode/decode」条目（plan L103）
- 问题: DynLV 新实现用 `input.readBytes(len)` 读值；`IBinaryDataReader.readBytes` 默认实现（:382-390）EOF 静默返回短数组。Phase 5 的严格校验只落在 decodeString 与 AbstractFixedLengthAsciiCodec，不覆盖 DynLV——按 plan 字面执行，EOF 静默截断在 DynLV 路径上两个 Phase 都修不掉。
- 修复方向: Phase 2 直接要求显式长度校验（循环读取 + `n == len` 校验，否则抛 ERR_RECORD_NO_ENOUGH_DATA）。

#### P1-5: Phase 5 EOF 红测试依赖 reader 类型（红因与描述不符）

- 位置: Phase 5「二进制 EOF 截断」红测试（plan L175）
- 问题: live 核实 ByteBufferBinaryDataReader 的 readBytes 抛 BufferUnderflowException（非静默）；带对象级 length 时 subInput（:417-419）直接抛 IllegalArgumentException（Phase 5 修复点够不到）。只有惰性 reader + 字段级定长才呈现"静默短读"。
- 修复方向: 红测试指定 StreamBinaryDataReader + 字段级定长（对象无 length）；ByteBuffer 的 BufferUnderflowException 统一为同一错误码作为第二验证点。

#### P1-6: Phase 5 回归测试命名错误（尾部换行路径不在 TestModelBasedRecordInput）

- 位置: Phase 5 Exit Criteria（plan L181）
- 问题: `TestModelBasedRecordInput` 用内联 30 字符字符串、**无尾部换行**；真正的 eos 尾部残留换行→trailer 路径在 **TestRecordTypeMatch**（test.txt 以 `\n` 结尾）。执行者会去"保护"不存在的路径，真正受影响的测试未被点名。
- 修复方向: Exit Criteria 点名 TestRecordTypeMatch（补 TestModelBasedRecordInput）；注意 xlsx 模型若文本模式带 length>0 会与 skip 修复交互，绿测试覆盖该路径。

### P2（Minor）

#### P2-1: 测试类计数错误
- 位置: Current Baseline「其他已核实事实」（plan L37）
- 问题: 声称"9 个测试类"；live 核实实际 **12 个**（TestBlockCached* 为 4 个非 2 个；plan 自身列举算出来也是 10 个）。
- 修复方向: 改为 12 个（TestBlockCached*×4）。

#### P2-2: 缺陷 #10 行号内部不一致
- 位置: Baseline 表 #10（plan L26）vs Phase 5（plan L172）
- 问题: 表内 `219-230`，Phase 5 写 `216-230`（后者正确，fixed 分支 216 行开始）。
- 修复方向: 统一为 216-230。

#### P2-3: Output 侧 body NPE 位置描述不准
- 位置: Phase 5「body 缺失」红测试（plan L173）
- 问题: `AbstractModelBasedRecordOutput.java:73` 在 **beginWrite()** 而非构造；红测试写"openOutput 断言抛"会误导（openOutput 不抛）。
- 修复方向: 红测试断言 openOutput 不抛、beginWrite 抛。

#### P2-4: Phase 1 红测试的 field 构造机制未指定
- 位置: Phase 1（plan L78）
- 问题: `<schema minLength="3"/>` 的 RecordSimpleFieldMeta 如何构造未写；`safeGetMinLen` 存在（RecordSimpleFieldMeta.java:96），测试可写但执行者需自行发明装配方式。
- 修复方向: 指定编程构造或 DslModelParser 解析内联最小模型。

#### P2-5: Phase 3 readWhen 测试"引用 header 变量"机制未指定
- 位置: Phase 3 红测试（plan L121）
- 问题: live 核实输入侧从不把 header 字段写入 eval scope（无 getVarName/setValue 调用；header 仅存 headerMeta 字段，readRepeatCount 用 BeanTool.getComplexProperty 访问）。readWhen 表达式 `(input,record,ctx)` 无法按名引用 header 变量。写侧已有同款管线（AbstractModelBasedRecordOutput.java:63,90 setLocalValues）——读侧缺失本身就是读写不对称的一部分。
- 修复方向: 测试用例②引用 header 变量要求补输入侧管线：readHeader/readTrailer 解析后 `context.getEvalScope().setLocalValues(headerMeta/trailerMeta)`（与写侧对称）；用例①可用同一记录内前置字段（record 参数）无需管线。

#### P2-6: Phase 4 fail-fast 入口"或"字含糊
- 位置: Phase 4 fail-fast 实现条目（plan L151）
- 问题: `RecordFileMeta.init`/`RecordObjectMeta.init` 或 `RecordMetaHelper 校验入口`——属性在字段上，init 校验需遍历字段，入口选择影响"加载时抛 vs IO 打开时抛"的测试写法。
- 修复方向: 定死 `RecordFileMeta.init`（遍历 header/body/trailer 类型下所有字段，嵌套 typeRef 递归）。

#### P2-7: DynLV String→UTF-8 硬编码限制未标注
- 位置: Phase 2（plan L102）
- 问题: codec 无 charset 字段，String 按 UTF-8 编码与模型 charset 可能不一致。
- 修复方向: javadoc 注明已知限制。

#### P2-8: transformOut 与 content/codec 分支交互未裁定
- 位置: Phase 3（plan L129）
- 问题: `field.getContent()` 非空时 writeField0 直接写 content（binary:37-39），transformOut 是否应跳过 content 字段未定义。
- 修复方向: 裁定 content 字段不适用 transformOut/parseExpr（content 原样写出）。

## 引用准确性核查

Plan 引用的 30+ 处 file:line 全部 live 核实，除 P2-1/P2-2/P2-3 外均准确（含 xdef 行号、readers、codecs、serializer/deserializer、packet codec、错误码参数名等）。关键行为事实（subInput 急切/惰性语义、readBytes 默认短读 vs ByteBuffer 抛错、readFully 抛 ERR_RECORD_NO_ENOUGH_DATA、streaming 路径绕过 readField、writeSide setLocalValues 同款、RecordSerializerCodeGenerator 零引用）全部经源码确认。

## R2 复审（2026-08-16，fresh session）

- Reviewer: 独立审查 agent（read-only，零文件改动）
- 方法: 逐条核对 R1 修复落盘 + 想象性执行（Phase 3/5 新条目）+ live repo 全量引用复核（30+ 处 file:line 全部重验）
- Verdict: **FAIL→已修复**——R1 的 2 Blocker + 6 Major + 8 Minor 全部落实且表述准确、无回退；新发现 1 Major + 6 Minor，按审查指定方向全部落盘（见 plan 203 Draft Review 元数据与对应条目）。

### R1 修复落实核对（全部通过）

P0-1 公式改 `remaining = (start+length) - baseIn.pos()`（L179，惰性 reader 下 = length−已消费、急切 reader 下恒 0 无双跳；start 捕获点与 pos :56 为两个变量）+ 红测试指定 `StreamBinaryDataReader`（惰性，SubBinaryDataReader :251-253）✓；P0-2 fixed 集合共用公式（L181，fixed 循环本会读到 subInput EOF，remaining 恒 0，skip 无害）✓；P1-1 流式接线（processFieldStreaming :214 / processObjectStreaming STAGE_INIT :52-71 均不调用 readField/readObject 属实，Exit Criteria 补 Rule #23 验证）✓；P1-2 集合语义（fixed→continue、count/expr→null 不加入，红测试两条）✓；P1-3 header/trailer 裁定（readHeader :86-89 / readTrailer :237-251 确实忽略返回值）✓；P1-4 DynLV 显式长度校验（readBytes 默认实现 :382-390 确实静默短读）✓；P1-5 EOF 红测试（ByteBuffer readBytes :368-372 抛 BufferUnderflowException、subInput :417-419 抛 IllegalArgumentException）✓；P1-6 Exit Criteria 点名 TestRecordTypeMatch（test.txt 以 `\n` 结尾，xxd 复核）✓；P2-1~P2-8 全部落实 ✓。

### 新发现（已落盘）

- **R2-1 【Major】** 流式对象区域残留缺口：`StreamingRecordDeserializer` STAGE_INIT 对 length>0 对象创建 subInput（:58-68），帧完成处（newEndOfObjectResult :135）无残留跳过——缺陷 #10 同形在流式路径保留，且被 Phase 3 的 P1-1 修复放大（readWhen 跳过 + length 区域组合必然错位）；plan 的"#10 全部修复"声明不成立 → Phase 5 已补"流式对象区域残留对齐"条目 + 红测试 + Exit Criteria。
- R2-2 【Minor】集合 repeatUntil 分支（:208-215）无条件 `coll.add(value)` → 裁定补"null 不加入"。
- R2-3 【Minor】processFieldStreaming 是可重入状态机（then() 回调 :245-252），"顶部求值"会在每 stage 重入重复求值 → 改定"FIELD_STAGE_BEFORE_READ 阶段、offset 之前求值一次"。
- R2-4 【Minor】输入侧管线 `setLocalValues(headerMeta)` 在段不存在时对 null 调用未定义 → 补 null-guard；repeatCountFieldName 在 header 缺失时显式抛错不静默 0。
- R2-5 【Minor】header readWhen 在构造期求值、header 字段尚未入 scope → 注明仅可引用外部 scope 变量。
- R2-6 【Minor】SubBinaryDataReader.subInput（:125-127）绕过外层 position 计数器，嵌套子区域 + 惰性 reader 下对齐公式失真 → 已声明为 watch-only residual（Deferred But Adjudicated）。
- R2-7 【Minor】Non-Goals 与 Phase 5 reader 异常统一措辞张力 → Non-Goals 补注"具体 reader 实现的异常统一除外"。

### 结论

修复后 0 Blocker / 0 Major，plan 可转 active 执行。

- 修订已落盘到 plan 203（Draft Review 元数据 + 各 Phase 条目 + Exit Criteria）。
- plan 保持 draft；需 fresh-session R2 复审（0 Blocker / 0 Major）后转 active。