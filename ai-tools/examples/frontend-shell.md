# pnpm Monorepo 后台系统需求说明 / 生成提示词

> 使用方式：将本文件作为"系统提示词"或"完整需求文档"提供给代码生成模型，要求其基于此从零实现一个可运行的 monorepo 前端项目。假设模型**看不到任何现有源码**。

---

## 0. 角色与目标

你现在是一个资深前端架构师和产品工程师，目标是：

- 从零搭建一个**基于 pnpm monorepo 管理的前端项目**；
- 实现一个「AI + 流程编排 + 仪表盘 + 插件」结合的**后台管理系统**；
- **业务功能与视觉复杂度要达到甚至超越一个成熟的商业后台模板**（有丰富页面、图表、表单、流程画布等），而不是简单 Demo；
- 在功能上至少包含本文件中明确列出的所有模块与行为。

执行本任务时：

- **不允许依赖任何现有源码**；只能依据本文件的描述；
- 可以自由设计内部实现，但必须满足业务需求和技术约束。

---

## 1. 技术与架构约束

在满足业务功能的前提下，必须满足以下技术约束，确保项目现代、可维护、可扩展。

### 1.1 Monorepo 形态

1. 使用 **pnpm** 作为包管理器和 workspace 管理工具。
2. 目录结构如下：

```txt
.
├─ package.json                 # 根 package 配置
├─ pnpm-workspace.yaml          # pnpm workspace 配置
├─ tsconfig.base.json           # 基础 TypeScript 配置
├─ tailwind.config.ts           # 共享 Tailwind 配置
├─ apps/                        # 所有正式业务应用
│  ├─ main/                     # 主后台应用（包含完整业务功能）
│  │  ├─ src/
│  │  ├─ public/
│  │  │   └─ locales/           # i18n 翻译文件
│  │  ├─ package.json
│  │  ├─ vite.config.ts
│  │  └─ tailwind.config.ts     # 主应用 Tailwind 配置（继承共享配置）
│  └─ plugin-demo/              # 插件示例应用
│     ├─ src/
│     ├─ package.json
│     └─ vite.config.ts
├─ packages/
│  ├─ ui/                        # 通用 UI 组件库（shadcn 风格）
│  │  ├─ src/
│  │  ├─ package.json
│  │  └─ tsconfig.json
│  ├─ core/                      # 后台框架核心（布局、多标签页、权限守卫、插件加载基础设施等，与业务无关）
│  │  ├─ src/
│  │  ├─ package.json
│  │  └─ tsconfig.json
│  └─ shared/                    # 通用类型与工具（如菜单项、用户等标准类型定义）
│     ├─ src/
│     ├─ package.json
│     └─ tsconfig.json
└─ examples/                     # 可选，用于存放其他教学示例（可保留为空）
```

3. 所有 app 和 package 的 TypeScript 配置通过 `tsconfig.base.json` 统一，开启严格模式。
4. 包间依赖使用 `workspace:*` 协议。

### 1.2 关键依赖版本（保持高版本）

运行时依赖（示例版本，要求不低于）：

- React: `"react": "^19.2.0"`, `"react-dom": "^19.2.0"`
- Zustand: `"zustand": "^5.0.11"`
- React Query: `"@tanstack/react-query": "^5.90.21"`
- React Router DOM: `"react-router-dom": "^7.13.1"`
- i18n:
    - `"i18next": "^25.8.14"`
    - `"react-i18next": "^16.5.6"`
    - `"i18next-browser-languagedetector": "^8.2.1"`
    - `"i18next-http-backend": "^3.0.2"`
- UI & 动画:
    - Radix UI 系列（dialog, dropdown-menu, select, tabs, tooltip, slot, checkbox, label, scroll-area, separator, collapsible 等）
    - `"lucide-react": "^0.577.0"`
    - `"class-variance-authority": "^0.7.1"`
    - `"tailwind-merge": "^3.5.0"`
    - `"tailwindcss": "^3.4.19"`
    - `"tailwindcss-animate": "^1.0.7"`
- 可视化与流程:
    - `"@xyflow/react": "^12.10.1"` (流程图/节点编辑)
    - `"recharts": "^2.12.0"` (图表)
- 通知: `"sonner": "^2.0.7"`
- AI 相关: `"ai": "^4.0.0"`, `"@ai-sdk/openai": "^1.0.0"` (只要求接口占位)
- **插件动态加载**: `"systemjs": "^6.15.1"` (用于插件系统)
- 工具库: `"clsx": "^2.1.1"`

开发依赖（简要）:

- Vite 7（React + TS 插件）
- TypeScript 5.9 严格模式
- ESLint 9 + React 插件 + prettier config
- Vitest（单测）
- Playwright（端到端）
- Husky + lint-staged
- MSW（Mock Service Worker）

### 1.3 共享库配置（插件系统关键）

**插件系统必须使用 SystemJS 格式**，主应用与插件需要共享以下所有库，避免重复打包：

| 类别          | 共享库                                                                                                                                                                                                                                                                                   | 说明                            |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| **核心框架**  | react, react-dom, react/jsx-runtime                                                                                                                                                                                                                                                      | 避免多实例，保证 Hooks 正常工作 |
| **路由**      | react-router-dom                                                                                                                                                                                                                                                                         | 共享路由实例                    |
| **UI 组件库** | @radix-ui/react-dialog, @radix-ui/react-dropdown-menu, @radix-ui/react-select, @radix-ui/react-tabs, @radix-ui/react-tooltip, @radix-ui/react-separator, @radix-ui/react-checkbox, @radix-ui/react-label, @radix-ui/react-scroll-area, @radix-ui/react-slot, @radix-ui/react-collapsible | shadcn/ui 的底层依赖            |
| **图标**      | lucide-react                                                                                                                                                                                                                                                                             | 图标库                          |
| **样式工具**  | tailwind-merge, class-variance-authority, clsx                                                                                                                                                                                                                                           | 样式工具函数                    |
| **图表**      | recharts                                                                                                                                                                                                                                                                                 | 图表库                          |
| **通知**      | sonner                                                                                                                                                                                                                                                                                   | Toast 通知                      |
| **状态管理**  | zustand                                                                                                                                                                                                                                                                                  | 状态管理                        |
| **数据请求**  | @tanstack/react-query                                                                                                                                                                                                                                                                    | 数据请求                        |
| **流程图**    | @xyflow/react                                                                                                                                                                                                                                                                            | 流程编辑                        |
| **国际化**    | i18next, react-i18next                                                                                                                                                                                                                                                                   | 多语言支持                      |

