# 2292 unit-test-coverage-roadmap WI0 — 全仓覆盖率基线与分模块目标裁定

> Plan Status: completed
> Last Reviewed: 2026-10-02（rev3：实施期三个管线结构性发现与修复，见 Revision Note）
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI0 条目）
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/00-plan-authoring-and-execution-guide.md

## Purpose

消费既有 jacoco 管线产出全仓分模块行/分支覆盖基线，落一个可重复执行的快照脚本（WI13 复用），并裁定分模块覆盖目标，为 WI1–WI12 提供唯一完成判定口径。

## Current Baseline

- root pom `coverage` profile 默认激活：jacoco 0.8.14 `prepare-agent` 输出到 `jacocoArgLine`，surefire `argLine` 已接线 `${jacocoArgLine}`（surefire argLine 位于 pom.xml:177-183，coverage profile 位于 pom.xml:418-455）。
- jacoco agent 排除口径：`_gen/**`、`_*.java`、`_*.xml`、`*Errors.*`、`*Configs.*`、`*Constants*`、`parse/antlr/**`，与 sonar 口径基本对齐（不完全相等，以 jacoco 侧为准）。
- **基线数据源裁定（rev2）**：`tests/pom.xml` 的 `report-aggregate` 只覆盖其直接依赖（约 135/409 模块，nop-jq、nop-retry、nop-graph、nop-format 大部、nop-dyn、nop-sys-api、nop-db-migration 等均不在内），不能作为全仓基线源。全仓基线以**逐模块 `jacoco:report`**（各模块 `target/site/jacoco/jacoco.xml`，由各模块自己的 `target/jacoco.exec` 生成）为唯一数据源；`tests` 聚合报告退出本 plan 数据面。
- `-Dmaven.test.failure.ignore=true` 无效：root pom surefire 配置显式 `testFailureIgnore=false`（pom.xml:184 附近），POM 显式配置覆盖 -D 用户属性。失败策略改为「-fae + 缺口检测 + 逐模块独立补跑」闭环。
- 2026-10-02 首轮全仓 test 已跑（8:53 wall clock，-T 1C -fae）：4 个模块测试失败（nop-code-web、nop-stream-fraud-example、nop-rule-service、nop-lint-nop），下游 3 个模块 SKIPPED（nop-code-app、nop-lint-maven-plugin、nop-lint-graphql）。失败原因待归因。
- 本 worktree 使用 `ai-dev/tools/mvnq` 作为 Maven 启动器（worktree 内排队 + 独立 `.m2-repo`），`.m2-repo` 已安装全部 409 个模块工件。
- `ai-dev/analysis/2026-10/` 目录已存在；`ai-dev/logs/2026-10/` 目录尚不存在（本 plan 首个日志条目时创建）；`ai-dev/tools/` 现无 coverage/baseline 脚本。
- roadmap 文件数比（2026-09-30）仅为粗筛信号：nop-jq（7 个测试类驱动官方 430 用例）、nop-xlang（数据用例集）会被严重低估，须以 jacoco 实测修正。

## Goals

- 产出可重复执行的基线脚本：`ai-dev/tools/coverage-baseline.sh`（编排清理→全仓 test→缺口补跑→逐模块 report→解析）+ `ai-dev/tools/coverage-baseline.mjs`（解析逐模块 jacoco.xml → 分模块 JSON + markdown 表）。
- 跑通全仓 `test` + 逐模块报告，产出 2026-10-02 基线快照（JSON + md）落 `ai-dev/analysis/2026-10/`。
- 冻结模块→roadmap 分层（内核/引擎/外围）映射表，按预设阈值（内核 ≥55% 行覆盖、业务引擎层 ≥45%、外围 ≥30% 行覆盖）逐模块裁定目标，可按实测推翻并记录理由；修正 nop-jq/nop-xlang 等数据驱动模块的文件数比误判。

## Non-Goals

