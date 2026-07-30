import { test } from '@nop-entropy/e2e-shared';
import { expect } from '@playwright/test';
import { login } from '@nop-entropy/e2e-shared';

test('diagnose: capture every single URL including navigation', async ({ page }) => {
  // Capture ALL responses from the start
  const allUrls: string[] = [];
  page.on('response', (resp) => {
    const url = resp.url();
    const ct = resp.headers()['content-type'] || '';
    allUrls.push(`[${resp.status()}] ${url} (${ct.substring(0, 40)})`);
  });

  await login(page, { username: 'nop', password: '123' });

  // Now navigate
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(3000);

  // Print unique URLs (skip assets/vendor)
  const unique = [...new Set(allUrls)];
  console.log(`\n=== ALL ${unique.length} unique responses ===`);
  for (const u of unique) {
    if (!u.includes('/assets/') && !u.includes('/vendor/') && !u.includes('favicon') && !u.includes('locales')) {
      console.log(u);
    } else {
      // Print a condensed count
    }
  }

  // Show counts by type
  const rpc = unique.filter(u => u.includes('/r/'));
  const graphql = unique.filter(u => u.includes('graphql'));
  const html = unique.filter(u => u.includes('text/html'));
  const json = unique.filter(u => u.includes('application/json'));
  console.log(`\n=== Counts ===`);
  console.log(`HTML: ${html.length}`);
  console.log(`JSON: ${json.length}`);
  console.log(`RPC (/r/): ${rpc.length}`);
  console.log(`GraphQL: ${graphql.length}`);

  expect(true).toBe(true);
});