**共享方式**：

- 编译时：插件 package.json 中声明这些库为 peerDependencies。
- 运行时：主应用在入口处通过 SystemJS 将自身已加载的库实例注册到全局模块注册表，插件通过 SystemJS 加载时自动使用主应用的实例。

**正确注册方法**（主应用初始化时执行）：
```ts
import * as React from 'react';
import * as ReactDOM from 'react-dom';
import * as ReactRouterDOM from 'react-router-dom';
import * as Zustand from 'zustand';
// ... 导入其他共享库

// 1. 定义一个虚拟的基础路径（如 "/nop/"），用于存放共享模块
const SHARED_MODULE_BASE = './nop/';  // 相对路径，也可以是绝对路径

// 2. 为每个共享库创建一个虚拟的 URL 并注册实例
const sharedModules = {
  'react': React,
  'react-dom': ReactDOM,
  'react-router-dom': ReactRouterDOM,
  'zustand': Zustand,
  // ... 其他库
};

for (const [name, lib] of Object.entries(sharedModules)) {
  // 构造虚拟的模块路径
  const modulePath = SHARED_MODULE_BASE + name + '.js';
  // 将模块名映射到该虚拟路径
  System.addImportMap({
    imports: {
      [name]: modulePath,
    },
  });
  // 将库实例注册到虚拟路径上
  System.set(modulePath, lib);
}
```

- 插件必须使用 SystemJS 格式构建（构建输出 `.system.js` 文件），且构建时需将共享库标记为 external（例如在 Vite 中通过 `rollupOptions.external` 配置）。
- 插件必须默认导出一个 React 组件作为入口。

- 主应用需要提供加载插件的能力（根据配置动态加载）。
- 主应用需要提供插件通信接口（访问状态、i18n、主题、通知等）。

### 1.4 视觉复杂度要求

> 项目的视觉复杂度需要接近成熟的商业后台模板，而不是简单的 Demo。

1. **玻璃拟态效果（Glassmorphism）**
    - 卡片、弹窗、侧边栏等组件需要支持玻璃拟态风格。
    - 实现方式：通过全局 CSS 变量（如 `--glass-background`, `--glass-blur`）定义半透明背景、模糊程度和阴影；组件通过添加预设类（如 `glass`）应用这些变量。可提供 `<GlassCard>` 等封装组件简化使用。
    - 半透明背景（`backdrop-filter: blur()`），模糊效果，悬浮感阴影，渐变边框。

2. **渐变效果**
    - 按钮、标签、标题等需要支持渐变色；主按钮使用渐变背景；文字渐变效果；背景渐变。

3. **动画效果**
    - 页面切换过渡动画、菜单展开/折叠动画、按钮悬停动画、卡片悬停动画、通知进入/退出动画、主题切换过渡动画。

4. **细节打磨**
    - 一致的圆角大小、阴影层次、间距系统；响应式设计（移动端适配）；无障碍支持（ARIA 属性）；键盘导航支持。

5. **主题完整性**
    - 每套主题需要覆盖所有 CSS 变量；主题切换需要平滑过渡；图表、流程图等第三方组件需要跟随主题；玻璃拟态效果需要跟随主题调整透明度。

### 1.5 主题系统变量规范

> 本节定义主题系统的变量命名规范，不包含具体颜色值。具体颜色值由 `theme-factory.md` 定义。
> AI 生成代码时应严格按照本节定义的变量名实现，以便后续通过 `theme-factory.md` 进行精细调整。

#### 1.5.1 主题数据结构

```typescript
// apps/main/src/types/theme.ts

export type DisplayMode = 'light' | 'dark' | 'system';
export type VisualStyle = 'default' | 'glassmorphism';

export interface ThemeColors {
  // 主色调（Primary）- 品牌主色
  primary: string;
  primaryHover: string;
  primaryActive: string;
  
  // 次要色（Secondary）- 辅助色
  secondary: string;
  secondaryHover: string;
  secondaryActive: string;
  
  // 背景色（Background）- 页面背景
  background: string;
  backgroundSecondary: string;
  backgroundTertiary: string;
  
  // 前景色（Foreground）- 文字颜色
  foreground: string;
  foregroundSecondary: string;
  foregroundMuted: string;
  
  // 边框色（Border）- 边框和分割线
  border: string;
  borderHover: string;
  borderActive: string;
  
  // 状态色（Status）- 操作反馈
  success: string;
  warning: string;
  error: string;
  info: string;
  
  // 强调色（Accent）- 高亮和特殊标记
  accent: string;
  accentSecondary: string;
}

export interface GlassmorphismColors {
  // 玻璃拟态效果（Light Mode）
  glassBackground: string;
  glassBorder: string;
  glassShadow: string;
  glassBlur: string;
  
  // 玻璃拟态效果（Dark Mode）
  glassBackgroundDark: string;
  glassBorderDark: string;
  glassShadowDark: string;
  glassBlurDark: string;
}

export interface Theme {
  id: string;              // 主题唯一标识，如 'ocean-depths'
  name: string;            // 显示名称，如 '海洋深处'
  description: string;     // 主题描述
  light: ThemeColors;      // 浅色模式配色
  dark: ThemeColors;       // 深色模式配色
  glassmorphism: GlassmorphismColors;  // 玻璃拟态配色
  bestUsedFor: string[];   // 适用场景
}

export interface ThemeConfig {
  themeId: string;         // 当前主题 ID
  displayMode: DisplayMode; // 显示模式
  visualStyle: VisualStyle; // 视觉风格
}
```

#### 1.5.2 CSS 变量映射

生成代码时，必须将 `ThemeColors` 映射为以下 CSS 变量：

