# 128 docs-for-ai 结构改进：新增模型设计入口与权限开发入口

> Plan Status: completed
> Last Reviewed: 2026-06-09
> Source: 用户反馈 — docs-for-ai 缺少模型设计独立文档，INDEX.md 路由结构需从"任务→文档"升级为"开发阶段→文档"
> Related: none
> Audit: required

## Current Baseline

### 现有文档结构

1. **INDEX.md** — 扁平路由表，按"任务→文档"映射。缺少按开发阶段（模型设计→后台开发→前台开发→权限开发）的顶层入口。
2. **00-required-reading-backend.md** — 后端必读索引，已包含"实体建模与代码生成"场景，但没有独立的模型设计文档作为路由目标。
3. **00-required-reading-frontend.md** — 前端必读索引，结构清晰。
4. **模型设计知识分散**在以下文件中：
   - `02-core-guides/code-style.md` — ORM 命名规范（主键 VARCHAR(36) tagSet="seq"、列命名、关系命名）
   - `02-core-guides/model-first-development.md` — 模型优先开发流程（修改→生成→构建链路）、VARCHAR precision 自动选择、菜单图标
   - `03-runbooks/create-new-entity.md` — 最小闭环步骤
   - `03-runbooks/add-field-and-validation.md` — 新增字段
   - `03-runbooks/add-dict-and-constants.md` — 新增字典
5. **关键知识缺口**：
   - **stdDataType vs stdSqlType 的设计意图和语义分离**：这是 Nop ORM 的核心类型系统设计（`stdSqlType`=数据库物理类型，`stdDataType`=Java 逻辑类型），docs-for-ai 中没有任何文档解释这个概念。源 truth 在 `docs/theory/lowcode-orm-1.md` 和 `docs/user-guide/etl.md`，但 AI 按规则不读 `docs/`。
   - **ID 设计策略**：为什么用 VARCHAR(36) + tagSet="seq" 而不是数据库自增，也没有在 docs-for-ai 中解释。
   - **字段设计中的 domain/stdDomain/stdDataType 三层关系**。
6. **权限文档**只有 `02-core-guides/auth-and-permissions.md`（132 行），覆盖了 HTTP 认证和调试配置，但缺少：
   - action-auth.xml 结构与菜单资源生成链路
   - 操作权限检查的详细机制
   - 数据权限规则的配置方式
   - 角色管理的平台默认实现

### 当前必读入口对比

| 开发阶段 | 是否有独立入口 | 现状 |
|----------|--------------|------|
| 模型设计 | 无 | 知识分散在 5+ 个文件中，缺少一个集中回答"ORM 模型怎么设计"的文档 |
| 后台开发 | 有 | `00-required-reading-backend.md` |
| 前台开发 | 有 | `00-required-reading-frontend.md` |
| 权限开发 | 无 | `auth-and-permissions.md` 被两个必读索引引用，但不是独立入口 |

## Goals

1. 新增 `02-core-guides/orm-model-design.md` — 集中回答"ORM 模型应该怎么设计"的规范文档，包含 stdDataType/stdSqlType 分离、ID 策略、字段设计、关系设计等核心概念。
2. 新增 `00-required-reading-model-design.md` — 模型设计必读索引，与后端、前端必读索引并列。
3. 重构 INDEX.md 路由结构 — 在快速路由表顶部增加四个开发阶段入口（模型设计、后台开发、前台开发、权限开发），形成"阶段→场景→文档"的三级路由。
4. 增强 `02-core-guides/auth-and-permissions.md` — 补充 action-auth.xml 结构、操作权限机制、数据权限配置。
5. 更新现有文档交叉引用 — `code-style.md`、`model-first-development.md`、`00-required-reading-backend.md`、`00-required-reading-frontend.md` 增加对新 `orm-model-design.md` 的引用，避免知识重复定义。

## Non-Goals

- 不改动 `docs/` 目录下的理论文档。
- 不改动应用层项目（nop-app-mall）的文档结构。
- 不改动 `nop-entropy` 的实际代码。
- 不创建新的 `03-runbooks/` 文件（模型设计已有足够的 runbook 覆盖，缺的是 core-guide 层的知识整合）。
- 不重新组织 `03-runbooks/` 的文件结构。

## Task Route

