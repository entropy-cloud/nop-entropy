# 18 checkstyle.xml qa profile 配置段切换 + Checkstyle 工具级终裁回填（roadmap item 3b）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 3](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [plan 17](17-checkstyle-facet-adjudication.md) · [checkstyle-pmd-migration 账本](../../../nop-lint/docs/checkstyle-pmd-migration.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [design 06 §8](../../design/nop-lint/06-pmd-errorprone-alignment.md)

## Purpose

checkstyle 侧切换判据达成（17 行 = landed 9 + out-of-purpose 8，plan 17 落地）后执行配置段处置：移除 root pom 与 nop-kernel pom 的 maven-checkstyle-plugin 接线及死残余、删除 checkstyle.xml，迁移门禁同步适配，Checkstyle 工具级终裁回填统一账本。本计划的单个 commit 即切换纪律的回退点（`git revert` 本计划 commit 恢复双工具并行态，roadmap item 3 状态翻转随之连带回退，账实一致）。

## Current Baseline

- 判据事实（plan 17 落地，closure audit agent_200546ad APPROVE）：checkstyle 17 行 = landed 9 行/9 规则（同语料对照零 diff 在档 §4.2）+ out-of-purpose 8 行（理由在档）；migration doc §3 判据 = "全部行 landed 或 out-of-purpose → 该段可移除"。
- checkstyle 接线点全景（R1 审查实测）：
  - root `pom.xml`：qa profile 插件块（469 起注释行含使用口径）+ **死残余**：属性 `maven-checkstyle-plugin.version/failsOnError/failOnViolation/includeTestSourceDirectory/spring-cloud-build-checkstyle.branch`（73–78 行）与整块注释掉的 pluginManagement（139–166 行）。
  - **`nop-kernel/pom.xml:404–421`：独立 qa profile 自带 maven-checkstyle-plugin 块**（无 parent，被 root reactor 聚合；configLocation 指向同一 checkstyle.xml）——roadmap 在用工具清单此前漏记该接线点。不处置则 nop-kernel 子树 `checkstyle:check -Pqa` 因配置缺失 BUILD FAILURE，且终裁宣称"原接线点按判据处置"不成立。
  - `checkstyle.xml`（根目录）；CI workflows / scripts / package.json 无引用（实测）；`ai-dev/design/code-quality/checkstyle-configuration.md`（resolved 状态 owner doc，含 `-Dcheckstyle.config.location` 运行指引）删除后指引悬空，须处置。
- mapping 门禁（`check-lint-tool-migration-mapping.mjs`）适配点（R1 实测）：`buildLiveContext()`（:177–188）对 CHECKSTYLE_FILE 直接 `readFileSync`——文件删除后是未捕获 ENOENT 而非结构化报错；`checkAll` 的 enum-set/ghost 双向校验以两侧 config 并集为 authority。缺席适配三要素：①buildLiveContext 对 checkstyle.xml `existsSync` 容缺席并传缺席标记（null 哨兵，与"存在但解析为空"区分）进 checkAll；②缺席侧放行判据 = doc 中 checkstyle: 前缀行全部 ∈ {landed, out-of-purpose}，任一行 keep-checkstyle/deferred → hard error（未迁移行不得静默注销）；③**selfTest control 1（dropped row）现取 sampleSources[0]=checkstyle:RegexpSingleline，缺席放行后该侧 ghost 校验空转 → control 1 必然假红，须改锚 pmd 行**；缺席负控用例以合成 doc 直调 `checkAll({doc, checkstyleXml: null, pmdXml, ruleIndex})` 构造，不删真实文件。
- design 06 §8/§8.1 待修订文本：§8.1 增注"现两份旧配置均不可移除"与步骤 1–4 原文（plan 16/17 两度承诺全量修订归本计划）；migration doc §3 第 2 条 deep-only 条款（"CyclomaticComplexity 不得计为已覆盖除非升 deep 档"）与 item 3a 增注并存待处置——显式裁定：CC 行 landed（deep 面），standard 面不设防为**已接受 delta**（复杂度度量在新定位下属 out-of-purpose 可选面，plan 16 裁定在档），不阻碍切换。
- 统一账本回填机制门（plan 15）：终裁 ≠ 待裁须证据列含 `](./` 或 `](../` markdown 链接 + 残余范围非 —；分面标注 token ∈ 四值词表。
- 测试/门禁基线：全绿（plan 17 后）。

## Goals

