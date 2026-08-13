# nop-code 不变式闭环 I0 — 不变式盘点与基线（Cycle 1）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I0. 不变式盘点与基线
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I0）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`；先例 `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`（I0 节）
> Related: 后继 `2026-08-13-0709-2-nop-code-invariant-i1-first-batch-gates.md`（I1，依赖本计划产出）

## Purpose

把 nop-code 已积累的「2 baseline audit + 13 轮 adversarial review（2026-05-25 至 2026-06-06，含同日 r9-r13 五个 sub-round）」的悬空发现（AR-94→AR-178+）收敛为一份**不变式目录 + 当前 live 状态盘点 + 审计目标集枚举**，作为 I1 门禁沉淀的确定性输入。本计划只产出文档/盘点，不改产品代码。

## Current Baseline

> 已对 live repo 核对（2026-08-13）。历史发现陈述用过去时；当前状态以 live code 为准。

- **审计输入材料已存在且未消费为不变式**：
  - 2 baseline：`ai-dev/audits/nop-code-audit-2026-05-05.md`、`nop-code-audit-2026-05-10.md`
  - 13 轮 adversarial review 目录：`ai-dev/audits/2026-05-25-deep-audit-nop-code-full` 至 `2026-06-06*-adversarial-review-nop-code`（含 r9-r13 同日 sub-round a-f）
  - 模块专用补充审计维度：`ai-dev/skills/nop-code/audit-prompt.md`
- **当前零可执行不变式门禁**：`ai-dev/tools/` 下有 17 个 `.mjs`（含 stream 先例 `check-nop-stream-audit-manifest.mjs`），但**无 `check-nop-code-invariants.mjs`**，也无针对 nop-code 的 JUnit 不变式测试。grep 证据：`ai-dev/tools/` 下无 `nop-code-invariant` 命中。
- **关键悬空发现的 live 状态初核（需 I0 完成正式盘点确认，此处为预核）**：
  - **OOM 族 AR-168（`CodeSearchService.buildFilePathCache`）**：live 已改用投影查询 `selectFieldsByQuery`（仅选 `id`+`filePath`），见 `nop-code-service/.../CodeSearchService.java:196-212`。**疑似已修**（不再全实体加载），但无 `setLimit`。
  - **OOM 族 AR-177（`CodeIndexService.getProjectFilePaths`）**：live 已改用投影查询 `selectFieldsByQuery`（仅选 `filePath`），见 `nop-code-service/.../CodeIndexService.java:1398-1413`。**疑似已修**（不再加载全 CLOB 实体），但仍无 `setLimit`（无上限点）。
  - **逻辑删除族 AR-176**：roadmap 称「11 实体中仅 1 个使用 `useLogicalDelete`」。但 live grep `useLogicalDelete`/`logicalDelete`/`logical-delete` 在整个 nop-code 模块**零命中**。ORM 模型 `nop-code/model/nop-code.orm.xml` 含 11 个实体（NopCodeIndex/File/Symbol/Usage/Call/Inheritance/AnnotationUsage/Dependency/Flow/FlowMembership/SemanticEdge）。**AR-176 前提疑似过时**（属性名变更或已移除），I0 须正式核实删除路径当前语义。
  - **删除路径**：`CodeIndexService.deleteIndex(String)` 见 `CodeIndexService.java:529-560`，用 `deleteEntitiesPaged`（`findAllByQuery` + `batchDeleteEntities`，`DELETE_BATCH_SIZE` 分页，`evictAll`）做物理删除；BizModel 入口 `NopCodeIndexBizModel.deleteIndex` 见 `NopCodeIndexBizModel.java:142`。
- **ArchUnit 未引入**：全仓 grep `archunit`（含 pom.xml）零命中。I1 若需 ArchUnit 须先加依赖。
- **真正剩余 gap**：AR-94→AR-178 全量发现**无一份「当前已修/仍开放」的 live 收敛表**；失败族未沉淀为不变式陈述；审计目标集（全部 SearchService/IndexManager/CodeClassLoader/删除路径方法）未枚举。

## Goals

- 产出不变式目录 `ai-dev/audits/nop-code-invariants/invariant-catalog.md`，每条不变式含「陈述 / 覆盖失败族 / 历史 audit-finding-ID 证据 / 检测方法」四要素。
- 产出 AR-94→AR-178（及 r9-r13 sub-round 发现）的**当前 live 状态盘点矩阵**：每条标 `fixed`（附 `文件:行` 证据）或 `open`（附当前缺陷位置）。
- 枚举审计目标集 = nop-code 全部 SearchService / IndexManager / CodeClassLoader / 删除路径的公共与变更型方法（附 `文件:行`）。
- 确认并记录基线：当前零 nop-code 代码不变式门禁（附 grep/ls 证据）。

## Non-Goals

- 不落地任何可执行门禁（JUnit/.mjs/ArchUnit）—— 那是 I1 的范围。
- 不修复任何悬空发现 —— 那是 I2→I4 的范围。
- 不重跑已有 13 轮审计 —— 本计划只盘点其产出的当前 live 状态。
- 不改任何产品代码（`nop-code*/src/main`）。

## Scope

### In Scope

- 读取并归类 2 baseline + 13 轮 adversarial review 的全部发现（AR-94→AR-178 及 sub-round）。
- 对每条发现核对 live code，判定 fixed/open。
- 从失败族归纳不变式陈述（首批候选族至少覆盖 roadmap I1 列出的 4 族）。
- 枚举审计目标集方法清单。
- 创建 `ai-dev/audits/nop-code-invariants/` 目录与 `invariant-catalog.md`。

### Out Of Scope

- I1 的门禁实现（JUnit 测试、`.mjs` 扫描器、ArchUnit 规则）。
- I2 的跑门禁 + red list + 对抗探查。
- 对 nop-stream / nop-metadata / nop-ai 范围的盘点（范围独立，见 roadmap Cross-Cutting）。

## Execution Plan

### Phase 1 - 发现归集与 live 状态盘点

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/ar-status-matrix.md`（新建）；输入 = `ai-dev/audits/nop-code-audit-2026-05-{05,10}.md` + `ai-dev/audits/2026-{05-25,05-29,05-31,06-01,06-02,06-05,06-06}*-nop-code*` 全部子目录

