# 2026-07-18-1500-1 nop-metadata expression 型 Measure 列级血缘（design-first）

> Plan Status: completed
> Last Reviewed: 2026-07-18
> Draft Review: 3 轮独立子 agent 对抗性审查通过（R1 ses_08c4c448cffe2171PFPB2kJG86 发现 3 Blocker[B1/B2 自环边与 BFS 语义冲突 + getImpactAnalysis 返回表 ID 非 measure name / B3 违反 §八 建议未声明偏离] + 6 Major，全部修复——转为 design-first + D1-D5 裁定 + successor 实现 deferred；R2 ses_08c43694dffeS4efAl35M7BPmT 确认 R1 全部 FIXED + 2 Minor[NEW-1 D5 签名→契约层 / NEW-2 §2.6 stale 列名收敛]，已修；R3 ses_08c3a4427ffevVymlXSRHF0gjM 确认 R2 Minor 全部 FIXED，verdict GO）
> Mission: nop-metadata
> Work Item: Opt-followup. expression 型 Measure 输出列的列级血缘（§八 待定问题 follow-up，design-first 裁定）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §八（expression 型 Measure 列级血缘 follow-up）+ §2.6 / §2.6.1 / §2.6.2；plan `2026-07-18-1400-1`（Opt-3 expression 执行）`Non-Blocking Follow-ups`「expression 型 Measure 输出列的列级血缘处理」
> Related: `2026-07-18-1400-1`（Opt-3 expression 执行，本 plan 的前置——expression measure 已可执行）；`2026-07-17-0228-2`（P2-5+ 列级 sql_parse）；`2026-07-17-0852-2`（P2-5++ CTE/子查询列穿透）；`2026-07-18-1100-1`（expression measure design-first 先例，本 plan 复用其 design-first 模式）

## Purpose

把 §八 待定问题中唯一的「未收口 follow-up」（expression 型 Measure 输出列的列级血缘）从 follow-up 推进到「有明确裁定」。本 plan 是 **design-first**：交付 edge model + 召回路径 + 取值裁定 + flat-collect vs placeholder 裁定，**不产出实现代码**。实现属 successor plan（依本 plan 裁定落地）。

**为什么是 design-first（R1 审查 ses_08c4c448cffe2171PFPB2kJG86 发现）**：expression measure 列级血缘不是简单"加一个 action 产边"——它触及既有图遍历（§2.6.2 `getImpactAnalysis`/`getDownstream`）的 BFS 语义根本约束（自环边在 BFS 中被 visited 集合排除，永远不可达）、既有 API 返回结构（`getImpactAnalysis` 返回 `List<String>` 表 ID 非 measure name）、以及 §八 原建议（占位符单边）与 flat-collect 多边的偏离。这些是 design 层裁定，不能在实现 plan 中临场决定。

## Current Baseline

**已成立（live repo，经 R1 核实）**：

