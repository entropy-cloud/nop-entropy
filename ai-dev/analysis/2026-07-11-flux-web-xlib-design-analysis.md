# flux-web.xlib 全新实现方案分析

> 分析目标：仿照 `web.xlib` + `control.xlib` 的模式，**从零编写** `flux-web.xlib` + `flux-control.xlib`，产出 Flux JSON Schema。后续 `web.xlib` / `control.xlib` 将被废弃。
>
> 分析基础：
> - `nop-web/.../xlib/web.xlib`（929 行，37 个标签）
> - `nop-web/.../xlib/control.xlib`（1206 行，75 个控件标签）
> - `nop-web/.../xlib/web/*.xpl`（10 个辅助文件，其中 `page_wizard.xpl` 未在 dispatch 中使用）
> - `nop-ui/.../XuiHelper.java`（控件解析链 + GraphQL 选择集生成）
> - `nop-codegen/.../templates/orm-web/`（代码生成模板）
> - `ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md`（Schema 对比）

---

## 1. 为什么不继承、必须全新实现

### 1.1 web.xlib 的标签分类

对 web.xlib 全部 37 个标签 + 10 个 xpl 辅助文件逐个分析后，按"输出是否与 AMIS 耦合"分为三类：

| 分类 | 标签数 | 说明 | flux-web.xlib 处理方式 |
|------|--------|------|----------------------|
| **A. AMIS 结构耦合** | ~20 | 直接输出 AMIS 特有结构（crud 工具栏组件、fieldSet、group、tabs/tab、operation 列、autoGenerateFilter 等） | 全部重写，输出 Flux 对应结构 |
| **B. AMIS 属性耦合** | ~9 | 输出 AMIS 属性名（`visibleOn`/`disabledOn`/`actionType`/`FormDefaultAttrs` 中的 AMIS 属性列表） | 重写，改为 Flux 属性名 |
| **C. 渲染器无关逻辑** | ~8 | 模型加载、GraphQL 选择集生成、API URL 模板渲染、控件解析调度 | 核心逻辑落在 Java 工具类（`XuiHelper`、`SchemaLoader`），xpl 层只是调用 |

### 1.2 C 类逻辑的可复用性

C 类"渲染器无关逻辑"看似可以抽取共享，但逐行分析后发现**全部落在 Java 层或极短的 c:script 中**：

| C 类逻辑 | 实现位置 | xpl 层代码量 | Flux 复用方式 |
|---------|---------|-------------|-------------|
| 视图模型加载 | `ResourceComponentManager.loadComponentModel()` | 1 行 | 直接调用 |
| ObjMeta 加载 | `SchemaLoader.loadXMeta()` | 1 行 | 直接调用 |
| controlLib 加载 | `XplLibHelper.loadLib()` | 1 行 | 直接调用，默认路径改为 flux-control.xlib |
| GraphQL listSelection | `XuiHelper.getListSelection(gridModel, objMeta)` | 1 行 | 直接调用 |
| GraphQL formSelection | `XuiHelper.getFormSelection(formModel, objMeta)` | 1 行 | 直接调用 |
| formProps 计算 | `XuiHelper.getFormProps(formModel, objMeta)` | 1 行 | 直接调用 |
| 关联属性解析 | `XuiHelper.getRelationProp(propMeta, objMeta)` | 1 行 | 直接调用 |
| 过滤属性追加 | `XuiHelper.appendFilterProps(url, fixedProps)` | 1 行 | 直接调用 |
| API URL 模板渲染 | `url.$renderTemplateForScope("{@","}",genScope)` | 1 行 | 直接调用 |
| 页面类型分发 | `c:choose` on `pageModel.type` | 10 行 | 逻辑相同，引用的 xpl 不同 |
| cell 只读判断 | readonly/mandatory/filterOp 计算 | ~40 行 | 逻辑相同，输出属性名不同 |

**结论**：C 类逻辑的核心全在 `io.nop.xui.utils.XuiHelper`（nop-ui 模块），这是 Java 工具类，与渲染器完全无关，flux-web.xlib 可直接调用。xpl 层需要重写的 C 类逻辑仅约 50 行 c:script（cell 只读判断 + 页面类型分发），复制成本远低于维护共享文件的耦合成本。

### 1.3 全新实现的决定性理由

1. **web.xlib 82% 的标签输出 AMIS 特有结构**（A+B 类），继承后大部分标签都要覆盖，继承没有收益。
2. **A 类标签的 Flux 等价物结构差异巨大**，不是改属性名能解决的——例如 `grid_crud.xpl` 中 AMIS 预置的 `filter-toggler`/`columns-toggler`/`switch-per-page`/`statistics` 在 Flux 中不存在，需要重新设计 toolbar 组装逻辑。
3. **后续要废弃 web.xlib**，继承关系反而成为技术债——废弃时需要切断继承链。
4. **Flux 有独有的结构节点**（fragment/loop/recurse/reaction/data-source），全新实现可以充分利用这些能力，不受 AMIS 结构限制。

---

## 2. flux-web.xlib 完整标签清单

### 2.1 标签总览（37 个标签，全新实现）

