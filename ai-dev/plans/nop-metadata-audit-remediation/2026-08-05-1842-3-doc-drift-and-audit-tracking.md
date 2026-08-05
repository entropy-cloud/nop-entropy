# 文档契约漂移修复 + 审计追踪治理（实体表 21/39、META-004 枚举、I*Biz 断言、multi-audit P1 入库）

> Plan Status: active
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查 consensus——R1 `ses_02e781917ffemIWTq8vvO6WfJo`（1 Blocker：默认方向 (a) 与"纯文档计划/无代码变更"Closure Gates 矛盾——改为默认 (b) + (a) 分支构建/测试门禁指引；2 Major：40→39 接口计数、选项 (a) 无测试处置；6 Minor——全部修复）；R2 `ses_02ee342ffePAoJ7mU5p24LQl`（0 Blocker 0 Major，8 Minor 文本/路径修正——已修复）。全部 Blocker/Major 清零，裁定可执行。
> Source: `ai-dev/audits/2026-08-05-0655-multi-audit-nop-metadata-audit-remediation.md`（[P1-04][P1-05][P1-06]）、`ai-dev/audits/2026-08-05-0655-open-audit-nop-metadata-audit-remediation.md`（[AR-04][AR-05]）
> Related: 执行顺序 `{3}` of 3 — 与 `{1}`、`{2}` 无文件域冲突；治理修复（AR-05）依赖本批计划存在（登记引用），宜在 `{1}`/`{2}` 草拟后执行
> Mission: nop-metadata-audit-remediation

## Purpose

（a）修复 docs-for-ai 三处文档契约漂移：模块文档实体表格 21/39、source-anchors.md META-004 transformType 枚举错误、"每个 BizModel 都有接口"全称断言与 NopMetaSearch 例外冲突；（b）修复治理缺口：2026-08-05 两轮审计（multi-audit + open-audit）的全部 P1 从未登记入 arm-index / roadmap，导致 mission closure audit 的"0 untraceable"口径不覆盖这些发现——本计划将它们全部登记入追踪矩阵并附带本批 remediation plan 引用，恢复可追溯性。

## Current Baseline

2026-08-05 live repo 核对：

- **实体表格**（`docs-for-ai/03-modules/nop-metadata.md:19-41`）：表格列 21 个实体（止于 NopMetaManifest），`nop-metadata/model/nop-metadata.orm.xml` 实际 **39 个 `<entity>`**（rg -c 核实）——缺失 18 个：NopMetaOrmModel / NopMetaSemanticType / NopMetaEntityRelation / NopMetaEntityUniqueKey / NopMetaEntityIndex / NopMetaDomain / NopMetaDict / NopMetaDictItem / NopMetaPipeline / NopMetaReconciliationEntity / NopMetaModelChangedEvent / NopMetaGlossary / NopMetaGlossaryTerm / NopMetaClassification / NopMetaTag / NopMetaTagLabel / NopMetaBusinessDomain / NopMetaDataProduct（含 NopMetaClassification/NopMetaTagLabel/NopMetaGlossary 等有独立 BizModel + xmeta + 页面的实体）
- **META-004 枚举**（`docs-for-ai/04-reference/source-anchors.md:168`）：写 `transformType: direct/expression/aggregate`；代码三方一致为 `direct/derived/aggregated`——`_NopMetadataCoreConstants.java:149-159`（LINEAGE_TRANSFORM_DERIVED = "derived"、LINEAGE_TRANSFORM_AGGREGATED = "aggregated"）、`nop-metadata-meta/src/main/resources/_vfs/dict/meta/lineage-transform.dict.yaml`、`SqlColumnLineageExtractor.java:479-492`
- **I*Biz 全称断言**（`docs-for-ai/03-modules/nop-metadata.md:109`）："每个 BizModel 都实现了对应的 `INopMeta*Biz` 接口"；`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/` 下 **39 个接口**（ls 核实；audit 的"40 个/1/40 例外"为继承计数误差，实际 1/39 例外）**无 INopMetaSearchBiz**；`NopMetaSearchBizModel`（`nop-metadata/nop-metadata-service/.../search/NopMetaSearchBizModel.java`，@BizModel("NopMetaSearch")，含 rebuildSearchIndex @BizMutation + searchMetadata @BizQuery 两个自定义方法，bean 已注册于 app-service.beans.xml:57）为唯一例外；`TestNopMetaBizInterfaceCompleteness` 逐接口断言签名、**不含 NopMetaSearch**；`nop-metadata.md:168` 另有包路径省略写法（P2-23 归 backlog，本计划只处理全称断言）
- **治理缺口**：`ai-dev/audits/arm-index-nop-metadata.md` P1 汇总表（12 行数据）无 multi-audit 条目（grep "0655|P1-0[1-6]" 0 命中）；multi-audit 6 个 P1 + open-audit 5 个 P1 无任何 tracking 引用；MV closure audit "P1 12/12 PASS + 0 untraceable" 不覆盖这些输入（其中 2 个是实测可绕过的 SSRF）；两轮审计文件头在本批处理中已置 `Audit Status: planned`（此前为 open 且 11 个 P1 零引用）
- **登记 ID 约定**：`arm-unclosed-findings-nop-metadata.md` 已约定轮次限定 ID 格式（`<YYYY-MM-DD-HHmm>#<来源内编号>`），本批登记采用 `2026-08-05-0655#P1-01` / `2026-08-05-0655#AR-01` 形式，避免与历史 `P1-MA*-*` 编号混淆
- 两轮审计 P1 全集（本批 3 个 remediation plan 承接）：P1-01/P1-02/AR-01/AR-02（SSRF，plan `{1}`）、P1-03/AR-03（血缘 API，plan `{2}`）、P1-04/P1-05/P1-06/AR-04/AR-05（文档+治理，本 plan）
- 绿色基线：docs 无构建依赖；`check-doc-links.mjs --strict` 需 0 errors

