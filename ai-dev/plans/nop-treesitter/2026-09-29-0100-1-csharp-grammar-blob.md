# 2026-09-29-0100-1 C# grammar blob（scanner.c → Java scanner + corpus 验证 ≥95%）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 20（2026-09-28 用户指示增补——nop-code N5.6 C# 语言扩展的 blob 前置）；上游 `~/sources/treesitter/grammars/tree-sitter-c-sharp/`（v0.25.8 生成，LANGUAGE_VERSION 15，**有外部 scanner.c**（640 行）；**parser.c 含 17 个 `(TSStateId)(-1)` 模式**——条目 19 的 no-lookahead 修复直接适用）；先例 plan `2026-09-28-2400-1-rust-grammar-blob.md`）

## Purpose

为 tree-sitter-c-sharp 构建 blob + Java 翻译外部 scanner 并通过上游 corpus 验证（≥95%），解锁 nop-code N5.6（C# 语言扩展）。

## Current Baseline

- 条目 18/19 先例全在档（vendored provenance、Ts2Java 命令、corpus 测试范式、no-lookahead 修复已入 extract/Lexer/GLRParser、mark_end 缺省语义已修）。
- 上游 scanner.c（640 行）：13 外部 token（1 OPT_SEMI + 8 INTERPOLATION_*（REGULAR/VERBATIM/RAW 三态插值栈：dollar_count/open_brace_count/quote_count/string_type，serialize = 2 字节头 + 每层 4 字节）+ 3 RAW_STRING_* + 1 LAMBDA_PAREN_OPEN（C# 14 simple-lambda 投机扫描：whole-identifier 缓冲消解 ref/readonly 前缀冲突，NO_PAREN/FAILED_AFTER_PAREN/SUCCESS 三态——FAILED 时必须 return false 让 runtime 回退））。
- corpus：**19 文件**（含 attributes/classes/contextual-keywords/enums/expressions/identifiers/interfaces/literals/preprocessor/query-syntax/type-operators/statements/...，约 380K），**179 sections**（95% 线 = ≥171 通过，ADJUDICATED 预算 ≤8）；无 :skip/:language 元数据段，CorpusUtil 兼容。

## Goals

- G1：vendored parser.c + corpus；`grammars/c-sharp/tree-sitter-c-sharp-blob.bin`。
- G2：`CSharpScanner`（忠实翻译，含插值栈 serialize 布局与 lambda 投机扫描回退语义）+ `TreeSitterCSharp` compat。
- G3：`CSharpCorpusTest` ≥95%（ADJUDICATED 逐条 root cause + C-oracle 对照；section 下限）。
- G4：scanner 保真专项：插值（`$"`/`$@"`/`$$"` 转义括号）、raw string（`"""` 多引号配对）、OPT_SEMI、lambda 投机（`(x, y) =>` 不误触发；`(ref x) =>` 触发；FAILED 后由内建 `(` 接管）。

## Non-Goals

- C# 提取器/nop-code 集成（N5.6）；query/highlights scm；运行时行为变更（例外：corpus <95% 或加载层缺陷的修复义务）。

## Execution Plan

### Phase 1 - vendoring + blob + CSharpScanner + compat

Status: completed
Targets: `src/test/resources/upstream/grammars/tree-sitter-c-sharp/`、`src/main/resources/grammars/c-sharp/tree-sitter-c-sharp-blob.bin`、`scanner/CSharpScanner.java`（新）、`compat/TreeSitterCSharp.java`（新）

- Item Types: `Fix`

- [x] vendored parser.c + corpus（diff byte 一致）；Ts2Java 生成 blob（同 Go/Rust 命令形态）
- [x] `CSharpScanner`：13 枚举序对应；插值栈 Array→`ArrayList<Interpolation>`（serialize = quote_count + stack.size + 每层 4 字节，先清零后读）；`scan_lambda_paren_open` 三态翻译（whole-identifier 缓冲 + `scoped` 软修饰语义 + FAILED_AFTER_PAREN return false 触发 runtime 回退）；ASCII 字符分类口径（C locale）。**主 scan 门控次序对账单（含审查补充的关键分支）**：
      ① LAMBDA_PAREN_OPEN 最前（NO_PAREN fall-through / FAILED_AFTER_PAREN return false）；
      ② error-recovery gate：OPT_SEMI && INTERPOLATION_REGULAR_START 同时 valid → return false；
      ③ OPT_SEMI **零宽契约**：lookahead 非 `;` 时仍 return true（空 token）；
      ④ RAW_STRING_START→END（失败置 did_advance fall through 进 CONTENT）→CONTENT；
      ⑤ INTERPOLATION START：`result_symbol` 三次改写链（REGULAR→VERBATIM→RAW），**string_type 位组合**——raw push 保留 dollar_count、verbatim 实际落盘为 VERBATIM|REGULAR；
      ⑥ START_QUOTE 可 advance 后 `return quote_count > 0`；END_QUOTE 失败置 did_advance fall through；OPEN_BRACE 校验 dollar_count 与非 `{`；CLOSE_BRACE 用 advance()（非 skip）吃空白且分支内显式 return false、**内部 shadow 同名 brace_advanced**；
      ⑦ STRING_CONTENT **top-down 三态**：is_raw→is_verbatim→is_regular else-if 链、verbatim 成对 `""` 的 continue、循环尾 `if (lookahead != '{') brace_advanced = 0; advance; did_advance = true;`、**整个分支 `return did_advance`（零宽必须 false，译成 true 会零进度自旋）**；
      ⑧ did_advance/brace_advanced 为函数级共享局部状态（跨 RAW/INTERPOLATION 分支），brace_advanced 在 CLOSE_BRACE 与 STRING_CONTENT 内各自重置/shadow
