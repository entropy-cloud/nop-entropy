# checkstyle/pmd 迁移映射 + 双工具并行 + 回退预案（roadmap item 40）

> Plan Status: active
> Last Reviewed: 2026-09-24
> Review: R1 对抗审查 agent_9e12cd2a（1 Blocker + 4 Major + minors 全部落表：B1 manifest 行数口径/M1 门禁契约三钉/M2 id 字段校验/M3 profile+delta 附注/M4 dogfooding 实跑；门禁改名消歧/landed 多 id 语法）
> Source: ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md §8（迁移步骤 Phase 4）、§7（coverage manifest——PMD 面已有逐条 tier 账本）、根 pom `qa` profile（checkstyle 10.21.1 + pmd 7.26.0，`failOnViolation=false` 报告态）
> Related: roadmap item 40（deps: 29, 39 done）；前序 item 28（check-*.mjs manifest 路由）、29/36（PMD/EP coverage manifest tier 1=33）

## Purpose

三件事收口：(1) 为 `checkstyle.xml`（17 条激活规则）与 `pmd-ruleset.xml`（9 条规则）逐条建立迁移映射——nop-lint 已落地等价规则的给出规则 id，其余明确路由（keep-checkstyle / keep-pmd / deferred——四态词表由门禁强制；import-order 类序列属性检查已由 item 28 路由 maintain-mjs,不属本表），杜绝"切换后覆盖缺口"；(2) 固化双工具并行期的运行口径与切换判据；(3) 回退预案。本 item **不执行切换**（不删 checkstyle.xml/pmd-ruleset.xml，不改 qa profile）——切换是映射表全绿后的独立运维动作。

## Current Baseline（live 核对 2026-09-24）

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

Status: planned
Targets: `nop-lint/docs/checkstyle-pmd-migration.md`、`ai-dev/tools/check-lint-tool-migration-mapping.mjs`

- Item Types: `Decision | Proof`

- [ ] 精确枚举 checkstyle.xml 激活规则（TreeWalker module 逐条 + severity=ignore 排除口径）与 pmd-ruleset.xml 9 条，共 26 行
- [ ] 逐行裁定映射（landed 行给出规则 id 并按文件内 id 字段实存验证 + profile/delta 附注；keep/deferred 行给理由），写入 `nop-lint/docs/checkstyle-pmd-migration.md`
- [ ] 门禁脚本 `check-lint-tool-migration-mapping.mjs`（XML enum-set 双向对账 + landed 按 id 字段存在性 + 词表强制 + self-test 三正控）退出 0
- [ ] 并行期实跑：nop-lint check（standard）vs qa profile 双工具，2 个代表模块对照记录落文档
- [ ] 迁移文档成文（映射表 + 并行期口径含实跑记录 + 切换判据按 enforcement surface + 回退预案）
- [ ] `ai-dev/logs/` 条目更新

Exit Criteria:

- [ ] 26 行映射全部冻结（无待定）；`landed:` 行按规则文件内 id 字段全部实存（门禁强制）；landed 行附注 profile 面 + 语义 delta
- [ ] 门禁 + self-test 退出码 0
- [ ] 迁移文档存在且含并行期/切换判据/回退三节
- [ ] `ai-dev/logs/` 条目更新

### Phase 2 - owner docs + 全量回归 + 收口

Status: planned
Targets: `ai-dev/design/nop-lint/06-*.md §8`、roadmap

- Item Types: `Proof`

- [ ] design 06 §8 增注（映射表落点 + 切换判据摘要）；`check-doc-links --strict` 0
- [ ] 全量回归 nop-lint 子模块（零代码变更项：core/nop 抽跑即可，如实记录）
- [ ] roadmap item 40 → done（closure audit 后）

Exit Criteria:

- [ ] 全量绿 + doc-links 0
- [ ] roadmap item 40 → done

## Closure Gates

- [ ] 26 行映射冻结且 landed 行规则实存（门禁背书）；并行期实跑对照记录在档
- [ ] 门禁 + self-test 退出码 0
- [ ] 迁移文档三节齐（映射/并行期/回退）
- [ ] owner docs 与 live 一致
- [ ] 独立子 agent closure audit 写入 Closure 段
- [ ] checklist/hollow/doc-links 门禁 0/0/0

## Non-Blocking Follow-ups

- keep-checkstyle/keep-pmd 路由行的 nop-lint 等价实现（随 manifest tier 2/3 后续批次消化）
- 切换执行（映射表 keep 路由清零后的独立运维动作）

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
