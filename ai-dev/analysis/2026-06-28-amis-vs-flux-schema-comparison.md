# Nop-AMIS Schema vs Flux Schema 对比分析

> 对比对象：Nop 平台 AMIS 输出（`xview.xdef` → `page.yaml` → AMIS JSON，基于 `ai-dev/analysis/2026-06-28-amis-component-schema.md`）
> vs Flux 前端框架（`nop-chaos-flux` 的 `flux-guide/flux-types/*.d.ts` 定义的 TypeScript Schema）

---

## 1. 核心架构差异

| 维度 | Nop-AMIS (Java 后端) | Flux (TypeScript 前端) |
|------|---------------------|----------------------|
| 渲染引擎 | Baidu AMIS (React) | @nop-chaos/flux-react (自研 React) |
| Schema 定义语言 | XDef XML (xview.xdef, form.xdef, grid.xdef) | TypeScript `.d.ts` （编译期类型） |
| Schema 生成方式 | 服务器运行时：Java XPL 模板生成 JSON | 客户端直接写 JSON，编译期校验 |
| 组件数量 | ~61 种（通过 control.xlib 注册） | ~87 种（通过 RendererRegistry 注册） |
| 类型安全 | 运行时 resolve（`XuiHelper.getControlTag()`） | 编译期 TypeScript 类型检查 |
| 继承机制 | `xdef:ref` 组合 + XML 属性继承 | TypeScript 接口继承（`BaseSchema`→`BoundFieldSchemaBase`→具体） |
| 项目结构 | Maven 模块，Java + XPL 模板 | pnpm monorepo，TypeScript 包 |

---

## 2. 组件 Schema 设计对比

### 2.1 基础属性

| 属性 | Nop-AMIS | Flux | 差异说明 |
|------|---------|------|---------|
| type | `type: string`（必选） | `type: string`（必选） | 概念一致 |
| id | `id?: string` | `id?: string` | 一致 |
| name | `name?: string` | `name?: string` | 一致 |
| label | `label?: string \| false` | `label?: string` | Flux 不支持 `false` 隐藏 label |
| className | `className?: string` | `className?: string` | 一致 |
| title | `title?: string` | `title?: SchemaTpl` | Flux 支持模板表达式 |
| visibleOn | `visibleOn?: string`（表达式） | `visible?: SchemaBoolean` | **命名差异**：Nop 统一 `*On` 后缀，Flux 用 `visible/hidden/disabled` |
| disabledOn | `disabledOn?: string` | `disabled?: SchemaBoolean` | 同上 |
| when | 无 | `when?: SchemaBoolean` | Flux 独有：条件激活（子树生命周期控制） |
| validateOn | 组件级 `validations` | `validateOn?: string[]` | Flux 支持 `change/blur/submit` |
| onMount | 无（靠 action api） | `onMount?: ActionSchema[]` | Flux 有生命周期回调 |

### 2.2 页面容器

| 特性 | Nop-AMIS `type: "page"` | Flux `type: "page"` |
|------|------------------------|---------------------|
| `header`/`footer` | 无 | 有 `header`/`footer` 区域 |
| `aside` | 有（`page_crud.xpl` 生成） | 无（靠 `flex` 布局组合） |
| `pullRefresh` | 无 | 有（移动端下拉刷新） |
| `initApi` | 有 | 有 |
| `size`/`modalSize` | 有（picker 模式） | 无（dialog 尺寸在 dialog 上） |

### 2.3 表格/CRUD

| 特性 | Nop-AMIS `type: "crud"` | Flux `type: "crud"` |
|------|------------------------|--------------------|
| 列定义 | `columns` | `columns`（同 `TableSchema`） |
| 操作列 | `operation` 特殊列类型 + `buttons` | 无内置操作列（toolbar 加按钮实现） |
| 工具栏 | `headerToolbar`/`footerToolbar`（预置组件） | `toolbar`/`footerToolbar`（任意 SchemaInput） |
| 批量操作 | `bulkActions` | 无内置（通过 selectedRowKeys 实现） |
| 分页 | `pagination`/`switch-per-page`/`statistics` 预置 | `pagination` 对象配置或 false 关闭 |
| 过滤 | `<filter>` 子表单 | 手动在 toolbar 加输入控件 |
| `footable` | 有（列折叠） | 无 |

### 2.4 表单控件

