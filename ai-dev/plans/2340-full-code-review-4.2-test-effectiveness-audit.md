# 2340 full-code-review 4.2 测试有效性审计

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 4.2（MA4）；Skill=`ai-dev/skills/unit-test-antipatterns.md`（P-1~P-8 反模式清单——**维度 21 的核心审计基线，执行前必须完整阅读**）+ `ai-dev/skills/deep-audit-prompts.md`（维度 21 正文 + 共享前缀）
> Related: owner doc `docs-for-ai/02-core-guides/testing.md`；Roadmap Details：「zero-test 模块清单核定（nop-code-api/dao、nop-spring、nop-autotest、nop-benchmark、nop-biz-auth-api 等）；@Disabled 全清单及理由标定；测试反模式抽查」。
> **机械基线预核**（主 agent 实测）：@Disabled 出现于 57 个文件；zero-test 候选实测——nop-code-api 0 test、nop-spring 0、nop-autotest 0、nop-benchmark 0、**nop-biz-auth-api 2 test（非零，需裁定）**；全仓 test 分布 top：nop-ai-agent 459/nop-stream-runtime 283/nop-stream-core 256/nop-metadata-service 166/nop-orm 137。check/nop-code.md 等历史测试类条目为撞车输入。
> 只读审计（P0 走异步注入）。
> **判级口径**（维度 21 补充）：P1=测试无法捕获任何真实 bug（纯 getter/setter、assertNotNull 遍历枚举）；P2=保护力弱但有边际价值；P3=命名/结构问题。命中反模式须引用编号（P-1~P-8）并以「核心逻辑改成错误实现后测试是否仍通过」验证。
> Draft Review: agent_086e467c 首轮 1 Blocker（撞车锚点大半幽灵引用——TestPdfFontResolution 实为 3.6 P3-3.6-012 非 3.9、3.7/3.8 维度12-07 引用证伪、check2/nop-code.md 应为 check/）+ 3 Major（baseline ~450 失实实测 281 src-test/118 zero-test、清单核定 agent 超载拆分预案 A/B 分离、反模式抽样框写死——模块组排除已深审域/major=S-A 级/变异推演 ≥10）+ 4 Minor（点名模块 5→6 定裁/零测试判据写死/@Disabled 立案门槛/closure 四项+P2 台账+owner-doc 行）——本轮修复后 agent_1f8951af 聚焦复核全 PASS（3 处 Minor 计数残留 R1/R2/R3 已顺手修正）——进入执行。

## Purpose

执行 roadmap 工作项 4.2：测试有效性审计（zero-test 清单核定、@Disabled 全清单标定、反模式抽查），产出报告 `4.2-test-effectiveness-audit.md`。

## Current Baseline

