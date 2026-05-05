import path from 'node:path';
import { test, expect } from '@playwright/test';
import { rpc, loginRpc } from './helpers/graphql.js';

const INDEX_ID = 'test-symbol-e2e';
const TEST_PROJECT_DIR = path.resolve(import.meta.dirname, '../../../nop-code/nop-code-service');

async function login(page) {
  await page.goto('/');
  await page.waitForLoadState('networkidle');
  await page.locator('input[placeholder="账号"]').fill('nop');
  await page.locator('input[placeholder="密码"]').fill('123');
  await page.getByRole('button', { name: '登 录' }).click();
  await page.waitForURL('**/dashboard-main', { timeout: 10000 });
}

test.describe('符号搜索', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    const resp = await rpc(request, 'NopCodeIndex__indexDirectory', {
      indexId: INDEX_ID,
      directoryPath: TEST_PROJECT_DIR,
      filePattern: '**/*.java',
    });
    expect(resp.ok).toBeTruthy();
  });

  test('RPC: 分页查询符号', async ({ request }) => {
    const resp = await rpc<{
      total: number;
      items: { name: string; kind: string }[];
    }>(request, 'NopCodeSymbol__findSymbols', {
      indexId: INDEX_ID,
      query: 'CodeIndex',
      offset: 0,
      limit: 10,
    });
    expect(resp.ok).toBeTruthy();
    expect(resp.data.total).toBeGreaterThan(0);
    expect(resp.data.items.length).toBeGreaterThan(0);
    expect(resp.data.items[0].name).toContain('CodeIndex');
  });

  test('RPC: 分页查询第二页', async ({ request }) => {
    const resp = await rpc<{
      total: number;
      items: { name: string }[];
    }>(request, 'NopCodeSymbol__findSymbols', {
      indexId: INDEX_ID,
      offset: 1,
      limit: 1,
    });
    expect(resp.ok).toBeTruthy();
    expect(resp.data.total).toBeGreaterThan(1);
    expect(resp.data.items.length).toBe(1);
  });

  test('浏览器: 导航到符号搜索页面', async ({ page }) => {
    await login(page);

    await page.goto(`/#/NopCodeSymbol-main?indexId=${INDEX_ID}`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10000 });
  });

  test('浏览器: 输入关键词搜索并验证表格结果', async ({ page }) => {
    await login(page);

    await page.goto(`/#/NopCodeSymbol-main?indexId=${INDEX_ID}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10000 });

    const queryInput = page.locator('input[name="filter_query"]');
    await queryInput.fill('CodeIndexService');

    const responsePromise = page.waitForResponse(
      (resp) =>
        resp.url().includes('/graphql') &&
        resp.request().postData()?.includes('findSymbols'),
    );
    await page.getByRole('button', { name: '查询' }).click();
    const response = await responsePromise;

    const json = await response.json();
    const result = json?.data?.NopCodeSymbol__findSymbols;
    expect(result).toBeDefined();
    expect(result.total).toBeGreaterThan(0);
    expect(result.items.length).toBeGreaterThan(0);

    await expect(page.locator('td').first()).toBeVisible({ timeout: 5000 });
  });

  test('浏览器: 查看符号详情', async ({ page }) => {
    await login(page);

    await page.goto(`/#/NopCodeSymbol-main?indexId=${INDEX_ID}`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10000 });

    // search first to load data
    const queryInput = page.locator('input[name="filter_query"]');
    await queryInput.fill('CodeIndexService');

    const searchResp = page.waitForResponse(
      (resp) =>
        resp.url().includes('/graphql') &&
        resp.request().postData()?.includes('findSymbols'),
    );
    await page.getByRole('button', { name: '查询' }).click();
    await searchResp;

    await expect(page.locator('td').first()).toBeVisible({ timeout: 10000 });

    const viewButton = page.locator('button:has-text("查看详情"), a:has-text("查看详情")').first();
    await viewButton.click();

    await expect(page.locator('text=符号详情').first()).toBeVisible({ timeout: 10000 });
  });
});