- 不修改任何产品代码、ORM 模型、生成管线（protected areas 零修改）；**也不修改任何 pom（含 tests/pom.xml）**。
- 不调整 jacoco/sonar 排除口径。
- 不新增任何业务测试（WI1–WI12 的事）。
- 不刷新 roadmap Current Baseline 表格数字（WI13 的事）。

## Scope

### In Scope

- `ai-dev/tools/coverage-baseline.sh` + `ai-dev/tools/coverage-baseline.mjs` 新增。
- 全仓 maven test（含缺口补跑）+ 逐模块 jacoco report 的一次执行。
- `ai-dev/analysis/2026-10/` 下基线报告 + 快照 JSON + 分层映射与目标裁定记录。
- roadmap `## Work Item Status` WI0 checkbox 勾选（独立 closure audit 后）。

### Out Of Scope

- 其余 13 个 WI 的 plan 与测试。
- `docs-for-ai/` 更新（本计划不改约定、不改 API）。

## Execution Plan

### Phase 1 - 基线脚本落位

Status: completed
Targets: `ai-dev/tools/coverage-baseline.sh`、`ai-dev/tools/coverage-baseline.mjs`

- Item Types: `Feature`（No new test required: ai-dev/tools 下开发辅助脚本，与既有 code-stats.mjs 等同性质；本 plan Phase 2 的真实管线执行即其功能验证）

- [x] `coverage-baseline.sh`：编排 `find **/target -name '*.exec' -delete`（新鲜度契约，M1）→ `mvnq -- test -T 1C -fae`（全仓测试）→ 缺口检测（有 src/test/java 但无 exec/报告的模块逐个 `mvnq -- test -pl <module> -fae` 独立补跑，忽略退出码只认 exec 是否产出）→ `mvnq -- org.jacoco:jacoco-maven-plugin:0.8.14:report -T 1C -fae`（逐模块报告）→ `node ai-dev/tools/coverage-baseline.mjs`。
- [x] `coverage-baseline.mjs`：扫描各模块 `target/site/jacoco/jacoco.xml`，按模块汇总 LINE/BRANCH 计数器，输出 markdown 表（含与 roadmap 分层目标比对列）+ JSON 快照（含行覆盖低于阈值的逐类清单，供后续 WI 定位低覆盖类）；支持 `--out <dir>` 指定输出目录、`--skip-maven`（离线重解析，WI13 复用）。
- [x] 脚本落位 `ai-dev/tools/` 且 git diff 仅新增 ai-dev/tools 下文件（本 plan 新增：coverage-baseline.sh、coverage-baseline.mjs、plans/2292 plan 文件；仓库既有 dirty 文件与本 plan 无关）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两个脚本存在于 `ai-dev/tools/` 且 `node ai-dev/tools/coverage-baseline.mjs --help` 可执行。
- [x] 脚本不修改任何产品代码与任何 pom。
- [x] No owner-doc update required（纯工具脚本，不改变平台使用契约）。
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-10 目录新建）。

### Phase 2 - 全仓 test + 逐模块报告执行

Status: completed
Targets: 全仓 reactor、各模块 `target/site/jacoco/jacoco.xml`

- Item Types: `Proof`

- [x] exec 清理后 `mvnq -- -Pcoverage test -T 1C -fae` 全仓跑完；失败与 SKIPPED 模块记录清单。
- [x] 缺口模块逐个独立补跑（43 个缺口模块；显式前置 `jacoco:prepare-agent` 解决 nop-kernel 组无 parent / 组 pom profile 停用问题，见 rev3）。
- [x] `-Pcoverage jacoco:report -T 1C -fae` 产出逐模块报告（198/318 模块有报告，其余为无测试/管线外，见基线报告裁定）。
- [x] 失败测试清单记录到基线报告（4 模块 8 类已归因；TestParallel2PcJdbcE2E 复跑确定性失败，已记 `ai-dev/bugs/2026-10/2026-10-02-stream-2pc-commit-key-single-subtask.md`）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap 点名模块逐一有覆盖数字：有测试的点名模块存在 `target/site/jacoco/jacoco.xml` 且 LINE 总数非零；零测试模块（如 nop-wf-core 80/0、nop-biz-auth-api 28/0、各 *-api）在快照中记 NO-EXEC/0%（这正是缺口信号）；archetype 模板与纯 pom 聚合模块除外。
- [x] 测试失败清单已记录并初步归因。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 基线快照、目标裁定与基线报告

