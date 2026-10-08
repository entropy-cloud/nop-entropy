# 2356 full-code-review 6.9 check 系列 P2/P3 批量状态标定（watch-only 归档）

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.9（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: 承接 6.4（P0 全标定——fix-ai-check 分支处置以 master 为准的方法学先例）与 6.5–6.8（P1 四批全闭——批次 a 44/48、b 29/46、c 33/46、d 22+43/69）。同族先例=plan 2352–2355（冻结三要素/处置面实测/承接规则/双轨口径形态照抄；本项为批量归档而非逐条深标定——性质对齐 6.4 的「批量状态标定」）。
> 机械基线（主 agent 实测，2026-10-08）：check 系列 P2/P3 宇宙 = **694 条**（P2 359 + P3 335，`### [P2]`/`### [P3]` 逐份 grep；单元=52 份，扣除 ROADMAP/SUMMARY）。处置提及面：fix-ai-check 提及 513 处/41 文件（含 P0/P1 处置块引用——P2/P3 级逐条归属 Phase 1 实测）。**P0 已由 6.4 全标定（36 P0）、P1 已由 6.5–6.8 四批全闭（209 P1）**——本项仅处理 P2/P3 层。
> **分类规则（批量归档裁定了优先级）**：每条 P2/P3 归入且仅归入一类——① **轮内已标定**：该条目被轮内审计报告行级终判覆盖（3.5/3.6/3.7/3.8/3.9/3.10/3.11/2.x 深审族、4.1–4.5 工程质量族、5.1–5.3 一致性族、6.1–6.8 反族——承接其终判：fixed/registered（已在轮内报告或 index 登记）/watch-only/adj-deferred；已注册的 P2 不重复入 P2 台账）＞ ② **处置块已修**：条目带 fix-ai-check 处置块且标注 fixed/partially-fixed（机械采信+抽样验证）＞ ③ **watch-only 默认**：无标注无轮内覆盖——批量归档为 watch-only（P3 天然档；P2 归档时逐条过一遍升级筛查）。**升级筛查（异常项通道，roadmap 6.9 Details）**：②③ 类 P2 逐条读标题+现状段，凡疑似 P1+ 量级（安全击穿/静默数据损坏/核心契约漂移且无护栏）→ 列入「升级候选清单」上报 R1.0 裁决（**不自动改判**、不自动入 P1 汇总——R1.0 裁决后才定）；P3 仅机械归档不做升级筛查（roadmap 规则 #3：P3 watch-only 不进 roadmap）。
> **验证协议（批量归档的抽验门槛）**：② 类处置块采信抽验 ≥10%（≥35 条）——代码亲读/实跑核验「标注 fixed」是否属实（6.4 F4 教训：仅读断言不判 already-fixed；抽验含实跑 ≥10 条）；① 类轮内标定抽验 ≥15 条在先锚回读；③ 类升级筛查 100% 覆盖全部②③类 P2 标题+现状段（P3 不做）。冻结协议三要素（seed=2356+宇宙快照哈希+抽验样本表 `_tmp/fcr-6.9-sample.csv`）落盘，脚本 `_tmp/fcr-6.9-sample.py`，报告附录记 SHA-256。
> 上游标定富集面（承接源，Phase 1 对账登记）：轮内全部审计报告的 check 系列标定台账（3.5 :xxx 25 条/3.6 19/3.7 22/3.8 24/3.9 86/3.10 29/3.11 14+item14/2.1–2.8 深审族 check2 对照/4.1 3348 catch 面/4.2 测试有效性/4.3 巨型文件/4.4 50/4.5 88/5.1 136 声明/5.2/5.3/6.1–6.8）；**6.5–6.8 的 P1 标定不重复**（已闭批次不在本项宇宙）。
> 只读审计。**执行分支口径**：以当前工作树（audit/full-code-review）为读、以 master 可达性为锚；实跑红按 plan 2331 :93 先例归因预存并登记。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 6.9：check 系列 P2/P3（694 条）批量状态标定——轮内已标定承接 + 处置块采信抽验 + 其余 watch-only 归档 + 异常项升级候选上报 R1.0，产出报告 `6.9-check-p2p3-watchonly-archive.md`。

## Current Baseline