```css
/* :root 元素上的 CSS 变量 */

/* 主色调 */
--color-primary
--color-primary-hover
--color-primary-active

/* 次要色 */
--color-secondary
--color-secondary-hover
--color-secondary-active

/* 背景色 */
--color-background
--color-background-secondary
--color-background-tertiary

/* 前景色 */
--color-foreground
--color-foreground-secondary
--color-foreground-muted

/* 边框色 */
--color-border
--color-border-hover
--color-border-active

/* 状态色 */
--color-success
--color-warning
--color-error
--color-info

/* 强调色 */
--color-accent
--color-accent-secondary

/* 玻璃拟态（当 visualStyle 为 glassmorphism 时启用） */
--glass-background
--glass-border
--glass-shadow
--glass-blur
```

#### 1.5.3 主题文件结构

```
apps/main/src/
├── config/
│   └── themes/
│       ├── index.ts           # 导出所有主题和工具函数
│       ├── types.ts           # 主题类型定义（可从 types/theme.ts 引入）
│       ├── ocean-depths.ts    # 海洋深处主题
│       ├── tech-innovation.ts # 科技创新主题
│       ├── forest-canopy.ts   # 森林树冠主题
│       ├── sunset-boulevard.ts# 日落大道主题
│       └── modern-minimalist.ts # 现代简约主题
├── stores/
│   └── theme-store.ts         # Zustand 主题状态管理
└── utils/
    └── theme-css.ts           # CSS 变量生成和应用工具
```

#### 1.5.4 核心实现要求

1. **主题 Store（Zustand）**
   ```typescript
   // apps/main/src/stores/theme-store.ts
   // 必须使用 zustand + persist 中间件
   // 持久化 key: 'theme-config'
   // 提供: themeId, displayMode, visualStyle
   // 提供: setTheme, setDisplayMode, setVisualStyle, updateConfig
   ```

2. **CSS 变量应用函数**
   ```typescript
   // apps/main/src/utils/theme-css.ts
   // generateCSSVariables(theme, mode, visualStyle): Record<string, string>
   // applyTheme(theme, mode, visualStyle): void
   // - 将 CSS 变量应用到 document.documentElement
   // - 设置 data-theme, data-mode, data-style 属性
   ```

3. **Tailwind 配置**
   ```typescript
   // apps/main/tailwind.config.ts
   // 必须将 CSS 变量映射为 Tailwind 颜色
   // 示例: colors.primary.DEFAULT: 'var(--color-primary)'
   ```

4. **主题切换组件**
   ```tsx
   // apps/main/src/components/ThemeSelector.tsx
   // 展示 5 个主题选项
   // 每个选项显示主题名称、描述、预览色块
   // 支持 displayMode 和 visualStyle 切换
   ```

#### 1.5.5 五套主题的色调描述

| 主题 ID | 显示名称 | 色调描述 | 设计风格 |
|---------|----------|----------|----------|
| `ocean-depths` | 海洋深处（Ocean Depths） | 深青蓝色调，带有海洋深处的宁静感 | 专业稳重 |
| `tech-innovation` | 科技创新（Tech Innovation） | 紫蓝色调，具有科技未来感 | 创新大胆 |
| `forest-canopy` | 森林树冠（Forest Canopy） | 森林绿色调，自然有机 | 自然环保 |
| `sunset-boulevard` | 日落大道（Sunset Boulevard） | 橙红色调，温暖有活力 | 创意热情 |
| `modern-minimalist` | 现代简约（Modern Minimalist） | 黑白灰色调，极简现代 | 简洁专业 |

#### 1.5.6 第三方组件主题集成

1. **Recharts 图表主题**
    - 必须响应主题变化
    - 使用 `--color-primary`, `--color-secondary` 等变量作为图表配色
    - 提供 `getChartTheme(colors: ThemeColors)` 工具函数

2. **@xyflow/react 流程图主题**
    - 节点颜色跟随主题
    - 连线颜色使用 `--color-border`
    - 画布背景使用 `--color-background-tertiary`
    - 提供 `getFlowTheme(colors: ThemeColors)` 工具函数

3. **Sonner 通知主题**
    - 通知样式跟随主题
    - 使用主题状态色（success, warning, error, info）

#### 1.5.7 便于定制
- 核心架构（Store、CSS 变量名、Tailwind 配置）保持不变
- 只需修改 `apps/main/src/config/themes/*.ts` 文件即可完成主题定制

### 1.6 核心数据模型约定

为保障框架的可复用性，以下数据结构在整个产品系列中必须保持一致。所有类型定义应放在 `packages/shared/src/types/` 下，供主应用、插件和 core 包共同引用。

#### 1.6.1 用户对象（User）
```typescript
// packages/shared/src/types/user.ts
export interface User {
  id: string;
  username: string;
  nickname?: string;
  avatar?: string;
  email?: string;
  roles: string[];           // 用户拥有的角色列表
}
```

#### 1.6.2 认证状态（AuthState）
```typescript
export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  token?: string;
}
```

#### 1.6.3 菜单项（MenuItem）
```typescript
// packages/shared/src/types/menu.ts
export interface MenuItem {
  id: string;                // 唯一标识
  title?: string;            // 直接文本（后备，当 titleKey 不存在时使用）
  titleKey?: string;         // i18n 键（优先使用，通过 t(titleKey) 获取显示文本）
  path: string;              // 路由路径，如 '/dashboard'
  icon?: string;             // 图标名称（Lucide React 图标名）
  children?: MenuItem[];     // 子菜单
  badge?: string;            // 徽标（如 'new'）
  pageType: 'builtin' | 'plugin';  // 页面类型
  componentId?: string;      // 当 pageType='builtin' 时，对应内置组件标识
  pluginUrl?: string;        // 当 pageType='plugin' 时，插件模块 URL
  roles?: string[];          // 允许访问的角色列表（若未提供或空数组，表示登录即可见）
  sort?: number;             // 排序权重
  hideInMenu?: boolean;      // 是否在侧边栏菜单中隐藏（但仍可被路由匹配，适用于详情页等）
}
```

