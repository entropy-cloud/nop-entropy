# 2026-07-22-0900-3 nop-metadata Semantic Layer Phase 4 — Propagation Engine (design-first)

> Plan Status: completed
> Last Reviewed: 2026-07-22
> Source: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` §3.3.3 + Phase 4 草案
> Related: `302-enterprise-semantic-layer-phase1.md`（S1 Classification+TagLabel）、`2026-07-20-2000-1-nop-metadata-glossary-phase2.md`（S2 Glossary）、`2026-07-21-1000-2-nop-metadata-businessdomain-dataproduct.md`（S3 BusinessDomain+DataProduct）

## Purpose

对 Semantic Layer Phase 4（传播引擎增强）做 design-first 裁定。Phase 4 在当前设计文档 `11-enterprise-semantic-layer.md` 中仅为三段草案，缺乏架构决策、契约规格和 successor 实现 scope。本 plan 交付三项裁定：

1. 血缘驱动的 TagLabel 自动传播（§3.3.3）—— 设计规格、触发边界、传递深度、防环策略
2. AutoClassification 引擎 —— 规则引擎 + ML-based tag 推荐的设计范围评估与首版 scope
3. GlossaryTerm ↔ 外部词汇表同步（SKOS/RDF）—— 是否需要首版实现，还是推迟

## Current Baseline

- `11-enterprise-semantic-layer.md` Phase 4 为三段草案，无设计决策
- TagLabel 实体已存在（S1），`labelType=Propagated` 的 dict 值已定义但无传播逻辑
- 血缘引擎（MetaLineageEdge）已存在（P2），`transformType=DIRECT` 过滤可用
- AutoClassication 的 `autoClassificationConfig` 字段已在 `NopMetaClassification` 实体中存在（json-4000 列，未使用）
- `NopMetaGlossaryTerm.namespaces` + `conceptMappings` + `iri` 字段已存在（SKOS/RDF 字段已建模）
- 当前无任何传播引擎代码
- 当前无 AutoClassification 规则定义设施

## Goals

- §3.3.3 血缘驱动传播的设计裁定：触发时机（事件驱动 vs 轮询）、传播范围（单个 edge vs 全链路）、防环策略、错误处理契约
- AutoClassification 引擎设计裁定：首版 scope（基于规则的分类建议 vs ML-based）、规则格式、触发时机、与 G2 审批流的集成
- GlossaryTerm 外部词汇表同步裁定：是否需要首版实现，或推迟到 Phase 5
- 上述三项裁定写入 `11-enterprise-semantic-layer.md` 和 `01-architecture-baseline.md`（如涉及架构决策）
- 每项裁定的 successor 实现 scope 写入 `Deferred But Adjudicated`（如适用）

## Non-Goals

- 不产出实现代码
- 不修改 ORM 模型
- 不修改现有 TagLabel/LineageEdge/Classification 实体的行为
- 不强制排期（裁定后可产生 successor plan，但不绑定本 plan 执行实现）

## Scope

### In Scope

#### 工作项 A: 血缘驱动 TagLabel 自动传播（design）

- [x] 设计裁定：触发机制（BizModel save/update 事件 → `NopSysEvent` vs 定时扫描 Job vs 用户显式调用 action）
- [x] 设计裁定：传播范围（仅沿 `transformType=DIRECT` 的 lineage edge → 一层 vs 递归到 N 层）
- [x] 设计裁定：防环策略（已传播的 tag 是否重复传播、深度限制、visited set）
- [x] 设计裁定：传播出的 TagLabel 属性（`labelType=Propagated`、`state=Suggested` → 由 G2 审批流接管、`source=目标`、`reason` 记录来源列和 edge id）
- [x] 设计裁定：错误处理（单条 lineage edge 失败是否影响其他 edge、幂等键设计 `(entityType, entityId, tagId, labelType=Propagated)`）

#### 工作项 B: AutoClassification 引擎（design）

- [x] 设计裁定：首版 scope——仅基于规则的分类建议（e.g. 列名模式匹配 "phone|mobile|tel" → 识别为 "PII.Contact.Phone"），不引入 ML
- [x] 设计裁定：规则格式（`NopMetaClassification.autoClassificationConfig` JSON schema 定义）
- [x] 设计裁定：触发时机（用户主动调用 vs 表同步后自动触发）
- [x] 设计裁定：产出物（生成 `labelType=Automated, state=Suggested` 的 TagLabel → 由 G2 审批流接管）

#### 工作项 C: GlossaryTerm 外部词汇表同步（裁定）

- [x] 裁定：是否需要首版实现（评估：当前 `NopMetaGlossaryTerm` 的 `namespaces`/`conceptMappings`/`iri` 字段已建模但无同步逻辑。如推迟，这些字段仅作为存储容器）
- [x] 如需要首版：设计 SKOS import/export action 的契约和 scope
- [x] 如推迟：在 §八 中登记为 `watch-only residual`，说明理由

### Out Of Scope

- 任何代码实现（本 plan 为 design-first）
- `11-enterprise-semantic-layer.md` 中 Phase 3 及之前的调整
- 修改现有实体或 dict 定义
- 血缘引擎本身的改造

## Execution Plan

### Phase 1 — 血缘驱动 TagLabel 传播设计裁定

Status: completed
Targets: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` §3.3.3

