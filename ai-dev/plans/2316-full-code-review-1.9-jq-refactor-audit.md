# 2316 full-code-review 1.9 nop-jq + nop-refactor 首审

> Plan Status: draft
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.9（MA1，v6 新增）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/05/13 + 共享前缀）
> Related: owner doc `docs-for-ai/01-repo-map/module-groups.md`（jq 行）+ `docs-for-ai/03-modules/nop-refactor.md`；jq 设计权威 `ai-dev/design/`（jq 01/02，仓内路由）
> Skill 适配注记：两模块均小（jq 71/7 扁平、refactor 33/16 三子模块 core/java/graphql）。维度 01 结构边界；维度 05 对 jq 适配为**求值语义与官方套件基线一致性**（无业务 codegen 链），对 refactor 适配为**check/plan/apply/verify 四段契约连通性**；维度 13 对 jq 适配为错误路径与部分求值行为（无鉴权面），对 refactor 聚焦 **apply 写面信任边界**。roadmap Details 所称「EditPlanApplier」为 nop-lint-core 实存类（`io.nop.lint.core.fix.EditPlanApplier`，apply/verify 经 nop-lint fix 机制单点应用），是四段契约第 3 段的字面写盘入口——`RefactorOperationRunner.java:61` 持 nop-refactor src/main 唯一调用点（nop-lint-core 自身 FixApplier 另有调用点，归 4.6）。本项审**接缝**（调用点契约 + 入口写前校验 toAbsolutePath/normalize/temp-file 语义）；EditPlanApplier 内部管线归 4.6。
> Draft Review: agent_dfcd9f7b 首轮（3 Major + 7 Minor）；agent_69b581fe 复审 FAIL 指出 6 处残留（M2/m3/m5/m6/m7 及限定语）——本轮全部修复

## Purpose

执行 roadmap 工作项 1.9 首审：nop-jq（求值语义对官方 jq 1.7.1 套件基线一致性、错误路径、部分求值行为）+ nop-refactor（四段契约连通性、RefactorRuleGates 单一实现与 cap 两键前置门、apply 写面信任边界），产出报告 `1.9-jq-refactor-first-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- nop-jq：71/7，扁平单模块（parent=nop-entropy，2026-09-28 自 nop-kernel 迁出）；包结构 io/nop/jq/（IJsonQuery/JqDirectQuery/JqEngine/JqLexer/JqParser/JqToken* + ast/ + runtime/）；测试基线 TestJqOfficial（官方 jq 1.7.1 套件 430/430，jq roadmap 记录 447 块）。jq roadmap items 1–10 已 done（2026-10-03 账本对账回填）。
- nop-refactor：33/16，三子模块 core（operation 四段契约：RefactorOperation/Runner/RenameOperation/RewriteOperation + RefactorRuleGates/RefactorVerifier/RefactorResult/Verification + cli/NopRefactorCli）/java（1 文件 JavaSymbolResolverAdapter）/graphql（NopRefactorBizModel + Rename/RewriteInput）；测试 16（TestRefactorVerifier 等）。refactor roadmap 13 WI 已于 2026-09-26 终态收口。
- 消费面：nop-ai-toolkit 的 jq 工具（JqToolExecutor）消费 nop-jq；nop-refactor 消费 nop-lint transform 规则与 fix 机制、nop-java-parser。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS——**早于两模块现形态**（jq 2026-09-28 迁出、refactor 后入库），不作为两模块的绿色证据；Phase 3 收口前补一次 `./mvnw test -pl nop-jq,nop-refactor -am` 定向运行作锚点（只读审计的验证性运行，不改代码）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.9-jq-refactor-first-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.9-<序号>`）+ 驳回台账（正文）+ 裁决。
- jq 面：结构边界（扁平单模块内 ast/runtime 分层）；求值语义对官方套件基线的覆盖一致性（TestJqOfficial 覆盖范围与跳过面）；错误路径（解析错误/运行时错误 fail 行为）；部分求值行为（生成器/惰性序列语义）。
- refactor 面：check/plan/apply/verify 四段契约连通性（入口 RefactorOperation→Runner→FileEdit→RefactorVerifier→RefactorResult 全链 file:line）；RefactorRuleGates 单一实现验证（是否唯一实现类）与 cap 两键前置门；apply 写面信任边界（写盘路径校验/目标白名单——信任边界定义：谁能触发对哪些路径的写）；NopRefactorCli 与 GraphQL 面的输入一致性。
- P2 走显式裁决通道（异常项上报 R1.0，见 roadmap 规则 #3/6.9）、P3 watch 归档，与 index 行计数一致。
- index 登记 + 独立 closure audit + roadmap 1.9 翻 done。

## Non-Goals

