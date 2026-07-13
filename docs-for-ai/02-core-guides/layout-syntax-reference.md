# Form Layout DSL 语法参考

本文档完整描述 `<form>` 内 `<layout>` 元素的文本 DSL 语法。

如果你只需要改字段标签或增删几个字段，看 `view-and-page-customization.md` 的 Form 章节即可。如果你需要**字段分组、折叠、跨列、Tab 布局、查询运算符配置**，本文档是权威参考。

> 本文同时适用于 **AMIS（`web.xlib`）和 Flux（`flux-web.xlib`）** 两种渲染器。所有语法（`*` 必填、`@` 只读、`!` 隐藏标签、跨列、分组、Tab、查询运算符）在两种渲染器下行为一致，除非显式标注 `(AMIS only)` 或 `(Flux only)`。Flux 渲染器特定的属性命名映射（`requiredOn` → `required`、`readonlyOn` → `readOnly`、`clearValueOnHidden` → `hiddenFieldPolicy.clearValueWhenHidden`、`initFetch` → `autoInit`、`asideResizor` → `asideResizable`）和已确认不支持的 form 属性（`silentPolling`/`wrapWithPanel`/`canAccessSuperData`）见 `flux-rendering.md` 的 AMIS vs Flux 差异表。

## 最小示例

```xml
<form id="edit">
    <layout>
        userName[用户名] status[状态]
        email[邮箱] phone[电话]
    </layout>
</form>
```

每行 1-N 个字段，空格分隔。`fieldName[标签]` 定义一个字段及其显示标签。

## 语法元素总表

| 语法 | 含义 | 示例 |
|------|------|------|
| `fieldName` | 字段（使用 xmeta 中的 displayName） | `status` |
| `fieldName[标签]` | 字段 + 自定义标签 | `status[状态]` |
| `fieldName(2)` | 字段跨 2 列 | `remark(2)` |
| `fieldName(1,2)` | 字段跨 1 行 2 列 | `remark(1,2)` |
| `*fieldName` | 本表单必填 | `*email[邮箱]` |
| `@fieldName` | 本表单只读 | `@userName` |
| `!fieldName` | 隐藏字段标签 | `!deptId` |
| `===...===groupId[组标题]===...===` | 有标题分组（不可折叠） | `=====baseInfo[基本信息]======` |
| `===...===`>`groupId[组标题]===...===` | 有标题分组（可折叠，缺省展开） | `=====>baseInfo[基本信息]======` |
| `===...===^groupId[组标题]===...===` | 有标题分组（可折叠，缺省收起） | `=====^advInfo[高级信息]=========` |
| `===...===` 单独一行 | 结束当前分组 | |
| `----` 单独一行 | 分割线 | `----` |

---

## 1. 基本字段排列

每行可以放 1 到 N 个字段，空格分隔。默认两列布局。

```xml
<layout>
    code[单号] status[状态]
    supplierId[供应商] warehouseId[仓库]
    remark[备注]
</layout>
```

- 同一行的字段左右排列
- 不同行的字段上下排列
- 如果某行只有一个字段，它占左边一列，右边留空

## 2. 字段标签

### 2.1 显式标签

```xml
status[审批状态]
```

渲染时显示"审批状态"而非 xmeta 中的 `displayName`。

### 2.2 省略标签

```xml
status
```

使用 xmeta 中该 prop 的 `displayName`。如果 xmeta 未定义 `displayName`，则使用字段名。

## 3. Cell 修饰符

修饰符在字段名前，可组合使用，顺序不限。

### 3.1 `*` — 本表单必填

```xml
*email[邮箱]
```

强制该字段在本表单中必填。

**必填判定优先级**：layout `*` > `<cell mandatory="true">` > xmeta `mandatory="true"`

- 如果 xmeta prop 已声明 `mandatory="true"`，页面**自动显示必填**，**不需要**在 layout 中加 `*`
- `*` 仅用于 xmeta 中非 mandatory 但在本表单需要必填的场景
- 典型场景：字段在数据模型中可选，但在某个特定表单中必填（如审批表单中要求备注必填，但其他表单中备注可选）

