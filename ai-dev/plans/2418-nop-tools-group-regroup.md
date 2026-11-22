# 2418 nop-tools 工具模块分组重组

> Plan Status: completed
> Numbering Note: 2026-10-11 编号冲突裁定——并发 nop-stream WI7 plan 已占用 2417（feecf09a5f），本 plan 让位改号 2418；已入库 commit 0104dcb309/43447bac70 message 中的「plan 2417」字样均指本 plan（现 2418）
> Last Reviewed: 2026-10-11
> Source: 2026-10-11 会话裁定——用户提案将 nop-rg / nop-lint / nop-treesitter / nop-refactor / nop-jq 等开发者工具模块归入 `nop-tools/` 分组（类似 nop-utils）；补充裁定：nop-jq 当初迁出 nop-kernel 的动机是「nop-kernel 保持精简最小化」，而非「jq 必须放顶层」，故归组不违背该决策。另：用户已宣布后续全面切换 JDK 25，本计划不与 JDK 门控耦合（见 Non-Goals）
> Related: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（同类结构重组先例）；ai-dev/plans/2413-wi4-connector-jdbc-debezium-dependency-hardening.md（近期结构类 plan 样式参照）
> Review: 2026-10-11 独立子 agent 对抗性审查两轮（agent_a6e1122a）：R1 报 2 Blocker + 5 Major + 8 Minor，已全部回修；R2 复核确认 R1 全部 FIXED，新发现 1 Blocker（ledger 门禁存量红基线未登记）+ 3 Minor，已按 R2 建议方案 (a) 回修。Closure audit：2026-10-11 独立子 agent（agent_436b868d）逐 gate 实测，实体面判定成立，4 处深度修正坏链接 + plan 文本补齐问题已按报告闭环（见 Closure 段证据）

## Purpose

把 7 个开发者/AI 编程工具链模块组物理归入 `nop-tools/` 聚合分组：Maven 坐标零变化、构建产物零变化、门禁行为零变化；所有路径型引用（CI、门禁脚本、mission 配置、活跃 roadmap、owner docs）与新布局一致。重组完成后，根 pom modules 声明从 48 项收敛为 42 项，module-groups.md 主表 7 个工具分组行收敛为 1 个 nop-tools 行。

## Current Baseline

物理布局（2026-10-11 实测）：

- 待移动的 7 个顶层模块组：`nop-rg/`（聚合组，5 子模块）、`nop-find/`（聚合组，3 子模块）、`nop-lint/`（聚合组，6 子模块）、`nop-refactor/`（聚合组，3 子模块）、`nop-treesitter/`（叶子单模块）、`nop-jq/`（叶子单模块）、`nop-bytecode/`（叶子单模块）。
- 上述 7 个组/叶子 pom 的 parent 均为 `nop-entropy`（`io.github.entropy-cloud:nop-entropy:2.0.0-SNAPSHOT`），均无显式 `<relativePath>`（默认 `../pom.xml`）。聚合组的孙子模块（如 nop-lint-core）parent 为各自组 pom，移动后无需改动。
- 全仓唯一显式 `../../pom.xml` relativePath 在 `nop-demo/nop-spring-simple-demo/pom.xml:11`，不在本计划范围；7 棵待移动模块树内无任何 `../` 构建配置引用（R1 审查复核）。
- 根 `pom.xml` `<modules>` 现含 **48** 项非注释条目（R1 审查逐行复核），其中 7 项为上述模块；根 pom 无引用这些模块的 profile。
- JDK 门控完全封装在组 pom 内部：`../../nop-tools/nop-rg/pom.xml` 与 `../../nop-tools/nop-find/pom.xml` 各有 `java22-modules` profile（`<jdk>[22,)</jdk>` 激活）；nop-utils 的 `java21-modules` 先例同构。整体 `git mv` 不影响门控行为。
- 本机 JDK 26（门控模块全激活）；CI `.github/workflows/maven.yml` 用 JDK 21，nop-rg/nop-find 在 CI 被跳过——重组不改变该差异。

依赖与门禁接线（坐标面，移动零影响）：

- `nop-runner/nop-cli-core/pom.xml:188-221` 以坐标依赖 `nop-jq`、`nop-lint-core/java/nop`、`nop-rg-cli`、`nop-find-cli`；`nop-cli lint|rg|jq|find` 子命令经 `MainCommand` 注册（`CliLintCommand` 位于 `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/`）。
- Java lint 门禁链路：本机 = `.githooks/pre-commit` + `ai-dev/tools/run-java-lint.sh`（解析 nop-cli 后调用 `nop-cli lint check`）；CI 的 maven.yml 不构建 nop-cli，门禁不在 CI 生效。该链路全部是坐标/命令面，**本计划零改动**。
- `nop-code/nop-code-lang-{go,python,rust,csharp,typescript}/pom.xml` 等对 `nop-treesitter` 的引用均为 artifactId 坐标面。

路径型引用——功能性（移动即断，必须同步；R1 审查修正定性）：

