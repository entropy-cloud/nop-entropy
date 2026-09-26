# 09 WI9 操作框架骨架——四段契约框架化 + 符号解析适配 SPI

> Plan Status: completed
> Last Reviewed: 2026-09-26
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M2 WI9 原文 + Purpose 执行路径统一裁定 + Cross-Cutting 完成判定 + Current Baseline 的 WI2 裁定行 + Rules 行为红线）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §一.3（四段契约）/§二（模块拓扑与依赖规则——语言适配 SPI 注入 core）/§四（RefactorResult 载荷契约，WI5 已落地）；`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md` §四（两项裁定）/§五（对 WI9 plan 的转录义务）；`ai-dev/design/nop-refactor/00-vision.md` §三 原则 1–9
> Related: `ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（WI4——框架唯一应用路径）；`ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md`（WI5——框架唯一校验路径与载荷面）；`ai-dev/plans/nop-refactor/02-wi2-symbol-solver-spike-and-p1-scope.md`（WI2——符号域与引用搜索裁定的 plan 载体）；`ai-dev/plans/nop-refactor/06-wi6-graphql-actions.md`、`ai-dev/plans/nop-refactor/07-wi7-cli-batch-form.md`（被收敛的两个已落地消费面）；后续 WI10/WI11（rename 解析落地归属）、WI12（RenameInput GraphQL 接线归属）
> Review: R1(fresh session): REVISE - 1B+3M+5m all fixed. R2(fresh session): REVISE - delta check 完成（2026-09-26）：src/main grep 口径 4 处补齐 + 溯源更正后放行执行。

## Purpose

执行 roadmap WI9（Item Type: Fix）：把 baseline §一.3 的 check → plan → apply → verify 四段契约从文档形态与两个消费面各自的内联执行链，框架化为 nop-refactor-core 的 operation 包——语言无关 `RefactorOperation` SPI（check 可行性校验 / plan 编辑计划 + nonApplied 预收集 / apply 经 WI4 `EditPlanApplier` 唯一应用路径 / verify 经 WI5 `RefactorVerifier` 唯一校验路径）。roadmap Purpose 执行路径统一裁定：codemod 面在 WI9 框架存在前已落地（复用 WI4 入口 + WI5 载荷），WI9 的职责是把这条已验证的路径**框架化**而非另建第二实现；closure 验收必须包含"rename 与 codemod 走同一 plan/apply/verify 机制、无第二执行路径"的接线证明。同期落符号解析适配 SPI：core 定义语言无关 `SymbolResolverAdapter`，新建 nop-refactor-java 模块提供 Java 适配（消费 nop-java-parser + nop-lint-java ScopeAnalyzer 语义，符号域与引用搜索按 WI2 裁定）；RenameOperation 以 fail-closed 骨架起步（实际解析 WI10/WI11）。四段最小实现，不做 LTK 式 Undo/脚本/participants。

## Current Baseline

（live 已核对，2026-09-25；行号为核对时快照，执行时以 live 为准）

- **WI4 已 completed**（plan 03，roadmap 已勾选）：`EditPlanApplier.apply(Path file, byte[] source, List<Fix> edits, LintLanguage language, boolean dryRun)` → `EditPlanResult(finalSource, rolledBack, appliedEdits, skippedConflicts, skippedEdits)` + additive `appliedFixes`（nop-lint-core fix 包）——dryRun=true 不落盘；原子写 + 守卫回滚 + skippedEdits 携带被跳过编辑本体。本 plan 的唯一应用路径。
- **WI5 已 completed**（plan 05，roadmap 已勾选）：nop-refactor/nop-refactor-core 已存在载荷面——`RefactorResult` / `Verification` / `FileEdit` / `RefactorStats`（`SkippedBuckets` 四桶含 rolledBack；`CostTier` IN_PROCESS/OUT_OF_PROCESS）/ `NonApply`（`Reason` 四枚举 CONFLICT/OUT_OF_SCOPE/UNRESOLVED_TARGET/ROLLED_BACK，构造器 fail-closed）/ `EditedFile` / `NopRefactorException`（extends NopException）/ `RefactorVerifier`。`RefactorVerifier.verify` 单文件 parseOk 谓词 = 重解析无 ERROR/missing 恢复节点，异常 fail-closed；`assemble(applied, files, edits, nonApplied)` 组装完整载荷；`symbolIntact` 恒 null（rename 类操作 WI9–WI12 接管正式计算）；`residualRuleCount=0` = 未配规则子集。本 plan 的唯一校验路径，载荷契约零缩水。
- **共享加载门已存在**：`RefactorRuleGates.verifyRewriteRuleset(loaded, registry)`（nop-refactor-core，WI6/WI7 单一实现——仅 transform 规则 + requires 空 + 语言绑定已注册）。
- **WI6 面已落**（plan 06 `Plan Status: completed`）：nop-refactor-graphql `NopRefactorBizModel.rewrite` 内联完整 codemod 执行链——输入校验 → `RuleSetLoader.loadRuleSet` → `RefactorRuleGates` → `ExemptionFilter` 豁免门 → `LintEngine(registry, STANDARD)` → 逐文件 read + cap 两键（max-source-size 字节级前置门 + 读后 backstop / max-target-files 默认 512）→ lint → transformDegraded>0 fail-closed → 豁免门控 → 两阶段（compute 全量校验前置 / land 经 `EditPlanApplier.apply`）→ `RefactorVerifier.assemble`；path grammar 三分支（namespace 拒绝 / `/`-根拒绝 / toRealPath 工作目录约束）。测试在仓：TestNopRefactorBizModel（11/11）+ TestNopRefactorGraphQL（RPC e2e 四断言）。
- **WI7 面已落**（plan 07 `Plan Status: completed`，roadmap WI7 已勾选）：nop-refactor-core `io.nop.refactor.core.cli` 包（NopRefactorCli / RefactorRenderers / RefactorOptions）——同一引擎第二消费面：`RuleSetLoader` → `RefactorRuleGates.verifyRewriteRuleset` → `TargetScanner.scan` → `LintEngine(registry, profile)` → 豁免门 → `EditPlanApplier.apply` → `RefactorVerifier(languageByPath::get, engine, List.of())`；退出码三态（0/1/2）。测试在仓：TestRefactorCliEndToEnd。
- **gap（本 plan 的新增量）**：nop-refactor-core 无 operation 包——四段契约只存在于 design 文档与上述两个 face 的各自内联链中，无框架载体（roadmap Purpose 裁定的"框架化"对象即这两条同构链）；`SymbolResolverAdapter` 不存在；nop-refactor-java 模块不存在（nop-refactor/pom.xml modules 现仅 nop-refactor-core + nop-refactor-graphql）。
- **WI2 裁定转录（roadmap 点名义务，源 = `ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md` §二/§四；spike 代码在 `_tmp/wi2-spike/`）**：
  - 实测数字：类型引用解析率 GLOBAL 72.3%（3826/5292）、SINGLE（单模块）61.9%（3278/5292）；io.nop.* 跨模块解析仅 18/5292（**0.3%**）；第三方依赖重模块无 jar solver 时解析面崩塌（nop-lint-core 9.0%、nop-lint-java 14.4%、nop-java-parser 30.7%）。
  - 裁定 1：**P1 rename 符号域 v1 = 单模块内（module-scoped）**——WI10 局部变量/参数单文件（ScopeAnalyzer 语义）；WI11 字段/非虚方法/类型模块内域，跨文件引用按"简单名 + 同包/import 绑定"过滤，歧义或跨模块引用显式 `nonApplied`，不静默漏改。被拒绝替代：classpath 可达域（0.3% 跨模块解析率 + Maven classpath 装配器与无状态成本模型冲突）。
  - 裁定 2：**引用搜索落点 = 操作器内嵌轻量索引，不消费 nop-code**——目标模块文件按需 JavaParser 解析 + 声明索引（简单名 → 声明节点）+ 引用过滤（同包 / import / 限定名），无持久索引、无外部服务依赖；nop-code 不进 v1 依赖拓扑（baseline §二 CORE→CODE 边 v1 不接线）。
- **nop-java-parser 消费面（live 形态）**：`io.nop.javaparser.JavaParseTool`——`parseJavaSource(SourceLocation, String)` → `JavaParserParseResult`（CompilationUnit 载体）；`resolveSymbol(boolean)` 开关控制（适配 v1 按结构面语义取值——默认关闭 SymbolSolver 或有测试钉定取值） `JavaSymbolSolver` 装配（默认 `NopTypeResolve extends ReflectionTypeSolver`，过滤 io.nop./java./jakarta.）。模块位于 nop-utils/nop-java-parser，pom 依赖 javaparser-core + javaparser-symbol-solver-core + nop-xlang。
- **nop-lint-java ScopeAnalyzer 复用形态（live 核对后裁定）**：`io.nop.lint.java.semantic.ScopeAnalyzer` 为 **public final class**（nop-lint-java），公开 API = `definitionOf(CompilationUnit, line, column)` → `Definition(name, line, column)` / `declaredNames` / `scopeKind` / `shadows`——可直接依赖消费。裁定：nop-refactor-java **直接 Maven 依赖 nop-lint-java**、经公开 API 消费（零复制、零反射、零行为修改——nop-lint 零修改红线）；同模块 `JavaScopeResolver`（ServiceLoader 注册的 ScopeResolver 实现）为消费形态参考。此公开面即 WI10/WI11 的 rename 消费面。
- **注册点**：nop-refactor/pom.xml `<modules>`（现两项，本 plan 增补 nop-refactor-java）；`docs-for-ai/01-repo-map/module-groups.md` :26 nop-refactor 行（现列 core + graphql，本 plan 增补 java 子模块）。
- **deps 状态**：WI9 deps = WI2（completed）、WI4（completed）、WI5（completed）——全满足，可开工；WI6/WI7 已落但非 deps，收敛改造与其既有测试面无依赖冲突。
- **预算口径**：vision 原则 9 上界锚点 = nop-lint 族 main Java 21,927 行 / 6 模块（2026-09-25 实测）；本 plan 新增量 = core operation 包（框架 + 两 operation）+ nop-refactor-java 薄适配模块，四段最小实现，行数收口统一归 WI13 审计。

## 执行面裁定记录（主会话预裁定的 live 细化，逐条编号供 Phase 引用）

1. **四段契约框架化形态（Decision）**：core 新增 operation 包——`RefactorOperation` SPI 语言无关，操作实现只拥有 check（可行性校验，结构化拒绝不可行输入）与 plan（产出 per-file 编辑列表 + nonApplied 预收集，不落盘）；**apply 与 verify 为框架 owned 的单点**：apply 经 WI4 `EditPlanApplier.apply`、verify 经 WI5 `RefactorVerifier.assemble`，operation 框架**不新增任何应用/校验实现**——plan 产出编辑列表后走与既有 codemod 链完全相同的 WI4 入口与 WI5 组装（红线，closure audit 以 src/main 口径核对 nop-refactor 内两调用点仅框架一处；test 域既有直接消费不在此列）。
2. **RewriteOperation = codemod 面收敛（Decision + 零行为变化硬约束）**：WI6/WI7 两条内联链收敛为框架下的 RewriteOperation 实现；NopRefactorBizModel 与 NopRefactorCli 改为消费框架唯一路径。
   **RewriteOperation 输入契约（R1 Blocker 钉死——face↔operation 分界）**：
   - **face 负责**（输入准备阶段）：path grammar 校验（三分支）→ 目标收集（显式/目录递归/扩展名分类）→ 逐文件读取（含 cap 字节级前置门）→ 收集期 nonApplied 预收集（skipped targets / no-rules language / cap 超限）→ 向 operation 递交**目标集 `List<PreparedTarget>`**（每项 = path + languageId + bytes 原文）+ **已加载规则集 `LoadedRuleSet`** + 豁免谓词 + LintEngine（profile 由 face 决定）。
   - **operation 负责**（执行阶段）：从加载门/豁免门起接管直至 assemble——规则加载门校验 → 逐 target lint → transformDegraded fail-closed → transformFixes 豁免门控 → 编辑列表 + nonApplied(CONFLICT/ROLLED_BACK) 预收集 → 框架 apply（WI4）→ 框架 verify（WI5 assemble）。
   - **分界效果**：GraphQL 的 cap 前置门/path grammar/异常包装 留在 face；CLI 的 TargetScanner.scan/退出码/渲染留在 face；operation 零 face 感知。
   - **Face nonApplies 并入**：face 的收集期 nonApplied 列表作为 operation plan 的入参传入（plan 在其末尾合并 face 预收集与执行期生成），assemble 消费合并后的完整列表。
   **零行为变化的操作定义（R1 Major-3 改锚）**：“既有断言/fixture 零修改且全绿 + 显式语义收敛清单之外无差异”。CLI 落盘纪律在框架统一为两阶段 + landed 枚举后，其非测试锚定行为随之收敛为 GraphQL 形态（安全方向统一），design 01 增注记录。
   **profile/engine 归属（R1 Major-2 裁定）**：operation 构造接受外部注入的 LintEngine（含 profile——两 face 各自钉 STANDARD 后传入），operation 零 profile 感知。CLI runFull 注入契约完整保留。列入 design 01 增注义务。
3. **符号解析适配 SPI（Decision + R1 Major-4 语义操作集）**：core 定义 `SymbolResolverAdapter`（语言无关接口）。**v1 语义操作集**：
   - **输入**：单模块源文件集合（path + content 对）；查询 = 目标定位（FQN 或 文件+字节偏移，直接转录 baseline §三 RenameInput——WI12 的 GraphQL RenameInput 与 core 输入模型一致）。
   - **操作**：(a) `buildIndex(files)` → 声明索引（简单名 → 声明位置+种类：局部变量/参数/字段/方法/类型）；(b) `resolveReference(index, target)` → 引用列表（同包/import/限定名绑定过滤，不可解析为显式 UNRESOLVED 结果形态非 null）。
   - **输出值类型**：语言中立——名字(String) + 字节区间(SourceRange) + 种类(枚举)；core 不知道 CompilationUnit。
   - 接口语义不出现 nop-code；nop-refactor-java 提供 Java 适配实现。依赖方向：java → core 单向（Maven），core 经 SPI 接口消费适配（运行时注入）——core 零 Java 适配依赖。
4. **ScopeAnalyzer 复用形态（live 核对后定）**：public 直接依赖消费（见 Current Baseline 对应条目）——不复制代码、不反射、不改 nop-lint-java。
5. **RenameOperation 输入模型 + plan v1 形态（Rule 24 裁定 + R1 Major-4）**：**输入三元组**直接转录 baseline §三 RenameInput——目标定位（FQN 或 文件+字节偏移）、新名（非空字符串）、符号域范围（v1 恒为单模块内——WI2 裁定 1）；check 校验三元组形态（非法定位/空新名 → 结构化拒绝）。plan() 抛 `NopRefactorException("not yet implemented: rename symbol resolution lands in WI10")`——fail-closed 显式失败。apply/verify 继承框架唯一路径。实际解析 WI10（第一档）/WI11（第二档）落地。
6. **接线验收形态（roadmap 明文）**：四段生命周期由框架同一段执行模板驱动——RewriteOperation 端到端测试证明 plan → WI4 → WI5 运行时连通；测试域 fixture operation（非 rewrite 的最小 operation）经同一框架路径跑通，证明框架 operation 无关、非 RewriteOperation 专用；RenameOperation 经同一框架构造与生命周期契约测试 + plan fail-closed 消息精确断言；叠加结构证据（调用点单点 grep 核对，src/main 口径 + core pom 无 nop-refactor-java 依赖）——共同构成"rename 与 codemod 走同一 plan/apply/verify 机制、无第二执行路径"的证明。
7. **roadmap 措辞处置**：WI9 原文"符号解析适配 SPI（语言无关 core，Java 适配先行；nop-java-parser 消费）"落为裁定 3/4 的模块与依赖形态；"Java 适配先行"= nop-refactor-java 是 SPI 的第一个实现模块。roadmap 正文按其 Rules 不回写。

## Goals

- **operation 框架（core operation 包）**：`RefactorOperation` SPI 四段契约框架化——check / plan 由操作实现，apply / verify 框架单点（WI4 唯一应用路径 + WI5 唯一校验路径）；红线：框架不新增应用/校验实现。
- **RewriteOperation（codemod 面收敛）**：WI6/WI7 已验证执行链收敛为框架实现，两 face 委托消费，零行为变化（既有测试不改全绿）。
- **SymbolResolverAdapter SPI（core）+ Java 适配（nop-refactor-java 新模块）**：消费 nop-java-parser 解析入口 + nop-lint-java ScopeAnalyzer 公开 API；v1 = SPI 骨架 + 内嵌轻量索引最小可测面（按需解析 + 声明索引 + 同包/import 绑定过滤）；符号域 v1 = 单模块内、不消费 nop-code（WI2 两裁定）。
- **RenameOperation（rename 骨架）**：check 最小校验 + plan fail-closed（"not yet implemented: rename symbol resolution lands in WI10"）；apply/verify 继承框架唯一路径。
- **模块注册**：nop-refactor/pom.xml modules 增补 nop-refactor-java；module-groups.md nop-refactor 行增补。
- **接线证明（closure 硬验收）**：rename 与 codemod 走同一 plan/apply/verify 机制，无第二执行路径（裁定 6 的四件证据）。

## Non-Goals

- 不做 LTK 式 Undo、重构脚本、participants（roadmap WI9 明文）。
- 不做 rename 实际符号解析与引用改写——WI10（局部变量/参数单文件）/ WI11（字段/非虚方法/类型模块域）承接；`symbolIntact` 正式计算 WI10 前身形态、WI12 正式语义。
- 不做 GraphQL/CLI 新面（WI6/WI7 已落）——本 plan 仅把既有两 face 收敛到框架路径（零行为变化），不新增 action、不改 schema、不改退出码语义、不改 cap 配置键。
- 不消费 nop-code（WI2 裁定 2——CORE→CODE 边 v1 不接线）；不建持久索引、不引入外部索引服务。
- 不改 nop-lint / nop-treesitter / nop-code 任何行为与公开契约（零修改红线；golden 字节不变 + 全测试绿是 closure audit 固定核对项）。
- 不改 WI5 载荷契约（消费面，字段不缩水也不扩张）；不改 RefactorRuleGates 门语义。
- 不做工程级 rename / classpath 装配器（WI2 已拒绝出预算，升级路径归 design 层另议）。

## Scope

### In Scope

- nop-refactor-core 新增 operation 包：`RefactorOperation` SPI + 框架执行模板（apply/verify 单点）+ RewriteOperation + RenameOperation 骨架 + 测试。
- nop-refactor-core 新增 `SymbolResolverAdapter` 语言无关接口。
- 新建 nop-refactor/nop-refactor-java：pom（依赖 nop-refactor-core + nop-java-parser + nop-lint-java）+ Java 适配实现 + 契约测试；nop-refactor/pom.xml 注册。
- NopRefactorBizModel 与 NopRefactorCli 委托改造（收敛到框架唯一路径，零行为变化）。
- `ai-dev/design/nop-refactor/01-architecture-baseline.md` 增注（operation 框架形态 + nop-refactor-java 落地与依赖方向 + ScopeAnalyzer 复用形态 + 内嵌索引 v1 面）；`docs-for-ai/01-repo-map/module-groups.md` 增补。

### Out Of Scope

- nop-lint / nop-treesitter / nop-code 行为面（零修改红线）。
- WI5 载荷与 verification 语义（消费面不改）。
- rename 解析语义、引用改写、stale-import 检查、symbolIntact 计算（WI10/WI11/WI12）。
- GraphQL rename 对、pattern 直给、glob 展开、规则内容（WI12 / 既有 Follow-up / WI8）。
- docs-for-ai 模块文档新建（WI13 收口统一新建；module-groups.md 一行增补除外）。

## Execution Plan

前置依赖（roadmap 拓扑序）：WI2 / WI4 / WI5 均已 completed（roadmap 已勾选）。Phase 1 开工前以 git status 自查 nop-refactor / nop-lint 工作树干净。若 WI5/WI6/WI7 landed 形态与本 plan Current Baseline 记载不一致，以 landed 源码 + design 01 增注为准并回写本节。

### Phase 1 - core operation 包：SPI + 框架 + RewriteOperation 收敛 + 接线测试（Decision + Fix）

Status: completed
Targets: `nop-refactor/nop-refactor-core/src/main|test/java/io/nop/refactor/core/operation/`、`nop-refactor/nop-refactor-graphql/src/main/java/io/nop/refactor/graphql/NopRefactorBizModel.java`、`nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/cli/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix`

- [x] core `operation` 包骨架（裁定 1）：`RefactorOperation` SPI（语言无关——check 可行性校验 / plan 产出编辑列表 + nonApplied 预收集，不落盘）+ 框架执行模板（apply/verify 框架 owned 单点：`EditPlanApplier.apply` 与 `RefactorVerifier.assemble` 各仅此一处调用；框架零新增应用/校验实现——红线）
- [x] RewriteOperation（裁定 2，codemod 面收敛）：WI6/WI7 已验证链（RuleSetLoader → RefactorRuleGates → 豁免门 → STANDARD LintEngine → transformFixes 门控 → transformDegraded fail-closed → 两阶段 compute/land）收敛为 operation 实现；face 专属面（cap 两键与 path grammar 输入门、CLI 选项/渲染/退出码）留 face 边界
- [x] NopRefactorBizModel 与 NopRefactorCli 委托改造：改消费框架唯一路径——零行为变化硬约束（裁定 2）：既有测试不修改且全绿
- [x] 接线测试（裁定 6 前两件）：RewriteOperation 经框架端到端（plan → WI4 apply → WI5 assemble，载荷可判读）+ 测试域 fixture operation（非 rewrite 最小 operation）经同一框架路径跑通——证明框架 operation 无关
- [x] `ai-dev/design/nop-refactor/01-architecture-baseline.md` 增注：operation 包形态（SPI 四段分工、apply/verify 框架单点红线、face 委托收敛、face 专属面边界、profile/engine 归属与注入契约、落盘纪律统一语义收敛清单）——纯增注不改写
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw compile -pl nop-refactor/nop-refactor-core -am` 成功
- [x] **端到端验证**（Minimum Rules #22）：RewriteOperation 从输入（规则集 + 目标文件）到 RefactorResult 载荷（edits / diff / verification / stats / nonApplied）完整路径测试贯通（与零行为变化证明的 face e2e 合并构成 Rule 22 完整覆盖）——组件级单测不替代
- [x] **接线验证**（Minimum Rules #23）：框架运行时确实调用 WI4 `EditPlanApplier` 与 WI5 `RefactorVerifier`——测试断言 fixture 规则产出的真实编辑（非空 diff 含标记物）与冲突/回滚 nonApplied 路径，非仅类型存在
- [x] **无静默跳过**（Minimum Rules #24）：check 对不可行输入显式结构化拒绝；nonApplied 预收集逐条带 reason + context（`NonApply` 构造器 fail-closed 消费）；无空方法体、无吞异常、无 placeholder 返回
- [x] **新功能测试清单**（Minimum Rules #25）：RefactorOperation SPI 契约、RewriteOperation plan 编辑列表与 nonApplied 预收集、fixture operation 无关性、face 委托后行为等价——逐项列出并落为测试
- [x] 零行为变化证明：TestNopRefactorBizModel / TestNopRefactorGraphQL / TestRefactorCliEndToEnd 既有断言与 fixture 零修改且全绿（scoped 核对）
- [x] design 01 增注已落档且与 landed 实现互洽（偏差即回写文档）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-refactor-java 模块 + 符号解析适配 SPI + RenameOperation 骨架 + 接线证明（Fix）