### 3.2 `@` — 本表单只读

```xml
@userName
```

强制该字段在本表单中只读。

**只读判定优先级**：layout `@` > `<cell readonly="true">` > xmeta `updatable="false"`（在 edit 模式生效）

- 典型场景：`add` 表单中某些字段不允许用户填写（由系统自动填充），但 `edit` 表单中允许修改

### 3.3 `!` — 隐藏标签

```xml
!deptId
```

不显示字段标签（直接占据整行宽度）。典型场景：树形选择器、富文本编辑器等不需要左侧标签的控件。

### 3.4 组合使用

```xml
*@remark[备注](2)
```

含义：必填 + 只读 + 跨 2 列。

## 4. 跨列与跨行

### 4.1 跨列 `(colSpan)`

```xml
remark(2)
```

该字段占据 2 个列宽。注意：**传入的是跨越的列数**，`(2)` 表示占 2 列。

### 4.2 跨行跨列 `(rowSpan, colSpan)`

```xml
remark(1,2)
```

该字段跨 1 行 2 列。第一个数字是行跨度，第二个是列跨度。

## 5. 分组

分组标题行的通用结构：

```
===...==== [折叠标记] [#嵌套层级] groupId[组标题] ===...===
```

- **`=`的数量不限**，至少 2 个，纯 `=` 行（无后续内容）表示结束当前分组
- **折叠标记**（三选一，可省略）：
  - `>` — 可折叠，缺省**展开**。渲染为 `<fieldSet collapsable="true" collapsed="false">`
  - `^` — 可折叠，缺省**收起**。渲染为 `<fieldSet collapsable="true" collapsed="true">`
  - `<` — 同 `^`（兼容写法，但在 XML 中需要转义为 `&lt;`，建议用 `^` 代替）
  - 省略 — **不可折叠**。有标题时渲染为 `<fieldSet collapsable="false">`
- **`#` 嵌套层级**：1-5 个 `#`，表示嵌套深度（见 §5.6）
- **groupId**：可选的组标识符（Java 标识符，不含空格），用于编程引用和 `<cells>` 中的 `visibleOn` 控制
- **`[组标题]`**：组的显示标题

> **注意**：如果 groupId 位置写了含空格的文本（如 `====basic info====`），解析器会将其当作 label 而非 id——因为 id 不允许包含空格。中文连续字符（如 `基本信息`）不含空格，会被当作 id。如果要同时指定 id 和 label，必须用 `groupId[label]` 语法。

### 5.1 不可折叠分组

```xml
<layout>
    =====baseInfo[基本信息]======
    code[单号] status[状态]
    supplierId[供应商] warehouseId[仓库]

    =====amountInfo[金额信息]======
    totalAmount[合计金额] totalTaxAmount[税额合计]
</layout>
```

无折叠标记（`>`/`^`/`<` 均未出现），渲染为 `<fieldSet collapsable="false">`——标题不可点击折叠，只做视觉分隔。

### 5.2 可折叠分组（缺省展开）

```xml
===========>baseInfo[基本信息]======
code[单号] status[状态]
```

`>` 在组标题前表示该组**可折叠，缺省展开**。渲染为 AMIS `<fieldSet collapsable="true" collapsed="false">`。

### 5.3 可折叠分组（缺省收起）

```xml
=========^advInfo[高级信息]=========
internalNote[内部备注] tags[标签]
```

`^` 在组标题前表示该组**可折叠，缺省收起**。渲染为 AMIS `<fieldSet collapsable="true" collapsed="true">`。

**使用场景**：不常用的字段（审计信息、扩展属性、内部备注等）默认折叠，用户点击展开。避免表单过长。

### 5.4 分组的渲染规则

| 组配置 | 渲染结果 |
|--------|---------|
| 有 label（不管是否可折叠） | `<fieldSet collapsable="{foldable}">` |
| 有 id 但无 label | `<group direction="vertical">`（视觉容器，无标题） |
| 无 id 无 label（匿名） | 无包裹，字段行直接输出 |

### 5.5 组的动态可见性

