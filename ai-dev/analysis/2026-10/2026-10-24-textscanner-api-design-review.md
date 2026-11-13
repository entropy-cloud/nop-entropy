# TextScanner 接口设计与重构必要性分析

> Status: resolved
> Date: 2026-10-24
> Scope: `nop-kernel/nop-commons` 的 `io.nop.commons.text.tokenizer.TextScanner` 及其仓内 50 个消费文件（nop-core / nop-xlang / nop-markdown / nop-ai-core / nop-report-core / nop-rpc-model / nop-graphql-core / nop-excel / nop-ioc / nop-ui / nop-stream / nop-idea-plugin 等）
> Conclusion: 不做整体重写；先修正确性缺陷并补齐契约文档，再做向后兼容的 API 收敛，最后按需引入 CharSequence 专用批量扫描层（plan-first）。性能不是重构的主要理由。

## Context

- 问题：`TextScanner` 是 Nop 平台手写词法分析的事实标准入口，已有 1558 行、110 个 public 方法、9 个 public 可变字段，方法族语义（读/试探/消费/跳空白/批量读到分隔符）高度相似但行为不同。
- 决策点：是否需要在设计、直观性、AI 易用性、性能等维度做接口级重构，以及重构的边界和优先级。
- 约束：
  - `ai-dev/plans/2341-full-code-review-4.3-giant-files-audit.md:7` 已登记历史裁定「TextScanner 1558（1.4 保留）」，本次不推翻该结论，只补充接口层证据和增量建议。
  - `TextScanner` 位于公共模块 `nop-commons`，仓外也可能被使用，不能做破坏性删除/改名。
  - `ai-dev/plans/344-nop-json-parser-writer-fast-path.md` 的 JMH 实测表明：在 JSON 树解析中词法层可优化面合计 <10%，因此「为了性能重写」缺乏收益证据。

## 现状画像

### 规模与消费面

| 指标 | 数值 | 证据 |
|------|------|------|
| 源文件行数 | 1558 | `TextScanner.java:1-1558` |
| public 方法（含重载/工厂） | 110 | `grep -cE "^    public " TextScanner.java` |
| public 可变字段 | 9（`line/col/pos/cur/lineSkipped/blankSkipped/useEvalException/peekToken/peekTokenLen`） | `TextScanner.java:79-90` |
| 仓内消费文件 | 50 | `grep -rl TextScanner --include=*.java` |
| 字段直读（近似） | `.cur` 约 258 次；`.line/.col/.pos` 合计约 43 次；`.peekToken` 8 次 | 50 个消费文件内 grep（含少量同名非扫描器调用，仅作量级参考） |
| 单元测试 | 1 个测试类、15 个用例 | `TestTextScanner.java:19-320` |

### 方法调用频次（近似，含同名非扫描器调用）

高频：`skipBlank` 154、`match` 132、`location` 101、`tryMatch` 78、`next` 73、`fromString` 70、`isEnd` 69、`newError` 58、`startsWith` 52、`setPeekToken` 51、`peek` 44、`tryMatchToken` 43、`consume` 33、`tryConsume` 31、`nextUntil` 28。

中频：`nextJavaVar` 26、`nextNumber` 25、`nextGraphQLVar` 24、`skipBlankInLine` 20、`nextJsonString` 14、`checkEnd` 12、`nextInt` 10、`nextXmlName` 9、`matchToken` 9。

低频/仓内零调用：`nextNumberOrPercent`、`skipSlashComment`、`skipStarComment`、`checkEndOrBlankSkipped`、`checkIsDigit`、`checkIsHex`、`startsWithLine`、`startsWithToken`、`startsWithTokenIgnoreCase`、`startsWithSpace`、`tryConsumeIgnoreCase`、`tryConsumeToken`、`moveToNextToken`、`beginPosition`、`endPosition`、`checkNotNull`、`checkNotEmpty`、`rangePosition`/`isRangePosition`、`nextUntilUnescaped`（约 20 个 public 方法在仓内无调用）。

## 设计合理性评估

### 合理之处（应保留）

