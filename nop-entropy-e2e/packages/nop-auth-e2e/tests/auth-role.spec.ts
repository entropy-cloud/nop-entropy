import { test } from '@nop-entropy/e2e-shared';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';
import { RolePO } from './page-objects/role.po.js';

const TEST_ID = `e2e_role_${Date.now()}`;
const createdRoleIds: string[] = [];

async function cleanupTestRoles(request: APIRequestContext): Promise<void> {
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

/** 已登录的角色管理页面——spec 只拿业务页面对象，不接触引擎与 DOM。 */
async function openRoleAdmin(page: Page, engine: EngineAdapter): Promise<RolePO> {
  await login(page, { username: 'nop', password: '123' });
  const rolePO = new RolePO(page, engine);
  await rolePO.goto();
  return rolePO;
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

    const resp = await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E创建_${TEST_ID}`, isPrimary: 0, remark: 'e2e auto-created' },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data).toBeTruthy();
    createdRoleIds.push(roleId);
  });

  test('RPC: 查询角色列表', async ({ request }) => {
    const roleId = `${TEST_ID}_list`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E列表_${TEST_ID}`, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const resp = await rpc<{ total: number }>(request, 'NopAuthRole__findPage', {
      query: { offset: 0, limit: 10, filter: { $type: 'eq', name: 'roleId', value: roleId } },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data.total).toBeGreaterThanOrEqual(1);
  });

  test('RPC: 读取角色详情', async ({ request }) => {
    const roleId = `${TEST_ID}_get`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E详情_${TEST_ID}`, isPrimary: 0, remark: 'detail test' },
    });
    createdRoleIds.push(roleId);

    const resp = await rpc<{ roleId: string; roleName: string; remark: string }>(
      request,
      'NopAuthRole__get',
      { id: roleId },
    );

    expect(resp.ok).toBe(true);
    expect(resp.data.roleId).toBe(roleId);
    expect(resp.data.roleName).toBe(`E2E详情_${TEST_ID}`);
    expect(resp.data.remark).toBe('detail test');
  });

  test('RPC: 更新角色', async ({ request }) => {
    const roleId = `${TEST_ID}_update`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E更新_${TEST_ID}`, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const updatedName = `E2E更新_${TEST_ID}_done`;
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

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E删除_${TEST_ID}`, isPrimary: 0 },
    });

    expect((await rpc(request, 'NopAuthRole__delete', { id: roleId })).ok).toBe(true);
    expect((await rpc(request, 'NopAuthRole__get', { id: roleId })).ok).toBe(false);
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

  test('浏览器: 导航到角色管理页面', async ({ page, engine }) => {
    const rolePO = await openRoleAdmin(page, engine);

    await expect(page).toHaveURL(/NopAuthRole-main/);
    await rolePO.assertListVisible();
  });

  test('浏览器: 创建新角色', async ({ page, engine }) => {
    const roleId = `${TEST_ID}_ui_create`;
    const rolePO = await openRoleAdmin(page, engine);

    await rolePO.createRole({
      roleId,
      roleName: `E2E_UI创建_${TEST_ID}`,
      remark: 'created via UI',
    });
    createdRoleIds.push(roleId);

    await rolePO.assertRoleExists(roleId);
  });

  test('浏览器: 查看角色详情', async ({ request, page, engine }) => {
    const roleId = `${TEST_ID}_ui_view`;
    const roleName = `E2E_UI查看_${TEST_ID}`;

    const saveResp = await rpc<{ roleId: string }>(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0, remark: 'view test' },
    });
    expect(saveResp.ok).toBe(true);
    createdRoleIds.push(roleId);

    const rolePO = await openRoleAdmin(page, engine);
    await rolePO.viewRole(roleId);

    expect(await rolePO.readViewField('roleName')).toBe(roleName);
  });

  test('浏览器: 编辑角色', async ({ request, page, engine }) => {
    const roleId = `${TEST_ID}_ui_edit`;
    const roleName = `E2E_UI编辑_${TEST_ID}`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const rolePO = await openRoleAdmin(page, engine);
    const updatedName = `${roleName}_done`;
    await rolePO.editRole(roleId, { roleName: updatedName });

    // 真实验证 UI 编辑持久化：读表格 roleName 列应为更新后的值
    await rolePO.searchRole(roleId);
    expect(await rolePO.readTableField(roleId, 'roleName')).toBe(updatedName);
  });

  test('浏览器: 删除角色', async ({ request, page, engine }) => {
    const roleId = `${TEST_ID}_ui_delete`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E_UI删除_${TEST_ID}`, isPrimary: 0 },
    });

    const rolePO = await openRoleAdmin(page, engine);
    await rolePO.deleteRole(roleId);

    await rolePO.assertRoleNotExists(roleId);
  });

  test('浏览器: 按名称检索角色', async ({ request, page, engine }) => {
    const roleA = `${TEST_ID}_search_a`;
    const roleB = `${TEST_ID}_search_b`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId: roleA, roleName: `E2E_搜索A_${TEST_ID}`, isPrimary: 0 },
    });
    await rpc(request, 'NopAuthRole__save', {
      data: { roleId: roleB, roleName: `E2E_搜索B_${TEST_ID}`, isPrimary: 0 },
    });
    createdRoleIds.push(roleA, roleB);

    const rolePO = await openRoleAdmin(page, engine);

    await rolePO.assertRoleExists(roleA);
    await rolePO.assertRoleExists(roleB);
  });

  test('浏览器: 打开角色用户分配', async ({ request, page, engine }) => {
    const roleId = `${TEST_ID}_ui_users`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E_角色用户_${TEST_ID}`, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const rolePO = await openRoleAdmin(page, engine);
    await rolePO.assignUsers(roleId);

    await rolePO.assertPanelVisible();
  });

  test('浏览器: 打开角色授权页面', async ({ request, page, engine }) => {
    const roleId = `${TEST_ID}_ui_auth`;

    await rpc(request, 'NopAuthRole__save', {
      data: { roleId, roleName: `E2E_角色授权_${TEST_ID}`, isPrimary: 0 },
    });
    createdRoleIds.push(roleId);

    const rolePO = await openRoleAdmin(page, engine);
    await rolePO.assignPermissions(roleId);

    await rolePO.assertPanelVisible();
  });
});
