# E2E 测试排障指南

> **受众**：基于 Nop 平台编写 E2E 测试的开发者与 AI。列出常见失败模式、根因和修复方向。

## 通用调试方法

### 0. `page.content()` —— 不猜，先看 DOM

**不要猜选择器为什么失效。Playwright 超时时，第一步永远是 `page.content()` 抓取完整 DOM。**

```typescript
import { writeFileSync } from 'fs';

// 在测试中插入（调试用，提交前删除）
const html = await page.content();
writeFileSync('/tmp/debug.html', html);
console.log('Saved /tmp/debug.html — check main-content area for error boundaries');
```

**排查步骤**：
1. 在超时前（或 `.waitForSelector` 失败后）调用 `page.content()` 写入文件
2. 搜索 `main-content` 区域：查找 `error`、`Error`、`ErrorBoundary`、`circle-alert` 等关键词
3. 如果页面是空白或报错 text，不要花时间猜选择器是否写对——先确认页面本身渲染了正确的组件

**典型例子**：Flux 模式下 `waitForList` 超时，根因可能是 React rolldown 打包错误（error boundary 显示 `Calling "require" for "react"...`），而不是 `.nop-table` 选择器写错。`page.content()` 可以直接暴露这类错误信息。

### 1. `curl` 验证后端 API

在后端 API 层面验证数据是否正确返回：

```bash
curl -s -X POST http://localhost:8080/r/SiteMapApi__getSiteMap \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'nop-locale: zh-CN' -H 'nop-tenant: 0' \
  | python3 -m json.tool | head -50
```

### 2. 日志优先：超时/空白是"果"，先查服务端

Playwright 超时、页面空白、列表不出现都是**症状**，根因几乎总在服务端或页面模型层。六步法：

1. **自起干净 server**（不沿用遗留/他人占用 server），保证日志可追踪；
2. **最小复现 + pass/fail 对照**：同一请求的已知成功路径 vs 失败路径，缩小变量；
3. **倒查服务端日志**：按 `errorCode=`（错误码）→ `@_loc=[行:列]/vfs路径`（view.xml/xmeta 层定位）→ `Caused by:`（异常链）顺序读结构化错误；
4. **后端 mvn 全绿 ≠ 前端渲染绿**：页面模型错误后端单测覆盖不到（`validate-page-model` 类检查在启动期页面聚合才触发），后端全绿页面仍可能渲染失败；
5. **长期跨计划携带的"环境问题"几乎都是未诊断的真实缺陷**：不要用"环境问题"兜底，先按 1-3 排除；
6. 判别：`page.content()` 看**浏览器端 DOM**（组件是否渲染），服务端日志看**根因**（为什么失败）——先服务端后浏览器，参见 `04-reference/debugging-checklist.md`。

## 页面显示「500 模块渲染失败」

### 1. SiteMapApi 返回 `children: null` 导致侧边栏崩溃

**症状**：登录后浏览器控制台报渲染错误，所有页面均显示「500 模块渲染失败」。侧边栏 DOM 存在但渲染中断。

**根因**：`SiteMapApi__getSiteMap` 返回的 TOPM（顶级菜单）资源如果无子节点，`children` 字段为 `null` 而非空数组 `[]`。React SPA 的菜单渲染器遍历 `children` 时遇到 `null` 崩溃，导致整个侧边栏渲染失败，所有页面均无法加载。

**排查**：

```bash
# 用 curl 直接查看 SiteMapApi 返回值中的 children 字段
curl -s -X POST http://localhost:8080/r/SiteMapApi__getSiteMap \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'nop-locale: zh-CN' -H 'nop-tenant: 0' \
  | python3 -c "
import json,sys
data = json.load(sys.stdin)
def check(node, path):
    c = node.get('children')
    if c is None:
        print(f'{path}/children = null (CRASH!)')
    elif isinstance(c, list):
        for i, child in enumerate(c):
            check(child, f'{path}/{i}')
check(data.get('data',{}), 'root')
"
```