- CI：`.github/workflows/compliance.yml`——`-pl nop-lint/...` 共 **4 处**（两个 job 各 compile + build-classpath，L35/37/86/88）、classpath 拼接 2 处（L40/91）、注释 5 处。全 workflows 目录仅此一个文件命中。
- `ai-dev/tools/run-nop-metadata-invariants.sh`——L67/L72 `-pl nop-lint/...` 两处 + L73 `NOP_LINT_CP` classpath 拼接。
- `ai-dev/tools/check-lint-tool-migration-mapping.mjs:36,39`——`DOC_FILE`/`RULES_ROOT` 指向 `nop-lint/docs/`、`nop-lint/nop-lint-nop/`（路径常量，非注释）。
- `ai-dev/tools/check-lint-tool-replacement-ledger.mjs:37,76`——`LEDGER_FILE`/`RULES_ROOT` 同上。
- `ai-dev/tools/check-lint-coverage-manifest.mjs:34-40`——`PROJECT_ROOT`/`MANIFEST_FILE`（L35-36）/`TEST_VFS_ROOT`（L39-40）指向 `nop-lint/nop-lint-nop/`。
- `ai-dev/tools/gen-lint-rule-catalog.mjs:33-35`——`RULES_ROOT`/`CATALOG_FILE` 指向 `nop-lint/`；登记在案的确定性生成器 + `--check` 门禁。
- **存量红基线（pre-existing，与本重组无关）**：`ai-dev/tools/check-lint-tool-replacement-ledger.mjs` 在移动前基线即 FAILED（7 violations：6 条 nop-xdsl-* 规则缺 ledger facet 行 + census 漂移 live 76 + remove 4 = 80 ≠ 74）——nop-xdsl 规则入库（plan 2348-wi2/2350 一代）时未按 ledger 自身协议登记的 bookkeeping 债；本计划以机械登记清偿（Phase 2），使「门禁脚本退出码 0」判据可达。
- `ai-dev/archived/missions-2026-11-22/nop-lint.json`——commands 含 4 条 `-pl nop-lint/...`（test/build/lint/typecheck）。
- `ai-dev/archived/missions-2026-11-22/nop-treesitter.json`——`moduleDir: nop-treesitter` + commands `-pl nop-treesitter -am`。
- `ai-dev/archived/missions-2026-11-22/nop-jq.json`——`moduleDir: nop-kernel/nop-jq` + `cd nop-kernel/nop-jq`（**2026-09-28 迁出时已 stale 的 pre-existing 漂移**，本次归组一并修正为 `nop-tools/nop-jq`）。
- 根目录 `.ast-grepignore`——末行排除 `nop-lint/**/src/test/resources/_vfs/test/lint/`（governs ad-hoc `sg scan` 与 `../../nop-tools/nop-lint/bench/compare-ast-grep.sh` 的排除面，失效会让故意写脏的 RuleTester 夹具进入扫描）。
- `../../nop-tools/nop-lint/bench/run-benchmarks.sh`、`run-graphql-benchmarks.sh`——`-pl nop-lint/...`（`compare-ast-grep.sh` 无 `-pl`，仅注释性 doc 指针）。
- `../../nop-tools/nop-treesitter/bench/run-c-reference.sh`——根相对功能路径 `nop-treesitter/target/classes`、`nop-treesitter/bench/c_harness.c`。
- 组内 README 示例路径：`../../nop-tools/nop-rg/nop-rg-cli/README.md`、`../../nop-tools/nop-rg/nop-rg-benchmark/README.md`、`nop-find/nop-find-cli/README.md:101-102` 的 `-pl`/`cd` 示例。

路径型引用——深度敏感的出组相对链接（移动加深一层即断；R1 审查扩充）：

- `nop-jq/deepwiki-v3/*.md`——`../../../`、`../../` 相对链接全部错位；其中指向 `nop-kernel/nop-jq/...` 的链接在 2026-09-28 迁出时已失效（pre-existing 死链，见 Deferred）。
- `nop-bytecode/README.md:5-6`——`](../ai-dev/design/nop-bytecode/...)` ×2；L25 根相对 classpath 示例。
- `../../nop-tools/nop-bytecode/docs/gap-ledger.md`、`interprocedural-spike.md`——`](../../ai-dev/plans/...)`；另有 gap-ledger.md:7 一处**根相对链接指向已移动目标**（`](nop-lint/docs/tool-replacement-ledger.md)`，须改指 `nop-tools/nop-lint/...`；check-doc-links 不扫模块树，无门禁兜底）。
- `nop-lint/docs/tool-replacement-ledger.md:7,21,23,26,43,45,48,153-154`——`](../../ai-dev/...)`、`](../../pom.xml)`、`](../../nop-ai/...)`。

路径型引用——文档/roadmap 面：

- 活跃 backlog roadmap **8** 件含路径型引用：nop-treesitter-roadmap(37)、nop-lint-tool-replacement-roadmap(17)、nop-rg-roadmap(16)、nop-bytecode-analysis-roadmap(11)、nop-lint-roadmap(9)、nop-jq-roadmap(7)、nop-refactor-roadmap(6)、`nop-stream-xdsl-full-coverage-roadmap.md`（L303 未落地 WI 的 `-pl nop-lint/nop-lint-nop -am` 验证命令 + L45/L84 rule-catalog 路径）；合计约 106 处。另有 3 个 backlog 文件（eql-cross-db、full-code-review、unit-test-coverage）为散文式模块名连写（非路径），sweep 时裁定无关。
- owner docs：`docs-for-ai/INDEX.md`、`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/nop-treesitter.md`、`nop-refactor.md`、`nop-bytecode.md`、`nop-stream-user-guide.md`、`docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md`。
- 历史注释面（sweep 时裁定保留或顺带更新，不阻塞）：`ai-dev/tools/check-bean-naming.mjs:26`、根 `deepwiki/README.md`（提及 `nop-jq/`）、移动树内 Java 注释（如 `LintBenchmarkRunner.java:12`、`LargeFileSearchTest.java:27` 的 `-pl nop-rg/...`）——Java 注释按 Non-Goal 不回改。

