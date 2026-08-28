import { test } from '@playwright/test';
import { expect } from '@playwright/test';
import { login, navigateTo } from '@nop-entropy/e2e-shared';

const EXTENSION_ID = 'example-extension-demo';
const EXTENSION_NAME = 'Harbor Operations Suite';
const EXTENSION_VERSION = '0.0.1';

// playwright.config.ts 的 use.baseURL 指向 Quarkus 后端（如 http://localhost:8083）
const HOST_BASE_URL = process.env.E2E_BASE_URL ?? process.env.BASE_URL ?? 'http://localhost:8083';

test.describe('IndexHtmlProvider extension injection (production contract)', () => {
  test('server-injected HTML carries data-nop-extension anchors and extension URLs', async ({ request }) => {
    // 1. 服务端 index.html 定制发生：<!--NOP_EXTENSIONS_INJECT--> 占位符被替换
    const html = await request.get('/');
    expect(html.ok()).toBeTruthy();
    const htmlText = await html.text();

    expect(htmlText).not.toContain('<!--NOP_EXTENSIONS_INJECT-->');
    expect(htmlText).toContain(
      `<script type="module" data-nop-extension data-nop-extension-id="${EXTENSION_ID}" src="/extensions/${EXTENSION_ID}/assets/index.js"></script>`,
    );

    // 至少有一个 <link rel="stylesheet" data-nop-extension ...> 注入
    const cssAnchors = htmlText.match(/<link rel="stylesheet" data-nop-extension data-nop-extension-id="[^"]+"[^>]*>/g);
    expect(cssAnchors).not.toBeNull();
    expect(cssAnchors!.length).toBeGreaterThan(0);
    for (const tag of cssAnchors!) {
      expect(tag).toContain(`data-nop-extension-id="${EXTENSION_ID}"`);
    }
  });

  test('extension static assets are reachable over HTTP (Spring/Quarkus static mapping)', async ({ request }) => {
    // entry chunk（Quarkus 会自动解 .gz）
    const entryResp = await request.get(`/extensions/${EXTENSION_ID}/assets/index.js`);
    expect(entryResp.ok()).toBeTruthy();
    const entryBody = await entryResp.text();
    // ESM library 交付：包含 default export
    expect(entryBody).toContain('export');
    expect(entryBody).toContain('default');

    // manifest 可访问
    const manifestResp = await request.get(`/extensions/${EXTENSION_ID}/extension.json`);
    expect(manifestResp.ok()).toBeTruthy();
    const manifest = (await manifestResp.json()) as {
      id: string;
      name: string;
      version?: string;
      entry: string;
      styleAssets?: string[];
      assets?: string[];
    };
    expect(manifest.id).toBe(EXTENSION_ID);
    expect(manifest.name).toBe(EXTENSION_NAME);
    expect(manifest.version).toBe(EXTENSION_VERSION);
    expect(manifest.entry).toBe('./assets/index.js');
    expect(manifest.styleAssets).toBeDefined();
    expect(manifest.styleAssets!.length).toBeGreaterThan(0);
    expect(manifest.assets).toBeDefined();
    expect(manifest.assets!.length).toBeGreaterThan(0);
  });

  test('host front-end loads the extension entry and applies it (DOM-scan contract)', async ({ page }) => {
    // 登录 host（Quarkus 后端真实登录 nop/123）
    await login(page, { baseUrl: HOST_BASE_URL });

    // 扩展注册的 builtin page 路由应可访问
    await navigateTo(page, '/examples/extension-harbor');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const urlAfterNav = page.url();
    expect(urlAfterNav).toContain('/examples/extension-harbor');

    // 扩展定制确实发生：builtin page 被渲染，而不是默认 404
    const bodyText = await page.locator('body').innerText();
    const isDefault404 = bodyText.includes('404') && bodyText.includes('Page Not Found');
    expect(isDefault404).toBeFalsy();
  });
});