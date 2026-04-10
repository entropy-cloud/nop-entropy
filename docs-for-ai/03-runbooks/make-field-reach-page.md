# 让字段从模型落到页面

## 适用场景

- 新增字段后，希望它最终出现在列表、查看页或编辑页里。
- 你不只是想“字段存在”，而是要把它真正走通到页面。

## AI 决策提示

- 默认顺序是：ORM / 模型 -> XMeta -> 生成 view 基线 -> 保留层 view/page。
- 不要只改页面文件。
- 如果生成基线已经包含字段，保留层只做展示方式定制。

## 最小闭环

### 1. 先让字段进入 ORM / 模型

字段需要先在 ORM 或源模型中存在。

### 2. 再让字段进入 XMeta

至少要确认：

1. 字段可见。
2. `insertable/updatable/queryable/sortable` 是否符合预期。
3. domain / schema 是否正确。

### 3. 确认生成 view 基线已经带出字段

如果 `_gen/_Xxx.view.xml` 已经出现该字段，说明模型到页面的生成链基本打通。

### 4. 在保留层 view/page 里决定最终展示方式

例如：

1. 只是在 edit/view layout 中显式摆放。
2. 给列表列改成自定义控件。
3. 改成 drawer/dialog 展示。

## 最值得抄的真实链路

`C:/can/nop/nop-app-mall` 的 `LitemallGoods.detail`

1. ORM 字段：
   `app-mall-dao/src/main/resources/_vfs/app/mall/orm/_app.orm.xml`
2. XMeta 字段：
   `app-mall-meta/src/main/resources/_vfs/app/mall/model/LitemallGoods/_LitemallGoods.xmeta`
3. 生成 view 基线：
   `app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/_gen/_LitemallGoods.view.xml`
4. 保留层页面定制：
   `app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`

这个例子里：

1. `detail` 在 ORM 中定义为 `html-64k`。
2. XMeta 把它声明为可插入、可更新、可查询、可排序。
3. 生成 view 已经把它放进 list/view/edit。
4. 保留层 view 再把它改成列表中的“查看详情”按钮和编辑表单里的富文本字段。

## 常见坑

1. 只改保留层 `view.xml`，但字段根本没进 XMeta。
2. 只改 `_gen/_Xxx.view.xml`，下次生成就丢。
3. 想做展示定制，却去修改 ORM，而不是改保留层 view。

## 相关文档

- `./add-field-and-validation.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/view-and-page-customization.md`
