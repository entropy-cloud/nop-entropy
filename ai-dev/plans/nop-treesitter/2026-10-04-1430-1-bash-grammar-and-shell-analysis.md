# 2026-10-04-1430-1 Bash grammar blob + shell 分析语义层（AI Agent 转换/分析）

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: 用户指示（2026-10-04）——"nop-treesitter 现在已经有 python 等解析，也需要增加 bash 的解析；找一个完整的 bash 做参考，用途就是 ai agent 执行的时候做转换和分析"；`ai-dev/backlog/nop-treesitter-roadmap.md` 条目 21；上游参考仓库 `tree-sitter/tree-sitter-bash`（**当前不在** `~/sources/treesitter/grammars/`，需先获取）；语义层参考实现 `~/ai/codex` 的 `codex-rs/shell-command/src/{bash.rs,parse_command.rs,shell_snapshot_literals.rs,command_safety/is_dangerous_command.rs}` 与 `codex-rs/apply-patch/src/invocation.rs`；先例 plan `2026-09-28-2400-1-rust-grammar-blob.md`、`2026-09-29-0100-1-csharp-grammar-blob.md`
> Related: `ai-dev/design/nop-ai-shell/04-bash-syntax.md`、`ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md`

## Purpose

让 nop-treesitter 具备 **bash 语法**能力，并把"AI Agent 执行 shell 命令时的转换与分析"落成可复用的公共 API：把一段 shell 脚本稳定地转换为结构化命令序列（可证明的字面量 argv / 不可证明的显式状态）、提取 heredoc、标记动态构造点、抽取字面量文件路径。

## Current Baseline

**已成立的事实**

- nop-treesitter 是纯 Java tree-sitter 运行时（GLR + 图结构栈 + 外部 scanner VM + query 引擎 + 增量重解析），已交付 9 个 grammar blob：`json` / `java` / `javascript` / `typescript` / `tsx` / `python` / `go` / `rust` / `c-sharp`（`src/main/resources/grammars/<name>/tree-sitter-<name>-blob.bin`）。
- grammar 装配路径固定且已被 4 个先例验证：`Ts2Java`（`io.nop.treesitter.codegen.Ts2Java`，从 vendored `src/parser.c` 抽表 → blob 格式 v4）→ `compat/TreeSitter<Lang>` 适配器（静态接线 scanner 工厂）→ `corpus/<Lang>CorpusTest`（上游 `test/corpus/*.txt` 全量跑，≥95% 门禁 + `ADJUDICATED` 逐条 root cause + C-oracle 对照）。
- 已有 scanner 落地先例：`PythonScanner`（12 token）、`RustScanner`（11 token）、`CSharpScanner`（13 token），均为 `scanner.c` 的忠实手译 + 单独的 `*ScannerIdiomsTest` 保真专项。
- `DefaultTreeSitterLanguageProvider.BUILTIN_BLOBS`（`provider/DefaultTreeSitterLanguageProvider.java`）当前只注册 6 个（json/java/javascript/python/typescript/tsx）；`go`/`rust`/`c-sharp` 只能经 compat 类触达。
- GraphQL 门面 `TreeSitterBizModel.parseTreeSitter(source, language)` 已上线，未知 language 返回 `nop.err.treesitter.unknown-language`。
- `TestTreeSitterProvider.java:40` 硬断言 `assertEquals(7, provider.languageNames().size())`（6 内置 + 1 测试 classpath custom provider）。

**真正剩余的 gap**

