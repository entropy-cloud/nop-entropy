# 2312 full-code-review 1.5 xlang 执行后端 + kernel 杂项模块组审计

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.5（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 02/16 + 共享前缀）
> Related: plan 2309（1.2 已审 nop-xlang 本体）；owner doc `ai-dev/design/xlang-java/`（设计 review r5 PASS 为既有覆盖——本项审「已 review 范围之外的部分」）
> Skill 适配注记：本组 9 个模块（xlang-java/truffle/e2e/javac/antlr4/dataset/markdown/record-mapping/kernel-cli）为 kernel 组内执行后端与杂项。维度 02 按模块组口径（9 模块逐一职责纯度/边界；阈值 1500，500–1500 登记并移交 4.3——本组候选 ~6：ExecToTruffleTranslator 1070/ExecutableTreeFingerprints 772/MarkdownSection 922/AstGrammarBuilder 681/RecordMappingTool 679/FrameLayoutMapper 540；record-mapping 两个 867 行为 `_gen/` 生成物豁免）；维度 16 正文通用适配——重点：e2e 模块 main 55/test 5、javac/antlr4 各 test 3、kernel-cli test 2 的测试面与「设计 review r5 PASS 已覆盖架构面」的边界。jq 引擎已迁出 nop-jq（归 1.9）；nop-jpath 桥接面属 1.1/1.3/6.x 既有审计域——本项不含（roadmap 注记）。
> Draft Review: agent_67424940 一次 PASS（4 Minor 一行级修订已随手落实：truffle fail-loud 假设改写、In Scope 前缀统一、r5 必读输入、阈值 1500、Gen__ 豁免注记）

## Purpose

执行 roadmap 工作项 1.5：审计 xlang 执行后端（xlang-java/truffle/e2e/javac/antlr4）与 kernel 杂项（dataset/markdown/record-mapping/kernel-cli），产出报告 `1.5-xlang-backend-misc-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-xlang-java`：main 14 / test 77；`ExecToJavaTranslator.java` **2656 行**（`io/nop/xlang/java/translator/`）。
- `nop-xlang-truffle`：main 121 / test 26；`nop-xlang-java-e2e`：main 55 / test 5。
- `nop-javac`：14/3；`nop-antlr4`：28/3；`nop-dataset`：59/6；`nop-markdown`：25/9；`nop-record-mapping`：24/5；`nop-kernel-cli`：5/2。
- 既有覆盖：xlang 执行后端设计 review 5 轮（r5 PASS，`ai-dev/audits/xlang-execution-optimization/`）——架构设计面已背书；本项聚焦实现面（维度 02 职责边界 + 维度 16 测试有效性）。
- 1.1 已确认组间零出边/零循环；1.2/1.3/1.4 已覆盖 xlang 本体/core/commons/api-core/codegen/xdefs。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.5-xlang-backend-misc-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.5-<序号>`）+ 驳回台账（正文）+ 裁决。
- 维度 02（模块组）：ExecToJavaTranslator 2656 行解剖与裁定（拆分/保留/monitor）；truffle 节点族结构（121 main 的节点组织/与 Executable 语义的对应完备性）；e2e 模块 main 55/test 5 的定位（e2e 资产 vs 产品代码）；javac/antlr4 包装层纯度；dataset/markdown/record-mapping/kernel-cli 四杂项职责与边界。
- 维度 16（测试有效性）：zero-test/低测试模块清单核定（javac 3/antlr4 3/kernel-cli 2/record-mapping 5/dataset 6）；e2e 55 main 的测试口径（E2E 测试是「被测资产」还是「测试代码」——test 5 的合理性裁定）；truffle 26 test 对 121 main 的覆盖抽样；@Disabled 清单；测试反模式抽查。
- index 登记 + 独立 closure audit + roadmap 1.5 翻 done。

## Non-Goals

- nop-xlang 本体（1.2）、nop-core（1.3）、nop-jq（1.9）、nop-jpath 桥接面（1.1/1.3/6.x 域）。
- 设计 review 已背书的架构决策面（r5 PASS）——发现若与设计决策冲突，标注「设计已裁定」而非新缺陷。
- 全仓测试有效性（4.2）——本项只产出本组子集。

## Scope

### In Scope

- `nop-kernel/nop-xlang-java/**`、`nop-kernel/nop-xlang-truffle/**`、`nop-kernel/nop-xlang-java-e2e/**`、`nop-kernel/nop-javac/**`、`nop-kernel/nop-antlr4/**`、`nop-kernel/nop-dataset/**`、`nop-kernel/nop-markdown/**`、`nop-kernel/nop-record-mapping/**`、`nop-kernel/nop-kernel-cli/**`

### Out Of Scope

- 上述之外 kernel 兄弟模块；xlang 执行优化的历史审计目录（只作输入引用）

## Execution Plan

### Phase 1 - 两维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: 9 模块

- Item Types: `Proof`

- [ ] 动手前基线确认：`git status` 干净后开始
- [ ] 机械基线：9 模块 main/test 行数榜、>1500 清单、@Disabled/@Ignore 清单、测试类↔被测类映射表（注明口径）
- [ ] 维度 02 子 agent（必读：`ai-dev/design/xlang-java/01-architecture-baseline.md` + `ai-dev/audits/xlang-execution-optimization/` r5 报告移交清单，先建「已裁定」对照表再判级）：ExecToJavaTranslator 2656 行解剖与裁定；truffle 节点族组织（节点类清单/覆盖的 Executable 类型完备性对照 xlang 的 Executable 族——缺类型=静默降级候选）；e2e 模块定位裁定（main 55 全部为 `Gen__test_xlang_e2e_*`——corpus xpl 编译产物落 main；Gen__* 前缀生成物按内容豁免，裁定对象是位置口径而非文件内容）；javac/antlr4 包装纯度；四杂项模块职责边界
- [ ] 维度 16 子 agent：低测试模块逐一裁定（javac/antlr4 是包装层？kernel-cli 5 类）；e2e 模块测试口径；truffle 覆盖抽样（26 test 覆盖哪些节点族、缺口）；@Disabled 核定；反模式抽查（无断言/sleep 依赖/顺序依赖）
- [ ] 深挖迭代：有发现的维度派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-1.5-dim{02,16}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立维度复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 1.2/4.2 边界协调（测试面发现的最终归属）
- [ ] ExecToJavaTranslator 裁定收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-1.5-dim{02,16}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `1.5-xlang-backend-misc-audit.md`；index 登记 1.5 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 独立子 agent closure audit；通过后 roadmap 1.5 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [ ] 报告落盘且两维度全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 1.5 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- ExecToJavaTranslator 若裁定「拆分」，具体拆分 plan 归 R1.x；测试缺口若成立，补测试归 R1.x。

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