#### 1.6.4 菜单配置响应格式
```typescript
export interface MenuResponse {
  items: MenuItem[];
  home?: string;             // 首页路径，默认为 '/dashboard'
}
```

**使用规则**：
- 菜单渲染时，优先使用 `titleKey` 通过 i18n 获取标题；若 `titleKey` 不存在，则回退使用 `title`；若两者均无，可显示 `id` 作为后备。
- 权限控制基于角色：若菜单项定义了 `roles` 数组，则用户必须拥有至少一个匹配的角色才能访问；若未定义 `roles` 或数组为空，视为登录用户均可访问。
- `hideInMenu` 用于详情页等不应出现在侧边栏导航中但仍需被路由匹配的页面。

### 1.7 packages/core 的职责与边界

`packages/core` 应作为与具体业务无关的可复用框架核心，提供以下功能：

- **基础布局组件**：如 `MainLayout`、`Sidebar`、`TopBar`、`TabsBar`，通过 props 或 context 接收菜单数据、用户信息，**不直接依赖应用层的 store**。
- **权限守卫 Hooks**：如 `usePermissionGuard`，接受用户角色和菜单项所需角色，返回是否有权限；不关心具体角色来源。
- **多标签页管理**：提供标签页的抽象管理（激活、关闭、刷新），基于 `MenuItem` 类型。
- **插件加载器基础设施**：提供 SystemJS 共享库注册的通用方法（如 `registerSharedModules`），但具体的模块映射由应用层传入。
- **主题切换抽象**：可能提供 `ThemeProvider` 的包装，但具体主题定义由应用层提供。

**边界明确**：
- `core` 包**不引用** `apps/main` 或任何业务模块中的任何内容。
- 所有业务数据（菜单、用户、主题配置）通过依赖注入（props 或 context）传入。
- `core` 包依赖于 `packages/shared` 中定义的标准类型。

---

## 2. 业务功能需求（必须实现）

整体是一个「运营控制台 + AI 工作台 + 流程编排 + 插件机制」型后台系统。需要实现以下模块，每个模块都要有**清晰菜单入口**和**完整页面**，而不是只显示"敬请期待"。

### 2.1 仪表盘（Dashboard）

目标：运营同学进入系统看到的第一个页面，用来快速了解当前系统运行情况。

需求：

1. **关键指标卡片**
    - 至少 4 个指标卡，例如：
        - 今日总请求数
        - 最近 24 小时成功率
        - 平均响应时间
        - 活跃会话数 / 在线用户数
    - 每个卡片包含：标题、主数值、对比信息（例如与昨天相比的变化趋势，显示百分比和趋势方向箭头）。
    - 卡片需要支持**玻璃拟态效果**，带有半透明背景、模糊背景、悬浮感。
    - 指标变化需要用颜色区分：上涨用绿色，下跌用红色。

2. **趋势图**
    - 至少 1 个随时间变化的图表（折线图或面积图），展示最近一段时间的关键指标趋势：
        - 例如最近 7 天的请求量 / 成功量 / 错误量。
    - 图表配色需要与主题风格协调。

3. **复杂图表（必须）**
    - 除基础趋势图外，至少再提供 **3 类复杂图表**：
        - **组合图**（柱状 + 折线双轴）：展示请求量与成功率同屏对比
        - **堆叠图**（Stacked Bar/Area）: 展示不同渠道或模块的贡献占比
        - **漏斗图/雷达图/热力图**: 任选其一用于业务深度分析
    - 图表必须具备交互性：tooltip、legend、数据高亮、筛选。
    - 图表需要支持**主题色跟随**，配色与主题风格协调。

4. **分类统计图**
    - 至少 1 个分类维度的图表（柱状图 / 饼图等），展示：
        - 不同业务模块、不同错误类型或不同接口的占比。
    - 饼图需要支持交互式 tooltip 和图例。

5. **最近事件列表**
    - 一个表格/列表，展示最近若干条重要事件，例如：
        - 最近错误
        - 最近发布 / 配置变更
        - 最近插件安装 / 启用
    - 每条事件包含：时间、类型、描述、影响范围、状态标签。
    - 列表需要支持**玻璃拟态效果**。
    - 不同类型事件用不同颜色标签区分（错误-红色、成功-绿色、警告-黄色）。

6. **时间范围过滤**
    - 提供时间范围选择器（例如：今天 / 最近 7 天 / 最近 30 天 / 自定义范围）。
    - 切换时间范围时，指标卡片和图表展示的数据要跟随变化。
    - 时间范围选择器需要支持快捷选项和日期范围选择器。

7. **插件挂载区域**
    - 仪表盘页面需要预留一个区域，用于动态加载已启用插件的组件内容（例如插件提供的小组件、小卡片等）。
    - 当插件禁用时，该区域显示占位内容或隐藏。
    - 挂载区域需要与整体布局协调，不能显得突兀。

> 注意：不要求实现真实后端统计逻辑，但前端要有完整的数据结构和交互流程。

---

### 2.2 AI 工作台（AI Workbench）

目标：提供一个 AI 辅助工作区域，用于对接各种"助手"，帮助用户分析、总结、设计流程等。

需求：

1. **会话列表**
    - 左侧展示会话列表（宽度可调整），每个会话包含：
        - 会话标题（可编辑）
        - 最近更新时间
        - 助手类型图标
    - 用户可以点击会话切换当前对话。
    - 支持新建会话、删除会话、重命名会话操作。
    - 会话列表需要支持搜索/筛选。
    - 当前选中会话需要高亮显示。
    - 会话列表需要支持**玻璃拟态效果**。

2. **对话区域**
    - 右侧为当前会话的聊天界面：
        - 垂直消息列表，区分「用户」与「AI」消息，不同角色用不同样式区分。
        - AI 消息支持 Markdown 渲染（代码块、列表、粗体等）。
        - 消息支持时间戳显示。
        - 支持滚动加载历史消息。
    - 底部输入区域：
        - 多行文本输入框（支持 Shift+Enter 换行）
        - 发送按钮
        - 停止生成按钮（流式输出时显示）
    - AI 回复需要支持流式输出效果（打字机效果）。Mock 数据中可使用定时器分块输出文本来模拟流式。