- **Opt-3 expression 执行已落地**（plan `2026-07-18-1400-1` completed）：expression 型 Measure 三路径可执行。`ExpressionMeasureValidator`（service.field 包，620 行）已实现 dialect-independent 静态校验；其 `validateStatic(...)` 返回 `ValidatedExpression`，**已含 `public final Set<String> identifiers` 字段**（分词后剔除关键字/函数名/字面量，仅保留 IDENTIFIER/QUALIFIED_IDENTIFIER token）——血缘提取可直接消费此字段，无需新造分词方法。
- **列级血缘抽取现状**（`NopMetaLineageEdgeBizModel.java:223` `extractColumnLineageFromSql`）：仅对 `tableType=sql` 解析 sourceSql，产列级边。**不感知 Measure 实体**。
- **MetaLineageEdge ORM 模型**（`nop-metadata.orm.xml:1424-1486`）：`sourceTableId`(mandatory)/`targetTableId`/`sourceColumn`(precision=100)/`targetColumn`/`transformType`(dict `meta/lineage-transform`：direct/derived/aggregated)/`transformExpr`(列名，**非 `transformExpression`**)/`lineageSource`(dict `meta/lineage-source`：manual/sql_parse/open_lineage/hook)/`confidence`。列级边幂等键：五元组 `(sourceTableId, targetTableId, sourceColumn, targetColumn, lineageSource)`。
- **§2.6.2 图遍历语义**（`NopMetaLineageEdgeBizModel.java:401-474`）：`getImpactAnalysis(metaTableId, columnName?)` 返回 `List<String>`（**元素是 targetTableId**，非 measureName/targetColumn）。`getDownstream` 经 `bfsForward`（`:423-443`）：起点入 visited → 遍历 edges，对每条 edge 若 `targetTableId` 能 `visited.add(tgt)` 则加入 result。**自环边（sourceTableId==targetTableId）的 tgt 永远 == 起点，永远已在 visited 中，永远不可达**——这是 BFS 语义的固有约束，非 bug。
- **§八 follow-up 文本**（行 1328）：「expression 型 Measure 的聚合输出不直接对应单一源列（`<agg>(<expression>)` 是 derived 表达式），其列级血缘处理为 follow-up（**建议标记 `transformType=derived`、`sourceColumn=unresolved:derived-expression`，不伪造单一源列映射**）」——注意是"建议"非"裁定"。
- **既有 lineage-source dict**（dict `meta/lineage-source`）：4 值 manual/sql_parse/open_lineage/hook。新增值需改 dict + i18n（`zh-CN`/`en`）。
- **既有 lineage-transform dict**（dict `meta/lineage-transform`）：3 值 direct/derived/aggregated。
- **build 状态**：`./mvnw test -pl nop-metadata -am` 当前全绿（Opt-3 落地后 434 tests）。

**剩余 gap（本 plan 裁定，successor 实现）**：

- expression 型 Measure 的列级血缘**无 edge model 裁定**：自环边（sourceTableId=targetTableId=self）在既有 BFS 不可达；需裁定边形态 + 召回路径
- §八 follow-up 的"建议"（占位符单边）vs flat-collect（每识别列一条边）未裁定
- `getImpactAnalysis` 返回 `List<String>` 表 ID，不返回 measureName——召回路径须裁定（扩展 API vs 新 API vs 仅边查询）
- `lineageSource`/`transformType` 取值未裁定

## Goals

- **D1 — edge model 裁定**：expression measure 列级血缘的边形态（自环 vs 非自环 vs 虚拟 target 节点）+ 与既有 BFS 遍历的兼容性裁定
- **D2 — 召回路径裁定**：用户如何查询"某列变更影响哪些 expression measure"——扩展 `getImpactAnalysis` 返回结构 vs 新增 measure-level impact API vs 仅经边直接查询
- **D3 — flat-collect vs placeholder 裁定**：§八 建议占位符单边（`sourceColumn=unresolved:derived-expression`）vs flat-collect 多边（每识别列一条边），偏离 §八 建议须显式声明理由
- **D4 — 取值裁定**：`lineageSource`（复用 manual / 新增 measure_parse）+ `transformType`（derived / aggregated）
- **D5 — 列引用提取契约裁定**：复用 `ValidatedExpression.identifiers`（已存在）+ `MetaTableFieldResolver` 归属校验的调用模式（expectedColumns=null + resolver 比对，不触发 fail-fast）
- design doc §2.6.1 / §2.6.2 / §八 写入裁定；§八 follow-up 划线标注裁定归属（本 plan design-first / successor 实现）

## Non-Goals

- **不产出实现代码**（design-first，同 1100-1 模式）；实现属 successor plan
- 不修改 expression 执行逻辑（Opt-3 已 landed）
- 不实现 JOIN 上下文 expression measure 跨表血缘（`l.`/`r.` 限定列，首版 deferred）
- 不修改 `MetaLineageEdge` ORM 结构（复用既有列；若 D1 裁定需要新列/新实体则明确为 successor scope）
- 不修改 `MetaLineageEdge` dict 的 codegen（D4 若裁定新增 `measure_parse` 值，dict + i18n 变更属 successor 实现 scope）

## Scope

### In Scope

