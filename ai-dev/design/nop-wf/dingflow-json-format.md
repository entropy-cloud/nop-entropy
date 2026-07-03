# DingFlow JSON 格式设计

> Status: draft
> Created: 2026-07-02
> Scope: 钉钉审批流 JSON 导入格式的完整规格定义

## 定位

本文档定义 nop-wf 的 **DingFlow JSON 格式**——一种面向审批流的 JSON 序列化格式，用于：
1. 从钉钉审批导入流程定义
2. 前端可视化设计器输出中间格式
3. 转换器（`dingflow-tran.xlib`）的输入契约

**设计约束**：
- 所有枚举值使用**字符串类型**（非数字），便于后期扩展
- 覆盖**全部审批场景**（发起→审批→抄送→条件路由→子流程→超时→结束），不含通用工作流的定时器节点和触发器节点
- 必须覆盖 nop-wf `approval-flow-design.md` 定义的全部审批模式
- 生成的 XWF 必须是完整 DAG，通过 `WfModelAnalyzer` 校验

**关联文档**：
- `approval-flow-design.md` — 审批流核心模式（XWF 目标格式）
- `extensions-design.md` — P1/P3 设计原则（转换器不侵蚀引擎核心）
- `ai-dev/analysis/2026-07-02-flowlong-warmflow-dingflow-json-format-comparison.md` — 格式对比分析

---

## 一、顶层结构