| 标签名 | outputMode | 对应 web.xlib | 核心变化 |
|--------|-----------|--------------|---------|
| **页面入口** | | | |
| `GenPage` | xjson | GenPage | 分发逻辑相同，引用 flux-web/ 下的 xpl |
| `GenPageDefault` | xjson | GenPageDefault | 相同（抛异常 / Delta 覆盖） |
| **表单** | | | |
| `GenForm` | xjson | GenForm | 模型加载相同，`<form>` 结构适配 Flux |
| `GenFormImpl` | xjson | GenFormImpl | `FormDefaultAttrs` 改为 Flux 属性集 |
| `GenFormBody` | xjson | GenFormBody | 分发逻辑相同（tabs/simpleTable/groups） |
| `GenFormTable` | xjson | GenFormTable | `fieldSet`→`fieldset`，`group`→`container` |
| `GenFormRow` | xjson | GenFormRow | `divider`→`separator`，`group`→`flex` |
| `GenFormCell` | xjson | GenFormCell | 逻辑相同（group 类型递归） |
| `GenFormSimpleCell` | none | GenFormSimpleCell | 核心重写（见 2.2） |
| `GenLayoutGroups` | xjson | GenLayoutGroups | 逻辑相同 |
| `GenLayoutTabs` | xjson | GenLayoutTabs | `visibleOn`→`visible`，`tabs/tab` 结构适配 |
| `GenAccordion` | xjson | GenAccordion | `collapse-group`→`collapse(accordion)` |
| `GenAccordionBody` | xjson | GenAccordionBody | 同上 |
| **表格** | | | |
| `GenGrid` | xjson | GenGrid | 模型加载相同 |
| `GenGridImpl` | xjson | GenGridImpl | **核心重写**：Flux crud 结构差异大 |
| `GenGridCols` | xjson | GenGridCols | 逻辑相同 |
| `GenGridCol` | xjson | GenGridCol | `visibleOn`→`visible`，searchable 逻辑适配 |
| **操作** | | | |
| `GenActions` | xjson | GenActions | `button`→Flux button，`dropdown-button`→Flux dropdown |
| `NormalizeAction` | none | NormalizeAction | **核心重写**：onClick 已存在则透传，否则 actionType→ActionSchema DAG |
| **控件桥接** | | | |
| `DefaultControl` | none | DefaultControl | 逻辑相同，加载 flux-control.xlib |
| `GenDispView` | none | GenDispView | 子页面渲染递归到 flux-web 自身 |
| `GenInputTable` | xjson | GenInputTable | `input-table`→Flux array-editor/combo |
| `GenTable` | xjson | GenTable | Flux 只读表格 |
| **API / 页面加载** | | | |
| `NormalizeApi` | none | NormalizeApi | 完全复用（模板渲染逻辑渲染器无关） |
| `LoadPage` | none | LoadPage | dialog 属性集改为 Flux dialog 属性 |
| **辅助** | | | |
| `FluxFormDefaultAttrs` | none | FormDefaultAttrs | **重写**：Flux form 属性集 |
| `FluxGridDefaultAttrs` | none | GridDefaultAttrs | **重写**：Flux crud 属性集 |
| `FluxPageDefaultAttrs` | none | PageDefaultAttrs | **重写**：Flux page 属性集 |
| `GetColDefaultWidth` | none | GetColDefaultWidth | 直接复用逻辑 |
| `GetColDefaultAlign` | none | GetColDefaultAlign | 直接复用逻辑 |
| `GetFormDefaultSize` | none | GetFormDefaultSize | 直接复用逻辑 |
| `AutoGenerateFilter` | xjson | AutoGenerateFilter | 重写或废弃（Flux 无直接等价） |

### 2.2 GenFormSimpleCell：核心重写点

这是整个 xlib 中信息密度最高的标签（web.xlib 中 80 行 c:script）。Flux 版本需要改动的点：

```
AMIS 输出                          →  Flux 输出
─────────────────────────────────────────────────────
disabledOn: cellModel?.disabledOn  →  disabled: cellModel?.disabledOn
visibleOn:  cellModel?.visibleOn   →  visible:  cellModel?.visibleOn
staticOn:   cellModel?.readonlyOn  →  (删除，用 disabled 表达)
requiredOn: cellModel?.requiredOn  →  required: mandatory (在 validations 中处理)
clearValueOnHidden: ...            →  (删除，Flux 无此概念)
label: false (隐藏)                →  label: '' (Flux 不支持 false)
required: true                     →  required: true (一致)
hint: ...                          →  hint: ... (一致)
columnRatio: ...                   →  flex: columnRatio (Flux 用 flex 布局)
submitOnChange: ...                →  submitOnChange: ... (一致)
```

以下逻辑**完全保留**（从 web.xlib 复制，与渲染器无关）：
- cellModel / propMeta 获取
- 只读判断（`!propMeta.updatable` / `!propMeta.insertable` / `fixedProps`）
- editMode 计算（`rd ? 'view' : formModel.editMode`）
- filterOp 解析和 `filter_X__op` 命名规则
- i18n label 计算
- mandatory 判断
- genControl / view / DefaultControl 三路控件解析

### 2.3 NormalizeAction：AMIS actionType → Flux ActionSchema

这是**最大的结构转换**。

#### 2.3.1 核心规则：onClick 优先

NormalizeAction 的首要判断逻辑：

```
if (action.onClick 已存在)
    → 直接透传 onClick，不做任何转换
else
    → 从 action.api / actionType / dialog / drawer 转换生成 onClick
```

