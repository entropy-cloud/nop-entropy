# nop-rg 编码与字节保真设计

> 定位：规范文档（设计决策 + 使用契约）。只写结论与理由，不写实现代码、不写演进历史。
> 关联：`ai-dev/backlog/nop-rg-encoding-fidelity-roadmap.md`（执行分解）、`ai-dev/design/nop-rg/01-architecture-baseline.md`（总体架构）。
> 状态：**设计已定，未实施**。

## 一、问题定义

### 1.1 搜索本身已在字节域，解码只发生在两处

澄清一个常见误判：nop-rg 的模式匹配、行定位、count 计数**全部在字节域**运行，不存在"必须转 UTF-8 才能搜"的开销。

| 环节 | 是否解码 | 实现位置 |
| --- | --- | --- |
| literal 模式匹配 | 否 | `SearchCommand.patternBytes`（UTF-8 编码）+ SWAR/BMH 在 `MemorySegment` |
| 行定位 / 行号 / count | 否 | `ByteScan` + `MatchAggregator` 的 `advanceLineStart` 免解码路径 |
| 命中行文本输出（text 口径） | **是** | `LineCursor.text` / `MatchAggregator.decode` |
| regex 整文件路径 | **是** | `RegexSearcher.decode`（整文件解码为 String） |
| regex 候选行路径（Wave 5 item 19） | **是（仅候选行）** | `RegexSearcher.processCandidateLine` |

因此"假定 UTF-8 换取性能"在 literal 路径**无性能可换**。

### 1.2 真实缺陷是正确性，不是性能

`new String(bytes, StandardCharsets.UTF_8)` 对非法字节静默替换为 `U+FFFD`，且 JDK 在非法序列上会退回 `CharsetDecoder` 慢路径。后果：

1. **输出失真且不可逆**：`--json` 的 `lines.text` / `submatches[].match.text` 在非 UTF-8 文件上输出替换符，无法用于精确定位或回写。
2. **二进制嗅探只覆盖一部分**：`SearchCoordinator.isBinary` 在头部 8KB 找 NUL 即跳过文件，纯二进制多被过滤；漏网的是**合法文本但非 UTF-8**（GBK / Latin-1 / Shift-JIS 的源码、日志、CSV），这类文件 NUL 少，会被当文本处理并输出失真。
3. **regex 在非 UTF-8 上静默错配**：`RegexSearcher` 假定 UTF-8 整文件/候选行解码，非 UTF-8 文件上模式与实际字节不匹配——错误是静默的（返回 0 命中而非报错）。
4. **`rg --json` 契约缺字段**：rg 在非 UTF-8 场景输出 `{"text": <lossy>, "bytes": <base64>}`，nop-rg 只输出 `text`（`JsonOutput.writeMatch`），兼容面不完整。

## 二、设计决策

### 决策 1：默认字节保真——不引入"假定 UTF-8"开关

**拒绝**增加诸如 `--assume-utf8` / `--no-decode` 的性能开关。理由：

- 合法 UTF-8 输入下当前解码已是最优路径，开关零收益；
- 非法 UTF-8 输入下开关只是把"替换"换成"抛错/跳过"，都不是有用语义；
- 引入开关等于把一个正确性缺陷伪装为调优选项，会让用户在不知情下持续得到失真输出。

**采纳**：默认行为改为**字节保真直通**——输出侧不经过 lossy 解码，而是保留原始字节并按 JSON 契约双字段输出（见决策 2）。合法 UTF-8 下与现状字节等价；非法 UTF-8 下**比现状更保真**（无信息损失），且不再走 decoder 慢路径。

### 决策 2：输出契约对齐 rg —— `text` + `bytes` 双字段

命中行与子匹配的输出结构改为：

- `text`：按 UTF-8 lossy 转义后的可读文本（与 rg 一致：非法序列记 `U+FFFD`），保证 JSON 合法与终端可读；
- `bytes`：原始字节的 **base64**（仅当该区间非合法 UTF-8 时输出；合法 UTF-8 时省略，保持现有输出零增长）。

依据：rg 15.x `--json` 在非法字节场景输出 `bytes`（base64，含行终止符）。**注意与 rg 的形态差异是裁定**：rg 为 `text`/`bytes` 互斥（非法行不再输出 text），nop-rg 输出为**有意超集**——`text`（lossy）恒在 + `bytes` 仅在区间原始字节非法 UTF-8 时出现（该出现条件与 `--encoding` 无关；指定 GBK 等编码时同样按原始字节是否合法 UTF-8 判定）。这样既保留可读性，又不丢失可编程消费者所需的精确字节，且消费方按 rg 的「读 bytes 优先」逻辑天然兼容。`lines.bytes` 覆盖区间含行终止符（`lineStart..lineEnd`），submatch `bytes` 为命中的字节区间 `[byteStart, byteEnd)`。

