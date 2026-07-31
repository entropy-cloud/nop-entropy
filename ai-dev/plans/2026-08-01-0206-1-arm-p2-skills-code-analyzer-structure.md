# 1 arm-p2-skills-code-analyzer-structure — nop-ai-skills 结构治理（code-analyzer 大文件拆分 + 模块职责）

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/audits/2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md`（P2-MA1-004/005、P3-MA1-014/015）+ `ai-dev/audits/arm-index.md` §P2 修复追踪
> Mission: audit-remediation
> Work Item: MA1.3-P2-004/005 + MA1.3-P3-014/015（第九批 deferred successor）
> Related: `2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md`（deferred 出处）、`2026-07-31-2248-1-arm-p2-structural-successor.md`

## Purpose

把 nop-ai-skills 模块组（nop-ai-code-analyzer / nop-ai-deepwiki）剩余的结构类 deferred 项收口：拆分两个超大文件（CodeFileInfo 589 行 / FileLanguageStats 515 行），裁定 code-analyzer 混合职责问题，修正 deepwiki 依赖 scope。P2-MA1-004/005 在计划 `2026-07-31-1834-3` Deferred But Adjudicated 段登记（Successor Required: no），本计划按严重度排序重开承接（自主裁定，覆盖前记录）；P3-MA1-014/015 在 Non-Blocking Follow-ups 登记为"后续批次"，本计划承接。

## Current Baseline

- **P2-MA1-004 live**：`nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/CodeFileInfo.java` 589 行，包含 7 个类型（CodeFileInfo、AccessModifier 枚举、CodeSymbol 基类、CodeClassInfo、CodeFunctionInfo、CodeCallInfo、CodeVariableInfo）+ 3 个 `internString*` 工具方法（:568-589）+ `collectUsedFns()` 递归逻辑，数据模型与 interning 紧耦合。
- **P2-MA1-004 限定名使用面**（拆分决策输入）：`CodeFileInfo.X` 嵌套限定名仅被同模块 2 个主源码文件使用——`JavaCodeFileInfoParser.java`（27 次出现 / 23 行）、`JavaCodeFileInfoGenerator.java`（2 处）；仓库内无模块外消费者。
- **P2-MA1-005 live**：`nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/FileLanguageStats.java` 515 行，混合 4 个关注点：语言映射（`ExtensionLanguageMapper` 已在同包但 stats 内仍有 IGNORED_DIRECTORIES/IGNORED_EXTENSIONS 静态集 :31-32）、文件系统遍历、注释检测（`LineStats` :116）、统计聚合（`LanguageStats` :44）。
- **P2-MA1-005 调用面（裁定输入）**：仓库级 grep 显示 `FileLanguageStats` 除类自身外**零 Java 引用**（JavaCodeFileInfoParser 不引用；无生产消费者、无直接测试）。拆分前必须先裁定：拆分（附直接调用测试）/ 删除 / 接线。
- **P3-MA1-014 live**：code-analyzer 模块 5 个包（code/git/maven/project/stats），maven 包 10 个文件，pom 声明 nop-shell 依赖且仅 `MavenProject.java` 使用；git 包 `GitIgnoreFile` 有模块外消费者（nop-cli-core `CliFileCommand`）。
- **P3-MA1-015 live**：`nop-ai/nop-ai-skills/nop-ai-deepwiki/pom.xml` 对 `nop-ai-code-analyzer`、`nop-ai-tools` 声明 **compile**（无显式 scope）依赖。生产代码 0 个 Java 文件（仅 `_vfs/nop/ai/tasks/`、`_vfs/nop/ai/prompts/` 资源），但**测试源码** `nop-ai-deepwiki/src/test/java/io/nop/ai/deepwiki/TestDeepWikiPrompts.java:10` `import io.nop.ai.tools.file.FileToolBizModel`（@Disabled 仅运行时跳过，test-compile 仍编译）。nop-ai-code-analyzer 在生产与测试中均 0 引用。
- 既有测试：`nop-ai-code-analyzer/src/test/.../code/TestJavaCodeFileInfoParser.java`、`TestJavaCodeFileInfoGenerator.java`（MA4.1-05/06 新增）、`TestJavaFileSplitter.java`、`TestJavaParser.java` 覆盖 code 包；stats 包无直接测试。
- 绿色基线：`ai-dev/logs/2026/07-31.md` 记录全量 `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（04-13 批次 :139 为 5564 tests 含 nop-ai-agent 2867；1446-3 批次 :234 为 3444——数字随批次测试增长变化，以执行时实测为准）。

