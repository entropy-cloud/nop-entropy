# 2409 full-code-review R2.0 — 跨维度裁决（P2 台账清账 + 修复冲突 sweep + 登记缺口修正）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R2.0（跨维度裁决）；R1.0 汇聚 `_tmp/fcr-r10-aggregation.md` §3 遗留义务（「P2 台账 44 行仅盘点不裁决，R1.0 后续须逐行裁决」）
> Related: R1.34/plan 2400（P1-6.7-005/006 交付）、R1.x 全部 done 行、index P2 裁决台账
> Owner Doc: `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md`（台账即本体）
> Plan Review: 首轮 agent_2237d5ae **NOT READY**（2026-10-10）：3 Major（①台账实为 45 行非 44〔P2-R1.5-001 汇聚后入账〕且子计数两错；②grep pattern 漏「待 R1.0 批量裁决」4 行盲区；③「多数已登记升级触发」失实〔实为 6/45，3.11 族 3/12；理由本体在 3.11 报告 §五〕+ 3.11 报告 :282「触发条款不得删除」护栏未带入）+ 4 Minor（successor 枚举漏 7.1/7.4 两台账；归属重定向未回写；R1.10「部分」未归一；占位行三要素载体未写明）——**本轮全部修订**，并按审查建议增「明示裁量」段（部分行带现成处方本批有意不执行）。核心主张（登记缺口/R1.0 遗留义务/R1.x 全 done/五起交叉事件在案/deferred-watch 非 rubber-stamp）全部核验成立。第二轮复核 agent_2237d5ae：六项修订 5 项落实、新发现 1 Major（Goals/Phase 2 标题/Deferred 节三处残留 44）+ 3 Minor（口径族 13→14、nop-code 族 3 行→1 行覆盖 3 findings、Phase 2 执行项补触发条款保留与原措辞替换说明）——已全部修正（剩余 2 处「44 行」为汇聚原文的合法历史引用），复核意见明示修正后即为 READY，进入执行。

## Purpose

收口 R2.0 跨维度裁决：①修正 index 登记缺口（P1-6.7-005/006 已由 R1.34 修复但行未翻 fixed）；②履行 R1.0 遗留义务——P2 裁决台账 45 行逐行裁决（修 / deferred 且带 Why Not Blocking Closure + Successor，规则 #3）；③修复冲突 sweep（R1.x 修复间无冲突的实证盘点）。

## Current Baseline（2026-10-10 live 亲读）

- **R1.x 全部 done**：roadmap MR 表 R1.1–R1.42 + R1.6a/b/c + R1.10b 共 46 行全部 done（index MR 表 R1.10 行原为「done（部分）」〔closure audit M-2 勘正位点：在 index 非 roadmap〕，其部分=successor R1.10b 已于 plan 2376 交付完毕、P1-2.6-004 已翻 fixed，本批加归一注记）。
- **登记缺口**：index P1 汇总表 :156-157 的 P1-6.7-005/006 仍为 open——交付事实已核实 live：`ChatOptions.maxContextTokens`（ChatOptions.java:80/:214）+ `resolveMaxContextTokens` 三消费者收敛（PipelineCompactor.java:74/:177/:181）+ BAIL `ChatToolResponseMessage.error` 注入链（plan 2400 交付，独立 closure audit PASS）——两行应翻 fixed（引 R1.34/plan 2400）。
- **R1.0 遗留义务**：`_tmp/fcr-r10-aggregation.md` §3 明文「P2 裁决台账盘点（44 行，全部『待 R1.0 裁决』）……本汇聚仅盘点不裁决。R1.0 后续须对每行作出『修 / deferred（Why Not Blocking Closure + Successor）』裁决并同步 roadmap」——该义务未履行，现由 R2.0 承接（跨维度裁决归属）。
- **P2 台账现状**：**45 行**（index「P2 裁决台账」节，2026-10-10 实数；R1.0 汇聚时点为 44，P2-R1.5-001 系汇聚后入账——行数以执行时 grep 实数为准）全部待裁决（「待 R1.0 裁决」41 + 「待 R1.0 批量裁决」4〔含 P2-R1.5-001〕）。构成：3.11 安全例外族 12 行（F-WF-01-1..F-D1-1 + P2-3.11-001）；巨型文件拆分 4 行（P2-4.3-001..004）；文档草稿态 1 行（P2-5.2-001）；check 溯源终裁 **14 行**（P2-6.4-001..P2-6.8-002，各行已带复核终裁理由）；增量巡检 **13 项**（P2-7.1-001..006、P2-7.2-001、AR-06、P2-7.6-001、P2-R1.5-001 及 3 个批量占位行 ×21/×35/×5——占位行覆盖 61 个散量 P2）；nop-code 流程族 1 行（P2-7.4-001..003，单行覆盖 3 findings）〔子计数按族归并，以 live 行枚举为准〕。
- **修复冲突面**：R1.x 已知交叉事件均有在案裁定——R1.8 附带修 R1.6c 下游回归（plan 2373 记录）、R1.12 提前消化 R1.17 附带工单（plan 2378 记录）、R1.20 修订 session 作用域（plan 2386 追记 + 独立修订 commit）、R1.23 提前执行（门禁阻断，plan 2387 记录）、R1.10→R1.10b 拆分（已闭环）。

