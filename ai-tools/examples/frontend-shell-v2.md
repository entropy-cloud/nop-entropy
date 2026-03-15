# pnpm Monorepo 后台系统框架生成提示词（基于原型页面）

> 使用方式：将本文件作为"系统提示词"或"完整需求文档"提供给代码生成模型，要求其基于原型页面从零实现一个可运行的 monorepo 前端项目框架。假设模型**看不到任何现有源码**。

---

## 0. 角色与目标

你现在是一个资深前端架构师和产品工程师，目标是：

- 从零搭建一个**基于 pnpm monorepo 管理的前端项目框架**；
- **根据原型页面确定主题配色、字体和整体视觉风格**；
- 实现一个完整的**后台管理系统框架**，包含布局、导航、多标签页、核心组件等基础设施；
- 先生成整体框架，菜单可以是全面的，但内部模块可以用占位组件实现；
- 重点实现框架结构、布局系统、导航系统、多标签页控制等核心能力。

执行本任务时：

- **不允许依赖任何现有源码**；只能依据本文件的描述；
- 可以自由设计内部实现，但必须满足业务需求和技术约束。

---

## 1. 技术与架构约束

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
│  ├─ ui/                        # @nop-chaos/ui - 通用 UI 组件库（shadcn CLI 安装，非手写）
│  │  ├─ src/
│  │  ├─ package.json            # name: "@nop-chaos/ui"
│  │  └─ tsconfig.json
│  ├─ core/                      # @nop-chaos/core - 后台框架核心（布局、多标签页、权限守卫、插件加载基础设施等，与业务无关）
│  │  ├─ src/
│  │  ├─ package.json            # name: "@nop-chaos/core"
│  │  └─ tsconfig.json
│  └─ shared/                    # @nop-chaos/shared - 通用类型与工具（如菜单项、用户等标准类型定义）
│     ├─ src/
│     ├─ package.json            # name: "@nop-chaos/shared"
│     └─ tsconfig.json
└─ examples/                     # 可选，用于存放其他教学示例（可保留为空）
```

3. 所有 app 和 package 的 TypeScript 配置通过 `tsconfig.base.json` 统一，开启严格模式。
4. 包间依赖使用 `workspace:*` 协议。

### 1.2 命名规范与目录约定

#### 1.2.1 文件命名规范（遵循 React 社区主流规范）

| 文件类型 | 命名规范 | 示例 |
|---------|---------|------|
| **React 组件** | PascalCase | `Dashboard.tsx`, `UserProfile.tsx`, `MainLayout.tsx` |
| **Hooks** | camelCase，以 `use` 开头 | `useAuth.ts`, `useTheme.ts`, `useTabManagement.ts` |
| **Store 文件（Zustand）** | camelCase，以 `Store` 结尾 | `authStore.ts`, `themeStore.ts`, `tabStore.ts` |
| **工具函数** | camelCase | `formatDate.ts`, `apiClient.ts`, `themeCss.ts` |
| **类型定义** | camelCase | `user.ts`, `menu.ts`, `theme.ts` |
| **常量/配置** | camelCase 或 UPPER_SNAKE_CASE | `apiConfig.ts`, `routes.ts`, `API_ENDPOINTS.ts` |
| **样式文件** | 与组件同名 | `Button.css`, `Sidebar.module.css` |
| **测试文件** | 组件名 + `.test` 或 `.spec` | `Button.test.tsx`, `utils.spec.ts` |

#### 1.2.2 目录命名规范

| 目录类型 | 命名规范 | 示例 |
|---------|---------|------|
| **功能模块目录** | kebab-case | `dashboard/`, `user-profile/`, `flow-editor/` |
| **组件目录** | PascalCase | `Button/`, `Sidebar/`, `TabsBar/` |
| **工具目录** | camelCase 或 kebab-case | `utils/`, `helpers/`, `api-clients/` |

#### 1.2.3 页面与目录映射规则

**每个菜单项应对应一个独立目录**，目录结构如下：

```txt
apps/main/src/
├─ pages/                      # 页面目录
│  ├─ dashboard/               # 仪表盘模块（对应菜单：/dashboard）
│  │  ├─ index.tsx             # 主页面组件
│  │  ├─ components/           # 模块专用组件
│  │  │  ├─ MetricCard.tsx
│  │  │  └─ TrendChart.tsx
│  │  ├─ hooks/                # 模块专用 hooks
│  │  │  └─ useDashboardData.ts
│  │  └─ types.ts              # 模块专用类型
│  │
│  ├─ ai-workbench/            # AI 工作台模块（对应菜单：/ai-workbench）
│  │  ├─ index.tsx
│  │  ├─ components/
│  │  │  ├─ ChatMessage.tsx
│  │  │  ├─ AssistantSelector.tsx
│  │  │  └─ ContextToggle.tsx
│  │  └─ hooks/
│  │     └─ useChat.ts
│  │
│  ├─ flow-editor/             # 流程编排模块（对应菜单：/flow-editor）
│  │  ├─ index.tsx             # 流程列表页
│  │  ├─ [id]/                 # 流程编辑页（动态路由，对应 /flow-editor/123）
│  │  │  └─ index.tsx
│  │  ├─ components/
│  │  │  ├─ FlowCanvas.tsx
│  │  │  ├─ NodePanel.tsx
│  │  │  └─ PropertyEditor.tsx
│  │  └─ hooks/
│  │     └─ useFlowEditor.ts
│  │
│  ├─ data-management/         # 数据管理模块
│  │  ├─ master-detail/        # 主子表示例（对应菜单：/data-management/master-detail）
│  │  │  ├─ index.tsx          # 主表列表页
│  │  │  ├─ [id]/              # 详情页（对应 /data-management/master-detail/123）
│  │  │  │  └─ index.tsx
│  │  │  ├─ components/
│  │  │  │  ├─ MasterTable.tsx
│  │  │  │  ├─ DetailTabs.tsx
│  │  │  │  └─ InlineEditTable.tsx
│  │  │  └─ hooks/
│  │  │     └─ useMasterDetail.ts
│  │
│  ├─ plugins/                 # 插件系统模块
│  │  ├─ management/           # 插件管理（对应菜单：/plugins/management）
│  │  │  └─ index.tsx
│  │  └─ demo/                 # 插件示例（对应菜单：/plugins/demo）
│  │     └─ index.tsx
│  │
│  ├─ settings/                # 系统设置模块
│  │  ├─ theme/                # 主题设置（对应菜单：/settings/theme）
│  │  │  └─ index.tsx
│  │  ├─ language/             # 语言设置（对应菜单：/settings/language）
│  │  │  └─ index.tsx
│  │  └─ layout/               # 布局设置（对应菜单：/settings/layout）
│  │     └─ index.tsx
│  │
│  ├─ auth/                    # 认证模块
│  │  └─ login/                # 登录页（对应菜单：/auth/login）
│  │     └─ index.tsx
│  │
│  └─ errors/                  # 错误页面
│     ├─ 404.tsx
│     ├─ 403.tsx
│     └─ 500.tsx
│
├─ components/                 # 全局共享组件
│  └─ layout/                  # 布局组件（UI 基础组件从 @nop-chaos/ui 导入，不在此目录）
│     ├─ MainLayout.tsx        # 主布局容器
│     ├─ Sidebar.tsx           # 左侧菜单栏
│     ├─ TopBar.tsx            # 顶部导航栏（包含用户信息、通知、语言切换等）
│     └─ TabsBar.tsx           # 多标签页导航栏（标签页管理）
│
├─ hooks/                      # 全局共享 hooks
│  ├─ useAuth.ts               # 认证相关 hook
│  ├─ useTheme.ts              # 主题相关 hook
│  └─ useTabManagement.ts      # 标签页管理 hook
│
├─ store/                      # 全局状态管理（Zustand）
│  ├─ authStore.ts
│  ├─ themeStore.ts
│  └─ tabStore.ts
│
├─ utils/                      # 工具函数
│  ├─ apiClient.ts
│  └─ themeCss.ts
│
├─ types/                      # 全局类型定义
│  ├─ user.ts
│  └─ menu.ts
│
└─ config/                     # 配置文件
   ├─ i18n/
   │  └─ languages.ts
   └─ routes/
      └─ menu-config.ts
