# XView视图模型开发指南

## 概述

XView是Nop平台的前端视图模型，采用与具体前端框架无关的XML格式定义。它通过页面(page)、表格(grid)、表单(form)、操作(action)等业务领域概念来表达前端交互逻辑，最终通过`x:gen-extends`元编程机制生成AMIS页面。

**核心公式**：`XView = Δ_view ⊕ Generator<XMeta>`

### 设计理念

1. **框架无关**：XView不依赖具体前端框架，可适配AMIS、阿里LowCodeEngine等多种框架
2. **领域导向**：使用业务领域概念而非技术概念描述界面
3. **分层构造**：字段级 → 表单/表格级 → 页面级，逐层抽象
4. **Delta定制**：通过继承和Delta机制实现差异化定制

## 核心模型结构

### View根元素

```xml
<view bizObjName="string" x:schema="/nop/schema/xui/xview.xdef">
    <!-- 关联的元数据模型 -->
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>

    <!-- 控件库，控制字段到控件的映射 -->
    <controlLib>/nop/web/xlib/control.xlib</controlLib>

    <!-- 表格定义集合 -->
    <grids xdef:key-attr="id" xdef:body-type="list">
        <grid id="list" xdef:ref="grid.xdef"/>
    </grids>

    <!-- 表单定义集合 -->
    <forms xdef:key-attr="id" xdef:body-type="list">
        <form id="edit" xdef:ref="form.xdef"/>
    </forms>

    <!-- 页面定义集合 -->
    <pages xdef:key-attr="name" xdef:body-type="list">
        <crud name="main" grid="list" />
        <simple name="view" form="view" />
    </pages>
</view>
```

### 核心属性

| 属性 | 说明 | 示例 |
|------|------|------|
| `bizObjName` | 业务对象名称 | `NopAuthUser` |
| `objMeta` | 关联的XMeta模型路径 | `/nop/auth/model/NopAuthUser/NopAuthUser.xmeta` |
| `controlLib` | 控件库路径 | `/nop/web/xlib/control.xlib` |

## Grid（表格）配置

### 基本结构

```xml
<grid id="list" editMode="query" columnNum="4"
      affixHeader="true" sortable="true"
      selectable="true" multiple="true">
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>

    <cols xdef:key-attr="id" xdef:body-type="list">
        <col id="userName" label="用户名" width="100px" align="left"
             mandatory="true" sortable="true" fixed="left"/>
        <col id="status" label="状态" width="80px" align="center"/>
        <col id="createTime" label="创建时间" sortable="true"/>
    </cols>
</grid>
```

### Grid属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | string | 表格唯一标识 |
| `displayName` | string | 表格显示名称 |
| `editMode` | xml-name | 编辑模式：edit/add/view/query |
| `columnNum` | int | 表格列数 |
| `affixHeader` | boolean | 是否固定表头 |
| `checkOnItemClick` | boolean | 点击行是否选中 |
| `selectable` | boolean | 是否支持选择 |
| `multiple` | boolean | 多选模式 |
| `sortable` | boolean | 是否支持排序 |
| `combineNum` | int | 自动合并单元格的列数 |
| `combineFromIndex` | int-or-string | 合并起始列 |

### Col属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | string | 列标识，对应XMeta中的prop name |
| `label` | string | 列标题 |
| `width` | string | 列宽度，如`100px` |
| `align` | string | 对齐方式：left/center/right |
| `fixed` | string | 固定列：left/right |
| `sortable` | boolean | 是否可排序 |
| `mandatory` | boolean | 是否必填 |
| `readonly` | boolean | 是否只读 |
| `hidden` | boolean | 是否隐藏 |
| `breakpoint` | string | 响应式断点，从该列开始折叠 |
| `groupName` | string | 分组名 |

### 示例：使用bounded-merge限制列

```xml
<view x:extends="_gen/_LitemallGoods.view.xml">
    <grids>
        <grid id="list">
            <!-- bounded-merge表示只保留当前指定的列 -->
            <cols x:override="bounded-merge">
                <col id="id"/>
                <col id="name" label="商品名称"/>
                <col id="picUrl" label="商品图片"/>
                <col id="retailPrice" label="零售价格"/>
                <!-- 其他列不会继承 -->
            </cols>
        </grid>
    </grids>
</view>
```