- Type: docs-only improvement (no code change)
- Owner Docs: `docs-for-ai/INDEX.md`、`docs-for-ai/90-maintenance/maintenance-rules.md`
- Skill Selection Basis: none（文档整理任务，不涉及代码生成或 ORM 建模）

## Infrastructure And Config Prereqs

No infra prereqs beyond existing baseline. Docs-only work.

## Execution Plan

### Phase 1 - 新增 ORM 模型设计规范文档

Status: planned
Targets: `docs-for-ai/02-core-guides/orm-model-design.md`
Skill: none
Required Pre-Reading:
- `02-core-guides/code-style.md`（现有 ORM 命名规范来源）
- `02-core-guides/model-first-development.md`（现有模型开发流程）
- `03-runbooks/create-new-entity.md`（新建实体 runbook）
- `03-runbooks/add-field-and-validation.md`（字段校验 runbook）
- `03-runbooks/add-dict-and-constants.md`（字典 runbook）

- Item Types: `Add`
- Prereqs: none

- [ ] **Pre-flight:** Read all docs listed in `Required Pre-Reading` above.
  - Skill: none
- [ ] **Add** 创建 `02-core-guides/orm-model-design.md`，包含以下核心内容：
  - stdDataType vs stdSqlType 的语义分离（数据库物理类型 vs Java 逻辑类型，两者独立配置，框架自动转换）
  - ID 设计策略：VARCHAR(36) + tagSet="seq" 的原因（分布式兼容、跨数据库、字符串 ID 便于合并迁移）
  - 主键规范：固定字段名 id，stdSqlType="VARCHAR" precision="36"，stdDataType="string"，tagSet="seq"
  - 外键字段规范：与主键相同类型（VARCHAR(36) stdDataType="string"），通过 orm:ref-* 声明关系
  - stdDataType 可选值速查（ORM 常用子集，完整列表见 `StdDataType.java`）：boolean / byte / short / int / long / float / double / decimal / string / date / time / datetime / timestamp / bytes
  - stdSqlType 可选值速查（ORM 常用子集，完整列表见 `StdSqlType.java`）：BOOLEAN / TINYINT / SMALLINT / INTEGER / BIGINT / DECIMAL / NUMERIC / FLOAT / REAL / DOUBLE / CHAR / VARCHAR / DATE / TIME / DATETIME / TIMESTAMP / BINARY / VARBINARY / JSON / CLOB / BLOB / GEOMETRY
  - stdSqlType 到 stdDataType 的默认映射表（每个 StdSqlType 枚举值都有固定的 stdDataType 默认值，如 VARCHAR→string、INTEGER→int、BIGINT→long）
  - domain / stdDomain / stdDataType 三层关系
  - 字典字段设计（dict name 格式、value 类型、ext:dict 引用）
  - 通用字段（禁止手动添加的框架管理字段）
  - 关系设计（to-one tagSet="pub"、to-many tagSet="pub,cascade-delete,insertable,updatable"）
  - 与现有文档的交叉引用（指向 model-first-development.md 的流程内容，指向 code-style.md 的命名规范，指向各 runbook 的操作步骤）
  - Skill: none

Exit Criteria:

- [ ] `orm-model-design.md` 存在且包含上述所有核心内容
- [ ] stdDataType vs stdSqlType 的分离有明确说明和示例
- [ ] ID 策略有原因说明（不只是规范本身）
- [ ] 文档与现有 code-style.md、model-first-development.md 无矛盾
- [ ] No owner-doc update required（本阶段就是创建文档）

### Phase 2 - 新增模型设计必读索引

Status: planned
Targets: `docs-for-ai/00-required-reading-model-design.md`
Skill: none
Required Pre-Reading:
- Phase 1 产出的 `02-core-guides/orm-model-design.md`
- `00-required-reading-backend.md`（参照其结构）

- Item Types: `Add`
- Prereqs: Phase 1

- [ ] **Pre-flight:** Read all docs listed in `Required Pre-Reading` above.
  - Skill: none
- [ ] **Add** 创建 `00-required-reading-model-design.md`，结构与后端/前端必读索引一致：
  - 全局必读：`orm-model-design.md`、`model-first-development.md`
  - 按场景选读：字段设计、字典设计、关系设计、代码生成、菜单图标
  - Skill: none