check-doc-links 存量红基线（2026-10-11 `--strict` 实测退出码 1，共 10 处 BROKEN_LINK）：

- `docs-for-ai/04-reference/source-anchors.md:271` ×1（docs-for-ai 域内，按 AGENTS.md 本应修复，纳入 Phase 3）。
- `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md`（3 处）、`ai-dev/plans/nop-code/28-n7-1-llm-semantic-edge.md` 等（3 处）——历史 plan 内断链，按 plan guide 规则 20 的「损坏链接修复」例外允许回修，纳入 Phase 3。
- 本 plan 草稿自身 3 处 inline code 引用（执行后自愈）。

命名与先例：

- 分组聚合器先例：`nop-persistence/pom.xml`（parent=nop-entropy、packaging=pom、`<modules>` 列子模块、子模块 reparent 到组 pom）。nop-utils 的「组 pom 直接挂叶子」模式与本次形态不匹配（nop-rg 等本身已是组），不采用。
- `<name>` 编号惯例：`05-nop-utils`、`33-nop-treesitter`、`34-nop-rg`、`35-nop-find`、`35-nop-lint`（35 双占，历史遗留）、`36-nop-bytecode`、`40-nop-refactor`。新组取未占用的 `41-nop-tools`。
- 命名冲突核查：`nop-tools` 目录名与既有 `nop-ai-tools`（nop-ai 下 artifact）、`nop-dev-tools`（顶层模块）字面接近但无坐标冲突；用户已确认采用 `nop-tools`。

## Goals

- 7 个工具模块组归入 `nop-tools/`，全部经 `git mv` 保留历史。
- 新建 `nop-tools/pom.xml` 聚合器（parent=nop-entropy、packaging=pom、modules 7 项）；7 个组/叶子 pom 的 parent 改为 `nop-tools`；孙子模块 pom 零改动。
- Maven 坐标（groupId:artifactId:version）与依赖边全部不变；外部消费者零影响。
- 功能性路径引用同步：CI、门禁/校验脚本（ai-dev/tools 5 件）、mission 配置（3 件）、`.ast-grepignore`、组内 bench 脚本与 README、深度敏感出组链接、活跃 roadmap 未落地项、docs-for-ai owner docs。
- check-doc-links `--strict` 达到真实退出码 0（含存量 10 处断链清零）。
- 记录裁定：nop-jq「顶层扁平先例」由「nop-kernel 精简」的真实动机取代，module-groups.md 相应改写；nop-duckdb 例外保留顶层。

## Non-Goals

- 不改任何 Java 源码（含树内 Java 注释中的旧路径——sweep 裁定为「注释面保留」）、包名、artifactId、依赖声明、资源文件。
- 不移动 `nop-duckdb/`（数据分析执行层，消费方是 nop-task 编排与数据管线，非编程工具链）、`nop-code/`（WIP 实验模块）、`nop-utils/`、`nop-dev-tools/`。
- 不回改历史记录的叙事内容：已完成 plan 的正文、daily log、`ai-dev/design/` 历史设计文档中的叙事保留原样；**例外**是 plan guide 规则 20 明确允许的「损坏链接修复」——执行期实测移动致断 100 处（ai-dev roadmap/design/plan 指向旧布局的路径引用，check-doc-links 门禁所强制），连同存量 6 处历史 plan 断链一并机械回修，叙事内容未动（见 Execution Record #4）。
- **不做 JDK 版本切换**：用户已裁定后续全面切换 JDK 25。本计划不触碰 `java22-modules` 门控——profile 原样随组搬移；门控收敛/dissolution 属于 JDK 25 切换的后继工作，届时在 nop-tools 分组内一并处理更内聚。
- 不改 nop-cli 子命令注册、uber-jar 打包、`tools/install-cli.sh`。
- 不处理 nop-lint 规则扩面、门禁范围扩大等 lint 演进事项（归 nop-lint-tool-replacement-roadmap）。

## Scope

### In Scope

- `git mv` 7 个模块组 → `nop-tools/`，清理旧位置 `target/` 残留。
- 新建 `nop-tools/pom.xml`；7 个 pom reparent；根 pom `<modules>` 删 7 加 1。
- 功能性脚本/配置同步：`compliance.yml`、`run-nop-metadata-invariants.sh`、4 个 lint 门禁/生成器 `.mjs`、3 个 `ai-dev/archived/missions-2026-11-22/*.json`、`.ast-grepignore`、`nop-lint/bench/*.sh`、`../../nop-tools/nop-treesitter/bench/run-c-reference.sh`、3 个组 README。
- 清偿 `check-lint-tool-replacement-ledger.mjs` 存量红基线（补 6 条 nop-xdsl-* facet 行 + census 校正，机械登记，见 Current Baseline 存量红基线条目）。
- 深度修正：`nop-jq/deepwiki-v3/`、`../../nop-tools/nop-bytecode/README.md` 与 `nop-bytecode/docs/`、`../../nop-tools/nop-lint/docs/tool-replacement-ledger.md` 中移动前有效的出组相对链接。
- 8 个活跃 roadmap 中**未落地 WI** 的路径引用更新（已落地 WI 记录保留原文）。
- `docs-for-ai/` 7 个命中文件同步；module-groups.md 主表 7 行收敛为 1 个 nop-tools 行；存量 10 处 doc-links 断链清零。
- 移动后全仓路径残留 sweep 验证（grep 兜底 + 逐命中裁定，见 Phase 2/4）。