### 示例：自定义列控件

```xml
<col id="detail" label="详情">
    <gen-control>
        <wrapper>
            <body>
                <button label="查看" actionType="dialog" level="primary">
                    <dialog title="商品详情" size="md">
                        <body>
                            <tpl tpl="${detail|raw}"/>
                        </body>
                    </dialog>
                </button>
            </body>
        </wrapper>
    </gen-control>
</col>
```

## Form（表单）配置

### 基本结构

```xml
<form id="edit" editMode="update" title="编辑-用户"
      size="md" labelAlign="right" labelWidth="100px"
      submitOnChange="false" resetAfterSubmit="false">
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>

    <!-- 表单布局DSL -->
    <layout>
        userName[用户名] nickName[昵称]
        email[邮箱] status[状态]
        deptId[部门] userType[用户类型]
    </layout>

    <!-- 表单字段配置 -->
    <cells xdef:key-attr="id" xdef:body-type="list">
        <cell id="userName" mandatory="true"/>
        <cell id="email" readonly="false"/>
    </cells>
</form>
```

### Form属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | string | 表单唯一标识 |
| `title` | string | 表单标题 |
| `editMode` | xml-name | 编辑模式：add/update/view/query |
| `layoutControl` | xml-name | 布局控件：tabs/wizard |
| `size` | string | 对话框大小：sm/md/lg/xl/full |
| `submitOnChange` | boolean | 变更时自动提交 |
| `submitOnInit` | boolean | 初始化时提交 |
| `resetAfterSubmit` | boolean | 提交后重置 |
| `labelAlign` | string | 标签对齐：left/right/top |
| `labelWidth` | string | 标签宽度 |
| `defaultColumnRatio` | int | 默认列比例 |
| `persistData` | string | 持久化数据的key |
| `preventEnterSubmit` | boolean | 阻止回车提交 |

### 布局DSL语法

表单布局使用特殊DSL语法：

```xml
<layout>
    <!-- 水平排列 -->
    userName[用户名] nickName[昵称]

    <!-- 垂直分隔符 -->
    ===========>baseInfo[基本信息]==========
    deptId[部门] userType[用户类型]

    <!-- 隐藏字段 -->
    !deptId

    <!-- 空字段 -->
    =====

    <!-- 引用子表 -->
    !products
</layout>
```

### Cell属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `id` | string | 字段标识 |
| `label` | string | 字段标签 |
| `mandatory` | boolean | 是否必填 |
| `readonly` | boolean | 是否只读 |
| `custom` | boolean | 是否自定义（不要求在meta中定义） |
| `notSubmit` | boolean | 不提交到后台 |
| `columnRatio` | int | 列比例 |
| `submitOnChange` | boolean | 变更时自动提交 |
| `collapseTitle` | string | 折叠标题 |

### 示例：条件表单定义

```xml
<!-- NopAuthUser.view.xml -->
<view x:extends="super">
    <forms>
        <!-- 当不使用扩展信息时显示简化表单 -->
        <form id="view" feature:on="!nop.auth.use-ext-info">
            <layout>
                userName status nickName
            </layout>
        </form>

        <!-- 当使用扩展信息时显示完整表单 -->
        <form id="view" feature:on="nop.auth.use-ext-info">
            <layout>
                ===========>baseInfo[基本信息]==========
                userName status nickName
                ===========>extInfo[扩展信息]=========
                idType idNbr birthday workNo
            </layout>
        </form>
    </forms>
</view>
```

### 示例：子表配置

```xml
<form id="edit" size="lg">
    <layout>
        goodsSn[商品编号] name[商品名称]
        =========products[商品库存]=======
        !products
    </layout>
    <cells>
        <!-- 引用外部view模型中的grid来显示子表 -->
        <cell id="products">
            <view path="/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml"
                  grid="ref-edit"/>
        </cell>
    </cells>
</form>
```

