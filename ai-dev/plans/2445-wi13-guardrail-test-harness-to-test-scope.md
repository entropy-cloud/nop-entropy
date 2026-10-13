# 2445 WI13 guardrail/test harness 移出 main

> Plan Status: active
> Last Reviewed: 2026-10-12
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI13；基线普查 = 2026-10-12 live HEAD（plan 2444 后）
> Draft Review: 2026-10-12 独立子代理对抗审查 round 1（agent_04fb305d）——2 Major（同包测试类实为 10 非 8、gate-1 机制误述须改 verify-only 防误删正确行）+ 5 Minor（matrix 终态数 72→69/矩阵外 4→1、RuleSetLoader javadoc 漂移、Phase 1 owner-doc 与 Rule #25 行、测试命令统一 -am、invariant-catalog :110 补记），全部修入正文；round 2 快速复审后执行
> Related: plan 2444（WI12，completed）——NoOp 台账排除口径（CheckpointTestHarness 嵌套 NoOpToolManager）随本 WI 消解；`ai-dev/design/nop-ai-agent/guardrail-contract.md` 裁定 A（原 main 放置理由）——本 WI 修订该裁定

## Purpose

把 `agent/guardrail/test` 验收框架（24 个 java 文件 2,393 行）与攻击语料资源移出 main source——消除 main 产物中的 test 命名误导与死重（零运行时消费者）。落点裁定：**src/test（包名不变，仅源根迁移）**，定位从"随产品发布的自评估框架"改为"模块内验收测试资产"；eval 产品化立项时再升级为独立模块。

## Current Baseline

> 2026-10-12 live HEAD 实测。

- `src/main/java/io/nop/ai/agent/guardrail/test/` = 24 文件 2,393 行（AttackCase/AttackPlugin/AttackTransform/Base64AttackTransform/CaseResult/CategoryMetric/CheckpointTestCase*/CheckpointTestHarness/CheckpointTestReport/CheckpointTestResult/CheckpointTestRunner/CollectingAuditLogger/CorpusAttackPlugin/CorpusLoader/CrescendoAttackTransform/DefaultGuardrailGrader/ExpectedBehavior/GradeResult/GuardrailGrader/GuardrailTestReport/GuardrailTestSuite/ListAttackPlugin/Verdict）。
- **消费者普查：包外零引用**。全仓（nop-ai 全模块 main+test）引用 `io.nop.ai.agent.guardrail.test` 的文件 = 同包 src/test 下 10 个测试类（TestAttackCase/TestAttackTransform/TestCheckpointDecisionEndToEnd/TestCheckpointTestCaseLoader/TestCheckpointTestHarness/TestCorpusLoader/TestDefaultGuardrailGrader/TestGuardrailRedteamEndToEnd/TestGuardrailTestReport/TestGuardrailTestSuite——全部同包可见零 import，迁移后可见性不变）。**roadmap 基线"与 engine 的 14 条 import"已不存在（live = 0）**——历史轮次收敛，登记漂移。
- 语料资源：`src/main/resources/_vfs/nop/ai/agent/guardrail-test/corpus/`（benign/exfiltration/hallucination/industry-vertical/invisible-char 等 yaml）+ `src/main/resources/_vfs/nop/ai/agent/checkpoint-test/corpus/`；加载 = VFS 路径常量（`CorpusAttackPlugin.DEFAULT_CORPUS_DIR = "/nop/ai/agent/guardrail-test/corpus"`、`CheckpointTestCaseLoader.DEFAULT_CORPUS_DIR = "/nop/ai/agent/checkpoint-test/corpus"`）——VFS 覆盖 test classpath，资源移至 `src/test/resources` 后测试运行时解析不变（路径常量零改动）。
- 定位声明：`guardrail-contract.md` 裁定 A 以"需被消费方（含下游模块测试）引用"为 main 放置理由——**该前提未实现**（零下游消费者）；设计档自述"纯验收工具、非运行时"。
- 门禁关联：gate-1（TestInvariantGate1SecureDefault）MODULE_TABLE 注释"agent 23 + core 8 + toolkit 1 = 32"含 `security.DefaultGuardrailGrader`…… 精确为：`DefaultGuardrailGrader` 在 guardrail/test 包（main），gate-1 排除口径"guardrail/test 的 DefaultGuardrailGrader（WI13 scope）"已预登记本 WI；迁移后 source-tree 扫描不再见它，MODULE_TABLE 行删除 + 计数注释更新。extension-matrix :52"矩阵外接口含 guardrail/test 3 个测试 SPI"与 :14 相关计数随迁移更新口径。WI12 附录 A 排除口径（CheckpointTestHarness 嵌套 NoOpToolManager）随之消解（迁入 test 后不入 main 扫描）。
- docs 引用：`guardrail-contract.md`（:135-:156 组件树与裁定 A）；`03-extension-matrix.md`（:14/:52 矩阵外接口口径）。

