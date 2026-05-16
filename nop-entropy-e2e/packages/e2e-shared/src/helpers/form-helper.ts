import type { Page } from '@playwright/test';

interface FillFieldOptions {
  inFilter?: boolean;
}

export async function fillField(
  page: Page,
  fieldName: string,
  value: string,
  options?: FillFieldOptions,
): Promise<void> {
  const selector = options?.inFilter
    ? `input[name^="filter_${fieldName}"]`
    : `input[name="${fieldName}"]`;

  const input = page.locator(selector).first();
  const visible = await input.isVisible().catch(() => false);
  if (!visible) return;

  await input.clear();
  await input.fill(value);
}

export async function readField(
  page: Page,
  fieldName: string,
): Promise<string> {
  const input = page.locator(`input[name="${fieldName}"]`).first();
  return input.inputValue();
}

export async function selectOption(
  page: Page,
  fieldName: string,
  optionLabel: string | number,
): Promise<void> {
  const labelStr = String(optionLabel);

  const namedControl = page.locator(`[name="${fieldName}"]`).first();
  const namedVisible = await namedControl.isVisible().catch(() => false);
  if (namedVisible) {
    await namedControl.click();
    const option = page
      .locator('.cxd-Select-menu li, .cxd-Select-menu .cxd-Select-option')
      .filter({ hasText: labelStr })
      .first();
    await option.waitFor({ state: 'visible' });
    await option.click();
    return;
  }

  const amisControl = page.locator(
    `.cxd-Form-item[data-amis-name="${fieldName}"] .cxd-Select`,
  ).first();
  const amisVisible = await amisControl.isVisible().catch(() => false);
  if (!amisVisible) return;

  await amisControl.click();
  const pattern = new RegExp(escapeRegExp(labelStr), 'i');
  const option = page
    .locator('.cxd-Select-popover .cxd-Select-option')
    .filter({ hasText: pattern })
    .first();
  await option.waitFor({ state: 'visible', timeout: 5_000 });
  await option.click();
  await page.waitForTimeout(300);
}

function escapeRegExp(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
