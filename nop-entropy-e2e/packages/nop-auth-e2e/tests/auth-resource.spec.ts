import { test } from '@nop-entropy/e2e-shared';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';
import { ResourcePO } from './page-objects/resource.po.js';

const TEST_ID = `e2e_res_${Date.now()}`;
const createdResourceIds: string[] = [];
const SITE_ID = 'main';

async function ensureDefaultSite(request: APIRequestContext): Promise<void> {
  await rpc(request, 'NopAuthSite__save', {
    data: { siteId: SITE_ID, displayName: 'Main Site', orderNo: 1, status: 1 },
  });
}

async function cleanupTestResources(request: APIRequestContext): Promise<void> {
  const resp = await rpc<{ items: { resourceId: string }[] }>(request, 'NopAuthResource__findPage', {
    query: { offset: 0, limit: 200 },
  });
  if (!resp.ok) return;
  for (const item of resp.data.items) {
    if (item.resourceId.startsWith('e2e_res_')) {
      await rpc(request, 'NopAuthResource__delete', { id: item.resourceId }).catch(() => {});
    }
  }
}

/** 已登录的资源管理页面——spec 只拿业务页面对象，不接触引擎与 DOM。 */
async function openResourceAdmin(page: Page, engine: EngineAdapter): Promise<ResourcePO> {
  await login(page, { username: 'nop', password: '123' });
  const resourcePO = new ResourcePO(page, engine);
  await resourcePO.goto();
  return resourcePO;
}

/** 资源在 UI 上新建（而不是 RPC），确保它出现在 CRUD 表格里被真实交互覆盖。 */
function resourceFormData(resourceId: string, displayName: string, extra: Record<string, unknown> = {}) {
  return { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1, ...extra };
}

test.describe('资源管理 - RPC', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await ensureDefaultSite(request);
    await cleanupTestResources(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdResourceIds) {
      await rpc(request, 'NopAuthResource__delete', { id }).catch(() => {});
    }
    createdResourceIds.length = 0;
  });

  test('RPC: 创建资源', async ({ request }) => {
    const resourceId = `${TEST_ID}_create`;

    const resp = await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName: 'E2E_创建', resourceType: 'TOPM', orderNo: 9000, status: 1 },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data).toBeTruthy();
    createdResourceIds.push(resourceId);
  });

  test('RPC: 查询资源列表', async ({ request }) => {
    const resourceId = `${TEST_ID}_list`;

    const saveResp = await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName: 'E2E_列表', resourceType: 'TOPM', orderNo: 1, status: 1 },
    });
    expect(saveResp.ok).toBe(true);
    createdResourceIds.push(resourceId);

    const resp = await rpc<{ total: number }>(request, 'NopAuthResource__findPage', {
      query: { offset: 0, limit: 10, filter: { $type: 'eq', name: 'resourceId', value: resourceId } },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data.total).toBeGreaterThanOrEqual(1);
  });

  test('RPC: 读取资源详情', async ({ request }) => {
    const resourceId = `${TEST_ID}_get`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName: 'E2E_详情', resourceType: 'TOPM', orderNo: 1, status: 1, remark: 'detail test' },
    });
    createdResourceIds.push(resourceId);

    const resp = await rpc<{ resourceId: string; displayName: string; remark: string }>(
      request,
      'NopAuthResource__get',
      { id: resourceId },
    );

    expect(resp.ok).toBe(true);
    expect(resp.data.resourceId).toBe(resourceId);
    expect(resp.data.displayName).toBe('E2E_详情');
    expect(resp.data.remark).toBe('detail test');
  });

  test('RPC: 更新资源', async ({ request }) => {
    const resourceId = `${TEST_ID}_update`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName: 'E2E_更新', resourceType: 'TOPM', orderNo: 1, status: 1 },
    });
    createdResourceIds.push(resourceId);

    const updatedName = 'E2E_更新_done';
    await rpc(request, 'NopAuthResource__update', {
      data: { id: resourceId, displayName: updatedName, remark: 'updated via rpc' },
    });

    const getResp = await rpc<{ displayName: string; remark: string }>(
      request,
      'NopAuthResource__get',
      { id: resourceId },
    );
    expect(getResp.ok).toBe(true);
    expect(getResp.data.displayName).toBe(updatedName);
    expect(getResp.data.remark).toBe('updated via rpc');
  });

  test('RPC: 删除资源', async ({ request }) => {
    const resourceId = `${TEST_ID}_delete`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName: 'E2E_删除', resourceType: 'TOPM', orderNo: 1, status: 1 },
    });

    expect((await rpc(request, 'NopAuthResource__delete', { id: resourceId })).ok).toBe(true);
    expect((await rpc(request, 'NopAuthResource__get', { id: resourceId })).ok).toBe(false);
  });
});

