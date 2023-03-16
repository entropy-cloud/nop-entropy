# 可视化设计器

## 1. 修改列名后保存，刷新前台列表页面后发现修改没有应用。但是设计器中已经修改。
保存到后台后，可以发现page.yaml中label携带了i18n key，则前台得到的页面实际上会被国际化文本替换。
```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
  - name: deptType
    label: '@i18n:col.NopAuthDept.deptType,prop.label.NopAuthDept.deptType|改变类型'
    placeholder: '-'
    x:virtual: true
  x:virtual: true
```

`@i18n:key|defaultValue`格式中，字符|之后的部分是缺省值，只有当i18n key对应的国际化文本不存在的时候才会返回这个值。

在设计器里查看的时候，因为是在设计阶段所以没有替换i18n key。如果一定要以修改的值为准，则可以删除i18n key.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
body:
  name: crud-grid
  columns:
  - name: deptType
    label: '改变类型'
    placeholder: '-'
    x:virtual: true
  x:virtual: true
```
在设计器中修改，则需要在json视图中自己删除

![](remove-i18n-key.png)

# 部署问题
