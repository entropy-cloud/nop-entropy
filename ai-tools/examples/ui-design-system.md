# 设计系统规范 v4.0

## 1. 概述

本规范用于统一企业级运营控制台类产品的视觉语言、组件表达与页面生成规则，适用于 dashboard、CRUD、主菜单系统、表单、表格、列表、弹窗、抽屉等典型后台场景。

本规范同时承担两类职责：

- 作为设计与开发协作的统一依据，明确页面结构、视觉层级、状态反馈与主题体系。
- 作为模板生成与组件扩展的提示词基础，使后续新增页面或未预定义组件时，仍能保持一致的视觉风格与实现方式。

技术落地要求如下：

- 页面使用 `React` 开发。
- 样式体系使用 `Tailwind CSS`。
- 基础组件优先采用 `shadcn/ui`。
- 主题与风格差异统一通过 CSS 变量、Tailwind 映射和组件变体控制，不直接修改 `shadcn/ui` 源码。

---

## 2. 设计原则

### 2.1 产品气质

整体风格应体现以下特征：

- 专业、可信、清晰，适用于企业级运营与管理场景。
- 信息层级明确，强调秩序感与可读性。
- 装饰控制在辅助层，不干扰数据、表单与操作路径。
- 保留适度精致感与现代感，但避免营销化、海报化或强情绪化表达。

### 2.2 结构优先原则

页面视觉效果首先依赖结构秩序建立，而非依赖大面积色块或高强度装饰。所有页面应优先保证：

- 清晰的信息分区
- 稳定的导航层级
- 统一的容器边界
- 明确的标题与正文层级
- 可预期的交互反馈

### 2.3 色彩使用原则

界面以低饱和中性色为主，强调色仅用于关键操作、当前状态、焦点反馈与局部高亮。不得将强调色作为大面积正文背景或主要阅读区底色。

### 2.4 材质切换原则

主题差异主要通过材质表达实现，而不是通过改变信息结构实现。不同主题下，页面布局、组件骨架与交互逻辑应保持稳定，仅在背景、边框、透明度、圆角、阴影与氛围层上产生差异。

### 2.5 组件统一原则

所有组件与页面模块均应遵循统一结构：

1. 容器层：定义背景、边框、圆角、阴影与留白。
2. 标题层：定义模块名称、说明、分组标签与辅助信息。
3. 内容层：承载主要数据、表单、列表或操作。
4. 状态层：体现 hover、focus、active、selected、disabled、趋势、语义状态等反馈。

当后续新增未预定义组件时，应优先将其映射到上述结构后再进行视觉扩展。

---

## 3. 主题体系

### 3.1 主题维度

设计系统采用双维主题结构：

- `材质主题`：`classic` / `glass`
- `明暗模式`：`light` / `dark`

共形成四种组合：

- `classic + light`
- `classic + dark`
- `glass + light`
- `glass + dark`

任意页面、组件、模块均应支持在这四种组合下保持统一风格与可读性。

### 3.2 Classic 主题定义

`classic` 主题用于强调稳定、专业、清晰的企业级后台体验。

特征如下：

- 使用实体面板与清晰描边
- 阴影较轻，层级关系明确但不过度悬浮
- 主色偏蓝，适合运营、审批、设置、数据管理等场景
- 内容边界清楚，适合高信息密度页面

### 3.3 Glass 主题定义

`glass` 主题用于表达更轻盈、精致、现代的控制台体验。

特征如下：

- 使用半透明面板与适度背景模糊
- 圆角略大于 classic 主题
- 阴影更柔和，背景允许存在轻量氛围层
- 交互主色偏蓝青，激活强调偏青绿，环境辅助色可使用薄荷绿与淡紫色

限制如下：

- 氛围色仅用于背景或装饰层，不承担正文信息表达
- 半透明效果不得影响表格、表单、正文和状态文本的可读性

### 3.4 明暗模式原则

#### Light 模式

- 页面背景明亮、干净，边框清晰
- 阴影较轻，强调信息结构和界面秩序
- 适合长时间浏览和高频操作场景

#### Dark 模式

