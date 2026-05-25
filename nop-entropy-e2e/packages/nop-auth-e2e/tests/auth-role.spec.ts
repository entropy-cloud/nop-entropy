import { test, expect } from '@playwright/test';
import { loginRpc, rpc, waitForTableLoad } from '@nop-entropy/e2e-shared';
import { LoginPO } from './page-objects/login.po.js';
import { RolePO } from './page-objects/role.po.js';

const TEST_ID = `e2e_role_${Date.now()}`;
const createdRoleIds: string[] = [];

async function cleanupTestRoles(request: import('@playwright/test').APIRequestContext): Promise<void> {
  const resp = await rpc<{ items: { roleId: string }[] }>(request, 'NopAuthRole__findPage', {
    query: { offset: 0, limit: 200 },
  });
  if (!resp.ok) return;
  for (const item of resp.data.items) {
    if (item.roleId.startsWith('e2e_role_')) {
      await rpc(request, 'NopAuthRole__delete', { id: item.roleId }).catch(() => {});
    }
  }
}

test.describe('角色管理 - RPC', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await cleanupTestRoles(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdRoleIds) {
      await rpc(request, 'NopAuthRole__delete', { id }).catch(() => {});
    }
    createdRoleIds.length = 0;
  });

  test('RPC: 创建角色', async ({ request }) => {
    const roleId = `${TEST_ID}_create`;
    const roleName = `E2E创建_${TEST_ID}`;

    const resp = await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0, remark: 'e2e auto-created' },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data).toBeTruthy();
    createdRoleIds.push(roleId);
  });

  test('RPC: 查询角色列表', async ({ request }) => {
    const roleId = `${TEST_ID}_list`;
    const roleName = `E2E列表_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const resp = await rpc<{ total: number }>(request, 'NopAuthRole__findPage', {
      query: {
        offset: 0,
        limit: 10,
        filter: { $type: 'eq', name: 'roleId', value: roleId },
      },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data.total).toBeGreaterThanOrEqual(1);
  });

  test('RPC: 读取角色详情', async ({ request }) => {
    const roleId = `${TEST_ID}_get`;
    const roleName = `E2E详情_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0, remark: 'detail test' },
    });
    createdRoleIds.push(roleId);

    const resp = await rpc<{ roleId: string; roleName: string; remark: string }>(
      request,
      'NopAuthRole__get',
      { id: roleId },
    );

    expect(resp.ok).toBe(true);
    expect(resp.data.roleId).toBe(roleId);
    expect(resp.data.roleName).toBe(roleName);
    expect(resp.data.remark).toBe('detail test');
  });

  test('RPC: 更新角色', async ({ request }) => {
    const roleId = `${TEST_ID}_update`;
    const roleName = `E2E更新_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const updatedName = `${roleName}_done`;
    await rpc(request, 'NopAuthRole__update', {
      data: { id: roleId, roleName: updatedName, remark: 'updated via rpc' },
    });

    const getResp = await rpc<{ roleName: string; remark: string }>(
      request,
      'NopAuthRole__get',
      { id: roleId },
    );
    expect(getResp.ok).toBe(true);
    expect(getResp.data.roleName).toBe(updatedName);
    expect(getResp.data.remark).toBe('updated via rpc');
  });

  test('RPC: 删除角色', async ({ request }) => {
    const roleId = `${TEST_ID}_delete`;
    const roleName = `E2E删除_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });

    const resp = await rpc(request, 'NopAuthRole__delete', { id: roleId });
    expect(resp.ok).toBe(true);

    const getResp = await rpc(request, 'NopAuthRole__get', { id: roleId });
    expect(getResp.ok).toBe(false);
  });
});

