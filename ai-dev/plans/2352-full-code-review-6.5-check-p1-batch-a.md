# 2352 full-code-review 6.5 check 系列 P1 抽样标定 a（kernel/core-framework/persistence）

> Plan Status: active
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.5（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: Owner=`ai-dev/audits/check/SUMMARY.md`；**批次权威定义**=矩阵 §6（`00-scope-and-dimension-matrix.md` :171-176）：批次 a 成员 = **nop-kernel、nop-core-framework、nop-persistence** 三行（矩阵 §2 行名）；check 报告按模块名映射到批次成员；四批并集=§2 全部 18 基线行。Roadmap Details：「按矩阵 §6 的批次成员表分批抽样标定（每批 ≥50%，优先安全与数据正确性类；仍 live 的进入 R1.0 输入）」。
> 机械基线（主 agent 实测，2026-10-07；经 Draft Review agent_966287dc 实测勘正）：check 单元报告 54 份；批次 a 候选单元 **12 份**＝kernel 族 5（kernel-small/nop-commons/nop-core/nop-xlang/nop-api-core）+ core-framework 族 1（nop-core-framework）+ persistence 族 **6**（nop-orm/nop-orm-eql/orm-periph/nop-dao/db-migration/**nosql-cdc**〔路径 nop-persistence/nop-nosql+nop-cdc，归 nop-persistence 行——草审 B1 补入，否则四批并集不变量破缺〕）。**批次 a P1 分母 = 48 条**（kernel 族 17 + core-framework 4 + persistence 5 单元 26 + nosql-cdc 1；58 Phase 1 总量 − 批次 b 三单元 10 = 48，账平）；≥50% 下限 = 24 条。**类别派生规则（草审 M1，源报告无类别字段仅有维度 D1-D8）**：安全 = 维度含 D5（本批 ~2 条）；数据正确性 = 维度含 D1（本批 ~42 条）；其余为「其他」——按此规则「优先类全抽」≈91.7% 覆盖（44 条），预期标定量 ~44 条（执行者与 closure audit 按此量级预期）。**fix-ai-check 处置面（草审 m2 → 聚焦复核实测翻转勘正）**：批次 a P1 **47/48 条已带 fix-ai-check 处置标注**（2026-08-22 后写入单元报告；SUMMARY:101「尚未处置」系过时行）——粗分类 ~43 已修复/1 部分修复（db-migration DDL 方言）/3 含暂缓残余（均 db-migration）；唯一无标注 = nosql-cdc 1 条。fix-ai-check ∈ master ∈ 当前工作树（聚焦复核实测 Lazy.java loaded=true 修复逐字吻合）。already-fixed 先验高；实跑对象=处置标注所附回归测试（每条已指名）；still-live 预期 = nosql-cdc 1 条 + 部分/暂缓残余 + 可能修复回归。commit 锚（6410a6bfe0/e6010dc6eb）仅作 P0 同文件邻接上下文。
> 上游标定富集面（草审 F2 补全）：1.2/1.3/1.4（kernel 深审）、**1.5**（xlang 后端+kernel 杂项——与 kernel-small 六子域全域重叠）/1.6/2.1/2.2/2.3（persistence 深审 60 条发现）/2.4（nosql+cdc 深审 38 条——nosql-cdc 单元邻接）/3.11（路径拼接 grep 123 处——nop-core 两条 D5 直接撞车面）/6.2/6.3（db-migration 域 P3）/6.4（check P0 台账）。
> 只读审计。**执行分支口径**：live-ness 以当前工作树（audit/full-code-review）为读、以 master 可达性为锚。
> **方法学裁定**：① 四态标定 + **partially-fixed 映射规则**（暂缓残余→still-live 附 residual 注记；中间态 Phase 2 收敛——2351 F3 同款）+ **dup 归并方向写死**：check P1 与轮内深审发现同根时，check P1 标 duplicated 并入轮内发现 ID（如 P1-2.2-NNN），保留 check #溯源，不另立 P1-6.5-NNN；② 抽样 ≥50% 且安全（D5）/数据正确性（D1）全抽 + 其余 seed 抽样；**冻结协议三要素**（草审 M4）：seed 值 + 宇宙快照（对账表文件+行清单哈希）+ 冻结表在 Phase 1 落盘，报告记录 SHA-256（沿 6.1 先例——`_tmp/` gitignored，持久可见性靠报告附录）；③ live-ness 双锚=代码亲读+**处置标注所附回归测试实跑**（47/48 条有指名测试——6.4 F4 教训；nosql-cdc 无标注条目实跑域内测试）；④ 撞车检索面=相邻工作项发现清单**全量 grep，匹配键=归一化文件路径+类/方法符号**（行号必漂移——2026-08 至 2026-10 修复落盘）。
> Draft Review: agent_966287dc-a7f9-42d9-85f6-1b4339473722 首轮 1 Blocker（B1 候选清单漏 nosql-cdc——批次 a 分母 47→48、四批并集不变量）+ 4 Major（M1 类别维度派生规则+87% 量级预期/M2 撞车面补 1.5/2.4/3.11/6.3+匹配键/M3 partially-fixed+dup 方向/M4 冻结协议三要素）+ 5 Minor 全修（m1 6.4 输入快照规则/m2 处置面空转表述/m3 Agent 3 触发阈值主体/m4 分支口径/m5 3.5 行号 :19+统计表机械核对）——本轮全部修复；聚焦复核（agent_4678bd83）10 项裁定 9 PASS + 1 Blocker（m2 修复引入事实倒置——SUMMARY:101 过时行致处置面误判，聚焦复核独立实测 47/48 已标注〔~43 已修复/1 部分修复/3 暂缓〕）——按复核员给定口径修正三处后转 active。

## Purpose

执行 roadmap 工作项 6.5：check 系列 P1 批次 a（kernel/core-framework/persistence）抽样标定 ≥50%，产出报告 `6.5-check-p1-batch-a.md`，仍 live 者进入 R1.0 输入。

## Current Baseline

- 矩阵 §6 批次 a 定义与候选单元见头部注记；SUMMARY Phase 1 计数（P1=58）含批次 b 成员，精确分母 Phase 1 对账落盘。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（测试实跑为验证副作用）；工作树在前序收口 commit 后干净。
- 撞车面（Phase 2 必查，草审 M2 补全后）：**1.2/1.3/1.4/1.5**（kernel+后端深审发现清单）/1.6/2.1/2.2/2.3（persistence 深审 60 条）/2.4（nosql+cdc 38 条）/3.11（路径拼接 123 处）/6.2/6.3（db-migration 域 P3）/6.4（check P0 台账，以其 Phase 1 完成台账+宇宙表为时点快照输入）；check2 系列（3.5 :19 等已标定面——行号勘正）。匹配键=归一化文件路径+类/方法符号。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.5-check-p1-batch-a.md`：摘要 + 批次 a 单元→P1 清单对账 + 抽样表（冻结）+ 标定台账 + 仍 live 清单 + 驳回台账 + 裁决。
- **抽样标定 ≥50%**：优先安全与数据正确性类全抽，其余按冻结脚本抽样；仍 live 者 `P1-6.5-<序号>` 编号进 R1.0 输入（check #溯源保留）。
- index 登记 + 独立 closure audit + roadmap 6.5 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 批次 b/c/d（6.6-6.8）；P2/P3（6.9）；修复执行；<50% 未抽样部分的补齐（按 roadmap 设计即为抽样制）。

## Scope

### In Scope

- 批次 a 三成员组的 check P1 全量对账 + ≥50% 抽样标定
- 只读输入：check/ 各单元报告、轮内深审报告、git 历史（fix-ai-check 锚）

### Out Of Scope

- 批次 b/c/d；P0（6.4 已闭）；修复执行。

## Execution Plan

### Phase 1 - 对账、抽样与标定（2-3 agent 并行）

Status: completed
Targets: 批次 a check 单元报告 P1 清单

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘：① **独立枚举 ROADMAP Phase 1 单元表 diff 候选清单**（草审 B1 防再漏——diff 结果落盘）② 批次 a 单元→P1 全量清单 48 条（逐条：#ID/模块/摘要/维度 D1-D8/类别派生〔D5=安全、D1=数据正确性、其余其他〕/fix-ai-check 处置〔47/48 已标注：~43 已修复/1 部分修复/3 暂缓——逐条复核标注与 live 一致性〕/轮内撞车候选）③ **各报告统计表 P1 数 = P1 小节数机械核对**（草审 m5）④ 抽样表冻结脚本 `_tmp/fcr-6.5-sample.py`（D5/D1 全抽 + 其余 seed 抽样至总覆盖 ≥50%〔预期 ~42 条 ≈87%〕）——冻结三要素落盘：seed 值 + 宇宙快照哈希 + 冻结表
- [x] Agent 1「抽样标定份 A」：15/15 全部 already-fixed（12 次模块级实跑全绿零降级；撞车 15/15 无同根；db-migration#P1-6 部分+暂缓条判 already-fixed——暂缓残余已由 check2 fb3f860729 DdlSyntax 方言族闭环，同根溯源留痕不 dup）→ `_tmp/fcr-6.5-checkp1-a.md`
- [x] Agent 2「抽样标定份 B」：14 already-fixed + 1 still-live（**P1-6.5-B1** nosql-cdc ZERO 半分支：Duration.ZERO 透传 GETEX PX 0 被 Redis 拒绝——null 半分支已由 check2 fc2b9347f6 修复；TestNosqlCache 无 ZERO 用例；按 partially-fixed 规则立 still-live 附 residual）；撞车 18 符号全量比对 4 命中全部异根、0 duplicated → `_tmp/fcr-6.5-checkp1-b.md`
- [x] Agent 3「抽样标定份 C + 轮内撞车候选全量比对」：14/14 全部 already-fixed（10 模块 10 次实跑 127 tests 全绿；撞车 29 符号零同根；1 邻接注记 nop-orm-eql#P1-3 修复后残余缺口归 2.2 台账承接）→ `_tmp/fcr-6.5-checkp1-c.md`
- [x] 主 agent 抽查质量：冻结表重放 SHA-256 一致 + 3 条亲验（B1 NosqlCache.getAsync null/ZERO 双半分支亲读证实 still-live/A1 StringHelper:2491-2493 `..` 显式拒绝亲读/A1 抽验面抽点）
- [x] 产物 `_tmp/fcr-6.5-checkp1-a/b/c.md`（三 agent 分报告）+ 冻结抽样表 `_tmp/fcr-6.5-frozen.csv`

Exit Criteria:

- [x] 初稿落盘，still-live 1 条 ≥9 行完整条目；44 条标定台账含双锚证据
- [x] 抽样 91.7% 达成（44/48）；抽样表可重放（SHA-256 三次比对一致）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读 + already-fixed 抽验 ≥30%（含实跑）+ 撞车全量比对复验 + 判级复核（降级提议汇总主 agent 终裁）
- [ ] 主 agent 汇总：四态统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.5-checkp1-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.5-check-p1-batch-a.md`（附录记冻结协议：seed 值+宇宙快照+冻结表 SHA-256——`_tmp/` gitignored 持久可见性靠报告附录，沿 6.1 先例）；index 登记 6.5 行（替换占位行）+ 仍 live 条目进 P1 汇总（dup 者并入轮内发现 ID 不另立编号）
- [ ] 定向验证锚点：标定抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——抽样覆盖率核验）；**通过后** roadmap 6.5 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试实跑（抽验条目强制），免除全量构建。

- [ ] 报告落盘且批次 a 对账 + ≥50% 抽样标定全覆盖
- [ ] 每条标定双锚证据可复现；四态归属依据 roadmap 定义；撞车全量比对留痕
- [ ] index 计数与报告标定台账一一对应；仍 live 清单完整进 P1 汇总
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.5 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；still-live 修复归 R1.x；未抽样余量按 roadmap 抽样制设计即为可接受残留。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