- nop-jpath 的 jq 子集桥接面（1.1/1.3/6.x 域）；nop-lint 内部（4.6 增量首审）。
- jq 语法完整性与官方套件的增量扩展（功能开发归 jq roadmap）。
- refactor 的 rename 语义正确性大规模验证（其 roadmap 已收口；本项审契约与信任边界，抽查不穷举）。

## Scope

### In Scope

- `nop-jq/**`、`nop-refactor/**`

### Out Of Scope

- 消费方模块内部（nop-ai-toolkit/nop-lint 只看接缝）

## Execution Plan

### Phase 1 - 两模块深挖（deep-audit 迭代轮次，子 agent 并行）

Status: completed
Targets: nop-jq + nop-refactor

- Item Types: `Proof`

- [x] 动手前基线确认：`git status` 干净后开始
- [x] 机械基线：两模块行数榜、公开 API 清单（public 类/方法）、测试方法计数（jq 7 个测试文件的方法数、refactor 16 个）
- [x] jq 子 agent：结构边界（ast/runtime 分层单向性）；TestJqOfficial 覆盖面分析（@TestFactory 动态挂载；跳过面=JqOfficialCase.parse 的 isModuleDependent + %%FAIL IGNORE——447 块到 430 的差值；宽松点=数值宽松比较/NaN≡null/运行时错误仅比对期望前缀/%%FAIL 接受 OOM·SO）；错误路径行为（JqParser 解析失败、JqEngine 运行时错误的异常类型与信息质量）；部分求值行为（生成器/懒序列/短路语义抽查）；消费接缝（JqToolExecutor 输入信任）
- [x] refactor 子 agent：四段契约连通性全链追踪（check→plan→apply→verify 各段入口/出口 file:line；断链候选）；RefactorRuleGates 唯一实现验证与 cap 两键前置门（键名/默认值/生效路径）；apply 写面信任边界（写盘路径来源校验、路径逃逸面、GraphQL 面输入→FileEdit 的信任传递）；NopRefactorCli 参数与 GraphQL Input 的一致性
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-1.9-{jq,refactor}.md`

Exit Criteria:

- [x] 两份初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 每模块派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [x] 统一 severity 判级；与 4.6（nop-lint）边界协调
- [x] 契约连通性结论收敛（jq 语义基线结论 + refactor 四段结论各一条总评）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-1.9-{jq,refactor}-review.md`；保留发现判级理由 + 证据可复现
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `1.9-jq-refactor-first-audit.md`；index 登记 1.9 行（P0–P3 计数；P1 进汇总表）
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [x] 定向验证锚点：`./mvnw test -pl nop-jq,nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-graphql -am`（TestJqOfficial 430/430 为预期基线）
- [x] 独立子 agent closure audit；通过后 roadmap 1.9 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；2026-09-22 基线早于两模块现形态不作绿色证据——以 Phase 3 的定向 test 运行为验证锚点。

- [x] 报告落盘且两模块面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应（P1 行同步进汇总表）
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 1.9 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定）

## Closure

Status Note: 1.9 两模块面全部完成（P0=0/P1=1/P2=9/P3=21，驳回 0/降级 0）；closure audit 两轮（第一轮 FAIL 纯收口机械件——index 缺行/定向测试未跑；修复后第二轮 PASS）；P1-1.9-001 进 R1.0；定向测试 jq 560+refactor 103 全绿；未改产品代码。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: agent_94909b80-a084-4f73-adf9-0d9bdc5de359（独立 fresh-session closure auditor，只读，两轮）
- Evidence:
  - 第一轮 FAIL→修复：index 缺 1.9 行（v6 时 index 表未扩展，一并补 1.10 占位）、refactor 定向测试未跑、3 Minor（P1 表错引/叙事计数/refactor-19 别名）
  - 第二轮 PASS：index 1.9 行 0/1/9/21 精确对应；surefire 复算 jq 560/0、refactor 37/29/37 全绿；log 表述收敛；无回归确认
  - severity 标定：P1-1.9-001 维持 P1 独立裁定成立（关键校勘：真实默认链 Default* 三件套两信任分支均自动批准，结论更强）；拒升 P0 正确（无数据损坏）；与 1.8-01-02（P1）/1.7-refactor-01（P2）判级谱系一致
  - 31 条发现抽验 8 组 file:line 零虚构；正结论 3/3 独立复现
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs` 退出码 0
  - Anti-Hollow（审计版）：430/430 与四段全通均有可复现实证

Follow-up:

- no remaining plan-owned work（P1-1.9-001 修复精算在案归 R1.0；jq-02/jq-12 语义归属 R1.0 对账裁定移交 jq roadmap；EditPlanApplier 内部管线归 4.6；CLI 三项不对称归 4.5）