1. **没有任何 bash grammar**。`~/sources/treesitter/grammars/` 下只有 c-sharp / go / java / javascript / json / python / rust / typescript 八家，**没有 tree-sitter-bash**；`/Volumes/data/sources/treesitter/tree-sitter-ng/tree-sitter-bash` 只是 JNI binding（`org_treesitter_TreeSitterBash.c`），**不含 `parser.c` / `scanner.c` / `test/corpus`**。因此本计划的第一步不是写代码，而是**获取并 vendoring 上游 grammar 源**。
2. **没有面向 shell 的语义层**。nop-treesitter 目前只提供"任意语言的通用树"，把树翻译成"这条 shell 命令会做什么"的能力（命令拆分 / heredoc 提取 / 动态构造标记 / 字面量路径抽取）在仓库内不存在。
3. **已存在一个手写的 bash 子集解析器，但不在本模块**：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java`（模块共 4508 行 Java），配套 `ai-dev/design/nop-ai-shell/04-bash-syntax.md`（1800+ 行模型文档）与 `ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md`（Tier 1/2/3 支持矩阵，明确"不追求 Bash 兼容性"）。二者**尚未做过任何对接裁定**。

**参考实现的可用性裁定（codex）**

`~/ai/codex` 确认有可借鉴的 bash tree-sitter 语义层，用 `tree-sitter-bash`（crates.io `0.25`，lock 解析为 `0.25.1`，无 patch、无 vendored grammar）。它**刻意不完整**——fail-closed 白名单，而非完整 shell 模型：

- **字面量白名单拆分**（`codex-rs/shell-command/src/bash.rs:29-98`）：仅允许 10 类命名节点 + 6 个匿名 token；`root.has_error()` 直接拒绝；子 shell、重定向、`&`、`$VAR`、`$(...)`、反引号、heredoc、`if/for/while/case`、变量前缀赋值、glob、`~` 全部返回"不可证明"。
- **字面量收割**（`bash.rs:136-160`）：接受复杂语法，遍历所有 `command` 节点取静态已知词，动态词截断。注释明写"可用于识别危险字面命令，**不可用于证明命令安全**"。
- **heredoc 提取**（`codex-rs/apply-patch/src/invocation.rs:341-386`）：用带 anchor 的 query 保证 heredoc 语句是唯一顶层语句；且这是唯一**不**做 `has_error()` 门控的路径。
- **深度分片解码**（`shell_snapshot_literals.rs`，715 行）：唯一不走 `has_error()` 的解码器，命令替换（`$()` 与反引号，含反引号转义手写解码）、heredoc 按 expansion 切段、声明类包装剥离。
- **消费方**：审批/execpolicy 拆分（`core/src/exec_policy.rs:880`，解析失败即整条当不透明命令）、危险命令检测（`is_dangerous_command.rs:66`，深度上限 8）、命令摘要与路径抽取（`parse_command.rs:1998`）、apply_patch handler（`core/src/tools/handlers/apply_patch.rs:514`）。
- **测试形态**：无 insta snapshot、无 `.sh` fixture，全部内联字符串断言；`shell_snapshot_tests.rs` 与真实 `/bin/bash`、`/bin/zsh` 做差分测试。

结论：**grammar 用上游 tree-sitter-bash；语义层需求清单用 codex 的行为契约，但按 nop-treesitter 的定位重写**（nop-treesitter 是通用解析库，不承担安全策略裁定）。

## Goals

- **G1**：vendored `tree-sitter-bash`（`parser.c` + `scanner.c` + `test/corpus/` + provenance），`Ts2Java` 产出 `grammars/bash/tree-sitter-bash-blob.bin`。
- **G2**：`BashScanner` —— `scanner.c` 的忠实 Java 翻译；`compat/TreeSitterBash` 静态接线 scanner 工厂。
- **G3**：`BashCorpusTest` 上游 corpus ≥95%，逐条分歧登记 root cause + C-oracle 对照；`BashScannerIdiomsTest` 保真专项全绿。
- **G4**：shell 语义层公共 API（`io.nop.treesitter.shell`），提供 6 项能力：字面量命令序列拆分、字面量收割、heredoc 提取、动态构造点标记、字面量文件路径抽取、脚本摘要。**未能证明时返回显式"不可证明"状态，禁止静默返回空结果**。
- **G5**：`bash` 进入 `BUILTIN_BLOBS`（NopIoC / GraphQL 可解析），语义层经 GraphQL action 暴露。
- **G6**：模块文档 / README / wiki / roadmap / 日志同步到 live baseline。

## Non-Goals

- **不做 shell 执行、sandbox、审批策略裁定**。"这条命令危险不危险"是 agent guardrail 的职责；本模块只提供判定所需的结构化事实与"可证明/不可证明"状态。
- **不做 shell 运行时语义求值**：参数展开、算术展开、进程替换、命令替换的**运行时值一律不求值**（codex 也只标记不求值）。
- **不做 zsh / fish / powershell 语法**。codex 用 bash grammar 兼解 zsh，本模块不复制该做法。
- **不做 bash query / highlights `.scm`**（与 go/rust/c-sharp 先例一致：无当前消费方）。例外：若 Phase 0 裁定 heredoc 提取必须依赖 `#any-of?` 谓词，则该谓词支持属 in-scope。
- **不改 `BashSyntaxParser` / 不迁移 `nop-ai-shell`**。只产出衔接裁定结论（Phase 4），迁移另立 successor plan。
- **不改运行时行为**，唯一例外：corpus <95% 或 blob/扫描器装配层缺陷的修复义务（与 rust/c# 先例同）。