- 页面背景更深，文字层级依赖明度对比建立
- 边框与分割线应保留足够可见度，不可完全消失
- 高亮元素需克制使用，避免界面产生噪声

#### Dark + Glass 模式补充规则

- 面板应适度提高不透明度，以确保可读性
- 装饰层弱化，不可压过业务内容
- 正文、表头、说明文字需满足足够对比度，建议不低于 WCAG AA 标准

---

## 4. 页面结构规范

### 4.1 标准后台壳层

标准页面采用以下结构：

- 左侧固定导航
- 顶部吸顶 Header
- 中央内容区
- 移动端侧边栏切换为抽屉或侧滑层

此结构适用于 dashboard、列表页、详情页、设置页、审批页与复合型管理页面。

### 4.2 关键布局尺寸

| 项目 | 建议值 |
|------|--------|
| 侧边栏宽度 | `252px` |
| 顶栏高度 | `68px` |
| 内容区最大宽度 | `1480px` |
| 桌面内容横向内边距 | `28px` |
| 桌面内容纵向内边距 | `32px` |
| 模块间距 | `20px / 24px / 28px` |

### 4.3 页面布局模式

常用布局模式如下：

- dashboard：4 列指标卡 + 2 列主内容区
- CRUD 列表页：顶部操作区 + 筛选区 + 表格区 + 分页区
- 详情页：概览卡片 + 主要信息区 + 辅助信息区
- 设置页：左侧分组导航 + 右侧配置面板

### 4.4 响应式规则

- `<=1024px`：侧边栏切换为抽屉
- `<=1024px`：双列内容通常收敛为单列
- `<=640px`：指标卡、表单行、操作组优先使用单列堆叠

---

## 5. 可生成的视觉规则

### 5.1 生成目标

后续基于本规范生成页面或组件时，应确保生成结果具备以下特征：

- 明确属于企业级后台控制台风格
- 具备稳定的信息架构和统一的模块边界
- 能够自然扩展 dashboard、CRUD、导航、详情、弹窗、表单、表格等场景
- 当新增组件未在模板中预定义时，仍可依据本规范保持视觉一致性

### 5.2 新组件扩展方法

对于未预定义组件，按以下顺序处理：

1. 判断组件类型：导航类、输入类、数据展示类、反馈类、容器类、浮层类。
2. 套用统一骨架：容器层、标题层、内容层、状态层。
3. 确定强调方式：主操作、当前状态、趋势提示、语义状态或焦点反馈。
4. 根据主题切换材质表达：classic 偏实体，glass 偏半透明。
5. 将颜色、阴影、圆角映射到现有 token，不新增无来源的独立样式。

### 5.3 生成约束

后续生成页面或组件时，应始终遵守以下规则：

1. 新组件优先纳入卡片、面板、列表项、弹层等容器体系中，不直接裸露在页面背景层。
2. 强调色只用于主操作、当前状态、焦点与少量趋势提示。
3. 新组件圆角必须复用既有圆角体系。
4. hover 反馈应以轻抬升、轻背景变化、轻阴影增强为主，不得使用夸张缩放或复杂动效。
5. focus 态必须清晰统一，使用主色 ring 或等价可访问性方案。
6. 文本层级必须明确区分标题、正文、说明与禁用状态。
7. 数据密集型区域优先保证整洁和可读性，不使用过度装饰。
8. glass 主题中的装饰仅保留在背景氛围层，不进入正文阅读区域。

### 5.4 反向约束

生成结果不得出现以下偏差：

- 不得表现为营销官网、电商活动页、海报化页面
- 不得使用大面积高饱和主色铺底
- 不得使用强霓虹、过亮发光描边或厚重拟物效果
- 不得使所有组件都呈现相同的浮层胶囊感
- 不得因透明度过高而损害 dark 模式可读性

### 5.5 页面/组件生成提示词模板

以下模板可直接用于后续页面或组件生成：