Status: completed
Targets: `nop-refactor/nop-refactor-java/`（新建）、`nop-refactor/nop-refactor-core/src/main|test/java/io/nop/refactor/core/`、`nop-refactor/pom.xml`、`docs-for-ai/01-repo-map/module-groups.md`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix + Proof`

- [x] core `SymbolResolverAdapter` 接口（裁定 3）：语言无关 SPI，v1 面 = WI2 裁定收敛的语义契约（单模块内符号域；声明索引构建 + 同包/import/限定名绑定过滤；不可解析为显式结果形态非 null 静默）；接口语义不出现 nop-code
- [x] nop-refactor-java 模块骨架：pom（parent nop-refactor；依赖 nop-refactor-core + nop-java-parser + nop-lint-java——裁定 4 直接消费 ScopeAnalyzer 公开 API；直接 import 的类按 Maven 卫生声明直接依赖）+ 包结构 + nop-refactor/pom.xml modules 注册
- [x] Java 适配实现（nop-refactor-java）：实现 core SPI——解析入口消费 nop-java-parser（JavaParseTool.parseJavaSource → JavaParserParseResult，live 形态）+ ScopeAnalyzer 公开 API（definitionOf / declaredNames / scopeKind / shadows——WI10/WI11 rename 消费面）+ 内嵌轻量索引最小可测面（按需解析 + 简单名声明索引 + 绑定过滤）；fixture 源码契约测试
- [x] RenameOperation（core operation 包，裁定 5）：check 最小输入形态校验（非法目标定位/新名结构化拒绝）+ plan() 抛 `NopRefactorException("not yet implemented: rename symbol resolution lands in WI10")`（消息精确，fail-closed）+ apply/verify 继承框架唯一路径
- [x] 接线证明（裁定 6 后两件，roadmap 明文验收）：RenameOperation 经与 RewriteOperation 同一框架执行模板的生命周期契约测试 + plan fail-closed 消息精确断言 + 结构证据（closure audit grep，src/main 口径：EditPlanApplier/RefactorVerifier 在 nop-refactor src/main 内仅框架单点调用，test 域既有直接消费不在此列；core pom 无 nop-refactor-java 依赖——依赖方向证明）
- [x] `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 行增补 nop-refactor-java 子模块
- [x] `ai-dev/design/nop-refactor/01-architecture-baseline.md` §二 增注：nop-refactor-java 落地、依赖方向（java → core 单向，SPI 注入）、ScopeAnalyzer 复用形态、内嵌索引 v1 面（WI2 裁定承接）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw compile -pl nop-refactor/nop-refactor-java -am` 成功——新模块进聚合构建
- [x] Java 适配契约测试绿：fixture 源码上声明索引构建、同包/import 绑定过滤命中与不命中、不可解析显式结果形态——SPI 可实现可消费的最小面证明（WI10/WI11 消费面已立）
- [x] **接线验证**（Minimum Rules #23）：(a) RenameOperation 与 RewriteOperation 经同一段框架执行模板跑 check/plan/apply/verify 生命周期（同构测试断言——rename 与 codemod 同一机制）；(b) 依赖方向：nop-refactor-core 的 pom 无 nop-refactor-java 依赖（core 语言无关，SPI 注入方向正确）
- [x] **无静默跳过**（Minimum Rules #24）：RenameOperation.plan 显式抛 not-yet-implemented 且消息有精确断言；适配对不可解析目标返回显式"不可解析"形态而非 null/空静默
- [x] **新功能测试清单**（Minimum Rules #25）：SymbolResolverAdapter 接口契约、Java 适配索引/过滤行为、RenameOperation check 拒绝面 + plan fail-closed——逐项列出并落为测试
- [x] 零行为红线自查（scoped git diff）：nop-lint / nop-treesitter / nop-code 零修改；nop-refactor-core 既有载荷与 verification 语义零修改
- [x] module-groups.md 增补 + design 01 §二 增注落档
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 in-scope 项完成，无残留未勾选 checklist
- [x] 四段契约框架化成立：check/plan/apply/verify 全部有框架载体；apply/verify 经 WI4/WI5 唯一实现；operation 框架零新增应用/校验实现（红线核对）
- [x] 接线证明成立（roadmap 明文验收）：rename 与 codemod 走同一 plan/apply/verify 机制——两实现同框架路径测试 + fixture operation 无关性 + 调用点单点结构证据（src/main 口径）
- [x] WI2 裁定转录义务已履行：本 plan Current Baseline 含 72.3% / 61.9% / 18(0.3%) 三个数字、"单模块内"符号域裁定、内嵌索引落点裁定及 analysis §五 指针（roadmap 点名）
- [x] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-code 零修改；WI5 载荷契约零缩水；WI6/WI7 face 行为零变化（既有测试零修改全绿）
- [x] owner docs 已同步：design 01 增注（operation 框架 + nop-refactor-java）、module-groups.md 增补
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端路径（operation 输入 → check/plan → apply → verify → RefactorResult）运行时连通（不只是类型存在），(b) 无空方法体/静默跳过/no-op 作为正常实现——RenameOperation.plan 的 fail-closed 异常是 Minimum Rules #24 的合规显式失败形态（有测试钉住），不计为空壳
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-core --severity high` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-java --severity high` 退出 0
- [x] `./mvnw test -pl nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-graphql -am` 全绿
- [x] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径——全仓存量违规在本 plan 范围外）
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 3（self-verification 载荷经 WI5 单点组装不缩水）、原则 6（fail-closed——not-yet-implemented 显式失败、nonApplied 结构化、不可解析显式形态）、原则 8（自完备——nop-java-parser / nop-lint-java 自家资产，不引入外部引擎）、原则 9（复杂度预算——core operation 包 + java 薄适配模块、四段最小实现、复用 WI4/WI5 机制不重造）为本 WI 重点核对项
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（范围口径：本 plan 变更文件零错误；全仓 gate 因并发在途文档 churn 波动时，提交前全局复跑）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/09-wi9-operation-framework.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- rename 解析与引用改写落地（SymbolResolverAdapter 的完整消费、名字冲突 fail-closed 进 nonApplied、引用计数断言的 symbolIntact 前身形态）：roadmap WI10/WI11 承接——Why Not Blocking Closure：WI9 的契约面是 SPI 骨架 + fail-closed 骨架操作，解析语义本就属后续 WI 的 scope。
- Refactor__previewRename / Refactor__applyRename GraphQL 接线与 symbolIntact 正式语义：roadmap WI12 承接（plan 06 裁定 a——schema 增字段非破坏增长）。
- 工程级 rename（classpath/工程装配器升级路径）：WI2 裁定已拒绝入预算；若未来立项走 design 层另议，不在本 roadmap。

## Closure

Status Note: WI9 全部交付落地——四段契约框架化为 core operation 包（RefactorOperation SPI check/plan + RefactorOperationRunner apply/verify 框架单点）；两 face（GraphQL BizModel / CLI）收敛为 face 准备 + runner 委托，零行为变化（既有测试 15+11+4 零修改全绿）；SymbolResolverAdapter 语言无关 SPI + nop-refactor-java 首个适配模块（内嵌索引 + 绑定过滤 + ScopeAnalyzer 公开 API 消费）；RenameOperation fail-closed 骨架（marker 精确钉住）经同一 runner 驱动；接线证明四件齐备（两实现同框架路径测试 + fixture operation 无关性真实落盘 + src/main 调用点单点 grep + core pom 零 java 依赖）。审计发现 4 Major（log 条目被并发 session 覆盖/import 顺序违规/Resolution 空引用崩溃缺陷/提交时序）已全部修复并经 delta 复核通过。
Completed: 2026-09-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_f96b9f84-614f-45b6-bdbc-1780120590e4（首轮全量审计 + delta 复核同一 agent）
- Audit Session: agent_f96b9f84-614f-45b6-bdbc-1780120590e4（2026-09-26）
- Evidence:
  - Phase 1 Exit Criteria 8/8 PASS（首轮审计）、Phase 2 Exit Criteria 8/8 PASS：结构证据独立复跑——EditPlanApplier.apply 仅 RefactorOperationRunner.java:60、RefactorVerifier 构造/assemble 仅 :96-98（src/main 口径，test 域按计划明文豁免）；core pom 零 nop-refactor-java
  - 零行为红线：nop-lint/nop-treesitter/nop-code 零源码修改（git diff 实证）；WI5 载荷文件 diff 为空；TestRefactorCliEndToEnd 15/11/4 零修改全绿
  - Anti-Hollow：16 个新 main 文件逐一读毕无空实现/吞异常；MarkerOperation 经同一 runner 真实落盘（operation 无关性）；RenameOperation plan fail-closed 位于任何落盘之前（Rule 24 合规）
  - vision 回扣：原则 3/6/8/9 PASS（新增 main Java 1,073 行/16 文件/2 模块，远低于 nop-lint 锚点，正式收口归 WI13）
  - delta 复核 4/4 PASS：log 条目落档（被并发 session 覆盖后重写）、nop-refactor 路径 import 违规归零、Resolution.of 空引用缺陷修复 + 契约测试（6/6 绿）、WI9 工作随本提交落地
  - `./mvnw test -pl core,java,graphql -am` BUILD SUCCESS（core 36 + java 6 + graphql 23，上游 816+104 全绿）
  - scan-hollow core/java --severity high 双 0 findings；check-doc-links --strict 0 errors；check-import-order nop-refactor 路径零违规
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/09-wi9-operation-framework.md --strict` 退出 0

