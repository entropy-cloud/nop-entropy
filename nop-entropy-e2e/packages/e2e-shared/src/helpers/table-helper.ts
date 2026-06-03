import type { Page } from '@playwright/test';
import { AMIS } from './amis-selectors.js';

const FIELD_TO_HEADER: Record<string, string> = {
  roleId: '角色ID',
  roleName: '角色名',
  isPrimary: '是否主角色',
  userName: '用户名',
  nickName: '昵称',
  gender: '性别',
  email: '邮箱',
  phone: '电话',
  userType: '用户类型',
  status: '状态',
  resourceId: '资源ID',
  displayName: '显示名称',
  resourceType: '资源类型',
  siteId: '站点',
  orderNo: '排序',
  parentId: '父资源',
  icon: '图标',
  routePath: '前端路由',
  url: '链接',
  component: '组件名',
  remark: '备注',
  createdBy: '创建人',
  createTime: '创建时间',
  updatedBy: '修改人',
  updateTime: '修改时间',
};

export async function waitForTableLoad(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
  const spinner = page.locator(AMIS.SPINNER).first();
  const spinnerVisible = await spinner.isVisible().catch(() => false);
  if (spinnerVisible) {
    await spinner.waitFor({ state: 'hidden' });
  }
  await page
    .waitForFunction(() => document.querySelectorAll('tbody > tr').length > 0, {
      timeout: 15_000,
    })
    .catch(() => {});
}

export async function navigateToEntity(
  page: Page,
  entityName: string,
): Promise<void> {
  const targetPath = `/${entityName}-main`;

  // If already on the target page, just wait for table
  if (page.url().includes(targetPath)) {
    await waitForTableLoad(page);
    return;
  }

  // Try navigating directly. If SPA routes aren't ready yet (menu still loading),
  // the app may redirect to home. Retry once after waiting.
  await page.goto(`/#${targetPath}`);
  await page.waitForLoadState('networkidle');

  if (!page.url().includes(targetPath)) {
    // SPA may not have registered the route yet — wait for sidebar to appear
    // (indicating menu data has loaded and routes are registered), then retry
    await page.locator('nav').waitFor({ state: 'visible', timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(500);
    await page.goto(`/#${targetPath}`);
    await page.waitForLoadState('networkidle');
  }

  await waitForTableLoad(page);
}

export async function readTableCell(
  page: Page,
  rowIdentifier: string,
  columnName: string,
): Promise<string> {
  const headerName = FIELD_TO_HEADER[columnName] ?? columnName;

  const headers = page.locator('th');
  const headerCount = await headers.count();
  let colIndex = -1;

  for (let i = 0; i < headerCount; i++) {
    const text = (await headers.nth(i).innerText()).trim();
    if (text === headerName) {
      colIndex = i;
      break;
    }
  }

  if (colIndex < 0) {
    throw new Error(`Column "${columnName}" (header "${headerName}") not found in table headers`);
  }

  const row = page.locator('tbody > tr').filter({ hasText: rowIdentifier }).first();
  const cell = row.locator('td').nth(colIndex);
  return (await cell.innerText()).trim();
}

export async function getTableRowCount(page: Page): Promise<number> {
  return page.locator('tbody > tr').count();
}

export { FIELD_TO_HEADER };