## Scope

### In Scope

- 上游 grammar 获取、vendoring、provenance 记录
- blob 生成、`BashScanner`、`TreeSitterBash`、provider 注册
- 上游 corpus 测试 + scanner 保真专项
- `io.nop.treesitter.shell` 语义层公共 API + 单元/端到端测试
- GraphQL action 暴露语义层
- 契约 design doc、模块文档、README、wiki、roadmap、日志

### Out Of Scope

- `nop-ai-shell` 的 parser 替换或双 parser 融合
- 任何 shell 执行 / 权限 / 沙箱代码
- zsh、powershell、fish 等其他 shell
- 增量重解析在 shell 场景的专门优化（通用 `parseIncremental` 已可用，本计划不新增 shell 专用路径）
- 性能调优（若 corpus 跑不动先修正确性；perf 归 `docs/perf-tuning.md` 既有流程）

## Execution Plan

### Phase 0 - 参考基线获取与契约裁定

Status: planned
Targets: `~/sources/treesitter/grammars/tree-sitter-bash/`（新建，只读参考副本）、`nop-treesitter/src/test/resources/upstream/grammars/tree-sitter-bash/`、`ai-dev/design/nop-treesitter/`

- Item Types: `Decision`、`Proof`

- [ ] **获取上游 grammar 源**。`~/sources/treesitter/grammars/` 无 tree-sitter-bash；`/Volumes/data/sources/treesitter/tree-sitter-ng/tree-sitter-bash` 只是 JNI binding，无 `parser.c`/`scanner.c`/`test/corpus`。获取路径按优先级：① clone `tree-sitter/tree-sitter-bash` 到与 codex 同代的 v0.25.x tag（记录精确 tag 与 commit SHA）；② 退路 crates.io `tree-sitter-bash` crate 包（须先验证其是否含 `test/corpus`，不含则仍需 ①）。
- [ ] **实测并记录 grammar 指纹**：`LANGUAGE_VERSION`、`STATE_COUNT`、`LARGE_STATE_COUNT`、`SYMBOL_COUNT`、`TOKEN_COUNT`、`EXTERNAL_TOKEN_COUNT`、`FIELD_COUNT`、外部 token 清单。**不得沿用本 plan 或任何对话中的预估数字**——以 vendored `parser.c` 实测为准，写入 `PROVENANCE.md`。
- [ ] vendored `src/parser.c`、`src/scanner.c`、`test/corpus/*.txt`（全量）、`PROVENANCE.md`（含 tag/SHA/license/生成器版本 + "Never modified" 声明，格式对齐 `tree-sitter-typescript/PROVENANCE.md`）；确认 `scanner.c` 存在且非空（若该版本无 scanner.c，本 Phase 立即改为零外部 scanner 路线并回写本 plan 的 Phase 1 描述）。
- [ ] **统计 corpus 基线**：文件数、section 总数、`:skip` / `:language` 元数据段、`CorpusUtil` 兼容性；据此把 Phase 2 的 section 下限断言写成实测值。
- [ ] **语义层契约 design doc**：新建 `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md`（遵循 `ai-dev/design/00-design-writing-guide.md`，只写契约与裁定，不写类签名/方法列表/伪代码）。必须裁定的点：
  1. **语义层归属**：放 `io.nop.treesitter.shell`（推荐——grammar 与语义同源，且 nop-treesitter 不得反向依赖 nop-ai），`nop-ai-shell` 作为消费方。
  2. **"不可证明"表达**：拆分 API 在无法证明时返回显式状态（而非空列表 / null）；`has_error()` 为真时一律不可证明。
  3. **两套拆分的分工**（照 codex 的 A/B 策略）：白名单拆分用于"证明安全"，字面量收割用于"识别危险"；design doc 必须写明两者**不可互换**的边界。
  4. **heredoc 提取实现路径**：节点遍历（推荐，不需新谓词）vs `TSQuery` + `#any-of?`（若选后者，`#any-of?` 支持列入 in-scope）。
  5. **动态构造点枚举**：命令替换（`$()` / 反引号）、参数展开、算术展开、进程替换、重定向、heredoc、后台 `&`、变量前缀赋值——哪些计入"含动态部分"。
  6. **zsh 兼容声明**：不承诺 zsh 语法；调用方若传入 zsh 特有构造，视为不可证明。
