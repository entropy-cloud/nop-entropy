# 工作流配置参考

## XWF 文件结构

工作流定义文件使用 `.xwf` 后缀，根标签为 `<workflow>`。典型的文件骨架：

```xml
<workflow x:extends="/nop/wf/base/oa.xwf"
          x:schema="/nop/schema/wf/wf.xdef" xmlns:x="/nop/schema/xdsl.xdef"
          wfName="my-flow" wfVersion="1" displayName="我的流程">
    <start startStepName="first-step"/>
    <end/>

    <steps>
        <step name="first-step" displayName="第一步">
            <assignment>...</assignment>
            <transition>...</transition>
        </step>
    </steps>

    <actions>
        <action name="agree" common="true" local="true">
            <transition appState="agree"/>
        </action>
    </actions>
</workflow>
```

| 元素 | 用途 |
|------|------|
| `<start>` | 定义流程入口，`startStepName` 指定首个步骤 |
| `<end>` | 流程结束标记 |
| `<steps>` | 步骤定义列表 |
| `<actions>` | 动作定义列表（可被步骤引用） |

---

## 步骤 (`<step>`)

步骤是工作流的基本执行单元。

### 基本属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | string | 必填 | 步骤唯一标识 |
| `displayName` | string | 必填 | 步骤显示名称 |
| `specialType` | string | — | 特殊类型：`cc`（抄送）、`co-sign`（会签）等 |
| `execGroupType` | enum | `none` | 执行分组类型：`none`、`and-group`、`or-group`、`seq-group`、`vote-group` |
| `passWeight` | int | — | 投票会签通过权重（vote-group 时使用） |
| `passPercent` | double | — | 投票会签通过百分比（vote-group 时使用） |
| `allowReject` | boolean | false | 是否允许驳回 |
| `allowWithdraw` | boolean | false | 是否允许撤回 |
| `internal` | boolean | false | 是否内部步骤（前端不显示） |
| `joinType` | enum | — | 汇聚类型：`and`（全部到达后激活）、`or`（任一到达即激活） |

### 步骤类型

| 标签 | 语义 |
|------|------|
| `<step>` | 普通步骤，需要 actor 处理 |
| `<join>` | 汇聚步骤，等待前置步骤到达后激活 |
| `<flow>` | 子流程步骤，启动子工作流实例 |

### 执行分组 (`execGroupType`)

当 transition 到某步骤时，如果该步骤配置了 `execGroupType`，则每个 actor 会生成一个独立的步骤实例，共享同一个 `execGroup` ID。

| 类型 | 中文 | 语义 |
|------|------|------|
| `none` | 无分组 | 只生成一个步骤实例 |
| `and-group` | 并行会签 | 所有实例都完成才通过 |
| `or-group` | 或签 | 任一实例完成即通过，其余跳过 |
| `seq-group` | 串签 | 按 `execOrder` 顺序依次激活 |
| `vote-group` | 投票会签 | 按权重/百分比决定是否通过 |

### 投票会签扩展槽位

`vote-group` 除了默认的 `passWeight` / `passPercent` 规则外，还支持通过步骤槽位自定义完成/拒绝判定：

```xml
<step name="review" execGroupType="vote-group">
    <check-exec-group-complete>
        <wf-vote:passCount count="3"/>
    </check-exec-group-complete>
    <check-exec-group-reject>
        <wf-vote:rejectCount count="2"/>
    </check-exec-group-reject>
</step>
```

规则：

- 配置了 `<check-exec-group-complete>` 时，优先使用槽位判定，忽略 `passWeight` / `passPercent`
- 配置了 `<check-exec-group-reject>` 时，优先使用槽位判定拒绝条件
- 常用标签库位于 `/nop/wf/xlib/wf-vote.xlib`

注意：`<check-complete>` 虽然在 schema 中存在，但当前运行时未接线，不要把它当成可用的异步完成机制；异步唤醒应使用 `waitSignals` + `signalWf`

---

## 参与者分配 (`<assignment>`)

```xml
<step name="approval">
    <assignment selection="single" mustInAssignment="true">
        <actors>
            <actor actorModelId="mgr" actorType="role" actorId="manager"
                   selectUser="true" voteWeight="1"
                   wf:permissions="transfer,delegate" wf:upLevel="1"/>
        </actors>
        <defaultOwnerExpr>...</defaultOwnerExpr>
    </assignment>
</step>
```