通过在 `<cells>` 中为组 id 配置 `visibleOn`，可以按条件显示/隐藏整个分组：

```xml
<form id="edit">
    <layout>
        =====baseInfo[基本信息]======
        code[单号] status[状态]

        ======finance[财务信息]======
        amount currencyId
    </layout>
    <cells>
        <cell id="finance" visibleOn="${status == 'APPROVED'}"/>
    </cells>
</form>
```

`<cell id="finance">` 的 id 对应组的 groupId，`visibleOn` 控制该分组是否显示。

### 5.6 嵌套分组

在 `#` 标记嵌套层级（1-5 级，最多 5 层）：

```xml
<layout>
    #outer[外层组]
    fieldA fieldB
    ##inner[内层组]
    fieldC fieldD
</layout>
```

`#` 数量越多层级越深。内层组在外层组的行内渲染。通常不需要手动管理嵌套层级——解析器会根据组标题的出现顺序自动推断。

### 5.7 结束当前分组

单独一行 `===`（只有等号、无后续内容）结束当前分组：

```xml
=====>baseInfo[基本信息]======
code[单号] status[状态]
====
```

通常不需要显式结束——下一个组标题或 layout 文本结束会自动关闭当前组。

### 5.8 `~` 续行标记（高级）

解析器支持在组标题行的首尾使用 `~` 作为续行标记（`~~~====...====~~~`），用于嵌套分组的行内延续。这是内部机制，手写 layout 时通常不需要使用——用 `#` 嵌套层级（§5.6）或显式分组标题即可满足所有常见需求。

## 6. 分割线

单独一行 `----`（至少 2 个连字符）产生一个视觉分割线：

```xml
code[单号] status[状态]
----
remark[备注]
```

渲染为 AMIS `<divider>`。

## 7. Tab 布局

当字段组很多时，可以用 Tab 替代垂直分组：

```xml
<form id="edit" layoutControl="tabs">
    <layout>
        =====baseInfo[基本信息]======
        code[单号] status[状态]
        supplierId[供应商]

        =====finance[财务信息]======
        totalAmount[合计] currencyId[币种]

        =====audit[审计信息]==========
        createdBy[创建人] createTime[创建时间]
    </layout>
</form>
```

`layoutControl="tabs"` 将分组渲染为 AMIS `<tabs>`，每个 group 变成一个 Tab 页签。组的 label 成为 Tab 标题。

> **注意**：`form.xdef` 中还定义了 `layoutControl="wizard"`（向导布局），但 AMIS 渲染器目前仅实现了 `tabs`。

## 8. 弹窗大小（size）

```xml
<form id="edit" size="lg">
```

| 值 | 含义 | 自动推导条件 |
|----|------|-------------|
| `sm` | 小弹窗 | 字段数 < 5 |
| `md` | 中弹窗 | 字段数 5-19 |
| `lg` | 大弹窗 | 字段数 ≥ 20 |

如果不设置 `size`，自动按字段数推导。

## 9. editMode 模式

| 值 | 含义 | 说明 |
|----|------|------|
| `add` | 新增 | 字段可编辑；主键若 `insertable=false` 则只读 |
| `edit` | 编辑 | 字段可编辑；字段若 `updatable=false` 则只读 |
| `view` | 查看 | 全部只读 |
| `query` | 查询条件 | 字段名自动加 `filter_` 前缀；配合 `filterOp` 使用 |

`add` 表单通常通过 `x:prototype="edit"` 继承 edit 表单的 layout，只需改 `editMode`。

## 10. 完整示例（对照 NopAuthUser）

以下为简化摘录（省略部分字段），展示了分组 + 折叠 + Tab + 修饰符的综合使用。完整文件见 `nop-auth/nop-auth-web/.../NopAuthUser.view.xml`：