## Goals

- CodeFileInfo.java 拆分：7 个类型各自独立文件，interning 逻辑提取为独立工具类，行数降至 <300 行。
- FileLanguageStats.java 拆分或删除/接线裁定：语言映射/遍历/注释检测/聚合 4 关注点分离（若裁定拆分），行数降至 <300 行。
- code-analyzer 混合职责裁定落盘（拆模块 or 保持+文档，二选一裁定）。
- deepwiki pom 依赖修正（移除 nop-ai-code-analyzer + nop-ai-tools 改 test scope 或保持 compile，视测试引用裁定）。
- 拆分后全部既有测试通过，且新增针对拆分产物的 focused 测试。

## Non-Goals

- 不拆 `ReActAgentExecutor`/`DefaultAgentEngine`（MA4.2-05，3728/3681 行——核心运行时大重构，audit 自信度仅 Likely，需 design-first，另行规划；1446-2 已裁定 Successor Required: no，保持 optimization candidate）。
- 不处理 nop-ai-maven/nop-ai-tools 等其他模块的 P3（由计划 2 承接）。
- 不改 CodeFileInfo 各类型的成员签名（字段、方法签名、继承关系保持）；嵌套限定名 `CodeFileInfo.X` 提升为顶层 `X` 是本次拆分的目标，允许更新同模块 2 个消费者（见 Current Baseline 使用面）。
- 不迁移 ThoughtStorage 持久化方案（P3-MA1-013 属计划 2）。

## Scope

### In Scope

- `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/`：CodeFileInfo.java 拆分（CodeSymbol/CodeClassInfo/CodeFunctionInfo/CodeCallInfo/CodeVariableInfo/AccessModifier + intern 工具类）+ 同模块消费者适配。
- `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/`：FileLanguageStats 处置（拆分 / 删除 / 接线裁定 + 执行）。
- `nop-ai-code-analyzer` 模块职责裁定（P3-MA1-014）：maven 包与 code/stats/git 的边界。
- `nop-ai/nop-ai-skills/nop-ai-deepwiki/pom.xml`（P3-MA1-015）：依赖 scope 修正。
- 新增/更新测试：拆分产物的 focused 单测；arm-index §P2/P3 追踪行更新。

### Out Of Scope

- MA4.2-05 引擎大文件拆分（见 Non-Goals）。
- nop-ai-tools/nop-ai-rag 结构项（计划 2）。
- 测试质量 P3 残余（计划 3）。

## Execution Plan

### Phase 1 - CodeFileInfo 拆分

Status: completed
Targets: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/`

- Item Types: `Fix | Proof`

- [x] 将 CodeSymbol、CodeClassInfo、CodeFunctionInfo、CodeCallInfo、CodeVariableInfo、AccessModifier 从 CodeFileInfo.java 提取为同包独立顶层文件（保持可见性与原声明一致，继承关系不变）
- [x] internString/internStringSet/internStringMap 提取为独立工具类（如 `CodeSymbolInterning`），CodeFileInfo 委托调用
- [x] collectUsedFns() 递归逻辑保持位置或按内聚度移动，行为不变
- [x] 适配主源码消费者：`JavaCodeFileInfoParser.java`（27 次出现 / 23 行）、`JavaCodeFileInfoGenerator.java`（2 处）的 `CodeFileInfo.X` 限定名更新为顶层 `X` 或按新结构 import
- [x] 新增 `TestCodeFileInfoStructure`（或扩展既有测试）：验证拆分后类型引用路径、interning 去重语义不变
- [x] 更新 `TestJavaCodeFileInfoParser`/`TestJavaCodeFileInfoGenerator` 的 import 变更（核验：两测试无 `CodeFileInfo.X` 嵌套引用，无需变更）

Exit Criteria:

- [x] CodeFileInfo.java < 300 行（wc -l 验证：216 行）
- [x] 拆分后 `./mvnw compile -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -am` 通过（含新增 focused 测试：TestCodeFileInfoStructure 7 方法）
- [x] **无静默跳过**：无空方法体/无新增 UOE/无行为降级（git diff 显示除移动与限定名更新外无逻辑变更）
- [x] 契约核对：各类型成员签名/继承关系 diff 为 0（脚本化归一对比 6 类型 + 主类，全部 IDENTICAL）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/08-01.md` Phase 1 条目）

### Phase 2 - FileLanguageStats 处置（拆分 or 删除 or 接线）

