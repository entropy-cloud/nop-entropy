import { test, expect } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';
import { ExtensionHostPO } from './page-objects/extension-host.po.js';

const EXTENSION_ID = 'example-extension-demo';
const EXTENSION_NAME = 'Harbor Operations Suite';
const EXTENSION_VERSION = '0.0.1';
const EXTENSION_BUILTIN_ROUTE = '/examples/extension-harbor';

function host(page: Page, request: APIRequestContext): ExtensionHostPO {
  return new ExtensionHostPO(page, request);
}

test.describe('IndexHtmlProvider extension 注入（生产契约）', () => {
  test('服务端 index.html 携带 data-nop-extension 锚点与扩展资源地址', async ({ page, request }) => {
    await host(page, request).assertInjectedIntoIndexHtml(EXTENSION_ID);
  });

  test('扩展静态资源经 HTTP 可达', async ({ page, request }) => {
    const extension = host(page, request);

    await extension.assertEntryAssetReachable(EXTENSION_ID);

    const manifest = await extension.readManifest(EXTENSION_ID);
    expect(manifest.id).toBe(EXTENSION_ID);
    expect(manifest.name).toBe(EXTENSION_NAME);
    expect(manifest.version).toBe(EXTENSION_VERSION);
    expect(manifest.entry).toBe('./assets/index.js');
    expect(manifest.styleAssets).toBeDefined();
    expect(manifest.styleAssets!.length).toBeGreaterThan(0);
    expect(manifest.assets).toBeDefined();
    expect(manifest.assets!.length).toBeGreaterThan(0);
  });

  test('宿主前端加载并应用扩展入口（内置页可用）', async ({ page, request }) => {
    const extension = host(page, request);

    // 登录 host（Quarkus 后端真实登录 nop/123）
    await extension.signIn();

    await extension.openBuiltinPage(EXTENSION_BUILTIN_ROUTE);
  });
});
