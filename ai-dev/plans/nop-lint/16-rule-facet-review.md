# 16 现有 62 条生产规则分面复审（core / optional 逐条裁定 + 处置落地）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 2](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [plan 15（统一账本与分面登记节）](15-tool-replacement-ledger.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [roadmap 定位三轴准入判据](../../backlog/nop-lint-tool-replacement-roadmap.md)

## Purpose

按新定位（AI 编码期核心缺陷防线，风格面 out-of-purpose）对现有 62 条生产规则逐条分面复审：标注 core / out-of-purpose，对风格面规则逐条裁定处置（降 info 或移出库），分面表落统一账本 `nop-lint/docs/tool-replacement-ledger.md`，机械处置落地后全部门禁与测试保持绿。此后新规则按 roadmap 定位三轴准入判据执行。

## Current Baseline

- 62 条生产规则实测（`find …/_vfs/nop/lint/rules -name "*.rule.yml" | wc -l` = 62）：antipattern 9 / api 6 / exception 9 / nop 6 / quality 26 / security 6；severity 分布 = error 7、warning 54、info 1（quality/no-star-import 已是 info）。目录权威 = `nop-lint/docs/rule-catalog.md`（由 `gen-lint-rule-catalog.mjs` 确定性生成，`--check` 门禁强制再生成；**手改无效**）。id 形态：59 条为 `<dir>/<name>`，3 条 XNode 规则为裸 id（`nop-orm-mandatory-default`、`nop-xbiz-auth-not-sole-guard`、`nop-orm-unique-key`，language: XML）。
- 引擎处置杠杆实测（执行前核实）：
  - **不存在 per-profile 规则成员机制**——`LintProfile`（nop-lint-core/engine/LintProfile.java 类注解）明确"one shared rule set … behavioral differences come from analyzer availability only, never from rule downgrading"；profile 只按 capability ceiling 过滤规则（`requires: METRICS` 的 3 条 metrics 规则 + 其他 deep 依赖规则在 standard 档 skipByProfile）。roadmap item 2 措辞中的"降出默认档"在现行引擎中**没有对应机制**——本计划将其重裁定为两个真实杠杆：`severity 降 info`（信号降级，仍全档可见）与`移出库`（删规则）。"consumer 侧 ruleset exemptions"（design 09 §4）是运行方豁免通道，不作为本计划处置杠杆。
  - severity 变更的版本纪律（rule-catalog 头注 + design 01 §2）：**severity 变更必须升 version（1.0→1.1）并在规则文件头注记录**。design 02 §4 另有已记录契约："全库 version 断言保持 62 条全 1.0（版本定档统一归 nop-refactor WI13）"——本计划的 1.1 升档是对该契约的显式修订，须在 design 02 §4 增注。
- 规则 id 的既有消费点全景（remove 联动清单的事实基础，plan 16 R1 审查逐点实测）：
  1. 主资源 `rules/**/*.rule.yml`；
  2. suite 目录 `src/test/resources/_vfs/test/lint/suites/<dir>/<rule>/`（内含 `x:extends` 指针 + valid/invalid fixtures + `.expect`——`.expect` 只钉 line/ruleId/messageContains，**不钉 severity**）；
  3. `TestNopRuleSuites`：`EXPECTED_RULE_IDS` 集合断言 + **硬编码 `assertEquals("1.0", rule.getMetadata().getVersion())` 两处**（主规则循环 + XNODE_RULE_IDS 循环）；
  4. `TestProductionRuleCount`：`EXPECTED_IDS` 全量 62 id 集合 + `assertEquals(62, ids.size())` 双断言；
  5. `TestProductionRuleFixes`：6 条 autofix 规则 FixCase（use-collection-isempty、no-throw-npe、replace-hashtable、replace-vector、random-mod、equals-null）；
  6. deep-suites 家族 launcher：`TestMetricsRuleSuites.DEEP_SUITE_TOTAL`（=11，deep-suites 目录禁静默增减断言）与 L3/L4 launcher 的 PRODUCTION_SUITES 集合；
  7. `ai-dev/tools/check-lint-coverage-manifest.mjs`：tier-1 行做 fixture 存在性检查（`existsSync`）——**remove 一条 tier-1 映射规则必须翻转该 manifest 行 tier 为 `out-of-purpose` + reason**（manifest 词表现无该值：Phase 3 给 `TIER_VOCAB` 增补 `out-of-purpose` + reason 强制（仿 checkExcluded）+ manifest 头注 rubric 行 + design 06 §7 rubric 增注；**禁止翻 t3**——tier 3 = L3/L4 机制面前瞻，对被移出的 L1 规则为事实为假，会毒化 item 6 覆盖矩阵）；manifest 条目本身不可删（design 06 §1–§2 enum-set 双向钉死）；tier 1 = 33 计数同步（roadmap Current baseline + 统一账本行级账本索引）；
  8. `ai-dev/tools/check-lint-tool-migration-mapping.mjs`：landed 行校验目标 id 是规则树中某文件的 `id:` 字段——**landed 7 行 / 6 目标（no-system-out、nop/no-empty-catch×2 行同目标、no-finalize、method-cyclomatic-complexity、no-star-import、no-hardcoded-crypto；EmptyCatchBlock 两行同目标）remove 任一目标都会打红该门禁**（silent-swallow 不在 landed 行内，它只是 §4.1 实测记录中的诊断来源）；
  9. `nop-lint/docs/rule-catalog.md`（生成物）与 `gen-lint-rule-catalog.mjs` self-test 的硬编码（`rules.length !== 62` 即 exit 2、no-system-out 探针）；
  10. 统一账本 `## 分面裁定登记` 节规则级分面表。
  - 历史记录（design 02 增注、旧 plans、logs、design 06 §1–§2 映射表行）合法保留旧 id，豁免于零残留检查。