```xml
<form id="add" layoutControl="tabs">
    <layout>
        ===========>baseInfo[基本信息]======
        userName status[用户状态]
        nickName[昵称] deptId[部门]
        userType[用户类型] gender[性别]
        password[密码] *__password2[重复密码]
        email[邮件] phone[电话]

        ===========^extInfo[扩展信息]=========
        idType[证件类型] idNbr[证件号]
        birthday[生日] workNo[工号]
        positionId[职务] telephone[座机]
        remark[备注]
    </layout>

    <cells>
        <cell id="__password2" custom="true" label="重复密码">
            <gen-control>
                <input-password>
                    <validations equalsField="password" />
                </input-password>
            </gen-control>
        </cell>
    </cells>
</form>
```

要点：
- `layoutControl="tabs"` → 两个分组渲染为两个 Tab
- `>` → baseInfo 可折叠但缺省展开
- `^` → extInfo 可折叠且缺省收起（低频字段默认隐藏）
- `*__password2[重复密码]` → `__password2` 是 `custom="true"` 的前端专用字段（不在 xmeta 中），用 `*` 标记必填

## 11. 查询表单配置

### 11.1 基本查询表单

```xml
<form id="query" editMode="query" title="查询条件">
    <layout>
        code[单号] status[状态]
        supplierId[供应商] businessDate[业务日期]
    </layout>
</form>
```

查询模式（`editMode="query"`）下，字段名自动映射为 `filter_{propName}`，默认使用 `eq` 运算符。

### 11.2 查询运算符配置

对于文本字段（名称、编码等），通常需要模糊查询而非精确匹配。有两种配置位置：

**在 cell 上配置 `filterOp`（优先级最高）**：

```xml
<cells>
    <cell id="code" filterOp="like"/>
    <cell id="businessDate" filterOp="date-between"/>
</cells>
```

**在 xmeta prop 上配置 `ui:filterOp`（全局默认）**：

```xml
<prop name="code" queryable="true" ui:filterOp="like"/>
<prop name="businessDate" queryable="true" ui:filterOp="date-between"/>
```

配置后，查询字段名自动变为 `filter_code__like`、`filter_businessDate__date-between`。

**运算符解析优先级**：`cell.filterOp` > `prop.ui:filterOp` > `cell.ui:filterOp` > `prop.xui:defaultFilterOp`

### 11.3 常用 filterOp 值

| filterOp | 含义 | 适用类型 |
|----------|------|---------|
| `eq` | 等于（默认） | 所有类型 |
| `like` | 模糊匹配 | String |
| `in` | 包含于集合 | 所有类型 |
| `date-between` | 日期范围 | Date |
| `datetime-between` | 日期时间范围 | DateTime |

### 11.4 查询必填配置

```xml
<prop name="status" queryable="true" ui:queryMandatory="true"/>
```

或在 layout 中用 `*` 标记：

```xml
<layout>
    *status[状态] code[单号]
</layout>
```

### 11.5 后端查询权限控制

xmeta 中的 `queryable` 属性控制后端是否允许该字段作为查询条件：

- `queryable="false"` → 后端拒绝该字段的查询请求（抛 `ERR_BIZ_PROP_NOT_SUPPORT_QUERY`）
- `allowFilterOp="eq,in"` → 限制该字段只接受指定运算符
- 未配置 `allowFilterOp` 时，默认允许 `{eq, in, date-between, datetime-between}`

这是安全层面的后端校验，不是 UI 层面的显隐控制。

## 12. ID 字段处理

技术主键 `id` 如果没有业务含义，建议不在表单 layout 中显示。

**方式 A（推荐）：layout 中省略 `id`**

在定制层 view.xml 中重写 form 的 layout，不包含 `id` 字段。`id` 仍在 GraphQL 查询中自动传递（框架始终将主键加入 selection），只是不显示。

**方式 B：xmeta 中标记 `internal="true"`**

```xml
<prop name="id" internal="true"/>
```

codegen 自动跳过 `internal` 字段，不生成到任何表单的 layout 中。

## 相关文档

- `./view-and-page-customization.md` — 快速参考（基础增删改字段）
- `./page-dsl-pattern-catalog.md` — 复杂页面 DSL 模式
- `./external-app-examples.md` — 外部应用代码示例
- `./frontend-rendering-pipeline.md` — 页面生成管线
- `./amis-rendering.md` — AMIS 渲染细节
