# FlowLong / Warm-Flow / DingTalk 工作流 JSON 格式深度对比分析

> Status: open
> Date: 2026-07-02
> Scope: 三套工作流 JSON 序列化格式的数据结构、表达能力、与 nop-wf XWF 模型的映射成本
> Conclusion: _(待定)_

## Context

- **要回答的问题**：FlowLong、Warm-Flow、DingTalk（现有 `beeflow/*.json`）三种 JSON 格式各自的优缺点是什么？nop-wf 的 dingflow 转换器应该选择/设计什么样的 JSON 格式？
- **涉及模块**：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/dingflow-tran/impl_GenWorkflow.xpl`（转换器）、`nop-wf/nop-wf-service/src/test/resources/_vfs/nop/wf/beeflow/*.json`（输入数据）
- **约束**：JSON 格式必须能完整表达 nop-wf 的 DAG 模型（steps + transitions + join + conditions + execGroup + actors）；转换器实现受 `extensions-design.md` 的 P1/P3 原则约束（不侵蚀引擎核心、模型运行时不可变）
- **已有分析**：`ai-dev/analysis/2026-07-02-flowlong-warmflow-vs-nop-wf-comparison.md` 覆盖引擎能力对比，本文件仅聚焦 JSON 格式设计
- **关联设计文档**：
  - `ai-dev/design/nop-wf/approval-flow-design.md` — nop-wf 审批流核心模式的 XWF 模型表达（步骤类型、execGroup、splitType、action 系统、actor 模型）。转换器生成的 XWF 必须符合此文档的契约。
  - `ai-dev/design/nop-wf/extensions-design.md` — nop-wf 扩展机制设计原则（P1 引擎核心不可侵蚀、P2 外置模块优先、P3 模型即代码运行时不可变）。直接影响转换器的实现架构。

---

## 一、三个项目 JSON 格式总览

### 1.1 FlowLong：纯递归树（单根 linked-list + 分支容器）

```
ProcessModel
 └── nodeConfig (NodeModel)       ← 根节点，type=0（发起人）
      ├── type                    ← TaskType 枚举值
      ├── nodeName/nodeKey        ← 名称/标识
      ├── nodeAssigneeList[]      ← 办理人
      ├── examineMode             ← 会签/或签/顺签/票签
      ├── setType                 ← 办理人设置方式
      ├── childNode (NodeModel)   ← 顺序下一节点（单链表指针）
      │    ├── conditionNodes[]   ← 排他网关分支（type=4 时有效）
      │    ├── parallelNodes[]    ← 并行网关分支（type=8 时有效）
      │    ├── inclusiveNodes[]   ← 包容网关分支（type=9 时有效）
      │    └── childNode          ← 继续链式
      └── parentNode (transient)  ← 反指父节点（不出现在 JSON 中）
```

**核心特征**：
- 单一根节点 `nodeConfig`，通过 `childNode` 形成链表式顺序
- 分支通过 `conditionNodes[]/parallelNodes[]/inclusiveNodes[]/routeNodes[]` 容器表达
- 条件分支的每个 `ConditionNode` 携带 `conditionList`（外 OR 内 AND）+ 自己的 `childNode` 子树
- `parentNode` 是运行时反指，序列化前清除

**JSON 形态**（简化）：
```json
{
  "key": "simpleProcess",
  "name": "Simple Process",
  "nodeConfig": {
    "nodeName": "发起人", "nodeKey": "k001", "type": 0,
    "childNode": {
      "nodeName": "条件路由", "nodeKey": "k002", "type": 4,
      "conditionNodes": [
        {
          "nodeName": "条件1", "type": 3, "priorityLevel": 1,
          "conditionList": [[{ "field": "day", "operator": ">", "value": "7" }]],
          "childNode": {
            "nodeName": "经理审批", "nodeKey": "k004", "type": 1,
            "setType": 1, "examineMode": 2,
            "nodeAssigneeList": [{ "id": "test001", "name": "经理" }]
          }
        },
        {
          "nodeName": "条件2", "type": 3, "priorityLevel": 2,
          "conditionList": [],  // 默认分支
          "childNode": { /* ... */ }
        }
      ],
      "childNode": {
        "nodeName": "抄送", "nodeKey": "k015", "type": 2
      }
    }
  }
}
```

### 1.2 Warm-Flow：分层图（flat nodeList[] + 节点内嵌 skipList[]）

```
DefJson
 ├── flowName/flowCode
 ├── nodeList (NodeJson[])
 │    ├── nodeCode                ← 唯一标识
 │    ├── nodeType                ← 0=start/1=between/2=end/3=serial/4=parallel/5=inclusive
 │    ├── permissionFlag          ← "role:manager@@user:123"
 │    ├── nodeRatio               ← "0"(或签)/"100"(会签)/"50"(50%票签)
 │    ├── skipList (SkipJson[])   ← 从本节点出发的边
 │    │    ├── nowNodeCode        ← 来源（= 父节点 nodeCode）
 │    │    ├── nextNodeCode       ← 目标
 │    │    ├── skipType           ← "PASS"/"REJECT"/"NONE"
 │    │    └── skipCondition      ← "eq@@amount|5000"
 │    └── coordinate              ← 界面坐标
 └── (无全局 edge 列表)
