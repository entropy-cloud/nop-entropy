import type { APIRequestContext, Page } from '@playwright/test';
import { expect, login } from '@nop-entropy/e2e-shared';

/** Extension 在服务端注入的宿主契约。 */
export interface ExtensionManifest {
  id: string;
  name: string;
  version?: string;
  entry: string;
  styleAssets?: string[];
  assets?: string[];
}

/**
 * Extension 宿主页面对象。
 *
 * 对外只表达"扩展是否被注入 / 静态资源是否可达 / 扩展内置页是否可用"
 * 这三类业务事实，HTML 锚点与路由细节收口在此。
 */
export class ExtensionHostPO {
  constructor(
    private page: Page,
    private request: APIRequestContext,
  ) {}

  /** 登录宿主应用（baseUrl 走 playwright.config 的 use.baseURL，不在此处硬编码端口）。 */
  async signIn(username = 'nop', password = '123'): Promise<void> {
    await login(this.page, { username, password });
  }

  /** 服务端定制 index.html 已发生（占位符被 extension 脚本/样式替换）。 */
  async assertInjectedIntoIndexHtml(extensionId: string): Promise<void> {
    const html = await this.request.get('/');
    expect(html.ok()).toBeTruthy();
    const htmlText = await html.text();

    expect(htmlText).not.toContain('<!--NOP_EXTENSIONS_INJECT-->');
    expect(htmlText).toContain(
      `<script type="module" data-nop-extension data-nop-extension-id="${extensionId}" src="/extensions/${extensionId}/assets/index.js"></script>`,
    );

    const cssAnchors = htmlText.match(
      /<link rel="stylesheet" data-nop-extension data-nop-extension-id="[^"]+"[^>]*>/g,
    );
    expect(cssAnchors).not.toBeNull();
    expect(cssAnchors!.length).toBeGreaterThan(0);
    for (const tag of cssAnchors!) {
      expect(tag).toContain(`data-nop-extension-id="${extensionId}"`);
    }
  }

  /** Extension 清单可读且声明了入口与静态资源。 */
  async readManifest(extensionId: string): Promise<ExtensionManifest> {
    const resp = await this.request.get(`/extensions/${extensionId}/extension.json`);
    expect(resp.ok()).toBeTruthy();
    return (await resp.json()) as ExtensionManifest;
  }

  /** Extension 打包产物（ESM library，含 default export）在 HTTP 上可达。 */
  async assertEntryAssetReachable(extensionId: string): Promise<void> {
    const entryResp = await this.request.get(`/extensions/${extensionId}/assets/index.js`);
    expect(entryResp.ok()).toBeTruthy();
    const entryBody = await entryResp.text();
    expect(entryBody).toContain('export');
    expect(entryBody).toContain('default');
  }

  /** 打开扩展注册的内置页面并确认被真正渲染（而非默认 404）。 */
  async openBuiltinPage(route: string): Promise<void> {
    const { navigateTo } = await import('@nop-entropy/e2e-shared');
    await navigateTo(this.page, route);
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(2000);

    expect(this.page.url()).toContain(route);

    const bodyText = await this.page.locator('body').innerText();
    const isDefault404 = bodyText.includes('404') && bodyText.includes('Page Not Found');
    expect(isDefault404).toBeFalsy();
  }
}
