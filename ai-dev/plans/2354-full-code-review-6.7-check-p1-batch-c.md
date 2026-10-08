# 2354 full-code-review 6.7 check 系列 P1 抽样标定 c（ai/stream/code/graph/credential/datav/metadata）

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.7（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: 批次权威定义=矩阵 §6：批次 c 成员 = **nop-ai、nop-stream、nop-code、nop-graph、nop-credential、nop-datav、nop-metadata**（metadata 唯一归属批次 c）。同族先例=plan 2352/2353（修复后形态照抄：独立枚举 diff/维度派生/冻结三要素/dup 方向/符号匹配键/分支口径/强制实跑/处置面实测先验）。
> 机械基线（主 agent 实测，2026-10-07；经 Draft Review agent_254ef0f5 逐份实测勘正）：批次 c 候选单元 **12 份**（ai 族 4/stream 族 4/code/graph/datav/metadata）+ **msg-cluster-cred 单元的 credential 份额 1 条**（该单元 P1=8＝cluster 4+message 3+credential 1〔DefaultCredentialKeyProvider passphrase 入 param :172〕；矩阵 §6 为唯一权威——credential→批次 c、message/cluster→批次 d；Phase 1 对账执行该裁定并记录）。**批次 c 对账池 = 46 条 P1**（ai 20〔ai-core-api 3/ai-rest 7/ai-toolkit-skills 6/nop-ai-agent 4〕+ stream 14〔core 4/cep 1/flow-conn 3/runtime 6〕+ code 5 + graph 4 + datav 1 + metadata 1 + mcc-cred 1；SUMMARY Phase 4 的 49 含批次 d 的 search 5 条已扣除）。**处置面先验（草审 B2 实测翻转）**：批次 c **46 条 P1 零处置标注**（8 单元共 9 处 fix-ai-check 标注全部挂 P0 条目——P0 已由 6.4 闭环；SUMMARY :101「P1 尚未处置」对本批成立）——净池从零标定，实跑三分支全走第三分支（域内测试）。**轮内已终判承接（草审 M1）**：mcc-cred :172 已被 3.10 终判 **still-live 并登记 `P1-check-mcc-172` 进 R1.0 台账**——直接承接（计入对账台账、归并 P1-check-mcc-172 保留 check 溯源，不重复抽样）；**净抽样池 = 46−1 = 45 条，≥50% 下限 = 23**（双轨口径：净池 %/严格基线 46 %）。
> 上游标定富集面（撞车承载，草审 M2 勘正后）：ai arm 台账（`ai-dev/backlog/audit-remediation-roadmap.md` v16 + `ai-dev/audits/arm-index.md` + 30+ 份 arm-MA*.nop-ai-*——对应 7.1 域）/metadata 台账（`arm-unclosed-findings-nop-metadata.md`——其 P1×1 live=OrmModelImporter currentTimeMillis 与 check nop-metadata P1〔MetaTableProfiler :331-346〕非同条，7.3 域）/2.8（stream-sql 首审——四 stream 单元非其范围；其 :83 登记 2 处预存测试失败为实跑红先对照锚）/6.4（stream P0×4 already-fixed :53 + graph Edge P0 #25 处置块——graph 先例载体）/6.5（nosql P2-6.5-001 同域邻接）/3.10（mcc-cred 终判）/3.7（rule 属批次 b 噪音项不引）/7.3/7.4/7.6 为 todo 勿引声明。
> 只读审计。**执行分支口径**：live-ness 以当前工作树（audit/full-code-review）为读、以 master 可达性为锚。
> **方法学裁定**（照抄 2353 修复后形态 + 草审 m2/m3/m4 补全）：① 四态 + partially-fixed 映射规则 + dup 归并方向写死（mcc-cred 承接 P1-check-mcc-172；同根→并入轮内发现 ID 保留 check #溯源）；② 抽样 ≥50% 双轨口径（净池 45 的 ≥50%=23 / 严格基线 46）+ D5/D1 全抽 + 其余 seed 抽样；**D1 量级预估 Phase 1 落盘**（stream/code/graph 面 D1 重仓，全抽规模可能 30±——3 agent 分配弹性预留）；冻结协议三要素（seed=2354+宇宙快照哈希+冻结表 `_tmp/fcr-6.7-frozen.csv`）落盘，脚本 `_tmp/fcr-6.7-sample.py`，报告附录记 SHA-256；③ live-ness 双锚=代码亲读+实跑三分支（本批 P1 零标注→全走第三分支域内测试；**实跑红先对照 2.8 :83 预存失败登记〔TestStreamConnectorRegistryDiscovery 等〕与 6.4 台账，不得径判 still-live**）；④ 撞车检索面=相邻工作项发现清单全量 grep，匹配键=归一化文件路径+类/方法符号；⑤ 执行分支口径见上。
> Draft Review: agent_254ef0f5-a359-4eda-abcd-d8cdc602791a 首轮 2 Blocker（B1 机械基线五分量全错+49 含批次 d search 5 条——实测池 46=ai 20/stream 14/code 5/graph 4/datav 1/metadata 1/mcc-cred 1/B2 处置面先验倒置——批次 c P1 零标注〔9 处标注全挂 P0〕）+ 4 Major（M1 漏 3.10 撞车/承接——mcc-cred :172 已终判 P1-check-mcc-172，净池 45/下限 23/M2 撞车面五处归属勘正〔6.5→6.4/删 2.2/6.6 改 6.4 载体/3.10 域注记/3.7 删〕）+ 5 Minor 全修（m1 双轨口径/m2 D1 量级预估/m3 closure audit 抽样覆盖率核验/m4 预存失败参照锚/m5 ai arm 台账路径写实）——本轮全部修复；聚焦复核（agent_f5709e50，2026-10-08）逐项独立实测 **PASS 零问题**（五分量逐单元亲数/9 标注全挂 P0 逐处回溯/P1-check-mcc-172 同一发现实锤/撞车面五处归属全吻合/想象性分析无卡死点）——转 active。

