/**
 * HTML 打印分页验收（ai-dev/design/nop-report/html-acceptance-baseline.md）：
 * headless Chromium（print 媒介）逐页截图 golden + DOM 断言。
 *
 * 运行（仓库根目录）：
 *   ./mvnw test -pl nop-format/nop-excel -Dtest=GenHtmlPrintSample   # 生成 sample.html
 *   node nop-format/nop-excel/src/test/html-acceptance/accept.mjs [--update-golden]
 *
 * golden：nop-format/nop-excel/src/test/resources/golden/html-print/page-*.png
 * playwright：复用 nop-entropy-e2e 工作区的 playwright@1.60（chromium revision 1223，须已安装浏览器）。
 * 环境前提：CJK 字体（无字体的 CI 需先安装，与 nop-report-pdf golden 同一口径）；
 *         字体/浏览器版本变化会使截图字节不一致——确认渲染语义正确后用 --update-golden 重录。
 */
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const moduleDir = resolve(here, '../../..');                 // nop-format/nop-excel
const repoRoot = resolve(moduleDir, '../..');
const goldenDir = resolve(moduleDir, 'src/test/resources/golden/html-print');
const UPDATE = process.argv.includes('--update-golden');

const e2ePlaywright = join(repoRoot, 'nop-entropy-e2e/node_modules/.pnpm/playwright@1.60.0/node_modules/playwright/index.js');
if (!existsSync(e2ePlaywright)) {
    console.error('playwright 未找到：' + e2ePlaywright + '（先在 nop-entropy-e2e 安装依赖）');
    process.exit(1);
}
const require = createRequire(import.meta.url);
const { chromium } = require(e2ePlaywright);

const sampleFile = resolve(here, 'sample.html');
if (!existsSync(sampleFile)) {
    console.error('sample.html 不存在——先运行 ./mvnw test -pl nop-format/nop-excel -Dtest=GenHtmlPrintSample');
    process.exit(1);
}

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 900, height: 1200 } });
await page.emulateMedia({ media: 'print' });
await page.goto('file://' + sampleFile);
await page.waitForTimeout(200);

const domChecks = await page.evaluate(() => {
    const pages = [...document.querySelectorAll('.xpt-page')];
    return {
        pageCount: pages.length,
        theadRows: pages.map(p => p.querySelectorAll('thead tr').length),
        footers: pages.map(p => p.querySelector('.xpt-page-footer')?.textContent?.trim()),
        bodyRowCount: pages.reduce((n, p) => n + p.querySelectorAll('tbody tr').length, 0)
    };
});

let failed = 0;
function check(name, ok, detail) {
    console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : ' — ' + detail));
    if (!ok) failed++;
}

check('page count > 1', domChecks.pageCount > 1, 'pages=' + domChecks.pageCount);
check('each page repeats header row', domChecks.theadRows.every(n => n === 1), JSON.stringify(domChecks.theadRows));
check('each page has footer', domChecks.footers.every(f => f && f.includes('页')), JSON.stringify(domChecks.footers));
check('page numbering', domChecks.footers[0] === '第 1 / ' + domChecks.pageCount + ' 页'
    && domChecks.footers.at(-1) === '第 ' + domChecks.pageCount + ' / ' + domChecks.pageCount + ' 页',
    JSON.stringify(domChecks.footers));
check('all body rows rendered', domChecks.bodyRowCount === 120, 'rows=' + domChecks.bodyRowCount);

if (UPDATE)
    mkdirSync(goldenDir, { recursive: true });

const goldenFiles = existsSync(goldenDir) ? readdirSync(goldenDir).filter(f => f.endsWith('.png')) : [];
if (!UPDATE)
    check('golden count matches pages', goldenFiles.length === domChecks.pageCount,
        'golden=' + goldenFiles.length + ' pages=' + domChecks.pageCount);

for (let i = 1; i <= domChecks.pageCount; i++) {
    const name = 'page-' + i + '.png';
    const goldenPath = join(goldenDir, name);
    const shot = await page.locator('.xpt-page').nth(i - 1).screenshot();
    if (UPDATE) {
        writeFileSync(goldenPath, shot);
        console.log('UPDATED golden ' + name);
        continue;
    }
    if (!existsSync(goldenPath)) {
        check('golden ' + name, false, 'missing — run with --update-golden');
        continue;
    }
    const equal = Buffer.compare(readFileSync(goldenPath), shot) === 0;
    check('golden ' + name, equal,
        equal ? '' : 'bytes differ — 字体/浏览器版本变化时确认渲染语义后用 --update-golden 重录');
}

await browser.close();
process.exit(failed === 0 ? 0 : 1);