## Goals

- `docs-for-ai/03-modules/nop-metadata.md` 实体表格覆盖全部 39 个实体（或显式注明完整清单入口 = orm.xml 39 个），表名与 orm.xml 一致
- `docs-for-ai/04-reference/source-anchors.md:168` 枚举修正为 `direct/derived/aggregated`（含 META-004 同包相关注释交叉核对）
- "每个 BizModel 都有接口"断言与实现一致：二选一裁定（默认 (b) 文档显式声明 NopMetaSearch Pseudo-BizModel 例外；选 (a) 新增接口则按 Phase 1 分支指引补齐构建/测试门禁）
- 两轮审计全部 11 个 P1 登记入 `arm-index-nop-metadata.md`（状态 planned + 本批 plan 引用，轮次限定 ID 格式 `2026-08-05-0655#P1-xx`/`#AR-0x`）+ roadmap 新增 MR5 工作项段 + `arm-unclosed-findings-nop-metadata.md` 登记段，恢复"0 untraceable"口径
- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Non-Goals

- 不处理 P2 批次（P2-01~27 / AR-06~10 归 Follow-up Backlog，见 roadmap）
- 不修改 orm.xml（实体清单以现有 39 个为准，仅文档同步）
- 不改 `NopMetaSearchBizModel` 的方法签名或行为（仅接口/文档面）
- 不回写历史审计文件（既有 arm-*nop-metadata* 只允许增补登记，不覆盖结论）
- 不执行带行为变更的代码改动（若裁定新增 INopMetaSearchBiz 接口，属纯接口声明 + BizModel implements，无签名/行为变更；仍按 Phase 1 (a) 分支指引补齐构建/测试门禁）

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-metadata.md` 实体表格补全 39（或注明入口）
- `docs-for-ai/04-reference/source-anchors.md:168` 枚举修正 + META-004 关联段落交叉核对
- `nop-metadata.md:109` 全称断言裁定与修正（新增接口 或 文档声明例外）
- `arm-index-nop-metadata.md` 登记 11 个 P1（planned + plan 引用，轮次限定 ID）；roadmap 新增 MR5 工作项段登记；`arm-unclosed-findings-nop-metadata.md` 追加登记段（11 项 P1 承接说明）
- `check-doc-links.mjs --strict` 验证

### Out Of Scope

- P2 批次的任何修复（仅登记 backlog）
- orm.xml / 生成物变更
- 其他 docs-for-ai 文件的批量审阅（P2-22/23/24/26 等归 backlog）

## Execution Plan

### Phase 1 - 模块文档实体表格补全 + I*Biz 断言裁定

Status: planned
Targets: `docs-for-ai/03-modules/nop-metadata.md`

- Item Types: `Fix | Decision | Proof`

- [ ] **实体表格补全（Fix）**：以 `nop-metadata/model/nop-metadata.orm.xml` 为准补全缺失 18 个实体行（表名与 orm.xml 逐一核对），或显式注明"以下为核心实体，完整清单见 orm.xml（39 个）"——执行时裁定，以读者不误判模块实体总量为准
- [ ] **I*Biz 断言裁定（Decision，默认方向 (b)）**：二选一——(a) 在 `nop-metadata-dao/src/main/java/io/nop/metadata/biz/` 新增 `INopMetaSearchBiz`（声明 rebuildSearchIndex + searchMetadata 两方法，`NopMetaSearchBizModel implements` 之，对齐其余 39 个接口模式，无行为变更）；(b) `nop-metadata.md:109` 显式声明 NopMetaSearch 为 Pseudo-BizModel 例外。**默认方向 (b)**：audit 允许二选一、接口无跨模块调用方（`ai-dev/plans/10-nop-metadata-search-integration.md:18,136` 记录 deferred）、本 plan 主题为文档治理，纯文档路径风险最低。**若执行者裁定选 (a)**，本 plan 不再是纯文档计划，必须同时：(i) Closure Gates 恢复 `./mvnw compile` + `./mvnw test -pl nop-metadata-dao,nop-metadata-service -am`；(ii) 按 Minimum Rule #25 在 `TestNopMetaBizInterfaceCompleteness` 补 INopMetaSearchBiz 方法签名断言（不允许 "No new test required" 豁免）；(iii) 记录 nop-metadata-api/dao 公共面新增声明。裁定结论与理由写入本 plan Phase 1 记录
- [ ] **全称断言文本修正（Fix）**：按裁定结果使 `:109` 断言与实现一致（补接口则保持原文；声明例外则加例外说明）
- [ ] 交叉核对：表格其余 21 行表名与 orm.xml 无漂移（audit 已确认无错误，执行时复核）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 模块文档实体清单覆盖 39 个实体（或显式入口说明），表名与 orm.xml 一致（rg 复核）
- [ ] `:109` 断言与实现一致（接口存在 或 例外声明在位）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本 plan 修改 docs 后必跑）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - source-anchors.md META-004 枚举修正

Status: planned
Targets: `docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Fix | Proof`

- [ ] **枚举修正（Fix）**：`:168` 改为 `direct/derived/aggregated`，交叉检查 META-004 同包注释（source-anchors.md META-004 行描述）
- [ ] **三方核实（Proof）**：常量类（`_NopMetadataCoreConstants.java:149-159`）/ dict（`nop-metadata-meta/src/main/resources/_vfs/dict/meta/lineage-transform.dict.yaml`）/ extractor（`SqlColumnLineageExtractor.java:479-492`）三处与实际枚举一致（文档修正后，全仓仅文档一处为错误表述，三方核对全通过）
- [ ] 全文件 grep 复核 `expression`/`aggregate` 残留在 source-anchors.md 中与 transformType 相关的表述（防同类错误复发）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] source-anchors.md 中 transformType 枚举与代码三方一致（grep 复核）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 治理修复：11 个 P1 登记入 arm-index + roadmap

Status: planned
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Decision`