```text
请生成一个企业级运营控制台风格的 {页面/组件名称}，用于后台管理场景。

要求：
1. 使用 React + Tailwind CSS + shadcn/ui。
2. 保持专业、可信、低噪声、强秩序感的整体视觉。
3. 页面结构遵循后台壳层逻辑，组件结构遵循“容器层 + 标题层 + 内容层 + 状态层”。
4. 同时兼容 classic / glass 与 light / dark 四种主题组合。
5. 以低饱和中性色表面为主，强调色仅用于主操作、当前状态、焦点与趋势提示。
6. classic 主题偏实体、清晰；glass 主题偏半透明、柔和、精致，但不得影响可读性。
7. 保持与 dashboard、CRUD、主菜单、表格、表单、列表等后台场景一致的设计语言。

输出要求：
- React 组件结构
- Tailwind 类名
- shadcn/ui 组件组合方式
- 如需额外样式，仅通过 CSS 变量或少量语义化扩展类实现
```

---

## 6. 视觉规范

### 6.1 颜色系统

颜色变量使用 HSL 格式定义，以便与 `shadcn/ui` 和 Tailwind token 映射保持一致。除标准语义色外，建议同时维护业务扩展变量，用于侧边栏、面板、页面背景、文本层级等场景。

#### 6.1.1 Classic 主题

| 变量名 | 用途 | Light 模式 | Dark 模式 |
|--------|------|------------|-----------|
| `--primary` | 主色 | `219 79% 46%` | `212 88% 61%` |
| `--primary-foreground` | 主色前景 | `0 0% 100%` | `0 0% 100%` |
| `--foreground` | 默认文字 | `221 39% 15%` | `210 40% 98%` |
| `--muted-foreground` | 次级文字 | `215 18% 32%` | `214 20% 74%` |
| `--border` | 标准边框 | `214 24% 86%` | `216 24% 22%` |
| `--background` | 根背景 | `210 40% 96%` | `217 49% 8%` |
| `--card` | 卡片背景 | `0 0% 100%` | `215 39% 15%` |
| `--ring` | 聚焦环 | `219 79% 46%` | `212 88% 61%` |

扩展变量：

| 变量名 | 用途 | Light 模式 | Dark 模式 |
|--------|------|------------|-----------|
| `--page-bg` | 页面背景 | `#eef3f8` | `#0a1320` |
| `--panel-bg` | 面板背景 | `#ffffff` | `#162132` |
| `--panel-border` | 面板边框 | `#dbe3ef` | `#2c3b51` |
| `--sidebar-bg` | 侧栏背景 | `#f7f9fc` | `#0f1724` |
| `--sidebar-border` | 侧栏边框 | `#d7dfeb` | `#1f2b3d` |
| `--sidebar-hover` | 侧栏 hover | `#edf2f8` | `#182334` |
| `--sidebar-active` | 侧栏激活背景 | `#e5efff` | `#1c365d` |
| `--sidebar-active-color` | 侧栏激活文字 | `#1d4ed8` | `#93c5fd` |
| `--text-strong` | 强标题文字 | `#142033` | `#f1f5f9` |
| `--text-soft` | 常规正文文字 | `#334155` | `#dbe4f0` |

#### 6.1.2 Glass 主题

| 变量名 | 用途 | Light 模式 | Dark 模式 |
|--------|------|------------|-----------|
| `--primary` | 主交互色 | `203 76% 42%` | `193 82% 52%` |
| `--primary-foreground` | 主色前景 | `0 0% 100%` | `0 0% 100%` |
| `--foreground` | 默认文字 | `214 30% 20%` | `210 40% 98%` |
| `--muted-foreground` | 次级文字 | `214 15% 34%` | `214 16% 76%` |
| `--border` | 标准边框基底 | `0 0% 100%` | `0 0% 100%` |
| `--background` | 根背景 | `210 35% 97%` | `215 39% 10%` |
| `--card` | 卡片背景基底 | `0 0% 100%` | `217 43% 13%` |
| `--ring` | 聚焦环 | `203 76% 42%` | `193 82% 52%` |

扩展变量：

