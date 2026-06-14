# 让字段从模型落到页面

## 适用场景

- 新增字段后，希望它最终出现在列表、查看页或编辑页里。
- 你不只是想"字段存在"，而是要把它真正走通到页面。

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

典型链路（以实体 `Xxx` 的 `detail` 字段为例）：

1. ORM 字段：在 `*.orm.xml` 中定义字段类型（如 `html-64k`）。
2. XMeta 字段：在 `_Xxx.xmeta` 中声明 `insertable/updatable/queryable/sortable`。
3. 生成 view 基线：`_gen/_Xxx.view.xml` 自动带出字段到 list/view/edit。
4. 保留层页面定制：`Xxx.view.xml` 把字段改成目标展示方式。

这个链路展示了：

1. ORM 定义数据类型。
2. XMeta 控制可见性和操作权限。
3. 生成 view 打通到页面的默认路径。
4. 保留层做最终展示定制。

## 常见坑

1. 只改保留层 `view.xml`，但字段根本没进 XMeta。
2. 只改 `_gen/_Xxx.view.xml`，下次生成就丢。
3. 想做展示定制，却去修改 ORM，而不是改保留层 view。

## 相关文档

- `./add-field-and-validation.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/view-and-page-customization.md`