3. **助手类型切换**
    - 顶部提供助手类型选择器，至少 3 种预置助手类型：
        - 通用助手：回答一般性问题
        - 数据分析助手：专注于数据分析和报表
        - 流程设计助手: 帮助设计和优化业务流程
    - 每种助手类型在 UI 上要有清晰的视觉区分（图标、颜色、描述）。
    - 切换助手类型时可以选择：
        - 在当前会话中切换（保留历史消息）
        - 开启一个新会话

4. **上下文附加开关**
    - 提供一个显式开关，用于控制「是否附带当前页面上下文」。
    - 开关打开时，界面上要有清晰标识，提示用户当前提问会包含哪些上下文信息。
    - 上下文信息预览（显示将要发送的上下文摘要，可 Mock）。

5. **对话操作**
    - 支持复制消息内容。
    - 支持重新生成回复。
    - 支持清空对话历史。

> 不要求实现真实 AI 模型调用，但要预留接口位置，数据结构上要能清晰表示"会话""消息""助手配置"。

---

### 2.3 流程编排（Flow / Pipeline Editor）

目标：让用户以可视化方式设计和管理业务流程。

需求：

1. **流程列表页面**
    - 展示所有流程的列表，每条记录包含：
        - 流程名称
        - 流程描述
        - 状态（例如：启用 / 禁用 / 草稿）
        - 创建时间
        - 最近更新时间
    - 支持操作：
        - 新建流程（进入编辑画布）
        - 编辑流程（进入编辑画布）
        - 复制流程
        - 删除流程（需确认）
        - 启用/禁用流程
    - 支持搜索和筛选（按状态、名称）。
    - 列表支持分页。

2. **流程编辑页面（画布）**
    - 提供一个可缩放 / 可拖拽的流程画布：
        - 支持鼠标滚轮缩放
        - 支持拖拽移动画布
        - 支持触摸板操作
    - 用户可以：
        - 在画布中添加节点
        - 拖动节点位置
        - 在节点之间连线
    - 节点添加方式必须支持「拖拽投放」：
        - 左侧节点面板可拖拽节点模板到画布落点创建节点
        - 保留快捷操作（按钮添加）作为辅助
    - 画布必须提供缩略图（MiniMap）并可快速定位。
    - 画布需要支持网格对齐功能（可开关）。
    - 画布背景需要与主题协调。

3. **节点类型**
    - 至少支持 **6 种节点类型**：
        - 开始节点（Start）： 流程入口，只能有一个
        - 结束节点（End）： 流程出口，可以有多个
        - 任务节点（Task）： 执行具体任务
        - 条件分支节点（Condition）： 根据条件选择分支
        - 并行节点（Parallel）： 同时执行多个分支
        - 循环节点（Loop）： 重复执行直到条件满足
    - 每种节点类型需要有独特的视觉样式（颜色、图标、形状）。
    - 节点需要支持主题色。

4. **节点属性编辑**
    - 点击节点时，弹出编辑框（Modal/Drawer/侧边面板），允许编辑：
        - 节点名称
        - 节点描述
        - 节点类型特定属性（例如：任务节点的执行方式、超时设置；条件节点的条件表达式等）
    - 不同节点类型有不同的属性表单。

5. **连线编辑**
    - 连线至少支持：
        - 连线标签/条件表达式
        - 连线样式（如颜色、线型 - 实线/虚线/点线）
    - 连线需要有箭头指示方向。
    - 条件分支节点的连线需要显示条件标签。

6. **浮动工具栏（Floating Toolbar）**
    - Hover 到节点或连线时，必须显示浮动工具栏，至少包含：
        - 删除
        - 快速复制
        - 快速跳转编辑
    - 工具栏需要跟随主题风格。

7. **编辑器效率能力（必须）**
    - 支持 Undo / Redo（按钮 + 快捷键 Ctrl/Cmd+Z、Ctrl/Cmd+Y）。
    - 支持 Copy / Paste（按钮或快捷键 Ctrl/Cmd+C、Ctrl/Cmd+V），至少支持节点复制。
    - 支持 Delete 键删除当前选中节点/连线。
    - 允许导出当前流程 JSON。
    - 可一键恢复到最近保存版本。

> 不要求实际执行流程，仅需保证"流程结构"可以完整表达、保存和恢复。

---

### 2.4 主子表管理（Master-Detail CRUD）【增强版】

目标：提供一个完整的主子表数据管理页面，展示复杂的数据编辑场景，并演示多种编辑模式。

需求：

#### 2.4.1 主表列表页面
- 展示主表数据列表（例如订单列表），支持：
    - 多选（复选框列）
    - 排序（点击表头）
    - 分页（页码选择器、每页条数选择）
    - 刷新
- 列表需要支持主题色。
- **点击行或点击“查看明细”按钮，应在多标签页导航中打开一个新的标签页，显示该主表记录的详情页**。
- 操作列：编辑、删除按钮（编辑也可触发详情页打开）。
- 行悬停高亮效果。
- 列表需要支持**玻璃拟态效果**。

#### 2.4.2 主表详情页面（多子表、多编辑模式）
详情页在一个新的标签页中打开，URL 应包含记录 ID（如 `/master-detail/orders/:id`）。该页面应作为**隐藏菜单项**（`hideInMenu: true`）存在于菜单配置中，以便路由匹配。

页面结构：
- **顶部区域**：显示主表核心信息（只读展示，如订单号、客户名称、订单状态、创建时间等），用卡片包裹。
- **子表区域**：包含至少 **3 个子表**，每个子表采用不同的编辑方式，全面展示各种编辑场景：

---

##### **子表 1：就地编辑表格（Inline Editing）**
- **用途**：订单明细（商品、数量、单价、小计）
- **编辑方式**：在表格内直接编辑（类似 Excel）
    - 双击单元格进入编辑状态
    - 支持新增行（底部或顶部有“添加”按钮）
    - 支持删除行（每行有删除按钮）
    - 支持行内验证（如数量必须为正整数，单价必须为正数）
    - 支持实时计算小计（数量 × 单价）
    - 支持撤销修改（每行或整体）
    - 有“已修改”标记（如行背景色变化或侧边标记）
