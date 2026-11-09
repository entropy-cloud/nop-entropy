# docx 视觉验收工具与验收口径（docx roadmap WI0）

> Status: active（本记录为时效性证据；工具为 test 基建，消费方为 docx roadmap 的解析/生成/round-trip WI）
> Date: 2026-11-10
> Sources: nop-format/nop-ooxml/nop-ooxml-docx/src/test/java/io/nop/ooxml/docx/verify/DocxVisualVerifier.java、ai-dev/backlog/nop-docx-document-model-roadmap.md（WI0）、nop-pdf roadmap WI0 工具链

## 1. 工具链形态

docx 视觉验收 = `DocxVisualVerifier`（nop-ooxml-docx **test scope**）桥接：

```text
docx（语料或产物）
  → LibreOffice headless 转换 PDF（soffice --headless --convert-to pdf）
  → PdfImageRenderer.renderPage/renderAll（nop-pdf verify，main 代码）
  → PdfGoldenManager.compare/update + ImageDiff 容差
  → RegionProbe 像素抽样断言（补充手段）
```

- 语料来源：`io.nop.ooxml.docx.corpus.DocxCorpusBuilder`（程序合成，确定性契约：无时间戳/随机 ID，zip entry 固定 mtime，同方法两次调用字节一致）。
- golden 目录：`<repo-root>/_tmp/docx-golden/<fixture>/`，命名 `<sample>-page-<n>`；快照机器本地、不入库（`_tmp/` 已 gitignore）。与 nop-pdf 的 `_tmp/pdf-golden/` 刻意分离，避免两类 golden 混淆。

## 2. 口径裁定

| 项 | 裁定 | 理由 |
| --- | --- | --- |
| DPI | 110（`PdfImageRenderer.DEFAULT_DPI`） | 与 nop-pdf WI0 已确立口径一致，golden 可比 |
| 容差 | ImageDiff 默认（channel tolerance 8） | 沿用 nop-pdf verify 既有默认；首轮 golden 建立后按需收紧 |
| PASS 判定 | `PASS \|\| BASELINE_CREATED` | 与 nop-report-pdf 渲染测试既有约定一致；首跑建 baseline 属正常 |
| 更新模式 | `-Dnop.docx.golden.update=true`，由 DocxVisualVerifier **自持**（不走 `nop.pdf.golden.update`） | PdfGoldenManager 本体零改动；docx 与 pdf 更新口径互不干扰 |
| soffice 依赖 | `assumeTrue(isSofficeAvailable())` 具名跳过 + 日志 | soffice 缺失的机器/CI 上视觉 fixture 不可跑是环境事实，必须显式暴露而非静默通过 |
| soffice 发现 | `-Dnop.docx.soffice.path` → 常见安装位置 → PATH | 覆盖 macOS brew/App 安装与 Linux 包管理器 |
| soffice 隔离 | `-env:UserInstallation=file://<tmp>/soffice-profile` | 规避首启向导与并发 profile 锁冲突 |
| 转换超时 | 180s，超时/非零退出即 fail-fast（含捕获输出） | no-silent-no-op：转换失败必须显式报错 |
| 转换器选型 | LibreOffice headless（外部工具，仅 test scope） | WI0 阶段仓库内无 docx→PDF 能力；模型→PDF 渲染器属后续 WI，不提前混入产品代码 |

## 3. 冒烟覆盖（TestDocxVisualVerifier）

- simple：单页、文本 ink 存在、页角白底、golden。
- multi-section：≥2 页、每页 ink、golden（横向第二节渲染为横版页）。
- merged-table：单页、ink、非白像素占比 > 0.005（表格边框/底纹绘制存在）、golden。

首轮跑出的 golden（本机，LibreOffice 26.8.2.2）：`_tmp/docx-golden/wi0-docx-corpus/{simple,multi-section,merged-table}`。

## 4. 后续 WI 的消费方式

- 解析 WI：新增复杂语料样本 → 先跑 soffice 转换冒烟确认样本合法 → 用 OfficeDocModelParser 解析后做结构断言。
- 生成 WI：`docx → 模型 → docx` 后，把**再生成 docx** 亦过一次视觉链路与 golden（同一 fixture 目录追加 `-regen` 后缀命名），形成「语义等价断言 + 视觉等价」双重验收。
- PDF 渲染 WI：渲染器产出 PDF 后走同一 PdfImageRenderer/golden 口径，与 LibreOffice 对照页可并列 fixture。

## 5. 已知限制

- LibreOffice 渲染不是 OOXML 规范的逐字节实现：视觉 golden 是「渲染等价」证据，结构/语义等价仍以 round-trip 断言为准。
- LibreOffice 版本升级可能引起 golden 全量漂移：升级后以更新模式重建 baseline，并在当月日志记录版本号。
- 语料合法性在 zip 结构 + XML well-formed 层面全量保证；LibreOffice 打开性目前仅 3 个冒烟样本实证，其余样本随消费 WI 逐步实证。
