# 让业务实体具备审批能力

> **前置阅读**：[`02-core-guides/workflow-configuration.md`](../02-core-guides/workflow-configuration.md)（理解 step/transition/action/listener）
> **设计原理**：平台设计文档中的"可审批实体集成设计"（可审批实体集成的架构背景与约束）

本文以具体步骤说明如何让一个业务实体（如订单、申请单）获得标准化的审批能力（提交/通过/驳回/撤回/反审核），并与 nop-wf 审批流串联。

---

## 能力概览

标记 `use-approval` 的实体经 codegen 自动获得：

- `I*Biz extends IApprovableBiz`，暴露 5 个标准审批 action（自动成为 GraphQL mutation）
- `submitForApproval` / `withdrawApproval` / `approve` / `reject` / `reverseApprove`
- 通用审批 source（状态守卫 + wf 启动 + 幂等），业务联动在 xbiz 注入（`append`/`observe`）

审批状态四态：`UNSUBMITTED`（未提交）/ `SUBMITTED`（已提交待审批）/ `APPROVED`（已批准）/ `REJECTED`（已驳回，可经 `submitForApproval` 重提修正）。

---

## 步骤 1：ORM 模型声明能力

在实体上标记 `use-approval`，并确保有审批字段：

```xml
<entity name="...LeaveRequest" tagSet="use-approval" useWorkflow="true" ...>
    <columns>
        <column name="approveStatus" ext:dict="wf/approve-status" mandatory="true" .../>
        <column name="approvedBy" stdDomain="userId" stdSqlType="VARCHAR" precision="36" .../>
        <column name="approvedAt" stdSqlType="DATETIME" .../>
        <!-- WORKFLOW 模式才需要 useWorkflow="true"（OrmEntityModelInitializer 自动补 nopFlowId 列） -->
        <!-- 其他业务字段 -->
    </columns>
</entity>
```

**tag 命名说明**：`use-approval` 对齐 nop 官方 entity 能力 tag 先例 `use-ext-field`（`use-` 前缀 + kebab-case）。不要写成 camelCase 的 `approveFlow`。

> 字段集权威在 `<domain>/model/*.orm.xml`。`use-approval` 实体必须含 `approveStatus`/`approvedBy`/`approvedAt`（由 `approval-support.xbiz` 的标准 action 自动读写）。`nopFlowId` 列由平台 `OrmEntityModelInitializer` 对 `useWorkflow="true"` 实体自动补齐（无需 codegen）。

---

## 步骤 2：objMeta 配置流程属性

流程配置（审批模式、关联工作流）挂在 objMeta 扩展属性上，**不建配置表**：

```xml
<!-- {BizObj}.xmeta：流程配置作为 wf: 命名空间属性挂在 meta 根标签上（XDSL 扩展属性是 attribute，不是子节点） -->
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:wf="wf"
      wf:wfName="leave-approval">
    <props/>
</meta>
```

**不设 `approvalMode`**——`wf:wfName` 的有无本身区分模式（上面示例是 WORKFLOW，故配了 `wfName`）：

| 配置 | 模式 | 行为 |
|------|------|------|
| 未标 `use-approval` | 无审批 | 不进入审批 |
| 标 `use-approval`、不配 `wf:wfName` | DIRECT | 单级直接审核（不启动 wf） |
| 标 `use-approval`、配 `wf:wfName` | WORKFLOW | 多级审批（启动 wf，`wfVersion` 默认最新） |

运行时由 codegen 生成的 source 判断 `wf:wfName` 有无决定是否启动 wf（无需 `approvalMode`）。配置随 xmeta 走 Delta 定制，不同租户/行业覆盖 xmeta 即改配置。

---

## 步骤 3：codegen 生成审批骨架

执行代码生成后（见 [`generate-business-code.md`](generate-business-code.md)），`use-approval` 实体自动得到：

- `I*Biz extends ICrudBiz<Xxx>, IApprovableBiz<Xxx>`（Java 接口，codegen 条件追加）
- `_Xxx.xbiz` 通过 `x:extends` 继承 `approval-support.xbiz`，自动获得 5 个标准审批 action 的 source（状态守卫 + wf 启动，由 xbiz source 提供，非 Java BizModel）
- `nopFlowId` 列由平台 `OrmEntityModelInitializer` 对 `useWorkflow="true"` 实体自动补齐

**业务开发者无需手写 submit/approve 的状态迁移与 wf 启动逻辑**——这些由骨架统一处理。

---

## 步骤 4：注入业务联动（xbiz 层，非 Java 钩子）

标准 action 的状态迁移与审计字段（`approveStatus`/`approvedBy`/`approvedAt`）由 `approval-support.xbiz` 统一处理。**业务联动不写 Java 钩子，在 xbiz 层注入**（XDSL，随 Delta 定制）。

### 方式 A：在 action source 追加

业务 xbiz `x:extends="_Xxx.xbiz"`，在 `approve` action 的 source 用 `x:override="append"` 追加联动：

```xml
<!-- LeaveRequest.xbiz -->
<mutation name="approve">
    <source x:override="append"><![CDATA[
        // 审批通过后联动（扣减额度、发通知等）
        // approveStatus/approvedBy/approvedAt 已由 approval-support.xbiz 设置，无需重复
        inject('biz_LeaveBalance').deduct(entity.userId, entity.days);
    ]]></source>
</mutation>
```

`append` 在标准状态迁移之后执行；`prepend` 在之前。

### 方式 B：用 `<observes>` 监听 action 触发

```xml
<observes>
    <observe id="notify-on-approved" from="LeaveRequest" eventPattern="approve">
        <source><![CDATA[
            // approve action 执行时自动触发（通知等独立关注点）
        ]]></source>
    </observe>
</observes>
```