| 变量名 | 用途 | Light 模式 | Dark 模式 |
|--------|------|------------|-----------|
| `--page-bg` | 页面背景 | `linear-gradient(180deg, #f4f7fb 0%, #eaf0f7 100%)` | `linear-gradient(180deg, #0b1320 0%, #101827 100%)` |
| `--panel-bg` | 面板背景 | `rgba(255, 255, 255, 0.92)` | `rgba(19, 31, 48, 0.92)` |
| `--panel-border` | 面板边框 | `rgba(214, 226, 240, 0.88)` | `rgba(148, 163, 184, 0.14)` |
| `--sidebar-bg` | 侧栏背景 | `rgba(247, 249, 252, 0.92)` | `rgba(15, 23, 36, 0.9)` |
| `--sidebar-border` | 侧栏边框 | `rgba(215, 223, 235, 0.9)` | `rgba(148, 163, 184, 0.16)` |
| `--sidebar-hover` | 侧栏 hover | `rgba(237, 242, 248, 0.86)` | `rgba(30, 41, 59, 0.84)` |
| `--sidebar-active` | 侧栏激活背景 | `rgba(59, 130, 246, 0.14)` | `rgba(56, 189, 248, 0.18)` |
| `--sidebar-active-color` | 侧栏激活文字 | `#0369a1` | `#67e8f9` |
| `--accent-teal` | 激活强调色 | `#0d9488` | `#2dd4bf` |
| `--accent-teal-soft` | 激活浅底色 | `rgba(20, 184, 166, 0.12)` | `rgba(20, 184, 166, 0.2)` |
| `--accent-mint` | 背景氛围色一 | `rgba(167, 243, 208, 0.9)` | `rgba(20, 184, 166, 0.4)` |
| `--accent-lavender` | 背景氛围色二 | `rgba(196, 181, 253, 0.9)` | `rgba(139, 92, 246, 0.4)` |
| `--text-strong` | 强标题文字 | `#122033` | `#f8fafc` |
| `--text-soft` | 常规正文文字 | `#314155` | `#d6e0eb` |

#### 6.1.3 语义色

| 变量名 | 用途 | Light 模式 | Dark 模式 |
|--------|------|------------|-----------|
| `--success` | 成功 | `160 84% 39%` | `160 70% 50%` |
| `--success-bg` | 成功背景 | `160 84% 95%` | `160 30% 20%` |
| `--warning` | 警告 | `38 92% 50%` | `38 82% 60%` |
| `--warning-bg` | 警告背景 | `38 92% 95%` | `38 30% 20%` |
| `--danger` | 危险 | `0 84% 60%` | `0 70% 50%` |
| `--danger-bg` | 危险背景 | `0 84% 95%` | `0 30% 20%` |

#### 6.1.4 使用规则

- 主色用于 CTA、当前导航、焦点、激活边条、指标强调线。
- 语义色用于 Badge、状态提示、趋势说明、小型图标容器。
- 背景氛围色仅用于 glass 主题的页面背景装饰层。
- 正文区、表格区、表单区始终以中性色表面为主。

### 6.2 间距系统

采用 `4px` 基础网格，推荐如下：

| Token | 数值 | 用途 |
|-------|------|------|
| `--space-1` | `4px` | 图标与文字微间距 |
| `--space-2` | `8px` | 紧凑间距 |
| `--space-3` | `12px` | 常规组件内间距 |
| `--space-4` | `16px` | 表单组、列表项内间距 |
| `--space-5` | `20px` | 卡片与区块内边距 |
| `--space-6` | `24px` | 模块级内边距与区块间距 |
| `--space-7` | `28px` | 页面主内容区块间距 |
| `--space-8` | `32px` | 页面边距 |

### 6.3 字体系统

| 属性 | 值 |
|------|----|
| 字族 | `"Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif` |
| 默认字号 | `14px` |
| 正文字重 | `400` |
| 强调文字 | `500 / 600` |
| 页面标题 | `700` |

推荐字阶：

| Token | 尺寸 | 用途 |
|-------|------|------|
| `--text-xs` | `12px` | 辅助说明 |
| `--text-sm` | `13px` | 区块标题、说明文字 |
| `--text-base` | `14px` | 正文、按钮、表格内容 |
| `--text-md` | `15px` | 次级标题 |
| `--text-lg` | `18px` | 页面标题 |
| `--text-xl` | `30px` | 指标主数值 |