- root pom（qa profile 插件块 + 73–78 死属性 + 139–166 注释块 + 469–470 使用口径注释行）与 nop-kernel pom（404–421 插件块）的 checkstyle 痕迹清零；`checkstyle.xml` 删除（git 历史保留，revert 本计划 commit 即恢复）。
- mapping 门禁缺席判据落地（三要素如上）+ self-test 重构（control 1 改锚 pmd 行 + 缺席合成 doc 负控）。
- design 06 §8/§8.1 判据文本全量修订（含 §3 deep-only 条款显式裁定）+ migration doc §1/§3/§5 注记 + `checkstyle-configuration.md` 归档注记。
- 统一账本：Checkstyle 终裁行 `core-face-replaced` + 分面行 `core / out-of-purpose` 回填；roadmap item 3 → `done`；"checkstyle 侧可移除待 plan 18 切换"陈旧短语（roadmap Current baseline 首条 + 统一账本行级账本索引行）随切换同步改写。

## Non-Goals

- pmd-ruleset.xml 与 pmd 插件（item 4；pmd 侧 keep 为主，不可移除）。
- Sonar/SpotBugs 等其他工具接线；`docs-for-ai/`（No owner-doc update required——qa profile 非平台契约；checkstyle-configuration.md 属 ai-dev/design 面由本计划处置）。

## Scope

### In Scope

- `pom.xml`、`nop-kernel/pom.xml`（checkstyle 痕迹清零）、`checkstyle.xml`（删除）。
- `ai-dev/tools/check-lint-tool-migration-mapping.mjs`（缺席判据三要素 + self-test 重构）。
- `ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md` §8/§8.1 全量修订；`ai-dev/design/code-quality/checkstyle-configuration.md` 归档注记。
- `nop-lint/docs/checkstyle-pmd-migration.md` §1/§3/§5 注记；`nop-lint/docs/tool-replacement-ledger.md` 回填。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md`（在用工具清单行接线点更正 + item 3 行 plan 指针更新 + 状态翻转）；`ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- maven qa profile 其余插件（pmd/spotbugs）；任何 Java 代码；`docs-for-ai/`。

## Execution Plan

### Phase 1 - 切换执行 + 门禁缺席判据 + 终裁回填

Status: completed
Targets: `pom.xml`、`nop-kernel/pom.xml`、`checkstyle.xml`、mapping 门禁、design 06、`checkstyle-configuration.md`、migration doc、统一账本、roadmap

- Item Types: `Fix`（切换）+ `Decision`（终裁回填 + deep-only 条款裁定）

- [x] root pom：qa profile maven-checkstyle-plugin 块删除；73–78 死属性删除；139–166 注释 pluginManagement 块删除；469–470 使用口径注释行改写（去 checkstyle 字样）；nop-kernel pom：404–421 插件块删除 + 71、73–76 含 checkstyle 字样死属性删除（与 root 73–78 同款；pmd/spotbugs 块不动）；`checkstyle.xml` 删除
- [x] mapping 门禁缺席判据（三要素）：`buildLiveContext` existsSync 容缺席（null 哨兵传 checkAll）；缺席侧放行 = checkstyle 行全部 landed/out-of-purpose 否则 hard error；pmd 侧校验不变；self-test：control 1 改锚 pmd 行（pmd:EmptyCatchBlock 或 pmd:JumbledIncrementer）、新增合成 doc 缺席负控两例（缺席 + 全部行 landed/out-of-purpose → 放行；缺席 + 残留 keep-checkstyle 行 → 拒绝），直调 `checkAll` 构造不删真实文件
- [x] design 06 §8 头部与 §8.1 全量修订：checkstyle 段已按判据移除（landed 9 + out-of-purpose 8，plan 17 对照零 diff 在档，切换 = plan 18 commit）；pmd 段判据未达成不可移除；步骤 1–4 原文标注为被 3a/3b 计划取代的历史文本；§8.1 增注中"现两份旧配置均不可移除"改写
- [x] migration doc：§1 现状段注记（checkstyle 接线已移除，含 nop-kernel 侧）；§3 第 2 条 deep-only 条款显式裁定（CC 行 landed（deep 面），standard 面不设防 = 已接受 delta，理由 = plan 16 将复杂度度量裁为 out-of-purpose 可选面）并宣告判据达成；§5 回退预案注记（revert 本计划 commit 恢复并行态，状态翻转连带回退）
- [x] `ai-dev/design/code-quality/checkstyle-configuration.md` 头部归档注记（checkstyle 接线已随 item 3b 移除，指引指向的历史文件见 git 历史）
- [x] 统一账本回填（列值过回填机制门）：Checkstyle 终裁行 = `core-face-replaced`，残余范围 = `风格/可选面 8 行 out-of-purpose（显式不迁移）`，证据 = `[行级账本 §2/§4.2](./checkstyle-pmd-migration.md)`，roadmap items = `3`；分面行分面标注 = `core / out-of-purpose`，依据/证据/重估触发同源（core 9 行承接 + 风格 8 行归档；重估触发 = 风格面入 mandate 或对照被推翻）
- [x] roadmap：在用工具清单 Checkstyle 行更正（接线点补 nop-kernel + 注记已移除）；item 3 行 plan 3b 指针改为本 plan 文件名
- [x] 门禁联跑 + pom 验证：`grep -ci checkstyle pom.xml nop-kernel/pom.xml` 均 0（判据以 grep 为准）+ `./mvnw help:effective-pom -Pqa -N` 解析成功（解析合法性辅证）；纯配置/门禁/文档变更，mvn test 显式免除