- [ ] **P1 登记（Fix，AR-05 主体）**：在 `arm-index-nop-metadata.md` P1 汇总表登记 11 个 P1（P1-01..06 + AR-01..05），**ID 采用轮次限定格式 `2026-08-05-0655#P1-01` 等**（沿用 arm-unclosed-findings 约定，避免与历史 `P1-MA*-*` 及历史 AR-01/02 混淆），状态 = planned，附来源审计路径 + 本批 remediation plan 引用（`{1}`/`{2}`/`{3}`）；**镜像守卫：若 {1}/{2} 先行收口已将自身行写为 fixed，登记时不得回退状态（已 fixed 行保持 fixed + 补 plan 引用，缺失行才新增 planned）**；同时更新"未闭包 P1 数"口径（原 0 项 → 11 项 planned/视先行收口情况，注明来源轮次）
- [ ] **roadmap 登记（Fix）**：roadmap **新增 MR5 工作项段**（11 个 P1 全部登记为 planned + plan 引用，标注来源 multi-audit 2026-08-05-0655 + open-audit 2026-08-05-0655）——AR-05 要求 arm-index + roadmap 双登记，**不允许跳过**（roadmap MR1-4 已 done，新段承接本轮输入）；**同步 roadmap 头部注记**（"M0→MG 收尾"口径后追加 MR5 说明）
- [ ] **arm-unclosed-findings 登记（Fix）**：`arm-unclosed-findings-nop-metadata.md` 追加登记段：11 项 P1 → 状态 planned，承接 plan 引用 + 指向 arm-index（该文件是 mission 的"未闭包清单"，防止第二份索引 stale）
- [ ] **追踪口径修复（Decision）**：在 arm-index 汇总段注明 MV closure audit "P1 12/12 PASS + 0 untraceable" 口径不覆盖 2026-08-05 两轮审计输入，本批登记后恢复可追溯（修复声明口径，不回写历史 audit 结论）
- [ ] 交叉核对：**逐 ID 核对新增登记段 11 行**（或 grep 登记段内 "multi-audit 2026-08-05" 标记），**不得使用 `AR-0[1-5]` 模式 grep 全文件**（arm-index 历史行已有 AR-01/AR-02 等 12 处命中，会产生假阳性）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 11 个 P1 全部在 arm-index 可查（登记段逐 ID 核对，grep 命中 11 项）
- [ ] roadmap MR5 段登记在位（11 项，不允许跳过）
- [ ] arm-unclosed-findings-nop-metadata.md 登记段在位
- [ ] 追踪矩阵口径说明已更新（无 untraceable finding）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（文档链接检查 + closure audit）

