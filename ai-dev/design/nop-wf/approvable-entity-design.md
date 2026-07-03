# 可审批实体集成设计

> Status: draft
> Created: 2026-07-03

## 定位

本文是 nop-wf 设计文档的第四篇，回答"**业务实体如何集成审批能力**"——在不修改 `WorkflowEngineImpl` 引擎核心的前提下，让任意业务实体通过声明式标记获得标准化的审批能力（提交/通过/驳回/撤回/反审核），并与 nop-wf 审批流、业务处理流正确串联。

面向在 nop-wf 之上构建带审批业务的应用开发者与平台开发者。

### 与 nop-wf 其他设计文档的关系

| 文档 | 回答的问题 | 本文关系 |
|------|-----------|---------|
| `approval-flow-design.md` | 引擎能做什么（步骤/分组/路由/action/actor/状态机/DAG） | 引擎层；本文在其之上构建实体集成层 |
| `extensions-design.md` | 如何扩展引擎（不改核心） | 扩展机制层；本文的 use-approval 能力遵循"不改引擎、走外置模块+扩展点"原则 |
| `dingflow-json-format.md` | 外部如何对接（DingFlow JSON） | 接口契约层；无关 |
| **本文** | **业务实体如何集成审批能力**（IApprovableBiz 契约 + use-approval tag + codegen + objMeta 配置 + 两流串联） | — |

四篇共享同一 Vision（引擎核心不可侵蚀、模型即代码），分别覆盖引擎、扩展、对接、**实体集成**四个维度。

## 核心理念：两流正交分离

带审批的业务包含两个正交关注点，分别由平台两个独立引擎承担：

| | 审批流 | 业务处理流 |
|---|---|---|
| 引擎 | **nop-wf**（独立工作流引擎，不依赖 nop-task） | **nop-task** |
| 文件 | `.xwf` | `.task.xml` |
| 职责 | 参与者分配、多级审批、会签/或签/串签、驳回、转办 | 校验、规则决策、过账、库存/状态回写 |
| 输出 | 审批结果信号（通过/驳回） | 业务数据变更（含 approveStatus 终态） |
| 是否知道业务语义 | 否——不知道 APPROVED 是什么 | 否——不知道审批有几级 |

**唯一接触点是"审批通过/驳回"这一个回调信号。** 审批流不含业务编排，业务流不含审批编排。

## 状态归属：状态归业务处理，不由 wf 写业务表

`approveStatus`（四态：UNSUBMITTED/SUBMITTED/APPROVED/REJECTED）的**唯一写者是业务处理流**（task 或 BizModel）。审批流（nop-wf）**不直接操作业务表的状态字段**。

nop-wf 引擎提供了 `bizEntityStateProp` + transition 的 `bizEntityState` 自动回写能力（`WorkflowEngineImpl.changeBizEntityState`，`wf.xdef` 根属性），**本设计不使用该能力**。理由：

1. **语义归属**：approveStatus 四态是业务概念，wf 不应感知。
2. **原子性**：状态变更几乎总伴随业务联动，必须在同一 `@BizMutation` 事务内完成。wf 直接改状态、回调再改业务会割裂并脱离事务。
3. **边界清晰**：业务实体归 BizModel/DAO 拥有，wf 越界直写违反边界。

wf 只通过回调信号通知"审批通过/驳回"，由业务处理流执行状态迁移与业务联动。wf 的 `bizEntityFlowIdProp` 仍可用于把生成 `wfId` 绑回业务表（仅关联，不写状态）。

## use-approval 能力声明

### tag 命名

业务实体在 ORM 模型 entity 层级标记：

```xml
<entity name="...Order" tagSet="use-approval" ...>
```

**命名依据**：对齐 nop 官方 entity 层级能力 tag 的唯一先例 `use-ext-field`（`use-` 前缀 + kebab-case 名词，见 `nop-sys.orm.xml` 的 `NopSysNoticeTemplate`）。nop 所有 tag 全小写/kebab-case，无 camelCase。

