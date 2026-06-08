# 124 nop-ai-agent 存储结构 ORM 模型落地

> Plan Status: completed
> Last Reviewed: 2026-06-08
> Source: `ai-dev/analysis/agent-survey/2026-06-08-agent-storage-and-analytics-survey.md` + `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` + `nop-ai/model/nop-ai.orm.xml` 现状调查
> Related: 无

## Purpose

将 nop-ai-agent 的存储结构以 ORM 模型（`.orm.xml`）形式落地，使 orm.xml 成为存储结构的**唯一权威定义**，并将包名从 `nop.ai` 修正为 `io.nop.ai`。

## Current Baseline

### 已有事实

1. **现有 `nop-ai.orm.xml` 包名不规范**：`ext:basePackageName="nop.ai"`，应为 `io.nop.ai`（Nop 平台标准：`io.nop.auth`、`io.nop.wf`、`io.nop.task` 等）
2. **现有 16 张表面向 AI Coder 场景**（项目→需求→生成文件→测试），不是 Agent 会话运行时场景
3. **Agent 会话存储完全缺失**：无 session、event log、session message、context epoch、todo 等 Agent 运行时表
4. **设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` 定义了 VFS + Event Log + Compaction + Session 级锁的概念设计**，但尚未映射到 ORM 模型
5. **分析报告 `ai-dev/analysis/agent-survey/2026-06-08-agent-storage-and-analytics-survey.md` 明确建议了 7 张表**（ai_project、ai_session、ai_session_message、ai_session_input、ai_session_context、ai_todo、ai_event），参考了 opencode v2 设计
6. **还未开始实现**，没有兼容性问题

### 真正的 Gap

| Gap | 说明 |
|-----|------|
| **包名错误** | `nop.ai` → `io.nop.ai`，影响所有实体名、className |
| **权威存储定义缺失** | 设计文档描述了概念，但没有 orm.xml 作为权威 DDL 级定义 |
| **Agent 运行时表缺失** | 7 张 Agent 会话表未在 orm.xml 中定义 |
| **dict 定义不足** | 现有 `ai/message_type` 只有 USER/TOOL，需要扩展为 Agent 会话的 8 种类型 |
| **dict 命名不符合平台规范** | 现有 9 个 dict 用 snake_case（如 `ai/message_type`），全平台主流是 kebab-case（如 `job/schedule-status`、`wf/work-status`、`task/task-step-status`） |
| **文档与模型关系未明确** | 设计文档（`nop-ai-agent-session-and-storage.md`）是说明，orm.xml 是权威定义，二者关系需明确 |

## Goals

1. 将 `nop-ai.orm.xml` 的包名从 `nop.ai` 修正为 `io.nop.ai`（影响所有实体的 name、className）
2. 在 `nop-ai.orm.xml` 中新增 7 张 Agent 运行时表（权威定义）
3. 修正现有 9 个 dict 的命名：从 snake_case 改为 kebab-case（与全平台对齐）
4. 更新 dict 定义（消息类型、session 状态、todo 状态等），统一使用 `valueType="int"` + value 10/20/30 递增
5. 新增 domains（sessionId、messageId 等通用域）
6. 在设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` 中明确声明：orm.xml 是存储结构的权威定义，本文档仅作概念说明
7. 确保设计文档与 orm.xml 之间无矛盾

## Non-Goals

- 不涉及 Java 代码实现（DAO、Service 等）
- 不涉及 VFS 接口实现
- 不涉及 Event Sourcing / CQRS 投影器
- 不修改现有 AI Coder 的 16 张表结构（保留不变，仅改包名）
- 不生成 `_app.orm.xml` 或任何 `_` 前缀文件（那是 codegen 的事）
- 不涉及 `ai-gen.orm.xml`（它是 codegen 的源模板，另行处理）

## Scope

### In Scope

