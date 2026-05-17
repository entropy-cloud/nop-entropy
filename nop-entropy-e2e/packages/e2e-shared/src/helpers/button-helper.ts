import type { Page, Locator } from '@playwright/test';
import { AMIS } from './amis-selectors.js';

/**
 * Click a button identified by its label text.
 *
 * Resolution order:
 *   1. Visible <button> or clickable element whose accessible name equals `label`
 *   2. "更多" dropdown expansion → click the matching dropdown menu item
 *
 * This abstracts away whether the button is directly visible or nested
 * inside a "更多" dropdown group.
 */
export async function clickByLabel(
  page: Page,
  label: string,
): Promise<void> {
  const direct = page
    .getByRole('button', { name: label, exact: true })
    .first();
  const directVisible = await direct.isVisible().catch(() => false);
  if (directVisible) {
    await direct.click();
    return;
  }

  const textEl = page.getByText(label, { exact: true }).first();
  const textVisible = await textEl.isVisible().catch(() => false);
  if (textVisible) {
    await textEl.click();
    return;
  }

  await expandMoreAndClick(page, null, label);
}

/**
 * Click a button inside a specific table row, identified by business text.
 *
 * @param page       Playwright page
 * @param rowText    Text content that uniquely identifies the target row
 * @param label      Button label to click (e.g. '立即触发', '编辑', '删除')
 *
 * Resolution order inside the row:
 *   1. Direct <button> with matching label
 *   2. Expand the row's "更多" dropdown → click matching menu item
 */
export async function clickInRow(
  page: Page,
  rowText: string,
  label: string,
): Promise<void> {
  const row = page.locator('tr').filter({ hasText: rowText }).first();
  await row.waitFor({ state: 'visible' });

  const directBtn = row
    .getByRole('button', { name: label, exact: true })
    .first();
  const directVisible = await directBtn.isVisible().catch(() => false);
  if (directVisible) {
    await directBtn.click();
    return;
  }

  await expandMoreAndClick(page, row, label);
}

/**
 * Expand a "更多" dropdown and click the menu item matching `label`.
 *
 * @param page   Playwright page
 * @param scope  If provided, scope "更多" search to this Locator (a row).
 *              If null, search the full page.
 * @param label  Menu item text to click after expansion.
 */
async function expandMoreAndClick(
  page: Page,
  scope: Locator | null,
  label: string,
): Promise<void> {
  const container = scope ?? page;
  const moreBtn = container
    .getByRole('button', { name: '更多', exact: true })
    .first();
  const moreVisible = await moreBtn.isVisible().catch(() => false);
  if (!moreVisible) {
    throw new Error(
      `Button "${label}" not found (not visible directly, and no "更多" dropdown)`,
    );
  }

  await moreBtn.click();
  await page.waitForTimeout(500);

  const menuItem = page
    .locator(AMIS.DROPDOWN_MENU)
    .getByText(label, { exact: true })
    .first();
  await menuItem.waitFor({ state: 'visible' });
  await menuItem.click();
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

/**
 * @deprecated Use clickByLabel instead.
 */
export async function clickButton(page: Page, label: string): Promise<void> {
  await clickByLabel(page, label);
}

/**
 * @deprecated Use clickInRow instead.
 */
export async function clickRowAction(
  page: Page,
  cellValue: string,
  actionLabel: string,
): Promise<void> {
  await clickInRow(page, cellValue, actionLabel);
}
