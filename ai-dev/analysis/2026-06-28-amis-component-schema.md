# AMIS 组件 Schema 分析

> 分析 Nop 平台中 view.xml → page.yaml 所使用的 AMIS 组件及其属性。
> 来源: `xview.xdef`, `form.xdef`, `grid.xdef`, `disp.xdef`, `action.xdef`, `api.xdef`, `control.xlib`, `web.xlib` 及各 page 模板。

---

## 1. 顶层页面容器 (Page Types)

view.xml 的 `<pages>` 下定义 5 种页面类型: `crud`, `picker`, `simple`, `tabs`, `wizard`。每种映射为不同的 AMIS JSON 结构。

### 1.1 `type: "page"` — 公用根容器

```typescript
interface PageSchema {
  type: "page";
  name?: string;
  title?: string;
  subTitle?: string;
  remark?: string;
  className?: string;
  headerClassName?: string;
  bodyClassName?: string;
  asideResizor?: boolean;
  asideMinWidth?: number;
  asideMaxWidth?: number;
  asideClassName?: string;
  asideSticky?: boolean;
  initFetch?: boolean;
  interval?: number;          // 轮询刷新(ms)
  silentPolling?: boolean;
  stopAutoRefreshWhen?: string; // 表达式
  initApi?: ApiSchema;
  initFetchOn?: string;
  data?: Record<string, any>;
  body?: any;
  aside?: any;
  /** picker 模式特有 */
  size?: "sm" | "md" | "lg" | "xl" | "full";
  modalSize?: "sm" | "md" | "lg" | "xl" | "full";
}
```

---

### 1.2 `crud` / `pickerSchema` — 列表页面

> 由 `page_crud.xpl` 生成。picker 模式时根类型为 `pickerSchema`, 否则为 `crud`。

```typescript
interface CrudSchema {
  type: "crud" | "pickerSchema";
  name?: string;                // 默认 "crud-grid"
  className?: string;
  columnNum?: number;
  affixHeader?: boolean;
  checkOnItemClick?: boolean;
  selectable?: boolean;
  multiple?: boolean;
  combineNum?: number;
  combineFromIndex?: number | string;
  placeholder?: string;
  rowClassName?: string;
  headerClassName?: string;
  footerClassName?: string;
  toolbarClassName?: string;
  stopAutoRefreshWhen?: string;
  itemCheckableOn?: string;     // 表达式
  prefixRow?: any[];
  affixRow?: any[];
  affixRowClassName?: string;
  affixRowClassNameExpr?: string;
  prefixRowClassName?: string;
  prefixRowClassNameExpr?: string;
  autoFillHeight?: boolean | number;
  syncLocation?: boolean;       // 默认 false
  pickerMode?: boolean;
  defaultParams?: Record<string, any>;
  maxItemSelectionLength?: number;
  footable?: boolean;           // 列数过多时折叠

  api?: ApiSchema;
  saveOrderApi?: ApiSchema;
  filter?: Record<string, any>;

  bulkActions?: ActionSchema[];
  itemActions?: ActionSchema[];
  headerToolbar?: any[];
  footerToolbar?: any[];
  columns?: ColumnSchema[];
  itemAction?: ActionSchema;
  labelTpl?: string;
  source?: SourceSchema;        // picker 模式
  valueField?: string;          // picker 模式
  labelField?: string;          // picker 模式
}

interface SourceSchema {
  url?: string;
  method?: string;
  valueField?: string;
  labelField?: string;
  filter?: Record<string, any>;
}
```

### 1.3 `form` — 表单页面

> Nop 的 `layoutMode` 属性 (`form.xdef` 属性) 直接映射为 AMIS form 的 `mode` 属性。