**触发条件**：任何 TOPM 类型的 `NopAuthResource` 记录（包括测试创建的资源）只要出现在 SiteMapApi 响应中，且没有子菜单（`children` 为空），就会触发此问题。

**修复/规避**：

| 方式 | 说明 |
|------|------|
| 创建资源后触发 SPA 重加载 | 在浏览器测试中，先 `browserLogin` 登录 SPA，然后创建资源，之后 `page.goto('#/...')` 仅更改 hash 路由，SPA 不再重新拉取 SiteMapApi |
| 避免在 SPA 加载前创建 TOPM 资源 | 不要先在 RPC 中创建 TOPM 资源再执行 `browserLogin`，这会污染 SiteMapApi 响应 |
| 使用 `SUBM` 类型 | 测试资源改用 `resourceType: 'SUBM'`（子菜单），不会出现在顶级菜单中 |
| 不要 `page.reload()` | 如果 SPA 已加载、资源在之后才被创建，`page.reload()` 会重新拉取 SiteMapApi，此时新创建的 TOPM 资源会触发崩溃 |

**关键顺序**：`goto()` 后资源的增删不影响已加载的 SiteMapApi（SPA 只拉取一次）。所以正确的测试顺序是：先登录 SPA，再创建测试资源，最后 `goto()` 到 CRUD 页面。

### 2. 侧边栏菜单为空（已加载但无数据）

**症状**：登录后 `.menu-scroll` 内无子元素，但无渲染错误。SiteMapApi 返回的 `data.children` 为空数组或非常少。

**排查顺序**：

1. **确认没有误用 `MockAuthAdapter` 的 `login`**
   `MockAuthAdapter.ts` 导出 `login as mockLogin`，但模块索引 `index.ts` 也导出了 `Navigation.ts` 的 `login`。测试中用 `from '@nop-entropy/e2e-shared'` 导入的 `login` 来自 `Navigation.ts`（真实登录）。**只有显式导入 `mockLogin` 才会启用 mock 模式**。如果误用 `mockLogin`，SiteMapApi 会被拦截返回 `defaultSiteMapResponse`（无 `NopAuthResource-main` 等业务路由）。

2. **确认 `ensureDefaultSite` 没有静默失败**
   `beforeEach` 中调 `NopAuthSite__save({data: {siteId:'main', ...}})`。首次调用成功（INSERT），后续调用因 `siteId: 'main'` 已存在而报 `duplicate-key` 错误，被 `.catch(() => {})` 吞掉。**这不影响页面渲染**（站点记录仍存在），但如果 site 不存在则会影响到菜单加载。

### 2. CRUD 列表不可见（flux 单引擎）

**症状**：`waitForList` 超时，`.nop-crud` / `.nop-table` 未渲染。

> **渲染引擎已收敛为 flux 单引擎**：`nop-entropy-e2e` 不再有 amis 分支，`E2E_ENGINE`
> 环境变量与 `test:amis` 脚本已删除，`playwright.config.ts` 恒定传
> `-Dnop.web.render-mode=flux`。前端站点资源（`nop-frontend-support/nop-web-site`）
> 同样只含 flux 产物。

**排查顺序**：

1. **检查后端 render-mode**
   - 经 Playwright webServer 启动时 `playwright.config.ts` 已自动传 `-Dnop.web.render-mode=flux`
   - 手动 `mvn quarkus:dev` 时必须自行补上，否则页面模型按 amis 渲染、flux 适配器全部落空
   - 前后端渲染模式不匹配时页面渲染为空或组件无法被适配器识别

2. **检查前端站点资源是否为最新 flux 构建**
   - `nop-web-site` jar 里的静态资源决定了被测前端；换过前端后必须重装：
     `./mvnw install -pl nop-frontend-support/nop-web-site -DskipTests`

3. **不要用 `page.goto('#/...')` 做导航**
   - SPA 已加载时 `page.goto('#/route')` 会被壳层自身导航（登录后重定向）踩掉或静默忽略
   - 统一走页面对象的 `goto(route)` / `navigateTo(page, route)`：赋值 `location.hash`
     + 生效校验 + 防回弹重试（`@nop-entropy/e2e-shared` 的 `hashRoute` 模块）

