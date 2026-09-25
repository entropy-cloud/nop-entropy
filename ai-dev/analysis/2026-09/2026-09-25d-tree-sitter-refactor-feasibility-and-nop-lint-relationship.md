# Tree-sitter 重构（Refactor）可行性分层分析与 nop-lint 关系定位

> Status: open
> Date: 2026-09-25
> Scope: 本项目（nop-entropy）增加 Java / TypeScript 重构能力的技术路线选型；tree-sitter 在重构场景的能力边界；与 nop-lint 的关系定位
> Conclusion: 建议采用**分层混合架构**：①语法发现与匹配层用 tree-sitter（即自家 nop-treesitter，已建成）；②改写层用既有 fix 管线（byte-range 文本替换，保格式）；③语义级重构（rename/extract/change signature）tree-sitter 硬边界不可行，外接既有语义层（Java = nop-java-parser/JavaParser，TS = NodeTscBridge/tsc）；④大版本框架迁移不自建，接 OpenRewrite。与 nop-lint 的关系分三种形态：**语法级 codemod = nop-lint fix 面的产品化延伸**（fix 管线已完整建成，62 条生产规则 autoFixable 全 false，属"有管线无货"）；**语义级重构 = 兄弟设施**（复用解析/语义/索引层，是 operation 不是 rule，不进 lint 规则引擎）；**大迁移 = 外部工具共存**（OpenRewrite）。落地建议 R0（补 fix 模板）→ R1（codemod 批量模式）→ R2（语义级 rename 先行）→ R3（OpenRewrite 集成评估），逐级立项 design/plan。
> 增注（2026-09-25，设计裁定后）：refactor 能力的接口形态已裁定 **AI-first / GraphQL-first，不以 LSP 组织**——见 `ai-dev/design/nop-refactor/00-vision.md` 与 `01-architecture-baseline.md`。本文 §五 中"R1 transform CLI"、"LSP codeAction 一并设计"等表述以该裁定为准：GraphQL `Refactor__preview/apply` 为第一操作面（CLI 为同引擎批处理形态），LSP 面归编辑器兼容存量、零扩张。另（同日两项后续裁定）：①自完备约束（`ai-dev/design/self-contained-design.md`）——结论 ④ 与 R3 的 OpenRewrite 定位收敛为"下游侧可选的独立外部工具"，平台不引入其引擎作为能力面；②复杂度预算 + 性价比门（vision §三.9/§四）——R0-R3 路线收敛为 **P0 codemod 面（R0/R1）+ P1 rename（R2 收窄至单模块符号域）**，extract/inline 与结构变换类默认 out（AI 直接重写 + verify 可替代）。

## Context

- 回答三个问题：①本项目如何增加针对 Java 和 TypeScript 的 refactor 能力？②是否采用 tree-sitter 解析？③与 nop-lint 的关系是什么？
- 触发：`2026-09-25-java-refactor-tools-survey.md` 完成 14 仓库工具落位，其结论"lint 负责检查与报告、工具覆盖安全执行代码变更"促成了一个需要深挖的问题——本项目自建 refactor 能力的边界究竟画在哪。
- 前序分析（本文直接承接，不重复其内容）：`2026-09-07-pure-java-tree-sitter-feasibility.md`（纯 Java runtime 路线 A/B/C，**路线 A 已落地为 nop-treesitter**）、`2026-09-07-tree-sitter-runtime-architecture.md`（C runtime 移植拆解）、`2026-09-19-ast-lint-technology-survey.md`（lint 侧选型，已兑现为 nop-lint）、`2026-09-25-nop-lint-quality-optimization-deep-audit.md`（nop-lint 现状审计）。
- 方法：2 个并行 Explore 子代理实读 nop-treesitter / nop-lint 全模块（含 fix 管线逐类核对），2 个并行网络调研子代理（tree-sitter JVM 绑定与重构边界、主流重构框架工程模式），关键结论逐条人工复核代码事实。

## 一、现状资产盘点（代码事实，动手前必读）