```

#### 1.2.4 目录映射示例

菜单配置与目录的对应关系：

```typescript
// config/routes/menu-config.ts
export const menuConfig: MenuItem[] = [
  {
    id: 'dashboard',
    titleKey: 'menu.dashboard',
    path: '/dashboard',
    icon: 'LayoutDashboard',
    pageType: 'builtin',
    componentId: 'dashboard',  // 映射到 pages/dashboard/index.tsx
  },
  {
    id: 'ai-workbench',
    titleKey: 'menu.aiWorkbench',
    path: '/ai-workbench',
    icon: 'Bot',
    pageType: 'builtin',
    componentId: 'ai-workbench',  // 映射到 pages/ai-workbench/index.tsx
  },
  {
    id: 'flow-editor',
    titleKey: 'menu.flowEditor',
    path: '/flow-editor',
    icon: 'GitBranch',
    pageType: 'builtin',
    componentId: 'flow-editor',  // 映射到 pages/flow-editor/index.tsx
    children: [
      {
        id: 'flow-editor-list',
        titleKey: 'menu.flowEditorList',
        path: '/flow-editor',
        icon: 'List',
        pageType: 'builtin',
        componentId: 'flow-editor',  // 映射到 pages/flow-editor/index.tsx
      },
      {
        id: 'flow-editor-edit',
        titleKey: 'menu.flowEditorEdit',
        path: '/flow-editor/:id',  // 动态路由
        icon: 'Edit',
        pageType: 'builtin',
        componentId: 'flow-editor-edit',  // 映射到 pages/flow-editor/[id]/index.tsx
        hideInMenu: true,  // 不在菜单中显示
      },
    ],
  },
  {
    id: 'data-management',
    titleKey: 'menu.dataManagement',
    path: '/data-management',
    icon: 'Database',
    pageType: 'builtin',
    componentId: 'data-management',
    children: [
      {
        id: 'master-detail',
        titleKey: 'menu.masterDetail',
        path: '/data-management/master-detail',
        icon: 'Table',
        pageType: 'builtin',
        componentId: 'master-detail',  // 映射到 pages/data-management/master-detail/index.tsx
      },
      {
        id: 'master-detail-id',
        titleKey: 'menu.masterDetailDetail',
        path: '/data-management/master-detail/:id',
        pageType: 'builtin',
        componentId: 'master-detail-detail',  // 映射到 pages/data-management/master-detail/[id]/index.tsx
        hideInMenu: true,
      },
    ],
  },
];
```

#### 1.2.5 组件导出规范

每个模块目录下的 `index.tsx` 应作为默认导出：

```tsx
// pages/dashboard/index.tsx
// 方式1：直接在 index.tsx 中定义并导出
export function Dashboard() {
  const { t } = useTranslation();
  
  return (
    <div className="p-6">
      <h1>{t('dashboard.title')}</h1>
      {/* 页面内容 */}
    </div>
  );
}
export default Dashboard;

