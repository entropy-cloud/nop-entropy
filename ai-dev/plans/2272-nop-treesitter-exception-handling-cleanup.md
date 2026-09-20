# 2272 nop-treesitter 异常处理合规收尾（ISE/IAE 豁免入档）

> Plan Status: active
> Last Reviewed: 2026-09-20
> Source: `ai-dev/logs/2026/09-20.md` 深夜五/六条目、`docs-for-ai/02-core-guides/error-handling.md`、plan 2271 收口后的验收核查
> Related: `ai-dev/plans/2271-nop-treesitter-quality-fixes.md`（已完成，其 Closure Gates 已豁免存量 IAE/ISE）

> Draft review：独立子 agent 一轮对抗审查（2 Major + 5 Minor）+ 修订点复核 7/7 PASS（"可直接执行"）。审查 agent：`agent_e45dc22a-cf64-4d98-afaf-bcd3927d4bd9`。

## Purpose

在 plan 2271 与其后的异常处理核查基础上，把 nop-treesitter main 代码中剩余的异常处理不合规项修复完毕，并把用户裁定（`IllegalStateException`/`IllegalArgumentException` 允许）连同相关 JDK 标准异常的豁免边界落档到 owner doc，使模块异常处理与 `docs-for-ai/02-core-guides/error-handling.md` 完全对齐（豁免项除外）。

## Current Baseline

- 已修复（commit `67d7f36dd8`）：`TreeSitterException` 改继承 `NopException`（规范模块异常类模板，四构造器）；`TSQuery` #match? 正则 rethrow 已补 cause。414 tests 全绿。
- 用户裁定（2026-09-20，本计划输入）：`IllegalStateException` 与 `IllegalArgumentException` 在本模块**允许使用**——即 129 处 ISE + 3 处 IAE（codegen 格式校验、GLR 不变式防护、arena/lexer 校验等）不转换为模块异常类，清偿工作不再立项。
- 全模块恰 10 处 catch，逐处复核后确认合规的 7 处：`Language`:141/:484、`Ts2Java`:29、`ParserCExtractor`:949、`TSQuery`:260（均为 rethrow with cause）；`ParserCExtractor`:118/:349（NumberFormatException → 替代解析 → 兜底 throw，尝试性解析模式）。
- 待修清单（本次 scope）：
  1. `GLRParser.appendTree`（DEBUG 渲染路径）`catch (RuntimeException)` 把 `symbolName` 越界静默降级为 `"?" + sym` —— 用异常做控制流且丢弃前无留证（error-handling.md 不允许的位置与形态）。
  2. `ScannerCompiler:354` `catch (NumberFormatException e)` 后 `throw new IllegalStateException(...)` **未传 cause**（丢失原始异常链，与已修的 TSQuery 同型）。
  3. `ParserCExtractor:1682` `\x` 转义解码用 `try { parseInt(hex,16) } catch (NumberFormatException ignored)` 做控制流——语义是"非法十六进制则按字面字符降级"，属解码回退而非异常处理，可用字符预校验消除 try-catch。
  4. `ParserCExtractor:118` catch 变量名 `ignored` 名不副实（catch 后有完整的 fallback 逻辑），误导审计。
- 待裁定入档：`Subtree.child` 抛 `IndexOutOfBoundsException`（JDK 集合 API 标准语义，对齐 `List.get` 契约）、`GLRParser` 未知 action type 抛 `UnsupportedOperationException`（规范明确认可的快速失败形态）——归入 JDK 标准异常豁免边界，与 ISE/IAE 裁定一并写入模块 owner doc。

## Goals

- 修复上述 4 处：消除异常控制流与静默降级、补齐丢失的异常链、修正误导性命名。
- 豁免裁定入档：`docs-for-ai/03-modules/nop-treesitter.md` 新增"异常处理约定"小节，写明（a）ISE/IAE 经 2026-09-20 用户裁定豁免及适用场景边界；（b）IOOBE/UOE 的 JDK 标准语义豁免；（c）业务/公共 API 路径仍必须走 `TreeSitterException`（extends NopException）/`NopException + ErrorCode`。
- 全程行为零变化（4 处修复均为等价重构或错误路径增强；DEBUG 路径输出不变）。

## Non-Goals

- 不转换 129+3 处 ISE/IAE（用户裁定豁免，清偿不再立项）。
- 不改 `blob-format.md` 的 reader contract 及其 ISE 语义。
- 不动测试代码中的异常用法（assertThrows 等为测试断言，不在规范约束范围）。
- 不动 compat 层（纯包装，无自有异常路径）。

## Scope

### In Scope

- `parser/glr/GLRParser.java`（appendTree）
- `scanner/ScannerCompiler.java`（intOperand 补 cause）
- `codegen/ParserCExtractor.java`（hex 解码预校验、`ignored` 改名）
- `docs-for-ai/03-modules/nop-treesitter.md`（异常处理约定小节）
- 对应测试调整（如断言受影响）与 `ai-dev/logs/`

### Out Of Scope

- `subtree/Subtree.java` 的 IOOBE（裁定豁免，不改代码）
- 其余全部模块文件（2271 已收口）

## Execution Plan

### Phase 1 - catch 路径合规修复

Status: in progress
Targets: `GLRParser.java`、`ScannerCompiler.java`、`ParserCExtractor.java`