- 裁定判据（roadmap 定位三轴准入判据 + Purpose 分面表，本计划逐条执行）：
  - `core` = 规则捕捉核心缺陷发现面之一：正确性 bug / 资源 / 并发 / 安全 / 数据流 bug / 平台不变式（Nop 契约：VFS、日志门面、异常纪律、BizModel/IBiz 契约、ORM 完整性、事务注解、auth）。
  - `out-of-purpose` = 风格/可选面（可读性惯例、API 现代化建议、纯命名/格式），信号价值不达"高信号优先"门槛；roadmap 预期 no-star-import、control-statement-braces 在此面。
  - 处置词表（引擎杠杆重裁定）：`keep`（保持现 severity）/ `demote-info`（severity→info + version 1.1 + 头注记录）/ `remove`（移出库）。
  - 附加裁定约束（防跨账本破坏，R1 审查 B3/B4）：
    - **migration-mapping landed 行规则不做 remove**——即使判为风格面也只 `demote-info`（id 必须保留，工具级收口归 items 3/4 与工具终裁联动）；landed 目标中风格面者为 no-star-import。
    - out-of-purpose 行处置 = `keep` 仅当 severity 已为 info；no-star-import 已是 info，其处置 = `keep`，约束自然满足（无 demote 可做——severity 零变化时禁止虚假升版）；风格规则留 warning 与 Hard constraint 2（高信号准入）冲突。
    - core 行处置必为 keep；若逐条裁定中出现 core 行需降档/移除，必须升级为 plan 级偏差记录并重审判据。
    - autofix 6 条（TestProductionRuleFixes 覆盖面）的处置裁定须显式权衡"可修复加分"（AI 自纠价值，准入判据 3）——remove 命中 autofix 规则时联动第 5 消费点。
- 统一账本 `## 分面裁定登记` 节已就绪（plan 15）：per-tool 分面表 8 行待裁（本计划不回填 tool 行）；规则级分面表将作为该节新 subsection 落账本。账本书写纪律：单元格禁 `|`。账本门禁 `check-lint-tool-replacement-ledger.mjs` 现锚定 (工具, 终裁) 与 (工具, 分面标注) 两表——规则级表以 (规则, 分面) 锚定不会误触现有锚（首单元格不同）。
- 测试口径：nop-lint-nop 模块 suite 测试当前全绿（2026-09-26–28 commits；`./mvnw test -pl nop-lint/nop-lint-nop -am` 有同模块族 09-24 绿记录，无已知红基线）。

## Goals

