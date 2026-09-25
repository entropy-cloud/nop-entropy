# 07 WI7 refactor CLI 批处理形态——同引擎 preview/apply 与退出码三态

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI7 原文 + Cross-Cutting 完成判定 + Framework/Platform Reuse 表 + Rules）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §三（preview/apply 两段语义）/§五（CLI 定位：Refactor__* 的批处理形态，引擎同一实现、不引入第二套语义）；`ai-dev/design/nop-refactor/00-vision.md` §三 原则 1（CLI 非第二套语义）/3（self-verification 载荷）/6（fail-closed）/9（复杂度预算）
> Related: `ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（WI4 已 completed——本 plan 的应用入口）；`ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md`（WI5——本 plan 的载荷与 verification 计算面前置）；`ai-dev/plans/nop-refactor/04-wi3-transform-dsl.md`（WI3 已 completed——v1 命令面消费的 transform 规则集）；后续 06（WI6 GraphQL 面，同引擎面的并行消费方）、08（WI8 闭环演示，CLI 入口为其同型补充证明）
> Review: 尚未进行对抗性 draft review（本文件为起草稿；R1 审查完成后在此回填结论）

## Purpose

执行 roadmap WI7（Item Type: Fix）：把 refactor 能力以 CLI 批处理形态暴露——同一引擎的 preview（UnifiedDiff 输出）/ apply（stats 汇总），退出码三态钉死（0/1/2），CLI 不引入第二套语义。CLI 层只新增参数解析、目标集合编排、渲染与退出码映射；编辑计算、应用、diff、verification、stats、nonApplied 全部复用既有面（WI3 transform 通道、WI4 应用入口、WI5 载荷与计算面）。

## Current Baseline

（live 已核对，2026-09-25；行号为核对时快照，执行时以 live 为准）

- **WI4 已 completed（plan 03，2026-09-25）**：应用入口 `EditPlanApplier.apply(Path, byte[], List<Fix>, LintLanguage, boolean dryRun)` → `EditPlanResult(finalSource, rolledBack, appliedEdits, skippedConflicts)`，落 nop-lint-core fix 包；FixApplier 多轮循环的每轮机械核已经它执行。WI5（plan 05）将对其结果载荷做 additive 扩展（被跳过编辑列表，路由裁定已在其 plan 内）。
- **WI3 已 completed（plan 04，2026-09-25）**：transform 规则 DSL 落地——transform 匹配不产报告型 Diagnostic，经 `LintResult` 独立 transform 编辑通道分流（RuleSetRunner）；CLI 层规则集豁免对 transform 编辑同构门控（applier 闭包应用同一 rule-id/path 谓词）；transform 匹配与降级各有独立 LintStats 计数（accessor 名以 live 为准）；baseline 对 transform 编辑构造性无交集。
- **WI5 尚未 landed（起草时点）**：RefactorResult / Verification / FileEdit / RefactorStats / NonApply 载荷类型、verification 计算面（重解析 + 残留 lint + stats + diff 组装，diff 复用 `UnifiedDiff.of`）由 plan 05 交付。**本 plan 开工门槛 = WI5 载荷 landed**（见 Execution Plan 前置声明）。
- **nop-refactor 模块**：由 plan 05（WI5）创建 `nop-refactor/nop-refactor-core` 模块骨架并注册根 pom。本 plan 的 CLI 落其中 cli 包，不开新模块。
- **nop-lint CLI 形态（live 已核对，"core 带 cli"先例）**：
  - **main 类落点** = `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/NopLintCli.java`——CLI 主入口落 core 模块 cli 包。双入口形态：`main(args)` → `System.exit(run(...))`；进程内入口 `run(args, out, err)` 返回退出码，测试直调（捕获流）。
  - **命令行解析栈 = 手写 fail-closed，无框架**：`CliOptions`（record）`parse(String...)` 逐项解析子命令 + 位置目标 + `--` 开关，任何非法输入抛 `NopLintException` 且消息携带 usage 行；子命令形态（check 默认 + match/test 两个次级子命令）。
  - **退出码语义实现点** = `NopLintCli` 上的 public 常量 `EXIT_OK=0` / `EXIT_VIOLATIONS=1` / `EXIT_INTERNAL=2`；映射点在 `runChecked` 尾部（成功/违规判定）+ `runFull` 的 `AbortedRun` 控制流（任何中止异常 → 2）。错误呈现：stderr `nop-lint: error:` 前缀 + 完整栈，不吞错，部分报告永不伪报成功。三态语义形态 = 0 成功结局 / 1 业务性未全过 / 2 内部错误中止（参数解析失败、目标缺失、规则加载失败、文件不可读均归 2）。
  - **渲染面** = `cli/Reporter` 接口（`render(outcome, Writer)`，writer 生命周期归 CLI 持有）+ `ConsoleReporter`（console golden 字节面）+ `MachineReporters`（sarif / checkstyle-xml / **json** / junit-xml）——`--format json` 机器可读先例**存在**。
  - **打包启动方式** = 纯 `java -cp` classpath 装配（nop-lint 各 pom 无 mainClass/assembly/shade 配置，javadoc 显式裁定分发形态）；运行时 classpath 须携带语言绑定与规则资源模块。
  - **目标扫描** = `cli/TargetScanner`（显式文件照收、目录递归展开、扩展名→语言表绑定）。
  - **e2e 形态** = `TestNopLintCliEndToEnd`（`runFull` + 显式 rulesPrefix 指向 fixture 规则集 + 临时目录文件，断言退出码与 stdout/stderr）与 `TestNopLintCliExitCodes`（Run record 捕获 exitCode/stdout/stderr，三态逐项断言）。
  - **豁免/扫描组件落点** = `ExemptionFilter` 在 nop-lint-core `suppress/` 包（经依赖传递可供 refactor 消费）；`MatchCommand` 是 pattern 直给先例，但属"报"面查询（编译 pattern 打印匹配，无编辑产出），不构成改写面 pattern 直给的复用基础。
- **架构定位（baseline §五）**：CLI 是 Refactor__* action 的批处理形态，供流水线与无 GraphQL 运行时使用；引擎同一实现，CLI 不引入第二套语义（对齐 nop-lint CLI/Maven/GraphQL 共享 `CheckRunner` 的既有形态）。baseline §三：preview/apply 两段语义（dry-run 与落盘分离），无会话状态、无 plan-token；apply 内部重算编辑计划（操作确定性保证两次计算一致）。
- roadmap Rules：行为红线 = nop-lint / nop-treesitter 零行为修改；本 plan 无上游扩展（与 WI5 不同——WI5 的两处 additive 扩展已在其 plan 内裁定，本 plan 只读消费其 landed 形态）。

## Goals

- **落点（预算内）**：CLI 落 `nop-refactor/nop-refactor-core` 内新增 cli 包（沿 nop-lint "core 带 cli" 先例——NopLintCli 即落 nop-lint-core cli 包）；**不开新模块**（vision 原则 9）。
- **同引擎红线（防双引擎）**：CLI 与 WI6 GraphQL 面消费同一组引擎面，引擎面清单钉死——(1) 规则集加载 = RuleSetLoader / LintEngine 显式规则集入口；(2) transform 编辑计算 = LintResult transform 通道（WI3）；(3) 编辑应用 = EditPlanApplier（WI4）；(4) diff 组装 = UnifiedDiff.of（WI5 复用面）；(5) verification / stats / nonApplied = WI5 计算面与载荷类型。CLI 只新增：参数解析、目标集合展开、逐文件循环编排、渲染、退出码映射。**CLI 不新增引擎逻辑、不新增载荷字段**。
- **preview 通路**：transform 规则集 + 目标文件集合 → 逐文件编辑计算 → WI4 入口 dryRun（不落盘）→ UnifiedDiff 输出（复用 WI5 diff 组装）+ nonApplied 清单。
- **apply 通路**：同一引擎面重算编辑计划（无状态——单次调用内先算后落，无 plan-token/会话，对齐 baseline §三）→ WI4 入口原子落盘 → WI5 verification 计算面 + RefactorStats → stats 汇总 + nonApplied 清单。
- **退出码三态**：0/1/2 边界在本 plan 内显式定义（见"退出码三态边界"节）；实现形态对齐 nop-lint（CLI 类上 public 常量 + 中止控制流 + `error:` 前缀错误呈现）。
- **命令面从简（v1）**：preview / apply 两子命令 + transform 规则集指定 + 目标文件集合（显式文件或目录）+ `--json` 机器可读形态（nop-lint `--format json` 先例存在，故纳入对齐——同一载荷的另一种渲染）。参数风格沿 nop-lint：子命令 + 位置目标 + `--` 开关，手写 fail-closed 解析，解析失败 → 退出码 2 + usage。
- **豁免门控同构**：复用 nop-lint `ExemptionFilter` 谓词形态对 transform 编辑同构门控（WI3 裁定 4 的 refactor 消费面），被豁免编辑归 nonApplied(out-of-scope)；不建第二套豁免实现。
- **零行为红线**：nop-lint / nop-treesitter 既有行为零修改（只读消费）；nop-lint 全 6 模块测试零改动通过、golden 字节面不变。

## Non-Goals

- **pattern 直给（不经规则集）——显式裁定为 Non-Goal**：从裸 pattern 到编辑计划需要新的组装逻辑，属引擎面而非 CLI 壳职责（同引擎红线禁止 CLI 层私建）；nop-lint `match` 子命令先例是查询面而非编辑面，复用不成立。**不对称裁定显式记录：pattern 直给由 WI6 GraphQL 面 RewriteInput 承载，CLI v1 只收 transform 规则集**——防 scope 膨胀；CLI 侧补 pattern 直给须另立 plan 并回写本裁定。
- 不建 nop-refactor-graphql（WI6）、不做 rename 面（WI9–WI12）、不做 check/plan/apply/verify 操作框架化（WI9）。
- 不新增载荷字段、不新增 nonApplied 第四分类——CLI 渲染 WI5 载荷 as-is。
- 不改 nop-lint 任何行为面（报告字节、退出码、CheckRunner、豁免语义、transform 通道零修改）。
- 不做 nop-lint check 高级面的 refactor 对位：`--profile` 资源门开关、`--cache`、baseline 系列均不进 v1（baseline 对 transform 编辑构造性无交集，WI3 裁定 4；refactor CLI 固定全量生成路径）。
- 机器可读格式家族（sarif / checkstyle-xml / junit-xml）不进 v1——只做 `--json`。
- 不新增打包机制（shade/assembly/mainClass manifest）——分发沿 `java -cp` classpath 装配先例。

## 退出码三态边界（Decision——本 plan 显式定义，执行与 closure audit 逐格对照）

**对齐方式（live 核对结论）**：nop-lint 的三态实现点 = `NopLintCli` public 常量 + `runChecked` 尾部映射 + `AbortedRun` 中止控制流。WI7 在 refactor CLI 类上以同型落地：public 退出码常量（值钉死 0/1/2，命名沿 `EXIT_*` 风格随实现）+ 中止异常控制流把错误路径收敛到 2。三态边界：

- **0（全部可应用 / 已全部应用）**：运行完成且 nonApplied 为空。preview = 目标集合上全部 transform 编辑可应用（无冲突跳过、无豁免剔除、无目标未解析）；apply = 全部编辑已原子落盘（无守卫回滚、无跳过项）。
- **1（存在 nonApplied——部分未应用 / 未落盘）**：运行完成但 nonApplied 非空——conflict（重叠编辑被 Fixer 语义跳过，经 WI5 扩展的被跳过编辑列表实产）/ out-of-scope（规则集豁免命中）/ unresolved-target（目标未解析）任一来源。preview 语义 = 部分未纳入计划；apply 语义 = 部分未落盘。部分未应用不是错误而是结构化结果（baseline §四 裁定）。**守卫回滚归类**：apply 中 `rolledBack=true` 的文件不计入已应用集合、按 nonApplied 呈现（映射进既有三分支，不新增第四分支；具体归类落 design 01 增注）。
- **2（错误中止：解析 / 加载 / IO）**：CLI 参数解析失败（stderr 携带 usage 行）、transform 规则集加载/编译失败、目标路径不存在 / 不可读 / 语言不可绑定、落盘 IO 失败（原子写失败路径）、`transformDegraded > 0`（资源门降级导致编辑丢失——v1 固定全量生成路径下不应发生，发生即内部错误）、载荷组装内部错误。呈现对齐 nop-lint：`nop-refactor: error:` 前缀（措辞随实现）+ stderr + 完整栈 + 不吞错，部分报告永不伪报成功。

**边界注记（防伪报）**：

- 落盘 IO 失败归 2 而非 1：nonApplied 三分支无一可归因 IO 失败，新增第四分类即违反"CLI 不新增载荷字段"红线——不可归因到三分支的失败是运行错误，不是批量结果的一部分（与 baseline §四 "fail-closed 指文件级失败可归因"的定义一致）。
- 1 与 2 的判定分界 = "运行是否完成"：载荷已组装完成 → 0/1 按 nonApplied 判定；中止（未完成）→ 2。
- 语言不可绑定的目标文件 fail-closed（退出码 2），不静默跳过——refactor 是改写面，宁可中止不可漏改（nop-lint check 的 SkippedFiles 面是"报"面既有行为，不对位照搬）。

## Scope

### In Scope

- `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/cli/`：CLI 入口类（双入口形态）、退出码常量、fail-closed 参数解析、preview/apply 逐文件编排、豁免谓词接线、渲染面（console + --json）。
- `nop-refactor/nop-refactor-core/src/test/java/...`：焦点测试 + 退出码矩阵测试 + `TestRefactorCliEndToEnd` 端到端测试（临时目录 fixture 规则集 + 目标文件）。
- `ai-dev/design/nop-refactor/01-architecture-baseline.md` 增注：v1 命令面裁定（子命令/参数清单/规则集定位形态/目标集合解析形态）、渲染面裁定、退出码三态边界、守卫回滚的 nonApplied 归类、分发形态（java -cp）。
- `ai-dev/logs/` 对应日期条目。

### Out Of Scope

- nop-lint / nop-treesitter 的任何行为面与测试基线（本 plan 零上游扩展）。
- nop-refactor-graphql 模块（WI6）；rename 面（WI9+）；操作框架（WI9）。
- pattern 直给（Non-Goal 显式裁定，见上）；机器可读格式家族（sarif 等）；`--profile` / `--cache` / baseline 对位面。
- docs-for-ai 模块文档新建（归 WI13 收口）。

## Execution Plan

前置依赖（roadmap 拓扑序 deps: WI4, WI5）与执行顺序：

- **WI4（plan 03）已 completed + roadmap WI4 已勾选**——应用入口门槛已满足。
- **WI5（plan 05）是本 plan 的开工门槛**：Phase 1 开工前置 = plan 05 已 completed 且 roadmap WI5 已勾选（RefactorResult/Verification/FileEdit/RefactorStats/NonApply 载荷 + verification 计算面 + UnifiedDiff.of diff 组装 + Fixer.MergeResult/EditPlanResult additive 扩展全部 landed）。**若执行时未 landed，本 plan 保持 blocked 等待，不得自带第二套载荷**（禁止 CLI 自建 RefactorResult 同构 record 或第二套 stats/verification 组装）。
- **与 WI6 可并行**（无 deps 关系，编号非屏障）；两侧消费同一组引擎面，任一侧需要新引擎面时走各自 plan 的上游扩展裁定，不在渲染壳内私建。

若 WI5 landed 形态与本 plan 记载不一致，以 landed 源码 + design 01 增注为准并回写本节 baseline。

### Phase 1 - CLI 骨架与 preview 通路（Decision + Fix）

Status: planned
Targets: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/cli/`、`nop-refactor/nop-refactor-core/src/test/java/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix`

