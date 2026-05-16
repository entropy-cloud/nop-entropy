import type { Page } from '@playwright/test';
import { AMIS } from './amis-selectors.js';
import { FIELD_TO_HEADER } from './table-helper.js';

export async function waitForModal(page: Page): Promise<void> {
  await page.locator(`${AMIS.MODAL}`).first().waitFor({ state: 'visible', timeout: 10_000 });
  await page.waitForLoadState('networkidle');
}

export async function waitForDrawer(page: Page): Promise<void> {
  await page.locator(`${AMIS.DRAWER}`).first().waitFor({ state: 'visible', timeout: 10_000 });
  await page.waitForLoadState('networkidle');
}

export async function fillModalField(
  page: Page,
  fieldName: string,
  value: string,
): Promise<void> {
  const container = page.locator(`${AMIS.MODAL}, ${AMIS.DRAWER}`).last();
  const input = container.locator(`input[name="${fieldName}"]`).first();

  const visible = await input.isVisible().catch(() => false);
  if (!visible) return;

  await input.clear();
  await input.fill(value);
}

export async function readModalField(
  page: Page,
  fieldName: string,
  fieldLabelMap?: Record<string, string>,
): Promise<string> {
  const container = page.locator(`${AMIS.MODAL}, ${AMIS.DRAWER}`).last();

  const input = container.locator(`input[name="${fieldName}"]`).first();
  const inputVisible = await input.isVisible().catch(() => false);
  if (inputVisible) {
    return input.inputValue();
  }

  const labelMap = { ...FIELD_TO_HEADER, ...fieldLabelMap };
  const label = labelMap[fieldName];
  if (!label) return '';

  const formItem = container.locator(AMIS.FORM_ITEM).filter({ hasText: label }).first();
  const formItemVisible = await formItem.isVisible().catch(() => false);
  if (!formItemVisible) return '';

  const valueEl = formItem.locator('.cxd-Form-static, .cxd-PlainField, .cxd-Static').first();
  const valueVisible = await valueEl.isVisible().catch(() => false);
  if (valueVisible) {
    return (await valueEl.textContent())?.trim() ?? '';
  }

  const itemText = (await formItem.textContent()) ?? '';
  const afterLabel = itemText.replace(new RegExp(`^.*${label}\\*?`), '').trim();
  return afterLabel;
}
