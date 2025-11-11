# XView View Model

## Functional Design

XView is a frontend UI description that is framework-agnostic and oriented toward business domains. It expresses the core interaction logic of the frontend using a small set of concepts such as page, grid, form, and action. The final frontend page `page.yaml` can use the `x:gen-extends` metaprogramming mechanism to dynamically generate AMIS pages based on the xview model.

The XView model decomposes the construction of the frontend UI into field-level, form/grid-level, and page-level.

1. `control.xlib` infers the display control for a single field according to data type, data domain, and edit mode.
2. The form’s layout model controls how the page is laid out. You can adjust the page layout without changing the field controls.
3. When constructing pages, you can directly reference already defined forms and grids.

### Standard CRUD Pages

Generally, the `xxx-web` module will execute the code generator under the precompile directory during Maven packaging to generate web frontend code based on xmeta. For example, in `nop-auth-web/precompile/gen-page.xgen`:

```xml
<c:script>
// Generate page files view.xml and page.yaml based on xmeta
codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/auth" },$scope);
</c:script>
```

For example, the `_NopAuthUser.view.xml` model generated based on the `NopAuthUser.xmeta` model:

```xml
<view ...>
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>

    <controlLib>/nop/web/xlib/control.xlib</controlLib>

    <grids>
        <grid id="list" x:abstract="true">
            <cols>

                <!-- Username -->
                <col id="userName" mandatory="true" sortable="true"/>
                ..
                <!-- Birthday -->
                <col id="birthday" sortable="true" x:abstract="true"/>
            </cols>
        </grid>
        <grid id="pick-list" x:prototype="list" x:abstract="true"/>
    </grids>

    <forms>
        <form id="view" editMode="view" title="View - User" i18n-en:title="View User">
            <layout>
 userName[Username] nickName[Nickname]
 deptId[Department] openId[External User Identifier]
 ...
</layout>
        </form>
        <form id="add" editMode="add" title="Add - User" i18n-en:title="Add User" x:prototype="edit"/>
        <form id="edit" editMode="update" title="Edit - User" i18n-en:title="Edit User">
            <layout>...</layout>
        </form>
        <form id="query" editMode="query" title="Query Condition" i18n-en:title="Query Condition" x:abstract="true">
            <layout/>
        </form>
        <form id="asideFilter" editMode="query" x:abstract="true" submitOnChange="true">
            <layout/>
        </form>
        <form id="batchUpdate" editMode="update" x:abstract="true" title="Update - User" i18n-en:title="Update User">
            <layout/>
        </form>
    </forms>

    <pages>
        <crud name="main" grid="list" asideFilterForm="asideFilter" filterForm="query" x:abstract="true">
            ...
        </crud>
        <picker name="picker" grid="pick-list" asideFilterForm="asideFilter" filterForm="query" x:abstract="true">
            ...
        </picker>
        <simple name="add" form="add">
            <api url="@mutation:NopAuthUser__save/id"/>
        </simple>
        <simple name="view" form="view">
            <api url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
        </simple>
        <simple name="update" form="edit">
            <initApi url="@query:NopAuthUser__get/{@formSelection}?id=$id"/>
            <api url="@mutation:NopAuthUser__update/id?id=$id" withFormData="true"/>
        </simple>
    </pages>
</view>
```

Many generated nodes are marked with `x:abstract="true"`, indicating the node is virtual. It will only be retained if explicitly declared in the derived model. This design is similar to the abstract attribute in Spring Beans XML—`x:abstract` indicates the node exists as a template.

