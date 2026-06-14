# 外部应用页面 DSL 示例

本页收录外部应用项目（如 `nop-app-mall`）中真实存在的页面 DSL 模式片段，供 `page-dsl-pattern-catalog.md` 和 `view-and-page-customization.md` 引用。

示例均来自外部应用仓库，展示的是 **保留层定制** 的典型写法，不是平台内置模块。

---

## 1. `bounded-merge` — 只保留指定列

**场景**：继承了生成基线的完整 grid，但只想保留少量列。

```xml
<view x:extends="_gen/_LitemallGoods.view.xml" ...>
  <grids>
    <grid id="list">
      <cols x:override="bounded-merge">
        <col id="id"/>
        <col id="name"/>
        <col id="picUrl"/>
        <col id="detail" label="详情">
          <gen-control>
            <wrapper>
              <body>
                <button label="查看" actionType="dialog" level="primary">
                  <dialog title="商品详情" size="md">
                    <body>
                      <tpl tpl="${'$'}{detail|raw}"/>
                    </body>
                  </dialog>
                </button>
              </body>
            </wrapper>
          </gen-control>
        </col>
        <col id="counterPrice"/>
        <col id="retailPrice"/>
      </cols>
    </grid>
  </grids>
</view>
```

**要点**：`x:override="bounded-merge"` 表示继承基础模型的配置，但仅保留当前显式列出的 `col`。基础模型中有、当前模型中没有的子节点会被自动删除。

---

## 2. `x:prototype` — 克隆 grid 并覆盖 filter

**场景**：基于同一个 `list` grid 派生多个状态分组列表，每个列表只改 filter。

```xml
<grids>
  <grid id="list"/>
  <grid id="wait-approve-list" x:prototype="list">
    <filter>
      <eq name="status" value="#{AppMallDaoConstants.AFTERSALE_STATUS_REQUEST}"/>
    </filter>
  </grid>
  <grid id="wait-refund-list" x:prototype="list">
    <filter>
      <eq name="status" value="#{AppMallDaoConstants.AFTERSALE_STATUS_APPROVED}"/>
    </filter>
  </grid>
</grids>
```

**要点**：`x:prototype="list"` 从 `list` grid 克隆全部配置，再用局部覆盖（如 `<filter>`）实现差异。不需要复制整份 grid 定义。

---

## 3. `x:prototype-override` — 克隆 crud 并局部覆盖 action

**场景**：基于同一个 `main` crud 派生不同状态的 crud 页面，每个页面有自己的批量操作按钮。

```xml
<crud name="wait-approve" x:prototype="main" grid="wait-approve-list">
  <listActions x:prototype-override="bounded-merge">
    <action id="batch-approve-button" label="批准" batch="true" level="primary">
      <api url="@mutation:LitemallAftersale__batchApprove">
        <data><ids>$ids</ids></data>
      </api>
    </action>
    <action id="batch-reject-button" label="拒绝" batch="true" level="danger">
      <api url="@mutation:LitemallAftersale__batchReject">
        <data><ids>$ids</ids></data>
      </api>
    </action>
  </listActions>
  <rowActions x:prototype-override="bounded-merge">
    <action id="row-view-button"/>
  </rowActions>
</crud>

<crud name="wait-refund" x:prototype="main" grid="wait-refund-list">
  <listActions x:prototype-override="remove"/>
  <rowActions x:prototype-override="bounded-merge">
    <action id="row-view-button"/>
    <action id="refund-button" label="退款" batch="true" level="primary">
      <api url="@mutation:LitemallAftersale__refund">
        <data><id>$id</id></data>
      </api>
      <confirmText>确认退款吗？</confirmText>
    </action>
  </rowActions>
</crud>
```

**要点**：
- `x:prototype="main"` 从 `main` crud 克隆。
- `x:prototype-override="bounded-merge"` 只保留显式列出的 action。
- `x:prototype-override="remove"` 删除所有继承的 action。

---

## 4. `tabs` — 多状态工作台组装

**场景**：把多个 crud 页面组装成一个带 tab 切换的工作台。

```xml
<tabs name="all" mountOnEnter="true" unmountOnExit="true">
  <tab name="main" title="全部"/>
  <tab name="wait-approve" title="待审批"/>
  <tab name="wait-refund" title="待退款"/>
</tabs>
```