```json
{
  "processName": "salary-adjustment",
  "displayName": "调薪审批",
  "version": "1",
  "form": { ... },
  "nodeConfig": { ... }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `processName` | string | 是 | 流程标识（唯一），对应 XWF 的 `wfName` |
| `displayName` | string | 是 | 流程显示名称 |
| `version` | string | 否 | 版本号，默认 `"1"` |
| `form` | object | 否 | 表单字段定义（见 §七） |
| `nodeConfig` | object | 是 | 根节点，必须是 `type: "start"`（见 §二） |

---

## 二、节点结构

所有节点共享基础字段，按 `type` 区分子字段。

### 2.1 基础字段（所有节点）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | 是 | 节点类型（见 §2.2） |
| `id` | string | 否 | 节点唯一标识。未提供时转换器自动生成（按遍历顺序 `step1`, `step2`, ...） |
| `displayName` | string | 是 | 节点显示名称 |
| `childNode` | object | 否 | 顺序下一节点（单链表指针）。无 `childNode` 表示流程结束 |
| `formAuths` | array | 否 | 表单字段权限（见 §八） |

### 2.2 节点类型

| type 值 | 说明 | 对应 nop-wf XWF |
|---------|------|-----------------|
| `"start"` | 发起人节点。根节点必须是此类型 | `<step>` + `actorType="wf-actor:CurrentCaller"` |
| `"approval"` | 审批节点。支持会签/或签/顺签/票签 | `<step>` + `execGroupType` |
| `"cc"` | 抄送节点。通知相关人员，需确认已阅 | `<step specialType="cc">` |
| `"router"` | 条件路由节点。包含 `branches[]`，按条件选择分支 | `<step>` + `<transition splitType="or">` |
| `"subprocess"` | 子流程节点。启动一个子工作流，等待其结束后继续 | `<flow>` step type |
| `"end"` | 结束节点。显式标记流程结束 | `<to-end/>` |

> **扩展约定**：未来如需新增节点类型（如 `"parallel"` 并行分发），只需新增字符串值，不破坏现有解析。

### 2.3 `start` 节点

```json
{
  "type": "start",
  "displayName": "发起人",
  "formAuths": [ ... ],
  "childNode": { ... }
}
```

发起人节点固定使用 `wf-actor:CurrentCaller`（当前调用者），无需 `assignment`。
`start` 节点的 `childNode` 通常是第一个审批节点或 `router`。

### 2.4 `approval` 节点

```json
{
  "type": "approval",
  "id": "dept-approve",
  "displayName": "部门经理审批",
  "assignment": { ... },
  "examineMode": "countersign",
  "voteConfig": { ... },
  "rejectConfig": { ... },
  "selfApprove": { ... },
  "timeout": { ... },
  "permissions": ["transfer", "add-sign"],
  "formAuths": [ ... ],
  "childNode": { ... }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `assignment` | object | 是 | 审批人分配（见 §三） |
| `examineMode` | string | 否 | 协作模式（见 §四）。默认 `"single"`（单人审批） |
| `voteConfig` | object | 否 | 票签配置，仅 `examineMode="vote"` 时有效（见 §四） |
| `rejectConfig` | object | 否 | 驳回策略（见 §五） |
| `selfApprove` | object | 否 | 审批人=发起人时的处理策略（见 §六） |
| `timeout` | object | 否 | 超时配置（见 §九） |
| `permissions` | array | 否 | 允许的操作列表（见 §十） |

### 2.5 `cc` 节点

```json
{
  "type": "cc",
  "id": "cc-hr",
  "displayName": "抄送HR",
  "assignment": { ... },
  "formAuths": [ ... ],
  "childNode": { ... }
}
```

抄送节点固定使用 `confirm`（已阅）action 前进。对应 XWF 的 `specialType="cc"`。

### 2.6 `router` 节点

```json
{
  "type": "router",
  "id": "amount-router",
  "displayName": "金额路由",
  "branches": [
    {
      "displayName": "小额审批",
      "priority": 1,
      "conditions": [ ... ],
      "childNode": { ... }
    },
    {
      "displayName": "大额审批",
      "priority": 2,
      "conditions": [],
      "childNode": { ... }
    }
  ],
  "childNode": { ... }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `branches` | array | 是 | 条件分支列表，按 `priority` 升序匹配，第一个满足的分支执行 |
| `childNode` | object | 否 | **所有分支汇聚后的后续节点**。如果所有 `branches` 的 `childNode` 执行完毕后，流程回到此节点的 `childNode` 继续 |

**分支结构**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `displayName` | string | 是 | 分支显示名称 |
| `priority` | number | 是 | 优先级（数字越小优先级越高）。转换器映射为 XWF `<to-step order="N">` |
| `conditions` | array | 是 | 条件列表。**空数组 `[]` 表示默认分支**（else），无需条件匹配 |
| `childNode` | object | 否 | 命中此分支后执行的节点子树 |

**转换规则**：`router` 转换为 nop-wf 的 `splitType="or"` transition + 多个 `<to-step order=N><when>` + 自动 `<join>` 汇聚。详见 §十二。

### 2.7 `subprocess` 节点

```json
{
  "type": "subprocess",
  "id": "sub-review",
  "displayName": "技术评审子流程",
  "subprocess": {
    "processName": "tech-review",
    "version": "1",
    "async": false,
    "args": {
      "amount": "${wfVars.amount}",
      "projectName": "${wfVars.projectName}"
    },
    "outputs": {
      "reviewResult": "wfVars.reviewResult",
      "reviewer": "wfVars.reviewer"
    }
  },
  "childNode": { ... }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `subprocess` | object | 是 | 子流程配置（见下表） |

**subprocess 配置**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `processName` | string | 是 | 子流程的 `wfName`（工作流定义名） |
| `version` | string | 否 | 子流程版本，默认 `"1"` |
| `async` | boolean | 否 | 是否异步执行。`false`（默认）= 等待子流程结束后继续；`true` = 不等待，直接进入 `childNode` |
| `args` | object | 否 | 传给子流程的参数（key-value）。值支持 XLang 表达式 `${...}` 引用父流程变量 |
| `outputs` | object | 否 | 子流程结束后回传到父流程的变量映射。key = 子流程输出变量名，value = 父流程变量名 |

对应 nop-wf 的 `<flow>` step type：

```xml
<flow name="sub-review" displayName="技术评审子流程">
    <start wfName="tech-review" wfVersion="1">
        <arg name="amount" value="${wfVars.amount}"/>
        <arg name="projectName" value="${wfVars.projectName}"/>
    </start>
    <output name="reviewResult" as="wfVars.reviewResult"/>
</flow>
```

### 2.8 `end` 节点

```json
{
  "type": "end"
}
```

结束节点无 `childNode`、无 `assignment`。对应 XWF 的 `<to-end/>`。

---

## 三、审批人分配（assignment）

`assignment` 对象定义如何确定审批人。

### 3.1 assignment.type 枚举

| type 值 | 说明 | 额外字段 | 对应 nop-wf actorType |
|---------|------|---------|----------------------|
| `"user"` | 指定用户 | `userIds: string[]` | `actorType="user" actorId=...` |
| `"role"` | 指定角色 | `roleIds: string[]` | `actorType="role" actorId=...` |
| `"dept"` | 指定部门 | `deptIds: string[]` | `actorType="dept" actorId=...` |
| `"starter"` | 流程发起人 | 无 | `actorType="wf-actor:Starter"` |
| `"current-caller"` | 当前调用者 | 无 | `actorType="wf-actor:CurrentCaller"` |
| `"starter-manager"` | 发起人的上级主管 | `level: number`（上溯层级，默认 1） | `actorType="wf-actor:StarterManager"` + `wf:upLevel` |
| `"starter-dept-manager"` | 发起人的部门负责人 | `level: number`（上溯层级，默认 0） | `actorType="wf-actor:StarterDeptManager"` + `wf:upLevel` |
| `"self-select"` | 发起人自选 | `selection: string`（见 §3.2） | `selectUser="true"` + `selection` |
| `"all-users"` | 所有用户 | 无 | `actorType="all" actorId="all"` |

> **扩展约定**：自定义 actor 类型可通过新增 `type` 值 + 注册对应的 `wf-actor:xxx` 标签实现。

### 3.2 selection 枚举（仅 `self-select`）

| selection 值 | 说明 | 对应 nop-wf |
|-------------|------|------------|
| `"auto"` | 自动选择（如果只有一个 actor 则自动分配） | `selection="auto"` |
| `"single"` | 用户从候选人中选择一个 | `selection="single"` |
| `"multiple"` | 用户从候选人中选择多个 | `selection="multiple"` |

### 3.3 示例

```json
// 指定用户
{ "type": "user", "userIds": ["u001", "u002"] }

// 角色
{ "type": "role", "roleIds": ["manager", "vp"] }

// 发起人上级（上溯2级）
{ "type": "starter-manager", "level": 2 }

// 发起人自选（单选）
{ "type": "self-select", "selection": "single" }

// 部门
{ "type": "dept", "deptIds": ["dept-finance"] }
```

### 3.4 多 actor 组合

当一个审批节点需要多种类型的审批人时，使用 `actors` 数组：

```json
"assignment": {
  "actors": [
    { "type": "user", "userIds": ["u001"] },
    { "type": "role", "roleIds": ["manager"] }
  ]
}
```

每个 actor 生成一个 XWF `<actor>` 元素，共享同一个 `execGroup`。

单 actor 时可简写为 `"assignment": { "type": "user", "userIds": ["u001"] }`。

---

## 四、协作模式（examineMode）

### 4.1 枚举值

| examineMode 值 | 说明 | 对应 nop-wf execGroupType |
|---------------|------|--------------------------|
| `"single"` | 单人审批（默认）。只有一个审批人时无需分组 | `none` |
| `"sequential"` | 顺签。按顺序逐一审批 | `seq-group` |
| `"countersign"` | 会签。所有人并行审批，全部同意才通过 | `and-group` |
| `"or-sign"` | 或签。任意一人审批即通过 | `or-group` |
| `"vote"` | 票签。按权重/比例/人数投票 | `vote-group` |

### 4.2 voteConfig（仅 `examineMode="vote"` 时）

```json
"examineMode": "vote",
"voteConfig": {
  "passType": "percent",
  "passPercent": 0.6
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `passType` | string | 是 | 通过判定方式（见下表） |
| `passWeight` | number | 否 | `passType="weight"` 时生效。已通过成员的权重和达到此值即通过 |
| `passPercent` | number | 否 | `passType="percent"` 时生效。已通过成员的权重占比达到此比例即通过（0~1） |
| `passCount` | number | 否 | `passType="count"` 时生效。已通过人数达到此值即通过 |
| `rejectCount` | number | 否 | `passType="count"` 时可选。已被拒绝人数达到此值即整体拒绝 |

| passType 值 | 说明 | 对应 nop-wf |
|------------|------|------------|
| `"weight"` | 按权重通过 | `passWeight` 属性 |
| `"percent"` | 按比例通过 | `passPercent` 属性 |
| `"count"` | 按人数通过/拒绝 | `wf-vote:passCount` / `wf-vote:rejectCount` 标签 |

### 4.3 权重设置

票签场景下，每个审批人可设置权重。在 `actors` 数组中为每个 actor 设置 `weight`：

```json
"assignment": {
  "actors": [
    { "type": "user", "userIds": ["u001"], "weight": 3 },
    { "type": "user", "userIds": ["u002"], "weight": 2 },
    { "type": "user", "userIds": ["u003"], "weight": 1 }
  ]
},
"examineMode": "vote",
"voteConfig": {
  "passType": "weight",
  "passWeight": 4
}
```

未设置 `weight` 时默认为 1。

---

## 五、驳回策略（rejectConfig）

```json
"rejectConfig": {
  "strategy": "to-previous",
  "targetStep": "start"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `strategy` | string | 是 | 驳回策略（见下表） |
| `targetStep` | string | 否 | `strategy="to-specified"` 时必填。目标步骤的 `id` |

| strategy 值 | 说明 | 对应 nop-wf |
|------------|------|------------|
| `"to-previous"` | 驳回到上一个审批步骤（默认） | `doReject` 默认行为（DAG 父节点） |
| `"to-initiator"` | 驳回到发起人 | `rejectSteps` 指定 start step |
| `"to-specified"` | 驳回到指定步骤 | `rejectSteps` 指定目标步骤 |
| `"terminate"` | 驳回后终止流程 | `disagree` action + `<to-end/>` |

未配置 `rejectConfig` 时，默认使用 `"to-previous"` 策略。

---

## 六、审批人=发起人处理（selfApprove）

当审批人与发起人是同一人时的处理策略。

```json
"selfApprove": {
  "strategy": "to-supervisor",
  "level": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `strategy` | string | 是 | 处理策略（见下表） |
| `level` | number | 否 | `strategy="to-supervisor"` 或 `"to-dept-manager"` 时有效。上溯层级 |

| strategy 值 | 说明 |
|------------|------|
| `"self-approve"` | 由发起人自己审批（允许自审） |
| `"skip"` | 自动跳过此步骤 |
| `"to-supervisor"` | 转交给直接上级审批 |
| `"to-dept-manager"` | 转交给部门负责人审批 |

未配置 `selfApprove` 时，默认允许自审（`"self-approve"`）。

---

## 七、表单定义（form）

```json
"form": {
  "fields": [
    {
      "name": "amount",
      "type": "money",
      "label": "调整后工资",
      "required": true,
      "placeholder": "请输入金额"
    },
    {
      "name": "reason",
      "type": "textarea",
      "label": "调薪原因",
      "required": false
    },
    {
      "name": "department",
      "type": "select",
      "label": "所属部门",
      "options": ["财务", "技术", "人事"]
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 字段标识。在条件表达式中通过此名引用 |
| `type` | string | 是 | 字段类型：`"text"` / `"textarea"` / `"money"` / `"number"` / `"select"` / `"date"` / `"datetime"` / `"file"` |
| `label` | string | 是 | 显示名称 |
| `required` | boolean | 否 | 是否必填 |
| `placeholder` | string | 否 | 输入提示 |
| `options` | array | 否 | `type="select"` 时的选项列表 |

表单字段在运行时通过 `wfVars.{name}` 或 `wfRt.bizEntity.{name}` 引用。

---

## 八、表单字段权限（formAuths）

每个节点可配置表单字段的读写权限。

```json
"formAuths": [
  { "field": "amount", "readable": true, "editable": false },
  { "field": "reason", "readable": true, "editable": true }
]
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `field` | string | 是 | 表单字段名（对应 `form.fields[].name`） |
| `readable` | boolean | 否 | 是否可读，默认 `true` |
| `editable` | boolean | 否 | 是否可编辑，默认 `false` |

对应 nop-wf 的 `<wf:formAuth>` actor 级字段权限。

---

## 九、超时配置（timeout）

```json
"timeout": {
  "duration": "48h",
  "action": "auto-reject",
  "remindBefore": "4h"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `duration` | string | 是 | 超时时长。duration 表达式，如 `"30m"` / `"48h"` / `"2d"` / `"1w"`（见 §九.1） |
| `action` | string | 是 | 超时动作（见下表） |
| `remindBefore` | string | 否 | 提前提醒时长（同 duration 格式，如 `"4h"`）。需要 `nop-wf-scheduler` 模块支持 |

| action 值 | 说明 | 对应 nop-wf |
|----------|------|------------|
| `"auto-pass"` | 超时自动通过 | `dueAction` 调用 `agree` action |
| `"auto-reject"` | 超时自动拒绝 | `dueAction` 调用 `reject` action |
| `"remind"` | 仅提醒，不自动处理 | `remindTime` 字段 + 外部调度器 |

对应 nop-wf 的 `dueTime` / `dueAction` / `remindTime` 字段。实际触发依赖 `nop-wf-scheduler` 模块（见 `extensions-design.md` §五）。

### 9.1 duration 表达式格式

`duration` 和 `remindBefore` 字段统一使用 **duration 字符串**，格式为 `{数值}{单位}`：

| 单位 | 含义 | 示例 |
|------|------|------|
| `s` | 秒 | `"30s"` |
| `m` | 分钟 | `"15m"` |
| `h` | 小时 | `"48h"` |
| `d` | 天 | `"3d"` |
| `w` | 周 | `"1w"` |

支持复合写法：`"1d6h"`（1天6小时）。数值支持小数：`"1.5h"`（1.5小时）。

> duration 表达式是全局复用的格式约定，凡是需要表达时间时长的字段（如 `timeout.duration`、`timeout.remindBefore`、未来的节点延迟配置等）统一使用此格式。

---

## 十、操作权限（permissions）

控制审批人在此节点可执行的操作。

```json
"permissions": ["transfer", "add-sign", "reject"]
```

| permission 值 | 说明 | 对应 nop-wf |
|--------------|------|------------|
| `"agree"` | 允许同意。默认启用 | `agree` action |
| `"reject"` | 允许驳回。默认启用 | `reject` action |
| `"transfer"` | 允许转交 | `transferToActor` |
| `"add-sign"` | 允许加签（临时增加审批人） | `addActor` |
| `"delegate"` | 允许委派（受派人处理完后回到当前步骤） | `transferToActor(exitCurrentStep=false)` |
| `"withdraw"` | 允许撤回（仅 start 节点有效） | `doWithdraw` |

未配置 `permissions` 时，默认启用 `agree` 和 `reject`。

---

## 十一、条件表达式（conditions）

`router` 节点的 `branches[].conditions` 使用结构化条件。

### 11.1 单条件

```json
"conditions": [
  { "field": "amount", "operator": ">", "value": 50000 }
]
```

多个条件之间默认 **AND** 关系。

### 11.2 条件组（OR/AND 组合）

使用 `conditionGroups` 表达 OR 关系（组间 OR，组内 AND）：

```json
"conditionGroups": [
  {
    "logic": "and",
    "conditions": [
      { "field": "amount", "operator": ">", "value": 50000 },
      { "field": "department", "operator": "==", "value": "finance" }
    ]
  },
  {
    "logic": "and",
    "conditions": [
      { "field": "department", "operator": "==", "value": "hr" }
    ]
  }
]
```

> 上述含义：`(amount > 50000 AND department == "finance") OR (department == "hr")`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `conditionGroups` | array | 否 | 条件组列表。**组间 OR**。优先于 `conditions` |
| `conditionGroups[].logic` | string | 否 | 组内逻辑，目前仅 `"and"`（预留 `"or"` 扩展） |
| `conditionGroups[].conditions` | array | 是 | 组内条件列表 |
| `conditions` | array | 否 | 简化形式，等同于一个 `conditionGroups` 元素。**元素间 AND** |

### 11.3 operator 枚举

| operator 值 | 说明 | XLang 等价 |
|------------|------|-----------|
| `"=="` | 等于 | `<eq>` |
| `"!="` | 不等于 | `<ne>` |
| `">"` | 大于 | `<gt>` |
| `">="` | 大于等于 | `<ge>` |
| `"<"` | 小于 | `<lt>` |
| `"<="` | 小于等于 | `<le>` |
| `"in"` | 包含于列表 | `<in>` |
| `"not-in"` | 不包含于列表 | `<not-in>` |
| `"contains"` | 字符串包含 | `<contains>` |
| `"not-contains"` | 字符串不包含 | `<not-contains>` |
| `"empty"` | 为空 | `<empty>` |
| `"not-empty"` | 不为空 | `<not-empty>` |

### 11.4 field 引用

`field` 值对应 `form.fields[].name`。转换器生成 XLang 表达式时自动添加前缀：

- 业务实体字段：`wfRt.bizEntity.{field}`
- 流程变量：`wfVars.{field}`
- 发起人属性：`wfRt.starter.{field}`（如 `wfRt.starter.deptId`）

转换器默认使用 `wfRt.bizEntity.{field}`。如需引用发起人属性，使用点号路径：`"field": "starter.deptId"`。

---

## 十二、转换映射规则（JSON → XWF）

### 12.1 总体流程

```
DingFlow JSON (树)
    │
    ▼  树遍历 + 展平
中间 DAG (steps + transitions + joins)
    │
    ▼  XML 模板生成
nop-wf XWF (DAG)
```

转换器实现遵守 `extensions-design.md` 的 P1 原则（不侵蚀引擎核心），放在 `nop-wf-service` 或 `dingflow-tran.xlib` 层。

### 12.2 节点映射

| JSON 节点 | XWF 产物 |
|----------|---------|
| `start` | `<step name="start">` + `<actor actorType="wf-actor:CurrentCaller">` + `<transition><to-step ...></transition>` |
| `approval` | `<step execGroupType="...">` + `<assignment><actors>...</actors></assignment>` + `<transition onAppStates="agree">` |
| `cc` | `<step specialType="cc">` + `<transition onAppStates="confirm">` |
| `router` | 前一步骤的 `<transition splitType="or">` + 多个 `<to-step order=N><when>` + 自动 `<join>` |
| `subprocess` | `<flow name="...">` + `<start wfName="..." wfVersion="...">` + `<arg>` + `<output>` |
| `end` | 前一步骤的 `<transition><to-end/></transition>` |

### 12.3 examineMode 映射

| examineMode | XWF execGroupType |
|------------|-------------------|
| `"single"` | `none` |
| `"sequential"` | `seq-group` |
| `"countersign"` | `and-group` |
| `"or-sign"` | `or-group` |
| `"vote"` | `vote-group` + `passWeight`/`passPercent` 或 `<check-exec-group-complete>` |

### 12.4 router 展平规则

`router` 节点转换为：
1. 前一步骤的 `<transition splitType="or">`
2. 每个 `branch` 转换为 `<to-step order="{priority}"><when>{conditions}</when></to-step>`
3. 分支内 `childNode` 子树展开为独立的 step 链
4. 所有分支末尾自动汇聚到一个 `<join>` 步骤
5. `<join>` 的 `childNode`（即 router 的 `childNode`）作为汇聚后的后续步骤

```
router 节点:
  branches: [B1(childNode: A→B), B2(childNode: C)]
  childNode: D

转换为 XWF:
  prev-step → splitType="or" → [A→B(when B1条件), C(when B2条件)]
                                        ↓              ↓
                                     join → D
```

### 12.5 自动 join 生成

转换器为每个 `router` 自动生成一个 `<join>` 步骤：

```xml
<join name="join-{routerId}" displayName="合并" internal="true">
    <transition>
        <to-step stepName="{router.childNode.id}"/>
    </transition>
</join>
```

所有分支的末尾 step 的 transition 指向此 join。

---

## 十三、完整示例

### 13.1 简单线性审批

```json
{
  "processName": "expense-reimburse",
  "displayName": "费用报销",
  "version": "1",
  "nodeConfig": {
    "type": "start",
    "displayName": "发起人",
    "childNode": {
      "type": "approval",
      "id": "manager-approve",
      "displayName": "部门经理审批",
      "assignment": { "type": "starter-manager", "level": 1 },
      "permissions": ["agree", "reject", "transfer"],
      "childNode": {
        "type": "cc",
        "id": "cc-finance",
        "displayName": "抄送财务",
        "assignment": { "type": "role", "roleIds": ["finance"] },
        "childNode": {
          "type": "end"
        }
      }
    }
  }
}
```

### 13.2 条件路由 + 会签

```json
{
  "processName": "salary-adjustment",
  "displayName": "调薪审批",
  "version": "1",
  "form": {
    "fields": [
      { "name": "amount", "type": "money", "label": "调整后工资", "required": true },
      { "name": "department", "type": "select", "label": "所属部门", "options": ["finance", "tech", "hr"] }
    ]
  },
  "nodeConfig": {
    "type": "start",
    "displayName": "发起人",
    "formAuths": [
      { "field": "amount", "readable": true, "editable": true },
      { "field": "department", "readable": true, "editable": true }
    ],
    "childNode": {
      "type": "router",
      "id": "dept-router",
      "displayName": "部门路由",
      "branches": [
        {
          "displayName": "财务部",
          "priority": 1,
          "conditions": [
            { "field": "department", "operator": "==", "value": "finance" }
          ],
          "childNode": {
            "type": "approval",
            "id": "finance-vote",
            "displayName": "财务部会签",
            "assignment": {
              "actors": [
                { "type": "user", "userIds": ["u001"], "weight": 1 },
                { "type": "user", "userIds": ["u002"], "weight": 1 },
                { "type": "user", "userIds": ["u003"], "weight": 1 }
              ]
            },
            "examineMode": "vote",
            "voteConfig": {
              "passType": "count",
              "passCount": 2
            },
            "rejectConfig": { "strategy": "to-initiator" }
          }
        },
        {
          "displayName": "其他部门",
          "priority": 2,
          "conditions": [],
          "childNode": {
            "type": "approval",
            "id": "default-approve",
            "displayName": "默认审批",
            "assignment": { "type": "starter-dept-manager" },
            "examineMode": "or-sign"
          }
        }
      ],
      "childNode": {
        "type": "approval",
        "id": "vp-approve",
        "displayName": "VP终审",
        "assignment": { "type": "role", "roleIds": ["vp"] },
        "selfApprove": { "strategy": "skip" },
        "timeout": {
          "duration": "48h",
          "action": "auto-reject"
        },
        "childNode": {
          "type": "end"
        }
      }
    }
  }
}
```

### 13.3 顺签 + 多级主管

```json
{
  "processName": "leave-request",
  "displayName": "请假审批",
  "nodeConfig": {
    "type": "start",
    "displayName": "发起人",
    "childNode": {
      "type": "approval",
      "id": "sequential-approve",
      "displayName": "逐级审批",
      "assignment": {
        "actors": [
          { "type": "starter-manager", "level": 1 },
          { "type": "starter-manager", "level": 2 },
          { "type": "starter-manager", "level": 3 }
        ]
      },
      "examineMode": "sequential",
      "childNode": {
        "type": "end"
      }
    }
  }
}
```

### 13.4 发起人自选审批人

```json
{
  "processName": "custom-approval",
  "displayName": "自定义审批",
  "nodeConfig": {
    "type": "start",
    "displayName": "发起人",
    "childNode": {
      "type": "approval",
      "id": "user-selected",
      "displayName": "指定审批人",
      "assignment": {
        "type": "self-select",
        "selection": "multiple"
      },
      "examineMode": "countersign",
      "childNode": {
        "type": "end"
      }
    }
  }
}
```

### 13.5 子流程调用 + 超时

```json
{
  "processName": "project-approval",
  "displayName": "项目审批",
  "nodeConfig": {
    "type": "start",
    "displayName": "发起人",
    "childNode": {
      "type": "approval",
      "id": "manager-approve",
      "displayName": "项目经理审批",
      "assignment": { "type": "starter-manager", "level": 1 },
      "timeout": {
        "duration": "24h",
        "action": "auto-reject",
        "remindBefore": "4h"
      },
      "childNode": {
        "type": "subprocess",
        "id": "tech-review",
        "displayName": "技术评审",
        "subprocess": {
          "processName": "tech-review",
          "version": "1",
          "async": false,
          "args": {
            "projectName": "${wfVars.projectName}",
            "budget": "${wfVars.budget}"
          },
          "outputs": {
            "reviewResult": "wfVars.reviewResult",
            "reviewer": "wfVars.reviewer"
          }
        },
        "childNode": {
          "type": "approval",
          "id": "vp-approve",
          "displayName": "VP终审",
          "assignment": { "type": "role", "roleIds": ["vp"] },
          "childNode": {
            "type": "end"
          }
        }
      }
    }
  }
}
```

---

## 十四、与现有 DingTalk 格式的差异

| 维度 | 现有 DingTalk JSON | 本设计 DingFlow JSON |
|------|-------------------|---------------------|
| 节点类型 | 数字枚举（0/1/2/3/4） | **字符串枚举**（`"start"`/`"approval"`/`"cc"`/`"router"`/`"subprocess"`/`"end"`） |
| 审批人类型 | 数字枚举（`assigneeType`: 1/2/3/4/7） | **字符串枚举**（`type`: `"user"`/`"role"`/`"dept"`/...） |
| 协作模式 | 数字枚举（`multiInstanceApprovalType`: 0/1/2） | **字符串枚举**（`examineMode`: `"single"`/`"sequential"`/`"countersign"`/`"or-sign"`/`"vote"`） |
| 票签 | 不支持 | ✅ `voteConfig`（weight/percent/count） |
| 子流程 | 不支持 | ✅ `subprocess` 节点（args/outputs/async） |
| 超时 | 无 | ✅ `timeout`（duration/action/remindBefore） |
| 条件运算符 | 数字枚举（`operator`: 3/4/20/...） | **字符串枚举**（`">"`/`"<="`/`"=="`/`"in"`/...） |
| 驳回策略 | 无显式配置 | ✅ `rejectConfig`（to-previous/to-initiator/to-specified/terminate） |
| 审批人=发起人 | `flowNodeSelfAuditorType` 数字枚举 | **字符串枚举**（`selfApprove.strategy`: `"self-approve"`/`"skip"`/`"to-supervisor"`/`"to-dept-manager"`） |
| 表单字段权限 | `formAuths`（每节点重复） | `formAuths`（结构相同，转换器可做全局去重优化） |
| 表单定义 | `flowWidgets` | `form.fields`（简化，去掉钉钉特有字段） |
| 钉钉特有字段 | `rid`/`workFlowDef`/`flowPermission`/`flowDefJson` 等 | 移除 |

---

## 十五、与 FlowLong 格式对比

### 15.1 功能覆盖对比

| 维度 | FlowLong JSON | 本设计 DingFlow JSON | 差异说明 |
|------|-------------|---------------------|---------|
| **节点类型** | 13 种数字枚举（major/approval/cc/conditionNode/conditionBranch/parallelBranch/inclusiveBranch/callProcess/timer/trigger/autoPass/autoReject/routeBranch） | 6 种字符串枚举（start/approval/cc/router/subprocess/end） | DingFlow 砍掉 timer/trigger/autoPass/autoReject（nop-wf 用其他方式表达），砍掉 parallelBranch/inclusiveBranch（审批场景不需要） |
| **协作模式** | `examineMode` 数字（1=sort/2=countersign/3=orSign/4=voteSign） + `passWeight` | `examineMode` 字符串 + `voteConfig`（passType=weight/percent/count） | DingFlow 的票签更灵活：支持按权重/比例/人数三种判定，FlowLong 仅按权重 |
| **子流程** | `callProcess` 字段 + `callAsync` 布尔 | `subprocess` 节点 + `async`/`args`/`outputs` | DingFlow 更完整：支持参数传递 schema 和输出回传映射，FlowLong 仅传 `businessKey` |
| **超时** | `term`（小时）+ `termAuto`（布尔）+ `termMode`（0=auto-pass/1=auto-reject） | `timeout.duration`（`"48h"`/`"2d"`）+ `action` + `remindBefore` | DingFlow 用 duration 字符串，支持 m/h/d/w 单位、提前提醒 |
| **驳回** | `rejectStrategy` 数字（1~5）+ `rejectStart` | `rejectConfig.strategy` 字符串 + `targetStep` | 功能等价。FlowLong 多一个 `rejectStart`（驳回后重审策略），DingFlow 通过转换器默认行为覆盖 |
| **审批人=发起人** | `approveSelf` 数字（0~3） | `selfApprove.strategy` 字符串 + `level` | 功能等价。DingFlow 额外支持 `level` 多级上溯 |
| **审批人设置** | `setType` 数字（1~8） + `nodeAssigneeList` | `assignment.type` 字符串 + `actors[]` | 功能等价。DingFlow 支持多 actor 组合（actors 数组），FlowLong 单 actor |
| **条件表达式** | `conditionList = [[Expression]]` 外 OR 内 AND + SpEL | `conditions` / `conditionGroups` 结构化 + 字符串 operator | FlowLong 支持 SpEL（更强但需解析器）；DingFlow 用结构化 operator（更安全但表达力稍弱）。需要复杂逻辑时由转换器生成 XLang |
| **权重** | `NodeAssignee.weight` | actor 级 `weight` | 功能等价 |
| **并行/包容网关** | `parallelNodes[]` / `inclusiveNodes[]` | 不支持 | DingFlow 不覆盖（审批场景不需要并行分发） |
| **定时器节点** | `TaskType.timer(6)` | 不支持 | DingFlow 用 `timeout` + 外部调度器替代 |
| **触发器节点** | `TaskType.trigger(7)` | 不支持 | DingFlow 不覆盖（nop-wf 通过 action source + listener 实现） |
| **自动通过/拒绝节点** | `autoPass(30)` / `autoReject(31)` | 不支持 | DingFlow 用 `selfApprove.strategy="skip"` + `<when>` 条件等价 |
| **路由跳转** | `routeBranch(23)` | 不支持 | DingFlow 用 `router` + `branches` 的条件路由等价 |

### 15.2 设计哲学差异

| 维度 | FlowLong | DingFlow |
|------|----------|----------|
| **目标场景** | 通用工作流引擎（审批 + 定时 + 并行 + 子流程 + AI） | **纯审批流**（审批 + 抄送 + 条件路由 + 子流程） |
| **类型系统** | 数字枚举（`type: 0`/`examineMode: 2`） | **字符串枚举**（`type: "approval"`/`examineMode: "countersign"`） |
| **可扩展性** | 扩展需改枚举或用 `extendConfig` | 扩展只需新增字符串值 |
| **表达式** | SpEL 字符串 | 结构化 field/operator/value |
| **子流程参数** | 弱（仅 `businessKey`） | 强（`args` + `outputs` schema 映射） |
| **JSON 体积** | 较大（13 种节点类型 + 多种分支容器） | 精简（6 种节点类型 + 单一分支结构） |

### 15.3 FlowLong 有而 DingFlow 刻意不覆盖的能力

以下能力**不是遗漏**，而是**设计选择**——nop-wf 通过其他机制覆盖：

| FlowLong 能力 | DingFlow 不支持的原因 | nop-wf 替代方案 |
|-------------|---------------------|----------------|
| parallelBranch（并行网关） | 审批场景不需要多人并行走不同分支 | 如需并行分发，直接在 XWF 中用 `splitType="and"` |
| inclusiveBranch（包容网关） | 同上 | `splitType="and"` + 多个 `<when>` |
| timer 节点 | 定时触发是调度器职责，不是审批节点 | `timeout` 配置 + `nop-wf-scheduler` 模块 |
| trigger 节点 | 事件驱动由 nop-wf 的 signal 机制处理 | `<subscribes>` + signal |
| autoPass/autoReject 节点 | 自动决策通过条件表达式实现 | `selfApprove.strategy="skip"` + `<when>` 条件 |
| AI 审批节点 | `extensions-design.md` §四 明确不新增 step 类型 | action `<source>` + `wf-ai.xlib` 标签库 |
| 节点模型动态加减签 | `extensions-design.md` §八 明确拒绝运行时改模型 | 预定义可选步骤 + 条件路由 |

---

## 十六、设计约束

1. **所有枚举值必须是字符串**。禁止使用数字枚举。这是可扩展性的基础。
2. **节点类型限定 6 种**（start/approval/cc/router/subprocess/end）。不引入通用工作流概念（parallel/inclusive/timer/trigger）。
3. **条件表达式使用结构化 field/operator/value**。不使用 SpEL 或其他脚本语言字符串。需要复杂逻辑时，由转换器生成 XLang `<when>` 标签。
4. **router 的 childNode 是所有分支的汇聚后续**。空 branches 或所有条件不匹配时走 priority 最低的默认分支（`conditions: []`）。
5. **转换器负责自动 join 生成**。JSON 中不显式声明 join 节点。
6. **子流程参数和输出使用 XLang 表达式**。`args` 的值支持 `${...}` 表达式引用父流程变量。

---

## References

- 审批流核心模式：`ai-dev/design/nop-wf/approval-flow-design.md`
- 扩展机制设计：`ai-dev/design/nop-wf/extensions-design.md`
- 格式对比分析：`ai-dev/analysis/2026-07-02-flowlong-warmflow-dingflow-json-format-comparison.md`
- XDef Schema：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef`
- execGroupType 枚举：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/model/WfExecGroupType.java`
- Actor 标签库：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/wf-actor.xlib`
- assignment selection 枚举：`nop-wf/nop-wf-api/src/main/java/io/nop/wf/api/actor/WfAssignmentSelection.java`
- 基础工作流模板：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf`
- 现有 DingTalk JSON（参考）：`nop-wf/nop-wf-service/src/test/resources/_vfs/nop/wf/beeflow/*.json`
- 转换器骨架：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/dingflow-tran/impl_GenWorkflow.xpl`