排版规则：

- 页面标题与指标数值字重应明显高于正文。
- 区块标题可使用全大写与较大字距强化秩序感。
- 辅助说明文字应降低对比度，但仍需保持可读性。

### 6.4 圆角系统

| Token | 数值 | 用途 |
|-------|------|------|
| `--radius-sm` | `8px` | 小按钮、输入框 |
| `--radius-md` | `10px` | 导航项、图标按钮 |
| `--radius-lg` | `12px` | 标准卡片 |
| `--radius-xl` | `16px` | glass 卡片、小型浮层 |
| `--radius-2xl` | `20px` | 大型 glass 面板、弹层 |
| `--radius-full` | `9999px` | Badge、Pill |

### 6.5 阴影系统

| Token | 值 | 用途 |
|-------|----|------|
| `--shadow-sm` | `0 2px 4px rgba(15, 23, 42, 0.04)` | classic 常规卡片 |
| `--shadow-md` | `0 14px 30px rgba(15, 23, 42, 0.08)` | classic hover / 强调卡片 |
| `--shadow-glass-sm` | `0 6px 18px rgba(15, 23, 42, 0.05)` | glass 常规卡片 |
| `--shadow-glass-md` | `0 16px 38px rgba(15, 23, 42, 0.10)` | glass hover / 强调卡片 |

阴影使用规则：

- 默认阴影应轻量，避免厚重漂浮感。
- hover 时仅提升一个层级。
- glass 主题允许阴影更柔和，但不可过于梦幻化。

### 6.6 动效与过渡

| Token | 值 | 用途 |
|-------|----|------|
| `--transition-fast` | `0.15s ease` | hover / focus 快速反馈 |
| `--transition-base` | `0.2s ease` | 默认状态变化 |
| `--transition-slow` | `0.3s ease` | 抽屉、展开、浮层入场 |

动效原则：

- 动效应服务于状态识别，不服务于展示炫技。
- 组件 hover 以轻微位移、阴影增强、背景变化为主。
- 抽屉、弹窗、菜单使用平滑过渡，不使用夸张缩放或旋转。

### 6.7 特殊效果

#### 6.7.1 Glass 背景氛围层

glass 主题允许使用柔和背景渐变与模糊光斑，以建立轻量科技感。该层仅作为环境背景，不参与信息表达。

亮色建议：

```css
background: linear-gradient(180deg, #f4f7fb 0%, #eaf0f7 100%);
```

暗色建议：

```css
background: linear-gradient(180deg, #0b1320 0%, #101827 100%);
```

#### 6.7.2 Glass 模糊效果

glass 主题中的卡片、侧栏、顶栏可使用 `backdrop-filter: blur(8px)` 至 `blur(12px)`。不建议使用过强模糊，以免界面显得漂浮失焦。

### 6.8 图标系统

- 图标库统一使用 `Lucide`。
- 图标默认继承文字颜色 `currentColor`。
- 建议尺寸：`16px`、`18px`、`20px`、`24px`。
- 图标与文本组合时保持垂直居中，并使用 `8px` 或 `12px` 间距。

### 6.9 响应式断点

| Token | 数值 |
|-------|------|
| `--breakpoint-sm` | `640px` |
| `--breakpoint-md` | `768px` |
| `--breakpoint-lg` | `1024px` |
| `--breakpoint-xl` | `1280px` |
| `--breakpoint-2xl` | `1536px` |

---

## 7. 交互规范

### 7.1 通用状态

所有可交互元素应至少定义以下状态：

| 状态 | 规则 |
|------|------|
| `hover` | 轻微背景变化，可叠加 `translateY(-1px)` 或阴影增强 |
| `focus` | 使用主色 ring，保证键盘可访问性 |
| `active` | 按压时阴影略收，位移归零或背景加深 |
| `disabled` | 降低对比度与不透明度，禁止 hover 反馈 |
| `selected` | 使用浅色高亮背景、强调文字与可选边条 |

