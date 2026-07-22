# AMIS 组件 DOM 结构与选择器参考

> Status: open
> Date: 2026-07-22
> Scope: 基于 `control.xlib`（组件使用清单）和 `amis-react19/packages/amis-ui/src/components/`（DOM 渲染源码）的分析

## Context

- `control.xlib` 定义了 Nop 数据类型到 AMIS 组件类型的映射（例如 `edit-string` → `input-text`）
- `web.xlib` 通过 XPL 模板将这些映射渲染为 AMIS JSON schema，最终由 `amis-react19` 渲染为 DOM
- E2E 测试需要基于实际 DOM 结构编写健壮的选择器
- 当前 `AmisAdapter.ts` 和 `helpers/amis-selectors.ts` 中的选择器存在遗漏和不准确

## 组件 DOM 选择器速查表

以下是 `control.xlib` 中使用的所有 AMIS 组件类型及其在 `amis-react19` 中渲染的实际 DOM 结构。

### 容器类

| 控件标签 | AMIS `type` | CSS Selector | 数据属性 | 说明 |
|---|---|---|---|---|
| (page) | `page` | 无固定选择器 | — | 外部容器，没有特定 class |
| (form) | `form` | `form.cxd-Form` | HTML `<form>` | 根元素是 `<form>` 元素 |
| (group) | `group` | 无特定 class | — | 字段分组容器，通常只包含 div |
| (crud) | `crud` | `.cxd-Crud` / `.cxd-Crud2` | `[data-role="container"]` | 旧版/新版 CRUD |
| (tabs) | `tabs` | `.cxd-Tabs` | `[data-role="container"]` | 标签页容器 |
| (wizard) | `wizard` | `.cxd-Wizard` | — | 向导容器 |
| (fieldSet) | `fieldSet` | `.cxd-FieldSet` | — | 可折叠字段集 |
| (collapse-group) | `collapse-group` | `.cxd-CollapseGroup` | — | 折叠面板组 |
| (divider) | `divider` | `.cxd-Divider` | — | 分割线 |

### 文本输入类 (`input-text`)

| 控件标签 | AMIS `type` | CSS Selector | 数据属性 | 说明 |
|---|---|---|---|---|
| `edit-string` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 文本输入 |
| `edit-int` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 整数（用 input-text + isInt 验证） |
| `edit-short` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 同上 |
| `edit-byte` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 同上 |
| `edit-long` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 同上 |
| `edit-email` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | Email 格式 |
| `edit-url` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | URL 格式 |
| `edit-phone` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 电话号码 |
| `edit-path` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | URL 路径 |
| `query-string` | `input-text` | `.cxd-InputBox` | `[data-amis-name]` | 查询过滤输入框 |

**选择器策略**：
- 输入框的 `<input>` 元素通过 `name` 属性定位：`input[name="fieldName"]`
- 外层包装 `.cxd-InputBox` 有 `[data-amis-name]`
- **推荐**：优先用 `input[name]`，后备用 `[data-amis-name]`

### 数字输入类

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `edit-double` | `native-number` | `input[type="number"]` | 原生数字输入 |
| `edit-decimal` | `input-number` | `.cxd-NumberInput` | 高精度数字 |

**选择器策略**：
- `native-number` 渲染为 HTML5 `<input type="number">`，可通过 `name` 属性定位
- `input-number` 渲染为 `.cxd-NumberInput` 包装，内部含 `<input type="text">`

### 日期选择类

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `edit-date` | `input-date` | `.cxd-DatePicker` | 日期选择 |
| `edit-datetime` | `input-datetime` | `.cxd-DatePicker` | 日期时间 |
| `query-date` | `input-date-range` | `.cxd-DateRangePicker` | 日期范围查询 |
| `query-datetime` | `input-datetime-range` | `.cxd-DateRangePicker` | 日期时间范围查询 |

**选择器策略**：
- `.cxd-DatePicker` 内部有 `input` 和日历面板

### 选择类

