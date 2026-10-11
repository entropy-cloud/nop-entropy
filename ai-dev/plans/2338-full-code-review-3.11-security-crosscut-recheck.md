# 2338 full-code-review 3.11 全仓安全横切复核

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.11（MA3）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`（登记口径）+ `ai-dev/skills/deep-audit-prompts.md`（共享前缀）
> Related: owner doc `ai-dev/audits/security-audit/2026-09-19-consolidation-summary.md`（**本项权威输入**：item 14 = ledger 中 14 条 successor-deferred MEDIUM）；Roadmap Details：「security-audit consolidation-summary 中 item 14 的 MEDIUM×14 逐项现状（已修/仍 live/缓解生效）+ 全仓新增敏感面（密钥/日志/路径拼接）grep 复扫」。
> **item 14 名册**（14 条 successor-deferred）：F-API1-1（unmapped error 公开默认）、F-API4-1（/px/ 全 header 透传）、F-WF-01-1（委托锚死代码/auth 侧替换）、F-WF-01-2（check-start-auth 未调用）、F-WF-01-3（notifySubFlowEndAsync 无鉴权）、F-WF-02-1（task state 明文持久化）、F-AI1-1（账号切换 auth-header 累积）、F-AI2-2（双沙箱接缝未接线）、F-N1-1（Apache 重定向全 header 转发）、F-N1-2（OkHttp useSsl trust-ALL）、F-N2-2（MQTT 无 topic ACL）、F-N3-2（流控制面 RPC 无鉴权）、F-F2-1（NopFileRecord CRUD 无对象级 auth）、F-D1-1（queryEntityData 返回全部列）。
> **撞车对照现成结论**（本轮次已深审，直接引用核对不重开）：3.1（nop-wf 深审）已标定 F-WF-01-1/2/3、F-WF-02-1 四条 item-14 相关条目（F-WF-03-1 系 batch-4 非 item 14）；3.8 的 P1-3.8-001/002 实质承接 F-F2-1 同族（报告未点名，标定表记录映射）；3.9 已标定 F-N1-2 撞车（OkHttp trust-all adjudicated 按 plan 2283 + P3-3.9-017 keyStore 信任锚残留）。未覆盖条目（F-API1-1/F-API4-1/F-AI1-1/F-AI2-2/F-N1-1/F-N2-2/F-N3-2/F-D1-1 共 8 条）为本项实测重点。
> 只读审计（P0 走异步注入）；consolidation-summary 与 AUTH/CRED/WF/NET/AI/API/FILE/DATA 源报告为点名例外输入。
> **标定口径**：逐项现状三态判定（已修/仍 live/缓解生效）+ 证据 file:line；仍 live 且升 P0/P1 的转 R1.0 输入；缓解生效的登记缓解机制。grep 复扫注明口径（模式+排除项）。**锚点失效规则：源锚点经重构更名/删除时，须重定位等价代码路径后再下三态判定，禁止以锚点缺失代替判定。**
> Draft Review: agent_6ada2ff0 首轮 1 Blocker（F-N1-1 落空——14 条仅分配 13 条，3.9 对 F-N1-1 零标定已证伪，归实测组 6+8=14 自洽）+ 6 Major（F-D1-1 原锚点已死换新锚点+锚点失效规则、F-WF-02-1 补 3.2 去重必读、baseline 计数 9 已修=3+6 勘正、Phase 3 补 security-audit-roadmap item 14 行 owner-doc 同步、仍 live P2 显式裁决例外通道、Non-Goals 括号例外落 checklist）+ 8 Minor（撞车引用精确化/预存失败悬空引用删除/In-Scope 域清单对齐/grep 窗口写死/去重清单措辞/修复方向要素/相交面点名）——本轮修复后 agent_268dc927 聚焦复核：B1 残留两处（:59 分组 7+7、:40 nop-orchestrator 幽灵模块）已补勘正，其余全 PASS——进入执行。

## Purpose

执行 roadmap 工作项 3.11：全仓安全横切复核（item 14 MEDIUM×14 逐项现状标定 + 全仓新增敏感面 grep 复扫），产出报告 `3.11-security-crosscut-recheck.md`。

## Current Baseline

- consolidation-summary（2026-09-19）：ledger CRITICAL 0/HIGH 1（F-AI4-1 已 batch-4 修复）/MEDIUM 23（**9 已修〔3 FIXED batches1-2 + 6 batch-4，item 13 done〕+ 14 successor-deferred**）+ LOW ~31（5 fixed rest watch/constraint）。
- 本轮次已有安全域深审结论可撞车对照：3.1（wf 六条）、3.8（file 权限面 P1-3.8-001/002；P1-3.8-003 系引擎执行表非 file 域）、3.9（gateway/network + OkHttp trust-all）、3.10（auth/credential + MFA codec）。
- 测试资产：以 find 实测为准。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS。本项为纯标定与扫描型工作项，无模块测试锚点（验证以复核员独立复扫为锚）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.11-security-crosscut-recheck.md`：摘要 + item 14 逐项标定表 + grep 复扫台账 + P0–P3 发现（`P<级别>-3.11-<序号>`）+ 驳回台账 + 裁决。
- **item 14 逐项现状标定**（roadmap 点名）：14 条逐项三态判定（已修/仍 live/缓解生效；**仍 live 且 P2 走报告显式 P2 裁决例外通道，逐项转 R1.0 裁决**）——撞车对照 6 条（F-WF-01-1/2/3 引 3.1；F-WF-02-1 引 3.1（回灌）+ **3.2（维持结论与 persist 族 P2，去重必读）**；F-F2-1 由 3.8 P1-3.8-001/002 实质承接〔3.8 报告未点名 F-F2-1，标定表须记录映射〕；F-N1-2 引 3.9/plan 2283）+ 实测 8 条（F-API1-1 默认值现状〔CoreConfigs.java:225-226 默认 true + ErrorMessageManager.java:334 消费点〕、F-API4-1 /px/ header 链、F-AI1-1 auth-header 累积、F-AI2-2 沙箱接缝、**F-N1-1 Apache 重定向全 header 转发〔无现成结论，live 实测：apache 模块零 redirect 处理 vs JDK/OkHttp 消费 isFollowRedirects〕**、F-N2-2 topic ACL、F-N3-2 控制面 RPC〔MessageRpcServer/StreamControlRpcServer/stream-control-rpc.beans.xml〕、F-D1-1 列裁剪〔**原锚点 NopMetaTableQueryAction 已随 nop-metadata 聚合上下文拆分重构死亡——新锚点 NopMetaEntityBizModel.queryData:299 + NopMetaEntityQueryAction.java:82-89，propNames 仍取全列无 published 过滤，缺陷疑仍 live 换宿主**〕）。6+8=14 自洽。
- **新增敏感面 grep 复扫**（roadmap 点名）：密钥（key/secret/password/token 硬编码与日志泄漏）、日志（PII/凭证明文——3.9/3.10 已立发现的同族横切盘点）、路径拼接（文件/SQL 拼接未转义）三类模式的全仓 grep + 抽样核实，注明口径。
- 仍 live 且 P0/P1 级的转 R1.0 输入（登记 index）；**仍 live 且 P2 的条目：在报告显式开辟 P2 裁决例外（逐项转 R1.0 裁决）并在 index「P2 裁决台账」登记**；缓解生效条目在报告登记缓解机制并同步 security-audit-roadmap item 14 行。
- index 登记 + 独立 closure audit + roadmap 3.11 翻 done。

