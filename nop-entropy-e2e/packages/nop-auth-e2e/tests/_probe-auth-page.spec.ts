import { test } from '@nop-entropy/e2e-shared';
import { writeFileSync } from 'fs';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';

test('capture console errors on 授权 click', async ({ request, page, engine }) => {
  await loginRpc(request);
  const roleId = `pe_${Date.now()}`;
  await rpc(request, 'NopAuthRole__save', { data: { roleId, roleName: 'PE', isPrimary: 0 } });

  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 300)); });
  page.on('pageerror', (e) => errors.push(`PAGEERROR: ${String(e).slice(0, 300)}`));

  await login(page, { username: 'nop', password: '123' });
  await page.goto('#/NopAuthRole-main');
  await page.waitForLoadState('networkidle');
  await engine.crudContainer(page).waitFor({ state: 'visible', timeout: 30_000 });
  await page.waitForTimeout(2000);

  await page.locator('[data-slot="table-body"] [data-slot="dropdown-button-trigger"]').first().click();
  await page.waitForTimeout(1000);
  await page.evaluate(() => {
    const items = Array.from(document.querySelectorAll('[data-slot="dropdown-menu-item"]'));
    const it = items.find((i) => (i.textContent || '').includes('授权'));
    (it as HTMLElement)?.click();
  });
  await page.waitForTimeout(4000);

  writeFileSync('/tmp/auth-console-errors.txt', errors.join('\n---\n'));
  await rpc(request, 'NopAuthRole__delete', { id: roleId }).catch(() => {});
});