### 1.1 解析层：nop-treesitter 已是生产级纯 Java tree-sitter

- **纯 Java GLR runtime，零 JNI/FFM**：核心 = `parser/glr/GLRParser` + `lexer/Lexer` + `subtree/SubtreeArena`（int32 索引）+ `query/TSQuery|TSQueryCursor` + `scanner/ScannerVM`（external scanner 字节码 VM）；上游 `parser.c` 由 `codegen/Ts2Java` 提取 parse table 生成二进制 blob（格式规范 `nop-treesitter/src/main/resources/blob-format.md`，"TSJB" v4）。JNI 仅存在于 test scope 作等价性对照（`nop-treesitter/pom.xml:54-67`，`migration/JniEquivalenceTest`）。
- **内置 6 语法**：json/java/javascript/typescript/tsx/python（`provider/DefaultTreeSitterLanguageProvider.java:28-34`），corpus 字节级对拍 JS 116/116、TS 110/111、TSX 110/111、Python 115/117（`docs-for-ai/03-modules/nop-treesitter.md:9-15`）。
- **增量解析完整**：`TSParser.parseIncremental(language, oldTree, edits, newSource)` + `TSInputEdit` + `ChangedRanges`，契约 = 增量产物与全量解析字节等价（`nop-treesitter/.../TSParser.java:26-28`）；nop-lint 侧已配 `EditCalculator`（Myers 多 hunk 最小化编辑序列，design 03 §1.2 裁定）。
- **对外无 Java/TS 语义绑定**：TSNode 无 per-node point 访问器、无 `text()`（调用方按 `startByte()/endByte()` 从 `TSTree.source()` 切片）；无 stack-graphs 类 name resolution（外部生态同样没有——见 2.2）。

### 1.2 nop-lint：check 管线之外，fix 管线已完整建成

这是本次调研最重要的存量事实——**重构工具的全部安全机制在 nop-lint 里已经存在**：

| 重构安全机制 | nop-lint 既有实现 | 位置 |
|---|---|---|
| 编辑模型（byte-range 替换） | `Fix(range, replacement, ruleId, description, order)` | `nop-lint-core/.../fix/Fix.java:15-16` |
| 模板化改写（$VAR/$$$VAR） | `TemplateFix`（编译期槽位切分，未声明捕获拒绝） | `fix/TemplateFix.java:27-117` |
| 冲突仲裁（overlapping edits） | `Fixer.merge`（声明序贪心选非重叠集，重叠计入 skipped-conflicts） | `fix/Fixer.java:21-67` |
| multipass 收敛 + 重解析守卫 | `FixApplier`（≤10 轮，每轮重新 lint，语法破坏回滚原子写） | `fix/FixApplier.java:49-96` |
| dry-run + unified diff | `--fix-dry-run` + `UnifiedDiff`（手写格式化器，3 行上下文） | `cli/CliOptions.java:144-145`、`fix/UnifiedDiff.java` |
| 基线交互（已知告警不产 fix） | BaselineEngine 指纹命中诊断永不成为 fix 候选 | `suppress/BaselineEngine.java:15-31` |
| 组合面 fail-closed | fix+xscript、XML+fix 编译期拒绝 | `lint-rule.xdef:132-137` |

但**生产侧"有管线无货"**：`nop-lint-nop` 的 62 条生产规则 `autoFixable: true` 为 0 条、带 `fix:` 模板为 0 条（2026-09-25 grep 实测，`nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/`）——fix 引擎只有 demo/测试规则在用。

### 1.3 语义层：混合架构已就位（这是能做语义级重构的存量前提）