Status: completed
Targets: `ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md`、同目录快照 JSON

- Item Types: `Decision` + `Proof`

- [x] 运行 .mjs 产出分模块 LINE/BRANCH 覆盖快照 JSON + markdown 表（coverage-baseline-2026-10-02.json/.md，198 模块有数字）。
- [x] 冻结模块→分层映射表（内核 = nop-kernel + nop-persistence + nop-core-framework；引擎 = nop-wf/batch/sys/rule/dyn/service-framework；外围 = 其余；映射实现于 coverage-baseline.mjs layerOf，报告「分层汇总」节记录口径）。
- [x] 逐模块裁定目标：采纳预设 kernel ≥55% / engine ≥45% / periphery ≥30%；nop-jq、nop-xlang 按语义基线裁定（理由见基线报告「目标裁定」节）；整体不推翻预设，个别模块调整由对应 WI plan 携带理由申请。
- [x] 基线报告落 `ai-dev/analysis/2026-10/`，含：快照数字、分层映射与目标裁定表、文件数比误判修正、失败测试清单、无报告模块裁定。
- [x] roadmap WI0 checkbox 勾选（独立 closure audit 通过后）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 基线报告存在且含分模块行/分支覆盖数字 + 每模块目标及理由。
- [x] 快照 JSON 存在且重跑 `coverage-baseline.mjs --skip-maven` 输出一致（幂等，已实测 diff 为空）。
- [x] roadmap WI0 checkbox 状态、本 plan 状态、当日 log 三处一致（audit 通过后已同步勾选）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 纯 ai-dev 工具/文档计划：`./mvnw compile`/`./mvnw lint` 不适用（无产品代码变更）；Phase 2 的全仓 test 运行即为本 plan 的核心验证动作。

- [x] 覆盖率基线快照（JSON + md）已产出且数字来自真实 jacoco 逐模块报告（exec 已清理重建，无陈旧数据混入）
- [x] roadmap 点名模块逐一有非零 LINE 数字（无静默缺模块——B1 修复的验收点；零测试模块按 0% 记录，10 个管线外/结构性例外已逐个裁定，见基线报告）
- [x] 分模块目标裁定完成，每条裁定有理由（含推翻预设的记录：仅 2 例语义基线，整体采纳预设）；模块→分层映射表已冻结
- [x] 可重复脚本落位 `ai-dev/tools/`（WI13 可复用；幂等再生已实测）
- [x] 不存在被静默降级的 in-scope 项（失败测试均归因：1 例 bug 流程 / 3 例 flaky 与维护债记录）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：基线数字抽查 3 个模块与其 surefire 测试类数量的合理性对照（非全零/非凭空）——nop-core 66 测试类→36.59%、nop-wf-service 22 测试文件→82.44%、nop-wf-core 0 测试→NO-EXEC/0%，与 roadmap 文件数比方向一致
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md --strict` 退出码 0
- [x] roadmap `## Work Item Status` WI0 checkbox 与 plan/log 一致

## Revision Note

