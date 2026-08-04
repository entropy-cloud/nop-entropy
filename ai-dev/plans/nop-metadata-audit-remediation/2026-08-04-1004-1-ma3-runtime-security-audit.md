# MA3 运行时与安全审计（service + web）

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Draft Review: 3 轮独立子 agent 对抗性审查通过（第 1 轮 4 Major + 7 Minor 全部修复；第 2 轮 5 Minor 全部修复；第 3 轮最终验证 5/5 PASS，1 Minor 措辞已顺手修正，裁定可执行）。Session: ses_0357a1871ffemP79rcWdjFRdXM / ses_0356f45f3ffeWPQplhgO6Cr2p9 / ses_0355b0441ffeXWP7M203OpmK69。
> Mission: nop-metadata-audit-remediation
> Work Item: MA3（3.1 XDSL/XLang / 3.2 GraphQL/API / 3.3 安全与权限 / 3.4 异步与事务）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA3 里程碑）、`ai-dev/skills/deep-audit-prompts.md`（维度 10/12/13/14）
> Related: 执行顺序 `{1}` of 3 — 硬前置：M0（0.1-0.4）全部 done、MA1（1.1-1.4）done、MA2（2.1-2.4）done，以 M0.3 未闭包清单作为历史发现对照输入，以 M0.4 基线（813/0）为测试基线；产物（P0/P1 发现）是 MR2 批量修复的输入；MA3.3 复核 `2026-07-20-1554#MISSING-AUTH` watch-only 裁定、MA3.4 复核 `2026-07-20-1554#post-commit-SEMANTIC` 与 `2026-07-20-1554#RACE` 登记状态。

## Purpose

对 nop-metadata 的运行时与安全面执行审计（roadmap MA3 的 4 个工作项：XDSL/XLang 正确性、GraphQL/API 层、安全与权限模型、异步与事务模式），覆盖 07-23 自评盲区（xbiz 文件从未审计），产出审计报告并更新 arm-index，为 MR2 批量修复提供输入。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 roadmap 一致，已二次确认文件与计数）：

- roadmap MA3 四行（3.1-3.4）状态为 `todo`；MA1/MA2 已 completed（MA1 发现 1 P1 = P1-MA1-001 归 MR1；MA2 无 P1，3 P2 新增归 MR1/MR2 裁决）
- 对照源：`ai-dev/audits/arm-unclosed-findings-nop-metadata.md`（M0.3 清单，轮次限定 ID 为权威）+ `arm-index-nop-metadata.md`（MA1/MA2 报告已登记）
- **xbiz 规模（live）**：`find nop-metadata -name "*.xbiz" -not -path "*/target/*"` = **78 个**（service `_vfs/nop/metadata/model/NopMeta*/` 下成对：`_NopMeta*.xbiz` 生成物 + `NopMeta*.xbiz` 手写保留层）——07-23 审计从未覆盖 xbiz（盲区，MA3.1 主对象）
- **xwf（live）**：3 个（`metaDataContractApproval` / `qualityBreachApproval` / `tagLabelConfirmApproval`，均为 `v1.xwf`）；xmeta 79 个（含 NopMetaSearch.xmeta——其 P1-MA1-001 陈旧类引用已归 MR1，MA3 不重复处理）
- 待 MA3 复核的历史登记项：
  - `2026-07-20-1554#MISSING-AUTH`（自定义 @BizMutation 缺细粒度 @Auth，watch-only，plan 300 裁定）→ **MA3.3 复核**
  - `2026-07-23-0714#维度11-04`（computeQualityScore 经 `dao().saveEntity()` 绕过 xmeta insertable 验证，open，MR2 归属）→ **MA3.2 复核后归 MR2**
  - `2026-07-20-1554#post-commit-SEMANTIC`（dispatchActions post-commit 语义 = runWithoutTransaction 同步，watch-only）→ **MA3.4 复核**
  - `2026-07-20-1554#RACE`（upsertExternalTable 读-写竞态，待复核；相关唯一键已补 UK 体系 35+ 键）→ MA3.4 初步复核，终局归 MA6.6