- **提交方式**：与主表和其他子表一起通过“保存全部”按钮提交。

##### **子表 2：弹框编辑（Modal Editing）**
- **用途**：收货地址（收货人、电话、省份、城市、详细地址）
- **编辑方式**：点击每行的“编辑”按钮，弹出模态框进行编辑
    - 模态框内包含完整表单
    - 支持新增地址（点击“新增”弹出空表单）
    - 支持删除地址（需二次确认）
    - 支持设置默认地址（单选）
- **特点**：编辑时聚焦于单条记录，适合字段较多或需要复杂布局的场景。

##### **子表 3：抽屉编辑（Drawer Editing）**
- **用途**：物流信息（物流公司、运单号、发货状态、预计送达时间、备注）
- **编辑方式**：点击每行的“编辑”按钮，从右侧滑出抽屉进行编辑
    - 抽屉内包含表单
    - 支持新增、删除
    - 支持查看物流轨迹（可模拟静态数据）
- **特点**：保留主表上下文的视觉联系，适合需要较大编辑空间但又不希望完全离开页面的场景。

---

#### 2.4.3 子表公共要求
- 每个子表有独立的卡片/区域，带标题和操作按钮（新增、刷新等）。
- 子表表格需要支持主题色。
- 每个子表需显示当前未保存的修改标记（例如卡片标题旁显示“已修改”红点）。
- 支持行内验证，错误时高亮显示并给出提示。
- 支持撤销单行修改或整个子表的修改。

#### 2.4.4 保存与验证
- 提供**“保存全部”按钮**，一次性提交主表（如有修改）和所有子表的修改。
- 保存前进行整体验证，有错误时定位到对应子表和行。
- 保存成功/失败后显示 toast 提示。
- 支持取消/放弃所有编辑。
- 离开页面时如果有未保存修改，弹出确认提示。

#### 2.4.5 批量操作
- 主表列表支持批量删除选中记录。
- 支持批量导出（可模拟导出功能，如下载 JSON）。
- 批量操作需要二次确认。

#### 2.4.6 查询条件区域
- 详情页顶部可放置查询条件（可选），用于筛选子表数据（如按商品名称筛选子表1）。
- 主表列表页的查询条件区域要求：
    - 默认单行，包含主表关键字段查询、状态筛选、日期范围。
    - “更多”按钮展开更多条件（至少 3-5 个）。
    - 支持清空/重置。
    - 查询按钮触发搜索，支持回车快捷键。
    - 查询区域支持**玻璃拟态效果**。

---

### 2.5 插件管理（Plugins）

目标：通过插件系统扩展平台能力，并有一个完整 demo 插件。

需求：

1. **插件管理页面**
    - 列出所有可用插件，至少包含一个 demo 插件。
    - 每个插件项包含：
        - 插件图标
        - 插件名称
        - 简短描述
        - 当前状态（启用 / 禁用）
        - 版本号
        - 作者/来源
    - 支持操作：
        - 启用/禁用插件（开关）
        - 查看插件详情
        - 配置插件（如有配置项）
    - 插件列表需要支持**玻璃拟态效果**。

    - 启用/禁用状态切换时显示确认提示。
    - 状态需要被持久化，刷新页面后可以恢复。

2. **插件示例页面**
    - 为 demo 插件提供一个独立页面（在主应用中的某条菜单路由对应）。
    - 当插件启用时，在该页面展示插件提供的功能示例，例如：
        - 自定义统计报表（使用主应用共享的图表库）
        - 一组仪表卡片（使用主应用共享的 UI 组件）
        - 与主应用交互（访问主应用状态、触发通知等）
    - 当插件禁用时，该页面应展示清晰的提示：插件未启用，请联系管理员启用。
    - 页面需要支持**玻璃拟态效果**。

3. **插件挂载区域**
    - 主应用中（例如 Dashboard 或某个"工作台"页面）要预留区块，用于动态加载插件渲染的内容（例如插件提供的小组件、小卡片等）。
    - 挂载区域需要与整体布局协调。
    - 当没有启用的插件时,该区域显示占位内容或隐藏。

4. **插件通信能力**
    - 插件可以访问主应用的状态管理（Zustand store）。
    - 插件可以访问主应用的国际化（i18n）。
    - 插件可以访问主应用的主题系统（获取当前主题、响应主题变化）。
    - 插件可以触发主应用的通知（toast）。
    - 插件可以获取当前用户信息。

5. **plugin-demo 子项目**
    - 在 apps/plugin-demo 目录下创建一个独立的插件项目。
    - 插件必须能够独立开发、构建、部署。
    - 插件项目需要有自己的 package.json 和 vite 配置。
    - 插件必须使用 SystemJS 格式构建。
    - 插件需要声明所有共享库为 peerDependencies。
    - 插件必须默认导出一个 React 组件作为入口。
    - 插件需要实现至少 3 个功能点来展示与主应用的集成能力。

> **插件系统必须使用 SystemJS 进行动态加载，主应用与插件共享所有公共库（见 1.3 节共享库配置）。**

---

### 2.6 动态菜单系统（Dynamic Menu）

目标: 支持从后端加载菜单配置，实现灵活的菜单管理，且路由根据菜单动态生成。

需求：

1. **菜单数据加载**
    - 应用启动时从 API 加载菜单配置。
    - 每个菜单项必须符合 `packages/shared` 中定义的 `MenuItem` 结构（见 1.6.3 节）。
    - 支持多级嵌套菜单。
    - 加载失败时显示错误提示并提供重试。
    - 菜单加载时显示骨架屏或 loading 状态。

