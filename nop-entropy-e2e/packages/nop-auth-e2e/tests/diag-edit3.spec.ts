import { test, getEngine } from '@nop-entropy/e2e-shared';
import { login } from '@nop-entropy/e2e-shared';
import { ResourcePO } from './page-objects/resource.po.js';

test('diag: dialog ancestor chain', async ({ page }) => {
  await login(page, { username: 'nop', password: '123' });
  await page.waitForTimeout(3000);
  const po = new ResourcePO(page, getEngine());
  await po.goto();
  const rid = `diag_res_${Date.now()}`;
  const dialog = await po.clickAdd();
  await po.fillForm({ resourceId: rid, siteId: 'main', displayName: 'DIAG_' + rid, resourceType: 'TOPM', orderNo: 1, status: 1 });
  await dialog.submit();
  await po.waitForList();

  await po.clickEdit(rid);
  await page.waitForTimeout(3000);

  const info = await page.evaluate(() => {
    const el = document.querySelector('#displayName-control') as HTMLInputElement | null;
    if (!el) return 'NO INPUT';
    // 祖先链（tag + class 前 40 字符）
    const chain: string[] = [];
    let node: HTMLElement | null = el;
    while (node && node !== document.body) {
      chain.push(`${node.tagName}.${(node.className || '').toString().slice(0, 30)}`);
      node = node.parentElement;
    }
    chain.push('BODY');
    // #root 子节点
    const root = document.querySelector('#root');
    const rootChildren = root ? Array.from(root.children).map(c => c.tagName + '.' + (c.className || '').toString().slice(0, 30)) : [];
    // document.body 直接子节点
    const bodyChildren = Array.from(document.body.children).map(c => `${c.tagName}#${c.id}.${(c.className || '').toString().slice(0, 30)}`);
    return JSON.stringify({ chain, rootChildren, bodyChildren }, null, 1);
  });
  console.log('ANCESTRY: ' + info);
});