1. **单字符游标 + 前瞻模型清晰**：`cur` 为当前字符，`next()` 推进，`peek(n)` 前瞻，适合递归下降 parser，避免完整 token 流的分配。
2. **错误定位能力完整**：`newError(ErrorCode)` 自动附带 reader 状态、`pos`、`SourceLocation`，并区分 `NopEvalException`（`TextScanner.java:147-157`）。
3. **缓冲区复用**：`getReusableBuffer()`/`localBuf` 显著减少 `String` 分配（`TextScanner.java:766-769`）。
4. **针对性快路径**：JSON 字符串与十进制整数的 CharSequence 前瞻快路径（`TextScanner.java:696-752`、`1203-1235`），在不改变语义的前提下降低热路径成本。
5. **流式/内存双输入支持**：`ICharReader` 抽象同时支持 `CharSequenceReader` 与 `FastBufferedReader`，接口稳定。

### P0：正确性缺陷（与重构无关，优先修）

| 编号 | 问题 | 证据 | 影响 |
|------|------|------|------|
| TS-P0-01 | `consumeIgnoreCase(char, ErrorCode)` 调用的是 `tryConsume(c)` 而非 `tryConsumeIgnoreCase(c)`，大小写不敏感语义完全失效 | `TextScanner.java:358-361` | 当前仓内无调用（仅 `consumeIgnoreCase(String)` 被用于 `XNodeParser.java:460`），属潜伏 API 缺陷；外部调用即错 |
| TS-P0-02 | `nextUntilUnescaped` 在输入以反斜杠结尾时，`out.accept(cur)` 收到 `-1`，被 `IntConsumer` 转成 `'\uFFFF'` 写出 | `TextScanner.java:1378-1389` | 已在 `ai-dev/analysis/2026-09/2026-09-05-nop-format-deep-bug-hunt.md:73` 记录；当前仅在 Markdown 侧规避，根因未修 |

### P1：契约与直觉性缺陷（重构的主要理由）

| 编号 | 问题 | 证据 | 后果 |
|------|------|------|------|
| TS-P1-01 | **`match*` 隐式跳过空白，`consume*` 不跳**，但命名不体现差异 | `consume` `:331-339`，`match` `:405-418`，`tryMatch` `:420-436` | 最容易选错的 API 族；AI/新人在「匹配后继续读 token」与「精确消费字符」之间反复误用 |
| TS-P1-02 | **`peek(0)` 返回当前字符**，而 `peek()` 返回下一字符 | `peek()` `:190-192` vs `peek(int)` `:247-251` | 违反常见 `peek(offset)` 约定（offset=0 通常指下一字符），差一位错误难以靠阅读发现 |
| TS-P1-03 | **`nextUntil*` 停在分隔符之前且不消费分隔符**，签名/命名均未表达 | `nextUntil` `:1325-1332`；`MultiLineConfigParser.java:59-62` 注释专门提醒；`2026-09-05-nop-format-deep-bug-hunt.md:56`（E2） | 已造成实际解析 bug（注释/批注解析后停在 `"`，`nextXmlName` 抛错） |
| TS-P1-04 | **`nextUntil` 返回共享 `localBuf`**，所有权未声明；下一次扫描即被覆写 | `getReusableBuffer` `:766-769`，`nextUntil` `:1325-1332` | AI 若把返回值暂存跨调用使用会读到脏数据；现有消费方被迫写 `.toString()`/`.trim()` 链式防御 |
| TS-P1-05 | **换行/分隔符消费策略在同族方法间不一致**：`nextLine` 消费换行，`nextUntilEndOfLine` 不消费；`nextXmlNamespace` 在 `:` 前停下不消费 | `nextLine` `:1420-1433`、`nextUntilEndOfLine` `:1320-1322`、`nextXmlNamespace` `:900-924` | AI 无法从名字推断后置条件，必须逐方法读实现 |
| TS-P1-06 | **`blankSkipped`/`lineSkipped` 是「上一次读取函数」的全局副作用**，且 `skipBlankInLine` 也置 `blankSkipped=true` | `:84-85`、`skipBlank` `:479-489`、`skipBlankInLine` `:525-531` | 跨方法状态污染；`checkTokenEnd`/`checkBlankSkipped` 的判定依赖调用历史，难以推理 |
| TS-P1-07 | **`startsWith("")`/`startsWith(null)` 直接抛下标/NPE**，未做入参守卫 | `:194-196` | 外部传入空串（常见于空 `expected`）时异常信息与扫描位置无关 |
| TS-P1-08 | **`useEvalException` 为 public 可变字段**，错误类型由数据状态决定 | `:87`，仓内 4 个消费文件直写 | 异常类型成为隐式全局配置，违反最小惊讶原则 |

