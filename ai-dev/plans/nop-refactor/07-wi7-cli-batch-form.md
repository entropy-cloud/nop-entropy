# 07 WI7 refactor CLI 批处理形态——同引擎 preview/apply 与退出码三态

> Plan Status: active
> Last Reviewed: 2026-09-25
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI7 原文 + Cross-Cutting 完成判定 + Framework/Platform Reuse 表 + Rules）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §三（preview/apply 两段语义）/§五（CLI 定位：Refactor__* 的批处理形态，引擎同一实现、不引入第二套语义）；`ai-dev/design/nop-refactor/00-vision.md` §三 原则 1（CLI 非第二套语义）/3（self-verification 载荷）/6（fail-closed）/9（复杂度预算）
> Related: `ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（WI4 已 completed——本 plan 的应用入口）；`ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md`（WI5——本 plan 的载荷与 verification 计算面前置）；`ai-dev/plans/nop-refactor/04-wi3-transform-dsl.md`（WI3 已 completed——v1 命令面消费的 transform 规则集）；后续 06（WI6 GraphQL 面，同引擎面的并行消费方）、08（WI8 闭环演示，CLI 入口为其同型补充证明）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——4 Major（preview 载荷组装路径未钉死致"第二套组装"漏洞；退出码边界缺 profile 门控格且 transformDegraded 格不可构造；preview 侧 dryRun 守卫回滚无主；混合规则集静默忽略）+ 6 Minor；全部修订后执行（R2 复核见 Review Record）

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
  - **豁免/扫描组件落点** = `ExemptionFilter` 在 nop-lint-core `suppress/` 包（nop-refactor-core 对 nop-lint-core 为直接依赖，public 谓词直接可用）；`MatchCommand` 是 pattern 直给先例，但属"报"面查询（编译 pattern 打印匹配，无编辑产出），不构成改写面 pattern 直给的复用基础。
- **架构定位（baseline §五）**：CLI 是 Refactor__* action 的批处理形态，供流水线与无 GraphQL 运行时使用；引擎同一实现，CLI 不引入第二套语义（对齐 nop-lint CLI/Maven/GraphQL 共享 `CheckRunner` 的既有形态）。baseline §三：preview/apply 两段语义（dry-run 与落盘分离），无会话状态、无 plan-token；apply 内部重算编辑计划（操作确定性保证两次计算一致）。
- roadmap Rules：行为红线 = nop-lint / nop-treesitter 零行为修改；本 plan 无上游扩展（与 WI5 不同——WI5 的两处 additive 扩展已在其 plan 内裁定，本 plan 只读消费其 landed 形态）。

## Goals

- **落点（预算内）**：CLI 落 `nop-refactor/nop-refactor-core` 内新增 cli 包（沿 nop-lint "core 带 cli" 先例——NopLintCli 即落 nop-lint-core cli 包）；**不开新模块**（vision 原则 9）。
- **同引擎红线（防双引擎）**：CLI 与 WI6 GraphQL 面消费同一组引擎面，引擎面清单钉死——(1) 规则集加载 = RuleSetLoader / LintEngine 显式规则集入口；(2) transform 编辑计算 = LintResult transform 通道（WI3）；(3) 编辑应用 = EditPlanApplier（WI4）；(4) diff 组装 = UnifiedDiff.of（WI5 复用面）；(5) verification / stats / nonApplied = WI5 计算面与载荷类型。CLI 只新增：参数解析、目标集合展开、逐文件循环编排、渲染、退出码映射。**CLI 不新增引擎逻辑、不新增载荷字段**。
- **preview 通路**：transform 规则集 + 目标文件集合 → 逐文件编辑计算 → WI4 入口 dryRun（不落盘）→ **`RefactorVerifier.assemble(applied=false)` 组装完整载荷**（diff + verification + stats 一次产出——单一组装路径，R1 Major-1 裁定；CLI 内禁止直调 UnifiedDiff.of 手工拼装）。
- **apply 通路**：同一引擎面重算编辑计划（无状态——单次调用内先算后落，无 plan-token/会话，对齐 baseline §三）→ WI4 入口原子落盘 → **同一 `RefactorVerifier.assemble(applied=true)`** → stats 汇总 + nonApplied 清单。
- **规则集加载门（fail-closed，R1 Major-2/4 裁定 + R2 Major B 钉死机制）**：规则集加载后先做两道校验再执行——(a) **规则形态校验**：任何规则 `getTransform()==null` 即拒绝（含纯 report 规则、fix 规则、xscript 规则——refactor CLI 的载荷面只有改写通道，report 诊断被静默丢弃即伪报成功；沿 CheckRunner.verifyRuleLanguages 的加载期 fail-closed 先例）；(b) **profile 门校验 = "transform 规则 requires 为空才接受，非空即拒绝"**（空 requires 在引擎 gate 恒 RUN；非空 requires 的规则在 STANDARD 下可能 SKIP/DEGRADE——其 transform 编辑将从不被计算，静默漏改违反"宁可中止不可漏改"；引擎 gate 逻辑全私有且 stats 计数无规则归因，逐规则复刻 gate = 引擎门第二实现，违反同引擎红线——requires 空判定是零重复的保守等价门）。**加载门恒以 STANDARD 判定，与注入 engine 携带的 profile 无关**。校验失败 = 结构化错误退出码 2。
- **退出码三态**：0/1/2 边界在本 plan 内显式定义（见"退出码三态边界"节）；实现形态对齐 nop-lint（CLI 类上 public 常量 + 中止控制流 + `error:` 前缀错误呈现）。
- **命令面从简（v1）**：preview / apply 两子命令 + transform 规则集指定 + 目标文件集合（显式文件或目录）+ `--json` 机器可读形态（nop-lint `--format json` 先例存在，故纳入对齐——同一载荷的另一种渲染）。参数风格沿 nop-lint：子命令 + 位置目标 + `--` 开关，手写 fail-closed 解析，解析失败 → 退出码 2 + usage。
- **豁免门控同构**：复用 nop-lint `ExemptionFilter` 谓词形态对 transform 编辑同构门控（WI3 裁定 4 的 refactor 消费面），被豁免编辑归 nonApplied(out-of-scope)；不建第二套豁免实现。
- **零行为红线**：nop-lint / nop-treesitter 既有行为零修改（只读消费）；nop-lint 全 6 模块测试零改动通过、golden 字节面不变。

## Non-Goals

- **pattern 直给（不经规则集）——显式裁定为 Non-Goal**：从裸 pattern 到编辑计划需要新的组装逻辑，属引擎面而非 CLI 壳职责（同引擎红线禁止 CLI 层私建）；nop-lint `match` 子命令先例是查询面而非编辑面，复用不成立。**不对称裁定显式记录：pattern 直给由 WI6 GraphQL 面 RewriteInput 承载，CLI v1 只收 transform 规则集**——防 scope 膨胀；CLI 侧补 pattern 直给须另立 plan 并回写本裁定。
- 不建 nop-refactor-graphql（WI6）、不做 rename 面（WI9–WI12）、不做 check/plan/apply/verify 操作框架化（WI9）。
- 不新增载荷字段——CLI 渲染 WI5 载荷 as-is（NonApply.Reason 的 ROLLED_BACK 第四枚举与 SkippedBuckets 第四桶由 plan 06 裁定 9 additive 承载并经 design 01 §四增注 owns，非本 plan 新增）。
- 不改 nop-lint 任何行为面（报告字节、退出码、CheckRunner、豁免语义、transform 通道零修改）。
- 不做 nop-lint check 高级面的 refactor 对位：`--profile` 资源门开关、`--cache`、baseline 系列均不进 v1（baseline 对 transform 编辑构造性无交集，WI3 裁定 4；refactor CLI 固定全量生成路径）。
- 机器可读格式家族（sarif / checkstyle-xml / junit-xml）不进 v1——只做 `--json`。
- 不新增打包机制（shade/assembly/mainClass manifest）——分发沿 `java -cp` classpath 装配先例。

## 退出码三态边界（Decision——本 plan 显式定义，执行与 closure audit 逐格对照）

**对齐方式（live 核对结论）**：nop-lint 的三态实现点 = `NopLintCli` public 常量 + `runChecked` 尾部映射 + `AbortedRun` 中止控制流。WI7 在 refactor CLI 类上以同型落地：public 退出码常量（值钉死 0/1/2，命名沿 `EXIT_*` 风格随实现）+ 中止异常控制流把错误路径收敛到 2。三态边界：

- **0（全部可应用 / 已全部应用）**：运行完成且 nonApplied 为空，且无任何 profile skip/degrade。preview = 目标集合上全部 transform 编辑可应用（无冲突跳过、无豁免剔除、无目标未解析、无守卫回滚）；apply = 全部编辑已原子落盘。
- **1（存在 nonApplied——部分未应用 / 未落盘）**：运行完成但 nonApplied 非空——conflict（重叠编辑被 Fixer 语义跳过，经 WI5 扩展的被跳过编辑列表实产）/ out-of-scope（规则集豁免命中）/ unresolved-target（v1 codemod 面不产此来源——枚举位保留，rename 面接管，R1 Minor-6）/ **守卫回滚**（preview dryRun 与 apply 均可触发 `rolledBack=true`——preview 的提议会破坏语法同样进 1，R1 Major-3）任一来源。守卫回滚粒度 = 每文件一条 nonApplied——reason 映射已由 plan 06 裁定 9 钉死为 **`NonApply.Reason.ROLLED_BACK`（additive 第四枚举，design 01 §四增注 owns；WI5 契约测试 3→4 + SkippedBuckets 第四桶同步）**，本 plan 早前"映射进既有三分支/不新增第四分类"措辞由该裁定取代（R2 Major-B 跨 plan 同步）。部分未应用不是错误而是结构化结果（baseline §四 裁定）。
- **2（错误中止：解析 / 加载 / IO）**：CLI 参数解析失败（stderr 携带 usage 行）、transform 规则集加载/编译失败、**规则集加载门校验失败（混入非 transform 规则 / profile SKIP/DEGRADE 规则——R1 Major-2/4）**、目标路径不存在 / 不可读 / 语言不可绑定、落盘 IO 失败（原子写失败路径）、`transformDegraded > 0`（**STANDARD 下 budget ladder 关闭 fix 门**导致改写编辑丢失——R2 Major A 改锚：FAST 下 fixOpen=false 连该计数都不产出、恒为零，这正是钉死 STANDARD 的理由；该降级可正常发生，但对改写面意味着静默丢编辑，故收敛为中止错误）、载荷组装内部错误。呈现对齐 nop-lint：`nop-refactor: error:` 前缀（措辞随实现）+ stderr + 完整栈 + 不吞错，部分报告永不伪报成功。

**profile 钉死与测试注入（R1 Major-2 裁定 + R2 Major A 改锚）**：生产入口固定 `LintProfile.STANDARD`（fix 生成开启、transform 通道活跃）。**FAST 的语义是关门而非降级**：`fixOpen = profile != FAST` 下 transform 分支整体不产出、`transformDegraded` 恒为 0（引擎自家测试钉死此点）——不可用作降级格构造器。`transformDegraded>0` 格（exit 2）的确定性测试锚 = **CLI 侧退出码映射的组件单测**：样本经 public `LintStats.Builder.incTransformDegraded(int)` 构造（零上游扩展红线），断言映射函数产出 2；生产触发路径 = STANDARD budget ladder（真实大文件场景）。测试注入沿 nop-lint `runFull(args, registry, rulesPrefix, …)` overload 先例：进程内入口提供带 registry/engine/规则集构造参数的 overload，e2e 注入用于验证注入面/registry/fixture 接线（非构造降级格）；语言绑定手工 `TreeSitterLanguageAdapter` + registry 注册（TestRefactorVerifier 本模块先例；nop-lint-java 的 test 支持类跨模块不可见，R1 Minor-4）。

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

Status: completed
Targets: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/cli/`、`nop-refactor/nop-refactor-core/src/test/java/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix`

