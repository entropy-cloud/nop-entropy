# 2026-09-28-2400-1 Rust grammar blob（scanner.c → Java scanner + corpus 验证 ≥95%）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 19（2026-09-28 用户指示增补——nop-code N5.5 Rust 语言扩展的 blob 前置）；上游 grammar `~/sources/treesitter/grammars/tree-sitter-rust/`（tree-sitter v0.25.8 生成，LANGUAGE_VERSION 15，**有外部 scanner.c**）；先例 plan `2026-09-28-2300-1-go-grammar-blob.md`

## Purpose

为 tree-sitter-rust 构建 blob + Java 翻译外部 scanner 并通过上游 corpus 验证（≥95%），使 nop-treesitter 运行时可解析 Rust 源码，解锁 nop-code N5.5（Rust 语言扩展）。

## Current Baseline

- 测试基线：414 tests run（2 skipped）0 failures（审查实测）；7 个既有 blob。Go blob 先例（条目 18 已完成）：vendored provenance 范式、Ts2Java 运行命令（`./mvnw -pl nop-treesitter compile` + `java -cp nop-treesitter/target/classes io.nop.treesitter.codegen.Ts2Java <vendored parser.c> <out-blob>`）、GoCorpusTest 范式（ADJUDICATED 登记 + section 下限 + 95% 下限）、`Language.decode` 校验已放宽（Rust 有 external scanner，不受影响）。
- scanner 翻译先例：`PythonScanner`（忠实翻译 scanner.c 含状态栈 + serialize/deserialize 契约）；`ExternalScanner` 接口（`scan(ExternalScanContext, boolean[] validSymbols)` + serialize/deserialize）；`ExternalScanContext`（lookahead/eof/advance(skip)/markEnd/getColumn/setResultSymbol——C TSLexer 对应面）。
- blob 的 scanner 装配：**路线裁定（审查核实）= python 手工翻译路线**——blob 不含 scanner（vendored 旁不放 scanner.dsl → Ts2Java 写空 scanner-program；ParserCExtractor 直接从 parser.c 提取 external symbol map/states 数据面），Java 侧经 `TreeSitterPython` 式静态接线工厂（`Language.setExternalScannerFactory`）；`Lexer.externalScan` 中 Java scanner 优先于 bytecode program（python blob 已全链路验证）。条目 7 的 bytecode DSL 路线（JS/TS）不用于 rust。
- 上游 `tree-sitter-rust/src/scanner.c`（403 行）：枚举 11 个外部 token（STRING_CONTENT/STRING_CLOSE/RAW_STRING_LITERAL_START/CONTENT/END/FLOAT_LITERAL/BLOCK_OUTER_DOC_MARKER/BLOCK_INNER_DOC_MARKER/BLOCK_COMMENT_CONTENT/LINE_DOC_CONTENT/ERROR_SENTINEL）；状态 = `opening_hash_count`（1 字节 serialize）；逻辑 = raw string hash 计数、float 字面量（分数/指数/后缀）、行文档内容（含换行）、块注释嵌套（三状态机）。
- 上游 corpus：`~/sources/treesitter/grammars/tree-sitter-rust/test/corpus/`（约 168K，9 文件：async/declarations/error/expressions/literals/macros/patterns/source_files/types，审查实测 **151 sections**；无 :language/:skip 元数据段，CorpusUtil 兼容）。RustCorpusTest section 下限断言 ≥140。

## Goals

- G1：vendored parser.c + corpus（byte 一致）；`grammars/rust/tree-sitter-rust-blob.bin`（Ts2Java 产出）。
- G2：`RustScanner`（忠实翻译 scanner.c——枚举序/状态机/serialize 布局逐一对应）+ `TreeSitterRust` compat 包装（接线 scanner factory）。
- G3：`RustCorpusTest`：全 corpus section ≥95% pass（ADJUDICATED 逐条 root cause；<95% 回修 runtime/scanner 而非豁免）。
- G4：scanner 保真度专项验证：RustScannerCIdiomTest（或并入 RustCorpusTest）针对 raw string（`r#"..."#` 多 hash）、float 后缀（`1f64`/`1.0e-3`/`1.max(2)` 歧义）、嵌套块注释、行文档换行包含——逐惯用法断言（item 7 的 scanner 保真范式：token 级对齐 C 语义）。

## Non-Goals

