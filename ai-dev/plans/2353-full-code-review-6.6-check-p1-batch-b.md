# 2353 full-code-review 6.6 check 系列 P1 抽样标定 b（service-framework/业务样板/可复用业务·除 metadata）

> Plan Status: draft
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.6（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: 批次权威定义=矩阵 §6：批次 b 成员 = **nop-service-framework、业务样板（auth/job/task/wf）、可复用业务（sys/report/rule/batch/dyn/file/retry/tcc）**（metadata 唯一归属批次 c）。同族先例=plan 2352（6.5 批次 a，Draft Review 缺陷类型 B1 漏单元/M1 类别规则/M2 撞车面/M3 partially-fixed/M4 冻结协议——全部已修复形态照抄）。
> 机械基线（主 agent 实测，2026-10-07；经 Draft Review agent_aea409af 逐份实测勘正）：批次 b 候选单元 **13 份**＝service-framework 族 3（nop-biz P1=2 / nop-graphql P1=3 / gateway-bizauth P1=5）+ 业务样板 4（nop-auth P1=1 / nop-job P1=3 / nop-task P1=5 / nop-wf P1=5）+ 可复用业务 6（nop-sys P1=4 / nop-report P1=6 / nop-rule P1=1 / nop-batch P1=5 / nop-dyn P1=3 / file-retry-tcc P1=3）。**批次 b P1 分母 = 46 条**（10+14+22；SUMMARY Phase 2 的 18 含 sys 4 条归本批、Phase 3 的 42 扣除 metadata 1/format×4 20/nop-excel 3〔批次 d〕后本批得 18，18+4=22；≥50% 下限 = 23）。**处置面先验（草审 B2 实测翻转勘正——SUMMARY:101 过时行勿引）**：**34/46 条已带 fix-ai-check 处置块**（2026-08-22/23/24，多数指名回归测试，如 nop-biz :41→TestCacheActionDecorator、nop-graphql :49→TestBeanMethodBatchFetcher、nop-sys :60→TestSysSequenceGenerator、nop-batch :85→TestAsyncFetchLoaderFailure）；**12/46 无标注**（gateway-bizauth 5/nop-report 6/nop-rule 1）——AF 先验高；实跑对象=处置标注所指测试（无标注条目实跑域内/模块测试）。**轮内已终判承接（草审 M2）**：gateway-bizauth 5 条已被 3.9 §2.2 全标定（:49/:86 still-live 已注册 P1-check-gw-49/86、:70 duplicated→check2 :66、:109 AF、:131 duplicated→check2 :110）、nop-auth 1 条已被 3.10 全标定（:51 AF + TestAuthPermissionCacheAndAdminGuard）、nop-task 5 条已被 3.2 撞车盘点（P1×4 AF + 1 条 plan 349 落地）——**共 11 条直接承接轮内终判（dup/AF 保留 check 溯源），不重复抽样，仍计入对账台账**；净抽样池 = 46−11 = 35 条。
> 上游标定富集面（撞车承载，草审 M3/M4 补全）：3.1（wf——**check/nop-wf.md 未触碰，覆盖薄勿当先验**）/3.2（task——check 14 条全标定）/3.3/3.4（batch 深审新发现同域）/3.5（sys——仅标定锁 P0 一条，覆盖薄）/3.6（report——仅标定 ~5 条）/3.7（rule/dyn——check 面覆盖薄）/3.8（fcc 18 条全标定——含本批 3 条 P1 先验）/3.9（gateway 24 条全标定）/3.10（auth 14 条全标定）/2.6（biz/graphql——**完全未引用 check/nop-biz 与 check/nop-graphql**）/3.11/6.2/6.3/6.4；check2 已标定面（auth 15/wf 12/sys 12/report 19/rule 15+dyn 16/batch 12/gateway 18 条）为同根判定与 dup 归并直接输入。
> 只读审计。**执行分支口径**：live-ness 以当前工作树（audit/full-code-review）为读、以 master 可达性为锚。
> **方法学裁定**（照抄 2352 修复后形态 + 草审 M1 第三分支）：① 四态 + partially-fixed 映射规则（暂缓残余→still-live 附 residual）+ dup 归并方向写死（check P1 同根→duplicated 并入轮内发现 ID 保留 check #溯源；gateway :49/:86 直接归并 P1-check-gw-49/86）；② 抽样 ≥50%（净抽样池 35 条中抽 ≥23），安全（D5）/数据正确性（D1）全抽 + 其余 seed 抽样；冻结协议三要素（seed=2353+宇宙快照哈希+冻结表 `_tmp/fcr-6.6-frozen.csv`）落盘，脚本 `_tmp/fcr-6.6-sample.py`，报告附录记 SHA-256；③ live-ness 双锚=代码亲读+**实跑三分支**：处置标注指名测试→实跑之 / 标注无指名测试〔file-retry-tcc 形态〕→实跑域内或模块测试 / 无标注→实跑域内测试（6.4 F4 教训——仅读断言不判 already-fixed）；④ 撞车检索面=相邻工作项发现清单全量 grep（6.3 教训），匹配键=归一化文件路径+类/方法符号。
> Draft Review: agent_aea409af-8808-4019-8577-e2b89ee5b654 首轮 2 Blocker（B1 三处计数错误——14 份→13 份/分母 ≈60+→46（10+14+22，sys 4 条搬运+三处扣除）/下限 23 + B2 处置面先验事实倒置——34/46 已带标注〔10 单元〕、12/46 无标注〔gateway/report/rule〕、SUMMARY:101 过时行复发）+ 4 Major（M1 fcc 处置无指名测试+3.8 全量标定承接+方法学③第三分支/M2 轮内已终判 11 条承接规则/M3 撞车面漏 3.3/3.4/M4 漏 check2 已标定面+各 3.x 覆盖度实测注记）+ 5 Minor 全修（m1 Non-Goals 补 6.9 排除/m2 冻结脚本路径命名/m3 ≥9 行歧义消除/m4 提交后转 active/m5 D5/D1 量级预估〔Phase 1 对账落盘〕）——本轮全部修复，待聚焦复核。