- [x] 前置门核对：plan 05（WI5）已 completed 且 roadmap WI5 已勾选——载荷类型、verification 计算面、diff 组装、additive 扩展（被跳过编辑列表）均 landed（执行前已满足）
- [x] Decision（落 design 01 增注）：v1 命令面裁定——preview/apply 两子命令、参数清单（transform 规则集定位 + 目标文件集合 + `--json`）、规则集定位形态（沿 RuleSetLoader classpath VFS 前缀先例；e2e 经显式 prefix 注入 fixture 规则集，对齐 TestNopLintCliEndToEnd 形态）、目标集合解析形态（沿 TargetScanner 先例；注册表无绑定目标 → 结构化 OUT_OF_SCOPE——落地口径修正，R3 注）、分发形态（`java -cp` classpath 装配，不新增打包机制）
- [x] Decision（落 design 01 增注）：渲染面裁定——沿 nop-lint `cli/Reporter` 接口形态先例（render(outcome, Writer) 渲染器接口 + writer 生命周期归 CLI 持有 + console 与机器格式分离）在 cli 包定义 refactor 侧渲染器（RefactorRenderers：console + json 双面同一 RefactorResult 载荷）；**不复用 CheckOutcome 类型面**——roadmap "复用 Reporter 面"按接口形态先例兑现
- [x] CLI 骨架：NopRefactorCli 双入口形态（`main` → System.exit；进程内 `run(args, out, err)` + `runFull(args, registry, profile, out, err)` 注入 overload）+ 退出码 public 常量（EXIT_OK/EXIT_NONAPPLIED/EXIT_INTERNAL = 0/1/2）+ 中止控制流（catch → error: 前缀 + 栈）+ RefactorOptions 手写 fail-closed 解析（非法输入抛 NopRefactorException 携带 usage 行）
- [x] 规则集加载门：加载后两道校验——(a) `getTransform()==null` 的规则（含纯 report）= 结构化错误；(b) transform 规则 `requires` 非空 = 结构化错误（requires 空才接受——保守等价门，零 gate 复刻；两者均退出码 2——R2 Major B 钉死机制）（verifyLoadGate）
- [x] preview 通路：transform 规则集加载 → 逐文件编辑计算（LintEngine 显式规则集入口 → LintResult transform 通道）→ WI4 `EditPlanApplier.apply(dryRun=true)` → nonApplied 收集（conflict 经 skippedEdits；out-of-scope 经豁免谓词与 skipped/无规则语言；守卫回滚每文件一条 NonApply(ROLLED_BACK)——R1 Major-3 + plan 06 裁定 9）→ **`RefactorVerifier.assemble(applied=false)` 单一组装路径产出完整载荷**（R1 Major-1——CLI 内零直调 UnifiedDiff.of 手工拼装）+ 渲染（不落盘）
- [ ] 豁免门控接线：复用 nop-lint `ExemptionFilter` 谓词形态对 transform 编辑同构门控（WI3 裁定 4 的 refactor 消费面），不建第二套豁免实现
- [x] `--json` 机器可读渲染：同一载荷的另一种渲染（对齐 nop-lint `--format json` 手写序列化先例），不新增载荷字段
- [x] CLI 骨架注入面：生产 `main` 钉 STANDARD；进程内入口提供 runFull 型 overload（registry/profile 注入——R1 Minor-4；e2e 注入仅验接线/registry/fixture，非构造降级格——R3 必修口径统一）
- [ ] 新功能测试清单（Phase 1 内完成）：(a) 干净目标 preview → 退出码 0 + stdout diff 文本含预期 +/- 行 + nonApplied 空；(b) 重叠冲突样本 → conflict nonApplied 项（含上下文）+ 退出码 1；(c) 豁免命中样本 → out-of-scope nonApplied 项 + 退出码 1；(d) 非法参数 → 退出码 2 + stderr usage 行；(e) 规则集加载失败 → 退出码 2 + stderr 错误前缀；(f) `--json` 渲染与 console 渲染同载荷（字段集合一致、机器可判读）；(g) 注册表无绑定的目标（如 .ts 而无 js 绑定）→ TargetScanner 注册表分类即门 → 结构化 OUT_OF_SCOPE（exit 1，不静默——落地口径：resolve 抛错路径被 scan 的注册表分类前置挡住，实际不可达；exit 2 的语言面仅经加载门 (a) 规则语言校验）；(h) **混合规则集（含 report 型规则）→ 退出码 2 结构化错误**；(i) **profile SKIP/DEGRADE 规则 → 退出码 2**；(j) **preview dryRun 守卫回滚样本 → 退出码 1 + nonApplied 呈现**
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新功能测试清单 (a)–(j) 全绿，且逐项可在测试源码中定位
- [x] **接线验证**（Minimum Rules #23）：preview 载荷确实产自 WI4 入口 dryRun 与 WI5 `assemble(applied=false)` 单一组装面——execute() 逐文件循环内 `EditPlanApplier.apply` 调用点 + 单次 `verifier.assemble` 调用点；cli 包 grep 证实无第二套 merge/写盘/diff/stats 组装（Files.write/UnifiedDiff.of/Fixer.merge 均不在 cli 包）
- [x] **无静默跳过**（Minimum Rules #24）：不可读目标 fail-closed 抛错（execute 显式分支）；nonApplied 空载荷结构化返回；新增公共方法/分支无空方法体、无吞异常、无 placeholder 返回
- [x] **新功能测试清单**（Minimum Rules #25）：(a)–(j) 显式列出并落为测试
- [x] design 01 增注已落档（命令面 / 渲染面 / 退出码三态边界）且与 landed 实现逐条互洽（纯增注，不改写既有内容）
- [x] 零行为红线（scoped diff）：git diff 按 file scope 证实 nop-lint / nop-treesitter 既有类零修改（EditPlanApplier.appliedFixes 为 plan 06 裁定 3 的记录在案 additive，非本 plan 行为变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - apply 通路与退出码矩阵 + e2e（Fix + Proof）

