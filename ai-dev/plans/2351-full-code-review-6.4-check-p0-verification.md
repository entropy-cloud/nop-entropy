# 2351 full-code-review 6.4 check 系列 P0×36 逐项复核标定

> Plan Status: draft
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.4（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: Owner=`ai-dev/audits/check/SUMMARY.md`（36 P0 权威清单，安全类 9 + 数据正确性 16 + 流处理/状态类 4 + 功能完全失效类 7）；Roadmap Details：「check 系列 36 个 P0 逐项标定：still-live / already-fixed / false-positive / duplicated；check/SUMMARY 记录部分 P0 曾在 fix-ai-check 分支处置——标定时以该处置记录为输入、**以 master 当前代码为准**复核 live-ness；仍 live 的全部进入 R1.0 输入」。
> 机械基线（主 agent 实测，2026-10-07，读 check/SUMMARY.md）：36 P0 全量清单在案（#1-36 逐条含模块/问题/影响）；**SUMMARY 自载修复进展**：fix-ai-check 分支 2026-08-21（6 单元 118 条含其全部 P0/P1）+ 2026-08-22（**剩余 32 条 P0 清零**，安全 8/数据正确性 15/流处理失效 9，逐条红测试→修复→绿闭环）——即 SUMMARY 自称 36/36 已在分支处置；6.4 的核心问题 = **这些修复是否已在 master**、逐条 live-ness 为何。上游轮次内已标定面（本 round 深审的撞车先例，逐条引用不重判）：#13（2.1 check2 游标域）、#14（2.3 already-fixed 双探针）、#15/16/17（3.2 continuation-skip 等三 P0 already-fixed）、#18（3.5 锁 P0 already-fixed 反向）、#21（3.8 :27 CANCEL_SUCCESS already-fixed TestTccRunner 10/10）、#26-29（2.8/7.2 域——批次4 修复 commit 2de622fb6a 含 stream reduce/CEP/2PC/source 重启；**2.8 报告口径**）、#30（2.3 JdbcBatcher 实跑）、#32/33/34（3.9 NET 族三 P0 already-fixed 实测 :21/:45/:70）、#35/36（4.5 nop-utils 23/23 已修 P0×2）。**粗判轮内已标定 ≈17-20 条，净新复核 ≈16-19 条**（Phase 1 精确对账落盘）。
> 只读审计（P0 即时通道语义在此反转：本项发现的 still-live P0 = 直接移交 R1.0 输入清单，不就地修复——roadmap 6.4 原文）。
> **方法学裁定**：① 标定四态定义（roadmap）：still-live（问题在 master 复现）/ already-fixed（修复在 master 在位，附 commit/测试证据）/ false-positive（审计前提不成立）/ duplicated（与他条同根）；② 判定证据标准：亲读 master 位点代码 + 红测试存在性（fix-ai-check 闭环声称附回归测试——验证测试在 master 且能失败）双锚，仅代码亲读不足以判 already-fixed 时降级为「修复待验证」；③ 轮内已标定条目抽验 30% 防漂移（重验 live-ness，不重开判级）；④ SECURITY 类（#1-#9）优先且全量亲读。
> Draft Review: （待独立子 agent 对抗审查）

## Purpose

执行 roadmap 工作项 6.4：check 系列 36 个 P0 逐项标定（四态），产出报告 `6.4-check-p0-verification.md`，仍 live 者全部进入 R1.0 输入。

## Current Baseline

- 36 P0 清单 + fix-ai-check 处置记载见头部注记（check/SUMMARY.md 为权威输入，各单元报告条目末尾「处置（fix-ai-check 分支）」标注为逐条输入）。
- 轮内撞车先例 ≈17-20 条（见头部注记映射），Phase 1 精确对账。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（含测试运行——跑单测验证红测试为只读副作用）；工作树在前序收口 commit 后干净。
- 撞车面（Phase 2 必查）：2.1/2.3/3.2/3.5/3.8/3.9/4.5（轮内标定引用）；2.8/7.2（stream 域——注意 7.2 尚未执行，stream 四条 #26-29 以 2de622fb6a 批次修复 commit 与 2.8 报告口径为输入，**不等待 7.2**）；6.1/6.2（若 still-live 位点属空壳/吞异常同型，交叉引用）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.4-check-p0-verification.md`：摘要 + 36 行标定台账（编号/模块/原问题/四态标定/证据/处置去向）+ 仍 live 清单 + 驳回台账 + 裁决。
- **36 条全量标定**（三分工作流）：
  1. **轮内已标定条目（≈17-20）**：引用轮报告结论 + 抽验 30% live-ness 防漂移（Phase 1 对账落盘精确清单）。
  2. **净新复核条目（≈16-19）**：逐条亲读 master 位点 + 红测试存在性验证（fix-ai-check 声称附回归测试——测试在 master 吗？能失败吗〔读断言逻辑即可，跑测试为可选加强〕）。
  3. **SECURITY 类 #1-#9 全量亲读**（优先级最高，无论轮内标定与否全验 live-ness）。
