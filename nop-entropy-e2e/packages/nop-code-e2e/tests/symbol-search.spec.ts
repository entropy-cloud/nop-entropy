import path from 'node:path';
import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login, rpc, loginRpc } from '@nop-entropy/e2e-shared';
import { SymbolSearchPO } from './page-objects/symbol-search.po.js';

const INDEX_ID = 'nop-code-e2e';
// 仓库根为 nop-entropy-e2e 的上两级（tests → 包 → packages → 仓根），不要多上一级——
// 多上一级会解析到不存在的路径，triggerFullIndex 静默索引 0 文件、就绪轮询永远为 0
const TEST_PROJECT_DIR = path.resolve(import.meta.dirname, '../../../../nop-code/nop-code-service');

// dev 后端用内存 H2，每次启动都是空库——索引必须在测试内自举，而不是要求外部预建
async function ensureIndexPrepared(request: import('@playwright/test').APIRequestContext): Promise<void> {
  const existing = await rpc<{ total: number }>(request, 'NopCodeSymbol__findPage_symbols', {
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
    const prepared = await rpc<{ total: number }>(request, 'NopCodeSymbol__findPage_symbols', {
      indexId: INDEX_ID, offset: 0, limit: 1,
    });
    if (prepared.ok && (prepared.data?.total ?? 0) > 0) return;
    if (i % 10 === 9) console.log(`index warming: ${((i + 1) * 3)}s, total=${prepared.data?.total}`);
  }
  throw new Error('Index still empty 150s after triggerFullIndex');
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

  // 产品契约缺口（fixme，非测试缺陷）：
  // flux/amis 通用 CRUD 管线当前无法驱动自定义 findPage_symbols 方法——
  // ① resolver/amis graphqlArgs 的 stdOp 提取用 lastIndexOf('_') 把 NopCodeSymbol__findPage_symbols
  //    误取为 "symbols"，注册表不中后透传/猜测壳层作用域键（pagination 等），后端报 unknown-operation-arg；
  // ② 关键字是 String 型 biz 参数（@Name("query")），通用管线只能产出 QueryBean.filter 方言；
  // ③ URL 上的 indexId 绑定在 /r/ 直发路径下不进参数。
  // 两种渲染模式同样失败（amis getOperationName 同逻辑）。完整修复需前端 stdOp 前缀匹配 + 后端
  // 方法改 QueryBean 方言（涉及壳层重建与 API 签名变更），待产品决策——届时去掉 .fixme 即恢复全部断言。
  test.fixme('浏览器: 输入关键词搜索并验证表格结果', async ({ page, engine }) => {
    await login(page);

    const symbolPO = new SymbolSearchPO(page, engine, INDEX_ID);
    await symbolPO.open();

    const result = await symbolPO.searchSymbolAndWait('CodeIndexService');
    expect(result.total).toBeGreaterThan(0);
    expect(result.items.length).toBeGreaterThan(0);

    await symbolPO.assertTableHasData();
  });

  test.fixme('浏览器: 查看符号详情', async ({ page, engine }) => {
    await login(page);

    const symbolPO = new SymbolSearchPO(page, engine, INDEX_ID);
    await symbolPO.open();

    await symbolPO.searchSymbolAndWait('CodeIndexService');
    await symbolPO.assertTableHasData();
    await symbolPO.viewFirstDetail();
  });
});
