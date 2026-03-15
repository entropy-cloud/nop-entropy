# React 最佳实践 - frontend-shell-v2 实现指南

> 本文档从 `.opencode/skills/react-best-practices` 中提取了对实现 `frontend-shell-v2.md` 有价值的最佳实践。
> 来源：Vercel Engineering - React Best Practices v1.0.0

---

## 目录

1. [组件设计模式](#1-组件设计模式)
2. [状态管理最佳实践](#2-状态管理最佳实践)
3. [数据请求优化](#3-数据请求优化)
4. [渲染性能优化](#4-渲染性能优化)
5. [localStorage 持久化](#5-localstorage-持久化)
6. [事件处理优化](#6-事件处理优化)
7. [Bundle 优化](#7-bundle-优化)
8. [JavaScript 性能优化](#8-javascript-性能优化)
9. [高级模式](#9-高级模式)
10. [项目特定优化建议](#10-项目特定优化建议)

---

## 1. 组件设计模式

### 1.1 不要在组件内部定义组件

**影响：HIGH** - 防止每次渲染时重新挂载

在组件内部定义组件会在每次渲染时创建新的组件类型。React 会将其视为不同的组件，完全重新挂载，销毁所有状态和 DOM。

```tsx
// ❌ 错误：每次渲染都重新挂载
function UserProfile({ user, theme }) {
  const Avatar = () => (
    <img src={user.avatarUrl} className={theme === 'dark' ? 'avatar-dark' : 'avatar-light'} />
  )
  return <div><Avatar /></div>
}

// ✅ 正确：提取到外部，通过 props 传递
function Avatar({ src, theme }: { src: string; theme: string }) {
  return <img src={src} className={theme === 'dark' ? 'avatar-dark' : 'avatar-light'} />
}

function UserProfile({ user, theme }) {
  return <div><Avatar src={user.avatarUrl} theme={theme} /></div>
}
```

**症状**：
- 输入框每次按键都失去焦点
- 动画意外重启
- useEffect 的 cleanup/setup 在每次父组件渲染时运行
- 滚动位置重置

### 1.2 Memoized 组件的默认参数提取

**影响：MEDIUM** - 恢复 memo 优化

当 memoized 组件有非基本类型（数组、函数、对象）的默认参数时，每次渲染都会创建新实例，导致 memo 失效。

```tsx
// ❌ 错误：onClick 每次渲染都是新值
const UserAvatar = memo(function UserAvatar({ onClick = () => {} }: { onClick?: () => void }) {
  // ...
})

// ✅ 正确：使用稳定的常量作为默认值
const NOOP = () => {};

const UserAvatar = memo(function UserAvatar({ onClick = NOOP }: { onClick?: () => void }) {
  // ...
})
```

### 1.3 在渲染时计算派生状态

**影响：MEDIUM** - 避免冗余渲染和状态漂移

如果值可以从当前 props/state 计算得出，不要存储到 state 或在 effect 中更新。

```tsx
// ❌ 错误：冗余的 state 和 effect
function Form() {
  const [firstName, setFirstName] = useState('First')
  const [lastName, setLastName] = useState('Last')
  const [fullName, setFullName] = useState('')

  useEffect(() => {
    setFullName(firstName + ' ' + lastName)
  }, [firstName, lastName])

  return <p>{fullName}</p>
}

// ✅ 正确：在渲染时派生
function Form() {
  const [firstName, setFirstName] = useState('First')
  const [lastName, setLastName] = useState('Last')
  const fullName = firstName + ' ' + lastName

  return <p>{fullName}</p>
}
```

---

## 2. 状态管理最佳实践

### 2.1 使用函数式 setState 更新

**影响：MEDIUM** - 防止闭包陷阱和不必要的回调重建

```tsx
// ❌ 错误：需要 state 作为依赖
function TodoList() {
  const [items, setItems] = useState(initialItems)
  
  const addItems = useCallback((newItems: Item[]) => {
    setItems([...items, ...newItems])
  }, [items])  // items 变化导致回调重建
  
  return <ItemsEditor items={items} onAdd={addItems} />
}

// ✅ 正确：稳定的回调，无闭包陷阱
function TodoList() {
  const [items, setItems] = useState(initialItems)
  
  const addItems = useCallback((newItems: Item[]) => {
    setItems(curr => [...curr, ...newItems])
  }, [])  // 无依赖
  
  return <ItemsEditor items={items} onAdd={addItems} />
}
```

### 2.2 使用惰性状态初始化

**影响：MEDIUM** - 避免每次渲染都执行昂贵的初始化

```tsx
// ❌ 错误：每次渲染都执行 JSON.parse
function UserProfile() {
  const [settings, setSettings] = useState(
    JSON.parse(localStorage.getItem('settings') || '{}')
  )
  return <SettingsForm settings={settings} onChange={setSettings} />
}

// ✅ 正确：只在初始渲染时执行
function UserProfile() {
  const [settings, setSettings] = useState(() => {
    const stored = localStorage.getItem('settings')
    return stored ? JSON.parse(stored) : {}
  })
  return <SettingsForm settings={settings} onChange={setSettings} />
}
```

### 2.3 使用 useRef 存储频繁变化的临时值

**影响：MEDIUM** - 避免不必要的重渲染

当值频繁变化且不需要触发重渲染时（如鼠标位置、临时标志），使用 ref 而非 state。

```tsx
// ❌ 错误：每次鼠标移动都触发重渲染
function Tracker() {
  const [lastX, setLastX] = useState(0)
  useEffect(() => {
    const onMove = (e: MouseEvent) => setLastX(e.clientX)
    window.addEventListener('mousemove', onMove)
    return () => window.removeEventListener('mousemove', onMove)
  }, [])
  // ...
}

// ✅ 正确：无重渲染
function Tracker() {
  const lastXRef = useRef(0)
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      lastXRef.current = e.clientX
    }
    window.addEventListener('mousemove', onMove)
    return () => window.removeEventListener('mousemove', onMove)
  }, [])
  // ...
}
```

### 2.4 使用 Transitions 处理非紧急更新

**影响：MEDIUM** - 保持 UI 响应性

```tsx
import { startTransition } from 'react'

// ❌ 错误：每次滚动都阻塞 UI
function ScrollTracker() {
  const [scrollY, setScrollY] = useState(0)
  useEffect(() => {
    const handler = () => setScrollY(window.scrollY)
    window.addEventListener('scroll', handler, { passive: true })
    return () => window.removeEventListener('scroll', handler)
  }, [])
}

// ✅ 正确：非阻塞更新
function ScrollTracker() {
  const [scrollY, setScrollY] = useState(0)
  useEffect(() => {
    const handler = () => {
      startTransition(() => setScrollY(window.scrollY))
    }
    window.addEventListener('scroll', handler, { passive: true })
    return () => window.removeEventListener('scroll', handler)
  }, [])
}
```

---

## 3. 数据请求优化

### 3.1 使用 React Query 进行自动去重

**影响：MEDIUM-HIGH** - 自动请求去重

项目使用 `@tanstack/react-query`，它提供类似 SWR 的自动去重功能。

```tsx
// ❌ 错误：每个实例都发请求，无去重
function UserList() {
  const [users, setUsers] = useState([])
  useEffect(() => {
    fetch('/api/users')
      .then(r => r.json())
      .then(setUsers)
  }, [])
}

// ✅ 正确：多个实例共享一个请求
import { useQuery } from '@tanstack/react-query'

function UserList() {
  const { data: users } = useQuery({
    queryKey: ['users'],
    queryFn: () => fetch('/api/users').then(r => r.json())
  })
}
```

### 3.2 并行执行独立操作

**影响：CRITICAL** - 2-10 倍性能提升

```tsx
// ❌ 错误：顺序执行，3 次往返
const user = await fetchUser()
const posts = await fetchPosts()
const comments = await fetchComments()

// ✅ 正确：并行执行，1 次往返
const [user, posts, comments] = await Promise.all([
  fetchUser(),
  fetchPosts(),
  fetchComments()
])
```

---

## 4. 渲染性能优化

### 4.1 使用显式条件渲染

**影响：LOW** - 防止渲染 0 或 NaN

```tsx
// ❌ 错误：count=0 时渲染 "0"
function Badge({ count }: { count: number }) {
  return <div>{count && <span className="badge">{count}</span>}</div>
}

// ✅ 正确：count=0 时什么都不渲染
function Badge({ count }: { count: number }) {
  return <div>{count > 0 ? <span className="badge">{count}</span> : null}</div>
}
```

### 4.2 CSS content-visibility 用于长列表

**影响：HIGH** - 更快的初始渲染

```css
/* 全局样式 */
.tab-item {
  content-visibility: auto;
  contain-intrinsic-size: 0 40px;
}
```

```tsx
// 用于标签页列表、菜单列表等
function TabsBar({ tabs }: { tabs: Tab[] }) {
  return (
    <div className="overflow-x-auto">
      {tabs.map(tab => (
        <div key={tab.id} className="tab-item">
          {tab.title}
        </div>
      ))}
    </div>
  )
}
```

### 4.3 提升静态 JSX 元素

**影响：LOW** - 避免重新创建

```tsx
// ❌ 错误：每次渲染都创建新元素
function Container() {
  return <div>{loading && <div className="animate-pulse h-4 bg-gray-200" />}</div>
}

// ✅ 正确：复用同一元素
const loadingSkeleton = <div className="animate-pulse h-4 bg-gray-200" />

function Container() {
  return <div>{loading && loadingSkeleton}</div>
}
```

### 4.4 使用 useTransition 替代手动 loading 状态

**影响：LOW** - 减少重渲染

```tsx
import { useTransition, useState } from 'react'

// ❌ 错误：手动管理 loading 状态
function SearchResults() {
  const [isLoading, setIsLoading] = useState(false)
  
  const handleSearch = async (value: string) => {
    setIsLoading(true)
    const data = await fetchResults(value)
    setResults(data)
    setIsLoading(false)
  }
}

// ✅ 正确：使用 useTransition
function SearchResults() {
  const [isPending, startTransition] = useTransition()
  
  const handleSearch = (value: string) => {
    startTransition(async () => {
      const data = await fetchResults(value)
      setResults(data)
    })
  }
  // isPending 自动提供 pending 状态
}
```

---

## 5. localStorage 持久化

### 5.1 版本化和最小化存储数据

**影响：MEDIUM** - 防止 schema 冲突

用于主题、标签页、菜单展开状态等持久化。

```tsx
// ❌ 错误：无版本、无错误处理
localStorage.setItem('theme', JSON.stringify(fullThemeObject))

// ✅ 正确：版本化 + 最小化 + 错误处理
const STORAGE_VERSION = 'v1'

function saveTheme(theme: { mode: 'light' | 'dark' }) {
  try {
    localStorage.setItem(`theme:${STORAGE_VERSION}`, JSON.stringify(theme))
  } catch {
    // 无痕模式、配额超限或被禁用时会抛出异常
  }
}

function loadTheme() {
  try {
    const data = localStorage.getItem(`theme:${STORAGE_VERSION}`)
    return data ? JSON.parse(data) : null
  } catch {
    return null
  }
}

// 迁移示例
function migrateTheme() {
  try {
    const old = localStorage.getItem('theme:v0')
    if (old) {
      const oldData = JSON.parse(old)
      saveTheme({ mode: oldData.darkMode ? 'dark' : 'light' })
      localStorage.removeItem('theme:v0')
    }
  } catch {}
}
```

### 5.2 缓存 Storage API 调用

**影响：LOW-MEDIUM** - 减少昂贵的 I/O

```tsx
// 缓存 localStorage 读取
const storageCache = new Map<string, string | null>()

function getLocalStorage(key: string) {
  if (!storageCache.has(key)) {
    storageCache.set(key, localStorage.getItem(key))
  }
  return storageCache.get(key)
}

function setLocalStorage(key: string, value: string) {
  localStorage.setItem(key, value)
  storageCache.set(key, value)  // 保持缓存同步
}

// 监听外部变化（其他标签页）
window.addEventListener('storage', (e) => {
  if (e.key) storageCache.delete(e.key)
})
```

---

## 6. 事件处理优化

### 6.1 使用被动事件监听器

**影响：MEDIUM** - 消除滚动延迟

```tsx
// ❌ 错误：阻塞滚动
useEffect(() => {
  const handleWheel = (e: WheelEvent) => console.log(e.deltaY)
  document.addEventListener('wheel', handleWheel)
  return () => document.removeEventListener('wheel', handleWheel)
}, [])

// ✅ 正确：立即滚动
useEffect(() => {
  const handleWheel = (e: WheelEvent) => console.log(e.deltaY)
  document.addEventListener('wheel', handleWheel, { passive: true })
  return () => document.removeEventListener('wheel', handleWheel)
}, [])
```

### 6.2 将交互逻辑放在事件处理器中

**影响：MEDIUM** - 避免 effect 重复运行

```tsx
// ❌ 错误：事件建模为 state + effect
function Form() {
  const [submitted, setSubmitted] = useState(false)
  
  useEffect(() => {
    if (submitted) {
      post('/api/register')
      showToast('Registered')
    }
  }, [submitted])
  
  return <button onClick={() => setSubmitted(true)}>Submit</button>
}

// ✅ 正确：直接在处理器中执行
function Form() {
  function handleSubmit() {
    post('/api/register')
    showToast('Registered')
  }
  
  return <button onClick={handleSubmit}>Submit</button>
}
```

### 6.3 缩小 Effect 依赖

**影响：LOW** - 最小化 effect 重新运行

```tsx
// ❌ 错误：任何 user 字段变化都会重新运行
useEffect(() => {
  console.log(user.id)
}, [user])

// ✅ 正确：只在 id 变化时重新运行
useEffect(() => {
  console.log(user.id)
}, [user.id])
```

---

## 7. Bundle 优化

### 7.1 避免桶文件导入

**影响：CRITICAL** - 200-800ms 导入成本

大型图标库和组件库可能有 **10,000+ 个重导出**，直接导入可以避免加载未使用的模块。

```tsx
// ❌ 错误：可能加载整个图标库（取决于打包配置）
import { Check, X, Menu, LayoutDashboard, Bot } from 'lucide-react'

// ✅ 正确：直接导入（如果打包工具未优化）
import Check from 'lucide-react/dist/esm/icons/check'
import X from 'lucide-react/dist/esm/icons/x'
import Menu from 'lucide-react/dist/esm/icons/menu'
```

**Vite 优化配置**：

```ts
// vite.config.ts
export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // 将 lucide-react 单独打包
          'lucide': ['lucide-react'],
          // 将 recharts 单独打包
          'recharts': ['recharts'],
        }
      }
    }
  }
})
```

### 7.2 动态导入重型组件

**影响：CRITICAL** - 直接影响 TTI 和 LCP

对于 **Vite + React** 项目，使用 `React.lazy()` + `Suspense` 进行代码分割：

```tsx
import { lazy, Suspense } from 'react'

// 用于 @xyflow/react 流程编辑器等重型组件
const FlowEditor = lazy(() => import('./FlowEditor').then(m => ({ 
  default: m.FlowEditor 
})))

// 用于 Recharts 图表
const DashboardChart = lazy(() => import('./DashboardChart'))

// 使用时配合 Suspense
function FlowEditorPage() {
  return (
    <Suspense fallback={<Skeleton className="h-96" />}>
      <FlowEditor />
    </Suspense>
  )
}
```

**注意**：原始文档使用 `next/dynamic`（Next.js 特有），这里改为 Vite 兼容的 `React.lazy()`。

### 7.3 基于用户意图预加载

**影响：MEDIUM** - 减少感知延迟

```tsx
function EditorButton({ onClick }: { onClick: () => void }) {
  const preload = () => {
    if (typeof window !== 'undefined') {
      void import('./FlowEditor')
    }
  }

  return (
    <button
      onMouseEnter={preload}
      onFocus={preload}
      onClick={onClick}
    >
      打开流程编辑器
    </button>
  )
}
```

---

## 8. JavaScript 性能优化

### 8.1 使用 Set/Map 进行 O(1) 查找

**影响：LOW-MEDIUM** - O(n) 到 O(1)

```tsx
// 用于菜单权限检查
const allowedRoles = new Set(['admin', 'editor'])

function canAccess(userRoles: string[]) {
  return userRoles.some(role => allowedRoles.has(role))
}

// 用于菜单项查找
function buildMenuMap(menus: MenuItem[]) {
  const menuById = new Map(menus.map(m => [m.id, m]))
  const menuByPath = new Map(menus.map(m => [m.path, m]))
  return { menuById, menuByPath }
}
```

### 8.2 使用 toSorted() 替代 sort() 保持不可变性

**影响：MEDIUM-HIGH** - 防止 React state 变异 bug

```tsx
// ❌ 错误：变异原数组
function UserList({ users }: { users: User[] }) {
  const sorted = useMemo(
    () => users.sort((a, b) => a.name.localeCompare(b.name)),
    [users]
  )
  return <div>{sorted.map(renderUser)}</div>
}

// ✅ 正确：创建新数组
function UserList({ users }: { users: User[] }) {
  const sorted = useMemo(
    () => users.toSorted((a, b) => a.name.localeCompare(b.name)),
    [users]
  )
  return <div>{sorted.map(renderUser)}</div>
}

```

### 8.3 合并多个数组迭代

**影响：LOW-MEDIUM** - 减少迭代次数

```tsx
// ❌ 错误：3 次迭代
const admins = users.filter(u => u.isAdmin)
const testers = users.filter(u => u.isTester)
const inactive = users.filter(u => !u.isActive)

// ✅ 正确：1 次迭代
const admins: User[] = []
const testers: User[] = []
const inactive: User[] = []

for (const user of users) {
  if (user.isAdmin) admins.push(user)
  if (user.isTester) testers.push(user)
  if (!user.isActive) inactive.push(user)
}
```

---

## 9. 高级模式

### 9.1 应用初始化只执行一次

**影响：LOW-MEDIUM** - 避免开发模式下重复初始化

不要将应用级初始化（必须在整个应用生命周期中只运行一次）放在组件的 `useEffect([])` 中。组件可能会重新挂载，effect 会重新运行。应该使用模块级别的守卫或在入口模块中初始化。

```tsx
// ❌ 错误：开发模式运行两次，重新挂载时会重新运行
function App() {
  useEffect(() => {
    loadFromStorage()
    checkAuthToken()
    initTheme()
  }, [])
  
  // ...
}

// ✅ 正确：每次应用加载只执行一次
let didInit = false

function App() {
  useEffect(() => {
    if (didInit) return
    didInit = true
    loadFromStorage()
    checkAuthToken()
    initTheme()
  }, [])
  
  // ...
}
```

**为什么重要**：
- React 18 StrictMode 会在开发模式下挂载组件两次
- 路由切换可能导致组件重新挂载
- 避免重复的事件监听器注册

**参考**：[React 官方文档 - 你可能不需要 Effect](https://react.dev/learn/you-might-not-need-an-effect#initializing-the-application)

---

## 参考资源

- [React 官方文档](https://react.dev)
- [You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect)
- [React Compiler](https://react.dev/learn/react-compiler)
- [TanStack Query 文档](https://tanstack.com/query)
- [Zustand 文档](https://zustand-demo.pmnd.rs/)