## Non-Goals

- ledger 已 FIXED/batch-4 条目重复深审（**例外：抽验 2 条防回归**——见 Phase 1 checklist）；LOW/watch-only 条目逐项深审（**例外：现状登记入报告附录**——见 Phase 1 checklist）；各深审工作项已立案发现的重复立案（撞车对照引用）。

## Scope

### In Scope

- item 14 名册 14 条对应 live 文件（nop-core〔F-API1-1〕/nop-rpc-http+nop-spring-web-starter〔F-API4-1 /px/——owner 面 nop-graphql-core runProxy〕/nop-ai〔F-AI1-1/F-AI2-2〕/nop-wf〔F-WF-01-x/F-WF-02-1〕/nop-http-client-apache〔F-N1-1〕/nop-http-client-okhttp〔F-N1-2〕/nop-vertx-mqtt〔F-N2-2〕/nop-stream-runtime+nop-rpc-core〔F-N3-2〕/nop-file〔F-F2-1〕/nop-metadata〔F-D1-1〕域的标定例外——只做条目实证不展开深审）
- 全仓 grep 复扫（三类模式：密钥/日志 PII/路径拼接）
- 只读输入：consolidation-summary、security-audit 源报告（API-01/API-04/AI-01/AI-02/NET-01/NET-02/NET-03/FILE-02/DATA-01/WF-01/WF-02）、3.1/3.8/3.9/3.10 报告（撞车对照）

### Out Of Scope

- ledger FIXED/batch-4 条目深审；LOW 条目深审；6.x 反模式专项（6.2 吞异常扫描为独立工作项）；各深审工作项范围的内模块全面深审。

## Execution Plan

### Phase 1 - 标定与复扫（双 agent 并行）

Status: completed
Targets: item 14 名册 + 全仓 grep

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：14 条名册对应 live 文件清单、grep 口径设计（三模式+排除项+窗口写死）、既有深审报告撞车结论索引、**item14 组与 grep 组相交面点名（F-AI1-1↔密钥/日志族、F-API4-1↔header 日志族）**
- [x] item 14 标定子 agent：撞车对照 6 条（F-WF-01-1/2/3、F-WF-02-1 引 3.1/3.2；F-F2-1 映射 3.8 P1-3.8-001/002；F-N1-2 引 3.9/plan 2283——核对 live 无漂移）+ 实测 8 条（F-API1-1/F-API4-1/F-AI1-1/F-AI2-2/F-N1-1/F-N2-2/F-N3-2/F-D1-1 逐项 live 实证三态判定）
- [x] grep 复扫子 agent：三类模式全仓 grep（密钥硬编码与日志/PII 凭证明文/路径拼接；**口径在机械基线步写死：全仓窗口 + 文件类型排除表**）+ 抽样核实 + 与已立案发现的撞车去重
- [x] FIXED/batch-4 抽验 2 条防回归 + LOW/watch-only 现状登记入报告附录
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A/B 面均第 2 轮收敛）
- [x] 产物 `_tmp/fcr-3.11-{item14,grep}.md`