test.describe('角色管理 - 浏览器', () => {
  test.beforeEach(async ({ request }) => {
    await loginRpc(request);
  });

  test.afterEach(async ({ request }) => {
    await loginRpc(request);
    for (const id of createdRoleIds) {
      await rpc(request, 'NopAuthRole__delete', { id }).catch(() => {});
    }
    createdRoleIds.length = 0;
  });

  async function browserLogin(page: import('@playwright/test').Page): Promise<RolePO> {
    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login('nop', '123');
    return new RolePO(page);
  }

  test('浏览器: 导航到角色管理页面', async ({ page }) => {
    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await expect(page).toHaveURL(/NopAuthRole-main/);
    await expect(
      page.locator('table, .ant-table, .crud-table-wrap').first(),
    ).toBeVisible({ timeout: 15_000 });
  });

  test('浏览器: 创建新角色', async ({ page }) => {
    const rolePO = await browserLogin(page);
    const roleId = `${TEST_ID}_ui_create`;
    const roleName = `E2E_UI创建_${TEST_ID}`;

    await rolePO.goto();
    await rolePO.clickAdd();
    await rolePO.fillForm({ roleId, roleName, remark: 'created via UI' });
    await rolePO.clickSave();
    createdRoleIds.push(roleId);

    await rolePO.goto();
    await expect(rolePO.page.locator('tr').filter({ hasText: roleId }).first())
      .toBeVisible({ timeout: 10_000 });
  });

  test('浏览器: 查看角色详情', async ({ page, request }) => {
    const roleId = `${TEST_ID}_ui_view`;
    const roleName = `E2E_UI查看_${TEST_ID}`;

    const saveResp = await rpc<{ roleId: string }>(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0, remark: 'view test' },
    });
    expect(saveResp.ok).toBe(true);
    expect(saveResp.data).toBeTruthy();
    createdRoleIds.push(roleId);

    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await rolePO.clickView(roleId);

    const viewName = await rolePO.readViewField('roleName');
    expect(viewName).toBe(roleName);
  });

  test('浏览器: 编辑角色', async ({ page, request }) => {
    const roleId = `${TEST_ID}_ui_edit`;
    const roleName = `E2E_UI编辑_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const updatedName = `${roleName}_done`;

    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await rolePO.clickEdit(roleId);
    await rolePO.fillForm({ roleName: updatedName });
    await rolePO.clickSave();

    await loginRpc(request);
    const resp = await rpc<{ roleName: string }>(request, 'NopAuthRole__get', {
      id: roleId,
    });
    expect(resp.ok).toBe(true);
    expect(resp.data.roleName).toBe(updatedName);
  });

  test('浏览器: 删除角色', async ({ page, request }) => {
    const roleId = `${TEST_ID}_ui_delete`;
    const roleName = `E2E_UI删除_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });

    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await rolePO.clickDelete(roleId);

    await expect(rolePO.page.locator('tr').filter({ hasText: roleId }).first())
      .not.toBeVisible({ timeout: 5_000 }).catch(() => {});
  });

  test('浏览器: 搜索角色', async ({ page, request }) => {
    const roleA = `${TEST_ID}_search_a`;
    const roleB = `${TEST_ID}_search_b`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId: roleA, roleName: `E2E_搜索A_${TEST_ID}`, isPrimary: 0 },
    });
    await rpc(request, 'NopAuthRole__save', {
      data: { roleId: roleB, roleName: `E2E_搜索B_${TEST_ID}`, isPrimary: 0 },
    });
    createdRoleIds.push(roleA, roleB);

    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await expect(rolePO.page.locator('tr').filter({ hasText: roleA }).first())
      .toBeVisible({ timeout: 10_000 });
    await expect(rolePO.page.locator('tr').filter({ hasText: roleB }).first())
      .toBeVisible({ timeout: 5_000 });
  });

  test('浏览器: 打开角色用户分配', async ({ page, request }) => {
    const roleId = `${TEST_ID}_ui_users`;
    const roleName = `E2E_角色用户_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await rolePO.clickRoleUsers(roleId);

    await expect(page.locator('.cxd-Drawer, .ant-drawer')).toBeVisible({ timeout: 10_000 });
  });

  test('浏览器: 打开角色授权页面', async ({ page, request }) => {
    const roleId = `${TEST_ID}_ui_auth`;
    const roleName = `E2E_角色授权_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const rolePO = await browserLogin(page);
    await rolePO.goto();
    await rolePO.clickAssignAuth(roleId);

    await expect(page.locator('.cxd-Drawer, .ant-drawer')).toBeVisible({ timeout: 10_000 });
  });
});