Exit Criteria:

- [x] `grep -ci checkstyle pom.xml nop-kernel/pom.xml` 输出双 0；repo 无 checkstyle.xml；`./mvnw help:effective-pom -Pqa -N` exit 0
- [x] `node ai-dev/tools/check-lint-tool-migration-mapping.mjs` 主检查 + self-test exit 0（缺席侧放行 + pmd 侧不变 + 新负控全过）
- [x] 负控：合成 doc 缺席 + 残留 keep-checkstyle 行 → 门禁逻辑拒绝（self-test control 断言即证据；另以临时编辑真实 doc 复核一次主检查 exit 1，还原 exit 0）
- [x] 五门禁（ledger/migration-manifest/mapping/coverage/doc-links strict）全绿
- [x] 统一账本终裁行/分面行回填且账本门禁 exit 0（回填机制门通过）
- [x] design 06 / migration doc / checkstyle-configuration.md 修订在档；`ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required（qa profile 非平台契约；design 面修订在档即 owner-doc 同步）

## Closure Gates

> 纯配置/门禁/文档变更：构建验证 = grep 判据 + effective-pom 解析 + 全门禁绿（mvn test 不适用，显式免除）。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] 切换完成：两 pom 无 checkstyle 痕迹、checkstyle.xml 删除、回退点 = 本计划 commit（revert 连带 roadmap 状态回退，账实一致）
- [x] 判据文本（design 06 §8.1）与账本终裁已收敛，无"checkstyle 不可移除"类陈旧宣称残留
- [x] roadmap item 3 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] Anti-Hollow Check：门禁缺席判据不是空壳（负控在案：缺席 + 残留 keep 行必须拒绝）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/18-checkstyle-qa-profile-switchover.md --strict` exit 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- nop-spring-demo 等 demo 模块在 plan 17 对照实跑中使用默认 checkstyle 配置的旁支现象，随 checkstyle 整体移除而消灭，无需处置。

## Closure

Status Note: 切换完成且经独立 fresh-session 子 agent closure audit 七项全 PASS（审计员独立复现负控：缺席 + 未迁移行被 "no silent deregistration" 拒绝；checkstyle.xml 可从 git 历史恢复，revert 本计划 commit 即回并行态；diff 无 .java 文件，mvn test 免除成立）。审计 Major-1（§3 残留陈旧行）与 Minor-2（landed 11 全表口径歧义）已随收口修正。本计划关闭，Checkstyle 终裁 core-face-replaced 成立。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_18f4e291-d865-43fc-a49d-7b1a6e9f59fe（fresh session）
- Audit Session: agent_18f4e291-d865-43fc-a49d-7b1a6e9f59fe
- Evidence:
  - 切换面：grep 双 0、checkstyle.xml 已删（git show HEAD:checkstyle.xml 可恢复）、effective-pom -Pqa -N 成功、pmd/spotbugs 块未受损
  - 门禁缺席判据：null 哨兵/缺席放行判据/pmd authority/control 1 改锚 + control 5/6 逐条实读核对；主检查 + self-test exit 0；审计员复做负控（RegexpSingleline 翻 keep-checkstyle → exit 1 报 no silent deregistration，还原 exit 0）
  - 终裁回填：core-face-replaced + 残余范围 + 证据锚过账本回填机制门（门禁 exit 0）；行级索引行同步
  - 文档收口：design 06 §8/§8.1、migration doc §1/§3/§5、checkstyle-configuration.md 归档注记、roadmap 清单行（含 nop-kernel 接线点更正）全在档
  - 回归：五门禁全 exit 0；diff 无 .java 文件（mvn test 免除成立）；check-plan-checklist --strict exit 0（回写后）
  - 审计发现处置：Major-1（migration doc §3 残留"两份均不可移除"陈旧行）已随本 commit 改写为 pmd-only 表述；Minor-2（landed 11 全表口径歧义）已在账本索引与 roadmap baseline 钉明 split（checkstyle landed 9 / pmd landed 2）；Minor-1（item 3 翻转先于 audit 完成的时序瑕疵）——本 audit APPROVE 使终态收敛一致，特此记录该时序偏差（后续 item 收口严格等 audit 报告后再翻状态）
- Follow-up:

Follow-up:

- 无 remaining plan-owned work（pmd 侧收口归 item 4 另 plan）。