## Page（页面）配置

### CRUD页面

```xml
<crud name="main" grid="list" filterForm="query" asideFilterForm="asideFilter">
    <table mode="table" filterDefaultVisible="true"
           autoFillHeight="true" alwaysShowPagination="true"
           stopAutoRefreshWhenModalIsOpen="true">
        <api url="@query:NopAuthUser__findPage/{@pageSelection}"/>
    </table>

    <listActions>
        <action id="add-button" label="新增" level="primary">
            <dialog page="add" size="md"/>
        </action>
    </listActions>

    <rowActions x:override="bounded-merge">
        <action id="row-update-button" actionType="drawer"/>
        <action id="row-delete-button"/>
    </rowActions>
</crud>
```

### Simple页面

```xml
<simple name="view" form="view" panelClassName="no-heading">
    <initApi url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
</simple>

<simple name="add" form="add" useFormActions="true"
        redirect="/user-list" reload="window">
    <api url="@mutation:NopAuthUser__save/id"/>
    <messages>
        <success>保存成功</success>
    </messages>
</simple>
```

### Tabs页面

```xml
<tabs name="tabsView" tabsMode="vertical" mountOnEnter="true" unmountOnExit="true">
    <tab name="main" page="main" title="@i18n:common.treeView"/>
    <tab name="list" page="list" title="@i18n:common.listView"/>
</tabs>
```

### Picker页面

```xml
<picker name="picker" grid="pick-list" size="md"
       asideFilterForm="asideFilter" filterForm="query">
    <listActions>
        <action id="select-button" label="确定" level="primary"
                batch="true" close="picker"/>
    </listActions>
</picker>
```

### Table属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `mode` | string | 显示模式：table/grid/cards |
| `filterDefaultVisible` | boolean | 过滤条件默认可见 |
| `filterTogglable` | boolean | 过滤条件可切换 |
| `stopAutoRefreshWhenModalIsOpen` | boolean | 弹框时停止刷新 |
| `alwaysShowPagination` | boolean | 总是显示分页 |
| `autoFillHeight` | boolean | 自适应高度 |
| `initFetch` | boolean | 初始化时拉取 |
| `loadDataOnce` | boolean | 一次性加载 |
| `noOperations` | boolean | 不显示操作列 |
| `multiple` | boolean | 多选模式 |
| `pickerMode` | boolean | 选择器模式 |

## Action（操作）配置

### 操作类型

```xml
<!-- Ajax操作 -->
<action id="test-ajax" actionType="ajax" reload="none">
    <api url="@query:NopAuthDept__get?id=$id" gql:selection="managerId"/>
</action>

<!-- Dialog操作 -->
<action id="detail-button" label="查看详情" actionType="dialog">
    <dialog title="商品详情" size="md" noActions="true">
        <body>
            <!-- 对话框内容 -->
        </body>
    </dialog>
</action>

<!-- Drawer操作 -->
<action id="edit-button" actionType="drawer" label="编辑">
    <drawer page="edit" size="lg"/>
</action>

<!-- Link操作 -->
<action id="create-link" actionType="link" url="/goods-create" label="新建商品"/>
```

### 操作组

```xml
<actionGroup id="row-more-button" label="更多">
    <action id="row-user-button" label="用户">
        <drawer page="role-users.page.yaml" noActions="true"/>
    </action>
    <action id="row-auth-button" label="授权">
        <drawer page="assign-auth.page.yaml" noActions="true"/>
    </action>
</actionGroup>
```

### 操作属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `actionType` | string | 操作类型：ajax/dialog/drawer/link/submit |
| `label` | string | 按钮文本 |
| `level` | string | 按钮级别：primary/secondary/info等 |
| `batch` | boolean | 批量操作 |
| `close` | string | 操作后关闭的窗口 |
| `reload` | string | 操作后刷新的组件 |
| `confirm` | string | 确认提示 |
| `size` | string | 弹框大小：sm/md/lg/xl/full |

## 常见场景实现

### 1. 左侧树形过滤