test.describe('资源管理 - 浏览器', () => {
  test.beforeEach(async ({ request }) => {
    await loginRpc(request);
    await ensureDefaultSite(request);
  });

  test.afterEach(async ({ request }) => {
    await loginRpc(request);
    for (const id of createdResourceIds) {
      await rpc(request, 'NopAuthResource__delete', { id }).catch(() => {});
    }
    createdResourceIds.length = 0;
  });

  test('浏览器: 导航到资源管理页面', async ({ page, engine }) => {
    const resourcePO = await openResourceAdmin(page, engine);

    await expect(page).toHaveURL(/NopAuthResource-main/);
    await resourcePO.assertListVisible();
  });

  test('浏览器: 创建新资源', async ({ page, engine }) => {
    const resourceId = `${TEST_ID}_ui_create`;
    const resourcePO = await openResourceAdmin(page, engine);

    await resourcePO.createResource(
      resourceFormData(resourceId, `E2E_UI创建_${TEST_ID}`, { orderNo: 9100 }),
    );
    createdResourceIds.push(resourceId);

    await resourcePO.assertResourceExists(resourceId);
  });

  test('浏览器: 查看资源详情', async ({ page, engine }) => {
    const resourceId = `${TEST_ID}_ui_view`;
    const displayName = `E2E_UI查看_${TEST_ID}`;
    const resourcePO = await openResourceAdmin(page, engine);

    await resourcePO.createResource(resourceFormData(resourceId, displayName, { remark: 'view test' }));
    createdResourceIds.push(resourceId);

    await resourcePO.viewResource(resourceId);

    expect(await resourcePO.readViewField('displayName')).toBe(displayName);
  });

  test('浏览器: 编辑资源', async ({ request, page, engine }) => {
    const resourceId = `${TEST_ID}_ui_edit`;
    const displayName = `E2E_UI编辑_${TEST_ID}`;
    const resourcePO = await openResourceAdmin(page, engine);

    await resourcePO.createResource(resourceFormData(resourceId, displayName));
    createdResourceIds.push(resourceId);

    const updatedName = `${displayName}_done`;
    await resourcePO.editResource(resourceId, { displayName: updatedName });

    await loginRpc(request);
    const resp = await rpc<{ displayName: string }>(request, 'NopAuthResource__get', { id: resourceId });
    expect(resp.ok).toBe(true);
    expect(resp.data.displayName).toBe(updatedName);
  });

  test('浏览器: 选择资源图标', async ({ request, page, engine }) => {
    const resourceId = `${TEST_ID}_ui_icon`;
    const resourcePO = await openResourceAdmin(page, engine);

    await resourcePO.createResource(resourceFormData(resourceId, `E2E_UI图标_${TEST_ID}`));
    createdResourceIds.push(resourceId);

    await resourcePO.selectResourceIcon(resourceId, 'settings');

    await loginRpc(request);
    const resp = await rpc<{ icon?: string }>(request, 'NopAuthResource__get', { id: resourceId });
    expect(resp.ok).toBe(true);
    expect(resp.data.icon).toBe('settings');
  });

  test('浏览器: 删除资源', async ({ page, engine }) => {
    const resourceId = `${TEST_ID}_ui_delete`;
    const resourcePO = await openResourceAdmin(page, engine);

    await resourcePO.createResource(
      resourceFormData(resourceId, `E2E_UI删除_${TEST_ID}`),
    );

    await resourcePO.deleteResource(resourceId);

    await resourcePO.assertResourceNotExists(resourceId);
  });
});
