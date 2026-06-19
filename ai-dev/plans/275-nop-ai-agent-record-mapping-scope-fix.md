# 275 nop-ai-agent record-mapping scope 修正——消除 main resource 引用 test-scope 依赖的静默 no-op

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: 20-5 (carry-over from plan 272)
> Last Reviewed: 2026-06-20
> Source: carry-over from `ai-dev/plans/272-nop-ai-agent-configuration-fixes.md` §Deferred But Adjudicated / §Follow-up
> Related: 272-nop-ai-agent-configuration-fixes
>
> **Granularity Note**: Below standalone threshold (~30–60 lines estimated production-code change). No bundle-eligible siblings found (15-1 = different file/pattern, env-source = different subsystem, 05-2/14-04 = explicitly no successor required). Proceeding per carry-over escape-hatch clause; ROADMAP will attempt harder bundling next cycle.

## Purpose

收口 carry-over 20-5：`nop-ai-agent` 的 `src/main/resources` 中有文件引用了 `<scope>test</scope>` 的 `nop-record-mapping` 模块的类和资源。在生产环境（消费方依赖 nop-ai-agent 但 classpath 上无 nop-record-mapping），`.agent-plan.md` 加载器因 `optional="true"` 被静默跳过——用户放入 `.agent-plan.md` 文件后不会报错也不会加载，属于 Silent No-Op（plan guide Lesson #9 / Minimum Rule #24）。本计划消除 main-resource 对 test-scope 依赖的引用，确保配置一致性。

## Current Baseline

- `pom.xml:49-50`：`nop-record-mapping` 声明为 `<scope>test</scope>`（plan 272 Phase 2 将 `nop-dao`/`nop-message-core` 也移到 test scope 以清理传递依赖泄漏，但 `nop-record-mapping` 的 scope 问题 deferred 到本计划）。
- `agent-plan.register-model.xml:7-8`（`src/main/resources`）：`<loader fileType="agent-plan.md" mappingName="agentPlan.Markdown_to_AgentPlanModel" optional="true" class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>` ——引用 test-scope 依赖中的类。
- `agentPlan.record-mappings.xml`（`src/main/resources/_vfs/nop/record/mapping/`）：引用 `/nop/schema/record/record-mappings.xdef` 和 `/nop/record/xlib/record-mapping-gen.xlib`——两者均来自 `nop-record-mapping` 模块。
- `RegisterModelDiscovery.java:248-261`（nop-xlang）：当 `optional="true"` 且目标类不存在（`NoClassDefFoundError`）时，catch 异常并 `LOG.warn(...)` 后跳过 loader 注册——即生产环境中 `.agent-plan.md` loader 从不注册。
- `.agent-plan.md` 文件仅存在于 `src/test/resources/_vfs/test/test-agent-plan.agent-plan.md`——dev/test 便利格式，非生产部署格式。
- `TestAgentPlanMarkdownLoader.java`：端到端测试，从 `.agent-plan.md` 加载到 `AgentPlan`，仅在 test classpath（含 nop-record-mapping）下运行。
- `.agent-plan.xml` / `.agent-plan.yaml` 使用 `xdsl-loader`，不依赖 `nop-record-mapping`，生产环境正常工作。

## Goals

- `src/main/resources` 中不再有文件引用 test-scope Maven 依赖的类或资源——消除 Silent No-Op 根因。
- `.agent-plan.md` 加载在 test scope 继续正常工作（现有 `TestAgentPlanMarkdownLoader` 通过）。
- `.agent-plan.xml` / `.agent-plan.yaml` 加载在 test 和 production context 均正常。
- `nop-record-mapping` 维持 test scope（与 plan 272 依赖清理方向一致），不泄漏传递依赖给下游消费者。

## Non-Goals

- 不将 `nop-record-mapping` 改为 compile scope（方案 A 已排除：会泄漏传递依赖，与 plan 272 方向冲突）。
- 不重设计 record-mapping 的 markdown 解析管线。
- 不修改 `.agent-plan.md` 的格式规范或 `agentPlan.record-mappings.xml` 的 mapping 字段定义。
- 不修改 `nop-record-mapping` 模块本身。
- 不处理 15-1（`Team.members` 类型优化）、env-source、05-2 等其他 carry-over（不同子系统/不同修复模式）。

## Scope

### In Scope

