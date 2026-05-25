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
```

Each test package auto-starts its own Quarkus backend via Playwright's `webServer` config. The backend runs in `dev` profile (H2 database, no MySQL required).

### Backend Services

| Package | Backend Module | Default Port | Database |
|---------|---------------|-------------|----------|
| nop-auth-e2e | nop-auth/nop-auth-app | 8080 | H2 |
| nop-code-e2e | nop-code/nop-code-app | 8081 | H2 |
| nop-job-e2e | nop-job/nop-job-app | 8082 | H2 |

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

### All tests (starts all 3 backends)

```bash
cd nop-entropy-e2e
pnpm test
```

### Single package

```bash
pnpm test:auth    # Auth tests only
pnpm test:code    # Code tests only
pnpm test:job     # Job tests only
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

### 1. Create a Page Object

Page objects live in `tests/page-objects/` and extend shared base classes.

**Simple page (navigate to a URL):**

```typescript
// tests/page-objects/my-feature.po.ts
import { BasePage } from '@nop-entropy/e2e-shared';
import type { Page } from '@playwright/test';

export class MyFeaturePO extends BasePage {
  override get entityName(): string {
    return 'MyEntity';  // navigates to /#/MyEntity-main
  }
}
```

**CRUD page (with add/edit/delete/view helpers):**

```typescript
// tests/page-objects/user.po.ts
import { AmisCrudPage, fillModalField, readModalField } from '@nop-entropy/e2e-shared';
import type { Page } from '@playwright/test';

export class UserPO extends AmisCrudPage {
  override get entityName(): string { return 'NopAuthUser'; }

  async fillAddForm(data: { userName: string; nickName: string }) {
    await fillModalField(this.page, 'userName', data.userName);
    await fillModalField(this.page, 'nickName', data.nickName);
  }
}
```

`AmisCrudPage` provides: `search()`, `clickAdd()`, `clickSave()`, `clickView()`, `clickEdit()`, `clickDelete()`.

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

Browser tests automate real UI interactions. Always login first.

```typescript
import { test, expect } from '@playwright/test';
import { loginRpc, rpc } from '@nop-entropy/e2e-shared';
import { LoginPO } from './page-objects/login.po.js';
import { UserPO } from './page-objects/user.po.js';

test.describe('User Management - Browser', () => {
  // Setup test data via RPC
  let testUserId: string;
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    const resp = await rpc(request, 'NopAuthUser__save', {
      data: { userName: 'e2e_test', nickName: 'Test', password: 'Test@123', status: 1, userType: 1, gender: 1 },
    });
    testUserId = resp.data.id;
  });

  test.afterAll(async ({ request }) => {
    await rpc(request, 'NopAuthUser__delete', { id: testUserId }).catch(() => {});
  });

  test('search user in browser', async ({ page }) => {
    // Login
    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login('nop', '123');

    // Navigate and search
    const userPO = new UserPO(page);
    await userPO.goto();
    await userPO.searchUser('e2e_test');

    // Verify
    await userPO.assertUserExists('e2e_test');
  });
});
```

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

#### `rpc<T>(request, operation, params?): Promise<RpcResponse<T>>`

Send an authenticated RPC request to `POST /r/{operation}`.

| Param | Type | Description |
|-------|------|-------------|
| request | `APIRequestContext` | Playwright request fixture |
| operation | `string` | Operation name, e.g. `'NopAuthUser__findPage'` |
| params | `Record<string, unknown>` | Request body parameters |

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

### Page Objects

#### `LoginPage`

Login page at `/`. Methods:

| Method | Description |
|--------|-------------|
| `goto()` | Navigate to `/` and wait for load |
| `login(username?, password?)` | Fill credentials and submit. Waits for redirect away from login. |
| `assertLoggedIn()` | Assert URL does not contain `#/login` |

#### `BasePage` (abstract)

Base class for entity pages. Subclasses must implement `entityName`.

| Method | Description |
|--------|-------------|
| `entityName` (abstract getter) | Entity name for URL construction |
| `goto()` | Navigate to `/#/{entityName}-main` and wait for table |

#### `AmisCrudPage` (extends BasePage)

Full CRUD page object with AMIS UI helpers.

| Method | Description |
|--------|-------------|
| `search(fieldName, value)` | Fill filter field + click search + wait for table |
| `clickAdd()` | Click "新增" button + wait for modal |
| `clickSave()` | Click "确认" button + wait for modal close + table refresh |
| `clickView(rowId)` | Click "查看" row action + wait for modal |
| `clickEdit(rowId)` | Click "编辑" row action + wait for modal |
| `clickDelete(rowId)` | Click "删除" row action + confirm dialog + wait for table |

### Helper Functions

#### Form Helpers

| Function | Description |
|----------|-------------|
| `fillField(page, fieldName, value, options?)` | Fill a form input. Set `options.inFilter: true` for query/filter fields (adds `filter_` prefix). |
| `readField(page, fieldName)` | Read input value from a form field |
| `selectOption(page, fieldName, optionLabel)` | Select a dropdown option by label text |

#### Modal/Drawer Helpers

| Function | Description |
|----------|-------------|
| `waitForModal(page)` | Wait for AMIS modal to appear |
| `waitForDrawer(page)` | Wait for AMIS drawer to appear |
| `fillModalField(page, fieldName, value)` | Fill an input inside the topmost modal/drawer |
| `readModalField(page, fieldName)` | Read a field value from the topmost modal/drawer. Handles both editable inputs and static display fields. |

#### Table Helpers

| Function | Description |
|----------|-------------|
| `waitForTableLoad(page)` | Wait for spinner to disappear and table rows to render |
| `navigateToEntity(page, entityName)` | Navigate to `/#/{entityName}-main` |
| `readTableCell(page, rowIdentifier, columnName)` | Read a cell value by row text and column name (uses `FIELD_TO_HEADER` mapping) |
| `getTableRowCount(page)` | Count data rows in the current table |

#### Button Helpers

| Function | Description |
|----------|-------------|
| `clickByLabel(page, label)` | Click a button by label. Falls back to expanding "更多" dropdown if button is hidden. |
| `clickInRow(page, rowText, label)` | Click a button inside a specific table row. Falls back to "更多" dropdown. |
| `confirmDialog(page)` | Click the confirm button in an AMIS dialog |
| ~~`clickButton(page, label)`~~ | Deprecated. Use `clickByLabel`. |
| ~~`clickRowAction(page, cellValue, actionLabel)`~~ | Deprecated. Use `clickInRow`. |

### AMIS CSS Selectors

Available as `AMIS` constant:

```typescript
import { AMIS } from '@nop-entropy/e2e-shared';

AMIS.MODAL            // '.cxd-Modal'
AMIS.DRAWER           // '.cxd-Drawer'
AMIS.TABLE            // '.cxd-Table'
AMIS.CRUD             // '.cxd-Crud'
AMIS.BUTTON           // '.cxd-Button'
AMIS.SPINNER          // '.cxd-Spinner'
AMIS.FORM_ITEM        // '.cxd-Form-item'
AMIS.CONFIRM_BTN      // '.cxd-Modal--confirm .cxd-Button--primary'
```

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
        command: `./mvnw quarkus:dev -Dquarkus.http.port=${port} -Dquarkus.profile=dev`,
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
"test:wf": "pnpm --filter nop-wf-e2e test"
```

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