## Goals

- index 两行 stale 状态修正（有交付实证）。
- P2 台账 45 行逐行落裁决：全部 **deferred-watch**（Why Not Blocking + Successor 逐行/逐族落账），无一静默降级；不新开修复批（P2 修复如需，走各自 successor 通道另立 plan——不在本裁决批）。
- 修复冲突 sweep 结论落账（roadmap R2.0 行）。

## Non-Goals

- P2 项的实际修复（裁决≠修复；修复走 successor 通道另立 plan）。**明示裁量**：部分行带现成低成本处方（如 P2-7.1-004 换 JSON 序列化器、P2-7.1-003 换 NopException），本批有意不执行——P2 修复不属 R1.x 强制范围（roadmap 规则 #3），统一走 successor 通道按优先级另立。
- P3 watch 项（规则 #3 明确不入）。
- V.1 全量回归（后继工作项）。

## 裁决口径（规则 #3 要求逐行带 Why Not Blocking + Successor）

各族裁决依据（行内既有终裁理由即为 Why Not Blocking 的事实基础，R2.0 确认并登记 disposition）：

1. **3.11 安全例外族 12 行**：deferred-watch。Why Not Blocking：各行为 3.11 复核终裁 P2——**理由本体以 3.11 报告 §五为准（行内仅部分携带：实测全台账仅 6/45 行带显式「升级触发」条款，3.11 族内 3/12：F-N1-1/F-N3-2/P2-3.11-001；其余行内只有修复处方）**，裁决文本须逐行从报告 §五补引终裁理由。**硬性护栏（3.11 报告 :282 明文）：裁决时不得删除行内/报告既有升级触发条款**——Phase 2 执行项显式保留。Successor：security-audit 维护通道（触发条件命中即按该通道另立修复 plan）。
2. **P2-4.3-001..004 巨型文件**：deferred-watch（optimization candidate）。Why Not Blocking：4.3 审计裁定拆分建议级、保留/monitor 台账带量化触发条件。Successor：4.3 拆分台账触发命中时另立拆分 plan。
3. **P2-5.2-001 草稿态批量标注**：deferred-watch。Why Not Blocking：P1 部分（maker-checker/03）已由 R1.42 修复；残余 29 篇为文档治理非代码缺陷。Successor：docs 维护通道（逐篇触碰时顺手标注 + 专批可选）。
4. **check 溯源终裁 14 行（P2-6.x）**：deferred-watch。Why Not Blocking：各行即 6.4–6.8 批次复核终裁 P2 的理由本体（零消费者/不可达/主体已修/降档判例）。Successor：watch（触发条件命中转修复 plan）。
5. **增量巡检族（P2-7.x/AR-06）**：deferred-watch。Why Not Blocking：各行 7.x 审计终裁理由在案（行内未载者从各 7.x 报告补引）。Successor 按 owning 台账逐族：P2-7.1-001..006→`ai-dev/backlog/audit-remediation-roadmap.md`（7.1 行 Owner Doc）；P2-7.2-001/AR-06/backlog×35→`nop-stream-invariant-loop-roadmap.md`；P2-7.6-001/×21→`nop-datav-audit-followups.md`；P2-7.3-×5→`arm-unclosed-findings-nop-metadata.md`；P2-7.4-001..003→`nop-code-invariant-loop-roadmap.md`。**批量占位行裁决文本须显式引用 owning 台账的逐项状态节作为三要素载体**（台账本体即逐项跟踪）。
6. **P2-R1.5-001（双向 1:1 detach SOE 残余）**：deferred-watch。Why Not Blocking：R1.5 执行期终裁 P2（触发面窄：双向关联双侧已装载 + detach 显式 null 级联）；R1.5 的 P1 主体（detach 语义对齐）已修并有判别回归。Successor：watch（orm 域触碰级联语义时一并评估环检测）。