- **Phase 1**: scope 一致性修复（决策 + 实施消除 main-resource 对 test-scope 依赖的引用）
- **Phase 2**: 文档同步 + 全量验证

### Out Of Scope

- plan model xdef codegen 管线重设计（05-2，optimization candidate，无 successor）。
- `Team.members` 类型优化（15-1，optimization candidate，无 successor）。
- `SandboxRequest.environmentVariables` 来源链路追溯（env-source，independent successor）。

## Execution Plan

### Phase 1 - Scope 一致性修复

Status: completed
Targets: `pom.xml`, `agent-plan.register-model.xml`, `agentPlan.record-mappings.xml`, `src/test/resources/_vfs/`

- Item Types: `Fix | Decision`

- [x] **Decision**：确认 `.agent-plan.md` 为 dev/test 便利格式（非生产功能）。依据：唯一 `.agent-plan.md` 文件在 `src/test/resources`，生产部署格式为 `.agent-plan.xml`/`.agent-plan.yaml`。方案 A（将 `nop-record-mapping` 改为 compile scope）已排除——会泄漏 `nop-record-mapping` 的传递依赖，与 plan 272 Phase 2 依赖清理方向直接冲突。采用方案 B：将 `.agent-plan.md` loader 和 record-mappings 文件从 main resources 移到 test resources，保持 `nop-record-mapping` 为 test scope。
- [x] **Fix**：从 `src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml` 移除 `.agent-plan.md` loader 条目（保留 `.agent-plan.xml`/`.agent-plan.yaml` 的 xdsl-loader）。
- [x] **Fix**：将 `agentPlan.record-mappings.xml` 从 `src/main/resources/_vfs/nop/record/mapping/` 迁移到 `src/test/resources/_vfs/nop/record/mapping/`（test 运行时 Nop _vfs 合并 main+test classpath，mappingName 解析路径 `/nop/record/mapping/agentPlan.record-mappings.xml` 在 test 下仍可达）。
- [x] **Fix**：在 `src/test/resources/_vfs/nop/core/registry/` 新建 `agent-plan-md.register-model.xml`（`name="agent-plan"`，与 main 同名但文件名不同以避免 VFS 同路径去重），通过 `discover()` 的 name 聚合 + `DeltaMerger` 按 `fileType` key 追加 `.agent-plan.md` loader 条目（`RegisterModelDiscovery.discover()` 按 `name` 属性聚合并经 `DeltaMerger` 合并同名 model（`RegisterModelDiscovery.java:114-120`），因此 test delta 会与 main 合并而非覆盖，使 `.agent-plan.md` loader 仅在 test classpath 下注册）。
- [x] **Proof**：新增 `TestAgentPlanRegisterModelScope` 验证 production-context 行为：main register-model 资源内容不含 `record_mapping`/`MarkdownDslResourceLoaderFactory`/任何 plain `<loader>` 元素（即不存在对 test-scope 缺失类的引用），且 `.agent-plan.xml` 生产格式端到端加载正常。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `src/main/resources` 中 grep `record_mapping|record-mapping|MarkdownDsl` 返回零结果（main resources 不再引用 test-scope 依赖）
- [x] `TestAgentPlanMarkdownLoader` 端到端测试通过（`.agent-plan.md` 在 test scope 仍可加载）
- [x] `.agent-plan.xml` 和 `.agent-plan.yaml` 加载测试通过（production 格式不受影响）
- [x] **无静默跳过**：production-context 下 `agent-plan.register-model.xml` 加载不产生 `nop.register-model.ignore-invalid-loader` warn 日志——验证方式：`TestAgentPlanRegisterModelScope.testMainRegisterModelHasNoTestScopeReferences` 断言 main register-model 不含 test-scope 类引用 / plain loader 元素
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] `No owner-doc update required`：main→test 文件迁移不改变 public contract，`nop-record-mapping` 维持 test scope，模块依赖关系不变
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文档同步与全量验证

Status: completed
Targets: `ai-dev/design/nop-ai-agent/`, `ai-dev/plans/272-*.md`, `ai-dev/logs/`

- Item Types: `Follow-up`

