import { test } from '@nop-entropy/e2e-shared';

test('trace axios login', async ({ page }) => {
  await page.goto('http://localhost:4173/');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);

  // Monkey-patch fetch to trace all calls
  await page.evaluate(() => {
    const origFetch = window.fetch;
    (window as any).__fetchLog__ = [];
    window.fetch = async (input: any, init?: any) => {
      const url = typeof input === 'string' ? input : input?.url;
      const method = init?.method ?? 'GET';
      const entry: any = { url, method, startTime: Date.now() };
      (window as any).__fetchLog__.push(entry);
      try {
        const resp = await origFetch(input, init);
        entry.status = resp.status;
        entry.endTime = Date.now();
        entry.durationMs = entry.endTime - entry.startTime;
        entry.done = true;
        return resp;
      } catch (err: any) {
        entry.error = err?.message ?? String(err);
        entry.endTime = Date.now();
        entry.durationMs = entry.endTime - entry.startTime;
        entry.done = true;
        throw err;
      }
    };
  });

  // Fill and submit login form
  await page.locator('input[name="username"]').fill('nop');
  await page.locator('input[name="password"]').fill('123');
  await page.locator('button[type="submit"]').click();

  // Wait and poll for completion
  for (let i = 0; i < 20; i++) {
    await page.waitForTimeout(1000);
    const log = await page.evaluate(() => {
      const entries = (window as any).__fetchLog__ ?? [];
      return entries.map((e: any) => ({
        url: e.url?.slice(0, 100),
        method: e.method,
        status: e.status,
        done: e.done,
        durationMs: e.durationMs,
        error: e.error,
      }));
    });
    const allDone = log.every((e: any) => e.done);
    if (allDone && log.length > 0) {
      console.log(`=== FETCH LOG (after ${i + 1}s, all done) ===`);
      console.log(JSON.stringify(log, null, 2));
      break;
    }
    if (i === 19) {
      console.log(`=== FETCH LOG (after 20s, timeout) ===`);
      console.log(JSON.stringify(log, null, 2));
    }
  }

  const state = await page.evaluate(() => ({
    url: window.location.href,
    auth: sessionStorage.getItem('auth:v2'),
  }));
  console.log('=== FINAL STATE ===');
  console.log(JSON.stringify(state, null, 2));
});
