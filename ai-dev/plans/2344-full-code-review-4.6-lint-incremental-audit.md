# 2344 full-code-review 4.6 nop-lint 增量首审（v6）

> Plan Status: active
> Last Reviewed: 2026-10-06
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 4.6（MA4，v6 增补）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 02/09/16 + 共享前缀）
> Related: Roadmap Details：「nop-lint 三个新子模块（js 2026-09-22 / maven-plugin 2026-09-24 / graphql 2026-09-25 入库）：语言适配表与 CLI 扩展、maven 插件 check goal 零新管线语义、路径三分法与规则白名单 fail-closed 语义；tool-replacement roadmap 收口（2026-10-03 账本对账）后的残留裁定面复核」。
> 机械基线（主 agent 实测）：nop-lint-js 10/nop-lint-maven-plugin 1/nop-lint-graphql 4/nop-lint-core 126/nop-lint-nop 1 main Java；设计文档 `ai-dev/design/nop-lint/`（15 文件：00-nop-lint-design/pattern-dsl/rule-library/execution-engine/migration/suppression/12-check-scripts-migration-manifest 等——**注意 nop-lint/nop-lint-java/ 是 Maven 产品模块非文档目录**）。
> 只读审计（P0 走异步注入）。**增量首审性质**：nop-lint-core 126 文件已有 tool-replacement roadmap 多轮治理（2026-10-03 账本对账收口）——本项聚焦三个新子模块深审 + core 残留裁定面复核，非 core 全量深审。
> **标定口径**：新子模块无 check 基线（首审）；core 残留面以 tool-replacement roadmap 账本为对照基线。
> Draft Review: agent_d05e3a9d 首轮 4 Major（F1 设计文档路径幻影——nop-lint-java 是产品模块非文档目录，实路径 ai-dev/design/nop-lint/ 15 文件；F2 nop-lint-java 18 main scope 洞——显式 Out Of Scope 豁免；F3 统一账本 tool-replacement-ledger.md「残余范围」列漏入；F4 Closure Gates 首条预勾选取消）+ 4 Minor（P0 移 Phase 1/-am 先例锚定/core 消费点读取面/三分法落点提示）——本轮修复后 agent_3cee8c23 聚焦复核全 PASS（Out Of Scope 小节顺带补 nop-lint-java）——进入执行。

## Purpose

执行 roadmap 工作项 4.6：nop-lint 增量首审（三个新子模块深审 + tool-replacement 收口后残留面复核），产出报告 `4.6-lint-incremental-audit.md`。

## Current Baseline

