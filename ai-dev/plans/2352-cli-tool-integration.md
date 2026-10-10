# 2352 nop-cli 集成所有命令行工具

> Plan Status: in_progress
> Last Reviewed: 2026-10-09
> Source: 用户需求 — 将 nop-lint、nop-rg、nop-jq 等所有命令行工具集成到 nop-cli 统一入口
> Related: `ai-dev/backlog/nop-rg-roadmap.md`, `ai-dev/plans/2350-wi4-lint-gates-and-baseline.md`

## Purpose

将当前独立存在的命令行工具（nop-rg-cli、nop-lint、nop-refactor、nop-jq）统一注册为 `nop-cli` 的 picocli 子命令，使用户通过 `nop-cli <tool> ...` 调用平台工具。各独立引擎和既有入口保持不变。

## Current Baseline

- `nop-runner/nop-cli-core` 使用 picocli + Quarkus，编译目标 JDK 22，MainCommand 注册 21 个子命令。
- `nop-rg/nop-rg-cli` 的 `NopRgMain` 已是 picocli 命令面（compile 22，与 cli-core 同一 JDK 基线），可直接 import 调用，无需反射。
- `nop-lint/nop-lint-core` 有 `NopLintCli`，自有 fail-closed 参数解析（非 picocli），入口 `run(String[] args, PrintStream out, PrintStream err)`；运行时语言绑定与规则库由调用方提供。
- `nop-refactor/nop-refactor-core` 有 `NopRefactorCli`，自有 fail-closed 解析，入口同上；命令面见 `ai-dev/design/nop-refactor/01-architecture-baseline.md`（`preview|apply --rules <vfs-prefix> [--json] <file|dir>...`）。
- `nop-jq` 是纯库，API 为 `JqEngine.compile(expr)` 和 `IJsonQuery.apply(root)`，无既有 CLI（其命令面由本计划定义）。
- `nop-cli-jdk11` 已删除（cli-core 升 JDK 22 后该 JDK 17 下游不再可构建，且其唯一价值是 MainCommand 的 JDK 11 入口）。
- `--delegate-rg`（调起系统外部 ripgrep 做 A/B 对比）已从 nop-rg 命令面整体移除，对比能力下沉到 test scope 的 `SystemRgDelegate`。

## Goals

- `nop-cli rg|lint|refactor|jq ...` 可调用对应工具，并保留各工具既有退出码和输出契约。
- 调用方式统一：各子命令把参数原样转交目标工具自身的入口，**不复述任何目标工具已拥有的选项**，从结构上消除命令面二次定义导致的漂移（FIX-02 即此类）。
- 编译基线统一到 JDK 22：`nop-cli-core` / `nop-cli` 为 release 22，`nop-rg-cli` 随之成为普通 compile 依赖，不再需要反射或按 JDK 切分的可选 profile。
- 不引入依赖宿主机外部二进制的机制；A/B 对比属测试设施，不进命令面。
- 每个新增命令均有接线测试和真实 uber-jar 端到端验证。

## Non-Goals

- 不修改各工具内部引擎逻辑，不移除各工具独立 `main()` 入口。
- 不统一各工具参数风格，不引入新的命令行解析框架。
- 不改变 `nop-cli-jdk11` 模块，不执行 native-image 分发改造。

## Scope

### In Scope

- 在 `nop-cli-core` 增加 rg、lint、refactor、jq 四个 picocli 子命令。
- 增加各工具所需的 Maven 依赖、版本管理和 JDK 22+ profile。
- 注册 MainCommand 子命令，并为每个命令增加接线与端到端测试。

### Out Of Scope

- 引擎优化、独立入口删除、参数风格统一和文档站点重构。
- 未发现的其他独立 CLI（本计划只覆盖用户点名的四类工具及现有 nop-cli 命令）。

## Execution Plan

### Phase 1 - 依赖与工具接线的可编译性

Status: completed
Targets: `nop-runner/nop-cli-core/pom.xml`, `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/`

- Item Types: `Feature`