```

**核心特征**：
- `nodeList` 是扁平的，所有节点在一层
- 边不独立存在，而是归属于每个 `NodeJson.skipList`
- 持久化时通过 `DefJson.copyCombine()` 展平为 `List<Node>` + `List<Skip>` 双表
- 条件使用 `{operator}@@{variable}|{value}` 字符串表达式
- 办理人使用 `permissionFlag` 字符串（`@@` 分隔多值）

**JSON 形态**：
```json
{
  "flowName": "请假审批", "flowCode": "leave",
  "nodeList": [
    {
      "nodeCode": "start", "nodeName": "开始", "nodeType": 0,
      "skipList": [{
        "nowNodeCode": "start", "nextNodeCode": "submit",
        "skipType": "PASS", "skipCondition": null
      }]
    },
    {
      "nodeCode": "submit", "nodeName": "部门审批", "nodeType": 1,
      "permissionFlag": "role:manager",
      "nodeRatio": "100",
      "skipList": [{
        "nowNodeCode": "submit", "nextNodeCode": "end",
        "skipType": "PASS", "skipCondition": "gt@@amount|5000"
      }]
    },
    {
      "nodeCode": "end", "nodeName": "结束", "nodeType": 2,
      "skipList": []
    }
  ]
}
```

### 1.3 DingTalk（现有 beeflow JSON）：树形（DingTalk API 原生格式）

```
{
  nodeConfig (root)
   ├── name                     ← 节点名
   ├── type                     ← 0=发起/1=审批/2=抄送/3=条件/4=路由
   ├── childNode                ← 顺序下一节点
   │    ├── type                   
   │    ├── assignees[]            ← 办理人
   │    │    ├── assigneeType      ← 1=上级/2=部门主管/3=角色/4=指定人/7=部门
   │    │    ├── assignees[]       ← 指定人列表
   │    │    ├── roles[]           ← 角色列表
   │    │    ├── layer/layerType   ← 层级参数
   │    │    └── rid               ← 配置 ID
   │    ├── multiInstanceApprovalType ← 0=依次/1=会签/2=或签
   │    ├── approvalType           ← 0=人工/1=自动通过/2=自动拒绝
   │    ├── conditionNodes[]       ← 路由节点（type=4）下的条件分支
   │    │    ├── name/type/priorityLevel
   │    │    ├── conditionGroups[]  ← 条件组
   │    │    │    ├── conditions[]
   │    │    │    │    ├── varName    ← 字段名
   │    │    │    │    ├── operator   ← 操作符（数字枚举）
   │    │    │    │    └── val        ← 值
   │    │    │    └── id
   │    │    └── childNode           ← 命中后的执行节点
   │    ├── ccs[]                  ← 抄送人（type=2）
   │    └── formAuths[]            ← 表单字段权限
   └── flowWidgets[]               ← 表单字段定义
       └── workFlowDef             ← 流程元信息
```

**核心特征**：
- 钉钉 API 原生格式，递归树结构
- 节点类型用数字枚举（0/1/2/3/4）
- 条件使用 `conditionGroups[].conditions[]` 两层结构（组内 AND，组间 OR）
- 审批人使用 `assignees[]` 数组 + `assigneeType` 区分类型
- 包含大量钉钉特有字段（`formAuths`, `flowWidgets`, `workFlowDef.flowAdmins` 等）

---

## 二、多维对比

### 2.1 结构范式

| 维度 | FlowLong | Warm-Flow | DingTalk |
|------|----------|-----------|----------|
| **结构类型** | 递归树（单链表+分支容器） | 分层图（flat nodes + 内嵌 edges） | 递归树（单链表+条件分支） |
| **node 关系** | `childNode` 指针链 | `nodeList[]` + 每个 node 的 `skipList[]` 引用 | `childNode` 指针链 |
| **edge 存储** | 隐式（childNode 指针） | 显式（skipList, 依附于 source node） | 隐式（childNode 指针） |
| **gateway 表达** | 节点类型 + 分支容器字段 | 节点类型（3/4/5）+ skip 条件遍历 | 路由类型节点（type=4）+ conditionNodes |
| **join 表达** | 隐式（所有分支跑完回到 childNode） | gateway 节点分流后自带汇聚逻辑 | 隐式（树结构天然汇聚） |
| **数据冗余度** | 低（无冗余，parentNode transient） | 低中（nodeCode 在 skip 中重复） | 中（formAuths 在每层重复） |
| **自包含性** | ⚠️ `parentNode` 是运行时补的 | ✅ 完全自包含 | ✅ 完全自包含 |

### 2.2 节点类型编码

| FlowLong | Warm-Flow | DingTalk | nop-wf 概念 |
|----------|-----------|----------|-------------|
| 0 major（发起人） | 0 start | 0 发起人 | start step |
| 1 approval | 1 between | 1 审批人 | step（默认） |
| 2 cc | — | 2 抄送人 | step specialType="cc" |
| 3 conditionNode（分支内） | — | 3 条件 | `to-step <when>` |
| 4 conditionBranch（排他网关） | 3 serial | 4 路由 | transition splitType="or" |
| — | 4 parallel | — | splitType="and" |
| — | 5 inclusive | — | splitType="and" + `<when>` |
| 8 parallelBranch | — | — | splitType="and" |
| 9 inclusiveBranch | — | — | splitType="and" 多个 `<when>` |
| 5 callProcess | — | — | `<flow>` step |
| 6 timer | — | — | `due-time-expr` + `dueAction` |
| — | 2 end | — | `<end/>` |
| -1 end | — | — | `<end/>` |
| 23 routeBranch | — | — | `transitTo()` |

**判断**：Warm-Flow 的 6 种类型最精简且与 BPMN 网关概念对齐；FlowLong 13 种最全但存在语义冗余（conditionNode 3 和 conditionBranch 4 可合并）；DingTalk 5 种最贴近审批场景实际（没有并行/包容网关，因为钉钉场景不需要）。

### 2.3 条件表达式

| 维度 | FlowLong | Warm-Flow | DingTalk |
|------|----------|-----------|----------|
| **表达式格式** | 嵌套 List：`conditionList = [[Expression]]` 外 OR 内 AND | 字符串：`eq@@amount|5000` | `conditionGroups[].conditions[]` 组内 AND 组间 OR |
| **表达力** | 高（任意组合 OR/AND） | 中（单层条件 + 多个 @@ 运算符） | 高（任意组合 OR/AND + 钉钉特有函数 `fx.has0()`） |
| **变量引用** | `field` 名字段名 | `{variable}\|{value}` 字符串 | `varName` 字段名 |
| **操作符** | 字符串运算符（`>`/`==`/`<`/`>=`/`<=`/`!=`） | 8 个前缀运算符（`eq`/`ne`/`gt`/`ge`/`lt`/`le`/`like`/`notLike`） | 数字枚举（20+ 个值，含 `fx.has0()` 等钉钉专属） |
| **默认分支** | 空 `conditionList = []` | 无条件（`skipCondition = null`） | 无 conditionGroups（`conditionGroups: []`） |
| **SpEL 支持** | ✅ SpEL 完整 | ✅ `spel@@` 前缀 | ❌ 无（固定表达式） |
| **可读性** | 🟡 嵌套 JSON 可读，但结构深 | ✅ 字符串紧凑 | 🟡 条件分组结构深 |

**判断**：Warm-Flow 的 `op@@var|value` 字符串格式最简洁但表达能力有限（无法表达复杂 OR/AND 组合）；FlowLong 和 DingTalk 的嵌套结构表达能力更强但 JSON 体积更大。

### 2.4 办理人（Actor）表达

| 维度 | FlowLong | Warm-Flow | DingTalk |
|------|----------|-----------|----------|
| **格式** | `nodeAssigneeList[]` + `setType` | `permissionFlag` 字符串 | `assignees[]` + `assigneeType` |
| **指定用户** | setType=1, id=userId | `user:{userId}` | assigneeType=4, assignees=[userId] |
| **角色** | setType=3, id=roleId | `role:{roleId}` | assigneeType=3, roles=[roleId] |
| **主管** | setType=2, `examineLevel` | `${handler}` + SpEL | assigneeType=1 (上级) / 2 (部门主管) |
| **发起人自选** | setType=4, `selectMode` | `warmFlowInitiator` 占位符 | — |
| **发起人自己** | setType=5 | `warmFlowInitiator` | — |
| **部门** | setType=7, id=deptId | `dept:{deptId}` | assigneeType=7 |
| **多级主管** | setType=6, `directorLevel` | SpEL 自实现 | layer/layerType 多层 |
| **候选人模式** | setType=8 + `nodeCandidate` | `permissionFlag` 无区分 | — |
| **权重** | `weight` 字段（票签） | `nodeRatio` 字符串隐含 | — |
| **可扩展性** | 枚举扩展 | 字符串前缀 + SPI 可插拔 | 枚举扩展 |

**判断**：Warm-Flow 的字符串 `permissionFlag` 设计最灵活（任意前缀可注册 SPI）；FlowLong 的枚举设计最严谨但扩展需改枚举；DingTalk 的枚举局限最大（钉钉内置类型不可扩展）。

### 2.5 协作模式（会签/或签/顺签/票签）

| 维度 | FlowLong | Warm-Flow | DingTalk |
|------|----------|-----------|----------|
| **字段** | `examineMode` 枚举 + `passWeight` | `nodeRatio` 字符串 | `multiInstanceApprovalType` |
| **会签** | examineMode=2 | nodeRatio="100" | multiInstanceApprovalType=1 |
| **或签** | examineMode=3 | nodeRatio="0" | multiInstanceApprovalType=2 |
| **顺签** | examineMode=1（默认） | "100@@sequence" | multiInstanceApprovalType=0 |
| **票签（按比例）** | examineMode=4 + passWeight | "50"（0<ratio<100） | — |
| **票签（按通过数）** | — | "passCount3" | — |
| **票签（按拒绝数）** | — | "rejectCount2" | — |
| **可扩展性** | 枚举扩展 | 字符串前缀 + 策略 SPI | 数字枚举 |

**判断**：Warm-Flow 的 `nodeRatio` 字符串设计是三者中最灵活的——它不仅编码比例，还编码顺序、拒绝数、通过数，且可通过 SPI 注册新策略前缀。FlowLong 的枚举 + `passWeight` 次之。DingTalk 仅三种模式，最小。

### 2.6 子流程

| 维度 | FlowLong | Warm-Flow | DingTalk |
|------|----------|-----------|----------|
| **格式** | `callProcess` 字段 + `callAsync` | 不支持 | 不支持 |
| **引用方式** | `processId:processName` 或 `processKey` | — | — |
| **同步/异步** | `callAsync` boolean | — | — |
| **参数传递** | `businessKey`（弱） | — | — |

**判断**：只有 FlowLong 支持子流程。Warm-Flow 和 DingTalk 都无此能力。

### 2.7 表单与权限

| 维度 | FlowLong | Warm-Flow | DingTalk |
|------|----------|-----------|----------|
| **表单引用** | `actionUrl` / `instanceUrl` | `formPath` + `formCustom` | `flowWidgets[]` 内联定义 |
| **字段权限** | `extendConfig.formPermissions` | 无 | `formAuths[]`（readable/editable） |
| **表单类型** | 外挂 URL | 外挂或内置 JSON | 内联 widget 定义 |
| **流程图坐标** | 无 | `coordinate` 字段 | 无 |

**判断**：DingTalk 的完整表单字段定义和字段级权限是独有的，但也使 JSON 体积膨胀（`salary-adjustment.json` 742 行，表单字段在每层 node 的 `formAuths` 中重复出现）。

---

## 三、与 nop-wf XWF 模型的映射复杂度

nop-wf 的 `wf.xdef` 模型是 **DAG（steps + transitions + join + actions）**，映射成本取决于输入格式的差距。

### 3.1 FlowLong 树 → nop-wf DAG 映射

```
FlowLong 树                      nop-wf DAG
──────────                      ─────────
childNode 链 →   →   →   →      steps[] 线性 + transitions
conditionNodes →   →   →   →    transition splitType="or" + <to-step when=...>
parallelNodes  →   →   →   →    transition splitType="and"
conditionNode.childNode →   →   条件分支内的步骤子链
conditionNode 的 childNode 后   join 步骤（汇聚所有分支）
→ ❌ 树转 DAG 需要展平。需要将递归树遍历为 DAG 节点列表，同时正确构建汇聚语义

映射步骤：
1. DFS 遍历整个树，按出现顺序分配 step name
2. 对每个条件分支组的末尾自动插入 join step
3. 将 conditionNodes/parallelNodes 转换为 splitType="or"/"and" 的 transition
4. conditionList → <when> 标签嵌套（/nop/schema/wf/wf.xdef 条件格式）
5. 子流程 callProcess → <flow> step type
```

**复杂度：高**。需要：
- 树→DAG 展平算法
- 自动 join 点插入
- 条件表达式翻译（SpEL → XLang `<when>` 标签）

### 3.2 Warm-Flow 图 → nop-wf DAG 映射

```
Warm-Flow 图                      nop-wf DAG
────────────                      ─────────
nodeList[]         →   →   →    steps[]
NodeJson.skipList[]  →   →   →  transitions（每个 step 的 <transition>）
skipType="PASS"    →   →   →    onAppStates="agree"
skipType="REJECT"  →   →   →    onAppStates="reject"
skipCondition      →   →   →    <transition onAppStates="agree"><when>...</when></transition>
gateway 节点        →   →   →   splitType="or/and" 的 transition
permissionFlag     →   →   →    <actor actorType="user/role/dept">
nodeRatio          →   →   →    execGroupType="and-group/or-group/seq-group/vote-group"
```

**复杂度：中**。优势：
- nodeList 已经是 flat 的，不需要树→DAG 展平
- 每个节点的 skipList 直接映射到 `<transition>`
- 条件字符串 `eq@@amount|5000` 需翻译为 XLang 表达式
- permissionFlag 字符串需映射到 nop-wf 的 `wf-actor:xxx` 标签

### 3.3 DingTalk 树 → nop-wf DAG 映射

```
DingTalk 树                       nop-wf DAG
───────────                      ─────────
childNode 链     →   →   →     steps[] 线性 + transitions
conditionNodes   →   →   →     transition splitType="or" + <to-step when=...>
assignees[]      →   →   →     <actor actorType=...>
multiInstanceApprovalType →     execGroupType
formAuths        →   →   →     <wf:formAuth>
conditionGroups  →   →   →     <when> 嵌套条件
```

**复杂度：高**（同 FlowLong）。额外问题：
- 钉钉专属的 `fx.has0()` 函数需映射到 XLang
- `flowWidgets[]` 需映射到 Nop 表单定义
- 钉钉的 `assigneeType` 枚举与 nop-wf `wf-actor` 标签库不完全对齐

---

## 四、优缺点总结

### FlowLong 树格式

| 优点 | 缺点 |
|------|------|
| ✅ 语义直观：树结构与审批流程的"顺序+分支"心理模型一致 | ❌ 树→DAG 展平成本高 |
| ✅ 自包含：单 JSON 文件包含完整流程定义 | ❌ 条件分支前后的隐式汇聚没有显式 join |
| ✅ 表达能力最强：13 种节点类型 + 子流程 + 定时器 + 触发器 | ❌ `parentNode` transcient，序列化/反序列化需要额外处理 |
| ✅ SpEL 条件表达式灵活 | ❌ JSON 体积随嵌套深度线性增长（深层节点路径冗长） |
| ✅ `nodeAssigneeList` + `weight` 支持完整票签 | ❌ 网关前后汇聚逻辑不明确（靠 engine 隐式处理） |

### Warm-Flow 图格式

| 优点 | 缺点 |
|------|------|
| ✅ **flat nodeList 直接映射到 nop-wf DAG，无需展平算法** | ❌ 节点显式成为网关类型（3/4/5）而非 transition 属性，语义被固化 |
| ✅ `skipList` 内嵌在节点中，transition 关系清晰 | ❌ 条件表达式字符串格式表达力有限（无法嵌套 OR/AND） |
| ✅ `permissionFlag` 字符串可扩展（SPI 注册前缀策略） | ❌ 无子流程支持 |
| ✅ `nodeRatio` 字符串编码了完整协作模式（会签/或签/票签/顺签/通过数/拒绝数） | ❌ 无表单字段权限 |
| ✅ 数据结构精简，JSON 体积小 | ❌ 字符串条件需解析器 |
| ✅ 坐标 `coordinate` 字段天然支持流程图渲染 | |

### DingTalk 树格式（现有）

| 优点 | 缺点 |
|------|------|
| ✅ **是钉钉 API 原生格式**，直接对接钉钉审批流导入场景 | ❌ 大量钉钉特有字段（`flowWidgets`, `workFlowDef`, `formAuths`, `flowPermission`）增加噪音 |
| ✅ 条件分组表达式完整（AND/OR 任意嵌套） | ❌ 树→DAG 展平成本高 |
| ✅ 表单字段定义（`flowWidgets`）和字段权限（`formAuths`）内联 | ❌ `formAuths` 在每个节点重复，数据冗余大 |
| ✅ 字段级权限（readable/editable）与 nop-wf `wf:formAuth` 对齐 | ❌ 没有并行/包容网关（钉钉场景无需） |
| ✅ 内置多级主管和「审批人=提交人」处理策略 | ❌ 审批人类型枚举无法扩展 |

---

### 4.2 关键判断：DingTalk 格式比 FlowLong 更适用于 nop-wf 的 dingflow 场景

**结论：是的，对于 nop-wf 的 dingflow 审批流导入/转换场景，DingTalk 的 JSON 格式比 FlowLong 更合用。**

理由如下：

#### 1. DingTalk 是审批流格式，FlowLong 是通用工作流格式

| 维度 | DingTalk | FlowLong |
|------|----------|----------|
| **设计目标** | 企业审批（请假/报销/付款/调薪） | 通用工作流引擎（审批 + 定时 + 子流程 + 并行 + AI） |
| **节点类型数** | 5 种（0发起/1审批/2抄送/3条件/4路由） | 13 种（含 timer/trigger/parallel/callProcess/route 等） |
| **nop-wf 不需要的类型** | 无（都在审批域内） | parallelBranch, inclusiveBranch, timer, trigger, callProcess, autoPass, autoReject, routeBranch nop-wf 用其他方式表达 |

FlowLong 的 13 种节点类型中，超过一半在审批流场景无用或需要在转换中做特殊处理：
- `timer(6)`/`trigger(7)` → nop-wf 通过 `due-time-expr` + 外部调度器处理（`extensions-design.md` §五）
- `autoPass(30)`/`autoReject(31)` → nop-wf 通过 `approvalType` + `<when>` 等价，不需独立 step 类型
- `parallelBranch(8)`/`inclusiveBranch(9)` → nop-wf 通过 `splitType="and"` + `<when>` 表达，不需独立 step 类型
- `callProcess(5)` → nop-wf 用 `<flow>` step 类型

DingTalk 的 5 种节点类型全部在审批域内且直接对应 nop-wf 的概念，**无浪费映射面**。

#### 2. DingTalk 的 formAuths/flowWidgets 与 nop-wf 的字段权限天然对齐

nop-wf 的 `wf.xdef` 支持 `<wf:formAuth>` actor 级字段权限（`wf.xdef` 中 `assignment.xdef` 定义），这是 FlowLong 和 Warm-Flow 都没有的独有能力。DingTalk 的 `formAuths[]`（readable/editable 每个节点配置）是**唯一**能直接为 nop-wf 的 `wf:formAuth` 提供数据的外部格式。

FlowLong 仅在 `extendConfig.formPermissions` 中简单记录，没有结构化字段级权限模型。

#### 3. DingTalk 的 CC 是独立节点类型（type=2），FlowLong 的 CC 是审批人的一种办理方式（PerformType.copy=9）

DingTalk 把"抄送"作为流程中的独立步骤，与 nop-wf 的 `specialType="cc"` 概念完全一致。FlowLong 把抄送视为审批人的一种操作模式（`PerformType.copy`），转换时需要额外映射。

#### 4. DingTalk 的 conditionGroups 结构最接近 nop-wf 的 `<when>` 语义

DingTalk 的条件结构 `conditionGroups[].conditions[]`（组间 OR，组内 AND）与 nop-wf 同一 transition 内多个 `<to-step>` 的独立 `<when>` 标签（隐式 OR 语义）概念对齐。条件字段名 `varName` 可直接映射到 XLang 的 `wfVars.{name}` 或 `wfRt.bizEntity.{name}`。

FlowLong 使用 SpEL 字符串表达式，需要字符串解析→XLang 翻译，增加转换复杂度。

#### 5. DingTalk 的树结构与审批流心理模型一致

审批流的用户思维模式是"第一步→第二步→第三步"的线性顺序，分支是"如果 A 条件成立走这条线"。DingTalk 的 `childNode` 直接对应"下一步"，`conditionNodes` 直接对应"分支条件"，与用户直觉一致。

FlowLong 虽然也是树结构，但其额外的分支容器类型（`conditionNodes`/`parallelNodes`/`inclusiveNodes`/`routeNodes`）反映了通用工作流的复杂性，超出了审批流的需求。

#### 6. DingTalk 的局限不影响 nop-wf（因为 nop-wf 本身已覆盖）

| DingTalk 的局限 | 对 nop-wf 的影响 | 说明 |
|----------------|-----------------|------|
| 无票签（只有依次/会签/或签） | 不影响 | 生成的 XWF 可设 `execGroupType="vote-group"` + `passWeight` 扩展票签；DingTalk 未提供此语义时可由转换器配置默认值或走文档约定 |
| 无并行/包容网关 | 不影响 | nop-wf 的审批流场景中，并行分发通过 `splitType="and"` 在 transition 层表达，不需额外输入 |
| 无子流程 | 不影响 | nop-wf 的 `<flow>` 步骤类型不需要从输入 JSON 获取子流程信息 |
| 审批人类型枚举不可扩展 | 略有限制 | 转换器做 `assigneeType` → `wf-actor:xxx` 的静态映射表即可（1→StarterManager, 2→StarterDeptManager, 3→role, 4→user, 7→dept） |
| 条件运算符是钉钉数字枚举 | 略有限制 | 转换器维护运算符转换表（3→`<=`, 4→`>`, 20→`fx.has0()` 等价 XLang 等）|

#### 综合结论

| 判断维度 | DingTalk | FlowLong |
|---------|----------|----------|
| 与审批域匹配度 | ✅ **高**（原生审批流格式） | 🟡 中（通用工作流格式，大半类型在审批场景无用） |
| 与 nop-wf 概念对齐度 | ✅ **高**（CC/条件/字段权限/线性链路一一对应） | 🟡 中（需要跨概念映射：PerformType→execGroupType, SpEL→XLang） |
| 转换复杂度 | ✅ **低到中**（5 类型 + 直映射） | 🟡 **中到高**（13 类型 + 条件 SpEL 翻译 + 冗余类型处理） |
| 字段权限支持 | ✅ **唯一支持**（formAuths 直接提供 wf:formAuth 数据） | ❌ 无（extendConfig 中无结构化模型） |
| 表单集成 | ✅ **完整**（flowWidgets 定义表单字段） | ❌ 仅 URL 引用 |
| 有意义的额外信息 | 多（表格定义 + 权限 + 流程元信息） | 少（仅有流程结构，表单和权限弱） |

**因此，对于 nop-wf 的 dingflow 审批流导入/转换场景：**
- **DingTalk 格式是天选来源**——它恰好覆盖了 nop-wf 审批流能力的一个匹配子集，没有多余概念
- **FlowLong 格式更适合需要"通用工作流引擎迁移到 nop-wf"的场景**，而不是审批流场景
- **转换器的第一优先级应是完善 DingTalk 树→XWF 的转换**，然后才是考虑 FlowLong JSON 支持

---

## 五、对 nop-wf dingflow JSON 格式的设计建议

### 5.1 核心设计原则

1. **保持 DingTalk 树格式作为输入**：因为它是钉钉 API 的原生格式，接收方（nop-wf）应该在转换器中处理树→DAG 算法，而不是要求上游改变格式。
2. **在转换器内部使用临时图结构**：转换器内部可将树展平为中间 DAG 表示（类似 Warm-Flow 的 nodeList + skipList），再生成 XWF。
3. **JSON 输入格式应忠于来源**：不重新设计钉钉格式，而是增强 `impl_GenWorkflow.xpl` 的处理能力。

### 5.2 转换器架构（受 P1/P3 约束）

参考 `extensions-design.md` §三 设计原则，转换器实现需遵守：

- **P1 引擎核心不可侵蚀**（`extensions-design.md` §3.1）：树→DAG 展平算法**不能**放在 `nop-wf-core` 中作为引擎的一部分，应放在：
  - `dingflow-tran.xlib`（XPL 模板层，处理变量替换和 XML 生成）
  - 或 `nop-wf-service` 中的 Java 辅助类 `DingFlowToXwfConverter`（处理复杂树遍历逻辑），由 XPL 通过 `<c:invoke>` 调用
  - 或 `nop-wf-web` 中（如果是前端导入后预览场景）
- **P3 模型即代码，运行时不可变**（`extensions-design.md` §3.3）：转换生成的 XWF 必须在模型加载期就是完整的 DAG，所有 join 点、分支路径、条件表达式必须静态确定。不允许生成"待运行时动态补充"的残缺模型。

```
DingTalk JSON（树）         →  临时 DAG（flat）           →  nop-wf XWF（完整 DAG）
──────────────                   ─────────                    ─────────────────
nodeConfig (root)                steps[] (展平后)             <steps>（所有 step 完整定义）
  └── childNode                  transitions[]                 <transition>（含所有 onAppStates）
      └── conditionNodes         joins[]                       <join>（joinType, waitSteps 静态确定）
          └── childNode          conditions[]                  <when>（XLang 表达式，编译期可校验）
                                 actors[]                      <actor>（actorType + actorModelId）
                                 execGroups[]                  execGroupType
```

- 树→DAG 展平在 service 层或 xlib 层完成（不在 core）
- 输出的 XWF 模型必须通过 `WfModelAnalyzer` 的 DAG 无环校验

### 5.3 如果需要在 nop-wf 中定义新的"从零创建"JSON 格式

如果用户不是从钉钉导入，而是**在 nop-wf 中直接创建审批流**，建议使用以下格式（融合 DingTalk 和 Warm-Flow 优点）：

```json
{
  "wfName": "salary-adjustment",
  "displayName": "调薪审批",
  "nodes": [
    { "id": "submit",      "type": "start",     "displayName": "发起申请",  "assignment": { "type": "initiator" } },
    { "id": "dept-approval","type": "approval",  "displayName": "部门审批",
      "assignment": { "type": "supervisor", "upLevel": 1 },
      "examineMode": "countersign" },
    { "id": "vp-approval", "type": "approval",  "displayName": "VP审批",
      "assignment": { "type": "role", "roleId": "vp" },
      "examineMode": "or-sign" },
    { "id": "cc-hr",       "type": "cc",        "displayName": "抄送HR",
      "assignment": { "type": "specified", "assignees": ["hr-manager"] } },
    { "id": "end",         "type": "end" }
  ],
  "edges": [
    { "from": "submit",        "to": "dept-approval", "label": "agree" },
    { "from": "dept-approval", "to": "vp-approval",
      "label": "agree", "condition": "wfVars.amount > 50000" },
    { "from": "dept-approval", "to": "cc-hr",
      "label": "agree", "condition": "wfVars.amount <= 50000" },
    { "from": "vp-approval",   "to": "cc-hr",         "label": "agree" },
    { "from": "cc-hr",         "to": "end",            "label": "confirm" }
  ]
}
```

这种格式：
- 采用 Warm-Flow 的 flat `nodes[]` + 独立 `edges[]` 而非内嵌 skipList（更易阅读和编辑）
- 节点类型采用字符串语义而非数字枚举（更易理解）
- `assignment` 独立为对象（容纳 nop-wf 的 `selectUser` 和 `selection` 双层模型）
- `examineMode` 字符串值直接映射到 nop-wf 的 `execGroupType`
- 条件使用 XLang 表达式（直接嵌入到生成的 XWF 中）

**但在导入 DingTalk JSON 的场景下不适用**。

---

## 六、结论与建议

### 核心结论：DingTalk 格式是最佳输入

**DingTalk 的 JSON 格式比 FlowLong 和 Warm-Flow 都更适用于 nop-wf 的 dingflow 审批流导入场景。** 理由（详见 §4.2）：

1. DingTalk 的 5 种节点类型天然对审批域，无多余概念；FlowLong 的 13 种中超过一半在审批场景需特殊处理
2. DingTalk 的 `formAuths`+`flowWidgets` 是唯一能为 nop-wf 的 `wf:formAuth` 提供结构化字段权限数据的外部格式
3. DingTalk 的 `conditionGroups` 结构最接近 nop-wf 的 `<when>` 语义，映射直接
4. DingTalk 的 CC 节点（type=2）与 nop-wf `specialType="cc"` 概念完全一致
5. DingTalk 的局限（无票签、无并行网关、无子流程）不影响 nop-wf，因为 nop-wf 自带这些能力

### 对转换器实施建议

1. **保持 DingTalk 树格式作为输入**。不改造 JSON 格式。
2. **树→DAG 展平放在 `nop-wf-service` 或 `dingflow-tran.xlib` 层**，不放在 `nop-wf-core` 的引擎中（遵守 P1）。
3. **生成的 XWF 必须是完整 DAG**，所有 join 点和条件路径静态确定（遵守 P3）。不允许生成依赖运行时修改的残缺模型。
4. **转换器架构**：Java 辅助类做树遍历和 DAG 构建（`DingFlowToXwfConverter.java`），XPL 做 XML 模板生成。
5. **第二步**：如果需要 FlowLong JSON 输入支持，实现第二个转换器 `FlowLongToXwfConverter`，共享 `DagBuilder`（中间 DAG 结构）。

### 对"从零创建"场景的建议

6. 如果需要 nop-wf 原生 JSON 格式（不从钉钉导入，直接创建），采用 flat `nodes[]` + 独立 `edges[]` 图格式（见 §5.3 示例），与 nop-wf DAG 模型最自然对应。此时 DingTalk 树格式不作为输入，仅作为转换器的目标格式之一。

### 被否决的方案

- **"把 DingTalk JSON 改为 tree with explicit join"**：不需要。DingTalk 格式是上游输入，不应该改；转换器内部处理展平即可。
- **"用 Warm-Flow 完全替代 DingTalk 格式"**：否决。理由：两者场景不同。Warm-Flow 格式适合从零创建，DingTalk 适合导入。可同时支持两种输入格式（在转换器中做格式检测）。
- **"把树→DAG 展平放在 WorkflowEngineImpl 中"**：否决（违反 P1）。展平是导入期转换逻辑，不是引擎运行时逻辑，应放在 service 层或 xlib 层。

---

## Open Questions

- [ ] 树→DAG 展平中，conditionNodes 的汇聚点如何确定？（现有 nop-wf 示例中，所有分支汇聚到一个显式 `<join>` 步骤，但 DingTalk 树中条件分支的"后续节点"是路由节点的 childNode——需要对齐这两种模型。参见 `approval-flow-design.md` DAG 约束章节关于 join 的定义。）
- [ ] DingTalk 的 route type=4（路由）与 conditionNodes 的关系：一个路由节点可以有多个 conditionNodes，每个匹配后走自己的 childNode。所有 conditionNodes 中最多一个会命中（priorityLevel 决定优先级）。这等价于 nop-wf 的 `splitType="or"` + `<to-step order=N><when>`。但路由节点自己的 childNode 是在所有 conditionNodes 都**不**匹配时的默认路径吗？还是匹配后的共同后续？
- [ ] 钉钉 `multiInstanceApprovalType` 只有 0/1/2（依次/会签/或签），不支持票签。需要时如何扩展？可参考 `extensions-design.md` §七 的 `wf-vote.xlib` 票签策略标签库方案：转换时如缺失票签参数，使用默认 `execGroupType="and-group"` 或生成注释提示用户手动补充 `<check-exec-group-complete>`。
- [ ] 字段权限去重：DingTalk 的 `formAuths[]` 在每个步骤节点重复。转换时是保留重复（XWF 不做去重）还是提取到全局 `<form-auths>` 然后在各步骤 `<ref-form-auths>` 引用？

## References

- 设计文档：`ai-dev/design/nop-wf/approval-flow-design.md`（审批流核心模式契约）
- 扩展设计：`ai-dev/design/nop-wf/extensions-design.md`（P1/P2/P3 设计原则）
- 现有 DingTalk JSON：`nop-wf/nop-wf-service/src/test/resources/_vfs/nop/wf/beeflow/*.json`
- 现有转换器骨架：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/dingflow-tran/impl_GenWorkflow.xpl`
- FlowLong 模型源码：`c:/can/sources/flowlong/flowlong-core/src/main/java/com/aizuda/bpm/engine/model/`
- Warm-Flow JSON DTO：`c:/can/sources/warm-flow/warm-flow-core/src/main/java/org/dromara/warm/flow/core/dto/`
- nop-wf XDef：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef`
- nop-wf 工作流配置：`docs-for-ai/02-core-guides/workflow-configuration.md`
- 引擎能力对比（已有分析）：`ai-dev/analysis/2026-07-02-flowlong-warmflow-vs-nop-wf-comparison.md`