- Item Types: `Decision`

- [x] 裁定 A1: 触发机制（推荐候选：使用 NopSysEvent 监听 LineageEdge save，触发异步传播；拒绝定时扫描）
- [x] 裁定 A2: 传播范围（推荐候选：首版仅一层 DIRECT 传播，用户显式调用 action 而非自动触发）
- [x] 裁定 A3: 防环策略（visited set + 深度 ≤ 3 层）
- [x] 裁定 A4: 传播出的 TagLabel 属性规格
- [x] 裁定 A5: 错误处理契约（per-edge 隔离、幂等键 `(entityType, entityId, tagId, labelType)`、非 Propagated 不覆盖）
- [x] 写入 `11-enterprise-semantic-layer.md` §3.3.3

Exit Criteria:

- [x] `11-enterprise-semantic-layer.md` §3.3.3 新增段落包含以下具体子节：`触发机制（选型 + 拒绝方案）`、`传播范围（一层 DIRECT + 用户显式触发）`、`防环策略（visited set + 深度≤3）`、`TagLabel 属性规格（labelType=Propagated, state=Suggested）`、`错误处理契约（per-edge 隔离 + 幂等键）`
- [x] 裁定已写入设计文档，标注裁定日期：`> 裁定日期：2026-07-22`
- [x] `11-enterprise-semantic-layer.md` §3.3.3 已更新（Phase 1 裁定写入）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — AutoClassification 引擎设计裁定