**要点**：`tab name` 对应同级 `<crud name="...">`。`mountOnEnter="true"` 表示切到该 tab 时才挂载子页面，避免一次性加载所有列表。

---

## 5. `gen-control` — 服务端动态控件

**场景**：列表列中嵌入一个按钮，点击后弹出对话框显示富文本详情。

```xml
<col id="detail" label="详情">
  <gen-control>
    <wrapper>
      <body>
        <button label="查看" actionType="dialog" level="primary">
          <dialog title="商品详情" size="md">
            <body>
              <tpl tpl="${'$'}{detail|raw}"/>
            </body>
          </dialog>
        </button>
      </body>
    </wrapper>
  </gen-control>
</col>
```

**要点**：`gen-control` 中的内容在服务端动态生成。`${'$'}{detail|raw}` 在服务端解析后生成前端的 `${detail|raw}`。用 `<wrapper>` 包裹是为了避免 button 的 label 覆盖 column 的 label。

---

## 6. `gen-control` — input-table 内联子表

**场景**：在表单中用 `input-table` 内联编辑子表数据，不引用外部 view。

```xml
<cell id="specifications">
  <gen-control>
    <input-table addable="@:true" editable="@:true"
                 removable="@:true" needConfirm="@:false">
      <columns j:list="true">
        <input-text name="specification" label="规格名" required="true"/>
        <input-text name="value" label="规格值" required="true"/>
        <input-text name="picUrl" label="图片" required="true"/>
      </columns>
    </input-table>
  </gen-control>
  <selection>id,specification,value,picUrl</selection>
</cell>
```

**要点**：`input-table` 的 `columns` 用 `j:list="true"` 表示这是 JSON 数组格式的列定义。`<selection>` 控制提交到后台的字段。

---

## 7. 外部子表 view 引用

**场景**：父表单中引用外部子表的 view 文件来编辑子表，不内联子表 DSL。

```xml
<cell id="products">
  <view path="/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml"
        grid="ref-edit"/>
</cell>
```

**要点**：`path` 指向子表的 view 文件，`grid="ref-edit"` 表示使用子表 view 中定义的 `ref-edit` grid 来渲染子表编辑界面。

---

## 8. 外部 page.yaml 片段引用

**场景**：把一段 `input-table` 配置抽成独立的 page.yaml 文件，再从 view.xml 中引用。

外部片段文件 `attributes.page.yaml`：

```yaml
type: input-table
addable: true
editable: true
removable: true
needConfirm: false
columns:
  - type: input-text
    name: attribute
    label: 参数名
  - type: input-text
    name: value
    label: 参数值
```

引用方式：

```xml
<cell id="attributes">
  <view path="/app/mall/pages/LitemallGoods/attributes.page.yaml"/>
  <selection>id, attribute, value</selection>
</cell>
```

**要点**：把重复使用的 `input-table` 配置抽成独立文件，避免在多个 view 中复制相同 DSL。

---

## 9. 薄 `page.yaml` wrapper

**场景**：已有完整 view.xml，只需要一个独立页面入口。

`add.page.yaml`：

```yaml
x:gen-extends: |
  <web:GenPage view="LitemallGoods.view.xml" page="add" xpl:lib="/nop/web/xlib/web.xlib" />
title: '@i18n:LitemallGoods.forms.add.$title|新增-商品'
```

`main.page.yaml`：

```yaml
x:gen-extends: |
  <web:GenPage view="LitemallBrand.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
asideResizor: false
style:
  boxShadow: ' 0px 0px 0px 0px transparent'
```

**要点**：
- `x:gen-extends` 引用 view 中已定义的 page（如 `page="add"`），自动生成页面骨架。
- `page.yaml` 只做 page 级包装（title、style、aside 等），不重复定义 grid/form。
- 不要因为要改一点 page 级样式，就回头复制整份 `view.xml`。

---

## 10. Delta 覆盖平台页面 + `feature:on` 条件布局

**场景**：外部应用通过 Delta 覆盖平台内置的用户管理页面，用 feature flag 切换两套布局。

