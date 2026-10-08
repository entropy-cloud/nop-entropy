# 2355 full-code-review 6.8 check 系列 P1 抽样标定 d（集成运行时/外围/工具/格式/其他）

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.8（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: 批次权威定义=矩阵 §6：批次 d 成员 = **集成运行时（spring/quarkus/network/integration）、runner/autotest/demo、nop-entropy-e2e、nop-rg、nop-lint + nop-treesitter、其他（format/record/utils/search/message/cluster/dev-tools/frontend-support/migration/benchmark）**（metadata 唯一归批次 c〔6.7 已闭〕、search 唯一归批次 d）。同族先例=plan 2352/2353/2354（修复后形态照抄：独立枚举 diff/维度派生/冻结三要素/dup 方向/符号匹配键/分支口径/强制实跑/处置面实测先验/承接规则/双轨口径）。
> 机械基线（主 agent 实测，2026-10-08；对抗审查 agent_d2cf04c9 逐份复核吻合）：批次 d 候选单元 **17 份**＝集成运行时 4（spring-quarkus P1=1 / net-http-rpc P1=4 / net-misc P1=7 / nop-integration P1=4）+ runner/autotest/demo 3（runner-cli P1=2 / nop-autotest P1=3 / demo-migration P1=2）+ format 5（nop-excel P1=3 / format-record P1=6 / format-pdf-svg P1=4 / format-office P1=3 / format-misc P1=7）+ 其他 5（nop-utils P1=7 / nop-search P1=5 / dev-tools P1=2 / frontend-benchmark P1=2 / msg-cluster-cred 份额 7〔该单元 P1=8=cluster 4+message 3+credential 1；credential 1 条已由 6.7 承接 P1-check-mcc-172〕）。**e2e/nop-rg/nop-lint+treesitter 无 check 单元报告**（Phase 1 对账落盘零报告声明）。**批次 d P1 分母 = 69 条**（70−1 credential 份额）；SUMMARY 分相账平（Phase 3 42=18+1+3+20 / Phase 4 49=44+5 / Phase 5 26 / Phase 6 16）。
> **抽样协议（双轨口径，对抗审查 B1 处方——本批承接面大，写死下限不可行）**：Phase 1 承接实测得净池 N=69−C（C=轮内行级终判承接数）。**净池轨**：抽样 S ≥ ⌈N/2⌉；**严格基线轨**：批次半覆盖 S+C ≥ 35（=⌈69/2⌉，承接已裁定的条目计入批次覆盖）。两轨同时满足；D5/D1 全抽（在净池内）若已超 ⌈N/2⌉ 则「其他」抽 0（同 2352/2353 形态）；A/B/C 分配按实际抽样数均分（弹性：>39 时 13/13/13±，否则按量均分并在脚本注释写死规则）。**承接面先验（对抗审查实测勘正）**：确定性承接 ≈44 条＝format-misc 7〔4.4 A 面行 1-7 全锚〕+ nop-search 5〔4.4 B 面 S-02..S-06 全部「保留 P1 live」带锚〕+ msg-cluster 份额 7〔4.4 C 面 M-01..03+cluster P1×4 全确认〕+ nop-utils 7〔4.5 台账 23/23 已修，7 条 P1 全带 2026-08-22 处置块〕+ dev-tools 2〔4.5 台账 #1/#2 行级锚〕+ runner-cli 2〔4.5 台账 #1/#2〕+ frontend-benchmark ≥1〔4.5 §2.4 blanket+PageProvider 行级锚；JavaScriptService.refreshConfig 仅单元级判定——按「行级锚才承接」严格口径入池，Phase 1 裁定〕+ demo-migration 1〔4.5 §2.5 仅覆盖 nop-migration addCatalog P1；**demo-migration 另一条 P1（nop-quarkus-demo DemoBizModel 匿名类 NPE）不在 4.5 台账、必须入池**〕+ net-misc 7 + net-http-rpc ≥3〔3.9 net-misc 24 条表+net-http-rpc 表逐条终判；:24 still-live/:65 AF/:83 adjudicated-deferred plan 2283〕。**1.8 不在承接先验**（结构审计维度 01/02，其 P1×1 file/credential-app 半拆不对应任何 check nop-integration P1——nop-integration 4 条 P1 全部无轮内锚、必须入池）。**4.4/4.5 preempt 声明即承接依据**：4.4 :678「6.8 直接引用台账不再重开标定」、4.5 :515「dev-tools 25 条无需重复标定」；Phase 1 承接实测以行级锚逐条验证为准（先验仅供聚焦），实测 C 后落盘双轨下限。
> **承接条目的 R1.0 路由与编号（对抗审查 M1 处方）**：承接条目**不新编 P1-6.8-NNN 号**——仍 live 者沿轮内在先 finding ID（P1-4.4-XXX / P1-4.5-XXX / P1-3.9-XXX 等），dup/AF 保留 check # 溯源；6.8 报告承接台账逐条登记「check # → 在先 ID → 终判 → index 登记状态」映射；**发现轮内 still-live 条目未在 index P1 汇总登记的，Phase 3 补登记**（R1.0 汇聚以报告全集为准防漏单——roadmap R1.0 Details 对账规则）；禁止双重立项（既有在先 ID open 者不重复入 P1 汇总）。
> **处置面先验（主 agent grep 实测 2026-10-08，对抗审查逐单元复核吻合；提及数含 P0/P2/P3 注释，P1 级归属 Phase 1 逐条实测）**：format×4=76 提及（record 20/pdf-svg 22/office 18/misc 16——format-record 全部 21 条带处置块抽查属实）、nop-utils 23（全部 23 条带 2026-08-22 处置块）、dev-tools 25、net-misc 3、nop-integration 2、nop-search 1；spring-quarkus/net-http-rpc/runner-cli/nop-autotest/demo-migration/nop-excel/frontend-benchmark/msg-cluster-cred 零提及。
> 上游标定富集面（撞车承载）：4.4/4.5/3.9 全标定面（见上）/1.8（集成层结构审计——撞车面对照非承接源）/4.2（**测试有效性审计——zero-test 清单据点+P1-4.2-001/002 HTTP 适配层、P2-4.2-002/005 与 spring-quarkus/net/autotest 池成员邻接，对抗审查 m3 补入**）/6.2（吞异常扫描）/6.3（接线审计——runner/spring 邻接）/2.7（IoC 合规整域）/4.1（错误处理全仓面）/3.11（JWT 主密钥族 22 文件）/5.1（docs 一致性）；**7.7（pulsar/record 残留）todo 勿引**；6.1（空壳扫描 P2-6.1-001 Pulsar seekToPosition——message 面邻接登记不立案）；3.6 与 format-pdf-svg 零管辖交叉（对抗审查实测确认）。
> 只读审计。**执行分支口径**：live-ness 以当前工作树（audit/full-code-review）为读、以 master 可达性为锚。
> **方法学裁定**（照抄 2354 修复后形态 + 本批特有补全）：① 四态 + partially-fixed 映射规则（暂缓残余→still-live 附 residual）+ dup 归并方向写死（同根→并入轮内发现 ID 保留 check #溯源；归并 AF 目标亦记 dup 列——2354 B-2 canonical 裁定沿用）；② 抽样双轨协议（见头部）+ D5/D1 全抽 + 其余 seed 抽样；D5/D1 量级 Phase 1 净池上落盘；冻结协议三要素（**seed=2355 + 宇宙快照哈希 + 冻结表 `_tmp/fcr-6.8-frozen.csv`**——两处表述一致，对抗审查 m2 勘正）落盘，脚本 `_tmp/fcr-6.8-sample.py`，报告记 SHA-256；③ live-ness 双锚=代码亲读+实跑三分支：处置标注指名测试→实跑之 / 标注无指名测试→实跑域内或模块测试 / 无标注→实跑域内测试；**零测试模块退化规则**：nop-spring-web-starter 实测无 src/test（4.2 清单为据）——无域内测试可跑时双锚退化为「代码亲读+git 历史锚+同模块邻接测试/构建验证」，逐条登记「证据弱化：zero-test」不得静默跳过；**实跑红处置（对抗审查 m1 勘正——2.8 :83 两条预存均在批次 b/c 域与本批零交集，撤死锚）**：红测试先查 6.4 台账与轮内报告有无预存登记，有则对照归因；无预存锚的红测试按 plan 2331 :93 先例「归因预存并登记」（git 历史+失败模式归因）后裁定，禁止径判 still-live；④ 撞车检索面=相邻工作项发现清单全量 grep（4.4/4.5/3.9/1.8/4.2/6.2/6.3/2.7/4.1/3.11/5.1），匹配键=归一化文件路径+类/方法符号。