**兼容性**：合法 UTF-8 文件的输出与当前逐字节相同（新增字段仅在非法场景出现），既有对拍测试（`RgComparisonTest` 等）不受影响。

### 决策 3：`--encoding <charset>` 作为显式功能，且必须三处一致

若提供命名编码支持（如 `--encoding gbk`），**必须同时作用于三处**，任一处遗漏都会造成静默错配：

1. **模式编码**：`SearchCommand.patternBytes` 当前写死 UTF-8，须按 `--encoding` 编码（否则搜不到）；
2. **regex 解码**：`RegexSearcher` 的整文件与候选行解码须用指定 Charset（否则 regex 语义错误）；
3. **输出编码**：`text` 字段按指定编码解码后转义，`bytes` 字段仍为原始字节。`line_number`/`submatch start-end` 等行内偏移保持**原始文件字节口径**（2026-11-20 plan 2575 R11 实测裁定：rg 在 `--encoding` 下报告的是转码后流的偏移——gbk.txt 行 3 实测 rg=37 vs 原始文件 31；nop-rg 有意不对齐，原始偏移与 `bytes` 字段引用原始字节一致，对可编程消费者更有用）。

`--encoding none`（字节保真，不做任何字符解码）是决策 2 的显式形式；未指定时默认等价于 `--encoding none` 但对合法 UTF-8 正常可读（即决策 2 的双字段行为）。

### 决策 4：二进制判定与编码解耦

`isBinary` 的 NUL 嗅探保持不变（在头部 8KB 内）。`--encoding` 不改变二进制判定——即便指定了 GBK，二进制文件仍按二进制跳过。若要强制搜索二进制内容，属独立开关（不在本设计范围）。

### 决策 5：性能不倒退（2026-11-20 plan 2575 R12 实测修正机制表述）

- 合法 UTF-8：输出路径与现状同级——合法性扫描仅发生在**命中行内容**（非全文件），且合法行除扫描外走原 `new String` 快路径，输出与改动前逐字节相同（golden + `RgComparisonTest` 双证）；端到端实测 34MB 语料 `--json` 229ms（system rg 同语料 133ms，差值为既有 JVM/初始化开销，与编码改动无关）。
- 非法 UTF-8：机制表述修正——原「基础扫描比 decoder 回退快」在 JDK 25 上**不成立**（`new String` 的 lossy 路径已是 SIMD 级校验+替换，实测约为手写逐序列解码的 3 倍速）。实现裁定：合法性判定保留自有扫描（SWAR ASCII 预扫 + 逐序列校验，用于 bytes 出现条件与合法性语义），lossy text 直接复用 `new String`。非法场景的净增成本 = base64 输出（决策 2 新契约的必需成本，非回退），实测 20MB GBK 语料端到端 490ms；「较现状更快」不再承诺。
- 后续优化候选（non-blocking）：Vector API SIMD 合法性扫描（对齐 rg 的 simd utf8 校验）。
- `text` 字段在「消费方不读文本」时（count 口径）已免解码，保持现状。

## 三、语义保真与验收

| 项 | 验收方式 |
| --- | --- |
| 合法 UTF-8 输出零变化 | 既有 `RgComparisonTest` 对拍全绿；JSON 逐字节 diff 无差异 |
| 非法 UTF-8：`bytes` 可还原原始字节 | base64 解码 == 原始文件区间（逐字节断言） |
| 非法 UTF-8：`text` 与 rg 一致 | 与系统 rg `--json` 对同一文件逐字段比对（`text` 为 lossy 形态） |
| `--encoding gbk` 三处一致 | GBK 语料：模式命中、regex 命中、输出文本均正确；漏改任一处该用例必红 |
| 无静默错配 | `--encoding` 非法值时 exit 3 显式报错，不回退 UTF-8 |
| 性能不倒退 | JMH 或 `ib bench`：合法 UTF-8 语料输出路径无可测退化；非法语料较现状有提升 |

**强制探针**：`--encoding gbk` 的 key 验收必须包含「只改模式编码、不改 regex 解码」与「只改输出、不改模式」两个**故意破坏**的变异探针，确认对应用例确实变红（防假绿）。

## 四、非目标

- 不实现自动编码探测（charset 自动识别）——不可靠，且 rg 亦不做。
- 不实现 BOM 自动跳过以外的编码特殊处理。
- 不改变二进制判定策略（决策 4）。
- 不把本设计混入 Glob / 搜索性能 roadmap（`nop-rg-globset-parity-roadmap.md`）——性质不同（正确性 vs 性能）。