### assignment 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `selection` | enum | `auto` | 参与者选择策略：`auto`（自动分配，无需用户选择）、`single`（必须从候选人中选一个）、`multiple`（从候选人中选多个） |
| `mustInAssignment` | boolean | true | 是否限制在前台传入的 actor 必须在 assignment 范围内 |
| `ignoreNoAssign` | boolean | false | 是否允许不选择 actor |
| `useManagerWhenNoAssign` | boolean | true | 未选择时是否自动使用上级 |

### selection 策略说明

`selection` 控制的是**前端是否需要弹出人员选择框**：

- `auto`（默认）：引擎自动使用所有配置的 actor，不需要前端传入 `selectedActors`
- `single`：前端必须传入一个 `selectedActor`，引擎在候选人列表中匹配
- `multiple`：前端可以传入多个 `selectedActors`

### actor 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `actorModelId` | string | 必填 | actor 唯一标识，用于在同一步骤的多个 actor 间区分。也作为 candidates 列表的 key |
| `actorType` | string | 必填 | actor 类型：`user`、`role`、`dept`、`wf-actor:Starter` 等 |
| `actorId` | string | — | actor 标识值（user ID、role ID 等） |
| `selectUser` | boolean | true | 是否将 actor 解析为用户级别（true = 展开为具体用户，false = 保留 group actor 本身） |
| `voteWeight` | int | 1 | 投票会签时的权重 |
| `assignForUser` | boolean | false | 是否为 actor 中的每个用户生成独立的步骤实例 |
| `wf:upLevel` | int | — | 向上找几级（用于 `StarterManager`/`StarterDeptManager`） |
| `wf:permissions` | csv-set | — | 该 actor 在步骤上的操作权限，如 `transfer,delegate,add-sign` |

### selectUser 详解

`selectUser` 控制的是**actor 解析层级**，而非前端选择：

- `true`（默认）：将 role/dept/StarterManager 解析为其包含的具体用户。步骤的 actor 类型变为 `user`
- `false`：保留 role/dept 等 group actor 本身。步骤的 actor 类型不变

与 `assignment.selection` 的区别：
- `selectUser` → 决定"谁是可以执行的人"（user 还是 group）
- `selection` → 决定"是否允许前端从候选人中挑选"

### actorModelId 详解

`actorModelId` 是 `<actor>` 节点的**唯一标识符**（`xdef:key-attr`），在同一 `<assignment>` 内不能重复。它的作用：

1. **候选取重**：当 engine 构建 candidates 列表时，用 `actorModelId` 区分不同的 actor 定义
2. **步骤关联**：创建步骤实例时记录 `actorModelId`，用于追溯该步骤实例对应哪个 actor 定义
3. **权限校验**：`findCandidate()` 匹配时，通过 `actorModelId` 确定候选人的 `voteWeight`、`assignForUser` 等属性

注意：`actorModelId` **不参与 actor 解析**——它只是标识符。实际的 actor 查找由 `actorType` + `actorId` 决定。

### 内置 actorType

| actorType | 语义 |
|-----------|------|
| `user` | 指定用户（需要 `actorId`） |
| `role` | 指定角色（需要 `actorId`） |
| `dept` | 指定部门（需要 `actorId`） |
| `wf-actor:Starter` | 流程发起人 |
| `wf-actor:CurrentCaller` | 当前调用者 |
| `wf-actor:StarterManager` | 发起人的第 N 级上级（`wf:upLevel` 控制级数） |
| `wf-actor:StarterDeptManager` | 发起人的第 N 级部门负责人 |
| `all` | 所有人（用户 start auth 检查） |

---

## 动作 (`<action>`)

动作是用户操作步骤的入口。

### action 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | string | 必填 | 动作标识 |
| `displayName` | string | 必填 | 动作显示名称 |
| `common` | boolean | false | 是否公共动作（自动添加到所有步骤，无需 `<ref-action>`） |
| `local` | boolean | false | 是否局部动作（不结束当前步骤） |
| `forReject` | boolean | false | 是否退回动作（触发退回逻辑 `doReject`） |
| `forWithdraw` | boolean | false | 是否撤回动作（触发撤回逻辑 `doWithdraw`） |
| `internal` | boolean | false | 是否内部动作（前端不显示） |
| `persist` | boolean | true | 是否记录 action 记录到数据库 |

