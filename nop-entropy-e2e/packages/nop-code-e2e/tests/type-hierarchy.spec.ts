import path from 'node:path';
import { test } from '@nop-entropy/e2e-shared';
import { expect, type APIRequestContext } from '@playwright/test';
import { login, rpc, loginRpc } from '@nop-entropy/e2e-shared';
import { TypeHierarchyPO } from './page-objects/type-hierarchy.po.js';

const INDEX_ID = 'nop-code-e2e';
// 仓库根为 nop-entropy-e2e 的上两级（tests → 包 → packages → 仓根），不要多上一级——
// 多上一级会解析到不存在的路径，triggerFullIndex 静默索引 0 文件、就绪轮询永远为 0
const TEST_PROJECT_DIR = path.resolve(import.meta.dirname, '../../../../nop-code/nop-code-service');

// dev 后端用内存 H2，每次启动都是空库——索引必须在测试内自举，而不是要求外部预建
async function ensureIndexPrepared(request: APIRequestContext): Promise<void> {
  const existing = await rpc<{ total: number }>(request, 'NopCodeSymbol__searchSymbols', {
    indexId: INDEX_ID, offset: 0, limit: 1,
  });
  if (existing.ok && (existing.data?.total ?? 0) > 0) return;

  // triggerFullIndex 是同步全量构建（非异步），nop-code-service 目录实测 >10s——
  // 必须放宽 rpc 超时，否则请求被 10s 缺省超时打断、索引半途而废
  const build = await rpc(request, 'NopCodeIndex__triggerFullIndex', {
    indexId: INDEX_ID,
    projectPath: TEST_PROJECT_DIR,
  }, 300_000);
  if (!build.ok) {
    throw new Error(`triggerFullIndex failed: ${JSON.stringify(build.errors)}`);
  }

  // 构建完成后轮询符号可见（orm 提交与查询可见性存在亚秒级延迟，上限 150s）
  for (let i = 0; i < 50; i++) {
    await new Promise((r) => setTimeout(r, 3000));
    const prepared = await rpc<{ total: number }>(request, 'NopCodeSymbol__searchSymbols', {
      indexId: INDEX_ID, offset: 0, limit: 1,
    });
    if (prepared.ok && (prepared.data?.total ?? 0) > 0) return;
    if (i % 10 === 9) console.log(`index warming: ${((i + 1) * 3)}s, total=${prepared.data?.total}`);
  }
  throw new Error('Index still empty 150s after triggerFullIndex');
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

  test('浏览器: 打开类型层级页面', async ({ page, engine }) => {
    await login(page);

    const hierarchyPO = new TypeHierarchyPO(page, engine);
    await hierarchyPO.open();
  });
});