## Execution Plan

### Phase 1 - index 登记缺口修正

Status: completed
Targets: index P1 汇总表 :156-157

- Item Types: `Fix`

- [x] P1-6.7-005/006 翻 fixed（引 R1.34/plan 2400 + live 锚：ChatOptions.java:80/:214、PipelineCompactor.java:74/:177/:181、ChatToolResponseMessage.error 注入链 ReActAgentExecutor.java:1175）——`grep "| open |"` index=0
- [x] `ai-dev/logs/` 条目更新

Exit Criteria:

- [x] index P1 汇总表零 open 行（`grep -c "| open |"` = 0）
- [x] 翻转行带 live 证据锚

### Phase 2 - P2 台账 45 行逐行裁决

Status: completed
Targets: index P2 裁决台账节

- Item Types: `Decision`

- [x] 按裁决口径六族逐行**替换**（45/45 行，程序化处理 assert hit==45）「裁决结果」列原「待 R1.0（批量）裁决（…）」措辞为「**R2.0 裁决（2026-10-10）：deferred-watch**」+ Why Not Blocking（行内未载者从所属审计报告补引）+ Successor 通道——原措辞中的处方内容并入 Why Not Blocking 后移除（否则 grep=0 门禁不可能通过）；**6 处既有「升级触发」条款逐一原样保留**（3.11 报告 :282 护栏）；批量占位行（×21/×35/×5）单行裁决指向 owning 台账并引用其逐项状态节
- [x] `ai-dev/logs/` 条目更新

Exit Criteria:

- [x] 台账零「待 R1.0」残留（`grep -c "待 R1.0"`=0——pattern 须同时覆盖「待 R1.0 裁决」41 行与「待 R1.0 批量裁决」4 行两种措辞，否则 4 行盲区使门禁失效）
- [x] 每行裁决含 deferred-watch disposition + Why Not Blocking + Successor 三要素

### Phase 3 - 修复冲突 sweep + roadmap 收口

Status: completed
Targets: roadmap R2.0 行、daily log

- Item Types: `Decision | Proof`

- [x] R1.x 修复冲突盘点结论落账：五起交叉事件（R1.8/R1.12/R1.20 修订/R1.23/R1.10b）全部有独立裁定记录、无未决冲突
- [x] index MR 表 R1.10 行「done（部分）」归一为 done（**closure audit M-2 勘正**：「部分」字样实际位于 index MR 表而非 roadmap——roadmap R1.10 行自 plan 2375 收口即为纯 done；本项落点为 index 行加归一注记，消除对账噪音）
- [x] roadmap R2.0 翻 done：状态注记**写明承接 R1.0 遗留义务**（P2 台账裁决归属从 R1.0 重定向至 R2.0）+ 裁决结论摘要 + 指向 index 台账；index 台账头部「R1.0 逐项裁决」措辞同步（避免 stale 归属）
- [x] daily log 条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（含本 plan 与台账裁决抽查），evidence 写入 Closure 段