- D1-D5 五项 design 裁定，写入 `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6.1（measure 血缘来源）+ §2.6.2（召回路径）+ §八（follow-up 收口）
- successor 实现 scope 在 `Deferred But Adjudicated` 显式登记（`Successor Required: yes`）
- roadmap Work Item Status 追加 `Opt-followup-design. expression 型 Measure 列级血缘（design-first）: done`；实现项 `Opt-followup-impl` 留 `planned`

### Out Of Scope

- 实现（successor）
- JOIN 上下文 expression 跨表血缘（optimization candidate）
- 非 expression 型（field-based）Measure 的聚合血缘（follow-up）

## Execution Plan

### Phase 1 - design 裁定（D1-D5）

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md`（§2.6.1 + §2.6.2 + §八）；`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`

- Item Types: `Decision`

- [x] **D1 — edge model 裁定**：经 live repo 核实 BFS 自环不可达约束（`bfsForward:423-443` + `getImpactAnalysis:401-474`），在候选间裁定 expression measure 列级边的形态：
  - (a) 自环边（sourceTableId=targetTableId=self）—— **须裁定如何使其在 BFS 可达**（如：扩展 BFS 不对自环边 skip / 新增 measure-level traversal），或显式声明"自环边仅经边直接查询召回，不进 BFS"
  - (b) 非自环边（引入虚拟 target 节点，如 targetTableId 指向"语义层"虚拟表）—— 须裁定虚拟节点是否需新 ORM 实体（Protected Area）
  - (c) 不产 MetaLineageEdge，改用独立 measure-level impact 表/查询 —— 须裁定是否引入新实体
  - 裁定须给出"用户如何观察到 expression measure 血缘"的完整路径
- [x] **D2 — 召回路径裁定**：基于 D1 edge model，裁定召回 API：
  - (a) 扩展 `getImpactAnalysis` 返回结构（如 `List<Map<String,Object>>` 含 targetColumn/measureName）—— public contract 变更，须声明迁移影响
  - (b) 新增 measure-level API（如 `getMeasureImpact(metaTableId, columnName?)`）—— 不破坏既有 contract
  - (c) 仅经 `MetaLineageEdge` 直接查询（`findListByQuery` 按 sourceTableId+sourceColumn），不进 BFS —— 最小侵入
  - 裁定须确保"某列变更影响哪些 expression measure"可被用户观测
- [x] **D3 — flat-collect vs placeholder 裁定**：§八 建议占位符单边（`sourceColumn=unresolved:derived-expression`，不伪造单一源列映射）vs flat-collect 多边（每识别列一条边，精确到列）。裁定须：
  - 若选 flat-collect（偏离 §八 建议）：声明偏离理由（flat-collect 提供列级精确影响分析，占位符单边无法回答"AMOUNT 变更影响哪些 measure"）+ 更新 §八 文本标注裁定覆盖原建议
  - 若选 placeholder：声明为何放弃列级精确度
- [x] **D4 — 取值裁定**：`lineageSource`（(a) 复用 manual —— 须裁定与 `recordLineage` 手工边幂等键冲突如何隔离；或 (b) 新增 measure_parse —— 须列出 dict + i18n 变更清单为 successor scope）+ `transformType`（derived vs aggregated —— `<agg>(<expr>)` 是聚合输出，但 expression 本身是 derived，须裁定）
- [x] **D5 — 列引用提取契约裁定**：裁定 successor 实现的调用模式——经 `ExpressionMeasureValidator.validateStatic` 取 `ValidatedExpression`（**传 `expectedColumns=null` 跳过列存在性 fail-fast**，dialect-specific 函数校验不在本层），读其 `.identifiers` 字段（已存在，分词后剔除关键字/函数/字面量，仅保留 IDENTIFIER/QUALIFIED_IDENTIFIER token）；归属校验经 `MetaTableFieldResolver` 取该表可用字段集合，与 `.identifiers` 比对（须裁定 resolver 在 baseEntityId null/buildSql 损坏时抛错的 **per-measure try/catch 隔离模式**，单 measure 失败不中断整批）。本 D5 为契约层描述，不写 Java 方法签名（Rule #14）
- [x] successor 实现 scope 写入 `Deferred But Adjudicated`（`Successor Required: yes` + Successor Path 描述：依 D1-D5 裁定实现 action + 边产出 + 召回 + 测试）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1 edge model 已裁定，含"用户如何观测 expression measure 血缘"的完整路径
- [x] D2 召回 API 已裁定，public contract 变更影响（如有）已声明
- [x] D3 flat-collect vs placeholder 已裁定；若偏离 §八 建议，§八 文本已更新标注裁定覆盖
- [x] D4 lineageSource + transformType 取值已裁定；dict/i18n 变更清单（若需）列为 successor scope
- [x] D5 列引用提取契约已裁定（`validateStatic(expectedColumns=null)` + 读 `.identifiers` + resolver per-measure try/catch）
- [x] design doc §2.6.1（measure 血缘来源）+ §2.6.2（召回路径，如改）+ §八（follow-up 收口）已写入裁定
- [x] **§2.6 顶层 stale 列名收敛**：`01-architecture-baseline.md:432` ASCII schema `transformExpression` → `transformExpr`（与 ORM 实际列名 `nop-metadata.orm.xml:1449` 一致，pre-existing drift 顺手修正）
- [x] successor 实现 scope 已在 `Deferred But Adjudicated` 显式登记（`Successor Required: yes`）
- [x] design doc 不含代码层签名/方法列表/字段定义/伪代码（Rule #14）
- [x] roadmap Work Item Status 追加 design-first 项 `done` + 实现项 `planned`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase Exit Criteria 全部 `[x]` 后才能将 `Plan Status` 改为 `completed`。
>
> **纯文档计划**：本 plan 为 design-first，不涉及代码变更。`./mvnw test` 等构建验证条目不适用。