```typescript
interface FormSchema {
  type: "form";
  name?: string;
  mode?: "normal" | "horizontal" | "inline";  // 来自 Nop layoutMode
  title?: string;
  label?: string;
  className?: string;
  bodyClassName?: string;
  panelClassName?: string;
  size?: "sm" | "md" | "lg";
  labelAlign?: "left" | "right" | "top";
  labelWidth?: string;
  submitText?: string;
  wrapWithPanel?: boolean;
  inheritData?: boolean;
  promptPageLeave?: boolean;

  // 提交相关
  submitOnChange?: boolean;
  submitOnInit?: boolean;
  resetAfterSubmit?: boolean;
  reload?: string;
  target?: string;
  redirect?: string;
  persistData?: string;
  persistDataKeys?: string;
  preventEnterSubmit?: boolean;

  // API
  api?: ApiSchema;
  initApi?: ApiSchema;
  asyncApi?: ApiSchema;
  initAsyncApi?: ApiSchema;
  messages?: Record<string, string>;

  // 轮询
  interval?: number;
  checkInterval?: number;
  initCheckInterval?: number;
  initFetch?: boolean;
  silentPolling?: boolean;
  canAccessSuperData?: boolean;
  initFetchOn?: string;
  stopAutoRefreshWhen?: string;

  data?: Record<string, any>;
  rules?: FormRuleSchema[];
  body?: any[];
  actions?: ActionSchema[];
  debug?: boolean;
}

interface FormRuleSchema {
  id: string;
  rule: string;         // XLang 表达式
  message: string;
  name?: string;        // 验证失败高亮的字段
}
```

### 1.4 `tabs` / `tab` — 标签页

```typescript
interface TabsSchema {
  type: "tabs";
  tabsMode?: "" | "vertical" | "chrome" | "simple" | "strong" | "line" | "radio" | "card";
  tabsClassName?: string;
  closeable?: boolean;
  draggable?: boolean;
  mountOnEnter?: boolean;
  unmountOnExit?: boolean;
  tabs: TabSchema[];
}

interface TabSchema {
  title: string;
  name?: string;
  icon?: string;
  iconPosition?: "left" | "right";
  hash?: string;
  reload?: boolean;
  className?: string;
  disabled?: boolean;
  unmountOnExit?: boolean;
  lazyLoad?: boolean;
  body?: any;
}
```

### 1.5 `wizard` / `step` — 向导

```typescript
interface WizardSchema {
  type: "wizard";
  mode?: "horizontal" | "vertical";
  className?: string;
  actionClassName?: string;
  actionPrevLabel?: string;
  actionNextLabel?: string;
  actionNextSaveLabel?: string;
  actionFinishLabel?: string;
  reload?: string;
  redirect?: string;
  target?: string;
  startStep?: string;
  initFetch?: boolean;
  api?: ApiSchema;
  initApi?: ApiSchema;
  initFetchOn?: string;
  steps: StepSchema[];
}

interface StepSchema {
  type: "step";        // 自动设置
  title: string;
  body?: any;
}
```

---

## 2. CRUD 网格相关

### 2.1 `crud` 列

```typescript
interface ColumnSchema {
  name: string;
  label?: string;
  type?: string;               // 缺省根据 control 库解析
  sortable?: boolean;
  width?: number | string;
  toggled?: boolean;
  align?: "left" | "center" | "right";
  fixed?: "left" | "right";
  groupName?: string;
  className?: string;
  labelClassName?: string;
  classNameExpr?: string;
  visibleOn?: string;
  disabledOn?: string;
  breakpoint?: string;
  backgroundScale?: number;    // xui:backgroundScale
  searchable?: any;            // 搜索框配置对象
  remark?: string;
  hint?: string;

  // 由 control 库注入
  // 例如 "type": "static", "type": "static-image", "type": "operation"
}

interface OperationColumnSchema extends ColumnSchema {
  type: "operation";
  buttons: ActionSchema[];
}
```

### 2.2 `input-table` — 可编辑表格

```typescript
interface InputTableSchema {
  type: "input-table";
  removable?: boolean;         // 默认 true
  addable?: boolean;           // 默认 true
  editable?: boolean;          // 默认 true
  needConfirm?: boolean;       // 默认 false
  showIndex?: boolean;         // 默认 true
  affixRow?: any[];
  columns: ColumnSchema[];
}
```

### 2.3 CRUD 内置工具栏组件