// 方式2：从子文件导入后导出
import { DashboardView } from './DashboardView';
export default DashboardView;
```

对于子组件，可以在模块的 `components/` 目录下单独定义：

```tsx
// pages/dashboard/components/MetricCard.tsx
export function MetricCard({ title, value, trend }: MetricCardProps) {
  // ...
}

// pages/dashboard/components/index.ts
export { MetricCard } from './MetricCard';
export { TrendChart } from './TrendChart';
```

**推荐做法**：
- 简单页面：直接在 `index.tsx` 中定义组件
- 复杂页面：在 `index.tsx` 中导入并导出主组件，将复杂逻辑拆分到子组件中

### 1.3 关键依赖版本（必须完整保留，不低于指定版本）

运行时依赖（要求不低于）：

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

### 1.3.1 shadcn/ui 组件安装规范

**重要：所有 UI 组件必须通过 shadcn CLI 安装到 `@nop-chaos/ui` 包，禁止手动编写。**

shadcn/ui 提供了 60+ 个高质量组件，这些组件应该通过 CLI 自动安装到 monorepo 的共享 UI 包中。

#### 包命名约定

```
packages/
├─ ui/         → @nop-chaos/ui      # UI 组件库
├─ core/       → @nop-chaos/core    # 框架核心
└─ shared/     → @nop-chaos/shared  # 共享类型
```

#### 初始化（在 packages/ui 目录执行）

```bash
cd packages/ui
pnpm dlx shadcn@latest init
```

初始化时会创建 `components.json` 配置文件，**关键配置如下**：

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/styles/globals.css",
    "baseColor": "neutral",
    "cssVariables": true
  },
  "aliases": {
    "components": "@nop-chaos/ui/components",
    "utils": "@nop-chaos/ui/lib/utils",
    "ui": "@nop-chaos/ui/components"
  }
}
```

