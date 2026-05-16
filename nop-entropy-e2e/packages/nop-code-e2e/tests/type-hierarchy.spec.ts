import path from 'node:path';
import { test, expect } from '@playwright/test';
import { rpc, loginRpc } from '@nop-entropy/e2e-shared';
import { LoginPO } from './page-objects/login.po.js';
import { TypeHierarchyPO } from './page-objects/type-hierarchy.po.js';

const INDEX_ID = 'nop-code-e2e';
const TEST_PROJECT_DIR = path.resolve(import.meta.dirname, '../../../../../nop-code/nop-code-service');

test.describe('类型层级查询', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);

    const existing = await rpc<{ total: number }>(request, 'NopCodeSymbol__findPage_symbols', {
      indexId: INDEX_ID, offset: 0, limit: 1,
    });
    if (existing.ok && (existing.data?.total ?? 0) > 0) return;
    throw new Error('Index not prepared. Run indexDirectory first.');
  });

  test('RPC: 查询 super 方向类型层级', async ({ request }) => {
    const resp = await rpc<{ symbol: { name: string; qualifiedName: string }; superTypes: unknown[] }>(
      request,
      'NopCodeSymbol__getTypeHierarchy',
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
      'NopCodeSymbol__getTypeHierarchy',
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
    const resp = await rpc(request, 'NopCodeSymbol__getTypeHierarchy', {
      indexId: INDEX_ID,
      qualifiedName: 'com.nonexistent.FooBar',
      direction: 'both',
      maxDepth: 5,
    });
    expect(resp.ok).toBeTruthy();
  });

  test('浏览器: 登录后导航到类型层级页面', async ({ page }) => {
    const loginPO = new LoginPO(page);
    await loginPO.goto();
    await loginPO.login();

    const hierarchyPO = new TypeHierarchyPO(page);
    await hierarchyPO.goto();
  });
});