4. **检查共享 context 中的 cookie 冲突**
   - `request.post('/r/LoginApi__login', {data: {principalId, principalSecret, loginType: 1}})` 使用 `APIRequestContext`，不设 cookie
   - 浏览器表单登录后，context 中会设置 `nop-token` cookie — 这是浏览器会话的凭证
   - 两个登录方式共享同一个 context 的 cookie jar，但 token 机制不同，一般不会冲突。如果设了请求级别的 auth header，优先于 cookie

5. **RPC 创建的资源在 CRUD 表中不可见**
   Playwright 的 `APIRequestContext`（`loginRpc` + `rpc` 调用）和浏览器页面会话隔离。通过 RPC 创建的 `NopAuthResource` 不会出现在后续 `page.goto` 加载的 CRUD 表格中。

   **根因**：不同会话上下文 + CRUD 列表只在首次渲染/查询时取数，不自动拉取后续 RPC 创建的数据。

   **正确做法**：在浏览器测试中，通过业务页面对象用 UI 表单创建资源，而非 RPC：

   ```typescript
   // ❌ 不推荐：RPC 创建，CRUD 表看不到
   await rpc(request, 'NopAuthResource__save', { data: {...} });
   await resourcePO.goto(); // 表为空

   // ✅ 推荐：UI 表单创建，CRUD 表自动刷新
   await resourcePO.createResource({ resourceId: '...', displayName: '...' });
   await resourcePO.waitForList(); // 资源已出现在表中
   ```

## RPC 调用失败

### 1. LoginApi__login 返回 400/500

**常见原因**：

两条路径（`APIRequestContext.post()` 与 `RpcClient.ts` 的裸 `fetch`）参数格式已统一为
`{principalId, principalSecret, loginType}`：

```typescript
{ principalId: 'nop', principalSecret: '123', loginType: 1 }
```

- 不要省略 `loginType: 1`，否则参数验证失败
- 裸 `fetch` 路径的 `url` 必须是**绝对 origin**（`new URL(page.url()).origin`）；
  传相对路径 `'/'` 会拼出非法 URL

### 2. ensureDefaultSite 报 duplicate-key

**根因**：`NopAuthSite__save` 中未传 `version` 字段，ORM 无法判断是 INSERT 还是 UPDATE。首次调用 INSERT 成功，后续调用因 `siteId: 'main'` 唯一键冲突而失败。

**修复方向**：如果站点肯定已存在，跳过 `ensureDefaultSite`；或用 `NopAuthSite__get` 先查再决定是否 save；或在 `save` 对象中包含 `version` 字段。

## RPC 调用错误处理

### 1. Nop RPC 返回错误的方式

Nop 的 RPC 接口（`/r/{operation}`）**总是返回 HTTP 200**，错误信息编码在 JSON body 中：

```json
{"status": -1, "code": "nop.err.dao.sql.duplicate-key", "msg": "数据库记录的键值冲突"}
```

因此**判断 RPC 是否成功不能只看 HTTP 状态码**，必须解析 body 并检查 `status` 字段：

```typescript
const resp = await request.post('/r/NopAuthSite__save', { data: {...} });
const json = await resp.json();
if (json.status !== 0) {
  // 错误在 json.code 和 json.msg 中
  console.error('RPC failed:', json.code, json.msg);
}
```

### 2. 不要用 `.catch(() => {})` 吞掉 RPC 错误

**错误写法**：
```typescript
await rpc(request, 'NopAuthSite__save', { data: {...} }).catch(() => {});
```

`.catch(() => {})` 只能捕获异常（网络中断、JSON 解析失败），**无法捕获 Nop 业务错误**（`status: -1`）。`rpc()` 对 Nop 错误不会抛异常，而是返回 `{ok: false, status: -1, errors: [...]}`。正确做法：