- `use-approval` 而非 `approveFlow`：camelCase 违例；`approval` 涵盖 DIRECT/WORKFLOW 全部模式，不预设"流程"（DIRECT 单级审核不是 flow）。
- 备选 `approvable`（对齐 `insertable`/`updatable` 形容词惯例）亦可，但 entity 层级先例是 `use-xxx`。

### tag 触发的 codegen

`use-approval` 由 codegen 链识别（落 nop-wf-core 的 codegen 扩展）。对标记实体生成的 `_Xxx.xbiz`（codegen 生成的基础 xbiz）通过 `x:extends` 继承平台 `approval-support.xbiz`（位于 nop-wf-core 的 VFS，定义标准审批 action 的声明与 source），自动获得审批能力：

```xml
<!-- codegen 生成的 _Xxx.xbiz -->
<biz x:schema="/nop/schema/biz/xbiz.xdef"
     x:extends="/nop/wf/base/approval-support.xbiz" ...>
```

| 生成物 | 来源 |
|--------|------|
| `I*Biz` 接口 | `IXxxBiz extends ICrudBiz<Xxx>, IApprovableBiz<Xxx>` |
| 标准审批 action | `approval-support.xbiz` 提供（`submitForApproval`/`approve`/`reject`/`withdrawApproval`/`reverseApprove`，含状态守卫 + wf 启动的 source），经 `x:extends` 自动继承 |
| `flowInstanceId` 字段 | ORM 列（若实体未显式声明，codegen 补齐） |

业务侧的 `Xxx.xbiz`（非下划线保留层）`x:extends="_Xxx.xbiz"`，可在此基础上用 `x:override` 注入业务联动（见下文"业务联动注入"）。

## IApprovableBiz 接口与标准 action

### 接口定义（落 nop-wf-core）

```java
public interface IApprovableBiz<T extends IOrmEntity> extends ICrudBiz<T> {
    T submitForApproval(String id, IServiceContext context);
    T withdrawApproval(String id, IServiceContext context);
    T approve(String id, IServiceContext context);
    T reject(String id, IServiceContext context);
    T reverseApprove(String id, IServiceContext context);
}
```

### 标准 action 命名与状态迁移

| action | 状态迁移 | 语义 |
|--------|---------|------|
| `submitForApproval` | UNSUBMITTED→SUBMITTED | 提交审批（按 objMeta 是否配 `wf:wfName` 决定是否启动 wf） |
| `withdrawApproval` | SUBMITTED→UNSUBMITTED | 撤回审批 |
| `approve` | SUBMITTED→APPROVED | 审批通过 |
| `reject` | SUBMITTED→REJECTED | 驳回 |
| `reverseApprove` | APPROVED→SUBMITTED | 反审核（回到待审批） |

> 命名避免 `submit`（与"提交表单/保存"语义混淆，脱离上下文不表意）；`submitForApproval` 明确"为审批而提交"。`approve`/`reject`/`reverseApprove` 本身是审批动词，在审批上下文足够明确，无需再加后缀。

### 标准 action 与业务联动注入（xbiz 层，非 Java 钩子）

codegen 为 use-approval 实体生成标准审批 action（`submitForApproval`/`approve`/`reject`/...）到 xbiz，每个 action 的 `<source>`（见 `xbiz.xdef` actions/mutation/source）含通用逻辑（状态守卫 + wf 启动 + 幂等）。

**业务联动不通过 Java 钩子，而是在 xbiz 层注入**（XDSL 定制，随 Delta 走）。两种方式：

**方式 A：在 action 的 source 追加（`x:override="append"`）**

业务 xbiz `x:extends` 生成基类后，在 `approve` action 的 source 追加联动 XPL：

```xml
<!-- Xxx.xbiz，extends codegen 生成的 _Xxx.xbiz -->
<mutation name="approve">
    <source x:override="append"><![CDATA[
        // 审批通过后的业务联动（过账/库存等），追加在标准状态迁移之后执行
        inject('biz_ErpFinVoucher').postEvent(buildEvent(entity));
    ]]></source>
</mutation>
```

