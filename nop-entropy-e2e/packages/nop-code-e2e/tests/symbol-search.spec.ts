import path from 'node:path';
import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login, rpc, loginRpc } from '@nop-entropy/e2e-shared';
import { SymbolSearchPO } from './page-objects/symbol-search.po.js';

const INDEX_ID = 'nop-code-e2e';
const TEST_PROJECT_DIR = path.resolve(import.meta.dirname, '../../../../../nop-code/nop-code-service');

// dev 后端用内存 H2，每次启动都是空库——索引必须在测试内自举，而不是要求外部预建
async function ensureIndexPrepared(request: import('@playwright/test').APIRequestContext): Promise<void> {
  const existing = await rpc<{ total: number }>(request, 'NopCodeSymbol__findPage_symbols', {
    indexId: INDEX_ID, offset: 0, limit: 1,
  });
  if (existing.ok && (existing.data?.total ?? 0) > 0) return;

  const build = await rpc(request, 'NopCodeIndex__triggerFullIndex', {
    indexId: INDEX_ID,
    projectPath: TEST_PROJECT_DIR,
  });
  if (!build.ok) {
    throw new Error(`triggerFullIndex failed: ${JSON.stringify(build.errors)}`);
  }

  const prepared = await rpc<{ total: number }>(request, 'NopCodeSymbol__findPage_symbols', {
    indexId: INDEX_ID, offset: 0, limit: 1,
  });
  if (!prepared.ok || (prepared.data?.total ?? 0) === 0) {
    throw new Error(`Index still empty after triggerFullIndex: ${JSON.stringify(prepared.errors)}`);
  }
}

test.describe('符号搜索', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await ensureIndexPrepared(request);
  });

  test('RPC: 分页查询符号', async ({ request }) => {
    const resp = await rpc<{
      total: number;
      items: { name: string; kind: string }[];
    }>(request, 'NopCodeSymbol__findPage_symbols', {
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
    }>(request, 'NopCodeSymbol__findPage_symbols', {
      indexId: INDEX_ID,
      offset: 1,
      limit: 1,
    });
    expect(resp.ok).toBeTruthy();
    expect(resp.data.total).toBeGreaterThan(1);
    expect(resp.data.items.length).toBe(1);
  });

  test('浏览器: 导航到符号搜索页面', async ({ page, engine }) => {
    await login(page);

    const symbolPO = new SymbolSearchPO(page, engine, INDEX_ID);
    await symbolPO.open();

    await expect(page.locator('text=名称关键词').first()).toBeVisible({ timeout: 10_000 });
  });

  test('浏览器: 输入关键词搜索并验证表格结果', async ({ page, engine }) => {
    await login(page);

    const symbolPO = new SymbolSearchPO(page, engine, INDEX_ID);
    await symbolPO.open();

    const result = await symbolPO.searchSymbolAndWait('CodeIndexService');
    expect(result.total).toBeGreaterThan(0);
    expect(result.items.length).toBeGreaterThan(0);

    await symbolPO.assertTableHasData();
  });

  test('浏览器: 查看符号详情', async ({ page, engine }) => {
    await login(page);

    const symbolPO = new SymbolSearchPO(page, engine, INDEX_ID);
    await symbolPO.open();

    await symbolPO.searchSymbolAndWait('CodeIndexService');
    await symbolPO.assertTableHasData();
    await symbolPO.viewFirstDetail();
  });
});