- 62 条规则逐条裁定完成：分面（core / out-of-purpose）+ 处置（keep / demote-info / remove）+ 逐条理由，62 行表落统一账本分面裁定登记节。
- 风格面机械处置落地：`demote-info` 规则 severity→info（version 1.1 + 头注 + 测试 version 断言同步）；`remove` 规则按上表 10 点消费面全景逐点联动。
- 统一账本门禁扩展：规则级分面表纳入 `check-lint-tool-replacement-ledger.mjs` 看守（id 与 live 规则集联动 + 分面 enum + 处置 enum + remove 行证据保留），self-test 同步增补。
- owner-doc 同步：design 02（准入判据增注 + §4 version 契约修订增注）+ design 06 §8.1（判据修订联动增注，兑现 roadmap 风险提示）。

## Non-Goals

- 不回填统一账本 per-tool 分面表（items 3/4/9 的职责）。
- 不新增任何规则、不改任何 core 规则的 pattern/xscript 语义。
- 不动 requires: METRICS / deep 依赖规则的运行机制（metrics 3 条规则已天然不在 standard 强制面）。
- 不引入 per-profile 规则成员机制（如未来需要，归 design 11 修订）。
- 不处理 ruleset exemptions / suppression 机制（design 09 现状保留）。
- 不移除 migration-mapping landed 行的规则（工具级收口归 items 3/4，见裁定约束）。

## Scope

### In Scope

- `nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/**`：demote-info 的 severity+version+头注；remove 的文件删除。
- `nop-lint/nop-lint-nop/src/test/resources/_vfs/test/lint/suites/**`：remove 的 suite 目录删除（含 deep-suites 家族对应目录）。
- `nop-lint/nop-lint-nop/src/test/java/io/nop/lint/nop/TestNopRuleSuites.java`（EXPECTED_RULE_IDS + version 断言同步）、`TestProductionRuleCount.java`（EXPECTED_IDS + size）、`TestProductionRuleFixes.java`（remove 命中 autofix 规则时）、deep launcher（remove 命中 deep 规则时）。
- `nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml`：remove 命中 tier-1 映射行时的 tier 翻转（→`out-of-purpose` + reason）。
- `ai-dev/tools/check-lint-coverage-manifest.mjs`：`TIER_VOCAB` 增补 `out-of-purpose` + reason 强制 + self-test 增补。
- `nop-lint/docs/checkstyle-pmd-migration.md`：remove/demote 触及 landed 行时的行级增注（本计划约束下只有 demote-info 场景：landed 行 note 增补 info 档事实）。
- `ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md`：§8.1 增注（分面复审对切换判据的联动事实）+ §7 rubric 增注（tier 词表新增 out-of-purpose）。
- `ai-dev/design/nop-lint/02-rule-library.md`：处置词表 + 准入判据引用 + §4 version 契约修订增注。
- `nop-lint/docs/rule-catalog.md`：生成器再生成（禁止手改）。
- `ai-dev/tools/gen-lint-rule-catalog.mjs`：self-test 硬编码同步（62→live 数或动态化；探针规则若被 remove 换探针）。
- `nop-lint/docs/tool-replacement-ledger.md`：62 行规则级分面表（remove 行保留证据）。
- `ai-dev/tools/check-lint-tool-replacement-ledger.mjs`：规则级表 checker + self-test 增补。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md`：item 2 状态翻转 + Current baseline 计数同步（tier 1 = 33 若翻转、62 规则计数）。
- `ai-dev/logs/2026/09-28.md` 日志。

### Out Of Scope

- 规则语义变更、新规则落地、引擎改动、maven/CLI 生态行为变更。
- `docs-for-ai/` 更新——显式裁定：No owner-doc update required（nop-lint 使用面文档不含规则清单权威——目录在 nop-lint/docs，准入在 ai-dev/design）。

## Execution Plan

### Phase 1 - 处置词表裁定与 owner-doc 增注

Status: completed
Targets: `ai-dev/design/nop-lint/02-rule-library.md`、`ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md`

- Item Types: `Decision`

- [x] 处置词表落定：keep / demote-info（severity→info，version 1.0→1.1，头注记录）/ remove（10 点消费面联动清单，见 Current Baseline）；"降出默认档"无引擎机制的重裁定记录在案
- [x] 裁定约束落定：landed 行规则禁 remove；out-of-purpose+keep 仅当 severity 已 info（no-star-import 已 info → keep）；core 行必 keep
- [x] design 02 增注：分面复审归属（链接统一账本规则级分面表）+ 新规则准入判据引用（roadmap 定位三轴）+ 处置词表 + §4 version 契约修订增注（WI13 全库 1.0 定档 → 分面复审例外规则 1.1）
- [x] design 06 §8.1 增注：分面复审对切换判据的联动事实（landed 行禁 remove 的约束、no-star-import 预期 info 档、判据文本修订归 items 3/4）
- [x] roadmap item 2 状态 todo→planned（本 plan 过 draft review 后立即翻转）

Exit Criteria:

- [x] design 02 增注节在档：处置词表三值 + 联动清单引用 + 准入判据引用 + §4 修订增注，与 roadmap Hard constraints 一致
- [x] design 06 §8.1 增注在档
- [x] roadmap item 2 = `planned`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/2026/09-28.md` 已含本 plan 执行条目（Phase 1 落一行；Phase 3/4 补全）

