import { test, expect } from '@playwright/test';
import { loginRpc, rpc } from '@nop-entropy/e2e-shared';
import { LoginPO } from './page-objects/login.po.js';
import { ResourcePO } from './page-objects/resource.po.js';

const TEST_ID = `e2e_res_${Date.now()}`;
const createdResourceIds: string[] = [];
const SITE_ID = 'main';

async function ensureDefaultSite(request: import('@playwright/test').APIRequestContext): Promise<void> {
  await rpc(request, 'NopAuthSite__save', {
    data: { siteId: SITE_ID, displayName: 'Main Site', orderNo: 1, status: 1 },
  }).catch(() => {});
}

async function cleanupTestResources(request: import('@playwright/test').APIRequestContext): Promise<void> {
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
    const displayName = `E2E_创建`;

    const resp = await rpc(request, 'NopAuthResource__save', {
      data: {
        resourceId,
        siteId: SITE_ID,
        displayName,
        resourceType: 'TOPM',
        orderNo: 9000,
        status: 1,
      },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data).toBeTruthy();
    createdResourceIds.push(resourceId);
  });

  test('RPC: 查询资源列表', async ({ request }) => {
    const resourceId = `${TEST_ID}_list`;
    const displayName = `E2E_列表`;

    const saveResp = await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1 },
    });
    expect(saveResp.ok).toBe(true);
    createdResourceIds.push(resourceId);

    const resp = await rpc<{ total: number }>(request, 'NopAuthResource__findPage', {
      query: {
        offset: 0,
        limit: 10,
        filter: { $type: 'eq', name: 'resourceId', value: resourceId },
      },
    });

    expect(resp.ok).toBe(true);
    expect(resp.data.total).toBeGreaterThanOrEqual(1);
  });

  test('RPC: 读取资源详情', async ({ request }) => {
    const resourceId = `${TEST_ID}_get`;
    const displayName = `E2E_详情`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1, remark: 'detail test' },
    });
    createdResourceIds.push(resourceId);

    const resp = await rpc<{ resourceId: string; displayName: string; remark: string }>(
      request,
      'NopAuthResource__get',
      { id: resourceId },
    );

    expect(resp.ok).toBe(true);
    expect(resp.data.resourceId).toBe(resourceId);
    expect(resp.data.displayName).toBe(displayName);
    expect(resp.data.remark).toBe('detail test');
  });

  test('RPC: 更新资源', async ({ request }) => {
    const resourceId = `${TEST_ID}_update`;
    const displayName = `E2E_更新`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1 },
    });
    createdResourceIds.push(resourceId);

    const updatedName = `${displayName}_done`;
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
    const displayName = `E2E_删除`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1 },
    });

    const resp = await rpc(request, 'NopAuthResource__delete', { id: resourceId });
    expect(resp.ok).toBe(true);

    const getResp = await rpc(request, 'NopAuthResource__get', { id: resourceId });
    expect(getResp.ok).toBe(false);
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

  async function browserLogin(page: import('@playwright/test').Page): Promise<ResourcePO> {
    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login('nop', '123');
    return new ResourcePO(page);
  }

  test('浏览器: 导航到资源管理页面', async ({ page }) => {
    const resourcePO = await browserLogin(page);
    await resourcePO.goto();
    await expect(page).toHaveURL(/NopAuthResource-main/);
    await expect(
      page.locator('table, .ant-table, .crud-table-wrap').first(),
    ).toBeVisible({ timeout: 15_000 });
  });

  test('浏览器: 创建新资源', async ({ page }) => {
    const resourcePO = await browserLogin(page);
    const resourceId = `${TEST_ID}_ui_create`;
    const displayName = `E2E_UI创建_${TEST_ID}`;

    await resourcePO.goto();
    await resourcePO.clickAdd();
    await resourcePO.fillForm({
      resourceId,
      siteId: SITE_ID,
      displayName,
      resourceType: 'TOPM',
      orderNo: 9100,
      status: 1,
    });
    await resourcePO.clickSave();
    createdResourceIds.push(resourceId);

    await expect(resourcePO.page.locator('tr').filter({ hasText: resourceId }).first())
      .toBeVisible({ timeout: 10_000 });
  });

  test('浏览器: 查看资源详情', async ({ page, request }) => {
    const resourceId = `${TEST_ID}_ui_view`;
    const displayName = `E2E_UI查看_${TEST_ID}`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1, remark: 'view test' },
    });
    createdResourceIds.push(resourceId);

    const resourcePO = await browserLogin(page);
    await resourcePO.goto();
    await resourcePO.clickView(resourceId);

    const viewName = await resourcePO.readViewField('displayName');
    expect(viewName).toBe(displayName);
  });

  test('浏览器: 编辑资源', async ({ page, request }) => {
    const resourceId = `${TEST_ID}_ui_edit`;
    const displayName = `E2E_UI编辑_${TEST_ID}`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1 },
    });
    createdResourceIds.push(resourceId);

    const updatedName = `${displayName}_done`;

    const resourcePO = await browserLogin(page);
    await resourcePO.goto();
    await resourcePO.clickEdit(resourceId);
    await resourcePO.fillForm({ displayName: updatedName, resourceType: 'TOPM' });
    await resourcePO.clickSave();

    await loginRpc(request);
    const resp = await rpc<{ displayName: string }>(
      request,
      'NopAuthResource__get',
      { id: resourceId },
    );
    expect(resp.ok).toBe(true);
    expect(resp.data.displayName).toBe(updatedName);
  });

  test('浏览器: 删除资源', async ({ page, request }) => {
    const resourceId = `${TEST_ID}_ui_delete`;
    const displayName = `E2E_UI删除_${TEST_ID}`;

    await rpc(request, 'NopAuthResource__save', {
      data: { resourceId, siteId: SITE_ID, displayName, resourceType: 'TOPM', orderNo: 1, status: 1 },
    });

    const resourcePO = await browserLogin(page);
    await resourcePO.goto();
    await resourcePO.clickDelete(resourceId);

    await expect(resourcePO.page.locator('tr').filter({ hasText: resourceId }).first())
      .not.toBeVisible({ timeout: 5_000 }).catch(() => {});
  });
});
