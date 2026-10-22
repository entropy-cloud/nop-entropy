# HTML 打印分页验收（WI4）

对 `HtmlReportRendererFactory` 的分页打印输出做 headless 浏览器验收（口径见
`ai-dev/design/nop-report/html-acceptance-baseline.md`）。

## 运行

```bash
# 1. 生成样例 HTML（120 行长表 + A4 pageSetup）
./mvnw test -pl nop-format/nop-excel -Dtest=GenHtmlPrintSample

# 2. 截图 + DOM 断言 + golden 比对（在仓库根目录）
node nop-format/nop-excel/src/test/html-acceptance/accept.mjs

# 重录 golden（确认渲染语义正确后；字体/浏览器版本变化会导致字节差异）
node nop-format/nop-excel/src/test/html-acceptance/accept.mjs --update-golden
```

## 断言面

- DOM：分页数 > 1；每节 `<thead>` 复制表头行；每节页脚 `第 &P / &N 页` 求值正确；全部 tbody 行被渲染
- 视觉：逐 `.xpt-page` 元素截图与 golden 逐字节比对（`src/test/resources/golden/html-print/page-*.png`）

## 环境前提

- playwright@1.60（复用 `nop-entropy-e2e` 工作区依赖，chromium revision 1223，浏览器须已安装：
  `cd nop-entropy-e2e && npx playwright install chromium`）
- CJK 字体（无系统字体的 CI 需先安装，与 nop-report-pdf golden 同一口径）
- golden 与渲染字体/浏览器版本绑定；版本升级后重录用 `--update-golden`

## 已知限制（design 登记）

- 分页模式忽略 fitToWidth/fitToHeight/scale（按原始尺寸装箱）
- 横向分页与关键列重复（repeat-key-columns HTML 等价）为后续交付
