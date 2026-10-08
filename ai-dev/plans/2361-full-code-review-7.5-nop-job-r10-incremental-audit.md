# 2361 full-code-review 7.5 nop-job R10 发现标定 + 增量巡检

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.5（MA7，Owner Doc=—）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/09/14 巡检侧重）
> Related: **`ai-dev/audits/2026-06-19-0931-adversarial-review-nop-job/01-open-findings.md`（R10 发现清单：17 条 open〔AR-83~99〕，summary 表 P1 :441/P2 :442/P3 :443）**——R10=nop-job 第 10 轮 adversarial review（2026-06-19）；既有审计区（2026-05/06 deep+adversarial 多轮）不重审。
> 机械基线（主 agent 实测 2026-10-08；**对抗审查 agent_eed12f8e 勘正 M1——R10 实为 17 条**）：R10（AR-83~AR-99）= **5 P1（AR-83~87：资源双重计算/null-cost NPE/所有权丢失后仍执行/批处理 fail-vs-defer/死错误码静默兜底）+8 P2（AR-88~95）+4 P3（AR-96~99）=17 条**（报告 ：441/:442/:443 与 ：451 自述「新增 17 项」；权威矩阵 :30/:138 把 17 条全路由给 7.5——**初稿「13 条」静默排除 P3×4 系口径缺陷**）；summary 表锚=P1 :441/P2 :442（m1 勘正）。修复窗口证据：2026-06-19..HEAD 108 提交（承接 plan 339 completed 修复 AR-86/87/98、plan 344 引 AR-86 等）。**nop-job/ 2026-08-01 后 50 提交**（增量巡检面；确定性超集口径沿 2357〔m3/m4 勘正：显式零点窗口+偏差登记；域内域外标记〕）。**header 冻结标定对象（m7）**：AR-83~AR-95（P1+P2 层）+AR-96~99（P3 层批量归 watch-only——规则 #3）。
> **范围裁定**：① R10 17 条逐条四态标定（already-fixed/still-live/duplicated/false-positive；发现准入与双锚沿 2357 形态；R10 报告自带「审查者亲读复核」声明仍须独立亲验——以当前代码为准）；② 增量巡检=50 提交 diff triage（维度 04/09/14 为主）；③ 撞车检索面（对抗审查 M3/m5 勘正）：**6.6 批次 b（nop-job 唯一 check 归属批次——plan 2353 已标定 nop-job P1=3，对照 `ai-dev/audits/check/nop-job.md` 与 R10 同 coordinator/worker/dao 面）**/既有 nop-job 审计区 11 目录（2026-05-18~06-19）/6.9（nop-job P2/P3 全量处置对照——54 单元全覆盖）/arm 台账（nop-job 无 arm——对冲写法保留）。
> **方法学裁定**：① 标定+巡检双 agent（R10 17 条标定 / 50 提交增量巡检）；② 发现编号 `P1-7.5-NNN`/`P2-7.5-NNN`（R10 条目标定结果沿用 R10 ID 承载，仍 live 的 P1 进 R1.0 输入）；③ P0 即时通道；④ 验证协议：双锚；Phase 2 still-live 100% 亲读+标定抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2361+清单哈希）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 plan 2331 **:92** 先例归因登记（m6 勘正）；**增量面确定性口径沿 2357**（裸 --since 时区敏感→显式零点窗口实测+偏差登记，覆盖账容差 ±2 并登记）。
> Draft Review: agent_eed12f8e 首轮 **4 Major（M1 R10 实为 17 条含 P3×4 静默排除/M2 Exit Criteria 预设结果/M3 撞车面漏 6.6 唯一归属/M4 确定性口径）+ 7 Minor（m1–m7）**——本轮修复；聚焦复核发现 M1 三处残留（:6/:8/:9）+M2 半修（≥9 预设未删）+**N1 新引入 5 处虚假 [x]**（draft 状态误勾已完成项——执行脚本错误），全部修正（5 处回退+17 条全链一致+预设删除）后二次核认 **PASS 零问题**（commits `c097aa13cd`/`6d0cd51142`）——转 active。

## Purpose