这意味着：
- 如果用户在 view.xml 中已经为某个 action 手写了 Flux 原生的 `onClick`（通过扩展属性或 Delta 注入），NormalizeAction **直接使用该值**，跳过所有 AMIS→Flux 转换逻辑。
- 只有当 `onClick` 不存在时，才回退到从 AMIS 风格的 `api`/`actionType`/`dialog`/`drawer` 推导生成 Flux ActionSchema。

这一设计使得：
1. **代码生成的页面**（从 ORM 模型自动生成）走自动转换路径——XView 中的 `<action>` 使用 `api`/`dialog` 等 AMIS 风格配置，flux-web.xlib 自动转换为 Flux `onClick`。
2. **手工定制的高级页面**可以直接写 Flux 原生 `onClick`（ActionSchema DAG），利用 Flux 的重试/防抖/并行等高级特性，不受转换逻辑限制。
3. **渐进迁移**——同一个 action 可以先用自动转换跑通，后续根据需要替换为手写 `onClick`。

#### 2.3.2 转换规则（onClick 不存在时）

**AMIS 动作模型**（扁平）：
```json
{
  "actionType": "ajax",
  "api": { "url": "...", "method": "POST" },
  "confirmText": "确认？",
  "dialog": { "page": "edit", "title": "编辑" },
  "next": { "actionType": "close" }
}
```

**Flux 动作模型**（DAG）：
```json
{
  "onClick": {
    "type": "confirm",
    "when": { "message": "确认？" },
    "then": [
      { "type": "api", "url": "...", "method": "POST" },
      { "type": "dialog", "page": "edit", "title": "编辑" },
      { "type": "component", "action": "close" }
    ]
  }
}
```

actionType 到 Flux action type 的映射规则：

| AMIS actionType | Flux Action type | 说明 |
|----------------|-----------------|------|
| `ajax` | `api` | HTTP 请求 |
| `dialog` | `dialog` | 打开对话框（page 引用方式需调整） |
| `drawer` | `drawer` | 打开抽屉 |
| `copy` | `set-value` | 复制值 |
| `reload` | `component:reload` | 刷新组件 |
| `close` | `component:close` | 关闭对话框 |
| `toast` | `toast` | 提示消息 |
| 无 actionType + 有 api | 推断为 `api` | 兼容 |
| 无 actionType + 有 dialog | 推断为 `dialog` | 兼容 |
| `next` | `then[]` 数组 | 链式→顺序 DAG。**注：`action.xdef` 实际没有 `next` 字段，XView 模型不通过 `next` 表达 action 链** |

`confirmText` → 在 action 链头部插入 `{type:'confirm', when:{message:confirmText}}` 守卫节点。

---

## 3. flux-control.xlib 完整标签清单

### 3.1 命名约定（与 control.xlib 一致）

保持 `{mode}-{type}` 格式，使 `XuiHelper.getControlTag()` 的解析链**零改动可用**：

```
control → domain → domain(去后缀) → stdDomain → relKind → stdDataType
   ↓ 每级尝试 {mode}-{value}，找不到则 mode 降级
edit → query/edit, list-view → view, list-edit → edit
最终兜底: view-any
```

### 3.2 标签清单与 AMIS→Flux 控件映射

#### 数值类型

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-double` | `{type:'native-number', value:...}` | `{type:'input-number', value:...}` |
| `edit-decimal` | `{type:'input-number', precision:...}` | `{type:'input-number', precision:...}` |
| `edit-short/byte/int/long` | `{type:'input-text', validations:'isInt'}` | `{type:'input-number', validations:{isInt:true}}` |

#### 字符串类型

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-string` | `{type:'input-text', validations:'maxLength=..', ...}` | `{type:'input-text', validations:{maxLength:.., minLength:..}, ...}` |
| `edit-textarea/longtext/remark` | `{type:'textarea', minRows:3}` | `{type:'textarea', minRows:3}` |
| `edit-html` | `{type:'input-rich-text'}` | `{type:'markdown-editor'}` ⚠️ |
| `view-html` | `{type:'tpl', tpl:'${name\|raw}'}` | `{type:'html', html:'${name}'}` |
| `edit-tag-list` | `{type:'input-tag'}` | `{type:'tag-list'}` |
| `edit-string-array` | `{type:'input-array', items:{type:'input-text'}}` | `{type:'array-editor', item:{type:'input-text'}}` |

> ⚠️ `edit-html`：AMIS 用 HTML 编辑器，Flux 用 Markdown 编辑器。这是**格式差异**（HTML vs Markdown 产物），需确认业务上是否可接受切换。

#### 特殊域

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-email` | `{type:'input-text', validations:'isEmail'}` | `{type:'input-text', validations:{isEmail:true}}` |
| `edit-url` | `{type:'input-text', validations:'isUrl'}` | `{type:'input-text', validations:{isUrl:true}}` |
| `edit-phone` | `{type:'input-text', validations:'isPhoneNumber'}` | `{type:'input-text', validations:{isPhoneNumber:true}}` |
| `edit-password` | `{type:'input-password'}` | `{type:'input-password', showRevealToggle:true}` |
| `view-password` | `{type:'static', value:'***'}` | `{type:'text', text:'***'}` |
| `edit-ascii` | `{type:'input-text', validations:'isAlpha'}` | `{type:'input-text', validations:{isAlpha:true}}` |

#### 日期/时间

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-date` | `{type:'input-date', format:'YYYY-MM-DD'}` | `{type:'input-date', format:'YYYY-MM-DD'}` |
| `edit-datetime` | `{type:'input-datetime', format:'...'}` | `{type:'input-datetime', format:'...'}` |
| `edit-timestamp` | x:prototype='edit-datetime' | x:prototype='edit-datetime' |
| `query-date` | `{type:'input-date-range', name:'filter_X__dateBetween'}` | `{type:'date-range', name:'filter_X__dateBetween'}` |
| `query-datetime` | `{type:'input-datetime-range', ...}` | `{type:'date-range', showTime:true, ...}` |