### 7.2 按钮

- 主按钮：使用主色或渐变主色，文字为白色。
- 次按钮：使用面板底色配合描边。
- 危险按钮：使用危险色语义，避免误用主色。
- 小按钮：可用于表格行内操作，尺寸需明显小于主操作按钮。

### 7.3 输入框

- 默认边框使用 `--border` 或对应 input token。
- focus 时边框与 ring 切换为主色。
- readonly 状态应降低背景对比但保留清晰边界。
- glass 模式下允许半透明输入框，但不得降低输入内容可读性。

### 7.4 表格

- 表头使用弱色背景、小号大写文字、清晰分隔线。
- 行 hover 使用极浅主色染色。
- 行内操作使用小尺寸 outline 按钮。
- 数据密集区域优先稳定和整洁，不做复杂装饰。

### 7.5 导航菜单

- 分组标题弱化处理，强调信息分组而非视觉抢占。
- 当前导航项需具备背景高亮、文字高亮与左侧边条。
- 二级菜单展开使用平滑过渡。
- 移动端通过抽屉承载导航。

### 7.6 弹窗与抽屉

- 遮罩背景使用半透明深色，并可叠加轻微模糊。
- 弹窗与抽屉的圆角大于常规卡片，但仍保持后台语境。
- 标题区、正文区、底部操作区应层级分明。

### 7.7 Toast / 状态反馈

- 通知组件使用清晰语义色区分成功、警告、错误与信息。
- 位置建议在右上角或页面操作区附近。
- 动画应轻量，不影响持续操作。

### 7.8 主题切换机制

通过 JavaScript 切换根元素的 `data-theme` 和暗色模式状态。例如：

```tsx
<html data-theme={theme} className={mode === "dark" ? "dark" : ""}>
```

或：

```js
document.documentElement.dataset.theme = 'glass'
document.documentElement.dataset.mode = 'dark'
```

---

## 8. React + Tailwind CSS + shadcn/ui 落地规范

### 8.1 token 分层

建议将 token 分为两层：

- `shadcn/ui` 标准 token：`--background`、`--foreground`、`--card`、`--primary`、`--border`、`--ring` 等
- 业务扩展 token：`--page-bg`、`--panel-bg`、`--panel-border`、`--sidebar-bg`、`--text-strong`、`--text-soft` 等

### 8.2 推荐组件清单

优先使用以下 `shadcn/ui` 组件：

- `Button`
- `Card`
- `Input`
- `Select`
- `Textarea`
- `Badge`
- `Table`
- `Tabs`
- `Dialog`
- `Sheet`
- `DropdownMenu`
- `Tooltip`
- `Separator`
- `ScrollArea`
- `Toast` 或 `Sonner`

### 8.3 组件映射建议

| 视觉对象 | 推荐实现 |
|----------|----------|
| 页面壳层 | 自定义 `AppShell` + `Sheet` |
| 左侧导航项 | `Button` 变体或语义化 `button` |
| 顶栏图标按钮 | `Button` 的 `size="icon"` |
| 面板 / 卡片 | `Card` |
| 指标卡 | `Card` 扩展变体 |
| 状态标签 | `Badge` 扩展变体 |
| 筛选表单 | `Input` + `Select` + `Button` |
| 数据表格 | `Table` |
| 列表块 | `Card` 或语义化容器 |
| 移动端侧栏 | `Sheet` |

### 8.4 Tailwind 使用原则

- 结构、布局、间距、排版优先通过 Tailwind 类名表达。
- 颜色、圆角、阴影、模糊通过 CSS 变量与 Tailwind token 映射统一控制。
- 页面内如需扩展风格，应优先使用语义化 class、`cva` 或组件变体。
- 避免为单一页面引入大量临时 CSS。

### 8.5 主题变量基础示例

以下变量可作为 `globals.css` 的基础实现：

