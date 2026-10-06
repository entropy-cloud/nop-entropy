# 2347 full-code-review 5.3 跨模块公共 API（nop-*-api）契约一致性与版本健康审计

> Plan Status: active
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 5.3（MA5）；Skill=`ai-dev/skills/cross-module-dependency-audit-prompt.md` + `ai-dev/skills/deep-audit-prompts.md`（维度 20）
> Related: owner doc `docs-for-ai/01-repo-map/module-groups.md`；Roadmap Details：「nop-*-api 模块表面（api/xmeta/api.xml）与实现一致性、破坏性变更记录、版本健康」；`CHANGELOG.md`（破坏性变更记录载体）。
> 机械基线（主 agent 实测，2026-10-07；经 Draft Review agent_1255faa4 逐项实测勘正）：全深度扫描 `*`-api 模块目录共 **26 个**（25 平台 + 1 demo）＝顶层组 21 + 嵌套 5。顶层组 21：ai 95 / auth 90 / sys 57 / wf 71 / code 75 / job 42 / dyn 45 / metadata 30 / report 27 / integration 24 / biz-auth-api 28 / rule 21 / graph 14 / retry 15 / tcc 15 / batch 12 / task 12 / credential 9 / search 8 / file 3 / datav 0〔空壳，仅 pom，1 commit 951ab3ee86〕。嵌套 5：nop-auth/nop-oauth/nop-oauth-api 9 / nop-core-framework/nop-plugin/nop-plugin-api 10+plugin.xdef / nop-network/nop-http/nop-http-api 46+1 `http-api.beans.xml`+1 `nop-http-api.beans`（autoconfig，后缀不同）+唯一 @Deprecated 命中 / nop-network/nop-rpc/nop-rpc-api 18 / nop-demo/nop-ddd-demo/demo-ddd-api 18。合计 main Java **794**。资源面仅 4 模块非空（见上）；**api 模块 xmeta=0 全域**。版本健康机械面：根 pom 2.0.0-SNAPSHOT 统一版本 + `${nop-entropy.version}`；**nop-bom 登记状态待机械口径 3 全量核对**，已知线索两条：demo-ddd-api 未入 bom、bom 含幻影 artifact `nop-stream-api` 且 `tests/pom.xml` 声明依赖之（预登记线索，不作预判）。@Deprecated 仅 http-api 1 文件命中。git churn（2026-07-01 起，含 `--no-merges` 复核）：ai-api 24 / metadata-api 13 / auth-api 11 / job-api 9 / biz-auth-api 8 / code-api 7 / retry-api 7 / plugin-api 6 / sys-api 5 / credential-api 5 / http-api 4 / integration-api 4 / wf-api·rule-api·graph-api·tcc-api 3 / 其余 ≤2（dyn 2 / oauth-api 2 / task 2 / datav 1 / file 1 / report 1 / batch 1 / rpc-api 1 / search 0 / demo-ddd-api 0，逐一实测）。
> 只读审计（P0 走异步注入）。**机械全量 + 深审抽样结合**：26 模块全量机械扫描（churn/破坏性变更/幻影接口/版本健康/消费者声明卫生）；深审抽样 16 模块（3 agent 并行，Tier 划分见 Goals）。**方法学裁定**：① deep-audit 维度 20（跨模块契约一致性）为主判定锚（共享前缀 P0–P3 判级 :329-333 适用）；② cross-module-dependency-audit-prompt 的 ORM 数据依赖域（DAG/refEntityName/机制 B）**已被 1.1 覆盖不重做**，本项仅取其「契约面与声明一致性」方法立场；③ `api 模块纯净性`（pom 依赖污染）已被 1.1 覆盖不重做，本项 pom 面仅核对「api→impl 反向依赖」之外的新维度（消费者侧声明卫生）；④ severity 判级统一以 deep-audit 共享前缀 P0–P3 为准（roadmap 通用约定），**全部子 agent（含机械全量 agent）prompt 装配 = 共享前缀（含误报校准 :335-345）+ 本 plan 裁定**；⑤ 机械口径 4 显式继承共享前缀误报校准为排除规则（「必要的传递依赖声明」「平台核心包 nop-api-core/nop-commons/nop-core/nop-xlang/nop-markdown 经传递链稳定获得不需单独声明」不报告）；⑥ 破坏性变更口径中「接口方法签名变更线索」子项允许半自动核查（文件删除/重命名机械检出 + 签名变更语义 diff 人工留痕方法）。**范围裁定**：demo-ddd-api 严格不匹配 roadmap `nop-*-api` 字面，属保守扩大纳入（仅机械口径、Non-Goals 排除深审，显式 scope decision）。
> Draft Review: agent_1255faa4-dc33-4cf9-8e2f-7a8bf4dafb80 首轮 1 Blocker（B1 模块计数 27→26＋「graph?」编辑残留）+ 4 Major（M1 合计 742→794/M2 churn 漏 sys 5·plugin 6·http 4/M3 bom「登记全量」不成立〔demo-ddd-api 未登记+bom 幻影 nop-stream-api 预登记为线索〕/M4 口径 4 缺共享前缀豁免规则）+ 6 Minor（beans 命名/datav 消费者 1+0/Scope 缺 2.7/pom 复核分工/demo-ddd 范围显式化/签名变更半自动）——本轮全部修复，待聚焦复核。