```xml
<view x:extends="super" x:schema="/nop/schema/xui/xview.xdef"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:feature="feature">
  <forms>
    <form id="view" feature:on="!nop.auth.use-ext-info">
      <layout>
        ===========>baseInfo[基本信息]======
        userName status[用户状态]
        nickName[昵称] deptId[部门]
        userType[用户类型] gender[性别]
        email[邮件] phone[电话]
        expireAt[用户过期时间] changePwdAtLogin[登陆后立刻修改密码]
      </layout>
    </form>
    <form id="view" feature:on="nop.auth.use-ext-info">
      <layout>
        ===========>baseInfo[基本信息]======
        userName status[用户状态]
        nickName[昵称] deptId[部门]
        userType[用户类型] gender[性别]
        email[邮件] phone[电话]
        expireAt[用户过期时间] changePwdAtLogin[登陆后立刻修改密码]
        ===========>extInfo[扩展信息]=========
        idType[证件类型] idNbr[证件号]
        birthday[生日] workNo[工号]
        positionId[职务] telephone[座机]
        remark[备注]
        createdBy[创建人] createTime[创建时间]
        updatedBy[修改人] updateTime[更新时间]
      </layout>
    </form>
  </forms>
</view>
```

**要点**：
- `x:extends="super"` 表示继承平台原始 view。
- 两份同 id 的 `<form id="view">` 通过 `feature:on` 条件切换。
- `feature:on="!nop.auth.use-ext-info"` 表示当该 feature 关闭时使用简洁布局。
- `feature:on="nop.auth.use-ext-info"` 表示当该 feature 开启时显示扩展信息。

---

## 11. 树形 CRUD + `add-child` 预填父节点

**场景**：树形区域管理页面，支持查看树、选树、新增子节点。

```xml
<grid id="tree-list" x:prototype="list">
  <selection>children @TreeChildren(max:5)</selection>
</grid>

<crud name="main" grid="tree-list" ...>
  <table loadDataOnce="true" sortable="false" pager="none">
    <api url="@query:LitemallRegion__findList?filter_pid=__null"
         gql:selection="{@listSelection}"/>
  </table>
  <rowActions>
    <actionGroup id="row-more-button" label="@i18n:common.more" level="primary">
      <action id="row-add-child-button" label="@i18n:common.addChild">
        <dialog page="add-child"/>
      </action>
    </actionGroup>
  </rowActions>
</crud>

<simple name="add-child" form="add">
  <api url="@mutation:LitemallRegion__save/id" withFormData="true"/>
  <data>
    <_ j:key="pid">$id</_>
  </data>
</simple>
```

**要点**：
- `<selection>children @TreeChildren(max:5)</selection>` 定义树形层级关系。
- `loadDataOnce="true"` 一次加载全部树数据，前端做本地过滤。
- `filter_pid=__null` 只查根节点。
- `<data><_ j:key="pid">$id</_></data>` 把当前选中节点的 `$id` 作为 `pid` 传给新增子节点表单。

---

## 12. CRUD wiring 速记

**场景**：标准 CRUD 页面的取数、初始化、提交 wiring。

```xml
<!-- 列表取数 -->
<crud name="main" grid="tree-list" ...>
  <table loadDataOnce="true" sortable="false" pager="none">
    <api url="@query:LitemallRegion__findList?filter_pid=__null"
         gql:selection="{@listSelection}"/>
  </table>
</crud>

<!-- 新增 -->
<simple name="add" form="add">
  <api url="@mutation:LitemallRegion__save/id"/>
</simple>

<!-- 查看 -->
<simple name="view" form="view">
  <initApi url="@query:LitemallRegion__get?id=$id"
           gql:selection="{@formSelection}"/>
</simple>

<!-- 编辑 -->
<simple name="update" form="edit">
  <initApi url="@query:LitemallRegion__get?id=$id"
           gql:selection="{@formSelection}"/>
  <api url="@mutation:LitemallRegion__update/id?id=$id"
       withFormData="true"/>
</simple>
```

**要点**：
- 列表：`@query:...__findPage` 或 `__findList`，配合 `gql:selection="{@listSelection}"`。
- 查看/编辑：`@query:...__get?id=$id` 初始化表单。
- 新增：`@mutation:...__save/id`。
- 编辑：`@mutation:...__update/id?id=$id`，`withFormData="true"` 提交表单数据。