- 三个新子模块：nop-lint-js（10 main，2026-09-22 入库）/nop-lint-maven-plugin（1 main，09-24）/nop-lint-graphql（4 main，09-25）。**nop-lint-java（18 main + 13 test）显式 Out Of Scope**——语义引擎适配层（tool-replacement 治理 + nop-lint 自有 roadmap M1–M6 覆盖；残留面复核涉及该模块时仅作读取面）。
- nop-lint-core 126 main（tool-replacement roadmap 多轮治理后）；nop-lint-java 18 main（Out Of Scope 见上）；nop-lint-nop 1 main（空壳合法例证——6.1 先例）。
- 设计文档：`ai-dev/design/nop-lint/` 15 文件（同上）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；**Phase 1 机械基线须 git log 复核 2026-09-22 后 nop-lint 组产品代码变更并登记**（三个新子模块本身就是基线后入库）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.6-lint-incremental-audit.md`：摘要 + 标定/裁定台账 + P0–P3 发现（`P<级别>-4.6-<序号>`）+ 驳回台账 + 裁决。
- **三个新子模块深审**（roadmap 点名）：js（语言适配表与 CLI 扩展——JS 规则执行面）、maven-plugin（check goal 零新管线语义验证——是否真复用 core 管线）、graphql（GraphQL 规则面）；**路径三分法与规则白名单 fail-closed 语义**（roadmap 点名——路径解析三分法在三个新子模块的一致性、规则白名单未命中时 fail-closed 还是 fail-open）。
- **tool-replacement 收口后残留裁定面复核**：三层账本对账——统一账本 tool-replacement-ledger.md「残余范围」列（PMD ImplicitSwitchFallThrough deferred/check-mjs 3 deferred+7 maintain+7 exclude/SonarQube not-replaceable/EP-NullAway 待裁/SpotBugs-ArchUnit out-of-principle）+ 行级账本 checkstyle-pmd-migration.md 26 行 + design 12 manifest（mjs 面 24 行）+ 工具级终裁 roadmap——逐项现状复核。
- index 登记 + 独立 closure audit + roadmap 4.6 翻 done。

## Non-Goals

- nop-lint-core 全量深审（tool-replacement 多轮治理已覆盖；仅残留面）；**nop-lint-java（18 main——Out Of Scope 豁免理由见 Current Baseline；残留面涉及时只读）**；规则语义正确性全面验证（抽查制）；nop-lint-nop（合法空壳）。

## Scope

### In Scope

- `nop-lint/nop-lint-js/**`、`nop-lint/nop-lint-maven-plugin/**`、`nop-lint/nop-lint-graphql/**`（深审）
- `nop-lint/nop-lint-core/**`（仅残留裁定面复核；**三个子模块深审所需的 core 消费点〔TargetScanner/CliOptions/LintLanguage registry/CheckRunner〕允许作为深审读取面**）
- 只读输入：`ai-dev/design/nop-lint/`（15 文件设计组）、`nop-lint/docs/tool-replacement-ledger.md`（**统一账本——「残余范围」列所在，带门禁 check-lint-tool-replacement-ledger.mjs**）、`nop-lint/docs/checkstyle-pmd-migration.md`（行级账本 26 行）、`ai-dev/backlog/nop-lint-tool-replacement-roadmap.md`（工具级终裁 roadmap）

### Out Of Scope

- nop-lint-core 全量；nop-lint-nop；规则语义全面验证；demo。

## Execution Plan

### Phase 1 - 深挖（单 agent 分批）

Status: in progress
Targets: 三个新子模块 + core 残留面

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净按 4.1 口径处理）
- [ ] 机械基线：三新子模块文件清单、core 残留面账本对账清单、git log 变更登记、测试资产 find 实测
- [ ] 深审 agent：三新子模块深审（js 适配表/CLI 扩展 + maven-plugin check goal 管线复用验证 + graphql 规则面）+ 路径三分法/白名单 fail-closed 语义横切核对 + core 残留面账本复核
- [ ] 裁定复核迭代：初稿完成后主 agent 抽查质量（以「三个新子模块全深审 + fail-closed 语义有结论 + 残留面有对账」为完成判据）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-4.6-lint.md`

Exit Criteria:

- [ ] 初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [ ] fail-closed 语义核对结论 + 残留面对账结论落盘
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 账本对账独立验证
- [ ] 主 agent 汇总：判级统一、与 tool-replacement roadmap（successor 域）协调
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-4.6-lint-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `4.6-lint-incremental-audit.md`；index 登记 4.6 行（如有 P2 走「P2 裁决台账」通道）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl <审计域有测试资产的模块列表，Phase 1 实测后填写>`（不带 -am——**显式偏离裁定：-am 拉入两个已归因预存失败〔nop-stream-runtime TestStreamConnectorRegistryDiscovery、nop-sys-dao 测试编译断裂，2.8/3.5 域登记，同 plan 2339-2343 先例〕**；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；通过后 roadmap 4.6 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因预存并登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且三新子模块/fail-closed 语义/残留面对账全覆盖——覆盖度量：js/maven-plugin/graphql 各自深审结论 + fail-closed 语义核对表 + 残留面对账表
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 4.6 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