`append` 在标准 source 之后追加；`prepend` 在之前插入。XDSL 合并按 action `name` 匹配。

**方式 B：用 `<observes>` 监听 action 触发**

xbiz 的 `<observes>/<observe>`（`xbiz.xdef` §observes）监听 BizModel action 执行（`from=bizObjName, event=actionId`）：

```xml
<observes>
    <observe id="on-approved" from="Xxx" eventPattern="approve">
        <source><![CDATA[
            // approve action 执行时自动触发，适合独立关注点（通知、分发、审计）
        ]]></source>
    </observe>
</observes>
```

两种都是 XDSL 注入，无需 Java。选择：联动需紧贴状态迁移顺序用 source `append`/`prepend`；独立关注点（可多处分发、与主流程解耦）用 `observe`。

## 标准字段约定

| 字段 | 类型 | 用途 |
|------|------|------|
| `approveStatus` | 字典 `wf/approve-status`（四态，**nop-wf 模块统一定义**） | 审批状态，业务处理流唯一写者 |
| `flowInstanceId` | String(32) | 关联 `nop_wf_instance.wfId`（VARCHAR32）；DIRECT/NONE 模式为空 |

`approveStatus` 的字典 `wf/approve-status` 由 nop-wf 模块统一定义（对齐 nop-wf 既有的 `wf/wf-status`/`wf/wf-def-status` 等字典），四态值标准化：`UNSUBMITTED`/`SUBMITTED`/`APPROVED`/`REJECTED`。所有 `use-approval` 实体引用此标准字典（`ext:dict="wf/approve-status"`），不各自定义。字段集权威源为各模块 `<domain>/model/*.orm.xml`；`use-approval` 实体必须含 `approveStatus`，WORKFLOW 模式须含 `flowInstanceId`。

## objMeta 承载流程配置（不用配置表）

**不引入审批配置表。** 每个业务实体的审批配置挂在 objMeta（XMeta）的扩展属性上（仅 `wf:wfName`；`wfVersion` 不配，启动时默认用最新版本），运行时通过 `thisObj.objMeta` 获取：

```xml
<!-- {BizObj}.xmeta：流程配置作为 wf: 命名空间属性挂在 meta 根标签上（XDSL 扩展属性惯例：attribute，非子节点） -->
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:wf="wf"
      wf:wfName="my-order-approval">
    <props/>
</meta>
```

```java
// BizModel 运行时：判断 wf:wfName 有无决定是否启动 wf（无需 approvalMode）
String wfName = thisObj.getObjMeta().getExt("wf:wfName");
if (wfName != null) { /* WORKFLOW：启动 wf */ } else { /* DIRECT：仅状态迁移 */ }
```

理由：
- objMeta 是每个 BizObject 的元数据载体，天然适合承载"按单据类型配置"。
- 流程配置随模型走，受 Delta 定制（不同租户/行业覆盖 xmeta 即改配置），无需建表、无需刷数据。
- 避免 `ApprovalConfig` 配置表带来的额外实体 + 运维负担。

## wf 回调串联

审批通过/驳回经 nop-wf 结束事件回调业务处理：

```
审批人在 wf 内 agree/reject
  │ wf 只编排审批流程，不碰业务表
  ▼ 流程结束事件（triggerEvent，见 WfRuntime + NopWfCoreConstants）
.xwf 的 <listener eventPattern="*end">
  │ source 经 xlib 标签回调业务 action
  ▼
biz.approve(id) / biz.reject(id)
  └─ action source 执行 approveStatus 迁移 + xbiz 注入的业务联动（source append / observe）
```

回调用 xlib 标签封装（落 nop-wf-core，命名空间如 `wf-approval`）：

```xml
<!-- .xwf listener -->
<listener id="on-approved" eventPattern="*end">
    <source>
        <wf-approval:notifyResult bizObj="Order" approved="true"/>
    </source>
</listener>
```

标签内部 `inject('biz_' + bizObj)` 调用 `approve`/`reject`（继承自 IApprovableBiz）。回调无需 Java 中间层。