- [ ] **codex 行为契约清单化**：把 codex 的 6 类行为（字面量白名单拆分 / 字面量收割 / heredoc 提取 / 动态标记 / 字面量路径抽取 / 摘要）逐条抄成"期望行为"清单，作为 Phase 3 的 Exit Criteria 依据（只抄行为与判定，不抄 Rust 代码）。

Exit Criteria:

- [ ] `~/sources/treesitter/grammars/tree-sitter-bash/` 与 vendored 副本均存在且 `parser.c` byte 一致
- [ ] `PROVENANCE.md` 含实测 grammar 指纹与 corpus section 总数，且数字与 vendored 文件核对一致（可复算）
- [ ] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` 存在，6 个裁定点均有明确结论；`check-doc-links --strict` 对新文件 0 errors
- [ ] 本 Phase 产生的裁定已回写本 plan（Phase 1/2/3 的 Targets 与断言数字随实测更新）
- [ ] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 1 - blob 生成 + BashScanner + TreeSitterBash

Status: planned
Targets: `src/main/resources/grammars/bash/tree-sitter-bash-blob.bin`、`src/main/java/io/nop/treesitter/scanner/BashScanner.java`、`src/main/java/io/nop/treesitter/compat/TreeSitterBash.java`

- Item Types: `Feature`

- [ ] `Ts2Java` 从 vendored `parser.c` 生成 blob（命令形态对齐 go/rust/c# 先例），blob 落 `grammars/bash/`；记录 blob 字节数与 Phase 0 实测表的一致性。
- [ ] `BashScanner`：`scanner.c` 忠实翻译，逐 token 枚举序对齐 `EXTERNAL_TOKEN_COUNT`；**主 scan 门控次序必须写成可核对的对账单**（照 `CSharpScanner` 的 8 项门控清单体例），至少覆盖：各外部 token 的触发条件与互斥关系、`valid_symbols` 门控（含 error-recovery 态下多 token 同时 valid 时的 return false 语义）、零宽 token 契约（无推进时必须 return false，否则自旋）、`mark_end` 语义（对齐 item 19 已修的 C 缺省语义）、任何跨调用状态（若有）的 serialize/deserialize 布局。
- [ ] `TreeSitterBash`：blob + scanner 工厂静态接线（形态对齐 `TreeSitterRust` / `TreeSitterCSharp`）。
- [ ] **单元测试**：`BashScannerIdiomsTest` 覆盖对账单每一项的正反例（照 `CSharpScannerIdiomsTest` 体例）。

Exit Criteria:

- [ ] 编译通过；`TreeSitterBash` 可加载并解析最小 bash 片段（`echo hi`、`ls -la | grep x`、`for i in a b; do echo $i; done`）
- [ ] `BashScannerIdiomsTest` 全绿，且**每条对账单条目都有对应断言**（无"整体通过"式覆盖）
- [ ] 新增功能的测试覆盖已显式列出（`BashScannerIdiomsTest` 逐例对应门控条目）——见 Rule #25
- [ ] **无静默跳过**：任一未覆盖的外部 token 分支以显式异常 / 显式 return false 表达，不存在空分支或 placeholder 返回（见 Rule #24）
- [ ] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` 与本 Phase 实现一致（无 drift）
- [ ] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 2 - 上游 corpus 验证 ≥95%

Status: planned
Targets: `src/test/java/io/nop/treesitter/corpus/BashCorpusTest.java`

- Item Types: `Proof`、`Fix`

- [ ] `BashCorpusTest`：经 `TreeSitterBash` 加载（Rule #23 接线证据）+ `externalTokenCount` 断言 + scanner 实例断言；跑 Phase 0 统计出的全量 corpus 文件，section 下限断言写死为实测值。
- [ ] 门禁：`pass * 100 >= sections.size() * 95` 且 `pass == sections.size() - adjudicated.size()`；每条 `ADJUDICATED` 必须写明 corpus 文件 + 标题 + root cause，并给出 C-oracle 证据（复用 rust/c# 计划留下的自建 C runtime + grammar 比对器思路 `_tmp/rust-oracle/`，或对照官方 tree-sitter CLI 0.25.x corpus 实跑）。
- [ ] **若 <95%**：定位 scanner / blob / runtime 缺陷并在同 Phase 修复，**不得豁免、不得下调门禁**（对齐 rust/c# 先例的硬义务）。此类修复按 `Fix` 归类，并同步评估是否需要回写 `docs-for-ai/03-modules/nop-treesitter.md` 的已知偏差段。
- [ ] 既有 9 个 grammar 的 corpus 测试零回归（java 108 / json 7 / js 116 / ts / tsx / python / go / rust / c-sharp）。