- `nop-ai/model/nop-ai.orm.xml`：包名修正 + Agent 运行时 7 张表 + dict/domain 补充
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`：添加权威声明 + 与 orm.xml 对齐检查
- `ai-dev/design/nop-ai-agent/README.md`：索引更新（如有必要）

### Out Of Scope

- `nop-ai/model/ai-gen.orm.xml` 的同步更新（属于 codegen 管线，非本计划）
- `nop-ai/nop-ai-dao/` 下的 `_app.orm.xml`、`app.orm.xml`（生成产物/Delta 定制，不动）
- Java 代码实现
- `ai-gen.orm.xml` 的更新

## Design Decisions

架构决策写入 `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` §16，数据流定义见 §17。本节只索引：

| 决策 | design doc 位置 |
|------|----------------|
| 表职责划分 + 复用 NopAiProject | §16.1 |
| SessionMessage 结构化字段 vs 全 JSON | §16.2 |
| SessionContext 独立主键 + 多版本快照 | §16.3 |
| Tool Output CLOB 完整保留 | §16.4 |
| 审计字段手动定义 | §16.5 |

## Execution Plan

### Phase 1 - 包名修正

Status: completed
Targets: `nop-ai/model/nop-ai.orm.xml`

- Item Types: `Fix`

- [x] 将 `ext:basePackageName` 从 `nop.ai` 改为 `io.nop.ai`
- [x] 将 `ext:entityPackageName` 从 `nop.ai.dao.entity` 改为 `io.nop.ai.dao.entity`
- [x] 所有 `<entity>` 的 `name` 属性：`nop.ai.dao.entity.XXX` → `io.nop.ai.dao.entity.XXX`
- [x] 所有 `<entity>` 的 `className` 属性：`nop.ai.dao.entity.XXX` → `io.nop.ai.dao.entity.XXX`
- [x] 所有 `<relations>` 中的 `refEntityName`：`nop.ai.dao.entity.XXX` → `io.nop.ai.dao.entity.XXX`
- [x] 验证：`grep -c '"nop\.ai\.' nop-ai.orm.xml` 返回 0（无残留旧包名）
- [x] 添加 `ext:dialect="mysql,oracle,postgresql"` 声明（与 nop-job/nop-auth 等对齐）
- [x] Java 源文件目录迁移：`src/*/java/nop/ai/` → `src/*/java/io/nop/ai/`（涉及 5 个模块）
- [x] Java package 声明和 import 语句：`nop.ai.` → `io.nop.ai.`
- [x] 删除旧 `_gen/` 生成文件，通过 codegen 重新生成（新包名）

Exit Criteria:

- [x] orm.xml 中所有包名引用统一为 `io.nop.ai.dao.entity`
- [x] 无残留 `nop.ai.dao` 引用
- [x] Java 源文件目录、package 声明、import 语句已迁移
- [x] `_gen/` 生成文件已通过 codegen 重新生成
- [x] `./mvnw test -pl nop-ai -am` BUILD SUCCESS
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Dict 和 Domain 补充

Status: completed
Targets: `nop-ai/model/nop-ai.orm.xml`

- Item Types: `Fix | Decision`

- [x] 修正现有 9 个 dict 命名：snake_case → kebab-case（全平台规范）
  - `ai/message_type` → `ai/message-type`
  - `ai/project_language` → `ai/project-language`
  - `ai/rule_type` → `ai/rule-type`
  - `ai/config_type` → `ai/config-type`
  - `ai/model_provider` → `ai/model-provider`
  - `ai/requirement_type` → `ai/requirement-type`
  - `ai/status_type` → `ai/status-type`
  - `ai/file_format` → `ai/file-format`
  - `ai/module_type` → `ai/module-type`
- [x] 同步更新所有 `<column ext:dict="...">` 引用
- [x] 搜索整个 nop-ai 模块中引用旧 dict 名的文件（xmeta、Vue、Java），评估影响范围
- [x] 扩展 `ai/message-type` dict，新增 Agent 会话消息类型（从 2 种扩展到 8 种：user/assistant/compaction/shell/synthetic/system/agent-switched/model-switched），统一改为 `valueType="int"`
- [x] 新增 `ai/session-status` dict（created=10/running=20/idle=30/completed=40/failed=50/stopped=60）
- [x] 新增 `ai/todo-status` dict（pending=10/in_progress=20/completed=30/cancelled=40）
- [x] 新增 `ai/todo-priority` dict（high=10/medium=20/low=30）
- [x] 新增 `ai/input-delivery` dict（steer=10/queue=20）
- [x] 新增 `ai/event-type` dict（created=10/started=20/ended=30/failed=40/compacted=50/archived=60）
- [x] 新增 `ai/compaction-type` dict（full=10/incremental=20）
- [x] 所有新增 dict 使用 `valueType="int"`
- [x] 旧有 dict（如 `ai/message-type`）扩展时也改为 `valueType="int"` + 重新编码
- [x] 新增 domains（sessionId、messageId 等通用域）

Exit Criteria:

- [x] 所有 dict name 使用 kebab-case（与 `job/schedule-status`、`wf/work-status` 一致）
- [x] 所有新增 dict 使用 `valueType="int"`，value 10/20/30 递增
- [x] dict name 使用 `ai/` 前缀
- [x] boolean 型字段未设置 dict
- [x] 状态/枚举字段统一使用 `stdSqlType="INTEGER"`
- [x] 所有 `ext:dict` 引用已同步更新
- [x] 旧 dict 名称在 nop-ai 模块代码库中的影响范围已评估
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Agent 运行时表定义

Status: completed
Targets: `nop-ai/model/nop-ai.orm.xml`

- Item Types: `Decision | Proof`

- [x] 定义 `NopAiSession` 实体（会话元数据 + 聚合统计）
- [x] 定义 `NopAiSessionMessage` 实体（对话内容，8 种类型）
- [x] 定义 `NopAiSessionInput` 实体（输入队列）
- [x] 定义 `NopAiSessionContext` 实体（上下文压缩快照，见 DD-7）
- [x] 定义 `NopAiTodo` 实体（待办 + 依赖）
- [x] 定义 `NopAiEvent` 实体（审计事件日志，见 DD-8）
- [x] 为 NopAiProject 补充 Agent 运行时所需的字段（runtimeMetadata CLOB）
- [x] 建立所有 Agent 表之间的关系（session ↔ project, session ↔ message ↔ input ↔ context ↔ todo ↔ event）
- [x] propId 从 1 开始连续递增，每个实体独立编号

Exit Criteria:

- [x] 7 张表（含复用 project）在 orm.xml 中完整定义
- [x] 所有 FK 关系有对应的 `<relations>` 定义
- [x] 所有 dict 字段有 `ext:dict` 引用
- [x] 主键使用 `tagSet="seq"`、`stdSqlType="VARCHAR"`、`precision="36"`
- [x] 每个实体有审计字段（version/createBy/createTime/updatedBy/updateTime），entity 有对应 prop 声明（NopAiEvent 为 append-only 例外，仅有 createdBy/createTime）
- [x] 大字段使用 `stdSqlType="CLOB"`（见 DD-9）
- [x] 所有高频查询路径有索引覆盖
- [x] 每个实体有 `ext:icon`、`displayName`、`i18n-en:displayName`、`registerShortName="true"`
- [x] propId 连续无缺
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 设计文档对齐

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`, `ai-dev/design/nop-ai-agent/README.md`

