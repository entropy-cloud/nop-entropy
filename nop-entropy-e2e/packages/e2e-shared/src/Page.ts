import type { Page as PlaywrightPage } from '@playwright/test';
import type { EngineAdapter } from './types';

export abstract class BasePage {
  constructor(
    protected page: PlaywrightPage,
    protected engine: EngineAdapter,
  ) {}

  async goto(hashRoute: string): Promise<void> {
    // 不用 page.goto(hash)：SPA 已加载时对同页 hash 的 goto 会被壳层自身的导航
    // （如登录后重定向）踩掉或静默忽略——直接赋值 location.hash 触发 hashchange，
    // 并校验 hash 生效，未生效重试（壳层登录后重定向与菜单路由注册存在竞态）
    const target = `#/${hashRoute}`;
    for (let i = 0; i < 3; i++) {
      await this.page.evaluate((h) => {
        window.location.hash = h;
      }, target);
      const applied = await this.page
        .waitForFunction((h) => window.location.hash === h, target, { timeout: 3_000 })
        .then(() => true)
        .catch(() => false);
      if (applied) return;
      await this.page.waitForTimeout(600);
    }
    throw new Error(`hash 导航未生效: ${target}（当前 ${this.page.url()}）`);
    await this.page.waitForLoadState('networkidle');
  }
}