Exit Criteria:

- [x] 两份初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [x] item 14 名册 14 条逐项三态判定表落盘（0 已修/12 live/2 缓解）+ grep 复扫台账（口径可复现）落盘
- [x] No owner-doc update required（只读；owner-doc 同步在 Phase 3 执行）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + item 14 判定独立验证（A 14/14 确认零改判、判级抽验 P2 全成立；B 口径重放全一致、13-30/13-31 维持）
- [x] 主 agent 汇总：判级统一（按 2.6 总裁据）、撞车去重终裁（F-F2-1 升 P1 合并 3.8 族不新占编号；7 项撞车去重；无跨报告冲突）
- [x] 标定表收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.11-{item14,grep}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.11-security-crosscut-recheck.md`；index 登记 3.11 行 + P2 裁决台账 12 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（本项零 P0）
- [x] 定向验证锚点：grep 复扫口径重放由 B 面复核独立执行全一致（9 条命令）+ 4 项关键判定亲读确认（本项无模块测试锚点——验证以复核员独立复扫为锚）；doc-links exit 0
- [x] **owner-doc 同步**：security-audit-roadmap item 14 行已回填标定结果；consolidation ledger F-WF-01-3 缓解补记行已就地替换（:29，Status 词表不变）——P1-3.10-001 教训：台账链断裂=虚假保证
- [x] 独立子 agent closure audit；通过后 roadmap 3.11 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 复扫口径可复现（另一 agent 按口径重放计数一致——B 面复核 9 条命令全一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立复扫为验证锚点。

- [x] 报告落盘且 item 14 全 14 条 + grep 三模式全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.11 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 全仓安全横切复核计划收口。报告 `3.11-security-crosscut-recheck.md` 落盘：通过无 P0/P1 新发现附 P2×1/P3×1；**item 14 全 14 条标定完成 0 已修/12 仍 live/2 缓解生效**（复核 14/14 亲读确认、判级抽验 P2 全成立、零驳回零改判）。F-F2-1 升 P1 合并 3.8 P1-3.8-001/002 族（不新占编号）+ F-WF-02-1 带修复裁定直入 R1.0；**P2 裁决例外 12 条转 R1.0**（index「P2 裁决台账」已登记）。grep 复扫三模式 123 处全量核实、口径重放 9 条全一致；新发现 P2-3.11-001（JWT 主密钥族 22 文件含 nop-batch-app 键漂移死配置）+ P3-3.11-001（SSO client-secret 第 4 实例）。**owner-doc 同步完成**：security-audit-roadmap item 14 行回填 + consolidation ledger F-WF-01-3 缓解补记就地替换。closure audit 一次 PASS（处方为机械收口项，已落实）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_e8548472-864d-48ab-804a-7c2f045ee19b（fresh session，只读 closure auditor；一次 PASS 附机械处方）
- Evidence:
  - 计数勾稽：P2×1/P3×1 ↔ 报告实点 ↔ index 3.11 行（0/0/1/1）↔ P2 裁决台账 12 行 ↔ item 14 标定表 14 行（0/12/2 守恒）↔ 复核净账
  - P1/P0 台账：无 3.11 新增 P1 行正确（F-F2-1 合并 3.8 族有 P1-check-mcc-172 先例同口径）；「P0 发现追踪」无 3.11 新增
  - item 14 四项关键判定亲读独立复现：F-F2-1（xmeta updatable + 纯 Crud + LocalResourceStore 无 .. 防御）/F-WF-02-1（五处 serialize 锚点精确命中 + xdef persist=true）/F-D1-1（新锚点 :82-84 全列无过滤 + :89-90 逐列取值）/F-N1-1（helper 零 redirect + 缺省 false + 变体零消费者三要件）
  - P2 判级抽验 2 条成立（F-N3-2 MessageRpcServer:83-103 无身份直派 + 升级触发条件保留；F-D1-1 与 3.8 P1 族分级区别成立）
  - 格式：P2 条 10 要素/P3 条 9 要素完整
  - owner-doc 同步亲读确认（roadmap item 14 行回填 + ledger 补记措辞可用）
  - 验证：口径重放独立复现（enc-key 23 行/client-secret 4 文件/键漂移亲证）；`check-doc-links.mjs --strict` exit 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以复核员独立复扫 + closure auditor 亲读为锚
  - Deferred 项分类检查：无 deferred；P2 裁决例外 12 条走报告显式例外通道（roadmap 规则 #3 合规），无 in-scope live defect 被静默降级

Follow-up:

- no remaining plan-owned work（item 14 的 11 条 live P2 + P2-3.11-001 经 P2 裁决台账转 R1.0 逐项裁决；F-F2-1/F-WF-02-1 已在 R1.0 立案；缓解登记 2 条完成台账闭环）
