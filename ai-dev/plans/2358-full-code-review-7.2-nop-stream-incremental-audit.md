# 2358 full-code-review 7.2 nop-stream 增量巡检（CDC WI1–WI8/Debezium 换代/dmlMode 面 + invariant-loop backlog P2 批量标定）

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.2（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/10/14）
> Related: **`ai-dev/backlog/nop-stream-cdc-roadmap.md`（CDC WI1–WI8 权威出处——对抗审查 M1 钉定）**；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`（闭环飞轮 377 行，Owner Doc——**backlog P2 清单在 Follow-up Backlog 各批次 Status 字段，非 Work Item Status 区**〔对抗审查 m1 指位勘正〕）；`ai-dev/backlog/nop-stream-production-roadmap.md`（40/43 done；Debezium 2.4 基线 item 53）；2.8 报告（stream-sql 首审+预存失败锚 :83）/6.7 批次 c stream 四单元 P1 标定/6.4（stream P0×4 :53）。
> 机械基线（主 agent 实测 2026-10-08；对抗审查 agent_c66ea00b 复核勘正）：nop-stream/ **2026-09-22..HEAD = 62 提交**（2026-09-26..10-05；**确定性口径沿 2357 先验**：裸 `--since` 时区敏感，对账取确定性超集+偏差登记〔m3〕）。**四套 WI 编号并存（聚焦复核 M1 二次勘正）**：cdc WI1–8（已全 done，权威=`nop-stream-cdc-roadmap.md`）/stream-sql wi11–wi23（`nop-stream-sql-roadmap.md`）/**eql-cross-db-compat WI11**（970f45a0ba，DAO upsert 原语迁 nop-dao，plan `29-eql-wi11-dao-upsert-primitive`）/**multi-engine-comparison WI8**（cdcda8ad45，窗口事件化，plan `2332-wi8-window-events-maxdelay`）——production roadmap 本窗无 WI 编号提交；映射表 Phase 1 落盘。**stream-sql 波 ≈27–29 提交（~45%）**为 2.8 已审域；CDC 收口 3c542f7046 触域外（nop-dependencies kafka 3.5.0→3.9.2/nop-kernel/nop-message）、WI11 迁 UpsertDialect 入 nop-dao。**Debezium 2.4→3.7.0.Final 换代属实**（nop-message/nop-message-debezium/pom.xml:16）；**dmlMode 面= nop-stream-connector-jdbc**（JdbcTwoPhaseCommitSink/Builder/ConnectorFactory，WI5 提交 706a5b8a4c）。
> **invariant-loop backlog 量级勘正（对抗审查 B1——roadmap 7.2 行「P2×~15」低报 6 倍，记 roadmap 口径缺陷不改文本）**：Follow-up Backlog `Status: todo` **98 行（P3 2 条→open P2 ≈96 条；捆绑条目展开 >120 单元）**。**标定采用 6.9 式批量标定**（非逐条深标定——单会话粒度纪律）：三分类=fixed-by-subsequent-plan（后续 plan/轮已修）/**still-open**（含 P1 级→升级 R1.0；P2 级→P2 裁决台账通道）/watch-only，抽验 ≥20%（≥20 条）含实跑 ≥5。
> **范围裁定（对抗审查 M2/M3/M4 勘正）**：① **提交级已审域标记**：2.8 收口（plan 2326/2327 修复验证，2026-10-05）前合入的 stream-sql 波提交=已审域登记不重审；其后提交=在审（覆盖账 62=在审 N+已审域登记 M 双记，机械核验）；② **域外触碰登记移交**（2357 M4 规则继承）：nop-dependencies kafka 版本面→平台 BOM 责任面登记、nop-dao UpsertDialect 迁入面登记；③ 巡检维度=**04/10/14**（m2 勘正：2PC/checkpoint/offset 为状态机维度 10 高发面）；④ **发现准入规则（2357 形态）**：增量引入/实质恶化才立条，预存缺陷登记撞车台账；⑤ **预存锚精确归属（对抗审查 M4 勘正）**：TestBatchTaskCheckpoint 实位 nop-batch-core（2.8 自判归 3.4 域）——红测试归因 3.3 台账非本批；**TestStreamConnectorRegistryDiscovery 红为窗内 WI4 引入且 HEAD 仍红（SINGLE_INSTANCE 断言 vs PARALLEL 工厂）=本批最确定在窗发现，必须审计而非豁免**。
> **方法学裁定**：① 巡检两跳：聚类→逐簇 diff triage；② 发现编号 `P1-7.2-NNN`/`P2-7.2-NNN`；P0 即时通道；③ 撞车检索面=2.8/6.4/6.7/6.9/invariant 各轮 AR 台账/cdc-roadmap；④ 验证协议：发现双锚；Phase 2 still-live 100% 亲读+其余抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2358+提交簇哈希+backlog 样本表）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红先对照 2.8 :83 的 TestStreamConnectorRegistryDiscovery（在窗回归，见上）与 6.4 台账，其余红按 2331 :93 先例归因登记。
> Draft Review: agent_c66ea00b 首轮 **1 Blocker（B1 backlog open P2 实测 ~96 条〔展开 >120 单元〕vs 主张 ~15——6 倍低报，记 roadmap 口径缺陷）** + 4 Major（M1–M4）+ 4 Minor（m1–m4）——本轮全部修复（详见各节勘正注记）；聚焦复核 9 项 PASS 但发现 M1 修复引入新张冠李戴（production WI11 实为 eql-cross-db-compat WI11；窗内编号实为四套体系），一行修正后二次核认 **PASS 零问题**（commits `4f5ff5c8ce`/`ba822d061a`；跨 worktree plan 文件注记为非阻塞观察）——转 active。

## Purpose

执行 roadmap 工作项 7.2：nop-stream 增量巡检（62 提交：CDC WI1–WI8/Debezium 换代/jdbc-2pc dmlMode 面，按提交级已审域标记划分）+ invariant-loop backlog open P2（~96 条）批量标定，产出报告 `7.2-nop-stream-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- nop-stream 已有 21 轮 deep/adversarial+23 阶段独立审计+飞轮——本项为增量巡检+backlog 批量标定。
- 2.8 已首审 stream-sql（收口 2026-10-05）；6.7 已标定 stream 四单元 P1；6.4 已闭 stream P0。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.2-nop-stream-incremental-audit.md`：摘要 + 增量对账台账（62 提交聚类+三 roadmap WI 映射+已审域标记）+ backlog P2 批量标定台账（~96 条三分类+抽验）+ 发现列表（双锚）+ 域外触碰登记 + 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入（backlog still-open P1 级升级上报）；P0 即时通道。
- index 登记 7.2 行 + 独立 closure audit + roadmap 7.2 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 已审域提交重审（登记不重审）；stream-sql 重审（2.8 已闭）；飞轮自身运营（归其 mission）；修复执行。

## Scope

### In Scope

- nop-stream/ 62 提交中「在审」提交的 diff 巡检（维度 04/10/14；已审域提交登记）
- invariant-loop backlog open P2（~96 条）批量标定（三分类+抽验 ≥20% 含实跑 ≥5）
- 只读输入：git 历史、cdc-roadmap、invariant-loop 台账、2.8/6.4/6.7 报告

### Out Of Scope

- 已审域提交重审；飞轮运营；修复执行；非本窗口增量；域外路径审计（登记移交）。

## Execution Plan

### Phase 1 - 增量对账、巡检与 backlog 标定（2 agent 并行）

Status: in progress
Targets: nop-stream/ 在审增量面 + invariant backlog

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（ba822d061a）、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent）：① 62 提交全量清单（确定性超集口径；聚类+**三 roadmap WI 映射表**+**提交级已审域标记**〔2.8 收口前 stream-sql 波=登记不重审〕+**域外触碰登记移交**）② invariant backlog open P2 全量清单（Follow-up Backlog 区枚举 ~96 条展开计数）③ 冻结协议三要素 ④ 覆盖账 62=在审 N+已审域 M 落盘
- [ ] Agent 1「增量面巡检」：在审提交逐簇 diff triage（维度 04/10/14；发现准入沿 2357 规则；**TestStreamConnectorRegistryDiscovery 在窗回归必审**）→ `_tmp/fcr-7.2-audit-a.md`
- [ ] Agent 2「backlog 批量标定」：open P2 三分类（fixed-by-subsequent-plan/still-open/watch-only；代码亲读+抽验实跑 ≥5；撞车比对 2.8/6.7/6.9/AR 台账）→ `_tmp/fcr-7.2-audit-b.md`
- [ ] 主 agent 抽查质量：冻结表重放+发现/标定抽验（含实跑 ≥1）
- [ ] 产物 `_tmp/fcr-7.2-audit-a/b.md` + 聚类清单 + backlog 清单
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 审计产物落盘；still-live ≥9 行条目；双锚证据
- [ ] 覆盖账 62=N+M 账平；backlog P2 全量三分类；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁 + backlog 标定抽验 ≥20% + 撞车复验 + 净账
- [ ] 主 agent 汇总：四态统一、台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.2-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.2-nop-stream-incremental-audit.md`；index 登记 7.2 行 + 仍 live 进 P1 汇总或 P2 台账（backlog still-open P1 级升级上报形态）
- [ ] 定向验证锚点：受影响模块测试绿（或引用绿色基线+在窗回归对照）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（六项：证据完整性/严重性标定/引用路径真实性/计数对应——覆盖账+backlog 全量/已审域标记合规——无违规重审无静默跳过/流程状态）；**通过后** roadmap 7.2 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 巡检口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划（实跑为验证副作用），免除全量构建。

- [ ] 报告落盘且在审增量面全覆盖（覆盖账 62=N+M）+ backlog P2 全量三分类
- [ ] 每条发现/标定双锚证据可复现；撞车全量比对留痕（含在窗回归对照）
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入（backlog still-open P1 级升级上报）
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.2 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x；backlog still-open P2 级 → P2 裁决台账通道；飞轮继续归其 mission。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