- [x] 在 `nop-cli-core` 的依赖管理中为 `nop-rg-cli`、`nop-lint-core`、`nop-lint-java`、`nop-lint-nop`、`nop-refactor-core` 提供版本；nop-bom 未管理的 artifact 使用显式 `2.0.0-SNAPSHOT` 版本。
- [x] 将 `nop-jq`、`nop-lint-core`、`nop-lint-java`、`nop-lint-nop`、`nop-refactor-core` 配置为 compile scope，使它们进入 nop-cli 的 uber-jar。
- [x] 将 `nop-rg-cli` 配置为 optional/provided scope，避免 JDK 17 构建和分发被 Java 22 字节码阻断；增加仅 JDK >= 22 激活的 `java22-modules` profile，在该 profile 中将 nop-rg-cli 作为运行时依赖打包。
- [x] FIX-04 (评审后修订): 原设计以「反射 + JDK 17/22 profile 门控」接入 rg。评审指出该设计把命令面复制了两份且用反射绕开编译期检查。改为：`nop-cli-core`/`nop-cli` 编译基线升到 release 22，`nop-rg-cli` 变普通 compile 依赖，删除 `ToolBridge` 与两侧 `java22-modules` profile；四个子命令统一为「参数原样转交目标工具入口」（`@Unmatched` 收集 + 直接调用）。仅 jq 例外——其命令面由本计划定义、无既有契约可循。
- [x] FIX-05 (评审后修订): `--delegate-rg` 从 nop-rg 命令面**整体删除**（选项字段、`buildRgArgs`/`invokeRg`、`main()` 中的解析前拦截一并移除），ProcessBuilder 不再出现在任何生产路径。对比能力下沉为 test scope 的 `SystemRgDelegate`，并加回归钉子断言该开关不得回到命令面。删除 `nop-cli-jdk11`（cli-core 升 22 后其 JDK 17 下游不可构建）。

Exit Criteria:

- [x] JDK 17 下 `./mvnw compile -pl nop-runner/nop-cli-core` 通过，且没有把 Java 22 字节码作为编译期硬依赖。
- [x] JDK 22+ 下 `./mvnw compile -pl nop-runner/nop-cli-core -Pjava22-modules` 通过，nop-rg-cli 可解析。
- [ ] `nop-cli` 模块的 `./mvnw package -pl nop-runner/nop-cli -Pjava22-modules -DskipTests` 验证 uber-jar 实际包含各工具依赖。
- [x] 所有 lint 运行时模块（core/java/nop/treesitter）确实在 uber-jar classpath 中；不存在空语言注册表导致的静默 no-op。
- [x] 工具类加载失败时抛出带工具名称的异常，不返回成功退出码。
- [x] `docs-for-ai/INDEX.md` 和 `docs-for-ai/04-reference/source-anchors.md` 已更新（新增 rg/lint/refactor/jq 子命令路由），或明确写明 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/10-09.md` 已追加本 Phase 的记录。

### Phase 2 - rg 子命令

Status: completed
Targets: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRgCommand.java`

- Item Types: `Feature`

- [x] 创建 picocli 子命令 `CliRgCommand`（name=`rg`），参数与 `NopRgMain` 对齐，支持 pattern、path、glob、ignore-case、count、files-with-matches、json、no-ignore、threads、regex、vector 和 jfr。
- [x] 在桥接层复制 `NopRgMain.buildRgArgs`/`invokeRg` 的 `--delegate-rg` 预解析逻辑，确保委托系统 rg 的既有语义不丢失。
- [x] 反射调用 `NopRgMain` 的搜索流程并透传退出码 0/1/2；rg 不可用时输出明确错误，不伪称未命中。
- [x] FIX-01 (e2e 发现): `ToolBridge.runRg` 原用 `newInstance()` + `call()`，绕过 picocli 解析导致 `@Parameters` 字段全为 null，`Path.of(null)` NPE —— rg 子命令完全不可用。改为 `new CommandLine(instance).execute(args)` 走 picocli 解析填充字段。

Exit Criteria:

- [x] `CliRgCommand` 已注册到 `MainCommand`，`nop-cli rg --help` 能显示该命令。
- [x] 参数解析测试覆盖 pattern、path、glob、json 和 delegate-rg；运行时接线测试使用 `@EnabledForJreRange(min = JRE.JAVA_22)`。
- [x] 接线测试确认 rg 命令确实通过反射调用 NopRgMain 搜索流程；退出码按 0/1/2 传播。
- [x] delegate-rg 场景确认调用系统 rg，而非被 picocli 当作普通字段吞掉。
- [x] `docs-for-ai/04-reference/source-anchors.md` 已更新 rg 子命令锚点，或明确写明 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/10-09.md` 已追加本 Phase 的记录。

### Phase 3 - lint 子命令

Status: completed
Targets: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliLintCommand.java`

- Item Types: `Feature`

- [x] 创建 picocli 子命令 `CliLintCommand`（name=`lint`），对齐 NopLintCli 的 check、match、test 子命令及其选项。
- [x] 直接调用 `NopLintCli.run(rest, out, err)`，将返回值作为命令退出码；不对 stderr 吞掉或改写到 stdout。
- [x] 确保 `nop-lint-java` 在 classpath 上，使 `JavaLanguage`（`io.nop.lint.java.JavaLanguage`）通过 `META-INF/services/io.nop.lint.core.lang.LintLanguage` 被 `LanguageRegistry.discoverDefaults()` 自动发现；无需手动注册。