1. `objMeta` indicates that the current xview model will use the field configuration from the specified XMeta file.
2. `controlLib` controls how field types map to specific frontend controls and generally does not need to be modified. However, if we need to display different components for Mobile compared to standard Web pages, we can specify a control library for Mobile.
3. By default, alternative grids `list` and `pick-list` are generated. `pick-list` is used for pop-up selection list pages. The attribute `x:prototype="list"` on `pick-list` indicates that `pick-list` is generated based on the structure of its sibling `list`, i.e., the selection list page is the same as the regular list page. You can customize `pick-list` in a derived xview model to tailor the selection list page.
4. Similar to the relationship between `pick-list` and `list`, the add form `add` inherits from `edit` by default, meaning unless specially customized, the layout of the add page is the same as the edit page. Each form has its own `editMode`, allowing the same field to be displayed using different controls when adding, modifying, selecting, or viewing.
5. The `main` page sets `filterForm="query"` and `asiderFilterForm="asideFilter"`. This means the `query` form will be used as the query condition form on the `main` page. If the `asideFilter` form is configured, part of the query conditions will be displayed in a left side sidebar on the `main` page.

### Basic Grid Configuration

For specific configuration options, see the [grid.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/grid.xdef) meta-model definition.

#### 1. Control which fields the list displays, and their order

```xml
<grid id="list">
   <cols x:override="bounded-merge">
      <col id="fieldA">
      </col>

      <col id="fieldB">
      </col>
   </cols>
</grid>
```

`x:override="bounded-merge"` indicates that the scope of the `cols` child nodes is limited to the range specified here. Extra fields defined in the inherited base model will be automatically removed. If `x:override` is not specified, the default is `merge` mode—the result adds and modifies fields relative to the base model, unless you explicitly specify `x:override="remove"` to delete fields.

#### 2. Specify the column header, width, alignment, etc.

```xml

<col width="100px" align="right" id="fieldA" label="My Field" />

```

#### 3. Specify explicit control

By default, the display control for grid fields is determined by the field type and the `editMode` specified on the grid. The specific controls used are defined in [control.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/control.xlib).

If you need to explicitly specify the display control, use `gen-control`:

```xml
<col id="fieldA">
  <gen-control>
    <c:script>
       return {
         'type': 'my-control'
       }
    </c:script>
  </gen-control>
</col>
```

### Basic Form Configuration

For the DSL used for form layouts, see [layout.md](layout.md).

Form configuration options are defined in the [form.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/form.xdef) meta-model.

## Common Feature Configurations

### 1. Display filter conditions as a tree on the left

For example, in the user management page corresponding to `NopAuthUser`, there is a unit tree on the left. Clicking a unit filters the user list on the right by unit. The existing query conditions in the user list and the unit tree’s query conditions are merged together and sent to the backend.

```xml
<form id="asideFilter" submitOnChange="true">
    <layout>
        ==dept[Department]==
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
```

You only need to add a form with `id="asideFilter"`. `submitOnChange` means a query is submitted immediately upon clicking.

### 2. List data has a nested parent-child relationship

For example, a unit tree where `Parent Unit - Child Unit` forms a tree structure.

As required by the AMIS frontend component, as long as the backend returns data with a `children` field, it will automatically expand as a tree. The Nop platform adds a Tree structure extension for GraphQL, making it easy to specify recursive tree data retrieval via the `@TreeChildren` directive.

```yaml
url: "@query:NopAuthDept__findList/value:id,label:deptName,children @TreeChildren(max:5)?filter_parentId=__null"
```

This call invokes the backend function `NopAuthDept__findList`, requesting a `children` field and specifying that up to 5 levels of data are returned recursively via the `@TreeChildren` directive.

`filter_parentId=__null` means filtering for root node lists using the condition `parentId=null`.

### 3. Organize multiple existing pages into a composite page using tabs

See the tabs used for the department management feature in `NopAuthDept.view.xml`:

```xml
    <tabs name="tabsView" tabsMode="vertical" mountOnEnter="true" unmountOnExit="true">
        <tab name="main" page="main" title="@i18n:common.treeView"/>
        <tab name="list" page="list" title="@i18n:common.listView"/>
    </tabs>
```

### 4. Add multiple query conditions for the list page

As shown in `NopAuthUser.view.xml`, just add fields to the form with `id="query"`. The default generated CRUD page references the `query` form via the `filterForm` attribute.

