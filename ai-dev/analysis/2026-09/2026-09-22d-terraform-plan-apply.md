# Terraform plan/apply 源码级调研：变更意图快照与批准后重放机制

> Status: resolved
> Date: 2026-09-22
> Scope: maker-checker 通用机制设计参考（快照/重放/staleness 语义）
> Conclusion: Terraform 用"ZIP 快照（变更集 + prior state + 配置快照 + 变量值）+ apply 时 serial/lineage 比对拒绝过期 + 逐变更重放一致性复核"三件套实现"审批意图快照 + 批准后重放"；本平台 maker-checker 快照应封存同样三元组，staleness 用版本号比对而非长持锁，且快照需加签名（Terraform OSS 无签名是其留白）。可借鉴点/不可照搬点见文末。

## Context

- 上位调研：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md` 把 Terraform 的 plan/apply 分离列为"审批意图快照 + 批准后重放"的典型实现，是本平台通用 maker-checker 核心语义的参考原型。
- 本文在源码级验证：plan 文件里到底封存了什么、apply 如何保证只执行已批准的变更、plan 与 apply 之间的状态漂移如何被发现。
- 源码：本地 `~/sources/terraform`（git clone --depth 1）。注意：现行版本为 BUSL-1.1 许可（含 IBM 版权头），非开源 MPL 版本，仅供设计参考，不可直接复制代码。文中路径均为仓库相对路径。

## 调研目标

1. `terraform plan -out` 产出的 plan 文件结构（封存哪些上下文）。
2. `terraform apply <planfile>` 如何做到"重放已批准变更"而非重新计算。
3. plan→apply 窗口期的 staleness 检测与防篡改机制。
4. 变更集数据模型、审批展示面（JSON）、策略钩子、并发保护、失败恢复。

## 调研结果

### 1. plan 文件：ZIP 包封存五类内容

- 物理格式：`terraform/internal/plans/planfile/writer.go` 的 `Create` 依次写入 `tfplan`（protobuf）、`tfstate`（refresh 后的先验状态快照）、`tfstate-prev`（上次运行结束状态）、`tfconfig/`（配置源码快照）、`.terraform.lock.hcl`（依赖锁）。
- 设计意图（`writer.go` 顶部注释原文要点）：plan 文件同时包含配置快照与最新状态快照，**以便 Terraform 能检测世界是否已变化并拒绝 apply**。
- 内存模型 `Plan`（`terraform/internal/plans/plan.go`）关键字段：`Changes`（变更集）、`PriorState`（注释明确"apply 动作使用的主快照"）、`PrevRunState`（仅用于报告漂移）、`VariableValues`（plan 时变量值固化，apply 时不可更改）、`Backend.Workspace`（禁止把 plan 应用到别的 workspace）、`TargetAddrs/ForceReplaceAddrs`（plan 选项快照）、`Applyable/Complete/Errored` 状态标记、`Checks`（check 块结果快照）、`FunctionResults`（函数调用结果哈希，apply 时校验）。
- 关键声明：`Plan` 类型注释明确"plan 必须伴随生成它的配置，因为 plan 本身不包含执行变更所需的全部信息"——**意图与上下文是一起封存的**。

### 2. apply 重放：按 diff 建图，不重新求值意图

- 入口：`terraform/internal/command/apply.go` `Run` → `LoadPlanFile` → 从 plan 内嵌 backend 配置重建 backend（`internal/backend/local/backend_local.go` `localRunForPlanFile`）。
- 核心执行：`terraform/internal/terraform/context_apply.go` `Context.Apply` 文档注释要求"传入的配置必须与 Plan 时相同"；以 `plan.Changes` 解码 + `plan.PriorState.DeepCopy()` 作为工作状态建图。
- 建图依据是 diff 而非 config：`terraform/internal/terraform/graph_builder_apply.go` `ApplyGraphBuilder` 用 `DiffTransformer`，类型注释："图从 diff（而非 config/state）构建，确保 apply 图不会修改任何不在 diff 里的资源"。
- 即 config 快照仍被加载（诊断/provider 配置/输出求值），但"对哪些对象做什么"完全由 `plan.Changes` 决定。

### 3. staleness 检测：版本号比对而非长持锁；无签名

- lineage 不一致 → "Saved plan does not match the given state"；serial 不一致 → **"Saved plan is stale"**（"the state was changed by another operation after the plan was created"）。见 `terraform/internal/backend/local/backend_local.go` `localRunForPlanFile`（约 300-341 行）；serial/lineage 定义在 `terraform/internal/states/statefile/version.go`。
- 依赖锁比对（约 282-299 行）：不一致报 "Inconsistent dependency lock file"，防止把 plan 拿到别的环境 apply。
- apply 最后一道防线：`terraform/internal/terraform/node_resource_apply_instance.go` `checkPlannedChange`（约 403-467 行）——apply 现场构造的 actualChange 与 plan 中 plannedChange 比对，action 不符或 after 值不兼容（`objchange.AssertObjectCompatible`）即报 "Provider produced inconsistent final plan" 中止。
- **plan 文件无签名/HMAC/checksum**（全包 grep 无命中）：文件内容可被篡改而不被发现，防护全靠上述环境一致性校验兜底。cloud 版 plan 本地不可用（`terraform/internal/cloud/cloudplan/saved_plan.go` 只存 RunID+Hostname 书签，`terraform/internal/plans/planfile/wrapped.go`）。

### 4. 变更集模型

- `terraform/internal/plans/changes.go`：`Changes{Resources, Queries, ActionInvocations, Outputs}`；`ResourceInstanceChange` 含 `Addr/PrevRunAddr`（moved 语义）/`ProviderAddr/Change/ActionReason/Private`（provider 不透明数据原样存原样还）。
- 动作枚举 `terraform/internal/plans/action.go`：`NoOp/Create '+'/Read/Update '~'/DeleteThenCreate '∓'/CreateThenDelete '±'/Delete '-'/Forget` 等，`IsReplace()` 覆盖 4 种 replace 组合。
- before/after：`Change` 结构（`changes.go`）持 `Before/After cty.Value`，序列化经 `NewDynamicValue`（msgpack，`terraform/internal/plans/dynamic_value.go`）；敏感路径单独存 `BeforeSensitivePaths/AfterSensitivePaths`。

### 5. 高风险标记与二次确认

- `ActionReason`（`terraform/internal/plans/changes.go` 约 539-630 行）：`ReplaceBecauseTainted/ReplaceByRequest/ReplaceByTriggers/ReplaceBecauseCannotUpdate` + 8 种 `DeleteBecause*`——**"为什么要 replace/delete"的结构化原因**。
- 确认交互只在 plan+apply 合一运行时出现（`terraform/internal/backend/local/backend_apply.go` `opApply`：`mustConfirm := hasUI && !op.AutoApprove && !trivialPlan`）；**提供 plan 文件时跳过确认**（`terraform/internal/command/apply.go` `helpApply`：将直接执行该 plan 描述的动作，不再提示）——批准动作 = 接受该文件这一行为本身。
- destroy 不能套用 plan 文件（`apply.go LoadPlanFile` 约束）。

### 6. 审批展示面：`terraform show -json`（只读，apply 不读它）

- 实现 `terraform/internal/command/jsonplan/`；顶层字段：`format_version/variables/planned_values/resource_drift/resource_changes/output_changes/prior_state/checks/timestamp/applyable/complete/errored`。
- `resource_changes[].change`（`terraform/internal/command/jsonplan/resource.go`、`plan.go`）：`actions`（replace 用 `["delete","create"]` 二元数组，注释明说审批 UI 只需扫 "delete"）、`before/after`、`after_unknown`（未知叶子标 true）、`before_sensitive/after_sensitive`（敏感叶子掩码）、`replace_paths`、`action_reason`——这套字段分类法就是给"人审"设计的。

### 7. 策略钩子与 cloud 边界

- OSS 版有可选插件式 policy 引擎：`terraform/internal/policy/`（环境变量 `TF_POLICY_PLUGIN` 指向 gRPC 插件，接口 `EvaluateResource/EvaluateProvider/EvaluateModule`）；plan 与 apply 都挂了 `PolicyClient`（`terraform/internal/terraform/context_plan.go`、`context_apply.go`）。
- entitlement 门槛（`terraform/internal/command/meta_policy.go`）：local backend 无授权，OSS 本地仅为实验通道；正式审批/策略/Run 编排在 HCP cloud 侧。

### 8. 并发保护与失败恢复

- state 锁：plan 与 apply **同样**在读取 state 前加锁（`terraform/internal/backend/local/backend_local.go` L57 `op.StateLocker.Lock`），结束即释放（`backend_plan.go`/`backend_apply.go` 的 defer Unlock）。plan→apply 窗口不持锁，靠 serial/lineage 校验兜底——**"快照 + 到期校验"而非"长事务"**。
- apply 中途失败：`Context.Apply` 保证返回部分更新的 state；`StateHook` 周期持久化（`terraform/internal/backend/local/backend_apply.go` 约 246-513 行）；持久化失败则 `backupStateForError` 写 `errored.tfstate`。
- 失败后**同一 plan 不可续跑**（serial 已变，再 apply 命中 "Saved plan is stale"），正确做法是重新 plan；资源级幂等由 provider 语义 + state 对账保证，create 失败资源标 tainted 供下次 replace（`terraform/internal/terraform/node_resource_apply_instance.go` `taintInstanceState`）。
- 出错 plan 可写出（`plan.Errored`）但 apply 拒绝："Cannot apply incomplete plan"（`backend_apply.go` 约 230 行）。

### 9. 审批链信息流与各环节防护

| 环节 | 证据 | 防护 |
|------|------|------|
| 意图固化 | `terraform/internal/backend/local/backend_plan.go` → `planfile.Create` | 变更集+变量值+backend/workspace+依赖锁封存 ZIP |
| 传输/展示 | `terraform/internal/command/views/show.go` + `jsonplan` | 只读层；敏感值掩码 |
| 批准动作 | `backend_apply.go` 提供文件即跳过 confirm | 批准 = 接受该文件 |
| apply 环境校验 | `backend_local.go` lineage/serial + 依赖锁 + workspace | 防漂移/防换环境重放，不防内容篡改 |
| apply 意图复核 | `checkPlannedChange` + `AssertObjectCompatible` | 重放结果与批准值不符即中止 |
| 审计快照 | `PrevRunState/PriorState/resource_drift/Checks/Timestamp/FunctionResults` | plan 当时"看到什么"连同漂移证据封存 |

## 与当前项目的关系

- **可借鉴**（对应上位分析文档 3.2 节设计）：
  1. 快照三元组：`requestData` 不应只存请求参数，应封存"变更集 + 校验时数据基线（版本号/关键字段快照）+ 生成变更所用配置版本"；
  2. staleness 用"送审时数据版本 vs 批准时数据版本"乐观校验（serial/lineage 思想），审批期间不长持锁；
  3. 重放一致性复核：执行前/执行中对比"实际结果 vs 批准快照"，不符即中止（checkPlannedChange 思想）；
  4. 审批视图与执行视图分离：审批 UI 字段分类法（结构化高风险原因、before/after、未知值提示、敏感值掩码）照抄 `jsonplan` 分类；
  5. "批准 = 接受快照"语义：approve 后按快照执行，不再走一遍新鲜请求解析。
- **不可照搬**：
  1. plan 文件无签名——审批快照走网络传输且环境不可信，必须服务端留存哈希或 HMAC；
  2. 失败后强制重新 plan——若重放成本高需自建断点/已完成子集跳过，并把部分成功状态及时持久化；
  3. BUSL 许可证：只借鉴设计，不可复制代码。

## Open Questions

- [ ] Nop 侧"数据基线版本"取什么粒度：ORM 实体 version 字段？bizObjId 级 revision 计数？跨实体复合变更时如何取统一 serial？
- [ ] 快照哈希由谁校验：approve 入口校验（服务端留存哈希）已够，是否还需要 checker 端到端验签？

## References

- 本地源码：`~/sources/terraform`（clone 自 github.com/hashicorp/terraform，--depth 1）
- 上位分析：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md`
- 关键源码文件：`terraform/internal/plans/planfile/writer.go`、`terraform/internal/plans/plan.go`、`terraform/internal/backend/local/backend_local.go`、`terraform/internal/terraform/context_apply.go`、`terraform/internal/terraform/graph_builder_apply.go`、`terraform/internal/terraform/node_resource_apply_instance.go`、`terraform/internal/plans/changes.go`、`terraform/internal/command/jsonplan/plan.go`
