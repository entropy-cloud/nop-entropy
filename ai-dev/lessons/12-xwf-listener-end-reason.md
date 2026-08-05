# 12: xwf listener 结束判定缺失 — *end listener 不区分驳回/通过，驳回即通过

> Date: 2026-08-05
> Severity: High — nop-metadata 3 条审批流 *end listener 未判定结束原因，R2.1 重写引入回归：disagree（驳回）结束同样触发 approve，驳回即通过/发起人自批

## 场景

nop-metadata 工作流（metaDataContractApproval / tagLabelConfirmApproval / qualityBreachApproval）在 `*end` 节点注册 listener 处理流程结束。R2.1 把 listener 从 xlib 方式重写为 c:script 后：

```xml
<!-- R2.1 引入的回归：只判断"流程结束"就 approve -->
<c:script>if (wfRt.wf.record.appState !== 'finished') ... approve()</c:script>
```

缺陷：**所有结束原因（agree 通过 / disagree 驳回 / 异常退出）都走到同一个 `*end` 事件**，listener 只判断 `appState !== 'finished'`，没有区分结束原因。结果：

1. 审批人点"驳回"（disagree）→ 流程结束 → listener 仍执行 approve → **驳回即通过**
2. 发起人可自批：提交后直接结束流程 → 自己 approve 自己
3. 功能从"驳回应保留 DRAFT 待改"变成"驳回即 ACTIVE"

R3.4 修复：*end listener 增加结束原因判定，`wfRt.wf.record.appState !== 'disagree'` 才 approve。

## 根因

1. **把"流程结束"当成"审批通过"**：xwf 的 `*end` 是流程终止事件，不是业务结果事件；通过/驳回是 record 上的状态（appState），必须显式判定。
2. **listener 重写时只做语法迁移**：R2.1 从 xlib 迁移到 c:script 时聚焦"调用方式"（import 依赖、API 存在性），没有逐条核对语义（结束原因分支），语义在迁移中丢失。
3. **缺少驳回路径测试**：只有"通过"正路径测试，没有 disagree 结束 → 不应 approve 的负路径测试。

## 正确做法

1. ***end listener 必须判定结束原因**：approve/reject 的单一事实源是 `record.appState`（或等价业务状态），不是 `wfRt` 流程状态；listener 内显式区分 `disagree`（驳回）与通过。
2. **xwf 重写/迁移类改动必须做语义 diff**：不能只验证"语法可解析、调用可编译"，要逐节点对比迁移前后行为（事件、分支、状态机转移）。
3. **驳回路径测试为必选项**：每条审批流至少一条负路径测试——disagree 结束 → approveStatus 保持 SUBMITTED、status 保持 DRAFT（R3.4 `testDataContractApprovalDisagreeDoesNotApprove` 模式）。

## 判定规则

> **"流程结束" ≠ "审批通过"**。审批流 listener 判定通过的唯一标准：业务状态（appState/approveStatus）显式等于"通过"值；结束原因不判定的 *end listener 按 P1 缺陷处理。
>
> xwf/listener 重写后必须验证：驳回路径、异常路径、正常路径三条路径的行为都符合预期，不能只验证正路径。

## 适用范围

- 所有 xwf 审批流的 *end/listener 编写与审查
- xwf 重写（xlib→c:script 等）的回归验证
- 工作流语义审计（approve/reject 单一事实源）

## 参考

- `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/wf/metaDataContractApproval/v1.xwf` + `tagLabelConfirmApproval/v1.xwf`（R3.4 修复后带 appState 判定）
- 审计：`ai-dev/audits/2026-08-05-0856-arm-MA7.6-nop-metadata-workflow.md`（P1-MA7.6-01）
- 修复：roadmap R2.1（引入回归）/ R3.4（结束原因守卫 + 测试）