## Purpose

执行 roadmap 工作项 6.8：check 系列 P1 批次 d 抽样标定 ≥50%（双轨口径），产出报告 `6.8-check-p1-batch-d.md`，仍 live 者按路由规则进 R1.0 输入。

## Current Baseline

- 批次 d 定义、候选单元、双轨抽样协议、承接先验见头部注记；精确分母/承接数/净池 Phase 1 对账落盘（对账含独立枚举 diff 步骤防漏单元——2352 B1 教训）。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（测试实跑为验证副作用）；工作树在前序收口 commit 后干净。
- net-http-rpc 单元在 check/ROADMAP.md 状态为 in-progress（报告已存在）——Phase 1 对账时如实登记，不影响 P1 清单提取。
- 撞车面（Phase 2 必查）：4.4（format/search/msg-cluster 全标定+preempt）/4.5（utils/dev-tools/runner/frontend-benchmark/migration 全标定 88 条+preempt）/3.9（NET 族全标定——net-http-rpc/net-misc）/1.8（集成层结构对照）/4.2（zero-test 清单+测试缺口邻接）/6.2/6.3/2.7/4.1/3.11/5.1；7.7 todo 勿引；6.1 message 面邻接登记。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.8-check-p1-batch-d.md`：摘要 + 对账台账（含承接台账：check #→在先 ID→终判→index 登记状态映射）+ 冻结抽样表 + 标定台账 + 仍 live 清单 + 驳回台账（FP=0 亦显式宣告）+ 裁决（附录记冻结协议 SHA-256）。
- **抽样标定双轨达标**（净池轨 S≥⌈N/2⌉ + 严格基线轨 S+C≥35）；仍 live 者按路由规则进 R1.0（净池新发现编 `P1-6.8-<序号>`；承接仍 live 者沿在先 ID 并核对 index 登记状态）。
- index 登记 + 独立 closure audit + roadmap 6.8 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 批次 a/b/c（6.5/6.6/6.7 已闭）；P0（6.4 已闭）；P2/P3（6.9 归档——批次 d 单元报告内 P2/P3 与处置残余不在本项范围）；修复执行；e2e/nop-rg/nop-lint 域（无 check 发现，非本项范围）。

## Scope

### In Scope

- 批次 d 成员组 check P1 全量对账（17 份单元+1 份额裁定+承接实测）+ 双轨抽样标定
- 只读输入：check/ 单元报告、轮内深审报告（4.4/4.5/3.9/1.8/4.2 等）、git 历史

### Out Of Scope

- 批次 a/b/c；credential 份额（6.7 已承接）；P0；P2/P3（6.9）；修复执行；e2e/nop-rg/nop-lint 域。

## Execution Plan

### Phase 1 - 对账、承接实测、抽样与标定（3 agent 并行）

Status: planned
Targets: 批次 d check 单元报告 P1 清单

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent 执行）：① **独立枚举 diff**（ROADMAP Phase 3/4/5/6 单元表 vs plan 候选 17 份；msg-cluster-cred 拆分裁定执行：cluster 4+message 3 入池、credential 1 归 6.7 已承接；e2e/nop-rg/nop-lint 零报告声明落盘）② 批次 d 对账池 69 条全量清单（维度派生 D5/D1 量级在净池上落盘+处置标注逐条实测+**承接实测逐单元表：每条 P1 核对 4.4/4.5/3.9 行级锚，得 C 与净池 N=69−C**，frontend-benchmark JavaScriptService 条目按严格口径裁定承接/入池并留痕）③ 统计表机械核对（17 份逐份+SUMMARY 分相账平）④ 冻结协议三要素（seed=2355 + **宇宙快照哈希** + `_tmp/fcr-6.8-frozen.csv`；`_tmp/fcr-6.8-sample.py`；双轨下限按实测 N 落盘；A/B/C 分配规则写死）
- [ ] Agent 1/2/3「抽样标定份 A/B/C」：逐条四态标定（代码亲读+实跑三分支〔零测试退化规则登记〕+撞车比对）
- [ ] 主 agent 抽查质量：冻结表重放 + 承接台账抽验（在先锚回读）+ 标定抽验（含实跑）
- [ ] 产物 `_tmp/fcr-6.8-checkp1-a/b/c.md` + 冻结表 + 承接台账（universe.md ⑤节）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 初稿落盘，每条 still-live 条目 ≥9 行完整；标定台账双锚证据（zero-test 退化逐条登记）
- [ ] 双轨抽样达标（净池轨+严格基线轨，实测 N 与 C 落盘）；抽样表可重放（SHA-256 记录+主 agent 重放一致）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁 + already-fixed 抽验 ≥30% 含实跑 + 撞车复验（含全量 grep）+ **承接台账复核（在先锚回读+index 登记状态核对）** + canonical 净账（dup 口径沿 2354 B-2 裁定）
- [ ] 主 agent 汇总：四态统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.8-checkp1-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.8-check-p1-batch-d.md`（附录记冻结协议 SHA-256）；index 登记 6.8 行（替换占位行）+ 净池仍 live 进 P1 汇总（P1-6.8-NNN）+ **承接仍 live 条目核对 index 登记状态、缺失者补登记** + P2 裁决台账
- [ ] 定向验证锚点：标定抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（五项：证据完整性/严重性标定/引用路径真实性/计数对应——**抽样覆盖率双轨核验+承接台账核验**）；**通过后** roadmap 6.8 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试实跑（处置指名测试强制），免除全量构建。

- [ ] 报告落盘且批次 d 对账 + 双轨抽样标定全覆盖（含承接台账）
- [ ] 每条标定双锚证据可复现（zero-test 模块按退化规则登记）；四态归属依据 roadmap 定义；撞车全量比对留痕
- [ ] index 计数与报告标定台账一一对应；净池仍 live 清单完整进 P1 汇总；承接仍 live 条目 index 登记状态全部核对（缺失补登）
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.8 回填 `done`；log 收口记录已写
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