- still-live 者逐条附 R1.0 输入条目（问题/位点/修复方向/严重级复核——check 时代的 P0 判级按当前共享前缀复核，可能降级）；index「P0 发现追踪」表登记（如仍 live 者触发即时通道语义——**按 roadmap 6.4 定义仅移交 R1.0 不就地修复**，登记表仅记标定结果）。
- index 登记 + 独立 closure audit + roadmap 6.4 翻 done（closure audit 先于翻 done）。

## Non-Goals

- P1 抽样标定（6.5-6.8）；P2/P3 归档（6.9）；still-live 修复执行（R1.x）；stream 域等待 7.2。

## Scope

### In Scope

- check/SUMMARY.md 36 P0 全量四态标定 + 仍 live 清单
- 只读输入：check/ 各单元报告（fix-ai-check 处置标注）、轮内深审报告标定、git 历史（fix-ai-check 分支处置的 merge 状态——`git log`/`git branch --contains` 验证修复 commit 是否可达 master）

### Out Of Scope

- P1/P2/P3 标定；修复执行；7.2 域等待。

## Execution Plan

### Phase 1 - 对账与三分标定（3 agent 并行）

Status: planned
Targets: 36 P0 位点（master live code + check/ 处置记录）

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘：36 行宇宙表（编号/模块/问题/轮内标定与否/分配）——轮内标定映射精确化（头部粗判 → 逐条核对轮报告）
- [ ] Agent 1「SECURITY #1-9 全量亲读 + 净新数据正确性类」：#1-9 逐条 live-ness + 分配到的净新条目
- [ ] Agent 2「净新复核主体」：分配到的净新条目逐条（代码亲读 + 红测试存在性 + fix-ai-check 分支 merge 状态验证）
- [ ] Agent 3「轮内已标定抽验 30% + 其余净新」：抽验条目 live-ness 重验（防漂移）+ 分配净新条目
- [ ] 主 agent 抽查质量：标定抽验 ≥8 条独立复判（含 ≥2 条 SECURITY）+ 台账对账（36 行全覆盖零遗漏）
- [ ] 产物 `_tmp/fcr-6.4-checkp0.md`（三 agent 分节）+ 36 行标定台账

Exit Criteria:

- [ ] 初稿落盘，每条标定含四态归属 + 双锚证据（代码位点 + 测试/commit）；still-live 条目 ≥9 行完整条目
- [ ] 36 行台账零遗漏；轮内标定抽验 ≥30% 完成
- [ ] No owner-doc update required（只读——still-live 移交 R1.0）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 条目 100% 亲读复核 + already-fixed 抽验 ≥30%（防「修复不在 master 误判已修」）+ 撞车核对（轮内标定引用逐条对）+ 判级复核（check 时代 P0 判级按共享前缀复核——still-live 者可能降级）
- [ ] 主 agent 汇总：四态统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.4-checkp0-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.4-check-p0-verification.md`；index 登记 6.4 行（替换占位行）+ still-live 清单并入「P0 发现追踪」表（标定性质登记）或 P1 汇总（降级者）
- [ ] 定向验证锚点：标定抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——36 行台账 ↔ 报告 ↔ index 对应）；**通过后** roadmap 6.4 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（6.1 已覆盖空壳面）

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试运行（可选加强），免除全量构建。

- [ ] 报告落盘且 36 条全量标定——覆盖度量：36/36 行（SECURITY 9 全亲读 + 净新 100% + 轮内抽验 ≥30%）
- [ ] 每条标定双锚证据可复现；四态归属依据 roadmap 定义；误报校准留痕
- [ ] index 计数与报告标定台账一一对应；仍 live 清单完整移交 R1.0
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.4 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；still-live 修复归 R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