Follow-up:

- rename 解析与引用改写落地（SymbolResolverAdapter 的完整消费、名字冲突 fail-closed 进 nonApplied、引用计数断言的 symbolIntact 前身形态）：roadmap WI10/WI11 承接——Why Not Blocking Closure：WI9 的契约面是 SPI 骨架 + fail-closed 骨架操作，解析语义本就属后续 WI 的 scope。（WI10 注意：definitionAt 同文件多同名声明消歧为 v1 简化面，ScopeAnalyzer 正式语义接管时需补歧义用例）
- Refactor__previewRename / Refactor__applyRename GraphQL 接线与 symbolIntact 正式语义：roadmap WI12 承接（plan 06 裁定 a——schema 增字段非破坏增长）。
- 工程级 rename（classpath/工程装配器升级路径）：WI2 裁定已拒绝入预算；若未来立项走 design 层另议，不在本 roadmap。

## Review Record

- **R1(2026-09-25, fresh session): REVISE** — 1 Blocker + 3 Major + 5 Minor, all fixed in text:
  - Blocker: RewriteOperation input contract (face/operation boundary) pinned — face handles path grammar + collection + read + cap + collection-phase nonApplies, submits PreparedTarget(path,languageId,bytes) + LoadedRuleSet + exemptions + LintEngine; operation takes over from load gate through assemble
  - Major-2: profile/engine ownership — operation accepts injected LintEngine, faces pin STANDARD; runFull injection contract preserved
  - Major-3: unified landing discipline = two-phase + landed enumeration (superset); CLI non-test-anchored timing converges; explicit semantic convergence list in design 01
  - Major-4: SymbolResolverAdapter semantic operation set + RenameOperation input triple (FQN or file+offset, new name, single-module scope) transcribed from baseline §三
  - Minor: test count 11, grep src/main scope, Phase 2 Item Types Decision+Fix, resolveSymbol v1 default off, Rule 22 face e2e combination
- **R2(2026-09-25, fresh session): REVISE** — 4 项文本修复落档于 commit a3403c8935：premature [x] 撤销 / Review Record 刷新 / Phase 2 Proof 标签 / design 01 增注枚举补全。该提交另声称的两项实际未按声称落地：src/main grep 口径（4 处）0/4 落地（delta check 补修，见下）；"Plan Status 格式"项在该提交中被误写为 added_fix_marker 字面量造成损坏，于 commit 8059902d89（2026-09-26）恢复为 `> Plan Status: active` 并清除矛盾占位段。
- **Delta check（2026-09-26, fresh session agent_f452d33f）**：R2 全部声称逐项对 live 文本与 git 证据复核——4 项真实落地 PASS、Plan Status 恢复 PASS（经 8059902d89）、src/main grep 口径 0/4 落地 FAIL（Major，已在本次修订补齐 4 处 + 本记录更正溯源）。代码前置抽查（RefactorVerifier.assemble / EditPlanApplier.apply 签名 / NopRefactorBizModel 内联链 / JavaParseTool / ScopeAnalyzer public API / WI2 analysis 数字 72.3%/61.9%/0.3%）全部 PASS。**结论：补修后放行执行。**