- MA1.3 结论引用：GraphQL 暴露面 39 个 I*Biz 接口（14 含自定义方法，共 43 个 @BizQuery/@BizMutation，全部带 IServiceContext 末参）/ service main 41 处 @BizModel / 含测试 42；GraphQL 暴露面 Map 返回已清零（计数口径见 MA2.3，MA4.3 另行核实）
- **webhook SSRF 防护已存在（live）**：`CheckpointActionDispatcher` webhook host allowlist + `TestCheckpointActionDispatcherWebhookSsrf` 测试（MA3.3 做回归核对，深挖归 MA7.6）
- **action-auth 配置位置（live）**：`nop-metadata-web/src/main/resources/_vfs/nop/metadata/auth/`（3 个文件：生成基线 + 保留层 + app 级）——MISSING-AUTH 复核的对象
- 07-23 盲区声明（roadmap 已知盲区段）：xbiz 文件、web 模块页面资源、质量调度（cron）与事件链路、搜索索引隔离、导入引擎安全、联邦查询 withConnection 数据权限面——MA3 覆盖 xbiz/cron 事件链路；web 模块资源归 MA7、withConnection 旁路归 MA7.2（MA3.3 只做基础权限面核对，不替代域特有深挖）
- 测试基线：813 tests / 0 failures（M0.4，2026-08-04 实测；范围 `-pl nop-metadata -am -T 1C`）

## Goals

- 产出 MA3 审计报告（4 份：3.1 XDSL/XLang / 3.2 GraphQL / 3.3 安全 / 3.4 异步事务），发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>`
- 每个发现标注 P 级 + 修复归属（MR2/即时通道/非阻塞）
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪
- 对 3 项 watch-only/待复核历史登记（MISSING-AUTH / post-commit-SEMANTIC / RACE）给出复核结论并登记
- 无 P0 时保持绿色基线；发现 P0 走即时通道

## Non-Goals

- 不修复审计发现（修复归 MR2 批量修复，P0 例外走即时通道）
- 不审计 ORM/BizModel 结构（MA2 已覆盖）、工程质量（MA4 承接）、域特有安全深挖（MA7.1/7.2/7.5/7.6 承接）
- 不改任何 `src/` 代码（纯审计计划）

## Scope

### In Scope

- 3.1 XDSL 与 XLang 正确性审计（维度 10）：78 个 xbiz + 3 个 xwf + 79 个 xmeta + beans.xml + `_app.orm.xml` 的语法/语义正确性；x:schema 引用、x:extends 合规（沿用 MA1.4 判定标准）、xbiz 方法声明与 BizModel Java 方法签名兼容性（07-23 盲区主项）、xwf 流程定义 XDSL 正确性（流程语义与 nop-wf 集成细节归 MA7.6）
- 3.2 GraphQL 与 API 层审计（维度 12）：@BizQuery/@BizMutation 映射、分页（QueryBean + doFindPage/doFindList）、FieldSelectionBean 注入、selection 绕过（手动序列化/反序列化）、/r/ 与 /q/ 行为一致性、硬编码 SQL 绕过 ORM、computeQualityScore xmeta 验证绕过复核（11-04，归 MR2）、queryAggregation 11 参数状态复核（`2026-07-21-2039#维度07-03`）
- 3.3 安全与权限模型审计（维度 13）：方法级权限注解现状（@Auth 细粒度核对 + MISSING-AUTH watch-only 复核）、xmeta 字段级权限（creatable/updatable/readable）、敏感字段可见性（connectionConfig 凭据类字段）、数据权限（requireEntity/getEntityById 残余核对，归属 MR2）、直接数据访问旁路（withConnection 基础面，深挖归 MA7.2）
- 3.4 异步与事务模式审计（维度 14）：质量检查点 cron 调度（MetaQualityCheckpointScheduler）、dispatchActions post-commit 语义复核、upsertExternalTable 竞态初步复核、txn()/afterCommit 使用、长事务与资源泄漏
- 审计报告（`ai-dev/audits/YYYY-MM-DD-HHmm-arm-MA3.<n>-nop-metadata-<dimension>.md`）+ arm-index 更新

### Out Of Scope

- MA4-MA7 审计（后续计划）
- 任何修复（MR1/MR2/MR3 承接）
- `docs-for-ai/` 文档修改（MA5 覆盖；审计发现记录为 finding 即可）

## Execution Plan