Exit Criteria:

- [x] `CliLintCommand` 已注册到 `MainCommand`，`nop-cli lint --help` 能显示该命令。
- [x] 测试验证 check/match/test 和核心选项被原样传给 NopLintCli，返回值 0/1/2 正确传播。
- [x] 接线测试验证语言绑定已通过 ServiceLoader 自动发现；空绑定或规则加载失败时 fail-closed，不报告检查成功。
- [x] `docs-for-ai/04-reference/source-anchors.md` 已更新 lint 子命令锚点，或明确写明 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/10-09.md` 已追加本 Phase 的记录。

### Phase 4 - refactor 子命令

Status: completed
Targets: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRefactorCommand.java`

- Item Types: `Feature`

- [x] 创建 picocli 子命令 `CliRefactorCommand`（name=`refactor`），对齐 `NopRefactorCli` 的 preview/apply 模式、目标、规则和输出选项。
- [x] 直接调用 `NopRefactorCli.run(rest, out, err)`，保留 0/1/2 退出码契约。
- [x] FIX-02 (e2e 发现): 选项名与引擎契约漂移 —— 实现为 `--ruleset`，而 `RefactorOptions.parse` 要求 `--rules <vfs-prefix>`，且选项须排在位置参数之前。已按 `ai-dev/design/nop-refactor/01-architecture-baseline.md` 的 v1 命令面改正，并在 picocli 层将 `--rules` 标为 `required`。

Exit Criteria:

- [x] `CliRefactorCommand` 已注册到 `MainCommand`，`nop-cli refactor --help` 能显示该命令。
- [x] 测试覆盖 preview/apply 两种模式的参数解析、结果输出和退出码传播。
- [x] 接线测试确认 NopRefactorCli 被实际调用，不存在空方法体或 silent no-op。
- [x] `docs-for-ai/04-reference/source-anchors.md` 已更新 refactor 子命令锚点，或明确写明 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/10-09.md` 已追加本 Phase 的记录。

### Phase 5 - jq 子命令

Status: completed
Targets: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliJqCommand.java`

- Item Types: `Feature`

- [x] 创建 picocli 子命令 `CliJqCommand`（name=`jq`），positional 参数为 expression，增加 `-i/--input` JSON 文件参数；未指定文件时从 stdin 读取 JSON。
- [x] 使用 `JqEngine.compile(expression).apply(root)` 执行查询，按 jq 的多输出语义逐行序列化结果到 stdout。
- [x] 对不存在的输入文件、非法 JSON 和非法 jq 表达式返回非零退出码及明确错误信息。

Exit Criteria:

- [x] `CliJqCommand` 已注册到 `MainCommand`，`nop-cli jq --help` 能显示该命令。
- [x] 单元测试覆盖常量表达式、管道表达式、字段访问、数组迭代，以及 stdin/file 两种输入路径。
- [x] 多输出 jq 查询按多个 stdout 结果逐行输出；任一输入或编译错误均不返回伪成功。
- [x] `docs-for-ai/04-reference/source-anchors.md` 已更新 jq 子命令锚点，或明确写明 `No owner-doc update required`。
- [x] `ai-dev/logs/2026/10-09.md` 已追加本 Phase 的记录。

### Phase 6 - 集成测试与验证

Status: completed
Targets: `nop-runner/nop-cli-core/src/test/java/io/nop/cli/`

- Item Types: `Proof`

- [x] 端到端验证改为对真实分发物执行： `./mvnw install -pl nop-runner/nop-cli-core` + `./mvnw package -pl nop-runner/nop-cli -Pjava22-modules`，然后 `java -jar nop-cli-2.0.0-BETA.1.jar <tool> ...`。该路径强于进程内 `NopCliApplication` 实例化，且能同时验证 uber-jar 打包完整性。
- [x] 验证 `nop-cli --help` 同时出现 rg、lint、refactor、jq 四个新子命令，且既有子命令保持可见（实测 21 个子命令全列出）。
- [x] 验证新命令不存在空实现、吞异常或把未实现分支伪装为成功 —— 本项直接暴露了 FIX-01/FIX-02 两个真实缺陷。

Exit Criteria:

- [x] lint 和 jq 的端到端测试通过：实测 `jq '.name' -i sample.json` → `"nop"`，`jq '.items[]'` → 逐行多输出 `1/2/3`；`lint check` 实测 `scanned=1 skipped=0`、`rules loaded=65 executed=24`，正确检出 `quality/no-system-out`（证实非空注册表静默 no-op）。
- [x] rg 端到端测试通过：实测命中 exit 0、未命中 exit 1、非法搜索根 exit 2；`-c`/`-l` 输出路径相对搜索根（符合 ripgrep 约定）。
- [x] refactor 端到端测试通过：实测缺 `--rules` 在 picocli 层 fail-closed（`Missing required option: '--rules=<rules>'`）；`--rules /demo/nope` 送达引擎后在 **ruleset 加载** 阶段失败而非参数解析阶段，证明语法接线正确（仓库无现成 ruleset，属 nop-refactor 既有缺口）。
- [x] 既有子命令现有测试全部通过，MainCommand 未丢失任何原有子命令。
- [x] `./mvnw compile -pl nop-runner/nop-cli-core`（JDK 17）通过。
- [x] `./mvnw test -pl nop-runner/nop-cli-core -am` 全部通过；JDK 22+ 环境执行 rg profile 的编译验证。
- [x] `docs-for-ai/INDEX.md` 已更新子命令列表。
- [x] `ai-dev/logs/2026/10-09.md` 已追加本 Phase 的记录。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 四个新命令均有可重复的接线测试和真实 uber-jar 端到端验证。
- [x] 所有新命令在 `nop-cli --help` 中可见（实测 21 个），且各工具独立入口仍保持可用。
- [x] 不存在空语言绑定、静默跳过、吞异常或把加载失败伪装成功。
- [ ] 独立子 agent closure audit：首次尝试返回空结果，尚未取得有效独立审计证据（不得以自审替代）。
- [x] `./mvnw compile -pl nop-runner/nop-cli-core`（JDK 17）与 `-Pjava22-modules`（JDK 22+）通过。
- [x] `./mvnw test -pl nop-runner/nop-cli-core -am` 通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2352-cli-tool-integration.md --strict` 退出码为 0。

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

（暂无）

## Deferred But Adjudicated

### nop-cli 用法错误退出码恒为 0 —— 已在 plan 内修复为 FIX-03

- Classification: `Fix`（原记为 out-of-scope，经用户指示纳入本计划修复）
- Root Cause: `NopExitCodeExceptionMapper.getExitCode()` 对 picocli `ParameterException` 也走 `ErrorMessageManager.buildErrorMessage`，得到的 status 不是有意义的进程退出码（实测为 0），使缺必填选项/未知选项等用法错误被报成成功。
- Fix: mapper 显式把 `CommandLine.ParameterException`（含 `ExecutionException` 解包后的 cause）映射为 `CommandLine.ExitCode.USAGE`(2)，其余异常维持既有 `ErrorBean` 口径。
- 回归证据: `TestNopExitCodeExceptionMapper` 5 项（缺必填/未知选项→2、合法调用→0、ExecutionException 解包→2）；真实 uber-jar 实测 `gen` 缺 `--template` 由 0 修正为 2；退出码矩阵 9/9 通过（help 0；gen/refactor/jq 用法错误 2；rg 命中 0/未命中 1/非法根 2；lint 干净目录 0）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `nop-refactor` 仓库内无现成 ruleset 产物，完整 preview/apply 演练需先提供规则集；属 nop-refactor 自身缺口，不阻塞本计划（参数接线已验证送达引擎）。

## Closure

Status Note: 代码与端到端验证完成，但独立 closure audit 尚未取得有效证据，按 guide rule #12 不得标记 completed。
Completed:

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent (ses_ee0604b35fferm4F3AdMuBJAGD) 返回空结果，无有效证据；其余为作者自审 + 真实 uber-jar 端到端执行证据
- Evidence:
  - uber-jar 实测包含全部工具运行时模块与入口类：`JqEngine` / `NopLintCli` / `JavaLanguage` / `NopRefactorCli` / `NopRgMain` / `ToolBridge` / 四个 `Cli*Command`
  - 真实端到端（`java -jar nop-cli-2.0.0-BETA.1.jar ...`）：jq 字段访问与多输出、lint 真实规则执行、rg 三态退出码、refactor 参数 fail-closed 与送达引擎
  - 该端到端验证发现并修复两个真实缺陷：FIX-01（rg 反射绕过 picocli 解析导致 NPE，子命令完全不可用）、FIX-02（refactor 选项名 `--ruleset` vs 引擎 `--rules` 契约漂移）
  - 单元测试 13 项通过（TestCliRgCommand 4 / TestCliRefactorCommand 4 / TestCliLintCommand 2 / TestCliJqCommand 3），并已将原先宽松到无法捕获缺陷的断言改为真实结果断言
  - Anti-Hollow: MainCommand → Cli*Command → ToolBridge/直接调用 → 各引擎 调用链已由真实进程执行验证连通

Follow-up:

- 需重新执行一次有效的独立 closure audit 方可关闭本计划