## Purpose

执行 roadmap 工作项 6.6：check 系列 P1 批次 b 抽样标定 ≥50%，产出报告 `6.6-check-p1-batch-b.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- 批次 b 定义与候选单元见头部注记；精确分母 Phase 1 对账落盘（对账含独立枚举 diff 步骤防漏单元——2352 B1 教训）。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（测试实跑为验证副作用）；工作树在前序收口 commit 后干净。
- 撞车面（Phase 2 必查，草审 M3/M4 补全后）：3.1（wf——check 面覆盖薄）/3.2（task——check 14 条全标定）/3.3/3.4（batch 新发现同域）/3.5（sys——覆盖薄）/3.6（report——~5 条）/3.7（rule/dyn——覆盖薄）/3.8（fcc 18 条全标定）/3.9（gateway 24 条全标定——P1-check-gw-49/86 与 :70/:109/:131 终判直接归并）/3.10（auth 14 条全标定——:51 AF 承接）/2.6（biz/graphql——check 面零覆盖）/3.11/6.2/6.3/6.4；check2 已标定面（auth 15/wf 12/sys 12/report 19/rule 15+dyn 16/batch 12/gateway 18）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.6-check-p1-batch-b.md`：摘要 + 对账台账 + 冻结抽样表 + 标定台账 + 仍 live 清单 + 驳回台账 + 裁决。
- **抽样标定 ≥50%**（D5/D1 全抽 + 其余 seed 抽样；冻结三要素落盘）；仍 live 者 `P1-6.6-<序号>` 编号进 R1.0 输入（dup 者并入轮内 ID 保留溯源）。
- index 登记 + 独立 closure audit + roadmap 6.6 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 批次 a/c/d；P0（6.4 已闭）；metadata（批次 c）；P2/P3（6.9 归档——批次 b 单元报告内大量 P2/P3 与处置残余不在本项范围）；修复执行。

## Scope

### In Scope

- 批次 b 三成员组 check P1 全量对账 + ≥50% 抽样标定
- 只读输入：check/ 单元报告、轮内深审报告、git 历史

### Out Of Scope

- 批次 a/c/d；metadata；P0；修复执行。

## Execution Plan

### Phase 1 - 对账、抽样与标定（3 agent 并行）

Status: planned
Targets: 批次 b check 单元报告 P1 清单

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent 执行）：① 独立枚举 ROADMAP Phase 1-3 单元表 diff 候选 13 份（草审 B1 防再漏）② 批次 b 单元→P1 全量清单 46 条（维度派生 D5/D1〔量级预估 Phase 1 落盘〕+ 处置标注核对〔34/46 先验逐条复核〕+ 轮内已终判 11 条承接标记〔gateway 5/auth 1/task 5——不重复抽样〕）③ 统计表机械核对（13 份）④ 冻结协议三要素（seed=2353；脚本 `_tmp/fcr-6.6-sample.py`；冻结表 `_tmp/fcr-6.6-frozen.csv`；净抽样池 35 条抽 ≥23）
- [ ] Agent 1/2/3「抽样标定份 A/B/C」：逐条四态标定（代码亲读+实跑三分支：标注指名测试/标注无指名→域内或模块测试/无标注→域内测试+撞车比对）
- [ ] 主 agent 抽查质量：冻结表重放 + 标定抽验（含实跑）
- [ ] 产物 `_tmp/fcr-6.6-checkp1-a/b/c.md` + 冻结表
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 初稿落盘，每条 still-live 条目 ≥9 行完整；标定台账双锚证据
- [ ] 抽样 ≥50% 达成；抽样表可重放（SHA-256 记录）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁 + already-fixed 抽验 ≥30% 含实跑 + 撞车复验 + 净账
- [ ] 主 agent 汇总：四态统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.6-checkp1-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.6-check-p1-batch-b.md`（附录记冻结协议 SHA-256）；index 登记 6.6 行（替换占位行）+ 仍 live 进 P1 汇总（dup 并入轮内 ID）
- [ ] 定向验证锚点：标定抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——抽样覆盖率核验）；**通过后** roadmap 6.6 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试实跑（处置指名测试强制），免除全量构建。

- [ ] 报告落盘且批次 b 对账 + ≥50% 抽样标定全覆盖
- [ ] 每条标定双锚证据可复现；四态归属依据 roadmap 定义；撞车全量比对留痕
- [ ] index 计数与报告标定台账一一对应；仍 live 清单完整进 P1 汇总
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.6 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；still-live 修复归 R1.x；未抽样余量按抽样制设计为可接受残留。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
