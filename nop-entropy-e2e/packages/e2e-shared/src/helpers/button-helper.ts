import type { Page } from '@playwright/test';
import { AMIS } from './amis-selectors.js';

export async function clickButton(page: Page, label: string): Promise<void> {
  const byRole = page.getByRole('button', { name: label, exact: true });
  const roleVisible = await byRole.first().isVisible().catch(() => false);
  if (roleVisible) {
    await byRole.first().click();
    return;
  }

  const byText = page.getByText(label, { exact: true });
  const textVisible = await byText.first().isVisible().catch(() => false);
  if (textVisible) {
    await byText.first().click();
  }
}

export async function confirmDialog(page: Page): Promise<void> {
  const confirmBtn = page.locator(AMIS.CONFIRM_BTN).first();
  const visible = await confirmBtn.isVisible().catch(() => false);
  if (visible) {
    await confirmBtn.click();
    return;
  }

  const modalBtn = page
    .locator(`${AMIS.MODAL}`)
    .getByRole('button', { name: '确认', exact: true })
    .first();
  await modalBtn.click();
}

export async function clickRowAction(
  page: Page,
  cellValue: string,
  actionLabel: string,
): Promise<void> {
  const row = page.locator('tr').filter({ hasText: cellValue }).first();
  await row.waitFor({ state: 'visible' });

  if (actionLabel === '查看') {
    const viewBtn = row.getByRole('button', { name: '查看', exact: true });
    const viewVisible = await viewBtn.isVisible().catch(() => false);
    if (viewVisible) {
      await viewBtn.click();
      return;
    }
  }

  const moreActions = ['编辑', '删除', '重置密码', '禁用用户', '用户', '授权'];
  if (moreActions.includes(actionLabel)) {
    const moreBtn = row.getByRole('button', { name: '更多', exact: true });
    const moreVisible = await moreBtn.isVisible().catch(() => false);
    if (moreVisible) {
      await moreBtn.click();
      await page.waitForTimeout(500);
      const menuItem = page
        .locator(AMIS.DROPDOWN_MENU)
        .getByText(actionLabel, { exact: true })
        .first();
      await menuItem.waitFor({ state: 'visible' });
      await menuItem.click();
      return;
    }
  }

  const directBtn = row
    .getByRole('button', { name: actionLabel, exact: true })
    .first();
  const directVisible = await directBtn.isVisible().catch(() => false);
  if (directVisible) {
    await directBtn.click();
    return;
  }

  await row.getByText(actionLabel, { exact: true }).first().click();
}