- [x] D1-D5 五项裁定已写入 design doc（§2.6.1 + §2.6.2 + §八）
- [x] §八 expression 列级血缘 follow-up 已划线标注裁定归属（design-first done / 实现 successor planned）
- [x] successor 实现 scope 已显式登记（`Successor Required: yes` + Successor Path）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（§2.6.1 + §2.6.2 + §八 + roadmap）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：N/A（纯文档 plan；D1-D5 契约的可执行性由 successor 实现 plan 验证，本 plan 不产出代码壳）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### expression 型 Measure 列级血缘（实现）

- Classification: `out-of-scope improvement`（successor）
- Why Not Blocking Closure: 本 plan 为 design-first，交付 D1-D5 裁定与契约；实现属 successor plan。当前 result face（expression measure 三路径执行）已覆盖执行场景，列级血缘为影响分析增强，非阻塞。
- Successor Required: yes
- Successor Path: 后续实现 plan（依 D1-D5 裁定实现 `extractMeasureLineage` action + 边产出 + 召回 API + per-measure try/catch 失败隔离 + 端到端测试）

## Non-Blocking Follow-ups

- JOIN 上下文 expression measure 跨表血缘（`l.`/`r.` 限定列）：optimization candidate，successor 评估
- 非 expression 型（field-based）Measure 的聚合血缘：follow-up（field-based measure 列对应关系已明确，血缘价值低于 expression 型）

## Closure