| 控件标签 | AMIS `type` | CSS Selector | 数据属性 |
|---|---|---|---|
| `edit-enum` | `select` | `.cxd-Select` | `[data-amis-name]` |
| `edit-select` | `select` | `.cxd-Select` | `[data-amis-name]` |
| `edit-list-select` | `list-select` | `.cxd-ListControl` | — |
| `edit-radios` | `radios` | `.cxd-Radios` | — |
| `edit-boolFlag` | `switch` | `[data-role="switch"]` / `.cxd-Switch` | `[data-role="switch"]` |
| `edit-tree-parent` | `tree-select` | `.cxd-TreeSelection` | — |
| `edit-deptId` | `tree-select` | `.cxd-TreeSelection` | — |

**选择器策略**（Select 下拉选择）：
- 根元素 `.cxd-Select` 有 `[data-amis-name="fieldName"]`
- 选项弹出面板 `.cxd-Select-popover` 内含选项 `.cxd-Select-option`
- 触发展开：点击 `.cxd-Select`
- **推荐**：`[data-amis-name="fieldName"]` 定位，`.cxd-Select-option` 过滤选项文本

**Switch 开关**：
- 根元素 `[data-role="switch"]`
- 内部 `<input type="checkbox">` 有 `name` 属性
- **推荐**：`[data-role="switch"]` 比 `.cxd-Switch` 更稳定（不依赖主题前缀）

### 多行/富文本

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `edit-textarea` | `textarea` | `.cxd-TextareaControl` | 多行文本 |
| `edit-html` | `input-rich-text` | 无固定 class | 富文本编辑器 |
| `edit-xml` | `editor` | `.cxd-Editor` | 代码编辑器 |

### 标签数组

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `edit-tag-list` | `input-tag` | `.cxd-TagControl` | 标签列表 |
| `edit-string-array` | `input-array` | `.cxd-ArrayControl` | 字符串数组 |

### 文件上传

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `edit-file` | `input-file` | `.cxd-ImageControl` / `.cxd-FileControl` | 文件上传 |
| `edit-image` | `input-file` | 同上 | 图片上传（委托给 edit-file） |

### 关系选择 (Picker)

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `edit-relation` | `picker` | `.cxd-Picker`* | 关联记录选择器 |
| `edit-roleId` | `picker` | `.cxd-Picker`* | 角色选择器 |
| `edit-userId` | `picker` | `.cxd-Picker`* | 用户选择器 |
| `edit-ref-id` | `picker` | `.cxd-Picker`* | 引用 ID |
| `edit-ref-ids` | `picker` | `.cxd-Picker`* | 多引用 ID |

> `*` 注意：这里的 `picker` 是 **Nop 中用于选择关联记录的 AMIS crud 弹窗选择器**，不是 `amis-ui` 中 iOS 风格的 `PickerColumns`。它的 DOM 结构是一个 CRUD 弹窗，包含表单和表格。`type: "picker"` 实际上渲染为一个按钮 + 弹窗（内含 CRUD 组件）

### 只读显示类

| 控件标签 | AMIS `type` | CSS Selector | 说明 |
|---|---|---|---|
| `view-labelProp` | `static` | `.cxd-Form-static` / `.cxd-Static` | 纯文本显示 |
| `view-enum` | `static` | 同上 | 枚举值显示 |
| `view-relation` | `static` | 同上 | 关联显示 |
| `view-boolFlag` | `static-mapping` | `.cxd-MappingField` | 布尔值映射标签 |
| `view-image` | `static-image` | `.cxd-ImageControl` | 图片显示 |
| `view-images` | `static-images` | `.cxd-ImagesControl` | 多图片 |
| `view-html` | `tpl` | `.cxd-Tpl` | HTML 模板 |
| `view-xml` | `code` | `.cxd-Code` | XML 代码块 |
| `view-pre` | `pre-static` | `.cxd-Static` | 预格式化文本 |

### 按钮/操作