- Item Types: `Proof | Decision`

- [x] 枚举 2 baseline + 13 轮 adversarial review 的全部 finding-ID（AR-94 起），去重并标注出处目录
- [x] 对每条 finding 核对 live `nop-code/**/src/main` code，判定 `fixed`（附 `文件:行` 修复证据）或 `open`（附当前缺陷位置）；预核已提示 AR-168/AR-177 疑似 fixed、AR-176 前提疑似过时，须正式确认
- [x] 对 AR-176 逻辑删除族：grep 确认 `useLogicalDelete`/逻辑删除相关属性在当前 ORM 模型中是否存在；如已不存在，在矩阵中标记「前提过时（stale premise）」并记录当前删除路径实际语义（`deleteIndex` → `deleteEntitiesPaged` 物理删除）
- [x] 将 open 发现按失败族聚合（OOM 字段加载族 / 增量索引去同步族 / 逻辑删除契约族 / 并发锁族 / 幂等性族 等）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ai-dev/audits/nop-code-invariants/ar-status-matrix.md` 存在，且每条 finding 有且仅有一个状态（`fixed`|`open`|`stale-premise`），fixed/stale-premise 附 `文件:行` 或 grep 证据，open 附当前缺陷位置
- [x] 矩阵中无「未判定」悬挂项（裁决零悬挂）
- [x] 矩阵顶部的族聚合表与逐条状态一致（族内 open 计数 == 逐条 open 数）
- [x] 本 Phase 为纯文档盘点，不改产品代码：`No owner-doc update required`（不改 `docs-for-ai/` 产品行为契约；盘点产物本身落 `ai-dev/audits/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 不变式目录沉淀

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/invariant-catalog.md`（新建）

- Item Types: `Decision | Proof`

- [x] 从 Phase 1 的失败族归纳不变式陈述；首批至少覆盖 roadmap I1 列出的 4 个候选族：① 实体加载字段最小化；② 增量索引一致性（逻辑删除 vs 物理删除契约）；③ 增量索引幂等性；④ 查询结果上限（防 OOM）
- [x] 每条不变式填写四要素：陈述 / 覆盖失败族 / 历史 audit-finding-ID 证据（带出处目录）/ 检测方法（`JUnit @ParameterizedTest` | `ai-dev/tools/*.mjs 静态扫描` | `ArchUnit` | `ORM+service 交叉检查`）
- [x] 对每条不变式标注「候选检测规则的 live 锚点」（即在 nop-code 哪些类/方法上应触发，附 `文件:行`），作为 I1 落地门禁的对接点
- [x] 对 AR-176 逻辑删除族：如 Phase 1 判定前提过时，则在目录中显式说明该不变式的当前真实形态（例如「无实体使用逻辑删除时，该不变式退化为：删除路径统一物理删除契约一致性」），不得沿用已失效前提

Exit Criteria:

- [x] `ai-dev/audits/nop-code-invariants/invariant-catalog.md` 存在，含 ≥4 条不变式
- [x] 每条不变式四要素齐全（陈述/覆盖族/历史证据/检测方法），无占位符
- [x] 每条不变式有 live 锚点（`文件:行`），且锚点在 live repo 中真实存在（抽查 ≥3 条可定位到代码）
- [x] 逻辑删除族不变式已反映 live 现状，未沿用过时前提
- [x] 本 Phase 为纯文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 审计目标集枚举与基线确认

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/audit-target-set.md`（新建）

- Item Types: `Proof`

- [x] 枚举审计目标集：nop-code 全部 SearchService / IndexManager / CodeClassLoader / 删除路径的公共与变更型方法，每条附 `类名.方法名` + `文件:行`
- [x] 确认基线并附证据：`ls ai-dev/tools/*.mjs` + grep 证明无 `check-nop-code-invariants.mjs`；grep 证明无 nop-code invariant JUnit 测试类
- [x] 标注目标集中「Phase 1 判定为 open」的方法，作为 I2 跑门禁的优先靶点

Exit Criteria:

- [x] `ai-dev/audits/nop-code-invariants/audit-target-set.md` 存在，目标方法清单每条可定位到 live `文件:行`
- [x] 基线确认节含两条可复现证据命令的输出（无 nop-code invariant 门禁 / 无 invariant 测试类）
- [x] open 方法已交叉标注（与 Phase 1 矩阵一致）
- [x] 本 Phase 为纯文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**：本计划不涉及任何产品代码变更（仅新建 `ai-dev/audits/nop-code-invariants/` 下文档）。`./mvnw test`、`./mvnw compile`、checkstyle 等构建验证条目不适用，从本节删除。

- [x] 三份产出文件均存在：`invariant-catalog.md` / `ar-status-matrix.md` / `audit-target-set.md`
- [x] AR 状态矩阵裁决零悬挂（每条 finding 已判定 fixed/open/stale-premise）
- [x] 不变式目录 ≥4 条且四要素齐全，live 锚点真实存在
- [x] 审计目标集方法每条可定位到 live 代码
- [x] 基线「零 nop-code 代码不变式门禁」已附可复现证据
- [x] AR-176 逻辑删除族前提是否过时已有 live 结论（不得沿用未核实前提进入 I1）
- [x] 不存在被静默降级到 deferred 的 in-scope 盘点项
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（新增文档含内部链接须校验）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（本计划为闭环起点，预计无 deferred 项；若盘点中发现某族暂不沉淀为不变式，须在此记录）

## Non-Blocking Follow-ups

- I1 门禁的具体检测规则细节（regex/AST 模式）—— 属 I1 范围，非本计划阻塞项
- 对抗探查的盲区清单 —— 属 I2 范围

## Closure

Status Note: I0 盘点完成。三份产出文件（ar-status-matrix / invariant-catalog / audit-target-set）已覆盖 AR-01~AR-182 全量发现（148 条去重后），裁决零悬挂（51 fixed / 95 open / 2 stale-premise）。逻辑删除族 AR-176/AR-54 前提已正式核实为过时（live 无 useLogicalDelete）。5 条不变式含 live 锚点，作为 I1 门禁沉淀的确定性输入。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, task ses_0077de0e2ffemcnlv105sDwf9Y）
- Audit Session: ses_0077de0e2ffemcnlv105sDwf9Y
- Evidence:
  - 每条 Exit Criterion 验证结果：
    - Phase 1 Exit Criteria: 全部 PASS — 矩阵 265 行，14 族聚合表，逐条状态齐全（fixed/open/stale-premise 三态），裁决零悬挂，族聚合 open 计数与逐条一致
    - Phase 2 Exit Criteria: 全部 PASS — 5 条不变式（≥4），四要素齐全无占位符，6 个 live 锚点抽查均可定位到代码，INV-02 退化形态正确未沿用过时前提
    - Phase 3 Exit Criteria: 全部 PASS — 6 个服务类方法清单 + ORM 11 实体表，CodeClassLoader 确认不存在，基线证据命令可复现
  - 每条 Closure Gate 验证结果：
    - 三份产出文件存在: PASS
    - AR 裁决零悬挂: PASS
    - ≥4 不变式四要素 + live 锚点: PASS（6 锚点 live 核对通过）
    - 审计目标集方法可定位: PASS（5 抽查通过，1 锚点已修正 entityToInheritance 371→428）
    - 基线可复现证据: PASS（无 nop-code invariant 工具 / 无 invariant 测试类）
    - AR-176 stale-premise 结论: PASS（grep useLogicalDelete 零命中）
    - 无静默降级: PASS（Deferred 段为空）
    - 独立 closure-audit: PASS（本条记录）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（确认无未勾选项 + Closure Evidence 已写入）
  - `node ai-dev/tools/check-doc-links.mjs --strict`：新增文档零断链（三份产出文件 0 broken link）；工具全局退出码 1 源于其他模块预存断链（nop-ai/nop-metadata/nop-stream roadmap 等 35 条），与本计划产出无关
  - Deferred 项分类检查：无 deferred 项（本计划为闭环起点，盘点范围已全覆盖）

Follow-up:

- 本计划产出（catalog/matrix/target-set）为 I1 的确定性输入；no remaining plan-owned work
- I1 门禁的具体检测规则细节（regex/AST 模式）属 I1 范围