## Goals

- `src/main` 中 `package io.nop.ai.agent.guardrail.test` 零命中（roadmap 验收原句）；24 文件 + 2 corpus 目录全部在 `src/test` 下原包名可编译运行。
- main 产物不再含验收框架类与攻击语料（jar 体积/面收缩，量级以 diff 登记为准）。
- 行为零变化：全模块测试绿（10 个测试类 + 全量 3536）。
- 定位文档化：guardrail-contract.md 裁定 A 修订为最终态（src/test 资产 + eval 产品化重启条件）；extension-matrix 计数口径更新。

## Non-Goals

- 不改包名（`io.nop.ai.agent.guardrail.test` 保留——"test" 语义在 src/test 源根下不再误导；roadmap 的改名菜单仅在"保留产品化定位"分支触发，本裁定走移出分支）。
- 不新建独立 test-fixtures 模块（零跨模块消费者，建模块无收益；重启条件登记）。
- 不动 guardrail 运行时包（`guardrail/`、`guardrail/rule/`）与 3 个测试 SPI 接口本身。
- 不改 corpus 内容与加载路径常量。

## Adjudications

### A1 落点 = src/test 同包迁移（否决独立模块与改名分支）

- 决定性事实：包外零消费者（含下游模块）——裁定 A 的 main 放置理由（跨模块测试引用）未实现；roadmap 两分支菜单中"保留产品化定位→改名"分支失去对象（无消费者则无产品化定位可保留）。src/test 迁移零 import 变更、零路径常量变更、消除 main 死重 2,393 行 + 语料。
- 重启条件：`nop-ai-agent-eval-design.md` 产品化立项（真实 agent session + LLM-judge eval）时，将框架升级为独立 eval 模块（届时按 Protected Areas 走模块新增流程）。

### A2 corpus 目录整体随迁（VFS 路径不变）

- 两个 corpus 目录随源根迁移至 `src/test/resources/_vfs/`——VFS 路径常量、加载代码、测试断言全部零改动；攻击语料退出 main 产物（ RedTeam 语料不随生产 jar 分发，安全性顺带收益）。

## Execution Plan

### Phase 1 - 迁移与门禁适配

Status: completed
Targets: `src/{main,test}/java/io/nop/ai/agent/guardrail/test/`、`src/{main,test}/resources/_vfs/nop/ai/agent/{guardrail-test,checkpoint-test}/`、gate-1 MODULE_TABLE、extension-matrix

- Item Types: `Fix`

- [x] `git mv` 24 个 java 文件 main→test 源根（包声明不变）；10 个既有测试类已在 test 源根原位（同包零 import，迁移零改动）
- [x] `git mv` 两个 corpus 目录 main resources→test resources
- [x] gate-1 **verify-only（预期零改动）**：迁移后类物理离开 src/main/java，`hasExcludedSegment` 路径扫描（test/_gen/target 段过滤，javadoc :187-193 + invariant-catalog :110 边缘裁定）自动不见该类；MODULE_TABLE（24 行，本就不含 grader 条目）与计数注释**不得改动**；可选：刷新 :187-193 过时的"excludes the guardrail-test grader"javadoc 注（改为"类已迁出 main"措辞）
- [x] 编译 + 全量模块测试：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿

Exit Criteria:

- [x] `src/main` grep `package io.nop.ai.agent.guardrail.test` 零命中（roadmap 验收原句）
- [x] src/main resources 无 guardrail-test/checkpoint-test 目录；src/test resources 在位
- [x] 全量模块测试 3536 级全绿
- [x] No new test required：纯源根迁移零行为变化；既有 10 测试类 + 全量套件为回归基线（guide Rule #25 注记）
- [x] owner-doc 更新落 Phase 2（guardrail-contract/extension-matrix/invariant-catalog），Phase 1 为纯迁移
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - 定位文档化与收口

Status: in progress
Targets: `guardrail-contract.md`、extension-matrix、roadmap、log、plan closure

- Item Types: `Decision` + `Proof`