- 宇宙 694 条见头部注记；P0/P1 层已由 6.4–6.8 闭合不在本项范围。
- 6.5–6.8 期间累积的 watch-only 认知：批次报告内 P2/P3 与处置残余明确 Non-Goals 移交本项（2353/2354/2355 Non-Goals 均写明「P2/P3 归 6.9」）。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（实跑为抽验副作用）；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.9-check-p2p3-watchonly-archive.md`：摘要 + 宇宙对账台账（694 条三分类计数，逐单元分布表）+ 承接台账（①类）+ 处置块采信抽验记录（②类）+ 升级候选清单（上报 R1.0 裁决）+ watch-only 归档宣告（③类，全表 `_tmp` 产物引用）+ 裁决（附录记冻结协议 SHA-256）。
- 三分类全覆盖：694 = ①+②+③ 账平；升级候选逐条附疑似理由（不改判级）。
- index 登记 6.9 行 + 独立 closure audit + roadmap 6.9 翻 done（closure audit 先于翻 done）。

## Non-Goals

- P0（6.4 已闭）/P1（6.5–6.8 已闭）层任何重标定；修复执行；升级候选的改判与登记（R1.0 裁决权）；非 check 系列的 P2/P3（各轮内报告自有发现不在本项宇宙）。

## Scope

### In Scope

- check 系列 52 份单元报告 P2/P3 全量分类（694 条）+ 分类规则落地 + 抽验 + 升级筛查
- 只读输入：check/ 单元报告、轮内审计报告、git 历史

### Out Of Scope

- P0/P1 层；修复执行；改判级（R1.0 裁决权）；轮内报告自有发现（非 check 系列）。

## Execution Plan

### Phase 1 - 对账、分类与抽验（2 agent 并行）

Status: planned
Targets: check 系列 P2/P3 全量清单

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent 执行）：① 694 条全量清单（逐单元 P2/P3 计数+条目号+处置标注实测+轮内覆盖标记）② 三分类机械划分（①轮内已标定〔承接台账：在先报告行级锚〕/②处置块已修/③watch-only 默认）③ 统计核对（逐份+三分类账平）④ 冻结协议三要素（seed=2356+宇宙快照哈希+抽验样本表）⑤ **升级筛查**：②③类 P2 全部标题+现状段过读，产出升级候选清单（附疑似理由，不改判）
- [ ] Agent 1「抽验」：②类处置块采信抽验 ≥35 条（代码亲读+实跑 ≥10 条——6.4 F4 教训）+ ①类承接锚回读 ≥15 条 → `_tmp/fcr-6.9-verify-a.md`
- [ ] Agent 2「升级候选复核」：对账 agent 的升级候选清单逐条独立复核（亲读代码锚，独立判断疑似量级是否成立、有无遗漏候选——②③类 P2 全量快扫兜底）→ `_tmp/fcr-6.9-verify-b.md`
- [ ] 主 agent 抽查质量：冻结表重放 + 抽验抽读 + 升级候选终览
- [ ] 如发现 live P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 初稿落盘；三分类账平；升级候选每条 ≥3 行疑似理由
- [ ] 抽验达标（②类 ≥35 条含实跑 ≥10、①类 ≥15 锚回读）；样本表可重放（SHA-256）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：三分类边界抽验（①②③ 归类错误抽验 ≥20 条）+ 升级候选终裁复核（维持/降级为 watch-only 的理由化）+ 计数收敛
- [ ] 主 agent 汇总：分类统一、升级候选清单定稿

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.9-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `6.9-check-p2p3-watchonly-archive.md`（附录记冻结协议 SHA-256）；index 登记 6.9 行（替换占位行）+ 升级候选以「上报 R1.0 裁决」形态登记（不占 P1 汇总/P2 台账——裁决权在 R1.0）
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（五项：证据完整性/分类正确性/引用路径真实性/计数对应——三分类账平+抽验覆盖率/升级候选完整性）；**通过后** roadmap 6.9 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 分类口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划；抽验含只读测试实跑，免除全量构建。

- [ ] 报告落盘且 694 条三分类全覆盖（①+②+③=694 账平）
- [ ] 抽验达标（②类 ≥35 含实跑 ≥10、①类 ≥15）；升级筛查 ②③类 P2 100% 覆盖
- [ ] index 计数与报告分类台账一一对应；升级候选清单完整上报形态登记
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.9 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（升级候选的裁决与后续修复归 R1.0/R1.x；watch-only 档案为终态归档，无后续义务。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