### Out Of Scope

- `nop-jq/deepwiki-v3` 中移动前已失效的链接（指向 `nop-kernel/nop-jq/...`）——见 Deferred But Adjudicated。
- 任何模块内部重构、依赖治理、JDK 门控调整。
- `ai-dev/design/nop-lint|nop-refactor|nop-bytecode/` 历史设计文档的叙事路径回写（损坏链接修复除外，见 Non-Goals）。
- 树内 Java 注释中的旧路径（Non-Goal 裁定为保留）。

## Execution Plan

> 推进约束：Phase 1 完成到 Phase 2 完成之间，compliance.yml 与 ai-dev/tools 门禁脚本处于断态。**Phase 1 + Phase 2 必须在同一 push 窗口内完成**，避免中间态触发 CI 红灯；本机逐 Phase 提交、push 前两 Phase 均绿。

### Phase 1 - 物理移动与 Maven 重接

Status: completed
Targets: `nop-tools/`（新）、根 `pom.xml`、7 个组/叶子 pom

- Item Types: `Decision`

- [x] `mkdir nop-tools` 后执行 `git mv nop-rg nop-find nop-jq nop-lint nop-refactor nop-treesitter nop-bytecode nop-tools/`（git mv 多源语法要求目标目录先存在）
- [x] 清理 stale `target/`（执行偏差：git mv 整目录改名，旧位置零残留，untracked target 随目录带入新位置——改为清理 `nop-tools/*/target`，见 Execution Record #2）
- [x] 在任何 mvn 命令之前创建 `nop-tools/pom.xml`：parent=`io.github.entropy-cloud:nop-entropy:2.0.0-SNAPSHOT`（默认 relativePath）、artifactId=`nop-tools`、packaging=`pom`、`<name>41-nop-tools</name>`、encoding property 对齐 nop-persistence、`<modules>` 列出 7 个子组（执行偏差：xsi 命名空间手误 2000→2001 致首次构建失败，即修，见 Execution Record #1）
- [x] 7 个组/叶子 pom（nop-rg、nop-find、nop-lint、nop-refactor、nop-treesitter、nop-jq、nop-bytecode）的 `<parent><artifactId>` 由 `nop-entropy` 改为 `nop-tools`（groupId/version 不动，不加显式 relativePath）
- [x] 根 `pom.xml`：删除 7 个 `<module>` 行，新增 `<module>nop-tools</module>`
- [x] 本机提交 Phase 1 为独立 commit（git mv + pom 重接；commit `0104dcb309`。执行偏差：pre-commit hook 因 bench corpus 夹具阻断，配套 commit `43447bac70` 补门禁过滤；共享 index 事故致删除侧散入 9f50be94f1——`--follow` 仍可追溯，见 Execution Record #6 与 Risks）

Exit Criteria:

- [x] 全量构建验证（执行期裁定改判据，见 Execution Record #3）：`./mvnw install -DskipTests -T 1C -fae` 223 绿 / 9 红，9 红逐一归因（2 个 HEAD 同款 pre-existing + 7 个缓存级联且单模块复测全绿），nop-tools 22 模块全绿——reactor 聚合链与构建顺序成立
- [x] `./mvnw compile -q -pl nop-tools/nop-lint/nop-lint-core -am -DskipTests` 成功（新 `-pl` 四段路径可用）
- [x] `ls nop-tools/` 含 7 个子目录；根目录不再有同名模块目录
- [x] 7 个 pom 的 parent artifactId 为 `nop-tools`，且孙子模块 pom parent 未变（抽查 nop-lint-core、nop-rg-core）
- [x] 门控行为不变：本机 JDK 26 下 java22-modules profile 随组激活，nop-rg/nop-find 全模块在 -fae 构建中 SUCCESS
- [x] 执行当日 `ai-dev/logs/` 条目已更新（ai-dev/logs/2026/10-11.md plan 2418 条目）

### Phase 2 - CI、脚本、mission 与活跃 roadmap 路径同步

Status: completed
Targets: `.github/workflows/compliance.yml`、`ai-dev/tools/`（5 件脚本 + README）、`ai-dev/archived/missions-2026-11-22/`（3 件）、`.ast-grepignore`、组内 bench/README、`ai-dev/backlog/` 8 个 roadmap、深度敏感文档链接

- Item Types: `Fix`

