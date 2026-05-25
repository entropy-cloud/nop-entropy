import { defineConfig, devices } from '@playwright/test';

const port = parseInt(process.env.PORT || '8080', 10);
const baseURL = process.env.BASE_URL || `http://localhost:${port}`;

export default defineConfig({
  testDir: 'tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  expect: { timeout: 15_000 },

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    actionTimeout: 10_000,
  },

  webServer: process.env.SKIP_WEBSERVER
    ? undefined
    : {
        command: `./mvnw quarkus:dev -Dquarkus.http.port=${port} -Dquarkus.profile=dev`,
        cwd: '../../../../../nop-auth/nop-auth-app',
        port,
        timeout: 120_000,
        reuseExistingServer: !process.env.CI,
        stdout: 'pipe',
        stderr: 'pipe',
      },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