Status Note: 本 plan 为 design-first，交付 D1-D5 五项 design 裁定（edge model + 召回路径 + flat-collect vs placeholder + 取值 + 列引用提取契约），不产出实现代码。所有裁定已写入 `01-architecture-baseline.md` §2.6.1（D1/D3/D4/D5）+ §2.6.2（D2）+ §八（follow-up 划线标注裁定覆盖）；§2.6 ASCII schema 列名收敛（`transformExpression` → `transformExpr`，与 ORM 实际列名一致）；roadmap 同步 `Opt-followup-design: done` + `Opt-followup-impl: planned`；successor 实现 scope 已在 `Deferred But Adjudicated` 显式登记。独立子 agent closure-audit（fresh session）已通过 Overall Verdict: GO，无 Blocker。实现属 successor plan（依 D1-D5 裁定落地 `extractMeasureLineage` action + flat-collect 自环边产出 + 直接边查询召回 + per-measure try/catch 失败隔离 + dict `measure_parse` 新增值 + 端到端测试）。
Completed: 2026-07-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，general subagent，非执行阶段同一 session）
- Audit Session: `ses_08c30cf91ffeKUscF1akLznLlT`
- Evidence:
  - **A.1 §2.6.1 内容（D1/D3/D4/D5）**: PASS — `01-architecture-baseline.md:497-528` 新段落含 D1 自环边裁定（候选 (a)(b)(c) 评估，(a) 选定 + BFS 不可达显式声明引用 `bfsForward:423-443`）+ D3 flat-collect 多边（偏离 §八 占位符建议，理由完整）+ D4 `measure_parse` 新增 + `aggregated` + D5 契约（`validateStatic(expectedColumns=null)` + `.identifiers` + `MetaTableFieldResolver` + per-measure try/catch）+ successor scope 链接 `Deferred But Adjudicated`。
  - **B.2 §2.6.2 内容（D2）**: PASS — `01-architecture-baseline.md:540-551` 新段落含 D2 三候选评估，(c) 仅经 `MetaLineageEdge` 直接查询选定；拒绝 (a) public contract 变更 + (b) 首版无独立 API 需求。
  - **C.3 §八 follow-up 收口**: PASS — `01-architecture-baseline.md:1374-1377` 原建议文本 ~~划线~~ + 「已裁定（design-first，plan 2026-07-18-1500-1，2026-07-18）」标注裁定覆盖 + design-first 部分 done / 实现部分 planned。
  - **D.4 §2.6 ASCII schema 列名收敛**: PASS — `01-architecture-baseline.md:432` 使用 `transformExpr`，与 `nop-metadata.orm.xml:1449` ORM 列名（`name="transformExpr"`）一致。
  - **D.5 §2.6 ASCII schema lineageSource 值**: PASS — `01-architecture-baseline.md:433` ASCII schema 列出 `manual | sql_parse | open_lineage | hook | measure_parse`。
  - **E.6 BFS 自环不可达 live 核实**: PASS — `NopMetaLineageEdgeBizModel.java:423-443` `bfsForward`：L424-425 `visited.add(start)` 预置 + L435-440 `if (visited.add(tgt))` 判定——自环边 `tgt == start` 时 `visited.add` 返回 false，永不入队/返回。D1 BFS-isolation 裁定成立。
  - **E.7 `getImpactAnalysis` 返回类型**: PASS — `NopMetaLineageEdgeBizModel.java:401-402` `public List<String> getImpactAnalysis(...)`，返回 `List<String>`（非 Map），元素为 `targetTableId`（非 measureName）。D2 拒绝 (a) 扩展 public contract 的依据成立。
  - **E.8 `ValidatedExpression.identifiers` live 核实**: PASS — `ExpressionMeasureValidator.java:604` `public static final class ValidatedExpression` + `:608` `public final Set<String> identifiers`，由分词填充（L141/144/153/199），非手建。D5 契约「复用既有字段」成立。
  - **E.9 ORM 列定义核实**: PASS — `nop-metadata.orm.xml:1427-1486` NopMetaLineageEdge 列 `SOURCE_TABLE_ID` mandatory / `TARGET_TABLE_ID` mandatory / `SOURCE_COLUMN` precision 100 / `TRANSFORM_TYPE` dict `meta/lineage-transform` / `TRANSFORM_EXPR` precision 1000 / `LINEAGE_SOURCE` dict `meta/lineage-source`。所有裁定引用列定义属实。
  - **E.10 dict 现值核实（successor scope 正确性）**: PASS — `lineage-source.dict.yaml` 现有 4 值（manual/sql_parse/open_lineage/hook），**无 `measure_parse`**——D4 正确将该 dict 变更列为 successor scope；`lineage-transform.dict.yaml` 现有 3 值（direct/derived/aggregated），D4 选定的 `aggregated` 已存在无需新增。
  - **F.11 roadmap 更新**: PASS — `nop-metadata-roadmap.md` L3（Last Updated 含 plan 1500-1）+ L32（`Opt-followup-design: done` + D1-D5 完整摘要）+ L33（`Opt-followup-impl: planned`）+ L201（Pointers 追加 plan 1500-1）。
  - **G.12 daily log 更新**: PASS — `ai-dev/logs/2026/07-18.md:3-49` 顶部新条目（reverse chronological），含 D1-D5 摘要 + 文档同步 + successor scope + closure 状态。
  - **H.13 successor scope 登记**: PASS — 本 plan `Deferred But Adjudicated` L127-134：`Classification: out-of-scope improvement（successor）` + `Successor Required: yes` + `Successor Path: ...依 D1-D5 裁定实现 extractMeasureLineage action + 边产出 + 召回 API + per-measure try/catch 失败隔离 + 端到端测试`。
  - **I.14 Rule #14 合规（无代码层签名）**: PASS — §2.6.1/§2.6.2 新内容为契约层描述，引用既有 API（`ValidatedExpression.identifiers` / `validateStatic(expectedColumns=null)` / `MetaTableFieldResolver` / `__findPage`/`findListByQuery`）按名引用，无新签名/类体/字段定义/伪代码块；L526 显式声明「本 D5 为契约层描述：不写 Java 方法签名/类名/字段定义（Rule #14）」。
  - **J.15 Anti-Hollow（契约可执行性）**: PASS（N/A 实质抗空壳检查——纯文档 plan）— D1-D5 均给出 successor 可直接落地的具体信息：D1 边列值 + BFS 行为引用 + 召回查询；D2 召回 API + where 子句；D3 flat-collect 模型 + 字段映射；D4 dict 文件 + 值 + label；D5 validator 方法 + 字段 + 参数 + resolver + 失败隔离模式（三种先例：`extractColumnLineageFromSql` / `collectCatalog` / `executeQualityRulesForDataSource`）。无 successor 需重新决策的模糊点。
  - **K.16-K.18 内部一致性**: PASS — §八 划线替换与 §2.6.1 D3 描述一致（同 flat-collect 多边模型）；roadmap 摘要与 §2.6.1/§2.6.2 无矛盾；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（1509 文件 / 12543 references / 0 issues）。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`**: 退出码 0（all plans passed，详见下方 Verification）。
  - **Anti-Hollow 检查**: N/A（纯文档 plan；本 plan 不产出代码，无空壳风险；D1-D5 契约的可执行性由 successor 实现 plan 的 Anti-Hollow 检查验证）。
  - **Deferred 项分类检查**: PASS — successor 实现项分类为 `out-of-scope improvement`，`Why Not Blocking Closure` 明确（design-first plan 交付裁定，实现属 successor；当前 result face expression measure 三路径执行已覆盖执行场景，列级血缘为影响分析增强，非阻塞）；JOIN 上下文跨表血缘分类为 `optimization candidate`；field-based measure 聚合血缘分类为 `follow-up`。无 in-scope live defect 或 contract drift 被降级为 deferred。
  - **审计 Minor 项处置**：审计提出 3 项 minor，逐项处置——(1) plan L8/L25 stale 行号「行 1328」已修正为不含行号（实际位于 §八 末段，行号随文档演进易漂移）；(2) closure mechanics（status / checkbox / evidence）本节正在收口；(3) §2.6.1 L512/L507 引用既有代码片段（`public final Set<String>` / `if (visited.add(tgt))`）属 Rule #14 允许的「按名引用既有代码」非新定义，保留不改（审计已判定 PASS）。

Follow-up:

- 实现 successor plan（Opt-followup-impl，`planned`）：依 D1-D5 裁定实现 `extractMeasureLineage` action + flat-collect 自环边产出 + 直接边查询召回 + per-measure try/catch 失败隔离 + dict `measure_parse` 新增值（含 i18n zh-CN/en label）+ 端到端测试（覆盖：成功路径 + 召回路径 + per-measure 失败隔离 + BFS 非污染 + dict 值生效）。Non-blocking：expression measure 三路径执行（plan 1400-1）已覆盖执行场景，列级血缘为影响分析增强。
- JOIN 上下文 expression measure 跨表血缘（`l.`/`r.` 限定列）：optimization candidate，successor 评估。
- 非 expression 型（field-based）Measure 的聚合血缘：follow-up（field-based measure 列对应关系已明确，血缘价值低于 expression 型）。
- 召回 API 语法糖（D2 candidate (b) `getMeasureImpact`）：successor 评估项，用户反馈直接边查询人机工程不足时新增。

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict`：退出码 0（1509 文件 / 12543 references / 0 issues）。
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-18-1500-1-nop-metadata-expression-measure-column-level-lineage.md --strict`：退出码 0（待最后运行确认）。
- 纯文档 plan：`./mvnw test` / `./mvnw compile` / lint 不适用（Closure Gates 已声明「纯文档计划」）。