## Purpose

执行 roadmap 工作项 6.7：check 系列 P1 批次 c 抽样标定 ≥50%，产出报告 `6.7-check-p1-batch-c.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- 批次 c 定义与候选单元见头部注记；精确分母与处置面 Phase 1 对账落盘（独立枚举 diff 防漏单元）。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（测试实跑为验证副作用）；工作树在前序收口 commit 后干净。
- 撞车面（Phase 2 必查，草审 M2 勘正后）：ai arm 台账（路径见头部）/2.8（stream-sql——四 stream 单元非其范围；预存失败锚 :83）/6.4（stream P0×4+graph Edge 处置块）/6.5（nosql P2-6.5-001 邻接）/**3.10（mcc-cred 终判 P1-check-mcc-172 承接）**/metadata 台账（非同条声明）/4.4（search 邻接）/6.2/6.3/6.2；7.1/7.2/7.3/7.4/7.6 todo 勿引。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.7-check-p1-batch-c.md`：摘要 + 对账台账 + 冻结抽样表 + 标定台账 + 仍 live 清单 + 驳回台账 + 裁决。
- **抽样标定 ≥50%**（净池，D5/D1 全抽 + 其余 seed 抽样；冻结三要素落盘）；仍 live 者 `P1-6.7-<序号>` 编号进 R1.0 输入（dup 并入轮内 ID 保留溯源）。
- index 登记 + 独立 closure audit + roadmap 6.7 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 批次 a/b/d；P0（6.4 已闭）；P2/P3（6.9 归档——批次 c 单元报告内 P2/P3 不在本项范围）；修复执行；ai/stream 域的全量深审（7.1/7.2 另行）。

## Scope

### In Scope

- 批次 c 七成员组 check P1 全量对账 + ≥50% 抽样标定
- 只读输入：check/ 单元报告、轮内深审报告、ai arm 台账、metadata 台账、git 历史

### Out Of Scope

- 批次 a/b/d；P0；P2/P3（6.9）；修复执行；7.1/7.2 全量增量面。

## Execution Plan

### Phase 1 - 对账、抽样与标定（3 agent 并行）