### Phase 1 - MA3.1 XDSL 与 XLang 正确性审计

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/resources/_vfs/`（xbiz 78 + xwf 3 + xmeta 79 + beans.xml + `_app.orm.xml`）

- Item Types: `Proof`

- [x] **启动门禁核查**：确认 M0/MA1/MA2 已 done（roadmap 对应行 done、arm-index 报告清单已含 MA1/MA2 报告）；未满足则不启动并上报
- [x] 执行维度 10 审计（XDSL 与 XLang 正确性）：xbiz/xwf/xmeta/beans.xml 全量 XDSL 文件 x:schema 引用指向、x:extends 合规（`_NopMeta*` 生成物与 `NopMeta*` 保留层成对关系）、x:override 语义检查（replace/merge/remove，deep-audit-prompts 维度 10 步骤 3）、命名空间声明、beans.xml bean class 与 Java 类一致性（仅引用 MA1.4/MA2.4 结论，不重复审计）、xbiz 方法声明与对应 BizModel Java 方法签名兼容性
- [x] **生成物边界规则（AGENTS.md Hard Stop）**：`_NopMeta*.xbiz`、`_app.orm.xml` 等生成文件发现缺陷时，发现归属必须回指源模型（orm.xml / 保留层 / 生成模板），不得把生成文件缺陷记为手写层缺陷
- [x] 运行 `find nop-metadata \( -name "*.xbiz" -o -name "*.xwf" -o -name "*.xmeta" \) -not -path "*/target/*"` 获取 XDSL 文件清单基线（78 xbiz / 3 xwf / 79 xmeta，排除 target/；注意 `-o` 需括号分组，否则排除只作用于最后一支）
- [x] 历史对照：维度 10 历史来源——07-20-1554 `10-xdsl-xlang.md` 已审计 xbiz（结论"保留层 xbiz 为空壳，无自定义 action 声明" + 1 项 minor: xmlns:ioc），**本计划表述修正：07-23 从未审计 xbiz（盲区），07-20 仅有空壳级结论，MA3.1 做全量深化而非首次审计**；M0.3 清单无维度 10 条目，不对不存在的条目虚构核对记录
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1136-arm-MA3.1-nop-metadata-xdsl.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 报告包含：XDSL 文件清单（78 xbiz / 3 xwf / 79 xmeta 计数口径记录）、x:schema/x:extends 合规性结论、xbiz-方法签名兼容性核对表、xwf XDSL 正确性结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 07-23 盲区（xbiz）已覆盖且有审计结论（无发现也需显式声明）
- [x] 文档变化：`No owner-doc update required`（审计报告为证据层）
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新（见本日志 08-04.md Phase 1 条目）

### Phase 2 - MA3.2 GraphQL 与 API 层审计

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/` + `nop-metadata/nop-metadata-api/`

- Item Types: `Proof`

- [x] 执行维度 12 审计（GraphQL 与 API 层）：@BizQuery/@BizMutation 映射正确性、分页（QueryBean + doFindPage/doFindList）、FieldSelectionBean 使用（不手动构建）、selection 绕过（手动序列化/反序列化、Map 直返）、/r/ 与 /q/ 一致性、硬编码 SQL/HQL 绕过 ORM
- [x] 复核 `2026-07-23-0714#维度11-04`（computeQualityScore 绕过 xmeta insertable 验证）：live 核对实际保存路径（`QualityResultWriter.java:50` 的 `resultDao.saveEntity(row)`，computeQualityScore 经执行器链到达）与 xmeta insertable 定义，给出复核结论（open 则维持 MR2 归属）
- [x] 复核 `2026-07-21-2039#维度07-03`（queryAggregation 11 参数未用 @RequestBean）：live 核对签名；**引用 MA1.3 已有 live 复核结论**（arm-index P2 行备注：本审计已复核确认仍 open），确认 MR2 归属登记
- [x] 历史对照：以 M0.3 未闭包清单中维度 11/12 相关条目逐一核对——维度 11 条目 `11-02/11-03/11-04`（11-02/11-03 为 P3 已裁定 watch-only/deferred，仅确认状态不回审；11-04 见上）、维度 12 实际条目为 `2026-07-19-1118#维度12-01`（FieldSelectionBean 未完全下推，P3，仅确认状态归 MA6 复核）；07-23 未审计维度 12，无对应条目；不对不存在的条目虚构核对记录
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1156-arm-MA3.2-nop-metadata-graphql.md`

Exit Criteria:

- [x] 报告包含：GraphQL 暴露面映射核对结论、分页/selection 模式合规结论、11-04 复核结论（含 live 证据）、07-03 复核结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新（见本日志 08-04.md Phase 2 条目）

### Phase 3 - MA3.3 安全与权限模型审计

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/` + `_vfs/**/*.xmeta` + `nop-metadata-web/src/main/resources/_vfs/nop/metadata/auth/`（action-auth 生成基线 + 保留层）+ `nop-metadata-app/src/main/resources/_vfs/nop/metadata/auth/`（app 级 `app.action-auth.xml`）+ `CheckpointActionDispatcher` webhook 实现