```css
:root,
html[data-theme="classic"] {
  --background: 210 40% 96%;
  --foreground: 221 39% 15%;
  --card: 0 0% 100%;
  --card-foreground: 221 39% 15%;
  --popover: 0 0% 100%;
  --popover-foreground: 221 39% 15%;
  --primary: 219 79% 46%;
  --primary-foreground: 0 0% 100%;
  --secondary: 210 33% 97%;
  --secondary-foreground: 221 39% 15%;
  --muted: 210 33% 97%;
  --muted-foreground: 215 18% 32%;
  --accent: 217 100% 96%;
  --accent-foreground: 217 70% 45%;
  --destructive: 0 84% 60%;
  --destructive-foreground: 0 0% 100%;
  --border: 214 24% 86%;
  --input: 214 24% 86%;
  --ring: 219 79% 46%;
  --radius: 0.75rem;

  --page-bg: #eef3f8;
  --panel-bg: #ffffff;
  --panel-border: #dbe3ef;
  --sidebar-bg: #f7f9fc;
  --sidebar-border: #d7dfeb;
  --sidebar-hover: #edf2f8;
  --sidebar-active: #e5efff;
  --sidebar-active-color: #1d4ed8;
  --text-strong: #142033;
  --text-soft: #334155;
  --shadow-sm: 0 2px 4px rgba(15, 23, 42, 0.04);
  --shadow-md: 0 14px 30px rgba(15, 23, 42, 0.08);
  --glass-blur: none;
  --sidebar-width: 252px;
  --header-height: 68px;
}

html[data-theme="glass"] {
  --background: 210 35% 97%;
  --foreground: 214 30% 20%;
  --card: 0 0% 100%;
  --card-foreground: 214 30% 20%;
  --popover: 0 0% 100%;
  --popover-foreground: 214 30% 20%;
  --primary: 203 76% 42%;
  --primary-foreground: 0 0% 100%;
  --secondary: 175 60% 95%;
  --secondary-foreground: 173 84% 30%;
  --muted: 210 25% 95%;
  --muted-foreground: 214 15% 34%;
  --accent: 173 80% 96%;
  --accent-foreground: 173 84% 32%;
  --destructive: 0 84% 60%;
  --destructive-foreground: 0 0% 100%;
  --border: 0 0% 100%;
  --input: 0 0% 100%;
  --ring: 203 76% 42%;
  --radius: 0.875rem;

  --page-bg: linear-gradient(180deg, #f4f7fb 0%, #eaf0f7 100%);
  --panel-bg: rgba(255, 255, 255, 0.92);
  --panel-border: rgba(214, 226, 240, 0.88);
  --sidebar-bg: rgba(247, 249, 252, 0.92);
  --sidebar-border: rgba(215, 223, 235, 0.9);
  --sidebar-hover: rgba(237, 242, 248, 0.86);
  --sidebar-active: rgba(59, 130, 246, 0.14);
  --sidebar-active-color: #0369a1;
  --accent-teal: #0d9488;
  --accent-teal-soft: rgba(20, 184, 166, 0.12);
  --accent-mint: rgba(167, 243, 208, 0.9);
  --accent-lavender: rgba(196, 181, 253, 0.9);
  --text-strong: #122033;
  --text-soft: #314155;
  --shadow-sm: 0 6px 18px rgba(15, 23, 42, 0.05);
  --shadow-md: 0 16px 38px rgba(15, 23, 42, 0.1);
  --glass-blur: blur(8px);
  --sidebar-width: 252px;
  --header-height: 68px;
}

html.dark[data-theme="classic"],
html[data-theme="classic"][data-mode="dark"] {
  --background: 217 49% 8%;
  --foreground: 210 40% 98%;
  --card: 215 39% 15%;
  --card-foreground: 210 40% 98%;
  --popover: 215 39% 15%;
  --popover-foreground: 210 40% 98%;
  --primary: 212 88% 61%;
  --primary-foreground: 0 0% 100%;
  --secondary: 216 28% 18%;
  --secondary-foreground: 210 40% 98%;
  --muted: 216 28% 18%;
  --muted-foreground: 214 20% 74%;
  --accent: 214 45% 25%;
  --accent-foreground: 210 40% 98%;
  --destructive: 0 70% 50%;
  --destructive-foreground: 0 0% 100%;
  --border: 216 24% 22%;
  --input: 216 24% 22%;
  --ring: 212 88% 61%;

  --page-bg: #0a1320;
  --panel-bg: #162132;
  --panel-border: #2c3b51;
  --sidebar-bg: #0f1724;
  --sidebar-border: #1f2b3d;
  --sidebar-hover: #182334;
  --sidebar-active: #1c365d;
  --sidebar-active-color: #93c5fd;
  --text-strong: #f1f5f9;
  --text-soft: #dbe4f0;
  --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.3);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.4);
}

html.dark[data-theme="glass"],
html[data-theme="glass"][data-mode="dark"] {
  --background: 215 39% 10%;
  --foreground: 210 40% 98%;
  --card: 217 43% 13%;
  --card-foreground: 210 40% 98%;
  --popover: 217 43% 13%;
  --popover-foreground: 210 40% 98%;
  --primary: 193 82% 52%;
  --primary-foreground: 0 0% 100%;
  --secondary: 173 30% 18%;
  --secondary-foreground: 173 70% 66%;
  --muted: 215 28% 18%;
  --muted-foreground: 214 16% 76%;
  --accent: 173 32% 18%;
  --accent-foreground: 173 70% 66%;
  --destructive: 0 70% 50%;
  --destructive-foreground: 0 0% 100%;
  --border: 0 0% 100%;
  --input: 0 0% 100%;
  --ring: 193 82% 52%;
  --radius: 1rem;

  --page-bg: linear-gradient(180deg, #0b1320 0%, #101827 100%);
  --panel-bg: rgba(19, 31, 48, 0.92);
  --panel-border: rgba(148, 163, 184, 0.14);
  --sidebar-bg: rgba(15, 23, 36, 0.9);
  --sidebar-border: rgba(148, 163, 184, 0.16);
  --sidebar-hover: rgba(30, 41, 59, 0.84);
  --sidebar-active: rgba(56, 189, 248, 0.18);
  --sidebar-active-color: #67e8f9;
  --accent-teal: #2dd4bf;
  --accent-teal-soft: rgba(20, 184, 166, 0.2);
  --accent-mint: rgba(20, 184, 166, 0.4);
  --accent-lavender: rgba(139, 92, 246, 0.4);
  --text-strong: #f8fafc;
  --text-soft: #d6e0eb;
  --shadow-sm: 0 4px 16px rgba(0, 0, 0, 0.3);
  --shadow-md: 0 8px 32px rgba(0, 0, 0, 0.4);
  --glass-blur: blur(8px);
}
```