- **Java**：nop-lint-java 的 L1 pattern 走 tree-sitter java blob；L2+ 语义分析走 `nop-java-parser`（`nop-utils/nop-java-parser`，JavaParser 包装，`nop-lint-java/pom.xml:29`）——dataflow/scope/metrics/type 四个 Resolver SPI 已存在（`nop-lint-java/.../semantic/`），其中 `ScopeAnalyzer.resolve` 已有正确的外向逐界走查（审计 C1 指出 DefUseChain 是另一套错误近似，两者并存）。
- **TypeScript**：nop-lint-js 走 `tsc/NodeTscBridge`（常驻 Node tsc 进程 + 握手/超时/重启预算，`TscTypeResolver` L2 类型查询）——与 ts-morph 同源（都是 TypeScript Compiler API），**无需引入 ts-morph**。已知缺陷 C8（进程重启会话污染）已归 plan 13。
- **缺口**：nop-lint-js 只注册了 TypeScript/Tsx 两个 LintLanguage，javascript blob 虽在 nop-treesitter 内置但 lint 侧无绑定（`nop-lint-js/src/main/resources/META-INF/services/io.nop.lint.core.lang.LintLanguage` 仅 2 行）；LSP v1 只有 publishDiagnostics，无 codeAction（design 03 §2 增注："range sync / codeAction / 插件打包留候选池"）。
- **nop-code**（只读代码智能）：符号表/调用层级/依赖图/环检测已有查询 API，Java 用 JavaParser（`nop-code-lang-java`）、TS 用 nop-treesitter compat 层（`nop-code-lang-typescript`）——这是跨文件重构需要的"全仓索引"的现成雏形，但目前只有查询面，无改写面。

### 1.4 设计口径：rewrite 本来就在 nop-lint 目标内

design 00-overview §1 设计目标表："自动修复 | ESLint multipass fixer | **+ pattern-level rewrite**"；§2.1 Pattern DSL 设计为可独立使用的搜索 DSL（`nop-lint match`）。即"查得准"与"改得对"在设计上同源，refactor 不是 nop-lint 的能力外。

## 二、外部生态调研结论（有出处）

### 2.1 tree-sitter 做重构：语法层成立，语义层硬边界

- **能做**：容错解析（"robust enough to provide useful results even in the presence of syntax errors"——LSP 编辑中代码场景）、结构匹配、byte-range 文本替换。ast-grep 的 fix 机制 = "replacing the target node text with a new string"（https://ast-grep.github.io/guide/rewrite-code），CST 节点带字节范围使"替换命中段、其余逐字保留"天然保格式——**与 nop-lint Fix 的既有模型完全一致**。
- **不能做**：类型归因/name resolution。ast-grep 官方自述"only operates on the syntactic level"（https://ast-grep.github.io/advanced/tool-comparison）；OpenRewrite 官方表述更直接："Without type attribution, automated refactoring tools would be limited to simple find-and-replace operations"（https://docs.openrewrite.org/concepts-and-explanations/type-attribution）。
- **补语义的官方路线已死**：tree-sitter 生态给语法树补 name resolution 的唯一系统性方案 stack-graphs 已被 GitHub 归档（archived，README 声明不再维护，https://github.com/github/stack-graphs）。**"给 tree-sitter 造类型系统"此路不通，业界已用脚投票。**
- **JVM 绑定不需要**（对本项目）：官方 java-tree-sitter 要求 JDK 23+ FFM（https://github.com/tree-sitter/java-tree-sitter）、bonede/tree-sitter-ng 为 JNI（https://github.com/bonede/tree-sitter-ng）——本项目 own runtime 已落地，引入外部绑定反而是倒退；两者可继续仅作 test-scope 等价性 oracle（现状如此）。

### 2.2 混合架构是业界标准形态，不是妥协

- **GitHub Code Navigation**：官方双轨——search-based（tree-sitter tag query，~10 语言）+ precise（编译器级 LSIF/SCIP）；投资 stack-graphs 试图让 tree-sitter 达到 name resolution 级，2025 年归档放弃（https://github.blog/engineering/product-and-technology/bringing-code-navigation-to-communities/）。
- **Sourcegraph**：tree-sitter 做快速意图识别，精确 code intel 交给 SCIP indexer（scip-java = javac 编译器插件）（https://sourcegraph.com/blog/how-cody-understands-your-codebase）。
- **编辑器/LSP 生态**：tree-sitter 承担高亮/结构选择，rename 等重构一律转发语言服务器（jdt.ls/tsserver）——tree-sitter 管"发现"、重解析器管"语义"是通行分工。
- **OpenRewrite 的类型归因**：不自制符号表，借用真实编译器绑定（现行实现直接 import `com.sun.tools.javac.*`，https://github.com/openrewrite/rewrite/blob/main/rewrite-java-11/src/main/java/org/openrewrite/java/isolated/ReloadableJava11Parser.java），classpath 不全时显式降级为 shallow 类型。**教训：语义正确性要么借编译器、要么别做。**