### P2：结构性问题（影响 AI 易用性与长期维护）

1. **方法数量与命名族过多**：`consume`/`tryConsume`/`match`/`tryMatch`/`matchToken`/`tryMatchToken`/`moveToNextToken` 七套入口，叠加 `startsWith*` 五种变体。AI 选择空间过大，且没有一张「是否消费、是否跳空白、是否要求 token 边界」的契约表。
2. **重载语义漂移**：`nextUntil(Predicate, boolean, String)` 返回 `MutableString`，`nextUntil(Predicate, IntConsumer)` 返回 `boolean`；`nextUntilEnd()` 有两个重载（`void` 与 `MutableString`）。同一名字承担「收集/跳过/是否 EOF 报错」不同职责。
3. **重复实现**：`nextJavaString`/`nextJsonString`/`nextLooseJsonString` 三份逐字符循环高度相似（`:1158-1309`）；`skipSlashComment`/`skipStarComment` 与 `skipJavaComment` 能力重叠（`:1435-1503`）。
4. **双状态源**：快路径要求扫描器 `pos` 与底层 `reader` 内部位置严格对齐，需要显式防御检查并手工结算 `pos/col/lineState`（`:705-739`、`:1207-1234`）。这是性能优化的必要代价，但把「不变量」散落在多个方法里，缺少集中断言。
5. **`peekToken` 侧信道**：用 public 字段 `peekToken/peekTokenLen` 在 parser 与 scanner 间传递预读 token（`:89-90`、`:112-120`），不是内聚的 lookahead API；`AbstractExprParser` 与 `ExcelFormulaParser` 各写 51/11 次 `setPeekToken`，说明该模式已被大量复制。
6. **位置区间 API 弱**：`beginPosition`/`endPosition` 通过隐式状态配对（`:170-178`），无法嵌套，也没有返回显式 mark 对象。
7. **错误码族不一致**：`consume(char)` 用 `ERR_SCAN_UNEXPECTED_CHAR`，`consume(String)` 用 `ERR_SCAN_UNEXPECTED_STR`；`checkBlankSkipped` 抛错不带 `ARG_CUR`（`:498-501`）。
8. **测试覆盖偏科**：现有 15 个用例集中在 number/JSON/line，XML 名称、注释、`nextUntil`/`skipUntil`、`consumeIgnoreCase`、`nextUntilUnescaped` 均无直接测试（`TestTextScanner.java`）。

### 性能评估

结论：**性能不构成整体重构的理由，但存在「输入表示相关」的隐性悬崖**。

- JMH 证据（`ai-dev/plans/344-nop-json-parser-writer-fast-path.md:123,190`）：JSON 字符串快路径单独收益 ≈0（415.7K vs 423.6K ops/s）；整数快路径后 425,175 ±4,892 vs 基线 423,608 ±12,246（误差内持平）。Profile 热点：`LinkedHashMap.newNode` 19.6%、`skipBlank` 18.7%（safepoint 采样偏差放大）、`nextJsonString` 3.9%、`consumeDigits` 2.9%——词法层合计 <10%。
- 真正的结构性成本：
  - `startsWith` 对长串逐字符 `peek(i)`（`:204-208`），每次一个虚调用；`nextUntil` 每字符一次谓词 + 一次 `IntConsumer`（`:1358-1371`）。
  - 快路径只在 `getBaseSequence() != null`（即 `CharSequenceReader`）时生效（`:103-107`、`:697-699`、`:1204-1206`）。`FastBufferedReader` 输入会静默退回逐字符路径，形成性能悬崖且无提示。
  - `location()` 每次返回 `SourceLocation`（`:159-168`），高频 parser 每 token 调用一次；plan 344 已将 SourceLocation 优化显式 deferred。
- 优化方向（低成本、行为不变）：把 `nextUntil`/`skipUntil`/`startsWith` 在 `CharSequence` 输入上改为基于索引的批量扫描（与现有 JSON 快路径同构），而不是扩大逐字符方法数量。

### AI 易用性评估