Exit Criteria:

- [ ] `00-required-reading-model-design.md` 存在且结构清晰
- [ ] 所有引用的文档路径都存在
- [ ] No owner-doc update required

### Phase 3 - 增强 auth-and-permissions.md

Status: planned
Targets: `docs-for-ai/02-core-guides/auth-and-permissions.md`
Skill: none
Required Pre-Reading:
- `02-core-guides/auth-and-permissions.md`（现有内容）
- 源码锚点（文档维护例外）：`nop-auth/model/nop-auth.orm.xml`、action-auth.xml 相关模板

- Item Types: `Add`
- Prereqs: none（可与 Phase 1/2 并行，但 Phase 4 依赖本阶段）

- [ ] **Pre-flight:** Read `auth-and-permissions.md` and search for action-auth.xml patterns in the repo.
  - Skill: none
- [ ] **Add** 增强 `auth-and-permissions.md`，补充以下内容：
  - action-auth.xml 结构说明：resourceType（TOPM/SUBM/ACT）、菜单资源生成链路（从 ORM 模型 → `_*.action-auth.xml` → 菜单/权限）
  - 操作权限检查机制：`nop.auth.enable-action-auth` 启用后的检查流程
  - 数据权限配置：DockerBean 规则配置方式、字段级数据权限
  - 角色与用户管理的平台默认实体（NopAuthRole、NopAuthUser、NopAuthRoleUserData、NopAuthSiteUserData）
  - Skill: none

Exit Criteria:

- [ ] auth-and-permissions.md 包含 action-auth.xml 结构说明
- [ ] auth-and-permissions.md 包含操作权限检查机制
- [ ] auth-and-permissions.md 包含数据权限配置
- [ ] No owner-doc update required

### Phase 4 - 重构 INDEX.md 路由结构

Status: planned
Targets: `docs-for-ai/INDEX.md`
Skill: none
Required Pre-Reading:
- Phase 1 产出的 `02-core-guides/orm-model-design.md`
- Phase 2 产出的 `00-required-reading-model-design.md`
- 现有 `INDEX.md`

- Item Types: `Add`
- Prereqs: Phase 1, Phase 2, Phase 3

- [ ] **Pre-flight:** Read all docs listed in `Required Pre-Reading` above.
  - Skill: none
- [ ] **Add** 在 INDEX.md 快速路由表顶部增加"按开发阶段"入口区块：

  ```markdown
  ## 按开发阶段入口

  | 开发阶段 | 必读入口 | 核心规范 |
  |----------|---------|---------|
  | **模型设计** | **`00-required-reading-model-design.md`** | `02-core-guides/orm-model-design.md` |
  | **后台开发** | **`00-required-reading-backend.md`** | `02-core-guides/service-layer.md` |
  | **前台开发** | **`00-required-reading-frontend.md`** | `02-core-guides/view-and-page-customization.md` |
  | **权限开发** | **`02-core-guides/auth-and-permissions.md`** | 同左（当前唯一入口） |
  ```

- [ ] **Add** 在 INDEX.md "快速路由"表中增加新文档的路由条目：
  - `| 理解 ORM 模型设计规范 | 02-core-guides/orm-model-design.md |`
  - `| 模型设计必读文档索引 | 00-required-reading-model-design.md |`
  - Skill: none

Exit Criteria:

- [ ] INDEX.md 包含"按开发阶段入口"区块
- [ ] 四个开发阶段入口全部列出且链接正确
- [ ] 快速路由表增加了新文档条目
- [ ] 原有路由条目未被删除或破坏
- [ ] No owner-doc update required

### Phase 5 - 更新现有文档交叉引用

Status: planned
Targets: `docs-for-ai/00-required-reading-backend.md`, `docs-for-ai/00-required-reading-frontend.md`, `docs-for-ai/02-core-guides/code-style.md`, `docs-for-ai/02-core-guides/model-first-development.md`
Skill: none
Required Pre-Reading:
- Phase 1 产出的 `02-core-guides/orm-model-design.md`
- `02-core-guides/code-style.md`
- `02-core-guides/model-first-development.md`
- `00-required-reading-backend.md`
- `00-required-reading-frontend.md`

- Item Types: `Add`
- Prereqs: Phase 1, Phase 3

