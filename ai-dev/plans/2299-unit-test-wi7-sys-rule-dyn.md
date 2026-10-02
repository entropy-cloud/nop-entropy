# 2299 unit-test-coverage-roadmap WI7 — nop-sys / nop-rule / nop-dyn 补强

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI7 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

可复用业务模块补强：nop-sys（sys-api 57 main/0 test 序列号/数据字典/分布式锁语义、sys-dao 67.99% 补强）、nop-rule（rule-core 71.19% 已达标、rule-api 21 main/0、rule-dao 17 main/0）、nop-dyn（dyn-api 45 main/0、dyn-dao 29.58% 动态表单校验）。

## Current Baseline

- nop-sys-api：NO-EXEC（无 src/test、无 junit 依赖）；nop-sys-dao 67.99% / 1331L（靶点 SysDictLoader 0%、OrmEntityChangeLogInterceptor 8.97%）。
- nop-rule-core 71.19%（靶点 RuleServiceImpl 64L/0%）；nop-rule-api / nop-rule-dao：NO-EXEC（无 junit 依赖）。
- nop-dyn-api：NO-EXEC（无 junit 依赖）；nop-dyn-dao 29.58% / 693L（靶点 DynEntityMetaToOrmModel 347L/16.43%、NopDynFunctionMeta 0%）。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi7-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。
- WI0 失败遗留：nop-rule-service 的 TestNopRuleDefinitionBizModel 3 errors（FILE_HASH 快照非确定性字段）——本 WI 顺带修复该快照测试（testing.md「快照不匹配诊断」：ORM 字段补 tagSet 或重录 output；如需改 orm 模型 tagSet 属测试资源修正，记录后执行）。

## Goals

- nop-sys-api：**结构性用例** ≥8 个（实测该模块 57 文件全部为 `*Api` 接口 + `*InputBean/*OutputBean` 数据类，零实现逻辑——序列号/字典/锁**实现语义**在 sys-dao/sys-service，归入下一条）。
- nop-sys-dao：字典/序列号/锁实现语义 ≥6 用例（SysDictLoader、OrmEntityChangeLogInterceptor 等靶点）。
- nop-rule-api + nop-rule-dao：结构性用例 ≥6 个。
- nop-dyn-api：结构性用例 ≥4 个；nop-dyn-dao：DynEntityMetaToOrmModel 转换语义 ≥4 用例。
- nop-rule-service 快照测试修复并转绿（修复主路径见下）。
- 各模块测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；nop-rule-core（71.19% 已达标）移出本 WI 增量范围——显式裁定：roadmap WI7 条目中 rule-core "决策树/决策矩阵执行语义" 以达标确认替代增量补强。

## Scope

### In Scope

- `nop-sys|nop-rule|nop-dyn/*/src/test/**` 新增测试与资源。
- **pom 裁定**：sys-api/rule-api/rule-dao/dyn-api 四模块 pom 实测均无 junit 依赖，各新增 `junit-jupiter` test-scope 并逐项记录。
- nop-rule-service 既有快照测试修复。

## Current Baseline 补充（FILE_HASH 修复主路径，审查确认）

- 根因已查实：nop-file 的 G9-04-01 特性在 DaoResourceFileStore 上传时以 DigestInputStream 计算 SHA-256 写入 FILE_HASH（确定性字段），rule-service 的 `_cases` 快照录制于该特性之前 → "期望空实际有值"。**字段是确定性的，快照是过期的**（修正 WI0 报告的"非确定性字段"初判）。
- **修复主路径**：testing.md「重新录制 output」——保留 input 快照，删该用例 output，`@EnableSnapshot(saveOutput = true)` 单方法重录，切回 CHECKING 验证绿。不改 orm tagSet（字段应被 pin 住，var 掩蔽会永久弱化快照）。
- 仅当重录后仍不稳定时，才按 testing.md ORM 变更流程评估 tagSet——该变更落在 nop-file-dao 产品模型，属跨模块裁定，须先记录再执行。

### Out Of Scope

- 产品代码；nop-rule-core 增量（已达标）。

## Execution Plan

### Phase 1 - 增量测试编写与快照修复

Status: completed
Targets: `nop-sys|nop-rule|nop-dyn/*/src/test/**`、nop-rule-service 快照

- Item Types: `Fix`