### Phase 2 - 62 条逐条裁定与分面表落账本

Status: completed
Targets: `nop-lint/docs/tool-replacement-ledger.md`

- Item Types: `Decision`

> **执行期裁定增注（deep-only keep）**：metrics 3 条（method-cognitive/cyclomatic/npath-complexity）裁定为 out-of-purpose + keep——requires: METRICS 使其在 standard 强制面 skipByProfile，结构上满足"高信号默认流"约束意图；本约束字面（仅 info 可 keep）收窄为适用 standard 面 warning 规则。裁定理由已落账本规则级表"裁定理由"列。

- [x] 逐条读 62 个 rule.yml（id / severity / message / pattern 语义），按 Phase 1 判据逐条标注：分面 + 处置 + 一句理由
- [x] 62 行表落统一账本 `## 分面裁定登记` 节新 subsection `### 规则级分面表（62 条生产规则复审，roadmap item 2）`，列：规则 | 分面 | 处置 | 裁定理由 | 重估触发；全部单元格遵守禁 `|` 纪律；id 与 rule-catalog id 列逐字一致（含 3 条裸 id XNode 规则）
- [x] 裁定自检：62 行全覆盖；分面 ∈ {core, out-of-purpose}；处置 ∈ {keep, demote-info, remove}；core 行处置全 keep；remove 清单不含 landed 6 目标；out-of-purpose+keep 行 severity 已为 info 或 deep-only（见执行期裁定增注）；remove 命中 autofix 6 条时逐条记录权衡——实测 remove 4 条均不在 autofix 6 条内（零命中，无需权衡记录）

Exit Criteria:

- [x] 统一账本规则级分面表恰 62 行，id 集合与 live 规则集一致（Phase 3 checker 上线后以门禁联动为准；closure audit 实测：live 58 每个恰一行、非 live id 恰为 4 条 remove 行）
- [x] 表中分面/处置值全部落在词表内且满足裁定约束（core→keep、remove 不含 landed、out-of-purpose+keep 仅 info 或 deep-only）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/2026/09-28.md` 已含裁定计数汇总（Phase 4 完整化）

### Phase 3 - 机械处置落地与门禁扩展

Status: completed
Targets: rules 主资源、suite 目录、TestNopRuleSuites / TestProductionRuleCount / TestProductionRuleFixes / deep launcher、`pmd-errorprone-coverage.yml`、`checkstyle-pmd-migration.md`、`gen-lint-rule-catalog.mjs`、`check-lint-tool-replacement-ledger.mjs`、rule-catalog.md、统一账本

- Item Types: `Fix`（处置落地）+ `Proof`（门禁扩展）

- [x] demote-info 规则逐个执行：severity `warning`→`info`、version `1.0`→`1.1`、头注追加处置记录（分面复审 demote-info，roadmap item 2，执行日）；`TestNopRuleSuites` version 断言同步（硬编码 "1.0" 改为按规则 id 的期望版本映射，默认 1.0、demote 规则 1.1）
- [x] remove 规则逐个执行（10 点联动清单逐点勾验）：
  - [x] 主 rule.yml 删除
  - [x] suite 目录删除（含 `.expect`）；deep 规则同步对应 deep-suites 目录与 launcher（`TestMetricsRuleSuites.DEEP_SUITE_TOTAL`、L3/L4 PRODUCTION_SUITES）
  - [x] `TestNopRuleSuites.EXPECTED_RULE_IDS` 移除
  - [x] `TestProductionRuleCount.EXPECTED_IDS` 移除 + size 断言改新值（62→N）
  - [x] `TestProductionRuleFixes` FixCase 移除（若命中 autofix 6 条）
  - [x] `pmd-errorprone-coverage.yml` tier-1 映射行翻转为 `out-of-purpose` tier + reason（引用统一账本规则级表锚）；`check-lint-coverage-manifest.mjs` 的 `TIER_VOCAB` 增补 `out-of-purpose` 值 + reason 强制（仿 checkExcluded，self-test 同步增补）+ manifest 头注 rubric 行 + design 06 §7 rubric 增注；roadmap Current baseline 与统一账本行级账本索引的 tier 1 = 33 计数同步
  - [x] `gen-lint-rule-catalog.mjs` self-test 硬编码 62 同步（或动态化）；探针规则若被 remove 换探针
  - [x] `rule-catalog.md` 再生成
  - [x] 统一账本规则级表 remove 行**保留**（处置列 `remove` + 行尾备注"已移出"，理由与重估触发留存——Hard constraint 3 证据链），checker 对 remove 行豁免 live-id 存在性校验
- [x] `check-lint-tool-replacement-ledger.mjs` 扩展：规则级表 checker——锚定 (规则, 分面)；**双向一致性校验**（本 checker 是"处置落地后"不变式——须在 remove 执行完毕后上线，顺序：先删规则后启校验）：每个 live 规则 id 恰有一行（防漏行）、非 remove 行 id 必须存在于 live 规则集、remove 行 id 必须**不**存在于 live 规则集（防陈旧 remove 标记）、行数等式 = live 数 + remove 行数 = 62（plan 期常量，checker 注释标明未来加规则须同步）；分面 ∈ {core, out-of-purpose}；处置 ∈ {keep, demote-info, remove}；self-test 增补 known-bad 用例（非法分面 / 非法处置 / 非 remove 行幽灵 id / remove 行 id 仍 live / live 规则缺行 / 行数不一致）
- [x] landed 行 demote-info 场景：`checkstyle-pmd-migration.md` 对应行 note 增补 info 档事实（不改 status——landed 判定不含 severity）；§4.1"现行口径"表两行（62 条规则库 / 140 diagnostics）增注"2026-09-24 实测快照，分面复审后口径以 rule-catalog 与统一账本为准"

Exit Criteria:

- [x] `node ai-dev/tools/gen-lint-rule-catalog.mjs --check` exit 0 且 `self-test` exit 0
- [x] `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs` 主检查 + self-test exit 0
- [x] 负控验证一次：临时给规则级表某行写非法处置值，门禁 exit 1；还原复跑 exit 0
- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿
- [x] demote-info 规则 version 均 1.1 且头注有处置记录（grep 计数 = demote 行数）
- [x] remove 规则零残留：活消费面清单 grep 全空（主资源、suites、EXPECTED_RULE_IDS、TestProductionRuleCount、TestProductionRuleFixes、deep launcher、manifest fixture 列、migration-mapping landed 行、rule-catalog）；历史记录（design 增注、旧 plans、logs、design 06 §1–§2 表行）豁免
- [x] `node ai-dev/tools/check-lint-coverage-manifest.mjs` 与 `check-lint-tool-migration-mapping.mjs` exit 0（tier 翻转与 landed 增注后不破）；design 06 §7 rubric 增注 + manifest 头注 rubric 行在档
- [x] `ai-dev/logs/2026/09-28.md` 已记录处置执行与联动细节

### Phase 4 - 回归与收口准备

Status: completed
Targets: 全部门禁与文档一致性

- Item Types: `Proof`

- [x] 五门禁 + coverage/mapping 门禁联跑全绿
- [x] roadmap item 2 状态翻转前置检查：Phase 1–3 全部 Exit Criteria 勾选完毕（done 翻转仍须等独立 closure audit）
- [x] `ai-dev/logs/2026/09-28.md` 完整执行条目（裁定计数汇总：core x / out-of-purpose y（demote a / remove b / keep c））

Exit Criteria:

- [x] 门禁联跑全 exit 0：ledger（含规则级表）、migration-manifest、tool-migration-mapping、coverage-manifest（含 out-of-purpose tier 新词表）、gen-lint-rule-catalog --check、doc-links strict
- [x] 日志条目含裁定计数汇总与门禁结果
- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 复跑确认全绿（收口前最终态）

## Closure Gates

> 涉及规则资源变更（非 Java 产品代码）：构建验证 = `./mvnw test -pl nop-lint/nop-lint-nop -am`（Phase 3/4 Exit Criteria）。

- [x] Phase 1–4 全部 Exit Criteria 勾选完毕
- [x] 62 条规则逐条裁定完成且分面表落统一账本，表中裁定与 live 规则资源状态一致（demote/remove 已机械落地，remove 行证据保留）
- [x] 全部门禁绿 + nop-lint-nop 测试全绿
- [x] roadmap item 2 状态 `done`——仅在独立 closure audit 完成并写入 Closure 证据后翻转；roadmap 中无与本计划产出矛盾的文本（Current baseline 计数同步在案）
- [x] 无被静默降级的 in-scope live defect（复审发现的规则语义问题若超出处置杠杆，登记为独立发现，不入 follow-up 掩盖）
- [x] design 02 / design 06（§7 rubric + §8.1）owner-doc 增注在档
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] Anti-Hollow Check：closure audit 确认（a）62 行表每行有裁定理由列（非 catalog 复述），（b）账本门禁对规则级表的真实违规能拒绝（负控在案），（c）无空 checker
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-nop --severity high` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/16-rule-facet-review.md --strict` exit 0

## Deferred But Adjudicated

（无——本计划无延期项）

## Non-Blocking Follow-ups

- 复审中若发现某 core 规则存在语义缺陷（pattern 误报/漏报面），本计划只登记不修——修复归独立 bug fix（Bug Fix Test Coverage Rule 适用），不阻塞本计划 closure。
- ruleset exemptions 面的消费侧文档（design 09）与处置词表的关系梳理，归后续 plan。
- items 3/4 收口时的"核心行升规则 / 风格行 out-of-purpose 归档"判据文本修订（roadmap 风格行处置的最终归档），以本计划分面表为输入。

## Closure

Status Note: 四个 Phase 全部完成且经独立 fresh-session 子 agent closure audit 逐项 live 复核通过（含审计员自行复做的双重负控：非法处置值与 ghost id 均被门禁拒绝），62 条规则裁定（core 46 / out-of-purpose 16）与 live 资源状态一致，全部门禁与 995 项模块测试绿，roadmap item 2 已翻 `done`。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_a3d8c4ac-be79-429c-828b-2b806fcb497d（fresh session）
- Audit Session: agent_a3d8c4ac-be79-429c-828b-2b806fcb497d
- Evidence:
  - Phase 1 owner-doc：design 02 §4 WI13 修订增注 + §5 分面复审节、design 06 §7 rubric v3 + §8.1 联动增注四处在档 PASS
  - Phase 2 分面表：62 行实数核对（core 46 / demote 8 / remove 4 / keep 50 与声明一致）；抽样 8 行对照 rule.yml 语义全符；remove 4 ∩ landed 6 目标 = ∅ PASS
  - Phase 3 机械处置：demote 三重标记（severity info / version 1.1 / 头注）精确 8 文件；remove 零残留（8 个活消费面 grep 全空；REMOVED_RULE_IDS 已接 assertFalse 非死代码）；census 62→58；tier 翻转 3 行 + 计数 33→30 双处同步 PASS
  - 门禁与测试：`./mvnw test -pl nop-lint/nop-lint-nop -am` 995 tests 0 failures BUILD SUCCESS；六门禁 + hollow 扫描全 exit 0；`check-plan-checklist --strict` exit 0（收口回写后）
  - 负控复做（审计员独立执行）：非法处置值 → exit 1；ghost id → exit 1 双报（ghost + missing bookkeeping）；均还原复绿
  - 裁定抽审 6 条：empty-while-body vs empty-if-block 区分、no-return-null、autofix 权衡、metrics deep-only keep、landed 禁 remove、remove 4 理由——无 Hard constraint 2 / Purpose 违背
  - 审计 Minor-1（Phase 2 Exit Criteria 4 项漏勾）已随收口回写补勾；Minor-2（roadmap L121 历史锚点注记）已采纳
- Follow-up:

Follow-up:

- core 规则语义缺陷（若复审中发现）归独立 bug fix，不阻塞 closure（本计划实际未发现此类登记项）。
- items 3/4 收口时的判据文本修订以本计划分面表为输入（roadmap 风险提示的兑现路径）。
