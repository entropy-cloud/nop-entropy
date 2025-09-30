# Layout Language

In the Nop platform, we use NopLayout, a domain-specific language dedicated to form layouts, to separate form layout information from (field-level) content information. For example, for the following form layout

```xml
 <form id="add" editMode="add" title="新增-用户" i18n-en:title="Add User">
  <layout>
    ============>baseInfo[基本信息]======
    userName[用户名] status[用户状态]
    nickName[昵称]
    deptId[部门]

    ===========^extInfo[扩展信息]=========
    idType[证件类型] idNbr[证件号]
    birthday[生日] workNo[工号]
    positionId[职务]
    remark[备注]
  </layout>
</form>
```

This renders as

![](layout/group-layout.png)

## I. Layout Syntax

NopLayout defines the following rules:

1. Specify fields displayed per line; for example, `a b` means this line displays fields `a` and `b`.

2. Use `fieldName[displayName]` to specify the field name and its display label.

3. Mark a group with `===groupName[groupLabel]===`.

4. If a group is given a label, it is rendered with the `FieldSet` widget; use `^` to indicate a collapsed group and `>` for an expanded group.

5. Prefix a field with `@` to mark it as read-only; the frontend will use a view-mode widget.

6. Prefix a field with `!` to hide its label.

For example, to add department-based filtering on the left side of the user list, you essentially introduce a filter form in the sidebar

```xml
<form id="asideFilter" submitOnChange="true" editMode="query">
  <layout>
    ==dept[部门]==
    !deptId
  </layout>
  <cells>
    <cell id="deptId">
      <gen-control>
        <input-tree
           source="@query:NopAuthDept__findList/value:id,label:deptName,
              children @TreeChildren(max:5)?filter_parentId=__null"/>
      </gen-control>
    </cell>
  </cells>
</form>
```

The `deptId` field is prefixed with `!`, meaning its label is hidden, resulting in

![](layout/aside-filter.png)

## II. Field Control Inference

In most cases, field-level display controls can be inferred from the field type and data domain defined in the data model.

For example, for the `gender` field, we specify that it uses the `auth/gender` dictionary, so its values must come from that dictionary. For the `email` field, its domain is set to `email`, requiring input to conform to the email format.

When inferring the control to use, we first determine the field’s editing mode (usually the same as the form’s `editMode`, but it can be specified per field), then search the control tag library `control.xlib` in the following order, taking the first match:

1. Look for a control named `{editMode}-{control}`, where `control` is explicitly specified via the `control` attribute in the `cell` configuration.

2. Look for `{editMode}-{domain}` controls, e.g., `<edit-email/>`.

3. If a dictionary is configured, look for a control named `{editMode}-enum`.

4. Look for `{editMode}-{stdDomain}` controls. `stdDomain` refers to globally registered standard data domains, which refine primitive data types—for example, `stdDomain=xml` denotes a text field whose content is XML.

5. If it’s a foreign-key association, look for a control named `{editMode}-to-one` and try an associated-object picker.

6. Look for `{editMode}-{dataType}` controls, such as `<edit-string/>` and `<edit-double/>`.

7. If `editMode` is neither `edit` nor `view`, restart the search from step 1 using `edit` mode.

8. If in `view` mode, use `<view-any/>`; otherwise, use `<edit-any/>`.

editMode includes `add/view/update/edit/query`, among others, and is used to distinguish different usage contexts for a field. Different contexts may use different controls—for example, a single-select control in edit mode versus a multi-select query control in query mode—yet the same field name can be used in the form layout.

**Field control inference is an extensible mechanism.** When recurring field-level semantics emerge in business, we can assign a distinct domain to them in the data model; the frontend will then automatically render all fields marked with that domain using a uniform control. The steps are:

1. Define the `domain` in the data model and assign it to the fields.