- **优点**：方法名动词前缀（`next`/`try`/`skip`/`check`）基本一致；错误构造集中在 `newError`；工厂方法 `fromString`/`fromReader` 直观。
- **主要障碍**：
  1. 相似方法无契约表（是否消费分隔符、是否跳空白、返回值是否可持有），AI 必须读实现才能选对。证据：`MultiLineConfigParser.java:59-62` 需要注释提醒 `nextUntil` 不消费分隔符；`SimpleExprParser.java:169` 的谓词误用外层 `sc` 而非参数 `s`（因恰好是同一实例而未暴露）。
  2. 共享缓冲区返回值（`MutableString`）与「返回 `String`」的方法混用，所有权语义不一致。
  3. 9 个 public 字段允许绕过方法直接改 `cur/pos/line`，AI 容易写出破坏 `lineState`/reader 同步的代码。
  4. 同族方法后置条件不一致（TS-P1-05），无法靠命名推断。
  5. 仓内零调用方法约占 public 方法 18%，增加候选集噪声；AI 可能选中未维护/无测试的路径。

## 重构意见

### 总体裁定

**需要「接口契约重构」，不需要「实现重写」。** 保留 `TextScanner` 类名、`ICharReader` 抽象、现有方法签名与语义（向后兼容），按三阶段渐进推进：

#### Phase A（P0，立即，独立小 PR）

1. 修 `consumeIgnoreCase(char, ErrorCode)` 调用错误（`:358-361`），补大小写回归测试。
2. 修 `nextUntilUnescaped` EOF 时 `out.accept(-1)`（`:1378-1389`），补「尾部反斜杠」测试。
3. 为 `startsWith*` 增加空串/null 守卫，抛带位置的 `ERR_SCAN_INVALID_CHAR` 或明确 `IllegalArgumentException`。
4. 为上述三条各加回归测试；现有 15 个用例不回退。

#### Phase B（P1，向后兼容的 API 收敛）

1. **契约文档化**（不改签名）：在类 Javadoc 增加方法族矩阵，至少四列：`是否消费匹配内容`、`是否消费分隔符`、`是否跳空白`、`返回值所有权`。这是对 AI 易用性收益最大、成本最低的一项。
2. **所有权显式化**：新增 `String nextUntilString(...)` / `String nextTextUntil(...)` 等返回不可变结果的方法（内部仍用 `localBuf`），并在旧 `MutableString` 方法 Javadoc 标注「borrowed until next scanner call」。不删除旧方法。
3. **命名消歧**：新增别名 `consumeAndSkipBlank` / `tryConsumeAndSkipBlank` 作为 `match`/`tryMatch` 的语义化入口；旧名保留并标 `@Deprecated(forRemoval = false)`。同理为 `nextLine` 与 `nextUntilEndOfLine` 增加 `consumeLine`/`readToLineEnd` 明确命名。
4. **状态封装**：新增 `getCur()/getLine()/getCol()/getPos()` 与 `setUseEvalException(boolean)`；保留 public 字段以免破坏性能与 258 处 `.cur` 直读，但在字段 Javadoc 标注「只读、勿写」。不要改成 getter-only（会破坏热点与兼容性）。
5. **零调用方法治理**：对约 20 个仓内零调用方法标注 `@Deprecated` 并在 Javadoc 指向替代方法（如 `skipJavaComment` 替代 `skipSlashComment`/`skipStarComment`），不直接删除。
6. **位置区间**：新增 `SourceLocation mark()` + `locationSince(mark)`，旧 `beginPosition/endPosition` 保留并委托实现；解决不可嵌套问题。

#### Phase C（P2，plan-first，按需）

1. **CharSequence 专用批量层**：抽取包内 `CharSequenceCursor`（索引直读 `charAt` + 批量 `indexOf`/前缀比较），`TextScanner` 作为门面在 `getBaseSequence() != null` 时委托。目标是消除 `startsWith`/`nextUntil`/`skipUntil` 的逐字符虚调用，并让流式输入保持现状。必须满足：现有全部 `TestTextScanner` + 下游模块测试全绿，`SourceLocation` 行列号逐位一致。
2. **统一的 lookahead API**：以 `TokenBuffer`/`peekToken()` 方法替换 `peekToken/peekTokenLen` public 字段；先提供新 API 并让 `AbstractExprParser`、`ExcelFormulaParser` 迁移，再 deprecate 字段。该改动跨 `nop-xlang`/`nop-report-core`，必须 plan-first。
3. **字符串扫描去重**：把 `nextJavaString`/`nextJsonString`/`nextLooseJsonString` 收敛为参数化的单一实现（转义策略 + 闭引号判定策略），降低三份循环漂移风险。属于内部重构，可用现有测试守住契约。

### 被否决的方案