- [ ] **Pre-flight:** Read all docs listed in `Required Pre-Reading` above.
  - Skill: none
- [ ] **Add** `00-required-reading-backend.md`：在"实体建模与代码生成"场景中增加 `02-core-guides/orm-model-design.md` 引用，标注为模型设计规范核心文档。
  - Skill: none
- [ ] **Add** `code-style.md`：在"ORM 命名规范"节末尾增加交叉引用，指向 `orm-model-design.md`（概念设计知识归 orm-model-design.md 所有，code-style.md 保留命名格式规范）。
  - Skill: none
- [ ] **Add** `model-first-development.md`：在相关文档节增加交叉引用，指向 `orm-model-design.md`（开发流程归 model-first-development.md，模型设计概念归 orm-model-design.md）。
  - Skill: none
- [ ] **Add** `00-required-reading-frontend.md`：在"认证与权限（页面可见性）"场景描述中更新，反映 Phase 3 增强后的 auth-and-permissions.md 新增内容（action-auth.xml 结构等）。
  - Skill: none

Exit Criteria:

- [ ] backend required reading 引用了 orm-model-design.md
- [ ] code-style.md 的 ORM 命名规范节有交叉引用指向 orm-model-design.md
- [ ] model-first-development.md 有交叉引用指向 orm-model-design.md
- [ ] frontend required reading 的 auth 场景描述反映了增强后的内容
- [ ] 没有知识重复定义（canonical owner 是 orm-model-design.md，其他文件只引用不重复）
- [ ] No owner-doc update required

## Plan Audit

- Status: passed (revision 2)
- Reviewer / Agent: independent subagent
- Evidence (Round 1):
  - B1/B2 FIXED: stdDataType/stdSqlType 列表已从 `StdDataType.java`/`StdSqlType.java` 枚举源验证并补全，标注为"ORM 常用子集"并指向权威源。
  - B3 FIXED: Goal #5 中的条件语言已移除，改为"更新现有文档交叉引用"。独立权限必读索引移入 Deferred。
  - M1 FIXED: Phase 4 prereqs 已更新为依赖 Phase 1 + Phase 2 + Phase 3。Phase 3 prereqs 已标注"Phase 4 依赖本阶段"。
  - M2/M3/M4/M5 FIXED: Phase 5 扩展为更新 code-style.md、model-first-development.md、backend/frontend required reading 的交叉引用，避免知识重复定义。
- Evidence (Round 2):
  - All Round 1 fixes verified as correctly landed.
  - Phase 5 prereqs fixed to include Phase 3 dependency.
  - stdSqlType list updated to include NUMERIC.
  - Verdict: PASS with no remaining blockers.

## Closure Gates

- [ ] in-scope behavior is complete (all 5 phases)
- [ ] relevant docs are aligned (INDEX.md, backend required reading, all new docs)
- [ ] verification: all new/modified files exist and cross-references are correct
- [ ] no in-scope item downgraded to deferred/follow-up
- [ ] plan audit passed before implementation
- [ ] each phase has `Required Pre-Reading` listed
- [ ] text consistency verified: status, phases, gates all agree
- [ ] closure audit was independent
- [ ] closure evidence exists in files

## Deferred But Adjudicated

### 00-required-reading-auth.md 独立权限必读索引

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 auth-and-permissions.md 增强后已足够作为权限开发入口。当权限相关文档数量增长到 3+ 个时再考虑拆分独立必读索引。
- Successor Required: `no`（在 auth 内容自然增长后触发）

## Closure

Status Note: All 5 phases completed. Plan audit passed (2 rounds). Closure audit passed with no blockers. One minor inaccuracy (FLOAT/REAL mapping) fixed post-audit.

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (closure audit)
- Evidence: All exit criteria verified PASS. orm-model-design.md exists with all required content. auth-and-permissions.md enhanced with action-auth/data-auth/platform entities. INDEX.md restructured with dev-stage entries. Cross-references added to backend/frontend required reading, code-style.md, model-first-development.md. No knowledge duplication. Minor fix applied: FLOAT/REAL mapping corrected to REAL→float, FLOAT→double.

Follow-up:

- 当权限相关文档数量超过 3 个时，考虑创建 `00-required-reading-auth.md`