Status: completed
Targets: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/`

- Item Types: `Fix | Decision | Proof`

- [x] 裁定前置（落盘理由）：FileLanguageStats 零生产调用者/零直接测试，评估其功能是否被 JavaCodeFileInfoParser/coder 链等价覆盖——裁定：a) 拆分（保留能力 + 直接调用测试）；b) 删除（无消费者，防死代码）；c) 接线（挂入 parser/coder 链）——三选一。**裁定 = a) 拆分**：①功能完整非空壳（GitHub Languages API 兼容工具，516 行真实实现），删除=销毁可用能力；②parser/coder 链无自然消费点（其产物是 CodeFileInfo 符号数据，非语言统计），强行接线=为制造消费者而人工耦合；③audit 原建议即拆分（信心高）。裁定落盘：本 plan + `ai-dev/audits/arm-index.md` 第九批追踪小节。
- [x] 若裁定拆分：语言映射（IGNORED_* + initializeIgnoredItems）提取为独立类（`IgnoredItems`，未并入 ExtensionLanguageMapper——两者关注点不同：扩展名→语言 vs 跳过项）；文件系统遍历提取（`FileTreeWalker`）；注释检测（LineStats）提取（`CodeLineAnalyzer`）；统计聚合（`LanguageStatsAggregator`，含 LanguageStats/StatType/排序输出）；FileLanguageStats 主类只保留编排入口，188 行（< 300）
- [x] 若裁定拆分：新增 `TestFileLanguageStats`——**直接实例化** FileLanguageStats 对样本目录断言统计结果/语言分类/注释检测边界（5 方法：综合统计值断言/按类型统计+GitHub API 字符串/局部映射优先级/忽略规则/CodeLineAnalyzer 注释边界）
- [x] 若裁定删除：确认无消费者后删除文件 + pom 无残留引用（不适用——裁定拆分）
- [x] 裁定与执行结果更新 arm-index §P2 追踪（第九批追踪小节，P2-MA1-005 行）

Exit Criteria:

- [x] 裁定记录落盘（拆分 + Why 拒绝删除/接线），无 in-scope live defect 被静默降级
- [x] 若拆分：FileLanguageStats.java < 300 行（wc -l 验证：188 行）；`./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -am` 通过
- [x] 若接线：新增 parser/coder 链调用断言（不适用——裁定拆分）
- [x] **端到端验证**（若拆分）：样本目录 统计 → 语言分类 → 聚合 全路径由直接调用测试断言结果值（`TestFileLanguageStats` 5 方法全值断言：Java 12 行/4 注释/6 代码、Python 5 行、Maven 局部映射、字节数精确断言、GitHub API 降序字符串）
- [x] **无静默跳过**：无空方法体/无行为降级（语义对比：LanguageStats/StatType/analyzeFileLines/detectComment/isBlockCommentLanguage/isStringLiteral/getStatValue 全部 IDENTICAL，getStatsByType 仅 `getComprehensiveStats` 调用外移）；若删除则 grep 全仓 0 残留（不适用——拆分）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - code-analyzer 模块职责裁定（P3-MA1-014）

Status: completed
Targets: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/` + `ai-dev/design/nop-ai/`

- Item Types: `Decision | Proof`

- [x] 检查 maven 包（10 文件，nop-shell 依赖仅 MavenProject 使用）与 git 包（GitIgnoreFile 有 nop-cli-core 外部消费者）的实际使用面，裁定：a) 拆独立模块 nop-ai-maven-analyzer；b) 保持现状+边界文档化——二选一（git 包归属一并纳入裁定输入）。**裁定 = b) 保持现状 + 边界文档化**。裁定输入（live）：`code → maven`（JavaParserBuilder/JarResolverCollection/JavaCodeFileInfoGenerator）而 `maven → project → git`（MavenProject→GitProject→GitIgnoreFile）——拆分即双向环依赖（maven-analyzer→core 与 core→maven-analyzer），破环须迁 7/10 maven 文件进 core，拆分自行溶解；nop-shell 仅 1 文件使用、两真实消费者（nop-ai-coder 经 task XML 用 code 包 JavaFileSplitter；nop-cli-core 经传递依赖用 git 包 GitIgnoreFile）均未触 maven 包；git 包是真正的跨模块公共面，天然属 core 侧。
- [x] 裁定结果落盘（design doc 或模块 javadoc），含拒绝替代方案理由（落盘 `ai-dev/design/nop-ai/02-code-analyzer-module-boundary.md`：拒绝拆分的 4 条理由 + 拒绝 nop-shell optional/provided 的理由 + 未来迁移触发条件；`ai-dev/design/nop-ai/README.md` + `ai-dev/design/README.md` 注册）
- [x] 若选 a：pom 拆分 + 依赖修正 + 全量 build/test 验证（不适用——裁定 b）
- [x] 裁定与执行结果更新 arm-index §P2 追踪（第九批追踪小节，P3-MA1-014 行）

