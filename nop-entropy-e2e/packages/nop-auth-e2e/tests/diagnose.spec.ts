import { test } from '@nop-entropy/e2e-shared';
import { login } from '@nop-entropy/e2e-shared';

test('raw sitemap', async ({ page }) => {
  await login(page, { username: 'nop', password: '123' });

  const raw = await page.evaluate(async () => {
    const token = JSON.parse(sessionStorage.getItem('auth:v2') || '{}')?.state?.token;
    const resp = await fetch('/r/SiteMapApi__getSiteMap', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-access-token': token },
      body: JSON.stringify({ siteId: 'main' }),
    });
    return resp.text();
  });

  // Find routePath entries in the raw JSON
  const matches = raw.match(/"routePath"\s*:\s*"[^"]*"/g) || [];
  console.log('=== ALL ROUTE PATHS ===');
  matches.forEach(m => console.log(m));

  console.log('\n=== RAW (first 3000 chars) ===');
  console.log(raw.slice(0, 3000));
});