#### 布尔/枚举

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-boolFlag/boolean` | `{type:'switch', trueValue:1, falseValue:0}` | `{type:'switch', trueValue:1, falseValue:0}` |
| `view-boolFlag/boolean` | `{type:'static-mapping', map:{1:'是',0:'否'}}` | `{type:'mapping', map:{1:'是',0:'否'}}` |
| `edit-enum` | `{type:'select', source:'@dict:...'}` | `{type:'select', source:'@dict:...'}` |
| `view-enum/labelProp` | `{type:'static', name:'labelProp'}` | `{type:'text', name:'labelProp'}` |
| `edit-radios` | `{type:'radios', source:'@dict:...'}` | `{type:'radio-group', source:'@dict:...'}` |

#### 关联/引用

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-relation/to-one` | `{type:'picker', ...}` | `{type:'picker', ...}` 基本一致 |
| `view-relation/to-one` | `{type:'static', name:'labelProp'}` | `{type:'text', name:'labelProp'}` |
| `edit-ref-id` | `{type:'picker', valueField:'id', multiple:false}` | 同 |
| `edit-ref-ids` | `{type:'picker', multiple:true}` | 同 |
| `edit-to-many` | 调用 web.xlib 的 GenInputTable | 调用 flux-web.xlib 的 GenInputTable |
| `view-to-many` | 调用 web.xlib 的 GenTable | 调用 flux-web.xlib 的 GenTable |
| `edit-tree-parent` | `{type:'tree-select', source:...}` | `{type:'tree-select', source:...}` |
| `edit-deptId` | `{type:'tree-select', source:...}` | 同 |
| `edit-roleId/userId` | `{type:'picker', x:extends:'...'}` | 同 |

