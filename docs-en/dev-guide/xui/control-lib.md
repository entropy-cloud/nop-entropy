# According to control.xlib dynamically derived frontend controls

## XView Model
The XView model provides a blueprint of the visual structure that is independent of the implementation technology. In the final `page.yaml` file, we use the following tags based on the XView model to dynamically generate JSON files.

```yaml
x:gen-extends: |
    <web:GenPage view="NopDynFile.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
title: This configuration will override the dynamically generated title configuration
```

* The `page.yaml` file can be manually written. The XView model only provides a dynamic generation aid.
* Dynamic generation occurs in the `x:gen-extends` stage, where it generates a base class for the current page, which can then be modified using Delta correction techniques.

## Control Mapping
The `<web:GenPage>` tag converts the XView model into specific frontend code when generating the page. Additional information from the `control.xlib` tag library is required at this stage to map logical fields to specific frontend controls. By using different `control.xlib` libraries for the same `xview.xml` model, we can produce different results, such as using different control mappings for mobile and web versions.

If the `controlLib` attribute is not configured in the XView model, it defaults to `/nop/web/xlib/control.xlib`.

In `control.xlib`, we can define three types of controls: `edit-userId`, `<view-userId>`, and `<query-userId>` for the userId type. These correspond to different controls depending on whether the user is in edit, view, or query mode.

When searching for a corresponding control for a specific field:
1. Check if the XView model's `form` or `grid` has a configured `control`.
2. Look up `ui:control` in XMeta.
3. Check if the XView model's `form` or `grid` has a configured `domain`.
4. Look up `domain` in XMeta.
5. Check if the XView model's `form` or `grid` has a configured `stdDomain`.
6. Look up `stdDomain` in XMeta.
7. Based on XMeta's configuration, follow the `ext:kind` relationship.
8. Finally, based on XMeta's data type configuration, follow the `stdDataType`.

The `stdDomain` is defined in the `StdDomainRegistry`, which includes types like xpl, xml, csv-set, prop-name, etc.

## Edit Mode
In addition to the above, the XView model allows for configuring an `editMode` on its `form` or `grid`. Different `editMode` values correspond to different controls. The `editMode` can also be extended as needed. For example, when editing a list in the list page, we might want to enable a special edit mode called `list-edit`.

```yaml
<grid editMode="list-edit">
</grid>
```

For all controls, it will first look for `<list-edit-xx>` controls. If not found, it will fall back to standard `<edit-xx>` controls.

Using this field mapping mechanism, we can abstract the data at the field level. For example, many fields are of type `String`, but `userId` and `roleId` have different business meanings and should correspond to different controls. We can choose the appropriate domain for them in the Excel data model using `control.xlib`.

> In the backend processing, we can further abstract the fields based on `domain` or `stdDomain`.