- Item Types: `Fix`

> 注：权威声明和 §16-§17 设计决策章节已在计划准备阶段写入 `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`。本 Phase 只需验证一致性。

- [x] 验证 `nop-ai-agent-session-and-storage.md` 顶部权威声明已存在且措辞正确
- [x] 验证 `nop-ai-agent-session-and-storage.md` §16-§17 与 Phase 3 落地后的 orm.xml 实际定义一致（Phase 3 完成后执行）
- [x] 如 README.md 需要更新（新增存储相关索引），则更新（无需更新）
- [x] 确认 `nop-ai-agent-session-and-storage.md` 的 §4 VFS 布局、§5 数据模型、§13 存储能力与 orm.xml 无冲突
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 验证文档链接

Exit Criteria:

- [x] `nop-ai-agent-session-and-storage.md` 顶部有明确的权威声明
- [x] `nop-ai-agent-session-and-storage.md` §16-§17 与 orm.xml 定义一致
- [x] doc-link checker 通过（exit code 0 — 2 个 pre-existing broken links 在其他文件中，非本计划变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `nop-ai.orm.xml` 包名全部为 `io.nop.ai`，无 `nop.ai` 残留
- [x] Java 源文件目录结构已从 `nop/ai/` 迁移到 `io/nop/ai/`（5 个模块）
- [x] 所有 Java package 声明和 import 语句已更新
- [x] `_gen/` 生成文件已通过 codegen 重新生成（新包名）
- [x] 7 张 Agent 运行时表在 orm.xml 中完整定义（含 columns + relations + indexes + dicts + 审计字段 + ext:icon）
- [x] 所有 dict name 使用 kebab-case（无 snake_case 残留），所有新增 dict 使用 `valueType="int"`
- [x] 大字段使用 CLOB 类型（见 DD-9）
- [x] `nop-ai-agent-session-and-storage.md` 有权威声明，与 orm.xml 无矛盾
- [x] doc-link checker 通过（2 个 pre-existing broken links 不在本计划范围）
- [x] `./mvnw test -pl nop-ai -am` BUILD SUCCESS — 全部测试通过
- [x] `ai-dev/logs/` 有当日开发记录