#### 文件/图片

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-file` | `{type:'input-file', ...}` | `{type:'input-file', ...}` |
| `edit-image` | `{type:'input-file', accept:'image/*'}` | `{type:'input-image'}` |
| `view-image` | `{type:'static-image', enlargeAble:true}` | `{type:'image', enlargeAble:true}` |
| `view-images` | `{type:'static-images'}` | `{type:'images'}` |
| `edit-file-list/images` | `{type:'input-file', multiple:true}` | `{type:'input-file', multiple:true}` |
| `view-file` | `{type:'group', ...download action}` | Flux container + download action |
| `view-file-list` | `{type:'group', ...dialog with table}` | Flux dialog + table |

#### XML/代码

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `view-xml` | `{type:'code', language:'xml'}` | `{type:'json-view', language:'xml'}` |
| `edit-xml` | `{type:'editor', language:'xml'}` | `{type:'editor', language:'xml'}` |

#### 其他

| 标签 | AMIS 输出 | Flux 输出 |
|------|----------|----------|
| `edit-hidden` | `{type:'hidden'}` | Flux 无 hidden，返回特殊标记让 cell 跳过输出或写入 form data |
| `view-hidden` | `{type:'hidden'}` | 同上 |
| `view-any` | `{type:'static', name:...}` | `{type:'text', name:...}` (兜底) |
| `edit-any` | x:prototype='edit-string' | x:prototype='edit-string' |

### 3.3 x:prototype 继承关系（与 control.xlib 保持一致）

flux-control.xlib 可以自由使用 `x:prototype` 复用标签定义，与 control.xlib 保持相同的继承链：

```
edit-timestamp  ← edit-datetime
edit-longtext   ← edit-textarea
edit-remark     ← edit-textarea
view-enum       ← view-labelProp
view-boolean    ← view-boolFlag
edit-boolean    ← edit-boolFlag
query-timestamp ← query-datetime
edit-images     ← edit-file-list
edit-to-one     ← edit-relation
view-to-one     ← view-relation
query-to-one    ← edit-to-one
query-to-many   ← edit-to-one
view-ref-ids    ← view-ref-id
view-hidden     ← edit-hidden
edit-any        ← edit-string
view-any        ← view-labelProp
view-xpl        ← view-xml
edit-xpl        ← edit-xml
```

---

## 4. flux-web.xlib 的目录结构

```
nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/
├── flux-web.xlib              ← 新增：Flux 页面生成主库（~33 个标签）
├── flux-control.xlib          ← 新增：Flux 控件映射库（~55 个标签）
├── flux-web/                  ← 新增：flux-web.xlib 辅助 xpl
│   ├── impl_GenPage.xpl       ← 页面入口（模型加载 + 类型分发）
│   ├── impl_GenForm.xpl       ← 表单入口（模型加载）
│   ├── impl_GenGrid.xpl       ← 表格入口（模型加载 + genScope）
│   ├── init_grid_gen_scope.xpl ← GraphQL 选择集初始化
│   ├── page_crud.xpl          ← CRUD 页面（Flux crud 结构）
│   ├── page_simple.xpl        ← 简单页面
│   ├── page_picker.xpl        ← 选择器页面
│   ├── page_tabs.xpl          ← 标签页
│   └── grid_crud.xpl          ← CRUD 表格区域（Flux toolbar 组装）
│
├── web.xlib                   ← 现有（后续废弃）
├── control.xlib               ← 现有（后续废弃）
├── web/                       ← 现有
├── std.xlib                   ← 现有（validator 辅助，可共享引用）
├── view-gen.xlib              ← 现有（view.xml 生成，渲染器无关，保持不动）
└── api-web.xlib               ← 现有
```

**关键点**：
- `flux-web/` 目录**独立于** `web/` 目录，不复用任何 `web/*.xpl` 文件
- `flux-web/` 中的 xpl 可以调用 `flux-web.xlib` 自身的标签（通过 `thisLib:` 命名空间）
- `std.xlib`（validator 实现）是渲染器无关的，flux-control.xlib 可以引用
- `view-gen.xlib` 在 Stage A（代码生成期）生成 XView 模型，与渲染器无关，完全不需要改动

---

## 5. 关键设计决策

### 5.1 Java 工具类：零改动复用

以下 Java 工具类/方法全部位于 `nop-ui` 或 `nop-core` 模块，与渲染器完全无关，flux-web.xlib 直接调用：

| Java 方法 | 所在类 | 用途 |
|-----------|--------|------|
| `getControlTag(lib, dispMeta, propMeta, objMeta, editMode)` | `XuiHelper` | 控件解析链 |
| `getListSelection(gridModel, objMeta)` | `XuiHelper` | 生成 list GraphQL 选择集 |
| `getFormSelection(formModel, objMeta)` | `XuiHelper` | 生成 form GraphQL 选择集 |
| `getFormProps(formModel, objMeta)` | `XuiHelper` | 获取表单字段列表 |
| `getRelationProp(propMeta, objMeta)` | `XuiHelper` | 获取关联属性 meta |
| `appendFilterProps(url, fixedProps)` | `XuiHelper` | URL 追加固定过滤参数 |
| `loadXMeta(path)` | `SchemaLoader` | 加载 obj meta |
| `loadLib(path)` | `XplLibHelper` | 加载 controlLib |
| `loadComponentModel(path)` | `ResourceComponentManager` | 加载 view model |
| `$renderTemplateForScope(...)` | XLang 扩展方法 | API URL 模板渲染 |

**不需要修改任何 Java 代码。**

### 5.2 XView 模型层：零改动复用

XView（`xview.xdef`）是渲染器无关的中层抽象。`flux-web.xlib` 消费**完全相同的** `*.view.xml` 文件。

不同之处仅在 `<controlLib>` 的指向：
```xml
<!-- AMIS 模式 -->
<controlLib>/nop/web/xlib/control.xlib</controlLib>

<!-- Flux 模式 -->
<controlLib>/nop/web/xlib/flux-control.xlib</controlLib>
```

### 5.3 CRUD 工具栏：重新设计

AMIS 的 `grid_crud.xpl` 依赖大量 AMIS 预置组件，Flux 没有这些组件。Flux 版的 `grid_crud.xpl` 需要手动组装：

```
AMIS grid_crud.xpl                        Flux grid_crud.xpl
────────────────────────────────          ──────────────────────────
headerToolbar:                            toolbar:
  filter-toggler        (AMIS 内置)         (Flux 无等价，用 filter 表单替代)
  GenActions(listActions)                  GenActions(listActions)
  bulkActions           (AMIS 内置)         (Flux 手动组装批量按钮)
  columns-toggler       (AMIS 内置)         (Flux 无等价，可省略或自定义)
  reload                (AMIS 内置)         {type:'button', onClick:{type:'component:reload'}}

footerToolbar:                            footerToolbar:
  statistics            (AMIS 内置)         {type:'statistics'}
  switch-per-page       (AMIS 内置)         (Flux 在 pagination 中配置)
  pagination            (AMIS 内置)         {type:'pagination'}

columns:                                  columns:
  GenGridCols                              GenGridCols (相同)
  operation (AMIS 特殊列类型)               (Flux 无 operation 列，rowActions
                                            放入 toolbar 或通过 column.render 实现)

filter:                                   filter:
  <filter> 子表单 (AMIS 内置)               (Flux 在 toolbar 手动放置控件，
                                            或保留 filter 表单结构)
```

### 5.4 Dialog/Drawer：属性集适配

Flux dialog 的属性名与 AMIS 不同。`LoadPage` 标签需要产出 Flux dialog 属性：

```
AMIS dialog props:           Flux dialog props:
closeOnEsc                   closeOnEsc        (一致)
closeOnOutside               closeOnOutside    (一致)
size                         (删除，用 width/height)
height                       height            (一致)
width                        width             (一致)
showCloseButton              showClose         (改名)
actions                      actions           (一致，但 action 结构不同)
title                        title             (一致)
noActions → actions:[]       actions:[]        (一致)
```

### 5.5 hidden 字段处理

AMIS 有 `type:'hidden'` 控件，Flux 没有。处理策略：

**方案 A（推荐）**：`flux-control.xlib` 的 `edit-hidden` / `view-hidden` 返回 `{type:'_hidden', name:...}` 特殊标记，`GenFormSimpleCell` 检测到后将其值写入 form 的 `data` 属性而非 body。

**方案 B**：`GenFormSimpleCell` 在调用 `DefaultControl` 前先检查 propMeta 是否 hidden，如果是则直接跳过控件生成，将值放入 form data。

### 5.6 Flux 独有能力的利用（增强项，非首版必须）

Flux 的结构节点让 flux-web.xlib 有机会生成更优化的 Schema：

| Flux 能力 | 使用场景 | 对应 XView 概念 |
|----------|---------|----------------|
| `data-source` | 命名数据源 + 轮询 + 公式 | page.initApi、enum 的 `@dict:` |
| `fragment(isolate)` | 子树数据隔离 | 子表编辑区域 |
| `reaction` | 响应式联动 | `visibleOn`/`disabledOn` 表达式 |
| `loop` | 循环渲染 | to-many 关系列表 |
| `recurse` | 递归渲染 | 树形数据 |
| Action DAG | 复杂操作链 | action.next 链 |

首版可以全部降级为 Flux 基础结构（平铺映射），后续逐步利用高级能力。

---

## 6. 代码生成模板的适配

### 6.1 view.xml 生成模板（Stage A）：无需改动

`_gen/_{objName}.view.xml.xgen` 中的 `<controlLib>/nop/web/xlib/control.xlib</controlLib>` 是默认值，用户可通过 Delta 覆盖为 flux-control.xlib。

view-gen.xlib 完全与渲染器无关，不需要改动。

web-gen.xlib（GenLayout 等）完全与渲染器无关，不需要改动。

### 6.2 page.yaml 生成模板（Stage A）：需要 flux 版本

当前 `main.page.yaml.xgen` 硬编码：
```xml
<web:GenPage view="${metaInfo.objName}.view.xml" page="main"
             xpl:lib="/nop/web/xlib/web.xlib" />
```

Flux 版本：
```xml
<flux-web:GenPage view="${metaInfo.objName}.view.xml" page="main"
                  xpl:lib="/nop/web/xlib/flux-web.xlib" />
```

**两种策略**：

**策略 A：全局配置切换**。在 codegen 模板中读取配置决定使用哪个 xlib：
```xml
<c:script>
    const fluxMode = $config.var('nop.web.renderer') == 'flux';
    const lib = fluxMode ? '/nop/web/xlib/flux-web.xlib' : '/nop/web/xlib/web.xlib';
    const ns = fluxMode ? 'flux-web' : 'web';
</c:script>
```

**策略 B：独立模板目录**。为 Flux 提供独立的 `/nop/templates/orm-web-flux/` 模板目录。

推荐**策略 A**——改动最小，且用户可通过 Delta 定制单个 page.yaml 来选择渲染器。

### 6.3 运行时动态切换（可选增强）

更灵活的方案是在 `PageProvider.loadPage()` 中根据请求参数决定用哪个渲染器。但这需要 Java 层改动，且 page.yaml 的 `x:gen-extends` 在加载时就执行了，运行时切换较难实现。

更实际的做法是：一个 view.xml 可以同时有 main.page.yaml（AMIS）和 main.flux.page.yaml（Flux），前端路由根据需要请求不同文件。

---

## 7. 标签实现详解（核心标签伪代码）

### 7.1 GenPage

```xml
<GenPage outputMode="xjson">
    <attr name="view" mandatory="true" type="String" stdDomain="v-path"/>
    <attr name="page" mandatory="true" type="String"/>
    <attr name="forPicker" type="Boolean" optional="true"/>
    <attr name="fixedProps" optional="true" stdDomain="csv-set"/>
    <source>
        <c:include src="flux-web/impl_GenPage.xpl"/>
    </source>
</GenPage>
```

`flux-web/impl_GenPage.xpl` 与现有 `web/impl_GenPage.xpl` 几乎相同，唯一区别是 controlLib 默认路径：
```xml
let controlLib = XplLibHelper.loadLib(viewModel.controlLib || '/nop/web/xlib/flux-control.xlib');
```

### 7.2 GenGridImpl（CRUD 核心差异）

```xml
<GenGridImpl outputMode="xjson">
    <attr name="gridModel"/>
    <attr name="objMeta"/>
    <attr name="genScope"/>
    <attr name="bizObjName" implicit="true"/>
    <attr name="i18nRoot" implicit="true"/>
    <attr name="controlLib" implicit="true"/>
    <attr name="ignoreCols" implicit="true" optional="true" stdDomain="csv-set"/>
    <source>
        <crud xpl:attrs="xpl('thisLib:FluxGridDefaultAttrs', gridModel)">
            <initApi xpl:attrs="xpl('thisLib:NormalizeApi',gridModel.initApi,genScope)"
                     xpl:if="gridModel.initApi"/>
            <api xpl:attrs="xpl('thisLib:NormalizeApi',gridModel.api,genScope)"/>
            <saveOrderApi xpl:attrs="xpl('thisLib:NormalizeApi',gridModel.saveOrderApi,genScope)"
                          xpl:if="gridModel.saveOrderApi"/>
            <columns j:list="true">
                <thisLib:GenGridCols gridModel="${gridModel}" objMeta="${objMeta}" ignoreCols="${ignoreCols}"/>
            </columns>
            <!-- Flux 没有 headerToolbar/footerToolbar 预置组件，
                 toolbar/footerToolbar 由 grid_crud.xpl 手动组装 -->
        </crud>
    </source>
</GenGridImpl>
```

### 7.3 NormalizeAction（动作转换核心）

```xml
<NormalizeAction outputMode="none">
    <attr name="action"/>
    <attr name="genScope"/>
    <attr name="viewModel" implicit="true"/>
    <source><![CDATA[
        let result = _.delete({...action}, ['actionType','type','actions']);

        const iconOnly = action.iconOnly && action.icon;
        result.label = iconOnly ? null : action.label;
        result.icon = action.icon;
        result.level = iconOnly ? null : action.level;
        result.tooltip = iconOnly ? action.label : null;

        // *** 核心规则：onClick 已存在则直接透传，不做任何转换 ***
        if(action.onClick != null){
            result.onClick = action.onClick;
            return _.filterNull(result);
        }

        // === onClick 不存在时，从 api/actionType/dialog/drawer 转换 ===
        let actionType = action.actionType;
        if(!actionType){
            if(action.dialog) actionType = 'dialog';
            else if(action.drawer) actionType = 'drawer';
            else actionType = 'ajax';
        }

        // 构建 Flux ActionSchema DAG
        let then = [];

        // confirmText → 头部插入 confirm 守卫
        let whenGuard = null;
        if(action.confirmText){
            whenGuard = { message: action.confirmText };
        }

        // 主动作
        if(actionType == 'ajax' || action.api){
            let api = action.api;
            if(api){
                then.push({
                    type: 'api',
                    url: api.url.$renderTemplateForScope('{@','}',genScope),
                    "gql:selection": api['gql:selection']?.$renderTemplateForScope('{@','}',genScope),
                    method: api.method || 'POST',
                    data: api.withFormData ? genScope.formData : api.data
                });
            }
        } else if(actionType == 'dialog'){
            let dialog = xpl('thisLib:LoadPage', action.dialog.page, action.dialog, genScope?.fixedProps);
            then.push({ type: 'dialog', dialog });
        } else if(actionType == 'drawer'){
            let drawer = xpl('thisLib:LoadPage', (action.drawer||action.dialog).page,
                            action.drawer||action.dialog, genScope?.fixedProps);
            then.push({ type: 'drawer', drawer });
        } else if(actionType == 'reload'){
            then.push({ type: 'component', action: 'reload', target: action.target });
        } else if(actionType == 'close'){
            then.push({ type: 'component', action: 'close' });
        } else if(actionType == 'copy'){
            then.push({ type: 'set-value', target: action.target, value: action.copy });
        } else if(actionType == 'toast'){
            then.push({ type: 'toast', message: action.toast });
        }

        // feedback (操作结果反馈对话框)
        if(action.feedback?.page){
            let feedback = xpl('thisLib:LoadPage', action.feedback.page, action.feedback);
            then.push({ type: 'dialog', dialog: feedback });
        }

        // 组装 onClick
        if(whenGuard){
            result.onClick = { type: 'confirm', when: whenGuard, then };
        } else {
            result.onClick = then.length == 1 ? then[0] : { type: 'sequence', then };
        }

        return _.filterNull(result);
    ]]></source>
</NormalizeAction>
```

### 7.4 GenFormSimpleCell（关键差异点）

```xml
<GenFormSimpleCell outputMode="none">
    <attr name="formCell"/>
    <attr name="formModel" implicit="true"/>
    <attr name="objMeta" implicit="true"/>
    <attr name="bizObjName" implicit="true"/>
    <attr name="i18nRoot" implicit="true"/>
    <attr name="controlLib" implicit="true"/>
    <attr name="fixedProps" implicit="true" optional="true" stdDomain="csv-set"/>
    <source><![CDATA[
        // === 以下逻辑与 web.xlib 完全相同（渲染器无关）===
        let cellModel = formModel.getCell(formCell.id);
        let propMeta = objMeta?.getProp(cellModel?.prop || formCell.id);
        let rd = formCell.readonly || cellModel?.readonly
                     || (!cellModel?.custom && (formModel.editMode == 'update' or formModel.editMode == 'edit')
                         && propMeta && !propMeta?.updatable)
                     || (!cellModel?.custom && formModel.editMode == 'add' && propMeta && !propMeta?.insertable);

        if(!rd && fixedProps?.contains(formCell.id)){
           rd = true;
           $out.appendChild(location(),"_hidden",{name:formCell.id}) // Flux 无 hidden，输出特殊标记
        }

        let mode = cellModel?.editMode || (rd? 'view' : formModel.editMode) || 'view'
        const labelKey = '@i18n:cell.'+i18nRoot+"."+formCell.id+(cellModel?.label ? '' : ',prop.label.'
               +bizObjName+'.'+propMeta?.name);
        let label = labelKey.$i18n(cellModel?.label || propMeta?.displayName);

        let mandatory = cellModel?.mandatory ?? propMeta?.mandatory;
        if(mode.startsWith('query'))
            mandatory = cellModel?.mandatory ?? propMeta?.['ui:queryMandatory']

        let filterOp = cellModel?.filterOp || propMeta?.['ui:filterOp']
                  || cellModel?.['ui:filterOp'] || propMeta?.['xui:defaultFilterOp']

        // === 以下输出结构改为 Flux 属性名 ===
        let cell = {
            name: (mode.startsWith('query') && !formCell.id.startsWith('v_') ?
                        'filter_' + formCell.id + (filterOp ? '__'+filterOp:'')
                        : formCell.id),
            placeholder: cellModel?.placeholder,
            label: formCell.hideLabel ? '' : label,   // Flux 不支持 false，用空串
            required: mandatory ? true : null,
            hint: cellModel?.hint,
            // *** 核心差异：visibleOn→visible, disabledOn→disabled ***
            disabled: cellModel?.disabledOn || (rd ? true : null),
            visible: cellModel?.visibleOn,
            // *** Flux 无 staticOn/clearValueOnHidden/requiredOn ***
            flex: cellModel?.columnRatio ?? formModel?.defaultColumnRatio,
            submitOnChange: cellModel?.submitOnChange ?? formModel?.submitOnChange,
            desc: cellModel?.desc
        };

        // 权限标记保持一致（后端处理）
        if(cellModel){
            if(cellModel['xui:permissions'])
               cell['xui:permissions'] = cellModel['xui:permissions'];
            if(cellModel['xui:roles'])
               cell['xui:roles'] = cellModel['xui:roles'];
        }

        // 控件解析逻辑与 web.xlib 完全相同
        let cellXpl = cellModel?.genControl;
        let control = null;
        if(cellXpl != null){
            control = eval(cellXpl,{dispMeta:cellModel,propMeta,bizObjName,objMeta,editMode:mode,mandatory});
        }else if(cellModel?.view){
            control = xpl `<thisLib:GenDispView dispMeta="${cellModel}" editMode="${mode}" mandatory="${mandatory}" />`
        }else{
            control = xpl `<thisLib:DefaultControl dispMeta="${cellModel}" editMode="${mode}" mandatory="${mandatory}" />`;
        }
        if(control != null){
            cell.putAll(control);
        }
        return cell;
    ]]></source>
</GenFormSimpleCell>
```

---

## 8. 实施计划

### Phase 1: flux-control.xlib（控件映射库）

**工作量**：75 个标签，每个标签 5-15 行，总计约 1000 行。

**优先级**：最高。它是 flux-web.xlib 的 `DefaultControl` 的依赖，且与 web.xlib 完全解耦，可独立开发和测试。

**验证方法**：编写测试，对每种 `{mode}-{type}` 调用 `XuiHelper.getControlTag()` 并验证返回的 Flux JSON 结构。

### Phase 2: flux-web.xlib 核心标签

**范围**：GenPage、GenForm/GenFormImpl、GenFormBody、GenLayoutGroups、GenFormTable/Row/Cell/SimpleCell、GenGrid/GenGridImpl、GenGridCols/Col、DefaultControl、NormalizeApi、GenActions。

**工作量**：约 20 个标签 + 4 个 xpl 文件 + 5 个 stub xpl 文件，总计约 700 行。

**验证方法**：对 `nop-auth` 模块的 `NopAuthUser.view.xml` 生成 Flux JSON，验证 CRUD + Form 结构正确。

### Phase 3: 完整页面类型

**范围**：page_crud.xpl、page_simple.xpl、page_picker.xpl、page_tabs.xpl、grid_crud.xpl、GenInputTable、GenTable、GenDispView、LoadPage、NormalizeAction。

**工作量**：约 10 个标签 + 5 个 xpl 文件，总计约 400 行。

**验证方法**：全量生成 `nop-auth` 模块所有页面，人工检查 Flux JSON 结构。

### Phase 4: 代码生成模板适配

**范围**：修改 `_gen/_{objName}.view.xml.xgen` 中的 controlLib 默认值，提供 flux 版本的 `main.page.yaml.xgen`。

**工作量**：2-3 个模板文件改动。

### Phase 5: 废弃 web.xlib

在全链路验证 Flux 输出正确后，移除 web.xlib、control.xlib 及 web/ 目录。

---

## 9. 风险矩阵

| 风险 | 等级 | 影响 | 缓解 |
|------|------|------|------|
| Flux Schema 仍在演进 | 中 | 控件映射需持续更新 | flux-control.xlib 独立文件，Delta 可覆盖 |
| NormalizeAction 映射不完整 | 低 | 复杂操作链可手写 onClick 绕过转换 | onClick 优先规则：转换仅作自动生成兜底，手写 onClick 直接透传 |
| hidden 字段处理不一致 | 低 | 表单提交可能丢值 | 使用 _hidden 标记 + form data 注入 |
| CRUD 工具栏功能缺失 | 中 | filter-toggler/columns-toggler 等功能丢失 | Flux 侧自定义实现或接受功能降级 |
| 富文本 HTML→Markdown 切换 | 中 | 数据格式不兼容 | 确认业务需求，可能需要数据迁移 |
| 双向递归依赖 | 低 | flux-control 的 to-many 调用 flux-web | Phase 1+2 必须同时完成 |
| Flux 表达式语法差异 | 低 | `${var}` 语法一致，但过滤器/函数不同 | 检查 XView 中表达式的 Flux 兼容性 |

---

## 10. 总结

| 维度 | 结论 |
|------|------|
| **可行性** | 高。核心 Java 工具类（XuiHelper）渲染器无关，XView 模型层复用，只需重写 xpl 输出层 |
| **工作量** | flux-control.xlib ~1000 行（75 个控件映射）+ flux-web.xlib ~1000 行（37 个标签）+ 9 个 xpl 文件 ~400 行 ≈ **2400 行** |
| **Java 改动** | 零。所有 Java 工具类直接复用 |
| **模型改动** | 零。XView（xview.xdef）完全复用 |
| **代码生成模板** | 极少。controlLib 默认值 + page.yaml xgen 的 xpl:lib 路径 |
| **关键瓶颈** | NormalizeAction 的 AMIS→Flux 动作 DAG 转换；CRUD 工具栏功能降级 |
| **推荐首步** | 先写 flux-control.xlib（55 个控件映射），它是独立可测试的最小单元 |