2. **动态页面渲染**
    - **路由系统必须根据菜单配置动态生成**。除了登录页、404、403 等少数公开路由外，所有后台页面由一个通配路由（如 `/*`）配合动态页面组件统一处理。
    - 动态页面组件根据当前路径匹配菜单项：
        - 如果匹配成功，根据 `pageType` 渲染：
            - `builtin`：从内置组件映射表（`builtinComponentMap`）中取出对应组件渲染。
            - `plugin`：使用 SystemJS 动态加载插件模块，渲染插件导出的组件。
        - 如果匹配失败，跳转到 404 页面。
    - 内置组件映射表需提前定义，将 `componentId` 映射到实际 React 组件（可使用 `React.lazy` 优化）。
    - 页面切换时保持状态（滚动位置、表单数据等），可通过路由组件自身控制。
    - 页面加载失败时显示错误页面。
    - **对于 `hideInMenu: true` 的菜单项，路由系统仍应正常匹配，只是不在侧边栏显示**。

3. **权限处理**
    - 菜单项可包含 `roles` 数组（见 1.6.3 节）。
    - 动态页面组件需检查当前用户角色是否与菜单项所需角色有交集：
        - 若无权限且已登录，应停留在应用内，显示 403 无权限页面或重定向到工作区首页，**不能跳转到登录页**。
    - 可在菜单加载后过滤掉无权限的菜单项，或在渲染时判断。

4. **菜单状态持久化**
    - 菜单展开/折叠状态需要持久化。
    - 刷新页面后恢复之前的菜单状态。
    - 支持记住最后访问的菜单项。

    - 菜单图标支持 Lucide React 图标库。

---

### 2.7 系统设置（Settings）

目标: 允许用户配置主题、语言和布局偏好。

需求：

1. **主题设置**
    - 至少 **5 套精心设计的主题**，每套主题包含：
        - 独特的名称和设计风格描述
        - 完整的颜色体系（见 1.5 节）
        - 浅色和深色两套配色方案
    - 主题列表：
        - 海洋深处（Ocean Depths）
        - 科技创新（Tech Innovation）
        - 森林树冠（Forest Canopy）
        - 日落大道（Sunset Boulevard）
        - 现代简约（Modern Minimalist）
    - 支持 **3 种显示模式**:
        - 浅色模式（light）
        - 深色模式（dark）
        - 跟随系统（system）
    - 支持 **2 种视觉风格**:
        - 默认风格（default）
        - 玻璃拟态（glassmorphism）
    - 用户应能在设置页面中切换主题、显示模式、视觉风格。
    - 切换后，所有页面视觉风格立即变化。
    - 主题选择要被持久化（例如 localStorage）。
    - 主题切换时需要平滑过渡动画。

2. **语言设置**
    - 至少支持中文（简体）和英文两种语言。
    - 在设置页面中提供语言选择控件。
    - 语言切换后，菜单、页面标题、按钮文案、提示信息等都要更新。
    - 语言选择需要被持久化。
    - 首次打开采用浏览器语言或默认配置，之后以用户设置为准。

3. **布局设置**
    - 提供侧边栏折叠/展开设置（可配置默认状态）。
    - 提供"是否启用多标签页导航"的开关:
        - 启用时使用顶部多标签页导航。
        - 关闭时退化为单页面路由导航。
    - 布局设置需要被持久化。

4. **设置页面布局**
    - 设置页面需要分组展示（主题、语言、布局等）。
    - 每个设置组需要卡片包裹。
    - 设置页面需要支持**玻璃拟态效果**。

---

### 2.8 导航与多标签页（Navigation & Tabs）

目标: 模仿较完整的商业后台导航体验。

需求：

1. **左侧多级菜单**
    - 一级菜单为模块级入口：仪表盘、AI 工作台、流程、主子表、插件、系统设置等。
    - 二级菜单为模块内页面，例如:
        - 流程列表 / 流程编辑
        - 插件管理 / 插件示例
        - 各种设置子页面
    - 菜单支持折叠/展开动画。
    - 菜单需要支持**玻璃拟态效果**（启用时）。
    - 菜单图标需要与主题协调。
    - 对于 `hideInMenu: true` 的菜单项，不在侧边栏渲染。

2. **顶部标签页导航**
    - 每次点击菜单路由时，在顶部打开标签页:
        - 相同页面（相同路径）不重复打开多个标签。
    - 标签页支持:
        - 切换激活
        - 关闭当前
        - 关闭其他
        - 关闭全部
        - 刷新当前
    - 标签页需要显示页面图标和标题（从菜单数据中获取，优先使用 `titleKey` 国际化，若无则用 `title`）。
    - 标签页过多时支持滚动或下拉选择。
    - 标签页需要支持主题色。

3. **顶栏信息区域**
    - 显示当前用户信息（头像、名称）。
    - 提供主题快速切换入口（下拉菜单）。
    - **提供语言快速切换入口（下拉菜单，必须）**:
        - 显示当前选中语言的名称（如"简体中文"/"English"）
        - 下拉列表显示所有可选语言（从 `apps/main/src/config/i18n/languages.ts` 读取）
        - 可选显示国旗图标
        - 切换后立即生效，刷新页面后保持选择
    - 提供通知入口（图标 + 未读数量）。
    - 提供用户菜单（个人设置、退出登录等）。
    - 顶栏需要支持**玻璃拟态效果**。

4. **侧边栏折叠**
    - 折叠时只显示图标。
    - 展开时显示图标和文字。
    - 折叠/展开有动画过渡。
    - 折叠状态下悬停显示 tooltip。

---

### 2.9 登录与会话（Auth）

目标: 用户需要登录后才能访问后台页面。

需求：

1. **登录页**
    - 页面包含:
        - 系统 Logo 和标题
        - 用户名/邮箱输入框
        - 密码输入框（可切换显示/隐藏）
        - 记住我复选框
        - 登录按钮
        - 忘记密码链接（可选）
        - **语言切换下拉选择框**（必须）: 位于页面右上角或登录表单附近，允许用户切换界面语言
    - 文案需要支持中英文切换。
    - 登录页需要精美的背景设计（渐变、图案等）。
    - 登录表单需要**玻璃拟态效果**。
    - 语言切换下拉框需要显示当前选中语言的名称（如"简体中文"/"English"），可选显示国旗图标。

2. **登录逻辑**
    - 登录可以使用简化规则（例如任意非空用户名+密码即成功），但必须:
        - 生成一个「当前用户」对象存储到全局状态，用户对象需符合 `User` 结构（见 1.6.1 节）。
        - 后续页面可以显示该用户的名称和头像。
    - 登录失败时显示错误提示。
    - 登录成功后跳转到之前的页面或首页。
    - 未登录时访问任意后台路由，应自动跳转到登录页。