### local=true 详解

`local="true"` 意味着该动作执行后**步骤不退出**（不执行 `doExitStep`）。适用于：

- `agree` 等仅修改状态的过渡动作（步骤存活等待其他条件触发 transition）
- `confirm` 等仅标记已读的确认动作（cc 步骤）

与非 local 动作的区别：

```
非 local（默认）:  action 执行 → doTransition → doExitStep(COMPLETED) → 步骤成为历史
local=true:        action 执行 → doTransition → 步骤保持 ACTIVE/WAITING
```

### common action 机制

`common="true"` 的动作自动对**所有步骤**可见，无需 `<ref-action>` 显式引用。权限检查时直接通过，不需要步骤级 action 存在。

非 common 的动作必须通过 `<ref-action>` 在步骤中显式引用才能在该步骤使用。

注意：如果子工作流通过 `x:extends` 继承基模板后在 `<actions>` 中定义了与基类同名的 action，则会覆盖基类的定义（`x:extends` 按 `name` key 合并）。但这不是"步骤覆盖基类"，而是全局 action 定义级别的覆盖。

### action 执行生命周期

```
checkAllowedAction (状态/权限/when条件)
  → checkActionAuth (自定义权限检查)
  → initArgs (参数初始化)
  → forReject? → doReject (退回逻辑)
  → forWithdraw? → doWithdraw (撤回逻辑)
  → runSource (XPL 执行)
  → saveActionRecord (持久化)
  → transition? → doTransition (迁移逻辑)
  → !local? → doExitStep (步骤退出，标记 COMPLETED/REJECTED)
  → checkEnd (检查工作流是否结束)
```

---

## 迁移/过渡 (`<transition>`)

transition 定义了 action 执行后的步骤流向。

### transition 子元素

| 子元素 | 说明 |
|--------|------|
| `<to-step stepName="xxx"/>` | 迁移到指定步骤 |
| `<to-end/>` | 结束工作流 |
| `<to-empty/>` | 空迁移（仅改变状态，不导航到任何步骤） |
| 无子元素（仅属性） | 仅改变 appState，不产生步骤导航 |

### transition 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `appState` | string | 设置当前步骤实例的 `appState` |
| `wfAppState` | string | 设置工作流实例的 `appState` |
| `bizEntityState` | string | 更新关联业务实体的状态字段 |
| `splitType` | enum | 分支类型：`and`（所有满足条件的分支都执行）、`or`（只执行第一个满足条件的分支） |

### 三态 transition

1. **含 `<to-step>`**：改变 appState + 创建目标步骤实例
2. **含 `<to-end>`**：改变 appState + 标记工作流结束
3. **仅 `appState`（无子元素）**：只改变当前步骤的 `appState`，**不产生步骤导航**。步骤是否退出由 action 的 `local` 属性决定

### 步骤级 transition

步骤上也可以直接定义 `<transition>`，用于在步骤的 `appState` 变化时自动触发。这通常配合 `local="true"` 的 action 使用：

```xml
<step name="approval">
    <transition onAppStates="agree">
        <to-step stepName="next"/>
    </transition>
</step>
```

`onAppStates` 表示当步骤的 `appState` 进入该值列表时触发此 transition。

### `<to-step>` 条件路由

`<to-step>` 支持 `<when>` 条件表达式，配合 `splitType` 实现条件分支：

```xml
<transition splitType="or">
    <to-step stepName="approve-a" order="1">
        <when>
            <le name="wfRt.bizEntity.amount" value="@:1000"/>
        </when>
    </to-step>
    <to-step stepName="approve-b" order="2">
        <!-- 默认分支 -->
    </to-step>
</transition>
```

| splitType | 行为 |
|-----------|------|
| `and` | 所有 `<when>` 条件满足的 `<to-step>` 都执行（并行分发） |
| `or` | 按 `order` 排序，第一个 `<when>` 条件满足的 `<to-step>` 执行，其余跳过（排他路由） |

---

## oa.xwf 基模板

`oa.xwf`（位于 `nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf`）是所有审批流示例的基模板。它通过 `x:extends` 机制提供：

