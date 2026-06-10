> **Plan Status**: active
> **Module**: nop-ai-agent
> **Work Item**: L0-2
> **Last Reviewed**: 2026-06-10
> **Source**: Roadmap check 2026-06-10 identified L0-2 as the single most urgent item (Layer 0 blocker)
> **Related**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 0, §5 技术债

# 129 Unify Enum: AgentExecStatus vs AgentTaskStatus/AgentPlanStatus

## Purpose

消除 `agent-plan.record-mappings.xml` 引用的 `AgentTaskStatus` / `AgentPlanStatus` 枚举类不存在的问题，以及 `taskStatus` 字段名与 generated model `status` 属性不匹配导致值被静默丢弃到 extProps 的问题，使 record-mappings 的 Markdown 解析路径在运行时正确工作。

## Current Baseline

### 已存在

1. **`AgentExecStatus`** 枚举 (`io.nop.ai.agent.model.AgentExecStatus`)：4 值 — `pending`, `running`, `completed`, `failed`。位于 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentExecStatus.java`。
2. **`agent-plan.xdef`** schema 引用 `enum:io.nop.ai.agent.model.AgentExecStatus` 用于 Plan/Phase/Task 的 `status` 字段（3 处）。位于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef`。
3. **Generated model classes**（`_gen/` 下）：`_AgentPlanModel`、`_AgentPlanTaskModel`、`_AgentPlanPhase` 等均使用 `AgentExecStatus` 类型。
4. **`agent-plan.record-mappings.xml`**：映射 Markdown → `AgentPlanModel`，其中 `taskStatus` 字段引用 `dict="io.nop.ai.agent.plan.AgentTaskStatus"`，`planStatus` 字段引用 `dict="io.nop.ai.agent.plan.AgentPlanStatus"`。

### Gap

- `io.nop.ai.agent.plan.AgentTaskStatus` — **不存在**
- `io.nop.ai.agent.plan.AgentPlanStatus` — **不存在**
- record-mappings 使用 `dict=` 属性引用这两个不存在的类作为字典，在运行时会导致 dict lookup 失败或抛异常
- record-mappings 的 Task 映射中字段名 `taskStatus` 与 generated model 的属性名 `status`（setter `setStatus()`）**不匹配**。由于 `AbstractComponentModel` 实现了 `IPropSetMissingHook`，`setTaskStatus()` 不会报错，值被静默存入 `extProps` Map 而非 `status` 字段——导致**静默数据丢失**
- `planStatus` 字段名与 `_AgentPlanModel.setPlanStatus()` 一致，无此问题

### 设计决策要点

**决策**：不创建独立的 `AgentTaskStatus` / `AgentPlanStatus`，而是将 record-mappings 中的 dict 引用统一到已有的 `AgentExecStatus`。理由：
1. xdef schema 中 Plan/Phase/Task 的 `status` 字段均已定义为 `enum:io.nop.ai.agent.model.AgentExecStatus`
2. Generated code 全部使用 `AgentExecStatus` 类型
3. 三级（Plan/Phase/Task）共享同一状态枚举符合 Agent 执行模型设计（统一的生命周期状态）
4. 如果将来需要更细粒度的状态，可通过 Delta 扩展，不需要预先分裂

## Goals

- record-mappings 中的 `taskStatus` 和 `planStatus` 字段正确映射到 `AgentExecStatus` 枚举
- `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- Markdown → AgentPlanModel 的 record-mapping 解析路径可正确处理 status 字段

## Non-Goals

- 不扩展 `AgentExecStatus` 的枚举值（保持 4 值）
- 不修改 `agent-plan.xdef` schema（已正确使用 `AgentExecStatus`）
- 不修改 `_gen/` 下的生成代码
- 不创建独立的 `AgentTaskStatus` / `AgentPlanStatus` 枚举类

## Scope

### In Scope

- 修改 `agent-plan.record-mappings.xml`：将 `dict="io.nop.ai.agent.plan.AgentTaskStatus"` 和 `dict="io.nop.ai.agent.plan.AgentPlanStatus"` 替换为 `dict="io.nop.ai.agent.model.AgentExecStatus"`
- 确认 record-mapping 字段名与 generated model 字段名一致（`taskStatus` → 需映射到 `status` 还是保持 `taskStatus`）
- 添加或扩展测试验证 Markdown 解析中的 status 字段被正确解析为 `AgentExecStatus`
- Roadmap L0-2 状态更新为 ✅

### Out Of Scope

- Layer 1+ 工作项的实现
- `AgentExecStatus` 枚举值的扩展
- `BaseAgent` 的清理（L1-14）

## Execution Plan

### Phase 1 - Fix record-mappings dict references

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/record/mapping/agent-plan.record-mappings.xml`