### 2.3 编辑模型与安全机制：nop-lint 与主流完全同型

| 维度 | ast-grep fix | ESLint fixer | OpenRewrite | **nop-lint（既有）** |
|---|---|---|---|---|
| 编辑表达 | 模板串替换匹配节点 | range 编辑对象数组 | LST 节点改写+重打印 | Fix(range,replacement) + TemplateFix |
| 冲突处理 | 文档未定义语义 | 多 fix 不得 overlap，冲突跳过 | LST 结构天然不重叠 | 声明序贪心非重叠集 + skipped-conflicts 计数 |
| 收敛 | — | multipass ≤10 | `causesAnotherCycle` ≤3 | multipass ≤10 + 收敛守卫 + 回滚 |
| dry-run | 默认 diff + interactive | `--fix-dry-run` | `rewrite:dryRun` 产 patch | `--fix-dry-run` + UnifiedDiff |
| 验证范式 | snapshot test | — | RewriteTest 含 before-only（断言"不该改时不改"） | RuleTester valid/invalid fixture（同型） |

出处：ast-grep fix 参考（https://ast-grep.github.io/reference/yaml/fix）、ESLint custom rules（https://eslint.org/docs/latest/extend/custom-rules）、OpenRewrite recipe best practices（https://docs.openrewrite.org/authoring-recipes/recipe-conventions-and-best-practices）、nop-lint design 03 §3 增注。

**结论：nop-lint fix 面在机制完备性上已达主流水位，缺的不是机制是内容（fix 模板）与规模通道（跨文件/组合/全仓）。**

### 2.4 跨文件重构的工程范式（未来语义级 refactor 需要的先例）

- OpenRewrite 两遍式：先 parse 全部源文件为 LST → precondition 过滤 → recipe 执行；跨文件 rename 之所以可靠，是每个文件的类型引用都指向归因后的 FQN——"索引分布式存在于类型引用里"而非中央符号表（https://docs.openrewrite.org/reference/yaml-format-reference、https://docs.openrewrite.org/recipes/java/changetype）。
- GritQL 提供 `multifile {}` 语言级原语：多步骤对所有文件求值并共享全局状态（https://docs.grit.io/language/patterns）。
- 共同模式：**scan（全仓累积）→ edit（基于全貌二次修改）两遍式，拒绝边扫边改**；幂等靠"改完即不再匹配"+ before-only 测试保障；规模化依赖确定性（Moderne："identical output every run"，并披露 LLM 改写真实迁移成功率仅 30-40% 作为对照，https://moderne.ai）。

### 2.5 TypeScript 特别事项

- **tree-sitter-typescript 长尾语法有正确性风险**：`satisfies` 等热门语法跟进快（3 周内，PR #228），但 TS 4.7 variance annotations（2022-05 的语法）至今不能解析（issue #370，open）、`accessor` 被拒（#369）、decorator 有多个结构性 open bug（#309/#310/#331）；release 节奏放缓至约一年一版（https://github.com/tree-sitter/tree-sitter-typescript/issues/370）。nop-treesitter 锁定上游 blob， inherits 同样风险——TS 侧"纯 tree-sitter 解析一切"不可作为唯一解析器承诺，作为**匹配层 + missing/ERROR 节点显式计数**是可接受的（nop-lint 现有 fail-closed 解析矩阵已按此设计）。
- **TS 语义层正确选择就是 tsc**：ts-morph 只是 Compiler API 包装（https://github.com/dsherret/ts-morph）；NodeTscBridge 直接对 tsc 协议，等价且少一层。TS codemod 主流里 jscodeshift（Babel+recast）不类型感知、Grit/ast-grep 同为语法级——类型感知改写只有 tsc 一条路。
- **JS 覆盖缺口**：javascript grammar blob 已内置但 nop-lint-js 未注册绑定（见 1.3）——TS 重构若要覆盖 `.js`/`.jsx` 文件需补注册，属小改动。