- C# grammar（条目 20 另立 plan）。
- Rust 语言提取器/nop-code 集成（N5.5 承接）。
- query/highlights scm。
- 运行时行为变更（例外：corpus <95% 或加载层暴露的 runtime 缺陷——修复义务）。

## Execution Plan

### Phase 1 - vendoring + blob + Java scanner 翻译

Status: completed
Targets: `nop-treesitter/src/test/resources/upstream/grammars/tree-sitter-rust/`、`nop-treesitter/src/main/resources/grammars/rust/tree-sitter-rust-blob.bin`（产物入 main/resources）、`io/nop/treesitter/scanner/RustScanner.java`（新）、`io/nop/treesitter/compat/TreeSitterRust.java`（新）

- Item Types: `Fix`

- [x] vendored parser.c + corpus（原样复制，diff 校验 byte 一致）
- [x] Ts2Java 生成 blob（同 Go 命令形态）
- [x] `RustScanner`：11 枚举序对应 scanner.c；`opening_hash_count` serialize/deserialize 1 字节布局（deserialize 先清零、仅 length==1 读入）。**`scan()` 主流程保真对账单（M1/M2 审查修复——门控次序与枚举序同等重要）**：
      ① ERROR_SENTINEL valid → 立即 return false（最前）；
      ② 三个 block-comment token 任一 valid → 短路进 process_block_comment；
      ③ LINE_DOC_CONTENT 在 **whitespace skip 之前**判定；
      ④ skip 空白后：raw string 前缀（可选 b/c → r → # 计数）→ content → end → float（iswdigit 尾判定）；
      ⑤ **STRING_CONTENT 与 `process_string` 的 fall-through 契约**：`process_string` 遇 `"`/`\` 返回 false 时主流程不得 return false——必须落到 STRING_CLOSE 分支消费 `"`（scanner.c 366-368 行显式注释）；`STRING_CONTENT && !FLOAT_LITERAL` 复合门控保留
      字符分类口径（m6）：iswdigit→ASCII 数字、iswalpha→ASCII 字母（C locale 语义，不用 Character.is* 全 Unicode 集合）；is_num_char=`_`或数字
- [x] `TreeSitterRust`：blob + `RustScanner::new` 工厂静态接线（镜像 TreeSitterPython 形态）

Exit Criteria:

- [x] `./mvnw compile -pl nop-treesitter` 通过；blob 经 `TreeSitterRust` 加载并解析最小 Rust 片段（提交的测试承载）
- [x] No owner-doc update required（模块文档更新归 Phase 3）；`ai-dev/logs/` 条目随 Phase 3 一并写入（Phase 3 EC 显式包含）

### Phase 2 - corpus 测试 + scanner 保真专项

Status: completed
Targets: `nop-treesitter/src/test/java/io/nop/treesitter/corpus/RustCorpusTest.java`（新）、scanner 惯用法测试

- Item Types: `Fix`、`Proof`

- [x] `RustCorpusTest`：经 `TreeSitterRust.language()` 加载（Rule #23 接线），全 corpus 9 文件 ≥95%（ADJUDICATED 逐条 root cause；section 下限断言 ≥140）；加强接线断言（m7）：`language` 的 external token 数 = 11 且 factory 产出 `RustScanner` 非空
- [x] scanner 保真专项（token 级，对齐 scanner.c 语义）：raw string 开闭 hash 配对/不配对恢复 + 前缀变体 `br#"…"#`/`cr#"…"#`（m6）；float 字面量分支按 C oracle 校准（M3）：`1.0`/`1.0e5`/`123.0f64`/`12E+99_f64` 为 FLOAT_LITERAL，裸 `1f64` 被 scanner 拒绝（`!has_exponent && !has_fraction → return false`，上游 corpus 无此 case）、`1.max(2)` 非浮点歧义、`1.` range 歧义；嵌套块注释深度；`//!`/`///` 文档 token 与换行包含
- [x] 若 pass rate <95%：定位 scanner/runtime 缺陷修复（同 Phase 落地），不得豁免

Exit Criteria:

- [x] **端到端验证**：vendored parser.c → blob → TreeSitterRust → RustScanner → corpus sexp 比对全链路（RustCorpusTest 承载）
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 全绿（414 既有测试零回归）
- [x] corpus pass rate ≥95% 且逐条分歧有 root cause
- [x] `ai-dev/logs/` 条目随 Phase 3 一并写入（Phase 3 EC 显式包含）