#### 安装所有组件

```bash
# 在 packages/ui 目录下执行
cd packages/ui

# 一次性安装所有组件（推荐）
pnpm dlx shadcn@latest add --all

# 或按需安装常用组件
pnpm dlx shadcn@latest add button card dialog input select tabs sidebar accordion scroll-area separator toast sonner tooltip popover sheet table avatar badge skeleton
```

#### 安装后目录结构

```
packages/ui/
├─ src/
│  ├─ components/
│  │  └─ ui/                      # shadcn CLI 自动生成的组件
│  │     ├─ button.tsx
│  │     ├─ card.tsx
│  │     ├─ dialog.tsx
│  │     ├─ input.tsx
│  │     ├─ select.tsx
│  │     ├─ tabs.tsx
│  │     └─ ... (60+ 组件)
│  ├─ lib/
│  │  └─ utils.ts                 # shadcn 自动生成的工具函数 (cn 等)
│  └─ index.ts                    # 统一导出所有组件（必须维护）
├─ components.json                # shadcn 配置
├─ package.json                   # name: "@nop-chaos/ui"
└─ tsconfig.json
```

#### packages/ui/package.json 示例

```json
{
  "name": "@nop-chaos/ui",
  "version": "0.0.1",
  "type": "module",
  "exports": {
    ".": "./src/index.ts",
    "./lib/utils": "./src/lib/utils.ts"
  },
  "peerDependencies": {
    "react": "^19.2.0",
    "react-dom": "^19.2.0"
  },
  "dependencies": {
    "@radix-ui/react-dialog": "^1.1.0",
    "@radix-ui/react-dropdown-menu": "^2.1.0",
    "@radix-ui/react-select": "^2.1.0",
    "@radix-ui/react-tabs": "^1.1.0",
    "@radix-ui/react-tooltip": "^1.1.0",
    "@radix-ui/react-slot": "^1.1.0",
    "@radix-ui/react-scroll-area": "^1.2.0",
    "@radix-ui/react-separator": "^1.1.0",
    "@radix-ui/react-checkbox": "^1.1.0",
    "@radix-ui/react-label": "^2.1.0",
    "@radix-ui/react-collapsible": "^1.1.0",
    "class-variance-authority": "^0.7.1",
    "clsx": "^2.1.1",
    "lucide-react": "^0.577.0",
    "tailwind-merge": "^3.5.0"
  }
}
```

#### packages/ui/src/index.ts 示例（统一导出）

**重要**：每次通过 shadcn CLI 安装新组件后，需要在此文件中添加导出。