## 三、分析：解析技术选型（问题 ② 的答案）

**"是否采用 tree-sitter 解析"不是一个单一决策，分四层给答案：**

| 层 | 决策 | 依据 |
|---|---|---|
| 语法发现/匹配 | **tree-sitter（自家 nop-treesitter）** | 唯一已落地的语法层底座；容错 + 增量解析是编辑器/LSP 场景刚需；ast-grep/GritQL/GitHub 同型验证 |
| 改写应用 | **不依赖解析器重写组件**——byte-range 文本替换（既有 Fix 模型） | tree-sitter 本身无 rewrite/打印组件（绑定 API 只有 parse/query/edit）；"替换命中段其余逐字保留"由 CST 字节范围天然保格式；重建整文件的 lossless 打印路线（OpenRewrite LST trivia 内嵌）成本不可行且无必要 |
| 语义验证/改写 | **tree-sitter 不可行，外接编译器级语义**：Java = nop-java-parser（JavaParser SymbolSolver），TS = NodeTscBridge | 类型归因是 rename/change signature 的硬前提（2.1）；stack-graphs 路线已死；本项目语义层已存在（1.3），纯 Java runtime 的"零 native 分发"优势与"借编译器做语义"不冲突 |
| 大版本框架迁移 | **不自建解析改写引擎，接 OpenRewrite** | javac 类型归因 + 数百 recipe 生态 + classpath 解析体系无法在 tree-sitter 上重建；Apache-2.0、Maven 插件形态与本项目共存无冲突（survey 已落位源码备研读） |

**被否决的解析路线**：①引入外部 JVM 绑定（jtreesitter/JDK 23+ 或 bonede/JNI）——own runtime 已生产级，倒退；②给 tree-sitter 造类型/name resolution——stack-graphs 归档即业界否决证据；③自建 OpenRewrite 级 LST（trivia 内嵌 + javac 归因）——单兵 6-12 月量级且要长期追 javac/tsc 版本，OpenRewrite 直接可用；④TS 侧引入 ts-morph——NodeTscBridge 已同源覆盖，多一层依赖无增益。

## 四、分析：与 nop-lint 的关系（问题 ③ 的答案）

survey 的"lint 管检查、工具管变更"二分法**对外部工具选型成立，对本项目自建能力不成立**——nop-lint 的 fix 管线（1.2）已经跨过了"执行代码变更"的门槛。按重构形态拆开，关系是三种不同的形态：

### 形态 A：语法级 codemod —— nop-lint 的 fix 面产品化（同一模块族）

- **判断依据**：lint rule 与 rewrite rule 在业界本就是同一 DSL 的两种用途（ast-grep: `pattern` + `fix`；GritQL: pattern + `=>`；ESLint: rule + fixer）；区别只在"动不动手"，不在"怎么找"。nop-lint 的 pattern DSL（$VAR/$$$VAR/关系规则/组合规则/strictness）+ xscript 语义过滤 + fix 管线 = 一个完整的 codemod 引擎，只是没有以 codemod 的产品形态暴露。
- **现状缺口**：①62 条规则零 fix 模板（有引擎无货）；②CLI 无"批量 transform"心智——`--fix` 语义是"修告警"，没有"只改不报"的纯改写模式（Grit 式 rewrite）；③无规则集/recipe 组合执行（一次 pass 跑一组 transform）；④跨文件原子提交面（fix 是逐文件原子，无全仓事务性报告）。
- **落地含义**：新增量集中在"补货 + 产品面"（R0/R1，见 §六），引擎内核零改动或近零改动。这是三者中投入产出比最高的形态。

