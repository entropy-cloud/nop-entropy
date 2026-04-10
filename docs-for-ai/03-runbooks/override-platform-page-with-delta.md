# 用 Delta 覆盖平台页面

## 适用场景

- 需要改平台内置页面，而不是你自己的业务页面。
- 希望升级平台时保留你的覆盖层。

## AI 决策提示

- 优先在 `_vfs/_delta/default/...` 下按原路径覆盖。
- 页面覆盖通常不单独发生，常常还要同时补 ORM alias / XMeta / Biz 层能力。
- 默认用 `x:extends="super"`，不要复制一整份基础页面。

## 最小闭环

### 1. 先找到基础页面路径

例如：

```text
nop/sys/pages/NopSysNoticeTemplate/NopSysNoticeTemplate.view.xml
```

### 2. 在 Delta 目录创建同路径文件

```text
_vfs/_delta/default/nop/sys/pages/NopSysNoticeTemplate/NopSysNoticeTemplate.view.xml
```

### 3. 用 `x:extends="super"` 增量覆盖

```xml
<view x:extends="super" x:schema="/nop/schema/xui/xview.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    ...
</view>
```

### 4. 如果页面字段来自扩展属性，先补 Delta ORM

例如通过 alias 暴露：

```xml
<alias name="extFldA" propPath="extFields.fldA.string" type="String" tagSet="pub"/>
```

## 最值得抄的真实链路

`nop-demo/nop-quarkus-demo` 对 `NopSysNoticeTemplate` 的覆盖

1. Delta ORM：
   `nop-demo/nop-quarkus-demo/src/main/resources/_vfs/_delta/default/nop/sys/orm/app.orm.xml`
2. Delta 页面：
   `nop-demo/nop-quarkus-demo/src/main/resources/_vfs/_delta/default/nop/sys/pages/NopSysNoticeTemplate/NopSysNoticeTemplate.view.xml`
3. 基础页面：
   `nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysNoticeTemplate/NopSysNoticeTemplate.view.xml`

这个例子里：

1. Delta ORM 先为基础实体补 `extFldA/extFldB` alias。
2. Delta 页面再把这两个字段放到 query/edit/list。
3. 整个过程没有去改平台原始页面源码。

## 常见坑

1. 直接改平台原始 `view.xml`。
2. 页面里加了字段，但底层 ORM/XMeta 根本没有这个字段或 alias。
3. 在 Delta 页面里复制整份原始页面，导致升级时难以跟进。

## 相关文档

- `./prefer-delta-over-direct-modification.md`
- `../02-core-guides/delta-customization.md`
- `../02-core-guides/view-and-page-customization.md`