- [x] **Follow-up**：在 source-plan `272-nop-ai-agent-configuration-fixes.md` 末尾添加 `## Follow-up handled by 275-nop-ai-agent-record-mapping-scope-fix.md`（创建可追溯链接）。
- [x] **Follow-up**：`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` 中 M4a（line 28）与 §7 检查清单（line 426）原描述 `.agent-plan.md` 为支持格式，已修正为 dev/test-only 便利格式（生产部署格式为 `.agent-plan.xml`/`.yaml`），并标注 plan 275 scope 收口。
- [x] **Follow-up**：全量构建验证。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `272-nop-ai-agent-configuration-fixes.md` 包含 `## Follow-up handled by 275-nop-ai-agent-record-mapping-scope-fix.md`
- [x] design 文档与实际 scope 决策一致（roadmap M4a + §7 检查清单已修正为 `.agent-plan.md` 为 dev/test-only）
- [x] `./mvnw clean install -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（全量构建）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] 20-5 confirmed live defect（main resource 引用 test-scope 依赖的 Silent No-Op）已修复
- [x] `src/main/resources` 中不存在对 test-scope Maven 依赖的类/资源引用
- [x] `.agent-plan.md` 在 test scope 仍可加载
- [x] `.agent-plan.xml` / `.agent-plan.yaml` 在 production context 正常加载
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）配置变更后 production classpath 加载链无断裂，（b）无 optional+missing-class 的静默跳过模式残留
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项；15-1 / env-source / 05-2 经裁定不在本 plan scope，各自独立 successor 或无 successor。）

## Non-Blocking Follow-ups

- 15-1 `Team.members` ConcurrentMap 类型优化（carry-over from plan 272，optimization candidate，无 successor）
- env-source `SandboxRequest.environmentVariables` 来源链路追溯（carry-over from plan 274，independent successor）
- 05-2 plan model xdef codegen 管线重设计（optimization candidate，无 successor）

## Closure

Status Note: carry-over 20-5 收口。`nop-ai-agent` 的 `src/main/resources` 不再引用 test-scope 的 `nop-record-mapping` 模块的类或资源——`.agent-plan.md` loader 与 `agentPlan.record-mappings.xml` 已迁移到 `src/test/resources`，`.agent-plan.md` loader 经 test-only `agent-plan-md.register-model.xml`（同 `name="agent-plan"`，文件名不同避免 VFS 去重）在 test classpath 经 `discover()` name 聚合 + DeltaMerger 追加注册。生产环境（classpath 无 nop-record-mapping）加载链无断裂、无 `nop.register-model.ignore-invalid-loader` 静默跳过。`.agent-plan.xml`/`.agent-plan.yaml`（xdsl-loader，框架提供）生产正常；`.agent-plan.md`（dev/test 便利格式）test 正常。`nop-record-mapping` 维持 test scope，无传递依赖泄漏。
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: opencode 独立子 agent closure audit（fresh session `ses_11ecf2ebaffeaKC44Pbmu9GvD3`，非实现 session，read-only 审计）
- Audit Session: ses_11ecf2ebaffeaKC44Pbmu9GvD3
- Evidence:
  - Phase 1 Exit Criteria: PASS — `grep -rn "record_mapping|record-mapping|MarkdownDsl" src/main/resources/` exit 1（0 命中）；`TestAgentPlanMarkdownLoader` 通过（md test scope 加载）；`TestAgentPlanRegisterModelScope` 3 tests 通过（main 无 test-scope 类引用 / test delta 携带 md loader / `.agent-plan.xml` 端到端加载为 AgentPlan）；`./mvnw clean test -pl nop-ai/nop-ai-agent -T 1C` BUILD SUCCESS（2756 tests，0 failures）
  - Phase 2 Exit Criteria: PASS — plan 272 含 `## Follow-up handled by 275-*`；roadmap M4a + §7 检查清单修正 md 为 dev/test-only；`./mvnw clean install -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS
  - Closure Gates 11/11: PASS（main resource 0 test-scope 引用 / 无 optional+missing-class 静默跳过模式 / md test 加载正常 / xml+yaml 生产加载正常 / 无 in-scope defect 降级 / doc 已同步）
  - `node ai-dev/tools/check-plan-checklist.mjs 275-*.md --strict` 退出码 0（见下方验证）
  - Anti-Hollow 检查结果：production classpath 加载链无断裂（main register-model 仅 xdsl-loader，无 `class=` 属性）；`scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 findings）
  - Deferred 项分类检查：15-1 / env-source / 05-2 均为不同子系统 optimization/watch-only，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（15-1 / env-source / 05-2 为显式 Non-Blocking Follow-ups，各自独立 successor 或无 successor）
