# nop-entropy E2E Tests

> **定位**：nop-entropy 平台自身的端到端测试基础设施。面向平台开发者。如果你在使用 Nop 构建业务应用，e2e 测试模式参见 `docs-for-ai/02-core-guides/testing.md`。

End-to-end tests using [Playwright](https://playwright.dev/) with auto-started Quarkus backend services.

## Architecture

```
nop-entropy-e2e/
  packages/
    e2e-shared/       # Shared helpers, page objects, RPC utilities
    nop-auth-e2e/     # Auth service tests (port 8080)
    nop-code-e2e/     # Code service tests (port 8081)
    nop-job-e2e/      # Job service tests (port 8082)
    nop-web-e2e/      # Host + Extension contract tests (port 8083)
```

Each test package auto-starts its own Quarkus backend via Playwright's `webServer` config. The backend runs in `dev` profile (H2 database, no MySQL required) and always in **flux render mode** (`-Dnop.web.render-mode=flux`).

> **必须串行跑**：根目录 `pnpm test` 使用 `--workspace-concurrency=1`。四个 `mvn quarkus:dev`
> 并行启动会争抢固定的 JDWP 调试端口，导致 `bind failed: Address already in use`。

> **端口占用**：默认端口 8080/8081/8082/8083。机器上已有服务占用时用 `PORT=xxxx` 覆盖
> （单包执行：`PORT=8181 pnpm --filter nop-code-e2e test`）。端口被占时 Playwright 会
> `reuseExistingServer` 复用别人的进程，表现为 404 `File Not Found`。

### Backend Services

| Package | Backend Module | Default Port | Database |
|---------|---------------|-------------|----------|
| nop-auth-e2e | nop-auth/nop-auth-app | 8080 | H2 |
| nop-code-e2e | nop-code/nop-code-app | 8081 | H2 |
| nop-job-e2e | nop-job/nop-job-app | 8082 | H2 |
| nop-web-e2e | nop-demo/nop-quarkus-demo | 8083 | H2 |

## Prerequisites

1. **Java 21** and **Maven** (use `./mvnw`)
2. **Node.js 20+** and **pnpm**
3. **Playwright browsers**:

```bash
cd nop-entropy-e2e
pnpm install
pnpm --filter nop-auth-e2e exec playwright install --with-deps chromium
```

4. **Maven build** (must be done before e2e tests):

```bash
./mvnw clean install -DskipTests -T 1C
```

## Running Tests

### All tests (starts all 4 backends sequentially)

```bash
cd nop-entropy-e2e
pnpm test
```

### Single package

```bash
pnpm test:auth    # Auth tests only
pnpm test:code    # Code tests only
pnpm test:job     # Job tests only
pnpm test:web     # Host + Extension contract tests only
```

### Custom port

Override the port via environment variable:

```bash
PORT=9080 pnpm test:auth
```

### Skip auto-start (use already-running server)

```bash
SKIP_WEBSERVER=1 BASE_URL=http://localhost:8080 pnpm test:auth
```

### Headed mode (see the browser)

```bash
cd packages/nop-auth-e2e
pnpm test:headed
```

## Test Types

Each package may contain two types of tests:

- **RPC tests** — Direct HTTP API calls via `@nop-entropy/e2e-shared` `rpc()` helper. Uses Playwright's `request` fixture (no browser needed). Ideal for testing backend CRUD, business logic, and data integrity.
- **Browser tests** — Full Playwright browser automation with page objects. Tests real UI interactions (login, form fill, table navigation, modal dialogs).

Both types can be mixed in a single spec file. RPC is often used in `beforeAll`/`beforeEach` to set up test data, then browser tests verify the UI.

---

## Writing New E2E Tests

### 测试代码只写业务意图

**硬约束**：`*.spec.ts` 里不得出现 DOM 选择器、控件类型、引擎分支。业务一律以
`fieldName` 和业务方法名表达；所有定位、表单填充、等待、重试都收口在页面对象里。

```typescript
// ❌ 反例：spec 里漏出技术控件概念
await page.locator('tr').filter({ hasText: roleId }).first().toBeVisible();
await expect(engine.table(page)).toBeVisible();
const dialog = await rolePO.clickAdd();
await dialog.setField('roleId', id);

// ✅ 正例：只表达业务意图
await rolePO.createRole({ roleId, roleName });
await rolePO.assertRoleExists(roleId);
await rolePO.readViewField('roleName');
```

### 1. Create a Page Object

Page objects live in `tests/page-objects/` and extend the shared base classes
(`BasePage` / `CrudListPage`) from `@nop-entropy/e2e-shared`.

**Simple page (navigate + assert ready):**

```typescript
// tests/page-objects/my-feature.po.ts
import type { Page } from '@playwright/test';
import { BasePage, expect } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';

export class MyFeaturePO extends BasePage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine);
  }

  async open(): Promise<void> {
    await this.goto('MyEntity-main');       // 确定性 hash 导航
    await this.assertOpened();
  }

  async assertOpened(): Promise<void> {
    await expect(this.fieldInput('keyword')).toBeVisible({ timeout: 20_000 });
  }
}
```

**CRUD page (business operations over a generic base):**

```typescript
// tests/page-objects/user.po.ts
import type { Page } from '@playwright/test';
import { CrudListPage } from '@nop-entropy/e2e-shared';
import type { EngineAdapter, FormData } from '@nop-entropy/e2e-shared';

export type UserFormData = FormData;   // { fieldName: 业务值 }

export class UserPO extends CrudListPage {
  constructor(page: Page, engine: EngineAdapter) {
    super(page, engine, {
      entityRoute: 'NopAuthUser-main',
      entityName: 'NopAuthUser',
      columnHeaders: ['', 'userName', 'nickName', 'deptId', 'gender', 'phone'],
    });
  }

  async goto(): Promise<void> {
    await super.goto('NopAuthUser-main');
    await this.waitForListWithReload();
  }

  /** 按用户名检索——spec 只知道 userName 这个字段名 */
  async searchUser(userName: string): Promise<void> {
    await this.search('userName', userName);
  }

  async createUser(data: UserFormData): Promise<void> {
    await this.goto();
    await this.clickAdd();
    await this.fillDialog(data);
    await this.clickSave();
  }
}
```

### 2. `CrudListPage` 提供的业务语义 API

| 分类 | 方法 |
|------|------|
| 导航/就绪 | `goto(route)`、`tryGoto(route)`、`navigate()`、`waitForList()`、`waitForListWithReload()`、`assertListVisible()` |
| 检索 | `search(fieldName, value)`、`findRowDeep(text, sortHeader?)`、`findRowDeepOrFail(...)` |
| 表单 | `clickAdd(): FormDialog`、`fillDialog({fieldName: value})`、`readDialogField(fieldName)`、`selectDialogOption(fieldName, option)`、`clickSave()` |
| 行操作 | `clickView(row)`、`clickViewDeep(row)`、`clickEdit(row)`、`clickDelete(row)`、`runRowAction(row, action)`、`confirmRowAction(row, action)`、`openRowPanel(row, action, panel)` |
| 断言 | `assertEntityExists(text)`、`assertEntityNotExists(text)`、`readTableField(row, columnFieldName)`、`readViewField(fieldName)`、`assertViewFieldPresent(fieldName)`、`waitForRow(text)` |
| 状态 | `getTableRowCount()`、`deleteEntityViaApi(entityName, id)`、`graphQL` |

`columnHeaders` 是 `readTableField` / `readViewField` 的列名→表头映射兜底；优先读单元格
自身的 `data-field`，只有渲染层未渲染该属性时才回退到索引映射。

### 2. Write RPC Tests

RPC tests call the backend directly via `POST /r/{operation}`. No browser is involved.

```typescript
import { test, expect } from '@playwright/test';
import { loginRpc, rpc } from '@nop-entropy/e2e-shared';

interface MyItem {
  id: string;
  name: string;
  status: number;
}

test.describe('My Entity - RPC', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request); // login as nop/123
  });

  test('create and retrieve', async ({ request }) => {
    // Create
    const createResp = await rpc<MyItem>(request, 'MyEntity__save', {
      data: { name: 'test', status: 1 },
    });
    expect(createResp.ok).toBeTruthy();
    const id = createResp.data.id;

    // Read
    const getResp = await rpc<MyItem>(request, 'MyEntity__get', { id });
    expect(getResp.ok).toBeTruthy();
    expect(getResp.data.name).toBe('test');

    // Cleanup
    await rpc(request, 'MyEntity__delete', { id });
  });

  test('findPage with filter', async ({ request }) => {
    const resp = await rpc<{ total: number; items: MyItem[] }>(
      request,
      'MyEntity__findPage',
      {
        query: {
          offset: 0,
          limit: 10,
          filter: { $type: 'eq', name: 'name', value: 'test' },
        },
      },
    );
    expect(resp.ok).toBeTruthy();
    expect(resp.data.total).toBeGreaterThanOrEqual(0);
  });
});
```

### 3. Write Browser Tests

浏览器测试只调用业务页面对象的方法。

```typescript
import { test, expect } from '@nop-entropy/e2e-shared';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';
import { UserPO } from './page-objects/user.po.js';

/** 已登录的业务页面——spec 不接触引擎与 DOM */
async function openUserAdmin(page, engine) {
  await login(page, { username: 'nop', password: '123' });
  const userPO = new UserPO(page, engine);
  await userPO.goto();
  return userPO;
}

test.describe('用户管理 - 浏览器', () => {
  test('创建新用户', async ({ request, page, engine }) => {
    await loginRpc(request);
    const data = makeUserData('uicreate');
    const saveResp = await rpc(request, 'NopAuthUser__save', { data });

    const userPO = await openUserAdmin(page, engine);
    await userPO.createUser(data);

    await userPO.searchUser(data.userName);
    await userPO.assertUserExists(data.userName);
  });

  test('编辑用户', async ({ request, page, engine }) => {
    const userPO = await openUserAdmin(page, engine);
    await userPO.searchUser(userName);
    await userPO.editUser(userName, { nickName: '新昵称' });
    await userPO.searchUser(userName);
    expect(await userPO.readTableField(userName, 'nickName')).toBe('新昵称');
  });
});
```

`test` 从 `@nop-entropy/e2e-shared` 导入（而非 `@playwright/test`），它额外注入了
`engine` fixture 与 flux 调试记录器。

### 4. Test Data Cleanup Pattern

Always clean up test data to keep tests idempotent:

```typescript
const createdIds: string[] = [];

test.beforeAll(async ({ request }) => {
  await loginRpc(request);
  // Also cleanup stale data from previous failed runs
  await cleanupTestData(request);
});

test.afterAll(async ({ request }) => {
  for (const id of createdIds) {
    await rpc(request, 'MyEntity__delete', { id }).catch(() => {});
  }
  createdIds.length = 0;
});
```

Use a naming prefix (e.g., `e2e_`) so cleanup can find stale data:

```typescript
async function cleanupTestData(request: APIRequestContext) {
  const resp = await rpc<{ items: { id: string; name: string }[] }>(
    request, 'MyEntity__findPage', { query: { offset: 0, limit: 200 } },
  );
  if (!resp.ok) return;
  for (const item of resp.data.items) {
    if (item.name.startsWith('e2e_')) {
      await rpc(request, 'MyEntity__delete', { id: item.id }).catch(() => {});
    }
  }
}
```

---

## Shared Library API Reference (`@nop-entropy/e2e-shared`)

### RPC Functions

#### `loginRpc(request, username?, password?): Promise<string>`

Authenticate via the Nop RPC API. Caches the access token for subsequent `rpc()` calls.

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| request | `APIRequestContext` | required | Playwright request fixture |
| username | `string` | `'nop'` | Login username |
| password | `string` | `'123'` | Login password |

Returns the access token. Must be called before `rpc()`.

#### `rpc<T>(request, operation, params?, timeoutMs?): Promise<RpcResponse<T>>`

Send an authenticated RPC request to `POST /r/{operation}`.

| Param | Type | Description |
|-------|------|-------------|
| request | `APIRequestContext` | Playwright request fixture |
| operation | `string` | Operation name, e.g. `'NopAuthUser__findPage'` |
| params | `Record<string, unknown>` | Request body parameters |
| timeoutMs | `number` | 显式超时。长耗时同步操作（如 `NopCodeIndex__triggerFullIndex`）必须传，Playwright `apiRequestContext` 缺省只有 10s |

Returns `RpcResponse<T>`:

```typescript
interface RpcResponse<T> {
  status: number;  // HTTP status code
  ok: boolean;     // true if json.status === 0
  data: T;         // Response data payload
}
```

#### `resetAuth(): void`

Clear cached access token. Useful for switching users.

### 页面对象

#### `LoginPage`

登录页页面对象——spec 用它表达"登录 / 断言仍在登录页"，不接触输入框与按钮。

| 方法 | 说明 |
|------|------|
| `open(baseUrl?)` | 打开应用入口 |
| `waitForForm(timeoutMs?)` | 等登录表单渲染完成 |
| `submitCredentials({username, password})` | 填写凭据并提交，**不**断言结果 |
| `assertSignedIn()` / `assertStillOnLogin()` | 断言登录成功 / 仍停留在登录路由 |

#### `BasePage`（抽象）

所有页面对象的基类。

| 方法 | 说明 |
|------|------|
| `goto(route)` | 确定性 hash 导航（赋值 + 生效校验 + 防回弹重试） |
| `tryGoto(route)` | 同上，导航被登录守卫弹回时返回 `false` 而不抛错 |
| `fieldInput(fieldName)` / `fillField(fieldName, value)` | 按 fieldName 定位/填写字段输入控件（`protected`，供子类 PO 使用） |

#### `CrudListPage`（继承 `BasePage`）

CRUD 列表页页面对象。业务语义 API 见上文
[「`CrudListPage` 提供的业务语义 API」](#2-crudlistpage-提供的业务语义-api)。

构造参数 `CrudPageConfig`：

| 字段 | 说明 |
|------|------|
| `entityRoute` | 页面路由（实体名），`navigate()` 拼接 `-main` |
| `entityName` | 业务对象名 |
| `columnHeaders` | 列 fieldName 顺序（首项为 `''` 表示无选择列），`readTableField` / `readViewField` 的兜底映射 |

#### `FormDialog`

弹窗表单封装，由 `clickAdd()` 返回。提供 `setField(fieldName, value)`、
`getField(fieldName)`、`selectOption(fieldNames, options)`、`submit()`、
`switchToTab(label)`、`subForm(fieldName)`。

#### `EngineAdapter`（flux 单引擎）

`getEngine()` 返回 `FluxAdapter`。DOM 契约集中在适配层，spec 与业务 PO 不感知：

| 契约 | 选择器 |
|------|--------|
| CRUD 容器 / 表格 | `.nop-crud` / `.nop-table` |
| 表格行 | `[data-slot="table-body"] tr[data-slot="table-row"]` |
| 单元格 | `td[data-field="<fieldName>"]` |
| 字段根 | `[data-field="<fieldName>"][data-renderer="<renderer>"]` |
| 弹窗 / 抽屉 | `[data-slot="dialog-surface"]` / `[data-slot="drawer-surface"]` |
| 下拉选项 | `[data-slot="combobox-item"][data-value="<value>"]`（只看 `:visible`） |

---

## Nop RPC Conventions

The e2e tests use the Nop platform's RPC API pattern:

### URL Pattern

```
POST /r/{EntityName}__{action}
```

### Standard CRUD Operations

| Operation | Parameters | Description |
|-----------|-----------|-------------|
| `Entity__get` | `{ id }` | Get by ID |
| `Entity__findPage` | `{ query: { offset, limit, filter?, orderBy? } }` | Paginated query |
| `Entity__findList` | `{ query: { filter? } }` | List query |
| `Entity__save` | `{ data: { ...fields } }` | Create (no id) or update (with id) |
| `Entity__update` | `{ data: { id, ...fields } }` | Partial update |
| `Entity__delete` | `{ id }` | Delete by ID |

### Filter Syntax

```typescript
// Equal
{ $type: 'eq', name: 'fieldName', value: 'exactValue' }

// Like
{ $type: 'like', name: 'fieldName', value: '%pattern%' }

// In
{ $type: 'in', name: 'fieldName', value: ['a', 'b'] }

// Range
{ $type: 'range', name: 'fieldName', value: { min: 0, max: 100 } }
```

### Response Format

```json
{
  "status": 0,        // 0 = success
  "code": 0,
  "msg": "",
  "data": { ... }     // Response payload
}
```

The `rpc()` helper returns `resp.ok === true` when `status === 0`.

### Authentication

RPC calls require a Bearer token obtained via login:

```
POST /r/LoginApi__login
Body: { "principalId": "nop", "principalSecret": "123", "loginType": 1 }
Response: { "status": 0, "data": { "accessToken": "..." } }
```

The `loginRpc()` helper manages this automatically.

---

## Playwright Configuration

Each package has its own `playwright.config.ts` with:

- **`webServer`**: Auto-starts the Quarkus backend via `mvn quarkus:dev`
- **`PORT` env var**: Override the default port
- **`SKIP_WEBSERVER` env var**: Disable auto-start (use already-running server)
- **`BASE_URL` env var**: Override the base URL entirely
- **`CI` env var**: Enables retries, forbids `.only`, disables server reuse

---

## Adding a New E2E Test Package

To create tests for a new backend module (e.g., `nop-wf`):

1. **Create the package directory:**

```bash
mkdir -p nop-entropy-e2e/packages/nop-wf-e2e/tests/page-objects
```

2. **Add `package.json`:**

```json
{
  "name": "nop-wf-e2e",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "playwright test",
    "test:headed": "playwright test --headed",
    "report": "playwright show-report",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@nop-entropy/e2e-shared": "workspace:*"
  },
  "devDependencies": {
    "@playwright/test": "~1.60.0",
    "@types/node": "~22.15.0",
    "typescript": "~5.9.3"
  }
}
```

3. **Add `playwright.config.ts`** (customize port and module path):

```typescript
import { defineConfig, devices } from '@playwright/test';

const port = parseInt(process.env.PORT || '8083', 10);
const baseURL = process.env.BASE_URL || `http://localhost:${port}`;

export default defineConfig({
  testDir: 'tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  expect: { timeout: 15_000 },

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
  },

  webServer: process.env.SKIP_WEBSERVER
    ? undefined
    : {
        // render-mode 固定 flux——测试与前端站点资源都是 flux 单引擎
        command: `./mvnw quarkus:dev -Dquarkus.http.port=${port} -Dquarkus.profile=dev -Dnop.web.render-mode=flux`,
        cwd: '../../../../../nop-wf/nop-wf-app',
        port,
        timeout: 120_000,
        reuseExistingServer: !process.env.CI,
        stdout: 'pipe',
        stderr: 'pipe',
      },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
```

4. **Add `tsconfig.json`:**

Copy from an existing package and adjust `outDir`.

5. **Add root script** in `nop-entropy-e2e/package.json`:

```json
"test:wf": "NOP_WEB_RENDER__MODE=flux pnpm --filter nop-wf-e2e test"
```

根 `test` 脚本已带 `--workspace-concurrency=1`，新包会被自动纳入串行执行。

6. **Ensure the backend app** has H2 configured in its `application.yaml` under the `%dev` profile and a unique `quarkus.http.port`.

---

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `ECONNREFUSED` | Backend not started | Remove `SKIP_WEBSERVER` or run `./mvnw clean install -DskipTests` first |
| `Login failed` | Backend not ready when test starts | Increase `webServer.timeout` (default 120s) |
| Port conflict | Another process on the same port | Use `PORT=XXXX` env var to pick a different port |
| `Playwright not found` | Browsers not installed | Run `pnpm exec playwright install --with-deps chromium` |
| `Module not found` | pnpm deps not installed | Run `pnpm install` |
| Build fails after code change | Stale jars | Run `./mvnw clean install -DskipTests -T 1C` |
| SPA blank (white screen after login) | Missing or mismatched frontend build | Not a `.js.gz` decompression issue. Quarkus/Undertow serves `.js.gz` transparently with `Content-Encoding: gzip` — **do not decompress**. Rebuild the frontend at `nop-frontend-support/nop-web-site` |