```typescript
// packages/ui/src/index.ts

// 表单输入组件
export { Button, buttonVariants } from "./components/ui/button"
export { Input } from "./components/ui/input"
export { Textarea } from "./components/ui/textarea"
export { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./components/ui/select"
export { Checkbox } from "./components/ui/checkbox"
export { Label } from "./components/ui/label"
export { Switch } from "./components/ui/switch"
export { Slider } from "./components/ui/slider"
export { Calendar } from "./components/ui/calendar"
export { Popover, PopoverContent, PopoverTrigger } from "./components/ui/popover"

// 布局组件
export { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "./components/ui/card"
export { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "./components/ui/accordion"
export { Collapsible, CollapsibleContent, CollapsibleTrigger } from "./components/ui/collapsible"
export { Tabs, TabsContent, TabsList, TabsTrigger } from "./components/ui/tabs"
export { ScrollArea, ScrollBar } from "./components/ui/scroll-area"
export { Separator } from "./components/ui/separator"
export { AspectRatio } from "./components/ui/aspect-ratio"
export { Sidebar, SidebarContent, SidebarFooter, SidebarHeader, SidebarProvider } from "./components/ui/sidebar"

// 反馈组件
export { Alert, AlertDescription, AlertTitle } from "./components/ui/alert"
export { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "./components/ui/dialog"
export { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle, SheetTrigger } from "./components/ui/sheet"
export { Drawer, DrawerContent, DrawerDescription, DrawerFooter, DrawerHeader, DrawerTitle, DrawerTrigger } from "./components/ui/drawer"
export { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "./components/ui/tooltip"
export { toast, Toaster } from "./components/ui/sonner"
export { Skeleton } from "./components/ui/skeleton"
export { Progress } from "./components/ui/progress"
export { Spinner } from "./components/ui/spinner"

// 导航组件
export { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbSeparator } from "./components/ui/breadcrumb"
export { NavigationMenu, NavigationMenuContent, NavigationMenuItem, NavigationMenuLink, NavigationMenuList, NavigationMenuTrigger } from "./components/ui/navigation-menu"
export { Menubar, MenubarContent, MenubarItem, MenubarMenu, MenubarSeparator, MenubarShortcut, MenubarTrigger } from "./components/ui/menubar"
export { Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious } from "./components/ui/pagination"
export { Command, CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator, CommandShortcut } from "./components/ui/command"

// 数据展示组件
export { Table, TableBody, TableCaption, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "./components/ui/table"
export { Avatar, AvatarFallback, AvatarImage } from "./components/ui/avatar"
export { Badge } from "./components/ui/badge"
export { Carousel, CarouselContent, CarouselItem, CarouselNext, CarouselPrevious } from "./components/ui/carousel"

// 覆盖层组件
export { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "./components/ui/alert-dialog"
export { HoverCard, HoverCardContent, HoverCardTrigger } from "./components/ui/hover-card"
export { ContextMenu, ContextMenuCheckboxItem, ContextMenuContent, ContextMenuItem, ContextMenuLabel, ContextMenuRadioGroup, ContextMenuItemSelect, ContextMenuSeparator, ContextMenuShortcut, ContextMenuTrigger } from "./components/ui/context-menu"
export { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuShortcut, DropdownMenuTrigger } from "./components/ui/dropdown-menu"

// 工具函数
export { cn } from "./lib/utils"
```

#### AI 生成规范

生成代码时，AI **必须遵守以下规则**：

1. **禁止生成已安装组件的源码**：Button、Dialog、Select 等组件已通过 CLI 安装到 `@nop-chaos/ui`

2. **集中导入（推荐）**：从 `@nop-chaos/ui` 统一导入
   ```tsx
   // ✅ 推荐：集中导入（简洁，符合社区习惯）
   import { Button, Card, CardContent, Dialog, DialogContent, Input } from "@nop-chaos/ui"
   
   // ❌ 不推荐：子路径导入（冗长，不必要）
   import { Button } from "@nop-chaos/ui/components/button"
   import { Card, CardContent } from "@nop-chaos/ui/components/card"
   ```

3. **安装新组件后更新导出**：使用 `shadcn add xxx` 安装新组件后，需要在 `packages/ui/src/index.ts` 中添加对应的导出

4. **其他包依赖 @nop-chaos/ui**：
   ```json
   // apps/main/package.json
   {
     "dependencies": {
       "@nop-chaos/ui": "workspace:*",
       "@nop-chaos/core": "workspace:*",
       "@nop-chaos/shared": "workspace:*"
     }
   }
   ```

#### 常用组件列表（CLI 安装后可用）

| 类别 | 组件 |
|------|------|
| **表单输入** | Button, Input, Textarea, Select, Checkbox, Radio Group, Switch, Slider, Calendar, Date Picker, Input OTP |
| **布局** | Card, Accordion, Collapsible, Tabs, Scroll Area, Separator, Aspect Ratio, Sidebar |
| **反馈** | Alert, Dialog, Sheet, Drawer, Popover, Tooltip, Toast, Sonner, Spinner, Skeleton, Progress |
| **导航** | Breadcrumb, Navigation Menu, Menubar, Pagination, Command, Combobox |
| **数据展示** | Table, Data Table, Avatar, Badge, Carousel, Chart, Empty |
| **覆盖层** | Alert Dialog, Hover Card, Context Menu, Dropdown Menu |

开发依赖（简要）:

- Vite 7（React + TS 插件）
- TypeScript 5.9 严格模式
- ESLint 9 + React 插件 + prettier config
- Vitest（单测）
- Playwright（端到端）
- Husky + lint-staged
- MSW（Mock Service Worker）

### 1.4 共享库配置（插件系统关键）

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

### 1.5 主题系统要求

**本节仅定义主题系统的基本要求，具体的配色、字体等视觉方案应根据原型页面确定。**