Exit Criteria:

- [x] 裁定记录落盘（含 Why 拒绝理由），路径明确（`ai-dev/design/nop-ai/02-code-analyzer-module-boundary.md`）
- [x] 若发生模块拆分：`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` 通过（不适用——裁定 b，无模块拆分）
- [x] **接线验证**（若拆分）：下游模块（含 nop-cli-core 对 GitIgnoreFile）对 code-analyzer 的引用在运行时仍连通（不适用——无拆分；既有连通性由全量 build+test 覆盖）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - deepwiki 依赖 scope 修正（P3-MA1-015）

Status: completed
Targets: `nop-ai/nop-ai-skills/nop-ai-deepwiki/pom.xml`

- Item Types: `Fix | Proof`

- [x] 确认依赖真实使用面：nop-ai-code-analyzer 生产与测试均 0 引用 → **移除**该依赖；nop-ai-tools 仅 `TestDeepWikiPrompts.java:10` import `FileToolBizModel`（:38 `@Inject FileToolBizModel`，生产 0 引用）→ 改为 `<scope>test</scope>`
- [x] 若测试 import 后续调整（如移除 @Disabled 用例），复核 scope 选择；保留 test scope 满足 test-compile（TestDeepWikiPrompts 保持 @Disabled，test-compile 已验证通过）
- [x] `./mvnw compile -pl nop-ai/nop-ai-skills/nop-ai-deepwiki -am` + `./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-deepwiki -am` 通过
- [x] 更新 arm-index §P2 追踪（P3-MA1-015 行）

Exit Criteria:

- [x] deepwiki pom 依赖与真实使用一致（nop-ai-code-analyzer 移除、nop-ai-tools 为 test scope，依赖树验证：`nop-ai-tools:jar:...:test` direct、code-analyzer 仅经 tools 传递且 test scope）
- [x] deepwiki 模块 compile + test 通过（test-compile 不因 import 失败）
- [x] 无 owner-doc update required（纯 pom 修正，不影响模块契约；`docs-for-ai/03-modules/nop-ai.md` 及 docs-for-ai 全树 grep `deepwiki`/`code-analyzer` 0 引用，核对后确认无需同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 关闭条件：本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选后，才能将 Plan Status 改为 completed。