Status: completed
Targets: 批次 c check 单元报告 P1 清单

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（7134697080）、`git status` 干净后开始（65e9e4b163 后工作树干净佐证）
- [x] 主 agent 对账落盘（委派对账 agent 执行）：① **独立枚举 diff=一致零出入**（Phase 4 12 单元→11 份+Phase 3 metadata 1 份+Phase 5 msg-cluster-cred credential 份额；拆分裁定落盘：credential 1 入池/cluster 4+message 3 归批次 d）② 46 条 P1 全量清单（维度全部取自条目内「维度」字段原标注零派生；处置面实测=46 条零标注与先验吻合〔9 处标注全挂 P0〕；mcc-cred :172 承接标记 carry=P1-check-mcc-172，3.10 :93/:475 锚点实测命中）③ **统计表机械核对 12/12 通过**（SUMMARY Phase 4 49−search 5=44+metadata 1+mcc 1=46 三向账平）④ 冻结协议三要素：seed=2354 + 净池快照 `0e819cc0…` + 冻结表 `_tmp/fcr-6.7-frozen.csv` SHA-256 `6b175321…`（**33 条=D5×5+D1×28 全抽+其他 0**，覆盖净池 73.3%/严格基线 71.7% 双达标；分配 A11/B11/C11 标准轮转〔弹性阈值 >33 未触发〕；双跑逐字节 diff PASS）
- [x] Agent 1/2/3「抽样标定份 A/B/C」：逐条四态标定（代码亲读+域内测试实跑〔全走第三分支；实跑红对照 2.8 :83 预存登记——三份零触发〕+撞车比对）——**粗账 A=AF 4/live 7、B=AF 5/live 6、C=AF 5/live 6，合计 AF 14 / still-live 19 / dup 0 / FP 0**；零 P0 疑似；B 份惯例注记：ai-rest#P1-3 同根 ai-toolkit-skills#P0-1（6.4 已 AF 终态）按「同根已修不并组」记 AF 保留 check 溯源（dup 口径 Phase 2 canonical）
- [x] 主 agent 抽查质量：冻结表重放 SHA-256 一致（6b175321…）+ 标定抽验——A#1 AnthropicDialect assistant→"model" :750-751 亲读在位 / B#11 PageRank weight÷outDegree 未归一化亲读在位 / A#2 AF 提交 3772408e1f（plan 350）可达 / 独立复跑 TestAlgorithms 21/21 绿与 B 份声明一致
- [x] 产物 `_tmp/fcr-6.7-checkp1-a/b/c.md` + 冻结表 `_tmp/fcr-6.7-frozen.csv`
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（三份初判零 P0 疑似）

Exit Criteria:

- [x] 初稿落盘，每条 still-live 条目 ≥9 行完整；标定台账双锚证据
- [x] 抽样 ≥50% 达成（净池 33/45=73.3%、严格基线 33/46=71.7%）；抽样表可重放（SHA-256 记录+主 agent 重放一致）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：**VERDICT PASS 零事实翻案**——still-live 19/19 逐条亲读（符号重定位）+ 严重性终裁 **P1×11 / P2×8**；AF 抽验 9/14=64% 锚内容核验+7/14=50% 实跑全绿（16 提交 merge-base 亲证）；撞车定向 7 条复验+33 锚全量 grep 零新增同根；B-2 canonical 裁定 ai-rest#P1-3 记 dup 非 AF（6.6 先例——dup 11 含 9 归并 AF 目标同型）；**canonical AF 13 / dup 1 / still-live 19 / FP 0=33**；46=33+1（P1-check-mcc-172）+12（未抽样全「其他」类）账平；终态 P0=0/P1=11/P2=8/P3=0
- [x] 主 agent 汇总：四态统一（B-2 列间转移 AF→dup）、撞车去重终裁（零新增同根维持）
- [x] 台账收敛（复核 §5 canonical + 三份产物净账按 canonical 对齐）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-6.7-checkp1-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `6.7-check-p1-batch-c.md`（附录记冻结协议 SHA-256）；index 登记 6.7 行（替换占位行）+ 仍 live 进 P1 汇总或 P2 台账（P1 汇总 11 行 + P2 台账 8 行）
- [x] 定向验证锚点：标定抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——**抽样覆盖率核验**）；**通过后** roadmap 6.7 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试实跑（处置指名测试强制），免除全量构建。

- [ ] 报告落盘且批次 c 对账 + ≥50% 抽样标定全覆盖
- [ ] 每条标定双锚证据可复现；四态归属依据 roadmap 定义；撞车全量比对留痕
- [ ] index 计数与报告标定台账一一对应；仍 live 清单完整移交
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.7 回填 `done`；log 收口记录已写
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
