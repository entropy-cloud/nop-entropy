/**
 * HTML 交互行为验收（WI5）：headless Chromium（screen 媒介）排序/折叠/响应式 DOM 断言。
 *
 * 运行（仓库根目录）：
 *   node nop-format/nop-excel/src/test/html-acceptance/interact.mjs
 */
import { createRequire } from 'node:module';
import { join, resolve, dirname } from 'node:path';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../../../..');
const e2ePlaywright = join(repoRoot, 'nop-entropy-e2e/node_modules/.pnpm/playwright@1.60.0/node_modules/playwright/index.js');
if (!existsSync(e2ePlaywright)) {
    console.error('playwright 未找到：' + e2ePlaywright);
    process.exit(1);
}
const require = createRequire(import.meta.url);
const { chromium } = require(e2ePlaywright);

const fixture = resolve(here, 'interact-fixture.html');
let failed = 0;
function check(name, ok, detail) {
    console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : ' — ' + detail));
    if (!ok) failed++;
}

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 900, height: 1200 } });
await page.goto('file://' + fixture);
await page.waitForTimeout(100);

// 排序：点击「金额」列 → tbody 按 3,7,20,500 升序
await page.click('th:nth-child(3)');
let order = await page.$$eval('tbody tr', rows => rows.map(r => r.children[2].textContent.trim()));
check('sort by amount ascending', JSON.stringify(order) === JSON.stringify(['3', '7', '20', '500']), JSON.stringify(order));

// 排序后折叠组标记随行走（排序不破坏组结构标记）
const groupAttr = await page.$eval('tbody tr[data-expand-group]', r => r.getAttribute('data-expand-group'));
check('group markers survive sort', groupAttr === 'row-0', groupAttr);

// 折叠：点击母行 → 子行隐藏、aria-expanded=false；再点恢复
await page.click('tr[data-expand-toggle]');
const afterCollapse = await page.$eval('tbody', tbody => {
    const rows = [...tbody.querySelectorAll('tr')];
    return rows.filter(r => r.style.display === 'none').length;
});
check('collapse hides child rows', afterCollapse === 2, 'hidden=' + afterCollapse);
const ariaCollapsed = await page.$eval('tr[data-expand-toggle]', r => r.getAttribute('aria-expanded'));
check('aria-expanded=false after collapse', ariaCollapsed === 'false', ariaCollapsed);

await page.click('tr[data-expand-toggle]');
const afterExpand = await page.$eval('tbody', tbody => {
    const rows = [...tbody.querySelectorAll('tr')];
    return rows.filter(r => r.style.display === 'none').length;
});
check('expand restores child rows', afterExpand === 0, 'hidden=' + afterExpand);

// 响应式：窄视口下容器 CSS 生效
await page.setViewportSize({ width: 480, height: 800 });
const responsive = await page.$eval('#xpt-report', el => getComputedStyle(el).overflowX);
check('narrow viewport overflow-x auto', responsive === 'auto', responsive);
const fontSize = await page.$eval('#xpt-report td', el => getComputedStyle(el).fontSize);
check('narrow viewport cell font 12px', fontSize === '12px', fontSize);

await browser.close();
process.exit(failed === 0 ? 0 : 1);