Exit Criteria:

- [x] roadmap/index/log 三方一致
- [x] 独立 closure audit 落盘

## Closure Gates

- [x] index P1 汇总零 open 行
- [x] P2 台账 45 行全部带 R2.0 裁决（三要素齐备；行数以 live grep 实数核对）
- [x] 修复冲突 sweep 结论在案
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（本 plan 即裁决批；无新增 deferred 项。45 行 P2 的 deferred-watch 裁决即交付物本体。）

## Non-Blocking Follow-ups

- P2 项实际修复：各 successor 通道按触发条件/优先级另立 plan（不在本批）

## Closure

Status Note: R2.0 三项交付闭合：①index 登记缺口修正（P1-6.7-005/006 补翻 fixed，P1 汇总零 open 行）；②R1.0 遗留义务履行——P2 裁决台账 45 行全部 deferred-watch 清账（WNB+Successor 三要素逐行落账、6 处升级触发条款原样保留〔3.11 报告 :282 护栏〕、批量占位行指向 owning 台账、头部归属重定向 R1.0→R2.0）；③修复冲突 sweep（五起交叉事件全部在案裁定、无未决冲突）。独立 closure audit 首轮 Cannot Close（Blocker-1 头部同步静默失配 + M-2「R1.10 归一」位点误记 + Minor×3）→ 全部修正后关账。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_8b43b4c8（独立 fresh-session closure auditor，2026-10-10）
- Evidence:
  - Phase 1 PASS：index `grep "| open |"`=0；P1-6.7-005/006 三锚亲读核实（ChatOptions.java:80/:214、PipelineCompactor.java:74/:177/:181、ReActAgentExecutor.java:1175 BAIL error 注入逐字命中）
  - Phase 2 PASS：45/45 行机械核对含 deferred-watch+WNB+Successor；`grep "待 R1.0"`=0（HEAD 原文 46 处→0，双措辞无盲区）；抽验 5 行（P2-3.11-001/P2-6.7-001/P2-R1.5-001/×21/F-N3-2）三要素齐备；**裁决诚实性双 PASS**——P2-3.11-001 WNB 溯源 3.11 报告 :200/:243、P2-R1.5-001 溯源 plan 2368 :126/:131，非编造
  - Phase 3 PASS（修后）：五起交叉事件与在案记录一致；index MR 表 R1.10 归一注记落位；台账头部归属重定向实写（audit Blocker-1 修正）
  - 验证命令重放：doc-links --strict 0 errors（6 warnings 均为未触碰旧文件）；git status 4 文件全 ai-dev 文档、零代码
  - 首轮 audit 发现与修正：**Blocker-1** 台账头部同步因 replace 目标串与实文差「P2」二字静默失配——已按实文重写（含 Successor 列历史归属值说明）；**M-2** 「R1.10 归一」位点误记（实为 index MR 表非 roadmap）——plan/roadmap/daily log 四处已按事实勘正并归一 index 行；**m-3** deferred-watch 双写 45 处清理；**m-4** P2-6.8-001 触发原文「nop-js 复活入 reactor」补回；m-5 观察记录不改
  - Deferred 分类检查：本批即裁决批，无新增 deferred；45 行 deferred-watch 处置的诚实性经抽验溯源成立
  - 关单前置动作已按 audit 处方完成：全部修正落盘 → `node ai-dev/tools/check-doc-links.mjs --strict` 复跑 0 errors → 本 Evidence 写入 → Plan Status 翻 completed → `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 复跑退出码 0

Follow-up:

- 45 行 P2 的实际修复：各 successor 通道按触发条件/优先级另立 plan（roadmap 规则 #3 通道，不在本批）