### 形态 B：语义级重构（rename/extract/inline/change signature）—— 兄弟设施，不是 lint 规则

- **判断依据**：语义级重构是 **operation 不是 rule**——它没有"违规→修复"的语义，是"用户意图→全仓协调编辑"；硬塞进 lint 规则引擎会破坏 Diagnostic/Fix 的契约（一个 rename 产生 N 个文件的 M 个编辑，不是"每 match 一个 fix"），也会把 baseline/suppression/退出码语义搅乱。
- **共享的是底座，不是引擎**：解析层（nop-treesitter）、语义层（nop-java-parser / NodeTscBridge）、全仓索引（nop-code 符号/依赖图）全部复用；新增的是"重构操作执行器"——两遍式（scan 建 symbol map → edit 协调多处）+ 多文件原子性 + import 更新。执行形态应是独立 CLI/API（`nop-refactor rename ...` 之类），与 nop-lint 并列消费同一批底层模块。
- **nop-lint 对它的贡献还有一个间接面**：LSP codeAction（v1 缺失）是把"lint 诊断 → quickfix"和"光标处 → refactor 菜单"接到编辑器的同一入口；若 R2 立项，codeAction 应一并设计（lint 侧已具备 fix 载体，CodeAction 直接消费 Diagnostic.fix）。
- **工作量与风险**：三者中最大（符号索引正确性、跨文件原子性、import 重排），建议仅 rename 先行试点（Java 先，JavaParser SymbolSolver + ScopeAnalyzer 已有正确的作用域语义可复用——审计 C1 已确认 ScopeAnalyzer.resolve 语义正确）。

### 形态 C：大版本框架迁移 —— 外部工具共存（survey 已选型）

- OpenRewrite 作为下游 Nop 应用大版本升级的迁移路径（survey Open Question 2 待 design 评估）；nop-lint 与它无代码关系，但有**靶点互补**：nop-lint match/pattern DSL 是发现"哪些文件命中某结构"的快速通道，可作为迁移评估/验证的前置扫描（match 找靶点 → OpenRewrite 执行 → nop-lint check 验证）。三者串联恰好构成"发现-执行-验证"闭环。

### 关系总结图

```
                    ┌────────────────────────────────────────┐
                    │            共享底座（已建成）              │
                    │  nop-treesitter（语法+CST+增量+query）    │
                    │  nop-java-parser（JavaParser 语义）        │
                    │  NodeTscBridge（tsc 语义）                │
                    │  nop-code（符号/依赖图，只读）              │
                    └───────┬───────────────┬────────────────┘
                            │               │
              ┌─────────────▼───┐   ┌───────▼──────────────┐
              │ nop-lint        │   │ 重构操作执行器（R2 新建）│
              │ · check 管线     │   │ rename/extract/...    │
              │ · fix 管线       │   │ 两遍式+多文件原子      │
              │   └─ 形态 A：     │   └───────┬──────────────┘
              │     codemod/     │           │
              │     transform    │           │
              │   （R0/R1 补面）  │           │
              └─────────────────┘           │
                     ▲                      │
                     │  发现靶点 / 验证结果    │
              ┌──────┴──────────────────────▼──────┐
              │ 形态 C：OpenRewrite（外部，大迁移）    │
              └────────────────────────────────────┘
```

## 五、落地路线建议（逐级立项，本文不立项）

| 阶段 | 内容 | 性质 | 前置 |
|---|---|---|---|
| **R0** | 给 62 条生产规则中机械可修者补 fix 模板（TemplateFix 已支持 $VAR/$$$VAR；注意审计 C2 TemplateFix 多捕获 NPE 已归 plan 11，先修后用） | 纯内容工作，规则 YAML + RuleTester fixture | plan 11 落地（fix 正确性缺陷） |
| **R1** | codemod 产品面：transform 规则类（`action: transform` 或 severity 脱钩——"只改不报"）、`nop-lint transform` CLI（规则集批量 dry-run→diff→apply）、报告面复用 Reporter 五格式；顺带补 javascript LintLanguage 注册 | design 先行（CLI 参数面/退出码/报告语义均需裁定） | R0 验证 fix 模板质量后 |
| **R2** | 语义级重构操作器：rename 先行（Java：JavaParser SymbolSolver + ScopeAnalyzer；TS：NodeTscBridge），两遍式 + 多文件原子 + import 重排；LSP codeAction 一并设计 | 独立 design（与 nop-code 索引的关系需裁定：借用查询面 vs 内嵌索引） | R1 后按需；plan 13（tsc bridge 会话隔离）应先落地 |
| **R3** | OpenRewrite 集成评估：Nop 大版本升级迁移路径 design（survey Open Question 2） | design 评估 | 有真实大版本升级需求时 |

