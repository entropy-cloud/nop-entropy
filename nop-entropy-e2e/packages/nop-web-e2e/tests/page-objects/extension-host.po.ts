import type { APIRequestContext, Page } from '@playwright/test';
import { expect, loginRpc } from '@nop-entropy/e2e-shared';

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

  /**
   * 建立已认证的宿主会话。
   *
   * 本 demo 扩展会覆盖系统登录页（nop-chaos-next `examples/extension-demo`
   * 的 `ExtensionLoginPage`）：该页只有用户名、没有密码字段，提交的是**演示用
   * 假 token**，真实后端在 `LoginApi__getLoginUserInfo` 会以 401 拒绝它，
   * 会话随即被清空。真实后端的测试因此不能走该表单，这里直接用真实登录拿到
   * token，再按宿主 zustand persist（key `auth:v2`，默认落在 sessionStorage）
   * 的形状写入持久化状态，复现宿主登录流程产出的结果。
   */
  async signIn(username = 'nop', password = '123'): Promise<void> {
    await this.page.goto('/');

    const token = await loginRpc(this.request, username, password);
    await this.page.evaluate(
      ({ accessToken, principalId }) => {
        const persisted = JSON.stringify({
          state: {
            user: { id: principalId, username: principalId, nickname: principalId, roles: [] },
            isAuthenticated: true,
            token: accessToken,
            tokens: { accessToken },
          },
          version: 0,
        });
        // 活跃存储由 auth config 决定（默认 sessionStorage），与扩展的登录页一样
        // 两个都写，避免配置差异导致会话丢失
        sessionStorage.setItem('auth:v2', persisted);
        localStorage.setItem('auth:v2', persisted);
      },
      { accessToken: token, principalId: username },
    );

    await this.page.reload();
    await this.page.waitForLoadState('networkidle');
    // 引导完成后应离开登录路由
    await this.page.waitForFunction(
      () => !window.location.hash.startsWith('#/auth/login'),
      undefined,
      { timeout: 20_000 },
    );
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