| 场景 | 正确做法 |
|------|----------|
| 清理操作（可容忍失败） | 检查 `resp.ok`，失败时至少 `console.warn` |
| 前置条件（必须成功） | 检查 `resp.ok` 并 `expect(resp.ok).toBe(true)` |
| 仅用于调试 | 检查 `resp.errors` 或 `resp.status` 打印完整错误信息 |

**推荐模式**：
```typescript
const resp = await rpc(request, 'NopAuthSite__save', { data: {...} });
if (!resp.ok) {
  console.warn(`ensureDefaultSite failed: status=${resp.status}, errors=${JSON.stringify(resp.errors)}`);
}
```

### 3. `rpc()` 函数的返回值

| 字段 | 类型 | 说明 |
|------|------|------|
| `ok` | `boolean` | `status === 0` |
| `status` | `number` | Nop 返回的状态码（0=成功，-1=业务错误） |
| `data` | `T` | 成功时的数据 |
| `errors` | `Array<{message: string}>` | 错误详情（如有） |

调用方应优先检查 `resp.ok`，不 ok 时读取 `resp.errors` 或 `resp.status` 获得错误信息，不要假设异常会被抛出。

## 测试间状态泄漏

### 1. createdResourceIds 跨测试积累

`createdResourceIds` 是模块级数组，RPC `describe` 和浏览器 `describe` 共用。如果 `afterAll` / `afterEach` 未能正确清理，残留的 ID 会在下一个测试块中被删除，可能影响其他测试创建的同名资源。

**最佳实践**：
- RPC block 的 `afterAll` 只清理 RPC block 创建的 ID
- Browser block 的 `afterEach` 只清理该测试创建的 ID
- 两个 block 各用独立的 `createdResourceIds` 数组

### 2. H2 数据库状态

- Quarkus dev 模式下 H2 是 `jdbc:h2:mem:test`（内存库），后端重启后数据清零
- 如果后端不重启，之前测试创建的、删除的记录会残留到下一个测试运行
- `NopAuthResource` 使用 `useLogicalDelete="true"`，`__delete` 只设 `DEL_FLAG=1`，记录仍在表中
- 软删除的资源理论上不会被业务查询返回（`DEL_FLAG=0` 过滤），但如果 site map 有缓存，可能返回过期数据

### 3. NopAuthResource 的版本号（version）

`NopAuthResource` 有 `versionProp="version"`（乐观锁）。如果用 `save` 更新已存在的资源但没传 `version`，ORM 可能报乐观锁异常。`save` 时如果传入 `version` 值则按 UPDATE 执行，否则按 INSERT。

## Playwright context 生命周期

| 对象 | 生命周期 | 共享的数据 |
|------|----------|-----------|
| `browser` | 测试文件级别或全局 | 无 |
| `context` | 测试文件级别（默认） | cookies、localStorage、sessionStorage |
| `page` | 单个测试 | 控制台事件监听（`fixtures.ts` 中附加） |

- `sessionStorage` 在 Playwright 的 Chromium 中跨 page 共享（同一 context 下），直到 context 关闭
- `localStorage` 同理，测试完成后不会自动清除
- 如果在测试 A 中写入 `localStorage` 或 `sessionStorage`，测试 B 能读到
- 建议：如果测试依赖干净的存储状态，在 `beforeEach` 中显式 `page.evaluate(() => localStorage.clear())`

## E2E 收口验证门禁

- **Playwright 收口不得只用无 console/pageerror 守卫的调试 spec**——须跑带 console/pageerror 断言守卫的 fixtures 套件；无守卫的调试 spec 可能因 bundle 重复注册、运行时错误而假绿。
- **页面范式重写批（如 page.yaml → flux 原生）必须同步扫描消费旧页面契约的浏览器 spec**（等待谓词/选择器/断言 token）——"页面改了、测试谓词没跟"是迁移期高发回归形态。

## 相关文档

- `e2e-testing.md`（E2E 测试模式与标准 CRUD）
- `../00-required-reading-e2e-testing.md`（E2E 必读索引）
- `{nop-entropy-e2e}/README.md`（平台内部实现参考）
