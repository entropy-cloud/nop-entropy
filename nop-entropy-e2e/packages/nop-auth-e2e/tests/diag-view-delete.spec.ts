import { test, expect } from '@playwright/test';
import { login, rpc } from '@nop-entropy/e2e-shared';

const TEST_ID = `diag_${Date.now()}`;

test('diag: view dialog, delete confirm, search toolbar', async ({ page, request }) => {
  const roleId = `${TEST_ID}_role`;
  const roleName = `E2E_诊断_${TEST_ID}`;
  await rpc(request, 'NopAuthRole__save', {
    data: { roleId, roleName, remark: '测试备注' },
  });

  await login(page, { fast: true });
  await page.goto('#/NopAuthRole-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // 1. Dump CRUD toolbar
  const toolbar = page.locator('.cxd-Crud-toolbar, .cxd-Crud .cxd-Button-toolbar').first();
  if (await toolbar.count().then(c => c > 0)) {
    console.log('=== TOOLBAR HTML ===');
    console.log(await toolbar.innerHTML());
  } else {
    // Dump all buttons in the CRUD area
    const crudArea = page.locator('.cxd-Crud').first();
    const buttons = crudArea.locator('button');
    const btnCount = await buttons.count();
    console.log('=== CRUD BUTTONS ===');
    for (let i = 0; i < Math.min(10, btnCount); i++) {
      const text = await buttons.nth(i).textContent();
      const cls = await buttons.nth(i).getAttribute('class');
      console.log(`Button ${i}: text="${text?.trim()}" class="${cls}"`);
    }
  }

  // 2. Search for the role
  const filterInput = page.locator('input[name^="filter_roleName"]').first();
  if (await filterInput.isVisible().catch(() => false)) {
    await filterInput.fill(roleName);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(2000);
  }

  // Find our row
  const rows = page.locator('tr, .cxd-Table-row');
  const rowCount = await rows.count();
  let targetRow = null;
  for (let i = 0; i < rowCount; i++) {
    const text = await rows.nth(i).textContent();
    if (text && text.includes(roleId)) {
      targetRow = rows.nth(i);
      break;
    }
  }

  if (targetRow) {
    // 3. Click "查看" (View) — direct button
    const viewBtn = targetRow.locator('button').filter({ hasText: /查看/ }).first();
    await viewBtn.click();
    await page.waitForTimeout(1500);

    const dialog = page.locator('.cxd-Modal, .cxd-Dialog').first();
    if (await dialog.isVisible().catch(() => false)) {
      console.log('=== VIEW DIALOG HTML (first 5000 chars) ===');
      const html = await dialog.innerHTML();
      console.log(html.substring(0, 5000));
    }

    // Close dialog
    const closeBtn = page.locator('.cxd-Modal-close, button:has-text("关闭"), button:has-text("取消")').first();
    if (await closeBtn.isVisible().catch(() => false)) {
      await closeBtn.click();
    } else {
      await page.keyboard.press('Escape');
    }
    await page.waitForTimeout(500);

    // 4. Click "更多" → "删除" and dump confirmation
    const moreBtn = targetRow.locator('button').filter({ hasText: /更多/ }).first();
    await moreBtn.click();
    await page.waitForTimeout(500);

    const deleteItem = page.locator('.cxd-DropDown-menuItem, .cxd-DropDown-menu > *').filter({ hasText: /删除/ }).first();
    await deleteItem.click();
    await page.waitForTimeout(1500);

    // Dump confirmation dialog
    const confirmDialog = page.locator('.cxd-Modal, .cxd-Dialog, .cxd-Alert').last();
    if (await confirmDialog.isVisible().catch(() => false)) {
      console.log('=== CONFIRM DIALOG HTML (first 3000 chars) ===');
      const confirmHtml = await confirmDialog.innerHTML();
      console.log(confirmHtml.substring(0, 3000));
    }

    // Dump all visible buttons
    const allButtons = page.locator('button:visible');
    const allBtnCount = await allButtons.count();
    console.log('=== ALL VISIBLE BUTTONS ===');
    for (let i = 0; i < allBtnCount; i++) {
      const text = await allButtons.nth(i).textContent();
      console.log(`Visible Button ${i}: "${text?.trim()}"`);
    }
  } else {
    console.log('TARGET ROW NOT FOUND');
  }

  // Cleanup
  await rpc(request, 'NopAuthRole__delete', { data: { roleId } });
});