执行 roadmap 工作项 7.5：nop-job R10 发现（5 P1/8 P2）修复状态标定 + 增量巡检（50 提交），产出报告 `7.5-nop-job-r10-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- R10 清单 17 条 open（2026-06-19 审查者亲读复核声明）；其后 nop-job/ 108 提交（2026-06-19..HEAD——修复窗口；其中 2026-08-01 后 50 为本项增量巡检面〔m2 勘正〕）。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.5-nop-job-r10-incremental-audit.md`：摘要 + R10 17 条标定台账 + 增量巡检台账 + 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.5 行 + 独立 closure audit + roadmap 7.5 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 既有审计区重审；修复执行；非 R10 清单（17 条外）与增量面的发现；P3×4 深标定（批量归 watch-only——规则 #3）。

## Scope

### In Scope

- R10 17 条标定（P1+P2 13 条四态独立亲验+P3×4 批量归 watch-only）
- nop-job/ 2026-08-01 后 50 提交（确定性口径）diff 巡检
- 只读输入：git 历史、R10 报告、既有审计区对照

### Out Of Scope

- 既有审计区重审；修复执行；非本范围发现。

## Execution Plan

### Phase 1 - 标定与巡检（2 agent 并行）

Status: completed
Targets: R10 清单 + 增量面

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（6d0cd51142）、`git status` 干净后开始
- [x] 主 agent 对账落盘（委派对账 agent）：① **17/17 全量登记**（AR-83~87=P1×5/88~95=P2×8/96~99=P3×4 与 summary 锚逐条 diff 一致；P3×4 按规则 #3 批量归 watch-only 在册无静默排除；审查者亲读声明标记不采信终局须独立亲验）② **承接对账 17/17 全覆盖**——plan 267/268/269（全 completed）+保持性 325/338/339/344；残留声明 2 处（AR-92 filesort watch-only/AR-93 deferred）交 Agent 1 复核；防撞车（2026-08-13 invariant 系 AR-9x 系 nop-code 另一套 ID 空间不计入）③ **50 提交零偏差**（域内 30/域外 20；波次 W1~W10；author-date 口径 49 已登记；`--` 后置选项伪影警告登记）④ 冻结协议（clusters `1dc048c2…`；seed=SHA(plan:csv) 推导细节登记——采样 AR-94/84/87/93/92+巡检 seq 18/43/28/3/49 已由 Agent 1 实算重放一致）⑤ 双 agent 分工确认
- [x] Agent 1「R10 标定」：**四态分布 already-fixed 17 / still-live 0 / dup 0 / FP 0**——P1×5 全亲读锚点+回归测试在库（AR-87 较 R10 建议更强：executorKind 完全退出路由）；P2×8 全 fixed（AR-92 filesort=watch-only residual 声明属实/AR-93 deferred 前提亲验已修）；P3×4 快速核验全成立；样本 5 条实跑/深验（TestJobWorkerScanner 16/16+TestJobDispatcherScannerRouting 8/8+TestJobCoordinatorScanner 27/27 全绿）；撞车零冲突（6.6 check 16 条与 R10 0 同缺陷；3 处同面项为组合性强化非重复）→ `_tmp/fcr-7.5-audit-a.md`
- [x] Agent 2「增量巡检」：**编号发现 0 条**（零 P0/P1/P2/P3 立条）+Watch 注记 3 条（W-1 固定排序窗口尾部饥饿 P3-watch/W-2 raced 分支静默丢弃/W-3 dim-21 测试污染）；域外 20 登记逐行；样本 5 深检全净（REQUIRES_NEW 自调用生效性亲验等）；窗内 5 项自消解链登记防误立 → `_tmp/fcr-7.5-audit-b.md`
- [x] 主 agent 抽查质量：冻结 clusters 重放一致（1dc048c2…）+ AR-87 显式抛错锚亲读成立 + W-1 固定窗口亲读成立（seed 推导细节差异登记——采样已实算重放一致）
- [x] 产物 `_tmp/fcr-7.5-audit-a/b.md` + 清单
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（两 agent 一致零 P0 疑似）

Exit Criteria:

- [x] 审计产物落盘；still-live 0 条（按实际计数落盘）；双锚证据
- [x] 17 条全量标定+增量面全覆盖（50 零偏差）；样本表可重放
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁+标定/发现抽验 ≥30%+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.5-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.5-nop-job-r10-incremental-audit.md`；index 登记 7.5 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项）；**通过后** roadmap 7.5 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划（实跑为验证副作用），免除全量构建。

- [ ] 报告落盘且 17 条全量标定+增量面全覆盖（确定性口径）
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.5 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
