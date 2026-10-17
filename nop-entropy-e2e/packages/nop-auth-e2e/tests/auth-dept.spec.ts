import { test } from '@nop-entropy/e2e-shared';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';
import { DeptPO } from './page-objects/dept.po.js';

const TEST_ID = `e2e_dept_${Date.now()}`;

interface DeptDetail {
  deptId: string;
  deptName: string;
  parentId?: string;
}

async function cleanupTestDepts(request: APIRequestContext): Promise<void> {
  const resp = await rpc<{ items: { deptId: string }[] }>(request, 'NopAuthDept__findPage', {
    query: { offset: 0, limit: 200 },
  });
  if (!resp.ok) return;
  for (const item of resp.data.items) {
    if (item.deptId.startsWith('e2e_dept_')) {
      await rpc(request, 'NopAuthDept__delete', { id: item.deptId }).catch(() => {});
    }
  }
}

async function openDeptTabs(page: Page, engine: EngineAdapter): Promise<DeptPO> {
  await login(page, { username: 'nop', password: '123' });
  const deptPO = new DeptPO(page, engine);
  await deptPO.openTabs();
  return deptPO;
}

test.describe('部门 - 标签页与新建', () => {
  test.beforeEach(async ({ request }) => {
    await loginRpc(request);
    await cleanupTestDepts(request);
  });

  test('浏览器: 标签页切换', async ({ page, engine }) => {
    const deptPO = await openDeptTabs(page, engine);

    await expect(page.locator('[data-slot="tabs-trigger"]').filter({ hasText: '列表视图' })).toBeVisible();

    await deptPO.switchTab('列表视图');
    await expect(
      page.locator('[data-slot="tabs-content"]:not([hidden]) .nop-crud'),
    ).toBeVisible({ timeout: 15_000 });

    await deptPO.switchTab('树形视图');
    await expect(
      page.locator('[data-slot="tabs-content"]:not([hidden]) .nop-crud'),
    ).toBeVisible({ timeout: 15_000 });
  });

  test('浏览器: 新建部门并选择父部门', async ({ request, page, engine }) => {
    await loginRpc(request);

    const parentName = `${TEST_ID}_父`;
    const parentResp = await rpc<DeptDetail>(request, 'NopAuthDept__save', {
      data: { deptName: parentName },
    });
    expect(parentResp.ok).toBeTruthy();

    const deptPO = await openDeptTabs(page, engine);
    const childName = `${TEST_ID}_子`;
    await deptPO.createDept(childName, parentName);

    await loginRpc(request);
    const resp = await rpc<{ items: DeptDetail[] }>(request, 'NopAuthDept__findPage', {
      query: {
        filter: { $type: 'eq', name: 'deptName', value: childName },
      },
    });
    expect(resp.ok).toBeTruthy();
    expect(resp.data.items.length).toBeGreaterThanOrEqual(1);
    expect(resp.data.items[0].parentId).toBe(parentResp.data.deptId);
  });
});