| 方案 | 否决理由 |
|------|----------|
| 用 ANTLR/正式 lexer 整体替换 | `ai-dev/plans/345-xpath-xtransform-hardening.md:303` 已裁定工具链替换收益不明确；且会改变所有 parser 的错误位置/容错语义，回归面覆盖 12+ 模块 |
| 把 `TextScanner` 拆成多个类（读/消费/数字/字符串/注释） | 历史裁定「1.4 保留」；拆分会让 50 个消费方同时改 import/委托，收益低于契约文档化；当前文件规模未达拆分阈值 |
| 把 public 字段全部私有化 | 258 处 `.cur` 直读 + 热点性能，破坏性过大；用「文档标注只读 + 新增访问器」渐进替代 |
| 为性能重写整个扫描器 | JMH 证明 JSON 词法层收益 <10%，且快路径已覆盖两个最热方法；整体重写风险/收益不成比例 |

### 兼容与验收策略

- Phase A/B 全部保持 public 签名不变，只加方法/文档/`@Deprecated`，属于 `nop-commons` 兼容演进，不需要跨模块迁移。
- Phase C 涉及 `nop-xlang`/`nop-report-core` 调用面，必须先写 `ai-dev/plans/` 计划并满足：`./mvnw test -pl nop-kernel/nop-commons,nop-kernel/nop-core,nop-kernel/nop-xlang -am` 通过，且 `nop-report-core`、`nop-ai-core` 编译冒烟通过（沿用 plan 344 的验证门禁）。
- AI 易用性验收建议：新增一份方法族矩阵后，用 3-5 个「给定输入 + 期望后置状态」的小用例覆盖 `consume/match/nextUntil/skipUntil/nextLine` 的消费语义，作为文档与实现的共同 tripwire。

## Conclusion

- **不需要整体重构，需要契约级重构**：优先修 2 个正确性缺陷，随后以「方法族契约表 + 返回值所有权 + 命名别名 + 状态封装」收敛接口，最后按需做 CharSequence 批量扫描层。
- 设计合理性的核心问题不是方法多，而是**同族方法的消费/跳空白/分隔符/缓冲区所有权语义不一致且未文档化**；这直接造成历史 bug（E2）和 AI 选错方法。
- 性能维度不支持大规模重写：词法层在 JSON 解析中占比 <10%，且快路径已存在；性能改进应集中在「流式输入静默变慢」这一表示相关性问题上。
- 被否决的方案：ANTLR 替换、类拆分、字段全私有化、性能驱动的整体重写。
- 后续工作：Phase A 可直接实施；Phase B 建议新建 `ai-dev/plans/XX-textscanner-contract-hardening.md`；Phase C 单独 plan-first（涉及 `nop-xlang`/`nop-report-core`）。本次仅产出分析，未创建计划文档。

## Open Questions

- [ ] `nextUntilUnescaped` 的根因修复是否会影响 `MarkdownNormalizer` 的既有规避逻辑（需确认其是否已过滤 `\uFFFF`）？
- [ ] `startsWith("")` 的期望语义是「恒 true」还是「非法参数」？仓内无调用，需确认外部契约。
- [ ] Phase C 的 `CharSequenceCursor` 是否应同时服务 `ITextTokenizer` 路径，还是只作为 `TextScanner` 内部实现？
- [ ] `peekToken` 字段迁移后，`XLangOperator` 预读长度语义（`peekTokenLen`）是否需要改为由 token 自身携带？

## References

- `nop-kernel/nop-commons/src/main/java/io/nop/commons/text/tokenizer/TextScanner.java`
- `nop-kernel/nop-commons/src/test/java/io/nop/commons/text/TestTextScanner.java`
- `nop-kernel/nop-commons/src/main/java/io/nop/commons/io/stream/ICharReader.java`
- `ai-dev/plans/344-nop-json-parser-writer-fast-path.md`（JMH 基线与快路径收益）
- `ai-dev/plans/345-xpath-xtransform-hardening.md:303`（ANTLR 替换否决）
- `ai-dev/plans/2341-full-code-review-4.3-giant-files-audit.md:7`（TextScanner 1.4 保留裁定）
- `ai-dev/analysis/2026-09/2026-09-05-nop-format-deep-bug-hunt.md:56,73`（E2 与 U+FFFF 根因）
- `ai-dev/audits/check2/nop-commons.md:132,157`（`MutableString` 与 `getReusableBuffer` 的暴露风险）