```xml
<form id="asideFilter" submitOnChange="true">
    <layout>
        ==dept[部门]==
        !deptId
    </layout>
    <cells>
        <cell id="deptId">
            <gen-control>
                <input-tree
                    source="@query:NopAuthDept__findList/value:id,label:deptName,children @TreeChildren(max:5)?filter_parentId=__null"/>
            </gen-control>
        </cell>
    </cells>
</form>

<crud name="main" grid="list" asideFilterForm="asideFilter" filterForm="query"/>
```

### 2. Tab页组织表单

```xml
<form id="edit" layoutControl="tabs" size="lg">
    <layout>
        ========== intro[商品介绍] ================
        goodsSn[商品编号] name[商品名称]

        =========specs[商品规格]=======
        !specifications

        =========attrs[商品参数]========
        !attributes
    </layout>
</form>
```

### 3. 弹出对话框操作

```xml
<crud name="role-users" grid="simple-list">
    <listActions>
        <action id="select-user-button" label="选择用户">
            <dialog page="select-role-users" size="md" noActions="true"/>
        </action>
    </listActions>
</crud>

<crud name="select-role-users" grid="pick-list" title="选择用户">
    <listActions>
        <action id="batch-add-user" label="提交" level="primary"
                batch="true" close="select-role-users" reload="role-users-grid">
            <api url="@mutation:NopAuthRole__addRoleUsers">
                <data>
                    <roleId>$roleId</roleId>
                    <userIds>$ids</userIds>
                </data>
            </api>
        </action>
    </listActions>
</crud>
```

### 4. 隐藏操作列

```xml
<crud name="main">
    <table noOperations="true"/>
</crud>
```

### 5. 自定义查询条件

```xml
<form id="query" editMode="query" title="查询条件">
    <layout>
        userName nickName phone status
    </layout>
</form>

<crud name="main" grid="list" filterForm="query"/>
```

### 6. URL指定查询和排序

```xml
<api url="@query:NopAuthUser__findPage?filter_userStatus=1&amp;orderField=userName&amp;orderDir=asc"/>
```

### 7. 只在前台使用的字段

```xml
<cell id="__useImportFile" label="导入模型文件" custom="true" stdDomain="boolean"/>
```

### 8. 条件启用按钮

```xml
<rowActions x:override="bounded-merge">
    <action id="row-update-button"/>
    <action id="row-delete-button" feature:on="!$status eq 'DELETED'"/>
</rowActions>
```

## 元模型参考

### xview.xdef位置

`/nop/schema/xui/xview.xdef`

### Grid.xdef位置

`/nop/schema/xui/grid.xdef`

### Form.xdef位置

`/nop/schema/xui/form.xdef`

### 相关XDef

| 文件 | 路径 | 说明 |
|------|------|------|
| `xview.xdef` | `/nop/schema/xui/` | View模型定义 |
| `grid.xdef` | `/nop/schema/xui/` | Grid表格定义 |
| `form.xdef` | `/nop/schema/xui/` | Form表单定义 |
| `page.xdef` | `/nop/schema/xui/` | Page页面定义 |
| `action.xdef` | `/nop/schema/xui/` | Action操作定义 |
| `api.xdef` | `/nop/schema/xui/` | API定义 |
| `disp.xdef` | `/nop/schema/xui/` | 显示属性定义 |

## Delta定制机制

### x:extends继承

```xml
<view x:extends="_gen/_LitemallGoods.view.xml">
    <!-- 继承并修改 -->
    <grids>
        <grid id="list">
            <cols x:override="bounded-merge">
                <!-- 只保留这些列 -->
                <col id="id"/>
                <col id="name"/>
            </cols>
        </grid>
    </grids>
</view>
```

### x:prototype继承

```xml
<forms>
    <form id="edit">
        <layout>
            userName[用户名] nickName[昵称]
        </layout>
    </form>

    <!-- 从edit继承layout -->
    <form id="add" editMode="add" x:prototype="edit">
        <!-- 不需要重复layout -->
    </form>
</forms>
```

### x:override选项