## Purpose

执行 roadmap 工作项 5.3：跨模块公共 API（nop-*-api）契约一致性与版本健康审计，产出报告 `5.3-api-contract-version-health-audit.md`。

## Current Baseline

- 机械基线：26 个 `-api` 模块（25 平台 + demo-ddd-api），main Java 794，清单与 churn 数据见头部注记；资源面仅 rule-api/wf-api（api.xml）、plugin-api（plugin.xdef）、http-api（beans 面两文件）；api 模块 xmeta=0。
- 版本健康机械面：全仓统一 2.0.0-SNAPSHOT（`${nop-entropy.version}`）；**26 个 pom 的版本声明与 parent/bom 关系由机械全量 agent 口径 3 独立全量核对**（非深审 agent 职责）；bom 已知线索两条见头部注记（demo-ddd-api 未入 bom、幻影 `nop-stream-api`）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；本项只读（验证以复核员独立抽验为锚）；工作树在 5.2 收口 commit 后干净。
- 撞车面（Phase 2 必查）：1.1（api 模块纯净性/pom 污染）、2.7（IoC/beans 面）、3.10（nop-auth-api 增量 diff 面——NopAuthLoginAttempt/RateLimitCounter 已标定）、4.2（nop-biz-auth-api 测试有效性——2 test 有效约束已裁定）、5.1（docs-for-ai 声明域）。3.5/5.1 的幻影方法学先例（ILockService/@IocBean）适用为本项「幻影 API」判级参照。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/5.3-api-contract-version-health-audit.md`：摘要 + 机械全量台账 + 深审抽样台账 + P0–P3 发现（`P<级别>-5.3-<序号>`）+ 驳回台账 + 裁决。
- **机械全量扫描（26 模块）**，四项口径全部脚本化可复现：
  1. **破坏性变更记录**：`git log` 逐 api 模块盘点历史（含文件删除/接口方法签名变更线索），与 `CHANGELOG.md` 条目对照——破坏性变更是否留痕（CHANGELOG 含「破坏性变更/迁移指南」体例，如 2026-08-23 nop-plugin 六态状态机收敛条）。
  2. **幻影 API 扫描**：api 模块公开接口/DTO 全量清单，`implements`/调用点 grep——无实现且无消费者的幻影接口（参照 ILockService 先例判级）。
  3. **版本健康**：26 pom 逐个核对版本声明与 parent/bom 关系；@Deprecated 面（api 模块内几乎为零——已弃用 API 是否实际散落 impl 模块的结构性问题登记）。
  4. **消费者声明卫生**：消费者 pom 是否声明其直接 import 的 api 模块（missing-direct-dep 机械检测），与 1.1 的 api 纯净性（被依赖侧）互补。**排除规则（硬性）**：显式继承 deep-audit 共享前缀误报校准（:335-345）——「必要的传递依赖声明」与「平台核心包 nop-api-core/nop-commons/nop-core/nop-xlang/nop-markdown 经传递链稳定获得不需单独声明」不报告；机械 agent prompt 装配 = 共享前缀（含误报校准）+ 本 plan 方法学裁定。
- **深审抽样 16 模块（3 agent 并行）**，按 Tier 分配：
  - **Agent A「churn+安全面」（5）**：`nop-ai-api`（churn 24 最高、IChatService 公开契约 owner doc 点名）、`nop-auth-api`（churn 11+安全面+3.10 背景）、`nop-credential-api`（安全面）、`nop-biz-auth-api`（service-framework 公共 biz 契约+4.2 背景）、`nop-oauth-api`（安全面嵌套 api）。
  - **Agent B「大消费面+特殊资源面」（5）**：`nop-sys-api`（57 java）、`nop-wf-api`（71 java+nop-wf.api.xml）、`nop-metadata-api`（churn 13）、`nop-rule-api`（nop-rule.api.xml）、`nop-job-api`（churn 9+7.5 前置背景）。
  - **Agent C「churn+嵌套+特殊裁决」（6）**：`nop-code-api`（75 java+churn 7）、`nop-retry-api`（churn 7）、`nop-http-api`（46 java+唯一 @Deprecated+beans 面随 api 发布）、`nop-rpc-api`（rpc 契约面）、`nop-datav-api`（**空壳裁决**：仅 pom、唯一 commit 为父级 feat 携带、maven 依赖声明消费者 1（datav-dao）/java import 消费者 0——保留/补实/删除三选一裁定）、`nop-plugin-api`（xdef 随 api 发布+2026-08-23 破坏性变更主角——验证该 CHANGELOG 条与 live 一致）。
  - 每模块深审清单（维度 20 适配）：① 表面↔实现一致性（接口有 impl、DTO 被接口引用、实现侧跨模块公共类型反向缺口）② api.xml/xdef 资源面与 live model 一致（rule/wf/plugin/http 四模块）③ 破坏性变更留痕抽验 ④ 事件 pub/sub 匹配（如有）⑤ 配置键与 @InjectValue 一致（抽查）⑥ 版本/弃用卫生。每模块 ≥3 条核对声明，发现按统一格式 ≥9 行。
- **机械-only 模块**（batch/dyn/file/graph/integration/report/search/task/tcc + demo-ddd-api，共 10）：机械口径覆盖 + 异常上报，不深审（深审 16 + 机械-only 10 = 26 全覆盖）。
- index 登记 + 独立 closure audit + roadmap 5.3 翻 done。

## Non-Goals

- 1.1 已覆盖的 api 模块 pom 纯净性/循环依赖重扫（引用其正结论，仅登记撞车）；ORM 数据依赖域（cross-module-dependency-prompt 主体，机制 B）；docs-for-ai 声明域（5.1）；api 模块测试有效性（4.2）；发现修复执行（归 R1.0/R1.x）；demo-ddd-api 深审。

## Scope

### In Scope

- 26 个 `-api` 模块（机械全量）+ 16 模块深审抽样（清单见 Goals）
- 只读输入：`CHANGELOG.md`、`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/04-reference/safe-api-reference.md`、git 历史（api 模块 path 限定）、1.1/2.7/3.10/4.2/5.1 报告（撞车对照）

### Out Of Scope

- 1.1/4.2/5.1 已闭包域重扫；修复执行；demo-ddd-api 深审；`nop-biz` 等非 api 模块的公共类型治理（仅登记「应入 api 未入」线索归 R1.0）。

## Execution Plan

### Phase 1 - 机械全量与深审抽样（3+1 agent 并行）

Status: in progress
Targets: 26 个 `-api` 模块 + git 历史 + CHANGELOG.md

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净：登记在途变更，触及扫描域则等待，未触及登记豁免继续）
- [ ] 机械全量 agent：四口径脚本化扫描（破坏性变更↔CHANGELOG 对照/幻影接口/版本健康/消费者声明卫生），26 模块台账落盘（允许分批产出，批次边界=口径）
- [ ] Agent A：Tier A 5 模块深审（每模块 ≥3 条核对声明）
- [ ] Agent B：Tier B 5 模块深审（含 rule/wf api.xml 与 live model 对照）
- [ ] Agent C：Tier C 6 模块深审（含 datav 空壳裁决、plugin CHANGELOG 对照、http @Deprecated 面）
- [ ] 主 agent 抽查质量：口径重放 ≥5 条（机械数字复算 + 深审声明抽验）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 产物 `_tmp/fcr-5.3-api.md`

Exit Criteria:

- [ ] 初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [ ] 机械全量台账（26 模块 × 4 口径）+ 深审抽样台账（16 模块）落盘
- [ ] No owner-doc update required（只读——发现归 R1.0 修复）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 机械口径重放（≥5 条独立复算）+ 撞车核对（1.1/2.7/3.10/4.2/5.1 报告对照）
- [ ] 主 agent 汇总：判级统一（P0–P3 共享前缀）、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-5.3-api-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `5.3-api-contract-version-health-audit.md`；index 登记 5.3 行（P0/P1/P2/P3 计数）+ P1 汇总行（如有）+ P2 裁决台账行（如开辟例外）+ 清理 5.1/5.2 残留占位行（index 文本一致性缺陷顺手修复）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：机械口径重放由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/index 计数与报告发现清单一一对应）；通过后 roadmap 5.3 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 机械口径可复现（复核员重放一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [ ] 报告落盘且机械全量/深审抽样全覆盖——覆盖度量：26 模块机械台账 + 16 模块深审（3+1 agent）
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 5.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；API 修复工单归 R1.0/R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
