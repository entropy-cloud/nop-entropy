import path from 'node:path';
import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login, rpc, loginRpc } from '@nop-entropy/e2e-shared';
import { TypeHierarchyPO } from './page-objects/type-hierarchy.po.js';

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

test.describe('类型层级查询', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await ensureIndexPrepared(request);
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

  test('浏览器: 登录后导航到类型层级页面', async ({ page, engine }) => {
    await login(page);

    const hierarchyPO = new TypeHierarchyPO(page, engine);
    await hierarchyPO.open();
  });
});