| 控件类型 | Nop-AMIS type | Flux type | 差异 |
|---------|--------------|-----------|------|
| 文本 | `input-text` | `input-text` | 几乎一致 |
| 数字 | `input-number` / `native-number` | `input-number` | Nop 多一个 `native-number`（原生数字） |
| 密码 | `input-password` | `input-password` | Flux 有 `showRevealToggle` |
| 多行文本 | `textarea` | `textarea` | 一致 |
| 富文本 | `input-rich-text` | `markdown-editor` | **不同**：Nop 用 HTML 编辑器，Flux 用 Markdown |
| 下拉选择 | `select` / `list-select` | `select` | 基本一致 |
| 单选框 | `radios` | `radio-group` | 命名差异 |
| 复选框 | `checkbox` | `checkbox` / `checkbox-group` | Flux 拆为两个类型 |
| 开关 | `switch` | `switch` | 一致 |
| 日期 | `input-date` / `input-datetime` | `input-date` / `input-datetime` / `input-time` | Flux 多一个 time，且用联合类型 |
| 日期范围 | `input-date-range` / `input-datetime-range` | `date-range` | 命名差异 |
| 标签 | `input-tag` | `tag-list` | 命名差异 |
| 数组 | `input-array` | `array-editor` / `array-field` / `combo` | Flux 拆为 3 个类型，更丰富 |
| 文件上传 | `input-file` / `input-image` | `input-file` / `input-image` | 基本一致 |
| 树选择 | `tree-select` | `tree-select` / `input-tree` | Flux 多一个 `input-tree`（可编辑树） |
| 代码编辑 | `editor` | `editor` | 一致 |
| 隐藏字段 | `hidden` | 无 | Flux 不需要（不写进 body 即可） |
| 级联选择 | 无 | `cascading-select` | Flux 有级联专用组件 |
| 穿梭框 | 无 | `transfer` | Flux 有 |
| 条件构建器 | 无 | `condition-builder` | Flux 有 |
| KV 编辑 | 无 | `key-value` | Flux 有 |
| 对象/变体字段 | 无 | `object-field` / `variant-field` / `detail-field` | Flux 有 |

### 2.5 显示控件

| 控件类型 | Nop-AMIS | Flux |
|---------|---------|------|
| 纯文本 | `static` | `text` |
| 图片 | `static-image` / `static-images` | `image` |
| 映射显示 | `static-mapping` | `mapping` / `status` |
| 模板 | `tpl` | `html` / `markdown` |
| 代码显示 | `code` / `pre-static` | `json-view` |
| 卡片 | 无 | `card` / `cards` |
| 徽标 | 无 | `badge` |
| 进度条 | 无 | `progress` |
| 空状态 | 无 | `empty` |
| 图表 | 无 | `chart` |
| 轮播 | 无 | `carousel` |
| 音视频 | 无 | `audio` / `video` |
| 图标 | 无 | `icon` |
| 按钮组 | `dropdown-button`（仅下拉） | `button-group` / `dropdown-button` |
| 树状显示 | 无 | `tree` |
| 列表 | 无 | `list` |
| 时间线 | 无 | `timeline` |
| 步骤条 | `wizard step` | `steps` |

### 2.6 布局容器

| 控件类型 | Nop-AMIS | Flux |
|---------|---------|------|
| 分组 | `group` | `container` / `flex` |
| 字段集 | `fieldSet` | `fieldset` |
| 折叠面板 | `collapse-group` / `collapse` | `collapse`（含 accordion） |
| 标签页 | `tabs` / `tab` | `tabs` |
| 分割线 | `divider` | `separator` |
| 网格布局 | 无 | `grid`（列宽 grid） |
| Flex | 无 | `flex` |
| alert/提示 | 无 | `alert` |
| loading | 无 | `spinner` |

### 2.7 结构节点（Flux 独有）

| 类型 | 用途 |
|------|------|
| `fragment` | 条件分组 + 数据隔离 |
| `loop` | 循环渲染（items + itemName + body） |
| `recurse` | 递归渲染（树形数据） |
| `reaction` | 响应式监听（watch + when + actions） |
| `dynamic-renderer` | 延迟加载渲染器 |
| `scope-debug` | 调试用：显示当前数据域 |

Nop-AMIS 没有等价物——这些需要在 Nop 中用 XPL 模板语法（`<c:for>`、`<c:if>`）在生成阶段实现。

---

## 3. 动作系统对比

| 特性 | Nop-AMIS | Flux |
|------|---------|------|
| 定义方式 | `actionType` + `api`/`dialog`/`drawer` 字段 | Action Algebra：`onClick` 携带 `ActionSchema` |
| 动作链 | 简单链（ajax→dialog→close） | DAG（`then`/`onError`/`onSettled`/`parallel`） |
| 条件守卫 | `confirmText` | `when` 字段 |
| 重试 | 无 | `retry`（times/delay/strategy/maxDelay） |
| 防抖 | 无 | `debounce` / `throttle` |
| 缓存 | `api.cache`（仅 HTTP 缓存） | `OperationControlConfig.cacheTTL` |
| 去重 | 无 | `dedup`（cancel-previous/parallel/ignore-new） |
| 超时 | 无 | `timeout` |
| 组件方法 | `actionType="reload"`/`close` | `component:submit`/`component:reset` 等 |
| 扩展方式 | 无 | `namespace:method` 任意扩展 |
| `onEvent` | 有（xjson-map 格式） | 无（统一用 onClick/onChange 等） |

**结论**：Flux 的动作系统大幅领先 Nop-AMIS，支持 DAG、重试、防抖、去重、超时、条件守卫等工业级特性。

---

## 4. 数据流对比

