# 2026-10-04-1430-1 Bash grammar 基座 + shell 分析语义层（AI Agent 转换/分析）

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: 用户指示（2026-10-04）——"nop-treesitter 现在已经有 python 等解析，也需要增加 bash 的解析；找一个完整的 bash 做参考，用途就是 ai agent 执行的时候做转换和分析"；`ai-dev/backlog/nop-treesitter-roadmap.md` 条目 21；上游 `tree-sitter/tree-sitter-bash`（**当前不在** `~/sources/treesitter/grammars/`）；语义层参考实现 `~/ai/codex`（`codex-rs/shell-command/src/{bash.rs,parse_command.rs,shell_snapshot_literals.rs,command_safety/is_dangerous_command.rs}`、`codex-rs/apply-patch/src/invocation.rs`）；先例 plan `ai-dev/plans/nop-treesitter/2026-09-28-2400-1-rust-grammar-blob.md`、`2026-09-29-0100-1-csharp-grammar-blob.md`
> Related: `ai-dev/design/nop-ai-shell/04-bash-syntax.md`、`ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md`、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java`
> 审查记录: 经两轮独立子 agent 对抗性审查（R1 `ses_efa5f4c70ffedKiRHSX9bTmcL6`、R2 `ses_efa5f4c67ffex4At0yLsCT8Us0`），本版已吸收全部 Blocker 与 Major
> Phase 0 实测回写（2026-10-04，消除草案全部预估数字）:
>   - 上游 = `tree-sitter/tree-sitter-bash` **v0.25.1** / `a06c2e4415e9bc0346c6b86d401879ffb44058f7`（与 codex 解析到的 0.25.1 同代，草案的 `a06c2e4…` 线索成立）
>   - 指纹：`LANGUAGE_VERSION=15` `STATE_COUNT=7571` `LARGE_STATE_COUNT=523` `SYMBOL_COUNT=280` `ALIAS_COUNT=0` `TOKEN_COUNT=174` `EXTERNAL_TOKEN_COUNT=29` `FIELD_COUNT=19` `MAX_ALIAS_SEQUENCE_LENGTH=8` **`MAX_RESERVED_WORD_SET_SIZE=0`** `PRODUCTION_ID_COUNT=159` `SUPERTYPE_COUNT=1`
>   - blob **1,280,029 bytes**（`Ts2Java` 报 7571 states / 280 symbols）
>   - corpus **5 文件 / 100 section**，**无 CRLF fixture**（`crlf.txt` 标题带 CRLF 但字节里无 `
`，`.gitattributes` 强制 `eol=lf`）、**0** 处 `-{3,}` 出现在 input 段、**0** 处 `:skip` / `:language`
>   - oracle：`tree-sitter` CLI **0.25.10**（与 `parser.c` 生成器同版本），`tree-sitter parse` 可用
>   - **`MAX_RESERVED_WORD_SET_SIZE=0` 推翻 Phase 2 的猜测**：bash **没有** reserved words（`grammar.json` 的 `reserved` 为 `{}`），不落在 go 先例那个高风险形状上，`Language.decode` 的 reserved-word 校验不需要 en route 修复
>   - `scanner.c` **1217 行 / 29 token**（C# 为 527/13）；`ts_lex_modes` 中 `(TSStateId)(-1)` 模式数 = **0**（bash 不触发 item 19 的 no-lookahead 机制）
>   - 外部 token **不按子系统连续**：heredoc 族是 0-4 **加** 23-25，`${...}` 运算符族是 17-22；**序号 25 是陷阱**（`parser.c` 名 `heredoc_redirect_token1` = `scanner.c` 的 `NEWLINE`），误读会越界
>   - 命名节点 **62**（含 3 个 `_` 前缀隐藏 supertype）、匿名 token **122**；`FIELD_COUNT=19` 的 field→node 映射见 analysis
>   - `herestring_redirect` 是独立节点，`<<<` 是匿名 token **不经外部 scanner**（Phase 5 的问题由此裁定）
>   - corpus 结果 **100/100 = 100%，0 条裁定节**（Phase 2 的 95% 门禁与 C-oracle 对照均未被需要）

> **命名约定偏离声明**：guide Rule #21 要求 `NN-<描述>.md`，本目录 21 个历史计划全部使用 `YYYY-MM-DD-HHMM-N-<slug>.md`。本计划沿用目录既有惯例（Rule #20：历史计划默认视为历史记录，不因规范演进回写）。

## Purpose

让 nop-treesitter 具备 **bash 语法**能力，并把"AI Agent 执行 shell 命令前的转换与分析"落成可复用、可验证、**类型级不可误用**的公共 API：把一段 shell 脚本转换为结构化命令序列（可证明 / 不可证明显式二分）、提取 heredoc、标记动态构造点、抽取字面量文件路径、生成结构化摘要。

## Current Baseline

**已成立的事实**

- nop-treesitter 是纯 Java tree-sitter 运行时（GLR + 图结构栈 + 外部 scanner VM + query 引擎 + 增量重解析），已交付 9 个 grammar blob：`json` / `java` / `javascript` / `typescript` / `tsx` / `python` / `go` / `rust` / `c-sharp`（`src/main/resources/grammars/<name>/tree-sitter-<name>-blob.bin`）。
- grammar 装配路径固定并已被 go / rust / c-sharp 三个先例验证：`Ts2Java`（`io.nop.treesitter.codegen.Ts2Java`，从 vendored `src/parser.c` 抽表 → blob 格式 v4）→ `compat/TreeSitter<Lang>` 适配器（`static { LANGUAGE.setExternalScannerFactory(...) }` 静态单例）→ `corpus/<Lang>CorpusTest`（上游 `test/corpus/*.txt` 全量跑，≥95% 硬门禁 + `ADJUDICATED` 逐条 root cause + C-oracle 对照）。生成命令形态见 rust 计划：先 `./mvnw -pl nop-treesitter compile`，再 `java -cp nop-treesitter/target/classes io.nop.treesitter.codegen.Ts2Java <vendored parser.c> <out-blob>`。
- 已有外部 scanner 手译先例：`PythonScanner`（12 token）、`RustScanner`（11 token）、`CSharpScanner`（13 token），各配 `*ScannerIdiomsTest` 保真专项。`TreeSitterPython` / `TreeSitterRust` / `TreeSitterCSharp` 三者均为 compat 层**静态单例**。
- provider 路径独立于 compat：`src/main/java/io/nop/treesitter/provider/DefaultTreeSitterLanguageProvider.java` 的 `BUILTIN_BLOBS`（第 28-34 行）只注册 **6** 个（json / java / javascript / python / typescript / tsx）；`loadLanguage`（第 62-76 行）对未注册名字返回 `null`；python 分支单独 `language.setExternalScannerFactory(PythonScanner::new)`。`go` / `rust` / `c-sharp` **不在** `BUILTIN_BLOBS`，只能经 compat 类触达。
- GraphQL 门面 `io.nop.treesitter.biz.TreeSitterBizModel`（`@BizQuery` 解析入口 `parseTreeSitter`），bean 注册在**唯一**的 `src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml`（两条：bean id `nopTreeSitterLanguageProvider` + `io.nop.treesitter.biz.TreeSitterBizModel`）。Nop IoC **无注解扫描**，新增门面必须改这个文件。
- **primary API 缺口（已确认，本计划必须补）**：`io.nop.treesitter.TSNode`（`TSNode.java:19-207`）的 public 面只有 `symbol / effectiveSymbol / type / named / isVisible / isExtra / startByte / endByte / cursor / parent / nextSibling / childCount / namedChildCount / child(int) / namedChild(int)`——**没有 `hasError()`，没有按 field 名取子节点**。`hasError()` 与 `getChildByFieldName(String)` 目前只存在于 compat 层（`compat/TSNode.java:76,139`），field 导航只存在于 `cursor/TSTreeCursor` 的 `currentFieldName()` / `gotoChildByFieldName(String)`。
- **query 引擎缺口（已确认）**：`query/TSQueryParser.java:83,199` 对 anchor `.` 直接抛 `anchor '.' is not supported by this runtime`；`TSQueryParser.java:234` 只支持 `#eq?` / `#match?`，**不支持 `#any-of?`**。
- `TestTreeSitterProvider.java` 有三处会因新增内置语法而失效：第 36 行 `Set.of("json","java","javascript","python","typescript","tsx")` 循环、第 40 行 `assertEquals(7, provider.languageNames().size())`、第 41 行注释 `"six built-ins plus the test-classpath custom provider"`；`DefaultTreeSitterLanguageProvider` 类 Javadoc 第 16-17 行也写 `"the six shipped grammars"`。
- `nop-treesitter/pom.xml` 的 `corpus-heap` profile（surefire `-Xmx2G -XX:+UseG1GC`，第 101-138 行）`activeByDefault=true`，corpus 测试**无需**额外堆参数；`nop-bom/pom.xml` 不含 nop-treesitter（`grep -c treesitter` = 0），**无需**登记 BOM。

**真正剩余的 gap**

1. **没有任何 bash grammar**。`~/sources/treesitter/grammars/` 只有 8 家（c-sharp / go / java / javascript / json / python / rust / typescript），**无 tree-sitter-bash**；`/Volumes/data/sources/treesitter/tree-sitter-ng/tree-sitter-bash` 只有 `build.gradle` + `src/main/c/org_treesitter_TreeSitterBash.c`（JNI binding），`/Volumes/data/sources/treesitter/seart-group-java-tree-sitter/tree-sitter-bash` 是**空目录**——两者都**不含** `parser.c` / `scanner.c` / `test/corpus`。第一步必须是获取上游源。
2. **bash scanner 规模远超既有先例**（据 `/Volumes/data/sources/treesitter/gotreesitter/grammars/bash_scanner.go:9-40` 与 `bash_external_lex_states_gen.go:5`，其头部注释指向上游 parser.c commit `a06c2e4415e9bc0346c6b86d401879ffb44058f7`）：**29 个外部 token**（`HEREDOC_START` … `ERROR_RECOVERY`），是 C#（13）的两倍以上；scanner 形态是单一巨型 `scan()` + 多个共享 `goto` 标签 + 堆上可增长 heredoc 栈 + 变长 serialize（含溢出 `return 0` 守卫）+ 大量零宽 token。`check-oversized-files.mjs` 阈值 WARN 500 / **ERROR 700**（当前 `nop-treesitter` 已有 3 个 pre-existing ERROR），忠实翻译极可能突破 700 行。**以上数字必须在 Phase 0 实测复核后才可写入实现计划。**
3. **没有面向 shell 的语义层**。把通用树翻译成"这条 shell 命令会做什么"的能力在仓库内不存在。
4. **primary API 缺 `hasError()` / field 访问**（见上）。语义层的 fail-closed 安全门与 heredoc 的 field 定位都依赖这两个能力，走 primary API 拿不到。
5. **已存在一个手写 bash 子集解析器，但不在本模块**：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java`（595 行，是 `io.nop.ai.shell.parser` 包内唯一文件；模块 `src/main` 共 4508 行，含测试 9213 行）。设计文档 `ai-dev/design/nop-ai-shell/04-bash-syntax.md`（1838 行，表达式树模型）与 `ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md`（490 行，Tier 1/2/3 支持矩阵）。该 spec 的四条设计原则原文是"覆盖 LLM 常见输出 / 渐进式支持 / 安全优先 / 与现有 parser 对齐"（spec 第 12-14 行）——**没有**"不追求 Bash 兼容性"这类表述。两者尚未做过任何对接裁定。

**参考实现的可借鉴范围裁定（codex，R1/R2 已逐行核对）**

`~/ai/codex` 用 `tree-sitter-bash`（crates.io 声明 `"0.25"`，`Cargo.lock` 解析为 `0.25.1`，registry 来源、无 `[patch]`、`patches/` 内无 tree-sitter 相关补丁）。可借鉴的是**行为契约**，不是完整性——它是刻意的 fail-closed 白名单：

- **能力 1 参考：字面量命令序列拆分**（`bash.rs:29-98` `try_parse_word_only_commands_sequence`）。`ALLOWED_KINDS`（`bash.rs:36-50`）是**11 项**正向白名单：`program / list / pipeline / command / command_name / word / string / string_content / raw_string / number / concatenation`；`ALLOWED_PUNCT_TOKENS`（`bash.rs:52`）是 6 项匿名 token：`&& || ; | " '`。全树 DFS，**任何**白名单外的命名节点或匿名 token 立即 `None`；`root.has_error()` 为真立即 `None`（`bash.rs:30`）。词法级判据有三个函数：`is_literal_word_or_number`（`bash.rs:260-274`：无 named child + 不以 `=` 开头 + 不含 `{ } * ? [ ] \ ~ ^ # $ \``）、`parse_double_quoted_string`（`bash.rs:276-301`：全部 named child 必须是 `string_content`，且不得含 `\` 对 `$ \` " \\ \n` 的转义）、`parse_plain_command_from_node`（`bash.rs:162-218`：child 仅限 `command_name / word / number / string / raw_string / concatenation`）。
- **能力 2 参考：字面量收割**（`bash.rs:136-160` `parse_shell_lc_literal_commands`）。仍要求 `!root.has_error()`（`bash.rs:140`），但遍历**全部** `command` 节点（穿透控制流与替换）。其 `parse_literal_command_from_node`（`bash.rs:220-239`）在遇到非字面量 child 时是**跳过并继续**（`else if found_command_name && let Some(word) = ...` 无 else 分支），不是截断——例如 `rm -rf $TARGET /data` 得到 `["rm","-rf","/data"]`。函数文档注释明确写"suitable for identifying dangerous literal commands, but **must not be used to prove that a command is safe**"（`bash.rs:129-135`），且可见性是 `pub(crate)` 而非 `pub`。
- **能力 3 参考：heredoc 提取**（`apply-patch/src/invocation.rs:341-386`）。用带 anchor `.` 的 query 表达"heredoc 语句是唯一顶层语句"，谓词含 `#any-of?`；是唯一**不做** `has_error()` 门控的路径。依赖 field 名 `body:` / `redirect:` / `name:` / `argument:`。负例集见 `invocation.rs:719-787`。
- **能力 4 参考：分片解码**（`shell_snapshot_literals.rs`，715 行，唯一不走 `has_error()` 的解码器）——命令替换（`$()` 与反引号是**同一个** `command_substitution` 节点）、heredoc 按 expansion 切段、`concatenation` 逐 part 递归（`bash.rs:248-255`，任一 part 非字面量即整体 `None`）。
- **消费方**：审批/execpolicy 拆分（`core/src/exec_policy.rs:880`，解析失败即整条当不透明命令）、危险命令检测（`command_safety/is_dangerous_command.rs:66`，深度上限 8）、命令摘要与路径抽取（`parse_command.rs:1997-2123`）、apply_patch handler（`core/src/tools/handlers/apply_patch.rs:514`）。
- **测试形态**：无 insta snapshot、无 `.sh` fixture，全部内联字符串断言；`shell_snapshot_tests.rs` 与真实 `/bin/bash`、`/bin/zsh` 做差分测试。对抗样例见 `is_dangerous_command.rs:272-290`（`printf x | rm -rf /tmp/x`、`if test -d /tmp/example; then rm --force /tmp/example; fi`、`rm -rf "$TARGET" >/dev/null`、`for target in /tmp/a /tmp/b; do rm -r -f "$target"; done`、`echo "$(rm -rf /tmp/example)"`、`bash -c 'rm -rf /tmp/example'`、`trap 'rm -rf /tmp/example' EXIT`）。

**已确认的 owner-doc drift（本计划必须收敛，Rule #16：不得降级为 non-blocking）**

| 文件 | 现状 | live 事实 |
| --- | --- | --- |
| `docs-for-ai/03-modules/nop-treesitter.md:14` | 内置语法列 9 个（含 go/rust/c-sharp） | `BUILTIN_BLOBS` 只有 6 个；go/rust/c-sharp 只能经 compat 触达 |
| 同上 `:39` | bean id `treeSitterLanguageProvider` | 实际 `nopTreeSitterLanguageProvider` |
| 同上 `:12,60` | "GraphQL `parseTreeSitter` action" | `TreeSitterBizModel` 用的是 `@BizQuery`；平台无 "action" 概念 |
| `docs-for-ai/04-reference/source-anchors.md` TS-002 | "**六**内置语法 blob" | 加 bash 后变七——**必然变更**，不是条件式 |
| 同上 TS-003 | bean id `treeSitterLanguageProvider` | 同上 drift |
| `nop-treesitter/README.md` | "上述**六个**内置语法名" | 加 bash 后必须改 |
| `nop-treesitter/docs/wiki.md` FAQ | 内置 json/java/javascript/typescript/tsx（5 个，漏 python） | 既有 drift |

## Goals

- **G1**：vendored `tree-sitter-bash`（`parser.c` + `scanner.c` + `test/corpus/` + provenance），`Ts2Java` 产出 `grammars/bash/tree-sitter-bash-blob.bin`。
- **G2**：`BashScanner` —— `scanner.c` 忠实翻译；`compat/TreeSitterBash` 静态接线 scanner 工厂。
- **G3**：`bash` 进入 `BUILTIN_BLOBS`（NopIoC / GraphQL 可解析），provider 路径独立可解析含 heredoc 的脚本；相关既有断言与注释同步修正。
- **G4**：`BashCorpusTest` 上游 corpus ≥95%，逐条分歧登记 root cause + C-oracle 对照；`BashScannerIdiomsTest` 保真专项全绿。
- **G5**：primary API 补齐 `hasError()` 与按 field 名取子节点，使语义层不依赖 compat 层即可实现 fail-closed 门与 field 定位。
- **G6**：shell 语义层公共 API 6 项能力：能力 1 证明型拆分 / 能力 2 字面量收割 / 能力 3 heredoc 提取 / 能力 4 动态构造标记 / 能力 5 字面量路径抽取 / 能力 6 结构化摘要。**能力 1 与能力 2 的返回类型在类型系统层面不可互换**；未能证明时返回显式"不可证明"状态，禁止静默返回空结果或降级为原文。
- **G7**：语义层经 GraphQL `@BizQuery` 暴露，容器 bean 路径可用。
- **G8**：模块文档 / README / wiki / roadmap / owner-doc drift 全部收敛到 live baseline。

## Non-Goals

- **不做 shell 执行、sandbox、审批策略裁定**。"这条命令危险不危险"是 agent guardrail 的职责；本模块只提供判定所需的结构化事实与"可证明 / 不可证明"状态。
- **不做 shell 运行时语义求值**：参数展开、算术展开、进程替换、命令替换的**运行时值一律不求值**（codex 亦只标记不求值）。
- **不做 zsh / fish / powershell 语法**。codex 用 bash grammar 兼解 zsh（`bash.rs:268-272` 注释说明拒绝 `=`/`^` 是为 zsh），本模块不复制该做法，也不承诺 zsh 语义。
- **不改 `BashSyntaxParser` / 不迁移 `nop-ai-shell`**。只产出衔接裁定结论（Phase 7）。
- **不做 query / highlights `.scm`**（与 go/rust/c-sharp 先例一致：无当前消费方）。
- **不改 query 引擎**（不加 anchor、不加 `#any-of?`）。因此 heredoc 提取**必须**走节点遍历路线，理由见 Phase 0 裁定 4。
- **除 Phase 3（primary API 补齐，Rule #17 显式授权）外不改运行时行为**；其他例外仅限 corpus <95% 或 blob/scanner 装配层缺陷的修复义务（与 rust/c# 先例同）。

## Scope

### In Scope

- 上游 grammar 获取、vendoring、provenance、指纹实测、节点/field 契约清单
- blob 生成、`BashScanner`、`TreeSitterBash`、`BUILTIN_BLOBS` 注册与既有断言修正
- 上游 corpus 测试 + scanner 保真专项 + oracle 可用性验证
- primary `TSNode` 的 `hasError()` 与 field 导航补齐
- `io.nop.treesitter.shell` 语义层公共 API + 单元/端到端测试
- GraphQL `@BizQuery` 暴露 + bean 注册
- 契约 analysis、design doc、模块文档、README、wiki、roadmap、owner-doc drift 收敛、日志

### Out Of Scope

- `nop-ai-shell` 的 parser 替换或双 parser 融合
- 任何 shell 执行 / 权限 / 沙箱代码
- zsh、powershell、fish 等其他 shell
- query 引擎能力扩展（anchor / `#any-of?` / 量词）
- 增量重解析在 shell 场景的专门优化（通用 `parseIncremental` 已可用）
- 性能调优（若 corpus 跑不动先修正确性；perf 归 `nop-treesitter/docs/perf-tuning.md` 既有流程）

## Execution Plan

### Phase 0 - 上游获取、指纹实测与契约清单

Status: completed
Targets: `~/sources/treesitter/grammars/tree-sitter-bash/`（新建，只读参考副本）、`nop-treesitter/src/test/resources/upstream/grammars/tree-sitter-bash/`、`ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md`（新）、`ai-dev/design/nop-treesitter/`（新目录 + `README.md`）

- Item Types: `Decision`、`Proof`

- [x] **获取上游 grammar 源**。路径优先级：① clone `tree-sitter/tree-sitter-bash` 到与 codex 同代的 v0.25.x（`a06c2e4415e9bc0346c6b86d401879ffb44058f7` 是 gotreesitter 记录的 parser.c 来源 commit，作为定位线索而非必须匹配；记录实际 tag + commit SHA）；② 退路 crates.io `tree-sitter-bash` crate 包（须先验证是否含 `test/corpus`，不含则仍需 ①）。
- [x] **实测 grammar 指纹并写入 analysis**：`LANGUAGE_VERSION`、`STATE_COUNT`、`LARGE_STATE_COUNT`、`SYMBOL_COUNT`、`TOKEN_COUNT`、`EXTERNAL_TOKEN_COUNT`、`FIELD_COUNT`。**禁止沿用本 plan 或任何对话中的预估数字**（本 plan Baseline 中的 29 是二手来源）。
- [x] **导出节点名 / field 名契约清单**（analysis 必交付，Phase 4-6 依赖它，缺此项不得开工）：① 外部 token 完整枚举（名 + 序号 + 触发条件）；② 语义层依赖的命名节点全清单（至少覆盖 `program` / `list` / `pipeline` / `command` / `command_name` / `word` / `number` / `string` / `string_content` / `raw_string` / `concatenation` / `command_substitution` / `process_substitution` / `simple_expansion` / `expansion` / `arithmetic_expansion` / `variable_assignment` / `declaration_command` / `redirected_statement` / `heredoc_redirect` / `heredoc_start` / `heredoc_body` / `heredoc_end` / `file_redirect` / `file_descriptor`）；③ **field 名 → id 映射表**（`FIELD_COUNT` 只是数字，不含映射；能力 3 必需 `body:` / `redirect:` / `name:` / `argument:`）；④ 匿名 token 清单。清单须与 vendored `parser.c` / `node-types.json` 可逐条复算。
- [x] vendored `src/parser.c`、`src/scanner.c`、`test/corpus/*.txt`（全量）、`PROVENANCE.md`。**注意**：7 个 vendored 目录中只有 `tree-sitter-typescript/` 有 `PROVENANCE.md`，且其内容是 "commit-less snapshot, no local edits"，与"精确 tag + commit SHA"相反——本计划**新规范**化 provenance（记录 tag/SHA/license/生成器版本/"Never modified"），并**显式裁定是否回填其余 6 个目录**（推荐：回填，因为它们同样是第三方 MIT 代码）。
- [x] **验证 oracle 可用性**：`tree-sitter --version` 存在且为 0.25.x。**`_tmp/rust-oracle/` 不可用**（`_tmp/` 被 `.gitignore` 忽略、目录已不存在，且 c# 计划 Closure 已明确"自建临时比对器行为与官方不符，已弃用其对照结论"）。**官方 tree-sitter CLI corpus 实跑是唯一合法 C-oracle 主路径**；若需自建比对器，必须作为可提交的脚本 + 用法交付，不得只留在 `_tmp/`。
- [x] **corpus 兼容性实测**（`CorpusUtil` 用 `Files.readAllLines`，会**剥离行终止符**）：① 上游 corpus 是否含 `crlf.txt` 类 CRLF fixture（`\r` 会被静默剥离，而 heredoc / `string_content` / `NEWLINE` 外部 token 对 `\r\n` 敏感）；② heredoc body 内是否出现 `---`（`CorpusUtil` 以 `-{3,}` 终止 input 段，会导致 input 提前截断）；③ `:skip` / `:language` 元数据段；④ 文件数与 section 总数。**section 下限断言值在实测后写死**，并据此在 Phase 2 写死 `externalTokenCount` 期望值。
- [x] **6 项契约裁定**（写入 `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md`，遵循 `ai-dev/design/00-design-writing-guide.md`：只写契约与判定，**禁写类名/方法签名/参数列表/伪代码**）：
  1. **语义层归属**。既有先例必须纳入调研：`nop-code/nop-code-lang-<lang>`（csharp/go/java/python/rust/typescript 共 6 个模块）已把"tree-sitter 之上的语言语义层 + 分析器"放在独立模块（如 `PythonLanguageAdapter` / `PythonCodeFileAnalyzer` + `_lang-python.beans.xml`），而非放进 nop-treesitter。候选：(a) `io.nop.treesitter.shell`（grammar 与语义同源）；(b) 新增 `nop-code-lang-bash` 风格模块。裁定须给出理由并说明为什么不违反"nop-treesitter 不得反向依赖消费方"。
  2. **"不可证明"的表达形态**：类型/状态设计；`hasError()` 为真时一律不可证明；不可证明状态**不得**被静默转成空集合。
  3. **能力 1 与能力 2 的分工与类型级隔离**。codex 用三重防护：文档注释 + `pub(crate)` 可见性 + 消费侧只把能力 1 用于审批。裁定须给出类型系统层面的隔离方案（推荐：能力 1 返回携带"已证明"标记的密封类型，能力 2 返回不带该语义且带显式"不可作安全证明"标记的类型，二者不可赋值互換），并要求存在一条**误用测试**（把能力 2 结果传给能力 1 消费入口 → 编译失败或运行时显式失败）。
  4. **heredoc 提取实现路径**：query 引擎明确不支持 anchor `.`（`TSQueryParser.java:83,199`）与 `#any-of?`（`TSQueryParser.java:234`），而 codex 的 heredoc 约束**正是**由首尾 anchor 表达。裁定：**节点遍历为唯一路线**；禁止为此扩展 query 引擎。若后续发现节点遍历无法等价表达，作为 successor plan 单独立项。
  5. **能力 1 的白名单是封闭正向集合还是可扩展**：推荐封闭集合（codex 的 11 项 `ALLOWED_KINDS` + 6 项 `ALLOWED_PUNCT_TOKENS` 为基线），并明确 `declaration_command` / `test_command` / `negated_command` / `command` 内的 `variable_assignment` **均在白名单外**（否则 `declare -x A=1 rm -rf /data` 会被误判为可证明）。
  6. **动态构造点枚举**：至少含 `command_substitution`（`$()` 与反引号是同一节点）/ `simple_expansion`（`$VAR`）/ `expansion`（`${VAR}`）/ `arithmetic_expansion` / `process_substitution` / `file_redirect` 与 `redirected_statement` / heredoc 三节点 / 后台 `&`（**无节点类型，只能标区间**）/ `variable_assignment` / **`concatenation`**（字面量与展开的混合片段，如 `pre"$X"post`；不打标记会使含展开的命令显示为"无动态构造点"，构成安全漏洞）。
- [x] **codex 行为契约清单化**：把 6 类行为逐条抄成"期望行为"清单（只抄行为与判定，不抄 Rust 代码），作为 Phase 4-6 的 Exit Criteria 依据。**必须修正的两处抄写错误**：能力 2 是"跳过非字面量 child 并继续"而非"截断"；能力 1 的词法级判据是三个函数而非单纯字符黑名单。
- [x] **`ai-dev/design/nop-treesitter/README.md`**（阅读顺序 / 文档结构 / 职责边界 / AGE owner-doc 声明，形态对齐 `ai-dev/design/nop-ai-shell/README.md`）+ 在 **`ai-dev/design/README.md`** 的 Subsystem Design 表登记新目录（design guide 强制项）。
- [x] **AGENTS.md 路由遵循**：Phase 3-6 触及 IoC 与 GraphQL → 须先读 `docs-for-ai/02-core-guides/ioc-and-config.md` 与 `docs-for-ai/02-core-guides/api-and-graphql.md`（`@BizQuery` / `@BizMutation` 术语、保留动作名）。plan 中统一用 `@BizQuery`，不使用"action"。

Exit Criteria:

- [x] `~/sources/treesitter/grammars/tree-sitter-bash/` 与 vendored 副本均存在且 `parser.c` byte 一致
- [x] analysis 文件包含四项指纹 + 外部 token 全枚举 + 节点名清单 + **field 名映射表** + 匿名 token 清单，且每项可与 vendored `parser.c` 复算一致
- [x] `tree-sitter --version` 实测记录在案（0.25.x），或明确记录不可用并同步调整 Phase 2 的 oracle 路径
- [x] corpus 兼容性四项实测结论记录在案；**section 下限与 `externalTokenCount` 期望值已写死并回写 Phase 1/2 的 checklist**
- [x] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` + `README.md` 存在，6 个裁定点均有明确结论；`ai-dev/design/README.md` 已登记
- [x] 本 Phase 实测数字已回写本 plan 的 Phase 1/2/3 描述与断言值（消除一切预估）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 对新增/修改文件 0 errors
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 1 - blob 生成、BashScanner、compat 与 provider 接线

Status: completed
Targets: `src/main/resources/grammars/bash/tree-sitter-bash-blob.bin`、`src/main/java/io/nop/treesitter/scanner/BashScanner.java`（新）、`src/main/java/io/nop/treesitter/compat/TreeSitterBash.java`（新）、`src/main/java/io/nop/treesitter/provider/DefaultTreeSitterLanguageProvider.java`、`src/test/java/io/nop/treesitter/biz/TestTreeSitterProvider.java`

- Item Types: `Feature`、`Fix`

- [x] **生成 blob 并记录实际输出**：先 `./mvnw -pl nop-treesitter compile`，再 `java -cp nop-treesitter/target/classes io.nop.treesitter.codegen.Ts2Java <vendored parser.c> nop-treesitter/src/main/resources/grammars/bash/tree-sitter-bash-blob.bin`。blob 字节数与 Phase 0 指纹表**分别记录、交叉列示**（二者不是同类量，不做"一致性校验"）。
- [x] **文件规模裁定**：忠实翻译的 `BashScanner.java` 极可能突破 `check-oversized-files.mjs` 的 700 行 ERROR 阈值（`nop-treesitter` 已有 3 个 pre-existing ERROR）。Phase 1 必须先裁定拆分策略（按 token 分组拆成 `BashScanner` + 若干协作类 / 或登记为已知例外并说明理由），并把裁定写入 design doc。
- [x] `BashScanner`：`scanner.c` 忠实翻译，枚举序与 Phase 0 实测的 `EXTERNAL_TOKEN_COUNT` 严格对齐。**主 scan 门控次序必须写成可逐条核对的对账单**（体例对齐 `CSharpScanner` 的 8 项门控清单），至少覆盖：
  - 每个外部 token 的触发条件与互斥关系；
  - `valid_symbols` 门控，特别是 error-recovery 态下多 token 同时 valid 时的 `return false` 语义，以及 `ERROR_RECOVERY` 门控在本 grammar 中的散布位置；
  - **零宽 token 契约**：无推进时必须 `return false`（译成 `true` 会零进度自旋）；
  - **`mark_end` 语义**：对齐 item 19 已修的 C 缺省语义（末次调用定 token 末尾，未调用取 return 位置）；
  - **宽字符分类口径**：C locale 语义，`iswspace/iswalpha/iswdigit/iswalnum/isdigit` 必须映射为 ASCII 判定，**不得**用 `Character.isWhitespace` 等全 Unicode 集合；特别注意上游故意写的 `iswspace(lookahead) && lookahead != '\n'` 这类"看似冗余"判定必须原样保留；
  - **跨调用状态与 serialize/deserialize**：堆上可增长 heredoc 栈的变长布局；**C 的序列化缓冲区溢出 `return 0`（放弃序列化）在 Java `ExternalScanner.serialize()` 返回 `byte[]`（无长度上限）下如何映射，必须在本 Phase 裁定并记录**（消费侧 `GLRParser` 直接 `versions[0].externalScannerState = externalScanner.serialize()`，无长度校验）。
- [x] `TreeSitterBash`：blob + scanner 工厂**静态接线**（形态对齐 `TreeSitterPython` / `TreeSitterRust` / `TreeSitterCSharp`）。
- [x] **provider 接线（Rule #23）**：`BUILTIN_BLOBS` 增加 `bash` 条目；`loadLanguage` 中按现有 python 分支形态接线 `BashScanner` 工厂，使 provider 路径能独立解析含 heredoc 的脚本、不依赖 compat 类。
- [x] **同步修正三处既有断言与注释**（否则 provider 路径的 bash 行为零覆盖）：`TestTreeSitterProvider.java:36` 的 `Set.of(...)` 循环加 `bash`；`:40` 的 `assertEquals(7, ...)` 改为新值；`:41` 注释"六个内置"改为新措辞；`DefaultTreeSitterLanguageProvider` 类 Javadoc `"the six shipped grammars"` 同步。
- [x] **测试（Rule #25）**：`BashScannerIdiomsTest` 覆盖门控对账单**每一条**的正反例（禁止"整体通过"式覆盖）；`TestTreeSitterProvider` 新增一条经 provider 解析**含 heredoc** 的脚本的断言（heredoc 是唯一必须走 scanner 的路径，可同时证明 `setExternalScannerFactory(BashScanner::new)` 真的接上，而不只是 blob 能加载）。

Exit Criteria:

- [x] 编译通过；`TreeSitterBash` 可加载并解析最小 bash 片段（`echo hi`、`ls -la | grep x`、`for i in a b; do echo $i; done`）
- [x] `BashScannerIdiomsTest` 全绿，且**每条对账单条目都有对应断言**（无"整体通过"式覆盖）
- [x] **接线验证**：provider 路径（容器 bean，非裸 `new`）解析含 heredoc 的脚本成功，且该断言能区分"scanner 工厂已接线"与"未接线"（Rule #23）
- [x] **无静默跳过**：任一未覆盖的外部 token 分支以显式异常 / 显式 `return false` 表达，不存在空分支或 placeholder 返回（Rule #24）
- [x] 文件规模裁定已落地，`check-oversized-files.mjs --module nop-treesitter` 未因本 Phase **新增** ERROR（pre-existing 3 个 ERROR 需在 Phase 7 前单独说明）
- [x] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` 与本 Phase 实现一致（无 drift）
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 2 - 上游 corpus 验证 ≥95%

Status: completed
Targets: `src/test/java/io/nop/treesitter/corpus/BashCorpusTest.java`（新）

- Item Types: `Proof`、`Fix`

- [x] `BashCorpusTest`：经 `TreeSitterBash` 加载（Rule #23 接线证据）+ `externalTokenCount` 断言（值取 Phase 0 实测）+ scanner 实例断言；跑 Phase 0 统计出的全量 corpus 文件；section 下限断言写死为 Phase 0 实测值。
- [x] 门禁：`pass * 100 >= sections.size() * 95` 且 `pass == sections.size() - adjudicated.size()`；每条 `ADJUDICATED` 必须写明 corpus 文件 + 标题 + root cause，并以**官方 tree-sitter CLI 0.25.x corpus 实跑**为 C-oracle 证据（Phase 0 已验证可用）。
- [x] **若 <95%**：定位 scanner / blob / runtime 缺陷并在同 Phase 修复，**不得豁免、不得下调门禁**（对齐 rust/c# 先例硬义务）。此类修复按 `Fix` 归类；若触及运行时行为，须同步评估 `docs-for-ai/03-modules/nop-treesitter.md` 已知偏差段。
- [x] **零回归判据数字化**：先记录基线 `mvnw test -pl nop-treesitter` 的 tests/failures 总数（rust 先例 414 / c# 先例 421 / 428 可作量级参照），Phase 2 结束时对比，9 个既有 grammar 的 corpus 结果不得变化。
- [x] 既有 scanner 的两处已知修复**无需重诊断**，但需在本 Phase 确认适用：(a) `ts_lex_modes` 的 `(TSStateId)(-1)` no-lookahead 状态机制——**bash 的此类模式实测为 0 处**（`grep -c "(uint16_t)-1" src/parser.c` = 0），该机制对 bash 不生效，无需覆盖；(b) `Language.decode` 对"带 reserved words 的 grammar"的校验——**实测 `MAX_RESERVED_WORD_SET_SIZE=0`、`grammar.json` 的 `reserved` 为 `{}`，bash 没有 reserved words**，因此不属该校验的高风险形状，**无需 en route 修复**。两项均因 Phase 0 实测而免实施，非跳过。

Exit Criteria:

- [x] **端到端验证**：vendored `parser.c` → `Ts2Java` → blob → `TreeSitterBash` → `BashScanner` → corpus sexp 全链路打通（Rule #22）
- [x] **接线验证**：`BashScanner` 确实被解析路径在运行时调用（不是仅注册未调用）（Rule #23）
- [x] pass rate ≥95%，逐条分歧有 root cause + C-oracle 对照
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 退出码 0，tests/failures 与基线对比无回归
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-treesitter --severity high` 退出码 0
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 3 - primary API 补齐（hasError + field 导航）

Status: completed
Targets: `src/main/java/io/nop/treesitter/TSNode.java`、`src/test/java/io/nop/treesitter/cursor/`（新测试）

- Item Types: `Feature`

> 本 Phase 是 Rule #17 显式授权的运行时变更。存在理由：语义层的 fail-closed 门（`hasError()`）与 heredoc 的 field 定位（`body:` / `redirect:` / `name:` / `argument:`）都必需，而 primary API（`TSParser.parse` 返回的树，也是 GraphQL `parseTreeSitter` 用的那条）当前**两者都没有**——只有 compat 层有。若不补，语义层只能建在 compat 树上，而 provider 路径返回 primary 树，Phase 4-6 的端到端验收将无法成立。

- [x] `TSNode` 补 `hasError()`：语义为"以该节点为根的子树中是否存在 `ERROR` / `MISSING` / `isExtra` 异常节点"，参照 `compat/TSNode.java:76-88` 的既有实现（compat 版本是从该节点向下遍历子节点）。**primary 版本必须覆盖 ERROR 子树内部**（compat 版本是否覆盖需在实现时核实并对齐，差异必须显式记录）。
- [x] `TSNode` 补按 field 名取子节点（参照 `compat/TSNode.java:139` 的 `getChildByFieldName`），底层复用 `cursor/TSTreeCursor` 已有的 field 导航能力，**不得**新写第二套 field 查找逻辑。
- [x] **对齐验证**：primary 与 compat 的 `hasError()` / field 取子节点在同一棵树上必须给出一致结果（`compat` 迁移层的存在意义就是"只换 import、行为不变"）。
- [x] **测试（Rule #25）**：`hasError()` 在无错树 / 含 `ERROR` 节点 / 含 `MISSING` 节点 / `ERROR` 深层嵌套 四类输入上的行为断言；field 导航在 `heredoc_redirect` 的 `redirect:` 与 `redirected_statement` 的 `body:` 上断言；primary vs compat 一致性断言；未知 field 名返回"无此子节点"而非抛异常或返回根自身。

Exit Criteria:

- [x] **接线验证**：`TSParser.parse(...)` 返回的 primary 树上可直接调用新增 API，且 Phase 4-6 的语义层实现**只依赖 primary API**（执行时须提供证据证明未使用 compat 类）（Rule #23）
- [x] **无静默跳过**：未知 field 名、`null` 入参等边界显式失败或显式返回"无"，不返回根节点冒充成功（Rule #24）
- [x] primary / compat 一致性测试全绿；`migration/JniEquivalenceTest` 等既有迁移测试零回归
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 退出码 0
- [x] `docs-for-ai/03-modules/nop-treesitter.md` 公共 API 章节已补这两个方法（Rule #17）
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 4 - 语义层 A：能力 1（证明型拆分）与能力 2（字面量收割）

Status: completed
Targets: `src/main/java/io/nop/treesitter/shell/`（新包，类名以 Phase 0 design doc 裁定为准）、`src/test/java/io/nop/treesitter/shell/`

- Item Types: `Feature`

- [x] **能力 1 — 证明型命令序列拆分**。按 Phase 0 裁定 5 的**封闭正向白名单**实现（基线：codex 的 11 项 `ALLOWED_KINDS` + 6 项 `ALLOWED_PUNCT_TOKENS`）。必须为**拒绝**（返回"不可证明"）且有测试的构造：子 shell `( )`、花括号 `{ }`、`file_redirect` / 重定向 `>` `<` `>>`、后台 `&`、`command_substitution`（`$()` 与反引号）、`simple_expansion`（`$VAR`）、`expansion`（`${VAR}`）、`process_substitution`（`<(...)`）、`arithmetic_expansion`（`$((...))`）、`if` / `for` / `while` / `case` / `function_definition`、`declaration_command`（`declare` / `export` / `local` / `readonly` / `typeset`）、`test_command`（`[[ ]]`）、`negated_command`（`!`）、`command` 内的 `variable_assignment`（`FOO=bar ls`）、heredoc 三节点、brace expansion、glob 元字符、`~`、`^`、`#`、`=`。
  **词法级判据必须逐条实现并各有一组断言**（这是最容易被实现成"只查字符"而误判的地方）：① `word` / `number` 必须无 named child；② 不以 `=` 开头；③ 不含 `{ } * ? [ ] \ ~ ^ # $ \`` 任一字符；④ 双引号串的全部 named child 必须是 `string_content`；⑤ 双引号串不得含 `\` 对 `$ \` " \\ \n` 的转义（双引号抑制 glob 但不抑制转义移除）；⑥ `concatenation` 逐 part 递归，任一 part 非字面量即整体判为不可证明。
  **回归红线**：`a"$X"b` 这类混合拼接**绝不能**被判为可证明 argv；`declare -x A=1 rm -rf /data` **绝不能**被判为可证明命令序列。
- [x] **能力 2 — 字面量收割**。仍要求 `!hasError()`；遍历**全部后代** `command` 节点（穿透 `if` / `for` / `while` / `case` / 管道 / `command_substitution`）。**必须先由 Phase 0 裁定非字面量 child 的处理语义**（codex 是"跳过并继续"，本 plan 初稿误写为"截断"）：候选 (a) 跳过并继续（codex 语义，会高估已知信息）、(b) 截断（低估）、(c) 整条命令标记为"部分字面量"（推荐，既不伪造 argv 也不丢信息）。
  **红线条数断言（Rule #22 anti-hollow）**：对抗样例必须断言**命中数量**而非仅"检出危险命令"，否则"只遍历直接子节点"的空壳实现会蒙混过关。例如 `bash -c 'rm -rf /tmp/example'` 必须断言恰好找到 2 个 `command`（外层 `bash` + 内层 `rm`）。
- [x] **能力 1 / 能力 2 的类型级隔离**（Phase 0 裁定 3）：二者的返回类型在类型系统层面不可互换；**必须存在一条误用测试**——把能力 2 的结果传给能力 1 的消费入口，编译失败或运行时显式失败。
- [x] **测试（Rule #25）**：每条白名单外构造各一组负例；每个词法判据各一组正反例；codex 对抗集全覆盖（`printf x | rm -rf /tmp/x`、`if test -d d; then rm --force d; fi`、`rm -rf "$TARGET" >/dev/null`、`for t in a b; do rm -r -f "$t"; done`、`echo "$(rm -rf x)"`、`bash -c 'rm -rf x'`、`trap 'rm -rf x' EXIT`）；负例集还须包含 codex 刻意验证的"格式错误脚本**不应**被误判"（`if then rm -rf /tmp/example`）。

Exit Criteria:

- [x] 能力 1 与能力 2 的行为与"期望行为清单"（Phase 0 交付）逐条对齐，每条有对应测试名
- [x] **端到端验证**：一段真实 agent 生成的复合脚本（fixture 固定，见 Phase 6）从 primary `TSParser.parse` 入口走到能力 1 / 能力 2 的结构化输出，断言输出含预期命令序列（Rule #22）
- [x] **接线验证**：语义层在运行时确实调用 `TSParser.parse` + `TreeSitterBash`，且**未**使用 compat 类（计数器 / 断言 / 调用链审查三选一给出证据）（Rule #23）
- [x] **无静默跳过**：每条不可证明路径返回显式状态；不存在"返回空列表当作没有命令"的路径（Rule #24）
- [x] 误用测试通过（能力 2 结果不能被当作能力 1 结果使用）
- [x] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` 与本 Phase 实现一致（无 drift）
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 5 - 语义层 B：能力 3（heredoc 提取）与能力 4（动态构造标记）

Status: completed
Targets: `src/main/java/io/nop/treesitter/shell/`（续）、`src/test/java/io/nop/treesitter/shell/`

- Item Types: `Feature`

- [x] **能力 3 — heredoc 提取（节点遍历路线，Phase 0 裁定 4）**。必须实现 codex query anchor 所表达的等价判定：
  - *直接形态*：根节点只有一个命名子节点；该子节点是 `redirected_statement`；其 `redirect:` field 唯一子节点是 `heredoc_redirect`；`heredoc_redirect` 恰有 3 个命名子节点且依次为 `heredoc_start` / `heredoc_body` / `heredoc_end`。
  - *`cd` 前置形态*：`redirected_statement` 的 `body:` field 是 `list`，该 `list` 恰含 2 个 `command` + 1 个字面量 `&&`（**分隔符必须是 `&&`，`;` / `|` / `||` 一律不匹配**）；第 1 个 `command` 的 `name:` 文本等于 `cd` 且恰有 1 个 `argument:`；第 2 个 `command` 的 `name:` 文本命中调用方传入的允许名集合。
  - delimiter 取自 `heredoc_start` 的**原文**；是否引号包裹（`'` / `"` / `\`）决定 body 是否按 expansion 切段——引号包裹时 body 为纯字面量。
  - **负例必须覆盖** codex `invocation.rs:719-787` 的全部 anchor 负例（`cd foo;` / `cd bar ||` / `cd bar |` / `echo foo &&` / `apply_patch foo <<'PATCH'` / `cd foo && cd bar &&` / `cd foo bar &&` / `echo foo; cd bar &&`），**并补充系统性负例**：`echo x && cat <<EOF`（heredoc 非唯一顶层语句）、任何在 heredoc 前后出现额外命令的形态。仅"找到第一个 `heredoc_redirect` 就返回"的空壳实现必须被这些负例判红。
  - 注意：herestring（`<<<`）在 codex 参考实现中**不与 `<<` / `<<-` 区分**（`heredoc_redirect` 的外部 token 只有 `HEREDOC_ARROW` / `HEREDOC_ARROW_DASH`）。本计划的处理方式必须在 Phase 0 裁定并记录，不得默认扩大契约。
- [x] **能力 4 — 动态构造标记**。对以下构造产出带**源码字节区间**与类型的标记列表（不求值）：`command_substitution`（`$()` 与反引号同一节点）、`simple_expansion`（`$VAR`）、`expansion`（`${VAR}`）、`arithmetic_expansion`、`process_substitution`、`file_redirect` / `redirected_statement`、heredoc 三节点、后台 `&`（**无节点类型，只能标区间**）、`variable_assignment`、**`concatenation`**（字面量与展开的混合片段）。`concatenation` 必须打标记——否则含展开的命令会显示为"无动态构造点"，构成安全漏洞而非仅仅不完整。
- [x] **测试（Rule #25）**：能力 3 的每种形态各有正例；负例覆盖 codex 全集 + 上述系统性负例；能力 4 对**每一个**枚举的构造各有一个标记断言，`concatenation` 与后台 `&` 单独断言（两者最容易漏）。

Exit Criteria:

- [x] 能力 3 / 能力 4 与"期望行为清单"逐条对齐，每条有对应测试名
- [x] **端到端验证**：含 heredoc + 命令替换 + `concatenation` + 后台 `&` 的 fixture 脚本，一次解析同时产出正确的 heredoc body 与完整动态标记集合（Rule #22）
- [x] **接线验证**：能力 3 / 能力 4 复用 Phase 4 已验证的解析路径，不新增旁路入口（Rule #23）
- [x] **无静默跳过**：heredoc 未命中、标记集合为空但脚本含已知动态构造等情形必须可区分，不允许"空集合"与"无动态构造"混淆（Rule #24）
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 6 - 语义层 C：能力 5（字面量路径抽取）、能力 6（结构化摘要）与 GraphQL 暴露

Status: completed
Targets: `src/main/java/io/nop/treesitter/shell/`（续）、`src/main/java/io/nop/treesitter/biz/`（新增 shell 门面，以 Phase 0 裁定 1 为准）、`src/main/resources/_vfs/nop/treesitter/beans/app-treesitter.beans.xml`、`src/test/resources/`（E2E fixture）

- Item Types: `Feature`

- [x] **能力 5 — 字面量文件路径抽取**。"什么算路径"必须先在 Phase 0 定义并在本 Phase 逐维度实现，六个维度：
  1. **来源命令的参数位置约定**：`read` / `cat` / `less` / `head` / `tail` / `grep` / `rg` / `find` / `ls` / `stat` / `wc` / `file` / `cp` / `mv` / `rm` / `mkdir` / `cd` / `diff` 等各自读路径的参数序号；`--` 之后视为路径。
  2. **字面量形态**：绝对路径（`/` 开头）/ 相对路径（`.` `..` 开头）/ 裸文件名；**排除**含 `~` 前缀（是展开）、含 glob 元字符、含未解引号的展开片段。
  3. **来源可信度标注**：必须标注来自能力 1（已证明）还是能力 2（未证明）——两者可信度不同，混同即为安全漏洞。
  4. **flag 排除**：`-` 开头 token 不作路径。
  5. **重定向目的地**：`file_redirect` 的 `destination:` 单独成一类（写路径，与读路径区分）。
  6. **不判定**：显式声明**不做**文件系统存在性检查（否则就不是"字面量抽取"）。
  codex 的对应逻辑（`parse_command.rs:1997-2123`）还包含"剥离 `head`/`tail`/`awk`/`wc` 等小格式化辅助命令以偏向主命令"的启发式——是否纳入本能力必须在 Phase 0 裁定。
- [x] **能力 6 — 结构化摘要**。字段清单与序列化形态必须在 Phase 0 裁定并在本 Phase 落地：至少含命令序列、连接符、是否含动态部分、证明状态。**"不可证明"必须作为摘要中的一等状态，而非降级为原始文本**——必须有一条测试能区分这两种输出（否则实现成 `summary != null ? summary : originalText` 也会通过）。
- [x] **GraphQL 暴露**：语义层经 `@BizQuery` 暴露（`@BizQuery` 而非"action"，遵循 `docs-for-ai/02-core-guides/api-and-graphql.md`）；错误走 `TreeSitterErrors` + `.param(...)`；动作名避开平台保留名（`findList` 等）。
- [x] **bean 注册**：Nop IoC 无注解扫描，新增门面**必须**在 `app-treesitter.beans.xml` 注册 `<bean>`，否则容器内不存在。Phase 0 裁定 1 若选择独立模块，则改为该模块自己的 `_vfs` beans 文件并在此登记。
- [x] **测试（Rule #25）**：能力 5 六维度各有断言（含"不做存在性检查"的显式断言）；能力 6 的"不可证明 ≠ 原文降级"断言；GraphQL `@BizQuery` 端到端断言。
- [x] **固定 E2E fixture**：`src/test/resources/` 下放一份具名的 agent 风格复合脚本 fixture（同时含 heredoc、管道、命令替换、`concatenation`、`for` 循环、变量前缀赋值、重定向），Phase 4/5/6 的端到端 Exit Criteria 共用它，使验收可复现。

Exit Criteria:

- [x] **端到端验证**：固定 fixture 从 `language="bash"` 的 GraphQL `@BizQuery` 入口（容器 bean 路径，非裸 `new`）一路走到能力 1-6 的结构化输出，断言含预期命令序列、heredoc body、完整动态标记集、路径候选、摘要状态（Rule #22）
- [x] **接线验证**：GraphQL 门面在容器中可解析（证明 beans.xml 已改）；门面确实调用语义层，语义层确实调用 `TSParser.parse`（Rule #23）
- [x] **无静默跳过**：能力 5 空结果、能力 6 不可证明、heredoc 未命中四类路径均可区分，不存在静默降级（Rule #24）
- [x] `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md` 与本 Phase 实现一致（无 drift）
- [x] `ai-dev/logs/2026/10-04.md` 条目已更新

### Phase 7 - 文档、owner-doc drift 收敛与 roadmap

Status: completed
Targets: `docs-for-ai/03-modules/nop-treesitter.md`、`docs-for-ai/04-reference/source-anchors.md`、`nop-treesitter/README.md`、`nop-treesitter/docs/wiki.md`、`ai-dev/backlog/nop-treesitter-roadmap.md`、`ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md`、`ai-dev/logs/2026/10-04.md`

- Item Types: `Fix`、`Decision`

- [x] **收敛 Current Baseline 表中列出的全部 owner-doc drift**（`Fix`，不得降级为 non-blocking）：模块文档内置语法列表区分"provider 注册的 6+N 个"与"blob 已交付的 9+N 个"（消除 6 vs 9 混淆）；模块文档与 `source-anchors.md` TS-003 的 bean id 改为 `nopTreeSitterLanguageProvider`；模块文档 `@BizQuery` 术语修正（去掉"action"）；`source-anchors.md` TS-002 的"六内置语法 blob"改为新值（**必然变更**）；`README.md` "六个内置语法名"改为新值；`nop-treesitter/docs/wiki.md` FAQ 补 python 并更新为新值。
- [x] 模块文档：新增 bash 行（blob 大小、corpus 通过率、裁定计数、scanner token 数）+ compat 行补 `TreeSitterBash` + 公共 API 补 Phase 3 两个方法 + 新增"shell 分析语义层"章节（6 项能力 + 可证明/不可证明边界 + 类型级隔离说明 + 消费方指引）。
- [x] `nop-treesitter/README.md` 用户指南补 bash 解析与语义层示例；`nop-treesitter/docs/wiki.md` 导航同步。
- [x] `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 21 `planned`→`done`，回填实测数字。
- [x] `check-oversized-files.mjs --module nop-treesitter` 的 3 个 pre-existing ERROR（`ParserCExtractor` 1714 / `GLRParser` 2074 / `Language` 1139 行）：本计划**未新增** ERROR 是硬要求；这 3 项作为独立治理项登记 successor（不得混入本计划悄悄修）。
- [x] **nop-ai-shell 衔接裁定**（`Decision`，只裁定不迁移）：在 design doc 或新 analysis 中记录 `BashSyntaxParser`（595 行、Tier 1/2/3 子集、手写）与本模块（全量 tree-sitter 树 + 语义层）的边界，比较"替换 / 双 parser 融合 / 并行"三条路的代价，给出结论与理由。若结论是迁移，明确写入 successor plan 路径。
- [x] `docs-for-ai/INDEX.md`：如路由变化则同步（若无需变更，显式记录"无需变更"）。

Exit Criteria:

- [x] Current Baseline 表列出的 7 条 owner-doc drift 全部收敛，`check-doc-links --strict` 退出码 0
- [x] roadmap 条目 21 状态与实测数字一致
- [x] 衔接裁定结论已落文档，且明确 successor 路径或明确无 successor
- [x] `ai-dev/logs/2026/10-04.md` 收口条目已写入（含实测数字、裁定结论、pre-existing 超大文件项的 successor 归属）
- [x] `./mvnw -Pqa -pl nop-treesitter verify -DskipTests` 退出码 0（`qa` profile = PMD + SpotBugs，两者按设计 `failOnViolation=false` / `failOnError=false`）
- [x] checkstyle 现状已记录：直接跑 `checkstyle:check` 对本模块**全量失败**（4678 条 / 84 文件），含本计划从未触碰的既有文件；属 pre-existing debt，不作为本 plan 的 gate（见 `Deferred But Adjudicated`）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 上游 tree-sitter-bash 已 vendored，provenance 与实测 grammar 指纹可复算
- [x] `tree-sitter-bash-blob.bin` 在档；compat 与 provider 两条路径均可加载解析含 heredoc 的脚本
- [x] `BashScanner` 为 `scanner.c` 忠实翻译，门控对账单逐条有断言
- [x] `BashCorpusTest` ≥95%，逐条分歧有 root cause + 官方 CLI C-oracle 对照；既有 9 grammar 零回归
- [x] primary `TSNode.hasError()` 与 field 导航已补齐，primary/compat 一致性测试全绿
- [x] 语义层 6 项能力全部落地，各有能力对应的正例 + 负例测试；能力 1 / 能力 2 类型级不可互换且有误用测试
- [x] GraphQL `@BizQuery` 端到端路径（容器 bean，固定 fixture）验证通过
- [x] Anti-Hollow：closure audit 已验证 (a) 组件调用链运行时连通，(b) 端到端路径从入口到输出完整，(c) 无空方法体 / 静默跳过 / no-op 作为正常实现
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-treesitter --severity high` 退出码 0
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（尤其：Current Baseline 表列出的 7 条 owner-doc drift）
- [x] 受影响 owner docs（模块文档 / `source-anchors` / README / wiki / roadmap / design doc / analysis）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并按 Rule #27 将证据写入本文件 `Closure` 段
- [x] `./mvnw compile -pl nop-treesitter` 退出码 0
- [x] `./mvnw test -pl nop-treesitter -am -T 1C` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 对**本计划新增/修改文件 0 errors**（全仓退出码为 1，剩余 error 全在本计划之外，见 `Deferred But Adjudicated`；**closure audit 查出本计划自身引入的 3 条并已全部修复**）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-file> --strict` 退出码 0
- [x] `node ai-dev/tools/check-import-order.mjs` 对本模块新增文件 0 违规
- [x] `node ai-dev/tools/check-error-param-consistency.mjs` 对本模块新增错误码 0 违规
- [x] `./mvnw -Pqa -pl nop-treesitter verify -DskipTests` 退出码 0（`qa` profile = PMD + SpotBugs，两者按设计 `failOnViolation=false` / `failOnError=false`）
- [x] checkstyle 现状已记录：直接跑 `checkstyle:check` 对本模块**全量失败**（4678 条 / 84 文件），含本计划从未触碰的既有文件；属 pre-existing debt，不作为本 plan 的 gate（见 `Deferred But Adjudicated`）

## Deferred But Adjudicated

### `GLRParser` 错误恢复零进展活锁（`echo ${x!y}` 抛异常，C runtime 恢复）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属**共享 runtime 代码**（`GLRParser.handleError` / skip-merge 的 100 轮无进展守卫），本 plan 的 Non-Goals 明确排除 Phase 3 之外的运行时行为变更；且已用对照实验证明 `BashScanner` 本身忠实（不挂 scanner / 挂 always-false stub 均正常，只有忠实 scanner 触发；`expansion_word:` 区域上游同样没有 `in_error_recovery` 门控）。它是 **confirmed live defect** 而非优化项，因此**不允许留在 non-blocking 区**：已建独立 plan 承接，模块文档已披露，本 plan 的测试**刻意不把抛异常行为钉死**，以便 successor 修改时不必对抗测试。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/nop-treesitter/2026-10-04-1900-1-glr-recovery-livelock-guard.md`

### 其余 7 个 vendored grammar 目录的 provenance 回填（含 python/rust 缺失 `scanner.c`）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 计划内要求的裁定（「是否回填」）已完成并落文档：裁定为**不回填**，因为 `tree-sitter-python` / `tree-sitter-rust` 只 vendored `parser.c`、**缺 `scanner.c`**（其手译 scanner 的源文件不在仓库内，无法附上诚实的 SHA），且本地参考副本未 pin、HEAD 已漂移。改为模块级 `nop-treesitter/src/test/resources/upstream/grammars/PROVENANCE.md` 记录策略 + 实测状态 + 两个 blocker——**已确认的 drift 已收敛为如实记录**，而不是留白。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/nop-treesitter/2026-10-04-1830-1-vendored-grammar-provenance-backfill.md`

### `checkstyle:check` 对本模块的全量失败（4678 条 / 84 文件）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: **pre-existing**，覆盖从未被本计划触碰的既有文件（`CSharpScanner.java` / `PythonScanner.java` / `RustScanner.java` / 迁移层的 `TSNode` 等），治理它需要为整个模块补 Javadoc / final 参数 / 80 列，属独立的可读性治理项。计划原文的 gate 措辞（「checkstyle（`-Pqa -pl nop-treesitter`）通过」）本身有误——`qa` profile 实为 PMD + SpotBugs，checkstyle 不在其中；已把 gate 改为可执行的 `qa` profile 并记录 checkstyle 现状。
- Successor Required: `yes`
- Successor Path: 待登记（模块级代码规范治理；与本计划无因果关系）

## Non-Blocking Follow-ups

- **bash `queries/highlights.scm` 接入**
  - Classification: `out-of-scope improvement`
  - Why Not Blocking Closure: 与 go/rust/c-sharp 先例一致——仓库内无任何 shell 高亮消费方；本计划的使用场景是 agent 的转换与分析，不是渲染。
  - Successor Required: `no`
  - Successor Path: —
- **语义层的增量重解析专用路径（shell 脚本编辑场景）**
  - Classification: `optimization candidate`
  - Why Not Blocking Closure: 通用 `TSParser.parseIncremental` 已可用且与全量重解析字节等价；shell 场景无性能证据表明需要专用路径。
  - Successor Required: `no`（出现实测性能问题则另立）
  - Successor Path: —
- **语义层调用侧性能基准**
  - Classification: `optimization candidate`
  - Why Not Blocking Closure: perf 归 `nop-treesitter/docs/perf-tuning.md` 既有流程；本计划不引入新的热路径结构。
  - Successor Required: `no`
  - Successor Path: —
- **`check-doc-links.mjs --strict` 的 4 条 pre-existing error**
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 全部在本计划之外——若干条在已 `completed` 的 plan `2307-eql-live-defect-and-dead-surface-remediation.md`（按 Rule #20 历史计划默认不回写）、在另一 agent 进行中的 nop-message mission（其 audits 目录尚未建立）、以及在 `ai-dev/plans/nop-bytecode/` 下。**closure audit 期间本计划自身引入的 3 条（docs-for-ai 引用 ai-dev 的越界链接 + 1 条相对路径写错）已全部修复**。本计划新增/修改文件 **0 error**（已按文件逐条核对）。
  - Successor Required: `no`（其中 nop-message 那条随该 mission 自然消解）
  - Successor Path: —
- **`check-oversized-files.mjs` 的 3 个 pre-existing ERROR（`ParserCExtractor` / `GLRParser` / `Language`）拆分治理**
  - Classification: `out-of-scope improvement`
  - Why Not Blocking Closure: 属既有技术债，与 bash 支持无因果关系；本计划的硬要求仅为"不新增 ERROR"。
  - Successor Required: `yes`
  - Successor Path: 待 Phase 7 登记独立治理项

## Risks And Rollback

| 风险 | 缓解 |
|---|---|
| 上游 tree-sitter-bash 的 scanner 规模（外部 token 数、共享 `goto`、heredoc 栈）远超既有先例，手译不可行或产出空壳 | Phase 0 实测 + Phase 1 强制门控对账单逐条断言 + Phase 2 corpus ≥95% 硬门禁 + Phase 1 文件规模拆分裁定 |
| `CorpusUtil` 剥离行终止符导致 CRLF fixture 无法复现；heredoc body 内 `---` 导致 input 提前截断 | Phase 0 兼容性实测；必要时扩展 `CorpusUtil`（按 `Fix` 归类，属 in-scope 修复义务） |
| Phase 2 <95% 时唯一合法 oracle（官方 tree-sitter CLI）不可用 | Phase 0 前置验证 CLI 存在且为 0.25.x |
| 能力 1 白名单或词法判据实现不严 → 含展开的命令被判"已证明"（安全漏洞） | Phase 4 明确红线断言（`a"$X"b`、`declare -x A=1 rm -rf /data`）+ 白名单封闭正向集合裁定 |
| 能力 2 只遍历直接子节点 / 静默截断 → 漏报或伪造 argv | Phase 4 强制"命中数量"断言（如 `bash -c 'rm -rf x'` 恰好 2 个 command）+ 非字面量 child 处理语义先裁定 |
| 消费方把能力 2 结果当安全证明 | 能力 1/2 类型级不可互换 + 误用测试 + "不可证明"状态在四种路径上均可区分 |
| primary API 补 `hasError()` 影响既有调用方 | Phase 3 强制 primary/compat 一致性测试 + 既有迁移测试零回归 |
| 语义层与 `nop-ai-shell/BashSyntaxParser` 双实现并存造成消费方困惑 | Phase 7 强制产出衔接裁定结论；未裁定不得标记 plan completed |
| 新增 `BUILTIN_BLOBS` 条目打破既有三处断言与注释 | Phase 1 checklist 已逐条列出（`:36` 循环、`:40` 数字、`:41` 注释、provider 类 Javadoc） |

## Closure

Status Note: 本计划经**三轮独立子 agent 对抗性 closure audit**，第一轮判定
`NOT-COMPLETED` 并查出 1 个 CRITICAL + 2 个 HIGH + 3 个 MEDIUM + 若干 LOW 的**真实
fail-open 缺陷类**（其中 CRITICAL 是"tree-sitter 的字节坐标被当作 Java 字符下标使用"，
使含多字节字符的脚本可以把破坏性命令报成**乱名 + 空目标**且**仍标记为已证明**）；第二轮
在整改后判定 `COMPLETED-WITH-FINDINGS`，13 项整改全部验证通过，但又在**同一批缺陷类**
内查出 4 项残留（grep 位置表被自身是文件的 flag 操作数击穿、heredoc 裸命令锚点只挡
`word`、重定向目标在被拒脚本上仍标 `PROVEN`、以及 item 8 自己在同一包内留下 4 处死代码）。
第二轮残留已全部整改并补回归断言（见下"第二轮整改"）。grammar/scanner 半边两轮均判定
成立且无法打破：上游 corpus 100/100、0 裁定节，既有 9 个语法零回归。

Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（R1 与 R2 均为**全新 session**，与实现 session 分离；
  R1 在 Phase 1-6 全部落地、116 项全部勾选、`## Closure` 仍为 `（待填）` 时发起）
- Audit Session:
  - R1: `ses_ef95962c9ffe2ogmPSDJJ4Gk48` → verdict **`NOT-COMPLETED`**
  - R2: `ses_ef93467c6ffeezHzgWdUbw1flX`（R1 整改后） → verdict **`COMPLETED-WITH-FINDINGS`**
  - R3: `ses_ef918d459ffetIJwte70gT8nBl`（R2 整改后，最终确认轮） → verdict
    **`COMPLETED-WINDINGS`**（"WITH-FINDINGS" 指能力 5 的启发式盲区，已全部收口并披露）
- Evidence:

**Phase 与 Exit Criteria 逐条**

| Phase | 判定 | live 证据 |
|---|---|---|
| 0 上游获取/实测/契约清单 | PASS | 参考副本与 vendored `parser.c` SHA-256 同为 `5ad30bb1…77e7f`、`scanner.c` 同为 `7cc25d70…58a9`；analysis §1-§6 载有 29 项外部 token 全枚举 + 62 命名节点 + 122 匿名 token + **19 项 field→node 映射** |
| 1 blob/scanner/接线 | PASS | blob 1,280,029 B；代理式计数探针实测**单次 heredoc 解析调用 `scan()` 11 次**，compat 与 provider 两条路径各 11 次；不挂 scanner 或挂 always-false stub 时同脚本退化为 `(program (ERROR …))`——因此 heredoc 断言无法空过 |
| 2 corpus ≥95% | PASS | `BashCorpusTest` **100/100 sections, 0 adjudicated**；模块 623 tests / 0 failures |
| 3 primary API | PASS | `TSNode.java` 的 `hasError()` / `childByFieldName(String)`；compat `TSNode` 委派；`TSNodeErrorAndFieldTest` 11 项全绿（含 primary/compat 同树一致性） |
| 4 能力 1/2 | PASS | `ShellProvenSplitTest` 34 项、`ShellLiteralHarvestTest` 14 项；codex 对抗集**按命中数量**断言；`ShellTypeIsolationTest` 用 `javax.tools.JavaCompiler` **实编译**误用片段并断言编译失败 |
| 5 能力 3/4 | PASS | `ShellHeredocExtractionTest` 19 项（负例含 codex anchor 全集 + 8 种非 `word` 参数形态）；`ShellDynamicConstructTest` 19 项（封闭枚举每项一个断言 + 后台 `&` + `concatenation` 单独断言） |
| 6 能力 5/6 + GraphQL | PASS | `ShellPathExtractionTest` 28 项（六维度 + 三档置信度 + 重定向方向）；`ShellSummaryTest` 9 项；`TestShellAnalysisGraphQL` 9 项经 `BeanContainer` + `GraphQLEngine`，含固定 fixture 端到端 |
| 7 文档/drift | PASS | 7 条 owner-doc drift 全部收敛；模块文档新增「公共 API：primary vs compat」「shell 分析语义层」「三条必须知道的限制」「byte offset 坐标」四节 |

**Closure Gates 逐条**

| Gate | 判定 | 证据 |
|---|---|---|
| 上游 vendored + provenance + 指纹可复算 | PASS | SHA-256 双份一致；`PROVENANCE.md` 记 tag/SHA/license/生成器版本/never-modified/每文件哈希 |
| blob 在档 + 两路径解析 heredoc | PASS | 上述代理计数探针 + `TestTreeSitterProvider.bashHeredocParsesThroughTheProviderPath` |
| scanner 忠实 + 门控对账单逐条断言 | PASS | `BashScannerIdiomsTest` 37 项，每项 `assertOracle(...)` 对照官方 CLI 0.25.10 导出的 sexp |
| corpus ≥95% + 9 语法零回归 | PASS | 100/100 / 0 裁定；423 → 623 tests，0 failures |
| primary `hasError()` + field 导航 + 一致性 | PASS | 委派实现 + 11 项一致性测试；`migration/JniEquivalenceTest` 零回归 |
| 6 项能力 + 类型级隔离 + 误用测试 | PASS | `ProvenSplit` 为 `sealed permits` 两态；两结果类型无公共父类型；误用**实编译失败** |
| GraphQL 端到端（容器 bean + 固定 fixture） | PASS | `TestShellAnalysisGraphQL` / `TestTreeSitterBeans` |
| Anti-Hollow（调用链连通、无空壳） | PASS | `scan-hollow-implementations --severity high` critical/high/medium/low **全 0**；调用链：`TreeSitterShell`/provider → `BashScanner`（11 次 `scan()`）与 `ShellAnalysisBizModel`（容器 bean）→ `ShellAnalysis` → `TSParser.parse`；`shell/` 包 0 处 compat import |
| 不存在被静默降级的 in-scope defect | PASS | R1/R2 查出的全部 live defect 均已修，**无一条**留在 deferred；三项 deferred 均**已确认非本计划 scope 或已有 successor** |
| owner docs 同步到 live baseline | PASS | 模块文档 / source-anchors（新增 TS-005/TS-006）/ README / wiki / INDEX / design README / design / analysis / roadmap |
| 独立子 agent closure audit 已完成并写入本段 | PASS | 本节即证据；两轮 session id 与 verdict 见上 |
| `./mvnw compile -pl nop-treesitter` | PASS | 退出码 0 |
| `./mvnw test -pl nop-treesitter -am -T 1C` | PASS | 退出码 0；**623 tests / 0 failures / 2 skipped** |
| `check-doc-links.mjs --strict` | PASS（限定范围） | 本计划新增/修改文件 **0 error**；全仓退出码 1 的剩余 error 在他人文件（见 `Deferred But Adjudicated`）。**R2 查出本计划自身引入的 3 条（docs-for-ai→ai-dev 越界 2 条 + 相对路径写错 1 条）已全部修复** |
| `check-plan-checklist.mjs <file> --strict` | PASS | 退出码 0 |
| `check-import-order.mjs` | PASS | 本模块新增文件 **0 违规** |
| `check-error-param-consistency.mjs` | PASS | 本模块新增错误码 **0 违规** |
| `-Pqa -pl nop-treesitter verify` | PASS | 退出码 0（`qa` profile = PMD + SpotBugs，均按设计不 fail） |

**Anti-Hollow 检查结果**

- 调用链运行时连通：以动态代理包裹真实 `BashScanner` 计数，单次 heredoc 解析 `scan()`
  **11 次**；同一脚本在「不挂 scanner」与「挂 always-false stub」两种情况下退化为
  `(program (ERROR …))`，证明 heredoc 断言不是空过。
- 端到端路径完整：固定 agent fixture 经 primary `TSParser.parse` → 六项能力
  （`ShellAnalysisEndToEndTest`），并经容器 bean + `GraphQLEngine`
  （`TestShellAnalysisGraphQL.theFixtureAnalysesEndToEndThroughGraphQl`）。
- 无空方法体 / 静默跳过 / no-op：两轮 audit 各自独立复核；第二轮额外查出并已清除 4 处
  死代码（`UNSUPPORTED_LANGUAGE` 常量、`ProvenSplitter` 的不可达分支、`ShellAnalysis.sourceText`、
  `ShellSource.isBlank/length`）。

**三轮 audit 查出的缺陷与整改（R1 13 项 + R2 14 项 + R3 收口）**

R1：① **CRITICAL** byte offset 当 char index（新增 `ShellSource` 按字节切片解码；三个
测试类各补非 ASCII 回归）② 能力 5 有 flag 时整体丢路径 ③ 加引号路径不可见 ④ partial
被丢弃（新增 `HARVESTED_PARTIAL`）⑤ heredoc `apply_patch foo <<'PATCH'` 负例未实现
⑥ 空白脚本返回 `PROVEN` 且零命令（新增封闭原因 `NO_COMMAND`）⑦ 空脚本原因误标
⑧ `ProofStatus.UNANALYZABLE` 死代码（删除，摘要收敛二态）⑨ `chmod`/`chown` 误当路径
⑩ 两处空壳测试 ⑪ 3 条文档越界/断链 ⑫ 「已披露 GLR 偏差」声明不成立（模块文档与 README
补已知偏差框）⑬ 「共享一次解析」javadoc 不实。

R2：⑭ **HIGH** 位置表被自身是文件的 flag 操作数击穿（`grep -f <patterns>` 把**模式**报成
脚本读取的文件）→ 改为**按参数角色**判定：绝大多数触碰文件的命令"非 flag 参数全是路径"，
grep 族与 `chmod`/`chown` 额外要求"看起来像路径"（含分隔符或 `.` 开头）⑮ heredoc 裸命令
锚点只挡 `word`（`apply_patch "$X"` / `FOO=bar apply_patch` 直接穿过）→ 改为
**`command` 恰有一个命名子节点**，8 种非 `word` 参数形态各补断言 ⑯ 重定向目标在被拒脚本
上仍标 `PROVEN` → 置信度改由整次抽取所用判定给出 ⑰ `<` 输入重定向被报成**写**路径 →
方向改由重定向运算符判定，`2>&1` 的描述符跳过 ⑱ 四处死代码（R1 ⑨ 自己造成的孤儿常量等）
⑲ 文档封闭原因集合漏 `NO_COMMAND`、多 `UNSUPPORTED_LANGUAGE` ⑳ GraphQL DTO 仍宣传
已删除的状态、`ShellAnalysis` 注释仍引用它、响亮失败零测试覆盖 → 全部修正并补测试
㉑ 模块文档表格「能断定安全」与同页「已证明≠安全」自相矛盾 → 表格改为"能否断定没有运行时
才算得出的值"，安全判定明确归属 guardrail ㉒ `flagsAreNotPaths` 其实从未被改写（新增测试
写在旁边）→ 就地改写为断言具体路径集合 ㉓ 剥引号后接受含空格/`;`/`|` 的垃圾路径 →
`isPathLiteral` 增加空白与 shell 分隔符规则 ㉔ 引号 flag（`rm "-rf"`）被当路径 → flag 判定
改在剥引号**之后** ㉕ 整改 commit 顺带引入 1 条新断链（`2309` 计划里的省略号路径）→ 已修
㉖ 解析次数 javadoc 的数字依分支而异（4 / 6）→ 如实改写 ㉗ 内联全限定名与包内风格不一致
→ 改回 import。

R3（14 项整改全部验证通过后仍查出，已全部收口）：㉘ **HIGH** 能力 5 假报未止——`dd if=/etc/passwd
of=/tmp/x`（每个操作数都是 `key=value` 赋值）、`find /var -type f`（`-type` 的操作数 `f` 是类型
不是文件，**R2 的角色模型引入**）、`tr a-z A-Z`（字符集）、`tar czf`（无连字符 flag 簇）被报成
文件——**与 R2 item 14 声称已消除的是同一类**。整改：`find` / `dd` / `tr` / `tar` 及一切未列出
的命令**整体移出角色表**，并在代码里写明为什么漏报可恢复而伪造不可恢复。㉙ **R2 引入的漏报
回归**：grep 族的**裸文件名**（`grep foo README.md`）因 `isPathLike` 要求含分隔符而被丢弃。
整改方向确定为**接受并披露**（方向安全）并新增测试钉住，使其成为决定而非意外。㉚
**`String.isBlank()` 对 U+00A0 / U+2007 / U+202F 返回 false**（`Character.isWhitespace` 不把
它们算空白），导致**纯非 ASCII 空白的脚本返回 PROVEN 且带一条乱命名的命令**——正是
"No-command 永远不被伪造"要防的形状。整改：改用 `isWhitespace || isSpaceChar` 判定，
并补 4 个非 ASCII 空白输入的断言。㉛ 三处"重定向目的地恒为写"的过期契约（`ShellPath.Access`
的公开 javadoc、模块文档、design doc）——`Access` 的 javadoc 重写为"重定向按方向、
命令参数一律按读，因为参数方向是命令语义而非文本属性"。㉜ **normative design doc 未随 R2 的
模型变更更新**（Decision 14 仍在描述已删除的位置表与"grep 从位置 2 起"）→ Decision 14 按
角色模型重写。㉝ R3 残留死代码（`PathExtractor.language` 未读字段、未用 `Set` import、
`isPathLike` 的孤儿 javadoc 骑在 `looksLikeAnOptionValue` 上）→ 全清。㉞ `ShellAnalysis`
javadoc 的解析次数（第 26 项声称已改，实际未落地）→ 如实改为 **4（可证明）/ 6（不可证明）**。
㉟ Closure 段"本计划测试刻意不把抛异常行为钉死"与实际不符（新增的
`aParseThatCannotProduceATreeRaisesRatherThanReturningASummary` **确实**为 `echo ${x!y}` 钉住了
"抛异常"）→ deferral 的理由改为**准确**的表述：该测试只断言"抛"，不绑定任何消息或恢复行为，
因此 successor 修掉活锁后**无需删除或重写该测试**。㊱ 模块文档限制 3 对 `time rm -rf /` 的
描述错误（`rm -rf /` 是 `time` 的**参数**而非另一条命令）→ 改对。㊲ `<>` 读写双向重定向在
**错误恢复树上**方向判为写 → 作为限制披露（错误树上运算符子节点不可靠）。

**Deferred 项分类检查**

三项 deferred 全部复核为**合法 non-blocking**，且无任何 in-scope live defect 被伪装进去：
(a) `GLRParser` 错误恢复活锁——已确认是**共享 runtime 代码**的 confirmed live defect，本计划
Non-Goals 明确排除 Phase 3 之外的运行时行为变更；R1/R2 均独立复现了它的对照实验
（不挂 scanner / always-false stub 均正常），确认**不是**本计划的 scanner 缺陷；已建 successor
并在模块文档「不抛异常」契约旁披露；**无任何测试把抛异常行为钉死**（新增的
`aParseThatCannotProduceATreeRaisesRatherThanReturningASummary` 只断言"抛"，不绑消息，
successor 修掉后无需对抗测试）。(b) 其余 7 个 vendored 目录的 provenance 回填——计划内
要求的裁定已完成并落文档，回填工作归 successor。(c) 模块全量 `checkstyle:check` 失败——
**pre-existing**，覆盖本计划从未触碰的既有文件；原 gate 措辞有误（`-Pqa` 实为 PMD+SpotBugs）
已在 gate 处更正。

Follow-up:

- **no remaining plan-owned work。** 三项 deferred 均已建 successor 或明确判定为
  pre-existing 且不在本计划 scope：`2026-10-04-1900-1-glr-recovery-livelock-guard.md`、
  `2026-10-04-1830-1-vendored-grammar-provenance-backfill.md`、（c）待登记为独立的模块代码规范
  治理项。

## Problem

用户需求是"AI Agent 执行 shell 命令时做转换和分析"。仓库内当前只有两个互不连通的部分：通用 tree-sitter 运行时（无 bash grammar）与 `nop-ai-shell` 的手写 bash 子集 parser（无 tree-sitter）。缺了 grammar，"分析"没有语法基础；缺了语义层，通用树对 agent 无用；缺了 primary API 的 `hasError()` / field 导航，语义层的 fail-closed 门与 heredoc 定位在 provider 路径上无法实现。

## Root Cause

不是缺陷，而是 roadmap 条目 18/19/20 的选型是"按语言逐个补 grammar"，未覆盖"agent 需要 shell 语义"这一消费维度；同时 bash 是唯一在 `~/sources/treesitter/grammars/` 参考副本中缺失的候选语言，因此比其他语言多一步上游获取；语义层所需的 `hasError()` / field 导航此前只在 compat 迁移层实现，primary API 从未需要，故未补。

## Documentation Follow-Up

- `ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md`（新建：指纹、token 枚举、节点名 + field 映射、oracle 可用性、corpus 兼容性）
- `ai-dev/design/nop-treesitter/README.md` + `01-bash-shell-analysis.md`（新建）+ `ai-dev/design/README.md` 登记
- `docs-for-ai/03-modules/nop-treesitter.md`（内置语法分层、bean id、`@BizQuery` 术语、bash 行、compat 行、公共 API、shell 语义层章节）
- `docs-for-ai/04-reference/source-anchors.md`（TS-002 内置语法数、TS-003 bean id）
- `nop-treesitter/README.md`、`nop-treesitter/docs/wiki.md`
- `ai-dev/backlog/nop-treesitter-roadmap.md` 条目 21
- `docs-for-ai/INDEX.md`（如路由变化）
- `ai-dev/logs/2026/10-04.md`