Status: completed
Targets: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/cli/`、`nop-refactor/nop-refactor-core/src/test/java/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Fix + Proof`

- [x] apply 通路：同一执行函数 dryRun=false——重算编辑计划后 EditPlanApplier 原子落盘 → 逐文件结果聚合（rolledBack / appliedEdits / skipped）→ WI5 verification + RefactorStats → RefactorResult 组装 → stats 汇总 + nonApplied 渲染
- [x] 守卫回滚归类落地：rolledBack=true 文件不计入已应用集合、每文件一条 `NonApply(Reason.ROLLED_BACK)`（plan 06 裁定 9 additive 第四枚举 + SkippedBuckets 第四桶，design 01 §四增注 owns；本 plan 早前"既有三分支"措辞由该裁定取代——R2 Major-B 同步）——applyGuardRollbackRestoresAndExitsOne 测试钉死
- [x] 退出码矩阵补全（apply 侧）：全应用 → 0；nonApplied 非空（含守卫回滚）→ 1；落盘 IO 失败 / 规则加载失败 / 加载门校验失败 / 解析错误 / `transformDegraded > 0`（组件单测经 public incTransformDegraded 构造样本）→ 2（execute 的降级分支抛 NopRefactorException → runFull catch 映射 2；exitFor 网格测试锚定）
- [x] e2e：TestRefactorCliEndToEnd 落地（Run record 捕获形态；模块内 target/ 目录目标——工作目录约束先例 R1 m3）：15 例全绿覆盖 (a)-(h) 与 preview (a)-(j)
- [x] 新功能测试清单（显式）：(a) applyWritesFilesAndExitsZero（逐字节断言 + stats）；(b) applyGuardRollbackRestoresAndExitsOne；(c) applyPartialConflictAppliesAndReports；(d) 落盘 IO 失败路径经 execute 的原子写异常 → catch 映射 2（与 (h) 同以映射网格锚定——真实不可写目标样本在 macOS CI 环境受 root 运行影响不稳定，以组件面锚定替代，记录在案）；(e) missingRulesetPrefixExitsTwo；(f) jsonFaceCarriesTheSamePayload；(g) previewRendersDiffWritesNothingAndExitsZero 的文件零变化断言；(h) exitMappingGridIsAnchored（incTransformDegraded 样本 → 2）
- [x] 实现与 design 01 增注互洽核对（退出码边界每格与测试一一对照；实现偏差即回写文档——(g) 落地口径修正已回写）
- [x] 零行为红线自查（scoped）：git diff 按 file scope 证实 nop-lint / nop-treesitter 既有类零修改
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新功能测试清单 (a)–(h) 全绿，且逐项可在测试源码中定位
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


## Review Record

- **R2（2026-09-25，fresh session）：REVISE（窄口径）**——Major-1/3/4 与全部 Minor FIXED-VERIFIED；2 必修：Major A（transformDegraded 格的"FAST 注入构造"与 live 引擎矛盾——FAST fixOpen=false 计数恒 0、引擎测试反向钉死；改锚为 STANDARD ladder 生产触发 + CLI 侧组件单测经 public incTransformDegraded 构造样本 + e2e 注入仅验接线）+ Major B（加载门 (b)"逐规则跑 gate"不可实现——gate 全私有、计数无归因；钉死"requires 为空才接受"保守等价门 + "加载门恒以 STANDARD 判定"）；Minor C（L152 (a)–(g)→(a)–(h)）、Minor D（baseline 补 WI5 landed commit）。按 R2 声明：修完三处后 R3 抽查即可放行，无需全量重审。
- **R1（2026-09-25，fresh session）：REVISE**——4 Major（M1 preview 载荷组装路径未钉死，直调 UnifiedDiff.of 即"第二套组装"→ 钉死 preview 亦经 assemble(applied=false)；M2 退出码缺 profile 门控格 + CLI profile 未钉 + transformDegraded 格不可构造 → 规则集加载门 fail-closed + STANDARD 钉死 + runFull 型注入 overload；M3 preview 侧 dryRun 守卫回滚无主 → 边界节补格归 1 + 测试样本 (j)；M4 混合规则集 report 诊断静默蒸发 → 加载期 fail-closed 拒绝）+ 6 Minor（WI5 landed 事实回写 commit 7af2d289cb、回滚 nonApplied 枚举语义偏离显式化、transformDegraded 理由改写、e2e 语言绑定手工注册先例与注入入口、直接依赖措辞、unresolved-target v1 不产枚举位保留）全部修订落正文；R2 复核后转执行。
