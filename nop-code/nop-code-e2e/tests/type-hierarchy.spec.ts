import path from 'node:path';
import { test, expect } from '@playwright/test';
import { rpc } from './helpers/graphql.js';

const INDEX_ID = 'test-e2e';
const TEST_PROJECT_DIR = path.resolve(import.meta.dirname, '../../../nop-code/nop-code-service');

async function login(page) {
  await page.goto('/');
  await page.waitForLoadState('networkidle');
  await page.locator('input[placeholder="账号"]').fill('nop');
  await page.locator('input[placeholder="密码"]').fill('123');
  await page.getByRole('button', { name: '登 录' }).click();
  await page.waitForURL('**/dashboard-main', { timeout: 10000 });
}

test.describe('类型层级查询', () => {
  test.beforeAll(async ({ request }) => {
    const resp = await rpc(request, 'NopCodeIndex__indexDirectory', {
      indexId: INDEX_ID,
      directoryPath: TEST_PROJECT_DIR,
      filePattern: '**/*.java',
    });
    expect(resp.status).toBe(200);
  });

  test('RPC: 查询 super 方向类型层级', async ({ request }) => {
    const resp = await rpc<{ symbol: { name: string; qualifiedName: string }; superTypes: unknown[] }>(
      request,
      'NopCodeTypeHierarchy__get',
      {
        indexId: INDEX_ID,
        qualifiedName: 'io.nop.code.service.impl.CodeIndexService',
        direction: 'super',
        maxDepth: 3,
      },
    );

    expect(resp.ok).toBeTruthy();
    expect(resp.data.symbol.name).toBe('CodeIndexService');
    expect(resp.data.superTypes).toBeDefined();
  });

  test('RPC: 查询 sub 方向类型层级', async ({ request }) => {
    const resp = await rpc<{ symbol: { name: string }; subTypes: unknown[] }>(
      request,
      'NopCodeTypeHierarchy__get',
      {
        indexId: INDEX_ID,
        qualifiedName: 'io.nop.code.service.api.ICodeIndexService',
        direction: 'sub',
        maxDepth: 2,
      },
    );

    expect(resp.ok).toBeTruthy();
    expect(resp.data.symbol.name).toBe('ICodeIndexService');
    expect(resp.data.subTypes).toBeDefined();
  });

  test('RPC: 查询不存在的类型正常返回', async ({ request }) => {
    const resp = await rpc(request, 'NopCodeTypeHierarchy__get', {
      indexId: INDEX_ID,
      qualifiedName: 'com.nonexistent.FooBar',
      direction: 'both',
      maxDepth: 5,
    });
    expect(resp.status).toBe(200);
  });

  test('浏览器: 登录后导航到类型层级页面', async ({ page }) => {
    await login(page);

    await page.goto('/#/type-hierarchy-main');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('text=类型层级查询')).toBeVisible({ timeout: 10000 });
  });

  test('浏览器: 填写表单并提交查询', async ({ page }) => {
    await login(page);

    await page.goto('/#/type-hierarchy-main');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('text=类型层级查询')).toBeVisible({ timeout: 10000 });

    await page.locator('input[name="indexId"]').fill(INDEX_ID);
    await page.locator('input[name="qualifiedName"]').fill(
      'io.nop.code.service.impl.CodeIndexService',
    );
    await page.locator('input[name="direction"]').fill('super');
    await page.locator('input[name="maxDepth"]').fill('3');

    const responsePromise = page.waitForResponse(
      (resp) => resp.url().includes('/graphql') && resp.request().postData()?.includes('NopCodeTypeHierarchy'),
    );
    await page.getByRole('button', { name: '提交' }).click();
    await responsePromise;
  });
});