| 选项 | 说明 |
|------|------|
| `merge` | 合并（默认） |
| `bounded-merge` | 限制范围合并，删除多余节点 |
| `remove` | 删除节点 |
| `replace` | 完全覆盖 |
| `merge-replace` | 合并后覆盖指定节点 |

### x:abstract抽象节点

```xml
<!-- 生成的基类文件 -->
<form id="edit" x:abstract="true">
    <layout>...</layout>
</form>

<!-- 派生文件中需要明确声明才能保留 -->
<form id="edit">
    <!-- 可以修改或使用默认的layout -->
</form>
```

## 代码生成与Delta

### 生成流程

1. **代码生成器**：根据XMeta生成`_Xxx.view.xml`
2. **Delta定制**：创建`Xxx.view.xml`，`x:extends="_gen/Xxx.view.xml"`
3. **合并运行**：自动合并基类和Delta，生成最终视图

### 示例：LitemallGoods的Delta

```xml
<!-- LitemallGoods.view.xml -->
<view x:extends="_gen/_LitemallGoods.view.xml">
    <grids>
        <grid id="list">
            <cols x:override="bounded-merge">
                <!-- 只显示需要的列 -->
                <col id="id"/>
                <col id="name" label="商品名称"/>
                <col id="retailPrice" label="零售价格"/>
            </cols>
        </grid>
    </grids>

    <forms>
        <form id="edit" size="lg">
            <layout>
                goodsSn[商品编号] name[商品名称]
                =========products[商品库存]=======
                !products
            </layout>
        </form>

        <form id="add" x:prototype="edit">
            <layout x:override="remove"/>
        </form>
    </forms>

    <pages>
        <crud name="main">
            <rowActions x:override="bounded-merge">
                <action id="row-update-button" actionType="drawer"/>
                <action id="row-delete-button"/>
            </rowActions>
        </crud>
    </pages>
</view>
```

## 常见问题

### 1. CRUD行操作按钮默认会刷新表格

解决：设置`reload="none"`

```xml
<action id="test-ajax" actionType="ajax" reload="none">
    <api url="@query:NopAuthDept__get?id=$id"/>
</action>
```

### 2. 子表数据需要手动指定selection

```xml
<cell id="attributes">
    <view path="/app/mall/pages/LitemallGoods/attributes.page.yaml"/>
    <!-- 需要指定GraphQL查询字段 -->
    <selection>id,attribute,value</selection>
</cell>
```

### 3. 条件查询默认使用eq算符

在XMeta中修改：

```xml
<prop name="userName" allowFilterOp="eq,contains" xui:defaultFilterOp="contains"/>
```

### 4. 弹出对话框传递固定参数

```xml
<action id="row-edit-rule-nodes" label="规则节点" actionType="drawer">
    <dialog page="/nop/rule/pages/NopRuleNode/ref-ruleDefinition.page.yaml">
        <data>
            <ruleId>$ruleId</ruleId>
        </data>
    </dialog>
</action>
```

### 5. 使用input-table编辑内联表格

```xml
<cell id="specifications">
    <gen-control>
        <input-table addable="@:true" editable="@:true" removable="@:true">
            <columns j:list="true">
                <input-text name="specification" label="规格名" required="true"/>
                <input-text name="value" label="规格值" required="true"/>
            </columns>
        </input-table>
    </gen-control>
    <selection>id,specification,value,picUrl</selection>
</cell>
```

## 相关文档

- [前台开发指南](./frontend-development.md) - AMIS框架使用
- [GraphQL服务开发指南](./api-development.md) - GraphQL API开发
- [XDef核心概念](../05-xlang/xdef-core.md) - XDef元模型
- [Delta定制基础](../01-core-concepts/delta-basics.md) - Delta机制详解
- [Delta定制场景](../01-core-concepts/delta-scenarios.md) - Delta实际应用

## 源码参考

- **XView模型定义**：`io.nop.xui.model.UiViewModel`
- **Grid模型**：`io.nop.xui.model.UiGridModel`
- **Form模型**：`io.nop.xui.model.UiFormModel`
- **Page模型**：`io.nop.xui.model.UiPageModel`
- **生成器**：`io.nop.xui.generator.XuiGenerator`
