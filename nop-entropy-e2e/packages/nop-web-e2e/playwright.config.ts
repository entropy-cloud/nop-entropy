import { defineConfig, devices } from '@playwright/test';

const backendPort = parseInt(process.env.PORT || '8083', 10);
const backendTimeout = parseInt(process.env.BACKEND_TIMEOUT || '180000', 10);
const frontendDevMode = process.env.FRONTEND_DEV_MODE === 'true';
const frontendPort = parseInt(process.env.FRONTEND_PORT || '4173', 10);
const nopChaosNextDir = process.env.NOP_CHAOS_NEXT_DIR || '../../../nop-chaos-next';
const extensionNames = process.env.NOP_INDEX_EXTENSION_NAMES || 'example-extension-demo';

const explicitBaseUrl = process.env.BASE_URL;
const baseURL = explicitBaseUrl ?? (
  frontendDevMode
    ? `http://localhost:${frontendPort}`
    : `http://localhost:${backendPort}`
);

export default defineConfig({
  testDir: 'tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 120_000,
  expect: { timeout: 15_000 },

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
  },

  webServer: (() => {
    const servers: {
      command: string;
      cwd: string;
      port: number;
      timeout: number;
      reuseExistingServer: boolean;
      stdout?: 'pipe' | 'ignore';
      stderr?: 'pipe' | 'ignore';
    }[] = [];

    if (!process.env.SKIP_WEBSERVER) {
      servers.push({
        command: `mvn quarkus:dev -Dquarkus.http.port=${backendPort} -Dquarkus.profile=dev -Dnop.web.index-extension-names=${extensionNames}`,
        cwd: '../../../nop-demo/nop-quarkus-demo',
        port: backendPort,
        timeout: backendTimeout,
        reuseExistingServer: !process.env.CI,
        stdout: 'pipe',
        stderr: 'pipe',
      });
    }

    return servers.length > 0 ? servers : undefined;
  })(),

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});