Exit Criteria:

- [ ] **端到端验证**：vendored `parser.c` → `Ts2Java` → blob → `TreeSitterBash` → `BashScanner` → corpus sexp 全链路打通（Rule #22）
- [ ] **接线验证**：`BashScanner` 确实被 `TreeSitterBash` 的 scanner 工厂在运行时产出并被解析路径调用（不是仅注册未调用）（Rule #23）
- [ ] pass rate ≥95%，逐条分歧有 root cause + C-oracle 对照；全模块测试零回归
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` 退出码 0
- [ ] `scan-hollow-implementations --module nop-treesitter --severity high` 退出码 0
- [ ] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 3 - shell 语义层公共 API

Status: planned
Targets: `src/main/java/io/nop/treesitter/shell/`（新包，具体类名与签名以 Phase 0 design doc 为准）、`src/main/java/io/nop/treesitter/biz/TreeSitterBizModel.java` 或新增 shell 门面（以 design doc 裁定为准）

- Item Types: `Feature`

- [ ] **能力 1 — 字面量命令序列拆分**（codex `bash.rs:29-98` 契约）：只接受白名单节点集合；`root.has_error()` 为真或出现白名单外构造时，返回显式"不可证明"状态。必须拒绝并有测试的构造：子 shell `( )`、花括号 `{ }`、重定向 `>` `<` `>>`、后台 `&`、`$VAR` / `${VAR}` / `$(...)` / 反引号、`<(...)` 进程替换、`$((...))` 算术展开、`if`/`for`/`while`/`case`/函数定义、变量前缀赋值（`FOO=bar ls`）、heredoc、brace expansion、glob、`~`、`^`、`#`、`=`。
- [ ] **能力 2 — 字面量收割**（codex `bash.rs:136-160` 契约）：接受复杂语法，遍历全部 `command` 节点收集静态已知词，遇首个非字面量参数即截断。**必须能在 `if/for/while`、管道、命令替换内部找到命令**（对抗样例照 codex `is_dangerous_command.rs:272-290`：`printf x | rm -rf /tmp/x`、`if test -d d; then rm --force d; fi`、`trap 'rm -rf x' EXIT`、`for t in a b; do rm -r -f "$t"; done`、`bash -c 'rm -rf x'`）。
- [ ] **能力 3 — heredoc 提取**：识别 `<<` / `<<-` / `<<<`，从 `heredoc_start` 取 delimiter、判定是否引号包裹（决定是否切分 expansion）、取 `heredoc_body`；带 anchor 语义的"heredoc 语句是唯一顶层语句"约束按 Phase 0 裁定实现。负例必须覆盖 codex 的 anchor 负例集（`cd foo;` / `cd bar ||` / `cd bar |` / `echo foo &&` / `cd foo && cd bar &&` / `cd foo bar &&` / `echo foo; cd bar &&`）。
- [ ] **能力 4 — 动态构造点标记**：对命令替换、参数展开、算术展开、进程替换、重定向、heredoc、后台 `&`、变量前缀赋值，产出带源码区间与类型的标记列表（不求值）。
- [ ] **能力 5 — 字面量文件路径抽取**：从能力 1/2 的结果中抽取"看起来是路径"的字面量 token，输出候选路径及其来源命令（供 agent 判断 read/list/search 意图）。
- [ ] **能力 6 — 脚本摘要**：复合脚本 → 结构化摘要（命令序列 + 连接符 + 是否含动态部分），供 UI / 遥测 / 审批缓存 key 使用；不可证明时摘要显式标注为不可证明而非降级为原文。
- [ ] **GraphQL 暴露**：语义层经 GraphQL action 可调用（形态对齐既有 `parseTreeSitter`；错误走 `TreeSitterErrors` + `.param(...)`，遵守 `docs-for-ai/02-core-guides/error-handling.md`）。
- [ ] **测试**（Rule #25，逐能力列出）：每个能力至少一组"能证明"正例 + 一组"必须判为不可证明/必须标记为动态"的负例；对抗集必须包含 codex 的已知陷阱样本（`rm -rf "$TARGET" >/dev/null`、`echo "$(rm -rf x)"`、brace expansion、extended glob、`~`、`^`、`#`、`=`）。

