# 2357 full-code-review 7.1 nop-ai 增量审计（2026-08-01 后全部非安全维度增量）

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.1（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 03/09/12——契约/错误处理/测试质量；安全维度排除）
> Related: arm 台账 `ai-dev/backlog/audit-remediation-roadmap.md`（v16，2026-08-01 收口，50/50 done——本项审计其后的增量）；`ai-dev/audits/arm-index.md` + 32 份 arm-MA*.nop-ai-*（先例承载）；4.3 报告 P2-4.3-003（ReActAgentExecutor 1935 行心跳/steering 块——已有裁定，本项只做复杂度巡检不重复拆分裁定）。
> 机械基线（主 agent 实测，2026-10-08）：**nop-ai/ 2026-08-01..HEAD 共 147 提交**（其中 2026-09-01 后 66、nop-ai-agent 2026-09-25 后 16——roadmap 所称「重构 25 WI」的落地面，精确 WI 映射 Phase 1 对账落盘）；子模块 22 个（ls 实测）。**千行手写文件现状 = 5 个**（全部 nop-ai-agent/engine：ReActAgentExecutor 1935/DefaultAgentEngine 1300/AgentSessionLifecycle 1182/AgentToolDispatcher 1072/DefaultAgentEngineConfig 1031——roadmap 所称「3 个回涨千行文件」的现势面，回涨判定〔arm 基线时 <1000〕Phase 1 用 git 实测落盘）。arm v16 收口后新增的可审面 = 147 提交的 diff 面（arm 50/50 done 的 已审区不做全维度重审，只审增量）。
> **范围裁定**：① 非安全维度（安全面已由 security-audit 族 + 3.10 增量安全审计覆盖——7.1 Details 明文「非安全维度：契约/错误处理/测试质量」）；② 增量=2026-08-01 后 merge 的 diff（以 git log/diff 为准，不重复 arm 已审代码）；③ 复杂度巡检=千行回涨文件的职责混载/热路径复杂度评估（供 R1.0 参考，不强制拆分——对齐 4.3 裁定形态）。
> **方法学裁定**：① 审计维度=deep-audit 维度 03（API 契约）/09（错误处理）/12（测试质量）+ 复杂度巡检；判级锚 deep-audit 共享前缀 P0–P3；② 增量审计按「提交簇→受影响面→维度扫描」两跳：先 git log 分组聚类（重构波/修复波/配置波），再对每簇 diff 做 triage（安全维度排除、生成物/_gen 排除、test 文件归测试质量维度）；③ **P0 即时通道**照 roadmap；④ still-live 发现编号 `P1-7.1-NNN`/`P2-7.1-NNN` 进 R1.0 输入（P2 走裁决台账需显式开辟）；⑤ 撞车检索面=6.5–6.9 各批次报告的 ai 域发现（6.7 批次 c ai 域 20 条 P1 标定+6.9 升级候选 ai 域条目——同根归并保留溯源）+ arm-index（arm 已修不复报）。
> **验证协议**：报告内每条发现双锚（diff 位点+代码亲读；测试质量类附测试证据）；冻结协议三要素（seed=2357+提交簇清单哈希+triage 样本表）落盘；结束时跑受影响模块 `./mvnw test -pl nop-ai/<受影响模块> -am` 或引用当前绿色基线（本项只读，实跑为验证副作用）。
> Draft Review:（待对抗审查）

## Purpose

执行 roadmap 工作项 7.1：nop-ai 2026-08-01 后全部非安全维度增量审计（契约/错误处理/测试质量 + 千行回涨文件复杂度巡检），产出报告 `7.1-nop-ai-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- arm v16（2026-08-01）50/50 done——其后 nop-ai/ 147 提交为增量面（其中含 2026-09-25 起 nop-ai-agent 重构波）。
- 6.7 批次 c 已标定 ai 域 check P1 20 条（AF 9/live 11→P1×6 新编+P2×2）——本项撞车面对照，不重复标定。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.1-nop-ai-incremental-audit.md`：摘要 + 增量面对账台账（147 提交聚类+WI 映射）+ 千行回涨判定表 + 分维度发现列表（P0–P3，双锚）+ 复杂度巡检段 + 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.1 行 + 独立 closure audit + roadmap 7.1 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 安全维度增量（security-audit/3.10 已覆盖）；arm 已审代码的全维度重审；拆分实施（4.3 P2-4.3-003 已裁定，本项仅巡检）；修复执行；check 系列 ai 域条目重标定（6.7/6.9 已闭）。

## Scope

### In Scope

- nop-ai/ 22 子模块 2026-08-01..HEAD diff 的契约/错误处理/测试质量三维审计
- 千行回涨文件（≥1000 行手写）复杂度巡检
- 只读输入：git 历史、arm 台账、6.7/6.9 ai 域发现

### Out Of Scope

- 安全维度；arm 已审区全维度重审；拆分实施；修复执行；_gen/生成物。

## Execution Plan

### Phase 1 - 增量对账与三维审计（3 agent 并行）

Status: planned
Targets: nop-ai/ 增量 diff 面

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘（委派对账 agent）：① 147 提交全量清单（逐提交 hash/日期/子模块/主题，聚类为重构波/修复波/配置文档波）+WI 映射落盘 ② 千行回涨判定（现势 5 文件 × git 实测 arm 基线时行数，回涨/新生成归类）③ 冻结协议三要素（seed=2357+提交簇清单哈希+triage 样本表）④ 三 agent 分工切面（按子模块组：ai-agent 面/ai-core+api+dao+meta 面/toolkit+skills+tools+rest+mcp+gateway 等其余面）
- [ ] Agent 1/2/3「分面三维审计」：各面对所辖 diff 逐簇 triage（维度 03/09/12），发现逐条双锚（diff 位点+代码亲读），测试质量维度核对新增代码的测试覆盖声明；P0 疑似即时上报
- [ ] 主 agent 抽查质量：冻结表重放+发现抽验（含代码亲读）
- [ ] 产物 `_tmp/fcr-7.1-audit-a/b/c.md` + 提交簇清单 + 千行回涨判定表
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 初稿落盘，每条 still-live ≥9 行完整条目；发现双锚证据
- [ ] 增量面 147 提交全覆盖聚类；千行回涨判定 git 实测落盘；样本表可重放
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁 + already-fixed 抽验 + 撞车复验（6.7/6.9 ai 域+arm-index）+ 净账
- [ ] 主 agent 汇总：四态统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.1-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.1-nop-ai-incremental-audit.md`；index 登记 7.1 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：受影响模块测试绿（或引用绿色基线）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项+审计覆盖核验）；**通过后** roadmap 7.1 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 审计口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划（实跑为验证副作用），免除全量构建；受影响模块测试绿或引用绿色基线。

- [ ] 报告落盘且 147 提交增量面全覆盖（聚类+三维扫描）
- [ ] 每条发现双锚证据可复现；判级依据 deep-audit P0–P3 定义；撞车全量比对留痕
- [ ] index 计数与报告发现清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.1 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x；复杂度巡检结论供 R1.0 参考。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