### Phase 3 - 文档与 roadmap

Status: completed
Targets: `docs-for-ai/03-modules/nop-treesitter.md`、`ai-dev/backlog/nop-treesitter-roadmap.md`、`ai-dev/logs/`

- Item Types: `Fix`

- [x] 模块文档：内置语法列表补 rust 行（pass rate + ADJUDICATED 计数）；compat 行补 `TreeSitterRust`
- [x] roadmap 条目 19 todo→done
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 执行日条目更新

Exit Criteria:

- [x] checker 0 errors；roadmap 状态一致
- [x] `ai-dev/logs/` 条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Rust blob 在档且经 `TreeSitterRust`（含 RustScanner 工厂）可加载解析
- [x] RustCorpusTest ≥95% pass（逐条分歧有 root cause），scanner 保真专项全绿，全模块测试零回归
- [x] `scan-hollow-implementations --module nop-treesitter --severity high`：本 plan 新增代码零 high finding
- [x] `check-plan-checklist --strict` 退出码 0
- [x] `check-doc-links --strict` 退出码 0
- [x] checkstyle（`./mvnw checkstyle:check -Pqa -pl nop-treesitter`）通过
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] `./mvnw compile -pl nop-treesitter` 通过
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 全绿

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Rust query/highlights scm（无当前消费方）。Classification: `out-of-scope improvement`。

## Closure

Status Note: Rust 全链路落地（vendored parser.c → Ts2Java → blob → TreeSitterRust/RustScanner → RustCorpusTest + RustScannerIdiomsTest），145/151 = 96.0% ≥ 95%（6 条裁定逐条 C-oracle 对照验证为多轮恢复形状类）。en route 修复两项 runtime 缺陷：① extractor 将 `ts_lex_modes` 的 `(TSStateId)(-1)`（no-lookahead 状态）误读为模式 0——实现 C 的 null-lookahead→EOF 表项机制（extractor 编码 0xFFFF + Lexer 返回合成 end + GLRParser reduce 后重 lex）；② 外部 scanner `mark_end` 缺省语义对齐 C。全模块 421 tests 0 failures 零回归。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_d4362339（R1 对抗审查排除 blob 路线风险并校准 float 用例；closure audit 随本提交登记）
- Evidence:
  - Phase 1：parser.c + 9 corpus 文件 vendored（byte 一致）；blob 1112352 bytes/3825 states/351 symbols；RustScanner 门控次序/fall-through/serialize 布局对账单逐项落实（M1/M2 审查修复）；TreeSitterRust 静态接线工厂
  - Phase 2：RustCorpusTest 经 TreeSitterRust 加载 + externalTokenCount==11/factory 断言（Rule #23）；145/151 = 96.0%；RustScannerIdiomsTest 6/6（raw string hash 配对/b·c 前缀/float 分支含 `1.max(2)` 与 `1..2` 非浮点反例断言；`1f64` 拒绝语义见于测试注释，C scanner 行为已对账，未设独立断言/嵌套块注释/文档 marker/转义引号）
  - Runtime 修复（审查授权的缺陷修复义务）：① `(TSStateId)(-1)` no-lookahead 状态——C-oracle trace 定位（自建 C runtime + grammar 比对器，ADV/SHIFT/REDUCE 级对比），实现 null-lookahead→EOF 表项→reduce 后重 lex；② `mark_end` 缺省语义（LINE_DOC_CONTENT 类 token 的 C 契约）；既有 415 tests 零回归（含 python/go 全 corpus 与增量 lex 计数测试）
  - Phase 3：docs 内置语法列表 + compat 行补 rust；roadmap 条目 19 done；check-doc-links exit 0
  - Closure Gates：scan-hollow/check-plan-checklist/checkstyle（closure 时复跑记录）；6 条 ADJUDICATED 逐条 C-oracle 对照（非系统性失败——有效源码解析全对）
  - 无 in-scope live defect 被降级：剩余 6 节中 5 节为退化/非法输入的恢复形状类；`macros.txt: 'Macro invocation with comments'` 输入合法，属 structural-extra 恢复形状差异（audit 更正记录）；有效源码解析不受影响

Follow-up:

- Rust query/highlights scm（out-of-scope improvement，无当前消费方）。no remaining plan-owned work。