3. **退出登录**
    - 退出时清除用户状态。
    - 跳转到登录页。
    - 需要二次确认。

---

### 2.10 国际化（i18n）

目标: 全局多语言支持。

需求:

1. **语言配置集中管理**
    - 所有可选语言必须在 `apps/main/src/config/i18n/languages.ts` 文件中集中配置。
    - 配置文件格式示例:
      ```typescript
      // apps/main/src/config/i18n/languages.ts
      export interface LanguageConfig {
        code: string;        // 语言代码，如 'zh-CN', 'en'
        name: string;        // 显示名称，如 '简体中文', 'English'
        nativeName: string;  // 原生名称，如 '简体中文', 'English'
        flag?: string;       // 可选的国旗图标标识
      }
      
      export const SUPPORTED_LANGUAGES: LanguageConfig[] = [
        { code: 'zh-CN', name: '简体中文', nativeName: '简体中文', flag: 'CN' },
        { code: 'en', name: 'English', nativeName: 'English', flag: 'US' },
        // 开发阶段可在此添加更多语言
      ];
      
      export const DEFAULT_LANGUAGE = 'zh-CN';
      ```
    - 语言选择组件必须从该配置文件读取可选语言列表，确保新增语言时只需修改一处。

2. **单文件翻译资源**
    - 每种语言的翻译文案必须放在**单个 JSON 文件**中，不拆分为多个命名空间文件。
    - 文件路径: `apps/main/public/locales/{langCode}/translation.json`
    - 示例结构:
      ```
      apps/main/public/
        locales/
          zh-CN/
            translation.json   # 中文翻译（单文件，使用嵌套结构）
          en/
            translation.json   # 英文翻译（单文件，使用嵌套结构）
      ```
    - JSON 内部可以使用嵌套结构组织文案，例如:
      ```json
      {
        "common": {
          "save": "保存",
          "cancel": "取消",
          "delete": "删除"
        },
        "dashboard": {
          "title": "仪表盘",
          "todayRequests": "今日总请求数"
        },
        "auth": {
          "login": "登录",
          "username": "用户名",
          "password": "密码"
        }
      }
      ```
    - 使用 i18next 的点分隔符（如 `t('common.save')`）访问嵌套键，无需显式指定命名空间。

3. 所有可见文案必须通过 i18n 资源加载，而不是硬编码字符串。
4. 首次打开采用浏览器语言或默认配置，之后以用户设置为准。
5. 语言切换需要有平滑过渡效果。
6. 日期、数字、货币格式需要根据语言自动调整。
7. **语言切换入口**:
    - **登录页面**: 必须提供语言切换下拉选择框，允许用户在登录前切换界面语言。
    - **登录后页面**: 顶部导航栏必须提供语言切换下拉选择框（见 2.8 节顶栏信息区域）。
    - 语言切换后立即生效，无需刷新页面。
    - 语言选择需要被持久化（localStorage），刷新后保持用户选择。

---

### 2.11 通知与错误处理（Notifications & Error Handling）

目标: 让用户清楚地知道操作结果与系统状态。

需求:

1. **通知系统**
    - 所有重要操作（保存、删除、启用插件、切换主题等）完成后，要有成功/失败提示。
    - 接口调用失败、数据加载异常等要通过明显的通知告知用户。
    - 通知需要支持不同类型（success、error、warning、info）。
    - 通知需要有自动关闭时间，也支持手动关闭。
    - 通知位置一致（建议右上角或右下角）。
    - 通知需要支持主题色。

2. **错误页**
    - 提供一个全局错误页面，用于路由层面捕获到的异常:
        - 包含错误代码（404、500 等）
        - 包含错误信息展示区域
        - 包含返回首页的按钮
        - 包含返回上一页的按钮（如果有历史）
    - 支持 404、500 等常见错误页面。
    - 错误页面需要有友好的视觉设计。

3. **确认对话框**
    - 危险操作（删除、退出等）前需要二次确认。
    - 确认对话框需要支持中英文。
    - 确认对话框需要支持主题色。

---

### 2.12 Mock 数据与开发体验（Mock & DX）

目标: 在没有真实后端的情况下提供完整体验。

需求:

1. 在开发环境中（dev 模式）启用基于 Mock Service Worker 的接口 mock。
2. 至少 mock 以下数据:
    - 仪表盘指标和图表数据（支持时间范围过滤）
    - 流程列表与流程详情
    - 插件列表与状态
    - AI 助手的回复文本（支持流式输出）
    - 菜单配置数据（符合 `MenuItem` 结构）
    - 用户信息（符合 `User` 结构）
    - 主子表数据（主表 + 多个子表），且子表数据足够丰富以展示多种编辑模式
3. 通过环境变量（如 `VITE_ENABLE_MOCK`）控制是否启用 mock，生产构建默认不启用。
4. Mock 数据需要足够丰富，支持各种场景（空数据、错误、大量数据等）。

---

## 3. 输出要求（对代码生成模型）

> 以下是代码生成模型需要满足的输出要求，不是功能需求。

1. **必须输出完整的目录结构与关键文件内容**，可以用树结构 + 多个 code block 表达。
2. 代码要能在 Node 18+ 环境下, 通过以下命令成功执行:

```bash
pnpm install
pnpm build
pnpm dev
```

3. 不要求实现真实后端或真实 AI 能力, 但**前端所有页面与交互都必须能正常运行**, mock 数据要足以支撑全流程体验。
4. 所有业务功能都要能在 UI 中被访问到, 不能只在代码中定义未挂到路由/菜单。
5. 代码质量要求:
    - TypeScript 严格模式，无 any 类型
    - 无 ESLint 错误
    - 组件需要有良好的类型定义
    - 需要有适当的错误处理
    - 需要有适当的 loading 状态处理

> 以上即为完整需求与提示词。  
> 生成代码时，请严格以本文件为唯一真相来源，不能假设有其他项目或源码可用。