- Item Types: `Proof`

- [x] 执行维度 13 审计（安全与权限模型）：@Auth 注解现状盘点（细粒度缺失范围）、xmeta 字段级权限（creatable/updatable/readable）合理性、敏感字段可见性（connectionConfig 凭据/加密字段、脱敏链路）、requireEntity/getEntityById 数据鉴权残余核对（与 MA2.3 结论衔接）、withConnection 直查旁路基础核对（深挖归 MA7.2）
- [x] **webhook allowlist 回归核对（roadmap MA3.3 明确项）**：live 核对 `CheckpointActionDispatcher` webhook host allowlist 实现与 `TestCheckpointActionDispatcherWebhookSsrf` 断言一致（回归验证；SSRF 深挖与 allowlist 扩充归 MA7.6）
- [x] 复核 `2026-07-20-1554#MISSING-AUTH`（自定义 @BizMutation 缺细粒度 @Auth）：live 核对 `nop-metadata-web/src/main/resources/_vfs/nop/metadata/auth/` action-auth 配置（生成基线 + 保留层 + app 级）与粗粒度兜底现状，给出复核结论（维持 watch-only 或升级）
- [x] 历史对照：维度 13 历史来源——07-20-1554 `13-security-permission.md` 条目与 07-21 安全相关条目（MISSING-AUTH 为 M0.3 唯一安全面 P1 登记）；**07-23 从未审计维度 13**，无对应条目；不对不存在的条目虚构核对记录
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1204-arm-MA3.3-nop-metadata-security.md`

Exit Criteria:

- [x] 报告包含：@Auth 现状盘点表、xmeta 字段级权限核对结论、敏感字段可见性结论（connectionConfig 含凭据字段处置）、requireEntity 残余核对结论、MISSING-AUTH 复核结论、**webhook allowlist 回归核对结论（TestCheckpointActionDispatcherWebhookSsrf 断言与实现一致性）**
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新（见本日志 08-04.md Phase 3 条目）

### Phase 4 - MA3.4 异步与事务模式审计

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/` + `event/` + `entity/NopMetaDataSourceBizModel.java`（RACE 复核对象）+ `entity/NopMetaQualityCheckpointBizModel.java`（post-commit 复核对象）

- Item Types: `Proof`

- [x] 执行维度 14 审计（异步与事务模式）：txn() 事务边界、afterCommit 使用场景、长事务风险（含远程调用/重计算）、异步处理错误处理与重试（质量检查点 cron 调度链路 MetaQualityCheckpointScheduler）、并发竞态（乐观锁/悲观锁）、资源泄漏（连接/流）
- [x] 复核 `2026-07-20-1554#post-commit-SEMANTIC`（dispatchActions post-commit = runWithoutTransaction 同步）：live 核对 CheckpointActionDispatcher javadoc 与实现（调用方 NopMetaQualityCheckpointBizModel），给出复核结论（维持 watch-only 或升级）
- [x] 初步复核 `2026-07-20-1554#RACE`（upsertExternalTable 读-写竞态）：live 核对唯一键（UK 体系 35+ 键）与 upsert 路径（NopMetaDataSourceBizModel.java:435），记录初步结论（终局定论归 MA6.6）
- [x] 历史对照：M0.3 清单**无维度 14 条目**（仅上述 2 个命名 ID 登记项，已在执行项中覆盖）；07-20-1554 `14-async-transaction.md` 为正向确认来源（核对引用），不对不存在的条目虚构核对记录
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1212-arm-MA3.4-nop-metadata-async-txn.md`

Exit Criteria:

- [x] 报告包含：事务边界/afterCommit/长事务结论、cron 调度链路可靠性结论、post-commit-SEMANTIC 复核结论、RACE 初步复核结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新（见本日志 08-04.md Phase 4 条目）

## Closure Gates

> 纯审计计划（不改代码），构建验证以绿色基线保持为准。

- [x] 4 份 MA3 审计报告全部产出且含 P 级标注 + 修复归属
- [x] 历史对照按各 Phase 显式条目清单完成：`MISSING-AUTH` / `post-commit-SEMANTIC` / `RACE` / `11-04` / `07-03` 复核结论齐全；`11-02`/`11-03`/`12-01` 等 P3 条目仅确认状态不回审（roadmap 规则 1）；维度 10/13/14 无 M0.3 条目处不虚构核对记录；xbiz 盲区已覆盖
- [x] 5 项历史登记复核结论（MISSING-AUTH / post-commit-SEMANTIC / RACE / 11-04 / 07-03）已回填 arm-index（含 P2 表与 P1 watch-only 状态更新）
- [x] arm-index-nop-metadata.md 报告清单 + P0/P1 追踪已更新
- [x] P0 发现已走即时通道（若存在）；P1 发现已归入 MR2 修复清单
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证报告非空壳（有实际发现清单与可追溯引用，非模板占位）
- [x] `./mvnw compile -pl nop-metadata -am`（绿色基线保持验证）
- [x] `./mvnw test -pl nop-metadata -am`（绿色基线保持验证）
- [x] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-metadata-audit-remediation/2026-08-04-1004-1-ma3-runtime-security-audit.md --strict` 退出码 0（closure 时，Minimum Rule #26）