| AMIS `type` | CSS Selector | 说明 |
|---|---|---|
| `button` | `.cxd-Button` | 通用按钮 |
| `submit` | `.cxd-Button[type="submit"]` | 提交按钮 |
| `action` | `.cxd-Button--link` 或 `<a>` | 链接样式操作 |
| `dropdown-button` | `.cxd-DropDownButton` | 下拉按钮组 |

**按钮选择器策略**：
- 按角色/文本定位：`page.getByRole('button', { name: /编辑/ })`
- 按 CSS class 定位：`.cxd-Button--primary`（主要按钮）
- **推荐**：优先 `getByRole`，次选 CSS class

### 弹窗/对话框

| AMIS `type` | CSS Selector | Role |
|---|---|---|
| dialog | `.cxd-Modal` | `role="dialog"` |
| drawer | `.cxd-Drawer` | `role="dialog"` |

**弹窗选择器策略**：
- 子结构：`.cxd-Modal-header` / `.cxd-Modal-body` / `.cxd-Modal-footer`
- 确认按钮在 `.cxd-Modal-footer` 内的 `.cxd-Button--primary`
- 关闭按钮 `.cxd-Modal-close`
- 遮罩 `.cxd-Modal-overlay`
- **推荐**：`.cxd-Modal` 定位顶层弹窗，`[role="dialog"]` 后备

### 表格

| AMIS `type` | CSS Selector | 行选择器 | 列选择器 |
|---|---|---|---|
| `crud` > table | `.cxd-Table2` / `.cxd-Table` | `.cxd-Table-row` | `[data-col]` |
| `table` | `.cxd-Table2` / `.cxd-Table` | `.cxd-Table-row` | `[data-col]` |

**表格选择器策略**：
- 当前 AMIS 版本（Nop 使用的）生成 `.cxd-Table` class，新版 `amis-react19` 用 `.cxd-Table2`
- 行（`<tr>`）有 class `.cxd-Table-row` 和 `row-index` 属性
- 单元格（`<td>`）有 `data-col="{index}"` 属性
- **推荐**：`.cxd-Table-row` 定位行，`td[data-col]` 定位单元格

## 关键选择器最佳实践

### 数据属性优先策略

AMIS 组件在以下元素上提供了 `data-amis-name` 属性：

| 组件 | data-amis-name 位置 | 值 |
|---|---|---|
| Select | `.cxd-Select` 根元素 | 字段的 `name` 属性 |
| InputBox | `.cxd-InputBox` 根元素 | 字段的 `name` 属性 |
| Checkbox | `.cxd-Checkbox` 根元素 | 字段的 `name` 属性 |
| FormField | 包装器有 `data-role="form-item"` | 无 data-amis-name |

**推荐选择器优先级**（从最稳定到最不稳定）：
1. `getByRole()` 按钮文本定位（国际化问题最小）
2. `[data-amis-name="fieldName"]` 定位控件容器
3. `input[name="fieldName"]` 定位原生表单元素
4. `.cxd-*` class 选择器（依赖主题前缀，但当前项目统一用 `cxd` 主题）
5. 父容器遍历 `.cxd-Form-label` → sibling

### 主题前缀

- 当前项目使用 `cxd` 主题，所有 class 前缀为 `cxd-`
- 主题通过 `amis-core/src/theme.tsx` 的 `makeClassnames('cxd-')` 注入
- 如果未来切换主题（`ang`、`dark`），所有 `.cxd-*` 选择器失效
- `data-*` 属性、`role`、`aria-*` 不受主题影响

### void Element vs 渲染实际 DOM

`input-text` 控件在 AMIS 中由 `InputBox` 渲染，结构为：
```
.cxd-InputBox
  ├── input[type="text"]  (实际输入)
  └── .cxd-InputBox-clear (清除按钮)
```

而 `static` 控件没有输入框，直接渲染文本内容。

## 当前 E2E 选择器问题清单

