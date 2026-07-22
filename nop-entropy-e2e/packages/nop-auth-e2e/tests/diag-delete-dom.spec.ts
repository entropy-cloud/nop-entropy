import { test } from '@nop-entropy/e2e-shared';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';

const TEST_ID = `diagdom_${Date.now()}`;

test('diag: delete confirm dialog DOM inspection', async ({ request, page }) => {
  await loginRpc(request);

  const userName = `${TEST_ID}_del`;
  const saveResp = await rpc<{ id: string }>(request, 'NopAuthUser__save', {
    data: { userName, nickName: 'DiagDom', password: 'Test@1234', status: 1, userType: 1, gender: 1 },
  });
  console.log('Save resp ok:', saveResp.ok, 'id:', saveResp.data?.id);
  const userId = saveResp.data?.id;

  await login(page, { username: 'nop', password: '123' });
  await page.goto('#/NopAuthUser-main');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Search
  const filterInput = page.locator('input[name^="filter_userName"]').first();
  await filterInput.waitFor({ state: 'visible', timeout: 10_000 });
  await filterInput.fill(userName);
  await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(2000);

  // Find row
  const dataRow = await page.evaluate((name) => {
    const trs = document.querySelectorAll('tr');
    for (let i = 0; i < trs.length; i++) {
      if ((trs[i].textContent || '').includes(name)) return i;
    }
    return -1;
  }, userName);

  if (dataRow < 0) {
    console.log('ROW NOT FOUND');
    if (userId) await rpc(request, 'NopAuthUser__delete', { id: userId }).catch(() => {});
    return;
  }

  const row = page.locator('tr').nth(dataRow);

  // Click 更多
  const moreBtn = row.locator('button').filter({ hasText: /更多/ }).first();
  await moreBtn.click();
  await page.waitForTimeout(800);

  // Click 删除
  const deleteItem = page.locator('.cxd-DropDown-menu > *').filter({ hasText: /删除/ }).first();
  await deleteItem.click();
  await page.waitForTimeout(1500);

  // === DOM inspection via page.evaluate ===
  const dialogInfo = await page.evaluate(() => {
    const results: any = {};

    const alertdialogs = document.querySelectorAll('[role="alertdialog"]');
    results.alertdialogCount = alertdialogs.length;

    if (alertdialogs.length > 0) {
      const dlg = alertdialogs[alertdialogs.length - 1] as HTMLElement;
      results.alertdialogHTML = dlg.innerHTML.substring(0, 3000);
      results.alertdialogClasses = dlg.className;
      results.alertdialogCS = {
        display: getComputedStyle(dlg).display,
        visibility: getComputedStyle(dlg).visibility,
        opacity: getComputedStyle(dlg).opacity,
        pointerEvents: getComputedStyle(dlg).pointerEvents,
        zIndex: getComputedStyle(dlg).zIndex,
      };

      const btns = dlg.querySelectorAll('button, [role="button"]');
      results.dialogButtons = Array.from(btns).map((b) => {
        const el = b as HTMLElement;
        const cs = getComputedStyle(el);
        const r = el.getBoundingClientRect();
        return {
          text: el.textContent?.trim(),
          className: el.className.substring(0, 100),
          tag: el.tagName,
          role: el.getAttribute('role'),
          disabled: (el as HTMLButtonElement).disabled,
          display: cs.display,
          visibility: cs.visibility,
          pointerEvents: cs.pointerEvents,
          offsetParent: !!el.offsetParent,
          rect: { x: r.x, y: r.y, w: r.width, h: r.height },
        };
      });
    }

    // Also check all dialogs / modals
    const allModals = document.querySelectorAll('[role="dialog"], [role="alertdialog"], .cxd-Modal');
    results.allModalCount = allModals.length;
    results.allModalInfo = Array.from(allModals).map((m) => ({
      tag: m.tagName,
      role: m.getAttribute('role'),
      className: m.className.substring(0, 100),
      display: getComputedStyle(m as HTMLElement).display,
      childButtonCount: m.querySelectorAll('button').length,
    }));

    // Visible confirm-like buttons anywhere
    const allBtns = document.querySelectorAll('button, [role="button"]');
    const visibleConfirm = Array.from(allBtns)
      .filter((b) => {
        const el = b as HTMLElement;
        const t = el.textContent?.trim() || '';
        const cs = getComputedStyle(el);
        return (
          (t === 'Confirm' || t === '确定' || t === '确认' || t === 'OK' || t === 'Delete' || t === '删除') &&
          cs.display !== 'none' &&
          cs.visibility !== 'hidden' &&
          el.offsetParent !== null
        );
      })
      .map((b) => {
        const el = b as HTMLElement;
        const r = el.getBoundingClientRect();
        return {
          text: el.textContent?.trim(),
          className: el.className.substring(0, 100),
          inAlertDialog: !!el.closest('[role="alertdialog"]'),
          inDialog: !!el.closest('[role="dialog"]'),
          inCxdModal: !!el.closest('.cxd-Modal'),
          rect: { x: r.x, y: r.y, w: r.width, h: r.height },
        };
      });
    results.visibleConfirmButtons = visibleConfirm;

    // Overlays
    const overlays = document.querySelectorAll(
      '[data-slot], .cxd-Modal-overlay, .cxd-Modal--th-inner',
    );
    results.overlayInfo = Array.from(overlays).map((o) => ({
      tag: o.tagName,
      dataSlot: o.getAttribute('data-slot'),
      className: o.className.substring(0, 100),
      display: getComputedStyle(o as HTMLElement).display,
      zIndex: getComputedStyle(o as HTMLElement).zIndex,
      offsetParent: !!(o as HTMLElement).offsetParent,
    }));

    return results;
  });

  console.log('\n=== DIALOG DOM INSPECTION ===');
  console.log(JSON.stringify(dialogInfo, null, 2));

  // Try native click
  const clickResult = await page.evaluate(() => {
    const dlg = document.querySelector('[role="alertdialog"]');
    if (!dlg) return { error: 'no alertdialog' };
    const btns = dlg.querySelectorAll('button, [role="button"]');
    const results: any[] = [];
    for (const btn of btns) {
      const el = btn as HTMLElement;
      const text = el.textContent?.trim() || '';
      const cs = getComputedStyle(el);
      if (
        (text === 'Confirm' || text === '确定' || text === '确认' || text === 'OK') &&
        cs.display !== 'none' &&
        cs.visibility !== 'hidden'
      ) {
        try {
          el.click();
          results.push({ clicked: text, className: el.className.substring(0, 80) });
        } catch (e) {
          results.push({ error: String(e), text });
        }
      }
    }
    return { results };
  });
  console.log('\nNative click result:', JSON.stringify(clickResult));

  await page.waitForTimeout(3000);

  // Check post-delete state
  const afterDelete = await page.evaluate(() => {
    const dlg = document.querySelector('[role="alertdialog"]');
    return {
      alertDialogExists: !!dlg,
      alertDialogVisible:
        dlg ? getComputedStyle(dlg as HTMLElement).display !== 'none' : false,
    };
  });
  console.log('After delete:', JSON.stringify(afterDelete, null, 2));

  // Search again
  const filterInput2 = page.locator('input[name^="filter_userName"]').first();
  if (await filterInput2.isVisible().catch(() => false)) {
    await filterInput2.clear();
    await filterInput2.fill(userName);
    await page.locator('.cxd-Table-searchableForm button[type="submit"]').first().click({ force: true });
    await page.waitForTimeout(2000);

    const stillThere = await page.evaluate((name) => {
      const trs = document.querySelectorAll('tr');
      for (const tr of trs) {
        if ((tr.textContent || '').includes(name)) return true;
      }
      return false;
    }, userName);
    console.log(`User still in table: ${stillThere}`);
  }

  if (userId) {
    await rpc(request, 'NopAuthUser__delete', { id: userId }).catch(() => {});
  }
});