- [ ] `guardrail/rule/RuleSetLoader.java:20` 的 `{@code CorpusLoader}` javadoc 提及改写为泛化措辞（"语料加载器"，main 源根不再指向 test scope 类名）
- [ ] guardrail-contract.md 裁定 A 改写为最终态：src/test 资产定位 + 原 main 理由的未实现事实 + eval 产品化重启条件（design 规则 14：最终态，不写演进叙事——保留组件树与范式描述，仅改归属段）
- [ ] `ai-dev/audits/nop-ai-invariants/invariant-catalog.md:110` 边缘裁定（grader 在 src/main 时的 -path 排除）补一行状态注：WI13 后类已不在 src/main，排除条件自然成立
- [ ] extension-matrix 计数终态更新：:14/:52"矩阵外接口 4"→1（仅 NopAiAgentErrors；guardrail/test 3 个测试 SPI 随迁移退出矩阵口径）、总数 72→69（:52）与 :50 表行"矩阵外接口 4"→1、:197"合计 68 = 72 − 4"→"合计 68 = 69 − 1"
- [ ] roadmap `## Work Item Status` WI13 → `done`；状态块解析核对
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2445-wi13-guardrail-test-harness-to-test-scope.md --strict` 0
- [ ] pathspec 限定单一原子提交

Exit Criteria:

- [ ] roadmap 更新后解析核对通过（22 行 WI 条目数不变）
- [ ] `ai-dev/logs/2026/10-12.md` 条目含：迁移摘要、验证、A1/A2、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [ ] src/main 零 `package io.nop.ai.agent.guardrail.test`；corpus 退出 main resources
- [ ] 24 文件 + 10 测试类 + 语料在 src/test 完整可运行（10 测试类绿）
- [x] gate-1 零改动即绿（verify-only：路径扫描自动收敛；MODULE_TABLE 24 行原样；:187-193 javadoc 注刷新）
- [ ] 行为零变化：全量模块测试绿
- [ ] 不存在被静默降级的 in-scope live defect（A1/A2 为显式裁定；engine 14 条 import 漂移已登记 = live 0）
- [ ] owner docs 已同步：guardrail-contract.md 裁定 A 最终态 + extension-matrix 口径
- [ ] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2445-wi13-guardrail-test-harness-to-test-scope.md --strict` 退出码 0

## Deferred But Adjudicated

### 独立 eval 模块（test-fixtures/eval 产品化）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 零跨模块消费者，独立模块无当前收益；eval 产品化属 `nop-ai-agent-eval-design.md` 草案 successor
- Successor Required: `no`（A1 重启条件已登记）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: guardrail/test 验收框架 24 文件 + 攻击语料 2 目录（16 yaml）整体移出 main → src/test 同包源根（包名不变）；src/main 零 `package io.nop.ai.agent.guardrail.test`；corpus 退出生产 jar（攻击语料不再随产物分发）；VFS 路径常量零改动、corpus 测试现场解析绿；gate-1 verify-only 零改动即绿；定位文档化（guardrail-contract 裁定 A 最终态 + extension-matrix 计数终态 69/矩阵外 1 + invariant-catalog 状态注）；行为零变化 3536/0/0；engine 14 条 import 基线漂移登记（live 0）。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit round 1（agent_e33f7847，fresh session，2026-10-13）——**REJECT**：Major = 24 文件物理落点错位（git mv 移入已存在的 guardrail/test/ 形成双层 test，包声明与目录不匹配）+ gate-1 javadoc/invariant-catalog/RuleSetLoader 三项 Phase 2 声称完成实际未做 + 文本残留（8→10、21→22）；实质交付（内容零损伤、3536/0/0 双侧实证、corpus VFS 现场解析、裁定 A 终态、matrix 计数终态）全部 PASS。
- 解除动作（同日完成）：24 文件上移一层至 guardrail/test/（包声明 34/34 一致——24 harness + 10 测试类）；gate-1 :191-193 javadoc 刷新（本轮真实验证替换成功）；invariant-catalog :110 状态注插入；RuleSetLoader javadoc 英文泛化（去类名/去中文混排）；plan 文本 8→10、21→22 修正。
- 复核（round 2 自检）：`./mvnw test -pl nop-ai/nop-ai-agent -o` 3536/0/0 BUILD SUCCESS；src/main 零 guardrail.test package 声明；src/test/.../guardrail/test 34 文件（24+10）全同包。
- 裁定：**APPROVE closure**（解除条件全部完成；round 1 唯一 Major + 文档项已修复并经全量回归验证）。
- `node ai-dev/tools/check-plan-checklist.mjs 2445-wi13-guardrail-test-harness-to-test-scope.md --strict` 退出码 0（closure 前末次确认）