Exit Criteria:

- [ ] **端到端验证**：一段真实 agent 生成的复合脚本（如含 heredoc + 管道 + 命令替换 + `for` 循环）从 `language="bash"` 入口一路走到结构化输出，断言输出包含预期的命令序列、heredoc body、动态标记（Rule #22）
- [ ] **接线验证**：GraphQL action 在运行时确实调用语义层，语义层在运行时确实调用 `TSParser.parse` + `TreeSitterBash`（计数器 / 断言 / 调用链审查三选一给出证据）（Rule #23）
- [ ] **无静默跳过**：每个能力的"不可证明"分支都返回显式状态；`UnsupportedOperationException("not yet implemented: ...")` 或显式错误码，不存在"返回空列表当作没有命令"的路径（Rule #24）
- [ ] 能力 1 与能力 2 的"不可互换"边界在 design doc 与 Javadoc 中均已写明，并有测试证明能力 1 的成功蕴含能力 2 的成功、反之不成立
- [ ] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` 与本 Phase 实现一致（无 drift）
- [ ] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 4 - Nop 集成、文档与 roadmap

Status: planned
Targets: `provider/DefaultTreeSitterLanguageProvider.java`、`src/test/java/io/nop/treesitter/biz/TestTreeSitterProvider.java`、`docs-for-ai/03-modules/nop-treesitter.md`、`nop-treesitter/README.md`、`nop-treesitter/docs/wiki.md`、`ai-dev/backlog/nop-treesitter-roadmap.md`、`ai-dev/logs/2026/10-04.md`

- Item Types: `Fix`、`Decision`

- [ ] `BUILTIN_BLOBS` 增加 `bash` 条目（含 blob 路径）；`loadLanguage` 中按现有 `python` 分支形态接线 `BashScanner` 工厂（provider 路径必须能独立解析 bash，不依赖 compat 类）。
- [ ] **同步修正 `TestTreeSitterProvider.java:40` 的 `assertEquals(7, ...)`** 为新值并更新其"六个内置"注释——这是已确认的会 breakage 的断言，不得遗忘。
- [ ] 模块文档：内置语法列表补 bash（pass rate + 裁定计数）、compat 行补 `TreeSitterBash`、新增"shell 分析语义层"章节（能力清单 + 可证明/不可证明边界 + 消费方指引）。
- [ ] `nop-treesitter/README.md` 用户指南补 bash 解析与语义层示例；`nop-treesitter/docs/wiki.md` 导航同步。
- [ ] `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 21 `planned`→`done`，并回填实测数字（blob 大小、corpus 通过率、裁定计数）。
- [ ] **nop-ai-shell 衔接裁定**（只裁定、不迁移）：在 design doc 或新 analysis 中记录 `BashSyntaxParser`（Tier 1/2/3 子集、手写）与本模块（全量 tree-sitter 树 + 语义层）的边界，给出"是否替换 / 是否双 parser 融合 / 是否保持并行"的结论与理由。若结论是迁移，明确写入 successor plan 路径。
- [ ] `docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md`：如路由或锚点变化则同步（若无需变更，显式记录"无需变更"）。

Exit Criteria:

- [ ] `language="bash"` 的 GraphQL 解析与语义层查询端到端可用（容器 bean 路径，非裸 `new`）
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` 退出码 0；既有 grammar 与 provider 测试零回归
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <this-file> --strict` 退出码 0（仅在收口时）
- [ ] `node ai-dev/tools/check-import-order.mjs` 对本模块新增文件 0 违规
- [ ] checkstyle（`-Pqa -pl nop-treesitter`）通过
- [ ] `ai-dev/logs/2026/10-04.md` 条目已更新（含实测数字与裁定结论）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 上游 tree-sitter-bash 已 vendored，provenance 与实测 grammar 指纹可复算
- [ ] `tree-sitter-bash-blob.bin` 在档，`TreeSitterBash` 与 provider 两条路径均可加载解析
- [ ] `BashScanner` 为 `scanner.c` 忠实翻译，门控对账单逐条有断言
- [ ] `BashCorpusTest` ≥95%，逐条分歧有 root cause + C-oracle 对照；既有 9 grammar 零回归
- [ ] 语义层 6 项能力全部落地，各有能力对应的正例 + 负例测试；不可证明路径显式失败
- [ ] GraphQL 端到端路径（容器 bean）验证通过；接线证据已记录
- [ ] `scan-hollow-implementations --module nop-treesitter --severity high` 退出码 0
- [ ] Anti-Hollow：closure audit 已验证 (a) 组件调用链运行时连通，(b) 端到端路径完整，(c) 无空方法体 / 静默跳过 / no-op 作为正常实现
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs（模块文档 / README / wiki / roadmap / design doc / source-anchors）已同步到 live baseline
- [ ] 独立子 agent closure-audit 已完成并按 Rule #27 将证据写入本文件 `Closure` 段
- [ ] `./mvnw test -pl nop-treesitter -am -T 1C` 退出码 0
- [ ] `check-doc-links --strict`、`check-plan-checklist --strict`、`check-import-order.mjs` 均退出码 0
- [ ] checkstyle（`-Pqa -pl nop-treesitter`）通过

## Deferred But Adjudicated

（起草时为空。执行中出现延期项时必须在此登记 Classification / Why Not Blocking Closure / Successor Required / Successor Path，不得只留在 execution list 里不勾选。）

## Non-Blocking Follow-ups

- bash `queries/highlights.scm` 接入。Classification: `out-of-scope improvement`（与 go/rust/c# 先例一致，当前无消费方）。
- 语义层的增量重解析专用路径（shell 脚本编辑场景）。Classification: `optimization candidate`（通用 `parseIncremental` 已可用）。
- shell 语义层的调用侧性能基准。Classification: `optimization candidate`（归 `docs/perf-tuning.md` 既有流程）。

## Risks And Rollback

| 风险 | 缓解 |
|---|---|
| 上游 tree-sitter-bash 的 corpus 含 `:skip` / `:language` 段或非标准格式，`CorpusUtil` 读不了 | Phase 0 先统计兼容性；必要时扩展 `CorpusUtil`（按 `Fix` 归类，属 in-scope 修复义务） |
| `scanner.c` 复杂（跨调用状态 / 转义表），手译出偏差 | 门控对账单 + 逐 token C-oracle 对照 + corpus ≥95% 硬门禁（对齐 rust/c# 先例） |
| 用户拿到的不是"完整 bash"（例如上游版本裁剪了某些构造） | Phase 0 记录实测 corpus 覆盖面；语义层的"不可证明"状态保证不完整时不静默通过 |
| 与 `nop-ai-shell/BashSyntaxParser` 双实现并存造成消费方困惑 | Phase 4 强制产出衔接裁定结论；未裁定不得标记 plan completed |
| 新增 `BUILTIN_BLOBS` 条目打破既有硬断言 | Phase 4 checklist 已显式列出 `TestTreeSitterProvider` 断言修正 |

## Closure

Status Note: （待填）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Audit Session: （待填）
- Evidence: （待填：逐条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + live code path / test name；`check-plan-checklist --strict`、`scan-hollow-implementations`、`check-doc-links --strict` 的退出码；Anti-Hollow 调用链追踪结果）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）

## Problem

用户需求是"AI Agent 执行 shell 命令时做转换和分析"。仓库内当前只有两个互不连通的部分：通用 tree-sitter 运行时（无 bash grammar）与 `nop-ai-shell` 的手写 bash 子集 parser（无 tree-sitter，且明确不追求 bash 兼容）。缺了 grammar，"分析"没有语法基础；缺了语义层，通用树对 agent 无用。

## Root Cause

不是缺陷，而是 roadmap 条目 18/19/20 的选型是"按语言逐个补 grammar"，未覆盖"agent 需要 shell 语义"这一消费维度；同时 bash 是唯一在 `~/sources/treesitter/grammars/` 参考副本中缺失的候选语言，因此比其他语言多一步上游获取。

## Documentation Follow-Up

- `docs-for-ai/03-modules/nop-treesitter.md`：内置语法列表、compat 行、新增 shell 语义层章节
- `nop-treesitter/README.md`、`nop-treesitter/docs/wiki.md`
- `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md`（新建）
- `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 21
- `docs-for-ai/INDEX.md` / `docs-for-ai/04-reference/source-anchors.md`（如路由/锚点变化）