```typescript
// 头部的工具栏
interface FilterTogglerSchema { type: "filter-toggler"; id?: string; }
interface ColumnsTogglerSchema { type: "columns-toggler"; align?: "left" | "right"; id?: string; }
interface ReloadButtonSchema { type: "reload"; align?: "left" | "right"; id?: string; }
interface BulkActionsSchema { type: "bulkActions"; id?: string; }

// 底部的工具栏
interface PaginationSchema { type: "pagination"; id?: string; }
interface StatisticsSchema { type: "statistics"; id?: string; }
interface SwitchPerPageSchema { type: "switch-per-page"; id?: string; }

// 过滤与提交
interface ResetButtonSchema { type: "reset"; label: string; id?: string; }
interface SubmitButtonSchema { type: "submit"; label: string; level: string; id?: string; }
interface AutoGenerateFilterSchema {
  type: "autoGenerateFilter";
  columnsNum?: number;
  showBtnToolbar?: boolean;
  defaultCollapsed?: boolean;
}
```

---

## 3. 布局容器 (Layout)

```typescript
interface GroupSchema {
  type: "group";
  id?: string;
  direction?: "horizontal" | "vertical"; // 默认 horizontal
  visibleOn?: string;
  body: any[];
}

interface FieldSetSchema {
  type: "fieldSet";
  id?: string;
  title?: string;
  collapsable?: boolean;
  collapsed?: boolean;
  visibleOn?: string;
  body: any[];
}

interface CollapseGroupSchema {
  type: "collapse-group";
  id?: string;
  activeKey?: string[];
  body: CollapseSchema[];
}

interface CollapseSchema {
  type: "collapse";
  key?: string;
  header?: string;
  id?: string;
  body: any[];
}

interface DividerSchema {
  type: "divider";
  name?: string;
  lineType?: "dashed" | "solid";
}

interface AsideSchema {
  type: "aside";
  body: any[];
}

interface InputGroupSchema {
  type: "input-group";
  name?: string;
  body: any[];
}
```

---

## 4. 表单输入控件 (Form Inputs)

### 4.1 文本相关

```typescript
interface InputTextSchema {
  type: "input-text";
  name: string;
  label?: string | false;
  placeholder?: string;
  hint?: string;
  desc?: string;
  required?: boolean;
  disabled?: boolean;
  disabledOn?: string;
  visibleOn?: string;
  staticOn?: string;
  requiredOn?: string;
  clearValueOnHidden?: boolean;
  columnRatio?: number;
  submitOnChange?: boolean;
  clearable?: boolean;
  maxLength?: number;
  minLength?: number;
  value?: any;
  validations?: Record<string, any>;
  transform?: {
    upperCase?: boolean;
    lowerCase?: boolean;
  };
}

interface TextareaSchema {
  type: "textarea";
  name: string;
  label?: string;
  minRows?: number;
  maxLength?: number;
  value?: any;
}

interface InputRichTextSchema {
  type: "input-rich-text";
  name: string;
  label?: string;
}

interface InputPasswordSchema {
  type: "input-password";
  name: string;
  label?: string;
}

interface NativeNumberSchema {
  type: "native-number";
  name: string;
  label?: string;
  value?: any;
}

interface InputNumberSchema {
  type: "input-number";
  name: string;
  label?: string;
  precision?: number;
  value?: any;
}

interface InputTagSchema {
  type: "input-tag";
  name: string;
  clearable?: boolean;
  disabled?: boolean;
}

interface InputArraySchema {
  type: "input-array";
  name: string;
  items: {
    type: "input-text";
    required?: boolean;
    validations?: Record<string, any>;
  };
}

interface CodeEditorSchema {
  type: "editor" | "code";
  name?: string;
  language?: string;
}
```

### 4.2 日期/时间

```typescript
interface InputDateSchema {
  type: "input-date";
  name: string;
  format?: string;           // YYYY-MM-DD
  value?: any;
}

interface InputDatetimeSchema {
  type: "input-datetime";
  name: string;
  format?: string;           // YYYY-MM-DD HH:mm:ss
  value?: any;
}

interface InputDateRangeSchema {
  type: "input-date-range";
  name: string;
  inputFormat?: string;
  valueFormat?: string;
  shortcuts?: string;        // "today,yesterday,1dayago,7daysago"
}

interface InputDatetimeRangeSchema {
  type: "input-datetime-range";
  name: string;
  timeFormat?: string;
  inputFormat?: string;
  valueFormat?: string;
  shortcuts?: string;
}
```

