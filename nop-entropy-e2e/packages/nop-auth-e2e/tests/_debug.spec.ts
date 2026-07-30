import { test } from '@playwright/test';
import { login, FluxAdapter } from '@nop-entropy/e2e-shared';
import { ResourcePO } from './page-objects/resource.po';

test('diag: verify save + refresh', async ({ page }) => {
  page.on('console', (msg) => console.log(`[${msg.type()}] ${msg.text().slice(0, 400)}`));

  const saveUrls: string[] = [];
  const findListUrls: string[] = [];
  page.on('request', (req) => {
    const url = req.url();
    if (url.includes('NopAuthResource__save')) saveUrls.push(url);
    if (url.includes('NopAuthResource__findList')) findListUrls.push(url);
  });

  const engine = new FluxAdapter();
  await login(page, { username: 'nop', password: '123' });
  const po = new ResourcePO(page, engine);
  await po.goto();
  await po.clickAdd();
  await po.fillForm({
    resourceId: 'e2e_diag_save2',
    siteId: 'main',
    displayName: 'E2E Save2',
    resourceType: 'TOPM',
    orderNo: 9999,
    status: 1,
  });

  console.log(`\nBefore submit: ${findListUrls.length} findList calls`);

  // Click submit directly from dialog footer
  const submitBtn = page.locator('[data-slot="dialog-footer"] button').filter({ hasText: /确定/ }).first();
  await submitBtn.click();
  
  // Wait for everything to settle
  await page.waitForTimeout(5000);

  console.log(`\nAfter submit: ${saveUrls.length} save calls, ${findListUrls.length} findList calls`);
  saveUrls.forEach(u => console.log(`  SAVE: ${u.replace(/\?.*/, '')}`));
  findListUrls.forEach(u => console.log(`  FIND: ${u.replace(/\?.*/, '')}`));

  // Check if resource exists in DB
  const dbCheck = await page.evaluate(async () => {
    const r = await fetch('/r/NopAuthResource__findList?filter_resourceId=e2e_diag_save2', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
    });
    const d = await r.json();
    return { count: d.data?.length ?? 0, first: d.data?.[0] };
  });
  console.log(`\nDB check: ${JSON.stringify(dbCheck, null, 2).slice(0, 500)}`);

  // Check table content
  const tableContent = await page.evaluate(() => {
    const rows = document.querySelectorAll('[data-slot="table-body"] tr[data-slot="table-row"]');
    return `${rows.length} rows`;
  });
  console.log(`Table: ${tableContent}`);

  // Cleanup
  await page.evaluate(async () => {
    try {
      const r = await fetch('/r/NopAuthResource__findList?filter_resourceId=e2e_diag_save2', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
      const d = await r.json();
      const id = d.data?.[0]?.id;
      if (id) await fetch('/r/NopAuthResource__delete', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id }) });
    } catch {}
  });
});