- Item Types: `Fix`

- [x] 将 `taskStatus` 字段的 `name="taskStatus"` 改为 `name="status"`（匹配 `_AgentPlanTaskModel.setStatus()`）
- [x] 将 `taskStatus` 字段的 `dict="io.nop.ai.agent.plan.AgentTaskStatus"` 改为 `dict="io.nop.ai.agent.model.AgentExecStatus"`
- [x] 将 `planStatus` 字段的 `dict="io.nop.ai.agent.plan.AgentPlanStatus"` 改为 `dict="io.nop.ai.agent.model.AgentExecStatus"`
- [x] 确认 `planStatus` 字段的 `name="planStatus"` 与 `_AgentPlanModel.setPlanStatus()` 一致（已确认匹配，无需修改）

Exit Criteria:

- [x] record-mappings 中 Task 映射的 `field name` 为 `"status"`（非 `"taskStatus"`），杜绝 extProps 静默数据丢失
- [x] record-mappings 中不再引用 `AgentTaskStatus` 或 `AgentPlanStatus`
- [x] record-mappings 中所有 dict 引用指向 `io.nop.ai.agent.model.AgentExecStatus`
- [x] `grep -r "AgentTaskStatus\|AgentPlanStatus" nop-ai/nop-ai-agent/src/main/` 返回 0 匹配
- [x] **无静默跳过**：修改后的 record-mapping 中 Task 的 status 值通过 `setStatus()` 设置到 model 属性，而非存入 extProps
- [x] No owner-doc update required（本修改不改变 live baseline 的 public contract）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Add test for Markdown status parsing

Status: planned
Targets: `nop-ai/nop-ai-agent/src/test/`

- Item Types: `Proof`

- [ ] 编写测试：构造包含 `任务状态` / `计划状态` Markdown 列的输入，验证 record-mapping 解析后对应字段为 `AgentExecStatus` 枚举值
- [ ] 测试覆盖：`pending`、`running`、`completed`、`failed` 四种状态值
- [ ] 测试覆盖：status 字段为空时的默认行为（不抛异常）
- [ ] 关键断言：`taskModel.getStatus() == AgentExecStatus.pending`（验证 model 属性，非 extProps）
- [ ] 关键断言：`planModel.getPlanStatus() == AgentExecStatus.pending`（验证 model 属性，非 extProps）

Exit Criteria:

- [ ] 新测试文件存在于 `nop-ai/nop-ai-agent/src/test/`
- [ ] 测试断言 `taskModel.getStatus()` 返回正确的 `AgentExecStatus` 值（不是 extProps）
- [ ] 测试断言 `planModel.getPlanStatus()` 返回正确的 `AgentExecStatus` 值（不是 extProps）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Update roadmap and verify build

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Follow-up`

- [ ] 将 roadmap 中 L0-2 状态从 ❌ 改为 ✅
- [ ] 更新 §2.2 和 §5 技术债表中对应条目
- [ ] 更新 §7 审计检查清单中枚举一致性检查项
- [ ] 运行 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 确认编译通过
- [ ] 运行 `./mvnw test -pl nop-ai/nop-ai-agent -am` 确认全量测试通过
- [ ] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 确认无断裂链接

Exit Criteria:

- [ ] `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L0-2 标记为 ✅
- [ ] §5 技术债表"枚举 schema 不一致"条目标注为已解决
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 退出码 0
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 退出码 0
- [ ] 若 roadmap 变更影响 `docs-for-ai/` 路由或约定：相应更新；否则写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] record-mappings 不再引用不存在的枚举类
- [ ] record-mappings Task 映射的 field name 为 `status`（非 `taskStatus`），杜绝静默数据丢失到 extProps
- [ ] Markdown → AgentPlanModel 的 status 字段解析路径有测试覆盖，且测试断言 `getStatus()`/`getPlanStatus()` 返回正确的枚举值
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] Roadmap L0-2 标记为 ✅
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步：No owner-doc update required（record-mapping 是内部实现细节，不影响 public contract）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：无新增空壳代码，修改的文件有对应测试验证行为
- [ ] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- L1-11（缺失枚举类 AgentTaskStatus/AgentPlanStatus）将被标记为 ✅ 已确认无需额外工作（与 L0-2 合并解决），后续 plan 可更新 roadmap 中 L1-11 的状态
- `BaseAgent` 清理（L1-14）独立于本 plan

## Closure

Status Note: <<执行完成后填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 时填写>>
- Evidence: <<待 closure audit 时填写>>

Follow-up:

- L1-11 roadmap 状态更新（与 L0-2 合并解决，无需独立实现）