| 问题 | 位置 | 当前写法 | 推荐写法 |
|---|---|---|---|
| 硬编码 filter 前缀 | `AmisAdapter.ts`, `helpers/form-helper.ts` | `input[name^="filter_userName"]` | **通过 Nop 的 schema 配置获取 filter 字段 name**，或增加 `queryAdapter` |
| 对话框确认按钮过于脆弱 | `button-helper.ts` | `.cxd-Modal--confirm .cxd-Button--primary` | **不需要**：确认弹窗结构是 `cxd-Modal-footer` + `cxd-Button--primary` |
| queryButton 只搜 AMIS 文本 | `AmisAdapter.ts:31` | `button:has-text("查询"), has-text("搜索")` | 可接受（业务语义不变） |
| cellValue 依赖 th 索引 | `table-helper.ts` | `th` → 列索引 → `td:nth-child(N)` | **改为** `td[data-col]` 或 `[data-field]` |
| Table 选择器过时 | `AmisAdapter.ts:12` | `.cxd-Table` | 当前版本 .cxd-Table 正确，注意未来可能变为 .cxd-Table2 |
| 弹窗定位不够精确 | `AmisAdapter.ts:64-65` | `.cxd-Modal, .cxd-Dialog` | `.cxd-Modal` + 可选的 `.cxd-Modal--size` |
| Spinner 等待不稳定 | `table-helper.ts` | `.cxd-Spinner` | 可接受，但应增加可见性判断 |

## 关键缺失的选择器（建议新增）

| 需要抽象的交互 | AMIS 端实现方法 | 建议 EngineAdapter 方法签名 |
|---|---|---|
| 搜索/查询字段输入 | `input[name^="filter_xxx"]` → 填写 → 触发表单提交 | `searchField(page, fieldName): Locator` |
| 执行搜索 | 点击 filter form 的 submit 按钮或按 Enter | `executeSearch(page): Promise<void>` |
| 刷新按钮 | `.cxd-Crud-toolbar` 内的 `.cxd-Button[icon="refresh"]` 或 `.cxd-Button` 含 `fa-sync` | `refreshButton(page): Locator` |
| 确认删除 | `.cxd-Modal-footer` 内的 `.cxd-Button--primary` | `confirmButton(dialog): Locator` |
| Checkbox 操作 | `[data-role="checkbox"] > input[type="checkbox"]` | `checkboxField(dialog, fieldName): Locator` |
| 表格行定位 | `.cxd-Table-row` 按 `row-index` 或文本内容过滤 | `tableRow(page, text): Locator` |
| 表格列值读取 | `td[data-col]` 按列索引 | `cellValue(row, colName): Promise<string>` |

## 关于 data-amis-name 的补充

Nop 的 AMIS 适配器（`host-amis-adapter.js`）会在渲染时自动将表单控件的 `name` 属性映射为 `data-amis-name` HTML 属性。这个机制对 E2E 测试极其重要——因为：

- 它不依赖 class 名称（不受主题切换影响）
- 它直接对应业务字段名（如 `userName`、`status`）
- 它在所有输入控件（Select、InputBox、Checkbox）上存在

**当前缺失的利用**：`AmisAdapter.formField()` 只用了 `input[name]`，没有利用 `[data-amis-name]`。如果 Nop 未来的 AMIS 版本在某些场景下不再渲染原生 `<input name>` 而只用 data-amis-name，当前选择器会失效。建议将 `formField` 改为双重策略：`[data-amis-name="${fieldName}"] input` 或 `input[name="${fieldName}"]`。

## References

- `control.xlib` — Nop 数据类型 → AMIS 组件映射（1206 行）
- `web.xlib` — AMIS 页面结构生成模板
- `amis-react19/packages/amis-ui/src/components/` — AMIS 组件 React 源码
- `amis-react19/packages/amis-core/src/theme.tsx` — 主题前缀机制
- `nop-entropy-e2e/packages/e2e-shared/src/helpers/amis-selectors.ts` — 当前选择器常量
- `nop-entropy-e2e/packages/e2e-shared/src/AmisAdapter.ts` — 当前适配器实现