> 事件名说明：引擎 `EVENT_AFTER_END` 当前常量值与 `EVENT_BEFORE_END` 同为 `"before-end"`，故 listener 的 `eventPattern` 用通配 `*end` 规避。

## DIRECT / WORKFLOW 双模（由 `wf:wfName` 有无决定）

不设独立的 `approvalMode` 配置——**`wf:wfName` 的有无本身即模式判断**，避免与 `wfName` 冗余及矛盾状态（如配了 wfName 却标 DIRECT）：

| 条件 | 模式 | submitForApproval 行为 | approve 入口 |
|------|------|----------------------|------------|
| 未标 `use-approval` | 无审批 | 不进入审批流程 | — |
| 标 `use-approval`、无 `wf:wfName` | DIRECT | approveStatus UNSUBMITTED→SUBMITTED，不启动 wf | 前端直接调 `approve` |
| 标 `use-approval`、有 `wf:wfName` | WORKFLOW | approveStatus→SUBMITTED + 启动 wf（绑 flowInstanceId） | 审批人在 wf 内 agree，结束事件经 listener 回调 `approve` |

两种审批模式**复用同一个 `approve` action**，业务联动统一经 xbiz `append`/`observe` 注入。submitForApproval 内按 `wfName` 有无分流（有 `wfName` 才启动 wf，避免 DIRECT 与 WORKFLOW 两条路径都跑导致双写）。

## 反模式

| 反模式 | 正确做法 |
|--------|---------|
| 用 `bizEntityStateProp`/`bizEntityState` 让 wf 直写 approveStatus | 状态归业务处理流，wf 只回调 |
| 在 `.xwf` source 里写业务编排（过账/库存） | 业务联动在 xbiz 层注入（action source `append` 或 `<observes>`），wf 只编排审批 |
| 建 `ApprovalConfig` 配置表存 wfName | 挂 objMeta `wf:` 命名空间属性，随模型/Delta 走 |
| 各业务 BizModel 手写 submit/approve 状态迁移 | 标 `use-approval`，由 codegen 生成标准 action source，联动用 xbiz `append`/`observe` 注入 |
| 用 Java 钩子（如 `onApproved`）做业务联动 | 联动在 xbiz 层注入（XDSL，随 Delta 定制），不写 Java 钩子 |
| 命名 action 为 `submit`（与保存混淆） | 用 `submitForApproval` |
| 用 camelCase tag（`approveFlow`） | 用 `use-approval`（对齐 `use-ext-field`） |
| listener 回调不做幂等 | `approve`/`reject` 入口由 guardTransition 守卫（wf 事件可能重试） |

## 设计约束（继承 nop-wf Vision）

- **引擎核心不可侵蚀**：本能力不修改 `WorkflowEngineImpl`，全部通过 codegen 生成 + objMeta 配置 + wf 扩展点（listener/xlib）实现，符合 `extensions-design.md` 原则。
- **模型即代码**：能力声明（`use-approval` tag）与配置（objMeta ext）都是模型，受 XDSL 校验与 Delta 定制。
- **不引入新模块**：接口/标签/codegen 模板落 `nop-wf-core`，随 nop-wf 分发，所有用 nop-wf 的应用自动获得。

## 相关文档

- `approval-flow-design.md` — 引擎能做什么（审批流核心模式）
- `extensions-design.md` — 如何扩展引擎
- `nop-entropy/docs-for-ai/02-core-guides/workflow-configuration.md` — wf 配置参考（step/transition/action/listener）
- `nop-entropy/docs-for-ai/03-runbooks/enable-approval-on-entity.md` — 实操指南：让业务实体具备审批能力
- `nop-entropy/docs-for-ai/03-runbooks/build-approval-flow.md` — 审批流模式配置（会签/或签/分支等）
- 源码锚点：`WfRuntime.triggerEvent`、`WorkflowEngineImpl.bindBizEntityFlowId`/`changeBizEntityState`、`wf.xdef`（`bizEntityFlowIdProp`/`listeners`）、`nop_wf_instance.wfId`
