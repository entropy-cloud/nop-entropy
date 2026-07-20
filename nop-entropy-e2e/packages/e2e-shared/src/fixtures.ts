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