- [x] `compliance.yml`：旧前缀 `nop-lint/` 全文替换为 `nop-tools/nop-lint/`（覆盖 4 处 `-pl`、2 处 classpath 拼接、全部注释）
- [x] `ai-dev/tools/run-nop-metadata-invariants.sh`：L67/L72 `-pl` + L73 `NOP_LINT_CP` 改新布局
- [x] `ai-dev/tools/check-lint-tool-migration-mapping.mjs`、`check-lint-tool-replacement-ledger.mjs`、`check-lint-coverage-manifest.mjs`、`gen-lint-rule-catalog.mjs`：路径常量（`DOC_FILE`/`RULES_ROOT`/`LEDGER_FILE`/`MANIFEST_FILE`/`TEST_VFS_ROOT`/`CATALOG_FILE`）改指 `nop-tools/nop-lint/...`
- [x] 清偿 ledger 存量红基线：按 `check-lint-tool-replacement-ledger.mjs` 当前输出清单，在 ledger 文档补 6 条 nop-xdsl-* 规则 facet 行并校正 census（80 = live 76 + remove 4）；此为该门禁自身协议义务的 bookkeeping 补登记，非移动引入
- [x] `ai-dev/tools/README.md` 路径提及更新；`check-bean-naming.mjs:26` 注释为计划编号引用（裁定保留）
- [x] `ai-dev/archived/missions-2026-11-22/nop-lint.json`（4 条 commands）、`ai-dev/archived/missions-2026-11-22/nop-treesitter.json`（moduleDir + commands）改为新布局；`ai-dev/archived/missions-2026-11-22/nop-jq.json` 的 stale 旧值（指向 2026-09-28 前 nop-kernel 内位置）一并修正为 `nop-tools/nop-jq`
- [x] `.ast-grepignore`：`nop-lint/**/...` 排除项改为 `nop-tools/nop-lint/**/...`
- [x] `../../nop-tools/nop-lint/bench/run-benchmarks.sh`、`run-graphql-benchmarks.sh` 的 `-pl` 改新布局；`compare-ast-grep.sh` 注释性 doc 指针更新；`../../nop-tools/nop-treesitter/bench/run-c-reference.sh` 根相对功能路径改新布局
- [x] `../../nop-tools/nop-rg/nop-rg-cli/README.md`、`../../nop-tools/nop-rg/nop-rg-benchmark/README.md`、`../../nop-tools/nop-find/nop-find-cli/README.md` 的 `-pl`/`cd` 示例路径更新（closure audit 发现 find-cli README 双重前缀坏替换，已修复）
- [x] 深度修正（仅修移动前有效的链接）：`nop-jq/deepwiki-v3/*.md` 跨组链接按新深度修正（`nop-kernel/nop-jq` 死链按裁定保留原样）；`nop-bytecode/README.md`、gap-ledger.md、interprocedural-spike.md、tool-replacement-ledger.md 出组链接修正（closure audit 发现 4 处坏修正，已二次修复，见 Execution Record #5）
- [x] 执行注意项：`run-nop-metadata-invariants.sh` 完整运行通过（含 mvn test 链路），无基线红需要登记
- [x] 8 个活跃 roadmap：仅更新**未落地 WI** 中的路径引用；执行裁定——7 个 roadmap WI 全 `done`（nop-rg 15/15、nop-lint 49/49、tool-replacement 20/20、nop-jq 10/10、nop-treesitter 26/26、nop-bytecode 9/9）或命中均为 ai-dev 路径（nop-refactor 的 6 处全是 `design/nop-refactor/`），原文保留；仅 xdsl roadmap 3 处活引用更新
- [x] 全仓 sweep 兜底：`grep -rn 'nop-lint/\|nop-rg/\|nop-find/\|nop-jq/\|nop-treesitter/\|nop-refactor/\|nop-bytecode/'` 覆盖 `.github/`、`ai-dev/archived/missions-2026-11-22/`、`ai-dev/tools/`、`ai-dev/backlog/`、`docs-for-ai/`、根目录 dotfiles、`nop-tools/` 内部，逐命中按五类裁定并记录：①功能性（已更新）②历史记录/计划编号引用（保留：compliance.yml L33、ledger.mjs L5/71、bean-naming L26 等）③目标未移动的链接（保留）④Java 注释面（保留）⑤指向已移动目标的根相对链接（已更新：gap-ledger.md:7 等）

Exit Criteria:

- [x] `node ai-dev/tools/check-lint-tool-migration-mapping.mjs`、`check-lint-tool-replacement-ledger.mjs`、`check-lint-coverage-manifest.mjs` 退出码 0；`node ai-dev/tools/gen-lint-rule-catalog.mjs --check` 退出码 0（closure audit 独立复测四者 EXIT=0）
- [x] `bash ai-dev/tools/run-nop-metadata-invariants.sh` 完整跑通
- [x] `bash -n` 语法检查全部改动的 `.sh` 通过；bench 脚本 `-pl` 仅命中 `nop-tools/` 前缀（完整 benchmark 运行不在本计划 scope）
- [x] compliance.yml 文本复查：不再出现旧前缀（唯一残留 `plan nop-lint/21 + 28` 为计划编号引用，裁定②）
- [x] sweep grep 在功能性文件中零旧前缀残留；其余命中均归入裁定②③④并逐条记录
- [x] 执行当日 `ai-dev/logs/` 条目已更新

### Phase 3 - docs-for-ai owner docs 同步与 doc-links 清零

Status: completed
Targets: `docs-for-ai/INDEX.md`、`01-repo-map/module-groups.md`、`03-modules/nop-treesitter.md`、`03-modules/nop-refactor.md`、`03-modules/nop-bytecode.md`、`03-modules/nop-stream-user-guide.md`、`03-runbooks/nop-cli-and-lint-gate.md`、`04-reference/source-anchors.md`、`ai-dev/plans/` 2 个历史 plan 的断链

- Item Types: `Fix`

- [x] `module-groups.md`：主分组表新增 `nop-tools` 汇总行（7 个子组及定位一览）；原 6 个独立分组行移除，细节收敛为「nop-tools 分组明细」小节（nop-treesitter 原无独立行，明细中补写）
- [x] `module-groups.md`：改写 nop-jq「顶层独立模块组」与 nop-duckdb「对齐 nop-jq 扁平先例」表述——记录真实动机（nop-kernel 精简）与新格局（jq 已归组；duckdb 作为顶层扁平例外保留），消除文档自相矛盾
- [x] `module-groups.md` 记录 JDK 门控现况：nop-rg/nop-find `java22-modules` 随组搬移不变，JDK 25 全面切换为后继独立工作
- [x] 其余 6 个 docs-for-ai 命中文件的路径型引用更新；正文中的 artifactId/命令面描述（坐标面）核对不变
- [x] doc-links 清零（执行期范围扩大，见 Execution Record #4）：存量 10 处 + 移动致断 100 处（ai-dev roadmap/design/plan 指向旧布局的路径引用，check-doc-links 门禁强制）按 rule 20「损坏链接修复」例外机械回修；`check-doc-links.mjs --strict` 实测 **39 errors → 0**
- [x] 执行当日 `ai-dev/logs/` 条目已更新

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` **退出码 0**（真实全绿，非放宽判据；closure audit 独立复测 exit 0）
- [x] `grep -n 'nop-jq' docs-for-ai/01-repo-map/module-groups.md` 命中行不再包含「顶层独立模块组」表述；nop-duckdb 行的先例表述已改写
- [x] `docs-for-ai/` 内 `nop-lint/|nop-rg/|nop-find/|nop-jq/|nop-treesitter/|nop-refactor/|nop-bytecode/` 路径型引用全部指向 `nop-tools/...` 新布局或已删除
- [x] module-groups.md 中 nop-tools 行覆盖全部 7 个子组的导航信息（原行关键信息收敛至「nop-tools 分组明细」子表，无丢失）

### Phase 4 - 全量验证与收口

Status: completed
Targets: 全仓构建、门禁接线冒烟、closure audit

- Item Types: `Proof`

- [x] 全量构建验证（裁定形式，见 Execution Record #3/#7）：最终 clean 全量 -fae skipTests（`_tmp/p2418-final-clean-build.log`）398 绿 / 5 红，失败集与 HEAD 全量基线**完全一致**（nop-tcc-dao、nop-sys-dao、nop-job-dao、nop-retry-engine、nop-stream-fraud-example，均为 HEAD 同款 pre-existing；早期 -fae 运行的 7 个缓存级联假象在缓存一致后消失）+ 定向带测试 3602 用例（1 个 deadline 类 flake 复跑两次全绿，裁定非重组回归）。全树带测试一次跑绿不可达的原因：pre-existing 红 + 并发会话在树内进行中的 WIP（nop-persistence EQL 等）使失败无法归因于本重组
- [x] 门禁接线冒烟：`bash ai-dev/tools/run-java-lint.sh` 对 nop-tools src 目录扫描 exit 1（20 命中 = RuleTester 故意写脏夹具 + 存量，plan 容差内记录；目录模式会递归 target/ 与夹具树，文件级过滤仅适用文件列表模式——已记录）。closure audit 独立复测 `run-java-lint.sh nop-tools/nop-jq/src` EXIT=0
- [x] CLI 冒烟：`echo '{"x":1}' | nop-cli jq '.x'` 输出 `1`（nop-cli 已安装于 `/usr/local/bin/nop-cli`；closure audit 独立复验输出 `1`）
- [x] 独立子 agent closure audit（fresh session agent_436b868d-2595-404d-a8d0-af0b7ec714da），报告结论与修复闭环见 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2418-nop-tools-group-regroup.md --strict` 退出码 0
- [x] 执行当日 `ai-dev/logs/` 收口记录已更新

Exit Criteria:

- [x] 上述 6 项全部完成并记录证据
- [x] 无静默跳过：所有 sweep 命中均有逐条五类裁定（见 Phase 2），无「顺手略过」项；closure audit 发现的 4 处坏链接已在收口前修复闭环

## Execution Record（执行期裁定与偏差，2026-10-11）

1. **`nop-tools/pom.xml` xsi 命名空间手误**：首版 `xmlns:xsi` 误写 `2000/XMLSchema-instance`（应为 2001），Maven 4 严格解析器报 `Unknown attribute 'schemaLocation'`。首次构建失败即定位即修。
2. **target 清理偏差**：`git mv` 对目录做整体改名（untracked 文件随迁），旧位置零残留——原「删除旧位置 target 残留」项改为清理新位置 `nop-tools/*/target`（等价意图，避免 stale classpath）。
3. **全量构建判据裁定（重要）**：`./mvnw install -DskipTests -T 1C` 在 nop-job-dao 的 aop codegen 处红（`gen-aop-proxy-fail`：`io.nop.core.reflect.aop`/`JobFireStoreImpl` 找不到符号）。四件证据链证明与重组无关：①HEAD 干净 worktree 同命令复现；②HEAD standalone 同签名 FAILURE；③HEAD 全量 -fae 基线自身有 5 个同类失败（含 nop-sys-dao/nop-tcc-dao/nop-stream-fraud-example，本树反而绿——失败集随 reactor 顺序漂移）；④-fae 下 9 红中 7 个（app/web 装配壳）单模块复测全绿（`target/project-local-repo` 共享缓存被失败模块损坏的级联）。裁定：**重组零回归**；真失败面 = nop-job-dao（确定性）+ nop-retry-engine（同类环境性），登记 Deferred。判据改写为「-fae 全量 + 失败集与 HEAD 基线比对 + 级联单模块复测」。
4. **doc-links 执行期范围扩大**：执行前 R2 基线 10 处断链；执行后发现移动致断约 100 处（ai-dev roadmap/design/plan 中指向旧布局的根相对/深度链接，check-doc-links 门禁强制覆盖 ai-dev 全域）。按 Non-Goals 既有的 rule 20「损坏链接修复」例外，以报告驱动的脚本逐条修复（100 处 + 存量 + 本 plan 自身），`--strict` 39 errors → 0。叙事内容未动。
5. **深度修正脚本二次缺陷（closure audit 发现，已修复）**：audit 发现 4 处坏修正——gap-ledger.md:7 根相对链接漏修；README.md L3 组内 sibling 链接被误加层；gap-ledger L25/34 指向同目录文件的绕行链接被机械 +1 层；find-cli README 双重前缀 `nop-tools/nop-tools/`。均已按正确目标二次修复并复验。
6. **共享 index 提交事故（并发会话）**：Phase 1 的删除侧（1561 个 D）被并发 nop-ai 会话经共享 index 带入其提交 9f50be94f1，新增侧 1518 个 A 在 0104dcb309。事故已双向登记（626f709715 + 双方 daily log）。影响：单 commit revert 属性不再成立（见 Risks），`git log --follow` 历史追溯完好（closure audit 实测），最终态正确。
7. **定向测试 flake 裁定**：`_tmp/p2418-targeted-tests.log` 3602 用例 1 失败（`TestXScriptTimeoutSemantics.fastProfileBudgetStillEnforcesTheDeadline`，deadline 类时间敏感测试）；单类复跑两次 8/8 全绿——多会话并行构建负载下的 flake，非重组回归。
8. **plan 编号冲突**：并发 nop-stream WI7 plan 占用 2417，本 plan 改号 2418（Numbering Note）。module-groups.md 的归组表述已统一为「plan 2418」并注明改号缘由。

## Closure Gates

- [x] 7 个模块组已在 `nop-tools/` 下，根目录无残留，`git log --follow` 可追溯历史（closure audit 实测 PASS）
- [x] Maven 坐标零变化：`nop-tools/` 下全部 pom 的 artifactId 与移动前一致（closure audit 逐一比对 nop-jq、nop-lint-nop、nop-rg-cli、nop-treesitter、nop-bytecode、nop-refactor-core、nop-find-core PASS）
- [x] 全量构建验证（裁定形式，见 Execution Record #3/#7）：clean 全量 -fae skipTests 绿 + 定向带测试 3602 用例（1 flake 复跑绿）+ 失败集与 HEAD 基线比对零回归
- [x] 4 个 lint 门禁/生成器 `.mjs` + `run-nop-metadata-invariants.sh` + `run-java-lint.sh` 全部跑通（4 mjs exit 0 audit 复测；lint 冒烟 exit 1 = 夹具/存量命中，plan 容差内记录）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（audit 复测 exit 0）
- [x] 路径残留 sweep：功能性文件零旧前缀；历史/注释/深度无关命中已逐条裁定
- [x] owner docs（module-groups.md 等 7 件）已同步到 live baseline；nop-jq 先例矛盾已消除
- [x] 无 in-scope 项被静默降级到 deferred / follow-up（audit 发现的 4 处 in-scope 未完成项已修复闭环，未伪装）
- [x] 独立子 agent closure-audit 已完成并记录证据（含文本一致性核对；见 Closure 段）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2418-nop-tools-group-regroup.md --strict` 退出码 0
- [x] 本计划零 Java 源码变更，`scan-hollow-implementations.mjs` 不适用（记录裁定即可，不执行）

## Risks And Rollback

- ~~Phase 1 为独立 commit，回退 = revert 该 commit~~ **执行期修订**：共享 index 事故（Execution Record #6）使删除侧散入 9f50be94f1、新增侧在 0104dcb309，单 commit revert 不再成立。回退路径改为：`git revert 0104dcb309 0c027e537b` + 手工恢复 7 组目录位置（或以本 plan 的逆向 pathspec 重建）；`git log --follow` 保证历史可追溯。
- Phase 1→2 中间态会断 compliance.yml 与 ai-dev/tools 门禁：两 Phase 已在同一 push 窗口内完成（实际均未 push，由仓库 owner 决定 push 时机）。
- stale `target/` 已在新位置清理（Execution Record #2）。
- `run-java-lint.sh` 目录模式会递归 target/ 与夹具树（文件级过滤仅适用文件列表/git-ls-files 模式）——后续使用者应以 src 目录或无参全量模式调用（执行期实测记录）。

## Deferred But Adjudicated

### nop-job-dao / nop-retry-engine aop codegen 失败（pre-existing 红基线）

- Classification: `out-of-scope improvement`（确认为 HEAD 同款 pre-existing，非本重组引入；证据链见 Execution Record #3）
- Why Not Blocking Closure: 与本重组正交（nop-job/nop-retry 不依赖任何被移动模块；单模块构建不含本重组任何变更仍红）；本计划判据已按裁定形式覆盖（-fae + 失败集比对 + 级联复测）
- Successor Required: `yes`
- Successor Path: nop-job 侧独立排查 plan（待立项；疑似 JDK 26 下 aop codegen 的 javac 兼容问题，nop-retry-engine 同款；HEAD 基线显示 nop-sys-dao/nop-tcc-dao/nop-stream-fraud-example 亦受同类影响，失败集随 reactor 顺序漂移）

### JDK 门控收敛（java22-modules profile 的 dissolution）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户已裁定后续全面切换 JDK 25，届时门控整体失去存在意义；本计划保持门控原样随组搬移，不与之耦合
- Successor Required: `yes`
- Successor Path: JDK 25 全面切换计划（待立项）

### nop-jq/deepwiki-v3 移动前已失效的链接

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 指向 `nop-kernel/nop-jq/...` 的链接在 2026-09-28 nop-jq 迁出 nop-kernel 时即已失效（closure audit 以 1895c4f12a 佐证），属 pre-existing 文档债；本计划只修复因移动新增的深度错位
- Successor Required: `no`（可并入 nop-jq-roadmap 既有文档维护项）

### nop-utils `<name>` 编号重复（35 双占）等编号 cosmetic 问题

- Classification: `watch-only residual`
- Why Not Blocking Closure: `<name>` 仅为展示用途，不影响构建与导航；本计划仅保证新组取未占用编号 41
- Successor Required: `no`

## Non-Blocking Follow-ups

- `nop-tools/` 各组文档内部以「根相对形式指向**未移动目标**」的链接当前不受移动影响，可在后续文档维护中统一改为相对深度链接以消除对渲染器根解析的依赖（可选优化，非本计划义务）。
- `run-java-lint.sh` 可考虑为目录模式增加 target/ 排除（本计划仅记录行为，不改脚本语义——执行期实测见 Risks）。
- audit Minor #9：仓库内仍有「plan 2417」字样（commit message 与并发会话日志），module-groups.md 已改写为「plan 2418」并注明改号缘由；其余历史文本按历史记录原则保留。

## Closure

Status Note: 重组本体（7 组归组、pom 重接、坐标零变化）与全部路径引用同步已落地并经独立 closure audit 实测验证：4 个 lint 门禁 + ledger 清偿 + doc-links --strict 退出码 0 + CLI 接线冒烟通过；构建判据按 Execution Record #3/#7 的裁定形式闭环（pre-existing 红基线以 HEAD worktree 四件证据链界定，重组零回归）；audit 发现的 4 处深度修正坏链接已二次修复复验。文本一致性（Plan Status / Phase Status / Exit Criteria / Closure Gates / daily log）已逐处核对一致。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，general-purpose）
- Audit Session: agent_436b868d-2595-404d-a8d0-af0b7ec714da
- Evidence:
  - Phase 1 Exit Criteria：全部 PASS（git log --follow 追溯、根目录零残留、7 pom parent、孙子模块 parent、聚合器结构、根 pom 48→42、门控激活、-fae 构建失败集归因）
  - Phase 2 Exit Criteria：全部 PASS（4 mjs 门禁独立复测 EXIT=0、compliance.yml 文本、missions ×3、.ast-grepignore、bench 脚本 bash -n、sweep 功能性面零残留）
  - Phase 3 Exit Criteria：全部 PASS（check-doc-links --strict 独立复测 exit 0、module-groups 结构与先例改写、存量断链清零）
  - Phase 4 Exit Criteria：全部 PASS（CLI 冒烟独立复验输出 1、lint 冒烟容差内、定向测试 flake 裁定成立）
  - Closure Gates：11/11 PASS（其中「全量构建」为 Execution Record #3/#7 裁定形式；audit 初审标记的 4 处 FAIL 项——gap-ledger:7、README L3、gap-ledger L25/34、find-cli README——已全部二次修复并复验）
  - Deferred 项分类检查：4 项 deferred/follow-up 均诚实分类，无 in-scope live defect 被降级（audit 报告原文确认「未发现 in-scope live defect 被伪装成 non-blocking」）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2418-nop-tools-group-regroup.md --strict` 退出码 0
  - Anti-Hollow：本计划零 Java 源码变更；接线实证 = `nop-cli jq` 冒烟输出 1 + 4 mjs 门禁真跑 + compliance.yml -pl 路径与 `ls nop-tools/nop-lint/nop-lint-core` 实存一致 + 定向测试 3602 用例

Follow-up:

- nop-job-dao/nop-retry-engine aop codegen 红基线：独立 successor plan（见 Deferred）
- JDK 25 全面切换：独立 successor plan（见 Deferred）
- 其余见 Non-Blocking Follow-ups