依赖提示：质量审计 plan 08-14 正在途（P1 规则编译复用、P6 children 缓存直接影响 codemod 批量模式的吞吐；C2/C8直接影响 fix/TS 链路正确性）——**R0/R1 不应与 plan 07-14 并行抢改同一批文件，建议排在其后**。

## 六、Open Questions

- [ ] R1 的 transform 规则在 DSL 上的表达形态：新 `action` 槽位 vs `severity: silent` + `fix` 复用 vs 独立 `transform.rule.yml` 类型（涉及 xdef 元模型扩展，需 design 裁定）
- [ ] R2 rename 的符号索引来源：复用 nop-code 查询面（服务化）还是操作器内嵌轻量索引（工具化）——影响 nop-code 的定位演进
- [ ] TS 长尾语法（variance annotations 等）在 nop-treesitter 的跟进策略：等上游 vs 自补 grammar（blob 生成链已具备自补能力，见 1.1）
- [ ] LSP codeAction 立项时机：随 R2 还是提前到 R1 后（lint quickfix 不依赖语义层，可先行）

## References

- 代码事实：`nop-treesitter/`（README/blob-format/provider/codegen）、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/`（Fix/TemplateFix/Fixer/FixApplier/UnifiedDiff）、`nop-lint/nop-lint-java/pom.xml:29`、`nop-lint/nop-lint-js/src/main/resources/META-INF/services/io.nop.lint.core.lang.LintLanguage`、`nop-utils/nop-java-parser`
- 前序分析：`ai-dev/analysis/2026-09/2026-09-25-java-refactor-tools-survey.md`（14 仓库落位）、`2026-09-07-pure-java-tree-sitter-feasibility.md`、`2026-09-07-tree-sitter-runtime-architecture.md`、`2026-09-19-ast-lint-technology-survey.md`、`2026-09-25-nop-lint-quality-optimization-deep-audit.md`
- 设计文档：`ai-dev/design/nop-lint/00-overview.md`（§1 目标表、§3 架构图）、`03-execution-engine.md`（§1.2 增量、§3 fix 安全）、`04-ast-grep-alignment.md`（§7 fix 对标）
- 外部来源（调研子代理 2026-09 实查）：https://tree-sitter.github.io/tree-sitter/ 、https://github.com/tree-sitter/java-tree-sitter 、https://github.com/bonede/tree-sitter-ng 、https://github.com/github/stack-graphs 、https://ast-grep.github.io/guide/rewrite-code 、https://docs.openrewrite.org/concepts-and-explanations/type-attribution 、https://docs.openrewrite.org/authoring-recipes/recipe-conventions-and-best-practices 、https://github.com/openrewrite/rewrite/blob/main/rewrite-java-11/src/main/java/org/openrewrite/java/isolated/ReloadableJava11Parser.java 、https://github.com/tree-sitter/tree-sitter-typescript/issues/370 、https://github.com/dsherret/ts-morph 、https://github.com/getgrit/gritql 、https://docs.grit.io/language/patterns 、https://github.blog/engineering/product-and-technology/bringing-code-navigation-to-communities/ 、https://sourcegraph.com/blog/how-cody-understands-your-codebase 、https://moderne.ai
- 本地研读材料：`~/sources/refactor/`（survey 落位的 14 仓库）、`~/sources/treesitter/`（tree-sitter 生态源码）