- Item Types: `Fix`

- [ ] GLRParser.appendTree：以显式 symbol 范围检查替代 `catch (RuntimeException)` 静默降级。范围必须与 `Language.checkSymbolRange` 一致：`[0, symbolCount + aliasCount)`——**上界含 alias 区间**（用 `symbolCount()` 单独做上界会把 alias 符号错误降级为 `"?"`，审查 F3）；越界符号（chainContainer/builtinError/builtinErrorRepeat）渲染为 `"?" + sym` 的现行为保持不变
- [ ] ScannerCompiler.intOperand：ISE 构造补 `e` 为 cause（消息文本不变，既有断言 `malformedIntegerOperandFailsLoudly` 只锁消息、不受影响——审查 F4 确认）
- [ ] ParserCExtractor `\x` 转义：以十六进制字符预校验替代 try-catch 控制流。**必须逐条复刻的三条现语义**（审查 F2）：① 候选为 `substring(i+1, min(i+3, len))`，**1 位 hex 也成功解码**（`"\xa"` → U+000A，不是降级）；② `\x` 恰为串尾时（`i+2 > len`）现行为**连 `'x'` 都不 append，直接跳过**；③ 两位候选中任一字符非 hex（如 `"xg1"`）则**整体降级** append `'x'`，后续字符由主循环重放。全有全无（parseInt 全成全败），无部分解析值
- [ ] ParserCExtractor:118 catch 变量 `ignored` 改为非误导命名
- [ ] 复核上述文件无新引入的裸 RuntimeException / 中文消息

Exit Criteria:

- [ ] `grep -n "catch" src/main/java -r` 全模块复核：除豁免场景（尝试性解析 + 显式兜底 throw）外，每个 catch 要么 rethrow with cause，要么被显式条件检查替代
- [ ] `./mvnw test -pl nop-treesitter -am` 全绿（414 基线；无既有断言修改——4 处均为等价重构）
- [ ] 新增测试（审查 F1 修正：6 个 vendored parser.c 中 `\x` 出现次数为 0，既有测试不覆盖该路径）：① 在 `Ts2JavaExtractionTest` 的合成 parser.c 骨架中加入含合法 `\x`（1 位与 2 位各一）与 malformed `\x`（非 hex 字符、串尾截断）的 C 字符串用例，断言成功解码与降级输出；② `ScannerCompilerTest.malformedIntegerOperandFailsLoudly` 补一条 cause 链断言（`getCause() instanceof NumberFormatException`）
- [ ] No new test required（仅此一项）：GLRParser 变更仅在 `ts.debug` 开启时可见——DEBUG 为类加载期常量（GLRParser:59），测试进程内无法安全切换；以逐行等价性 diff 复核替代（见 Closure Gates 验证方式）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 豁免裁定入档

Status: planned
Targets: `docs-for-ai/03-modules/nop-treesitter.md`

- Item Types: `Decision`

- [ ] 新增"异常处理约定"小节：业务/公共 API 路径用 `TreeSitterException`（extends NopException）与 `NopException + ErrorCode`；ISE/IAE 经 2026-09-20 用户裁定豁免（格式校验/不变式防护/_codegen 工具），并引用 error-handling.md 的两档策略作为默认规则；IOOBE（Subtree.child，JDK 集合语义）与 UOE（未知 action type 快速失败）归入 JDK 标准异常豁免边界
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [ ] 约定小节与 live 代码一致（举例的类/行为可在源码定位）
- [ ] doc link checker 退出码 0
- [ ] `ai-dev/logs/` 收口条目完成

## Closure Gates

> 所有 Phase Exit Criteria 全部 `[x]` 后才可进入关闭流程；独立 closure audit 由单独子 agent 执行。

- [ ] 4 处修复落地且经 live code 复核（无 catch-RuntimeException 静默降级、无丢 cause、无异常控制流残留、无 `ignored` 误导命名）
- [ ] 豁免裁定已入档 owner doc 且与 live 代码一致
- [ ] 无 in-scope live defect 被降级到 deferred / follow-up
- [ ] 行为零变化：测试断言零修改、`ts.debug` 输出路径行为不变——验证方式：closure audit 对 appendTree 与 hex 解码两处变更做逐行等价性 diff 复核（对照本 plan 钉死的语义清单），非自动化检查（审查 F6）
- [ ] 独立子 agent closure audit 完成并记录证据
- [ ] **Anti-Hollow Check**：修复点均可在源码定位且非注释性声明；无新增空方法/静默跳过
- [ ] `./mvnw compile -pl nop-treesitter -am` 通过
- [ ] `./mvnw test -pl nop-treesitter -am` 通过
- [ ] `./mvnw checkstyle:check -Pqa -pl nop-treesitter` 通过（前置：本地仓库已有各 reactor 依赖 SNAPSHOT；如失败先跑一次 `./mvnw install -DskipTests -pl nop-treesitter -am`——审查 F5）

## Deferred But Adjudicated

### ISE/IAE 全量转换（129+3 处）

- Classification: `removed from scope through a recorded scope change`
- Why Not Blocking Closure: 用户 2026-09-20 明确裁定 ISE/IAE 允许使用；原全仓审计的"中期独立 checkstyle 规则集"建议不再适用于本模块
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:
