# checkstyle/pmd 迁移映射 + 双工具并行 + 回退预案（roadmap item 40）

> Plan Status: completed
> Last Reviewed: 2026-09-24
> Review: R1 对抗审查 agent_9e12cd2a（1 Blocker + 4 Major + minors 全部落表：B1 manifest 行数口径/M1 门禁契约三钉/M2 id 字段校验/M3 profile+delta 附注/M4 dogfooding 实跑；门禁改名消歧/landed 多 id 语法）
> Source: ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md §8（迁移步骤 Phase 4）、§7（coverage manifest——PMD 面已有逐条 tier 账本）、根 pom `qa` profile（checkstyle 10.21.1 + pmd 7.26.0，`failOnViolation=false` 报告态）
> Related: roadmap item 40（deps: 29, 39 done）；前序 item 28（check-*.mjs manifest 路由）、29/36（PMD/EP coverage manifest tier 1=33）

## Purpose

三件事收口：(1) 为 `checkstyle.xml`（17 条激活规则）与 `pmd-ruleset.xml`（9 条规则）逐条建立迁移映射——nop-lint 已落地等价规则的给出规则 id，其余明确路由（keep-checkstyle / keep-pmd / deferred——四态词表由门禁强制；import-order 类序列属性检查已由 item 28 路由 maintain-mjs,不属本表），杜绝"切换后覆盖缺口"；(2) 固化双工具并行期的运行口径与切换判据；(3) 回退预案。本 item **不执行切换**（不删 checkstyle.xml/pmd-ruleset.xml，不改 qa profile）——切换是映射表全绿后的独立运维动作。

## Current Baseline

> live 核对 2026-09-24

- 根 pom `qa` profile：maven-checkstyle-plugin 3.6.0（checkstyle 10.21.1）+ maven-pmd-plugin 3.28.0（pmd 7.26.0）+ spotbugs，全部 `failOnViolation=false`/`failOnError=false`——**报告态，非阻塞门禁**，用法 `./mvnw checkstyle:check -Pqa` / `./mvnw pmd:check -Pqa`（注释明示）。
- `checkstyle.xml`：TreeWalker 16 条 + RegexpSingleline 1 条（pending 精确枚举，Phase 1 冻结）。
- `pmd-ruleset.xml`：9 条（EmptyCatchBlock、JumbledIncrementer、AvoidBranchingStatementAsLastInLoop、ImplicitSwitchFallThrough、CloneMethodMustImplementCloneable、CloneMethodReturnTypeMustMatchClassName、ProperCloneImplementation、HardCodedCryptoKey、InsecureCryptoIv）。
- nop-lint 生产库 62 条（coverage manifest tier 1 = 33 行已对账）；`nop-lint check` CLI 全参数面可用（item 39）；`nop-lint-maven-plugin` check goal 可用（item 37）。
- **B1 口径修正（R1）**：coverage manifest 只含 PMD 9 条中的 3 条（EmptyCatchBlock t1、HardCodedCryptoKey t1、InsecureCryptoIv t2），其余 6 条（JumbledIncrementer/AvoidBranchingStatementAsLastInLoop/ImplicitSwitchFallThrough/CloneMethodMustImplementCloneable/CloneMethodReturnTypeMustMatchClassName/ProperCloneImplementation）为 **manifest 外新增映射行**，逐条独立裁定；InsecureCryptoIv 是 tier 2（无 fixture 无规则文件），manifest 行不能支撑 landed 状态——tier 语义随行带入映射附注。checkstyle 17 条全部为新增映射面。

## Goals

1. **映射表（首个交付物）**：26 行逐行映射，每行恰一状态：`landed:<规则 id>`（多 id 并集允许 `landed:<id1+id2>`；附 manifest 行如有）/ `keep-checkstyle` / `keep-pmd` / `deferred:<理由>`。禁止"待定"。**landed 行强制附注（M3）**：(a) 生效 profile 面——deep 规则（requires METRICS/L3/L4）在 CLI 默认 standard 档 skipByProfile，只有 `--profile deep` + provider 装配才触发，必须标注；(b) 语义 delta——阈值/豁免/严重度/scope 与原工具配置的差异（如 no-star-import 会新增 static-star 告警面——checkstyle 配置 allowStaticMemberImports=true；method-cyclomatic-complexity 阈值 10 ≠ checkstyle 15 且 scope 不含构造器；no-empty-catch 无 expected 变量豁免）。**切换判据按 enforcement surface 计**：若切换后的门禁面是 CLI 默认 standard 档，deep-only 行在默认面等效于 keep——切换判据不得把 deep-only 行计为已覆盖。
2. 映射表落 `nop-lint/docs/checkstyle-pmd-migration.md`（**门禁唯一输入与文档唯一宿主**——"回写 plan"不成立，落点唯一），含：双工具并行期运行口径（现状即并行：qa profile 报告态 + nop-lint CLI/plugin 可全量运行）、切换判据（按 enforcement surface 计，见 Goal 1）、回退预案（git 历史恢复 + qa profile 原样保留）。**并行期实跑（M4，roadmap M3 把 repo-wide dogfooding 指派给本 item）**：对 2 个代表模块（nop-lint-core + 一个业务模块）实际运行 `nop-lint check`（standard 档）与 `./mvnw checkstyle:check -Pqa` / `pmd:check -Pqa`，输出对照记录（诊断数/覆盖面/噪音面）落入迁移文档并行期一节。
3. **防腐门禁** `ai-dev/tools/check-lint-tool-migration-mapping.mjs`（R1 改名消歧，与 check-lint-migration-manifest.mjs 区分）。校验面（M1/M2 钉死）：(a) **唯一输入** = `nop-lint/docs/checkstyle-pmd-migration.md` 的 markdown 表格（固定列：source / status / target / note）；(b) **enum-set 双向对账**——脚本解析 checkstyle.xml + pmd-ruleset.xml 的激活规则集（排除 severity=ignore），与映射表行集双向硬错（XML 有行没映射 = 漂移；映射有行 XML 没有 = 幽灵行）；(c) `landed:` 行的规则存在性**按规则文件内 `id:` 字段校验**（扫描 rules 树建 id→路径索引；6 处 id/路径分叉——如 exception/no-empty-catch.rule.yml 内 id 是 nop/no-empty-catch——路径推导必错）；(d) 词表强制：status 恰一于 {landed, keep-checkstyle, keep-pmd, deferred}，未知 token 硬错（"禁止待定"由门禁执行）；(e) self-test 正控 ≥3：漏行、坏状态词、landed 指向不存在 id。
4. design 06 §8 增注（映射表落点 + 切换判据 + 回退）。

## Non-Goals

- 不执行切换（不修改 qa profile / 不删 checkstyle.xml、pmd-ruleset.xml——切换判据达成后是独立运维动作，本 plan 只交付判据）
- 不为 keep 路由规则新写 nop-lint 实现（那是 manifest tier 2/3 的后续批次，本 item 只记账路由）
- spotbugs（qa profile 第三件套不在本 item 范围）

## Execution Plan

### Phase 1 - 映射表冻结 + 门禁 + 文档

Status: completed
Targets: `nop-lint/docs/checkstyle-pmd-migration.md`、`ai-dev/tools/check-lint-tool-migration-mapping.mjs`

- Item Types: `Decision | Proof`

- [x] 精确枚举 checkstyle.xml 激活规则（RegexpSingleline + TreeWalker 16，severity=ignore 无条目）与 pmd-ruleset.xml 9 条，共 26 行
- [x] 逐行裁定映射（landed 7 行带 profile/delta 附注；keep-checkstyle 12 + keep-pmd 7 带理由），写入 `nop-lint/docs/checkstyle-pmd-migration.md` §2
- [x] 门禁脚本 `check-lint-tool-migration-mapping.mjs`（XML enum-set 双向对账 + landed 按 id 字段存在性 + 词表强制 + self-test 三正控）退出 0（62 规则 id 索引 / 26 映射行；执行期修复两处解析 bug：walker 块注释示例模块误收、pmd ref 最后段提取）
- [x] 并行期实跑：nop-lint-core 对照记录落文档 §4.1（nop-lint 121 文件/140 诊断 vs checkstyle 48 vs pmd 7；**dogfood 首轮即暴露抑制解析器对自身包 javadoc 指令字面量 fail-closed 中止的真缺陷——两处 javadoc 最小改写修复，上下文感知识别裁定留 follow-up**）
- [x] 迁移文档成文（§1 并行期口径 + §2 映射表 + §3 切换判据按 enforcement surface + §4 实跑对照 + §5 回退预案）
- [x] `ai-dev/logs/` 条目更新

Exit Criteria:

- [x] 26 行映射全部冻结（无待定）；landed 7 行按规则文件内 id 字段全部实存（门禁强制）；附注 profile 面 + 语义 delta 齐全
- [x] 门禁 + self-test 退出码 0
- [x] 迁移文档存在且含并行期/切换判据/回退三节
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - owner docs + 全量回归 + 收口

Status: completed
Targets: `ai-dev/design/nop-lint/06-*.md §8`、roadmap

- Item Types: `Proof`

- [x] design 06 §8 增注（映射表落点 + 切换判据摘要 + dogfood 缺陷修复记录）；`check-doc-links --strict` 0
- [x] 全量回归 nop-lint 子模块（core 743/0 + nop 75/0——core 含 CommentSuppressionScanner/SuppressionSpan javadoc 改写的重编译验证）
- [x] roadmap item 40 → done（R1 audit APPROVED 后翻转）

Exit Criteria:

- [x] 全量绿 + doc-links 0
- [x] roadmap item 40 → done

## Closure Gates

- [x] 26 行映射冻结且 landed 行规则实存（门禁背书）；并行期实跑对照记录在档
- [x] 门禁 + self-test 退出码 0
- [x] 迁移文档三节齐（映射/并行期/回退）
- [x] owner docs 与 live 一致
- [x] 独立子 agent closure audit 写入 Closure 段
- [x] checklist/hollow/doc-links 门禁 0/0/0

## Non-Blocking Follow-ups

- keep-checkstyle/keep-pmd 路由行的 nop-lint 等价实现（随 manifest tier 2/3 后续批次消化）
- 切换执行（映射表 keep 路由清零后的独立运维动作）

## Closure

Status Note: 26 行映射冻结（landed 7 / keep-checkstyle 12 / keep-pmd 7），门禁 enum-set 双向对账 + id 字段校验 + self-test 三正控全绿；并行期 dogfood 三工具实跑对照在档并暴露/修复了抑制解析器 javadoc 自扫描缺陷；audit APPROVED（其 minor 簿记滞后两处已随收口 reconcile——Phase 1 Status 行与 EC log 条目勾选）。
Completed: 2026-09-24

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_b672950a-9224-4b9a-a732-c1402099404e（fresh session）
- Evidence:
  - 26 行自数吻合（checkstyle 17 + pmd 9，landed 7/12/7）且 3 个 landed 抽样含 id/路径分叉机器证明（PASS）
  - 门禁 + self-test 退出 0；独立 ghost-row 破坏试验（exit 1 → git 恢复 → 0）（PASS）
  - core 743/0 + nop 75/0 独立重跑（javadoc 改写零回归）；doc-links/checklist/hollow 三门禁 0（PASS）
  - dogfood 121/140/48/7 四处记载一致；javadoc 修复 grep 实证无残留（PASS）
  - design 06 §8 增注与 live 一致（PASS）
  - Audit minor（Phase 1 Status 滞后/EC 勾选滞后）已随收口 reconcile

Follow-up:

- keep-checkstyle/keep-pmd 路由行的 nop-lint 等价实现（随 manifest tier 2/3 后续批次）
- 切换执行（keep 路由清零后的独立运维动作）
- 抑制解析器上下文感知指令识别（dogfood 发现的引擎级裁定）

Follow-up:

- （closure 时填写）