它不是简单的 demo，而是一个基础抽象层：把常见 OA 审批语义外置为公共 action、步骤约束和标签库条件。很多其他引擎需要内置在节点类型或引擎分支里的逻辑，在 nop-wf 中先表现为模板和扩展属性。平台级扩展机制见 `../06-extensibility/platform-extensibility-mechanism.md`，workflow 案例见 `../06-extensibility/nop-wf-as-example.md`。

1. **公共标准 actions**（所有步骤自动可见）：

   | Action | 属性 | 行为 |
   |--------|------|------|
   | `agree` | common, local | 设置 appState=agree，触发 transition |
   | `disagree` | common | 设置 wfAppState=disagree，transition to-end |
   | `reject` | common, forReject, local | 触发退回逻辑 |
   | `confirm` | common, local | 设置 appState=confirm（仅 cc 步骤可用） |
   | `complete` | common, local | 设置 appState=complete |
   | `delegate` | common, local | 委派操作（仅 cc 步骤可用） |
   | `delegateReturn` | common, local | 委派返回（仅 cc 步骤可用） |

2. **`<when>` 标签库**（`oa.xlib`）：控制 action 是否允许执行。例如 `oa:WhenAllowAgree` 会检查 `specialType != 'cc'`，即抄送步骤不允许 agree。

    常见 `<when>` 标签：
    | 标签 | 允许条件 |
    |------|---------|
    | `oa:WhenAllowAgree` | `specialType != 'cc'` |
    | `oa:WhenAllowConfirm` | `specialType == 'cc'` |
    | `oa:WhenAllowReject` | `specialType != 'cc'` |
    | `oa:WhenAllowDelegate` | `specialType == 'cc'` |

3. **基础 start 步骤**：使用 `wf-actor:CurrentCaller` 作为发起人。

### 为什么 `oa.xwf` 重要

`oa.xwf` 体现了 nop-wf 的一个核心设计：

- 引擎只保留相对稳定的底层原语
- 领域语义优先沉淀到可继承模板
- 差异化规则优先通过 `<when>` 标签和扩展属性表达

因此，当你想扩展审批动作、抄送规则、默认 reject 逻辑时，优先先想“能不能扩 base `.xwf`”，而不是先改引擎。

---

## 步骤状态机

步骤实例的生命周期：

```
CREATED (0)
  → WAITING (20)     [等待前置条件/signals/join 就绪]
  → ACTIVATED (30)   [等待用户处理]
    → COMPLETED (40)   [正常完成]
    → REJECTED (90)    [退回]
    → WITHDRAWN (100)  [撤回]
    → CANCELLED (110)  [取消]
    → TRANSFERRED (120) [转交]
    → KILLED (70)      [强制终止]
    → SKIPPED (50)     [跳过]
    → EXPIRED (60)     [超时]
```

---

## 常见误区

### 1. selectUser ≠ 前端选择框

`selectUser="true"` 只决定 actor 是否展开为用户级别。前端是否需要弹出人员选择框由 `<assignment selection="single|multiple">` 控制。

### 2. forReject action 不需要 \<to-step\>

```xml
<!-- 错误：双重创建步骤实例 -->
<action name="reject" forReject="true" common="true">
    <transition appState="reject">
        <to-step stepName="submit"/> <!-- ← 不要加 -->
    </transition>
</action>

<!-- 正确：引擎的 doReject 会自动处理退回逻辑 -->
<action name="reject" forReject="true" common="true">
    <transition appState="reject"/>
</action>
```

`forReject="true"` 时，`doReject()` 会自动找到前驱步骤并创建新实例。如果同时加 `<to-step>`，会创建双份。

### 3. forWithdraw action 同样不需要 \<to-step\>

同上，`doWithdraw()` 已处理撤回逻辑。显式 `<to-step>` 会导致双重创建。

### 4. local=true 的作用范围

`local="true"` 只阻止 `doExitStep`（步骤不退出为历史），**不阻止** `doTransition`。即：
- action 的 `source` 会执行
- `<transition>` 会执行（状态改变、子步骤创建）
- **但步骤本身保持 active**，不会变成 completed/rejected

### 5. 不恰当的 local=true 会导致步骤不退出

如果在 reject 等应该退出步骤的 action 上误用 `local="true"`，会出现"当前步骤和新创建的前驱步骤同时 active"的问题。