1. **主题支持要求**：
   - 系统必须支持主题切换功能
   - 支持浅色模式（light）、深色模式（dark）、跟随系统（system）三种显示模式
   - 主题配置需要被持久化（localStorage）
   - 主题切换需要平滑过渡动画

2. **主题架构**：
   - 使用 CSS 变量实现主题系统
   - 提供主题 Store（Zustand）管理主题状态
   - 提供 CSS 变量应用工具函数
   - Tailwind 配置需要映射 CSS 变量

3. **第三方组件主题集成**：
   - Recharts 图表需要响应主题变化
   - @xyflow/react 流程图需要跟随主题
   - Sonner 通知需要跟随主题

4. **主题数据结构**（根据原型页面填充具体值）：
   ```typescript
   export type DisplayMode = 'light' | 'dark' | 'system';
   
   export interface ThemeConfig {
     themeId: string;         // 当前主题 ID
     displayMode: DisplayMode; // 显示模式
   }
   ```

**具体主题配色方案应根据原型页面的视觉设计确定，包括：**
- 主色调（Primary）
- 次要色（Secondary）
- 背景色（Background）
- 前景色（Foreground）
- 边框色（Border）
- 状态色（Success, Warning, Error, Info）
- 强调色（Accent）

### 1.6 核心数据模型约定