```xml
<form id="query">
    <layout>
        userName gender nickName phone status
    </layout>
</form>

<pages>
  <crud filterForm="query" ... >
</pages>
```

By default, fields are queried using the equality condition. You can customize property settings in the `NopAuthUser.xmeta` file to specify query operators.

```xml
<prop name="userName" allowFilterOp="eq,contains" xui:defaultFilterOp="contains"/>
```

This configuration indicates that `userName` allows filtering using the `eq` and `contains` operators. `eq` means equality; `contains` means substring containment, implemented via `like`. `xui:defaultFilterOp` indicates that the default filter operator is `contains`.

All supported filter operators are defined in the `FilterOp.java` class. Common ones include `eq`, `ne`, `gt`, `ge`, `lt`, `le`, `contains`, `in`, `startsWith`, and `endsWith`.

### 5. The list page has no row action buttons; hide the operations column

```xml
 <crud name="xxx">
    <table noOperations="true" />
 </crud>
```

### 6. Add a page similar to the default CRUD page, but with different query conditions for the list

```xml

        <crud name="list" grid="list" x:prototype="main">
            <table x:prototype-override="replace">
                <api url="@query:NopAuthDept__findPage/{@pageSelection}"/>
            </table>
        </crud>

```

`x:prototype` indicates inheritance from a sibling node. `x:prototype-override="replace"` indicates that the current node overrides the inherited `table` node. By default, nodes are merged (`merge`), not completely replaced.

### 7. Click a button to open a dialog, complete the form, execute a backend operation, close the dialog, and refresh the original page

See [LitemallGoods.view.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master)

```xml
<crud name="role-users" grid="simple-list">
    <listActions>
        <action id="select-user-button" label="@i18n:common.selectUser">
            <dialog page="select-role-users" size="md" noActions="true">
            </dialog>
        </action>
    </listActions>
</crud>

<crud name="select-role-users" grid="pick-list" title="@i18n:common.selectUser">

    <listActions>
        <action id="batch-add-user-button" label="@i18n:common.submit" level="primary"
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

* If an `action` configuration has a `dialog` child node, it means a pop-up dialog is used for display. The `page` attribute on `dialog` can directly reference a pre-defined page. If it’s a full path, it corresponds to an externally defined complete page; if it’s the name of a `page`, it references the page defined in the current XView model.
* `noActions="true"` on the `dialog` indicates that the dialog’s built-in submit and cancel buttons are not used.
* `batch="true"` on the `action` indicates an operation applied to a batch of selected list items. `close` indicates that the current window will close after the operation completes. `reload` indicates reloading the specified grid by name, i.e., the CRUD grid in the `role-users` page.

### 8. Submit child table data together with the main table

```xml
<form id="edit">
<cells>
  <cell id="products">
        <!-- You can reference a grid in an external view model to display the child table -->
        <view path="/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml"
              grid="ref-edit"/>
    </cell>
</cells>
</form>
```

Add a view configuration to the child table property, specifying which grid edits the child table data. The XView model automatically analyzes the view configuration, extracts the grid’s field list, and merges it into the GraphQL request corresponding to the current form.

### 9. Customize row action buttons on the list

```xml
<crud name="main">
    <!-- bounded-merge means the merge result is limited to the scope of the current model.
         Child nodes present in the base model but not in the current model are automatically removed.
         The default generated code already defines row-update-button and row-delete-button with x:abstract=true,
         so you only need to declare the id here to enable the inherited buttons, avoiding duplicate code.
     -->
    <rowActions x:override="bounded-merge">
        <!--
            Use a drawer instead of a dialog to display the edit form
        -->
        <action id="row-update-button" actionType="drawer"/>

        <action id="row-delete-button"/>

    </rowActions>