- [x] `TreeSitterCSharp`：blob + factory 静态接线

Exit Criteria:

- [x] 编译通过；blob 经 `TreeSitterCSharp` 加载解析最小 C# 片段（提交的测试承载）
- [x] No owner-doc update required（Phase 3 统一）；`ai-dev/logs/` 随 Phase 3 写入

### Phase 2 - corpus 测试 + 保真专项

Status: completed
Targets: `corpus/CSharpCorpusTest.java`（新）、scanner 惯用法测试

- Item Types: `Fix`、`Proof`

- [x] `CSharpCorpusTest`：经 `TreeSitterCSharp` 加载（Rule #23）+ externalTokenCount==13 断言；全 corpus 19 文件 ≥95%（ADJUDICATED 逐条 root cause + C-oracle 对照，`_tmp/rust-oracle/` 比对器按 c-sharp parser.c 重建复用）；section 下限断言 ≥170
- [x] 保真专项：插值三态（`$"{x}"`/`$@"...{x}..."`/`$$"{x}"` 转义）、raw string 多引号、OPT_SEMI 缺省分号、lambda 投机正反例、`(TSStateId)(-1)` 状态经 no-lookahead 机制正确 reduce（对照 C oracle `~/sources/treesitter` 自建比对器，条目 19 产物复用）
- [x] 若 <95%：定位 scanner/runtime 缺陷修复（同 Phase 落地），不得豁免

Exit Criteria:

- [x] **端到端验证**：parser.c → blob → TreeSitterCSharp → CSharpScanner → corpus sexp 比对全链路
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 全绿（既有 8 blob 零回归）
- [x] pass rate ≥95% 且逐条分歧有 root cause

### Phase 3 - 文档与 roadmap

Status: completed
Targets: `docs-for-ai/03-modules/nop-treesitter.md`、roadmap、`ai-dev/logs/`

- Item Types: `Fix`

- [x] 模块文档内置语法列表补 csharp 行（pass rate + 裁定计数）+ compat 行补 `TreeSitterCSharp`
- [x] roadmap 条目 20 todo→done
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 执行日条目更新

Exit Criteria:

- [x] checker 0 errors；roadmap 状态一致；log 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] C# blob 在档且经 `TreeSitterCSharp` 可加载解析
- [x] CSharpCorpusTest ≥95%（逐条分歧有 root cause + C-oracle 对照），保真专项全绿，全模块测试零回归
- [x] `scan-hollow-implementations --module nop-treesitter --severity high`：新增代码零 high finding
- [x] `check-plan-checklist --strict` 退出码 0
- [x] `check-doc-links --strict` 退出码 0
- [x] checkstyle（`-Pqa -pl nop-treesitter`）通过
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 全绿

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- C# query/highlights scm（无当前消费方）。Classification: `out-of-scope improvement`。

## Closure

Status Note: C# 全链路落地（vendored parser.c → Ts2Java → blob → TreeSitterCSharp/CSharpScanner → CSharpCorpusTest + CSharpScannerIdiomsTest），177/179 = 98.9% ≥ 95%（2 条裁定:对照官方 tree-sitter CLI 0.25.8 corpus 实跑（179/179 全绿，audit 独立复现）——`#line`+方法声明组合的 ERROR 包裹恢复形状差异（官方为干净 class_declaration，登记为 N5.6 可能的 successor 保真缺口）与错误恢复-插值扫描器零宽交互（verbatim @" 区域；audit 更正:非 $" 区域））。全模块 428 tests 0 failures 零回归。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_fdeca9e8（R1 对抗审查 4 Major 全修——externalTokenCount==13/`-1` 模式归属 parser.c/INTERPOLATION ×8/STRING_CONTENT top-down 三态与 did_advance 零宽 return-false 清单；closure audit 随本提交登记）
- Evidence:
  - Phase 1：parser.c + scanner.c + 19 corpus 文件 vendored（byte 一致）；blob 5790294 bytes/8495 states/542 symbols；CSharpScanner 门控对账单 8 项逐条落实（含 STRING_CONTENT `return did_advance` 零宽契约与 brace_advanced shadow）
  - Phase 2：CSharpCorpusTest 经 TreeSitterCSharp 加载 + externalTokenCount==13/instanceof 断言（Rule #23）；177/179 = 98.9%；2 条裁定经官方 tree-sitter CLI 0.25.8 corpus 实跑对照登记 root cause（audit 独立复现 179/179；自建临时比对器行为与官方不符，已弃用其对照结论）；CSharpScannerIdiomsTest 6/6（插值三态/`$$` 转义/raw string/lambda 正反例）
  - Phase 3：docs 内置语法列表 + compat 行补 csharp；roadmap 条目 20 done；check-doc-links exit 0
  - Closure Gates：scan-hollow exit 0；check-plan-checklist --strict exit 0；checkstyle -Pqa（closure 时复跑记录）；全量 428 tests 0 failures
  - 无 in-scope live defect 被降级：2 条裁定均登记精确 root cause（其一显式标注为 N5.6 可能 successor 的保真缺口）

Follow-up:

- C# query/highlights scm（out-of-scope improvement）。`#line` 组合保真缺口若 N5.6 实测受影响则立 successor plan。no remaining plan-owned work。