2. Add control implementations for different editing modes in `control.xlib` (for example, for `domain=roleId`, you can provide `<edit-roleId/>`, `<view-roleId/>`, and `<query-roleId/>`). Alternatively, without modifying `control.xlib` directly, you can extend the built-in `control.xlib` via the Delta customization mechanism.

The processing of user-defined domains is identical to that of built-in domains.

If a control is specialized and doesn’t warrant abstraction into a unified domain, you can directly specify the display control used in the form, for example

```xml
<form id="edit">
   <layout>
      ...
   </layout>
   <cells>
      <cell id="fldA">
         <gen-control>
            这里输出具体使用的控件描述
         </gen-control>
      </cell>
   </cells>
</form>
```

`gen-control` executes the XPL template language to generate a JSON control description.

## III. Field Interactions

Beyond layout information, you can specify inter-field interactions via supplemental `cell` configuration. For example

```xml
<form id="default" >
  <layout>
    sid[资源ID] siteId[站点ID]
    displayName[显示名称] orderNo[排序]
    resourceType[资源类型] parentId[父资源ID]
    =====menuProps=============
    icon[图标] routePath[前端路由]
    url[链接] component[组件名]
    target[链接目标] hidden[是否隐藏]
    keepAlive[隐藏时保持状态] noAuth[不检查权限]
    depends[依赖资源]
    =====authProps============
    permissions[权限标识]
    =====otherProps===========
    status[状态] remark[备注]
  </layout>

  <cells>
    <cell id="parentId">
      <requiredOn>${resourceType != 'TOPM'}</requiredOn>
    </cell>
    <cell id="menuProps">
      <visibleOn>${resourceType != 'FNPT'}</visibleOn>
    </cell>
    <!--
     If a field group has no label, it only acts as a group and will not be displayed as a FieldSet
    -->
    <cell id="authProps">
      <visibleOn>${resourceType == 'FNPT'}</visibleOn>
    </cell>
  </cells>
</form>
```

`cell` can target not only individual fields but also field groups, allowing you to set show/hide conditions for a group. In the example above, `menuProps` corresponds to a group and is shown only when `resourceType != 'FNPT'`.

## IV. Prototype Inheritance

In information systems, it’s common for the layouts of create, update, and view forms to be largely or even entirely identical, while the controls used for update and view differ. Conventional techniques struggle to achieve reuse in such partially similar scenarios, but in the Nop platform, leveraging the principles of Reversible Computation, we provide a standardized solution:

1. **Abstract shared, potentially reusable information into a DSL syntax node**

2. **Use the built-in `x:prototype` mechanism in XLang to construct a node from a sibling node as its template**

3. **Apply Delta customization via `x:prototype-override` to modify the template**

Applied to frontend forms, the approach is:

1. Abstract the form layout into a `<form/>` node

2. Define `<form id="default" x:abstract="true"/>` as the template; set `x:prototype` of the view/edit forms to `default`

3. Adjust the display of specific fields via `<cell/>` nodes

```xml
<form id="default" x:abstract="true">
   <layout>
      ....
   </layout>
   <cells>
     <cell id="xx" >
        <visibleOn>yyy</visibleOn>
     </cell>
   </cells>
</form>

<form id="view" x:prototype="default" editMode="view">
</form>

<form id="edit" x:prototype="default" editMode="edit">
</form>
```

`x:abstract` is a special attribute defined by XLang; when set to `true`, the node serves only as a template and will be automatically removed from the final merged output, similar to the `abstract` attribute in Spring XML configuration.

## V. Extended Layouts

The NopLayout language can also support more complex layout structures. For example, it supports multi-level nested layouts

```xml
<form>
  <layout>
      ====#sub1===
      ====##sub1_1===
       a b
      ====##sub1_2===
       c d
  </layout>
</form>
```

In group markers, `#` denotes nesting levels, similar to Markdown; multiple `#` indicate the depth. `##` represents a child of `#`.
<!-- SOURCE_MD5:e7ca797ac4969c220cfd8cc3c0839d68-->