Status: planned
Targets: 全量文档检查 + closure audit

- Item Types: `Proof`

- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（全量）
- [ ] 独立子 agent closure audit（fresh session）逐项核对，证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] check-doc-links --strict 0 errors
- [ ] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [ ] 无静默降级：文档契约漂移 + 治理缺口为 fixed，无 live defect 被降级
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 三处文档契约漂移（实体表 / META-004 枚举 / I*Biz 断言）与 live repo 一致
- [ ] 11 个 P1 全部登记入 arm-index（planned + plan 引用）+ roadmap MR5 段 + arm-unclosed-findings 登记段，无 untraceable finding
- [ ] 必要 focused verification 已完成（grep 核对 + check-doc-links）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证文档表述与 live repo 一致（抽样核对实体表行数 / 枚举值 / 接口文件存在性），无空头声明
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 构建验证按裁定分支：默认 (b) 纯文档路径 → `./mvnw` 验证条目删除（guide 纯文档计划条款）；若执行者选 (a)（新增 INopMetaSearchBiz）→ 必须 `./mvnw compile` + `./mvnw test -pl nop-metadata-dao,nop-metadata-service -am` 通过，且接口签名断言测试（TestNopMetaBizInterfaceCompleteness 扩展）全绿

## Deferred But Adjudicated

### P2 批次（multi-audit P2-01~27 + open-audit AR-06~10）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 mission 规则 P2 不驱动 remediation plan，全部登记入 roadmap Follow-up Backlog（本次随本计划一并写入），不阻塞本 plan 的文档契约收敛；无 P2 属于本 plan in-scope
- Successor Required: `no`（backlog 跟踪，按需立项）
- Successor Path: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（## Follow-up Backlog）

## Non-Blocking Follow-ups

- （按执行结果补充）

## Closure

Status Note: （执行收口时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent，fresh session）
- Evidence: （每条 Exit Criterion / Closure Gate 验证结果 + 工具退出码，收口时写入）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）

## Optional Sections

- `## Risks And Rollback`：新增 INopMetaSearchBiz 接口属 nop-metadata-api/dao 公共面——但为纯接口声明 + BizModel implements（无签名/行为变更），回滚成本 = 删除接口文件 + 还原 implements；文档变更全部 git 可逆
- `## Outdated Note`：若执行期间实体清单发生变化（orm.xml 增删实体），以执行时 live repo 为准同步表格
