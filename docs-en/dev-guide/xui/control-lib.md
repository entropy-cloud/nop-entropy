
# Deriving Front-End Controls Dynamically from control.xlib

## XView Model
The XView model provides a technology-agnostic definition of view outlines. In the final `page.yaml` file we use the following tag to dynamically generate a JSON file based on the XView model

```yaml

x:gen-extends: |
    <web:GenPage view="NopDynFile.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

title: The configuration here will override the dynamically generated title configuration
```

* `page.yaml` can be authored manually; the XView model merely provides an auxiliary means for dynamic generation.
* Dynamic generation occurs at the `x:gen-extends` stage. It produces a base class for the current page, upon which we can fine-tune the generated details using the Delta adjustment technique.

## Control Mapping
When the `<web:GenPage>` tag converts the XView model into concrete front-end code, it requires additional `control.xlib` tag library information, which is responsible for mapping logical field structures to specific front-end controls.
Using this mechanism, for the same `xview.xml` model, different `control.xlib` libraries can yield different generation results; for example, we can use different control-mapping libraries for mobile and web.

In the XView model we can configure the controlLib attribute; if not specified, `/nop/web/xlib/control.xlib` is used by default.

In `control.xlib` we can define three kinds of controls—edit, view, and query—for each type. For example, `<edit-userId>`, `<view-userId>`, and `<query-userId>` specify which controls to use to render the userId type in edit, view, and query states, respectively.

The following order is used to resolve the control for a field:

1. The control configured on a form cell or grid column in XView—this is equivalent to explicitly specifying the control type
2. The `ui:control` configured on the prop in XMeta
3. The domain configured on a form cell or grid column in XView
4. The domain configured on the prop in XMeta
5. The stdDomain configured on a form cell or grid column in XView
6. The stdDomain configured on the prop in XMeta
7. Associations inferred from `ext:kind` configured in XMeta
8. The StdDataType inferred from the field type configured in XMeta

* stdDomain refers to standard data domains defined in the StdDomainRegistry, including extension types such as xpl, xml, csv-set, prop-name, etc.

## Edit Modes
You can also configure editMode on a form cell or grid column in XView; different editModes correspond to different controls. The three controls listed above—edit, view, and query—actually correspond to three different editModes.
Edit modes can also be extended as needed. For example, when editing on a list page, we may want to enable a special edit mode list-edit. We can configure

```
<grid editMode="list-edit">
</grid>
```

Then all controls will first attempt to resolve to `<list-edit-xx>` controls; if not found, they fall back to the standard `<edit-xx>` controls.

With this field-mapping mechanism, we achieve field-level abstraction. For example, many fields are of type String, but userId and roleId have different business semantics and should map to different controls; you can assign different domains to them in the Excel data model.
When generating code, `control.xlib` maps them to different controls.

> In backend service processing, we can also implement field-level domain abstraction based on the domain or stdDomain configuration.

<!-- SOURCE_MD5:bf20f1d46cd85bb08338ca93bb365003-->