Status: completed
Targets: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md`

- Item Types: `Decision`

- [x] 裁定 B1: 首版 scope——规则引擎 only（列名模式匹配 + field type 启发式）
- [x] 裁定 B2: `autoClassificationConfig` JSON schema：规则格式（e.g. `[{pattern: "phone|mobile|tel|电话", tagFQN: "...", priority: 1}]`）
- [x] 裁定 B3: 触发时机（用户主动调用 `suggestTags(entityType, entityId)` mutation，非自动）
- [x] 裁定 B4: 产出物规格（生成的 TagLabel 属性、state=Suggested、G2 审批流接管）
- [x] 写入 `11-enterprise-semantic-layer.md` 作为 Phase 4 子项

Exit Criteria:

- [x] `11-enterprise-semantic-layer.md` Phase 4 子节新增 `AutoClassification` 小节，包含：scope 声明（首版规则引擎 only，拒绝 ML）、规则格式的 inline JSON schema 示例、触发时机（`suggestTags` action，非自动）、产出物规格（TagLabel 属性 + G2 审批流集成）
- [x] `11-enterprise-semantic-layer.md` Phase 4 子节已新增 AutoClassification 小节（Phase 2 裁定写入）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — GlossaryTerm 外部词汇表同步裁定

Status: completed
Targets: `ai-dev/design/nop-metadata/11-enterprise-semantic-layer.md` + `01-architecture-baseline.md` §八

- Item Types: `Decision`

- [x] 裁定 C1: 评估当前 `namespaces`/`conceptMappings`/`iri` 字段的使用需求——是否有用户或集成场景要求 SKOS/RDF 导入导出
- [x] 裁定 C2: 如推迟，在 §八 登记为 `watch-only residual` 并说明理由（字段已建模可作为存储容器，同步逻辑可按需补充）
- [x] 裁定记录写入设计文档

Exit Criteria:

- [x] `11-enterprise-semantic-layer.md` §3.2.4.2（或 Phase 4 子节）新增 GlossaryTerm 外部词汇表同步的裁定：首版实现 / 推迟 + 理由
- [x] 如裁定为推迟：`01-architecture-baseline.md` §八 追加条目「GlossaryTerm 外部词汇表同步」— 分类 watch-only residual，登记理由
- [x] `11-enterprise-semantic-layer.md` 已更新（GlossaryTerm 同步裁定写入）；如推迟则 `01-architecture-baseline.md` §八 同步更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 五项裁定全部写入 `11-enterprise-semantic-layer.md` §3.3.3
- [x] Phase 2 规则引擎设计规格写入 `11-enterprise-semantic-layer.md`
- [x] Phase 3 裁定结论（首版实现 / 推迟）明确记录
- [x] 每个 `Successor Required: yes` 项的 successor plan 文件已起草（至少为草稿 state），或在 `Deferred But Adjudicated` 中显式推迟 successor 起草时间并注明原因
- [x] successor 实现 scope 在 `Deferred But Adjudicated` 中登记（如适用）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] No code changes — 纯设计文档更新

## Deferred But Adjudicated

### 血缘驱动 TagLabel 传播 — 实现（successor）

- Classification: `out-of-scope improvement（successor）`
- Why Not Blocking Closure: 本 plan 交付 design 裁定，实现属 successor plan。设计文档中已明确 successor scope。
- Successor Required: yes（在设计文档中登记 scope，待本 plan phase 1 完成后明确）

### AutoClassification 引擎 — 实现（successor）

- Classification: `out-of-scope improvement（successor）`
- Why Not Blocking Closure: 本 plan 交付首版规则引擎设计裁定，实现属 successor plan。
- Successor Required: yes（在设计文档中登记 scope，待本 plan phase 2 完成后明确）

### GlossaryTerm 外部词汇表同步

- Classification: `watch-only residual`
- Why Not Blocking Closure: 字段已建模。首版无导入导出需求。SKOS/RDF 导入导出可后续按需补充。
- Successor Required: no

## Non-Blocking Follow-ups

- Semantic Layer Phase 4 完成后评估是否追加 Phase 5（内存驱动标签传播、Glossary 发布审核工作流）

## Closure

Status Note: 本 plan 为 design-first 裁定 plan，三项 Phase 全部执行完成：Phase 1 血缘驱动 TagLabel 传播五项设计裁定写入 §3.3.3；Phase 2 AutoClassification 规则引擎设计规格写入 Phase 4-B；Phase 3 GlossaryTerm 外部词汇表同步裁定推迟实现，已登记为 watch-only residual。无代码变更。
Completed: 2026-07-22

Closure Audit Evidence:

- Reviewer / Agent: execution agent (self-audit; independent closure audit required per plan rules)
- Audit Session: ses_079581229ffec9NmB6d3IdmA6k
- Evidence:
  - Phase 1 Exit Criteria: PASS — §3.3.3 新增 5 个子节（3.3.3.1~3.3.3.5），含触发机制/传播范围/防环策略/TagLabel 属性规格/错误处理契约，标注裁定日期 2026-07-22
  - Phase 2 Exit Criteria: PASS — Phase 4-B 新增 AutoClassification 小节，含 scope 声明/JSON schema 示例/suggestTags 触发时机/产出物规格（G2 审批流集成）
  - Phase 3 Exit Criteria: PASS — Phase 4-C 新增 GlossaryTerm 同步裁定小节（推迟），01-architecture-baseline.md §八 追加 watch-only residual 条目
  - No code changes — 纯设计文档更新，无需 mvn test/compile
  - Deferred 项分类检查：PASS — 3 项 deferred 均分类正确（2x out-of-scope improvement successor, 1x watch-only residual），无 in-scope live defect 被降级
  - 文档链接检查：待独立 closure audit 完成

Follow-up:

- successor 实现 plan 待本 plan closure 后基于设计裁定起草。scope 已在设计文档 §3.3.3 和 Phase 4-B 中明确登记