为保障框架的可复用性，以下数据结构在整个产品系列中必须保持一致。所有类型定义应放在 `packages/shared/src/types/` 下。

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
  hideInMenu?: boolean;      // 是否在侧边栏菜单中隐藏（但仍可被路由匹配）
}
```

#### 1.6.4 菜单配置响应格式
```typescript
export interface MenuResponse {
  items: MenuItem[];
  home?: string;             // 首页路径，默认为 '/dashboard'
}
```

### 1.7 packages/core 的职责与边界

`packages/core` 应作为与具体业务无关的可复用框架核心，提供以下功能：

- **基础布局组件**：如 `MainLayout`、`Sidebar`、`TopBar`、`TabsBar`，通过 props 或 context 接收菜单数据、用户信息。
- **权限守卫 Hooks**：如 `usePermissionGuard`，接受用户角色和菜单项所需角色，返回是否有权限。
- **多标签页管理**：提供标签页的抽象管理（激活、关闭、刷新），基于 `MenuItem` 类型。
- **插件加载器基础设施**：提供 SystemJS 共享库注册的通用方法。
- **主题切换抽象**：提供 `ThemeProvider` 的包装。

**边界明确**：
- `core` 包**不引用** `apps/main` 或任何业务模块中的任何内容。
- 所有业务数据通过依赖注入（props 或 context）传入。
- `core` 包依赖于 `packages/shared` 中定义的标准类型。

---

## 2. 框架核心功能（必须实现）

### 2.1 整体布局系统

**目标**：实现一个可复用的后台布局框架。

**布局结构**：

```
┌─────────────────────────────────────────────────────┐
│ TopBar（顶部导航栏）                                  │
│ [Logo] [面包屑]           [通知] [语言] [主题] [用户] │
├────────┬────────────────────────────────────────────┤
│        │ TabsBar（标签页导航栏）                      │
│        │ [仪表盘×] [AI工作台×] [设置] [+]             │
│ Side   ├────────────────────────────────────────────┤
│ bar    │                                             │
│        │                                             │
│ [菜单] │ Content Area（内容区域）                     │
│        │                                             │
│        │                                             │
└────────┴────────────────────────────────────────────┘
```

**需求**：

1. **主布局组件（MainLayout）**：
   - 包含侧边栏、顶栏、内容区域、标签页栏
   - 支持侧边栏折叠/展开
   - 响应式设计（移动端适配）
   - 布局需要支持主题色

2. **侧边栏（Sidebar）**：
   - 多级菜单支持
   - 菜单折叠/展开动画
   - 折叠状态下显示 tooltip
   - 支持主题色

3. **顶栏（TopBar）- 顶部导航栏**：
   - **位置**：页面顶部，占据整行
   - **内容**：
     - 左侧：Logo、面包屑导航
     - 右侧：通知图标（带未读数）、语言切换、主题切换、用户菜单（头像+名称）
   - **高度**：通常 48-64px
   - **样式**：支持玻璃拟态效果（可选）

4. **标签页栏（TabsBar）- 多标签页导航**：
   - **位置**：TopBar 下方，内容区域上方
   - **功能**：
     - 显示当前打开的所有标签页
     - 每个标签显示图标和标题（来自菜单配置）
     - 支持关闭标签（×按钮）
     - 支持右键菜单（关闭其他、关闭全部、刷新）
   - **高度**：通常 32-40px
   - **样式**：支持主题色，激活标签高亮

5. **内容区域（Content Area）**：
   - 用于渲染当前激活标签页的内容
   - 支持路由切换过渡动画
   - 滚动条样式适配主题

### 2.2 多标签页导航系统

**目标**：实现类似浏览器标签页的导航体验。

**需求**：

1. **标签页栏（TabsBar）**：
   - 每次点击菜单路由时，在标签页栏打开标签页
   - 相同路径不重复打开标签
   - 标签页显示页面图标和标题（优先使用 i18n）

2. **标签页操作**：
   - 切换激活
   - 关闭当前
   - 关闭其他
   - 关闭全部
   - 刷新当前

3. **标签页状态管理**：
   - 标签页列表需要被持久化
   - 刷新页面后恢复标签页状态
   - 标签页过多时支持滚动

### 2.3 动态菜单系统

**目标**：支持从后端加载菜单配置，动态生成路由。

**需求**：

1. **菜单数据加载**：
   - 应用启动时从 API 加载菜单配置
   - 支持多级嵌套菜单
   - 加载失败时显示错误提示

2. **动态页面渲染**：
   - 路由系统根据菜单配置动态生成
   - 根据 `pageType` 渲染不同类型页面：
     - `builtin`：从内置组件映射表中取出组件
     - `plugin`：使用 SystemJS 动态加载插件

3. **权限处理**：
   - 菜单项可包含 `roles` 数组
   - 检查用户角色权限
   - 无权限时显示 403 页面

4. **菜单状态持久化**：
   - 菜单展开/折叠状态持久化
   - 支持记住最后访问的菜单项

### 2.4 登录与会话

**目标**：用户需要登录后才能访问后台页面。

**需求**：

1. **登录页**：
   - 系统 Logo 和标题
   - 用户名/密码输入框
   - 语言切换下拉选择框
   - 支持中英文

2. **登录逻辑**：
   - 生成用户对象存储到全局状态
   - 登录失败时显示错误提示
   - 登录成功后跳转到首页
   - 未登录时自动跳转到登录页

3. **退出登录**：
   - 清除用户状态
   - 跳转到登录页
   - 需要二次确认

### 2.5 国际化（i18n）

**目标**：全局多语言支持。

**需求**：

1. **语言配置集中管理**：
   - 所有可选语言在配置文件中集中配置
   - 语言选择组件从配置文件读取可选语言列表

2. **单文件翻译资源**：
   - 每种语言的翻译文案放在单个 JSON 文件中
   - 文件路径: `apps/main/public/locales/{langCode}/translation.json`

3. **语言切换入口**：
   - 登录页面提供语言切换
   - 登录后页面顶部导航栏提供语言切换
   - 语言切换后立即生效，无需刷新页面
   - 语言选择需要被持久化

### 2.6 通知系统

**目标**：让用户清楚地知道操作结果与系统状态。

**需求**：

1. 所有重要操作完成后，要有成功/失败提示
2. 通知支持不同类型（success、error、warning、info）
3. 通知需要支持主题色

### 2.7 错误处理

**需求**：

1. 提供全局错误页面（404、500 等）
2. 危险操作前需要二次确认
3. 确认对话框支持中英文

---

## 3. 模块占位与菜单结构

### 3.1 菜单结构（全面）

系统应包含以下菜单结构，但内部模块可以用占位组件实现：

```
├─ 仪表盘（Dashboard）
│  └─ 数据概览
├─ AI 工作台（AI Workbench）
│  └─ 对话界面
├─ 流程编排（Flow Editor）
│  ├─ 流程列表
│  └─ 流程编辑
├─ 数据管理（Data Management）
│  ├─ 主子表示例
│  └─ 详情页（hideInMenu: true）
├─ 插件系统（Plugins）
│  ├─ 插件管理
│  └─ 插件示例
├─ 系统设置（Settings）
│  ├─ 主题设置
│  ├─ 语言设置
│  └─ 布局设置
└─ 帮助与文档
   └─ 使用指南