- [ ] 前置门核对：plan 05（WI5）已 completed 且 roadmap WI5 已勾选——载荷类型、verification 计算面、diff 组装、additive 扩展（被跳过编辑列表）均 landed；未 landed 则本 plan 整体保持 blocked，记入当日 log
- [ ] Decision（落 design 01 增注）：v1 命令面裁定——preview/apply 两子命令、参数清单（transform 规则集定位 + 目标文件集合 + `--json`）、规则集定位形态（沿 RuleSetLoader classpath VFS 前缀先例；e2e 经显式 prefix 注入 fixture 规则集，对齐 TestNopLintCliEndToEnd 形态）、目标集合解析形态（沿 TargetScanner 先例；语言不可绑定 fail-closed → 退出码 2）、分发形态（`java -cp` classpath 装配，不新增打包机制）
- [ ] Decision（落 design 01 增注）：渲染面裁定——沿 nop-lint `cli/Reporter` 接口形态先例（render(outcome, Writer) 渲染器接口 + writer 生命周期归 CLI 持有 + console 与机器格式分离）在 cli 包定义 refactor 侧渲染器（渲染 preview 输出面 / RefactorResult）；**不复用 CheckOutcome 类型面**（载荷类型不兼容，强行复用即破坏 WI5 载荷契约）——roadmap "复用 Reporter 面"按接口形态先例兑现
- [ ] CLI 骨架：入口类双入口形态（`main` → System.exit；进程内 `run(args, out, err)` 返回退出码供测试直调）+ 退出码 public 常量（0/1/2）+ 中止控制流 + 手写 fail-closed 参数解析（非法输入抛模块异常且消息携带 usage 行，异常形态沿 error-handling 两层策略，随 plan 05 landed 的模块异常面）
- [ ] preview 通路：transform 规则集加载 → 逐文件编辑计算（LintEngine 显式规则集入口 → LintResult transform 通道）→ WI4 `EditPlanApplier.apply(dryRun=true)` → nonApplied 收集（conflict 经 WI5 扩展的被跳过编辑列表；out-of-scope 经豁免谓词；unresolved-target 按目标解析结果）→ diff 组装（UnifiedDiff.of，复用 WI5 组装面）+ nonApplied 清单渲染（不落盘）
- [ ] 豁免门控接线：复用 nop-lint `ExemptionFilter` 谓词形态对 transform 编辑同构门控（WI3 裁定 4 的 refactor 消费面），不建第二套豁免实现
- [ ] `--json` 机器可读渲染：同一载荷的另一种渲染（对齐 nop-lint `--format json` 先例），不新增载荷字段
- [ ] 新功能测试清单（Phase 1 内完成）：(a) 干净目标 preview → 退出码 0 + stdout diff 文本含预期 +/- 行 + nonApplied 空；(b) 重叠冲突样本 → conflict nonApplied 项（含上下文）+ 退出码 1；(c) 豁免命中样本 → out-of-scope nonApplied 项 + 退出码 1；(d) 非法参数 → 退出码 2 + stderr usage 行；(e) 规则集加载失败 → 退出码 2 + stderr 错误前缀；(f) `--json` 渲染与 console 渲染同载荷（字段集合一致、机器可判读）；(g) 语言不可绑定目标 → 退出码 2（fail-closed，不静默跳过）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新功能测试清单 (a)–(g) 全绿，且逐项可在测试源码中定位
- [ ] **接线验证**（Minimum Rules #23）：preview 输出的 diff 确实产自 WI4 入口 dryRun 与 WI5 diff 组装面——以调用链证明（CLI 逐文件循环内 `EditPlanApplier.apply(dryRun=true)` 调用点 + UnifiedDiff.of 组装调用点），并以 grep 证实 cli 包内无第二套 merge/写盘/diff 实现
- [ ] **无静默跳过**（Minimum Rules #24）：语言不可绑定 / 目标不可读 fail-closed（(g)(d) 证据）；nonApplied 的"空"与"未计算"机器可区分；新增公共方法/分支无空方法体、无吞异常、无 placeholder 返回
- [ ] **新功能测试清单**（Minimum Rules #25）：(a)–(g) 显式列出并落为测试
- [ ] design 01 增注已落档（命令面 / 渲染面 / 退出码三态边界）且与 landed 实现逐条互洽（纯增注，不改写既有内容）
- [ ] 零行为红线（scoped diff）：git diff 按 file scope 证实 nop-lint / nop-treesitter 既有类零修改
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - apply 通路与退出码矩阵 + e2e（Fix + Proof）

