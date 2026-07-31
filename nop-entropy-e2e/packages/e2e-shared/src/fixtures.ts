import { test as base } from '@playwright/test';
import type { EngineAdapter } from './types';
import { getEngine } from './engine';

type CustomFixtures = {
  engine: EngineAdapter;
};

export const test = base.extend<CustomFixtures>({
  engine: async ({}, use) => {
    await use(getEngine());
  },
  page: async ({ page }, use) => {
    // 默认开启 flux 调试记录器（window.__FLUX_DEBUG__），
    // 所有 flux ajax 请求/响应、monitor 错误、notify 消息都会记录到
    // window.__fluxDebug，测试可通过 dumpFluxDebug(page) 读取。
    // 开关在页面加载前设置，flux env 首次创建时即生效。
    await page.addInitScript(() => {
      (window as any).__FLUX_DEBUG__ = true;
    });

    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });
    await use(page);
    if (errors.length > 0 && process.env.E2E_ASSERT_NO_CONSOLE_ERRORS) {
      throw new Error(`Console errors detected:\n${errors.join('\n')}`);
    }
  },
});