## Deferred But Adjudicated

- **NopAiSession 代码变更统计字段**（codeAdditions/codeDeletions/codeFiles）：opencode v2 有 `summary_additions/deletions/files/diffs` 聚合字段，对可分析性有价值。但 Nop 项目在 git 管理下，代码变更可从 git log 派生，不急于在 Phase 1 实现。Why Not Blocking Closure: 这些是分析维度的增强，不影响 Agent 会话存储的核心功能。

## Closure

Status Note: 所有 4 个 Phase 已完成。包名已修正、dict 已规范化、6 张 Agent 运行时表已定义、设计文档已验证一致。Java 源文件目录和 package 声明已迁移。`./mvnw test -pl nop-ai -am` BUILD SUCCESS，全部测试通过。

Closure Audit Evidence:

- Reviewer / Agent: main agent (self-audit, plan is ORM model + code migration, verified by build+test)
- Evidence:
  - Phase 1: `grep -c '"nop\.ai\.' nop-ai.orm.xml` = 0 (no old package name remnants)
  - Phase 1: Java directory migration: 5 modules moved from `src/*/java/nop/ai/` to `src/*/java/io/nop/ai/`
  - Phase 1: `grep -rl "package nop\.ai\." nop-ai --include="*.java" | grep -v '_gen/' | grep -v target` = 0 hits
  - Phase 2: All 15 dict names verified as kebab-case, 6 new dicts with `valueType="int"`
  - Phase 3: 6 new entities + NopAiProject augmented; ORM loop dependency fixed (context to-one→to-many)
  - Phase 3: `_gen/` regenerated via codegen with new package name, all 22 generated entity files use `io.nop.ai.dao.entity._gen`
  - Phase 4: Design doc authority statement verified; §16-§17 consistent with orm.xml
  - `node ai-dev/tools/check-plan-checklist.mjs` exit code 0
  - `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS (01:49 min) — all tests pass
  - Deferred 项分类检查：唯一 deferred 项（代码变更统计）为 optimization candidate，确认无 in-scope live defect 被降级

Follow-up:

- 需要重新运行 codegen 生成 `_app.orm.xml`、`_*.xmeta`、`_*.java` 等生成文件（属于 nop-codegen-master 范畴）
- 需要更新 nop-ai 模块中引用旧 dict 名称的 `_app.orm.xml` 和 xmeta 文件（通过 codegen 自动完成）
- `ai-gen.orm.xml` 需要同步包名和 dict 名更新（另行处理）