```

### 3.2 占位组件要求

对于每个菜单项对应的页面，可以使用占位组件实现，但必须：

1. **基础结构完整**：
   - 页面有基本布局（标题、内容区域）
   - 支持 i18n（页面标题、描述等）
   - 支持主题色

2. **交互可用**：
   - 可以在标签页中打开
   - 可以刷新、关闭
   - 路由正常工作

3. **视觉一致**：
   - 与整体框架风格一致
   - 支持主题切换

**占位组件示例**：

```tsx
// 仪表盘占位组件
export function DashboardPlaceholder() {
  const { t } = useTranslation();
  
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">
        {t('dashboard.title')}
      </h1>
      <div className="text-muted-foreground">
        {t('dashboard.placeholder')}
      </div>
      {/* 可以放置一些基本的示例内容 */}
    </div>
  );
}
```

### 3.3 核心组件优先级

在实现框架时，应按以下优先级实现组件：

**P0（必须实现）**：
- MainLayout
- Sidebar（含菜单）
- TopBar
- TabsBar
- LoginPage
- ErrorPages（404、403、500）
- 主题系统基础设施
- i18n 基础设施
- 动态路由系统

**P1（重要但可简化）**：
- 用户菜单
- 通知组件
- 确认对话框
- **UI 组件库**：通过 `shadcn add --all` 安装到 `@nop-chaos/ui`，AI 禁止手写这些组件

**P2（占位即可）**：
- 仪表盘页面
- AI 工作台页面
- 流程编辑页面
- 数据管理页面
- 插件管理页面
- 设置页面

---

## 4. Mock 数据要求

在开发环境中，至少 mock 以下数据：

1. **菜单配置数据**（符合 `MenuItem` 结构）
2. **用户信息**（符合 `User` 结构）
3. **认证相关**（登录、登出）
4. **插件列表**（基础结构）

Mock 数据需要：
- 足够丰富以展示各种场景
- 支持通过环境变量控制是否启用
- 生产构建默认不启用

---

## 5. 输出要求

1. **必须输出完整的目录结构与关键文件内容**
2. 代码要能在 Node 18+ 环境下成功执行：
   ```bash
   pnpm install
   pnpm build
   pnpm dev
   ```
3. 所有框架功能（布局、导航、标签页、主题、i18n 等）必须能正常运行
4. 菜单结构完整，但内部模块可以是占位组件
5. 代码质量要求：
   - TypeScript 严格模式，无 any 类型
   - 无 ESLint 错误
   - 组件需要有良好的类型定义
   - 需要有适当的错误处理和 loading 状态

---

## 6. 实施指导

### 6.1 开发顺序建议

1. **第一步：基础架构**
   - 初始化 monorepo 结构
   - 配置 TypeScript、ESLint、Tailwind
   - 配置 pnpm workspace

2. **第二步：核心包开发**
   - `packages/shared` → `@nop-chaos/shared`：类型定义
   - `packages/core` → `@nop-chaos/core`：布局、路由、权限、标签页管理
   - `packages/ui` → `@nop-chaos/ui`：**使用 shadcn CLI 安装所有组件**
     ```bash
     cd packages/ui
     pnpm dlx shadcn@latest init
     pnpm dlx shadcn@latest add --all
     ```

3. **第三步：主应用开发**
   - 集成核心包
   - 实现登录页
   - 实现主布局
   - 实现动态菜单和路由
   - 实现标签页系统
   - 实现主题系统
   - 实现 i18n

4. **第四步：占位页面**
   - 为每个菜单项创建占位组件
   - 确保路由正常工作
   - 确保标签页功能正常

5. **第五步：插件系统**
   - 实现插件加载基础设施
   - 创建 plugin-demo 示例

### 6.2 关键注意事项

1. **主题配色应根据原型页面确定**，本文件不指定具体颜色值
2. **框架优先于业务**，先确保框架稳定可用
3. **占位组件足够**，不需要实现完整的业务功能
4. **版本要求严格**，所有依赖版本不得低于指定版本
5. **插件系统使用 SystemJS**，确保共享库正确配置
6. **UI 组件通过 CLI 安装**：在 `@nop-chaos/ui` 中使用 `shadcn add --all`，禁止 AI 手写这些组件

---

> 以上即为简化版框架生成提示词。
> 生成代码时，请严格以本文件为唯一真相来源。
> 主题配色、字体等视觉方案应根据实际原型页面确定。