- [x] sys-api 结构性 11 用例（crud Api 契约 4 + bean 契约 7）；sys-dao 实现语义 18 用例（SysDictLoader 4 JunitBaseTestCase+localDb、OrmEntityChangeLogInterceptor 6、EntityResourceLockState 2 等共 12+）。
- [x] rule-api 10 + rule-dao 5 用例；dyn-api 8 用例；dyn-dao 5 用例（DynEntityMetaToOrmModel 转换语义）。
- [x] TestNopRuleDefinitionBizModel 快照修复转绿：确认根因（G9-04-01 SHA-256 FILE_HASH）→ 删 3 用例 output 重录（force-save-output）→ CHECKING 4/4 绿；_cases 变更 14 文件全部 output 侧，input 未动；CRLF 噪声已还原。
- [x] 各模块测试全绿（sys-api 11、sys-dao 64、rule-api 10、rule-dao 5、dyn-api 8、dyn-dao 19、rule-service 5；-am 链因上游非本 plan 模块红改用 -pl 单模块验证，偏差 1）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 11 测试文件 / 51 用例（≥25），全部含显式语义断言；快照测试 CHECKING 模式 4/4 绿。（勘误：原记"8 文件"与 git 实际不符；且 TestOrmEntityChangeLogInterceptor.java 曾被 .gitignore `**/log/` 规则误忽略未入库，audit REJECT 后已加精确负向例外修复入库，见偏差 4）
- [x] 各模块测试全绿。
- [x] pom 变更仅 4 处 test-scope junit（sys-api/rule-api/rule-dao/dyn-api，按 plan 裁定）；orm tagSet 零修改。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI7 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删模块 exec → baseline 脚本复测（label wi7-2026-10-02）：sys-api 0→7.48%、sys-dao 67.99%→75.21%（+7.22）、rule-api 0→5.70%、rule-dao 0%（结构性测试断言元数据而非行，如实记录）、rule-service 44.19% 持平、dyn-api 0→4.01%、dyn-dao 29.58%→48.20%（+18.62）、rule-core 71.19% 持平（已裁定移出）。
- [x] 残余缺口显式裁定（Deferred 段：api 族结构性模块行覆盖天然低，语义面由 sys-dao/dyn-dao 承载）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI7 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI7 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 全部新增测试绿（含既有测试零回归；nop-rule-service 快照修复后 4/4 绿）
- [x] 产品代码零修改；pom 仅 4 处 test-scope 新增且记录
- [x] 覆盖增量实测记录
- [x] 残余缺口显式裁定（无静默降级）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：序列号/锁语义测试断言行为而非仅实例化（audit 抽查：TestOrmEntityChangeLogInterceptor 审计列语义 6 断言、TestDynEntityMetaToOrmModelTransform 转换产物断言）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2299-unit-test-wi7-sys-rule-dyn.md --strict` 退出码 0
- [x] roadmap WI7 checkbox 与 plan/log 一致

## 执行偏差记录

1. -am 链在上游非本 plan 模块（nop-stream-connector-debezium）红测试处中断，按预案改用 `-pl :<module> -fae` 单模块验证（接受 .m2-repo 工件）。
2. .m2-repo 的 nop-file-dao jar 过期（9/20，早于 G9-04-01）导致首次快照测试假绿；为复现缺陷执行 `mvnq -- install -pl :nop-file-dao -am -DskipTests -fae`（跳测试安装，零代码改动）后重现 3 errors 再重录——测量与修复有效性的必要前提。
3. 快照重录波及记录：response2/response4-exec.json5 新增 `context:null` 日志字段（录制以来产品侧新增字段的固化）；nop_rule_node.csv 行序变化（业务等价，不变量由 testImportDuplicatePredicate 独立锚定）。_cases 口径：14 为删+重录毛变更，git 净变更 8 文件、全部 output 侧。
4. （audit REJECT 必修项）TestOrmEntityChangeLogInterceptor.java 被 .gitignore `**/log/` 规则（原义为忽略运行期日志目录）误忽略而未入库；已在 .gitignore 增加精确负向例外 `!**/src/*/java/**/log/` + `!**/src/*/java/**/log/**` 后入库。教训：包目录名恰为 log 的测试源码会被该规则吞掉。

## Deferred But Adjudicated

### api 族模块行覆盖低位（sys-api 7.48% / rule-api 5.70% / dyn-api 4.01% / rule-dao 0%）

- Classification: `watch-only residual`（WI13 复裁）
- Why Not Blocking Closure: 四模块为接口/bean 声明性模块（sys-api 57 文件全为 Api+Input/OutputBean），本 WI 以结构性契约测试建立从 0 到有的测试面（@BizModel 名/泛型绑定/bean 契约/错误码），行覆盖天然低——语义实现面在 sys-dao（+7.22）与 dyn-dao（+18.62）已显著覆盖。api 族行覆盖目标的合理性归 WI13 复裁。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 4 项产品缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi7-defect-suspects.md）：detached 实体 ref 访问抛错、SysDictLoader 忽略 locale、existsDict 租户旁路、ChangeLogInterceptor null 值审计区分。

## Non-Blocking Follow-ups

（执行结束时填写）

## Closure

Status Note: 11 测试文件/51 用例全绿（七模块），sys-dao 75.21% 与 XML counter 逐位一致，快照修复 4/4 绿（WI0 失败项清零），缺陷嫌疑源码实锤（SysDictLoader.loadDict locale 参数零引用）。首轮 audit REJECT（.gitignore **/log/ 误吞测试包目录）→ 精确负向例外整改 → 复核 APPROVE（4/4 VERIFIED，副作用零噪声）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_283c9299-bab3-4d12-95c7-2c00ca823fce，fresh session；首轮 REJECT 一项必修 + 复核 APPROVE）

## Optional Sections

- Risks And Rollback: 纯测试增量 + 快照修复可独立回滚；orm tagSet 变更按 testing.md「ORM 模型变更 + 重新录制快照一起提交」执行。
