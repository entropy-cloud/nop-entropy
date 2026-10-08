# 2359 full-code-review 7.3 nop-metadata P1×1 终验 + P2 台账巡检

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.3（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: `ai-dev/audits/arm-unclosed-findings-nop-metadata.md`（Owner Doc，137 行 v3——**只读不动**：roadmap 规则 #2「既有非本目录审计文件一律不动」）；`ai-dev/audits/arm-index-nop-metadata.md`（P1 汇总镜像 :240 已记维度20-01 fixed）。
> 机械基线（**对抗审查 agent_9621ec45 重定基勘正——初稿「P1×1 live」系虚假基线**）：台账维度20-01（OrmModelImporter currentTimeMillis 残余）**已由 MR2 修复**——commit 7478eeffe0（2026-08-05，plan-2026-08-04-1543-3 R2.5，diff=两处 System→CoreMetrics+TestCoreMetricsUsage 扫描扩展至 dao）；现状 `OrmModelImporter.java:55/:65` 为 DDD-006 合规形态（:58/:68 锚漂移为空行）；nop-metadata 主代码 `System.currentTimeMillis`=**0 处**；**台账行「open」与 arm-index「fixed」状态分叉**（流程缺陷，本项登记不回写台账）。roadmap 7.3 行「P1×1 live 复核」措辞记口径缺陷注记（沿 7.2「记不改文本」先例）。**重定基范围 = P1×1 fixed 终验（修复亲读+git 锚）+ P2×16 台账巡检 + 台账↔index 状态对账**。P2×16 计数精确（对抗审查实测：48 行=3 P1+11 登记+3 P0+16 P2+15 P3；4 行已裁定 watch-only/optimization）。
> **方法学裁定**：① P1×1 fixed 终验=修复 commit 亲读+当前代码合规形态核对+arm-index :240 镜像核对；② **P2 三分类标定（m1 勘正——沿 2358 实为三分类）**：fixed-by-later-plan/still-open/watch-only-维持；**条=行**（16 行；行 4 捆 3 ID/7 位点逐 ID 标定〔m2〕；行 16 内嵌 P2-MA3-03 已 arm R2.15 终裁 deferred——沿既有裁决收口不另开题）；③ 每条判定依据一句+代码/台账锚（15/16 行带锚，16-03 可重推导）；④ 撞车检索面=6.7（check nop-metadata P1=MetaTableProfiler 非同条声明核对）/6.9（nop-metadata 3 条升级候选：#P2-L119 维持/#P2-L80、#P2-L98 降级）；⑤ **watch-only P1 落账核对显式对准（m3）**：维度16-01 台账行无完成复核注记+仍题「19/40」vs MA4.4 :165-173 终判「收敛为 8/40 维持 watch-only」——落账缺口登记于报告；⑥ 裁决落账（M1 勘正）：**本项不回写既有台账文件（规则 #2）**——状态分叉与标定终态登记于本项报告+轮次 index；⑦ 验证协议：双锚；Phase 2 抽验 ≥30%；冻结协议三要素（seed=2359+台账行清单哈希）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚。
> Draft Review: agent_9621ec45 首轮 **1 Blocker（B1 机械基线虚假实测——P1 已被 MR2 修复 62 天，台账 open 与 arm-index fixed 分叉，:58/:68 锚漂移空行）** + 2 Major（M1/M2）+ 4 Minor（m1–m4）——本轮全部修复（详见各节勘正注记）；聚焦复核（同一审查员）逐项 PASS 全锚亲读为真（1 cosmetic：Phase 2 EC 措辞未展开——语义由方法学⑥全局定义零矛盾）——转 active。

## Purpose

执行 roadmap 工作项 7.3：nop-metadata P1×1 fixed 终验（台账↔index 状态对账）+ P2×16 台账巡检，产出报告 `7.3-nop-metadata-incremental-audit.md`；P2 still-open 归 P2 台账通道。

## Current Baseline

- 台账 v3（2026-08-05）48 行：3 P1（维度20-01 已 fixed 分叉/MISSING-AUTH watch-only/维度16-01 watch-only 落账缺口）+11 登记 P1（08-05 两轮全 fixed）+3 P0+**16 P2**+15 P3；arm-index :240 已记维度20-01 fixed。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.3-nop-metadata-incremental-audit.md`：摘要 + P1 fixed 终验段（修复亲读+git 锚+状态分叉缺陷登记）+ P2×16 三分类标定台账 + watch-only P1 落账缺口登记 + 裁决（附录记冻结协议）。
- P2 still-open 归 P2 台账通道；终态全量登记于本项报告+轮次 index（不回写既有台账）。
- index 登记 7.3 行 + 独立 closure audit + roadmap 7.3 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 修复执行；回写既有台账文件（规则 #2——状态更正登记于本项报告）；watch-only P1 重复复核（落账缺口登记即可）；P3×15（roadmap 规则 #3 不处理）/前序 deferred 登记 5 行（已裁定触发条件挂起）/08-05 两轮 11 P1 fixed 段重验（带承接 plan 与测试证据）/check nop-metadata 重标定（6.7 已闭）。

## Scope

### In Scope

- P1×1 fixed 终验（7478eeffe0 亲读+现状合规核对+arm-index 镜像核对+状态分叉登记）
- P2×16 行三分类标定（行 4 逐 ID 展开；行 16 内嵌沿既有裁决）
- watch-only P1 落账缺口核对（维度16-01 两点显式对准）
- 只读输入：git 历史、台账、arm-index、6.7/6.9 metadata 域发现

### Out Of Scope

- 修复执行；回写既有台账；watch-only P1 重复复核；P3×15；deferred 5 行；08-05 fixed 段重验；非台账发现。

## Execution Plan

### Phase 1 - 终验与标定（1 agent）

Status: in progress
Targets: 台账 P1×1+P2×16

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（00bc7d0e0d）、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派审计 agent）：① P1×1 fixed 终验（7478eeffe0 diff 亲读+OrmModelImporter :55/:65 合规形态+arm-index :240 镜像+**台账↔index 状态分叉缺陷登记**）② P2×16 行三分类标定（条=行；行 4 逐 ID；行 16 沿既有裁决；每条判定依据+锚）③ watch-only P1 落账缺口核对（维度16-01 两点）④ 撞车比对 ⑤ 冻结协议三要素 → `_tmp/fcr-7.3-audit-a.md`
- [ ] 主 agent 抽查质量：冻结表重放+标定抽验 ≥3 条（含代码亲读）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；标定双锚证据
- [ ] P1 终验+16 行全量标定；样本表可重放
- [ ] No owner-doc update required（只读——不回写既有台账；终态登记于本项报告+轮次 index）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：P1 终验复核+P2 标定抽验 ≥30%+状态分叉缺陷核实+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.3-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.3-nop-metadata-incremental-audit.md`；index 登记 7.3 行 + still-open 归 P2 台账通道（P1 无 R1.0 新输入——终验 fixed）
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项）；**通过后** roadmap 7.3 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [ ] 报告落盘且 P1 终验+16 行全量标定+状态分叉登记
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；still-open 清单完整归 P2 台账通道
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（状态分叉登记供台账 owner 后续维护参考；watch-only 维持。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