---

## 9. 验收标准

后续使用本规范生成页面、模板或组件时，应满足以下标准：

1. 页面明显属于企业级后台控制台，而非营销型站点。
2. 壳层结构完整，包含导航、顶栏与内容区的清晰关系。
3. 信息层级明确，卡片、标题、操作与正文有清楚的主次关系。
4. 颜色使用克制，以中性色为主、强调色为辅。
5. 同时兼容 `classic / glass` 与 `light / dark`。
6. 新组件可自然融入现有体系，不破坏整体风格。
7. glass 主题具备氛围感，但不影响可读性。
8. dark 模式下边界、文字、状态反馈仍清晰可辨。
9. 实现方式符合 `React + Tailwind CSS + shadcn/ui` 约束。
10. 规范可直接用于后续模板生成与组件扩展。

---

## 10. 总结

本规范以企业级运营控制台场景为核心，通过统一的页面结构、清晰的视觉层级、双维主题体系、稳定的组件骨架与可落地的 token 设计，建立了一套可持续扩展的设计系统。

设计与开发团队在后续执行中应遵循以下基本原则：

- 以结构秩序建立可信度
- 以材质差异区分主题风格
- 以局部强调色表达状态与操作
- 以 React、Tailwind CSS、shadcn/ui 作为统一实现基础

在此基础上，系统既可直接生成标准 dashboard 与 CRUD 模板，也可在新增组件和新场景时保持稳定、一致、可扩展的视觉表现。