</crud>
```

### 10. When a form has too much data, display it in tabs

```xml
<form id="view" layoutControl="tabs" >...</form>
```

Configure `layoutControl="tabs"`.

### 11. Click a row button to pop up the CRUD page for a related child table

```xml
<action id="row-edit-rule-nodes" label="@i18n:rule.ruleNodes|规则节点" actionType="drawer">
    <dialog page="/nop/rule/pages/NopRuleNode/ref-ruleDefinition.page.yaml" size="xl">
        <data>
            <ruleId>$ruleId</ruleId>
            <ruleDefinition>
                <displayName>$displayName</displayName>
            </ruleDefinition>
        </data>
    </dialog>
</action>
```

When popping up the dialog, use the `data` section to specify which field values are fixed in the pop-up page.

> When the `ruleId` field is converted to a view control for display, it needs to use `ruleDefinition.displayName` as the display text, so we need to pass this value.

In the pop-up page file `ref-ruleDefinition.page.yaml`, we can reference an existing CRUD page and use `fixedProps` to specify which fields have fixed values and are not editable.

```yaml
x:gen-extends: |
  <web:GenPage view="NopRuleNode.view.xml" page="main" fixedProps="ruleId" xpl:lib="/nop/web/xlib/web.xlib" />
```

### 12. Use the Combo component to display recursive data structures

See the configuration of `ruleInputs` in `NopRuleDefinition.view.xml`:

```xml
<cell id="ruleInputs">
    <gen-control>
        return { "$ref": "viewInputDefinition" }
    </gen-control>
</cell>
```

Introduce `definitions` in the `page.yaml` file:

```yaml
x:gen-extends: |
    <web:GenPage view="NopRuleDefinition.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

definitions:
    "x:extends": "var-definitions.json5"
```

### 13. Add a field used only on the frontend; its value will not be submitted to the backend

`custom="true"` indicates that this field does not need to be defined in meta. A double underscore prefix indicates that the field is used only on the frontend and will not be submitted to the backend.

```xml
    <cell id="__useImportFile" label="Import Model File" custom="true" stdDomain="boolean">
    </cell>
```

### 14. Specify query and sort conditions via the URL

```xml
<api url="@query:NopAuthUser__findList?filter_userStatus=1&amp;orderField=userName&amp;orderDir=asc" />
```

Sorting conditions are specified via `orderField={fieldName}&orderDir={asc|desc}`. An array format can also be passed.

## Troubleshooting

### 1. Executing an AJAX call from a CRUD row button triggers a table reload by default

```xml
        <crud name="main">
            <rowActions>
                <action id="test_ajax" level="primary" label="nop test ajax"
                        actionType="ajax" reload="none">
                    <api url="@query:NopAuthDept__get?id=$id" gql:selection="managerId"/>
                </action>
            </rowActions>
        </crud>
```
You can set `reload="none"` to disable this behavior.

### 2. How to pass parameters to a referenced subpage

```xml
<form id="rowView" editMode="view" title="View Contract" size="lg">
  <layout>
  !@contractId
  </layout>
  <cells>
    <cell id="contractId">
        <view path="/app/demo/pages/ContractMain/detail.page.yaml" />
    </cell>
  </cells>
  <data>
    <id>$contractId</id>
  </data>
</form>
```

* AMIS subpages can directly access variables in the parent scope, so setting the form’s data causes each form control to see the corresponding variables.

Alternatively, you can achieve this by customizing the view:

```xml
    <cell id="contractId">
        <view path="/app/demo/pages/ContractMain/ContractMain.view.xml" page="viewContract"/>
    </cell>
```

* Add a page definition to the specified view.xml model file, then use Delta customization to inherit existing pages and customize the `initApi` configuration. This is customization at the XView model level.

You can also include `page.yaml` via `view`, then inherit an existing `page.yaml` and customize at the AMIS level.

## default-query Configuration
If the meta has a `default-query` tag, all fields that are visible and !internal and queriable and whose ui:show does not contain Q will be automatically collected into the query form, using AMIS’s autoGenerateFilter mechanism to implement the frontend query form.

<!-- SOURCE_MD5:5da0f55b77f76fb67ffdd8fc50435d02-->
