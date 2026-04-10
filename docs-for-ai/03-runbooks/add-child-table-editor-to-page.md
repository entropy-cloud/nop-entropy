# 给页面增加子表编辑

## 适用场景

- 父实体编辑页里需要同时编辑多个 `to-many` 子表。
- 需要子表跟随父表一起新增、编辑、保存。

## AI 决策提示

- 先确认 ORM relation 和 XMeta 已经把子表暴露出来。
- 再决定子表是内联 `input-table`，还是引用外部 `view/page`。
- 不要先从页面硬塞一个随机数组控件开始。

## 最小闭环

### 1. ORM relation 必须存在

例如 `to-many`：

1. `attributes`
2. `products`
3. `specifications`

### 2. XMeta 允许页面编辑

重点看：

1. `insertable="true"`
2. `updatable="true"`
3. `ui:editGrid` / `ui:viewGrid`

### 3. 必要时在保留层 XMeta 再开启

如果生成元数据还不够，优先在保留层 `.xmeta` 开启，而不是先写 Java。

### 4. 在父页面里接入子表编辑器

常见方式：

1. 直接 `gen-control` + `input-table`
2. `<view path="...Xxx.view.xml" grid="ref-edit"/>`
3. `<view path="...fragment.page.yaml"/>`

## 最值得抄的真实链路

`C:/can/nop/nop-app-mall` 的 `LitemallGoods`

1. ORM relation：
   `app-mall-dao/src/main/resources/_vfs/app/mall/orm/_app.orm.xml`
2. 生成 XMeta：
   `app-mall-meta/src/main/resources/_vfs/app/mall/model/LitemallGoods/_LitemallGoods.xmeta`
3. 保留层 XMeta：
   `app-mall-meta/src/main/resources/_vfs/app/mall/model/LitemallGoods/LitemallGoods.xmeta`
4. 父页面：
   `app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`
5. 外部子表 view：
   `app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml`

这个例子里：

1. ORM 先声明 `attributes/products/specifications` 三个 `to-many`。
2. XMeta 为它们生成 `sub-grid-edit` / `sub-grid-view` 能力。
3. 保留层 XMeta 再明确开放插入和更新。
4. 页面里分别用 `input-table`、外部 `view`、外部 `page.yaml` 三种方式接入子表。

## 常见坑

1. 页面里想编辑子表，但 ORM relation 根本没建。
2. relation 有了，但 XMeta 还是不可插入/更新。
3. 把复杂子表全内联在一个页面里，导致维护困难；本来应该拆成外部 `view` 或 page 片段。

## 相关文档

- `./add-field-and-validation.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/view-and-page-customization.md`