### 4.3 选择类

```typescript
interface SelectSchema {
  type: "select";
  name: string;
  searchable?: boolean;
  clearable?: boolean;
  multiple?: boolean;
  selectFirst?: boolean;
  source?: string;           // "@dict:xxx" or URL
  value?: any;
}

interface ListSelectSchema {
  type: "list-select";
  name: string;
  clearable?: boolean;
  selectFirst?: boolean;
  source?: string;
  value?: any;
}

interface RadiosSchema {
  type: "radios";
  name: string;
  clearable?: boolean;
  source?: string;
  value?: any;
}

interface CheckboxSchema {
  type: "checkbox";
  name: string;
}

interface SwitchSchema {
  type: "switch";
  name: string;
  trueValue?: any;           // 默认 1
  falseValue?: any;          // 默认 0
  disabled?: boolean;
  value?: any;
}

interface TreeSelectSchema {
  type: "tree-select";
  name?: string;
  clearable?: boolean;
  source?: { url: string };
  value?: any;
}
```

### 4.4 文件/图片上传

```typescript
interface InputFileSchema {
  type: "input-file";
  name: string;
  receiver?: string;         // 上传 URL
  accept?: string;           // "image/*"
  maxSize?: number;
  useChunk?: boolean;        // 默认 false
  multiple?: boolean;         // file-list 模式
}

interface InputImageSchema {
  type: "input-image";
  name: string;
  receiver?: string;
  accept?: string;
}
```

### 4.5 隐藏/动作嵌套

```typescript
interface HiddenSchema {
  type: "hidden";
  name: string;
  value?: any;
}

interface ActionLinkSchema {
  type: "action";
  actionType: "download" | "dialog";
  label?: string;
  level?: "link";
  visibleOn?: string;
  api?: ApiSchema;
  dialog?: DialogSchema;
}
```

---

## 5. 显示控件 (Display)

```typescript
interface StaticSchema {
  type: "static";
  name?: string;
  label?: string;
}

interface StaticImageSchema {
  type: "static-image";
  name?: string;
  enlargeAble?: boolean;
  width?: number;
  height?: number;
}

interface StaticImagesSchema {
  type: "static-images";
  name?: string;
  enlargeAble?: boolean;
}

interface StaticMappingSchema {
  type: "static-mapping";
  map: Record<string, string>;  // "1" -> HTML 标签
}

interface TplSchema {
  type: "tpl";
  tpl: string;               // "${xxx|raw}"
}

interface PreStaticSchema {
  type: "pre-static";
  name?: string;
}

interface TableSchema {
  type: "table";
  source?: string;
  columns: TableColumnSchema[];
}

interface TableColumnSchema {
  name?: string;
  label?: string;
  type?: string;
  level?: "link";
}
```

---

## 6. 按钮与动作 (Actions)

```typescript
interface ActionSchema {
  type?: "button" | "action" | "submit" | "reset";
  id?: string;
  label?: string;
  icon?: string;
  iconClassName?: string;
  iconOnly?: boolean;
  rightIcon?: string;
  rightIconClassName?: string;
  level?: "info" | "success" | "warning" | "danger" | "link" | "primary" | "secondary" | "dark";
  size?: "xs" | "sm" | "md" | "lg";
  block?: boolean;
  active?: boolean;
  activeLevel?: string;
  activeClassName?: string;
  disabledTip?: string;
  tooltip?: string;
  tooltipPlacement?: "top" | "bottom" | "left" | "right";
  hotKey?: string;

  // 动作类型
  actionType: "ajax" | "dialog" | "drawer" | "link" | "url" | "download" | "copy" | "reload" | "close";
  api?: ApiSchema;
  initApi?: ApiSchema;
  link?: string;              // 单页跳转
  url?: string;               // 浏览器跳转
  blank?: boolean;
  redirect?: string;
  reload?: string;
  target?: string;
  close?: boolean | string;
  copyFormat?: string;
  content?: string;
  countDown?: number;
  countDownTpl?: string;
  confirmText?: string;

  // 权限
  "xui:permissions"?: string;
  "xui:roles"?: string;

  // 条件
  disabledOn?: string;
  visibleOn?: string;

  required?: string[];

  body?: any[];               // 自定义按钮内容
  dialog?: DialogSchema;
  drawer?: DialogSchema;
  feedback?: DialogSchema;

  onClick?: string;           // JS 代码
  onEvent?: Record<string, any>;

  messages?: {
    success?: string;
    failed?: string;
  };
}

interface DropdownButtonSchema {
  type: "dropdown-button";
  id?: string;
  label?: string;
  icon?: string;
  level?: string;
  className?: string;
  btnClassName?: string;
  block?: boolean;
  size?: string;
  iconOnly?: boolean;
  defaultIsOpened?: boolean;
  closeOnClick?: boolean;
  closeOnOutside?: boolean;
  trigger?: "click" | "hover";
  hideCaret?: boolean;
  disabledOn?: string;
  visibleOn?: string;
  buttons: ActionSchema[];
  onEvent?: Record<string, any>;
}
```

---

## 7. 弹框 (Dialog / Drawer)

```typescript
interface DialogSchema {
  title?: string;
  size?: "sm" | "md" | "lg" | "xl" | "full";
  height?: string;
  width?: string;
  closeOnEsc?: boolean;
  closeOnOutside?: boolean;
  showCloseButton?: boolean;
  noActions?: boolean;          // 隐藏默认按钮
  actions?: ActionSchema[];     // 自定义按钮
  data?: Record<string, any>;
  body?: any;                   // 实际内容, 由 GenPage 生成
}
```

---

## 8. API 定义

```typescript
interface ApiSchema {
  url: string;
  method?: "get" | "post" | "put" | "delete";
  data?: Record<string, any>;
  dataType?: "json" | "form" | "form-data";
  headers?: Record<string, string>;
  cache?: number;               // 缓存 ms
  responseType?: "blob";
  replaceData?: boolean;
  autoRefresh?: boolean;
  withFormData?: boolean;
  convertKeyToPath?: boolean;
  sendOn?: string;              // 条件表达式
  trackExpression?: string;
  responseData?: Record<string, any>;
  requestAdaptor?: string;      // JS 函数
  adaptor?: string;             // JS 函数
  "gql:selection"?: string;     // Nop GraphQL selection
}
```

---

## 9. Pickers (关联选择器)

```typescript
interface PickerSchema {
  type: "picker";
  "x:extends": string;          // picker.page.yaml 路径
  valueField: string;
  labelField: string;
  source?: {
    valueField?: string;
    labelField?: string;
  };
  joinValues?: boolean;         // 默认 false
  extractValue?: boolean;       // 默认 true
  multiple?: boolean;
}
```

---

## 10. AMIS Component Index

下表列出 Nop 平台中使用的所有 AMIS 组件类型:

| type | 用途 | 来源 |
|------|------|------|
| `page` | 页面根容器 | `page_crud.xpl`, `page_simple.xpl`, `page_tabs.xpl` |
| `crud` | CRUD 列表 | `grid_crud.xpl` |
| `pickerSchema` | Picker 列表 | `grid_crud.xpl` |
| `form` | 表单 | `page_simple.xpl`, `page_crud.xpl` |
| `tabs` | 标签页容器 | `page_tabs.xpl`, `GenLayoutTabs` |
| `tab` | 标签页 | `GenLayoutTabs` |
| `wizard` | 向导 | `page_wizard.xpl` |
| `step` | 向导步骤 | `page_wizard.xpl` |
| `group` | 行内字段组 | `GenFormRow`, `view-file`, `edit-file` |
| `fieldSet` | 可折叠字段集 | `GenFormTable` |
| `collapse-group` | 折叠面板组 | `GenAccordion` |
| `collapse` | 折叠面板 | `GenAccordionBody` |
| `divider` | 分割线 | `GenFormRow` |
| `aside` | 侧边栏 | `page_crud.xpl` |
| `input-text` | 文本输入 | `edit-string`, `edit-int`, `edit-long` 等 |
| `input-number` | 数字输入 | `edit-decimal` |
| `native-number` | 原生数字输入 | `edit-double` |
| `textarea` | 多行文本 | `edit-textarea` |
| `input-rich-text` | 富文本/HTML | `edit-html` |
| `input-password` | 密码输入 | `edit-password` |
| `input-date` | 日期选择 | `edit-date` |
| `input-datetime` | 日期时间 | `edit-datetime` |
| `input-date-range` | 日期范围 | `query-date` |
| `input-datetime-range` | 日期时间范围 | `query-datetime` |
| `input-file` | 文件上传 | `edit-file`, `edit-file-list` |
| `input-image` | 图片上传 | `edit-image` |
| `input-tag` | 标签输入 | `edit-tag-list` |
| `input-array` | 数组输入 | `edit-string-array` |
| `input-group` | 输入组合 | `edit-file` (supportFileLink) |
| `input-table` | 可编辑表格 | `GenInputTable`, `GenTable` |
| `select` | 下拉选择 | `edit-enum`, `edit-select` |
| `list-select` | 列表选择 | `edit-list-select` |
| `radios` | 单选按钮 | `edit-radios` |
| `checkbox` | 复选框 | `edit-file` (supportFileLink) |
| `switch` | 开关 | `edit-boolFlag` |
| `tree-select` | 树形选择 | `edit-tree-parent`, `edit-deptId` |
| `picker` | 记录选择器 | `edit-relation`, `edit-roleId` 等 |
| `hidden` | 隐藏字段 | `edit-hidden` |
| `static` | 静态文本 | `view-labelProp`, `view-relation` 等 |
| `static-image` | 静态图片 | `view-image`, `list-view-image` |
| `static-images` | 多图展示 | `view-images` |
| `static-mapping` | 值映射显示 | `view-boolFlag` |
| `tpl` | 模板显示 | `view-html` |
| `code` | 代码显示(只读) | `view-xml` |
| `editor` | 代码编辑器 | `edit-xml` |
| `pre-static` | 预格式化文本 | `view-pre` |
| `button` | 按钮 | `NormalizeAction` |
| `action` | 动作(链接) | `view-file`, `view-file-list` |
| `dropdown-button` | 下拉按钮组 | `GenActions` (actionGroup) |
| `operation` | 操作列 | `grid_crud.xpl` |
| `filter-toggler` | 过滤切换 | `grid_crud.xpl` 工具栏 |
| `columns-toggler` | 列显示切换 | `grid_crud.xpl` 工具栏 |
| `reload` | 刷新按钮 | `grid_crud.xpl` 工具栏 |
| `pagination` | 分页 | `grid_crud.xpl` 工具栏 |
| `statistics` | 统计 | `grid_crud.xpl` 工具栏 |
| `switch-per-page` | 每页条数 | `grid_crud.xpl` 工具栏 |
| `bulkActions` | 批量操作 | `grid_crud.xpl` 工具栏 |
| `reset` | 重置按钮 | `grid_crud.xpl` 过滤栏 |
| `submit` | 提交/查询按钮 | `grid_crud.xpl` 过滤栏 |
| `autoGenerateFilter` | 自动过滤 | `AutoGenerateFilter` |
| `table` | 简单表格 | `view-file-list` 弹框内 |

---

## 11. 数据流概览

```
view.xml (xview.xdef 元模型)
  │
  ▼
page.yaml 通过 x:gen-extends 调用 <web:GenPage view="xxx.view.xml" page="xxx"/>
  │
  ▼
impl_GenPage.xpl 加载 viewModel, objMeta, controlLib
  │
  ├─ page type=crud  → page_crud.xpl  → grid_crud.xpl
  ├─ page type=picker → page_picker.xpl → grid_crud.xpl
  ├─ page type=simple → page_simple.xpl → GenFormBody
  ├─ page type=tabs   → page_tabs.xpl
  └─ page type=wizard → page_wizard.xpl
  │
  ▼
每个表单 cell / 网格 col 通过 XuiHelper.getControlTag() 在 control.xlib 中
查找 {mode}-{type} 标签, 生成对应的 AMIS 组件 JSON
  │
  ▼
生成的 AMIS JSON 经过 WebPageHelper.fixPage() 后处理
(添加 amis CSS class, 规范化 group body, 转义 GraphQL URL 等)
```