Status: planned
Targets: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/cli/`、`nop-refactor/nop-refactor-core/src/test/java/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Fix + Proof`

- [ ] apply 通路：同一引擎面重算编辑计划（无状态——单次调用内先算后落，无 plan-token/会话，对齐 baseline §三）→ WI4 `EditPlanApplier.apply(dryRun=false)` 原子落盘 → 逐文件结果聚合（rolledBack / appliedEdits / skipped）→ WI5 verification 计算面 + RefactorStats → RefactorResult 组装 → stats 汇总 + nonApplied 渲染
- [ ] 守卫回滚归类落地：`rolledBack=true` 文件不计入已应用集合、按 nonApplied 既有三分支呈现（不新增第四分支）——归类形态并入 design 01 增注
- [ ] 退出码矩阵补全（apply 侧）：全应用 → 0；nonApplied 非空（含守卫回滚）→ 1；落盘 IO 失败 / 规则加载失败 / 解析错误 / `transformDegraded > 0` → 2
- [ ] e2e：`TestRefactorCliEndToEnd` 形态——临时目录 fixture 规则集 + 目标文件（对齐 TestNopLintCliEndToEnd / TestNopLintCliExitCodes 的 Run record 捕获形态）：preview 断言 diff 文本与退出码、apply 断言落盘内容与退出码；nonApplied 场景（冲突 / 豁免）→ 退出码 1；解析错误 / 规则加载失败 → 退出码 2
- [ ] 新功能测试清单（显式）：(a) apply 全应用样本 → 退出码 0 + 文件内容逐字节断言 + stats 可判读（影响文件数 / 编辑数）；(b) apply 守卫回滚样本 → 回滚后内容落盘 + 该文件 nonApplied 呈现 + 退出码 1；(c) apply 混合样本（部分冲突）→ 已应用文件落盘 + conflict nonApplied 项 + 退出码 1；(d) apply 落盘 IO 失败（不可写目标）→ 退出码 2；(e) apply 规则集加载失败 → 退出码 2；(f) apply `--json` 与 console 同载荷；(g) preview 后磁盘内容零变化（预览不落盘物证）
- [ ] 实现与 design 01 增注互洽核对（退出码边界每格与测试一一对照；实现偏差即回写文档）
- [ ] 零行为红线自查（scoped）：git diff 按 file scope 证实 nop-lint / nop-treesitter 既有类零修改
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新功能测试清单 (a)–(g) 全绿，且逐项可在测试源码中定位
- [ ] **端到端验证**（Minimum Rules #22）：`TestRefactorCliEndToEnd` 从 CLI 进程内入口经规则集加载 → transform 编辑计算 → WI4 入口落盘 → WI5 verification/stats 组装 → 渲染输出 → 退出码完整跑通；preview 与 apply 两子命令各有完整链路证据（组件级单测不能替代）
- [ ] **接线验证**（Minimum Rules #23）：apply 确实经 WI4 入口落盘——cli 包内无自建写盘调用（grep 证实 Files.write/atomic-move 仅存在于 EditPlanApplier 路径）；stats / verification / nonApplied 确实产自 WI5 计算面与载荷类型（无第二套组装）
- [ ] **无静默跳过**（Minimum Rules #24）：守卫回滚不伪报已应用（(b) 证据）；IO 失败不吞、不伪报（(d) 证据）；退出码 1/2 边界每格有测试锚定，无第四态
- [ ] **新功能测试清单**（Minimum Rules #25）：(a)–(g) 显式列出并落为测试
- [ ] design 01 增注（退出码矩阵 + 守卫回滚归类）与 landed 实现逐条互洽
- [ ] 零行为红线（scoped diff）：nop-lint / nop-treesitter 零修改；`./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java,nop-lint/nop-lint-js,nop-lint/nop-lint-nop,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql -am` 零改动通过（golden 字节面不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 项完成，无残留未勾选 checklist
- [ ] 退出码三态边界 = 本 plan 定义逐格成立（closure audit 逐格对照测试证据：0 = nonApplied 空、1 = nonApplied 非空（conflict/out-of-scope/unresolved-target + 守卫回滚归类）、2 = 中止错误（解析/加载/IO/降级）四类齐全；无第四态、无边界外样本）
- [ ] 同引擎红线成立：CLI 不新增引擎逻辑（规则加载/编辑计算/应用/diff/verification/豁免各只有一份实现、全在既有面）；不新增载荷字段（渲染面与 WI5 载荷字段逐一对照无增减）；WI6 消费同一组引擎面的接线清单可对照
- [ ] 零行为红线（scoped diff）：nop-lint / nop-treesitter 既有行为零修改；nop-lint 全 6 模块测试绿、golden 字节面不变
- [ ] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端路径从 CLI 进程内入口到渲染输出与退出码运行时连通（不只是类型存在），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [ ] owner docs 已同步：design 01 增注（命令面/渲染面/退出码三态边界/守卫回滚归类/分发形态）落档（否则不得关闭）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-core --severity high` 退出 0
- [ ] 代码规范检查通过：`node ai-dev/tools/check-import-order.mjs` 退出 0（本 plan 新增/变更文件零违规——范围口径）
- [ ] vision 原则 1–9 回扣核对（closure audit 执行）：原则 1（CLI 是同一引擎批处理形态、非第二套语义）、原则 3（self-verification 载荷不缩水、不新增字段）、原则 6（fail-closed 错误面与退出码边界）、原则 9（预算——cli 包落 core 内、不开新模块、不新增打包机制）为本 WI 重点核对项
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-core -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/07-wi7-cli-batch-form.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- （若有执行中发现的优化项，关闭时填写；in-scope live defect 不得入列）

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