- rev2（2026-10-02）：对抗性审查（独立子 agent，agent_a2776c36）发现 B1（tests 聚合缺 274 模块）、B2（testFailureIgnore 死参数）、M1（exec 陈旧数据）、M2（分层映射缺失）及 Minor 若干。修复：基线源改为逐模块 jacoco:report；失败策略改为 -fae + 缺口补跑闭环；脚本加 exec 清理；Phase 3 增加分层映射冻结；补齐 Phase 2/3 Exit Criteria 引导块与 Closure Gates 的 checklist/Anti-Hollow 门。
- rev3（2026-10-02，实施期实测发现，均已在脚本内修复）：
  1. `-Dmaven.test.failure.ignore=true` 确认无效（root pom surefire 显式 `testFailureIgnore=false`），首轮 reactor 有 4 模块测试失败、3 模块被 SKIP；
  2. **nop-kernel 组 pom 无 `<parent>` 声明**，其全部子模块（nop-core/nop-xlang/nop-commons/nop-api-core/nop-dataset/nop-codegen/nop-javac/nop-jpath/nop-markdown/nop-record-mapping/nop-xlang-* 等）不继承 root pom 的 coverage profile → prepare-agent 不执行 → 无 exec（`-Pcoverage help:active-profiles -pl :nop-core` 实证 "profiles [coverage] do not exist"）；
  3. 组 pom 内 JDK 触发 profile 被激活会停用祖先 pom 的 activeByDefault profile（nop-demo 的 `build-quarkus-modules-on-jdk17-plus` 等，波及 nop-quarkus-demo/nop-spring-demo/nop-spring-gateway 等）；
  4. 修复：缺口补跑显式前置 `jacoco:prepare-agent`（`-Djacoco.propertyName=jacocoArgLine -Djacoco.excludes=<与 root pom 一致>`），已单点验证 nop-core 产出 exec；报告阶段加 `-Pcoverage` 保证排除配置生效（报告实测无 _gen 混入）；
  5. 脚本自身三轮修正：`--out/--label` shift 缺失、内联 node 顶层 return 非法、`--skip-test/--skip-maven` shift 缺失致参数解析死循环。

## Deferred But Adjudicated

（无 — WI0 范围内无延期项）

## Non-Blocking Follow-ups

- `tests/target/site/jacoco-aggregate`（tests 直接依赖闭包视角，跨模块归因）可作为后续补充视角，不属本 plan 验收面。
- 全仓 test 中暴露的 flaky 测试若不影响基线数字有效性，逐条记录在基线报告 follow-up 区，由对应模块 WI 处理。

## Closure

Status Note: 三个 Phase 全部完成。脚本落位（coverage-baseline.sh/.mjs，幂等再生实测 diff 为空）；全仓 test + 逐模块报告产出（198/318 模块有数字，其余为无测试/管线外并逐个裁定）；基线报告与目标裁定落位（采纳预设 + 2 例语义基线）。独立 closure audit 9/9 PASS（APPROVE）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore agent，task_id: agent_f1c65a85-2e7c-491d-8e1f-554622b21fa8，fresh session，非实现会话）
- Evidence:
  - 9 项门控全部 PASS（脚本可执行 / 零产品代码与 pom 修改 / Anti-Hollow 三模块数字与 jacoco.xml 原始 counter 逐位吻合 / 318 模块中 null 者恰为 10 个已裁定例外 + 零测试模块 / 基线报告五要素齐备 / 失败清单与 surefire 实测精确一致 / log 一致 / plan 文本一致性由 checker 实证 / bug 记录完整）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md --strict` 收尾后退出码 0
  - Anti-Hollow：nop-core XML LINE 11003+19069=30072 == JSON；nop-wf-service 756/917 == JSON；nop-wf-core null 与零测试一致
  - scan-hollow-implementations.mjs：N/A（本 plan 无产品代码变更，仅 ai-dev 工具脚本，已由 Phase 2 真实管线执行验证功能）
  - Deferred 项分类检查：无 deferred 项；4 个测试失败模块全部归因（1 例 bugs/ 流程 + 3 例报告记录），无 in-scope live defect 被降级

Follow-up:

- nop-stream TestParallel2PcJdbcE2E 2PC commit-key 嫌疑 → `ai-dev/bugs/2026-10/2026-10-02-stream-2pc-commit-key-single-subtask.md`，修复独立立项
- nop-kernel 组 pom 补 parent / nop-rg argLine 有意替换的覆盖率采集盲区 → build-infra 独立裁定（基线报告 Open Questions）
- 10 个无报告模块的补测确认 → WI12

## Optional Sections

- Risks And Rollback: 全仓 test 耗时约 9-15 min（-T 1C 实测 8:53）；用 mvnq 排队避免与其他 agent 冲突；某一步失败可独立重跑（脚本分步幂等），无回滚需求。
