# 2358 full-code-review 7.2 nop-stream 增量巡检（CDC WI1–WI8/Debezium 换代/dmlMode 面 + invariant-loop backlog P2 标定）

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.2（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/10/14——数据正确性/状态机/资源管理巡检侧重）
> Related: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`（闭环飞轮 377 行——21 轮审计沉淀，Owner Doc）；`ai-dev/backlog/nop-stream-production-roadmap.md`（活跃建设 40/43 done）；2.8 报告（stream-sql 首审+预存失败锚 :83）/6.7 批次 c stream 四单元 P1 标定（14 条：AF 3/live 8→P1×2 新编+P2×1 等——撞车面对照）/6.4（stream P0×4 already-fixed :53）。
> 机械基线（主 agent 实测，2026-10-08）：nop-stream/ **2026-09-22..HEAD 共 62 提交**（增量巡检面——CDC WI1–WI8/Debezium 2.4→3.7 换代与 jdbc-2pc dmlMode 面的精确映射 Phase 1 落盘）；invariant-loop 飞轮 377 行（**P2- 提及 89 处，open backlog P2×~15 的精确清单 Phase 1 从 roadmap Work Item Status 区枚举落盘**）；nop-stream-connector-debezium 模块在 pom modules。
> **范围裁定**：① 增量面=2026-09-22 后 62 提交 diff（nop-stream 40+ 轮在先审计的已审区不重审——巡检非全量深审）；② invariant-loop backlog P2 标定=枚举 open P2（~15）逐条四态标定（fixed/still-open/watch-only/已由轮内承接——still-open 的 P1 级进 R1.0 输入，P2 级按裁决台账通道）；③ 巡检维度=04（数据正确性）/14（资源管理）为主+增量引入缺陷判定（沿 2357 准入规则：diff 引入/实质恶化才立条，预存缺陷登记撞车台账）。
> **方法学裁定**：① 巡检两跳：git log 聚类（CDC WI 波/Debezium 换代波/修复波）→ 逐簇 diff triage；② 发现编号 `P1-7.2-NNN`/`P2-7.2-NNN`；P0 即时通道；③ 撞车检索面=2.8（stream-sql 面+预存失败锚）/6.4（stream P0×4）/6.7（stream 四单元标定）/6.9（stream 域升级候选）/invariant-loop 各轮 AR 台账；④ 验证协议：发现双锚（diff 位点+代码亲读）；Phase 2 still-live 100% 亲读+其余抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2358+提交簇哈希+样本表）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红先对照 2.8 :83 预存登记（TestStreamConnectorRegistryDiscovery/TestBatchTaskCheckpoint——本批域内！与 6.8 不同，此处预存锚有效）与 6.4 台账，无预存锚按 2331 :93 先例归因登记。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.2：nop-stream 增量巡检（2026-09-22 后 62 提交：CDC WI1–WI8/Debezium 换代/jdbc-2pc dmlMode 面）+ invariant-loop backlog P2×~15 标定，产出报告 `7.2-nop-stream-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- nop-stream 已有 21 轮 deep/adversarial 审计+23 阶段独立审计+invariant-loop 飞轮——本项为增量巡检非全量深审。
- 2.8 已首审 stream-sql；6.7 已标定 stream 四单元 P1；6.4 已闭 stream P0。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.2-nop-stream-incremental-audit.md`：摘要 + 增量对账台账（62 提交聚类+CDC WI 映射）+ invariant P2 标定台账（~15 条四态）+ 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.2 行 + 独立 closure audit + roadmap 7.2 翻 done（closure audit 先于翻 done）。

## Non-Goals

- nop-stream 已审区全量重审；stream-sql 重审（2.8 已闭）；invariant-loop 飞轮自身运营（I1–I6 循环归其 mission）；修复执行。

## Scope

### In Scope

- nop-stream/ 2026-09-22..HEAD 62 提交 diff 巡检（CDC WI 面/Debezium 换代/dmlMode）
- invariant-loop backlog open P2×~15 逐条标定
- 只读输入：git 历史、invariant-loop 台账、2.8/6.4/6.7 报告

### Out Of Scope

- 已审区重审；飞轮运营；修复执行；非 2026-09-22 后增量。

## Execution Plan

### Phase 1 - 增量对账与巡检（2 agent 并行）

Status: planned
Targets: nop-stream/ 增量 diff 面 + invariant backlog

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent）：① 62 提交全量清单（聚类+CDC WI1–WI8 映射+Debezium 版本核验〔2.4→3.7 主张 vs pom 实测〕+dmlMode 面定位）② invariant backlog open P2 精确清单（roadmap Status 区枚举，预期 ~15）③ 冻结协议三要素 ④ 双 agent 分工（Agent 1=增量面巡检/Agent 2=backlog 标定）
- [ ] Agent 1「增量面巡检」：逐簇 diff triage（维度 04/14 为主），发现准入沿 2357 规则，双锚，P0 即时上报 → `_tmp/fcr-7.2-audit-a.md`
- [ ] Agent 2「backlog 标定」：open P2 逐条四态标定（代码亲读+实跑〔预存锚对照〕+撞车比对）→ `_tmp/fcr-7.2-audit-b.md`
- [ ] 主 agent 抽查质量：冻结表重放+发现/标定抽验（含实跑）
- [ ] 产物 `_tmp/fcr-7.2-audit-a/b.md` + 聚类清单
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 初稿落盘；still-live ≥9 行条目；双锚证据
- [ ] 62 提交全覆盖聚类；backlog P2 全量标定；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁 + 标定抽验 ≥30% + 撞车复验 + 净账
- [ ] 主 agent 汇总：四态统一、台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.2-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.2-nop-stream-incremental-audit.md`；index 登记 7.2 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：受影响模块测试绿（或引用绿色基线+预存失败对照）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（五项）；**通过后** roadmap 7.2 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 巡检口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划（实跑为验证副作用），免除全量构建。

- [ ] 报告落盘且 62 提交增量面全覆盖 + backlog P2 全量标定
- [ ] 每条发现/标定双锚证据可复现；撞车全量比对留痕（含预存失败锚对照）
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.2 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x；飞轮继续归其 mission。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