| 特性 | Nop-AMIS | Flux |
|------|---------|------|
| 作用域 | 组件树隐式继承 | 显式 `ScopeRef` 词法作用域 |
| 初始化数据 | `data` / `initApi` | `data` / `initApi` |
| 数据源容器 | 无 | `data-source`（命名数据源 + 轮询 + 公式） |
| 数据服务 | 无 | `service`（数据容器） |
| 合并策略 | 无 | `mergeStrategy`（replace/append/prepend/merge/upsert） |
| 组件间通信 | `target`/`reload` 字符串引用 | Action Algebra `refreshTable`/`refreshSource` |
| 数据隔离 | 无 | `fragment.isolate` 实现子树隔离 |
| 派生数据 | 无 | `data-source.formula` 公式计算 |

---

## 5. 表达式系统对比

| 特性 | Nop-AMIS | Flux |
|------|---------|------|
| 变量引用 | `${variable}` | `${variable}` |
| 过滤器 | 有限（取决于后端实现） | 20+ 内置过滤器（TRIM/UPPER 等） |
| 数学运算 | `${expr}`（部分支持） | `$Math.PI` |
| 日期函数 | 有限 | `$Date.format()` |
| JSON 工具 | 有限 | `$JSON.stringify()` |
| 国际化 | `@i18n:key` 或 `${'@i18n:key'}` | `t('key')` |
| 条件简写 | 无 | `||` 默认值 |
| 编译时优化 | 无 | 编译期预编译 + 依赖跟踪 |

---

## 6. 校验系统对比

| 特性 | Nop-AMIS | Flux |
|------|---------|------|
| 校验定义 | 组件级 `validations` (map) | 组件级 `validations` (map) |
| 自定义校验 | `validator` xjson-map | 无（通过表达式） |
| 触发时机 | 无 | `validateOn`: change/blur/submit |
| 错误显示时机 | 无 | `showErrorOn`: touched/dirty/visited/submit |
| 表单级校验 | `rules` | `FormSchema.validations` |
| 回调 | 无 | `onSubmitSuccess`/`onSubmitError`/`onValidateError` |

---

## 7. Flux-guide 文档充分性评估

### 7.1 已充分覆盖的内容

| 文档 | 覆盖度 | 评价 |
|------|--------|------|
| `01-quickstart.md` | ✅ 17 个代码段 | 覆盖最常用场景 |
| `02-reference.md` | ✅ 表达式、API、动作、数据流、校验、结构节点 | 核心机制完整 |
| `flux-types/*.d.ts` | ✅ 87 种组件接口 | 权威知识源 |
| `design-patterns/*` | ✅ 9 个场景 cookbook | 实战导向 |
| `mobile/*` | ✅ 5 个移动端组件 | 专项覆盖 |

### 7.2 不足之处

| 缺失 | 影响 | 严重度 |
|------|------|--------|
| **组件生命周期文档**（onMount/onUnmount 行为） | 开发者不清楚组件何时初始化、何时销毁 | 中 |
| **渲染器注册指南**（如何写自定义 renderer + 注册） | 无法扩展新组件类型 | 高 |
| **ScopeRef 数据流详细说明**（子→父通信、isolate 边界） | 复杂场景的数据流难以推测 | 中 |
| **性能优化指南**（`React.memo`/`useScopeSelector` 原理、避免无效渲染） | 大表单可能出现性能问题 | 中 |
| **AMIS → Flux 迁移指南** | Nop 生态的 AMIS 用户迁移困难 | 中 |
| **Accessibility 指南**（aria 属性支持） | 无法满足无障碍要求 | 低 |
| **flux-types 与源码的关系说明**（是手工抽取还是自动生成？） | 可能存在类型漂移 | 低 |

### 7.3 flux-types 覆盖率

```
flux-types/schema.d.ts 类型数: 87（含 mobile）
Nop-AMIS 组件类型数: 61
交集: ~35（同类控件）
Flux 独有: ~52（结构节点、移动端、高级控件、显示控件）
Nop-AMIS 独有: ~26（CRUD 工具栏组件如 filter-toggler/columns-toggler 等）
```

Flux 的类型覆盖面比 Nop-AMIS 更广，尤其在**高级表单控件**（combo/transfer/condition-builder）、**数据容器**（data-source/service）、**结构节点**（fragment/loop/recurse/reaction）、**显示控件**（card/carousel/audio/video）和**移动端组件**方面有明显优势。

---

## 8. 结论

| 维度 | 胜出方 | 理由 |
|------|--------|------|
| 组件丰富度 | Flux | 87 vs 61 类型，尤其结构节点和高级表单 |
| 动作系统 | Flux | 工业级 Action Algebra DAG |
| 数据流 | Flux | 命名数据源、合并策略、数据隔离 |
| 类型安全 | Flux | TypeScript 编译期校验 |
| 服务器端生成 | Nop-AMIS | Java 运行时生成 JSON，适合 Java 后端场景 |
| 与 Nop 生态集成 | Nop-AMIS | 原生支持 view.xml + xmeta + GraphQL |
| CRUD 开箱即用 | Nop-AMIS | filter-toggler/columns-toggler 等预置工具栏 |
| 文档覆盖度 | Flux | flux-guide 基本充分，但有 3 个中优先级缺口 |