- [x] P2-MA1-004/005 处置完成（拆分文件 < 300 行 / 裁定删除或接线），行为无变化（diff 验证）
- [x] P3-MA1-014 裁定落盘，无 in-scope live defect 被静默降级
- [x] P3-MA1-015 依赖 scope 修正完成（deepwiki compile+test 全绿）
- [x] 必要 focused verification 已完成（新增测试覆盖拆分产物）
- [x] 受影响的 owner docs 已同步（`ai-dev/design/nop-ai/` 或 `docs-for-ai/03-modules/nop-ai.md`），或明确 No owner-doc update required
- [x] `ai-dev/audits/arm-index.md` 新增第九批追踪小节并登记四行（P2-MA1-004/005、P3-MA1-014/015；当前无对应行）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证无空方法体/静默跳过/no-op；无"为死代码做 SRP 拆分"且无消费者证明
- [x] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [x] `./mvnw test -pl nop-ai -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0

## Deferred But Adjudicated

### MA4.2-05 引擎大文件拆分（ReActAgentExecutor 3728 行 / DefaultAgentEngine 3681 行）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 核心运行时类，audit 原文"Large but structured (many private methods). Size itself isn't a defect"，自信度仅 Likely；3728+ 行拆分为大风险重构（2867 tests 回归面），需 design-first 且独立 plan。1446-2 已裁定 Successor Required: no，保持 watch-only。
- Successor Required: `no`
- Successor Path: 无（watch-only residual）

### FileLanguageStats 若裁定为删除

- Classification: `optimization candidate`（若发生）
- Why Not Blocking Closure: 零生产调用者/零直接测试，删除消除死代码风险；裁定理由落盘后不作为缺陷残留。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA1.4 P3 项（P3-MA1-023~030，gateway 测试比/隐式依赖/VfsMavenCli.execute 命名等）：已由 MR1/MR2 裁定为 watch-only residual，不入本计划。
- P3-MA1-039（NopAiSessionContext revision 属性名）：ORM 裁定项，低价值，watch-only。

## Closure

Status Note: 4 个 Phase 全部完成并独立 closure audit APPROVE。CodeFileInfo 590→216 行（7 类型 + interning 工具拆分，语义 IDENTICAL）、FileLanguageStats 516→188 行（4 关注点拆分，裁定理由落盘）、P3-MA1-014 裁定保持现状+边界文档化（`ai-dev/design/nop-ai/02-code-analyzer-module-boundary.md`）、P3-MA1-015 deepwiki pom 依赖修正（移除 code-analyzer、tools 改 test scope）。全量 build+test 绿，scan-hollow exit 0。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_0467195adffeM094NriQc4GfAf`，general subagent）
- Audit Session: `ses_0467195adffeM094NriQc4GfAf`
- Evidence:
  - **Phase 1 PASS**：7 个新文件在 `code/`；`wc -l CodeFileInfo.java` = 217（<300）；继承关系 `CodeClassInfo.java:15`/`CodeFunctionInfo.java:13` extends CodeSymbol；ripgrep `CodeFileInfo\.[A-Z]` 全仓 0 残留；audit 独立脚本对比 git HEAD 原嵌套类与 6 个新文件（归一化）全部 IDENTICAL（仅 `public static`→`public` 声明行）；`TestCodeFileInfoStructure` 7/7 绿。
  - **Phase 2 PASS**：4 个新类在 `stats/`；`wc -l FileLanguageStats.java` = 188（<300）；裁定=拆分落盘 plan Phase 2 item 1；audit 独立重算样本数值全部匹配（Java 12/2/4/6、Python 5/1/1/3、build.pom→Maven、字节数精确、GitHub API 降序字符串、忽略规则）；`TestFileLanguageStats` 5/5 绿；walker/analyzeFileLines 的 catch 语义与 HEAD 原版 byte-identical（保留行为非新增）。
  - **Phase 3 PASS**：`02-code-analyzer-module-boundary.md`（55 行，4 条拒绝拆分理由 + 拒绝 nop-shell optional + 迁移触发条件）；nop-ai/README.md:10,15 与 design/README.md:36 注册。
  - **Phase 4 PASS**：deepwiki pom 无 code-analyzer 声明；`nop-ai-tools` `<scope>test</scope>`（pom.xml:22）；deepwiki 唯一 Java 文件为测试目录 TestDeepWikiPrompts.java（:10 import FileToolBizModel）。
  - **Gate 5 PASS**：`./mvnw test -pl nop-ai/nop-ai-skills/nop-ai-code-analyzer -T 1C -q` exit 0（26 tests 0 failures）；`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` BUILD SUCCESS（2026-08-01T02:55）；`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；`scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0（0 findings 全级别）；`check-doc-links.mjs --strict` exit 0（0 errors；4 条 warning 属兄弟计划 0206-3，非本 plan 文件）。
  - **Gate 6（文本一致性）**：Phases 1-4 Status: completed 且全部 `[x]`；Closure Gates 12/12 勾选；`ai-dev/logs/2026/08-01.md` Phase 1 + Phases 2-4 条目存在；arm-index 第九批小节四行已登记。
  - **Anti-Hollow**：`CodeSymbolInterning` 被 CodeFileInfo.intern()（:202-215）及 5 个符号类型 intern() 调用；`FileTreeWalker`/`CodeLineAnalyzer`/`LanguageStatsAggregator`/`IgnoredItems` 被 FileLanguageStats（:34-35,131-158）调用并由 TestFileLanguageStats 直接消费——非死代码、非"为死代码做 SRP 拆分"（直接调用测试为消费者证据）；无空方法体/静默跳过新增。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 后执行）
  - Deferred 项分类检查：无 in-scope live defect 被降级（P2-MA1-004/005、P3-MA1-014/015 全部落定；Deferred But Adjudicated 段仅 MA4.2-05 引擎拆分与"FileLanguageStats 若裁定删除"（未发生），均 non-blocking）

Follow-up:

- no remaining plan-owned work
- 兄弟计划 `2026-08-01-0206-3-arm-p3-test-quality-residual.md` 的 4 条 doc-link warning（测试文件路径待该计划落地后自然闭合）属其执行面，非本 plan 残留