## Deferred But Adjudicated

### MA3 P2/P3 finding 的修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 规则 1 明示本 roadmap 只处理 P0/P1；P2/P3 记录为 deferred successor，由后续批次另行规划（同 MA1/MA2 裁定）。
- Successor Required: `no`（后续批次另行规划，非本批 scope）

## Non-Blocking Follow-ups

- watch-only 项复核维持原裁定者，登记结论即可（不产生修复债务）
- xbiz 全量深化审计如发现系统性问题（如 xbiz 与 Java 方法签名大面积不一致），作为 MR2 修复清单输入

## Closure

Status Note: 4 个 Phase 全部执行完毕（纯审计计划，零代码变更）。4 份 MA3 审计报告（xdsl/graphql/security/async-txn）全部产出，含 P 级标注 + 修复归属 + 历史对照；5 项历史登记复核结论齐全（MISSING-AUTH 维持 watch-only / post-commit-SEMANTIC 维持 watch-only / RACE 初步复核完成终局归 MA6.6 / 11-04 open 证据更新 / 07-03 open 确认）；arm-index 与 roadmap 已回填；绿色基线保持（813/0）；独立 closure audit PASS。可以关闭。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，task_id ses_035017b45ffeXDOaTzdUAyL1Pm）
- Audit Session: ses_035017b45ffeXDOaTzdUAyL1Pm
- Evidence:
  - 8 项核查全部 PASS：① plan checklist 一致性（4 Phase completed + 42 项 execution/exit criteria 全 [x]，仅 Closure Gates 待勾选——本 closure 段已补）；② Anti-Hollow（4 份报告含实际发现清单+可追溯行号引用，抽查 8 处 file:line 全部解析，如 QualityResultWriter.java:50 saveEntity、_vfs/nop/metadata/wf/ 3 个 v1.xwf、NopMetaTableBizModel.java:196-252 死 selection 参数、orm.xml:1314-1316 UK、_NopMetaDataContract.xbiz approval-support extends）；③ 5 项历史复核结论齐备（arm-index:56/58/75/78/88-89 + unclosed-findings:25/49/56/58/59）；④ arm-index 报告清单 +4 行、P1 表 +2 行（P1-MA3-001/002）、P2 表复核更新；⑤ roadmap 3.1-3.4 → done；⑥ 0 P0（4 份统计表均为 0，无需即时通道）；⑦ 无静默降级（xwf P1→MR2 正确归类；全部非阻塞项均有裁定理由）；⑧ 绿色基线（日志记录 813/0，独立复核 `./mvnw compile -pl nop-metadata -am -q` exit 0、check-doc-links exit 0）
  - 审计后修正 3 项文档缺陷（非阻塞）：MA3.1 统计表 P2 4→5、arm-index MA3.1 行与 roadmap 头计数修正（8 P2 / 17 P3）、arm-index P1 计数措辞修正
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码为 0（勾选全部 Closure Gates 并写入 Closure Evidence 后复核）
  - Anti-Hollow 检查结果：4 份报告为真实审计产物（实际发现清单、P 级+归属标注、可追溯 file:line 引用、历史对照表），非模板空壳；本计划为纯审计零代码变更，scan-hollow 不适用（无可扫描的新实现代码）
  - Deferred 项分类检查：无 in-scope live defect 被降级——全部 P1/P2 归 MR1/MR2，非阻塞项均附明确裁定理由（审计结论修正/装饰项/设计裁定/watch-only）

Follow-up:

- no remaining plan-owned work（MA3 发现归 MR1/MR2 批量修复，由 roadmap R2.0 展开器承接）
- watch-only 项复核结论已登记（MISSING-AUTH/post-commit-SEMANTIC 维持、RACE 初步结论归 MA6.6 终局），不产生修复债务