联动代码内部可调 task（[`service-layer.md`](../02-core-guides/service-layer.md)）或 `I*Biz`。纯审批、无联动的实体跳过此步。

> **xbiz source 内置变量**：`source` 中可用 `thisObj`（当前 BizObject）、`svcCtx`（`IServiceContext`，获取当前用户/上下文）、`entity`（`append` 场景下由基类 source 已声明）、`inject('beanName')`（获取 IoC Bean）、`now()`（全局函数，当前时间）。详见 [`xlang-and-xpl-basics.md`](../02-core-guides/xlang-and-xpl-basics.md) 的"xbiz action source 内置变量"章节。

---

## 步骤 5：WORKFLOW 模式编写审批流与回调

DIRECT 模式到此结束（前端直接调 `approve`）。WORKFLOW 模式还需：

### 5.1 定义审批流（.xwf）

见 [`build-approval-flow.md`](build-approval-flow.md) 配置步骤/会签/分支等。基模板 `x:extends="/nop/wf/base/oa.xwf"`。

```xml
<workflow wfName="leave-approval" wfVersion="1" displayName="请假审批"
          x:extends="/nop/wf/base/oa.xwf"
          bizEntityFlowIdProp="nopFlowId" ...>
    <start startStepName="submit"/><end/>
    <steps>
        <step name="submit" displayName="提交">
            <assignment><actors>
                <actor actorModelId="starter" actorType="wf-actor:Starter"/>
            </actors></assignment>
            <transition onAppStates="agree"><to-step stepName="manager-approval"/></transition>
        </step>
        <step name="manager-approval" displayName="经理审批" allowReject="true">
            <assignment><actors>
                <actor actorModelId="mgr" actorType="wf-actor:StarterManager" wf:upLevel="1"/>
            </actors></assignment>
            <transition onAppStates="agree"><to-end/></transition>
        </step>
    </steps>
</workflow>
```

### 5.2 配置结束回调

审批通过/驳回经 wf 结束事件回调业务 action。用 `wf-approval` xlib 标签封装（标签落 nop-wf-core）：

```xml
<listeners>
    <listener id="on-approved" eventPattern="*end">
        <source>
            <wf-approval:notifyResult bizObj="LeaveRequest" approved="true"/>
        </source>
    </listener>
</listeners>
```

标签内部通过 `IBizObjectManager.getBizObject(bizObj).invoke(...)` 调用 `approve`/`reject`（注意：Biz Object 不是 IoC bean，无 `biz_Xxx` 命名约定）。**审批状态由业务 action 改写，不由 wf 引擎直写**（不使用 `bizEntityStateProp`）。

> `eventPattern` 用通配 `*end`：引擎 `EVENT_AFTER_END` 常量值当前与 `EVENT_BEFORE_END` 同为 `before-end`，通配规避。

---

## 完整链路

```
DIRECT 模式:
  前端调 approve() ──▶ action source: SUBMITTED→APPROVED
                       + xbiz append/observe 注入的业务联动

WORKFLOW 模式:
  前端调 submitForApproval() ──▶ action source: UNSUBMITTED→SUBMITTED + 启动 wf(绑 nopFlowId)
        │
        ▼ 审批人在 wf 内 agree（多级/会签…），wf 不碰业务表
  <listener eventPattern="*end">
        │ <wf-approval:notifyResult bizObj="..." approved="true"/>
        ▼
  approve action source: SUBMITTED→APPROVED + xbiz append/observe 注入的业务联动
```

两种模式复用同一个 `approve` action；业务联动统一经 xbiz `append`/`observe` 注入（不写 Java 钩子）。

---

## 常见误区

| 误区 | 正确做法 |
|------|---------|
| 手写 submit/approve 状态迁移 | 标 `use-approval`，由 codegen 生成标准 action source，联动用 xbiz `append`/`observe` 注入 |
| 手写 approvedBy/approvedAt 回写 | `approval-support.xbiz` 的 `approve`/`reject` action 已自动设置（`svcCtx.getUserId()` / `now()`），`reverseApprove` 自动清空 |
| 用 wf 的 `bizEntityStateProp` 让引擎改 approveStatus | 审批状态由业务 action（BizModel/xbiz 层）改，wf 只回调 |
| 在 `.xwf` source 写业务联动（扣额度/通知） | 联动在 xbiz 层注入（action source `append` / `<observes>`），wf 只编排审批 |
| 用 Java 钩子（`onApproved`）做业务联动 | 联动在 xbiz 层注入（XDSL，随 Delta 定制），不写 Java 钩子 |
| 建 ApprovalConfig 配置表 | 流程属性挂 objMeta 扩展属性 |
| action 命名 `submit` | 用 `submitForApproval`（避免与保存混淆） |
| 假设 REJECTED 是终态不可重提 | `submitForApproval` 接受 `REJECTED` 源态，允许驳回后修正重审（守卫放行 `UNSUBMITTED`/`null`/`REJECTED` 三态） |
| 期望 `reverseApprove` 回到 `SUBMITTED`（待审队列） | `reverseApprove` 目标态为 `REJECTED`（保留审计语义：审批被作废而非回到待审）。清空 `approvedBy`/`approvedAt` 表示作废，与 `reject`（写入驳回人审计字段）区分 |

---

## 参考

- 设计原理：平台设计文档中的"可审批实体集成设计"
- 审批流模式配置：[`build-approval-flow.md`](build-approval-flow.md)（会签/或签/分支/驳回等 15 个示例）
- wf 配置参考：[`02-core-guides/workflow-configuration.md`](../02-core-guides/workflow-configuration.md)
- 服务层编写：[`02-core-guides/service-layer.md`](../02-core-guides/service-layer.md)
- 代码生成：[`generate-business-code.md`](generate-business-code.md)