- 全仓实测（主 agent）：src/test 目录 **281 个**（pom 模块 ~471）；**有 src/main 且无 src/test/java 的模块 120 个**（118 无 src/test 目录 + 2 仅 test 资源：nop-xdefs/nop-retry-service）——即 zero-test 全量清单真实量级（按 Phase 1 写死判据）。top 消费者 nop-ai-agent 459/nop-stream-runtime 283/nop-stream-core 256/nop-metadata-service 166/nop-orm 137 test 文件。
- @Disabled 57 文件（grep 口径：`grep -rln "@Disabled" --include="*.java" .` 排除 target）。
- zero-test 候选：roadmap 点名 nop-code-api/dao、nop-spring、nop-autotest、nop-benchmark、nop-biz-auth-api——实测 nop-code-api/spring/autotest/benchmark 均 0 test，**biz-auth-api 有 2 个（需核实内容后裁定清单）**；「nop-code-api/dao」的 dao 侧需实测（可能指 nop-code-dao）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；历次深审已立的测试有效性发现撞车对照（**精确锚点，已核实**）：3.5 P2-3.5-007（backoff 测试无保护力）、**3.6 P3-3.6-012（TestPdfFontResolution assumeTrue 门控——原编号 B 面 15-06）**、3.6 P2-3.6-004（removeCell offset≠0 零覆盖）、3.8 P3-3.8-008（SM-06 超时决策无测试锚定）、4.1 P2-4.1-003（ToolSchemaConverter 吞异常降级——错误处理域但测试面相关）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.2-test-effectiveness-audit.md`：摘要 + zero-test 清单 + @Disabled 标定台账 + 反模式抽查台账 + P0–P3 发现（`P<级别>-4.2-<序号>`）+ 驳回台账 + 裁决。
- **zero-test 模块清单核定**（roadmap 点名）：全仓 src/test 为空的 main 模块全量清单（口径：mvn 模块级统计）——逐模块裁定（合理〔api/_codegen/app 装配型〕/缺口需补/移交 R1.0）；roadmap 点名 6 模块逐个裁定（biz-auth-api 2 test 内容核实）。
- **@Disabled 全清单及理由标定**（roadmap 点名）：57 文件逐个标定（禁用理由/是否环境门控 assumeTrue 替代品/是否应恢复）；**立案门槛：@Disabled 行升格为发现须满足「掩盖真缺陷（被禁测试针对的 main 代码已坏）」或「应恢复而长期未恢复且无 successor」——仅记录性条目留在台账不占 finding 编号**；3.6 P3-3.6-012（TestPdfFontResolution 门控）撞车对照。
- **反模式抽查**（维度 21 核心，P-1~P-8 基线）：按模块组抽样（每 major 组 ≥3 个测试类逐方法检查命中反模式编号）+「改成错误实现测试是否仍通过」变异推演抽样；历次深审撞车对照（精确锚点见 Current Baseline 修正行）。
- index 登记 + 独立 closure audit + roadmap 4.2 翻 done。

## Non-Goals

- 全量逐方法反模式审计（抽查制）；nop-stream/nop-ai-agent 等已多轮深审域的全面重审（撞车对照即可）；AutoTest 快照文件本体审计（平台标准模式）；测试基建工具（nop-autotest 框架本体）深审。

## Scope

### In Scope

- 全仓 src/test Java（zero-test 清单与 @Disabled 全清单为全量口径；反模式为抽查口径）
- 只读输入：`ai-dev/skills/unit-test-antipatterns.md`、`docs-for-ai/02-core-guides/testing.md`、历次深审报告（撞车对照）

### Out Of Scope

- 快照文件；测试框架本体；已深审域全面重审；6.x 专项。

## Execution Plan

### Phase 1 - 清单与抽查（双 agent 并行）

Status: completed
Targets: 全仓 src/test

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净按 4.1 口径处理：登记在途变更，触及扫描域则等待）
- [x] 机械基线：zero-test 全量清单（**口径写死：pom 模块有 src/main 且无 src/test/java 目录 = zero-test**；仅 test 资源无 java 亦计入并标注）、@Disabled 57 文件清单、反模式抽样表（按写死抽样框）
- [x] zero-test 清单子 agent A：~120 模块逐模块裁定（**分批预案：按顶层模块组分 3-4 批派发**）+ 点名 6 模块深裁（biz-auth-api 2 test 内容核实）
- [x] @Disabled 标定子 agent B：57 文件逐个标定（理由/环境门控/应否恢复）——与 A 分离避免单会话超载
- [x] 反模式抽查子 agent：完整阅读 unit-test-antipatterns.md 后按**写死抽样框**执行——模块组=roadmap 顶层组排除已多轮深审域（nop-stream/nop-ai-agent/nop-auth/nop-credential/nop-wf/nop-task/nop-batch/nop-report/nop-sys/nop-rule/nop-dyn/nop-metadata/nop-code——撞车对照即可）与非 Java 测试组；major 组=矩阵 §1 S/A 级组；每 major 组 ≥3 测试类逐方法检查；变异推演样本量 ≥10（优先命中 P-1/P-3 的测试）+ 历次深审撞车对照（精确锚点见 baseline）
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A/B 面均第 2 轮收敛）
- [x] 产物 `_tmp/fcr-4.2-{zero,anti}.md`

Exit Criteria:

- [x] 两份初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [x] zero-test 全量清单（120）+ @Disabled 全清单（57）标定落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实（10-05/10-06 跨日）

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 清单口径重放（A 面 SET-MATCH-EXACT + 台账抽 15 全保留 + top 10 全核；B 面升格 5 条全保留且 21-17/21-22 证据加强）
- [x] 主 agent 汇总：判级统一（维度 21 补充判级）、撞车去重终裁（5 锚点零 duplicated）
- [x] 清单收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-4.2-{zero,anti}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `4.2-test-effectiveness-audit.md`；index 登记 4.2 行 + P1 汇总 6 行（无 P2 裁决例外——本项 P2 均 P2 级明确发现直接进 R1.0 输入池）；**No owner-doc update required（只读）**
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项零 P0）
- [x] 定向验证锚点：zero-test 口径重放由 A 面复核独立执行 SET-MATCH-EXACT + closure auditor 独立重放 120 一致；doc-links exit 0
- [x] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——七项实质全 PASS）；通过后 roadmap 4.2 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 清单口径可复现（复核员重放一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [x] 报告落盘且 zero-test 清单/@Disabled 全清单/反模式抽查全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀+维度 21 补充；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 4.2 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 测试有效性审计计划收口。报告 `4.2-test-effectiveness-audit.md` 落盘：通过附 P1×6/P2×9（净 15 条，零驳回零降级零改判）。**zero-test 120 模块（32%）口径重放 SET-MATCH-EXACT**（93 合理/25 缺口/2 移交 R1.0）；@Disabled 57→真禁用 53 + 升格 2 + 误报 2；反模式抽检 28 行仅 2 命中、变异推演 14 个发现侧全「改错仍绿」；**biz-auth-api 2 test 有效约束移出 roadmap 点名清单**。复核加强 13 处透明记载（21-17 证据低估扩充/21-22 双语义并存/top10-02 无 e2e）。closure audit 七项实质全 PASS（处方 2 条机械项已落实：摘要 12→13、10-06.md log 新建）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_4721626e-7742-4bfd-a16c-cb4293448239（fresh session，只读 closure auditor；一次 PASS 附 2 处方）
- Evidence:
  - 计数勾稽：P0=0/P1=6/P2=9/P3=0=15 ↔ 报告实点 ↔ index 4.2 行（0/6/9/0）↔ 映射表 15 行 ↔ index P1 汇总 6 行 ↔ 复核净账（A top10 + B 5）守恒；120 行台账 auditor 独立 grep 计数 93+25+2 吻合
  - P1 台账：6 行在案一致；「P0 发现追踪」无 4.2 新增正确
  - 关键判定亲读 4/4：spring-web-starter 零 src/test + 418 LOC + e2e grep spring 零命中/TestStringHelper 51 @Test 中 5 不触 SUT+1 零断言精确/reconciler 禁用注记逐字+唯一 live 用例常量检查/isWhitespace 6 字符集双向分歧 + 零测试锚定
  - zero-test 口径独立重放 120 一致（[仅test资源] 2 处亲验）
  - 格式：抽 4 条 10 要素完整
  - biz-auth-api 移出清单裁定记载完整（B6 逐类深裁 + 重新评定触发条件）
  - 验证：doc-links exit 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以复核员独立重放 + closure auditor 独立重放为锚
  - Deferred 项分类检查：无 deferred；P1×6+P2×9 经 index 进 R1.0；zero-test 缺口 25 模块补测建议移交 R1.0 排期，无 in-scope live defect 被静默降级

Follow-up:

- no remaining plan-owned work（P1×6+P2×9 修复归属 R1.0 分族；zero-test 缺口 25 模块补测移交 R1.0 排期；@Disabled 台账 51 合理禁用留档；U 备注无）
